package com.architectpsg.commandlibrary.feature.shell.ui.decks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.ActionRow
import com.architectpsg.commandlibrary.feature.shell.ui.problemText

/** Explicit-save form; all mutation state belongs to the retained controller, never composition. */
@Composable
internal fun DeckEditorDialog(controller: DeckLibraryController, form: DeckEditorForm, state: DeckLibraryUiState, search: SearchController?) {
    val enabled = !state.pending && !state.mutationBusy
    AlertDialog(onDismissRequest = {}, title = { Text(stringResource(when (form.mode) {
        DeckEditorMode.DETAILS -> R.string.deck_edit_details
        DeckEditorMode.ENTRY -> R.string.deck_edit_entry
        DeckEditorMode.ADD -> R.string.deck_add_card
        DeckEditorMode.REMOVE -> R.string.deck_remove_entry
    })) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.deck_edit_unsaved))
            if (form.mode == DeckEditorMode.DETAILS) {
                OutlinedTextField(form.name, { v -> controller.editorChange { it.copy(name = v) } }, enabled = enabled,
                    singleLine = true, label = { Text(stringResource(R.string.decks_name)) }, modifier = Modifier.fillMaxWidth().testTag("deck-editor-name"))
                OutlinedTextField(form.description, { v -> controller.editorChange { it.copy(description = v) } }, enabled = enabled,
                    label = { Text(stringResource(R.string.decks_description)) }, modifier = Modifier.fillMaxWidth().testTag("deck-editor-description"))
                OutlinedTextField(form.primer, { v -> controller.editorChange { it.copy(primer = v) } }, enabled = enabled,
                    label = { Text(stringResource(R.string.decks_primer)) }, modifier = Modifier.fillMaxWidth().testTag("deck-editor-primer"))
            } else if (form.mode == DeckEditorMode.REMOVE) Text(stringResource(R.string.deck_edit_remove_warning))
            else {
                if (form.mode == DeckEditorMode.ADD && search != null) DeckCardPicker(search, form.base.id.raw,
                    form.card?.name, enabled, onClearSelection = { controller.editorChange { it.copy(card = null) } },
                    onSelect = { card -> controller.editorChange { it.copy(card = card) } })
                OutlinedTextField(form.quantity, { v -> controller.editorChange { it.copy(quantity = v) } }, enabled = enabled, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text(stringResource(R.string.deck_edit_quantity)) },
                    modifier = Modifier.fillMaxWidth().testTag("deck-editor-quantity"))
                var boardMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton({ boardMenu = true }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.deck_edit_board, stringResource(boardLabel(form.board)))) }
                    DropdownMenu(boardMenu, { boardMenu = false }) { BoardRole.entries.forEach { board ->
                        DropdownMenuItem(text = { Text(stringResource(boardLabel(board))) }, onClick = { controller.editorChange { it.copy(board = board) }; boardMenu = false })
                    } }
                }
                var finishMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton({ finishMenu = true }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.deck_edit_finish, stringResource(finishLabel(form.finish)))) }
                    DropdownMenu(finishMenu, { finishMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.deck_finish_any)) }, onClick = { controller.clearFinishRestriction(); finishMenu = false })
                        Finish.entries.forEach { finish -> DropdownMenuItem(text = { Text(stringResource(finishLabel(Knowledge.Known(finish)))) }, onClick = {
                            controller.editorChange { it.copy(finish = Knowledge.Known(finish)) }; finishMenu = false
                        }) }
                    }
                }
                OutlinedTextField(form.notes, { v -> controller.editorChange { it.copy(notes = v) } }, enabled = enabled,
                    label = { Text(stringResource(R.string.decks_entry_notes)) }, modifier = Modifier.fillMaxWidth().testTag("deck-editor-notes"))
                val categories = form.base.categoryOrder ?: SnapshotList.copyOf(form.base.entries.flatMap { it.labels }.distinctBy { it.id })
                if (categories.isNotEmpty()) {
                    var categoryIndex by rememberSaveable(form.base.id.raw, form.entry?.id?.value) { mutableIntStateOf(0) }
                    val index = categoryIndex.coerceIn(0, categories.lastIndex)
                    val category = categories[index]
                    Text(stringResource(R.string.decks_categories))
                    Text(category.name.take(512))
                    Checkbox(checked = form.labels.any { it.id == category.id }, enabled = enabled, onCheckedChange = { checked ->
                        controller.editorChange { current -> current.copy(labels = SnapshotList.copyOf(if (checked) current.labels + category else current.labels.filter { it.id != category.id })) }
                    }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = category.name.take(512) }.testTag("deck-editor-category"))
                    ActionRow {
                        OutlinedButton({ categoryIndex = index - 1 }, enabled = index > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.decks_previous_category)) }
                        OutlinedButton({ categoryIndex = index + 1 }, enabled = index < categories.lastIndex, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.decks_next_category)) }
                    }
                }
            }
            if (state.invalidEdit) Text(stringResource(R.string.deck_edit_invalid), color = MaterialTheme.colorScheme.error)
            state.mutationProblem?.let { Text(stringResource(problemText(it)), color = MaterialTheme.colorScheme.error) }
            if (state.mutationBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.pending) Text(stringResource(R.string.decks_check_pending))
        }
    }, confirmButton = {
        TextButton(if (state.pending) controller::retry else controller::confirmEditor, enabled = !state.mutationBusy,
            modifier = Modifier.heightIn(min = 48.dp).testTag("deck-editor-save")) { Text(stringResource(if (state.pending) R.string.decks_retry else R.string.deck_edit_save)) }
    }, dismissButton = {
        TextButton(if (state.pending) controller::closePendingEditor else controller::discardEditor, enabled = !state.mutationBusy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(if (state.pending) R.string.decks_close else R.string.deck_edit_discard)) }
    })
}

@Composable
internal fun DeckCardPicker(search: SearchController, ownerKey: String, selectedName: String?, enabled: Boolean,
    onClearSelection: () -> Unit, onSelect: (CardRecord) -> Unit, canInteract: () -> Boolean = { true }) {
    val state by search.state.collectAsState()
    var offset by rememberSaveable(ownerKey, search.queryInput) { mutableIntStateOf(0) }
    OutlinedTextField(search.queryInput, { value -> if (canInteract()) { search.onQueryChange(value); onClearSelection() } }, enabled = enabled,
        singleLine = true, label = { Text(stringResource(R.string.deck_edit_search)) }, modifier = Modifier.fillMaxWidth().testTag("deck-editor-search"))
    OutlinedButton({ if (canInteract()) { onClearSelection(); search.submit() } }, enabled = enabled && state.loading == null, modifier = Modifier.heightIn(min = 48.dp).testTag("deck-editor-search-submit")) { Text(stringResource(R.string.deck_edit_search_action)) }
    state.queryProblem?.let { problem -> Text(when (problem) {
        QueryProblem.EMPTY -> stringResource(R.string.search_problem_empty)
        QueryProblem.TOO_LONG -> pluralStringResource(R.plurals.search_problem_too_long, SearchController.MAX_QUERY_CODE_POINTS, SearchController.MAX_QUERY_CODE_POINTS)
    }, color = MaterialTheme.colorScheme.error) }
    if (state.loading != null) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.problem?.let { Text(stringResource(problemText(it)), color = MaterialTheme.colorScheme.error) }
    if (state.stale) Text(stringResource(R.string.deck_edit_stale_cards))
    if (state.searched && state.loading == null && state.results.isEmpty() && state.problem == null) Text(stringResource(R.string.deck_edit_no_cards))
    state.results.drop(offset).take(20).forEach { card ->
        OutlinedButton({ if (canInteract()) onSelect(card) }, enabled = enabled && !state.stale,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("deck-add-result-${card.oracleId.raw}")) { Text(card.name.take(512)) }
    }
    if (offset > 0 || offset + 20 < state.results.size || state.canLoadMore) ActionRow {
        OutlinedButton({ offset = (offset - 20).coerceAtLeast(0) }, enabled = offset > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.deck_edit_previous_cards)) }
        if (offset + 20 < state.results.size) OutlinedButton({ offset += 20 }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.deck_edit_next_cards)) }
        else if (state.canLoadMore) OutlinedButton({ if (canInteract()) search.loadMore() }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.deck_edit_more_cards)) }
    }
    selectedName?.let { Text(stringResource(R.string.deck_edit_card_selected, it.take(512))) }
    Text(stringResource(R.string.deck_edit_no_printing))
}

internal fun finishLabel(value: Knowledge<Finish>): Int = when (value) {
    is Knowledge.Absent -> R.string.deck_finish_any
    is Knowledge.Unknown -> R.string.deck_finish_unknown
    is Knowledge.Known -> when (value.value) {
        Finish.NONFOIL -> R.string.deck_finish_nonfoil
        Finish.FOIL -> R.string.deck_finish_foil
        Finish.ETCHED -> R.string.deck_finish_etched
    }
}
