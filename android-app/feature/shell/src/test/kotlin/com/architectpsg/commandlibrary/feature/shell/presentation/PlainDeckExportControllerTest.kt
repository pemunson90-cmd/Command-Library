@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.document.DocumentRef
import com.architectpsg.commandlibrary.feature.shell.document.PlainDeckWriteResult
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Actual retained producer over explicit read-only snapshots and scripted typed boundary ports. */
class PlainDeckExportControllerTest {
    @Test fun constructionAndVisibilityCommandsDoNoIoAndHaveNoFrame() = runTest {
        val f = ExportFixture(this)
        assertEquals(PlainDeckExportState(), f.controller.state.value)
        f.controller.resume(); f.controller.hide(); f.controller.cancelPreparation(); f.controller.cancelWrite(); runCurrent()
        assertEquals(PlainDeckExportState(), f.controller.state.value)
        assertTrue(f.loads.isEmpty()); assertTrue(f.validated.isEmpty()); assertTrue(f.rendered.isEmpty()); assertTrue(f.writes.isEmpty())
    }

    @Test fun ownerNonceIsBoundedNonblankConstructionPrecondition() = runTest {
        for (nonce in listOf("", " \t", "x".repeat(257))) {
            var rejected = false
            try { ExportFixture(this, nonce = nonce) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
        ExportFixture(this, nonce = "x".repeat(256))
    }

    @Test fun nonlocalSourceNeverLoadsAndCanBeReplacedByExplicitLocalPreparation() = runTest {
        val f = ExportFixture(this)
        f.controller.prepare(f.head.id.copy(source = ProviderId("foreign")), f.head.revision); runCurrent()
        assertEquals(Problem.INVALID_INPUT, f.controller.state.value.problem); assertTrue(f.loads.isEmpty())
        assertNotNull(f.ready()); assertEquals(listOf(f.head.id), f.loads)
    }

    @Test fun successfulLoadMustMatchQualifiedIdBeforeValidationOrRendering() = runTest {
        for (wrong in listOf(exportDeck("other"), exportDeck().copy(id = DeckId(ProviderId("other-provider"), "deck")))) {
            val f = ExportFixture(this); f.load = { _, _ -> Outcome.Success(wrong) }
            f.controller.prepare(f.head.id, f.head.revision); runCurrent()
            assertEquals(Problem.CORRUPT_DATA, f.controller.state.value.problem)
            assertNull(f.controller.state.value.preview); assertTrue(f.validated.isEmpty()); assertTrue(f.rendered.isEmpty())
        }
    }

    @Test fun olderAndNewerLoadedRevisionAreBothStaleWithNoSilentAdoption() = runTest {
        for (revision in listOf(3L, 5L)) {
            val f = ExportFixture(this); f.head = f.head.copy(revision = Revision(revision))
            f.controller.prepare(f.head.id, Revision(4)); runCurrent()
            assertEquals(Problem.STALE_REVISION, f.controller.state.value.problem)
            assertNull(f.controller.state.value.preview); assertTrue(f.validated.isEmpty()); assertTrue(f.rendered.isEmpty())
        }
    }

    @Test fun typedLibraryFailuresPreserveProblemAndStopBeforeValidation() = runTest {
        val cases = listOf(exportFailure(FailureCode.PERSISTENCE) to Problem.PERSISTENCE,
            Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Private source") to Problem.NOT_FOUND,
            Outcome.Unknown("Private evidence", SnapshotList.empty()) to Problem.UNKNOWN_RESULT)
        for ((outcome, problem) in cases) {
            val f = ExportFixture(this); f.load = { _, _ -> outcome }
            f.controller.prepare(f.head.id, f.head.revision); runCurrent()
            assertEquals(problem, f.controller.state.value.problem); assertFalse(f.controller.state.value.preparing)
            assertNull(f.controller.state.value.preview); assertTrue(f.validated.isEmpty()); assertTrue(f.rendered.isEmpty())
        }
    }

    @Test fun typedValidatorAndRendererRefusalsArePreservedWithoutDestinationAccess() = runTest {
        val cases = listOf(exportFailure(FailureCode.CORRUPT_DATA) to Problem.CORRUPT_DATA,
            Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Static policy") to Problem.UNSUPPORTED,
            Outcome.Unknown("Uncertain", SnapshotList.empty()) to Problem.UNKNOWN_RESULT)
        for ((outcome, problem) in cases) {
            val validator = ExportFixture(this); validator.validation = { _, _ -> outcome }
            validator.controller.prepare(validator.head.id, validator.head.revision); runCurrent()
            assertEquals(problem, validator.controller.state.value.problem); assertTrue(validator.rendered.isEmpty())
            val renderer = ExportFixture(this); renderer.render = { _, _ -> outcome }
            renderer.controller.prepare(renderer.head.id, renderer.head.revision); runCurrent()
            assertEquals(problem, renderer.controller.state.value.problem); assertNull(renderer.controller.state.value.preview)
            assertTrue(validator.writes.isEmpty()); assertTrue(renderer.writes.isEmpty())
        }
    }

    @Test fun emptySavedDeckIsValidatedRenderedOnceAndRetainedWithoutInventingCards() = runTest {
        val f = ExportFixture(this); f.head = exportDeck().copy(entries = SnapshotList.empty())
        f.output = PlainDeckListExport(SnapshotBytes("Command Library Deck List 1\nDeck\n".toByteArray()), SnapshotList.empty())
        val preview = f.ready()
        assertSame(f.head, preview.source); assertSame(f.output, preview.export)
        assertEquals(listOf(f.head), f.validated); assertEquals(listOf(f.head), f.rendered); assertTrue(f.writes.isEmpty())
    }

    @Test fun allOrderedDeckAndOriginalEntryLossesAndImmutableBytesSurviveUnchanged() = runTest {
        val f = ExportFixture(this)
        val preview = f.ready()
        assertSame(f.head, preview.source); assertSame(f.output, preview.export)
        assertSame(f.output.losses, preview.export.losses); assertSame(f.output.bytes, preview.export.bytes)
        assertEquals(16 + 9 * 2, preview.export.losses.size)
        assertEquals(listOf(2, 3), preview.source.entries.map { it.quantity })
        assertEquals(listOf(BoardRole.MAIN, BoardRole.SIDEBOARD), preview.source.entries.map { it.board })
        assertEquals(listOf(f.head), f.validated); assertEquals(listOf(f.head), f.rendered)
    }

    @Test fun oversizedRenderedBytesAndLossCountAreTypedCorruptionBeforeAnyPicker() = runTest {
        val cases = listOf(PlainDeckListExport(SnapshotBytes(ByteArray(PlainDeckListLimits.MAX_BYTES + 1)), SnapshotList.empty()),
            PlainDeckListExport(SnapshotBytes(byteArrayOf(1)), SnapshotList.copyOf(List(PlainDeckListLimits.MAX_LOSSES + 1) {
                PlainDeckListLoss(null, PlainDeckListLossField.DECK_ID) })))
        for (output in cases) {
            val f = ExportFixture(this); f.output = output
            f.controller.prepare(f.head.id, f.head.revision); runCurrent()
            assertEquals(Problem.CORRUPT_DATA, f.controller.state.value.problem)
            assertNull(f.controller.state.value.preview); assertNull(f.controller.state.value.pendingPicker); assertTrue(f.writes.isEmpty())
        }
    }

    @Test fun zeroByteRenderIsCorruptionWithoutPreviewPickerAuthorityOrWriterAccess() = runTest {
        val f = ExportFixture(this); val baseline = f.output
        f.output = baseline.copy(bytes = SnapshotBytes(byteArrayOf()))
        f.controller.prepare(f.head.id, f.head.revision); runCurrent()
        assertEquals(Problem.CORRUPT_DATA, f.controller.state.value.problem)
        assertFalse(f.controller.state.value.preparing); assertNull(f.controller.state.value.preview)
        assertNull(f.controller.state.value.pendingPicker); assertFalse(f.controller.state.value.pickerAuthorized)
        val forged = PlainDeckExportPreview(f.head, f.output)
        assertNull(f.controller.requestDestination(forged))
        f.controller.pickerResult("fixture-owner:1", DocumentRef("content://selected/empty-render")); runCurrent()
        assertTrue(f.writes.isEmpty()); assertEquals(0, f.controller.state.value.attempts)
        assertEquals(listOf(f.head), f.rendered)
    }

    @Test fun exactRenderedByteLimitIsAdmittedWithoutReparsingOrReplacingBytes() = runTest {
        val f = ExportFixture(this); f.output = PlainDeckListExport(SnapshotBytes(ByteArray(PlainDeckListLimits.MAX_BYTES)), SnapshotList.empty())
        assertSame(f.output.bytes, f.ready().export.bytes)
        assertEquals(1, f.rendered.size)
    }

    @Test fun everyLossRequiresCorrectScopeOriginalEntryAndUniqueFieldEntryPair() = runTest {
        val entry = EntityId("original-main")
        val cases = listOf(
            listOf(PlainDeckListLoss(entry, PlainDeckListLossField.DECK_NAME)),
            listOf(PlainDeckListLoss(null, PlainDeckListLossField.ENTRY_ID)),
            listOf(PlainDeckListLoss(EntityId("unrelated"), PlainDeckListLossField.NOTES)),
            List(2) { PlainDeckListLoss(null, PlainDeckListLossField.PRIMER) },
            List(2) { PlainDeckListLoss(entry, PlainDeckListLossField.ROLE_OVERRIDES) })
        for (losses in cases) {
            val f = ExportFixture(this); f.output = f.output.copy(losses = SnapshotList.copyOf(losses))
            f.controller.prepare(f.head.id, f.head.revision); runCurrent()
            assertEquals(Problem.CORRUPT_DATA, f.controller.state.value.problem); assertNull(f.controller.state.value.preview)
        }
    }

    @Test fun noncooperativeSupersededReadCannotRenderOrClearNewPreparation() = runTest {
        val f = ExportFixture(this); val old = f.head; val gate = CompletableDeferred<Unit>()
        f.load = { id, _ -> if (id == old.id) { withContext(NonCancellable) { gate.await() }; Outcome.Success(old) } else Outcome.Success(f.head) }
        f.controller.prepare(old.id, old.revision); runCurrent()
        val firstTicket = f.controller.state.value.formTicket
        f.head = exportDeck("new"); f.output = f.output.copy(losses = SnapshotList.empty())
        f.controller.prepare(f.head.id, f.head.revision); runCurrent()
        val current = checkNotNull(f.controller.state.value.preview)
        assertTrue(f.controller.state.value.formTicket > firstTicket)
        gate.complete(Unit); runCurrent()
        assertSame(current, f.controller.state.value.preview); assertFalse(f.controller.state.value.preparing)
        assertEquals(listOf(f.head), f.rendered); assertTrue(f.writes.isEmpty())
    }

    @Test fun cancelPreparationFencesLateReadAndExplicitRetryDoesNotReuseIt() = runTest {
        val f = ExportFixture(this); val gate = CompletableDeferred<Unit>(); val captured = f.head
        f.load = { _, _ -> withContext(NonCancellable) { gate.await() }; Outcome.Success(captured) }
        f.controller.prepare(f.head.id, f.head.revision); runCurrent(); f.controller.cancelPreparation()
        assertFalse(f.controller.state.value.preparing)
        gate.complete(Unit); runCurrent()
        assertNull(f.controller.state.value.preview); assertTrue(f.rendered.isEmpty())
        f.load = { _, _ -> Outcome.Success(f.head) }
        assertNotNull(f.ready()); assertEquals(2, f.loads.size)
    }

    @Test fun exactPreviewReferenceIsRequiredAndRetainedFrameBlocksReplacementUntilDiscard() = runTest {
        val f = ExportFixture(this); val preview = f.ready()
        assertNull(f.controller.requestDestination(preview.copy()))
        f.controller.prepare(exportDeck("other").id, Revision(0)); runCurrent()
        assertSame(preview, f.controller.state.value.preview); assertEquals(1, f.loads.size)
        f.controller.discard(preview.copy()); assertSame(preview, f.controller.state.value.preview)
        f.controller.discard(preview); assertNull(f.controller.state.value.preview)
        val next = f.ready(); assertNotSame(preview, next)
        assertNull(f.controller.requestDestination(preview)); assertNotNull(f.controller.requestDestination(next))
    }

    @Test fun callbackWritesSameFrozenBytesOnceWithoutReloadValidateOrRenderAfterDeckChanges() = runTest {
        val f = ExportFixture(this); val preview = f.ready(); val ticket = checkNotNull(f.controller.requestDestination(preview))
        val selected = DocumentRef("content://selected/frozen")
        f.head = f.head.copy(revision = Revision(5), name = "Changed later")
        f.controller.pickerResult("foreign-ticket", selected); assertEquals(ticket, f.controller.state.value.pendingPicker)
        f.controller.pickerResult(ticket, selected); f.controller.pickerResult(ticket, selected); runCurrent()
        assertEquals(1, f.writes.size); assertSame(preview.export.bytes, f.writes.single().bytes)
        assertEquals(selected, f.writes.single().destination); assertSame(preview, f.controller.state.value.preview)
        assertEquals(1, f.loads.size); assertEquals(1, f.validated.size); assertEquals(1, f.rendered.size)
        assertEquals(PlainDeckWriteResult.Written, f.controller.state.value.lastWrite)
    }

    @Test fun nullPickerAndLaunchFailureConsumeOnlyMatchingTicketPreservingPreviewAndPriorResult() = runTest {
        val f = ExportFixture(this); val preview = f.ready()
        f.submit(preview, "content://selected/prior"); runCurrent(); val prior = f.controller.state.value.lastWrite
        val cancelled = checkNotNull(f.controller.requestDestination(preview))
        f.controller.pickerResult(cancelled, null)
        assertNull(f.controller.state.value.pendingPicker); assertSame(preview, f.controller.state.value.preview)
        assertSame(prior, f.controller.state.value.lastWrite)
        val failed = checkNotNull(f.controller.requestDestination(preview))
        f.controller.pickerFailed("other", Problem.PERMISSION); assertEquals(failed, f.controller.state.value.pendingPicker)
        f.controller.pickerFailed(failed, Problem.PERMISSION)
        assertNull(f.controller.state.value.pendingPicker); assertEquals(Problem.PERMISSION, f.controller.state.value.problem)
        assertSame(prior, f.controller.state.value.lastWrite); assertEquals(1, f.writes.size)
    }

    @Test fun pendingPickerBlocksReplacementAndHidePermanentlyRevokesEvenAfterResume() = runTest {
        val f = ExportFixture(this); val preview = f.ready(); val ticket = checkNotNull(f.controller.requestDestination(preview))
        assertNull(f.controller.requestDestination(preview)); f.controller.prepare(f.head.id, f.head.revision); f.controller.discard(preview)
        f.controller.hide(); assertEquals(ticket, f.controller.state.value.pendingPicker); assertFalse(f.controller.state.value.pickerAuthorized)
        f.controller.resume(); assertTrue(f.controller.state.value.visible); assertFalse(f.controller.state.value.pickerAuthorized)
        f.controller.pickerResult(ticket, DocumentRef("content://selected/revoked")); runCurrent()
        assertTrue(f.writes.isEmpty()); assertEquals(0, f.controller.state.value.attempts)
        assertNull(f.controller.state.value.pendingPicker); assertSame(preview, f.controller.state.value.preview)
        f.submit(preview, "content://selected/explicit-new"); runCurrent(); assertEquals(1, f.writes.size)
    }

    @Test fun hiddenPreviewCannotAuthorizeAndRevokedLaunchFailureCannotResurrectTicket() = runTest {
        val f = ExportFixture(this); val preview = f.ready(); f.controller.hide()
        assertNull(f.controller.requestDestination(preview)); f.controller.resume()
        val ticket = checkNotNull(f.controller.requestDestination(preview)); f.controller.hide()
        f.controller.pickerFailed(ticket, Problem.PERMISSION); f.controller.resume()
        f.controller.pickerResult(ticket, DocumentRef("content://selected/late")); runCurrent()
        assertTrue(f.writes.isEmpty()); assertNull(f.controller.state.value.pendingPicker)
    }

    @Test fun retainedControllerKeepsTicketButDifferentOwnerNonceCannotAcceptIt() = runTest {
        val first = ExportFixture(this, nonce = "owner-one"); val second = ExportFixture(this, nonce = "owner-two")
        val a = first.ready(); val b = second.ready()
        val ticket = checkNotNull(first.controller.requestDestination(a)); val other = checkNotNull(second.controller.requestDestination(b))
        assertNotEquals(ticket, other)
        second.controller.pickerResult(ticket, DocumentRef("content://selected/foreign")); runCurrent()
        assertEquals(other, second.controller.state.value.pendingPicker); assertTrue(second.writes.isEmpty())
        first.controller.pickerResult(ticket, DocumentRef("content://selected/retained")); runCurrent()
        assertEquals(1, first.writes.size)
    }

    @Test fun typedWriterOutcomesAndOrdinaryThrowRetainFrameForExplicitNewDestinationOnly() = runTest {
        val outcomes = listOf(PlainDeckWriteResult.Written, PlainDeckWriteResult.NotStarted(Problem.PERMISSION),
            PlainDeckWriteResult.PossiblyModified(Problem.PERSISTENCE), PlainDeckWriteResult.PossiblyModified(Problem.UNEXPECTED))
        for ((index, expected) in outcomes.withIndex()) {
            val f = ExportFixture(this); val preview = f.ready()
            f.writer = { _, _, _ -> if (index == 3) error("Private URI or transport details") else expected }
            f.submit(preview, "content://selected/result-$index"); runCurrent()
            assertEquals(expected, f.controller.state.value.lastWrite); assertSame(preview, f.controller.state.value.preview)
            assertEquals(PlainDeckExportWritePhase.IDLE, f.controller.state.value.writePhase)
            assertEquals(1, f.writes.size); f.controller.resume(); runCurrent(); assertEquals(1, f.writes.size)
        }
    }

    @Test fun nativeCancellationWhileJobActivePropagatesAfterTruthfulUnknownResultPublication() = runTest {
        val f = ExportFixture(this); val preview = f.ready(); val signal = CancellationException("Writer token cancellation")
        var completion: Throwable? = null; var cleanup = false
        f.writer = { _, _, _ ->
            currentCoroutineContext().job.invokeOnCompletion { completion = it }
            try { assertTrue(currentCoroutineContext().isActive); throw signal } finally { cleanup = true }
        }
        f.submit(preview, "content://selected/cancelled"); runCurrent()
        assertTrue(cleanup); assertTrue(completion is CancellationException)
        assertEquals(PlainDeckWriteResult.PossiblyModified(Problem.UNKNOWN_RESULT), f.controller.state.value.lastWrite)
        assertEquals(PlainDeckExportWritePhase.IDLE, f.controller.state.value.writePhase); assertSame(preview, f.controller.state.value.preview)
    }

    @Test fun noncooperativeWriterRetainsOwnershipUntilCleanupEvenWhenJobNoLongerActive() = runTest {
        val f = ExportFixture(this); val preview = f.ready(); val gate = CompletableDeferred<Unit>(); val closeGate = CompletableDeferred<Unit>()
        var cleanupStarted = false; var cleanup = false; var job: Job? = null
        f.writer = { _, _, token ->
            job = currentCoroutineContext().job
            try { withContext(NonCancellable) { gate.await() }; PlainDeckWriteResult.Written }
            finally { withContext(NonCancellable) { cleanupStarted = true; closeGate.await(); assertTrue(token.isCancelled); cleanup = true } }
        }
        val firstTicket = checkNotNull(f.controller.requestDestination(preview))
        f.controller.pickerResult(firstTicket, DocumentRef("content://selected/blocked")); runCurrent()
        assertEquals(PlainDeckExportWritePhase.WRITING, f.controller.state.value.writePhase)
        f.controller.cancelWrite(); runCurrent()
        assertFalse(checkNotNull(job).isActive); assertFalse(cleanup)
        assertEquals(PlainDeckExportWritePhase.CANCELLING, f.controller.state.value.writePhase)
        assertNull(f.controller.state.value.lastWrite)
        f.controller.prepare(exportDeck("replacement").id, Revision(0)); f.controller.discard(preview)
        assertNull(f.controller.requestDestination(preview)); f.controller.cancelPreparation()
        f.controller.hide(); f.controller.resume(); runCurrent()
        assertSame(preview, f.controller.state.value.preview); assertEquals(1, f.loads.size); assertEquals(1, f.writes.size)
        assertEquals(PlainDeckExportWritePhase.CANCELLING, f.controller.state.value.writePhase)
        gate.complete(Unit); runCurrent()
        assertTrue(cleanupStarted); assertFalse(cleanup)
        assertEquals(PlainDeckExportWritePhase.CANCELLING, f.controller.state.value.writePhase)
        assertNull(f.controller.requestDestination(preview)); f.controller.discard(preview)
        assertSame(preview, f.controller.state.value.preview)
        closeGate.complete(Unit); runCurrent()
        assertTrue(cleanup); assertEquals(PlainDeckExportWritePhase.IDLE, f.controller.state.value.writePhase)
        assertEquals(PlainDeckWriteResult.PossiblyModified(Problem.UNKNOWN_RESULT), f.controller.state.value.lastWrite)
        val successorGate = CompletableDeferred<Unit>()
        f.writer = { _, _, _ -> successorGate.await(); PlainDeckWriteResult.Written }
        f.submit(preview, "content://selected/after-closure"); runCurrent()
        f.controller.pickerResult(firstTicket, DocumentRef("content://selected/old-callback")); f.controller.pickerFailed(firstTicket, Problem.PERMISSION)
        assertFalse(closeGate.complete(Unit)); runCurrent()
        assertEquals(PlainDeckExportWritePhase.WRITING, f.controller.state.value.writePhase)
        assertEquals(2, f.writes.size)
        successorGate.complete(Unit); runCurrent()
        assertEquals(2, f.writes.size); assertEquals(PlainDeckWriteResult.Written, f.controller.state.value.lastWrite)
    }

    @Test fun hideResumeDuringWriteRetainsOwnerAndFrozenPayloadWithoutReauthorizingIo() = runTest {
        val f = ExportFixture(this); val preview = f.ready(); val gate = CompletableDeferred<Unit>()
        f.writer = { _, _, _ -> gate.await(); PlainDeckWriteResult.Written }
        f.submit(preview, "content://selected/hidden-write"); runCurrent()
        f.controller.hide(); assertFalse(f.controller.state.value.visible)
        f.controller.resume(); assertTrue(f.controller.state.value.visible)
        assertNull(f.controller.requestDestination(preview)); assertEquals(1, f.writes.size)
        gate.complete(Unit); runCurrent()
        assertSame(preview, f.controller.state.value.preview); assertSame(preview.export.bytes, f.writes.single().bytes)
        assertEquals(PlainDeckWriteResult.Written, f.controller.state.value.lastWrite)
    }

    @Test fun exactUriHistoryRejectsAToBToAEvenWhenFirstAttemptWasNotStarted() = runTest {
        val f = ExportFixture(this); val preview = f.ready()
        f.writer = { _, _, _ -> PlainDeckWriteResult.NotStarted(Problem.PERMISSION) }
        f.submit(preview, "content://selected/A"); runCurrent()
        f.writer = { _, _, _ -> PlainDeckWriteResult.Written }
        f.submit(preview, "content://selected/B"); runCurrent()
        val before = f.controller.state.value.lastWrite
        f.submit(preview, "content://selected/A"); runCurrent()
        assertEquals(listOf("content://selected/A", "content://selected/B"), f.writes.map { it.destination.uri })
        assertSame(before, f.controller.state.value.lastWrite); assertEquals(2, f.controller.state.value.attempts)
        assertEquals(Problem.INVALID_INPUT, f.controller.state.value.problem)
        assertTrue(f.writes.all { it.bytes === preview.export.bytes })
    }

    @Test fun uriAdmissionUsesExactUtf16UnitsAndRejectsOverlimitBeforeWriter() = runTest {
        val f = ExportFixture(this); val preview = f.ready()
        val exact = "x".repeat(PlainDeckExportLimits.MAX_URI_CODE_UNITS - 2) + "😃"
        assertEquals(16_384, exact.length)
        f.submit(preview, exact); runCurrent(); assertEquals(1, f.writes.size)
        val prior = f.controller.state.value.lastWrite
        f.submit(preview, exact + "x"); runCurrent()
        assertEquals(1, f.writes.size); assertEquals(1, f.controller.state.value.attempts)
        assertSame(prior, f.controller.state.value.lastWrite); assertEquals(Problem.INVALID_INPUT, f.controller.state.value.problem)
    }

    @Test fun thirtyTwoDestinationsAreNonEvictingThenOnlyExactDiscardAllowsNewExport() = runTest {
        val f = ExportFixture(this); val preview = f.ready(); val tickets = mutableListOf<String>()
        repeat(PlainDeckExportLimits.MAX_DESTINATION_ATTEMPTS) { index ->
            val ticket = checkNotNull(f.controller.requestDestination(preview)); tickets += ticket
            f.controller.pickerResult(ticket, DocumentRef("content://selected/$index")); runCurrent()
        }
        assertEquals(32, f.writes.size); assertEquals(32, f.controller.state.value.attempts)
        assertNull(f.controller.requestDestination(preview)); assertEquals(Problem.INVALID_INPUT, f.controller.state.value.problem)
        f.controller.pickerResult(tickets.first(), DocumentRef("content://selected/late")); runCurrent()
        assertEquals(32, f.writes.size)
        f.controller.discard(preview); assertEquals(0, f.controller.state.value.attempts); assertNull(f.controller.state.value.lastWrite)
        val next = f.ready(); val ticket = checkNotNull(f.controller.requestDestination(next))
        assertFalse(ticket in tickets)
        f.controller.pickerResult(tickets.last(), DocumentRef("content://selected/stale")); assertEquals(ticket, f.controller.state.value.pendingPicker)
        f.controller.pickerResult(ticket, DocumentRef("content://selected/0")); runCurrent()
        assertEquals(33, f.writes.size); assertEquals(1, f.controller.state.value.attempts)
    }

    @Test fun alreadyCancelledOwningScopeStillReleasesWriteOwnershipWithUnknownResult() = runTest {
        val scopeJob = SupervisorJob()
        val f = ExportFixture(this, owner = CoroutineScope(scopeJob + StandardTestDispatcher(testScheduler)))
        val preview = f.ready(); scopeJob.cancel()
        f.submit(preview, "content://selected/cancelled-owner"); runCurrent()
        assertTrue(f.writes.isEmpty()); assertEquals(PlainDeckExportWritePhase.IDLE, f.controller.state.value.writePhase)
        assertEquals(PlainDeckWriteResult.PossiblyModified(Problem.UNKNOWN_RESULT), f.controller.state.value.lastWrite)
        f.controller.discard(preview); assertNull(f.controller.state.value.preview)
    }
}

private class ExportFixture(
    private val test: TestScope,
    owner: CoroutineScope = test.backgroundScope,
    nonce: String = "fixture-owner",
) : LocalDeckLibrary, PlainDeckListCodec {
    data class Write(val destination: DocumentRef, val bytes: SnapshotBytes)
    var head = exportDeck()
    var output = PlainDeckListExport(SnapshotBytes("Command Library Deck List 1\nDeck\n2 Same\nSideboard\n3 Same\n".toByteArray()), exportLosses(head))
    val loads = mutableListOf<DeckId>()
    val validated = mutableListOf<DeckSnapshot>()
    val rendered = mutableListOf<DeckSnapshot>()
    val writes = mutableListOf<Write>()
    var load: suspend (DeckId, CancelToken) -> Outcome<DeckSnapshot> = { _, cancel -> cancel.throwIfCancelled(); Outcome.Success(head) }
    var validation: (DeckSnapshot, CancelToken) -> Outcome<Unit> = { _, cancel -> cancel.throwIfCancelled(); Outcome.Success(Unit) }
    var render: (DeckSnapshot, CancelToken) -> Outcome<PlainDeckListExport> = { _, cancel -> cancel.throwIfCancelled(); Outcome.Success(output) }
    var writer: suspend (DocumentRef, SnapshotBytes, CancelToken) -> PlainDeckWriteResult = { _, _, cancel -> cancel.throwIfCancelled(); PlainDeckWriteResult.Written }
    val controller = PlainDeckExportController(owner, this, this,
        { deck, cancel -> validated += deck; validation(deck, cancel) },
        { destination, bytes, cancel -> writes += Write(destination, bytes); writer(destination, bytes, cancel) },
        StandardTestDispatcher(test.testScheduler), nonce)

    fun ready(): PlainDeckExportPreview {
        controller.prepare(head.id, head.revision); test.runCurrent()
        return checkNotNull(controller.state.value.preview)
    }
    fun submit(preview: PlainDeckExportPreview, uri: String) {
        controller.pickerResult(checkNotNull(controller.requestDestination(preview)), DocumentRef(uri))
    }
    override suspend fun load(id: DeckId, cancel: CancelToken): Outcome<DeckSnapshot> { loads += id; return load.invoke(id, cancel) }
    override fun render(deck: DeckSnapshot, cancel: CancelToken): Outcome<PlainDeckListExport> { rendered += deck; return render.invoke(deck, cancel) }
    override suspend fun parse(input: TransferInput, cancel: CancelToken): Outcome<PlainDeckList> = error("No input consumption")
    override suspend fun create(initial: DeckSnapshot, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> = error("No deck mutation")
    override suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<DeckSummary>> = error("No implicit list")
    override suspend fun duplicate(id: DeckId, expected: Revision, name: String, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> = error("No duplicate mutation")
}

private fun exportDeck(raw: String = "deck"): DeckSnapshot {
    fun entry(id: String, quantity: Int, board: BoardRole) = DeckEntry(EntityId(id), CardIdentity(
        Knowledge.Unknown("Oracle not selected", SnapshotList.empty()), Knowledge.Unknown("Printing not selected", SnapshotList.empty()),
        ProviderIdentity(ProviderId("fixture"), "raw", "preserved"), Knowledge.Known("Same"), LanguageTag("ja")),
        quantity, board, Knowledge.Known(Finish.FOIL), "Retained notes", SnapshotList.of(EntryLabel(EntityId("label"), "Label")), SnapshotList.empty())
    return DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE, raw), "Saved deck", Knowledge.Absent("No format"), "Description",
        Knowledge.Known("Folder"), SnapshotList.of("Tag"), UtcTimestamp(1), UtcTimestamp(2), Revision(4),
        SnapshotList.of(entry("original-main", 2, BoardRole.MAIN), entry("original-side", 3, BoardRole.SIDEBOARD)),
        Knowledge.Absent("No budget"), EntityId("profile"), SnapshotList.empty(), SnapshotList.empty(), "Primer")
}

private fun exportLosses(deck: DeckSnapshot): SnapshotList<PlainDeckListLoss> {
    val deckFields = listOf(PlainDeckListLossField.DECK_ID, PlainDeckListLossField.DECK_NAME, PlainDeckListLossField.FORMAT,
        PlainDeckListLossField.DESCRIPTION, PlainDeckListLossField.FOLDER, PlainDeckListLossField.TAGS, PlainDeckListLossField.CREATED_AT,
        PlainDeckListLossField.MODIFIED_AT, PlainDeckListLossField.REVISION, PlainDeckListLossField.BUDGET, PlainDeckListLossField.PROFILE_ID,
        PlainDeckListLossField.CATALOG_REFERENCES, PlainDeckListLossField.PACKAGE_INSTANCES, PlainDeckListLossField.PRIMER,
        PlainDeckListLossField.CATEGORY_ORDER, PlainDeckListLossField.ENTRY_ORDER)
    val entryFields = listOf(PlainDeckListLossField.ENTRY_ID, PlainDeckListLossField.ORACLE_ID, PlainDeckListLossField.PRINTING_ID,
        PlainDeckListLossField.RAW_IDENTITY, PlainDeckListLossField.LANGUAGE, PlainDeckListLossField.FINISH, PlainDeckListLossField.NOTES,
        PlainDeckListLossField.LABELS, PlainDeckListLossField.ROLE_OVERRIDES)
    return SnapshotList.copyOf(deckFields.map { PlainDeckListLoss(null, it) } + deck.entries.flatMap { entry -> entryFields.map { PlainDeckListLoss(entry.id, it) } })
}

private fun exportFailure(code: FailureCode): Outcome.Failure = Outcome.Failure(DomainFailure(code, "Static fixture refusal", SnapshotList.empty()))
