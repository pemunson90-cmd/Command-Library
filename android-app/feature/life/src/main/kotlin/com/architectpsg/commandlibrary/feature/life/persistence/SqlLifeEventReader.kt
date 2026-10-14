package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*

/** Checked immutable history reads, not semantic replay/undo authority or an operational GameService. */
public class SqlLifeEventReader(private val db: Database, private val codec: LifePayloadCodecPort) {
    public suspend fun archive(id: GameId, eventId: Long, cancel: CancelToken): Outcome<GameEventArchive> = gameTransaction(db, cancel) { tx ->
        identities(id)
        requireGame(eventId >= 0, FailureCode.INVALID_INPUT, "Negative event identity")
        archiveWithin(tx, id, eventId, parentRevision(tx, id, cancel), cancel)
    }

    internal suspend fun archiveWithin(tx: DbTransaction, id: GameId, eventId: Long, revision: Long, cancel: CancelToken): GameEventArchive {
        val meta = metadata(tx, id, eventId, revision, cancel)
            ?: throw GameStoreProblem(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Game event not found"))
        return load(tx, id, meta, revision, cancel)
    }

    public suspend fun events(id: GameId, page: PageRequest, cancel: CancelToken): Outcome<Page<GameEvent>> = gameTransaction(db, cancel) { tx ->
        identities(id)
        val cursor = LifeEventCursor(id)
        val after = page.cursor?.let(cursor::decode)
        val revision = parentRevision(tx, id, cancel)
        val parameters = identityParameters(id).toMutableList()
        parameters += "limit" to gameNumber(page.limit.toLong() + 1)
        if (after != null) {
            parameters += "revision" to gameNumber(after.revision)
            parameters += "event" to gameNumber(after.eventId)
        }
        val predicate = if (after == null) "" else " AND (revision>:revision OR (revision=:revision AND event_id>:event))"
        val rows = gameValue(tx.query(gameRequest("SELECT ${LifeEventMetadata.columns} FROM life_event WHERE game_source=:source AND game_id=:id$predicate ORDER BY revision,event_id LIMIT :limit",
            *parameters.toTypedArray(), maxRows = page.limit + 1), cancel)).map { LifeEventMetadata.read(it, revision) }
        var previous = after
        for (row in rows) {
            val prior = previous
            corruptGame(prior == null || row.key.revision > prior.revision || (row.key.revision == prior.revision && row.key.eventId > prior.eventId), "Event page order mismatch")
            previous = row.key
        }
        val selected = mutableListOf<LifeEventMetadata>()
        var bytes = 0L
        for (row in rows) {
            if (selected.size == page.limit || bytes + row.length > EVENT_PAGE_BYTES) break
            selected += row; bytes += row.length
        }
        // Every valid individual archive fits the page budget; a nonempty page always advances.
        corruptGame(rows.isEmpty() || selected.isNotEmpty(), "Event page cannot advance")
        val events = selected.map { load(tx, id, it, revision, cancel).event }
        val next = if (selected.size < rows.size) cursor.encode(selected.last().key) else null
        Page(SnapshotList.copyOf(events), next, SnapshotReference(ProviderId("local-life-events"), null, Revision(revision), null))
    }

    private fun identities(id: GameId) {
        gameIdentity(id.source.value, FailureCode.INVALID_INPUT); gameIdentity(id.raw, FailureCode.INVALID_INPUT)
    }
    private fun identityParameters(id: GameId): Array<Pair<String, SqlValue>> = arrayOf("source" to gameText(id.source.value), "id" to gameText(id.raw))
    private suspend fun parentRevision(tx: DbTransaction, id: GameId, cancel: CancelToken): Long {
        val rows = gameValue(tx.query(gameRequest("SELECT ${typedGameInteger("revision")} FROM life_game WHERE game_source=:source AND game_id=:id LIMIT 2", *identityParameters(id), maxRows = 2), cancel))
        if (rows.isEmpty()) throw GameStoreProblem(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Game not found"))
        corruptGame(rows.size == 1, "Duplicate game identity")
        return rows.single().gameRequiredLong("revision").also { corruptGame(it >= 0, "Invalid parent revision") }
    }
    private suspend fun metadata(tx: DbTransaction, id: GameId, eventId: Long, parentRevision: Long, cancel: CancelToken): LifeEventMetadata? {
        val rows = gameValue(tx.query(gameRequest("SELECT ${LifeEventMetadata.columns} FROM life_event WHERE game_source=:source AND game_id=:id AND event_id=:event LIMIT 2", *identityParameters(id), "event" to gameNumber(eventId), maxRows = 2), cancel))
        corruptGame(rows.size <= 1, "Duplicate event identity")
        return rows.singleOrNull()?.let { LifeEventMetadata.read(it, parentRevision) }?.also { corruptGame(it.key.eventId == eventId, "Event lookup identity mismatch") }
    }
    private suspend fun load(tx: DbTransaction, id: GameId, meta: LifeEventMetadata, parentRevision: Long, cancel: CancelToken): GameEventArchive {
        cancel.throwIfCancelled()
        val rows = gameValue(tx.query(gameRequest("SELECT archive_payload FROM life_event WHERE game_source=:source AND game_id=:id AND event_id=:event LIMIT 2", *identityParameters(id), "event" to gameNumber(meta.key.eventId), maxRows = 2), cancel))
        corruptGame(rows.size == 1, "Event archive missing")
        val bytes = (rows.single().gameColumn("archive_payload") as? SqlValue.Blob)?.value
            ?: rejectGame(FailureCode.CORRUPT_DATA, "Invalid event archive blob")
        corruptGame(bytes.size == meta.length, "Event archive length mismatch")
        val archive = gameValue(codec.decodeArchive(bytes))
        if (archive.version != 1) throw GameStoreProblem(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Unsupported event archive model version"))
        val event = archive.event
        corruptGame(event.gameId == id && event.id == meta.key.eventId && event.revision.value == meta.key.revision && event.groupId.value == meta.groupId && event.at == meta.at &&
            (event.undoOfEventId as? Knowledge.Known)?.value == meta.undo, "Event archive/projection mismatch")
        val group = event.action as? GameAction.Group
        corruptGame(group == null || group.groupId == event.groupId, "Atomic action/event group mismatch")
        if (meta.undo != null) {
            val target = metadata(tx, id, meta.undo, parentRevision, cancel)
            corruptGame(target != null && target.key.revision < meta.key.revision, "Undo target missing/not earlier")
        }
        cancel.throwIfCancelled()
        return archive
    }
}
