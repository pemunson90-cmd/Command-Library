package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import kotlin.time.Duration

/** Explicit controls compose the same-observation settlement; caller owns fresh lineage allocation. */
internal object LifeTimingControls {
    fun countdownPause(snapshot: GameSnapshot, paused: Boolean, reading: GameTimeObservation): Outcome<TimingReduction> {
        editable(snapshot)?.let { return it }
        val clock = snapshot.clock
        if (clock.mode == SeatClockMode.NONE || clock.paused == paused) return invalidInput("Countdown pause request has no state change")
        if (!paused && clock.mode != SeatClockMode.SHARED && !eligible(snapshot, clock.activeSeat)) return invalidInput("Choose an active participant before resuming countdown")
        if (!paused && exhausted(clock)) return invalidInput("Cannot resume an exhausted countdown under a clamping zero policy")
        val settled = LifeTimingEngine.settle(snapshot, reading).valueOr { return it }
        return Outcome.Success(settled.copy(snapshot = settled.snapshot.copy(clock = settled.snapshot.clock.copy(
            paused = paused, anchor = reading.anchor(), activeSeat = if (clock.mode == SeatClockMode.SHARED) null else clock.activeSeat))))
    }

    fun elapsedPause(snapshot: GameSnapshot, paused: Boolean, reading: GameTimeObservation): Outcome<TimingReduction> {
        editable(snapshot)?.let { return it }
        if (snapshot.elapsed.paused == paused) return invalidInput("Elapsed pause request has no state change")
        if (!paused && (!snapshot.settings.elapsedTimers.enabled || snapshot.elapsed.turnLineageId !is Knowledge.Known)) {
            return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Elapsed timing requires enabled settings and explicitly initialized known lineage")
        }
        val settled = LifeTimingEngine.settle(snapshot, reading).valueOr { return it }
        return Outcome.Success(settled.copy(snapshot = settled.snapshot.copy(elapsed = settled.snapshot.elapsed.copy(paused = paused, anchor = reading.anchor()))))
    }

    fun switchTurn(snapshot: GameSnapshot, seat: EntityId, lineage: EntityId, reading: GameTimeObservation): Outcome<TimingReduction> {
        editable(snapshot)?.let { return it }
        if (!eligible(snapshot, seat)) return invalidInput("Turn target is not an active participant")
        if (snapshot.turnNumber == Long.MAX_VALUE) return invalidInput("Turn number is exhausted")
        if ((snapshot.elapsed.turnLineageId as? Knowledge.Known)?.value == lineage) return invalidInput("A new turn requires a fresh lineage")
        val settled = LifeTimingEngine.settle(snapshot, reading).valueOr { return it }
        val current = settled.snapshot
        val turned = when (current.clock.mode) {
            SeatClockMode.CHESS, SeatClockMode.PER_TURN -> {
                val seatTurn = SeatClockReducer.switchTurn(current, seat, reading.legacy()).valueOr { return it }.snapshot
                if (exhausted(seatTurn.clock)) seatTurn.copy(clock = seatTurn.clock.copy(paused = true)) else seatTurn
            }
            SeatClockMode.NONE, SeatClockMode.SHARED -> current.copy(activeSeatId = seat, turnNumber = current.turnNumber + 1)
        }
        return Outcome.Success(settled.copy(snapshot = turned.copy(elapsed = current.elapsed.copy(
            turnElapsed = Duration.ZERO, turnLineageId = Knowledge.Known(lineage), anchor = reading.anchor()))))
    }

    fun passClock(snapshot: GameSnapshot, seat: EntityId, reading: GameTimeObservation): Outcome<TimingReduction> {
        editable(snapshot)?.let { return it }
        if (snapshot.clock.mode != SeatClockMode.CHESS && snapshot.clock.mode != SeatClockMode.PER_TURN) return invalidInput("Priority passing requires seat countdowns")
        if (!eligible(snapshot, seat) || seat == snapshot.clock.activeSeat) return invalidInput("Priority target is not a different active participant")
        val settled = LifeTimingEngine.settle(snapshot, reading).valueOr { return it }
        var clock = settled.snapshot.clock.copy(activeSeat = seat, anchor = reading.anchor())
        if (exhausted(clock)) clock = clock.copy(paused = true)
        return Outcome.Success(settled.copy(snapshot = settled.snapshot.copy(clock = clock)))
    }

    fun recover(snapshot: GameSnapshot, reading: GameTimeObservation): Outcome<TimingRecovery> {
        SnapshotGraphValidator.validate(snapshot).valueOr { return it }
        val clock = snapshot.clock
        val elapsed = snapshot.elapsed
        for (anchor in listOfNotNull(clock.anchor, elapsed.anchor)) {
            if (anchor.processSessionId == reading.processSessionId && reading.monotonic < anchor.monotonicElapsed) return invalidInput("Monotonic recovery reading precedes a current-process anchor")
        }
        val restart = (!clock.paused && clock.anchor?.processSessionId != reading.processSessionId) ||
            (!elapsed.paused && elapsed.anchor?.processSessionId != reading.processSessionId)
        val repairPause = !elapsed.paused && elapsed.turnLineageId !is Knowledge.Known
        val nextClock = if (clock.mode != SeatClockMode.NONE && (restart || clock.anchor?.let { it.processSessionId != reading.processSessionId } == true)) {
            clock.copy(paused = true, anchor = reading.anchor())
        } else clock
        val nextElapsed = if (restart || repairPause || elapsed.anchor?.let { it.processSessionId != reading.processSessionId } == true) {
            elapsed.copy(paused = true, anchor = if (elapsed.anchor != null || !elapsed.paused) reading.anchor() else null)
        } else elapsed
        val result = snapshot.copy(clock = nextClock, elapsed = nextElapsed)
        return Outcome.Success(TimingRecovery(result, result != snapshot,
            (!clock.paused && nextClock.paused) || (!elapsed.paused && nextElapsed.paused)))
    }

    private fun editable(snapshot: GameSnapshot): Outcome<Nothing>? {
        SnapshotGraphValidator.validate(snapshot).valueOr { return it }
        return if (snapshot.status == GameStatus.COMPLETED) invalidInput("Reopen completed game before changing timers") else null
    }
    private fun eligible(snapshot: GameSnapshot, seat: EntityId?): Boolean = seat != null && snapshot.seats.any { it.id == seat && it.participation is SeatParticipation.Active }
    private fun exhausted(clock: SeatClockState): Boolean = clock.zeroPolicy != ClockZeroPolicy.ALERT && when (clock.mode) {
        SeatClockMode.SHARED -> clock.shared!!.remaining <= Duration.ZERO
        SeatClockMode.CHESS, SeatClockMode.PER_TURN -> clock.seats.single { it.seatId == clock.activeSeat }.remaining <= Duration.ZERO
        SeatClockMode.NONE -> false
    }
    private fun GameTimeObservation.anchor(): ClockAnchor = ClockAnchor(wall, monotonic, processSessionId)
    private fun GameTimeObservation.legacy(): ClockReading = ClockReading(monotonic, wall, processSessionId)
}
