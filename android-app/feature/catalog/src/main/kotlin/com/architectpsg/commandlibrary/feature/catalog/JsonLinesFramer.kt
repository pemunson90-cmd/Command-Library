package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.ByteChunk
import com.architectpsg.commandlibrary.core.model.CancelToken
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Internal parser safeguards, not an estimate of a source feed or an import contract. */
internal data class JsonLinesLimits(
    val maxInputBytes: Long,
    val maxRecordBytes: Int,
    val maxRecords: Long,
    val maxNesting: Int,
) {
    init {
        require(maxInputBytes > 0 && maxRecords > 0)
        require(maxRecordBytes in 1..(128 * 1024)) { "Record byte limit exceeds parser ceiling" }
        require(maxNesting in 1..32) { "Nesting limit exceeds parser ceiling" }
    }
}

/**
 * Frames one JSON object per physical line. LF and CRLF are accepted, as is a final object
 * without a newline. Empty input, blank lines, a UTF-8 BOM, and bare CR are rejected.
 * The byte limit includes delimiters. The record limit excludes a CRLF delimiter.
 * A suspended callback stops byte consumption; only the current bounded record is retained.
 */
internal class JsonLinesFramer private constructor(
    private val maxInputBytes: Long,
    private val maxRecordBytes: Int,
    private val maxRecords: Long,
    private val maxNesting: Int,
    private val cancel: CancelToken,
    private val maxChunkBytes: Int,
) {
    constructor(limits: JsonLinesLimits, cancel: CancelToken) : this(limits.maxInputBytes,
        limits.maxRecordBytes, limits.maxRecords, limits.maxNesting, cancel, Int.MAX_VALUE)
    constructor(limits: FullFeedFramingLimits, cancel: CancelToken) : this(limits.maxInputBytes,
        limits.maxRecordBytes, limits.maxRecords.toLong(), limits.maxNesting, cancel, FullFeedFramingLimits.MAX_CHUNK_BYTES)
    private val line = ByteArray(maxRecordBytes)
    private var length = 0
    private var pendingCr = false
    private var sequence = 0L
    private var offset = 0L
    private var count = 0L
    private var finalSeen = false
    private val digest = MessageDigest.getInstance("SHA-256")
    private var finalHash: String? = null

    val observedBytes: Long get() = offset
    val recordCount: Long get() = count
    val sha256: String get() = requireNotNull(finalHash) { "Hash requires final byte chunk" }

    suspend fun accept(chunk: ByteChunk, onRecord: suspend (String) -> Unit) {
        currentCoroutineContext().ensureActive()
        cancel.throwIfCancelled()
        require(!finalSeen && chunk.sequence == sequence && chunk.offset == offset) {
            "Noncontiguous or repeated byte chunk"
        }
        require(sequence < Long.MAX_VALUE) { "Chunk sequence exceeds limit" }
        require(chunk.bytes.size <= maxChunkBytes) { "Chunk exceeds byte limit" }
        val bytes = chunk.bytes.copyBytes()
        require(bytes.size.toLong() <= maxInputBytes - offset) { "Input exceeds byte limit" }
        for (byte in bytes) {
            currentCoroutineContext().ensureActive()
            cancel.throwIfCancelled()
            digest.update(byte)
            offset++
            val unsigned = byte.toInt() and 0xff
            when {
                pendingCr -> {
                    require(unsigned == 10) { "Bare CR in JSONL input" }
                    pendingCr = false
                    emit(onRecord)
                }
                unsigned == 13 -> pendingCr = true
                unsigned == 10 -> emit(onRecord)
                else -> {
                    require(length < line.size) { "Record exceeds byte limit" }
                    line[length++] = byte
                }
            }
        }
        sequence++
        if (chunk.final) {
            require(!pendingCr) { "Bare CR at end of JSONL input" }
            if (length > 0) emit(onRecord)
            require(count > 0) { "Empty JSONL input" }
            finalHash = digest.digest().joinToString("") { "%02x".format(it) }
            finalSeen = true
        }
    }

    fun finish() {
        cancel.throwIfCancelled()
        require(finalSeen) { "Missing final byte chunk" }
    }

    private suspend fun emit(onRecord: suspend (String) -> Unit) {
        currentCoroutineContext().ensureActive()
        cancel.throwIfCancelled()
        require(length > 0) { "Blank JSONL line" }
        require(count < maxRecords) { "Record count exceeds limit" }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val raw = decoder.decode(ByteBuffer.wrap(line, 0, length)).toString()
        require(!raw.startsWith('\uFEFF')) { "UTF-8 BOM is not accepted" }
        try { ScryfallCodec.parse(raw, maxNesting) }
        catch (_: Exception) { throw IllegalArgumentException("Invalid JSON record") }
        currentCoroutineContext().ensureActive()
        cancel.throwIfCancelled()
        onRecord(raw)
        currentCoroutineContext().ensureActive()
        cancel.throwIfCancelled()
        count++
        length = 0
    }
}
