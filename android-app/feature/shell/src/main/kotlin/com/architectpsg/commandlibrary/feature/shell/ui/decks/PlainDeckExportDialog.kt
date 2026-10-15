package com.architectpsg.commandlibrary.feature.shell.ui.decks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.document.PlainDeckWriteResult
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.ActionRow
import com.architectpsg.commandlibrary.feature.shell.ui.problemText

private fun exportIdle(state: PlainDeckExportState, pending: Boolean): Boolean =
    !state.preparing && state.pendingPicker == null && !pending && state.writePhase == PlainDeckExportWritePhase.IDLE

/** Business callback used by the actual widget; stale node attachment is never its authority. */
internal fun plainExportChooseAction(controller: PlainDeckExportController, ticket: Long, preview: PlainDeckExportPreview,
    pickerPending: () -> Boolean, choose: (PlainDeckExportPreview) -> Unit): () -> Unit = {
    val current = controller.state.value
    if (current.formTicket == ticket && current.visible && current.preview === preview && exportIdle(current, pickerPending()) &&
        current.attempts < PlainDeckExportLimits.MAX_DESTINATION_ATTEMPTS) choose(preview)
}

internal data class PlainExportEndFrame(val ticket: Long, val preview: PlainDeckExportPreview, val attempts: Int,
    val result: PlainDeckWriteResult?, val problem: Problem?)
internal fun plainExportEndFrame(state: PlainDeckExportState, preview: PlainDeckExportPreview): PlainExportEndFrame =
    PlainExportEndFrame(state.formTicket, preview, state.attempts, state.lastWrite, state.problem)
private fun endCurrent(state: PlainDeckExportState, frame: PlainExportEndFrame, pending: Boolean): Boolean =
    state.visible && state.formTicket == frame.ticket && state.preview === frame.preview && state.attempts == frame.attempts &&
        state.lastWrite === frame.result && state.problem == frame.problem && exportIdle(state, pending)
internal fun plainExportEndAction(controller: PlainDeckExportController, frame: PlainExportEndFrame,
    pickerPending: () -> Boolean): () -> Unit = {
    if (endCurrent(controller.state.value, frame, pickerPending())) controller.discard(frame.preview)
}
internal fun plainExportHideAction(controller: PlainDeckExportController, ticket: Long): () -> Unit = {
    val current = controller.state.value
    if (current.formTicket == ticket && current.visible) controller.hide()
}

/** Root owns picker admission and all IO. Composition/resume never chooses or writes a file. */
@Composable
internal fun PlainDeckExportSurface(controller: PlainDeckExportController,
    onChooseFile: (PlainDeckExportPreview) -> Unit, pickerPending: Boolean = false) {
    val state by controller.state.collectAsState()
    val pending by rememberUpdatedState(pickerPending)
    val choose by rememberUpdatedState(onChooseFile)
    key(state.formTicket) {
        val list = rememberLazyListState()
        val preview = state.preview
        when {
            state.visible -> PlainExportDialog(controller, state, list, { pending }, { choose(it) })
            preview != null -> Surface(tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.plain_export_hidden))
                    ExportPhase(state)
                    ExportResult(state.lastWrite)
                    TextButton({
                        val current = controller.state.value
                        if (current.formTicket == state.formTicket && !current.visible && current.preview === preview) controller.resume()
                    }, modifier = Modifier.exportTouch().testTag("plain-export-resume")) { Text(stringResource(R.string.plain_export_resume)) }
                }
            }
        }
    }
}

@Composable
private fun PlainExportDialog(controller: PlainDeckExportController, state: PlainDeckExportState, list: LazyListState,
    pickerPending: () -> Boolean, chooseFile: (PlainDeckExportPreview) -> Unit) {
    val ticket = state.formTicket
    val preview = state.preview
    val pending = pickerPending()
    val idle = exportIdle(state, pending)
    val entries = remember(preview) { preview?.source?.entries?.associateBy { it.id } ?: emptyMap() }
    val total = remember(preview) { preview?.source?.entries?.sumOf { it.quantity.toLong() } ?: 0L }
    var warning by remember(ticket) { mutableStateOf<PlainExportEndFrame?>(null) }
    AlertDialog(onDismissRequest = plainExportHideAction(controller, ticket),
        title = { Text(stringResource(R.string.plain_export_open)) }, text = {
            LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).testTag("plain-export-list"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (preview != null) {
                    item(key = "plain-export-source") {
                        SelectionContainer { Text(preview.source.name, modifier = Modifier.testTag("plain-export-source"), style = MaterialTheme.typography.titleMedium) }
                    }
                    item(key = "plain-export-revision") { Text(stringResource(R.string.plain_export_revision, preview.source.revision.value), modifier = Modifier.testTag("plain-export-revision")) }
                    item(key = "plain-export-format") { Text(stringResource(R.string.plain_export_format), modifier = Modifier.testTag("plain-export-format")) }
                    item(key = "plain-export-summary") { Text(stringResource(R.string.plain_export_summary, preview.source.entries.size, total), modifier = Modifier.testTag("plain-export-summary")) }
                    item(key = "plain-export-loss-summary") { Text(stringResource(R.string.plain_export_loss_summary, preview.export.losses.size), modifier = Modifier.testTag("plain-export-loss-summary")) }
                    items(preview.export.losses, key = { plainExportLossKey(it) }) { loss ->
                        val rowKey = plainExportLossKey(loss)
                        val entry = entries[loss.entryId]
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(plainExportLossLabel(loss.field)), modifier = Modifier.testTag(rowKey), style = MaterialTheme.typography.titleSmall)
                            if (entry != null) {
                                val name = (entry.identity.originalName as? Knowledge.Known)?.value
                                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                    SelectionContainer { Text(name ?: stringResource(R.string.plain_export_unnamed_entry), softWrap = false, modifier = Modifier.testTag("$rowKey-name")) }
                                }
                                Text(stringResource(R.string.plain_export_entry_details, entry.quantity, stringResource(boardLabel(entry.board))))
                            }
                        }
                    }
                }
                if (state.preparing) item(key = "plain-export-preparing") {
                    Text(stringResource(R.string.plain_export_preparing), modifier = Modifier.testTag("plain-export-preparing"))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedButton({
                        val current = controller.state.value
                        if (current.formTicket == ticket && current.visible && current.preparing) controller.cancelPreparation()
                    }, modifier = Modifier.exportTouch().testTag("plain-export-stop-preparation")) { Text(stringResource(R.string.plain_export_stop_preparation)) }
                }
                if (state.pendingPicker != null || pending) item(key = "plain-export-pending") {
                    Text(stringResource(if (state.pendingPicker != null && !state.pickerAuthorized) R.string.plain_export_pending_revoked else R.string.plain_export_pending), modifier = Modifier.testTag("plain-export-pending"))
                }
                if (state.writePhase != PlainDeckExportWritePhase.IDLE) item(key = "plain-export-phase") {
                    ExportPhase(state)
                    if (state.writePhase == PlainDeckExportWritePhase.WRITING) OutlinedButton({
                        val current = controller.state.value
                        if (current.formTicket == ticket && current.visible && current.preview === preview &&
                            current.attempts == state.attempts && current.writePhase == PlainDeckExportWritePhase.WRITING) controller.cancelWrite()
                    }, modifier = Modifier.exportTouch().testTag("plain-export-cancel-write")) { Text(stringResource(R.string.plain_export_cancel_write)) }
                }
                if (state.lastWrite != null) item(key = "plain-export-result") { ExportResult(state.lastWrite) }
                state.problem?.let { problem -> item(key = "plain-export-problem") {
                    Text(stringResource(problemText(problem)), modifier = Modifier.testTag("plain-export-problem"), color = MaterialTheme.colorScheme.error)
                } }
                if (state.attempts >= PlainDeckExportLimits.MAX_DESTINATION_ATTEMPTS) item(key = "plain-export-limit") {
                    Text(stringResource(R.string.plain_export_limit), modifier = Modifier.testTag("plain-export-limit"))
                }
                if (preview != null) item(key = "plain-export-actions") {
                    ActionRow {
                        OutlinedButton({
                            val current = controller.state.value
                            if (current.formTicket == ticket && current.visible && current.preview === preview && exportIdle(current, pickerPending()))
                                warning = plainExportEndFrame(current, preview)
                        }, enabled = idle, modifier = Modifier.exportTouch().testTag("plain-export-end")) { Text(stringResource(R.string.plain_export_end)) }
                    }
                }
                if (!state.preparing && preview == null && state.problem == null) item(key = "no-preview") { Text(stringResource(R.string.plain_export_no_preview)) }
            }
        }, confirmButton = {
            if (preview != null) TextButton(plainExportChooseAction(controller, ticket, preview, pickerPending, chooseFile),
                enabled = idle && state.attempts < PlainDeckExportLimits.MAX_DESTINATION_ATTEMPTS,
                modifier = Modifier.exportTouch().testTag("plain-export-choose")) { Text(stringResource(R.string.plain_export_choose)) }
        }, dismissButton = {
            TextButton(plainExportHideAction(controller, ticket), modifier = Modifier.exportTouch().testTag("plain-export-hide")) { Text(stringResource(R.string.plain_export_hide)) }
        })
    warning?.let { frame ->
        AlertDialog(onDismissRequest = { if (controller.state.value.formTicket == ticket && controller.state.value.visible) warning = null },
            title = { Text(stringResource(R.string.plain_export_end_title)) }, text = { Text(stringResource(R.string.plain_export_end_warning)) },
            confirmButton = {
                TextButton(plainExportEndAction(controller, frame, pickerPending), enabled = endCurrent(state, frame, pending),
                    modifier = Modifier.exportTouch().testTag("plain-export-end-confirm")) { Text(stringResource(R.string.plain_export_end_confirm)) }
            }, dismissButton = {
                TextButton({ if (controller.state.value.formTicket == ticket && controller.state.value.visible) warning = null },
                    modifier = Modifier.exportTouch().testTag("plain-export-end-cancel")) { Text(stringResource(R.string.plain_export_end_cancel)) }
            })
    }
}

@Composable
private fun ExportPhase(state: PlainDeckExportState) {
    when (state.writePhase) {
        PlainDeckExportWritePhase.IDLE -> Unit
        PlainDeckExportWritePhase.WRITING -> {
            Text(stringResource(R.string.plain_export_writing), modifier = Modifier.testTag("plain-export-writing"))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        PlainDeckExportWritePhase.CANCELLING -> {
            Text(stringResource(R.string.plain_export_cancelling), modifier = Modifier.testTag("plain-export-cancelling"))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ExportResult(result: PlainDeckWriteResult?) {
    when (result) {
        null -> Unit
        PlainDeckWriteResult.Written -> Text(stringResource(R.string.plain_export_written), modifier = Modifier.testTag("plain-export-written"))
        is PlainDeckWriteResult.NotStarted -> Text(stringResource(R.string.plain_export_not_started), modifier = Modifier.testTag("plain-export-not-started"))
        is PlainDeckWriteResult.PossiblyModified -> Text(stringResource(R.string.plain_export_possibly_modified), modifier = Modifier.testTag("plain-export-possibly-modified"))
    }
}

internal fun plainExportLossKey(loss: PlainDeckListLoss): String {
    val entryId = loss.entryId
    return if (entryId == null) "plain-export-loss-deck-${loss.field.name}"
        else "plain-export-loss-entry-${entryId.value}-${loss.field.name}"
}

internal fun plainExportLossLabel(field: PlainDeckListLossField): Int = when (field) {
    PlainDeckListLossField.DECK_ID -> R.string.plain_export_loss_deck_id
    PlainDeckListLossField.DECK_NAME -> R.string.plain_export_loss_deck_name
    PlainDeckListLossField.FORMAT -> R.string.plain_export_loss_format
    PlainDeckListLossField.DESCRIPTION -> R.string.plain_export_loss_description
    PlainDeckListLossField.FOLDER -> R.string.plain_export_loss_folder
    PlainDeckListLossField.TAGS -> R.string.plain_export_loss_tags
    PlainDeckListLossField.CREATED_AT -> R.string.plain_export_loss_created_at
    PlainDeckListLossField.MODIFIED_AT -> R.string.plain_export_loss_modified_at
    PlainDeckListLossField.REVISION -> R.string.plain_export_loss_revision
    PlainDeckListLossField.BUDGET -> R.string.plain_export_loss_budget
    PlainDeckListLossField.PROFILE_ID -> R.string.plain_export_loss_profile_id
    PlainDeckListLossField.CATALOG_REFERENCES -> R.string.plain_export_loss_catalog_references
    PlainDeckListLossField.PACKAGE_INSTANCES -> R.string.plain_export_loss_package_instances
    PlainDeckListLossField.PRIMER -> R.string.plain_export_loss_primer
    PlainDeckListLossField.CATEGORY_ORDER -> R.string.plain_export_loss_category_order
    PlainDeckListLossField.ENTRY_ORDER -> R.string.plain_export_loss_entry_order
    PlainDeckListLossField.ENTRY_ID -> R.string.plain_export_loss_entry_id
    PlainDeckListLossField.ORACLE_ID -> R.string.plain_export_loss_oracle_id
    PlainDeckListLossField.PRINTING_ID -> R.string.plain_export_loss_printing_id
    PlainDeckListLossField.RAW_IDENTITY -> R.string.plain_export_loss_raw_identity
    PlainDeckListLossField.LANGUAGE -> R.string.plain_export_loss_language
    PlainDeckListLossField.FINISH -> R.string.plain_export_loss_finish
    PlainDeckListLossField.NOTES -> R.string.plain_export_loss_notes
    PlainDeckListLossField.LABELS -> R.string.plain_export_loss_labels
    PlainDeckListLossField.ROLE_OVERRIDES -> R.string.plain_export_loss_role_overrides
}

private fun Modifier.exportTouch(): Modifier = this.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
