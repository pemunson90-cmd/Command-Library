package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Checked callback-scoped reads. Service must await local work and never publish from this callback. */
public class LifeActionContext internal constructor(
    public val current: StoredLifeGame, public val eventId: Long,
    private val tx: DbTransaction, private val reader: SqlLifeEventReader, private val cancel: CancelToken,
) {
    private val access = Mutex()
    private var active = true
    private var failure: Outcome<Nothing>? = null

    public suspend fun archive(eventId: Long): Outcome<GameEventArchive> = checked {
        requireGame(eventId >= 0, FailureCode.INVALID_INPUT, "Negative event identity")
        reader.archiveWithin(tx, current.checkpoint.snapshot.id, eventId, current.checkpoint.snapshot.revision.value, cancel)
    }

    /** Descending revision, never imported numeric event ID. Unknown intent requires explicit repair. */
    public suspend fun latestUndoTarget(): Outcome<GameEventArchive> = checked {
        val game = current.checkpoint.snapshot
        // Validate excluded undo projections before using them to choose a different original action.
        val invalid = gameValue(tx.query(gameRequest("""SELECT 1 AS invalid FROM life_event e
            WHERE e.game_source=:source AND e.game_id=:id AND e.undo_of_event_id IS NOT NULL AND (
            typeof(e.undo_of_event_id)!='integer' OR e.undo_of_event_id<0 OR typeof(e.event_id)!='integer' OR e.event_id<0
            OR typeof(e.revision)!='integer' OR e.revision<1 OR e.revision>:revision
            OR NOT EXISTS (SELECT 1 FROM life_event t WHERE t.game_source=e.game_source AND t.game_id=e.game_id
                AND t.event_id=e.undo_of_event_id AND typeof(t.revision)='integer' AND t.revision>=1 AND t.revision<e.revision))
            LIMIT 1""", *lifeIdentityParameters(game.id), "revision" to gameNumber(game.revision.value), maxRows = 1), cancel))
        corruptGame(invalid.isEmpty(), "Malformed historical undo projection")
        val rows = gameValue(tx.query(gameRequest("""SELECT ${LifeEventMetadata.columns} FROM life_event e
            WHERE e.game_source=:source AND e.game_id=:id AND e.undo_of_event_id IS NULL
            AND NOT EXISTS (SELECT 1 FROM life_event u WHERE u.game_source=e.game_source AND u.game_id=e.game_id AND u.undo_of_event_id=e.event_id)
            ORDER BY e.revision DESC LIMIT 1""", *lifeIdentityParameters(game.id), maxRows = 1), cancel))
        if (rows.isEmpty()) throw GameStoreProblem(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "No eligible original action to undo"))
        corruptGame(rows.size == 1, "Multiple selected undo targets")
        val meta = LifeEventMetadata.read(rows.single(), game.revision.value)
        val archive = reader.archiveWithin(tx, game.id, meta.key.eventId, game.revision.value, cancel)
        when (archive.event.undoOfEventId) {
            is Knowledge.Unknown -> throw GameStoreProblem(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Undo intent requires explicit repair"))
            is Knowledge.Known -> rejectGame(FailureCode.CORRUPT_DATA, "Selected target is an undo record")
            is Knowledge.Absent -> archive
        }
    }

    /** Success(null) is checked absence; a non-success still latches rollback. */
    public suspend fun randomPredecessor(requestId: EntityId): Outcome<LifeRandomHistory?> = checked {
        readRandomPredecessor(tx, reader, current.checkpoint.snapshot, requestId, cancel)
    }

    private suspend fun <T> checked(read: suspend () -> T): Outcome<T> = access.withLock {
        cancel.throwIfCancelled()
        if (!active) return@withLock Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, "Action context is closed", SnapshotList.empty()))
        try { Outcome.Success(read()) } catch (problem: GameStoreProblem) {
            if (failure == null) failure = problem.status
            problem.status
        }
    }

    /** Drain any in-flight read even when the callback is cancelling; no reads overlap writes/commit. */
    internal suspend fun close(): Outcome<Nothing>? = withContext(NonCancellable) {
        access.withLock { active = false; failure }
    }
}
