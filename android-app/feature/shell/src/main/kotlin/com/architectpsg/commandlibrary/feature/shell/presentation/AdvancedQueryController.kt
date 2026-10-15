package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class AdvancedQueryState(
    val visible: Boolean = false,
    val snapshot: SnapshotReference? = null,
    val sourceVersion: String? = null,
    val input: String = "",
    val policy: FaceMatchPolicy = FaceMatchPolicy.ANY_FACE,
    val preparation: CatalogSearchPreparationState? = null,
    val busy: Boolean = false,
    val preparing: Boolean = false,
    val problem: Problem? = null,
    val diagnostics: List<QueryDiagnostic> = emptyList(),
    val page: Page<CardRecord>? = null,
    val selected: CardRecord? = null,
)

/** Explicit local work only. Every result is fenced by input, policy and the selected snapshot. */
internal class AdvancedQueryController(
    private val scope: CoroutineScope,
    private val query: LocalCardQuery,
    private val preparation: CatalogSearchPreparation,
    private val parse: (String) -> CardQueryParseResult,
    private val dispatcher: CoroutineDispatcher,
) {
    private val mutable = MutableStateFlow(AdvancedQueryState())
    val state: StateFlow<AdvancedQueryState> = mutable.asStateFlow()
    private var owner = 0L
    private var job: Job? = null
    private fun invalidate() { ++owner; job?.cancel(); job = null }
    fun open(snapshot: SnapshotReference?, sourceVersion: String?) {
        val old = mutable.value
        if (old.snapshot == snapshot && old.snapshot != null) {
            mutable.value = old.copy(visible = true)
            return
        } else {
            invalidate()
            mutable.value = AdvancedQueryState(visible = true, snapshot = snapshot, sourceVersion = sourceVersion,
                input = old.input, policy = old.policy, problem = if (snapshot == null) Problem.NOT_INSTALLED else null)
        }
        refreshPreparation()
    }
    fun resume() { mutable.value = mutable.value.copy(visible = true) }
    fun hide() { invalidate(); mutable.value = mutable.value.copy(visible = false, busy = false, preparing = false) }
    fun stop() { invalidate(); mutable.value = mutable.value.copy(busy = false, preparing = false) }
    fun input(value: String) {
        if (!mutable.value.visible) return
        if (value.length > 4096 || value.codePointCount(0, value.length) > 2048) {
            invalidate(); mutable.value = mutable.value.copy(busy = false, preparing = false, problem = Problem.INVALID_INPUT, page = null, selected = null)
            return
        }
        invalidate()
        mutable.value = mutable.value.copy(input = value, busy = false, preparing = false, page = null,
            selected = null, diagnostics = emptyList(), problem = null)
    }
    fun policy(value: FaceMatchPolicy) {
        if (!mutable.value.visible || mutable.value.policy == value) return
        invalidate()
        mutable.value = mutable.value.copy(policy = value, busy = false, preparing = false, page = null,
            selected = null, diagnostics = emptyList(), problem = null)
    }
    fun select(card: CardRecord) {
        val current = mutable.value
        val snapshot = current.snapshot ?: return
        if (current.visible && !current.busy && current.page?.items?.any { it == card } == true &&
            card.provenance.generation == snapshot.generation && card.oracleId.source == snapshot.source)
            mutable.value = current.copy(selected = card)
    }
    fun clearSelection() { mutable.value = mutable.value.copy(selected = null) }
    fun refreshPreparation() = preparationWork(false)
    fun prepare() = preparationWork(true)
    private fun preparationWork(advance: Boolean) {
        val current = mutable.value
        val generation = current.snapshot?.generation ?: return
        if (!current.visible || current.busy) return
        invalidate(); val ticket = owner
        mutable.value = current.copy(busy = true, preparing = advance, problem = null, page = null, selected = null)
        job = scope.launch {
            try {
                do {
                    currentCoroutineContext().ensureActive()
                    val result = callPort(dispatcher) { token ->
                        if (advance) preparation.advance(generation, token) else preparation.status(generation, token)
                    }
                    if (owner != ticket) return@launch
                    when (result) {
                        is PortResult.Failed -> { mutable.value = mutable.value.copy(problem = result.problem); return@launch }
                        is PortResult.Ok -> {
                            if (result.value.generation != generation || result.value.projectionVersion != 1) {
                                mutable.value = mutable.value.copy(problem = Problem.SOURCE_MISMATCH); return@launch
                            }
                            mutable.value = mutable.value.copy(preparation = result.value,
                                problem = if (result.value.readiness == CatalogSearchPreparationReadiness.FAILED) Problem.CORRUPT_DATA else null)
                            if (!advance || result.value.readiness == CatalogSearchPreparationReadiness.READY ||
                                result.value.readiness == CatalogSearchPreparationReadiness.FAILED) return@launch
                        }
                    }
                    yield()
                } while (true)
            } finally { if (owner == ticket) { mutable.value = mutable.value.copy(busy = false, preparing = false); job = null } }
        }
    }
    fun submit() = search(false)
    fun next() = search(true)
    private fun search(next: Boolean) {
        val current = mutable.value
        val snapshot = current.snapshot ?: return
        if (!current.visible || current.busy || current.preparation?.readiness != CatalogSearchPreparationReadiness.READY) return
        val cursor = if (next) current.page?.nextCursor ?: return else null
        invalidate(); val ticket = owner
        mutable.value = current.copy(busy = true, problem = null, diagnostics = emptyList(), selected = null,
            page = if (next) current.page else null)
        job = scope.launch {
            try {
                val parsed = callPort(dispatcher) { Outcome.Success(parse(current.input)) }
                if (owner != ticket) return@launch
                when (parsed) {
                    is PortResult.Failed -> { mutable.value = mutable.value.copy(problem = parsed.problem); return@launch }
                    is PortResult.Ok -> when (val ast = parsed.value) {
                        is CardQueryParseResult.Invalid -> { mutable.value = mutable.value.copy(diagnostics = ast.diagnostics); return@launch }
                        is CardQueryParseResult.Valid -> {
                            val result = callPort(dispatcher) { token -> query.local(ast.query,
                                SearchContext(SnapshotList.of(snapshot), SnapshotList.of(LanguageTag.ENGLISH), current.policy), PageRequest(30, cursor), token) }
                            if (owner != ticket) return@launch
                            when (result) {
                                is PortResult.Failed -> mutable.value = mutable.value.copy(problem = result.problem)
                                is PortResult.Ok -> {
                                    val page = result.value
                                    val valid = page.snapshot == snapshot && page.items.all {
                                        it.oracleId.source == snapshot.source && it.provenance.provider == snapshot.source && it.provenance.generation == snapshot.generation && it.language == LanguageTag.ENGLISH
                                    } && page.items.map { it.oracleId }.distinct().size == page.items.size &&
                                        page.items.size <= 30 && (page.nextCursor == null || (page.items.isNotEmpty() && page.nextCursor != cursor))
                                    mutable.value = mutable.value.copy(page = page.takeIf { valid }, problem = if (valid) null else Problem.SOURCE_MISMATCH)
                                }
                            }
                        }
                    }
                }
            } finally { if (owner == ticket) { mutable.value = mutable.value.copy(busy = false); job = null } }
        }
    }
}
