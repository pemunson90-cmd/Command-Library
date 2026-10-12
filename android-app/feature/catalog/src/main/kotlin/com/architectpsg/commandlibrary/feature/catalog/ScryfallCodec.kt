package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

/** Version 1 payload is the complete validated source object, decoded through this versioned mapper. */
internal object ScryfallCodec {
    const val VERSION = 1
    const val FORMAT = "scryfall-json-array"
    const val PARSER = "scryfall-v1"

    fun parse(raw: String, maxNesting: Int): JsonObject {
        val reader = JsonReader(StringReader(raw))
        reader.strictness = Strictness.STRICT
        val parsed = read(reader, 0, maxNesting)
        require(reader.peek() == JsonToken.END_DOCUMENT && parsed is JsonObject) { "Expected one JSON object" }
        return parsed
    }

    private fun read(reader: JsonReader, depth: Int, limit: Int): JsonElement {
        require(depth <= limit) { "JSON nesting exceeds limit" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val value = JsonObject()
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    require(!value.has(key)) { "Duplicate JSON field: $key" }
                    value.add(key, read(reader, depth + 1, limit))
                }
                reader.endObject()
                value
            }
            JsonToken.BEGIN_ARRAY -> {
                val value = JsonArray()
                reader.beginArray()
                while (reader.hasNext()) value.add(read(reader, depth + 1, limit))
                reader.endArray()
                value
            }
            JsonToken.STRING -> JsonPrimitive(reader.nextString())
            JsonToken.NUMBER -> JsonPrimitive(BigDecimal(reader.nextString()))
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
            else -> error("Unexpected JSON token")
        }
    }

    fun mapped(raw: String, provider: ProviderId, generation: GenerationId, source: ResourceUri?, date: Knowledge<UtcTimestamp>, observed: UtcTimestamp, hash: ContentHash?, notice: SourceNotice?, maxNesting: Int): Pair<CardRecord, PrintingRecord> {
        val obj = parse(raw, maxNesting)
        require(obj.string("object") == "card") { "Expected Scryfall card object" }
        require(obj.string("lang") == "en") { "Only English records are supported" }
        val provenance = Provenance(provider, source, Knowledge.Unknown("Source version not supplied", SnapshotList.empty()), date, observed, Knowledge.Unknown("Effective date not supplied", SnapshotList.empty()), generation, hash, notice)
        val oracle = OracleId(provider, obj.required("oracle_id"))
        val id = CardId(provider, obj.required("id"))
        val language = LanguageTag.ENGLISH
        val faceObjects = obj.objects("card_faces")
        faceObjects.forEach { require(it.string("object") == "card_face") { "Invalid card face object" } }
        val faces = faceObjects.mapIndexed { index, face ->
            CardFace(
                FaceIdentity(oracle, index, face.knowledgeString("oracle_id")),
                face.required("name"), face.knowledgeString("oracle_text"), face.knowledgeString("mana_cost"),
                face.knowledgeTypeLine("type_line"), face.stats(), SnapshotList.empty(),
            )
        }
        val name = obj.required("name")
        val card = CardRecord(
            oracle, name, obj.knowledgeString("oracle_text"), obj.knowledgeString("mana_cost"),
            obj.knowledgeDecimal("cmc"), obj.knowledgeColors("colors"), obj.knowledgeColors("color_identity"),
            obj.knowledgeTypeLine("type_line"), obj.stats(), obj.knowledgeStrings("keywords"),
            obj.knowledgeString("layout"), obj.knowledgeBoolean("reserved"), normalize(name), language,
            SnapshotList.copyOf(faces), SnapshotList.of(ProviderIdentity(provider, "oracle", oracle.raw)), provenance, null,
        )
        val printingRelationships = obj.objects("all_parts").map { part ->
            require(part.string("object") == "related_card") { "Invalid related card object" }
            val kind = when (part.required("component")) {
                "meld_part" -> FaceRelationshipKind.MELD_PART
                "meld_result" -> FaceRelationshipKind.MELD_RESULT
                "combo_piece", "token" -> FaceRelationshipKind.OTHER
                else -> error("Unknown related card component")
            }
            val rawId = part.required("id")
            FaceRelationship(kind, unknown("Oracle mapping not supplied by all_parts"),
                unknown("Face mapping not supplied by all_parts"), Knowledge.Known(CardId(provider, rawId)),
                Knowledge.Known(ProviderIdentity(provider, "printing", rawId)), SnapshotList.empty())
        }
        val faceAssets = faceObjects.mapIndexed { index, face ->
            PrintingFaceAsset(
                PrintingFaceIdentity(id, index, face.knowledgeString("id")), language,
                face.knowledgeString("printed_name"), face.knowledgeString("printed_text"),
                face.knowledgeString("printed_type_line"), face.knowledgeString("flavor_text"),
                face.knowledgeString("artist"), face.knowledgeString("illustration_id").map { ProviderIdentity(provider, "illustration", it) },
                SnapshotList.copyOf(face.images("image_uris")), SnapshotList.empty(),
            )
        }
        val links = buildList {
            for (field in listOf("scryfall_uri", "uri")) obj.string(field)?.let { add(SuppliedLink(field, uri(it), provenance)) }
            for (field in listOf("related_uris", "purchase_uris")) {
                val linksObject = obj.optionalObject(field)
                linksObject?.entrySet()?.forEach { (key, value) ->
                    require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Invalid $field link" }
                    add(SuppliedLink("$field.$key", uri(value.asString), provenance))
                }
            }
        }
        val vendor = buildList {
            for (field in listOf("multiverse_ids", "mtgo_id", "arena_id", "tcgplayer_id", "tcgplayer_etched_id", "cardmarket_id", "illustration_id")) {
                val value = obj.get(field) ?: continue
                if (value.isJsonNull) continue
                if (field == "multiverse_ids") {
                    require(value.isJsonArray) { "Invalid multiverse_ids" }
                    value.asJsonArray.forEach { add(ProviderIdentity(provider, field, it.strictInteger().toString())) }
                } else add(ProviderIdentity(provider, field, if (field == "illustration_id") value.strictString() else value.strictInteger().toString()))
            }
        }
        val printing = PrintingRecord(
            id, Knowledge.Known(oracle), obj.required("set"), obj.knowledgeString("set_name"),
            obj.required("collector_number"), language, obj.knowledgeString("rarity"), obj.knowledgeFinishes("finishes"),
            obj.imageUri(), SnapshotList.copyOf(obj.images("image_uris")), SnapshotList.copyOf(faceAssets),
            SnapshotList.copyOf(printingRelationships), SnapshotList.copyOf(links), SnapshotList.copyOf(vendor), provenance,
        )
        require(faces.isEmpty() || faces.size == faceAssets.size) { "Face asset relation mismatch" }
        return card to printing
    }

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()

    private fun JsonObject.required(key: String): String = string(key)?.takeIf { it.isNotBlank() } ?: error("Missing $key")
    private fun JsonObject.string(key: String): String? {
        val value = get(key) ?: return null
        if (value.isJsonNull) return null
        return value.strictString()
    }
    private fun JsonElement.strictString(): String {
        require(isJsonPrimitive && asJsonPrimitive.isString) { "Expected string" }
        return asString
    }
    private fun JsonElement.strictInteger(): Long {
        require(isJsonPrimitive && asJsonPrimitive.isNumber) { "Expected integer" }
        val decimal = asBigDecimal
        require(decimal >= BigDecimal.ZERO && decimal <= BigDecimal.valueOf(Long.MAX_VALUE)) { "Integer out of range" }
        return decimal.longValueExact()
    }
    private fun JsonObject.knowledgeString(key: String): Knowledge<String> = when {
        !has(key) -> Knowledge.Absent("$key not supplied")
        get(key).isJsonNull -> Knowledge.Absent("$key supplied null")
        else -> Knowledge.Known(get(key).strictString())
    }
    private fun JsonObject.knowledgeBoolean(key: String): Knowledge<Boolean> = when {
        !has(key) -> Knowledge.Absent("$key not supplied")
        get(key).isJsonNull -> Knowledge.Absent("$key supplied null")
        else -> {
            val value = get(key)
            require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "Invalid $key" }
            Knowledge.Known(value.asBoolean)
        }
    }
    private fun JsonObject.knowledgeDecimal(key: String): Knowledge<BigDecimal> = when {
        !has(key) -> Knowledge.Absent("$key not supplied")
        get(key).isJsonNull -> Knowledge.Absent("$key supplied null")
        else -> {
            val value = get(key)
            require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "Invalid $key" }
            Knowledge.Known(value.asBigDecimal.also { require(it.signum() >= 0 && it.scale() <= 16 && it.precision() <= 32) })
        }
    }
    private fun JsonObject.knowledgeStrings(key: String): Knowledge<SnapshotList<String>> = when {
        !has(key) -> Knowledge.Absent("$key not supplied")
        get(key).isJsonNull -> Knowledge.Absent("$key supplied null")
        else -> {
            require(get(key).isJsonArray) { "Invalid $key" }
            Knowledge.Known(SnapshotList.copyOf(getAsJsonArray(key).map { it.strictString() }))
        }
    }
    private fun JsonObject.knowledgeColors(key: String): Knowledge<SnapshotList<Color>> = knowledgeStrings(key).map { values ->
        SnapshotList.copyOf(values.map { value -> when (value) {
            "W" -> Color.WHITE; "U" -> Color.BLUE; "B" -> Color.BLACK; "R" -> Color.RED; "G" -> Color.GREEN
            else -> error("Unknown color")
        } })
    }
    private fun JsonObject.knowledgeFinishes(key: String): Knowledge<SnapshotList<Finish>> = knowledgeStrings(key).map { values ->
        SnapshotList.copyOf(values.map { value -> when (value) {
            "nonfoil" -> Finish.NONFOIL; "foil" -> Finish.FOIL; "etched" -> Finish.ETCHED
            else -> error("Unknown finish")
        } })
    }
    private fun JsonObject.knowledgeTypeLine(key: String): Knowledge<TypeLine> = knowledgeString(key).map { line ->
        val superTypes = setOf("Basic", "Legendary", "Snow", "World", "Ongoing", "Elite", "Host")
        val segments = line.split(" // ").map { it.split(" — ", limit = 2) }
        val mainTokens = segments.flatMap { it[0].split(' ').filter(String::isNotBlank) }
        val subtypes = segments.flatMap { if (it.size == 2) it[1].split(' ').filter(String::isNotBlank) else emptyList() }
        TypeLine(line, SnapshotList.copyOf(mainTokens.filter { it in superTypes }.distinct()),
            SnapshotList.copyOf(mainTokens.filter { it !in superTypes }.distinct()), SnapshotList.copyOf(subtypes.distinct()))
    }
    private fun JsonObject.stats(): CardStats = CardStats(knowledgeString("power"), knowledgeString("toughness"), knowledgeString("loyalty"), knowledgeString("defense"))
    private fun JsonObject.optionalObject(key: String): JsonObject? {
        val value = get(key) ?: return null
        if (value.isJsonNull) return null
        require(value.isJsonObject) { "Invalid $key" }
        return value.asJsonObject
    }
    private fun JsonObject.objects(key: String): List<JsonObject> {
        val value = get(key) ?: return emptyList()
        if (value.isJsonNull) return emptyList()
        require(value.isJsonArray) { "Invalid $key" }
        return value.asJsonArray.map { require(it.isJsonObject) { "Invalid $key member" }; it.asJsonObject }
    }
    private fun JsonObject.images(key: String): List<PrintingImageAsset> {
        val values = optionalObject(key) ?: return emptyList()
        return values.entrySet().map { (variant, value) -> PrintingImageAsset(variant, uri(value.strictString()), Knowledge.Absent("width not supplied"), Knowledge.Absent("height not supplied")) }
    }
    private fun JsonObject.imageUri(): Knowledge<ResourceUri> {
        val values = optionalObject("image_uris") ?: return Knowledge.Absent("image_uris not supplied")
        val normal = values.string("normal") ?: return Knowledge.Absent("normal image not supplied")
        return Knowledge.Known(uri(normal))
    }
    private fun uri(value: String): ResourceUri {
        require(value.startsWith("https://") && value.length <= 4096) { "Invalid source URI" }
        return ResourceUri(value)
    }
    private fun <T, R> Knowledge<T>.map(action: (T) -> R): Knowledge<R> = when (this) {
        is Knowledge.Known -> Knowledge.Known(action(value))
        is Knowledge.Absent -> this
        is Knowledge.Unknown -> this
    }
    private fun <T> unknown(reason: String): Knowledge<T> = Knowledge.Unknown(reason, SnapshotList.empty())
}
