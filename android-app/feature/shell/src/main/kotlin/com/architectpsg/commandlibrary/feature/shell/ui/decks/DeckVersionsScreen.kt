package com.architectpsg.commandlibrary.feature.shell.ui.decks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.*

internal object DeckVersionsTags {
    const val LIST = "deck-versions-list"
    const val DETAIL = "deck-versions-detail"
    const val LABEL = "deck-versions-label"
    const val CAPTURE = "deck-versions-capture"
    const val PREVIEW = "deck-versions-preview"
    const val RECOVERY_LABEL = "deck-versions-recovery-label"
    const val RESTORE = "deck-versions-restore"
    const val RETRY = "deck-versions-retry"
}

@Composable
internal fun DeckVersionsScreen(controller: DeckVersionsController, widthClass: WidthClass, onClose: (() -> Unit)? = null, draftEditor: DeckDraftEditor? = null) {
    val state by controller.state.collectAsState()
    val draftState = draftEditor?.state?.collectAsState()?.value
    val mutationsBlocked = draftState != null && draftEditor?.hasMutationBarrier == true
    var abandon by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = widthClass.gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(stringResource(R.string.deck_versions_title), style = MaterialTheme.typography.headlineMedium)
        if (mutationsBlocked) Text(stringResource(R.string.deck_draft_versions_blocked))
        if (onClose != null) TextButton({ if (controller.requestClose()) onClose() }, modifier = Modifier.versionTouch()) { Text(stringResource(R.string.deck_versions_close)) }
        if (widthClass == WidthClass.COMPACT) VersionsList(controller,state,Modifier.weight(1f),true,mutationsBlocked) { abandon = true }
        else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            VersionsList(controller,state,Modifier.weight(1f),false,mutationsBlocked) { abandon = true }
            VersionsDetail(controller,state,Modifier.weight(1f),mutationsBlocked)
        }
    }
    if (state.captureForm) AlertDialog(onDismissRequest = controller::closeCapture,
        title = { Text(stringResource(R.string.deck_versions_capture)) }, text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.deck_versions_unsaved))
                OutlinedTextField(state.label,controller::label,enabled = !state.pending && !state.mutationBusy,
                    label = { Text(stringResource(R.string.deck_versions_label)) },modifier = Modifier.fillMaxWidth().testTag(DeckVersionsTags.LABEL))
                if (state.invalidLabel) Text(stringResource(R.string.deck_versions_invalid_label),color = MaterialTheme.colorScheme.error)
                state.mutationProblem?.let { VersionProblem(it) }
                if (state.pending) Text(stringResource(R.string.deck_versions_pending))
            }
        }, confirmButton = {
            TextButton(if (state.pending) controller::retry else controller::capture,enabled = !state.mutationBusy && (state.pending || !mutationsBlocked),modifier = Modifier.versionTouch().testTag(DeckVersionsTags.CAPTURE)) {
                Text(stringResource(if (state.pending) R.string.deck_versions_retry else R.string.deck_versions_capture))
            }
        }, dismissButton = { TextButton(controller::closeCapture,modifier = Modifier.versionTouch()) { Text(stringResource(R.string.deck_versions_cancel_form)) } })
    if (state.discardPrompt) AlertDialog(onDismissRequest = controller::keepDraft,
        title = { Text(stringResource(R.string.deck_versions_discard_question)) },text = { Text(stringResource(R.string.deck_versions_unsaved)) },
        confirmButton = { TextButton({ if (controller.discardDraft()) onClose?.invoke() },modifier = Modifier.versionTouch()) { Text(stringResource(R.string.deck_versions_discard)) } },
        dismissButton = { TextButton(controller::keepDraft,modifier = Modifier.versionTouch()) { Text(stringResource(R.string.deck_versions_keep)) } })
    if (abandon) AlertDialog(onDismissRequest = { abandon = false },title = { Text(stringResource(R.string.deck_versions_abandon_question)) },
        text = { Text(stringResource(R.string.deck_versions_abandon_warning)) },
        confirmButton = { TextButton({ abandon = false; controller.abandonConfirmed() },enabled = !state.mutationBusy,modifier = Modifier.versionTouch()) { Text(stringResource(R.string.deck_versions_abandon)) } },
        dismissButton = { TextButton({ abandon = false },modifier = Modifier.versionTouch()) { Text(stringResource(R.string.deck_versions_keep_checking)) } })
}

@Composable private fun VersionsList(controller: DeckVersionsController,state: DeckVersionsUiState,modifier: Modifier,detail: Boolean,mutationsBlocked: Boolean,abandon: () -> Unit) {
    LazyColumn(modifier.fillMaxHeight().testTag(DeckVersionsTags.LIST),verticalArrangement = Arrangement.spacedBy(12.dp),contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.head?.let { Text(stringResource(R.string.deck_versions_head,it.name.take(512),it.revision.value)) }
                if (state.loadingHead || state.loadingList || state.mutationBusy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.deck_versions_loading)) }
                state.headProblem?.let { VersionProblem(it) };state.listProblem?.let { VersionProblem(it) };state.mutationProblem?.let { VersionProblem(it) }
                ActionRow {
                    OutlinedButton({ controller.refreshHead();controller.refreshVersions() },enabled = !state.loadingHead && !state.loadingList,modifier = Modifier.versionTouch()) { Text(stringResource(R.string.deck_versions_refresh)) }
                    OutlinedButton(controller::beginCapture,enabled = !mutationsBlocked && state.head != null && !state.pending && !state.mutationBusy,modifier = Modifier.versionTouch().testTag("deck-versions-new")) { Text(stringResource(R.string.deck_versions_capture)) }
                }
                if (state.pending) MessageBlock(null,listOf(stringResource(R.string.deck_versions_pending)),MessageTone.INFO) {
                    OutlinedButton(controller::retry,enabled = !state.mutationBusy,modifier = Modifier.versionTouch().testTag(DeckVersionsTags.RETRY)) { Text(stringResource(R.string.deck_versions_retry)) }
                    OutlinedButton(abandon,enabled = !state.mutationBusy,modifier = Modifier.versionTouch()) { Text(stringResource(R.string.deck_versions_abandon)) }
                }
                state.captured?.let { Text(stringResource(R.string.deck_versions_saved,it.label)) }
                state.restored?.let { Text(stringResource(R.string.deck_versions_restored,it.snapshot.revision.value));Text(stringResource(R.string.deck_versions_recovery_saved,it.recoveryVersion.label));RecordedTime(it.recoveryVersion.createdAt) }
                if (state.head != null && !state.loadingList && state.listProblem == null && state.items.isEmpty()) Text(stringResource(R.string.deck_versions_empty))
                if (detail) DetailHeader(controller,state,mutationsBlocked)
            }
        }
        if (detail) state.preview?.takeIf { !state.pending }?.let { p ->
            item { ManifestDifference(p) }
            items(p.difference.entries) { change -> ChangedEntry(change) }
        }
        items(state.items,key = { it.id.value }) { version ->
            OutlinedButton({ controller.selectVersion(version.id) },enabled = !state.pending && !state.mutationBusy,
                modifier = Modifier.fillMaxWidth().versionTouch().semantics { selected = state.selectedId == version.id }.testTag("deck-version-${version.id.value}")) {
                Column {
                    Text(stringResource(R.string.deck_versions_version,stringResource(if (version.kind == DeckVersionKind.USER) R.string.deck_versions_user else R.string.deck_versions_recovery),version.label))
                    Text(stringResource(R.string.deck_versions_source_revision,version.sourceRevision.value));RecordedTime(version.createdAt)
                }
            }
        }
        item { if (state.cursor != null) OutlinedButton(controller::loadMore,enabled = !state.loadingList,modifier = Modifier.versionTouch().testTag("deck-versions-more")) { Text(stringResource(R.string.deck_versions_more)) } }
    }
}
@Composable private fun VersionsDetail(controller: DeckVersionsController,state: DeckVersionsUiState,modifier: Modifier,mutationsBlocked: Boolean) {
    LazyColumn(modifier.fillMaxHeight().testTag(DeckVersionsTags.DETAIL),verticalArrangement = Arrangement.spacedBy(12.dp),contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { DetailHeader(controller,state,mutationsBlocked) } }
        state.preview?.takeIf { !state.pending }?.let { p ->
            item { ManifestDifference(p) }
            items(p.difference.entries) { change -> ChangedEntry(change) }
        }
    }
}
@Composable private fun DetailHeader(controller: DeckVersionsController,state: DeckVersionsUiState,mutationsBlocked: Boolean) {
    if (state.loadingTarget || state.previewing) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.targetProblem?.let { VersionProblem(it) };state.previewProblem?.let { VersionProblem(it) }
    state.selected?.let {
        SectionHeading(it.label);Text(stringResource(R.string.deck_versions_source_revision,it.sourceRevision.value));RecordedTime(it.createdAt)
        OutlinedButton(controller::previewRestore,enabled = state.head != null && !state.pending && !state.mutationBusy && !state.previewing,modifier = Modifier.versionTouch().testTag(DeckVersionsTags.PREVIEW)) { Text(stringResource(R.string.deck_versions_preview)) }
    }
    state.preview?.takeIf { !state.pending }?.let {
        Text(stringResource(R.string.deck_versions_preview_notice))
        Text(stringResource(R.string.deck_versions_unsaved))
        OutlinedTextField(state.recoveryLabel,controller::recoveryLabel,enabled = !state.mutationBusy,
            label = { Text(stringResource(R.string.deck_versions_recovery_label)) },modifier = Modifier.fillMaxWidth().testTag(DeckVersionsTags.RECOVERY_LABEL))
        if (state.invalidLabel) Text(stringResource(R.string.deck_versions_invalid_label),color = MaterialTheme.colorScheme.error)
        ActionRow {
            OutlinedButton(controller::confirmRestore,enabled = !mutationsBlocked && !state.mutationBusy,modifier = Modifier.versionTouch().testTag(DeckVersionsTags.RESTORE)) { Text(stringResource(R.string.deck_versions_restore)) }
            TextButton(controller::cancelPreview,enabled = !state.mutationBusy,modifier = Modifier.versionTouch()) { Text(stringResource(R.string.deck_versions_cancel_preview)) }
        }
    }
}
@Composable private fun VersionProblem(problem: Problem) = MessageBlock(null,listOf(stringResource(problemText(problem))),MessageTone.ERROR)
private fun Modifier.versionTouch(): Modifier = sizeIn(minWidth = 48.dp,minHeight = 48.dp)
@Composable private fun RecordedTime(value: UtcTimestamp) { Text(stringResource(R.string.deck_versions_timestamp,value.epochSeconds,value.nanoAdjustment)) }
@Composable private fun DataField(label: Int,text: String) { SectionHeading(stringResource(label));DataText(text) }
@Composable private fun DataText(text: String) {
    var offset by remember { mutableIntStateOf(0) }
    val start = offset.coerceIn(0,text.length);var end = (start+4096).coerceAtMost(text.length)
    if (end < text.length && end > start && text[end-1].isHighSurrogate() && text[end].isLowSurrogate()) end--
    Text(if (text.isEmpty()) stringResource(R.string.deck_versions_empty_text) else text.substring(start,end))
    if (start > 0 || end < text.length) ActionRow {
        TextButton({ offset = 0 },enabled = start > 0,modifier = Modifier.versionTouch()) { Text(stringResource(R.string.deck_versions_text_start)) }
        TextButton({ offset = end },enabled = end < text.length,modifier = Modifier.versionTouch()) { Text(stringResource(R.string.deck_versions_text_next)) }
    }
}
/** One retained nested value at a time; traversal is never a flattened package or history graph. */
@Composable private fun <T> Reader(values: List<T>,render: @Composable (T) -> Unit) {
    var position by remember { mutableIntStateOf(0) }
    if (values.isEmpty()) { Text(stringResource(R.string.deck_versions_empty_list));return }
    val index = position.coerceIn(0,values.lastIndex)
    Text(stringResource(R.string.deck_versions_position,index+1,values.size))
    key(index) { render(values[index]) }
    if (values.size > 1) ActionRow {
        TextButton({ position = index-1 },enabled = index > 0,modifier = Modifier.versionTouch()) { Text(stringResource(R.string.deck_versions_previous)) }
        TextButton({ position = index+1 },enabled = index < values.lastIndex,modifier = Modifier.versionTouch()) { Text(stringResource(R.string.deck_versions_next)) }
    }
}
@Composable private fun <T> Fact(label: Int,value: Knowledge<T>,render: @Composable (T) -> Unit) {
    SectionHeading(stringResource(label))
    when (value) {
        is Knowledge.Known -> render(value.value)
        is Knowledge.Absent -> { Text(stringResource(R.string.deck_versions_absent));DataField(R.string.deck_versions_reason,value.reason) }
        is Knowledge.Unknown -> {
            Text(stringResource(R.string.deck_versions_unknown));DataField(R.string.deck_versions_reason,value.reason)
            SectionHeading(stringResource(R.string.deck_versions_evidence));Reader(value.evidence) { EvidenceValue(it) }
        }
    }
}
@Composable private fun EvidenceValue(value: Evidence) {
    DataText(value.description);value.source?.let { DataField(R.string.deck_versions_uri,it.value) } ?: Text(stringResource(R.string.deck_versions_absent))
    value.recordedAt?.let { RecordedTime(it) } ?: Text(stringResource(R.string.deck_versions_absent))
}
@Composable private fun Qualified(source: ProviderId,raw: String) { DataField(R.string.deck_versions_source,source.value);DataField(R.string.deck_versions_id,raw) }
@Composable private fun ManifestDifference(preview: DeckRestorePreview) {
    var field by remember { mutableStateOf(DeckChangedField.NAME) };var menu by remember { mutableStateOf(false) }
    FeaturePanel {
        SectionHeading(stringResource(R.string.deck_versions_inspect_field))
        Box {
            OutlinedButton({ menu = true },modifier = Modifier.versionTouch().testTag("deck-versions-field")) { Text(stringResource(fieldLabel(field))) }
            DropdownMenu(menu,{ menu = false }) { DeckChangedField.entries.forEach { f ->
                DropdownMenuItem(text = { Text(if (f in preview.difference.changedFields) stringResource(R.string.deck_versions_changed,stringResource(fieldLabel(f))) else stringResource(fieldLabel(f))) },
                    onClick = { field = f;menu = false },modifier = Modifier.versionTouch())
            } }
        }
        key(field) {
            SectionHeading(stringResource(R.string.deck_versions_current));ManifestField(preview.current,field)
            SectionHeading(stringResource(R.string.deck_versions_historical));ManifestField(preview.target.manifest,field)
        }
    }
}
@Composable private fun ManifestField(value: DeckSnapshot,field: DeckChangedField) {
    when (field) {
        DeckChangedField.NAME -> DataText(value.name)
        DeckChangedField.FORMAT -> Fact(R.string.deck_versions_field_format,value.format) { Qualified(it.source,it.raw) }
        DeckChangedField.DESCRIPTION -> DataText(value.description)
        DeckChangedField.PRIMER -> DataText(value.primer)
        DeckChangedField.FOLDER -> Fact(R.string.deck_versions_field_folder,value.folder) { DataText(it) }
        DeckChangedField.TAGS -> Reader(value.tags) { DataText(it) }
        DeckChangedField.CREATED_AT -> RecordedTime(value.createdAt)
        DeckChangedField.MODIFIED_AT -> RecordedTime(value.modifiedAt)
        DeckChangedField.REVISION -> DataText(value.revision.value.toString())
        DeckChangedField.BUDGET -> Fact(R.string.deck_versions_field_budget,value.budget) {
            DataText(it.value.toString());SectionHeading(stringResource(R.string.deck_versions_unit))
            when (val unit = it.unit) {
                is DecimalUnit.Fiat -> DataText(unit.currencyCode)
                is DecimalUnit.Tix -> Text(stringResource(R.string.deck_versions_tix))
                is DecimalUnit.Native -> Qualified(unit.provider,unit.symbol)
            }
            DataField(R.string.deck_versions_precision,it.precision.toString());DataField(R.string.deck_versions_scale,it.scale.toString())
        }
        DeckChangedField.PROFILE -> value.profileId?.let { DataText(it.value) } ?: Text(stringResource(R.string.deck_versions_absent))
        DeckChangedField.CATALOG_REFERENCES -> Reader(value.catalog) {
            DataField(R.string.deck_versions_source,it.source.value)
            DataField(R.string.deck_versions_generation,it.generation?.value ?: stringResource(R.string.deck_versions_absent))
            DataField(R.string.deck_versions_field_revision,it.revision?.value?.toString() ?: stringResource(R.string.deck_versions_absent))
            DataField(R.string.deck_versions_language,it.language?.value ?: stringResource(R.string.deck_versions_absent))
        }
        DeckChangedField.CATEGORIES -> value.categoryOrder?.let { Reader(it) { LabelValue(it) } } ?: Text(stringResource(R.string.deck_versions_absent))
        DeckChangedField.ENTRY_ORDER -> Reader(value.entries) { DataText(it.id.value) }
        DeckChangedField.PACKAGES -> Reader(value.packageInstances) {
            DataField(R.string.deck_versions_id,it.id.value);DataField(R.string.deck_versions_package,it.packageId.value);DataField(R.string.deck_versions_package_version,it.versionId.value)
            Text(stringResource(if (it.policy == PackageLinkPolicy.LINKED) R.string.deck_versions_linked else R.string.deck_versions_copy))
            SectionHeading(stringResource(R.string.deck_versions_contributions));Reader(it.contributions) {
                DataField(R.string.deck_versions_package_entry,it.packageEntryId.value);DataField(R.string.deck_versions_deck_entry,it.deckEntryId.value);DataField(R.string.deck_versions_quantity,it.quantity.toString())
            }
        }
    }
}
@Composable private fun LabelValue(value: EntryLabel) { DataField(R.string.deck_versions_id,value.id.value);DataText(value.name) }
@Composable private fun ChangedEntry(value: CardChange) {
    FeaturePanel {
        SectionHeading(stringResource(R.string.deck_versions_entry,value.entryId?.value ?: stringResource(R.string.deck_versions_absent)))
        SectionHeading(stringResource(R.string.deck_versions_current));EntryValue(value.before)
        SectionHeading(stringResource(R.string.deck_versions_historical));EntryValue(value.after)
        DataField(R.string.deck_versions_reason,value.reason);Reader(value.evidence) { EvidenceValue(it) }
    }
}
@Composable private fun EntryValue(value: DeckEntry?) {
    if (value == null) { Text(stringResource(R.string.deck_versions_entry_added_removed));return }
    DataField(R.string.deck_versions_id,value.id.value);DataField(R.string.deck_versions_quantity,value.quantity.toString())
    DataField(R.string.deck_versions_board,stringResource(versionBoardLabel(value.board)))
    Fact(R.string.deck_versions_oracle,value.identity.oracleId) { Qualified(it.source,it.raw) }
    Fact(R.string.deck_versions_printing,value.identity.printingId) { Qualified(it.source,it.raw) }
    Fact(R.string.deck_versions_original_name,value.identity.originalName) { DataText(it) }
    value.identity.raw?.let { DataField(R.string.deck_versions_raw,it.rawId);DataField(R.string.deck_versions_source,it.provider.value);DataField(R.string.deck_versions_kind,it.kind) } ?: Text(stringResource(R.string.deck_versions_absent))
    DataField(R.string.deck_versions_language,value.identity.language.value)
    Fact(R.string.deck_versions_finish,value.finish) { Text(stringResource(when (it) { Finish.NONFOIL -> R.string.deck_versions_nonfoil;Finish.FOIL -> R.string.deck_versions_foil;Finish.ETCHED -> R.string.deck_versions_etched })) }
    DataField(R.string.deck_versions_notes,value.notes)
    SectionHeading(stringResource(R.string.deck_versions_labels));Reader(value.labels) { LabelValue(it) }
    SectionHeading(stringResource(R.string.deck_versions_overrides));Reader(value.roleOverrides) {
        DataField(R.string.deck_versions_deck_entry,it.entryId?.value ?: stringResource(R.string.deck_versions_absent))
        Fact(R.string.deck_versions_oracle,it.oracleId) { Qualified(it.source,it.raw) }
        DataField(R.string.deck_versions_role,it.role);DataField(R.string.deck_versions_enabled,stringResource(if (it.enabled) R.string.deck_versions_true else R.string.deck_versions_false));RecordedTime(it.modifiedAt)
    }
}

private fun fieldLabel(value: DeckChangedField): Int = when (value) {
    DeckChangedField.NAME -> R.string.deck_versions_field_name
    DeckChangedField.FORMAT -> R.string.deck_versions_field_format
    DeckChangedField.DESCRIPTION -> R.string.deck_versions_field_description
    DeckChangedField.PRIMER -> R.string.deck_versions_field_primer
    DeckChangedField.FOLDER -> R.string.deck_versions_field_folder
    DeckChangedField.TAGS -> R.string.deck_versions_field_tags
    DeckChangedField.CREATED_AT -> R.string.deck_versions_field_created_at
    DeckChangedField.MODIFIED_AT -> R.string.deck_versions_field_modified_at
    DeckChangedField.REVISION -> R.string.deck_versions_field_revision
    DeckChangedField.BUDGET -> R.string.deck_versions_field_budget
    DeckChangedField.PROFILE -> R.string.deck_versions_field_profile
    DeckChangedField.CATALOG_REFERENCES -> R.string.deck_versions_field_catalog_references
    DeckChangedField.CATEGORIES -> R.string.deck_versions_field_categories
    DeckChangedField.PACKAGES -> R.string.deck_versions_field_packages
    DeckChangedField.ENTRY_ORDER -> R.string.deck_versions_field_entry_order
}
private fun versionBoardLabel(value: BoardRole): Int = when (value) {
    BoardRole.MAIN -> R.string.deck_versions_board_main
    BoardRole.SIDEBOARD -> R.string.deck_versions_board_sideboard
    BoardRole.MAYBEBOARD -> R.string.deck_versions_board_maybeboard
    BoardRole.COMMANDER -> R.string.deck_versions_board_commander
    BoardRole.PARTNER -> R.string.deck_versions_board_partner
    BoardRole.BACKGROUND -> R.string.deck_versions_board_background
    BoardRole.COMPANION -> R.string.deck_versions_board_companion
}
