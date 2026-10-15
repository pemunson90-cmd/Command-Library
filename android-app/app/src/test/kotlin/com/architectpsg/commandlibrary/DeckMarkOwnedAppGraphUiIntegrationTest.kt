package com.architectpsg.commandlibrary

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.data.storage.collection.codec.LocalInventoryPatchCodec
import com.architectpsg.commandlibrary.data.storage.collection.codec.OwnedLotPayloadCodec
import com.architectpsg.commandlibrary.di.AppGraph
import com.architectpsg.commandlibrary.di.SystemUtcClock
import com.architectpsg.commandlibrary.feature.collection.persistence.SqlLocalInventory
import com.architectpsg.commandlibrary.feature.shell.ShellPorts
import com.architectpsg.commandlibrary.feature.shell.R as ShellR
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.annotation.Config

/** Real activity/ports/Room on the host, with synthetic selected-file catalog bytes only. */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class DeckMarkOwnedAppGraphUiIntegrationTest {
    private val rule = createAndroidComposeRule<MainActivity>()
    private val suffix = UUID.randomUUID().toString()
    private val cardName = "Synthetic mark owned $suffix"
    private val oracle = OracleId(ProviderId("scryfall"), "oracle-$suffix")
    private val printingId = CardId(ProviderId("scryfall"), "printing-$suffix")
    private val cancel = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private lateinit var ports: ShellPorts
    private lateinit var before: InventorySnapshot
    private lateinit var knownDeck: DeckSnapshot
    private lateinit var foreignDeck: DeckSnapshot
    private lateinit var originalLot: OwnedLot
    private lateinit var zeroLot: OwnedLot
    private lateinit var ambiguousLot: OwnedLot
    private lateinit var settingsBefore: SettingsSnapshot
    private lateinit var catalogBefore: LocalCatalogStatus

    private fun <T> ok(result: Outcome<T>): T {
        assertTrue(result.toString(), result is Outcome.Success)
        return (result as Outcome.Success).value
    }
    private val inventory get() = checkNotNull(ports.localInventory)
    private val library get() = checkNotNull(ports.localDecks)
    private fun snapshot() = runBlocking { ok(inventory.snapshot(cancel)) }
    private fun storedDeck(id: DeckId) = runBlocking { ok(library.load(id, cancel)) }

    // Actual immutable seed is complete before MainActivity creates its retained shell controllers.
    private val seed = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val app = ApplicationProvider.getApplicationContext<CommandLibraryApplication>()
            runBlocking {
                ports = ok(app.graph.ports())
                val bytes = """[{"object":"card","id":"${printingId.raw}","oracle_id":"${oracle.raw}","lang":"en","name":"$cardName","set":"tst","set_name":"Synthetic host fixtures","collector_number":"1","rarity":"common","finishes":["nonfoil"],"keywords":[],"scryfall_uri":"https://scryfall.com/card/tst/1/synthetic","oracle_text":"Draw a card.","layout":"normal"}]""".toByteArray(Charsets.UTF_8)
                val source = CatalogImportSource(ResourceUri("content://selected/mark-owned-$suffix"), "Synthetic selected fixture", ProviderId("scryfall"), LanguageTag.ENGLISH,
                    Knowledge.Unknown("Synthetic date not supplied", SnapshotList.empty()), Knowledge.Absent("Synthetic source URI not supplied"),
                    Knowledge.Absent("Synthetic checksum not supplied"), Knowledge.Known(bytes.size.toLong()), Knowledge.Absent("Synthetic notice not supplied"))
                ok(ports.catalogImport.`import`(source, flowOf(ByteChunk(0, 0, SnapshotBytes(bytes), null, true)), {}, cancel))
                val printing = ok(checkNotNull(ports.catalogPrintings).printing(printingId, cancel))
                assertEquals(oracle, (printing.oracleId as Knowledge.Known).value)
                assertEquals(LanguageTag.ENGLISH, printing.language)
                assertEquals(Knowledge.Known(SnapshotList.of(Finish.NONFOIL)), printing.finishes)
                assertNotNull(printing.provenance.generation)
                val knownIdentity = CardIdentity(Knowledge.Known(oracle), Knowledge.Known(printingId), null, Knowledge.Known(cardName), LanguageTag.ENGLISH)
                val foreignIdentity = CardIdentity(
                    Knowledge.Unknown("Original foreign Oracle unresolved", SnapshotList.of(Evidence("Original Oracle evidence", ResourceUri("content://selected/oracle-$suffix"), UtcTimestamp(123)))),
                    Knowledge.Unknown("Original foreign printing unresolved", SnapshotList.of(Evidence("Original printing evidence", null, UtcTimestamp(124)))),
                    ProviderIdentity(ProviderId("synthetic-foreign"), "Original discriminator", "Original raw $suffix:001"),
                    Knowledge.Known("Foreign unresolved $suffix"), LanguageTag("ja"))
                val now = checkNotNull(ports.utcClock).utcNow()
                fun deck(raw: String, name: String, entries: SnapshotList<DeckEntry>) = DeckSnapshot(
                    DeckId(LocalDeckLibraryLimits.SOURCE, raw), name, Knowledge.Absent("No format selected"), "Synthetic deck metadata", Knowledge.Absent("No folder"), SnapshotList.empty(),
                    now, now, Revision(0), entries, Knowledge.Absent("No budget"), null, SnapshotList.empty(), SnapshotList.empty(), "Strategy stays in the deck")
                val a = DeckEntry(EntityId("mark-entry-a-$suffix"), knownIdentity, 3, BoardRole.MAIN, Knowledge.Absent("No deck finish restriction"), "Strategy A must not transfer", SnapshotList.empty(), SnapshotList.empty())
                val b = a.copy(id = EntityId("mark-entry-b-$suffix"), notes = "Strategy B must not transfer")
                knownDeck = deck("mark-known-deck-$suffix", "Known mark owned $suffix", SnapshotList.of(a, b))
                val foreignEntry = DeckEntry(EntityId("mark-foreign-entry-$suffix"), foreignIdentity, 2, BoardRole.MAIN, Knowledge.Known(Finish.FOIL),
                    "Foreign strategy must not become physical notes", SnapshotList.empty(), SnapshotList.empty())
                foreignDeck = deck("mark-foreign-deck-$suffix", "Foreign mark owned $suffix", SnapshotList.of(foreignEntry))
                ok(library.create(knownDeck, EntityId("create-known-$suffix"), cancel))
                ok(library.create(foreignDeck, EntityId("create-foreign-$suffix"), cancel))
                knownDeck = ok(library.load(knownDeck.id, cancel))
                foreignDeck = ok(library.load(foreignDeck.id, cancel))
                // All merge-key metadata matches the independently specified mark-owned defaults.
                val initialKnown = OwnedLot(EntityId("mark-known-lot-$suffix"), knownIdentity, Knowledge.Known(Finish.NONFOIL), CardCondition.UNKNOWN, "", 2, "", Revision(0),
                    Knowledge.Absent("Acquisition time not supplied"))
                val unknownIdentity = foreignIdentity.copy(raw = ProviderIdentity(ProviderId("synthetic-foreign"), "Unselected evidence", "unselected-$suffix"))
                val initialZero = OwnedLot(EntityId("mark-zero-lot-$suffix"), unknownIdentity, Knowledge.Unknown("Original zero finish unknown", SnapshotList.of(Evidence("Zero finish evidence", null, null))),
                    CardCondition.DAMAGED, "Zero shelf", 0, "Zero note remains exact", Revision(0), Knowledge.Unknown("Original zero acquisition unknown", SnapshotList.empty()))
                // Language exclusions are definitive. A positive JA indeterminate candidate is
                // necessary for a genuinely incomplete foreign estimate; the zero row cannot do it.
                val initialAmbiguous = initialZero.copy(id = EntityId("mark-ambiguous-lot-$suffix"), quantity = 1, condition = CardCondition.UNKNOWN,
                    location = "Ambiguous shelf", notes = "Ambiguous note remains exact", identity = unknownIdentity.copy(raw = ProviderIdentity(ProviderId("synthetic-foreign"), "Other raw", "candidate-$suffix")))
                val initial = ok(inventory.snapshot(cancel))
                val seeded = ok(inventory.apply(CollectionPatch(SnapshotList.of(CollectionEdit.Add(initialKnown), CollectionEdit.Add(initialZero), CollectionEdit.Add(initialAmbiguous))),
                    initial.revision, EntityId("seed-mark-inventory-$suffix"), cancel))
                assertTrue(seeded is InventoryApplyResult.Committed)
                before = ok(inventory.snapshot(cancel))
                originalLot = before.lots.single { it.id == initialKnown.id }
                zeroLot = before.lots.single { it.id == initialZero.id }
                ambiguousLot = before.lots.single { it.id == initialAmbiguous.id }
                settingsBefore = ok(ports.settings.read(cancel))
                catalogBefore = ok(ports.catalogStatus.status(cancel))
                assertFalse(catalogBefore.installedLanguages.contains(LanguageTag("ja")))
            }
            base.evaluate()
        }
    } }
    @get:Rule val chain: RuleChain = RuleChain.outerRule(seed).around(rule)
    @Before fun hostBudget() = budget(300)
    @After fun resetBudget() = budget(60)
    private fun budget(seconds: Long) {
        Class.forName("androidx.test.espresso.IdlingPolicies")
            .getMethod("setMasterPolicyTimeout", Long::class.javaPrimitiveType, TimeUnit::class.java)
            .invoke(null, seconds, TimeUnit.SECONDS)
    }
    private fun await(check: () -> Boolean) = rule.waitUntil(20_000) { rule.waitForIdle(); check() }
    private fun enabled(tag: String) = await { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
    private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun scroll(listTag: String, matcher: SemanticsMatcher) {
        // A real async load may not have populated the LazyColumn yet. Retry only the
        // absent-node assertion; every successful action still scrolls the actual list.
        await {
            if (!exists(listTag)) false else try {
                rule.onNodeWithTag(listTag).performScrollToNode(matcher)
                true
            } catch (_: AssertionError) { false }
        }
    }
    private fun dialogScroll(tag: String) = scroll("mark-owned-list", hasTestTag(tag))
    private fun field(id: EntityId, kind: String, value: String) {
        val tag = "mark-owned-$kind-${id.value}"
        dialogScroll(tag); enabled(tag); rule.onNodeWithTag(tag).performTextReplacement(value)
    }
    private fun text(id: EntityId, kind: String): String {
        val tag = "mark-owned-$kind-${id.value}"
        dialogScroll(tag)
        return rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
    }
    private fun dialogClick(tag: String) { dialogScroll(tag); enabled(tag); rule.onNodeWithTag(tag).performClick() }
    private fun footerClick(tag: String) { enabled(tag); rule.onNodeWithTag(tag).performClick() }
    private fun navigate(tag: String) {
        // Compact navigation keeps secondary destinations in the actual More menu.
        await { exists(tag) || exists("shell-nav-more") }
        if (!exists(tag)) {
            enabled("shell-nav-more")
            rule.onNodeWithTag("shell-nav-more").performClick()
        }
        enabled(tag)
        rule.onNodeWithTag(tag).performClick()
    }
    private fun inventoryPane() {
        // Global Resume keeps the originating destination, including Cards, after save.
        navigate("shell-nav-collection")
        await { exists("collection-inventory-tab") || exists("collection-inventory") }
        if (exists("collection-inventory-tab")) rule.onNodeWithTag("collection-inventory-tab").performClick()
        await { exists("collection-inventory") }
    }
    private fun awaitCatalogGenerationUi() {
        navigate("shell-nav-catalog")
        val generation = catalogBefore.activeGenerations.first { it.status == GenerationStatus.ACTIVE }.id.value
        // Observe the actual shared status controller's rendered generation before asking
        // mark-owned for a current finish proof; a direct seed lookup cannot prove UI readiness.
        await { rule.onAllNodesWithText(generation).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(generation).performScrollTo().assertIsDisplayed()
    }
    private fun displayedRevision(revision: Revision) {
        inventoryPane()
        rule.onNodeWithTag("collection-inventory").performScrollToIndex(0)
        val label = rule.activity.getString(ShellR.string.collection_revision, revision.value)
        await { rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun openDeck(deck: DeckSnapshot) {
        navigate("shell-nav-collection")
        if (exists("collection-deck-tab")) rule.onNodeWithTag("collection-deck-tab").performClick()
        await { exists("collection-allocation") }
        val deckTag = "collection-deck-${deck.id.raw}"
        scroll("collection-allocation", hasTestTag(deckTag))
        enabled(deckTag); rule.onNodeWithTag(deckTag).performClick()
        scroll("collection-allocation", hasTestTag("collection-mark-owned"))
        footerClick("collection-mark-owned")
        val select = "mark-owned-select-${deck.entries.first().id.value}"
        dialogScroll(select); enabled(select)
    }
    private fun assertReadOnlyBaseline() {
        assertEquals(before, snapshot())
        assertEquals(knownDeck, storedDeck(knownDeck.id))
        assertEquals(foreignDeck, storedDeck(foreignDeck.id))
    }
    private fun assertSettingsAndCatalogUnchanged() = runBlocking {
        assertEquals(settingsBefore, ok(ports.settings.read(cancel)))
        assertEquals(catalogBefore, ok(ports.catalogStatus.status(cancel)))
    }
    private fun reopenedSnapshot(): InventorySnapshot = runBlocking {
        val app = rule.activity.application as CommandLibraryApplication
        ok(StorageDatabase.open(app, AppGraph.DATABASE_FILE)).use { db ->
            val reader = SqlLocalInventory(db, OwnedLotPayloadCodec(), LocalInventoryPatchCodec(), SystemUtcClock, ok(db.capabilities(cancel)).maxBoundParameters)
            ok(reader.snapshot(cancel))
        }
    }
    private fun undoThroughUi(after: InventorySnapshot): InventorySnapshot {
        displayedRevision(after.revision)
        rule.onNodeWithTag("collection-inventory").performScrollToNode(hasTestTag("collection-undo"))
        footerClick("collection-undo")
        await { snapshot().revision.value == after.revision.value + 1 }
        val undone = snapshot()
        displayedRevision(undone.revision)
        return undone
    }

    @Test fun selectedKnownEntriesMergeAdditionalCopiesOnceThenUiUndoRestoresOriginalSupply() {
        awaitCatalogGenerationUi()
        openDeck(knownDeck)
        val a = knownDeck.entries[0].id; val b = knownDeck.entries[1].id
        // Whole-deck shared supply2 covers A2 and B0: remaining1/3 are suggestions,
        // while the explicit additional quantities2/1 deliberately differ from those suggestions.
        dialogClick("mark-owned-select-${a.value}"); dialogClick("mark-owned-select-${b.value}")
        assertEquals("1", text(a, "quantity")); assertEquals("3", text(b, "quantity"))
        for ((entryId, quantity) in listOf(a to "2", b to "1")) {
            field(entryId, "quantity", quantity)
            dialogClick("mark-owned-finish-load-${entryId.value}")
            dialogClick("mark-owned-finish-NONFOIL-${entryId.value}")
        }
        assertReadOnlyBaseline()
        footerClick("mark-owned-preview"); enabled("mark-owned-confirm")
        val change = "mark-owned-preview-change-${originalLot.id.value}"
        dialogScroll(change)
        rule.onNodeWithTag(change).assertTextEquals("$cardName: inventory quantity 2 → 5")
        for (quantity in listOf(2, 1)) {
            val merge = SemanticsMatcher("actual generated Add merge row") {
                it.config.contains(SemanticsProperties.TestTag) && it.config[SemanticsProperties.TestTag].startsWith("mark-owned-preview-merge-")
            } and hasText("Merge $quantity additional copies of $cardName into the existing lot for $cardName.")
            scroll("mark-owned-list", merge)
            rule.onNode(merge).assertIsDisplayed()
        }
        assertReadOnlyBaseline()
        footerClick("mark-owned-confirm")
        await { !exists("mark-owned-list") }
        val saved = snapshot()
        assertEquals(before.revision.value + 1, saved.revision.value)
        assertEquals(before.lots.map { if (it.id == originalLot.id) it.copy(quantity = 5, revision = saved.revision) else it }, saved.lots.toList())
        assertEquals(before.lots.map { it.id }, saved.lots.map { it.id })
        assertEquals(zeroLot, saved.lots.single { it.id == zeroLot.id })
        assertEquals(ambiguousLot, saved.lots.single { it.id == ambiguousLot.id })
        assertEquals(knownDeck, storedDeck(knownDeck.id)); assertEquals(foreignDeck, storedDeck(foreignDeck.id))
        displayedRevision(saved.revision) // Actual callback/Collection refresh, not just the DB write.
        assertEquals(saved, reopenedSnapshot())
        // A real activity recreation rebuilds composition without recording the additions again.
        rule.activityRule.scenario.recreate()
        navigate("shell-nav-collection")
        displayedRevision(saved.revision)
        assertEquals(saved, snapshot()); assertEquals(knownDeck, storedDeck(knownDeck.id))
        val undone = undoThroughUi(saved)
        assertEquals(before.revision.value + 2, undone.revision.value)
        assertEquals(before.lots.map { if (it.id == originalLot.id) it.copy(revision = undone.revision) else it }, undone.lots.toList())
        assertEquals(undone, reopenedSnapshot())
        assertEquals(knownDeck, storedDeck(knownDeck.id)); assertEquals(foreignDeck, storedDeck(foreignDeck.id))
        assertSettingsAndCatalogUnchanged()
    }

    @Test fun unresolvedForeignInputSurvivesGlobalResumeAndAddsUnknownPhysicalCopyWithoutSourceConsent() {
        openDeck(foreignDeck)
        val entry = foreignDeck.entries.single()
        dialogClick("mark-owned-select-${entry.id.value}")
        assertEquals("", text(entry.id, "quantity")) // Genuine incomplete estimate, not zero.
        dialogClick("mark-owned-finish-UNKNOWN-${entry.id.value}")
        val notes = "Exact physical note $suffix"
        val location = "Exact physical shelf $suffix"
        field(entry.id, "quantity", "-1"); field(entry.id, "notes", notes); field(entry.id, "location", location)
        footerClick("mark-owned-preview")
        val invalid = rule.activity.getString(ShellR.string.shell_problem_invalid_input)
        rule.onNodeWithTag("mark-owned-list").performScrollToIndex(0)
        await { rule.onAllNodesWithText(invalid).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithTag("mark-owned-confirm").fetchSemanticsNodes().none { !it.config.contains(SemanticsProperties.Disabled) })
        assertReadOnlyBaseline()
        footerClick("mark-owned-close")
        await { !exists("mark-owned-list") }
        navigate("shell-nav-cards")
        footerClick("mark-owned-resume")
        assertEquals("-1", text(entry.id, "quantity"))
        assertEquals(notes, text(entry.id, "notes")); assertEquals(location, text(entry.id, "location"))
        assertReadOnlyBaseline()
        field(entry.id, "quantity", "1")
        footerClick("mark-owned-preview"); enabled("mark-owned-confirm")
        assertReadOnlyBaseline()
        footerClick("mark-owned-confirm"); await { !exists("mark-owned-list") }
        val saved = snapshot()
        assertEquals(before.revision.value + 1, saved.revision.value)
        val beforeIds = before.lots.map { it.id }.toSet()
        val added = saved.lots.filter { it.id !in beforeIds }.single()
        assertEquals(before.lots, SnapshotList.copyOf(saved.lots.filter { it.id in beforeIds }))
        assertEquals(entry.identity, added.identity)
        assertEquals(Knowledge.Unknown("Physical finish not recorded", SnapshotList.empty<Evidence>()), added.finish)
        assertEquals(CardCondition.UNKNOWN, added.condition); assertEquals(1, added.quantity)
        assertEquals(notes, added.notes); assertEquals(location, added.location)
        assertEquals(Knowledge.Absent("Acquisition time not supplied"), added.acquiredAt)
        assertEquals(saved.revision, added.revision)
        assertEquals(knownDeck, storedDeck(knownDeck.id)); assertEquals(foreignDeck, storedDeck(foreignDeck.id))
        displayedRevision(saved.revision)
        rule.onNodeWithTag("collection-inventory").performScrollToNode(hasTestTag("collection-lot-${added.id.value}"))
        rule.onNodeWithTag("collection-lot-${added.id.value}").assertIsDisplayed()
        assertEquals(saved, reopenedSnapshot())
        assertSettingsAndCatalogUnchanged() // No JA source installation, consent or profile-setting side effects.
        val undone = undoThroughUi(saved)
        assertEquals(before.lots, undone.lots)
        assertEquals(before.revision.value + 2, undone.revision.value)
        assertEquals(undone, reopenedSnapshot())
        assertEquals(foreignDeck, storedDeck(foreignDeck.id)); assertSettingsAndCatalogUnchanged()
    }
}
