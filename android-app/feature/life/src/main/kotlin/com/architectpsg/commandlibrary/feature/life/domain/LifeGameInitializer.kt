package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import kotlin.time.Duration

/** Trusted pure/offline placement policy; structural validation does not attest source authority. */
internal fun interface LifeVariantInitializer {
    fun initialize(input: GameCreation): Outcome<SnapshotList<VariantState>>
}

/** Pure proposal. Caller owns fresh identities and invokes only for a new durable creation operation. */
internal object LifeGameInitializer {
    fun initialize(input: GameCreation, id: GameId, reading: GameTimeObservation, openingLineage: EntityId,
        variants: LifeVariantInitializer? = null): Outcome<GameCheckpoint> {
        val states = if (input.settings.variantDefinitions.isEmpty()) SnapshotList.empty() else {
            val backend = variants ?: return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Variant initialization requires an explicit versioned placement policy")
            backend.initialize(input).valueOr { return it }
        }
        val counters = ArrayList<CounterState>()
        for (definition in input.settings.counterDefinitions) for (seat in input.seats) {
            val slots: List<EntityId?> = when (definition.role) {
                CounterRole.COMMANDER_DAMAGE -> input.seats.sortedBy { it.order }.filter { it.id != seat.id }.flatMap { it.commanders.map { slot -> slot.id } }
                CounterRole.COMMANDER_TAX -> seat.commanders.map { it.id }
                else -> listOf(null)
            }
            for (slot in slots) counters += CounterState(definition.id, seat.id, slot, definition.initial, false)
        }
        val snapshot = GameSnapshot(id, 1, Revision(0), input.settings, GameStatus.PAUSED, reading.wall,
            Knowledge.Absent("New game has not ended"), input.seats, SnapshotList.copyOf(counters), null, 0,
            input.clock, states, Knowledge.Absent("New game has no action event"), Knowledge.Absent("No result confirmed"),
            elapsed = ElapsedTimerState(Duration.ZERO, Duration.ZERO, true, null, Knowledge.Known(openingLineage)))
        when (val checked = SnapshotGraphValidator.validate(snapshot)) {
            is Outcome.Success -> Unit
            is Outcome.Failure -> return invalidInput(checked.error.message)
            is Outcome.Unavailable -> return checked
            is Outcome.Unknown -> return checked
        }
        return Outcome.Success(GameCheckpoint(snapshot, Knowledge.Absent("No event has been settled"), null))
    }
}
