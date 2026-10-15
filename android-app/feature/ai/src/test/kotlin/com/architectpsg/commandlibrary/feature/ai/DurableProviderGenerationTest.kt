package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Synthetic in-memory durability/transport oracles; no provider or real-database acceptance claim. */
@OptIn(ExperimentalCoroutinesApi::class)
class DurableProviderGenerationTest {
    private val noCancel = object : CancelToken {
        override val isCancelled: Boolean = false
        override fun throwIfCancelled(): Unit = Unit
    }
    private val epoch = EntityId("process")
    private val profile = ProviderProfileFixtures.profile()
    private val deck = DeckId(ProviderId("local"), "deck")
    private val conversation = NewConversation(EntityId("conversation"), deck, OfflineLabMode.CHAT, "Exact title")
    private val hash = ContentHash("SHA-256", "a".repeat(64))
    private val prompt = PromptArtifact(OfflineLabMode.CHAT.templateId, 1, deck, Revision(7),
        "Captured 日本語 artifact", SnapshotList.of(PromptBinding("context", "exact")), hash, UtcTimestamp(10))
    private val input = NewHistoryTurn(EntityId("attempt"), HistoryTurnSource.PROVIDER_GENERATION, prompt,
        HistoryPromptProvenance(hash, false, null), "", profile.id, profile.selectedModelId, ownerEpoch = epoch)
    private fun unknown(): Knowledge<Nothing> = Knowledge.Unknown(AiHistoryLimits.UNFINISHED_REASON, SnapshotList.empty())
    private fun chunk(sequence: Long, text: String = "", finish: GenerationFinishReason? = null,
        usage: Knowledge<Usage> = unknown()): Outcome<TokenChunk> = Outcome.Success(TokenChunk(input.id, sequence,
        text, SnapshotList.empty(), finish, usage))
    private fun failed(): Outcome.Failure = Outcome.Failure(DomainFailure(FailureCode.PERSISTENCE,
        "Synthetic private persistence diagnostic", SnapshotList.empty()))
    private fun <T> ok(result: Outcome<T>): T = (result as Outcome.Success).value

    private inner class History : LocalAiHistory {
        val events = mutableListOf<String>()
        val turns = linkedMapOf<EntityId, Turn>()
        val checkpoints = mutableListOf<HistoryProgress>()
        var parent: Conversation? = null
        var appendFault = false
        var appendCommitThenThrow = false
        var createFault = false
        var failCheckpointNumber: Int? = null
        var commitFailedCheckpoint = false
        var throwFailedCheckpoint = false
        var checkpointDelayForever = false
        var alterAppendAck: (Turn) -> Turn = { it }
        var alterCheckpointAck: (Turn) -> Turn = { it }
        var writes = 0
        private fun missing(): Outcome.Unavailable = Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Missing")
        override suspend fun getConversation(id: EntityId, cancel: CancelToken): Outcome<Conversation> =
            parent?.let { Outcome.Success(it) } ?: missing()
        override suspend fun createConversation(input: NewConversation, cancel: CancelToken): Outcome<Conversation> {
            writes++; events += "create"
            if (createFault) return failed()
            return Outcome.Success(Conversation(input.id, input.deckId, input.mode, input.title, Revision(1),
                UtcTimestamp(11), UtcTimestamp(11)).also { parent = it })
        }
        override suspend fun getTurn(id: EntityId, cancel: CancelToken): Outcome<Turn> =
            turns[id]?.let { Outcome.Success(it) } ?: missing()
        override suspend fun appendTurn(conversationId: EntityId, expectedRevision: Revision, input: NewHistoryTurn,
            cancel: CancelToken): Outcome<Turn> {
            writes++; events += "append"
            assertEquals(parent!!.revision, expectedRevision)
            if (appendFault) return failed()
            assertFalse(turns.containsKey(input.id))
            input.retryOf?.let { assertTrue(turns[it]!!.status in setOf(TurnStatus.COMPLETE, TurnStatus.INTERRUPTED, TurnStatus.FAILED)) }
            val turn = Turn(input.id, conversationId, Revision(1), turns.size + 1L, input.source, input.prompt,
                input.provenance, parent!!.mode, input.providerProfileId, input.modelId, input.retryOf, input.ownerEpoch,
                TurnStatus.PENDING, "", unknown(), UtcTimestamp(12), unknown())
            turns[input.id] = turn; parent = parent!!.copy(revision = parent!!.revision.next())
            if (appendCommitThenThrow) throw CancellationException("Synthetic ambiguous append")
            events += "ack"
            return Outcome.Success(alterAppendAck(turn))
        }
        override suspend fun checkpoint(turnId: EntityId, expectedRevision: Revision, ownerEpoch: EntityId,
            progress: HistoryProgress, cancel: CancelToken): Outcome<Turn> {
            writes++; checkpoints += progress; events += "checkpoint:${progress.status}"
            cancel.throwIfCancelled()
            if (checkpointDelayForever) awaitCancellation()
            val old = turns[turnId]!!
            assertEquals(old.revision, expectedRevision); assertEquals(old.ownerEpoch, ownerEpoch)
            assertTrue(old.status in setOf(TurnStatus.PENDING, TurnStatus.STREAMING))
            assertTrue(progress.rawContent.startsWith(old.rawContent))
            val next = old.copy(revision = old.revision.next(), status = progress.status,
                rawContent = progress.rawContent, usage = progress.usage,
                finishedAt = if (progress.status in setOf(TurnStatus.PENDING, TurnStatus.STREAMING)) unknown() else Knowledge.Known(UtcTimestamp(13)))
            val fail = failCheckpointNumber == checkpoints.size
            if (!fail || commitFailedCheckpoint) turns[turnId] = next
            if (fail) {
                if (throwFailedCheckpoint) throw IllegalStateException("Synthetic private write exception")
                return failed()
            }
            return Outcome.Success(alterCheckpointAck(next))
        }
        override suspend fun pageConversations(deckId: DeckId, page: PageRequest, cancel: CancelToken): Outcome<Page<Conversation>> = error("unused")
        override suspend fun pageTurns(conversationId: EntityId, page: PageRequest, cancel: CancelToken): Outcome<Page<HistoryTurnSummary>> = error("unused")
        override suspend fun deleteConversation(id: EntityId, expectedRevision: Revision, cancel: CancelToken): Outcome<Unit> = error("unused")
        override suspend fun recover(ownerEpoch: EntityId, cancel: CancelToken): Outcome<Long> = error("not coordinator ownership")
    }
    private inner class Client(val history: History, val script: suspend FlowCollector<Outcome<TokenChunk>>.() -> Unit) : ProviderTextClient {
        var sends = 0
        var request: GenerationRequest? = null
        override fun stream(profile: ProviderProfile, request: GenerationRequest, cancel: CancelToken): Flow<Outcome<TokenChunk>> = flow {
            sends++; this@Client.request = request; history.events += "send"
            assertTrue(history.events.contains("ack"))
            assertEquals(TurnStatus.PENDING, history.turns[request.id]!!.status)
            try { script() } finally { history.events += "closed" }
        }
    }
    private suspend fun run(history: History, client: Client, value: NewHistoryTurn = input,
        parent: NewConversation = conversation,
        request: GenerationRequest = capturedRequest(value), callback: suspend (Turn) -> Unit = {}): Outcome<Turn> =
        DurableProviderGeneration(client).run(history, epoch, profile, parent, value, request, noCancel, callback)
    private fun capturedRequest(value: NewHistoryTurn = input): GenerationRequest = GenerationRequest(value.id,
        SnapshotList.of(GenerationMessage(MessageRole.USER, value.prompt.rendered, null)),
        null, null, null, null, SnapshotList.empty())

    @Test fun explicitCapturedOptionsReachClientUnchangedWithoutDefaults() = runTest {
        val h = History(); val c = Client(h) { emit(chunk(0, "ok", GenerationFinishReason.STOP)) }
        val request = capturedRequest().copy(maxTokens = 77, temperature = java.math.BigDecimal("0.2"),
            topP = java.math.BigDecimal("0.8"), seed = 42, stopSequences = SnapshotList.of("END"))
        ok(run(h, c, request = request))
        assertSame(request, c.request)
    }
    @Test fun requestIdentityAndExactCapturedUserMessageAreFencedBeforeWrites() = runTest {
        for (request in listOf(capturedRequest().copy(id = EntityId("different")),
            capturedRequest().copy(messages = SnapshotList.of(GenerationMessage(MessageRole.USER, "changed", null))),
            capturedRequest().copy(messages = SnapshotList.of(GenerationMessage(MessageRole.SYSTEM, input.prompt.rendered, null))))) {
            val h = History(); val c = Client(h) { error("Must not send") }
            assertEquals(FailureCode.INVALID_INPUT, (run(h, c, request = request) as Outcome.Failure).error.code)
            assertTrue(h.events.isEmpty()); assertEquals(0, c.sends)
        }
    }

    @Test fun pendingAckPrecedesSinglePostWithExactCapturedUserArtifactAndNullOptions() = runTest {
        val h = History(); val c = Client(h) { emit(chunk(0, "reply", GenerationFinishReason.STOP)) }
        val seen = mutableListOf<Turn>()
        val result = ok(run(h, c, callback = { seen += it }))
        assertEquals(listOf("create", "append", "ack", "send", "closed", "checkpoint:COMPLETE"), h.events)
        assertEquals(listOf(TurnStatus.PENDING, TurnStatus.COMPLETE), seen.map { it.status })
        assertEquals(TurnStatus.COMPLETE, result.status); assertEquals("reply", result.rawContent)
        val request = c.request!!
        assertEquals(input.id, request.id)
        assertEquals(listOf(GenerationMessage(MessageRole.USER, prompt.rendered, null)), request.messages)
        assertNull(request.maxTokens); assertNull(request.temperature); assertNull(request.topP); assertNull(request.seed)
        assertTrue(request.stopSequences.isEmpty()); assertEquals(1, c.sends)
    }
    @Test fun missingAckNeverDispatchesAndNoWriteAutomaticallyReplays() = runTest {
        for (create in listOf(true, false)) {
            val h = History().also { it.createFault = create; it.appendFault = !create }
            val c = Client(h) { error("must not send") }
            val result = run(h, c) as Outcome.Failure
            assertEquals(FailureCode.PERSISTENCE, result.error.code)
            assertTrue(result.error.message.contains("uncertain")); assertTrue(result.error.evidence.toString().contains(input.id.value))
            assertFalse(result.toString().contains("private")); assertEquals(0, c.sends)
            assertEquals(if (create) 1 else 2, h.writes); assertTrue(h.checkpoints.isEmpty())
        }
    }
    @Test fun mismatchedImmutableInputsFailBeforeAnyWriteOrPost() = runTest {
        val wrong = listOf(input.copy(source = HistoryTurnSource.OFFLINE_CAPTURE), input.copy(rawContent = "x"),
            input.copy(ownerEpoch = EntityId("old")), input.copy(providerProfileId = EntityId("other")),
            input.copy(modelId = "different"), input.copy(retryOf = input.id),
            input.copy(prompt = prompt.copy(deckId = DeckId(ProviderId("local"), "other"))),
            input.copy(prompt = prompt.copy(templateId = OfflineLabMode.ROAST.templateId)),
            input.copy(prompt = prompt.copy(rendered = "bad\uD800")),
            input.copy(provenance = input.provenance.copy(effectiveTemplateHash = ContentHash("SHA-1", "aa"))))
        for (value in wrong) {
            val h = History(); val c = Client(h) { error("must not send") }
            assertEquals(FailureCode.INVALID_INPUT, (run(h, c, value) as Outcome.Failure).error.code)
            assertEquals(0, h.writes); assertEquals(0, c.sends)
        }
    }
    @Test fun existingConversationRequiresExactDeckModeAndTitle() = runTest {
        val h = History(); h.createConversation(conversation, noCancel); h.writes = 0
        val c = Client(h) { error("must not send") }
        assertEquals(FailureCode.INVALID_INPUT, (run(h, c, parent = conversation.copy(title = "changed")) as Outcome.Failure).error.code)
        assertEquals(0, h.writes); assertEquals(0, c.sends)
    }
    @Test fun cumulativePrefixesAndKnownUsageSurviveUnknownTrailingTerminal() = runTest {
        val h = History()
        val usage = Usage(Knowledge.Known(0L), Knowledge.Known(5L), Knowledge.Known(2L), unknown(), Knowledge.Known("reported-id"))
        val c = Client(h) {
            emit(chunk(0, "a", usage = Knowledge.Known(usage)))
            emit(chunk(1, "日本語"))
            emit(chunk(2, "!", GenerationFinishReason.STOP))
            error("terminal must immediately close upstream")
        }
        val result = ok(run(h, c))
        assertEquals(listOf("a", "a日本語", "a日本語!"), h.checkpoints.map { it.rawContent })
        assertEquals(listOf(TurnStatus.STREAMING, TurnStatus.STREAMING, TurnStatus.COMPLETE), h.checkpoints.map { it.status })
        assertEquals(Knowledge.Known(usage), result.usage); assertEquals(Revision(4), result.revision)
        assertTrue(h.events.indexOf("closed") < h.events.indexOf("checkpoint:COMPLETE"))
    }
    @Test fun partialKnownUsageMergesWithoutLosingEarlierKnownCountsOrBilling() = runTest {
        val h = History()
        val first = Usage(Knowledge.Known(7L), unknown(), Knowledge.Known(0L), unknown(), Knowledge.Known("id"))
        val last = Usage(unknown(), Knowledge.Known(3L), unknown(), unknown(), unknown())
        val c = Client(h) {
            emit(chunk(0, "a", usage = Knowledge.Known(first)))
            emit(chunk(1, finish = GenerationFinishReason.STOP, usage = Knowledge.Known(last)))
        }
        val usage = (ok(run(h, c)).usage as Knowledge.Known).value
        assertEquals(Knowledge.Known(7L), usage.inputTokens); assertEquals(Knowledge.Known(3L), usage.outputTokens)
        assertEquals(Knowledge.Known(0L), usage.cachedInputTokens); assertEquals(Knowledge.Known("id"), usage.providerRequestId)
        assertTrue(usage.billedAmount is Knowledge.Unknown)
    }
    @Test fun nonStopTerminalRemainsVisiblyIncomplete() = runTest {
        for (finish in listOf(GenerationFinishReason.TOKEN_LIMIT, GenerationFinishReason.TOOL_CALL, GenerationFinishReason.INTERRUPTED,
            GenerationFinishReason.FAILED)) {
            val h = History(); val c = Client(h) { emit(chunk(0, "partial", finish)) }
            val result = ok(run(h, c))
            assertEquals(if (finish == GenerationFinishReason.FAILED) TurnStatus.FAILED else TurnStatus.INTERRUPTED, result.status)
            assertEquals("partial", result.rawContent)
        }
    }
    @Test fun truncatedOrFailedStreamPersistsReceivedPrefixAsFailed() = runTest {
        for (failure in listOf(false, true)) {
            val h = History(); val c = Client(h) {
                emit(chunk(0, "prefix"))
                if (failure) emit(Outcome.Failure(DomainFailure(FailureCode.TRANSPORT, "Synthetic failure", SnapshotList.empty())))
            }
            assertTrue(run(h, c) is Outcome.Failure)
            assertEquals("prefix", h.turns[input.id]!!.rawContent); assertEquals(TurnStatus.FAILED, h.turns[input.id]!!.status)
            assertTrue(h.events.indexOf("closed") < h.events.indexOf("checkpoint:FAILED")); assertEquals(1, c.sends)
        }
    }
    @Test fun wrongRequestSequenceUnsupportedTokensAndInvalidUsageCannotCommitChunk() = runTest {
        val base = (chunk(1, "bad", GenerationFinishReason.STOP) as Outcome.Success).value
        val invalid = listOf(base.copy(requestId = EntityId("wrong")), base.copy(sequence = 2),
            base.copy(tokenIds = SnapshotList.of(1)), base.copy(usage = Knowledge.Known(
                Usage(Knowledge.Known(-1L), unknown(), unknown(), unknown(), unknown()))))
        for (value in invalid) {
            val h = History(); val c = Client(h) { emit(chunk(0, "good")); emit(Outcome.Success(value)) }
            assertEquals(FailureCode.CORRUPT_DATA, (run(h, c) as Outcome.Failure).error.code)
            assertEquals("good", h.turns[input.id]!!.rawContent); assertEquals(TurnStatus.FAILED, h.turns[input.id]!!.status)
        }
    }
    @Test fun replyByteLimitAndStrictUnicodeRejectWholeOffendingDelta() = runTest {
        val prefix = "é".repeat(AiHistoryLimits.MAX_REPLY_BYTES / 2)
        for (bad in listOf("x", "\uD800", "\uDC00")) {
            val h = History(); val c = Client(h) { emit(chunk(0, prefix)); emit(chunk(1, bad, GenerationFinishReason.STOP)) }
            assertTrue(run(h, c) is Outcome.Failure)
            assertEquals(prefix, h.turns[input.id]!!.rawContent); assertEquals(TurnStatus.FAILED, h.turns[input.id]!!.status)
        }
        val h = History(); val c = Client(h) { emit(chunk(0, prefix, GenerationFinishReason.STOP)) }
        assertEquals(TurnStatus.COMPLETE, ok(run(h, c)).status)
    }
    @Test fun cancellationClosesUpstreamBeforeIndependentInterruptedFinalization() = runTest {
        val h = History(); val c = Client(h) { emit(chunk(0, "retained")); awaitCancellation() }
        val running = async { run(h, c) }
        runCurrent(); assertEquals(TurnStatus.STREAMING, h.turns[input.id]!!.status)
        running.cancelAndJoin()
        assertEquals(TurnStatus.INTERRUPTED, h.turns[input.id]!!.status); assertEquals("retained", h.turns[input.id]!!.rawContent)
        assertTrue(h.events.indexOf("closed") < h.events.indexOf("checkpoint:INTERRUPTED")); assertEquals(1, c.sends)
    }
    @Test fun callbackFailureCancelsUpstreamAndFinalizesWithoutSwallowingException() = runTest {
        val h = History(); val c = Client(h) { emit(chunk(0, "retained")); error("must close") }
        val sentinel = IllegalStateException("Synthetic callback failure")
        try {
            run(h, c, callback = { if (it.status == TurnStatus.STREAMING) throw sentinel })
            fail("Expected callback failure")
        } catch (actual: IllegalStateException) { assertSame(sentinel, actual) }
        assertEquals(TurnStatus.INTERRUPTED, h.turns[input.id]!!.status); assertEquals("retained", h.turns[input.id]!!.rawContent)
        assertTrue(h.events.indexOf("closed") < h.events.indexOf("checkpoint:INTERRUPTED")); assertEquals(1, c.sends)
    }
    @Test fun pendingCallbackFailureNeverDispatchesAndInterruptsAckedTurn() = runTest {
        val h = History(); val c = Client(h) { error("must not send") }
        val sentinel = IllegalStateException("Synthetic pending callback failure")
        try { run(h, c, callback = { throw sentinel }); fail("Expected callback failure") }
        catch (actual: IllegalStateException) { assertSame(sentinel, actual) }
        assertEquals(0, c.sends); assertEquals(TurnStatus.INTERRUPTED, h.turns[input.id]!!.status)
    }
    @Test fun ambiguousCheckpointNeverOverwritesPossibleCommitOrReplaysSend() = runTest {
        for (commit in listOf(false, true)) for (throws in listOf(false, true)) {
            val h = History().also { it.failCheckpointNumber = 1; it.commitFailedCheckpoint = commit; it.throwFailedCheckpoint = throws }
            val c = Client(h) { emit(chunk(0, "possibly committed")); error("must close on uncertainty") }
            val result = run(h, c) as Outcome.Failure
            assertEquals(FailureCode.PERSISTENCE, result.error.code); assertFalse(result.toString().contains("private"))
            assertEquals(1, h.checkpoints.size); assertEquals(1, c.sends)
            assertEquals(if (commit) "possibly committed" else "", h.turns[input.id]!!.rawContent)
            assertEquals(FailureCode.STALE_REVISION, (run(h, c) as Outcome.Failure).error.code)
            assertEquals(1, c.sends); assertEquals(1, h.checkpoints.size)
        }
    }
    @Test fun ambiguousAppendCancellationNeverPostsOrTriesToFinalizePossibleCommit() = runTest {
        val h = History().also { it.appendCommitThenThrow = true }; val c = Client(h) { error("must not send") }
        try { run(h, c); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertEquals(TurnStatus.PENDING, h.turns[input.id]!!.status); assertTrue(h.checkpoints.isEmpty()); assertEquals(0, c.sends)
        assertEquals(FailureCode.STALE_REVISION, (run(h, c) as Outcome.Failure).error.code); assertEquals(0, c.sends)
    }
    @Test fun terminalCheckpointFailureReturnsUncertaintyWithoutSecondWrite() = runTest {
        val h = History().also { it.failCheckpointNumber = 1; it.commitFailedCheckpoint = true }
        val c = Client(h) { emit(chunk(0, "done", GenerationFinishReason.STOP)) }
        assertEquals(FailureCode.PERSISTENCE, (run(h, c) as Outcome.Failure).error.code)
        assertEquals(TurnStatus.COMPLETE, h.turns[input.id]!!.status); assertEquals(1, h.checkpoints.size); assertEquals(1, c.sends)
    }
    @Test fun cancellationFinalizationIsBoundedAndDoesNotReplayTimedOutWrite() = runTest {
        val h = History(); val c = Client(h) {
            emit(chunk(0, "retained")); h.checkpointDelayForever = true; awaitCancellation()
        }
        val running = async { run(h, c) }
        runCurrent(); running.cancelAndJoin()
        assertEquals(2, h.checkpoints.size); assertEquals(TurnStatus.STREAMING, h.turns[input.id]!!.status)
        assertEquals(1, c.sends)
    }

    @Test fun inconsistentPendingAckNeverDispatchesOrOverwritesPossibleCommit() = runTest {
        val h = History().also { it.alterAppendAck = { turn -> turn.copy(modelId = "wrong-model") } }
        val c = Client(h) { error("must not send") }
        assertEquals(FailureCode.PERSISTENCE, (run(h, c) as Outcome.Failure).error.code)
        assertEquals(0, c.sends); assertTrue(h.checkpoints.isEmpty())
        assertEquals(TurnStatus.PENDING, h.turns[input.id]!!.status)
    }
    @Test fun inconsistentCheckpointAckCannotAdvanceOwnershipOrOverwrite() = runTest {
        val h = History().also { it.alterCheckpointAck = { turn -> turn.copy(ordinal = turn.ordinal + 1) } }
        val c = Client(h) { emit(chunk(0, "retained")); error("must close") }
        assertEquals(FailureCode.PERSISTENCE, (run(h, c) as Outcome.Failure).error.code)
        assertEquals(1, h.checkpoints.size); assertEquals(1, c.sends)
        assertEquals("retained", h.turns[input.id]!!.rawContent)
    }
    @Test fun malformedUnicodeDeltaWithRemainingCapacityIsRejectedWithoutReplacement() = runTest {
        for (bad in listOf("\uD800", "\uDC00", "bad\uD800suffix")) {
            val h = History(); val c = Client(h) { emit(chunk(0, "valid 😀")); emit(chunk(1, bad, GenerationFinishReason.STOP)) }
            assertEquals(FailureCode.CORRUPT_DATA, (run(h, c) as Outcome.Failure).error.code)
            assertEquals("valid 😀", h.turns[input.id]!!.rawContent)
        }
    }
    @Test fun existingAttemptNeverDispatchesAndExplicitRetryUsesNewAttemptIdentity() = runTest {
        val h = History(); val c = Client(h) { emit(chunk(0, "first", GenerationFinishReason.STOP)) }
        ok(run(h, c)); val writes = h.writes
        assertEquals(FailureCode.STALE_REVISION, (run(h, c) as Outcome.Failure).error.code)
        assertEquals(writes, h.writes); assertEquals(1, c.sends)
        val retry = input.copy(id = EntityId("retry"), retryOf = input.id)
        val retryClient = Client(h) {
            emit(Outcome.Success((chunk(0, "retry", GenerationFinishReason.STOP) as Outcome.Success).value.copy(requestId = retry.id)))
        }
        val result = ok(run(h, retryClient, retry))
        assertEquals(retry.id, result.id); assertEquals(input.id, result.retryOf)
        assertEquals("first", h.turns[input.id]!!.rawContent); assertEquals(1, retryClient.sends)
    }
}
