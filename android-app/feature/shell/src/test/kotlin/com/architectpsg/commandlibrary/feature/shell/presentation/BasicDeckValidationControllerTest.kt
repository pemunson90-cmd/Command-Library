package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BasicDeckValidationControllerTest {
    private fun <T> unknown(): Knowledge<T> = Knowledge.Unknown("Not reported", SnapshotList.empty())
    private val deck = DeckSnapshot(DeckId(ProviderId("local"), "deck"), "Deck", unknown(), "", unknown(),
        SnapshotList.empty(), UtcTimestamp(0), UtcTimestamp(1), Revision(2), SnapshotList.empty(), unknown(), null,
        SnapshotList.empty(), SnapshotList.empty())
    private val snapshot = SnapshotReference(ProviderId("scryfall"), GenerationId("generation"), null, LanguageTag.ENGLISH)
    private fun reply(r: BasicDeckValidationRequest) = BasicDeckValidationResult(r.deck.id, r.deck.revision, r.snapshot,
        r.format, LegalityStatus.REVIEW_REQUIRED, SnapshotList.empty(), SnapshotList.empty(), "2026-09-25")
    private class Port(val body: suspend (BasicDeckValidationRequest) -> Outcome<BasicDeckValidationResult>) : LocalBasicDeckValidation {
        override suspend fun analyze(request: BasicDeckValidationRequest, cancel: CancelToken) = body(request)
    }
    @Test fun unknownSavedFormatDoesNotTriggerAnAssessmentOrChangeSavedDeck() = runTest {
        val calls = mutableListOf<BasicDeckValidationRequest>()
        val controller = BasicDeckValidationController(this, Port { calls.add(it); Outcome.Success(reply(it)) },
            StandardTestDispatcher(testScheduler)) { _, _ -> true }
        controller.open(deck, snapshot); controller.assess(); runCurrent()
        assertTrue(calls.isEmpty()); assertNull(controller.state.value.format); assertNull(controller.state.value.result)
        controller.format(BasicDeckFormat.COMMANDER); controller.assess(); runCurrent()
        assertSame(deck, calls.single().deck); assertEquals(BasicDeckFormat.COMMANDER, calls.single().format)
        assertSame(deck, controller.state.value.deck); assertEquals(LegalityStatus.REVIEW_REQUIRED, controller.state.value.result!!.status)
        controller.close(); controller.open(deck, snapshot)
        assertNull(controller.state.value.format); assertNull(controller.state.value.result)
    }
    @Test fun sourceChangedDuringSuspendedReadCannotPublishAResultForTheOldCatalog() = runTest {
        var current = true
        val gate = CompletableDeferred<Unit>()
        val controller = BasicDeckValidationController(this, Port { gate.await(); Outcome.Success(reply(it)) },
            StandardTestDispatcher(testScheduler)) { _, _ -> current }
        controller.open(deck, snapshot); controller.format(BasicDeckFormat.COMMANDER); controller.assess(); runCurrent()
        current = false; gate.complete(Unit); runCurrent()
        assertEquals(Problem.STALE_GENERATION, controller.state.value.problem)
        assertNull(controller.state.value.result); assertFalse(controller.state.value.busy)
    }
    @Test fun incorrectReceiptAndClosedOrStaleRenderedActionsCannotPublish() = runTest {
        var calls = 0
        val controller = BasicDeckValidationController(this, Port { calls++; Outcome.Success(reply(it).copy(deckRevision = Revision(3))) },
            StandardTestDispatcher(testScheduler)) { _, _ -> true }
        controller.open(deck, snapshot); val old = controller.state.value
        controller.format(BasicDeckFormat.COMMANDER)
        controller.rendered(old) { fail("Stale rendered selection acted") }
        controller.assess(); runCurrent()
        assertEquals(Problem.CORRUPT_DATA, controller.state.value.problem); assertNull(controller.state.value.result)
        controller.assess(); controller.close(); runCurrent()
        assertEquals(1, calls); assertNull(controller.state.value.deck); assertNull(controller.state.value.result)
    }
}
