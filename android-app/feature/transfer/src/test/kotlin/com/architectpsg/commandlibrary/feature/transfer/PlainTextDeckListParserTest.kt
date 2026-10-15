package com.architectpsg.commandlibrary.feature.transfer

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlainTextDeckListParserTest {
    private val codec = PlainTextDeckListCodec()
    private val token = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private fun frame(bytes: ByteArray, sequence: Long = 0, offset: Long = 0, final: Boolean = true) =
        ByteChunk(sequence, offset, SnapshotBytes(bytes), null, final)
    private fun input(chunks: Flow<ByteChunk>, expected: Knowledge<Long> = Knowledge.Absent("Not supplied"), name: String = "list.txt", id: String = "input") =
        TransferInput(EntityId(id), name, Knowledge.Unknown("Ignored", SnapshotList.empty()), expected, chunks)
    private suspend fun parse(text: String): PlainDeckList = success(codec.parse(input(flowOf(frame(text.toByteArray()))), token))
    private fun success(outcome: Outcome<PlainDeckList>): PlainDeckList {
        assertTrue("Expected success, got $outcome", outcome is Outcome.Success)
        return (outcome as Outcome.Success).value
    }
    private fun failure(outcome: Outcome<*>, code: FailureCode = FailureCode.INVALID_INPUT) {
        assertTrue("Expected failure, got $outcome", outcome is Outcome.Failure)
        val error = (outcome as Outcome.Failure).error
        assertEquals(code, error.code)
        assertTrue(error.evidence.isEmpty())
    }
    private fun row(number: Long, offset: Long, raw: String, entry: PlainDeckListEntry? = null) =
        PlainDeckListRow(RowLocation("list.txt", number, offset, null), raw, entry, SnapshotList.empty())

    @Test fun physicalRowsAndOffsetsAreIndependentFixedOracle() = runTest {
        val doc = parse("\uFEFFCommand Library Deck List 1\r\nDeck\n2\t  Café // (SET)  \r\n\nSideboard\n1 Commander\n")
        assertTrue(doc.complete)
        assertEquals(EntityId("input"), doc.inputId)
        assertEquals(listOf(
            row(1, 0, "Command Library Deck List 1"),
            row(2, 32, "Deck"),
            row(3, 37, "2\t  Café // (SET)  ", PlainDeckListEntry(2, "Café // (SET)  ", BoardRole.MAIN)),
            row(4, 59, ""), row(5, 60, "Sideboard"),
            row(6, 70, "1 Commander", PlainDeckListEntry(1, "Commander", BoardRole.SIDEBOARD)),
        ), doc.rows)
    }

    @Test fun everyLegalPartitionOfBomUtf8AndCrLfHasSameFixedRows() = runTest {
        val bytes = "\uFEFF1 é\r\n".toByteArray()
        val expected = listOf(row(1, 0, "1 é", PlainDeckListEntry(1, "é", BoardRole.MAIN)))
        for (cuts in 0 until (1 shl (bytes.size - 1))) {
            val frames = ArrayList<ByteChunk>()
            var start = 0
            for (index in 1..bytes.size) {
                if (index == bytes.size || cuts and (1 shl (index - 1)) != 0) {
                    frames.add(frame(bytes.copyOfRange(start, index), frames.size.toLong(), start.toLong(), index == bytes.size))
                    start = index
                }
            }
            val doc = success(codec.parse(input(flow { for (chunk in frames) emit(chunk) }), token))
            assertTrue(doc.complete)
            assertEquals("partition $cuts", expected, doc.rows)
        }
    }

    @Test fun emptyBomOnlyAndTerminatorRules() = runTest {
        assertTrue(parse("").rows.isEmpty())
        assertTrue(parse("\uFEFF").rows.isEmpty())
        assertEquals(listOf(row(1, 0, "")), parse("\n").rows)
        assertEquals(listOf(row(1, 0, ""), row(2, 2, "")), parse("\r\n\r\n").rows)
        assertEquals(listOf(row(1, 0, " \t")), parse(" \t").rows)
        assertEquals(listOf(row(1, 0, "")), parse("\uFEFF\n").rows)
        assertEquals("\uFEFF", parse("\uFEFF\uFEFF").rows.single().rawText)
    }

    @Test fun allBoardsInitialMainAndExactHeadings() = runTest {
        val doc = parse("1 Initial\nCommander\n1 C\nPartner\n1 P\nBackground\n1 B\nCompanion\n1 Co\nDeck\n1 M\nSideboard\n1 S\nMaybeboard\n1 Mb\n")
        assertTrue(doc.complete)
        assertEquals(listOf(BoardRole.MAIN, BoardRole.COMMANDER, BoardRole.PARTNER, BoardRole.BACKGROUND, BoardRole.COMPANION, BoardRole.MAIN, BoardRole.SIDEBOARD, BoardRole.MAYBEBOARD), doc.rows.mapNotNull { it.entry?.board })
        val exact = parse(" Deck\nDeck \n1 Sideboard\n")
        assertFalse(exact.complete)
        assertEquals(listOf("INVALID_ROW", "INVALID_ROW"), exact.rows.flatMap { it.issues }.map { it.code })
        assertEquals("Sideboard", exact.rows.last().entry?.name)
    }

    @Test fun quantitiesNamesAndInvalidRowsRetainAllSyntax() = runTest {
        val invalid = listOf("0 N", "-1 N", "+1 N", "01 N", "1.5 N", "1x N", "2147483648 N", "1", "1 \t", "١ N", "x1 N", "Side board", "# comment")
        val doc = parse((invalid + listOf("2147483647\t\tN (ABC) // x2  ", "2 N", "2 N")).joinToString("\n"))
        assertFalse(doc.complete)
        assertEquals(invalid, doc.rows.take(invalid.size).map { it.rawText })
        assertEquals(List(10) { "INVALID_QUANTITY" } + List(3) { "INVALID_ROW" }, doc.rows.take(invalid.size).map { it.issues.single().code })
        for (bad in doc.rows.take(invalid.size)) {
            assertNull(bad.entry)
            val issue = bad.issues.single()
            assertEquals(bad.location, issue.location)
            assertEquals(IssueSeverity.ERROR, issue.severity)
            assertTrue(issue.resolution is Knowledge.Absent)
        }
        assertEquals(PlainDeckListEntry(Int.MAX_VALUE, "N (ABC) // x2  ", BoardRole.MAIN), doc.rows[invalid.size].entry)
        assertEquals(2, doc.rows.takeLast(2).count { it.entry?.name == "N" })
    }

    @Test fun optionalMarkerFutureAndMisplacedMalformedMarkers() = runTest {
        assertTrue(parse("Command Library Deck List 1\n1 A").complete)
        for (version in listOf("2", "999999999999999999999999999999999999999999")) {
            val result = codec.parse(input(flowOf(frame("Command Library Deck List $version\n1 A".toByteArray()))), token)
            assertEquals(UnavailableReason.UNSUPPORTED, (result as Outcome.Unavailable).reason)
        }
        for (marker in listOf("Command Library Deck List", "Command Library Deck List 0", "Command Library Deck List 01", "Command Library Deck List +1", "Command Library Deck List 1 ", "Command Library Deck Lists")) {
            val doc = parse(marker)
            assertEquals("INVALID_MARKER", doc.rows.single().issues.single().code)
        }
        val repeated = parse("Command Library Deck List 1\nCommand Library Deck List 1\nCommand Library Deck List 2")
        assertEquals(listOf("INVALID_MARKER", "INVALID_MARKER"), repeated.rows.flatMap { it.issues }.map { it.code })
        assertEquals("INVALID_MARKER", parse("\nCommand Library Deck List 1").rows.last().issues.single().code)
    }

    @Test fun futureVersionBodyIsOpaqueButPhysicalAndTransportBoundsStillApply() = runTest {
        val prefix = "Command Library Deck List 2\n"
        val opaqueBodies = listOf("1 " + "a".repeat(16385), "1 A\n".repeat(10001), "Command Library Deck List 0\nnot v1 syntax")
        for (body in opaqueBodies) {
            val result = codec.parse(input(flowOf(frame((prefix + body).toByteArray()))), token)
            assertEquals(UnavailableReason.UNSUPPORTED, (result as Outcome.Unavailable).reason)
        }
        val opaque = prefix + "1 " + "a".repeat(16385)
        failure(codec.parse(input(flow { emit(frame(opaque.toByteArray())); throw IllegalStateException("secret") }), token), FailureCode.TRANSPORT)
        for (body in listOf("z".repeat(65537), "\n".repeat(20000), "A\rB")) {
            failure(codec.parse(input(flowOf(frame((prefix + body).toByteArray()))), token))
        }
        failure(codec.parse(input(flowOf(frame(prefix.toByteArray() + byteArrayOf(0x80.toByte())))), token))
    }

    @Test fun strictUtf8AndLoneCrRejectWholeDocument() = runTest {
        val malformed = listOf(
            byteArrayOf(0x80.toByte()), byteArrayOf(0xc0.toByte(), 0xaf.toByte()),
            byteArrayOf(0xe0.toByte(), 0x80.toByte(), 0x80.toByte()),
            byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()),
            byteArrayOf(0xf4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()),
            byteArrayOf(0xf5.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte()),
            byteArrayOf(0xe2.toByte(), 0x82.toByte()), byteArrayOf(0xe2.toByte(), 32),
            "1 A\r".toByteArray(), "1 A\r1 B".toByteArray(), "\r\r\n".toByteArray(),
        )
        for (bytes in malformed) failure(codec.parse(input(flowOf(frame("1 Good\n".toByteArray() + bytes))), token))
        assertEquals("𝄞", parse("1 𝄞").rows.single().entry?.name)
    }

    @Test fun framingKnownLengthAndIgnoredEnvelope() = runTest {
        val bytes = "1 A".toByteArray()
        val good = input(flowOf(frame(bytes, final = false), frame(byteArrayOf(), 1, 3)), Knowledge.Known(3))
        assertTrue(success(codec.parse(good, token)).complete)
        val bad = listOf(
            flowOf<ByteChunk>(), flowOf(frame(bytes, 1)), flowOf(frame(bytes, offset = 1)),
            flowOf(frame(byteArrayOf(), final = false), frame(bytes, 1)), flowOf(frame(bytes, final = false)),
            flowOf(frame(bytes), frame(byteArrayOf(), 1, 3)),
            flowOf(frame(byteArrayOf(49), final = false), frame(byteArrayOf(32,65), 2, 1)),
            flowOf(frame(byteArrayOf(49), final = false), frame(byteArrayOf(32,65), 1, 2)),
        )
        for (chunks in bad) failure(codec.parse(input(chunks), token))
        for (size in listOf(-1L, 0L, 2L, 4L, 8L * 1024 * 1024 + 1)) failure(codec.parse(input(flowOf(frame(bytes)), Knowledge.Known(size)), token))
        assertTrue(success(codec.parse(input(flowOf(frame(bytes)), Knowledge.Unknown("Untrusted", SnapshotList.empty())), token)).complete)
    }

    @Test fun readFailureAfterFinalNeverSucceedsAndIsSanitized() = runTest {
        for (text in listOf("1 A", "Command Library Deck List 2")) {
            val result = codec.parse(input(flow { emit(frame(text.toByteArray())); throw IllegalStateException("private path /secret") }), token)
            failure(result, FailureCode.TRANSPORT)
            assertFalse((result as Outcome.Failure).error.message.contains("secret"))
        }
    }

    @Test fun cancellationAndFatalErrorsPropagate() = runTest {
        val cancelled = object : CancelToken {
            override val isCancelled = true
            override fun throwIfCancelled(): Unit = throw CancellationException("token")
        }
        try { codec.parse(input(flowOf(frame(byteArrayOf()))), cancelled); fail() } catch (_: CancellationException) { }
        try { codec.parse(input(flow { throw CancellationException("upstream") }), token); fail() } catch (_: CancellationException) { }
        val fatal = AssertionError("fatal")
        try { codec.parse(input(flow { throw fatal }), token); fail() } catch (actual: AssertionError) { assertSame(fatal, actual) }
        var polls = 0
        val during = object : CancelToken {
            override val isCancelled = false
            override fun throwIfCancelled() { if (++polls == 5) throw CancellationException("during scan") }
        }
        try { codec.parse(input(flowOf(frame("z".repeat(65536).toByteArray()))), during); fail() } catch (_: CancellationException) { }
        assertEquals(5, polls)
    }

    @Test fun nameLineDisplayNameAndIdentifierCeilings() = runTest {
        assertEquals(16384, parse("1 " + "é".repeat(8192)).rows.single().entry?.name?.toByteArray()?.size)
        failure(codec.parse(input(flowOf(frame(("1 " + "a".repeat(16385)).toByteArray()))), token))
        assertEquals(65536, parse("z".repeat(65536)).rows.single().rawText.length)
        failure(codec.parse(input(flowOf(frame("z".repeat(65537).toByteArray()))), token))
        assertTrue(success(codec.parse(input(flowOf(frame(byteArrayOf())), name = "é".repeat(512), id = "a".repeat(1024)), token)).complete)
        for ((name, id) in listOf("a".repeat(1025) to "i", "bad\uD800" to "i", "n" to "i".repeat(1025), "n" to "i\uDC00")) {
            failure(codec.parse(input(flowOf(frame(byteArrayOf())), name = name, id = id), token))
        }
    }

    @Test fun lineEntryAndTotalByteCeilingsAreInclusive() = runTest {
        assertEquals(20000, parse("\n".repeat(20000)).rows.size)
        failure(codec.parse(input(flowOf(frame("\n".repeat(20001).toByteArray()))), token))
        assertEquals(10000, parse("1 A\n".repeat(10000)).rows.size)
        failure(codec.parse(input(flowOf(frame("1 A\n".repeat(10001).toByteArray()))), token))
        val exact = ("z".repeat(65535) + "\n").repeat(128).toByteArray()
        assertEquals(8 * 1024 * 1024, exact.size)
        assertEquals(128, success(codec.parse(input(flowOf(frame(exact)), Knowledge.Known(exact.size.toLong())), token)).rows.size)
        failure(codec.parse(input(flowOf(frame(exact + byteArrayOf(10)))), token))
    }

    @Test fun documentFailureDoesNotStopOrdinaryRowErrorConsumption() = runTest {
        var observed = false
        val result = codec.parse(input(flow {
            emit(frame("invalid\n".toByteArray(), final = false))
            observed = true
            emit(frame("1 A".toByteArray(), 1, 8))
        }), token)
        assertTrue(observed)
        val doc = success(result)
        assertFalse(doc.complete)
        assertEquals("A", doc.rows.last().entry?.name)
    }
}
