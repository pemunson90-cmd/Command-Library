package com.architectpsg.commandlibrary.feature.catalog

import android.content.Context
import android.content.ContextWrapper
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.common.ConsentCoordinator
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.feature.catalog.api.AndroidCatalogServices
import com.architectpsg.commandlibrary.feature.settings.DataStoreSettingsRepository
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private typealias FactoryAuthorizer = suspend (RequestSpec, ResourceUri, CancelToken, () -> Unit) -> Unit

/** Construction and admission tests; all stores are real, all transport responses are synthetic. */
@RunWith(AndroidJUnit4::class)
class AndroidCatalogServicesTest {
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val token = BULK_NO_CANCEL
    private class PrivateFilesContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = root
    }
    private class SyntheticClient : NetworkClient {
        var closes = 0
        var closeError: IOException? = null
        var beforeClose: suspend () -> Unit = {}
        override suspend fun send(request: RequestSpec, cancel: CancelToken): Outcome<ByteResponse> = error("Unexpected network send")
        override fun stream(request: RequestSpec, cancel: CancelToken): Flow<Outcome<ByteChunk>> = flow { error("Unexpected network stream") }
        override suspend fun close() { beforeClose(); closes++; closeError?.let { throw it } }
    }
    private fun <T> value(result: Outcome<T>): T {
        assertTrue("Expected success: $result", result is Outcome.Success)
        return (result as Outcome.Success).value
    }
    private fun requestSpec(uri: ResourceUri): RequestSpec = RequestSpec(
        EndpointProfile(EntityId("synthetic-test-endpoint"), Origin("https", "api.scryfall.com", 443),
            SnapshotList.empty(), null, 1, RetryPolicy(1, Duration.ZERO, Duration.ZERO, true)),
        Request(HttpMethod.GET, uri, SnapshotList.empty(), null), 1.seconds,
        RequestCharge.NONCHARGED, RetryClassification.NEVER_AUTOMATIC, null, 1024,
    )
    private suspend fun denied(hook: FactoryAuthorizer, uri: ResourceUri) {
        var enqueued = false
        var rejected = false
        try { hook(requestSpec(uri), uri, token) { enqueued = true } }
        catch (_: Exception) { rejected = true }
        assertTrue("Unregistered or closed authorizer accepted a request", rejected)
        assertFalse("Rejected request reached enqueue", enqueued)
    }

    private inner class Fixture(
        val context: PrivateFilesContext, val db: StorageDatabase,
        val settings: DataStoreSettingsRepository, val gate: ConsentCoordinator,
    ) {
        private val clock = object : Clock {
            override fun utcNow() = UtcTimestamp(1000)
            override fun monotonicNow(): Duration = Duration.ZERO
        }
        private val ids = object : IdSource { override fun nextId() = UUID.randomUUID().toString() }
        suspend fun create(builder: (FactoryAuthorizer) -> NetworkClient): AndroidCatalogServices =
            AndroidCatalogServices.create(context, db, settings, gate, clock, ids, builder)
    }
    private suspend fun fixture(action: suspend (Fixture) -> Unit) = withContext(Dispatchers.IO) {
        val uuid = UUID.randomUUID()
        val root = File(base.filesDir, "factory-test-$uuid").also { assertTrue(it.mkdir()) }
        val context = PrivateFilesContext(base, root)
        val store = File(root, "settings")
        val installation = File(base.noBackupFilesDir, "factory-test-installation-$uuid")
        val name = "factory-test-$uuid.db"
        val gate = ConsentCoordinator()
        val settings = DataStoreSettingsRepository.create(store, installation, gate)
        val db = value(StorageDatabase.open(context, name))
        try { action(Fixture(context, db, settings, gate)) }
        finally { withContext(NonCancellable) {
            settings.close(); db.close(); base.deleteDatabase(name)
            // A test-created final symlink is removed itself before recursive directory cleanup.
            File(root, "acquisition-artifacts").delete()
            root.deleteRecursively(); installation.delete()
        } }
    }

    @Test fun canonicalPlanPersistsAndForgedProfileAndUnregisteredHookReject(): Unit = runBlocking {
        fixture { f ->
            val client = SyntheticClient()
            var builders = 0
            lateinit var hook: FactoryAuthorizer
            val services = f.create { supplied -> builders++; hook = supplied; client }
            try {
                assertEquals(1, builders)
                assertSame(services.downloads, services.runner)
                assertNotNull(services.bulkImport); assertNotNull(services.printingLookup); assertNotNull(services.oracleLookup)
                val canonical = services.englishSearchRequest("Synthetic English catalog", false)
                val planned = value(services.downloads.plan(canonical, token))
                assertEquals(canonical, planned.plan.request)
                assertEquals(canonical, value(services.downloads.status(planned.plan.jobId, token)).plan.request)
                val forged = canonical.copy(sourceVersion = Knowledge.Known("forged"))
                val deniedPlan = services.downloads.plan(forged, token)
                assertEquals(FailureCode.INVALID_INPUT, (deniedPlan as Outcome.Failure).error.code)
                denied(hook, canonical.intent.resource)
            } finally { services.close() }
            assertEquals(1, client.closes)
            denied(hook, ResourceUri(EnglishSearchPageCodec.INITIAL_URI))
        }
    }

    @Test fun swallowedPrematureAuthorizationFailsCreationAndPreservesDistinctCloseFailure(): Unit = runBlocking {
        fixture { f ->
            val client = SyntheticClient().also { it.closeError = IOException("synthetic close failure") }
            lateinit var hook: FactoryAuthorizer
            var attempted = false
            val thrown = try {
                f.create { supplied ->
                    hook = supplied
                    try { runBlocking { supplied(requestSpec(ResourceUri(EnglishSearchPageCodec.INITIAL_URI)),
                        ResourceUri(EnglishSearchPageCodec.INITIAL_URI), token) { error("Premature enqueue") } } }
                    catch (_: Exception) { attempted = true }
                    client
                }
                fail("Premature authorization was swallowed by builder")
                null
            } catch (error: Throwable) { error }
            assertTrue(attempted)
            assertEquals(1, client.closes)
            assertNotNull(thrown)
            assertNotSame(client.closeError, thrown)
            assertTrue(thrown!!.suppressed.any { it === client.closeError })
            denied(hook, ResourceUri(EnglishSearchPageCodec.INITIAL_URI))
        }
    }

    @Test fun cancelledCreationAfterBuilderReturnsClosesOwnedClient(): Unit = runBlocking {
        fixture { f ->
            val client = SyntheticClient()
            var returnedClient = false
            lateinit var hook: FactoryAuthorizer
            val owner = launch(start = CoroutineStart.LAZY) {
                val creationJob = currentCoroutineContext().job
                try {
                    f.create { supplied ->
                        hook = supplied
                        returnedClient = true
                        creationJob.cancel(CancellationException("Synthetic creation cancellation"))
                        client
                    }
                    fail("Cancelled creation returned a facade")
                } catch (_: CancellationException) { }
            }
            owner.start(); owner.join()
            assertTrue("Builder was never reached", returnedClient)
            assertEquals(1, client.closes)
            denied(hook, ResourceUri(EnglishSearchPageCodec.INITIAL_URI))
        }
    }

    @Test fun closingFromCancelledOwnerFinishesSuspendingTransportCleanup(): Unit = runBlocking {
        fixture { f ->
            val client = SyntheticClient().also { it.beforeClose = { delay(1) } }
            lateinit var hook: FactoryAuthorizer
            val services = f.create { supplied -> hook = supplied; client }
            val owner = launch(start = CoroutineStart.LAZY) {
                currentCoroutineContext().job.cancel(CancellationException("Owner stopped"))
                services.close()
            }
            owner.start(); owner.join()
            assertEquals(1, client.closes)
            denied(hook, ResourceUri(EnglishSearchPageCodec.INITIAL_URI))
        }
    }

    @Test fun existingSymlinkAtFixedPrivateChildRejectsBeforeTransportBuilder(): Unit = runBlocking {
        fixture { f ->
            val child = File(f.context.filesDir, "acquisition-artifacts")
            val outside = File(base.filesDir, "factory-test-outside-${UUID.randomUUID()}").also { assertTrue(it.mkdir()) }
            try {
                Os.symlink(outside.path, child.path)
                var builderCalls = 0
                var rejected = false
                try { f.create { builderCalls++; SyntheticClient() } }
                catch (_: Exception) { rejected = true }
                assertTrue("Symlink child was accepted", rejected)
                assertEquals(0, builderCalls)
                assertTrue(OsConstants.S_ISLNK(Os.lstat(child.path).st_mode))
            } finally { child.delete(); outside.deleteRecursively() }
        }
    }
}
