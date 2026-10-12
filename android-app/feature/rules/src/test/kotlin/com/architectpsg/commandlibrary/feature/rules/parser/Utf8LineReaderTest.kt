package com.architectpsg.commandlibrary.feature.rules.parser

import com.architectpsg.commandlibrary.core.model.ByteChunk
import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.SnapshotBytes
import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules.NeverCancelled
import com.architectpsg.commandlibrary.feature.rules.parser.SyntheticRules.chunks
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** Every input in this class is SYNTHETIC. */
class Utf8LineReaderTest {

    private suspend fun lines(input: Flow<ByteChunk>, limits: RulesParserLimits = RulesParserLimits()): List<String> {
        val out = ArrayList<String>()
        readUtf8Lines(input, limits, NeverCancelled) { line, _ -> out += line }
        return out
    }

    private suspend fun failure(input: Flow<ByteChunk>, limits: RulesParserLimits = RulesParserLimits()): RulesParseFailure {
        try {
            readUtf8Lines(input, limits, NeverCancelled) { _, _ -> }
        } catch (expected: RulesParseException) {
            return expected.failure
        }
        fail("Expected a parse failure")
        throw AssertionError()
    }

    private fun chunk(sequence: Long, offset: Long, text: String, final: Boolean) =
        ByteChunk(sequence, offset, SnapshotBytes(text.toByteArray()), null, final)

    @Test
    fun everyLineEndingStyleGivesTheSameLines() = runTest {
        val expected = listOf("alpha", "", "beta")
        assertEquals(expected, lines(chunks("alpha\n\nbeta\n")))
        assertEquals(expected, lines(chunks("alpha\r\n\r\nbeta\r\n")))
        assertEquals(expected, lines(chunks("alpha\r\rbeta\r")))
        // A CRLF pair split across chunks is still one line ending.
        assertEquals(expected, lines(chunks("alpha\r\n\r\nbeta\r\n", size = 1)))
    }

    @Test
    fun lastLineWithoutTerminatorIsDeliveredAndATrailingTerminatorAddsNoLine() = runTest {
        assertEquals(listOf("one", "two"), lines(chunks("one\ntwo")))
        assertEquals(listOf("one", "two"), lines(chunks("one\ntwo\n")))
    }

    @Test
    fun leadingByteOrderMarkIsRemovedOnlyAtTheStart() = runTest {
        assertEquals(listOf("first", "\uFEFFsecond"), lines(chunks("\uFEFFfirst\n\uFEFFsecond\n")))
    }

    @Test
    fun multiByteCharactersSplitAcrossChunksDecode() = runTest {
        val text = "caf\u00E9 \u2013 \u65E5\u672C \uD83C\uDCCF\nnext\n"
        assertEquals(listOf("caf\u00E9 \u2013 \u65E5\u672C \uD83C\uDCCF", "next"), lines(chunks(text, size = 1)))
    }

    @Test
    fun malformedUtf8ReportsTheByteOffset() = runTest {
        val bytes = "abc".toByteArray() + byteArrayOf(0xFF.toByte()) + "def".toByteArray()
        assertEquals(RulesParseFailure.MalformedUtf8(3), failure(chunks(bytes, size = 2)))
    }

    @Test
    fun sequenceCutOffAtEndOfInputIsMalformed() = runTest {
        val bytes = "ab".toByteArray() + byteArrayOf(0xE2.toByte(), 0x82.toByte())
        assertEquals(RulesParseFailure.MalformedUtf8(2), failure(chunks(bytes, size = 3)))
    }

    @Test
    fun streamWithoutAFinalChunkIsTruncated() = runTest {
        assertEquals(RulesParseFailure.Truncated, failure(chunks("100.1. Synthetic.\n".toByteArray(), final = false)))
    }

    @Test
    fun gapsRepeatsAndChunksAfterFinalAreRejected() = runTest {
        assertEquals(
            RulesParseFailure.NoncontiguousChunk(1, 3, 1, 4),
            failure(flowOf(chunk(0, 0, "abc", false), chunk(1, 4, "def", true))),
        )
        assertEquals(
            RulesParseFailure.NoncontiguousChunk(1, 3, 0, 3),
            failure(flowOf(chunk(0, 0, "abc", false), chunk(0, 3, "def", true))),
        )
        assertEquals(
            RulesParseFailure.ChunkAfterFinal,
            failure(flowOf(chunk(0, 0, "abc", true), chunk(1, 3, "def", true))),
        )
    }

    @Test
    fun inputLimitCountsEveryByte() = runTest {
        assertEquals(listOf("12345"), lines(chunks("12345", size = 2), RulesParserLimits(maxInputBytes = 5)))
        assertEquals(
            RulesParseFailure.LimitExceeded(RulesLimit.INPUT_BYTES, 5, 6),
            failure(chunks("123456", size = 2), RulesParserLimits(maxInputBytes = 5)),
        )
    }

    @Test
    fun lineLimitCountsUtf8Bytes() = runTest {
        // Two-byte characters: three of them are six bytes.
        assertEquals(listOf("\u00E9\u00E9"), lines(chunks("\u00E9\u00E9\n"), RulesParserLimits(maxLineBytes = 4)))
        assertEquals(
            RulesParseFailure.LimitExceeded(RulesLimit.LINE_BYTES, 4, 6),
            failure(chunks("\u00E9\u00E9\u00E9\n"), RulesParserLimits(maxLineBytes = 4)),
        )
    }

    @Test
    fun oneLargeChunkDecodesInBoundedWindowsWithoutLosingCharacters() = runTest {
        // 5,000 repeats of a 2-byte character and a 4-byte surrogate pair: 15,000 chars, 30,000 bytes, one chunk.
        val line = "\u00E9\uD83C\uDCCF".repeat(5_000)
        val limits = RulesParserLimits(maxLineBytes = 32 * 1024)
        assertEquals(listOf(line, "next"), lines(chunks("$line\nnext\n", size = 1 shl 20), limits))
    }

    @Test
    fun tokenCancelledByTheLastUnterminatedLineCallbackThrowsInsteadOfReturningADigest() = runTest {
        // "two" has no terminator, so it is delivered from the end-of-input path of one final chunk.
        val token = object : CancelToken {
            var cancelled = false
            override val isCancelled: Boolean get() = cancelled
            override fun throwIfCancelled() {
                if (cancelled) throw CancellationException("Synthetic caller cancelled")
            }
        }
        val seen = ArrayList<String>()
        try {
            readUtf8Lines(chunks("one\ntwo"), RulesParserLimits(), token) { line, _ ->
                seen += line
                if (line == "two") token.cancelled = true
            }
            fail("Expected cancellation")
        } catch (expected: CancellationException) {
            assertEquals(listOf("one", "two"), seen)
        }
    }

    @Test
    fun digestCoversExactlyTheBytesRead() = runTest {
        val bytes = "\uFEFF100.1. Synthetic.\r\n".toByteArray()
        val digest = readUtf8Lines(chunks(bytes, size = 5), RulesParserLimits(), NeverCancelled) { _, _ -> }
        assertEquals(bytes.size.toLong(), digest.byteCount)
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(expected, digest.sha256Hex)
    }
}
