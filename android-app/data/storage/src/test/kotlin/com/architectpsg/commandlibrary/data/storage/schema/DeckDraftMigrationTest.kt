package com.architectpsg.commandlibrary.data.storage.schema

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Physical SQL evidence only. Synthetic descriptors do not establish draft-adapter integrity. */
class DeckDraftMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    private val hash = "0".repeat(64)
    private fun sql(c: SQLiteConnection, q: String) { c.prepare(q).use { it.step() } }
    private fun schema(c: SQLiteConnection, resource: String) {
        requireNotNull(javaClass.getResourceAsStream("/$resource")).bufferedReader().use { it.readText() }
            .split(";\n").map(String::trim).filter(String::isNotEmpty).forEach { sql(c, it) }
        sql(c, "PRAGMA foreign_keys=ON")
    }
    private fun objects(c: SQLiteConnection): List<List<String?>> = c.prepare(
        "SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name!='room_master_table' ORDER BY type,name"
    ).use { s -> buildList { while (s.step()) add((0..3).map { if (s.isNull(it)) null else s.getText(it) }) } }
    private suspend fun migrate(c: SQLiteConnection) {
        sql(c, "BEGIN IMMEDIATE"); Migration14To15.migrate(c); sql(c, "COMMIT")
    }
    private fun seed(c: SQLiteConnection, deck: String, sequence: Int) {
        sql(c, "INSERT INTO deck_head VALUES('local-decks','$deck',0,'D',1,0,1,0,0,0)")
        sql(c, "INSERT INTO deck_revision VALUES('local-decks','$deck',0,'D',1,0,1,0,0,0,1,'$hash',X'00')")
        sql(c, "INSERT INTO deck_operation VALUES($sequence,'create-$deck',1,'$hash','CREATE','local-decks','$deck',0)")
    }
    private fun slot(c: SQLiteConnection, deck: String) {
        sql(c, "INSERT INTO deck_draft_state VALUES('local-decks','$deck',1,1,0,1,1,'$hash',X'00',NULL,'WRITE','$hash','$hash')")
    }
    private fun intent(c: SQLiteConnection, deck: String, operation: String, generation: Int, receipt: Int? = null) {
        // Nullable terminal fields intentionally empty: this is a physical constraint fixture.
        sql(c, "INSERT INTO deck_draft_intent VALUES('$operation','local-decks','$deck',1,$generation,0,1,1,'$hash',X'00','TEXT',1,1,'$hash',X'00',1,'$hash','PREPARED',${receipt ?: "NULL"},NULL,NULL,NULL,NULL,NULL,'$hash')")
    }
    private fun constraint(action: () -> Unit) {
        try { action(); fail("Expected immediate SQL constraint failure") } catch (_: SQLiteException) { }
    }
    private fun plan(c: SQLiteConnection, query: String, index: String) {
        val details = c.prepare("EXPLAIN QUERY PLAN $query").use { s -> buildList { while (s.step()) add(s.getText(3)) } }
        assertTrue(details.toString(), details.any { it.contains(index) })
        assertFalse(details.toString(), details.any { it.startsWith("SCAN deck_") })
    }
    @Test fun actual14To15PreservesEveryOldObjectAndPayloadAndEqualsFreshExport() = runBlocking {
        BundledSQLiteDriver().open(":memory:").use { c ->
            schema(c, "deck-versions-schema14.sql"); val before = objects(c)
            seed(c, "a", 1)
            sql(c, "INSERT INTO life_profile VALUES('profile',0,'É','é',X'0001FF',7,9)")
            migrate(c)
            assertEquals(before, objects(c).filterNot { it[2] in setOf("deck_draft_state", "deck_draft_intent") })
            BundledSQLiteDriver().open(":memory:").use { fresh ->
                schema(fresh, "deck-drafts-schema15.sql"); assertEquals(objects(fresh), objects(c))
            }
            c.prepare("SELECT hex(payload) FROM life_profile").use { assertTrue(it.step()); assertEquals("0001FF", it.getText(0)) }
            c.prepare("SELECT hex(payload) FROM deck_revision").use { assertTrue(it.step()); assertEquals("00", it.getText(0)) }
            c.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
    }
    @Test fun immediateCycleInsertionQualifiedReferencesAndGlobalReservationsAreConstrained() = runBlocking {
        BundledSQLiteDriver().open(":memory:").use { c ->
            schema(c, "deck-versions-schema14.sql"); migrate(c); seed(c,"a",1); seed(c,"b",2)
            constraint { slot(c,"missing") }
            slot(c,"a"); slot(c,"b"); intent(c,"a","a-op",1); intent(c,"b","b-op",1)
            sql(c,"UPDATE deck_draft_state SET latest_operation_id='a-op' WHERE deck_id='a'")
            constraint { sql(c,"UPDATE deck_draft_state SET latest_operation_id='b-op' WHERE deck_id='a'") }
            constraint { intent(c,"a","a-op",2) }
            constraint { intent(c,"a","same-generation",1) }
            constraint { intent(c,"a","missing-receipt",2,99) }
            intent(c,"a","receipt-one",2,1)
            constraint { intent(c,"a","duplicate-receipt",3,1) }
            constraint { sql(c,"UPDATE deck_draft_state SET raw_base_revision=99 WHERE deck_id='a'") }
            constraint { sql(c,"UPDATE deck_draft_intent SET result_revision=99 WHERE operation_id='a-op'") }
            constraint { sql(c,"UPDATE deck_draft_intent SET effect_base_revision=99 WHERE operation_id='a-op'") }
            constraint { sql(c,"DELETE FROM deck_draft_intent WHERE operation_id='a-op'") }
            c.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
            // Cross-table operation namespace and semantic hash/status/latest invariants require
            // the real adapter. SQL uniqueness across these two tables cannot establish them.
        }
    }
    @Test fun boundedLatestPendingAndReverseReceiptQueriesUseAcceptedIndexes() = runBlocking {
        BundledSQLiteDriver().open(":memory:").use { c ->
            schema(c, "deck-drafts-schema15.sql"); seed(c,"a",1); slot(c,"a"); intent(c,"a","op",1)
            plan(c,"SELECT operation_id FROM deck_draft_intent WHERE provider='local-decks' AND deck_id='a' ORDER BY source_generation DESC LIMIT 1","index_deck_draft_intent_order")
            plan(c,"SELECT operation_id FROM deck_draft_intent WHERE provider='local-decks' AND deck_id='a' AND status='PREPARED' ORDER BY source_generation DESC LIMIT 2","index_deck_draft_intent_pending")
            plan(c,"SELECT operation_id FROM deck_draft_intent WHERE receipt_sequence=1","index_deck_draft_intent_receipt")
            plan(c,"SELECT sequence FROM deck_operation WHERE provider='local-decks' AND deck_id='a' AND kind='DRAFT_COMMIT' ORDER BY sequence DESC LIMIT 1","index_deck_operation_version_latest")
        }
    }
    @Test fun interruptedMigrationRollsBackAllElevenNewObjects() = runBlocking {
        BundledSQLiteDriver().open(":memory:").use { c ->
            schema(c,"deck-versions-schema14.sql"); val before=objects(c)
            sql(c,"BEGIN IMMEDIATE"); Migration14To15.migrate(c)
            assertEquals(before.size+11,objects(c).size)
            sql(c,"ROLLBACK"); assertEquals(before,objects(c))
        }
    }
    @Test fun pinnedRawBytesAndCycleSurviveIndependentFileReopen() = runBlocking {
        val path=folder.newFile("draft-schema.db").path
        BundledSQLiteDriver().open(path).use { c ->
            schema(c,"deck-versions-schema14.sql"); migrate(c); seed(c,"a",1)
            sql(c,"BEGIN IMMEDIATE"); slot(c,"a"); intent(c,"a","op",1)
            sql(c,"UPDATE deck_draft_state SET latest_operation_id='op',raw=X'00D800FF' WHERE deck_id='a'")
            sql(c,"UPDATE deck_draft_intent SET raw=X'00D800FF' WHERE operation_id='op'"); sql(c,"COMMIT")
        }
        BundledSQLiteDriver().open(path).use { c ->
            sql(c,"PRAGMA foreign_keys=ON")
            c.prepare("SELECT hex(s.raw),hex(i.raw),s.latest_operation_id FROM deck_draft_state s JOIN deck_draft_intent i ON s.latest_operation_id=i.operation_id").use {
                assertTrue(it.step()); assertEquals("00D800FF",it.getText(0)); assertEquals("00D800FF",it.getText(1)); assertEquals("op",it.getText(2)); assertFalse(it.step())
            }
            c.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
    }
}
