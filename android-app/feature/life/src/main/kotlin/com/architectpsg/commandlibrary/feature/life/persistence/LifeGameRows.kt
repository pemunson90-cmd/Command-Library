package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*

internal data class GameMetadata(
    val id: GameId, val revision: Long, val operation: String, val hash: String, val sequence: Long,
    val status: GameStatus, val started: UtcTimestamp, val ended: UtcTimestamp?, val updated: UtcTimestamp,
    val creationLength: Int, val checkpointLength: Int,
) {
    companion object {
        val columns = (listOf("game_source", "game_id", "creation_operation_id", "status").map { boundedGameText(it) } +
            boundedGameText("creation_sha256", 64) +
            listOf("revision", "checkpoint_sequence", "started_seconds", "started_nano", "ended_seconds", "ended_nano", "updated_seconds", "updated_nano").map(::typedGameInteger) +
            "CASE WHEN typeof(creation_payload)='blob' THEN length(creation_payload) ELSE NULL END AS creation_length" +
            "CASE WHEN typeof(checkpoint_payload)='blob' THEN length(checkpoint_payload) ELSE NULL END AS checkpoint_length").joinToString(",")
        fun read(row: RowData): GameMetadata {
            val source = row.gameRequiredText("game_source").also { gameIdentity(it, FailureCode.CORRUPT_DATA) }
            val raw = row.gameRequiredText("game_id").also { gameIdentity(it, FailureCode.CORRUPT_DATA) }
            val operation = row.gameRequiredText("creation_operation_id").also { gameIdentity(it, FailureCode.CORRUPT_DATA) }
            val revision = row.gameRequiredLong("revision")
            val sequence = row.gameRequiredLong("checkpoint_sequence")
            corruptGame(revision >= 0 && sequence >= 0, "Negative game revision/sequence")
            val hash = row.gameRequiredText("creation_sha256")
            corruptGame(hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' }, "Invalid game creation hash")
            val status = GameStatus.entries.singleOrNull { it.name == row.gameRequiredText("status") }
                ?: rejectGame(FailureCode.CORRUPT_DATA, "Invalid game status")
            val start = gameTimestamp(row.gameRequiredLong("started_seconds"), row.gameRequiredLong("started_nano"))
            val endSeconds = row.gameOptionalLong("ended_seconds"); val endNano = row.gameOptionalLong("ended_nano")
            corruptGame((endSeconds == null) == (endNano == null), "Incomplete game end timestamp")
            val end = endSeconds?.let { gameTimestamp(it, checkNotNull(endNano)) }
            val updated = gameTimestamp(row.gameRequiredLong("updated_seconds"), row.gameRequiredLong("updated_nano"))
            val creationLength = row.gameRequiredLong("creation_length")
            val checkpointLength = row.gameRequiredLong("checkpoint_length")
            corruptGame(creationLength in 1..CREATION_BYTES.toLong() && checkpointLength in 1..CHECKPOINT_BYTES.toLong(), "Game payload exceeds byte budget")
            return GameMetadata(GameId(ProviderId(source), raw), revision, operation, hash, sequence, status, start, end, updated,
                creationLength.toInt(), checkpointLength.toInt())
        }
    }
}

internal data class GameSeatProjection(
    val id: String, val name: String, val folded: String,
    val deckSource: String?, val deckId: String?, val deckName: String?, val deckFolded: String?,
) {
    companion object {
        val columns = (listOf("seat_id", "player_name", "deck_source", "deck_id", "deck_name").map { boundedGameText(it) } +
            listOf("player_name_folded", "deck_name_folded").map { boundedGameText(it, 4 * GAME_STRING_BYTES) }).joinToString(",")
        fun of(seat: Seat, code: FailureCode): GameSeatProjection {
            gameIdentity(seat.id.value, code); gameString(seat.playerName, GAME_STRING_BYTES, code)
            val folded = gameFold(seat.playerName).also { gameString(it, 4 * GAME_STRING_BYTES, code) }
            val deck = (seat.deck as? Knowledge.Known)?.value
            deck?.let {
                gameIdentity(it.id.source.value, code); gameIdentity(it.id.raw, code); gameString(it.name, GAME_STRING_BYTES, code)
            }
            val deckFolded = deck?.name?.let(::gameFold)?.also { gameString(it, 4 * GAME_STRING_BYTES, code) }
            return GameSeatProjection(seat.id.value, seat.playerName, folded, deck?.id?.source?.value, deck?.id?.raw, deck?.name, deckFolded)
        }
        fun read(row: RowData): GameSeatProjection {
            val id = row.gameRequiredText("seat_id").also { gameIdentity(it, FailureCode.CORRUPT_DATA) }
            val name = row.gameRequiredText("player_name"); val folded = row.gameRequiredText("player_name_folded")
            corruptGame(folded == gameFold(name), "Game player-name fold mismatch")
            val deckSource = row.gameOptionalText("deck_source"); val deckId = row.gameOptionalText("deck_id")
            val deckName = row.gameOptionalText("deck_name"); val deckFolded = row.gameOptionalText("deck_name_folded")
            val deck = listOf(deckSource, deckId, deckName, deckFolded)
            corruptGame(deck.all { it == null } || deck.all { it != null }, "Incomplete game deck projection")
            if (deckSource != null) {
                gameIdentity(deckSource, FailureCode.CORRUPT_DATA); gameIdentity(checkNotNull(deckId), FailureCode.CORRUPT_DATA)
                corruptGame(deckFolded == gameFold(checkNotNull(deckName)), "Game deck-name fold mismatch")
            }
            return GameSeatProjection(id, name, folded, deckSource, deckId, deckName, deckFolded)
        }
    }
}
