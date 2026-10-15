package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PromptOverrideControllerTest {
    @Test fun loadsAllModesAndRecreatedOwnerUsesExactSavedBodyAndVersion() = runTest {
        val store = OverrideStore()
        val f = OverrideFixture(this, store)
        assertFalse(f.controller.state.value.templateReady)
        f.controller.render(); assertEquals(0, f.requests.size)
        runCurrent()
        assertEquals(OfflineLabMode.entries.toSet(), store.loads.toSet())
        for (mode in OfflineLabMode.entries) {
            f.controller.setMode(mode)
            f.controller.setText(OfflinePromptField.OVERRIDE, body(mode.name))
            f.controller.saveTemplate(); runCurrent()
            assertEquals(mode.shippedVersion, store.rows[mode]?.shippedVersion)
            assertTrue(f.controller.state.value.templateSaved)
        }
        val restarted = OverrideFixture(this, store); runCurrent()
        for (mode in OfflineLabMode.entries) {
            restarted.controller.setMode(mode)
            assertEquals(body(mode.name), restarted.controller.state.value.templateBody)
            restarted.controller.render(); runCurrent()
            assertEquals(body(mode.name), restarted.requests.last().overrideBody)
        }
    }

    @Test fun failedLoadIsBlockedUntilExplicitReloadAndNeverSavesAsAbsent() = runTest {
        val store = OverrideStore().apply { failLoad = true }
        val f = OverrideFixture(this, store); runCurrent()
        assertFalse(f.controller.state.value.templateReady)
        assertEquals(Problem.SOURCE_UNAVAILABLE, f.controller.state.value.baseline.problem)
        f.controller.setText(OfflinePromptField.OVERRIDE, body("blocked")); f.controller.saveTemplate()
        assertEquals(0, store.writes)
        store.failLoad = false
        f.controller.reloadTemplate(); runCurrent()
        assertTrue(f.controller.state.value.templateReady)
        assertNull(f.controller.state.value.baseline.row)
    }

    @Test fun lateSaveSurvivesModeHideAndDeckChangeWithoutSavingNewerDraft() = runTest {
        val store = OverrideStore(); val f = OverrideFixture(this, store); runCurrent()
        val gate = CompletableDeferred<Unit>(); store.gate = gate
        f.controller.setText(OfflinePromptField.OVERRIDE, body("first"))
        f.controller.saveTemplate(); runCurrent()
        f.controller.setText(OfflinePromptField.OVERRIDE, body("newer"))
        f.controller.setMode(OfflineLabMode.ROAST)
        f.controller.setText(OfflinePromptField.OVERRIDE, body("roast"))
        f.controller.hide()
        f.head = f.head.copy(revision = Revision(2))
        f.controller.open(f.head, null)
        gate.complete(Unit); runCurrent()
        assertEquals(body("first"), store.rows[OfflineLabMode.CHAT]?.body)
        assertEquals(body("roast"), f.controller.state.value.templateBody)
        f.controller.setMode(OfflineLabMode.CHAT)
        assertEquals(body("newer"), f.controller.state.value.templateBody)
        assertTrue(OfflineLabMode.CHAT in f.controller.state.value.dirtyModes)
        assertFalse(f.controller.state.value.templateSaved)
        assertEquals(1, store.writes)
    }

    @Test fun delayedSaveBookkeepingPreservesNewerSameModeAndOtherModeRenderedFrames() = runTest {
        for (activeMode in listOf(OfflineLabMode.CHAT, OfflineLabMode.ROAST)) {
            val store = OverrideStore(); val f = OverrideFixture(this, store); runCurrent()
            val gate = CompletableDeferred<Unit>(); store.gate = gate
            f.controller.setText(OfflinePromptField.OVERRIDE, body("saving chat"))
            f.controller.saveTemplate(); runCurrent()
            f.controller.setMode(activeMode)
            f.controller.setText(OfflinePromptField.OVERRIDE, body("newer ${activeMode.name}"))
            f.controller.render(); runCurrent()
            val rendered = checkNotNull(f.controller.state.value.result)
            val frame = f.controller.state.value.frame
            gate.complete(Unit); runCurrent()
            assertSame(rendered, f.controller.state.value.result)
            assertEquals(frame, f.controller.state.value.frame)
            assertEquals(body("newer ${activeMode.name}"), f.controller.state.value.templateBody)
            assertEquals(body("saving chat"), store.rows[OfflineLabMode.CHAT]?.body)
            // An unrelated mode reload also carries no authority over this rendered deck frame.
            val otherMode = if (activeMode == OfflineLabMode.CHAT) OfflineLabMode.ROAST else OfflineLabMode.CHAT
            f.controller.reloadTemplate(otherMode); runCurrent()
            assertSame(rendered, f.controller.state.value.result)
            assertEquals(frame, f.controller.state.value.frame)
        }
    }

    @Test fun unknownCommittedSaveReloadsTruthAndPreservesDraftWithoutReplay() = runTest {
        val store = OverrideStore(); val f = OverrideFixture(this, store); runCurrent()
        val gate = CompletableDeferred<Unit>(); store.gate = gate; store.unknown = true
        f.controller.setText(OfflinePromptField.OVERRIDE, body("committed")); f.controller.saveTemplate(); runCurrent()
        f.controller.setText(OfflinePromptField.OVERRIDE, body("newer"))
        gate.complete(Unit); runCurrent()
        val s = f.controller.state.value
        assertEquals(body("committed"), s.baseline.row?.body)
        assertEquals(body("newer"), s.templateBody)
        assertTrue(s.templateReady); assertFalse(s.templateSaved); assertEquals(1, store.writes)
    }

    @Test fun staleSaveReloadsForeignRevisionAndRequiresAnotherExplicitSave() = runTest {
        val store = OverrideStore(); val f = OverrideFixture(this, store); runCurrent()
        store.rows[OfflineLabMode.CHAT] = row(OfflineLabMode.CHAT, body("external"), 3)
        f.controller.setText(OfflinePromptField.OVERRIDE, body("draft")); f.controller.saveTemplate(); runCurrent()
        assertEquals(Revision(3), f.controller.state.value.baseline.row?.revision)
        assertEquals(body("draft"), f.controller.state.value.templateBody)
        assertEquals(1, store.writes)
        f.controller.saveTemplate(); runCurrent()
        assertEquals(Revision(4), store.rows[OfflineLabMode.CHAT]?.revision)
        assertTrue(f.controller.state.value.templateSaved)
    }

    @Test fun futureVersionRequiresExplicitAdoptionOrResetAndKeepsOtherModes() = runTest {
        val store = OverrideStore()
        store.rows[OfflineLabMode.CHAT] = row(OfflineLabMode.CHAT, body("future"), 1).copy(shippedVersion = 99)
        store.rows[OfflineLabMode.ROAST] = row(OfflineLabMode.ROAST, body("roast"), 8)
        val f = OverrideFixture(this, store); runCurrent()
        assertTrue(f.controller.state.value.versionMismatch)
        assertEquals(body("future"), f.controller.state.value.templateBody)
        f.controller.render(); runCurrent(); assertTrue(f.requests.isEmpty())
        f.controller.saveTemplate(); runCurrent()
        assertFalse(f.controller.state.value.versionMismatch)
        assertEquals(OfflineLabMode.CHAT.shippedVersion, store.rows[OfflineLabMode.CHAT]?.shippedVersion)
        f.controller.resetTemplate(); runCurrent()
        assertNull(store.rows[OfflineLabMode.CHAT]?.body)
        assertEquals(body("roast"), store.rows[OfflineLabMode.ROAST]?.body)
        f.controller.render(); runCurrent(); assertNull(f.requests.single().overrideBody)
    }

    @Test fun staleCallbacksAndRejectedInputCannotAffectOtherModeOrAuthorizeOldDraft() = runTest {
        val store = OverrideStore(); val f = OverrideFixture(this, store); runCurrent()
        val old = f.controller.state.value
        f.controller.setText(OfflinePromptField.OVERRIDE, body("chat"))
        f.controller.setText(OfflinePromptField.OVERRIDE, "x".repeat(32769))
        f.controller.setMode(OfflineLabMode.ROAST)
        f.controller.setText(OfflinePromptField.OVERRIDE, body("late"), old.mode, old.frame)
        f.controller.saveTemplate(old.mode, old.frame); f.controller.resetTemplate(old.mode, old.frame)
        assertEquals(0, store.writes)
        f.controller.resetTemplate(); runCurrent()
        f.controller.setMode(OfflineLabMode.CHAT); f.controller.saveTemplate(); f.controller.render(); runCurrent()
        assertEquals(body("chat"), f.controller.state.value.templateBody)
        assertTrue(OfflineLabMode.CHAT in f.controller.state.value.rejectedOverrides)
        assertEquals(1, store.writes); assertTrue(f.requests.isEmpty())
    }

    @Test fun pendingResetKeepsNewerDraftAndFailedAdoptionKeepsVersionReview() = runTest {
        val store = OverrideStore()
        store.rows[OfflineLabMode.CHAT] = row(OfflineLabMode.CHAT, body("saved"), 1)
        val f = OverrideFixture(this, store); runCurrent()
        val gate = CompletableDeferred<Unit>(); store.gate = gate
        f.controller.resetTemplate(); runCurrent()
        f.controller.setText(OfflinePromptField.OVERRIDE, body("newer"))
        gate.complete(Unit); runCurrent()
        assertNull(store.rows[OfflineLabMode.CHAT]?.body)
        assertEquals(body("newer"), f.controller.state.value.templateBody)
        assertFalse(f.controller.state.value.templateSaved)
        store.gate = null
        store.rows[OfflineLabMode.CHAT] = row(OfflineLabMode.CHAT, "future grammar", 3).copy(shippedVersion = 99)
        f.controller.reloadTemplate(); runCurrent()
        store.rejectSave = true
        f.controller.saveTemplate(); runCurrent()
        assertTrue(f.controller.state.value.versionMismatch)
        assertEquals(body("newer"), f.controller.state.value.templateBody)
        assertEquals(99, store.rows[OfflineLabMode.CHAT]?.shippedVersion)
    }

    @Test fun cancelledAcknowledgementReloadsBeforeAnotherWrite() = runTest {
        val store = OverrideStore(); val f = OverrideFixture(this, store); runCurrent()
        store.cancelAck = true
        f.controller.setText(OfflinePromptField.OVERRIDE, body("committed")); f.controller.saveTemplate(); runCurrent()
        assertEquals(body("committed"), f.controller.state.value.baseline.row?.body)
        assertEquals(body("committed"), f.controller.state.value.templateBody)
        assertTrue(OfflineLabMode.CHAT in f.controller.state.value.dirtyModes)
        assertTrue(f.controller.state.value.templateReady)
        assertEquals(1, store.writes)
    }

    @Test fun absentPortKeepsSessionBehaviorWithoutPersistenceReceipt() = runTest {
        val f = OverrideFixture(this, null)
        f.controller.setText(OfflinePromptField.OVERRIDE, body("session")); f.controller.saveTemplate()
        assertFalse(f.controller.state.value.persistenceAvailable); assertFalse(f.controller.state.value.templateSaved)
        f.controller.resetTemplate(); assertNull(f.controller.state.value.overrides[OfflineLabMode.CHAT])
    }

    private fun body(label: String) = "$label {{deck}} {{constraints}} {{request}}"
    private fun row(mode: OfflineLabMode, body: String, revision: Long) =
        StoredPromptOverride(mode, mode.shippedVersion, Revision(revision), body, UtcTimestamp(1))
}

/** A stateful test double exercises controller acknowledgements; production uses the real SQL port. */
private class OverrideStore : LocalPromptOverrides {
    val rows = mutableMapOf<OfflineLabMode, StoredPromptOverride>()
    val loads = mutableListOf<OfflineLabMode>()
    var writes = 0
    var failLoad = false
    var unknown = false
    var rejectSave = false
    var cancelAck = false
    var gate: CompletableDeferred<Unit>? = null
    override suspend fun load(mode: OfflineLabMode, cancel: CancelToken): Outcome<StoredPromptOverride?> {
        cancel.throwIfCancelled(); loads += mode
        return if (failLoad) Outcome.Unavailable(UnavailableReason.SOURCE_UNAVAILABLE, "test load failed") else Outcome.Success(rows[mode])
    }
    override suspend fun save(mode: OfflineLabMode, expected: Revision?, body: String, cancel: CancelToken): Outcome<StoredPromptOverride> {
        writes++; gate?.await(); cancel.throwIfCancelled()
        if (rows[mode]?.revision != expected) return stale()
        if (rejectSave) return Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, "test grammar rejected", SnapshotList.empty()))
        val row = StoredPromptOverride(mode, mode.shippedVersion, Revision((expected?.value ?: 0) + 1), body, UtcTimestamp(1))
        rows[mode] = row
        if (cancelAck) throw CancellationException("test lost acknowledgement")
        return if (unknown) Outcome.Unknown("test uncertain acknowledgement", SnapshotList.empty()) else Outcome.Success(row)
    }
    override suspend fun reset(mode: OfflineLabMode, expected: Revision?, cancel: CancelToken): Outcome<StoredPromptOverride?> {
        writes++; gate?.await(); cancel.throwIfCancelled()
        if (rows[mode]?.revision != expected) return stale()
        val row = rows[mode]?.let { it.copy(body = null, shippedVersion = mode.shippedVersion, revision = Revision(it.revision.value + 1)) }
        if (row != null) rows[mode] = row
        return Outcome.Success(row)
    }
    private fun stale(): Outcome<Nothing> = Outcome.Failure(DomainFailure(FailureCode.STALE_REVISION, "test stale", SnapshotList.empty()))
}

@OptIn(ExperimentalCoroutinesApi::class)
private class OverrideFixture(test: TestScope, store: LocalPromptOverrides?) : OfflineDeckPrompts {
    var head = DeckSnapshot(DeckId(ProviderId("local"), "saved"), "Deck", Knowledge.Absent("none"), "",
        Knowledge.Absent("none"), SnapshotList.empty(), UtcTimestamp(1), UtcTimestamp(1), Revision(1), SnapshotList.empty(),
        Knowledge.Absent("none"), null, SnapshotList.empty(), SnapshotList.empty(), "")
    val requests = mutableListOf<OfflinePromptRequest>()
    private val shipped = OfflineLabMode.entries.map { mode -> PromptTemplate(mode.templateId, mode.shippedVersion,
        mode.name, "${mode.name} {{deck}} {{constraints}} {{request}}", Knowledge.Absent("shipped"),
        SnapshotList.of(PromptVariable("deck", true, "deck"), PromptVariable("constraints", true, "constraints"), PromptVariable("request", true, "request"))) }
    val controller = OfflinePromptController(test.backgroundScope, StandardTestDispatcher(test.testScheduler), this, { head }, store)
    init { controller.open(head, null) }
    override fun templates() = SnapshotList.copyOf(shipped)
    override suspend fun render(request: OfflinePromptRequest, cancel: CancelToken): Outcome<OfflinePromptResult> {
        cancel.throwIfCancelled(); requests += request
        val template = shipped.single { it.id == request.mode.templateId }
        return Outcome.Success(OfflinePromptResult(PromptArtifact(template.id, template.shippedVersion, request.deck.id,
            request.deck.revision, "Complete", SnapshotList.empty(), ContentHash("SHA-256", "ab"), UtcTimestamp(1)),
            request.mode, template, request.overrideBody != null, request.snapshot, 0, request.deck.entries.size))
    }
    override suspend fun review(deck: DeckSnapshot, snapshot: SnapshotReference?, reply: String, cancel: CancelToken): Outcome<OfflineReplyReview> = error("unused")
    override suspend fun card(reference: OfflineCardReference, snapshot: SnapshotReference, cancel: CancelToken): Outcome<CardRecord> = error("unused")
}
