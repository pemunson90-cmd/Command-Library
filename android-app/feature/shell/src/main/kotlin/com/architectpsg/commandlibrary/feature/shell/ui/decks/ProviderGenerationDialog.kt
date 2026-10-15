package com.architectpsg.commandlibrary.feature.shell.ui.decks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.ProviderGenerationController
import com.architectpsg.commandlibrary.feature.shell.ui.problemText

@Composable
internal fun ProviderGenerationDialog(controller: ProviderGenerationController) {
    val state by controller.state.collectAsState()
    if (!state.visible) return
    val renderedFrame = state.frame
    Dialog(onDismissRequest = controller::hide) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.provider_generation_open), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.provider_generation_scope))
                SelectionContainer { Text(state.capture?.artifact?.rendered.orEmpty(), Modifier.testTag("provider-generation-prompt")) }
                state.profiles.forEach { row ->
                    FilterChip(selected = state.selected == row, onClick = { controller.select(row) },
                        enabled = !state.sending && !state.busy && !state.uncertain,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("provider-generation-profile-${row.profile.id.value}"),
                        label = { Text(row.profile.name) })
                }
                state.selected?.let { selected ->
                    Text(stringResource(R.string.provider_generation_destination, selected.profile.baseUri?.value.orEmpty(),
                        selected.profile.selectedModelId, selected.revision.value), Modifier.testTag("provider-generation-destination"))
                }
                if (state.cursor != null) TextButton({ controller.loadProfiles(true) }, enabled = !state.busy && !state.sending) {
                    Text(stringResource(R.string.provider_generation_more))
                }
                if (state.profiles.isEmpty() && !state.busy) Text(stringResource(R.string.provider_generation_no_profiles))
                TextButton({ controller.loadProfiles() }, enabled = !state.busy && !state.sending && !state.uncertain) {
                    Text(stringResource(R.string.provider_generation_reload_profiles))
                }
                Row {
                    Checkbox(state.consent, { controller.consent(it, renderedFrame) }, enabled = state.selected != null && !state.sending && !state.busy && !state.uncertain,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("provider-generation-consent"))
                    Text(stringResource(R.string.provider_generation_consent), Modifier.padding(top = 12.dp))
                }
                Button({ controller.send(renderedFrame) }, enabled = state.consent && !state.sending && !state.busy && !state.uncertain,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("provider-generation-send")) {
                    Text(stringResource(if (state.turn == null) R.string.provider_generation_send else R.string.provider_generation_retry))
                }
                if (state.sending) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(controller::cancel, Modifier.heightIn(min = 48.dp).testTag("provider-generation-cancel")) {
                        Text(stringResource(R.string.provider_generation_cancel))
                    }
                }
                state.turn?.let { turn ->
                    Text(stringResource(R.string.provider_generation_status, turn.status.name), Modifier.testTag("provider-generation-status"))
                    SelectionContainer { Text(turn.rawContent, Modifier.testTag("provider-generation-reply")) }
                    Text(stringResource(R.string.provider_generation_usage_unknown))
                }
                if (state.uncertain) {
                    Text(stringResource(R.string.provider_generation_uncertain), Modifier.testTag("provider-generation-uncertain"))
                    TextButton(controller::reload, enabled = !state.sending && !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.provider_generation_reload))
                    }
                    TextButton(controller::abandonUncertain, enabled = !state.sending && !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.provider_generation_abandon))
                    }
                }
                state.problem?.let { Text(stringResource(problemText(it)), color = MaterialTheme.colorScheme.error) }
                TextButton(controller::hide, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.shell_action_dismiss)) }
            }
        }
    }
}
