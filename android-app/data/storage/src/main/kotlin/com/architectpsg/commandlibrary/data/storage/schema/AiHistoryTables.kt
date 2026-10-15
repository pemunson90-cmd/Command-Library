package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Version1 payload is strict UTF8 title; deletion clears title and source-qualified deck projections. */
@Entity(tableName = "ai_conversation", indices = [
    Index(value = ["change_sequence"], name = "index_ai_conversation_change_sequence", unique = true),
    Index(value = ["deck_source", "deck_raw", "deleted", "conversation_id"], name = "index_ai_conversation_deck"),
])
internal data class AiConversationEntity(
    @PrimaryKey @ColumnInfo(name = "conversation_id") val conversation_id: String,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "change_sequence") val change_sequence: Long,
    @ColumnInfo(name = "deleted") val deleted: Boolean,
    @ColumnInfo(name = "deck_source") val deck_source: String,
    @ColumnInfo(name = "deck_raw") val deck_raw: String,
    @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "created_seconds") val created_seconds: Long,
    @ColumnInfo(name = "created_nanos") val created_nanos: Int,
    @ColumnInfo(name = "modified_seconds") val modified_seconds: Long,
    @ColumnInfo(name = "modified_nanos") val modified_nanos: Int,
    @ColumnInfo(name = "payload_version") val payload_version: Int,
    @ColumnInfo(name = "payload_length") val payload_length: Int,
    @ColumnInfo(name = "payload_sha256") val payload_sha256: String,
    @ColumnInfo(name = "payload") val payload: ByteArray?,
)

/** Metadata is separate from content; interrupted recovery never rewrites prompt/reply payloads. */
@Entity(tableName = "ai_turn", indices = [
    Index(value = ["conversation_id", "ordinal"], name = "index_ai_turn_conversation_ordinal", unique = true),
    Index(value = ["deleted", "status", "owner_epoch"], name = "index_ai_turn_recovery"),
])
internal data class AiTurnEntity(
    @PrimaryKey @ColumnInfo(name = "turn_id") val turn_id: String,
    @ColumnInfo(name = "conversation_id") val conversation_id: String,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "ordinal") val ordinal: Long,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "owner_epoch") val owner_epoch: String?,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "deleted") val deleted: Boolean,
    @ColumnInfo(name = "started_seconds") val started_seconds: Long,
    @ColumnInfo(name = "started_nanos") val started_nanos: Int,
    @ColumnInfo(name = "finished_seconds") val finished_seconds: Long?,
    @ColumnInfo(name = "finished_nanos") val finished_nanos: Int?,
    @ColumnInfo(name = "payload_version") val payload_version: Int,
    @ColumnInfo(name = "payload_length") val payload_length: Int,
    @ColumnInfo(name = "payload_sha256") val payload_sha256: String,
    @ColumnInfo(name = "payload") val payload: ByteArray?,
)
