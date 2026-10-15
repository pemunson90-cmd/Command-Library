package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** User-owned data has no catalog/profile lifecycle foreign key. */
@Entity(tableName = "deck_head", primaryKeys = ["provider", "deck_id"])
internal data class DeckHeadEntity(
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "deck_id") val deck_id: String,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_seconds") val created_seconds: Long,
    @ColumnInfo(name = "created_nano") val created_nano: Int,
    @ColumnInfo(name = "modified_seconds") val modified_seconds: Long,
    @ColumnInfo(name = "modified_nano") val modified_nano: Int,
    @ColumnInfo(name = "entry_count") val entry_count: Int,
    @ColumnInfo(name = "total_quantity") val total_quantity: Long,
)

/** Insert-only complete historical content and its own independent bounded projection. */
@Entity(tableName = "deck_revision", primaryKeys = ["provider", "deck_id", "revision"], foreignKeys = [
    ForeignKey(entity = DeckHeadEntity::class, parentColumns = ["provider", "deck_id"],
        childColumns = ["provider", "deck_id"], onDelete = ForeignKey.NO_ACTION),
])
internal data class DeckRevisionEntity(
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "deck_id") val deck_id: String,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_seconds") val created_seconds: Long,
    @ColumnInfo(name = "created_nano") val created_nano: Int,
    @ColumnInfo(name = "modified_seconds") val modified_seconds: Long,
    @ColumnInfo(name = "modified_nano") val modified_nano: Int,
    @ColumnInfo(name = "entry_count") val entry_count: Int,
    @ColumnInfo(name = "total_quantity") val total_quantity: Long,
    @ColumnInfo(name = "payload_bytes") val payload_bytes: Long,
    @ColumnInfo(name = "payload_sha256") val payload_sha256: String,
    @ColumnInfo(name = "payload") val payload: ByteArray,
)

/** Append-only receipts: sequence is an epoch, not a contiguous count or deck revision. */
@Entity(tableName = "deck_operation", foreignKeys = [
    ForeignKey(entity = DeckRevisionEntity::class, parentColumns = ["provider", "deck_id", "revision"],
        childColumns = ["provider", "deck_id", "result_revision"], onDelete = ForeignKey.NO_ACTION),
], indices = [
    Index(value = ["operation_id"], name = "index_deck_operation_id", unique = true),
    Index(value = ["provider", "deck_id", "result_revision"], name = "index_deck_operation_result"),
])
internal data class DeckOperationEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "sequence") val sequence: Long = 0,
    @ColumnInfo(name = "operation_id") val operation_id: String,
    @ColumnInfo(name = "request_version") val request_version: Int,
    @ColumnInfo(name = "request_sha256") val request_sha256: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "deck_id") val deck_id: String,
    @ColumnInfo(name = "result_revision") val result_revision: Long,
)
