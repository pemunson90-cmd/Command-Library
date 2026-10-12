package com.architectpsg.commandlibrary.data.storage

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Starts from the actual KSP-exported schema 1 and its identity, rather than a lookalike fixture. */
@RunWith(AndroidJUnit4::class)
class RulesStorageMigrationTest {
    private lateinit var context: Context
    private lateinit var fileName: String
    private var database: StorageDatabase? = null
    private val token = object : CancelToken {
        override val isCancelled: Boolean get() = false
        override fun throwIfCancelled() = Unit
    }

    @Before fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        fileName = "rules-migration-${UUID.randomUUID()}.db"
    }

    @After fun close() {
        database?.close()
        context.deleteDatabase(fileName)
    }

    @Test fun actualV1CatalogSurvivesRulesMigrationActivationAndReopen() = runBlocking {
        val schema = exportedV1()
        val original = createV1(schema)
        database = success(StorageDatabase.open(context, fileName))
        assertEquals(2, success(database!!.capabilities(token)).schemaVersion)
        assertCatalogUnchanged(schema, original)
        assertEquals(1L, scalar("SELECT count(*) AS total FROM catalog_name_fts WHERE catalog_name_fts MATCH 'sentinel'"))

        write(
            corpus("rules-old", entries = 1, references = 2),
            "INSERT INTO rule_entry(corpus_id,provider,local_id,kind,number,title,text,ordinal,entry_payload) VALUES ('rules-old','rules-provider','100.1','NUMBERED','100.1','Café','Café says \"OR\"; draw a card.',0,'entry-é')",
            "INSERT INTO rule_reference(corpus_id,from_provider,from_local_id,ref_ordinal,raw_target,resolved_provider,resolved_local_id,evidence_payload) VALUES ('rules-old','rules-provider','100.1',0,'see 999.1',NULL,NULL,'unknown-evidence')",
            "INSERT INTO rule_reference(corpus_id,from_provider,from_local_id,ref_ordinal,raw_target,resolved_provider,resolved_local_id,evidence_payload) VALUES ('rules-old','rules-provider','100.1',1,'see external rule','external-provider','outside-corpus','known-evidence')",
            "INSERT INTO rules_text_fts(title,text,corpus_id,provider,local_id) SELECT COALESCE(title,''),text,corpus_id,provider,local_id FROM rule_entry WHERE corpus_id='rules-old'",
            "INSERT INTO rules_active(singleton_id,generation_id) VALUES (1,'rules-old')",
            "INSERT INTO rules_import_attempt(attempt_id,generation_id,source_payload,attempt_payload,phase,started_at_epoch_seconds,started_at_nano,finished_at_epoch_seconds,finished_at_nano,failure_payload,payload_schema_version) VALUES ('attempt-old','rules-old','source-unknown','attempt-completed','COMPLETED',10,42,11,43,NULL,1)",
            "INSERT INTO rules_import_attempt(attempt_id,generation_id,source_payload,attempt_payload,phase,started_at_epoch_seconds,started_at_nano,finished_at_epoch_seconds,finished_at_nano,failure_payload,payload_schema_version) VALUES ('attempt-failed','discarded-staging','source-unknown','attempt-failed-é','FAILED',12,44,13,45,'failure-é',1)",
        )
        assertEquals(1L, scalar("SELECT count(*) AS total FROM rules_text_fts WHERE rules_text_fts MATCH 'cafe'"))
        assertEquals(0L, scalar("SELECT count(*) AS total FROM rules_text_fts WHERE rules_text_fts MATCH 'provider'"))
        assertEquals(listOf(SqlValue.Null, SqlValue.Null, SqlValue.Text("unknown-evidence")), values(
            "SELECT resolved_provider,resolved_local_id,evidence_payload FROM rule_reference WHERE ref_ordinal=0",
        ))
        assertEquals(listOf(SqlValue.Text("external-provider"), SqlValue.Text("outside-corpus"), SqlValue.Text("known-evidence")), values(
            "SELECT resolved_provider,resolved_local_id,evidence_payload FROM rule_reference WHERE ref_ordinal=1",
        ))
        assertEquals(listOf(SqlValue.Null, SqlValue.Null, SqlValue.Null, SqlValue.Null), values(
            "SELECT upstream_version,effective_at_epoch_seconds,source_date_epoch_seconds,source_uri FROM rules_corpus",
        ))
        assertConstraintFailure("INSERT INTO rules_active(singleton_id,generation_id) VALUES (2,'missing-corpus')")
        assertConstraintFailure("INSERT INTO rule_entry(corpus_id,provider,local_id,kind,text,ordinal,entry_payload) VALUES ('missing-corpus','p','x','NOTICE','invalid',1,'invalid')")
        assertConstraintFailure("INSERT INTO rule_entry(corpus_id,provider,local_id,kind,text,ordinal,entry_payload) VALUES ('rules-old','another-provider','different-id','NOTICE','duplicate ordinal',0,'invalid')")
        assertConstraintFailure("INSERT INTO rule_reference(corpus_id,from_provider,from_local_id,ref_ordinal,raw_target,evidence_payload) VALUES ('rules-old','rules-provider','missing-entry',0,'unknown','invalid')")
        assertConstraintFailure("DELETE FROM rules_corpus WHERE generation_id='rules-old'")

        database!!.close()
        database = success(StorageDatabase.open(context, fileName))
        assertCatalogUnchanged(schema, original)
        assertEquals(listOf(SqlValue.Integer(1), SqlValue.Text("rules-old")), values("SELECT singleton_id,generation_id FROM rules_active"))
        assertEquals(1L, scalar("SELECT count(*) AS total FROM rules_text_fts WHERE rules_text_fts MATCH 'cafe'"))
        assertEquals(listOf(SqlValue.Text("failure-é"), SqlValue.Integer(45), SqlValue.Text("attempt-failed-é")), values(
            "SELECT failure_payload,finished_at_nano,attempt_payload FROM rules_import_attempt WHERE attempt_id='attempt-failed'",
        ))

        // Failed activation rolls back both corpus creation and pointer replacement.
        val failed = database!!.transaction({ transaction ->
            success(transaction.execute(request(corpus("rules-discard")), token))
            success(transaction.execute(request("UPDATE rules_active SET generation_id='rules-discard' WHERE singleton_id=1"), token))
            transaction.execute(request("INSERT INTO rule_entry(corpus_id,provider,local_id,kind,text,ordinal,entry_payload) VALUES ('missing','p','x','NOTICE','bad',0,'bad')"), token)
        }, token)
        assertTrue(failed is Outcome.Failure)
        assertEquals(0L, scalar("SELECT count(*) AS total FROM rules_corpus WHERE generation_id='rules-discard'"))
        assertEquals(listOf(SqlValue.Text("rules-old")), values("SELECT generation_id FROM rules_active WHERE singleton_id=1"))

        // Replacement is atomic; retired deletion cascades entries/references and explicitly removes FTS.
        write(
            corpus("rules-new"),
            "UPDATE rules_active SET generation_id='rules-new' WHERE singleton_id=1",
            "DELETE FROM rules_text_fts WHERE corpus_id='rules-old'",
            "DELETE FROM rules_corpus WHERE generation_id='rules-old'",
        )
        database!!.close()
        database = success(StorageDatabase.open(context, fileName))
        assertCatalogUnchanged(schema, original)
        assertEquals(listOf(SqlValue.Text("rules-new")), values("SELECT generation_id FROM rules_active WHERE singleton_id=1"))
        for (table in listOf("rule_entry", "rule_reference", "rules_text_fts")) {
            assertEquals(table, 0L, scalar("SELECT count(*) AS total FROM $table WHERE corpus_id='rules-old'"))
        }
        assertEquals(2L, scalar("SELECT count(*) AS total FROM rules_import_attempt"))
        // Audit row survives retirement/deletion of its associated corpus.
        assertEquals(listOf(SqlValue.Text("rules-old")), values("SELECT generation_id FROM rules_import_attempt WHERE attempt_id='attempt-old'"))
        assertEquals(0L, scalar("SELECT count(*) AS total FROM pragma_foreign_key_check"))
    }

    private fun exportedV1(): JSONObject {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val path = "com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase/1.json"
        return testContext.assets.open(path).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            .also { assertEquals(1, it.getInt("version")) }
    }

    private fun createV1(schema: JSONObject): Map<String, List<SqlValue>> {
        val file = context.getDatabasePath(fileName)
        file.parentFile!!.mkdirs()
        val snapshots = linkedMapOf<String, List<SqlValue>>()
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            connection.execute("PRAGMA foreign_keys=ON")
            connection.execute("BEGIN IMMEDIATE")
            try {
                val entities = schema.getJSONArray("entities")
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
                    val sentinel = (0 until fields.length()).map { field -> sentinel(fields.getJSONObject(field)) }
                    val sql = "INSERT INTO `$table` (${columns.joinToString(",") { "`$it`" }}) VALUES (${columns.joinToString(",") { "?" }})"
                    connection.prepare(sql).use { statement ->
                        sentinel.forEachIndexed { i, value -> when (value) {
                            is SqlValue.Text -> statement.bindText(i + 1, value.value)
                            is SqlValue.Integer -> statement.bindLong(i + 1, value.value)
                            SqlValue.Null -> statement.bindNull(i + 1)
                            else -> error("Unexpected fixture value")
                        } }
                        statement.step()
                    }
                    snapshots[table] = sentinel
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) connection.execute(setup.getString(i))
                connection.execute("PRAGMA user_version=1")
                connection.execute("COMMIT")
            } catch (failure: Throwable) {
                connection.execute("ROLLBACK")
                throw failure
            }
            connection.prepare("SELECT identity_hash FROM room_master_table WHERE id=42").use {
                assertTrue(it.step())
                assertEquals(schema.getString("identityHash"), it.getText(0))
            }
        }
        return snapshots
    }

    private fun sentinel(field: JSONObject): SqlValue {
        val column = field.getString("columnName")
        if (column in setOf("source_version", "provider_face_id", "image_uri")) return SqlValue.Null
        if (field.getString("affinity") == "INTEGER") return SqlValue.Integer(if (column == "face_index") 0 else 37)
        return SqlValue.Text(when (column) {
            "generation_id" -> "catalog-old"
            "provider", "oracle_provider" -> "legacy-provider"
            "oracle_id" -> "oracle-old"
            "printing_id" -> "printing-old"
            "language", "oracle_language" -> "en"
            "collector_number" -> "007a"
            else -> "sentinel-é-$column"
        })
    }

    private suspend fun assertCatalogUnchanged(schema: JSONObject, snapshots: Map<String, List<SqlValue>>) {
        val entities = schema.getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            val fields = entity.getJSONArray("fields")
            val columns = (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
            val rows = success(database!!.query(request("SELECT ${columns.joinToString(",") { "`$it`" }} FROM `$table`"), token))
            assertEquals("Rows in $table", 1, rows.size)
            assertEquals("Every v1 column in $table", snapshots.getValue(table), rows.single().columns.map { it.value })
        }
    }

    private fun corpus(id: String, entries: Long = 0, references: Long = 0): String =
        "INSERT INTO rules_corpus(generation_id,provider,language,status,content_uri,display_name,source_payload,provenance_payload,observed_at_epoch_seconds,observed_at_nano,content_sha256,observed_bytes,entry_count,reference_count,payload_schema_version) " +
            "VALUES ('$id','rules-provider','en','ACTIVE','content://rules/$id','rules.txt','source-unknown','provenance-é',10,42,'${"a".repeat(64)}',120,$entries,$references,1)"

    private suspend fun write(vararg statements: String) {
        success(database!!.transaction({ transaction ->
            for (sql in statements) success(transaction.execute(request(sql), token))
        }, token))
    }

    private suspend fun assertConstraintFailure(sql: String) {
        val result = database!!.transaction({ transaction -> transaction.execute(request(sql), token) }, token)
        assertTrue("Expected SQL constraint failure: $sql, got $result", result is Outcome.Failure)
        assertEquals(FailureCode.PERSISTENCE, (result as Outcome.Failure).error.code)
    }

    private suspend fun values(sql: String): List<SqlValue> =
        success(database!!.query(request(sql), token)).single().columns.map { it.value }

    private suspend fun scalar(sql: String): Long = (values(sql).single() as SqlValue.Integer).value
    private fun request(sql: String): SqlRequest = SqlRequest(sql, SnapshotList.empty())
    private fun SQLiteConnection.execute(sql: String) { prepare(sql).use { it.step() } }
    private fun <T> success(outcome: Outcome<T>): T {
        assertTrue("Expected success, got $outcome", outcome is Outcome.Success)
        return (outcome as Outcome.Success).value
    }
}
