package com.architectpsg.commandlibrary.data.storage.ai

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.HostLifeDatabase
import com.architectpsg.commandlibrary.data.storage.schema.Migration15To16
import com.architectpsg.commandlibrary.feature.ai.SqlLocalProviderProfiles
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Actual bundled SQLite via the existing test adapter, not Android Room or native-vault proof. */
class SqlLocalProviderProfilesTest {
    @get:Rule val folder = TemporaryFolder()
    private val token = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private class Time : Clock {
        var calls = 0
        override fun utcNow(): UtcTimestamp = UtcTimestamp(1000L + calls++, 23)
        override fun monotonicNow(): Duration = Duration.ZERO
    }
    /** Deterministic synthetic scope references only. Key access always fails the test. */
    private class Vault : SecretVault {
        var suffix = ""
        var references = 0
        override fun reference(scope: SecretScope): Outcome<SecretRef> {
            references++
            return Outcome.Success(SecretRef(EntityId("fixture-${scope.providerProfileId.value}$suffix"), scope))
        }
        override suspend fun put(scope: SecretScope, value: String, cancel: CancelToken): Outcome<SecretRef> = error("Repository must not put keys")
        override suspend fun read(ref: SecretRef, cancel: CancelToken): Outcome<String> = error("Repository must not read keys")
        override suspend fun remove(ref: SecretRef, cancel: CancelToken): Outcome<Unit> = error("Repository must not remove keys")
    }
    private fun profile(id: String = "provider"): ProviderProfile {
        val entity = EntityId(id)
        return ProviderProfile(entity, "Synthetic provider 日本語", ProviderDialect.OPENAI_RESPONSES,
            EndpointProfile(entity, Origin("https", "example.test", 443), SnapshotList.empty(), null, 3,
                RetryPolicy(1, Duration.ZERO, 2.seconds, false)), "manual-model",
            ProviderCapabilities(true, true, false, false, true, false), null,
            Knowledge.Absent("Not supplied"), SnapshotList.empty(), ResourceUri("https://example.test/v1"))
    }
    private fun <T> ok(outcome: Outcome<T>): T {
        assertTrue("Expected success: $outcome", outcome is Outcome.Success)
        return (outcome as Outcome.Success).value
    }
    private fun failed(outcome: Outcome<*>, code: FailureCode) {
        assertTrue("Expected $code: $outcome", outcome is Outcome.Failure)
        assertEquals(code, (outcome as Outcome.Failure).error.code)
    }
    private suspend fun open(file: File): HostLifeDatabase = HostLifeDatabase(file, initialize = false).also {
        Migration15To16.migrate(it.connection)
        it.sql("PRAGMA busy_timeout=3000")
    }
    private fun repo(db: Database, time: Time = Time(), vault: Vault = Vault()) = SqlLocalProviderProfiles(db, time, vault)

    @Test fun migrationPreservesUnrelatedRowsAndCompleteCreateReplaceReopenDeleteReservesId() = runBlocking {
        val file = folder.newFile()
        // Representative schema15 fixture, not a claim to have instantiated the full Room schema15.
        HostLifeDatabase(file, initialize = false).use { old ->
            old.sql("CREATE TABLE unrelated_quantity (id TEXT PRIMARY KEY, quantity INTEGER NOT NULL)")
            old.sql("INSERT INTO unrelated_quantity VALUES ('keep',42)")
            old.sql("PRAGMA user_version=15")
        }
        lateinit var edited: StoredProviderProfile
        open(file).use { db ->
            val time = Time(); val r = repo(db, time)
            val created = ok(r.create(profile(), token))
            assertEquals(Revision(1), created.revision)
            val normalized = created.profile.copy(name = "Edited", selectedModelId = "another-manual-model")
            edited = ok(r.replace(normalized, created.revision, token))
            assertEquals(created.createdAt, edited.createdAt)
            assertEquals(Revision(2), edited.revision)
            assertNotEquals(created.modifiedAt, edited.modifiedAt)
            failed(r.replace(profile(), created.revision, token), FailureCode.STALE_REVISION)
            assertEquals(42L, db.scalar("SELECT quantity FROM unrelated_quantity WHERE id='keep'"))
        }
        HostLifeDatabase(file, initialize = false).use { db ->
            val r = repo(db)
            assertEquals(edited, ok(r.load(edited.profile.id, token)))
            ok(r.remove(edited.profile.id, edited.revision, token))
            assertEquals(UnavailableReason.NOT_FOUND, (r.load(edited.profile.id, token) as Outcome.Unavailable).reason)
            assertEquals(UnavailableReason.NOT_FOUND, (r.remove(edited.profile.id, Revision(3), token) as Outcome.Unavailable).reason)
            failed(r.create(profile(), token), FailureCode.STALE_REVISION)
            assertEquals(1L, db.scalar("SELECT count(*) FROM provider_profile WHERE deleted=1 AND revision=3 AND change_sequence=3 AND payload IS NULL AND payload_length=0 AND payload_version=1"))
            assertTrue(ok(r.list(PageRequest(10), token)).items.isEmpty())
            assertEquals(42L, db.scalar("SELECT quantity FROM unrelated_quantity WHERE id='keep'"))
        }
    }

    @Test fun binaryKeysetHasNoDroppedRowsAndEveryMutationIncludingTombstoneInvalidatesCursor() = runBlocking {
        open(folder.newFile()).use { db ->
            val r = repo(db)
            val ids = listOf("A", "a", "b", "日本")
            ids.reversed().forEach { ok(r.create(profile(it), token)) }
            val first = ok(r.list(PageRequest(2), token))
            assertEquals(listOf("A", "a"), first.items.map { it.profile.id.value })
            val last = ok(r.list(PageRequest(2, first.nextCursor), token))
            assertEquals(listOf("b", "日本"), last.items.map { it.profile.id.value })
            assertNull(last.nextCursor)
            ok(r.remove(EntityId("日本"), Revision(1), token))
            failed(r.list(PageRequest(2, first.nextCursor), token), FailureCode.STALE_REVISION)
            val current = ok(r.list(PageRequest(1), token))
            val old = ok(r.load(EntityId("b"), token))
            ok(r.replace(old.profile.copy(name = "Changed"), old.revision, token))
            failed(r.list(PageRequest(1, current.nextCursor), token), FailureCode.STALE_REVISION)
            failed(r.list(PageRequest(1, "pp1:invalid"), token), FailureCode.INVALID_INPUT)
            assertEquals(4L, db.scalar("SELECT count(*) FROM provider_profile"))
        }
    }

    @Test fun strictMetadataPreflightNeverFetchesOversizedBlobAndDetectsProjectionChecksumAndTypeCorruption() = runBlocking {
        open(folder.newFile()).use { db ->
            val r = repo(db)
            ok(r.create(profile(), token))
            val baseline = db.blobQueries
            db.sql("UPDATE provider_profile SET payload=zeroblob(65537),payload_length=65537")
            failed(r.load(EntityId("provider"), token), FailureCode.CORRUPT_DATA)
            failed(r.list(PageRequest(10), token), FailureCode.CORRUPT_DATA)
            assertEquals(baseline, db.blobQueries)
        }
        for (mutation in listOf("modified_nanos=1000000000", "payload_sha256='bad'", "payload_length='wrong'",
            "profile_id=CAST(zeroblob(257) AS TEXT)", "payload='not-a-blob'", "deleted=2")) {
            open(folder.newFile()).use { db ->
                val r = repo(db); ok(r.create(profile(), token)); val before = db.blobQueries
                db.sql("UPDATE provider_profile SET $mutation")
                // ID corruption remains discoverable through list, not a lookup for the previous ID.
                failed(r.list(PageRequest(10), token), FailureCode.CORRUPT_DATA)
                assertEquals(before, db.blobQueries)
            }
        }
        for (mutation in listOf("revision=2", "modified_seconds=2000", "payload_sha256='" + "0".repeat(64) + "'")) {
            open(folder.newFile()).use { db ->
                val r = repo(db); ok(r.create(profile(), token)); db.sql("UPDATE provider_profile SET $mutation")
                failed(r.load(EntityId("provider"), token), FailureCode.CORRUPT_DATA)
            }
        }
    }

    @Test fun scopedReferenceIsDbFirstAttachableButBlocksOriginChangeAndDeleteUntilExplicitDetach() = runBlocking {
        open(folder.newFile()).use { db ->
            val vault = Vault(); val r = repo(db, vault = vault)
            val created = ok(r.create(profile(), token))
            val scope = SecretScope(created.profile.id, created.profile.endpoint!!.origin, "provider-auth-v1")
            val ref = ok(vault.reference(scope))
            val attachedProfile = created.profile.copy(secretRef = ref, endpoint = created.profile.endpoint!!.copy(secret = ref))
            val attached = ok(r.replace(attachedProfile, created.revision, token))
            assertEquals(attached, ok(r.load(attached.profile.id, token)))
            failed(r.remove(attached.profile.id, attached.revision, token), FailureCode.INVALID_INPUT)
            val changed = profile().copy(baseUri = ResourceUri("https://other.test/v2"),
                endpoint = profile().endpoint!!.copy(origin = Origin("https", "other.test", 443)))
            failed(r.replace(changed, attached.revision, token), FailureCode.INVALID_INPUT)
            // Coordinator owns vault removal. The repository can validate only scope and CAS detach.
            val detached = ok(r.replace(profile(), attached.revision, token))
            val moved = ok(r.replace(changed, detached.revision, token))
            ok(r.remove(moved.profile.id, moved.revision, token))
            assertTrue(vault.references >= 3)
        }
    }

    @Test fun actualVaultIdentityMismatchIsRejectedForInputAndDetectedOnLoad() = runBlocking {
        open(folder.newFile()).use { db ->
            val vault = Vault(); val r = repo(db, vault = vault); val p = profile()
            val scope = SecretScope(p.id, p.endpoint!!.origin, "provider-auth-v1")
            val ref = ok(vault.reference(scope))
            val attached = p.copy(secretRef = ref, endpoint = p.endpoint!!.copy(secret = ref))
            val invalidRef = ref.copy(id = EntityId("wrong-synthetic-ref"))
            failed(r.create(attached.copy(secretRef = invalidRef, endpoint = p.endpoint!!.copy(secret = invalidRef)), token), FailureCode.INVALID_INPUT)
            assertEquals(0L, db.scalar("SELECT count(*) FROM provider_profile"))
            ok(r.create(attached, token))
            vault.suffix = "-changed"
            failed(r.load(p.id, token), FailureCode.CORRUPT_DATA)
            failed(r.list(PageRequest(5), token), FailureCode.CORRUPT_DATA)
        }
    }

    @Test fun canceledWriteRollsBackWhileCancellationAfterCommitRequiresReload() = runBlocking {
        val file = folder.newFile()
        open(file).use { db ->
            var stopped = false
            val cancel = object : CancelToken {
                override val isCancelled get() = stopped
                override fun throwIfCancelled() { if (stopped) throw CancellationException("Synthetic cancellation") }
            }
            db.afterWrite = { stopped = true }
            try { repo(db).create(profile(), cancel); fail("Expected cancellation") } catch (_: CancellationException) { }
            assertEquals(0L, db.scalar("SELECT count(*) FROM provider_profile"))
            db.afterWrite = null
            val committedCancellation = object : Database by db {
                override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
                    val result = db.transaction(action, cancel)
                    if (result is Outcome.Success) throw CancellationException("Synthetic cancellation after adapter commit")
                    return result
                }
            }
            try { repo(committedCancellation).create(profile(), token); fail("Expected cancellation") } catch (_: CancellationException) { }
        }
        HostLifeDatabase(file, initialize = false).use { db ->
            val r = repo(db)
            assertEquals(Revision(1), ok(r.load(EntityId("provider"), token)).revision)
            failed(r.create(profile(), token), FailureCode.STALE_REVISION)
        }
    }

    @Test fun globalSequenceAndRevisionExhaustionRejectBeforeClockOrWrite() = runBlocking {
        open(folder.newFile()).use { db ->
            val time = Time(); val r = repo(db, time)
            val created = ok(r.create(profile(), token)); val calls = time.calls
            db.sql("UPDATE provider_profile SET change_sequence=9223372036854775807")
            failed(r.create(profile("other"), token), FailureCode.INVALID_INPUT)
            failed(r.replace(created.profile, created.revision, token), FailureCode.INVALID_INPUT)
            failed(r.remove(created.profile.id, created.revision, token), FailureCode.INVALID_INPUT)
            assertEquals(calls, time.calls)
            assertEquals(1L, db.scalar("SELECT count(*) FROM provider_profile"))
        }
        open(folder.newFile()).use { db ->
            val time = Time(); val r = repo(db, time); ok(r.create(profile(), token))
            // Rewrite both revision projections coherently, preserving the actual checked payload.
            val payload = db.connection.prepare("SELECT payload FROM provider_profile").use { s -> check(s.step()); s.getBlob(0) }
            val offset = 8 + 4 + "provider".toByteArray(Charsets.UTF_8).size
            java.nio.ByteBuffer.wrap(payload).putLong(offset, Long.MAX_VALUE)
            val hash = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
            db.connection.prepare("UPDATE provider_profile SET revision=9223372036854775807,payload=?,payload_sha256=?").use { s ->
                s.bindBlob(1, payload); s.bindText(2, hash); s.step()
            }
            val calls = time.calls
            failed(r.replace(profile(), Revision(Long.MAX_VALUE), token), FailureCode.INVALID_INPUT)
            failed(r.remove(EntityId("provider"), Revision(Long.MAX_VALUE), token), FailureCode.INVALID_INPUT)
            assertEquals(calls, time.calls)
            assertEquals(Long.MAX_VALUE, db.scalar("SELECT revision FROM provider_profile"))
        }
    }

    @Test fun twoIndependentConnectionsWithSameExpectedRevisionHaveOneWinnerAndOneStale() = runBlocking {
        val file = folder.newFile()
        open(file).use { first ->
            val created = ok(repo(first).create(profile(), token))
            HostLifeDatabase(file, initialize = false).use { second ->
                second.sql("PRAGMA busy_timeout=3000")
                val start = CompletableDeferred<Unit>()
                val outcomes = listOf(first, second).mapIndexed { index, db -> async(Dispatchers.IO) {
                    start.await()
                    repo(db).replace(created.profile.copy(name = "Writer $index"), created.revision, token)
                } }
                start.complete(Unit)
                val results = outcomes.awaitAll()
                assertEquals(1, results.count { it is Outcome.Success })
                assertEquals(1, results.count { it is Outcome.Failure && it.error.code == FailureCode.STALE_REVISION })
                val durable = ok(repo(first).load(created.profile.id, token))
                assertEquals(Revision(2), durable.revision)
                assertEquals(2L, first.scalar("SELECT change_sequence FROM provider_profile"))
            }
        }
    }
}
