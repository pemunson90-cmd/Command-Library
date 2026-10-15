package com.architectpsg.commandlibrary.data.storage.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.HostLifeDatabase
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CancellationException

/** Composition over the shared actual host SQLite adapter; distinct from Room/native evidence. */
internal class HostDeckDatabase private constructor(val host: HostLifeDatabase) : Database by host, Closeable {
    constructor(file: File, initialize: Boolean = true) : this(HostLifeDatabase(file, initialize = false)) {
        if (initialize) javaClass.getResourceAsStream("/deck-library-schema10.sql")!!.bufferedReader().useLines { lines ->
            lines.filter { it.startsWith("CREATE TABLE IF NOT EXISTS `deck_") || it.startsWith("CREATE UNIQUE INDEX IF NOT EXISTS `index_deck_") || it.startsWith("CREATE INDEX IF NOT EXISTS `index_deck_") }
                .forEach { host.sql(it) }
        }
    }
    var loseAcknowledgement: Boolean = false
    override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
        val result = host.transaction(action, cancel)
        if (loseAcknowledgement && result is Outcome.Success) throw CancellationException("Acknowledgement interrupted after commit")
        return result
    }
    fun sql(sql: String) = host.sql(sql)
    fun count(table: String): Long = host.scalar("SELECT count(*) FROM $table")
    override fun close() = host.close()
}
