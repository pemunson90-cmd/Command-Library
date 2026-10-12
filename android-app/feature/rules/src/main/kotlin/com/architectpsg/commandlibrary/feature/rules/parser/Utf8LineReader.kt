package com.architectpsg.commandlibrary.feature.rules.parser

import com.architectpsg.commandlibrary.core.model.ByteChunk
import com.architectpsg.commandlibrary.core.model.CancelToken
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow

/** Byte count and SHA-256 of everything read. */
internal class ReadDigest(val byteCount: Long, val sha256Hex: String)

/**
 * Stops on either cancellation signal: the coroutine's job or the caller's token. Both throw
 * `CancellationException`, which the parser never converts into a result.
 */
internal suspend fun CancelToken.checkpoint() {
    currentCoroutineContext().ensureActive()
    throwIfCancelled()
}

/**
 * Reads [chunks] as strict UTF-8 and hands each line to [onLine] without holding more than one line.
 *
 * Chunks must be contiguous from sequence 0 and offset 0 and end with exactly one final chunk. A
 * leading byte order mark is removed. `\r\n`, `\n` and a lone `\r` each end a line; terminators are
 * not part of the line. The line after the last terminator is delivered when it is not empty.
 * Decoding is done here rather than by a platform `CharsetDecoder`, so validity and the reported
 * [RulesParseFailure.MalformedUtf8] offset are the same on the JVM and on Android.
 * Cancellation is checked for every chunk, every decode window, before and after every line
 * callback, and before the digest is returned. Throws [RulesParseException] for any failure;
 * cancellation propagates unchanged.
 */
internal suspend fun readUtf8Lines(
    chunks: Flow<ByteChunk>,
    limits: RulesParserLimits,
    cancel: CancelToken,
    onLine: suspend (line: String, lineNumber: Long) -> Unit,
): ReadDigest {
    val reader = LineAssembler(limits, cancel, onLine)
    val decoder = StrictUtf8Decoder()
    val digest = MessageDigest.getInstance("SHA-256")
    // One reusable output window, so decoding memory does not grow with the chunk size.
    val window = CharArray(DECODE_WINDOW_CHARS)
    var expectedSequence = 0L
    var expectedOffset = 0L
    var finalSeen = false

    chunks.collect { chunk ->
        cancel.checkpoint()
        if (finalSeen) throw RulesParseException(RulesParseFailure.ChunkAfterFinal)
        if (chunk.sequence != expectedSequence || chunk.offset != expectedOffset) {
            throw RulesParseException(
                RulesParseFailure.NoncontiguousChunk(expectedSequence, expectedOffset, chunk.sequence, chunk.offset),
            )
        }
        // Checked before copying, so an oversized chunk is rejected without another allocation.
        val total = expectedOffset + chunk.bytes.size
        if (total > limits.maxInputBytes) {
            throw RulesParseException(RulesParseFailure.LimitExceeded(RulesLimit.INPUT_BYTES, limits.maxInputBytes, total))
        }
        val bytes = chunk.bytes.copyBytes()
        digest.update(bytes)
        var filled = 0
        for (i in bytes.indices) {
            filled = decoder.accept(bytes[i], expectedOffset + i, window, filled)
            // A code point adds at most two chars, so flush while two slots remain.
            if (filled > window.size - 2) {
                cancel.checkpoint()
                reader.accept(window, filled)
                filled = 0
            }
        }
        if (filled > 0) reader.accept(window, filled)
        if (chunk.final) decoder.finish()
        expectedSequence += 1
        expectedOffset = total
        finalSeen = chunk.final
    }
    if (!finalSeen) throw RulesParseException(RulesParseFailure.Truncated)
    reader.finish()
    cancel.checkpoint()
    return ReadDigest(expectedOffset, digest.digest().toHex())
}

private const val DECODE_WINDOW_CHARS = 8 * 1024

private fun ByteArray.toHex(): String {
    val digits = "0123456789abcdef"
    val out = CharArray(size * 2)
    for (i in indices) {
        val value = this[i].toInt() and 0xff
        out[i * 2] = digits[value ushr 4]
        out[i * 2 + 1] = digits[value and 0x0f]
    }
    return String(out)
}

/**
 * Strict UTF-8 (RFC 3629, Unicode Table 3-7 well-formed byte sequences): rejects overlong forms,
 * surrogates, code points above U+10FFFF, stray continuation bytes and truncated sequences. State
 * carries across chunks, so a sequence may be split anywhere. A malformed sequence is reported at
 * the absolute offset of its first byte.
 */
private class StrictUtf8Decoder {
    private var needed = 0
    private var codePoint = 0
    private var lower = 0x80
    private var upper = 0xBF
    private var sequenceStart = 0L

    /** Decodes [byte] at absolute [offset] into [out] starting at [filled]; returns the new fill. */
    fun accept(byte: Byte, offset: Long, out: CharArray, filled: Int): Int {
        val b = byte.toInt() and 0xFF
        if (needed == 0) {
            sequenceStart = offset
            when (b) {
                in 0x00..0x7F -> {
                    out[filled] = b.toChar()
                    return filled + 1
                }
                in 0xC2..0xDF -> begin(1, b and 0x1F, 0x80, 0xBF)
                0xE0 -> begin(2, b and 0x0F, 0xA0, 0xBF)
                in 0xE1..0xEC, 0xEE, 0xEF -> begin(2, b and 0x0F, 0x80, 0xBF)
                0xED -> begin(2, b and 0x0F, 0x80, 0x9F)
                0xF0 -> begin(3, b and 0x07, 0x90, 0xBF)
                in 0xF1..0xF3 -> begin(3, b and 0x07, 0x80, 0xBF)
                0xF4 -> begin(3, b and 0x07, 0x80, 0x8F)
                else -> malformed(offset)
            }
            return filled
        }
        if (b < lower || b > upper) malformed(sequenceStart)
        lower = 0x80
        upper = 0xBF
        codePoint = (codePoint shl 6) or (b and 0x3F)
        needed -= 1
        if (needed > 0) return filled
        if (codePoint < 0x10000) {
            out[filled] = codePoint.toChar()
            return filled + 1
        }
        out[filled] = Character.highSurrogate(codePoint)
        out[filled + 1] = Character.lowSurrogate(codePoint)
        return filled + 2
    }

    /** End of input: a sequence still waiting for bytes is malformed. */
    fun finish() {
        if (needed > 0) malformed(sequenceStart)
    }

    private fun begin(continuationBytes: Int, bits: Int, secondLower: Int, secondUpper: Int) {
        needed = continuationBytes
        codePoint = bits
        lower = secondLower
        upper = secondUpper
    }

    private fun malformed(offset: Long): Nothing = throw RulesParseException(RulesParseFailure.MalformedUtf8(offset))
}

/** Splits decoded characters into lines, enforcing the per-line UTF-8 byte limit. */
private class LineAssembler(
    private val limits: RulesParserLimits,
    private val cancel: CancelToken,
    private val onLine: suspend (String, Long) -> Unit,
) {
    private val line = StringBuilder()
    private var lineBytes = 0
    private var lineNumber = 0L
    private var atStart = true
    private var lastWasCarriageReturn = false

    suspend fun accept(chars: CharArray, length: Int) {
        for (i in 0 until length) {
            val c = chars[i]
            if (atStart) {
                atStart = false
                if (c == '\uFEFF') continue
            }
            when (c) {
                '\n' -> {
                    if (lastWasCarriageReturn) lastWasCarriageReturn = false else emit()
                }
                '\r' -> {
                    emit()
                    lastWasCarriageReturn = true
                }
                else -> {
                    lastWasCarriageReturn = false
                    lineBytes += utf8Length(c)
                    if (lineBytes > limits.maxLineBytes) {
                        throw RulesParseException(
                            RulesParseFailure.LimitExceeded(RulesLimit.LINE_BYTES, limits.maxLineBytes.toLong(), lineBytes.toLong()),
                        )
                    }
                    line.append(c)
                }
            }
        }
    }

    suspend fun finish() {
        if (line.isNotEmpty()) emit()
    }

    private suspend fun emit() {
        lineNumber += 1
        val text = line.toString()
        line.setLength(0)
        lineBytes = 0
        cancel.checkpoint()
        onLine(text, lineNumber)
        // A callback that does not suspend gives the coroutine no other chance to notice a cancel.
        cancel.checkpoint()
    }

    // A surrogate pair is 4 UTF-8 bytes: count them all on the high surrogate.
    private fun utf8Length(c: Char): Int = when {
        c.isHighSurrogate() -> 4
        c.isLowSurrogate() -> 0
        c.code < 0x80 -> 1
        c.code < 0x800 -> 2
        else -> 3
    }
}
