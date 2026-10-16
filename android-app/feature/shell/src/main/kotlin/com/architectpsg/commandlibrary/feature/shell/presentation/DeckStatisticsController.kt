package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class DeckStatisticsState(
    val deck: DeckSnapshot? = null,
    val snapshot: SnapshotReference? = null,
    val boards: Set<BoardRole> = setOf(BoardRole.MAIN),
    val tags: Set<EntityId> = emptySet(),
    val allTags: Boolean = true,
    val restrictEntries: Boolean = false,
    val entries: Set<EntityId> = emptySet(),
    val busy: Boolean = false,
    val problem: Problem? = null,
    val result: DeckStatisticsResult? = null,
)

/** Read-only captured statistics; filter edits and stale contexts invalidate pending results. */
internal class DeckStatisticsController(
    private val scope: CoroutineScope,
    private val statistics: LocalDeckStatistics,
    private val dispatcher: CoroutineDispatcher,
    private val current: (DeckSnapshot, SnapshotReference) -> Boolean,
) {
    private val mutable = MutableStateFlow(DeckStatisticsState())
    val state: StateFlow<DeckStatisticsState> = mutable.asStateFlow()
    private var ticket = 0L
    private var job: Job? = null
    fun rendered(frame: DeckStatisticsState, action: () -> Unit) { if (mutable.value === frame) action() }
    fun open(deck: DeckSnapshot, snapshot: SnapshotReference) {
        if (mutable.value.deck != null || !current(deck, snapshot)) return
        mutable.value = DeckStatisticsState(deck = deck, snapshot = snapshot)
        compute()
    }
    private fun edit(change: (DeckStatisticsState) -> DeckStatisticsState) {
        if (mutable.value.deck == null) return
        ++ticket; job?.cancel(); job = null
        mutable.value = change(mutable.value).copy(result = null, busy = false, problem = null)
    }
    fun board(value: BoardRole) = edit { it.copy(boards = if (value in it.boards) it.boards - value else it.boards + value) }
    fun tag(value: EntityId) = edit { it.copy(tags = if (value in it.tags) it.tags - value else it.tags + value) }
    fun allTags(value: Boolean) = edit { it.copy(allTags = value) }
    fun restrictEntries(value: Boolean) = edit { it.copy(restrictEntries = value) }
    fun entry(value: EntityId) = edit { it.copy(entries = if (value in it.entries) it.entries - value else it.entries + value) }
    fun compute() {
        val s = mutable.value; val deck = s.deck ?: return; val snapshot = s.snapshot ?: return
        if (s.busy) return
        if (!current(deck, snapshot)) { mutable.update { it.copy(problem = Problem.STALE_GENERATION, result = null) }; return }
        val request = DeckStatisticsRequest(deck, snapshot, DeckStatisticsFilter(
            SnapshotList.copyOf(BoardRole.entries.filter { it in s.boards }),
            if (s.restrictEntries) SnapshotList.copyOf(deck.entries.filter { it.id in s.entries }.map { it.id }) else null,
            SnapshotList.copyOf(s.tags), s.allTags))
        val call = ++ticket
        mutable.update { it.copy(busy = true, result = null, problem = null) }
        job = scope.launch {
            val result = callPort(dispatcher) { statistics.analyze(request, it) }
            if (call != ticket) return@launch
            if (!current(deck, snapshot)) { mutable.update { it.copy(busy = false, result = null, problem = Problem.STALE_GENERATION) }; return@launch }
            when (result) {
                is PortResult.Ok -> {
                    val r = result.value
                    if (r.deckId != deck.id || r.deckRevision != deck.revision || r.snapshot != snapshot || r.filter != request.filter)
                        mutable.update { it.copy(busy = false, result = null, problem = Problem.CORRUPT_DATA) }
                    else mutable.update { it.copy(busy = false, result = r) }
                }
                is PortResult.Failed -> mutable.update { it.copy(busy = false, result = null, problem = result.problem) }
            }
        }
    }
    fun close() { ++ticket; job?.cancel(); job = null; mutable.value = DeckStatisticsState() }
}
