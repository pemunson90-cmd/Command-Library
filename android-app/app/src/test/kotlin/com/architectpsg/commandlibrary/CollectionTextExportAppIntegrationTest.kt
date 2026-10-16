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
import com.architectpsg.commandlibrary.data.storage.collection.codec.LocalInventoryPatchCodec
import com.architectpsg.commandlibrary.data.storage.collection.codec.OwnedLotPayloadCodec
import com.architectpsg.commandlibrary.di.AppGraph
import com.architectpsg.commandlibrary.di.SystemUtcClock
import com.architectpsg.commandlibrary.feature.collection.persistence.SqlLocalInventory
import com.architectpsg.commandlibrary.feature.shell.ShellPorts
import com.architectpsg.commandlibrary.feature.shell.R as ShellR
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.Locale
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

/** Actual AppGraph/Room/renderer/controller/SAF/resolver/writer. Only OS destinations are synthetic. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class CollectionTextExportAppIntegrationTest {
    private val rule = createAndroidComposeRule<MainActivity>()
    private val suffix = UUID.randomUUID().toString()
    private val authority = "synthetic.collection.export.$suffix"
    private val cancel = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private lateinit var app: CommandLibraryApplication
    private lateinit var ports: ShellPorts
    private lateinit var source: InventorySnapshot
    private lateinit var unrelated: DeckSnapshot
    private lateinit var omitted: ByteArtifact
    private lateinit var included: ByteArtifact
    private lateinit var destinations: Map<String, Destination>
    private lateinit var provider: SelectedCollectionDocumentProvider
    private lateinit var before: List<Any?>
    private val inventory get() = checkNotNull(ports.localInventory)
    private val library get() = checkNotNull(ports.localDecks)
    private class Destination(val uri: Uri, val file: File, val partial: ByteArray, val fail: Boolean, val privateMessage: String) {
        val sentinel = ByteArray(32_768) { 90 }
        val opens = AtomicInteger()
        val modes = CopyOnWriteArrayList<String>()
    }
    /** Failure deliberately truncates/writes DURING OPEN before returning any descriptor. */
    private class SelectedCollectionDocumentProvider(private val destinations: Map<Uri, Destination>) : ContentProvider() {
        val mutations = AtomicInteger()
        override fun onCreate() = true
        private fun target(uri: Uri) = destinations[uri] ?: throw FileNotFoundException("Unknown synthetic destination")
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (mode != "wt") throw FileNotFoundException("Unexpected export mode")
            val destination = target(uri)
            destination.opens.incrementAndGet(); destination.modes.add(mode)
            if (destination.fail) {
                destination.file.writeBytes(destination.partial)
                throw FileNotFoundException(destination.privateMessage)
            }
            return ParcelFileDescriptor.open(destination.file, ParcelFileDescriptor.parseMode(mode))
        }
        override fun getType(uri: Uri): String { target(uri); return "text/plain" }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? =
            throw UnsupportedOperationException("Export must not query destination metadata")
        override fun insert(uri: Uri, values: ContentValues?): Uri? { mutations.incrementAndGet(); throw UnsupportedOperationException() }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int { mutations.incrementAndGet(); throw UnsupportedOperationException() }
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int { mutations.incrementAndGet(); throw UnsupportedOperationException() }
    }
    private fun <T> ok(value: Outcome<T>): T {
        assertTrue("Expected success: $value", value is Outcome.Success)
        return (value as Outcome.Success).value
    }
    private val seed = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            app = ApplicationProvider.getApplicationContext()
            destinations = listOf("revoked", "A", "B", "opt-in").associateWith { key ->
                Destination(Uri.parse("content://$authority/document/private-$key"), File(app.cacheDir, "collection-$suffix-$key.txt"),
                    "Synthetic partial $suffix\n".toByteArray(Charsets.UTF_8), key == "A", "PRIVATE_OPEN_FAILURE $suffix $key")
                    .also { it.file.writeBytes(it.sentinel) }
            }
            provider = SelectedCollectionDocumentProvider(destinations.values.associateBy { it.uri })
            ContentProviderController.of(provider).create(authority)
            runBlocking {
                ports = ok(app.graph.ports())
                val known = CardIdentity(Knowledge.Known(OracleId(ProviderId("synthetic-oracle"), "qualified-oracle")),
                    Knowledge.Known(CardId(ProviderId("synthetic-printing"), "qualified-printing")),
                    ProviderIdentity(ProviderId("foreign"), "original", "raw Ω"), Knowledge.Known("Ω \"quoted\"\nSecond line"), LanguageTag("ja"))
                val lots = (0 until 12).map { index ->
                    val identity = when (index) {
                        0, 2 -> known // Same identity remains separate physical lots with distinct saved metadata.
                        1 -> CardIdentity(Knowledge.Unknown("Original uncertain oracle", SnapshotList.empty()), Knowledge.Absent("No printing"),
                            null, Knowledge.Unknown("No guessed name", SnapshotList.empty()), LanguageTag.ENGLISH)
                        else -> known.copy(originalName = Knowledge.Known("Saved card $index"))
                    }
                    OwnedLot(EntityId("collection-export-${index.toString().padStart(2, '0')}-$suffix"), identity,
                        if (index == 1) Knowledge.Unknown("Original finish evidence omitted", SnapshotList.empty()) else Knowledge.Known(Finish.NONFOIL),
                        if (index == 1) CardCondition.UNKNOWN else CardCondition.LIGHT_PLAYED,
                        "PRIVATE-LOCATION-$index 棚", index, "PRIVATE-NOTES-$index \"Ω\"\nRetained", Revision(0),
                        if (index == 1) Knowledge.Absent("Not recorded") else Knowledge.Known(UtcTimestamp(123, 456)))
                }
                val initial = ok(inventory.snapshot(cancel))
                assertTrue(ok(inventory.apply(CollectionPatch(SnapshotList.copyOf(lots.map { CollectionEdit.Add(it) })), initial.revision,
                    EntityId("seed-collection-export-$suffix"), cancel)) is InventoryApplyResult.Committed)
                source = ok(inventory.snapshot(cancel))
                assertTrue(source.complete); assertEquals(12, source.lots.size); assertEquals(66L, source.lots.sumOf { it.quantity.toLong() })
                unrelated = PlainDeckExportBoundaryFixture.source(DeckId(LocalDeckLibraryLimits.SOURCE, "unrelated-collection-$suffix"),
                    "Unrelated saved deck Ω", checkNotNull(ports.utcClock).utcNow())
                unrelated = ok(library.create(unrelated, EntityId("seed-unrelated-collection-$suffix"), cancel))
                val renderer = checkNotNull(ports.collectionTextRendering) // Actual production binding, never replaced.
                omitted = ok(renderer.render(source, false, false, cancel))
                included = ok(renderer.render(source, true, true, cancel))
                assertArtifact(omitted, false); assertArtifact(included, true)
            }
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
    private fun scroll(matcher: SemanticsMatcher) = await {
        if (!exists("collection-text-export-list")) false else try {
            rule.onNodeWithTag("collection-text-export-list").performScrollToNode(matcher); true
        } catch (_: AssertionError) { false }
    }
    private fun exportClick(tag: String) { scroll(hasTestTag(tag)); click(tag) }
    private fun exportText(tag: String, text: String) {
        scroll(hasTestTag(tag)); rule.onNodeWithTag(tag).assertTextEquals(text).assertIsDisplayed()
    }
    private fun open() {
        navigate("shell-nav-collection"); click("collection-text-export-open")
        await { exists("collection-text-export-list") }
    }
    private fun choicesOff() {
        scroll(hasTestTag("collection-text-export-notes")); rule.onNodeWithTag("collection-text-export-notes").assertIsOff()
        scroll(hasTestTag("collection-text-export-locations")); rule.onNodeWithTag("collection-text-export-locations").assertIsOff()
    }
    private fun review(artifact: ByteArtifact) {
        exportText("collection-text-export-capture", rule.activity.getString(ShellR.string.collection_text_export_capture,
            source.revision.value, source.lots.size, 66L, artifact.bytes.size))
        for (loss in artifact.losses) {
            scroll(hasText(loss)); rule.onNodeWithText(loss).assertIsDisplayed()
        }
        val sample = rule.activity.getString(ShellR.string.collection_text_export_sample, 10)
        scroll(hasText(sample)); rule.onNodeWithText(sample).assertIsDisplayed()
        scroll(hasTestTag("collection-text-export-save"))
        enabled("collection-text-export-save")
    }
    private fun prepare() {
        await { exists("shell-nav-collection") || exists("shell-nav-more") }
        before = baseline()
        open(); choicesOff(); exportClick("collection-text-export-prepare"); review(omitted)
        assertNull(shadowOf(rule.activity).peekNextStartedActivityForResult()); noOpens(); unchanged()
    }
    private fun choose(): IntentForResult {
        exportClick("collection-text-export-save")
        await { shadowOf(rule.activity).peekNextStartedActivityForResult() != null }
        return checkNotNull(shadowOf(rule.activity).nextStartedActivityForResult).also {
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, it.intent.action); assertEquals("text/plain", it.intent.type)
            assertEquals("command-library-collection-r${source.revision.value}.txt", it.intent.getStringExtra(Intent.EXTRA_TITLE))
        }
    }
    private fun deliver(launch: IntentForResult, key: String) {
        rule.runOnIdle { shadowOf(rule.activity).receiveResult(launch.intent, Activity.RESULT_OK,
            Intent().setData(destinations.getValue(key).uri).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) }
    }
    private fun noOpens() {
        destinations.values.forEach {
            assertEquals(0, it.opens.get()); assertTrue(it.modes.isEmpty()); assertArrayEquals(it.sentinel, it.file.readBytes())
        }
    }
    private fun assertArtifact(artifact: ByteArtifact, privateFields: Boolean) {
        val bytes = artifact.bytes.copyBytes(); val text = bytes.toString(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
        assertEquals(ContentHash("SHA-256", digest), artifact.checksum)
        assertEquals("text/plain;charset=utf-8", artifact.mediaType); assertNull(artifact.manifest)
        assertTrue(text.startsWith("Command Library Collection List 1\nInventory revision: ${source.revision.value}\n"))
        assertTrue(text.contains("Lot count: 12\nPhysical quantity: 66\n"))
        assertTrue(text.contains("Original name: Known \"Ω \\\"quoted\\\"\\nSecond line\""))
        assertTrue(text.contains("Oracle: Known source=\"synthetic-oracle\" id=\"qualified-oracle\""))
        assertTrue(text.contains("Printing: Known source=\"synthetic-printing\" id=\"qualified-printing\""))
        assertTrue(text.contains("Oracle: Unknown\nPrinting: Absent")); assertTrue(text.contains("Finish: Unknown\nCondition: Unknown"))
        for ((index, lot) in source.lots.withIndex()) {
            assertTrue(text.contains("Lot ${index + 1}\nLot ID: \"${lot.id.value}\"\nLot revision: ${lot.revision.value}\nQuantity: ${lot.quantity}\n"))
            assertEquals(privateFields, text.contains("PRIVATE-NOTES-${lot.quantity} "))
            assertEquals(privateFields, text.contains("PRIVATE-LOCATION-${lot.quantity} 棚"))
        }
        assertEquals(12, Regex("(?m)^Lot ID: ").findAll(text).count())
        assertFalse(text.contains("Original uncertain oracle")); assertFalse(text.contains("No guessed name"))
    }
    private fun written(key: String, artifact: ByteArtifact) {
        exportText("collection-text-export-result", rule.activity.getString(ShellR.string.collection_text_export_written))
        val target = destinations.getValue(key)
        assertEquals(1, target.opens.get()); assertEquals(listOf("wt"), target.modes.toList())
        assertArrayEquals(artifact.bytes.copyBytes(), target.file.readBytes()); assertEquals(artifact.bytes.size.toLong(), target.file.length())
        assertArtifact(artifact, key == "opt-in"); unchanged()
    }
    private fun partial(problem: Int) {
        exportText("collection-text-export-result", rule.activity.getString(ShellR.string.plain_export_possibly_modified))
        val label = rule.activity.getString(problem)
        scroll(hasText(label)); rule.onNodeWithText(label).assertIsDisplayed()
        val target = destinations.getValue("A")
        assertEquals(1, target.opens.get()); assertEquals(listOf("wt"), target.modes.toList())
        assertArrayEquals(target.partial, target.file.readBytes())
        for (privateText in listOf(target.privateMessage, target.uri.toString(), target.file.absolutePath, "PRIVATE_OPEN_FAILURE"))
            rule.onAllNodes(hasText(privateText, substring = true), useUnmergedTree = true).assertCountEquals(0)
        assertNull(shadowOf(rule.activity).peekNextStartedActivityForResult()); unchanged()
    }
    private fun hideNavigateReopen() {
        exportClick("collection-text-export-hide"); await { !exists("collection-text-export-list") }
        navigate("shell-nav-cards"); open()
    }
    private fun baseline(): List<Any?> = runBlocking {
        val values = listOf(ok(inventory.snapshot(cancel)), ok(library.load(unrelated.id, cancel)), ok(library.list(PageRequest(100), cancel)),
            ok(ports.settings.read(cancel)), ok(ports.catalogStatus.status(cancel)), ok(checkNotNull(ports.personalTags).load(cancel)),
            ok(checkNotNull(ports.providerProfiles).list(PageRequest(100), cancel)), ok(checkNotNull(ports.playerProfiles).list(PageRequest(100), cancel)),
            ok(checkNotNull(ports.ordinaryGames).history(GameHistoryFilter(), PageRequest(100), cancel)),
            ok(checkNotNull(ports.localDeckVersions).list(unrelated.id, PageRequest(100), cancel)))
        ok(StorageDatabase.open(app, AppGraph.DATABASE_FILE)).use { db ->
            val reader = SqlLocalInventory(db, OwnedLotPayloadCodec(), LocalInventoryPatchCodec(), SystemUtcClock,
                ok(db.capabilities(cancel)).maxBoundParameters)
            assertEquals(source, ok(reader.snapshot(cancel)))
            // All tables, including payload BLOBs, receipts, undo links, reserved merge IDs and unrelated domains.
            // Explicit row counts prevent the bounded query API silently turning this into a partial baseline.
            suspend fun rows(sql: String) = ok(db.query(SqlRequest(sql, SnapshotList.empty(), SqlRequest.MAX_ROWS), cancel))
            val tableCount = (rows("SELECT count(*) AS n FROM sqlite_master WHERE type='table'").single().columns.single().value as SqlValue.Integer).value
            assertTrue("Fixture schema must fit the bounded complete table enumeration", tableCount <= SqlRequest.MAX_ROWS)
            val names = rows("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").map { (it.columns.single().value as SqlValue.Text).value }
            assertEquals(tableCount, names.size.toLong())
            val stored = names.associateWith { name ->
                assertTrue(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")))
                val count = (rows("SELECT count(*) AS n FROM \"$name\"").single().columns.single().value as SqlValue.Integer).value
                assertTrue("Fixture table $name must be captured completely", count <= SqlRequest.MAX_ROWS)
                rows("SELECT * FROM \"$name\" ORDER BY 1").also { assertEquals(count, it.size.toLong()) }
            }
            values + listOf(stored)
        }
    }
    private fun unchanged() {
        assertEquals(before, baseline()); assertEquals(0, provider.mutations.get())
    }

    @Test fun completeDefaultPrivacyCaptureSurvivesRecreationRevocationAndWritesOnceThenExplicitOptIn() {
        prepare()
        rule.activityRule.scenario.recreate(); review(omitted); noOpens(); unchanged()
        val cancelled = choose()
        rule.activityRule.scenario.recreate()
        rule.runOnIdle { shadowOf(rule.activity).callOnActivityResult(cancelled.requestCode, Activity.RESULT_CANCELED, null) }
        review(omitted); noOpens(); unchanged()
        val revoked = choose()
        hideNavigateReopen()
        scroll(hasTestTag("collection-text-export-save")); rule.onNodeWithTag("collection-text-export-save").assertIsNotEnabled()
        deliver(revoked, "revoked"); review(omitted); noOpens(); unchanged()
        val launch = choose(); deliver(launch, "B"); written("B", omitted)
        rule.activityRule.scenario.recreate(); review(omitted); written("B", omitted)
        rule.runOnIdle { shadowOf(rule.activity).callOnActivityResult(launch.requestCode, Activity.RESULT_OK,
            Intent().setData(destinations.getValue("B").uri).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) }
        rule.waitForIdle(); written("B", omitted)
        exportClick("collection-text-export-end"); await { !exists("collection-text-export-list") }
        open(); choicesOff()
        exportClick("collection-text-export-notes"); rule.onNodeWithTag("collection-text-export-notes").assertIsOn()
        exportClick("collection-text-export-locations"); rule.onNodeWithTag("collection-text-export-locations").assertIsOn()
        exportClick("collection-text-export-prepare"); review(included)
        assertEquals(0, destinations.getValue("opt-in").opens.get())
        deliver(choose(), "opt-in"); written("opt-in", included)
        assertEquals(0, destinations.getValue("revoked").opens.get()); assertEquals(0, destinations.getValue("A").opens.get())
        assertArrayEquals(omitted.bytes.copyBytes(), destinations.getValue("B").file.readBytes()); assertEquals(1, destinations.getValue("B").opens.get())
        exportClick("collection-text-export-end"); await { !exists("collection-text-export-list") }; unchanged()
        assertNull(shadowOf(rule.activity).peekNextStartedActivityForResult())
    }

    @Test fun providerPartialOpenIsUncertainAndRedactedWithReusedUriRejectedBeforeFreshFrozenRetry() {
        prepare(); deliver(choose(), "A"); partial(ShellR.string.shell_problem_persistence); review(omitted)
        assertEquals(0, destinations.getValue("B").opens.get())
        hideNavigateReopen(); partial(ShellR.string.shell_problem_persistence); review(omitted)
        deliver(choose(), "A"); partial(ShellR.string.shell_problem_invalid_input)
        deliver(choose(), "B"); written("B", omitted)
        assertArrayEquals(destinations.getValue("A").partial, destinations.getValue("A").file.readBytes())
        assertEquals(1, destinations.getValue("A").opens.get()); assertEquals(0, destinations.getValue("opt-in").opens.get())
        rule.activityRule.scenario.recreate(); review(omitted); written("B", omitted)
        assertEquals(1, destinations.getValue("A").opens.get()); assertEquals(0, destinations.getValue("revoked").opens.get())
        assertNull(shadowOf(rule.activity).peekNextStartedActivityForResult()); unchanged()
    }
}
