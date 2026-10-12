package com.architectpsg.commandlibrary.feature.rules.parser

import com.architectpsg.commandlibrary.core.model.ByteChunk
import com.architectpsg.commandlibrary.core.model.CancelToken
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
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
    val decoder: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val digest = MessageDigest.getInstance("SHA-256")
    // One reusable output window, so decoding memory does not grow with the chunk size.
    val window = CharBuffer.allocate(DECODE_WINDOW_CHARS)
    var expectedSequence = 0L
    var expectedOffset = 0L
    var finalSeen = false
    // Bytes the decoder has not consumed yet: at most one incomplete UTF-8 sequence (3 bytes).
    var carry = ByteArray(0)
    // Absolute offset of carry[0] in the input.
    var carryOffset = 0L

    suspend fun decode(input: ByteBuffer, inputOffset: Long, endOfInput: Boolean) {
        while (true) {
            cancel.checkpoint()
            val result = decoder.decode(input, window, endOfInput)
            if (result.isError) throw RulesParseException(RulesParseFailure.MalformedUtf8(inputOffset + input.position()))
            window.flip()
            reader.accept(window)
            window.clear()
            // Underflow: all complete sequences are consumed; an incomplete one stays in input.
            if (!result.isOverflow) return
        }
    }

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
        val input = if (carry.isEmpty()) {
            ByteBuffer.wrap(bytes)
        } else {
            ByteBuffer.allocate(carry.size + bytes.size).also { buffer ->
                buffer.put(carry).put(bytes)
                buffer.flip()
            }
        }
        val inputOffset = carryOffset
        decode(input, inputOffset, endOfInput = chunk.final)
        carry = ByteArray(input.remaining()).also { input.get(it) }
        carryOffset = inputOffset + input.position() - carry.size
        expectedSequence += 1
        expectedOffset = total
        finalSeen = chunk.final
    }
    if (!finalSeen) throw RulesParseException(RulesParseFailure.Truncated)
    // With endOfInput = true every byte was consumed or reported; flush ends any shift state.
    val tail = CharBuffer.allocate(1)
    if (decoder.flush(tail).isError) throw RulesParseException(RulesParseFailure.MalformedUtf8(carryOffset))
    tail.flip()
    reader.accept(tail)
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

    suspend fun accept(chars: CharBuffer) {
        while (chars.hasRemaining()) {
            val c = chars.get()
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
