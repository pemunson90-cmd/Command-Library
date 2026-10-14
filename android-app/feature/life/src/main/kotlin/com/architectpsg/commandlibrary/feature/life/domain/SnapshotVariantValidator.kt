package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*

/** Declared graph references only; authentic source and variant action semantics remain engine work. */
internal fun validateSnapshotVariants(snapshot: GameSnapshot): Outcome<Unit> {
    val definitions = snapshot.settings.variantDefinitions
    val states = snapshot.variants
    if (definitions.map { it.id }.toSet().size != definitions.size || states.map { it.definitionId }.toSet().size != states.size ||
        definitions.map { it.id }.toSet() != states.map { it.definitionId }.toSet()) return corruptData("Missing/extra/duplicate variant")
    val statesById = states.associateBy { it.definitionId }
    for (definition in definitions) {
        validateVariantDefinitionState(definition, statesById.getValue(definition.id)).valueOr { return it }
    }
    return Outcome.Success(Unit)
}

/** Same reference-only checks reused by authored managers; not source/execution authority. */
internal fun validateVariantDefinitionState(definition: VariantDefinition, state: VariantState): Outcome<Unit> {
    if (state.definitionId != definition.id) return corruptData("Variant definition identity mismatch")
    val zones = definition.zones.associateBy { it.id }
    val cards = definition.cards.associateBy { it.id }
    val actions = definition.allowedActions.associateBy { it.id }
    if (zones.size != definition.zones.size || cards.size != definition.cards.size || actions.size != definition.allowedActions.size ||
        definition.phases.toSet().size != definition.phases.size) return corruptData("Duplicate variant vocabulary")
    if (definition.allowedActions.any { it.fromZone?.let { id -> id !in zones } == true || it.toZone?.let { id -> id !in zones } == true } ||
        definition.cards.any { card -> card.actionIds.any { it !in actions } }) return corruptData("Dangling variant definition reference")
    if (state.version != definition.version || state.zones.size != zones.size || state.zones.map { it.zoneId }.toSet() != zones.keys) return corruptData("Variant version/zone mismatch")
    if (definition.phases.isNotEmpty() && state.phase !in definition.phases) return corruptData("Unknown variant phase")
    if (state.properties.map { it.key }.toSet().size != state.properties.size || state.actionCounts.map { it.actionId }.toSet().size != state.actionCounts.size) return corruptData("Duplicate variant property/action count")
    for (count in state.actionCounts) {
        val action = actions[count.actionId] ?: return corruptData("Unknown variant counted action")
        if (action.maximumPerPhase?.let { count.count > it } == true) return corruptData("Variant action exceeds phase limit")
    }
    for (zone in state.zones) {
        if (zones.getValue(zone.zoneId).maximumCards?.let { zone.cardIds.size > it } == true) return corruptData("Variant zone exceeds capacity")
        if (zone.cardIds.any { it !in cards }) return corruptData("Unknown variant zone card")
    }
    if (state.drawn.any { it !in cards } || state.discarded.any { it !in cards } ||
        state.properties.any { (it.value as? VariantValue.Card)?.value?.let { id -> id !in cards } == true }) return corruptData("Unknown variant card reference")
    return Outcome.Success(Unit)
}
