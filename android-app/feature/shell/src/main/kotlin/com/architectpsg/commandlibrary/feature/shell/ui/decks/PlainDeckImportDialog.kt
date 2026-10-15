package com.architectpsg.commandlibrary.feature.shell.ui.decks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.ActionRow
import com.architectpsg.commandlibrary.feature.shell.ui.problemText

private fun importOwned(s: PlainDeckImportState): Boolean = s.submitting || s.uncertain || s.awaitingFresh
private inline fun importCommand(controller: PlainDeckImportController, ticket: Long, action: (PlainDeckImportState) -> Unit) {
    val current = controller.state.value
    if (current.formTicket == ticket && current.visible) action(current)
}
private fun syntaxReady(input: PlainDeckList): Boolean = input.complete && input.rows.any { it.entry != null } &&
    input.rows.none { row -> row.issues.any { it.severity == IssueSeverity.ERROR } }

/** These are the same business callbacks passed to widgets, independent of node attachment. */
internal fun plainImportNameAction(controller: PlainDeckImportController, ticket: Long): (String) -> Unit = { raw ->
    importCommand(controller, ticket) { if (!importOwned(it)) controller.name(raw) }
}
internal fun plainImportConfirmAction(controller: PlainDeckImportController, ticket: Long,
    preview: PlainDeckImportPreview, pickerPending: () -> Boolean): () -> Unit = {
    importCommand(controller, ticket) { current ->
        if (!pickerPending() && !importOwned(current) && !current.parsing && !current.previewing && current.preview === preview)
            controller.confirm(preview)
    }
}
internal data class PlainImportWarningFrame(val ticket: Long, val inputName: String?, val input: PlainDeckList?,
    val name: String, val preview: PlainDeckImportPreview?)
internal fun plainImportWarningFrame(state: PlainDeckImportState): PlainImportWarningFrame =
    PlainImportWarningFrame(state.formTicket, state.inputName, state.input, state.name, state.preview)
private fun warningCurrent(state: PlainDeckImportState, frame: PlainImportWarningFrame, pending: Boolean): Boolean =
    state.visible && state.formTicket == frame.ticket && state.inputName == frame.inputName && state.input === frame.input &&
        state.name == frame.name && state.preview === frame.preview && !importOwned(state) && !pending
internal fun plainImportReplaceAction(controller: PlainDeckImportController, frame: PlainImportWarningFrame,
    pickerPending: () -> Boolean, choose: () -> Unit): () -> Unit = {
    if (warningCurrent(controller.state.value, frame, pickerPending())) choose()
}
internal fun plainImportDiscardAction(controller: PlainDeckImportController, frame: PlainImportWarningFrame,
    pickerPending: () -> Boolean): () -> Unit = {
    if (warningCurrent(controller.state.value, frame, pickerPending())) controller.discardInput()
}

/** Root supplies real picker admission. Composition never opens a stream, generates IDs or parses. */
@Composable
internal fun PlainDeckImportSurface(controller: PlainDeckImportController, onChooseFile: () -> Unit, pickerPending: Boolean = false) {
    val state by controller.state.collectAsState()
    val pending by rememberUpdatedState(pickerPending)
    val choose by rememberUpdatedState(onChooseFile)
    key(state.formTicket) {
        // Preserve the actual physical-row position while the dialog is hidden.
        val list = rememberLazyListState()
        when {
            state.visible -> PlainImportDialog(controller, state, list, { pending }, { choose() })
            state.inputName != null -> Surface(tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.plain_import_hidden))
                    TextButton({
                        val current = controller.state.value
                        if (current.formTicket == state.formTicket && !current.visible && current.inputName != null) controller.resume()
                    }, modifier = Modifier.importTouch().testTag("plain-import-resume")) { Text(stringResource(R.string.plain_import_resume)) }
                }
            }
            state.currentHead != null && state.acknowledgedId != null -> {
                val head = state.currentHead
                Surface(tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.plain_import_saved, head.name), modifier = Modifier.testTag("plain-import-saved"))
                        TextButton({
                            val current = controller.state.value
                            if (current.formTicket == state.formTicket && !current.visible && current.currentHead === head &&
                                current.acknowledgedId == state.acknowledgedId && current.input == null && !importOwned(current) && !pending)
                                controller.discardInput()
                        }, enabled = !pending, modifier = Modifier.importTouch().testTag("plain-import-dismiss")) { Text(stringResource(R.string.plain_import_dismiss)) }
                    }
                }
            }
        }
    }
}

private enum class ImportWarningKind { REPLACE, DISCARD }
@Composable
private fun PlainImportDialog(controller: PlainDeckImportController, state: PlainDeckImportState, list: LazyListState,
    pickerPending: () -> Boolean, chooseFile: () -> Unit) {
    val ticket = state.formTicket
    val editable = !importOwned(state)
    val pending = pickerPending()
    var warning by remember(ticket) { mutableStateOf<Pair<ImportWarningKind, PlainImportWarningFrame>?>(null) }
    val input = state.input
    val preview = state.preview
    AlertDialog(onDismissRequest = { importCommand(controller, ticket) { controller.close() } },
        title = { Text(stringResource(R.string.plain_import_title)) }, text = {
            LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).testTag("plain-import-list"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "new-deck") { Text(stringResource(R.string.plain_import_new_deck), modifier = Modifier.testTag("plain-import-new-deck")) }
                item(key = "unresolved") { Text(stringResource(R.string.plain_import_unresolved), modifier = Modifier.testTag("plain-import-unresolved")) }
                item(key = "syntax-only") { Text(stringResource(R.string.plain_import_syntax_only)) }
                item(key = "name") {
                    OutlinedTextField(state.name, plainImportNameAction(controller, ticket), enabled = editable,
                        label = { Text(stringResource(R.string.plain_import_name)) }, modifier = Modifier.fillMaxWidth().testTag("plain-import-name"))
                    Text(stringResource(R.string.plain_import_name_help))
                }
                if (state.parsing) item(key = "reading") {
                    Text(stringResource(R.string.plain_import_reading))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedButton({ importCommand(controller, ticket) { if (it.parsing && !importOwned(it)) controller.cancelParse() } },
                        modifier = Modifier.importTouch().testTag("plain-import-stop-reading")) { Text(stringResource(R.string.plain_import_stop_reading)) }
                }
                if (state.previewing || state.submitting) item(key = "working") {
                    Text(stringResource(if (state.previewing) R.string.plain_import_previewing else R.string.plain_import_submitting))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (pending) item(key = "picker-pending") { Text(stringResource(R.string.plain_import_picker_pending), modifier = Modifier.testTag("plain-import-picker-pending")) }
                if (state.uncertain) item(key = "uncertain") { Text(stringResource(R.string.plain_import_uncertain), modifier = Modifier.testTag("plain-import-uncertain")) }
                if (state.awaitingFresh) item(key = "awaiting-fresh") { Text(stringResource(R.string.plain_import_awaiting_fresh), modifier = Modifier.testTag("plain-import-awaiting-fresh")) }
                state.problem?.let { problem -> item(key = "problem") { Text(stringResource(problemText(problem)), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("plain-import-problem")) } }
                if (input != null && !syntaxReady(input)) item(key = "invalid-input") {
                    Text(stringResource(R.string.plain_import_invalid_rows), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("plain-import-invalid"))
                }
                if (preview != null) item(key = "preview-summary") {
                    Text(stringResource(R.string.plain_import_preview_summary, preview.input.rows.count { it.entry != null },
                        preview.input.rows.sumOf { (it.entry?.quantity ?: 0).toLong() }), modifier = Modifier.testTag("plain-import-preview-summary"))
                }
                items(input?.rows ?: SnapshotList.empty(), key = { it.location.rowNumber }) { row ->
                    Text(stringResource(R.string.plain_import_line, row.location.rowNumber), style = MaterialTheme.typography.titleSmall)
                    // No maxLines/ellipsis or substring: the exact physical row remains selectable.
                    Box(Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                        SelectionContainer {
                            Text(if (row.rawText.isEmpty()) stringResource(R.string.plain_import_blank_line) else row.rawText,
                                softWrap = false, modifier = Modifier.testTag("plain-import-row-${row.location.rowNumber}"))
                        }
                    }
                    row.entry?.let { entry -> Text(stringResource(R.string.plain_import_card_row, entry.quantity, stringResource(boardLabel(entry.board)))) }
                    row.issues.forEach { issue -> Text(stringResource(plainImportIssueLabel(issue.code)), color = MaterialTheme.colorScheme.error) }
                }
                item(key = "input-actions") {
                    ActionRow {
                        OutlinedButton({ importCommand(controller, ticket) { latest ->
                            if (!importOwned(latest) && !pickerPending()) warning = ImportWarningKind.REPLACE to plainImportWarningFrame(latest)
                        } }, enabled = editable && !pending, modifier = Modifier.importTouch().testTag("plain-import-choose")) { Text(stringResource(R.string.plain_import_choose)) }
                        OutlinedButton({ importCommand(controller, ticket) { latest ->
                            if (!importOwned(latest) && !pickerPending()) warning = ImportWarningKind.DISCARD to plainImportWarningFrame(latest)
                        } }, enabled = editable && !pending, modifier = Modifier.importTouch().testTag("plain-import-discard")) { Text(stringResource(R.string.plain_import_discard)) }
                    }
                }
            }
        }, confirmButton = {
            when {
                state.uncertain || state.awaitingFresh -> TextButton({ importCommand(controller, ticket) {
                    if (!it.submitting && (it.uncertain || it.awaitingFresh)) controller.retry()
                } }, enabled = !state.submitting, modifier = Modifier.importTouch().testTag("plain-import-retry")) { Text(stringResource(R.string.plain_import_retry)) }
                preview != null -> TextButton(plainImportConfirmAction(controller, ticket, preview, pickerPending),
                    enabled = editable && !state.parsing && !state.previewing && !pending,
                    modifier = Modifier.importTouch().testTag("plain-import-confirm")) { Text(stringResource(R.string.plain_import_confirm)) }
                else -> TextButton({ importCommand(controller, ticket) { latest ->
                    if (!importOwned(latest) && !latest.parsing && !latest.previewing && !pickerPending()) controller.preview()
                } }, enabled = editable && !state.parsing && !state.previewing && !pending && input != null && syntaxReady(input),
                    modifier = Modifier.importTouch().testTag("plain-import-preview")) { Text(stringResource(R.string.plain_import_preview)) }
            }
        }, dismissButton = {
            TextButton({ importCommand(controller, ticket) { controller.close() } }, modifier = Modifier.importTouch().testTag("plain-import-close")) { Text(stringResource(R.string.plain_import_close)) }
        })
    warning?.let { (kind, frame) ->
        val replace = kind == ImportWarningKind.REPLACE
        AlertDialog(onDismissRequest = { importCommand(controller, ticket) { warning = null } },
            title = { Text(stringResource(if (replace) R.string.plain_import_replace_title else R.string.plain_import_discard_title)) },
            text = { Text(stringResource(if (replace) R.string.plain_import_replace_warning else R.string.plain_import_discard_warning)) },
            confirmButton = {
                val action = if (replace) plainImportReplaceAction(controller, frame, pickerPending) { warning = null; chooseFile() }
                    else plainImportDiscardAction(controller, frame, pickerPending)
                TextButton(action, enabled = warningCurrent(state, frame, pending), modifier = Modifier.importTouch()
                    .testTag(if (replace) "plain-import-replace-confirm" else "plain-import-discard-confirm")) {
                    Text(stringResource(if (replace) R.string.plain_import_replace_confirm else R.string.plain_import_discard_confirm))
                }
            }, dismissButton = {
                TextButton({ importCommand(controller, ticket) { warning = null } }, modifier = Modifier.importTouch()
                    .testTag(if (replace) "plain-import-replace-cancel" else "plain-import-discard-cancel")) { Text(stringResource(R.string.plain_import_cancel)) }
            })
    }
}

private fun Modifier.importTouch(): Modifier = this.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
private fun plainImportIssueLabel(code: String): Int = when (code) {
    PlainDeckListIssueCodes.INVALID_QUANTITY -> R.string.plain_import_invalid_quantity
    PlainDeckListIssueCodes.INVALID_ROW -> R.string.plain_import_invalid_row
    PlainDeckListIssueCodes.INVALID_MARKER -> R.string.plain_import_invalid_marker
    else -> R.string.plain_import_other_issue
}
