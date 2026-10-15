package com.architectpsg.commandlibrary

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Actual Room on host with real-file OS bridge; not native/device execution. */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class DeckSchemaRoomIntegrationTest {
    private val token = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private fun <T> ok(value: Outcome<T>): T = (value as Outcome.Success).value
    @Test fun actualRoomFreshAndSchema9UpgradeOpenEmptyDeckTablesPreservingOldBytes() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (upgrade in listOf(false, true)) {
            val name = "deck-schema-${UUID.randomUUID()}.db"
            try {
                if (upgrade) {
                    val file = context.getDatabasePath(name); file.parentFile!!.mkdirs()
                    BundledSQLiteDriver().open(file.path).use { c ->
                        val fixture = File(checkNotNull(System.getProperty("commandlibrary.schema9.fixture"))).readText()
                        fixture.split(";\n").map(String::trim).filter(String::isNotEmpty).forEach { q -> c.prepare(q).use { it.step() } }
                        c.prepare("PRAGMA user_version=9").use { it.step() }
                        c.prepare("INSERT INTO life_profile VALUES('profile',0,'É','é',X'0001FF',7,9)").use { it.step() }
                    }
                }
                ok(StorageDatabase.open(context, name)).use { db ->
                    assertEquals(10, ok(db.capabilities(token)).schemaVersion)
                    for (table in listOf("deck_head", "deck_revision", "deck_operation")) {
                        val rows = ok(db.query(SqlRequest("SELECT count(*) AS n FROM $table", SnapshotList.empty(), 1), token))
                        assertEquals(SqlValue.Integer(0), rows.single().columns.single().value)
                    }
                    if (upgrade) {
                        val rows = ok(db.query(SqlRequest("SELECT hex(payload) AS payload FROM life_profile WHERE profile_id='profile'", SnapshotList.empty(), 1), token))
                        assertEquals(SqlValue.Text("0001FF"), rows.single().columns.single().value)
                    }
                }
            } finally { context.deleteDatabase(name) }
        }
    }
}
