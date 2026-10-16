package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.ManualTagPlanning
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class ManualTagsState(
    val deck: DeckSnapshot? = null,
    val pool: PersonalTagPool? = null,
    val draft: SnapshotList<PersonalTag> = SnapshotList.empty(),
    val selectedEntries: Set<EntityId> = emptySet(),
    val selectedTags: Set<EntityId> = emptySet(),
    val query: String = "",
    val name: String = "",
    val editingId: EntityId? = null,
    val busy: Boolean = false,
    val conflict: Boolean = false,
    val uncertain: Boolean = false,
    val problem: Problem? = null,
    val closeRequested: Boolean = false,
) {
    val dirty: Boolean get() = pool != null && draft != pool.tags
    val visibleTags: List<PersonalTag> get() {
        val key = searchKey(query)
        return draft.filter { !it.retired && searchKey(it.name).contains(key) }
    }
}
private fun searchKey(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT)

/** Vocabulary CAS and deck patch acknowledgements remain distinct; never silently merge conflicts. */
internal class ManualTagsController(
    private val scope: CoroutineScope,
    private val repository: LocalPersonalTags,
    private val planning: ManualTagPlanning,
    private val dispatcher: CoroutineDispatcher,
    private val current: (DeckSnapshot) -> Boolean,
    private val apply: (DeckSnapshot, DeckPatch) -> Boolean,
    private val nextId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutable = MutableStateFlow(ManualTagsState())
    val state: StateFlow<ManualTagsState> = mutable.asStateFlow()
    private var ticket = 0L
    /** Disposed UI callbacks cannot act on a different pool, selection or deck. */
    fun rendered(frame: ManualTagsState, action: () -> Unit) {
        if (mutable.value === frame) action()
    }
    fun open(deck: DeckSnapshot) {
        if (mutable.value.deck != null || !current(deck)) return
        mutable.value = ManualTagsState(deck = deck)
        reload()
    }
    fun query(value: String) { mutable.update { it.copy(query = value) } }
    fun name(value: String) { if (!mutable.value.busy) mutable.update { it.copy(name = value, problem = null) } }
    fun edit(id: EntityId) {
        if (mutable.value.busy) return
        val tag = mutable.value.draft.find { it.id == id } ?: return
        mutable.update { it.copy(editingId = id, name = tag.name, problem = null) }
    }
    fun putName() {
        val s = mutable.value
        if (s.busy || s.pool == null || s.conflict || s.uncertain) return
        // Persistence remains authoritative for strict Unicode/size/uniqueness validation.
        if (s.name.isBlank()) { mutable.update { it.copy(problem = Problem.INVALID_INPUT) }; return }
        val id = s.editingId ?: EntityId(nextId())
        val tags = if (s.editingId == null) s.draft + PersonalTag(id, s.name, false)
            else s.draft.map { if (it.id == id) it.copy(name = s.name) else it }
        mutable.update { it.copy(draft = SnapshotList.copyOf(tags), name = "", editingId = null, problem = null) }
    }
    fun retire(id: EntityId, retired: Boolean) {
        val s = mutable.value
        if (s.busy || s.conflict || s.uncertain) return
        mutable.update { it.copy(draft = SnapshotList.copyOf(it.draft.map { tag -> if (tag.id == id) tag.copy(retired = retired) else tag })) }
    }
    fun move(id: EntityId, direction: Int) {
        val s = mutable.value
        if (s.busy || s.conflict || s.uncertain || direction !in listOf(-1, 1)) return
        val tags = s.draft.toMutableList(); val from = tags.indexOfFirst { it.id == id }; val to = from + direction
        if (from < 0 || to !in tags.indices) return
        tags.add(to, tags.removeAt(from)); mutable.update { it.copy(draft = SnapshotList.copyOf(tags)) }
    }
    fun entry(id: EntityId) {
        if (mutable.value.busy || mutable.value.deck?.entries?.none { it.id == id } != false) return
        mutable.update { it.copy(selectedEntries = if (id in it.selectedEntries) it.selectedEntries - id else it.selectedEntries + id) }
    }
    fun tag(id: EntityId) {
        if (mutable.value.busy) return
        mutable.update { it.copy(selectedTags = if (id in it.selectedTags) it.selectedTags - id else it.selectedTags + id) }
    }
    fun save() {
        val s = mutable.value; val pool = s.pool ?: return
        if (s.busy || s.conflict || s.uncertain || !s.dirty) return
        val call = ++ticket
        mutable.update { it.copy(busy = true, problem = null) }
        scope.launch {
            val result = callPort(dispatcher) { repository.replace(pool.revision, s.draft, it) }
            if (call != ticket) return@launch
            when (result) {
                is PortResult.Ok -> mutable.update { it.copy(pool = result.value, draft = result.value.tags, busy = false) }
                is PortResult.Failed -> mutable.update { it.copy(busy = false, problem = result.problem,
                    uncertain = result.problem in setOf(Problem.PERSISTENCE, Problem.INTERNAL, Problem.UNKNOWN_RESULT, Problem.UNEXPECTED),
                    conflict = result.problem == Problem.STALE_REVISION) }
            }
        }
    }
    fun reload() {
        val s = mutable.value
        if (s.deck == null || s.busy) return
        val call = ++ticket
        mutable.update { it.copy(busy = true, problem = null) }
        scope.launch {
            val result = callPort(dispatcher) { repository.load(it) }
            if (call != ticket) return@launch
            when (result) {
                is PortResult.Ok -> {
                    val loaded = result.value
                    if (s.pool == null || s.draft == loaded.tags) mutable.update {
                        it.copy(pool = loaded, draft = loaded.tags, busy = false, conflict = false, uncertain = false)
                    } else mutable.update { it.copy(pool = loaded, busy = false, conflict = true, uncertain = false) }
                }
                is PortResult.Failed -> mutable.update { it.copy(busy = false, problem = result.problem) }
            }
        }
    }
    /** Explicitly discard retained pool input after inspecting a stale/uncertain reload. */
    fun useSavedPool() {
        val s = mutable.value
        if (!s.busy && s.pool != null && s.conflict && !s.uncertain) mutable.update { it.copy(draft = s.pool.tags, conflict = false, uncertain = false, name = "", editingId = null, problem = null) }
    }
    fun assign(remove: Boolean) {
        val s = mutable.value; val deck = s.deck ?: return; val pool = s.pool ?: return
        if (s.busy || s.dirty || s.conflict || s.uncertain || s.name.isNotEmpty() || !current(deck)) {
            mutable.update { it.copy(problem = Problem.STALE_REVISION) }; return
        }
        val entries = SnapshotList.copyOf(deck.entries.filter { it.id in s.selectedEntries }.map { it.id })
        val tags = SnapshotList.copyOf(s.selectedTags)
        val result = if (remove) planning.remove(deck, entries, tags) else planning.assign(deck, pool, entries, tags)
        when (result) {
            is Outcome.Success -> if (apply(deck, result.value)) { ++ticket; mutable.value = ManualTagsState() }
                else mutable.update { it.copy(problem = Problem.STALE_REVISION) }
            else -> mutable.update { it.copy(problem = Problem.INVALID_INPUT) }
        }
    }
    fun close() {
        val s = mutable.value
        if (s.busy) return
        if (s.dirty || s.name.isNotEmpty() || s.uncertain) mutable.update { it.copy(closeRequested = true) }
        else discardAndClose()
    }
    fun keepEditing() { mutable.update { it.copy(closeRequested = false) } }
    fun discardAndClose() { if (!mutable.value.busy) { ++ticket; mutable.value = ManualTagsState() } }
}
