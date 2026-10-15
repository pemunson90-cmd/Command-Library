package com.architectpsg.commandlibrary.feature.shell.ui.collection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.*

@Composable internal fun CollectionBulkEditDialog(controller: CollectionController, form: CollectionBulkForm, state: CollectionUiState) {
    val inventoryBusy by controller.inventoryWriterBusy.collectAsState()
    val editing = !state.pending && !state.mutationBusy && !state.bulkPreviewing
    val draft = form.draft
    AlertDialog(onDismissRequest = controller::closeBulkForm,
        title = { Text(stringResource(R.string.collection_bulk_edit)) }, text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp).testTag("collection-bulk-dialog-list"),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    if (inventoryBusy && !controller.hasMutationBarrier) Text(stringResource(R.string.collection_other_writer))
                    Text(pluralStringResource(R.plurals.collection_bulk_selected,form.ids.size,form.ids.size))
                    Text(stringResource(R.string.collection_bulk_unsaved))
                    form.ids.indices.filter { form.originals[it] == null }.forEach { Text(stringResource(R.string.collection_bulk_missing_id,form.ids[it].value),color = MaterialTheme.colorScheme.error) }
                    BulkMode(R.string.collection_quantity,draft.quantity is BulkValue.Set,editing) { controller.bulkDraft(draft.copy(quantity = if(it) BulkValue.Set("") else BulkValue.Keep)) }
                    OriginalSummary(form,{it.quantity}) { Text(it.toString()) }
                    (draft.quantity as? BulkValue.Set)?.let { value -> OutlinedTextField(value.value,{ controller.bulkDraft(draft.copy(quantity = BulkValue.Set(it))) }, enabled = editing,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text(stringResource(R.string.collection_quantity)) },modifier = Modifier.fillMaxWidth().testTag("collection-bulk-quantity")) }
                    BulkMode(R.string.collection_condition,draft.condition is BulkValue.Set,editing) { controller.bulkDraft(draft.copy(condition = if(it) BulkValue.Set(CardCondition.UNKNOWN) else BulkValue.Keep)) }
                    OriginalSummary(form,{it.condition}) { Text(stringResource(conditionLabel(it))) }
                    (draft.condition as? BulkValue.Set)?.let { value ->
                        var open by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton({ open = true },enabled = editing,modifier = Modifier.touch().testTag("collection-bulk-condition")) { Text(stringResource(conditionLabel(value.value))) }
                            DropdownMenu(open,{open = false}) { CardCondition.entries.forEach { condition -> DropdownMenuItem(text = { Text(stringResource(conditionLabel(condition))) },onClick = { open = false; controller.bulkDraft(draft.copy(condition = BulkValue.Set(condition))) },modifier = Modifier.touch()) } }
                        }
                    }
                    BulkMode(R.string.collection_location,draft.location is BulkValue.Set,editing) { controller.bulkDraft(draft.copy(location = if(it) BulkValue.Set("") else BulkValue.Keep)) }
                    OriginalSummary(form,{it.location}) { if(it.isEmpty()) Text(stringResource(R.string.collection_bulk_empty_location)) else PagedText(stringResource(R.string.collection_location),it,"bulk-common") }
                    (draft.location as? BulkValue.Set)?.let { value -> OutlinedTextField(value.value,{ controller.bulkDraft(draft.copy(location = BulkValue.Set(it))) },enabled = editing,
                        label = { Text(stringResource(R.string.collection_location)) },modifier = Modifier.fillMaxWidth().testTag("collection-bulk-location")) }
                    BulkMode(R.string.collection_finish,draft.finish is BulkValue.Set,editing) { controller.bulkDraft(draft.copy(finish = if(it) BulkValue.Set(Finish.NONFOIL) else BulkValue.Keep)) }
                    OriginalSummary(form,{it.finish}) { Text(stringResource(finishLabel(it))) }
                    (draft.finish as? BulkValue.Set)?.let { value -> ActionRow { Finish.entries.forEach { finish -> FilterChip(selected = value.value == finish,onClick = { controller.bulkDraft(draft.copy(finish = BulkValue.Set(finish))) },enabled = editing,
                        label = { Text(stringResource(finishLabel(Knowledge.Known(finish)))) },modifier = Modifier.touch().testTag("collection-bulk-finish-${finish.name}")) } } }
                    if (!draft.changes) Text(stringResource(R.string.collection_bulk_all_keep))
                    if (state.inventory?.revision != form.base.revision) {
                        Text(stringResource(R.string.collection_stale_form))
                        OutlinedButton(controller::useRefreshedBulkInventory,enabled = editing,modifier = Modifier.touch().testTag("collection-bulk-rebase")) { Text(stringResource(R.string.collection_use_refreshed)) }
                    }
                    state.bulkProblem?.let { CollectionProblem(it) };state.mutationProblem?.let { CollectionProblem(it) }
                    if (state.bulkPreviewing || state.mutationBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (!state.pending) CollectionIssues(state.issues)
                    if (state.pending) Text(stringResource(R.string.collection_pending))
                    else if (state.bulkPreview != null) {
                        Text(stringResource(R.string.collection_bulk_preview_notice))
                        TextButton(controller::cancelBulkPreview,enabled = editing,modifier = Modifier.touch()) { Text(stringResource(R.string.collection_cancel_preview)) }
                        CollectionIssues(state.bulkPreview.projection?.issues ?: SnapshotList.empty())
                    }
                }
                if (!state.pending) items(state.bulkPreview?.rows ?: SnapshotList.empty(),key = { it.id.value }) { row ->
                    FeaturePanel {
                        Text(stringResource(R.string.collection_bulk_id,row.id.value))
                        BulkLot(R.string.collection_bulk_before,row.before)
                        BulkLot(R.string.collection_bulk_after,row.after)
                        Text(stringResource(R.string.collection_bulk_preserve))
                        row.issues.forEach { Text(stringResource(bulkIssueText(it)),color = MaterialTheme.colorScheme.error) }
                        row.catalogProblem?.let { CollectionProblem(it) };CollectionIssues(row.structuralIssues)
                        TextButton({ controller.excludeBulkLot(row.id) },enabled = editing,modifier = Modifier.touch().testTag("collection-bulk-exclude-${row.id.value}")) { Text(stringResource(R.string.collection_bulk_exclude)) }
                    }
                }
            }
        }, confirmButton = {
            when {
                state.pending -> TextButton(controller::retry,enabled = !state.mutationBusy,modifier = Modifier.touch().testTag("collection-bulk-retry")) { Text(stringResource(R.string.collection_retry)) }
                state.bulkPreview != null -> TextButton(controller::confirmBulkPreview,enabled = !inventoryBusy && editing && !state.bulkPreview.blocked,modifier = Modifier.touch().testTag("collection-bulk-confirm")) { Text(stringResource(R.string.collection_bulk_confirm)) }
                else -> TextButton(controller::previewBulkEdit,enabled = editing && draft.changes && form.ids.isNotEmpty(),modifier = Modifier.touch().testTag("collection-bulk-preview")) { Text(stringResource(R.string.collection_bulk_preview)) }
            }
        }, dismissButton = { TextButton(controller::closeBulkForm,enabled = !state.mutationBusy,modifier = Modifier.touch().testTag("collection-bulk-close")) { Text(stringResource(R.string.collection_close)) } })
}
@Composable private fun BulkMode(label: Int,set: Boolean,enabled: Boolean,change: (Boolean)->Unit) {
    SectionHeading(stringResource(label))
    ActionRow {
        FilterChip(!set,{change(false)},enabled = enabled,label = { Text(stringResource(R.string.collection_bulk_keep)) },modifier = Modifier.touch().testTag("bulk-keep-$label"))
        FilterChip(set,{change(true)},enabled = enabled,label = { Text(stringResource(R.string.collection_bulk_set)) },modifier = Modifier.touch().testTag("bulk-set-$label"))
    }
}
@Composable private fun BulkLot(title: Int,lot: OwnedLot?) {
    SectionHeading(stringResource(title))
    if (lot == null) { Text(stringResource(R.string.collection_bulk_missing));return }
    DetailField(stringResource(R.string.collection_quantity),lot.quantity.toString())
    DetailField(stringResource(R.string.collection_condition),stringResource(conditionLabel(lot.condition)))
    DetailField(stringResource(R.string.collection_finish),stringResource(finishLabel(lot.finish)))
    PagedText(stringResource(R.string.collection_location),lot.location,lot.id.value)
}
private fun bulkIssueText(issue: CollectionBulkIssue): Int = when(issue) {
    CollectionBulkIssue.MISSING -> R.string.collection_bulk_missing
    CollectionBulkIssue.QUANTITY -> R.string.collection_bulk_quantity_issue
    CollectionBulkIssue.PRINTING -> R.string.collection_bulk_printing_issue
    CollectionBulkIssue.CATALOG -> R.string.collection_bulk_catalog_issue
    CollectionBulkIssue.FINISH -> R.string.collection_bulk_finish_issue
}

@Composable private fun <T> OriginalSummary(form: CollectionBulkForm, value: (OwnedLot)->T, show: @Composable (T)->Unit) {
    val originals = form.originals
    if (originals.isEmpty() || originals.any { it == null }) { Text(stringResource(R.string.collection_bulk_original_missing));return }
    val first = value(checkNotNull(originals.first()))
    if (originals.all { value(checkNotNull(it)) == first }) { Text(stringResource(R.string.collection_bulk_common));show(first) }
    else Text(stringResource(R.string.collection_bulk_mixed))
}
