package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*
import java.math.BigDecimal
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalDeckStatisticsEngineTest {
    private val f = StatisticsFixtures
    private class Ports : CatalogStatus, CatalogDeckFactsLookup {
        val f = StatisticsFixtures
        var statusCalls = 0
        val calls = mutableListOf<Pair<OracleId, CardId?>>()
        var statuses: List<Outcome<LocalCatalogStatus>> = listOf(Outcome.Success(f.status()))
        var lookup: suspend (OracleId, CardId?, SnapshotReference, CancelToken) -> Outcome<CatalogDeckFacts> = { oracle, printing, _, _ ->
            Outcome.Success(f.facts(f.card(oracle), printing))
        }
        override suspend fun status(cancel: CancelToken): Outcome<LocalCatalogStatus> {
            cancel.throwIfCancelled()
            return statuses[minOf(statusCalls++, statuses.lastIndex)]
        }
        override suspend fun facts(oracleId: OracleId, printingId: CardId?, snapshot: SnapshotReference, cancel: CancelToken): Outcome<CatalogDeckFacts> {
            cancel.throwIfCancelled()
            calls.add(oracleId to printingId)
            assertEquals(f.snapshot, snapshot)
            return lookup(oracleId, printingId, snapshot, cancel)
        }
        fun engine() = LocalDeckStatisticsEngine(this, this)
    }
    private fun ok(result: Outcome<DeckStatisticsResult>): DeckStatisticsResult {
        assertTrue("Expected success: $result", result is Outcome.Success)
        return (result as Outcome.Success).value
    }
    private fun failure(code: FailureCode, result: Outcome<*>) {
        assertEquals(code, (result as Outcome.Failure).error.code)
    }

    @Test fun cacheIsExactOracleAndPrintingWhileEachPhysicalEntryIsWeightedSeparately() = runBlocking {
        val ports = Ports()
        val oracle = f.oracle()
        val printing = f.printing()
        val entries = arrayOf(f.entry("a", 2), f.entry("b", 3, board = BoardRole.SIDEBOARD),
            f.entry("c", 4, printing = Knowledge.Known(printing)), f.entry("d", 5, printing = Knowledge.Known(printing)),
            f.entry("e", 6, printing = Knowledge.Known(f.printing("other"))))
        val request = f.request(*entries)
        val before = request.copy()
        val result = ok(ports.engine().analyze(request, f.cancel))
        assertEquals(listOf(oracle to null, oracle to printing, oracle to f.printing("other")), ports.calls)
        assertEquals(20L, result.copies)
        assertEquals(20L, result.averageManaValue.copies)
        assertEquals(entries.map { it.id }, result.entryIds)
        assertSame(request.filter, result.filter)
        assertEquals(2, ports.statusCalls)
        assertEquals(before, request)
        // A second analysis re-reads facts; no stale cache survives a captured call.
        ok(ports.engine().analyze(request, f.cancel))
        assertEquals(6, ports.calls.size)
    }

    @Test fun boardsEntriesAndAndOrManualTagFiltersIntersectWithoutDuplicateCounts() = runBlocking {
        val a = EntryLabel(EntityId("a"), "Ramp")
        val b = EntryLabel(EntityId("b"), "Draw")
        val first = f.entry("first", 2, labels = SnapshotList.of(a, b))
        val second = f.entry("second", 3, labels = SnapshotList.of(a))
        val side = f.entry("side", 5, board = BoardRole.SIDEBOARD, labels = SnapshotList.of(b))
        val automatic = f.entry("automatic", 7).copy(roleOverrides = SnapshotList.of(RoleOverride(null, f.unknown(), "Ramp", true, UtcTimestamp(0))))
        val entries = arrayOf(first, second, side, automatic)
        suspend fun run(filter: DeckStatisticsFilter): DeckStatisticsResult = ok(Ports().engine().analyze(f.request(*entries, filter = filter), f.cancel))
        val tags = SnapshotList.of(a.id, b.id)
        val or = run(f.filter(tags = tags))
        assertEquals(listOf(first.id, second.id, side.id), or.entryIds)
        assertEquals(10L, or.copies)
        assertEquals(listOf(5L, 7L), or.tags.map { it.copies })
        val and = run(f.filter(tags = tags, all = true))
        assertEquals(listOf(first.id), and.entryIds)
        assertEquals(2L, and.copies)
        val intersect = run(f.filter(boards = SnapshotList.of(BoardRole.MAIN), ids = SnapshotList.of(first.id, side.id), tags = SnapshotList.of(b.id)))
        assertEquals(listOf(first.id), intersect.entryIds)
        assertEquals(2L, intersect.copies)
        val none = run(f.filter(ids = SnapshotList.empty()))
        assertTrue(none.entryIds.isEmpty())
        assertEquals(0L, none.copies)
        assertTrue(run(f.filter(boards = SnapshotList.empty())).entryIds.isEmpty())
        assertEquals(17L, run(f.filter()).copies)
    }

    @Test fun legacyImplicitLabelsAndRegisteredUnusedTagsKeepExistingMeaning() = runBlocking {
        val used = EntryLabel(EntityId("used"), "Used")
        val unused = EntryLabel(EntityId("unused"), "Unused")
        val request = f.request(f.entry(labels = SnapshotList.of(used)), filter = f.filter(tags = SnapshotList.of(used.id)))
        assertEquals(1L, ok(Ports().engine().analyze(request, f.cancel)).copies)
        val explicit = request.copy(deck = request.deck.copy(categoryOrder = SnapshotList.of(used, unused)), filter = f.filter(tags = SnapshotList.of(unused.id)))
        assertEquals(0L, ok(Ports().engine().analyze(explicit, f.cancel)).copies)
    }

    @Test fun invalidWholeDeckFiltersAndIdentitySizesFailBeforeAnyIo() = runBlocking {
        val entry = f.entry()
        val label = EntryLabel(EntityId("tag"), "Tag")
        val base = f.request(entry.copy(labels = SnapshotList.of(label)))
        val badOracle = entry.copy(identity = entry.identity.copy(oracleId = Knowledge.Known(f.oracle("bad\uD800"))))
        val badPrinting = entry.copy(identity = entry.identity.copy(printingId = Knowledge.Known(f.printing("x".repeat(1025)))))
        val requests = listOf(
            base.copy(deck = base.deck.copy(entries = SnapshotList.of(entry, entry))),
            base.copy(deck = base.deck.copy(name = " ")),
            base.copy(filter = f.filter(boards = SnapshotList.of(BoardRole.MAIN, BoardRole.MAIN))),
            base.copy(filter = f.filter(ids = SnapshotList.of(entry.id, entry.id))),
            base.copy(filter = f.filter(tags = SnapshotList.of(label.id, label.id))),
            base.copy(filter = f.filter(ids = SnapshotList.of(EntityId("missing")))),
            base.copy(filter = f.filter(tags = SnapshotList.of(EntityId("missing")))),
            base.copy(filter = f.filter(ids = SnapshotList.of(EntityId("x".repeat(DeckPayloadLimits.MAX_ID_BYTES + 1))))),
            base.copy(filter = f.filter(tags = SnapshotList.copyOf(List(DeckStatisticsLimits.MAX_ENTRIES + 1) { EntityId("t$it") }))),
            base.copy(deck = f.deck(badOracle)),
            base.copy(deck = f.deck(badPrinting)),
            base.copy(snapshot = f.snapshot.copy(generation = null)), base.copy(snapshot = f.snapshot.copy(revision = Revision(1))),
            base.copy(snapshot = f.snapshot.copy(language = LanguageTag("ja"))), base.copy(snapshot = f.snapshot.copy(source = ProviderId("other"))))
        for (request in requests) {
            val ports = Ports()
            failure(FailureCode.INVALID_INPUT, ports.engine().analyze(request, f.cancel))
            assertEquals(0, ports.statusCalls)
            assertTrue(ports.calls.isEmpty())
        }
        val badUnselected = base.copy(deck = base.deck.copy(entries = SnapshotList.of(entry, entry)), filter = f.filter(boards = SnapshotList.empty()))
        failure(FailureCode.INVALID_INPUT, Ports().engine().analyze(badUnselected, f.cancel))
    }

    @Test fun missingAndForeignIdentitiesStayUnresolvedWithoutNameGuessingOrPrintingFallback() = runBlocking {
        val ports = Ports()
        val entries = arrayOf(f.entry("missing", 2, oracle = f.unknown()),
            f.entry("foreign", 3, oracle = Knowledge.Known(OracleId(ProviderId("foreign"), "oracle"))),
            f.entry("foreign-printing", 5, printing = Knowledge.Known(CardId(ProviderId("foreign"), "printing"))))
        val result = ok(ports.engine().analyze(f.request(*entries), f.cancel))
        assertTrue(ports.calls.isEmpty())
        assertEquals(10L, result.unresolvedCopies)
        assertEquals(10L, result.unknownManaValueCopies)
        assertEquals(10L, result.unknownTypeCopies)
        assertEquals(10L, result.unknownCostCopies)
        assertEquals(10L, result.unknownTokenReferenceCopies)
        assertEquals(0L, result.unknownLandProductionCopies)
    }

    @Test fun onlyMissingLookupReasonsBecomeUnresolvedAndFailuresAreNeverHidden() = runBlocking {
        for (reason in listOf(UnavailableReason.NOT_FOUND, UnavailableReason.NOT_INSTALLED, UnavailableReason.SOURCE_UNAVAILABLE)) {
            val ports = Ports()
            ports.lookup = { _, _, _, _ -> Outcome.Unavailable(reason, "synthetic missing fact") }
            val result = ok(ports.engine().analyze(f.request(f.entry("a", 2), f.entry("b", 3)), f.cancel))
            assertEquals(5L, result.unresolvedCopies)
            assertEquals(1, ports.calls.size)
            assertEquals(2, ports.statusCalls)
        }
        val outcomes: List<Outcome<CatalogDeckFacts>> = listOf(
            Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "unsupported"), Outcome.Unavailable(UnavailableReason.OFFLINE, "offline"),
            Outcome.Failure(DomainFailure(FailureCode.CORRUPT_DATA, "corrupt", SnapshotList.empty())),
            Outcome.Failure(DomainFailure(FailureCode.STALE_GENERATION, "stale", SnapshotList.empty())),
            Outcome.Unknown("Unknown source failure", SnapshotList.empty()))
        for (expected in outcomes) {
            val ports = Ports()
            ports.lookup = { _, _, _, _ -> expected }
            assertEquals(expected, ports.engine().analyze(f.request(f.entry()), f.cancel))
        }
    }

    @Test fun snapshotStatusIsCheckedBeforeAndAfterEvenEmptySelections() = runBlocking {
        for (empty in listOf(false, true)) {
            val request = f.request(f.entry(), filter = if (empty) f.filter(boards = SnapshotList.empty()) else f.filter())
            val stale = f.status().copy(activeGenerations = SnapshotList.of(f.status().activeGenerations.single().copy(id = GenerationId("changed"))))
            for (staleFirst in listOf(false, true)) {
                val ports = Ports()
                ports.statuses = if (staleFirst) listOf(Outcome.Success(stale)) else listOf(Outcome.Success(f.status()), Outcome.Success(stale))
                failure(FailureCode.STALE_GENERATION, ports.engine().analyze(request, f.cancel))
                assertEquals(if (staleFirst) 1 else 2, ports.statusCalls)
                assertEquals(if (staleFirst || empty) 0 else 1, ports.calls.size)
            }
        }
        val ports = Ports()
        ports.statuses = listOf(Outcome.Success(f.status().copy(initialized = false, activeGenerations = SnapshotList.empty())))
        val missing = ports.engine().analyze(f.request(), f.cancel) as Outcome.Unavailable
        assertEquals(UnavailableReason.NOT_INSTALLED, missing.reason)
        assertTrue(ports.calls.isEmpty())
    }

    @Test fun statusFailuresPropagateAndForeignStatusNeverMixesSources() = runBlocking {
        val error = Outcome.Failure(DomainFailure(FailureCode.PERSISTENCE, "status failure", SnapshotList.empty()))
        for (after in listOf(false, true)) {
            val ports = Ports()
            ports.statuses = if (after) listOf(Outcome.Success(f.status()), error) else listOf(error)
            assertEquals(error, ports.engine().analyze(f.request(), f.cancel))
        }
        val ports = Ports()
        ports.statuses = listOf(Outcome.Success(f.status().copy(source = Knowledge.Known(ProviderId("foreign")))))
        failure(FailureCode.STALE_GENERATION, ports.engine().analyze(f.request(), f.cancel))
    }

    @Test fun returnedIdentitySourceGenerationPrintingLanguageAndFacesAreRevalidated() = runBlocking {
        val card = f.card()
        val base = f.facts(card, f.printing())
        val invalid = listOf(base.copy(snapshot = f.snapshot.copy(generation = GenerationId("wrong"))),
            base.copy(printingId = null), base.copy(printingId = f.printing("wrong")),
            base.copy(card = card.copy(oracleId = f.oracle("wrong"))),
            base.copy(card = card.copy(language = LanguageTag("ja"))),
            base.copy(card = card.copy(providerIdentities = SnapshotList.of(ProviderIdentity(f.source, "oracle", "wrong")))),
            base.copy(card = card.copy(revision = Revision(1))),
            base.copy(card = card.copy(provenance = f.provenance().copy(provider = ProviderId("other")))),
            base.copy(card = card.copy(provenance = f.provenance().copy(generation = GenerationId("wrong")))),
            base.copy(card = card.copy(faces = SnapshotList.of(f.face(card).copy(identity = FaceIdentity(f.oracle("wrong"), 0, f.unknown()))))),
            base.copy(card = card.copy(faces = SnapshotList.of(f.face(card), f.face(card)))),
            base.copy(reportedParts = Knowledge.Known(SnapshotList.of(f.token().copy(printingId = CardId(ProviderId("other"), "part"))))))
        for (value in invalid) {
            val ports = Ports()
            ports.lookup = { _, _, _, _ -> Outcome.Success(value) }
            failure(FailureCode.CORRUPT_DATA, ports.engine().analyze(f.request(f.entry(printing = Knowledge.Known(f.printing()))), f.cancel))
        }
    }

    @Test fun malformedProductionUnsupportedComponentsAndFullFactBudgetsRemainFailures() = runBlocking {
        val bad: List<Pair<CatalogDeckFacts, Boolean>> = listOf(
            f.facts(production = Knowledge.Known(SnapshotList.of("W", "W"))) to false,
            f.facts(production = Knowledge.Known(SnapshotList.of("H"))) to true,
            f.facts(parts = Knowledge.Known(SnapshotList.of(f.token(component = "future")))) to true,
            f.facts(f.card(mv = Knowledge.Known(BigDecimal("1e-1000000")))) to false,
            f.facts(f.card().copy(keywords = Knowledge.Known(SnapshotList.of("x".repeat(33 * 1024 * 1024))))) to false)
        for ((value, unsupported) in bad) {
            val ports = Ports()
            ports.lookup = { _, _, _, _ -> Outcome.Success(value) }
            val result = ports.engine().analyze(f.request(f.entry()), f.cancel)
            if (unsupported) assertEquals(UnavailableReason.UNSUPPORTED, (result as Outcome.Unavailable).reason)
            else failure(FailureCode.CORRUPT_DATA, result)
        }
        val ports = Ports()
        val large = "x".repeat(17 * 1024 * 1024)
        ports.lookup = { oracle, printing, _, _ -> Outcome.Success(f.facts(f.card(oracle).copy(originalText = Knowledge.Known(large)), printing)) }
        failure(FailureCode.CORRUPT_DATA, ports.engine().analyze(f.request(f.entry("a"), f.entry("b", oracle = Knowledge.Known(f.oracle("other")))), f.cancel))
        assertEquals(2, ports.calls.size)
    }

    @Test fun cancellationBeforeIoDuringBoundsAndNativeSuspensionPropagates() = runBlocking {
        val exception = CancellationException("synthetic cancellation")
        val cancelled = object : CancelToken {
            override val isCancelled = true
            override fun throwIfCancelled(): Unit = throw exception
        }
        val before = Ports()
        try { before.engine().analyze(f.request(f.entry()), cancelled); fail("Expected cancellation") }
        catch (actual: CancellationException) { assertSame(exception, actual) }
        assertEquals(0, before.statusCalls)
        val during = Ports()
        var reading = false
        var polls = 0
        during.lookup = { _, _, _, _ -> reading = true; Outcome.Success(f.facts(f.card().copy(originalText = Knowledge.Known("x".repeat(100_000))))) }
        val token = object : CancelToken {
            override val isCancelled get() = reading && polls >= 50
            override fun throwIfCancelled() { if (reading && ++polls == 50) throw exception }
        }
        try { during.engine().analyze(f.request(f.entry()), token); fail("Expected cancellation") }
        catch (actual: CancellationException) { assertSame(exception, actual) }
        assertEquals(1, during.statusCalls)
        var closed = false
        var returned = false
        val native = Ports()
        native.lookup = { _, _, _, _ -> try { awaitCancellation() } finally { closed = true } }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { native.engine().analyze(f.request(f.entry()), f.cancel); returned = true }
        job.cancelAndJoin()
        assertTrue(closed)
        assertFalse(returned)
        val nativeBounds = Ports()
        var inFacts = false
        var boundsReturned = false
        nativeBounds.lookup = { _, _, _, _ -> inFacts = true; Outcome.Success(f.facts(f.card().copy(originalText = Knowledge.Known("x".repeat(100_000))))) }
        val boundsJob = launch(start = CoroutineStart.UNDISPATCHED) {
            val ownContext = currentCoroutineContext()
            var checks = 0
            val nativeToken = object : CancelToken {
                override val isCancelled = false
                override fun throwIfCancelled() { if (inFacts && ++checks == 40) ownContext.cancel(exception) }
            }
            nativeBounds.engine().analyze(f.request(f.entry()), nativeToken)
            boundsReturned = true
        }
        boundsJob.join()
        assertTrue(boundsJob.isCancelled)
        assertFalse(boundsReturned)
        assertEquals(1, nativeBounds.statusCalls)
    }
}
