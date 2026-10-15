package com.architectpsg.commandlibrary.feature.catalog

import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonParser
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration

/** Actual exported schema9, including real FKs/FTS. Faults distinguish rollback from lost commit ACK. */
internal class SearchProjectionSqlDatabase(path: String = ":memory:") : Database, AutoCloseable {
    private val connection = BundledSQLiteDriver().open(path)
    var beforeWrite: ((SqlRequest) -> Unit)? = null
    var afterCommit: (() -> Unit)? = null
    var beforeQuery: ((SqlRequest) -> Unit)? = null
    private var sequence = 0

    init {
        exec("PRAGMA foreign_keys=ON")
        if (rows("SELECT name FROM sqlite_master WHERE name='catalog_generation'").isEmpty()) {
            var directory = File(requireNotNull(System.getProperty("user.dir")))
            val relative = "android-app/data/storage/schemas/com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase/9.json"
            while (!File(directory, relative).isFile) directory = directory.parentFile ?: error("Actual schema9 absent")
            val schema = JsonParser.parseString(File(directory, relative).readText()).asJsonObject.getAsJsonObject("database")
            for (entry in schema.getAsJsonArray("entities")) {
                val entity = entry.asJsonObject
                val table = entity["tableName"].asString
                exec(entity["createSql"].asString.replace("\${TABLE_NAME}", table))
                for (index in entity.getAsJsonArray("indices") ?: emptyList())
                    exec(index.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}", table))
            }
        }
    }

    suspend fun install(records: List<String>): GenerationId = LocalBulkCatalog(this,
        object : Clock {
            override fun utcNow() = UtcTimestamp(3)
            override fun monotonicNow() = Duration.ZERO
        }, object : IdSource { override fun nextId() = "projection-fixture-${++sequence}" },
        CatalogVerifiedInputBinding { _, _, _ -> error("No verified input in synthetic fixture") })
        .`import`(bulkSelected(), bulkChunks(records.joinToString("\n")), {}, BULK_NO_CANCEL).bulkValue().installed.generation.id

    fun exec(statement: String) { connection.prepare(statement).use { it.step() } }
    fun rows(statement: String): SnapshotList<RowData> = read(bulkSql(statement, 101))
    private fun read(request: SqlRequest): SnapshotList<RowData> = connection.prepare(request.statement).use { statement ->
        bind(statement, request)
        SnapshotList.copyOf(buildList {
            while (statement.step()) {
                check(size < request.maxRows)
                add(RowData(SnapshotList.copyOf((0 until statement.getColumnCount()).map { index ->
                    RowColumn(statement.getColumnName(index), when (statement.getColumnType(index)) {
                        1 -> SqlValue.Integer(statement.getLong(index))
                        3 -> SqlValue.Text(statement.getText(index))
                        5 -> SqlValue.Null
                        else -> error("Unexpected fixture SQLite type")
                    })
                })))
            }
        })
    }

    private fun bind(statement: SQLiteStatement, request: SqlRequest) {
        val names = Regex(":([A-Za-z][A-Za-z0-9_]*)").findAll(request.statement).map { it.groupValues[1] }.distinct().toList()
        check(names.toSet() == request.parameters.map { it.name }.toSet())
        names.forEachIndexed { index, name ->
            when (val value = request.parameters.single { it.name == name }.value) {
                SqlValue.Null -> statement.bindNull(index + 1)
                is SqlValue.Integer -> statement.bindLong(index + 1, value.value)
                is SqlValue.Text -> statement.bindText(index + 1, value.value)
                else -> error("Unexpected fixture parameter")
            }
        }
    }

    override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> {
        cancel.throwIfCancelled()
        return try { beforeQuery?.invoke(request); Outcome.Success(read(request)) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { Outcome.Failure(bulkError(FailureCode.PERSISTENCE, "Injected or actual fixture read failure")) }
    }

    override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
        cancel.throwIfCancelled()
        exec("BEGIN IMMEDIATE")
        var committed = false
        val transaction = object : DbTransaction {
            override var state: DbTransactionState = DbTransactionState.Active
            fun latch(result: Outcome<Nothing>) { if (state == DbTransactionState.Active) state = DbTransactionState.RollbackOnly(result) }
            override fun abort(error: DomainFailure): Nothing { latch(Outcome.Failure(error)); throw TransactionAbort(error) }
            override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> {
                val result = this@SearchProjectionSqlDatabase.query(request, cancel)
                when (result) { is Outcome.Failure -> latch(result); is Outcome.Unavailable -> latch(result); is Outcome.Unknown -> latch(result); else -> Unit }
                return result
            }
            override suspend fun execute(request: SqlRequest, cancel: CancelToken): Outcome<DbWriteResult> {
                cancel.throwIfCancelled()
                return try {
                    beforeWrite?.invoke(request)
                    connection.prepare(request.statement).use { bind(it, request); it.step() }
                    Outcome.Success(DbWriteResult(rows("SELECT changes() AS n").single().bulkLong("n")))
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { Outcome.Failure(bulkError(FailureCode.PERSISTENCE, "Injected or actual fixture write failure")).also { latch(it) } }
            }
        }
        try {
            val value = action(transaction)
            cancel.throwIfCancelled()
            val failure = (transaction.state as? DbTransactionState.RollbackOnly)?.status ?: when (value) {
                is Outcome.Failure -> value; is Outcome.Unavailable -> value; is Outcome.Unknown -> value; else -> null
            }
            if (failure != null) { exec("ROLLBACK"); return failure }
            exec("COMMIT")
            committed = true
            afterCommit?.invoke()
            return Outcome.Success(value)
        } catch (error: CancellationException) { if (!committed) exec("ROLLBACK"); throw error }
        catch (error: TransactionAbort) { if (!committed) exec("ROLLBACK"); return (transaction.state as? DbTransactionState.RollbackOnly)?.status ?: Outcome.Failure(error.failure) }
        catch (_: Exception) { if (!committed) exec("ROLLBACK"); return (transaction.state as? DbTransactionState.RollbackOnly)?.status ?: Outcome.Failure(bulkError(FailureCode.INTERNAL, "Fixture transaction acknowledgement failed")) }
    }

    override suspend fun capabilities(cancel: CancelToken): Outcome<DbCapabilities> {
        cancel.throwIfCancelled()
        return Outcome.Success(DbCapabilities("bundled SQLite", 9, true, true, true, 999))
    }
    override fun close() { connection.close() }
}
