package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Commands belong to the retained ViewModel dispatcher. Mutation lifetime is independent of layout. */
internal class DeckVersionsController(
    private val scope: CoroutineScope,
    private val versions: LocalDeckVersions,
    private val decks: LocalDeckLibrary,
    private val dispatcher: CoroutineDispatcher,
    private val nextEntityId: () -> EntityId,
    private val onCommitted: (DeckId, Revision) -> Unit = { _, _ -> },
    private val mutationAllowed: () -> Boolean = { true },
) {
    private sealed interface Request {
        val base: DeckSnapshot
        val label: String
        val operation: EntityId
        data class Capture(override val base: DeckSnapshot, override val label: String, override val operation: EntityId) : Request
        data class Restore(val preview: DeckRestorePreview, override val label: String, override val operation: EntityId) : Request { override val base = preview.current }
    }
    private enum class Discard { FORM, CLOSE, OPEN, SELECT, PREVIEW, CAPTURE }
    private val mutable = MutableStateFlow(DeckVersionsUiState())
    val state: StateFlow<DeckVersionsUiState> = mutable.asStateFlow()
    private var headGeneration = 0L
    private var listGeneration = 0L
    private var targetGeneration = 0L
    private var previewGeneration = 0L
    private var headJob: Job? = null
    private var listJob: Job? = null
    private var targetJob: Job? = null
    private var previewJob: Job? = null
    private var request: Request? = null
    private var uncertain = false
    private var discard = Discard.FORM
    private var queuedDeck: DeckId? = null
    private var queuedVersion: EntityId? = null
    private var visible = false

    private fun editing(): Boolean = request == null && !mutable.value.mutationBusy
    private fun dirty(): Boolean = (mutable.value.captureForm && mutable.value.label.isNotEmpty()) || mutable.value.recoveryLabel.isNotEmpty()
    fun openDeck(id: DeckId) {
        if (request != null || mutable.value.mutationBusy) { visible = true; return }
        if (mutable.value.deckId == id) { visible = true; refreshHead(); refreshVersions(); return }
        if (dirty() && mutable.value.deckId != id) { discard = Discard.OPEN; queuedDeck = id; mutable.update { it.copy(discardPrompt = true) }; return }
        visible = true
        invalidateReads()
        mutable.value = DeckVersionsUiState(deckId = id)
        refreshHead(); refreshVersions()
    }
    private fun invalidateReads() {
        ++headGeneration; ++listGeneration; ++targetGeneration; ++previewGeneration
        headJob?.cancel(); listJob?.cancel(); targetJob?.cancel(); previewJob?.cancel()
    }
    /** Keep deck mutation admission on this route until reconciliation or explicit abandonment. */
    fun requestClose(): Boolean {
        if (request != null || mutable.value.pending || mutable.value.mutationBusy) return false
        if (editing() && dirty()) { discard = Discard.CLOSE; mutable.update { it.copy(discardPrompt = true) }; return false }
        closeReads(); return true
    }
    private fun closeReads() { visible = false; invalidateReads(); mutable.update { it.copy(loadingHead = false, loadingList = false, loadingTarget = false, previewing = false) } }
    fun keepDraft() { queuedDeck = null; queuedVersion = null; mutable.update { it.copy(discardPrompt = false) } }
    fun discardDraft(): Boolean {
        if (!editing()) return false
        val action = discard; val next = queuedDeck; val nextVersion = queuedVersion; queuedDeck = null; queuedVersion = null
        mutable.update { it.copy(captureForm = false, label = "", recoveryLabel = "", discardPrompt = false, invalidLabel = false) }
        if (action == Discard.OPEN && next != null) openDeck(next)
        if (action == Discard.SELECT && nextVersion != null) selectVersion(nextVersion)
        if (action == Discard.PREVIEW) cancelPreview()
        if (action == Discard.CAPTURE) beginCapture()
        if (action == Discard.CLOSE) { closeReads(); return true }
        return false
    }
    fun closeCapture() {
        if (request != null) { mutable.update { it.copy(captureForm = false) }; return }
        if (!editing()) return
        if (mutable.value.label.isNotEmpty()) { discard = Discard.FORM; mutable.update { it.copy(discardPrompt = true) } }
        else mutable.update { it.copy(captureForm = false) }
    }
    fun beginCapture() {
        if (!mutationAllowed()) return
        if (!editing() || mutable.value.captureForm || mutable.value.head == null) return
        if (mutable.value.recoveryLabel.isNotEmpty()) {
            discard = Discard.CAPTURE; mutable.update { it.copy(discardPrompt = true) }; return
        }
        mutable.update { it.copy(captureForm = true, label = "", invalidLabel = false, mutationProblem = null, captured = null, restored = null) }
    }
    fun label(value: String) { if (editing()) mutable.update { it.copy(label = value, invalidLabel = false) } }
    fun recoveryLabel(value: String) { if (editing() && !mutable.value.captureForm) mutable.update { it.copy(recoveryLabel = value, invalidLabel = false) } }
    private fun validLabel(text: String): Boolean {
        if (text.length > 1024 || text.isBlank()) return false
        var bytes = 0; var i = 0
        while (i < text.length) {
            val cp = text[i++].code
            bytes += when {
                cp < 128 -> 1
                cp < 2048 -> 2
                cp in 0xd800..0xdbff -> { if (i == text.length || text[i++].code !in 0xdc00..0xdfff) return false; 4 }
                cp in 0xdc00..0xdfff -> return false
                else -> 3
            }
            if (bytes > 1024) return false
        }
        return true
    }
    private fun validHead(value: DeckSnapshot, id: DeckId) = value.id == id && id.source == LocalDeckLibraryLimits.SOURCE && value.entries.size <= DeckPayloadLimits.MAX_ENTRIES
    fun refreshHead() {
        val id = mutable.value.deckId ?: return
        headJob?.cancel(); val generation = ++headGeneration
        mutable.update { it.copy(loadingHead = true, headProblem = null) }
        headJob = scope.launch {
            try {
                val result = callPort(dispatcher) { decks.load(id, it) }
                currentCoroutineContext().ensureActive()
                if (generation != headGeneration || !visible || mutable.value.deckId != id) return@launch
                when (result) {
                    is PortResult.Failed -> mutable.update { it.copy(headProblem = result.problem) }
                    is PortResult.Ok -> if (!validHead(result.value,id)) mutable.update { it.copy(headProblem = Problem.CORRUPT_DATA) }
                    else if (result.value.revision.value < (mutable.value.head?.revision?.value ?: 0L)) mutable.update { it.copy(headProblem = Problem.STALE_REVISION) }
                    else {
                        if (mutable.value.preview?.current?.revision != result.value.revision) { ++previewGeneration; previewJob?.cancel() }
                        mutable.update { it.copy(head = result.value, preview = it.preview?.takeIf { p -> p.current.revision == result.value.revision }, previewing = false) }
                    }
                }
            } finally { if (generation == headGeneration) mutable.update { it.copy(loadingHead = false) } }
        }
    }
    fun refreshVersions() = page(null, true)
    fun loadMore() { if (!mutable.value.loadingList) mutable.value.cursor?.let { page(it, true) } }
    private fun page(cursor: String?, allowReset: Boolean) {
        val id = mutable.value.deckId ?: return
        listJob?.cancel(); val generation = ++listGeneration; val epoch = mutable.value.epoch
        mutable.update { it.copy(loadingList = true, listProblem = null) }
        listJob = scope.launch {
            try {
                val result = callPort(dispatcher) { versions.list(id, PageRequest(20,cursor), it) }
                currentCoroutineContext().ensureActive()
                if (generation != listGeneration || !visible || mutable.value.deckId != id) return@launch
                when (result) {
                    is PortResult.Failed -> if (result.problem == Problem.STALE_REVISION && cursor != null && allowReset) {
                        mutable.update { it.copy(items = SnapshotList.empty(), cursor = null, epoch = null, listProblem = Problem.STALE_REVISION) }
                        page(null,false)
                    } else mutable.update { it.copy(listProblem = result.problem, cursor = if (result.problem == Problem.STALE_REVISION) null else it.cursor) }
                    is PortResult.Ok -> {
                        val p = result.value
                        val oldIds = if (cursor == null) emptySet() else mutable.value.items.map { it.id }.toSet()
                        val valid = p.snapshot.source == LocalDeckLibraryLimits.SOURCE && p.snapshot.revision != null && p.snapshot.generation == null && p.snapshot.language == null &&
                            p.items.size <= 20 && (cursor == null || p.snapshot.revision == epoch) && p.items.map { it.id }.toSet().size == p.items.size &&
                            p.items.all { it.deckId == id && validLabel(it.label) && it.id !in oldIds && it.predecessorVersionId != it.id &&
                                ((it.kind == DeckVersionKind.USER && it.restoredTargetVersionId == null) || (it.kind == DeckVersionKind.RECOVERY && it.restoredTargetVersionId != null)) } &&
                            (p.nextCursor == null || (p.items.isNotEmpty() && p.nextCursor != cursor))
                        if (!valid) mutable.update { it.copy(listProblem = Problem.CORRUPT_DATA, cursor = null) }
                        else mutable.update { it.copy(items = if (cursor == null) p.items else SnapshotList.copyOf(it.items + p.items), cursor = p.nextCursor, epoch = p.snapshot.revision) }
                    }
                }
            } finally { if (generation == listGeneration) mutable.update { it.copy(loadingList = false) } }
        }
    }
    fun selectVersion(id: EntityId) {
        if (!editing()) return
        val deck = mutable.value.deckId ?: return
        if (mutable.value.selectedId == id && mutable.value.selected != null) return
        if (mutable.value.recoveryLabel.isNotEmpty() && mutable.value.selectedId != id) {
            discard = Discard.SELECT; queuedVersion = id; mutable.update { it.copy(discardPrompt = true) }; return
        }
        targetJob?.cancel(); previewJob?.cancel(); ++previewGeneration
        val generation = ++targetGeneration
        mutable.update { it.copy(selectedId = id, selected = null, loadingTarget = true, targetProblem = null, preview = null, previewing = false, previewProblem = null, recoveryLabel = "") }
        targetJob = scope.launch {
            try {
                val result = callPort(dispatcher) { versions.load(deck,id,it) }
                currentCoroutineContext().ensureActive()
                if (generation != targetGeneration || !visible || mutable.value.deckId != deck) return@launch
                when (result) {
                    is PortResult.Failed -> mutable.update { it.copy(targetProblem = result.problem) }
                    is PortResult.Ok -> if (validVersion(result.value,deck,id)) mutable.update { it.copy(selected = result.value) }
                    else mutable.update { it.copy(targetProblem = Problem.CORRUPT_DATA) }
                }
            } finally { if (generation == targetGeneration) mutable.update { it.copy(loadingTarget = false) } }
        }
    }
    private fun validVersion(value: DeckVersion, deck: DeckId, id: EntityId) = value.id == id && value.deckId == deck && validHead(value.manifest,deck) &&
        value.sourceRevision == value.manifest.revision && validLabel(value.label) && value.predecessorVersionId != id
    fun previewRestore() {
        if (!editing() || mutable.value.previewing) return
        val head = mutable.value.head ?: return
        val target = mutable.value.selected ?: return
        val generation = ++previewGeneration; val selection = targetGeneration
        mutable.update { it.copy(previewing = true, preview = null, previewProblem = null) }
        previewJob = scope.launch {
            try {
                val result = callPort(dispatcher) { versions.previewRestore(head.id,target.id,head.revision,it) }
                currentCoroutineContext().ensureActive()
                if (generation != previewGeneration || selection != targetGeneration || !visible) return@launch
                when (result) {
                    is PortResult.Failed -> mutable.update { it.copy(previewProblem = result.problem) }
                    is PortResult.Ok -> {
                        val p = result.value
                        if (mutable.value.head?.revision != head.revision) mutable.update { it.copy(previewProblem = Problem.STALE_REVISION) }
                        else if (p.current != head || p.target != target || p.difference.before != p.current || p.difference.after != p.target.manifest || p.current.createdAt != p.target.manifest.createdAt) mutable.update { it.copy(previewProblem = Problem.CORRUPT_DATA) }
                        else mutable.update { it.copy(preview = p) }
                    }
                }
            } finally { if (generation == previewGeneration) mutable.update { it.copy(previewing = false) } }
        }
    }
    fun cancelPreview() {
        if (!editing()) return
        if (mutable.value.recoveryLabel.isNotEmpty()) { discard = Discard.PREVIEW; mutable.update { it.copy(discardPrompt = true) }; return }
        ++previewGeneration; previewJob?.cancel(); mutable.update { it.copy(preview = null, previewing = false, previewProblem = null, recoveryLabel = "") }
    }
    fun capture() {
        if (!mutationAllowed()) return
        if (!editing()) return
        val s = mutable.value; val head = s.head ?: return
        if (!s.captureForm || !validLabel(s.label)) { mutable.update { it.copy(invalidLabel = true) }; return }
        freeze { Request.Capture(head,s.label,nextEntityId()) }
    }
    fun confirmRestore() {
        if (!mutationAllowed()) return
        if (!editing()) return
        val s = mutable.value; val p = s.preview ?: return
        if (s.head?.revision != p.current.revision || s.selectedId != p.target.id) { mutable.update { it.copy(previewProblem = Problem.STALE_REVISION) }; return }
        if (!validLabel(s.recoveryLabel)) { mutable.update { it.copy(invalidLabel = true) }; return }
        if (p.current.revision.value == Long.MAX_VALUE) { mutable.update { it.copy(mutationProblem = Problem.PERSISTENCE) }; return }
        freeze { Request.Restore(p,s.recoveryLabel,nextEntityId()) }
    }
    private fun freeze(make: () -> Request) {
        try { request = make() } catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.update { it.copy(mutationProblem = Problem.INTERNAL) }; return }
        attempt()
    }
    fun retry() { if (request != null && !mutable.value.mutationBusy) attempt() }
    private fun attempt() {
        val frozen = request ?: return
        mutable.update { it.copy(mutationBusy = true, pending = true, mutationProblem = null, captured = null, restored = null) }
        scope.launch {
            try {
                val result: PortResult<Pair<DeckVersion?,DeckRestoreResult?>> = callPort(dispatcher) { token -> when (frozen) {
                    is Request.Capture -> when (val r = versions.capture(frozen.base.id,frozen.base.revision,frozen.label,frozen.operation,token)) {
                        is Outcome.Success -> Outcome.Success(r.value to null)
                        is Outcome.Failure -> r; is Outcome.Unknown -> r; is Outcome.Unavailable -> r
                    }
                    is Request.Restore -> when (val r = versions.restore(frozen.base.id,frozen.preview.target.id,frozen.base.revision,frozen.label,frozen.operation,token)) {
                        is Outcome.Success -> Outcome.Success(null to r.value)
                        is Outcome.Failure -> r; is Outcome.Unknown -> r; is Outcome.Unavailable -> r
                    }
                } }
                currentCoroutineContext().ensureActive()
                when (result) {
                    is PortResult.Failed -> {
                        uncertain = uncertain || result.problem in setOf(Problem.PERSISTENCE,Problem.INTERNAL,Problem.TRANSPORT,Problem.UNKNOWN_RESULT,Problem.UNEXPECTED)
                        if (!uncertain) request = null
                        mutable.update { it.copy(pending = uncertain, mutationProblem = result.problem, preview = if (result.problem == Problem.STALE_REVISION && !uncertain) null else it.preview) }
                    }
                    is PortResult.Ok -> {
                        val capture = result.value.first; val restore = result.value.second
                        val valid = when (frozen) {
                            is Request.Capture -> capture != null && validVersion(capture,frozen.base.id,capture.id) && capture.label == frozen.label && capture.manifest == frozen.base && notEarlier(capture.createdAt,frozen.base.modifiedAt) && notEarlier(capture.createdAt,frozen.base.createdAt)
                            is Request.Restore -> restore != null && validRestore(frozen,restore)
                        }
                        if (!valid) { uncertain = true; mutable.update { it.copy(pending = true, mutationProblem = Problem.CORRUPT_DATA) } }
                        else {
                            request = null; uncertain = false
                            mutable.update { it.copy(pending = false, captureForm = false, label = "", recoveryLabel = "", preview = null, captured = capture, restored = restore) }
                            if (restore != null) try { onCommitted(restore.snapshot.id,restore.snapshot.revision) }
                            catch (error: Exception) { currentCoroutineContext().ensureActive(); mutable.update { it.copy(mutationProblem = Problem.UNEXPECTED) } }
                        }
                    }
                }
                // Both capture and restore replays are historical responses; always reread actual heads/pages.
                if (visible) { refreshHead(); refreshVersions() }
            } catch (e: CancellationException) {
                if (request != null) { uncertain = true; mutable.update { it.copy(pending = true) } }; throw e
            } finally { mutable.update { it.copy(mutationBusy = false) } }
        }
    }
    private fun notEarlier(a: UtcTimestamp,b: UtcTimestamp) = a.epochSeconds > b.epochSeconds || (a.epochSeconds == b.epochSeconds && a.nanoAdjustment >= b.nanoAdjustment)
    private fun validRestore(frozen: Request.Restore,r: DeckRestoreResult): Boolean {
        val base = frozen.base; val target = frozen.preview.target; val saved = r.snapshot; val recovery = r.recoveryVersion
        return base.revision.value < Long.MAX_VALUE && saved.id == base.id && saved.revision.value == base.revision.value + 1 && saved.createdAt == base.createdAt &&
            notEarlier(saved.modifiedAt,base.modifiedAt) && notEarlier(saved.modifiedAt,base.createdAt) && notEarlier(saved.modifiedAt,target.createdAt) &&
            saved == target.manifest.copy(id = base.id,createdAt = base.createdAt,revision = saved.revision,modifiedAt = saved.modifiedAt) &&
            validVersion(recovery,base.id,recovery.id) && recovery.id != target.id && recovery.label == frozen.label && recovery.manifest == base && recovery.createdAt == saved.modifiedAt
    }
    /** UI must first disclose that the frozen operation may already be committed. */
    fun abandonConfirmed() {
        if (request == null || mutable.value.mutationBusy) return
        request = null; uncertain = false
        mutable.update { it.copy(pending = false, mutationProblem = null, preview = null, captureForm = false, label = "", recoveryLabel = "") }
        if (visible) { refreshHead(); refreshVersions() }
    }
}
