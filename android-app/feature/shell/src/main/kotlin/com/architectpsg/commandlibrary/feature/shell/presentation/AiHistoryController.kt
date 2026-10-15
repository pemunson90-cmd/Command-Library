package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.AiHistoryAccess
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class HistoryPromptCapture(val mode: OfflineLabMode, val artifact: PromptArtifact,
    val provenance: HistoryPromptProvenance)
internal data class OfflineHistoryCapture(val prompt: HistoryPromptCapture, val reply: String)
internal data class AiHistoryState(
    val visible: Boolean = false, val deckId: DeckId? = null, val deckName: String = "",
    val conversations: List<Conversation> = emptyList(), val conversationCursor: String? = null,
    val selected: Conversation? = null, val turns: List<HistoryTurnSummary> = emptyList(), val turnCursor: String? = null,
    val turn: Turn? = null, val capture: OfflineHistoryCapture? = null, val title: String = "",
    val titleRejected: Boolean = false, val busy: Boolean = false, val writing: Boolean = false,
    val problem: Problem? = null, val saved: Boolean = false, val confirmDelete: Boolean = false,
    val uncertainSave: Boolean = false, val frame: Long = 0,
)

/** Reads have navigation fences; possible committed writes survive dialog closure and route changes. */
internal class AiHistoryController(
    private val scope: CoroutineScope, private val access: AiHistoryAccess,
    private val dispatcher: CoroutineDispatcher, private val id: () -> EntityId = { EntityId(UUID.randomUUID().toString()) },
) {
    private val mutable = MutableStateFlow(AiHistoryState())
    val state = mutable.asStateFlow()
    private var readJob: Job? = null
    private var writeJob: Job? = null
    private data class SaveIntent(val conversationId: EntityId, val turnId: EntityId,
        val capture: OfflineHistoryCapture, val title: String)
    private var intent: SaveIntent? = null
    private fun replace(next: AiHistoryState) {
        readJob?.cancel()
        mutable.value = next.copy(frame = mutable.value.frame + 1, busy = false, problem = null, confirmDelete = false)
    }
    fun open(deck: DeckSnapshot) {
        if (mutable.value.writing) return
        val old = mutable.value
        if (old.uncertainSave && old.capture != null) {
            replace(old.copy(visible = true)) // Keep the original IDs/content until explicit resolution/discard.
            return
        }
        replace(AiHistoryState(visible = true, deckId = deck.id, deckName = deck.name))
        refresh()
    }
    fun capture(value: OfflineHistoryCapture, deckName: String) {
        if (mutable.value.writing || intent != null) return
        replace(AiHistoryState(visible = true, deckId = value.prompt.artifact.deckId, deckName = deckName,
            capture = value, title = value.prompt.mode.name))
    }
    fun hide() { replace(mutable.value.copy(visible = false)) }
    fun title(value: String, frame: Long) {
        val s = state.value
        if (!s.visible || s.writing || s.uncertainSave || frame != s.frame || s.capture == null) return
        val valid = validTitle(value)
        mutable.value = s.copy(title = if (value.length <= AiHistoryLimits.MAX_TITLE_BYTES) value else s.title,
            titleRejected = !valid, frame = s.frame + 1, problem = null)
    }
    private fun validTitle(value: String): Boolean = try {
        value.isNotBlank() && value.length <= AiHistoryLimits.MAX_TITLE_BYTES && Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(value)).remaining() <= AiHistoryLimits.MAX_TITLE_BYTES
    } catch (_: Exception) { false }
    private suspend fun <T> withHistory(cancel: CancelToken, action: suspend (LocalAiHistory) -> Outcome<T>): Outcome<T> =
        when (val opened = access.open(cancel)) {
            is Outcome.Success -> action(opened.value)
            is Outcome.Failure -> opened
            is Outcome.Unavailable -> opened
            is Outcome.Unknown -> opened
        }
    private fun <T> read(block: suspend (LocalAiHistory, CancelToken) -> Outcome<T>, accept: (T, AiHistoryState) -> AiHistoryState) {
        val s = state.value
        if (!s.visible || s.writing) return
        readJob?.cancel()
        val frame = s.frame + 1
        mutable.value = s.copy(frame = frame, busy = true, problem = null, confirmDelete = false)
        readJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = callPort(dispatcher) { cancel -> withHistory(cancel) { block(it, cancel) } }
                currentCoroutineContext().ensureActive()
                if (state.value.frame != frame || !state.value.visible) return@launch
                mutable.value = when (result) {
                    is PortResult.Ok -> accept(result.value, state.value)
                    is PortResult.Failed -> state.value.copy(problem = result.problem,
                        conversationCursor = if (result.problem == Problem.STALE_REVISION) null else state.value.conversationCursor,
                        turnCursor = if (result.problem == Problem.STALE_REVISION) null else state.value.turnCursor)
                }
            } finally { if (state.value.frame == frame) mutable.value = state.value.copy(busy = false) }
        }
    }
    fun refresh(more: Boolean = false) {
        val s = state.value; val deck = s.deckId ?: return
        if (s.capture != null || s.uncertainSave) return
        val cursor = if (more) s.conversationCursor ?: return else null
        read({ port, token -> port.pageConversations(deck, PageRequest(25, cursor), token) }) { page, current ->
            current.copy(conversations = (if (more) current.conversations else emptyList()) + page.items,
                conversationCursor = page.nextCursor, selected = null, turns = emptyList(), turnCursor = null, turn = null, saved = false)
        }
    }
    fun selectConversation(id: EntityId) {
        if (state.value.capture != null) return
        read({ port, token -> port.getConversation(id, token) }) { conversation, current ->
            if (conversation.deckId != current.deckId) current.copy(problem = Problem.CORRUPT_DATA)
            else current.copy(selected = conversation, turns = emptyList(), turnCursor = null, turn = null)
        }
    }
    fun loadTurns(more: Boolean = false) {
        val s = state.value; val selected = s.selected ?: return
        val cursor = if (more) s.turnCursor ?: return else null
        read({ port, token -> port.pageTurns(selected.id, PageRequest(25, cursor), token) }) { page, current ->
            current.copy(turns = (if (more) current.turns else emptyList()) + page.items, turnCursor = page.nextCursor, turn = null)
        }
    }
    fun selectTurn(id: EntityId) {
        val parent = state.value.selected ?: return
        read({ port, token -> port.getTurn(id, token) }) { turn, current ->
            if (turn.conversationId != parent.id || turn.prompt.deckId != current.deckId) current.copy(problem = Problem.CORRUPT_DATA)
            else current.copy(turn = turn)
        }
    }
    fun back() {
        val s = state.value
        if (s.writing || s.uncertainSave) return
        if (s.capture != null) { replace(s.copy(capture = null, saved = false)); refresh() }
        else if (s.turn != null) replace(s.copy(turn = null))
        else if (s.selected != null) { replace(s.copy(selected = null, turns = emptyList(), turnCursor = null)); refresh() }
    }
    fun reviewCurrent(action: (Turn) -> Boolean) {
        val s = state.value; val turn = s.turn ?: return
        if (!s.visible || s.busy || s.writing) return
        if (action(turn)) hide() else mutable.value = s.copy(problem = Problem.STALE_REVISION)
    }
    fun discardPendingCapture() {
        val s = state.value
        if (s.writing) return
        intent = null
        replace(s.copy(capture = null, uncertainSave = false, saved = false))
        refresh() // This discards pending UI intent, never promises rollback of already stored records.
    }
    fun save(frame: Long) {
        val s = state.value; val capture = s.capture ?: return
        if (!s.visible || s.writing || s.busy || s.saved || frame != s.frame || s.titleRejected || !validTitle(s.title)) return
        val operation = intent ?: SaveIntent(id(), id(), capture, s.title).also { intent = it }
        if (operation.capture != capture || operation.title != s.title) return
        replace(s)
        mutable.value = state.value.copy(writing = true)
        writeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var acknowledged = false
            try {
                val result = callPort(dispatcher) { token -> withHistory(token) { persist(it, operation, token) } }
                currentCoroutineContext().ensureActive()
                val current = state.value
                if (result is PortResult.Ok) {
                    acknowledged = true; intent = null
                    if (current.capture == operation.capture) mutable.value = current.copy(saved = true, uncertainSave = false, problem = null)
                } else if (current.capture == operation.capture) mutable.value = current.copy(
                    problem = (result as PortResult.Failed).problem, uncertainSave = true)
            } finally {
                val current = state.value
                mutable.value = current.copy(writing = false, uncertainSave = !acknowledged && intent != null || current.uncertainSave)
            }
        }
    }
    private suspend fun persist(port: LocalAiHistory, operation: SaveIntent, token: CancelToken): Outcome<Turn> {
        val capture = operation.capture; val artifact = capture.prompt.artifact
        val wanted = NewConversation(operation.conversationId, artifact.deckId, capture.prompt.mode, operation.title)
        val loaded = port.getConversation(wanted.id, token)
        val header = when {
            loaded is Outcome.Success -> loaded.value
            loaded is Outcome.Unavailable && loaded.reason == UnavailableReason.NOT_FOUND -> when (val created = port.createConversation(wanted, token)) {
                is Outcome.Success -> created.value
                is Outcome.Failure -> return created
                is Outcome.Unavailable -> return created
                is Outcome.Unknown -> return created
            }
            loaded is Outcome.Failure -> return loaded
            loaded is Outcome.Unavailable -> return loaded
            loaded is Outcome.Unknown -> return loaded
            else -> return invalidStored()
        }
        if (header.deckId != wanted.deckId || header.mode != wanted.mode || header.title != wanted.title) return invalidStored()
        val existing = port.getTurn(operation.turnId, token)
        if (existing is Outcome.Success) return if (sameCapture(existing.value, operation)) existing else invalidStored()
        if (existing !is Outcome.Unavailable || existing.reason != UnavailableReason.NOT_FOUND) return existing
        val appended = port.appendTurn(header.id, header.revision, NewHistoryTurn(operation.turnId,
            HistoryTurnSource.OFFLINE_CAPTURE, artifact, capture.prompt.provenance, capture.reply), token)
        return if (appended is Outcome.Success && !sameCapture(appended.value, operation)) invalidStored() else appended
    }
    private fun sameCapture(turn: Turn, operation: SaveIntent): Boolean = turn.id == operation.turnId &&
        turn.conversationId == operation.conversationId && turn.source == HistoryTurnSource.OFFLINE_CAPTURE &&
        turn.mode == operation.capture.prompt.mode && turn.prompt == operation.capture.prompt.artifact &&
        turn.provenance == operation.capture.prompt.provenance && turn.rawContent == operation.capture.reply &&
        turn.status == TurnStatus.COMPLETE && turn.providerProfileId == null && turn.modelId == null &&
        turn.retryOf == null && turn.ownerEpoch == null && turn.usage is Knowledge.Unknown
    private fun invalidStored(): Outcome<Nothing> = Outcome.Failure(DomainFailure(FailureCode.CORRUPT_DATA,
        "Saved history does not match this capture", SnapshotList.empty()))
    fun askDelete() { if (state.value.visible && !state.value.busy && !state.value.writing && state.value.selected != null)
        mutable.value = state.value.copy(confirmDelete = true) }
    fun cancelDelete() { mutable.value = state.value.copy(confirmDelete = false) }
    fun delete(frame: Long) {
        val s = state.value; val selected = s.selected ?: return
        if (!s.visible || s.busy || s.writing || !s.confirmDelete || frame != s.frame) return
        replace(s)
        mutable.value = state.value.copy(writing = true)
        writeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = callPort(dispatcher) { token -> withHistory(token) { it.deleteConversation(selected.id, selected.revision, token) } }
                currentCoroutineContext().ensureActive()
                val current = state.value
                if (current.selected?.id == selected.id) {
                    mutable.value = if (result is PortResult.Ok) current.copy(selected = null, turn = null, turns = emptyList(), turnCursor = null)
                    else current.copy(problem = (result as PortResult.Failed).problem, confirmDelete = false)
                }
            } finally {
                mutable.value = state.value.copy(writing = false)
                if (state.value.visible && state.value.selected == null) refresh()
            }
        }
    }
}
