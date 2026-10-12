package com.architectpsg.commandlibrary.data.storage

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageDatabaseTest {
    private lateinit var context: Context
    private lateinit var fileName: String
    private lateinit var database: StorageDatabase
    private val neverCancel = TestCancelToken()

    @Before fun open() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        fileName = "storage-test-${UUID.randomUUID()}.db"
        database = success(StorageDatabase.open(context, fileName))
    }

    @After fun close() {
        if (::database.isInitialized) database.close()
        if (::context.isInitialized && ::fileName.isInitialized) context.deleteDatabase(fileName)
    }

    @Test fun boundFtsLiteralPunctuationQuotesAndKeywordsRemainData() = runBlocking {
        val name = "Chandra's \"OR\" AND NOT NEAR (Fire/Ice): +magic*"
        success(database.transaction({ transaction -> success(transaction.execute(insert(name), neverCancel)) }, neverCancel))
        // Literal phrase quoting belongs to the caller. SQL remains fixed and bound.
        val phrase = "\"${name.replace("\"", "\"\"")}\""
        val rows = success(database.query(request(
            "SELECT name FROM catalog_name_fts WHERE catalog_name_fts MATCH :term",
            "term" to SqlValue.Text(phrase),
        ), neverCancel))
        assertEquals(name, (rows.single().columns.single().value as SqlValue.Text).value)
        val injection = "x'); DROP TABLE catalog_name_fts; --"
        success(database.transaction({ transaction -> success(transaction.execute(insert(injection), neverCancel)) }, neverCancel))
        val exact = success(database.query(request(
            "SELECT name FROM catalog_name_fts WHERE name = :name", "name" to SqlValue.Text(injection),
        ), neverCancel))
        assertEquals(injection, (exact.single().columns.single().value as SqlValue.Text).value)
        assertEquals(2L, count())
    }

    @Test fun repeatedNamedParametersUseActualSqliteIndexes() = runBlocking {
        val rows = success(database.query(request(
            "SELECT :second AS first_value, :first AS second_value, :second AS repeated_value, ':ignored' AS literal_value /* :comment */",
            "first" to SqlValue.Integer(7), "second" to SqlValue.Text("bound"),
        ), neverCancel))
        assertEquals(listOf(SqlValue.Text("bound"), SqlValue.Integer(7), SqlValue.Text("bound"), SqlValue.Text(":ignored")),
            rows.single().columns.map { it.value })
    }

    @Test fun sqliteExtendedParameterSuffixesAreRejectedBeforeNativeBinding() = runBlocking {
        for (suffix in listOf("😀", "\u0301", "\u2003", "(x)", "()")) {
            val sql = "SELECT :a$suffix AS first_value, :a AS second_value"
            BundledSQLiteDriver().open(":memory:").use { connection ->
                connection.prepare(sql).use { statement ->
                    statement.bindLong(1, 11)
                    statement.bindLong(2, 22)
                    assertTrue(statement.step())
                    assertEquals(11L, statement.getLong(0))
                    assertEquals(22L, statement.getLong(1))
                }
            }
            val refused = database.query(request(sql, "a" to SqlValue.Integer(11)), neverCancel)
            assertEquals(FailureCode.INVALID_INPUT, (refused as Outcome.Failure).error.code)
            val rolledBack = database.transaction({ transaction ->
                success(transaction.execute(insert("discard"), neverCancel))
                transaction.query(request(sql, "a" to SqlValue.Integer(11)), neverCancel)
            }, neverCancel)
            assertEquals(FailureCode.INVALID_INPUT, (rolledBack as Outcome.Failure).error.code)
            assertEquals(0L, count())
        }
    }

    @Test fun engineRollbackPreventsEveryLaterStatementAndSurvivesReopen() = runBlocking {
        success(database.transaction({ transaction ->
            success(transaction.execute(request("CREATE TABLE storage_rollback_probe(id INTEGER PRIMARY KEY)"), neverCancel))
        }, neverCancel))
        var first: Outcome<*>? = null
        val outcome = database.transaction({ transaction ->
            success(transaction.execute(request("INSERT INTO storage_rollback_probe(id) VALUES (1)"), neverCancel))
            first = transaction.execute(request("INSERT OR ROLLBACK INTO storage_rollback_probe(id) VALUES (1)"), neverCancel)
            assertTrue(first is Outcome.Failure)
            assertSame(first, transaction.execute(request("INSERT INTO storage_rollback_probe(id) VALUES (2)"), neverCancel))
            assertSame(first, transaction.query(request("SELECT id FROM storage_rollback_probe"), neverCancel))
            Unit
        }, neverCancel)
        assertSame(first, outcome)
        database.close()
        database = success(StorageDatabase.open(context, fileName))
        val rows = success(database.query(request("SELECT count(*) AS total FROM storage_rollback_probe"), neverCancel))
        assertEquals(SqlValue.Integer(0), rows.single().columns.single().value)
    }

    @Test fun mutationReturningQueriesIncludingWithAreRejectedAndRollBack() = runBlocking {
        val mutating = listOf(
            "INSERT INTO catalog_name_fts(name, text, generation_id, provider, oracle_id, language) VALUES ('bad', '', 'g', 'p', 'o', 'en') RETURNING name",
            "UPDATE catalog_name_fts SET name='bad' RETURNING name",
            "DELETE FROM catalog_name_fts RETURNING name",
            "WITH source AS (SELECT 'bad' AS name) INSERT INTO catalog_name_fts(name, text, generation_id, provider, oracle_id, language) SELECT name, '', 'g', 'p', 'o', 'en' FROM source RETURNING name",
        )
        for (sql in mutating) {
            val outcome = database.transaction({ transaction ->
                success(transaction.execute(insert("discard"), neverCancel))
                val rejected = transaction.query(request(sql), neverCancel)
                assertEquals(FailureCode.INVALID_INPUT, (rejected as Outcome.Failure).error.code)
                Unit
            }, neverCancel)
            assertTrue(outcome is Outcome.Failure)
            assertEquals(0L, count())
        }
    }

    @Test fun foreignCoroutineAndThreadCallsCannotPoisonOwnerTransaction() = runBlocking {
        val failure = DomainFailure(FailureCode.INVALID_INPUT, "foreign abort", SnapshotList.empty())
        success(database.transaction({ transaction ->
            success(transaction.execute(insert("keep"), neverCancel))
            coroutineScope {
                suspend fun rejectForeignCalls() {
                    assertTrue(transaction.query(request("SELECT 1 AS value"), neverCancel) is Outcome.Failure)
                    assertTrue(transaction.execute(insert("foreign"), neverCancel) is Outcome.Failure)
                    try {
                        transaction.abort(failure)
                    }
                    catch (rejected: IllegalStateException) { assertTrue(rejected.message!!.contains("callback coroutine")) }
                }
                launch { rejectForeignCalls() }.join()
                launch(start = CoroutineStart.UNDISPATCHED) { rejectForeignCalls() }.join()
                async { rejectForeignCalls() }.await()
            }
            // Returning from an undispatched scope must restore usable callback ownership.
            success(transaction.query(request("SELECT 1 AS value"), neverCancel))
            coroutineScope { yield() }
            success(transaction.execute(insert("keep-after-scope"), neverCancel))
            val threadResult = CompletableDeferred<Throwable>()
            val thread = Thread {
                try { transaction.abort(failure) } catch (rejected: Throwable) { threadResult.complete(rejected) }
            }
            thread.start()
            assertTrue(withTimeout(10_000) { threadResult.await() } is IllegalStateException)
            thread.join()
            assertEquals(DbTransactionState.Active, transaction.state)
            success(transaction.query(request("SELECT count(*) AS total FROM catalog_name_fts"), neverCancel))
            Unit
        }, neverCancel))
        assertEquals(2L, count())
    }

    @Test fun ownerAbortAfterSuspendedNestedScopesRollsBack() = runBlocking {
        val failure = DomainFailure(FailureCode.INVALID_INPUT, "owner abort after scope", SnapshotList.empty())
        val outcome = database.transaction({ transaction ->
            success(transaction.execute(insert("discard"), neverCancel))
            coroutineScope {
                launch(start = CoroutineStart.UNDISPATCHED) { yield() }.join()
                yield()
            }
            // No query/execute call may be needed to repair a stale thread-local frame.
            transaction.abort(failure)
        }, neverCancel)
        assertEquals(failure, (outcome as Outcome.Failure).error)
        assertEquals(0L, count())
    }

    @Test fun ignoredQueryFailureRollsBackEarlierWrite() = runBlocking {
        val outcome = database.transaction({ transaction ->
            success(transaction.execute(insert("discard"), neverCancel))
            assertTrue(transaction.query(request("SELECT name FROM absent_table"), neverCancel) is Outcome.Failure)
            assertTrue(transaction.state is DbTransactionState.RollbackOnly)
            Unit
        }, neverCancel)
        assertTrue(outcome is Outcome.Failure)
        assertEquals(0L, count())
    }

    @Test fun missingParameterAndManualCommitLatchRollback() = runBlocking {
        for (invalid in listOf(request("SELECT :missing"), request("COMMIT"))) {
            val outcome = database.transaction({ transaction ->
                success(transaction.execute(insert("discard"), neverCancel))
                assertTrue(transaction.query(invalid, neverCancel) is Outcome.Failure)
                Unit
            }, neverCancel)
            assertEquals(FailureCode.INVALID_INPUT, (outcome as Outcome.Failure).error.code)
            assertEquals(0L, count())
        }
    }

    @Test fun abortAndCaughtAbortBothRollBackWithTypedStatus() = runBlocking {
        val failure = DomainFailure(FailureCode.INVALID_INPUT, "invalid generation", SnapshotList.empty())
        for (catchAbort in listOf(false, true)) {
            val outcome = database.transaction({ transaction ->
                success(transaction.execute(insert("discard"), neverCancel))
                if (catchAbort) {
                    try { transaction.abort(failure) } catch (abort: TransactionAbort) { assertSame(failure, abort.failure) }
                    assertEquals(DbTransactionState.RollbackOnly(Outcome.Failure(failure)), transaction.state)
                    Unit
                } else transaction.abort(failure)
            }, neverCancel)
            assertEquals(Outcome.Failure(failure), outcome)
            assertEquals(0L, count())
        }
    }

    @Test fun returnedTypedStatusesRollBackAndFirstFailureWins() = runBlocking {
        val statuses = listOf<Outcome<Nothing>>(
            Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, "validation", SnapshotList.empty())),
            Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "unsupported"),
            Outcome.Unknown("unknown validation", SnapshotList.empty()),
        )
        for (status in statuses) {
            val outcome = database.transaction({ transaction ->
                success(transaction.execute(insert("discard"), neverCancel))
                status
            }, neverCancel)
            assertSame(status, outcome)
            assertEquals(0L, count())
        }
        var first: Outcome<*>? = null
        val outcome = database.transaction({ transaction ->
            success(transaction.execute(insert("discard"), neverCancel))
            first = transaction.query(request("SELECT :missing"), neverCancel)
            statuses[1]
        }, neverCancel)
        assertSame(first, outcome)
        assertEquals(0L, count())
    }

    @Test fun thrownExceptionAndForeignKeyViolationRollBack() = runBlocking {
        val thrown = database.transaction({ transaction ->
            success(transaction.execute(insert("discard"), neverCancel))
            throw IllegalStateException("callback error")
        }, neverCancel)
        assertTrue(thrown is Outcome.Failure)
        assertEquals(0L, count())
        val foreignKey = database.transaction({ transaction ->
            success(transaction.execute(insert("discard"), neverCancel))
            transaction.execute(request(
                "INSERT INTO catalog_active(dataset_key, generation_id) VALUES (:key, :generation)",
                "key" to SqlValue.Text("cards"), "generation" to SqlValue.Text("missing"),
            ), neverCancel)
        }, neverCancel)
        assertTrue(foreignKey is Outcome.Failure)
        assertEquals(0L, count())
    }

    @Test fun callerTokenCancellationBeforeCommitWinsOverLatchedFailure() = runBlocking {
        val token = TestCancelToken()
        try {
            database.transaction({ transaction ->
                success(transaction.execute(insert("discard"), token))
                transaction.query(request("SELECT :missing"), token)
                token.cancelled.set(true)
                Unit
            }, token)
            fail("Cancellation must escape")
        } catch (cancelled: CancellationException) {
            assertSame(token.exception, cancelled)
        }
        assertEquals(0L, count())
    }

    @Test fun structuredCancellationAfterWriteRollsBackAndRethrows() = runBlocking {
        val written = CompletableDeferred<Unit>()
        val caught = CompletableDeferred<CancellationException>()
        val cancellation = CancellationException("instrumentation cancellation")
        val worker = launch {
            try {
                database.transaction({ transaction ->
                    success(transaction.execute(insert("discard"), neverCancel))
                    written.complete(Unit)
                    awaitCancellation()
                }, neverCancel)
            } catch (cancelled: CancellationException) {
                caught.complete(cancelled)
                throw cancelled
            }
        }
        withTimeout(10_000) { written.await() }
        worker.cancel(cancellation)
        worker.join()
        assertSame(cancellation, caught.await())
        assertEquals(0L, count())
    }

    @Test fun queryBoundsAt101AndValuesAreCopiedOutOfStatementLifetime() = runBlocking {
        val rows = success(database.query(SqlRequest(
            "WITH RECURSIVE numbers(value) AS (SELECT 1 UNION ALL SELECT value+1 FROM numbers WHERE value < 120) SELECT value FROM numbers",
            SnapshotList.empty(), 101,
        ), neverCancel))
        assertEquals(101, rows.size)
        assertEquals(SqlValue.Integer(101), rows.last().columns.single().value)
        try { SqlRequest("SELECT 1", SnapshotList.empty(), 102); fail("102 must be rejected") }
        catch (expected: IllegalArgumentException) { assertEquals(101, SqlRequest.MAX_ROWS) }
        val bytes = byteArrayOf(1, 2, 3)
        val values = success(database.query(request(
            "SELECT :blob AS blob_value, :decimal AS decimal_value, :null AS null_value, :integer AS integer_value",
            "blob" to SqlValue.Blob(SnapshotBytes(bytes)), "decimal" to SqlValue.DecimalText("12.340"),
            "null" to SqlValue.Null, "integer" to SqlValue.Integer(42),
        ), neverCancel)).single()
        database.close()
        bytes[0] = 99
        assertArrayEquals(byteArrayOf(1, 2, 3), (values.columns[0].value as SqlValue.Blob).value.copyBytes())
        assertEquals(SqlValue.Text("12.340"), values.columns[1].value)
        assertEquals(SqlValue.Null, values.columns[2].value)
        assertEquals(SqlValue.Integer(42), values.columns[3].value)
    }

    @Test fun committedRowsSurviveCloseReopenAndLateTokenCancellation() = runBlocking {
        val token = TestCancelToken()
        success(database.transaction({ transaction -> success(transaction.execute(insert("keep"), token)) }, token))
        token.cancelled.set(true)
        database.close()
        database = success(StorageDatabase.open(context, fileName))
        assertEquals(1L, count())
    }

    @Test fun escapedTransactionAndClosedDatabaseCannotBeUsed() = runBlocking {
        var escaped: DbTransaction? = null
        success(database.transaction({ transaction -> escaped = transaction; Unit }, neverCancel))
        assertTrue(escaped!!.query(request("SELECT 1 AS value"), neverCancel) is Outcome.Failure)
        try { escaped!!.abort(DomainFailure(FailureCode.INVALID_INPUT, "late abort", SnapshotList.empty())) }
        catch (rejected: IllegalStateException) { assertTrue(rejected.message!!.contains("callback coroutine")) }
        assertEquals(DbTransactionState.Active, escaped!!.state)
        database.close()
        database.close()
        assertTrue(database.query(request("SELECT 1 AS value"), neverCancel) is Outcome.Failure)
    }

    @Test fun newerSchemaIsRejectedAndCommittedRowsRemain() = runBlocking {
        success(database.transaction({ transaction -> success(transaction.execute(insert("keep"), neverCancel)) }, neverCancel))
        database.close()
        val file = context.getDatabasePath(fileName)
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            connection.prepare("PRAGMA user_version = 2").use { it.step() }
        }
        val before = file.readBytes()
        val refused = StorageDatabase.open(context, fileName)
        assertEquals(UnavailableReason.UNSUPPORTED, (refused as Outcome.Unavailable).reason)
        assertArrayEquals(before, file.readBytes())
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            connection.prepare("PRAGMA user_version").use { assertTrue(it.step()); assertEquals(2L, it.getLong(0)) }
            connection.prepare("SELECT count(*) FROM catalog_name_fts").use { assertTrue(it.step()); assertEquals(1L, it.getLong(0)) }
        }
    }

    @Test fun corruptFileIsRejectedWithoutChangingItsBytes() = runBlocking {
        database.close()
        context.deleteDatabase(fileName)
        val file = context.getDatabasePath(fileName)
        val bytes = "This is not a SQLite database and must remain recoverable".toByteArray()
        file.writeBytes(bytes)
        val refused = StorageDatabase.open(context, fileName)
        assertEquals(FailureCode.CORRUPT_DATA, (refused as Outcome.Failure).error.code)
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun capabilitiesUseRealEngineForeignKeysParameterLimitAndFts5() = runBlocking {
        val capabilities = success(database.capabilities(neverCancel))
        assertTrue(capabilities.engineVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+.*")))
        assertEquals(1, capabilities.schemaVersion)
        assertTrue(capabilities.fts5)
        assertTrue(capabilities.transactions)
        assertTrue(capabilities.foreignKeys)
        assertTrue(capabilities.maxBoundParameters >= 999)
        assertEquals(capabilities, success(database.capabilities(neverCancel)))
        assertEquals(0L, count())
    }

    private suspend fun count(): Long = (success(database.query(request("SELECT count(*) AS total FROM catalog_name_fts"), neverCancel))
        .single().columns.single().value as SqlValue.Integer).value

    private fun insert(name: String): SqlRequest = request(
        "INSERT INTO catalog_name_fts(name, text, generation_id, provider, oracle_id, language) VALUES (:name, :text, :generation, :provider, :oracle, :language)",
        "name" to SqlValue.Text(name), "text" to SqlValue.Text(""), "generation" to SqlValue.Text("test-generation"),
        "provider" to SqlValue.Text("test-provider"), "oracle" to SqlValue.Text(name), "language" to SqlValue.Text("en"),
    )

    private fun request(sql: String, vararg values: Pair<String, SqlValue>): SqlRequest =
        SqlRequest(sql, SnapshotList.copyOf(values.map { SqlParameter(it.first, it.second) }))

    private fun <T> success(outcome: Outcome<T>): T {
        assertTrue("Expected success, got $outcome", outcome is Outcome.Success)
        return (outcome as Outcome.Success).value
    }

    private class TestCancelToken : CancelToken {
        val cancelled = AtomicBoolean(false)
        val exception = CancellationException("caller token cancellation")
        override val isCancelled: Boolean get() = cancelled.get()
        override fun throwIfCancelled() { if (isCancelled) throw exception }
    }
}
