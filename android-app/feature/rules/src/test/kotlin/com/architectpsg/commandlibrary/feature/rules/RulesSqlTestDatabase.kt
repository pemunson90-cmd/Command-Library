package com.architectpsg.commandlibrary.feature.rules

import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonParser
import java.io.File
import kotlinx.coroutines.CancellationException

/** Actual bundled SQLite + actual exported schema2, with real FK/FTS/rollback semantics. */
internal class RulesSqlTestDatabase : Database, AutoCloseable {
    private val connection = BundledSQLiteDriver().open(":memory:")
    var maxTextBytesRead = 0
    var afterQuery: ((SqlRequest) -> Unit)? = null
    init {
        exec("PRAGMA foreign_keys=ON")
        var directory = File(System.getProperty("user.dir"))
        val relative = "android-app/data/storage/schemas/com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase/2.json"
        while (!File(directory, relative).isFile) directory = directory.parentFile ?: error("Actual rules schema missing")
        val schema = JsonParser.parseString(File(directory, relative).readText()).asJsonObject.getAsJsonObject("database")
        for (element in schema.getAsJsonArray("entities")) {
            val entity = element.asJsonObject
            val name = entity["tableName"].asString
            exec(entity["createSql"].asString.replace("\${TABLE_NAME}", name))
            entity.getAsJsonArray("indices")?.forEach { exec(it.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}", name)) }
        }
    }
    fun exec(sql: String) { connection.prepare(sql).use { it.step() } }
    private fun bind(statement: SQLiteStatement, request: SqlRequest) {
        val names = Regex(":([A-Za-z][A-Za-z0-9_]*)").findAll(request.statement).map { it.groupValues[1] }.distinct().toList()
        require(names.toSet() == request.parameters.map { it.name }.toSet())
        for ((index, name) in names.withIndex()) when (val value = request.parameters.single { it.name == name }.value) {
            SqlValue.Null -> statement.bindNull(index + 1)
            is SqlValue.Integer -> statement.bindLong(index + 1, value.value)
            is SqlValue.Text -> statement.bindText(index + 1, value.value)
            else -> error("Unexpected fixture parameter")
        }
    }
    override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> {
        cancel.throwIfCancelled()
        val result = connection.prepare(request.statement).use { statement ->
            bind(statement, request)
            SnapshotList.copyOf(buildList {
                while (statement.step()) {
                    require(size < request.maxRows)
                    add(RowData(SnapshotList.copyOf((0 until statement.getColumnCount()).map { index ->
                        RowColumn(statement.getColumnName(index), when (statement.getColumnType(index)) {
                            1 -> SqlValue.Integer(statement.getLong(index))
                            3 -> SqlValue.Text(statement.getText(index).also { maxTextBytesRead = maxOf(maxTextBytesRead, it.toByteArray(Charsets.UTF_8).size) })
                            5 -> SqlValue.Null
                            else -> error("Unexpected fixture column")
                        })
                    })))
                }
            })
        }
        afterQuery?.invoke(request)
        return Outcome.Success(result)
    }
    override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
        cancel.throwIfCancelled(); exec("BEGIN IMMEDIATE")
        val tx = object : DbTransaction {
            override var state: DbTransactionState = DbTransactionState.Active
            override fun abort(error: DomainFailure): Nothing { state = DbTransactionState.RollbackOnly(Outcome.Failure(error)); throw TransactionAbort(error) }
            override suspend fun query(request: SqlRequest, cancel: CancelToken) = this@RulesSqlTestDatabase.query(request, cancel)
            override suspend fun execute(request: SqlRequest, cancel: CancelToken): Outcome<DbWriteResult> {
                cancel.throwIfCancelled()
                connection.prepare(request.statement).use { bind(it, request); it.step() }
                val count = connection.prepare("SELECT changes()").use { it.step(); it.getLong(0) }
                return Outcome.Success(DbWriteResult(count))
            }
        }
        try {
            val value = action(tx)
            cancel.throwIfCancelled()
            (tx.state as? DbTransactionState.RollbackOnly)?.let { exec("ROLLBACK"); return it.status }
            when (value) {
                is Outcome.Failure -> { exec("ROLLBACK"); return value }
                is Outcome.Unavailable -> { exec("ROLLBACK"); return value }
                is Outcome.Unknown -> { exec("ROLLBACK"); return value }
            }
            exec("COMMIT")
            return Outcome.Success(value)
        } catch (error: CancellationException) { exec("ROLLBACK"); throw error }
        catch (error: TransactionAbort) { exec("ROLLBACK"); return Outcome.Failure(error.failure) }
        catch (error: Exception) { exec("ROLLBACK"); throw error }
    }
    override suspend fun capabilities(cancel: CancelToken): Outcome<DbCapabilities> = Outcome.Success(DbCapabilities("Actual bundled SQLite fixture", 2, true, true, true, 999))
    override fun close() { connection.close() }
}
