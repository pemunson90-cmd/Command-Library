package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.ByteChunk
import com.architectpsg.commandlibrary.core.model.CatalogImportLimits
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Byte framing deliberately precedes JSON parsing so neither the file nor a record is buffered without a bound. */
internal class JsonArrayFramer(private val limits: CatalogImportLimits) {
    private enum class State { START, FIRST, RECORD, AFTER_RECORD, DONE }
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
    val observedBytes: Long get() = offset
    val recordCount: Int get() = count
    val sha256: String get() = digest.digest().joinToString("") { "%02x".format(it) }

    suspend fun accept(chunk: ByteChunk, onRecord: suspend (String) -> Unit) {
        require(!finalSeen && chunk.sequence == sequence && chunk.offset == offset) { "Noncontiguous or repeated byte chunk" }
        sequence++
        val bytes = chunk.bytes.copyBytes()
        require(offset + bytes.size <= limits.maxInputBytes) { "Input exceeds byte limit" }
        digest.update(bytes)
        offset += bytes.size
        for (byte in bytes) {
            val character = byte.toInt() and 0xff
            when (state) {
                State.START -> if (!space(character)) {
                    require(character == '['.code) { "Expected JSON array" }
                    state = State.FIRST
                }
                State.FIRST -> if (!space(character)) {
                    if (character == ']'.code) state = State.DONE else begin(character)
                }
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
                                    onRecord(decoder.decode(ByteBuffer.wrap(record.toByteArray())).toString())
                                    count++
                                    state = State.AFTER_RECORD
                                }
                            }
                        }
                    }
                }
                State.AFTER_RECORD -> if (!space(character)) {
                    when (character) {
                        ','.code -> state = State.FIRST
                        ']'.code -> state = State.DONE
                        else -> error("Expected comma or array end")
                    }
                }
                State.DONE -> require(space(character)) { "Trailing bytes after JSON array" }
            }
        }
        if (chunk.final) finalSeen = true
    }

    fun finish() {
        require(finalSeen && state == State.DONE) { "Truncated JSON array" }
    }

    private fun begin(character: Int) {
        require(character == '{'.code) { "Expected record object" }
        require(count < limits.maxPrintingRecords) { "Printing count exceeds limit" }
        record.reset()
        record.write(character)
        depth = 1
        quoted = false
        escaped = false
        state = State.RECORD
    }

    private fun space(character: Int): Boolean = character == 32 || character == 9 || character == 10 || character == 13
}
