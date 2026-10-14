package com.architectpsg.commandlibrary.feature.shell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.CatalogImportLimits
import com.architectpsg.commandlibrary.core.model.ChecksumVerification
import com.architectpsg.commandlibrary.core.model.JobPhase
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.LocalCatalogStatus
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.CatalogStatusUiState
import com.architectpsg.commandlibrary.feature.shell.presentation.ImportFailure
import com.architectpsg.commandlibrary.feature.shell.presentation.ImportUiState
import com.architectpsg.commandlibrary.feature.shell.presentation.Problem
import com.architectpsg.commandlibrary.feature.shell.presentation.activeGeneration
import com.architectpsg.commandlibrary.feature.shell.presentation.installed

@Composable
internal fun CatalogScreen(
    statusState: CatalogStatusUiState,
    importState: ImportUiState,
    limits: CatalogImportLimits,
    widthClass: WidthClass,
    pickerUnavailable: Boolean,
    onPickFile: () -> Unit,
    onCancelImport: () -> Unit,
    onDismissImport: () -> Unit,
    onRetryStatus: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDownloads: (() -> Unit)? = null,
) {
    val installed = statusState.status?.installed == true
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        val sectionModifier = Modifier.padding(horizontal = widthClass.gutter, vertical = 16.dp)
        if (widthClass == WidthClass.EXPANDED) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusSection(statusState, onRetryStatus, sectionModifier.weight(1f), onOpenDownloads)
                ImportSection(importState, limits, installed, pickerUnavailable, onPickFile, onCancelImport, onDismissImport, sectionModifier.weight(1f))
            }
        } else {
            Column(Modifier.widthIn(max = ReadingWidth).fillMaxWidth()) {
                StatusSection(statusState, onRetryStatus, sectionModifier, onOpenDownloads)
                ImportSection(importState, limits, installed, pickerUnavailable, onPickFile, onCancelImport, onDismissImport, sectionModifier)
            }
        }
    }
}

@Composable
private fun StatusSection(state: CatalogStatusUiState, onRetry: () -> Unit, modifier: Modifier, onOpenDownloads: (() -> Unit)?) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(stringResource(R.string.catalog_title), style = MaterialTheme.typography.headlineMedium)
        if (onOpenDownloads != null) OutlinedButton(onClick = onOpenDownloads,
            modifier = Modifier.heightIn(min = 48.dp).testTag("catalog-downloads-open")) { Text(stringResource(R.string.downloads_title)) }
        val status = state.status
        if (status == null && state.loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.padding(4.dp))
                Text(stringResource(R.string.catalog_status_loading), style = MaterialTheme.typography.bodyLarge)
            }
        }
        state.problem?.let { problem ->
            MessageBlock(
                title = stringResource(R.string.catalog_status_failed),
                body = listOf(stringResource(problemText(problem))),
                tone = MessageTone.ERROR,
            ) {
                OutlinedButton(onClick = onRetry, enabled = !state.loading) { Text(stringResource(R.string.shell_action_retry)) }
            }
        }
        if (status != null) {
            if (status.installed) InstalledStatus(status) else EmptyStatus(status)
        }
    }
}

@Composable
private fun EmptyStatus(status: LocalCatalogStatus) {
    MessageBlock(
        title = stringResource(R.string.catalog_empty_title),
        body = listOf(stringResource(R.string.catalog_empty_body)),
        tone = MessageTone.INFO,
        announce = false,
    )
    DetailField(stringResource(R.string.catalog_field_default_language), languageName(status.defaultLanguage))
}

@Composable
private fun InstalledStatus(status: LocalCatalogStatus) {
    val generation = status.activeGeneration()
    DetailField(stringResource(R.string.catalog_field_source), knowledgeText(status.source) { it.value })
    DetailField(stringResource(R.string.catalog_field_source_date), knowledgeText(status.sourceDate) { formatTimestamp(it) })
    if (status.sourceDate !is Knowledge.Known) {
        Text(
            text = stringResource(R.string.catalog_source_date_unknown_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    DetailField(stringResource(R.string.catalog_field_imported), knowledgeText(status.fetchedAt) { formatTimestamp(it) })
    if (generation != null) {
        DetailField(stringResource(R.string.catalog_field_cards), formatCount(generation.counts.oracleCards))
        DetailField(stringResource(R.string.catalog_field_printings), formatCount(generation.counts.printings))
        DetailField(stringResource(R.string.catalog_field_faces), formatCount(generation.counts.faces))
        DetailField(stringResource(R.string.catalog_field_format), generation.format)
        DetailField(stringResource(R.string.catalog_field_generation), generation.id.value)
        generation.provenance.checksum?.let { DetailField(stringResource(R.string.catalog_field_checksum), it.hex.lowercase()) }
    }
    val separator = stringResource(R.string.detail_list_separator)
    DetailField(
        stringResource(R.string.catalog_field_languages),
        if (status.installedLanguages.isEmpty()) stringResource(R.string.shell_value_none) else status.installedLanguages.map { languageName(it) }.joinToString(separator),
    )
    DetailField(stringResource(R.string.catalog_field_default_language), languageName(status.defaultLanguage))
}

@Composable
private fun ImportSection(
    state: ImportUiState,
    limits: CatalogImportLimits,
    installed: Boolean,
    pickerUnavailable: Boolean,
    onPickFile: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(stringResource(R.string.import_title))
        Text(stringResource(R.string.import_body), style = MaterialTheme.typography.bodyLarge)
        if (installed) Text(stringResource(R.string.import_replace_note), style = MaterialTheme.typography.bodyLarge)
        ImportLimits(limits)
        when (state) {
            is ImportUiState.Running -> ImportProgress(state, onCancel)
            is ImportUiState.Succeeded -> ImportSucceeded(state, onPickFile, onDismiss)
            is ImportUiState.Failed -> ImportFailed(state, limits, onPickFile, onDismiss)
            is ImportUiState.Cancelled -> ImportCancelled(state, onPickFile, onDismiss)
            ImportUiState.Idle -> Button(onClick = onPickFile, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.import_action_choose))
            }
        }
        if (pickerUnavailable) {
            MessageBlock(title = null, body = listOf(stringResource(R.string.import_picker_unavailable)), tone = MessageTone.ERROR)
        }
    }
}

@Composable
private fun ImportLimits(limits: CatalogImportLimits) {
    Column(Modifier.semantics(mergeDescendants = true) {}.padding(vertical = 4.dp)) {
        Text(stringResource(R.string.import_limits_title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        val lines = listOf(
            stringResource(R.string.import_limit_size, formatBytes(limits.maxInputBytes)),
            stringResource(R.string.import_limit_records, formatCount(limits.maxPrintingRecords.toLong())),
            stringResource(R.string.import_limit_record_size, formatBytes(limits.maxRecordBytes.toLong())),
            stringResource(R.string.import_limit_nesting, limits.maxNesting),
            stringResource(R.string.import_limit_language),
        )
        lines.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ImportProgress(state: ImportUiState.Running, onCancel: () -> Unit) {
    val description = stringResource(R.string.import_progress_description)
    Column(Modifier.fillMaxWidth().testTag(ShellTags.IMPORT_PROGRESS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MessageBlock(
            title = state.fileName?.let { stringResource(R.string.import_running_title, it) },
            body = listOf(
                if (state.fileName == null) stringResource(R.string.import_running_opening) else stringResource(phaseText(state.phase)),
            ),
            tone = MessageTone.INFO,
        )
        val reported = state.reportedBytes
        if (reported != null && reported > 0) {
            LinearProgressIndicator(
                progress = { (state.bytesRead.toFloat() / reported).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = description })
        }
        Text(
            text = if (reported != null) {
                stringResource(R.string.import_progress_read_of, formatBytes(state.bytesRead), formatBytes(reported))
            } else {
                stringResource(R.string.import_progress_read, formatBytes(state.bytesRead))
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        state.recordsStaged?.let { staged ->
            Text(
                pluralStringResource(R.plurals.import_records_saved, staged.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), formatCount(staged)),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        OutlinedButton(
            onClick = onCancel,
            enabled = !state.cancelling,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(if (state.cancelling) R.string.import_cancelling else R.string.import_action_cancel))
        }
    }
}

private fun phaseText(phase: JobPhase?): Int = when (phase) {
    null, JobPhase.QUEUED -> R.string.import_phase_queued
    JobPhase.DOWNLOADING -> R.string.import_phase_downloading
    JobPhase.STAGING -> R.string.import_phase_staging
    JobPhase.VALIDATING -> R.string.import_phase_validating
    JobPhase.ACTIVATING -> R.string.import_phase_activating
    JobPhase.COMPLETED -> R.string.import_phase_completed
    JobPhase.FAILED -> R.string.import_phase_failed
    JobPhase.CANCELLED -> R.string.import_phase_cancelled
}

@Composable
private fun ImportSucceeded(state: ImportUiState.Succeeded, onPickFile: () -> Unit, onDismiss: () -> Unit) {
    val result = state.result
    MessageBlock(
        title = stringResource(R.string.import_succeeded_title),
        body = listOf(stringResource(R.string.import_succeeded_body, state.fileName)),
        tone = MessageTone.INFO,
    ) {
        Button(onClick = onPickFile, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.import_action_choose))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.shell_action_dismiss))
        }
    }
    DetailField(stringResource(R.string.catalog_field_cards), formatCount(result.counts.oracleCards))
    DetailField(stringResource(R.string.catalog_field_printings), formatCount(result.counts.printings))
    DetailField(stringResource(R.string.catalog_field_faces), formatCount(result.counts.faces))
    DetailField(stringResource(R.string.import_result_bytes), formatBytes(result.verification.observedBytes))
    if (result.verification.rejectedRecords > 0) {
        DetailField(stringResource(R.string.import_result_rejected), formatCount(result.verification.rejectedRecords))
    }
    DetailField(stringResource(R.string.catalog_field_checksum), result.verification.computedChecksum.hex.lowercase())
    Text(
        text = stringResource(
            when (result.verification.checksumVerification) {
                ChecksumVerification.MATCHED_EXPECTED -> R.string.import_result_checksum_matched
                ChecksumVerification.COMPUTED_WITHOUT_EXPECTATION -> R.string.import_result_checksum_computed
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ImportFailed(state: ImportUiState.Failed, limits: CatalogImportLimits, onPickFile: () -> Unit, onDismiss: () -> Unit) {
    val reason = when (val failure = state.failure) {
        is ImportFailure.TooLarge -> {
            val limit = formatBytes(failure.limitBytes)
            val reported = failure.reportedBytes
            if (reported != null && reported > limits.maxInputBytes) {
                stringResource(R.string.import_failed_too_large_reported, formatBytes(reported), limit)
            } else {
                stringResource(R.string.import_failed_too_large, limit)
            }
        }
        ImportFailure.ReadFailed -> stringResource(R.string.import_failed_read)
        ImportFailure.PermissionDenied -> stringResource(R.string.import_failed_permission)
        is ImportFailure.Rejected -> when (failure.problem) {
            Problem.INVALID_INPUT -> stringResource(R.string.import_failed_invalid)
            Problem.SOURCE_MISMATCH -> stringResource(R.string.import_failed_source_mismatch)
            Problem.PERSISTENCE -> stringResource(R.string.import_failed_storage)
            else -> stringResource(problemText(failure.problem))
        }
    }
    MessageBlock(
        title = stringResource(R.string.import_failed_title),
        body = listOf(reason, stringResource(R.string.import_failed_unchanged)),
        tone = MessageTone.ERROR,
    ) {
        Button(onClick = onPickFile, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.import_action_choose))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.shell_action_dismiss))
        }
    }
}

@Composable
private fun ImportCancelled(state: ImportUiState.Cancelled, onPickFile: () -> Unit, onDismiss: () -> Unit) {
    MessageBlock(
        title = stringResource(R.string.import_cancelled_title),
        body = listOf(stringResource(R.string.import_cancelled_body, state.fileName ?: stringResource(R.string.import_unnamed_file))),
        tone = MessageTone.INFO,
    ) {
        Button(onClick = onPickFile, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.import_action_choose))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.shell_action_dismiss))
        }
    }
}
