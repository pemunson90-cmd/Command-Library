package com.architectpsg.commandlibrary

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
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
import java.math.BigDecimal
import java.util.UUID

/** Actual import/preparation/compiler/reader/Room and activity; all source records explicitly synthetic. */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class AdvancedQueryAppIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private lateinit var ports: ShellPorts
    private lateinit var generation: GenerationId
    private lateinit var deck: DeckSnapshot
    private lateinit var before: List<Any>
    private lateinit var originalAlpha: CardRecord
    private fun <T> ok(value: Outcome<T>): T { assertTrue(value.toString(), value is Outcome.Success); return (value as Outcome.Success).value }
    private fun baseline(): List<Any> = runBlocking { listOf(
        ok(checkNotNull(ports.localDecks).load(deck.id, token)),
        ok(checkNotNull(ports.localInventory).snapshot(token)), ok(ports.settings.read(token)), ok(ports.catalogStatus.status(token))) }
    private val seed = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val app = ApplicationProvider.getApplicationContext<CommandLibraryApplication>()
            runBlocking {
                ports = ok(app.graph.ports())
                val suffix = UUID.randomUUID().toString()
                val unknown = Knowledge.Unknown("Synthetic source date not supplied", SnapshotList.empty())
                val raw = corpus.toByteArray(Charsets.UTF_8)
                val source = CatalogImportSource(ResourceUri("content://selected/synthetic-query-$suffix"), "Synthetic typed-query corpus", ProviderId("scryfall"), LanguageTag.ENGLISH,
                    unknown, Knowledge.Absent("Selected synthetic source"), Knowledge.Absent("No publisher hash supplied"), Knowledge.Known(raw.size.toLong()), Knowledge.Absent("Synthetic fixture"))
                generation = ok(ports.catalogImport.`import`(source, flowOf(ByteChunk(0, 0, SnapshotBytes(raw), null, true)), {}, token)).generation.id
                originalAlpha = ok(ports.catalogLookup.lookupName("Alpha", PageRequest(10), token)).items.single()
                deck = PlainDeckExportBoundaryFixture.source(DeckId(LocalDeckLibraryLimits.SOURCE, "query-preserved-$suffix"), "Preserved query deck", checkNotNull(ports.utcClock).utcNow())
                ok(checkNotNull(ports.localDecks).create(deck, EntityId("query-preserved-request-$suffix"), token))
                assertEquals(CatalogSearchPreparationReadiness.NOT_PREPARED, ok(checkNotNull(ports.catalogSearchPreparation).status(generation, token)).readiness)
            }
            before = baseline()
            base.evaluate()
        }
    } }
    @get:Rule val chain: RuleChain = RuleChain.outerRule(seed).around(compose)
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun await(check: () -> Boolean) = compose.waitUntil(20_000) { compose.waitForIdle(); check() }
    private fun scroll(list: String, tag: String) = await {
        if (!exists(list)) false else try { compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag)); true } catch (_: AssertionError) { false }
    }
    private fun enabled(tag: String) = await { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
    private fun search(text: String, expectedCard: String? = null) {
        scroll("advanced-query-list", "advanced-query-input")
        compose.onNodeWithTag("advanced-query-input").performTextReplacement(text)
        scroll("advanced-query-list", "advanced-query-submit"); enabled("advanced-query-submit")
        compose.onNodeWithTag("advanced-query-submit").performClick()
        if (expectedCard != null) scroll("advanced-query-list", "advanced-query-card-$expectedCard")
    }
    @Test fun actualOfflinePreparationTypedQueriesDetailsUnsupportedSyntaxAndRecreationPreserveUserState() {
        await { exists("card-search-list") }
        scroll("card-search-list", "advanced-query-open"); compose.onNodeWithTag("advanced-query-open").performClick()
        scroll("advanced-query-list", "advanced-query-submit")
        compose.onNodeWithTag("advanced-query-submit").assertIsNotEnabled()
        runBlocking { assertEquals(CatalogSearchPreparationReadiness.NOT_PREPARED, ok(checkNotNull(ports.catalogSearchPreparation).status(generation, token)).readiness) }
        scroll("advanced-query-list", "advanced-query-prepare"); compose.onNodeWithTag("advanced-query-prepare").assertIsEnabled().performClick()
        scroll("advanced-query-list", "advanced-query-submit"); enabled("advanced-query-submit")
        runBlocking { assertEquals(CatalogSearchPreparationReadiness.READY, ok(checkNotNull(ports.catalogSearchPreparation).status(generation, token)).readiness) }
        search("set:aaa rarity:rare", "alpha") // Admitted original tokens are AAA/Rare, not normalized on disk.
        compose.onNodeWithTag("advanced-query-card-alpha").assertTextEquals("Alpha Fixture").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        scroll("advanced-query-list", "advanced-query-card-alpha")
        compose.onNodeWithTag("advanced-query-card-alpha").assertTextEquals("Alpha Fixture").performClick()
        await { compose.onAllNodesWithText("Alpha Fixture").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("card-printings").performScrollTo()
        await { exists("printing-alpha-a") }
        compose.onNodeWithTag("printing-alpha-a").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText(compose.activity.getString(ShellR.string.detail_back)).performScrollTo().performClick()
        search("name:(")
        scroll("advanced-query-list", "advanced-query-diagnostic")
        compose.onNodeWithTag("advanced-query-empty").assertDoesNotExist()
        search("owned>0")
        scroll("advanced-query-list", "advanced-query-problem")
        compose.onNodeWithTag("advanced-query-empty").assertDoesNotExist()
        search("NOT power>3", "alpha")
        // Actual prepared source/reader/compiler scope, including cross-face and missing-value behavior.
        runBlocking {
            val query = checkNotNull(ports.localCardQuery)
            val snapshot = SnapshotReference(ProviderId("scryfall"), generation, null, LanguageTag.ENGLISH)
            val crossing = QueryAst.And(SnapshotList.of(QueryAst.Numeric(SearchNumericField.POWER, Comparison.LT, BigDecimal(2)), QueryAst.Numeric(SearchNumericField.TOUGHNESS, Comparison.LT, BigDecimal(2))))
            fun context(policy: FaceMatchPolicy) = SearchContext(SnapshotList.of(snapshot), SnapshotList.of(LanguageTag.ENGLISH), policy)
            assertEquals(listOf("duo"), ok(query.local(crossing, context(FaceMatchPolicy.ANY_FACE), PageRequest(30), token)).items.map { it.oracleId.raw })
            assertTrue(ok(query.local(crossing, context(FaceMatchPolicy.SAME_FACE), PageRequest(30), token)).items.isEmpty())
            assertTrue(ok(query.local(crossing, context(FaceMatchPolicy.CANONICAL), PageRequest(30), token)).items.isEmpty())
            assertEquals(originalAlpha, ok(ports.catalogLookup.lookupName("Alpha", PageRequest(10), token)).items.single())
        }
        assertEquals(before, baseline())
    }
    private val corpus = """[
{"object":"card","id":"alpha-a","oracle_id":"alpha","lang":"en","name":"Alpha Fixture","set":"AAA","collector_number":"1","rarity":"Rare","finishes":["nonfoil","foil"],"cmc":2,"colors":["U"],"color_identity":["U"],"type_line":"Creature — Wizard","power":"2","toughness":"3","oracle_text":"Draw a card.","keywords":["Flying"],"layout":"Normal","reserved":false,"game_changer":false,"legalities":{"commander":"legal"}},
{"object":"card","id":"alpha-b","oracle_id":"alpha","lang":"en","name":"Alpha Fixture","set":"bbb","collector_number":"2","rarity":"common","finishes":["nonfoil"],"cmc":2,"colors":["U"],"color_identity":["U"],"type_line":"Creature — Wizard","power":"2","toughness":"3","oracle_text":"Draw a card.","keywords":["Flying"],"layout":"Normal","reserved":false,"game_changer":false,"legalities":{"commander":"legal"}},
{"object":"card","id":"beta-p","oracle_id":"beta","lang":"en","name":"Beta Fixture","set":"bbb","collector_number":"3","rarity":"common","finishes":["nonfoil"],"cmc":5,"colors":["R"],"color_identity":["R"],"type_line":"Creature — Warrior","power":"*","toughness":"4","oracle_text":"","keywords":[],"layout":"normal","reserved":false,"game_changer":false,"legalities":{"commander":"legal"}},
{"object":"card","id":"duo-p","oracle_id":"duo","lang":"en","name":"Front Fixture // Back Fixture","set":"ccc","collector_number":"4","rarity":"rare","finishes":["nonfoil"],"cmc":3,"color_identity":["U","R"],"type_line":"Creature — Wizard // Creature — Warrior","keywords":["First strike"],"layout":"modal_dfc","reserved":false,"game_changer":false,"legalities":{"commander":"legal"},"card_faces":[{"name":"Front Fixture","mana_cost":"{2}{U}","type_line":"Creature — Wizard","oracle_text":"Draw a card.","power":"1","toughness":"3","colors":["U"]},{"name":"Back Fixture","mana_cost":"{2}{R}","type_line":"Creature — Warrior","oracle_text":"Deal damage.","power":"5","toughness":"1","colors":["R"]}]},
{"object":"card","id":"delta-p","oracle_id":"delta","lang":"en","name":"Delta Fixture","set":"ddd","collector_number":"5","rarity":"common","cmc":0,"color_identity":[],"keywords":[],"layout":"normal","reserved":false,"legalities":{}}
]"""
}
