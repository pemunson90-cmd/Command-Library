package com.architectpsg.commandlibrary.feature.catalog

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.internal.Streams
import com.google.gson.stream.JsonWriter
import java.io.Writer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.CharacterCodingException

/** Separate receipt/v3 policy. Existing acquisition and legacy ruling limits are unchanged. */
internal object BoundedRulingsJson {
    fun bytes(value: String, maximum: Int): Int {
        require(value.length <= maximum)
        return try {
            Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value)).remaining().also { require(it <= maximum) }
        } catch (_: CharacterCodingException) { throw IllegalArgumentException("Invalid ruling Unicode") }
    }
    fun parse(value: String, maximum: Int): JsonObject { bytes(value, maximum); return ScryfallCodec.parse(value, 48) }
    fun encode(value: JsonObject, maximum: Int): String {
        val output = StringBuilder()
        val writer = object : Writer() {
            override fun write(buffer: CharArray, offset: Int, count: Int) {
                require(count <= maximum - output.length); output.append(buffer, offset, count)
            }
            override fun flush() = Unit
            override fun close() = Unit
        }
        Streams.write(value, JsonWriter(writer))
        return output.toString().also { parse(it, maximum) }
    }
    fun fields(value: JsonObject, vararg names: String): JsonObject { require(value.keySet() == names.toSet()); return value }
    fun text(value: JsonElement): String { require(value.isJsonPrimitive && value.asJsonPrimitive.isString); return value.asString }
    fun number(value: JsonElement): Long { require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber); return value.asBigDecimal.longValueExact() }
}
