package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*

internal data class LifeOperationalChange(val result: LifeResultRow?, val inserted: List<GameAlert>, val acknowledged: List<GameAlert>)
internal suspend fun prepareLifeOperational(tx: DbTransaction, before: GameSnapshot, after: GameSnapshot, state: LifeOperationalState, cancel: CancelToken): LifeOperationalChange {
    carryLifeAlerts(before, after, FailureCode.INVALID_INPUT)
    val result = lifeResultChange(before, after)
    for (alert in after.alerts) {
        gameIdentity(alert.id.value, FailureCode.INVALID_INPUT)
        (alert.target as? GameAlertTarget.SeatClock)?.let { gameIdentity(it.seatId.value, FailureCode.INVALID_INPUT) }
    }
    val inserted = after.alerts.filter { it.id !in state.alerts }
    requireGame(findLifeAlerts(tx, after.id, inserted.map { it.id }, cancel).isEmpty(), FailureCode.INVALID_INPUT, "Alert occurrence identity was already recorded")
    val acknowledged = after.alerts.filter { next -> state.alerts[next.id]?.let { it.alert.acknowledgedAt != next.acknowledgedAt } == true }
    return LifeOperationalChange(result, inserted, acknowledged)
}
internal suspend fun writeLifeOperational(tx: DbTransaction, id: GameId, state: LifeOperationalState, change: LifeOperationalChange, cancel: CancelToken) {
    val result = change.result
    if (result != null) {
        val parameters = arrayOf(*lifeIdentityParameters(id), "result" to gameText(result.id), "revision" to gameNumber(result.revision),
            "seconds" to gameNumber(result.at.epochSeconds), "nano" to gameNumber(result.at.nanoAdjustment.toLong()), "reason" to gameText(result.reason), "retracted" to gameNumber(if (result.retracted) 1 else 0))
        val sql = if (state.result == null) """INSERT INTO life_result (game_source,game_id,result_id,revision,finished_seconds,finished_nano,reason,retracted)
            VALUES (:source,:id,:result,:revision,:seconds,:nano,:reason,:retracted)""" else """UPDATE life_result SET result_id=:result,revision=:revision,
            finished_seconds=:seconds,finished_nano=:nano,reason=:reason,retracted=:retracted WHERE game_source=:source AND game_id=:id"""
        gameOne(gameValue(tx.execute(gameRequest(sql, *parameters), cancel)))
    }
    for (alert in change.inserted) {
        val (kind, seat) = when (val target = alert.target) {
            is GameAlertTarget.SeatClock -> "seat_clock" to target.seatId.value
            GameAlertTarget.SharedCountdown -> "shared_countdown" to null
            GameAlertTarget.TurnTimer -> "turn_timer" to null
            GameAlertTarget.GameTimer -> "game_timer" to null
        }
        gameOne(gameValue(tx.execute(gameRequest("""INSERT INTO life_alert (game_source,game_id,occurrence_id,target_kind,target_seat_id,
            fired_seconds,fired_nano,acknowledged_seconds,acknowledged_nano,delivery_mask)
            VALUES (:source,:id,:occurrence,:kind,:seat,:seconds,:nano,:ack_seconds,:ack_nano,0)""", *lifeIdentityParameters(id),
            "occurrence" to gameText(alert.id.value), "kind" to gameText(kind), "seat" to gameNullableText(seat),
            "seconds" to gameNumber(alert.firedAt.epochSeconds), "nano" to gameNumber(alert.firedAt.nanoAdjustment.toLong()),
            "ack_seconds" to (alert.acknowledgedAt?.let { gameNumber(it.epochSeconds) } ?: SqlValue.Null),
            "ack_nano" to (alert.acknowledgedAt?.let { gameNumber(it.nanoAdjustment.toLong()) } ?: SqlValue.Null)), cancel)))
    }
    for (alert in change.acknowledged) {
        val ack = alert.acknowledgedAt ?: rejectGame(FailureCode.INVALID_INPUT, "Acknowledgment cannot be removed")
        gameOne(gameValue(tx.execute(gameRequest("UPDATE life_alert SET acknowledged_seconds=:seconds,acknowledged_nano=:nano WHERE game_source=:source AND game_id=:id AND occurrence_id=:occurrence",
            *lifeIdentityParameters(id), "occurrence" to gameText(alert.id.value), "seconds" to gameNumber(ack.epochSeconds), "nano" to gameNumber(ack.nanoAdjustment.toLong())), cancel)))
    }
}
internal suspend fun writeLifeSeats(tx: DbTransaction, before: GameSnapshot, after: GameSnapshot, cancel: CancelToken) {
    val previous = before.seats.associate { it.id.value to GameSeatProjection.of(it, FailureCode.CORRUPT_DATA) }
    for (seat in after.seats) {
        val next = GameSeatProjection.of(seat, FailureCode.INVALID_INPUT)
        if (previous[next.id] == next) continue
        gameOne(gameValue(tx.execute(gameRequest("""UPDATE life_seat_search SET player_name=:name,player_name_folded=:folded,
            deck_source=:deck_source,deck_id=:deck_id,deck_name=:deck_name,deck_name_folded=:deck_folded WHERE game_source=:source AND game_id=:id AND seat_id=:seat""",
            *lifeIdentityParameters(after.id), "seat" to gameText(next.id), "name" to gameText(next.name), "folded" to gameText(next.folded),
            "deck_source" to gameNullableText(next.deckSource), "deck_id" to gameNullableText(next.deckId), "deck_name" to gameNullableText(next.deckName), "deck_folded" to gameNullableText(next.deckFolded)), cancel)))
    }
}
