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

/** Actual exported7 DDL. Opaque synthetic payloads prove byte preservation, not valid source data. */
class CatalogSourceReceiptMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    private fun sql(c: SQLiteConnection, text: String) { c.prepare(text).use { it.step() } }
    private fun number(c: SQLiteConnection, text: String): Long = c.prepare(text).use { assertTrue(it.step()); it.getLong(0) }
    private fun text(c: SQLiteConnection, query: String): String = c.prepare(query).use { assertTrue(it.step()); it.getText(0) }
    private fun old(file: File): SQLiteConnection = BundledSQLiteDriver().open(file.path).also { c ->
        sql(c, "PRAGMA foreign_keys=ON")
        val ddl = requireNotNull(javaClass.getResourceAsStream("/source-receipt-schema7.sql")).bufferedReader().use { it.readText() }
        ddl.split(";\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach { sql(c, it) }
        sql(c, "INSERT INTO acquisition_origin_cooldown VALUES ('synthetic://origin',19,600,17)")
        sql(c, "INSERT INTO life_game VALUES ('synthetic','game',8,'operation',X'010203','opaque',X'040506',13,'PAUSED',10,17,NULL,NULL,11,18)")
        sql(c, "INSERT INTO life_event VALUES ('synthetic','game',1,8,'group',11,18,NULL,X'070809')")
        sql(c, "INSERT INTO rulings_generation VALUES ('rulings','scryfall','ACTIVE','RULINGS_LANGUAGE_NOT_ASSERTED','opaque-provenance',1,1)")
        sql(c, "INSERT INTO ruling_record VALUES ('rulings',1,'scryfall','unmatched','wotc','2026-10-14','opaque-record')")
        sql(c, "INSERT INTO rulings_active VALUES (1,'rulings')")
    }
    private fun migrate(c: SQLiteConnection) = runBlocking { Migration7To8.migrate(c) }

    @Test fun additiveMigrationPreservesEveryOldDdlAndHistoryPayloadThroughReopen() {
        val file = folder.newFile("source-receipt.db")
        old(file).use { c ->
            val ddl = text(c, "SELECT group_concat(sql,'|') FROM (SELECT sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name)")
            sql(c, "BEGIN IMMEDIATE"); migrate(c); sql(c, "PRAGMA user_version=8"); sql(c, "COMMIT")
            assertEquals(ddl, text(c, "SELECT group_concat(sql,'|') FROM (SELECT sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name NOT IN ('catalog_source_receipt','index_catalog_source_receipt_metadata_job') ORDER BY name)"))
            sql(c, "INSERT INTO catalog_source_receipt VALUES ('receipt','no-retained-job',1,'opaque-é-😀')")
            assertEquals(0L, number(c, "SELECT count(*) FROM pragma_foreign_key_check"))
        }
        BundledSQLiteDriver().open(file.path).use { c ->
            assertEquals(8L, number(c, "PRAGMA user_version"))
            assertEquals("040506", text(c, "SELECT hex(checkpoint_payload) FROM life_game"))
            assertEquals("070809", text(c, "SELECT hex(archive_payload) FROM life_event"))
            assertEquals("opaque-record", text(c, "SELECT record_payload FROM ruling_record"))
            assertEquals("rulings", text(c, "SELECT generation_id FROM rulings_active"))
            assertEquals("opaque-é-😀", text(c, "SELECT receipt_payload FROM catalog_source_receipt"))
            assertEquals(19L, number(c, "SELECT revision FROM acquisition_origin_cooldown"))
        }
    }

    @Test fun uniqueMetadataJobAndReceiptIdRejectConflictsWithoutErasingHistory() {
        old(folder.newFile("constraints.db")).use { c ->
            migrate(c); sql(c, "INSERT INTO catalog_source_receipt VALUES ('receipt','metadata-job',1,'opaque')")
            assertThrows(SQLiteException::class.java) { sql(c, "INSERT INTO catalog_source_receipt VALUES ('different','metadata-job',1,'conflict')") }
            assertThrows(SQLiteException::class.java) { sql(c, "INSERT INTO catalog_source_receipt VALUES ('receipt','other-job',1,'conflict')") }
            assertEquals(0L, number(c, "SELECT count(*) FROM pragma_foreign_key_list('catalog_source_receipt')"))
            sql(c, "DELETE FROM catalog_source_receipt")
            assertEquals(1L, number(c, "SELECT count(*) FROM ruling_record"))
            assertEquals(1L, number(c, "SELECT count(*) FROM life_event"))
        }
    }

    @Test fun failedMigrationTransactionRollsBackNewTableVersionAndEvidenceTogether() {
        old(folder.newFile("rollback.db")).use { c ->
            sql(c, "BEGIN IMMEDIATE"); migrate(c); sql(c, "PRAGMA user_version=8")
            sql(c, "INSERT INTO catalog_source_receipt VALUES ('receipt','metadata-job',1,'opaque')")
            assertThrows(SQLiteException::class.java) { sql(c, "INSERT INTO catalog_source_receipt VALUES ('other','metadata-job',1,'conflict')") }
            sql(c, "ROLLBACK")
            assertEquals(7L, number(c, "PRAGMA user_version"))
            assertEquals(0L, number(c, "SELECT count(*) FROM sqlite_master WHERE name='catalog_source_receipt'"))
            assertEquals("rulings", text(c, "SELECT generation_id FROM rulings_active"))
            assertEquals("070809", text(c, "SELECT hex(archive_payload) FROM life_event"))
            migrate(c)
            assertEquals(0L, number(c, "SELECT count(*) FROM catalog_source_receipt"))
        }
    }
}
