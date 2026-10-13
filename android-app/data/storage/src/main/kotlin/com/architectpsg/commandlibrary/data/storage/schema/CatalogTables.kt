package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Fts5
import androidx.room3.FtsOptions
import androidx.room3.Index
import androidx.room3.PrimaryKey

/*
 * Storage schema version 1: the consumed catalog tables only (K2-FOUNDATION-DESIGN, "Storage schema
 * version 1"). The entities exist so that Room3 generates, validates and exports the physical
 * schema. All reads and writes go through the core Database port with named-parameter SQL; the
 * column names below are the contract the catalog component codes against.
 *
 * Conventions shared by every table:
 *  - Identity columns are source-qualified: `provider` holds the ProviderId value and the raw
 *    producer identifier is stored verbatim next to it. Nothing is guessed or normalized.
 *  - `*_payload` columns hold the complete original typed record as TEXT. The catalog component
 *    owns the payload encoding; the generation row records its version in `payload_schema_version`.
 *  - A nullable column means the source did not supply the value. It is never replaced by zero,
 *    an empty string or a file modification time.
 *  - Timestamps are a whole-second `*_epoch_seconds` column plus a `*_nano` adjustment (0..999999999).
 */

/** One row per import attempt. UUID identity; status moves staging, validated, active, retired or failed. */
@Entity(
    tableName = "catalog_generation",
    indices = [
        Index(value = ["status"], name = "index_catalog_generation_status"),
        Index(value = ["provider", "status"], name = "index_catalog_generation_provider_status"),
    ],
)
internal data class CatalogGenerationEntity(
    /** Generated UUID. Never derived from file contents or names. */
    @PrimaryKey @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "provider") val provider: String,
    /** Payload format label, for example the CatalogGeneration.format value. */
    @ColumnInfo(name = "format") val format: String,
    /** The local content URI the user picked. Distinct from the upstream source below. */
    @ColumnInfo(name = "content_uri") val contentUri: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    /** Upstream source URI only when the caller truthfully supplied one. */
    @ColumnInfo(name = "source_uri") val sourceUri: String?,
    /** Upstream source version only when the caller truthfully supplied one. */
    @ColumnInfo(name = "source_version") val sourceVersion: String?,
    /** Claimed upstream source date. Null means unknown; never filled from file or retrieval time. */
    @ColumnInfo(name = "source_date_epoch_seconds") val sourceDateEpochSeconds: Long?,
    @ColumnInfo(name = "source_date_nano") val sourceDateNano: Int?,
    /** The instant this app observed (imported) the file. Separate from the source date. */
    @ColumnInfo(name = "observed_at_epoch_seconds") val observedAtEpochSeconds: Long,
    @ColumnInfo(name = "observed_at_nano") val observedAtNano: Int,
    /** Lowercase hex SHA-256 of the exact incoming bytes. */
    @ColumnInfo(name = "content_sha256") val contentSha256: String,
    @ColumnInfo(name = "observed_bytes") val observedBytes: Long,
    /** Comma-separated language tags covered by this generation. */
    @ColumnInfo(name = "language_scope") val languageScope: String,
    /** Version of the payload encoding. Not the database schema version and not a dataset version. */
    @ColumnInfo(name = "payload_schema_version") val payloadSchemaVersion: Int,
    @ColumnInfo(name = "parser_version") val parserVersion: String,
    /** Encoded source notice (title, license, attribution, uri) when one was supplied. */
    @ColumnInfo(name = "notice_payload") val noticePayload: String?,
    @ColumnInfo(name = "oracle_count") val oracleCount: Long,
    @ColumnInfo(name = "face_count") val faceCount: Long,
    @ColumnInfo(name = "printing_count") val printingCount: Long,
    /** One of the GenerationStatus names. */
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "activated_at_epoch_seconds") val activatedAtEpochSeconds: Long?,
    @ColumnInfo(name = "activated_at_nano") val activatedAtNano: Int?,
    /** Legacy rows remain user-selected inputs; the owning versioned codec validates new origins. */
    @ColumnInfo(name = "input_kind", defaultValue = "'USER_CONTENT_URI'") val inputKind: String = "USER_CONTENT_URI",
    /** Provenance only. Acquisition retention/removal must never cascade into installed content. */
    @ColumnInfo(name = "artifact_job_id") val artifactJobId: String? = null,
    @ColumnInfo(name = "source_payload") val sourcePayload: String? = null,
)

/** The single persisted active pointer per dataset key. Activation updates it in one transaction. */
@Entity(
    tableName = "catalog_active",
    foreignKeys = [
        ForeignKey(
            entity = CatalogGenerationEntity::class,
            parentColumns = ["generation_id"],
            childColumns = ["generation_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index(value = ["generation_id"], name = "index_catalog_active_generation_id")],
)
internal data class CatalogActiveEntity(
    @PrimaryKey @ColumnInfo(name = "dataset_key") val datasetKey: String,
    @ColumnInfo(name = "generation_id") val generationId: String,
)

/** One canonical row per oracle identity and language, never per printing. */
@Entity(
    tableName = "oracle_card",
    primaryKeys = ["generation_id", "provider", "oracle_id", "language"],
    foreignKeys = [
        ForeignKey(
            entity = CatalogGenerationEntity::class,
            parentColumns = ["generation_id"],
            childColumns = ["generation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = ["generation_id", "language", "normalized_name", "provider", "oracle_id"],
            name = "index_oracle_card_lookup_order",
        ),
    ],
)
internal data class OracleCardEntity(
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "provider") val provider: String,
    /** Raw producer oracle id, verbatim. */
    @ColumnInfo(name = "oracle_id") val oracleId: String,
    @ColumnInfo(name = "language") val language: String,
    /** Original name with its original Unicode. */
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "original_text") val originalText: String?,
    /** Deterministic sort key; ties break on provider then oracle_id. */
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "normalized_search_text") val normalizedSearchText: String,
    /** Digest of the canonical game fields only; two rows with equal digests are canonically equal. */
    @ColumnInfo(name = "canonical_digest") val canonicalDigest: String,
    /** Complete canonical typed record. */
    @ColumnInfo(name = "record_payload") val recordPayload: String,
)

/** Original face order and multi-face identities of a canonical record. */
@Entity(
    tableName = "card_face",
    primaryKeys = ["generation_id", "provider", "oracle_id", "language", "face_index"],
    foreignKeys = [
        ForeignKey(
            entity = OracleCardEntity::class,
            parentColumns = ["generation_id", "provider", "oracle_id", "language"],
            childColumns = ["generation_id", "provider", "oracle_id", "language"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class CardFaceEntity(
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "oracle_id") val oracleId: String,
    @ColumnInfo(name = "language") val language: String,
    @ColumnInfo(name = "face_index") val faceIndex: Int,
    /** Producer face id when supplied; null when absent. Never invented. */
    @ColumnInfo(name = "provider_face_id") val providerFaceId: String?,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "face_payload") val facePayload: String,
)

/** A printing keeps its own identity; it never collapses onto name plus set. */
@Entity(
    tableName = "printing",
    primaryKeys = ["generation_id", "provider", "printing_id"],
    foreignKeys = [
        ForeignKey(
            entity = CatalogGenerationEntity::class,
            parentColumns = ["generation_id"],
            childColumns = ["generation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = OracleCardEntity::class,
            parentColumns = ["generation_id", "provider", "oracle_id", "language"],
            childColumns = ["generation_id", "oracle_provider", "oracle_id", "oracle_language"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(
            value = ["generation_id", "oracle_provider", "oracle_id", "oracle_language"],
            name = "index_printing_oracle",
        ),
        Index(
            value = ["generation_id", "set_code", "collector_number", "language"],
            name = "index_printing_set_number",
        ),
    ],
)
internal data class PrintingEntity(
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "provider") val provider: String,
    /** Raw producer printing id, verbatim. */
    @ColumnInfo(name = "printing_id") val printingId: String,
    /**
     * Oracle relation. All three are null together when the source supplied no resolvable oracle
     * identity; a non-null triple must reference an existing oracle_card row.
     */
    @ColumnInfo(name = "oracle_provider") val oracleProvider: String?,
    @ColumnInfo(name = "oracle_id") val oracleId: String?,
    @ColumnInfo(name = "oracle_language") val oracleLanguage: String?,
    @ColumnInfo(name = "set_code") val setCode: String,
    @ColumnInfo(name = "set_name") val setName: String?,
    /** TEXT so leading zeros and suffixes survive. Never coerced to an integer. */
    @ColumnInfo(name = "collector_number") val collectorNumber: String,
    /** The printing's own language, which may differ from the oracle record's language. */
    @ColumnInfo(name = "language") val language: String,
    @ColumnInfo(name = "rarity") val rarity: String?,
    /** Comma-separated Finish names. Null means unknown; the empty string means known to be none. */
    @ColumnInfo(name = "finishes") val finishes: String?,
    @ColumnInfo(name = "image_uri") val imageUri: String?,
    /** Typed original metadata: images, relationships, supplied links, vendor ids, provenance. */
    @ColumnInfo(name = "metadata_payload") val metadataPayload: String,
)

/** Face assets belong to their actual printing, never to every printing of an oracle record. */
@Entity(
    tableName = "printing_face_asset",
    primaryKeys = ["generation_id", "provider", "printing_id", "face_index"],
    foreignKeys = [
        ForeignKey(
            entity = PrintingEntity::class,
            parentColumns = ["generation_id", "provider", "printing_id"],
            childColumns = ["generation_id", "provider", "printing_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class PrintingFaceAssetEntity(
    @ColumnInfo(name = "generation_id") val generationId: String,
    /** Provider of the printing id, so the printing identity stays source-qualified. */
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "printing_id") val printingId: String,
    @ColumnInfo(name = "face_index") val faceIndex: Int,
    /** Supplied printing-face id; null when the producer gave none. Never invented. */
    @ColumnInfo(name = "provider_face_id") val providerFaceId: String?,
    @ColumnInfo(name = "language") val language: String,
    @ColumnInfo(name = "printed_name") val printedName: String?,
    /** Image variants, printed text, art metadata and printing-scoped relationships. */
    @ColumnInfo(name = "asset_payload") val assetPayload: String,
)

/**
 * Full-text index with one row per canonical oracle record and language. The four identity
 * columns are stored but not indexed; they join a hit back to oracle_card. Tokenizer policy is
 * explicit: unicode61 with remove_diacritics 2. Rows are not copied per printing. FTS5 tables
 * cannot carry foreign keys, so removing a generation also deletes its rows here by generation_id.
 */
@Fts5(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics", "2"],
    notIndexed = ["generation_id", "provider", "oracle_id", "language"],
)
@Entity(tableName = "catalog_name_fts")
internal data class CatalogNameFtsEntity(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "oracle_id") val oracleId: String,
    @ColumnInfo(name = "language") val language: String,
)
