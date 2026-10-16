package com.architectpsg.commandlibrary

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.di.AppGraph
import com.architectpsg.commandlibrary.feature.ai.SqlLocalPromptOverrides
import com.architectpsg.commandlibrary.feature.shell.ShellPorts
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.annotation.Config

/** Actual host Room, MainActivity and engine. Opaque legacy life bytes prove physical preservation only. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class PromptOverridesAppIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private lateinit var ports: ShellPorts
    private lateinit var deck: DeckSnapshot
    private lateinit var file: File
    private lateinit var oldObjects: List<List<SqlValue>>
    private lateinit var oldData: List<List<List<SqlValue>>>
    private val bodies = OfflineLabMode.entries.associateWith { "Saved ${it.name} Ω 日本語\n{{deck}}\n{{constraints}}\n{{request}}" }
    private val question = "Which strategy fits? 日本語"
    private val constraints = "Preserve unknown budgets Ω"
    private fun <T> ok(result: Outcome<T>): T {
        assertTrue("Expected success: $result", result is Outcome.Success)
        return (result as Outcome.Success).value
    }
    private val seed = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            runBlocking {
                val app = ApplicationProvider.getApplicationContext<CommandLibraryApplication>()
                file = app.getDatabasePath(AppGraph.DATABASE_FILE)
                assertFalse("Bootstrap must precede first AppGraph.ports()", file.exists())
                assertTrue(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
                val old = schema(16)
                assertEquals(16, old.getInt("version"))
                assertEquals(49, old.getJSONArray("entities").length())
                BundledSQLiteDriver().open(file.path).use { connection ->
                    val entities = old.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val table = entity.getString("tableName")
                        connection.exec(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        entity.optJSONArray("indices")?.let { indices ->
                            for (j in 0 until indices.length()) connection.exec(indices.getJSONObject(j)
                                .getString("createSql").replace("\${TABLE_NAME}", table))
                        }
                    }
                    val setup = old.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) connection.exec(setup.getString(i))
                    connection.exec("PRAGMA user_version=16")
                    connection.exec("PRAGMA foreign_keys=ON")
                    connection.exec("INSERT INTO life_profile VALUES('legacy-life',7,'É 日本語','é 日本語',X'0001FF8041',7,9)")
                    connection.exec("INSERT INTO provider_profile VALUES('legacy-deleted-provider',7,11,1,1,2,3,4,1,0,'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',NULL)")
                    assertEquals(listOf(listOf(SqlValue.Integer(16))), connection.rows("PRAGMA user_version"))
                    assertEquals(listOf(listOf(SqlValue.Text(old.getString("identityHash")))), connection.rows(IDENTITY))
                    assertTrue(connection.rows("PRAGMA foreign_key_check").isEmpty())
                    oldObjects = connection.rows(OBJECTS)
                    oldData = LEGACY.map { connection.rows(it) }
                }
                ports = ok(app.graph.ports()) // Actual root opens and migrates the schema16 file.
                checkNotNull(ports.promptOverrides)
                val suffix = UUID.randomUUID().toString()
                deck = PlainDeckExportBoundaryFixture.source(DeckId(LocalDeckLibraryLimits.SOURCE, "prompt-overrides-$suffix"),
                    "Prompt overrides $suffix", checkNotNull(ports.utcClock).utcNow())
                ok(checkNotNull(ports.deckSnapshotValidation)(deck, token))
                assertEquals(deck, ok(checkNotNull(ports.localDecks).create(deck, EntityId("prompt-overrides-seed-$suffix"), token)))
                for (mode in OfflineLabMode.entries) assertNull(ok(checkNotNull(ports.promptOverrides).load(mode, token)))
            }
            base.evaluate()
        }
    } }
    @get:Rule val chain: RuleChain = RuleChain.outerRule(seed).around(compose)

    private fun baseline(): List<Any> = runBlocking { listOf(ok(checkNotNull(ports.localDecks).load(deck.id, token)),
        ok(checkNotNull(ports.localDecks).list(PageRequest(100), token)), ok(checkNotNull(ports.localInventory).snapshot(token)),
        ok(ports.settings.read(token)), ok(ports.catalogStatus.status(token))) }
    private fun stored(mode: OfflineLabMode): StoredPromptOverride? = runBlocking { ok(checkNotNull(ports.promptOverrides).load(mode, token)) }
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun await(check: () -> Boolean) = compose.waitUntil(20_000) { compose.waitForIdle(); check() }
    private fun scroll(list: String, tag: String) = await {
        if (!exists(list)) false else try { compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag)); true } catch (_: AssertionError) { false }
    }
    private fun enabled(tag: String) {
        scroll("offline-prompt-list", tag)
        await { compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun click(tag: String) { enabled(tag); compose.onNodeWithTag(tag).performClick() }
    private fun edit(tag: String, value: String) { enabled(tag); compose.onNodeWithTag(tag).performTextReplacement(value) }
    private fun text(tag: String): String {
        scroll("offline-prompt-list", tag)
        val config = compose.onNodeWithTag(tag).fetchSemanticsNode().config
        return if (config.contains(SemanticsProperties.EditableText)) config[SemanticsProperties.EditableText].text
        else config[SemanticsProperties.Text].joinToString("") { it.text }
    }
    private fun navigate(tag: String) {
        await { exists(tag) || exists("shell-nav-more") }
        if (!exists(tag)) compose.onNodeWithTag("shell-nav-more").performClick()
        compose.onNodeWithTag(tag).performClick()
    }
    private fun openPrompt() {
        navigate("shell-nav-decks")
        await { exists("deck-library-overview") || exists("deck-library-list") }
        if (!exists("deck-library-overview")) {
            val row = "deck-open-${deck.id.raw}"
            scroll("deck-library-list", row); compose.onNodeWithTag(row).performClick()
        }
        scroll("deck-library-overview", "deck-offline-prompt")
        compose.onNodeWithTag("deck-offline-prompt").assertIsEnabled().performClick()
        enabled("offline-prompt-override")
        edit("offline-prompt-question", question)
        edit("offline-prompt-constraints", constraints)
    }
    private fun closePrompt() {
        compose.onNodeWithText(compose.activity.getString(com.architectpsg.commandlibrary.feature.shell.R.string.shell_action_dismiss)).performClick()
        await { !exists("offline-prompt-list") }
    }
    private fun expected(mode: OfflineLabMode, body: String?): String = runBlocking {
        ok(checkNotNull(ports.offlineDeckPrompts).render(OfflinePromptRequest(deck, mode, RoastIntensity.GENTLE,
            question, constraints, false, null, body), token)).artifact.rendered
    }
    private fun assertPreview(mode: OfflineLabMode, body: String?) {
        val rendered = expected(mode, body)
        click("offline-prompt-render")
        scroll("offline-prompt-list", "offline-prompt-preview")
        assertEquals(rendered, text("offline-prompt-preview"))
    }
    private fun independentRows(): Map<OfflineLabMode, StoredPromptOverride?> = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CommandLibraryApplication>()
        ok(StorageDatabase.open(app, AppGraph.DATABASE_FILE)).use { db ->
            assertEquals(19, ok(db.capabilities(token)).schemaVersion)
            val reader = SqlLocalPromptOverrides(db, checkNotNull(ports.utcClock))
            OfflineLabMode.entries.associateWith { ok(reader.load(it, token)) }
        }
    }

    @Test fun actualMigrationFourModeSaveReopenActivityRecreationAndExplicitResetConserveOtherUserState() {
        val before = baseline()
        openPrompt()
        val saved = mutableMapOf<OfflineLabMode, StoredPromptOverride>()
        for (mode in OfflineLabMode.entries) {
            click("offline-prompt-mode-${mode.name}")
            edit("offline-prompt-override", bodies.getValue(mode))
            click("offline-prompt-save-template")
            await { stored(mode)?.body == bodies.getValue(mode) }
            val row = checkNotNull(stored(mode))
            assertEquals(mode.shippedVersion, row.shippedVersion)
            assertEquals(Revision(1), row.revision)
            saved[mode] = row
            scroll("offline-prompt-list", "offline-prompt-template-saved")
            compose.onNodeWithTag("offline-prompt-template-saved").assertExists()
            assertPreview(mode, bodies.getValue(mode))
        }
        closePrompt()
        assertEquals(saved, independentRows()) // A separate actual Room handle sees exact persisted rows.
        compose.activityRule.scenario.recreate()
        openPrompt()
        for (mode in OfflineLabMode.entries) {
            click("offline-prompt-mode-${mode.name}")
            enabled("offline-prompt-override")
            assertEquals(bodies.getValue(mode), text("offline-prompt-override"))
            assertPreview(mode, bodies.getValue(mode))
        }
        val resetMode = OfflineLabMode.CHAT
        click("offline-prompt-mode-${resetMode.name}")
        click("offline-prompt-reset")
        await { stored(resetMode)?.let { it.body == null && it.revision == Revision(2) } == true }
        enabled("offline-prompt-reset") // Wait for the UI mutation acknowledgement as well as the committed row.
        val reset = checkNotNull(stored(resetMode))
        assertEquals(resetMode.shippedVersion, reset.shippedVersion)
        val shipped = checkNotNull(ports.offlineDeckPrompts).templates().single { it.id == resetMode.templateId }.body
        enabled("offline-prompt-override")
        assertEquals(shipped, text("offline-prompt-override"))
        assertPreview(resetMode, null)
        closePrompt()
        assertEquals(saved + (resetMode to reset), independentRows())
        assertEquals(before, baseline())
        BundledSQLiteDriver().open(file.path).use { connection ->
            assertEquals(listOf(listOf(SqlValue.Integer(18))), connection.rows("PRAGMA user_version"))
            assertEquals(listOf(listOf(SqlValue.Text(schema(18).getString("identityHash")))), connection.rows(IDENTITY))
            val afterObjects = connection.rows(OBJECTS)
            assertEquals(oldObjects, afterObjects.filter { it[1] !in ADDED_OBJECT_NAMES })
            assertEquals(ADDED_OBJECT_NAMES, afterObjects.filter { it !in oldObjects }.map { it[1] }.toSet())
            assertEquals(oldData, LEGACY.map { connection.rows(it) })
            assertTrue(connection.rows("PRAGMA foreign_key_check").isEmpty())
            assertEquals(listOf(listOf(SqlValue.Integer(1), SqlValue.Integer(0), SqlValue.Null)), connection.rows(
                "SELECT format_version,body_length,body FROM prompt_override WHERE mode='CHAT'"))
        }
    }

    private fun schema(version: Int): JSONObject {
        val relative = "data/storage/schemas/com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase/$version.json"
        val candidates = listOf(File(relative), File("../$relative"), File("android-app/$relative"))
        val source = checkNotNull(candidates.firstOrNull { it.isFile }) { "Missing actual KSP schema$version export from ${File(".").absolutePath}" }
        return JSONObject(source.readText()).getJSONObject("database")
    }
    private fun SQLiteConnection.exec(sql: String) { prepare(sql).use { it.step() } }
    private fun SQLiteConnection.rows(sql: String): List<List<SqlValue>> = prepare(sql).use { statement ->
        buildList {
            while (statement.step()) add((0 until statement.getColumnCount()).map { column ->
                when (statement.getColumnType(column)) {
                    1 -> SqlValue.Integer(statement.getLong(column))
                    3 -> SqlValue.Text(statement.getText(column))
                    4 -> SqlValue.Blob(SnapshotBytes(statement.getBlob(column)))
                    5 -> SqlValue.Null
                    else -> error("Unexpected physical fixture column type")
                }
            })
        }
    }
    private companion object {
        val ADDED_OBJECT_NAMES = setOf(SqlValue.Text("prompt_override"), SqlValue.Text("ai_conversation"), SqlValue.Text("index_ai_conversation_change_sequence"), SqlValue.Text("index_ai_conversation_deck"), SqlValue.Text("ai_turn"), SqlValue.Text("index_ai_turn_conversation_ordinal"), SqlValue.Text("index_ai_turn_recovery"))
        const val OBJECTS = "SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name<>'room_master_table' ORDER BY type,name"
        const val IDENTITY = "SELECT identity_hash FROM room_master_table WHERE id=42"
        val LEGACY = listOf("SELECT * FROM life_profile ORDER BY profile_id", "SELECT * FROM provider_profile ORDER BY profile_id")
    }
}
