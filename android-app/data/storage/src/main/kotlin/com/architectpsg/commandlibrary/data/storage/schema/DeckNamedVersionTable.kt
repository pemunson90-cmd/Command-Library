package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Immutable named references to complete deck revisions; receipts remain the creation authority. */
@Entity(tableName = "deck_named_version", foreignKeys = [
    ForeignKey(entity = DeckRevisionEntity::class, parentColumns = ["provider", "deck_id", "revision"],
        childColumns = ["provider", "deck_id", "source_revision"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = DeckOperationEntity::class, parentColumns = ["sequence"],
        childColumns = ["creating_sequence"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = DeckNamedVersionEntity::class, parentColumns = ["provider", "deck_id", "version_id"],
        childColumns = ["provider", "deck_id", "predecessor_version_id"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = DeckNamedVersionEntity::class, parentColumns = ["provider", "deck_id", "version_id"],
        childColumns = ["provider", "deck_id", "restored_target_version_id"], onDelete = ForeignKey.NO_ACTION),
], indices = [
    Index(value = ["provider", "deck_id", "version_id"], name = "index_deck_named_version_identity", unique = true),
    Index(value = ["creating_sequence"], name = "index_deck_named_version_receipt", unique = true),
    Index(value = ["provider", "deck_id", "creating_sequence"], name = "index_deck_named_version_order"),
    Index(value = ["provider", "deck_id", "source_revision"], name = "index_deck_named_version_source"),
    Index(value = ["provider", "deck_id", "predecessor_version_id"], name = "index_deck_named_version_predecessor"),
    Index(value = ["provider", "deck_id", "restored_target_version_id"], name = "index_deck_named_version_target"),
])
internal data class DeckNamedVersionEntity(
    @PrimaryKey @ColumnInfo(name = "version_id") val version_id: String,
    @ColumnInfo(name = "metadata_version") val metadata_version: Int,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "deck_id") val deck_id: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "created_seconds") val created_seconds: Long,
    @ColumnInfo(name = "created_nano") val created_nano: Int,
    @ColumnInfo(name = "source_revision") val source_revision: Long,
    @ColumnInfo(name = "creating_sequence") val creating_sequence: Long,
    @ColumnInfo(name = "predecessor_version_id") val predecessor_version_id: String?,
    @ColumnInfo(name = "restored_target_version_id") val restored_target_version_id: String?,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "metadata_sha256") val metadata_sha256: String,
)
