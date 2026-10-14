package com.architectpsg.commandlibrary.feature.shell.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.CounterRole
import com.architectpsg.commandlibrary.core.model.PlayerProfileSnapshot
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.*

internal object ProfileTags {
    const val LIST="profiles-list"
    const val NEW="profiles-new"
    const val NAME="profile-name"
    const val LIFE="profile-life"
    const val STEP="profile-step"
    const val SAVE="profile-save"
    const val CANCEL="profile-cancel"
    const val REFRESH="profiles-refresh"
    const val MORE="profiles-more"
    const val CONFIRM_DELETE="profile-confirm-delete"
    const val CANCEL_DELETE="profile-cancel-delete"
    const val RELOAD="profile-reload"
    const val CONFIRM_RELOAD="profile-confirm-reload"
    const val CANCEL_RELOAD="profile-cancel-reload"
}

/** Full profile CRUD UI; no game port, source installation, or gameplay destination. */
@Composable
internal fun ProfilesScreen(controller:ProfilesController,widthClass:WidthClass,modifier:Modifier=Modifier) {
    val state by controller.state.collectAsState()
    BackHandler(enabled=state.editor!=null || state.pendingDelete!=null) {
        when {
            state.pendingDelete != null -> controller.dismissDelete()
            state.editor?.pendingCounterDelete != null -> controller.dismissCounterDelete()
            state.editor?.counterEditor != null -> controller.closeCounterEditor()
            state.editor?.counterManager == true -> controller.showProfile()
            !state.busy -> controller.closeEditor()
        }
    }
    Box(modifier.fillMaxSize().imePadding(),contentAlignment=Alignment.TopCenter) {
        val editor=state.editor
        if(editor?.counterManager == true) ProfileCountersScreen(state,editor,controller,widthClass)
        else if(editor!=null) ProfileEditorScreen(state,editor,controller,widthClass)
        else LazyColumn(Modifier.widthIn(max=ReadingWidth).fillMaxSize().padding(horizontal=widthClass.gutter).testTag(ProfileTags.LIST),
            contentPadding=PaddingValues(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    SectionHeading(stringResource(R.string.profiles_title),style=MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.profiles_intro))
                    val label=stringResource(R.string.profiles_life)
                    ActionRow {
                        Button(onClick={controller.create(label)},enabled=!state.busy,modifier=Modifier.heightIn(min=48.dp).testTag(ProfileTags.NEW)) { Text(stringResource(R.string.profiles_new)) }
                        OutlinedButton(onClick=controller::refresh,enabled=!state.busy&&!state.loading,modifier=Modifier.heightIn(min=48.dp).testTag(ProfileTags.REFRESH)) { Text(stringResource(R.string.profiles_refresh)) }
                    }
                    ProfileProblem(state.problem)
                    if(state.loading) ProfileProgress(stringResource(R.string.profiles_loading))
                    if(state.busy) ProfileProgress(stringResource(R.string.profiles_working))
                    if(!state.loading&&state.problem==null&&state.items.isEmpty()) Text(stringResource(R.string.profiles_empty))
                }
            }
            items(state.items,key={it.id.value}) { profile -> ProfileRow(profile,controller,state.busy) }
            if(state.nextCursor!=null) item {
                OutlinedButton(onClick=controller::loadMore,enabled=!state.loading&&!state.busy,modifier=Modifier.heightIn(min=48.dp).testTag(ProfileTags.MORE)) { Text(stringResource(R.string.profiles_more)) }
            }
        }
    }
    state.pendingDelete?.let { target ->
        AlertDialog(onDismissRequest=controller::dismissDelete,
            title={Text(stringResource(R.string.profiles_delete_title))},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(state.busy)stringResource(R.string.profiles_delete_running)else stringResource(R.string.profiles_delete_body,target.value.name),maxLines=6,overflow=TextOverflow.Ellipsis)
                ProfileProblem(state.problem)
                if(state.busy)ProfileProgress(stringResource(R.string.profiles_working))
            }},
            confirmButton={TextButton(onClick=controller::confirmDelete,enabled=!state.busy,modifier=Modifier.heightIn(min=48.dp).testTag(ProfileTags.CONFIRM_DELETE)) {Text(stringResource(R.string.profiles_delete))}},
            dismissButton={TextButton(onClick=controller::dismissDelete,modifier=Modifier.heightIn(min=48.dp).testTag(ProfileTags.CANCEL_DELETE)) {Text(stringResource(if(state.busy)R.string.profiles_close else R.string.profiles_cancel))}})
    }
}

@Composable
private fun ProfileRow(profile:PlayerProfileSnapshot,controller:ProfilesController,busy:Boolean) {
    FeaturePanel {
        Text(profile.value.name,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
        Text(stringResource(R.string.profiles_values,profile.value.preferredLife.toString(),profile.value.adjustmentStep.toString()))
        val editLabel=stringResource(R.string.profiles_edit_named,profile.value.name)
        val deleteLabel=stringResource(R.string.profiles_delete_named,profile.value.name)
        ActionRow {
            OutlinedButton(onClick={controller.edit(profile)},enabled=!busy,modifier=Modifier.heightIn(min=48.dp).testTag("profile-edit-${profile.id.value}").semantics {contentDescription=editLabel}) {Text(stringResource(R.string.profiles_edit))}
            OutlinedButton(onClick={controller.requestDelete(profile)},enabled=!busy,modifier=Modifier.heightIn(min=48.dp).testTag("profile-delete-${profile.id.value}").semantics {contentDescription=deleteLabel}) {Text(stringResource(R.string.profiles_delete))}
        }
    }
}

@Composable
private fun ProfileEditorScreen(state:ProfilesUiState,editor:ProfileEditor,controller:ProfilesController,widthClass:WidthClass) {
    var reload by remember(editor.original?.id,editor.original?.revision) {mutableStateOf(false)}
    Column(Modifier.widthIn(max=ReadingWidth).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=widthClass.gutter,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        SectionHeading(stringResource(if(editor.original==null)R.string.profiles_new_title else R.string.profiles_edit_title),style=MaterialTheme.typography.headlineMedium)
        ProfileProblem(state.problem)
        ProfileField(editor.name,controller::changeName,stringResource(R.string.profiles_name),ProfileTags.NAME,!state.busy,
            if(state.validation==ProfileValidation.NAME)stringResource(R.string.profiles_invalid_name)else null)
        ProfileField(editor.life,controller::changeLife,stringResource(R.string.profiles_life),ProfileTags.LIFE,!state.busy,
            if(state.validation==ProfileValidation.LIFE)stringResource(R.string.profiles_invalid_life)else null)
        val original=editor.original?.value
        original?.counterDefinitions?.single {it.role==CounterRole.LIFE}?.let { life ->
            if(life.minimum!=null||life.maximum!=null)Text(stringResource(R.string.profiles_life_bounds,(life.minimum?:Int.MIN_VALUE).toString(),(life.maximum?:Int.MAX_VALUE).toString()))
        }
        ProfileField(editor.step,controller::changeStep,stringResource(R.string.profiles_step),ProfileTags.STEP,!state.busy,
            if(state.validation==ProfileValidation.STEP)stringResource(R.string.profiles_invalid_step)else null,KeyboardType.Number)
        OutlinedButton(onClick=controller::showCounters,enabled=!state.busy,
            modifier=Modifier.heightIn(min=48.dp).testTag(ProfileCounterTags.MANAGE)) {
            Text(stringResource(R.string.profile_counters_manage,editor.counterDefinitions.size))
        }
        if(original!=null)Text(stringResource(R.string.profiles_retained,editor.counterDefinitions.size,original.commanders.size))
        if(state.busy)ProfileProgress(stringResource(R.string.profiles_working))
        ActionRow {
            Button(onClick=controller::save,enabled=!state.busy,modifier=Modifier.heightIn(min=48.dp).testTag(ProfileTags.SAVE)) {Text(stringResource(R.string.profiles_save))}
            OutlinedButton(onClick=controller::closeEditor,enabled=!state.busy,modifier=Modifier.heightIn(min=48.dp).testTag(ProfileTags.CANCEL)) {Text(stringResource(R.string.profiles_cancel))}
            if(original!=null)TextButton(onClick={reload=true},enabled=!state.busy,modifier=Modifier.heightIn(min=48.dp).testTag(ProfileTags.RELOAD)) {Text(stringResource(R.string.profiles_reload))}
        }
    }
    if(reload)AlertDialog(onDismissRequest={reload=false},title={Text(stringResource(R.string.profiles_reload_title))},text={Text(stringResource(R.string.profiles_reload_body))},
        confirmButton={TextButton(onClick={reload=false;controller.reloadEditor()},modifier=Modifier.heightIn(min=48.dp).testTag(ProfileTags.CONFIRM_RELOAD)) {Text(stringResource(R.string.profiles_reload_confirm))}},
        dismissButton={TextButton(onClick={reload=false},modifier=Modifier.heightIn(min=48.dp).testTag(ProfileTags.CANCEL_RELOAD)) {Text(stringResource(R.string.profiles_cancel))}})
}

@Composable
internal fun ProfileField(value:String,onChange:(String)->Unit,label:String,tag:String,enabled:Boolean,error:String?,keyboard:KeyboardType=KeyboardType.Text) {
    val support:(@Composable ()->Unit)?=error?.let { message -> {Text(message)} }
    OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},singleLine=true,enabled=enabled,isError=error!=null,
        supportingText=support,keyboardOptions=KeyboardOptions(keyboardType=keyboard),modifier=Modifier.fillMaxWidth().testTag(tag))
}
@Composable
internal fun ProfileProblem(problem:Problem?) {
    problem?.let {MessageBlock(title=stringResource(R.string.profiles_notice),body=listOf(stringResource(problemText(it))),tone=MessageTone.ERROR)}
}
@Composable
private fun ProfileProgress(message:String) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=3.dp);Text(message)
    }
}
