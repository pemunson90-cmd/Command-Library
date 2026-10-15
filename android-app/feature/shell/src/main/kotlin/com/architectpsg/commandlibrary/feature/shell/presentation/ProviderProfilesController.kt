package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration

internal enum class ProviderProfileField { NAME, BASE, MODEL, API_VERSION }
internal enum class ProviderProfileConfirmation { DELETE, REMOVE_KEY }
internal data class ProviderProfileForm(
    val id: EntityId, val name: String = "", val base: String = "", val model: String = "",
    val apiVersion: String = "", val dialect: ProviderDialect = ProviderDialect.OPENAI_CHAT,
    val rejected: Set<ProviderProfileField> = emptySet(),
)
internal data class ProviderProfilesUiState(
    val visible: Boolean = false, val busy: Boolean = false,
    val items: List<StoredProviderProfile> = emptyList(), val nextCursor: String? = null,
    val selected: StoredProviderProfile? = null, val form: ProviderProfileForm? = null,
    val formRevision: Long = 0, val reloadRequired: Boolean = false,
    val confirmation: ProviderProfileConfirmation? = null, val problem: Problem? = null,
    val saved: Boolean = false, val settingsWarning: Boolean = false,
)

/** Retained by the root ViewModel. No plaintext credential is part of any retained state. */
internal class ProviderProfilesController(
    private val scope: CoroutineScope,
    private val actions: ProviderProfileActions,
    private val origin: (ResourceUri) -> Outcome<Origin>,
    private val portDispatcher: CoroutineDispatcher,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutable = MutableStateFlow(ProviderProfilesUiState())
    val state: StateFlow<ProviderProfilesUiState> = mutable.asStateFlow()
    private var ticket = 0L
    private var uncertainCreate = false
    private var reopen = false

    fun open() {
        mutable.value = mutable.value.copy(visible = true)
        if (state.value.busy) { reopen = true; return }
        refresh()
    }
    fun dismiss() {
        ++ticket
        mutable.value = mutable.value.copy(visible = false, confirmation = null, formRevision = state.value.formRevision + 1)
        // Never cancel a possibly committed mutation on window closure.
    }
    private fun ready() = state.value.visible && !state.value.busy
    fun newProfile() {
        if (!ready() || uncertainCreate) return
        ++ticket
        mutable.value = state.value.copy(selected = null, form = ProviderProfileForm(EntityId(idFactory())),
            formRevision = state.value.formRevision + 1, confirmation = null, problem = null, saved = false, reloadRequired = false)
    }
    fun select(id: EntityId) {
        if (!ready() || uncertainCreate) return
        run { t -> load(id, t) }
    }
    fun edit(field: ProviderProfileField, value: String, revision: Long) {
        val s = state.value
        if (!ready() || revision != s.formRevision || s.reloadRequired || uncertainCreate) return
        val f = s.form ?: return
        val valid = text(value, if (field == ProviderProfileField.BASE) 4096 else 1024, field == ProviderProfileField.API_VERSION)
        var next = f.copy(rejected = if (valid) f.rejected - field else f.rejected + field)
        if (valid || value.isEmpty()) next = when (field) {
            ProviderProfileField.NAME -> next.copy(name = value)
            ProviderProfileField.BASE -> next.copy(base = value)
            ProviderProfileField.MODEL -> next.copy(model = value)
            ProviderProfileField.API_VERSION -> next.copy(apiVersion = value)
        }
        mutable.value = s.copy(form = next, formRevision = s.formRevision + 1, confirmation = null, problem = null, saved = false)
    }
    fun dialect(value: ProviderDialect, revision: Long) {
        if (!ready() || revision != state.value.formRevision || state.value.reloadRequired || uncertainCreate) return
        val f = state.value.form ?: return
        mutable.value = state.value.copy(form = f.copy(dialect = value), formRevision = revision + 1, confirmation = null, saved = false)
    }
    fun refresh() {
        if (!ready()) return
        run { t ->
            page(null, t)
            val id = state.value.form?.id
            if (id != null && (state.value.selected != null || uncertainCreate || state.value.reloadRequired)) load(id, t)
        }
    }
    fun more() {
        if (!ready()) return
        val cursor = state.value.nextCursor ?: return
        run { t -> page(cursor, t) }
    }
    private suspend fun page(cursor: String?, t: Long) {
        when (val r = callPort(portDispatcher) { actions.page(PageRequest(25, cursor), it) }) {
            is PortResult.Ok -> publish(t) { it.copy(items = (if (cursor == null) emptyList() else it.items) + r.value.items.toList(), nextCursor = r.value.nextCursor) }
            is PortResult.Failed -> publish(t) { it.copy(problem = r.problem, nextCursor = if (r.problem == Problem.STALE_REVISION) null else it.nextCursor) }
        }
    }
    private fun adopt(value: StoredProviderProfile, s: ProviderProfilesUiState): ProviderProfilesUiState {
        val p = value.profile
        return s.copy(selected = value, form = ProviderProfileForm(p.id, p.name, p.baseUri?.value.orEmpty(), p.selectedModelId,
            (p.apiVersion as? Knowledge.Known)?.value.orEmpty(), p.dialect), formRevision = s.formRevision + 1,
            reloadRequired = false, confirmation = null)
    }
    private suspend fun load(id: EntityId, t: Long) {
        when (val r = callPort(portDispatcher) { actions.load(id, it) }) {
            is PortResult.Ok -> { uncertainCreate = false; publish(t) { adopt(r.value, it) } }
            is PortResult.Failed -> {
                if (r.problem == Problem.NOT_FOUND && uncertainCreate) {
                    uncertainCreate = false
                    publish(t) { it.copy(reloadRequired = false, problem = r.problem) }
                } else publish(t) { it.copy(problem = r.problem, reloadRequired = true) }
            }
        }
    }
    fun save(revision: Long) {
        val s = state.value
        if (!ready() || revision != s.formRevision) return
        val f = s.form ?: return
        if (uncertainCreate || s.reloadRequired) { refresh(); return }
        val bad = buildSet {
            if (!text(f.name, 1024)) add(ProviderProfileField.NAME)
            if (!text(f.base, 4096)) add(ProviderProfileField.BASE)
            if (!text(f.model, 1024)) add(ProviderProfileField.MODEL)
            if (!text(f.apiVersion, 1024, true)) add(ProviderProfileField.API_VERSION)
            addAll(f.rejected)
        }
        if (bad.isNotEmpty()) { mutable.value = s.copy(form = f.copy(rejected = bad), problem = Problem.INVALID_INPUT); return }
        val uri = ResourceUri(f.base)
        val admitted = try { origin(uri) } catch (_: Exception) { null }
        if (admitted !is Outcome.Success || admitted.value.scheme != "https") {
            mutable.value = s.copy(form = f.copy(rejected = f.rejected + ProviderProfileField.BASE), problem = Problem.INVALID_INPUT); return
        }
        val old = s.selected?.profile
        if (old?.secretRef != null && old.endpoint?.origin != admitted.value) {
            mutable.value = s.copy(problem = Problem.INVALID_INPUT); return
        }
        val endpoint = old?.endpoint?.copy(origin = admitted.value) ?: EndpointProfile(f.id, admitted.value,
            SnapshotList.empty(), null, 1, RetryPolicy(1, Duration.ZERO, Duration.ZERO, false))
        val api = if (f.apiVersion.isEmpty()) old?.apiVersion?.takeUnless { it is Knowledge.Known } ?: Knowledge.Absent("Not configured") else Knowledge.Known(f.apiVersion)
        val profile = ProviderProfile(f.id, f.name, f.dialect, endpoint, f.model,
            old?.capabilities ?: ProviderCapabilities(false, false, false, false, false, false), old?.secretRef,
            api, old?.extraHeaders ?: SnapshotList.empty(), uri)
        run(mutation = true) { t ->
            if (s.selected == null) uncertainCreate = true
            when (val r = callPort(portDispatcher) { actions.save(profile, s.selected?.revision, it) }) {
                is PortResult.Ok -> {
                    uncertainCreate = false
                    publish(t) { adopt(r.value.value, it).copy(saved = true, settingsWarning = r.value.settingsWarning, problem = null) }
                }
                is PortResult.Failed -> publish(t) { it.copy(problem = r.problem, reloadRequired = true) }
            }
            page(null, t)
        }
    }
    fun requestConfirmation(kind: ProviderProfileConfirmation, revision: Long) {
        if (ready() && !state.value.reloadRequired && state.value.selected != null && state.value.formRevision == revision)
            mutable.value = state.value.copy(confirmation = kind)
    }
    fun cancelConfirmation() { mutable.value = state.value.copy(confirmation = null) }
    fun confirm(revision: Long) {
        val s = state.value
        if (!ready() || s.reloadRequired || s.formRevision != revision) return
        val selected = s.selected ?: return
        val kind = s.confirmation ?: return
        mutable.value = s.copy(confirmation = null)
        if (kind == ProviderProfileConfirmation.REMOVE_KEY) keyMutation { actions.removeKey(selected.profile.id, selected.revision, it) }
        else run(mutation = true) { t ->
            when (val r = callPort(portDispatcher) { actions.delete(selected.profile.id, selected.revision, it) }) {
                is PortResult.Ok -> publish(t) { it.copy(selected = null, form = null, saved = true, settingsWarning = r.value.settingsWarning, problem = null, formRevision = it.formRevision + 1) }
                is PortResult.Failed -> publish(t) { it.copy(problem = r.problem, reloadRequired = true) }
            }
            page(null, t)
        }
    }
    /** Consumed once by a coroutine call; never copied into a state object or retried. */
    fun putKey(value: String, revision: Long) {
        val s = state.value
        if (!ready() || s.reloadRequired || revision != s.formRevision) return
        val selected = s.selected ?: return
        if (value.isBlank() || value.length > 16384 || value.any { Character.isISOControl(it) }) {
            mutable.value = s.copy(problem = Problem.INVALID_INPUT); return
        }
        keyMutation { actions.putKey(selected.profile.id, selected.revision, value, it) }
    }
    private fun keyMutation(action: suspend (CancelToken) -> Outcome<StoredProviderProfile>) {
        run(mutation = true) { t ->
            when (val r = callPort(portDispatcher, action)) {
                is PortResult.Ok -> publish(t) { adopt(r.value, it).copy(saved = true, problem = null) }
                is PortResult.Failed -> publish(t) { it.copy(problem = r.problem, reloadRequired = true) }
            }
            page(null, t)
        }
    }
    private fun publish(t: Long, update: (ProviderProfilesUiState) -> ProviderProfilesUiState) {
        if (t == ticket) mutable.value = update(state.value)
    }
    private fun run(mutation: Boolean = false, block: suspend (Long) -> Unit) {
        val t = ++ticket
        mutable.value = state.value.copy(busy = true, problem = null, saved = false, confirmation = null)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { block(t) }
            catch (e: CancellationException) {
                if (mutation) mutable.value = state.value.copy(reloadRequired = true, problem = Problem.UNKNOWN_RESULT)
                throw e
            } finally {
                mutable.value = state.value.copy(busy = false)
                if (t != ticket && mutation) mutable.value = state.value.copy(reloadRequired = true)
                if (reopen && state.value.visible) { reopen = false; refresh() }
            }
        }
    }
    private fun text(value: String, max: Int, empty: Boolean = false): Boolean {
        if (value.isEmpty()) return empty
        if (value.isBlank() || value.length > max || value.any { Character.isISOControl(it) }) return false
        return try { Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value)).remaining() <= max }
        catch (_: Exception) { false }
    }
}
