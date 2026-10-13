package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.CounterDefinition
import com.architectpsg.commandlibrary.core.model.CounterLossPolicy
import com.architectpsg.commandlibrary.core.model.CounterState
import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.GameAction
import com.architectpsg.commandlibrary.core.model.GameSnapshot
import com.architectpsg.commandlibrary.core.model.GameStatus
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.SnapshotList

/**
 * Explicit role bindings by definition ID. A label such as "Life" never selects behavior.
 * Core `GameSettings` has no persisted role field yet; see the worker handoff contract request.
 */
internal data class CounterRoles(
    val life: EntityId,
    val commanderDamage: EntityId?,
    val commanderTax: EntityId?,
)

/**
 * Where a counter's commander slot must come from. Commander damage accepts any slot in the game because
 * damage is attributed to the commander, not its controller (CR 903.10a), so a stolen commander can damage
 * its owner. Commander tax counts casts of the seat's own commander.
 */
internal enum class CounterScope { SEAT, ANY_COMMANDER, OWN_COMMANDER }

/** A counter whose loss policy is met. Reported only; elimination is a separate, persisted decision. */
internal data class LossCandidate(
    val seatId: EntityId,
    val definitionId: EntityId,
    val commanderSlotId: EntityId?,
    val value: Int,
    val policy: CounterLossPolicy,
)

/**
 * Result of one validated counter action. [snapshot] keeps the input revision and event pointers; only the
 * committing transaction advances them. [newlyReached] is the subset of [lossCandidates] this action caused.
 */
internal data class CounterReduction(
    val snapshot: GameSnapshot,
    val action: GameAction,
    val lossCandidates: SnapshotList<LossCandidate>,
    val newlyReached: SnapshotList<LossCandidate>,
)

internal object CounterReducer {
    /**
     * Applies [GameAction.AdjustCounter], [GameAction.SetCounterHidden] or one flat [GameAction.Group] of them, all or
     * nothing. A group inside a group is rejected: an event carries exactly one group ID.
     */
    fun reduce(snapshot: GameSnapshot, roles: CounterRoles, action: GameAction): Outcome<CounterReduction> {
        if (snapshot.status == GameStatus.COMPLETED) return invalidInput("Game is completed; reopen it before changing counters")
        val board = CounterBoard.of(snapshot, roles).valueOr { return it }
        val before = LinkedHashMap(board.states)
        board.apply(action)?.let { return it }
        val candidates = board.lossCandidates { key, policy -> !meets(policy, before[key]?.value ?: board.definition(key).initial) }
        return Outcome.Success(
            CounterReduction(
                snapshot = snapshot.copy(counters = SnapshotList.copyOf(board.states.values)),
                action = action,
                lossCandidates = SnapshotList.copyOf(candidates.map { it.first }),
                newlyReached = SnapshotList.copyOf(candidates.filter { it.second }.map { it.first }),
            ),
        )
    }

    /**
     * Commander damage from [sourceSlotId] to [victimSeatId], optionally deducting the same amount of life, as one
     * [GameAction.Group]. Either both counters change or neither does. A negative [amount] is a correction.
     */
    fun commanderDamage(
        snapshot: GameSnapshot,
        roles: CounterRoles,
        victimSeatId: EntityId,
        sourceSlotId: EntityId,
        amount: Int,
        deductLife: Boolean,
        groupId: EntityId,
    ): Outcome<CounterReduction> {
        val damage = roles.commanderDamage ?: return invalidInput("No commander damage counter is bound for this game")
        if (amount == 0) return invalidInput("Commander damage amount must be nonzero")
        if (deductLife && amount == Int.MIN_VALUE) return invalidInput("Commander damage amount $amount cannot be mirrored as life")
        val actions = buildList {
            add(GameAction.AdjustCounter(victimSeatId, damage, sourceSlotId, amount))
            if (deductLife) add(GameAction.AdjustCounter(victimSeatId, roles.life, null, -amount))
        }
        return reduce(snapshot, roles, GameAction.Group(SnapshotList.copyOf(actions), groupId))
    }

    /** Every counter currently meeting its loss policy, absent states counted at their initial value, without changing anything. */
    fun assessLoss(snapshot: GameSnapshot, roles: CounterRoles): Outcome<SnapshotList<LossCandidate>> {
        val board = CounterBoard.of(snapshot, roles).valueOr { return it }
        return Outcome.Success(SnapshotList.copyOf(board.lossCandidates { _, _ -> false }.map { it.first }))
    }

    internal fun meets(policy: CounterLossPolicy, value: Int): Boolean = when (policy) {
        CounterLossPolicy.None -> false
        is CounterLossPolicy.AtOrAbove -> value >= policy.threshold
        is CounterLossPolicy.AtOrBelow -> value <= policy.threshold
    }
}

private data class CounterKey(val definitionId: EntityId, val seatId: EntityId, val commanderSlotId: EntityId?)

/** Validated, privately owned working copy of a snapshot's counters. The input snapshot is never touched. */
private class CounterBoard private constructor(
    private val seats: SeatIndex,
    private val definitions: Map<EntityId, CounterDefinition>,
    private val roles: CounterRoles,
    val states: LinkedHashMap<CounterKey, CounterState>,
) {
    fun definition(key: CounterKey): CounterDefinition = definitions.getValue(key.definitionId)

    fun scopeOf(definitionId: EntityId): CounterScope = when (definitionId) {
        roles.commanderDamage -> CounterScope.ANY_COMMANDER
        roles.commanderTax -> CounterScope.OWN_COMMANDER
        else -> CounterScope.SEAT
    }

    /** Returns null when [action] applied cleanly; on failure the board may be partially changed and must be discarded. */
    fun apply(action: GameAction): Outcome.Failure? = when (action) {
        is GameAction.AdjustCounter -> adjust(action)
        is GameAction.SetCounterHidden -> setHidden(action)
        is GameAction.Group -> when {
            action.actions.isEmpty() -> invalidInput("Group ${action.groupId.value} has no actions")
            action.actions.any { it is GameAction.Group } -> invalidInput("Group ${action.groupId.value} nests another group")
            else -> action.actions.firstNotNullOfOrNull { apply(it) }
        }
        else -> invalidInput("${action::class.simpleName} is not a counter action")
    }

    private fun adjust(action: GameAction.AdjustCounter): Outcome.Failure? {
        val definition = definitions[action.definitionId] ?: return invalidInput("Unknown counter definition ${action.definitionId.value}")
        if (!seats.contains(action.seatId)) return invalidInput("Unknown seat ${action.seatId.value}")
        scopeError(action.definitionId, action.seatId, action.commanderSlotId)?.let { return invalidInput(it) }
        if (action.delta == 0) return invalidInput("A zero adjustment is not a change")
        val key = CounterKey(action.definitionId, action.seatId, action.commanderSlotId)
        val existing = states[key]
        val next = (existing?.value ?: definition.initial).toLong() + action.delta
        if (next < Int.MIN_VALUE || next > Int.MAX_VALUE) return invalidInput("Counter ${definition.id.value} would overflow to $next")
        boundsError(definition, next)?.let { return invalidInput(it) }
        states[key] = existing?.copy(value = next.toInt())
            ?: CounterState(action.definitionId, action.seatId, action.commanderSlotId, next.toInt(), inheritedHidden(action.seatId, action.definitionId))
        return null
    }

    /** Hides or shows every state of one seat's counter. Values are never reset; slot states are created at their initial value. */
    private fun setHidden(action: GameAction.SetCounterHidden): Outcome.Failure? {
        val definition = definitions[action.definitionId] ?: return invalidInput("Unknown counter definition ${action.definitionId.value}")
        if (!seats.contains(action.seatId)) return invalidInput("Unknown seat ${action.seatId.value}")
        val slots = slotsFor(action.seatId, action.definitionId)
        if (slots.isEmpty()) return invalidInput("Seat ${action.seatId.value} has no commander slots for counter ${definition.id.value}")
        val inherited = inheritedHidden(action.seatId, action.definitionId)
        val keys = slots.map { CounterKey(action.definitionId, action.seatId, it) }
        if (keys.all { (states[it]?.hidden ?: inherited) == action.hidden }) {
            return invalidInput("Counter ${definition.id.value} on seat ${action.seatId.value} is already ${if (action.hidden) "hidden" else "shown"}")
        }
        for (key in keys) {
            states[key] = states[key]?.copy(hidden = action.hidden)
                ?: CounterState(key.definitionId, key.seatId, key.commanderSlotId, definition.initial, action.hidden)
        }
        return null
    }

    /**
     * The states a seat's counter has, recorded or implicit at the initial value: one for a seat counter, the seat's own
     * slots for commander tax, and every opposing slot plus any recorded slot (a stolen own commander) for commander damage.
     */
    private fun slotsFor(seatId: EntityId, definitionId: EntityId): List<EntityId?> = when (scopeOf(definitionId)) {
        CounterScope.SEAT -> listOf(null)
        CounterScope.ANY_COMMANDER -> (seats.slotsOpposing(seatId) + existingSlots(seatId, definitionId)).distinct()
        CounterScope.OWN_COMMANDER -> seats.slotsOwnedBy(seatId)
    }

    private fun existingSlots(seatId: EntityId, definitionId: EntityId): List<EntityId> =
        states.keys.filter { it.seatId == seatId && it.definitionId == definitionId }.mapNotNull { it.commanderSlotId }

    /** A state created later follows its siblings, so a hidden counter does not reappear slot by slot. */
    private fun inheritedHidden(seatId: EntityId, definitionId: EntityId): Boolean {
        val siblings = states.values.filter { it.seatId == seatId && it.definitionId == definitionId }
        return siblings.isNotEmpty() && siblings.all { it.hidden }
    }

    private fun scopeError(definitionId: EntityId, seatId: EntityId, slotId: EntityId?): String? = when (scopeOf(definitionId)) {
        CounterScope.SEAT -> if (slotId == null) null else "Counter ${definitionId.value} is per seat and takes no commander slot"
        CounterScope.ANY_COMMANDER -> when {
            slotId == null -> "Commander damage requires the source commander slot"
            seats.ownerOfSlot(slotId) == null -> "Unknown commander slot ${slotId.value}"
            else -> null
        }
        CounterScope.OWN_COMMANDER -> when {
            slotId == null -> "Commander tax requires the seat's own commander slot"
            seats.ownerOfSlot(slotId) == null -> "Unknown commander slot ${slotId.value}"
            seats.ownerOfSlot(slotId) != seatId -> "Commander slot ${slotId.value} does not belong to seat ${seatId.value}"
            else -> null
        }
    }

    /**
     * Recorded states first, then implicit ones in table order. An absent state means its initial value, so whether a
     * state happens to be recorded (for example by hiding) never changes the assessment.
     */
    fun lossCandidates(isNew: (CounterKey, CounterLossPolicy) -> Boolean): List<Pair<LossCandidate, Boolean>> {
        val keys = LinkedHashSet(states.keys)
        for (seatId in seats.orderedSeatIds()) {
            for (definitionId in definitions.keys) slotsFor(seatId, definitionId).forEach { keys += CounterKey(definitionId, seatId, it) }
        }
        return keys.mapNotNull { key ->
            val policy = definition(key).lossPolicy
            val value = states[key]?.value ?: definition(key).initial
            if (!CounterReducer.meets(policy, value)) null
            else LossCandidate(key.seatId, key.definitionId, key.commanderSlotId, value, policy) to isNew(key, policy)
        }
    }

    companion object {
        fun of(snapshot: GameSnapshot, roles: CounterRoles): Outcome<CounterBoard> {
            val seats = SeatIndex.of(snapshot).valueOr { return it }
            val definitions = LinkedHashMap<EntityId, CounterDefinition>()
            for (definition in snapshot.settings.counterDefinitions) {
                if (definitions.put(definition.id, definition) != null) return corruptData("Duplicate counter definition ${definition.id.value}")
            }
            val bound = listOfNotNull(roles.life, roles.commanderDamage, roles.commanderTax)
            if (bound.toSet().size != bound.size) return invalidInput("Counter roles must bind distinct definitions")
            bound.firstOrNull { it !in definitions }?.let { return invalidInput("Counter role references unknown definition ${it.value}") }
            val life = definitions.getValue(roles.life)
            if (life.initial != snapshot.settings.initialLife) {
                return corruptData("Life definition initial ${life.initial} disagrees with game initial life ${snapshot.settings.initialLife}")
            }
            val board = CounterBoard(seats, definitions, roles, LinkedHashMap())
            for (state in snapshot.counters) {
                val definition = definitions[state.definitionId] ?: return corruptData("Counter state references unknown definition ${state.definitionId.value}")
                if (!seats.contains(state.seatId)) return corruptData("Counter state references unknown seat ${state.seatId.value}")
                board.scopeError(state.definitionId, state.seatId, state.commanderSlotId)?.let { return corruptData(it) }
                boundsError(definition, state.value.toLong())?.let { return corruptData(it) }
                val key = CounterKey(state.definitionId, state.seatId, state.commanderSlotId)
                if (board.states.put(key, state) != null) return corruptData("Duplicate counter state for ${state.definitionId.value} on seat ${state.seatId.value}")
            }
            return Outcome.Success(board)
        }

        private fun boundsError(definition: CounterDefinition, value: Long): String? {
            val minimum = definition.minimum
            val maximum = definition.maximum
            return when {
                minimum != null && value < minimum -> "Counter ${definition.id.value} value $value is below minimum $minimum"
                maximum != null && value > maximum -> "Counter ${definition.id.value} value $value is above maximum $maximum"
                else -> null
            }
        }
    }
}
