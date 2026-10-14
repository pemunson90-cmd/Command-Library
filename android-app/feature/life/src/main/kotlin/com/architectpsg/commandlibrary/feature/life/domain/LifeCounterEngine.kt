package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*

/** Counter-only semantics. The later event orchestrator owns settlement, revision and atomic persistence. */
internal object LifeCounterEngine {
    fun reduce(snapshot: GameSnapshot, action: GameAction): Outcome<CounterReduction> {
        val roles = SnapshotGraphValidator.validate(snapshot).valueOr { return it }
        if (snapshot.status == GameStatus.COMPLETED) return invalidInput("Reopen the game before changing counters")
        val actions = if (action is GameAction.Group) action.actions.toList() else listOf(action)
        if (actions.size !in 1..1024 || actions.any { it is GameAction.Group }) return invalidInput("Counter group must be flat and contain 1..1024 actions")
        val before = CounterReducer.assessLoss(snapshot, roles).valueOr { return it }.map { it.key() }.toHashSet()
        var current = snapshot
        val canonical = ArrayList<GameAction>(actions.size)
        for (item in actions) {
            val changed = applyOne(current, roles, item).valueOr { return it }
            current = changed.first
            canonical += changed.second
        }
        SnapshotGraphValidator.validate(current).valueOr { return it }
        val candidates = CounterReducer.assessLoss(current, roles).valueOr { return it }
        val logged = if (action is GameAction.Group) action.copy(actions = SnapshotList.copyOf(canonical)) else canonical.single()
        return Outcome.Success(CounterReduction(current, logged, candidates, SnapshotList.copyOf(candidates.filter { it.key() !in before })))
    }

    /** This helper expands damage/life exactly once; raw edits and canonical groups remain literal. */
    fun commanderDamage(snapshot: GameSnapshot, victim: EntityId, sourceSlot: EntityId, amount: Int, groupId: EntityId): Outcome<CounterReduction> {
        val roles = SnapshotGraphValidator.validate(snapshot).valueOr { return it }
        return CounterReducer.commanderDamage(snapshot, roles, victim, sourceSlot, amount, snapshot.settings.deductLifeForCommanderDamage, groupId)
    }

    /** Every turn boundary resets ALL marked explicit states; priority passing must not call this. */
    fun resetForTurn(snapshot: GameSnapshot, incomingSeat: EntityId): Outcome<GameSnapshot> {
        SnapshotGraphValidator.validate(snapshot).valueOr { return it }
        if (snapshot.status == GameStatus.COMPLETED) return invalidInput("Reopen the game before resetting turn counters")
        if (snapshot.seats.none { it.id == incomingSeat && it.participation is SeatParticipation.Active }) return invalidInput("Turn target is not an active participant")
        val definitions = snapshot.settings.counterDefinitions.associateBy { it.id }
        val reset = snapshot.counters.map { state ->
            val definition = definitions.getValue(state.definitionId)
            if (definition.resetPolicy == CounterResetPolicy.ON_TURN_START) state.copy(value = definition.initial) else state
        }
        val result = snapshot.copy(counters = SnapshotList.copyOf(reset))
        SnapshotGraphValidator.validate(result).valueOr { return it }
        return Outcome.Success(result)
    }

    private fun applyOne(snapshot: GameSnapshot, roles: CounterRoles, action: GameAction): Outcome<Pair<GameSnapshot, GameAction>> {
        val canonical = if (action is GameAction.SetCounter) {
            val definition = snapshot.settings.counterDefinitions.firstOrNull { it.id == action.definitionId }
                ?: return invalidInput("Unknown counter definition")
            val value = snapshot.counters.firstOrNull { it.definitionId == action.definitionId && it.seatId == action.seatId && it.commanderSlotId == action.commanderSlotId }?.value ?: definition.initial
            val delta = action.value.toLong() - value.toLong()
            if (delta == 0L || delta !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return invalidInput("Exact counter edit needs a nonzero representable logged delta")
            GameAction.AdjustCounter(action.seatId, action.definitionId, action.commanderSlotId, delta.toInt())
        } else action
        val result = when (canonical) {
            is GameAction.AdjustCounter, is GameAction.SetCounterHidden -> CounterReducer.reduce(snapshot, roles, canonical).valueOr { return it }.snapshot
            is GameAction.SetAdjustmentStep -> snapshot.copy(settings = snapshot.settings.copy(adjustmentStep = canonical.step))
            is GameAction.SetCommanderLifeDeduction -> snapshot.copy(settings = snapshot.settings.copy(deductLifeForCommanderDamage = canonical.enabled))
            is GameAction.AddCounterDefinition -> {
                val definitions = snapshot.settings.counterDefinitions
                if (canonical.definition.role != CounterRole.OTHER || definitions.any { it.id == canonical.definition.id } || definitions.size >= 128) return invalidInput("New counter requires a distinct OTHER identity within the 128-definition limit")
                proposal(snapshot.copy(settings = snapshot.settings.copy(counterDefinitions = SnapshotList.copyOf(definitions + canonical.definition)))).valueOr { return it }
            }
            is GameAction.UpdateCounterDefinition -> {
                val definitions = snapshot.settings.counterDefinitions
                val old = definitions.firstOrNull { it.id == canonical.definition.id } ?: return invalidInput("Unknown counter definition")
                if (canonical.definition.role != old.role) return invalidInput("Counter role cannot change")
                proposal(snapshot.copy(settings = snapshot.settings.copy(counterDefinitions = SnapshotList.copyOf(definitions.map {
                    if (it.id == canonical.definition.id) canonical.definition else it
                })))).valueOr { return it }
            }
            else -> return invalidInput("Action is not a counter edit or counter preference")
        }
        return Outcome.Success(result to canonical)
    }

    /** A malformed requested graph is invalid input, distinct from corruption of the authoritative input snapshot. */
    private fun proposal(snapshot: GameSnapshot): Outcome<GameSnapshot> = when (val checked = SnapshotGraphValidator.validate(snapshot)) {
        is Outcome.Success -> Outcome.Success(snapshot)
        is Outcome.Failure -> invalidInput(checked.error.message)
        is Outcome.Unavailable -> checked
        is Outcome.Unknown -> checked
    }
    private fun LossCandidate.key(): Triple<EntityId, EntityId, EntityId?> = Triple(definitionId, seatId, commanderSlotId)
}
