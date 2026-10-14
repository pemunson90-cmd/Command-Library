package com.architectpsg.commandlibrary.data.storage.life.codec

import com.architectpsg.commandlibrary.core.model.SnapshotList
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

internal class LifeWire<T>(val put: LifeWriter.(T) -> Unit, val get: LifeReader.() -> T)
internal fun <T> wire(put: LifeWriter.(T) -> Unit, get: LifeReader.() -> T) = LifeWire(put, get)

internal class LifeWriter(limit: Int) {
    private val buffer = ByteArrayOutputStream()
    private val output = DataOutputStream(object : OutputStream() {
        override fun write(value: Int) {
            require(buffer.size() < limit) { "Life payload exceeds byte budget" }
            buffer.write(value)
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            require(length <= limit - buffer.size()) { "Life payload exceeds byte budget" }
            buffer.write(bytes, offset, length)
        }
    })
    fun byte(value: Int) = output.writeByte(value)
    fun int(value: Int) = output.writeInt(value)
    fun long(value: Long) = output.writeLong(value)
    fun double(value: Double) { require(value.isFinite()); output.writeDouble(value) }
    fun bool(value: Boolean) = byte(if (value) 1 else 0)
    fun blob(bytes: ByteArray, max: Int) {
        require(bytes.size <= max) { "Life field exceeds byte budget" }
        int(bytes.size); output.write(bytes)
    }
    fun string(value: String) {
        require(value.length <= 65_536) { "String exceeds UTF8 budget" }
        val encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
        require(encoded.remaining() <= 65_536) { "String exceeds UTF8 budget" }
        val bytes = ByteArray(encoded.remaining()); encoded.get(bytes); blob(bytes, 65_536)
    }
    fun <T> v(codec: LifeWire<T>, value: T) = codec.put(this, value)
    fun finish(): ByteArray = buffer.toByteArray()
}

internal class LifeReader(bytes: ByteArray, var formatVersion: Int = 2) {
    private val buffer = ByteArrayInputStream(bytes)
    private val input = DataInputStream(buffer)
    val remaining: Int get() = buffer.available()
    fun byte(): Int = input.readUnsignedByte()
    fun int(): Int = input.readInt()
    fun long(): Long = input.readLong()
    fun double(): Double = input.readDouble().also { require(it.isFinite()) }
    fun bool(): Boolean = when (byte()) { 0 -> false; 1 -> true; else -> errorTag() }
    fun blob(max: Int): ByteArray {
        val size = int()
        require(size >= 0 && size <= max && size <= remaining) { "Invalid life field byte length" }
        return ByteArray(size).also { input.readFully(it) }
    }
    fun string(): String = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(blob(65_536))).toString()
    fun <T> v(codec: LifeWire<T>): T = codec.get(this)
}

internal fun errorTag(): Nothing = throw IllegalArgumentException("Unsupported life field tag")
internal val intWire = wire<Int>({ int(it) }, { int() })
internal val longWire = wire<Long>({ long(it) }, { long() })
internal val stringWire = wire<String>({ string(it) }, { string() })
internal fun <T> nullable(codec: LifeWire<T>) = wire<T?>({ value ->
    bool(value != null); if (value != null) v(codec, value)
}, { if (bool()) v(codec) else null })

/** Every element in these lists has at least one byte; never reserve untrusted count-sized capacity. */
internal fun <T> list(codec: LifeWire<T>, max: Int = Int.MAX_VALUE) = wire<SnapshotList<T>>({ values ->
    require(values.size <= max) { "Life collection exceeds count budget" }
    int(values.size); for (value in values) v(codec, value)
}, {
    val count = int()
    require(count >= 0 && count <= max && count <= remaining) { "Invalid life collection count" }
    val values = ArrayList<T>()
    repeat(count) { values.add(v(codec)) }
    SnapshotList.copyOf(values)
})

/** The ordered explicit constants are the stable wire tag table; declaration ordinal is never used. */
internal fun <T> enumeration(vararg values: T) = wire<T>({ value ->
    val index = values.indexOf(value); require(index >= 0); byte(index + 1)
}, { values.getOrNull(byte() - 1) ?: errorTag() })

internal val durationWire = wire<Duration>({ value ->
    require(value.isFinite()) { "Persistent duration must be finite" }
    value.toComponents { seconds: Long, nanos: Int -> long(seconds); int(nanos) }
}, {
    val seconds = long(); val nanos = int()
    require(nanos in -999_999_999..999_999_999 &&
        (seconds == 0L || nanos == 0 || (seconds > 0) == (nanos > 0))) { "Invalid duration components" }
    val value = seconds.seconds + nanos.nanoseconds
    require(value.isFinite()) { "Duration exceeds finite range" }
    value.toComponents { actualSeconds: Long, actualNanos: Int ->
        require(seconds == actualSeconds && nanos == actualNanos) { "Duration cannot be represented exactly" }
    }
    value
})
