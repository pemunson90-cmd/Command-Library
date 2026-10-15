package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.common.CoroutineCancelToken
import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
) {
    val templateBody: String get() = overrides[mode] ?: templates[mode]?.body.orEmpty()
    val rejected: Set<OfflinePromptField> get() =
        if (mode in rejectedOverrides) rejectedFields + OfflinePromptField.OVERRIDE else rejectedFields
}

/** Session state belongs to the retained shell owner. No clipboard, persistence or source preparation. */
internal class OfflinePromptController(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val prompts: OfflineDeckPrompts,
    private val currentDeck: () -> DeckSnapshot?,
) {
    private val mutable = MutableStateFlow(OfflinePromptState())
    val state: StateFlow<OfflinePromptState> = mutable.asStateFlow()
    private var ticket: Any = Any()
    private var job: Job? = null

    private fun invalidate(next: OfflinePromptState) {
        ticket = Any()
        job?.cancel()
        job = null
        mutable.value = next.copy(result = null, review = null, detail = null, copied = null, busy = false, problem = null)
    }

    fun open(deck: DeckSnapshot, snapshot: SnapshotReference?) {
        val previous = mutable.value
        if (previous.deck != deck) invalidate(OfflinePromptState(deck = deck, snapshot = snapshot, visible = true))
        else invalidate(previous.copy(visible = true, snapshot = snapshot))
        if (!live()) return
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
        if (s.deck != null && deck != s.deck) invalidate(s.copy(visible = false, deck = null))
    }

    fun setSnapshot(snapshot: SnapshotReference?) {
        if (!live()) return
        invalidate(mutable.value.copy(snapshot = snapshot))
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
        if (live()) invalidate(change(mutable.value))
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
    fun setText(field: OfflinePromptField, value: String) {
        if (!live()) return
        val s = mutable.value
        val limit = if (field == OfflinePromptField.REPLY) 256 * 1024 else 32 * 1024
        if (!fits(value, limit)) {
            invalidate(if (field == OfflinePromptField.OVERRIDE)
                s.copy(rejectedOverrides = s.rejectedOverrides + s.mode)
            else s.copy(rejectedFields = s.rejectedFields + field))
            return
        }
        val next = if (field == OfflinePromptField.OVERRIDE)
            s.copy(rejectedOverrides = s.rejectedOverrides - s.mode)
        else s.copy(rejectedFields = s.rejectedFields - field)
        invalidate(when (field) {
            OfflinePromptField.QUESTION -> next.copy(question = value)
            OfflinePromptField.CONSTRAINTS -> next.copy(constraints = value)
            OfflinePromptField.OVERRIDE -> next.copy(overrides = next.overrides + (next.mode to value))
            OfflinePromptField.REPLY -> next.copy(reply = value)
        })
    }
    fun resetTemplate() = edit {
        it.copy(overrides = it.overrides - it.mode, rejectedOverrides = it.rejectedOverrides - it.mode)
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
        if (s.rejected.isNotEmpty() || s.templates[s.mode] == null) {
            mutable.value = s.copy(problem = Problem.INVALID_INPUT); return
        }
        invalidate(s)
        val deck = checkNotNull(s.deck)
        val request = OfflinePromptRequest(deck, s.mode, s.intensity, s.question, s.constraints,
            s.includePrivateNotes, s.snapshot, s.overrides[s.mode])
        launch({ prompts.render(request, it) }) { result ->
            val a = result.artifact
            if (result.mode != s.mode || result.snapshot != s.snapshot || result.template != s.templates[s.mode] ||
                result.overrideUsed != (request.overrideBody != null) || a.deckId != deck.id ||
                a.deckRevision != deck.revision || a.templateId != s.mode.templateId ||
                a.templateVersion != s.mode.shippedVersion || a.rendered.isEmpty() ||
                result.oracleContextsIncluded < 0 || result.oracleContextsOmitted < 0) null
            else mutable.value.copy(result = result)
        }
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
