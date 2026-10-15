@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.document

import com.architectpsg.commandlibrary.core.model.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.CharacterCodingException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Local byte/resource boundary only; no codec, storage, provider completeness or native claim. */
class PlainDeckInputSourceTest {
    private class Tracked(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closes = 0
        override fun close() { closes++; super.close() }
    }
    private class Source(val read: () -> InputStream) : DocumentSource {
        var opens = 0
        var descriptions = 0
        override fun describe(ref: DocumentRef): DocumentDescription {
            descriptions++
            return DocumentDescription("Untrusted provider label", Long.MAX_VALUE)
        }
        override fun open(ref: DocumentRef): InputStream { opens++; return read() }
    }
    private val ref = DocumentRef("content://synthetic-plain-deck/list")
    private val id = EntityId("explicit-selected-input")
    private suspend fun failure(block: suspend () -> Unit): Throwable {
        var caught: Throwable? = null
        try { block() } catch (problem: Throwable) { caught = problem }
        return checkNotNull(caught) { "Expected input rejection" }
    }

    @Test fun selectedDocumentIsLazyIgnoresProviderMetadataAndClosesOnceAfterExactFrames() = runTest {
        val expected = ByteArray(DOCUMENT_CHUNK_BYTES + 7) { (it % 127).toByte() }
        val stream = Tracked(expected); val source = Source { stream }
        val inputs = PlainDeckInputSource(source, StandardTestDispatcher(testScheduler))
        val input = inputs.document(ref, id, "Selected deck list")
        assertEquals(0, source.opens); assertEquals(0, source.descriptions)
        assertTrue(input.expectedBytes is Knowledge.Unknown); assertTrue(input.mediaType is Knowledge.Unknown)
        val chunks = input.chunks.toList()
        assertEquals(listOf(0L, 1L), chunks.map { it.sequence })
        assertEquals(listOf(0L, DOCUMENT_CHUNK_BYTES.toLong()), chunks.map { it.offset })
        assertEquals(listOf(false, true), chunks.map { it.final })
        assertArrayEquals(expected, chunks.flatMap { it.bytes.copyBytes().asIterable() }.toByteArray())
        assertEquals(1, source.opens); assertEquals(0, source.descriptions); assertEquals(1, stream.closes)
        assertTrue(failure { input.chunks.toList() } is IllegalStateException)
        assertEquals(1, source.opens)
    }

    @Test fun abandoningCollectionOrCancellingDuringOpenCannotLeakProviderStream() = runTest {
        val first = Tracked(ByteArray(DOCUMENT_CHUNK_BYTES * 3))
        val source = Source { first }; val inputs = PlainDeckInputSource(source, StandardTestDispatcher(testScheduler))
        assertEquals(1, inputs.document(ref, id, "Selected deck list").chunks.take(1).toList().size)
        assertEquals(1, first.closes)
        val late = Tracked(byteArrayOf(1, 2, 3))
        lateinit var reader: Job
        val cancelling = Source { reader.cancel(); late }
        val input = PlainDeckInputSource(cancelling, StandardTestDispatcher(testScheduler)).document(ref, id, "Selected deck list")
        var emitted = 0
        reader = backgroundScope.launch { input.chunks.collect { emitted++ } }
        runCurrent()
        assertTrue(reader.isCancelled); assertEquals(0, emitted); assertEquals(1, late.closes)
    }

    @Test fun growingDocumentRejectsActualByteOverflowAndClosesWithoutTrustingReportedSize() = runTest {
        val stream = Tracked(ByteArray(PlainDeckListLimits.MAX_BYTES + 1))
        val source = Source { stream }
        val input = PlainDeckInputSource(source, StandardTestDispatcher(testScheduler)).document(ref, id, "Selected deck list")
        var emittedBytes = 0L
        val problem = failure { input.chunks.collect { emittedBytes += it.bytes.size } }
        assertTrue(problem is DocumentTooLargeException)
        assertTrue(emittedBytes <= PlainDeckListLimits.MAX_BYTES)
        assertEquals(1, stream.closes); assertEquals(0, source.descriptions)
    }

    @Test fun pasteFramesPreserveUtf8AcrossBoundaryAndEmptyInputHasOneFinalFrame() = runTest {
        val inputs = PlainDeckInputSource(Source { error("Paste must not open a document") }, StandardTestDispatcher(testScheduler))
        val text = "a".repeat(DOCUMENT_CHUNK_BYTES - 1) + "🧪é"
        val chunks = inputs.paste(text, id, "Pasted deck list").chunks.toList()
        assertEquals(listOf(0L, 1L), chunks.map { it.sequence })
        assertEquals(listOf(false, true), chunks.map { it.final })
        assertArrayEquals(text.toByteArray(Charsets.UTF_8), chunks.flatMap { it.bytes.copyBytes().asIterable() }.toByteArray())
        assertEquals(DOCUMENT_CHUNK_BYTES.toLong(), chunks.last().offset)
        val empty = inputs.paste("", id, "Pasted deck list").chunks.toList().single()
        assertEquals(ByteChunk(0, 0, SnapshotBytes(byteArrayOf()), null, true), empty)
    }

    @Test fun malformedOrEncodedOversizePasteEmitsNothingInsteadOfReplacementOrTruncation() = runTest {
        val inputs = PlainDeckInputSource(Source { error("Paste must not open a document") }, StandardTestDispatcher(testScheduler))
        for ((text, expected) in listOf(
            ("x" + 0xD800.toChar()) to CharacterCodingException::class.java,
            "é".repeat(PlainDeckListLimits.MAX_BYTES / 2 + 1) to DocumentTooLargeException::class.java,
        )) {
            var emitted = 0
            val problem = failure { inputs.paste(text, id, "Pasted deck list").chunks.collect { emitted++ } }
            assertTrue(expected.isInstance(problem)); assertEquals(0, emitted)
        }
    }
}
