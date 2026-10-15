package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class DeckLibraryForm(val source: DeckSnapshot?, val name: String = "")
internal enum class DeckEditorMode { DETAILS, ENTRY, ADD, REMOVE }
internal data class DeckEditorForm(
    val base: DeckSnapshot, val mode: DeckEditorMode, val entry: DeckEntry? = null,
    val card: CardRecord? = null, val name: String = base.name, val description: String = base.description,
    val primer: String = base.primer, val quantity: String = entry?.quantity?.toString() ?: "1",
    val board: BoardRole = entry?.board ?: BoardRole.MAIN,
    val finish: Knowledge<Finish> = entry?.finish ?: Knowledge.Absent("No finish restriction"),
    val notes: String = entry?.notes ?: "", val labels: SnapshotList<EntryLabel> = entry?.labels ?: SnapshotList.empty(),
)
internal data class DeckLibraryUiState(
    val items: SnapshotList<DeckSummary> = SnapshotList.empty(),
    val nextCursor: String? = null, val epoch: Revision? = null,
    val loading: Boolean = false, val listProblem: Problem? = null,
    val selected: DeckSnapshot? = null, val selectedId: DeckId? = null,
    val loadingDetail: Boolean = false, val detailProblem: Problem? = null,
    val form: DeckLibraryForm? = null, val invalidName: Boolean = false,
    val editor: DeckEditorForm? = null, val invalidEdit: Boolean = false,
    val mutationBusy: Boolean = false, val pending: Boolean = false,
    val mutationProblem: Problem? = null, val savedId: DeckId? = null,
)
private sealed interface DeckLibraryRequest {
    val operation: EntityId
    data class Create(val snapshot: DeckSnapshot, override val operation: EntityId) : DeckLibraryRequest {
        val name get() = snapshot.name
    }
    data class Duplicate(val source: DeckId, val revision: Revision, val name: String,
        override val operation: EntityId) : DeckLibraryRequest
    data class Patch(val base: DeckSnapshot, val patch: DeckPatch, override val operation: EntityId) : DeckLibraryRequest
    data class Undo(val base: DeckSnapshot, override val operation: EntityId) : DeckLibraryRequest
}

/** ViewModel-owned state. Layout/navigation never create requests or cancel a mutation. */
internal class DeckLibraryController(
    private val scope: CoroutineScope,
    private val repository: LocalDeckLibrary,
    private val dispatcher: CoroutineDispatcher,
    private val nextId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> UtcTimestamp,
    private val edits: LocalDeckEdits? = null,
    private val cardCurrent: (CardRecord) -> Boolean = { false },
) {
    private val mutable = MutableStateFlow(DeckLibraryUiState())
    val state: StateFlow<DeckLibraryUiState> = mutable.asStateFlow()
    private var listTicket = 0L
    private var detailTicket = 0L
    private var listJob: Job? = null
    private var detailJob: Job? = null
    private var request: DeckLibraryRequest? = null
    private var uncertainCommit = false
    private var userSelectionGeneration = 0L
    private var requestSelectionGeneration = 0L
    private val seenCursors = mutableSetOf<String>()

    val supportsEditing: Boolean get() = edits != null
    private fun editable(): DeckSnapshot? = mutable.value.selected?.takeIf {
        edits != null && request == null && !mutable.value.mutationBusy && it.packageInstances.isEmpty()
    }
    fun editDetails() { editable()?.let { base -> mutable.update { it.copy(editor = DeckEditorForm(base, DeckEditorMode.DETAILS), invalidEdit = false, mutationProblem = null) } } }
    fun editEntry(entryId: EntityId, remove: Boolean = false) { editable()?.let { base ->
        base.entries.firstOrNull { it.id == entryId }?.let { entry -> mutable.update { it.copy(
            editor = DeckEditorForm(base, if (remove) DeckEditorMode.REMOVE else DeckEditorMode.ENTRY, entry), invalidEdit = false, mutationProblem = null) } }
    } }
    fun addEntry() { editable()?.let { base -> mutable.update { it.copy(editor = DeckEditorForm(base, DeckEditorMode.ADD), invalidEdit = false, mutationProblem = null) } } }
    fun editorChange(change: (DeckEditorForm) -> DeckEditorForm) {
        if (request == null && !mutable.value.mutationBusy) mutable.update { state -> state.copy(editor = state.editor?.let(change), invalidEdit = false) }
    }
    fun closePendingEditor() { if (mutable.value.pending && !mutable.value.mutationBusy) mutable.update { it.copy(editor = null) } }
    fun discardEditor() { if (request == null && !mutable.value.mutationBusy) mutable.update { it.copy(editor = null, invalidEdit = false) } }
    fun moveEntry(entryId: EntityId, direction: Int) {
        if (direction != -1 && direction != 1) return
        val base = editable() ?: return
        val ids = base.entries.map { it.id }.toMutableList(); val index = ids.indexOf(entryId); val target = index + direction
        if (index < 0 || target !in ids.indices) return
        val moved = ids.removeAt(index); ids.add(target, moved)
        freezeEdit { DeckLibraryRequest.Patch(base, DeckPatch(SnapshotList.of(DeckEdit.ReorderEntries(SnapshotList.copyOf(ids))), "Reorder entries"), EntityId(nextId())) }
    }
    fun undo() {
        val base = editable() ?: return
        if (base.revision.value == Long.MAX_VALUE) { mutable.update { it.copy(mutationProblem = Problem.INVALID_INPUT) }; return }
        freezeEdit { DeckLibraryRequest.Undo(base, EntityId(nextId())) }
    }
    fun confirmEditor() {
        if (request != null || mutable.value.mutationBusy || edits == null) return
        val form = mutable.value.editor ?: return
        val selected = mutable.value.selected
        if (selected?.id != form.base.id || selected.revision != form.base.revision) {
            mutable.update { it.copy(mutationProblem = Problem.STALE_REVISION) }; return
        }
        val quantity = form.quantity.toIntOrNull()
        fun bounded(text: String, maximum: Int) = text.length <= maximum &&
            !text.indices.any { i -> (text[i].isHighSurrogate() && (i + 1 == text.length || !text[i + 1].isLowSurrogate())) ||
                (text[i].isLowSurrogate() && (i == 0 || !text[i - 1].isHighSurrogate())) } && text.toByteArray(Charsets.UTF_8).size <= maximum
        if (form.base.revision.value == Long.MAX_VALUE || form.name.isBlank() || !bounded(form.name, LocalDeckLibraryLimits.MAX_NAME_BYTES) ||
            !bounded(form.description, DeckPayloadLimits.MAX_TEXT_BYTES) || !bounded(form.primer, DeckPayloadLimits.MAX_TEXT_BYTES) ||
            !bounded(form.notes, DeckPayloadLimits.MAX_TEXT_BYTES) || (form.mode in setOf(DeckEditorMode.ENTRY, DeckEditorMode.ADD) && (quantity == null || quantity <= 0)) ||
            (form.mode == DeckEditorMode.ADD && (form.card == null || !cardCurrent(form.card) || form.base.entries.size >= DeckPayloadLimits.MAX_ENTRIES))) {
            mutable.update { it.copy(invalidEdit = true) }; return
        }
        freezeEdit {
            val changes: SnapshotList<DeckEdit> = when (form.mode) {
                DeckEditorMode.DETAILS -> SnapshotList.of(DeckEdit.Rename(form.name), DeckEdit.Description(form.description), DeckEdit.Primer(form.primer))
                DeckEditorMode.REMOVE -> SnapshotList.of(DeckEdit.Remove(checkNotNull(form.entry).id))
                DeckEditorMode.ENTRY -> SnapshotList.of(DeckEdit.Replace(checkNotNull(form.entry).copy(quantity = checkNotNull(quantity), board = form.board,
                    finish = form.finish, notes = form.notes, labels = form.labels)))
                DeckEditorMode.ADD -> {
                    val card = checkNotNull(form.card)
                    val entryId = EntityId(nextId())
                    require(form.base.entries.none { it.id == entryId })
                    val identity = CardIdentity(Knowledge.Known(card.oracleId), Knowledge.Absent("No printing selected"), null,
                        Knowledge.Known(card.name), card.language)
                    SnapshotList.of(DeckEdit.Add(DeckEntry(entryId, identity, checkNotNull(quantity), form.board, form.finish,
                        form.notes, form.labels, SnapshotList.empty())))
                }
            }
            DeckLibraryRequest.Patch(form.base, DeckPatch(changes, "Edit deck"), EntityId(nextId()))
        }
    }
    private fun freezeEdit(make: () -> DeckLibraryRequest) {
        if (request != null || mutable.value.mutationBusy) return
        try { request = make() } catch (_: Exception) { mutable.update { it.copy(mutationProblem = Problem.INTERNAL) }; return }
        requestSelectionGeneration = userSelectionGeneration
        attempt()
    }

    fun refresh() = page(null)
    fun nextPage() {
        val s = mutable.value
        if (!s.loading) s.nextCursor?.let(::page)
    }
    private fun page(cursor: String?) {
        listJob?.cancel()
        val ticket = ++listTicket
        val epoch = mutable.value.epoch
        if (cursor == null) seenCursors.clear()
        mutable.update { it.copy(loading = true, listProblem = null) }
        listJob = scope.launch {
            try {
                val result = callPort(dispatcher) { repository.list(PageRequest(20, cursor), it) }
                currentCoroutineContext().ensureActive()
                if (ticket != listTicket) return@launch
                when (result) {
                    is PortResult.Failed -> mutable.update { it.copy(listProblem = result.problem,
                        nextCursor = if (result.problem == Problem.STALE_REVISION) null else it.nextCursor) }
                    is PortResult.Ok -> {
                        val p = result.value
                        val valid = p.snapshot.source == LocalDeckLibraryLimits.SOURCE && p.snapshot.revision != null &&
                            p.snapshot.generation == null && p.snapshot.language == null && p.items.size <= 20 &&
                            (cursor == null || p.snapshot.revision == epoch) &&
                            p.items.map { it.id }.toSet().size == p.items.size && p.items.all {
                                it.id.source == LocalDeckLibraryLimits.SOURCE && it.name.isNotBlank() &&
                                    it.name.toByteArray(Charsets.UTF_8).size <= LocalDeckLibraryLimits.MAX_NAME_BYTES &&
                                    it.id.raw.toByteArray(Charsets.UTF_8).size <= LocalDeckLibraryLimits.MAX_ID_BYTES &&
                                    it.entryCount in 0..DeckPayloadLimits.MAX_ENTRIES && it.totalQuantity >= it.entryCount.toLong() &&
                                    it.totalQuantity <= it.entryCount.toLong() * Int.MAX_VALUE
                            } && (p.nextCursor == null || (p.items.isNotEmpty() && p.nextCursor != cursor && p.nextCursor !in seenCursors))
                        if (!valid) mutable.update { it.copy(listProblem = Problem.CORRUPT_DATA, nextCursor = null) }
                        else {
                            if (cursor != null) seenCursors += cursor
                            mutable.update { it.copy(items = p.items, epoch = p.snapshot.revision,
                                nextCursor = p.nextCursor, listProblem = null) }
                        }
                    }
                }
            } finally { if (ticket == listTicket) mutable.update { it.copy(loading = false) } }
        }
    }

    fun select(id: DeckId) {
        ++userSelectionGeneration
        loadSelection(id)
    }
    private fun loadSelection(id: DeckId) {
        detailJob?.cancel()
        val ticket = ++detailTicket
        mutable.update { it.copy(selectedId = id, selected = null, loadingDetail = true, detailProblem = null) }
        detailJob = scope.launch {
            try {
                val result = callPort(dispatcher) { repository.load(id, it) }
                currentCoroutineContext().ensureActive()
                if (ticket != detailTicket) return@launch
                when (result) {
                    is PortResult.Failed -> mutable.update { it.copy(detailProblem = result.problem) }
                    is PortResult.Ok -> if (result.value.id == id && id.source == LocalDeckLibraryLimits.SOURCE &&
                        result.value.entries.size <= DeckPayloadLimits.MAX_ENTRIES) {
                        mutable.update { it.copy(selected = result.value) }
                    } else mutable.update { it.copy(detailProblem = Problem.CORRUPT_DATA) }
                }
            } finally { if (ticket == detailTicket) mutable.update { it.copy(loadingDetail = false) } }
        }
    }
    fun clearSelection() {
        ++userSelectionGeneration
        ++detailTicket; detailJob?.cancel()
        mutable.update { it.copy(selectedId = null, selected = null, loadingDetail = false, detailProblem = null) }
    }
    fun create() {
        if (request == null && !mutable.value.mutationBusy)
            mutable.update { it.copy(form = DeckLibraryForm(null), editor = null, invalidName = false, mutationProblem = null, savedId = null) }
    }
    fun duplicate() {
        if (request == null && !mutable.value.mutationBusy) mutable.value.selected?.let { source ->
            mutable.update { it.copy(form = DeckLibraryForm(source, source.name), editor = null, invalidName = false,
                mutationProblem = null, savedId = null) }
        }
    }
    fun name(value: String) {
        if (request == null && !mutable.value.mutationBusy) mutable.update { it.copy(form = it.form?.copy(name = value), invalidName = false) }
    }
    fun dismissForm() {
        if (!mutable.value.mutationBusy) mutable.update { it.copy(form = null, invalidName = false) }
    }
    fun confirm() {
        if (request != null || mutable.value.mutationBusy) return
        val form = mutable.value.form ?: return
        if (form.name.isBlank() || form.name.toByteArray(Charsets.UTF_8).size > LocalDeckLibraryLimits.MAX_NAME_BYTES) {
            mutable.update { it.copy(invalidName = true) }; return
        }
        try {
            val op = EntityId(nextId())
            request = if (form.source != null) DeckLibraryRequest.Duplicate(form.source.id, form.source.revision, form.name, op)
            else {
                val time = now()
                DeckLibraryRequest.Create(DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE, nextId()), form.name,
                    Knowledge.Absent("No format selected"), "", Knowledge.Absent("No folder selected"), SnapshotList.empty(),
                    time, time, Revision(0), SnapshotList.empty(), Knowledge.Absent("No budget set"), null,
                    SnapshotList.empty(), SnapshotList.empty(), categoryOrder = SnapshotList.empty()), op)
            }
        } catch (_: Exception) { mutable.update { it.copy(mutationProblem = Problem.INTERNAL) }; return }
        requestSelectionGeneration = userSelectionGeneration
        attempt()
    }
    fun retry() { if (request != null && !mutable.value.mutationBusy) attempt() }
    /** UI must explicitly warn that a previous attempt may already have committed. */
    fun abandonConfirmed() {
        if (mutable.value.mutationBusy || request == null) return
        request = null; uncertainCommit = false
        mutable.update { it.copy(pending = false, form = null, editor = null, mutationProblem = null) }
        val selection = mutable.value.selectedId
        invalidateReads(); selection?.let(::loadSelection); refresh()
    }
    private fun invalidateReads() {
        ++listTicket; ++detailTicket
        listJob?.cancel(); detailJob?.cancel()
        mutable.update { it.copy(loading = false, loadingDetail = false) }
    }
    private fun attempt() {
        val frozen = request ?: return
        if (mutable.value.mutationBusy) return
        mutable.update { it.copy(mutationBusy = true, pending = true, mutationProblem = null, savedId = null) }
        scope.launch {
            try {
                val result = callPort(dispatcher) { cancel -> when (frozen) {
                    is DeckLibraryRequest.Create -> repository.create(frozen.snapshot, frozen.operation, cancel)
                    is DeckLibraryRequest.Duplicate -> repository.duplicate(frozen.source, frozen.revision, frozen.name, frozen.operation, cancel)
                    is DeckLibraryRequest.Patch -> checkNotNull(edits).apply(frozen.base.id, frozen.patch, frozen.base.revision, frozen.operation, cancel)
                    is DeckLibraryRequest.Undo -> checkNotNull(edits).undo(frozen.base.id, frozen.base.revision, frozen.operation, cancel)
                } }
                currentCoroutineContext().ensureActive()
                val keepSelection = userSelectionGeneration == requestSelectionGeneration
                var readAfter = mutable.value.selectedId
                invalidateReads()
                when (result) {
                    is PortResult.Failed -> {
                        uncertainCommit = uncertainCommit || result.problem in setOf(Problem.PERSISTENCE, Problem.INTERNAL, Problem.UNKNOWN_RESULT, Problem.UNEXPECTED)
                        if (!uncertainCommit) request = null
                        mutable.update { it.copy(pending = uncertainCommit, mutationProblem = result.problem) }
                    }
                    is PortResult.Ok -> {
                        val saved = result.value
                        val valid = saved.id.source == LocalDeckLibraryLimits.SOURCE && saved.name.isNotBlank() &&
                            saved.entries.size <= DeckPayloadLimits.MAX_ENTRIES && when (frozen) {
                                is DeckLibraryRequest.Create -> saved == frozen.snapshot
                                is DeckLibraryRequest.Duplicate -> saved.id != frozen.source && saved.revision == Revision(0) && saved.name == frozen.name
                                is DeckLibraryRequest.Patch -> saved.id == frozen.base.id && saved.createdAt == frozen.base.createdAt &&
                                    saved.revision.value == frozen.base.revision.value + 1 && saved.packageInstances.isEmpty()
                                is DeckLibraryRequest.Undo -> saved.id == frozen.base.id && saved.createdAt == frozen.base.createdAt &&
                                    saved.revision.value == frozen.base.revision.value + 1 && saved.packageInstances.isEmpty()
                            }
                        if (!valid) {
                            uncertainCommit = true
                            mutable.update { it.copy(mutationProblem = Problem.CORRUPT_DATA, pending = true) }
                        }
                        else {
                            request = null; uncertainCommit = false
                            mutable.update { it.copy(pending = false, form = null, editor = null, mutationProblem = null, savedId = saved.id) }
                            // Reconciliation may return an old immutable result; read the actual current head.
                            if (keepSelection) readAfter = saved.id
                        }
                    }
                }
                readAfter?.let(::loadSelection)
                refresh()
            } finally { mutable.update { it.copy(mutationBusy = false) } }
        }
    }
}
