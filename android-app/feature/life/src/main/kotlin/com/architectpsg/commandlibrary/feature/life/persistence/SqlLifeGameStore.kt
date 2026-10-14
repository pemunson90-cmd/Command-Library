package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*
import java.security.MessageDigest
import com.architectpsg.commandlibrary.feature.life.domain.SnapshotGraphValidator

public data class StoredLifeGame(public val creation: GameCreation, public val checkpoint: GameCheckpoint, public val sequence: Long) {
    init { require(sequence >= 0) }
}

/**
 * Creation, structural graph and core/seat projection persistence. Full event/result/alert lineage guards
 * and operational GameService binding remain separate. Initializer runs only for a new operation;
 * it must be local and must not perform nested database transactions or external durable effects.
 */
public class SqlLifeGameStore(private val db: Database, private val codec: LifePayloadCodecPort) {
    public suspend fun create(input: GameCreation, initialize: () -> Outcome<GameCheckpoint>, cancel: CancelToken): Outcome<StoredLifeGame> =
        gameTransaction(db, cancel) { tx ->
            gameIdentity(input.operationId.value, FailureCode.INVALID_INPUT)
            val existing = metadata(tx, "creation_operation_id=:operation", arrayOf("operation" to gameText(input.operationId.value)), cancel)
            if (existing != null) {
                val stored = load(tx, existing, cancel)
                requireGame(stored.creation == input, FailureCode.INVALID_INPUT, "Creation operation has different input")
                stored
            } else {
                val creationBytes = gameValue(codec.encodeCreation(input))
                requireGame(creationBytes.size in 1..CREATION_BYTES, FailureCode.INVALID_INPUT, "Creation payload exceeds byte budget")
                val checkpoint = gameValue(initialize())
                validateInitialGame(input, checkpoint)
                val checkpointBytes = gameValue(codec.encodeCheckpoint(checkpoint))
                requireGame(checkpointBytes.size in 1..CHECKPOINT_BYTES, FailureCode.INVALID_INPUT, "Checkpoint exceeds byte budget")
                val seats = checkpoint.snapshot.seats.map { GameSeatProjection.of(it, FailureCode.INVALID_INPUT) }
                insert(tx, input, checkpoint, creationBytes, checkpointBytes, seats, cancel)
                StoredLifeGame(input, checkpoint, 0)
            }
        }

    public suspend fun read(id: GameId, cancel: CancelToken): Outcome<StoredLifeGame> = gameTransaction(db, cancel) { tx ->
        gameIdentity(id.source.value, FailureCode.INVALID_INPUT); gameIdentity(id.raw, FailureCode.INVALID_INPUT)
        val meta = metadata(tx, "game_source=:source AND game_id=:id", identityParameters(id), cancel)
            ?: throw GameStoreProblem(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Game not found"))
        corruptGame(meta.id == id, "Stored game identity mismatch")
        load(tx, meta, cancel)
    }

    private suspend fun metadata(tx: DbTransaction, predicate: String, parameters: Array<Pair<String, SqlValue>>, cancel: CancelToken): GameMetadata? {
        val rows = gameValue(tx.query(gameRequest("SELECT ${GameMetadata.columns} FROM life_game WHERE $predicate LIMIT 2", *parameters, maxRows = 2), cancel))
        corruptGame(rows.size <= 1, "Duplicate game/operation identity")
        return rows.singleOrNull()?.let { GameMetadata.read(it) }
    }

    private suspend fun load(tx: DbTransaction, meta: GameMetadata, cancel: CancelToken): StoredLifeGame {
        // Check every variable-size projection before fetching either authoritative payload.
        val seatRows = gameValue(tx.query(gameRequest("SELECT ${GameSeatProjection.columns} FROM life_seat_search WHERE game_source=:source AND game_id=:id LIMIT 7",
            *identityParameters(meta.id), maxRows = 7), cancel))
        corruptGame(seatRows.size in 1..6, "Invalid stored game roster size")
        val seats = seatRows.map { GameSeatProjection.read(it) }
        corruptGame(seats.map { it.id }.distinct().size == seats.size, "Duplicate stored seat")
        val creationBytes = blob(tx, meta.id, "creation_payload", meta.creationLength, cancel)
        corruptGame(hash(creationBytes) == meta.hash, "Game creation hash mismatch")
        val creation = gameValue(codec.decodeCreation(creationBytes))
        corruptGame(creation.operationId.value == meta.operation, "Game operation/payload mismatch")
        val checkpoint = gameValue(codec.decodeCheckpoint(blob(tx, meta.id, "checkpoint_payload", meta.checkpointLength, cancel)))
        val game = checkpoint.snapshot
        gameValue(SnapshotGraphValidator.validate(game))
        val ended = (game.endedAt as? Knowledge.Known)?.value
        corruptGame(game.id == meta.id && game.revision.value == meta.revision && game.status == meta.status &&
            game.startedAt == meta.started && ended == meta.ended, "Game checkpoint/projection mismatch")
        corruptGame(game.seats.size in 1..6 && game.seats.map { it.id }.distinct().size == game.seats.size, "Invalid checkpoint roster")
        val expected = game.seats.map { GameSeatProjection.of(it, FailureCode.CORRUPT_DATA) }
        corruptGame(expected.associateBy { it.id } == seats.associateBy { it.id }, "Game seat/checkpoint projection mismatch")
        return StoredLifeGame(creation, checkpoint, meta.sequence)
    }

    private suspend fun blob(tx: DbTransaction, id: GameId, column: String, length: Int, cancel: CancelToken): SnapshotBytes {
        // column is one of the two private call-site constants, never user input.
        val rows = gameValue(tx.query(gameRequest("SELECT $column FROM life_game WHERE game_source=:source AND game_id=:id LIMIT 2",
            *identityParameters(id), maxRows = 2), cancel))
        corruptGame(rows.size == 1, "Game payload disappeared")
        val bytes = (rows.single().gameColumn(column) as? SqlValue.Blob)?.value
            ?: rejectGame(FailureCode.CORRUPT_DATA, "Invalid game payload type")
        corruptGame(bytes.size == length, "Game payload length mismatch")
        return bytes
    }

    private suspend fun insert(tx: DbTransaction, input: GameCreation, checkpoint: GameCheckpoint, creation: SnapshotBytes,
        payload: SnapshotBytes, seats: List<GameSeatProjection>, cancel: CancelToken) {
        val game = checkpoint.snapshot
        gameOne(gameValue(tx.execute(gameRequest("""INSERT INTO life_game
            (game_source,game_id,revision,creation_operation_id,creation_payload,creation_sha256,checkpoint_payload,checkpoint_sequence,
            status,started_seconds,started_nano,ended_seconds,ended_nano,updated_seconds,updated_nano)
            VALUES (:source,:id,0,:operation,:creation,:hash,:checkpoint,0,:status,:seconds,:nano,NULL,NULL,:seconds,:nano)""",
            *identityParameters(game.id), "operation" to gameText(input.operationId.value), "creation" to SqlValue.Blob(creation),
            "hash" to gameText(hash(creation)), "checkpoint" to SqlValue.Blob(payload), "status" to gameText(game.status.name),
            "seconds" to gameNumber(game.startedAt.epochSeconds), "nano" to gameNumber(game.startedAt.nanoAdjustment.toLong())), cancel)))
        for (seat in seats) {
            gameOne(gameValue(tx.execute(gameRequest("""INSERT INTO life_seat_search
                (game_source,game_id,seat_id,player_name,player_name_folded,deck_source,deck_id,deck_name,deck_name_folded)
                VALUES (:source,:id,:seat,:name,:folded,:deck_source,:deck_id,:deck_name,:deck_folded)""",
                *identityParameters(game.id), "seat" to gameText(seat.id), "name" to gameText(seat.name), "folded" to gameText(seat.folded),
                "deck_source" to gameNullableText(seat.deckSource), "deck_id" to gameNullableText(seat.deckId),
                "deck_name" to gameNullableText(seat.deckName), "deck_folded" to gameNullableText(seat.deckFolded)), cancel)))
        }
    }

    private fun identityParameters(id: GameId): Array<Pair<String, SqlValue>> =
        arrayOf("source" to gameText(id.source.value), "id" to gameText(id.raw))
    private fun hash(bytes: SnapshotBytes): String = MessageDigest.getInstance("SHA-256").digest(bytes.copyBytes())
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
