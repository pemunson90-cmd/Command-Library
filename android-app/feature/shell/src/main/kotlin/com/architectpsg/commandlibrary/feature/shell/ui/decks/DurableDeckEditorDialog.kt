package com.architectpsg.commandlibrary.feature.shell.ui.decks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.ActionRow
import com.architectpsg.commandlibrary.feature.shell.ui.problemText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The retained actor owns every value/request; adaptive composition owns only menu/paging state. */
@Composable
internal fun DurableDeckEditorDialog(editor: DeckDraftEditor, search: SearchController?) {
    val state by editor.state.collectAsState()
    val session = state.session ?: return
    if (!state.visible) return
    val body = state.body
    val inputEnabled = acceptsInput(state)
    AlertDialog(
        onDismissRequest = { sessionCommand(editor,session.ticket) { editor.requestExit(DeferredDeckAction.CloseEditor) } },
        title = { Text(stringResource(when (session.target) {
            DeckEditorTarget.Details -> R.string.deck_edit_details
            is DeckEditorTarget.Entry -> R.string.deck_edit_entry
            DeckEditorTarget.Add -> R.string.deck_add_card
            is DeckEditorTarget.Remove -> R.string.deck_remove_entry
        })) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.phase == DraftPhase.LOADING) Text(stringResource(R.string.deck_draft_loading))
                if (state.recovered) Text(stringResource(R.string.deck_draft_recovered))
                if (state.requestedTarget != null && state.requestedTarget != session.target)
                    Text(stringResource(R.string.deck_draft_other_target))
                when (body) {
                    is DeckDraftBody.Details -> {
                        OutlinedTextField(body.name, { value -> editBody(editor, session.ticket) { if (it is DeckDraftBody.Details) it.copy(name=value) else it } },
                            enabled=inputEnabled, singleLine=true, label={ Text(stringResource(R.string.decks_name)) },
                            modifier=Modifier.fillMaxWidth().testTag("deck-editor-name"))
                        OutlinedTextField(body.description, { value -> editBody(editor, session.ticket) { if (it is DeckDraftBody.Details) it.copy(description=value) else it } },
                            enabled=inputEnabled, label={ Text(stringResource(R.string.decks_description)) },
                            modifier=Modifier.fillMaxWidth().testTag("deck-editor-description"))
                        OutlinedTextField(body.primer, { value -> editBody(editor, session.ticket) { if (it is DeckDraftBody.Details) it.copy(primer=value) else it } },
                            enabled=inputEnabled, label={ Text(stringResource(R.string.decks_primer)) },
                            modifier=Modifier.fillMaxWidth().testTag("deck-editor-primer"))
                    }
                    is DeckDraftBody.Entry, is DeckDraftBody.Add -> DraftEntryFields(editor,state,search)
                    is DeckDraftBody.Remove -> Text(stringResource(R.string.deck_edit_remove_warning))
                    null -> Unit
                }
                if (state.rawDirty) Text(stringResource(R.string.deck_draft_waiting))
                else if (state.durable?.draft != null) Text(stringResource(R.string.deck_draft_raw_saved))
                else if (body != null && state.phase == DraftPhase.IDLE && !state.conflict)
                    Text(stringResource(R.string.deck_draft_ready))
                if (state.conflict) Text(stringResource(R.string.deck_draft_conflict),color=MaterialTheme.colorScheme.error)
                if (state.uncertain) Text(stringResource(R.string.deck_draft_uncertain))
                if (state.explicitlyInvalid) Text(stringResource(R.string.deck_edit_invalid),color=MaterialTheme.colorScheme.error)
                state.rawProblem?.let { Text(stringResource(problemText(it)),color=MaterialTheme.colorScheme.error) }
                state.contentProblem?.let { Text(stringResource(problemText(it)),color=MaterialTheme.colorScheme.error) }
                if (state.phase !in setOf(DraftPhase.CLOSED,DraftPhase.IDLE)) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.durable?.latestPreparation is DeckDraftPreparation.Pending) {
                    Text(stringResource(R.string.deck_draft_pending))
                    OutlinedButton({ sessionCommand(editor,session.ticket) { editor.discardPreparation() } },enabled=state.phase==DraftPhase.IDLE && !state.uncertain,
                        modifier=Modifier.heightIn(min=48.dp).testTag("deck-draft-discard-preparation")) {
                        Text(stringResource(R.string.deck_draft_discard_preparation))
                    }
                }
                OutlinedButton({ sessionCommand(editor,session.ticket) { editor.discardInput() } },enabled=state.phase==DraftPhase.IDLE,
                    modifier=Modifier.heightIn(min=48.dp).testTag("deck-draft-discard-input")) {
                    Text(stringResource(R.string.deck_draft_discard_input))
                }
            }
        },
        confirmButton = {
            val retry = state.hasFrozenCall || state.uncertain
            TextButton({ sessionCommand(editor,session.ticket) { current ->
                if (current.phase==DraftPhase.IDLE && current.exit==null && current.body!=null) {
                    if (current.hasFrozenCall || current.uncertain) editor.retryExact() else editor.confirmExplicit()
                }
            } },
                enabled=state.phase==DraftPhase.IDLE && state.exit==null && body!=null,
                modifier=Modifier.heightIn(min=48.dp).testTag("deck-editor-save")) {
                Text(stringResource(if (retry) R.string.decks_retry else R.string.deck_edit_save))
            }
        },
        dismissButton = {
            TextButton({ sessionCommand(editor,session.ticket) { editor.requestExit(DeferredDeckAction.CloseEditor) } },modifier=Modifier.heightIn(min=48.dp)) {
                Text(stringResource(R.string.deck_draft_close))
            }
        },
    )
}

private inline fun sessionCommand(editor: DeckDraftEditor, ticket: Long, action: (DraftEditorState)->Unit) {
    val current=editor.state.value
    if (current.session?.ticket==ticket && current.visible) action(current)
}

private fun acceptsInput(state: DraftEditorState): Boolean = state.visible && state.exit==null &&
    state.phase !in setOf(DraftPhase.CLOSED,DraftPhase.LOADING,DraftPhase.DISCARDING)

/** Read latest actor state inside callbacks so two fields cannot overwrite each other's newer input. */
private fun editBody(editor: DeckDraftEditor, ticket: Long, structural: Boolean=false, change: (DeckDraftBody)->DeckDraftBody) {
    val current=editor.state.value
    if (current.session?.ticket!=ticket || !acceptsInput(current) || structural && (current.structuralLocked || current.conflict)) return
    current.body?.let { editor.change(change(it)) }
}

@Composable
private fun DraftEntryFields(editor: DeckDraftEditor, state: DraftEditorState, search: SearchController?) {
    val session=state.session ?: return
    val body=state.body ?: return
    val quantity: String; val notes: String; val board: BoardRole
    val finish: Knowledge<Finish>; val labels: SnapshotList<EntryLabel>
    when (body) {
        is DeckDraftBody.Entry -> { quantity=body.quantity;notes=body.notes;board=body.board;finish=body.finish;labels=body.labels }
        is DeckDraftBody.Add -> { quantity=body.quantity;notes=body.notes;board=body.board;finish=body.finish;labels=body.labels }
        else -> return
    }
    val enabled=acceptsInput(state)
    val structural=enabled && !state.structuralLocked && !state.conflict
    if (body is DeckDraftBody.Add && search!=null) {
        DeckCardPicker(search,session.ticket.toString(),(body.selection?.identity?.originalName as? Knowledge.Known)?.value,
            structural,onClearSelection={ editBody(editor,session.ticket,true) { if(it is DeckDraftBody.Add) it.copy(selection=null) else it } },
            onSelect={ card -> sessionCommand(editor,session.ticket) { current ->
                if (acceptsInput(current) && !current.structuralLocked && !current.conflict) editor.provideCurrentCard(card)
            } }, canInteract={ editor.state.value.let { current ->
                current.session?.ticket==session.ticket && acceptsInput(current) && !current.structuralLocked && !current.conflict
            } })
        if (state.recovered && body.selection!=null) Text(stringResource(R.string.deck_draft_selection_evidence))
    }
    OutlinedTextField(quantity,{ value -> editBody(editor,session.ticket) { when(it) {
        is DeckDraftBody.Entry -> it.copy(quantity=value);is DeckDraftBody.Add -> it.copy(quantity=value);else -> it
    } } },enabled=enabled,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
        label={Text(stringResource(R.string.deck_edit_quantity))},modifier=Modifier.fillMaxWidth().testTag("deck-editor-quantity"))
    var boardMenu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({boardMenu=true},enabled=structural,modifier=Modifier.heightIn(min=48.dp)) {
            Text(stringResource(R.string.deck_edit_board,stringResource(boardLabel(board))))
        }
        DropdownMenu(boardMenu,{boardMenu=false}) { BoardRole.entries.forEach { selected ->
            DropdownMenuItem(text={Text(stringResource(boardLabel(selected)))},onClick={
                editBody(editor,session.ticket,true) { when(it) {
                    is DeckDraftBody.Entry -> it.copy(board=selected);is DeckDraftBody.Add -> it.copy(board=selected);else -> it
                } };boardMenu=false
            })
        } }
    }
    var finishMenu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({finishMenu=true},enabled=structural,modifier=Modifier.heightIn(min=48.dp)) {
            Text(stringResource(R.string.deck_edit_finish,stringResource(finishLabel(finish))))
        }
        DropdownMenu(finishMenu,{finishMenu=false}) {
            val choices: List<Knowledge<Finish>> = listOf(Knowledge.Absent("No finish restriction"))+Finish.entries.map { Knowledge.Known(it) }
            choices.forEach { selected -> DropdownMenuItem(text={Text(stringResource(finishLabel(selected)))},onClick={
                editBody(editor,session.ticket,true) { when(it) {
                    is DeckDraftBody.Entry -> it.copy(finish=selected);is DeckDraftBody.Add -> it.copy(finish=selected);else -> it
                } };finishMenu=false
            }) }
        }
    }
    OutlinedTextField(notes,{ value -> editBody(editor,session.ticket) { when(it) {
        is DeckDraftBody.Entry -> it.copy(notes=value);is DeckDraftBody.Add -> it.copy(notes=value);else -> it
    } } },enabled=enabled,label={Text(stringResource(R.string.decks_entry_notes))},
        modifier=Modifier.fillMaxWidth().testTag("deck-editor-notes"))
    Text(pluralStringResource(R.plurals.deck_draft_saved_categories,labels.size,labels.size))
    val base=state.base
    val derived by produceState<Pair<DeckSnapshot?,List<EntryLabel>>?>(null,base) {
        value=base?.let { exact -> exact to withContext(Dispatchers.Default) {
            exact.categoryOrder ?: exact.entries.flatMap { it.labels }.distinctBy { it.id }
        } }
    }
    val categories=base?.categoryOrder ?: derived?.takeIf { it.first===base }?.second ?: emptyList()
    if (categories.isNotEmpty()) {
        var offset by rememberSaveable(session.ticket) { mutableIntStateOf(0) }
        val index=offset.coerceIn(0,categories.lastIndex);val category=categories[index]
        Text(stringResource(R.string.decks_categories));Text(category.name.take(512))
        Checkbox(checked=labels.any { it.id==category.id },enabled=structural,onCheckedChange={ checked ->
            editBody(editor,session.ticket,true) { current ->
                val old=when(current) { is DeckDraftBody.Entry -> current.labels;is DeckDraftBody.Add -> current.labels;else -> SnapshotList.empty() }
                val updated=SnapshotList.copyOf(if(checked) old.filter { it.id!=category.id }+category else old.filter { it.id!=category.id })
                when(current) { is DeckDraftBody.Entry -> current.copy(labels=updated);is DeckDraftBody.Add -> current.copy(labels=updated);else -> current }
            }
        },modifier=Modifier.heightIn(min=48.dp).semantics { contentDescription=category.name.take(512) }.testTag("deck-editor-category"))
        ActionRow {
            OutlinedButton({offset=index-1},enabled=index>0,modifier=Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.decks_previous_category))}
            OutlinedButton({offset=index+1},enabled=index<categories.lastIndex,modifier=Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.decks_next_category))}
        }
    }
}

@Composable
internal fun DraftExitDialog(editor: DeckDraftEditor) {
    val state by editor.state.collectAsState()
    val exit=state.exit ?: return
    if (exit.phase==DraftExitPhase.READY) return
    AlertDialog(onDismissRequest={editor.chooseExit(exit.ticket,DraftExitChoice.STAY)},
        title={Text(stringResource(R.string.deck_draft_exit_title))},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if(exit.phase==DraftExitPhase.FLUSHING) R.string.deck_draft_exit_flushing else R.string.deck_draft_exit_problem))
            if(exit.phase==DraftExitPhase.FLUSHING) LinearProgressIndicator(Modifier.fillMaxWidth())
        }},
        confirmButton={TextButton({editor.chooseExit(exit.ticket,DraftExitChoice.STAY)},modifier=Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.deck_draft_stay))}},
        dismissButton={if(exit.phase==DraftExitPhase.NEEDS_CHOICE) Column {
            TextButton({editor.chooseExit(exit.ticket,DraftExitChoice.CONTINUE)},enabled=editor.canContinueExit,modifier=Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.deck_draft_continue))}
            TextButton({editor.chooseExit(exit.ticket,DraftExitChoice.DISCARD_INPUT)},modifier=Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.deck_draft_discard_input))}
        }},
    )
}


@Composable
internal fun DraftRecoveryBanner(editor: DeckDraftEditor, onResume: () -> Unit) {
    val state by editor.state.collectAsState()
    if (state.session==null || state.visible || !editor.hasMutationBarrier) return
    Surface(tonalElevation=2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.deck_draft_hidden_attention),modifier=Modifier.weight(1f))
            TextButton(onResume,modifier=Modifier.heightIn(min=48.dp).testTag("deck-draft-resume")) {
                Text(stringResource(R.string.deck_draft_resume))
            }
        }
    }
}
