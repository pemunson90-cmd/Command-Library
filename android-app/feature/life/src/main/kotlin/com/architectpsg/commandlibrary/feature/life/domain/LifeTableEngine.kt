package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*

internal data class TableReduction(
    val settledBefore: GameSnapshot, val snapshot: GameSnapshot, val action: GameAction,
    val alerts: SnapshotList<GameAlert>, val timedOutSeats: SnapshotList<EntityId>, val sharedTimedOut: Boolean,
)

/** Explicit table decisions; timing candidates never dispatch elimination or finish automatically. */
internal object LifeTableEngine {
    fun reduce(snapshot: GameSnapshot, action: GameAction, reading: GameTimeObservation): Outcome<TableReduction> {
        val roles = SnapshotGraphValidator.validate(snapshot).valueOr { return it }
        val actions = if (action is GameAction.Group) action.actions.toList() else listOf(action)
        if (actions.size !in 1..1024 || actions.any { it is GameAction.Group }) return invalidInput("Table group must be flat and contain 1..1024 actions")
        if (snapshot.status == GameStatus.COMPLETED && actions.any { it !is GameAction.AcknowledgeGameAlert }) return invalidInput("Reopen the completed game before editing table state")
        val settled = LifeTimingEngine.settle(snapshot, reading).valueOr { return it }
        var current = settled.snapshot
        for (item in actions) current = applyOne(current, roles, item, reading).valueOr { return it }
        SnapshotGraphValidator.validate(current).valueOr { return it }
        val alerts = current.alerts.associateBy { it.id }
        return Outcome.Success(TableReduction(settled.snapshot, current, action,
            SnapshotList.copyOf(settled.alerts.map { alerts.getValue(it.id) }), settled.timedOutSeats, settled.sharedTimedOut))
    }

    private fun applyOne(snapshot: GameSnapshot, roles: CounterRoles, action: GameAction, reading: GameTimeObservation): Outcome<GameSnapshot> {
        val result = when (action) {
            is GameAction.RenameSeat -> return changeSeat(snapshot, action.seatId) { it.copy(playerName = action.name) }
            is GameAction.ReorderSeats -> return Outcome.Success(RandomTools.reorderSeats(snapshot, action).valueOr { return it }.snapshot)
            is GameAction.SetSeatAppearance -> return changeSeat(snapshot, action.seatId) { it.copy(appearance = action.appearance) }
            is GameAction.SetGamePresentation -> snapshot.copy(settings = snapshot.settings.copy(presentation = action.presentation))
            is GameAction.SetAlertPreferences -> snapshot.copy(settings = snapshot.settings.copy(alertPreferences = action.preferences))
            is GameAction.EliminateSeat -> {
                val seat = snapshot.seats.firstOrNull { it.id == action.seatId } ?: return invalidInput("Unknown seat")
                if (seat.participation !is SeatParticipation.Active) return invalidInput("Seat is already eliminated")
                action.evidence?.let { evidence -> evidenceError(snapshot, roles, seat.id, evidence)?.let { return it } }
                val changed = changeSeat(snapshot, seat.id) { it.copy(participation = SeatParticipation.Eliminated(action.kind, action.reason, action.evidence, reading.wall)) }.valueOr { return it }
                val clock = if (!snapshot.clock.paused && snapshot.clock.activeSeat == seat.id) snapshot.clock.copy(paused = true) else snapshot.clock
                val elapsed = if (!snapshot.elapsed.paused && snapshot.activeSeatId == seat.id) snapshot.elapsed.copy(paused = true) else snapshot.elapsed
                changed.copy(clock = clock, elapsed = elapsed)
            }
            is GameAction.RestoreSeat -> {
                val seat = snapshot.seats.firstOrNull { it.id == action.seatId } ?: return invalidInput("Unknown seat")
                if (seat.participation !is SeatParticipation.Eliminated) return invalidInput("Seat is already active")
                return changeSeat(snapshot, seat.id) { it.copy(participation = SeatParticipation.Active) }
            }
            is GameAction.DismissLossCandidate -> {
                if (action.candidate.definitionId == null) return invalidInput("Counter dismissal needs a current counter observation; acknowledge clock alerts separately")
                evidenceError(snapshot, roles, action.seatId, action.candidate)?.let { return it }
                snapshot // Override is logged in the action, never a permanent threshold-disable bit.
            }
            is GameAction.SetMonarch -> {
                if (action.seatId?.let { !active(snapshot, it) } == true) return invalidInput("Monarch target is not an active seat")
                snapshot.copy(designations = snapshot.designations.copy(monarch = action.seatId))
            }
            is GameAction.SetInitiative -> {
                if (action.seatId?.let { !active(snapshot, it) } == true) return invalidInput("Initiative target is not an active seat")
                snapshot.copy(designations = snapshot.designations.copy(initiative = action.seatId, initiativeNote = action.note))
            }
            is GameAction.SetDayNight -> snapshot.copy(designations = snapshot.designations.copy(dayNight = action.state))
            is GameAction.SetBlessing -> {
                if (snapshot.seats.none { it.id == action.seatId } || (action.blessed && !active(snapshot, action.seatId))) return invalidInput("Blessing target is not eligible")
                val blessed = snapshot.designations.blessedSeats.toHashSet()
                if (action.blessed) blessed.add(action.seatId) else blessed.remove(action.seatId)
                val ordered = snapshot.seats.sortedBy { it.order }.map { it.id }.filter { it in blessed }
                snapshot.copy(designations = snapshot.designations.copy(blessedSeats = SnapshotList.copyOf(ordered)))
            }
            is GameAction.AcknowledgeGameAlert -> {
                val alert = snapshot.alerts.firstOrNull { it.id == action.alertId }
                    ?: return Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Unknown game alert occurrence")
                if (alert.acknowledgedAt != null) snapshot else snapshot.copy(alerts = SnapshotList.copyOf(snapshot.alerts.map {
                    if (it.id == alert.id) it.copy(acknowledgedAt = reading.wall) else it
                }))
            }
            else -> return invalidInput("Action is not a table decision")
        }
        return Outcome.Success(result)
    }

    private fun changeSeat(snapshot: GameSnapshot, id: EntityId, change: (Seat) -> Seat): Outcome<GameSnapshot> {
        if (snapshot.seats.none { it.id == id }) return invalidInput("Unknown seat")
        return Outcome.Success(snapshot.copy(seats = SnapshotList.copyOf(snapshot.seats.map { if (it.id == id) change(it) else it })))
    }
    private fun active(snapshot: GameSnapshot, id: EntityId): Boolean = snapshot.seats.any { it.id == id && it.participation is SeatParticipation.Active }

    /** Checks local counter observation, not external rule/source authority. Non-counter manual evidence stays declared. */
    private fun evidenceError(snapshot: GameSnapshot, roles: CounterRoles, seat: EntityId, evidence: LossEvidence): Outcome.Failure? {
        if (snapshot.seats.none { it.id == seat }) return invalidInput("Unknown evidence seat")
        if (evidence.commanderSlotId != null && snapshot.seats.none { owner -> owner.commanders.any { it.id == evidence.commanderSlotId } }) return invalidInput("Unknown evidence commander slot")
        if (evidence.definitionId == null) return null
        val candidates = CounterReducer.assessLoss(snapshot, roles).valueOr { return invalidInput("Cannot assess counter observation") }
        return if (candidates.any { it.seatId == seat && it.definitionId == evidence.definitionId && it.commanderSlotId == evidence.commanderSlotId &&
                it.value == evidence.observedValue && it.policy == evidence.policy }) null else invalidInput("Loss evidence does not match a current counter candidate")
    }
}
