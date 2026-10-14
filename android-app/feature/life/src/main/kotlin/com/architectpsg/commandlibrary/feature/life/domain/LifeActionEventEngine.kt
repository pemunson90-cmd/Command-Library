package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*

internal data class ActionEventAllocation(val eventId: Long, val nextRevision: Revision, val groupId: EntityId,
    val freshResultId: EntityId? = null, val turnLineage: EntityId? = null)
internal sealed interface RandomExecution {
    data class Resolve(val source: RandomSource, val context: RandomRequestContext? = null) : RandomExecution
    data class Replay(val recorded: RecordedRandomResult, val context: RandomRequestContext? = null) : RandomExecution
}
internal fun interface LifeVariantActionBackend {
    fun reduce(snapshot: GameSnapshot, action: GameAction.Variant): Outcome<VariantState>
}
internal data class ActionEventReduction(val archive: GameEventArchive, val checkpoint: GameCheckpoint,
    val alerts: SnapshotList<GameAlert>, val lossCandidates: SnapshotList<LossCandidate>, val newlyReached: SnapshotList<LossCandidate>,
    val timedOutSeats: SnapshotList<EntityId>, val sharedTimedOut: Boolean)
/** Pure proposed packets. No transaction, source authentication, target selection or publication. */
internal object LifeActionEventEngine {
    fun dispatch(current: GameCheckpoint, action: GameAction, reading: GameTimeObservation, allocation: ActionEventAllocation,
        random: RandomExecution? = null, variants: LifeVariantActionBackend? = null): Outcome<ActionEventReduction> {
        validAllocation(current.snapshot, action, allocation)?.let { return it }
        val actions = (action as? GameAction.Group)?.actions?.toList() ?: listOf(action)
        if (actions.size !in 1..1024 || actions.any { it is GameAction.Group } || actions.count { it is GameAction.SwitchTurn } > 1) return invalidInput("Action group must be bounded/flat with at most one turn switch")
        if (action is GameAction.Group && actions.any { it is GameAction.RequestRandom }) return invalidInput("Random requests cannot be grouped")
        val resultIndex = actions.indexOfFirst { it.resultControl() }
        if (actions.count { it.resultControl() } > 1 || (resultIndex >= 0 && actions.drop(resultIndex + 1).any { it !is GameAction.AcknowledgeGameAlert })) return invalidInput("Result control must be single and terminal except acknowledgments")
        if (actions.any { it is GameAction.SwitchTurn } && allocation.turnLineage == null) return invalidInput("Turn switch requires allocated fresh lineage")
        val settled = LifeTimingEngine.settle(current.snapshot, reading).valueOr { return it }
        var working = settled.snapshot
        var initialAutoPause = !current.snapshot.clock.paused && settled.snapshot.clock.paused
        val canonical = ArrayList<GameAction>()
        val recorded = ArrayList<RecordedRandomResult>()
        val timedOut = settled.timedOutSeats.toMutableSet()
        var shared = settled.sharedTimedOut
        fun accept(timing: TimingReduction) { working = timing.snapshot; timedOut += timing.timedOutSeats; shared = shared || timing.sharedTimedOut }
        for (item in actions) {
            var logged = item
            when (item) {
                is GameAction.SetCounter, is GameAction.AdjustCounter, is GameAction.SetCounterHidden,
                is GameAction.SetAdjustmentStep, is GameAction.SetCommanderLifeDeduction,
                is GameAction.AddCounterDefinition, is GameAction.UpdateCounterDefinition -> {
                    val reduced = LifeCounterEngine.reduce(working, item).valueOr { return it }
                    working = reduced.snapshot; logged = reduced.action
                }
                is GameAction.ClockPause -> {
                    if (!(item.paused && initialAutoPause && working.clock.paused)) {
                        accept(LifeTimingControls.countdownPause(working, item.paused, reading).valueOr { return it })
                    }
                    initialAutoPause = false
                }
                is GameAction.SetElapsedPaused -> accept(LifeTimingControls.elapsedPause(working, item.paused, reading).valueOr { return it })
                is GameAction.PassClock -> accept(LifeTimingControls.passClock(working, item.seatId, reading).valueOr { return it })
                is GameAction.SwitchTurn -> {
                    accept(LifeTimingControls.switchTurn(working, item.seatId, allocation.turnLineage!!, reading).valueOr { return it })
                    working = LifeCounterEngine.resetForTurn(working, item.seatId).valueOr { return it }
                }
                is GameAction.Finish, is GameAction.FinishWithOutcomes, is GameAction.Reopen -> {
                    val reduced = LifeResultEngine.reduce(working, item, reading,
                        ResultEventAllocation(allocation.eventId, allocation.nextRevision, allocation.freshResultId)).valueOr { return it }
                    working = reduced.snapshot; timedOut += reduced.timedOutSeats; shared = shared || reduced.sharedTimedOut
                }
                is GameAction.RequestRandom -> {
                    val reduced = when (random) {
                        is RandomExecution.Resolve -> LifeRandomEngine.resolve(working, item, reading.wall, random.source, random.context)
                        is RandomExecution.Replay -> LifeRandomEngine.replay(working, item, random.recorded, random.context)
                        null -> return invalidInput("Random request requires explicit resolution or recorded replay")
                    }.valueOr { return it }
                    working = reduced.snapshot; recorded += reduced.recorded
                }
                is GameAction.Variant -> working = variant(working, item, variants).valueOr { return it }
                is GameAction.Group -> return invalidInput("Nested group")
                else -> {
                    val reduced = LifeTableEngine.reduce(working, item, reading).valueOr { return it }
                    working = reduced.snapshot; timedOut += reduced.timedOutSeats; shared = shared || reduced.sharedTimedOut
                }
            }
            canonical += logged
        }
        val logged = if (action is GameAction.Group) action.copy(actions = SnapshotList.copyOf(canonical)) else canonical.single()
        val after = working.copy(revision = allocation.nextRevision, lastEventId = Knowledge.Known(allocation.eventId))
        return packet(current, settled.snapshot, after, logged, reading, allocation, Knowledge.Absent("User action"),
            SnapshotList.copyOf(recorded), SnapshotList.copyOf(timedOut), shared)
    }

    fun undo(current: GameCheckpoint, target: GameEventArchive, reading: GameTimeObservation,
        allocation: ActionEventAllocation): Outcome<ActionEventReduction> {
        validAllocation(current.snapshot, target.event.action, allocation)?.let { return it }
        val reduced = LifeSemanticUndo.undo(target, current.snapshot, reading,
            ResultEventAllocation(allocation.eventId, allocation.nextRevision, allocation.freshResultId)).valueOr { return it }
        return packet(current, reduced.settledCurrent, reduced.snapshot, target.event.action, reading, allocation,
            Knowledge.Known(target.event.id), SnapshotList.empty(), reduced.timedOutSeats, reduced.sharedTimedOut)
    }

    private fun packet(current: GameCheckpoint, before: GameSnapshot, after: GameSnapshot, action: GameAction,
        reading: GameTimeObservation, allocation: ActionEventAllocation, undoOf: Knowledge<Long>, recorded: SnapshotList<RecordedRandomResult>,
        timedOut: SnapshotList<EntityId>, shared: Boolean): Outcome<ActionEventReduction> {
        val oldRoles = SnapshotGraphValidator.validate(before).valueOr { return it }
        val roles = SnapshotGraphValidator.validate(after).valueOr { return it }
        val oldCandidates = CounterReducer.assessLoss(before, oldRoles).valueOr { return it }.map { it.key() }.toHashSet()
        val candidates = CounterReducer.assessLoss(after, roles).valueOr { return it }
        val event = GameEvent(allocation.eventId, after.id, allocation.groupId, action, reading.wall, undoOf, allocation.nextRevision, recorded)
        val archive = GameEventArchive(1, event, GameActionProvenance(event.id, before, after, reading))
        val oldAlerts = current.snapshot.alerts.map { it.id }.toHashSet()
        return Outcome.Success(ActionEventReduction(archive, GameCheckpoint(after, Knowledge.Known(event.id), current.recoveredProcessSessionId),
            SnapshotList.copyOf(after.alerts.filter { it.id !in oldAlerts }), candidates,
            SnapshotList.copyOf(candidates.filter { it.key() !in oldCandidates }), timedOut, shared))
    }

    private fun variant(snapshot: GameSnapshot, action: GameAction.Variant, backend: LifeVariantActionBackend?): Outcome<GameSnapshot> {
        if (snapshot.status == GameStatus.COMPLETED) return invalidInput("Reopen before variant actions")
        val definition = snapshot.settings.variantDefinitions.firstOrNull { it.id == action.definitionId } ?: return invalidInput("Unknown variant definition")
        val state = snapshot.variants.singleOrNull { it.definitionId == definition.id } ?: return invalidInput("Missing variant state")
        val command = definition.allowedActions.firstOrNull { it.id == action.actionId } ?: return invalidInput("Unknown variant action")
        if (action.selectedCards.any { id -> definition.cards.none { it.id == id } }) return invalidInput("Unknown selected variant card")
        val count = state.actionCounts.firstOrNull { it.actionId == action.actionId }?.count ?: 0
        if (command.maximumPerPhase?.let { count >= it } == true) return invalidInput("Variant action reached declared phase limit")
        val implementation = backend ?: return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Variant action requires authentic versioned rule backend")
        val next = implementation.reduce(snapshot, action).valueOr { return it }
        if (next.definitionId != definition.id || next.version != definition.version) return invalidInput("Variant backend changed definition identity/version")
        val proposed = snapshot.copy(variants = SnapshotList.copyOf(snapshot.variants.map { if (it.definitionId == definition.id) next else it }))
        when (val checked = SnapshotGraphValidator.validate(proposed)) {
            is Outcome.Success -> Unit
            is Outcome.Failure -> return invalidInput(checked.error.message)
            is Outcome.Unavailable -> return checked
            is Outcome.Unknown -> return checked
        }
        return Outcome.Success(proposed)
    }

    private fun validAllocation(current: GameSnapshot, action: GameAction, allocation: ActionEventAllocation): Outcome.Failure? =
        if (allocation.eventId < 0 || current.revision.value == Long.MAX_VALUE || allocation.nextRevision.value != current.revision.value + 1 ||
            (action is GameAction.Group && action.groupId != allocation.groupId)) invalidInput("Invalid next event/revision/group allocation") else null
    private fun GameAction.resultControl(): Boolean = this is GameAction.Finish || this is GameAction.FinishWithOutcomes || this is GameAction.Reopen
    private fun LossCandidate.key(): Triple<EntityId, EntityId, EntityId?> = Triple(definitionId, seatId, commanderSlotId)
}
