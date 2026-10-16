package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.document.DocumentRef
import com.architectpsg.commandlibrary.feature.shell.document.PlainDeckWriteResult
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionTextExportAdmissionTest {
    private class Inventory : LocalInventory {
        var reads = 0
        var wait: CompletableDeferred<Unit>? = null
        val source = InventorySnapshot(Revision(1), UtcTimestamp(1), SnapshotList.empty(), true)
        override suspend fun snapshot(cancel: CancelToken): Outcome<InventorySnapshot> {
            reads++; wait?.await(); return Outcome.Success(source)
        }
        override suspend fun apply(patch: CollectionPatch, expected: Revision, operationId: EntityId, cancel: CancelToken): Outcome<InventoryApplyResult> = error("Export must not write inventory")
        override suspend fun undo(expected: Revision, operationId: EntityId, cancel: CancelToken): Outcome<InventoryCommit> = error("Export must not undo inventory")
    }
    private fun artifact(source: InventorySnapshot): Outcome<ByteArtifact> {
        val bytes = "Captured synthetic collection".toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        return Outcome.Success(ByteArtifact("command-library-collection-r${source.revision.value}.txt", "text/plain;charset=utf-8",
            SnapshotBytes(bytes), ContentHash("SHA-256", hash), null, SnapshotList.empty()))
    }
    @Test fun sharedWriterBlocksNewPreparationAndRevokesLatePickerWithoutOpeningAFile() = runTest {
        val gate = InventoryMutationGate(); val inventory = Inventory(); var writes = 0
        val controller = CollectionTextExportController(this, inventory, { s, _, _, _ -> artifact(s) },
            { _, _, _ -> writes++; PlainDeckWriteResult.Written }, StandardTestDispatcher(testScheduler), "owner",
            inventoryReady = { !gate.busy.value })
        val first = checkNotNull(gate.tryAcquire())
        controller.open(); controller.prepare(); runCurrent()
        assertEquals(0, inventory.reads); assertNull(controller.state.value.preview)
        first.release(); controller.prepare(); runCurrent()
        val preview = checkNotNull(controller.state.value.preview)
        val ticket = checkNotNull(controller.requestDestination(preview))
        val uncertainWriter = checkNotNull(gate.tryAcquire())
        controller.pickerResult(ticket, DocumentRef("content://selected/export")); runCurrent()
        assertEquals(0, writes); assertNull(controller.state.value.pendingPicker)
        assertEquals(Problem.STALE_REVISION, controller.state.value.problem)
        assertTrue(uncertainWriter.owns)
        uncertainWriter.release()
        controller.pickerResult(ticket, DocumentRef("content://selected/export")); runCurrent()
        assertEquals(0, writes)
        val explicitRetry = checkNotNull(controller.requestDestination(preview))
        controller.pickerResult(explicitRetry, DocumentRef("content://selected/new-export")); runCurrent()
        assertEquals(1, writes); assertSame(inventory.source, preview.source)
    }
    @Test fun writerStartingWhileSnapshotIsSuspendedPreventsAnAuthorizedPreview() = runTest {
        val gate = InventoryMutationGate(); val inventory = Inventory()
        val wait = CompletableDeferred<Unit>(); inventory.wait = wait
        var renders = 0; var writes = 0
        val controller = CollectionTextExportController(this, inventory, { s, _, _, _ -> renders++; artifact(s) },
            { _, _, _ -> writes++; PlainDeckWriteResult.Written }, StandardTestDispatcher(testScheduler), "owner",
            inventoryReady = { !gate.busy.value })
        controller.open(); controller.prepare(); runCurrent()
        assertEquals(1, inventory.reads); assertTrue(controller.state.value.preparing)
        val writer = checkNotNull(gate.tryAcquire()); wait.complete(Unit); runCurrent()
        assertEquals(0, renders); assertEquals(0, writes); assertNull(controller.state.value.preview)
        assertEquals(Problem.STALE_REVISION, controller.state.value.problem); assertTrue(writer.owns)
        writer.release(); controller.prepare(); runCurrent()
        assertNotNull(controller.state.value.preview); assertEquals(1, renders); assertEquals(0, writes)
    }
}
