package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.ByteChunk
import com.architectpsg.commandlibrary.core.model.CatalogImportLimits
import com.architectpsg.commandlibrary.core.model.CancelToken
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Internal full-feed safety ceilings; no change to the tiny public import capability. */
internal data class FullFeedFramingLimits(
    val maxInputBytes: Long = 2L * 1024 * 1024 * 1024,
    val maxRecords: Int = 500_000,
    val maxRecordBytes: Int = 8 * 1024 * 1024,
    val maxNesting: Int = 32,
) {
    init {
        require(maxInputBytes in 1..2L * 1024 * 1024 * 1024)
        require(maxRecords in 1..500_000)
        require(maxRecordBytes in 1..8 * 1024 * 1024)
        require(maxNesting in 1..32)
    }
    companion object { const val MAX_CHUNK_BYTES = 64 * 1024 }
}

/**
 * Bytes including brackets, commas and whitespace count toward input; only object bytes
 * count toward each record. Only the current bounded record is retained. No decompression:
 * callers must separately validate/hash raw artifacts and supply verified decoded bytes.
 */
internal class JsonArrayFramer private constructor(
    private val limits: FullFeedFramingLimits,
    private val cancel: CancelToken?,
    private val maxChunkBytes: Int,
) {
    constructor(limits: CatalogImportLimits) : this(FullFeedFramingLimits(limits.maxInputBytes,
        limits.maxPrintingRecords, limits.maxRecordBytes, limits.maxNesting), null, Int.MAX_VALUE)
    constructor(limits: FullFeedFramingLimits, cancel: CancelToken) : this(limits, cancel, FullFeedFramingLimits.MAX_CHUNK_BYTES)
    private enum class State { START, FIRST, NEXT, RECORD, AFTER_RECORD, DONE }
    private var state = State.START
    private var sequence = 0L
    private var offset = 0L
    private var count = 0
    private var depth = 0
    private var quoted = false
    private var escaped = false
    private var finalSeen = false
    private val record = ByteArrayOutputStream()
    private val digest = MessageDigest.getInstance("SHA-256")
    private var finalHash: String? = null
    val observedBytes: Long get() = offset
    val recordCount: Int get() = count
    val sha256: String get() = requireNotNull(finalHash) { "Hash requires final byte chunk" }

    suspend fun accept(chunk: ByteChunk, onRecord: suspend (String) -> Unit) {
        checkCancellation()
        require(!finalSeen && chunk.sequence == sequence && chunk.offset == offset) { "Noncontiguous or repeated byte chunk" }
        require(sequence < Long.MAX_VALUE) { "Chunk sequence exceeds limit" }
        require(chunk.bytes.size <= maxChunkBytes) { "Chunk exceeds byte limit" }
        require(chunk.bytes.size.toLong() <= limits.maxInputBytes - offset) { "Input exceeds byte limit" }
        val bytes = chunk.bytes.copyBytes()
        for (byte in bytes) {
            checkCancellation()
            digest.update(byte)
            offset++
            val character = byte.toInt() and 0xff
            when (state) {
                State.START -> if (!space(character)) {
                    require(character == '['.code) { "Expected JSON array" }
                    state = State.FIRST
                }
                State.FIRST -> if (!space(character)) {
                    if (character == ']'.code) state = State.DONE else begin(character)
                }
                State.NEXT -> if (!space(character)) begin(character)
                State.RECORD -> {
                    require(record.size() < limits.maxRecordBytes) { "Record exceeds byte limit" }
                    record.write(character)
                    if (quoted) {
                        if (escaped) escaped = false
                        else if (character == '\\'.code) escaped = true
                        else if (character == '"'.code) quoted = false
                    } else {
                        when (character) {
                            '"'.code -> quoted = true
                            '{'.code, '['.code -> { depth++; require(depth <= limits.maxNesting) { "JSON nesting exceeds limit" } }
                            '}'.code, ']'.code -> {
                                depth--
                                require(depth >= 0) { "Unbalanced JSON record" }
                                if (depth == 0) {
                                    val decoder = StandardCharsets.UTF_8.newDecoder()
                                        .onMalformedInput(CodingErrorAction.REPORT)
                                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                                    val raw = decoder.decode(ByteBuffer.wrap(record.toByteArray())).toString()
                                    // Strict duplicate-key/type/framing syntax validation before delivery.
                                    try { ScryfallCodec.parse(raw, limits.maxNesting) }
                                    catch (_: Exception) { throw IllegalArgumentException("Invalid JSON record") }
                                    checkCancellation()
                                    onRecord(raw)
                                    checkCancellation()
                                    record.reset()
                                    count++
                                    state = State.AFTER_RECORD
                                }
                            }
                        }
                    }
                }
                State.AFTER_RECORD -> if (!space(character)) {
                    when (character) {
                        ','.code -> state = State.NEXT
                        ']'.code -> state = State.DONE
                        else -> error("Expected comma or array end")
                    }
                }
                State.DONE -> require(space(character)) { "Trailing bytes after JSON array" }
            }
        }
        sequence++
        if (chunk.final) {
            finalHash = digest.digest().joinToString("") { "%02x".format(it) }
            finalSeen = true
        }
    }

    fun finish() {
        cancel?.throwIfCancelled()
        require(finalSeen && state == State.DONE) { "Truncated JSON array" }
    }

    private fun begin(character: Int) {
        require(character == '{'.code) { "Expected record object" }
        require(count < limits.maxRecords) { "Printing count exceeds limit" }
        record.reset()
        record.write(character)
        depth = 1
        quoted = false
        escaped = false
        state = State.RECORD
    }

    private suspend fun checkCancellation() {
        currentCoroutineContext().ensureActive()
        cancel?.throwIfCancelled()
    }

    private fun space(character: Int): Boolean = character == 32 || character == 9 || character == 10 || character == 13
}
