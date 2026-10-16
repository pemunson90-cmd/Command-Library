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
import com.architectpsg.commandlibrary.data.storage.ai.codec.AiHistoryPayloadCodec
import com.architectpsg.commandlibrary.di.AppGraph
import com.architectpsg.commandlibrary.feature.ai.SqlLocalAiHistory
import com.architectpsg.commandlibrary.feature.shell.ShellPorts
import java.io.File
import java.security.MessageDigest
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

/** Actual host Room/MainActivity offline capture. Synthetic legacy life bytes prove physical preservation only. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class AiHistoryAppIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private lateinit var ports: ShellPorts
    private lateinit var history: LocalAiHistory
    private lateinit var deck: DeckSnapshot
    private lateinit var file: File
    private lateinit var oldObjects: List<List<SqlValue>>
    private lateinit var oldData: List<List<List<SqlValue>>>
    private val body = "Retained CHAT Ω 日本語\n{{deck}}\n{{constraints}}\n{{request}}"
    private val question = "Which strategy fits? 日本語"
    private val constraints = "Preserve unknown budgets Ω"
    private val reply = "Pasted offline reply Ω 日本語 — preserve exactly.\nNo generated provider response."
    private val title = "Saved offline capture É 日本語"
    private fun <T> ok(result: Outcome<T>): T {
        assertTrue("Expected success: $result", result is Outcome.Success)
        return (result as Outcome.Success).value
    }
    private fun missing(result: Outcome<*>) {
        assertTrue("Expected NOT_FOUND: $result", result is Outcome.Unavailable)
        assertEquals(UnavailableReason.NOT_FOUND, (result as Outcome.Unavailable).reason)
    }
    private fun stale(result: Outcome<*>) {
        assertTrue("Expected STALE_REVISION: $result", result is Outcome.Failure)
        assertEquals(FailureCode.STALE_REVISION, (result as Outcome.Failure).error.code)
    }
    private val seed = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            runBlocking {
                val app = ApplicationProvider.getApplicationContext<CommandLibraryApplication>()
                file = app.getDatabasePath(AppGraph.DATABASE_FILE)
                assertFalse("Bootstrap must precede first AppGraph.ports()", file.exists())
                assertTrue(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
                val old = schema(17)
                assertEquals(17, old.getInt("version"))
                assertEquals(50, old.getJSONArray("entities").length())
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
                    connection.exec("PRAGMA user_version=17")
                    connection.exec("PRAGMA foreign_keys=ON")
                    connection.exec("INSERT INTO life_profile VALUES('legacy-life',7,'É 日本語','é 日本語',X'0001FF8041',7,9)")
                    connection.exec("INSERT INTO provider_profile VALUES('legacy-deleted-provider',7,11,1,1,2,3,4,1,0,'$EMPTY_HASH',NULL)")
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    connection.prepare("INSERT INTO prompt_override VALUES('CHAT',1,1,7,11,23,?,?,?)").use { statement ->
                        statement.bindLong(1, bytes.size.toLong()); statement.bindText(2, hash(bytes)); statement.bindBlob(3, bytes)
                        statement.step()
                    }
                    assertEquals(listOf(listOf(SqlValue.Integer(17))), connection.rows("PRAGMA user_version"))
                    assertEquals(listOf(listOf(SqlValue.Text(old.getString("identityHash")))), connection.rows(IDENTITY))
                    assertTrue(connection.rows("PRAGMA foreign_key_check").isEmpty())
                    oldObjects = connection.rows(OBJECTS)
                    oldData = LEGACY.map { connection.rows(it) }
                }
                ports = ok(app.graph.ports()) // Actual AppGraph opens Room and migrates this schema17 file.
                history = ok(checkNotNull(ports.aiHistory).open(token)) // Root recovery succeeds before capability publication.
                assertSame(history, ok(checkNotNull(ports.aiHistory).open(token)))
                val suffix = UUID.randomUUID().toString()
                deck = PlainDeckExportBoundaryFixture.source(DeckId(LocalDeckLibraryLimits.SOURCE, "ai-history-$suffix"),
                    "Offline history $suffix", checkNotNull(ports.utcClock).utcNow())
                ok(checkNotNull(ports.deckSnapshotValidation)(deck, token))
                assertEquals(deck, ok(checkNotNull(ports.localDecks).create(deck, EntityId("ai-history-seed-$suffix"), token)))
                assertEquals(body, checkNotNull(ok(checkNotNull(ports.promptOverrides).load(OfflineLabMode.CHAT, token))).body)
                assertTrue(ok(history.pageConversations(deck.id, PageRequest(10), token)).items.isEmpty())
            }
            base.evaluate()
        }
    } }
    @get:Rule val chain: RuleChain = RuleChain.outerRule(seed).around(compose)

    private fun baseline(): List<Any?> = runBlocking { listOf(ok(checkNotNull(ports.localDecks).load(deck.id, token)),
        ok(checkNotNull(ports.localDecks).list(PageRequest(100), token)), ok(checkNotNull(ports.localInventory).snapshot(token)),
        ok(ports.settings.read(token)), ok(ports.catalogStatus.status(token)),
        ok(checkNotNull(ports.providerProfiles).list(PageRequest(100), token)),
        OfflineLabMode.entries.associateWith { ok(checkNotNull(ports.promptOverrides).load(it, token)) }) }
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun await(check: () -> Boolean) = compose.waitUntil(20_000) { compose.waitForIdle(); check() }
    private fun scroll(list: String, tag: String) = await {
        if (!exists(list)) false else try { compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag)); true } catch (_: AssertionError) { false }
    }
    private fun enabled(list: String, tag: String) {
        scroll(list, tag)
        await { compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun click(list: String, tag: String) { enabled(list, tag); compose.onNodeWithTag(tag).performClick() }
    private fun edit(list: String, tag: String, value: String) { enabled(list, tag); compose.onNodeWithTag(tag).performTextReplacement(value) }
    private fun text(list: String, tag: String): String {
        scroll(list, tag)
        val config = compose.onNodeWithTag(tag).fetchSemanticsNode().config
        return if (config.contains(SemanticsProperties.EditableText)) config[SemanticsProperties.EditableText].text
        else config[SemanticsProperties.Text].joinToString("") { it.text }
    }
    private fun navigate(tag: String) {
        await { exists(tag) || exists("shell-nav-more") }
        if (!exists(tag)) compose.onNodeWithTag("shell-nav-more").performClick()
        compose.onNodeWithTag(tag).performClick()
    }
    private fun overview() {
        navigate("shell-nav-decks")
        await { exists("deck-library-overview") || exists("deck-library-list") }
        if (!exists("deck-library-overview")) {
            val row = "deck-open-${deck.id.raw}"
            scroll("deck-library-list", row); compose.onNodeWithTag(row).performClick()
        }
    }
    private fun dismissHistory() {
        // The actual save callback hides the offline dialog before opening history: exactly one dismiss action.
        assertFalse(exists("offline-prompt-list"))
        compose.onAllNodesWithText(compose.activity.getString(com.architectpsg.commandlibrary.feature.shell.R.string.shell_action_dismiss))
            .assertCountEquals(1)
        compose.onNodeWithText(compose.activity.getString(com.architectpsg.commandlibrary.feature.shell.R.string.shell_action_dismiss)).performClick()
        await { !exists("ai-history-list") }
    }
    private fun chunks(prefix: String, expected: String): String {
        val parts = mutableListOf<String>()
        var offset = 0; var index = 0
        while (offset < expected.length) {
            var end = minOf(offset + 12_000, expected.length)
            if (end < expected.length && expected[end - 1].isHighSurrogate() && expected[end].isLowSurrogate()) end--
            parts += text("ai-history-list", "$prefix-${index++}")
            offset = end
        }
        return parts.joinToString("")
    }
    private fun <T> independent(read: suspend (LocalAiHistory) -> T): T = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CommandLibraryApplication>()
        ok(StorageDatabase.open(app, AppGraph.DATABASE_FILE)).use { db ->
            assertEquals(19, ok(db.capabilities(token)).schemaVersion)
            // Independent reads must not recover with a fabricated/new process epoch.
            read(SqlLocalAiHistory(db, AiHistoryPayloadCodec(), checkNotNull(ports.utcClock)))
        }
    }

    @Test fun actualRoomMigrationOfflineCaptureAfterReplyEditRecreationReopenAndDeletePreserveOtherUserState() {
        val before = baseline()
        overview()
        click("deck-library-overview", "deck-offline-prompt")
        click("offline-prompt-list", "offline-prompt-mode-CHAT")
        enabled("offline-prompt-list", "offline-prompt-override")
        assertEquals(body, text("offline-prompt-list", "offline-prompt-override"))
        edit("offline-prompt-list", "offline-prompt-question", question)
        edit("offline-prompt-list", "offline-prompt-constraints", constraints)
        scroll("offline-prompt-list", "offline-prompt-include-notes")
        compose.onNodeWithTag("offline-prompt-include-notes").assertIsOff()
        val expected = runBlocking { ok(checkNotNull(ports.offlineDeckPrompts).render(OfflinePromptRequest(deck,
            OfflineLabMode.CHAT, RoastIntensity.GENTLE, question, constraints, false, null, body), token)) }
        click("offline-prompt-list", "offline-prompt-render")
        assertEquals(expected.artifact.rendered, text("offline-prompt-list", "offline-prompt-preview"))
        edit("offline-prompt-list", "offline-prompt-reply", reply) // Reply editing clears preview, retains the captured prompt.
        await { !exists("offline-prompt-preview") }
        click("offline-prompt-list", "offline-prompt-save-history")
        await { !exists("offline-prompt-list") }
        edit("ai-history-list", "ai-history-title", title)
        assertTrue(runBlocking { ok(history.pageConversations(deck.id, PageRequest(10), token)).items.isEmpty() })
        click("ai-history-list", "ai-history-confirm-save")
        scroll("ai-history-list", "ai-history-saved")
        compose.onNodeWithTag("ai-history-saved").assertExists()
        val conversation = runBlocking { ok(history.pageConversations(deck.id, PageRequest(10), token)).items.single() }
        assertEquals(deck.id, conversation.deckId); assertEquals(OfflineLabMode.CHAT, conversation.mode)
        assertEquals(title, conversation.title); assertEquals(Revision(2), conversation.revision)
        val summary = runBlocking { ok(history.pageTurns(conversation.id, PageRequest(10), token)).items.single() }
        val turn = runBlocking { ok(history.getTurn(summary.id, token)) }
        assertEquals(conversation.id, turn.conversationId); assertEquals(Revision(1), turn.revision); assertEquals(1L, turn.ordinal)
        assertEquals(TurnStatus.COMPLETE, turn.status); assertEquals(HistoryTurnSource.OFFLINE_CAPTURE, turn.source)
        assertEquals(OfflineLabMode.CHAT, turn.mode); assertEquals(reply, turn.rawContent)
        // Only live render time differs from an independently invoked engine. Exact stored artifact is checked below.
        assertEquals(expected.artifact.copy(createdAt = turn.prompt.createdAt), turn.prompt)
        assertEquals(HistoryPromptProvenance(ContentHash("SHA-256", hash(body.toByteArray(Charsets.UTF_8))), true, null), turn.provenance)
        assertNull(turn.providerProfileId); assertNull(turn.modelId); assertNull(turn.ownerEpoch); assertNull(turn.retryOf)
        assertTrue(turn.usage is Knowledge.Unknown); assertTrue(turn.finishedAt is Knowledge.Known)
        assertEquals(turn.id, summary.id); assertEquals(turn.conversationId, summary.conversationId)
        assertEquals(turn.status, summary.status); assertEquals(turn.source, summary.source)
        assertEquals(turn.revision, summary.revision); assertEquals(turn.ordinal, summary.ordinal)
        assertEquals(turn.startedAt, summary.startedAt); assertEquals(turn.finishedAt, summary.finishedAt)
        dismissHistory()
        independent { reader ->
            assertEquals(conversation, ok(reader.getConversation(conversation.id, token)))
            assertEquals(turn, ok(reader.getTurn(turn.id, token)))
            assertEquals(listOf(conversation), ok(reader.pageConversations(deck.id, PageRequest(10), token)).items.toList())
            assertEquals(listOf(summary), ok(reader.pageTurns(conversation.id, PageRequest(10), token)).items.toList())
            assertTrue(ok(reader.pageConversations(deck.id.copy(raw = "unrelated-${deck.id.raw}"), PageRequest(10), token)).items.isEmpty())
        }
        compose.activityRule.scenario.recreate()
        overview()
        click("deck-library-overview", "deck-ai-history")
        click("ai-history-list", "ai-history-conversation-${conversation.id.value}")
        click("ai-history-list", "ai-history-load-turns")
        click("ai-history-list", "ai-history-turn-${turn.id.value}")
        assertEquals(reply, chunks("ai-history-reply", reply))
        val provenanceText = compose.activity.getString(com.architectpsg.commandlibrary.feature.shell.R.string.ai_history_provenance,
            turn.mode.name, turn.prompt.deckRevision.value, turn.prompt.templateVersion)
        assertEquals(provenanceText, text("ai-history-list", "ai-history-provenance"))
        click("ai-history-list", "ai-history-toggle-prompt")
        assertEquals(turn.prompt.rendered, chunks("ai-history-prompt", turn.prompt.rendered))
        click("ai-history-list", "ai-history-delete")
        click("ai-history-list", "ai-history-confirm-delete")
        scroll("ai-history-list", "ai-history-empty")
        compose.onNodeWithTag("ai-history-empty").assertExists()
        runBlocking { missing(history.getConversation(conversation.id, token)); missing(history.getTurn(turn.id, token)) }
        independent { reader ->
            missing(reader.getConversation(conversation.id, token)); missing(reader.getTurn(turn.id, token))
            assertTrue(ok(reader.pageConversations(deck.id, PageRequest(10), token)).items.isEmpty())
            stale(reader.createConversation(NewConversation(conversation.id, deck.id, OfflineLabMode.CHAT, title), token))
        }
        dismissHistory()
        assertEquals(before, baseline())
        BundledSQLiteDriver().open(file.path).use { connection ->
            assertEquals(listOf(listOf(SqlValue.Integer(18))), connection.rows("PRAGMA user_version"))
            assertEquals(listOf(listOf(SqlValue.Text(schema(18).getString("identityHash")))), connection.rows(IDENTITY))
            val afterObjects = connection.rows(OBJECTS)
            assertEquals(oldObjects, afterObjects.filter { it[1] !in ADDED })
            assertEquals(ADDED, afterObjects.filter { it !in oldObjects }.map { it[1] }.toSet())
            assertEquals(oldData, LEGACY.map { connection.rows(it) })
            assertTrue(connection.rows("PRAGMA foreign_key_check").isEmpty())
            assertEquals(listOf(listOf(SqlValue.Text(conversation.id.value), SqlValue.Integer(1), SqlValue.Text(""), SqlValue.Text(""),
                SqlValue.Text(""), SqlValue.Integer(0), SqlValue.Text(EMPTY_HASH), SqlValue.Null)), connection.rows(
                "SELECT conversation_id,deleted,deck_source,deck_raw,mode,payload_length,payload_sha256,payload FROM ai_conversation"))
            assertEquals(listOf(listOf(SqlValue.Text(turn.id.value), SqlValue.Text(conversation.id.value), SqlValue.Integer(1), SqlValue.Text(""),
                SqlValue.Null, SqlValue.Text(""), SqlValue.Integer(0), SqlValue.Text(EMPTY_HASH), SqlValue.Null)), connection.rows(
                "SELECT turn_id,conversation_id,deleted,source,owner_epoch,status,payload_length,payload_sha256,payload FROM ai_turn"))
        }
    }

    private fun schema(version: Int): JSONObject {
        val relative = "data/storage/schemas/com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase/$version.json"
        val candidates = listOf(File(relative), File("../$relative"), File("android-app/$relative"))
        val source = checkNotNull(candidates.firstOrNull { it.isFile }) { "Missing actual KSP schema$version export from ${File(".").absolutePath}" }
        return JSONObject(source.readText()).getJSONObject("database")
    }
    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
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
        const val EMPTY_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        const val OBJECTS = "SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name<>'room_master_table' ORDER BY type,name"
        const val IDENTITY = "SELECT identity_hash FROM room_master_table WHERE id=42"
        val LEGACY = listOf("SELECT * FROM life_profile ORDER BY profile_id", "SELECT * FROM provider_profile ORDER BY profile_id", "SELECT * FROM prompt_override ORDER BY mode")
        val ADDED = setOf("ai_conversation", "ai_turn", "index_ai_conversation_change_sequence", "index_ai_conversation_deck",
            "index_ai_turn_conversation_ordinal", "index_ai_turn_recovery").map { SqlValue.Text(it) }.toSet()
    }
}
