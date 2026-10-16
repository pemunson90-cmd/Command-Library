package com.architectpsg.commandlibrary.feature.shell.ui.decks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.ManualTagsController
import com.architectpsg.commandlibrary.feature.shell.ui.problemText

@Composable
internal fun ManualTagsDialog(controller: ManualTagsController) {
    val state by controller.state.collectAsState()
    val deck = state.deck ?: return
    val renderedState = state
    val action: (() -> Unit) -> Unit = { controller.rendered(renderedState, it) }
    Dialog(onDismissRequest = { action(controller::close) }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).padding(16.dp)
                .verticalScroll(rememberScrollState()).testTag("manual-tags-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.manual_tags_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.manual_tags_scope))
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.problem?.let { Text(stringResource(problemText(it)), color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(state.query, { value -> action { controller.query(value) } }, label = { Text(stringResource(R.string.manual_tags_search)) },
                    modifier = Modifier.fillMaxWidth().testTag("manual-tags-search"), singleLine = true)
                OutlinedTextField(state.name, { value -> action { controller.name(value) } }, label = { Text(stringResource(R.string.manual_tags_name)) },
                    enabled = !state.busy && !state.conflict && !state.uncertain,
                    modifier = Modifier.fillMaxWidth().testTag("manual-tags-name"), singleLine = true)
                TextButton({ action(controller::putName) }, enabled = state.pool != null && !state.busy && !state.conflict && !state.uncertain,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("manual-tags-put")) {
                    Text(stringResource(if (state.editingId == null) R.string.manual_tags_create else R.string.manual_tags_rename))
                }
                if (state.name.isNotEmpty() || state.editingId != null) TextButton({ action(controller::cancelName) }, enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.manual_tags_cancel_pool_name)) }
                state.visibleTags.forEach { tag ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(selected = tag.id in state.selectedTags, onClick = { action { controller.tag(tag.id) } },
                            enabled = !state.busy, label = { Text(tag.name) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("manual-tag-${tag.id.value}"))
                        TextButton({ action { controller.edit(tag.id) } }, enabled = !state.busy && state.editingId == null && state.name.isEmpty(),
                            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.manual_tags_edit)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton({ action { controller.move(tag.id, -1) } }, enabled = !state.busy && !state.conflict && !state.uncertain,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.manual_tags_up)) }
                        TextButton({ action { controller.move(tag.id, 1) } }, enabled = !state.busy && !state.conflict && !state.uncertain,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.manual_tags_down)) }
                        TextButton({ action { controller.retire(tag.id, true) } }, enabled = !state.busy && !state.conflict && !state.uncertain,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.manual_tags_retire)) }
                    }
                }
                state.draft.filter { it.retired }.forEach { tag ->
                    TextButton({ action { controller.retire(tag.id, false) } }, enabled = !state.busy && !state.conflict && !state.uncertain,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.manual_tags_restore, tag.name)) }
                }
                Button({ action(controller::save) }, enabled = state.dirty && state.name.isEmpty() && !state.busy && !state.conflict && !state.uncertain,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("manual-tags-save")) { Text(stringResource(R.string.manual_tags_save)) }
                TextButton({ action(controller::reload) }, enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("manual-tags-reload")) { Text(stringResource(R.string.manual_tags_reload)) }
                if (state.conflict) {
                    Text(stringResource(R.string.manual_tags_conflict))
                    TextButton({ action(controller::useSavedPool) }, enabled = !state.busy && !state.uncertain,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.manual_tags_use_saved)) }
                }
                if (state.uncertain) Text(stringResource(R.string.manual_tags_uncertain))
                HorizontalDivider()
                Text(stringResource(R.string.manual_tags_entries), style = MaterialTheme.typography.titleMedium)
                deck.entries.forEach { entry ->
                    val name = (entry.identity.originalName as? Knowledge.Known)?.value ?: entry.id.value
                    FilterChip(selected = entry.id in state.selectedEntries, onClick = { action { controller.entry(entry.id) } },
                        enabled = !state.busy, label = { Text(stringResource(R.string.manual_tags_entry, name, entry.quantity)) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("manual-tag-entry-${entry.id.value}"))
                    if (entry.labels.isNotEmpty()) Text(entry.labels.joinToString(" · ") { it.name })
                }
                Text(stringResource(R.string.manual_tags_deck_scope))
                state.categories.forEach { label ->
                    Text(label.name)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton({ action { controller.editCategory(label.id) } }, enabled = !state.busy && state.categoryId == null,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("manual-category-edit-${label.id.value}")) { Text(stringResource(R.string.manual_tags_deck_rename)) }
                        TextButton({ action { controller.moveCategory(label.id, -1) } }, enabled = !state.busy && state.categoryId == null,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("manual-category-up-${label.id.value}")) { Text(stringResource(R.string.manual_tags_up)) }
                        TextButton({ action { controller.moveCategory(label.id, 1) } }, enabled = !state.busy && state.categoryId == null,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("manual-category-down-${label.id.value}")) { Text(stringResource(R.string.manual_tags_down)) }
                    }
                    TextButton({ action { controller.removeCategory(label.id) } }, enabled = !state.busy && state.categoryId == null,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("manual-category-remove-${label.id.value}")) { Text(stringResource(R.string.manual_tags_deck_remove)) }
                }
                if (state.categoryId != null) {
                    OutlinedTextField(state.categoryName, { value -> action { controller.categoryName(value) } },
                        label = { Text(stringResource(R.string.manual_tags_deck_name)) }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("manual-category-name"), singleLine = true)
                    Button({ action(controller::saveCategory) }, enabled = !state.busy && !state.dirty && state.name.isEmpty() && !state.conflict && !state.uncertain,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("manual-category-save")) { Text(stringResource(R.string.manual_tags_deck_save)) }
                    TextButton({ action(controller::cancelCategory) }, enabled = !state.busy,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.manual_tags_deck_cancel)) }
                }
                // Deck-local or retired labels remain removable even when absent from the active pool.
                val localLabels = deck.categoryOrder ?: deck.entries.flatMap { it.labels }.distinctBy { it.id }
                localLabels.filter { label -> state.visibleTags.none { it.id == label.id } }.forEach { label ->
                    FilterChip(selected = label.id in state.selectedTags, onClick = { action { controller.tag(label.id) } },
                        enabled = !state.busy, label = { Text(stringResource(R.string.manual_tags_local, label.name)) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
                val ready = state.pool != null && !state.busy && !state.dirty && state.name.isEmpty() && !state.conflict && !state.uncertain &&
                    state.selectedEntries.isNotEmpty() && state.selectedTags.isNotEmpty() && state.categoryId == null
                Text(stringResource(R.string.manual_tags_apply_help))
                Button({ action { controller.assign(false) } }, enabled = ready,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("manual-tags-assign")) { Text(stringResource(R.string.manual_tags_assign)) }
                OutlinedButton({ action { controller.assign(true) } }, enabled = ready,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("manual-tags-remove")) { Text(stringResource(R.string.manual_tags_remove)) }
                TextButton({ action(controller::close) }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.manual_tags_close)) }
                if (state.closeRequested) {
                    Text(stringResource(R.string.manual_tags_discard_warning))
                    TextButton({ action(controller::keepEditing) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.manual_tags_keep)) }
                    TextButton({ action(controller::discardAndClose) }, modifier = Modifier.heightIn(min = 48.dp).testTag("manual-tags-discard")) { Text(stringResource(R.string.manual_tags_discard)) }
                }
            }
        }
    }
}
