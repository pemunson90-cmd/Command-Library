package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Typed checkpoint is authoritative; projections and CAS validation belong to the checked life adapter. */
@Entity(tableName = "life_game", primaryKeys = ["game_source", "game_id"], indices = [
    Index(value = ["creation_operation_id"], name = "index_life_game_creation_operation", unique = true),
    Index(value = ["status", "started_seconds", "started_nano", "game_source", "game_id"], name = "index_life_game_status_started"),
    Index(value = ["started_seconds", "started_nano", "game_source", "game_id"], name = "index_life_game_started"),
])
internal data class LifeGameEntity(
    @ColumnInfo(name = "game_source") val gameSource: String,
    @ColumnInfo(name = "game_id") val gameId: String,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "creation_operation_id") val creationOperationId: String,
    @ColumnInfo(name = "creation_payload") val creationPayload: ByteArray,
    @ColumnInfo(name = "creation_sha256") val creationSha256: String,
    @ColumnInfo(name = "checkpoint_payload") val checkpointPayload: ByteArray,
    @ColumnInfo(name = "checkpoint_sequence") val checkpointSequence: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "started_seconds") val startedSeconds: Long,
    @ColumnInfo(name = "started_nano") val startedNano: Int,
    @ColumnInfo(name = "ended_seconds") val endedSeconds: Long?,
    @ColumnInfo(name = "ended_nano") val endedNano: Int?,
    @ColumnInfo(name = "updated_seconds") val updatedSeconds: Long,
    @ColumnInfo(name = "updated_nano") val updatedNano: Int,
)

@Entity(tableName = "life_event", primaryKeys = ["game_source", "game_id", "event_id"], foreignKeys = [
    ForeignKey(entity = LifeGameEntity::class, parentColumns = ["game_source", "game_id"],
        childColumns = ["game_source", "game_id"], onDelete = ForeignKey.CASCADE),
], indices = [
    Index(value = ["game_source", "game_id", "revision"], name = "index_life_event_revision", unique = true),
    Index(value = ["game_source", "game_id", "undo_of_event_id"], name = "index_life_event_undo_target", unique = true),
])
internal data class LifeEventEntity(
    @ColumnInfo(name = "game_source") val gameSource: String,
    @ColumnInfo(name = "game_id") val gameId: String,
    @ColumnInfo(name = "event_id") val eventId: Long,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "at_seconds") val atSeconds: Long,
    @ColumnInfo(name = "at_nano") val atNano: Int,
    @ColumnInfo(name = "undo_of_event_id") val undoOfEventId: Long?,
    @ColumnInfo(name = "archive_payload") val archivePayload: ByteArray,
)

@Entity(tableName = "life_seat_search", primaryKeys = ["game_source", "game_id", "seat_id"], foreignKeys = [
    ForeignKey(entity = LifeGameEntity::class, parentColumns = ["game_source", "game_id"],
        childColumns = ["game_source", "game_id"], onDelete = ForeignKey.CASCADE),
], indices = [
    Index(value = ["player_name_folded", "game_source", "game_id"], name = "index_life_seat_player"),
    Index(value = ["deck_source", "deck_id", "game_source", "game_id"], name = "index_life_seat_deck"),
    Index(value = ["deck_name_folded", "game_source", "game_id"], name = "index_life_seat_deck_name"),
])
internal data class LifeSeatSearchEntity(
    @ColumnInfo(name = "game_source") val gameSource: String,
    @ColumnInfo(name = "game_id") val gameId: String,
    @ColumnInfo(name = "seat_id") val seatId: String,
    @ColumnInfo(name = "player_name") val playerName: String,
    @ColumnInfo(name = "player_name_folded") val playerNameFolded: String,
    @ColumnInfo(name = "deck_source") val deckSource: String?,
    @ColumnInfo(name = "deck_id") val deckId: String?,
    @ColumnInfo(name = "deck_name") val deckName: String?,
    @ColumnInfo(name = "deck_name_folded") val deckNameFolded: String?,
)

/** One retained current/retracted result projection per game; winners remain in the typed checkpoint. */
@Entity(tableName = "life_result", primaryKeys = ["game_source", "game_id"], foreignKeys = [
    ForeignKey(entity = LifeGameEntity::class, parentColumns = ["game_source", "game_id"],
        childColumns = ["game_source", "game_id"], onDelete = ForeignKey.CASCADE),
], indices = [Index(value = ["result_id"], name = "index_life_result_identity", unique = true)])
internal data class LifeResultEntity(
    @ColumnInfo(name = "game_source") val gameSource: String,
    @ColumnInfo(name = "game_id") val gameId: String,
    @ColumnInfo(name = "result_id") val resultId: String,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "finished_seconds") val finishedSeconds: Long,
    @ColumnInfo(name = "finished_nano") val finishedNano: Int,
    @ColumnInfo(name = "reason") val reason: String,
    @ColumnInfo(name = "retracted") val retracted: Boolean,
)

/** A delivery bit is operational bookkeeping, not exactly-once OS notification proof. */
@Entity(tableName = "life_alert", primaryKeys = ["game_source", "game_id", "occurrence_id"], foreignKeys = [
    ForeignKey(entity = LifeGameEntity::class, parentColumns = ["game_source", "game_id"],
        childColumns = ["game_source", "game_id"], onDelete = ForeignKey.CASCADE),
], indices = [Index(value = ["game_source", "game_id", "acknowledged_seconds", "acknowledged_nano"], name = "index_life_alert_acknowledgement")])
internal data class LifeAlertEntity(
    @ColumnInfo(name = "game_source") val gameSource: String,
    @ColumnInfo(name = "game_id") val gameId: String,
    @ColumnInfo(name = "occurrence_id") val occurrenceId: String,
    @ColumnInfo(name = "target_kind") val targetKind: String,
    @ColumnInfo(name = "target_seat_id") val targetSeatId: String?,
    @ColumnInfo(name = "fired_seconds") val firedSeconds: Long,
    @ColumnInfo(name = "fired_nano") val firedNano: Int,
    @ColumnInfo(name = "acknowledged_seconds") val acknowledgedSeconds: Long?,
    @ColumnInfo(name = "acknowledged_nano") val acknowledgedNano: Int?,
    @ColumnInfo(name = "delivery_mask") val deliveryMask: Int,
)

/** Intentionally no profile/game FK: deleting reusable setup cannot delete frozen game history. */
@Entity(tableName = "life_profile", indices = [Index(value = ["name_folded", "profile_id"], name = "index_life_profile_name")])
internal data class LifeProfileEntity(
    @PrimaryKey @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "name_folded") val nameFolded: String,
    @ColumnInfo(name = "payload") val payload: ByteArray,
    @ColumnInfo(name = "updated_seconds") val updatedSeconds: Long,
    @ColumnInfo(name = "updated_nano") val updatedNano: Int,
)
