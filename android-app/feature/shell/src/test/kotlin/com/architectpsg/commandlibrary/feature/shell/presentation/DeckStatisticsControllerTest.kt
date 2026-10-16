package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeckStatisticsControllerTest {
    private fun <T> unknown(): Knowledge<T> = Knowledge.Unknown("Not reported", SnapshotList.empty())
    private val deck = DeckSnapshot(DeckId(ProviderId("local"), "deck"), "Deck", unknown(), "", unknown(),
        SnapshotList.empty(), UtcTimestamp(0), UtcTimestamp(1), Revision(2), SnapshotList.empty(), unknown(), null,
        SnapshotList.empty(), SnapshotList.empty())
    private val snapshot = SnapshotReference(ProviderId("scryfall"), GenerationId("generation"), null, LanguageTag.ENGLISH)
    private fun reply(r: DeckStatisticsRequest) = DeckStatisticsResult(r.deck.id, r.deck.revision, r.snapshot, r.filter,
        SnapshotList.empty(), 0, 0, SnapshotList.empty(), 0, StatisticAverage(unknown(), 0), StatisticAverage(unknown(), 0),
        SnapshotList.empty(), 0, SnapshotList.empty(), 0, SnapshotList.empty(), 0, SnapshotList.empty(), 0,
        SnapshotList.empty(), SnapshotList.empty())
    private class Port(val body: suspend (DeckStatisticsRequest) -> Outcome<DeckStatisticsResult>) : LocalDeckStatistics {
        override suspend fun analyze(request: DeckStatisticsRequest, cancel: CancelToken) = body(request)
    }
    @Test fun changedCatalogDuringReadRejectsOtherwiseMatchingResult() = runTest {
        var current = true
        val gate = CompletableDeferred<Unit>()
        val controller = DeckStatisticsController(this, Port { gate.await(); Outcome.Success(reply(it)) },
            StandardTestDispatcher(testScheduler)) { _, _ -> current }
        controller.open(deck, snapshot); runCurrent()
        assertTrue(controller.state.value.busy)
        current = false; gate.complete(Unit); runCurrent()
        assertEquals(Problem.STALE_GENERATION, controller.state.value.problem)
        assertNull(controller.state.value.result); assertFalse(controller.state.value.busy)
    }
    @Test fun wrongRevisionOrFilterAcknowledgmentCannotPublish() = runTest {
        var wrongRevision = true
        val controller = DeckStatisticsController(this, Port {
            Outcome.Success(if (wrongRevision) reply(it).copy(deckRevision = Revision(3))
                else reply(it).copy(filter = it.filter.copy(requireAllTags = !it.filter.requireAllTags)))
        }, StandardTestDispatcher(testScheduler)) { _, _ -> true }
        controller.open(deck, snapshot); runCurrent()
        assertEquals(Problem.CORRUPT_DATA, controller.state.value.problem); assertNull(controller.state.value.result)
        wrongRevision = false; controller.compute(); runCurrent()
        assertEquals(Problem.CORRUPT_DATA, controller.state.value.problem); assertNull(controller.state.value.result)
    }
    @Test fun editedFiltersAndClosedDialogFenceQueuedReadsAndOldRenderedActions() = runTest {
        val requests = mutableListOf<DeckStatisticsRequest>()
        val controller = DeckStatisticsController(this, Port { requests.add(it); Outcome.Success(reply(it)) },
            StandardTestDispatcher(testScheduler)) { _, _ -> true }
        controller.open(deck, snapshot)
        val oldFrame = controller.state.value
        controller.board(BoardRole.MAIN); controller.restrictEntries(true)
        controller.rendered(oldFrame) { fail("Stale frame acted") }
        runCurrent(); assertTrue(requests.isEmpty()); assertNull(controller.state.value.result)
        controller.compute(); runCurrent()
        val captured = requests.single()
        assertTrue(captured.filter.boards.isEmpty()); assertEquals(SnapshotList.empty<EntityId>(), captured.filter.entryIds)
        assertEquals(captured.filter, controller.state.value.result!!.filter)
        controller.compute(); controller.close(); runCurrent()
        assertEquals(1, requests.size); assertNull(controller.state.value.deck); assertNull(controller.state.value.result)
    }
}
