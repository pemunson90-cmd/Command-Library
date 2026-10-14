package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FullCatalogDecodedInputTest {
    @Test fun gzipCrcConcatenatedMembersAndRawDecodedProof() = runBlocking {
        val first="{\"name\":\"é\"}\n".toByteArray(); val second="{\"name\":\"☃\"}".toByteArray()
        val gzip=bulkGzip(first)+bulkGzip(second); val decoded=ByteArrayOutputStream(); var final=false
        val proof=FullCatalogDecodedInput(CatalogBulkImportLimits(),BULK_NO_CANCEL).consume(ArtifactCompression.GZIP,bulkChunks(gzip,1)) { decoded.write(it.bytes.copyBytes()); final=it.final }
        assertArrayEquals(first+second,decoded.toByteArray()); assertTrue(final)
        assertEquals(gzip.size.toLong(),proof.rawBytes); assertEquals((first+second).size.toLong(),proof.decodedBytes); assertNotEquals(proof.rawHash,proof.decodedHash)
    }
    @Test fun truncationCrcTrailingGarbageAndMissingFinalReject() = runBlocking {
        val gzip=bulkGzip("abc".toByteArray())
        for(bytes in listOf(gzip.copyOf(gzip.size-1),gzip+byteArrayOf(0),gzip.copyOf().apply { this[size-8]=(this[size-8].toInt() xor 1).toByte() })) {
            try { FullCatalogDecodedInput(CatalogBulkImportLimits(),BULK_NO_CANCEL).consume(ArtifactCompression.GZIP,bulkChunks(bytes,2)) {}; fail("Invalid gzip accepted") } catch(_: IllegalArgumentException) {}
        }
        val chunks=flow { emit(ByteChunk(0,0,SnapshotBytes(byteArrayOf(1)),null,false)) }
        try { FullCatalogDecodedInput(CatalogBulkImportLimits(),BULK_NO_CANCEL).consume(ArtifactCompression.NONE,chunks) {}; fail() } catch(_: IllegalArgumentException) {}
    }
    @Test fun bombLimitsAndChunkContinuityAreCheckedBeforeDecodedDelivery() = runBlocking {
        var total=0
        try { FullCatalogDecodedInput(CatalogBulkImportLimits(maxDecodedBytes=32,maxChunkBytes=16),BULK_NO_CANCEL).consume(ArtifactCompression.GZIP,bulkChunks(bulkGzip(ByteArray(10000)),16)) { total+=it.bytes.size }; fail() } catch(_: IllegalArgumentException) {}
        assertTrue(total<=32)
        for(chunks in listOf(flow { emit(ByteChunk(1,0,SnapshotBytes(byteArrayOf(1)),null,true)) },flow { emit(ByteChunk(0,1,SnapshotBytes(byteArrayOf(1)),null,true)) },flow { emit(ByteChunk(0,0,SnapshotBytes(byteArrayOf(1)),null,true)); emit(ByteChunk(1,1,SnapshotBytes(byteArrayOf(2)),null,true)) })) {
            try { FullCatalogDecodedInput(CatalogBulkImportLimits(),BULK_NO_CANCEL).consume(ArtifactCompression.NONE,chunks) {}; fail() } catch(_: IllegalArgumentException) {}
        }
    }
    @Test fun strictUtf8IsValidatedByImporterAfterGzip() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            val source=bulkSelected().copy(compression=ArtifactCompression.GZIP)
            val malformed="{\"object\":\"card\",\"name\":\"".toByteArray()+byteArrayOf(0xc3.toByte(),0x28)+"\"}".toByteArray()
            assertTrue(db.importer().`import`(source,bulkChunks(bulkGzip(malformed)),{},BULK_NO_CANCEL) is Outcome.Failure)
            assertNull(db.active())
        }
    }
}
internal fun bulkGzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().let { out -> GZIPOutputStream(out).use { it.write(bytes) }; out.toByteArray() }
