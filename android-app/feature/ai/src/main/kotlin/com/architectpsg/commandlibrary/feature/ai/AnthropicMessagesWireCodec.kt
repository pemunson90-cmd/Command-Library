package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.*
import com.google.gson.stream.JsonWriter
import java.io.Writer
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/** Pure, bounded manual-model Anthropic Messages text codec. No credentials or transport access. */
public class AnthropicMessagesWireCodec {
    public fun encode(profile: ProviderProfile, request: GenerationRequest, cancel: CancelToken): Outcome<Request> {
        cancel.throwIfCancelled()
        return try {
            if (profile.dialect != ProviderDialect.ANTHROPIC || profile.extraHeaders.isNotEmpty() ||
                request.temperature != null || request.topP != null || request.seed != null) anthropicUnsupported()
            val normalized = when (val outcome = ProviderProfilePolicy.normalize(profile)) {
                is Outcome.Success -> outcome.value
                is Outcome.Failure -> return outcome
                is Outcome.Unavailable -> return outcome
                is Outcome.Unknown -> return outcome
            }
            anthropicRequire(normalized == profile, "Provider profile must be canonical", true)
            val version = (profile.apiVersion as? Knowledge.Known)?.value
                ?: anthropicReject("Explicit API version is required", true)
            anthropicRequire(validVersion(version), "Invalid API version", true)
            val maxTokens = request.maxTokens ?: anthropicReject("Explicit output token limit is required", true)
            anthropicRequire(maxTokens > 0, "Invalid output token limit", true)
            AnthropicMessagesJson.id(request.id.value, cancel, true)
            AnthropicMessagesJson.id(normalized.selectedModelId, cancel, true)
            anthropicRequire(request.messages.size in 1..ProviderTextLimits.MAX_MESSAGES, "Invalid message count", true)
            anthropicRequire(request.stopSequences.size <= ProviderTextLimits.MAX_STOP_SEQUENCES, "Stop sequence limit exceeded", true)
            val root = JsonObject()
            root.addProperty("model", normalized.selectedModelId)
            root.addProperty("stream", true)
            val messages = JsonArray()
            var contentBytes = 0
            for ((index, message) in request.messages.withIndex()) {
                cancel.throwIfCancelled()
                if (message.role == MessageRole.TOOL || message.toolCallId != null) anthropicUnsupported()
                contentBytes += AnthropicMessagesJson.textBytes(message.text,
                    ProviderTextLimits.MAX_MESSAGE_BYTES - contentBytes, cancel, true)
                if (message.role == MessageRole.SYSTEM) {
                    if (index != 0) anthropicUnsupported()
                    root.addProperty("system", message.text)
                } else messages.add(JsonObject().apply {
                    addProperty("role", when (message.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        else -> anthropicUnsupported()
                    })
                    addProperty("content", message.text)
                })
            }
            anthropicRequire(messages.size() > 0, "At least one conversation message is required", true)
            root.add("messages", messages)
            root.addProperty("max_tokens", maxTokens)
            if (request.stopSequences.isNotEmpty()) {
                val stops = JsonArray()
                for (stop in request.stopSequences) {
                    anthropicRequire(stop.isNotEmpty(), "Empty stop sequence", true)
                    AnthropicMessagesJson.textBytes(stop, ProviderTextLimits.MAX_ID_BYTES, cancel, true)
                    stops.add(stop)
                }
                root.add("stop_sequences", stops)
            }
            val output = RequestWriter(cancel)
            JsonWriter(output).use { writer -> GsonBuilder().disableHtmlEscaping().create().toJson(root, writer) }
            cancel.throwIfCancelled()
            Outcome.Success(Request(HttpMethod.POST,
                ResourceUri(normalized.baseUri!!.value.trimEnd('/') + "/messages"),
                SnapshotList.of(HttpHeader("Content-Type", "application/json"), HttpHeader("Accept", "text/event-stream"),
                    HttpHeader("anthropic-version", version)),
                SnapshotBytes(output.text.toString().toByteArray(Charsets.UTF_8))))
        } catch (rejection: AnthropicCodecRejection) { rejection.outcome }
    }

    public fun decode(requestId: EntityId, chunks: Flow<Outcome<ByteChunk>>, cancel: CancelToken): Flow<Outcome<TokenChunk>> = flow {
        val stop = StopCollection()
        var failure: Outcome<Nothing>? = null
        try {
            cancel.throwIfCancelled()
            AnthropicMessagesJson.id(requestId.value, cancel, true)
            val state = DecodeState(requestId, cancel)
            val sse = AnthropicMessagesSse(cancel)
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
                anthropicRequire(chunk.sequence == sequence && chunk.offset == offset, "Invalid transport chunk order")
                sequence++
                anthropicRequire(chunk.bytes.size > 0 || chunk.final, "Empty nonfinal transport chunk")
                if (metadata == null) {
                    val initial = chunk.metadata ?: anthropicReject("Missing HTTP stream metadata")
                    admit(initial)
                    metadata = initial
                } else chunk.metadata?.let { anthropicRequire(it == metadata, "Conflicting HTTP stream metadata") }
                anthropicRequire(chunk.bytes.size.toLong() <= ProviderTextLimits.MAX_WIRE_RESPONSE_BYTES - offset,
                    "Response byte limit exceeded")
                offset += chunk.bytes.size
                sse.accept(chunk.bytes.copyBytes()) { event ->
                    cancel.throwIfCancelled()
                    val token = state.event(event)
                    if (token != null) {
                        emit(Outcome.Success(token))
                        if (token.finishReason != null) throw stop
                    }
                }
                if (chunk.final) sse.end()
            }
            sse.end()
        } catch (done: StopCollection) {
            if (done !== stop) throw done
        } catch (rejection: AnthropicCodecRejection) { failure = rejection.outcome }
        failure?.let { emit(it) }
    }

    // Like Flow.take's abort, terminal unwind is cancellation so transport finally blocks close
    // and transport error handlers do not mistake normal early completion for an I/O failure.
    private class StopCollection : CancellationException("Anthropic Messages collection completed")

    private fun admit(metadata: HttpMetadata) {
        val retryAfter = (metadata.retryAfter as? Knowledge.Known)?.value
        anthropicRequire(retryAfter == null || retryAfter >= kotlin.time.Duration.ZERO, "Invalid HTTP retry metadata")
        if (metadata.statusCode !in 200..299) throw AnthropicCodecRejection(Outcome.Failure(DomainFailure(
            FailureCode.TRANSPORT, "HTTP stream rejected", SnapshotList.empty(),
            HttpFailureMetadata(metadata.statusCode, metadata.retryAfter, metadata.chargeStatus))))
        val contentTypes = metadata.headers.filter { it.name.equals("Content-Type", true) }
        anthropicRequire(contentTypes.isNotEmpty(), "Missing SSE content type")
        for (header in contentTypes) {
            val parts = header.value.split(';').map { it.trim() }
            anthropicRequire(parts[0].equals("text/event-stream", true), "Invalid SSE content type")
            var charsetSeen = false
            for (parameter in parts.drop(1)) {
                val pair = parameter.split('=', limit = 2)
                anthropicRequire(pair.size == 2 && pair[0].trim().equals("charset", true) && !charsetSeen,
                    "Unsupported SSE content type parameter")
                charsetSeen = true
                val charset = pair[1].trim()
                anthropicRequire(charset.equals("utf-8", true) || charset.equals("\"utf-8\"", true), "Invalid SSE charset")
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
                anthropicRequire(bytes <= ProviderTextLimits.MAX_REQUEST_BYTES - next, "Encoded request byte limit exceeded", true)
                bytes += next
                text.append(char)
            }
        }
        override fun flush() = Unit
        override fun close() = Unit
    }

    private fun validVersion(value: String): Boolean {
        if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value) || value.startsWith("0000")) return false
        return try { java.time.LocalDate.parse(value).toString() == value } catch (_: java.time.DateTimeException) { false }
    }

    private class DecodeState(private val requestId: EntityId, private val cancel: CancelToken) {
        private var sequence = 0L
        private var replyBytes = 0
        private var started = false
        private var nextIndex = 0L
        private var openIndex: Long? = null
        private var deltaPhase = false
        private var finish: GenerationFinishReason? = null
        private var stopReason: String? = null
        private var stopSequence: String? = null
        private var providerId: String? = null
        private var input: Long? = null
        private var creation: Long? = null
        private var cached: Long? = null
        private var output: Long? = null

        fun event(event: AnthropicSseEvent): TokenChunk? {
            val root = AnthropicMessagesJson.parse(event.data, cancel)
            val type = string(root, "type")
            anthropicRequire(event.name == type, "SSE event type mismatch")
            if (type == "ping") { fields(root, "type"); return null }
            if (type == "error") anthropicReject("Provider reported a streamed error")
            if (type == "message_start") {
                anthropicRequire(!started, "Duplicate message start")
                fields(root, "type", "message")
                val message = obj(root, "message")
                fields(message, "id", "type", "role", "model", "content", "usage", "stop_reason", "stop_sequence",
                    "container", "diagnostics", "stop_details")
                unsupportedNonNull(message, "container", "diagnostics", "stop_details")
                anthropicRequire(string(message, "type") == "message", "Invalid message object")
                if (string(message, "role") != "assistant") anthropicUnsupported()
                providerId = string(message, "id").also { AnthropicMessagesJson.id(it, cancel) }
                AnthropicMessagesJson.id(string(message, "model"), cancel)
                val content = required(message, "content")
                anthropicRequire(content.isJsonArray && content.asJsonArray.size() == 0, "Initial content must be empty")
                anthropicRequire(AnthropicMessagesJson.optional(message, "stop_reason") == null &&
                    AnthropicMessagesJson.optional(message, "stop_sequence") == null, "Initial finish reason is unsupported")
                readUsage(required(message, "usage"))
                started = true
                return null
            }
            anthropicRequire(started, "Missing message start")
            return when (type) {
                "content_block_start" -> {
                    anthropicRequire(!deltaPhase && openIndex == null, "Invalid content block order")
                    fields(root, "type", "index", "content_block")
                    val index = index(root)
                    anthropicRequire(index == nextIndex, "Invalid content block index")
                    val block = obj(root, "content_block")
                    fields(block, "type", "text", "citations")
                    if (string(block, "type") != "text") anthropicUnsupported()
                    AnthropicMessagesJson.optional(block, "citations")?.let {
                        if (!it.isJsonArray || it.asJsonArray.size() != 0) anthropicUnsupported()
                    }
                    openIndex = index
                    text(string(block, "text"))
                }
                "content_block_delta" -> {
                    anthropicRequire(!deltaPhase && openIndex != null && index(root) == openIndex,
                        "Invalid content delta order")
                    fields(root, "type", "index", "delta")
                    val delta = obj(root, "delta")
                    fields(delta, "type", "text")
                    if (string(delta, "type") != "text_delta") anthropicUnsupported()
                    text(string(delta, "text"))
                }
                "content_block_stop" -> {
                    fields(root, "type", "index")
                    anthropicRequire(!deltaPhase && openIndex != null && index(root) == openIndex,
                        "Invalid content stop order")
                    openIndex = null
                    nextIndex++
                    null
                }
                "message_delta" -> {
                    fields(root, "type", "delta", "usage")
                    anthropicRequire(openIndex == null, "Unclosed content block")
                    val delta = obj(root, "delta")
                    fields(delta, "stop_reason", "stop_sequence", "container", "stop_details")
                    unsupportedNonNull(delta, "container", "stop_details")
                    val reasonValue = AnthropicMessagesJson.optional(delta, "stop_reason")
                    if (reasonValue != null) {
                        val reason = AnthropicMessagesJson.string(reasonValue)
                        anthropicRequire(stopReason == null || stopReason == reason, "Conflicting finish reason")
                        finish = when (reason) {
                            "end_turn", "stop_sequence" -> GenerationFinishReason.STOP
                            "max_tokens", "model_context_window_exceeded" -> GenerationFinishReason.TOKEN_LIMIT
                            "refusal" -> GenerationFinishReason.FAILED
                            else -> anthropicUnsupported()
                        }
                        stopReason = reason
                    }
                    AnthropicMessagesJson.optional(delta, "stop_sequence")?.let {
                        anthropicRequire(stopReason == "stop_sequence", "Contradictory stop sequence")
                        val value = AnthropicMessagesJson.string(it)
                        anthropicRequire(value.isNotEmpty(), "Empty stop sequence")
                        AnthropicMessagesJson.textBytes(value, ProviderTextLimits.MAX_ID_BYTES, cancel)
                        anthropicRequire(stopSequence == null || stopSequence == value, "Conflicting stop sequence")
                        stopSequence = value
                    }
                    readUsage(required(root, "usage"))
                    deltaPhase = true
                    null
                }
                "message_stop" -> {
                    fields(root, "type")
                    anthropicRequire(openIndex == null && deltaPhase, "Invalid terminal event order")
                    token("", finish ?: anthropicReject("Terminal event missing finish reason"))
                }
                else -> anthropicUnsupported()
            }
        }

        private fun required(value: JsonObject, name: String): JsonElement =
            AnthropicMessagesJson.optional(value, name) ?: anthropicReject("Missing required event field")
        private fun obj(value: JsonObject, name: String): JsonObject {
            val member = required(value, name)
            anthropicRequire(member.isJsonObject, "Invalid event object")
            return member.asJsonObject
        }
        private fun string(value: JsonObject, name: String): String = AnthropicMessagesJson.string(required(value, name))
        private fun index(value: JsonObject): Long = AnthropicMessagesJson.nonnegativeLong(required(value, "index"))
        private fun fields(value: JsonObject, vararg names: String) {
            if (value.keySet().any { it !in names }) anthropicUnsupported()
        }
        private fun unsupportedNonNull(value: JsonObject, vararg names: String) {
            if (names.any { AnthropicMessagesJson.optional(value, it) != null }) anthropicUnsupported()
        }
        private fun text(value: String): TokenChunk? {
            replyBytes += AnthropicMessagesJson.textBytes(value, ProviderTextLimits.MAX_REPLY_BYTES - replyBytes, cancel)
            return if (value.isEmpty()) null else token(value, null)
        }
        private fun readUsage(value: JsonElement) {
            anthropicRequire(value.isJsonObject, "Invalid usage object")
            val obj = value.asJsonObject
            // Only authoritative component counters project to Usage. No prices or inferred zeros.
            unsupportedNonNull(obj, "server_tool_use")
            val nextInput = update(input, AnthropicMessagesJson.optional(obj, "input_tokens"))
            val nextCreation = update(creation, AnthropicMessagesJson.optional(obj, "cache_creation_input_tokens"))
            val nextCached = update(cached, AnthropicMessagesJson.optional(obj, "cache_read_input_tokens"))
            val nextOutput = update(output, AnthropicMessagesJson.optional(obj, "output_tokens"))
            if (nextInput != null && nextCreation != null && nextCached != null) {
                // Reject the entire snapshot: Unknown would let durable merging retain a stale known total.
                anthropicRequire(nextInput <= Long.MAX_VALUE - nextCreation &&
                    nextInput + nextCreation <= Long.MAX_VALUE - nextCached, "Input token sum overflow")
            }
            input = nextInput
            creation = nextCreation
            cached = nextCached
            output = nextOutput
        }
        private fun update(previous: Long?, value: JsonElement?): Long? {
            if (value == null) return previous
            val next = AnthropicMessagesJson.nonnegativeLong(value)
            anthropicRequire(previous == null || next >= previous, "Contradictory cumulative token usage")
            return next
        }
        private fun token(text: String, reason: GenerationFinishReason?): TokenChunk {
            // H08 limitation: separate uncached and cache-creation components are not durable fields.
            val total = if (input != null && creation != null && cached != null)
                input!! + creation!! + cached!! else null
            val usage = Knowledge.Known(Usage(known(total), known(output), known(cached), unknown(), known(providerId)))
            return TokenChunk(requestId, sequence++, text, SnapshotList.empty(), reason, usage)
        }
        private fun <T : Any> known(value: T?): Knowledge<T> = if (value == null) unknown() else Knowledge.Known(value)
        private fun <T> unknown(): Knowledge<T> = Knowledge.Unknown("Not reported by provider", SnapshotList.empty())
    }
}
