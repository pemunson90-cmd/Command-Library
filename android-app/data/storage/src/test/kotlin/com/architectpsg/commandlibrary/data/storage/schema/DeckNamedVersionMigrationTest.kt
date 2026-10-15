package com.architectpsg.commandlibrary.data.storage.schema

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** SQL constraints/query plans only: synthetic payloads/hashes are not version-backend evidence. */
class DeckNamedVersionMigrationTest {
    private fun sql(c: SQLiteConnection, q: String) { c.prepare(q).use { it.step() } }
    private fun schema(c: SQLiteConnection, resource: String) {
        requireNotNull(javaClass.getResourceAsStream("/$resource")).bufferedReader().use { it.readText() }
            .split(";\n").map(String::trim).filter(String::isNotEmpty).forEach { sql(c, it) }
        sql(c, "PRAGMA foreign_keys=ON")
    }
    private fun objects(c: SQLiteConnection): List<List<String?>> = c.prepare(
        "SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name!='room_master_table' ORDER BY type,name"
    ).use { s -> buildList { while (s.step()) add((0..3).map { if (s.isNull(it)) null else s.getText(it) }) } }
    private suspend fun migrate(c: SQLiteConnection) { sql(c, "BEGIN IMMEDIATE"); Migration13To14.migrate(c); sql(c, "COMMIT") }
    private fun seed(c: SQLiteConnection, deck: String, sequence: Int) {
        sql(c, "INSERT INTO deck_head VALUES('local-decks','$deck',0,'D',1,0,1,0,0,0)")
        sql(c, "INSERT INTO deck_revision VALUES('local-decks','$deck',0,'D',1,0,1,0,0,0,1,'${"0".repeat(64)}',X'00')")
        sql(c, "INSERT INTO deck_operation VALUES($sequence,'create-$deck',1,'${"0".repeat(64)}','CREATE','local-decks','$deck',0)")
    }
    private fun receipt(c: SQLiteConnection, deck: String, sequence: Int, kind: String = "CAPTURE") {
        sql(c, "INSERT INTO deck_operation VALUES($sequence,'named-$sequence',1,'${"0".repeat(64)}','$kind','local-decks','$deck',0)")
    }
    private fun named(c: SQLiteConnection, id: String, deck: String, sequence: Int, previous: String? = null, target: String? = null) {
        fun quoted(value: String?) = value?.let { "'$it'" } ?: "NULL"
        sql(c, "INSERT INTO deck_named_version VALUES('$id',1,'local-decks','$deck','Named',1,0,0,$sequence,${quoted(previous)},${quoted(target)},'USER','${"0".repeat(64)}')")
    }
    private fun constraint(action: () -> Unit) {
        try { action(); fail("Expected immediate SQL constraint failure") } catch (_: SQLiteException) { }
    }
    private fun plan(c: SQLiteConnection, query: String, index: String) {
        val details = c.prepare("EXPLAIN QUERY PLAN $query").use { s -> buildList { while (s.step()) add(s.getText(3)) } }
        assertTrue(details.toString(), details.any { it.contains(index) })
        assertFalse(details.toString(), details.any { it.startsWith("SCAN deck_") })
    }

    @Test fun actual13To14PreservesOldSchemaAndBytesAndEqualsFreshExport() = runBlocking {
        BundledSQLiteDriver().open(":memory:").use { c ->
            schema(c, "inventory-schema13.sql"); val before = objects(c)
            sql(c, "INSERT INTO life_profile VALUES('profile',0,'P','p',X'0001FF',7,9)")
            seed(c, "deck", 1); migrate(c)
            assertEquals(before, objects(c).filterNot { it[2] == "deck_named_version" || it[1] == "index_deck_operation_version_latest" })
            BundledSQLiteDriver().open(":memory:").use { fresh ->
                schema(fresh, "deck-versions-schema14.sql"); assertEquals(objects(fresh), objects(c))
            }
            c.prepare("SELECT hex(payload) FROM life_profile").use { assertTrue(it.step()); assertEquals("0001FF", it.getText(0)) }
            c.prepare("SELECT hex(payload) FROM deck_revision").use { assertTrue(it.step()); assertEquals("00", it.getText(0)) }
            c.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
    }

    @Test fun sameDeckReferencesGlobalIdentityAndUniqueReceiptAreImmediateConstraints() = runBlocking {
        BundledSQLiteDriver().open(":memory:").use { c ->
            schema(c, "inventory-schema13.sql"); migrate(c); seed(c, "a", 1); seed(c, "b", 2)
            for (sequence in 3..12) receipt(c, if (sequence == 4) "b" else "a", sequence)
            named(c, "a-first", "a", 3); named(c, "b-first", "b", 4)
            constraint { named(c, "missing-source", "missing", 5) }
            constraint { named(c, "wrong-predecessor", "a", 6, "b-first") }
            constraint { named(c, "wrong-target", "a", 7, target = "b-first") }
            constraint { named(c, "duplicate-receipt", "a", 3) }
            constraint { named(c, "a-first", "b", 8) }
            constraint { named(c, "missing-receipt", "a", 99) }
            named(c, "a-next", "a", 9, "a-first", "a-first")
            c.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
            // Kind/target/hash/receipt-deck and immediate chronology are intentionally backend
            // validation responsibilities; FK success alone never grants version integrity.
        }
    }

    @Test fun reverseReceiptAssociationAndPredecessorQueriesUseBoundedIndexes() = runBlocking {
        BundledSQLiteDriver().open(":memory:").use { c ->
            schema(c, "inventory-schema13.sql"); migrate(c); seed(c, "a", 1)
            receipt(c, "a", 2); named(c, "first", "a", 2)
            receipt(c, "a", 3, "RESTORE"); named(c, "next", "a", 3, "first", "first")
            for (kind in listOf("CAPTURE", "RESTORE")) plan(c,
                "SELECT sequence FROM deck_operation WHERE provider='local-decks' AND deck_id='a' AND kind='$kind' ORDER BY sequence DESC LIMIT 1",
                "index_deck_operation_version_latest")
            plan(c, "SELECT version_id FROM deck_named_version WHERE creating_sequence=3", "index_deck_named_version_receipt")
            plan(c, "SELECT version_id FROM deck_named_version WHERE provider='local-decks' AND deck_id='a' AND creating_sequence<3 ORDER BY creating_sequence DESC LIMIT 1", "index_deck_named_version_order")
            // Newest named row can be deleted while its receipt survives; this fixture proves
            // the condition the required reverse-association backend guard must detect.
            sql(c, "DELETE FROM deck_named_version WHERE version_id='next'")
            c.prepare("SELECT count(*) FROM deck_named_version WHERE creating_sequence=3").use { assertTrue(it.step()); assertEquals(0L, it.getLong(0)) }
            c.prepare("SELECT sequence FROM deck_operation WHERE kind='RESTORE'").use { assertTrue(it.step()); assertEquals(3L, it.getLong(0)) }
        }
    }

    @Test fun interruptedMigrationRollsBackAllAdditiveObjects() = runBlocking {
        BundledSQLiteDriver().open(":memory:").use { c ->
            schema(c, "inventory-schema13.sql"); val before = objects(c)
            sql(c, "BEGIN IMMEDIATE"); Migration13To14.migrate(c); sql(c, "ROLLBACK")
            assertEquals(before, objects(c))
        }
    }
}
