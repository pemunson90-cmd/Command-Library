package com.architectpsg.commandlibrary

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.ShellPorts
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
import java.util.UUID

/** Real provider-free engine/query/preparation/Room/clipboard and MainActivity. Synthetic source. */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class OfflinePromptAppIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private lateinit var ports: ShellPorts
    private lateinit var deck: DeckSnapshot
    private lateinit var snapshot: SnapshotReference
    private val privateMarker = "PRIVATE-NOTE-EXPLICIT-OPT-IN"
    private val reply = "Surprise angle: spicy tech\n[[Alpha Fixture]] [[A-Alpha Fixture]] [[Shared Fixture]] [[Missing Fixture]]"
    private fun <T> ok(value: Outcome<T>): T { assertTrue(value.toString(), value is Outcome.Success); return (value as Outcome.Success).value }
    private fun baseline(): List<Any> = runBlocking { listOf(ok(checkNotNull(ports.localDecks).load(deck.id, token)),
        ok(checkNotNull(ports.localDecks).list(PageRequest(100), token)), ok(checkNotNull(ports.localInventory).snapshot(token)),
        ok(ports.settings.read(token)), ok(ports.catalogStatus.status(token))) }
    private val seed = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            runBlocking {
                val app = ApplicationProvider.getApplicationContext<CommandLibraryApplication>()
                ports = ok(app.graph.ports())
                val suffix = UUID.randomUUID().toString()
                val unknown = Knowledge.Unknown("Synthetic source date", SnapshotList.empty())
                val bytes = corpus.toByteArray(Charsets.UTF_8)
                val source = CatalogImportSource(ResourceUri("content://selected/offline-prompt-$suffix"), "Synthetic offline prompt corpus", ProviderId("scryfall"), LanguageTag.ENGLISH,
                    unknown, Knowledge.Absent("Synthetic selected source"), Knowledge.Absent("No publisher hash"), Knowledge.Known(bytes.size.toLong()), Knowledge.Absent("Synthetic fixture"))
                val generation = ok(ports.catalogImport.`import`(source, flowOf(ByteChunk(0,0,SnapshotBytes(bytes),null,true)), {}, token)).generation.id
                snapshot = SnapshotReference(ProviderId("scryfall"), generation, null, LanguageTag.ENGLISH)
                val original = PlainDeckExportBoundaryFixture.source(DeckId(LocalDeckLibraryLimits.SOURCE, "offline-prompt-$suffix"), "Offline prompt $suffix", checkNotNull(ports.utcClock).utcNow())
                deck = original.copy(description=privateMarker, primer=privateMarker, entries=SnapshotList.copyOf(original.entries.mapIndexed { index, entry ->
                    if (index == 0) entry.copy(identity=CardIdentity(Knowledge.Known(OracleId(ProviderId("scryfall"),"alpha")), Knowledge.Known(CardId(ProviderId("scryfall"),"alpha-a")), null, Knowledge.Known("Alpha Fixture"), LanguageTag.ENGLISH), notes=privateMarker)
                    else entry
                }))
                ok(checkNotNull(ports.deckSnapshotValidation)(deck, token))
                ok(checkNotNull(ports.localDecks).create(deck, EntityId("offline-prompt-seed-$suffix"), token))
                // Real unprepared catalog is unavailable, never a false missing-card result.
                val offline = checkNotNull(ports.offlineDeckPrompts)
                val unprepared = ok(offline.review(deck, snapshot, "[[Alpha Fixture]]", token))
                assertEquals(OfflineReferenceStatus.UNAVAILABLE, unprepared.references.single().status)
                val preparation = checkNotNull(ports.catalogSearchPreparation)
                var state = ok(preparation.status(generation, token))
                var advances = 0
                while (state.readiness != CatalogSearchPreparationReadiness.READY) {
                    assertTrue("Bounded synthetic preparation", advances++ < 100)
                    state = ok(preparation.advance(generation, token))
                    assertNotEquals(CatalogSearchPreparationReadiness.FAILED, state.readiness)
                }
                val reviewed = ok(offline.review(deck, snapshot, reply, token))
                assertEquals(listOf(OfflineReferenceStatus.FOUND, OfflineReferenceStatus.FOUND, OfflineReferenceStatus.AMBIGUOUS, OfflineReferenceStatus.NOT_FOUND), reviewed.references.map { it.status })
                assertEquals(Knowledge.Known("spicy tech"), reviewed.statedSurpriseAngle)
                assertEquals(LegalityStatus.UNKNOWN, reviewed.formatLegality)
                assertTrue(reviewed.acquisitionCost is Knowledge.Unknown)
            }
            base.evaluate()
        }
    } }
    @get:Rule val chain: RuleChain = RuleChain.outerRule(seed).around(compose)
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun await(check: () -> Boolean) = compose.waitUntil(20_000) { compose.waitForIdle(); check() }
    private fun scroll(list: String, tag: String) = await {
        if (!exists(list)) false else try { compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag)); true } catch (_: AssertionError) { false }
    }
    private fun click(tag: String) { scroll("offline-prompt-list", tag); compose.onNodeWithTag(tag).assertIsEnabled().performClick() }
    private fun edit(tag: String, value: String) { scroll("offline-prompt-list", tag); compose.onNodeWithTag(tag).performTextReplacement(value) }
    private fun preview(): String {
        await { exists("offline-prompt-preview") }
        scroll("offline-prompt-list", "offline-prompt-preview")
        val config = compose.onNodeWithTag("offline-prompt-preview").fetchSemanticsNode().config
        return if (config.contains(SemanticsProperties.EditableText)) config[SemanticsProperties.EditableText].text
        else config[SemanticsProperties.Text].joinToString("") { it.text }
    }
    private fun copy(): String {
        val expected = preview(); click("offline-prompt-copy")
        val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = checkNotNull(clipboard.primaryClip).getItemAt(0).coerceToText(compose.activity).toString()
        assertEquals(expected, text)
        await { exists("offline-prompt-copy-feedback") }
        return text
    }
    @Test fun actualOfflineFourModeCopyPasteGroundingPrivateOptInRecreationAndNoWrites() {
        val before = baseline()
        await { exists("shell-nav-decks") || exists("shell-nav-more") }
        if (!exists("shell-nav-decks")) compose.onNodeWithTag("shell-nav-more").performClick()
        compose.onNodeWithTag("shell-nav-decks").performClick()
        val row = "deck-open-${deck.id.raw}"
        scroll("deck-library-list",row); compose.onNodeWithTag(row).performClick()
        scroll("deck-library-overview","deck-offline-prompt"); compose.onNodeWithTag("deck-offline-prompt").assertIsEnabled().performClick()
        edit("offline-prompt-constraints", "Budget is unknown; do not treat it as free.")
        for (mode in OfflineLabMode.entries) {
            click("offline-prompt-mode-${mode.name}"); click("offline-prompt-render")
            val text = copy()
            assertTrue(text.contains(deck.id.raw)); assertFalse(text.contains(privateMarker))
            for (entry in deck.entries) assertTrue(entry.id.value, text.contains(entry.id.value))
        }
        click("offline-prompt-include-notes"); click("offline-prompt-render"); assertTrue(copy().contains(privateMarker))
        click("offline-prompt-include-notes"); click("offline-prompt-render"); assertFalse(copy().contains(privateMarker))
        edit("offline-prompt-override", "{{deck}}")
        click("offline-prompt-render"); await { exists("offline-prompt-problem") }
        scroll("offline-prompt-list","offline-prompt-copy"); compose.onNodeWithTag("offline-prompt-copy").assertIsNotEnabled()
        click("offline-prompt-reset"); click("offline-prompt-render"); assertFalse(copy().contains(privateMarker))
        edit("offline-prompt-reply", reply); click("offline-prompt-review")
        await { exists("offline-prompt-reference-0") }
        scroll("offline-prompt-list","offline-prompt-angle")
        compose.onNodeWithTag("offline-prompt-angle").assertTextContains("spicy tech", substring=true)
        scroll("offline-prompt-list","offline-prompt-reference-2")
        compose.onNodeWithTag("offline-prompt-reference-2").assertIsNotEnabled()
        scroll("offline-prompt-list","offline-prompt-reference-3")
        compose.onNodeWithTag("offline-prompt-reference-3").assertIsNotEnabled()
        compose.activityRule.scenario.recreate()
        click("offline-prompt-reference-0"); await { exists("offline-prompt-detail") }
        compose.onNodeWithText("Alpha Fixture").assertIsDisplayed()
        compose.onNodeWithTag("offline-prompt-detail-back").performClick()
        assertEquals(before,baseline())
    }
    private val corpus = """[
{"object":"card","id":"alpha-a","oracle_id":"alpha","lang":"en","name":"Alpha Fixture","set":"aaa","collector_number":"1","rarity":"rare","finishes":["nonfoil"],"cmc":2,"colors":["U"],"color_identity":["U"],"type_line":"Creature — Wizard","power":"2","toughness":"3","oracle_text":"Draw a card.","keywords":["Flying"],"layout":"normal"},
{"object":"card","id":"a-alpha-p","oracle_id":"a-alpha","lang":"en","name":"A-Alpha Fixture","set":"aaa","collector_number":"2","rarity":"rare","cmc":2,"oracle_text":"Rebalanced fixture.","layout":"normal"},
{"object":"card","id":"shared-a-p","oracle_id":"shared-a","lang":"en","name":"Shared Fixture","set":"aaa","collector_number":"3","rarity":"rare","cmc":2,"oracle_text":"First fixture.","layout":"normal"},
{"object":"card","id":"shared-b-p","oracle_id":"shared-b","lang":"en","name":"Shared Fixture","set":"aaa","collector_number":"4","rarity":"rare","cmc":3,"oracle_text":"Second fixture.","layout":"normal"}
]"""
}
