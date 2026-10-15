package com.architectpsg.commandlibrary.data.storage

import com.architectpsg.commandlibrary.data.storage.schema.StorageSchema

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.schema.Migration2To3
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic sentinel data in the actual exported v2 DDL/identity; never a hand-built schema. */
@RunWith(AndroidJUnit4::class)
class AcquisitionStorageMigrationTest {
    private lateinit var context: Context
    private lateinit var fileName: String
    private var database: StorageDatabase? = null
    private val token = object : CancelToken {
        override val isCancelled: Boolean get() = false
        override fun throwIfCancelled() = Unit
    }

    @Before fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        fileName = "acquisition-migration-${UUID.randomUUID()}.db"
    }

    @After fun close() {
        database?.close()
        context.deleteDatabase(fileName)
    }

    @Test fun actualV2MigrationPreservesLegacyDataAndDurableAcquisitionRows() = runBlocking {
        val fixture = createV2()
        reopen()
        assertEquals(StorageSchema.VERSION, success(database!!.capabilities(token)).schemaVersion)
        assertLegacyUnchanged(fixture)

        write(job("paused"), job("completed"), job("unrelated"))
        success(database!!.transaction({ tx ->
            success(tx.execute(seen("paused", "raw-é", 1), token))
            val changed = success(tx.execute(request(
                "UPDATE acquisition_job SET state='PAUSED', owner_token=NULL, revision=revision+1, durable_offset=:bytes, transferred_bytes=:transfer, record_count=175, page_count=1, expected_records=350, next_page_uri=:next, first_page_sha256=:hash WHERE job_id='paused' AND owner_token='owner-paused' AND state='RUNNING' AND revision=7",
                "bytes" to SqlValue.Integer(5_000_000_123), "transfer" to SqlValue.Integer(5_000_000_999),
                "next" to SqlValue.Text("https://api.scryfall.com/cards/search?q=lang%3Aen&page=2"),
                "hash" to SqlValue.Text("b".repeat(64)),
            ), token))
            assertEquals(1L, changed.affectedRows)
            success(tx.execute(artifact("completed", "artifact-private"), token))
            assertEquals(1L, success(tx.execute(request(
                "UPDATE acquisition_job SET state='COMPLETED', phase='COMPLETE', owner_token=NULL, revision=revision+1 WHERE job_id='completed' AND owner_token='owner-completed' AND state='RUNNING' AND revision=7",
            ), token)).affectedRows)
            success(tx.execute(seen("completed", "raw-é", 1), token))
        }, token))
        val paused = values("SELECT * FROM acquisition_job WHERE job_id='paused'")
        val completed = values("SELECT * FROM acquisition_job WHERE job_id='completed'")
        val verified = values("SELECT * FROM acquisition_artifact WHERE job_id='completed'")
        reopen()
        assertEquals(paused, values("SELECT * FROM acquisition_job WHERE job_id='paused'"))
        assertEquals(completed, values("SELECT * FROM acquisition_job WHERE job_id='completed'"))
        assertEquals(verified, values("SELECT * FROM acquisition_artifact WHERE job_id='completed'"))
        assertEquals(listOf(SqlValue.Text("PAUSED"), SqlValue.Integer(8), SqlValue.Integer(2), SqlValue.Null,
            SqlValue.Integer(5_000_000_123), SqlValue.Integer(5_000_000_999), SqlValue.Integer(175), SqlValue.Integer(1),
            SqlValue.Integer(350), SqlValue.Text("b".repeat(64))), values(
            "SELECT state,revision,attempt,owner_token,durable_offset,transferred_bytes,record_count,page_count,expected_records,first_page_sha256 FROM acquisition_job WHERE job_id='paused'",
        ))
        assertEquals(listOf(SqlValue.Text("artifact-private"), SqlValue.Integer(5_000_000_123), SqlValue.Text("a".repeat(64)),
            SqlValue.Text("verified-é-☃")), values(
            "SELECT artifact_key,byte_count,sha256,verified_payload FROM acquisition_artifact WHERE job_id='completed'",
        ))
        assertEquals(2L, scalar("SELECT count(*) AS total FROM acquisition_seen_id WHERE raw_id='raw-é'"))

        // Producer IDs are unique per job, not globally, and child rows require a real owner job.
        assertConstraintFailure(seen("paused", "raw-é", 2))
        assertConstraintFailure(seen("absent", "orphan", 1))
        assertConstraintFailure(artifact("absent", "orphan-artifact"))
        assertConstraintFailure(artifact("unrelated", "artifact-private"))
        assertConstraintFailure(job("duplicate-key", partialKey = "partial-paused"))
        assertAcquisitionSchema()
        assertLegacyUnchanged(fixture)

        // Job retention/removal cascades only to its own acquisition children.
        write(request("DELETE FROM acquisition_job WHERE job_id='completed'"))
        reopen()
        assertEquals(0L, scalar("SELECT count(*) AS total FROM acquisition_artifact"))
        assertEquals(0L, scalar("SELECT count(*) AS total FROM acquisition_seen_id WHERE job_id='completed'"))
        assertEquals(1L, scalar("SELECT count(*) AS total FROM acquisition_seen_id WHERE job_id='paused'"))
        assertEquals(paused, values("SELECT * FROM acquisition_job WHERE job_id='paused'"))
        assertEquals(1L, scalar("SELECT count(*) AS total FROM acquisition_job WHERE job_id='unrelated'"))
        assertLegacyUnchanged(fixture)
        assertEquals(0L, scalar("SELECT count(*) AS total FROM pragma_foreign_key_check"))
        assertEquals(listOf(SqlValue.Text("ok")), values("SELECT integrity_check FROM pragma_integrity_check"))
    }

    @Test fun staleOwnerCasExplicitAbortRollsBackPrecedingSeenInsertEvenWhenCaught() = runBlocking {
        val fixture = createV2()
        reopen()
        write(job("owner"), seen("owner", "committed", 1))
        val original = values("SELECT * FROM acquisition_job WHERE job_id='owner'")
        val failure = DomainFailure(FailureCode.STALE_REVISION, "Acquisition owner changed", SnapshotList.empty())
        for (catchAbort in listOf(false, true)) {
            val outcome = database!!.transaction({ tx ->
                success(tx.execute(seen("owner", "uncommitted", 2), token))
                val changed = success(tx.execute(request(
                    "UPDATE acquisition_job SET revision=revision+1, durable_offset=999, page_count=2 WHERE job_id='owner' AND revision=7 AND state='RUNNING' AND owner_token='stale-owner'",
                ), token))
                assertEquals(0L, changed.affectedRows)
                // Successful SQL with no matching owner does not automatically mark rollback-only.
                assertEquals(DbTransactionState.Active, tx.state)
                if (catchAbort) {
                    try { tx.abort(failure) } catch (abort: TransactionAbort) { assertSame(failure, abort.failure) }
                    assertEquals(DbTransactionState.RollbackOnly(Outcome.Failure(failure)), tx.state)
                    assertEquals(Outcome.Failure(failure), tx.execute(seen("owner", "after-abort", 3), token))
                } else tx.abort(failure)
            }, token)
            assertEquals(Outcome.Failure(failure), outcome)
            reopen()
            assertEquals(original, values("SELECT * FROM acquisition_job WHERE job_id='owner'"))
            assertEquals(listOf(SqlValue.Text("committed"), SqlValue.Integer(1)), values(
                "SELECT raw_id,page_number FROM acquisition_seen_id WHERE job_id='owner'",
            ))
            assertLegacyUnchanged(fixture)
        }
    }

    @Test fun interruptedMigrationRollsBackPartialDdlAndCanRetry() = runBlocking {
        val fixture = createV2()
        val interruption = CancellationException("Synthetic interruption after job DDL")
        BundledSQLiteDriver().open(context.getDatabasePath(fileName).absolutePath).use { connection ->
            connection.execute("PRAGMA foreign_keys=ON")
            connection.execute("BEGIN IMMEDIATE")
            val interrupted = object : SQLiteConnection by connection {
                private var prepared = 0
                override fun prepare(sql: String): SQLiteStatement {
                    if (prepared++ == 4) throw interruption
                    return connection.prepare(sql)
                }
            }
            try {
                Migration2To3.migrate(interrupted)
                fail("Expected the synthetic migration interruption")
            } catch (cancelled: CancellationException) {
                assertSame(interruption, cancelled)
                connection.execute("ROLLBACK")
            }
            connection.prepare("PRAGMA user_version").use { assertTrue(it.step()); assertEquals(2L, it.getLong(0)) }
            connection.prepare(legacyDdlQuery).use { statement ->
                val afterRollback = buildList { while (statement.step()) add((0 until statement.getColumnCount()).map { statement.value(it) }) }
                assertEquals("Rollback retains the full baseline and leaves no newer objects", fixture.ddl, afterRollback)
            }
            connection.prepare("SELECT count(*) FROM sqlite_master WHERE name LIKE 'acquisition_%' OR name LIKE 'index_acquisition_%'").use {
                assertTrue(it.step()); assertEquals(0L, it.getLong(0))
            }
            connection.prepare("SELECT identity_hash FROM room_master_table WHERE id=42").use {
                assertTrue(it.step()); assertEquals(fixture.schema.getString("identityHash"), it.getText(0))
            }
        }
        // Real Room open must still complete 2->3->4 after the aborted acquisition DDL transaction.
        reopen()
        assertEquals(StorageSchema.VERSION, success(database!!.capabilities(token)).schemaVersion)
        assertAcquisitionSchema()
        assertLegacyUnchanged(fixture)
        reopen()
        assertLegacyUnchanged(fixture)
    }

    @Test fun recoveryPrimitivesFenceInterruptedOwnerAndRetainJobWideBudget() = runBlocking {
        val fixture = createV2()
        reopen()
        write(job("interrupted"), job("paused"), job("cancelled"), job("complete"), job("queued"))
        write(
            request("UPDATE acquisition_job SET state='PAUSED',owner_token=NULL WHERE job_id='paused'"),
            request("UPDATE acquisition_job SET state='CANCELLED',owner_token=NULL WHERE job_id='cancelled'"),
            request("UPDATE acquisition_job SET state='COMPLETED',phase='COMPLETE',owner_token=NULL WHERE job_id='complete'"),
            artifact("complete", "retained-artifact"),
            request("UPDATE acquisition_job SET state='QUEUED',owner_token=NULL WHERE job_id='queued'"),
            request("UPDATE acquisition_job SET durable_offset=5000000123,transferred_bytes=5000000999,record_count=175,page_count=1,expected_records=350,next_page_uri='page-two',first_page_sha256=:hash WHERE job_id='interrupted'",
                "hash" to SqlValue.Text("b".repeat(64))),
            seen("interrupted", "retained-id", 1),
        )
        val retained = listOf("paused", "cancelled", "complete", "queued").associateWith { id ->
            values("SELECT * FROM acquisition_job WHERE job_id='$id'")
        }
        val verified = values("SELECT * FROM acquisition_artifact WHERE job_id='complete'")
        reopen()
        // These are storage primitives; the runner must prove writer quiescence before recovery.
        success(database!!.transaction({ tx ->
            assertEquals(1L, success(tx.execute(request(
                "UPDATE acquisition_job SET state='INTERRUPTED',owner_token=NULL,revision=revision+1 WHERE state='RUNNING'",
            ), token)).affectedRows)
        }, token))
        reopen()
        for ((id, expected) in retained) assertEquals(expected, values("SELECT * FROM acquisition_job WHERE job_id='$id'"))
        assertEquals(verified, values("SELECT * FROM acquisition_artifact WHERE job_id='complete'"))
        val checkpoint = values("SELECT * FROM acquisition_job WHERE job_id='interrupted'")
        assertEquals(listOf(SqlValue.Text("INTERRUPTED"), SqlValue.Null, SqlValue.Integer(8), SqlValue.Integer(2),
            SqlValue.Integer(5_000_000_123), SqlValue.Integer(5_000_000_999), SqlValue.Integer(175), SqlValue.Integer(1)), values(
            "SELECT state,owner_token,revision,attempt,durable_offset,transferred_bytes,record_count,page_count FROM acquisition_job WHERE job_id='interrupted'",
        ))
        val failure = DomainFailure(FailureCode.STALE_REVISION, "Interrupted owner is fenced", SnapshotList.empty())
        assertEquals(Outcome.Failure(failure), database!!.transaction({ tx ->
            success(tx.execute(seen("interrupted", "stale-insert", 2), token))
            assertEquals(0L, success(tx.execute(request(
                "UPDATE acquisition_job SET revision=revision+1,durable_offset=999 WHERE job_id='interrupted' AND revision=7 AND state='RUNNING' AND owner_token='owner-interrupted'",
            ), token)).affectedRows)
            tx.abort(failure)
        }, token))
        reopen()
        assertEquals(checkpoint, values("SELECT * FROM acquisition_job WHERE job_id='interrupted'"))
        assertEquals(listOf(SqlValue.Text("retained-id")), values("SELECT raw_id FROM acquisition_seen_id WHERE job_id='interrupted'"))
        // Changed source/digest restarts the attempt atomically, retaining conservative transfer use.
        success(database!!.transaction({ tx ->
            success(tx.execute(request("DELETE FROM acquisition_seen_id WHERE job_id='interrupted'"), token))
            val changed = success(tx.execute(request(
                "UPDATE acquisition_job SET state='QUEUED',attempt=attempt+1,revision=revision+1,partial_key='partial-attempt-3',durable_offset=0,record_count=0,page_count=0,expected_records=NULL,next_page_uri=NULL,first_page_sha256=NULL,started_seconds=NULL,started_nano=NULL WHERE job_id='interrupted' AND state='INTERRUPTED' AND revision=8 AND owner_token IS NULL",
            ), token))
            if (changed.affectedRows != 1L) tx.abort(failure)
        }, token))
        reopen()
        assertEquals(listOf(SqlValue.Text("QUEUED"), SqlValue.Integer(3), SqlValue.Integer(9), SqlValue.Text("partial-attempt-3"),
            SqlValue.Integer(0), SqlValue.Integer(5_000_000_999), SqlValue.Integer(0), SqlValue.Integer(0),
            SqlValue.Null, SqlValue.Null, SqlValue.Null, SqlValue.Null, SqlValue.Null), values(
            "SELECT state,attempt,revision,partial_key,durable_offset,transferred_bytes,record_count,page_count,expected_records,next_page_uri,first_page_sha256,started_seconds,started_nano FROM acquisition_job WHERE job_id='interrupted'",
        ))
        assertEquals(0L, scalar("SELECT count(*) AS total FROM acquisition_seen_id WHERE job_id='interrupted'"))
        assertLegacyUnchanged(fixture)
    }

    private data class LegacyFixture(val schema: JSONObject, val rows: Map<String, List<SqlValue>>, val ddl: List<List<SqlValue>>)

    private fun createV2(): LegacyFixture {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val path = "com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase/2.json"
        val schema = assets.open(path).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        assertEquals(2, schema.getInt("version"))
        val entities = schema.getJSONArray("entities")
        assertEquals(13, entities.length())
        val snapshots = linkedMapOf<String, List<SqlValue>>()
        val file = context.getDatabasePath(fileName)
        file.parentFile!!.mkdirs()
        val ddl = BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            connection.execute("PRAGMA foreign_keys=ON")
            connection.execute("BEGIN IMMEDIATE")
            try {
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    val table = entity.getString("tableName")
                    connection.execute(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) for (i in 0 until indices.length()) {
                        connection.execute(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                    val fields = entity.getJSONArray("fields")
                    val columns = (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
                    val values = (0 until fields.length()).map { sentinel(table, fields.getJSONObject(it)) }
                    connection.prepare("INSERT INTO `$table` (${columns.joinToString(",") { "`$it`" }}) VALUES (${columns.joinToString(",") { "?" }})").use { statement ->
                        values.forEachIndexed { i, value -> statement.bindValue(i + 1, value) }
                        statement.step()
                    }
                    snapshots[table] = values
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) connection.execute(setup.getString(i))
                connection.execute("PRAGMA user_version=2")
                connection.execute("COMMIT")
            } catch (failure: Throwable) {
                connection.execute("ROLLBACK")
                throw failure
            }
            connection.prepare("SELECT identity_hash FROM room_master_table WHERE id=42").use {
                assertTrue(it.step())
                assertEquals(schema.getString("identityHash"), it.getText(0))
            }
            connection.prepare(legacyDdlQuery).use { statement ->
                buildList { while (statement.step()) add((0 until statement.getColumnCount()).map { statement.value(it) }) }
            }
        }
        return LegacyFixture(schema, snapshots, ddl)
    }

    private fun sentinel(table: String, field: JSONObject): SqlValue {
        val column = field.getString("columnName")
        // Exercise nullable unknown metadata alongside non-null opaque attempt/evidence payloads.
        if (!field.optBoolean("notNull") && column !in setOf("generation_id", "failure_payload")) return SqlValue.Null
        if (field.getString("affinity") == "INTEGER") return SqlValue.Integer(when {
            column == "face_index" || column == "ordinal" || column == "ref_ordinal" -> 0L
            column == "singleton_id" -> 1L
            column.endsWith("_nano") -> 987_654_321L
            else -> 5_000_000_037L
        })
        return SqlValue.Text(when (column) {
            "generation_id" -> if (table.startsWith("rules")) "rules-old" else "catalog-old"
            "corpus_id" -> "rules-old"
            "provider", "oracle_provider", "from_provider" -> "legacy-provider"
            "oracle_id" -> "oracle-old"
            "printing_id" -> "printing-old"
            "local_id", "from_local_id" -> "100.1"
            "language", "oracle_language" -> "en"
            "collector_number" -> "007a"
            else -> "sentinel-é-☃-\u0000-$table-$column"
        })
    }

    private suspend fun assertLegacyUnchanged(fixture: LegacyFixture) {
        val entities = fixture.schema.getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            val fields = entity.getJSONArray("fields")
            val columns = (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
            assertEquals("Every v2 column in $table", fixture.rows.getValue(table), values(
                "SELECT ${columns.joinToString(",") { "`$it`" }} FROM `$table`",
            ))
        }
        // Schema4 changes exactly the listed provenance columns; preserve every prior DDL byte.
        val restoredDdl = rows(legacyDdlQueryFor(fixture.ddl)).map { row ->
            val table = (row[1] as SqlValue.Text).value
            if (row[0] == SqlValue.Text("table") && table in setOf("catalog_generation", "rules_corpus")) {
                var ddl = (row[3] as SqlValue.Text).value
                ddl = ddl.replace(Regex(",\\s*`input_kind` TEXT NOT NULL DEFAULT 'USER_CONTENT_URI'"), "")
                    .replace(Regex(",\\s*`artifact_job_id` TEXT"), "")
                if (table == "catalog_generation") ddl = ddl.replace(Regex(",\\s*`source_payload` TEXT"), "")
                row.take(3) + SqlValue.Text(ddl)
            } else row
        }
        assertEquals("All legacy DDL after removing only schema4 additions", fixture.ddl, restoredDdl)
        val attempt = fixture.rows.getValue("rules_import_attempt")[3] as SqlValue.Text
        assertEquals(listOf(SqlValue.Blob(SnapshotBytes(attempt.value.toByteArray(Charsets.UTF_8)))), values(
            "SELECT CAST(attempt_payload AS BLOB) AS payload_bytes FROM rules_import_attempt",
        ))
        for (table in listOf("catalog_name_fts", "rules_text_fts")) {
            assertEquals(1L, scalar("SELECT count(*) AS total FROM $table WHERE $table MATCH 'sentinel'"))
        }
        assertConstraintFailure(request("DELETE FROM catalog_generation WHERE generation_id='catalog-old'"))
        assertConstraintFailure(request("DELETE FROM rules_corpus WHERE generation_id='rules-old'"))
        assertEquals(0L, scalar("SELECT count(*) AS total FROM pragma_foreign_key_check"))
    }

    private suspend fun assertAcquisitionSchema() {
        val indices = linkedMapOf(
            "index_acquisition_job_state_created" to (false to listOf("state", "created_seconds", "created_nano", "job_id")),
            "index_acquisition_job_created" to (false to listOf("created_seconds", "created_nano", "job_id")),
            "index_acquisition_job_partial_key" to (true to listOf("partial_key")),
            "index_acquisition_artifact_key" to (true to listOf("artifact_key")),
            "index_acquisition_seen_id_page" to (false to listOf("job_id", "page_number")),
        )
        for ((name, expected) in indices) {
            assertEquals(expected.second.map { listOf(SqlValue.Text(it)) }, rows("SELECT name FROM pragma_index_info('$name') ORDER BY seqno"))
            assertEquals(if (expected.first) 1L else 0L, scalar(
                "SELECT \"unique\" FROM pragma_index_list((SELECT tbl_name FROM sqlite_master WHERE name='$name')) WHERE name='$name'",
            ))
        }
        for (table in listOf("acquisition_artifact", "acquisition_seen_id")) {
            assertEquals(listOf(SqlValue.Text("acquisition_job"), SqlValue.Text("job_id"), SqlValue.Text("job_id"), SqlValue.Text("CASCADE")), values(
                "SELECT \"table\",\"from\",\"to\",on_delete FROM pragma_foreign_key_list('$table')",
            ))
        }
        assertEquals(0L, scalar("SELECT count(*) AS total FROM pragma_foreign_key_list('acquisition_job')"))
    }

    private fun job(id: String, partialKey: String = "partial-$id"): SqlRequest = request(
        "INSERT INTO acquisition_job(job_id,payload_version,plan_payload,plan_sha256,state,phase,revision,attempt,owner_token,created_seconds,created_nano,updated_seconds,updated_nano,started_seconds,started_nano,consent_id,partial_key,durable_offset,transferred_bytes,record_count,page_count) VALUES (:id,1,:plan,:hash,'RUNNING','TRANSFERRING',7,2,:owner,5000000037,123,5000000038,456,5000000037,789,'consent-retained',:partial,0,0,0,0)",
        "id" to SqlValue.Text(id), "plan" to SqlValue.Text("synthetic-plan-é-☃"),
        "hash" to SqlValue.Text("c".repeat(64)), "owner" to SqlValue.Text("owner-$id"), "partial" to SqlValue.Text(partialKey),
    )

    private fun artifact(id: String, key: String): SqlRequest = request(
        "INSERT INTO acquisition_artifact(job_id,artifact_key,byte_count,sha256,framing,compression,verified_payload,payload_version,completed_seconds,completed_nano) VALUES (:id,:key,5000000123,:hash,'JSONL','NONE',:payload,1,5000000039,987654321)",
        "id" to SqlValue.Text(id), "key" to SqlValue.Text(key), "hash" to SqlValue.Text("a".repeat(64)),
        "payload" to SqlValue.Text("verified-é-☃"),
    )

    private fun seen(id: String, raw: String, page: Long): SqlRequest = request(
        "INSERT INTO acquisition_seen_id(job_id,raw_id,page_number) VALUES (:id,:raw,:page)",
        "id" to SqlValue.Text(id), "raw" to SqlValue.Text(raw), "page" to SqlValue.Integer(page),
    )

    private suspend fun reopen() {
        database?.close()
        database = success(StorageDatabase.open(context, fileName))
    }

    private suspend fun write(vararg statements: SqlRequest) {
        success(database!!.transaction({ tx -> for (sql in statements) success(tx.execute(sql, token)) }, token))
    }

    private suspend fun assertConstraintFailure(sql: SqlRequest) {
        val outcome = database!!.transaction({ tx -> tx.execute(sql, token) }, token)
        assertEquals(FailureCode.PERSISTENCE, (outcome as Outcome.Failure).error.code)
    }

    private suspend fun rows(sql: String): List<List<SqlValue>> = success(database!!.query(request(sql), token)).map { row -> row.columns.map { it.value } }
    private suspend fun values(sql: String): List<SqlValue> = rows(sql).single()
    private suspend fun scalar(sql: String): Long = (values(sql).single() as SqlValue.Integer).value
    private fun request(sql: String, vararg values: Pair<String, SqlValue>): SqlRequest = SqlRequest(sql, SnapshotList.copyOf(values.map { SqlParameter(it.first, it.second) }))
    private fun SQLiteConnection.execute(sql: String) { prepare(sql).use { it.step() } }
    private fun SQLiteStatement.bindValue(index: Int, value: SqlValue) = when (value) {
        is SqlValue.Text -> bindText(index, value.value)
        is SqlValue.Integer -> bindLong(index, value.value)
        SqlValue.Null -> bindNull(index)
        else -> error("Unexpected fixture value")
    }
    private fun SQLiteStatement.value(index: Int): SqlValue = if (isNull(index)) SqlValue.Null else SqlValue.Text(getText(index))
    private fun <T> success(outcome: Outcome<T>): T {
        assertTrue("Expected success, got $outcome", outcome is Outcome.Success)
        return (outcome as Outcome.Success).value
    }

    private val legacyDdlQuery = LEGACY_DDL_SNAPSHOT_QUERY
}
