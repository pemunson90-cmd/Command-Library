package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.*
import com.google.gson.stream.JsonWriter
import java.io.Writer
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/** Pure, bounded manual-model Chat Completions text codec. No credentials or transport access. */
public class ChatCompletionsWireCodec {
    public fun encode(profile: ProviderProfile, request: GenerationRequest, cancel: CancelToken): Outcome<Request> {
        cancel.throwIfCancelled()
        return try {
            if (profile.dialect != ProviderDialect.OPENAI_CHAT || profile.apiVersion is Knowledge.Known ||
                profile.extraHeaders.isNotEmpty()) chatUnsupported()
            val normalized = when (val outcome = ProviderProfilePolicy.normalize(profile)) {
                is Outcome.Success -> outcome.value
                is Outcome.Failure -> return outcome
                is Outcome.Unavailable -> return outcome
                is Outcome.Unknown -> return outcome
            }
            ChatCompletionsJson.id(request.id.value, cancel, true)
            ChatCompletionsJson.id(normalized.selectedModelId, cancel, true)
            chatRequire(request.messages.size in 1..ProviderTextLimits.MAX_MESSAGES, "Invalid message count", true)
            chatRequire(request.stopSequences.size <= ProviderTextLimits.MAX_STOP_SEQUENCES, "Stop sequence limit exceeded", true)
            val root = JsonObject()
            root.addProperty("model", normalized.selectedModelId)
            root.addProperty("stream", true)
            val messages = JsonArray()
            var contentBytes = 0
            for (message in request.messages) {
                cancel.throwIfCancelled()
                if (message.role == MessageRole.TOOL || message.toolCallId != null) chatUnsupported()
                contentBytes += ChatCompletionsJson.textBytes(message.text,
                    ProviderTextLimits.MAX_MESSAGE_BYTES - contentBytes, cancel, true)
                messages.add(JsonObject().apply {
                    addProperty("role", when (message.role) {
                        MessageRole.SYSTEM -> "system"
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        MessageRole.TOOL -> chatUnsupported()
                    })
                    addProperty("content", message.text)
                })
            }
            root.add("messages", messages)
            request.maxTokens?.let { root.addProperty("max_tokens", it) }
            request.temperature?.let { ChatCompletionsJson.decimal(it, true); root.addProperty("temperature", it) }
            request.topP?.let { ChatCompletionsJson.decimal(it, true); root.addProperty("top_p", it) }
            request.seed?.let { root.addProperty("seed", it) }
            if (request.stopSequences.isNotEmpty()) {
                val stops = JsonArray()
                for (stop in request.stopSequences) {
                    chatRequire(stop.isNotEmpty(), "Empty stop sequence", true)
                    ChatCompletionsJson.textBytes(stop, ProviderTextLimits.MAX_ID_BYTES, cancel, true)
                    stops.add(stop)
                }
                root.add("stop", stops)
            }
            val output = RequestWriter(cancel)
            JsonWriter(output).use { writer -> GsonBuilder().disableHtmlEscaping().create().toJson(root, writer) }
            cancel.throwIfCancelled()
            Outcome.Success(Request(HttpMethod.POST,
                ResourceUri(normalized.baseUri!!.value.trimEnd('/') + "/chat/completions"),
                SnapshotList.of(HttpHeader("Content-Type", "application/json"), HttpHeader("Accept", "text/event-stream")),
                SnapshotBytes(output.text.toString().toByteArray(Charsets.UTF_8))))
        } catch (rejection: ChatCodecRejection) { rejection.outcome }
    }

    public fun decode(requestId: EntityId, chunks: Flow<Outcome<ByteChunk>>, cancel: CancelToken): Flow<Outcome<TokenChunk>> = flow {
        val stop = StopCollection()
        var failure: Outcome<Nothing>? = null
        try {
            cancel.throwIfCancelled()
            ChatCompletionsJson.id(requestId.value, cancel, true)
            val state = DecodeState(requestId, cancel)
            val sse = ChatCompletionsSse(cancel)
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
                chatRequire(chunk.sequence == sequence && chunk.offset == offset, "Invalid transport chunk order")
                sequence++
                chatRequire(chunk.bytes.size > 0 || chunk.final, "Empty nonfinal transport chunk")
                if (metadata == null) {
                    val initial = chunk.metadata ?: chatReject("Missing HTTP stream metadata")
                    admit(initial)
                    metadata = initial
                } else chunk.metadata?.let { chatRequire(it == metadata, "Conflicting HTTP stream metadata") }
                chatRequire(chunk.bytes.size.toLong() <= ProviderTextLimits.MAX_WIRE_RESPONSE_BYTES - offset,
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
        } catch (rejection: ChatCodecRejection) { failure = rejection.outcome }
        failure?.let { emit(it) }
    }

    // Like Flow.take's abort, terminal unwind is cancellation so transport finally blocks close
    // and transport error handlers do not mistake normal early completion for an I/O failure.
    private class StopCollection : CancellationException("Chat Completions collection completed")

    private fun admit(metadata: HttpMetadata) {
        val retryAfter = (metadata.retryAfter as? Knowledge.Known)?.value
        chatRequire(retryAfter == null || retryAfter >= kotlin.time.Duration.ZERO, "Invalid HTTP retry metadata")
        if (metadata.statusCode !in 200..299) throw ChatCodecRejection(Outcome.Failure(DomainFailure(
            FailureCode.TRANSPORT, "HTTP stream rejected", SnapshotList.empty(),
            HttpFailureMetadata(metadata.statusCode, metadata.retryAfter, metadata.chargeStatus))))
        val contentTypes = metadata.headers.filter { it.name.equals("Content-Type", true) }
        chatRequire(contentTypes.isNotEmpty(), "Missing SSE content type")
        for (header in contentTypes) {
            val parts = header.value.split(';').map { it.trim() }
            chatRequire(parts[0].equals("text/event-stream", true), "Invalid SSE content type")
            var charsetSeen = false
            for (parameter in parts.drop(1)) {
                val pair = parameter.split('=', limit = 2)
                chatRequire(pair.size == 2 && pair[0].trim().equals("charset", true) && !charsetSeen,
                    "Unsupported SSE content type parameter")
                charsetSeen = true
                val charset = pair[1].trim()
                chatRequire(charset.equals("utf-8", true) || charset.equals("\"utf-8\"", true), "Invalid SSE charset")
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
                chatRequire(bytes <= ProviderTextLimits.MAX_REQUEST_BYTES - next, "Encoded request byte limit exceeded", true)
                bytes += next
                text.append(char)
            }
        }
        override fun flush() = Unit
        override fun close() = Unit
    }

    private class DecodeState(private val requestId: EntityId, private val cancel: CancelToken) {
        private var sequence = 0L
        private var replyBytes = 0
        private var finish: GenerationFinishReason? = null
        private var providerId: String? = null
        private var input: Long? = null
        private var output: Long? = null
        private var cached: Long? = null
        private var total: Long? = null

        fun event(text: String): TokenChunk? {
            if (text == "[DONE]") {
                val reason = finish ?: chatReject("Terminal event missing finish reason")
                return token("", reason)
            }
            val root = ChatCompletionsJson.parse(text, cancel)
            if (ChatCompletionsJson.optional(root, "error") != null) chatReject("Provider reported a streamed error")
            unsupportedFields(root)
            var validMetadata = false
            ChatCompletionsJson.optional(root, "id")?.let {
                val id = ChatCompletionsJson.string(it)
                ChatCompletionsJson.id(id, cancel)
                chatRequire(providerId == null || providerId == id, "Conflicting provider request identity")
                providerId = id
                validMetadata = true
            }
            ChatCompletionsJson.optional(root, "object")?.let {
                chatRequire(ChatCompletionsJson.string(it) == "chat.completion.chunk", "Invalid completion object")
                validMetadata = true
            }
            ChatCompletionsJson.optional(root, "model")?.let {
                ChatCompletionsJson.id(ChatCompletionsJson.string(it), cancel)
                validMetadata = true
            }
            ChatCompletionsJson.optional(root, "created")?.let {
                ChatCompletionsJson.nonnegativeLong(it)
                validMetadata = true
            }
            val usage = ChatCompletionsJson.optional(root, "usage")
            if (usage != null) readUsage(usage)
            val choices = root.get("choices") ?: chatReject("Missing completion choices")
            chatRequire(choices.isJsonArray && choices.asJsonArray.size() <= 1, "Expected one completion choice")
            if (choices.asJsonArray.size() == 0) {
                chatRequire(usage != null || validMetadata, "Empty completion event")
                return null
            }
            val choiceValue = choices.asJsonArray[0]
            chatRequire(choiceValue.isJsonObject, "Invalid completion choice")
            val choice = choiceValue.asJsonObject
            val index = choice.get("index") ?: chatReject("Missing completion index")
            chatRequire(ChatCompletionsJson.nonnegativeLong(index) == 0L, "Unsupported completion index")
            unsupportedFields(choice)
            val deltaValue = ChatCompletionsJson.optional(choice, "delta")
            val delta = deltaValue?.let {
                chatRequire(it.isJsonObject, "Invalid text delta")
                it.asJsonObject
            }
            if (delta != null) {
                unsupportedFields(delta)
                ChatCompletionsJson.optional(delta, "role")?.let {
                    if (ChatCompletionsJson.string(it) != "assistant") chatUnsupported()
                }
            }
            val content = delta?.let { ChatCompletionsJson.optional(it, "content") }?.let {
                if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString) chatUnsupported()
                ChatCompletionsJson.string(it)
            }.orEmpty()
            chatRequire(finish == null || content.isEmpty(), "Text received after finish reason")
            val reasonValue = ChatCompletionsJson.optional(choice, "finish_reason")
            if (reasonValue != null) {
                val reason = when (ChatCompletionsJson.string(reasonValue)) {
                    "stop" -> GenerationFinishReason.STOP
                    "length" -> GenerationFinishReason.TOKEN_LIMIT
                    "content_filter" -> GenerationFinishReason.FAILED
                    else -> chatUnsupported()
                }
                chatRequire(finish == null || finish == reason, "Conflicting finish reason")
                finish = reason
            }
            chatRequire(delta != null || reasonValue != null, "Missing text delta")
            if (content.isEmpty()) return null
            replyBytes += ChatCompletionsJson.textBytes(content, ProviderTextLimits.MAX_REPLY_BYTES - replyBytes, cancel)
            return token(content, null)
        }

        private fun unsupportedFields(obj: JsonObject) {
            for (key in listOf("tool_calls", "function_call", "refusal")) {
                val value = ChatCompletionsJson.optional(obj, key) ?: continue
                val empty = when (key) {
                    "tool_calls" -> value.isJsonArray && value.asJsonArray.size() == 0
                    "function_call" -> value.isJsonObject && value.asJsonObject.size() == 0
                    "refusal" -> value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.isEmpty()
                    else -> false
                }
                if (!empty) chatUnsupported()
            }
        }

        private fun readUsage(value: JsonElement) {
            chatRequire(value.isJsonObject, "Invalid usage object")
            val obj = value.asJsonObject
            input = update(input, ChatCompletionsJson.optional(obj, "prompt_tokens"))
            output = update(output, ChatCompletionsJson.optional(obj, "completion_tokens"))
            total = update(total, ChatCompletionsJson.optional(obj, "total_tokens"))
            ChatCompletionsJson.optional(obj, "prompt_tokens_details")?.let {
                chatRequire(it.isJsonObject, "Invalid token details")
                cached = update(cached, ChatCompletionsJson.optional(it.asJsonObject, "cached_tokens"))
            }
            if (input != null && output != null && total != null) {
                chatRequire(input!! <= Long.MAX_VALUE - output!! && input!! + output!! == total,
                    "Contradictory token usage")
            }
        }

        private fun update(previous: Long?, value: JsonElement?): Long? {
            if (value == null) return previous
            val next = ChatCompletionsJson.nonnegativeLong(value)
            chatRequire(previous == null || next >= previous, "Contradictory cumulative token usage")
            return next
        }

        private fun token(text: String, reason: GenerationFinishReason?): TokenChunk {
            val usage = if (providerId == null && input == null && output == null && cached == null) unknown<Usage>() else
                Knowledge.Known(Usage(known(input), known(output), known(cached), unknown(), known(providerId)))
            return TokenChunk(requestId, sequence++, text, SnapshotList.empty(), reason, usage)
        }

        private fun <T : Any> known(value: T?): Knowledge<T> = if (value == null) unknown() else Knowledge.Known(value)
        private fun <T> unknown(): Knowledge<T> = Knowledge.Unknown("Not reported by provider", SnapshotList.empty())
    }
}
