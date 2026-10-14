package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*

internal sealed interface VariantManagerSelection {
    data object None : VariantManagerSelection
    data object Shuffle : VariantManagerSelection
    data class Top(val count: Int) : VariantManagerSelection
    data class Explicit(val minimum: Int, val maximum: Int) : VariantManagerSelection
}
internal enum class VariantInsertion { TOP, BOTTOM }
internal data class VariantManagerTransition(val actionId: String, val phases: SnapshotList<String>,
    val fromZone: String?, val toZone: String?, val selection: VariantManagerSelection = VariantManagerSelection.None,
    val insertion: VariantInsertion = VariantInsertion.BOTTOM, val appendDrawn: Boolean = false, val appendDiscarded: Boolean = false,
    val nextPhase: String? = null, val properties: SnapshotList<VariantProperty> = SnapshotList.empty(), val reset: Boolean = false)
internal data class VariantManagerPlan(val definition: VariantDefinition, val initial: VariantState,
    val policyKey: String, val policyValue: String, val transitions: SnapshotList<VariantManagerTransition>)

/** Trusted authored deterministic plans only; neither source authentication nor hidden randomization. */
internal class LifeVariantManager(plans: Iterable<VariantManagerPlan>) : LifeVariantInitializer, LifeVariantActionBackend, LifeVariantShuffleAuthority {
    private val plans = SnapshotList.copyOf(plans)

    override fun initialize(input: GameCreation): Outcome<SnapshotList<VariantState>> {
        if (input.settings.variantDefinitions.isEmpty()) return Outcome.Success(SnapshotList.empty())
        validatePlans().valueOr { return it }
        val definitions = input.settings.variantDefinitions
        if (definitions.map { it.id }.toSet().size != definitions.size) return invalidInput("Duplicate initialization variant")
        val states = ArrayList<VariantState>()
        for (definition in definitions) states += matched(definition).valueOr { return it }.initial
        return Outcome.Success(SnapshotList.copyOf(states))
    }

    override fun reduce(snapshot: GameSnapshot, action: GameAction.Variant): Outcome<VariantState> {
        validatePlans().valueOr { return it }
        validateSnapshotVariants(snapshot).valueOr { return it }
        if (snapshot.status == GameStatus.COMPLETED) return invalidInput("Reopen before variant actions")
        val definition = snapshot.settings.variantDefinitions.singleOrNull { it.id == action.definitionId }
            ?: return invalidInput("Unknown variant definition")
        val plan = matched(definition).valueOr { return it }
        val current = snapshot.variants.single { it.definitionId == definition.id }
        val marker = current.properties.singleOrNull { it.key == plan.policyKey }
        if (marker?.value != VariantValue.Text(plan.policyValue)) return unsupported("Variant execution policy requires exact installed version")
        for (property in plan.initial.properties) {
            val stored = current.properties.singleOrNull { it.key == property.key }
            if (stored == null || stored.value::class != property.value::class) return corruptData("Variant manager property missing or changed type")
        }
        val transition = plan.transitions.singleOrNull { it.actionId == action.actionId }
            ?: return invalidInput("Unknown authored variant action")
        if (current.phase !in transition.phases) return invalidInput("Variant action is not allowed in current phase")
        val command = definition.allowedActions.single { it.id == action.actionId }
        val oldCount = current.actionCounts.singleOrNull { it.actionId == action.actionId }?.count ?: 0
        // Same preflight as the outer event engine: phase/reset cannot bypass exhausted current quota.
        if (command.maximumPerPhase?.let { oldCount >= it } == true) return invalidInput("Variant action reached current phase limit")
        val source = transition.fromZone?.let { id -> current.zones.single { it.zoneId == id }.cardIds }
        val selected = when (val selection = transition.selection) {
            VariantManagerSelection.Shuffle -> return unsupported("Use recorded random request for authored shuffle")
            VariantManagerSelection.None -> {
                if (action.selectedCards.isNotEmpty()) return invalidInput("Variant action accepts no selected cards")
                emptyList()
            }
            is VariantManagerSelection.Top -> {
                if (action.selectedCards.isNotEmpty() || source!!.size < selection.count) return invalidInput("Top selection requires available cards and no injected order")
                source.take(selection.count)
            }
            is VariantManagerSelection.Explicit -> {
                if (action.selectedCards.size !in selection.minimum..selection.maximum) return invalidInput("Variant selection cardinality outside authored bounds")
                action.selectedCards.toList()
            }
        }
        val remaining = if (source != null) {
            val available = source.groupingBy { it }.eachCount()
            val needed = selected.groupingBy { it }.eachCount().toMutableMap()
            if (needed.any { (id, count) -> count > (available[id] ?: 0) }) return invalidInput("Selected variant occurrences are not in source zone")
            source.filter { id ->
                val count = needed[id] ?: 0
                if (count == 0) true else { needed[id] = count - 1; false }
            }
        } else emptyList()
        var next = if (transition.reset) plan.initial else current
        if (source != null) {
            val destination = if (transition.fromZone == transition.toZone) remaining else current.zones.single { it.zoneId == transition.toZone }.cardIds
            val inserted = if (transition.insertion == VariantInsertion.TOP) selected + destination else destination + selected
            next = next.copy(zones = SnapshotList.copyOf(current.zones.map { zone -> when (zone.zoneId) {
                transition.toZone -> zone.copy(cardIds = SnapshotList.copyOf(inserted))
                transition.fromZone -> zone.copy(cardIds = SnapshotList.copyOf(remaining))
                else -> zone
            } }))
        }
        if (transition.appendDrawn) next = next.copy(drawn = SnapshotList.copyOf(next.drawn + selected))
        if (transition.appendDiscarded) next = next.copy(discarded = SnapshotList.copyOf(next.discarded + selected))
        if (transition.nextPhase != null && transition.nextPhase != current.phase) {
            next = next.copy(phase = transition.nextPhase, actionCounts = SnapshotList.empty())
        }
        if (transition.properties.isNotEmpty()) {
            val replacements = transition.properties.associateBy { it.key }
            next = next.copy(properties = SnapshotList.copyOf(next.properties.map { replacements[it.key] ?: it }))
        }
        val count = next.actionCounts.singleOrNull { it.actionId == action.actionId }?.count ?: 0
        if (count == Int.MAX_VALUE || command.maximumPerPhase?.let { count >= it } == true) return invalidInput("Variant action count exhausted")
        val counts = if (next.actionCounts.any { it.actionId == action.actionId }) next.actionCounts.map {
            if (it.actionId == action.actionId) it.copy(count = count + 1) else it
        } else next.actionCounts + VariantActionCount(action.actionId, count + 1)
        next = next.copy(actionCounts = SnapshotList.copyOf(counts))
        validateProposed(definition, next).valueOr { return it }
        return Outcome.Success(next)
    }

    override fun authorizeShuffle(snapshot: GameSnapshot, request: RandomRequest.VariantZoneShuffle): Outcome<Unit> {
        validatePlans().valueOr { return it }
        validateSnapshotVariants(snapshot).valueOr { return it }
        if (snapshot.status == GameStatus.COMPLETED) return invalidInput("Reopen before variant shuffle")
        val definition = snapshot.settings.variantDefinitions.singleOrNull { it.id == request.definitionId } ?: return invalidInput("Unknown shuffle variant")
        val plan = matched(definition).valueOr { return it }
        val current = snapshot.variants.single { it.definitionId == definition.id }
        if (request.policyKey != plan.policyKey || request.policyValue != plan.policyValue ||
            current.properties.singleOrNull { it.key == plan.policyKey }?.value != VariantValue.Text(plan.policyValue)) return unsupported("Shuffle policy requires exact installed manager")
        for (property in plan.initial.properties) {
            val stored = current.properties.singleOrNull { it.key == property.key }
            if (stored == null || stored.value::class != property.value::class) return corruptData("Variant manager property missing or changed type")
        }
        val transition = plan.transitions.singleOrNull { it.actionId == request.actionId } ?: return invalidInput("Unknown authored shuffle action")
        if (transition.selection != VariantManagerSelection.Shuffle) return unsupported("Authored action is not a recorded shuffle")
        if (current.phase !in transition.phases || request.zoneId != transition.fromZone) return invalidInput("Shuffle phase or zone differs from authored policy")
        LifeVariantShuffle.prepare(snapshot, request).valueOr { return it }
        return Outcome.Success(Unit)
    }

    private fun matched(definition: VariantDefinition): Outcome<VariantManagerPlan> {
        val plan = plans.singleOrNull { it.definition.id == definition.id && it.definition.version == definition.version }
            ?: return unsupported("Variant requires an installed authored manager version")
        if (plan.definition != definition) return unsupported("Variant content differs from installed manager manifest")
        return Outcome.Success(plan)
    }
    internal fun validateConfiguration(): Outcome<Unit> = validatePlans()

    private fun validatePlans(): Outcome<Unit> {
        if (plans.map { it.definition.id to it.definition.version }.toSet().size != plans.size) return invalidInput("Duplicate authored manager version")
        for (plan in plans) {
            val definition = plan.definition
            validateProposed(definition, plan.initial).valueOr { return it }
            if (definition.phases.isEmpty() || definition.phases.any { it.isBlank() } || plan.policyKey.isBlank() || plan.policyValue.isBlank() ||
                plan.initial.properties.singleOrNull { it.key == plan.policyKey }?.value != VariantValue.Text(plan.policyValue)) return invalidInput("Manager needs explicit phases and retained policy identity")
            if (plan.transitions.map { it.actionId }.toSet().size != plan.transitions.size ||
                plan.transitions.map { it.actionId }.toSet() != definition.allowedActions.map { it.id }.toSet()) return invalidInput("Manager transitions must exactly cover declared actions")
            for (transition in plan.transitions) {
                val command = definition.allowedActions.single { it.id == transition.actionId }
                if (transition.phases.isEmpty() || transition.phases.toSet().size != transition.phases.size || transition.phases.any { it !in definition.phases } ||
                    transition.nextPhase?.let { it !in definition.phases } == true || transition.fromZone != command.fromZone || transition.toZone != command.toZone) return invalidInput("Invalid authored phase/zone policy")
                val moving = transition.fromZone != null && transition.toZone != null
                if ((transition.fromZone == null) != (transition.toZone == null) ||
                    (moving && transition.selection == VariantManagerSelection.None) ||
                    (!moving && (transition.selection != VariantManagerSelection.None || transition.appendDrawn || transition.appendDiscarded))) return invalidInput("Selection must match explicit movement policy")
                when (val selection = transition.selection) {
                    VariantManagerSelection.None -> Unit
                    VariantManagerSelection.Shuffle -> if (transition.fromZone != transition.toZone || transition.reset || transition.nextPhase != null ||
                        transition.properties.isNotEmpty() || transition.appendDrawn || transition.appendDiscarded) return invalidInput("Shuffle may change only full zone order and current action count")
                    is VariantManagerSelection.Top -> if (selection.count !in 1..10_000) return invalidInput("Top count must be bounded")
                    is VariantManagerSelection.Explicit -> if (selection.minimum < 0 || selection.maximum < selection.minimum || selection.maximum > 10_000) return invalidInput("Explicit selection must be bounded")
                }
                if (transition.reset && (moving || transition.nextPhase != null || transition.properties.isNotEmpty())) return invalidInput("Deterministic reset cannot mix other effects")
                if (transition.properties.map { it.key }.toSet().size != transition.properties.size) return invalidInput("Duplicate authored property replacement")
                for (property in transition.properties) {
                    val initial = plan.initial.properties.singleOrNull { it.key == property.key }
                    if (property.key == plan.policyKey || initial == null || initial.value::class != property.value::class ||
                        (property.value as? VariantValue.Card)?.value?.let { id -> definition.cards.none { it.id == id } } == true) return invalidInput("Invalid authored property replacement")
                }
            }
        }
        return Outcome.Success(Unit)
    }
    private fun validateProposed(definition: VariantDefinition, state: VariantState): Outcome<Unit> = when (val checked = validateVariantDefinitionState(definition, state)) {
        is Outcome.Failure -> invalidInput(checked.error.message)
        else -> checked
    }
    private fun unsupported(message: String) = Outcome.Unavailable(UnavailableReason.UNSUPPORTED, message)
}
