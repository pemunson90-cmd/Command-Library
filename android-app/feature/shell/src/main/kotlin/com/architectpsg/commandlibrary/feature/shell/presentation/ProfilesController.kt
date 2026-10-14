package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class ProfileValidation { NAME, LIFE, STEP }
internal data class ProfileEditor(
    val original: PlayerProfileSnapshot?, val lifeLabel: String,
    val name: String = "", val life: String = "20", val step: String = "1",
)
internal data class ProfilesUiState(
    val items: SnapshotList<PlayerProfileSnapshot> = SnapshotList.empty(),
    val nextCursor: String? = null, val loading: Boolean = true, val busy: Boolean = false,
    val editor: ProfileEditor? = null, val pendingDelete: PlayerProfileSnapshot? = null,
    val problem: Problem? = null, val validation: ProfileValidation? = null,
)

/** Scoped UI coordination over the complete profile port; never owns a game service or detached job. */
internal class ProfilesController(
    private val scope: CoroutineScope,
    private val repository: PlayerProfileRepository,
    private val portDispatcher: CoroutineDispatcher,
) {
    private val mutableState = MutableStateFlow(ProfilesUiState())
    val state: StateFlow<ProfilesUiState> = mutableState.asStateFlow()
    private var listJob: Job? = null
    private var generation = 0L

    fun refresh() {
        if (mutableState.value.busy) return
        load(null)
    }
    fun loadMore() {
        val current = mutableState.value
        if (current.busy || current.loading) return
        current.nextCursor?.let(::load)
    }
    private fun load(cursor: String?) {
        listJob?.cancel()
        val ticket = ++generation
        mutableState.update { it.copy(
            items = if (cursor == null) SnapshotList.empty() else it.items,
            nextCursor = if (cursor == null) null else it.nextCursor,
            loading = true, problem = null,
        ) }
        listJob = scope.launch {
            try {
                val result = callPort(portDispatcher) { repository.list(PageRequest(20,cursor),it) }
                currentCoroutineContext().ensureActive()
                if (ticket != generation) return@launch
                when (result) {
                    is PortResult.Failed -> mutableState.update { it.copy(loading=false,problem=result.problem) }
                    is PortResult.Ok -> mutableState.update { current ->
                        val merged = linkedMapOf<EntityId,PlayerProfileSnapshot>()
                        if (cursor != null) current.items.forEach { merged[it.id]=it }
                        result.value.items.forEach { row ->
                            if (merged[row.id]?.let { it.revision.value > row.revision.value } != true) merged[row.id]=row
                        }
                        current.copy(items=SnapshotList.copyOf(merged.values),nextCursor=result.value.nextCursor,loading=false)
                    }
                }
            } finally {
                if (ticket == generation) mutableState.update { it.copy(loading=false) }
            }
        }
    }

    fun create(lifeLabel: String) {
        if (!mutableState.value.busy) mutableState.update { it.copy(editor=ProfileEditor(null,lifeLabel),pendingDelete=null,problem=null,validation=null) }
    }
    fun edit(profile: PlayerProfileSnapshot) {
        if (!mutableState.value.busy) mutableState.update { it.copy(editor=profile.editor(),pendingDelete=null,problem=null,validation=null) }
    }
    fun changeName(value:String) = change { it.copy(name=value) }
    fun changeLife(value:String) = change { it.copy(life=value) }
    fun changeStep(value:String) = change { it.copy(step=value) }
    private fun change(update:(ProfileEditor)->ProfileEditor) {
        if (!mutableState.value.busy) mutableState.update { it.copy(editor=it.editor?.let(update),validation=null,problem=null) }
    }
    fun closeEditor() {
        if (!mutableState.value.busy) mutableState.update { it.copy(editor=null,validation=null,problem=null) }
    }
    fun reloadEditor() {
        val original = mutableState.value.editor?.original ?: return
        perform({ repository.read(original.id,it) }) { latest ->
            mutableState.update { it.copy(editor=latest.editor(),problem=null,validation=null) }
        }
    }
    fun save() {
        val current = mutableState.value
        if (current.busy) return
        val editor = current.editor ?: return
        val life = editor.life.toIntOrNull()
        val step = editor.step.toIntOrNull()
        val validation = when {
            editor.name.isBlank() -> ProfileValidation.NAME
            life == null -> ProfileValidation.LIFE
            step == null || step <= 0 -> ProfileValidation.STEP
            else -> null
        }
        if (validation != null) { mutableState.update { it.copy(validation=validation) };return }
        val value = try {
            val old = editor.original?.value
            if (old == null) PlayerProfileDraft(editor.name,life!!,
                SnapshotList.of(CounterDefinition(EntityId("life"),editor.lifeLabel,null,null,life,
                    CounterLossPolicy.AtOrBelow(0),false,CounterRole.LIFE)),SeatAppearance(null,null),SnapshotList.empty(),step!!)
            else old.copy(name=editor.name,preferredLife=life!!,adjustmentStep=step!!,
                counterDefinitions=SnapshotList.copyOf(old.counterDefinitions.map { if(it.role==CounterRole.LIFE)it.copy(initial=life)else it }))
        } catch (_: IllegalArgumentException) {
            mutableState.update { it.copy(validation=ProfileValidation.LIFE) };return
        }
        val original = editor.original
        perform({ cancel -> if(original==null)repository.create(value,cancel)else repository.update(original.id,value,original.revision,cancel) }) {
            mutableState.update { it.copy(editor=null,validation=null) }
            refresh()
        }
    }
    fun requestDelete(profile: PlayerProfileSnapshot) {
        if (!mutableState.value.busy) mutableState.update { it.copy(pendingDelete=profile,problem=null) }
    }
    fun dismissDelete() {
        if (!mutableState.value.busy) mutableState.update { it.copy(pendingDelete=null,problem=null) }
    }
    fun confirmDelete() {
        val target = mutableState.value.pendingDelete ?: return
        perform({ repository.delete(target.id,target.revision,it) }) {
            mutableState.update { it.copy(pendingDelete=null) }
            refresh()
        }
    }
    private fun <T> perform(action:suspend(CancelToken)->Outcome<T>,accepted:(T)->Unit) {
        if (mutableState.value.busy) return
        ++generation
        listJob?.cancel()
        mutableState.update { it.copy(busy=true,loading=false,problem=null,validation=null) }
        scope.launch {
            try {
                val result = callPort(portDispatcher,action)
                currentCoroutineContext().ensureActive()
                when(result) {
                    is PortResult.Failed -> mutableState.update { it.copy(problem=result.problem) }
                    is PortResult.Ok -> {
                        mutableState.update { it.copy(busy=false) }
                        accepted(result.value)
                    }
                }
            } finally { mutableState.update { it.copy(busy=false) } }
        }
    }
    private fun PlayerProfileSnapshot.editor() = ProfileEditor(this,
        value.counterDefinitions.single { it.role==CounterRole.LIFE }.label,value.name,value.preferredLife.toString(),value.adjustmentStep.toString())
}
