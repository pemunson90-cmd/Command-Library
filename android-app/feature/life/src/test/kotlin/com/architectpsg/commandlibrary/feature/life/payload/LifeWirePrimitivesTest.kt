package com.architectpsg.commandlibrary.feature.life.payload

import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class LifeWirePrimitivesTest {
    private fun rejected(problem: LifeWireProblem, block: () -> Unit) {
        assertEquals(problem, assertThrows(LifeWireException::class.java) { block() }.problem)
    }
    private fun rawDuration(seconds: Long, nanos: Int) = ByteBuffer.allocate(12).putLong(seconds).putInt(nanos).array()

    @Test fun profileFrameHasPinnedV1BytesAndCrossKindCannotBeDecoded() {
        val frame = LifeWireFraming.wrap(LifePayloadKind.PROFILE, byteArrayOf(42))
        assertArrayEquals(byteArrayOf(0x43, 0x4c, 0x4c, 0x50, 1, 0, 1, 0, 0, 0, 1, 42), frame)
        val body = LifeWireFraming.open(frame, LifePayloadKind.PROFILE)
        assertEquals(42, body.readByte()); body.requireEnd()
        rejected(LifeWireProblem.MALFORMED) { LifeWireFraming.open(frame, LifePayloadKind.SNAPSHOT) }
    }
    @Test fun framesRejectAllTruncationsBadMagicVersionLengthAndTrailingBytes() {
        val frame = LifeWireFraming.wrap(LifePayloadKind.PROFILE, byteArrayOf(42))
        for (length in 0 until frame.size) rejected(LifeWireProblem.MALFORMED) { LifeWireFraming.open(frame.copyOf(length), LifePayloadKind.PROFILE) }
        rejected(LifeWireProblem.MALFORMED) { LifeWireFraming.open(frame.copyOf().also { it[0] = 0 }, LifePayloadKind.PROFILE) }
        rejected(LifeWireProblem.MALFORMED) { LifeWireFraming.open(frame.copyOf().also { it[6] = 0 }, LifePayloadKind.PROFILE) }
        rejected(LifeWireProblem.UNSUPPORTED_VERSION) { LifeWireFraming.open(frame.copyOf().also { it[6] = 2 }, LifePayloadKind.PROFILE) }
        rejected(LifeWireProblem.MALFORMED) { LifeWireFraming.open(frame.copyOf().also { ByteBuffer.wrap(it).putInt(7, -1) }, LifePayloadKind.PROFILE) }
        rejected(LifeWireProblem.MALFORMED) { LifeWireFraming.open(frame.copyOf().also { ByteBuffer.wrap(it).putInt(7, Int.MAX_VALUE) }, LifePayloadKind.PROFILE) }
        rejected(LifeWireProblem.MALFORMED) { LifeWireFraming.open(frame + 0, LifePayloadKind.PROFILE) }
    }
    @Test fun newerVersionRequiresCompleteValidFramingBeforeUnsupportedClassification() {
        val frame = LifeWireFraming.wrap(LifePayloadKind.PROFILE, byteArrayOf(42)).also { it[6] = 2 }
        for (length in 0 until frame.size) rejected(LifeWireProblem.MALFORMED) {
            LifeWireFraming.open(frame.copyOf(length), LifePayloadKind.PROFILE)
        }
        rejected(LifeWireProblem.MALFORMED) {
            LifeWireFraming.open(frame.copyOf().also { ByteBuffer.wrap(it).putInt(7, 0) }, LifePayloadKind.PROFILE)
        }
        rejected(LifeWireProblem.UNSUPPORTED_VERSION) { LifeWireFraming.open(frame, LifePayloadKind.PROFILE) }
    }

    @Test fun frameCapsIncludeHeaderAndEachKindRetainsItsOwnLimit() {
        val limit = 4 * 1024 * 1024
        val body = ByteArray(limit - 11)
        assertEquals(limit, LifeWireFraming.wrap(LifePayloadKind.PROFILE, body).size)
        rejected(LifeWireProblem.TOO_LARGE) { LifeWireFraming.wrap(LifePayloadKind.SNAPSHOT, ByteArray(limit - 10)) }
        rejected(LifeWireProblem.TOO_LARGE) { LifeWireFraming.open(ByteArray(limit + 1), LifePayloadKind.PROFILE) }
        assertEquals(limit + 12, LifeWireFraming.wrap(LifePayloadKind.ARCHIVE, ByteArray(limit + 1)).size)
    }
    @Test fun primitivesPreserveExactSignedWidthsAndRejectNoncanonicalBooleanAndTrailingData() {
        val writer = LifeWireWriter(64)
        writer.writeBoolean(false); writer.writeBoolean(true); writer.writeInt(Int.MIN_VALUE); writer.writeLong(Long.MAX_VALUE)
        writer.writeTimestamp(UtcTimestamp(Long.MIN_VALUE, 999_999_999))
        val reader = LifeWireReader(writer.bytes())
        assertFalse(reader.readBoolean()); assertTrue(reader.readBoolean()); assertEquals(Int.MIN_VALUE, reader.readInt())
        assertEquals(Long.MAX_VALUE, reader.readLong()); assertEquals(UtcTimestamp(Long.MIN_VALUE, 999_999_999), reader.readTimestamp()); reader.requireEnd()
        rejected(LifeWireProblem.MALFORMED) { LifeWireReader(byteArrayOf(2)).readBoolean() }
        rejected(LifeWireProblem.MALFORMED) { LifeWireReader(byteArrayOf(0)).requireEnd() }
        rejected(LifeWireProblem.MALFORMED) { LifeWireReader(byteArrayOf(1, 2, 3)).readInt() }
        rejected(LifeWireProblem.MALFORMED) { LifeWireReader(rawDuration(0, -1)).readTimestamp() }
    }
    @Test fun utf8PreservesWhitespaceUnicodeAndExactByteBoundaryWithoutReplacement() {
        val value = "  Synthetic é ☃ 😀\u0000  "
        val writer = LifeWireWriter(100_000); writer.writeString(value)
        val reader = LifeWireReader(writer.bytes()); assertEquals(value, reader.readString()); reader.requireEnd()
        val boundary = "é".repeat(32_768)
        val exact = LifeWireWriter(65_540); exact.writeString(boundary)
        assertEquals(boundary, LifeWireReader(exact.bytes()).readString())
        rejected(LifeWireProblem.TOO_LARGE) { LifeWireWriter(100_000).writeString(boundary + "é") }
        rejected(LifeWireProblem.MALFORMED) { LifeWireWriter(100).writeString("\uD800") }
        rejected(LifeWireProblem.MALFORMED) { LifeWireWriter(100).writeString("\uDC00") }
        rejected(LifeWireProblem.MALFORMED) { LifeWireReader(byteArrayOf(0, 0, 0, 2, 0xc0.toByte(), 0xaf.toByte())).readString() }
        rejected(LifeWireProblem.MALFORMED) { LifeWireReader(byteArrayOf(-1, -1, -1, -1)).readString() }
        rejected(LifeWireProblem.TOO_LARGE) { LifeWireReader(ByteBuffer.allocate(4).putInt(65_537).array()).readString() }
    }
    @Test fun boundedWritesFailBeforeAddingPartialPrimitiveOrStringBytes() {
        val writer = LifeWireWriter(5); writer.writeByte(42)
        rejected(LifeWireProblem.TOO_LARGE) { writer.writeLong(1) }
        rejected(LifeWireProblem.TOO_LARGE) { writer.writeString("x") }
        assertArrayEquals(byteArrayOf(42), writer.bytes())
        writer.writeInt(7); assertEquals(5, writer.bytes().size)
        rejected(LifeWireProblem.TOO_LARGE) { writer.writeByte(0) }
    }
    @Test fun readerAndWriterOwnBytesAcrossCallerMutation() {
        val writer = LifeWireWriter(1); writer.writeByte(42)
        val raw = writer.bytes(); val reader = LifeWireReader(raw)
        raw[0] = 99
        assertEquals(42, reader.readByte()); reader.requireEnd()
        assertArrayEquals(byteArrayOf(42), writer.bytes())
        val frame = LifeWireFraming.wrap(LifePayloadKind.PROFILE, byteArrayOf(42))
        val opened = LifeWireFraming.open(frame, LifePayloadKind.PROFILE)
        frame[11] = 99
        assertEquals(42, opened.readByte()); opened.requireEnd()
    }

    @Test fun durationRoundTripsNanosecondsAndSignedFiniteExtremes() {
        val huge = 4_000_000_000_000_000_000L.milliseconds
        val lastFinite = (Long.MAX_VALUE / 2 - 1).milliseconds
        assertTrue(lastFinite.isFinite())
        for (value in listOf(Duration.ZERO, 1.nanoseconds, (-1).nanoseconds, 1.seconds + 1.nanoseconds,
            (-1).seconds - 1.nanoseconds, huge, -huge, lastFinite, -lastFinite)) {
            val writer = LifeWireWriter(12); writer.writeDuration(value)
            val reader = LifeWireReader(writer.bytes()); assertEquals(value, reader.readDuration()); reader.requireEnd()
        }
        for (value in listOf(Duration.INFINITE, -Duration.INFINITE)) rejected(LifeWireProblem.MALFORMED) { LifeWireWriter(12).writeDuration(value) }
        for (pair in listOf(rawDuration(1, -1), rawDuration(-1, 1), rawDuration(0, 1_000_000_000),
            rawDuration(0, Int.MIN_VALUE), rawDuration(Long.MAX_VALUE, 0), rawDuration(4_000_000_000_000_000L, 1))) {
            rejected(LifeWireProblem.MALFORMED) { LifeWireReader(pair).readDuration() }
        }
    }
}
