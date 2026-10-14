package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class RulingsUiState(val oracle: OracleId? = null, val loading: Boolean = false,
    val status: RulingsStatus? = null, val page: Page<StoredRuling>? = null, val problem: Problem? = null)

/** One bounded local page. Selection changes cancel reads and invalidate even noncooperative replies. */
internal class RulingsController(private val scope: CoroutineScope, private val port: CatalogRulings, private val dispatcher: CoroutineDispatcher) {
    private val mutable = MutableStateFlow(RulingsUiState())
    val state: StateFlow<RulingsUiState> = mutable.asStateFlow()
    private var job: Job? = null
    private var ticket = 0L
    fun select(oracle: OracleId) { load(oracle, null) }
    fun refresh() { state.value.oracle?.let { load(it, null) } }
    fun next() {
        if(state.value.loading || state.value.problem != null) return
        val oracle = state.value.oracle ?: return
        state.value.page?.nextCursor?.let { load(oracle, it) }
    }
    fun close() { job?.cancel(); ++ticket; mutable.value = RulingsUiState() }
    private fun load(oracle: OracleId, cursor: String?) {
        job?.cancel(); val current = ++ticket
        val previous = state.value.status
        mutable.value = RulingsUiState(oracle, true, if(cursor == null) null else previous)
        job = scope.launch {
            try {
                val status = if(cursor == null) when(val result = callPort(dispatcher) { port.status(it) }) {
                    is PortResult.Ok -> result.value
                    is PortResult.Failed -> { if(current == ticket) mutable.update { it.copy(problem = result.problem) }; return@launch }
                } else requireNotNull(previous)
                if(current != ticket) return@launch
                mutable.update { it.copy(status = status) }
                val active = status.active ?: return@launch
                when(val result = callPort(dispatcher) { port.rulings(oracle, PageRequest(10, cursor), it) }) {
                    is PortResult.Ok -> if(current == ticket) {
                        val page = result.value
                        val consistent = page.snapshot.generation == active.id && page.snapshot.source == ProviderId("scryfall") &&
                            page.snapshot.language == null && page.items.all { it.content.oracleId == oracle && it.id.generation == active.id } && page.items.size <= 10
                        mutable.update { it.copy(page = page.takeIf { consistent }, problem = if(consistent) null else Problem.STALE_GENERATION) }
                    }
                    is PortResult.Failed -> if(current == ticket) mutable.update { it.copy(problem = result.problem) }
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { if(current == ticket) mutable.update { it.copy(problem = Problem.UNEXPECTED) } }
            finally { if(current == ticket) mutable.update { it.copy(loading = false) } }
        }
    }
}
