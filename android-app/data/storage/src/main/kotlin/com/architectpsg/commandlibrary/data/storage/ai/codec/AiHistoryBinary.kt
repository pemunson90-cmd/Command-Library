package com.architectpsg.commandlibrary.data.storage.ai.codec

import com.architectpsg.commandlibrary.core.model.AiHistoryLimits
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

internal class AiWire<T>(val put: AiWriter.(T) -> Unit, val get: AiReader.() -> T)
internal fun <T> wire(put: AiWriter.(T) -> Unit, get: AiReader.() -> T): AiWire<T> = AiWire(put, get)

/** Local to one operation; collection budgets include every nested declared list. */
internal class AiWriter {
    private val buffer = ByteArrayOutputStream()
    private var elements = 0
    private var bindingBytes = 0
    private val output = DataOutputStream(object : OutputStream() {
        override fun write(value: Int) {
            reserve(1)
            buffer.write(value)
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            reserve(length)
            buffer.write(bytes, offset, length)
        }
    })
    private fun reserve(length: Int) {
        require(length <= AiHistoryLimits.MAX_PAYLOAD_BYTES - buffer.size()) { "History byte budget exceeded" }
    }
    fun byte(value: Int) { output.writeByte(value) }
    fun int(value: Int) { output.writeInt(value) }
    fun long(value: Long) { output.writeLong(value) }
    fun bool(value: Boolean) { byte(if (value) 1 else 0) }
    fun string(value: String, cap: Int = AiHistoryLimits.MAX_METADATA_BYTES, binding: Boolean = false) {
        // Count exact UTF8 bytes and reject unpaired UTF16 before allocating encoder storage.
        require(value.length <= cap) { "History string budget exceeded" }
        var length = 0
        var index = 0
        while (index < value.length) {
            val ch = value[index++]
            val width = when {
                ch < '\u0080' -> 1
                ch < '\u0800' -> 2
                ch in '\ud800'..'\udbff' -> {
                    require(index < value.length && value[index] in '\udc00'..'\udfff') { "Invalid history Unicode" }
                    index++
                    4
                }
                ch in '\udc00'..'\udfff' -> throw IllegalArgumentException("Invalid history Unicode")
                else -> 3
            }
            require(width <= cap - length) { "History string budget exceeded" }
            length += width
        }
        if (binding) {
            require(length <= AiHistoryLimits.MAX_PROMPT_BYTES - bindingBytes)
            bindingBytes += length
        }
        reserve(4 + length)
        val encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
        int(length)
        val bytes = ByteArray(length)
        encoded.get(bytes)
        output.write(bytes)
    }
    fun count(size: Int, cap: Int) {
        require(size <= cap && size <= 1024 - elements) { "History collection budget exceeded" }
        elements += size
        int(size)
    }
    fun <T> v(codec: AiWire<T>, value: T) { codec.put(this, value) }
    fun finish(): ByteArray = buffer.toByteArray()
}

internal class AiReader(bytes: ByteArray) {
    private val buffer = ByteArrayInputStream(bytes)
    private val input = DataInputStream(buffer)
    private var elements = 0
    private var bindingBytes = 0
    val remaining: Int get() = buffer.available()
    fun byte(): Int = input.readUnsignedByte()
    fun ushort(): Int = input.readUnsignedShort()
    fun int(): Int = input.readInt()
    fun long(): Long = input.readLong()
    private fun uint(): Long = int().toLong() and 0xffffffffL
    fun bool(): Boolean = when (byte()) { 0 -> false; 1 -> true; else -> badTag() }
    fun string(cap: Int = AiHistoryLimits.MAX_METADATA_BYTES, binding: Boolean = false): String {
        val length = uint()
        require(length <= cap.toLong() && length <= remaining.toLong()) { "Invalid history string length" }
        if (binding) {
            require(length <= (AiHistoryLimits.MAX_PROMPT_BYTES - bindingBytes).toLong())
            bindingBytes += length.toInt()
        }
        val bytes = ByteArray(length.toInt())
        input.readFully(bytes)
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
    fun count(cap: Int): Int {
        val count = uint()
        require(count <= cap.toLong() && count <= remaining.toLong() &&
            count <= (1024 - elements).toLong()) { "Invalid history collection count" }
        elements += count.toInt()
        return count.toInt()
    }
    fun <T> v(codec: AiWire<T>): T = codec.get(this)
}

internal fun badTag(): Nothing = throw IllegalArgumentException("Invalid history field tag")
internal val textWire: AiWire<String> = wire({ string(it) }, { string() })
internal val idTextWire: AiWire<String> = wire({ string(it, AiHistoryLimits.MAX_METADATA_BYTES) }, { string(AiHistoryLimits.MAX_METADATA_BYTES) })
internal fun <T> nullable(codec: AiWire<T>): AiWire<T?> = wire({ value ->
    bool(value != null)
    if (value != null) v(codec, value)
}, { if (bool()) v(codec) else null })

/** Every supported element has at least one wire byte. Counts are checked before allocation. */
internal fun <T> list(codec: AiWire<T>, cap: Int = 32): AiWire<SnapshotList<T>> = wire({ values ->
    count(values.size, cap)
    for (value in values) v(codec, value)
}, {
    val count = count(cap)
    val values = ArrayList<T>()
    repeat(count) { values.add(v(codec)) }
    SnapshotList.copyOf(values)
})
internal inline fun <reified T : Enum<T>> enumeration(): AiWire<T> = wire({ v(idTextWire, it.name) }, { enumValueOf<T>(v(idTextWire)) })
