package com.architectpsg.commandlibrary.feature.shell.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.*

internal object ProfileCounterTags {
    const val MANAGE = "profile-counters"
    const val MANAGER = "profile-counters-list"
    const val BACK = "profile-counters-back"
    const val ADD = "profile-counter-add"
    const val EDITOR = "profile-counter-editor"
    const val LABEL = "profile-counter-label"
    const val INITIAL = "profile-counter-initial"
    const val MINIMUM = "profile-counter-minimum"
    const val MAXIMUM = "profile-counter-maximum"
    const val LOSS_NONE = "profile-counter-loss-none"
    const val LOSS_ABOVE = "profile-counter-loss-above"
    const val LOSS_BELOW = "profile-counter-loss-below"
    const val THRESHOLD = "profile-counter-threshold"
    const val USE_COLOR = "profile-counter-use-color"
    const val COLOR = "profile-counter-color"
    const val RESET_TURN = "profile-counter-reset-turn"
    const val APPLY = "profile-counter-apply"
    const val CANCEL = "profile-counter-cancel"
    const val CONFIRM_DELETE = "profile-counter-delete-confirm"
    const val CANCEL_DELETE = "profile-counter-delete-cancel"
    fun edit(id: String): String = "profile-counter-edit-$id"
    fun delete(id: String): String = "profile-counter-delete-$id"
}

@Composable
internal fun ProfileCountersScreen(state: ProfilesUiState, editor: ProfileEditor, controller: ProfilesController, widthClass: WidthClass) {
    val counter = editor.counterEditor
    if (counter != null) CounterEditorScreen(counter, controller, !state.busy, widthClass)
    else LazyColumn(Modifier.widthIn(max = ReadingWidth).fillMaxSize().padding(horizontal = widthClass.gutter)
        .testTag(ProfileCounterTags.MANAGER), contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading(stringResource(R.string.profile_counters_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.profile_counter_draft_notice))
                ActionRow {
                    Button(onClick = controller::addCounter, enabled = !state.busy && editor.counterDefinitions.size < MaxProfileCounterDefinitions,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCounterTags.ADD)) { Text(stringResource(R.string.profile_counter_add)) }
                    OutlinedButton(onClick = controller::showProfile, enabled = !state.busy,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCounterTags.BACK)) { Text(stringResource(R.string.profile_counter_back)) }
                }
                if (editor.counterDefinitions.size >= MaxProfileCounterDefinitions) Text(stringResource(R.string.profile_counter_limit, MaxProfileCounterDefinitions))
                ProfileProblem(state.problem)
            }
        }
        items(editor.counterDefinitions, key = { it.id.value }) { value ->
            CounterRow(value, if (value.role == CounterRole.LIFE) editor.life else value.initial.toString(), controller, !state.busy)
        }
    }
    editor.pendingCounterDelete?.let { value ->
        AlertDialog(onDismissRequest = controller::dismissCounterDelete,
            title = { Text(stringResource(R.string.profile_counter_delete_title)) },
            text = { Text(stringResource(R.string.profile_counter_delete_body, value.label), maxLines = 6, overflow = TextOverflow.Ellipsis) },
            confirmButton = { TextButton(onClick = controller::confirmCounterDelete, enabled = !state.busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCounterTags.CONFIRM_DELETE)) { Text(stringResource(R.string.profiles_delete)) } },
            dismissButton = { TextButton(onClick = controller::dismissCounterDelete, enabled = !state.busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCounterTags.CANCEL_DELETE)) { Text(stringResource(R.string.profiles_cancel)) } })
    }
}

@Composable
private fun CounterRow(value: CounterDefinition, initial: String, controller: ProfilesController, enabled: Boolean) {
    FeaturePanel {
        Text(value.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val unset = stringResource(R.string.profile_counter_unbounded)
        Text(stringResource(R.string.profile_counter_values, initial, value.minimum?.toString() ?: unset, value.maximum?.toString() ?: unset))
        if (value.editableInProfile) {
            val edit = stringResource(R.string.profile_counter_edit_named, value.label)
            val delete = stringResource(R.string.profile_counter_delete_named, value.label)
            ActionRow {
                OutlinedButton(onClick = { controller.editCounter(value.id) }, enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCounterTags.edit(value.id.value)).semantics { contentDescription = edit }) { Text(stringResource(R.string.profiles_edit)) }
                OutlinedButton(onClick = { controller.requestCounterDelete(value.id) }, enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCounterTags.delete(value.id.value)).semantics { contentDescription = delete }) { Text(stringResource(R.string.profiles_delete)) }
            }
        } else Text(stringResource(R.string.profile_counter_kept))
    }
}

@Composable
private fun CounterEditorScreen(counter: ProfileCounterEditor, controller: ProfilesController, enabled: Boolean, widthClass: WidthClass) {
    Column(Modifier.widthIn(max = ReadingWidth).fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = widthClass.gutter, vertical = 16.dp).testTag(ProfileCounterTags.EDITOR),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading(stringResource(if (counter.original == null) R.string.profile_counter_new_title else R.string.profile_counter_edit_title),
            style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.profile_counter_draft_notice))
        ProfileField(counter.label, controller::changeCounterLabel, stringResource(R.string.profile_counter_label), ProfileCounterTags.LABEL, enabled,
            counter.error(ProfileCounterValidation.LABEL, R.string.profile_counter_invalid_label))
        ProfileField(counter.initial, controller::changeCounterInitial, stringResource(R.string.profile_counter_initial), ProfileCounterTags.INITIAL, enabled,
            counter.error(ProfileCounterValidation.INITIAL, R.string.profile_counter_invalid_initial))
        ProfileField(counter.minimum, controller::changeCounterMinimum, stringResource(R.string.profile_counter_minimum), ProfileCounterTags.MINIMUM, enabled,
            counter.error(ProfileCounterValidation.MINIMUM, R.string.profile_counter_invalid_integer)
                ?: counter.error(ProfileCounterValidation.BOUNDS, R.string.profile_counter_invalid_bounds))
        ProfileField(counter.maximum, controller::changeCounterMaximum, stringResource(R.string.profile_counter_maximum), ProfileCounterTags.MAXIMUM, enabled,
            counter.error(ProfileCounterValidation.MAXIMUM, R.string.profile_counter_invalid_integer))
        Text(stringResource(R.string.profile_counter_loss_title), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CounterLossChoice(counter.loss == ProfileCounterLoss.NONE, { controller.changeCounterLoss(ProfileCounterLoss.NONE) },
                stringResource(R.string.profile_counter_loss_none), ProfileCounterTags.LOSS_NONE, enabled)
            CounterLossChoice(counter.loss == ProfileCounterLoss.ABOVE, { controller.changeCounterLoss(ProfileCounterLoss.ABOVE) },
                stringResource(R.string.profile_counter_loss_above), ProfileCounterTags.LOSS_ABOVE, enabled)
            CounterLossChoice(counter.loss == ProfileCounterLoss.BELOW, { controller.changeCounterLoss(ProfileCounterLoss.BELOW) },
                stringResource(R.string.profile_counter_loss_below), ProfileCounterTags.LOSS_BELOW, enabled)
        }
        if (counter.loss != ProfileCounterLoss.NONE) ProfileField(counter.threshold, controller::changeCounterThreshold,
            stringResource(R.string.profile_counter_threshold), ProfileCounterTags.THRESHOLD, enabled,
            counter.error(ProfileCounterValidation.THRESHOLD, R.string.profile_counter_invalid_integer))
        CounterToggle(counter.useColor, controller::changeCounterUseColor, stringResource(R.string.profile_counter_use_color), ProfileCounterTags.USE_COLOR, enabled)
        if (counter.useColor) {
            ProfileField(counter.color, controller::changeCounterColor, stringResource(R.string.profile_counter_color), ProfileCounterTags.COLOR, enabled,
                counter.error(ProfileCounterValidation.COLOR, R.string.profile_counter_invalid_color), KeyboardType.Ascii)
            if (Regex("#[0-9a-fA-F]{8}").matches(counter.color)) {
                val description = stringResource(R.string.profile_counter_color_preview)
                Box(Modifier.size(32.dp).background(Color(counter.color.substring(1).toLong(16).toInt()))
                    .border(1.dp, MaterialTheme.colorScheme.outline).semantics { contentDescription = description })
            }
        } else Text(stringResource(R.string.profile_counter_color_unset))
        CounterToggle(counter.reset == CounterResetPolicy.ON_TURN_START,
            { controller.changeCounterReset(if (it) CounterResetPolicy.ON_TURN_START else CounterResetPolicy.MANUAL) },
            stringResource(R.string.profile_counter_reset_turn), ProfileCounterTags.RESET_TURN, enabled)
        ActionRow {
            Button(onClick = controller::applyCounter, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCounterTags.APPLY)) { Text(stringResource(R.string.profile_counter_apply)) }
            OutlinedButton(onClick = controller::closeCounterEditor, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileCounterTags.CANCEL)) { Text(stringResource(R.string.profiles_cancel)) }
        }
    }
}

@Composable
private fun ProfileCounterEditor.error(field: ProfileCounterValidation, message: Int): String? =
    if (validation == field) stringResource(message) else null

@Composable
private fun CounterLossChoice(selected: Boolean, onClick: () -> Unit, label: String, tag: String, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected, onClick = null, enabled = enabled); Text(label, Modifier.weight(1f))
    }
}

@Composable
private fun CounterToggle(value: Boolean, onChange: (Boolean) -> Unit, label: String, tag: String, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag).toggleable(value, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Checkbox(value, onCheckedChange = null, enabled = enabled); Text(label, Modifier.weight(1f))
    }
}
