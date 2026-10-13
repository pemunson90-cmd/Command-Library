package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Catalog generation notice_payload v1 also records source knowledge states for exact rehydration. */
internal object SourceMetadataCodec {
    fun encode(source: CatalogImportSource): String {
        val root = JsonObject()
        root.addProperty("version", 1)
        root.add("sourceDate", date(source.sourceDate))
        root.add("notice", source.notice.let { knowledge(it) { notice ->
            JsonObject().apply {
                addProperty("provider", notice.provider.value)
                addProperty("title", notice.title)
                add("license", knowledge(notice.license) { string -> JsonObject().apply { addProperty("text", string) } })
                addProperty("attribution", notice.attribution)
                notice.uri?.let { addProperty("uri", it.value) }
            }
        } })
        return root.toString().also { require(it.toByteArray(Charsets.UTF_8).size <= 128 * 1024) }
    }
    fun sourceDate(payload: String?): Knowledge<UtcTimestamp> = if (payload == null) Knowledge.Unknown("Source date metadata unavailable", SnapshotList.empty())
        else readKnowledge(readPayload(payload).getAsJsonObject("sourceDate")) { timestamp(it) }
    fun notice(payload: String?): SourceNotice? {
        if (payload == null) return null
        val root = readPayload(payload)
        val state = readKnowledge(root.getAsJsonObject("notice")) { item ->
            SourceNotice(ProviderId(item.get("provider").asString), item.get("title").asString,
                readKnowledge(item.getAsJsonObject("license")) { it.get("text").asString },
                item.get("attribution").asString, item.get("uri")?.asString?.let(::ResourceUri))
        }
        return (state as? Knowledge.Known)?.value
    }
    private fun readPayload(payload: String): JsonObject {
        require(payload.toByteArray(Charsets.UTF_8).size <= 128 * 1024) { "Source metadata exceeds limit" }
        val root = ScryfallCodec.parse(payload, 16)
        val version = requireNotNull(root.get("version")) { "Source metadata version missing" }
        require(version.isJsonPrimitive && version.asJsonPrimitive.isNumber && version.asBigDecimal.intValueExact() == 1) {
            "Unsupported source metadata version"
        }
        return root
    }
    private fun date(value: Knowledge<UtcTimestamp>): JsonObject = knowledge(value) { timestamp ->
        JsonObject().apply { addProperty("seconds", timestamp.epochSeconds); addProperty("nanos", timestamp.nanoAdjustment) }
    }
    private fun timestamp(value: JsonObject): UtcTimestamp = UtcTimestamp(value.get("seconds").asLong, value.get("nanos").asInt)
    private fun <T> knowledge(value: Knowledge<T>, known: (T) -> JsonObject): JsonObject = JsonObject().apply {
        when (value) {
            is Knowledge.Known -> { addProperty("state", "known"); add("value", known(value.value)) }
            is Knowledge.Absent -> { addProperty("state", "absent"); addProperty("reason", value.reason) }
            is Knowledge.Unknown -> {
                addProperty("state", "unknown"); addProperty("reason", value.reason)
                add("evidence", JsonArray().apply { value.evidence.forEach { evidence ->
                    add(JsonObject().apply {
                        addProperty("description", evidence.description)
                        evidence.source?.let { addProperty("source", it.value) }
                        evidence.recordedAt?.let { add("recordedAt", JsonObject().apply {
                            addProperty("seconds", it.epochSeconds); addProperty("nanos", it.nanoAdjustment)
                        }) }
                    })
                } })
            }
        }
    }
    private fun <T> readKnowledge(value: JsonObject, known: (JsonObject) -> T): Knowledge<T> = when (value.get("state").asString) {
        "known" -> Knowledge.Known(known(value.getAsJsonObject("value")))
        "absent" -> Knowledge.Absent(value.get("reason").asString)
        "unknown" -> Knowledge.Unknown(value.get("reason").asString, SnapshotList.copyOf(value.getAsJsonArray("evidence").map { item ->
            val obj = item.asJsonObject
            Evidence(obj.get("description").asString, obj.get("source")?.asString?.let(::ResourceUri),
                obj.get("recordedAt")?.asJsonObject?.let(::timestamp))
        }))
        else -> error("Unknown source knowledge state")
    }
}
