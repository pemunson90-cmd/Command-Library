package com.architectpsg.commandlibrary.feature.shell.ui.profiles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.*

internal object ProfileCommanderTags {
    const val MANAGE = "profile-commanders"
    const val MANAGER = "profile-commanders-list"
    const val BACK = "profile-commanders-back"
    const val ADD = "profile-commander-add"
    const val QUERY = "profile-commander-query"
    const val SEARCH = "profile-commander-search"
    const val RESULTS = "profile-commander-results"
    const val MORE = "profile-commander-more"
    const val CANCEL_PICKER = "profile-commander-picker-cancel"
    const val LABEL = "profile-commander-label"
    const val APPLY = "profile-commander-apply"
    const val CANCEL = "profile-commander-cancel"
    const val CONFIRM_DELETE = "profile-commander-delete-confirm"
    const val CANCEL_DELETE = "profile-commander-delete-cancel"
    fun edit(id: String) = "profile-commander-edit-$id"
    fun delete(id: String) = "profile-commander-delete-$id"
    fun select(source: String, raw: String) = "profile-commander-select-${source.length}:$source${raw.length}:$raw"
}

@Composable
internal fun ProfileCommandersScreen(state: ProfilesUiState, editor: ProfileEditor, controller: ProfilesController, widthClass: WidthClass) {
    val form = editor.commanderEditor
    if (editor.commanderPicker) {
        controller.commanderSearch?.let { CommanderPicker(it, controller, !state.busy, state.problem, widthClass) }
    } else if (form != null) {
        Column(Modifier.widthIn(max = ReadingWidth).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = widthClass.gutter, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionHeading(stringResource(R.string.profile_commander_edit_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.profile_appearance_draft_notice))
            ProfileField(form.label, controller::changeCommanderLabel, stringResource(R.string.profile_commander_label),
                ProfileCommanderTags.LABEL, !state.busy, if (form.invalidLabel) stringResource(R.string.profile_commander_invalid_label) else null)
            ActionRow {
                Button(onClick = controller::applyCommander, enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCommanderTags.APPLY)) { Text(stringResource(R.string.profile_counter_apply)) }
                OutlinedButton(onClick = controller::closeCommanderEditor, enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCommanderTags.CANCEL)) { Text(stringResource(R.string.profiles_cancel)) }
            }
        }
    } else LazyColumn(Modifier.widthIn(max = ReadingWidth).fillMaxSize().padding(horizontal = widthClass.gutter).testTag(ProfileCommanderTags.MANAGER),
        contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading(stringResource(R.string.profile_commanders_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.profile_appearance_draft_notice))
                ProfileProblem(state.problem)
                ActionRow {
                    if (controller.commanderSearch != null) Button(onClick = controller::openCommanderPicker, enabled = !state.busy,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCommanderTags.ADD)) { Text(stringResource(R.string.profile_commander_add)) }
                    OutlinedButton(onClick = controller::showProfile, enabled = !state.busy,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCommanderTags.BACK)) { Text(stringResource(R.string.profile_counter_back)) }
                }
                if (editor.commanders.isEmpty()) Text(stringResource(R.string.profile_commanders_empty))
            }
        }
        items(editor.commanders, key = { it.id.value }) { slot ->
            FeaturePanel {
                Text(slot.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                (slot.identity.originalName as? Knowledge.Known<String>)?.value?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                val edit = stringResource(R.string.profile_commander_edit_named, slot.label)
                val remove = stringResource(R.string.profile_commander_remove_named, slot.label)
                ActionRow {
                    OutlinedButton(onClick = { controller.editCommander(slot.id) }, enabled = !state.busy,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCommanderTags.edit(slot.id.value)).semantics { contentDescription = edit }) { Text(stringResource(R.string.profiles_edit)) }
                    OutlinedButton(onClick = { controller.requestCommanderDelete(slot.id) }, enabled = !state.busy,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCommanderTags.delete(slot.id.value)).semantics { contentDescription = remove }) { Text(stringResource(R.string.profile_commander_remove)) }
                }
            }
        }
    }
    editor.pendingCommanderDelete?.let { slot ->
        AlertDialog(onDismissRequest = controller::dismissCommanderDelete,
            title = { Text(stringResource(R.string.profile_commander_remove_title)) },
            text = { Text(stringResource(R.string.profile_commander_remove_body, slot.label), maxLines = 6, overflow = TextOverflow.Ellipsis) },
            confirmButton = { TextButton(onClick = controller::confirmCommanderDelete, enabled = !state.busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCommanderTags.CONFIRM_DELETE)) { Text(stringResource(R.string.profile_commander_remove)) } },
            dismissButton = { TextButton(onClick = controller::dismissCommanderDelete, enabled = !state.busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCommanderTags.CANCEL_DELETE)) { Text(stringResource(R.string.profiles_cancel)) } })
    }
}

@Composable
private fun CommanderPicker(search: SearchController, profiles: ProfilesController, enabled: Boolean, problem: Problem?, widthClass: WidthClass) {
    val state by search.state.collectAsState()
    LazyColumn(Modifier.widthIn(max = ReadingWidth).fillMaxSize().padding(horizontal = widthClass.gutter).testTag(ProfileCommanderTags.RESULTS),
        contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading(stringResource(R.string.profile_commander_add), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.search_field_help))
                ProfileField(search.queryInput, search::onQueryChange, stringResource(R.string.search_field_label), ProfileCommanderTags.QUERY, enabled,
                    when (state.queryProblem) {
                        QueryProblem.EMPTY -> stringResource(R.string.search_problem_empty)
                        QueryProblem.TOO_LONG -> pluralStringResource(R.plurals.search_problem_too_long, SearchController.MAX_QUERY_CODE_POINTS, SearchController.MAX_QUERY_CODE_POINTS)
                        null -> null
                    })
                ActionRow {
                    Button(onClick = search::submit, enabled = enabled && state.loading == null,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCommanderTags.SEARCH)) { Text(stringResource(R.string.search_action)) }
                    OutlinedButton(onClick = profiles::closeCommanderPicker, enabled = enabled,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCommanderTags.CANCEL_PICKER)) { Text(stringResource(R.string.profiles_cancel)) }
                }
                ProfileProblem(problem); ProfileProblem(state.problem)
                if (state.loading != null) { CircularProgressIndicator(); Text(stringResource(R.string.search_loading)) }
                if (state.stale) {
                    Text(stringResource(R.string.search_stale_body))
                    OutlinedButton(onClick = search::searchAgain, enabled = enabled && state.loading == null,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.search_again)) }
                }
                if (state.failedLoad != null) OutlinedButton(onClick = search::retry, enabled = enabled && state.loading == null,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.shell_action_retry)) }
                if (state.searched && state.loading == null && state.failedLoad == null && state.results.isEmpty()) Text(stringResource(R.string.search_no_results, state.query.orEmpty()))
            }
        }
        items(state.results, key = { ProfileCommanderTags.select(it.oracleId.source.value, it.oracleId.raw) }) { card ->
            val label = stringResource(R.string.profile_commander_select_named, card.name)
            OutlinedButton(onClick = { profiles.addCommander(card) }, enabled = enabled && state.loading == null && !state.stale,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(ProfileCommanderTags.select(card.oracleId.source.value, card.oracleId.raw))
                    .semantics { contentDescription = label }) { Text(label) }
        }
        if (state.nextCursor != null && !state.stale) item {
            OutlinedButton(onClick = search::loadMore, enabled = enabled && state.canLoadMore,
                modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCommanderTags.MORE)) { Text(stringResource(R.string.search_load_more)) }
        }
    }
}
