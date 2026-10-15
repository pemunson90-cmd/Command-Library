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

/** Actual exported8/9 DDL and bundled SQLite; opaque synthetic payloads, no corpus/device claim. */
class SearchProjectionMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    private fun sql(c: SQLiteConnection, text: String) { c.prepare(text).use { it.step() } }
    private fun number(c: SQLiteConnection, text: String): Long = c.prepare(text).use { assertTrue(it.step()); it.getLong(0) }
    private fun text(c: SQLiteConnection, query: String): String = c.prepare(query).use { assertTrue(it.step()); it.getText(0) }
    private fun schema(c: SQLiteConnection, version: Int) {
        val ddl = requireNotNull(javaClass.getResourceAsStream("/search-projection-schema$version.sql")).bufferedReader().use { it.readText() }
        ddl.split(";\n").map(String::trim).filter(String::isNotEmpty).forEach { sql(c, it) }
    }
    private fun old(file: File): SQLiteConnection = BundledSQLiteDriver().open(file.path).also { c ->
        sql(c, "PRAGMA foreign_keys=ON"); schema(c, 8)
        sql(c, "INSERT INTO catalog_generation(generation_id,provider,format,content_uri,display_name,observed_at_epoch_seconds,observed_at_nano,content_sha256,observed_bytes,language_scope,payload_schema_version,parser_version,oracle_count,face_count,printing_count,status) VALUES('g','synthetic','opaque','content://synthetic/source','opaque',1,0,'opaque',1,'en',1,'opaque',1,1,1,'RETIRED')")
        sql(c, "INSERT INTO oracle_card VALUES('g','synthetic','o','en','É — 😀','opaque-text','sort','search','opaque-digest','opaque-card-é-😀')")
        sql(c, "INSERT INTO card_face VALUES('g','synthetic','o','en',0,NULL,'face','opaque-face')")
        sql(c, "INSERT INTO printing VALUES('g','synthetic','p','synthetic','o','en','set',NULL,'007a','en',NULL,NULL,NULL,'opaque-printing')")
        sql(c, "INSERT INTO catalog_active VALUES('synthetic-dataset','g')")
        sql(c, "INSERT INTO catalog_source_receipt VALUES('receipt','metadata-job',1,'opaque-receipt-é-😀')")
        sql(c, "INSERT INTO acquisition_origin_cooldown VALUES('synthetic://origin',19,600,17)")
        sql(c, "INSERT INTO life_game VALUES('synthetic','game',8,'operation',X'010203','opaque',X'040506',13,'PAUSED',10,17,NULL,NULL,11,18)")
        sql(c, "INSERT INTO life_event VALUES('synthetic','game',1,8,'group',11,18,NULL,X'070809')")
        sql(c, "INSERT INTO rulings_generation VALUES('rulings','synthetic','ACTIVE','RULINGS_LANGUAGE_NOT_ASSERTED','opaque-provenance',1,1)")
        sql(c, "INSERT INTO ruling_record VALUES('rulings',1,'synthetic','unmatched','wotc','2026-10-14','opaque-ruling')")
        sql(c, "INSERT INTO rulings_active VALUES(1,'rulings')")
    }
    private fun migrate(c: SQLiteConnection) = runBlocking { Migration8To9.migrate(c) }
    private fun definitions(c: SQLiteConnection, oldOnly: Boolean = false) = text(c,
        "SELECT group_concat(sql,'|') FROM (SELECT sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name!='room_master_table'" +
            (if (oldOnly) " AND name NOT LIKE 'catalog_search_%' AND name NOT LIKE 'index_catalog_search_%'" else "") + " ORDER BY name)")
    private fun build(c: SQLiteConnection) = sql(c,
        "INSERT INTO catalog_search_build(generation_id,projection_version,status,phase,card_count,face_count,printing_count,text_count,legality_count) VALUES('g',1,'PREPARING','CARD',0,0,0,0,0)")
    private fun facts(c: SQLiteConnection) {
        build(c)
        sql(c, "INSERT INTO catalog_search_card_fact(generation_id,projection_version,provider,oracle_id,language,oracle_text_known,type_known,keywords_known,face_count) VALUES('g',1,'synthetic','o','en',0,0,1,1)")
        sql(c, "INSERT INTO catalog_search_face_fact(generation_id,projection_version,provider,oracle_id,language,face_index,oracle_text_known,type_known) VALUES('g',1,'synthetic','o','en',0,0,0)")
        sql(c, "INSERT INTO catalog_search_printing_fact(generation_id,projection_version,provider,printing_id,set_code,language,legalities_known) VALUES('g',1,'synthetic','p','set','en',0)")
        sql(c, "INSERT INTO catalog_search_legality_fact VALUES('g',1,'synthetic','p','synthetic:format','LEGAL')")
        sql(c, "INSERT INTO catalog_search_text_fact VALUES('g',1,'synthetic','o','en','CANONICAL',0,'NAME','elan synthetic')")
        sql(c, "INSERT INTO catalog_search_text_fts VALUES('elan synthetic','g',1,'synthetic','o','en','CANONICAL',0,'NAME')")
    }
    private fun originals(c: SQLiteConnection) {
        assertEquals("opaque-card-é-😀", text(c, "SELECT record_payload FROM oracle_card"))
        assertEquals("opaque-face", text(c, "SELECT face_payload FROM card_face"))
        assertEquals("opaque-printing", text(c, "SELECT metadata_payload FROM printing"))
        assertEquals("007a", text(c, "SELECT collector_number FROM printing"))
        assertEquals("g", text(c, "SELECT generation_id FROM catalog_active"))
        assertEquals("RETIRED", text(c, "SELECT status FROM catalog_generation"))
        assertEquals("opaque-receipt-é-😀", text(c, "SELECT receipt_payload FROM catalog_source_receipt"))
        assertEquals("040506", text(c, "SELECT hex(checkpoint_payload) FROM life_game"))
        assertEquals("070809", text(c, "SELECT hex(archive_payload) FROM life_event"))
        assertEquals("opaque-ruling", text(c, "SELECT record_payload FROM ruling_record"))
        assertEquals(19L, number(c, "SELECT revision FROM acquisition_origin_cooldown"))
        assertEquals(0L, number(c, "SELECT count(*) FROM pragma_foreign_key_check"))
    }

    @Test fun actual8To9MatchesFreshExportPreservesOldDefinitionsAndReopensWithoutPreparingData() {
        val file = folder.newFile("search.db")
        old(file).use { c ->
            val oldDdl = definitions(c)
            sql(c, "BEGIN IMMEDIATE"); migrate(c); sql(c, "PRAGMA user_version=9"); sql(c, "COMMIT")
            assertEquals(oldDdl, definitions(c, true))
            BundledSQLiteDriver().open(":memory:").use { fresh ->
                schema(fresh, 9); assertEquals(definitions(fresh), definitions(c))
            }
            assertEquals(0L, number(c, "SELECT count(*) FROM catalog_search_build"))
            assertEquals(0L, number(c, "SELECT count(*) FROM catalog_search_text_fts"))
            originals(c)
        }
        BundledSQLiteDriver().open(file.path).use { c ->
            assertEquals(9L, number(c, "PRAGMA user_version")); originals(c)
            assertEquals(0L, number(c, "SELECT count(*) FROM catalog_search_card_fact"))
        }
    }

    @Test fun versionedSourceQualifiedForeignKeysRejectOrphansAndDerivedDeletionKeepsOriginals() {
        old(folder.newFile("fk.db")).use { c ->
            migrate(c); facts(c)
            for (statement in listOf(
                "UPDATE catalog_search_card_fact SET projection_version=2",
                "UPDATE catalog_search_card_fact SET provider='wrong'",
                "UPDATE catalog_search_face_fact SET face_index=99",
                "UPDATE catalog_search_printing_fact SET printing_id='wrong'",
                "UPDATE catalog_search_legality_fact SET projection_version=2",
                "UPDATE catalog_search_text_fact SET oracle_id='wrong'")) {
                assertThrows(SQLiteException::class.java) { sql(c, statement) }
            }
            assertEquals(1L, number(c, "SELECT count(*) FROM catalog_search_text_fts WHERE catalog_search_text_fts MATCH 'value : \"elan\"'"))
            // FTS has no FK. Explicit owner cleanup is required; this does not claim automatic mirroring.
            sql(c, "DELETE FROM catalog_search_text_fts WHERE generation_id='g' AND projection_version=1")
            sql(c, "DELETE FROM catalog_search_build")
            for (table in listOf("card_fact", "face_fact", "printing_fact", "legality_fact", "text_fact", "text_fts"))
                assertEquals(table, 0L, number(c, "SELECT count(*) FROM catalog_search_$table"))
            originals(c)
        }
    }

    @Test fun failedMigrationRollsBackNewTablesAndVersionAndRetryPreservesOldRows() {
        old(folder.newFile("rollback.db")).use { c ->
            val before = definitions(c)
            sql(c, "BEGIN IMMEDIATE"); migrate(c); sql(c, "PRAGMA user_version=9"); facts(c)
            assertThrows(SQLiteException::class.java) { build(c) }
            sql(c, "ROLLBACK")
            assertEquals(8L, number(c, "PRAGMA user_version")); assertEquals(before, definitions(c))
            assertEquals(0L, number(c, "SELECT count(*) FROM sqlite_master WHERE name LIKE 'catalog_search_%'"))
            originals(c)
            sql(c, "BEGIN IMMEDIATE"); migrate(c); sql(c, "PRAGMA user_version=9"); sql(c, "COMMIT")
            originals(c); assertEquals(0L, number(c, "SELECT count(*) FROM catalog_search_build"))
        }
    }

    @Test fun nullableNumericAndEmptyColorEvidenceStayDistinctAndKeysHaveTextAffinity() {
        old(folder.newFile("evidence.db")).use { c ->
            migrate(c); facts(c)
            assertEquals("null", text(c, "SELECT typeof(mana_value_key) FROM catalog_search_card_fact"))
            assertEquals("null", text(c, "SELECT typeof(colors_mask) FROM catalog_search_card_fact"))
            sql(c, "UPDATE catalog_search_card_fact SET colors_mask=0,mana_value_key='2214748364805/'")
            assertEquals("integer", text(c, "SELECT typeof(colors_mask) FROM catalog_search_card_fact"))
            assertEquals(0L, number(c, "SELECT colors_mask FROM catalog_search_card_fact"))
            assertEquals("text", text(c, "SELECT typeof(mana_value_key) FROM catalog_search_card_fact"))
            assertEquals(0L, number(c, "SELECT count(*) FROM catalog_search_card_fact WHERE reserved=0"))
            assertEquals(0L, number(c, "SELECT count(*) FROM catalog_search_card_fact WHERE NOT(reserved=0)"))
            originals(c)
        }
    }
}
