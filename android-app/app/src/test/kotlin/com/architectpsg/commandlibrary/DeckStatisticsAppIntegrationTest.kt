package com.architectpsg.commandlibrary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.data.storage.decks.codec.DeckPayloadCodec
import com.architectpsg.commandlibrary.di.AppGraph
import com.architectpsg.commandlibrary.di.RandomUuidIdSource
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckLibrary
import com.architectpsg.commandlibrary.feature.shell.ShellPorts
import com.architectpsg.commandlibrary.feature.shell.R as ShellR
import java.math.BigDecimal
import java.math.MathContext
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.annotation.Config

/** Actual selected synthetic import/AppGraph/facts/engine/Room/UI. No authentic corpus or device claim. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class DeckStatisticsAppIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private lateinit var app: CommandLibraryApplication
    private lateinit var ports: ShellPorts
    private lateinit var deck: DeckSnapshot
    private lateinit var snapshot: SnapshotReference
    private val ramp = EntryLabel(EntityId("statistics-ramp"), "Ramp")
    private val draw = EntryLabel(EntityId("statistics-draw"), "Draw")
    private fun <T> ok(value: Outcome<T>): T {
        assertTrue("Expected success: $value", value is Outcome.Success)
        return (value as Outcome.Success).value
    }
    private val seed = TestRule { base, description -> object : Statement() {
        override fun evaluate() {
            runBlocking {
                app = ApplicationProvider.getApplicationContext()
                ports = ok(app.graph.ports())
                // Import before Activity creation so the real shell's first status read sees the generation.
                when (description.methodName) {
                    "importedFactsWeightedStatisticsFiltersAndRecreationAreReadOnly" -> {
                        val bytes = corpus.toByteArray(Charsets.UTF_8)
                        val source = CatalogImportSource(ResourceUri("content://selected/statistics-synthetic"), "Synthetic statistics corpus",
                            ProviderId("scryfall"), LanguageTag.ENGLISH, Knowledge.Unknown("Synthetic date", SnapshotList.empty()),
                            Knowledge.Absent("Selected synthetic source"), Knowledge.Absent("No publisher hash"),
                            Knowledge.Known(bytes.size.toLong()), Knowledge.Absent("Synthetic fixture"))
                        val generation = ok(ports.catalogImport.`import`(source,
                            flowOf(ByteChunk(0, 0, SnapshotBytes(bytes), null, true)), {}, token)).generation.id
                        snapshot = SnapshotReference(ProviderId("scryfall"), generation, null, LanguageTag.ENGLISH)
                    }
                    "withoutCatalogStatisticsIsDisabledAndExplainsAdmission" -> Unit
                    else -> error("Define catalog setup explicitly for each case")
                }
                val original = PlainDeckExportBoundaryFixture.source(DeckId(LocalDeckLibraryLimits.SOURCE, "statistics-deck"),
                    "Synthetic statistics deck", checkNotNull(ports.utcClock).utcNow())
                fun entry(raw: String, name: String, quantity: Int, board: BoardRole, labels: SnapshotList<EntryLabel>): DeckEntry {
                    val id = EntityId("statistics-entry-$raw")
                    return original.entries.first().copy(id = id, quantity = quantity, board = board, labels = labels,
                        notes = "Private unchanged $raw Ω", identity = CardIdentity(Knowledge.Known(OracleId(ProviderId("scryfall"), raw)),
                            Knowledge.Known(CardId(ProviderId("scryfall"), "$raw-print")), null, Knowledge.Known(name), LanguageTag.ENGLISH),
                        roleOverrides = SnapshotList.copyOf(original.entries.first().roleOverrides.map { it.copy(entryId = id,
                            oracleId = Knowledge.Known(OracleId(ProviderId("scryfall"), raw))) }))
                }
                deck = original.copy(entries = SnapshotList.of(
                    entry("statistics-land", "Synthetic Forest", 4, BoardRole.MAIN, SnapshotList.of(ramp)),
                    entry("statistics-sorcery", "Synthetic Growth", 2, BoardRole.MAIN, SnapshotList.of(ramp, draw)),
                    entry("statistics-creature", "Synthetic Hybrid", 1, BoardRole.SIDEBOARD, SnapshotList.of(draw))),
                    categoryOrder = SnapshotList.of(ramp, draw))
                ok(checkNotNull(ports.deckSnapshotValidation)(deck, token))
                deck = ok(checkNotNull(ports.localDecks).create(deck, EntityId("statistics-deck-seed"), token))
                // Root binds the actual engine only after independent source review; never substitute a fake.
                checkNotNull(ports.deckStatistics)
            }
            base.evaluate()
        }
    } }
    @get:Rule val chain: RuleChain = RuleChain.outerRule(seed).around(compose)
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun await(check: () -> Boolean) = compose.waitUntil(20_000) { compose.waitForIdle(); check() }
    private fun scroll(list: String, matcher: SemanticsMatcher) = await {
        if (!exists(list)) false else try { compose.onNodeWithTag(list).performScrollToNode(matcher); true } catch (_: AssertionError) { false }
    }
    private fun navigate(tag: String) {
        await { exists(tag) || exists("shell-nav-more") }
        if (!exists(tag)) compose.onNodeWithTag("shell-nav-more").performClick()
        compose.onNodeWithTag(tag).performClick()
    }
    private fun overview() {
        navigate("shell-nav-decks")
        val row = "deck-open-${deck.id.raw}"
        scroll("deck-library-list", hasTestTag(row)); compose.onNodeWithTag(row).performClick()
        scroll("deck-library-overview", hasTestTag("deck-statistics-open"))
    }
    private fun click(tag: String) {
        scroll("deck-statistics-list", hasTestTag(tag))
        await { compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performClick()
    }
    private fun visible(resource: Int, vararg values: Any) {
        val text = compose.activity.getString(resource, *values)
        scroll("deck-statistics-list", hasText(text))
        compose.onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }
    private fun totals(copies: Long, entries: Int) {
        val expected = compose.activity.getString(ShellR.string.deck_statistics_totals, copies, entries, 0L)
        scroll("deck-statistics-list", hasTestTag("deck-statistics-totals"))
        await {
            compose.onAllNodes(hasTestTag("deck-statistics-totals") and hasText(expected)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("deck-statistics-totals").assertTextEquals(expected)
    }
    private fun calculate(copies: Long, entries: Int) { click("deck-statistics-compute"); totals(copies, entries) }
    private fun analyze(boards: List<BoardRole>, tags: List<EntityId> = emptyList(), all: Boolean = true,
        entries: List<EntityId>? = null): DeckStatisticsResult = runBlocking {
        ok(checkNotNull(ports.deckStatistics).analyze(DeckStatisticsRequest(deck, snapshot,
            DeckStatisticsFilter(SnapshotList.copyOf(boards), entries?.let { SnapshotList.copyOf(it) }, SnapshotList.copyOf(tags), all)), token))
    }
    private fun average(expected: BigDecimal, average: StatisticAverage, denominator: Long) {
        assertEquals(denominator, average.copies)
        assertEquals(0, expected.compareTo((average.value as Knowledge.Known).value))
    }
    private fun baseline(): List<Any?> = runBlocking {
        val state = listOf(ok(checkNotNull(ports.localDecks).load(deck.id, token)),
            ok(checkNotNull(ports.localDecks).list(PageRequest(100), token)), ok(checkNotNull(ports.localInventory).snapshot(token)),
            ok(ports.settings.read(token)), ok(ports.catalogStatus.status(token)), ok(checkNotNull(ports.personalTags).load(token)),
            ok(checkNotNull(ports.providerProfiles).list(PageRequest(100), token)),
            ok(checkNotNull(ports.localDeckVersions).list(deck.id, PageRequest(100), token)))
        ok(StorageDatabase.open(app, AppGraph.DATABASE_FILE)).use { db ->
            val reader = SqlLocalDeckLibrary(db, DeckPayloadCodec(), checkNotNull(ports.utcClock), RandomUuidIdSource)
            assertEquals(deck, ok(reader.load(deck.id, token)))
            // Bound fixture rows include deck/source payloads and receipts, not just current revision.
            state + listOf(listOf("deck_head", "deck_revision", "deck_operation", "deck_undo_link", "deck_undo_state",
                "deck_named_version", "deck_draft_state", "deck_draft_intent", "catalog_generation", "catalog_active",
                "catalog_source_receipt", "oracle_card", "card_face", "printing", "printing_oracle_membership").associateWith { table ->
                ok(db.query(SqlRequest("SELECT * FROM $table ORDER BY 1,2", SnapshotList.empty(), 100), token))
            })
        }
    }

    @Test fun withoutCatalogStatisticsIsDisabledAndExplainsAdmission() {
        val before = baseline()
        overview()
        compose.onNodeWithTag("deck-statistics-open").assertIsNotEnabled()
        val caption = compose.activity.getString(ShellR.string.deck_statistics_install_catalog)
        scroll("deck-library-overview", hasText(caption))
        compose.onNodeWithText(caption).assertIsDisplayed()
        assertFalse(exists("deck-statistics-list"))
        assertEquals(before, baseline())
    }

    @Test fun importedFactsWeightedStatisticsFiltersAndRecreationAreReadOnly() {
        val before = baseline()
        val main = analyze(listOf(BoardRole.MAIN))
        assertEquals(6L, main.copies)
        assertEquals(deck.entries.take(2).map { it.id }, main.entryIds.toList())
        average(BigDecimal(2), main.averageNonlandManaValue, 2)
        val both = listOf(BoardRole.MAIN, BoardRole.SIDEBOARD)
        val all = analyze(both)
        assertEquals(deck.id, all.deckId); assertEquals(deck.revision, all.deckRevision); assertEquals(snapshot, all.snapshot)
        assertEquals(7L, all.copies); assertEquals(0L, all.unresolvedCopies)
        assertEquals(mapOf(BigDecimal.ZERO to 4L, BigDecimal(2) to 2L, BigDecimal(3) to 1L), all.manaCurve.associate { it.manaValue.stripTrailingZeros() to it.copies })
        average(BigDecimal.ONE, all.averageManaValue, 7)
        average(BigDecimal(7).divide(BigDecimal(3), MathContext.DECIMAL128), all.averageNonlandManaValue, 3)
        assertEquals(mapOf("Land" to 4L, "Sorcery" to 2L, "Creature" to 1L), all.types.associate { it.name to it.copies })
        assertEquals(mapOf("{1}" to 3L, "{G}" to 2L, "{G/U}" to 1L, "{G/P}" to 1L), all.costSymbols.associate { it.name to it.copies })
        assertEquals(listOf(StatisticCount("G", 4)), all.reportedLandProduction.toList())
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L), listOf(all.unknownManaValueCopies, all.unknownTypeCopies,
            all.unknownCostCopies, all.unknownLandProductionCopies, all.unknownTokenReferenceCopies))
        assertEquals(ReportedCardPart(CardId(ProviderId("scryfall"), "statistics-token"), "token", Knowledge.Known("Synthetic Elf"),
            Knowledge.Known("Token Creature — Elf")), all.tokenReferences.single())
        assertEquals(mapOf(ramp.id to 6L, draw.id to 3L), all.tags.associate { it.label.id to it.copies })
        assertEquals(2L, analyze(both, listOf(ramp.id, draw.id)).copies)
        assertEquals(7L, analyze(both, listOf(ramp.id, draw.id), false).copies)
        val empty = analyze(both, entries = emptyList())
        assertEquals(0L, empty.copies); assertEquals(0L, empty.averageManaValue.copies)
        assertTrue(empty.averageManaValue.value is Knowledge.Unknown)

        // The actual shell reads the imported generation, without any QueryAst preparation.
        navigate("shell-nav-catalog")
        await { compose.onAllNodesWithText(snapshot.generation!!.value).fetchSemanticsNodes().isNotEmpty() }
        overview()
        compose.onNodeWithTag("deck-statistics-open").assertIsEnabled().performClick()
        totals(6, 2)
        visible(ShellR.string.deck_statistics_policy)
        visible(ShellR.string.deck_statistics_nonland_average, "2", 2L)
        click("deck-statistics-board-SIDEBOARD"); calculate(7, 3)
        visible(ShellR.string.deck_statistics_average, "1", 7L)
        visible(ShellR.string.deck_statistics_nonland_average, BigDecimal(7).divide(BigDecimal(3), MathContext.DECIMAL128).toPlainString(), 3L)
        visible(ShellR.string.deck_statistics_unknowns, 0L, 0L, 0L)
        visible(ShellR.string.deck_statistics_symbols)
        visible(ShellR.string.deck_statistics_pair, "{G/U}", 1L)
        visible(ShellR.string.deck_statistics_pair, "{G/P}", 1L)
        visible(ShellR.string.deck_statistics_production_caveat, 0L)
        visible(ShellR.string.deck_statistics_overlap)
        visible(ShellR.string.deck_statistics_token_caveat, 0L)
        scroll("deck-statistics-list", hasText("Synthetic Elf")); compose.onNodeWithText("Synthetic Elf").assertIsDisplayed()
        click("deck-statistics-tag-${ramp.id.value}"); click("deck-statistics-tag-${draw.id.value}"); calculate(2, 1)
        click("deck-statistics-any-tag"); calculate(7, 3)
        click("deck-statistics-selected-mode"); calculate(0, 0)
        visible(ShellR.string.deck_statistics_average, compose.activity.getString(ShellR.string.deck_statistics_unknown), 0L)
        click("deck-statistics-entry-${deck.entries[0].id.value}"); calculate(4, 1)
        click("deck-statistics-board-MAIN"); calculate(0, 0)
        click("deck-statistics-entry-${deck.entries[2].id.value}"); calculate(1, 1)
        compose.activityRule.scenario.recreate()
        totals(1, 1)
        scroll("deck-statistics-list", hasTestTag("deck-statistics-board-SIDEBOARD"))
        compose.onNodeWithTag("deck-statistics-board-SIDEBOARD").assertIsSelected()
        click("deck-statistics-close")
        assertEquals(before, baseline())
        scroll("deck-library-overview", hasTestTag("deck-statistics-open")); compose.onNodeWithTag("deck-statistics-open").performClick()
        totals(6, 2)
        click("deck-statistics-close")
        assertEquals(before, baseline())
    }

    private val corpus = """[
{"object":"card","id":"statistics-land-print","oracle_id":"statistics-land","lang":"en","name":"Synthetic Forest","set":"sta","collector_number":"1","rarity":"common","finishes":["nonfoil"],"mana_cost":"","cmc":0,"colors":[],"color_identity":["G"],"type_line":"Basic Land — Forest","oracle_text":"{T}: Add {G}.","layout":"normal","produced_mana":["G"],"all_parts":[]},
{"object":"card","id":"statistics-sorcery-print","oracle_id":"statistics-sorcery","lang":"en","name":"Synthetic Growth","set":"sta","collector_number":"2","rarity":"common","finishes":["nonfoil"],"mana_cost":"{1}{G}","cmc":2,"colors":["G"],"color_identity":["G"],"type_line":"Sorcery","oracle_text":"Synthetic reported relationship only.","layout":"normal","produced_mana":[],"all_parts":[{"id":"statistics-token","component":"token","name":"Synthetic Elf","type_line":"Token Creature — Elf","uri":"https://api.scryfall.com/cards/statistics-token"}]},
{"object":"card","id":"statistics-creature-print","oracle_id":"statistics-creature","lang":"en","name":"Synthetic Hybrid","set":"sta","collector_number":"3","rarity":"common","finishes":["nonfoil"],"mana_cost":"{1}{G/U}{G/P}","cmc":3,"colors":["G","U"],"color_identity":["G","U"],"type_line":"Creature — Elf","power":"2","toughness":"2","oracle_text":"Synthetic cost alternatives.","layout":"normal","produced_mana":[],"all_parts":[]}
]"""
}
