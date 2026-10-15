package com.architectpsg.commandlibrary.data.storage.schema

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Actual host SQLite and KSP10/11 exports; opaque synthetic blobs, not codec/device proof. */
class DeckUndoMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    private fun sql(c: SQLiteConnection, q: String) { c.prepare(q).use { it.step() } }
    private fun number(c: SQLiteConnection, q: String): Long = c.prepare(q).use { assertTrue(it.step()); it.getLong(0) }
    private fun definitions(c: SQLiteConnection, oldOnly: Boolean = false): List<List<String?>> = c.prepare(
        "SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name!='room_master_table'" +
            (if (oldOnly) " AND tbl_name NOT IN ('deck_undo_link','deck_undo_state')" else "") + " ORDER BY type,name",
    ).use { s -> buildList { while (s.step()) add((0..3).map { if (s.isNull(it)) null else s.getText(it) }) } }
    private fun schema(c: SQLiteConnection, version: Int) {
        val path = if (version == 10) "/deck-library-schema10.sql" else "/deck-edits-schema11.sql"
        val ddl = requireNotNull(javaClass.getResourceAsStream(path)).bufferedReader().use { it.readText() }
        ddl.split(";\n").map(String::trim).filter(String::isNotEmpty).forEach { sql(c, it) }
        sql(c, "PRAGMA foreign_keys=ON"); sql(c, "PRAGMA user_version=$version")
    }
    private fun initial(c: SQLiteConnection) {
        sql(c, "INSERT INTO deck_head VALUES('local-decks','d',0,'Deck',1,0,1,0,0,0)")
        revision(c, 0)
        sql(c, "INSERT INTO deck_operation(operation_id,request_version,request_sha256,kind,provider,deck_id,result_revision) VALUES('op',1,'opaque','CREATE','local-decks','d',0)")
    }
    private fun revision(c: SQLiteConnection, revision: Int) = sql(c, "INSERT INTO deck_revision VALUES('local-decks','d',$revision,'Deck',1,0,1,0,0,0,3,'opaque',X'0001FF')")
    @Test fun actualExportMigrationPreservesEveryOldObjectAndBytesAndReopens() = runBlocking {
        val file = folder.newFile()
        BundledSQLiteDriver().open(file.path).use { c ->
            schema(c, 10); initial(c); val old = definitions(c)
            sql(c, "BEGIN IMMEDIATE"); Migration10To11.migrate(c); sql(c, "PRAGMA user_version=11"); sql(c, "COMMIT")
            assertEquals(old, definitions(c, true))
            BundledSQLiteDriver().open(":memory:").use { fresh -> schema(fresh, 11); assertEquals(definitions(fresh), definitions(c)) }
            assertEquals(0L, number(c, "SELECT count(*) FROM deck_undo_link")); assertEquals(0L, number(c, "SELECT count(*) FROM deck_undo_state"))
            assertEquals(1L, number(c, "SELECT sequence FROM deck_operation WHERE operation_id='op'"))
        }
        BundledSQLiteDriver().open(file.path).use { c ->
            sql(c, "PRAGMA foreign_keys=ON"); assertEquals(11L, number(c, "PRAGMA user_version"))
            c.prepare("SELECT hex(payload) FROM deck_revision").use { assertTrue(it.step()); assertEquals("0001FF", it.getText(0)) }
            assertEquals(0L, number(c, "SELECT count(*) FROM pragma_foreign_key_check"))
        }
    }
    @Test fun compositeForeignKeysRejectMissingAndCrossDeckUndoTargetsWithoutDeletingUserData() = runBlocking {
        BundledSQLiteDriver().open(":memory:").use { c ->
            schema(c, 10); initial(c); Migration10To11.migrate(c)
            assertThrows(SQLiteException::class.java) { sql(c, "INSERT INTO deck_undo_link VALUES('local-decks','d',1,0,NULL)") }
            revision(c, 1); sql(c, "INSERT INTO deck_undo_link VALUES('local-decks','d',1,0,NULL)")
            sql(c, "INSERT INTO deck_head VALUES('local-decks','other',1,'Other',1,0,1,0,0,0)")
            sql(c, "INSERT INTO deck_revision VALUES('local-decks','other',1,'Other',1,0,1,0,0,0,3,'opaque',X'0001FF')")
            assertThrows(SQLiteException::class.java) { sql(c, "INSERT INTO deck_undo_state VALUES('local-decks','other',1,1)") }
            assertThrows(SQLiteException::class.java) { sql(c, "INSERT INTO deck_undo_state VALUES('local-decks','d',1,2)") }
            sql(c, "INSERT INTO deck_undo_state VALUES('local-decks','d',1,1)")
            assertThrows(SQLiteException::class.java) { sql(c, "DELETE FROM deck_undo_link") }
            assertThrows(SQLiteException::class.java) { sql(c, "DELETE FROM deck_revision WHERE revision=1") }
            assertEquals(0L, number(c, "SELECT count(*) FROM pragma_foreign_key_check"))
        }
    }
    @Test fun migrationAndPartialUndoRowsRollBackAtomicallyWithoutSyntheticState() = runBlocking {
        BundledSQLiteDriver().open(":memory:").use { c ->
            schema(c, 10); initial(c); val old = definitions(c)
            sql(c, "BEGIN IMMEDIATE"); Migration10To11.migrate(c); sql(c, "PRAGMA user_version=11")
            assertThrows(SQLiteException::class.java) { sql(c, "INSERT INTO deck_undo_state VALUES('local-decks','d',1,1)") }
            sql(c, "ROLLBACK"); assertEquals(old, definitions(c)); assertEquals(10L, number(c, "PRAGMA user_version"))
            Migration10To11.migrate(c)
            for (writes in 1..3) {
                sql(c, "BEGIN IMMEDIATE"); revision(c, 1)
                if (writes >= 2) sql(c, "INSERT INTO deck_undo_link VALUES('local-decks','d',1,0,NULL)")
                if (writes >= 3) sql(c, "INSERT INTO deck_undo_state VALUES('local-decks','d',1,1)")
                sql(c, "ROLLBACK")
                assertEquals(1L, number(c, "SELECT count(*) FROM deck_revision"))
                assertEquals(0L, number(c, "SELECT count(*) FROM deck_undo_link")); assertEquals(0L, number(c, "SELECT count(*) FROM deck_undo_state"))
                assertEquals(1L, number(c, "SELECT count(*) FROM deck_operation"))
            }
        }
    }
}
