@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import android.graphics.Bitmap
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CardArtworkControllerTest {
    private val source = ProviderId("scryfall")
    private val printing = CardId(source, "synthetic-printing")
    private val generation = GenerationId("synthetic-generation")
    private val offer = CardArtworkOffer(printing, generation, null, "normal", Knowledge.Known("Synthetic Artist"),
        DownloadIntent(EntityId("synthetic-art"), DownloadKind.IMAGES, source,
            ResourceUri("https://cards.scryfall.io/normal/synthetic.jpg"), "synthetic-v1", LanguageTag.ENGLISH,
            null, null, Knowledge.Unknown("synthetic size", SnapshotList.empty())))

    private inner class Access : CardArtworkAccess {
        var prepares = 0; var loads = 0; var allowed = true
        var gate: CompletableDeferred<Unit>? = null
        var prepareGate: CompletableDeferred<Unit>? = null
        var loadOffer = offer
        var permissionFailure = false
        override suspend fun prepare(printingId: CardId, generation: GenerationId, cancel: CancelToken): Outcome<SnapshotList<CardArtworkOffer>> {
            prepares++; prepareGate?.let { withContext(NonCancellable) { it.await() } }
            return Outcome.Success(SnapshotList.of(offer))
        }
        override suspend fun load(offer: CardArtworkOffer, consentId: EntityId, cancel: CancelToken): Outcome<CardArtworkImage> {
            loads++; gate?.let { withContext(NonCancellable) { it.await() } }
            return Outcome.Success(CardArtworkImage(loadOffer, Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)))
        }
        override suspend fun permitted(offer: CardArtworkOffer, consentId: EntityId, cancel: CancelToken): Outcome<Boolean> =
            if (permissionFailure) Outcome.Unknown("synthetic unavailable", SnapshotList.empty()) else Outcome.Success(allowed)
    }
    private inner class Settings : SettingsRepository by SyntheticSettings() {
        var approvals = 0; var fail = false; var gate: CompletableDeferred<Unit>? = null
        override suspend fun approve(intent: DownloadIntent, cancel: CancelToken): Outcome<ConsentRecord> {
            approvals++; gate?.let { withContext(NonCancellable) { it.await() } }
            return if (fail) Outcome.Unavailable(UnavailableReason.CONSENT_REQUIRED, "synthetic")
                else Outcome.Success(ConsentRecord(EntityId("consent"), intent, UtcTimestamp(1), null, true))
        }
    }
    private fun TestScope.controller(a: Access, s: Settings) =
        CardArtworkController(backgroundScope, a, s, StandardTestDispatcher(testScheduler))

    @Test fun selectionAndReviewAreLocalOnlyUntilExplicitConfirmation() = runTest {
        val a = Access(); val s = Settings(); val c = controller(a, s)
        c.select(printing, generation); runCurrent()
        assertEquals(listOf(offer), c.state.value.offers); assertEquals(0, a.loads); assertEquals(0, s.approvals)
        c.review(offer); runCurrent(); assertEquals(offer, c.state.value.approval)
        c.dismissApproval(); runCurrent(); assertNull(c.state.value.approval); assertEquals(0, a.loads)
        c.review(offer); c.approveAndLoad(); runCurrent()
        assertEquals(1, s.approvals); assertEquals(1, a.loads); assertNotNull(c.state.value.image)
        c.select(printing, generation); runCurrent(); assertEquals(1, a.loads)
    }
    @Test fun failedApprovalNeverCallsBinaryAccess() = runTest {
        val a = Access(); val s = Settings().apply { fail = true }; val c = controller(a, s)
        c.select(printing, generation); runCurrent(); c.review(offer); c.approveAndLoad(); runCurrent()
        assertEquals(0, a.loads); assertNull(c.state.value.image); assertEquals(Problem.CONSENT_REQUIRED, c.state.value.problem)
    }
    @Test fun staleOrTamperedOffersCannotEnterApproval() = runTest {
        val a = Access(); val s = Settings(); val c = controller(a, s)
        c.select(printing, generation); runCurrent(); c.review(offer.copy(variant = "grid"))
        c.approveAndLoad(); runCurrent(); assertEquals(0, s.approvals)
        c.select(CardId(source, "other"), generation); runCurrent()
        assertTrue(c.state.value.offers.isEmpty()); assertNull(c.state.value.image)
    }
    @Test fun closeWhileApprovingPreventsALateTransferAndDoesNotReplay() = runTest {
        val a = Access(); val s = Settings().apply { gate = CompletableDeferred() }; val c = controller(a, s)
        c.select(printing, generation); runCurrent(); c.review(offer); c.approveAndLoad(); runCurrent()
        c.close(); s.gate!!.complete(Unit); runCurrent()
        assertEquals(0, a.loads); assertNull(c.state.value.image); assertTrue(c.state.value.offers.isEmpty())
    }
    @Test fun latePrepareAndLateLoadCannotRepopulateClosedSelection() = runTest {
        val a = Access().apply { prepareGate = CompletableDeferred() }; val s = Settings(); val c = controller(a, s)
        c.select(printing, generation); runCurrent(); c.close(); a.prepareGate!!.complete(Unit); runCurrent()
        assertTrue(c.state.value.offers.isEmpty())
        a.prepareGate = null; a.gate = CompletableDeferred()
        c.select(printing, generation); runCurrent(); c.review(offer); c.approveAndLoad(); runCurrent()
        c.close(); a.gate!!.complete(Unit); runCurrent(); assertNull(c.state.value.image)
    }
    @Test fun revocationAndUnknownPermissionClearVisiblePixelsWithoutRetrying() = runTest {
        for (unknown in listOf(false, true)) {
            val a = Access(); val s = Settings(); val c = controller(a, s)
            c.select(printing, generation); runCurrent(); c.review(offer); c.approveAndLoad(); runCurrent()
            assertNotNull(c.state.value.image)
            a.allowed = false; a.permissionFailure = unknown
            advanceTimeBy(300); runCurrent()
            assertNull(c.state.value.image); assertEquals(1, a.loads); c.close()
        }
    }
    @Test fun changedGenerationClearsPixelsAndRejectsPreviousOffer() = runTest {
        val a = Access(); val s = Settings(); val c = controller(a, s)
        c.select(printing, generation); runCurrent(); c.review(offer); c.approveAndLoad(); runCurrent()
        c.select(printing, GenerationId("new-generation")); runCurrent(); c.review(offer); c.approveAndLoad(); runCurrent()
        assertNull(c.state.value.image); assertEquals(1, a.loads)
    }
    @Test fun adapterCannotPublishPixelsForAnotherOffer() = runTest {
        val a = Access().apply { loadOffer = offer.copy(faceIndex = 1) }; val s = Settings(); val c = controller(a, s)
        c.select(printing, generation); runCurrent(); c.review(offer); c.approveAndLoad(); runCurrent()
        assertNull(c.state.value.image); assertEquals(Problem.SOURCE_MISMATCH, c.state.value.problem)
    }
}
