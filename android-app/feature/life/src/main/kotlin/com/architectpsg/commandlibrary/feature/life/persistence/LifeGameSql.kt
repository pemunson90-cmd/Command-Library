package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.util.Locale

internal const val GAME_STRING_BYTES = 65_536
internal const val CREATION_BYTES = 4 * 1024 * 1024
internal const val CHECKPOINT_BYTES = CREATION_BYTES + 65_536
internal class GameStoreProblem(val status: Outcome<Nothing>) : RuntimeException()
internal fun rejectGame(code: FailureCode, message: String): Nothing =
    throw GameStoreProblem(Outcome.Failure(DomainFailure(code, message, SnapshotList.empty())))
internal fun requireGame(valid: Boolean, code: FailureCode, message: String) { if (!valid) rejectGame(code, message) }
internal fun corruptGame(valid: Boolean, message: String) = requireGame(valid, FailureCode.CORRUPT_DATA, message)
internal fun <T> gameValue(outcome: Outcome<T>): T = when (outcome) {
    is Outcome.Success -> outcome.value
    is Outcome.Failure -> throw GameStoreProblem(outcome)
    is Outcome.Unavailable -> throw GameStoreProblem(outcome)
    is Outcome.Unknown -> throw GameStoreProblem(outcome)
}
internal suspend fun <T> gameTransaction(db: Database, cancel: CancelToken, action: suspend (DbTransaction) -> T): Outcome<T> {
    cancel.throwIfCancelled()
    return when (val result = db.transaction({ tx ->
        try { Outcome.Success(action(tx)) } catch (problem: GameStoreProblem) { problem.status }
    }, cancel)) {
        is Outcome.Success -> result.value
        is Outcome.Failure -> result
        is Outcome.Unavailable -> result
        is Outcome.Unknown -> result
    }
}
internal fun gameRequest(sql: String, vararg values: Pair<String, SqlValue>, maxRows: Int = 1): SqlRequest =
    SqlRequest(sql, SnapshotList.copyOf(values.map { SqlParameter(it.first, it.second) }), maxRows)
internal fun gameText(value: String): SqlValue = SqlValue.Text(value)
internal fun gameNumber(value: Long): SqlValue = SqlValue.Integer(value)
internal fun gameNullableText(value: String?): SqlValue = value?.let(::gameText) ?: SqlValue.Null
internal fun gameOne(result: DbWriteResult) = requireGame(result.affectedRows == 1L, FailureCode.PERSISTENCE, "Game write did not affect exactly one row")
internal fun gameFold(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC).lowercase(Locale.ROOT)
internal fun gameIdentity(value: String, code: FailureCode) {
    gameString(value, GAME_STRING_BYTES, code)
    requireGame(value.isNotBlank(), code, "Blank game identity")
}
internal fun gameString(value: String, limit: Int, code: FailureCode) {
    requireGame(value.length <= limit, code, "Game text exceeds byte budget")
    val size = try {
        Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(java.nio.CharBuffer.wrap(value)).remaining()
    } catch (error: CharacterCodingException) { rejectGame(code, "Invalid game UTF8") }
    requireGame(size <= limit, code, "Game text exceeds byte budget")
}
internal fun RowData.gameColumn(name: String): SqlValue = columns.singleOrNull { it.name == name }?.value
    ?: rejectGame(FailureCode.CORRUPT_DATA, "Missing game column")
internal fun RowData.gameOptionalText(name: String): String? = when (val value = gameColumn(name)) {
    SqlValue.Null -> null
    is SqlValue.Blob -> try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(value.value.copyBytes())).toString()
    } catch (error: CharacterCodingException) { rejectGame(FailureCode.CORRUPT_DATA, "Invalid stored game UTF8") }
    else -> rejectGame(FailureCode.CORRUPT_DATA, "Invalid stored game text type")
}
internal fun RowData.gameRequiredText(name: String): String = gameOptionalText(name)
    ?: rejectGame(FailureCode.CORRUPT_DATA, "Missing game text")
internal fun RowData.gameOptionalLong(name: String): Long? = when (val value = gameColumn(name)) {
    SqlValue.Null -> null
    is SqlValue.Integer -> value.value
    else -> rejectGame(FailureCode.CORRUPT_DATA, "Invalid game integer")
}
internal fun RowData.gameRequiredLong(name: String): Long = gameOptionalLong(name)
    ?: rejectGame(FailureCode.CORRUPT_DATA, "Missing game integer")
internal fun gameTimestamp(seconds: Long, nano: Long): UtcTimestamp {
    corruptGame(nano in 0..999_999_999, "Invalid game timestamp")
    return UtcTimestamp(seconds, nano.toInt())
}
/** Invalid optional values must remain distinguishable from actual NULL; FF is invalid UTF8/type. */
internal fun boundedGameText(column: String, limit: Int = GAME_STRING_BYTES): String =
    "CASE WHEN typeof($column)='null' THEN NULL WHEN typeof($column)='text' AND length(CAST($column AS BLOB))<=$limit THEN CAST($column AS BLOB) ELSE X'FF' END AS $column"
internal fun typedGameInteger(column: String): String =
    "CASE WHEN typeof($column)='null' THEN NULL WHEN typeof($column)='integer' THEN $column ELSE X'FF' END AS $column"
