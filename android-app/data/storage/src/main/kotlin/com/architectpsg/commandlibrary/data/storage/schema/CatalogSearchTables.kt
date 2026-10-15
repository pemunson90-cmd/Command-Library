package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Fts5
import androidx.room3.FtsOptions
import androidx.room3.Index

/** Derived, versioned search preparation only. Original payloads/pointers remain authoritative.
 * NULL means unknown comparison evidence, never zero/false. No population/readiness is implied.
 */
@Entity(
    tableName = "catalog_search_build",
    primaryKeys = ["generation_id", "projection_version"],
    foreignKeys = [
        ForeignKey(entity = CatalogGenerationEntity::class, parentColumns = ["generation_id"],
            childColumns = ["generation_id"], onDelete = ForeignKey.CASCADE),
    ],
)
internal data class CatalogSearchBuildEntity(
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "projection_version") val projectionVersion: Int,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "phase") val phase: String,
    @ColumnInfo(name = "cursor_provider") val cursorProvider: String?,
    @ColumnInfo(name = "cursor_id") val cursorId: String?,
    @ColumnInfo(name = "cursor_language") val cursorLanguage: String?,
    @ColumnInfo(name = "card_count") val cardCount: Long,
    @ColumnInfo(name = "face_count") val faceCount: Long,
    @ColumnInfo(name = "printing_count") val printingCount: Long,
    @ColumnInfo(name = "text_count") val textCount: Long,
    @ColumnInfo(name = "legality_count") val legalityCount: Long,
    @ColumnInfo(name = "diagnostic") val diagnostic: String?,
)

@Entity(
    tableName = "catalog_search_card_fact",
    primaryKeys = ["generation_id", "projection_version", "provider", "oracle_id", "language"],
    foreignKeys = [
        ForeignKey(entity = CatalogSearchBuildEntity::class, parentColumns = ["generation_id", "projection_version"],
            childColumns = ["generation_id", "projection_version"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = OracleCardEntity::class, parentColumns = ["generation_id", "provider", "oracle_id", "language"],
            childColumns = ["generation_id", "provider", "oracle_id", "language"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index(value = ["generation_id", "provider", "oracle_id", "language"], name = "index_catalog_search_card_fact_source"),
        Index(value = ["generation_id", "projection_version", "mana_value_key", "provider", "oracle_id", "language"], name = "index_catalog_search_card_fact_mana_value_key"),
        Index(value = ["generation_id", "projection_version", "power_key", "provider", "oracle_id", "language"], name = "index_catalog_search_card_fact_power_key"),
        Index(value = ["generation_id", "projection_version", "toughness_key", "provider", "oracle_id", "language"], name = "index_catalog_search_card_fact_toughness_key"),
        Index(value = ["generation_id", "projection_version", "loyalty_key", "provider", "oracle_id", "language"], name = "index_catalog_search_card_fact_loyalty_key"),
        Index(value = ["generation_id", "projection_version", "defense_key", "provider", "oracle_id", "language"], name = "index_catalog_search_card_fact_defense_key"),
        Index(value = ["generation_id", "projection_version", "colors_mask", "provider", "oracle_id", "language"], name = "index_catalog_search_card_fact_colors_mask"),
        Index(value = ["generation_id", "projection_version", "identity_mask", "provider", "oracle_id", "language"], name = "index_catalog_search_card_fact_identity_mask"),
        Index(value = ["generation_id", "projection_version", "layout", "provider", "oracle_id", "language"], name = "index_catalog_search_card_fact_layout"),
        Index(value = ["generation_id", "projection_version", "reserved", "provider", "oracle_id", "language"], name = "index_catalog_search_card_fact_reserved"),
    ],
)
internal data class CatalogSearchCardFactEntity(
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "projection_version") val projectionVersion: Int,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "oracle_id") val oracleId: String,
    @ColumnInfo(name = "language") val language: String,
    @ColumnInfo(name = "mana_value_key") val manaValueKey: String?,
    @ColumnInfo(name = "power_key") val powerKey: String?,
    @ColumnInfo(name = "toughness_key") val toughnessKey: String?,
    @ColumnInfo(name = "loyalty_key") val loyaltyKey: String?,
    @ColumnInfo(name = "defense_key") val defenseKey: String?,
    @ColumnInfo(name = "colors_mask") val colorsMask: Int?,
    @ColumnInfo(name = "identity_mask") val identityMask: Int?,
    @ColumnInfo(name = "layout") val layout: String?,
    @ColumnInfo(name = "reserved") val reserved: Int?,
    @ColumnInfo(name = "oracle_text_known") val oracleTextKnown: Int,
    @ColumnInfo(name = "type_known") val typeKnown: Int,
    @ColumnInfo(name = "keywords_known") val keywordsKnown: Int,
    @ColumnInfo(name = "face_count") val faceCount: Int,
)

@Entity(
    tableName = "catalog_search_face_fact",
    primaryKeys = ["generation_id", "projection_version", "provider", "oracle_id", "language", "face_index"],
    foreignKeys = [
        ForeignKey(entity = CatalogSearchCardFactEntity::class, parentColumns = ["generation_id", "projection_version", "provider", "oracle_id", "language"],
            childColumns = ["generation_id", "projection_version", "provider", "oracle_id", "language"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CardFaceEntity::class, parentColumns = ["generation_id", "provider", "oracle_id", "language", "face_index"],
            childColumns = ["generation_id", "provider", "oracle_id", "language", "face_index"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index(value = ["generation_id", "provider", "oracle_id", "language", "face_index"], name = "index_catalog_search_face_fact_source"),
        Index(value = ["generation_id", "projection_version", "power_key", "provider", "oracle_id", "language", "face_index"], name = "index_catalog_search_face_fact_power_key"),
        Index(value = ["generation_id", "projection_version", "toughness_key", "provider", "oracle_id", "language", "face_index"], name = "index_catalog_search_face_fact_toughness_key"),
        Index(value = ["generation_id", "projection_version", "loyalty_key", "provider", "oracle_id", "language", "face_index"], name = "index_catalog_search_face_fact_loyalty_key"),
        Index(value = ["generation_id", "projection_version", "defense_key", "provider", "oracle_id", "language", "face_index"], name = "index_catalog_search_face_fact_defense_key"),
        Index(value = ["generation_id", "projection_version", "colors_mask", "provider", "oracle_id", "language", "face_index"], name = "index_catalog_search_face_fact_colors_mask"),
    ],
)
internal data class CatalogSearchFaceFactEntity(
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "projection_version") val projectionVersion: Int,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "oracle_id") val oracleId: String,
    @ColumnInfo(name = "language") val language: String,
    @ColumnInfo(name = "face_index") val faceIndex: Int,
    @ColumnInfo(name = "power_key") val powerKey: String?,
    @ColumnInfo(name = "toughness_key") val toughnessKey: String?,
    @ColumnInfo(name = "loyalty_key") val loyaltyKey: String?,
    @ColumnInfo(name = "defense_key") val defenseKey: String?,
    @ColumnInfo(name = "colors_mask") val colorsMask: Int?,
    @ColumnInfo(name = "oracle_text_known") val oracleTextKnown: Int,
    @ColumnInfo(name = "type_known") val typeKnown: Int,
)

@Entity(
    tableName = "catalog_search_printing_fact",
    primaryKeys = ["generation_id", "projection_version", "provider", "printing_id"],
    foreignKeys = [
        ForeignKey(entity = CatalogSearchBuildEntity::class, parentColumns = ["generation_id", "projection_version"],
            childColumns = ["generation_id", "projection_version"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PrintingEntity::class, parentColumns = ["generation_id", "provider", "printing_id"],
            childColumns = ["generation_id", "provider", "printing_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index(value = ["generation_id", "provider", "printing_id"], name = "index_catalog_search_printing_fact_source"),
        Index(value = ["generation_id", "projection_version", "set_code", "provider", "printing_id"], name = "index_catalog_search_printing_fact_set_code"),
        Index(value = ["generation_id", "projection_version", "language", "provider", "printing_id"], name = "index_catalog_search_printing_fact_language"),
        Index(value = ["generation_id", "projection_version", "rarity", "provider", "printing_id"], name = "index_catalog_search_printing_fact_rarity"),
        Index(value = ["generation_id", "projection_version", "finish_mask", "provider", "printing_id"], name = "index_catalog_search_printing_fact_finish_mask"),
        Index(value = ["generation_id", "projection_version", "gamechanger", "provider", "printing_id"], name = "index_catalog_search_printing_fact_gamechanger"),
    ],
)
internal data class CatalogSearchPrintingFactEntity(
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "projection_version") val projectionVersion: Int,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "printing_id") val printingId: String,
    @ColumnInfo(name = "set_code") val setCode: String,
    @ColumnInfo(name = "language") val language: String,
    @ColumnInfo(name = "rarity") val rarity: String?,
    @ColumnInfo(name = "finish_mask") val finishMask: Int?,
    @ColumnInfo(name = "gamechanger") val gamechanger: Int?,
    @ColumnInfo(name = "legalities_known") val legalitiesKnown: Int,
)

@Entity(
    tableName = "catalog_search_legality_fact",
    primaryKeys = ["generation_id", "projection_version", "provider", "printing_id", "format"],
    foreignKeys = [
        ForeignKey(entity = CatalogSearchPrintingFactEntity::class, parentColumns = ["generation_id", "projection_version", "provider", "printing_id"],
            childColumns = ["generation_id", "projection_version", "provider", "printing_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index(value = ["generation_id", "projection_version", "format", "status", "provider", "printing_id"], name = "index_catalog_search_legality_fact_format_status"),
    ],
)
internal data class CatalogSearchLegalityFactEntity(
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "projection_version") val projectionVersion: Int,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "printing_id") val printingId: String,
    @ColumnInfo(name = "format") val format: String,
    @ColumnInfo(name = "status") val status: String,
)

@Entity(
    tableName = "catalog_search_text_fact",
    primaryKeys = ["generation_id", "projection_version", "provider", "oracle_id", "language", "scope_kind", "scope_index", "field"],
    foreignKeys = [
        ForeignKey(entity = CatalogSearchCardFactEntity::class, parentColumns = ["generation_id", "projection_version", "provider", "oracle_id", "language"],
            childColumns = ["generation_id", "projection_version", "provider", "oracle_id", "language"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index(value = ["generation_id", "projection_version", "field", "normalized_value", "provider", "oracle_id", "language", "scope_kind", "scope_index"], name = "index_catalog_search_text_fact_exact"),
    ],
)
internal data class CatalogSearchTextFactEntity(
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "projection_version") val projectionVersion: Int,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "oracle_id") val oracleId: String,
    @ColumnInfo(name = "language") val language: String,
    @ColumnInfo(name = "scope_kind") val scopeKind: String,
    @ColumnInfo(name = "scope_index") val scopeIndex: Int,
    @ColumnInfo(name = "field") val field: String,
    @ColumnInfo(name = "normalized_value") val normalizedValue: String,
)

/** One mirrored row per text fact. FTS has no FKs: owning writer must clean and verify mirrors. */
@Fts5(tokenizer = FtsOptions.TOKENIZER_UNICODE61, tokenizerArgs = ["remove_diacritics", "2"],
    notIndexed = ["generation_id", "projection_version", "provider", "oracle_id", "language", "scope_kind", "scope_index", "field"])
@Entity(tableName = "catalog_search_text_fts")
internal data class CatalogSearchTextFtsEntity(
    @ColumnInfo(name = "value") val value: String,
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "projection_version") val projectionVersion: Int,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "oracle_id") val oracleId: String,
    @ColumnInfo(name = "language") val language: String,
    @ColumnInfo(name = "scope_kind") val scopeKind: String,
    @ColumnInfo(name = "scope_index") val scopeIndex: Int,
    @ColumnInfo(name = "field") val field: String,
)
