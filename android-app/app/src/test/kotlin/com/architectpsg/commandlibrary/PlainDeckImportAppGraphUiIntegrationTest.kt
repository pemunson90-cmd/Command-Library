package com.architectpsg.commandlibrary

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.data.storage.decks.codec.DeckPayloadCodec
import com.architectpsg.commandlibrary.di.AppGraph
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckLibrary
import com.architectpsg.commandlibrary.feature.shell.ShellPorts
import com.architectpsg.commandlibrary.feature.shell.R as ShellR
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ContentProviderController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivity.IntentForResult
import kotlin.time.Duration

/** Actual activity registry/resolver/codec/controller/Room; synthetic OS document boundary only. */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class PlainDeckImportAppGraphUiIntegrationTest {
    private val rule = createAndroidComposeRule<MainActivity>()
    private val suffix = UUID.randomUUID().toString()
    private val authority = "synthetic.plain.$suffix"
    private val cancel = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private lateinit var ports: ShellPorts
    private lateinit var beforeDecks: List<DeckSnapshot>
    private lateinit var beforeInventory: InventorySnapshot
    private lateinit var beforeSettings: SettingsSnapshot
    private lateinit var beforeCatalog: LocalCatalogStatus
    private lateinit var documents: Map<String, Document>
    private val library get() = checkNotNull(ports.localDecks)
    private val inventory get() = checkNotNull(ports.localInventory)
    private val goodRows = listOf("Command Library Deck List 1", "Deck", "2 Café // (SET)  ", "1 Café // (SET)  ", "", "Sideboard", "3 日本語 Ω")
    private val invalidRows = listOf("Command Library Deck List 1", "Deck", "2 Valid Ω", "0 Invalid quantity", "Command Library Deck List nope", "", "Sideboard", "1 Foreign 日本語")
    private val correctedRows = listOf("Command Library Deck List 1", "Deck", "2 Valid Ω", "1 Corrected Café  ", "", "Sideboard", "1 Foreign 日本語")

    private class Document(val uri: Uri, val file: File, val displayName: String) {
        val opens = AtomicInteger()
    }
    /** One instance and unique authority per test. No static payload/registry or fake stream. */
    private class SyntheticDocumentsProvider(private val documents: Map<Uri, Document>) : ContentProvider() {
        override fun onCreate() = true
        private fun document(uri: Uri) = documents[uri] ?: throw FileNotFoundException("Unknown synthetic document")
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
            val document = document(uri)
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                addRow(arrayOf<Any?>(document.displayName, document.file.length()))
            }
        }
        override fun getType(uri: Uri): String { document(uri); return "text/plain" }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (mode != "r") throw FileNotFoundException("Read-only synthetic document")
            val document = document(uri)
            val descriptor = ParcelFileDescriptor.open(document.file, ParcelFileDescriptor.MODE_READ_ONLY)
            document.opens.incrementAndGet()
            return descriptor
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
    }
    private fun <T> ok(value: Outcome<T>): T {
        assertTrue(value.toString(), value is Outcome.Success)
        return (value as Outcome.Success).value
    }
    private fun decks(): List<DeckSnapshot> = runBlocking {
        val result = ArrayList<DeckSnapshot>()
        val seen = HashSet<DeckId>()
        val cursors = HashSet<String>()
        var cursor: String? = null
        do {
            val page = ok(library.list(PageRequest(PageRequest.MAX_LIMIT, cursor), cancel))
            for (summary in page.items) {
                assertTrue("Duplicate library row", seen.add(summary.id))
                result.add(ok(library.load(summary.id, cancel)))
            }
            cursor = page.nextCursor
            if (cursor != null) assertTrue("Repeated library cursor", cursors.add(cursor))
        } while (cursor != null)
        result
    }
    private fun snapshot() = runBlocking { ok(inventory.snapshot(cancel)) }
    // Every file, provider and durable seed exists before actual MainActivity/retained controllers.
    private val seed = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val app = ApplicationProvider.getApplicationContext<CommandLibraryApplication>()
            documents = listOf("good" to goodRows, "invalid" to invalidRows, "corrected" to correctedRows).associate { (key, rows) ->
                val file = File(app.cacheDir, "plain-import-$suffix-$key.txt")
                file.writeBytes((rows.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8))
                key to Document(Uri.parse("content://$authority/document/$key"), file, "Synthetic $key $suffix.txt")
            }
            ContentProviderController.of(SyntheticDocumentsProvider(documents.values.associateBy { it.uri })).create(authority)
            runBlocking {
                ports = ok(app.graph.ports())
                val time = checkNotNull(ports.utcClock).utcNow()
                val identity = CardIdentity(Knowledge.Unknown("Preserved before Oracle", SnapshotList.of(Evidence("Before evidence", null, time))),
                    Knowledge.Absent("Preserved before printing"), ProviderIdentity(ProviderId("synthetic"), "Original kind", "original-$suffix"),
                    Knowledge.Known("Preserved before card $suffix"), LanguageTag("ja"))
                val label = EntryLabel(EntityId("before-label-$suffix"), "Preserved category")
                val entry = DeckEntry(EntityId("before-entry-$suffix"), identity, 4, BoardRole.MAYBEBOARD,
                    Knowledge.Unknown("Preserved before finish", SnapshotList.empty()), "Before entry notes", SnapshotList.of(label), SnapshotList.empty())
                val existing = DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE, "before-plain-$suffix"), "Before plain $suffix",
                    Knowledge.Absent("Before format absent"), "Before description", Knowledge.Known("Before folder"), SnapshotList.of("Before tag"), time, time,
                    Revision(0), SnapshotList.of(entry), Knowledge.Absent("Before budget absent"), null, SnapshotList.empty(), SnapshotList.empty(),
                    "Before primer", SnapshotList.of(label))
                ok(library.create(existing, EntityId("seed-before-deck-$suffix"), cancel))
                val lot = OwnedLot(EntityId("before-plain-lot-$suffix"), identity, Knowledge.Unknown("Physical before finish", SnapshotList.empty()),
                    CardCondition.DAMAGED, "Before shelf", 0, "Before lot notes", Revision(0), Knowledge.Absent("Before acquisition absent"))
                val inventoryBeforeSeed = ok(inventory.snapshot(cancel))
                assertTrue(ok(inventory.apply(CollectionPatch(SnapshotList.of(CollectionEdit.Add(lot))), inventoryBeforeSeed.revision,
                    EntityId("seed-before-inventory-$suffix"), cancel)) is InventoryApplyResult.Committed)
                beforeInventory = ok(inventory.snapshot(cancel))
                beforeSettings = ok(ports.settings.read(cancel))
                beforeCatalog = ok(ports.catalogStatus.status(cancel))
            }
            beforeDecks = decks()
            base.evaluate()
        }
    } }
    @get:Rule val chain: RuleChain = RuleChain.outerRule(seed).around(rule)
    @Before fun hostBudget() = budget(300)
    @After fun resetBudget() = budget(60)
    private fun budget(seconds: Long) {
        Class.forName("androidx.test.espresso.IdlingPolicies").getMethod("setMasterPolicyTimeout", Long::class.javaPrimitiveType, TimeUnit::class.java)
            .invoke(null, seconds, TimeUnit.SECONDS)
    }
    private fun await(check: () -> Boolean) = rule.waitUntil(20_000) { rule.waitForIdle(); check() }
    private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun enabled(tag: String) = await { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
    private fun click(tag: String) { enabled(tag); rule.onNodeWithTag(tag).performClick() }
    private fun navigate(tag: String) {
        await { exists(tag) || exists("shell-nav-more") }
        if (!exists(tag)) click("shell-nav-more")
        click(tag)
    }
    private fun scroll(list: String, matcher: SemanticsMatcher) {
        await {
            if (!exists(list)) false else try {
                rule.onNodeWithTag(list).performScrollToNode(matcher); true
            } catch (_: AssertionError) { false }
        }
    }
    private fun dialogScroll(tag: String) = scroll("plain-import-list", hasTestTag(tag))
    private fun setName(value: String) {
        dialogScroll("plain-import-name"); enabled("plain-import-name")
        rule.onNodeWithTag("plain-import-name").performTextReplacement(value)
    }
    private fun assertName(value: String) {
        dialogScroll("plain-import-name")
        assertEquals(value, rule.onNodeWithTag("plain-import-name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }
    private fun assertRows(rows: List<String>) {
        rows.forEachIndexed { index, raw ->
            val tag = "plain-import-row-${index + 1}"
            dialogScroll(tag)
            rule.onNodeWithTag(tag).assertTextEquals(if (raw.isEmpty()) rule.activity.getString(ShellR.string.plain_import_blank_line) else raw).assertIsDisplayed()
        }
        rule.onAllNodesWithTag("plain-import-row-${rows.size + 1}").assertCountEquals(0)
    }
    private fun assertDisclosures() {
        dialogScroll("plain-import-unresolved")
        rule.onNodeWithTag("plain-import-unresolved").assertTextEquals(rule.activity.getString(ShellR.string.plain_import_unresolved)).assertIsDisplayed()
        val newDeck = rule.activity.getString(ShellR.string.plain_import_new_deck)
        scroll("plain-import-list", hasText(newDeck)); rule.onNodeWithText(newDeck).assertIsDisplayed()
    }
    private fun assertCardRow(row: Int, quantity: Int, boardResource: Int) {
        dialogScroll("plain-import-row-$row")
        val text = rule.activity.getString(ShellR.string.plain_import_card_row, quantity, rule.activity.getString(boardResource))
        scroll("plain-import-list", hasText(text)); rule.onNodeWithText(text).assertIsDisplayed()
    }
    private fun assertIssue(resource: Int) {
        val text = rule.activity.getString(resource)
        scroll("plain-import-list", hasText(text)); rule.onNodeWithText(text).assertIsDisplayed()
    }
    private fun openExistingDetails(): DeckSnapshot {
        navigate("shell-nav-decks")
        val existing = beforeDecks.single { it.name == "Before plain $suffix" }
        val row = "deck-open-${existing.id.raw}"
        scroll("deck-library-list", hasTestTag(row)); click(row)
        scroll("deck-library-overview", hasTestTag("deck-edit-details")); click("deck-edit-details")
        enabled("deck-editor-name")
        assertEquals(existing.name, rule.onNodeWithTag("deck-editor-name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        return existing
    }
    private fun closeEditor() = rule.onNodeWithText(rule.activity.getString(ShellR.string.deck_draft_close)).performClick()
    private fun continueRetainedExit() {
        val text = rule.activity.getString(ShellR.string.deck_draft_continue)
        await { rule.onAllNodesWithText(text).fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
        rule.onNodeWithText(text).performClick()
        await { rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }
    private fun returnToLibrary() {
        if (exists("deck-library-overview")) {
            rule.onNodeWithTag("deck-library-overview").performScrollToIndex(0)
            val back = rule.activity.getString(ShellR.string.decks_back)
            if (rule.onAllNodesWithText(back).fetchSemanticsNodes().isNotEmpty()) {
                rule.onNodeWithText(back).performClick()
                val continued = rule.activity.getString(ShellR.string.deck_draft_continue)
                await { exists("deck-library-list") || rule.onAllNodesWithText(continued).fetchSemanticsNodes().isNotEmpty() }
                if (!exists("deck-library-list")) continueRetainedExit()
            }
        }
        await { exists("deck-library-list") }
    }
    private fun openAndCloseCleanEditor() {
        val existing = openExistingDetails()
        closeEditor(); await { !exists("deck-editor-name") }
        assertReadOnlyBaseline()
        assertNull(runBlocking { ok(checkNotNull(ports.localDeckDrafts).load(existing.id, cancel)).draft })
        returnToLibrary()
    }
    private fun hiddenInvalidEditorBlocksImportUntilExplicitDiscard() {
        val existing = openExistingDetails()
        rule.onNodeWithTag("deck-editor-name").performTextReplacement("")
        closeEditor()
        val continued = rule.activity.getString(ShellR.string.deck_draft_continue)
        await {
            (!exists("deck-editor-name") && exists("deck-draft-resume")) ||
                rule.onAllNodesWithText(continued).fetchSemanticsNodes().isNotEmpty()
        }
        // A durable raw write may allow Close directly; use Continue only if the real
        // exit dialog requires a choice. Neither path authorizes the invalid deck name.
        if (!exists("deck-draft-resume")) continueRetainedExit()
        await { !exists("deck-editor-name") && exists("deck-draft-resume") }
        val drafts = checkNotNull(ports.localDeckDrafts)
        val retained = runBlocking { ok(drafts.load(existing.id, cancel)) }
        assertEquals("", (retained.draft?.body as? DeckDraftBody.Details)?.name)
        assertReadOnlyBaseline()
        returnToLibrary()
        scroll("deck-library-list", hasTestTag("deck-plain-import"))
        rule.onNodeWithTag("deck-plain-import").assertIsNotEnabled()
        assertNull(shadowOf(rule.activity).peekNextStartedActivityForResult())
        assertEquals(retained, runBlocking { ok(drafts.load(existing.id, cancel)) })
        assertReadOnlyBaseline()
        click("deck-draft-resume"); enabled("deck-editor-name")
        assertEquals("", rule.onNodeWithTag("deck-editor-name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        enabled("deck-draft-discard-input")
        rule.onNodeWithTag("deck-draft-discard-input").performScrollTo().performClick()
        await {
            runBlocking { ok(drafts.load(existing.id, cancel)).draft == null } &&
                rule.onAllNodesWithTag("deck-editor-name").fetchSemanticsNodes().any {
                    it.config[SemanticsProperties.EditableText].text == existing.name && !it.config.contains(SemanticsProperties.Disabled)
                }
        }
        closeEditor(); await { !exists("deck-editor-name") }
        returnToLibrary(); assertReadOnlyBaseline()
    }
    private fun launchedPicker(): IntentForResult {
        await { shadowOf(rule.activity).peekNextStartedActivityForResult() != null }
        return checkNotNull(shadowOf(rule.activity).nextStartedActivityForResult).also {
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, it.intent.action)
            assertTrue(it.intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.contains("text/plain") == true)
        }
    }
    private fun deliver(launch: IntentForResult, document: Document) {
        rule.runOnIdle {
            shadowOf(rule.activity).receiveResult(launch.intent, Activity.RESULT_OK,
                Intent().setData(document.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }
    private fun chooseReplacement() { dialogScroll("plain-import-choose"); click("plain-import-choose") }
    private fun initialFile(key: String) {
        navigate("shell-nav-decks")
        scroll("deck-library-list", hasTestTag("deck-plain-import")); click("deck-plain-import")
        deliver(launchedPicker(), documents.getValue(key))
        dialogScroll("plain-import-row-1") // The codec publishes the complete input, never a partial row prefix.
    }
    private fun assertOtherStoresUnchanged() = runBlocking {
        assertEquals(beforeInventory, ok(inventory.snapshot(cancel)))
        assertEquals(beforeSettings, ok(ports.settings.read(cancel)))
        assertEquals(beforeCatalog, ok(ports.catalogStatus.status(cancel)))
    }
    private fun assertReadOnlyBaseline() { assertEquals(beforeDecks, decks()); assertOtherStoresUnchanged() }
    private fun preview(entries: Int, copies: Long) {
        click("plain-import-preview"); enabled("plain-import-confirm")
        dialogScroll("plain-import-preview-summary")
        rule.onNodeWithTag("plain-import-preview-summary").assertTextEquals(
            rule.activity.getString(ShellR.string.plain_import_preview_summary, entries, copies)).assertIsDisplayed()
    }
    private fun reopened(id: DeckId): DeckSnapshot = runBlocking {
        val app = rule.activity.application as CommandLibraryApplication
        ok(StorageDatabase.open(app, AppGraph.DATABASE_FILE)).use { db ->
            val reader = SqlLocalDeckLibrary(db, DeckPayloadCodec(), object : Clock {
                override fun utcNow(): UtcTimestamp = error("Reopen read must not consume time")
                override fun monotonicNow(): Duration = error("Reopen read must not consume time")
            }, object : IdSource { override fun nextId(): String = error("Reopen read must not generate identity") })
            ok(reader.load(id, cancel))
        }
    }
    private data class ExpectedEntry(val quantity: Int, val name: String, val board: BoardRole)
    private fun assertCreated(name: String, expected: List<ExpectedEntry>): DeckSnapshot {
        await { !exists("plain-import-list") && exists("plain-import-saved") }
        val all = decks()
        val previousIds = beforeDecks.map { it.id }.toSet()
        assertEquals(beforeDecks, all.filter { it.id in previousIds })
        val saved = all.filter { it.id !in previousIds }.single()
        assertEquals(name, saved.name); assertEquals(LocalDeckLibraryLimits.SOURCE, saved.id.source)
        assertTrue(saved.id.raw.isNotBlank()); assertEquals(Revision(0), saved.revision)
        assertEquals(saved.createdAt, saved.modifiedAt)
        assertEquals(Knowledge.Absent("No format selected"), saved.format)
        assertEquals("", saved.description); assertEquals("", saved.primer)
        assertEquals(Knowledge.Absent("No folder selected"), saved.folder)
        assertEquals(Knowledge.Absent("No budget set"), saved.budget)
        assertNull(saved.profileId); assertTrue(saved.tags.isEmpty()); assertTrue(saved.catalog.isEmpty())
        assertTrue(saved.packageInstances.isEmpty()); assertEquals(SnapshotList.empty<EntryLabel>(), saved.categoryOrder)
        assertEquals(expected.size, saved.entries.size)
        assertEquals(expected.size, saved.entries.map { it.id }.toSet().size)
        val existingEntryIds = beforeDecks.flatMap { it.entries }.map { it.id }.toSet()
        assertTrue(saved.entries.none { it.id.value.isBlank() || it.id.value == saved.id.raw || it.id in existingEntryIds })
        expected.zip(saved.entries).forEach { (row, entry) ->
            assertEquals(row.quantity, entry.quantity); assertEquals(row.board, entry.board)
            assertEquals(CardIdentity(Knowledge.Unknown("Plain list does not identify an Oracle card", SnapshotList.empty()),
                Knowledge.Unknown("Plain list does not identify a printing", SnapshotList.empty()), null, Knowledge.Known(row.name), LanguageTag("und")), entry.identity)
            assertEquals(Knowledge.Unknown("Physical finish not supplied by plain list", SnapshotList.empty<Evidence>()), entry.finish)
            assertEquals("", entry.notes); assertTrue(entry.labels.isEmpty()); assertTrue(entry.roleOverrides.isEmpty())
        }
        assertOtherStoresUnchanged(); assertEquals(saved, reopened(saved.id))
        return saved
    }
    private fun assertLibraryShows(saved: DeckSnapshot) {
        navigate("shell-nav-decks")
        val tag = "deck-open-${saved.id.raw}"
        scroll("deck-library-list", hasTestTag(tag))
        rule.onNodeWithTag(tag).assert(hasText(saved.name)).assertIsDisplayed()
    }

    @Test fun filePreviewPreservesPhysicalRowsThenCreatesOneUnresolvedDeckAcrossActivityRecreation() {
        // Actual invalid retained input blocks import; explicit discard restores admission.
        hiddenInvalidEditorBlocksImportUntilExplicitDiscard()
        // An actual untouched editor leaves a clean hidden owner; it must not block import.
        openAndCloseCleanEditor()
        initialFile("good")
        val name = "Imported Café Ω $suffix  "
        setName(name); assertRows(goodRows); assertDisclosures()
        assertCardRow(3, 2, ShellR.string.decks_board_main)
        assertCardRow(4, 1, ShellR.string.decks_board_main)
        assertCardRow(7, 3, ShellR.string.decks_board_side)
        assertReadOnlyBaseline()
        preview(3, 6L)
        assertReadOnlyBaseline(); assertEquals(1, documents.getValue("good").opens.get())
        rule.activityRule.scenario.recreate()
        assertName(name); assertRows(goodRows); enabled("plain-import-confirm")
        assertReadOnlyBaseline(); assertEquals(1, documents.getValue("good").opens.get())
        click("plain-import-confirm")
        val saved = assertCreated(name, listOf(ExpectedEntry(2, "Café // (SET)  ", BoardRole.MAIN),
            ExpectedEntry(1, "Café // (SET)  ", BoardRole.MAIN), ExpectedEntry(3, "日本語 Ω", BoardRole.SIDEBOARD)))
        assertLibraryShows(saved) // Actual callback/library refresh, not only durable create.
        val committed = decks()
        rule.activityRule.scenario.recreate()
        assertLibraryShows(saved); assertEquals(committed, decks())
        assertEquals(1, documents.getValue("good").opens.get()); assertOtherStoresUnchanged()
        click("plain-import-dismiss")
        click("deck-open-${saved.id.raw}")
        await { exists("deck-library-overview") }
        rule.onNodeWithTag("deck-library-overview").performScrollToIndex(0)
        rule.onNode(hasText(saved.name) and hasAnyAncestor(hasTestTag("deck-library-overview"))).assertIsDisplayed()
        assertEquals(saved, reopened(saved.id)); assertEquals(committed, decks())
    }

    @Test fun invalidFileNeverImportsSubsetAndRawResumeReplacementAndPickerCancellationPreserveInput() {
        initialFile("invalid")
        val name = "Retained 日本語 Café $suffix  "
        setName(name); assertRows(invalidRows); assertDisclosures()
        assertIssue(ShellR.string.plain_import_invalid_quantity)
        assertIssue(ShellR.string.plain_import_invalid_marker)
        dialogScroll("plain-import-invalid")
        rule.onNodeWithTag("plain-import-invalid").assertTextEquals(rule.activity.getString(ShellR.string.plain_import_invalid_rows)).assertIsDisplayed()
        rule.onNodeWithTag("plain-import-preview").assertIsNotEnabled()
        assertTrue(rule.onAllNodesWithTag("plain-import-confirm").fetchSemanticsNodes().none { !it.config.contains(SemanticsProperties.Disabled) })
        assertReadOnlyBaseline()
        click("plain-import-close"); await { !exists("plain-import-list") }
        navigate("shell-nav-cards"); click("plain-import-resume")
        assertName(name); assertRows(invalidRows); assertReadOnlyBaseline()
        chooseReplacement(); click("plain-import-replace-cancel")
        assertName(name); assertRows(invalidRows); assertReadOnlyBaseline()
        assertNull(shadowOf(rule.activity).peekNextStartedActivityForResult())
        chooseReplacement(); click("plain-import-replace-confirm")
        val pending = launchedPicker()
        rule.activityRule.scenario.recreate()
        // Capture from the actual launch, deliver to the new actual registry's restored request.
        rule.runOnIdle { shadowOf(rule.activity).callOnActivityResult(pending.requestCode, Activity.RESULT_CANCELED, null) }
        assertName(name); assertRows(invalidRows); assertReadOnlyBaseline()
        assertEquals(1, documents.getValue("invalid").opens.get())
        assertEquals(0, documents.getValue("corrected").opens.get())
        chooseReplacement(); click("plain-import-replace-confirm")
        deliver(launchedPicker(), documents.getValue("corrected"))
        enabled("plain-import-preview"); assertName(name); assertRows(correctedRows); assertDisclosures()
        assertCardRow(3, 2, ShellR.string.decks_board_main)
        assertCardRow(4, 1, ShellR.string.decks_board_main)
        assertCardRow(7, 1, ShellR.string.decks_board_side)
        assertReadOnlyBaseline()
        preview(3, 4L)
        assertReadOnlyBaseline(); click("plain-import-confirm")
        val saved = assertCreated(name, listOf(ExpectedEntry(2, "Valid Ω", BoardRole.MAIN),
            ExpectedEntry(1, "Corrected Café  ", BoardRole.MAIN), ExpectedEntry(1, "Foreign 日本語", BoardRole.SIDEBOARD)))
        assertLibraryShows(saved)
        assertEquals(1, documents.getValue("invalid").opens.get()); assertEquals(1, documents.getValue("corrected").opens.get())
        assertEquals(0, documents.getValue("good").opens.get()); assertOtherStoresUnchanged()
        assertEquals(saved, reopened(saved.id))
    }
}
