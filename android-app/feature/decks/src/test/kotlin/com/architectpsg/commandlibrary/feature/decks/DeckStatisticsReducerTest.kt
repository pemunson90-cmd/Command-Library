package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*
import java.math.BigDecimal
import java.math.MathContext
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

/** Synthetic local source facts, never provider execution or gameplay/production guarantees. */
internal object StatisticsFixtures {
    val source = ProviderId("scryfall")
    val generation = GenerationId("captured-generation")
    val snapshot = SnapshotReference(source, generation, null, LanguageTag.ENGLISH)
    val cancel = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    fun <T> unknown(): Knowledge<T> = Knowledge.Unknown("Not reported", SnapshotList.empty())
    fun oracle(raw: String = "oracle") = OracleId(source, raw)
    fun printing(raw: String = "printing") = CardId(source, raw)
    fun provenance() = Provenance(source, ResourceUri("https://example.test/source"), unknown(), unknown(), UtcTimestamp(1),
        unknown(), generation, null, null)
    fun types(vararg names: String) = TypeLine(names.joinToString(" "), SnapshotList.empty(), SnapshotList.of(*names), SnapshotList.empty())
    fun card(id: OracleId = oracle(), mv: Knowledge<BigDecimal> = Knowledge.Known(BigDecimal("2")),
        cost: Knowledge<String> = Knowledge.Known("{1}{U}"), types: Knowledge<TypeLine> = Knowledge.Known(types("Creature")),
        layout: Knowledge<String> = Knowledge.Known("normal")) = CardRecord(
        oracleId = id, name = "Synthetic card", originalText = unknown(), manaCost = cost, manaValue = mv,
        colors = unknown(), colorIdentity = Knowledge.Known(SnapshotList.of(Color.BLUE)), types = types,
        stats = CardStats(unknown(), unknown(), unknown(), unknown()), keywords = unknown(), layout = layout,
        reserved = unknown(), normalizedSearchText = "synthetic card", language = LanguageTag.ENGLISH,
        faces = SnapshotList.empty(), providerIdentities = SnapshotList.empty(), provenance = provenance(), revision = null)
    fun facts(card: CardRecord = card(), printing: CardId? = null,
        production: Knowledge<SnapshotList<String>> = unknown(), parts: Knowledge<SnapshotList<ReportedCardPart>> = unknown()) =
        CatalogDeckFacts(card, snapshot, printing, production, parts)
    fun entry(id: String = "entry", copies: Int = 1, oracle: Knowledge<OracleId> = Knowledge.Known(oracle()),
        printing: Knowledge<CardId> = Knowledge.Absent("Not selected"), board: BoardRole = BoardRole.MAIN,
        labels: SnapshotList<EntryLabel> = SnapshotList.empty()) = DeckEntry(EntityId(id),
        CardIdentity(oracle, printing, null, unknown(), LanguageTag.ENGLISH), copies, board, unknown(), "", labels, SnapshotList.empty())
    fun deck(vararg entries: DeckEntry) = DeckSnapshot(DeckId(ProviderId("local"), "deck"), "Synthetic deck", unknown(), "",
        unknown(), SnapshotList.empty(), UtcTimestamp(0), UtcTimestamp(1), Revision(2), SnapshotList.of(*entries), unknown(),
        null, SnapshotList.empty(), SnapshotList.empty())
    fun filter(boards: SnapshotList<BoardRole> = SnapshotList.copyOf(BoardRole.entries), ids: SnapshotList<EntityId>? = null,
        tags: SnapshotList<EntityId> = SnapshotList.empty(), all: Boolean = false) = DeckStatisticsFilter(boards, ids, tags, all)
    fun request(vararg entries: DeckEntry, filter: DeckStatisticsFilter = filter()) = DeckStatisticsRequest(deck(*entries), snapshot, filter)
    fun status() = LocalCatalogStatus(StorageMode.FULL, LanguageTag.ENGLISH,
        SnapshotList.of(CatalogGeneration(generation, provenance(), SnapshotList.of(LanguageTag.ENGLISH), "synthetic", CatalogCounts(1, 1, 0, 0), GenerationStatus.ACTIVE)),
        Knowledge.Known(source), unknown(), unknown(), SnapshotList.of(LanguageTag.ENGLISH), true)
    fun token(raw: String = "token", name: Knowledge<String> = unknown(), type: Knowledge<String> = unknown(), component: String = "token") =
        ReportedCardPart(printing(raw), component, name, type)
    fun face(card: CardRecord, cost: String = "{9}{R}") = CardFace(FaceIdentity(card.oracleId, 0, unknown()), "Face", unknown(),
        Knowledge.Known(cost), card.types, card.stats, SnapshotList.empty())
    fun reduce(vararg pairs: Pair<DeckEntry, CatalogDeckFacts?>): DeckStatisticsResult {
        val request = request(*pairs.map { it.first }.toTypedArray())
        val bounds = DeckStatisticsBounds({ cancel.throwIfCancelled() })
        for (pair in pairs) pair.second?.let { bounds.facts(it) }
        return DeckStatisticsReducer.reduce(request, pairs.map { it.first }, pairs.map { EntryDeckFacts(it.first.id, it.second) }) { cancel.throwIfCancelled() }
    }
    fun error(code: FailureCode, action: () -> Unit) {
        try { action(); fail("Expected statistics failure") } catch (rejected: StatisticsRejection) {
            assertEquals(code, (rejected.outcome as Outcome.Failure).error.code)
        }
    }
    fun unsupported(action: () -> Unit) {
        try { action(); fail("Expected unsupported source fact") } catch (rejected: StatisticsRejection) {
            assertEquals(UnavailableReason.UNSUPPORTED, (rejected.outcome as Outcome.Unavailable).reason)
        }
    }
}

class DeckStatisticsReducerTest {
    private val f = StatisticsFixtures
    private fun number(value: Knowledge<BigDecimal>) = (value as Knowledge.Known).value
    private fun counts(values: List<StatisticCount>) = values.associate { it.name to it.copies }

    @Test fun weightedMeansUseKnownDenominatorsAndNumericManaBuckets() {
        val a = f.facts(f.card(mv = Knowledge.Known(BigDecimal("1.0"))))
        val b = f.facts(f.card(mv = Knowledge.Known(BigDecimal("1.00"))))
        val zeroLand = f.facts(f.card(mv = Knowledge.Known(BigDecimal.ZERO), cost = Knowledge.Known(""), types = Knowledge.Known(f.types("Land"))))
        val unknown = f.facts(f.card(mv = f.unknown()))
        val result = f.reduce(f.entry("a", 2) to a, f.entry("b", 3) to b, f.entry("land", 4) to zeroLand,
            f.entry("unknown", 5) to unknown, f.entry("unresolved", 6) to null)
        assertEquals(20L, result.copies)
        assertEquals(6L, result.unresolvedCopies)
        assertEquals(11L, result.unknownManaValueCopies)
        assertEquals(2, result.manaCurve.size)
        assertEquals(listOf(4L, 5L), result.manaCurve.map { it.copies })
        assertEquals(0, result.manaCurve[1].manaValue.compareTo(BigDecimal.ONE))
        assertEquals(9L, result.averageManaValue.copies)
        assertEquals(BigDecimal("5").divide(BigDecimal("9"), MathContext.DECIMAL128), number(result.averageManaValue.value))
        assertEquals(5L, result.averageNonlandManaValue.copies)
        assertEquals(0, number(result.averageNonlandManaValue.value).compareTo(BigDecimal.ONE))
    }

    @Test fun unknownTypesAreExcludedFromNonlandMeanAndOverlappingTypesAreIndependent() {
        val artifact = f.facts(f.card(types = Knowledge.Known(f.types("Artifact", "Creature"))))
        val hiddenType = f.facts(f.card(mv = Knowledge.Known(BigDecimal("9")), types = f.unknown()))
        val result = f.reduce(f.entry("a", 3) to artifact, f.entry("unknown", 5) to hiddenType)
        assertEquals(mapOf("Artifact" to 3L, "Creature" to 3L), counts(result.types))
        assertEquals(8L, result.copies)
        assertEquals(5L, result.unknownTypeCopies)
        assertEquals(3L, result.averageNonlandManaValue.copies)
        assertEquals(0, number(result.averageNonlandManaValue.value).compareTo(BigDecimal("2")))
        assertEquals(8L, result.averageManaValue.copies)
    }

    @Test fun emptyDenominatorsRemainUnknownAndKnownZeroIsNotMissing() {
        val empty = f.reduce()
        assertEquals(0L, empty.copies)
        assertEquals(0L, empty.averageManaValue.copies)
        assertTrue(empty.averageManaValue.value is Knowledge.Unknown)
        assertTrue(empty.averageNonlandManaValue.value is Knowledge.Unknown)
        val zero = f.reduce(f.entry(copies = 2) to f.facts(f.card(mv = Knowledge.Known(BigDecimal.ZERO), cost = Knowledge.Known(""))))
        assertEquals(Knowledge.Known(BigDecimal.ZERO), zero.averageManaValue.value)
        assertEquals(2L, zero.averageManaValue.copies)
        assertEquals(0L, zero.unknownCostCopies)
        assertTrue(zero.costSymbols.isEmpty())
        val unknown = f.reduce(f.entry(copies = 2) to null)
        assertEquals(2L, unknown.unknownManaValueCopies)
        assertEquals(2L, unknown.unknownTypeCopies)
        assertEquals(2L, unknown.unknownCostCopies)
        assertEquals(2L, unknown.unknownTokenReferenceCopies)
        assertEquals(0L, unknown.unknownLandProductionCopies)
        val missingCost = f.reduce(f.entry(copies = 3) to f.facts(f.card(cost = f.unknown())))
        assertEquals(3L, missingCost.unknownCostCopies)
        assertTrue(missingCost.costSymbols.isEmpty())
    }

    @Test fun rawSymbolAlternativesAndOccurrencesAreWeightedWithoutExpansion() {
        val cost = "{W}{W}{U/B}{2/R}{G/P}{W/U/P}{C}{S}{X}{Y}{Z}{0}{0007}"
        val result = f.reduce(f.entry(copies = 3) to f.facts(f.card(cost = Knowledge.Known(cost))))
        assertEquals(mapOf("{W}" to 6L, "{U/B}" to 3L, "{2/R}" to 3L, "{G/P}" to 3L, "{W/U/P}" to 3L,
            "{C}" to 3L, "{S}" to 3L, "{X}" to 3L, "{Y}" to 3L, "{Z}" to 3L, "{0}" to 3L, "{0007}" to 3L), counts(result.costSymbols))
        assertFalse(counts(result.costSymbols).containsKey("{B}"))
        val hugeInteger = "{" + "9".repeat(DeckStatisticsLimits.MAX_COST_BYTES - 2) + "}"
        assertEquals(listOf(hugeInteger), DeckStatisticsSymbols.read(hugeInteger) {})
    }

    @Test fun malformedAndFutureManaSymbolsHaveDistinctTypedOutcomes() {
        for (cost in listOf("W", "{}", "{W", "{W}}", "{{W}", "{ W}", "{W//U}", "{W/W}", "{1.5}", "{1/2}")) {
            f.error(FailureCode.CORRUPT_DATA) { DeckStatisticsSymbols.read(cost) {} }
        }
        for (cost in listOf("{H}", "{Q}", "{W/H}", "{3/U}", "{W/U/U}")) f.unsupported { DeckStatisticsSymbols.read(cost) {} }
    }

    @Test fun AlternativeLayoutsKeepTopLevelMvAndNeverSumAnyFaceCosts() {
        for (layout in listOf("split", "adventure", "transform", "modal_dfc", "meld", "future")) {
            val card = f.card(mv = Knowledge.Known(BigDecimal("4")), cost = Knowledge.Known("{U}"), layout = Knowledge.Known(layout))
            val result = f.reduce(f.entry(copies = 2) to f.facts(card.copy(faces = SnapshotList.of(f.face(card)))))
            assertEquals(2L, result.unknownCostCopies)
            assertTrue(result.costSymbols.isEmpty())
            assertEquals(0, number(result.averageManaValue.value).compareTo(BigDecimal("4")))
            assertEquals(1, result.issues.count { it.code == "statistics_alternative_cost" })
        }
        for (layout in listOf("normal", "saga", "class", "leveler", "prototype", "mutate")) {
            val card = f.card(cost = Knowledge.Known("{U}"), layout = Knowledge.Known(layout))
            assertEquals(mapOf("{U}" to 1L), counts(f.reduce(f.entry() to f.facts(card.copy(faces = SnapshotList.of(f.face(card))))).costSymbols))
        }
    }

    @Test fun landProductionCountsReportedPossibilitiesOncePerCopyWithoutIdentityInference() {
        val land = f.card(types = Knowledge.Known(f.types("Land")), cost = Knowledge.Known(""))
        val known = f.facts(land, production = Knowledge.Known(SnapshotList.of("W", "U", "C")))
        val empty = f.facts(land, production = Knowledge.Known(SnapshotList.empty()))
        val unknown = f.facts(land)
        val nonland = f.facts(production = Knowledge.Known(SnapshotList.of("R")))
        val result = f.reduce(f.entry("known", 2) to known, f.entry("empty", 3) to empty, f.entry("unknown", 4) to unknown,
            f.entry("nonland", 5) to nonland, f.entry("missing", 6) to null)
        assertEquals(mapOf("W" to 2L, "U" to 2L, "C" to 2L), counts(result.reportedLandProduction))
        assertEquals(4L, result.unknownLandProductionCopies)
        assertEquals(6L, result.unresolvedCopies)
        assertEquals(6L, result.unknownTypeCopies)
        assertEquals(20L, result.copies)
    }

    @Test fun tokenReferencesAreExactTokenComponentsAndMergeOptionalKnownMetadata() {
        val token = f.token()
        val named = f.token(name = Knowledge.Known("Token"))
        val typed = f.token(type = Knowledge.Known("Token Creature"))
        val parts = Knowledge.Known(SnapshotList.of(token, named, typed, f.token("combo", component = "combo_piece"),
            f.token("meld", component = "meld_result")))
        val result = f.reduce(f.entry("a", 5) to f.facts(parts = parts), f.entry("b", 7) to f.facts(parts = parts))
        assertEquals(1, result.tokenReferences.size)
        assertEquals(Knowledge.Known("Token"), result.tokenReferences.single().name)
        assertEquals(Knowledge.Known("Token Creature"), result.tokenReferences.single().typeLine)
        assertEquals(0L, result.unknownTokenReferenceCopies)
        val empty = f.reduce(f.entry() to f.facts(parts = Knowledge.Known(SnapshotList.empty())))
        assertTrue(empty.tokenReferences.isEmpty())
        assertEquals(0L, empty.unknownTokenReferenceCopies)
        assertEquals(1L, f.reduce(f.entry() to f.facts()).unknownTokenReferenceCopies)
        for (second in listOf(f.token(name = Knowledge.Known("Other")), f.token(type = Knowledge.Known("Other")))) {
            val first = f.token(name = Knowledge.Known("Token"), type = Knowledge.Known("Token Creature"))
            f.error(FailureCode.CORRUPT_DATA) { f.reduce(f.entry() to f.facts(parts = Knowledge.Known(SnapshotList.of(first, second)))) }
        }
    }

    @Test fun overlappingManualTagsDoNotDoubleHeadlineCopiesOrUseRoleOverrideStrings() {
        val a = EntryLabel(EntityId("a"), "Ramp")
        val b = EntryLabel(EntityId("b"), "Draw")
        val first = f.entry("first", 3, labels = SnapshotList.of(a, b))
        val second = f.entry("second", 2, labels = SnapshotList.of(b)).copy(roleOverrides = SnapshotList.of(
            RoleOverride(null, f.unknown(), "Ramp", true, UtcTimestamp(0))))
        val result = f.reduce(first to f.facts(), second to f.facts())
        assertEquals(5L, result.copies)
        assertEquals(listOf(3L, 5L), result.tags.map { it.copies })
        assertEquals(listOf(1, 2), result.tags.map { it.entries })
    }

    @Test fun boundedIssuesCoalesceAcrossManyUnresolvedEntriesAndQuantityArithmeticIsChecked() {
        val pairs = (0 until 100).map { f.entry("e$it", Int.MAX_VALUE) to null }
        val result = f.reduce(*pairs.toTypedArray())
        assertEquals(Int.MAX_VALUE.toLong() * 100, result.copies)
        assertEquals(1, result.issues.size)
        assertTrue(result.issues.single().evidence.isEmpty())
        assertNull(result.issues.single().entryId)
        assertTrue(result.issues.single().message.length < 256)
        f.error(FailureCode.CORRUPT_DATA) { statisticsAdd(Long.MAX_VALUE, 1) }
        assertEquals(Long.MAX_VALUE, statisticsAdd(Long.MAX_VALUE - 1, 1))
    }

    @Test fun budgetsValidateUnusedFaceAndKnowledgeMetadataAndBoundDecimalsBeforeAllocation() {
        for (mana in listOf(BigDecimal("-1"), BigDecimal("1e1"), BigDecimal("1e-33"), BigDecimal("9".repeat(65)))) {
            f.error(FailureCode.CORRUPT_DATA) { DeckStatisticsBounds({}).facts(f.facts(f.card(mv = Knowledge.Known(mana)))) }
        }
        DeckStatisticsBounds({}).facts(f.facts(f.card(mv = Knowledge.Known(BigDecimal("9".repeat(64))))))
        val card = f.card()
        for (bad in listOf(card.copy(faces = SnapshotList.copyOf(List(17) { f.face(card) })),
            card.copy(manaCost = Knowledge.Known("x".repeat(DeckStatisticsLimits.MAX_COST_BYTES + 1))),
            card.copy(types = Knowledge.Known(f.types(*List(65) { "T$it" }.toTypedArray()))),
            card.copy(types = Knowledge.Known(f.types("T".repeat(257)))),
            card.copy(originalText = Knowledge.Unknown("not reported", SnapshotList.of(Evidence("bad\uD800", null, null)))),
            card.copy(faces = SnapshotList.of(f.face(card).copy(text = Knowledge.Known("bad\uD800")))))) {
            f.error(FailureCode.CORRUPT_DATA) { DeckStatisticsBounds({}).facts(f.facts(bad)) }
        }
        f.error(FailureCode.CORRUPT_DATA) { DeckStatisticsBounds({}).facts(f.facts(parts = Knowledge.Known(SnapshotList.copyOf(List(129) { f.token("t$it") })))) }
        val cumulative = DeckStatisticsBounds({})
        val large = f.facts(card.copy(originalText = Knowledge.Known("x".repeat(17 * 1024 * 1024))))
        cumulative.facts(large)
        f.error(FailureCode.CORRUPT_DATA) { cumulative.facts(large) }
    }

    @Test fun reducerAndSymbolScansPropagateCancellationWithoutMutatingInputs() {
        val request = f.request(f.entry())
        val value = f.facts()
        val before = request.copy()
        val exception = CancellationException("synthetic cancellation")
        try { DeckStatisticsReducer.reduce(request, request.deck.entries, listOf(EntryDeckFacts(request.deck.entries.single().id, value))) { throw exception }; fail("Expected cancellation") }
        catch (actual: CancellationException) { assertSame(exception, actual) }
        var polls = 0
        try { DeckStatisticsSymbols.read("{" + "9".repeat(5000) + "}") { if (++polls == 100) throw exception }; fail("Expected cancellation") }
        catch (actual: CancellationException) { assertSame(exception, actual) }
        assertEquals(before, request)
        assertEquals("{1}{U}", value.card.manaCost.statisticsKnown())
    }
}
