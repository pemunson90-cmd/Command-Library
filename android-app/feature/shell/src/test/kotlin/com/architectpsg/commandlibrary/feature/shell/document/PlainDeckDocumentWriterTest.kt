@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.document

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.PlainDeckListLimits
import com.architectpsg.commandlibrary.core.model.SnapshotBytes
import com.architectpsg.commandlibrary.feature.shell.presentation.Problem
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** Selected-document resource boundary only. No export UI, remote durability or native claim. */
@RunWith(AndroidJUnit4::class)
class PlainDeckDocumentWriterTest {
    /** Every registration owns its file/counters; no static stream registry bypasses the resolver. */
    class SelectedDocumentProvider : ContentProvider() {
        lateinit var file: File
        val modes = mutableListOf<String>()
        val openedUris = mutableListOf<Uri>()
        var openThread: Thread? = null
        override fun onCreate(): Boolean = true
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            modes += mode
            openedUris += uri
            openThread = Thread.currentThread()
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
        }
        override fun getType(uri: Uri): String = "text/plain"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? = error("No metadata access")
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("No insert")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = error("No update")
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = error("No delete or rollback")
    }

    private class Token : CancelToken {
        var cancelled = false
        var thrown: CancellationException? = null
        override val isCancelled: Boolean get() = cancelled
        override fun throwIfCancelled() { thrown?.let { throw it }; if (cancelled) throw CancellationException("Synthetic cancellation") }
    }

    // Extra state prevents coroutine stack-trace recovery from copying this cancellation signal.
    private class TokenCancellation(val signal: Int) : CancellationException("Token cancellation $signal")

    private class Tracked : OutputStream() {
        val accepted = ByteArrayOutputStream()
        val chunks = mutableListOf<Int>()
        var flushes = 0
        var closes = 0
        var onWrite: (ByteArray, Int, Int) -> Unit = { bytes, offset, size -> accepted.write(bytes, offset, size) }
        var onFlush: () -> Unit = {}
        var onClose: () -> Unit = {}
        override fun write(value: Int) { error("Must use bounded bulk writes") }
        override fun write(bytes: ByteArray, offset: Int, size: Int) { chunks += size; onWrite(bytes, offset, size) }
        override fun flush() { flushes++; onFlush() }
        override fun close() { closes++; onClose() }
    }

    private val ref = DocumentRef("content://plain.writer.selected/document/deck")
    private val small = SnapshotBytes(byteArrayOf(1, 2, 3))
    private suspend fun cancellation(action: suspend () -> Unit): CancellationException {
        try { action() } catch (expected: CancellationException) { return expected }
        error("Cancellation must propagate")
    }

    @Test fun actualResolverUsesWtTruncatesAndWritesExactBytesOnIoDispatcher() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = Robolectric.setupContentProvider(SelectedDocumentProvider::class.java, "plain.writer.selected")
        provider.file = File.createTempFile("plain-deck-writer-", ".txt", context.cacheDir)
        provider.file.writeBytes(ByteArray(100_000) { 99 })
        val expected = ByteArray(DOCUMENT_CHUNK_BYTES + 13) { (it % 127).toByte() }
        val callerThread = Thread.currentThread()
        val writer = PlainDeckDocumentWriter(context.contentResolver, Dispatchers.IO)
        assertEquals(PlainDeckWriteResult.Written, writer.write(ref, SnapshotBytes(expected), Token()))
        assertArrayEquals(expected, provider.file.readBytes())
        assertNotSame(callerThread, provider.openThread)
        assertEquals(PlainDeckWriteResult.Written, writer.write(ref, SnapshotBytes(byteArrayOf()), Token()))
        assertEquals(0L, provider.file.length())
        assertEquals(listOf("wt", "wt"), provider.modes)
        assertEquals(listOf(Uri.parse(ref.uri), Uri.parse(ref.uri)), provider.openedUris)
    }

    @Test fun exactLimitWritesBoundedChunksAndFlushesClosesOnceWithoutRetry() = runTest {
        val stream = Tracked(); var opens = 0
        val raw = ByteArray(PlainDeckListLimits.MAX_BYTES) { (it % 251).toByte() }
        val snapshot = SnapshotBytes(raw)
        val writer = PlainDeckDocumentWriter(StandardTestDispatcher(testScheduler)) { selected ->
            assertEquals(ref, selected); opens++; raw.fill(0); stream
        }
        assertEquals(PlainDeckWriteResult.Written, writer.write(ref, snapshot, Token()))
        assertArrayEquals(snapshot.copyBytes(), stream.accepted.toByteArray())
        assertEquals(List(PlainDeckListLimits.MAX_BYTES / DOCUMENT_CHUNK_BYTES) { DOCUMENT_CHUNK_BYTES }, stream.chunks)
        assertEquals(1, opens); assertEquals(1, stream.flushes); assertEquals(1, stream.closes)
    }

    @Test fun emptyBytesStillOpenFlushAndCloseExactlyOnce() = runTest {
        val stream = Tracked(); var opens = 0
        val writer = PlainDeckDocumentWriter(StandardTestDispatcher(testScheduler)) { opens++; stream }
        assertEquals(PlainDeckWriteResult.Written, writer.write(ref, SnapshotBytes(byteArrayOf()), Token()))
        assertTrue(stream.chunks.isEmpty()); assertEquals(1, opens)
        assertEquals(1, stream.flushes); assertEquals(1, stream.closes)
    }

    @Test fun oversizedBytesAreNotStartedWithZeroDestinationAccess() = runTest {
        var opens = 0
        val writer = PlainDeckDocumentWriter(StandardTestDispatcher(testScheduler)) { opens++; error("Must not open") }
        assertEquals(PlainDeckWriteResult.NotStarted(Problem.INVALID_INPUT),
            writer.write(ref, SnapshotBytes(ByteArray(PlainDeckListLimits.MAX_BYTES + 1)), Token()))
        assertEquals(0, opens)
    }

    @Test fun tokenCancellationBeforeOpenPropagatesEvenWhenJobRemainsActive() = runTest {
        var opens = 0
        val writer = PlainDeckDocumentWriter(StandardTestDispatcher(testScheduler)) { opens++; error("Must not open") }
        val expected = TokenCancellation(1)
        assertSame(expected, cancellation { writer.write(ref, small, Token().apply { thrown = expected }) })
        assertTrue(currentCoroutineContext().isActive); assertEquals(0, opens)
    }

    @Test fun cancelledJobBeforeDispatchNeverAccessesDestinationOrReturnsResult() = runTest {
        var opens = 0; var returned = false
        val writer = PlainDeckDocumentWriter(StandardTestDispatcher(testScheduler)) { opens++; Tracked() }
        val job = backgroundScope.launch { writer.write(ref, small, Token()); returned = true }
        job.cancel(); runCurrent()
        assertTrue(job.isCancelled); assertFalse(returned); assertEquals(0, opens)
    }

    @Test fun nullAndThrowingOpenArePossiblyModifiedAndNeverRetried() = runTest {
        for (failure in listOf(null, IOException("Private provider detail"), SecurityException("Private URI"), IllegalStateException("Private failure"))) {
            var opens = 0
            val writer = PlainDeckDocumentWriter(StandardTestDispatcher(testScheduler)) { opens++; if (failure != null) throw failure else null }
            val problem = when (failure) { is SecurityException -> Problem.PERMISSION; is IllegalStateException -> Problem.UNEXPECTED; else -> Problem.PERSISTENCE }
            assertEquals(PlainDeckWriteResult.PossiblyModified(problem), writer.write(ref, small, Token()))
            assertEquals(1, opens)
        }
    }

    @Test fun partialWriteFailureDoesNotFlushOrRetryAndClosesExactlyOnce() = runTest {
        val stream = Tracked().apply { onWrite = { bytes, offset, _ -> accepted.write(bytes, offset, 1); throw IOException("Partial") } }
        var opens = 0
        val writer = PlainDeckDocumentWriter(StandardTestDispatcher(testScheduler)) { opens++; stream }
        assertEquals(PlainDeckWriteResult.PossiblyModified(Problem.PERSISTENCE), writer.write(ref, small, Token()))
        assertArrayEquals(byteArrayOf(1), stream.accepted.toByteArray())
        assertEquals(1, opens); assertEquals(0, stream.flushes); assertEquals(1, stream.closes)
    }

    @Test fun flushAndCloseFailuresPreventWrittenAndEachCloseRunsExactlyOnce() = runTest {
        for (failFlush in listOf(true, false)) {
            val stream = Tracked().apply {
                if (failFlush) onFlush = { throw IOException("Flush failed") }
                onClose = { throw IOException("Close failed") }
            }
            var opens = 0
            val writer = PlainDeckDocumentWriter(StandardTestDispatcher(testScheduler)) { opens++; stream }
            assertEquals(PlainDeckWriteResult.PossiblyModified(Problem.PERSISTENCE), writer.write(ref, small, Token()))
            assertArrayEquals(small.copyBytes(), stream.accepted.toByteArray())
            assertEquals(1, opens); assertEquals(1, stream.flushes); assertEquals(1, stream.closes)
        }
    }

    @Test fun originalFailureClassificationSurvivesCleanupFailure() = runTest {
        val stream = Tracked().apply {
            onWrite = { _, _, _ -> throw SecurityException("Primary") }
            onClose = { throw IOException("Secondary") }
        }
        val writer = PlainDeckDocumentWriter(StandardTestDispatcher(testScheduler)) { stream }
        assertEquals(PlainDeckWriteResult.PossiblyModified(Problem.PERMISSION), writer.write(ref, small, Token()))
        assertEquals(0, stream.flushes); assertEquals(1, stream.closes)
    }

    @Test fun jobCancelledDuringOpenClosesReturnedStreamWithoutWritingOrReturning() = runTest {
        val stream = Tracked(); var opens = 0; var returned = false
        lateinit var job: Job
        val writer = PlainDeckDocumentWriter(StandardTestDispatcher(testScheduler)) { opens++; job.cancel(); stream }
        job = backgroundScope.launch { writer.write(ref, small, Token()); returned = true }
        runCurrent()
        assertTrue(job.isCancelled); assertFalse(returned); assertEquals(1, opens)
        assertTrue(stream.chunks.isEmpty()); assertEquals(0, stream.flushes); assertEquals(1, stream.closes)
    }

    @Test fun tokenCancellationBetweenChunksPropagatesAndSuppressesCloseFailure() = runTest {
        val token = Token(); val closeFailure = IOException("Close detail")
        val expected = TokenCancellation(2)
        val stream = Tracked().apply {
            onWrite = { bytes, offset, count -> accepted.write(bytes, offset, count); token.thrown = expected }
            onClose = { throw closeFailure }
        }
        var opens = 0
        val writer = PlainDeckDocumentWriter(StandardTestDispatcher(testScheduler)) { opens++; stream }
        assertSame(expected, cancellation { writer.write(ref, SnapshotBytes(ByteArray(DOCUMENT_CHUNK_BYTES + 1)), token) })
        assertEquals(listOf(closeFailure), expected.suppressed.toList())
        assertEquals(listOf(DOCUMENT_CHUNK_BYTES), stream.chunks)
        assertEquals(1, opens); assertEquals(0, stream.flushes); assertEquals(1, stream.closes)
    }

    @Test fun jobCancellationDuringWriteCannotReturnFailureEvenIfWriteAndCloseThrow() = runTest {
        val stream = Tracked(); var returned = false
        lateinit var job: Job
        stream.onWrite = { _, _, _ -> job.cancel(); throw IOException("Write failed during cancellation") }
        stream.onClose = { throw IOException("Close failed too") }
        val writer = PlainDeckDocumentWriter(StandardTestDispatcher(testScheduler)) { stream }
        job = backgroundScope.launch { writer.write(ref, small, Token()); returned = true }
        runCurrent()
        assertTrue(job.isCancelled); assertFalse(returned)
        assertEquals(0, stream.flushes); assertEquals(1, stream.closes)
    }

    @Test fun cancellationInFlushOrCloseNeverReturnsWrittenAndStillClosesOnce() = runTest {
        for (atFlush in listOf(true, false)) {
            val token = Token()
            val stream = Tracked().apply {
                if (atFlush) onFlush = { token.cancelled = true } else onClose = { token.cancelled = true }
            }
            var opens = 0
            val writer = PlainDeckDocumentWriter(StandardTestDispatcher(testScheduler)) { opens++; stream }
            cancellation { writer.write(ref, small, token) }
            assertEquals(1, opens); assertEquals(1, stream.flushes); assertEquals(1, stream.closes)
        }
    }

    @Test fun jobCancelledDuringCloseCannotReturnWrittenAfterAllBytesWereAccepted() = runTest {
        val stream = Tracked(); var returned = false
        lateinit var job: Job
        stream.onClose = { job.cancel() }
        val writer = PlainDeckDocumentWriter(StandardTestDispatcher(testScheduler)) { stream }
        job = backgroundScope.launch { writer.write(ref, small, Token()); returned = true }
        runCurrent()
        assertTrue(job.isCancelled); assertFalse(returned)
        assertArrayEquals(small.copyBytes(), stream.accepted.toByteArray())
        assertEquals(1, stream.flushes); assertEquals(1, stream.closes)
    }
}
