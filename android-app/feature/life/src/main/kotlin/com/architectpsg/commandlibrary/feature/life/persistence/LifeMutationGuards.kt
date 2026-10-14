package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.life.domain.SnapshotGraphValidator

internal fun mutationGraph(game: GameSnapshot, code: FailureCode) {
    when (val checked = SnapshotGraphValidator.validate(game)) {
        is Outcome.Success -> Unit
        is Outcome.Failure -> rejectGame(code, checked.error.message)
        else -> if (code == FailureCode.INVALID_INPUT) rejectGame(code, "Unsupported new snapshot graph") else gameValue(checked)
    }
}
internal fun carryLifeAlerts(before: GameSnapshot, after: GameSnapshot, code: FailureCode) {
    val alerts = after.alerts.associateBy { it.id }
    val fired = after.clock.firedAlertIds.toHashSet()
    requireGame(before.clock.firedAlertIds.all { it in fired }, code, "Fired alert history cannot shrink")
    for (old in before.alerts) {
        val next = alerts[old.id] ?: rejectGame(code, "Alert occurrence cannot disappear")
        requireGame(next.copy(acknowledgedAt = old.acknowledgedAt) == old &&
            (old.acknowledgedAt == null || next.acknowledgedAt == old.acknowledgedAt), code, "Alert identity/acknowledgment cannot be rewritten")
    }
}
/** The ONLY allowed difference between immutable action state and later system settlement. */
internal fun lifeSettlement(before: GameSnapshot, after: GameSnapshot, code: FailureCode) {
    requireGame(after.copy(clock = before.clock, elapsed = before.elapsed, alerts = before.alerts) == before, code, "Settlement changed non-timing state")
    val old = before.clock; val next = after.clock
    requireGame(old.copy(seats = next.seats, shared = next.shared, anchor = next.anchor, paused = next.paused, firedAlertIds = next.firedAlertIds) == next &&
        (!old.paused || next.paused), code, "Settlement changed clock configuration/owner or resumed it")
    val banks = next.seats.associateBy { it.seatId }
    requireGame(banks.size == old.seats.size && banks.keys == old.seats.map { it.seatId }.toSet(), code, "Settlement changed bank identities")
    for (bank in old.seats) {
        val new = banks.getValue(bank.seatId)
        requireGame(new.initial == bank.initial && (if (!old.paused && old.activeSeat == bank.seatId) new.remaining <= bank.remaining else new.remaining == bank.remaining), code, "Settlement refilled/changed inactive countdown bank")
    }
    val shared = old.shared; val newShared = next.shared
    requireGame(if (shared == null) newShared == null else newShared != null && shared.initial == newShared.initial &&
        (if (old.paused) shared.remaining == newShared.remaining else newShared.remaining <= shared.remaining), code, "Settlement changed/refilled shared bank")
    val elapsed = before.elapsed; val changed = after.elapsed
    requireGame(elapsed.copy(gameElapsed = changed.gameElapsed, turnElapsed = changed.turnElapsed, anchor = changed.anchor, paused = changed.paused) == changed &&
        (!elapsed.paused || changed.paused) && (if (elapsed.paused) changed.gameElapsed == elapsed.gameElapsed && changed.turnElapsed == elapsed.turnElapsed else
            changed.gameElapsed >= elapsed.gameElapsed && changed.turnElapsed >= elapsed.turnElapsed), code, "Settlement resumed/refunded/reassigned elapsed time")
    carryLifeAlerts(before, after, code)
}
internal fun lifeActionPacket(current: StoredLifeGame, eventId: Long, write: LifeActionWrite) {
    val old = current.checkpoint.snapshot; val archive = write.archive; val event = archive.event
    val before = archive.provenance.before; val after = archive.provenance.afterAction
    val code = FailureCode.INVALID_INPUT
    requireGame(archive.version == 1 && event.id == eventId && event.gameId == old.id && event.revision.value == old.revision.value + 1 &&
        before.revision == old.revision && after.id == old.id && after.version == old.version && after.startedAt == old.startedAt, code, "Invalid new action envelope")
    mutationGraph(before, code); mutationGraph(after, code); lifeSettlement(old, before, code)
    requireGame(after.elapsed.gameElapsed >= before.elapsed.gameElapsed, code, "Actions cannot refund game elapsed time")
    requireGame(write.checkpoint.snapshot == after && write.checkpoint.lastSettledEventId == Knowledge.Known(eventId) &&
        write.checkpoint.recoveredProcessSessionId == current.checkpoint.recoveredProcessSessionId, code, "Action checkpoint/provenance mismatch")
    requireGame(event.at == archive.provenance.reading.wall, code, "New action observation/timestamp mismatch")
    val group = event.action as? GameAction.Group
    requireGame(group == null || group.groupId == event.groupId, code, "Action/event group mismatch")
    requireGame(event.undoOfEventId !is Knowledge.Unknown, code, "New undo metadata is incomplete")
    requireGame(after.seats.map { it.id }.toSet() == old.seats.map { it.id }.toSet(), code, "Action replaced roster identities")
    val oldSlots = old.seats.flatMap { seat -> seat.commanders.map { Triple(it.id, seat.id, it.identity) } }.toSet()
    val newSlots = after.seats.flatMap { seat -> seat.commanders.map { Triple(it.id, seat.id, it.identity) } }.toSet()
    requireGame(oldSlots == newSlots, code, "Action replaced commander semantic identities")
    val definitions = after.settings.counterDefinitions.associateBy { it.id }
    requireGame(old.settings.counterDefinitions.all { definitions[it.id]?.role == it.role } &&
        after.settings.counterDefinitions.filter { next -> old.settings.counterDefinitions.none { it.id == next.id } }.all { it.role == CounterRole.OTHER }, code, "Action deleted/rebound counter definitions")
    carryLifeAlerts(before, after, code)
}
internal fun lifeResultChange(before: GameSnapshot, after: GameSnapshot): LifeResultRow? {
    val code = FailureCode.INVALID_INPUT
    val old = (before.result as? Knowledge.Known)?.value
    val next = when (val value = after.result) {
        is Knowledge.Known -> value.value
        is Knowledge.Absent -> null
        is Knowledge.Unknown -> rejectGame(code, "New result knowledge is incomplete")
    }
    requireGame(old == null || next != null, code, "Recorded result cannot disappear")
    if (next == old) return null
    if (next == null) return null
    requireGame(next.revision == after.revision && (old == null || old.id == next.id), code, "Changed result revision/identity mismatch")
    requireGame(old != null || !next.retracted, code, "First result cannot be retracted")
    if (next.retracted) requireGame(next.copy(revision = old!!.revision, retracted = old.retracted) == old, code, "Retraction rewrote result facts")
    else requireGame(next.seatResults.map { it.seatId }.toSet() == after.seats.map { it.id }.toSet() && next.seatResults.size == after.seats.size, code, "New confirmation requires complete seat outcomes")
    return LifeResultRow.of(next, code)
}
