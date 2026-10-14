package com.architectpsg.commandlibrary.data.storage.schema

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real exported6 DDL and production migration. Synthetic opaque bytes are not valid life data. */
class RulingsMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    private fun sql(c: SQLiteConnection, text: String) { c.prepare(text).use { it.step() } }
    private fun number(c: SQLiteConnection, text: String): Long = c.prepare(text).use { assertTrue(it.step()); it.getLong(0) }
    private fun text(c: SQLiteConnection, query: String): String = c.prepare(query).use { assertTrue(it.step()); it.getText(0) }
    private fun old(file: File): SQLiteConnection = BundledSQLiteDriver().open(file.path).also { c ->
        sql(c, "PRAGMA foreign_keys=ON")
        val ddl = requireNotNull(javaClass.getResourceAsStream("/rulings-schema6.sql")).bufferedReader().use { it.readText() }
        ddl.split(";\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach { sql(c, it) }
        sql(c, "INSERT INTO acquisition_origin_cooldown VALUES ('synthetic://origin',19,600,17)")
        sql(c, "INSERT INTO life_game VALUES ('synthetic','game',8,'operation',X'010203','opaque',X'040506',13,'PAUSED',10,17,NULL,NULL,11,18)")
        sql(c, "INSERT INTO life_event VALUES ('synthetic','game',1,8,'group',11,18,NULL,X'070809')")
        sql(c, "INSERT INTO life_profile VALUES ('profile',3,'Synthetic','synthetic',X'0A0B',11,18)")
    }
    private fun migrate(c: SQLiteConnection) = runBlocking { Migration6To7.migrate(c) }
    private fun generation(c: SQLiteConnection, id: String) = sql(c, "INSERT INTO rulings_generation VALUES ('$id','scryfall','ACTIVE','RULINGS_LANGUAGE_NOT_ASSERTED','opaque',1,1)")
    private fun record(c: SQLiteConnection, generation: String, ordinal: Long) = sql(c, "INSERT INTO ruling_record VALUES ('$generation',$ordinal,'scryfall','unmatched-oracle','wotc','2026-10-14','opaque')")

    @Test fun additiveMigrationPreservesAllOldDdlAndOpaqueHistoryThroughReopen() {
        val file = folder.newFile("rulings.db")
        val oldDdl: String
        old(file).use { c ->
            oldDdl = text(c, "SELECT group_concat(sql,'|') FROM (SELECT sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name)")
            sql(c, "BEGIN IMMEDIATE"); migrate(c); sql(c, "PRAGMA user_version=7"); sql(c, "COMMIT")
            val retained = text(c, "SELECT group_concat(sql,'|') FROM (SELECT sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name NOT LIKE 'ruling%' AND name NOT LIKE 'index_ruling%' ORDER BY name)")
            assertEquals(oldDdl, retained)
            generation(c, "ruling-only"); record(c, "ruling-only", 1); sql(c, "INSERT INTO rulings_active VALUES (1,'ruling-only')")
            assertEquals(0L, number(c, "SELECT count(*) FROM catalog_generation"))
            assertEquals(0L, number(c, "SELECT count(*) FROM pragma_foreign_key_check"))
        }
        BundledSQLiteDriver().open(file.path).use { c ->
            assertEquals(7L, number(c, "PRAGMA user_version"))
            assertEquals(8L, number(c, "SELECT revision FROM life_game"))
            assertEquals(13L, number(c, "SELECT checkpoint_sequence FROM life_game"))
            assertEquals("040506", text(c, "SELECT hex(checkpoint_payload) FROM life_game"))
            assertEquals("010203", text(c, "SELECT hex(creation_payload) FROM life_game"))
            assertEquals("070809", text(c, "SELECT hex(archive_payload) FROM life_event"))
            assertEquals("0A0B", text(c, "SELECT hex(payload) FROM life_profile"))
            assertEquals(19L, number(c, "SELECT revision FROM acquisition_origin_cooldown"))
            assertEquals(1L, number(c, "SELECT count(*) FROM ruling_record"))
        }
    }

    @Test fun activeFkProtectsGenerationAndRowIdentityAllowsDuplicateContent() {
        old(folder.newFile("fk.db")).use { c ->
            migrate(c); generation(c, "old"); generation(c, "new")
            record(c, "old", 1); record(c, "old", 2)
            assertThrows(SQLiteException::class.java) { record(c, "old", 1) }
            assertThrows(SQLiteException::class.java) { record(c, "missing", 1) }
            sql(c, "INSERT INTO rulings_active VALUES (1,'old')")
            assertThrows(SQLiteException::class.java) { sql(c, "DELETE FROM rulings_generation WHERE generation_id='old'") }
            assertEquals(2L, number(c, "SELECT count(*) FROM ruling_record"))
            sql(c, "BEGIN IMMEDIATE"); sql(c, "UPDATE rulings_active SET generation_id='new'")
            sql(c, "DELETE FROM rulings_generation WHERE generation_id='old'"); sql(c, "COMMIT")
            assertEquals(0L, number(c, "SELECT count(*) FROM ruling_record"))
            assertEquals(1L, number(c, "SELECT count(*) FROM life_game"))
        }
    }

    @Test fun failedMigrationAndPointerSwitchRollbackWithoutChangingOldHistory() {
        old(folder.newFile("rollback.db")).use { c ->
            sql(c, "BEGIN IMMEDIATE"); migrate(c); sql(c, "PRAGMA user_version=7")
            assertThrows(SQLiteException::class.java) { record(c, "missing", 1) }; sql(c, "ROLLBACK")
            assertEquals(6L, number(c, "PRAGMA user_version"))
            assertEquals(0L, number(c, "SELECT count(*) FROM sqlite_master WHERE name LIKE 'ruling%'"))
            migrate(c); generation(c, "old"); generation(c, "new"); record(c, "old", 1)
            sql(c, "INSERT INTO rulings_active VALUES (1,'old')")
            sql(c, "BEGIN IMMEDIATE"); sql(c, "UPDATE rulings_active SET generation_id='new'")
            sql(c, "DELETE FROM rulings_generation WHERE generation_id='old'"); sql(c, "ROLLBACK")
            assertEquals("old", text(c, "SELECT generation_id FROM rulings_active"))
            assertEquals(1L, number(c, "SELECT count(*) FROM ruling_record"))
            assertEquals("070809", text(c, "SELECT hex(archive_payload) FROM life_event"))
        }
    }

    @Test fun mixedOrderIndexSupportsOraclePageWithoutTemporarySort() {
        old(folder.newFile("plan.db")).use { c ->
            migrate(c); generation(c, "generation"); record(c, "generation", 1)
            val plan = buildList { c.prepare("EXPLAIN QUERY PLAN SELECT ordinal,published_day FROM ruling_record WHERE generation_id='generation' AND oracle_provider='scryfall' AND oracle_id='unmatched-oracle' AND (published_day<'2026-10-15' OR (published_day='2026-10-15' AND ordinal>1)) ORDER BY published_day DESC,ordinal ASC LIMIT 21").use { s -> while(s.step()) add(s.getText(3)) } }
            assertTrue(plan.any { it.contains("index_ruling_record_oracle_day_ordinal") })
            assertFalse(plan.any { it.contains("TEMP B-TREE") })
        }
    }
}
