package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.ResourceUri
import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CancellationException

/** Structural source claims only. A candidate neither authenticates retrieval nor authorizes wire dispatch. */
internal data class ScryfallRulingsCandidate(
    val bulkId: EntityId,
    val metadataUri: ResourceUri,
    val downloadUri: ResourceUri,
    val updatedAt: UtcTimestamp,
    val compressedBytes: Long,
    val contentType: String?,
    val contentEncoding: String?,
)

/** Future discovery must fetch a fixed official endpoint before invoking this decoder. No URL input is accepted. */
internal object ScryfallRulingsMetadataCodec {
    const val SINGLE_ENDPOINT = "https://api.scryfall.com/bulk-data/rulings"
    const val LIST_ENDPOINT = "https://api.scryfall.com/bulk-data"
    private const val MAX_METADATA_BYTES = 1024 * 1024
    private const val MAX_STRING_BYTES = 4096
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val downloadPath = Regex("/rulings/[A-Za-z0-9][A-Za-z0-9._-]*\\.jsonl\\.gz")
    private val timestamp = Regex("([0-9]{4})-([0-9]{2})-([0-9]{2})T([0-9]{2}):([0-9]{2}):([0-9]{2})(?:\\.([0-9]{1,9}))?(Z|[+-][0-9]{2}:[0-9]{2})")
    private val jsonTypes = setOf("application/json", "application/jsonl", "application/x-ndjson", "application/jsonlines", "application/gzip", "application/x-gzip")

    fun decode(raw: String, compressedCeiling: Long = 2_147_483_648L): ScryfallRulingsCandidate {
        require(compressedCeiling > 0) { "Compressed-byte ceiling must be positive" }
        require(raw.length <= MAX_METADATA_BYTES && raw.toByteArray(Charsets.UTF_8).size <= MAX_METADATA_BYTES) { "Metadata exceeds byte limit" }
        try {
            val root = ScryfallCodec.parse(raw, 16)
            checkStrings(root)
            val selected = when (string(root, "object")) {
                "bulk_data" -> root
                "list" -> {
                    val data = root.get("data")
                    require(data != null && data.isJsonArray) { "Missing bulk data array" }
                    val candidates = data.asJsonArray.map { item ->
                        require(item.isJsonObject) { "Invalid bulk data entry" }
                        item.asJsonObject
                    }.filter { string(it, "type") == "rulings" }
                    require(candidates.size == 1) { "Expected exactly one rulings entry" }
                    candidates.single()
                }
                else -> throw IllegalArgumentException("Unexpected discovery object")
            }
            require(string(selected, "object") == "bulk_data" && string(selected, "type") == "rulings") { "Not rulings bulk metadata" }
            val id = string(selected, "id")
            require(uuid.matches(id)) { "Invalid bulk identity" }
            val metadata = string(selected, "uri")
            require(officialUri(metadata, "api.scryfall.com").rawPath == "/bulk-data/$id") { "Metadata URI disagrees with bulk identity" }
            val download = string(selected, "jsonl_download_uri")
            require(downloadPath.matches(officialUri(download, "data.scryfall.io").rawPath)) { "Unsupported rulings file path" }
            val size = selected.get("compressed_size")
            require(size != null && size.isJsonPrimitive && size.asJsonPrimitive.isNumber) { "Missing compressed byte count" }
            val bytes = size.asBigDecimal.longValueExact()
            require(bytes in 1..compressedCeiling) { "Compressed byte count outside policy" }
            val type = optionalString(selected, "content_type")
            val encoding = optionalString(selected, "content_encoding")
            require(type == null || type in jsonTypes) { "Unsupported rulings content type" }
            require(encoding == null || encoding == "gzip") { "Unsupported rulings content encoding" }
            return ScryfallRulingsCandidate(EntityId(id), ResourceUri(metadata), ResourceUri(download),
                publicationDate(string(selected, "updated_at")), bytes, type, encoding)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: JsonParseException) { throw IllegalArgumentException("Malformed discovery metadata", failure) }
        catch (failure: IOException) { throw IllegalArgumentException("Malformed discovery metadata", failure) }
        catch (failure: IllegalStateException) { throw IllegalArgumentException("Malformed discovery metadata", failure) }
        catch (failure: ArithmeticException) { throw IllegalArgumentException("Nonintegral or oversized metadata number", failure) }
    }

    private fun string(obj: JsonObject, field: String): String {
        val value = obj.get(field)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Missing or invalid metadata field: $field" }
        return value.asString
    }
    private fun optionalString(obj: JsonObject, field: String): String? =
        if (!obj.has(field) || obj.get(field).isJsonNull) null else string(obj, field)

    private fun checkStrings(value: JsonElement) {
        when {
            value.isJsonObject -> value.asJsonObject.entrySet().forEach { (key, child) ->
                require(key.length <= 256 && key.toByteArray(Charsets.UTF_8).size <= 256) { "Metadata key exceeds limit" }
                checkStrings(child)
            }
            value.isJsonArray -> value.asJsonArray.forEach(::checkStrings)
            value.isJsonPrimitive && value.asJsonPrimitive.isString -> {
                val text = value.asString
                require(text.length <= MAX_STRING_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_STRING_BYTES) { "Metadata string exceeds limit" }
            }
        }
    }
    private fun officialUri(raw: String, host: String): URI {
        require(raw.length <= 2048 && raw.all { it.code in 33..126 } && '%' !in raw) { "Ambiguous source URI" }
        val uri = try { URI(raw) } catch (_: URISyntaxException) { throw IllegalArgumentException("Malformed source URI") }
        require(uri.scheme == "https" && uri.host == host && uri.rawUserInfo == null && uri.rawFragment == null && uri.rawQuery == null) { "Untrusted source URI" }
        require(uri.rawAuthority == host || uri.rawAuthority == "$host:443") { "Untrusted source authority" }
        return uri
    }
    private fun publicationDate(raw: String): UtcTimestamp {
        val match = requireNotNull(timestamp.matchEntire(raw)) { "Invalid source timestamp" }
        val parts = match.groupValues
        val year = parts[1].toInt(); val month = parts[2].toInt(); val day = parts[3].toInt()
        val hour = parts[4].toInt(); val minute = parts[5].toInt(); val second = parts[6].toInt()
        require(year in 1..9999 && month in 1..12 && hour in 0..23 && minute in 0..59 && second in 0..59) { "Invalid source date components" }
        val zone = parts[8]
        require(zone != "-00:00") { "Unknown timestamp offset" }
        val offset = if (zone == "Z") 0 else {
            val hours = zone.substring(1, 3).toInt(); val minutes = zone.substring(4, 6).toInt()
            require(hours in 0..18 && minutes in 0..59 && (hours != 18 || minutes == 0)) { "Invalid timestamp offset" }
            (hours * 3600 + minutes * 60) * if (zone[0] == '-') -1 else 1
        }
        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply {
            gregorianChange = Date(Long.MIN_VALUE)
            isLenient = false
            clear()
            set(year, month - 1, day, hour, minute, second)
        }
        val nanos = parts[7].let { if (it.isEmpty()) 0 else it.padEnd(9, '0').toInt() }
        return UtcTimestamp(calendar.timeInMillis / 1000 - offset, nanos)
    }
}
