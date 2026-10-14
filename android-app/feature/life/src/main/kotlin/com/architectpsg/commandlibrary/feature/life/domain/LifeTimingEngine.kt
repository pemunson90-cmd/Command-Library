package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.time.Duration

/** Pure timing delta; no revision/event/result or external notification effects. */
internal data class TimingReduction(
    val snapshot: GameSnapshot,
    val alerts: SnapshotList<GameAlert>,
    val timedOutSeats: SnapshotList<EntityId>,
    val sharedTimedOut: Boolean,
)

internal data class TimingRecovery(val snapshot: GameSnapshot, val changed: Boolean, val pausedByRecovery: Boolean)

internal object LifeTimingEngine {
    fun undo(provenance: GameActionProvenance, originalAction: GameAction, current: GameSnapshot, reading: GameTimeObservation): Outcome<TimingUndoReduction> =
        LifeTimingUndo.undo(provenance, originalAction, current, reading)

    fun setCountdownPaused(snapshot: GameSnapshot, paused: Boolean, reading: GameTimeObservation): Outcome<TimingReduction> =
        LifeTimingControls.countdownPause(snapshot, paused, reading)
    fun setElapsedPaused(snapshot: GameSnapshot, paused: Boolean, reading: GameTimeObservation): Outcome<TimingReduction> =
        LifeTimingControls.elapsedPause(snapshot, paused, reading)
    fun switchTurn(snapshot: GameSnapshot, seat: EntityId, lineage: EntityId, reading: GameTimeObservation): Outcome<TimingReduction> =
        LifeTimingControls.switchTurn(snapshot, seat, lineage, reading)
    fun passClock(snapshot: GameSnapshot, seat: EntityId, reading: GameTimeObservation): Outcome<TimingReduction> =
        LifeTimingControls.passClock(snapshot, seat, reading)
    fun recover(snapshot: GameSnapshot, reading: GameTimeObservation): Outcome<TimingRecovery> =
        LifeTimingControls.recover(snapshot, reading)

    fun settle(snapshot: GameSnapshot, reading: GameTimeObservation): Outcome<TimingReduction> {
        SnapshotGraphValidator.validate(snapshot).valueOr { return it }
        val originalElapsed = snapshot.elapsed
        if (!originalElapsed.paused && originalElapsed.turnLineageId !is Knowledge.Known) return missingLineage()
        val anchor = ClockAnchor(reading.wall, reading.monotonic, reading.processSessionId)
        val alerts = ArrayList<GameAlert>()
        var sharedTimedOut = false
        var timedOut: SnapshotList<EntityId> = SnapshotList.empty()
        val clock = when (snapshot.clock.mode) {
            SeatClockMode.SHARED -> {
                val clock = snapshot.clock
                if (clock.paused) clock else {
                    val elapsed = delta(clock.anchor, reading).valueOr { return it }
                    val shared = clock.shared!! // graph guard proved the bank
                    val remaining = shared.remaining - elapsed
                    if (!remaining.isFinite()) return invalidInput("Shared countdown arithmetic left the finite range")
                    val crossing = shared.remaining > Duration.ZERO && remaining <= Duration.ZERO
                    val fired = clock.firedAlertIds.toHashSet()
                    val id = if (crossing) {
                        val occurrence = (1..fired.size + 1).first { EntityId("clock-shared-zero-v1/$it") !in fired }
                        EntityId("clock-shared-zero-v1/$occurrence")
                    } else null
                    if (id != null) alerts += GameAlert(id, snapshot.id, GameAlertTarget.SharedCountdown, reading.wall, null)
                    sharedTimedOut = crossing && clock.zeroPolicy == ClockZeroPolicy.ELIMINATE
                    val clamp = clock.zeroPolicy != ClockZeroPolicy.ALERT && remaining <= Duration.ZERO
                    clock.copy(shared = shared.copy(remaining = if (clamp) Duration.ZERO else remaining),
                        paused = clamp, anchor = anchor,
                        firedAlertIds = if (id == null) clock.firedAlertIds else SnapshotList.copyOf(clock.firedAlertIds + id))
                }
            }
            else -> {
                val settled = SeatClockReducer.settle(snapshot, ClockReading(reading.monotonic, reading.wall, reading.processSessionId)).valueOr { return it }
                timedOut = settled.timedOutSeats
                alerts += settled.alerts.map { GameAlert(it.id, it.gameId, GameAlertTarget.SeatClock(it.seatId), it.firedAt, it.acknowledgedAt) }
                settled.snapshot.clock
            }
        }
        val elapsed = if (originalElapsed.paused) originalElapsed else {
            val charged = delta(originalElapsed.anchor, reading).valueOr { return it }
            val gameTime = originalElapsed.gameElapsed + charged
            val turnTime = originalElapsed.turnElapsed + if (snapshot.activeSeatId == null) Duration.ZERO else charged
            if (!gameTime.isFinite() || !turnTime.isFinite()) return invalidInput("Elapsed timer arithmetic left the finite range")
            val settings = snapshot.settings.elapsedTimers
            for ((target, threshold, before, after) in listOf(
                Warning(GameAlertTarget.GameTimer, settings.gameWarning, originalElapsed.gameElapsed, gameTime),
                Warning(GameAlertTarget.TurnTimer, settings.turnWarning, originalElapsed.turnElapsed, turnTime),
            )) {
                if (threshold != null && before < threshold && after >= threshold) {
                    val lineage = if (target == GameAlertTarget.TurnTimer) (originalElapsed.turnLineageId as Knowledge.Known).value.value else ""
                    val id = warningId(snapshot.id, target, lineage, threshold)
                    // Reinstating a historical lineage never erases or repeats its warning occurrence.
                    val prior = snapshot.alerts.firstOrNull { it.id == id }
                    if (prior == null) alerts += GameAlert(id, snapshot.id, target, reading.wall, null)
                    else if (prior.target != target || prior.gameId != snapshot.id) return corruptData("Warning identity conflicts with recorded occurrence")
                }
            }
            originalElapsed.copy(gameElapsed = gameTime, turnElapsed = turnTime, anchor = anchor)
        }
        val existing = snapshot.alerts.associateBy { it.id }
        if (alerts.any { it.id in existing }) return corruptData("New countdown occurrence conflicts with recorded alert")
        val result = snapshot.copy(clock = clock, elapsed = elapsed, alerts = SnapshotList.copyOf(snapshot.alerts + alerts))
        return Outcome.Success(TimingReduction(result, SnapshotList.copyOf(alerts), timedOut, sharedTimedOut))
    }

    private data class Warning(val target: GameAlertTarget, val threshold: Duration?, val before: Duration, val after: Duration)

    private fun delta(anchor: ClockAnchor?, reading: GameTimeObservation): Outcome<Duration> {
        if (anchor == null) return corruptData("Running timer has no anchor")
        if (anchor.processSessionId != reading.processSessionId) return invalidInput("Recover timers before using a new process session")
        val elapsed = reading.monotonic - anchor.monotonicElapsed
        if (!elapsed.isFinite() || elapsed < Duration.ZERO) return invalidInput("Invalid monotonic timer interval")
        return Outcome.Success(elapsed)
    }

    private fun missingLineage(): Outcome.Unavailable = Outcome.Unavailable(UnavailableReason.UNSUPPORTED,
        "Elapsed timing requires an explicitly initialized known turn lineage")

    private fun warningId(game: GameId, target: GameAlertTarget, lineage: String, threshold: Duration): EntityId {
        val duration = threshold.toComponents { seconds: Long, nanos: Int -> "$seconds:$nanos" }
        val parts = listOf(game.source.value, game.raw, if (target == GameAlertTarget.GameTimer) "game" else "turn", lineage, duration)
        val digest = MessageDigest.getInstance("SHA-256").run {
            update("command-library-timing-warning-v1\u0000".toByteArray(Charsets.UTF_8))
            for (part in parts) {
                val bytes = part.toByteArray(Charsets.UTF_8)
                update(ByteBuffer.allocate(4).putInt(bytes.size).array()); update(bytes)
            }
            digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        }
        return EntityId("timing-warning-v1/$digest")
    }
}
