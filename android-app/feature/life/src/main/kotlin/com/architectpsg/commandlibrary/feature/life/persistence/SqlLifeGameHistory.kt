package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*

/** Offline checked history queries; no service/DI/UI or cross-page snapshot guarantee. */
public class SqlLifeGameHistory(private val db: Database, private val codec: LifePayloadCodecPort) {
    private val games = SqlLifeGameStore(db, codec)
    public suspend fun history(filter: GameHistoryFilter, page: PageRequest, cancel: CancelToken): Outcome<GameHistoryPage> = gameTransaction(db, cancel) { tx ->
        val prepared = HistoryFilter(filter)
        val cursor = LifeHistoryCursor(prepared.binding())
        val anchor = page.cursor?.let { resolveAnchor(tx, cursor.decode(it), cursor, cancel) }
        val predicates = prepared.predicates().toMutableList()
        val parameters = prepared.parameters().toMutableList()
        val filterWhere = predicates.joinToString(" AND ").ifEmpty { "1=1" }
        val counts = gameValue(tx.query(gameRequest("SELECT COUNT(*) AS total FROM life_game g WHERE $filterWhere", *parameters.toTypedArray(), maxRows = 1), cancel))
        corruptGame(counts.size == 1, "Missing history count")
        val total = counts.single().gameRequiredLong("total")
        corruptGame(total >= 0, "Negative history count")
        if (anchor != null) {
            predicates += "(g.started_seconds<:seek_seconds OR (g.started_seconds=:seek_seconds AND (g.started_nano<:seek_nano OR (g.started_nano=:seek_nano AND (g.game_source COLLATE BINARY<:seek_source COLLATE BINARY OR (g.game_source COLLATE BINARY=:seek_source COLLATE BINARY AND g.game_id COLLATE BINARY<:seek_id COLLATE BINARY))))))"
            parameters += listOf("seek_seconds" to gameNumber(anchor.started.epochSeconds), "seek_nano" to gameNumber(anchor.started.nanoAdjustment.toLong()),
                "seek_source" to gameText(anchor.id.source.value), "seek_id" to gameText(anchor.id.raw))
        }
        parameters += "limit" to gameNumber(page.limit.toLong() + 1)
        val where = predicates.joinToString(" AND ").ifEmpty { "1=1" }
        val rows = gameValue(tx.query(gameRequest("SELECT ${GameMetadata.columns} FROM life_game g WHERE $where ORDER BY started_seconds DESC,started_nano DESC,game_source COLLATE BINARY DESC,game_id COLLATE BINARY DESC LIMIT :limit",
            *parameters.toTypedArray(), maxRows = page.limit + 1), cancel)).map { GameMetadata.read(it) }
        var previous = anchor
        for (meta in rows) {
            val next = LifeHistoryKey(meta.started, meta.id)
            val prior = previous
            corruptGame(prior == null || compareKey(next, prior) < 0, "History page order did not advance")
            previous = next
        }
        val entries = ArrayList<GameHistoryEntry>()
        var bytes = 0L
        var last: LifeHistoryKey? = null
        for (meta in rows) {
            cancel.throwIfCancelled()
            val size = meta.creationLength.toLong() + meta.checkpointLength
            if (entries.size == page.limit || bytes + size > EVENT_PAGE_BYTES) break
            val game = games.readWithin(tx, meta.id, cancel).checkpoint.snapshot
            corruptGame(prepared.matches(game), "Selected history snapshot contradicts query projections")
            entries += GameHistoryEntry(game.id, game.revision, game.startedAt, game.endedAt, game.seats, game.result)
            bytes += size; last = LifeHistoryKey(meta.started, meta.id)
        }
        corruptGame(rows.isEmpty() || entries.isNotEmpty(), "History byte page cannot advance")
        corruptGame(total >= entries.size, "History count/page mismatch")
        val next = if (entries.size < rows.size) cursor.encode(checkNotNull(last)) else null
        GameHistoryPage(Page(SnapshotList.copyOf(entries), next, SnapshotReference(ProviderId("local-life-history"), null, null, null)), total)
    }

    /** Resolve identity without filter predicates: live name/deck edits must not erase a cursor boundary. */
    private suspend fun resolveAnchor(tx: DbTransaction, anchor: LifeHistoryAnchor, cursor: LifeHistoryCursor, cancel: CancelToken): LifeHistoryKey {
        var after: GameId? = null
        while (true) {
            cancel.throwIfCancelled()
            val parameters = mutableListOf("seconds" to gameNumber(anchor.started.epochSeconds), "nano" to gameNumber(anchor.started.nanoAdjustment.toLong()))
            val previous = after
            val seek = if (previous == null) "" else {
                parameters += listOf("source" to gameText(previous.source.value), "id" to gameText(previous.raw))
                " AND (game_source COLLATE BINARY<:source COLLATE BINARY OR (game_source COLLATE BINARY=:source COLLATE BINARY AND game_id COLLATE BINARY<:id COLLATE BINARY))"
            }
            val rows = gameValue(tx.query(gameRequest("SELECT ${boundedGameText("game_source")},${boundedGameText("game_id")} FROM life_game WHERE started_seconds=:seconds AND started_nano=:nano$seek ORDER BY game_source COLLATE BINARY DESC,game_id COLLATE BINARY DESC LIMIT 100",
                *parameters.toTypedArray(), maxRows = 100), cancel)).map { row ->
                val source = row.gameRequiredText("game_source").also { gameIdentity(it, FailureCode.CORRUPT_DATA) }
                val raw = row.gameRequiredText("game_id").also { gameIdentity(it, FailureCode.CORRUPT_DATA) }
                GameId(ProviderId(source), raw)
            }
            if (rows.isEmpty()) rejectGame(FailureCode.STALE_REVISION, "History cursor anchor was deleted; refresh history")
            var prior = previous
            for (id in rows) { corruptGame(prior == null || compareHistoryIds(id, prior) < 0, "History anchor lookup did not advance"); prior = id }
            rows.firstOrNull { cursor.identity(it) == anchor.identityHash }?.let { return LifeHistoryKey(anchor.started, it) }
            after = rows.last()
        }
    }
    private fun compareKey(left: LifeHistoryKey, right: LifeHistoryKey): Int =
        compareHistoryTimes(left.started, right.started).takeIf { it != 0 } ?: compareHistoryIds(left.id, right.id)
}

private class HistoryFilter(private val input: GameHistoryFilter) {
    private val player = folded(input.playerName)
    private val deck = folded(input.deckName)
    init { input.deckId?.let { gameIdentity(it.source.value, FailureCode.INVALID_INPUT); gameIdentity(it.raw, FailureCode.INVALID_INPUT) } }
    private fun folded(value: String?): String? = value?.let {
        gameString(it, GAME_STRING_BYTES, FailureCode.INVALID_INPUT)
        gameFold(it).also { folded -> gameString(folded, 4 * GAME_STRING_BYTES, FailureCode.INVALID_INPUT) }
    }
    fun binding(): List<String?> = listOf(player, input.deckId?.source?.value, input.deckId?.raw, deck,
        input.fromInclusive?.epochSeconds?.toString(), input.fromInclusive?.nanoAdjustment?.toString(), input.untilExclusive?.epochSeconds?.toString(), input.untilExclusive?.nanoAdjustment?.toString())
    fun predicates(): List<String> = buildList {
        if (player != null) add("EXISTS (SELECT 1 FROM life_seat_search s WHERE s.game_source=g.game_source AND s.game_id=g.game_id AND instr(s.player_name_folded,:player)>0)")
        if (input.deckId != null || deck != null) {
            val parts = mutableListOf("s.game_source=g.game_source", "s.game_id=g.game_id")
            if (input.deckId != null) parts += listOf("s.deck_source COLLATE BINARY=:deck_source COLLATE BINARY", "s.deck_id COLLATE BINARY=:deck_id COLLATE BINARY")
            if (deck != null) parts += "instr(s.deck_name_folded,:deck)>0"
            add("EXISTS (SELECT 1 FROM life_seat_search s WHERE ${parts.joinToString(" AND ")})")
        }
        if (input.fromInclusive != null) add("(g.started_seconds>:from_seconds OR (g.started_seconds=:from_seconds AND g.started_nano>=:from_nano))")
        if (input.untilExclusive != null) add("(g.started_seconds<:until_seconds OR (g.started_seconds=:until_seconds AND g.started_nano<:until_nano))")
    }
    fun parameters(): List<Pair<String, SqlValue>> = buildList {
        player?.let { add("player" to gameText(it)) }; deck?.let { add("deck" to gameText(it)) }
        input.deckId?.let { add("deck_source" to gameText(it.source.value)); add("deck_id" to gameText(it.raw)) }
        input.fromInclusive?.let { add("from_seconds" to gameNumber(it.epochSeconds)); add("from_nano" to gameNumber(it.nanoAdjustment.toLong())) }
        input.untilExclusive?.let { add("until_seconds" to gameNumber(it.epochSeconds)); add("until_nano" to gameNumber(it.nanoAdjustment.toLong())) }
    }
    fun matches(game: GameSnapshot): Boolean =
        (player == null || game.seats.any { gameFold(it.playerName).contains(player) }) &&
        ((input.deckId == null && deck == null) || game.seats.any { seat ->
            val linked = (seat.deck as? Knowledge.Known)?.value
            linked != null && (input.deckId == null || linked.id == input.deckId) && (deck == null || gameFold(linked.name).contains(deck))
        }) && (input.fromInclusive?.let { compareHistoryTimes(game.startedAt, it) >= 0 } ?: true) &&
        (input.untilExclusive?.let { compareHistoryTimes(game.startedAt, it) < 0 } ?: true)
}
