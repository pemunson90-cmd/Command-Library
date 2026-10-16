package com.architectpsg.commandlibrary.feature.shell.ui.decks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.BasicDeckValidationController
import com.architectpsg.commandlibrary.feature.shell.ui.problemText
import com.architectpsg.commandlibrary.feature.shell.ui.knowledgeText
import com.architectpsg.commandlibrary.feature.shell.ui.formatTimestamp

@Composable
internal fun BasicDeckValidationDialog(controller: BasicDeckValidationController) {
    val state by controller.state.collectAsState()
    val deck = state.deck ?: return
    val frame = state
    val action: (() -> Unit) -> Unit = { controller.rendered(frame, it) }
    Dialog(onDismissRequest = { action(controller::close) }) {
        Surface(shape = MaterialTheme.shapes.large) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 720.dp).padding(16.dp).testTag("basic-validation-list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(stringResource(R.string.basic_validation_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.basic_validation_capture, deck.name, deck.revision.value, state.snapshot?.generation?.value.orEmpty()))
                    Text(stringResource(R.string.basic_validation_scope))
                    Text(stringResource(R.string.basic_validation_choose))
                    FilterChip(state.format == BasicDeckFormat.COMMANDER,
                        { action { controller.format(BasicDeckFormat.COMMANDER) } },
                        label = { Text(stringResource(R.string.basic_validation_commander)) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("basic-validation-commander"))
                    Button({ action(controller::assess) }, enabled = state.format != null && !state.busy,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("basic-validation-assess")) {
                        Text(stringResource(R.string.basic_validation_assess))
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.problem?.let { Text(stringResource(problemText(it)), color = MaterialTheme.colorScheme.error) }
                }
                state.result?.let { result ->
                    item {
                        Text(stringResource(validationStatusText(result.status)), Modifier.testTag("basic-validation-status"), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.basic_validation_rules, result.rulesVersion))
                    }
                    result.checks.forEach { check -> item {
                        Text(check.message)
                        if (check.entryIds.isNotEmpty()) Text(stringResource(R.string.basic_validation_entries,
                            check.entryIds.joinToString(", ") { id ->
                                val entry = deck.entries.firstOrNull { it.id == id }
                                (entry?.identity?.originalName as? Knowledge.Known)?.value ?: id.value
                            }))
                        Text(stringResource(validationStatusText(check.status)), Modifier.testTag("basic-validation-check-${check.id}"))
                        check.ruleReferences.forEach { Text(stringResource(R.string.basic_validation_reference, it)) }
                    } }
                    item { Text(stringResource(R.string.basic_validation_source), style = MaterialTheme.typography.titleMedium) }
                    result.sourceLegalities.forEach { source -> item {
                        val matching = deck.entries.firstOrNull { (it.identity.oracleId as? Knowledge.Known)?.value == source.oracleId }
                        val name = (matching?.identity?.originalName as? Knowledge.Known)?.value ?: source.oracleId.raw
                        val status = (source.status as? Knowledge.Known)?.value ?: LegalityStatus.UNKNOWN
                        val sourceStatus = when (status) {
                            LegalityStatus.LEGAL -> R.string.basic_validation_source_legal
                            LegalityStatus.NOT_LEGAL -> R.string.basic_validation_source_not_legal
                            else -> validationStatusText(status)
                        }
                        Text(stringResource(R.string.basic_validation_source_row, name, stringResource(sourceStatus)))
                        Text(stringResource(R.string.basic_validation_source_date,
                            knowledgeText(source.provenance.sourceDate) { formatTimestamp(it) }))
                    } }
                }
                item { TextButton({ action(controller::close) }, modifier = Modifier.heightIn(min = 48.dp).testTag("basic-validation-close")) {
                    Text(stringResource(R.string.basic_validation_close))
                } }
            }
        }
    }
}

private fun validationStatusText(status: LegalityStatus): Int = when (status) {
    LegalityStatus.LEGAL -> R.string.basic_validation_legal
    LegalityStatus.NOT_LEGAL -> R.string.basic_validation_not_legal
    LegalityStatus.BANNED -> R.string.basic_validation_banned
    LegalityStatus.RESTRICTED -> R.string.basic_validation_restricted
    LegalityStatus.UNKNOWN -> R.string.basic_validation_unknown
    LegalityStatus.REVIEW_REQUIRED -> R.string.basic_validation_review
}
