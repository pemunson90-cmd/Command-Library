package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.CatalogImportLimits
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.JobPhase
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.LanguageTag
import com.architectpsg.commandlibrary.feature.shell.MemoryDocuments
import com.architectpsg.commandlibrary.feature.shell.SyntheticCatalog
import com.architectpsg.commandlibrary.feature.shell.document.DOCUMENT_CHUNK_BYTES
import com.architectpsg.commandlibrary.feature.shell.document.DocumentRef
import com.architectpsg.commandlibrary.feature.shell.failure
import com.architectpsg.commandlibrary.feature.shell.syntheticBytes
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportControllerTest {
    private val uri = "content://synthetic.documents/document/catalog.json"
    private val catalog = SyntheticCatalog(listOf("Synthetic Existing Card"))
    private val documents = MemoryDocuments()
    private val finished = AtomicInteger(0)

    private class ClosableStream(bytes: ByteArray) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        val closed = AtomicBoolean(false)
        override fun read(): Int = delegate.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
        override fun close() = closed.set(true)
    }

    private fun TestScope.controller(limits: CatalogImportLimits = CatalogImportLimits()) = ImportController(
        scope = ownerScope(),
        port = catalog,
        documents = documents,
        limits = limits,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        onFinished = { finished.incrementAndGet() },
    )

    @Test
    fun successfulImportStreamsTheWholeFileAndSuppliesOnlyKnownFacts() = runTest {
        val stream = ClosableStream(syntheticBytes(100_000))
        documents.put(uri, MemoryDocuments.Entry("catalog.json", 100_000, { stream }))
        val controller = controller()
        val previous = catalog.active!!.id

        assertTrue(controller.start(DocumentRef(uri)))
        advanceUntilIdle()

        val state = controller.state.value as ImportUiState.Succeeded
        assertEquals("catalog.json", state.fileName)
        assertEquals(100_000L, catalog.bytesReceived)
        assertTrue(catalog.active!!.id != previous)
        assertEquals(catalog.active!!.id, state.result.generation.id)
        val source = catalog.lastSource!!
        assertEquals(uri, source.contentUri.value)
        assertEquals("catalog.json", source.displayName)
        assertEquals("scryfall", source.provider.value)
        assertEquals(LanguageTag.ENGLISH, source.language)
        assertTrue(source.sourceDate is Knowledge.Unknown)
        assertTrue(source.sourceUri is Knowledge.Unknown)
        assertTrue(source.expectedChecksum is Knowledge.Unknown)
        assertTrue(source.expectedBytes is Knowledge.Unknown)
        assertTrue(source.notice is Knowledge.Unknown)
        assertTrue("stream closed", stream.closed.get())
        assertEquals(1, finished.get())
    }

    @Test
    fun runningStateReportsBytesReadAndImporterProgress() = runTest {
        documents.putBytes(uri, "catalog.json", syntheticBytes(3 * DOCUMENT_CHUNK_BYTES + 5))
        catalog.importGate = CompletableDeferred()
        val controller = controller()

        controller.start(DocumentRef(uri))
        advanceUntilIdle()

        val running = controller.state.value as ImportUiState.Running
        assertEquals("catalog.json", running.fileName)
        assertEquals(3L * DOCUMENT_CHUNK_BYTES + 5, running.reportedBytes)
        assertTrue("bytes read reported", running.bytesRead >= DOCUMENT_CHUNK_BYTES)
        assertTrue("bytes read never exceed the file", running.bytesRead <= 3L * DOCUMENT_CHUNK_BYTES + 5)
        assertEquals(JobPhase.STAGING, running.phase)
        assertEquals(1L, running.recordsStaged)
        assertFalse(running.cancelling)
        assertFalse("a second import cannot start while one runs", controller.start(DocumentRef(uri)))

        catalog.importGate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(controller.state.value is ImportUiState.Succeeded)
    }

    @Test
    fun cancelStopsTheImportClosesTheFileAndKeepsTheCommittedCatalog() = runTest {
        val stream = ClosableStream(syntheticBytes(5 * DOCUMENT_CHUNK_BYTES))
        documents.put(uri, MemoryDocuments.Entry("catalog.json", null, { stream }))
        catalog.importGate = CompletableDeferred()
        val previous = catalog.active!!.id
        val controller = controller()
        controller.start(DocumentRef(uri))
        advanceUntilIdle()
        assertTrue(controller.state.value is ImportUiState.Running)

        controller.cancel()
        assertTrue((controller.state.value as? ImportUiState.Running)?.cancelling == true)
        advanceUntilIdle()

        assertEquals(ImportUiState.Cancelled("catalog.json"), controller.state.value)
        assertTrue("importer observed cancellation", catalog.importCancelled.get())
        assertTrue("cancel token reports cancellation", catalog.lastImportToken!!.isCancelled)
        assertTrue("stream closed on cancellation", stream.closed.get())
        assertEquals("previous generation stays active", previous, catalog.active!!.id)
        assertEquals("status is re-read after cancellation", 1, finished.get())
        assertFalse(controller.isRunning)
    }

    @Test
    fun reportedSizeOverTheLimitIsRejectedBeforeReading() = runTest {
        val limits = CatalogImportLimits(maxInputBytes = 1_000)
        documents.putBytes(uri, "big.json", syntheticBytes(10), reportedBytes = 5_000)
        val controller = controller(limits)

        controller.start(DocumentRef(uri))
        advanceUntilIdle()

        assertEquals(ImportUiState.Failed("big.json", ImportFailure.TooLarge(1_000, 5_000)), controller.state.value)
        assertEquals("file never opened", 0, documents.opened.get())
        assertEquals("importer never called", 0, catalog.importCalls.get())
    }

    @Test
    fun fileGrowingPastTheLimitWhileReadingIsReportedAsTooLarge() = runTest {
        val limits = CatalogImportLimits(maxInputBytes = 40_000)
        documents.putBytes(uri, "unsized.json", syntheticBytes(200_000), reportedBytes = null)
        val controller = controller(limits)

        controller.start(DocumentRef(uri))
        advanceUntilIdle()

        assertEquals(ImportUiState.Failed("unsized.json", ImportFailure.TooLarge(40_000, null)), controller.state.value)
        assertTrue(catalog.bytesReceived <= 40_000)
    }

    @Test
    fun importerRejectionIsShownAsItsProblemWithoutDomainText() = runTest {
        documents.putBytes(uri, "catalog.json", syntheticBytes(500))
        catalog.importOutcomeOverride = failure(FailureCode.INVALID_INPUT)
        val controller = controller()

        controller.start(DocumentRef(uri))
        advanceUntilIdle()

        assertEquals(ImportUiState.Failed("catalog.json", ImportFailure.Rejected(Problem.INVALID_INPUT)), controller.state.value)
        assertEquals(1, finished.get())
    }

    @Test
    fun openFailuresNeverReachTheImporter() = runTest {
        documents.put(uri, MemoryDocuments.Entry("denied.json", 10, { throw SecurityException("synthetic denial") }))
        val controller = controller()
        controller.start(DocumentRef(uri))
        advanceUntilIdle()
        assertEquals(ImportUiState.Failed("denied.json", ImportFailure.PermissionDenied), controller.state.value)

        val missing = "content://synthetic.documents/document/missing.json"
        controller.start(DocumentRef(missing))
        advanceUntilIdle()
        assertEquals(ImportUiState.Failed("missing.json", ImportFailure.ReadFailed), controller.state.value)
        assertEquals(0, catalog.importCalls.get())
    }

    @Test
    fun importerThatReturnsWithoutReadingStillClosesTheFile() = runTest {
        val stream = ClosableStream(syntheticBytes(10))
        documents.put(uri, MemoryDocuments.Entry("catalog.json", 10, { stream }))
        val notReading = object : com.architectpsg.commandlibrary.core.model.CatalogImport {
            override suspend fun `import`(
                source: com.architectpsg.commandlibrary.core.model.CatalogImportSource,
                chunks: kotlinx.coroutines.flow.Flow<com.architectpsg.commandlibrary.core.model.ByteChunk>,
                onProgress: (com.architectpsg.commandlibrary.core.model.JobProgress) -> Unit,
                cancel: com.architectpsg.commandlibrary.core.model.CancelToken,
            ) = failure(FailureCode.PERSISTENCE)
        }
        val controller = ImportController(ownerScope(), notReading, documents, CatalogImportLimits(), StandardTestDispatcher(testScheduler)) {}

        controller.start(DocumentRef(uri))
        advanceUntilIdle()

        assertEquals(ImportUiState.Failed("catalog.json", ImportFailure.Rejected(Problem.PERSISTENCE)), controller.state.value)
        assertTrue(stream.closed.get())
    }

    @Test
    fun throwingImporterIsReportedAsUnexpected() = runTest {
        documents.putBytes(uri, "catalog.json", syntheticBytes(10))
        val throwing = object : com.architectpsg.commandlibrary.core.model.CatalogImport {
            override suspend fun `import`(
                source: com.architectpsg.commandlibrary.core.model.CatalogImportSource,
                chunks: kotlinx.coroutines.flow.Flow<com.architectpsg.commandlibrary.core.model.ByteChunk>,
                onProgress: (com.architectpsg.commandlibrary.core.model.JobProgress) -> Unit,
                cancel: com.architectpsg.commandlibrary.core.model.CancelToken,
            ): com.architectpsg.commandlibrary.core.model.Outcome<com.architectpsg.commandlibrary.core.model.CatalogImportResult> =
                throw IllegalStateException("synthetic adapter defect")
        }
        val controller = ImportController(ownerScope(), throwing, documents, CatalogImportLimits(), StandardTestDispatcher(testScheduler)) {}

        controller.start(DocumentRef(uri))
        advanceUntilIdle()

        assertEquals(ImportUiState.Failed("catalog.json", ImportFailure.Rejected(Problem.UNEXPECTED)), controller.state.value)
    }

    @Test
    fun missingDisplayNameFallsBackToTheDocumentIdentifier() = runTest {
        documents.putBytes(uri, null, syntheticBytes(10))
        val controller = controller()
        controller.start(DocumentRef(uri))
        advanceUntilIdle()
        assertEquals("catalog.json", (controller.state.value as ImportUiState.Succeeded).fileName)
    }

    @Test
    fun dismissReturnsToIdleOnlyAfterCompletion() = runTest {
        documents.putBytes(uri, "catalog.json", syntheticBytes(10))
        catalog.importGate = CompletableDeferred()
        val controller = controller()
        controller.start(DocumentRef(uri))
        runCurrent()
        controller.dismiss()
        assertTrue(controller.state.value is ImportUiState.Running)
        catalog.importGate!!.complete(Unit)
        advanceUntilIdle()
        controller.dismiss()
        assertEquals(ImportUiState.Idle, controller.state.value)
    }

    @Test
    fun longDisplayNamesAreShortenedAtACodePointBoundary() {
        val name = "🃏".repeat(300) + ".json"
        val bounded = ImportController.boundedDisplayName(name)
        assertTrue(bounded.toByteArray(Charsets.UTF_8).size <= ImportController.MAX_DISPLAY_NAME_BYTES)
        assertTrue(bounded.endsWith("…"))
        assertEquals("no split surrogate pair", bounded.length - 1, bounded.codePointCount(0, bounded.length - 1) * 2)
        assertEquals("short.json", ImportController.boundedDisplayName("short.json"))
    }
}
