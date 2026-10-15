package com.architectpsg.commandlibrary.data.storage.ai

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.ai.codec.AiHistoryPayloadCodec
import com.architectpsg.commandlibrary.feature.ai.LocalOfflineDeckPrompts
import com.architectpsg.commandlibrary.feature.decks.DeckSnapshotValidator
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

class AiHistoryPayloadCodecTest {
    private val codec = AiHistoryPayloadCodec()
    private val unknown = Knowledge.Unknown("Not supplied 未知", SnapshotList.of(Evidence("Original evidence", ResourceUri("https://example.test/e"), UtcTimestamp(42, 9))))
    private val time = UtcTimestamp(123, 456)
    private val hash = ContentHash("SHA-256", "a".repeat(64))
    private val snapshot = SnapshotReference(ProviderId("scryfall"), GenerationId("generation"), Revision(0), LanguageTag("ja"))
    private val prompt = PromptArtifact(OfflineLabMode.CHAT.templateId, 17, DeckId(ProviderId("local"), "deck"), Revision(0),
        "Exact 日本語 العربية café e\u0301 🐉\n", SnapshotList.of(PromptBinding("deck", "文脈 🐉")), hash, time)
    private fun turn() = Turn(EntityId("turn"), EntityId("conversation"), Revision(1), 1, HistoryTurnSource.PROVIDER_GENERATION,
        prompt, HistoryPromptProvenance(hash, true, snapshot), OfflineLabMode.CHAT, EntityId("profile"), "model", EntityId("retry"),
        EntityId("epoch"), TurnStatus.COMPLETE, "Raw [[reply]] 🐉\n", unknown, time, Knowledge.Known(UtcTimestamp(1)))
    private fun metadata(t: Turn) = HistoryTurnMetadata(t.id, t.conversationId, t.revision, t.ordinal, t.source, t.ownerEpoch,
        t.status, t.startedAt, t.finishedAt)
    private fun bytes(t: Turn = turn()) = (codec.encodeTurn(t) as Outcome.Success).value
    private fun roundtrip(t: Turn) { assertEquals(t, (codec.decodeTurn(bytes(t), metadata(t)) as Outcome.Success).value) }
    private fun invalid(t: Turn) { assertEquals(FailureCode.INVALID_INPUT, (codec.encodeTurn(t) as Outcome.Failure).error.code) }
    private fun corrupt(b: ByteArray, m: HistoryTurnMetadata = metadata(turn())) {
        val failure = codec.decodeTurn(SnapshotBytes(b), m) as Outcome.Failure
        assertEquals(FailureCode.CORRUPT_DATA, failure.error.code)
        assertEquals("Invalid history payload", failure.error.message)
    }

    @Test fun exactContentKnowledgeAndNativeDecimalSurvive() {
        roundtrip(turn())
        roundtrip(turn().copy(usage = Knowledge.Absent("No usage")))
        val amount = DecimalAmount(BigDecimal("123456789012345678901234567890.00000000000000000010"),
            DecimalUnit.Native(ProviderId("provider"), "credits 原"))
        roundtrip(turn().copy(usage = Knowledge.Known(Usage(Knowledge.Known(0), Knowledge.Absent("Unavailable"),
            Knowledge.Known(900), Knowledge.Known(amount), unknown))))
        roundtrip(turn().copy(usage = Knowledge.Known(Usage(unknown, Knowledge.Known(0), Knowledge.Absent("Unreported"),
            Knowledge.Known(DecimalAmount.exact("0.0000", DecimalUnit.Fiat("USD"))), Knowledge.Known("request")))))
    }

    @Test fun lifecycleMetadataNeverChangesContentBytes() {
        val streaming = turn().copy(status = TurnStatus.STREAMING, finishedAt = Knowledge.Unknown(AiHistoryLimits.UNFINISHED_REASON, SnapshotList.empty()))
        val recovered = streaming.copy(status = TurnStatus.INTERRUPTED, revision = Revision(9), ordinal = 2,
            ownerEpoch = EntityId("retained-owner"), startedAt = UtcTimestamp(900), finishedAt = Knowledge.Known(UtcTimestamp(-900)))
        assertEquals(bytes(streaming), bytes(recovered))
        assertEquals(recovered, (codec.decodeTurn(bytes(streaming), metadata(recovered)) as Outcome.Success).value)
        corrupt(bytes().copyBytes(), metadata(turn()).copy(id = EntityId("different")))
        corrupt(bytes().copyBytes(), metadata(turn()).copy(source = HistoryTurnSource.OFFLINE_CAPTURE))
        corrupt(bytes().copyBytes(), metadata(turn()).copy(revision = Revision(0)))
        invalid(streaming.copy(finishedAt = Knowledge.Unknown("Different reason", SnapshotList.empty())))
        invalid(turn().copy(ownerEpoch = null))
        invalid(turn().copy(prompt = prompt.copy(templateId = OfflineLabMode.ROAST.templateId)))
    }

    @Test fun pendingRequiresEmptyContentAndUnknownUsage() {
        val pending = turn().copy(status = TurnStatus.PENDING, rawContent = "",
            finishedAt = Knowledge.Unknown(AiHistoryLimits.UNFINISHED_REASON, SnapshotList.empty()))
        roundtrip(pending)
        invalid(pending.copy(rawContent = "Partial reply"))
        invalid(pending.copy(usage = Knowledge.Absent("No usage")))
        val knownUsage = Knowledge.Known(Usage(unknown, unknown, unknown, unknown, unknown))
        invalid(pending.copy(usage = knownUsage))
        // A frame valid for STREAMING must also reject forged noncanonical PENDING metadata.
        val streaming = pending.copy(status = TurnStatus.STREAMING, rawContent = "Partial reply")
        corrupt(bytes(streaming).copyBytes(), metadata(pending))
        corrupt(bytes(pending.copy(status = TurnStatus.STREAMING, usage = knownUsage)).copyBytes(), metadata(pending))
        // Canonical recovery changes metadata only and preserves unknown usage and empty pending bytes.
        val interrupted = pending.copy(status = TurnStatus.INTERRUPTED, revision = Revision(2), finishedAt = Knowledge.Known(time))
        assertEquals(bytes(pending), bytes(interrupted))
        assertEquals(interrupted, (codec.decodeTurn(bytes(pending), metadata(interrupted)) as Outcome.Success).value)
    }

    @Test fun malformedFramesAndPreallocationLengthsAreRejected() {
        val good = bytes().copyBytes()
        for (size in listOf(0, 7, good.size - 1)) corrupt(good.copyOf(size))
        corrupt(good + byteArrayOf(0))
        val sourceOffset = good.toString(Charsets.ISO_8859_1).indexOf("PROVIDER_GENERATION")
        assertTrue(sourceOffset >= 0)
        corrupt(good.copyOf().also { it[sourceOffset] = '!'.code.toByte() })
        corrupt(good.copyOf().also { ByteBuffer.wrap(it).putInt(8, Int.MAX_VALUE) })
        // First identity's UTF8 bytes begin after magic/version/length.
        corrupt(good.copyOf().also { it[12] = 0xc0.toByte(); it[13] = 0xaf.toByte() })
        corrupt(good.copyOf().also { ByteBuffer.wrap(it).putInt(4, 0) })
        val future = good.copyOf().also { ByteBuffer.wrap(it).putInt(4, 2) }
        assertEquals(UnavailableReason.UNSUPPORTED, (codec.decodeTurn(SnapshotBytes(future), metadata(turn())) as Outcome.Unavailable).reason)
        corrupt(ByteArray(AiHistoryLimits.MAX_PAYLOAD_BYTES + 1))
    }

    @Test fun invalidInputBudgetsUnicodeHashesAndCountsAreRejected() {
        invalid(turn().copy(rawContent = "\uD800"))
        invalid(turn().copy(rawContent = "🐉".repeat(AiHistoryLimits.MAX_REPLY_BYTES / 4 + 1)))
        invalid(turn().copy(prompt = prompt.copy(rendered = "x".repeat(AiHistoryLimits.MAX_PROMPT_BYTES + 1))))
        invalid(turn().copy(prompt = prompt.copy(bindings = SnapshotList.copyOf((1..33).map { PromptBinding("b$it", "x") }))))
        val half = "x".repeat(AiHistoryLimits.MAX_PROMPT_BYTES / 2 + 1)
        invalid(turn().copy(prompt = prompt.copy(bindings = SnapshotList.of(PromptBinding("a", half), PromptBinding("b", half)))))
        val full = "x".repeat(AiHistoryLimits.MAX_PROMPT_BYTES)
        invalid(turn().copy(prompt = prompt.copy(rendered = full, bindings = SnapshotList.of(PromptBinding("a", full)))))
        invalid(turn().copy(provenance = turn().provenance.copy(effectiveTemplateHash = ContentHash("SHA-256", "A".repeat(64)))))
        invalid(turn().copy(usage = Knowledge.Known(Usage(Knowledge.Known(-1), unknown, unknown, unknown, unknown))))
        invalid(turn().copy(usage = Knowledge.Unknown("x", SnapshotList.copyOf((1..33).map { Evidence("e", null, null) }))))
    }

    @Test fun largeActualOfflineEngineArtifactRoundTripsWithoutCatalogIo() = runTest {
        val identity = CardIdentity(unknown, unknown, null, Knowledge.Known("日本語 العربية café 🐉"), LanguageTag("ja"))
        val deck = DeckSnapshot(prompt.deckId, "Deck 日本語", unknown, "private description", unknown, SnapshotList.empty(), time, time,
            Revision(0), SnapshotList.copyOf((1..500).map {
                DeckEntry(EntityId("entry$it"), identity, 1, BoardRole.MAIN, unknown, "notes 文 ".repeat(50), SnapshotList.empty(), SnapshotList.empty())
            }), unknown, null, SnapshotList.empty(), SnapshotList.empty(), "private primer")
        val query = object : LocalCardQuery {
            override suspend fun local(query: QueryAst, context: SearchContext, page: PageRequest, cancel: CancelToken): Outcome<Page<CardRecord>> =
                error("No catalog lookup expected without snapshot")
        }
        val clock = object : Clock { override fun utcNow() = time; override fun monotonicNow() = Duration.ZERO }
        val engine = LocalOfflineDeckPrompts(query, clock, DeckSnapshotValidator::validate)
        val body = "質問 {{request}}\n{{deck}}\n制約 {{constraints}}"
        val cancel = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() {} }
        val result = (engine.render(OfflinePromptRequest(deck, OfflineLabMode.CHAT, RoastIntensity.MEDIUM, "What next? 🐉", "Keep exact", true,
            null, body), cancel) as Outcome.Success).value
        assertTrue(result.artifact.rendered.toByteArray(Charsets.UTF_8).size > 256 * 1024)
        val effective = ContentHash("SHA-256", MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
        val captured = turn().copy(source = HistoryTurnSource.OFFLINE_CAPTURE, prompt = result.artifact,
            provenance = HistoryPromptProvenance(effective, result.overrideUsed, result.snapshot), providerProfileId = null, modelId = null,
            retryOf = null, ownerEpoch = null)
        roundtrip(captured)
    }
}
