package com.architectpsg.commandlibrary.data.storage.schema

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.architectpsg.commandlibrary.core.model.SqlValue
import com.architectpsg.commandlibrary.data.storage.LEGACY_DDL_SNAPSHOT_QUERY
import com.architectpsg.commandlibrary.data.storage.legacyDdlQueryFor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Actual SQLite reproduces stale native assertions without relaxing legacy-object preservation. */
class LegacyDdlSnapshotTest {
    private fun sql(c: SQLiteConnection, text: String) { c.prepare(text).use { it.step() } }
    private fun rows(c: SQLiteConnection, text: String): List<List<SqlValue>> = c.prepare(text).use { s ->
        buildList { while (s.step()) add((0 until s.getColumnCount()).map { i -> when(s.getColumnType(i)) {
            1 -> SqlValue.Integer(s.getLong(i)); 3 -> SqlValue.Text(s.getText(i)); 5 -> SqlValue.Null
            else -> error("Unexpected DDL column type")
        } }) }
    }
    private fun schema4(c: SQLiteConnection) {
        val ddl = requireNotNull(javaClass.getResourceAsStream("/search-projection-schema4.sql")).bufferedReader().use { it.readText() }
        ddl.split(";\n").map(String::trim).filter(String::isNotEmpty).forEach { sql(c, it) }
    }

    @Test fun actual4ToCurrentReproducesBroadSnapshotFailureAndPreservesEveryOriginalObject() = runBlocking {
        BundledSQLiteDriver().open(":memory:").use { c ->
            sql(c, "PRAGMA foreign_keys=ON"); schema4(c)
            val before = rows(c, LEGACY_DDL_SNAPSHOT_QUERY)
            assertTrue(before.any { it[0] == SqlValue.Text("index") && it[3] == SqlValue.Null })
            assertTrue(before.any { it[2] == SqlValue.Text("catalog_name_fts_data") })
            sql(c, "BEGIN IMMEDIATE")
            Migration4To5.migrate(c); Migration5To6.migrate(c); Migration6To7.migrate(c); Migration7To8.migrate(c)
            val formerQuery = "SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name != 'room_master_table' AND tbl_name != 'acquisition_origin_cooldown' AND tbl_name NOT IN ('life_game','life_event','life_seat_search','life_result','life_alert','life_profile') ORDER BY type,name"
            assertNotEquals("The old native snapshot wrongly includes newer ruling/receipt tables", before, rows(c, formerQuery))
            assertEquals(before, rows(c, legacyDdlQueryFor(before)))
            Migration8To9.migrate(c)
            assertEquals(before, rows(c, legacyDdlQueryFor(before)))
            assertNotEquals(before, rows(c, LEGACY_DDL_SNAPSHOT_QUERY))
            sql(c, "ROLLBACK")
            assertEquals("Rollback must leave NO extra tables or objects", before, rows(c, LEGACY_DDL_SNAPSHOT_QUERY))
        }
    }

    @Test fun changedOldDdlMissingIndexesAndExtraIndexesOrTriggersRemainDetectable() {
        BundledSQLiteDriver().open(":memory:").use { c ->
            sql(c, "CREATE TABLE old_source(id TEXT PRIMARY KEY, value TEXT)")
            sql(c, "CREATE INDEX old_value ON old_source(value)")
            val before = rows(c, LEGACY_DDL_SNAPSHOT_QUERY); val query = legacyDdlQueryFor(before)
            for (change in listOf(
                "ALTER TABLE old_source ADD COLUMN unexpected TEXT",
                "DROP INDEX old_value",
                "CREATE INDEX unexpected ON old_source(id)",
                "CREATE TRIGGER unexpected AFTER INSERT ON old_source BEGIN SELECT 1; END")) {
                sql(c, "BEGIN"); sql(c, change)
                assertNotEquals(change, before, rows(c, query))
                sql(c, "ROLLBACK"); assertEquals(before, rows(c, query))
            }
        }
    }

    @Test fun newTablesAreSeparateButRollbackChecksRemainBroadAndNamesAreQuotedSafely() {
        BundledSQLiteDriver().open(":memory:").use { c ->
            sql(c, "CREATE TABLE \"old'quoted\"(id TEXT PRIMARY KEY)")
            val before = rows(c, LEGACY_DDL_SNAPSHOT_QUERY)
            val query = legacyDdlQueryFor(before)
            assertEquals(before, rows(c, query))
            sql(c, "CREATE TABLE genuinely_new(id INTEGER PRIMARY KEY)")
            assertEquals(before, rows(c, query))
            assertNotEquals(before, rows(c, LEGACY_DDL_SNAPSHOT_QUERY))
        }
        assertThrows(IllegalArgumentException::class.java) { legacyDdlQueryFor(emptyList()) }
    }
}
