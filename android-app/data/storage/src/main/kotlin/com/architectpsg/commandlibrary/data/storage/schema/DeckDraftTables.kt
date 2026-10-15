package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Mutable raw slot; tombstone generations and terminal discovery survive consumption/discard. */
@Entity(tableName = "deck_draft_state", primaryKeys = ["provider", "deck_id"], foreignKeys = [
    ForeignKey(entity = DeckHeadEntity::class, parentColumns = ["provider", "deck_id"],
        childColumns = ["provider", "deck_id"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = DeckRevisionEntity::class, parentColumns = ["provider", "deck_id", "revision"],
        childColumns = ["provider", "deck_id", "raw_base_revision"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = DeckDraftIntentEntity::class, parentColumns = ["provider", "deck_id", "operation_id"],
        childColumns = ["provider", "deck_id", "latest_operation_id"], onDelete = ForeignKey.NO_ACTION),
], indices = [
    Index(value = ["provider", "deck_id", "raw_base_revision"], name = "index_deck_draft_state_source"),
    Index(value = ["provider", "deck_id", "latest_operation_id"], name = "index_deck_draft_state_latest"),
])
internal data class DeckDraftStateEntity(
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "deck_id") val deck_id: String,
    @ColumnInfo(name = "metadata_version") val metadata_version: Int,
    @ColumnInfo(name = "generation") val generation: Long,
    @ColumnInfo(name = "raw_base_revision") val raw_base_revision: Long?,
    @ColumnInfo(name = "raw_version") val raw_version: Int?,
    @ColumnInfo(name = "raw_length") val raw_length: Long?,
    @ColumnInfo(name = "raw_sha256") val raw_sha256: String?,
    @ColumnInfo(name = "raw") val raw: ByteArray?,
    @ColumnInfo(name = "latest_operation_id") val latest_operation_id: String?,
    @ColumnInfo(name = "last_mutation_kind") val last_mutation_kind: String,
    @ColumnInfo(name = "last_mutation_sha256") val last_mutation_sha256: String,
    @ColumnInfo(name = "state_sha256") val state_sha256: String,
)

/** Request descriptors/pinned bytes are immutable; status and historical effect transition once. */
@Entity(tableName = "deck_draft_intent", foreignKeys = [
    ForeignKey(entity = DeckDraftStateEntity::class, parentColumns = ["provider", "deck_id"],
        childColumns = ["provider", "deck_id"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = DeckRevisionEntity::class, parentColumns = ["provider", "deck_id", "revision"],
        childColumns = ["provider", "deck_id", "source_base_revision"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = DeckOperationEntity::class, parentColumns = ["sequence"],
        childColumns = ["receipt_sequence"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = DeckRevisionEntity::class, parentColumns = ["provider", "deck_id", "revision"],
        childColumns = ["provider", "deck_id", "result_revision"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = DeckRevisionEntity::class, parentColumns = ["provider", "deck_id", "revision"],
        childColumns = ["provider", "deck_id", "effect_base_revision"], onDelete = ForeignKey.NO_ACTION),
], indices = [
    Index(value = ["provider", "deck_id", "operation_id"], name = "index_deck_draft_intent_identity", unique = true),
    Index(value = ["provider", "deck_id", "source_generation"], name = "index_deck_draft_intent_order", unique = true),
    Index(value = ["provider", "deck_id", "status", "source_generation"], name = "index_deck_draft_intent_pending"),
    Index(value = ["receipt_sequence"], name = "index_deck_draft_intent_receipt", unique = true),
    Index(value = ["provider", "deck_id", "source_base_revision"], name = "index_deck_draft_intent_source"),
    Index(value = ["provider", "deck_id", "result_revision"], name = "index_deck_draft_intent_result"),
    Index(value = ["provider", "deck_id", "effect_base_revision"], name = "index_deck_draft_intent_effect"),
])
internal data class DeckDraftIntentEntity(
    @PrimaryKey @ColumnInfo(name = "operation_id") val operation_id: String,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "deck_id") val deck_id: String,
    @ColumnInfo(name = "metadata_version") val metadata_version: Int,
    @ColumnInfo(name = "source_generation") val source_generation: Long,
    @ColumnInfo(name = "source_base_revision") val source_base_revision: Long,
    @ColumnInfo(name = "raw_version") val raw_version: Int,
    @ColumnInfo(name = "raw_length") val raw_length: Long,
    @ColumnInfo(name = "raw_sha256") val raw_sha256: String,
    @ColumnInfo(name = "raw") val raw: ByteArray,
    @ColumnInfo(name = "scope") val scope: String,
    @ColumnInfo(name = "patch_version") val patch_version: Int,
    @ColumnInfo(name = "patch_length") val patch_length: Long,
    @ColumnInfo(name = "patch_sha256") val patch_sha256: String,
    @ColumnInfo(name = "patch") val patch: ByteArray,
    @ColumnInfo(name = "request_version") val request_version: Int,
    @ColumnInfo(name = "request_sha256") val request_sha256: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "receipt_sequence") val receipt_sequence: Long?,
    @ColumnInfo(name = "result_revision") val result_revision: Long?,
    @ColumnInfo(name = "effect_kind") val effect_kind: String?,
    @ColumnInfo(name = "effect_generation") val effect_generation: Long?,
    @ColumnInfo(name = "effect_base_revision") val effect_base_revision: Long?,
    @ColumnInfo(name = "effect_raw_sha256") val effect_raw_sha256: String?,
    @ColumnInfo(name = "state_sha256") val state_sha256: String,
)
