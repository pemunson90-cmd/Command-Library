package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

class JsonArrayFramerTest {
    private val limits = CatalogImportLimits()
    private fun chunk(bytes: ByteArray, offset: Long = 0, sequence: Long = 0, final: Boolean = true) =
        ByteChunk(sequence, offset, SnapshotBytes(bytes), null, final)
    private suspend fun frame(input: ByteArray, limits: CatalogImportLimits = this.limits, width: Int = 7): List<String> {
        val frame = JsonArrayFramer(limits)
        val rows = mutableListOf<String>()
        input.asList().chunked(width).forEachIndexed { index, part ->
            frame.accept(chunk(part.toByteArray(), (index * width).toLong(), index.toLong(), index == (input.size - 1) / width)) { rows.add(it) }
        }
        frame.finish()
        assertEquals(input.size.toLong(), frame.observedBytes)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { "%02x".format(it) }, frame.sha256)
        return rows
    }
    @Test fun genuineThreePrintingsFrameAcrossByteBoundaries(): Unit = runBlocking {
        val bytes = javaClass.getResourceAsStream("/english-three-printings.json")!!.readBytes()
        val rows = frame(bytes)
        assertEquals(3, rows.size)
        assertTrue(rows[0].startsWith("{"))
    }
    @Test fun rejectsTrailingCommaAndTruncation(): Unit = runBlocking {
        for (text in listOf("[{},]", "[{}", "[{\"a\":1}]junk")) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { frame(text.toByteArray()) } }
        }
    }
    @Test fun rejectsInvalidUtf8AndLimits(): Unit = runBlocking {
        assertThrows(Exception::class.java) { runBlocking { frame(byteArrayOf(91, 123, 34, 120, 34, 58, 34, -1, 34, 125, 93)) } }
        assertEquals(1, frame("[{}]".toByteArray(), CatalogImportLimits(maxInputBytes = 4)).size)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { frame("[{}]\n".toByteArray(), CatalogImportLimits(maxInputBytes = 4)) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { frame("[{\"a\":1}]".toByteArray(), CatalogImportLimits(maxRecordBytes = 5)) } }
    }
    @Test fun validatesSequenceAndFinalFlag(): Unit = runBlocking {
        val reader = JsonArrayFramer(limits)
        reader.accept(chunk("[".toByteArray(), final = false)) { }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { reader.accept(chunk("{}]".toByteArray(), 1, 0)) { } } }
    }

    private class Token : CancelToken {
        var cancelled = false
        override val isCancelled get() = cancelled
        override fun throwIfCancelled() { if (cancelled) throw kotlinx.coroutines.CancellationException() }
    }
    private fun raw(size: Int) = "{\"x\":\"" + "x".repeat(size - 8) + "\"}"
    private suspend fun feed(f: JsonArrayFramer, input: ByteArray, onRecord: suspend (String) -> Unit = {}) {
        var offset = 0
        var sequence = 0L
        while (offset < input.size) {
            val end = minOf(input.size, offset + 64 * 1024)
            f.accept(chunk(input.copyOfRange(offset, end), offset.toLong(), sequence++, end == input.size), onRecord)
            offset = end
        }
        f.finish()
    }
    @Test fun fullProfileAcceptsEightMiBRecordAndLargerThanTinyInput(): Unit = runBlocking {
        val row = raw(8 * 1024 * 1024)
        val input = "[$row,{}]".toByteArray()
        val f = JsonArrayFramer(FullFeedFramingLimits(), Token())
        var seen = 0
        feed(f, input) { if (seen++ == 0) assertEquals(row, it) }
        assertEquals(2, seen)
        assertEquals(input.size.toLong(), f.observedBytes)
        val hash = MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { "%02x".format(it) }
        assertEquals(hash, f.sha256); assertEquals(hash, f.sha256)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { feed(JsonArrayFramer(FullFeedFramingLimits(), Token()), ("[" + raw(8 * 1024 * 1024 + 1) + "]").toByteArray()) }
        }
    }
    @Test fun tinyRecordAndCountBoundariesRemainUnchanged(): Unit = runBlocking {
        assertEquals(128 * 1024, CatalogImportLimits().maxRecordBytes)
        assertEquals(8L * 1024 * 1024, CatalogImportLimits().maxInputBytes)
        assertEquals(10_000, CatalogImportLimits().maxPrintingRecords)
        feed(JsonArrayFramer(limits), ("[" + raw(128 * 1024) + "]").toByteArray())
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            feed(JsonArrayFramer(limits), ("[" + raw(128 * 1024 + 1) + "]").toByteArray())
        } }
        val input = ("[" + List(10_001) { "{}" }.joinToString(",") + "]").toByteArray()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { feed(JsonArrayFramer(limits), input) } }
        val full = JsonArrayFramer(FullFeedFramingLimits(), Token())
        feed(full, input)
        assertEquals(10_001, full.recordCount)
        for (bad in listOf(FullFeedFramingLimits().maxInputBytes + 1)) {
            assertThrows(IllegalArgumentException::class.java) { CatalogImportLimits(maxInputBytes = bad) }
        }
        assertThrows(IllegalArgumentException::class.java) { CatalogImportLimits(maxPrintingRecords = 10_001) }
        assertThrows(IllegalArgumentException::class.java) { CatalogImportLimits(maxRecordBytes = 128 * 1024 + 1) }
    }
    @Test fun fullProfileCapsAndDelimiterAccounting(): Unit = runBlocking {
        assertEquals(2L * 1024 * 1024 * 1024, FullFeedFramingLimits().maxInputBytes)
        assertEquals(500_000, FullFeedFramingLimits().maxRecords)
        for (bad in listOf(0L, 2L * 1024 * 1024 * 1024 + 1)) {
            assertThrows(IllegalArgumentException::class.java) { FullFeedFramingLimits(maxInputBytes = bad) }
        }
        assertThrows(IllegalArgumentException::class.java) { FullFeedFramingLimits(maxRecords = 500_001) }
        assertThrows(IllegalArgumentException::class.java) { FullFeedFramingLimits(maxRecordBytes = 8 * 1024 * 1024 + 1) }
        assertThrows(IllegalArgumentException::class.java) { FullFeedFramingLimits(maxNesting = 33) }
        feed(JsonArrayFramer(FullFeedFramingLimits(4, 1, 2, 1), Token()), "[{}]".toByteArray())
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            feed(JsonArrayFramer(FullFeedFramingLimits(4, 1, 2, 1), Token()), "[{}] ".toByteArray())
        } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            JsonArrayFramer(FullFeedFramingLimits(), Token()).accept(chunk(ByteArray(65537))) {}
        } }
    }
    @Test fun strictSyntaxUtf8NestingAndFinalChecks(): Unit = runBlocking {
        assertEquals("{\"x\":\"É😀\"}", frame("[{\"x\":\"É😀\"}]".toByteArray(), width = 1).single())
        for (input in listOf("[{\"x\":1,\"x\":2}]", "[{\"x\":}]", "[{]}", "[{\"x\":\"\\uD800\"}]")) {
            assertThrows(Exception::class.java) { runBlocking { frame(input.toByteArray()) } }
        }
        feed(JsonArrayFramer(FullFeedFramingLimits(maxNesting = 2), Token()), "[{\"x\":{}}]".toByteArray())
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            feed(JsonArrayFramer(FullFeedFramingLimits(maxNesting = 1), Token()), "[{\"x\":{}}]".toByteArray())
        } }
        val f = JsonArrayFramer(limits)
        assertThrows(IllegalArgumentException::class.java) { f.sha256 }
        f.accept(chunk("[]".toByteArray())) {}
        f.finish()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { f.accept(chunk(byteArrayOf(), 2, 1)) {} } }
    }
    @Test fun fullPathChecksTokenAndCoroutineCancellation(): Unit = runBlocking {
        val token = Token()
        val f = JsonArrayFramer(FullFeedFramingLimits(), token)
        var callbacks = 0
        assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking {
            f.accept(chunk("[{},{}]".toByteArray())) { callbacks++; token.cancelled = true }
        } }
        assertEquals(1, callbacks); assertEquals(0, f.recordCount)
        assertThrows(kotlinx.coroutines.CancellationException::class.java) { f.finish() }
        val parent = kotlinx.coroutines.Job()
        var caught = false
        val job = kotlinx.coroutines.CoroutineScope(parent).launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            parent.cancel()
            try { JsonArrayFramer(FullFeedFramingLimits(), Token()).accept(chunk("[{}]".toByteArray())) { callbacks++ } }
            catch (_: kotlinx.coroutines.CancellationException) { caught = true }
        }
        job.join()
        assertTrue(caught); assertEquals(1, callbacks)
    }
}
