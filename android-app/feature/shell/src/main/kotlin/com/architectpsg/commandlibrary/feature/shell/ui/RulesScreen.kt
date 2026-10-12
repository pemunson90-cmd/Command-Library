package com.architectpsg.commandlibrary.feature.shell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.JobPhase
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.RuleEntry
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.Problem
import com.architectpsg.commandlibrary.feature.shell.presentation.RulesController
import com.architectpsg.commandlibrary.feature.shell.presentation.RulesImportPhase
import com.architectpsg.commandlibrary.feature.shell.presentation.toProblem

@Composable
internal fun RulesScreen(
    controller: RulesController,
    widthClass: WidthClass,
    pickerUnavailable: Boolean,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    LazyColumn(modifier, contentPadding = PaddingValues(widthClass.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeading(stringResource(R.string.rules_title)) }
        item { Text(stringResource(R.string.rules_local_help)) }
        item { Text(stringResource(R.string.rules_limits)) }
        item { Button(onClick = onPickFile, enabled = state.importPhase != RulesImportPhase.RUNNING) { Text(stringResource(R.string.rules_choose)) } }
        if (pickerUnavailable) item { Text(stringResource(R.string.import_picker_unavailable)) }
        state.fileName?.let { name -> item { DetailField(stringResource(R.string.rules_file), name) } }
        when (state.importPhase) {
            RulesImportPhase.RUNNING -> item {
                MessageBlock(null, listOf(
                    stringResource(R.string.rules_progress, formatCount(state.pass.toLong()), formatBytes(state.bytesRead)),
                    stringResource(rulesPhaseText(state.progress?.phase)),
                ), MessageTone.INFO) {
                    state.progress?.let { Text(stringResource(R.string.rules_staged, formatCount(it.completedUnits))) }
                    Button(onClick = controller::cancelImport) { Text(stringResource(R.string.import_action_cancel)) }
                }
            }
            RulesImportPhase.SUCCEEDED -> item { Text(stringResource(R.string.rules_import_done)) }
            RulesImportPhase.CANCELLED -> item { Text(stringResource(R.string.rules_import_cancelled)) }
            RulesImportPhase.FAILED -> item {
                MessageBlock(stringResource(R.string.rules_import_failed), listOf(
                    if (state.importTooLarge) stringResource(R.string.rules_import_large)
                    else stringResource(problemText(state.importProblem ?: Problem.UNEXPECTED)),
                ), MessageTone.ERROR)
            }
            RulesImportPhase.IDLE -> Unit
        }
        item {
            if (state.statusLoading) Text(stringResource(R.string.rules_status_loading))
            state.statusProblem?.let { Text(stringResource(problemText(it))) }
            OutlinedButton(onClick = controller::refresh) { Text(stringResource(R.string.shell_action_retry)) }
            val active = state.active
            if (active != null) {
                SectionHeading(stringResource(R.string.rules_installed, formatCount(active.entryCount)))
                DetailField(stringResource(R.string.rules_version), knowledgeText(active.source.version) { it })
                DetailField(stringResource(R.string.rules_source_date), knowledgeText(active.source.sourceDate) { formatTimestamp(it) })
                DetailField(stringResource(R.string.rules_source_notice), knowledgeText(active.source.notice) { it.attribution })
            } else {
                Text(stringResource(if (state.status?.active is Knowledge.Absent) R.string.rules_empty else R.string.rules_status_unknown))
            }
            val attempt = (state.status?.latestAttempt as? Knowledge.Known)?.value
            if (attempt != null) {
                Text(stringResource(R.string.rules_attempt, stringResource(if (attempt.phase == JobPhase.COMPLETED) R.string.rules_attempt_completed else rulesPhaseText(attempt.phase))))
                attempt.failure?.let { Text(stringResource(problemText(it.code.toProblem()))) }
            }
        }
        item {
            OutlinedTextField(value = controller.queryInput, onValueChange = { controller.queryInput = it },
                label = { Text(stringResource(R.string.rules_search_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = controller::submit, enabled = !state.searchLoading) { Text(stringResource(R.string.search_action)) }
            state.searchProblem?.let { Text(stringResource(problemText(it))) }
            state.detailProblem?.let { Text(stringResource(problemText(it))) }
            if (state.searchLoading || state.detailLoading) Text(stringResource(R.string.search_loading))
        }
        val selected = state.selected
        if (selected != null) {
            item {
                OutlinedButton(onClick = controller::clearSelection) { Text(stringResource(R.string.rules_back)) }
                SectionHeading(ruleLabel(selected))
                Text(selected.text)
            }
            if (selected.references.isNotEmpty()) item { SectionHeading(stringResource(R.string.rules_references)) }
            items(selected.references) { reference ->
                if (reference.resolved is Knowledge.Known) {
                    OutlinedButton(onClick = { controller.follow(reference) }, enabled = !state.detailLoading) { Text(reference.rawTarget) }
                } else Text(stringResource(R.string.rules_reference_unresolved, reference.rawTarget))
            }
        } else {
            items(state.results, key = { "${it.id.source.value}:${it.id.raw}" }) { entry ->
                OutlinedButton(onClick = { controller.select(entry) }, modifier = Modifier.fillMaxWidth()) { Text(ruleLabel(entry)) }
            }
            if (state.query != null && !state.searchLoading && state.results.isEmpty() && state.searchProblem == null) item {
                Text(stringResource(R.string.rules_no_results))
            }
            if (state.canLoadMore) item { OutlinedButton(onClick = controller::loadMore) { Text(stringResource(R.string.search_load_more)) } }
            if (state.results.size >= RulesController.MAX_RESULTS || state.pagesLoaded >= RulesController.MAX_PAGES) item { Text(stringResource(R.string.rules_result_limit)) }
        }
    }
}

private fun ruleLabel(entry: RuleEntry): String = listOfNotNull(
    (entry.number as? Knowledge.Known)?.value,
    (entry.title as? Knowledge.Known)?.value,
).joinToString(" ").ifBlank { entry.id.raw }

private fun rulesPhaseText(phase: JobPhase?): Int = when (phase) {
    null, JobPhase.QUEUED -> R.string.import_phase_queued
    JobPhase.DOWNLOADING -> R.string.import_phase_downloading
    JobPhase.STAGING -> R.string.import_phase_staging
    JobPhase.VALIDATING -> R.string.rules_phase_validating
    JobPhase.ACTIVATING -> R.string.rules_phase_activating
    JobPhase.COMPLETED -> R.string.import_phase_completed
    JobPhase.FAILED -> R.string.rules_import_failed
    JobPhase.CANCELLED -> R.string.rules_import_cancelled
}
