package com.architectpsg.commandlibrary.feature.shell.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.architectpsg.commandlibrary.core.model.CardRecord
import com.architectpsg.commandlibrary.core.model.CatalogLookup
import com.architectpsg.commandlibrary.core.model.GenerationId
import com.architectpsg.commandlibrary.core.model.OracleId
import com.architectpsg.commandlibrary.core.model.PageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class QueryProblem { EMPTY, TOO_LONG }

internal enum class SearchLoad { FIRST_PAGE, NEXT_PAGE }

/**
 * Results always come from one catalog snapshot ([generation]); pages are appended only while the
 * snapshot is unchanged. [stale] means the catalog changed since the results were read: they stay
 * visible for reference, more pages are refused, and the user can search again.
 */
internal data class SearchUiState(
    val query: String? = null,
    val queryProblem: QueryProblem? = null,
    val results: List<CardRecord> = emptyList(),
    val generation: GenerationId? = null,
    val nextCursor: String? = null,
    val loading: SearchLoad? = null,
    val problem: Problem? = null,
    val failedLoad: SearchLoad? = null,
    val cursorStale: Boolean = false,
    val activeGenerationKnown: Boolean = false,
    val activeGeneration: GenerationId? = null,
    val selected: CardRecord? = null,
) {
    val searched: Boolean get() = query != null

    val stale: Boolean
        get() = cursorStale ||
            (searched && loading != SearchLoad.FIRST_PAGE && failedLoad != SearchLoad.FIRST_PAGE &&
                activeGenerationKnown && activeGeneration != generation)

    val canLoadMore: Boolean get() = nextCursor != null && !stale && loading == null
}

internal class SearchController(
    private val scope: CoroutineScope,
    private val lookup: CatalogLookup,
    private val onStaleCursor: () -> Unit,
) {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    /** Text field contents; snapshot state so typing never waits for a flow emission. */
    var queryInput: String by mutableStateOf("")

    fun onQueryChange(text: String) {
        queryInput = text
        if (mutableState.value.queryProblem != null) mutableState.update { it.copy(queryProblem = null) }
    }

    fun submit() = search(queryInput)

    fun search(input: String) {
        val trimmed = input.trim()
        val problem = when {
            trimmed.isEmpty() -> QueryProblem.EMPTY
            trimmed.codePointCount(0, trimmed.length) > MAX_QUERY_CODE_POINTS -> QueryProblem.TOO_LONG
            else -> null
        }
        if (problem != null) {
            mutableState.update { it.copy(queryProblem = problem) }
            return
        }
        job?.cancel()
        mutableState.update {
            SearchUiState(
                query = trimmed,
                loading = SearchLoad.FIRST_PAGE,
                activeGenerationKnown = it.activeGenerationKnown,
                activeGeneration = it.activeGeneration,
            )
        }
        job = scope.launch { load(trimmed, cursor = null, kind = SearchLoad.FIRST_PAGE) }
    }

    fun loadMore() {
        val current = mutableState.value
        val query = current.query ?: return
        val cursor = current.nextCursor ?: return
        if (!current.canLoadMore) return
        mutableState.update { it.copy(loading = SearchLoad.NEXT_PAGE, problem = null, failedLoad = null) }
        job = scope.launch { load(query, cursor, SearchLoad.NEXT_PAGE) }
    }

    fun retry() {
        val current = mutableState.value
        when (current.failedLoad) {
            SearchLoad.FIRST_PAGE -> current.query?.let(::search)
            SearchLoad.NEXT_PAGE -> loadMore()
            null -> Unit
        }
    }

    /** Runs the last query again from its first page against the current catalog. */
    fun searchAgain() {
        mutableState.value.query?.let(::search)
    }

    fun select(card: CardRecord) = mutableState.update { it.copy(selected = card) }

    fun clearSelection() = mutableState.update { it.copy(selected = null) }

    /** Committed catalog status reports [generation] as active (null when nothing is installed). */
    fun onActiveGeneration(generation: GenerationId?) =
        mutableState.update { it.copy(activeGenerationKnown = true, activeGeneration = generation) }

    private suspend fun load(query: String, cursor: String?, kind: SearchLoad) {
        when (val result = callPort { lookup.lookupName(query, PageRequest(PAGE_SIZE, cursor), it) }) {
            is PortResult.Ok -> mutableState.update { current ->
                val page = result.value
                when {
                    current.query != query -> current
                    kind == SearchLoad.NEXT_PAGE && page.snapshot.generation != current.generation ->
                        current.copy(loading = null, cursorStale = true)
                    else -> current.copy(
                        results = if (kind == SearchLoad.FIRST_PAGE) distinct(page.items) else distinct(current.results + page.items),
                        generation = if (kind == SearchLoad.FIRST_PAGE) page.snapshot.generation else current.generation,
                        nextCursor = page.nextCursor,
                        loading = null,
                        problem = null,
                        failedLoad = null,
                    )
                }
            }
            is PortResult.Failed -> {
                val staleCursor = kind == SearchLoad.NEXT_PAGE && result.problem == Problem.STALE_GENERATION
                mutableState.update { current ->
                    when {
                        current.query != query -> current
                        staleCursor -> current.copy(loading = null, cursorStale = true)
                        else -> current.copy(loading = null, problem = result.problem, failedLoad = kind)
                    }
                }
                if (staleCursor) onStaleCursor()
            }
        }
    }

    /** One row per oracle identity; a repeated identity from an adapter never duplicates a row. */
    private fun distinct(cards: List<CardRecord>): List<CardRecord> {
        val seen = HashSet<OracleId>()
        return cards.filter { seen.add(it.oracleId) }
    }

    internal companion object {
        const val PAGE_SIZE: Int = 50
        const val MAX_QUERY_CODE_POINTS: Int = 128
    }
}
