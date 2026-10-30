package com.architectpsg.commandlibrary.feature.shell.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.architectpsg.commandlibrary.core.model.CardRecord
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.CardArtworkController
import com.architectpsg.commandlibrary.feature.shell.presentation.PrintingSelectionController

/** Pixels are supplied by the bounded app adapter. No URL loader or composition-triggered wire. */
@Composable
internal fun CardArtwork(card: CardRecord, controller: CardArtworkController, printings: PrintingSelectionController) {
    val selection by printings.state.collectAsState()
    val state by controller.state.collectAsState()
    val printing = selection.selected.takeIf { selection.card == card && !selection.loading && selection.problem == null }
    val generation = printing?.provenance?.generation
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(controller, printing?.id, generation) { controller.select(printing?.id, generation) }
    DisposableEffect(owner, controller, printing?.id, generation) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.close()
            if (event == Lifecycle.Event.ON_START) controller.select(printing?.id, generation)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); controller.close() }
    }
    // This synchronous selection fence also hides old pixels before a flow collector runs.
    val current = printing != null && state.printing == printing.id && state.generation == generation
    val image = state.image?.takeIf { current && it.offer.printingId == printing?.id && it.offer.generation == generation }
    val attribution = image?.let {
        stringResource(R.string.artwork_attribution, knowledgeText(it.offer.artist) { artist -> artist })
    }
    if (image != null) {
        CardArtworkFrame(attribution = attribution) {
            Image(image.bitmap.asImageBitmap(), contentDescription = stringResource(R.string.artwork_description, card.name),
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().testTag("card-artwork-image"))
        }
    } else CardArtworkFrame()
    if (current) {
        FeaturePanel(modifier = Modifier.testTag("card-artwork-controls")) {
            Text(stringResource(R.string.artwork_memory_only))
            if (state.busy) Text(stringResource(R.string.artwork_loading))
            state.problem?.let {
                MessageBlock(stringResource(R.string.artwork_failed), listOf(stringResource(problemText(it))), MessageTone.ERROR)
            }
            state.offers.forEachIndexed { index, offer ->
                OutlinedButton(onClick = { controller.review(offer) }, enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("card-artwork-load-$index")) {
                    Text(if (offer.faceIndex == null) stringResource(R.string.artwork_load)
                        else stringResource(R.string.artwork_load_face, offer.faceIndex + 1))
                }
            }
        }
        state.approval?.let { offer ->
            val reviewTicket = state.approvalTicket
            AlertDialog(onDismissRequest = { controller.dismissApproval(reviewTicket) },
                title = { Text(stringResource(R.string.artwork_approval_title)) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.artwork_approval_body))
                    Text(offer.intent.resource.value)
                    Text(stringResource(R.string.artwork_memory_only))
                } },
                confirmButton = { TextButton({ controller.approveAndLoad(reviewTicket, offer) }, modifier = Modifier.heightIn(min = 48.dp).testTag("card-artwork-approve")) {
                    Text(stringResource(R.string.artwork_approve))
                } },
                dismissButton = { TextButton({ controller.dismissApproval(reviewTicket) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.artwork_cancel))
                } })
        }
    }
}
