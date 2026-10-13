package com.architectpsg.commandlibrary.data.storage

import com.architectpsg.commandlibrary.data.storage.schema.StorageSchema

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
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

/**
 * Real committed schema-4 file, migrated through schema5 to current via StorageDatabase.open.
 * The schema6 gate also checks Room's automatic validation of all six additive life tables.
 */
@RunWith(AndroidJUnit4::class)
class OriginCooldownMigrationTest {
    private lateinit var context: Context
    private lateinit var fileName: String
    private var database: StorageDatabase? = null
    private val token = object : CancelToken {
        override val isCancelled: Boolean get() = false
        override fun throwIfCancelled() = Unit
    }

    @Before fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        fileName = "origin-cooldown-migration-${UUID.randomUUID()}.db"
    }

    @After fun close() { database?.close(); context.deleteDatabase(fileName) }

    @Test fun schema4RowsAndDdlSurviveSchema5MigrationAndCooldownOutlivesJob() = runBlocking {
        val fixture = createV4()
        reopen()
        assertEquals(StorageSchema.VERSION, success(database!!.capabilities(token)).schemaVersion)
        assertLegacyPreserved(fixture)
        assertCooldownShape()

        write("INSERT INTO acquisition_origin_cooldown(origin,revision,interval_seconds,interval_nano) VALUES ('https://api.scryfall.com:443',1,60,123)")
        write("INSERT INTO acquisition_origin_cooldown(origin,revision,interval_seconds,interval_nano) VALUES ('https://other.example:443',1,NULL,NULL)")
        val expected = rows("SELECT origin,revision,interval_seconds,interval_nano FROM acquisition_origin_cooldown ORDER BY origin")
        assertEquals(2, expected.size)
        reopen()
        assertEquals(expected, rows("SELECT origin,revision,interval_seconds,interval_nano FROM acquisition_origin_cooldown ORDER BY origin"))
        write("DELETE FROM acquisition_job WHERE job_id='job-old'")
        reopen()
        assertEquals(expected, rows("SELECT origin,revision,interval_seconds,interval_nano FROM acquisition_origin_cooldown ORDER BY origin"))
        assertEquals(0L, scalar("SELECT count(*) FROM acquisition_artifact"))
        assertEquals(0L, scalar("SELECT count(*) FROM acquisition_seen_id"))
        assertEquals(0L, scalar("SELECT count(*) FROM pragma_foreign_key_check"))
        assertLegacyPreserved(fixture, afterJobDelete = true)
    }

    @Test fun tombstoneRevisionCasSurvivesReopenAndFreshTableMatchesMigratedTable() = runBlocking {
        createV4()
        reopen()
        val migratedShape = cooldownShape()
        val origin = "https://api.scryfall.com:443"
        write("INSERT INTO acquisition_origin_cooldown(origin,revision,interval_seconds,interval_nano) VALUES ('$origin',1,30,0)")
        assertEquals(1L, update("UPDATE acquisition_origin_cooldown SET revision=2,interval_seconds=90,interval_nano=5 WHERE origin='$origin' AND revision=1"))
        assertEquals(0L, update("UPDATE acquisition_origin_cooldown SET revision=3,interval_seconds=0,interval_nano=0 WHERE origin='$origin' AND revision=1"))
        assertEquals(1L, update("UPDATE acquisition_origin_cooldown SET revision=3,interval_seconds=0,interval_nano=0 WHERE origin='$origin' AND revision=2"))
        reopen()
        assertEquals(listOf(SqlValue.Integer(3), SqlValue.Integer(0), SqlValue.Integer(0)),
            values("SELECT revision,interval_seconds,interval_nano FROM acquisition_origin_cooldown WHERE origin='$origin'"))
        assertEquals(0L, update("UPDATE acquisition_origin_cooldown SET revision=4,interval_seconds=0,interval_nano=0 WHERE origin='$origin' AND revision=2"))
        assertEquals(1L, update("UPDATE acquisition_origin_cooldown SET revision=4,interval_seconds=30,interval_nano=0 WHERE origin='$origin' AND revision=3"))
        assertEquals(listOf(SqlValue.Integer(4), SqlValue.Integer(30), SqlValue.Integer(0)),
            values("SELECT revision,interval_seconds,interval_nano FROM acquisition_origin_cooldown WHERE origin='$origin'"))

        val freshName = "origin-cooldown-fresh-${UUID.randomUUID()}.db"
        try {
            val fresh = success(StorageDatabase.open(context, freshName))
            try {
                assertEquals(StorageSchema.VERSION, success(fresh.capabilities(token)).schemaVersion)
                assertEquals(migratedShape, cooldownShape(fresh))
            } finally { fresh.close() }
        } finally { context.deleteDatabase(freshName) }
    }

    private data class Fixture(
        val schema: JSONObject,
        val rows: Map<String, List<List<SqlValue>>>,
        val columns: Map<String, List<List<SqlValue>>>,
        val ddl: List<List<SqlValue>>,
    )

    private fun createV4(): Fixture {
        val path = "com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase/4.json"
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val schema = assets.open(path).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        assertEquals(4, schema.getInt("version"))
        val entities = schema.getJSONArray("entities")
        assertEquals(17, entities.length())
        val file = context.getDatabasePath(fileName); file.parentFile!!.mkdirs()
        return BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            connection.execute("PRAGMA foreign_keys=ON")
            connection.execute("BEGIN IMMEDIATE")
            try {
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                    connection.execute(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) for (j in 0 until indices.length()) connection.execute(
                        indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    val fields = entity.getJSONArray("fields")
                    val names = (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
                    connection.prepare("INSERT INTO `$table` (${names.joinToString(",") { "`$it`" }}) VALUES (${names.joinToString(",") { "?" }})").use { statement ->
                        for (j in 0 until fields.length()) statement.bindValue(j + 1, sentinel(table, fields.getJSONObject(j)))
                        statement.step()
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) connection.execute(setup.getString(i))
                connection.execute("PRAGMA user_version=4")
                connection.execute("COMMIT")
            } catch (failure: Throwable) { connection.execute("ROLLBACK"); throw failure }
            assertEquals(listOf(listOf(SqlValue.Text(schema.getString("identityHash")))),
                connection.readRows("SELECT identity_hash FROM room_master_table WHERE id=42"))
            val snapshots = linkedMapOf<String, List<List<SqlValue>>>()
            val columns = linkedMapOf<String, List<List<SqlValue>>>()
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                snapshots[table] = connection.readRows(legacyRowsQuery(entity))
                columns[table] = connection.readRows("SELECT * FROM pragma_table_info('$table') ORDER BY cid")
            }
            Fixture(schema, snapshots, columns, connection.readRows(legacyDdlQuery))
        }
    }

    private fun sentinel(table: String, field: JSONObject): SqlValue {
        val column = field.getString("columnName")
        if (!field.optBoolean("notNull") && column !in setOf("generation_id", "failure_payload", "oracle_provider", "oracle_id", "oracle_language")) return SqlValue.Null
        if (field.getString("affinity") == "INTEGER") return SqlValue.Integer(when {
            column in setOf("face_index", "ordinal", "ref_ordinal", "source_slot") -> 0
            column == "singleton_id" -> 1
            column.endsWith("_nano") -> 987_654_321
            else -> 5_000_000_037
        })
        return SqlValue.Text(when (column) {
            "generation_id" -> if (table.startsWith("rules")) "rules-old" else "catalog-old"
            "corpus_id" -> "rules-old"
            "provider", "oracle_provider", "printing_provider", "from_provider" -> "legacy-provider"
            "oracle_id" -> "oracle-old"
            "printing_id" -> "printing-old"
            "local_id", "from_local_id" -> "100.1"
            "language", "oracle_language" -> "en"
            "job_id" -> "job-old"
            "collector_number" -> "007a"
            else -> "sentinel-é-☃-\u0000-$table-$column"
        })
    }

    private suspend fun assertLegacyPreserved(fixture: Fixture, afterJobDelete: Boolean = false) {
        val entities = fixture.schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
            if (afterJobDelete && table in setOf("acquisition_job", "acquisition_artifact", "acquisition_seen_id")) continue
            assertEquals("All schema4 values in $table", fixture.rows.getValue(table), rows(legacyRowsQuery(entity)))
            assertEquals("All schema4 columns in $table", fixture.columns.getValue(table),
                rows("SELECT * FROM pragma_table_info('$table') ORDER BY cid"))
        }
        assertEquals("All schema4 DDL", fixture.ddl, rows(legacyDdlQuery))
        assertEquals(1L, scalar("SELECT count(*) FROM catalog_name_fts WHERE catalog_name_fts MATCH 'sentinel'"))
        assertEquals(1L, scalar("SELECT count(*) FROM rules_text_fts WHERE rules_text_fts MATCH 'sentinel'"))
        assertEquals(0L, scalar("SELECT count(*) FROM pragma_foreign_key_check"))
    }

    private suspend fun assertCooldownShape() {
        assertEquals(listOf(
            listOf(SqlValue.Text("origin"), SqlValue.Text("TEXT"), SqlValue.Integer(1), SqlValue.Integer(1)),
            listOf(SqlValue.Text("revision"), SqlValue.Text("INTEGER"), SqlValue.Integer(1), SqlValue.Integer(0)),
            listOf(SqlValue.Text("interval_seconds"), SqlValue.Text("INTEGER"), SqlValue.Integer(0), SqlValue.Integer(0)),
            listOf(SqlValue.Text("interval_nano"), SqlValue.Text("INTEGER"), SqlValue.Integer(0), SqlValue.Integer(0)),
        ), rows("SELECT name,type,\"notnull\",pk FROM pragma_table_info('acquisition_origin_cooldown') ORDER BY cid"))
        assertEquals(0L, scalar("SELECT count(*) FROM pragma_foreign_key_list('acquisition_origin_cooldown')"))
        assertEquals(1L, scalar("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='acquisition_origin_cooldown'"))
    }

    private suspend fun cooldownShape(db: StorageDatabase = database!!): List<List<SqlValue>> =
        rows("SELECT name,type,\"notnull\",pk,dflt_value FROM pragma_table_info('acquisition_origin_cooldown') ORDER BY cid", db)

    private fun legacyRowsQuery(entity: JSONObject): String {
        val fields = entity.getJSONArray("fields")
        val names = (0 until fields.length()).map { "`${fields.getJSONObject(it).getString("columnName")}`" }
        return "SELECT ${names.joinToString(",")} FROM `${entity.getString("tableName")}` ORDER BY rowid"
    }

    private suspend fun reopen() { database?.close(); database = success(StorageDatabase.open(context, fileName)) }
    private suspend fun write(sql: String) { success(database!!.transaction({ tx -> success(tx.execute(request(sql), token)) }, token)) }
    private suspend fun update(sql: String): Long = success(database!!.transaction({ tx -> success(tx.execute(request(sql), token)) }, token)).affectedRows
    private suspend fun rows(sql: String, db: StorageDatabase = database!!): List<List<SqlValue>> =
        success(db.query(request(sql), token)).map { it.columns.map { column -> column.value } }
    private suspend fun values(sql: String): List<SqlValue> = rows(sql).single()
    private suspend fun scalar(sql: String): Long = (values(sql).single() as SqlValue.Integer).value
    private fun request(sql: String) = SqlRequest(sql, SnapshotList.empty())
    private fun SQLiteConnection.execute(sql: String) { prepare(sql).use { it.step() } }
    private fun SQLiteConnection.readRows(sql: String): List<List<SqlValue>> = prepare(sql).use { statement ->
        buildList { while (statement.step()) add((0 until statement.getColumnCount()).map { i ->
            when (statement.getColumnType(i)) {
                1 -> SqlValue.Integer(statement.getLong(i))
                3 -> SqlValue.Text(statement.getText(i))
                5 -> SqlValue.Null
                else -> error("Unexpected fixture value")
            }
        }) }
    }
    private fun SQLiteStatement.bindValue(index: Int, value: SqlValue) = when (value) {
        is SqlValue.Text -> bindText(index, value.value)
        is SqlValue.Integer -> bindLong(index, value.value)
        SqlValue.Null -> bindNull(index)
        else -> error("Unexpected fixture value")
    }
    private fun <T> success(outcome: Outcome<T>): T {
        assertTrue("Expected success, got $outcome", outcome is Outcome.Success)
        return (outcome as Outcome.Success).value
    }
    private val legacyDdlQuery = "SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name != 'room_master_table' AND tbl_name != 'acquisition_origin_cooldown' AND tbl_name NOT IN ('life_game','life_event','life_seat_search','life_result','life_alert','life_profile') ORDER BY type,name"
}
