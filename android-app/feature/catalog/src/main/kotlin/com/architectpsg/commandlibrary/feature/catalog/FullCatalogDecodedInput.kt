package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Inflater
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

/** Backpressured raw integrity + incremental RFC1952 decoding. No artifact or decoded file retained. */
internal class FullCatalogDecodedInput(private val limits: CatalogBulkImportLimits, private val cancel: CancelToken) {
    data class Proof(val rawBytes: Long, val rawHash: ContentHash, val decodedBytes: Long, val decodedHash: ContentHash)
    suspend fun consume(compression: ArtifactCompression, chunks: Flow<ByteChunk>, output: suspend (ByteChunk) -> Unit): Proof {
        val raw = MessageDigest.getInstance("SHA-256")
        val decoded = MessageDigest.getInstance("SHA-256")
        var sequence = 0L; var bytes = 0L; var final = false
        var outSequence = 0L; var outBytes = 0L
        suspend fun emit(value: ByteArray) {
            currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
            require(value.size.toLong() <= limits.maxDecodedBytes - outBytes)
            decoded.update(value)
            output(ByteChunk(outSequence++, outBytes, SnapshotBytes(value), null, false))
            outBytes += value.size
        }
        val gzip = if (compression == ArtifactCompression.GZIP) GzipDecoder(cancel, minOf(limits.maxChunkBytes, 64 * 1024), ::emit) else null
        try {
            chunks.collect { chunk ->
                currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
                require(!final && chunk.sequence == sequence && chunk.offset == bytes && sequence < Long.MAX_VALUE)
                require(chunk.bytes.size <= limits.maxChunkBytes && chunk.bytes.size.toLong() <= limits.maxRawBytes - bytes)
                val value = chunk.bytes.copyBytes(); raw.update(value); bytes += value.size; sequence++
                if (gzip == null) emit(value) else gzip.accept(value)
                final = chunk.final
            }
            require(final)
            gzip?.finish()
            cancel.throwIfCancelled(); currentCoroutineContext().ensureActive()
            output(ByteChunk(outSequence, outBytes, SnapshotBytes(byteArrayOf()), null, true))
            return Proof(bytes, ContentHash("SHA-256", raw.digest().hex()), outBytes, ContentHash("SHA-256", decoded.digest().hex()))
        } finally { gzip?.close() }
    }
}

/** Single or concatenated members accepted; any truncated member or trailing junk is rejected. */
private class GzipDecoder(val cancel: CancelToken, size: Int, val output: suspend (ByteArray) -> Unit) {
    private val inflater = Inflater(true)
    private val buffer = ByteArray(size)
    private val crc = CRC32()
    private var count = 0L
    private var members = 0
    private var state = 0 // header, deflate, trailer
    private val pending = HeaderBuffer()
    suspend fun accept(bytes: ByteArray) {
        var index = 0
        while (index < bytes.size) {
            currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
            when (state) {
                0 -> {
                    require(pending.size() < 64 * 1024)
                    pending.write(bytes[index++].toInt())
                    if (headerComplete(pending.data(), pending.size())) {
                        pending.reset(); inflater.reset(); crc.reset(); count = 0; state = 1
                    }
                }
                1 -> {
                    inflater.setInput(bytes, index, bytes.size - index)
                    do {
                        currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
                        val n = inflater.inflate(buffer)
                        if (n > 0) { crc.update(buffer, 0, n); count = (count + n) and 0xffffffffL; output(buffer.copyOf(n)) }
                        else require(inflater.finished() || inflater.needsInput())
                    } while (!inflater.finished() && !inflater.needsInput())
                    index = bytes.size - inflater.remaining
                    if (inflater.finished()) state = 2
                }
                2 -> {
                    pending.write(bytes[index++].toInt())
                    if (pending.size() == 8) {
                        val trailer = pending.toByteArray()
                        require(little(trailer, 0) == crc.value && little(trailer, 4) == count)
                        pending.reset(); state = 0; members++
                    }
                }
            }
        }
    }
    private fun headerComplete(b: ByteArray, length: Int): Boolean {
        if (length >= 1) require(b[0].toInt() and 255 == 31)
        if (length >= 2) require(b[1].toInt() and 255 == 139)
        if (length >= 3) require(b[2].toInt() and 255 == 8)
        if (length < 10) return false
        val flags = b[3].toInt() and 255; require(flags and 224 == 0)
        var i = 10
        if (flags and 4 != 0) {
            if (length < i + 2) return false
            val n = (b[i].toInt() and 255) or ((b[i+1].toInt() and 255) shl 8); i += 2
            if (length < i + n) return false
            i += n
        }
        for (flag in intArrayOf(8,16)) if (flags and flag != 0) {
            while (i < length && b[i] != 0.toByte()) i++
            if (i == length) return false
            i++
        }
        if (flags and 2 != 0) {
            if (length < i + 2) return false
            val check = CRC32().apply { update(b, 0, i) }.value and 65535
            require(check == ((b[i].toLong() and 255) or ((b[i+1].toLong() and 255) shl 8)))
            i += 2
        }
        require(length <= i); return length == i
    }
    fun finish() { require(members > 0 && state == 0 && pending.size() == 0) }
    fun close() { inflater.end() }
    private fun little(b: ByteArray, i: Int): Long = (0..3).fold(0L) { v, n -> v or ((b[i+n].toLong() and 255) shl (8*n)) }
}
internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

private class HeaderBuffer : ByteArrayOutputStream() { fun data(): ByteArray = buf }
