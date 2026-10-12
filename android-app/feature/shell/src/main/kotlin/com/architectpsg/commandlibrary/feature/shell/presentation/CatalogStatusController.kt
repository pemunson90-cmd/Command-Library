package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.CatalogGeneration
import com.architectpsg.commandlibrary.core.model.CatalogStatus
import com.architectpsg.commandlibrary.core.model.GenerationStatus
import com.architectpsg.commandlibrary.core.model.LocalCatalogStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [status] is the last committed status read from the port; a failed refresh keeps it and reports [problem]. */
internal data class CatalogStatusUiState(
    val status: LocalCatalogStatus? = null,
    val loading: Boolean = true,
    val problem: Problem? = null,
)

internal fun LocalCatalogStatus.activeGeneration(): CatalogGeneration? =
    activeGenerations.firstOrNull { it.status == GenerationStatus.ACTIVE } ?: activeGenerations.firstOrNull()

internal val LocalCatalogStatus.installed: Boolean get() = activeGenerations.isNotEmpty()

internal class CatalogStatusController(
    private val scope: CoroutineScope,
    private val port: CatalogStatus,
) {
    private val mutableState = MutableStateFlow(CatalogStatusUiState())
    val state: StateFlow<CatalogStatusUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    /** Reads committed status again; a newer refresh supersedes one still in flight. */
    fun refresh() {
        job?.cancel()
        mutableState.update { it.copy(loading = true) }
        job = scope.launch {
            when (val result = callPort { port.status(it) }) {
                is PortResult.Ok -> mutableState.value = CatalogStatusUiState(status = result.value, loading = false, problem = null)
                is PortResult.Failed -> mutableState.update { it.copy(loading = false, problem = result.problem) }
            }
        }
    }
}
