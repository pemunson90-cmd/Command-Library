package com.architectpsg.commandlibrary.feature.shell.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.architectpsg.commandlibrary.core.model.CardRecord
import com.architectpsg.commandlibrary.core.model.CatalogLookup
import com.architectpsg.commandlibrary.core.model.CatalogTextLookup
import com.architectpsg.commandlibrary.core.model.CatalogTextField
import com.architectpsg.commandlibrary.core.model.GenerationId
import com.architectpsg.commandlibrary.core.model.OracleId
import com.architectpsg.commandlibrary.core.model.PageRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val queryField: CatalogTextField = CatalogTextField.NAME,
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
    val suggestions: List<CardRecord> = emptyList(),
    val suggestionGeneration: GenerationId? = null,
    val suggestionsLoading: Boolean = false,
    val suggestionProblem: Problem? = null,
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
    private val portDispatcher: CoroutineDispatcher,
    private val textLookup: CatalogTextLookup? = null,
    private val enableSuggestions: Boolean = false,
    private val onStaleCursor: () -> Unit,
) {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()
    private var job: Job? = null
    private var requestTicket = 0L

    /** Text field contents; snapshot state so typing never waits for a flow emission. */
    var queryInput: String by mutableStateOf("")
    var fieldInput: CatalogTextField by mutableStateOf(CatalogTextField.NAME)
        private set
    val supportsOracleText: Boolean get() = textLookup != null

    fun onFieldChange(field: CatalogTextField) {
        if (field == fieldInput || (field == CatalogTextField.ORACLE_TEXT && textLookup == null)) return
        fieldInput = field
        invalidateRequest()
        clearSearch()
        suggest(queryInput)
    }

    fun onQueryChange(text: String) {
        if (text == queryInput) return
        queryInput = text
        invalidateRequest()
        clearSearch()
        suggest(text)
    }

    fun submit() = search(queryInput)

    fun search(input: String) {
        val ticket = invalidateRequest()
        val field = fieldInput
        val trimmed = input.trim()
        val problem = when {
            trimmed.isEmpty() -> QueryProblem.EMPTY
            trimmed.codePointCount(0, trimmed.length) > MAX_QUERY_CODE_POINTS -> QueryProblem.TOO_LONG
            else -> null
        }
        if (problem != null) {
            clearSearch(problem)
            return
        }
        mutableState.update {
            SearchUiState(
                query = trimmed,
                queryField = field,
                loading = SearchLoad.FIRST_PAGE,
                activeGenerationKnown = it.activeGenerationKnown,
                activeGeneration = it.activeGeneration,
            )
        }
        job = scope.launch { load(trimmed, field, cursor = null, kind = SearchLoad.FIRST_PAGE, ticket = ticket) }
    }

    fun loadMore() {
        val current = mutableState.value
        val query = current.query ?: return
        val cursor = current.nextCursor ?: return
        if (!current.canLoadMore) return
        val ticket = invalidateRequest()
        mutableState.update { it.copy(loading = SearchLoad.NEXT_PAGE, problem = null, failedLoad = null) }
        job = scope.launch { load(query, current.queryField, cursor, SearchLoad.NEXT_PAGE, ticket) }
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

    fun selectSuggestion(card: CardRecord) {
        val current = mutableState.value
        if (card !in current.suggestions || (current.activeGenerationKnown && current.suggestionGeneration != current.activeGeneration)) return
        invalidateRequest()
        mutableState.update { it.copy(selected = card, suggestions = emptyList(), suggestionsLoading = false, suggestionProblem = null) }
    }

    fun clearSelection() = mutableState.update { it.copy(selected = null) }

    /** End an embedded picker without carrying an earlier editor's query or selection forward. */
    fun reset() {
        invalidateRequest()
        queryInput = ""
        fieldInput = CatalogTextField.NAME
        clearSearch()
    }

    private fun invalidateRequest(): Long {
        requestTicket++
        job?.cancel()
        job = null
        return requestTicket
    }

    private fun clearSearch(problem: QueryProblem? = null) = mutableState.update {
        SearchUiState(queryProblem = problem, activeGenerationKnown = it.activeGenerationKnown, activeGeneration = it.activeGeneration)
    }

    /** Committed catalog status reports [generation] as active (null when nothing is installed). */
    fun onActiveGeneration(generation: GenerationId?) {
        val current = mutableState.value
        val discard = (current.suggestions.isNotEmpty() && current.suggestionGeneration != generation) ||
            (current.suggestionsLoading && current.activeGenerationKnown && current.activeGeneration != generation)
        if (discard) invalidateRequest()
        mutableState.update { it.copy(activeGenerationKnown = true, activeGeneration = generation,
            suggestions = if (discard) emptyList() else it.suggestions,
            suggestionGeneration = if (discard) null else it.suggestionGeneration,
            suggestionsLoading = if (discard) false else it.suggestionsLoading,
            suggestionProblem = if (discard) null else it.suggestionProblem) }
    }

    private fun suggest(input: String) {
        val query = input.trim()
        if (!enableSuggestions || fieldInput != CatalogTextField.NAME || query.isEmpty() ||
            query.codePointCount(0, query.length) > MAX_QUERY_CODE_POINTS) return
        val ticket = requestTicket
        job = scope.launch {
            delay(SUGGESTION_DELAY_MS)
            if (ticket != requestTicket) return@launch
            mutableState.update { it.copy(suggestionsLoading = true) }
            val result = callPort(portDispatcher) { lookup.lookupName(query, PageRequest(SUGGESTION_LIMIT), it) }
            if (ticket != requestTicket) return@launch
            when (result) {
                is PortResult.Ok -> {
                    val page = result.value
                    val current = mutableState.value
                    val problem = when {
                        page.items.isNotEmpty() && page.snapshot.generation == null -> Problem.CORRUPT_DATA
                        current.activeGenerationKnown && page.snapshot.generation != current.activeGeneration -> Problem.STALE_GENERATION
                        else -> null
                    }
                    mutableState.update { it.copy(suggestionsLoading = false, suggestionProblem = problem,
                        suggestions = if (problem == null) distinct(page.items).take(SUGGESTION_LIMIT) else emptyList(),
                        suggestionGeneration = if (problem == null) page.snapshot.generation else null) }
                    if (problem == Problem.STALE_GENERATION) onStaleCursor()
                }
                is PortResult.Failed -> mutableState.update { it.copy(suggestionsLoading = false, suggestionProblem = result.problem) }
            }
        }
    }

    private suspend fun load(query: String, field: CatalogTextField, cursor: String?, kind: SearchLoad, ticket: Long) {
        val result = callPort(portDispatcher) {
            if (field == CatalogTextField.NAME) lookup.lookupName(query, PageRequest(PAGE_SIZE, cursor), it)
            else requireNotNull(textLookup).lookupText(field, query, PageRequest(PAGE_SIZE, cursor), it)
        }
        if (ticket != requestTicket) return
        when (result) {
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
        const val SUGGESTION_LIMIT: Int = 6
        const val SUGGESTION_DELAY_MS: Long = 250
    }
}
