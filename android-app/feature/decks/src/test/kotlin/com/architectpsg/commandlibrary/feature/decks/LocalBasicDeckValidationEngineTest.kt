package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*
import java.math.BigDecimal
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalBasicDeckValidationEngineTest {
    private val f = BasicValidationFixtures
    private class Ports : CatalogDeckFactsLookup, CatalogFormatLegalityLookup, CatalogStatus {
        val f = BasicValidationFixtures
        var statusCalls = 0
        val factCalls = mutableListOf<Pair<OracleId, CardId?>>()
        val legalityCalls = mutableListOf<OracleId>()
        var statuses: List<Outcome<LocalCatalogStatus>> = listOf(Outcome.Success(f.status()))
        var readFacts: suspend (OracleId, CardId?, CancelToken) -> Outcome<CatalogDeckFacts> = { oracle, printing, _ ->
            Outcome.Success(f.facts(if (oracle.raw == "commander") f.commander() else f.island(oracle.raw), printing))
        }
        var readLegality: suspend (OracleId, CancelToken) -> Outcome<CatalogFormatLegality> = { oracle, _ -> Outcome.Success(f.legality(oracle)) }
        override suspend fun status(cancel: CancelToken): Outcome<LocalCatalogStatus> {
            cancel.throwIfCancelled()
            return statuses[minOf(statusCalls++, statuses.lastIndex)]
        }
        override suspend fun facts(oracleId: OracleId, printingId: CardId?, snapshot: SnapshotReference, cancel: CancelToken): Outcome<CatalogDeckFacts> {
            cancel.throwIfCancelled(); assertEquals(f.snapshot, snapshot)
            factCalls.add(oracleId to printingId)
            return readFacts(oracleId, printingId, cancel)
        }
        override suspend fun legality(oracleId: OracleId, formatId: FormatId, snapshot: SnapshotReference, cancel: CancelToken): Outcome<CatalogFormatLegality> {
            cancel.throwIfCancelled(); assertEquals(f.snapshot, snapshot); assertEquals(f.format, formatId)
            legalityCalls.add(oracleId)
            return readLegality(oracleId, cancel)
        }
        fun engine() = LocalBasicDeckValidationEngine(this, this, this)
    }
    private fun entries() = arrayOf(f.entry("commander", board = BoardRole.COMMANDER), f.entry("land", 99, oracle = Knowledge.Known(f.oracle("island"))))
    private fun request() = f.request(*entries())
    private fun ok(value: Outcome<BasicDeckValidationResult>): BasicDeckValidationResult {
        assertTrue("Expected useful assessment: $value", value is Outcome.Success)
        return (value as Outcome.Success).value
    }
    private fun failure(code: FailureCode, value: Outcome<*>) { assertEquals(code, (value as Outcome.Failure).error.code) }
    private fun perEntry(result: BasicDeckValidationResult, prefix: String, id: String) = result.checks.single {
        it.id.startsWith(prefix + "_") && it.entryIds == listOf(EntityId(id))
    }

    @Test fun realPortAssessmentRetainsCapturedIdentitySourceDatesAndInputWithoutApprovingDeck() = runBlocking {
        val ports = Ports()
        val request = request()
        val before = request.copy()
        val result = ok(ports.engine().analyze(request, f.cancel))
        assertEquals(request.deck.id, result.deckId)
        assertEquals(request.deck.revision, result.deckRevision)
        assertSame(request.snapshot, result.snapshot)
        assertEquals(request.format, result.format)
        assertEquals(LegalityStatus.REVIEW_REQUIRED, result.status)
        assertEquals(2, result.sourceLegalities.size)
        assertTrue(result.sourceLegalities.all { it.provenance.sourceDate == Knowledge.Known(UtcTimestamp(123)) })
        assertEquals(LegalityStatus.LEGAL, perEntry(result, "source_eligibility", "land").status)
        assertEquals(LegalityStatus.LEGAL, perEntry(result, "commander_eligibility", "commander").status)
        assertEquals(2, ports.statusCalls)
        assertEquals(before, request)
        assertTrue(request.deck.format is Knowledge.Unknown)
    }

    @Test fun factsCacheIncludesSelectedPrintingAndSourceEligibilityCacheUsesExactOracleOnly() = runBlocking {
        val ports = Ports()
        val oracle = f.oracle("island")
        val printing = f.printing()
        val entries = arrayOf(f.entry("a", 20, oracle = Knowledge.Known(oracle)), f.entry("b", 20, oracle = Knowledge.Known(oracle)),
            f.entry("c", 20, oracle = Knowledge.Known(oracle), printing = Knowledge.Known(printing)),
            f.entry("d", 20, oracle = Knowledge.Known(oracle), printing = Knowledge.Known(printing)),
            f.entry("e", 20, oracle = Knowledge.Known(oracle), printing = Knowledge.Known(f.printing("other"))))
        val result = ok(ports.engine().analyze(f.request(*entries), f.cancel))
        assertEquals(listOf(oracle to null, oracle to printing, oracle to f.printing("other")), ports.factCalls)
        assertEquals(listOf(oracle), ports.legalityCalls)
        assertEquals(1, result.sourceLegalities.size)
        assertEquals(LegalityStatus.LEGAL, result.checks.single { it.id == "deck_count" }.status)
        ok(ports.engine().analyze(f.request(*entries), f.cancel))
        assertEquals(6, ports.factCalls.size)
        assertEquals(2, ports.legalityCalls.size)
    }

    @Test fun maybeEntriesAreNotLookedUpAndForeignOrMissingIdentitiesDoNotGuessNames() = runBlocking {
        val ports = Ports()
        val unknown = f.entry("unknown", 20, oracle = f.unknown())
        val foreign = f.entry("foreign", 20, oracle = Knowledge.Known(OracleId(ProviderId("foreign"), "oracle")))
        val foreignPrinting = f.entry("foreign-print", 20, oracle = Knowledge.Known(f.oracle("island")), printing = Knowledge.Known(CardId(ProviderId("foreign"), "printing")))
        val maybe = f.entry("maybe", 40, BoardRole.MAYBEBOARD)
        val result = ok(ports.engine().analyze(f.request(unknown, foreign, foreignPrinting, maybe), f.cancel))
        assertTrue(ports.factCalls.isEmpty())
        assertEquals(listOf(f.oracle("island")), ports.legalityCalls)
        assertEquals(LegalityStatus.UNKNOWN, perEntry(result, "singleton", "unknown").status)
        assertEquals(LegalityStatus.UNKNOWN, perEntry(result, "color_identity", "foreign-print").status)
        assertEquals(LegalityStatus.LEGAL, perEntry(result, "source_eligibility", "foreign-print").status)
        assertTrue(result.checks.none { EntityId("maybe") in it.entryIds })
    }

    @Test fun structuralSnapshotAndSourceIdPreflightRejectBeforeAllIo() = runBlocking {
        val entry = f.entry()
        val base = f.request(entry)
        val badOracle = entry.copy(identity = entry.identity.copy(oracleId = Knowledge.Known(f.oracle("é".repeat(65)))))
        val badUnicode = entry.copy(identity = entry.identity.copy(printingId = Knowledge.Known(f.printing("bad\uD800"))))
        val invalid = listOf(base.copy(deck = f.deck(entry, entry)), base.copy(deck = base.deck.copy(name = " ")),
            f.request(badOracle), f.request(badUnicode), base.copy(snapshot = f.snapshot.copy(generation = null)),
            base.copy(snapshot = f.snapshot.copy(revision = Revision(1))), base.copy(snapshot = f.snapshot.copy(language = LanguageTag("ja"))),
            base.copy(snapshot = f.snapshot.copy(source = ProviderId("foreign"))))
        for (request in invalid) {
            val ports = Ports()
            failure(FailureCode.INVALID_INPUT, ports.engine().analyze(request, f.cancel))
            assertEquals(0, ports.statusCalls)
            assertTrue(ports.factCalls.isEmpty()); assertTrue(ports.legalityCalls.isEmpty())
        }
        val exact = Ports()
        val exactEntry = entry.copy(identity = entry.identity.copy(oracleId = Knowledge.Known(f.oracle("é".repeat(64)))))
        ok(exact.engine().analyze(f.request(exactEntry), f.cancel))
        assertEquals(1, exact.factCalls.size)
    }

    @Test fun mappedMissingFactsAndLegalitiesProduceUnknownOnlyAfterFinalStatusFence() = runBlocking {
        for (reason in listOf(UnavailableReason.NOT_FOUND, UnavailableReason.NOT_INSTALLED, UnavailableReason.SOURCE_UNAVAILABLE)) {
            val ports = Ports()
            ports.readFacts = { _, _, _ -> Outcome.Unavailable(reason, "synthetic missing") }
            ports.readLegality = { _, _ -> Outcome.Unavailable(reason, "synthetic missing") }
            val result = ok(ports.engine().analyze(request(), f.cancel))
            assertEquals(LegalityStatus.UNKNOWN, perEntry(result, "source_eligibility", "commander").status)
            assertEquals(LegalityStatus.UNKNOWN, perEntry(result, "commander_eligibility", "commander").status)
            assertEquals(LegalityStatus.REVIEW_REQUIRED, result.status)
            assertTrue(result.sourceLegalities.isEmpty())
            assertEquals(2, ports.statusCalls)
        }
        val ports = Ports()
        ports.readFacts = { _, _, _ -> Outcome.Unavailable(UnavailableReason.NOT_FOUND, "missing") }
        ports.readLegality = { _, _ -> Outcome.Unavailable(UnavailableReason.SOURCE_UNAVAILABLE, "not ready") }
        val stale = f.status().copy(activeGenerations = SnapshotList.of(f.status().activeGenerations.single().copy(id = GenerationId("changed"))))
        ports.statuses = listOf(Outcome.Success(f.status()), Outcome.Success(stale))
        failure(FailureCode.STALE_GENERATION, ports.engine().analyze(request(), f.cancel))
    }

    @Test fun unsupportedCorruptStaleAndOtherSourceFailuresPropagateInsteadOfBecomingUnknown() = runBlocking {
        val failures: List<Outcome<Nothing>> = listOf(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "unsupported"),
            Outcome.Unavailable(UnavailableReason.OFFLINE, "offline"), Outcome.Unknown("unknown transport evidence", SnapshotList.empty()),
            Outcome.Failure(DomainFailure(FailureCode.CORRUPT_DATA, "corrupt", SnapshotList.empty())),
            Outcome.Failure(DomainFailure(FailureCode.STALE_GENERATION, "stale", SnapshotList.empty())))
        for (expected in failures) {
            val facts = Ports()
            facts.readFacts = { _, _, _ -> expected }
            assertEquals(expected, facts.engine().analyze(request(), f.cancel))
            val legalities = Ports()
            legalities.readLegality = { _, _ -> expected }
            assertEquals(expected, legalities.engine().analyze(request(), f.cancel))
        }
    }

    @Test fun activeSnapshotBeforeAndAfterIsRequiredEvenForEmptyDeckAndFailuresRemainTyped() = runBlocking {
        val stale = f.status().copy(source = Knowledge.Known(ProviderId("foreign")))
        for (empty in listOf(false, true)) for (before in listOf(false, true)) {
            val ports = Ports()
            ports.statuses = if (before) listOf(Outcome.Success(stale)) else listOf(Outcome.Success(f.status()), Outcome.Success(stale))
            failure(FailureCode.STALE_GENERATION, ports.engine().analyze(if (empty) f.request() else request(), f.cancel))
            assertEquals(if (before) 1 else 2, ports.statusCalls)
        }
        val error = Outcome.Failure(DomainFailure(FailureCode.PERSISTENCE, "status failure", SnapshotList.empty()))
        val ports = Ports()
        ports.statuses = listOf(Outcome.Success(f.status()), error)
        assertEquals(error, ports.engine().analyze(f.request(), f.cancel))
        val missing = Ports()
        missing.statuses = listOf(Outcome.Success(f.status().copy(initialized = false, activeGenerations = SnapshotList.empty())))
        assertEquals(UnavailableReason.NOT_INSTALLED, (missing.engine().analyze(f.request(), f.cancel) as Outcome.Unavailable).reason)
    }

    @Test fun factsOraclePrintingSnapshotSourceVersionLanguageAndFacesAreValidated() = runBlocking {
        val card = f.island()
        val base = f.facts(card, f.printing())
        val face = CardFace(FaceIdentity(f.oracle("wrong"), 0, f.unknown()), "face", f.unknown(), f.unknown(), f.unknown(), card.stats, SnapshotList.empty())
        val invalid = listOf(base.copy(snapshot = f.snapshot.copy(generation = GenerationId("wrong"))), base.copy(printingId = null),
            base.copy(card = card.copy(oracleId = f.oracle("wrong"))), base.copy(card = card.copy(language = LanguageTag("ja"))),
            base.copy(card = card.copy(provenance = f.provenance().copy(provider = ProviderId("foreign")))),
            base.copy(card = card.copy(provenance = f.provenance().copy(generation = GenerationId("wrong")))),
            base.copy(card = card.copy(provenance = f.provenance().copy(version = Knowledge.Known("projection-version")))),
            base.copy(card = card.copy(faces = SnapshotList.of(face))), base.copy(card = card.copy(revision = Revision(1))),
            base.copy(card = card.copy(colorIdentity = Knowledge.Known(SnapshotList.of(Color.BLUE, Color.BLUE)))))
        val entry = f.entry("island", oracle = Knowledge.Known(card.oracleId), printing = Knowledge.Known(f.printing()))
        for (value in invalid) {
            val ports = Ports()
            ports.readFacts = { _, _, _ -> Outcome.Success(value) }
            failure(FailureCode.CORRUPT_DATA, ports.engine().analyze(f.request(entry), f.cancel))
        }
    }

    @Test fun legalityStatusAndOracleFormatSnapshotProviderGenerationVersionAreValidated() = runBlocking {
        val base = f.legality()
        val invalid = listOf(base.copy(oracleId = f.oracle("wrong")), base.copy(formatId = FormatId(f.source, "modern")),
            base.copy(snapshot = f.snapshot.copy(generation = GenerationId("wrong"))),
            base.copy(provenance = f.provenance().copy(provider = ProviderId("other"))),
            base.copy(provenance = f.provenance().copy(generation = GenerationId("other"))),
            base.copy(provenance = f.provenance().copy(version = Knowledge.Known("projection-v1"))),
            base.copy(status = Knowledge.Known(LegalityStatus.UNKNOWN)), base.copy(status = Knowledge.Known(LegalityStatus.REVIEW_REQUIRED)),
            base.copy(status = Knowledge.Absent("Not reported")), base.copy(formatId = FormatId(f.source, "Commander")))
        for (value in invalid) {
            val ports = Ports()
            ports.readLegality = { _, _ -> Outcome.Success(value) }
            val request = f.request(f.entry(oracle = Knowledge.Known(f.oracle())))
            failure(FailureCode.CORRUPT_DATA, ports.engine().analyze(request, f.cancel))
        }
        val unknown = Ports()
        unknown.readLegality = { oracle, _ -> Outcome.Success(f.legality(oracle, f.unknown())) }
        assertEquals(LegalityStatus.UNKNOWN, perEntry(ok(unknown.engine().analyze(request(), f.cancel)), "source_eligibility", "land").status)
    }

    @Test fun fullFactsAndLegalityMetadataShareOneBudgetAndInvalidUnusedFieldsFail() = runBlocking {
        val ports = Ports()
        val large = "x".repeat(17 * 1024 * 1024)
        ports.readFacts = { oracle, printing, _ -> Outcome.Success(f.facts(f.card(oracle.raw, text = Knowledge.Known(large)), printing)) }
        ports.readLegality = { oracle, _ -> Outcome.Success(f.legality(oracle, Knowledge.Unknown(large, SnapshotList.empty()))) }
        failure(FailureCode.CORRUPT_DATA, ports.engine().analyze(f.request(f.entry()), f.cancel))
        for (card in listOf(f.card().copy(manaValue = Knowledge.Known(BigDecimal("1e-1000000"))),
            f.card().copy(originalText = Knowledge.Unknown("missing", SnapshotList.of(Evidence("private\uD800", null, null)))),
            f.card().copy(faces = SnapshotList.copyOf(List(17) { CardFace(FaceIdentity(f.oracle(), it, f.unknown()), "face", f.unknown(), f.unknown(), f.unknown(), f.card().stats, SnapshotList.empty()) })))) {
            val invalid = Ports()
            invalid.readFacts = { _, _, _ -> Outcome.Success(f.facts(card)) }
            val request = f.request(f.entry(oracle = Knowledge.Known(f.oracle())))
            val result = invalid.engine().analyze(request, f.cancel)
            failure(FailureCode.CORRUPT_DATA, result)
            assertFalse(result.toString().contains("private"))
        }
    }

    @Test fun cancellationBeforeIoDuringSupplementaryTextAndInsideSourceReadPropagates() = runBlocking {
        val exception = CancellationException("synthetic local cancellation")
        val cancelled = object : CancelToken {
            override val isCancelled = true
            override fun throwIfCancelled(): Unit = throw exception
        }
        val before = Ports()
        try { before.engine().analyze(request(), cancelled); fail("Expected cancellation") }
        catch (actual: CancellationException) { assertSame(exception, actual) }
        assertEquals(0, before.statusCalls)
        val during = Ports()
        var reading = false
        var polls = 0
        during.readFacts = { oracle, printing, _ -> reading = true; Outcome.Success(f.facts(f.card(oracle.raw, text = Knowledge.Known("x" + "😀".repeat(100_000))), printing)) }
        val token = object : CancelToken {
            override val isCancelled get() = reading && polls >= 70
            override fun throwIfCancelled() { if (reading && ++polls == 70) throw exception }
        }
        try { during.engine().analyze(request(), token); fail("Expected interior cancellation") }
        catch (actual: CancellationException) { assertSame(exception, actual) }
        assertTrue(during.legalityCalls.isEmpty())
        var closed = false
        var returned = false
        val native = Ports()
        native.readLegality = { _, _ -> try { awaitCancellation() } finally { closed = true } }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { native.engine().analyze(request(), f.cancel); returned = true }
        job.cancelAndJoin()
        assertTrue(closed)
        assertFalse(returned)
    }
}
