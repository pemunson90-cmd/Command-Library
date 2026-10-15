package com.architectpsg.commandlibrary.feature.shell.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*

@Composable
internal fun AdvancedQueryScreen(controller: AdvancedQueryController, widthClass: WidthClass, onBack: () -> Unit,
    onUseCurrent: () -> Unit, onOpenCatalog: () -> Unit, modifier: Modifier = Modifier,
    rulings: RulingsController? = null, printings: PrintingSelectionController? = null,
    onLookupKeyword: ((String) -> Unit)? = null, onAddPrinting: ((PrintingRecord) -> Unit)? = null) {
    val state by controller.state.collectAsState()
    val split = widthClass == WidthClass.EXPANDED && LocalDensity.current.fontScale < 1.5f
    BackHandler { if (state.selected != null) controller.clearSelection() else onBack() }
    val pane: @Composable (Modifier) -> Unit = { paneModifier ->
        LazyColumn(paneModifier.testTag("advanced-query-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                TextButton(onBack, modifier = Modifier.heightIn(min = 48.dp).testTag("advanced-query-back")) { Text(stringResource(R.string.advanced_query_back)) }
                SectionHeading(stringResource(R.string.advanced_query_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.advanced_query_local))
                state.sourceVersion?.let { Text(stringResource(R.string.advanced_query_source_version, it)) }
                ActionRow {
                    OutlinedButton(onUseCurrent, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("advanced-query-current")) { Text(stringResource(R.string.advanced_query_current)) }
                    if (state.snapshot == null) OutlinedButton(onOpenCatalog, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.advanced_query_import)) }
                }
            }
            item {
                OutlinedTextField(state.input, controller::input, label = { Text(stringResource(R.string.advanced_query_input)) },
                    isError = state.diagnostics.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("advanced-query-input"))
                Text(stringResource(R.string.advanced_query_example))
                Text(stringResource(R.string.advanced_query_printing_scope))
                Text(stringResource(R.string.advanced_query_categories))
            }
            item {
                ActionRow {
                    FaceMatchPolicy.entries.forEach { policy -> FilterChip(state.policy == policy, { controller.policy(policy) },
                        label = { Text(stringResource(when (policy) {
                            FaceMatchPolicy.ANY_FACE -> R.string.advanced_query_any_face
                            FaceMatchPolicy.SAME_FACE -> R.string.advanced_query_same_face
                            FaceMatchPolicy.CANONICAL -> R.string.advanced_query_canonical
                        })) }, modifier = Modifier.heightIn(min = 48.dp).testTag("advanced-query-policy-${policy.name}")) }
                }
                Text(stringResource(when (state.policy) {
                    FaceMatchPolicy.ANY_FACE -> R.string.advanced_query_any_face_help
                    FaceMatchPolicy.SAME_FACE -> R.string.advanced_query_same_face_help
                    FaceMatchPolicy.CANONICAL -> R.string.advanced_query_canonical_help
                }))
            }
            item {
                val ready = state.preparation?.readiness == CatalogSearchPreparationReadiness.READY
                Text(stringResource(when (state.preparation?.readiness) {
                    CatalogSearchPreparationReadiness.READY -> R.string.advanced_query_ready
                    CatalogSearchPreparationReadiness.FAILED -> R.string.advanced_query_prepare_failed
                    CatalogSearchPreparationReadiness.PREPARING -> R.string.advanced_query_prepare_partial
                    else -> R.string.advanced_query_prepare_needed
                }), modifier = Modifier.testTag("advanced-query-readiness"))
                state.preparation?.processed?.let { Text(stringResource(R.string.advanced_query_progress, it.cards, it.printings)) }
                ActionRow {
                    Button(controller::submit, enabled = ready && !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("advanced-query-submit")) { Text(stringResource(R.string.advanced_query_search)) }
                    if (!ready) OutlinedButton(controller::prepare, enabled = state.snapshot != null && !state.busy,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("advanced-query-prepare")) { Text(stringResource(R.string.advanced_query_prepare)) }
                    OutlinedButton(controller::refreshPreparation, enabled = state.snapshot != null && !state.busy,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("advanced-query-refresh")) { Text(stringResource(R.string.advanced_query_refresh)) }
                    if (state.busy) OutlinedButton(controller::stop, modifier = Modifier.heightIn(min = 48.dp).testTag("advanced-query-stop")) { Text(stringResource(R.string.advanced_query_stop)) }
                }
                if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(if (state.preparing) R.string.advanced_query_preparing else R.string.advanced_query_working)) }
            }
            state.problem?.let { problem -> item {
                Text(stringResource(problemText(problem)), modifier = Modifier.testTag("advanced-query-problem"))
                if (problem == Problem.UNSUPPORTED) Text(stringResource(R.string.advanced_query_unsupported))
            } }
            items(state.diagnostics, key = { "${it.start}:${it.end}" }) { diagnostic ->
                Text(stringResource(R.string.advanced_query_diagnostic, diagnostic.start, diagnostic.end), modifier = Modifier.testTag("advanced-query-diagnostic"))
            }
            if (!state.busy && state.problem == null && state.page?.items?.isEmpty() == true)
                item { Text(stringResource(R.string.advanced_query_empty), modifier = Modifier.testTag("advanced-query-empty")) }
            items(state.page?.items.orEmpty(), key = { "${it.oracleId.source.value}:${it.oracleId.raw}" }) { card ->
                OutlinedButton({ controller.select(card) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("advanced-query-card-${card.oracleId.raw}")) { Text(card.name) }
            }
            if (state.page?.nextCursor != null) item {
                OutlinedButton(controller::next, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("advanced-query-next")) { Text(stringResource(R.string.advanced_query_next)) }
            }
        }
    }
    val detail: @Composable (Modifier) -> Unit = { detailModifier ->
        state.selected?.let { card -> CardDetail(card, controller::clearSelection, widthClass, detailModifier,
            rulings, printings, onOpenCatalog, onLookupKeyword, onAddPrinting) }
            ?: Text(stringResource(R.string.detail_select_prompt), modifier = detailModifier.padding(16.dp))
    }
    if (split) Row(modifier.fillMaxSize()) {
        pane(Modifier.weight(0.45f).fillMaxHeight()); VerticalDivider(); detail(Modifier.weight(0.55f).fillMaxHeight())
    } else if (state.selected != null) detail(modifier.fillMaxSize()) else pane(modifier.fillMaxSize())
}
