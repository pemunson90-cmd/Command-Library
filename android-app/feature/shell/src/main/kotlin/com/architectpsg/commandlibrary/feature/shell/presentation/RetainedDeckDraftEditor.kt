package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One retained serial mutation lane. Read tickets may expire; submitted mutations never do. */
internal class RetainedDeckDraftEditor(
    private val scope: CoroutineScope,
    private val library: LocalDeckLibrary,
    private val drafts: LocalDeckDrafts,
    private val planner: DeckDraftPlanner,
    private val project: (DeckSnapshot, DeckPatch, Revision, UtcTimestamp) -> Outcome<DeckSnapshot>,
    private val dispatcher: CoroutineDispatcher,
    private val nextId: () -> EntityId,
    private val monotonicMillis: () -> Long,
    private val cardCurrent: (CardRecord) -> Boolean,
    private val onCommitted: (DeckId, Revision) -> Unit,
) : DeckDraftEditor {
    private val mutable = MutableStateFlow(DraftEditorState())
    override val state: StateFlow<DraftEditorState> = mutable.asStateFlow()
    private var serial = 0L
    private var readTicket = 0L
    private var readJob: Job? = null
    private var drainJob: Job? = null
    private var timer: Job? = null
    private var deadline: Long? = null
    private var savedBody: DeckDraftBody? = null
    private var card: CardRecord? = null
    private var generation: GenerationId? = null
    private var observedHead: Revision? = null
    private var retiredHead: Pair<DeckId, Revision>? = null
    private var call: Call? = null
    private var rejected: Call? = null
    private var capture: Capture? = null
    private var frozen: Frozen? = null
    private var prepared: PreparedDeckDraft? = null
    private var recovery: DeckDraftPreparation.Pending? = null
    private var ownLater: SavedDeckDraft? = null
    private var knownAck: DeckDraftCommitAck? = null
    private var abandonedIntent: PreparedDeckDraft? = null
    private var ackNotified = false
    private var submitted = false
    private var paused = false
    private var authorizationPaused = false
    private var forceFlush = false
    private var discardWanted: DeckDraftDiscardMode? = null
    private var textCandidate: Capture? = null
    private var textSuppressed = -1L
    private var reconstructionSuppressed = false

    private data class Capture(val body: DeckDraftBody, val sequence: Long, val base: DeckSnapshot,
        val scope: DeckDraftCommitScope, val addedId: EntityId?, val operation: EntityId?, val session: DraftSession)
    private data class Frozen(val capture: Capture, val token: DeckDraftToken, val plan: DeckDraftPlan, val operation: EntityId,
        val recoveryOnly: Boolean = false)
    private sealed interface Call {
        data class Write(val generation: Long, val body: DeckDraftBody, val sequence: Long) : Call
        data class Prepare(val value: Frozen) : Call
        data class Commit(val intent: PreparedDeckDraft) : Call
        data class Discard(val generation: Long, val pending: EntityId?, val mode: DeckDraftDiscardMode) : Call
    }

    override val hasMutationBarrier: Boolean get() = mutable.value.let {
        it.phase != DraftPhase.CLOSED && (it.phase != DraftPhase.IDLE || it.rawDirty || it.conflict || it.uncertain ||
            it.hasFrozenCall || it.durable?.draft != null || it.rawProblem != null || it.exit != null)
    }
    override val canContinueExit: Boolean get() = mutable.value.exit?.action?.let {
        it is DeferredDeckAction.CloseEditor || it is DeferredDeckAction.SelectDeck || it is DeferredDeckAction.ClearDeck ||
            it is DeferredDeckAction.Navigate || it is DeferredDeckAction.OpenVersions
    } == true

    override fun open(id: DeckId, target: DeckEditorTarget) {
        val old = mutable.value
        if (old.session?.deckId == id && old.session.target == target) {
            mutable.value = if (requestedTargetConflictOnly(old)) old.copy(visible = true, requestedTarget = target, conflict = false, contentProblem = null)
                else old.copy(visible = true)
            return
        }
        if (old.session != null && (drainJob?.isActive == true || call != null || frozen != null || recovery != null ||
                knownAck != null || old.rawDirty || old.uncertain || old.exit != null || old.conflict || old.rawProblem != null)) {
            mutable.value = old.copy(visible = true, requestedTarget = target, contentProblem = Problem.STALE_REVISION)
            return
        }
        if (old.session?.deckId == id && old.durable?.draft != null) {
            mutable.value = old.copy(visible = true, requestedTarget = target, conflict = true, contentProblem = Problem.STALE_REVISION)
            return
        }
        val notified = if (old.session?.deckId == id) observedHead else retiredHead?.takeIf { it.first == id }?.second
        timer?.cancel(); readJob?.cancel()
        resetLane()
        observedHead = notified
        card = null
        val session = DraftSession(++serial, id, target)
        val ticket = ++readTicket
        mutable.value = DraftEditorState(visible = true, requestedTarget = target, session = session, phase = DraftPhase.LOADING)
        readJob = scope.launch {
            val loaded = readBoth(id)
            if (ticket != readTicket) return@launch
            when (loaded) {
                is PortResult.Failed -> { paused = true; mutable.value = mutable.value.copy(phase = DraftPhase.IDLE, rawProblem = loaded.problem); finishExit() }
                is PortResult.Ok -> { paused = false; adoptOpen(session, target, loaded.value.first, loaded.value.second); afterRead() }
            }
        }
    }

    private fun requestedTargetConflictOnly(s: DraftEditorState): Boolean = s.conflict &&
        s.requestedTarget != null && s.requestedTarget != s.session?.target && !s.rawDirty && !s.uncertain && s.rawProblem == null &&
        s.base != null && observedHead?.let { it.value > s.base.revision.value } != true && s.base.revision == s.currentHead?.revision && s.body == s.durable?.draft?.body &&
        call == null && rejected == null && frozen == null && capture == null && knownAck == null &&
        (recovery == null || recovery?.intent?.sourceToken == s.durable?.draft?.token)

    override fun change(body: DeckDraftBody) {
        val s = mutable.value
        val prior = s.body ?: return
        if (s.phase == DraftPhase.LOADING || s.exit != null || discardWanted != null || call is Call.Discard ||
            body.baseRevision != prior.baseRevision || target(body) != target(prior) ||
            (s.structuralLocked && prior is DeckDraftBody.Add && body is DeckDraftBody.Add && body.selection != prior.selection)) return
        if (body == prior) return
        val dirty = body != savedBody
        mutable.value = s.copy(body = body, editSequence = s.editSequence + 1, rawDirty = dirty, explicitlyInvalid = false)
        if (dirty && deadline == null) deadline = deadlineFromNow()
        if (!dirty) deadline = null
        schedule()
    }

    override fun confirmExplicit() {
        val s = mutable.value
        if (s.exit != null || discardWanted != null || s.phase == DraftPhase.LOADING || s.conflict) return
        if (recovery != null) { confirmRecovery(); return }
        if (prepared != null && frozen?.capture?.scope != DeckDraftCommitScope.TEXT_AUTOSAVE) { authorizationPaused = false; paused = false; kick(); return }
        if (capture != null || (frozen != null && frozen?.capture?.scope != DeckDraftCommitScope.TEXT_AUTOSAVE)) return
        val body = s.body ?: return
        val base = s.base ?: return
        try {
            // Capture before any suspension, even while an older raw write is still in flight.
            capture = Capture(body, s.editSequence, base, DeckDraftCommitScope.EXPLICIT_CONFIRMED,
                if (body is DeckDraftBody.Add) nextId() else null, nextId(), checkNotNull(s.session))
        } catch (_: Exception) { mutable.value = s.copy(contentProblem = Problem.INTERNAL); return }
        paused = false
        publish()
        kick()
    }

    override fun retryExact() {
        if (drainJob?.isActive == true || mutable.value.phase == DraftPhase.LOADING) return
        if (recovery != null && frozen == null && call == null && rejected == null) { reloadRecovery(); return }
        if (mutable.value.session == null) return
        if (mutable.value.body == null && mutable.value.rawProblem != null &&
            call == null && rejected == null && frozen == null && capture == null && knownAck == null &&
            prepared == null && abandonedIntent == null && discardWanted == null && !mutable.value.uncertain) {
            // Reload the same failed read owner; retain its navigation ticket and visibility.
            reloadRecovery(); return
        }
        paused = false
        authorizationPaused = false
        mutable.value = mutable.value.copy(rawProblem = null, contentProblem = null)
        kick()
    }

    override fun provideCurrentCard(card: CardRecord) { this.card = card }
    override fun onActiveGeneration(generation: GenerationId?) { this.generation = generation }
    override fun onDeckHeadChanged(id: DeckId, revision: Revision) {
        val s = mutable.value
        val retired = retiredHead
        if (retired?.first == id && revision.value > retired.second.value) retiredHead = id to revision
        if (s.session?.deckId != id) return
        if (observedHead == null || revision.value > checkNotNull(observedHead).value) observedHead = revision
        val known = knownAck?.snapshot?.revision ?: s.currentHead?.revision ?: return
        if (revision.value > known.value) {
            if (cleanHiddenOwner(s)) {
                // No input or mutation authority survives here. Load actual state on the next
                // open, retaining the notification as a lower bound rather than a snapshot.
                retiredHead = id to checkNotNull(observedHead)
                resetLane()
                mutable.value = DraftEditorState()
            } else mutable.value = s.copy(conflict = true, contentProblem = Problem.STALE_REVISION)
        }
    }

    private fun cleanHiddenOwner(s: DraftEditorState): Boolean {
        val session = s.session ?: return false
        val head = s.currentHead ?: return false
        val durable = s.durable ?: return false
        return !s.visible && s.phase == DraftPhase.IDLE && s.base == head &&
            s.body == savedBody && s.body == body(head, session.target) &&
            durable.draft == null && durable.latestPreparation !is DeckDraftPreparation.Pending &&
            !s.rawDirty && !s.conflict && !s.uncertain && !s.hasFrozenCall && !s.structuralLocked && !s.explicitlyInvalid &&
            s.rawProblem == null && s.contentProblem == null && s.exit == null &&
            call == null && rejected == null && capture == null && frozen == null && prepared == null && recovery == null &&
            ownLater == null && knownAck == null && abandonedIntent == null && discardWanted == null && textCandidate == null &&
            !submitted && !paused && !authorizationPaused && !forceFlush && deadline == null &&
            drainJob?.isActive != true && readJob?.isActive != true && timer?.isActive != true
    }

    override fun discardInput() = requestDiscard(DeckDraftDiscardMode.INPUT)
    override fun discardPreparation() = requestDiscard(DeckDraftDiscardMode.PREPARATION_ONLY)
    private fun requestDiscard(mode: DeckDraftDiscardMode) {
        if (mutable.value.session == null || mutable.value.phase == DraftPhase.LOADING || call is Call.Discard) return
        discardWanted = mode
        timer?.cancel()
        if (!mutable.value.uncertain && knownAck == null && paused) {
            call = null; capture = null; frozen = null; prepared = null
        }
        paused = false
        publish()
        kick()
    }

    override fun requestExit(action: DeferredDeckAction): Long {
        val s = mutable.value
        val ticket = ++serial
        mutable.value = s.copy(exit = PendingDraftExit(ticket, s.session?.ticket ?: -1L, s.editSequence, action, DraftExitPhase.FLUSHING))
        forceFlush = true
        reconstructionSuppressed = false
        timer?.cancel()
        if (s.session == null) finishExit() else kick()
        return ticket
    }
    override fun chooseExit(ticket: Long, choice: DraftExitChoice) {
        val exit = mutable.value.exit?.takeIf { it.ticket == ticket } ?: return
        when (choice) {
            DraftExitChoice.STAY -> {
                mutable.value = mutable.value.copy(exit = null, visible = true)
                forceFlush = false
                schedule()
            }
            DraftExitChoice.CONTINUE -> if (exit.phase == DraftExitPhase.NEEDS_CHOICE && canContinueExit) {
                mutable.value = mutable.value.copy(exit = exit.copy(phase = DraftExitPhase.READY))
            }
            DraftExitChoice.DISCARD_INPUT -> discardInput()
        }
    }
    override fun takeReadyExit(ticket: Long): DeferredDeckAction? {
        val s = mutable.value
        val exit = s.exit?.takeIf { it.ticket == ticket && it.phase == DraftExitPhase.READY && it.sessionTicket == (s.session?.ticket ?: -1L) } ?: return null
        mutable.value = s.copy(exit = null, visible = false)
        return exit.action
    }
    override fun onBackground() { forceFlush = true; reconstructionSuppressed = false; timer?.cancel(); kick() }

    private fun resetLane() {
        savedBody = null; deadline = null; call = null; rejected = null; capture = null; frozen = null; prepared = null
        recovery = null; ownLater = null; knownAck = null; abandonedIntent = null; submitted = false; paused = false
        authorizationPaused = false; forceFlush = false; discardWanted = null; textCandidate = null; textSuppressed = -1; reconstructionSuppressed = false
    }
    private fun deadlineFromNow(): Long {
        val now = monotonicMillis()
        return if (now > Long.MAX_VALUE - DeckDraftLimits.AUTOSAVE_DELAY_MILLIS) Long.MAX_VALUE else now + DeckDraftLimits.AUTOSAVE_DELAY_MILLIS
    }
    private fun due(): Boolean = forceFlush || deadline?.let { monotonicMillis() >= it } == true
    private fun schedule() {
        timer?.cancel()
        if (mutable.value.rawDirty && !mutable.value.conflict && !paused && mutable.value.exit == null) {
            val at = deadline ?: deadlineFromNow().also { deadline = it }
            timer = scope.launch { delay((at - monotonicMillis()).coerceAtLeast(0)); kick() }
        }
    }
    private fun publish() {
        mutable.value = mutable.value.copy(hasFrozenCall = call != null || rejected != null || capture != null || frozen != null || recovery != null || knownAck != null,
            structuralLocked = capture?.body is DeckDraftBody.Add || frozen?.capture?.body is DeckDraftBody.Add || recovery != null || discardWanted != null || call is Call.Discard)
    }
    private fun fail(problem: Problem, raw: Boolean, uncertain: Boolean = false) {
        paused = true
        mutable.value = mutable.value.copy(phase = DraftPhase.IDLE, uncertain = mutable.value.uncertain || uncertain,
            rawProblem = if (raw) problem else mutable.value.rawProblem,
            contentProblem = if (raw) mutable.value.contentProblem else problem,
            conflict = mutable.value.conflict || problem == Problem.STALE_REVISION)
        publish(); finishExit()
    }
    private fun ambiguous(problem: Problem): Boolean = problem in setOf(Problem.PERSISTENCE, Problem.TRANSPORT, Problem.INTERNAL, Problem.UNKNOWN_RESULT, Problem.UNEXPECTED, Problem.CORRUPT_DATA)
    private fun finishExit() {
        val s = mutable.value
        val exit = s.exit ?: return
        if (exit.phase == DraftExitPhase.READY) return
        val phase = if (paused || s.conflict || recovery != null || authorizationPaused || s.rawProblem != null || s.uncertain) DraftExitPhase.NEEDS_CHOICE
            else if (call == null && rejected == null && capture == null && frozen == null && knownAck == null && !s.rawDirty && s.phase != DraftPhase.LOADING) {
                if (s.durable?.draft != null && !canContinueExit) DraftExitPhase.NEEDS_CHOICE else DraftExitPhase.READY
            }
            else DraftExitPhase.FLUSHING
        mutable.value = s.copy(exit = exit.copy(phase = phase))
    }

    private fun kick() {
        if (drainJob?.isActive == true || mutable.value.phase == DraftPhase.LOADING) return
        if (paused) { finishExit(); return }
        drainJob = scope.launch {
            try {
                while (true) {
                    if (paused) break
                    val s = mutable.value
                    val session = s.session ?: break
                    if (rejected != null) { reconcileRejected(session.deckId); continue }
                    if (call != null) { execute(checkNotNull(call), session.deckId); continue }
                    if (knownAck != null) { reconcile(checkNotNull(knownAck)); continue }
                    if (abandonedIntent != null) { reconcileAbandoned(session.deckId); continue }
                    if (discardWanted != null) { beginDiscard(session.deckId); continue }
                    if (s.conflict) break
                    if (recovery != null) break
                    if (frozen != null && prepared == null) {
                        if (s.rawDirty && due() && authorizationPaused) { beginWrite(checkNotNull(s.body), s.editSequence); continue }
                        if (authorizationPaused) break
                        val value = checkNotNull(frozen)
                        if (s.durable?.draft?.token != value.token) {
                            if (s.durable?.draft?.body != value.capture.body) { beginWrite(value.capture.body, value.capture.sequence); continue }
                            frozen = value.copy(token = checkNotNull(s.durable?.draft).token)
                        }
                        call = Call.Prepare(checkNotNull(frozen)); publish(); continue
                    }
                    if (prepared != null) {
                        if (s.rawDirty && due()) { beginWrite(checkNotNull(s.body), s.editSequence); continue }
                        if (authorizationPaused) break
                        val intent = checkNotNull(prepared)
                        if (!submitted && !authorized(checkNotNull(frozen).capture.body)) {
                            authorizationPaused = true
                            mutable.value = mutable.value.copy(contentProblem = Problem.STALE_GENERATION)
                            break
                        }
                        call = Call.Commit(intent)
                        publish()
                        continue
                    }
                    val explicit = capture
                    if (explicit != null) {
                        val durable = s.durable?.draft
                        if (durable?.body != explicit.body || durable?.token?.baseRevision != explicit.base.revision) {
                            beginWrite(explicit.body, explicit.sequence); continue
                        }
                        plan(explicit, checkNotNull(durable).token)
                        continue
                    }
                    val candidate = textCandidate
                    if (candidate != null) {
                        textCandidate = null
                        val durable = s.durable?.draft
                        if (candidate.sequence != textSuppressed && durable?.body == candidate.body && s.base?.revision == candidate.body.baseRevision) plan(candidate, checkNotNull(durable).token)
                        continue
                    }
                    if (s.rawDirty && due()) { beginWrite(checkNotNull(s.body), s.editSequence); continue }
                    if (forceFlush && !reconstructionSuppressed && !s.rawDirty && s.durable?.draft != null && s.editSequence != textSuppressed &&
                        (s.body is DeckDraftBody.Details || s.body is DeckDraftBody.Entry) && s.base != null) {
                        textCandidate = Capture(checkNotNull(s.body), s.editSequence, s.base, DeckDraftCommitScope.TEXT_AUTOSAVE, null, null, session)
                        continue
                    }
                    break
                }
            } finally {
                mutable.value = mutable.value.copy(phase = DraftPhase.IDLE)
                publish()
                finishExit()
                if (!mutable.value.rawDirty && call == null && frozen == null && capture == null && knownAck == null) forceFlush = false
                drainJob = null
                schedule()
            }
        }
    }
    private fun beginWrite(body: DeckDraftBody, sequence: Long) {
        val generation = mutable.value.durable?.generation ?: return fail(Problem.CORRUPT_DATA, true)
        if (body == mutable.value.body && prepared == null) deadline = null
        call = Call.Write(generation, body, sequence)
        publish()
    }
    private suspend fun plan(value: Capture, token: DeckDraftToken) {
        mutable.value = mutable.value.copy(phase = DraftPhase.PREPARING)
        val proposed = callPort(dispatcher) { planner.propose(value.base, value.body, value.scope, value.addedId, it) }
        when (proposed) {
            is PortResult.Failed -> {
                if (value.scope == DeckDraftCommitScope.TEXT_AUTOSAVE && proposed.problem == Problem.INVALID_INPUT) {
                    textSuppressed = value.sequence
                } else mutable.value = mutable.value.copy(contentProblem = proposed.problem, explicitlyInvalid = proposed.problem == Problem.INVALID_INPUT)
                if (capture === value) capture = null
                if (proposed.problem != Problem.INVALID_INPUT) paused = true
            }
            is PortResult.Ok -> {
                if (value.scope == DeckDraftCommitScope.TEXT_AUTOSAVE && proposed.value.patch.edits.isEmpty() && proposed.value.residual == value.body) {
                    textSuppressed = value.sequence; if (capture === value) capture = null; return
                }
                val operation = try { value.operation ?: nextId() } catch (_: Exception) { fail(Problem.INTERNAL, false); return }
                frozen = Frozen(value, token, proposed.value, operation)
                if (capture === value) capture = null
                if (!authorized(value.body)) {
                    authorizationPaused = true
                    // No prepare submitted. Newer raw may still save; only an explicit retry unpauses authorization.
                    mutable.value = mutable.value.copy(contentProblem = Problem.STALE_GENERATION)
                } else call = Call.Prepare(checkNotNull(frozen))
            }
        }
        publish()
    }

    private suspend fun execute(request: Call, id: DeckId) {
        when (request) {
            is Call.Write -> {
                mutable.value = mutable.value.copy(phase = DraftPhase.SAVING_RAW)
                val result = callPort(dispatcher) { drafts.write(id, request.generation, request.body, it) }
                when (result) {
                    is PortResult.Failed -> mutationFailed(request, result.problem, true)
                    is PortResult.Ok -> {
                        val actual = result.value
                        if (!validState(id, actual) || request.generation == Long.MAX_VALUE || actual.generation != request.generation + 1 || actual.draft?.body != request.body) {
                            fail(Problem.CORRUPT_DATA, true, true); return
                        }
                        call = null; savedBody = request.body
                        val pending = actual.latestPreparation as? DeckDraftPreparation.Pending
                        if (pending != null && pending.intent.operationId != frozen?.operation) {
                            recovery = pending
                            mutable.value = mutable.value.copy(conflict = true, contentProblem = Problem.STALE_REVISION)
                        }
                        if (prepared != null) ownLater = actual.draft
                        else if (frozen?.capture?.body == request.body) frozen = frozen?.copy(token = checkNotNull(actual.draft).token)
                        val dirty = mutable.value.body != savedBody
                        mutable.value = mutable.value.copy(durable = actual, rawDirty = dirty, rawProblem = null, uncertain = false)
                        if (dirty && deadline == null) deadline = deadlineFromNow()
                        if (!dirty && prepared == null) deadline = null
                        if (frozen == null && capture == null && request.body.baseRevision == mutable.value.base?.revision &&
                            (request.body is DeckDraftBody.Details || request.body is DeckDraftBody.Entry)) {
                            textCandidate = Capture(request.body, request.sequence, checkNotNull(mutable.value.base), DeckDraftCommitScope.TEXT_AUTOSAVE, null, null, checkNotNull(mutable.value.session))
                        }
                    }
                }
            }
            is Call.Prepare -> {
                val value = request.value
                if (!authorized(value.capture.body)) {
                    if (mutable.value.uncertain) {
                        val loaded = readBoth(id)
                        if (loaded is PortResult.Failed) { fail(loaded.problem, false, true); return }
                        val (head, actual) = (loaded as PortResult.Ok).value
                        val found = actual.latestPreparation
                        if (found == null || !matches(found.intent, value)) {
                            // No fresh catalog authority: do not retry prepare. Disclose current
                            // state and retire only through the read-backed conflict path.
                            rejected = request; call = null; publish(); return
                        }
                        mutable.value = mutable.value.copy(durable = actual, currentHead = head)
                        when (found) {
                            is DeckDraftPreparation.Committed -> acceptAck(found.acknowledgement, found.intent)
                            is DeckDraftPreparation.Pending -> { call = null; prepared = found.intent; authorizationPaused = true; mutable.value = mutable.value.copy(uncertain = false, contentProblem = Problem.STALE_GENERATION) }
                            is DeckDraftPreparation.Abandoned -> { call = null; prepared = null; abandonedIntent = found.intent; submitted = false; mutable.value = mutable.value.copy(uncertain = false) }
                        }
                    } else { authorizationPaused = true; call = null; mutable.value = mutable.value.copy(contentProblem = Problem.STALE_GENERATION) }
                    publish(); return
                }
                mutable.value = mutable.value.copy(phase = DraftPhase.PREPARING)
                val result = callPort(dispatcher) { drafts.prepare(id, value.token, value.capture.scope, value.plan.patch, value.operation, it) }
                when (result) {
                    is PortResult.Failed -> mutationFailed(request, result.problem, false)
                    is PortResult.Ok -> {
                        val status = result.value
                        if (!matches(status.intent, value)) { fail(Problem.CORRUPT_DATA, false, true); return }
                        when (status) {
                            is DeckDraftPreparation.Pending -> { call = null; prepared = status.intent; mutable.value = mutable.value.copy(uncertain = false, contentProblem = null) }
                            is DeckDraftPreparation.Committed -> acceptAck(status.acknowledgement, status.intent)
                            is DeckDraftPreparation.Abandoned -> {
                                call = null; prepared = null; abandonedIntent = status.intent; submitted = false
                                mutable.value = mutable.value.copy(uncertain = false)
                            }
                        }
                    }
                }
            }
            is Call.Commit -> {
                if (!submitted && !authorized(checkNotNull(frozen).capture.body)) { fail(Problem.STALE_GENERATION, false); return }
                mutable.value = mutable.value.copy(phase = DraftPhase.COMMITTING)
                submitted = true // Set before dispatch; cancellation/transport is not rollback evidence.
                val result = callPort(dispatcher) { drafts.commit(id, request.intent.operationId, request.intent.requestSha256, it) }
                when (result) {
                    is PortResult.Failed -> {
                        if (!ambiguous(result.problem)) submitted = false
                        mutationFailed(request, result.problem, false)
                    }
                    is PortResult.Ok -> acceptAck(result.value, request.intent)
                }
            }
            is Call.Discard -> {
                mutable.value = mutable.value.copy(phase = DraftPhase.DISCARDING)
                val result = callPort(dispatcher) { drafts.discard(id, request.generation, request.pending, request.mode, it) }
                when (result) {
                    is PortResult.Failed -> mutationFailed(request, result.problem, true)
                    is PortResult.Ok -> {
                        val actual = result.value
                        val valid = validState(id, actual) && request.generation < Long.MAX_VALUE && actual.generation == request.generation + 1 &&
                            (request.mode != DeckDraftDiscardMode.INPUT || actual.draft == null) &&
                            (request.mode != DeckDraftDiscardMode.PREPARATION_ONLY || actual.draft?.body == mutable.value.durable?.draft?.body) &&
                            (request.pending == null || (actual.latestPreparation as? DeckDraftPreparation.Abandoned)?.intent?.operationId == request.pending ||
                                actual.latestPreparation?.intent?.sourceToken?.generation?.let { it > request.generation } == true)
                        if (!valid) { fail(Problem.CORRUPT_DATA, true, true); return }
                        call = null; frozen = null; capture = null; prepared = null; recovery = actual.latestPreparation as? DeckDraftPreparation.Pending; ownLater = null; submitted = false; discardWanted = null
                        val head = mutable.value.currentHead
                        val body = if (request.mode == DeckDraftDiscardMode.INPUT) head?.let { body(it, checkNotNull(mutable.value.session).target) } else mutable.value.body
                        savedBody = actual.draft?.body ?: body
                        val conflict = actual.draft?.body?.baseRevision?.let { it != head?.revision } == true || recovery != null
                        mutable.value = mutable.value.copy(body = body, durable = actual, base = head?.takeUnless { conflict },
                            rawDirty = body != savedBody, conflict = conflict, uncertain = false, rawProblem = null, contentProblem = null, explicitlyInvalid = false)
                        deadline = null; textCandidate = null; textSuppressed = if (mutable.value.rawDirty) -1 else mutable.value.editSequence; forceFlush = false
                    }
                }
            }
        }
        publish()
    }

    private suspend fun acceptAck(ack: DeckDraftCommitAck, intent: PreparedDeckDraft) {
        val value = checkNotNull(frozen)
        val checked = callPort(dispatcher) { cancel ->
            cancel.throwIfCancelled()
            if (!basicAck(ack, intent) || !matches(intent, value)) corrupt()
            else when (val expected = project(value.capture.base, value.plan.patch, value.capture.base.revision, ack.snapshot.modifiedAt)) {
                is Outcome.Success -> if (expected.value == ack.snapshot) Outcome.Success(Unit) else corrupt()
                else -> corrupt()
            }
        }
        if (checked is PortResult.Failed) { fail(checked.problem, false, true); return }
        call = null
        knownAck = ack
        ackNotified = false
        prepared = intent
        mutable.value = mutable.value.copy(uncertain = false, contentProblem = null)
    }

    private suspend fun reconcile(ack: DeckDraftCommitAck) {
        mutable.value = mutable.value.copy(phase = DraftPhase.COMMITTING)
        val loaded = readBoth(ack.snapshot.id)
        if (loaded is PortResult.Failed) { fail(loaded.problem, false); return }
        val (head, actual) = (loaded as PortResult.Ok).value
        mutable.value = mutable.value.copy(durable = actual, currentHead = head)
        if (!ackNotified) {
            ackNotified = true
            onCommitted(head.id, head.revision)
        }
        if (discardWanted != null) {
            mutable.value = mutable.value.copy(durable = actual, currentHead = head)
            knownAck = null; frozen = null; prepared = null; submitted = false; ownLater = null
            return
        }
        val value = checkNotNull(frozen)
        val source = value.capture
        while (true) {
            // Capture all owner-thread facts. Background pure work never reads mutable actor state.
            val ui = mutable.value
            val waiting = capture
            val knownLater = ownLater
            val expectedIntent = prepared
            val observed = observedHead
            val nextTarget = nextTarget(source, head)
            val canonical = body(head, nextTarget)
            val result = callPort(dispatcher) { cancel ->
                val sameOwner = ui.session == source.session && ui.body?.let { target(it) == target(source.body) } == true
                val terminal = actual.latestPreparation as? DeckDraftPreparation.Committed
                val effectMatches = when (val effect = ack.slotEffect) {
                    is DeckDraftSlotEffect.Cleared -> actual.generation == effect.resultGeneration && actual.draft == null
                    is DeckDraftSlotEffect.Residual -> actual.draft?.token == effect.token && actual.draft?.body == value.plan.residual
                    is DeckDraftSlotEffect.PreservedLater -> actual.draft == knownLater && knownLater?.token == effect.observedToken
                }
                if (head != ack.snapshot || observed?.let { it.value > head.revision.value } == true || !sameOwner ||
                    !effectMatches || terminal?.intent != expectedIntent || terminal?.acknowledgement != ack) conflictResult()
                else if (value.recoveryOnly) {
                    if (ui.body != source.body || waiting != null) conflictResult()
                    else Outcome.Success(Continuation(actual.draft?.body ?: canonical, null))
                } else when (val merged = DeckDraftQueuedInput.afterOwnCommit(source.body, value.plan.residual, ui.body, ack.snapshot, source.addedId, cancel)) {
                    is Outcome.Success -> {
                        if (waiting == null) Outcome.Success(Continuation(merged.value ?: canonical, null))
                        else when (val rebased = DeckDraftQueuedInput.afterOwnCommit(source.body, value.plan.residual, waiting.body, ack.snapshot, source.addedId, cancel)) {
                            is Outcome.Success -> Outcome.Success(Continuation(merged.value ?: canonical,
                                waiting.copy(body = rebased.value ?: checkNotNull(canonical), base = head)))
                            is Outcome.Failure -> rebased
                            is Outcome.Unavailable -> rebased
                            is Outcome.Unknown -> rebased
                        }
                    }
                    is Outcome.Failure -> merged
                    is Outcome.Unavailable -> merged
                    is Outcome.Unknown -> merged
                }
            }
            if (discardWanted != null) {
                knownAck = null; frozen = null; prepared = null; submitted = false; ownLater = null
                return
            }
            val latest = mutable.value
            if (latest.session != ui.session || latest.editSequence != ui.editSequence || latest.body != ui.body ||
                capture !== waiting || observedHead != observed) continue
            if (result is PortResult.Failed) {
                // Commit is known; exact post-read reconciliation retries without resubmitting.
                fail(result.problem, false)
                return
            }
            val continuation = (result as PortResult.Ok).value
            capture = continuation.waiting
            savedBody = actual.draft?.body ?: canonical
            val dirty = continuation.body != savedBody
            mutable.value = latest.copy(session = source.session.copy(target = nextTarget), body = continuation.body,
                base = head, rawDirty = dirty, conflict = false, recovered = false, requestedTarget = nextTarget,
                contentProblem = null, rawProblem = null, uncertain = false)
            knownAck = null; frozen = null; prepared = null; recovery = null; ownLater = null; submitted = false; authorizationPaused = false
            textCandidate = null; textSuppressed = if (dirty) -1 else latest.editSequence
            if (dirty && deadline == null) deadline = deadlineFromNow()
            if (!dirty) deadline = null
            publish()
            return
        }
    }
    private data class Continuation(val body: DeckDraftBody?, val waiting: Capture?)

    private fun nextTarget(source: Capture, head: DeckSnapshot): DeckEditorTarget =
        source.addedId?.takeIf { source.body is DeckDraftBody.Add && head.entries.any { e -> e.id == it } }?.let { DeckEditorTarget.Entry(it) } ?: source.session.target

    private fun mutationFailed(request: Call, problem: Problem, raw: Boolean) {
        if (mutable.value.uncertain && problem == Problem.STALE_REVISION) {
            // A negative exact retry is not an invented success. Disclose actual current facts first.
            rejected = request
            call = null
            mutable.value = mutable.value.copy(rawProblem = if (raw) problem else mutable.value.rawProblem,
                contentProblem = if (raw) mutable.value.contentProblem else problem)
            publish()
        } else fail(problem, raw, ambiguous(problem))
    }
    private suspend fun reconcileRejected(id: DeckId) {
        val request = checkNotNull(rejected)
        val loaded = readBoth(id)
        if (loaded is PortResult.Failed) { fail(loaded.problem, request is Call.Write || request is Call.Discard, true); return }
        val (head, actual) = (loaded as PortResult.Ok).value
        val intent = actual.latestPreparation
        val operation = when (request) {
            is Call.Prepare -> request.value.operation
            is Call.Commit -> request.intent.operationId
            else -> null
        }
        // Permanent preparation/commit receipts, unlike immediate raw mutation fingerprints,
        // remain authoritative. If discovered, validate the retained exact request and historical ACK.
        if (operation != null && intent is DeckDraftPreparation.Committed && intent.intent.operationId == operation) {
            mutable.value = mutable.value.copy(durable = actual, currentHead = head)
            acceptAck(intent.acknowledgement, intent.intent)
            if (knownAck != null) rejected = null
            return
        }
        savedBody = actual.draft?.body
        recovery = intent as? DeckDraftPreparation.Pending
        mutable.value = mutable.value.copy(durable = actual, currentHead = head, base = null,
            rawDirty = mutable.value.body != savedBody, conflict = true, uncertain = false,
            contentProblem = Problem.STALE_REVISION)
        rejected = null; call = null; capture = null; frozen = null; prepared = null; ownLater = null; submitted = false
        authorizationPaused = false; textCandidate = null; textSuppressed = mutable.value.editSequence
        discardWanted = null
        paused = true
        publish()
        finishExit()
    }

    private suspend fun reconcileAbandoned(id: DeckId) {
        val loaded = readBoth(id)
        if (loaded is PortResult.Failed) { fail(loaded.problem, false); return }
        val (head, actual) = (loaded as PortResult.Ok).value
        val source = checkNotNull(frozen).capture.body
        val knownRaw = actual.draft?.body == source || (ownLater != null && actual.draft == ownLater)
        recovery = actual.latestPreparation as? DeckDraftPreparation.Pending
        savedBody = actual.draft?.body
        val conflict = !knownRaw || mutable.value.body?.baseRevision != head.revision || recovery != null
        mutable.value = mutable.value.copy(durable = actual, currentHead = head, base = head.takeUnless { conflict },
            rawDirty = mutable.value.body != savedBody, conflict = conflict, uncertain = false,
            contentProblem = if (conflict) Problem.STALE_REVISION else null)
        frozen = null; prepared = null; abandonedIntent = null; ownLater = null; submitted = false
        textSuppressed = if (mutable.value.rawDirty) -1 else mutable.value.editSequence
        if (mutable.value.rawDirty && deadline == null) deadline = deadlineFromNow()
        publish()
    }

    private suspend fun beginDiscard(id: DeckId) {
        val loaded = readBoth(id)
        if (loaded is PortResult.Failed) { fail(loaded.problem, true); return }
        val (head, actual) = (loaded as PortResult.Ok).value
        val pending = (actual.latestPreparation as? DeckDraftPreparation.Pending)?.intent?.operationId
        val mode = checkNotNull(discardWanted)
        if (mode == DeckDraftDiscardMode.PREPARATION_ONLY && pending == null) { discardWanted = null; fail(Problem.INVALID_INPUT, true); return }
        mutable.value = mutable.value.copy(durable = actual, currentHead = head)
        call = Call.Discard(actual.generation, pending, mode)
        publish()
    }

    private fun confirmRecovery() {
        if (drainJob?.isActive == true || call != null) return
        val s = mutable.value
        val pending = recovery ?: return
        val raw = s.durable?.draft
        val base = s.base
        if (raw?.token != pending.intent.sourceToken || base == null || raw.body != s.body || base.revision != raw.body.baseRevision) {
            mutable.value = s.copy(conflict = true, contentProblem = Problem.STALE_REVISION)
            return
        }
        val added = pending.intent.patch.edits.filterIsInstance<DeckEdit.Add>().singleOrNull()?.entry?.id
        val captured = Capture(raw.body, s.editSequence, base, pending.intent.scope, added, pending.intent.operationId, checkNotNull(s.session))
        if (!authorized(captured.body)) { mutable.value = s.copy(contentProblem = Problem.STALE_GENERATION); return }
        paused = false
        drainJob = scope.launch {
            try {
                val result = callPort(dispatcher) { planner.propose(base, captured.body, pending.intent.scope, added, it) }
                if (result is PortResult.Failed) { fail(result.problem, false); return@launch }
                val plan = (result as PortResult.Ok).value
                if (plan.patch != pending.intent.patch) { fail(Problem.CORRUPT_DATA, false); return@launch }
                frozen = Frozen(captured, raw.token, plan, pending.intent.operationId, recoveryOnly = true)
                prepared = pending.intent; recovery = null; submitted = false; authorizationPaused = false
            } finally { drainJob = null; publish(); kick() }
        }
    }
    private fun reloadRecovery() {
        val session = mutable.value.session ?: return
        val requested = mutable.value.requestedTarget ?: session.target
        val ticket = ++readTicket
        mutable.value = mutable.value.copy(phase = DraftPhase.LOADING)
        readJob = scope.launch {
            val loaded = readBoth(session.deckId)
            if (ticket != readTicket) return@launch
            if (loaded is PortResult.Failed) { paused = true; mutable.value = mutable.value.copy(phase = DraftPhase.IDLE, rawProblem = loaded.problem); finishExit() }
            else {
                val (head, actual) = (loaded as PortResult.Ok).value
                paused = false
                if (mutable.value.rawDirty && mutable.value.body != actual.draft?.body) {
                    mutable.value = mutable.value.copy(phase = DraftPhase.IDLE, durable = actual, currentHead = head, conflict = true, contentProblem = Problem.STALE_REVISION)
                } else adoptOpen(session, requested, head, actual)
                afterRead()
            }
        }
    }
    private fun afterRead() {
        if (mutable.value.rawProblem != null) finishExit()
        else if (mutable.value.exit != null || forceFlush) kick()
    }
    private suspend fun readBoth(id: DeckId): PortResult<Pair<DeckSnapshot, DeckDraftState>> = callPort(dispatcher) { cancel ->
        when (val head = library.load(id, cancel)) {
            is Outcome.Success -> when (val raw = drafts.load(id, cancel)) {
                is Outcome.Success -> if (head.value.id == id && validState(id, raw.value)) Outcome.Success(head.value to raw.value) else corrupt()
                is Outcome.Failure -> raw
                is Outcome.Unavailable -> raw
                is Outcome.Unknown -> raw
            }
            is Outcome.Failure -> head
            is Outcome.Unavailable -> head
            is Outcome.Unknown -> head
        }
    }
    private fun adoptOpen(session: DraftSession, requested: DeckEditorTarget, head: DeckSnapshot, actual: DeckDraftState) {
        val raw = actual.draft
        val effective = raw?.body?.let(::target) ?: requested
        val stale = raw?.body?.baseRevision?.let { it != head.revision } == true || observedHead?.let { it.value > head.revision.value } == true
        val pending = actual.latestPreparation as? DeckDraftPreparation.Pending
        val terminal = actual.latestPreparation as? DeckDraftPreparation.Committed
        if (terminal != null && !basicAck(terminal.acknowledgement, terminal.intent)) {
            mutable.value = mutable.value.copy(phase = DraftPhase.IDLE, rawProblem = Problem.CORRUPT_DATA, conflict = true)
            return
        }
        recovery = pending
        val body = raw?.body ?: body(head, effective)
        savedBody = body
        val previous = mutable.value
        val sequence = if (previous.body == body) previous.editSequence else 0L
        if (previous.body != body) textSuppressed = -1
        reconstructionSuppressed = !forceFlush
        val conflict = stale || (raw != null && effective != requested) || (pending != null && raw?.token != pending.intent.sourceToken)
        val exit = mutable.value.exit?.takeIf { it.sessionTicket == session.ticket }
        mutable.value = DraftEditorState(visible = previous.visible, editSequence = sequence, recovered = raw != null || actual.latestPreparation != null,
            requestedTarget = requested, session = session.copy(target = effective), body = body, durable = actual,
            base = head.takeUnless { stale }, currentHead = head, phase = DraftPhase.IDLE, conflict = conflict,
            contentProblem = if (conflict) Problem.STALE_REVISION else null, exit = exit)
        val retired = retiredHead
        if (retired?.first == head.id && head.revision.value >= retired.second.value &&
            observedHead?.let { it.value > head.revision.value } != true) retiredHead = null
        publish()
        if (terminal != null) onCommitted(head.id, head.revision)
    }
    private fun authorized(body: DeckDraftBody): Boolean {
        if (body !is DeckDraftBody.Add) return true
        val selected = body.selection ?: return false
        val current = card ?: return false
        return generation != null && cardCurrent(current) && selected.generation == generation && selected.generation == current.provenance.generation &&
            selected.identity.oracleId == Knowledge.Known(current.oracleId) && selected.identity.originalName == Knowledge.Known(current.name) &&
            selected.identity.language == current.language && selected.identity.printingId is Knowledge.Absent && selected.identity.raw == null
    }
    private fun matches(intent: PreparedDeckDraft, value: Frozen): Boolean = intent.operationId == value.operation &&
        intent.sourceToken == value.token && intent.scope == value.capture.scope && intent.patch == value.plan.patch && hash(intent.requestSha256) &&
        (prepared == null || prepared == intent)
    private fun validState(id: DeckId, actual: DeckDraftState): Boolean {
        if (actual.generation < 0) return false
        val raw = actual.draft
        if (raw != null && (actual.generation == 0L || raw.token.deckId != id || raw.token.generation != actual.generation || raw.token.baseRevision != raw.body.baseRevision || !hash(raw.token.rawSha256))) return false
        val intent = actual.latestPreparation?.intent ?: return true
        return (actual.latestPreparation !is DeckDraftPreparation.Pending || raw != null) &&
            intent.sourceToken.deckId == id && intent.sourceToken.generation in 1..actual.generation && hash(intent.sourceToken.rawSha256) && hash(intent.requestSha256)
    }
    private fun basicAck(ack: DeckDraftCommitAck, intent: PreparedDeckDraft): Boolean {
        if (ack.operationId != intent.operationId || ack.preparedRequestSha256 != intent.requestSha256 || ack.sourceToken != intent.sourceToken ||
            !hash(intent.requestSha256) || !hash(intent.sourceToken.rawSha256) || ack.snapshot.id != intent.sourceToken.deckId) return false
        val base = intent.sourceToken.baseRevision.value
        val result = if (intent.patch.edits.isEmpty()) base else if (base == Long.MAX_VALUE) return false else base + 1
        if (ack.snapshot.revision.value != result) return false
        return when (val effect = ack.slotEffect) {
            is DeckDraftSlotEffect.Cleared -> intent.sourceToken.generation < Long.MAX_VALUE && effect.resultGeneration == intent.sourceToken.generation + 1
            is DeckDraftSlotEffect.Residual -> intent.sourceToken.generation < Long.MAX_VALUE && effect.token.deckId == ack.snapshot.id &&
                effect.token.generation == intent.sourceToken.generation + 1 && effect.token.baseRevision == ack.snapshot.revision && hash(effect.token.rawSha256)
            is DeckDraftSlotEffect.PreservedLater -> effect.observedToken.deckId == ack.snapshot.id && effect.observedToken.generation > intent.sourceToken.generation && hash(effect.observedToken.rawSha256)
        }
    }
    private fun hash(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
    private fun target(body: DeckDraftBody): DeckEditorTarget = when (body) {
        is DeckDraftBody.Details -> DeckEditorTarget.Details
        is DeckDraftBody.Entry -> DeckEditorTarget.Entry(body.entryId)
        is DeckDraftBody.Add -> DeckEditorTarget.Add
        is DeckDraftBody.Remove -> DeckEditorTarget.Remove(body.entryId)
    }
    private fun body(deck: DeckSnapshot, target: DeckEditorTarget): DeckDraftBody? = when (target) {
        DeckEditorTarget.Details -> DeckDraftBody.Details(deck.revision, deck.name, deck.description, deck.primer)
        DeckEditorTarget.Add -> DeckDraftBody.Add(deck.revision, "1", "", BoardRole.MAIN, Knowledge.Absent("No finish restriction"), SnapshotList.empty(), null)
        is DeckEditorTarget.Entry -> deck.entries.firstOrNull { it.id == target.id }?.let { DeckDraftBody.Entry(deck.revision, it.id, it.quantity.toString(), it.notes, it.board, it.finish, it.labels) }
        is DeckEditorTarget.Remove -> deck.entries.firstOrNull { it.id == target.id }?.let { DeckDraftBody.Remove(deck.revision, it.id) }
    }
    private fun corrupt(): Outcome<Nothing> = Outcome.Failure(DomainFailure(FailureCode.CORRUPT_DATA, "Invalid draft acknowledgement", SnapshotList.empty()))
    private fun conflictResult(): Outcome<Nothing> = Outcome.Failure(DomainFailure(FailureCode.STALE_REVISION, "Draft continuation conflicts with current state", SnapshotList.empty()))
}
