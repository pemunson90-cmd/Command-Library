package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*

/** Caller supplies the transaction's allocation. The outer orchestrator must reuse it, never increment again. */
internal data class ResultEventAllocation(val eventId: Long, val nextRevision: Revision, val freshResultId: EntityId? = null)
internal data class ResultReduction(
    val settledBefore: GameSnapshot, val snapshot: GameSnapshot, val action: GameAction,
    val alerts: SnapshotList<GameAlert>, val timedOutSeats: SnapshotList<EntityId>, val sharedTimedOut: Boolean,
)

/** Coherent proposed after-state only; no ID allocation, durable commit, stats publication or semantic undo. */
internal object LifeResultEngine {
    fun reduce(snapshot: GameSnapshot, action: GameAction, reading: GameTimeObservation, allocation: ResultEventAllocation): Outcome<ResultReduction> {
        SnapshotGraphValidator.validate(snapshot).valueOr { return it }
        if (allocation.eventId < 0 || snapshot.revision.value == Long.MAX_VALUE || allocation.nextRevision.value != snapshot.revision.value + 1) return invalidInput("Result action needs the next valid event/revision allocation")
        val reason = when (action) {
            is GameAction.Finish -> action.reason
            is GameAction.FinishWithOutcomes -> action.reason
            is GameAction.Reopen -> action.reason
            else -> return invalidInput("Action is not a result confirmation or reopen")
        }
        if (reason.isBlank()) return invalidInput("A new result decision needs an explicit nonblank reason")
        val existing = when (val known = snapshot.result) {
            is Knowledge.Known -> known.value
            is Knowledge.Absent -> null
            is Knowledge.Unknown -> return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Result knowledge requires explicit repair")
        }
        if (existing != null && allocation.freshResultId != null && allocation.freshResultId != existing.id) return invalidInput("Recorded result identity cannot be replaced")
        val outcomes: SnapshotList<SeatResult>?
        val id: EntityId
        if (action is GameAction.Reopen) {
            if (snapshot.status != GameStatus.COMPLETED) return invalidInput("Only a completed game can reopen")
            if (existing == null) return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Completed game needs a known result before retraction")
            id = existing.id
            outcomes = null
        } else {
            if (snapshot.status == GameStatus.COMPLETED) return invalidInput("Reopen the game before confirming another result")
            id = existing?.id ?: allocation.freshResultId ?: return invalidInput("First confirmation requires an allocated result identity")
            outcomes = finishOutcomes(snapshot, action).valueOr { return it }
        }
        val settled = LifeTimingEngine.settle(snapshot, reading).valueOr { return it }
        val current = settled.snapshot
        val reopened = action is GameAction.Reopen
        val result = if (reopened) existing!!.copy(revision = allocation.nextRevision, retracted = true) else GameResult(
            id, snapshot.id, SnapshotList.copyOf(outcomes!!.filter { it.result == SeatResultKind.WIN }.map { it.seatId }),
            reading.wall, reason, allocation.nextRevision, false, outcomes)
        val next = current.copy(revision = allocation.nextRevision, lastEventId = Knowledge.Known(allocation.eventId),
            status = if (reopened) GameStatus.PAUSED else GameStatus.COMPLETED,
            endedAt = if (reopened) Knowledge.Absent("Reopened by explicit action") else Knowledge.Known(reading.wall),
            result = Knowledge.Known(result), clock = current.clock.copy(paused = true), elapsed = current.elapsed.copy(paused = true))
        SnapshotGraphValidator.validate(next).valueOr { return it }
        return Outcome.Success(ResultReduction(current, next, action, settled.alerts, settled.timedOutSeats, settled.sharedTimedOut))
    }

    private fun finishOutcomes(snapshot: GameSnapshot, action: GameAction): Outcome<SnapshotList<SeatResult>> {
        val seats = snapshot.seats.map { it.id }.toSet()
        val rows = when (action) {
            is GameAction.FinishWithOutcomes -> action.seatResults
            is GameAction.Finish -> {
                val winners = action.winnerSeatIds.toHashSet()
                if (winners.size != action.winnerSeatIds.size || winners.any { it !in seats }) return invalidInput("Winner identities must be distinct game seats")
                SnapshotList.copyOf(snapshot.seats.sortedBy { it.order }.map { seat ->
                    SeatResult(seat.id, if (winners.isEmpty()) SeatResultKind.DRAW else if (seat.id in winners) SeatResultKind.WIN else SeatResultKind.LOSS, null)
                })
            }
            else -> return invalidInput("Action does not confirm outcomes")
        }
        if (rows.size != seats.size || rows.map { it.seatId }.toSet() != seats) return invalidInput("Confirmation needs exactly one outcome per game seat")
        return Outcome.Success(rows)
    }
}
