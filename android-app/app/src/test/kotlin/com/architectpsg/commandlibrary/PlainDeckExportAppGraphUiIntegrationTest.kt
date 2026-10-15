package com.architectpsg.commandlibrary

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import java.util.concurrent.CopyOnWriteArrayList
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

/** Actual activity/registry/resolver/codec/controller/writer/Room, with synthetic OS destinations. */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class PlainDeckExportAppGraphUiIntegrationTest {
    private val rule = createAndroidComposeRule<MainActivity>()
    private val suffix = UUID.randomUUID().toString()
    private val authority = "synthetic.export.$suffix"
    private val cancel = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private lateinit var ports: ShellPorts
    private lateinit var source: DeckSnapshot
    private lateinit var beforeDecks: List<DeckSnapshot>
    private lateinit var beforeInventory: InventorySnapshot
    private lateinit var beforeSettings: SettingsSnapshot
    private lateinit var beforeCatalog: LocalCatalogStatus
    private lateinit var destinations: Map<String, Destination>
    private lateinit var provider: SelectedDocumentProvider
    private val library get() = checkNotNull(ports.localDecks)
    private val inventory get() = checkNotNull(ports.localInventory)

    private class Destination(val uri: Uri, val file: File, val sentinel: ByteArray, val partial: ByteArray, val failDuringOpen: Boolean, val privateMessage: String) {
        val opens = AtomicInteger()
        val modes = CopyOnWriteArrayList<String>()
    }
    /** Instance-owned provider; its A failure modifies the file DURING OPEN, before any PFD returns. */
    private class SelectedDocumentProvider(private val destinations: Map<Uri, Destination>) : ContentProvider() {
        val mutations = AtomicInteger()
        override fun onCreate() = true
        private fun destination(uri: Uri) = destinations[uri] ?: throw FileNotFoundException("Unknown synthetic destination")
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (mode != "wt") throw FileNotFoundException("Unexpected synthetic destination mode")
            val destination = destination(uri)
            destination.opens.incrementAndGet(); destination.modes.add(mode)
            if (destination.failDuringOpen) {
                destination.file.writeBytes(destination.partial) // Deliberate truncation/partial modification before open fails.
                throw FileNotFoundException(destination.privateMessage)
            }
            return ParcelFileDescriptor.open(destination.file, ParcelFileDescriptor.parseMode(mode))
        }
        override fun getType(uri: Uri): String { destination(uri); return "text/plain" }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? =
            throw UnsupportedOperationException("Export must not query destination metadata")
        override fun insert(uri: Uri, values: ContentValues?): Uri? { mutations.incrementAndGet(); throw UnsupportedOperationException() }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int { mutations.incrementAndGet(); throw UnsupportedOperationException() }
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int { mutations.incrementAndGet(); throw UnsupportedOperationException() }
    }
    private fun <T> ok(value: Outcome<T>): T {
        assertTrue(value.toString(), value is Outcome.Success)
        return (value as Outcome.Success).value
    }
    private fun decks(): List<DeckSnapshot> = runBlocking {
        val loaded = ArrayList<DeckSnapshot>(); val seen = HashSet<DeckId>(); val cursors = HashSet<String>()
        var cursor: String? = null
        do {
            val page = ok(library.list(PageRequest(PageRequest.MAX_LIMIT, cursor), cancel))
            for (summary in page.items) {
                assertTrue("Duplicate library identity", seen.add(summary.id))
                loaded.add(ok(library.load(summary.id, cancel)))
            }
            cursor = page.nextCursor
            if (cursor != null) assertTrue("Repeated library cursor", cursors.add(cursor))
        } while (cursor != null)
        loaded
    }
    private val seed = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val app = ApplicationProvider.getApplicationContext<CommandLibraryApplication>()
            destinations = listOf("revoked", "A", "B").associateWith { key ->
                val file = File(app.cacheDir, "export-$suffix-$key.txt")
                val sentinel = ByteArray(1_024) { 90 }
                file.writeBytes(sentinel)
                val uri = Uri.parse("content://$authority/document/private-$key-$suffix")
                Destination(uri, file, sentinel, "Synthetic partial $suffix\n".toByteArray(Charsets.UTF_8), key == "A", "PRIVATE_OPEN_FAILURE $suffix $uri")
            }
            provider = SelectedDocumentProvider(destinations.values.associateBy { it.uri })
            ContentProviderController.of(provider).create(authority)
            runBlocking {
                ports = ok(app.graph.ports())
                source = PlainDeckExportBoundaryFixture.source(DeckId(LocalDeckLibraryLimits.SOURCE, "export-app-$suffix"),
                    "Actual export Ω $suffix", checkNotNull(ports.utcClock).utcNow())
                ok(checkNotNull(ports.deckSnapshotValidation)(source, cancel))
                assertEquals(source, ok(library.create(source, EntityId("seed-export-app-$suffix"), cancel)))
                val unrelated = source.copy(id = DeckId(LocalDeckLibraryLimits.SOURCE, "unrelated-export-$suffix"), name = "Unrelated export $suffix",
                    entries = SnapshotList.empty(), categoryOrder = SnapshotList.empty())
                ok(library.create(unrelated, EntityId("seed-unrelated-export-$suffix"), cancel))
                val zero = OwnedLot(EntityId("export-zero-$suffix"), source.entries[1].identity,
                    Knowledge.Unknown("Original physical finish", SnapshotList.of(Evidence("Original finish evidence", null, source.createdAt))),
                    CardCondition.DAMAGED, "Original 棚", 0, "Original lot note Ω", Revision(0),
                    Knowledge.Unknown("Original acquisition unresolved", SnapshotList.of(Evidence("Original acquisition evidence", null, source.createdAt))))
                val positive = zero.copy(id = EntityId("export-positive-$suffix"), identity = source.entries[0].identity,
                    quantity = 2, condition = CardCondition.LIGHT_PLAYED, location = "Other shelf", notes = "Other exact lot note",
                    finish = Knowledge.Known(Finish.FOIL), acquiredAt = Knowledge.Absent("Original acquisition absent"))
                val initial = ok(inventory.snapshot(cancel))
                assertTrue(ok(inventory.apply(CollectionPatch(SnapshotList.of(CollectionEdit.Add(zero), CollectionEdit.Add(positive))), initial.revision,
                    EntityId("seed-export-inventory-$suffix"), cancel)) is InventoryApplyResult.Committed)
                beforeInventory = ok(inventory.snapshot(cancel)); beforeSettings = ok(ports.settings.read(cancel)); beforeCatalog = ok(ports.catalogStatus.status(cancel))
            }
            beforeDecks = decks()
            assertEquals(229, PlainDeckExportBoundaryFixture.expectedBytes.size)
            assertEquals(81, PlainDeckExportBoundaryFixture.expectedLosses.size)
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
            if (!exists(list)) false else try { rule.onNodeWithTag(list).performScrollToNode(matcher); true }
            catch (_: AssertionError) { false }
        }
    }
    private fun exportText(tag: String, text: String) {
        scroll("plain-export-list", hasTestTag(tag))
        rule.onNodeWithTag(tag).assertTextEquals(text).assertIsDisplayed()
    }
    private fun assertReviewMetadata() {
        exportText("plain-export-source", source.name)
        exportText("plain-export-revision", rule.activity.getString(ShellR.string.plain_export_revision, source.revision.value))
        exportText("plain-export-format", rule.activity.getString(ShellR.string.plain_export_format))
        exportText("plain-export-summary", rule.activity.getString(ShellR.string.plain_export_summary, 8, 18L))
        exportText("plain-export-loss-summary", rule.activity.getString(ShellR.string.plain_export_loss_summary, 81))
    }
    private fun lossLabel(field: PlainDeckListLossField): Int = when (field) {
        PlainDeckListLossField.DECK_ID -> ShellR.string.plain_export_loss_deck_id
        PlainDeckListLossField.DECK_NAME -> ShellR.string.plain_export_loss_deck_name
        PlainDeckListLossField.FORMAT -> ShellR.string.plain_export_loss_format
        PlainDeckListLossField.DESCRIPTION -> ShellR.string.plain_export_loss_description
        PlainDeckListLossField.FOLDER -> ShellR.string.plain_export_loss_folder
        PlainDeckListLossField.TAGS -> ShellR.string.plain_export_loss_tags
        PlainDeckListLossField.CREATED_AT -> ShellR.string.plain_export_loss_created_at
        PlainDeckListLossField.MODIFIED_AT -> ShellR.string.plain_export_loss_modified_at
        PlainDeckListLossField.REVISION -> ShellR.string.plain_export_loss_revision
        PlainDeckListLossField.BUDGET -> ShellR.string.plain_export_loss_budget
        PlainDeckListLossField.PROFILE_ID -> ShellR.string.plain_export_loss_profile_id
        PlainDeckListLossField.CATALOG_REFERENCES -> ShellR.string.plain_export_loss_catalog_references
        PlainDeckListLossField.PACKAGE_INSTANCES -> ShellR.string.plain_export_loss_package_instances
        PlainDeckListLossField.PRIMER -> ShellR.string.plain_export_loss_primer
        PlainDeckListLossField.CATEGORY_ORDER -> ShellR.string.plain_export_loss_category_order
        PlainDeckListLossField.ENTRY_ORDER -> ShellR.string.plain_export_loss_entry_order
        PlainDeckListLossField.ENTRY_ID -> ShellR.string.plain_export_loss_entry_id
        PlainDeckListLossField.ORACLE_ID -> ShellR.string.plain_export_loss_oracle_id
        PlainDeckListLossField.PRINTING_ID -> ShellR.string.plain_export_loss_printing_id
        PlainDeckListLossField.RAW_IDENTITY -> ShellR.string.plain_export_loss_raw_identity
        PlainDeckListLossField.LANGUAGE -> ShellR.string.plain_export_loss_language
        PlainDeckListLossField.FINISH -> ShellR.string.plain_export_loss_finish
        PlainDeckListLossField.NOTES -> ShellR.string.plain_export_loss_notes
        PlainDeckListLossField.LABELS -> ShellR.string.plain_export_loss_labels
        PlainDeckListLossField.ROLE_OVERRIDES -> ShellR.string.plain_export_loss_role_overrides
    }
    private fun assertWholeReview() {
        assertReviewMetadata()
        var previous: Int? = null
        for (loss in PlainDeckExportBoundaryFixture.expectedLosses) {
            val entryId = loss.entryId
            val key = if (entryId == null) "plain-export-loss-deck-${loss.field.name}" else "plain-export-loss-entry-${entryId.value}-${loss.field.name}"
            rule.onNodeWithTag("plain-export-list").performScrollToKey(key)
            val index = rule.onNodeWithTag("plain-export-list").fetchSemanticsNode().config[SemanticsProperties.IndexForKey](key)
            previous?.let { assertEquals("All literal loss rows must stay consecutive and in report order", it + 1, index) }
            previous = index
            rule.onNodeWithTag(key).assertTextEquals(rule.activity.getString(lossLabel(loss.field))).assertIsDisplayed()
            loss.entryId?.let { id ->
                val entry = source.entries.single { it.id == id }
                rule.onNodeWithTag("$key-name").assertTextEquals((entry.identity.originalName as Knowledge.Known).value).assertIsDisplayed()
            }
        }
    }
    private fun prepareThroughUi() {
        navigate("shell-nav-decks")
        val row = "deck-open-${source.id.raw}"
        scroll("deck-library-list", hasTestTag(row)); click(row)
        scroll("deck-library-overview", hasText(source.name))
        rule.onNode(hasText(source.name) and hasAnyAncestor(hasTestTag("deck-library-overview"))).assertIsDisplayed()
        scroll("deck-library-overview", hasTestTag("deck-plain-export")); click("deck-plain-export")
        enabled("plain-export-choose"); assertWholeReview()
        assertNull(shadowOf(rule.activity).peekNextStartedActivityForResult())
        assertUnchanged(); assertNoOpens()
    }
    private fun choose(): IntentForResult {
        click("plain-export-choose")
        await { shadowOf(rule.activity).peekNextStartedActivityForResult() != null }
        return checkNotNull(shadowOf(rule.activity).nextStartedActivityForResult).also {
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, it.intent.action)
            assertEquals("text/plain", it.intent.type)
            assertEquals(rule.activity.getString(ShellR.string.plain_export_filename), it.intent.getStringExtra(Intent.EXTRA_TITLE))
        }
    }
    private fun deliver(launch: IntentForResult, key: String) {
        rule.runOnIdle { shadowOf(rule.activity).receiveResult(launch.intent, Activity.RESULT_OK,
            Intent().setData(destinations.getValue(key).uri).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) }
    }
    private fun assertUnchanged() = runBlocking {
        assertEquals(beforeDecks, decks())
        assertEquals(beforeInventory, ok(inventory.snapshot(cancel)))
        assertEquals(beforeSettings, ok(ports.settings.read(cancel)))
        assertEquals(beforeCatalog, ok(ports.catalogStatus.status(cancel)))
        assertEquals(0, provider.mutations.get())
    }
    private fun assertNoOpens() {
        for (destination in destinations.values) {
            assertEquals(0, destination.opens.get()); assertTrue(destination.modes.isEmpty())
            assertArrayEquals(destination.sentinel, destination.file.readBytes())
        }
    }
    private fun reopenedSource(): DeckSnapshot = runBlocking {
        val app = rule.activity.application as CommandLibraryApplication
        ok(StorageDatabase.open(app, AppGraph.DATABASE_FILE)).use { db ->
            val reader = SqlLocalDeckLibrary(db, DeckPayloadCodec(), object : Clock {
                override fun utcNow(): UtcTimestamp = error("Room read must not consume a clock")
                override fun monotonicNow(): Duration = error("Room read must not consume a clock")
            }, object : IdSource { override fun nextId(): String = error("Room read must not generate identities") })
            ok(reader.load(source.id, cancel))
        }
    }
    private fun assertWritten() {
        exportText("plain-export-written", rule.activity.getString(ShellR.string.plain_export_written))
        enabled("plain-export-choose")
        val target = destinations.getValue("B")
        assertEquals(1, target.opens.get()); assertEquals(listOf("wt"), target.modes.toList())
        assertArrayEquals(PlainDeckExportBoundaryFixture.expectedBytes, target.file.readBytes())
        assertEquals(229L, target.file.length()) // Sentinel was longer: no stale tail survives wt.
        assertUnchanged(); assertEquals(source, reopenedSource())
    }
    private fun assertPartial(problemResource: Int) {
        exportText("plain-export-possibly-modified", rule.activity.getString(ShellR.string.plain_export_possibly_modified))
        exportText("plain-export-problem", rule.activity.getString(problemResource))
        val failed = destinations.getValue("A")
        assertEquals(1, failed.opens.get()); assertEquals(listOf("wt"), failed.modes.toList())
        assertArrayEquals(failed.partial, failed.file.readBytes())
        for (privateText in listOf(failed.uri.toString(), failed.privateMessage, failed.file.absolutePath, "PRIVATE_OPEN_FAILURE"))
            rule.onAllNodes(hasText(privateText, substring = true), useUnmergedTree = true).assertCountEquals(0)
        assertNull(shadowOf(rule.activity).peekNextStartedActivityForResult())
        assertUnchanged()
    }
    private fun hideCardsResume() {
        click("plain-export-hide"); await { !exists("plain-export-list") && exists("plain-export-resume") }
        navigate("shell-nav-cards"); click("plain-export-resume")
        await { exists("plain-export-list") }
    }

    @Test fun reviewedAllBoardExportRetainsCancellationAndRevocationThenWritesOnceAcrossRecreation() {
        prepareThroughUi()
        rule.activityRule.scenario.recreate()
        enabled("plain-export-choose"); assertWholeReview(); assertUnchanged(); assertNoOpens()
        assertNull(shadowOf(rule.activity).peekNextStartedActivityForResult())
        val cancelled = choose()
        rule.activityRule.scenario.recreate()
        rule.runOnIdle { shadowOf(rule.activity).callOnActivityResult(cancelled.requestCode, Activity.RESULT_CANCELED, null) }
        enabled("plain-export-choose"); assertReviewMetadata(); assertUnchanged(); assertNoOpens()
        assertNull(shadowOf(rule.activity).peekNextStartedActivityForResult())
        val revoked = choose()
        hideCardsResume()
        rule.onNodeWithTag("plain-export-choose").assertIsNotEnabled()
        deliver(revoked, "revoked")
        enabled("plain-export-choose"); assertReviewMetadata(); assertUnchanged(); assertNoOpens()
        val written = choose()
        deliver(written, "B"); assertWritten()
        assertEquals(0, destinations.getValue("revoked").opens.get()); assertEquals(0, destinations.getValue("A").opens.get())
        rule.activityRule.scenario.recreate()
        assertWritten(); assertReviewMetadata()
        rule.runOnIdle { shadowOf(rule.activity).callOnActivityResult(written.requestCode, Activity.RESULT_OK,
            Intent().setData(destinations.getValue("B").uri).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) }
        rule.waitForIdle(); assertWritten()
        assertNull(shadowOf(rule.activity).peekNextStartedActivityForResult())
        scroll("plain-export-list", hasTestTag("plain-export-end"))
        click("plain-export-end"); click("plain-export-end-confirm")
        await { !exists("plain-export-list") && !exists("plain-export-resume") }
        assertArrayEquals(PlainDeckExportBoundaryFixture.expectedBytes, destinations.getValue("B").file.readBytes())
        assertEquals(1, destinations.getValue("B").opens.get()); assertUnchanged(); assertEquals(source, reopenedSource())
    }

    @Test fun partialModificationDuringProviderOpenRequiresDifferentDestinationAndPreservesAllStores() {
        prepareThroughUi()
        deliver(choose(), "A")
        assertPartial(ShellR.string.shell_problem_persistence); assertWholeReview()
        assertEquals(0, destinations.getValue("B").opens.get())
        hideCardsResume()
        assertPartial(ShellR.string.shell_problem_persistence); assertReviewMetadata()
        deliver(choose(), "A") // Same exact URI is refused before the provider can reopen/truncate it.
        assertPartial(ShellR.string.shell_problem_invalid_input)
        assertEquals(0, destinations.getValue("B").opens.get())
        deliver(choose(), "B"); assertWritten()
        val failed = destinations.getValue("A")
        assertArrayEquals(failed.partial, failed.file.readBytes()); assertEquals(1, failed.opens.get())
        assertEquals(0, destinations.getValue("revoked").opens.get())
        rule.activityRule.scenario.recreate()
        assertWritten(); assertReviewMetadata()
        assertArrayEquals(failed.partial, failed.file.readBytes()); assertEquals(1, failed.opens.get())
        assertNull(shadowOf(rule.activity).peekNextStartedActivityForResult())
        assertUnchanged(); assertEquals(source, reopenedSource())
    }
}
