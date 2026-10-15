package com.architectpsg.commandlibrary.feature.shell.ui.collection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.ActionRow

@Composable
internal fun CollectionEditDialog(controller: CollectionController, form: CollectionForm, state: CollectionUiState) {
    val editing = !state.pending && !state.mutationBusy && !state.previewing && state.preview == null
    AlertDialog(onDismissRequest = controller::requestCloseForm,
        title = { Text(stringResource(when (form.mode) {
            CollectionFormMode.ADD -> R.string.collection_add_title
            CollectionFormMode.EDIT -> R.string.collection_edit_title
            CollectionFormMode.REMOVE -> R.string.collection_remove_title
        })) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.collection_unsaved))
                if (form.mode == CollectionFormMode.REMOVE) Text(stringResource(R.string.collection_remove_warning))
                else {
                    OutlinedTextField(form.quantity, controller::quantity, enabled = editing, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text(stringResource(R.string.collection_quantity)) },
                        modifier = Modifier.fillMaxWidth().testTag(CollectionTags.QUANTITY))
                    if (form.mode == CollectionFormMode.ADD) {
                        Text(stringResource(R.string.collection_finish_choice))
                        ActionRow { form.supportedFinishes.forEach { finish ->
                            FilterChip(selected = form.finish == finish, onClick = { controller.finish(finish) }, enabled = editing,
                                label = { Text(stringResource(finishLabel(Knowledge.Known(finish)))) }, modifier = touch().testTag("collection-finish-${finish.ordinal}"))
                        } }
                        form.finish?.let { Text(stringResource(R.string.collection_selected_finish, stringResource(finishLabel(Knowledge.Known(it))))) }
                    } else Text(stringResource(R.string.collection_preserve_metadata))
                    Text(stringResource(R.string.collection_condition))
                    var conditionOpen by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton({ conditionOpen = true }, enabled = editing, modifier = touch()) { Text(stringResource(conditionLabel(form.condition))) }
                        DropdownMenu(conditionOpen, { conditionOpen = false }) {
                            CardCondition.entries.forEach { condition -> DropdownMenuItem(
                                text = { Text(stringResource(conditionLabel(condition))) },
                                onClick = { controller.condition(condition); conditionOpen = false }, modifier = touch()) }
                        }
                    }
                    OutlinedTextField(form.location, controller::location, enabled = editing, label = { Text(stringResource(R.string.collection_location)) }, modifier = Modifier.fillMaxWidth().testTag("collection-location"))
                    OutlinedTextField(form.notes, controller::notes, enabled = editing, label = { Text(stringResource(R.string.collection_notes)) }, modifier = Modifier.fillMaxWidth().testTag(CollectionTags.NOTES))
                }
                if (state.invalidForm) Text(stringResource(R.string.collection_invalid), color = MaterialTheme.colorScheme.error)
                if (state.inventory?.revision != form.base.revision) {
                    Text(stringResource(R.string.collection_stale_form))
                    OutlinedButton(controller::useRefreshedInventory, enabled = !state.pending && !state.mutationBusy, modifier = touch()) { Text(stringResource(R.string.collection_use_refreshed)) }
                }
                state.formProblem?.let { CollectionProblem(it) }
                state.mutationProblem?.let { CollectionProblem(it) }
                if (state.previewing || state.mutationBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.preview?.takeIf { !state.pending }?.let { preview ->
                    Text(stringResource(R.string.collection_preview_summary, preview.changes.size, preview.merges.size))
                    preview.changes.firstOrNull()?.let { change ->
                        if (change.after == null) Text(stringResource(R.string.collection_preview_removed))
                        else Text(stringResource(R.string.collection_preview_quantity, change.before?.quantity ?: 0, change.after!!.quantity))
                    }
                    if (!state.pending) TextButton(controller::cancelPreview, enabled = !state.mutationBusy, modifier = touch()) { Text(stringResource(R.string.collection_cancel_preview)) }
                }
                CollectionIssues(state.issues)
                if (state.pending) Text(stringResource(R.string.collection_pending))
            }
        },
        confirmButton = {
            when {
                state.pending -> TextButton(controller::retry, enabled = !state.mutationBusy, modifier = touch().testTag(CollectionTags.RETRY)) { Text(stringResource(R.string.collection_retry)) }
                state.preview != null -> TextButton(controller::confirmPreview, enabled = !state.mutationBusy && state.issues.isEmpty(), modifier = touch().testTag(CollectionTags.CONFIRM)) { Text(stringResource(R.string.collection_confirm)) }
                else -> TextButton(controller::previewEdit, enabled = !state.mutationBusy && !state.previewing, modifier = touch().testTag(CollectionTags.PREVIEW)) { Text(stringResource(R.string.collection_preview)) }
            }
        },
        dismissButton = { TextButton(controller::requestCloseForm, enabled = !state.mutationBusy, modifier = touch().testTag("collection-close-form")) { Text(stringResource(R.string.collection_close)) } },
    )
}

internal fun conditionLabel(value: CardCondition): Int = when (value) {
    CardCondition.MINT -> R.string.collection_mint
    CardCondition.NEAR_MINT -> R.string.collection_near_mint
    CardCondition.LIGHT_PLAYED -> R.string.collection_light_played
    CardCondition.MODERATE_PLAYED -> R.string.collection_moderate_played
    CardCondition.HEAVY_PLAYED -> R.string.collection_heavy_played
    CardCondition.DAMAGED -> R.string.collection_damaged
    CardCondition.UNKNOWN -> R.string.collection_unknown_condition
}
internal fun finishLabel(value: Knowledge<Finish>): Int = when (value) {
    is Knowledge.Absent -> R.string.collection_absent_finish
    is Knowledge.Unknown -> R.string.collection_unresolved_finish
    is Knowledge.Known -> when (value.value) {
        Finish.NONFOIL -> R.string.collection_nonfoil
        Finish.FOIL -> R.string.collection_foil
        Finish.ETCHED -> R.string.collection_etched
    }
}
