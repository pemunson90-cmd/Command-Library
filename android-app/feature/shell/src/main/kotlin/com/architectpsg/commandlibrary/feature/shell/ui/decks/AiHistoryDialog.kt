package com.architectpsg.commandlibrary.feature.shell.ui.decks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.architectpsg.commandlibrary.feature.shell.ui.problemText

@Composable
internal fun AiHistoryDialog(controller: AiHistoryController, onReview: (Turn) -> Boolean) {
    val state by controller.state.collectAsState()
    if (!state.visible) return
    val selected = state.selected
    val capture = state.capture
    val turn = state.turn
    var showPrompt by remember(capture?.prompt?.artifact, turn?.id) { mutableStateOf(false) }
    val prompt = if (showPrompt) capture?.prompt?.artifact?.rendered ?: turn?.prompt?.rendered.orEmpty() else ""
    val reply = capture?.reply ?: turn?.rawContent.orEmpty()
    val promptParts = remember(prompt) { historyTextParts(prompt) }
    val replyParts = remember(reply) { historyTextParts(reply) }
    Dialog(onDismissRequest = controller::hide) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).padding(16.dp)) {
                Text(stringResource(R.string.ai_history_title), style = MaterialTheme.typography.titleLarge)
                Text(state.deckName, style = MaterialTheme.typography.bodyMedium)
                LazyColumn(Modifier.weight(1f).testTag("ai-history-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.problem?.let { problem -> item { Text(problemText(problem), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("ai-history-problem")) } }
                    if (state.busy || state.writing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    if (capture != null) {
                        item { Text(stringResource(R.string.ai_history_capture_help)) }
                        item { OutlinedTextField(state.title, { controller.title(it, state.frame) },
                            label = { Text(stringResource(R.string.ai_history_name)) }, singleLine = true,
                            enabled = !state.writing && !state.uncertainSave && !state.saved,
                            isError = state.titleRejected, modifier = Modifier.fillMaxWidth().testTag("ai-history-title")) }
                        if (state.titleRejected) item { Text(stringResource(R.string.ai_history_title_invalid), color = MaterialTheme.colorScheme.error) }
                        if (state.saved) item { Text(stringResource(R.string.ai_history_saved), modifier = Modifier.testTag("ai-history-saved")) }
                        else item { Button({ controller.save(state.frame) }, enabled = !state.writing && !state.busy && !state.titleRejected,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("ai-history-confirm-save")) {
                            Text(stringResource(R.string.ai_history_save_confirm))
                        } }
                        if (state.uncertainSave) {
                            item { Text(stringResource(R.string.ai_history_unknown_save)) }
                            item { Text(stringResource(R.string.ai_history_no_rollback)) }
                            item { TextButton(controller::discardPendingCapture, enabled = !state.writing,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("ai-history-discard-intent")) {
                                Text(stringResource(R.string.ai_history_leave_pending))
                            } }
                        }
                    } else if (selected == null) {
                        item { OutlinedButton({ controller.refresh() }, enabled = !state.busy && !state.writing,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("ai-history-refresh")) { Text(stringResource(R.string.ai_history_refresh)) } }
                        if (!state.busy && state.problem == null && state.conversations.isEmpty())
                            item { Text(stringResource(R.string.ai_history_empty), modifier = Modifier.testTag("ai-history-empty")) }
                        items(state.conversations, key = { it.id.value }) { conversation ->
                            OutlinedButton({ controller.selectConversation(conversation.id) }, enabled = !state.busy && !state.writing,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai-history-conversation-${conversation.id.value}")) {
                                Text(conversation.title)
                            }
                        }
                        if (state.conversationCursor != null) item { TextButton({ controller.refresh(true) }, enabled = !state.busy && !state.writing,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("ai-history-more-conversations")) {
                            Text(stringResource(R.string.ai_history_more_conversations))
                        } }
                    } else if (turn == null) {
                        item { Text(checkNotNull(selected).title, style = MaterialTheme.typography.titleMedium) }
                        item { OutlinedButton({ controller.loadTurns() }, enabled = !state.busy && !state.writing,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("ai-history-load-turns")) { Text(stringResource(R.string.ai_history_open_turns)) } }
                        items(state.turns, key = { it.id.value }) { summary ->
                            OutlinedButton({ controller.selectTurn(summary.id) }, enabled = !state.busy && !state.writing,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai-history-turn-${summary.id.value}")) {
                                Column { Text(stringResource(R.string.ai_history_turn_number, summary.ordinal))
                                    Text(stringResource(historySourceText(summary.source)))
                                    if (summary.source == HistoryTurnSource.PROVIDER_GENERATION) Text(stringResource(historyStatusText(summary.status))) }
                            }
                        }
                        if (state.turnCursor != null) item { TextButton({ controller.loadTurns(true) }, enabled = !state.busy && !state.writing,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("ai-history-more-turns")) { Text(stringResource(R.string.ai_history_more_turns)) } }
                    }
                    if (capture != null || turn != null) {
                        val artifact = capture?.prompt?.artifact ?: checkNotNull(turn).prompt
                        val provenance = capture?.prompt?.provenance ?: checkNotNull(turn).provenance
                        val mode = capture?.prompt?.mode ?: checkNotNull(turn).mode
                        item { Text(stringResource(R.string.ai_history_provenance, mode.name, artifact.deckRevision.value, artifact.templateVersion),
                            modifier = Modifier.testTag("ai-history-provenance")) }
                        item { Text(stringResource(if (provenance.overrideUsed) R.string.ai_history_override else R.string.ai_history_shipped)) }
                        item { Text(stringResource(R.string.ai_history_hash, provenance.effectiveTemplateHash.hex)) }
                        if (turn != null) {
                            item { Text(stringResource(historySourceText(turn.source))) }
                            if (turn.source == HistoryTurnSource.PROVIDER_GENERATION) item { Text(stringResource(historyStatusText(turn.status))) }
                            val usage = (turn.usage as? Knowledge.Known)?.value
                            (usage?.inputTokens as? Knowledge.Known)?.value?.let { value -> item { Text(stringResource(R.string.ai_history_input_tokens, value)) } }
                            (usage?.outputTokens as? Knowledge.Known)?.value?.let { value -> item { Text(stringResource(R.string.ai_history_output_tokens, value)) } }
                            (usage?.cachedInputTokens as? Knowledge.Known)?.value?.let { value -> item { Text(stringResource(R.string.ai_history_cached_tokens, value)) } }
                            (usage?.billedAmount as? Knowledge.Known)?.value?.let { amount ->
                                val unit = when (val value = amount.unit) { is DecimalUnit.Fiat -> value.currencyCode
                                    DecimalUnit.Tix -> "TIX"; is DecimalUnit.Native -> "${value.provider.value}:${value.symbol}" }
                                item { Text(stringResource(R.string.ai_history_cost, amount.value.toPlainString(), unit)) }
                            }
                            item { Text(stringResource(R.string.ai_history_review_help)) }
                            item { OutlinedButton({ controller.reviewCurrent(onReview) }, enabled = reply.isNotEmpty() && !state.busy && !state.writing,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("ai-history-review")) { Text(stringResource(R.string.ai_history_review)) } }
                        }
                        item { TextButton({ showPrompt = !showPrompt }, modifier = Modifier.heightIn(min = 48.dp).testTag("ai-history-toggle-prompt")) {
                            Text(stringResource(if (showPrompt) R.string.ai_history_hide_prompt else R.string.ai_history_show_prompt))
                        } }
                        items(promptParts.indices.toList(), key = { "prompt-$it" }) { index ->
                            SelectionContainer { Text(promptParts[index], modifier = Modifier.testTag("ai-history-prompt-$index")) }
                        }
                        item { Text(stringResource(R.string.ai_history_reply), style = MaterialTheme.typography.titleMedium) }
                        if (replyParts.isEmpty()) item { Text(stringResource(R.string.ai_history_no_reply)) }
                        items(replyParts.indices.toList(), key = { "reply-$it" }) { index ->
                            SelectionContainer { Text(replyParts[index], modifier = Modifier.testTag("ai-history-reply-$index")) }
                        }
                    }
                    if (selected != null && capture == null) {
                        if (state.confirmDelete) {
                            item { Text(stringResource(R.string.ai_history_delete_warning)) }
                            item { ActionRow {
                                TextButton(controller::cancelDelete, enabled = !state.writing) { Text(stringResource(R.string.ai_history_cancel)) }
                                Button({ controller.delete(state.frame) }, enabled = !state.writing && !state.busy,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("ai-history-confirm-delete")) { Text(stringResource(R.string.ai_history_delete_confirm)) }
                            } }
                        } else item { TextButton(controller::askDelete, enabled = !state.writing && !state.busy,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("ai-history-delete")) { Text(stringResource(R.string.ai_history_delete)) } }
                    }
                }
                ActionRow {
                    if ((capture != null || selected != null) && !state.uncertainSave) TextButton(controller::back,
                        enabled = !state.writing, modifier = Modifier.heightIn(min = 48.dp).testTag("ai-history-back")) { Text(stringResource(R.string.ai_history_back)) }
                    TextButton(controller::hide, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.shell_action_dismiss)) }
                }
            }
        }
    }
}
private fun historySourceText(source: HistoryTurnSource): Int = when (source) {
    HistoryTurnSource.OFFLINE_CAPTURE -> R.string.ai_history_offline_capture
    HistoryTurnSource.PROVIDER_GENERATION -> R.string.ai_history_provider_attempt
}
private fun historyStatusText(status: TurnStatus): Int = when (status) {
    TurnStatus.PENDING -> R.string.ai_history_pending; TurnStatus.STREAMING -> R.string.ai_history_streaming
    TurnStatus.COMPLETE -> R.string.ai_history_complete; TurnStatus.INTERRUPTED -> R.string.ai_history_interrupted
    TurnStatus.FAILED -> R.string.ai_history_failed
}
/** Compose bounded paragraphs while preserving every UTF16 code unit and surrogate pair. */
private fun historyTextParts(value: String): List<String> = buildList {
    var start = 0
    while (start < value.length) {
        var end = minOf(start + 12_000, value.length)
        if (end < value.length && value[end - 1].isHighSurrogate() && value[end].isLowSurrogate()) end--
        add(value.substring(start, end)); start = end
    }
}
