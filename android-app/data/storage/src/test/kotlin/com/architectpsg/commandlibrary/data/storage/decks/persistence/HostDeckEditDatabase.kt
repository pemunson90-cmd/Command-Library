package com.architectpsg.commandlibrary.data.storage.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.HostLifeDatabase
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CancellationException

/** Actual host SQLite composition using the accepted KSP schema11 fixture; no Room/device claim. */
internal class HostDeckEditDatabase private constructor(val host: HostLifeDatabase) : Database by host, Closeable {
    constructor(file: File, initialize: Boolean = true) : this(HostLifeDatabase(file, initialize = false)) {
        if (initialize) javaClass.getResourceAsStream("/deck-edits-schema11.sql")!!.bufferedReader().useLines { lines ->
            lines.filter { it.startsWith("CREATE TABLE IF NOT EXISTS `deck_") || it.startsWith("CREATE UNIQUE INDEX IF NOT EXISTS `index_deck_") || it.startsWith("CREATE INDEX IF NOT EXISTS `index_deck_") }
                .forEach { host.sql(it) }
        }
    }
    var loseAcknowledgement = false
    override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
        val result = host.transaction(action, cancel)
        if (loseAcknowledgement && result is Outcome.Success) throw CancellationException("Acknowledgement interrupted after commit")
        return result
    }
    fun sql(sql: String) = host.sql(sql)
    fun count(table: String) = host.scalar("SELECT count(*) FROM $table")
    fun state(): Map<String, List<List<String>>> = TABLES.associateWith { table ->
        host.connection.prepare("SELECT * FROM $table ORDER BY 1,2,3").use { s -> buildList {
            while (s.step()) add((0 until s.getColumnCount()).map { i -> when (s.getColumnType(i)) {
                1 -> "integer:${s.getLong(i)}"
                3 -> "text:${s.getText(i)}"
                4 -> "blob:${s.getBlob(i).joinToString("") { "%02x".format(it.toInt() and 255) }}"
                5 -> "null"
                else -> "real:${s.getDouble(i)}"
            } })
        } }
    }
    override fun close() = host.close()
    companion object { val TABLES = listOf("deck_head", "deck_revision", "deck_operation", "deck_undo_link", "deck_undo_state") }
}
