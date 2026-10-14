package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*

/** Required for new writes only; mathematical historical replay does not authenticate old source policy. */
internal fun interface LifeVariantShuffleAuthority {
    fun authorizeShuffle(snapshot: GameSnapshot, request: RandomRequest.VariantZoneShuffle): Outcome<Unit>
}
internal data class VariantShuffleFacts(val definition: VariantDefinition, val state: VariantState,
    val zone: VariantZone, val count: Int)
internal object LifeVariantShuffle {
    fun prepare(snapshot: GameSnapshot, request: RandomRequest.VariantZoneShuffle): Outcome<VariantShuffleFacts> {
        val definition = snapshot.settings.variantDefinitions.singleOrNull { it.id == request.definitionId }
            ?: return invalidInput("Unknown shuffle variant definition")
        val state = snapshot.variants.single { it.definitionId == definition.id }
        val command = definition.allowedActions.singleOrNull { it.id == request.actionId }
            ?: return invalidInput("Unknown shuffle variant action")
        if (definition.version.isBlank() || command.fromZone != request.zoneId || command.toZone != request.zoneId) return invalidInput("Shuffle requires an explicit versioned same-zone action")
        if (state.properties.singleOrNull { it.key == request.policyKey }?.value != VariantValue.Text(request.policyValue)) return invalidInput("Shuffle policy marker differs from current state")
        val zone = state.zones.singleOrNull { it.zoneId == request.zoneId } ?: return invalidInput("Unknown shuffle zone")
        if (zone.cardIds.size > RecordedRandomValue.VariantZoneShuffle.MAX_CARDS) return invalidInput("Shuffle zone exceeds card bound")
        val count = state.actionCounts.singleOrNull { it.actionId == request.actionId }?.count ?: 0
        if (count == Int.MAX_VALUE || command.maximumPerPhase?.let { count >= it } == true) return invalidInput("Shuffle action count exhausted")
        return Outcome.Success(VariantShuffleFacts(definition, state, zone, count))
    }
    fun replay(snapshot: GameSnapshot, request: RandomRequest.VariantZoneShuffle, value: RecordedRandomValue): Outcome<GameSnapshot> {
        val facts = prepare(snapshot, request).valueOr { return it }
        val recorded = value as? RecordedRandomValue.VariantZoneShuffle ?: return invalidInput("Recorded value is not a variant shuffle")
        if (recorded.definitionId != request.definitionId || recorded.definitionVersion != facts.definition.version ||
            recorded.actionId != request.actionId || recorded.zoneId != request.zoneId || recorded.policyKey != request.policyKey ||
            recorded.policyValue != request.policyValue || recorded.beforeOrder != facts.zone.cardIds) return invalidInput("Recorded shuffle differs from exact request/before-state")
        val counts = if (facts.state.actionCounts.any { it.actionId == request.actionId }) facts.state.actionCounts.map {
            if (it.actionId == request.actionId) it.copy(count = facts.count + 1) else it
        } else facts.state.actionCounts + VariantActionCount(request.actionId, facts.count + 1)
        val next = facts.state.copy(zones = SnapshotList.copyOf(facts.state.zones.map { zone ->
            if (zone.zoneId == request.zoneId) zone.copy(cardIds = recorded.afterOrder) else zone
        }), actionCounts = SnapshotList.copyOf(counts))
        return Outcome.Success(snapshot.copy(variants = SnapshotList.copyOf(snapshot.variants.map { if (it.definitionId == request.definitionId) next else it })))
    }
}
