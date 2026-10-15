package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.common.CoroutineCancelToken
import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PromptOverrideBaseline(
    val loaded: Boolean = false,
    val row: StoredPromptOverride? = null,
    val problem: Problem? = null,
)

internal enum class OfflinePromptField { QUESTION, CONSTRAINTS, OVERRIDE, REPLY }
internal data class OfflinePromptState(
    val visible: Boolean = false,
    val deck: DeckSnapshot? = null,
    val snapshot: SnapshotReference? = null,
    val mode: OfflineLabMode = OfflineLabMode.CHAT,
    val intensity: RoastIntensity = RoastIntensity.GENTLE,
    val question: String = "",
    val constraints: String = "",
    val includePrivateNotes: Boolean = false,
    val overrides: Map<OfflineLabMode, String> = emptyMap(),
    val templates: Map<OfflineLabMode, PromptTemplate> = emptyMap(),
    val reply: String = "",
    val rejectedFields: Set<OfflinePromptField> = emptySet(),
    val rejectedOverrides: Set<OfflineLabMode> = emptySet(),
    val result: OfflinePromptResult? = null,
    val review: OfflineReplyReview? = null,
    val detail: CardRecord? = null,
    val busy: Boolean = false,
    val problem: Problem? = null,
    val copied: Boolean? = null,
    val persistenceAvailable: Boolean = false,
    val baselines: Map<OfflineLabMode, PromptOverrideBaseline> = emptyMap(),
    val dirtyModes: Set<OfflineLabMode> = emptySet(),
    val editGenerations: Map<OfflineLabMode, Long> = emptyMap(),
    val frame: Long = 0,
    val historyPrompt: HistoryPromptCapture? = null,
    val writingMode: OfflineLabMode? = null,
    val templateProblems: Map<OfflineLabMode, Problem> = emptyMap(),
) {
    val baseline: PromptOverrideBaseline get() = baselines[mode] ?: PromptOverrideBaseline()
    val templateReady: Boolean get() = !persistenceAvailable || baseline.loaded
    val versionMismatch: Boolean get() = baseline.row?.let { it.body != null && it.shippedVersion != mode.shippedVersion } == true
    val templateBody: String get() = overrides[mode] ?: baseline.row?.body ?: templates[mode]?.body.orEmpty()
    val templateSaved: Boolean get() = persistenceAvailable && baseline.loaded && mode !in dirtyModes && !versionMismatch && mode !in rejectedOverrides

    val rejected: Set<OfflinePromptField> get() =
        if (mode in rejectedOverrides) rejectedFields + OfflinePromptField.OVERRIDE else rejectedFields
}

/** Retained per-mode template drafts and explicit local persistence; deck work keeps its own fence. */
internal class OfflinePromptController(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val prompts: OfflineDeckPrompts,
    private val currentDeck: () -> DeckSnapshot?,
    private val savedTemplates: LocalPromptOverrides? = null,
) {
    // Preserve existing trailing-lambda callers while keeping persistence last in the primary API.
    constructor(scope: CoroutineScope, dispatcher: CoroutineDispatcher, prompts: OfflineDeckPrompts,
        currentDeck: () -> DeckSnapshot?) : this(scope, dispatcher, prompts, currentDeck, null)

    private val mutable = MutableStateFlow(OfflinePromptState(persistenceAvailable = savedTemplates != null))
    val state: StateFlow<OfflinePromptState> = mutable.asStateFlow()
    private var ticket: Any = Any()
    private var job: Job? = null
    private var persistenceJob: Job? = null
    private val loads = mutableMapOf<OfflineLabMode, Job>()

    private fun invalidate(next: OfflinePromptState) {
        ticket = Any()
        job?.cancel()
        job = null
        mutable.value = next.copy(frame = mutable.value.frame + 1, result = null, review = null, detail = null, copied = null, busy = false, problem = null)
    }

    fun open(deck: DeckSnapshot, snapshot: SnapshotReference?) {
        val previous = mutable.value
        if (previous.deck != deck) invalidate(previous.copy(deck = deck, snapshot = snapshot, visible = true,
            question = "", constraints = "", reply = "", includePrivateNotes = false, rejectedFields = emptySet(), historyPrompt = null))
        else invalidate(previous.copy(visible = true, snapshot = snapshot))
        if (!live()) return
        // Reload durable truth on reopen, retaining every dirty draft.
        if (savedTemplates != null) OfflineLabMode.entries.forEach { reloadTemplate(it) }
        // A complete explicit ID/version join; title and list order have no machine meaning.
        try {
            val templates = prompts.templates()
            if (templates.size != OfflineLabMode.entries.size || templates.any { !fits(it.body, 32 * 1024) })
                throw IllegalArgumentException()
            val mapped = OfflineLabMode.entries.associateWith { mode ->
                templates.single { it.id == mode.templateId && it.shippedVersion == mode.shippedVersion }
            }
            mutable.value = mutable.value.copy(templates = mapped)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.value = mutable.value.copy(templates = emptyMap(), problem = Problem.CORRUPT_DATA) }
    }

    fun observe(deck: DeckSnapshot?) {
        val s = mutable.value
        if (s.deck != null && deck != s.deck) invalidate(s.copy(visible = false, deck = null, historyPrompt = null))
    }

    fun setSnapshot(snapshot: SnapshotReference?) {
        if (!live()) return
        invalidate(mutable.value.copy(snapshot = snapshot, historyPrompt = null))
    }
    fun hide() { invalidate(mutable.value.copy(visible = false)) }
    fun useCurrentData(action: () -> Unit) { if (live()) action() }
    private fun live(): Boolean {
        val s = mutable.value
        if (!s.visible || s.deck == null) return false
        if (currentDeck() != s.deck) { observe(null); return false }
        return true
    }
    private fun edit(change: (OfflinePromptState) -> OfflinePromptState) {
        if (live()) invalidate(change(mutable.value).copy(historyPrompt = null))
    }
    fun setMode(mode: OfflineLabMode) = edit { it.copy(mode = mode) }
    fun setIntensity(intensity: RoastIntensity) = edit { it.copy(intensity = intensity) }
    fun setIncludePrivateNotes(include: Boolean) = edit { it.copy(includePrivateNotes = include) }

    /** First bound UTF16 length, then strictly count UTF8 without allocating a second input. */
    private fun fits(value: String, limit: Int): Boolean {
        if (value.length > limit) return false
        var bytes = 0
        var i = 0
        while (i < value.length) {
            val c = value[i++]
            bytes += when {
                c.code < 0x80 -> 1
                c.code < 0x800 -> 2
                c.isHighSurrogate() -> {
                    if (i == value.length || !value[i].isLowSurrogate()) return false
                    i++; 4
                }
                c.isLowSurrogate() -> return false
                else -> 3
            }
            if (bytes > limit) return false
        }
        return true
    }
    private fun matches(mode: OfflineLabMode, frame: Long): Boolean =
        live() && mutable.value.mode == mode && mutable.value.frame == frame

    fun setText(field: OfflinePromptField, value: String, mode: OfflineLabMode = mutable.value.mode,
        frame: Long = mutable.value.frame) {
        if (!matches(mode, frame)) return
        if (field == OfflinePromptField.OVERRIDE && !mutable.value.templateReady) return
        val s = mutable.value
        val limit = if (field == OfflinePromptField.REPLY) 256 * 1024 else 32 * 1024
        if (!fits(value, limit)) {
            invalidate(if (field == OfflinePromptField.OVERRIDE)
                s.copy(rejectedOverrides = s.rejectedOverrides + s.mode,
                    dirtyModes = s.dirtyModes + s.mode, editGenerations = s.editGenerations + (s.mode to generation(s.mode) + 1))
            else s.copy(rejectedFields = s.rejectedFields + field))
            return
        }
        val next = if (field == OfflinePromptField.OVERRIDE)
            s.copy(rejectedOverrides = s.rejectedOverrides - s.mode, templateProblems = s.templateProblems - s.mode)
        else s.copy(rejectedFields = s.rejectedFields - field)
        invalidate((when (field) {
            OfflinePromptField.QUESTION -> next.copy(question = value)
            OfflinePromptField.CONSTRAINTS -> next.copy(constraints = value)
            OfflinePromptField.OVERRIDE -> next.copy(overrides = next.overrides + (next.mode to value),
                dirtyModes = next.dirtyModes + next.mode,
                editGenerations = next.editGenerations + (next.mode to generation(next.mode) + 1))
            OfflinePromptField.REPLY -> next.copy(reply = value)
        }).copy(historyPrompt = if (field == OfflinePromptField.REPLY) s.historyPrompt else null))
    }
    private fun generation(mode: OfflineLabMode): Long = mutable.value.editGenerations[mode] ?: 0

    /** Durable bookkeeping is not a new deck frame when the active render inputs stay identical. */
    private fun updateTemplates(next: OfflinePromptState) {
        val previous = mutable.value
        fun overrideBody(state: OfflinePromptState): String? = state.overrides[state.mode] ?: state.baseline.row?.body
        if (overrideBody(previous) != overrideBody(next) || previous.templateReady != next.templateReady ||
            previous.versionMismatch != next.versionMismatch ||
            (previous.mode in previous.rejectedOverrides) != (next.mode in next.rejectedOverrides)) invalidate(next.copy(historyPrompt = null))
        else mutable.value = next
    }

    fun reloadTemplate(mode: OfflineLabMode = mutable.value.mode) {
        val store = savedTemplates ?: return
        if (loads[mode]?.isActive == true || mutable.value.writingMode == mode) return
        val old = mutable.value.baselines[mode] ?: PromptOverrideBaseline()
        updateTemplates(mutable.value.copy(baselines = mutable.value.baselines + (mode to old.copy(loaded = false, problem = null))))
        loads[mode] = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = invokePort { store.load(mode, it) }
                currentCoroutineContext().ensureActive()
                val baseline = when (result) {
                    is PortResult.Failed -> PromptOverrideBaseline(problem = result.problem)
                    is PortResult.Ok -> if (validRow(mode, result.value)) PromptOverrideBaseline(true, result.value)
                        else PromptOverrideBaseline(problem = Problem.CORRUPT_DATA)
                }
                val s = mutable.value
                updateTemplates(s.copy(baselines = s.baselines + (mode to baseline)))
            } finally { loads.remove(mode) }
        }.also { it.start() }
    }

    private fun validRow(mode: OfflineLabMode, row: StoredPromptOverride?): Boolean = row == null ||
        (row.mode == mode && row.shippedVersion > 0 && (row.body?.let { fits(it, 32 * 1024) } != false))

    private fun validReceipt(mode: OfflineLabMode, expected: Revision?, row: StoredPromptOverride?, body: String?): Boolean {
        if (!validRow(mode, row)) return false
        if (row == null) return expected == null && body == null
        if (row.shippedVersion != mode.shippedVersion || row.body != body) return false
        return if (expected == null) row.revision.value == 1L else row.revision == expected ||
            (expected.value < Long.MAX_VALUE && row.revision.value == expected.value + 1)
    }

    fun saveTemplate(mode: OfflineLabMode = mutable.value.mode, frame: Long = mutable.value.frame) {
        if (!matches(mode, frame) || savedTemplates == null) return
        val s = mutable.value
        if (!s.templateReady || mode in s.rejectedOverrides || s.templates[mode] == null) return
        mutateTemplate(mode, s.templateBody, false)
    }

    fun resetTemplate(mode: OfflineLabMode = mutable.value.mode, frame: Long = mutable.value.frame) {
        if (!matches(mode, frame)) return
        if (savedTemplates != null) {
            if (mutable.value.templateReady) mutateTemplate(mode, null, true)
        } else edit { it.copy(overrides = it.overrides - mode, rejectedOverrides = it.rejectedOverrides - mode,
            dirtyModes = it.dirtyModes - mode, editGenerations = it.editGenerations + (mode to generation(mode) + 1)) }
    }

    private fun mutateTemplate(mode: OfflineLabMode, body: String?, reset: Boolean) {
        val store = savedTemplates ?: return
        if (mutable.value.writingMode != null) return
        val captured = generation(mode)
        val expected = mutable.value.baselines[mode]?.row?.revision
        mutable.value = mutable.value.copy(writingMode = mode, templateProblems = mutable.value.templateProblems - mode)
        // invalidate() owns only the render/read job. A possible committed write survives UI changes.
        persistenceJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var acknowledged = false
            try {
                val result = invokePort { cancel ->
                    if (reset) store.reset(mode, expected, cancel)
                    else store.save(mode, expected, checkNotNull(body), cancel)
                }
                currentCoroutineContext().ensureActive()
                val s = mutable.value
                if (result is PortResult.Ok && validReceipt(mode, expected, result.value, body)) {
                    acknowledged = true
                    val unchangedDraft = generation(mode) == captured
                    updateTemplates(s.copy(baselines = s.baselines + (mode to PromptOverrideBaseline(true, result.value)),
                        overrides = if (unchangedDraft) s.overrides - mode else s.overrides,
                        dirtyModes = if (unchangedDraft) s.dirtyModes - mode else s.dirtyModes,
                        rejectedOverrides = if (unchangedDraft) s.rejectedOverrides - mode else s.rejectedOverrides,
                        editGenerations = if (unchangedDraft) s.editGenerations + (mode to captured + 1) else s.editGenerations))
                } else {
                    val problem = (result as? PortResult.Failed)?.problem ?: Problem.CORRUPT_DATA
                    updateTemplates(s.copy(templateProblems = s.templateProblems + (mode to problem), baselines = s.baselines + (mode to (s.baselines[mode] ?: PromptOverrideBaseline()).copy(loaded = false, problem = problem))))
                }
            } finally {
                val latest = mutable.value
                updateTemplates(latest.copy(writingMode = null,
                    templateProblems = if (acknowledged) latest.templateProblems - mode else latest.templateProblems +
                        (mode to (latest.templateProblems[mode] ?: Problem.UNKNOWN_RESULT)),
                    baselines = if (acknowledged) latest.baselines
                    else latest.baselines + (mode to (latest.baselines[mode] ?: PromptOverrideBaseline()).copy(loaded = false))))
                // Unknown/stale/cancelled outcomes establish no rollback. Read truth, never replay writes.
                if (!acknowledged && currentCoroutineContext().isActive) reloadTemplate(mode)
            }
        }
    }

    private suspend fun <T> invokePort(block: suspend (CancelToken) -> Outcome<T>): PortResult<T> = withContext(dispatcher) {
        try {
            when (val outcome = block(CoroutineCancelToken(currentCoroutineContext()))) {
                is Outcome.Success -> PortResult.Ok(outcome.value)
                is Outcome.Failure -> PortResult.Failed(outcome.error.code.toProblem())
                is Outcome.Unavailable -> PortResult.Failed(outcome.reason.toProblem())
                is Outcome.Unknown -> PortResult.Failed(Problem.UNKNOWN_RESULT)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { currentCoroutineContext().ensureActive(); PortResult.Failed(Problem.UNEXPECTED) }
    }
    private fun <T> launch(block: suspend (CancelToken) -> Outcome<T>, accept: (T) -> OfflinePromptState?) {
        if (!live()) return
        val owner = ticket
        mutable.value = mutable.value.copy(busy = true, problem = null, copied = null)
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val outcome = invokePort(block)
                currentCoroutineContext().ensureActive()
                if (ticket !== owner || !live()) return@launch
                mutable.value = when (outcome) {
                    is PortResult.Failed -> mutable.value.copy(problem = outcome.problem)
                    is PortResult.Ok -> accept(outcome.value) ?: mutable.value.copy(problem = Problem.CORRUPT_DATA)
                }
            } finally {
                if (ticket === owner) mutable.value = mutable.value.copy(busy = false)
            }
        }
    }
    fun render() {
        if (!live()) return
        val s = mutable.value
        if (s.rejected.isNotEmpty() || s.templates[s.mode] == null || !s.templateReady || s.versionMismatch) {
            mutable.value = s.copy(problem = Problem.INVALID_INPUT); return
        }
        invalidate(s)
        val deck = checkNotNull(s.deck)
        val request = OfflinePromptRequest(deck, s.mode, s.intensity, s.question, s.constraints,
            s.includePrivateNotes, s.snapshot, s.overrides[s.mode] ?: s.baseline.row?.body)
        launch({ prompts.render(request, it) }) { result ->
            val a = result.artifact
            if (result.mode != s.mode || result.snapshot != s.snapshot || result.template != s.templates[s.mode] ||
                result.overrideUsed != (request.overrideBody != null) || a.deckId != deck.id ||
                a.deckRevision != deck.revision || a.templateId != s.mode.templateId ||
                a.templateVersion != s.mode.shippedVersion || a.rendered.isEmpty() ||
                result.oracleContextsIncluded < 0 || result.oracleContextsOmitted < 0) null
            else mutable.value.copy(result = result, historyPrompt = HistoryPromptCapture(s.mode, a,
                HistoryPromptProvenance(ContentHash("SHA-256", java.security.MessageDigest.getInstance("SHA-256")
                    .digest((request.overrideBody ?: result.template.body).toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }), result.overrideUsed, result.snapshot)))
        }
    }
    fun captureHistory(mode: OfflineLabMode, frame: Long): OfflineHistoryCapture? {
        if (!matches(mode, frame)) return null
        val s = mutable.value
        val captured = s.historyPrompt ?: return null
        if (s.busy || s.rejected.isNotEmpty() || captured.mode != s.mode || captured.artifact.deckId != s.deck?.id ||
            captured.artifact.deckRevision != s.deck?.revision) return null
        return OfflineHistoryCapture(captured, s.reply)
    }
    fun copyPrompt(copy: (String) -> Boolean) {
        if (!live()) return
        val s = mutable.value
        val artifact = s.result?.artifact ?: return
        if (s.busy || s.rejected.isNotEmpty()) return
        // Read the current artifact here, never composition-captured text.
        val succeeded = try { copy(artifact.rendered) } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
        if (mutable.value === s && live()) mutable.value = s.copy(copied = succeeded)
    }
    fun review() {
        if (!live()) return
        val s = mutable.value
        if (s.rejected.isNotEmpty()) { mutable.value = s.copy(problem = Problem.INVALID_INPUT); return }
        invalidate(s)
        val deck = checkNotNull(s.deck)
        launch({ prompts.review(deck, s.snapshot, s.reply, it) }) { review ->
            if (review.deckId != deck.id || review.deckRevision != deck.revision ||
                review.snapshot != s.snapshot || review.rawText != s.reply || review.references.size > 50 ||
                review.formatLegality != LegalityStatus.UNKNOWN || review.colorIdentity !is Knowledge.Unknown ||
                review.budget !is Knowledge.Unknown || review.ownership !is Knowledge.Unknown || review.acquisitionCost !is Knowledge.Unknown ||
                ((review.statedSurpriseAngle as? Knowledge.Known)?.value?.let { it !in setOf(
                    "hidden synergies", "alternate win conditions", "theme reads", "spicy tech") } == true) ||
                review.references.any { ref -> !fits(ref.rawName, 512) || ref.snapshot != s.snapshot || ref.candidates.size > 2 ||
                    (ref.status == OfflineReferenceStatus.FOUND && (s.snapshot == null || ref.candidates.size != 1 ||
                        ref.candidates.single().source != s.snapshot.source)) }) null
            else mutable.value.copy(review = review)
        }
    }
    fun openReference(index: Int, expected: OfflineReplyReview? = mutable.value.review) {
        if (!live()) return
        val s = mutable.value
        val review = s.review ?: return
        if (review !== expected) return
        val reference = review.references.getOrNull(index) ?: return
        val snapshot = s.snapshot ?: return
        if (reference.status != OfflineReferenceStatus.FOUND || reference.candidates.size != 1 ||
            reference.snapshot != snapshot || s.busy) return
        // Preserve review while fencing previous detail work.
        ticket = Any(); job?.cancel()
        mutable.value = s.copy(detail = null, copied = null, problem = null)
        launch({ prompts.card(reference, snapshot, it) }) { card ->
            if (card.oracleId != reference.candidates.single() || card.provenance.provider != snapshot.source ||
                card.provenance.generation != snapshot.generation || card.language != snapshot.language) null
            else mutable.value.copy(detail = card)
        }
    }
    fun closeDetail() {
        if (!live()) return
        ticket = Any(); job?.cancel(); job = null
        mutable.value = mutable.value.copy(detail = null, busy = false, copied = null, problem = null)
    }
}
