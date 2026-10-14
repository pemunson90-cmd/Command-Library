package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class ProfileValidation { NAME, LIFE, STEP }
internal data class ProfileEditor(
    val original: PlayerProfileSnapshot?, val lifeLabel: String,
    val name: String = "", val life: String = "20", val step: String = "1",
    val counterDefinitions: SnapshotList<CounterDefinition> = original?.value?.counterDefinitions
        ?: SnapshotList.of(CounterDefinition(EntityId("life"), lifeLabel, null, null, 20,
            CounterLossPolicy.AtOrBelow(0), false, CounterRole.LIFE)),
    val counterManager: Boolean = false,
    val counterEditor: ProfileCounterEditor? = null,
    val pendingCounterDelete: CounterDefinition? = null,
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
    private val counterIds: () -> EntityId = { EntityId(UUID.randomUUID().toString()) },
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
    fun showCounters() = change { it.copy(counterManager = true) }
    fun showProfile() = change { it.copy(counterManager = false, counterEditor = null, pendingCounterDelete = null) }
    fun addCounter() {
        val state = mutableState.value
        val editor = state.editor ?: return
        if (state.busy || !editor.counterManager || editor.counterEditor != null || editor.pendingCounterDelete != null ||
            editor.counterDefinitions.size >= MaxProfileCounterDefinitions) return
        val id = try { counterIds() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(problem = Problem.INTERNAL) }; return }
        if (id.value.isBlank() || editor.counterDefinitions.any { it.id == id }) {
            mutableState.update { it.copy(problem = Problem.INTERNAL) }; return
        }
        change { it.copy(counterEditor = ProfileCounterEditor(id)) }
    }
    fun editCounter(id: EntityId) = change { editor ->
        val value = editor.counterDefinitions.find { it.id == id }
        if (!editor.counterManager || editor.counterEditor != null || editor.pendingCounterDelete != null || value?.editableInProfile != true) editor
        else editor.copy(counterEditor = value.profileCounterEditor())
    }
    private fun changeCounter(update: (ProfileCounterEditor) -> ProfileCounterEditor) = change { editor ->
        editor.copy(counterEditor = editor.counterEditor?.let { update(it).copy(validation = null) })
    }
    fun changeCounterLabel(value: String) = changeCounter { it.copy(label = value) }
    fun changeCounterInitial(value: String) = changeCounter { it.copy(initial = value) }
    fun changeCounterMinimum(value: String) = changeCounter { it.copy(minimum = value) }
    fun changeCounterMaximum(value: String) = changeCounter { it.copy(maximum = value) }
    fun changeCounterLoss(value: ProfileCounterLoss) = changeCounter { it.copy(loss = value) }
    fun changeCounterThreshold(value: String) = changeCounter { it.copy(threshold = value) }
    fun changeCounterUseColor(value: Boolean) = changeCounter { it.copy(useColor = value) }
    fun changeCounterColor(value: String) = changeCounter { it.copy(color = value) }
    fun changeCounterReset(value: CounterResetPolicy) = changeCounter { it.copy(reset = value) }
    fun closeCounterEditor() = change { it.copy(counterEditor = null) }
    fun applyCounter() = change { editor ->
        val counter = editor.counterEditor
        if (!editor.counterManager || counter == null) editor else when (val parsed = counter.parse()) {
            is ProfileCounterEditResult.Invalid -> editor.copy(counterEditor = counter.copy(validation = parsed.field))
            is ProfileCounterEditResult.Valid -> {
                val old = counter.original
                val index = editor.counterDefinitions.indexOfFirst { it.id == counter.id }
                if ((old != null && (index < 0 || !old.editableInProfile || editor.counterDefinitions[index] != old)) ||
                    (old == null && (index >= 0 || editor.counterDefinitions.size >= MaxProfileCounterDefinitions))) editor
                else {
                    val definitions = editor.counterDefinitions.toMutableList()
                    if (old == null) definitions.add(parsed.value) else definitions[index] = parsed.value
                    editor.copy(counterDefinitions = SnapshotList.copyOf(definitions), counterEditor = null)
                }
            }
        }
    }
    fun requestCounterDelete(id: EntityId) = change { editor ->
        val value = editor.counterDefinitions.find { it.id == id }
        if (!editor.counterManager || editor.counterEditor != null || editor.pendingCounterDelete != null || value?.editableInProfile != true) editor
        else editor.copy(pendingCounterDelete = value)
    }
    fun dismissCounterDelete() = change { it.copy(pendingCounterDelete = null) }
    fun confirmCounterDelete() = change { editor ->
        val target = editor.pendingCounterDelete
        if (!editor.counterManager || target?.editableInProfile != true || editor.counterEditor != null) editor
        else editor.copy(counterDefinitions = SnapshotList.copyOf(editor.counterDefinitions.filter { it.id != target.id }), pendingCounterDelete = null)
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
        if (editor.counterManager || editor.counterEditor != null || editor.pendingCounterDelete != null) return
        val life = editor.life.toIntOrNull()
        val step = editor.step.toIntOrNull()
        val validation = when {
            editor.name.isBlank() && editor.name != editor.original?.value?.name -> ProfileValidation.NAME
            life == null -> ProfileValidation.LIFE
            step == null || step <= 0 -> ProfileValidation.STEP
            else -> null
        }
        if (validation != null) { mutableState.update { it.copy(validation=validation) };return }
        val value = try {
            val old = editor.original?.value
            val definitions = SnapshotList.copyOf(editor.counterDefinitions.map { if(it.role==CounterRole.LIFE)it.copy(initial=life!!)else it })
            if (old == null) PlayerProfileDraft(editor.name,life!!,definitions,SeatAppearance(null,null),SnapshotList.empty(),step!!)
            else old.copy(name=editor.name,preferredLife=life!!,adjustmentStep=step!!,counterDefinitions=definitions)
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
    /** Dismisses only the modal; an already-started deletion remains owned by the scope. */
    fun dismissDelete() {
        mutableState.update { it.copy(pendingDelete=null,problem=if(it.busy)it.problem else null) }
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
