package com.architectpsg.commandlibrary.core.model

import kotlin.time.Duration

/** Immutable new-game input; the service owns idempotence, generated IDs and persistence. */
public data class GameCreation(
    public val operationId: EntityId,
    public val settings: GameSettings,
    public val seats: SnapshotList<Seat>,
    public val clock: SeatClockState,
) {
    init {
        require(seats.size in 1..6) { "A new game requires one to six seats" }
        val seatIds = seats.map { it.id }.toSet()
        require(seatIds.size == seats.size) { "Seat identities must be unique" }
        require(seats.map { it.order }.toSet() == seats.indices.toSet()) { "Seat orders must cover zero through seat count minus one" }
        require(seats.all { it.participation == SeatParticipation.Active }) { "New seats must be active" }
        val commanderIds = seats.flatMap { seat -> seat.commanders.map { it.id } }
        require(commanderIds.toSet().size == commanderIds.size) { "Commander-slot identities must be globally unique" }

        val definitions = settings.counterDefinitions
        require(definitions.map { it.id }.toSet().size == definitions.size) { "Counter-definition identities must be unique" }
        require(definitions.count { it.role == CounterRole.LIFE } == 1) { "Exactly one explicit LIFE definition is required" }
        require(definitions.count { it.role == CounterRole.COMMANDER_DAMAGE } <= 1) { "Commander-damage role must be unique" }
        require(definitions.count { it.role == CounterRole.COMMANDER_TAX } <= 1) { "Commander-tax role must be unique" }
        require(definitions.single { it.role == CounterRole.LIFE }.initial == settings.initialLife) { "LIFE initial must agree with game settings" }

        require(settings.clockMode == clock.mode) { "Clock mode must agree with game settings" }
        require(clock.paused && clock.activeSeat == null && clock.anchor == null && clock.firedAlertIds.isEmpty()) {
            "New clocks must be paused with no owner, anchor or prior alerts"
        }
        when (clock.mode) {
            SeatClockMode.NONE -> require(clock.seats.isEmpty() && clock.shared == null) { "NONE has no countdown banks" }
            SeatClockMode.SHARED -> {
                require(clock.seats.isEmpty()) { "SHARED has no per-seat banks" }
                val shared = requireNotNull(clock.shared) { "SHARED requires its single countdown" }
                require(shared.remaining == shared.initial) { "New shared countdown must retain its initial balance" }
            }
            SeatClockMode.CHESS, SeatClockMode.PER_TURN -> {
                require(clock.shared == null) { "Seat clocks cannot contain a shared bank" }
                require(clock.seats.size == seats.size && clock.seats.map { it.seatId }.toSet() == seatIds) {
                    "Seat clocks require exactly one bank for every seat"
                }
                require(clock.seats.all { it.initial.isFinite() && it.initial >= Duration.ZERO && it.remaining == it.initial }) {
                    "New seat banks must have finite nonnegative initial balances without elapsed time"
                }
            }
        }
    }
}
