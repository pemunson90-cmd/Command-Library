package com.architectpsg.commandlibrary.feature.shell.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.OracleId
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.RulingsController

internal object RulingTags {
    const val PANEL = "card-rulings"
    const val NEXT = "card-rulings-next"
    const val RELOAD = "card-rulings-reload"
    const val MANAGE = "card-rulings-manage"
}

/** Pure local page, literal publication day/comment and distinct actual retrieval provenance. */
@Composable
internal fun CardRulings(oracle: OracleId, controller: RulingsController, onOpenCatalog: (() -> Unit)?) {
    val state by controller.state.collectAsState()
    LaunchedEffect(oracle, controller) { controller.select(oracle) }
    DisposableEffect(controller) { onDispose { controller.close() } }
    FeaturePanel(modifier = Modifier.testTag(RulingTags.PANEL)) {
        SectionHeading(stringResource(R.string.rulings_title))
        if(state.oracle != oracle || state.loading) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(24.dp)); Text(stringResource(R.string.rulings_loading)) }
        }
        if(state.oracle == oracle) {
            state.problem?.let { MessageBlock(stringResource(R.string.rulings_failed), listOf(stringResource(problemText(it))), MessageTone.ERROR) }
            if(!state.loading && state.problem == null && state.status != null) {
                if(state.status?.active == null) {
                    Text(stringResource(if(onOpenCatalog == null) R.string.rulings_absent else R.string.rulings_not_installed))
                    if(onOpenCatalog != null) OutlinedButton(onClick = onOpenCatalog, modifier = Modifier.heightIn(min = 48.dp).testTag(RulingTags.MANAGE)) { Text(stringResource(R.string.rulings_manage)) }
                } else {
                    val active = requireNotNull(state.status?.active)
                    DetailField(stringResource(R.string.downloads_source), active.provenance.provider.value)
                    DetailField(stringResource(R.string.rulings_source_updated), knowledgeText(active.provenance.sourceDate) { formatTimestamp(it) })
                    DetailField(stringResource(R.string.rulings_retrieved), formatTimestamp(active.provenance.fetchedAt))
                    Text(stringResource(R.string.rulings_date_note), style = MaterialTheme.typography.bodySmall)
                    val page = state.page
                    if(page?.items?.isEmpty() == true) Text(stringResource(R.string.rulings_no_matches))
                    page?.items?.forEach { ruling ->
                        HorizontalDivider()
                        DetailField(stringResource(R.string.rulings_published), ruling.content.publishedAt.value)
                        DetailField(stringResource(R.string.rulings_publisher), ruling.content.publisher.upstreamValue)
                        SelectionContainer { Text(ruling.content.comment) }
                    }
                }
            }
            ActionRow {
                if(state.page?.nextCursor != null) OutlinedButton(onClick = controller::next, enabled = !state.loading,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(RulingTags.NEXT)) { Text(stringResource(R.string.rulings_next)) }
                OutlinedButton(onClick = controller::refresh, enabled = !state.loading,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(RulingTags.RELOAD)) { Text(stringResource(R.string.rulings_reload)) }
            }
        }
    }
}
