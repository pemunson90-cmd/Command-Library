package com.architectpsg.commandlibrary.data.storage.life

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.architectpsg.commandlibrary.core.common.RollbackOnlyLatch
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.NamedParameters
import com.architectpsg.commandlibrary.data.storage.schema.Migration5To6
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

/** Test-only Database contract adapter over actual host bundled SQLite; not Android Room evidence. */
internal class HostLifeDatabase(file: File, initialize: Boolean = true) : Database, Closeable {
    val connection: SQLiteConnection = BundledSQLiteDriver().open(file.path)
    var afterWrite: (() -> Unit)? = null
    var blobQueries = 0
    init {
        sql("PRAGMA foreign_keys=ON")
        if (initialize) runBlocking { Migration5To6.migrate(connection) }
    }
    fun sql(text: String) { connection.prepare(text).use { it.step() } }
    fun scalar(text: String): Long = connection.prepare(text).use { check(it.step()); it.getLong(0) }
    private fun bind(s: SQLiteStatement, r: SqlRequest) {
        NamedParameters.bindOrder(r.statement, r.parameters).forEachIndexed { i, v -> when(v) {
            SqlValue.Null -> s.bindNull(i+1)
            is SqlValue.Integer -> s.bindLong(i+1,v.value)
            is SqlValue.Text -> s.bindText(i+1,v.value)
            is SqlValue.DecimalText -> s.bindText(i+1,v.value)
            is SqlValue.Blob -> s.bindBlob(i+1,v.value.copyBytes())
        } }
    }
    private fun rows(r: SqlRequest, cancel: CancelToken): SnapshotList<RowData> {
        cancel.throwIfCancelled()
        if (r.statement.contains("SELECT payload")) blobQueries++
        return connection.prepare(r.statement).use { s ->
            bind(s,r)
            val rows = mutableListOf<RowData>()
            while(s.step()) {
                cancel.throwIfCancelled(); check(rows.size < r.maxRows)
                rows += RowData(SnapshotList.copyOf((0 until s.getColumnCount()).map { i ->
                    val v = when(s.getColumnType(i)) {
                        1 -> SqlValue.Integer(s.getLong(i))
                        3 -> SqlValue.Text(s.getText(i))
                        4 -> SqlValue.Blob(SnapshotBytes(s.getBlob(i)))
                        5 -> SqlValue.Null
                        else -> error("Unexpected SQL type")
                    }
                    RowColumn(s.getColumnName(i),v)
                }))
            }
            SnapshotList.copyOf(rows)
        }
    }
    private fun failure(e: Exception) = Outcome.Failure(DomainFailure(FailureCode.PERSISTENCE,e.message ?: "SQLite failure",SnapshotList.empty()))
    override suspend fun query(request: SqlRequest,cancel: CancelToken): Outcome<SnapshotList<RowData>> = try {
        Outcome.Success(rows(request,cancel))
    } catch(e: CancellationException) { throw e } catch(e: Exception) { failure(e) }
    override suspend fun <T> transaction(action: suspend(DbTransaction)->T,cancel: CancelToken): Outcome<T> {
        cancel.throwIfCancelled(); sql("BEGIN IMMEDIATE")
        val latch = RollbackOnlyLatch()
        val tx = object : DbTransaction {
            override val state get() = latch.state
            override suspend fun query(request: SqlRequest,cancel: CancelToken): Outcome<SnapshotList<RowData>> =
                latch.observe(this@HostLifeDatabase.query(request,cancel))
            override suspend fun execute(request: SqlRequest,cancel: CancelToken): Outcome<DbWriteResult> {
                cancel.throwIfCancelled()
                return latch.observe(try {
                    connection.prepare(request.statement).use { bind(it,request); it.step() }
                    val count = scalar("SELECT changes()")
                    afterWrite?.invoke()
                    Outcome.Success(DbWriteResult(count))
                } catch(e: CancellationException) { throw e } catch(e: Exception) { failure(e) })
            }
            override fun abort(error: DomainFailure): Nothing = latch.abort(error)
        }
        try {
            val value = action(tx)
            if(value is Outcome<*>) latch.observe(value)
            cancel.throwIfCancelled()
            return when(val allowed=latch.commitPermission()) {
                is Outcome.Success -> { sql("COMMIT"); Outcome.Success(value) }
                else -> { sql("ROLLBACK"); nonSuccess(allowed) }
            }
        } catch(e: CancellationException) { sql("ROLLBACK"); throw e
        } catch(e: TransactionAbort) { sql("ROLLBACK"); return nonSuccess(latch.commitPermission())
        } catch(e: Exception) { sql("ROLLBACK"); return failure(e) }
    }
    private fun <T> nonSuccess(o: Outcome<*>): Outcome<T> = when(o) {
        is Outcome.Failure -> o
        is Outcome.Unavailable -> o
        is Outcome.Unknown -> o
        is Outcome.Success -> error("Not a failure")
    }
    override suspend fun capabilities(cancel: CancelToken): Outcome<DbCapabilities> = error("Not needed by repository tests")
    override fun close() = connection.close()
}
