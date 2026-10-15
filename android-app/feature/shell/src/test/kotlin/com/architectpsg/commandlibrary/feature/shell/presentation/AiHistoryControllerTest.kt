package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.AiHistoryAccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiHistoryControllerTest {
    @Test fun uncertainCommittedCaptureRetriesOriginalIdsWithoutDuplicatingContent() = runTest {
        val port = HistoryMemory(); port.loseAppendAck = true
        var ids = 0
        val controller = AiHistoryController(backgroundScope, AiHistoryAccess { Outcome.Success(port) },
            StandardTestDispatcher(testScheduler)) { EntityId("id-${++ids}") }
        val capture = capture()
        controller.capture(capture, "Deck")
        controller.save(controller.state.value.frame); runCurrent()
        assertTrue(controller.state.value.uncertainSave)
        assertEquals(1, port.appendCalls); assertEquals(2, ids)
        controller.hide()
        controller.open(deck()) // Reopen keeps the unresolved original capture/IDs.
        assertEquals(capture, controller.state.value.capture)
        controller.save(controller.state.value.frame); runCurrent()
        assertTrue(controller.state.value.saved); assertFalse(controller.state.value.uncertainSave)
        assertEquals(1, port.appendCalls); assertEquals(2, ids)
        assertEquals(capture.prompt.artifact, port.turns.values.single().prompt)
        assertEquals(capture.reply, port.turns.values.single().rawContent)
        controller.save(controller.state.value.frame); runCurrent() // An old saved-form callback cannot create another capture.
        assertEquals(2, ids); assertEquals(1, port.turns.size)
    }
    @Test fun closingDialogCannotCancelPossiblyCommittedCapture() = runTest {
        val port = HistoryMemory(); port.release = CompletableDeferred()
        val controller = AiHistoryController(backgroundScope, AiHistoryAccess { Outcome.Success(port) }, StandardTestDispatcher(testScheduler))
        controller.capture(capture(), "Deck")
        controller.save(controller.state.value.frame); runCurrent()
        assertTrue(controller.state.value.writing)
        controller.hide()
        assertFalse(controller.state.value.visible)
        port.release!!.complete(Unit); runCurrent()
        assertEquals(1, port.turns.size)
        assertFalse(controller.state.value.writing)
        assertTrue(controller.state.value.saved)
    }
    @Test fun failedInitializationNeverClaimsEmptyHistoryAndExplicitRetryUsesRealAccess() = runTest {
        val port = HistoryMemory(); var fail = true; var opens = 0
        val controller = AiHistoryController(backgroundScope, AiHistoryAccess {
            opens++; if (fail) Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Unsupported history") else Outcome.Success(port)
        }, StandardTestDispatcher(testScheduler))
        controller.open(deck()); runCurrent()
        assertEquals(Problem.UNSUPPORTED, controller.state.value.problem)
        assertTrue(controller.state.value.conversations.isEmpty())
        fail = false; controller.refresh(); runCurrent()
        assertNull(controller.state.value.problem); assertEquals(2, opens)
        assertEquals(deck().id, port.lastDeck)
    }
    private fun capture(): OfflineHistoryCapture {
        val artifact = PromptArtifact(OfflineLabMode.CHAT.templateId, 1, deck().id, Revision(7), "Exact prompt Ω",
            SnapshotList.of(PromptBinding("deck", "Original deck context")), ContentHash("SHA-256", "a".repeat(64)), UtcTimestamp(9))
        return OfflineHistoryCapture(HistoryPromptCapture(OfflineLabMode.CHAT, artifact,
            HistoryPromptProvenance(ContentHash("SHA-256", "b".repeat(64)), true, null)), "Exact pasted reply 日本語")
    }
    private fun deck() = DeckSnapshot(DeckId(ProviderId("local"), "deck"), "Deck", Knowledge.Absent("none"), "",
        Knowledge.Absent("none"), SnapshotList.empty(), UtcTimestamp(1), UtcTimestamp(1), Revision(7), SnapshotList.empty(),
        Knowledge.Absent("none"), null, SnapshotList.empty(), SnapshotList.empty(), "")
}
/** Faithful acknowledgement-loss/navigation oracle only; actual Room/codec integration is separate. */
private class HistoryMemory : LocalAiHistory {
    val headers = linkedMapOf<EntityId, Conversation>()
    val turns = linkedMapOf<EntityId, Turn>()
    var loseAppendAck = false; var appendCalls = 0
    var release: CompletableDeferred<Unit>? = null
    var lastDeck: DeckId? = null
    private fun missing() = Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Not found")
    override suspend fun getConversation(id: EntityId, cancel: CancelToken): Outcome<Conversation> = headers[id]?.let { Outcome.Success(it) } ?: missing()
    override suspend fun createConversation(input: NewConversation, cancel: CancelToken): Outcome<Conversation> {
        val value = Conversation(input.id, input.deckId, input.mode, input.title, Revision(1), UtcTimestamp(1), UtcTimestamp(1))
        check(headers.put(input.id, value) == null)
        return Outcome.Success(value)
    }
    override suspend fun getTurn(id: EntityId, cancel: CancelToken): Outcome<Turn> = turns[id]?.let { Outcome.Success(it) } ?: missing()
    override suspend fun appendTurn(conversationId: EntityId, expectedRevision: Revision, input: NewHistoryTurn, cancel: CancelToken): Outcome<Turn> {
        appendCalls++
        val parent = checkNotNull(headers[conversationId]); check(parent.revision == expectedRevision)
        val value = Turn(input.id, conversationId, Revision(1), 1, input.source, input.prompt, input.provenance, parent.mode,
            null, null, null, null, TurnStatus.COMPLETE, input.rawContent, Knowledge.Unknown("Offline capture", SnapshotList.empty()),
            UtcTimestamp(2), Knowledge.Known(UtcTimestamp(2)))
        check(turns.put(input.id, value) == null)
        headers[conversationId] = parent.copy(revision = Revision(2), modifiedAt = UtcTimestamp(2))
        release?.await()
        return if (loseAppendAck) Outcome.Unknown("Acknowledgement lost", SnapshotList.empty()) else Outcome.Success(value)
    }
    override suspend fun pageConversations(deckId: DeckId, page: PageRequest, cancel: CancelToken): Outcome<Page<Conversation>> {
        lastDeck = deckId
        return Outcome.Success(Page(SnapshotList.copyOf(headers.values.filter { it.deckId == deckId }), null,
            SnapshotReference(ProviderId("local-ai-history"), null, Revision(1), null)))
    }
    override suspend fun pageTurns(conversationId: EntityId, page: PageRequest, cancel: CancelToken): Outcome<Page<HistoryTurnSummary>> = error("Not used")
    override suspend fun checkpoint(turnId: EntityId, expectedRevision: Revision, ownerEpoch: EntityId, progress: HistoryProgress, cancel: CancelToken): Outcome<Turn> = error("Not used")
    override suspend fun deleteConversation(id: EntityId, expectedRevision: Revision, cancel: CancelToken): Outcome<Unit> = error("Not used")
    override suspend fun recover(ownerEpoch: EntityId, cancel: CancelToken): Outcome<Long> = error("Not used")
}
