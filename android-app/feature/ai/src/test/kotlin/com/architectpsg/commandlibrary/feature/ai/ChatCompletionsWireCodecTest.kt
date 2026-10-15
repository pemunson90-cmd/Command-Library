package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.math.BigDecimal
import java.util.concurrent.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Synthetic protocol fixtures only: no authentic provider payloads, network or eligibility claims. */
class ChatCompletionsWireCodecTest {
    private val codec = ChatCompletionsWireCodec()
    private val requestId = EntityId("local-request")
    private val cancel = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private fun request(text: String = "synthetic prompt") = GenerationRequest(requestId,
        SnapshotList.of(GenerationMessage(MessageRole.USER, text, null)), null, null, null, null, SnapshotList.empty())
    private fun metadata(status: Int = 200, type: String = "text/event-stream") = HttpMetadata(status,
        SnapshotList.of(HttpHeader("Content-Type", type)), ResourceUri("https://example.test/v1/chat/completions"),
        UtcTimestamp(0), Knowledge.Unknown("Not reported", SnapshotList.empty()),
        Knowledge.Unknown("Not reported", SnapshotList.empty()))
    private fun body(request: GenerationRequest): String = (codec.encode(ProviderProfileFixtures.profile(), request, cancel)
        as Outcome.Success).value.body!!.copyBytes().toString(Charsets.UTF_8)
    private fun event(json: String): String = "data: $json\n\n"
    private fun delta(text: String, reason: String? = null): String = event("""{"id":"synthetic-provider-id","choices":[{"index":0,"delta":{"content":${JsonPrimitive(text)}},"finish_reason":${reason?.let { "\"$it\"" } ?: "null"}}]}""")
    private fun finish(reason: String = "stop") = event("""{"choices":[{"index":0,"delta":{},"finish_reason":"$reason"}]}""")
    private val done = "data: [DONE]\n\n"

    private fun chunks(bytes: ByteArray, cuts: List<Int> = emptyList(), http: HttpMetadata = metadata()): Flow<Outcome<ByteChunk>> = flow {
        val ends = (cuts + bytes.size).distinct().sorted()
        var start = 0
        for ((sequence, end) in ends.withIndex()) {
            emit(Outcome.Success(ByteChunk(sequence.toLong(), start.toLong(), SnapshotBytes(bytes.copyOfRange(start, end)),
                if (sequence == 0) http else null, end == bytes.size)))
            start = end
        }
    }
    private suspend fun decode(text: String): List<Outcome<TokenChunk>> = codec.decode(requestId, chunks(text.toByteArray()), cancel).toList()
    private fun tokens(values: List<Outcome<TokenChunk>>): List<TokenChunk> = values.map { (it as Outcome.Success).value }
    private fun failure(values: List<Outcome<TokenChunk>>) { assertTrue(values.last() is Outcome.Failure) }
    private fun unsupported(values: List<Outcome<TokenChunk>>) {
        assertEquals(UnavailableReason.UNSUPPORTED, (values.last() as Outcome.Unavailable).reason)
    }

    @Test fun encoderOmitsUnknownOptionsAndUsesOnlyStaticHeaders() {
        val encoded = (codec.encode(ProviderProfileFixtures.profile(), request("quotes \" \\ 日本語\n"), cancel) as Outcome.Success).value
        assertEquals(HttpMethod.POST, encoded.method)
        assertEquals("https://example.test/v1/chat/completions", encoded.uri.value)
        assertEquals(listOf(HttpHeader("Content-Type", "application/json"), HttpHeader("Accept", "text/event-stream")), encoded.headers)
        val json = JsonParser.parseString(encoded.body!!.copyBytes().toString(Charsets.UTF_8)).asJsonObject
        assertEquals(setOf("model", "stream", "messages"), json.keySet())
        assertEquals("manual-model", json.get("model").asString)
        assertTrue(json.get("stream").asBoolean)
        assertEquals("quotes \" \\ 日本語\n", json.getAsJsonArray("messages")[0].asJsonObject.get("content").asString)
        val options = JsonParser.parseString(body(request().copy(maxTokens = 7, temperature = BigDecimal("0.25"),
            topP = BigDecimal("0.9"), seed = 0, stopSequences = SnapshotList.of("END")))).asJsonObject
        assertEquals(7, options.get("max_tokens").asInt)
        assertEquals(BigDecimal("0.25"), options.get("temperature").asBigDecimal)
        assertEquals(BigDecimal("0.9"), options.get("top_p").asBigDecimal)
        assertEquals(0L, options.get("seed").asLong)
        assertEquals("END", options.getAsJsonArray("stop")[0].asString)
    }

    @Test fun encoderRejectsUnsupportedCapabilitiesAndInvalidUnicodeNumbersAndCounts() {
        val profile = ProviderProfileFixtures.profile()
        listOf(profile.copy(dialect = ProviderDialect.ANTHROPIC), profile.copy(apiVersion = Knowledge.Known("version")),
            profile.copy(extraHeaders = SnapshotList.of(HttpHeader("X-Synthetic", "value"))))
            .forEach { assertEquals(UnavailableReason.UNSUPPORTED, (codec.encode(it, request(), cancel) as Outcome.Unavailable).reason) }
        listOf(request().copy(messages = SnapshotList.of(GenerationMessage(MessageRole.TOOL, "text", null))),
            request().copy(messages = SnapshotList.of(GenerationMessage(MessageRole.USER, "text", "tool-id"))))
            .forEach { assertTrue(codec.encode(profile, it, cancel) is Outcome.Unavailable) }
        listOf(request("bad\uD800"), request().copy(temperature = BigDecimal("1e-33")),
            request().copy(temperature = BigDecimal("1e33")), request().copy(temperature = BigDecimal("1.123456789012345678901234567890123")),
            request().copy(messages = SnapshotList.empty()),
            request().copy(messages = SnapshotList.copyOf(List(65) { GenerationMessage(MessageRole.USER, "x", null) })),
            request().copy(stopSequences = SnapshotList.copyOf(List(33) { "stop" })),
            request().copy(stopSequences = SnapshotList.of("x".repeat(4097))))
            .forEach { assertEquals(FailureCode.INVALID_INPUT, (codec.encode(profile, it, cancel) as Outcome.Failure).error.code) }
    }

    @Test fun encoderHasSeparateExactContentAndEscapedBodyBudgets() {
        assertTrue(codec.encode(ProviderProfileFixtures.profile(), request("a".repeat(ProviderTextLimits.MAX_MESSAGE_BYTES)), cancel) is Outcome.Success)
        assertTrue(codec.encode(ProviderProfileFixtures.profile(), request("a".repeat(ProviderTextLimits.MAX_MESSAGE_BYTES + 1)), cancel) is Outcome.Failure)
        val overhead = body(request("")).toByteArray().size
        val available = ProviderTextLimits.MAX_REQUEST_BYTES - overhead
        val text = "\u0001".repeat(available / 6) + "a".repeat(available % 6)
        assertEquals(ProviderTextLimits.MAX_REQUEST_BYTES, body(request(text)).toByteArray().size)
        assertTrue(codec.encode(ProviderProfileFixtures.profile(), request(text + "a"), cancel) is Outcome.Failure)
        assertTrue(codec.encode(ProviderProfileFixtures.profile(), request("\n".repeat(ProviderTextLimits.MAX_MESSAGE_BYTES)), cancel) is Outcome.Failure)
    }

    @Test fun referenceStreamSurvivesByteUtf8AndCrLfSplitsWithTrailingUsage() = runBlocking {
        val reference = "\uFEFF: synthetic comment\r\nretry: 123\rid: ignored\n\n" +
            "data: {\r\ndata: \"id\":\"synthetic-provider-id\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"日😀\"},\"finish_reason\":null}]}\r\n\r\n" +
            finish() + event("""{"choices":[],"usage":{"prompt_tokens":0,"completion_tokens":2,"total_tokens":2,"prompt_tokens_details":{"cached_tokens":5}}}""") + done
        val bytes = reference.toByteArray()
        val expected = tokens(codec.decode(requestId, chunks(bytes), cancel).toList())
        assertEquals(listOf("日😀", ""), expected.map { it.text })
        assertEquals(listOf(0L, 1L), expected.map { it.sequence })
        assertEquals(GenerationFinishReason.STOP, expected.last().finishReason)
        assertTrue(expected.all { it.requestId == requestId && it.tokenIds.isEmpty() })
        val usage = (expected.last().usage as Knowledge.Known).value
        assertEquals(Knowledge.Known(0L), usage.inputTokens)
        assertEquals(Knowledge.Known(2L), usage.outputTokens)
        assertEquals(Knowledge.Known(5L), usage.cachedInputTokens)
        assertEquals(Knowledge.Known("synthetic-provider-id"), usage.providerRequestId)
        assertTrue(usage.billedAmount is Knowledge.Unknown)
        val boundaries = bytes.indices.filter { it > 0 && (bytes[it] == 10.toByte() || bytes[it] == 13.toByte() ||
            bytes[it].toInt() and 0x80 != 0 || it == 1) }
        for (boundary in boundaries) {
            assertEquals("Synthetic split $boundary", expected,
                tokens(codec.decode(requestId, chunks(bytes, listOf(boundary)), cancel).toList()))
        }
        assertEquals(expected, tokens(codec.decode(requestId, chunks(bytes, (1 until bytes.size).toList()), cancel).toList()))
    }

    @Test fun terminalImmediatelyUnwindsUpstreamWithoutAwaitingCloseOrNextChunk() = runBlocking {
        var closed = false
        var advanced = false
        val upstream = flow<Outcome<ByteChunk>> {
            try {
                emit(Outcome.Success(ByteChunk(0, 0, SnapshotBytes((delta("prefix") + finish() + done + "invalid suffix").toByteArray()), metadata(), false)))
                advanced = true
                awaitCancellation()
            } finally { closed = true }
        }
        val result = tokens(withTimeout(2_000) { codec.decode(requestId, upstream, cancel).toList() })
        assertEquals(2, result.size)
        assertTrue(closed)
        assertFalse(advanced)
        assertEquals(1, result.count { it.finishReason != null })
    }

    @Test fun decoderRejectionUsesCancellationToUnwindTransportErrorHandlers() = runBlocking {
        var closed = false
        var translated = false
        val upstream = flow<Outcome<ByteChunk>> {
            try {
                emit(Outcome.Success(ByteChunk(0, 0, SnapshotBytes(event("{invalid}").toByteArray()), metadata(), false)))
                awaitCancellation()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                translated = true
                emit(Outcome.Unknown("Synthetic transport translation", SnapshotList.empty()))
            } finally { closed = true }
        }
        failure(withTimeout(2_000) { codec.decode(requestId, upstream, cancel).toList() })
        assertTrue(closed)
        assertFalse(translated)
    }

    @Test fun finishReasonAloneAndMissingFinishNeverClaimSuccess() = runBlocking {
        for (wire in listOf(delta("partial"), delta("partial") + finish(), delta("partial") + done)) {
            val result = decode(wire)
            assertEquals("partial", (result.first() as Outcome.Success).value.text)
            failure(result)
            assertFalse(result.filterIsInstance<Outcome.Success<TokenChunk>>().any { it.value.finishReason != null })
        }
        assertEquals(GenerationFinishReason.TOKEN_LIMIT, tokens(decode(finish("length") + done)).single().finishReason)
        assertEquals(GenerationFinishReason.FAILED, tokens(decode(finish("content_filter") + done)).single().finishReason)
        unsupported(decode(finish("tool_calls") + done))
        failure(decode(finish() + delta("late") + done))
    }

    @Test fun usagePreservesEarlierFieldsAndRejectsContradictionsWithoutInventingCost() = runBlocking {
        val first = event("""{"choices":[],"usage":{"prompt_tokens":0,"prompt_tokens_details":{"cached_tokens":9}}}""")
        val second = event("""{"choices":[],"usage":{"completion_tokens":0,"total_tokens":0,"price":1.5}}""")
        val usage = (tokens(decode(first + finish() + second + done)).single().usage as Knowledge.Known).value
        assertEquals(Knowledge.Known(0L), usage.inputTokens)
        assertEquals(Knowledge.Known(0L), usage.outputTokens)
        assertEquals(Knowledge.Known(9L), usage.cachedInputTokens)
        assertTrue(usage.providerRequestId is Knowledge.Unknown)
        assertTrue(usage.billedAmount is Knowledge.Unknown)
        assertTrue(tokens(decode(finish() + done)).single().usage is Knowledge.Unknown)
        for (usageJson in listOf("{\"prompt_tokens\":-1}", "{\"completion_tokens\":1.5}",
            "{\"prompt_tokens\":9223372036854775808}", "{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":2}")) {
            failure(decode(event("{\"choices\":[],\"usage\":$usageJson}") + finish() + done))
        }
        failure(decode(event("""{"choices":[],"usage":{"prompt_tokens":2}}""") + first + finish() + done))
        failure(decode(event("""{"id":"first","choices":[]}""") + event("""{"id":"second","choices":[]}""") + done))
    }

    @Test fun malformedAndUnsupportedEventsAreStaticAndPreserveReceivedPrefix() = runBlocking {
        val malformed = listOf("{\"choices\":[],\"choices\":[]}", "{\"choices\":[],\"id\":\"\\ud800\"}",
            "{\"choices\":[],\"unknown\":1e33}", "{\"choices\":[],\"unknown\":${"[".repeat(33)}0${"]".repeat(33)}}",
            "{\"choices\":[],\"unknown\":[${List(10_001) { "null" }.joinToString(",")} ]}",
            "{\"choices\":[]}", "{\"choices\":[{\"index\":1,\"delta\":{}}]}",
            "{\"error\":{\"message\":\"synthetic-sensitive-error\"}}", "{invalid}")
        for (json in malformed) {
            val result = decode(delta("prefix") + event(json) + done)
            assertEquals("prefix", (result.first() as Outcome.Success).value.text)
            failure(result)
            assertFalse((result.last() as Outcome.Failure).error.message.contains("synthetic-sensitive-error"))
        }
        for (field in listOf("\"tool_calls\":[{}]", "\"function_call\":{\"name\":\"x\"}",
            "\"refusal\":\"synthetic refusal\"", "\"content\":[]", "\"role\":\"tool\"")) {
            unsupported(decode(event("{\"choices\":[{\"index\":0,\"delta\":{$field}}]}") + done))
        }
        assertEquals(GenerationFinishReason.STOP, tokens(decode(event("""{"choices":[{"index":0,"delta":{"content":null,"tool_calls":[],"function_call":{},"refusal":""}}]}""") + finish() + done)).single().finishReason)
    }

    @Test fun validatesHttpMetadataTransportOrderAndTypedFailurePropagation() = runBlocking {
        val wire = (finish() + done).toByteArray()
        val httpFailure = codec.decode(requestId, chunks(wire, http = metadata(429)), cancel).toList().single() as Outcome.Failure
        assertEquals(FailureCode.TRANSPORT, httpFailure.error.code)
        assertEquals(429, httpFailure.error.httpFailure!!.statusCode)
        for (type in listOf("application/json", "text/event-stream;charset=latin-1", "text/event-stream;charset=utf-8;charset=utf-8")) {
            failure(codec.decode(requestId, chunks(wire, http = metadata(type = type)), cancel).toList())
        }
        assertEquals(GenerationFinishReason.STOP, tokens(codec.decode(requestId,
            chunks(wire, http = metadata(type = "Text/Event-Stream; Charset=\"UTF-8\"")), cancel).toList()).single().finishReason)
        val conflict = metadata().copy(headers = SnapshotList.of(HttpHeader("Content-Type", "text/event-stream"), HttpHeader("content-type", "application/json")))
        failure(codec.decode(requestId, chunks(wire, http = conflict), cancel).toList())
        for (chunk in listOf(ByteChunk(1, 0, SnapshotBytes(wire), metadata(), true),
            ByteChunk(0, 1, SnapshotBytes(wire), metadata(), true), ByteChunk(0, 0, SnapshotBytes(wire), null, true),
            ByteChunk(0, 0, SnapshotBytes(byteArrayOf()), metadata(), false))) {
            failure(codec.decode(requestId, flow { emit(Outcome.Success(chunk)) }, cancel).toList())
        }
        val prefix = delta("prefix").toByteArray()
        for (second in listOf(ByteChunk(2, prefix.size.toLong(), SnapshotBytes(wire), null, true),
            ByteChunk(1, prefix.size.toLong() + 1, SnapshotBytes(wire), null, true),
            ByteChunk(1, prefix.size.toLong(), SnapshotBytes(wire), metadata(type = "application/json"), true))) {
            failure(codec.decode(requestId, flow {
                emit(Outcome.Success(ByteChunk(0, 0, SnapshotBytes(prefix), metadata(), false)))
                emit(Outcome.Success(second))
            }, cancel).toList())
        }
        val safe = Outcome.Failure(DomainFailure(FailureCode.TRANSPORT, "Synthetic transport failure", SnapshotList.empty(),
            HttpFailureMetadata(503, Knowledge.Unknown("Not supplied", SnapshotList.empty()), Knowledge.Unknown("Not supplied", SnapshotList.empty()))))
        var advanced = false
        val result = codec.decode(requestId, flow { emit(safe); advanced = true }, cancel).toList()
        assertSame(safe, result.single())
        assertFalse(advanced)
    }

    @Test fun finalEmptyChunkMayRepeatMetadataButNeverMakesEofSuccessful() = runBlocking {
        val bytes = delta("prefix").toByteArray()
        val result = codec.decode(requestId, flow {
            emit(Outcome.Success(ByteChunk(0, 0, SnapshotBytes(bytes), metadata(), false)))
            emit(Outcome.Success(ByteChunk(1, bytes.size.toLong(), SnapshotBytes(byteArrayOf()), metadata(), true)))
        }, cancel).toList()
        assertEquals("prefix", (result.first() as Outcome.Success).value.text)
        failure(result)
    }

    @Test fun strictUtf8AndReplyEventAndWireLimitsRejectBeforeUnboundedGrowth() = runBlocking {
        val malformed = "data: ".toByteArray() + byteArrayOf(0xc3.toByte(), 0x28) + "\n\n".toByteArray()
        failure(codec.decode(requestId, chunks(malformed), cancel).toList())
        failure(codec.decode(requestId, chunks("data: ".toByteArray() + byteArrayOf(0xc3.toByte())), cancel).toList())
        val half = "x".repeat(ProviderTextLimits.MAX_REPLY_BYTES / 2)
        val exact = tokens(decode(delta(half) + delta(half) + finish() + done))
        assertEquals(ProviderTextLimits.MAX_REPLY_BYTES, exact.sumOf { it.text.toByteArray().size })
        val over = decode(delta(half) + delta(half) + delta("x") + finish() + done)
        failure(over)
        assertEquals(2, over.filterIsInstance<Outcome.Success<TokenChunk>>().size)
        failure(decode(":" + "x".repeat(ProviderTextLimits.MAX_EVENT_BYTES) + "\n\n" + finish() + done))
        // One giant transport chunk is rejected from size metadata before copying or parsing its bytes.
        failure(codec.decode(requestId, chunks(ByteArray(ProviderTextLimits.MAX_WIRE_RESPONSE_BYTES.toInt() + 1)), cancel).toList())
    }

    @Test fun cancellationPropagatesFromEncoderParserAndCollectorAndClosesUpstream() = runBlocking {
        val cancelled = object : CancelToken {
            override val isCancelled = true
            override fun throwIfCancelled(): Unit = throw CancellationException("Synthetic cancellation")
        }
        try { codec.encode(ProviderProfileFixtures.profile(), request(), cancelled); fail("Expected cancellation") }
        catch (expected: CancellationException) { /* Expected propagation. */ }
        try { codec.decode(requestId, chunks((finish() + done).toByteArray()), cancelled).toList(); fail("Expected cancellation") }
        catch (expected: CancellationException) { /* Expected propagation. */ }
        try { ChatCompletionsJson.parse("{\"choices\":[]}", cancelled); fail("Expected parser cancellation") }
        catch (expected: CancellationException) { /* Cancellation is an IllegalStateException subtype. */ }
        var closed = false
        val upstream = flow<Outcome<ByteChunk>> {
            try {
                emit(Outcome.Success(ByteChunk(0, 0, SnapshotBytes(delta("prefix").toByteArray()), metadata(), false)))
                awaitCancellation()
            } finally { closed = true }
        }
        try {
            codec.decode(requestId, upstream, cancel).collect { throw CancellationException("Synthetic collector cancellation") }
            fail("Expected cancellation")
        } catch (expected: CancellationException) { assertTrue(closed) }
    }
}
