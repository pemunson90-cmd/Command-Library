package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.SettingChange
import com.architectpsg.commandlibrary.core.model.SettingsPatch
import com.architectpsg.commandlibrary.core.model.SettingsRepository
import com.architectpsg.commandlibrary.core.model.SettingsSnapshot
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.core.model.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class SettingsNotice {
    /** The stored settings changed after they were read; the latest values are shown and the selection was not saved. */
    CHANGED_ELSEWHERE,
}

/**
 * [snapshot] is the last committed settings state. The applied [theme] always follows it, never the
 * pending selection, so a failed save cannot leave the UI showing an uncommitted theme.
 */
internal data class SettingsUiState(
    val snapshot: SettingsSnapshot? = null,
    val loading: Boolean = true,
    val pendingTheme: ThemePreference? = null,
    val problem: Problem? = null,
    val notice: SettingsNotice? = null,
) {
    val theme: ThemePreference get() = snapshot?.theme ?: ThemePreference.SYSTEM
}

internal class SettingsController(
    private val scope: CoroutineScope,
    private val repository: SettingsRepository,
) {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun load() {
        if (job?.isActive == true) return
        mutableState.update { it.copy(loading = true, problem = null) }
        job = scope.launch {
            when (val result = callPort { repository.read(it) }) {
                is PortResult.Ok -> mutableState.update { it.copy(snapshot = result.value, loading = false, problem = null) }
                is PortResult.Failed -> mutableState.update { it.copy(loading = false, problem = result.problem) }
            }
        }
    }

    /** Saves [theme] against the revision that was read; a stale revision reloads instead of overwriting. */
    fun selectTheme(theme: ThemePreference) {
        val current = mutableState.value
        val snapshot = current.snapshot ?: return
        if (job?.isActive == true || snapshot.theme == theme) return
        mutableState.update { it.copy(pendingTheme = theme, problem = null, notice = null) }
        job = scope.launch {
            val patch = SettingsPatch(SnapshotList.of(SettingChange.Theme(theme)))
            when (val result = callPort { repository.update(patch, snapshot.revision, it) }) {
                is PortResult.Ok -> mutableState.update { it.copy(snapshot = result.value, pendingTheme = null) }
                is PortResult.Failed -> if (result.problem == Problem.STALE_REVISION) {
                    when (val latest = callPort { repository.read(it) }) {
                        is PortResult.Ok -> mutableState.update {
                            it.copy(snapshot = latest.value, pendingTheme = null, notice = SettingsNotice.CHANGED_ELSEWHERE)
                        }
                        is PortResult.Failed -> mutableState.update { it.copy(pendingTheme = null, problem = latest.problem) }
                    }
                } else {
                    mutableState.update { it.copy(pendingTheme = null, problem = result.problem) }
                }
            }
        }
    }

    fun dismissMessage() = mutableState.update { it.copy(problem = null, notice = null) }
}
