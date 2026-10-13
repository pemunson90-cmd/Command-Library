package com.architectpsg.commandlibrary.data.storage

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.schema.Migration3To4
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic sentinels in the committed real KSP schema3, including all acquisition tables. */
@RunWith(AndroidJUnit4::class)
class FullCatalogMigrationTest {
    private lateinit var context: Context
    private lateinit var fileName: String
    private var database: StorageDatabase? = null
    private val token = object : CancelToken {
        override val isCancelled: Boolean get() = false
        override fun throwIfCancelled() = Unit
    }

    @Before fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        fileName = "full-catalog-migration-${UUID.randomUUID()}.db"
    }
    @After fun close() { database?.close(); context.deleteDatabase(fileName) }

    @Test fun actualV3PreservesEveryLegacyColumnAndBackfillsOnlyWholeRelations() = runBlocking {
        val fixture = createV3()
        reopen()
        assertEquals(5, success(database!!.capabilities(token)).schemaVersion)
        assertLegacyPreserved(fixture)
        assertEquals(listOf(SqlValue.Text("USER_CONTENT_URI"), SqlValue.Null, SqlValue.Null), values(
            "SELECT input_kind,artifact_job_id,source_payload FROM catalog_generation",
        ))
        assertEquals(listOf(SqlValue.Text("USER_CONTENT_URI"), SqlValue.Null), values(
            "SELECT input_kind,artifact_job_id FROM rules_corpus",
        ))
        assertEquals(listOf(SqlValue.Text("catalog-old"), SqlValue.Text("legacy-provider"), SqlValue.Text("printing-old"),
            SqlValue.Integer(-1), SqlValue.Text("WHOLE"), SqlValue.Text("legacy-provider"), SqlValue.Text("oracle-old"), SqlValue.Text("en")),
            values("SELECT * FROM printing_oracle_membership"))
        assertEquals(0L, scalar("SELECT count(*) FROM printing_oracle_membership WHERE printing_id='printing-only'"))
        for (table in listOf("catalog_generation", "rules_corpus")) {
            assertEquals(listOf(SqlValue.Text("TEXT"), SqlValue.Integer(1), SqlValue.Text("'USER_CONTENT_URI'")), values(
                "SELECT type,\"notnull\",dflt_value FROM pragma_table_info('$table') WHERE name='input_kind'",
            ))
            assertEquals(0L, scalar("SELECT count(*) FROM pragma_foreign_key_list('$table') WHERE \"table\"='acquisition_job'"))
        }
        val source = fixture.rows.getValue("rules_corpus").single()[6] as SqlValue.Text
        assertEquals(listOf(SqlValue.Blob(SnapshotBytes(source.value.toByteArray(Charsets.UTF_8)))), values(
            "SELECT CAST(source_payload AS BLOB) FROM rules_corpus",
        ))
        reopen()
        assertLegacyPreserved(fixture)
        assertEquals(1L, scalar("SELECT count(*) FROM printing_oracle_membership"))
        assertEquals(listOf(SqlValue.Text("ok")), values("SELECT integrity_check FROM pragma_integrity_check"))
    }

    @Test fun everyPartialLegacyOracleTripleAbortsWithoutPublishingSchema4AndCanRetry() = runBlocking {
        // A composite SQLite FK accepts these six partial-null patterns; migration must reject all.
        for (mask in 1..6) {
            database?.close(); database = null
            context.deleteDatabase(fileName)
            val fixture = createV3(mask)
            repeat(2) {
                val refused = StorageDatabase.open(context, fileName)
                assertTrue("Partial triple $mask must fail migration, got $refused", refused is Outcome.Failure)
                BundledSQLiteDriver().open(context.getDatabasePath(fileName).absolutePath).use { connection ->
                    assertV3Unchanged(connection, fixture)
                }
            }
            // Correct this synthetic fixture from its already-present canonical identity, then retry.
            BundledSQLiteDriver().open(context.getDatabasePath(fileName).absolutePath).use { connection ->
                connection.execute("UPDATE printing SET oracle_provider='legacy-provider',oracle_id='oracle-old',oracle_language='en' WHERE printing_id='printing-old'")
            }
            reopen()
            assertEquals(5, success(database!!.capabilities(token)).schemaVersion)
            assertEquals(listOf(SqlValue.Integer(-1), SqlValue.Text("WHOLE")), values(
                "SELECT source_slot,source_kind FROM printing_oracle_membership",
            ))
            assertEquals(0L, scalar("SELECT count(*) FROM pragma_foreign_key_check"))
        }
    }

    @Test fun interruptedColumnAddsRollBackAndActualOpenRetries() = runBlocking {
        val fixture = createV3()
        val interruption = CancellationException("Synthetic interruption after two column additions")
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
                Migration3To4.migrate(interrupted)
                fail("Expected synthetic interruption")
            } catch (cancelled: CancellationException) {
                assertSame(interruption, cancelled)
                connection.execute("ROLLBACK")
            }
            assertV3Unchanged(connection, fixture)
        }
        reopen()
        assertEquals(5, success(database!!.capabilities(token)).schemaVersion)
        assertLegacyPreserved(fixture)
    }

    @Test fun danglingCompleteLegacyRelationAbortsBeforeForeignKeysAreEnabled() = runBlocking {
        val fixture = createV3()
        val invalid = BundledSQLiteDriver().open(context.getDatabasePath(fileName).absolutePath).use { connection ->
            connection.execute("PRAGMA foreign_keys=OFF")
            connection.execute("UPDATE printing SET oracle_id='missing-target' WHERE printing_id='printing-old'")
            fixture.copy(rows = fixture.rows + ("printing" to connection.readRows("SELECT * FROM printing ORDER BY rowid")))
        }
        // Room enables FKs at onOpen, after migration. Validation cannot rely on an INSERT FK.
        val refused = StorageDatabase.open(context, fileName)
        assertTrue("Dangling complete triple must fail migration, got $refused", refused is Outcome.Failure)
        BundledSQLiteDriver().open(context.getDatabasePath(fileName).absolutePath).use { connection ->
            assertV3Unchanged(connection, invalid)
            connection.execute("UPDATE printing SET oracle_id='oracle-old' WHERE printing_id='printing-old'")
        }
        reopen()
        assertEquals(5, success(database!!.capabilities(token)).schemaVersion)
        assertLegacyPreserved(fixture)
        assertEquals(1L, scalar("SELECT count(*) FROM printing_oracle_membership"))
    }

    @Test fun lateRepeatedFaceTargetsKeepSourceOrdinalsAndSourceQualifiedForeignKeys() = runBlocking {
        createV3()
        reopen()
        // Printing-only metadata arrives before its canonical targets; resolution happens afterward.
        write(copyPrinting("face-print"))
        assertConstraintFailure(membership("face-print", 0, "legacy-provider", "later-target"))
        write(
            copyOracle("legacy-provider", "later-target"),
            copyOracle("another-provider", "later-target"),
            "INSERT INTO card_face(generation_id,provider,oracle_id,language,face_index,name,face_payload) VALUES ('catalog-old','legacy-provider','later-target','en',0,'canonical face','canonical-face-payload')",
            "INSERT INTO printing_face_asset(generation_id,provider,printing_id,face_index,language,asset_payload) VALUES ('catalog-old','legacy-provider','face-print',7,'en','original-printing-face-seven')",
            membership("face-print", 0, "legacy-provider", "later-target"),
            membership("face-print", 1, "legacy-provider", "later-target"),
            membership("face-print", 7, "another-provider", "later-target"),
        )
        assertEquals(listOf(listOf(SqlValue.Integer(0), SqlValue.Text("FACE"), SqlValue.Text("legacy-provider")),
            listOf(SqlValue.Integer(1), SqlValue.Text("FACE"), SqlValue.Text("legacy-provider")),
            listOf(SqlValue.Integer(7), SqlValue.Text("FACE"), SqlValue.Text("another-provider"))), rows(
            "SELECT source_slot,source_kind,oracle_provider FROM printing_oracle_membership WHERE printing_id='face-print' ORDER BY source_slot",
        ))
        assertEquals(listOf(SqlValue.Text("legacy-provider"), SqlValue.Text("face-print")), values(
            "SELECT DISTINCT printing_provider,printing_id FROM printing_oracle_membership WHERE generation_id='catalog-old' AND oracle_provider='legacy-provider' AND oracle_id='later-target' AND oracle_language='en'",
        ))
        assertConstraintFailure(membership("face-print", 0, "another-provider", "later-target"))
        assertConstraintFailure(membership("missing-print", 0, "legacy-provider", "later-target"))
        assertConstraintFailure(membership("face-print", 2, "legacy-provider", "later-target", printingProvider = "wrong-printing-provider"))
        assertConstraintFailure(membership("face-print", 2, "legacy-provider", "later-target", generation = "wrong-generation"))
        assertConstraintFailure(membership("face-print", 2, "wrong-provider", "later-target"))
        assertConstraintFailure(membership("face-print", 2, "legacy-provider", "missing-oracle"))
        assertConstraintFailure(membership("face-print", 2, "legacy-provider", "later-target", "fr"))
        assertConstraintFailure("DELETE FROM oracle_card WHERE generation_id='catalog-old' AND oracle_id='later-target'")
        assertEquals(listOf("generation_id", "oracle_provider", "oracle_id", "oracle_language", "printing_provider", "printing_id").map {
            listOf(SqlValue.Text(it))
        }, rows("SELECT name FROM pragma_index_info('index_printing_oracle_membership_lookup') ORDER BY seqno"))
        assertEquals(0L, scalar("SELECT \"unique\" FROM pragma_index_list('printing_oracle_membership') WHERE name='index_printing_oracle_membership_lookup'"))
        val fk = rows("SELECT \"table\",\"from\",\"to\",on_update,on_delete FROM pragma_foreign_key_list('printing_oracle_membership') ORDER BY \"table\",seq")
        val expected = listOf(
            Triple("oracle_card", listOf("generation_id", "oracle_provider", "oracle_id", "oracle_language"), listOf("generation_id", "provider", "oracle_id", "language")),
            Triple("printing", listOf("generation_id", "printing_provider", "printing_id"), listOf("generation_id", "provider", "printing_id")),
        ).flatMap { (table, child, parent) -> child.indices.map { i ->
            listOf(SqlValue.Text(table), SqlValue.Text(child[i]), SqlValue.Text(parent[i]), SqlValue.Text("NO ACTION"),
                SqlValue.Text(if (table == "printing") "CASCADE" else "NO ACTION"))
        } }
        assertEquals(expected, fk)
        // Installed provenance names its job without a lifecycle FK into acquisition infrastructure.
        write(
            "UPDATE catalog_generation SET input_kind='VERIFIED_ARTIFACT',artifact_job_id='job-old',source_payload='catalog-source-v2-é'",
            "UPDATE rules_corpus SET input_kind='VERIFIED_ARTIFACT',artifact_job_id='job-old'",
        )
        val installedCatalog = rows("SELECT * FROM catalog_generation")
        val installedRules = rows("SELECT * FROM rules_corpus")
        val catalogContent = rows("SELECT * FROM oracle_card ORDER BY provider,oracle_id")
        val ruleContent = rows("SELECT * FROM rule_entry")
        val memberships = rows("SELECT * FROM printing_oracle_membership ORDER BY printing_id,source_slot")
        write("DELETE FROM acquisition_job WHERE job_id='job-old'")
        reopen()
        assertEquals(0L, scalar("SELECT count(*) FROM acquisition_artifact"))
        assertEquals(0L, scalar("SELECT count(*) FROM acquisition_seen_id"))
        assertEquals(installedCatalog, rows("SELECT * FROM catalog_generation"))
        assertEquals(installedRules, rows("SELECT * FROM rules_corpus"))
        assertEquals(catalogContent, rows("SELECT * FROM oracle_card ORDER BY provider,oracle_id"))
        assertEquals(ruleContent, rows("SELECT * FROM rule_entry"))
        assertEquals(memberships, rows("SELECT * FROM printing_oracle_membership ORDER BY printing_id,source_slot"))
        assertEquals(listOf(SqlValue.Text("catalog-old")), values("SELECT generation_id FROM catalog_active"))
        assertEquals(listOf(SqlValue.Text("rules-old")), values("SELECT generation_id FROM rules_active"))
        write("DELETE FROM printing WHERE generation_id='catalog-old' AND provider='legacy-provider' AND printing_id='face-print'")
        assertEquals(0L, scalar("SELECT count(*) FROM printing_oracle_membership WHERE printing_id='face-print'"))
        assertEquals(0L, scalar("SELECT count(*) FROM printing_face_asset WHERE printing_id='face-print'"))
        assertEquals(1L, scalar("SELECT count(*) FROM printing_oracle_membership WHERE printing_id='printing-old'"))
        write("DELETE FROM oracle_card WHERE generation_id='catalog-old' AND oracle_id='later-target'")
        assertEquals(0L, scalar("SELECT count(*) FROM pragma_foreign_key_check"))
    }

    private data class Fixture(val schema: JSONObject, val rows: Map<String, List<List<SqlValue>>>,
        val ddl: List<List<SqlValue>>, val columns: Map<String, List<List<SqlValue>>>)

    private fun createV3(partialMask: Int? = null): Fixture {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val path = "com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase/3.json"
        val schema = assets.open(path).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        assertEquals(3, schema.getInt("version"))
        val entities = schema.getJSONArray("entities")
        assertEquals(16, entities.length())
        val file = context.getDatabasePath(fileName); file.parentFile!!.mkdirs()
        return BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            connection.execute("PRAGMA foreign_keys=ON"); connection.execute("BEGIN IMMEDIATE")
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
                connection.execute(copyPrinting("printing-only"))
                if (partialMask != null) {
                    val names = listOf("oracle_provider", "oracle_id", "oracle_language")
                    val nulls = names.filterIndexed { i, _ -> partialMask and (1 shl i) != 0 }.joinToString(",") { "$it=NULL" }
                    connection.execute("UPDATE printing SET $nulls WHERE printing_id='printing-old'")
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) connection.execute(setup.getString(i))
                connection.execute("PRAGMA user_version=3"); connection.execute("COMMIT")
            } catch (failure: Throwable) { connection.execute("ROLLBACK"); throw failure }
            val snapshots = linkedMapOf<String, List<List<SqlValue>>>()
            val columns = linkedMapOf<String, List<List<SqlValue>>>()
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                snapshots[table] = connection.readRows(legacyRowsQuery(entity))
                columns[table] = connection.readRows("SELECT * FROM pragma_table_info('$table') ORDER BY cid")
            }
            Fixture(schema, snapshots, connection.readRows(legacyDdlQuery), columns)
        }
    }

    private fun sentinel(table: String, field: JSONObject): SqlValue {
        val column = field.getString("columnName")
        if (!field.optBoolean("notNull") && column !in setOf("generation_id", "failure_payload", "oracle_provider", "oracle_id", "oracle_language")) return SqlValue.Null
        if (field.getString("affinity") == "INTEGER") return SqlValue.Integer(when {
            column in setOf("face_index", "ordinal", "ref_ordinal") -> 0
            column == "singleton_id" -> 1
            column.endsWith("_nano") -> 987_654_321
            else -> 5_000_000_037
        })
        return SqlValue.Text(when (column) {
            "generation_id" -> if (table.startsWith("rules")) "rules-old" else "catalog-old"
            "corpus_id" -> "rules-old"
            "provider", "oracle_provider", "from_provider" -> "legacy-provider"
            "oracle_id" -> "oracle-old"
            "printing_id" -> "printing-old"
            "local_id", "from_local_id" -> "100.1"
            "language", "oracle_language" -> "en"
            "job_id" -> "job-old"
            "collector_number" -> "007a"
            else -> "sentinel-é-☃-\u0000-$table-$column"
        })
    }
    private suspend fun assertLegacyPreserved(fixture: Fixture) {
        val entities = fixture.schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
            assertEquals("Every schema3 value in $table", fixture.rows.getValue(table), rows(legacyRowsQuery(entity)))
            val original = fixture.columns.getValue(table)
            assertEquals("Every original column definition in $table", original,
                rows("SELECT * FROM pragma_table_info('$table') ORDER BY cid").take(original.size))
        }
        // ALTER TABLE changes only these two CREATE TABLE statements; preserve all other DDL exactly.
        fun unchanged(ddl: List<List<SqlValue>>) = ddl.filterNot {
            it[0] == SqlValue.Text("table") && it[1] in listOf(SqlValue.Text("catalog_generation"), SqlValue.Text("rules_corpus"))
        }
        assertEquals(unchanged(fixture.ddl), unchanged(rows(legacyDdlQuery)))
        assertEquals(0L, scalar("SELECT count(*) FROM pragma_foreign_key_check"))
        for (table in listOf("catalog_name_fts", "rules_text_fts")) {
            assertEquals(1L, scalar("SELECT count(*) FROM $table WHERE $table MATCH 'sentinel'"))
        }
    }
    private fun assertV3Unchanged(connection: SQLiteConnection, fixture: Fixture) {
        assertEquals(listOf(listOf(SqlValue.Integer(3))), connection.readRows("PRAGMA user_version"))
        assertEquals(listOf(listOf(SqlValue.Text(fixture.schema.getString("identityHash")))), connection.readRows("SELECT identity_hash FROM room_master_table WHERE id=42"))
        assertEquals(fixture.ddl, connection.readRows(legacyDdlQuery))
        assertEquals(listOf(listOf(SqlValue.Integer(0))), connection.readRows("SELECT count(*) FROM sqlite_master WHERE name='printing_oracle_membership'"))
        val entities = fixture.schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
            assertEquals(fixture.rows.getValue(table), connection.readRows(legacyRowsQuery(entity)))
            assertEquals(fixture.columns.getValue(table), connection.readRows("SELECT * FROM pragma_table_info('$table') ORDER BY cid"))
        }
    }
    private fun legacyRowsQuery(entity: JSONObject): String {
        val fields = entity.getJSONArray("fields")
        val names = (0 until fields.length()).map { "`${fields.getJSONObject(it).getString("columnName")}`" }
        return "SELECT ${names.joinToString(",")} FROM `${entity.getString("tableName")}` ORDER BY rowid"
    }
    private fun copyPrinting(id: String) = "INSERT INTO printing SELECT generation_id,provider,'$id',NULL,NULL,NULL,set_code,set_name,collector_number,language,rarity,finishes,image_uri,metadata_payload FROM printing WHERE printing_id='printing-old'"
    private fun copyOracle(provider: String, id: String) = "INSERT INTO oracle_card SELECT generation_id,'$provider','$id',language,name,original_text,normalized_name,normalized_search_text,canonical_digest,record_payload FROM oracle_card WHERE provider='legacy-provider' AND oracle_id='oracle-old'"
    private fun membership(printing: String, slot: Int, provider: String, oracle: String, language: String = "en",
        printingProvider: String = "legacy-provider", generation: String = "catalog-old") =
        "INSERT INTO printing_oracle_membership(generation_id,printing_provider,printing_id,source_slot,source_kind,oracle_provider,oracle_id,oracle_language) VALUES ('$generation','$printingProvider','$printing',$slot,'FACE','$provider','$oracle','$language')"
    private suspend fun reopen() { database?.close(); database = success(StorageDatabase.open(context, fileName)) }
    private suspend fun write(vararg statements: String) {
        success(database!!.transaction({ tx -> for (sql in statements) success(tx.execute(request(sql), token)) }, token))
    }
    private suspend fun assertConstraintFailure(sql: String) {
        val outcome = database!!.transaction({ tx -> tx.execute(request(sql), token) }, token)
        assertEquals(FailureCode.PERSISTENCE, (outcome as Outcome.Failure).error.code)
    }
    private suspend fun rows(sql: String): List<List<SqlValue>> = success(database!!.query(request(sql), token)).map { it.columns.map { c -> c.value } }
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
    private val legacyDdlQuery = "SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name != 'room_master_table' AND name NOT LIKE '%printing_oracle_membership%' ORDER BY type,name"
}
