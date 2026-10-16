package com.architectpsg.commandlibrary.feature.shell.ui.decks

import androidx.compose.foundation.background
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
import com.architectpsg.commandlibrary.feature.shell.presentation.DeckStatisticsController
import com.architectpsg.commandlibrary.feature.shell.ui.problemText

@Composable
internal fun DeckStatisticsDialog(controller: DeckStatisticsController) {
    val state by controller.state.collectAsState()
    val deck = state.deck ?: return
    val renderedState = state
    val action: (() -> Unit) -> Unit = { controller.rendered(renderedState, it) }
    Dialog(onDismissRequest = { action(controller::close) }) {
        Surface(shape = MaterialTheme.shapes.large) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 720.dp).padding(16.dp).testTag("deck-statistics-list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(stringResource(R.string.deck_statistics_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.deck_statistics_capture, deck.name, deck.revision.value, state.snapshot?.generation?.value.orEmpty()))
                    Text(stringResource(R.string.deck_statistics_policy))
                }
                BoardRole.entries.forEach { board -> item {
                    FilterChip(selected = board in state.boards, onClick = { action { controller.board(board) } },
                        label = { Text(stringResource(boardLabel(board))) }, modifier = Modifier.heightIn(min = 48.dp).testTag("deck-statistics-board-${board.name}"))
                } }
                item {
                    Text(stringResource(R.string.deck_statistics_tag_filter))
                    Row {
                        FilterChip(state.allTags, { action { controller.allTags(true) } }, label = { Text(stringResource(R.string.deck_statistics_all_tags)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("deck-statistics-all-tags"))
                        FilterChip(!state.allTags, { action { controller.allTags(false) } }, label = { Text(stringResource(R.string.deck_statistics_any_tag)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("deck-statistics-any-tag"))
                    }
                }
                val labels = deck.categoryOrder ?: SnapshotList.copyOf(deck.entries.flatMap { it.labels }.distinctBy { it.id })
                labels.forEach { label -> item {
                    FilterChip(label.id in state.tags, { action { controller.tag(label.id) } }, label = { Text(label.name) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("deck-statistics-tag-${label.id.value}"))
                } }
                item {
                    FilterChip(state.restrictEntries, { action { controller.restrictEntries(!state.restrictEntries) } },
                        label = { Text(stringResource(R.string.deck_statistics_selected_entries)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("deck-statistics-selected-mode"))
                }
                if (state.restrictEntries) deck.entries.forEach { entry -> item {
                    FilterChip(entry.id in state.entries, { action { controller.entry(entry.id) } },
                        label = { Text((entry.identity.originalName as? Knowledge.Known)?.value ?: entry.id.value) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("deck-statistics-entry-${entry.id.value}"))
                } }
                item {
                    Button({ action(controller::compute) }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("deck-statistics-compute")) { Text(stringResource(R.string.deck_statistics_compute)) }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.problem?.let { Text(stringResource(problemText(it)), color = MaterialTheme.colorScheme.error) }
                }
                state.result?.let { result ->
                    item {
                        Text(stringResource(R.string.deck_statistics_totals, result.copies, result.entryIds.size, result.unresolvedCopies), Modifier.testTag("deck-statistics-totals"))
                        Text(stringResource(R.string.deck_statistics_unknowns, result.unknownManaValueCopies, result.unknownTypeCopies, result.unknownCostCopies))
                        Text(stringResource(R.string.deck_statistics_average, statisticAverage(result.averageManaValue), result.averageManaValue.copies))
                        Text(stringResource(R.string.deck_statistics_nonland_average, statisticAverage(result.averageNonlandManaValue), result.averageNonlandManaValue.copies))
                        Text(stringResource(R.string.deck_statistics_curve), style = MaterialTheme.typography.titleMedium)
                    }
                    val maximum = result.manaCurve.maxOfOrNull { it.copies } ?: 0L
                    result.manaCurve.forEach { bucket -> item {
                        Text(stringResource(R.string.deck_statistics_pair, bucket.manaValue.toPlainString(), bucket.copies))
                        if (maximum > 0) Box(Modifier.fillMaxWidth((bucket.copies.toDouble() / maximum.toDouble()).toFloat().coerceIn(0f, 1f)).height(8.dp).background(MaterialTheme.colorScheme.primary))
                    } }
                    item { Text(stringResource(R.string.deck_statistics_types), style = MaterialTheme.typography.titleMedium) }
                    result.types.forEach { row -> item { Text(stringResource(R.string.deck_statistics_pair, row.name, row.copies)) } }
                    item { Text(stringResource(R.string.deck_statistics_symbols), style = MaterialTheme.typography.titleMedium) }
                    result.costSymbols.forEach { row -> item { Text(stringResource(R.string.deck_statistics_pair, row.name, row.copies)) } }
                    item {
                        Text(stringResource(R.string.deck_statistics_production), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.deck_statistics_production_caveat, result.unknownLandProductionCopies))
                    }
                    result.reportedLandProduction.forEach { row -> item { Text(stringResource(R.string.deck_statistics_pair, row.name, row.copies)) } }
                    item { Text(stringResource(R.string.deck_statistics_roles), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.deck_statistics_overlap)) }
                    result.tags.forEach { row -> item { Text(stringResource(R.string.deck_statistics_coverage, row.label.name, row.entries, row.copies)) } }
                    item {
                        Text(stringResource(R.string.deck_statistics_tokens), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.deck_statistics_token_caveat, result.unknownTokenReferenceCopies))
                    }
                    result.tokenReferences.forEach { part -> item {
                        Text((part.name as? Knowledge.Known)?.value ?: part.printingId.raw)
                    } }
                    result.issues.forEach { issue -> item { Text(issue.message) } }
                }
                item { TextButton({ action(controller::close) }, modifier = Modifier.heightIn(min = 48.dp).testTag("deck-statistics-close")) { Text(stringResource(R.string.manual_tags_close)) } }
            }
        }
    }
}
@Composable
private fun statisticAverage(average: StatisticAverage): String = (average.value as? Knowledge.Known)?.value?.toPlainString() ?: stringResource(R.string.deck_statistics_unknown)
