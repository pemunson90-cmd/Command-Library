package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*

public data class LifeActionWrite(public val archive: GameEventArchive, public val checkpoint: GameCheckpoint)

/** Atomic storage mechanics. Trusted local callbacks/semantic engine must be reviewed before service binding. */
public class SqlLifeGameMutator(private val db: Database, private val codec: LifePayloadCodecPort) {
    private val games = SqlLifeGameStore(db, codec)
    private val events = SqlLifeEventReader(db, codec)

    /** Full operational/spine read remains available even when append capacity is exhausted. */
    public suspend fun readCurrent(id: GameId, cancel: CancelToken): Outcome<StoredLifeGame> = gameTransaction(db, cancel) { tx ->
        val current = current(tx, id, null, null, cancel, writable = false)
        loadLifeOperational(tx, current.checkpoint.snapshot, cancel)
        current
    }

    /** Authoritative system settlement; exact no-op avoids encoding, writes and sequence churn. */
    public suspend fun systemCurrent(id: GameId, updatedAt: UtcTimestamp, build: (StoredLifeGame) -> Outcome<GameCheckpoint>, cancel: CancelToken): Outcome<StoredLifeGame> = gameTransaction(db, cancel) { tx ->
        val current = current(tx, id, null, null, cancel, writable = false)
        val operational = loadLifeOperational(tx, current.checkpoint.snapshot, cancel)
        val next = gameValue(build(current))
        mutationGraph(next.snapshot, FailureCode.INVALID_INPUT)
        lifeSettlement(current.checkpoint.snapshot, next.snapshot, FailureCode.INVALID_INPUT)
        requireGame(next.lastSettledEventId == current.checkpoint.lastSettledEventId, FailureCode.INVALID_INPUT, "System checkpoint changed settled event identity")
        if (next == current.checkpoint) return@gameTransaction current
        requireGame(current.sequence < Long.MAX_VALUE, FailureCode.INVALID_INPUT, "Checkpoint sequence exhausted")
        val bytes = checkpointBytes(next)
        val change = prepareLifeOperational(tx, current.checkpoint.snapshot, next.snapshot, operational, cancel)
        cas(tx, current, next, bytes, updatedAt, cancel)
        writeLifeOperational(tx, id, operational, change, cancel)
        StoredLifeGame(current.creation, next, current.sequence + 1)
    }

    /** Awaited local callback; nullable expected is legacy authoritative-in-transaction mode. */
    public suspend fun actionWithContext(id: GameId, expected: Revision?, build: suspend (LifeActionContext) -> Outcome<LifeActionWrite>, cancel: CancelToken): Outcome<StoredLifeGame> =
        actionTransaction(id, expected, null, { tx, current, nextId ->
            val context = LifeActionContext(current, nextId, tx, events, cancel)
            val proposed: Outcome<LifeActionWrite>
            var failedRead: Outcome<Nothing>?
            try { proposed = build(context) } finally { failedRead = context.close() }
            failedRead ?: proposed
        }, cancel)

    public suspend fun action(id: GameId, expected: Revision, expectedSequence: Long, build: (StoredLifeGame, Long) -> Outcome<LifeActionWrite>, cancel: CancelToken): Outcome<StoredLifeGame> =
        actionTransaction(id, expected, expectedSequence, { _, current, nextId -> build(current, nextId) }, cancel)

    private suspend fun actionTransaction(id: GameId, expected: Revision?, expectedSequence: Long?, build: suspend (DbTransaction, StoredLifeGame, Long) -> Outcome<LifeActionWrite>, cancel: CancelToken): Outcome<StoredLifeGame> = gameTransaction(db, cancel) { tx ->
        val current = current(tx, id, expected, expectedSequence, cancel)
        requireGame(current.checkpoint.snapshot.revision.value < Long.MAX_VALUE, FailureCode.INVALID_INPUT, "Game revision exhausted")
        val operational = loadLifeOperational(tx, current.checkpoint.snapshot, cancel)
        val nextId = nextEvent(tx, id, cancel)
        val write = gameValue(build(tx, current, nextId))
        lifeActionPacket(current, nextId, write)
        val undo = (write.archive.event.undoOfEventId as? Knowledge.Known)?.value
        if (undo != null) {
            val target = referenced(tx, id, undo, current.checkpoint.snapshot.revision.value, FailureCode.INVALID_INPUT, cancel)
            if (target.event.undoOfEventId is Knowledge.Unknown) throw GameStoreProblem(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Undo intent requires explicit repair"))
            requireGame(target.event.revision.value <= current.checkpoint.snapshot.revision.value && target.event.undoOfEventId is Knowledge.Absent, FailureCode.INVALID_INPUT, "Target is not an explicit original action")
            val used = gameValue(tx.query(gameRequest("SELECT 1 AS present FROM life_event WHERE game_source=:source AND game_id=:id AND undo_of_event_id=:undo LIMIT 1",
                *lifeIdentityParameters(id), "undo" to gameNumber(undo), maxRows = 1), cancel))
            requireGame(used.isEmpty(), FailureCode.INVALID_INPUT, "Event already undone")
        }
        val archiveBytes = gameValue(codec.encodeArchive(write.archive))
        requireGame(archiveBytes.size in 1..EVENT_ARCHIVE_BYTES, FailureCode.INVALID_INPUT, "Archive exceeds byte budget")
        val checkpointBytes = checkpointBytes(write.checkpoint)
        val change = prepareLifeOperational(tx, current.checkpoint.snapshot, write.checkpoint.snapshot, operational, cancel)
        cas(tx, current, write.checkpoint, checkpointBytes, write.archive.event.at, cancel)
        insertEvent(tx, write.archive.event, archiveBytes, cancel)
        writeLifeSeats(tx, current.checkpoint.snapshot, write.checkpoint.snapshot, cancel)
        writeLifeOperational(tx, id, operational, change, cancel)
        StoredLifeGame(current.creation, write.checkpoint, current.sequence + 1)
    }

    public suspend fun checkpoint(id: GameId, expected: Revision, expectedSequence: Long, updatedAt: UtcTimestamp, build: (StoredLifeGame) -> Outcome<GameCheckpoint>, cancel: CancelToken): Outcome<StoredLifeGame> = gameTransaction(db, cancel) { tx ->
        val current = current(tx, id, expected, expectedSequence, cancel)
        val operational = loadLifeOperational(tx, current.checkpoint.snapshot, cancel)
        val next = gameValue(build(current))
        mutationGraph(next.snapshot, FailureCode.INVALID_INPUT)
        lifeSettlement(current.checkpoint.snapshot, next.snapshot, FailureCode.INVALID_INPUT)
        requireGame(next.lastSettledEventId == current.checkpoint.lastSettledEventId, FailureCode.INVALID_INPUT, "Checkpoint changed settled event identity")
        val bytes = checkpointBytes(next)
        val change = prepareLifeOperational(tx, current.checkpoint.snapshot, next.snapshot, operational, cancel)
        cas(tx, current, next, bytes, updatedAt, cancel)
        writeLifeOperational(tx, id, operational, change, cancel)
        StoredLifeGame(current.creation, next, current.sequence + 1)
    }

    public suspend fun markDelivered(id: GameId, occurrenceId: EntityId, channels: Int, cancel: CancelToken): Outcome<Unit> = gameTransaction(db, cancel) { tx ->
        requireGame(channels in 1..7, FailureCode.INVALID_INPUT, "Invalid delivery channel bits")
        gameIdentity(occurrenceId.value, FailureCode.INVALID_INPUT)
        val game = games.readWithin(tx, id, cancel).checkpoint.snapshot
        val row = findLifeAlerts(tx, id, listOf(occurrenceId), cancel)[occurrenceId]
            ?: throw GameStoreProblem(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Alert occurrence not found"))
        val currentAlert = game.alerts.singleOrNull { it.id == occurrenceId }
        corruptGame(currentAlert == null || currentAlert == row.alert, "Delivery alert/checkpoint projection mismatch")
        val seat = (row.alert.target as? GameAlertTarget.SeatClock)?.seatId
        corruptGame(seat == null || game.seats.any { it.id == seat }, "Alert references unknown seat")
        gameOne(gameValue(tx.execute(gameRequest("UPDATE life_alert SET delivery_mask=delivery_mask|:bits WHERE game_source=:source AND game_id=:id AND occurrence_id=:occurrence",
            *lifeIdentityParameters(id), "occurrence" to gameText(occurrenceId.value), "bits" to gameNumber(channels.toLong())), cancel)))
    }

    private suspend fun current(tx: DbTransaction, id: GameId, expected: Revision?, sequence: Long?, cancel: CancelToken, writable: Boolean = true): StoredLifeGame {
        requireGame(sequence == null || sequence >= 0, FailureCode.INVALID_INPUT, "Negative checkpoint sequence")
        val stored = games.readWithin(tx, id, cancel)
        requireGame((expected == null || stored.checkpoint.snapshot.revision == expected) && (sequence == null || stored.sequence == sequence), FailureCode.STALE_REVISION, "Game revision/checkpoint changed")
        if (writable) requireGame(stored.sequence < Long.MAX_VALUE, FailureCode.INVALID_INPUT, "Checkpoint sequence exhausted")
        val game = stored.checkpoint.snapshot
        val revision = game.revision
        val latest = gameValue(tx.query(gameRequest("SELECT ${typedGameInteger("revision")} FROM life_event WHERE game_source=:source AND game_id=:id ORDER BY revision DESC LIMIT 1", *lifeIdentityParameters(id), maxRows = 1), cancel))
        if (game.lastEventId is Knowledge.Unknown || stored.checkpoint.lastSettledEventId is Knowledge.Unknown) throw GameStoreProblem(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Event lineage requires explicit repair"))
        if (revision.value == 0L) corruptGame(game.lastEventId is Knowledge.Absent && stored.checkpoint.lastSettledEventId is Knowledge.Absent && latest.isEmpty(), "Initial event lineage is contradictory")
        else {
            val last = (game.lastEventId as? Knowledge.Known)?.value
                ?: throw GameStoreProblem(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Missing live event lineage"))
            if (stored.checkpoint.lastSettledEventId !is Knowledge.Known) throw GameStoreProblem(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Missing settled event lineage"))
            corruptGame(stored.checkpoint.lastSettledEventId == Knowledge.Known(last) && latest.size == 1 && latest.single().gameRequiredLong("revision") == revision.value, "Current event/settled/revision lineage mismatch")
            val archive = referenced(tx, id, last, revision.value, FailureCode.CORRUPT_DATA, cancel)
            corruptGame(archive.event.revision == revision, "Current event revision mismatch")
            lifeSettlement(archive.provenance.afterAction, game, FailureCode.CORRUPT_DATA)
        }
        return stored
    }
    private suspend fun referenced(tx: DbTransaction, id: GameId, event: Long, revision: Long, code: FailureCode, cancel: CancelToken): GameEventArchive = try {
        events.archiveWithin(tx, id, event, revision, cancel)
    } catch (problem: GameStoreProblem) {
        if ((problem.status as? Outcome.Unavailable)?.reason == UnavailableReason.NOT_FOUND) rejectGame(code, "Referenced event missing")
        throw problem
    }
    private suspend fun nextEvent(tx: DbTransaction, id: GameId, cancel: CancelToken): Long {
        val rows = gameValue(tx.query(gameRequest("SELECT CASE WHEN typeof(MAX(event_id))='null' THEN NULL WHEN typeof(MAX(event_id))='integer' THEN MAX(event_id) ELSE X'FF' END AS maximum FROM life_event WHERE game_source=:source AND game_id=:id", *lifeIdentityParameters(id), maxRows = 1), cancel))
        corruptGame(rows.size == 1, "Missing event identity aggregate")
        val maximum = rows.single().gameOptionalLong("maximum") ?: return 0L
        corruptGame(maximum >= 0, "Invalid stored event identity")
        requireGame(maximum < Long.MAX_VALUE, FailureCode.INVALID_INPUT, "Event identity space exhausted")
        return maximum + 1
    }
    private fun checkpointBytes(checkpoint: GameCheckpoint): SnapshotBytes = gameValue(codec.encodeCheckpoint(checkpoint)).also {
        requireGame(it.size in 1..CHECKPOINT_BYTES, FailureCode.INVALID_INPUT, "Checkpoint exceeds byte budget")
    }
    private suspend fun cas(tx: DbTransaction, current: StoredLifeGame, next: GameCheckpoint, bytes: SnapshotBytes, at: UtcTimestamp, cancel: CancelToken) {
        val game = next.snapshot; val ended = (game.endedAt as? Knowledge.Known)?.value
        val result = gameValue(tx.execute(gameRequest("""UPDATE life_game SET revision=:next_revision,checkpoint_sequence=:next_sequence,checkpoint_payload=:payload,
            status=:status,ended_seconds=:ended_seconds,ended_nano=:ended_nano,updated_seconds=:seconds,updated_nano=:nano
            WHERE game_source=:source AND game_id=:id AND revision=:expected AND checkpoint_sequence=:sequence""",
            *lifeIdentityParameters(game.id), "next_revision" to gameNumber(game.revision.value), "next_sequence" to gameNumber(current.sequence + 1), "payload" to SqlValue.Blob(bytes),
            "status" to gameText(game.status.name), "ended_seconds" to (ended?.let { gameNumber(it.epochSeconds) } ?: SqlValue.Null), "ended_nano" to (ended?.let { gameNumber(it.nanoAdjustment.toLong()) } ?: SqlValue.Null),
            "seconds" to gameNumber(at.epochSeconds), "nano" to gameNumber(at.nanoAdjustment.toLong()), "expected" to gameNumber(current.checkpoint.snapshot.revision.value), "sequence" to gameNumber(current.sequence)), cancel))
        requireGame(result.affectedRows == 1L, FailureCode.STALE_REVISION, "Game CAS did not affect exactly one row")
    }
    private suspend fun insertEvent(tx: DbTransaction, event: GameEvent, bytes: SnapshotBytes, cancel: CancelToken) {
        val undo = (event.undoOfEventId as? Knowledge.Known)?.value
        gameOne(gameValue(tx.execute(gameRequest("""INSERT INTO life_event (game_source,game_id,event_id,revision,group_id,at_seconds,at_nano,undo_of_event_id,archive_payload)
            VALUES (:source,:id,:event,:revision,:group,:seconds,:nano,:undo,:archive)""", *lifeIdentityParameters(event.gameId), "event" to gameNumber(event.id), "revision" to gameNumber(event.revision.value),
            "group" to gameText(event.groupId.value), "seconds" to gameNumber(event.at.epochSeconds), "nano" to gameNumber(event.at.nanoAdjustment.toLong()), "undo" to (undo?.let(::gameNumber) ?: SqlValue.Null), "archive" to SqlValue.Blob(bytes)), cancel)))
    }
}
