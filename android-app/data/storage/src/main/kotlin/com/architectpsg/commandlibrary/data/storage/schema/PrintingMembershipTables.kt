package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

/**
 * Source-qualified canonical memberships of a printing. Slot -1/WHOLE describes the whole
 * printing; nonnegative slots/FACE are original printing-face ordinals, never canonical indices.
 * The owning importer validates kind/slot consistency and resolves all targets after EOF.
 * Multiple source faces may target the same Oracle; lookup de-duplicates printing identities.
 */
@Entity(
    tableName = "printing_oracle_membership",
    primaryKeys = ["generation_id", "printing_provider", "printing_id", "source_slot"],
    foreignKeys = [
        ForeignKey(
            entity = PrintingEntity::class,
            parentColumns = ["generation_id", "provider", "printing_id"],
            childColumns = ["generation_id", "printing_provider", "printing_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = OracleCardEntity::class,
            parentColumns = ["generation_id", "provider", "oracle_id", "language"],
            childColumns = ["generation_id", "oracle_provider", "oracle_id", "oracle_language"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index(
        value = ["generation_id", "oracle_provider", "oracle_id", "oracle_language", "printing_provider", "printing_id"],
        name = "index_printing_oracle_membership_lookup",
    )],
)
internal data class PrintingOracleMembershipEntity(
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "printing_provider") val printingProvider: String,
    @ColumnInfo(name = "printing_id") val printingId: String,
    @ColumnInfo(name = "source_slot") val sourceSlot: Int,
    @ColumnInfo(name = "source_kind") val sourceKind: String,
    @ColumnInfo(name = "oracle_provider") val oracleProvider: String,
    @ColumnInfo(name = "oracle_id") val oracleId: String,
    @ColumnInfo(name = "oracle_language") val oracleLanguage: String,
)
