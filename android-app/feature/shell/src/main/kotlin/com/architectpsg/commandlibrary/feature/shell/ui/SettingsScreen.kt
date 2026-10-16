package com.architectpsg.commandlibrary.feature.shell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.SourceNotice
import com.architectpsg.commandlibrary.core.model.ThemePreference
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.SettingsNotice
import com.architectpsg.commandlibrary.feature.shell.presentation.SettingsUiState

/** Persisted theme, actionable setup/local-data guidance and real optional provider/source controls. */
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    catalogNotice: SourceNotice?,
    widthClass: WidthClass,
    onSelectTheme: (ThemePreference) -> Unit,
    onRetry: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
    onProviders: (() -> Unit)? = null,
    onCatalog: (() -> Unit)? = null,
    onRules: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = ReadingWidth)
                .fillMaxWidth()
                .padding(horizontal = widthClass.gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeading(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            SectionHeading(stringResource(R.string.settings_setup_title), modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.settings_setup_body))
            onCatalog?.let { open -> OutlinedButton(open,
                modifier = Modifier.heightIn(min = 48.dp).testTag("settings-open-catalog")) {
                Text(stringResource(R.string.settings_setup_catalog))
            } }
            onRules?.let { open -> OutlinedButton(open,
                modifier = Modifier.heightIn(min = 48.dp).testTag("settings-open-rules")) {
                Text(stringResource(R.string.settings_setup_rules))
            } }
            SectionHeading(stringResource(R.string.settings_storage_title), modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.settings_storage_body))
            Text(stringResource(R.string.settings_export_help))
            Text(stringResource(R.string.settings_network_help))
            onProviders?.let { open ->
                OutlinedButton(onClick = open, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.provider_profiles_manage))
                }
            }
            val snapshot = state.snapshot
            if (snapshot == null && state.loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Text(stringResource(R.string.settings_loading), style = MaterialTheme.typography.bodyLarge)
                }
            }
            state.problem?.let { problem ->
                MessageBlock(
                    title = stringResource(if (snapshot == null) R.string.settings_load_failed else R.string.settings_save_failed),
                    body = listOf(stringResource(problemText(problem))),
                    tone = MessageTone.ERROR,
                ) {
                    if (snapshot == null) {
                        OutlinedButton(onClick = onRetry, enabled = !state.loading) { Text(stringResource(R.string.shell_action_retry)) }
                    } else {
                        TextButton(onClick = onDismissMessage) { Text(stringResource(R.string.shell_action_dismiss)) }
                    }
                }
            }
            if (state.notice == SettingsNotice.CHANGED_ELSEWHERE) {
                MessageBlock(title = null, body = listOf(stringResource(R.string.settings_changed_elsewhere)), tone = MessageTone.INFO) {
                    TextButton(onClick = onDismissMessage) { Text(stringResource(R.string.shell_action_dismiss)) }
                }
            }
            if (snapshot != null) {
                SectionHeading(stringResource(R.string.settings_theme_title), modifier = Modifier.padding(top = 8.dp))
                val shown = state.pendingTheme ?: snapshot.theme
                val enabled = state.pendingTheme == null
                Column(Modifier.selectableGroup()) {
                    ThemeOption(ThemePreference.SYSTEM, R.string.settings_theme_system, shown, enabled, onSelectTheme)
                    ThemeOption(ThemePreference.LIGHT, R.string.settings_theme_light, shown, enabled, onSelectTheme)
                    ThemeOption(ThemePreference.DARK, R.string.settings_theme_dark, shown, enabled, onSelectTheme)
                }
                if (state.pendingTheme != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        Text(stringResource(R.string.settings_saving), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            SectionHeading(stringResource(R.string.settings_about_title), modifier = Modifier.padding(top = 16.dp))
            Text(stringResource(R.string.settings_about_body), style = MaterialTheme.typography.bodyLarge)
            val notices = (snapshot?.sourceNotices.orEmpty() + listOfNotNull(catalogNotice)).distinct()
            if (notices.isNotEmpty()) {
                SectionHeading(stringResource(R.string.settings_notices_title), modifier = Modifier.padding(top = 16.dp))
                notices.forEach { notice -> NoticeEntry(notice) }
            }
        }
    }
}

@Composable
private fun ThemeOption(
    option: ThemePreference,
    label: Int,
    shown: ThemePreference,
    enabled: Boolean,
    onSelect: (ThemePreference) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = shown == option, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(option) })
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = shown == option, onClick = null, enabled = enabled)
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Notice text is source content shown as supplied. */
@Composable
private fun NoticeEntry(notice: SourceNotice) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(notice.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(notice.attribution, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val license = notice.license
        if (license is Knowledge.Known) DetailField(stringResource(R.string.settings_notice_license), license.value)
    }
}
