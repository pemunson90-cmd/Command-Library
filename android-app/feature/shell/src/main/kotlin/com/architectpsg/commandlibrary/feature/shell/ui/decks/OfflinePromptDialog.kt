package com.architectpsg.commandlibrary.feature.shell.ui.decks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.ActionRow
import com.architectpsg.commandlibrary.feature.shell.ui.CardDetail
import com.architectpsg.commandlibrary.feature.shell.ui.WidthClass
import com.architectpsg.commandlibrary.feature.shell.ui.problemText

@Composable
internal fun OfflinePromptDialog(controller: OfflinePromptController, onCopy: (String) -> Boolean, onUseCurrentData: () -> Unit,
    onSaveHistory: ((OfflineHistoryCapture) -> Unit)? = null, historySaveEnabled: Boolean = true,
    onProvider: ((HistoryPromptCapture) -> Unit)? = null) {
    val state by controller.state.collectAsState()
    val copy by rememberUpdatedState(onCopy)
    val refresh by rememberUpdatedState(onUseCurrentData)
    if (!state.visible) return
    val editorMode = state.mode
    val editorFrame = state.frame
    Dialog(onDismissRequest = controller::hide) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).padding(16.dp)) {
                Text(stringResource(R.string.offline_prompt_title), style = MaterialTheme.typography.titleLarge)
                val detail = state.detail
                if (detail != null) {
                    TextButton(controller::closeDetail, Modifier.heightIn(min = 48.dp).testTag("offline-prompt-detail-back")) {
                        Text(stringResource(R.string.detail_back))
                    }
                    CardDetail(detail, null, WidthClass.COMPACT, Modifier.weight(1f).testTag("offline-prompt-detail"))
                } else {
                    LazyColumn(Modifier.weight(1f).testTag("offline-prompt-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text(stringResource(R.string.offline_prompt_intro)) }
                        item {
                            ActionRow {
                                OfflineLabMode.entries.forEach { mode ->
                                    FilterChip(selected = state.mode == mode, onClick = { controller.setMode(mode) },
                                        modifier = Modifier.heightIn(min = 48.dp).testTag("offline-prompt-mode-${mode.name}"),
                                        label = { Text(stringResource(modeLabel(mode))) })
                                }
                            }
                        }
                        if (state.mode == OfflineLabMode.ROAST) item {
                            ActionRow {
                                RoastIntensity.entries.forEach { intensity ->
                                    FilterChip(selected = state.intensity == intensity, onClick = { controller.setIntensity(intensity) },
                                        modifier = Modifier.heightIn(min = 48.dp).testTag("offline-prompt-intensity-${intensity.name}"),
                                        label = { Text(stringResource(intensityLabel(intensity))) })
                                }
                            }
                        }
                        item { PromptField(state.question, R.string.offline_prompt_question, "offline-prompt-question",
                            OfflinePromptField.QUESTION, state, controller) }
                        item { PromptField(state.constraints, R.string.offline_prompt_constraints, "offline-prompt-constraints",
                            OfflinePromptField.CONSTRAINTS, state, controller) }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Checkbox(state.includePrivateNotes, controller::setIncludePrivateNotes,
                                    Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("offline-prompt-include-notes"))
                                Text(stringResource(R.string.offline_prompt_include_notes), Modifier.padding(top = 12.dp))
                            }
                        }
                        item {
                            Text(stringResource(if (state.persistenceAvailable) R.string.offline_prompt_persistent_help else R.string.offline_prompt_session))
                            Text(stringResource(R.string.offline_prompt_template_version, state.mode.shippedVersion))
                            if (state.persistenceAvailable) {
                                if (state.versionMismatch) Text(stringResource(R.string.offline_prompt_version_mismatch,
                                    checkNotNull(state.baseline.row).shippedVersion, state.mode.shippedVersion),
                                    Modifier.testTag("offline-prompt-template-version-mismatch"))
                                if (state.templateSaved) Text(stringResource(if (state.baseline.row?.body == null)
                                    R.string.offline_prompt_shipped else R.string.offline_prompt_saved),
                                    Modifier.testTag("offline-prompt-template-saved"))
                                if (state.mode in state.dirtyModes) Text(stringResource(R.string.offline_prompt_unsaved))
                                state.templateProblems[state.mode]?.let {
                                    Text(stringResource(problemText(it)), color = MaterialTheme.colorScheme.error)
                                }
                                if (!state.templateReady) {
                                    Text(stringResource(R.string.offline_prompt_load_required))
                                    state.baseline.problem?.let { Text(stringResource(problemText(it)), color = MaterialTheme.colorScheme.error) }
                                    TextButton({ controller.reloadTemplate(editorMode) }, Modifier.heightIn(min = 48.dp)
                                        .testTag("offline-prompt-template-reload"), enabled = state.writingMode != state.mode) {
                                        Text(stringResource(R.string.offline_prompt_reload))
                                    }
                                }
                            }
                        }
                        item { PromptField(state.templateBody, R.string.offline_prompt_template, "offline-prompt-override",
                            OfflinePromptField.OVERRIDE, state, controller) }
                        item {
                            ActionRow {
                                if (state.persistenceAvailable) Button({ controller.saveTemplate(editorMode, editorFrame) },
                                    Modifier.heightIn(min = 48.dp).testTag("offline-prompt-save-template"),
                                    enabled = state.templateReady && state.templates[state.mode] != null && state.writingMode == null && state.mode !in state.rejectedOverrides) {
                                    Text(stringResource(R.string.offline_prompt_save))
                                }
                                TextButton({ controller.resetTemplate(editorMode, editorFrame) }, Modifier.heightIn(min = 48.dp).testTag("offline-prompt-reset"),
                                    enabled = state.templateReady && state.writingMode == null) {
                                    Text(stringResource(R.string.offline_prompt_reset))
                                }
                                TextButton({ controller.useCurrentData { refresh() } }, Modifier.heightIn(min = 48.dp).testTag("offline-prompt-current-data")) {
                                    Text(stringResource(R.string.offline_prompt_current_data))
                                }
                            }
                            Text(stringResource(if (state.snapshot == null) R.string.offline_prompt_no_snapshot else R.string.offline_prompt_snapshot))
                        }
                        item {
                            Button(controller::render, Modifier.heightIn(min = 48.dp).testTag("offline-prompt-render"),
                                enabled = !state.busy && state.rejected.isEmpty() && state.templates[state.mode] != null && state.templateReady && !state.versionMismatch) {
                                Text(stringResource(R.string.offline_prompt_render))
                            }
                        }
                        state.result?.let { result ->
                            item {
                                Text(stringResource(R.string.offline_prompt_contexts, result.oracleContextsIncluded, result.oracleContextsOmitted))
                                SelectionContainer { Text(result.artifact.rendered, Modifier.testTag("offline-prompt-preview")) }
                            }
                            item {
                                Button({ controller.copyPrompt { copy(it) } }, Modifier.heightIn(min = 48.dp).testTag("offline-prompt-copy"), enabled = !state.busy) {
                                    Text(stringResource(R.string.offline_prompt_copy))
                                }
                            }
                        }
                        if (onProvider != null) item {
                            OutlinedButton({ controller.captureHistory(editorMode, editorFrame)?.prompt?.let(onProvider) },
                                enabled = state.historyPrompt != null && !state.busy && state.rejected.isEmpty(),
                                modifier = Modifier.heightIn(min = 48.dp).testTag("offline-prompt-provider")) {
                                Text(stringResource(R.string.provider_generation_open))
                            }
                        }
                        state.copied?.let { copied -> item {
                            Text(stringResource(if (copied) R.string.offline_prompt_copied else R.string.offline_prompt_copy_failed),
                                Modifier.testTag("offline-prompt-copy-feedback"))
                        } }
                        if (onSaveHistory != null) {
                            item { Text(stringResource(R.string.ai_history_prompt_required)) }
                            item { OutlinedButton({ controller.captureHistory(editorMode, editorFrame)?.let(onSaveHistory) },
                                enabled = historySaveEnabled && state.historyPrompt != null && !state.busy && state.rejected.isEmpty(),
                                modifier = Modifier.heightIn(min = 48.dp).testTag("offline-prompt-save-history")) {
                                Text(stringResource(R.string.ai_history_save))
                            } }
                        }
                        item { Text(stringResource(R.string.offline_prompt_review_scope)) }
                        item { PromptField(state.reply, R.string.offline_prompt_reply, "offline-prompt-reply",
                            OfflinePromptField.REPLY, state, controller) }
                        item {
                            Button(controller::review, Modifier.heightIn(min = 48.dp).testTag("offline-prompt-review"),
                                enabled = !state.busy && state.rejected.isEmpty()) { Text(stringResource(R.string.offline_prompt_review)) }
                        }
                        state.review?.let { review ->
                            item {
                                Text(stringResource(R.string.offline_prompt_checks))
                                Text(stringResource(R.string.offline_prompt_cost), Modifier.testTag("offline-prompt-cost"))
                                Text(stringResource(R.string.offline_prompt_angle,
                                    (review.statedSurpriseAngle as? Knowledge.Known)?.value ?: stringResource(R.string.offline_prompt_angle_unknown)), Modifier.testTag("offline-prompt-angle"))
                            }
                            review.references.forEachIndexed { index, ref -> item {
                                val canOpen = ref.status == OfflineReferenceStatus.FOUND && ref.candidates.size == 1 && ref.snapshot != null
                                if (canOpen) TextButton({ controller.openReference(index, review) }, Modifier.heightIn(min = 48.dp)
                                    .testTag("offline-prompt-reference-$index"), enabled = !state.busy) { Text(ref.rawName) }
                                else Text(ref.rawName, Modifier.testTag("offline-prompt-reference-$index"))
                                Text(stringResource(referenceLabel(ref.status)), Modifier.testTag("offline-prompt-reference-status-$index"))
                            } }
                        }
                        if (state.busy || state.writingMode != null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        if (state.rejected.isNotEmpty()) item {
                            Text(stringResource(R.string.offline_prompt_rejected), Modifier.testTag("offline-prompt-problem"), color = MaterialTheme.colorScheme.error)
                        } else state.problem?.let { problem -> item {
                            Text(stringResource(problemText(problem)), Modifier.testTag("offline-prompt-problem"), color = MaterialTheme.colorScheme.error)
                        } }
                    }
                }
                TextButton(controller::hide, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.shell_action_dismiss)) }
            }
        }
    }
}

@Composable
private fun PromptField(value: String, label: Int, tag: String, field: OfflinePromptField,
    state: OfflinePromptState, controller: OfflinePromptController) {
    OutlinedTextField(value, { controller.setText(field, it, state.mode, state.frame) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag),
        enabled = field != OfflinePromptField.OVERRIDE || state.templateReady,
        label = { Text(stringResource(label)) }, isError = field in state.rejected,
        supportingText = if (field in state.rejected) ({ Text(stringResource(R.string.offline_prompt_rejected)) }) else null)
}
private fun modeLabel(mode: OfflineLabMode): Int = when (mode) {
    OfflineLabMode.ROAST -> R.string.offline_prompt_roast
    OfflineLabMode.CHAT -> R.string.offline_prompt_chat
    OfflineLabMode.UPGRADE -> R.string.offline_prompt_upgrade
    OfflineLabMode.SURPRISE -> R.string.offline_prompt_surprise
}
private fun intensityLabel(intensity: RoastIntensity): Int = when (intensity) {
    RoastIntensity.GENTLE -> R.string.offline_prompt_gentle
    RoastIntensity.MEDIUM -> R.string.offline_prompt_medium
    RoastIntensity.SPICY -> R.string.offline_prompt_spicy
}
private fun referenceLabel(status: OfflineReferenceStatus): Int = when (status) {
    OfflineReferenceStatus.FOUND -> R.string.offline_prompt_found
    OfflineReferenceStatus.AMBIGUOUS -> R.string.offline_prompt_ambiguous
    OfflineReferenceStatus.NOT_FOUND -> R.string.offline_prompt_not_found
    OfflineReferenceStatus.UNAVAILABLE -> R.string.offline_prompt_unavailable
}
