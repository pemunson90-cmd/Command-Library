package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*
import kotlin.time.Duration

/** Structural new-game checks. Authentic variant/source semantics belong to the engine initializer. */
internal fun validateInitialGame(input: GameCreation, checkpoint: GameCheckpoint) {
    val code = FailureCode.INVALID_INPUT
    fun need(valid: Boolean, message: String) = requireGame(valid, code, message)
    val game = checkpoint.snapshot
    gameIdentity(game.id.source.value, code); gameIdentity(game.id.raw, code)
    need(game.version == 1 && game.revision.value == 0L, "Invalid initial game version/revision")
    need(game.settings == input.settings && game.seats == input.seats && game.clock == input.clock, "Initialized game differs from creation input")
    need(game.status != GameStatus.COMPLETED && game.turnNumber == 0L && game.activeSeatId == null, "Invalid initial game activity")
    need(game.endedAt is Knowledge.Absent && game.result is Knowledge.Absent && game.lastEventId is Knowledge.Absent &&
        checkpoint.lastSettledEventId is Knowledge.Absent && checkpoint.recoveredProcessSessionId == null, "Initial game contains incomplete/prior lineage")
    need(game.alerts.isEmpty() && game.designations == GameDesignations(), "Initial game contains prior alerts/designations")
    need(game.elapsed.gameElapsed == Duration.ZERO && game.elapsed.turnElapsed == Duration.ZERO && game.elapsed.paused &&
        game.elapsed.anchor == null && game.elapsed.turnLineageId is Knowledge.Absent, "Invalid initial elapsed timer")
    val definitions = input.settings.counterDefinitions.associateBy { it.id }
    val seats = input.seats.associateBy { it.id }
    val slots = input.seats.flatMap { seat -> seat.commanders.map { it.id to seat.id } }.toMap()
    val keys = mutableSetOf<Triple<EntityId, EntityId, EntityId?>>()
    for (state in game.counters) {
        val definition = definitions[state.definitionId] ?: rejectGame(code, "Initial counter definition missing")
        need(state.seatId in seats && state.value == definition.initial && !state.hidden, "Invalid initial counter value/seat/visibility")
        need(keys.add(Triple(state.seatId, state.definitionId, state.commanderSlotId)), "Duplicate initial counter")
        val validScope = when (definition.role) {
            CounterRole.COMMANDER_DAMAGE -> state.commanderSlotId != null && state.commanderSlotId in slots
            CounterRole.COMMANDER_TAX -> state.commanderSlotId != null && slots[state.commanderSlotId] == state.seatId
            CounterRole.LIFE, CounterRole.OTHER -> state.commanderSlotId == null
        }
        need(validScope, "Invalid initial counter commander scope")
    }
    val variants = input.settings.variantDefinitions
    need(variants.map { it.id }.distinct().size == variants.size, "Duplicate variant definition")
    need(game.variants.map { it.definitionId }.distinct().size == game.variants.size &&
        game.variants.map { it.definitionId }.toSet() == variants.map { it.id }.toSet(), "Missing/extra/duplicate initialized variant")
    for (definition in variants) {
        val state = game.variants.single { it.definitionId == definition.id }
        need(state.version == definition.version, "Initialized variant version mismatch")
        val zones = definition.zones.map { it.id }
        need(zones.distinct().size == zones.size && state.zones.map { it.zoneId }.distinct().size == state.zones.size &&
            state.zones.map { it.zoneId }.toSet() == zones.toSet(), "Missing/extra/duplicate initialized variant zone")
        val cards = definition.cards.map { it.id }
        need(cards.distinct().size == cards.size, "Duplicate variant card definition")
        val referenced = state.zones.flatMap { it.cardIds } + state.drawn + state.discarded + state.properties.mapNotNull { (it.value as? VariantValue.Card)?.value }
        need(referenced.all { it in cards }, "Unknown initialized variant card")
        for (zone in state.zones) {
            val maximum = definition.zones.single { it.id == zone.zoneId }.maximumCards
            need(maximum == null || zone.cardIds.size <= maximum, "Initial variant zone exceeds capacity")
        }
    }
}
