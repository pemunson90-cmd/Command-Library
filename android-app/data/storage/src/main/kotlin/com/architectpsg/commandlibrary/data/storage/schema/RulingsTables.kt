package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Independent from card/rules generations. Complete provenance lives in the versioned payload. */
@Entity(tableName = "rulings_generation")
internal data class RulingsGenerationEntity(
    @PrimaryKey @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "language_scope") val languageScope: String,
    @ColumnInfo(name = "provenance_payload") val provenancePayload: String,
    @ColumnInfo(name = "record_count") val recordCount: Long,
    @ColumnInfo(name = "payload_schema_version") val payloadSchemaVersion: Int,
)

/** Local ordinal identity retains duplicate records. Oracle IDs deliberately have no card FK. */
@Entity(
    tableName = "ruling_record",
    primaryKeys = ["generation_id", "ordinal"],
    foreignKeys = [ForeignKey(entity = RulingsGenerationEntity::class,
        parentColumns = ["generation_id"], childColumns = ["generation_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["generation_id", "oracle_provider", "oracle_id", "published_day", "ordinal"],
        orders = [Index.Order.ASC, Index.Order.ASC, Index.Order.ASC, Index.Order.DESC, Index.Order.ASC],
        name = "index_ruling_record_oracle_day_ordinal")],
)
internal data class RulingRecordEntity(
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "ordinal") val ordinal: Long,
    @ColumnInfo(name = "oracle_provider") val oracleProvider: String,
    @ColumnInfo(name = "oracle_id") val oracleId: String,
    @ColumnInfo(name = "publisher") val publisher: String,
    @ColumnInfo(name = "published_day") val publishedDay: String,
    @ColumnInfo(name = "record_payload") val recordPayload: String,
)

/** Repository singleton1. Active parent deletion is forbidden until an atomic pointer switch. */
@Entity(
    tableName = "rulings_active",
    foreignKeys = [ForeignKey(entity = RulingsGenerationEntity::class,
        parentColumns = ["generation_id"], childColumns = ["generation_id"], onDelete = ForeignKey.NO_ACTION)],
    indices = [Index(value = ["generation_id"], name = "index_rulings_active_generation_id")],
)
internal data class RulingsActiveEntity(
    @PrimaryKey @ColumnInfo(name = "singleton_id") val singletonId: Int,
    @ColumnInfo(name = "generation_id") val generationId: String,
)
