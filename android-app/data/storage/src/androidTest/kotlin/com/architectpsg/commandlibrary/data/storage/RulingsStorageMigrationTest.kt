package com.architectpsg.commandlibrary.data.storage

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.schema.StorageSchema
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Room open of exported6 identity/DDL. Compilation is not device execution evidence. */
@RunWith(AndroidJUnit4::class)
class RulingsStorageMigrationTest {
    private val cancel = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private fun <T> ok(value: Outcome<T>): T { assertTrue("Expected success, got $value", value is Outcome.Success); return (value as Outcome.Success).value }
    private suspend fun rows(db: StorageDatabase, sql: String) = ok(db.query(SqlRequest(sql, SnapshotList.empty(), 100), cancel))
    private suspend fun write(db: StorageDatabase, sql: String) = ok(db.transaction({ tx -> ok(tx.execute(SqlRequest(sql, SnapshotList.empty(), 1), cancel)) }, cancel))

    @Test fun exported6HistorySurvivesActualRoomMigrationIndependentRulingsAndReopen() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context: Context = instrumentation.targetContext
        val name = "rulings-migration-${UUID.randomUUID()}.db"
        var database: StorageDatabase? = null
        try {
            val path = context.getDatabasePath(name); requireNotNull(path.parentFile).mkdirs()
            val schema = JSONObject(instrumentation.context.assets.open("com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase/6.json").bufferedReader().use { it.readText() }).getJSONObject("database")
            BundledSQLiteDriver().open(path.path).use { c ->
                fun exec(sql: String) { c.prepare(sql).use { it.step() } }
                exec("PRAGMA foreign_keys=ON")
                val entities = schema.getJSONArray("entities")
                for(i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                    exec(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices")
                    if(indices != null) for(j in 0 until indices.length()) exec(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
                val setup = schema.getJSONArray("setupQueries")
                for(i in 0 until setup.length()) exec(setup.getString(i))
                exec("PRAGMA user_version=6")
                exec("INSERT INTO acquisition_origin_cooldown VALUES ('synthetic://origin',19,600,17)")
                exec("INSERT INTO life_game VALUES ('synthetic','game',8,'operation',X'010203','opaque',X'040506',13,'PAUSED',10,17,NULL,NULL,11,18)")
                exec("INSERT INTO life_event VALUES ('synthetic','game',1,8,'group',11,18,NULL,X'070809')")
                exec("INSERT INTO life_profile VALUES ('profile',3,'Synthetic','synthetic',X'0A0B',11,18)")
            }
            val first = ok(StorageDatabase.open(context, name)); database = first
            assertEquals(StorageSchema.VERSION, ok(first.capabilities(cancel)).schemaVersion)
            assertEquals(SqlValue.Text("040506"), rows(first, "SELECT hex(checkpoint_payload) AS bytes FROM life_game").single().columns.single().value)
            assertEquals(SqlValue.Text("070809"), rows(first, "SELECT hex(archive_payload) AS bytes FROM life_event").single().columns.single().value)
            write(first, "INSERT INTO rulings_generation VALUES ('rulings-only','scryfall','ACTIVE','RULINGS_LANGUAGE_NOT_ASSERTED','synthetic-opaque-not-reader-data',1,1)")
            write(first, "INSERT INTO ruling_record VALUES ('rulings-only',1,'scryfall','unmatched-oracle','wotc','2026-10-14','synthetic-opaque-not-reader-data')")
            write(first, "INSERT INTO rulings_active VALUES (1,'rulings-only')")
            val blocked = first.transaction({ tx -> tx.execute(SqlRequest("DELETE FROM rulings_generation", SnapshotList.empty(), 1), cancel) }, cancel)
            assertTrue(blocked is Outcome.Failure)
            val expected = rows(first, "SELECT generation_id,ordinal,record_payload FROM ruling_record")
            first.close(); database = null
            val reopened = ok(StorageDatabase.open(context, name)); database = reopened
            assertEquals(expected, rows(reopened, "SELECT generation_id,ordinal,record_payload FROM ruling_record"))
            assertEquals(SqlValue.Text("0A0B"), rows(reopened, "SELECT hex(payload) AS bytes FROM life_profile").single().columns.single().value)
            assertEquals(SqlValue.Integer(19), rows(reopened, "SELECT revision FROM acquisition_origin_cooldown").single().columns.single().value)
            assertEquals(SqlValue.Integer(0), rows(reopened, "SELECT count(*) AS n FROM catalog_active").single().columns.single().value)
            assertTrue(rows(reopened, "SELECT * FROM pragma_foreign_key_check").isEmpty())
        } finally { database?.close(); context.deleteDatabase(name) }
    }
}
