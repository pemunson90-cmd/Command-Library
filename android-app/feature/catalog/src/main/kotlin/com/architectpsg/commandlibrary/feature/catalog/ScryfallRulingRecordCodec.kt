package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.OracleId
import com.architectpsg.commandlibrary.core.model.ProviderId
import com.architectpsg.commandlibrary.core.model.RulingContent
import com.architectpsg.commandlibrary.core.model.RulingPublicationDay
import com.architectpsg.commandlibrary.core.model.RulingPublisher
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.io.IOException
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.CancellationException

/** One bounded decoded JSONL record; framing, gzip integrity and activation are separate. */
internal object ScryfallRulingRecordCodec {
    const val MAX_RECORD_BYTES = 128 * 1024
    const val MAX_STRING_BYTES = 64 * 1024
    const val MAX_KEY_BYTES = 256
    const val MAX_NESTING = 16
    fun decode(raw: String): RulingContent {
        require(raw.length <= MAX_RECORD_BYTES) { "Ruling record exceeds byte limit" }
        try {
            // Reject unpaired UTF-16 instead of silently counting replacement UTF-8 bytes.
            val bytes = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(raw)).remaining()
            require(bytes <= MAX_RECORD_BYTES) { "Ruling record exceeds byte limit" }
            val root = ScryfallCodec.parse(raw, MAX_NESTING)
            checkStrings(root)
            require(string(root, "object") == "ruling") { "Expected ruling object" }
            val oracle = string(root, "oracle_id")
            val source = string(root, "source")
            val publisher = RulingPublisher.entries.firstOrNull { it.upstreamValue == source }
                ?: throw IllegalArgumentException("Unsupported ruling source")
            val published = string(root, "published_at")
            val comment = string(root, "comment")
            return RulingContent(OracleId(ProviderId("scryfall"), oracle), publisher, RulingPublicationDay(published), comment)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: JsonParseException) { throw IllegalArgumentException("Malformed ruling record", failure) }
        catch (failure: IOException) { throw IllegalArgumentException("Malformed ruling record", failure) }
        catch (failure: IllegalStateException) { throw IllegalArgumentException("Malformed ruling record", failure) }
    }

    private fun string(root: JsonObject, name: String): String {
        val value = root.get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Missing or invalid ruling field: $name" }
        return value.asString
    }
    private fun checkStrings(value: JsonElement) {
        when {
            value.isJsonObject -> value.asJsonObject.entrySet().forEach { (key, child) ->
                require(key.length <= MAX_KEY_BYTES && key.toByteArray(Charsets.UTF_8).size <= MAX_KEY_BYTES) { "Ruling key exceeds byte limit" }
                checkStrings(child)
            }
            value.isJsonArray -> value.asJsonArray.forEach(::checkStrings)
            value.isJsonPrimitive && value.asJsonPrimitive.isString -> {
                val text = value.asString
                require(text.length <= MAX_STRING_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_STRING_BYTES) { "Ruling string exceeds byte limit" }
            }
        }
    }
}
