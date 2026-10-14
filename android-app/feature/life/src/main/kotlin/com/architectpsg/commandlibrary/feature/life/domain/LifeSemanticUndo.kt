package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*

internal data class SemanticUndoReduction(val settledCurrent: GameSnapshot, val snapshot: GameSnapshot,
    val alerts: SnapshotList<GameAlert>, val timedOutSeats: SnapshotList<EntityId>, val sharedTimedOut: Boolean)

/** Service must authenticate latest eligible target in its transaction; this helper never selects history. */
internal object LifeSemanticUndo {
    fun undo(target: GameEventArchive, current: GameSnapshot, reading: GameTimeObservation,
        allocation: ResultEventAllocation): Outcome<SemanticUndoReduction> {
        if (target.version != 1) return unsupported("Unsupported undo archive version")
        when (target.event.undoOfEventId) {
            is Knowledge.Known -> return invalidInput("An undo record cannot itself be undone")
            is Knowledge.Unknown -> return unsupported("Undo target metadata requires repair")
            is Knowledge.Absent -> Unit
        }
        if (allocation.eventId < 0 || current.revision.value == Long.MAX_VALUE || allocation.nextRevision.value != current.revision.value + 1 ||
            allocation.freshResultId != null) return invalidInput("Undo requires next event/revision without a fresh result identity")
        val before = target.provenance.before
        val after = target.provenance.afterAction
        SnapshotGraphValidator.validate(before).valueOr { return it }
        val actions = (target.event.action as? GameAction.Group)?.actions?.toList() ?: listOf(target.event.action)
        if (actions.isEmpty() || actions.size > 1024 || actions.any { it is GameAction.Group }) return unsupported("Undo needs a bounded flat action group")
        val added = actions.filterIsInstance<GameAction.AddCounterDefinition>().map { it.definition.id }.toHashSet()
        val finalDefinitions = after.settings.counterDefinitions.associateBy { it.id }
        val oldIds = before.settings.counterDefinitions.map { it.id }.toHashSet()
        val extras = current.settings.counterDefinitions.filter { it.id !in oldIds }
        for (definition in extras) {
            if (definition.role != CounterRole.OTHER) return invalidInput("Undo cannot retain an extra commander-scoped definition")
            if (definition.id in added && finalDefinitions[definition.id] == definition) continue
            // Later undone additions survive repeated undo as explicit hidden initial states.
            val rows = current.counters.filter { it.definitionId == definition.id }
            if (rows.size != current.seats.size || rows.map { it.seatId }.toSet() != current.seats.map { it.id }.toSet() ||
                rows.any { it.commanderSlotId != null || !it.hidden || it.value != definition.initial }) return invalidInput("Extra definition is neither target addition nor retained hidden initial state")
        }
        val timing = LifeTimingUndo.undo(target.provenance, target.event.action, current, reading).valueOr { return it }
        val live = timing.settledCurrent
        val result = reconcileResult(before, after, live, allocation.nextRevision).valueOr { return it }
        val retainedCounters = extras.flatMap { definition -> before.seats.map { CounterState(definition.id, it.id, null, definition.initial, true) } }
        val base = live.copy(revision = allocation.nextRevision, lastEventId = Knowledge.Known(allocation.eventId),
            settings = before.settings.copy(counterDefinitions = SnapshotList.copyOf(before.settings.counterDefinitions + extras)),
            seats = before.seats, counters = SnapshotList.copyOf(before.counters + retainedCounters), variants = before.variants,
            designations = before.designations, status = before.status, endedAt = before.endedAt, result = result)
        val composed = timing.compose(base).valueOr { return it }
        return Outcome.Success(SemanticUndoReduction(live, composed.snapshot, composed.alerts, composed.timedOutSeats, composed.sharedTimedOut))
    }

    private fun reconcileResult(before: GameSnapshot, after: GameSnapshot, current: GameSnapshot, revision: Revision): Outcome<Knowledge<GameResult>> {
        if (listOf(before.result, after.result, current.result).any { it is Knowledge.Unknown }) return unsupported("Undo result knowledge requires repair")
        val live = (current.result as? Knowledge.Known)?.value
        val original = (before.result as? Knowledge.Known)?.value
        if (original != null && (live == null || live.id != original.id)) return invalidInput("Undo cannot replace or lose stable result identity")
        if (before.status == GameStatus.COMPLETED) {
            if (after.status == GameStatus.COMPLETED) {
                if (live == null || live.retracted || current.status != GameStatus.COMPLETED || current.endedAt != before.endedAt) return invalidInput("Completed operational undo has incompatible result facts")
                return Outcome.Success(current.result)
            }
            if (original == null || original.seatResults.size != before.seats.size || original.seatResults.map { it.seatId }.toSet() != before.seats.map { it.id }.toSet()) {
                return unsupported("Reconfirming a historical result needs full explicit outcomes")
            }
            return Outcome.Success(Knowledge.Known(original.copy(revision = revision, retracted = false)))
        }
        return Outcome.Success(if (live == null) current.result else Knowledge.Known(if (live.retracted) live else live.copy(revision = revision, retracted = true)))
    }

    private fun unsupported(message: String): Outcome.Unavailable = Outcome.Unavailable(UnavailableReason.UNSUPPORTED, message)
}
