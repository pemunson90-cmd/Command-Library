package com.architectpsg.commandlibrary.feature.shell.document

import com.architectpsg.commandlibrary.core.model.ByteChunk
import com.architectpsg.commandlibrary.feature.shell.syntheticBytes
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DocumentChunkStreamTest {
    private class TrackingStream(private val delegate: InputStream) : InputStream() {
        val consumed = AtomicLong(0)
        val closed = AtomicBoolean(false)
        override fun read(): Int = delegate.read().also { if (it >= 0) consumed.incrementAndGet() }
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len).also { if (it > 0) consumed.addAndGet(it.toLong()) }
        override fun close() {
            closed.set(true)
            delegate.close()
        }
    }

    private fun assembled(chunks: List<ByteChunk>): ByteArray =
        ByteArrayOutputStream().also { out -> chunks.forEach { out.write(it.bytes.copyBytes()) } }.toByteArray()

    @Test
    fun splitsIntoBoundedContiguousChunksWithFinalOnlyOnLast() = runTest {
        val bytes = syntheticBytes(70_000)
        val input = TrackingStream(ByteArrayInputStream(bytes))
        val reported = mutableListOf<Long>()
        val stream = DocumentChunkStream(input, 8L * 1024 * 1024, StandardTestDispatcher(testScheduler)) { reported += it }

        val chunks = stream.chunks.toList()

        assertEquals(listOf(32_768, 32_768, 4_464), chunks.map { it.bytes.size })
        assertEquals(listOf(0L, 1L, 2L), chunks.map { it.sequence })
        assertEquals(listOf(0L, 32_768L, 65_536L), chunks.map { it.offset })
        assertEquals(listOf(false, false, true), chunks.map { it.final })
        assertTrue(chunks.all { it.metadata == null && it.bytes.size <= DOCUMENT_CHUNK_BYTES })
        assertArrayEquals(bytes, assembled(chunks))
        assertEquals(70_000L, reported.last())
        assertEquals(reported.sorted(), reported)
        assertTrue("input closed after a complete read", input.closed.get())
        assertNull(stream.failure)
    }

    @Test
    fun exactMultipleMarksLastFullChunkFinal() = runTest {
        val bytes = syntheticBytes(2 * DOCUMENT_CHUNK_BYTES)
        val chunks = DocumentChunkStream(ByteArrayInputStream(bytes), 8L * 1024 * 1024, StandardTestDispatcher(testScheduler)) {}.chunks.toList()
        assertEquals(listOf(DOCUMENT_CHUNK_BYTES, DOCUMENT_CHUNK_BYTES), chunks.map { it.bytes.size })
        assertEquals(listOf(false, true), chunks.map { it.final })
        assertArrayEquals(bytes, assembled(chunks))
    }

    @Test
    fun emptyDocumentYieldsOneEmptyFinalChunk() = runTest {
        val chunks = DocumentChunkStream(ByteArrayInputStream(ByteArray(0)), 8L * 1024 * 1024, StandardTestDispatcher(testScheduler)) {}.chunks.toList()
        assertEquals(1, chunks.size)
        assertEquals(0, chunks.single().bytes.size)
        assertTrue(chunks.single().final)
        assertEquals(0L, chunks.single().offset)
    }

    @Test
    fun stopsReadingOnceTheByteLimitIsExceeded() = runTest {
        val limit = 40_000L
        val input = TrackingStream(ByteArrayInputStream(syntheticBytes(4 * 1024 * 1024)))
        val stream = DocumentChunkStream(input, limit, StandardTestDispatcher(testScheduler)) {}
        val received = mutableListOf<ByteChunk>()
        try {
            stream.chunks.collect { received += it }
            fail("expected the limit to stop the stream")
        } catch (expected: DocumentTooLargeException) {
            assertEquals(limit, expected.limitBytes)
        }
        assertEquals(DocumentChunkStream.Failure.TOO_LARGE, stream.failure)
        assertTrue("reads stop within one chunk of the limit", input.consumed.get() <= limit + DOCUMENT_CHUNK_BYTES)
        assertTrue("no chunk past the limit reaches the importer", received.sumOf { it.bytes.size.toLong() } <= limit)
        assertTrue(input.closed.get())
    }

    @Test
    fun documentExactlyAtTheLimitIsAccepted() = runTest {
        val limit = 50_000L
        val chunks = DocumentChunkStream(ByteArrayInputStream(syntheticBytes(limit.toInt())), limit, StandardTestDispatcher(testScheduler)) {}.chunks.toList()
        assertEquals(limit, chunks.sumOf { it.bytes.size.toLong() })
        assertTrue(chunks.last().final)
    }

    @Test
    fun readErrorIsReportedAndPropagated() = runTest {
        val failing = object : InputStream() {
            var served = 0
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (served >= 10_000) throw IOException("synthetic read failure")
                val count = minOf(len, 10_000 - served)
                served += count
                return count
            }
        }
        val stream = DocumentChunkStream(failing, 8L * 1024 * 1024, StandardTestDispatcher(testScheduler)) {}
        try {
            stream.chunks.collect()
            fail("expected the read failure")
        } catch (expected: IOException) {
            assertEquals("synthetic read failure", expected.message)
        }
        assertEquals(DocumentChunkStream.Failure.READ_ERROR, stream.failure)
    }

    @Test
    fun streamCanBeCollectedOnlyOnce() = runTest {
        val stream = DocumentChunkStream(ByteArrayInputStream(syntheticBytes(10)), 100, StandardTestDispatcher(testScheduler)) {}
        stream.chunks.toList()
        try {
            stream.chunks.toList()
            fail("second collection must fail")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("once"))
        }
    }

    /** A provider stream that serves one chunk, then blocks inside read() until it is closed. */
    private class BlockingStream : InputStream() {
        val closed = CountDownLatch(1)
        val blocked = CountDownLatch(1)
        private var served = 0
        override fun read(): Int = throw UnsupportedOperationException()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (served < DOCUMENT_CHUNK_BYTES) {
                val count = minOf(len, DOCUMENT_CHUNK_BYTES - served)
                served += count
                return count
            }
            blocked.countDown()
            closed.await()
            throw IOException("stream closed while reading")
        }
        override fun close() = closed.countDown()
    }

    @Test
    fun cancellationClosesTheInputAndReleasesABlockedRead() = runBlocking {
        val input = BlockingStream()
        val stream = DocumentChunkStream(input, 8L * 1024 * 1024, Dispatchers.IO) {}
        val collector = launch(Dispatchers.Default) { stream.chunks.collect() }
        assertTrue("reader reached the blocking read", input.blocked.await(5, TimeUnit.SECONDS))
        val started = System.nanoTime()
        collector.cancel()
        withTimeout(2_000) { collector.join() }
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertTrue("input closed by cancellation", input.closed.count == 0L)
        assertTrue("cancellation completed within one second, took $elapsedMillis ms", elapsedMillis < 1_000)
        assertNull("a cancelled read is not reported as a read failure", stream.failure)
        assertTrue(stream.isClosed)
        assertFalse(collector.isActive)
    }
}
