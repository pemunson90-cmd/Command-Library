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

/** Actual bundled SQLite schema tests. Opaque synthetic blobs do not claim valid persisted service data. */
class LifeMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    private fun sql(c: SQLiteConnection, text: String) { c.prepare(text).use { it.step() } }
    private fun scalar(c: SQLiteConnection, text: String): Long = c.prepare(text).use { assertTrue(it.step()); it.getLong(0) }
    private fun text(c: SQLiteConnection, query: String): String = c.prepare(query).use { assertTrue(it.step()); it.getText(0) }
    private fun schema5(file: File): SQLiteConnection = BundledSQLiteDriver().open(file.path).also { c ->
        sql(c, "PRAGMA foreign_keys=ON")
        val fixture = requireNotNull(javaClass.getResourceAsStream("/life-schema5.sql")).bufferedReader().use { it.readText() }
        for (statement in fixture.split(";\n").map { it.trim() }.filter { it.isNotEmpty() }) sql(c, statement)
        sql(c, "INSERT INTO acquisition_origin_cooldown VALUES ('synthetic://origin', 19, 600, 17)")
    }
    private fun migrate(c: SQLiteConnection) = runBlocking { Migration5To6.migrate(c) }
    private fun game(c: SQLiteConnection, id: String = "game", operation: String = "operation") = sql(c, """INSERT INTO life_game
        (game_source, game_id, revision, creation_operation_id, creation_payload, creation_sha256, checkpoint_payload,
        checkpoint_sequence, status, started_seconds, started_nano, ended_seconds, ended_nano, updated_seconds, updated_nano)
        VALUES ('synthetic', '$id', 0, '$operation', X'010203', 'synthetic-not-a-real-hash', X'040506', 0, 'PAUSED', 10, 17, NULL, NULL, 10, 17)""")
    private fun event(c: SQLiteConnection, id: Int, revision: Int, undo: String = "NULL", gameId: String = "game") =
        sql(c, """INSERT INTO life_event VALUES ('synthetic', '$gameId', $id, $revision, 'group', 10, 17, $undo, X'010203')""")

    @Test fun additiveMigrationPreservesOldSchemaAndPayloadsThroughReopen() {
        val file = folder.newFile("life.db")
        schema5(file).use { c ->
            val oldSchema = text(c, "SELECT group_concat(sql, '|') FROM (SELECT sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name)")
            migrate(c); sql(c, "PRAGMA user_version=6")
            assertEquals(19L, scalar(c, "SELECT revision FROM acquisition_origin_cooldown"))
            assertEquals(17L, scalar(c, "SELECT interval_nano FROM acquisition_origin_cooldown"))
            val retained = text(c, "SELECT group_concat(sql, '|') FROM (SELECT sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name NOT LIKE 'life_%' AND name NOT LIKE 'index_life_%' ORDER BY name)")
            assertEquals(oldSchema, retained)
            game(c); event(c, 1, 1)
            assertEquals(0L, scalar(c, "SELECT count(*) FROM pragma_foreign_key_check"))
        }
        BundledSQLiteDriver().open(file.path).use { c ->
            assertEquals(6L, scalar(c, "PRAGMA user_version"))
            assertEquals(6L, scalar(c, "SELECT count(*) FROM sqlite_master WHERE type='table' AND name LIKE 'life_%'"))
            c.prepare("SELECT checkpoint_payload FROM life_game").use { assertTrue(it.step()); assertArrayEquals(byteArrayOf(4, 5, 6), it.getBlob(0)) }
            assertEquals(1L, scalar(c, "SELECT count(*) FROM life_event"))
            assertEquals(19L, scalar(c, "SELECT revision FROM acquisition_origin_cooldown"))
        }
    }
    @Test fun uniqueRevisionUndoAndParentConstraintsRejectInvalidWrites() {
        schema5(folder.newFile("constraints.db")).use { c ->
            migrate(c); game(c); event(c, 1, 1); event(c, 2, 2, "1")
            assertThrows(SQLiteException::class.java) { event(c, 3, 3, "1") }
            assertThrows(SQLiteException::class.java) { event(c, 4, 2) }
            assertThrows(SQLiteException::class.java) { event(c, 5, 5, gameId = "missing") }
            assertThrows(SQLiteException::class.java) { game(c) }
            assertThrows(SQLiteException::class.java) { game(c, id = "different-game") }
            assertEquals(2L, scalar(c, "SELECT count(*) FROM life_event"))
        }
    }
    @Test fun checkpointCasChecksSequenceAsWellAsUserRevision() {
        schema5(folder.newFile("cas.db")).use { c ->
            migrate(c); game(c)
            sql(c, "UPDATE life_game SET checkpoint_sequence=1, checkpoint_payload=X'09' WHERE game_source='synthetic' AND game_id='game' AND revision=0 AND checkpoint_sequence=0")
            assertEquals(1L, scalar(c, "SELECT changes()"))
            sql(c, "UPDATE life_game SET checkpoint_sequence=2, checkpoint_payload=X'08' WHERE game_source='synthetic' AND game_id='game' AND revision=0 AND checkpoint_sequence=0")
            assertEquals(0L, scalar(c, "SELECT changes()"))
            assertEquals(0L, scalar(c, "SELECT revision FROM life_game"))
            assertEquals(1L, scalar(c, "SELECT checkpoint_sequence FROM life_game"))
            c.prepare("SELECT checkpoint_payload FROM life_game").use { assertTrue(it.step()); assertArrayEquals(byteArrayOf(9), it.getBlob(0)) }
        }
    }
    @Test fun explicitGameDeleteCascadesChildrenAndPreservesReusableProfiles() {
        schema5(folder.newFile("cascade.db")).use { c ->
            migrate(c); game(c); event(c, 1, 1)
            sql(c, "INSERT INTO life_profile VALUES ('profile', 0, 'Synthetic', 'synthetic', X'01', 10, 17)")
            sql(c, "INSERT INTO life_seat_search VALUES ('synthetic', 'game', 'seat', 'Synthetic', 'synthetic', NULL, NULL, NULL, NULL)")
            sql(c, "INSERT INTO life_result VALUES ('synthetic', 'game', 'result', 1, 10, 17, 'Synthetic reason', 1)")
            sql(c, "INSERT INTO life_alert VALUES ('synthetic', 'game', 'occurrence', 'GAME_TIMER', NULL, 10, 17, NULL, NULL, 0)")
            sql(c, "DELETE FROM life_game WHERE game_source='synthetic' AND game_id='game'")
            for (table in listOf("life_event", "life_seat_search", "life_result", "life_alert")) assertEquals(0L, scalar(c, "SELECT count(*) FROM $table"))
            assertEquals(1L, scalar(c, "SELECT count(*) FROM life_profile"))
            assertEquals(0L, scalar(c, "SELECT count(*) FROM pragma_foreign_key_check"))
        }
    }
    @Test fun failedMigrationAndFailedActionTransactionsRollbackCompletely() {
        schema5(folder.newFile("rollback.db")).use { c ->
            sql(c, "BEGIN IMMEDIATE"); migrate(c)
            assertThrows(SQLiteException::class.java) { event(c, 1, 1, gameId = "missing") }
            sql(c, "ROLLBACK")
            assertEquals(5L, scalar(c, "PRAGMA user_version"))
            assertEquals(0L, scalar(c, "SELECT count(*) FROM sqlite_master WHERE name LIKE 'life_%'"))
            assertEquals(19L, scalar(c, "SELECT revision FROM acquisition_origin_cooldown"))
            migrate(c); game(c); event(c, 1, 1)
            sql(c, "BEGIN IMMEDIATE")
            sql(c, "UPDATE life_game SET revision=1, checkpoint_sequence=1, checkpoint_payload=X'09'")
            assertThrows(SQLiteException::class.java) { event(c, 2, 1) }
            sql(c, "ROLLBACK")
            assertEquals(0L, scalar(c, "SELECT revision FROM life_game"))
            assertEquals(0L, scalar(c, "SELECT checkpoint_sequence FROM life_game"))
            assertEquals(1L, scalar(c, "SELECT count(*) FROM life_event"))
        }
    }
}
