package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.math.BigDecimal
import java.util.concurrent.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Synthetic fixtures derived from pinned SDK declarations, never authentic wire/API acceptance. */
class AnthropicMessagesWireCodecTest {
    private val codec = AnthropicMessagesWireCodec()
    private val requestId = EntityId("local-request")
    private val cancel = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private fun profile() = ProviderProfileFixtures.profile().copy(dialect = ProviderDialect.ANTHROPIC,
        apiVersion = Knowledge.Known("2023-06-01"))
    private fun request(text: String = "synthetic prompt") = GenerationRequest(requestId,
        SnapshotList.of(GenerationMessage(MessageRole.USER, text, null)), 7, null, null, null, SnapshotList.empty())
    private fun metadata(status: Int = 200, type: String = "text/event-stream") = HttpMetadata(status,
        SnapshotList.of(HttpHeader("Content-Type", type)), ResourceUri("https://example.test/v1/messages"),
        UtcTimestamp(0), Knowledge.Unknown("Not reported", SnapshotList.empty()),
        Knowledge.Unknown("Not reported", SnapshotList.empty()))
    private fun body(request: GenerationRequest): String = (codec.encode(profile(), request, cancel)
        as Outcome.Success).value.body!!.copyBytes().toString(Charsets.UTF_8)
    private fun event(type: String, fields: String = "") = "event: $type\ndata: {\"type\":\"$type\"$fields}\n\n"
    private fun start(usage: String = "{}") = event("message_start", """, "message":{"id":"synthetic-provider-id","type":"message","role":"assistant","model":"manual-model","content":[],"stop_reason":null,"stop_sequence":null,"usage":$usage}""")
    private fun block(text: String = "", index: Int = 0) = event("content_block_start",
        """, "index":$index,"content_block":{"type":"text","text":${JsonPrimitive(text)}}""")
    private fun delta(text: String, index: Int = 0) = event("content_block_delta",
        """, "index":$index,"delta":{"type":"text_delta","text":${JsonPrimitive(text)}}""")
    private fun blockStop(index: Int = 0) = event("content_block_stop", ",\"index\":$index")
    private fun finish(reason: String = "end_turn", usage: String = "{}") = event("message_delta",
        """, "delta":{"stop_reason":"$reason","stop_sequence":null},"usage":$usage""")
    private fun usage(value: String) = event("message_delta", """, "delta":{},"usage":$value""")
    private val stop = event("message_stop")
    private fun complete(text: String = "prefix") = start() + block() + delta(text) + blockStop() + finish() + stop
    private fun chunks(bytes: ByteArray, cuts: List<Int> = emptyList(), http: HttpMetadata = metadata()): Flow<Outcome<ByteChunk>> = flow {
        var offset = 0
        for ((sequence, end) in (cuts + bytes.size).distinct().sorted().withIndex()) {
            emit(Outcome.Success(ByteChunk(sequence.toLong(), offset.toLong(), SnapshotBytes(bytes.copyOfRange(offset, end)),
                if (sequence == 0) http else null, end == bytes.size)))
            offset = end
        }
    }
    private suspend fun decode(text: String) = codec.decode(requestId, chunks(text.toByteArray()), cancel).toList()
    private fun tokens(values: List<Outcome<TokenChunk>>) = values.map { (it as Outcome.Success).value }
    private fun failure(values: List<Outcome<TokenChunk>>) { assertTrue("Expected failure: $values", values.last() is Outcome.Failure) }
    private fun unsupported(values: List<Outcome<TokenChunk>>) {
        assertEquals(UnavailableReason.UNSUPPORTED, (values.last() as Outcome.Unavailable).reason)
    }
    private fun finalUsage(values: List<Outcome<TokenChunk>>) = (tokens(values).last().usage as Knowledge.Known).value

    @Test fun encoderUsesExplicitOptionsStaticHeadersAndTopLevelSystem() {
        val input = request("quotes \" \\ 日本語\n").copy(messages = SnapshotList.of(
            GenerationMessage(MessageRole.SYSTEM, "system", null), GenerationMessage(MessageRole.USER, "user", null),
            GenerationMessage(MessageRole.ASSISTANT, "assistant", null)), stopSequences = SnapshotList.of("END"))
        val wire = (codec.encode(profile(), input, cancel) as Outcome.Success).value
        assertEquals(HttpMethod.POST, wire.method)
        assertEquals("https://example.test/v1/messages", wire.uri.value)
        assertEquals(listOf(HttpHeader("Content-Type", "application/json"), HttpHeader("Accept", "text/event-stream"),
            HttpHeader("anthropic-version", "2023-06-01")), wire.headers)
        val json = JsonParser.parseString(wire.body!!.copyBytes().toString(Charsets.UTF_8)).asJsonObject
        assertEquals(setOf("model", "stream", "messages", "max_tokens", "system", "stop_sequences"), json.keySet())
        assertEquals("manual-model", json.get("model").asString)
        assertTrue(json.get("stream").asBoolean)
        assertEquals(7, json.get("max_tokens").asInt)
        assertEquals("system", json.get("system").asString)
        assertEquals(listOf("user", "assistant"), json.getAsJsonArray("messages").map { it.asJsonObject.get("role").asString })
        assertEquals("END", json.getAsJsonArray("stop_sequences")[0].asString)
        val text = "quotes \" \\ 日本語\n😀"
        assertEquals(text, JsonParser.parseString(body(request(text))).asJsonObject.getAsJsonArray("messages")[0].asJsonObject.get("content").asString)
        assertEquals(setOf("model", "stream", "messages", "max_tokens"), JsonParser.parseString(body(request())).asJsonObject.keySet())
    }

    @Test fun encoderRejectsMissingInvalidVersionsNoncanonicalProfilesAndUnsupportedSettings() {
        val p = profile()
        for (version in listOf("2023-6-01", "2023-02-29", "2023-06-01 ", "2023-06-01\t", "0000-01-01")) {
            assertTrue(codec.encode(p.copy(apiVersion = Knowledge.Known(version)), request(), cancel) is Outcome.Failure)
        }
        assertTrue(codec.encode(p.copy(apiVersion = Knowledge.Unknown("Not supplied", SnapshotList.empty())), request(), cancel) is Outcome.Failure)
        assertTrue(codec.encode(p.copy(apiVersion = Knowledge.Absent("Not supplied")), request(), cancel) is Outcome.Failure)
        assertTrue(codec.encode(p.copy(baseUri = ResourceUri("HTTPS://EXAMPLE.TEST:443/v1/")), request(), cancel) is Outcome.Failure)
        assertTrue(codec.encode(p, request().copy(maxTokens = null), cancel) is Outcome.Failure)
        for (input in listOf(request().copy(temperature = BigDecimal("0.25")), request().copy(topP = BigDecimal("0.9")),
            request().copy(seed = 0), request().copy(messages = SnapshotList.of(GenerationMessage(MessageRole.TOOL, "x", null))),
            request().copy(messages = SnapshotList.of(GenerationMessage(MessageRole.USER, "x", "tool-id"))),
            request().copy(messages = SnapshotList.of(GenerationMessage(MessageRole.USER, "x", null), GenerationMessage(MessageRole.SYSTEM, "x", null))),
            request().copy(messages = SnapshotList.of(GenerationMessage(MessageRole.SYSTEM, "x", null), GenerationMessage(MessageRole.SYSTEM, "x", null))))) {
            assertTrue(codec.encode(p, input, cancel) is Outcome.Unavailable)
        }
        assertTrue(codec.encode(p.copy(dialect = ProviderDialect.OPENAI_CHAT), request(), cancel) is Outcome.Unavailable)
        assertTrue(codec.encode(p.copy(extraHeaders = SnapshotList.of(HttpHeader("X-Extra", "value"))), request(), cancel) is Outcome.Unavailable)
    }

    @Test fun encoderBudgetsContentAndEscapedWireSeparately() {
        assertTrue(codec.encode(profile(), request("a".repeat(ProviderTextLimits.MAX_MESSAGE_BYTES)), cancel) is Outcome.Success)
        for (input in listOf(request("bad\uD800"), request("a".repeat(ProviderTextLimits.MAX_MESSAGE_BYTES + 1)),
            request().copy(messages = SnapshotList.empty()), request().copy(messages = SnapshotList.of(GenerationMessage(MessageRole.SYSTEM, "x", null))),
            request().copy(messages = SnapshotList.copyOf(List(65) { GenerationMessage(MessageRole.USER, "x", null) })),
            request().copy(stopSequences = SnapshotList.copyOf(List(33) { "stop" })), request().copy(stopSequences = SnapshotList.of("")),
            request().copy(stopSequences = SnapshotList.of("x".repeat(4097))))) {
            assertEquals(FailureCode.INVALID_INPUT, (codec.encode(profile(), input, cancel) as Outcome.Failure).error.code)
        }
        val available = ProviderTextLimits.MAX_REQUEST_BYTES - body(request("")).toByteArray().size
        val text = "\u0001".repeat(available / 6) + "a".repeat(available % 6)
        assertEquals(ProviderTextLimits.MAX_REQUEST_BYTES, body(request(text)).toByteArray().size)
        assertTrue(codec.encode(profile(), request(text + "a"), cancel) is Outcome.Failure)
    }

    @Test fun referenceStreamPreservesStartTextMultipleBlocksAndArbitraryByteSplits() = runBlocking {
        val reference = "\uFEFF: comment\r\nretry: 123\rid: ignored\n\n" + start("""{"input_tokens":0,"cache_creation_input_tokens":2,"cache_read_input_tokens":3,"output_tokens":0}""") +
            event("ping") + block("日😀") + delta("tail") + blockStop() + event("ping") + block("second", 1) + blockStop(1) +
            finish(usage = """{"output_tokens":7}""") + event("ping") + stop
        val multiline = reference.replace("data: {\"type\":\"message_start\"", "data: {\r\ndata: \"type\":\"message_start\"")
            .replace("\n", "\r\n").replace("\r\r\n", "\r\n")
        val bytes = multiline.toByteArray()
        val expected = tokens(codec.decode(requestId, chunks(bytes), cancel).toList())
        assertEquals(listOf("日😀", "tail", "second", ""), expected.map { it.text })
        assertEquals(listOf(0L, 1L, 2L, 3L), expected.map { it.sequence })
        assertTrue(expected.all { it.requestId == requestId && it.tokenIds.isEmpty() })
        assertEquals(GenerationFinishReason.STOP, expected.last().finishReason)
        val usage = (expected.last().usage as Knowledge.Known).value
        assertEquals(Knowledge.Known(5L), usage.inputTokens)
        assertEquals(Knowledge.Known(3L), usage.cachedInputTokens)
        assertEquals(Knowledge.Known(7L), usage.outputTokens)
        assertEquals(Knowledge.Known("synthetic-provider-id"), usage.providerRequestId)
        assertTrue(usage.billedAmount is Knowledge.Unknown)
        val cuts = bytes.indices.filter { it > 0 && (bytes[it] == 10.toByte() || bytes[it] == 13.toByte() || bytes[it].toInt() and 0x80 != 0 || it == 1) }
        for (cut in cuts) assertEquals("Split $cut", expected, tokens(codec.decode(requestId, chunks(bytes, listOf(cut)), cancel).toList()))
        assertEquals(expected, tokens(codec.decode(requestId, chunks(bytes, (1 until bytes.size).toList()), cancel).toList()))
    }

    @Test fun cumulativeUsageRetainsNullOmissionZeroAndNeverGuessesAbsentComponents() = runBlocking {
        val initial = """{"input_tokens":0,"cache_creation_input_tokens":0,"cache_read_input_tokens":0,"output_tokens":0}"""
        val result = finalUsage(decode(start(initial) + finish(usage = """{"input_tokens":null,"output_tokens":2}""") +
            usage("""{"output_tokens":null,"cache_read_input_tokens":null}""") + stop))
        assertEquals(Knowledge.Known(0L), result.inputTokens)
        assertEquals(Knowledge.Known(0L), result.cachedInputTokens)
        assertEquals(Knowledge.Known(2L), result.outputTokens)
        val missing = finalUsage(decode(start("""{"input_tokens":4,"cache_read_input_tokens":3}""") + finish() + stop))
        assertTrue(missing.inputTokens is Knowledge.Unknown)
        assertTrue(missing.outputTokens is Knowledge.Unknown)
        assertEquals(Knowledge.Known(3L), missing.cachedInputTokens)
        val overflow = finalUsage(decode(start("""{"input_tokens":9223372036854775807,"cache_creation_input_tokens":1,"cache_read_input_tokens":0}""") + finish() + stop))
        assertTrue(overflow.inputTokens is Knowledge.Unknown)
        for (invalid in listOf("{\"input_tokens\":-1}", "{\"output_tokens\":1.5}", "{\"cache_read_input_tokens\":9223372036854775808}", "{\"output_tokens\":1e33}")) {
            failure(decode(start() + finish(usage = invalid) + stop))
        }
        for (counter in listOf("input_tokens", "output_tokens", "cache_creation_input_tokens", "cache_read_input_tokens")) {
            failure(decode(start("{\"$counter\":2}") + finish(usage = "{\"$counter\":1}") + stop))
        }
    }

    @Test fun terminalRequiresStopAndFinishWithEveryBlockClosed() = runBlocking {
        for (wire in listOf(start(), start() + block() + delta("partial"), start() + finish(), start() + stop,
            start() + block() + finish() + stop, start() + finish() + delta("late") + stop)) {
            val result = decode(wire)
            failure(result)
            assertFalse(result.filterIsInstance<Outcome.Success<TokenChunk>>().any { it.value.finishReason != null })
        }
        for ((reason, expected) in listOf("end_turn" to GenerationFinishReason.STOP, "stop_sequence" to GenerationFinishReason.STOP,
            "max_tokens" to GenerationFinishReason.TOKEN_LIMIT, "model_context_window_exceeded" to GenerationFinishReason.TOKEN_LIMIT,
            "refusal" to GenerationFinishReason.FAILED)) {
            assertEquals(expected, tokens(decode(start() + finish(reason) + stop)).single().finishReason)
        }
        for (reason in listOf("tool_use", "pause_turn", "unknown")) unsupported(decode(start() + finish(reason) + stop))
        failure(decode(start() + finish() + finish("max_tokens") + stop))
        val sequence = event("message_delta", """, "delta":{"stop_reason":"stop_sequence","stop_sequence":"END"},"usage":{}""")
        assertEquals(GenerationFinishReason.STOP, tokens(decode(start() + sequence + stop)).single().finishReason)
        failure(decode(start() + sequence + sequence.replace("END", "DIFFERENT") + stop))
        failure(decode(start() + sequence.replace("stop_reason\":\"stop_sequence", "stop_reason\":\"end_turn") + stop))
    }

    @Test fun rejectsOrderIndicesIdentityDuplicateFieldsAndUnsupportedContent() = runBlocking {
        for (wire in listOf(block(), start() + start(), start() + block(index = 1), start() + block() + block(),
            start() + delta("x"), start() + block() + delta("x", 1), start() + block() + blockStop(1),
            start() + blockStop(), start() + block() + blockStop() + block(index = 0), start() + finish() + block(),
            start().replace("\"assistant\"", "\"user\""), start().replace("\"content\":[]", "\"content\":[{}]"),
            start().replace("\"stop_reason\":null", "\"stop_reason\":\"end_turn\""),
            start().replace("\"id\":\"synthetic-provider-id\"", "\"id\":\"a\",\"id\":\"b\""),
            start().replace("\"model\":\"manual-model\"", "\"model\":\"\\ud800\""))) failure(decode(wire + stop))
        for (type in listOf("tool_use", "thinking", "redacted_thinking", "image")) {
            unsupported(decode(start() + block().replace("\"type\":\"text\"", "\"type\":\"$type\"") + stop))
        }
        for (type in listOf("input_json_delta", "thinking_delta", "signature_delta", "citations_delta")) {
            unsupported(decode(start() + block() + delta("x").replace("text_delta", type) + stop))
        }
        unsupported(decode(start() + block().replace("\"text\":\"\"", "\"text\":\"\",\"citations\":[{}]") + stop))
        unsupported(decode(start() + event("unknown") + stop))
        failure(decode(start().replace("event: message_start", "event: ping") + stop))
        failure(decode(start().replace("event: message_start\n", "") + stop))
        failure(decode(start().replace("event: message_start\n", "event: message_start\nevent: message_start\n") + stop))
    }

    @Test fun sanitizedErrorsPreservePrefixAndMalformedJsonFailsClosed() = runBlocking {
        val prefix = start() + block() + delta("prefix")
        for (bad in listOf(event("error", """, "error":{"message":"synthetic-sensitive-text"}"""),
            "event: content_block_delta\ndata: {invalid}\n\n", event("content_block_delta", ",\"unknown\":${"[".repeat(33)}0${"]".repeat(33)}"),
            event("content_block_delta", ",\"unknown\":[${List(10_001) { "null" }.joinToString(",")} ]"))) {
            val values = decode(prefix + bad + stop)
            assertEquals("prefix", (values.first() as Outcome.Success).value.text)
            failure(values)
            assertFalse((values.last() as Outcome.Failure).error.message.contains("synthetic-sensitive-text"))
        }
    }

    @Test fun terminalImmediatelyUnwindsOnceWithoutNextChunkAndIgnoresFollowingBytes() = runBlocking {
        var closed = false
        var advanced = false
        val upstream = flow<Outcome<ByteChunk>> {
            try {
                emit(Outcome.Success(ByteChunk(0, 0, SnapshotBytes((complete() + stop + "invalid suffix").toByteArray()), metadata(), false)))
                advanced = true
                awaitCancellation()
            } finally { closed = true }
        }
        val result = tokens(withTimeout(2_000) { codec.decode(requestId, upstream, cancel).toList() })
        assertEquals(listOf("prefix", ""), result.map { it.text })
        assertEquals(1, result.count { it.finishReason != null })
        assertTrue(closed)
        assertFalse(advanced)
    }

    @Test fun httpMetadataChunkOrderAndTypedUpstreamFailuresAreAdmittedBeforeParsing() = runBlocking {
        val wire = complete().toByteArray()
        val httpFailure = codec.decode(requestId, chunks(wire, http = metadata(429)), cancel).toList().single() as Outcome.Failure
        assertEquals(FailureCode.TRANSPORT, httpFailure.error.code)
        assertEquals(429, httpFailure.error.httpFailure!!.statusCode)
        for (type in listOf("application/json", "text/event-stream;charset=latin-1", "text/event-stream;charset=utf-8;charset=utf-8"))
            failure(codec.decode(requestId, chunks(wire, http = metadata(type = type)), cancel).toList())
        assertEquals(GenerationFinishReason.STOP, tokens(codec.decode(requestId,
            chunks(wire, http = metadata(type = "Text/Event-Stream; Charset=\"UTF-8\"")), cancel).toList()).last().finishReason)
        val conflicting = metadata().copy(headers = SnapshotList.of(HttpHeader("Content-Type", "text/event-stream"), HttpHeader("content-type", "application/json")))
        failure(codec.decode(requestId, chunks(wire, http = conflicting), cancel).toList())
        for (chunk in listOf(ByteChunk(1, 0, SnapshotBytes(wire), metadata(), true), ByteChunk(0, 1, SnapshotBytes(wire), metadata(), true),
            ByteChunk(0, 0, SnapshotBytes(wire), null, true), ByteChunk(0, 0, SnapshotBytes(byteArrayOf()), metadata(), false))) {
            failure(codec.decode(requestId, flow { emit(Outcome.Success(chunk)) }, cancel).toList())
        }
        val prefix = start().toByteArray()
        for (second in listOf(ByteChunk(2, prefix.size.toLong(), SnapshotBytes(wire), null, true),
            ByteChunk(1, prefix.size.toLong() + 1, SnapshotBytes(wire), null, true),
            ByteChunk(1, prefix.size.toLong(), SnapshotBytes(wire), metadata(type = "application/json"), true),
            ByteChunk(1, prefix.size.toLong(), SnapshotBytes(byteArrayOf()), metadata(), true))) {
            failure(codec.decode(requestId, flow {
                emit(Outcome.Success(ByteChunk(0, 0, SnapshotBytes(prefix), metadata(), false)))
                emit(Outcome.Success(second))
            }, cancel).toList())
        }
        val safe: List<Outcome<ByteChunk>> = listOf(Outcome.Failure(DomainFailure(FailureCode.TRANSPORT, "Synthetic transport failure", SnapshotList.empty(),
            HttpFailureMetadata(503, Knowledge.Unknown("Not supplied", SnapshotList.empty()), Knowledge.Unknown("Not supplied", SnapshotList.empty())))),
            Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Synthetic unavailable"), Outcome.Unknown("Synthetic unknown", SnapshotList.empty()))
        for (value in safe) {
            var advanced = false
            val result = codec.decode(requestId, flow { emit(value); advanced = true }, cancel).toList()
            assertSame(value, result.single())
            assertFalse(advanced)
        }
    }

    @Test fun unicodeStructureReplyEventAndResponseBudgetsFailBeforeGrowth() = runBlocking {
        val malformed = "event: ping\ndata: ".toByteArray() + byteArrayOf(0xc3.toByte(), 0x28) + "\n\n".toByteArray()
        failure(codec.decode(requestId, chunks(malformed), cancel).toList())
        failure(codec.decode(requestId, chunks("data: ".toByteArray() + byteArrayOf(0xc3.toByte())), cancel).toList())
        val half = "x".repeat(ProviderTextLimits.MAX_REPLY_BYTES / 2)
        assertEquals(ProviderTextLimits.MAX_REPLY_BYTES, tokens(decode(start() + block(half) + delta(half) + blockStop() + finish() + stop)).sumOf { it.text.toByteArray().size })
        val over = decode(start() + block(half) + delta(half) + delta("x") + blockStop() + finish() + stop)
        failure(over)
        assertEquals(2, over.filterIsInstance<Outcome.Success<TokenChunk>>().size)
        failure(decode(":" + "x".repeat(ProviderTextLimits.MAX_EVENT_BYTES) + "\n\n" + complete()))
        failure(codec.decode(requestId, chunks(ByteArray(ProviderTextLimits.MAX_WIRE_RESPONSE_BYTES.toInt() + 1)), cancel).toList())
        failure(codec.decode(EntityId("bad\nidentifier"), chunks(complete().toByteArray()), cancel).toList())
    }

    @Test fun cancellationAndCollectorExceptionsPhysicallyUnwindUpstreamUntranslated() = runBlocking {
        val cancelled = object : CancelToken {
            override val isCancelled = true
            override fun throwIfCancelled(): Unit = throw CancellationException("Synthetic cancellation")
        }
        try { codec.encode(profile(), request(), cancelled); fail("Expected cancellation") } catch (_: CancellationException) { }
        try { codec.decode(requestId, chunks(complete().toByteArray()), cancelled).toList(); fail("Expected cancellation") } catch (_: CancellationException) { }
        try { AnthropicMessagesJson.parse("{}", cancelled); fail("Expected cancellation") } catch (_: CancellationException) { }
        for (collectorError in listOf(CancellationException("Synthetic collector cancellation"), IllegalStateException("Synthetic collector failure"))) {
            var closed = false
            val upstream = flow<Outcome<ByteChunk>> {
                try {
                    emit(Outcome.Success(ByteChunk(0, 0, SnapshotBytes((start() + block("prefix")).toByteArray()), metadata(), false)))
                    awaitCancellation()
                } finally { closed = true }
            }
            try { codec.decode(requestId, upstream, cancel).collect { throw collectorError }; fail("Expected collector failure") }
            catch (actual: Exception) { assertSame(collectorError, actual); assertTrue(closed) }
        }
        var cancelledInFlight = false
        var cancelledClosed = false
        val inFlight = object : CancelToken {
            override val isCancelled get() = cancelledInFlight
            override fun throwIfCancelled() { if (isCancelled) throw CancellationException("Synthetic in-flight cancellation") }
        }
        val streaming = flow<Outcome<ByteChunk>> {
            try {
                val bytes = (start() + block("prefix")).toByteArray()
                emit(Outcome.Success(ByteChunk(0, 0, SnapshotBytes(bytes), metadata(), false)))
                cancelledInFlight = true
                emit(Outcome.Success(ByteChunk(1, bytes.size.toLong(), SnapshotBytes(delta("next").toByteArray()), null, false)))
            } finally { cancelledClosed = true }
        }
        try { codec.decode(requestId, streaming, inFlight).toList(); fail("Expected in-flight cancellation") }
        catch (_: CancellationException) { assertTrue(cancelledClosed) }
        var closed = false
        var translated = false
        val invalid = flow<Outcome<ByteChunk>> {
            try {
                emit(Outcome.Success(ByteChunk(0, 0, SnapshotBytes("event: ping\ndata: {invalid}\n\n".toByteArray()), metadata(), false)))
                awaitCancellation()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { translated = true; emit(Outcome.Unknown("Synthetic translation", SnapshotList.empty()))
            } finally { closed = true }
        }
        failure(withTimeout(2_000) { codec.decode(requestId, invalid, cancel).toList() })
        assertTrue(closed)
        assertFalse(translated)
    }
}
