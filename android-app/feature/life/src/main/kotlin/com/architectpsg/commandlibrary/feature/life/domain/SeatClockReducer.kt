package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.Clock
import com.architectpsg.commandlibrary.core.model.ClockAlert
import com.architectpsg.commandlibrary.core.model.ClockAnchor
import com.architectpsg.commandlibrary.core.model.ClockUndoPolicy
import com.architectpsg.commandlibrary.core.model.ClockZeroPolicy
import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.GameAction
import com.architectpsg.commandlibrary.core.model.GameSnapshot
import com.architectpsg.commandlibrary.core.model.GameStatus
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.SeatClockMode
import com.architectpsg.commandlibrary.core.model.SeatClockState
import com.architectpsg.commandlibrary.core.model.SeatTime
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.core.model.UnavailableReason
import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import kotlin.time.Duration

/**
 * One explicit time observation. [monotonic] is comparable only with anchors from the same [processSessionId];
 * [wall] is recorded for display and alerts and never used to measure elapsed time.
 */
internal data class ClockReading(val monotonic: Duration, val wall: UtcTimestamp, val processSessionId: EntityId) {
    fun anchor(): ClockAnchor = ClockAnchor(wall, monotonic, processSessionId)

    companion object {
        fun from(clock: Clock, processSessionId: EntityId): ClockReading =
            ClockReading(clock.monotonicNow(), clock.utcNow(), processSessionId)
    }
}

/**
 * Result of one clock calculation. [snapshot] keeps the input revision and event pointers. [alerts] are zero
 * transitions fired by this calculation and already recorded in `firedAlertIds`. [timedOutSeats] lists seats
 * that reached zero under [ClockZeroPolicy.ELIMINATE]; they are reported, not eliminated.
 */
internal data class ClockReduction(
    val snapshot: GameSnapshot,
    val alerts: SnapshotList<ClockAlert>,
    val timedOutSeats: SnapshotList<EntityId>,
)

/** [pausedByRecovery] is true when a running clock had to stop because its anchor belonged to an earlier process. */
internal data class RestartRecovery(val snapshot: GameSnapshot, val pausedByRecovery: Boolean)

internal data class SeatClockView(val seatId: EntityId, val remaining: Duration, val charging: Boolean)

/** Read-only live view. [zeroPending] means the charging seat has reached zero but no [SeatClockReducer.settle] recorded it yet. */
internal data class ClockView(
    val mode: SeatClockMode,
    val seats: SnapshotList<SeatClockView>,
    val activeSeat: EntityId?,
    val paused: Boolean,
    val untilZero: Duration?,
    val zeroPending: Boolean,
)

/**
 * Seat clock calculations for [SeatClockMode.CHESS] (per-seat bank) and [SeatClockMode.PER_TURN] (each turn starts
 * from the seat's initial time). Only the active seat is charged, by monotonic delta within one process session.
 * [SeatClockMode.SHARED] has no defined semantics in the core contract and is reported as unsupported.
 */
internal object SeatClockReducer {
    fun reduce(snapshot: GameSnapshot, action: GameAction, reading: ClockReading): Outcome<ClockReduction> = when (action) {
        is GameAction.SwitchTurn -> switchTurn(snapshot, action.seatId, reading)
        is GameAction.ClockPause -> setPaused(snapshot, action.paused, reading)
        else -> invalidInput("${action::class.simpleName} is not a clock action")
    }

    fun observe(snapshot: GameSnapshot, reading: ClockReading): Outcome<ClockView> {
        checkClock(snapshot)?.let { return it }
        checkReading(reading)?.let { return it }
        val clock = snapshot.clock
        if (clock.mode == SeatClockMode.NONE) return Outcome.Success(ClockView(clock.mode, SnapshotList.empty(), clock.activeSeat, clock.paused, null, false))
        val elapsed = if (clock.paused) Duration.ZERO else elapsedSince(clock, reading).valueOr { return it }
        val clamps = clock.zeroPolicy != ClockZeroPolicy.ALERT
        var untilZero: Duration? = null
        var zeroPending = false
        val seats = ArrayList<SeatClockView>(clock.seats.size)
        for (time in clock.seats) {
            if (clock.paused || time.seatId != clock.activeSeat) {
                seats += SeatClockView(time.seatId, time.remaining, false)
                continue
            }
            val live = time.remaining - elapsed
            if (!live.isFinite()) return invalidInput("Clock arithmetic for seat ${time.seatId.value} left the finite range")
            if (live > Duration.ZERO) untilZero = live
            zeroPending = time.remaining > Duration.ZERO && live <= Duration.ZERO
            seats += SeatClockView(time.seatId, if (clamps && live <= Duration.ZERO) Duration.ZERO else live, true)
        }
        return Outcome.Success(ClockView(clock.mode, SnapshotList.copyOf(seats), clock.activeSeat, clock.paused, untilZero, zeroPending))
    }

    /** Charges the active seat up to [reading] and fires a pending zero transition. Use for durable checkpoints and alert delivery. */
    fun settle(snapshot: GameSnapshot, reading: ClockReading): Outcome<ClockReduction> {
        checkClock(snapshot)?.let { return it }
        checkReading(reading)?.let { return it }
        return Outcome.Success(settleClock(snapshot, reading).valueOr { return it }.reduction(snapshot))
    }

    fun setPaused(snapshot: GameSnapshot, paused: Boolean, reading: ClockReading): Outcome<ClockReduction> {
        if (snapshot.status == GameStatus.COMPLETED) return invalidInput("Game is completed; reopen it before changing the clock")
        checkClock(snapshot)?.let { return it }
        checkReading(reading)?.let { return it }
        val clock = snapshot.clock
        if (clock.mode == SeatClockMode.NONE) return invalidInput("This game has no seat clock")
        if (clock.paused == paused) return invalidInput(if (paused) "Clock is already paused" else "Clock is already running")
        if (!paused) {
            if (clock.activeSeat == null) return invalidInput("Choose the active seat before starting the clock")
            // Resuming never measures time against the old anchor, so a paused anchor from an earlier process is safe to replace.
            return Outcome.Success(ClockReduction(snapshot.copy(clock = clock.copy(paused = false, anchor = reading.anchor())), SnapshotList.empty(), SnapshotList.empty()))
        }
        val settled = settleClock(snapshot, reading).valueOr { return it }
        return Outcome.Success(settled.copy(clock = settled.clock.copy(paused = true, anchor = reading.anchor())).reduction(snapshot))
    }

    /** Advances the turn to [seatId] (the same seat in a one-player game). A paused clock stays paused. */
    fun switchTurn(snapshot: GameSnapshot, seatId: EntityId, reading: ClockReading): Outcome<ClockReduction> {
        if (snapshot.status == GameStatus.COMPLETED) return invalidInput("Game is completed; reopen it before switching turns")
        checkClock(snapshot)?.let { return it }
        checkReading(reading)?.let { return it }
        if (snapshot.seats.none { it.id == seatId }) return invalidInput("Unknown seat ${seatId.value}")
        if (snapshot.turnNumber == Long.MAX_VALUE) return invalidInput("Turn number is exhausted")
        val turned = snapshot.copy(activeSeatId = seatId, turnNumber = snapshot.turnNumber + 1)
        if (snapshot.clock.mode == SeatClockMode.NONE) return Outcome.Success(ClockReduction(turned, SnapshotList.empty(), SnapshotList.empty()))
        val settled = settleClock(snapshot, reading).valueOr { return it }
        val seats = if (snapshot.clock.mode == SeatClockMode.PER_TURN) {
            settled.clock.seats.map { if (it.seatId == seatId) it.copy(remaining = it.initial) else it }
        } else settled.clock.seats
        val clock = settled.clock.copy(seats = SnapshotList.copyOf(seats), activeSeat = seatId, anchor = reading.anchor())
        return Outcome.Success(settled.copy(clock = clock).reduction(turned))
    }

    /**
     * Call once per new process before any other clock calculation. A running anchor from another process cannot be
     * measured, so the clock pauses and re-anchors with its durable balances unchanged; unpersisted time is not guessed
     * from wall time.
     */
    fun recoverAfterRestart(snapshot: GameSnapshot, reading: ClockReading): Outcome<RestartRecovery> {
        checkClock(snapshot)?.let { return it }
        checkReading(reading)?.let { return it }
        val clock = snapshot.clock
        val anchor = clock.anchor
        if (clock.mode == SeatClockMode.NONE || anchor == null || anchor.processSessionId == reading.processSessionId) {
            return Outcome.Success(RestartRecovery(snapshot, pausedByRecovery = false))
        }
        return Outcome.Success(RestartRecovery(snapshot.copy(clock = clock.copy(paused = true, anchor = reading.anchor())), pausedByRecovery = !clock.paused))
    }

    /**
     * Clock half of undoing the action that turned [before] into [current], at [reading]. Turn assignment (active seat,
     * turn number, and a per-turn refill) always returns to [before].
     *
     * [ClockUndoPolicy.RESTORE_TIME] continues the [before] timeline to [reading] as if the action never happened, so
     * before's active seat is charged from before's anchor. If before's running anchor belongs to an earlier process, that
     * time cannot be measured: the seat keeps the lower of its two durable balances (charged time is never refunded) and
     * the clock comes back paused, as [recoverAfterRestart] would leave it.
     *
     * [ClockUndoPolicy.KEEP_ELAPSED] keeps time charged where the clock charged it and reverts only active seat, pause flag
     * and turn. A pause that [current] received in another process session (restart recovery) is kept. A
     * reconstructed bank reaching zero also pauses under PAUSE/ELIMINATE and reports its new transition.
     *
     * A zero transition that [current] already fired is not fired again when the restored timeline replays it.
     */
    fun undo(before: GameSnapshot, current: GameSnapshot, reading: ClockReading): Outcome<ClockReduction> =
        undoWithAction(before, null, current, reading)

    /**
     * Use the immutable snapshot immediately after the undone action when [current] has since been settled,
     * paused, or recovered. Its original anchor provides action-time provenance; wall time never measures elapsed.
     * The three-argument overload requires the immediate after-action snapshot for a same-seat PER_TURN refill.
     */
    fun undo(before: GameSnapshot, afterAction: GameSnapshot, current: GameSnapshot, reading: ClockReading): Outcome<ClockReduction> =
        undoWithAction(before, afterAction, current, reading)

    private fun undoWithAction(before: GameSnapshot, afterAction: GameSnapshot?, current: GameSnapshot, reading: ClockReading): Outcome<ClockReduction> {
        checkClock(before)?.let { return it }
        checkClock(current)?.let { return it }
        checkReading(reading)?.let { return it }
        if (before.id != current.id) return invalidInput("Undo snapshots belong to different games")
        if (before.clock.mode != current.clock.mode) return invalidInput("Undo cannot cross a clock mode change")
        if (before.clock.seats.map { it.seatId }.toSet() != current.clock.seats.map { it.seatId }.toSet()) return invalidInput("Undo cannot cross a clock seat change")
        if (afterAction != null) {
            checkClock(afterAction)?.let { return it }
            if (afterAction.id != current.id || afterAction.turnNumber != current.turnNumber || afterAction.clock.activeSeat != current.clock.activeSeat ||
                afterAction.clock.mode != current.clock.mode || afterAction.clock.seats.map { it.seatId }.toSet() != current.clock.seats.map { it.seatId }.toSet()) {
                return invalidInput("Undo action snapshot does not match the current clock timeline")
            }
        }
        val restored = before.copy(revision = current.revision, lastEventId = current.lastEventId)
        if (current.clock.mode == SeatClockMode.NONE) return Outcome.Success(ClockReduction(restored, SnapshotList.empty(), SnapshotList.empty()))
        val fired = current.clock.firedAlertIds
        val settled = when (current.clock.undoPolicy) {
            ClockUndoPolicy.RESTORE_TIME -> {
                val continued = settleClock(measurableBefore(before, current, reading), reading).valueOr { return it }
                val newAlerts = continued.alerts.filter { it.id !in fired }
                continued.copy(
                    clock = continued.clock.copy(anchor = reading.anchor(), firedAlertIds = SnapshotList.copyOf(fired + newAlerts.map { it.id })),
                    alerts = newAlerts,
                    timedOut = continued.timedOut.filter { seat -> newAlerts.any { it.seatId == seat } },
                )
            }
            ClockUndoPolicy.KEEP_ELAPSED -> {
                val charged = settleClock(current, reading).valueOr { return it }
                val reverted = revertTurnRefill(before, afterAction, current, charged, reading).valueOr { return it }
                val recoveryPause = current.clock.paused && current.clock.anchor?.processSessionId != before.clock.anchor?.processSessionId
                reverted.copy(
                    clock = reverted.clock.copy(
                        activeSeat = before.clock.activeSeat,
                        paused = before.clock.paused || recoveryPause || reverted.refillStopped,
                        anchor = reading.anchor(),
                    ),
                )
            }
        }
        return Outcome.Success(settled.reduction(restored))
    }

    /** [before] as a timeline this process can continue; see [undo] for a running anchor from an earlier process. */
    private fun measurableBefore(before: GameSnapshot, current: GameSnapshot, reading: ClockReading): GameSnapshot {
        val clock = before.clock
        if (clock.paused || clock.anchor?.processSessionId == reading.processSessionId) return before
        val active = clock.activeSeat
        val durable = current.clock.seats.firstOrNull { it.seatId == active }?.remaining
        val seats = clock.seats.map { if (it.seatId == active && durable != null && durable < it.remaining) it.copy(remaining = durable) else it }
        return before.copy(clock = clock.copy(seats = SnapshotList.copyOf(seats), paused = true, anchor = reading.anchor()))
    }

    /**
     * Undoing a PER_TURN switch removes its refill while retaining both the outgoing settlement (when the
     * same seat switches to itself) and the charge since the refill. Reconstructed zero transitions follow
     * the same policy as a normal settlement, without reporting a transition already fired by this action.
     */
    private fun revertTurnRefill(before: GameSnapshot, afterAction: GameSnapshot?, current: GameSnapshot, charged: Settled, reading: ClockReading): Outcome<Settled> {
        val refilled = current.clock.activeSeat
        if (current.clock.mode != SeatClockMode.PER_TURN || current.turnNumber == before.turnNumber || refilled == null) return Outcome.Success(charged)
        var prior = before.clock.seats.firstOrNull { it.seatId == refilled } ?: return Outcome.Success(charged)
        val action = afterAction ?: current
        val switchAnchor = action.clock.anchor
        if (refilled == before.clock.activeSeat && !before.clock.paused) {
            val actionBank = action.clock.seats.single { it.seatId == refilled }
            if (switchAnchor == null || switchAnchor.processSessionId != before.clock.anchor?.processSessionId || actionBank.remaining != actionBank.initial) {
                return invalidInput("Same-seat refill undo requires the original after-action snapshot")
            }
            val atSwitch = ClockReading(switchAnchor.monotonicElapsed, switchAnchor.wallTime, switchAnchor.processSessionId)
            prior = settleClock(before, atSwitch).valueOr { return it }.clock.seats.single { it.seatId == refilled }
        }
        val time = charged.clock.seats.single { it.seatId == refilled }
        val remaining = prior.remaining - (time.initial - time.remaining)
        if (!remaining.isFinite()) return invalidInput("Clock arithmetic for seat ${refilled.value} left the finite range")
        val clamp = charged.clock.zeroPolicy != ClockZeroPolicy.ALERT && remaining <= Duration.ZERO
        val alreadyReported = charged.clock.firedAlertIds.any { id ->
            id !in before.clock.firedAlertIds && id.value.startsWith("clock-zero/${refilled.value}/turn-${current.turnNumber}/")
        }
        val alerts = ArrayList(charged.alerts)
        val timedOut = ArrayList(charged.timedOut)
        if (prior.remaining > Duration.ZERO && remaining <= Duration.ZERO && !alreadyReported) {
            val occurrence = (1..charged.clock.firedAlertIds.size + 1).first { zeroAlertId(refilled, current.turnNumber, it) !in charged.clock.firedAlertIds }
            alerts += ClockAlert(zeroAlertId(refilled, current.turnNumber, occurrence), current.id, refilled, reading.wall, null)
            if (charged.clock.zeroPolicy == ClockZeroPolicy.ELIMINATE) timedOut += refilled
        }
        val seats = charged.clock.seats.map { if (it.seatId == refilled) it.copy(remaining = if (clamp) Duration.ZERO else remaining) else it }
        return Outcome.Success(charged.copy(
            clock = charged.clock.copy(seats = SnapshotList.copyOf(seats), firedAlertIds = SnapshotList.copyOf(charged.clock.firedAlertIds + alerts.drop(charged.alerts.size).map { it.id })),
            alerts = alerts,
            timedOut = timedOut,
            refillStopped = clamp,
        ))
    }

    private data class Settled(val clock: SeatClockState, val alerts: List<ClockAlert>, val timedOut: List<EntityId>, val refillStopped: Boolean = false) {
        fun reduction(base: GameSnapshot): ClockReduction =
            ClockReduction(base.copy(clock = clock), SnapshotList.copyOf(alerts), SnapshotList.copyOf(timedOut))
    }

    /**
     * Alert ID for the [occurrence]th zero transition of a seat in a turn. Settling picks the first occurrence not yet in
     * `firedAlertIds`, so every real firing is unique (a redone turn alerts again), while an undo replay of the same
     * timeline derives the same ID and is recognised as already fired.
     */
    internal fun zeroAlertId(seatId: EntityId, turnNumber: Long, occurrence: Int): EntityId =
        EntityId("clock-zero/${seatId.value}/turn-$turnNumber/$occurrence")

    private fun settleClock(snapshot: GameSnapshot, reading: ClockReading): Outcome<Settled> {
        val clock = snapshot.clock
        if (clock.mode == SeatClockMode.NONE || clock.paused) return Outcome.Success(Settled(clock, emptyList(), emptyList()))
        val elapsed = elapsedSince(clock, reading).valueOr { return it }
        val active = clock.activeSeat ?: return corruptData("Running clock has no active seat")
        val time = clock.seats.firstOrNull { it.seatId == active } ?: return corruptData("Clock active seat ${active.value} has no clock entry")
        val next = time.remaining - elapsed
        if (!next.isFinite()) return invalidInput("Clock arithmetic for seat ${active.value} left the finite range")
        val alerts = ArrayList<ClockAlert>()
        val timedOut = ArrayList<EntityId>()
        if (time.remaining > Duration.ZERO && next <= Duration.ZERO) {
            val occurrence = (1..clock.firedAlertIds.size + 1).first { zeroAlertId(active, snapshot.turnNumber, it) !in clock.firedAlertIds }
            alerts += ClockAlert(zeroAlertId(active, snapshot.turnNumber, occurrence), snapshot.id, active, reading.wall, null)
            if (clock.zeroPolicy == ClockZeroPolicy.ELIMINATE) timedOut += active
        }
        // A clamping policy holds a seat at zero whenever it would run below it, including a seat resumed at zero.
        val clamp = clock.zeroPolicy != ClockZeroPolicy.ALERT && next <= Duration.ZERO
        val remaining = if (clamp) Duration.ZERO else next
        val paused = clamp
        val seats = clock.seats.map { if (it.seatId == active) SeatTime(it.seatId, it.initial, remaining) else it }
        return Outcome.Success(
            Settled(
                clock.copy(seats = SnapshotList.copyOf(seats), paused = paused, anchor = reading.anchor(), firedAlertIds = SnapshotList.copyOf(clock.firedAlertIds + alerts.map { it.id })),
                alerts,
                timedOut,
            ),
        )
    }

    /** Elapsed time of a running clock. Another process's monotonic origin is never reused. */
    private fun elapsedSince(clock: SeatClockState, reading: ClockReading): Outcome<Duration> {
        val anchor = clock.anchor ?: return corruptData("Running clock has no anchor")
        if (!reading.monotonic.isFinite()) return invalidInput("Monotonic reading must be finite")
        if (anchor.processSessionId != reading.processSessionId) {
            return invalidInput("Running clock was anchored by process session ${anchor.processSessionId.value}; recover it after restart first")
        }
        val elapsed = reading.monotonic - anchor.monotonicElapsed
        if (elapsed.isNegative()) return invalidInput("Monotonic reading ${reading.monotonic} precedes anchor ${anchor.monotonicElapsed}")
        if (!elapsed.isFinite()) return invalidInput("Elapsed clock time left the finite range")
        return Outcome.Success(elapsed)
    }

    private fun checkReading(reading: ClockReading): Outcome<Nothing>? =
        if (reading.monotonic.isFinite()) null else invalidInput("Monotonic reading must be finite")

    /** Structural checks shared by every calculation; returns null when the clock is usable. */
    private fun checkClock(snapshot: GameSnapshot): Outcome<Nothing>? {
        val seats = SeatIndex.of(snapshot).valueOr { return it }
        val clock = snapshot.clock
        if (clock.mode != snapshot.settings.clockMode) return corruptData("Clock mode ${clock.mode} disagrees with game setting ${snapshot.settings.clockMode}")
        when (clock.mode) {
            SeatClockMode.NONE -> return null
            SeatClockMode.SHARED -> return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "SHARED seat clock semantics are not defined by the core contract")
            SeatClockMode.CHESS, SeatClockMode.PER_TURN -> Unit
        }
        val timed = HashSet<EntityId>()
        for (time in clock.seats) {
            if (!timed.add(time.seatId)) return corruptData("Duplicate clock entry for seat ${time.seatId.value}")
            if (!seats.contains(time.seatId)) return corruptData("Clock entry references unknown seat ${time.seatId.value}")
            if (!time.initial.isFinite() || !time.remaining.isFinite()) return corruptData("Clock time for seat ${time.seatId.value} is not finite")
        }
        seats.seats.keys.firstOrNull { it !in timed }?.let { return corruptData("Seat ${it.value} has no clock entry") }
        clock.activeSeat?.let { if (it !in timed) return corruptData("Clock active seat ${it.value} is not a game seat") }
        clock.anchor?.let { if (!it.monotonicElapsed.isFinite()) return corruptData("Clock anchor is not finite") }
        if (!clock.paused) {
            if (clock.activeSeat == null) return corruptData("Running clock has no active seat")
            if (clock.anchor == null) return corruptData("Running clock has no anchor")
            if (snapshot.status == GameStatus.COMPLETED) return corruptData("Completed game has a running clock; finishing must pause it first")
        }
        return null
    }
}
