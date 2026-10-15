package com.architectpsg.commandlibrary.feature.shell.ui.collection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.ActionRow
import com.architectpsg.commandlibrary.feature.shell.ui.DetailField
import com.architectpsg.commandlibrary.feature.shell.ui.problemText
import kotlinx.coroutines.flow.StateFlow

/** Composition retains display state only. The controller owns input, proof and every request. */
@Composable
internal fun DeckMarkOwnedSurface(controller: DeckMarkOwnedController, inventoryBusy: StateFlow<Boolean>) {
    val state by controller.state.collectAsState()
    val busy by inventoryBusy.collectAsState()
    key(state.formTicket) {
        // Remains composed while hidden, so Resume restores the same list position.
        val list = rememberLazyListState()
        if (state.visible) MarkOwnedDialog(controller, inventoryBusy, state, busy, list)
        else if (state.deckId != null) {
            val ticket = state.formTicket
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.mark_owned_hidden))
                    TextButton({
                        val current = controller.state.value
                        if (current.formTicket == ticket && !current.visible && current.deckId != null) controller.resume()
                    }, modifier = Modifier.touch().testTag("mark-owned-resume")) {
                        Text(stringResource(R.string.mark_owned_resume))
                    }
                }
            }
        }
    }
}

private fun owned(state: DeckMarkOwnedState): Boolean = state.submitting || state.uncertain || state.awaitingFresh
private fun editable(state: DeckMarkOwnedState): Boolean = !state.loading && !owned(state)
private inline fun formCommand(controller: DeckMarkOwnedController, ticket: Long, action: (DeckMarkOwnedState) -> Unit) {
    val current = controller.state.value
    if (current.formTicket == ticket && current.visible) action(current)
}
private inline fun rowCommand(controller: DeckMarkOwnedController, ticket: Long, id: EntityId, identity: CardIdentity,
    action: (DeckMarkOwnedRow) -> Unit) = formCommand(controller, ticket) { current ->
    if (editable(current)) current.rows.firstOrNull { it.entry.id == id && it.entry.identity == identity }?.let(action)
}
private inline fun changeInput(controller: DeckMarkOwnedController, ticket: Long, row: DeckMarkOwnedRow,
    delta: (DeckMarkOwnedInput) -> DeckMarkOwnedInput) = rowCommand(controller, ticket, row.entry.id, row.entry.identity) { latest ->
    controller.change(latest.entry.id, latest.input, delta(latest.input))
}
private fun canConfirm(state: DeckMarkOwnedState, preview: DeckMarkOwnedPreview, busy: Boolean): Boolean =
    !busy && editable(state) && !state.previewing && state.preview === preview && state.issues.isEmpty() && preview.projection.issues.isEmpty()

@Composable
private fun MarkOwnedDialog(controller: DeckMarkOwnedController, busyFlow: StateFlow<Boolean>, state: DeckMarkOwnedState,
    busy: Boolean, list: LazyListState) {
    val ticket = state.formTicket
    var warning by remember(ticket) { mutableStateOf<DeckMarkOwnedRow?>(null) }
    var discard by remember(ticket) { mutableStateOf<SnapshotList<DeckMarkOwnedRow>?>(null) }
    val preview = state.preview
    val editing = editable(state)
    val retry = state.uncertain || state.awaitingFresh
    AlertDialog(onDismissRequest = { formCommand(controller, ticket) { controller.close() } },
        title = { Text(stringResource(R.string.mark_owned_title)) },
        text = {
            LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).testTag("mark-owned-list"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "summary") {
                    Text(stringResource(R.string.mark_owned_explanation))
                    state.deck?.let { Text(it.name, style = MaterialTheme.typography.titleMedium) }
                    val allocation = state.allocation
                    if (allocation == null) Text(stringResource(R.string.mark_owned_unavailable))
                    else if (!allocation.complete) {
                        Text(stringResource(R.string.mark_owned_whole_unknown))
                        Text(stringResource(R.string.mark_owned_incomplete))
                    } else {
                        Text(stringResource(R.string.mark_owned_estimate,
                            allocation.demands.sumOf { it.ownedQuantity.toLong() }, allocation.demands.sumOf { it.remainingQuantity.toLong() }))
                    }
                    state.estimateProblem?.let { MarkOwnedProblem(it) }
                    if (state.uncertain) Text(stringResource(R.string.mark_owned_uncertain))
                    if (state.awaitingFresh) Text(stringResource(R.string.mark_owned_awaiting_fresh))
                    if (state.loading || state.previewing || state.submitting) {
                        Text(stringResource(when {
                            state.loading -> R.string.mark_owned_loading
                            state.previewing -> R.string.mark_owned_previewing
                            else -> R.string.mark_owned_submitting
                        }))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    state.problem?.let { MarkOwnedProblem(it) }
                }
                if (busy && !owned(state)) item(key = "other-writer") {
                    Text(stringResource(R.string.mark_owned_busy), modifier = Modifier.testTag("mark-owned-busy"))
                }
                // Separate lazy items keep every field reachable even at large font scales.
                state.rows.forEach { row ->
                    val id = row.entry.id
                    item(key = "row-${id.value}") {
                        val name = cardName(row.entry.identity)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(row.selected, { selected -> rowCommand(controller, ticket, id, row.entry.identity) {
                                controller.select(id, selected)
                            } }, enabled = editing && (row.sourceProblem == null || row.selected),
                                modifier = Modifier.touch().semantics { contentDescription = name }.testTag("mark-owned-select-${id.value}"))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.mark_owned_select, name))
                                val estimate = row.estimate
                                if (estimate == null || !estimate.complete) {
                                    Text(stringResource(R.string.mark_owned_row_unknown))
                                    if (estimate != null) Text(stringResource(R.string.mark_owned_row_incomplete))
                                }
                                else {
                                    Text(stringResource(R.string.mark_owned_row_estimate, estimate.ownedQuantity, estimate.remainingQuantity))
                                }
                            }
                        }
                        row.sourceProblem?.let {
                            Text(stringResource(R.string.mark_owned_source_changed), color = MaterialTheme.colorScheme.error)
                            MarkOwnedProblem(it)
                            if (row.currentEntry != null) OutlinedButton({ rowCommand(controller, ticket, id, row.entry.identity) { latest ->
                                if (latest.sourceProblem != null && latest.currentEntry != null) warning = latest
                            } }, enabled = editing, modifier = Modifier.touch().testTag("mark-owned-use-current-${id.value}")) {
                                Text(stringResource(R.string.mark_owned_use_current))
                            }
                        }
                        row.problem?.let { MarkOwnedProblem(it) }
                    }
                    if (row.selected || row.sourceProblem != null) {
                        item(key = "quantity-${id.value}") {
                            OutlinedTextField(row.input.quantity, { text -> changeInput(controller, ticket, row) { it.copy(quantity = text) } },
                                enabled = editing, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                label = { Text(stringResource(R.string.mark_owned_quantity)) }, modifier = Modifier.fillMaxWidth().testTag("mark-owned-quantity-${id.value}"))
                        }
                        item(key = "finish-${id.value}") { MarkOwnedFinishes(controller, ticket, row, editing) }
                        item(key = "condition-${id.value}") {
                            var menu by rememberSaveable(ticket, id.value, row.entry.identity) { mutableStateOf(false) }
                            Text(stringResource(R.string.collection_condition))
                            Box {
                                OutlinedButton({ rowCommand(controller, ticket, id, row.entry.identity) { menu = true } }, enabled = editing,
                                    modifier = Modifier.touch().testTag("mark-owned-condition-${id.value}")) { Text(stringResource(conditionLabel(row.input.condition))) }
                                DropdownMenu(menu, { rowCommand(controller, ticket, id, row.entry.identity) { menu = false } }) {
                                    CardCondition.entries.forEach { condition -> DropdownMenuItem(
                                        text = { Text(stringResource(conditionLabel(condition))) },
                                        onClick = { rowCommand(controller, ticket, id, row.entry.identity) { latest ->
                                            controller.change(id, latest.input, latest.input.copy(condition = condition)); menu = false
                                        } },
                                        modifier = Modifier.touch()) }
                                }
                            }
                        }
                        item(key = "location-${id.value}") {
                            OutlinedTextField(row.input.location, { text -> changeInput(controller, ticket, row) { it.copy(location = text) } }, enabled = editing,
                                label = { Text(stringResource(R.string.collection_location)) }, modifier = Modifier.fillMaxWidth().testTag("mark-owned-location-${id.value}"))
                        }
                        item(key = "notes-${id.value}") {
                            OutlinedTextField(row.input.notes, { text -> changeInput(controller, ticket, row) { it.copy(notes = text) } }, enabled = editing,
                                label = { Text(stringResource(R.string.collection_notes)) }, modifier = Modifier.fillMaxWidth().testTag("mark-owned-notes-${id.value}"))
                        }
                    }
                }
                if (preview != null) {
                    item(key = "preview-title") { Text(stringResource(R.string.mark_owned_preview_notice)) }
                    items(preview.projection.changes, key = { "change-${it.lotId.value}" }) { change ->
                        val before = change.before
                        val after = change.after
                        val lot = after ?: before
                        if (lot != null) {
                            Text(stringResource(R.string.mark_owned_preview_change, cardName(lot.identity), before?.quantity ?: 0, after?.quantity ?: 0),
                                modifier = Modifier.testTag("mark-owned-preview-change-${change.lotId.value}"))
                            Text(stringResource(R.string.mark_owned_before))
                            if (before == null) Text(stringResource(R.string.mark_owned_no_lot)) else MarkOwnedLot(before)
                            Text(stringResource(R.string.mark_owned_after))
                            if (after == null) Text(stringResource(R.string.mark_owned_no_lot)) else MarkOwnedLot(after)
                        }
                    }
                    items(preview.projection.merges, key = { "merge-${it.requestedLotId.value}" }) { merge ->
                        val source = preview.patch.edits.filterIsInstance<CollectionEdit.Add>().firstOrNull { it.lot.id == merge.requestedLotId }?.lot
                        val survivor = preview.projection.changes.firstOrNull { it.lotId == merge.survivorLotId }?.after
                            ?: preview.inventory.lots.firstOrNull { it.id == merge.survivorLotId }
                        Text(stringResource(R.string.mark_owned_preview_merge, merge.quantityAdded,
                            source?.let { cardName(it.identity) } ?: stringResource(R.string.collection_unknown_name),
                            survivor?.let { cardName(it.identity) } ?: stringResource(R.string.collection_unknown_name)),
                            modifier = Modifier.testTag("mark-owned-preview-merge-${merge.requestedLotId.value}"))
                    }
                }
                items(state.issues, key = { "issue-${it.entryId?.value}-${it.code}" }) { issue ->
                    Text(stringResource(markOwnedIssueLabel(issue.code)), color = MaterialTheme.colorScheme.error)
                }
                item(key = "actions") {
                    ActionRow {
                        OutlinedButton({ formCommand(controller, ticket) { if (editable(it) && !it.loading) controller.refreshSources() } }, enabled = editing,
                            modifier = Modifier.touch().testTag("mark-owned-refresh")) { Text(stringResource(R.string.mark_owned_refresh)) }
                        OutlinedButton({ formCommand(controller, ticket) { if (editable(it)) discard = it.rows } }, enabled = editing,
                            modifier = Modifier.touch().testTag("mark-owned-discard")) { Text(stringResource(R.string.mark_owned_discard)) }
                    }
                }
            }
        }, confirmButton = {
            when {
                retry -> TextButton({ formCommand(controller, ticket) { if (!it.submitting && (it.uncertain || it.awaitingFresh)) controller.retry() } },
                    enabled = !state.submitting, modifier = Modifier.touch().testTag("mark-owned-retry")) { Text(stringResource(R.string.mark_owned_retry)) }
                preview != null -> TextButton({ formCommand(controller, ticket) { if (canConfirm(it, preview, busyFlow.value)) controller.confirm() } },
                    enabled = canConfirm(state, preview, busy), modifier = Modifier.touch().testTag("mark-owned-confirm")) { Text(stringResource(R.string.mark_owned_confirm)) }
                else -> TextButton({ formCommand(controller, ticket) {
                    if (editable(it) && !it.previewing && !busyFlow.value && it.rows.any { row -> row.selected }) controller.preview()
                } }, enabled = editing && !state.previewing && !busy && state.rows.any { it.selected },
                    modifier = Modifier.touch().testTag("mark-owned-preview")) { Text(stringResource(R.string.mark_owned_preview)) }
            }
        }, dismissButton = {
            TextButton({ formCommand(controller, ticket) { controller.close() } }, modifier = Modifier.touch().testTag("mark-owned-close")) { Text(stringResource(R.string.mark_owned_close)) }
        })

    warning?.let { warned ->
        val target = warned.currentEntry ?: return@let
        val exact = editing && state.rows.firstOrNull { it.entry.id == warned.entry.id } == warned
        AlertDialog(onDismissRequest = { formCommand(controller, ticket) { warning = null } },
            title = { Text(stringResource(R.string.mark_owned_clear_title)) },
            text = { Text(stringResource(R.string.mark_owned_clear_warning, cardName(target.identity), target.identity.language.value)) },
            confirmButton = { TextButton({ formCommand(controller, ticket) { latest ->
                if (editable(latest) && latest.rows.firstOrNull { it.entry.id == warned.entry.id } == warned) {
                    controller.acceptCurrentEntry(warned.entry.id, warned.entry.identity, target, warned.input)
                    warning = null
                }
            } }, enabled = exact, modifier = Modifier.touch().testTag("mark-owned-clear-confirm")) { Text(stringResource(R.string.mark_owned_clear_confirm)) } },
            dismissButton = { TextButton({ formCommand(controller, ticket) { warning = null } }, modifier = Modifier.touch().testTag("mark-owned-clear-cancel")) { Text(stringResource(R.string.mark_owned_cancel)) } })
    }
    discard?.let { warned ->
        AlertDialog(onDismissRequest = { formCommand(controller, ticket) { discard = null } },
            title = { Text(stringResource(R.string.mark_owned_discard_title)) }, text = { Text(stringResource(R.string.mark_owned_discard_warning)) },
            confirmButton = { TextButton({ formCommand(controller, ticket) { latest ->
                if (editable(latest) && latest.rows == warned) { controller.discardInput(); discard = null }
            } }, enabled = editing && state.rows == warned, modifier = Modifier.touch().testTag("mark-owned-discard-confirm")) { Text(stringResource(R.string.mark_owned_discard_confirm)) } },
            dismissButton = { TextButton({ formCommand(controller, ticket) { discard = null } }, modifier = Modifier.touch()) { Text(stringResource(R.string.mark_owned_cancel)) } })
    }
}

@Composable
private fun MarkOwnedFinishes(controller: DeckMarkOwnedController, ticket: Long, row: DeckMarkOwnedRow, editing: Boolean) {
    val id = row.entry.id.value
    Text(stringResource(R.string.mark_owned_finish))
    // Unknown is physical input, never Absent and never a guess from the deck's finish.
    ActionRow {
        FilterChip(row.input.finish is Knowledge.Unknown, { changeInput(controller, ticket, row) { it.copy(finish = DeckMarkOwnedInput().finish) } },
            enabled = editing, label = { Text(stringResource(R.string.mark_owned_finish_unknown)) }, modifier = Modifier.touch().testTag("mark-owned-finish-UNKNOWN-$id"))
        row.supportedFinishes.forEach { finish ->
            FilterChip(row.input.finish == Knowledge.Known(finish), { rowCommand(controller, ticket, row.entry.id, row.entry.identity) { latest ->
                if (finish in latest.supportedFinishes) controller.change(latest.entry.id, latest.input, latest.input.copy(finish = Knowledge.Known(finish)))
            } },
                enabled = editing, label = { Text(stringResource(finishLabel(Knowledge.Known(finish)))) }, modifier = Modifier.touch().testTag("mark-owned-finish-${finish.name}-$id"))
        }
    }
    if (row.input.finish is Knowledge.Known && row.input.finish.value !in row.supportedFinishes)
        Text(stringResource(R.string.mark_owned_finish_retained, stringResource(finishLabel(row.input.finish))))
    OutlinedButton({ rowCommand(controller, ticket, row.entry.id, row.entry.identity) { latest -> if (!latest.loadingFinishes && latest.sourceProblem == null) controller.loadFinishChoices(latest.entry.id) } },
        enabled = editing && !row.loadingFinishes && row.sourceProblem == null, modifier = Modifier.touch().testTag("mark-owned-finish-load-$id")) { Text(stringResource(R.string.mark_owned_finish_lookup)) }
    if (row.loadingFinishes) Text(stringResource(R.string.mark_owned_finish_loading))
    row.finishProblem?.let { MarkOwnedProblem(it) }
}

@Composable private fun cardName(identity: CardIdentity): String = (identity.originalName as? Knowledge.Known)?.value ?: stringResource(R.string.collection_unknown_name)
@Composable private fun MarkOwnedProblem(problem: Problem) { Text(stringResource(problemText(problem)), color = MaterialTheme.colorScheme.error) }
@Composable private fun MarkOwnedLot(lot: OwnedLot) {
    DetailField(stringResource(R.string.collection_finish), stringResource(if (lot.finish is Knowledge.Unknown) R.string.mark_owned_finish_unknown else finishLabel(lot.finish)))
    DetailField(stringResource(R.string.collection_condition), stringResource(conditionLabel(lot.condition)))
    DetailField(stringResource(R.string.collection_location), lot.location)
    DetailField(stringResource(R.string.collection_notes), lot.notes)
}
private fun markOwnedIssueLabel(code: String): Int = when (code) {
    InventoryIssueCodes.DUPLICATE_SELECTION -> R.string.collection_issue_duplicate
    InventoryIssueCodes.LOT_ALREADY_EXISTS -> R.string.collection_issue_exists
    InventoryIssueCodes.LOT_NOT_FOUND -> R.string.collection_issue_missing
    InventoryIssueCodes.STALE_LOT -> R.string.collection_issue_revision
    InventoryIssueCodes.INVALID_ADD_REVISION -> R.string.collection_issue_add_revision
    InventoryIssueCodes.QUANTITY_RANGE -> R.string.collection_issue_quantity
    InventoryIssueCodes.MERGE_TARGET_SELECTED -> R.string.collection_issue_overlap
    InventoryIssueCodes.QUANTITY_OVERFLOW -> R.string.collection_issue_overflow
    else -> R.string.collection_issue_rejected
}
