package com.architectpsg.commandlibrary

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.di.AppGraph
import com.architectpsg.commandlibrary.feature.ai.SqlLocalProviderProfiles
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Actual host Room/AppGraph proof. Legacy payloads are physical fixtures, not valid domain codecs. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class ProviderProfilesRoomIntegrationTest {
    private val token = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private fun <T> ok(result: Outcome<T>): T {
        assertTrue("Expected success: $result", result is Outcome.Success)
        return (result as Outcome.Success).value
    }
    private fun stale(result: Outcome<*>) {
        assertTrue("Expected stale revision: $result", result is Outcome.Failure)
        assertEquals(FailureCode.STALE_REVISION, (result as Outcome.Failure).error.code)
    }
    private fun missing(result: Outcome<*>) {
        assertTrue("Expected missing profile: $result", result is Outcome.Unavailable)
        assertEquals(UnavailableReason.NOT_FOUND, (result as Outcome.Unavailable).reason)
    }

    @Test fun schema15MigrationAndActualGraphProfilePersistencePreserveLegacyBytesAndReserveDeletedId() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val file = context.getDatabasePath(AppGraph.DATABASE_FILE)
        // Robolectric gives this single method a fresh application. Never overwrite an opened graph DB.
        assertFalse("Bootstrap must precede first AppGraph.ports()", file.exists())
        assertTrue(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val old = schema(15)
        val current = schema(18)
        assertEquals(15, old.getInt("version"))
        assertEquals(48, old.getJSONArray("entities").length())
        val driver = BundledSQLiteDriver()
        val beforeObjects: List<List<SqlValue>>
        val beforeData: List<List<List<SqlValue>>>
        driver.open(file.path).use { connection ->
            val entities = old.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                connection.exec(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (j in 0 until indices.length()) {
                        connection.exec(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
            }
            val setup = old.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) connection.exec(setup.getString(i))
            connection.exec("PRAGMA user_version=15")
            connection.exec("PRAGMA foreign_keys=ON")
            // Deliberately synthetic opaque BLOBs: preservation only, never decoded by a domain adapter.
            connection.exec("INSERT INTO life_profile VALUES('legacy-life',7,'É 日本語','é 日本語',X'0001FF8041',7,9)")
            connection.exec("INSERT INTO deck_head VALUES('legacy-source','legacy-deck',4,'Deck Ω',1,2,3,4,0,0)")
            connection.exec("INSERT INTO deck_revision VALUES('legacy-source','legacy-deck',4,'Deck Ω',1,2,3,4,0,0,5,'synthetic-physical-hash',X'FF00807F42')")
            connection.exec("INSERT INTO deck_operation VALUES(1,'legacy-receipt',1,'synthetic-request-hash','create','legacy-source','legacy-deck',4)")
            assertTrue(connection.rows("PRAGMA foreign_key_check").isEmpty())
            assertEquals(listOf(listOf(SqlValue.Integer(15))), connection.rows("PRAGMA user_version"))
            assertEquals(listOf(listOf(SqlValue.Text(old.getString("identityHash")))), connection.rows(IDENTITY))
            beforeObjects = connection.rows(OBJECTS)
            beforeData = LEGACY.map { connection.rows(it) }
        }

        val graph = (context as CommandLibraryApplication).graph
        val ports = ok(graph.ports()) // The actual composition root opens Room and migrates this file.
        val profiles = checkNotNull(ports.providerProfiles)
        val vault = checkNotNull(ports.secretVault)
        assertSame(profiles, ok(graph.ports()).providerProfiles)
        val id = EntityId("room-provider")
        val endpoint = EndpointProfile(id, Origin("https", "example.test", 443), SnapshotList.empty(), null, 3,
            RetryPolicy(1, Duration.ZERO, 2.seconds, false))
        val reference = ok(vault.reference(SecretScope(id, endpoint.origin, "provider-auth-v1")))
        val profile = ProviderProfile(id, "Synthetic provider 日本語", ProviderDialect.OPENAI_RESPONSES,
            endpoint.copy(secret = reference), "manual-model", ProviderCapabilities(true, true, false, false, true, false),
            reference, Knowledge.Absent("Not supplied"), SnapshotList.empty(), ResourceUri("https://example.test/v1"))
        val created = ok(profiles.create(profile, token))
        assertEquals(profile, created.profile)
        assertEquals(Revision(1), created.revision)
        assertEquals(created, ok(profiles.load(id, token)))
        val edited = ok(profiles.replace(created.profile.copy(name = "Edited Ω", selectedModelId = "manual-model-2"), created.revision, token))
        assertEquals(Revision(2), edited.revision)
        assertEquals(created.createdAt, edited.createdAt)
        stale(profiles.replace(created.profile, created.revision, token))
        assertEquals(edited, ok(profiles.load(id, token)))

        // Independent actual Room handle, actual adapter, actual graph vault, real graph clock.
        ok(StorageDatabase.open(context, AppGraph.DATABASE_FILE)).use { db ->
            assertEquals(18, ok(db.capabilities(token)).schemaVersion)
            val reader = SqlLocalProviderProfiles(db, checkNotNull(ports.utcClock), vault)
            assertEquals(edited, ok(reader.load(id, token)))
            assertEquals(listOf(edited), ok(reader.list(PageRequest(10), token)).items.toList())
            assertEquals(reference, ok(vault.reference(edited.profile.secretRef!!.scope)))
            // A pure reference creates no key. Detach it by CAS before deletion, without vault key calls.
            val detached = ok(profiles.replace(edited.profile.copy(secretRef = null,
                endpoint = edited.profile.endpoint!!.copy(secret = null)), edited.revision, token))
            assertEquals(Revision(3), detached.revision)
            assertEquals(detached, ok(reader.load(id, token)))
            ok(profiles.remove(id, detached.revision, token))
            missing(reader.load(id, token))
            assertTrue(ok(reader.list(PageRequest(10), token)).items.isEmpty())
            stale(profiles.create(profile, token))
        }
        // Reopen after the independent handle closed: the durable tombstone still reserves identity.
        ok(StorageDatabase.open(context, AppGraph.DATABASE_FILE)).use { db ->
            val reader = SqlLocalProviderProfiles(db, checkNotNull(ports.utcClock), vault)
            missing(reader.load(id, token))
            stale(reader.create(profile, token))
            val tombstone = ok(db.query(SqlRequest("SELECT revision,change_sequence,deleted,payload_length,payload FROM provider_profile WHERE profile_id='room-provider'",
                SnapshotList.empty(), 1), token)).single().columns.map { it.value }
            assertEquals(listOf(SqlValue.Integer(4), SqlValue.Integer(4), SqlValue.Integer(1), SqlValue.Integer(0), SqlValue.Null), tombstone)
        }
        driver.open(file.path).use { connection ->
            assertEquals(listOf(listOf(SqlValue.Integer(18))), connection.rows("PRAGMA user_version"))
            assertEquals(listOf(listOf(SqlValue.Text(current.getString("identityHash")))), connection.rows(IDENTITY))
            val afterObjects = connection.rows(OBJECTS)
            assertEquals(beforeObjects, afterObjects.filter { it[1] !in ADDED_OBJECT_NAMES })
            assertEquals(ADDED_OBJECT_NAMES, afterObjects.filter { it !in beforeObjects }.map { it[1] }.toSet())
            assertEquals(beforeData, LEGACY.map { connection.rows(it) })
            assertTrue(connection.rows("PRAGMA foreign_key_check").isEmpty())
        }
    }

    /** Bounded known Gradle working directories; a missing actual export fails instead of substituting SQL. */
    private fun schema(version: Int): JSONObject {
        val relative = "data/storage/schemas/com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase/$version.json"
        val candidates = listOf(File(relative), File("../$relative"), File("android-app/$relative"))
        val file = checkNotNull(candidates.firstOrNull { it.isFile }) { "Missing actual KSP schema$version export from ${File(".").absolutePath}" }
        return JSONObject(file.readText()).getJSONObject("database")
    }
    private fun SQLiteConnection.exec(sql: String) { prepare(sql).use { it.step() } }
    private fun SQLiteConnection.rows(sql: String): List<List<SqlValue>> = prepare(sql).use { statement ->
        buildList {
            while (statement.step()) add((0 until statement.getColumnCount()).map { column ->
                when (statement.getColumnType(column)) {
                    1 -> SqlValue.Integer(statement.getLong(column))
                    3 -> SqlValue.Text(statement.getText(column))
                    4 -> SqlValue.Blob(SnapshotBytes(statement.getBlob(column)))
                    5 -> SqlValue.Null
                    else -> error("Unexpected physical fixture column type")
                }
            })
        }
    }
    private companion object {
        const val OBJECTS = "SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name<>'room_master_table' ORDER BY type,name"
        const val IDENTITY = "SELECT identity_hash FROM room_master_table WHERE id=42"
        val ADDED_OBJECT_NAMES = setOf(SqlValue.Text("provider_profile"), SqlValue.Text("index_provider_profile_change_sequence"), SqlValue.Text("prompt_override"), SqlValue.Text("ai_conversation"), SqlValue.Text("index_ai_conversation_change_sequence"), SqlValue.Text("index_ai_conversation_deck"), SqlValue.Text("ai_turn"), SqlValue.Text("index_ai_turn_conversation_ordinal"), SqlValue.Text("index_ai_turn_recovery"))
        val LEGACY = listOf("SELECT * FROM life_profile ORDER BY profile_id", "SELECT * FROM deck_head ORDER BY provider,deck_id",
            "SELECT * FROM deck_revision ORDER BY provider,deck_id,revision", "SELECT * FROM deck_operation ORDER BY sequence")
    }
}
