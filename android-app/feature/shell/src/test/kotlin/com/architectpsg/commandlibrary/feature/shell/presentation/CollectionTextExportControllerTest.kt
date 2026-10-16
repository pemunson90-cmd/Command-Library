@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.document.DocumentRef
import com.architectpsg.commandlibrary.feature.shell.document.PlainDeckWriteResult
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic ports prove controller authority/ownership only; no encoder, SAF or durability claim. */
class CollectionTextExportControllerTest {
    @Test fun privateFieldsRequireSeparateExplicitOptInsAndCaptureIsFrozenBeforeAnyIo() = runTest {
        val f = CollectionExportFixture(this)
        val original = f.head
        assertFalse(f.controller.state.value.includeNotes); assertFalse(f.controller.state.value.includeLocations)
        f.controller.prepare(); runCurrent() // A hidden action confers no preparation authority.
        assertEquals(0, f.reads)
        val omitted = f.ready()
        assertEquals(listOf(CollectionRender(original, false, false)), f.renders)
        assertFalse(omitted.artifact.bytes.text().contains("PRIVATE-NOTES"))
        assertFalse(omitted.artifact.bytes.text().contains("PRIVATE-LOCATION"))
        assertTrue(f.writes.isEmpty())
        f.controller.discard(omitted); f.controller.open(); f.controller.notes(true)
        val notes = f.ready()
        assertTrue(notes.includeNotes); assertFalse(notes.includeLocations)
        assertTrue(notes.artifact.bytes.text().contains("PRIVATE-NOTES"))
        assertFalse(notes.artifact.bytes.text().contains("PRIVATE-LOCATION"))
        f.controller.discard(notes); f.controller.open(); f.controller.notes(true); f.controller.locations(true)
        val captured = f.ready()
        assertSame(original, captured.source)
        assertTrue(captured.includeNotes); assertTrue(captured.includeLocations)
        assertTrue(captured.artifact.bytes.text().contains("PRIVATE-LOCATION"))
        f.controller.notes(false); f.controller.locations(false) // Frozen preview cannot change consent or bytes.
        assertTrue(f.controller.state.value.includeNotes); assertTrue(f.controller.state.value.includeLocations)
        f.head = original.copy(revision = Revision(99), lots = SnapshotList.empty())
        val ticket = checkNotNull(f.controller.requestDestination(captured))
        assertTrue(f.writes.isEmpty()) // Requesting a picker is not authorization to open a document.
        f.controller.pickerResult(ticket, DocumentRef("content://selected/frozen")); runCurrent()
        assertEquals(3, f.reads); assertEquals(3, f.renders.size)
        assertSame(captured.artifact.bytes, f.writes.single().bytes)
        assertEquals(PlainDeckWriteResult.Written, f.controller.state.value.lastWrite)
        assertEquals(2, original.lots.single().quantity)
        assertEquals("PRIVATE-NOTES", original.lots.single().notes)
        assertEquals("PRIVATE-LOCATION", original.lots.single().location)
        assertEquals(0, f.mutations)
    }

    @Test fun exactPreviewOwnerNonceOnceOnlyCallbackAndStaleRenderedFramesFenceSelection() = runTest {
        val f = CollectionExportFixture(this, nonce = "owner-A"); val preview = f.ready()
        assertNull(f.controller.requestDestination(preview.copy()))
        val oldFrame = f.controller.state.value
        val ticket = checkNotNull(f.controller.requestDestination(preview))
        val other = CollectionExportFixture(this, nonce = "owner-B"); val otherPreview = other.ready()
        val otherTicket = checkNotNull(other.controller.requestDestination(otherPreview))
        assertNotEquals(ticket, otherTicket)
        f.controller.rendered(oldFrame) { f.controller.hide() }
        assertTrue(f.controller.state.value.visible)
        f.controller.pickerResult(otherTicket, DocumentRef("content://selected/wrong-owner"))
        f.controller.pickerFailed(otherTicket, Problem.PERMISSION)
        assertEquals(ticket, f.controller.state.value.pendingPicker)
        f.controller.pickerResult(ticket, DocumentRef("content://selected/once"))
        f.controller.pickerResult(ticket, DocumentRef("content://selected/replay")); runCurrent()
        assertEquals(listOf("content://selected/once"), f.writes.map { it.destination.uri })
        val current = f.controller.state.value
        f.controller.rendered(current) { f.controller.hide() }
        assertFalse(f.controller.state.value.visible)
        f.controller.rendered(f.controller.state.value) { f.controller.open() }
        assertFalse(f.controller.state.value.visible)
        assertTrue(other.writes.isEmpty())
        other.controller.pickerResult(otherTicket, null)
    }

    @Test fun hideRevokesPickerEvenAfterReopenAndFailureOrNullConsumeOnlyTheirOwnTicket() = runTest {
        val f = CollectionExportFixture(this); val preview = f.ready()
        val revoked = checkNotNull(f.controller.requestDestination(preview))
        f.controller.hide(); f.controller.open()
        assertFalse(f.controller.state.value.pickerAuthorized)
        f.controller.pickerResult(revoked, DocumentRef("content://selected/revoked")); runCurrent()
        assertTrue(f.writes.isEmpty()); assertNull(f.controller.state.value.pendingPicker)
        val failed = checkNotNull(f.controller.requestDestination(preview))
        f.controller.pickerFailed(revoked, Problem.PERSISTENCE)
        assertEquals(failed, f.controller.state.value.pendingPicker)
        f.controller.pickerFailed(failed, Problem.PERMISSION)
        assertEquals(Problem.PERMISSION, f.controller.state.value.problem)
        val revokedFailure = checkNotNull(f.controller.requestDestination(preview))
        f.controller.hide(); f.controller.open(); f.controller.pickerFailed(revokedFailure, Problem.PERSISTENCE)
        assertNull(f.controller.state.value.problem)
        val cancelled = checkNotNull(f.controller.requestDestination(preview))
        f.controller.pickerResult(cancelled, null)
        assertNull(f.controller.state.value.pendingPicker); assertTrue(f.writes.isEmpty())
        f.submit(preview, "content://selected/explicit"); runCurrent()
        assertEquals(1, f.writes.size)
    }

    @Test fun incompleteSourceAndMalformedArtifactsCannotReachPreviewPickerOrWriter() = runTest {
        val incomplete = CollectionExportFixture(this); incomplete.head = incomplete.head.copy(complete = false)
        incomplete.controller.open(); incomplete.controller.prepare(); runCurrent()
        assertEquals(Problem.CORRUPT_DATA, incomplete.controller.state.value.problem)
        assertNull(incomplete.controller.state.value.preview); assertFalse(incomplete.controller.state.value.preparing)
        assertTrue(incomplete.renders.isEmpty()); assertTrue(incomplete.writes.isEmpty())
        val valid = collectionArtifact(collectionExportSource(), false, false)
        val malformed = listOf(
            valid.copy(bytes = SnapshotBytes(byteArrayOf())),
            valid.copy(bytes = SnapshotBytes(ByteArray(PlainDeckListLimits.MAX_BYTES + 1))),
            valid.copy(bytes = SnapshotBytes("changed after digest".toByteArray())),
            valid.copy(checksum = ContentHash("SHA-256", "0".repeat(64))),
            valid.copy(checksum = ContentHash("SHA-512", valid.checksum.hex)),
            valid.copy(fileName = "private-name.txt"), valid.copy(mediaType = "text/csv"),
            valid.copy(losses = SnapshotList.copyOf(List(65) { "static loss" })),
            valid.copy(losses = SnapshotList.of("x".repeat(4097))),
        )
        for (artifact in malformed) {
            val f = CollectionExportFixture(this); f.render = { _, _, _, _ -> Outcome.Success(artifact) }
            f.controller.open(); f.controller.prepare(); runCurrent()
            assertEquals(Problem.CORRUPT_DATA, f.controller.state.value.problem)
            assertNull(f.controller.state.value.preview); assertFalse(f.controller.state.value.preparing)
            assertTrue(f.writes.isEmpty()); assertEquals(0, f.mutations)
        }
    }

    @Test fun typedReadAndRenderRefusalsStayDistinctAndNeverStartIo() = runTest {
        val cases = listOf(
            Outcome.Failure(DomainFailure(FailureCode.CORRUPT_DATA, "Static refusal", SnapshotList.empty())) to Problem.CORRUPT_DATA,
            Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Static refusal") to Problem.UNSUPPORTED,
            Outcome.Unknown("Static uncertainty", SnapshotList.empty()) to Problem.UNKNOWN_RESULT,
        )
        for ((outcome, problem) in cases) {
            val read = CollectionExportFixture(this); read.load = { _ -> outcome }
            read.controller.open(); read.controller.prepare(); runCurrent()
            assertEquals(problem, read.controller.state.value.problem); assertTrue(read.renders.isEmpty())
            val render = CollectionExportFixture(this); render.render = { _, _, _, _ -> outcome }
            render.controller.open(); render.controller.prepare(); runCurrent()
            assertEquals(problem, render.controller.state.value.problem); assertNull(render.controller.state.value.preview)
            assertTrue(read.writes.isEmpty()); assertTrue(render.writes.isEmpty())
        }
    }

    @Test fun cancelledNoncooperativePreparationCannotPublishOrClearItsPendingSuccessor() = runTest {
        val f = CollectionExportFixture(this); val oldSource = f.head
        val oldGate = CompletableDeferred<Unit>(); val nextGate = CompletableDeferred<Unit>()
        f.load = { _ -> withContext(NonCancellable) { oldGate.await() }; Outcome.Success(oldSource) }
        f.controller.open(); f.controller.prepare(); runCurrent()
        assertTrue(f.controller.state.value.preparing)
        f.controller.cancelPreparation()
        f.head = oldSource.copy(revision = Revision(5), lots = SnapshotList.empty())
        val nextSource = f.head
        f.load = { _ -> nextGate.await(); Outcome.Success(nextSource) }
        f.controller.locations(true); f.controller.prepare(); runCurrent()
        oldGate.complete(Unit); runCurrent()
        assertTrue(f.controller.state.value.preparing); assertNull(f.controller.state.value.preview)
        assertTrue(f.renders.isEmpty())
        nextGate.complete(Unit); runCurrent()
        val preview = checkNotNull(f.controller.state.value.preview)
        assertSame(nextSource, preview.source); assertTrue(preview.includeLocations)
        assertFalse(f.controller.state.value.preparing)
        assertEquals(listOf(CollectionRender(nextSource, false, true)), f.renders)
        assertEquals(2, oldSource.lots.single().quantity); assertEquals(0, f.mutations)
        assertTrue(f.writes.isEmpty())
    }

    @Test fun cancellationRetainsWriteOwnerUntilNoncooperativeWorkAndCleanupHaveBothFinished() = runTest {
        val f = CollectionExportFixture(this); val preview = f.ready()
        val work = CompletableDeferred<Unit>(); val close = CompletableDeferred<Unit>()
        var cleanupStarted = false; var cleanupFinished = false; var writerJob: Job? = null
        f.writer = { _, _, cancel ->
            writerJob = currentCoroutineContext().job
            try {
                withContext(NonCancellable) { work.await() }
                PlainDeckWriteResult.Written
            } finally {
                withContext(NonCancellable) {
                    cleanupStarted = true; close.await()
                    assertTrue(cancel.isCancelled); cleanupFinished = true
                }
            }
        }
        f.submit(preview, "content://selected/cancelled"); runCurrent()
        assertEquals(PlainDeckExportWritePhase.WRITING, f.controller.state.value.writePhase)
        f.controller.cancelWrite(); runCurrent()
        assertFalse(checkNotNull(writerJob).isActive)
        assertFalse(cleanupFinished); assertNull(f.controller.state.value.lastWrite)
        assertEquals(PlainDeckExportWritePhase.CANCELLING, f.controller.state.value.writePhase)
        f.controller.discard(preview); f.controller.prepare(); f.controller.hide(); f.controller.open()
        assertSame(preview, f.controller.state.value.preview); assertNull(f.controller.requestDestination(preview))
        work.complete(Unit); runCurrent()
        assertTrue(cleanupStarted); assertFalse(cleanupFinished)
        assertEquals(PlainDeckExportWritePhase.CANCELLING, f.controller.state.value.writePhase)
        f.controller.discard(preview); assertSame(preview, f.controller.state.value.preview)
        assertNull(f.controller.requestDestination(preview)); assertEquals(1, f.writes.size)
        close.complete(Unit); runCurrent()
        assertTrue(cleanupFinished); assertEquals(PlainDeckExportWritePhase.IDLE, f.controller.state.value.writePhase)
        assertEquals(PlainDeckWriteResult.PossiblyModified(Problem.UNKNOWN_RESULT), f.controller.state.value.lastWrite)
        f.writer = { _, _, _ -> PlainDeckWriteResult.Written }
        f.submit(preview, "content://selected/new-destination"); runCurrent()
        assertEquals(2, f.writes.size); assertEquals(PlainDeckWriteResult.Written, f.controller.state.value.lastWrite)
        assertTrue(f.writes.all { it.bytes === preview.artifact.bytes })
    }

    @Test fun typedWriteResultsRemainDistinctAndThrownCancellationOrFailureNeverClaimsRollback() = runTest {
        val outcomes = listOf(PlainDeckWriteResult.NotStarted(Problem.PERMISSION),
            PlainDeckWriteResult.PossiblyModified(Problem.PERSISTENCE), PlainDeckWriteResult.Written)
        for (outcome in outcomes) {
            val f = CollectionExportFixture(this); val preview = f.ready()
            f.writer = { _, _, _ -> outcome }; f.submit(preview, "content://selected/typed"); runCurrent()
            assertEquals(outcome, f.controller.state.value.lastWrite)
            assertEquals(PlainDeckExportWritePhase.IDLE, f.controller.state.value.writePhase)
            val problem = when (outcome) {
                is PlainDeckWriteResult.NotStarted -> outcome.problem
                is PlainDeckWriteResult.PossiblyModified -> outcome.problem
                PlainDeckWriteResult.Written -> null
            }
            assertEquals(problem, f.controller.state.value.problem)
        }
        for ((error, expected) in listOf(
            IllegalStateException("Synthetic write failure") to Problem.UNEXPECTED,
            CancellationException("Synthetic writer cancellation") to Problem.UNKNOWN_RESULT,
        )) {
            val f = CollectionExportFixture(this); val preview = f.ready(); var closed = false
            f.writer = { _, _, _ -> try { throw error } finally { closed = true } }
            f.submit(preview, "content://selected/thrown"); runCurrent()
            assertTrue(closed)
            assertEquals(PlainDeckWriteResult.PossiblyModified(expected), f.controller.state.value.lastWrite)
            assertEquals(PlainDeckExportWritePhase.IDLE, f.controller.state.value.writePhase)
        }
    }

    @Test fun reusedDestinationIsRejectedEvenAfterNotStartedAndOnlyExactDiscardResetsExport() = runTest {
        val f = CollectionExportFixture(this); val preview = f.ready()
        f.writer = { _, _, _ -> PlainDeckWriteResult.NotStarted(Problem.PERMISSION) }
        f.submit(preview, "content://selected/A"); runCurrent()
        f.writer = { _, _, _ -> PlainDeckWriteResult.Written }
        f.submit(preview, "content://selected/B"); runCurrent()
        val previous = f.controller.state.value.lastWrite
        f.submit(preview, "content://selected/A"); runCurrent()
        assertEquals(listOf("content://selected/A", "content://selected/B"), f.writes.map { it.destination.uri })
        assertSame(previous, f.controller.state.value.lastWrite); assertEquals(Problem.INVALID_INPUT, f.controller.state.value.problem)
        f.controller.discard(preview.copy()); assertSame(preview, f.controller.state.value.preview)
        val pending = checkNotNull(f.controller.requestDestination(preview))
        f.controller.discard(preview); assertSame(preview, f.controller.state.value.preview)
        f.controller.pickerResult(pending, null); f.controller.discard(preview)
        assertNull(f.controller.state.value.preview); assertNull(f.controller.state.value.lastWrite)
        assertFalse(f.controller.state.value.includeNotes); assertFalse(f.controller.state.value.includeLocations)
        val next = f.ready(); val ticket = checkNotNull(f.controller.requestDestination(next))
        assertNotEquals(pending, ticket)
        f.controller.pickerResult(pending, DocumentRef("content://selected/stale"))
        assertEquals(ticket, f.controller.state.value.pendingPicker)
        f.controller.pickerResult(ticket, DocumentRef("content://selected/A")); runCurrent()
        assertEquals(3, f.writes.size)
    }

    @Test fun cancelledOwningScopeDoesNotLeaveAnUnreleasableWriteOwner() = runTest {
        val job = SupervisorJob()
        val f = CollectionExportFixture(this, CoroutineScope(job + StandardTestDispatcher(testScheduler)))
        val preview = f.ready(); job.cancel()
        f.submit(preview, "content://selected/cancelled-scope"); runCurrent()
        assertTrue(f.writes.isEmpty()); assertEquals(PlainDeckExportWritePhase.IDLE, f.controller.state.value.writePhase)
        assertEquals(PlainDeckWriteResult.PossiblyModified(Problem.UNKNOWN_RESULT), f.controller.state.value.lastWrite)
        f.controller.discard(preview); assertNull(f.controller.state.value.preview)
    }
}

private data class CollectionRender(val source: InventorySnapshot, val notes: Boolean, val locations: Boolean)
private class CollectionExportFixture(
    private val test: TestScope,
    owner: CoroutineScope = test.backgroundScope,
    nonce: String = "collection-owner",
) : LocalInventory {
    data class Write(val destination: DocumentRef, val bytes: SnapshotBytes)
    var head = collectionExportSource()
    var reads = 0
    var mutations = 0
    val renders = mutableListOf<CollectionRender>()
    val writes = mutableListOf<Write>()
    var load: suspend (CancelToken) -> Outcome<InventorySnapshot> = { cancel -> cancel.throwIfCancelled(); Outcome.Success(head) }
    var render: (InventorySnapshot, Boolean, Boolean, CancelToken) -> Outcome<ByteArtifact> = { source, notes, locations, cancel ->
        cancel.throwIfCancelled(); Outcome.Success(collectionArtifact(source, notes, locations))
    }
    var writer: suspend (DocumentRef, SnapshotBytes, CancelToken) -> PlainDeckWriteResult = { _, _, cancel ->
        cancel.throwIfCancelled(); PlainDeckWriteResult.Written
    }
    val controller = CollectionTextExportController(owner, this,
        { source, notes, locations, cancel -> renders += CollectionRender(source, notes, locations); render(source, notes, locations, cancel) },
        { destination, bytes, cancel -> writes += Write(destination, bytes); writer(destination, bytes, cancel) },
        StandardTestDispatcher(test.testScheduler), nonce)
    fun ready(): CollectionTextExportPreview {
        controller.open(); controller.prepare(); test.runCurrent()
        return checkNotNull(controller.state.value.preview)
    }
    fun submit(preview: CollectionTextExportPreview, uri: String) {
        controller.pickerResult(checkNotNull(controller.requestDestination(preview)), DocumentRef(uri))
    }
    override suspend fun snapshot(cancel: CancelToken): Outcome<InventorySnapshot> { reads++; return load(cancel) }
    override suspend fun apply(patch: CollectionPatch, expected: Revision, operationId: EntityId, cancel: CancelToken): Outcome<InventoryApplyResult> {
        mutations++; error("Export must not apply inventory edits")
    }
    override suspend fun undo(expected: Revision, operationId: EntityId, cancel: CancelToken): Outcome<InventoryCommit> {
        mutations++; error("Export must not undo inventory edits")
    }
}

private fun collectionExportSource() = InventorySnapshot(Revision(4), UtcTimestamp(123), SnapshotList.of(
    OwnedLot(EntityId("original-lot"), CardIdentity(Knowledge.Absent("No oracle"), Knowledge.Absent("No printing"),
        null, Knowledge.Known("Saved card"), LanguageTag.ENGLISH), Knowledge.Known(Finish.NONFOIL),
        CardCondition.NEAR_MINT, "PRIVATE-LOCATION", 2, "PRIVATE-NOTES", Revision(3), Knowledge.Absent("Not recorded"))), true)

// A small honest renderer double, not a substitute proof of the production text grammar/privacy.
private fun collectionArtifact(source: InventorySnapshot, notes: Boolean, locations: Boolean): ByteArtifact {
    val text = buildString {
        append("Synthetic collection revision "); append(source.revision.value); append('\n')
        for (lot in source.lots) {
            append(lot.id.value); append(':'); append(lot.quantity); append('\n')
            if (notes) { append(lot.notes); append('\n') }
            if (locations) { append(lot.location); append('\n') }
        }
    }
    val bytes = text.toByteArray(Charsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
    return ByteArtifact("command-library-collection-r${source.revision.value}.txt", "text/plain;charset=utf-8",
        SnapshotBytes(bytes), ContentHash("SHA-256", digest), null, SnapshotList.of("Synthetic renderer; not a backup"))
}
private fun SnapshotBytes.text(): String = copyBytes().toString(Charsets.UTF_8)
