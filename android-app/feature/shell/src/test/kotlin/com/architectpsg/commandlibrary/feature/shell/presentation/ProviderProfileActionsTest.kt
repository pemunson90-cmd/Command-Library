@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.SyntheticSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

class ProviderProfileActionsTest {
    @Test fun attachFailurePreventsVaultPut() = runTest {
        val f = ProfileFixture(); f.seed(); f.replaceResult = profileFailure(FailureCode.PERSISTENCE)
        assertTrue(f.actions().putKey(f.id, Revision(1), "fixture", f.cancel) is Outcome.Failure)
        assertEquals(0, f.puts); assertNull(f.rows[f.id.value]!!.profile.secretRef)
    }
    @Test fun failedAndCancelledPutKeepRecoverableReference() = runTest {
        for (cancelled in listOf(false, true)) {
            val f = ProfileFixture(); f.seed()
            f.putResult = profileFailure(FailureCode.PERSISTENCE); f.cancelPut = cancelled
            try { f.actions().putKey(f.id, Revision(1), "fixture", f.cancel) } catch (_: CancellationException) { }
            assertNotNull(f.rows[f.id.value]!!.profile.secretRef); assertEquals(Revision(2), f.rows[f.id.value]!!.revision)
        }
    }
    @Test fun failedAndCancelledRemoveNeverDetach() = runTest {
        for (cancelled in listOf(false, true)) {
            val f = ProfileFixture(); f.seed(attached = true)
            f.removeResult = profileFailure(FailureCode.PERMISSION); f.cancelRemove = cancelled
            try { f.actions().removeKey(f.id, Revision(1), f.cancel) } catch (_: CancellationException) { }
            assertNotNull(f.rows[f.id.value]!!.profile.secretRef); assertEquals(0, f.replaces)
        }
    }
    @Test fun staleOrFailedDetachKeepsReferenceAfterVaultRemoval() = runTest {
        for (code in listOf(FailureCode.STALE_REVISION, FailureCode.PERSISTENCE)) {
            val f = ProfileFixture(); f.seed(attached = true); f.replaceResult = profileFailure(code)
            assertTrue(f.actions().removeKey(f.id, Revision(1), f.cancel) is Outcome.Failure)
            assertEquals(1, f.removes); assertNotNull(f.rows[f.id.value]!!.profile.secretRef)
        }
    }
    @Test fun twoInstancesSerializeAndQueuedStaleWriteNeverReachesVault() = runTest {
        val f = ProfileFixture(); f.seed(); f.putGate = CompletableDeferred()
        val first = async { f.actions().putKey(f.id, Revision(1), "fixture", f.cancel) }
        runCurrent()
        val second = async { f.actions().putKey(f.id, Revision(1), "fixture", f.cancel) }
        runCurrent(); assertEquals(1, f.puts)
        f.putGate!!.complete(Unit)
        first.await(); val stale = second.await() as Outcome.Failure
        assertEquals(FailureCode.STALE_REVISION, stale.error.code); assertEquals(1, f.puts)
    }
    @Test fun ordinarySaveAndDeleteCannotDetachReference() = runTest {
        val f = ProfileFixture(); val row = f.seed(attached = true)
        val forged = row.profile.copy(secretRef = null, endpoint = row.profile.endpoint!!.copy(secret = null))
        assertTrue(f.actions().save(forged, row.revision, f.cancel) is Outcome.Failure)
        assertTrue(f.actions().delete(f.id, row.revision, f.cancel) is Outcome.Failure)
        assertEquals(0, f.replaces); assertEquals(0, f.deletes)
    }
    @Test fun exhaustedSettingsRetriesReportWarningWithoutUndoingProfileOrTheme() = runTest {
        val f = ProfileFixture(); f.settingsFailure = true
        val result = f.actions().save(f.profile(), null, f.cancel) as Outcome.Success
        assertTrue(result.value.settingsWarning); assertEquals(3, f.settingsUpdates)
        assertEquals(ThemePreference.SYSTEM, f.settings.stored.theme); assertNotNull(f.rows[f.id.value])
    }
    @Test fun cancelledSettingsReconciliationDoesNotRollBackCommittedCreate() = runTest {
        val f = ProfileFixture(); f.cancelSettings = true
        try { f.actions().save(f.profile(), null, f.cancel); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertNotNull(f.rows[f.id.value]); assertEquals(1, f.creates)
    }
}

internal fun profileFailure(code: FailureCode) = Outcome.Failure(DomainFailure(code, "Fixture refusal", SnapshotList.empty()))
/** Fault-injecting ports only for presentation tests; no production adapter substitutes. */
internal class ProfileFixture : LocalProviderProfiles, SecretVault {
    val id = EntityId("profile-a")
    val host = Origin("https", "example.test", 443)
    val cancel = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() {} }
    val rows = sortedMapOf<String, StoredProviderProfile>()
    val settings = SyntheticSettings()
    var settingsFailure = false; var cancelSettings = false; var settingsUpdates = 0
    var creates = 0; var replaces = 0; var deletes = 0; var puts = 0; var removes = 0; var loads = 0
    var replaceResult: Outcome<StoredProviderProfile>? = null
    var createResult: Outcome<StoredProviderProfile>? = null
    var commitBeforeCreateResult = false
    var putResult: Outcome<SecretRef>? = null
    var removeResult: Outcome<Unit>? = null
    var loadGate: CompletableDeferred<Unit>? = null
    var putGate: CompletableDeferred<Unit>? = null
    var cancelPut = false; var cancelRemove = false
    val cursors = mutableListOf<String?>()
    private var ids = SnapshotList.empty<EntityId>()
    fun actions() = ProviderProfileActions(this, this, object : SettingsRepository by settings {
        override suspend fun read(cancel: CancelToken): Outcome<SettingsSnapshot> = Outcome.Success(settings.stored.copy(providerProfileIds = ids))
        override suspend fun update(patch: SettingsPatch, expected: Revision, cancel: CancelToken): Outcome<SettingsSnapshot> {
            settingsUpdates++
            if (cancelSettings) throw CancellationException()
            if (settingsFailure) return profileFailure(FailureCode.STALE_REVISION)
            ids = (patch.changes.single() as SettingChange.ProviderProfiles).ids
            return Outcome.Success(settings.stored.copy(providerProfileIds = ids))
        }
    })
    fun profile(key: EntityId = id): ProviderProfile = ProviderProfile(key, "Fixture", ProviderDialect.OPENAI_CHAT,
        EndpointProfile(key, host, SnapshotList.empty(), null, 1, RetryPolicy(1, Duration.ZERO, Duration.ZERO, false)),
        "manual-model", ProviderCapabilities(false, false, false, false, false, false), null,
        Knowledge.Absent("Unspecified"), SnapshotList.empty(), ResourceUri("https://example.test/v1"))
    fun seed(attached: Boolean = false, key: EntityId = id): StoredProviderProfile {
        var p = profile(key)
        if (attached) { val ref = (reference(SecretScope(key, host, "provider-auth-v1")) as Outcome.Success).value; p = p.copy(secretRef = ref, endpoint = p.endpoint!!.copy(secret = ref)) }
        return StoredProviderProfile(p, Revision(1), UtcTimestamp(1), UtcTimestamp(1)).also { rows[key.value] = it }
    }
    override suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<StoredProviderProfile>> {
        cursors += page.cursor
        val start = page.cursor?.toInt() ?: 0
        val values = rows.values.toList().drop(start).take(page.limit)
        return Outcome.Success(Page(SnapshotList.copyOf(values), if (start + values.size < rows.size) (start + values.size).toString() else null,
            SnapshotReference(ProviderId("fixture"), null, Revision(1), null)))
    }
    override suspend fun load(id: EntityId, cancel: CancelToken): Outcome<StoredProviderProfile> {
        loads++; loadGate?.await()
        return rows[id.value]?.let { Outcome.Success(it) } ?: Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Absent")
    }
    override suspend fun create(profile: ProviderProfile, cancel: CancelToken): Outcome<StoredProviderProfile> {
        creates++
        if (createResult != null && !commitBeforeCreateResult) return createResult!!
        if (rows.containsKey(profile.id.value)) return profileFailure(FailureCode.STALE_REVISION)
        val row = StoredProviderProfile(profile, Revision(1), UtcTimestamp(1), UtcTimestamp(1)); rows[profile.id.value] = row
        return createResult ?: Outcome.Success(row)
    }
    override suspend fun replace(profile: ProviderProfile, expected: Revision, cancel: CancelToken): Outcome<StoredProviderProfile> {
        replaces++; replaceResult?.let { return it }
        val old = rows[profile.id.value] ?: return Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Absent")
        if (old.revision != expected) return profileFailure(FailureCode.STALE_REVISION)
        return Outcome.Success(old.copy(profile = profile, revision = expected.next()).also { rows[profile.id.value] = it })
    }
    override suspend fun remove(id: EntityId, expected: Revision, cancel: CancelToken): Outcome<Unit> { deletes++; rows.remove(id.value); return Outcome.Success(Unit) }
    override fun reference(scope: SecretScope): Outcome<SecretRef> = Outcome.Success(SecretRef(EntityId("ref-${scope.providerProfileId.value}"), scope))
    override suspend fun put(scope: SecretScope, value: String, cancel: CancelToken): Outcome<SecretRef> {
        puts++; putGate?.await(); if (cancelPut) throw CancellationException()
        return putResult ?: reference(scope)
    }
    override suspend fun read(ref: SecretRef, cancel: CancelToken): Outcome<String> = Outcome.Unavailable(UnavailableReason.KEY_MISSING, "Not read by presentation")
    override suspend fun remove(ref: SecretRef, cancel: CancelToken): Outcome<Unit> {
        removes++; if (cancelRemove) throw CancellationException()
        return removeResult ?: Outcome.Success(Unit)
    }
}
