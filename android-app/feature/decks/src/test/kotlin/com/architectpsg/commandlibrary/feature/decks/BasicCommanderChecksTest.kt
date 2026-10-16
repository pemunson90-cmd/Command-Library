package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*
import java.math.BigDecimal
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

/** Synthetic installed English facts; no live banned-list or exhaustive rules acceptance. */
internal object BasicValidationFixtures {
    val source = ProviderId("scryfall")
    val generation = GenerationId("generation")
    val snapshot = SnapshotReference(source, generation, null, LanguageTag.ENGLISH)
    val format = FormatId(source, "commander")
    val cancel = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    fun <T> unknown(): Knowledge<T> = Knowledge.Unknown("Not reported", SnapshotList.empty())
    fun provenance() = Provenance(source, ResourceUri("https://example.test/source"), Knowledge.Known("installed-version"),
        Knowledge.Known(UtcTimestamp(123)), UtcTimestamp(456), unknown(), generation, null, null)
    fun oracle(id: String = "oracle") = OracleId(source, id)
    fun printing(id: String = "printing") = CardId(source, id)
    fun types(types: List<String> = listOf("Creature"), superTypes: List<String> = emptyList(), subtypes: List<String> = emptyList()) =
        TypeLine((superTypes + types + subtypes).joinToString(" "), SnapshotList.copyOf(superTypes), SnapshotList.copyOf(types), SnapshotList.copyOf(subtypes))
    fun card(id: String = "oracle", name: String = id, types: Knowledge<TypeLine> = Knowledge.Known(types()),
        text: Knowledge<String> = Knowledge.Known(""), identity: Knowledge<SnapshotList<Color>> = Knowledge.Known(SnapshotList.of(Color.BLUE))) =
        CardRecord(oracle(id), name, text, Knowledge.Known("{U}"), Knowledge.Known(BigDecimal.ONE), unknown(), identity, types,
            CardStats(unknown(), unknown(), unknown(), unknown()), unknown(), Knowledge.Known("normal"), unknown(), name,
            LanguageTag.ENGLISH, SnapshotList.empty(), SnapshotList.empty(), provenance(), null)
    fun commander(id: String = "commander") = card(id, types = Knowledge.Known(types(superTypes = listOf("Legendary"))))
    fun island(id: String = "island") = card(id, name = "Island", types = Knowledge.Known(types(listOf("Land"), listOf("Basic"), listOf("Island"))))
    fun facts(card: CardRecord = card(), printing: CardId? = null) = CatalogDeckFacts(card, snapshot, printing, unknown(), unknown())
    fun legality(oracle: OracleId = oracle(), status: Knowledge<LegalityStatus> = Knowledge.Known(LegalityStatus.LEGAL)) =
        CatalogFormatLegality(oracle, format, snapshot, status, provenance())
    fun entry(id: String = "entry", copies: Int = 1, board: BoardRole = BoardRole.MAIN, oracle: Knowledge<OracleId> = Knowledge.Known(oracle(id)),
        printing: Knowledge<CardId> = Knowledge.Absent("Not selected")) = DeckEntry(EntityId(id),
        CardIdentity(oracle, printing, null, Knowledge.Known("Localized display name"), LanguageTag("ja")), copies, board,
        unknown(), "", SnapshotList.empty(), SnapshotList.empty())
    fun deck(vararg entries: DeckEntry) = DeckSnapshot(DeckId(ProviderId("local"), "deck"), "Deck", unknown(), "", unknown(), SnapshotList.empty(),
        UtcTimestamp(1), UtcTimestamp(2), Revision(3), SnapshotList.of(*entries), unknown(), null, SnapshotList.empty(), SnapshotList.empty())
    fun request(vararg entries: DeckEntry) = BasicDeckValidationRequest(deck(*entries), snapshot, BasicDeckFormat.COMMANDER)
    fun row(id: String = "entry", copies: Int = 1, board: BoardRole = BoardRole.MAIN, card: CardRecord? = card(id),
        status: Knowledge<LegalityStatus>? = Knowledge.Known(LegalityStatus.LEGAL)): BasicEntryEvidence =
        BasicEntryEvidence(entry(id, copies, board, Knowledge.Known(card?.oracleId ?: oracle(id))), card?.let { facts(it) },
            status?.let { legality(card?.oracleId ?: oracle(id), it) })
    fun assess(vararg rows: BasicEntryEvidence): BasicDeckValidationResult {
        val request = request(*rows.map { it.entry }.toTypedArray())
        return BasicCommanderChecks.assess(request, rows.filter { it.entry.board != BoardRole.MAYBEBOARD }) {}
    }
    fun status() = LocalCatalogStatus(StorageMode.FULL, LanguageTag.ENGLISH,
        SnapshotList.of(CatalogGeneration(generation, provenance(), SnapshotList.of(LanguageTag.ENGLISH), "scryfall", CatalogCounts(1, 1, 0, 0), GenerationStatus.ACTIVE)),
        Knowledge.Known(source), Knowledge.Known(UtcTimestamp(123)), Knowledge.Known(UtcTimestamp(456)), SnapshotList.of(LanguageTag.ENGLISH), true)
}

class BasicCommanderChecksTest {
    private val f = BasicValidationFixtures
    private fun check(result: BasicDeckValidationResult, id: String) = result.checks.single { it.id == id }
    private fun perEntry(result: BasicDeckValidationResult, rule: String, id: String) = result.checks.single {
        it.id.startsWith(rule + "_") && it.entryIds == listOf(EntityId(id))
    }
    private fun commander() = f.row("commander", board = BoardRole.COMMANDER, card = f.commander())
    private fun main(copies: Int = 99) = f.row("land", copies, card = f.island())

    @Test fun countedBoardsConserveCopiesWhileMaybeSideboardAndCompanionStayDistinct() {
        val result = f.assess(commander(), main(), f.row("maybe", 50, BoardRole.MAYBEBOARD), f.row("side", 2, BoardRole.SIDEBOARD),
            f.row("companion", 1, BoardRole.COMPANION))
        assertEquals(LegalityStatus.LEGAL, check(result, "deck_count").status)
        assertEquals(listOf(EntityId("commander"), EntityId("land")), check(result, "deck_count").entryIds)
        assertEquals(LegalityStatus.NOT_LEGAL, check(result, "sideboard").status)
        assertEquals(LegalityStatus.REVIEW_REQUIRED, check(result, "companion").status)
        assertEquals(LegalityStatus.NOT_LEGAL, result.status)
        assertTrue(result.checks.none { EntityId("maybe") in it.entryIds })
    }

    @Test fun countsAndCommanderPresenceAreDefiniteWithoutAutoAssigningMain() {
        for (copies in listOf(98, 100)) assertEquals(LegalityStatus.NOT_LEGAL, check(f.assess(commander(), main(copies)), "deck_count").status)
        val missing = f.assess(main(100))
        assertEquals(LegalityStatus.NOT_LEGAL, check(missing, "commander_presence").status)
        assertEquals(LegalityStatus.NOT_LEGAL, missing.status)
        val empty = f.assess()
        assertEquals(LegalityStatus.NOT_LEGAL, empty.status)
        assertEquals(LegalityStatus.NOT_LEGAL, check(empty, "deck_count").status)
        assertEquals(LegalityStatus.UNKNOWN, check(empty, "commander_identity_context").status)
    }

    @Test fun pairedRolesSupportPresenceButNeverImplyAValidArrangement() {
        for (role in listOf(BoardRole.PARTNER, BoardRole.BACKGROUND)) {
            val designated = f.row("designated", board = role, card = f.commander("designated"))
            val result = f.assess(designated, main())
            assertEquals(LegalityStatus.LEGAL, check(result, "commander_presence").status)
            assertEquals(LegalityStatus.REVIEW_REQUIRED, check(result, "commander_arrangement").status)
            assertEquals(LegalityStatus.REVIEW_REQUIRED, result.status)
        }
        val pair = f.assess(commander(), f.row("partner", board = BoardRole.PARTNER, card = f.commander("partner")), main(98))
        assertEquals(LegalityStatus.LEGAL, check(pair, "deck_count").status)
        assertEquals(LegalityStatus.REVIEW_REQUIRED, check(pair, "commander_arrangement").status)
        assertEquals(LegalityStatus.REVIEW_REQUIRED, pair.status)
        val doubled = f.assess(commander().copy(entry = commander().entry.copy(quantity = 2)), main(98))
        assertEquals(LegalityStatus.NOT_LEGAL, perEntry(doubled, "commander_quantity", "commander").status)
    }

    @Test fun legendaryCreatureVehicleAndSpacecraftPositiveEligibilityUseRealTypeAndBoxFacts() {
        val creature = f.assess(commander(), main())
        assertEquals(LegalityStatus.LEGAL, perEntry(creature, "commander_eligibility", "commander").status)
        for (subtype in listOf("Vehicle", "Spacecraft")) {
            val card = f.card("vehicle", types = Knowledge.Known(f.types(listOf("Artifact"), listOf("Legendary"), listOf(subtype))))
                .copy(stats = CardStats(Knowledge.Known("*"), Knowledge.Known("5"), f.unknown(), f.unknown()))
            val result = f.assess(f.row("commander", board = BoardRole.COMMANDER, card = card), main())
            assertEquals(LegalityStatus.LEGAL, perEntry(result, "commander_eligibility", "commander").status)
            val missingBox = f.assess(f.row("commander", board = BoardRole.COMMANDER, card = card.copy(stats = card.stats.copy(power = f.unknown()))), main())
            assertEquals(LegalityStatus.UNKNOWN, perEntry(missingBox, "commander_eligibility", "commander").status)
        }
    }

    @Test fun singleAppropriateFaceCanSupplyEligibilityButMultifaceAndSpecialCardsNeedReview() {
        val card = f.card("face", types = f.unknown())
        val face = CardFace(FaceIdentity(card.oracleId, 0, f.unknown()), "Face", Knowledge.Known(""), f.unknown(),
            Knowledge.Known(f.types(superTypes = listOf("Legendary"))), card.stats, SnapshotList.empty())
        val known = f.assess(f.row("commander", board = BoardRole.COMMANDER, card = card.copy(faces = SnapshotList.of(face))), main())
        assertEquals(LegalityStatus.LEGAL, perEntry(known, "commander_eligibility", "commander").status)
        val multiface = f.assess(f.row("commander", board = BoardRole.COMMANDER, card = card.copy(layout = Knowledge.Known("transform"), faces = SnapshotList.of(face))), main())
        assertEquals(LegalityStatus.REVIEW_REQUIRED, perEntry(multiface, "commander_eligibility", "commander").status)
        val special = f.card("special", types = Knowledge.Known(f.types(listOf("Planeswalker"), listOf("Legendary"))), text = Knowledge.Known("This card can be your commander."))
        val result = f.assess(f.row("commander", board = BoardRole.COMMANDER, card = special), main())
        assertEquals(LegalityStatus.REVIEW_REQUIRED, perEntry(result, "commander_eligibility", "commander").status)
        assertNotEquals(LegalityStatus.NOT_LEGAL, result.status)
        val unknown = f.assess(f.row("commander", board = BoardRole.COMMANDER, card = f.commander().copy(types = f.unknown())), main())
        assertEquals(LegalityStatus.UNKNOWN, perEntry(unknown, "commander_eligibility", "commander").status)
    }

    @Test fun colorIdentityUsesWholeCardFactsAndNeverCostsColorsOrProduction() {
        val red = f.card("red", identity = Knowledge.Known(SnapshotList.of(Color.RED))).copy(colors = Knowledge.Known(SnapshotList.of(Color.BLUE)), manaCost = Knowledge.Known("{U}"))
        val fixed = f.assess(commander(), f.row("red", 99, card = red))
        assertEquals(LegalityStatus.NOT_LEGAL, perEntry(fixed, "color_identity", "red").status)
        assertEquals(LegalityStatus.NOT_LEGAL, fixed.status)
        val unknownIdentity = f.assess(commander(), f.row("unknown", 99, card = f.card(identity = f.unknown())))
        assertEquals(LegalityStatus.UNKNOWN, perEntry(unknownIdentity, "color_identity", "unknown").status)
        val colorsOnly = f.assess(commander().copy(facts = f.facts(f.commander().copy(colorIdentity = f.unknown(), colors = Knowledge.Known(SnapshotList.of(Color.BLUE))))), main())
        assertEquals(LegalityStatus.UNKNOWN, perEntry(colorsOnly, "color_identity", "land").status)
    }

    @Test fun missingOrUnassessedChoiceTextCannotCreateFalseDefiniteColorViolation() {
        val red = f.row("red", 99, card = f.card("red", identity = Knowledge.Known(SnapshotList.of(Color.RED))))
        for (text in listOf("Choose a color before the game begins.", "Ordinary nonempty text.")) {
            val cmd = commander().copy(facts = f.facts(f.commander().copy(originalText = Knowledge.Known(text))))
            val result = f.assess(cmd, red)
            assertEquals(LegalityStatus.REVIEW_REQUIRED, check(result, "commander_identity_context").status)
            assertEquals(LegalityStatus.REVIEW_REQUIRED, perEntry(result, "color_identity", "red").status)
            assertEquals(LegalityStatus.REVIEW_REQUIRED, result.status)
            assertTrue(check(result, "commander_identity_context").message.contains("not assessed"))
        }
        val absent = f.assess(commander().copy(facts = f.facts(f.commander().copy(originalText = f.unknown()))), red)
        assertEquals(LegalityStatus.UNKNOWN, check(absent, "commander_identity_context").status)
        assertEquals(LegalityStatus.UNKNOWN, perEntry(absent, "color_identity", "red").status)
        assertEquals(LegalityStatus.REVIEW_REQUIRED, absent.status)
    }

    @Test fun emptyRootTextDoesNotEstablishIdentityAcrossUnsupportedLayoutsOrUnresolvedFaceText() {
        val card = f.commander()
        fun face(text: Knowledge<String>, index: Int = 0) = CardFace(FaceIdentity(card.oracleId, index, f.unknown()),
            "Face", text, f.unknown(), card.types, card.stats, SnapshotList.empty())
        val emptyFace = face(Knowledge.Known(""))
        val forest = f.row("forest", 99, card = f.card("forest", identity = Knowledge.Known(SnapshotList.of(Color.RED)),
            types = Knowledge.Known(f.types(listOf("Land"), subtypes = listOf("Forest")))))
        val cases = listOf(
            card.copy(faces = SnapshotList.of(face(Knowledge.Known("Choose a color before the game begins.")))) to LegalityStatus.REVIEW_REQUIRED,
            card.copy(faces = SnapshotList.of(face(f.unknown()))) to LegalityStatus.UNKNOWN,
            card.copy(layout = Knowledge.Known("transform"), faces = SnapshotList.of(emptyFace, face(Knowledge.Known(""), 1))) to LegalityStatus.REVIEW_REQUIRED,
            card.copy(layout = Knowledge.Known("transform"), faces = SnapshotList.of(emptyFace, face(f.unknown(), 1))) to LegalityStatus.UNKNOWN,
            card.copy(layout = Knowledge.Known("future")) to LegalityStatus.REVIEW_REQUIRED,
            card.copy(layout = f.unknown()) to LegalityStatus.UNKNOWN)
        for ((candidate, status) in cases) {
            val result = f.assess(f.row("commander", board = BoardRole.COMMANDER, card = candidate), forest)
            assertEquals(status, check(result, "commander_identity_context").status)
            assertEquals(status, perEntry(result, "color_identity", "forest").status)
            assertEquals(status, perEntry(result, "basic_land_types", "forest").status)
            assertEquals(LegalityStatus.REVIEW_REQUIRED, result.status)
        }
        val supported = f.assess(f.row("commander", board = BoardRole.COMMANDER, card = card.copy(faces = SnapshotList.of(emptyFace))), forest)
        assertEquals(LegalityStatus.LEGAL, check(supported, "commander_identity_context").status)
        assertEquals(LegalityStatus.NOT_LEGAL, perEntry(supported, "color_identity", "forest").status)
        assertEquals(LegalityStatus.NOT_LEGAL, perEntry(supported, "basic_land_types", "forest").status)
    }

    @Test fun pairedCommanderIdentityUnionSupportsComparisonsWhilePairingStillNeedsReview() {
        val redCommander = f.commander("red-commander").copy(colorIdentity = Knowledge.Known(SnapshotList.of(Color.RED)))
        val main = f.row("red", 98, card = f.card("red", identity = Knowledge.Known(SnapshotList.of(Color.RED))))
        val result = f.assess(commander(), f.row("partner", board = BoardRole.PARTNER, card = redCommander), main)
        assertEquals(LegalityStatus.LEGAL, perEntry(result, "color_identity", "red").status)
        assertEquals(LegalityStatus.REVIEW_REQUIRED, check(result, "commander_arrangement").status)
        assertEquals(LegalityStatus.REVIEW_REQUIRED, result.status)
    }

    @Test fun basicLandTypeRuleIsSeparateFromReportedColorIdentityAndProduction() {
        val forest = f.card("forest", types = Knowledge.Known(f.types(listOf("Land"), subtypes = listOf("Forest"))),
            identity = Knowledge.Known(SnapshotList.empty()))
        val row = f.row("forest", 99, card = forest).copy(facts = f.facts(forest).copy(reportedProduction = Knowledge.Known(SnapshotList.of("U"))))
        val result = f.assess(commander(), row)
        assertEquals(LegalityStatus.LEGAL, perEntry(result, "color_identity", "forest").status)
        assertEquals(LegalityStatus.NOT_LEGAL, perEntry(result, "basic_land_types", "forest").status)
        assertTrue("903.5d" in perEntry(result, "basic_land_types", "forest").ruleReferences)
        val missing = f.assess(commander(), f.row("unknown", 99, card = f.card(types = f.unknown())))
        assertEquals(LegalityStatus.UNKNOWN, perEntry(missing, "basic_land_types", "unknown").status)
    }

    @Test fun singletonEnglishNameAndOracleGroupsAreCandidatesNotHardRejections() {
        val a = f.row("a", 49, card = f.card("shared", name = "Exact English Name"))
        val b = f.row("b", 50, card = f.card("shared", name = "Other Canonical Name"))
        val sameOracle = f.assess(commander(), a, b)
        assertEquals(LegalityStatus.REVIEW_REQUIRED, perEntry(sameOracle, "singleton", "a").status)
        assertTrue(perEntry(sameOracle, "singleton", "a").message.contains("duplicate candidate"))
        val sameName = f.assess(commander(), a, f.row("b", 50, card = f.card("different", name = "Exact English Name")))
        assertTrue(perEntry(sameName, "singleton", "b").message.contains("duplicate candidate"))
        assertEquals(LegalityStatus.REVIEW_REQUIRED, sameName.status)
        val separate = f.assess(commander(), f.row("a", card = f.card("a", name = "A")), f.row("b", 98, card = f.card("b", name = "B")))
        assertEquals(LegalityStatus.REVIEW_REQUIRED, check(separate, "singleton_exception_coverage").status)
        assertEquals(LegalityStatus.REVIEW_REQUIRED, separate.status)
    }

    @Test fun basicLandExemptionAndUnassessedCopyAllowancesRemainDistinctFromMissingText() {
        val basic = f.assess(commander(), main())
        assertEquals(LegalityStatus.LEGAL, perEntry(basic, "singleton", "land").status)
        for (text in listOf("A deck can have any number of cards named Example.", "A deck can have up to seven cards named Example.")) {
            val result = f.assess(commander(), f.row("copies", 99, card = f.card("copies", text = Knowledge.Known(text))))
            assertEquals(LegalityStatus.REVIEW_REQUIRED, perEntry(result, "singleton", "copies").status)
            assertEquals(LegalityStatus.REVIEW_REQUIRED, result.status)
        }
        val missing = f.assess(commander(), f.row("unknown", 99, card = f.card(text = f.unknown())))
        assertEquals(LegalityStatus.UNKNOWN, perEntry(missing, "singleton", "unknown").status)
    }

    @Test fun sourceEligibilityAggregationKeepsReportedStatusesSeparateAndDatesIntact() {
        for ((reported, overall) in listOf(LegalityStatus.LEGAL to LegalityStatus.REVIEW_REQUIRED,
            LegalityStatus.RESTRICTED to LegalityStatus.REVIEW_REQUIRED, LegalityStatus.BANNED to LegalityStatus.NOT_LEGAL,
            LegalityStatus.NOT_LEGAL to LegalityStatus.NOT_LEGAL)) {
            val result = f.assess(commander(), main().copy(legality = f.legality(f.island().oracleId, Knowledge.Known(reported))))
            assertEquals(overall, result.status)
            assertEquals(Knowledge.Known(reported), result.sourceLegalities.last().status)
            assertEquals(Knowledge.Known(UtcTimestamp(123)), result.sourceLegalities.last().provenance.sourceDate)
            assertNotEquals(LegalityStatus.LEGAL, result.status)
        }
        val missing = f.assess(commander(), main().copy(legality = null))
        assertEquals(LegalityStatus.UNKNOWN, perEntry(missing, "source_eligibility", "land").status)
        assertEquals(LegalityStatus.REVIEW_REQUIRED, missing.status)
        assertEquals("2026-09-25", missing.rulesVersion)
        val bannedWithoutFacts = f.assess(commander(), main().copy(facts = null,
            legality = f.legality(f.island().oracleId, Knowledge.Known(LegalityStatus.BANNED))))
        assertEquals(LegalityStatus.UNKNOWN, perEntry(bannedWithoutFacts, "color_identity", "land").status)
        assertEquals(LegalityStatus.NOT_LEGAL, bannedWithoutFacts.status)
    }

    @Test fun supplementaryBoundsTextCannotSkipInteriorCancellation() {
        val text = "x" + "😀".repeat(4_096)
        val exception = CancellationException("supplementary interior cancellation")
        var polls = 0
        val budget = BasicValidationBounds({ if (++polls == 4) throw exception })
        // Entry/index-zero/final-charge alone give only three polls. Four proves interior polling.
        try { budget.text(text); fail("Expected interior cancellation") }
        catch (actual: CancellationException) { assertSame(exception, actual) }
        assertEquals(4, polls)
        assertEquals(8_193, text.length)
    }

    @Test fun boundedChecksUseStaticMessagesAndReferencesWithoutPerCopyExpansionOrMutation() {
        val row = f.row("huge", Int.MAX_VALUE, card = f.card(text = Knowledge.Known("private payload")))
        val request = f.request(row.entry)
        val before = request.copy()
        val result = BasicCommanderChecks.assess(request, listOf(row)) {}
        assertTrue(result.checks.size < 20)
        assertTrue(result.checks.all { it.message.length < 512 && it.ruleReferences.isNotEmpty() })
        assertFalse(result.checks.toString().contains("private payload"))
        assertEquals(before, request)
        val exception = CancellationException("local cancel")
        try { BasicCommanderChecks.assess(request, listOf(row)) { throw exception }; fail("Expected cancellation") }
        catch (actual: CancellationException) { assertSame(exception, actual) }
        try { validationAdd(Long.MAX_VALUE, 1); fail("Expected overflow") }
        catch (actual: ValidationRejection) { assertEquals(FailureCode.CORRUPT_DATA, (actual.outcome as Outcome.Failure).error.code) }
    }
}
