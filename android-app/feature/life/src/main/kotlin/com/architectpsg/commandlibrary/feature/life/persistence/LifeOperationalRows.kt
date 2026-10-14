package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*

internal data class LifeResultRow(val id: String, val revision: Long, val at: UtcTimestamp, val reason: String, val retracted: Boolean) {
    companion object {
        val columns = (listOf("result_id", "reason").map { boundedGameText(it) } + listOf("revision", "finished_seconds", "finished_nano", "retracted").map(::typedGameInteger)).joinToString(",")
        fun of(result: GameResult, code: FailureCode): LifeResultRow {
            gameIdentity(result.id.value, code); gameString(result.reason, GAME_STRING_BYTES, code)
            return LifeResultRow(result.id.value, result.revision.value, result.finishedAt, result.reason, result.retracted)
        }
        fun read(row: RowData): LifeResultRow {
            val id = row.gameRequiredText("result_id"); gameIdentity(id, FailureCode.CORRUPT_DATA)
            val revision = row.gameRequiredLong("revision"); val retracted = row.gameRequiredLong("retracted")
            corruptGame(revision >= 0 && retracted in 0..1, "Invalid result revision/retraction")
            return LifeResultRow(id, revision, gameTimestamp(row.gameRequiredLong("finished_seconds"), row.gameRequiredLong("finished_nano")), row.gameRequiredText("reason"), retracted == 1L)
        }
    }
}
internal data class LifeAlertRow(val alert: GameAlert, val delivery: Int) {
    companion object {
        val columns = (listOf("occurrence_id", "target_seat_id").map { boundedGameText(it) } + boundedGameText("target_kind", 64) +
            listOf("fired_seconds", "fired_nano", "acknowledged_seconds", "acknowledged_nano", "delivery_mask").map(::typedGameInteger)).joinToString(",")
        fun read(row: RowData, game: GameId): LifeAlertRow {
            val id = row.gameRequiredText("occurrence_id"); gameIdentity(id, FailureCode.CORRUPT_DATA)
            val seat = row.gameOptionalText("target_seat_id")?.also { gameIdentity(it, FailureCode.CORRUPT_DATA) }
            val target = when (row.gameRequiredText("target_kind")) {
                "seat_clock" -> GameAlertTarget.SeatClock(EntityId(seat ?: rejectGame(FailureCode.CORRUPT_DATA, "Seat alert has no seat")))
                "shared_countdown" -> GameAlertTarget.SharedCountdown
                "turn_timer" -> GameAlertTarget.TurnTimer
                "game_timer" -> GameAlertTarget.GameTimer
                else -> rejectGame(FailureCode.CORRUPT_DATA, "Unknown alert target kind")
            }
            corruptGame(target is GameAlertTarget.SeatClock || seat == null, "Non-seat alert contains seat projection")
            val seconds = row.gameOptionalLong("acknowledged_seconds"); val nano = row.gameOptionalLong("acknowledged_nano")
            corruptGame((seconds == null) == (nano == null), "Incomplete alert acknowledgment")
            val ack = if (seconds == null) null else gameTimestamp(seconds, nano!!)
            val mask = row.gameRequiredLong("delivery_mask"); corruptGame(mask in 0..7, "Invalid alert delivery mask")
            return LifeAlertRow(GameAlert(EntityId(id), game, target, gameTimestamp(row.gameRequiredLong("fired_seconds"), row.gameRequiredLong("fired_nano")), ack), mask.toInt())
        }
    }
}
internal fun lifeIdentityParameters(id: GameId): Array<Pair<String, SqlValue>> = arrayOf("source" to gameText(id.source.value), "id" to gameText(id.raw))
internal suspend fun findLifeAlerts(tx: DbTransaction, id: GameId, ids: List<EntityId>, cancel: CancelToken): Map<EntityId, LifeAlertRow> {
    val found = LinkedHashMap<EntityId, LifeAlertRow>()
    for (chunk in ids.chunked(100)) {
        val parameters = lifeIdentityParameters(id).toMutableList()
        val names = chunk.mapIndexed { index, value -> parameters += "alert$index" to gameText(value.value); ":alert$index" }
        val rows = gameValue(tx.query(gameRequest("SELECT ${LifeAlertRow.columns} FROM life_alert WHERE game_source=:source AND game_id=:id AND occurrence_id IN (${names.joinToString(",")})", *parameters.toTypedArray(), maxRows = chunk.size), cancel))
        for (row in rows) {
            val checked = LifeAlertRow.read(row, id)
            corruptGame(checked.alert.id in chunk && found.put(checked.alert.id, checked) == null, "Unexpected/duplicate alert projection")
        }
    }
    return found
}
internal data class LifeOperationalState(val result: LifeResultRow?, val alerts: Map<EntityId, LifeAlertRow>)
internal suspend fun loadLifeOperational(tx: DbTransaction, game: GameSnapshot, cancel: CancelToken): LifeOperationalState {
    val expected = when (val result = game.result) {
        is Knowledge.Known -> LifeResultRow.of(result.value, FailureCode.CORRUPT_DATA)
        is Knowledge.Absent -> null
        is Knowledge.Unknown -> throw GameStoreProblem(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Result knowledge requires explicit repair"))
    }
    val rows = gameValue(tx.query(gameRequest("SELECT ${LifeResultRow.columns} FROM life_result WHERE game_source=:source AND game_id=:id LIMIT 2", *lifeIdentityParameters(game.id), maxRows = 2), cancel))
    corruptGame(rows.size <= 1, "Duplicate result projection")
    val result = rows.singleOrNull()?.let(LifeResultRow::read)
    corruptGame(result == expected, "Result/checkpoint projection mismatch")
    val alerts = findLifeAlerts(tx, game.id, game.alerts.map { it.id }, cancel)
    corruptGame(alerts.size == game.alerts.size && game.alerts.all { alerts[it.id]?.alert == it }, "Alert/checkpoint projection mismatch")
    return LifeOperationalState(result, alerts)
}
