package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Explicit additional copies, with one retained request and shared inventory-writer ownership. */
internal class DeckMarkOwnedController(
    private val scope: CoroutineScope,
    private val inventory: LocalInventory,
    private val decks: LocalDeckLibrary,
    private val allocator: OwnedQuantityAllocator,
    private val catalog: CatalogPrintingLookup,
    private val project: (InventorySnapshot, CollectionPatch, Revision, CancelToken) -> Outcome<InventoryProjection>,
    private val dispatcher: CoroutineDispatcher,
    private val nextEntityId: () -> EntityId,
    private val currentCatalogGeneration: () -> GenerationId?,
    private val mutationGate: InventoryMutationGate,
    private val onCommitted: (InventorySnapshot) -> Unit,
) {
    private val mutable = MutableStateFlow(DeckMarkOwnedState())
    val state: StateFlow<DeckMarkOwnedState> = mutable.asStateFlow()
    private var version = 0L
    private var formSerial = 0L
    private var sourceReady = false
    private var readJob: Job? = null
    private var mutationJob: Job? = null
    private var finishJob: Job? = null
    private var finishTicket = 0L
    private var lease: InventoryMutationGate.Lease? = null
    private data class Request(val preview: DeckMarkOwnedPreview, val operation: EntityId)
    private var request: Request? = null
    private var knownCommitted: InventoryCommit? = null
    private var deckFloor: Pair<DeckId, Revision>? = null
    private var inventoryFloor = Revision(0)
    private var inventoryTimeFloor: UtcTimestamp? = null
    private var notifiedGeneration: GenerationId? = null
    private var generationNotified = false

    fun resume() { if (mutable.value.deckId != null || request != null) mutable.value = mutable.value.copy(visible = true) }
    fun close() { mutable.value = mutable.value.copy(visible = false) }
    fun open(id: DeckId, policy: OwnedQuantityPolicy) {
        val s = mutable.value
        if (s.deckId == id && s.policy == policy) { resume(); return }
        if (lease != null || request != null || s.loading || s.previewing || s.rows.any { it.selected || it.input != initialInput(it.estimate) }) {
            mutable.value = s.copy(visible = true, problem = Problem.STALE_REVISION); return
        }
        if (!validPolicy(policy)) { mutable.value = s.copy(visible = true, problem = Problem.INVALID_INPUT); return }
        startLoad(id, policy, null)
    }
    /** Explicit new-source admission never changes the authority of a submitted request. */
    fun refreshSources() {
        if (lease != null || request != null || knownCommitted != null || mutable.value.loading) return
        val s = mutable.value
        val id = s.deckId ?: return
        val policy = s.policy ?: return
        startLoad(id, policy, s.rows)
    }
    private fun startLoad(id: DeckId, policy: OwnedQuantityPolicy, retained: SnapshotList<DeckMarkOwnedRow>?) {
        readJob?.cancel(); cancelFinishLookup(); sourceReady = false; val ticket = ++version
        if (deckFloor?.first != id) deckFloor = id to Revision(0)
        mutable.value = if (retained == null) DeckMarkOwnedState(formTicket = ++formSerial, visible = true, deckId = id, policy = policy, loading = true)
            else mutable.value.copy(loading = true, previewing = false, preview = null, allocation = null, estimateProblem = null,
                problem = null, issues = SnapshotList.empty(), rows = SnapshotList.copyOf(retained.map { clearChoices(it).copy(estimate = null, currentEstimate = null) }))
        readJob = scope.launch {
            try {
                val loaded = callPort(dispatcher) { cancel ->
                    when (val deck = decks.load(id, cancel)) {
                        is Outcome.Success -> if (!validDeck(deck.value, id, policy)) invalid() else when (val stored = inventory.snapshot(cancel)) {
                            is Outcome.Success -> when (val checked = checkedSnapshot(stored.value, cancel)) {
                                is Outcome.Success -> Outcome.Success(deck.value to stored.value)
                                is Outcome.Failure -> checked; is Outcome.Unavailable -> checked; is Outcome.Unknown -> checked
                            }
                            is Outcome.Failure -> stored; is Outcome.Unavailable -> stored; is Outcome.Unknown -> stored
                        }
                        is Outcome.Failure -> deck; is Outcome.Unavailable -> deck; is Outcome.Unknown -> deck
                    }
                }
                if (ticket != version) return@launch
                when (loaded) {
                    is PortResult.Failed -> mutable.value = mutable.value.copy(problem = loaded.problem)
                    is PortResult.Ok -> {
                        val (deck, stored) = loaded.value
                        if (!freshSources(deck, stored)) { mutable.value = mutable.value.copy(problem = Problem.STALE_REVISION); return@launch }
                        val estimated = callPort(dispatcher) { cancel ->
                            when (val result = allocator.allocate(deck, stored, policy, cancel)) {
                                is Outcome.Success -> if (validAllocation(result.value, deck, stored, policy, cancel)) result else corrupt()
                                is Outcome.Failure -> result; is Outcome.Unavailable -> result; is Outcome.Unknown -> result
                            }
                        }
                        if (ticket != version) return@launch
                        if (!freshSources(deck, stored)) { mutable.value = mutable.value.copy(problem = Problem.STALE_REVISION); return@launch }
                        val plan = (estimated as? PortResult.Ok)?.value
                        val rows = callPort(dispatcher) { cancel ->
                            mergeRows(deck, policy, plan, retained, cancel)
                        }
                        if (ticket != version) return@launch
                        if (!freshSources(deck, stored)) { mutable.value = mutable.value.copy(problem = Problem.STALE_REVISION); return@launch }
                        when (rows) {
                            is PortResult.Failed -> mutable.value = mutable.value.copy(problem = rows.problem)
                            is PortResult.Ok -> {
                                if (stored.revision.value > inventoryFloor.value) inventoryFloor = stored.revision
                                admitTime(stored.capturedAt)
                                if (deck.revision.value > (deckFloor?.second?.value ?: 0L)) deckFloor = deck.id to deck.revision
                                sourceReady = true
                                mutable.value = mutable.value.copy(deck = deck, inventory = stored, rows = rows.value,
                                    allocation = plan, estimateProblem = (estimated as? PortResult.Failed)?.problem, problem = null)
                            }
                        }
                    }
                }
            } finally { if (ticket == version) mutable.value = mutable.value.copy(loading = false) }
        }
    }

    private fun editable(): Boolean = lease == null && request == null && !mutable.value.loading
    private fun invalidate() {
        ++version; readJob?.cancel(); cancelFinishLookup()
        mutable.value = mutable.value.copy(preview = null, previewing = false, loading = false, issues = SnapshotList.empty(), problem = null)
    }
    fun select(id: EntityId, selected: Boolean) {
        if (!editable()) return
        val s = mutable.value
        if (s.rows.none { it.entry.id == id } || (selected && s.rows.count { it.selected && it.entry.id != id } >= InventoryProjectionLimits.MAX_EDITS)) {
            mutable.value = s.copy(problem = Problem.INVALID_INPUT); return
        }
        if (selected && s.rows.first { it.entry.id == id }.sourceProblem != null) { mutable.value = s.copy(problem = Problem.STALE_REVISION); return }
        invalidate()
        mutable.value = mutable.value.copy(rows = SnapshotList.copyOf(mutable.value.rows.map { if (it.entry.id == id) it.copy(selected = selected, problem = null) else it }))
    }
    /** Expected input fences an old whole-row callback from overwriting newer physical-copy input. */
    fun change(id: EntityId, expected: DeckMarkOwnedInput, value: DeckMarkOwnedInput) {
        if (!editable()) return
        val s = mutable.value
        val row = s.rows.firstOrNull { it.entry.id == id } ?: return
        if (row.input != expected) return
        invalidate()
        mutable.value = mutable.value.copy(rows = SnapshotList.copyOf(mutable.value.rows.map { if (it.entry.id == id) it.copy(input = value, problem = null) else it }))
    }
    /** Only an explicit clear-input confirmation may bind a changed identity. */
    fun acceptCurrentEntry(id: EntityId, expectedOldIdentity: CardIdentity, expectedCurrentEntry: DeckEntry, expectedInput: DeckMarkOwnedInput) {
        if (!editable()) return
        val s = mutable.value
        val row = s.rows.firstOrNull { it.entry.id == id } ?: return
        val replacement = row.currentEntry ?: return
        val deck = s.deck ?: return
        val stored = s.inventory ?: return
        if (row.sourceProblem == null || row.entry.identity != expectedOldIdentity || replacement != expectedCurrentEntry || row.input != expectedInput || replacement.board !in checkNotNull(s.policy).includeBoards ||
            !sourceReady || !freshSources(deck, stored) || deck.entries.none { it == replacement }) return
        invalidate()
        mutable.value = mutable.value.copy(rows = SnapshotList.copyOf(mutable.value.rows.map {
            if (it.entry.id == id) DeckMarkOwnedRow(replacement, initialInput(row.currentEstimate), estimate = row.currentEstimate) else it
        }))
    }
    /** Local metadata only, requested per row; lookup never changes physical input or allocates IDs. */
    fun loadFinishChoices(id: EntityId) {
        if (!editable() || !sourceReady) return
        val row = mutable.value.rows.firstOrNull { it.entry.id == id && it.sourceProblem == null } ?: return
        invalidate(); val ticket = version; val choice = ++finishTicket
        val active = activeGeneration()
        val printing = (row.entry.identity.printingId as? Knowledge.Known)?.value
        if (active == null || printing == null) {
            mutable.value = mutable.value.copy(rows = SnapshotList.copyOf(mutable.value.rows.map {
                if (it.entry.id == id) clearChoices(it).copy(finishProblem = if (printing == null) Problem.NOT_FOUND else Problem.STALE_GENERATION) else it
            }))
            return
        }
        mutable.value = mutable.value.copy(rows = SnapshotList.copyOf(mutable.value.rows.map {
            if (it.entry.id == id) clearChoices(it).copy(loadingFinishes = true) else it
        }))
        finishJob = scope.launch {
            try {
                val result = callPort(dispatcher) { catalog.printing(printing, it) }
                if (choice != finishTicket || ticket != version || lease != null) return@launch
                if (activeGeneration() != active) {
                    mutable.value = mutable.value.copy(rows = SnapshotList.copyOf(mutable.value.rows.map { if (it.entry.id == id) clearChoices(it).copy(finishProblem = Problem.STALE_GENERATION) else it }))
                    return@launch
                }
                val latest = mutable.value.rows.firstOrNull { it.entry.id == id } ?: return@launch
                if (latest.entry != row.entry || latest.sourceProblem != null) return@launch
                val choices = (result as? PortResult.Ok)?.value?.let { printingFinishes(row.entry.identity, it, active) }
                val problem = when { result is PortResult.Failed -> result.problem; choices == null -> Problem.STALE_GENERATION; choices.isEmpty() -> Problem.UNSUPPORTED; else -> null }
                mutable.value = mutable.value.copy(rows = SnapshotList.copyOf(mutable.value.rows.map {
                    if (it.entry.id == id) it.copy(supportedFinishes = choices ?: SnapshotList.empty(), finishProblem = problem, finishGeneration = active.takeIf { choices != null }) else it
                }))
            } finally {
                if (choice == finishTicket) mutable.value = mutable.value.copy(rows = SnapshotList.copyOf(mutable.value.rows.map { if (it.entry.id == id) it.copy(loadingFinishes = false) else it }))
            }
        }
    }
    private fun clearChoices(row: DeckMarkOwnedRow): DeckMarkOwnedRow = row.copy(supportedFinishes = SnapshotList.empty(), loadingFinishes = false, finishProblem = null, finishGeneration = null)
    private fun cancelFinishLookup() {
        ++finishTicket; finishJob?.cancel()
        mutable.value = mutable.value.copy(rows = SnapshotList.copyOf(mutable.value.rows.map { if (it.loadingFinishes) it.copy(loadingFinishes = false) else it }))
    }
    fun discardInput() {
        if (lease != null || request != null) return
        ++version; readJob?.cancel(); cancelFinishLookup()
        sourceReady = false
        mutable.value = DeckMarkOwnedState(formTicket = ++formSerial)
    }
    fun onActiveGeneration(value: GenerationId?) {
        notifiedGeneration = value; generationNotified = true
        if (request == null) {
            cancelFinishLookup()
            mutable.value = mutable.value.copy(rows = SnapshotList.copyOf(mutable.value.rows.map { if (it.finishGeneration != value) clearChoices(it) else it }))
        }
        if (request == null && mutable.value.preview?.rows?.any { it.input.finish is Knowledge.Known } == true && mutable.value.preview?.generation != value) {
            invalidate(); mutable.value = mutable.value.copy(problem = Problem.STALE_GENERATION)
        }
    }
    fun onDeckChanged(id: DeckId, revision: Revision) {
        if (mutable.value.deckId != id) return
        if (deckFloor?.first != id || revision.value > checkNotNull(deckFloor).second.value) deckFloor = id to revision
        val source = mutable.value.deck ?: return
        if (revision.value > source.revision.value && request == null) { sourceReady = false; invalidate(); mutable.value = mutable.value.copy(problem = Problem.STALE_REVISION) }
    }
    fun onInventoryChanged(revision: Revision) {
        if (revision.value > inventoryFloor.value) inventoryFloor = revision
        val source = mutable.value.inventory ?: return
        if (revision.value > source.revision.value && request == null) { sourceReady = false; invalidate(); mutable.value = mutable.value.copy(problem = Problem.STALE_REVISION) }
    }

    fun preview() {
        if (!editable()) return
        val s = mutable.value
        val deck = s.deck ?: return
        val stored = s.inventory ?: return
        val policy = s.policy ?: return
        if (!sourceReady || !freshSources(deck, stored)) { mutable.value = s.copy(problem = Problem.STALE_REVISION); return }
        val selected = s.rows.filter { it.selected }.map { it.copy(loadingFinishes = false) }
        if (selected.isEmpty() || selected.size > InventoryProjectionLimits.MAX_EDITS) { mutable.value = s.copy(problem = Problem.INVALID_INPUT); return }
        if (selected.any { it.sourceProblem != null }) { mutable.value = s.copy(problem = Problem.STALE_REVISION); return }
        invalidate(); val ticket = version
        val active = activeGeneration()
        mutable.value = mutable.value.copy(previewing = true)
        readJob = scope.launch {
            try {
                val rows = ArrayList<DeckMarkOwnedRow>()
                val records = HashMap<CardId, PortResult<PrintingRecord>>()
                for (row in selected) {
                    var problem: Problem? = if (quantity(row.input.quantity) == null || row.input.finish is Knowledge.Absent) Problem.INVALID_INPUT else null
                    if (row.input.finish is Knowledge.Known) {
                        val printing = (row.entry.identity.printingId as? Knowledge.Known)?.value
                        if (printing == null || active == null) problem = Problem.STALE_GENERATION
                        else {
                            val record = records.getOrPutSuspend(printing) { callPort(dispatcher) { catalog.printing(printing, it) } }
                            if (ticket != version) return@launch
                            if (activeGeneration() != active) { mutable.value = mutable.value.copy(problem = Problem.STALE_GENERATION); return@launch }
                            problem = when (record) { is PortResult.Failed -> record.problem; is PortResult.Ok -> if (finishProof(row, record.value, active)) problem else Problem.STALE_GENERATION }
                        }
                    }
                    rows.add(row.copy(problem = problem))
                }
                if (ticket != version) return@launch
                if (rows.any { it.problem != null }) {
                    val byId = rows.associateBy { it.entry.id }
                    mutable.value = mutable.value.copy(rows = SnapshotList.copyOf(mutable.value.rows.map { byId[it.entry.id] ?: it }), problem = Problem.INVALID_INPUT)
                    return@launch
                }
                // IDs belong to this explicit preview only; obsolete asynchronous callbacks allocate none.
                val ids = rows.map { nextEntityId() }
                if (ids.distinct().size != ids.size) { mutable.value = mutable.value.copy(problem = Problem.INVALID_INPUT); return@launch }
                val proposed = callPort(dispatcher) { cancel ->
                    val existing = stored.lots.mapTo(HashSet()) { cancel.throwIfCancelled(); it.id }
                    if (ids.any { it in existing }) return@callPort invalid()
                    val edits = rows.mapIndexed { index, row ->
                        cancel.throwIfCancelled()
                        CollectionEdit.Add(OwnedLot(ids[index], row.entry.identity, row.input.finish, row.input.condition, row.input.location,
                            checkNotNull(quantity(row.input.quantity)), row.input.notes, Revision(0), Knowledge.Absent("Acquisition time not supplied")))
                    }
                    val patch = CollectionPatch(SnapshotList.copyOf(edits))
                    when (val output = project(stored, patch, stored.revision, cancel)) {
                        is Outcome.Success -> if (validProjection(output.value, stored.revision)) Outcome.Success(DeckMarkOwnedPreview(deck, stored, policy, SnapshotList.copyOf(rows), patch, output.value, active)) else corrupt()
                        is Outcome.Failure -> output; is Outcome.Unavailable -> output; is Outcome.Unknown -> output
                    }
                }
                if (ticket != version) return@launch
                if (!freshSources(deck, stored)) { mutable.value = mutable.value.copy(problem = Problem.STALE_REVISION); return@launch }
                if (rows.any { it.input.finish is Knowledge.Known } && activeGeneration() != active) { mutable.value = mutable.value.copy(problem = Problem.STALE_GENERATION); return@launch }
                when (proposed) {
                    is PortResult.Failed -> mutable.value = mutable.value.copy(problem = proposed.problem)
                    is PortResult.Ok -> mutable.value = mutable.value.copy(preview = proposed.value, issues = proposed.value.projection.issues)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (ticket == version) mutable.value = mutable.value.copy(problem = Problem.INTERNAL) }
            finally { if (ticket == version) mutable.value = mutable.value.copy(previewing = false) }
        }
    }

    fun confirm() {
        if (lease != null || request != null || mutable.value.loading || mutable.value.previewing) return
        val candidate = mutable.value.preview ?: return
        if (candidate.projection.issues.isNotEmpty()) return
        val grant = mutationGate.tryAcquire() ?: run { mutable.value = mutable.value.copy(problem = Problem.PERSISTENCE); return }
        lease = grant
        mutable.value = mutable.value.copy(submitting = true, problem = null)
        val ticket = version
        mutationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val loaded = callPort(dispatcher) { cancel -> when (val result = decks.load(candidate.deck.id, cancel)) {
                    is Outcome.Success -> if (result.value == candidate.deck) result else stale()
                    is Outcome.Failure -> result; is Outcome.Unavailable -> result; is Outcome.Unknown -> result
                } }
                if (!owns(grant)) return@launch
                if (loaded is PortResult.Failed) { preflightFailed(loaded.problem, grant); return@launch }
                if (!current(candidate, ticket)) { preflightFailed(Problem.STALE_REVISION, grant); return@launch }
                val records = HashMap<CardId, PortResult<PrintingRecord>>()
                for (row in candidate.rows) if (row.input.finish is Knowledge.Known) {
                    val printing = (row.entry.identity.printingId as? Knowledge.Known)?.value
                    if (printing == null || candidate.generation == null || activeGeneration() != candidate.generation) { preflightFailed(Problem.STALE_GENERATION, grant); return@launch }
                    val record = records.getOrPutSuspend(printing) { callPort(dispatcher) { catalog.printing(printing, it) } }
                    if (!owns(grant)) return@launch
                    if (!current(candidate, ticket)) { preflightFailed(Problem.STALE_REVISION, grant); return@launch }
                    if (record is PortResult.Failed) { preflightFailed(record.problem, grant); return@launch }
                    if (activeGeneration() != candidate.generation || !finishProof(row, (record as PortResult.Ok).value, candidate.generation)) { preflightFailed(Problem.STALE_GENERATION, grant); return@launch }
                }
                if (records.isNotEmpty()) {
                    // Catalog IO may outlive an unnotified deck edit. The final independent
                    // deck read must still match the full admitted preview, never revision alone.
                    val finalDeck = callPort(dispatcher) { cancel -> when (val result = decks.load(candidate.deck.id, cancel)) {
                        is Outcome.Success -> if (result.value == candidate.deck) result else stale()
                        is Outcome.Failure -> result; is Outcome.Unavailable -> result; is Outcome.Unknown -> result
                    } }
                    if (!owns(grant)) return@launch
                    if (finalDeck is PortResult.Failed) { preflightFailed(finalDeck.problem, grant); return@launch }
                }
                if (!current(candidate, ticket)) { preflightFailed(Problem.STALE_REVISION, grant); return@launch }
                val frozen = Request(candidate, nextEntityId())
                if (candidate.rows.any { it.input.finish is Knowledge.Known } && activeGeneration() != candidate.generation) { preflightFailed(Problem.STALE_GENERATION, grant); return@launch }
                request = frozen
                perform(frozen, grant)
            } catch (e: CancellationException) {
                interrupted(Problem.UNKNOWN_RESULT, grant)
                throw e
            } catch (_: Exception) {
                interrupted(Problem.UNEXPECTED, grant)
            } finally { if (owns(grant)) mutable.value = mutable.value.copy(submitting = false) }
        }
    }
    fun retry() {
        if (mutationJob?.isActive == true) return
        val frozen = request
        val grant = lease
        if (frozen != null && grant != null && owns(grant)) {
            mutable.value = mutable.value.copy(submitting = true, problem = null)
            mutationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try { if (knownCommitted != null) readFresh(grant) else perform(frozen, grant) }
                catch (e: CancellationException) { interrupted(Problem.UNKNOWN_RESULT, grant); throw e }
                catch (_: Exception) { interrupted(Problem.UNEXPECTED, grant) }
                finally { if (owns(grant)) mutable.value = mutable.value.copy(submitting = false) }
            }
        } else refreshSources()
    }
    private fun current(p: DeckMarkOwnedPreview, ticket: Long): Boolean = sourceReady && version == ticket && mutable.value.preview === p && freshSources(p.deck, p.inventory)
    private fun owns(grant: InventoryMutationGate.Lease): Boolean = lease === grant && grant.owns
    private fun preflightFailed(problem: Problem, grant: InventoryMutationGate.Lease) {
        if (!owns(grant)) return
        if (problem == Problem.STALE_REVISION) sourceReady = false
        mutable.value = mutable.value.copy(submitting = false, preview = null, issues = SnapshotList.empty(), problem = problem)
        lease = null; grant.release()
    }
    private fun interrupted(problem: Problem, grant: InventoryMutationGate.Lease) {
        if (!owns(grant)) return
        if (request == null) preflightFailed(problem, grant)
        else if (knownCommitted != null) mutable.value = mutable.value.copy(awaitingFresh = true, uncertain = false, problem = problem)
        else unresolved(problem)
    }
    private fun unresolved(problem: Problem) { mutable.value = mutable.value.copy(uncertain = true, problem = problem) }
    private suspend fun perform(frozen: Request, grant: InventoryMutationGate.Lease) {
        val result = callPort(dispatcher) { inventory.apply(frozen.preview.patch, frozen.preview.inventory.revision, frozen.operation, it) }
        if (!owns(grant) || request !== frozen) return
        when (result) {
            is PortResult.Failed -> if (result.problem == Problem.STALE_REVISION || result.problem == Problem.INVALID_INPUT) resolvedRejection(result.problem, SnapshotList.empty(), grant) else unresolved(result.problem)
            is PortResult.Ok -> when (val applied = result.value) {
                is InventoryApplyResult.Rejected -> if (validRejection(applied, frozen)) resolvedRejection(Problem.INVALID_INPUT, applied.issues, grant) else unresolved(Problem.CORRUPT_DATA)
                is InventoryApplyResult.Committed -> {
                    val commit = applied.commit
                    val p = frozen.preview
                    if (p.inventory.revision.value == Long.MAX_VALUE || commit.projection != p.projection || commit.projection.issues.isNotEmpty() ||
                        commit.projection.sourceRevision != p.inventory.revision || commit.projection.resultRevision.value != p.inventory.revision.value + 1 ||
                        compareTime(commit.modifiedAt, p.inventory.capturedAt) < 0) { unresolved(Problem.CORRUPT_DATA); return }
                    knownCommitted = commit
                    mutable.value = mutable.value.copy(uncertain = false, awaitingFresh = true, problem = null)
                    readFresh(grant)
                }
            }
        }
    }
    private fun resolvedRejection(problem: Problem, issues: SnapshotList<DomainIssue>, grant: InventoryMutationGate.Lease) {
        if (problem == Problem.STALE_REVISION) sourceReady = false
        request = null; knownCommitted = null; lease = null; grant.release()
        mutable.value = mutable.value.copy(submitting = false, uncertain = false, awaitingFresh = false, preview = null, problem = problem, issues = issues)
    }
    private suspend fun readFresh(grant: InventoryMutationGate.Lease) {
        val committed = knownCommitted ?: return
        val read = callPort(dispatcher) { cancel -> when (val result = inventory.snapshot(cancel)) {
            is Outcome.Success -> checkedSnapshot(result.value, cancel)
            is Outcome.Failure -> result; is Outcome.Unavailable -> result; is Outcome.Unknown -> result
        } }
        if (!owns(grant) || knownCommitted !== committed) return
        when (read) {
            is PortResult.Failed -> mutable.value = mutable.value.copy(awaitingFresh = true, uncertain = false, problem = read.problem)
            is PortResult.Ok -> {
                val fresh = read.value
                if (fresh.revision.value < committed.projection.resultRevision.value || fresh.revision.value < inventoryFloor.value ||
                    compareTime(fresh.capturedAt, committed.modifiedAt) < 0 || inventoryTimeFloor?.let { compareTime(fresh.capturedAt, it) < 0 } == true) {
                    mutable.value = mutable.value.copy(awaitingFresh = true, uncertain = false, problem = Problem.STALE_REVISION); return
                }
                if (fresh.revision.value > inventoryFloor.value) inventoryFloor = fresh.revision
                admitTime(fresh.capturedAt)
                request = null; knownCommitted = null; lease = null
                mutable.value = DeckMarkOwnedState(formTicket = ++formSerial, inventory = fresh, savedRevision = committed.projection.resultRevision)
                grant.release()
                try { onCommitted(fresh) } catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.value = mutable.value.copy(problem = Problem.INTERNAL) }
            }
        }
    }

    private fun activeGeneration(): GenerationId? = try {
        val current = currentCatalogGeneration()
        if (generationNotified && notifiedGeneration != current) null else current
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    private fun freshSources(deck: DeckSnapshot, stored: InventorySnapshot): Boolean =
        deck.revision.value >= (deckFloor?.takeIf { it.first == deck.id }?.second?.value ?: 0L) && stored.revision.value >= inventoryFloor.value &&
            inventoryTimeFloor?.let { compareTime(stored.capturedAt, it) >= 0 } != false
    private fun quantity(value: String): Int? = value.takeIf { it.length in 1..10 && it.all { c -> c in '0'..'9' } }?.toIntOrNull()?.takeIf { it > 0 }
    private fun initialInput(demand: OwnedQuantityDemand?): DeckMarkOwnedInput = DeckMarkOwnedInput(quantity = demand?.takeIf { it.complete }?.remainingQuantity?.toString() ?: "")
    private fun finishProof(row: DeckMarkOwnedRow, printing: PrintingRecord, active: GenerationId): Boolean {
        val finish = (row.input.finish as? Knowledge.Known)?.value ?: return false
        val supported = printingFinishes(row.entry.identity, printing, active) ?: return false
        return finish in supported
    }
    private fun printingFinishes(identity: CardIdentity, printing: PrintingRecord, active: GenerationId): SnapshotList<Finish>? {
        val id = (identity.printingId as? Knowledge.Known)?.value ?: return null
        val oracle = (identity.oracleId as? Knowledge.Known)?.value
        val finishes = (printing.finishes as? Knowledge.Known)?.value ?: return null
        if (printing.id != id || printing.language != identity.language || (oracle != null && printing.oracleId != Knowledge.Known(oracle)) ||
            printing.provenance.provider != id.source || printing.provenance.generation != active ||
            printing.provenance.notice?.provider?.let { it != id.source } == true ||
            finishes.size > Finish.entries.size || finishes.distinct().size != finishes.size) return null
        return finishes
    }
    private fun admitTime(value: UtcTimestamp) {
        if (inventoryTimeFloor?.let { compareTime(value, it) > 0 } != false) inventoryTimeFloor = value
    }
    private fun validPolicy(policy: OwnedQuantityPolicy): Boolean = policy.ownership != OwnershipPolicy.IGNORE &&
        policy.includeBoards.isNotEmpty() && policy.includeBoards.size <= BoardRole.entries.size && policy.includeBoards.distinct().size == policy.includeBoards.size &&
        policy.pins.size <= OwnedQuantityLimits.MAX_ENTRIES && policy.pins.map { it.entryId }.distinct().size == policy.pins.size
    private fun validDeck(deck: DeckSnapshot, id: DeckId, policy: OwnedQuantityPolicy): Boolean {
        if (deck.id != id || deck.entries.size > OwnedQuantityLimits.MAX_ENTRIES || deck.entries.map { it.id }.distinct().size != deck.entries.size) return false
        val entries = deck.entries.associateBy { it.id }
        return policy.pins.all { pin -> entries[pin.entryId]?.let { entry ->
            val finish = entry.finish
            entry.board in policy.includeBoards && entry.identity.printingId == Knowledge.Known(pin.printingId) && (finish !is Knowledge.Known || finish.value == pin.finish)
        } == true }
    }
    private fun validProjection(p: InventoryProjection, expected: Revision): Boolean = p.sourceRevision == expected &&
        p.changes.size <= InventoryProjectionLimits.MAX_EDITS && p.merges.size <= InventoryProjectionLimits.MAX_EDITS && p.issues.size <= InventoryProjectionLimits.MAX_ISSUES &&
        expected.value < Long.MAX_VALUE && p.resultRevision.value == expected.value + 1 &&
        (p.issues.isEmpty() || (p.changes.isEmpty() && p.merges.isEmpty()))
    private fun checkedSnapshot(value: InventorySnapshot, cancel: CancelToken): Outcome<InventorySnapshot> {
        if (!value.complete || value.lots.size > InventoryProjectionLimits.MAX_LOTS) return corrupt()
        return when (val checked = project(value, CollectionPatch(SnapshotList.empty()), value.revision, cancel)) {
            is Outcome.Success -> if (checked.value == InventoryProjection(value.revision, value.revision, SnapshotList.empty(), SnapshotList.empty(), SnapshotList.empty())) Outcome.Success(value) else corrupt()
            is Outcome.Failure -> checked; is Outcome.Unavailable -> checked; is Outcome.Unknown -> checked
        }
    }
    private fun mergeRows(deck: DeckSnapshot, policy: OwnedQuantityPolicy, plan: OwnedQuantityPlan?, old: SnapshotList<DeckMarkOwnedRow>?, cancel: CancelToken): Outcome<SnapshotList<DeckMarkOwnedRow>> {
        val included = deck.entries.filter { cancel.throwIfCancelled(); it.board in policy.includeBoards }.associateBy { it.id }
        val demands = plan?.demands?.associateBy { cancel.throwIfCancelled(); it.entryId }.orEmpty()
        if (old == null) return Outcome.Success(SnapshotList.copyOf(included.values.map { cancel.throwIfCancelled(); DeckMarkOwnedRow(it, initialInput(demands[it.id]), estimate = demands[it.id]) }))
        val seen = HashSet<EntityId>(); val rows = ArrayList<DeckMarkOwnedRow>()
        for (row in old) {
            cancel.throwIfCancelled(); seen.add(row.entry.id)
            val current = included[row.entry.id]; val estimate = demands[row.entry.id]
            rows.add(if (current != null && current.identity == row.entry.identity) clearChoices(row).copy(entry = current, estimate = estimate, currentEntry = current, currentEstimate = estimate, sourceProblem = null, problem = null)
                else clearChoices(row).copy(estimate = null, currentEntry = current, currentEstimate = estimate, sourceProblem = Problem.STALE_REVISION, problem = null))
        }
        for ((id,entry) in included) {
            cancel.throwIfCancelled()
            if (id !in seen) {
                if (rows.size >= OwnedQuantityLimits.MAX_ENTRIES) return invalid()
                rows.add(DeckMarkOwnedRow(entry, initialInput(demands[id]), estimate = demands[id]))
            }
        }
        return Outcome.Success(SnapshotList.copyOf(rows))
    }
    private fun validAllocation(plan: OwnedQuantityPlan, deck: DeckSnapshot, stored: InventorySnapshot, policy: OwnedQuantityPolicy, cancel: CancelToken): Boolean {
        if (plan.deckId != deck.id || plan.deckRevision != deck.revision || plan.inventoryRevision != stored.revision || plan.policy.ownership != policy.ownership ||
            plan.policy.includeBoards != SnapshotList.copyOf(policy.includeBoards.sortedBy { it.name }) ||
            plan.policy.pins != SnapshotList.copyOf(policy.pins.sortedWith { a,b -> utf8Order(a.entryId.value,b.entryId.value) }) ||
            plan.demands.size > OwnedQuantityLimits.MAX_ENTRIES || plan.allocations.size > OwnedQuantityLimits.MAX_ALLOCATIONS || plan.incompleteEntryIds.size > OwnedQuantityLimits.MAX_ENTRIES) return false
        val included = deck.entries.filter { cancel.throwIfCancelled(); it.board in policy.includeBoards }.associateBy { it.id }
        if (plan.demands.size != included.size || plan.demands.map { it.entryId }.distinct().size != plan.demands.size) return false
        val demands = plan.demands.associateBy { it.entryId }
        if (plan.demands.any { cancel.throwIfCancelled(); included[it.entryId]?.quantity != it.requestedQuantity }) return false
        if (plan.incompleteEntryIds.distinct().size != plan.incompleteEntryIds.size || plan.incompleteEntryIds.toSet() != plan.demands.filter { !it.complete }.map { it.entryId }.toSet() || plan.complete != plan.incompleteEntryIds.isEmpty()) return false
        val lots = stored.lots.associateBy { cancel.throwIfCancelled(); it.id }
        val byEntry = HashMap<EntityId,Long>(); val byLot = HashMap<EntityId,Long>(); val edges = HashSet<Pair<EntityId,EntityId>>()
        for (a in plan.allocations) {
            cancel.throwIfCancelled()
            if (a.entryId !in included || a.lotId !in lots || !edges.add(a.entryId to a.lotId)) return false
            byEntry[a.entryId] = (byEntry[a.entryId] ?: 0L) + a.quantity
            byLot[a.lotId] = (byLot[a.lotId] ?: 0L) + a.quantity
        }
        return demands.all { (id,d) -> (byEntry[id] ?: 0L) == d.ownedQuantity.toLong() } && byLot.all { (id,n) -> n <= checkNotNull(lots[id]).quantity }
    }
    private fun validRejection(value: InventoryApplyResult.Rejected, frozen: Request): Boolean {
        val ids = frozen.preview.patch.edits.map { (it as CollectionEdit.Add).lot.id }.toSet()
        val codes = setOf(InventoryIssueCodes.DUPLICATE_SELECTION,InventoryIssueCodes.LOT_ALREADY_EXISTS,InventoryIssueCodes.LOT_NOT_FOUND,InventoryIssueCodes.STALE_LOT,
            InventoryIssueCodes.INVALID_ADD_REVISION,InventoryIssueCodes.QUANTITY_RANGE,InventoryIssueCodes.MERGE_TARGET_SELECTED,InventoryIssueCodes.QUANTITY_OVERFLOW)
        return value.actualRevision == frozen.preview.inventory.revision && value.issues.isNotEmpty() && value.issues.size <= InventoryProjectionLimits.MAX_ISSUES &&
            value.issues.all { it.entryId in ids && it.code in codes && it.severity == IssueSeverity.ERROR && it.evidence.isEmpty() } &&
            value.issues.map { it.entryId to it.code }.distinct().size == value.issues.size &&
            value.issues.zipWithNext().all { (a,b) ->
                val order = utf8Order(checkNotNull(a.entryId).value, checkNotNull(b.entryId).value)
                order < 0 || (order == 0 && a.code < b.code)
            }
    }
    private fun utf8Order(a: String, b: String): Int {
        val x = a.toByteArray(Charsets.UTF_8); val y = b.toByteArray(Charsets.UTF_8)
        for (i in 0 until minOf(x.size,y.size)) { val result = (x[i].toInt() and 255).compareTo(y[i].toInt() and 255); if (result != 0) return result }
        return x.size.compareTo(y.size)
    }
    private fun compareTime(a: UtcTimestamp, b: UtcTimestamp): Int = a.epochSeconds.compareTo(b.epochSeconds).takeIf { it != 0 } ?: a.nanoAdjustment.compareTo(b.nanoAdjustment)
    private suspend fun <K,V> MutableMap<K,V>.getOrPutSuspend(key: K, value: suspend () -> V): V = this[key] ?: value().also { this[key] = it }
    private fun invalid(): Outcome<Nothing> = Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT,"Invalid additional-copy input",SnapshotList.empty()))
    private fun stale(): Outcome<Nothing> = Outcome.Failure(DomainFailure(FailureCode.STALE_REVISION,"Stored deck changed",SnapshotList.empty()))
    private fun corrupt(): Outcome<Nothing> = Outcome.Failure(DomainFailure(FailureCode.CORRUPT_DATA,"Invalid additional-copy response",SnapshotList.empty()))
}
