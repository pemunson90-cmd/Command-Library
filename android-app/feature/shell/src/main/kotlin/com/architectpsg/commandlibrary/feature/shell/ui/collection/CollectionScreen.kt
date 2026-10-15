package com.architectpsg.commandlibrary.feature.shell.ui.collection

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.*

internal object CollectionTags {
    const val INVENTORY = "collection-inventory"
    const val ALLOCATION = "collection-allocation"
    const val PREVIEW = "collection-preview"
    const val CONFIRM = "collection-confirm"
    const val RETRY = "collection-retry"
    const val QUANTITY = "collection-quantity"
    const val NOTES = "collection-notes"
}

/** Lazy rows, retained controller state, and no writes or generated identifiers from composition. */
@Composable
internal fun CollectionScreen(controller: CollectionController, widthClass: WidthClass, onBrowseCards: (() -> Unit)? = null) {
    val state by controller.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var abandon by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(controller) { controller.ensureLoaded() }
    Column(Modifier.fillMaxSize().padding(horizontal = widthClass.gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(stringResource(R.string.collection_title), style = MaterialTheme.typography.headlineMedium)
        if (widthClass == WidthClass.COMPACT) PrimaryTabRow(tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.collection_inventory_tab)) }, modifier = Modifier.heightIn(min = 48.dp))
            Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.collection_deck_tab)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("collection-deck-tab"))
        }
        if (widthClass == WidthClass.COMPACT) {
            if (tab == 0) InventoryPane(controller, state, Modifier.weight(1f), { abandon = true }, onBrowseCards)
            else AllocationPane(controller, state, Modifier.weight(1f), true, { abandon = true })
        } else Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            InventoryPane(controller, state, Modifier.weight(1f), { abandon = true }, onBrowseCards)
            AllocationPane(controller, state, Modifier.weight(1f), false, { abandon = true })
        }
    }
    state.form?.let { CollectionEditDialog(controller, it, state) }
    if (state.discardPrompt) AlertDialog(onDismissRequest = controller::keepForm,
        title = { Text(stringResource(R.string.collection_discard_title)) }, text = { Text(stringResource(R.string.collection_unsaved)) },
        confirmButton = { TextButton(controller::discardForm, modifier = Modifier.touch()) { Text(stringResource(R.string.collection_discard)) } },
        dismissButton = { TextButton(controller::keepForm, modifier = Modifier.touch()) { Text(stringResource(R.string.collection_keep_editing)) } })
    if (abandon) AlertDialog(onDismissRequest = { abandon = false },
        title = { Text(stringResource(R.string.collection_abandon_title)) }, text = { Text(stringResource(R.string.collection_abandon_warning)) },
        confirmButton = { TextButton({ abandon = false; controller.abandonConfirmed() }, enabled = !state.mutationBusy, modifier = Modifier.touch()) { Text(stringResource(R.string.collection_abandon)) } },
        dismissButton = { TextButton({ abandon = false }, modifier = Modifier.touch()) { Text(stringResource(R.string.collection_keep_checking)) } })
}

@Composable
private fun InventoryPane(controller: CollectionController, state: CollectionUiState, modifier: Modifier, abandon: () -> Unit, browseCards: (() -> Unit)?) {
    val snapshot = state.inventory
    val selected = snapshot?.lots?.firstOrNull { it.id == state.selectedLotId }
    LazyColumn(modifier.fillMaxHeight().testTag(CollectionTags.INVENTORY), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            CollectionStatus(controller, state, abandon)
            ActionRow {
                OutlinedButton(controller::refresh, enabled = !state.loading && !state.mutationBusy, modifier = Modifier.touch()) { Text(stringResource(R.string.collection_refresh)) }
                OutlinedButton(controller::undo, enabled = snapshot != null && !state.pending && !state.mutationBusy && state.form == null, modifier = Modifier.touch().testTag("collection-undo")) { Text(stringResource(R.string.collection_undo)) }
            }
            Text(stringResource(R.string.collection_add_entry))
            if (browseCards != null) OutlinedButton(browseCards, modifier = Modifier.touch().testTag("collection-browse-cards")) { Text(stringResource(R.string.collection_browse_cards)) }
            if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.collection_loading)) }
            state.loadProblem?.let { CollectionProblem(it) }
            snapshot?.let { Text(stringResource(R.string.collection_revision, it.revision.value)) }
            if (snapshot?.lots?.isEmpty() == true) Text(stringResource(R.string.collection_empty))
            selected?.let { lot ->
                FeaturePanel {
                    SectionHeading(lotName(lot))
                    PrintingIdentity(lot)
                    DetailField(stringResource(R.string.collection_quantity), lot.quantity.toString())
                    DetailField(stringResource(R.string.collection_condition), stringResource(conditionLabel(lot.condition)))
                    DetailField(stringResource(R.string.collection_finish), stringResource(finishLabel(lot.finish)))
                    Text(stringResource(R.string.collection_language, lot.identity.language.value.take(128)))
                    PagedText(stringResource(R.string.collection_location), lot.location, lot.id.value)
                    PagedText(stringResource(R.string.collection_notes), lot.notes, lot.id.value)
                    ActionRow {
                        OutlinedButton({ controller.beginEdit(lot.id) }, enabled = !state.pending && !state.mutationBusy && state.form == null, modifier = Modifier.touch().testTag("collection-edit")) { Text(stringResource(R.string.collection_edit)) }
                        OutlinedButton({ controller.beginEdit(lot.id, true) }, enabled = !state.pending && !state.mutationBusy && state.form == null, modifier = Modifier.touch().testTag("collection-remove")) { Text(stringResource(R.string.collection_remove)) }
                    }
                }
            }
        }
        if (snapshot != null) items(snapshot.lots, key = { it.id.value }) { lot ->
            OutlinedButton({ controller.selectLot(lot.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { this.selected = lot.id == state.selectedLotId }.testTag("collection-lot-${lot.id.value}")) {
                Text(stringResource(R.string.collection_row, lotName(lot), lot.quantity))
            }
        }
    }
}

@Composable
private fun AllocationPane(controller: CollectionController, state: CollectionUiState, modifier: Modifier, status: Boolean, abandon: () -> Unit) {
    val plan = state.allocation
    LazyColumn(modifier.fillMaxHeight().testTag(CollectionTags.ALLOCATION), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            if (status) CollectionStatus(controller, state, abandon)
            SectionHeading(stringResource(R.string.collection_deck_tab))
            Text(stringResource(R.string.collection_deck_intro))
            Text(stringResource(R.string.collection_limit))
            OutlinedButton(controller::refreshDecks, enabled = !state.loadingDecks, modifier = Modifier.touch()) { Text(stringResource(R.string.collection_decks_refresh)) }
            if (state.loadingDecks) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.decksProblem?.let { CollectionProblem(it) }
            if (!state.loadingDecks && state.decks.isEmpty()) Text(stringResource(R.string.collection_no_decks))
        }
        items(state.decks, key = { "${it.id.source.value}:${it.id.raw}" }) { deck ->
            OutlinedButton({ controller.selectDeck(deck.id) }, enabled = !state.mutationBusy && !state.pending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { selected = deck.id == state.selectedDeckId }.testTag("collection-deck-${deck.id.raw}")) { Text(deck.name.take(512)) }
        }
        item {
            if (state.deckCursor != null) OutlinedButton(controller::nextDeckPage, enabled = !state.loadingDecks, modifier = Modifier.touch()) { Text(stringResource(R.string.collection_next_decks)) }
            SectionHeading(stringResource(R.string.collection_boards))
            BoardRole.entries.forEach { board ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = board in state.boards, enabled = !state.mutationBusy && !state.pending, role = Role.Checkbox,
                    onValueChange = { controller.includeBoard(board, it) }).semantics(mergeDescendants = true) {}) {
                    Checkbox(board in state.boards, null, enabled = !state.mutationBusy && !state.pending)
                    Text(stringResource(boardLabel(board)), modifier = Modifier.padding(top = 12.dp))
                }
            }
            ActionRow {
                OutlinedButton({ controller.ownership(OwnershipPolicy.SAME_ORACLE) }, enabled = !state.pending && !state.mutationBusy,
                    modifier = Modifier.touch().semantics { selected = state.ownership == OwnershipPolicy.SAME_ORACLE }) { Text(stringResource(R.string.collection_same_oracle)) }
                OutlinedButton({ controller.ownership(OwnershipPolicy.EXACT_PRINTING) }, enabled = !state.pending && !state.mutationBusy,
                    modifier = Modifier.touch().semantics { selected = state.ownership == OwnershipPolicy.EXACT_PRINTING }) { Text(stringResource(R.string.collection_exact_printing)) }
            }
            OutlinedButton(controller::calculateAllocation, enabled = state.selectedDeckId != null && !state.pending && !state.mutationBusy && !state.allocating,
                modifier = Modifier.touch().testTag("collection-calculate")) { Text(stringResource(R.string.collection_calculate)) }
            if (state.allocating) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.collection_calculating)) }
            state.allocationProblem?.let { CollectionProblem(it) }
            if (state.selectedDeckId != null && plan == null && !state.allocating) Text(stringResource(R.string.collection_plan_stale))
            if (plan != null) Text(stringResource(R.string.collection_plan_revision, state.selectedDeck?.name?.take(512) ?: plan.deckId.raw.take(512), plan.deckRevision.value, plan.inventoryRevision.value))
        }
        if (plan != null) items(plan.demands, key = { it.entryId.value }) { demand ->
            val entry = state.selectedDeck?.entries?.firstOrNull { it.id == demand.entryId }
            val name = (entry?.identity?.originalName as? Knowledge.Known)?.value?.take(256) ?: demand.entryId.value.take(256)
            Text(stringResource(if (demand.complete) R.string.collection_demand_exact else R.string.collection_demand_uncertain,
                name, demand.requestedQuantity, demand.ownedQuantity, demand.remainingQuantity))
        }
    }
}

@Composable
private fun CollectionStatus(controller: CollectionController, state: CollectionUiState, abandon: () -> Unit) {
    if (state.checkingCatalog) {
        LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.collection_checking_catalog))
        if (!state.mutationBusy) TextButton(controller::discardForm, modifier = Modifier.touch()) { Text(stringResource(R.string.collection_close)) }
    }
    state.formProblem?.let { if (state.form == null) CollectionProblem(it) }
    if (state.mutationBusy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.collection_saving)) }
    else if (state.pending) MessageBlock(null, listOf(stringResource(R.string.collection_pending)), MessageTone.INFO) {
        OutlinedButton(controller::retry, modifier = Modifier.touch().testTag(CollectionTags.RETRY)) { Text(stringResource(R.string.collection_retry)) }
        OutlinedButton(abandon, modifier = Modifier.touch()) { Text(stringResource(R.string.collection_abandon)) }
    }
    state.mutationProblem?.let { CollectionProblem(it) }
    state.savedRevision?.let { MessageBlock(null, listOf(stringResource(R.string.collection_saved, it.value)), MessageTone.INFO) }
    if (state.form == null) CollectionIssues(state.issues)
}

@Composable internal fun CollectionProblem(problem: Problem) = MessageBlock(null, listOf(stringResource(problemText(problem))), MessageTone.ERROR)
@Composable private fun lotName(lot: OwnedLot): String = (lot.identity.originalName as? Knowledge.Known)?.value?.take(256) ?: stringResource(R.string.collection_unknown_name)
@Composable private fun PrintingIdentity(lot: OwnedLot) {
    val printing = lot.identity.printingId
    if (printing is Knowledge.Known) Text(stringResource(R.string.collection_printing, printing.value.source.value.take(512), printing.value.raw.take(512)))
    else Text(stringResource(R.string.collection_unresolved_printing))
    lot.identity.raw?.let { Text(stringResource(R.string.collection_raw_identity, it.provider.value.take(512), it.kind.take(512), it.rawId.take(512))) }
}
@Composable private fun PagedText(label: String, text: String, id: String) {
    var offset by rememberSaveable(id, label) { mutableIntStateOf(0) }
    val start = offset.coerceIn(0, text.length)
    var end = (start + 4096).coerceAtMost(text.length)
    if (end < text.length && end > start && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
    DetailField(label, text.substring(start, end))
    if (start > 0 || end < text.length) ActionRow {
        TextButton({ offset = 0 }, enabled = start > 0, modifier = Modifier.touch()) { Text(stringResource(R.string.collection_text_start)) }
        TextButton({ offset = end }, enabled = end < text.length, modifier = Modifier.touch()) { Text(stringResource(R.string.collection_text_next)) }
    }
}
@Composable internal fun CollectionIssues(issues: SnapshotList<DomainIssue>) {
    if (issues.isEmpty()) return
    var offset by remember { mutableIntStateOf(0) }
    val start = offset.coerceIn(0, (issues.size - 1).coerceAtLeast(0))
    val end = minOf(start + 20, issues.size)
    for (i in start until end) {
        val issue = issues[i]
        MessageBlock(null, listOf(stringResource(R.string.collection_issue, issue.entryId?.value?.take(512) ?: stringResource(R.string.collection_unknown_name), stringResource(issueLabel(issue.code)))), MessageTone.ERROR)
    }
    if (start > 0 || end < issues.size) ActionRow {
        TextButton({ offset = (start - 20).coerceAtLeast(0) }, enabled = start > 0, modifier = Modifier.touch()) { Text(stringResource(R.string.collection_previous)) }
        TextButton({ offset = end }, enabled = end < issues.size, modifier = Modifier.touch()) { Text(stringResource(R.string.collection_next)) }
    }
}
internal fun Modifier.touch(): Modifier = this.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
private fun issueLabel(code: String): Int = when (code) {
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
internal fun boardLabel(board: BoardRole): Int = when (board) {
    BoardRole.MAIN -> R.string.collection_main
    BoardRole.SIDEBOARD -> R.string.collection_sideboard
    BoardRole.MAYBEBOARD -> R.string.collection_maybeboard
    BoardRole.COMMANDER -> R.string.collection_commander
    BoardRole.PARTNER -> R.string.collection_partner
    BoardRole.BACKGROUND -> R.string.collection_background
    BoardRole.COMPANION -> R.string.collection_companion
}
