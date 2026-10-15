package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * One explicit attempt. Never recovers, retries, or dispatches a durable existing attempt.
 * The caller supplies fresh IDs and explicit retry/charge consent; after any unacknowledged result
 * it reloads the original ID before authorizing another attempt. NOT_FOUND is not a replay receipt.
 */
public class DurableProviderGeneration(private val client: ProviderTextClient) {
    public suspend fun run(
        history: LocalAiHistory,
        epoch: EntityId,
        profile: ProviderProfile,
        conversation: NewConversation,
        input: NewHistoryTurn,
        request: GenerationRequest,
        cancel: CancelToken,
        onProgress: suspend (Turn) -> Unit,
    ): Outcome<Turn> {
        active(cancel)
        if (!validInput(epoch, profile, conversation, input, request)) return invalid()
        // A read is not an exactly-once receipt: append must still reserve the original ID.
        when (val previous = history.getTurn(input.id, cancel)) {
            is Outcome.Success -> return failure(FailureCode.STALE_REVISION, "Generation attempt already exists")
            is Outcome.Unavailable -> if (previous.reason != UnavailableReason.NOT_FOUND) return previous
            is Outcome.Failure -> return previous
            is Outcome.Unknown -> return previous
        }
        val state = Attempt(history, epoch, conversation, input)
        try {
            val parent = when (val found = history.getConversation(conversation.id, cancel)) {
                is Outcome.Success -> found.value
                is Outcome.Unavailable -> {
                    if (found.reason != UnavailableReason.NOT_FOUND) return found
                    state.uncertain = true
                    when (val created = history.createConversation(conversation, cancel)) {
                        is Outcome.Success -> { state.uncertain = false; created.value }
                        else -> return state.uncertainty()
                    }
                }
                is Outcome.Failure -> return found
                is Outcome.Unknown -> return found
            }
            if (parent.id != conversation.id || parent.deckId != conversation.deckId ||
                parent.mode != conversation.mode || parent.title != conversation.title) return invalid()
            active(cancel)
            state.uncertain = true
            val appended = history.appendTurn(conversation.id, parent.revision, input, cancel)
            if (appended !is Outcome.Success) return state.uncertainty()
            state.turn = appended.value
            if (!state.matches(appended.value, HistoryProgress(TurnStatus.PENDING, "", appended.value.usage)) ||
                appended.value.revision != Revision(1) || appended.value.usage !is Knowledge.Unknown) return state.uncertainty()
            state.uncertain = false
            onProgress(appended.value)
            active(cancel)
            var sequence = 0L
            var terminal: GenerationFinishReason? = null
            var streamError: Outcome<Nothing>? = null
            client.stream(profile, request, cancel).takeWhile { result ->
                active(cancel)
                if (result !is Outcome.Success) {
                    streamError = when (result) {
                        is Outcome.Failure -> result
                        is Outcome.Unavailable -> result
                        is Outcome.Unknown -> result
                        is Outcome.Success -> error("Unreachable")
                    }
                    false
                } else {
                    val chunk = result.value
                    val bytes = utf8Length(chunk.text, AiHistoryLimits.MAX_REPLY_BYTES - state.replyBytes)
                    val usage = mergeUsage(state.usage, chunk.usage)
                    if (chunk.requestId != input.id || chunk.sequence != sequence || sequence == Long.MAX_VALUE ||
                        chunk.tokenIds.isNotEmpty() || bytes == null || usage == null) {
                        streamError = failure(FailureCode.CORRUPT_DATA, "Invalid provider stream")
                        false
                    } else {
                        sequence++
                        state.reply.append(chunk.text)
                        state.replyBytes += bytes
                        state.usage = usage
                        terminal = chunk.finishReason
                        // takeWhile aborts the upstream before terminal persistence or callbacks.
                        if (terminal != null) false else {
                            val progress = HistoryProgress(TurnStatus.STREAMING, state.reply.toString(), state.usage)
                            if (!state.checkpoint(progress, cancel)) {
                                streamError = state.uncertainty()
                                false
                            } else { onProgress(state.turn!!); true }
                        }
                    }
                }
            }.collect()
            active(cancel)
            if (state.uncertain) return state.uncertainty()
            if (streamError != null || terminal == null) {
                val error = streamError ?: failure(FailureCode.TRANSPORT, "Provider stream ended without a terminal response")
                if (!state.finish(TurnStatus.FAILED)) return state.uncertainty()
                onProgress(state.turn!!)
                return error
            }
            val status = when (terminal) {
                GenerationFinishReason.STOP -> TurnStatus.COMPLETE
                GenerationFinishReason.FAILED -> TurnStatus.FAILED
                else -> TurnStatus.INTERRUPTED
            }
            if (!state.finish(status)) return state.uncertainty()
            onProgress(state.turn!!)
            return Outcome.Success(state.turn!!)
        } catch (stopped: CancellationException) {
            state.interrupt()
            throw stopped
        } catch (failed: Exception) {
            // A thrown write is as uncertain as an unacknowledged write outcome.
            if (state.uncertain) return state.uncertainty()
            // Includes downstream callback exceptions. Do not turn them into another provider send.
            state.interrupt()
            throw failed
        }
    }

    private class Attempt(
        val history: LocalAiHistory,
        val epoch: EntityId,
        val conversation: NewConversation,
        val input: NewHistoryTurn,
    ) {
        var turn: Turn? = null
        var uncertain = false
        val reply = StringBuilder()
        var replyBytes = 0
        var usage: Knowledge<Usage> = Knowledge.Unknown(AiHistoryLimits.UNFINISHED_REASON, SnapshotList.empty())

        suspend fun checkpoint(progress: HistoryProgress, token: CancelToken): Boolean {
            val previous = turn ?: return false
            uncertain = true // A thrown cancellation/failure never proves rollback.
            val result = history.checkpoint(input.id, previous.revision, epoch, progress, token)
            if (result !is Outcome.Success || !matches(result.value, progress) ||
                result.value.copy(revision = previous.revision, status = previous.status, rawContent = previous.rawContent,
                    usage = previous.usage, finishedAt = previous.finishedAt) != previous ||
                result.value.revision.value < previous.revision.value ||
                (result.value != previous && result.value.revision.value != previous.revision.value + 1)) return false
            turn = result.value
            uncertain = false
            return true
        }
        fun matches(value: Turn, progress: HistoryProgress): Boolean =
            value.id == input.id && value.conversationId == conversation.id && value.source == input.source &&
                value.prompt == input.prompt && value.provenance == input.provenance && value.mode == conversation.mode &&
                value.providerProfileId == input.providerProfileId && value.modelId == input.modelId &&
                value.retryOf == input.retryOf && value.ownerEpoch == epoch && value.status == progress.status &&
                value.rawContent == progress.rawContent && value.usage == progress.usage && value.ordinal > 0 &&
                (if (value.status == TurnStatus.PENDING || value.status == TurnStatus.STREAMING)
                    value.finishedAt == Knowledge.Unknown(AiHistoryLimits.UNFINISHED_REASON, SnapshotList.empty())
                else value.finishedAt is Knowledge.Known)

        suspend fun finish(status: TurnStatus): Boolean {
            if (uncertain || turn == null) return false
            return withContext(NonCancellable) {
                try {
                    withTimeout(FINALIZE_TIMEOUT_MS) {
                        checkpoint(HistoryProgress(status, reply.toString(), usage), independentCancel)
                    }
                } catch (_: Exception) { uncertain = true; false }
            }
        }
        suspend fun interrupt() {
            val value = turn ?: return
            if (uncertain || value.status !in setOf(TurnStatus.PENDING, TurnStatus.STREAMING)) return
            finish(TurnStatus.INTERRUPTED)
        }
        fun uncertainty(): Outcome.Failure = Outcome.Failure(DomainFailure(FailureCode.PERSISTENCE,
            "Generation history is uncertain; reload the original attempt before any new send",
            SnapshotList.of(Evidence("Original generation attempt: ${input.id.value}", null, null))))
    }

    private companion object {
        const val FINALIZE_TIMEOUT_MS = 5_000L
        val independentCancel = object : CancelToken {
            override val isCancelled: Boolean = false
            override fun throwIfCancelled(): Unit = Unit
        }
        suspend fun active(cancel: CancelToken) {
            currentCoroutineContext().ensureActive()
            cancel.throwIfCancelled()
            if (cancel.isCancelled) throw CancellationException("Generation cancelled")
        }
        fun invalid(): Outcome.Failure = failure(FailureCode.INVALID_INPUT, "Invalid durable generation input")
        fun failure(code: FailureCode, message: String): Outcome.Failure =
            Outcome.Failure(DomainFailure(code, message, SnapshotList.empty()))

        /** Counts before allocation and rejects malformed UTF16 rather than substituting bytes. */
        fun utf8Length(value: String, cap: Int): Int? {
            if (value.length > cap) return null
            var size = 0
            var index = 0
            while (index < value.length) {
                val ch = value[index++]
                val width = when {
                    ch < '\u0080' -> 1
                    ch < '\u0800' -> 2
                    ch in '\ud800'..'\udbff' -> {
                        if (index == value.length || value[index] !in '\udc00'..'\udfff') return null
                        index++; 4
                    }
                    ch in '\udc00'..'\udfff' -> return null
                    else -> 3
                }
                if (width > cap - size) return null
                size += width
            }
            return size
        }
        fun nativeVersion(value: Knowledge<String>): Boolean {
            val text = (value as? Knowledge.Known)?.value ?: return false
            if (!text.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return false
            return try { java.time.LocalDate.parse(text); true } catch (_: java.time.DateTimeException) { false }
        }
        fun validInput(epoch: EntityId, profile: ProviderProfile, conversation: NewConversation, input: NewHistoryTurn,
            request: GenerationRequest): Boolean {
            if (request.id != input.id || request.messages != SnapshotList.of(
                    GenerationMessage(MessageRole.USER, input.prompt.rendered, null))) return false
            if (input.source != HistoryTurnSource.PROVIDER_GENERATION || input.rawContent.isNotEmpty() ||
                input.ownerEpoch != epoch || input.providerProfileId != profile.id || input.modelId != profile.selectedModelId ||
                input.retryOf == input.id || input.prompt.deckId != conversation.deckId ||
                input.prompt.templateId != conversation.mode.templateId || input.prompt.templateVersion <= 0) return false
            val normalized = ProviderProfilePolicy.normalize(profile)
            if (normalized !is Outcome.Success || normalized.value != profile) return false
            when (profile.dialect) {
                ProviderDialect.OPENAI_CHAT -> if (profile.apiVersion is Knowledge.Known) return false
                ProviderDialect.ANTHROPIC -> if (!nativeVersion(profile.apiVersion) || request.maxTokens == null ||
                    request.temperature != null || request.topP != null || request.seed != null) return false
                else -> return false
            }
            // Mirror the input frame's byte accounting without allocating a payload or changing contracts.
            var bytes = 8L
            var valid = true
            fun field(value: String, cap: Int = AiHistoryLimits.MAX_METADATA_BYTES): Int {
                val count = utf8Length(value, cap)
                if (count == null) { valid = false; return 0 }
                bytes += 4L + count
                return count
            }
            fun hash(value: ContentHash) {
                if (value.algorithm != "SHA-256" || value.hex.length != 64 || value.hex.any { it !in "0123456789abcdef" }) valid = false
                field(value.algorithm); field(value.hex)
            }
            if (utf8Length(epoch.value, AiHistoryLimits.MAX_METADATA_BYTES) == null ||
                utf8Length(conversation.title, AiHistoryLimits.MAX_TITLE_BYTES) == null) return false
            field(input.id.value); field(conversation.id.value); field(input.source.name)
            val prompt = input.prompt
            field(prompt.templateId.value); bytes += 4
            field(prompt.deckId.source.value); field(prompt.deckId.raw); bytes += 8
            field(prompt.rendered, AiHistoryLimits.MAX_PROMPT_BYTES); bytes += 4
            if (prompt.bindings.size > AiHistoryLimits.MAX_BINDINGS) return false
            var bindingBytes = 0L
            for (binding in prompt.bindings) {
                field(binding.name); bindingBytes += field(binding.value, AiHistoryLimits.MAX_PROMPT_BYTES)
            }
            if (bindingBytes > AiHistoryLimits.MAX_PROMPT_BYTES) return false
            hash(prompt.contextHash); bytes += 12
            hash(input.provenance.effectiveTemplateHash); bytes += 2 // bool + nullable snapshot
            input.provenance.snapshot?.let { snapshot ->
                field(snapshot.source.value); bytes++
                snapshot.generation?.let { field(it.value) }; bytes++
                if (snapshot.revision != null) bytes += 8
                bytes++; snapshot.language?.let { field(it.value) }
            }
            field(conversation.mode.name); bytes++
            field(profile.id.value); bytes++; field(profile.selectedModelId); bytes++
            input.retryOf?.let { field(it.value) }
            field(""); bytes++ // unknown initial usage
            field(AiHistoryLimits.UNFINISHED_REASON); bytes += 4 // empty evidence list
            return valid && bytes <= AiHistoryLimits.MAX_PAYLOAD_BYTES
        }
        fun mergeUsage(previous: Knowledge<Usage>, incoming: Knowledge<Usage>): Knowledge<Usage>? {
            if (incoming !is Knowledge.Known) return previous
            val next = incoming.value
            fun count(value: Knowledge<Long>): Boolean = value !is Knowledge.Known || value.value >= 0
            if (!count(next.inputTokens) || !count(next.outputTokens) || !count(next.cachedInputTokens)) return null
            val amount = next.billedAmount
            if (amount is Knowledge.Known && (amount.value.value.signum() < 0 || amount.value.scale !in 0..1024 ||
                    amount.value.precision !in 1..1024)) return null
            val request = next.providerRequestId
            if (request is Knowledge.Known && utf8Length(request.value, AiHistoryLimits.MAX_METADATA_BYTES) == null) return null
            if (previous !is Knowledge.Known) return incoming
            fun <T> retain(old: Knowledge<T>, new: Knowledge<T>): Knowledge<T> = if (new is Knowledge.Known) new else old
            return Knowledge.Known(Usage(retain(previous.value.inputTokens, next.inputTokens),
                retain(previous.value.outputTokens, next.outputTokens), retain(previous.value.cachedInputTokens, next.cachedInputTokens),
                retain(previous.value.billedAmount, next.billedAmount), retain(previous.value.providerRequestId, next.providerRequestId)))
        }
    }
}
