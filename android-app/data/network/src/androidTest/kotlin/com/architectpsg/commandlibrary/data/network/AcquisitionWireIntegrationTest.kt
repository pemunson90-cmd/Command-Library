package com.architectpsg.commandlibrary.data.network

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.common.ConsentCoordinator
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.feature.catalog.api.AndroidCatalogServices
import com.architectpsg.commandlibrary.feature.settings.DataStoreSettingsRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.toList
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest
import okhttp3.Response
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private typealias FactoryAuthorizer = suspend (RequestSpec, ResourceUri, CancelToken, () -> Unit) -> Unit
private typealias GuardProbe = suspend (FactoryAuthorizer, RequestSpec, ResourceUri, CancelToken) -> Unit

/** Real factory, settings, Room, OkHttp enqueue and loopback HTTP; URL rewriting is synthetic. */
@RunWith(AndroidJUnit4::class)
class AcquisitionWireIntegrationTest {
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val token = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }

    private class PrivateFilesContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = root
    }

    private class DeviceClock : Clock {
        override fun utcNow(): UtcTimestamp {
            val millis = System.currentTimeMillis()
            val seconds = millis / 1000
            val remainder = millis % 1000
            return if (remainder < 0) UtcTimestamp(seconds - 1, ((remainder + 1000) * 1_000_000).toInt())
            else UtcTimestamp(seconds, (remainder * 1_000_000).toInt())
        }
        override fun monotonicNow(): Duration = SystemClock.elapsedRealtime().milliseconds
    }

    /** A real socket accepts one request, sends only 429 headers, then observes peer closure. */
    private class HeaderOnly429 : AutoCloseable {
        private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val received = CountDownLatch(1)
        private val closed = CountDownLatch(1)
        private val running = AtomicBoolean(true)
        private val thread = Thread({
            var socket: Socket? = null
            try {
                socket = listener.accept()
                socket.soTimeout = 4_000
                val input = socket.getInputStream()
                val header = ByteArrayOutputStream()
                while (header.size() < 16_384) {
                    val next = input.read()
                    if (next < 0) break
                    header.write(next)
                    val bytes = header.toByteArray()
                    if (bytes.size >= 4 && bytes.takeLast(4) == listOf(13.toByte(), 10.toByte(), 13.toByte(), 10.toByte())) break
                }
                requestHeaders = header.toString("US-ASCII")
                requests.incrementAndGet()
                received.countDown()
                val output = socket.getOutputStream()
                output.write(("HTTP/1.1 429 Too Many Requests\r\n" +
                    "Retry-After: 9999999999999999999999999999\r\n" +
                    "Content-Length: 1000000\r\nConnection: keep-alive\r\n\r\n").toByteArray(Charsets.US_ASCII))
                output.flush()
                try { if (input.read() == -1) closed.countDown() }
                catch (_: SocketException) { closed.countDown() }
            } catch (_: SocketTimeoutException) {
                // The assertion reports a missing physical close with a bounded deadline.
            } catch (_: SocketException) {
                if (requests.get() > 0) closed.countDown()
            } finally { runCatching { socket?.close() } }
        }, "synthetic-acquisition-429").apply { isDaemon = true; start() }
        val port: Int get() = listener.localPort
        val requests = AtomicInteger()
        @Volatile var requestHeaders: String = ""
            private set
        fun awaitRequest(): Boolean = received.await(5, TimeUnit.SECONDS)
        fun awaitClose(): Boolean = closed.await(5, TimeUnit.SECONDS)
        override fun close() {
            if (running.compareAndSet(true, false)) {
                listener.close()
                thread.join(5_000)
            }
        }
    }

    /** Only the Call's network destination changes. The response retains logical HTTPS identity. */
    private class LogicalCall(
        private val logical: OkRequest,
        private val wire: Call,
        private val onEnqueue: () -> Unit,
    ) : Call by wire {
        override fun request(): OkRequest = logical
        override fun execute(): Response = wire.execute().newBuilder().request(logical).build()
        override fun enqueue(responseCallback: Callback) {
            onEnqueue()
            wire.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) = responseCallback.onFailure(this@LogicalCall, e)
                override fun onResponse(call: Call, response: Response) =
                    responseCallback.onResponse(this@LogicalCall, response.newBuilder().request(logical).build())
            })
        }
        override fun clone(): Call = LogicalCall(logical, wire.clone(), onEnqueue)
    }

    private inner class Fixture(
        val context: PrivateFilesContext,
        var database: StorageDatabase,
        val databaseName: String,
        val settings: DataStoreSettingsRepository,
        val gate: ConsentCoordinator,
        val server: HeaderOnly429,
    ) {
        val clock = DeviceClock()
        val physicalEnqueues = AtomicInteger()
        private val ids = object : IdSource { override fun nextId(): String = UUID.randomUUID().toString() }
        private val clients = mutableListOf<OkHttpNetworkClient>()
        private val rewriteClients = mutableListOf<OkHttpClient>()

        suspend fun create(probe: GuardProbe = { _, _, _, _ -> }): AndroidCatalogServices =
            AndroidCatalogServices.create(context, database, settings, gate, clock, ids, { authorizer ->
                val rewrite = OkHttpClient.Builder().retryOnConnectionFailure(false)
                    .followRedirects(false).followSslRedirects(false).build()
                rewriteClients += rewrite
                OkHttpNetworkClient(clock, readTimeout = 5.seconds,
                    dispatchGuard = WireDispatchGuard { spec, uri, cancel, enqueue ->
                        probe(authorizer, spec, uri, cancel)
                        authorizer(spec, uri, cancel, enqueue)
                    }, exposeAcquisition429Metadata = true).also { client ->
                    client.callFactoryForTests = { logical ->
                        val changed = logical.newBuilder().url("http://127.0.0.1:${server.port}/").build()
                        LogicalCall(logical, rewrite.newCall(changed)) { physicalEnqueues.incrementAndGet() }
                    }
                    clients += client
                }
            })

        suspend fun reopenDatabase() {
            database.close()
            database = value(StorageDatabase.open(context, databaseName))
        }

        fun latestClient(): OkHttpNetworkClient? = clients.lastOrNull()

        suspend fun cleanup() {
            clients.forEach { try { it.close() } catch (_: Exception) { } }
            rewriteClients.forEach { it.dispatcher.executorService.shutdownNow(); it.connectionPool.evictAll() }
            server.close()
        }
    }

    private suspend fun fixture(action: suspend (Fixture) -> Unit) = withContext(Dispatchers.IO) {
        val uuid = UUID.randomUUID()
        val root = File(base.filesDir, "real-wire-$uuid").also { assertTrue(it.mkdir()) }
        val context = PrivateFilesContext(base, root)
        val installation = File(base.noBackupFilesDir, "real-wire-installation-$uuid")
        val name = "real-wire-$uuid.db"
        val gate = ConsentCoordinator()
        val settings = DataStoreSettingsRepository.create(File(root, "settings"), installation, gate)
        var database: StorageDatabase? = null
        var server: HeaderOnly429? = null
        var fixture: Fixture? = null
        try {
            val opened = value(StorageDatabase.open(context, name))
            database = opened
            val loopback = HeaderOnly429()
            server = loopback
            val ready = Fixture(context, opened, name, settings, gate, loopback)
            fixture = ready
            action(ready)
        } finally { withContext(NonCancellable) {
            if (fixture == null) server?.close() else fixture.cleanup()
            settings.close()
            (fixture?.database ?: database)?.close()
            base.deleteDatabase(name)
            root.deleteRecursively()
            installation.delete()
        } }
    }

    private fun <T> value(result: Outcome<T>): T {
        assertTrue("Expected success: $result", result is Outcome.Success)
        return (result as Outcome.Success).value
    }

    private fun assertValidatedConnection(context: Context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = try { if (manager == null) null else manager.activeNetwork?.let(manager::getNetworkCapabilities) }
            catch (_: SecurityException) { null }
        assertTrue("Native wire fixture requires an actual validated device network; factory admission fails closed without one",
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
    }

    private fun directSpec(uri: ResourceUri) = RequestSpec(
        EndpointProfile(EntityId("synthetic-unregistered"), Origin("https", "api.scryfall.com", 443),
            SnapshotList.empty(), null, 2, RetryPolicy(1, Duration.ZERO, Duration.ZERO, true)),
        com.architectpsg.commandlibrary.core.model.Request(HttpMethod.GET, uri, SnapshotList.empty(), null),
        5.seconds, RequestCharge.NONCHARGED, RetryClassification.NEVER_AUTOMATIC, null, 1024,
    )

    private suspend fun rows(db: StorageDatabase): SnapshotList<RowData> =
        value(db.query(SqlRequest("""SELECT revision,interval_seconds,interval_nano
            FROM acquisition_origin_cooldown WHERE origin=:origin""",
            SnapshotList.of(SqlParameter("origin", SqlValue.Text("https://api.scryfall.com:443"))), 1), token))

    @Test fun unregisteredDirectTransportSpecNeverEnqueues(): Unit = runBlocking {
        withTimeout(20_000) { fixture { f ->
            val services = f.create()
            try {
                val canonical = services.englishSearchRequest("Synthetic unregistered", false)
                val client = requireNotNull(f.latestClient())
                val result = client.stream(directSpec(canonical.intent.resource), token).toList()
                assertEquals(1, result.size)
                assertEquals(FailureCode.TRANSPORT, (result.single() as Outcome.Failure).error.code)
                assertEquals(0, f.physicalEnqueues.get())
                assertEquals(0, f.server.requests.get())
            } finally { services.close() }
        } }
    }

    @Test fun registeredOriginalWireAndCopiedSpecDenialPersistInfinite429AcrossReopen(): Unit = runBlocking {
        withTimeout(30_000) { fixture { f ->
            assertValidatedConnection(f.context)
            val copiedRejected = AtomicInteger()
            val copiedEnqueues = AtomicInteger()
            val services = f.create { authorizer, spec, uri, cancel ->
                var rejected = false
                try { authorizer(spec.copy(), uri, cancel) { copiedEnqueues.incrementAndGet() } }
                catch (_: Exception) { rejected = true }
                if (rejected) copiedRejected.incrementAndGet()
            }
            lateinit var first: AcquisitionSnapshot
            var consentId: EntityId? = null
            try {
                val request = services.englishSearchRequest("Synthetic 429", false)
                val planned = value(services.downloads.plan(request, token))
                consentId = value(f.settings.approve(request.intent, token)).id
                val queued = value(services.downloads.start(planned.plan.jobId, planned.revision, requireNotNull(consentId), token))
                first = value(services.runner.run(queued.plan.jobId, token))
                assertEquals(AcquisitionState.FAILED, first.state)
                assertEquals(FailureCode.TRANSPORT, first.failure?.code)
                assertEquals(1, first.attempt)
                assertTrue(first.transferredBytes > 0)
                assertEquals(0L, first.durableArtifactBytes)
                assertFalse(first.artifact is Knowledge.Known)
                assertEquals(1, copiedRejected.get())
                assertEquals(0, copiedEnqueues.get())
                assertEquals(1, f.physicalEnqueues.get())
                assertTrue("Synthetic HTTP request was not received", f.server.awaitRequest())
                assertEquals(1, f.server.requests.get())
                assertTrue(f.server.requestHeaders.startsWith("GET / HTTP/1.1"))
                assertTrue("429 declared body did not close at transport", f.server.awaitClose())
                val durable = rows(f.database).single()
                assertEquals(1L, (durable.columns.single { it.name == "revision" }.value as SqlValue.Integer).value)
                assertEquals(SqlValue.Null, durable.columns.single { it.name == "interval_seconds" }.value)
                assertEquals(SqlValue.Null, durable.columns.single { it.name == "interval_nano" }.value)
            } finally { services.close() }

            f.reopenDatabase()
            val restarted = f.create()
            try {
                repeat(2) {
                    val blocked = restarted.downloads.resume(first.plan.jobId, first.revision, requireNotNull(consentId), token)
                    assertEquals(FailureCode.TRANSPORT, (blocked as Outcome.Failure).error.code)
                    assertEquals(first, value(restarted.downloads.status(first.plan.jobId, token)))
                    assertEquals(1, f.physicalEnqueues.get())
                    assertEquals(1, f.server.requests.get())
                }
            } finally { restarted.close() }
        } }
    }

    @Test fun persistedConsentRevokedBeforeAuthorizerLeavesWaitingConsentAndNoWire(): Unit = runBlocking {
        withTimeout(20_000) { fixture { f ->
            assertValidatedConnection(f.context)
            var revoke: EntityId? = null
            val revocations = AtomicInteger()
            val services = f.create { _, _, _, _ ->
                val id = requireNotNull(revoke)
                if (revocations.compareAndSet(0, 1)) {
                    val current = value(f.settings.read(token))
                    value(f.settings.update(SettingsPatch(SnapshotList.of(SettingChange.RevokeConsent(id, false))),
                        current.revision, token))
                }
            }
            try {
                val request = services.englishSearchRequest("Synthetic revoked", false)
                val planned = value(services.downloads.plan(request, token))
                val approved = value(f.settings.approve(request.intent, token))
                val queued = value(services.downloads.start(planned.plan.jobId, planned.revision, approved.id, token))
                revoke = approved.id
                val stopped = value(services.runner.run(queued.plan.jobId, token))
                assertEquals(1, revocations.get())
                assertEquals(AcquisitionState.WAITING_CONSENT, stopped.state)
                assertEquals(AcquisitionState.WAITING_CONSENT,
                    value(services.downloads.status(stopped.plan.jobId, token)).state)
                assertEquals(0, f.physicalEnqueues.get())
                assertEquals(0, f.server.requests.get())
                val stored = value(f.settings.read(token))
                assertTrue(stored.consents.none { it.id == approved.id && it.revokedAt == null })
            } finally { services.close() }
        } }
    }
}
