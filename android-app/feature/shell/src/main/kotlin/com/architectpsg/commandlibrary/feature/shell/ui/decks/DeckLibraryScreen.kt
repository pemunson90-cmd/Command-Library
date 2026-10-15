package com.architectpsg.commandlibrary.feature.shell.ui.decks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.*

internal object DeckLibraryTags {
    const val LIST = "deck-library-list"
    const val NEW = "deck-library-new"
    const val NAME = "deck-library-name"
    const val CONFIRM = "deck-library-confirm"
    const val DUPLICATE = "deck-library-duplicate"
    const val OVERVIEW = "deck-library-overview"
    const val RETRY = "deck-library-retry"
    const val NEXT = "deck-library-next"
}

/** Real library with optional complete version navigation and whole-snapshot undo. */
@Composable
internal fun DeckLibraryScreen(controller: DeckLibraryController, widthClass: WidthClass, modifier: Modifier = Modifier, editorSearch: SearchController? = null, onOpenVersions: ((DeckId) -> Unit)? = null) {
    val state by controller.state.collectAsState()
    val selected = state.selected
    var abandon by rememberSaveable { mutableStateOf(false) }
    var entryOffset by rememberSaveable(state.selectedId?.raw, selected?.revision?.value) { mutableIntStateOf(0) }
    var descriptionOffset by rememberSaveable(state.selectedId?.raw, selected?.revision?.value) { mutableIntStateOf(0) }
    var primerOffset by rememberSaveable(state.selectedId?.raw, selected?.revision?.value) { mutableIntStateOf(0) }
    val compact = widthClass == WidthClass.COMPACT || LocalDensity.current.fontScale >= 1.5f
    BackHandler(state.form != null || (compact && state.selectedId != null)) {
        if (state.form != null) controller.dismissForm() else controller.clearSelection()
    }
    val status: @Composable () -> Unit = { LibraryStatus(state, controller) { abandon = true } }
    val list: @Composable (Modifier) -> Unit = { pane ->
        LazyColumn(pane.testTag(DeckLibraryTags.LIST), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionHeading(stringResource(R.string.decks_title), style = MaterialTheme.typography.headlineMedium)
                ActionRow {
                    Button(controller::create, enabled = !state.pending && !state.mutationBusy,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(DeckLibraryTags.NEW)) { Text(stringResource(R.string.decks_new)) }
                    OutlinedButton(controller::refresh, enabled = !state.loading, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.decks_refresh)) }
                }
            }
            item { status() }
            state.listProblem?.let { problem -> item { LibraryProblem(problem) } }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.decks_loading)) }
            if (!state.loading && state.items.isEmpty() && state.listProblem == null) item { Text(stringResource(R.string.decks_empty)) }
            items(state.items, key = { it.id.raw }) { row ->
                OutlinedButton(onClick = { controller.select(row.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("deck-open-${row.id.raw}")) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(row.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(countText(row.entryCount, row.totalQuantity))
                    }
                }
            }
            if (state.nextCursor != null) item {
                OutlinedButton(controller::nextPage, enabled = !state.loading, modifier = Modifier.heightIn(min = 48.dp).testTag(DeckLibraryTags.NEXT)) { Text(stringResource(R.string.decks_next_page)) }
            }
        }
    }
    val detail: @Composable (Modifier) -> Unit = { pane ->
        LazyColumn(pane.testTag(DeckLibraryTags.OVERVIEW), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                if (compact) OutlinedButton(controller::clearSelection, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.decks_back)) }
                SectionHeading(selected?.name ?: stringResource(R.string.decks_overview), style = MaterialTheme.typography.headlineMedium)
            }
            if (compact) item { status() }
            state.detailProblem?.let { problem -> item { LibraryProblem(problem) } }
            if (state.loadingDetail) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.decks_loading)) }
            if (selected != null) {
                item {
                    Text(countText(selected.entries.size, selected.entries.sumOf { it.quantity.toLong() }))
                    OutlinedButton(controller::duplicate, enabled = !state.pending && !state.mutationBusy,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(DeckLibraryTags.DUPLICATE)) { Text(stringResource(R.string.decks_duplicate)) }
                    if (controller.supportsEditing && selected.packageInstances.isEmpty()) ActionRow {
                        OutlinedButton(controller::editDetails, enabled = !state.pending && !state.mutationBusy, modifier = Modifier.heightIn(min = 48.dp).testTag("deck-edit-details")) { Text(stringResource(R.string.deck_edit_details)) }
                        if (editorSearch != null) OutlinedButton({ editorSearch.reset(); controller.addEntry() }, enabled = !state.pending && !state.mutationBusy, modifier = Modifier.heightIn(min = 48.dp).testTag("deck-add-entry")) { Text(stringResource(R.string.deck_add_card)) }
                    }
                    if (controller.supportsEditing || onOpenVersions != null) ActionRow {
                        if (controller.supportsEditing) OutlinedButton(controller::undo,
                            enabled = !state.pending && !state.mutationBusy && state.editor == null && state.form == null,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("deck-undo")) { Text(stringResource(R.string.deck_undo)) }
                        if (onOpenVersions != null) OutlinedButton({ onOpenVersions(selected.id) },
                            enabled = !state.pending && !state.mutationBusy && state.editor == null && state.form == null,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("deck-open-versions")) { Text(stringResource(R.string.decks_open_versions)) }
                    }
                    if (selected.packageInstances.isNotEmpty()) Text(stringResource(R.string.decks_package_edit_unavailable))
                }
                item {
                    val categories = selected.categoryOrder ?: SnapshotList.copyOf(selected.entries.flatMap { it.labels }.distinctBy { it.id })
                    CategoryReader(stringResource(if (selected.categoryOrder == null) R.string.decks_implicit_categories else R.string.decks_categories),
                        categories, "deck:${selected.id.raw}:${selected.revision.value}", "deck-categories")
                }
                if (selected.description.isNotEmpty()) item { PlainTextPage(stringResource(R.string.decks_description), selected.description, descriptionOffset) { descriptionOffset = it } }
                if (selected.primer.isNotEmpty()) item { PlainTextPage(stringResource(R.string.decks_primer), selected.primer, primerOffset) { primerOffset = it } }
                items(selected.entries.drop(entryOffset).take(20), key = { it.id.value }) { entry ->
                    FeaturePanel {
                        val supplied = (entry.identity.originalName as? Knowledge.Known)?.value
                        val label = supplied ?: entry.identity.raw?.rawId ?: (entry.identity.printingId as? Knowledge.Known)?.value?.raw
                            ?: (entry.identity.oracleId as? Knowledge.Known)?.value?.raw ?: stringResource(R.string.decks_unknown_card)
                        val preview = label.take(512).let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
                        Text(if (preview.length < label.length) "$preview…" else preview, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.decks_entry_quantity, entry.quantity, stringResource(boardLabel(entry.board))))
                        if (controller.supportsEditing && selected.packageInstances.isEmpty()) {
                            val index = selected.entries.indexOfFirst { it.id == entry.id }
                            ActionRow {
                                OutlinedButton({ controller.editEntry(entry.id) }, enabled = !state.pending && !state.mutationBusy, modifier = Modifier.heightIn(min = 48.dp).testTag("deck-edit-${entry.id.value}")) { Text(stringResource(R.string.deck_edit_entry)) }
                                OutlinedButton({ controller.editEntry(entry.id, true) }, enabled = !state.pending && !state.mutationBusy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.deck_remove_entry)) }
                                OutlinedButton({ controller.moveEntry(entry.id, -1) }, enabled = index > 0 && !state.pending && !state.mutationBusy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.deck_move_earlier)) }
                                OutlinedButton({ controller.moveEntry(entry.id, 1) }, enabled = index + 1 < selected.entries.size && !state.pending && !state.mutationBusy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.deck_move_later)) }
                            }
                        }
                        if (entry.labels.isNotEmpty()) CategoryReader(stringResource(R.string.decks_categories), entry.labels,
                            "entry:${selected.id.raw}:${selected.revision.value}:${entry.id.value}", "deck-entry-categories-${entry.id.value}")
                        if (entry.notes.isNotEmpty()) {
                            var notesOffset by rememberSaveable(selected.id.raw, selected.revision.value, entry.id.value) { mutableIntStateOf(0) }
                            PlainTextPage(stringResource(R.string.decks_entry_notes), entry.notes, notesOffset) { notesOffset = it }
                        }
                    }
                }
                if (selected.entries.size > 20) item {
                    ActionRow {
                        OutlinedButton({ entryOffset = (entryOffset - 20).coerceAtLeast(0) }, enabled = entryOffset > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.decks_previous_entries)) }
                        OutlinedButton({ entryOffset += 20 }, enabled = entryOffset + 20 < selected.entries.size, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.decks_next_entries)) }
                    }
                }
            } else if (!state.loadingDetail && state.detailProblem == null) item { Text(stringResource(R.string.decks_select)) }
        }
    }
    Box(modifier.fillMaxSize().imePadding()) {
        if (compact) {
            if (state.selectedId == null) list(Modifier.fillMaxSize()) else detail(Modifier.fillMaxSize())
        } else Row(Modifier.fillMaxSize()) {
            list(Modifier.weight(0.42f).fillMaxHeight())
            detail(Modifier.weight(0.58f).fillMaxHeight())
        }
    }
    state.form?.let { form ->
        AlertDialog(onDismissRequest = controller::dismissForm,
            title = { Text(stringResource(if (form.source == null) R.string.decks_new else R.string.decks_duplicate)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(form.name, controller::name, label = { Text(stringResource(R.string.decks_name)) },
                        enabled = !state.pending && !state.mutationBusy, isError = state.invalidName, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag(DeckLibraryTags.NAME))
                    if (state.invalidName) Text(stringResource(R.string.decks_invalid_name), color = MaterialTheme.colorScheme.error)
                    if (state.pending || state.mutationBusy) Text(stringResource(R.string.decks_check_pending))
                    state.mutationProblem?.let { LibraryProblem(it) }
                }
            },
            confirmButton = {
                if (state.pending) TextButton(controller::retry, enabled = !state.mutationBusy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.decks_retry)) }
                else TextButton(controller::confirm, enabled = !state.mutationBusy, modifier = Modifier.heightIn(min = 48.dp).testTag(DeckLibraryTags.CONFIRM)) { Text(stringResource(R.string.decks_confirm)) }
            },
            dismissButton = { TextButton(controller::dismissForm, enabled = !state.mutationBusy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.decks_close)) } },
        )
    }
    if (controller.draftEditor != null) DurableDeckEditorDialog(controller.draftEditor, editorSearch)
    else state.editor?.let { form -> DeckEditorDialog(controller, form, state, editorSearch) }
    if (abandon) AlertDialog(onDismissRequest = { abandon = false }, title = { Text(stringResource(R.string.decks_stop_checking)) },
        text = { Text(stringResource(R.string.decks_abandon_warning)) },
        confirmButton = { TextButton({ abandon = false; controller.abandonConfirmed() }, enabled = !state.mutationBusy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.decks_stop_checking)) } },
        dismissButton = { TextButton({ abandon = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.decks_keep_checking)) } })
}

@Composable private fun LibraryStatus(state: DeckLibraryUiState, controller: DeckLibraryController, onAbandon: () -> Unit) {
    if (state.mutationBusy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.decks_saving)) }
    else if (state.pending) MessageBlock(stringResource(R.string.decks_check_title), listOf(stringResource(R.string.decks_check_pending)), MessageTone.INFO) {
        OutlinedButton(controller::retry, modifier = Modifier.heightIn(min = 48.dp).testTag(DeckLibraryTags.RETRY)) { Text(stringResource(R.string.decks_retry)) }
        OutlinedButton(onAbandon, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.decks_stop_checking)) }
    }
    else if (state.savedId != null) MessageBlock(null, listOf(stringResource(R.string.decks_saved)), MessageTone.INFO)
    state.mutationProblem?.let { LibraryProblem(it) }
}
@Composable private fun LibraryProblem(problem: Problem) = MessageBlock(null, listOf(stringResource(problemText(problem))), MessageTone.ERROR)

/** One ordered category at a time, including definitions without memberships. */
@Composable private fun CategoryReader(title: String, values: SnapshotList<EntryLabel>, stateKey: String, tag: String) {
    var index by rememberSaveable(stateKey) { mutableIntStateOf(0) }
    val selected = index.coerceIn(0, (values.size - 1).coerceAtLeast(0))
    var offset by rememberSaveable(stateKey, selected) { mutableIntStateOf(0) }
    Column(Modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (values.isEmpty()) DetailField(title, stringResource(R.string.decks_no_categories))
        else {
            PlainTextPage(stringResource(R.string.decks_category_position, title, selected + 1, values.size), values[selected].name, offset) { offset = it }
            if (values.size > 1) ActionRow {
                OutlinedButton({ index = selected - 1 }, enabled = selected > 0, modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-previous")) { Text(stringResource(R.string.decks_previous_category)) }
                OutlinedButton({ index = selected + 1 }, enabled = selected + 1 < values.size, modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-next")) { Text(stringResource(R.string.decks_next_category)) }
            }
        }
    }
}

/** Bounded plain text, no Markdown execution or navigation; next page preserves surrogate pairs. */
@Composable private fun PlainTextPage(title: String, value: String, offset: Int, onOffset: (Int) -> Unit) {
    val start = offset.coerceIn(0, value.length)
    var end = (start + 4096).coerceAtMost(value.length)
    if (end < value.length && end > start && value[end - 1].isHighSurrogate() && value[end].isLowSurrogate()) end--
    FeaturePanel {
        SectionHeading(title)
        Text(value.substring(start, end))
        if (start > 0 || end < value.length) ActionRow {
            OutlinedButton({ onOffset(0) }, enabled = start > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.decks_text_start)) }
            OutlinedButton({ onOffset(end) }, enabled = end < value.length, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.decks_text_next)) }
        }
    }
}
internal fun boardLabel(board: BoardRole): Int = when (board) {
    BoardRole.MAIN -> R.string.decks_board_main
    BoardRole.SIDEBOARD -> R.string.decks_board_side
    BoardRole.MAYBEBOARD -> R.string.decks_board_maybe
    BoardRole.COMMANDER -> R.string.decks_board_commander
    BoardRole.PARTNER -> R.string.decks_board_partner
    BoardRole.BACKGROUND -> R.string.decks_board_background
    BoardRole.COMPANION -> R.string.decks_board_companion
}

@Composable private fun countText(entries: Int, quantity: Long): String = stringResource(R.string.decks_counts,
    pluralStringResource(R.plurals.decks_entry_count, entries, entries),
    pluralStringResource(R.plurals.decks_card_count, if (quantity == 1L) 1 else 2, quantity))
