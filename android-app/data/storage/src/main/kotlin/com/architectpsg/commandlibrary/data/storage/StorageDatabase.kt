package com.architectpsg.commandlibrary.data.storage

import android.content.Context
import androidx.room3.PooledConnection
import androidx.room3.Room
import androidx.room3.Transactor
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.architectpsg.commandlibrary.core.common.RollbackOnlyLatch
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.schema.Migration1To2
import com.architectpsg.commandlibrary.data.storage.schema.Migration2To3
import com.architectpsg.commandlibrary.data.storage.schema.Migration4To5
import com.architectpsg.commandlibrary.data.storage.schema.Migration5To6
import com.architectpsg.commandlibrary.data.storage.schema.Migration6To7
import com.architectpsg.commandlibrary.data.storage.schema.Migration7To8
import com.architectpsg.commandlibrary.data.storage.schema.Migration8To9
import com.architectpsg.commandlibrary.data.storage.schema.Migration3To4
import com.architectpsg.commandlibrary.data.storage.schema.StorageSchema
import com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Real Room3 storage. Close after callers have finished using this database.
 * Suspend transaction operations belong to the callback Job. Foreign Job or expired-scope
 * query/execute returns INVALID_INPUT without altering the owner latch. Synchronous abort
 * accepts the callback's sequential lexical scopes; independently launched coroutines,
 * foreign threads and expired scopes throw IllegalStateException before altering the latch.
 * Queries support SELECT and WITH SELECT only.
 */
public class StorageDatabase private constructor(private val room: StorageSchemaDatabase) : Database, Closeable {
    private val closed = AtomicBoolean(false)

    public override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> =
        attempt(cancel) {
            checkOpen()
            room.useReaderConnection { queryRows(it, request, cancel) }
        }

    public override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
        var rollbackStatus: Outcome<Nothing>? = null
        val result = attempt(cancel, checkAfter = false) {
            checkOpen()
            room.useWriterConnection { writer ->
                writer.withTransaction<Outcome<T>>(Transactor.SQLiteTransactionType.IMMEDIATE) {
                    val transaction = StorageTransaction(this)
                    withContext(TransactionContext(transaction)) {
                        transaction.start(
                            currentCoroutineContext()[Job],
                            checkNotNull(currentCoroutineContext()[TransactionContext]).token,
                        )
                        try {
                            val value = try {
                                action(transaction)
                            } catch (failure: TransactionAbort) {
                                transaction.observe(Outcome.Failure(failure.failure))
                                checkCancellation(cancel)
                                rollbackStatus = transaction.complete().nonSuccess()
                                rollback(rollbackStatus!!)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                transaction.observe(classify(failure))
                                checkCancellation(cancel)
                                rollbackStatus = transaction.complete().nonSuccess()
                                rollback(rollbackStatus!!)
                            }
                            if (value is Outcome<*>) transaction.observe(value)
                            // Room commits in NonCancellable after this callback. This is the final
                            // cancellation boundary; a cancellation after durable commit cannot undo it.
                            checkCancellation(cancel)
                            when (val permission = transaction.complete()) {
                                is Outcome.Success -> Outcome.Success(value)
                                else -> {
                                    rollbackStatus = permission.nonSuccess()
                                    rollback(rollbackStatus!!)
                                }
                            }
                        } finally {
                            transaction.finish()
                        }
                    }
                }
            }
        }.flatten()
        // SQLite can already have rolled back after an OR ROLLBACK conflict. Room's final
        // ROLLBACK then throws; retain the first typed status without allowing any later SQL.
        return rollbackStatus ?: result
    }

    public override suspend fun capabilities(cancel: CancelToken): Outcome<DbCapabilities> = attempt(cancel) {
        checkOpen()
        room.useWriterConnection { writer ->
            writer.withTransaction<DbCapabilities>(Transactor.SQLiteTransactionType.IMMEDIATE) {
                val engine = usePrepared("SELECT sqlite_version()") { check(it.step()); it.getText(0) }
                val schema = usePrepared("PRAGMA user_version") { check(it.step()); it.getLong(0).toInt() }
                val foreignKeys = usePrepared("PRAGMA foreign_keys") { check(it.step()); it.getLong(0) == 1L }
                val maximum = usePrepared("PRAGMA compile_options") { statement ->
                    var limit: Int? = null
                    while (statement.step()) {
                        checkCancellation(cancel)
                        val option = statement.getText(0)
                        if (option.startsWith("MAX_VARIABLE_NUMBER=")) limit = option.substringAfter('=').toInt()
                    }
                    checkNotNull(limit) { "SQLite did not report its parameter limit" }
                }
                // Verify the actual connection accepts its advertised limit.
                usePrepared("SELECT ?$maximum") { it.bindNull(maximum); check(it.step()) }
                usePrepared("CREATE VIRTUAL TABLE temp.command_library_fts_probe USING fts5(value)") { it.step() }
                usePrepared("INSERT INTO temp.command_library_fts_probe(value) VALUES (:value)") {
                    it.bindText(1, "capability"); it.step()
                }
                val fts5 = usePrepared("SELECT count(*) FROM temp.command_library_fts_probe WHERE value MATCH :term") {
                    it.bindText(1, "\"capability\""); check(it.step()); it.getLong(0) == 1L
                }
                checkCancellation(cancel)
                // The temporary FTS probe is also an actual transaction/rollback probe.
                rollback(DbCapabilities(engine, schema, fts5, transactions = true, foreignKeys, maximum))
            }
        }
    }

    public override fun close(): Unit {
        if (closed.compareAndSet(false, true)) room.close()
    }

    private fun checkOpen(): Unit = check(!closed.get()) { "Database is closed" }

    public companion object {
        /** Opens an app-private file, rejecting unreadable/newer files before Room can write. */
        public suspend fun open(context: Context, fileName: String): Outcome<StorageDatabase> = withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            if (fileName.isBlank() || fileName == ":memory:" || fileName.contains('/') || fileName.contains('\\')) {
                return@withContext invalid("Use an app-private database file name")
            }
            val driver = BundledSQLiteDriver()
            when (val admitted = SchemaVersionGate.admit(SchemaVersionGate.inspect(context.getDatabasePath(fileName), driver), StorageSchema.VERSION)) {
                is Outcome.Success -> Unit
                else -> return@withContext admitted.nonSuccess()
            }
            var database: StorageSchemaDatabase? = null
            try {
                database = Room.databaseBuilder(context.applicationContext, StorageSchemaDatabase::class.java, fileName)
                .addMigrations(Migration1To2, Migration2To3, Migration3To4, Migration4To5, Migration5To6, Migration6To7, Migration7To8, Migration8To9)
                    .setDriver(driver)
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
                database.useReaderConnection { connection ->
                    connection.usePrepared("SELECT count(*) FROM catalog_generation") { check(it.step()) }
                }
                currentCoroutineContext().ensureActive()
                Outcome.Success(StorageDatabase(database))
            } catch (cancelled: CancellationException) {
                database?.close()
                throw cancelled
            } catch (failure: Exception) {
                database?.close()
                classify(failure)
            }
        }
    }
}

private class StorageTransaction(connection: PooledConnection) : DbTransaction {
    private val monitor = Any()
    private var owner: Job? = null
    private var ownerToken: Any? = null
    private var connection: PooledConnection? = connection
    private val latch = RollbackOnlyLatch()
    override val state: DbTransactionState get() = synchronized(monitor) { latch.state }

    fun start(job: Job?, token: Any): Unit = synchronized(monitor) {
        owner = job
        ownerToken = token
    }

    fun observe(outcome: Outcome<*>): Unit = synchronized(monitor) {
        checkOwner()
        check(connection != null) { "Transaction callback has finished" }
        latch.observe(outcome)
        Unit
    }

    /** Revocation and the final latch decision are one atomic operation. */
    fun complete(): Outcome<Unit> = synchronized(monitor) {
        checkOwner()
        check(connection != null) { "Transaction callback has finished" }
        connection = null
        latch.commitPermission()
    }

    override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> =
        operation(cancel) { queryRows(it, request, cancel) }

    override suspend fun execute(request: SqlRequest, cancel: CancelToken): Outcome<DbWriteResult> =
        operation(cancel) { active ->
            val values = NamedParameters.bindOrder(request.statement, request.parameters)
            active.usePrepared(request.statement) { statement ->
                bind(statement, values)
                require(statement.getColumnCount() == 0) { "Execute requires a statement without result columns" }
                statement.step()
            }
            checkCancellation(cancel)
            active.usePrepared("SELECT changes()") { check(it.step()); DbWriteResult(it.getLong(0)) }
        }

    override fun abort(error: DomainFailure): Nothing = synchronized(monitor) {
        checkOwner()
        check(connection != null) { "Transaction callback has finished" }
        latch.abort(error)
    }

    fun finish(): Unit = synchronized(monitor) { connection = null }

    private suspend fun <T> operation(cancel: CancelToken, action: suspend (PooledConnection) -> T): Outcome<T> {
        checkCancellation(cancel)
        val callingJob = currentCoroutineContext()[Job]
        val active = synchronized(monitor) {
            // Reject foreign/post-callback calls before inspecting or mutating the latch.
            if (callingJob !== owner || !isOwner() || connection == null) return invalid("Transaction belongs to its active callback coroutine")
            when (val status = latch.state) {
                is DbTransactionState.RollbackOnly -> return status.status
                DbTransactionState.Active -> checkNotNull(connection)
            }
        }
        val outcome = attempt(cancel) { action(active) }
        observe(outcome)
        return outcome
    }

    private fun isOwner(): Boolean = TransactionContext.current.get().let {
        it != null && it.transaction === this && it.token === ownerToken
    }
    private fun checkOwner(): Unit = check(isOwner()) { "Transaction belongs to its callback coroutine" }
}

/**
 * A stable callback token survives undispatched scope restoration. Child coroutine builders
 * receive a distinct token, so they cannot synchronously abort the owner's transaction.
 * Suspend operations additionally require the exact callback Job.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
private class TransactionContext(private val transaction: StorageTransaction) :
    CopyableThreadContextElement<TransactionContext.Frame?>, AbstractCoroutineContextElement(Key) {
    val token: Any = Any()
    data class Frame(val transaction: StorageTransaction, val token: Any)
    companion object Key : CoroutineContext.Key<TransactionContext> {
        val current = ThreadLocal<Frame?>()
    }
    override fun updateThreadContext(context: CoroutineContext): Frame? {
        val previous = current.get()
        current.set(Frame(transaction, token))
        return previous
    }
    override fun restoreThreadContext(context: CoroutineContext, oldState: Frame?): Unit {
        if (oldState == null) current.remove() else current.set(oldState)
    }
    override fun copyForChild(): TransactionContext = TransactionContext(transaction)
    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext = overwritingElement
}

private suspend fun queryRows(connection: PooledConnection, request: SqlRequest, cancel: CancelToken): SnapshotList<RowData> {
    val values = NamedParameters.bindOrder(request.statement, request.parameters)
    NamedParameters.requireReadQuery(request.statement)
    return connection.usePrepared(request.statement) { statement ->
        bind(statement, values)
        val names = (0 until statement.getColumnCount()).map(statement::getColumnName)
        require(names.isNotEmpty() && names.distinct().size == names.size) { "Query requires uniquely named result columns" }
        SnapshotList.copyOf(boundedRows(request.maxRows) {
            checkCancellation(cancel)
            if (!statement.step()) null else RowData(SnapshotList.copyOf(names.mapIndexed { index, name ->
                val value = when (statement.getColumnType(index)) {
                    1 -> SqlValue.Integer(statement.getLong(index))
                    2 -> SqlValue.DecimalText(statement.getDouble(index).toString())
                    3 -> SqlValue.Text(statement.getText(index))
                    4 -> SqlValue.Blob(SnapshotBytes(statement.getBlob(index)))
                    5 -> SqlValue.Null
                    else -> error("Unknown SQLite column type")
                }
                RowColumn(name, value)
            }))
        })
    }
}

private fun bind(statement: SQLiteStatement, values: List<SqlValue>): Unit {
    values.forEachIndexed { offset, value ->
        val index = offset + 1
        when (value) {
            SqlValue.Null -> statement.bindNull(index)
            is SqlValue.Text -> statement.bindText(index, value.value)
            is SqlValue.Integer -> statement.bindLong(index, value.value)
            is SqlValue.DecimalText -> statement.bindText(index, value.value)
            is SqlValue.Blob -> statement.bindBlob(index, value.value.copyBytes())
        }
    }
}

private suspend fun checkCancellation(cancel: CancelToken): Unit {
    currentCoroutineContext().ensureActive()
    cancel.throwIfCancelled()
}

private suspend fun <T> attempt(cancel: CancelToken, checkAfter: Boolean = true, action: suspend () -> T): Outcome<T> {
    checkCancellation(cancel)
    return try {
        val result = action()
        if (checkAfter) checkCancellation(cancel)
        Outcome.Success(result)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        checkCancellation(cancel)
        classify(failure)
    }
}

private fun invalid(message: String): Outcome.Failure = Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, message, SnapshotList.empty()))
private fun classify(failure: Exception): Outcome.Failure = when (failure) {
    is IllegalArgumentException -> invalid(failure.message ?: "Invalid SQL request")
    is SQLiteException -> Outcome.Failure(DomainFailure(FailureCode.PERSISTENCE, "The local database operation failed", SnapshotList.empty()))
    else -> Outcome.Failure(DomainFailure(FailureCode.INTERNAL, "The local database operation could not complete", SnapshotList.empty()))
}

/** Covariant typed failures contain no value and can be propagated without wrapping them. */
private fun Outcome<*>.nonSuccess(): Outcome<Nothing> = when (this) {
    is Outcome.Failure -> this
    is Outcome.Unavailable -> this
    is Outcome.Unknown -> this
    is Outcome.Success -> error("Expected a non-success outcome")
}

private fun <T> Outcome<Outcome<T>>.flatten(): Outcome<T> = when (this) {
    is Outcome.Success -> value
    else -> nonSuccess()
}
