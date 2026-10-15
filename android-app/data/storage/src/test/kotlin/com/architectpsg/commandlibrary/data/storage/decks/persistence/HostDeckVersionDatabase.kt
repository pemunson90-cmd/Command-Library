package com.architectpsg.commandlibrary.data.storage.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.HostLifeDatabase
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CancellationException

/** Actual bundled SQLite/schema14; observation wraps real transactions, never substitutes rows. */
internal class HostDeckVersionDatabase private constructor(val host: HostLifeDatabase) : Database by host, Closeable {
    constructor(file: File, initialize: Boolean = true) : this(HostLifeDatabase(file, initialize = false)) {
        if (initialize) javaClass.getResourceAsStream("/deck-versions-schema14.sql")!!.bufferedReader().useLines { lines ->
            lines.filter { it.startsWith("CREATE TABLE IF NOT EXISTS `deck_") || it.startsWith("CREATE UNIQUE INDEX IF NOT EXISTS `index_deck_") || it.startsWith("CREATE INDEX IF NOT EXISTS `index_deck_") }
                .forEach { host.sql(it) }
        }
    }
    val queries = mutableListOf<SqlRequest>()
    var observe: ((SqlRequest) -> Unit)? = null
    var loseAcknowledgement = false
    var transactions = 0
    override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
        transactions++
        val result = host.transaction({ original ->
            action(object : DbTransaction by original {
                override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> {
                    queries += request; observe?.invoke(request)
                    return original.query(request, cancel)
                }
            })
        }, cancel)
        if (loseAcknowledgement && result is Outcome.Success) throw CancellationException("Acknowledgement interrupted after commit")
        return result
    }
    fun sql(sql: String) = host.sql(sql)
    fun count(table: String) = host.scalar("SELECT count(*) FROM $table")
    fun payloadReads(revision: Long): Int = queries.count { request -> request.statement.startsWith("SELECT payload FROM deck_revision") &&
        request.parameters.any { it.name == "revision" && it.value == SqlValue.Integer(revision) } }
    fun state(): Map<String, List<List<String>>> = TABLES.associateWith { table ->
        host.connection.prepare("SELECT * FROM $table ORDER BY 1,2").use { statement -> buildList {
            while (statement.step()) add((0 until statement.getColumnCount()).map { i -> when (statement.getColumnType(i)) {
                1 -> "integer:${statement.getLong(i)}"
                3 -> "text:${statement.getText(i)}"
                4 -> "blob:${hex(statement.getBlob(i))}"
                5 -> "null"
                else -> "real:${statement.getDouble(i)}"
            } })
        } }
    }
    fun plans(): List<String> = queries.distinctBy { it.statement }.filter { request ->
        request.statement.contains("o.kind=:kind ORDER BY o.sequence DESC LIMIT 1") ||
            request.statement.contains("n.creating_sequence<:sequence") || request.statement.contains("n.creating_sequence=:sequence")
    }.map { request ->
        host.connection.prepare("EXPLAIN QUERY PLAN ${request.statement}").use { statement ->
            com.architectpsg.commandlibrary.data.storage.NamedParameters.bindOrder(request.statement, request.parameters).forEachIndexed { i, value -> when (value) {
                is SqlValue.Text -> statement.bindText(i + 1, value.value)
                is SqlValue.Integer -> statement.bindLong(i + 1, value.value)
                else -> error("Unexpected plan parameter")
            } }
            val label = when {
                request.statement.contains("o.kind=:kind") -> "latest receipt"
                request.statement.contains("n.creating_sequence<:sequence") -> "immediate predecessor"
                else -> "exact receipt association"
            }
            "$label: " + buildList { while (statement.step()) add(statement.getText(3)) }.joinToString("\n")
        }
    }
    override fun close() = host.close()
    companion object {
        val TABLES = listOf("deck_head", "deck_revision", "deck_operation", "deck_undo_link", "deck_undo_state", "deck_named_version", "sqlite_sequence")
        private fun hex(bytes: ByteArray): String {
            val alphabet = "0123456789abcdef"
            return CharArray(bytes.size * 2).also { out -> bytes.forEachIndexed { i, byte ->
                val value = byte.toInt() and 255; out[i * 2] = alphabet[value ushr 4]; out[i * 2 + 1] = alphabet[value and 15]
            } }.concatToString()
        }
    }
}
