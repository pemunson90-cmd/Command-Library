package com.architectpsg.commandlibrary.feature.shell.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.document.DocumentChunkStream
import com.architectpsg.commandlibrary.feature.shell.document.DocumentDescription
import com.architectpsg.commandlibrary.feature.shell.document.DocumentRef
import com.architectpsg.commandlibrary.feature.shell.document.DocumentSource
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class RulesImportPhase { IDLE, RUNNING, SUCCEEDED, FAILED, CANCELLED }

internal data class RulesUiState(
    val status: LocalRulesStatus? = null,
    val statusLoading: Boolean = false,
    val statusProblem: Problem? = null,
    val importPhase: RulesImportPhase = RulesImportPhase.IDLE,
    val fileName: String? = null,
    val pass: Int = 0,
    val bytesRead: Long = 0,
    val progress: JobProgress? = null,
    val importProblem: Problem? = null,
    val importTooLarge: Boolean = false,
    val query: String? = null,
    val results: List<RuleEntry> = emptyList(),
    val generation: GenerationId? = null,
    val nextCursor: String? = null,
    val pagesLoaded: Int = 0,
    val searchLoading: Boolean = false,
    val searchProblem: Problem? = null,
    val selected: RuleEntry? = null,
    val detailLoading: Boolean = false,
    val detailProblem: Problem? = null,
) {
    val active: LocalRulesCorpus? get() = (status?.active as? Knowledge.Known)?.value
    val canLoadMore: Boolean get() = nextCursor != null && !searchLoading && results.size < RulesController.MAX_RESULTS && pagesLoaded < RulesController.MAX_PAGES
}

/** Local-only rules presentation. Every read/import runs on IO; entries and links pin the page corpus. */
internal class RulesController(
    private val scope: CoroutineScope,
    private val importer: LocalRulesImport,
    private val lookup: LocalRulesLookup,
    private val documents: DocumentSource,
    private val io: CoroutineDispatcher,
) {
    private val mutable = MutableStateFlow(RulesUiState())
    val state: StateFlow<RulesUiState> = mutable.asStateFlow()
    var queryInput: String by mutableStateOf("")
    private var importJob: Job? = null
    private var statusJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var searchEpoch = 0L

    fun refresh() {
        statusJob?.cancel()
        mutable.update { it.copy(statusLoading = true, statusProblem = null) }
        statusJob = scope.launch {
            when (val result = callPort(io) { lookup.status(it) }) {
                is PortResult.Ok -> {
                    val active = (result.value.active as? Knowledge.Known)?.value?.id
                    if (mutable.value.generation != null && result.value.active !is Knowledge.Unknown &&
                        mutable.value.generation != active) clearSearch(Problem.STALE_GENERATION)
                    mutable.update { it.copy(status = result.value, statusLoading = false, statusProblem = null) }
                }
                is PortResult.Failed -> mutable.update { it.copy(statusLoading = false, statusProblem = result.problem) }
            }
        }
    }

    fun start(ref: DocumentRef): Boolean {
        if (importJob?.isActive == true) return false
        mutable.update { it.copy(importPhase = RulesImportPhase.RUNNING, fileName = null, pass = 0,
            bytesRead = 0, progress = null, importProblem = null, importTooLarge = false) }
        val launched = scope.launch {
            try {
                runImport(ref)
            } catch (cancelled: CancellationException) {
                mutable.update { it.copy(importPhase = RulesImportPhase.CANCELLED) }
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(importPhase = RulesImportPhase.FAILED, importProblem = Problem.UNEXPECTED) }
            }
        }
        launched.invokeOnCompletion { refresh() }
        importJob = launched
        return true
    }

    fun cancelImport() { importJob?.cancel() }

    private suspend fun runImport(ref: DocumentRef) {
        val description = try {
            withContext(io) { documents.describe(ref) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DocumentDescription(null, null)
        }
        val name = ImportController.boundedDisplayName(description.displayName?.takeIf { it.isNotBlank() }
            ?: ImportController.fallbackName(ref))
        mutable.update { it.copy(fileName = name) }
        if ((description.reportedBytes ?: 0) > MAX_BYTES) {
            mutable.update { it.copy(importPhase = RulesImportPhase.FAILED, importTooLarge = true) }
            return
        }
        val unknown = Knowledge.Unknown("Not supplied by the selected local file", SnapshotList.empty())
        val source = try {
            LocalRulesImportSource(ResourceUri(ref.uri), name, ProviderId("local-rules"), LanguageTag.ENGLISH,
                unknown, unknown, unknown, unknown, unknown, Knowledge.Absent("No expected checksum was supplied"),
                Knowledge.Absent("Provider-reported size is not a verified expected size"))
        } catch (_: IllegalArgumentException) {
            mutable.update { it.copy(importPhase = RulesImportPhase.FAILED, importProblem = Problem.INVALID_INPUT) }
            return
        }
        val streamProblem = AtomicReference<Problem?>(null)
        val tooLarge = AtomicReference(false)
        // Opening occurs inside the cold collection on IO, with no cancellable dispatcher hop
        // between open and use. Every invocation AND every collection owns a fresh closeable input.
        val reopen = {
            flow {
                try {
                    documents.open(ref).let { input ->
                        DocumentChunkStream(input, MAX_BYTES, io) { count -> mutable.update { it.copy(bytesRead = count) } }.use { stream ->
                            mutable.update { it.copy(pass = it.pass + 1, bytesRead = 0) }
                            try { emitAll(stream.chunks) } finally {
                                if (stream.failure == DocumentChunkStream.Failure.TOO_LARGE) tooLarge.set(true)
                                if (stream.failure == DocumentChunkStream.Failure.READ_ERROR) streamProblem.set(Problem.SOURCE_UNAVAILABLE)
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (streamProblem.get() == null) streamProblem.set(if (error is SecurityException) Problem.PERMISSION else Problem.SOURCE_UNAVAILABLE)
                    throw error
                }
            }.flowOn(io)
        }
        when (val result = callPort(io) { cancel -> importer.import(source, reopen,
            { progress -> mutable.update { it.copy(progress = progress) } }, cancel) }) {
            is PortResult.Ok -> {
                if (mutable.value.generation != null && mutable.value.generation != result.value.corpus.id) {
                    clearSearch(Problem.STALE_GENERATION)
                }
                mutable.update { it.copy(importPhase = RulesImportPhase.SUCCEEDED) }
            }
            is PortResult.Failed -> mutable.update { it.copy(importPhase = RulesImportPhase.FAILED,
                importProblem = streamProblem.get() ?: result.problem, importTooLarge = tooLarge.get()) }
        }
    }

    /** A card lookup is a fresh local search, never a guessed entry or an import. */
    fun lookupKeyword(keyword: String) {
        clearSearch(null)
        queryInput = keyword
        submit()
    }

    fun submit() {
        val query = queryInput.trim()
        if (query.isEmpty() || query.codePointCount(0, query.length) > MAX_QUERY_CODE_POINTS) {
            mutable.update { it.copy(searchProblem = Problem.INVALID_INPUT) }
            return
        }
        clearSearch(null)
        mutable.update { it.copy(query = query, searchLoading = true) }
        val epoch = searchEpoch
        searchJob = scope.launch { load(query, null, null, epoch) }
    }

    fun loadMore() {
        val current = mutable.value
        if (!current.canLoadMore) return
        val query = current.query ?: return
        val corpus = current.generation ?: return
        mutable.update { it.copy(searchLoading = true, searchProblem = null) }
        val epoch = searchEpoch
        searchJob = scope.launch { load(query, current.nextCursor, corpus, epoch) }
    }

    private suspend fun load(query: String, cursor: String?, corpus: GenerationId?, epoch: Long) {
        val limit = minOf(PAGE_SIZE, MAX_RESULTS - mutable.value.results.size)
        when (val result = callPort(io) { lookup.search(RulesQuery(query, corpus, SnapshotList.empty(), PageRequest(limit, cursor)), it) }) {
            is PortResult.Ok -> {
                if (epoch != searchEpoch) return
                val page = result.value
                val generation = page.snapshot.generation
                if (generation == null || (corpus != null && corpus != generation) || page.items.any { it.corpusId != generation }) {
                    clearSearch(Problem.STALE_GENERATION)
                    return
                }
                mutable.update { it.copy(results = (it.results + page.items).distinctBy { entry -> entry.id }.take(MAX_RESULTS),
                    generation = generation, nextCursor = page.nextCursor, pagesLoaded = it.pagesLoaded + 1, searchLoading = false, searchProblem = null) }
            }
            is PortResult.Failed -> {
                if (epoch != searchEpoch) return
                if (result.problem == Problem.STALE_GENERATION) {
                    clearSearch(result.problem)
                    refresh()
                } else mutable.update { it.copy(searchLoading = false, searchProblem = result.problem) }
            }
        }
    }

    fun select(entry: RuleEntry) {
        if (entry.corpusId != mutable.value.generation) return
        readEntry(entry.id, entry.corpusId)
    }

    fun follow(reference: RuleCrossReference) {
        val selected = mutable.value.selected ?: return
        if (reference !in selected.references) return
        val target = (reference.resolved as? Knowledge.Known)?.value ?: return
        readEntry(target, selected.corpusId)
    }

    fun clearSelection() {
        detailJob?.cancel()
        mutable.update { it.copy(selected = null, detailLoading = false, detailProblem = null) }
    }

    private fun readEntry(id: RuleId, corpus: GenerationId) {
        detailJob?.cancel()
        mutable.update { it.copy(detailLoading = true, detailProblem = null) }
        val epoch = searchEpoch
        detailJob = scope.launch {
            when (val result = callPort(io) { lookup.entry(id, corpus, it) }) {
                is PortResult.Ok -> if (epoch == searchEpoch) {
                    if (result.value.corpusId != corpus || result.value.id != id) clearSearch(Problem.STALE_GENERATION)
                    else mutable.update { it.copy(selected = result.value, detailLoading = false) }
                }
                is PortResult.Failed -> if (epoch == searchEpoch) {
                    if (result.problem == Problem.STALE_GENERATION) { clearSearch(result.problem); refresh() }
                    else mutable.update { it.copy(selected = null, detailLoading = false, detailProblem = result.problem) }
                }
            }
        }
    }

    private fun clearSearch(problem: Problem?) {
        searchEpoch++
        searchJob?.cancel()
        detailJob?.cancel()
        mutable.update { it.copy(results = emptyList(), generation = null, nextCursor = null, selected = null,
            query = null, pagesLoaded = 0, searchLoading = false, detailLoading = false, searchProblem = problem, detailProblem = null) }
    }

    internal companion object {
        const val MAX_BYTES: Long = 8L * 1024 * 1024
        const val PAGE_SIZE: Int = 50
        const val MAX_RESULTS: Int = 200
        const val MAX_PAGES: Int = 4
        const val MAX_QUERY_CODE_POINTS: Int = 128
    }
}
