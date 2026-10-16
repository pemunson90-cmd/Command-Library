package com.architectpsg.commandlibrary.feature.shell.ui.collection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.document.PlainDeckWriteResult
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.problemText

@Composable
internal fun CollectionTextExportSurface(controller: CollectionTextExportController,
    chooseDestination: (CollectionTextExportPreview) -> Unit, pickerInFlight: Boolean) {
    val state by controller.state.collectAsState()
    if (!state.visible) return
    val frame = state
    val act: (() -> Unit) -> Unit = { controller.rendered(frame, it) }
    Dialog(onDismissRequest = { act(controller::hide) }) {
        Surface(shape = MaterialTheme.shapes.large) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 720.dp).padding(16.dp).testTag("collection-text-export-list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(stringResource(R.string.collection_text_export_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.collection_text_export_scope))
                }
                val preview = state.preview
                if (preview == null) item {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(state.includeNotes, { act { controller.notes(it) } }, enabled = !state.preparing,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("collection-text-export-notes"))
                        Text(stringResource(R.string.collection_text_export_notes))
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(state.includeLocations, { act { controller.locations(it) } }, enabled = !state.preparing,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("collection-text-export-locations"))
                        Text(stringResource(R.string.collection_text_export_locations))
                    }
                    Button({ act(controller::prepare) }, enabled = !state.preparing && !pickerInFlight,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("collection-text-export-prepare")) {
                        Text(stringResource(R.string.collection_text_export_prepare))
                    }
                    if (state.preparing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        TextButton({ act(controller::cancelPreparation) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.plain_export_stop_preparation))
                        }
                    }
                }
                else {
                    item {
                        Text(stringResource(R.string.collection_text_export_capture, preview.source.revision.value,
                            preview.source.lots.size, preview.source.lots.sumOf { it.quantity.toLong() }, preview.artifact.bytes.size),
                            Modifier.testTag("collection-text-export-capture"))
                        preview.artifact.losses.forEach { Text(it) }
                        Text(stringResource(R.string.collection_text_export_sample, minOf(10, preview.source.lots.size)))
                    }
                    preview.source.lots.take(10).forEach { lot -> item {
                        val name = (lot.identity.originalName as? Knowledge.Known)?.value?.take(160)?.trimEnd { it.isHighSurrogate() }
                            ?: stringResource(R.string.collection_text_export_unknown_name)
                        Text(stringResource(R.string.collection_text_export_row, lot.quantity, name))
                    } }
                    item {
                        Button({ act { chooseDestination(preview) } },
                            enabled = state.writePhase == PlainDeckExportWritePhase.IDLE && state.pendingPicker == null && !pickerInFlight,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("collection-text-export-save")) {
                            Text(stringResource(R.string.collection_text_export_save))
                        }
                        if (state.pendingPicker != null) Text(stringResource(if (state.pickerAuthorized)
                            R.string.plain_export_pending else R.string.plain_export_pending_revoked))
                        if (state.writePhase != PlainDeckExportWritePhase.IDLE) {
                            Text(stringResource(if (state.writePhase == PlainDeckExportWritePhase.CANCELLING)
                                R.string.plain_export_cancelling else R.string.collection_text_export_writing))
                            TextButton({ act(controller::cancelWrite) }, enabled = state.writePhase == PlainDeckExportWritePhase.WRITING,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("collection-text-export-cancel-write")) {
                                Text(stringResource(R.string.plain_export_cancel_write))
                            }
                        }
                        state.lastWrite?.let { written -> Text(stringResource(when (written) {
                            PlainDeckWriteResult.Written -> R.string.collection_text_export_written
                            is PlainDeckWriteResult.NotStarted -> R.string.plain_export_not_started
                            is PlainDeckWriteResult.PossiblyModified -> R.string.plain_export_possibly_modified
                        }), Modifier.testTag("collection-text-export-result")) }
                        TextButton({ act { controller.discard(preview) } },
                            enabled = state.writePhase == PlainDeckExportWritePhase.IDLE && state.pendingPicker == null,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("collection-text-export-end")) {
                            Text(stringResource(R.string.collection_text_export_end))
                        }
                    }
                }
                item {
                    state.problem?.let { Text(stringResource(problemText(it)), color = MaterialTheme.colorScheme.error) }
                    TextButton({ act(controller::hide) }, modifier = Modifier.heightIn(min = 48.dp).testTag("collection-text-export-hide")) {
                        Text(stringResource(R.string.plain_export_hide))
                    }
                }
            }
        }
    }
}
