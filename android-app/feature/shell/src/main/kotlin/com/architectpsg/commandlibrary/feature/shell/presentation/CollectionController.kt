package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Retained by a ViewModel. Only explicit commands freeze IDs or submit mutations. */
internal class CollectionController(
    private val scope: CoroutineScope,
    private val inventory: LocalInventory,
    private val decks: LocalDeckLibrary,
    private val allocator: OwnedQuantityAllocator,
    private val catalog: CatalogPrintingLookup,
    private val project: (InventorySnapshot, CollectionPatch, Revision, CancelToken) -> Outcome<InventoryProjection>,
    private val dispatcher: CoroutineDispatcher,
    private val nextEntityId: () -> EntityId,
    private val currentCatalogGeneration: () -> GenerationId? = { null },
) {
    private data class Preview(val form: CollectionForm, val patch: CollectionPatch, val projection: InventoryProjection)
    private sealed interface Request {
        val expected: Revision
        val operation: EntityId
        data class Apply(val preview: Preview, override val operation: EntityId) : Request { override val expected = preview.form.base.revision }
        data class Bulk(val preview: CollectionBulkPreview, override val operation: EntityId) : Request { override val expected = preview.form.base.revision }
        data class Undo(override val expected: Revision, override val operation: EntityId) : Request
    }
    private val mutable = MutableStateFlow(CollectionUiState())
    val state: StateFlow<CollectionUiState> = mutable.asStateFlow()
    private var indexedInventory: InventorySnapshot? = null
    private var inventoryPositions: Map<EntityId,Int> = emptyMap()
    private var bulkPositions: Map<EntityId,Int> = emptyMap()
    private var bulkGeneration = 0L
    private var bulkJob: Job? = null
    private var bulkProofGeneration: GenerationId? = null
    private var readGeneration = 0L
    private var formGeneration = 0L
    private var deckGeneration = 0L
    private var allocationGeneration = 0L
    private var selectionGeneration = 0L
    private var requestSelectionGeneration = 0L
    private var readJob: Job? = null
    private var formJob: Job? = null
    private var deckJob: Job? = null
    private var allocationJob: Job? = null
    private var preview: Preview? = null
    private var request: Request? = null
    private var uncertain = false
    private var observedDeckRevision: Revision? = null
    private var allocationDeckRevision: Revision? = null

    fun ensureLoaded() {
        if (mutable.value.inventory == null && !mutable.value.loading) refresh()
        if (mutable.value.deckEpoch == null && !mutable.value.loadingDecks) refreshDecks()
    }
    private fun validSnapshot(value: InventorySnapshot): Boolean = value.complete && value.lots.size <= InventoryProjectionLimits.MAX_LOTS
    private fun invalidateAllocation() {
        ++allocationGeneration; allocationJob?.cancel(); allocationDeckRevision = null
        mutable.update { it.copy(allocation = null, allocating = false, allocationProblem = null) }
    }
    fun refresh() {
        readJob?.cancel(); val generation = ++readGeneration
        invalidateAllocation()
        mutable.update { it.copy(loading = true, loadProblem = null) }
        readJob = scope.launch {
            try {
                val result: PortResult<Pair<InventorySnapshot,Map<EntityId,Int>>> = callPort(dispatcher) { token -> when (val loaded = inventory.snapshot(token)) {
                    is Outcome.Success -> Outcome.Success(loaded.value to if (validSnapshot(loaded.value)) indexLots(loaded.value,token) else emptyMap())
                    is Outcome.Failure -> loaded; is Outcome.Unknown -> loaded; is Outcome.Unavailable -> loaded
                } }
                currentCoroutineContext().ensureActive()
                if (generation != readGeneration) return@launch
                when (result) {
                    is PortResult.Failed -> mutable.update { it.copy(loadProblem = result.problem) }
                    is PortResult.Ok -> {
                        val snapshot = result.value.first
                        if (!validSnapshot(snapshot)) mutable.update { it.copy(loadProblem = Problem.CORRUPT_DATA) }
                        else if (snapshot.revision.value < (mutable.value.inventory?.revision?.value ?: 0L)) mutable.update { it.copy(loadProblem = Problem.STALE_REVISION) }
                        else {
                            indexedInventory = snapshot; inventoryPositions = result.value.second
                            if (preview?.form?.base?.revision != snapshot.revision) { preview = null; mutable.update { it.copy(preview = null) } }
                            if (mutable.value.bulkForm?.base?.revision?.let { it != snapshot.revision } == true) invalidateBulkPreview()
                            mutable.update { s -> s.copy(inventory = snapshot, selectedLotId = s.selectedLotId?.takeIf { id -> snapshot.lots.any { it.id == id } }) }
                        }
                    }
                }
            } finally { if (generation == readGeneration) mutable.update { it.copy(loading = false) } }
        }
    }
    fun selectLot(id: EntityId?) {
        ++selectionGeneration
        mutable.update { it.copy(selectedLotId = id) }
    }
    private fun canOpen(): Boolean = request == null && !mutable.value.mutationBusy && !mutable.value.previewing && !mutable.value.checkingCatalog && mutable.value.form == null && mutable.value.bulkForm == null
    fun beginEdit(id: EntityId, remove: Boolean = false) {
        if (!canOpen()) return
        val base = mutable.value.inventory ?: return
        val lot = base.lots.firstOrNull { it.id == id } ?: return
        ++formGeneration; preview = null; selectLot(id)
        mutable.update { it.copy(form = CollectionForm(if (remove) CollectionFormMode.REMOVE else CollectionFormMode.EDIT, base, lot),
            preview = null, invalidForm = false, formProblem = null, issues = SnapshotList.empty(), savedRevision = null) }
    }
    /** The root passes an actual current detail record, never a manually entered printing ID. */
    fun beginAdd(record: PrintingRecord) {
        if (!canOpen()) return
        val generation = ++formGeneration
        mutable.update { it.copy(checkingCatalog = true, formProblem = null, savedRevision = null) }
        formJob = scope.launch {
            try {
                val base = mutable.value.inventory ?: run {
                    val loaded = callPort(dispatcher) { inventory.snapshot(it) }
                    currentCoroutineContext().ensureActive()
                    if (generation != formGeneration) return@launch
                    when (loaded) {
                        is PortResult.Failed -> { mutable.update { it.copy(formProblem = loaded.problem) }; return@launch }
                        is PortResult.Ok -> {
                            if (!validSnapshot(loaded.value)) { mutable.update { it.copy(formProblem = Problem.CORRUPT_DATA) }; return@launch }
                            val latest = mutable.value.inventory
                            val snapshot = if (latest != null && latest.revision.value > loaded.value.revision.value) latest else loaded.value
                            mutable.update { it.copy(inventory = snapshot) }
                            snapshot
                        }
                    }
                }
                val result = callPort(dispatcher) { catalog.printing(record.id, it) }
                currentCoroutineContext().ensureActive()
                if (generation != formGeneration) return@launch
                when (result) {
                    is PortResult.Failed -> mutable.update { it.copy(formProblem = result.problem) }
                    is PortResult.Ok -> {
                        val current = result.value
                        val finishes = (current.finishes as? Knowledge.Known)?.value
                        if (!samePrinting(record, current)) mutable.update { it.copy(formProblem = Problem.STALE_GENERATION) }
                        else if (finishes.isNullOrEmpty()) mutable.update { it.copy(formProblem = Problem.UNSUPPORTED) }
                        else if (finishes.size > Finish.entries.size) mutable.update { it.copy(formProblem = Problem.CORRUPT_DATA) }
                        else try {
                            val newId = nextEntityId()
                            mutable.update { it.copy(form = CollectionForm(CollectionFormMode.ADD, mutable.value.inventory?.takeIf { fresh -> fresh.revision.value >= base.revision.value } ?: base, printing = current, newLotId = newId,
                                supportedFinishes = SnapshotList.copyOf(finishes.distinct())), preview = null, invalidForm = false, issues = SnapshotList.empty()) }
                        } catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.update { it.copy(formProblem = Problem.INTERNAL) } }
                    }
                }
            } finally { if (generation == formGeneration) mutable.update { it.copy(checkingCatalog = false) } }
        }
    }
    private fun samePrinting(a: PrintingRecord, b: PrintingRecord): Boolean = a.id == b.id && a.language == b.language && a.oracleId == b.oracleId
    private fun change(transform: (CollectionForm) -> CollectionForm) {
        if (request != null || mutable.value.mutationBusy) return
        ++formGeneration; formJob?.cancel(); preview = null
        mutable.update { it.copy(form = it.form?.let(transform)?.copy(dirty = true), preview = null, previewing = false,
            invalidForm = false, formProblem = null, issues = SnapshotList.empty()) }
    }
    fun quantity(value: String) = change { it.copy(quantity = value) }
    fun condition(value: CardCondition) = change { it.copy(condition = value) }
    fun location(value: String) = change { it.copy(location = value) }
    fun notes(value: String) = change { it.copy(notes = value) }
    fun finish(value: Finish) = change { if (it.mode == CollectionFormMode.ADD && value in it.supportedFinishes) it.copy(finish = value) else it }
    fun requestCloseForm() {
        if (mutable.value.mutationBusy) return
        if (request != null) { mutable.update { it.copy(form = null, preview = null, discardPrompt = false) }; return }
        if (mutable.value.form?.dirty == true) mutable.update { it.copy(discardPrompt = true) } else discardForm()
    }
    fun keepForm() { mutable.update { it.copy(discardPrompt = false) } }
    fun discardForm() {
        if (request != null || mutable.value.mutationBusy) return
        ++formGeneration; formJob?.cancel(); preview = null
        mutable.update { it.copy(form = null, preview = null, previewing = false, checkingCatalog = false,
            discardPrompt = false, invalidForm = false, formProblem = null, issues = SnapshotList.empty()) }
    }
    /** Explicitly preserve the draft values while obtaining a new before-image for preview. */
    fun useRefreshedInventory() {
        if (request != null || mutable.value.mutationBusy) return
        val form = mutable.value.form ?: return
        val snapshot = mutable.value.inventory ?: return
        val original = form.original?.let { old -> snapshot.lots.firstOrNull { it.id == old.id } }
        if (form.original != null && original == null) { mutable.update { it.copy(formProblem = Problem.NOT_FOUND) }; return }
        ++formGeneration; formJob?.cancel(); preview = null
        mutable.update { it.copy(form = form.copy(base = snapshot, original = original), preview = null, previewing = false, formProblem = null, issues = SnapshotList.empty()) }
    }
    fun cancelPreview() {
        if (request != null || mutable.value.mutationBusy) return
        ++formGeneration; formJob?.cancel(); preview = null
        mutable.update { it.copy(preview = null, previewing = false, issues = SnapshotList.empty()) }
    }
    fun previewEdit() {
        if (request != null || mutable.value.mutationBusy || mutable.value.previewing) return
        val form = mutable.value.form ?: return
        if (mutable.value.inventory?.revision != form.base.revision) { mutable.update { it.copy(formProblem = Problem.STALE_REVISION) }; return }
        val quantity = form.quantity.takeIf { it.length <= 10 }?.toIntOrNull()
        if (form.mode != CollectionFormMode.REMOVE && (quantity == null || quantity < 0 || (form.mode == CollectionFormMode.ADD && form.finish == null))) {
            mutable.update { it.copy(invalidForm = true) }; return
        }
        val generation = ++formGeneration
        mutable.update { it.copy(previewing = true, invalidForm = false, formProblem = null, issues = SnapshotList.empty(), preview = null) }
        formJob = scope.launch {
            try {
                val result = callPort(dispatcher) { cancel ->
                    val edit = when (form.mode) {
                        CollectionFormMode.REMOVE -> CollectionEdit.Remove(checkNotNull(form.original).id, "Explicit collection removal")
                        CollectionFormMode.EDIT -> CollectionEdit.Replace(checkNotNull(form.original).copy(quantity = checkNotNull(quantity), condition = form.condition, location = form.location, notes = form.notes))
                        CollectionFormMode.ADD -> {
                            val printing = checkNotNull(form.printing)
                            val identity = CardIdentity(printing.oracleId, Knowledge.Known(printing.id), null, Knowledge.Absent("Printing record supplies no original name"), printing.language)
                            CollectionEdit.Add(OwnedLot(checkNotNull(form.newLotId), identity, Knowledge.Known(checkNotNull(form.finish)), form.condition,
                                form.location, checkNotNull(quantity), form.notes, Revision(0), Knowledge.Absent("Acquisition time not recorded")))
                        }
                    }
                    val patch = CollectionPatch(SnapshotList.of(edit))
                    when (val proposed = project(form.base, patch, form.base.revision, cancel)) {
                        is Outcome.Success -> Outcome.Success(Preview(form, patch, proposed.value))
                        is Outcome.Failure -> proposed
                        is Outcome.Unavailable -> proposed
                        is Outcome.Unknown -> proposed
                    }
                }
                currentCoroutineContext().ensureActive()
                if (generation != formGeneration) return@launch
                when (result) {
                    is PortResult.Failed -> mutable.update { it.copy(formProblem = result.problem) }
                    is PortResult.Ok -> {
                        val candidate = result.value
                        if (mutable.value.inventory?.revision != form.base.revision) {
                            mutable.update { it.copy(formProblem = Problem.STALE_REVISION, preview = null) }; return@launch
                        }
                        if (candidate.projection.sourceRevision != form.base.revision || candidate.projection.issues.size > InventoryProjectionLimits.MAX_ISSUES) mutable.update { it.copy(formProblem = Problem.CORRUPT_DATA) }
                        else { preview = candidate; mutable.update { it.copy(preview = candidate.projection, issues = candidate.projection.issues) } }
                    }
                }
            } finally { if (generation == formGeneration) mutable.update { it.copy(previewing = false) } }
        }
    }
    fun confirmPreview() {
        if (request != null || mutable.value.mutationBusy) return
        val frozen = preview ?: return
        if (frozen.projection.issues.isNotEmpty()) return
        if (mutable.value.inventory?.revision != frozen.form.base.revision) { mutable.update { it.copy(formProblem = Problem.STALE_REVISION) }; return }
        val generation = formGeneration
        val confirmSelectionGeneration = selectionGeneration
        mutable.update { it.copy(mutationBusy = true, formProblem = null) }
        scope.launch {
            try {
                if (frozen.form.mode == CollectionFormMode.ADD) {
                    val record = checkNotNull(frozen.form.printing)
                    val result = callPort(dispatcher) { catalog.printing(record.id, it) }
                    currentCoroutineContext().ensureActive()
                    if (generation != formGeneration) return@launch
                    when (result) {
                        is PortResult.Failed -> { mutable.update { it.copy(formProblem = result.problem) }; return@launch }
                        is PortResult.Ok -> {
                            val current = result.value
                            val finishes = (current.finishes as? Knowledge.Known)?.value
                            if (!samePrinting(record, current) || finishes == null || frozen.form.finish !in finishes) {
                                mutable.update { it.copy(formProblem = Problem.STALE_GENERATION) }; return@launch
                            }
                        }
                    }
                }
                if (mutable.value.inventory?.revision != frozen.form.base.revision || preview !== frozen) {
                    mutable.update { it.copy(formProblem = Problem.STALE_REVISION) }; return@launch
                }
                try { request = Request.Apply(frozen, nextEntityId()) }
                catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.update { it.copy(mutationProblem = Problem.INTERNAL) }; return@launch }
                requestSelectionGeneration = confirmSelectionGeneration
                invalidateAllocation(); ++readGeneration; readJob?.cancel()
                perform(checkNotNull(request))
            } finally { mutable.update { it.copy(mutationBusy = false) } }
        }
    }
    fun undo() {
        if (request != null || mutable.value.mutationBusy || mutable.value.form != null || mutable.value.bulkForm != null) return
        val snapshot = mutable.value.inventory ?: return
        if (snapshot.revision.value == Long.MAX_VALUE) { mutable.update { it.copy(mutationProblem = Problem.PERSISTENCE) }; return }
        try { request = Request.Undo(snapshot.revision, nextEntityId()) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.update { it.copy(mutationProblem = Problem.INTERNAL) }; return }
        requestSelectionGeneration = selectionGeneration
        attempt()
    }
    fun retry() { if (request != null && !mutable.value.mutationBusy) attempt() }
    private fun attempt() {
        val frozen = request ?: return
        mutable.update { it.copy(mutationBusy = true) }
        invalidateAllocation(); ++readGeneration; readJob?.cancel()
        scope.launch { try { perform(frozen) } finally { mutable.update { it.copy(mutationBusy = false) } } }
    }
    private suspend fun perform(frozen: Request) {
        mutable.update { it.copy(pending = true, mutationProblem = null, savedRevision = null, loading = false) }
        try {
            val result: PortResult<InventoryApplyResult> = callPort(dispatcher) { cancel -> when (frozen) {
                is Request.Apply -> inventory.apply(frozen.preview.patch, frozen.expected, frozen.operation, cancel)
                is Request.Bulk -> inventory.apply(checkNotNull(frozen.preview.patch), frozen.expected, frozen.operation, cancel)
                is Request.Undo -> when (val undone = inventory.undo(frozen.expected, frozen.operation, cancel)) {
                    is Outcome.Success -> Outcome.Success(InventoryApplyResult.Committed(undone.value))
                    is Outcome.Failure -> undone
                    is Outcome.Unknown -> undone
                    is Outcome.Unavailable -> undone
                }
            } }
            currentCoroutineContext().ensureActive()
            when (result) {
                is PortResult.Failed -> {
                    uncertain = uncertain || result.problem in setOf(Problem.PERSISTENCE, Problem.INTERNAL, Problem.TRANSPORT, Problem.UNKNOWN_RESULT, Problem.UNEXPECTED)
                    if (!uncertain) { request = null; if (frozen is Request.Bulk && result.problem == Problem.STALE_REVISION) invalidateBulkPreview(false) }
                    mutable.update { it.copy(pending = uncertain, uncertain = uncertain, mutationProblem = result.problem) }
                }
                is PortResult.Ok -> when (val applied = result.value) {
                    is InventoryApplyResult.Rejected -> {
                        if (!uncertain) request = null
                        preview = null
                        invalidateBulkPreview(false)
                        mutable.update { it.copy(pending = uncertain, uncertain = uncertain, preview = null, issues = applied.issues, mutationProblem = null) }
                    }
                    is InventoryApplyResult.Committed -> {
                        val projection = applied.commit.projection
                        val valid = frozen.expected.value < Long.MAX_VALUE && projection.sourceRevision == frozen.expected &&
                            projection.resultRevision.value == frozen.expected.value + 1 && projection.issues.isEmpty() &&
                            projection.changes.size <= InventoryProjectionLimits.MAX_EDITS && projection.merges.size <= InventoryProjectionLimits.MAX_EDITS &&
                            (frozen !is Request.Bulk || projection == frozen.preview.projection)
                        if (!valid) {
                            uncertain = true
                            mutable.update { it.copy(pending = true, uncertain = true, mutationProblem = Problem.CORRUPT_DATA) }
                        } else {
                            request = null; uncertain = false; preview = null
                            val survivor = if (selectionGeneration == requestSelectionGeneration && frozen is Request.Apply && frozen.preview.form.mode == CollectionFormMode.ADD) projection.changes.firstOrNull()?.lotId else mutable.value.selectedLotId
                            invalidateBulkPreview(false)
                            mutable.update { it.copy(bulkForm = if (frozen is Request.Bulk) null else it.bulkForm, bulkSelectionBase = if (frozen is Request.Bulk) null else it.bulkSelectionBase, bulkIds = if (frozen is Request.Bulk) SnapshotList.empty() else it.bulkIds, bulkAnchor = if (frozen is Request.Bulk) null else it.bulkAnchor, bulkDiscardPrompt = false, pending = false, uncertain = false, form = null, preview = null, issues = SnapshotList.empty(),
                                selectedLotId = survivor, savedRevision = projection.resultRevision, mutationProblem = null) }
                        }
                    }
                }
            }
            // A receipt may describe an old commit. Never reconstruct current supply from that delta.
            refresh()
        } catch (e: CancellationException) {
            uncertain = true
            mutable.update { it.copy(pending = true, uncertain = true) }
            throw e
        }
    }
    /** Caller must first display the explicit may-already-have-committed warning. */
    fun abandonConfirmed() {
        if (request == null || mutable.value.mutationBusy) return
        request = null; uncertain = false; preview = null
        ++formGeneration; formJob?.cancel(); invalidateBulkPreview()
        mutable.update { it.copy(bulkForm = null, bulkSelectionBase = null, bulkIds = SnapshotList.empty(), bulkAnchor = null, bulkDiscardPrompt = false, pending = false, uncertain = false, form = null, preview = null, issues = SnapshotList.empty(), mutationProblem = null) }
        refresh()
    }
    private fun bulkEditable(): Boolean = request == null && !mutable.value.mutationBusy && mutable.value.form == null && !mutable.value.checkingCatalog
    private fun invalidateBulkPreview(cancelJob: Boolean = true) {
        ++bulkGeneration; if (cancelJob && request == null) bulkJob?.cancel(); bulkProofGeneration = null
        mutable.update { it.copy(bulkPreview = null, bulkPreviewing = false, bulkSelectionPreparing = false) }
    }
    /** Observer is a prompt invalidator; direct callback checks also fence publication/submission. */
    fun onActiveGeneration(generation: GenerationId?) {
        if (request != null || mutable.value.bulkForm?.draft?.finish !is BulkValue.Set) return
        if (bulkProofGeneration != generation) {
            invalidateBulkPreview(); mutable.update { it.copy(bulkProblem = Problem.STALE_GENERATION) }
        }
    }
    fun beginBulkSelection() {
        if (!bulkEditable() || mutable.value.bulkForm != null || mutable.value.bulkSelectionBase != null || mutable.value.bulkSelectionPreparing) return
        val base = mutable.value.inventory ?: return
        if (indexedInventory === base) {
            bulkPositions = inventoryPositions
            mutable.update { it.copy(bulkSelectionBase = base, bulkIds = SnapshotList.empty(), bulkAnchor = null, bulkSelectionProblem = null) }
        } else {
            invalidateBulkPreview(); val ticket = bulkGeneration
            mutable.update { it.copy(bulkSelectionPreparing = true) }
            bulkJob = scope.launch {
                try {
                    val prepared = callPort(dispatcher) { Outcome.Success(indexLots(base,it)) }
                    currentCoroutineContext().ensureActive()
                    if (ticket != bulkGeneration || !bulkEditable() || mutable.value.inventory !== base) return@launch
                    when (prepared) {
                        is PortResult.Failed -> mutable.update { it.copy(bulkSelectionProblem = prepared.problem) }
                        is PortResult.Ok -> { bulkPositions = prepared.value; mutable.update { it.copy(bulkSelectionBase = base, bulkIds = SnapshotList.empty(), bulkAnchor = null, bulkSelectionProblem = null) } }
                    }
                } finally { if (ticket == bulkGeneration) mutable.update { it.copy(bulkSelectionPreparing = false) } }
            }
        }
    }
    private fun indexLots(base: InventorySnapshot, cancel: CancelToken): Map<EntityId,Int> {
        val positions = HashMap<EntityId,Int>()
        for (i in base.lots.indices) { if (i % 1024 == 0) cancel.throwIfCancelled(); positions[base.lots[i].id] = i }
        cancel.throwIfCancelled(); return positions
    }
    fun endBulkSelection() {
        if (!bulkEditable() || mutable.value.bulkForm != null) return
        invalidateBulkPreview()
        mutable.update { it.copy(bulkSelectionBase = null, bulkIds = SnapshotList.empty(), bulkAnchor = null, bulkSelectionProblem = null) }
    }
    fun toggleBulkLot(id: EntityId, range: Boolean = false) {
        if (!bulkEditable() || mutable.value.bulkForm != null) return
        val state = mutable.value; val order = state.bulkSelectionBase?.lots ?: return
        val index = bulkPositions[id] ?: return
        val chosen = state.bulkIds.toMutableSet()
        val anchor = state.bulkAnchor?.let { bulkPositions[it] } ?: -1
        if (range && anchor >= 0 && maxOf(anchor,index)-minOf(anchor,index)+1 > InventoryProjectionLimits.MAX_EDITS) { mutable.update { it.copy(bulkSelectionProblem = Problem.INVALID_INPUT) }; return }
        if (range && anchor >= 0) for (i in minOf(anchor,index)..maxOf(anchor,index)) {
            chosen.add(order[i].id)
            if (chosen.size > InventoryProjectionLimits.MAX_EDITS) { mutable.update { it.copy(bulkSelectionProblem = Problem.INVALID_INPUT) }; return }
        }
        else if (!chosen.add(id)) chosen.remove(id)
        if (chosen.size > InventoryProjectionLimits.MAX_EDITS) { mutable.update { it.copy(bulkSelectionProblem = Problem.INVALID_INPUT) }; return }
        mutable.update { it.copy(bulkIds = SnapshotList.copyOf(chosen.sortedWith(compareBy<EntityId> { bulkPositions[it] ?: Int.MAX_VALUE }.thenBy { it.value })), bulkAnchor = if (range && anchor >= 0) state.bulkAnchor else id, bulkSelectionProblem = null) }
    }
    /** Missing selected IDs survive rebases; present rows follow display order, missing rows sort last by exact ID. */
    fun missingBulkIds(): SnapshotList<EntityId> = SnapshotList.copyOf(mutable.value.bulkIds.filter { it !in bulkPositions })
    fun beginBulkEdit() {
        if (!bulkEditable() || mutable.value.bulkForm != null) return
        val state = mutable.value; val base = state.bulkSelectionBase ?: return
        if (state.bulkIds.isEmpty()) return
        invalidateBulkPreview()
        mutable.update { it.copy(bulkForm = CollectionBulkForm(base,state.bulkIds,SnapshotList.copyOf(state.bulkIds.map { id -> bulkPositions[id]?.let { base.lots[it] } })), bulkProblem = null, savedRevision = null, issues = SnapshotList.empty(), mutationProblem = null) }
    }
    fun bulkDraft(draft: CollectionBulkDraft) {
        if (!bulkEditable()) return
        val form = mutable.value.bulkForm ?: return
        invalidateBulkPreview(); mutable.update { it.copy(bulkForm = form.copy(draft = draft), bulkProblem = null, issues = SnapshotList.empty(), mutationProblem = null) }
    }
    fun excludeBulkLot(id: EntityId) {
        if (!bulkEditable()) return
        val form = mutable.value.bulkForm ?: return
        invalidateBulkPreview()
        val ids = SnapshotList.copyOf(form.ids.filter { it != id })
        mutable.update { it.copy(bulkForm = form.copy(ids = ids, originals = SnapshotList.copyOf(form.ids.indices.filter { form.ids[it] != id }.map { form.originals[it] })), bulkIds = ids, bulkProblem = null, issues = SnapshotList.empty(), mutationProblem = null) }
    }
    fun useRefreshedBulkInventory() {
        if (!bulkEditable()) return
        val form = mutable.value.bulkForm ?: return
        val base = mutable.value.inventory ?: return
        if (indexedInventory !== base) { mutable.update { it.copy(bulkProblem = Problem.STALE_REVISION) }; return }
        invalidateBulkPreview(); bulkPositions = inventoryPositions
        mutable.update { it.copy(bulkForm = form.copy(base = base, originals = SnapshotList.copyOf(form.ids.map { id -> bulkPositions[id]?.let { base.lots[it] } })), bulkSelectionBase = base, bulkProblem = null, issues = SnapshotList.empty(), mutationProblem = null) }
    }
    fun closeBulkForm() {
        if (mutable.value.mutationBusy) return
        if (request != null) { mutable.update { it.copy(bulkForm = null, bulkPreview = null) }; return }
        if (mutable.value.bulkForm?.draft?.changes == true) mutable.update { it.copy(bulkDiscardPrompt = true) } else discardBulkForm()
    }
    fun keepBulkForm() { mutable.update { it.copy(bulkDiscardPrompt = false) } }
    fun discardBulkForm() {
        if (!bulkEditable()) return
        invalidateBulkPreview(); mutable.update { it.copy(bulkForm = null, bulkDiscardPrompt = false, bulkProblem = null) }
    }
    fun cancelBulkPreview() { if (bulkEditable()) invalidateBulkPreview() }
    private fun activeBulkGeneration(): GenerationId? = try { currentCatalogGeneration() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    private suspend fun bulkCandidate(form: CollectionBulkForm, generation: GenerationId?): CollectionBulkPreview = withContext(dispatcher) {
        currentCoroutineContext().ensureActive()
        val originals = form.originals.filterNotNull().associateBy { it.id }
        val records = mutableMapOf<CardId,PortResult<PrintingRecord>>()
        if (form.draft.finish is BulkValue.Set && generation != null) for (id in form.ids) {
            currentCoroutineContext().ensureActive()
            val printing = (originals[id]?.identity?.printingId as? Knowledge.Known)?.value ?: continue
            if (printing !in records) records[printing] = callPort(dispatcher) { catalog.printing(printing,it) }
        }
        val rows = form.ids.map { bulkRow(form,it,originals,records,generation) }
        if (rows.any { it.blocked }) return@withContext CollectionBulkPreview(form,SnapshotList.copyOf(rows),null,null,generation)
        val patch = CollectionPatch(SnapshotList.copyOf(rows.map { CollectionEdit.Replace(checkNotNull(it.after)) }))
        when (val result = callPort(dispatcher) { project(form.base,patch,form.base.revision,it) }) {
            is PortResult.Failed -> CollectionBulkPreview(form,SnapshotList.copyOf(rows.map { it.copy(catalogProblem = result.problem) }),null,null,generation)
            is PortResult.Ok -> {
                val p = result.value
                if (p.sourceRevision != form.base.revision || p.issues.size > InventoryProjectionLimits.MAX_ISSUES || p.changes.size > InventoryProjectionLimits.MAX_EDITS || p.merges.isNotEmpty())
                    CollectionBulkPreview(form,SnapshotList.copyOf(rows.map { it.copy(catalogProblem = Problem.CORRUPT_DATA) }),null,null,generation)
                else {
                    val byRow = p.issues.groupBy { it.entryId }
                    CollectionBulkPreview(form,SnapshotList.copyOf(rows.map { row -> row.copy(structuralIssues = SnapshotList.copyOf(byRow[row.id] ?: emptyList())) }),patch,p,generation)
                }
            }
        }
    }
    fun previewBulkEdit() {
        if (!bulkEditable() || mutable.value.bulkPreviewing) return
        val form = mutable.value.bulkForm ?: return
        if (!form.draft.changes || form.ids.isEmpty()) { mutable.update { it.copy(bulkProblem = Problem.INVALID_INPUT) }; return }
        if (mutable.value.inventory?.revision != form.base.revision) { mutable.update { it.copy(bulkProblem = Problem.STALE_REVISION) }; return }
        invalidateBulkPreview(); val ticket = bulkGeneration
        val active = if (form.draft.finish is BulkValue.Set) activeBulkGeneration() else null
        bulkProofGeneration = active
        mutable.update { it.copy(bulkPreviewing = true, bulkProblem = null, issues = SnapshotList.empty(), mutationProblem = null) }
        bulkJob = scope.launch {
            try {
                val candidate = bulkCandidate(form,active)
                currentCoroutineContext().ensureActive()
                if (ticket != bulkGeneration || mutable.value.bulkForm != form) return@launch
                if (mutable.value.inventory?.revision != form.base.revision) mutable.update { it.copy(bulkProblem = Problem.STALE_REVISION) }
                else if (form.draft.finish is BulkValue.Set && activeBulkGeneration() != active) mutable.update { it.copy(bulkProblem = Problem.STALE_GENERATION) }
                else mutable.update { it.copy(bulkPreview = candidate) }
            } finally { if (ticket == bulkGeneration) mutable.update { it.copy(bulkPreviewing = false) } }
        }
    }
    fun confirmBulkPreview() {
        if (!bulkEditable() || mutable.value.bulkPreviewing) return
        val frozen = mutable.value.bulkPreview?.takeIf { !it.blocked } ?: return
        if (mutable.value.inventory?.revision != frozen.form.base.revision || mutable.value.bulkForm != frozen.form) { mutable.update { it.copy(bulkProblem = Problem.STALE_REVISION) }; return }
        val ticket = bulkGeneration
        val finishChange = frozen.form.draft.finish is BulkValue.Set
        if (finishChange && (frozen.generation == null || activeBulkGeneration() != frozen.generation)) { invalidateBulkPreview(); mutable.update { it.copy(bulkProblem = Problem.STALE_GENERATION) }; return }
        mutable.update { it.copy(mutationBusy = true, bulkProblem = null) }
        bulkJob = scope.launch {
            try {
                val checked = if (finishChange) bulkCandidate(frozen.form,frozen.generation) else frozen
                currentCoroutineContext().ensureActive()
                if (ticket != bulkGeneration || mutable.value.bulkForm != frozen.form) return@launch
                if (checked.blocked) { mutable.update { it.copy(bulkPreview = checked) }; return@launch }
                if (mutable.value.inventory?.revision != frozen.form.base.revision) { invalidateBulkPreview(); mutable.update { it.copy(bulkProblem = Problem.STALE_REVISION) }; return@launch }
                if (finishChange && activeBulkGeneration() != frozen.generation) { invalidateBulkPreview(); mutable.update { it.copy(bulkProblem = Problem.STALE_GENERATION) }; return@launch }
                try { request = Request.Bulk(frozen,nextEntityId()) }
                catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.update { it.copy(mutationProblem = Problem.INTERNAL) }; return@launch }
                requestSelectionGeneration = selectionGeneration
                invalidateAllocation(); ++readGeneration; readJob?.cancel()
                perform(checkNotNull(request))
            } finally { mutable.update { it.copy(mutationBusy = false) } }
        }
    }
    fun refreshDecks() = pageDecks(null)
    fun nextDeckPage() { if (!mutable.value.loadingDecks) mutable.value.deckCursor?.let(::pageDecks) }
    private fun pageDecks(cursor: String?) {
        deckJob?.cancel(); val generation = ++deckGeneration; val epoch = mutable.value.deckEpoch
        mutable.update { it.copy(loadingDecks = true, decksProblem = null) }
        deckJob = scope.launch {
            try {
                val result = callPort(dispatcher) { decks.list(PageRequest(20, cursor), it) }
                currentCoroutineContext().ensureActive()
                if (generation != deckGeneration) return@launch
                when (result) {
                    is PortResult.Failed -> mutable.update { it.copy(decksProblem = result.problem) }
                    is PortResult.Ok -> {
                        val page = result.value
                        if (page.snapshot.source != LocalDeckLibraryLimits.SOURCE || page.snapshot.revision == null || page.snapshot.generation != null || page.snapshot.language != null ||
                            page.items.any { it.id.source != LocalDeckLibraryLimits.SOURCE } || (cursor != null && page.snapshot.revision != epoch) ||
                            page.nextCursor == cursor && cursor != null || page.nextCursor != null && page.items.isEmpty()) {
                            mutable.update { it.copy(decksProblem = Problem.CORRUPT_DATA, deckCursor = null) }
                        } else mutable.update { it.copy(decks = page.items, deckCursor = page.nextCursor, deckEpoch = page.snapshot.revision) }
                    }
                }
            } finally { if (generation == deckGeneration) mutable.update { it.copy(loadingDecks = false) } }
        }
    }
    fun selectDeck(id: DeckId) {
        if (mutable.value.selectedDeckId != id) observedDeckRevision = null
        invalidateAllocation()
        mutable.update { it.copy(selectedDeckId = id, selectedDeck = null) }
        calculateAllocation()
    }
    /** Root forwards actual editor/library revision observations; keep only this selection's fence. */
    fun onDeckChanged(id: DeckId, revision: Revision) {
        val visible = mutable.value
        if (visible.selectedDeckId != id) return
        val loaded = visible.selectedDeck?.revision?.value
        val inflight = allocationDeckRevision?.value
        val observed = observedDeckRevision?.value
        val known = listOfNotNull(loaded, inflight, observed).maxOrNull()
        if (observed == null || revision.value > observed) observedDeckRevision = revision
        if (known != null && revision.value > known) {
            invalidateAllocation()
            mutable.update { it.copy(selectedDeck = null) }
        }
    }
    fun includeBoard(board: BoardRole, included: Boolean) {
        invalidateAllocation()
        mutable.update { s -> s.copy(boards = SnapshotList.copyOf(BoardRole.entries.filter { if (it == board) included else it in s.boards })) }
    }
    fun ownership(value: OwnershipPolicy) {
        if (value != OwnershipPolicy.SAME_ORACLE && value != OwnershipPolicy.EXACT_PRINTING) return
        invalidateAllocation(); mutable.update { it.copy(ownership = value) }
    }
    fun calculateAllocation() {
        if (mutable.value.mutationBusy || request != null) return
        val id = mutable.value.selectedDeckId ?: return
        invalidateAllocation(); val generation = allocationGeneration
        val readTicket = readGeneration
        val policy = OwnedQuantityPolicy(mutable.value.boards, ownership = mutable.value.ownership)
        mutable.update { it.copy(allocating = true, allocationProblem = null) }
        allocationJob = scope.launch {
            try {
                val snapshot = callPort(dispatcher) { inventory.snapshot(it) }
                currentCoroutineContext().ensureActive()
                if (generation != allocationGeneration || readTicket != readGeneration) return@launch
                if (snapshot is PortResult.Failed) { mutable.update { it.copy(allocationProblem = snapshot.problem) }; return@launch }
                val fresh = (snapshot as PortResult.Ok).value
                if (!validSnapshot(fresh) || fresh.revision.value < (mutable.value.inventory?.revision?.value ?: 0L)) {
                    mutable.update { it.copy(allocationProblem = Problem.STALE_REVISION) }; return@launch
                }
                val loaded = callPort(dispatcher) { decks.load(id, it) }
                currentCoroutineContext().ensureActive()
                if (generation != allocationGeneration || readTicket != readGeneration) return@launch
                if (loaded is PortResult.Failed) { mutable.update { it.copy(allocationProblem = loaded.problem) }; return@launch }
                val deck = (loaded as PortResult.Ok).value
                if (deck.id != id || id.source != LocalDeckLibraryLimits.SOURCE) { mutable.update { it.copy(allocationProblem = Problem.CORRUPT_DATA) }; return@launch }
                if (deck.revision.value < (observedDeckRevision?.value ?: 0L)) {
                    mutable.update { it.copy(allocationProblem = Problem.STALE_REVISION) }; return@launch
                }
                allocationDeckRevision = deck.revision
                if (deck.revision.value > (observedDeckRevision?.value ?: -1L)) observedDeckRevision = deck.revision
                val result = callPort(dispatcher) { allocator.allocate(deck, fresh, policy, it) }
                currentCoroutineContext().ensureActive()
                if (generation != allocationGeneration || readTicket != readGeneration) return@launch
                when (result) {
                    is PortResult.Failed -> mutable.update { it.copy(allocationProblem = result.problem) }
                    is PortResult.Ok -> {
                        val plan = result.value
                        val visible = mutable.value
                        if (visible.selectedDeckId != id || deck.revision.value < (observedDeckRevision?.value ?: 0L) || fresh.revision.value < (visible.inventory?.revision?.value ?: 0L) ||
                            (visible.selectedDeck?.id == id && visible.selectedDeck.revision.value > deck.revision.value)) {
                            mutable.update { it.copy(allocationProblem = Problem.STALE_REVISION) }
                            return@launch
                        }
                        if (plan.deckId != id || plan.deckRevision != deck.revision || plan.inventoryRevision != fresh.revision || plan.policy != policy) {
                            mutable.update { it.copy(allocationProblem = Problem.CORRUPT_DATA) }
                        } else {
                            if (preview?.form?.base?.revision != fresh.revision) { preview = null; mutable.update { it.copy(preview = null) } }
                            mutable.update { it.copy(inventory = fresh, selectedDeck = deck, allocation = plan) }
                        }
                    }
                }
            } finally { if (generation == allocationGeneration) mutable.update { it.copy(allocating = false) } }
        }
    }
}
