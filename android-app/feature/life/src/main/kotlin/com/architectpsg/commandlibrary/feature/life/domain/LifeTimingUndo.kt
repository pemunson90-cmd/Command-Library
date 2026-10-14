package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import kotlin.time.Duration

/** Pure timing fields, deliberately not an independently persistable game snapshot. */
internal data class TimingPatch(
    val clock: SeatClockState, val elapsed: ElapsedTimerState,
    val activeSeatId: EntityId?, val turnNumber: Long, val alerts: SnapshotList<GameAlert>,
)

internal data class TimingUndoReduction(
    val settledCurrent: GameSnapshot, val patch: TimingPatch,
    val alerts: SnapshotList<GameAlert>, val timedOutSeats: SnapshotList<EntityId>, val sharedTimedOut: Boolean,
) {
    /** Caller first restores reversible semantic state, including status/results for undoFinish. */
    fun compose(base: GameSnapshot): Outcome<TimingReduction> {
        if (base.id != settledCurrent.id || roster(base) != roster(settledCurrent)) return invalidInput("Undo timing base has different game/immutable roster identity")
        val result = base.copy(clock = patch.clock, elapsed = patch.elapsed, activeSeatId = patch.activeSeatId,
            turnNumber = patch.turnNumber, alerts = patch.alerts)
        SnapshotGraphValidator.validate(result).valueOr { return it }
        return Outcome.Success(TimingReduction(result, alerts, timedOutSeats, sharedTimedOut))
    }
}

private fun roster(game: GameSnapshot): Map<EntityId, Map<EntityId, CardIdentity>> =
    game.seats.associate { seat -> seat.id to seat.commanders.associate { it.id to it.identity } }

internal object LifeTimingUndo {
    fun undo(original: GameActionProvenance, action: GameAction, current: GameSnapshot, reading: GameTimeObservation): Outcome<TimingUndoReduction> {
        val before = original.before
        val after = original.afterAction
        SnapshotGraphValidator.validate(after).valueOr { return it }
        SnapshotGraphValidator.validate(current).valueOr { return it }
        if (before.id != current.id || roster(before) != roster(after) || roster(after) != roster(current)) return invalidInput("Undo timing snapshots have different immutable identities")
        val actions = if (action is GameAction.Group) action.actions.toList() else listOf(action)
        if (actions.isEmpty() || actions.any { it is GameAction.Group } || actions.count { it is GameAction.SwitchTurn } > 1) {
            return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Undo requires a flat atomic group with at most one turn switch")
        }
        val turn = actions.filterIsInstance<GameAction.SwitchTurn>().singleOrNull()
        if (after.turnNumber != before.turnNumber + (if (turn == null) 0 else 1) ||
            (turn != null && (before.turnNumber == Long.MAX_VALUE || after.activeSeatId != turn.seatId || after.elapsed.turnElapsed != Duration.ZERO)) ||
            (turn == null && (after.activeSeatId != before.activeSeatId || after.elapsed.turnLineageId != before.elapsed.turnLineageId)) ||
            current.turnNumber != after.turnNumber || current.activeSeatId != after.activeSeatId || current.elapsed.turnLineageId != after.elapsed.turnLineageId) {
            return invalidInput("Original action does not match the immutable/current turn timeline")
        }
        if (turn != null && (after.elapsed.turnLineageId !is Knowledge.Known || after.elapsed.turnLineageId == before.elapsed.turnLineageId)) {
            return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Turn undo requires explicit distinct action-time lineage")
        }
        for (game in listOf(after, current)) {
            if (game.settings.elapsedTimers != before.settings.elapsedTimers || game.clock.mode != before.clock.mode ||
                game.clock.zeroPolicy != before.clock.zeroPolicy || game.clock.undoPolicy != before.clock.undoPolicy ||
                game.clock.seats.associate { it.seatId to it.initial } != before.clock.seats.associate { it.seatId to it.initial } ||
                game.clock.shared?.initial != before.clock.shared?.initial) return invalidInput("Undo cannot cross timing configuration changes")
        }
        if (current.clock.activeSeat != after.clock.activeSeat || (after.clock.paused && !current.clock.paused) ||
            (after.elapsed.paused && !current.elapsed.paused) || current.elapsed.gameElapsed < after.elapsed.gameElapsed ||
            current.elapsed.turnElapsed < after.elapsed.turnElapsed) return invalidInput("Current timers do not descend from original action")
        if (turn != null && before.clock.mode == SeatClockMode.PER_TURN) {
            val refilled = after.clock.seats.singleOrNull { it.seatId == turn.seatId }
            if (refilled == null || refilled.remaining != refilled.initial) return invalidInput("Original turn target does not contain its refill")
        }
        // Normalize a copy only at the immutable ORIGINAL action observation; never at undo-now.
        val normalized = LifeTimingEngine.settle(before, original.reading).valueOr { return it }.snapshot
        if (after.elapsed.gameElapsed < normalized.elapsed.gameElapsed) return invalidInput("Original action refunded elapsed game time")
        val settled = LifeTimingEngine.settle(current, reading).valueOr { return it }
        val live = settled.snapshot
        val newClockAlerts: List<GameAlert>
        val clock: SeatClockState
        val newTimedOut: List<EntityId>
        val newSharedTimeout: Boolean
        if (before.clock.mode == SeatClockMode.SHARED) {
            val restored = shared(normalized, live, reading).valueOr { return it }
            clock = restored.snapshot.clock
            newClockAlerts = restored.alerts
            newTimedOut = emptyList()
            newSharedTimeout = restored.sharedTimedOut
        } else {
            val undone = SeatClockReducer.undoWithTurnTarget(normalized, after, live,
                ClockReading(reading.monotonic, reading.wall, reading.processSessionId), turn?.seatId).valueOr { return it }
            var restored = undone.snapshot.clock
            if (!before.clock.paused && before.clock.anchor?.processSessionId != reading.processSessionId) restored = restored.copy(paused = true)
            if (restored.zeroPolicy != ClockZeroPolicy.ALERT && restored.activeSeat != null &&
                restored.seats.any { it.seatId == restored.activeSeat && it.remaining <= Duration.ZERO }) restored = restored.copy(paused = true)
            clock = restored
            newClockAlerts = undone.alerts.map { GameAlert(it.id, it.gameId, GameAlertTarget.SeatClock(it.seatId), it.firedAt, it.acknowledgedAt) }
            newTimedOut = undone.timedOutSeats
            newSharedTimeout = false
        }
        val priorElapsed = normalized.elapsed
        val recoveryPause = live.elapsed.paused && live.elapsed.anchor?.processSessionId != priorElapsed.anchor?.processSessionId
        val unmeasurable = !priorElapsed.paused && priorElapsed.anchor?.processSessionId != reading.processSessionId
        val elapsed = live.elapsed.copy(paused = priorElapsed.paused || recoveryPause || unmeasurable,
            turnElapsed = if (turn != null) priorElapsed.turnElapsed else live.elapsed.turnElapsed,
            turnLineageId = if (turn != null) priorElapsed.turnLineageId else live.elapsed.turnLineageId,
            anchor = ClockAnchor(reading.wall, reading.monotonic, reading.processSessionId))
        if (!elapsed.paused && elapsed.turnLineageId !is Knowledge.Known) return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Cannot restore running elapsed timer without known lineage")
        val existing = live.alerts.associateBy { it.id }
        if (newClockAlerts.any { it.id in existing }) return corruptData("Undo countdown occurrence conflicts with recorded alert")
        val allAlerts = SnapshotList.copyOf(live.alerts + newClockAlerts)
        return Outcome.Success(TimingUndoReduction(live, TimingPatch(clock, elapsed, normalized.activeSeatId, normalized.turnNumber, allAlerts),
            SnapshotList.copyOf(settled.alerts + newClockAlerts), SnapshotList.copyOf((settled.timedOutSeats + newTimedOut).distinct()),
            settled.sharedTimedOut || newSharedTimeout))
    }

    private fun shared(before: GameSnapshot, current: GameSnapshot, reading: GameTimeObservation): Outcome<TimingReduction> {
        val originalClock = before.clock
        val live = current.clock
        val anchor = ClockAnchor(reading.wall, reading.monotonic, reading.processSessionId)
        if (live.undoPolicy == ClockUndoPolicy.KEEP_ELAPSED) {
            val recoveryPause = live.paused && live.anchor?.processSessionId != originalClock.anchor?.processSessionId
            val unmeasurable = !originalClock.paused && originalClock.anchor?.processSessionId != reading.processSessionId
            val exhausted = live.zeroPolicy != ClockZeroPolicy.ALERT && live.shared!!.remaining <= Duration.ZERO
            val clock = live.copy(paused = originalClock.paused || recoveryPause || unmeasurable || exhausted,
                activeSeat = originalClock.activeSeat, anchor = anchor)
            return Outcome.Success(TimingReduction(current.copy(clock = clock), SnapshotList.empty(), SnapshotList.empty(), false))
        }
        var restored = originalClock
        if (!restored.paused && restored.anchor?.processSessionId != reading.processSessionId) {
            restored = restored.copy(shared = restored.shared!!.copy(remaining = minOf(restored.shared!!.remaining, live.shared!!.remaining)), paused = true, anchor = anchor)
        }
        val replay = LifeTimingEngine.settle(before.copy(clock = restored, elapsed = before.elapsed.copy(paused = true)), reading).valueOr { return it }
        val fired = live.firedAlertIds.toHashSet()
        val alerts = replay.alerts.filter { it.id !in fired }
        val clock = replay.snapshot.clock.copy(anchor = anchor, firedAlertIds = SnapshotList.copyOf(live.firedAlertIds + alerts.map { it.id }))
        return Outcome.Success(TimingReduction(current.copy(clock = clock), SnapshotList.copyOf(alerts), SnapshotList.empty(),
            replay.sharedTimedOut && alerts.any { it.target == GameAlertTarget.SharedCountdown }))
    }
}
