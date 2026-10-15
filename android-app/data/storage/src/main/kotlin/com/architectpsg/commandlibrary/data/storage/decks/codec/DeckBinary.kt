package com.architectpsg.commandlibrary.data.storage.decks.codec

import com.architectpsg.commandlibrary.core.model.DeckPayloadLimits
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

internal class DeckWire<T>(val put: DeckWriter.(T) -> Unit, val get: DeckReader.() -> T)
internal fun <T> wire(put: DeckWriter.(T) -> Unit, get: DeckReader.() -> T): DeckWire<T> = DeckWire(put, get)

/** Local to one operation; collection budgets include every nested declared list. */
internal class DeckWriter {
    private val buffer = ByteArrayOutputStream()
    private var elements = 0
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
        require(length <= DeckPayloadLimits.MAX_ENCODED_BYTES - buffer.size()) { "Deck byte budget exceeded" }
    }
    fun byte(value: Int) { output.writeByte(value) }
    fun int(value: Int) { output.writeInt(value) }
    fun long(value: Long) { output.writeLong(value) }
    fun bool(value: Boolean) { byte(if (value) 1 else 0) }
    fun string(value: String, cap: Int = DeckPayloadLimits.MAX_TEXT_BYTES) {
        // Count exact UTF8 bytes and reject unpaired UTF16 before allocating encoder storage.
        require(value.length <= cap) { "Deck string budget exceeded" }
        var length = 0
        var index = 0
        while (index < value.length) {
            val ch = value[index++]
            val width = when {
                ch < '\u0080' -> 1
                ch < '\u0800' -> 2
                ch in '\ud800'..'\udbff' -> {
                    require(index < value.length && value[index] in '\udc00'..'\udfff') { "Invalid deck Unicode" }
                    index++
                    4
                }
                ch in '\udc00'..'\udfff' -> throw IllegalArgumentException("Invalid deck Unicode")
                else -> 3
            }
            require(width <= cap - length) { "Deck string budget exceeded" }
            length += width
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
        require(size <= cap && size <= DeckPayloadLimits.MAX_COLLECTION_ELEMENTS - elements) { "Deck collection budget exceeded" }
        elements += size
        int(size)
    }
    fun <T> v(codec: DeckWire<T>, value: T) { codec.put(this, value) }
    fun finish(): ByteArray = buffer.toByteArray()
}

internal class DeckReader(bytes: ByteArray) {
    private val buffer = ByteArrayInputStream(bytes)
    private val input = DataInputStream(buffer)
    private var elements = 0
    val remaining: Int get() = buffer.available()
    fun byte(): Int = input.readUnsignedByte()
    fun ushort(): Int = input.readUnsignedShort()
    fun int(): Int = input.readInt()
    fun long(): Long = input.readLong()
    private fun uint(): Long = int().toLong() and 0xffffffffL
    fun bool(): Boolean = when (byte()) { 0 -> false; 1 -> true; else -> badTag() }
    fun string(cap: Int = DeckPayloadLimits.MAX_TEXT_BYTES): String {
        val length = uint()
        require(length <= cap.toLong() && length <= remaining.toLong()) { "Invalid deck string length" }
        val bytes = ByteArray(length.toInt())
        input.readFully(bytes)
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
    fun count(cap: Int): Int {
        val count = uint()
        require(count <= cap.toLong() && count <= remaining.toLong() &&
            count <= (DeckPayloadLimits.MAX_COLLECTION_ELEMENTS - elements).toLong()) { "Invalid deck collection count" }
        elements += count.toInt()
        return count.toInt()
    }
    fun <T> v(codec: DeckWire<T>): T = codec.get(this)
}

internal fun badTag(): Nothing = throw IllegalArgumentException("Invalid deck field tag")
internal val textWire: DeckWire<String> = wire({ string(it) }, { string() })
internal val idTextWire: DeckWire<String> = wire({ string(it, DeckPayloadLimits.MAX_ID_BYTES) }, { string(DeckPayloadLimits.MAX_ID_BYTES) })
internal fun <T> nullable(codec: DeckWire<T>): DeckWire<T?> = wire({ value ->
    bool(value != null)
    if (value != null) v(codec, value)
}, { if (bool()) v(codec) else null })

/** Every supported element has at least one wire byte. Counts are checked before allocation. */
internal fun <T> list(codec: DeckWire<T>, cap: Int = DeckPayloadLimits.MAX_LIST_ITEMS): DeckWire<SnapshotList<T>> = wire({ values ->
    count(values.size, cap)
    for (value in values) v(codec, value)
}, {
    val count = count(cap)
    val values = ArrayList<T>()
    repeat(count) { values.add(v(codec)) }
    SnapshotList.copyOf(values)
})
internal inline fun <reified T : Enum<T>> enumeration(): DeckWire<T> = wire({ v(idTextWire, it.name) }, { enumValueOf<T>(v(idTextWire)) })
