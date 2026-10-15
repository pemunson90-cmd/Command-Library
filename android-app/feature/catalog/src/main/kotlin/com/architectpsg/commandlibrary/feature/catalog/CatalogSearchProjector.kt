package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.common.DecimalComparisonKeys
import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal enum class CatalogProjectionTable(val sqlName: String) {
    CARD("catalog_search_card_fact"), FACE("catalog_search_face_fact"),
    PRINTING("catalog_search_printing_fact"), LEGALITY("catalog_search_legality_fact"),
    TEXT("catalog_search_text_fact"), TEXT_FTS("catalog_search_text_fts"),
}
internal data class CatalogProjectionRow(val table: CatalogProjectionTable, val data: RowData)
internal data class CatalogSearchProjection(
    val generation: GenerationId,
    val provider: ProviderId,
    val oracleId: OracleId?,
    val printingId: CardId,
    val rows: SnapshotList<CatalogProjectionRow>,
    val derivedStringBytes: Long,
    val estimatedStatements: Int,
)
internal class SearchProjectionBudgetExceeded(message: String) : IllegalArgumentException(message)

/** Pure derived v1 facts, not SQL/progress/readiness or source authority.
 * The owning writer MUST bind returned IDs to the original stored row before writing any facts.
 * Existing strict parsers are bounded but non-cancellable; no latency/performance claim is made.
 */
internal object CatalogSearchProjector {
    private const val VERSION = 1
    private const val RAW_LIMIT = 8 * 1024 * 1024
    private const val ROW_LIMIT = 1024
    private const val STRING_LIMIT = 8L * 1024 * 1024
    private const val STATEMENT_LIMIT = 2048

    fun project(raw: String, provenance: Provenance, cancel: CancelToken): CatalogSearchProjection {
        cancel.throwIfCancelled()
        if (raw.length > RAW_LIMIT) throw SearchProjectionBudgetExceeded("Projection source character budget exceeded")
        if (raw.toByteArray(Charsets.UTF_8).size > RAW_LIMIT) throw SearchProjectionBudgetExceeded("Projection source byte budget exceeded")
        val generation = requireNotNull(provenance.generation) { "Projection requires the actual generation" }
        for (id in listOf(generation.value, provenance.provider.value))
            require(id.length <= 128 && '\u0000' !in id && id.toByteArray(Charsets.UTF_8).size <= 128) { "Projection identity exceeds budget" }
        val obj = ScryfallCodec.parse(raw, 32)
        cancel.throwIfCancelled()
        val wholeCard = obj.get("oracle_id")?.isJsonNull == false
        val faces = obj.get("card_faces")?.takeIf { it.isJsonArray }?.asJsonArray
        val keywords = obj.get("keywords")?.takeIf { it.isJsonArray }?.asJsonArray
        val legalities = obj.get("legalities")?.takeIf { it.isJsonObject }?.asJsonObject
        val minimumRows = 1L + (legalities?.size() ?: 0) + if (wholeCard) {
            3L + 3L * (faces?.size() ?: 0) + 2L * (keywords?.size() ?: 0)
        } else 0L
        if (minimumRows > ROW_LIMIT) throw SearchProjectionBudgetExceeded("Projection source fan-out budget exceeded")
        cancel.throwIfCancelled()
        val (card, printing) = ScryfallCodec.mappedSource(raw, provenance.provider, generation, provenance.source,
            provenance.sourceDate, provenance.fetchedAt, provenance.checksum, provenance.notice, 32)
        cancel.throwIfCancelled()
        require(printing.provenance.generation == generation && printing.id.source == provenance.provider)
        require(printing.id.raw == obj.get("id").asString && printing.language == LanguageTag.ENGLISH)
        if (card != null) require(card.provenance.generation == generation && card.oracleId.source == provenance.provider &&
            card.oracleId.raw == obj.get("oracle_id").asString && card.language == LanguageTag.ENGLISH)
        val output = ArrayList<CatalogProjectionRow>()
        var stringBytes = 0L
        fun emit(table: CatalogProjectionTable, vararg values: Pair<String, Any?>) {
            cancel.throwIfCancelled()
            val columns = values.map { (name, value) -> RowColumn(name, when (value) {
                null -> SqlValue.Null
                is String -> SqlValue.Text(value)
                is Boolean -> SqlValue.Integer(if (value) 1 else 0)
                is Int -> SqlValue.Integer(value.toLong())
                else -> error("Unsupported projection scalar")
            }) }
            val addedBytes = columns.sumOf { (it.value as? SqlValue.Text)?.value?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L }
            val count = output.size + 1
            if (count > ROW_LIMIT || count * 2 > STATEMENT_LIMIT || stringBytes + addedBytes > STRING_LIMIT)
                throw SearchProjectionBudgetExceeded("Projection derived row/string/statement budget exceeded")
            stringBytes += addedBytes
            output.add(CatalogProjectionRow(table, RowData(SnapshotList.copyOf(columns))))
        }
        val common = arrayOf("generation_id" to generation.value, "projection_version" to VERSION,
            "provider" to provenance.provider.value)
        fun numeric(value: Knowledge<String>): String? = value.held()?.let(DecimalComparisonKeys::sourceKey)
        fun colors(value: Iterable<Color>?): Int? = value?.fold(0) { mask, color ->
            cancel.throwIfCancelled(); mask or when (color) { Color.WHITE -> 1; Color.BLUE -> 2; Color.BLACK -> 4; Color.RED -> 8; Color.GREEN -> 16 }
        }
        if (card != null) {
            val identity = common + arrayOf("oracle_id" to card.oracleId.raw, "language" to card.language.value)
            emit(CatalogProjectionTable.CARD, *identity,
                "mana_value_key" to card.manaValue.held()?.let { DecimalComparisonKeys.sourceKey(it.toPlainString()) },
                "power_key" to numeric(card.stats.power), "toughness_key" to numeric(card.stats.toughness),
                "loyalty_key" to numeric(card.stats.loyalty), "defense_key" to numeric(card.stats.defense),
                "colors_mask" to colors(card.colors.held()), "identity_mask" to colors(card.colorIdentity.held()),
                "layout" to card.layout.held(), "reserved" to card.reserved.held(),
                "oracle_text_known" to (card.originalText is Knowledge.Known), "type_known" to (card.types is Knowledge.Known),
                "keywords_known" to (card.keywords is Knowledge.Known), "face_count" to card.faces.size)
            fun text(scope: String, index: Int, field: String, original: String) {
                cancel.throwIfCancelled()
                val value = ScryfallCodec.normalize(original)
                val location = identity + arrayOf("scope_kind" to scope, "scope_index" to index, "field" to field)
                emit(CatalogProjectionTable.TEXT, *location, "normalized_value" to value)
                emit(CatalogProjectionTable.TEXT_FTS, *location, "value" to value)
            }
            text("CANONICAL", 0, "NAME", card.name)
            card.originalText.held()?.let { text("CANONICAL", 0, "ORACLE_TEXT", it) }
            card.types.held()?.let { text("CANONICAL", 0, "TYPE", it.original) }
            card.keywords.held()?.forEachIndexed { index, keyword ->
                cancel.throwIfCancelled(); text("KEYWORD", index, "KEYWORD", keyword)
            }
            card.faces.forEachIndexed { index, face ->
                cancel.throwIfCancelled()
                require(face.identity.oracleId == card.oracleId && face.identity.index == index)
                val rawFace = requireNotNull(faces)[index].asJsonObject
                emit(CatalogProjectionTable.FACE, *identity, "face_index" to index,
                    "power_key" to numeric(face.stats.power), "toughness_key" to numeric(face.stats.toughness),
                    "loyalty_key" to numeric(face.stats.loyalty), "defense_key" to numeric(face.stats.defense),
                    "colors_mask" to rawColors(rawFace.get("colors"), cancel),
                    "oracle_text_known" to (face.text is Knowledge.Known), "type_known" to (face.types is Knowledge.Known))
                text("FACE", index, "NAME", face.name)
                face.text.held()?.let { text("FACE", index, "ORACLE_TEXT", it) }
                face.types.held()?.let { text("FACE", index, "TYPE", it.original) }
            }
        }
        val printIdentity = common + arrayOf("printing_id" to printing.id.raw)
        val finishMask = printing.finishes.held()?.fold(0) { mask, finish ->
            cancel.throwIfCancelled(); mask or when (finish) { Finish.NONFOIL -> 1; Finish.FOIL -> 2; Finish.ETCHED -> 4 }
        }
        val changer = obj.get("game_changer")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
        emit(CatalogProjectionTable.PRINTING, *printIdentity, "set_code" to printing.setCode, "language" to printing.language.value,
            "rarity" to printing.rarity.held(), "finish_mask" to finishMask, "gamechanger" to changer,
            "legalities_known" to (legalities != null))
        legalities?.entrySet()?.forEach { (key, value) ->
            cancel.throwIfCancelled()
            if (key.isNotBlank()) {
                val format = FormatId(provenance.provider, key)
                val status = when (value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString) {
                    "legal" -> LegalityStatus.LEGAL; "banned" -> LegalityStatus.BANNED
                    "restricted" -> LegalityStatus.RESTRICTED; "not_legal" -> LegalityStatus.NOT_LEGAL
                    else -> LegalityStatus.UNKNOWN
                }
                require(format.source == printing.id.source)
                emit(CatalogProjectionTable.LEGALITY, *printIdentity, "format" to format.raw, "status" to status.name)
            }
        }
        cancel.throwIfCancelled()
        return CatalogSearchProjection(generation, provenance.provider, card?.oracleId, printing.id,
            SnapshotList.copyOf(output), stringBytes, output.size * 2)
    }

    private fun rawColors(value: JsonElement?, cancel: CancelToken): Int? {
        if (value?.isJsonArray != true) return null
        var mask = 0
        for (color in value.asJsonArray) {
            cancel.throwIfCancelled()
            if (!color.isJsonPrimitive || !color.asJsonPrimitive.isString) return null
            mask = mask or when (color.asString) { "W" -> 1; "U" -> 2; "B" -> 4; "R" -> 8; "G" -> 16; else -> return null }
        }
        return mask
    }
}
private fun <T> Knowledge<T>.held(): T? = (this as? Knowledge.Known)?.value
