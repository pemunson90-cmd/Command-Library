package com.architectpsg.commandlibrary.feature.shell.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.ConsentReviewController

/** At most five stored approvals are composed per page; record detail requires an explicit review. */
@Composable
internal fun ConsentReviewPanel(controller: ConsentReviewController) {
    val state by controller.state.collectAsState()
    var page by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(controller) { controller.load() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.close()
            if (event == Lifecycle.Event.ON_START) controller.load()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); controller.close() }
    }
    val records = state.snapshot?.consents.orEmpty().asReversed()
    val lastPage = ((records.size - 1).coerceAtLeast(0)) / 5
    val shownPage = page.coerceIn(0, lastPage)
    FeaturePanel(modifier = Modifier.testTag("consent-review-panel")) {
        SectionHeading(stringResource(R.string.consent_review_title))
        Text(stringResource(R.string.consent_review_body))
        OutlinedButton(controller::load, enabled = !state.loading && state.pending == null,
            modifier = Modifier.heightIn(min = 48.dp).testTag("consent-review-refresh")) {
            Text(stringResource(R.string.consent_review_refresh))
        }
        if (state.loading) Text(stringResource(R.string.settings_loading))
        if (state.changedElsewhere) Text(stringResource(R.string.settings_changed_elsewhere))
        state.problem?.let {
            MessageBlock(null, listOf(stringResource(problemText(it))), MessageTone.ERROR)
        }
        if (state.snapshot != null && records.isEmpty()) Text(stringResource(R.string.consent_review_empty))
        records.drop(shownPage * 5).take(5).forEach { record ->
            HorizontalDivider()
            Text(record.intent.source.value, style = MaterialTheme.typography.titleSmall)
            Text(record.intent.resource.value, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(stringResource(if (record.revokedAt == null) R.string.consent_active else R.string.consent_revoked))
            if (record.revokedAt == null) OutlinedButton({ controller.review(record.id) },
                enabled = !state.loading && state.pending == null,
                modifier = Modifier.heightIn(min = 48.dp).testTag("consent-review-${record.id.value}")) {
                Text(stringResource(R.string.consent_review_revoke))
            }
        }
        if (records.size > 5) ActionRow {
            TextButton({ page = shownPage - 1 }, enabled = shownPage > 0) { Text(stringResource(R.string.consent_previous)) }
            Text(stringResource(R.string.consent_page, shownPage + 1, lastPage + 1))
            TextButton({ page = shownPage + 1 }, enabled = shownPage < lastPage) { Text(stringResource(R.string.consent_next)) }
        }
        if (state.pending != null) Text(stringResource(R.string.settings_saving))
    }
    state.review?.let { record ->
        val ticket = state.reviewTicket
        AlertDialog(onDismissRequest = { controller.dismiss(ticket) },
            title = { Text(stringResource(R.string.consent_revoke_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(record.intent.source.value)
                Text(record.intent.resource.value)
                Text(stringResource(R.string.consent_revoke_body))
            } },
            confirmButton = { TextButton({ controller.revoke(ticket, record) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("consent-revoke-confirm")) { Text(stringResource(R.string.consent_revoke_confirm)) } },
            dismissButton = { TextButton({ controller.dismiss(ticket) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.artwork_cancel))
            } })
    }
}
