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

/** Real import/READY preparation/AppGraph/Room/reader/engine/UI. Clearly synthetic cards, not official deck approval. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class BasicDeckValidationAppIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private lateinit var app: CommandLibraryApplication
    private lateinit var ports: ShellPorts
    private lateinit var deck: DeckSnapshot
    private lateinit var snapshot: SnapshotReference
    private val forestName = "Synthetic Basic Forest"
    private val commanderName = "Synthetic Green Commander"
    private val bannedName = "Synthetic Banned Relic"
    private fun <T> ok(value: Outcome<T>): T {
        assertTrue("Expected success: $value", value is Outcome.Success)
        return (value as Outcome.Success).value
    }
    private val seed = TestRule { base, description -> object : Statement() {
        override fun evaluate() {
            runBlocking {
                app = ApplicationProvider.getApplicationContext()
                ports = ok(app.graph.ports())
                val installed = when (description.methodName) {
                    "explicitCommanderAssessmentPreservesUnknownSavedFormatAndNeedsReview", "bannedExtraCardProducesDefiniteIssuesWithoutHidingReview" -> true
                    "missingCatalogDisablesBasicChecksWithUsefulCaption" -> false
                    else -> error("Explicitly define catalog setup for every case")
                }
                if (installed) {
                    val bytes = corpus.toByteArray(Charsets.UTF_8)
                    val source = CatalogImportSource(ResourceUri("content://selected/basic-validation-synthetic"), "Synthetic Commander evidence",
                        ProviderId("scryfall"), LanguageTag.ENGLISH, Knowledge.Unknown("No authentic source date", SnapshotList.empty()),
                        Knowledge.Absent("Selected fixture"), Knowledge.Absent("No publisher hash"), Knowledge.Known(bytes.size.toLong()),
                        Knowledge.Absent("Synthetic fixture"))
                    val generation = ok(ports.catalogImport.`import`(source, flowOf(ByteChunk(0, 0, SnapshotBytes(bytes), null, true)), {}, token)).generation.id
                    snapshot = SnapshotReference(ProviderId("scryfall"), generation, null, LanguageTag.ENGLISH)
                    val preparation = checkNotNull(ports.catalogSearchPreparation)
                    var state = ok(preparation.status(generation, token))
                    var advances = 0
                    while (state.readiness != CatalogSearchPreparationReadiness.READY) {
                        assertTrue("Bounded actual synthetic preparation", advances++ < 100)
                        state = ok(preparation.advance(generation, token))
                        assertNotEquals(CatalogSearchPreparationReadiness.FAILED, state.readiness)
                    }
                    assertEquals(CatalogSearchPreparationReadiness.READY, ok(preparation.status(generation, token)).readiness)
                }
                val original = PlainDeckExportBoundaryFixture.source(DeckId(LocalDeckLibraryLimits.SOURCE, "basic-validation-deck"),
                    "Synthetic Commander checks", checkNotNull(ports.utcClock).utcNow())
                fun entry(raw: String, name: String, copies: Int, board: BoardRole): DeckEntry = original.entries.first().copy(
                    id = EntityId("entry-$raw"), quantity = copies, board = board, labels = SnapshotList.empty(), roleOverrides = SnapshotList.empty(),
                    notes = "Private unchanged $name Ω", identity = CardIdentity(Knowledge.Known(OracleId(ProviderId("scryfall"), raw)),
                        Knowledge.Known(CardId(ProviderId("scryfall"), "$raw-print")), null, Knowledge.Known(name), LanguageTag.ENGLISH))
                val entries = mutableListOf(entry("basic-forest", forestName, 99, BoardRole.MAIN),
                    entry("green-commander", commanderName, 1, BoardRole.COMMANDER))
                if (description.methodName == "bannedExtraCardProducesDefiniteIssuesWithoutHidingReview")
                    entries += entry("banned-relic", bannedName, 1, BoardRole.MAIN)
                deck = original.copy(entries = SnapshotList.copyOf(entries), categoryOrder = SnapshotList.empty())
                assertTrue(deck.format is Knowledge.Unknown)
                ok(checkNotNull(ports.deckSnapshotValidation)(deck, token))
                deck = ok(checkNotNull(ports.localDecks).create(deck, EntityId("basic-validation-seed"), token))
                // This is intentionally absent at the worker base; ROOT must bind the actual reviewed backend.
                checkNotNull(ports.basicDeckValidation)
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
    private fun overview() {
        await { exists("shell-nav-decks") || exists("shell-nav-more") }
        if (!exists("shell-nav-decks")) compose.onNodeWithTag("shell-nav-more").performClick()
        compose.onNodeWithTag("shell-nav-decks").performClick()
        val row = "deck-open-${deck.id.raw}"
        scroll("deck-library-list", hasTestTag(row)); compose.onNodeWithTag(row).performClick()
        scroll("deck-library-overview", hasTestTag("basic-validation-open"))
    }
    private fun click(tag: String) {
        scroll("basic-validation-list", hasTestTag(tag))
        await { compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performClick()
    }
    private fun visible(resource: Int, vararg arguments: Any) {
        val expected = compose.activity.getString(resource, *arguments)
        scroll("basic-validation-list", hasText(expected))
        compose.onAllNodesWithText(expected).onFirst().assertIsDisplayed()
    }
    private fun openUnselected() {
        compose.onNodeWithTag("basic-validation-open").assertIsEnabled().performClick()
        scroll("basic-validation-list", hasTestTag("basic-validation-commander"))
        compose.onNodeWithTag("basic-validation-commander").assertIsNotSelected()
        compose.onNodeWithTag("basic-validation-assess").assertIsNotEnabled()
        assertFalse(exists("basic-validation-status"))
        visible(ShellR.string.basic_validation_choose)
    }
    private fun assess() {
        click("basic-validation-commander")
        assertFalse(exists("basic-validation-status")) // Choosing a format alone does not assess.
        click("basic-validation-assess")
        scroll("basic-validation-list", hasTestTag("basic-validation-status"))
    }
    private fun check(id: String, resource: Int) {
        val tag = "basic-validation-check-$id"
        scroll("basic-validation-list", hasTestTag(tag))
        compose.onNodeWithTag(tag).assertTextEquals(compose.activity.getString(resource))
    }
    private fun result(): BasicDeckValidationResult = runBlocking { ok(checkNotNull(ports.basicDeckValidation).analyze(
        BasicDeckValidationRequest(deck, snapshot, BasicDeckFormat.COMMANDER), token)) }
    private fun common(result: BasicDeckValidationResult) {
        assertEquals(deck.id, result.deckId); assertEquals(deck.revision, result.deckRevision)
        assertEquals(snapshot, result.snapshot); assertEquals(BasicDeckFormat.COMMANDER, result.format)
        assertEquals("2026-09-25", result.rulesVersion)
        assertTrue(result.checks.map { it.id }.distinct().size == result.checks.size)
        result.sourceLegalities.forEach { source ->
            assertEquals(snapshot, source.snapshot)
            assertEquals(FormatId(ProviderId("scryfall"), "commander"), source.formatId)
            assertTrue(source.provenance.sourceDate is Knowledge.Unknown)
        }
    }
    private fun baseline(): List<Any?> = runBlocking {
        val values = listOf(ok(checkNotNull(ports.localDecks).load(deck.id, token)), ok(checkNotNull(ports.localDecks).list(PageRequest(100), token)),
            ok(checkNotNull(ports.localInventory).snapshot(token)), ok(ports.settings.read(token)), ok(ports.catalogStatus.status(token)),
            ok(checkNotNull(ports.personalTags).load(token)), ok(checkNotNull(ports.providerProfiles).list(PageRequest(100), token)),
            ok(checkNotNull(ports.playerProfiles).list(PageRequest(100), token)),
            ok(checkNotNull(ports.ordinaryGames).history(GameHistoryFilter(), PageRequest(100), token)),
            ok(checkNotNull(ports.localDeckVersions).list(deck.id, PageRequest(100), token)))
        ok(StorageDatabase.open(app, AppGraph.DATABASE_FILE)).use { db ->
            assertEquals(deck, ok(SqlLocalDeckLibrary(db, DeckPayloadCodec(), checkNotNull(ports.utcClock), RandomUuidIdSource).load(deck.id, token)))
            values + listOf(listOf("deck_head", "deck_revision", "deck_operation", "deck_undo_link", "deck_undo_state", "deck_named_version",
                "deck_draft_state", "deck_draft_intent", "catalog_generation", "catalog_active", "catalog_source_receipt", "oracle_card", "card_face",
                "printing", "printing_oracle_membership", "catalog_search_build", "catalog_search_card_fact", "catalog_search_face_fact",
                "catalog_search_printing_fact", "catalog_search_legality_fact", "life_profile", "provider_profile", "personal_tag_pool",
                "inventory_lot", "inventory_state", "inventory_operation", "inventory_group", "inventory_change",
                "life_game", "life_event", "life_result", "life_alert", "life_seat_search").associateWith { table ->
                ok(db.query(SqlRequest("SELECT * FROM $table ORDER BY 1,2", SnapshotList.empty(), 100), token))
            })
        }
    }

    @Test fun missingCatalogDisablesBasicChecksWithUsefulCaption() {
        val before = baseline()
        overview()
        compose.onNodeWithTag("basic-validation-open").assertIsNotEnabled()
        val caption = compose.activity.getString(ShellR.string.deck_statistics_install_catalog)
        scroll("deck-library-overview", hasText(caption)); compose.onNodeWithText(caption).assertIsDisplayed()
        assertFalse(exists("basic-validation-list"))
        assertEquals(before, baseline())
    }

    @Test fun explicitCommanderAssessmentPreservesUnknownSavedFormatAndNeedsReview() {
        val before = baseline()
        val assessed = result()
        common(assessed)
        assertEquals(100, deck.entries.sumOf { it.quantity })
        assertEquals(LegalityStatus.REVIEW_REQUIRED, assessed.status)
        assertEquals(2, assessed.sourceLegalities.size)
        assertTrue(assessed.sourceLegalities.all { it.status == Knowledge.Known(LegalityStatus.LEGAL) })
        assertEquals(LegalityStatus.LEGAL, assessed.checks.single { it.id == "deck_count" }.status)
        assertEquals(LegalityStatus.LEGAL, assessed.checks.single { it.id == "commander_eligibility_1" }.status)
        assertEquals(LegalityStatus.LEGAL, assessed.checks.single { it.id == "singleton_0" }.status)
        assertEquals(LegalityStatus.REVIEW_REQUIRED, assessed.checks.single { it.id == "commander_identity_context" }.status)
        val coverage = assessed.checks.single { it.id == "singleton_exception_coverage" }
        assertEquals(LegalityStatus.REVIEW_REQUIRED, coverage.status)
        assertTrue(coverage.ruleReferences.contains("201.3"))
        assertEquals(deck.entries.map { it.id }, coverage.entryIds.toList())
        overview(); openUnselected(); assess()
        compose.onNodeWithTag("basic-validation-status").assertTextEquals(compose.activity.getString(ShellR.string.basic_validation_review))
        visible(ShellR.string.basic_validation_scope)
        check("deck_count", ShellR.string.basic_validation_legal)
        check("commander_eligibility_1", ShellR.string.basic_validation_legal)
        check("singleton_0", ShellR.string.basic_validation_legal)
        check("commander_identity_context", ShellR.string.basic_validation_review)
        check("singleton_exception_coverage", ShellR.string.basic_validation_review)
        visible(ShellR.string.basic_validation_reference, "201.3")
        visible(ShellR.string.basic_validation_entries, commanderName)
        visible(ShellR.string.basic_validation_source_row, forestName, compose.activity.getString(ShellR.string.basic_validation_source_legal))
        visible(ShellR.string.basic_validation_source_row, commanderName, compose.activity.getString(ShellR.string.basic_validation_source_legal))
        visible(ShellR.string.basic_validation_source_date, compose.activity.getString(ShellR.string.shell_value_unknown))
        compose.activityRule.scenario.recreate()
        scroll("basic-validation-list", hasTestTag("basic-validation-status"))
        compose.onNodeWithTag("basic-validation-status").assertTextEquals(compose.activity.getString(ShellR.string.basic_validation_review))
        scroll("basic-validation-list", hasTestTag("basic-validation-commander")); compose.onNodeWithTag("basic-validation-commander").assertIsSelected()
        click("basic-validation-close")
        scroll("deck-library-overview", hasTestTag("basic-validation-open")); openUnselected()
        click("basic-validation-close")
        assertEquals(before, baseline())
    }

    @Test fun bannedExtraCardProducesDefiniteIssuesWithoutHidingReview() {
        val before = baseline()
        val assessed = result()
        common(assessed)
        assertEquals(101, deck.entries.sumOf { it.quantity })
        assertEquals(LegalityStatus.NOT_LEGAL, assessed.status)
        val count = assessed.checks.single { it.id == "deck_count" }
        assertEquals(LegalityStatus.NOT_LEGAL, count.status); assertTrue(count.ruleReferences.contains("903.5a"))
        val banned = assessed.checks.single { it.id == "source_eligibility_2" }
        assertEquals(LegalityStatus.NOT_LEGAL, banned.status)
        assertEquals(listOf(deck.entries[2].id), banned.entryIds.toList())
        assertEquals(Knowledge.Known(LegalityStatus.BANNED), assessed.sourceLegalities.single { it.oracleId.raw == "banned-relic" }.status)
        assertEquals(LegalityStatus.REVIEW_REQUIRED, assessed.checks.single { it.id == "singleton_exception_coverage" }.status)
        overview(); openUnselected(); assess()
        compose.onNodeWithTag("basic-validation-status").assertTextEquals(compose.activity.getString(ShellR.string.basic_validation_not_legal))
        check("deck_count", ShellR.string.basic_validation_not_legal)
        check("source_eligibility_2", ShellR.string.basic_validation_not_legal)
        check("singleton_exception_coverage", ShellR.string.basic_validation_review)
        visible(ShellR.string.basic_validation_entries, bannedName)
        visible(ShellR.string.basic_validation_source_row, bannedName, compose.activity.getString(ShellR.string.basic_validation_banned))
        click("basic-validation-close")
        assertEquals(before, baseline())
    }
    private val corpus = """[
{"object":"card","id":"basic-forest-print","oracle_id":"basic-forest","lang":"en","name":"Synthetic Basic Forest","set":"val","collector_number":"1","rarity":"common","finishes":["nonfoil"],"mana_cost":"","cmc":0,"colors":[],"color_identity":["G"],"type_line":"Basic Land — Forest","oracle_text":"{T}: Add {G}.","layout":"normal","produced_mana":["G"],"all_parts":[],"legalities":{"commander":"legal"}},
{"object":"card","id":"green-commander-print","oracle_id":"green-commander","lang":"en","name":"Synthetic Green Commander","set":"val","collector_number":"2","rarity":"rare","finishes":["nonfoil"],"mana_cost":"{2}{G}","cmc":3,"colors":["G"],"color_identity":["G"],"type_line":"Legendary Creature — Elf","power":"2","toughness":"2","oracle_text":"Vigilance","layout":"normal","produced_mana":[],"all_parts":[],"legalities":{"commander":"legal"}},
{"object":"card","id":"banned-relic-print","oracle_id":"banned-relic","lang":"en","name":"Synthetic Banned Relic","set":"val","collector_number":"3","rarity":"rare","finishes":["nonfoil"],"mana_cost":"{1}","cmc":1,"colors":[],"color_identity":[],"type_line":"Artifact","oracle_text":"A synthetic banned fixture.","layout":"normal","produced_mana":[],"all_parts":[],"legalities":{"commander":"banned"}}
]"""
}
