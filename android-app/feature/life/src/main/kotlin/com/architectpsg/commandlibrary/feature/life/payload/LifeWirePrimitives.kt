package com.architectpsg.commandlibrary.feature.life.payload

import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

internal const val LIFE_WIRE_HEADER_BYTES: Int = 11
internal const val LIFE_WIRE_MAX_STRING_BYTES: Int = 64 * 1024
private const val MAX_RAW_BYTES = 16 * 1024 * 1024

internal enum class LifePayloadKind(val tag: Int, val maxFrameBytes: Int) {
    PROFILE(1, 4 * 1024 * 1024), SNAPSHOT(2, 4 * 1024 * 1024), ARCHIVE(3, MAX_RAW_BYTES),
}
internal enum class LifeWireProblem { MALFORMED, TOO_LARGE, UNSUPPORTED_VERSION }
internal class LifeWireException(val problem: LifeWireProblem) : IllegalArgumentException("Life payload: $problem")
private fun fail(problem: LifeWireProblem): Nothing = throw LifeWireException(problem)

/** Checks precede capacity growth; neither output length nor backing capacity can exceed the cap. */
private class BoundedOutput(private val limit: Int) : OutputStream() {
    private var storage = ByteArray(minOf(32, limit))
    private var size = 0
    val remaining: Int get() = limit - size
    fun reserve(count: Int) {
        if (count < 0 || count > remaining) fail(LifeWireProblem.TOO_LARGE)
    }
    private fun grow(count: Int) {
        reserve(count)
        val required = size + count
        if (required > storage.size) {
            storage = storage.copyOf(minOf(limit, maxOf(required, maxOf(1, storage.size) * 2)))
        }
    }
    override fun write(value: Int) {
        grow(1); storage[size++] = value.toByte()
    }
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > bytes.size - length) throw IndexOutOfBoundsException()
        grow(length)
        bytes.copyInto(storage, size, offset, offset + length); size += length
    }
    fun bytes(): ByteArray = storage.copyOf(size)
}

internal class LifeWireWriter(maxBytes: Int) {
    init { require(maxBytes in 0..MAX_RAW_BYTES) }
    private val buffer = BoundedOutput(maxBytes)
    private val output = DataOutputStream(buffer)
    fun writeByte(value: Int) {
        if (value !in 0..255) fail(LifeWireProblem.MALFORMED)
        buffer.reserve(1); output.writeByte(value)
    }
    fun writeBoolean(value: Boolean): Unit = writeByte(if (value) 1 else 0)
    fun writeInt(value: Int) { buffer.reserve(4); output.writeInt(value) }
    fun writeLong(value: Long) { buffer.reserve(8); output.writeLong(value) }
    fun writeBytes(value: ByteArray) { buffer.reserve(value.size); output.write(value) }
    fun writeString(value: String) {
        buffer.reserve(4)
        val allowance = minOf(LIFE_WIRE_MAX_STRING_BYTES, buffer.remaining - 4)
        // Every valid UTF-8 string uses at least as many bytes as UTF-16 code units.
        if (value.length > allowance) fail(LifeWireProblem.TOO_LARGE)
        val encoder = Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        // One extra byte detects overflow; allocation never follows unbounded source length.
        val encoded = ByteBuffer.allocate(minOf(allowance + 1, value.length * 3 + 1))
        try {
            val result = encoder.encode(CharBuffer.wrap(value), encoded, true)
            if (result.isError) result.throwException()
            if (result.isOverflow) fail(LifeWireProblem.TOO_LARGE)
            val flushed = encoder.flush(encoded)
            if (flushed.isError) flushed.throwException()
            if (flushed.isOverflow) fail(LifeWireProblem.TOO_LARGE)
        } catch (_: CharacterCodingException) { fail(LifeWireProblem.MALFORMED) }
        val length = encoded.position()
        if (length > allowance) fail(LifeWireProblem.TOO_LARGE)
        buffer.reserve(4 + length)
        val bytes = ByteArray(length)
        encoded.flip(); encoded.get(bytes)
        writeInt(length); writeBytes(bytes)
    }
    fun writeDuration(value: Duration) {
        if (!value.isFinite()) fail(LifeWireProblem.MALFORMED)
        value.toComponents { seconds, nanos ->
            if (checkedDuration(seconds, nanos) != value) fail(LifeWireProblem.MALFORMED)
            buffer.reserve(12); writeLong(seconds); writeInt(nanos)
        }
    }
    fun writeTimestamp(value: UtcTimestamp) {
        buffer.reserve(12); writeLong(value.epochSeconds); writeInt(value.nanoAdjustment)
    }
    fun bytes(): ByteArray = buffer.bytes()
}

/** Owns a bounded input snapshot. A failed reader is discarded, never resumed or silently repaired. */
internal class LifeWireReader(bytes: ByteArray) {
    init { if (bytes.size > MAX_RAW_BYTES) fail(LifeWireProblem.TOO_LARGE) }
    private val buffer = ByteArrayInputStream(bytes.copyOf())
    private val input = DataInputStream(buffer)
    val remaining: Int get() = buffer.available()
    private fun need(count: Int) {
        if (count < 0 || count > remaining) fail(LifeWireProblem.MALFORMED)
    }
    fun readByte(): Int { need(1); return input.readUnsignedByte() }
    fun readBoolean(): Boolean = when (readByte()) {
        0 -> false
        1 -> true
        else -> fail(LifeWireProblem.MALFORMED)
    }
    fun readInt(): Int { need(4); return input.readInt() }
    fun readLong(): Long { need(8); return input.readLong() }
    fun readBytes(count: Int): ByteArray {
        need(count)
        return ByteArray(count).also(input::readFully)
    }
    fun readString(): String {
        val length = readInt()
        if (length < 0) fail(LifeWireProblem.MALFORMED)
        if (length > LIFE_WIRE_MAX_STRING_BYTES) fail(LifeWireProblem.TOO_LARGE)
        val bytes = readBytes(length)
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) { fail(LifeWireProblem.MALFORMED) }
    }
    fun readDuration(): Duration = checkedDuration(readLong(), readInt())
    fun readTimestamp(): UtcTimestamp {
        val seconds = readLong(); val nanos = readInt()
        if (nanos !in 0..999_999_999) fail(LifeWireProblem.MALFORMED)
        return UtcTimestamp(seconds, nanos)
    }
    fun requireEnd() { if (remaining != 0) fail(LifeWireProblem.MALFORMED) }
}

private fun checkedDuration(seconds: Long, nanos: Int): Duration {
    if (nanos !in -999_999_999..999_999_999 || (seconds > 0 && nanos < 0) || (seconds < 0 && nanos > 0)) {
        fail(LifeWireProblem.MALFORMED)
    }
    val whole = seconds.seconds
    if (!whole.isFinite()) fail(LifeWireProblem.MALFORMED)
    val result = whole + nanos.nanoseconds
    if (!result.isFinite() || !result.toComponents { s, n -> s == seconds && n == nanos }) {
        fail(LifeWireProblem.MALFORMED)
    }
    return result
}

internal object LifeWireFraming {
    private val magic = byteArrayOf(0x43, 0x4c, 0x4c, 0x50)
    fun wrap(kind: LifePayloadKind, body: ByteArray): ByteArray {
        if (body.size > kind.maxFrameBytes - LIFE_WIRE_HEADER_BYTES) fail(LifeWireProblem.TOO_LARGE)
        return LifeWireWriter(kind.maxFrameBytes).apply {
            writeBytes(magic); writeByte(kind.tag); writeByte(0); writeByte(1); writeInt(body.size); writeBytes(body)
        }.bytes()
    }
    fun open(bytes: ByteArray, expected: LifePayloadKind): LifeWireReader {
        if (bytes.size > expected.maxFrameBytes) fail(LifeWireProblem.TOO_LARGE)
        if (bytes.size < LIFE_WIRE_HEADER_BYTES) fail(LifeWireProblem.MALFORMED)
        val reader = LifeWireReader(bytes)
        for (byte in magic) if (reader.readByte() != byte.toInt()) fail(LifeWireProblem.MALFORMED)
        if (reader.readByte() != expected.tag) fail(LifeWireProblem.MALFORMED)
        val version = reader.readByte() * 256 + reader.readByte()
        val length = reader.readInt()
        if (length < 0 || length != reader.remaining) fail(LifeWireProblem.MALFORMED)
        if (version == 0) fail(LifeWireProblem.MALFORMED)
        if (version != 1) fail(LifeWireProblem.UNSUPPORTED_VERSION)
        return reader
    }
}
