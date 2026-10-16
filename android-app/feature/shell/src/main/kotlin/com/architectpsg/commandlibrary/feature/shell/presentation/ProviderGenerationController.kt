package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ProviderGenerationState(
    val visible: Boolean = false, val capture: HistoryPromptCapture? = null,
    val profiles: List<StoredProviderProfile> = emptyList(), val cursor: String? = null,
    val selected: StoredProviderProfile? = null, val busy: Boolean = false, val sending: Boolean = false,
    val consent: Boolean = false, val turn: Turn? = null, val uncertain: Boolean = false,
    val problem: Problem? = null, val frame: Long = 0,
    val maxTokensText: String = "", val maxTokensRejected: Boolean = false,
) {
    val maxTokens: Int? get() = maxTokensText.takeIf { it.matches(Regex("[1-9][0-9]{0,9}")) }?.toIntOrNull()
    val optionsReady: Boolean get() = !maxTokensRejected && (maxTokensText.isEmpty() || maxTokens != null) &&
        selected?.profile?.let { profile -> when (profile.dialect) {
            ProviderDialect.OPENAI_CHAT -> true
            ProviderDialect.OPENAI_RESPONSES -> profile.apiVersion !is Knowledge.Known
            ProviderDialect.ANTHROPIC -> maxTokens != null && profile.apiVersion is Knowledge.Known
            else -> false
        } } == true
}

/** An immutable preview and explicit send fence; recreation has no remembered dispatch intent. */
internal class ProviderGenerationController(private val scope: CoroutineScope,
    private val profiles: LocalProviderProfiles, private val generation: ProviderGenerationAccess,
    private val history: AiHistoryAccess, private val dispatcher: CoroutineDispatcher,
    private val id: () -> EntityId = { EntityId(UUID.randomUUID().toString()) }) {
    private val mutable = MutableStateFlow(ProviderGenerationState())
    val state = mutable.asStateFlow()
    private var read: Job? = null
    private var send: Job? = null
    private var conversation: NewConversation? = null
    private var attemptId: EntityId? = null
    fun open(capture: HistoryPromptCapture) {
        if (state.value.sending || state.value.uncertain) { reopen(); return }
        read?.cancel()
        conversation = null; attemptId = null
        mutable.value = ProviderGenerationState(visible = true, capture = capture, frame = state.value.frame + 1)
        loadProfiles()
    }
    fun reopen() { if (state.value.capture != null) mutable.value = state.value.copy(visible = true) }
    fun abandonUncertain() {
        if (state.value.sending || state.value.busy) return
        read?.cancel(); conversation = null; attemptId = null
        mutable.value = ProviderGenerationState(frame = state.value.frame + 1)
    }
    fun hide() {
        read?.cancel(); send?.cancel()
        mutable.value = state.value.copy(visible = false, consent = false, frame = state.value.frame + 1)
    }
    fun loadProfiles(more: Boolean = false) {
        val s = state.value
        if (!s.visible || s.sending || s.uncertain) return
        val cursor = if (more) s.cursor ?: return else null
        read?.cancel()
        val frame = s.frame + 1
        mutable.value = s.copy(busy = true, consent = false, selected = null, frame = frame)
        read = scope.launch {
            try {
                val result = callPort(dispatcher) { profiles.list(PageRequest(25, cursor), it) }
                if (state.value.frame != frame || !state.value.visible) return@launch
                mutable.value = when (result) {
                    is PortResult.Ok -> state.value.copy(profiles = (if (more) s.profiles else emptyList()) + result.value.items,
                        cursor = result.value.nextCursor, problem = null)
                    is PortResult.Failed -> state.value.copy(problem = result.problem)
                }
            } finally { if (state.value.frame == frame) mutable.value = state.value.copy(busy = false) }
        }
    }
    fun select(profile: StoredProviderProfile) {
        val s = state.value
        if (!s.visible || s.busy || s.sending || s.uncertain || profile !in s.profiles) return
        mutable.value = s.copy(selected = profile, consent = false, frame = s.frame + 1, problem = null)
    }
    fun consent(value: Boolean, frame: Long) {
        val s = state.value
        if (s.visible && !s.busy && !s.sending && !s.uncertain && s.selected != null && s.frame == frame)
            mutable.value = s.copy(consent = value)
    }
    fun maxTokens(value: String, frame: Long) {
        val s = state.value
        if (!s.visible || s.busy || s.sending || s.uncertain || s.frame != frame) return
        mutable.value = s.copy(maxTokensText = if (value.length <= 10) value else s.maxTokensText,
            maxTokensRejected = value.length > 10 || value.isNotEmpty() &&
                (!value.matches(Regex("[1-9][0-9]{0,9}")) || value.toIntOrNull() == null),
            consent = false, frame = s.frame + 1)
    }
    fun cancel() { send?.cancel() }
    fun send(frame: Long) {
        val s = state.value; val capture = s.capture ?: return; val selected = s.selected ?: return
        if (!s.visible || s.busy || s.sending || s.uncertain || !s.consent || !s.optionsReady || s.frame != frame) return
        val prior = s.turn
        if (prior != null && prior.status !in setOf(TurnStatus.COMPLETE, TurnStatus.INTERRUPTED, TurnStatus.FAILED)) return
        val header = conversation ?: NewConversation(id(), capture.artifact.deckId, capture.mode, capture.mode.name).also { conversation = it }
        val attempt = id().also { attemptId = it }
        val input = NewHistoryTurn(attempt, HistoryTurnSource.PROVIDER_GENERATION, capture.artifact, capture.provenance,
            "", selected.profile.id, selected.profile.selectedModelId, prior?.id)
        val request = GenerationRequest(attempt, SnapshotList.of(GenerationMessage(MessageRole.USER, capture.artifact.rendered, null)),
            s.maxTokens, null, null, null, SnapshotList.empty())
        mutable.value = s.copy(sending = true, consent = false, turn = null, problem = null)
        send = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var acknowledged = false
            try {
                val result = callPort(dispatcher) { token -> generation.run(selected, header, input, request, token) { turn ->
                    withContext(scope.coroutineContext.minusKey(Job)) {
                        if (attemptId == attempt) mutable.value = state.value.copy(turn = turn)
                    }
                } }
                if (attemptId != attempt) return@launch
                mutable.value = when (result) {
                    is PortResult.Ok -> { acknowledged = true; state.value.copy(turn = result.value, uncertain = false) }
                    is PortResult.Failed -> state.value.copy(problem = result.problem, uncertain = true)
                }
            } finally {
                if (attemptId == attempt) mutable.value = state.value.copy(sending = false,
                    uncertain = !acknowledged || state.value.uncertain, consent = false)
            }
        }
    }
    /** Explicit reload of the original ID can resolve uncertainty; it never sends or appends. */
    fun reload() {
        val original = attemptId ?: return
        if (state.value.sending || state.value.busy) return
        mutable.value = state.value.copy(visible = true, busy = true, consent = false)
        read = scope.launch {
            try {
                val result = callPort(dispatcher) { token -> when (val opened = history.open(token)) {
                    is Outcome.Success -> opened.value.getTurn(original, token)
                    is Outcome.Failure -> opened
                    is Outcome.Unavailable -> opened
                    is Outcome.Unknown -> opened
                } }
                mutable.value = when (result) {
                    is PortResult.Ok -> state.value.copy(turn = result.value, uncertain = result.value.status in setOf(TurnStatus.PENDING, TurnStatus.STREAMING), problem = null)
                    is PortResult.Failed -> state.value.copy(problem = result.problem, uncertain = true)
                }
            } finally { mutable.value = state.value.copy(busy = false) }
        }
    }
}
