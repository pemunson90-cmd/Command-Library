package com.architectpsg.commandlibrary.feature.shell.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*

internal object DownloadTags {
    const val LIST = "downloads-list"
    const val BACK = "downloads-back"
    const val NEW = "downloads-new"
    const val RULINGS_NEW = "downloads-rulings-new"
    const val RULINGS_REVIEW = "downloads-rulings-review"
    const val WIFI = "downloads-wifi"
    const val REFRESH = "downloads-refresh"
    const val MORE = "downloads-more"
    const val DETAIL = "download-detail"
    const val START = "download-start"
    const val PAUSE = "download-pause"
    const val CANCEL = "download-cancel"
    const val INSTALL = "download-install"
    const val STOP_INSTALL = "download-stop-install"
    const val APPROVE = "download-approve"
    const val CLOSE_APPROVAL = "download-approval-close"
    const val RELOAD_APPROVAL = "download-approval-reload"
    const val CONFIRM_CANCEL = "download-cancel-confirm"
    fun row(id: EntityId) = "download-open-${id.value}"
}

@Composable
internal fun DownloadsScreen(controller: DownloadsController, widthClass: WidthClass, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    LaunchedEffect(controller) { controller.load() }
    BackHandler {
        when {
            state.approval != null -> controller.dismissApproval()
            state.pendingCancel != null -> controller.dismissCancel()
            state.selectedId != null -> controller.closeDetail()
            else -> onBack()
        }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (state.selectedId != null) DownloadDetail(state, controller, widthClass)
        else LazyColumn(Modifier.widthIn(max = ReadingWidth).fillMaxSize().padding(horizontal = widthClass.gutter).testTag(DownloadTags.LIST),
            contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeading(stringResource(R.string.downloads_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(if(controller.rulingsEnabled) R.string.downloads_all_intro else R.string.downloads_intro))
                    Text(stringResource(R.string.downloads_lifecycle))
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(DownloadTags.WIFI)
                        .toggleable(state.wifiOnly, enabled = !state.planning && state.approval == null, role = Role.Checkbox, onValueChange = controller::changeWifiOnly),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Checkbox(state.wifiOnly, onCheckedChange = null); Text(stringResource(R.string.downloads_wifi), Modifier.weight(1f))
                    }
                    Text(stringResource(R.string.downloads_settings_apply))
                    val name = stringResource(R.string.downloads_english_name)
                    val rulingName = stringResource(R.string.downloads_rulings_metadata_name)
                    ActionRow {
                        Button(onClick = { controller.plan(name) }, enabled = state.ready && !state.planning && state.approval == null,
                            modifier = Modifier.heightIn(min = 48.dp).testTag(DownloadTags.NEW)) { Text(stringResource(R.string.downloads_new)) }
                        if(controller.rulingsEnabled) OutlinedButton(onClick = { controller.planRulings(rulingName) }, enabled = state.ready && !state.planning && state.approval == null,
                            modifier = Modifier.heightIn(min = 48.dp).testTag(DownloadTags.RULINGS_NEW)) { Text(stringResource(R.string.downloads_rulings_new)) }
                        OutlinedButton(onClick = controller::load, enabled = !state.loading,
                            modifier = Modifier.heightIn(min = 48.dp).testTag(DownloadTags.REFRESH)) { Text(stringResource(R.string.profiles_refresh)) }
                        OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).testTag(DownloadTags.BACK)) { Text(stringResource(R.string.downloads_back_catalog)) }
                    }
                    DownloadNotice(state)
                    if (state.loading || state.planning) DownloadBusy(stringResource(R.string.downloads_loading))
                    if (!state.loading && state.ready && state.problem == null && state.items.isEmpty()) Text(stringResource(
                        if(controller.rulingsEnabled) { if(state.nextCursor == null) R.string.downloads_all_empty else R.string.downloads_all_empty_page }
                        else if (state.nextCursor == null) R.string.downloads_empty else R.string.downloads_empty_page))
                    Text(stringResource(R.string.downloads_live_detail))
                }
            }
            items(state.items, key = { it.plan.jobId.value }) { row ->
                FeaturePanel {
                    Text(row.plan.request.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(downloadStateText(row))
                    Text(stringResource(R.string.downloads_updated, formatTimestamp(row.updatedAt)))
                    OutlinedButton(onClick = { controller.select(row.plan.jobId) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag(DownloadTags.row(row.plan.jobId))) { Text(stringResource(R.string.downloads_open)) }
                }
            }
            if (state.nextCursor != null) item {
                OutlinedButton(onClick = controller::loadMore, enabled = !state.loading,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(DownloadTags.MORE)) { Text(stringResource(R.string.downloads_more)) }
            }
        }
    }
    state.approval?.let { shown ->
        val busy = shown.plan.jobId in state.controls
        AlertDialog(onDismissRequest = controller::dismissApproval,
            title = { Text(stringResource(when(catalogDownloadStage(shown)) {
                CatalogDownloadStage.RULINGS_METADATA -> R.string.downloads_rulings_metadata_approve
                CatalogDownloadStage.RULINGS_FILE -> R.string.downloads_rulings_file_approve
                else -> R.string.downloads_approve_title
            })) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DownloadRequestDetails(shown)
                    Text(stringResource(if (busy) R.string.downloads_starting else if(catalogDownloadStage(shown) == CatalogDownloadStage.RULINGS_METADATA)
                        R.string.downloads_rulings_metadata_body else R.string.downloads_approve_body))
                    DownloadProblem(state.problem)
                    if (state.problem == Problem.STALE_REVISION) OutlinedButton(onClick = { controller.prepare(shown.plan.jobId) }, enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(DownloadTags.RELOAD_APPROVAL)) { Text(stringResource(R.string.downloads_reload_job)) }
                }
            },
            confirmButton = { TextButton(onClick = controller::confirmApproval, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag(DownloadTags.APPROVE)) { Text(stringResource(
                    if (shown.state == AcquisitionState.PLANNED) R.string.downloads_download else R.string.downloads_resume)) } },
            dismissButton = { TextButton(onClick = controller::dismissApproval, modifier = Modifier.heightIn(min = 48.dp).testTag(DownloadTags.CLOSE_APPROVAL)) {
                Text(stringResource(if (busy) R.string.profiles_close else R.string.downloads_not_now)) } })
    }
    state.pendingCancel?.let { id ->
        val busy = id in state.controls
        AlertDialog(onDismissRequest = controller::dismissCancel, title = { Text(stringResource(R.string.downloads_cancel_title)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (busy) R.string.downloads_cancelling else R.string.downloads_cancel_body)); DownloadProblem(state.problem)
            } },
            confirmButton = { TextButton(onClick = controller::confirmCancel, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag(DownloadTags.CONFIRM_CANCEL)) { Text(stringResource(R.string.downloads_cancel)) } },
            dismissButton = { TextButton(onClick = controller::dismissCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (busy) R.string.profiles_close else R.string.downloads_keep)) } })
    }
}

@Composable
private fun DownloadDetail(state: DownloadsUiState, controller: DownloadsController, widthClass: WidthClass) {
    Column(Modifier.widthIn(max = ReadingWidth).fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = widthClass.gutter, vertical = 16.dp).testTag(DownloadTags.DETAIL), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading(stringResource(R.string.downloads_detail_title), style = MaterialTheme.typography.headlineMedium)
        OutlinedButton(onClick = controller::closeDetail, modifier = Modifier.heightIn(min = 48.dp).testTag(DownloadTags.BACK)) { Text(stringResource(R.string.downloads_back_list)) }
        DownloadNotice(state)
        if (state.detailLoading) DownloadBusy(stringResource(R.string.downloads_loading))
        val row = state.selected
        if (row == null && !state.detailLoading) OutlinedButton(onClick = { state.selectedId?.let(controller::select) },
            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.shell_action_retry)) }
        if (row != null) {
            val id = row.plan.jobId
            val installing = id in state.installing
            val queued = id in state.queuedInstalls
            val stage = catalogDownloadStage(row)
            Text(row.plan.request.displayName, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(when { installing -> R.string.downloads_installing; queued -> R.string.downloads_waiting_install; else -> downloadStateResource(row) }))
            Text(stringResource(R.string.downloads_progress, formatBytes(row.transferredBytes), formatCount(row.acquiredRecords),
                knowledgeText(row.expectedRecords) { formatCount(it) }))
            if (row.state == AcquisitionState.RUNNING || installing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            state.installProgress[id]?.let { Text(stringResource(R.string.downloads_processed, formatCount(it.completedUnits))) }
            row.failure?.let { DownloadProblem(it.code.toProblem()) }
            if (stage == CatalogDownloadStage.ENGLISH && artifactMatchesCatalog(row, state.catalog)) Text(stringResource(R.string.downloads_matches_installed))
            if(stage == CatalogDownloadStage.RULINGS_FILE && artifactMatchesRulings(row, state.rulings)) Text(stringResource(R.string.downloads_rulings_matches))
            if(stage == CatalogDownloadStage.RULINGS_METADATA && row.state == AcquisitionState.COMPLETED) Text(stringResource(R.string.downloads_rulings_metadata_ready))
            val enabled = id !in state.controls && !installing && !queued
            val rulingFileName = stringResource(R.string.downloads_rulings_file_name)
            ActionRow {
                if (row.state == AcquisitionState.PLANNED || row.state in ResumableDownloads) Button(onClick = { controller.prepare(id) }, enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(DownloadTags.START)) { Text(stringResource(
                        if (row.state == AcquisitionState.PLANNED) R.string.downloads_download else R.string.downloads_resume)) }
                if (row.state in setOf(AcquisitionState.QUEUED, AcquisitionState.RUNNING)) OutlinedButton(onClick = { controller.pause(id) }, enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(DownloadTags.PAUSE)) { Text(stringResource(R.string.downloads_pause)) }
                if (row.state !in setOf(AcquisitionState.CANCELLED, AcquisitionState.COMPLETED)) OutlinedButton(onClick = { controller.requestCancel(id) }, enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(DownloadTags.CANCEL)) { Text(stringResource(R.string.downloads_cancel)) }
                if (row.state == AcquisitionState.COMPLETED && stage == CatalogDownloadStage.RULINGS_METADATA) Button(onClick = { controller.planRulingsFile(id, rulingFileName) },
                    enabled = enabled && !state.planning && state.approval == null, modifier = Modifier.heightIn(min = 48.dp).testTag(DownloadTags.RULINGS_REVIEW)) { Text(stringResource(R.string.downloads_rulings_review)) }
                if (row.state == AcquisitionState.COMPLETED && stage != CatalogDownloadStage.RULINGS_METADATA && !installing && !queued) Button(onClick = { controller.install(id) }, enabled = enabled && state.installing.isEmpty(),
                    modifier = Modifier.heightIn(min = 48.dp).testTag(DownloadTags.INSTALL)) { Text(stringResource(R.string.downloads_install)) }
                if (installing || queued) OutlinedButton(onClick = { controller.stopInstall(id) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag(DownloadTags.STOP_INSTALL)) { Text(stringResource(R.string.downloads_stop_install)) }
            }
            if (id in state.controls) DownloadBusy(stringResource(R.string.downloads_working))
            DownloadRequestDetails(row)
            DetailField(stringResource(R.string.downloads_planned), formatTimestamp(row.plan.createdAt))
            DetailField(stringResource(R.string.downloads_last_update), formatTimestamp(row.updatedAt))
            (row.artifact as? Knowledge.Known)?.value?.provenance?.let { provenance ->
                DetailField(stringResource(R.string.downloads_retrieved_start), formatTimestamp(provenance.retrievalStartedAt))
                DetailField(stringResource(R.string.downloads_retrieved_end), formatTimestamp(provenance.retrievalCompletedAt))
            }
        }
    }
}

@Composable
private fun DownloadRequestDetails(row: AcquisitionSnapshot) {
    val request = row.plan.request
    DetailField(stringResource(R.string.downloads_source), request.intent.source.value)
    DetailField(stringResource(R.string.downloads_url), request.intent.resource.value)
    if(request.languageScope == AcquisitionLanguageScope.RULINGS_LANGUAGE_NOT_ASSERTED)
        DetailField(stringResource(R.string.rulings_language), stringResource(R.string.rulings_language_unknown))
    else DetailField(stringResource(R.string.catalog_field_default_language), languageName(requireNotNull(request.intent.language)))
    DetailField(stringResource(R.string.downloads_source_version), knowledgeText(request.sourceVersion) { it })
    DetailField(stringResource(R.string.catalog_field_source_date), knowledgeText(request.sourceDate) { formatTimestamp(it) })
    DetailField(stringResource(R.string.downloads_expected_size), knowledgeText(request.intent.expectedBytes) { formatBytes(it) })
    DetailField(stringResource(R.string.downloads_budget), formatBytes(request.limits.maxTransferBytes))
    Text(stringResource(if (request.wifiOnly) R.string.downloads_wifi_required else R.string.downloads_network_allowed))
    Text(stringResource(if(request.source is AcquisitionSource.EnglishSearch) R.string.downloads_mutable_source else R.string.downloads_rulings_source_note))
}

@Composable
private fun DownloadNotice(state: DownloadsUiState) {
    DownloadProblem(state.problem)
    if (state.running.isNotEmpty()) Text(stringResource(R.string.downloads_running, formatCount(state.running.size.toLong())))
    state.installed?.let { Text(stringResource(R.string.downloads_installed, formatCount(it.installed.counts.oracleCards), formatCount(it.installed.counts.printings))) }
    state.rulingsInstalled?.let { Text(stringResource(R.string.downloads_rulings_installed, formatCount(it.generation.recordCount))) }
    state.rulings?.active?.let { Text(stringResource(R.string.downloads_rulings_status, formatCount(it.recordCount))) }
}
@Composable
private fun DownloadProblem(problem: Problem?) {
    problem?.let { MessageBlock(title = stringResource(R.string.downloads_notice), body = listOf(stringResource(problemText(it))), tone = MessageTone.ERROR) }
}
@Composable
private fun DownloadBusy(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp)); Text(text)
    }
}
private fun artifactMatchesCatalog(row: AcquisitionSnapshot, catalog: LocalCatalogStatus?): Boolean {
    val artifact = (row.artifact as? Knowledge.Known)?.value ?: return false
    return catalog?.activeGenerations?.any {
        it.provenance.provider == artifact.provenance.provider && it.provenance.checksum?.let { hash ->
            hash.algorithm.equals(artifact.sha256.algorithm, true) && hash.hex.equals(artifact.sha256.hex, true)
        } == true
    } == true
}
private fun artifactMatchesRulings(row: AcquisitionSnapshot, status: RulingsStatus?): Boolean {
    val artifact = (row.artifact as? Knowledge.Known)?.value ?: return false
    val source = status?.active?.provenance ?: return false
    return source.provider == artifact.provenance.provider && source.source == artifact.provenance.requestedUri &&
        source.checksum?.let { it.algorithm.equals(artifact.sha256.algorithm, true) && it.hex.equals(artifact.sha256.hex, true) } == true
}
@Composable
private fun downloadStateText(row: AcquisitionSnapshot): String = stringResource(downloadStateResource(row))
private fun downloadStateResource(row: AcquisitionSnapshot): Int = when (row.state) {
    AcquisitionState.PLANNED -> R.string.downloads_planned_state
    AcquisitionState.QUEUED -> R.string.downloads_queued
    AcquisitionState.RUNNING -> if (row.phase == AcquisitionPhase.VERIFYING) R.string.downloads_verifying else R.string.downloads_downloading
    AcquisitionState.PAUSED -> R.string.downloads_paused
    AcquisitionState.WAITING_NETWORK -> R.string.downloads_waiting_network
    AcquisitionState.WAITING_WIFI -> R.string.downloads_waiting_wifi
    AcquisitionState.WAITING_CONSENT -> R.string.downloads_waiting_consent
    AcquisitionState.INTERRUPTED -> R.string.downloads_interrupted
    AcquisitionState.FAILED -> R.string.downloads_failed
    AcquisitionState.CANCELLED -> R.string.downloads_cancelled
    AcquisitionState.COMPLETED -> R.string.downloads_complete
}
