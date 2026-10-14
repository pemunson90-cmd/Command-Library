package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.life.domain.LifeRandomEngine
import com.architectpsg.commandlibrary.feature.life.domain.RandomRequestContext

/** Checked original request and latest committed predecessor, including requests later undone. */
public data class LifeRandomHistory(public val original: RandomRequest, public val last: RecordedRandomResult)

/** Unindexed checked scan: bounded metadata/byte pages and one decoded archive at a time. */
internal suspend fun readRandomPredecessor(tx: DbTransaction, reader: SqlLifeEventReader, game: GameSnapshot,
    requestId: EntityId, cancel: CancelToken): LifeRandomHistory? {
    gameIdentity(requestId.value, FailureCode.INVALID_INPUT)
    var cursor: LifeEventKey? = null
    var history: LifeRandomHistory? = null
    while (true) {
        cancel.throwIfCancelled()
        val after = cursor
        val parameters = lifeIdentityParameters(game.id).toMutableList()
        if (after != null) {
            parameters += "revision" to gameNumber(after.revision)
            parameters += "event" to gameNumber(after.eventId)
        }
        val predicate = if (after == null) "" else " AND (revision>:revision OR (revision=:revision AND event_id>:event))"
        val rows = gameValue(tx.query(gameRequest("SELECT ${LifeEventMetadata.columns} FROM life_event WHERE game_source=:source AND game_id=:id$predicate ORDER BY revision,event_id LIMIT 100",
            *parameters.toTypedArray(), maxRows = 100), cancel)).map { LifeEventMetadata.read(it, game.revision.value) }
        if (rows.isEmpty()) return history
        var bytes = 0L
        var processed = false
        for (meta in rows) {
            cancel.throwIfCancelled()
            if (bytes + meta.length > EVENT_PAGE_BYTES) break
            val prior = cursor
            corruptGame(prior == null || meta.key.revision > prior.revision || (meta.key.revision == prior.revision && meta.key.eventId > prior.eventId), "Random history page order did not advance")
            val archive = reader.archiveWithin(tx, game.id, meta.key.eventId, game.revision.value, cancel)
            history = consumeRandomArchive(archive, requestId, history)
            bytes += meta.length
            cursor = meta.key // Advance only the processed prefix, never the last queried row.
            processed = true
        }
        corruptGame(processed, "Random history byte page cannot advance")
    }
}

private fun consumeRandomArchive(archive: GameEventArchive, requestId: EntityId, history: LifeRandomHistory?): LifeRandomHistory? {
    val event = archive.event
    val pending = ArrayDeque<GameAction>()
    pending.addLast(event.action)
    var actionMatches = false
    while (pending.isNotEmpty()) {
        when (val action = pending.removeLast()) {
            is GameAction.RequestRandom -> if (action.request.randomIdentity() == requestId) actionMatches = true
            is GameAction.Group -> action.actions.forEach(pending::addLast)
            else -> Unit
        }
    }
    val relevant = actionMatches || event.randomResults.any { it.requestId == requestId }
    if (!relevant) return history
    when (event.undoOfEventId) {
        is Knowledge.Known -> return history // Original request/record remains earlier in this immutable scan.
        is Knowledge.Unknown -> throw GameStoreProblem(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Relevant random event needs explicit undo-intent repair"))
        is Knowledge.Absent -> Unit
    }
    val action = event.action as? GameAction.RequestRandom ?: rejectGame(FailureCode.CORRUPT_DATA, "Random original is not a single request action")
    corruptGame(action.request.randomIdentity() == requestId && event.randomResults.size == 1 && event.randomResults.single().requestId == requestId,
        "Random action/record identity or cardinality mismatch")
    val before = archive.provenance.before
    val definitions = before.settings.counterDefinitions
    if (definitions.none { it.role == CounterRole.LIFE } && definitions.all { it.role == CounterRole.OTHER }) {
        throw GameStoreProblem(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Historical random replay requires explicit counter-role repair"))
    }
    val recorded = event.randomResults.single()
    val predecessor = history?.let { RandomRequestContext(it.original, it.last) }
    val replay = when (val checked = LifeRandomEngine.replay(before, action, recorded, predecessor)) {
        is Outcome.Success -> checked.value
        is Outcome.Failure -> rejectGame(FailureCode.CORRUPT_DATA, "Contradictory random history: ${checked.error.message}")
        is Outcome.Unavailable -> throw GameStoreProblem(checked)
        is Outcome.Unknown -> throw GameStoreProblem(checked)
    }
    corruptGame(replay.snapshot.copy(revision = event.revision, lastEventId = Knowledge.Known(event.id)) == archive.provenance.afterAction,
        "Recorded random action does not match immutable after-state")
    return LifeRandomHistory(history?.original ?: action.request, recorded)
}

private fun RandomRequest.randomIdentity(): EntityId = when (this) {
    is RandomRequest.Dice -> requestId
    is RandomRequest.ContinueDice -> requestId
    is RandomRequest.Coin -> requestId
    is RandomRequest.SeatPick -> requestId
    is RandomRequest.SeatOrder -> requestId
    is RandomRequest.VariantZoneShuffle -> requestId
}
