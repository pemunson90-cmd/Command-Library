package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class JsonArrayFramerTest {
    private val limits = CatalogImportLimits()
    private fun chunk(bytes: ByteArray, offset: Long = 0, sequence: Long = 0, final: Boolean = true) =
        ByteChunk(sequence, offset, SnapshotBytes(bytes), null, final)
    private suspend fun frame(input: ByteArray, limits: CatalogImportLimits = this.limits): List<String> {
        val frame = JsonArrayFramer(limits)
        val rows = mutableListOf<String>()
        input.asList().chunked(7).forEachIndexed { index, part ->
            frame.accept(chunk(part.toByteArray(), (index * 7).toLong(), index.toLong(), index == (input.size - 1) / 7)) { rows.add(it) }
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
        assertThrows(IllegalArgumentException::class.java) { runBlocking { frame("[{}]".toByteArray(), CatalogImportLimits(maxInputBytes = 4)) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { frame("[{\"a\":1}]".toByteArray(), CatalogImportLimits(maxRecordBytes = 5)) } }
    }
    @Test fun validatesSequenceAndFinalFlag(): Unit = runBlocking {
        val reader = JsonArrayFramer(limits)
        reader.accept(chunk("[".toByteArray(), final = false)) { }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { reader.accept(chunk("{}]".toByteArray(), 1, 0)) { } } }
    }
}
