package com.architectpsg.commandlibrary

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.data.storage.ai.codec.AiHistoryPayloadCodec
import com.architectpsg.commandlibrary.di.AppAiHistoryAccess
import com.architectpsg.commandlibrary.di.AppGraph
import com.architectpsg.commandlibrary.di.AppProviderGenerationAccess
import com.architectpsg.commandlibrary.feature.ai.SqlLocalAiHistory
import com.architectpsg.commandlibrary.feature.shell.ShellPorts
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Actual MainActivity/AppGraph/Room/client/codec; deliberately synthetic native Messages SSE, no socket or provider certification. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class AnthropicProviderGenerationAppIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private lateinit var app: CommandLibraryApplication
    private lateinit var ports: ShellPorts
    private lateinit var deck: DeckSnapshot
    private lateinit var saved: StoredProviderProfile
    private lateinit var network: SyntheticNetwork
    private lateinit var capturedPrompt: String
    private val prefix = "Synthetic prefix 日本語 Ω"
    private val suffix = " — complete."
    private fun <T> ok(result: Outcome<T>): T {
        assertTrue("Expected success: $result", result is Outcome.Success)
        return (result as Outcome.Success).value
    }

    private val seed = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            runBlocking {
                app = ApplicationProvider.getApplicationContext()
                ports = ok(app.graph.ports())
                val clock = checkNotNull(ports.utcClock)
                deck = PlainDeckExportBoundaryFixture.source(DeckId(LocalDeckLibraryLimits.SOURCE, "anthropic-provider-proof"),
                    "Synthetic native provider proof", clock.utcNow())
                ok(checkNotNull(ports.localDecks).create(deck, EntityId("anthropic-provider-proof-seed"), token))
                val profiles = checkNotNull(ports.providerProfiles)
                val id = EntityId("synthetic-keyless-anthropic-provider")
                val profile = ProviderProfile(id, "Synthetic keyless native provider", ProviderDialect.ANTHROPIC,
                    EndpointProfile(id, Origin("https", "example.test", 443), SnapshotList.empty(), null, 1,
                        RetryPolicy(1, Duration.ZERO, 1.seconds, false)), "manual-first",
                    ProviderCapabilities(false, true, false, false, false, false), null,
                    Knowledge.Known("2023-06-01"), SnapshotList.empty(), ResourceUri("https://example.test/v1"))
                val first = ok(profiles.create(profile, token))
                saved = ok(profiles.replace(profile.copy(selectedModelId = "manual-reviewed-model"), first.revision, token))
                assertEquals(Revision(2), saved.revision)
                assertEquals(Knowledge.Known("2023-06-01"), saved.profile.apiVersion)
                assertNull(saved.profile.secretRef)
                assertEquals(saved, ok(profiles.load(id, token)))
                ok(checkNotNull(ports.aiHistory).open(token))
            }
            val db = runBlocking { ok(StorageDatabase.open(app, AppGraph.DATABASE_FILE)) }
            try {
                val history = AppAiHistoryAccess(db, checkNotNull(ports.utcClock), (ports.aiHistory as AppAiHistoryAccess).epoch)
                network = SyntheticNetwork()
                val generation = AppProviderGenerationAccess(checkNotNull(ports.providerProfiles), history,
                    checkNotNull(ports.secretVault)) { network }
                // Test-only seam: retain every actual graph port, replacing only generation's transport factory.
                // MainActivity and recreation still receive the actual cached AppGraph ShellPorts instance.
                ShellPorts::class.java.getDeclaredField("providerGeneration").apply { isAccessible = true }.set(ports, generation)
                assertSame(generation, ports.providerGeneration)
                base.evaluate()
            } finally {
                network.finish.complete(Unit)
                db.close()
            }
        }
    } }
    @get:Rule val chain: RuleChain = RuleChain.outerRule(seed).around(compose)

    private suspend fun <T> independent(read: suspend (LocalAiHistory) -> T): T =
        ok(StorageDatabase.open(app, AppGraph.DATABASE_FILE)).use { db ->
            read(SqlLocalAiHistory(db, AiHistoryPayloadCodec(), checkNotNull(ports.utcClock)))
        } // Read only: do not recover using an invented process epoch.
    private suspend fun onlyTurn(reader: LocalAiHistory): Turn {
        val conversation = ok(reader.pageConversations(deck.id, PageRequest(10), token)).items.single()
        return ok(reader.getTurn(ok(reader.pageTurns(conversation.id, PageRequest(10), token)).items.single().id, token))
    }

    private inner class SyntheticNetwork : NetworkClient {
        val attempts = AtomicInteger()
        val closes = AtomicInteger()
        val prefixCheckpointed = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        lateinit var pending: Turn
        override suspend fun send(request: RequestSpec, cancel: CancelToken): Outcome<ByteResponse> = error("Unexpected non-stream request")
        override fun stream(request: RequestSpec, cancel: CancelToken): Flow<Outcome<ByteChunk>> = flow {
            assertEquals(1, attempts.incrementAndGet())
            // A second real Room handle must see committed PENDING before synthetic transport yields bytes.
            pending = independent { onlyTurn(it) }
            assertEquals(TurnStatus.PENDING, pending.status)
            assertEquals("", pending.rawContent)
            assertEquals(capturedPrompt, pending.prompt.rendered)
            assertEquals(saved.profile.id, pending.providerProfileId)
            assertEquals(saved.profile.selectedModelId, pending.modelId)
            assertEquals((ports.aiHistory as AppAiHistoryAccess).epoch, pending.ownerEpoch)
            assertEquals(HttpMethod.POST, request.request.method)
            assertEquals("https://example.test/v1/messages", request.request.uri.value)
            assertEquals(RequestCharge.POTENTIALLY_CHARGED, request.charge)
            assertEquals(RetryClassification.NEVER_AUTOMATIC, request.retryClassification)
            assertEquals(1, request.endpoint.retry.maxAttempts)
            assertTrue(request.endpoint.allowedRedirectOrigins.isEmpty())
            assertNull(request.endpoint.secret)
            assertNull(request.idempotencyKey)
            assertEquals(setOf(HttpHeader("Content-Type", "application/json"), HttpHeader("Accept", "text/event-stream"),
                HttpHeader("anthropic-version", "2023-06-01")), request.request.headers.toSet())
            assertEquals(3, request.request.headers.size)
            assertFalse(request.request.headers.any { it.name.equals("Authorization", true) || it.name.equals("X-Api-Key", true) })
            val body = JSONObject(checkNotNull(request.request.body).copyBytes().toString(Charsets.UTF_8))
            assertEquals(setOf("model", "stream", "messages", "max_tokens"), body.keys().asSequence().toSet())
            assertEquals(saved.profile.selectedModelId, body.getString("model"))
            assertTrue(body.getBoolean("stream"))
            assertEquals(77, body.getInt("max_tokens"))
            val messages = body.getJSONArray("messages")
            assertEquals(1, messages.length())
            assertEquals(setOf("role", "content"), messages.getJSONObject(0).keys().asSequence().toSet())
            assertEquals("user", messages.getJSONObject(0).getString("role"))
            assertEquals(capturedPrompt, messages.getJSONObject(0).getString("content"))
            val first = (
                "event: message_start\ndata: {\"type\":\"message_start\",\"message\":{\"id\":\"synthetic-native-response\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"manual-reviewed-model\",\"content\":[],\"stop_reason\":null,\"stop_sequence\":null,\"usage\":{\"input_tokens\":7,\"output_tokens\":0,\"cache_creation_input_tokens\":2,\"cache_read_input_tokens\":3}}}\n\n" +
                // Nonempty block-start text is intentional: it must not vanish before text_delta.
                "event: content_block_start\ndata: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"$prefix\"}}\n\n").toByteArray()
            val metadata = HttpMetadata(200, SnapshotList.of(HttpHeader("Content-Type", "text/event-stream")),
                request.request.uri, checkNotNull(ports.utcClock).utcNow(), Knowledge.Unknown("Synthetic", SnapshotList.empty()),
                Knowledge.Unknown("Synthetic is not billing evidence", SnapshotList.empty()))
            emit(Outcome.Success(ByteChunk(0, 0, SnapshotBytes(first), metadata, false)))
            prefixCheckpointed.complete(Unit) // emit returned only after actual coordinator consumed this prefix.
            finish.await()
            val last = (
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"$suffix\"}}\n\n" +
                "event: content_block_stop\ndata: {\"type\":\"content_block_stop\",\"index\":0}\n\n" +
                "event: message_delta\ndata: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":4}}\n\n" +
                "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n").toByteArray()
            emit(Outcome.Success(ByteChunk(1, first.size.toLong(), SnapshotBytes(last), null, true)))
            error("Actual codec must stop collection at accepted native message_stop")
        }
        override suspend fun close() { closes.incrementAndGet() }
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun await(check: () -> Boolean) = compose.waitUntil(20_000) { compose.waitForIdle(); check() }
    private fun scroll(list: String, tag: String) = await {
        if (!exists(list)) false else try { compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag)); true } catch (_: AssertionError) { false }
    }
    private fun click(list: String, tag: String) {
        scroll(list, tag)
        await { compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performClick()
    }
    private fun text(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
    private fun generationClick(tag: String) {
        await { compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }
    private fun overview() {
        await { exists("shell-nav-decks") || exists("shell-nav-more") }
        if (!exists("shell-nav-decks")) compose.onNodeWithTag("shell-nav-more").performClick()
        compose.onNodeWithTag("shell-nav-decks").performClick()
        await { exists("deck-library-overview") || exists("deck-library-list") }
        if (!exists("deck-library-overview")) click("deck-library-list", "deck-open-${deck.id.raw}")
    }

    @Test fun explicitNativeLimitAndConsentStreamThroughActualClientRoomAndRecreationWithoutReplay() {
        overview()
        click("deck-library-overview", "deck-offline-prompt")
        click("offline-prompt-list", "offline-prompt-mode-CHAT")
        scroll("offline-prompt-list", "offline-prompt-question")
        compose.onNodeWithTag("offline-prompt-question").performTextReplacement("Synthetic explicit request 日本語")
        click("offline-prompt-list", "offline-prompt-render")
        scroll("offline-prompt-list", "offline-prompt-preview")
        capturedPrompt = text("offline-prompt-preview")
        assertTrue(capturedPrompt.contains("Synthetic explicit request 日本語"))
        click("offline-prompt-list", "offline-prompt-provider")
        await { exists("provider-generation-prompt") }
        assertEquals(capturedPrompt, text("provider-generation-prompt"))
        generationClick("provider-generation-profile-${saved.profile.id.value}")
        assertEquals(compose.activity.getString(com.architectpsg.commandlibrary.feature.shell.R.string.provider_generation_destination,
            saved.profile.baseUri!!.value, saved.profile.selectedModelId, saved.revision.value), text("provider-generation-destination"))
        compose.onNodeWithText(compose.activity.getString(com.architectpsg.commandlibrary.feature.shell.R.string.provider_generation_version,
            "2023-06-01")).performScrollTo().assertExists()
        compose.onNodeWithText(compose.activity.getString(com.architectpsg.commandlibrary.feature.shell.R.string.provider_generation_consent))
            .performScrollTo().assertExists()
        assertEquals("", compose.onNodeWithTag("provider-generation-max-tokens").fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text)
        compose.onNodeWithTag("provider-generation-consent").assertIsOff().assertIsNotEnabled()
        compose.onNodeWithTag("provider-generation-send").assertIsNotEnabled()
        assertEquals(0, network.attempts.get())
        runBlocking { independent { assertTrue(ok(it.pageConversations(deck.id, PageRequest(10), token)).items.isEmpty()) } }
        compose.onNodeWithTag("provider-generation-max-tokens").performScrollTo().performTextReplacement("77")
        assertEquals("77", compose.onNodeWithTag("provider-generation-max-tokens").fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text)
        compose.onNodeWithTag("provider-generation-consent").assertIsOff()
        compose.onNodeWithTag("provider-generation-send").assertIsNotEnabled()
        generationClick("provider-generation-consent")
        assertEquals(0, network.attempts.get()) // Consent alone has no dispatch side effect.
        generationClick("provider-generation-send")
        await { network.prefixCheckpointed.isCompleted }
        val streaming = runBlocking { independent { onlyTurn(it) } }
        assertEquals(network.pending.id, streaming.id)
        assertEquals(TurnStatus.STREAMING, streaming.status)
        assertEquals(prefix, streaming.rawContent)
        assertTrue(streaming.revision.value > network.pending.revision.value)
        await { exists("provider-generation-reply") && text("provider-generation-reply") == prefix }
        compose.onNodeWithTag("provider-generation-consent").assertIsOff()
        compose.onNodeWithTag("provider-generation-send").assertIsNotEnabled()
        network.finish.complete(Unit)
        await { network.closes.get() == 1 }
        val complete = runBlocking { independent { onlyTurn(it) } }
        assertEquals(streaming.id, complete.id)
        assertEquals(TurnStatus.COMPLETE, complete.status)
        assertEquals(HistoryTurnSource.PROVIDER_GENERATION, complete.source)
        assertEquals(prefix + suffix, complete.rawContent)
        assertEquals(network.pending.prompt, complete.prompt)
        assertEquals(network.pending.provenance, complete.provenance)
        assertEquals(deck.id, complete.prompt.deckId)
        assertEquals(deck.revision, complete.prompt.deckRevision)
        assertNull(complete.retryOf)
        val usage = (complete.usage as Knowledge.Known).value
        assertEquals(Knowledge.Known("synthetic-native-response"), usage.providerRequestId)
        assertEquals(Knowledge.Known(12L), usage.inputTokens)
        assertEquals(Knowledge.Known(4L), usage.outputTokens)
        assertEquals(Knowledge.Known(3L), usage.cachedInputTokens)
        assertTrue(usage.billedAmount is Knowledge.Unknown)
        assertTrue(complete.finishedAt is Knowledge.Known)
        await { text("provider-generation-reply") == complete.rawContent }
        compose.onNodeWithTag("provider-generation-consent").assertIsOff()
        compose.onNodeWithTag("provider-generation-send").assertIsNotEnabled()

        compose.activityRule.scenario.recreate()
        // The Activity ViewModel retains the completed dialog across recreation, without dispatch intent.
        await { exists("provider-generation-prompt") && exists("provider-generation-reply") }
        assertEquals(capturedPrompt, text("provider-generation-prompt"))
        assertEquals(complete.rawContent, text("provider-generation-reply"))
        assertEquals(compose.activity.getString(com.architectpsg.commandlibrary.feature.shell.R.string.provider_generation_status,
            TurnStatus.COMPLETE.name), text("provider-generation-status"))
        compose.onNodeWithTag("provider-generation-consent").assertIsOff()
        compose.onNodeWithTag("provider-generation-send").assertIsNotEnabled()
        assertEquals(1, network.attempts.get())
        assertEquals(1, network.closes.get())
        compose.onNodeWithText(compose.activity.getString(com.architectpsg.commandlibrary.feature.shell.R.string.shell_action_dismiss))
            .performScrollTo().performClick()
        await { !exists("provider-generation-prompt") }
        overview()
        click("deck-library-overview", "deck-ai-history")
        click("ai-history-list", "ai-history-conversation-${complete.conversationId.value}")
        click("ai-history-list", "ai-history-load-turns")
        click("ai-history-list", "ai-history-turn-${complete.id.value}")
        scroll("ai-history-list", "ai-history-reply-0")
        assertEquals(complete.rawContent, text("ai-history-reply-0"))
        listOf(com.architectpsg.commandlibrary.feature.shell.R.string.ai_history_input_tokens to 12L,
            com.architectpsg.commandlibrary.feature.shell.R.string.ai_history_output_tokens to 4L,
            com.architectpsg.commandlibrary.feature.shell.R.string.ai_history_cached_tokens to 3L).forEach { (resource, count) ->
            val expected = compose.activity.getString(resource, count)
            compose.onNodeWithTag("ai-history-list").performScrollToNode(hasText(expected))
            compose.onNodeWithText(expected).assertExists()
        }
        click("ai-history-list", "ai-history-toggle-prompt")
        scroll("ai-history-list", "ai-history-prompt-0")
        assertEquals(capturedPrompt, text("ai-history-prompt-0"))
        runBlocking { independent { assertEquals(complete, onlyTurn(it)) } }
        assertEquals(1, network.attempts.get())
        assertEquals(1, network.closes.get())
        assertEquals(saved, runBlocking { ok(checkNotNull(ports.providerProfiles).load(saved.profile.id, token)) })
        assertEquals(deck, runBlocking { ok(checkNotNull(ports.localDecks).load(deck.id, token)) })
    }
}
