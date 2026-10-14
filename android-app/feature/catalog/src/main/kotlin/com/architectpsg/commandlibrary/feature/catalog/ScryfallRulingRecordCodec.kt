package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.OracleId
import com.architectpsg.commandlibrary.core.model.ProviderId
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.io.IOException
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.CancellationException

/** Literal source fields only: no supplied ruling ID, language, instant or retrieval authority. */
internal data class ScryfallRulingRecord(
    val oracleId: OracleId,
    val source: String,
    val publishedAt: String,
    val comment: String,
)

/** One bounded decoded JSONL record; framing, gzip integrity and activation are separate. */
internal object ScryfallRulingRecordCodec {
    const val MAX_RECORD_BYTES = 128 * 1024
    const val MAX_STRING_BYTES = 64 * 1024
    const val MAX_KEY_BYTES = 256
    const val MAX_NESTING = 16
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val date = Regex("([0-9]{4})-([0-9]{2})-([0-9]{2})")

    fun decode(raw: String): ScryfallRulingRecord {
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
            require(uuid.matches(oracle)) { "Invalid ruling Oracle identity" }
            val source = string(root, "source")
            require(source == "wotc" || source == "scryfall") { "Unsupported ruling source" }
            val published = string(root, "published_at")
            requireDate(published)
            val comment = string(root, "comment")
            require(comment.isNotBlank()) { "Invalid ruling comment" }
            return ScryfallRulingRecord(OracleId(ProviderId("scryfall"), oracle), source, published, comment)
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
    /** Source calendar date, proleptic Gregorian years0001–9999; never converted to an instant. */
    private fun requireDate(raw: String) {
        val parts = requireNotNull(date.matchEntire(raw)) { "Invalid ruling publication date" }.groupValues
        val year = parts[1].toInt(); val month = parts[2].toInt(); val day = parts[3].toInt()
        require(year in 1..9999 && month in 1..12) { "Invalid ruling publication date" }
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val days = when (month) { 2 -> if (leap) 29 else 28; 4, 6, 9, 11 -> 30; else -> 31 }
        require(day in 1..days) { "Invalid ruling publication date" }
    }
}
