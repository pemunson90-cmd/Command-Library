@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class PrintingSelectionControllerTest {
    private val generation = GenerationId("printing-fixture")
    private val card = syntheticCard("Printing fixture", generation)
    private fun printing(id: String) = PrintingRecord(CardId(card.oracleId.source, id), Knowledge.Known(card.oracleId), "tst",
        Knowledge.Known("Synthetic set"), id, LanguageTag.ENGLISH, Knowledge.Known("common"), Knowledge.Known(SnapshotList.of(Finish.NONFOIL)),
        Knowledge.Absent("no image"), SnapshotList.empty(), SnapshotList.empty(), SnapshotList.empty(), SnapshotList.empty(), SnapshotList.empty(), card.provenance)
    private inner class Fixture : CatalogPrintingLookup, CardLinks {
        var rows = listOf(printing("a"), printing("b"))
        var snapshot = SnapshotReference(card.oracleId.source, generation, null, LanguageTag.ENGLISH)
        var cursor: String? = null
        var gate: CompletableDeferred<Unit>? = null
        var ignoreCancellation = false
        var responseFailure: Outcome<Nothing>? = null
        var resolveHook: (() -> Unit)? = null
        val requests = mutableListOf<PageRequest>()
        override suspend fun printing(id: CardId, cancel: CancelToken): Outcome<PrintingRecord> = error("page only")
        override suspend fun printings(id: OracleId, page: PageRequest, cancel: CancelToken): Outcome<Page<PrintingRecord>> {
            requests += page
            val result = responseFailure ?: Outcome.Success(Page(SnapshotList.copyOf(rows), cursor, snapshot))
            val wait = gate
            if (ignoreCancellation) withContext(NonCancellable) { wait?.await() } else wait?.await()
            return result
        }
        override fun template(pattern: String, values: LinkTokenValues): Outcome<ResourceUri> = error("not used")
        override fun resolve(card: CardRecord, printing: PrintingRecord?, preferences: LinkPreferences): Outcome<SnapshotList<ResolvedLink>> {
            val target = preferences.destinations.single()
            val hook = resolveHook; resolveHook = null; hook?.invoke()
            return Outcome.Success(SnapshotList.of(ResolvedLink(target, ResourceUri("https://example.test/${printing!!.id.raw}/${target.id.value}"),
                LinkResolutionKind.SUPPLIED, printing.id, printing.provenance, Knowledge.Absent("none"))))
        }
    }
    private fun TestScope.controller(f: Fixture) = PrintingSelectionController(ownerScope(), f, StandardTestDispatcher(testScheduler), f).also {
        it.onActiveGeneration(generation); it.selectCard(card); it.setVisible(true)
    }

    @Test fun actualSelectionAndNextReplaceBoundedPagesAndInvalidateOldLinks() = runTest {
        val f = Fixture(); f.cursor = "second"; val c = controller(f); advanceUntilIdle()
        assertEquals(10, f.requests.single().limit); assertNull(c.state.value.selected)
        c.selectPrinting(printing("a").id); advanceUntilIdle()
        assertEquals(8, c.state.value.links.size); assertEquals("a", c.state.value.selected!!.id.raw)
        f.rows = listOf(printing("c")); f.cursor = null; c.next(); advanceUntilIdle()
        assertEquals("second", f.requests.last().cursor)
        assertEquals("c", c.state.value.page!!.items.single().id.raw)
        assertNull(c.state.value.selected); assertTrue(c.state.value.links.isEmpty())
    }

    @Test fun resizeVisibilityNoopPreservesSelectionButActualExitAndReloadClearIt() = runTest {
        val f = Fixture(); val c = controller(f); advanceUntilIdle(); c.selectPrinting(printing("b").id); advanceUntilIdle()
        c.setVisible(true); c.selectCard(card); advanceUntilIdle()
        assertEquals("b", c.state.value.selected!!.id.raw); assertEquals(1, f.requests.size)
        c.setVisible(false); advanceUntilIdle(); assertNull(c.state.value.selected)
        c.setVisible(true); advanceUntilIdle(); assertEquals(2, f.requests.size)
        c.selectPrinting(printing("b").id); advanceUntilIdle(); c.reload(); advanceUntilIdle()
        assertNull(c.state.value.selected); assertTrue(c.state.value.links.isEmpty())
    }

    @Test fun activeGenerationChangeRemovalAndStaleReloadNeverRebindOldCard() = runTest {
        val f = Fixture(); val c = controller(f); advanceUntilIdle(); c.selectPrinting(printing("a").id); advanceUntilIdle()
        val ticket = c.state.value.ticket
        c.onActiveGeneration(GenerationId("replacement")); c.openLink(ticket, "scryfall") { fail("stale launch") }; c.reload(); advanceUntilIdle()
        assertEquals(Problem.STALE_GENERATION, c.state.value.problem); assertNull(c.state.value.selected); assertEquals(1, f.requests.size)
        c.onActiveGeneration(null); advanceUntilIdle(); assertNull(c.state.value.page)
    }

    @Test fun lateNoncooperativeReadCannotRepopulateChangedCardOrClosedRoute() = runTest {
        val f = Fixture(); f.ignoreCancellation = true; val held = CompletableDeferred<Unit>(); f.gate = held
        val c = controller(f); runCurrent(); assertTrue(c.state.value.loading)
        c.selectCard(null); held.complete(Unit); advanceUntilIdle()
        assertNull(c.state.value.card); assertNull(c.state.value.page)
        f.gate = CompletableDeferred(); c.selectCard(card); runCurrent(); c.setVisible(false)
        f.gate!!.complete(Unit); advanceUntilIdle(); assertNull(c.state.value.page)
    }

    @Test fun malformedPagesNeverOfferSelection() = runTest {
        val f = Fixture(); val c = controller(f); advanceUntilIdle()
        val malformed = listOf(
            { f.snapshot = f.snapshot.copy(generation = GenerationId("wrong")) },
            { f.snapshot = f.snapshot.copy(source = ProviderId("wrong")) },
            { f.snapshot = f.snapshot.copy(language = LanguageTag("fr")) },
            { f.rows = List(11) { printing("$it") } },
            { f.rows = listOf(printing("a"), printing("a")) },
            { f.rows = emptyList(); f.cursor = "not-empty" },
            { f.rows = listOf(printing("a").copy(oracleId = Knowledge.Known(OracleId(card.oracleId.source, "wrong")))) },
            { f.rows = listOf(printing("a").copy(provenance = card.provenance.copy(generation = GenerationId("wrong")))) },
        )
        for (breakPage in malformed) {
            f.rows = listOf(printing("a")); f.cursor = null
            f.snapshot = SnapshotReference(card.oracleId.source, generation, null, LanguageTag.ENGLISH)
            breakPage(); c.reload(); advanceUntilIdle()
            assertEquals(Problem.STALE_GENERATION, c.state.value.problem); assertNull(c.state.value.page)
            c.selectPrinting(printing("a").id); assertNull(c.state.value.selected)
        }
    }

    @Test fun sourceUnknownRelationRemainsUnknownInsteadOfFabricatedCanonicalId() = runTest {
        val f = Fixture(); f.rows = listOf(printing("a").copy(oracleId = Knowledge.Unknown("reversible", SnapshotList.empty())))
        val c = controller(f); advanceUntilIdle(); c.selectPrinting(printing("a").id); advanceUntilIdle()
        assertTrue(c.state.value.selected!!.oracleId is Knowledge.Unknown)
    }

    @Test fun currentTicketIsRecheckedAfterResolverDispatchBeforeBrowserCallback() = runTest {
        val f = Fixture(); val c = controller(f); advanceUntilIdle(); c.selectPrinting(printing("a").id); advanceUntilIdle()
        val ticket = c.state.value.ticket
        f.resolveHook = { c.selectPrinting(printing("b").id) }
        var launches = 0; c.openLink(ticket, "scryfall") { launches++ }; advanceUntilIdle()
        assertEquals(0, launches); assertEquals("b", c.state.value.selected!!.id.raw)
        c.openLink(c.state.value.ticket, "scryfall") { launches++ }; advanceUntilIdle(); assertEquals(1, launches)
        c.openLink(ticket, "scryfall") { launches++ }; advanceUntilIdle(); assertEquals(1, launches)
    }
    @Test fun queuedBrowserReplyIsDiscardedWhenNavigationEventClosesRoute() = runTest {
        val f = Fixture(); val c = controller(f); advanceUntilIdle()
        c.selectPrinting(printing("a").id); advanceUntilIdle()
        var launches = 0
        c.openLink(c.state.value.ticket, "scryfall") { launches++ }
        c.setVisible(false)
        advanceUntilIdle()
        assertEquals(0, launches); assertNull(c.state.value.selected)
    }

    @Test fun collectionEntryAcceptsOnlyExactCurrentPrintingTicketAndVisibleRoute() = runTest {
        val f = Fixture(); val c = controller(f); advanceUntilIdle()
        c.selectPrinting(printing("a").id); advanceUntilIdle()
        val old = c.state.value.ticket; val accepted = mutableListOf<PrintingRecord>()
        c.withCurrentPrinting(old, printing("b").id) { accepted += it }
        assertTrue(accepted.isEmpty())
        c.withCurrentPrinting(old, printing("a").id) { accepted += it }
        assertEquals(listOf(printing("a")), accepted)
        c.selectPrinting(printing("b").id)
        c.withCurrentPrinting(old, printing("a").id) { fail("Old selection must not enter collection") }
        advanceUntilIdle(); val current = c.state.value.ticket
        c.setVisible(false)
        c.withCurrentPrinting(current, printing("b").id) { fail("Closed card route must not enter collection") }
    }

    @Test fun collectionEntryChecksRootCurrentCardPredicateSynchronously() = runTest {
        val f = Fixture(); var current = true
        val c = PrintingSelectionController(ownerScope(), f, StandardTestDispatcher(testScheduler), f) { current }
        c.onActiveGeneration(generation); c.selectCard(card); c.setVisible(true); advanceUntilIdle()
        c.selectPrinting(printing("a").id); advanceUntilIdle(); val ticket = c.state.value.ticket
        current = false
        c.withCurrentPrinting(ticket, printing("a").id) { fail("Stale root status must block before asynchronous collectors") }
        current = true
        var calls = 0; c.withCurrentPrinting(ticket, printing("a").id) { calls++ }; assertEquals(1, calls)
    }

}
