package com.architectpsg.commandlibrary.data.storage.schema

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Actual exported9/10 and host bundled SQLite; synthetic opaque blobs, not Room/device execution. */
class DeckLibraryMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    private fun sql(c: SQLiteConnection, q: String) { c.prepare(q).use { it.step() } }
    private fun number(c: SQLiteConnection, q: String): Long = c.prepare(q).use { assertTrue(it.step()); it.getLong(0) }
    private fun definitions(c: SQLiteConnection, oldOnly: Boolean = false): List<List<String?>> = c.prepare(
        "SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name!='room_master_table'" +
            (if (oldOnly) " AND tbl_name NOT IN ('deck_head','deck_revision','deck_operation')" else "") + " ORDER BY type,name",
    ).use { s -> buildList { while (s.step()) add((0..3).map { if (s.isNull(it)) null else s.getText(it) }) } }
    private fun schema(c: SQLiteConnection, version: Int) {
        val path = if (version == 9) "/search-projection-schema9.sql" else "/deck-library-schema10.sql"
        val ddl = requireNotNull(javaClass.getResourceAsStream(path)).bufferedReader().use { it.readText() }
        ddl.split(";\n").map(String::trim).filter(String::isNotEmpty).forEach { sql(c, it) }
        sql(c, "PRAGMA foreign_keys=ON"); sql(c, "PRAGMA user_version=$version")
    }
    private fun head(c: SQLiteConnection) = sql(c, "INSERT INTO deck_head VALUES('local-decks','d',0,'Deck',1,0,1,0,0,0)")
    private fun revision(c: SQLiteConnection) = sql(c, "INSERT INTO deck_revision VALUES('local-decks','d',0,'Deck',1,0,1,0,0,0,3,'opaque',X'0001FF')")
    private fun receipt(c: SQLiteConnection, op: String = "op") = sql(c, "INSERT INTO deck_operation(operation_id,request_version,request_sha256,kind,provider,deck_id,result_revision) VALUES('$op',1,'opaque','CREATE','local-decks','d',0)")

    @Test fun additiveMigrationMatchesFreshExportPreservesAllOldObjectsAndReopens() = runBlocking {
        val file = folder.newFile()
        BundledSQLiteDriver().open(file.path).use { c ->
            schema(c, 9); val old = definitions(c)
            sql(c, "INSERT INTO life_profile VALUES('profile',0,'É','é',X'0001FF',7,9)")
            sql(c, "BEGIN IMMEDIATE"); Migration9To10.migrate(c); sql(c, "PRAGMA user_version=10"); sql(c, "COMMIT")
            assertEquals(old, definitions(c, true))
            BundledSQLiteDriver().open(":memory:").use { fresh -> schema(fresh, 10); assertEquals(definitions(fresh), definitions(c)) }
            assertEquals(0L, number(c, "SELECT count(*) FROM deck_head"))
            assertEquals(0L, number(c, "SELECT coalesce(max(sequence),0) FROM deck_operation"))
            assertEquals(3L, number(c, "SELECT length(payload) FROM life_profile"))
            head(c); revision(c); receipt(c)
        }
        BundledSQLiteDriver().open(file.path).use { c ->
            sql(c, "PRAGMA foreign_keys=ON")
            assertEquals(10L, number(c, "PRAGMA user_version"))
            assertEquals(1L, number(c, "SELECT sequence FROM deck_operation"))
            assertEquals(3L, number(c, "SELECT length(payload) FROM deck_revision"))
            assertEquals(0L, number(c, "SELECT count(*) FROM pragma_foreign_key_check"))
        }
    }
    @Test fun resultForeignKeysAndUniqueOperationsProtectIndependentUserData() = runBlocking {
        BundledSQLiteDriver().open(":memory:").use { c ->
            schema(c, 9); Migration9To10.migrate(c)
            assertThrows(SQLiteException::class.java) { revision(c) }
            head(c)
            assertThrows(SQLiteException::class.java) { receipt(c) }
            revision(c); receipt(c)
            assertThrows(SQLiteException::class.java) { receipt(c) }
            assertThrows(SQLiteException::class.java) { sql(c, "DELETE FROM deck_revision") }
            assertThrows(SQLiteException::class.java) { sql(c, "DELETE FROM deck_head") }
            sql(c, "DELETE FROM catalog_generation"); sql(c, "DELETE FROM life_profile")
            assertEquals(1L, number(c, "SELECT count(*) FROM deck_head"))
            assertEquals(1L, number(c, "SELECT count(*) FROM deck_operation"))
            assertEquals(0L, number(c, "SELECT count(*) FROM pragma_foreign_key_check"))
        }
    }
    @Test fun failedMigrationAndInterruptedWritesRollBackWithoutSeedingReceipts() = runBlocking {
        BundledSQLiteDriver().open(":memory:").use { c ->
            schema(c, 9); val old = definitions(c)
            sql(c, "BEGIN IMMEDIATE"); Migration9To10.migrate(c); sql(c, "PRAGMA user_version=10"); head(c)
            assertThrows(SQLiteException::class.java) { receipt(c) }
            sql(c, "ROLLBACK")
            assertEquals(old, definitions(c)); assertEquals(9L, number(c, "PRAGMA user_version"))
            Migration9To10.migrate(c)
            for (writes in 1..3) {
                sql(c, "BEGIN IMMEDIATE"); head(c)
                if (writes >= 2) revision(c)
                if (writes >= 3) receipt(c)
                sql(c, "ROLLBACK")
                for (table in listOf("deck_head", "deck_revision", "deck_operation"))
                    assertEquals(table, 0L, number(c, "SELECT count(*) FROM $table"))
            }
        }
    }
}
