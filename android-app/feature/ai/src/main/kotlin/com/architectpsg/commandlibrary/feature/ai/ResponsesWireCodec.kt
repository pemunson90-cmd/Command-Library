package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.*
import com.google.gson.stream.JsonWriter
import java.io.Writer
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/** Pure, bounded manual-model Responses text codec. No credentials or transport access. */
public class ResponsesWireCodec {
    public fun encode(profile: ProviderProfile, request: GenerationRequest, cancel: CancelToken): Outcome<Request> {
        cancel.throwIfCancelled()
        return try {
            if (profile.dialect != ProviderDialect.OPENAI_RESPONSES || profile.apiVersion is Knowledge.Known ||
                profile.extraHeaders.isNotEmpty() || request.seed != null || request.stopSequences.isNotEmpty()) responsesUnsupported()
            val normalized = when (val outcome = ProviderProfilePolicy.normalize(profile)) {
                is Outcome.Success -> outcome.value
                is Outcome.Failure -> return outcome
                is Outcome.Unavailable -> return outcome
                is Outcome.Unknown -> return outcome
            }
            responsesRequire(normalized == profile, "Provider profile must be canonical", true)
            ResponsesJson.id(request.id.value, cancel, true)
            ResponsesJson.id(normalized.selectedModelId, cancel, true)
            responsesRequire(request.messages.size in 1..ProviderTextLimits.MAX_MESSAGES, "Invalid message count", true)
            val root = JsonObject()
            root.addProperty("model", normalized.selectedModelId)
            root.addProperty("stream", true)
            root.addProperty("store", false)
            val messages = JsonArray()
            var contentBytes = 0
            for ((index, message) in request.messages.withIndex()) {
                cancel.throwIfCancelled()
                if (message.role == MessageRole.TOOL || message.toolCallId != null) responsesUnsupported()
                contentBytes += ResponsesJson.textBytes(message.text,
                    ProviderTextLimits.MAX_MESSAGE_BYTES - contentBytes, cancel, true)
                if (message.role == MessageRole.SYSTEM) {
                    if (index != 0) responsesUnsupported()
                    root.addProperty("instructions", message.text)
                } else messages.add(JsonObject().apply {
                    addProperty("role", when (message.role) {
                        MessageRole.SYSTEM -> responsesUnsupported()
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        MessageRole.TOOL -> responsesUnsupported()
                    })
                    addProperty("content", message.text)
                })
            }
            responsesRequire(messages.size() > 0, "At least one conversation message is required", true)
            root.add("input", messages)
            request.maxTokens?.let { root.addProperty("max_output_tokens", it) }
            request.temperature?.let {
                responsesRequire(it >= java.math.BigDecimal.ZERO && it <= java.math.BigDecimal("2"), "Invalid temperature", true)
                ResponsesJson.decimal(it, true)
                root.addProperty("temperature", it)
            }
            request.topP?.let { ResponsesJson.decimal(it, true); root.addProperty("top_p", it) }
            val output = RequestWriter(cancel)
            JsonWriter(output).use { writer -> GsonBuilder().disableHtmlEscaping().create().toJson(root, writer) }
            cancel.throwIfCancelled()
            Outcome.Success(Request(HttpMethod.POST,
                ResourceUri(normalized.baseUri!!.value.trimEnd('/') + "/responses"),
                SnapshotList.of(HttpHeader("Content-Type", "application/json"), HttpHeader("Accept", "text/event-stream")),
                SnapshotBytes(output.text.toString().toByteArray(Charsets.UTF_8))))
        } catch (rejection: ResponsesCodecRejection) { rejection.outcome }
    }

    public fun decode(requestId: EntityId, chunks: Flow<Outcome<ByteChunk>>, cancel: CancelToken): Flow<Outcome<TokenChunk>> = flow {
        val stop = StopCollection()
        var failure: Outcome<Nothing>? = null
        try {
            cancel.throwIfCancelled()
            ResponsesJson.id(requestId.value, cancel, true)
            val state = DecodeState(requestId, cancel)
            val sse = ResponsesSse(cancel)
            var sequence = 0L
            var offset = 0L
            var metadata: HttpMetadata? = null
            chunks.collect { result ->
                cancel.throwIfCancelled()
                val chunk = when (result) {
                    is Outcome.Success -> result.value
                    is Outcome.Failure -> { failure = result; throw stop }
                    is Outcome.Unavailable -> { failure = result; throw stop }
                    is Outcome.Unknown -> { failure = result; throw stop }
                }
                responsesRequire(chunk.sequence == sequence && chunk.offset == offset, "Invalid transport chunk order")
                sequence++
                responsesRequire(chunk.bytes.size > 0 || chunk.final, "Empty nonfinal transport chunk")
                if (metadata == null) {
                    val initial = chunk.metadata ?: responsesReject("Missing HTTP stream metadata")
                    admit(initial)
                    metadata = initial
                } else chunk.metadata?.let { responsesRequire(it == metadata, "Conflicting HTTP stream metadata") }
                responsesRequire(chunk.bytes.size.toLong() <= ProviderTextLimits.MAX_WIRE_RESPONSE_BYTES - offset,
                    "Response byte limit exceeded")
                offset += chunk.bytes.size
                sse.accept(chunk.bytes.copyBytes()) { event ->
                    cancel.throwIfCancelled()
                    val result = state.event(event)
                    for (token in result.tokens) emit(Outcome.Success(token))
                    if (result.failure != null) { failure = result.failure; throw stop }
                    if (result.tokens.any { it.finishReason != null }) throw stop
                }
                if (chunk.final) sse.end()
            }
            sse.end()
        } catch (done: StopCollection) {
            if (done !== stop) throw done
        } catch (rejection: ResponsesCodecRejection) { failure = rejection.outcome }
        failure?.let { emit(it) }
    }

    // Like Flow.take's abort, terminal unwind is cancellation so transport finally blocks close
    // and transport error handlers do not mistake normal early completion for an I/O failure.
    private class StopCollection : CancellationException("Responses collection completed")

    private fun admit(metadata: HttpMetadata) {
        val retryAfter = (metadata.retryAfter as? Knowledge.Known)?.value
        responsesRequire(retryAfter == null || retryAfter >= kotlin.time.Duration.ZERO, "Invalid HTTP retry metadata")
        if (metadata.statusCode !in 200..299) throw ResponsesCodecRejection(Outcome.Failure(DomainFailure(
            FailureCode.TRANSPORT, "HTTP stream rejected", SnapshotList.empty(),
            HttpFailureMetadata(metadata.statusCode, metadata.retryAfter, metadata.chargeStatus))))
        val contentTypes = metadata.headers.filter { it.name.equals("Content-Type", true) }
        responsesRequire(contentTypes.isNotEmpty(), "Missing SSE content type")
        for (header in contentTypes) {
            val parts = header.value.split(';').map { it.trim() }
            responsesRequire(parts[0].equals("text/event-stream", true), "Invalid SSE content type")
            var charsetSeen = false
            for (parameter in parts.drop(1)) {
                val pair = parameter.split('=', limit = 2)
                responsesRequire(pair.size == 2 && pair[0].trim().equals("charset", true) && !charsetSeen,
                    "Unsupported SSE content type parameter")
                charsetSeen = true
                val charset = pair[1].trim()
                responsesRequire(charset.equals("utf-8", true) || charset.equals("\"utf-8\"", true), "Invalid SSE charset")
            }
        }
    }

    private class RequestWriter(private val cancel: CancelToken) : Writer() {
        val text = StringBuilder()
        private var bytes = 0
        override fun write(buffer: CharArray, offset: Int, count: Int) {
            for (index in offset until offset + count) {
                if (index and 1023 == 0) cancel.throwIfCancelled()
                val char = buffer[index]
                // Every source string was validated before Gson sees it. A surrogate pair is 4 bytes.
                val next = when { char.isHighSurrogate() -> 0; char.isLowSurrogate() -> 4
                    char.code < 128 -> 1; char.code < 2048 -> 2; else -> 3 }
                responsesRequire(bytes <= ProviderTextLimits.MAX_REQUEST_BYTES - next, "Encoded request byte limit exceeded", true)
                bytes += next
                text.append(char)
            }
        }
        override fun flush() = Unit
        override fun close() = Unit
    }

    private data class EventResult(val tokens: List<TokenChunk> = emptyList(), val failure: Outcome<Nothing>? = null)
    private class Part(val index: Long, val text: StringBuilder, var textDone: Boolean = false, var closed: Boolean = false)
    private class Item(val index: Long, val id: String, val phase: String?, val parts: MutableList<Part> = mutableListOf(),
        var status: String = "in_progress", var closed: Boolean = false)

    private class DecodeState(private val requestId: EntityId, private val cancel: CancelToken) {
        private var sequence = 0L
        private var eventSequence: Long? = null
        private var replyBytes = 0
        private var providerId: String? = null
        private val items = mutableListOf<Item>()
        private var partCount = 0
        // Keep unprojected counters too: a later decreasing snapshot must never preserve stale Known usage.
        private val counters = mutableMapOf<String, Long>()

        fun event(event: ResponsesSseEvent): EventResult {
            val root = ResponsesJson.parse(event.data, cancel)
            val type = string(root, "type")
            responsesRequire(event.name == "message" || event.name == type, "SSE event type mismatch")
            val next = number(root, "sequence_number")
            responsesRequire(eventSequence == null || next > eventSequence!!, "Invalid event sequence")
            eventSequence = next
            if (type == "error") return providerFailure()
            if (type == "response.created") {
                responsesRequire(providerId == null, "Duplicate response creation")
                val response = obj(root, "response")
                val id = string(response, "id")
                ResponsesJson.id(id, cancel)
                val status = string(response, "status")
                responsesRequire(status == "queued" || status == "in_progress", "Invalid initial response status")
                noMetadata(response, "error", "incomplete_details")
                emptyOutput(response)
                providerId = id
                readUsage(response)
                return EventResult()
            }
            responsesRequire(providerId != null, "Missing response creation")
            return when (type) {
                "response.in_progress" -> {
                    val response = response(root, "in_progress")
                    noMetadata(response, "error", "incomplete_details")
                    // Lifecycle snapshots are not a second source of generated content.
                    if (items.isNotEmpty()) responsesUnsupported()
                    emptyOutput(response)
                    readUsage(response)
                    EventResult()
                }
                "response.output_item.added" -> {
                    responsesRequire(items.lastOrNull()?.closed != false, "Output item interleaving")
                    val index = number(root, "output_index")
                    responsesRequire(items.size < 64 && (items.isEmpty() || index > items.last().index), "Invalid output item index")
                    val value = obj(root, "item")
                    val id = identity(value)
                    responsesRequire(items.none { it.id == id }, "Duplicate output item identity")
                    responsesRequire(string(value, "status") == "in_progress", "Invalid initial item status")
                    val content = array(value, "content")
                    if (content.size() != 0) responsesUnsupported()
                    items.add(Item(index, id, phase(value)))
                    EventResult()
                }
                "response.content_part.added" -> {
                    val item = current(root, true)
                    responsesRequire(item.parts.lastOrNull()?.closed != false, "Content part interleaving")
                    val index = number(root, "content_index")
                    responsesRequire(partCount < 64 && (item.parts.isEmpty() || index > item.parts.last().index), "Invalid content part index")
                    val value = obj(root, "part")
                    val text = partText(value)
                    val token = append(text)
                    item.parts.add(Part(index, StringBuilder(text)))
                    partCount++
                    result(token)
                }
                "response.output_text.delta" -> {
                    val part = currentPart(root)
                    responsesRequire(!part.textDone, "Text delta after text completion")
                    emptyMetadata(root)
                    val text = string(root, "delta")
                    val token = append(text)
                    part.text.append(text)
                    result(token)
                }
                "response.output_text.done" -> {
                    val part = currentPart(root)
                    responsesRequire(!part.textDone, "Duplicate text completion")
                    emptyMetadata(root)
                    responsesRequire(string(root, "text") == part.text.toString(), "Conflicting text snapshot")
                    part.textDone = true
                    EventResult()
                }
                "response.content_part.done" -> {
                    val part = currentPart(root)
                    responsesRequire(part.textDone, "Content completion before text completion")
                    responsesRequire(partText(obj(root, "part")) == part.text.toString(), "Conflicting content snapshot")
                    part.closed = true
                    EventResult()
                }
                "response.output_item.done" -> {
                    val item = current(root, false)
                    responsesRequire(item.parts.all { it.closed }, "Unclosed content part")
                    val value = obj(root, "item")
                    snapshotItem(value, item, false, true)
                    item.status = string(value, "status")
                    item.closed = true
                    EventResult()
                }
                "response.completed" -> {
                    val response = response(root, "completed")
                    noMetadata(response, "error", "incomplete_details")
                    responsesRequire(items.all { it.closed && it.status == "completed" && it.parts.all { part -> part.closed } }, "Unclosed completed output")
                    snapshotOutput(response, true)
                    readUsage(response)
                    result(token("", GenerationFinishReason.STOP))
                }
                "response.incomplete" -> {
                    val response = response(root, "incomplete")
                    noMetadata(response, "error")
                    val reason = when (string(obj(response, "incomplete_details"), "reason")) {
                        "max_output_tokens" -> GenerationFinishReason.TOKEN_LIMIT
                        "max_messages", "steered" -> GenerationFinishReason.INTERRUPTED
                        "content_filter" -> GenerationFinishReason.FAILED
                        else -> responsesUnsupported()
                    }
                    snapshotOutput(response, false)
                    readUsage(response)
                    result(token("", reason))
                }
                "response.failed" -> {
                    val response = response(root, "failed")
                    noMetadata(response, "incomplete_details")
                    snapshotOutput(response, false)
                    readUsage(response)
                    providerFailure()
                }
                else -> responsesUnsupported()
            }
        }

        private fun response(root: JsonObject, status: String): JsonObject {
            val value = obj(root, "response")
            responsesRequire(string(value, "id") == providerId, "Conflicting response identity")
            responsesRequire(string(value, "status") == status, "Conflicting response status")
            return value
        }
        private fun emptyOutput(value: JsonObject) {
            if (array(value, "output").size() != 0) responsesUnsupported()
        }
        private fun identity(value: JsonObject): String {
            if (string(value, "type") != "message" || string(value, "role") != "assistant") responsesUnsupported()
            return string(value, "id").also { ResponsesJson.id(it, cancel) }
        }
        private fun phase(value: JsonObject): String? = ResponsesJson.optional(value, "phase")?.let {
            ResponsesJson.string(it).also { phase -> if (phase != "final_answer") responsesUnsupported() }
        }
        private fun current(root: JsonObject, withId: Boolean): Item {
            val item = items.lastOrNull() ?: responsesReject("Missing output item")
            responsesRequire(!item.closed && number(root, "output_index") == item.index, "Invalid output item coordinates")
            if (withId) responsesRequire(string(root, "item_id") == item.id, "Conflicting output item identity")
            return item
        }
        private fun currentPart(root: JsonObject): Part {
            val item = current(root, true)
            val part = item.parts.lastOrNull() ?: responsesReject("Missing content part")
            responsesRequire(!part.closed && number(root, "content_index") == part.index, "Invalid content part coordinates")
            return part
        }
        private fun partText(value: JsonObject): String {
            if (string(value, "type") != "output_text") responsesUnsupported()
            emptyMetadata(value)
            return string(value, "text")
        }
        private fun emptyMetadata(value: JsonObject) {
            for (key in listOf("annotations", "logprobs")) ResponsesJson.optional(value, key)?.let {
                if (!it.isJsonArray || it.asJsonArray.size() != 0) responsesUnsupported()
            }
        }
        private fun noMetadata(value: JsonObject, vararg keys: String) {
            responsesRequire(keys.all { ResponsesJson.optional(value, it) == null }, "Contradictory response metadata")
        }
        private fun snapshotOutput(value: JsonObject, completed: Boolean) {
            if (!value.has("output")) {
                responsesRequire(!completed, "Missing final output snapshot")
                return
            }
            val output = array(value, "output") // Explicit null is invalid, including failed/incomplete.
            responsesRequire(output.size() == items.size, "Conflicting output snapshot length")
            for (index in items.indices) {
                cancel.throwIfCancelled()
                responsesRequire(output[index].isJsonObject, "Invalid output snapshot")
                snapshotItem(output[index].asJsonObject, items[index], completed, false)
            }
        }
        private fun snapshotItem(value: JsonObject, item: Item, completed: Boolean, closing: Boolean) {
            responsesRequire(identity(value) == item.id && phase(value) == item.phase, "Conflicting output item snapshot")
            val status = string(value, "status")
            responsesRequire(status in listOf("in_progress", "completed", "incomplete"), "Invalid output item status")
            when {
                completed -> responsesRequire(item.closed && status == "completed" && item.status == status, "Conflicting completed item status")
                closing -> responsesRequire(status == "completed" || status == "incomplete", "Invalid item completion status")
                item.closed -> responsesRequire(status == item.status, "Conflicting closed item status")
                else -> responsesRequire(status == "in_progress" || status == "incomplete", "Unobserved output item completion")
            }
            val parts = array(value, "content")
            responsesRequire(parts.size() == item.parts.size, "Conflicting content snapshot length")
            for (index in item.parts.indices) {
                cancel.throwIfCancelled()
                responsesRequire(parts[index].isJsonObject, "Invalid content snapshot")
                responsesRequire(partText(parts[index].asJsonObject) == item.parts[index].text.toString(), "Conflicting content snapshot")
            }
        }
        private fun readUsage(response: JsonObject) {
            val value = ResponsesJson.optional(response, "usage") ?: return
            responsesRequire(value.isJsonObject, "Invalid usage object")
            val usage = value.asJsonObject
            for (name in listOf("input_tokens", "output_tokens", "total_tokens")) counter(name, ResponsesJson.optional(usage, name))
            for ((name, fields) in listOf("input_tokens_details" to listOf("cached_tokens", "cache_write_tokens"),
                "output_tokens_details" to listOf("reasoning_tokens"))) {
                ResponsesJson.optional(usage, name)?.let { details ->
                    responsesRequire(details.isJsonObject, "Invalid usage details")
                    for (field in fields) counter(field, ResponsesJson.optional(details.asJsonObject, field))
                }
            }
        }
        private fun counter(name: String, value: JsonElement?) {
            if (value == null) return
            val next = ResponsesJson.nonnegativeLong(value)
            responsesRequire(counters[name] == null || next >= counters.getValue(name), "Contradictory cumulative token usage")
            counters[name] = next
        }
        private fun append(text: String): TokenChunk? {
            replyBytes += ResponsesJson.textBytes(text, ProviderTextLimits.MAX_REPLY_BYTES - replyBytes, cancel)
            return if (text.isEmpty()) null else token(text, null)
        }
        private fun providerFailure(): EventResult = EventResult(
            if (providerId == null && counters.isEmpty()) emptyList() else listOf(token("", null)),
            Outcome.Failure(DomainFailure(FailureCode.CORRUPT_DATA, "Provider reported a streamed error", SnapshotList.empty())))
        private fun result(token: TokenChunk?) = EventResult(if (token == null) emptyList() else listOf(token))
        private fun token(text: String, reason: GenerationFinishReason?): TokenChunk {
            // H08: cache-write/reasoning/total and phase have no separate durable projection; costs remain unknown.
            val usage = Knowledge.Known(Usage(known(counters["input_tokens"]), known(counters["output_tokens"]),
                known(counters["cached_tokens"]), unknown(), known(providerId)))
            return TokenChunk(requestId, sequence++, text, SnapshotList.empty(), reason, usage)
        }
        private fun required(value: JsonObject, name: String): JsonElement = value.get(name)
            ?.takeUnless { it.isJsonNull } ?: responsesReject("Missing required event field")
        private fun obj(value: JsonObject, name: String): JsonObject {
            val member = required(value, name)
            responsesRequire(member.isJsonObject, "Invalid event object")
            return member.asJsonObject
        }
        private fun array(value: JsonObject, name: String): JsonArray {
            val member = required(value, name)
            responsesRequire(member.isJsonArray, "Invalid event array")
            return member.asJsonArray
        }
        private fun string(value: JsonObject, name: String): String = ResponsesJson.string(required(value, name))
        private fun number(value: JsonObject, name: String): Long = ResponsesJson.nonnegativeLong(required(value, name))
        private fun <T : Any> known(value: T?): Knowledge<T> = if (value == null) unknown() else Knowledge.Known(value)
        private fun <T> unknown(): Knowledge<T> = Knowledge.Unknown("Not reported by provider", SnapshotList.empty())
    }
}
