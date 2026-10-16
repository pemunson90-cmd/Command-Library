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

/** Synthetic fixtures derived from official SDK 9998c1a declarations + SOURCE02 app policy.
 * These are not authentic provider traffic, SDK execution, terms or billing acceptance. */
class ResponsesWireCodecTest {
    private val codec = ResponsesWireCodec()
    private val requestId = EntityId("local-request")
    private val cancel = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private fun profile() = ProviderProfileFixtures.profile().copy(dialect = ProviderDialect.OPENAI_RESPONSES)
    private fun request(text: String = "synthetic prompt") = GenerationRequest(requestId,
        SnapshotList.of(GenerationMessage(MessageRole.USER, text, null)), null, null, null, null, SnapshotList.empty())
    private fun metadata(status: Int = 200, type: String = "text/event-stream") = HttpMetadata(status,
        SnapshotList.of(HttpHeader("Content-Type", type)), ResourceUri("https://example.test/v1/responses"),
        UtcTimestamp(0), Knowledge.Unknown("Not reported", SnapshotList.empty()), Knowledge.Unknown("Not reported", SnapshotList.empty()))
    private fun body(request: GenerationRequest = request()) = (codec.encode(profile(), request, cancel) as Outcome.Success)
        .value.body!!.copyBytes().toString(Charsets.UTF_8)
    private fun quote(text: String) = JsonPrimitive(text).toString()
    private fun part(text: String) = """{"type":"output_text","text":${quote(text)},"annotations":[],"logprobs":null}"""
    private fun item(texts: List<String>, status: String = "completed", id: String = "item-a", phase: String = "null") =
        """{"type":"message","role":"assistant","id":"$id","status":"$status","phase":$phase,"content":[${texts.joinToString(",") { part(it) }}]}"""
    private fun response(status: String, output: String? = "[]", usage: String? = null, id: String = "response-a", extra: String = "") =
        """{"id":"$id","status":"$status"${if (output == null) "" else ",\"output\":$output"}${if (usage == null) "" else ",\"usage\":$usage"}$extra}"""
    private fun event(type: String, sequence: Long, fields: String = "", name: String? = type) =
        "${if (name == null) "" else "event: $name\n"}data: {\"type\":\"$type\",\"sequence_number\":$sequence$fields}\n\n"
    private fun created(usage: String? = null) = event("response.created", 0, ",\"response\":" + response("in_progress", usage = usage))
    private fun added(index: Long = 7, id: String = "item-a", phase: String = "null", sequence: Long = 2) =
        event("response.output_item.added", sequence, """, "output_index":$index,"item":${item(emptyList(), "in_progress", id, phase)}""")
    private fun coordinates(index: Long = 7, content: Long = 12, id: String = "item-a") =
        """, "output_index":$index,"content_index":$content,"item_id":"$id""""
    private fun partAdded(text: String = "", sequence: Long = 4, index: Long = 7, content: Long = 12, id: String = "item-a") =
        event("response.content_part.added", sequence, coordinates(index, content, id) + ",\"part\":" + part(text))
    private fun delta(text: String, sequence: Long = 6, index: Long = 7, content: Long = 12, id: String = "item-a") =
        event("response.output_text.delta", sequence, coordinates(index, content, id) + ",\"delta\":" + quote(text) + ",\"logprobs\":[]")
    private fun textDone(text: String, sequence: Long = 8, index: Long = 7, content: Long = 12, id: String = "item-a") =
        event("response.output_text.done", sequence, coordinates(index, content, id) + ",\"text\":" + quote(text) + ",\"logprobs\":[]")
    private fun partDone(text: String, sequence: Long = 10, index: Long = 7, content: Long = 12, id: String = "item-a") =
        event("response.content_part.done", sequence, coordinates(index, content, id) + ",\"part\":" + part(text))
    private fun itemDone(texts: List<String>, sequence: Long = 12, index: Long = 7, id: String = "item-a", status: String = "completed", phase: String = "null") =
        event("response.output_item.done", sequence, """, "output_index":$index,"item":${item(texts, status, id, phase)}""")
    private fun completed(texts: List<String> = listOf("prefix"), usage: String? = null) = event("response.completed", 14,
        ",\"response\":" + response("completed", "[${item(texts)}]", usage))
    private fun prefix(text: String = "prefix") = created() + added() + partAdded() + delta(text)
    private fun closed(text: String = "prefix") = prefix(text) + textDone(text) + partDone(text) + itemDone(listOf(text))
    private fun complete(text: String = "prefix") = closed(text) + completed(listOf(text))
    private fun terminal(type: String, output: String? = null, usage: String? = null, id: String = "response-a", extra: String = "") =
        event("response.$type", 50, ",\"response\":" + response(type, output, usage, id, extra))
    private fun incomplete(reason: String = "max_output_tokens", output: String? = null, extra: String = "") =
        terminal("incomplete", output, extra = ",\"incomplete_details\":{\"reason\":\"$reason\"}$extra")
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
    private fun lastUsage(values: List<Outcome<TokenChunk>>) = (tokens(values).last().usage as Knowledge.Known).value

    @Test fun encodeUsesExplicitPrivacyNativeInputOptionsAndStaticHeaders() {
        val input = request().copy(messages = SnapshotList.of(GenerationMessage(MessageRole.SYSTEM, "system", null),
            GenerationMessage(MessageRole.USER, "quotes \" \\ 日本語\n😀", null), GenerationMessage(MessageRole.ASSISTANT, "answer", null)),
            maxTokens = 7, temperature = BigDecimal("0.25"), topP = BigDecimal("0.9"))
        val wire = (codec.encode(profile(), input, cancel) as Outcome.Success).value
        assertEquals(HttpMethod.POST, wire.method)
        assertEquals("https://example.test/v1/responses", wire.uri.value)
        assertEquals(listOf(HttpHeader("Content-Type", "application/json"), HttpHeader("Accept", "text/event-stream")), wire.headers)
        val json = JsonParser.parseString(body(input)).asJsonObject
        assertEquals(setOf("model", "stream", "store", "instructions", "input", "max_output_tokens", "temperature", "top_p"), json.keySet())
        assertEquals("manual-model", json.get("model").asString)
        assertTrue(json.get("stream").asBoolean)
        assertFalse(json.get("store").asBoolean)
        assertEquals("system", json.get("instructions").asString)
        assertEquals(7, json.get("max_output_tokens").asInt)
        assertEquals(BigDecimal("0.25"), json.get("temperature").asBigDecimal)
        assertEquals(BigDecimal("0.9"), json.get("top_p").asBigDecimal)
        assertEquals(listOf("user", "assistant"), json.getAsJsonArray("input").map { it.asJsonObject.get("role").asString })
        assertEquals(input.messages[1].text, json.getAsJsonArray("input")[0].asJsonObject.get("content").asString)
        assertEquals(setOf("model", "stream", "store", "input"), JsonParser.parseString(body()).asJsonObject.keySet())
        for (temperature in listOf("0", "2")) assertTrue(codec.encode(profile(), request().copy(temperature = BigDecimal(temperature)), cancel) is Outcome.Success)
    }

    @Test fun encodeRejectsUnsupportedSettingsAndNoncanonicalOrInvalidInput() {
        for (input in listOf(request().copy(seed = 0), request().copy(stopSequences = SnapshotList.of("END")),
            request().copy(messages = SnapshotList.of(GenerationMessage(MessageRole.TOOL, "x", null))),
            request().copy(messages = SnapshotList.of(GenerationMessage(MessageRole.USER, "x", "tool-id"))),
            request().copy(messages = SnapshotList.of(GenerationMessage(MessageRole.USER, "x", null), GenerationMessage(MessageRole.SYSTEM, "x", null))),
            request().copy(messages = SnapshotList.of(GenerationMessage(MessageRole.SYSTEM, "x", null), GenerationMessage(MessageRole.SYSTEM, "x", null))))) {
            assertTrue(codec.encode(profile(), input, cancel) is Outcome.Unavailable)
        }
        for (p in listOf(profile().copy(dialect = ProviderDialect.OPENAI_CHAT), profile().copy(apiVersion = Knowledge.Known("version")),
            profile().copy(extraHeaders = SnapshotList.of(HttpHeader("X-Extra", "value"))))) {
            assertTrue(codec.encode(p, request(), cancel) is Outcome.Unavailable)
        }
        assertTrue(codec.encode(profile().copy(baseUri = ResourceUri("HTTPS://EXAMPLE.TEST:443/v1/")), request(), cancel) is Outcome.Failure)
        for (input in listOf(request("bad\uD800"), request().copy(messages = SnapshotList.empty()),
            request().copy(messages = SnapshotList.of(GenerationMessage(MessageRole.SYSTEM, "x", null))),
            request().copy(messages = SnapshotList.copyOf(List(65) { GenerationMessage(MessageRole.USER, "x", null) })),
            request().copy(temperature = BigDecimal("2.01")), request().copy(temperature = BigDecimal("1e-33")),
            request().copy(topP = BigDecimal("0.123456789012345678901234567890123")))) {
            assertEquals(FailureCode.INVALID_INPUT, (codec.encode(profile(), input, cancel) as Outcome.Failure).error.code)
        }
    }

    @Test fun encodeContentAndEscapedRequestBudgetsAreIndependent() {
        assertTrue(codec.encode(profile(), request("a".repeat(ProviderTextLimits.MAX_MESSAGE_BYTES)), cancel) is Outcome.Success)
        assertTrue(codec.encode(profile(), request("a".repeat(ProviderTextLimits.MAX_MESSAGE_BYTES + 1)), cancel) is Outcome.Failure)
        val available = ProviderTextLimits.MAX_REQUEST_BYTES - body(request("")).toByteArray().size
        val text = "\u0001".repeat(available / 6) + "a".repeat(available % 6)
        assertEquals(ProviderTextLimits.MAX_REQUEST_BYTES, body(request(text)).toByteArray().size)
        assertTrue(codec.encode(profile(), request(text + "a"), cancel) is Outcome.Failure)
    }

    @Test fun sourceDerivedReferencePreservesStartTextAndNeverAppendsDoneSnapshots() = runBlocking {
        val text = "日😀tail"
        val reference = created() + added() + partAdded("日😀") + delta("tail") + textDone(text) + partDone(text) + itemDone(listOf(text)) + completed(listOf(text))
        val result = tokens(decode(reference))
        assertEquals(listOf("日😀", "tail", ""), result.map { it.text })
        assertEquals(listOf(0L, 1L, 2L), result.map { it.sequence })
        assertTrue(result.all { it.requestId == requestId && it.tokenIds.isEmpty() })
        assertEquals(GenerationFinishReason.STOP, result.last().finishReason)
        val usage = (result.last().usage as Knowledge.Known).value
        assertEquals(Knowledge.Known("response-a"), usage.providerRequestId)
        assertTrue(usage.inputTokens is Knowledge.Unknown)
        assertTrue(usage.outputTokens is Knowledge.Unknown)
        assertTrue(usage.cachedInputTokens is Knowledge.Unknown)
        assertTrue(usage.billedAmount is Knowledge.Unknown)
    }

    @Test fun framingHandlesUtf8CrLfBomCommentsMultilineAndDefaultDataOnlyEvents() = runBlocking {
        val reference = "\uFEFF: comment\r\nretry: 123\rid: ignored\n\n" + complete("日😀tail")
        val variants = listOf(reference, reference.replace(Regex("event: [^\\n]*\\n"), ""),
            reference.replace(Regex("event: [^\\n]*\\n"), "event: message\n"),
            reference.replace(Regex("event: [^\\n]*\\n"), "event:\n"))
        val expected = tokens(decode(reference))
        for (variant in variants) {
            val multiline = variant.replace("data: {\"type\":", "data: {\r\ndata: \"type\":").replace("\n", "\r\n").replace("\r\r\n", "\r\n")
            val bytes = multiline.toByteArray()
            assertEquals(expected, tokens(codec.decode(requestId, chunks(bytes), cancel).toList()))
            assertEquals(expected, tokens(codec.decode(requestId, chunks(bytes, (1 until bytes.size).toList()), cancel).toList()))
        }
        failure(decode(complete().replaceFirst("event: response.created", "event: wrong")))
        failure(decode(complete().replaceFirst("\"type\":\"response.created\",", "")))
    }

    @Test fun multipleGappedItemsAndPartsPreserveArrayOrderWithoutRepartition() = runBlocking {
        val first = created() + added(index = Long.MAX_VALUE - 1) + partAdded("a", index = Long.MAX_VALUE - 1, content = 9) +
            textDone("a", index = Long.MAX_VALUE - 1, content = 9) + partDone("a", index = Long.MAX_VALUE - 1, content = 9) +
            partAdded("b", 11, Long.MAX_VALUE - 1, Long.MAX_VALUE) + textDone("b", 12, Long.MAX_VALUE - 1, Long.MAX_VALUE) +
            partDone("b", 13, Long.MAX_VALUE - 1, Long.MAX_VALUE) + itemDone(listOf("a", "b"), 14, Long.MAX_VALUE - 1)
        val second = added(Long.MAX_VALUE, "item-b", sequence = 16) + partAdded("c", 18, Long.MAX_VALUE, 0, "item-b") +
            textDone("c", 20, Long.MAX_VALUE, 0, "item-b") + partDone("c", 22, Long.MAX_VALUE, 0, "item-b") +
            itemDone(listOf("c"), 24, Long.MAX_VALUE, "item-b")
        val final = event("response.completed", 26, ",\"response\":" + response("completed", "[${item(listOf("a", "b"))},${item(listOf("c"), id = "item-b")}]") )
        assertEquals(listOf("a", "b", "c", ""), tokens(decode(first + second + final)).map { it.text })
        val reordered = final.replace("[${item(listOf("a", "b"))},${item(listOf("c"), id = "item-b")}]", "[${item(listOf("c"), id = "item-b")},${item(listOf("a", "b"))}]")
        failure(decode(first + second + reordered))
        failure(decode(closed("ab") + completed(listOf("a", "b"))))
    }

    @Test fun wrongCoordinatesOrderingIdentitiesAndDuplicateSnapshotsFail() = runBlocking {
        for (wire in listOf(
            added(), created() + partAdded(), created() + added() + delta("x"),
            created() + added() + added(index = 8, sequence = 3),
            prefix() + delta("x", 8, index = 8), prefix() + delta("x", 8, content = 13), prefix() + delta("x", 8, id = "other"),
            prefix() + textDone("wrong"), prefix() + partDone("prefix"),
            prefix() + textDone("prefix") + delta("x", 9), prefix() + textDone("prefix") + textDone("prefix", 9),
            prefix() + textDone("prefix") + partDone("wrong"), prefix() + itemDone(listOf("prefix")),
            closed() + itemDone(listOf("prefix"), 13), closed() + completed(listOf("wrong")),
            prefix() + textDone("prefix") + partDone("prefix") + itemDone(listOf("prefix"), id = "wrong"),
            closed() + completed().replace("\"id\":\"item-a\"", "\"id\":\"wrong\""),
            created() + added() + partAdded() + delta("x", 4), created() + added() + partAdded() + delta("x", 3))) {
            failure(decode(wire))
        }
    }

    @Test fun indicesCannotRepeatDecreaseOrBeNegativeOrFractional() = runBlocking {
        for (value in listOf("-1", "0.5", "9223372036854775808", "\"7\"")) {
            failure(decode(created() + added().replace("\"output_index\":7", "\"output_index\":$value")))
        }
        val finished = created() + added() + itemDone(emptyList(), 3)
        for (index in listOf(0L, 7L)) failure(decode(finished + added(index, "item-b", sequence = 4)))
        failure(decode(finished + added(8, "item-a", sequence = 4)))
        val partClosed = created() + added() + partAdded("a") + textDone("a") + partDone("a")
        for (index in listOf(0L, 12L)) failure(decode(partClosed + partAdded("b", 11, content = index)))
    }

    @Test fun phaseAndKnownMetadataAreImmutableAndNontextCapabilitiesUnsupported() = runBlocking {
        val finalPhase = complete().replace("\"phase\":null", "\"phase\":\"final_answer\"")
        assertEquals(GenerationFinishReason.STOP, tokens(decode(finalPhase)).last().finishReason)
        assertEquals(GenerationFinishReason.STOP, tokens(decode(complete().replace(",\"phase\":null", "")))).last().finishReason)
        failure(decode(created() + added(phase = "\"final_answer\"") + partAdded() + delta("prefix") + textDone("prefix") + partDone("prefix") + itemDone(listOf("prefix"))))
        failure(decode(closed() + completed().replace("\"phase\":null", "\"phase\":\"final_answer\"")))
        for (wire in listOf(created() + added(phase = "\"commentary\""), created() + added(phase = "\"other\""),
            created() + added().replace("\"type\":\"message\"", "\"type\":\"reasoning\""),
            created() + added().replace("\"role\":\"assistant\"", "\"role\":\"user\""),
            created() + added() + partAdded().replace("\"output_text\"", "\"refusal\""),
            created() + added() + partAdded().replace("\"output_text\"", "\"reasoning_text\""),
            created() + added() + partAdded().replace("\"annotations\":[]", "\"annotations\":[{}]"),
            prefix() + textDone("prefix").replace("\"logprobs\":[]", "\"logprobs\":[{}]"),
            created() + event("response.reasoning_text.delta", 1), created() + event("response.cancelled", 1))) unsupported(decode(wire))
    }

    @Test fun lifecycleIdentityStatusAndContradictoryMetadataNeverBecomeSuccess() = runBlocking {
        val progress = event("response.in_progress", 1, ",\"response\":" + response("in_progress"))
        assertEquals(GenerationFinishReason.STOP, tokens(decode(created() + progress + added() + partAdded() + delta("prefix") + textDone("prefix") + partDone("prefix") + itemDone(listOf("prefix")) + completed())).last().finishReason)
        for (wire in listOf(created() + created(), created().replace("\"in_progress\"", "\"cancelled\""),
            created().replace("\"output\":[]", "\"output\":null"),
            created() + progress.replace("response-a", "wrong"), created() + progress.replace("\"status\":\"in_progress\"", "\"status\":\"queued\""),
            closed() + completed().replace("response-a", "wrong"), closed() + completed().replace("\"status\":\"completed\"", "\"status\":\"cancelled\""),
            prefix() + terminal("failed", id = "wrong"), prefix() + incomplete().replace("response-a", "wrong"),
            created().replace("\"output\":[]", "\"output\":[],\"error\":{}"),
            created() + progress.replace("\"output\":[]", "\"output\":[],\"incomplete_details\":{}"),
            closed() + completed().replace("\"output\":[", "\"error\":{},\"output\":["),
            prefix() + incomplete(extra = ",\"error\":{}"), prefix() + terminal("failed", extra = ",\"incomplete_details\":{}"))) failure(decode(wire))
        unsupported(decode(created().replace("\"output\":[]", "\"output\":[${item(listOf("x"))}]")))
        unsupported(decode(created() + added().replace("\"content\":[]", "\"content\":[${part("x")}]")))
    }

    @Test fun onlyCompletedWithClosedExactFullOutputCanSucceed() = runBlocking {
        for (wire in listOf("data: [DONE]\n\n", created(), prefix(), prefix() + textDone("prefix"), closed(), closed() + "data: [DONE]\n\n",
            prefix() + completed(), closed() + terminal("completed"), closed() + terminal("completed", "null"),
            closed() + terminal("completed", "[]"), closed() + terminal("completed", "[${item(listOf("prefix"), "incomplete")}]"),
            created() + added() + itemDone(emptyList(), status = "incomplete") + terminal("completed", "[${item(emptyList())}]"))) failure(decode(wire))
        val empty = tokens(decode(created() + terminal("completed", "[]")))
        assertEquals(listOf(""), empty.map { it.text })
        assertEquals(GenerationFinishReason.STOP, empty.single().finishReason)
        assertEquals(GenerationFinishReason.STOP, tokens(decode(complete() + "data: corrupt ignored after terminal\n\n")).last().finishReason)
    }

    @Test fun incompleteReasonsMapToNoncompleteTerminalsAndPreserveExactPrefix() = runBlocking {
        for ((reason, finish) in listOf("max_output_tokens" to GenerationFinishReason.TOKEN_LIMIT, "max_messages" to GenerationFinishReason.INTERRUPTED,
            "steered" to GenerationFinishReason.INTERRUPTED, "content_filter" to GenerationFinishReason.FAILED)) {
            val result = tokens(decode(prefix() + incomplete(reason)))
            assertEquals(listOf("prefix", ""), result.map { it.text })
            assertEquals(finish, result.last().finishReason)
        }
        unsupported(decode(prefix() + incomplete("unknown")))
        failure(decode(prefix() + terminal("incomplete")))
        assertEquals(GenerationFinishReason.TOKEN_LIMIT, tokens(decode(prefix() + incomplete(output = "[${item(listOf("prefix"), "incomplete")}]") )).last().finishReason)
        assertEquals(GenerationFinishReason.TOKEN_LIMIT, tokens(decode(closed() + incomplete(output = "[${item(listOf("prefix"))}]") )).last().finishReason)
    }

    @Test fun incompleteAndFailedSnapshotsCannotNullRewriteRepartitionOrPromoteObservedParts() = runBlocking {
        for (output in listOf("null", "[]", "[${item(emptyList(), "incomplete")}]", "[${item(listOf("pre"), "incomplete")}]",
            "[${item(listOf("prefixX"), "incomplete")}]", "[${item(listOf("pre", "fix"), "incomplete")}]",
            "[${item(listOf("prefix"), "completed")}]", "[${item(listOf("prefix"), "incomplete", id = "other")}]",
            "[${item(listOf("prefix"), "incomplete", phase = "\"final_answer\"")}]") ) {
            failure(decode(prefix() + incomplete(output = output)))
            failure(decode(prefix() + terminal("failed", output)))
        }
        val exact = "[${item(listOf("prefix"), "in_progress")}]"
        assertEquals(GenerationFinishReason.TOKEN_LIMIT, tokens(decode(prefix() + incomplete(output = exact))).last().finishReason)
        failure(decode(prefix() + terminal("failed", exact))) // failure is expected even for a valid failed snapshot
        failure(decode(closed() + incomplete(output = "[${item(listOf("prefix"), "incomplete")}]")))
    }

    @Test fun failedAndStandaloneErrorUseStaticMessagesAndRetainPrefixAndReportedUsage() = runBlocking {
        val wire = prefix() + terminal("failed", usage = """{"input_tokens":0,"output_tokens":2}""", extra = ",\"error\":{\"message\":\"private-secret\"}")
        val values = decode(wire)
        failure(values)
        assertEquals("prefix", (values.first() as Outcome.Success).value.text)
        val update = (values[values.lastIndex - 1] as Outcome.Success).value
        assertEquals("", update.text)
        assertNull(update.finishReason)
        val usage = (update.usage as Knowledge.Known).value
        assertEquals(Knowledge.Known(0L), usage.inputTokens)
        assertEquals(Knowledge.Known(2L), usage.outputTokens)
        assertFalse(values.toString().contains("private-secret"))
        for (wireError in listOf(event("error", 0, ",\"message\":\"private-secret\""), prefix() + event("error", 9, ",\"message\":\"private-secret\""))) {
            val errors = decode(wireError)
            failure(errors)
            assertFalse(errors.toString().contains("private-secret"))
        }
    }

    @Test fun directUsageKeepsZeroCacheReadAndOmittedNullKnownWithoutAddingBreakdowns() = runBlocking {
        val initial = """{"input_tokens":10,"output_tokens":0,"total_tokens":10,"input_tokens_details":{"cached_tokens":0,"cache_write_tokens":3},"output_tokens_details":{"reasoning_tokens":0}}"""
        val final = """{"input_tokens":null,"output_tokens":5,"total_tokens":15,"input_tokens_details":{"cached_tokens":null,"cache_write_tokens":4},"output_tokens_details":{"reasoning_tokens":2}}"""
        val result = lastUsage(decode(created(initial) + added() + partAdded() + delta("prefix") + textDone("prefix") + partDone("prefix") + itemDone(listOf("prefix")) + completed(usage = final)))
        assertEquals(Knowledge.Known(10L), result.inputTokens)
        assertEquals(Knowledge.Known(5L), result.outputTokens)
        assertEquals(Knowledge.Known(0L), result.cachedInputTokens)
        assertTrue(result.billedAmount is Knowledge.Unknown)
        val absent = lastUsage(decode(closed() + completed(usage = """{"input_tokens_details":{"cache_write_tokens":4},"output_tokens_details":{"reasoning_tokens":2}}""")))
        assertTrue(absent.inputTokens is Knowledge.Unknown)
        assertTrue(absent.outputTokens is Knowledge.Unknown)
        assertTrue(absent.cachedInputTokens is Knowledge.Unknown)
    }

    @Test fun everyReportedCounterIsExactNonnegativeLongAndCannotDecrease() = runBlocking {
        val shapes = listOf("input_tokens" to "", "output_tokens" to "", "total_tokens" to "",
            "cached_tokens" to "input_tokens_details", "cache_write_tokens" to "input_tokens_details", "reasoning_tokens" to "output_tokens_details")
        fun usage(counter: String, parent: String, value: String) = if (parent.isEmpty()) "{\"$counter\":$value}" else "{\"$parent\":{\"$counter\":$value}}"
        for ((counter, parent) in shapes) {
            for (invalid in listOf("-1", "1.5", "9223372036854775808", "1e33", "\"2\"", "true")) failure(decode(created(usage(counter, parent, invalid))))
            failure(decode(created(usage(counter, parent, "2")) + terminal("completed", "[]", usage(counter, parent, "1"))))
            assertEquals(GenerationFinishReason.STOP, tokens(decode(created(usage(counter, parent, "9223372036854775807")) + terminal("completed", "[]"))).last().finishReason)
        }
    }

    @Test fun strictJsonRejectsDuplicateFieldsUnicodeDepthNodesAndMalformedPayloads() = runBlocking {
        for (wire in listOf(created().replace("\"sequence_number\":0", "\"sequence_number\":0,\"sequence_number\":1"),
            created().replace("\"response-a\"", "\"\\uD800\""), created().replace("\"id\":\"response-a\"", "\"id\":\"\""),
            created().replace("\"output\":[]", "\"output\":[],\"extra\":" + "[".repeat(33) + "0" + "]".repeat(33)),
            created().replace("\"output\":[]", "\"output\":[],\"extra\":[" + List(10_001) { "null" }.joinToString(",") + "]"),
            "data: {} trailing\n\n", "data: {unquoted:true}\n\n")) failure(decode(wire))
        val malformed = byteArrayOf(0xc3.toByte(), 0x28)
        failure(codec.decode(requestId, chunks(malformed), cancel).toList())
        failure(decode(": " + "x".repeat(ProviderTextLimits.MAX_EVENT_BYTES) + "\n\n"))
    }

    @Test fun replyAndWireBudgetsBoundedWithoutAllocatingSparseIndices() = runBlocking {
        val text = "a".repeat(ProviderTextLimits.MAX_REPLY_BYTES)
        assertEquals(GenerationFinishReason.STOP, tokens(decode(complete(text))).last().finishReason)
        failure(decode(prefix(text) + delta("a", sequence = 7)))
        val padding = (": " + "a".repeat(1024) + "\n\n").repeat(8192)
        failure(decode(padding))
        var wire = created()
        for (index in 0..64) wire += added(index.toLong(), "item-$index", sequence = index * 4L + 2) + itemDone(emptyList(), index * 4L + 3, index.toLong(), "item-$index")
        failure(decode(wire))
        var parts = created() + added()
        for (index in 0..64) parts += partAdded("", index * 6L + 4, content = index.toLong()) + textDone("", index * 6L + 6, content = index.toLong()) + partDone("", index * 6L + 8, content = index.toLong())
        failure(decode(parts))
    }

    @Test fun transportMetadataOrderingAndUpstreamFailuresRemainTyped() = runBlocking {
        for (http in listOf(metadata(401), metadata(type = "application/json"), metadata(type = "text/event-stream; charset=latin1"))) {
            failure(codec.decode(requestId, chunks(complete().toByteArray(), http = http), cancel).toList())
        }
        for (chunk in listOf(ByteChunk(1, 0, SnapshotBytes(created().toByteArray()), metadata(), false),
            ByteChunk(0, 1, SnapshotBytes(created().toByteArray()), metadata(), false),
            ByteChunk(0, 0, SnapshotBytes(created().toByteArray()), null, false),
            ByteChunk(0, 0, SnapshotBytes(byteArrayOf()), metadata(), false))) {
            failure(codec.decode(requestId, flow { emit(Outcome.Success(chunk)) }, cancel).toList())
        }
        val expected = Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Upstream capability")
        assertEquals(listOf(expected), codec.decode(requestId, flow { emit(expected) }, cancel).toList())
        val first = created().toByteArray()
        val changed = flow {
            emit(Outcome.Success(ByteChunk(0, 0, SnapshotBytes(first), metadata(), false)))
            emit(Outcome.Success(ByteChunk(1, first.size.toLong(), SnapshotBytes(added().toByteArray()), metadata(type = "text/event-stream; charset=utf-8"), false)))
        }
        failure(codec.decode(requestId, changed, cancel).toList())
    }

    @Test fun terminalAndErrorCloseUpstreamBeforeAnotherChunkOrEof() = runBlocking {
        for (wire in listOf(complete(), prefix() + incomplete(), prefix() + terminal("failed"), event("error", 0))) {
            var closed = false
            var advanced = false
            val source = flow<Outcome<ByteChunk>> {
                try {
                    emit(Outcome.Success(ByteChunk(0, 0, SnapshotBytes(wire.toByteArray()), metadata(), false)))
                    advanced = true
                    awaitCancellation()
                } finally { closed = true }
            }
            val result = withTimeout(2000) { codec.decode(requestId, source, cancel).toList() }
            assertTrue(result.isNotEmpty())
            assertTrue(closed)
            assertFalse(advanced)
        }
    }

    @Test fun cancellationAndDownstreamExceptionsPropagateAndCloseTransport() = runBlocking {
        val cancellation = CancellationException("local cancel")
        val cancelled = object : CancelToken {
            override val isCancelled = true
            override fun throwIfCancelled(): Unit = throw cancellation
        }
        try { codec.encode(profile(), request(), cancelled); fail("Expected cancellation") } catch (actual: CancellationException) { assertSame(cancellation, actual) }
        try { codec.decode(requestId, chunks(complete().toByteArray()), cancelled).toList(); fail("Expected cancellation") } catch (actual: CancellationException) { assertSame(cancellation, actual) }
        var activeCancelled = false
        var activeClosed = false
        val activeToken = object : CancelToken {
            override val isCancelled get() = activeCancelled
            override fun throwIfCancelled() { if (activeCancelled) throw cancellation }
        }
        val activeSource = flow<Outcome<ByteChunk>> {
            try {
                emit(Outcome.Success(ByteChunk(0, 0, SnapshotBytes(complete().toByteArray()), metadata(), false)))
                awaitCancellation()
            } finally { activeClosed = true }
        }
        try {
            codec.decode(requestId, activeSource, activeToken).collect { activeCancelled = true }
            fail("Expected active cancellation")
        } catch (actual: CancellationException) { assertSame(cancellation, actual) }
        assertTrue(activeClosed)
        for (problem in listOf(IllegalStateException("collector"), CancellationException("collector cancel"))) {
            var closed = false
            var advanced = false
            val source = flow<Outcome<ByteChunk>> {
                try {
                    emit(Outcome.Success(ByteChunk(0, 0, SnapshotBytes(prefix().toByteArray()), metadata(), false)))
                    advanced = true
                    awaitCancellation()
                } finally { closed = true }
            }
            try {
                codec.decode(requestId, source, cancel).collect { throw problem }
                fail("Expected downstream exception")
            } catch (actual: Exception) { assertSame(problem, actual) }
            assertTrue(closed)
            assertFalse(advanced)
        }
    }
}
