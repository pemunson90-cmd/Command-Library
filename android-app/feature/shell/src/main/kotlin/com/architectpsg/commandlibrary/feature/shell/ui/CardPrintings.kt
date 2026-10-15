package com.architectpsg.commandlibrary.feature.shell.ui

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*

/** No composition-owned selection/read jobs: moving this panel across panes preserves its state. */
@Composable
internal fun CardPrintings(card: CardRecord, controller: PrintingSelectionController, onAddPrinting: ((PrintingRecord) -> Unit)? = null) {
    val state by controller.state.collectAsState()
    val browser = LocalUriHandler.current
    FeaturePanel(modifier = Modifier.testTag("card-printings")) {
        SectionHeading(stringResource(R.string.printings_title))
        if (state.card != card || state.loading) {
            Text(stringResource(R.string.printings_loading))
        } else {
            state.problem?.let { problem ->
                MessageBlock(stringResource(R.string.printings_failed), listOf(stringResource(problemText(problem))), MessageTone.ERROR)
                if (problem == Problem.STALE_GENERATION) Text(stringResource(R.string.printings_reload_search))
            }
            if (state.problem == null) {
                if (state.page?.items?.isEmpty() == true) Text(stringResource(R.string.printings_empty))
                state.page?.items?.forEach { printing ->
                    FilterChip(
                        selected = state.selected?.id == printing.id,
                        onClick = { controller.selectPrinting(printing.id) },
                        label = { Text(stringResource(R.string.printings_choice, printing.setCode, printing.collectorNumber, printing.language.value)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("printing-${printing.id.raw}"),
                    )
                }
                state.selected?.let { printing ->
                    HorizontalDivider()
                    DetailField(stringResource(R.string.printings_identity), stringResource(R.string.printings_qualified_id, printing.id.source.value, printing.id.raw))
                    DetailField(stringResource(R.string.printings_set), knowledgeText(printing.setName) { it })
                    val separator = stringResource(R.string.detail_list_separator)
                    val finishText = when (val finishes = printing.finishes) {
                        is Knowledge.Known -> finishes.value.map { finish ->
                            stringResource(when (finish) {
                                Finish.NONFOIL -> R.string.printings_nonfoil
                                Finish.FOIL -> R.string.printings_foil
                                Finish.ETCHED -> R.string.printings_etched
                            })
                        }.joinToString(separator).ifEmpty { stringResource(R.string.shell_value_none) }
                        else -> knowledgeText(finishes) { "" }
                    }
                    DetailField(stringResource(R.string.printings_finishes), finishText)
                    if (onAddPrinting != null && (printing.finishes as? Knowledge.Known)?.value?.isNotEmpty() == true) {
                        TextButton(onClick = { controller.withCurrentPrinting(state.ticket, printing.id, onAddPrinting) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("printing-add-collection")) {
                            Text(stringResource(R.string.printings_add_collection))
                        }
                    }
                    if (printing.oracleId !is Knowledge.Known) Text(stringResource(R.string.printings_relation_unknown))
                    if (state.linksLoading) Text(stringResource(R.string.printings_links_loading))
                    state.links.forEach { link ->
                        SectionHeading(stringResource(targetLabel(link.target)))
                        when (val result = link.result) {
                            is PortResult.Failed -> Text(stringResource(if (result.problem == Problem.NOT_FOUND) R.string.printings_link_missing else problemText(result.problem)))
                            is PortResult.Ok -> {
                                val resolved = result.value
                                SelectionContainer { Text(resolved.uri.value) }
                                resolved.provenance?.let { provenance ->
                                    DetailField(stringResource(R.string.printings_link_source), provenance.provider.value)
                                    DetailField(stringResource(R.string.printings_link_source_date), knowledgeText(provenance.sourceDate) { formatTimestamp(it) })
                                    DetailField(stringResource(R.string.printings_link_retrieved), formatTimestamp(provenance.fetchedAt))
                                }
                                if (resolved.kind == LinkResolutionKind.TEMPLATE) Text(stringResource(R.string.printings_template_warning))
                                if (link.target == "cardmarket") Text(stringResource(R.string.printings_cardmarket_warning))
                                TextButton(onClick = {
                                    controller.openLink(state.ticket, link.target) { uri ->
                                        try { browser.openUri(uri.value) }
                                        catch (_: ActivityNotFoundException) { controller.launchFailed(state.ticket) }
                                        catch (_: SecurityException) { controller.launchFailed(state.ticket) }
                                        catch (_: IllegalArgumentException) { controller.launchFailed(state.ticket) }
                                    }
                                }, modifier = Modifier.heightIn(min = 48.dp).testTag("printing-link-${link.target}")) {
                                    Text(stringResource(R.string.printings_open, stringResource(targetLabel(link.target))))
                                }
                            }
                        }
                    }
                    if (state.launchFailed) Text(stringResource(R.string.printings_launch_failed), color = MaterialTheme.colorScheme.error)
                }
            }
            ActionRow {
                if (state.page?.nextCursor != null) OutlinedButton(onClick = controller::next,
                    enabled = !state.loading && state.problem == null, modifier = Modifier.heightIn(min = 48.dp).testTag("printings-next")) {
                    Text(stringResource(R.string.printings_next))
                }
                OutlinedButton(onClick = controller::reload, enabled = !state.loading,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("printings-reload")) { Text(stringResource(R.string.printings_reload)) }
            }
        }
    }
}

private fun targetLabel(target: String): Int = when (target) {
    "scryfall" -> R.string.printings_scryfall
    "gatherer" -> R.string.printings_gatherer
    "edhrec" -> R.string.printings_edhrec
    "spellbook" -> R.string.printings_spellbook
    "tcgplayer" -> R.string.printings_tcgplayer
    "cardmarket" -> R.string.printings_cardmarket
    "cardkingdom" -> R.string.printings_cardkingdom
    else -> R.string.printings_cardhoarder
}
