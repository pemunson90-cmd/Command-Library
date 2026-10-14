package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import kotlin.time.Duration

/** Current graph coherence only. Does not certify event/source lineage or authorize live timing. */
internal object SnapshotGraphValidator {
    fun validate(snapshot: GameSnapshot): Outcome<CounterRoles> {
        if (snapshot.version != 1) return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Unsupported game model version")
        val seats = SeatIndex.of(snapshot).valueOr { return it }
        val definitions = snapshot.settings.counterDefinitions
        val life = definitions.filter { it.role == CounterRole.LIFE }
        val damage = definitions.filter { it.role == CounterRole.COMMANDER_DAMAGE }
        val tax = definitions.filter { it.role == CounterRole.COMMANDER_TAX }
        if (life.size != 1 || damage.size > 1 || tax.size > 1) return corruptData("Missing/ambiguous explicit counter roles")
        val roles = CounterRoles(life.single().id, damage.singleOrNull()?.id, tax.singleOrNull()?.id)
        // Duplicate definition IDs (including cross-role aliases) are rejected by CounterBoard before binding checks.
        CounterReducer.validateState(snapshot, roles).valueOr { return it }
        if (snapshot.activeSeatId?.let { !seats.contains(it) } == true) return corruptData("Unknown turn owner")
        validateClock(snapshot, seats)?.let { return it }
        val elapsed = snapshot.elapsed
        if (!validAnchor(elapsed.anchor)) return corruptData("Invalid elapsed anchor")
        if (!elapsed.paused && (elapsed.anchor == null || !snapshot.settings.elapsedTimers.enabled)) return corruptData("Running elapsed timer lacks enabled settings/anchor")
        if (snapshot.status == GameStatus.COMPLETED && (!snapshot.clock.paused || !elapsed.paused)) return corruptData("Completed game has running timers")
        val designations = snapshot.designations
        if (listOfNotNull(designations.monarch, designations.initiative).any { !seats.contains(it) } ||
            designations.blessedSeats.any { !seats.contains(it) } || designations.blessedSeats.toSet().size != designations.blessedSeats.size) return corruptData("Invalid designation target/duplicate blessing")
        if (snapshot.alerts.map { it.id }.toSet().size != snapshot.alerts.size) return corruptData("Duplicate game alert")
        for (alert in snapshot.alerts) {
            if (alert.gameId != snapshot.id || ((alert.target as? GameAlertTarget.SeatClock)?.seatId?.let { !seats.contains(it) } == true)) return corruptData("Invalid game alert identity/seat")
        }
        val result = (snapshot.result as? Knowledge.Known)?.value
        if (result != null) {
            if (result.gameId != snapshot.id || result.revision.value > snapshot.revision.value ||
                result.winners.any { !seats.contains(it) } || result.seatResults.any { !seats.contains(it.seatId) }) return corruptData("Invalid result identity/revision/seat")
            if (result.retracted == (snapshot.status == GameStatus.COMPLETED)) return corruptData("Result retraction/completion mismatch")
            val ended = (snapshot.endedAt as? Knowledge.Known)?.value
            if (ended != null && ended != result.finishedAt) return corruptData("Result/end timestamp mismatch")
        }
        if (((snapshot.lastEventId as? Knowledge.Known)?.value ?: 0L) < 0L) return corruptData("Invalid last event identity")
        validateSnapshotVariants(snapshot).valueOr { return it }
        return Outcome.Success(roles)
    }

    private fun validAnchor(anchor: ClockAnchor?): Boolean = anchor == null ||
        (anchor.monotonicElapsed.isFinite() && anchor.monotonicElapsed >= Duration.ZERO)

    private fun validateClock(snapshot: GameSnapshot, seats: SeatIndex): Outcome.Failure? {
        val clock = snapshot.clock
        if (clock.mode != snapshot.settings.clockMode) return corruptData("Clock mode/settings mismatch")
        if (!validAnchor(clock.anchor)) return corruptData("Invalid countdown anchor")
        if (clock.activeSeat?.let { !seats.contains(it) } == true) return corruptData("Unknown countdown owner")
        if (clock.firedAlertIds.toSet().size != clock.firedAlertIds.size) return corruptData("Duplicate fired alert identity")
        when (clock.mode) {
            SeatClockMode.NONE -> if (clock.seats.isNotEmpty() || clock.shared != null || clock.activeSeat != null || clock.anchor != null || !clock.paused) return corruptData("NONE has countdown state")
            SeatClockMode.SHARED -> if (clock.seats.isNotEmpty() || clock.shared == null) return corruptData("Invalid shared countdown banks")
            SeatClockMode.CHESS, SeatClockMode.PER_TURN -> {
                if (clock.shared != null || clock.seats.size != seats.seats.size || clock.seats.map { it.seatId }.toSet() != seats.seats.keys) return corruptData("Invalid seat countdown banks")
                if (clock.seats.any { !it.initial.isFinite() || it.initial < Duration.ZERO || !it.remaining.isFinite() }) return corruptData("Nonfinite countdown balance")
                if (!clock.paused && clock.activeSeat == null) return corruptData("Running seat clock has no owner")
            }
        }
        if (!clock.paused && (clock.anchor == null || clock.activeSeat?.let { seats.seats.getValue(it).participation is SeatParticipation.Eliminated } == true)) return corruptData("Running countdown lacks anchor/active participant")
        return null
    }
}
