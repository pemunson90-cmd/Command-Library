package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflinePromptControllerTest {
    @Test fun explicitModeMappingAndPrivateInputsAndSessionReset() = runTest {
        val f = PromptFixture(this)
        assertEquals(0, f.requests.size)
        assertFalse(f.controller.state.value.includePrivateNotes)
        f.controller.setText(OfflinePromptField.QUESTION, "Question {{deck}}")
        f.controller.setText(OfflinePromptField.CONSTRAINTS, "Budget 30")
        for (mode in OfflineLabMode.entries) {
            f.controller.setMode(mode)
            f.controller.setIntensity(RoastIntensity.SPICY)
            f.controller.setText(OfflinePromptField.OVERRIDE, "Session {{deck}} {{constraints}} {{request}}")
            f.controller.setIncludePrivateNotes(true)
            f.controller.render(); runCurrent()
            val request = f.requests.last()
            assertEquals(mode, request.mode); assertEquals(RoastIntensity.SPICY, request.intensity)
            assertEquals("Question {{deck}}", request.question); assertEquals("Budget 30", request.constraints)
            assertTrue(request.includePrivateNotes); assertNotNull(request.overrideBody)
            assertEquals(mode.templateId, f.controller.state.value.result?.artifact?.templateId)
            f.controller.resetTemplate(); assertNull(f.controller.state.value.overrides[mode])
            f.controller.render(); runCurrent(); assertNull(f.requests.last().overrideBody)
        }
    }

    @Test fun copyUsesCurrentArtifactOncePerExplicitActionAndReportsPlatformOutcome() = runTest {
        val f = PromptFixture(this); f.controller.render(); runCurrent()
        val rendered = checkNotNull(f.controller.state.value.result).artifact.rendered
        val copies = mutableListOf<String>()
        f.controller.copyPrompt { copies += it; true }
        assertEquals(listOf(rendered), copies); assertEquals(true, f.controller.state.value.copied)
        // Reading retained state (the recreation-equivalent owner) triggers no work or copy.
        val retained = f.controller.state
        assertSame(retained, f.controller.state); assertEquals(1, f.requests.size); assertEquals(1, copies.size)
        f.controller.copyPrompt { false }; assertEquals(false, f.controller.state.value.copied)
        f.controller.setText(OfflinePromptField.QUESTION, "Changed")
        f.controller.copyPrompt { copies += it; true }
        assertEquals(1, copies.size); assertNull(f.controller.state.value.copied)
        f.controller.render(); runCurrent(); f.head = null
        f.controller.copyPrompt { copies += it; true }
        assertEquals(1, copies.size); assertFalse(f.controller.state.value.visible)
    }

    @Test fun rejectedFieldCannotBeBypassedByAnotherEditModeOrRender() = runTest {
        val f = PromptFixture(this)
        f.controller.setText(OfflinePromptField.QUESTION, "previous")
        f.controller.setText(OfflinePromptField.QUESTION, "x".repeat(32769))
        f.controller.setText(OfflinePromptField.CONSTRAINTS, "valid")
        f.controller.setMode(OfflineLabMode.ROAST)
        f.controller.render(); runCurrent()
        assertTrue(f.requests.isEmpty()); assertTrue(OfflinePromptField.QUESTION in f.controller.state.value.rejected)
        f.controller.setText(OfflinePromptField.QUESTION, "fixed")
        f.controller.render(); runCurrent(); assertEquals("fixed", f.requests.single().question)
        f.controller.setText(OfflinePromptField.REPLY, "x".repeat(262145))
        f.controller.setText(OfflinePromptField.OVERRIDE, "fixed {{deck}} {{constraints}} {{request}}")
        f.controller.review(); runCurrent(); assertEquals(0, f.reviews)
        f.controller.setText(OfflinePromptField.REPLY, "[[Card]]")
        f.controller.review(); runCurrent(); assertEquals(1, f.reviews)

        for (resetChat in listOf(false, true)) {
            val modes = PromptFixture(this)
            val previous = "Previous CHAT {{deck}} {{constraints}} {{request}}"
            val corrected = "Corrected CHAT {{deck}} {{constraints}} {{request}}"
            modes.controller.setText(OfflinePromptField.OVERRIDE, previous)
            modes.controller.setText(OfflinePromptField.OVERRIDE, "x".repeat(32769))
            modes.controller.setMode(OfflineLabMode.ROAST)
            modes.controller.resetTemplate()
            modes.controller.render(); runCurrent()
            assertEquals(OfflineLabMode.ROAST, modes.requests.single().mode)
            assertNull(modes.requests.single().overrideBody)
            modes.controller.setMode(OfflineLabMode.CHAT)
            assertEquals(previous, modes.controller.state.value.templateBody)
            modes.controller.render(); runCurrent()
            assertEquals(1, modes.requests.size)
            assertEquals(Problem.INVALID_INPUT, modes.controller.state.value.problem)
            assertNull(modes.controller.state.value.result)
            modes.controller.setMode(OfflineLabMode.ROAST)
            modes.controller.setText(OfflinePromptField.OVERRIDE, "ROAST {{deck}} {{constraints}} {{request}}")
            modes.controller.render(); runCurrent()
            assertEquals(2, modes.requests.size)
            assertEquals(OfflineLabMode.ROAST, modes.requests.last().mode)
            modes.controller.setMode(OfflineLabMode.CHAT)
            modes.controller.render(); runCurrent()
            assertEquals(2, modes.requests.size)
            assertEquals(Problem.INVALID_INPUT, modes.controller.state.value.problem)
            assertNull(modes.controller.state.value.result)
            if (resetChat) modes.controller.resetTemplate()
            else modes.controller.setText(OfflinePromptField.OVERRIDE, corrected)
            modes.controller.render(); runCurrent()
            assertEquals(3, modes.requests.size)
            assertEquals(OfflineLabMode.CHAT, modes.requests.last().mode)
            assertEquals(if (resetChat) null else corrected, modes.requests.last().overrideBody)
            assertNotNull(modes.controller.state.value.result)
        }
    }

    @Test fun utf8LimitAndMalformedSurrogatesAreRejectedWithoutTruncation() = runTest {
        val f = PromptFixture(this)
        val exact = "é".repeat(16384)
        f.controller.setText(OfflinePromptField.QUESTION, exact)
        f.controller.render(); runCurrent(); assertEquals(exact, f.requests.single().question)
        f.controller.setText(OfflinePromptField.QUESTION, exact + "é")
        assertTrue(OfflinePromptField.QUESTION in f.controller.state.value.rejected)
        f.controller.setText(OfflinePromptField.QUESTION, "\uD800")
        assertTrue(OfflinePromptField.QUESTION in f.controller.state.value.rejected)
    }

    @Test fun lateRenderCannotReplaceNewInputOrExplicitSourceRefreshOrHiddenFrame() = runTest {
        val f = PromptFixture(this)
        val first = CompletableDeferred<Unit>()
        f.render = { request, _ -> withContext(NonCancellable) { first.await() }; Outcome.Success(f.result(request)) }
        f.controller.render(); runCurrent()
        f.controller.setText(OfflinePromptField.QUESTION, "latest")
        f.render = { request, _ -> Outcome.Success(f.result(request)) }
        f.controller.render(); runCurrent()
        val latest = f.controller.state.value.result
        first.complete(Unit); runCurrent(); assertSame(latest, f.controller.state.value.result)
        val next = f.snapshot.copy(generation = GenerationId("next"))
        f.controller.setSnapshot(next); assertNull(f.controller.state.value.result)
        assertEquals(next, f.controller.state.value.snapshot)
        f.controller.render(); runCurrent(); assertEquals(next, f.requests.last().snapshot)
        f.controller.hide(); assertNull(f.controller.state.value.result)
        assertFalse(f.controller.state.value.visible)
    }

    @Test fun deckRevisionAndForeignPortFramesFailClosed() = runTest {
        val f = PromptFixture(this)
        f.render = { request, _ -> Outcome.Success(f.result(request).copy(mode = OfflineLabMode.UPGRADE)) }
        f.controller.render(); runCurrent(); assertNull(f.controller.state.value.result)
        assertEquals(Problem.CORRUPT_DATA, f.controller.state.value.problem)
        f.render = { request, _ -> Outcome.Success(f.result(request).let { it.copy(artifact = it.artifact.copy(deckRevision = Revision(8))) }) }
        f.controller.render(); runCurrent(); assertNull(f.controller.state.value.result)
        f.head = checkNotNull(f.head).copy(revision = Revision(2))
        f.controller.render(); runCurrent(); assertFalse(f.controller.state.value.visible)
        assertEquals(2, f.requests.size)
    }

    @Test fun reviewPreservesAllStatusesAndRejectsForeignReplyOrSnapshot() = runTest {
        val f = PromptFixture(this)
        f.controller.setText(OfflinePromptField.REPLY, "Narrative [[Card]]")
        f.controller.review(); runCurrent()
        assertEquals(OfflineReferenceStatus.entries.toList(), checkNotNull(f.controller.state.value.review).references.map { it.status })
        f.review = { deck, snapshot, text -> f.reviewResult(deck, snapshot, text).copy(rawText = "foreign") }
        f.controller.review(); runCurrent(); assertNull(f.controller.state.value.review)
        assertEquals(Problem.CORRUPT_DATA, f.controller.state.value.problem)
        f.review = { deck, snapshot, text -> f.reviewResult(deck, snapshot, text).copy(snapshot = snapshot?.copy(generation = GenerationId("other"))) }
        f.controller.review(); runCurrent(); assertNull(f.controller.state.value.review)
    }

    @Test fun detailOnlyCallsPortForSingleFoundOriginAndUsesLiveDeckGuard() = runTest {
        val f = PromptFixture(this); f.controller.review(); runCurrent()
        for (index in 1..3) f.controller.openReference(index)
        runCurrent(); assertEquals(0, f.cards)
        f.controller.openReference(0); runCurrent(); assertEquals(1, f.cards)
        assertSame(f.original, f.controller.state.value.detail)
        f.controller.closeDetail(); assertNull(f.controller.state.value.detail)
        f.cardResult = Outcome.Unavailable(UnavailableReason.SOURCE_UNAVAILABLE, "private source error")
        f.controller.openReference(0); runCurrent()
        assertNull(f.controller.state.value.detail); assertEquals(Problem.SOURCE_UNAVAILABLE, f.controller.state.value.problem)
        f.head = null; f.controller.openReference(0); runCurrent(); assertEquals(2, f.cards)
    }

    @Test fun noncooperativeReviewAndDetailCannotPublishAfterFrameChanges() = runTest {
        val f = PromptFixture(this)
        val reviewGate = CompletableDeferred<Unit>()
        f.review = { deck, snapshot, text ->
            withContext(NonCancellable) { reviewGate.await() }
            f.reviewResult(deck, snapshot, text)
        }
        f.controller.review(); runCurrent()
        f.controller.setSnapshot(f.snapshot.copy(generation = GenerationId("next")))
        reviewGate.complete(Unit); runCurrent()
        assertNull(f.controller.state.value.review)
        f.controller.setSnapshot(f.snapshot)
        f.review = { deck, snapshot, text -> f.reviewResult(deck, snapshot, text) }
        f.controller.review(); runCurrent()
        val oldReview = checkNotNull(f.controller.state.value.review)
        val cardGate = CompletableDeferred<Unit>()
        f.loadCard = { withContext(NonCancellable) { cardGate.await() }; Outcome.Success(f.original) }
        f.controller.openReference(0, oldReview); runCurrent()
        f.controller.setText(OfflinePromptField.REPLY, "new reply")
        cardGate.complete(Unit); runCurrent()
        assertNull(f.controller.state.value.detail); assertNull(f.controller.state.value.review)
        f.controller.review(); runCurrent()
        f.controller.openReference(0, oldReview); runCurrent()
        assertEquals(1, f.cards) // stale rendered reference callback is not current review authority
    }

    @Test fun typedErrorsStayStaticAndNativeCancellationPropagates() = runTest {
        val f = PromptFixture(this)
        f.render = { _, _ -> Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, "private diagnostics", SnapshotList.empty())) }
        f.controller.render(); runCurrent(); assertEquals(Problem.INVALID_INPUT, f.controller.state.value.problem)
        var completion: Throwable? = null
        f.render = { _, _ ->
            currentCoroutineContext().job.invokeOnCompletion { completion = it }
            throw CancellationException("cancel")
        }
        f.controller.render(); runCurrent(); assertTrue(completion is CancellationException)
        assertFalse(f.controller.state.value.busy); assertNull(f.controller.state.value.result)
    }
    @Test fun historyCaptureKeepsExactRenderedPromptWhenPastedReplyClearsPreview() = runTest {
        val f = PromptFixture(this)
        f.controller.setText(OfflinePromptField.QUESTION, "Original prompt question")
        f.controller.render(); runCurrent()
        val before = f.controller.state.value
        val artifact = checkNotNull(before.result).artifact
        f.controller.setText(OfflinePromptField.REPLY, "Pasted reply 日本語")
        val after = f.controller.state.value
        assertNull(after.result)
        assertNull(f.controller.captureHistory(before.mode, before.frame)) // Old composition callbacks cannot save.
        val capture = checkNotNull(f.controller.captureHistory(after.mode, after.frame))
        assertSame(artifact, capture.prompt.artifact)
        assertEquals("Pasted reply 日本語", capture.reply)
        assertEquals(64, capture.prompt.provenance.effectiveTemplateHash.hex.length)
        f.controller.setText(OfflinePromptField.QUESTION, "New question needs a new preview")
        val changed = f.controller.state.value
        assertNull(f.controller.captureHistory(changed.mode, changed.frame))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class PromptFixture(test: TestScope) : OfflineDeckPrompts {
    val snapshot = SnapshotReference(ProviderId("scryfall"), GenerationId("one"), Revision(1), LanguageTag.ENGLISH)
    var head: DeckSnapshot? = DeckSnapshot(DeckId(ProviderId("local"), "saved"), "Deck", Knowledge.Absent("none"), "private",
        Knowledge.Absent("none"), SnapshotList.empty(), UtcTimestamp(1), UtcTimestamp(1), Revision(1), SnapshotList.empty(),
        Knowledge.Absent("none"), null, SnapshotList.empty(), SnapshotList.empty(), "private primer")
    private val shipped = OfflineLabMode.entries.reversed().map { mode -> PromptTemplate(mode.templateId, mode.shippedVersion,
        "Non-authoritative title", "${mode.name} {{deck}} {{constraints}} {{request}}", Knowledge.Absent("session only"),
        SnapshotList.of(PromptVariable("deck", true, "deck"), PromptVariable("constraints", true, "constraints"), PromptVariable("request", true, "request"))) }
    val requests = mutableListOf<OfflinePromptRequest>()
    var reviews = 0
    var cards = 0
    private val absent = Knowledge.Absent("not supplied")
    val original = CardRecord(OracleId(snapshot.source, "card"), "Card", Knowledge.Known("Original text"), absent, absent,
        absent, absent, absent, CardStats(absent, absent, absent, absent), absent, absent, absent, "card", LanguageTag.ENGLISH,
        SnapshotList.empty(), SnapshotList.empty(), Provenance(snapshot.source, null, absent, absent, UtcTimestamp(1), absent,
            snapshot.generation, null, null), Revision(1))
    var cardResult: Outcome<CardRecord> = Outcome.Success(original)
    var loadCard: suspend () -> Outcome<CardRecord> = { cardResult }
    var render: suspend (OfflinePromptRequest, CancelToken) -> Outcome<OfflinePromptResult> = { request, cancel ->
        cancel.throwIfCancelled(); Outcome.Success(result(request))
    }
    var review: suspend (DeckSnapshot, SnapshotReference?, String) -> OfflineReplyReview = { deck, snapshot, text -> reviewResult(deck, snapshot, text) }
    val controller = OfflinePromptController(test.backgroundScope, StandardTestDispatcher(test.testScheduler), this) { head }
    init { controller.open(checkNotNull(head), snapshot) }
    override fun templates(): SnapshotList<PromptTemplate> = SnapshotList.copyOf(shipped)
    fun result(request: OfflinePromptRequest): OfflinePromptResult {
        val template = shipped.single { it.id == request.mode.templateId }
        return OfflinePromptResult(PromptArtifact(template.id, template.shippedVersion, request.deck.id, request.deck.revision,
            "Complete ${request.mode.name}: ${request.question}", SnapshotList.empty(), ContentHash("SHA-256", "ab"), UtcTimestamp(1)),
            request.mode, template, request.overrideBody != null, request.snapshot, 0, request.deck.entries.size)
    }
    override suspend fun render(request: OfflinePromptRequest, cancel: CancelToken): Outcome<OfflinePromptResult> {
        requests += request; return render.invoke(request, cancel)
    }
    fun reviewResult(deck: DeckSnapshot, snapshot: SnapshotReference?, text: String): OfflineReplyReview = OfflineReplyReview(
        deck.id, deck.revision, snapshot, text, SnapshotList.copyOf(OfflineReferenceStatus.entries.map { status ->
            OfflineCardReference("Card", status, snapshot,
                if (status == OfflineReferenceStatus.FOUND) SnapshotList.of(OracleId(checkNotNull(snapshot).source, "card")) else SnapshotList.empty(),
                Knowledge.Unknown("unknown", SnapshotList.empty()))
        }), Knowledge.Unknown("not stated", SnapshotList.empty()), LegalityStatus.UNKNOWN,
        Knowledge.Unknown("unknown", SnapshotList.empty()), Knowledge.Unknown("unknown", SnapshotList.empty()),
        Knowledge.Unknown("unknown", SnapshotList.empty()), Knowledge.Unknown("unknown", SnapshotList.empty()))
    override suspend fun review(deck: DeckSnapshot, snapshot: SnapshotReference?, reply: String, cancel: CancelToken): Outcome<OfflineReplyReview> {
        cancel.throwIfCancelled(); reviews++; return Outcome.Success(review.invoke(deck, snapshot, reply))
    }
    override suspend fun card(reference: OfflineCardReference, snapshot: SnapshotReference, cancel: CancelToken): Outcome<CardRecord> {
        cancel.throwIfCancelled(); cards++; assertEquals(reference.snapshot, snapshot)
        return loadCard()
    }
}
