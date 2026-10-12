package com.architectpsg.commandlibrary.data.network

import com.architectpsg.commandlibrary.core.model.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Synthetic local fixtures only; these tests make no external network requests. */
class OkHttpNetworkClientTest {
    private class Token : CancelToken { @Volatile var cancelled = false; override val isCancelled get() = cancelled; override fun throwIfCancelled() { if (cancelled) throw CancellationException() } }
    private class TestClock(var now: Duration = Duration.ZERO, val real: Boolean = false) : Clock {
        override fun utcNow() = UtcTimestamp(1_700_000_000)
        override fun monotonicNow() = if (real) System.nanoTime().let { (it / 1_000_000).milliseconds } else now
    }
    private class Fixture : AutoCloseable {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private val executor = Executors.newCachedThreadPool()
        val origin get() = Origin("http", "127.0.0.1", server.address.port)
        init { server.executor = executor; server.start() }
        fun at(path: String, action: (com.sun.net.httpserver.HttpExchange) -> Unit) { server.createContext(path) { exchange -> try { action(exchange) } finally { exchange.close() } } }
        override fun close() { server.stop(0); executor.shutdownNow() }
        fun spec(path: String = "/", limit: Long = 300_000, method: HttpMethod = HttpMethod.GET, attempts: Int = 1, headers: List<HttpHeader> = emptyList(), redirects: List<Origin> = emptyList(), charge: RequestCharge = RequestCharge.NONCHARGED) = RequestSpec(
            EndpointProfile(EntityId("fixture"), origin, SnapshotList.copyOf(redirects), null, 8, RetryPolicy(attempts, 10.milliseconds, 60.seconds, true)),
            Request(method, ResourceUri("http://127.0.0.1:${server.address.port}$path"), SnapshotList.copyOf(headers), null), 5.seconds, charge,
            if (charge == RequestCharge.NONCHARGED) RetryClassification.SAFE_IDEMPOTENT else RetryClassification.NEVER_AUTOMATIC, null, limit)
    }
    private fun client(clock: TestClock = TestClock(), pauses: MutableList<Duration> = mutableListOf()) = OkHttpNetworkClient(clock, allowLoopbackHttpForTests = true, pause = { pauses.add(it); clock.now += it })
    private fun success(value: Outcome<ByteChunk>) = (value as Outcome.Success).value
    private fun assertFailure(value: Outcome<*>, text: String) { assertTrue(value.toString(), value is Outcome.Failure); assertTrue((value as Outcome.Failure).error.message, value.error.message.contains(text)) }

    @Test fun chunksAreBoundedContiguousAndCarryMetadata() = runBlocking {
        Fixture().use { f ->
            val body = ByteArray(180_000) { (it % 251).toByte() }
            f.at("/") { e -> assertEquals("identity", e.requestHeaders.getFirst("Accept-Encoding")); assertNotNull(e.requestHeaders.getFirst("User-Agent")); assertNotNull(e.requestHeaders.getFirst("Accept")); e.responseHeaders.add("ETag", "\"test\""); e.sendResponseHeaders(200, body.size.toLong()); e.responseBody.write(body) }
            val results = client().stream(f.spec(), Token()).toList().map(::success)
            var offset = 0L
            results.forEachIndexed { index, chunk -> assertEquals(index.toLong(), chunk.sequence); assertEquals(offset, chunk.offset); assertTrue(chunk.bytes.size <= 65536); offset += chunk.bytes.size }
            assertEquals(180_000L, offset); assertNotNull(results.first().metadata); assertNotNull(results.last().metadata); assertTrue(results.last().final); assertEquals(1, results.count { it.final }); assertEquals(UtcTimestamp(1_700_000_000), results.first().metadata!!.receivedAt)
            assertArrayEquals(body, results.flatMap { it.bytes.copyBytes().asIterable() }.toByteArray())
        }
    }
    @Test fun emptyHeadAndErrorStatuses() = runBlocking {
        Fixture().use { f ->
            f.at("/empty") { it.sendResponseHeaders(204, -1) }
            f.at("/head") { it.responseHeaders.add("Content-Length", "99"); it.sendResponseHeaders(200, -1) }
            f.at("/error") { it.sendResponseHeaders(403, 6); it.responseBody.write("secret".toByteArray()) }
            for ((path, method) in listOf("/empty" to HttpMethod.GET, "/head" to HttpMethod.HEAD)) {
                val chunks = client().stream(f.spec(path, method = method), Token()).toList(); assertEquals(1, chunks.size); assertTrue(success(chunks.single()).final); assertNotNull(success(chunks.single()).metadata)
            }
            val failed = client().send(f.spec("/error"), Token()); assertFailure(failed, "403"); assertFalse(failed.toString().contains("secret"))
        }
    }
    @Test fun exactBoundOverBoundAndTruncatedNeverEmitFinal() = runBlocking {
        Fixture().use { f ->
            f.at("/exact") { it.sendResponseHeaders(200, 4); it.responseBody.write(byteArrayOf(1,2,3,4)) }
            f.at("/unknown") { it.sendResponseHeaders(200, 0); it.responseBody.write(byteArrayOf(1,2,3,4,5)) }
            f.at("/truncated") { it.sendResponseHeaders(200, 9); it.responseBody.write(byteArrayOf(1,2,3,4)) }
            assertTrue(client().send(f.spec("/exact", 4), Token()) is Outcome.Success)
            assertFailure(client().send(f.spec("/exact", 3), Token()), "bound")
            for (path in listOf("/unknown", "/truncated")) {
                val values = client().stream(f.spec(path, if (path == "/unknown") 4 else 20), Token()).toList()
                assertTrue(values.last() is Outcome.Failure); assertFalse(values.filterIsInstance<Outcome.Success<ByteChunk>>().any { it.value.final })
            }
            val tiny = OkHttpNetworkClient(TestClock(), allowLoopbackHttpForTests = true, maximumBufferedBytes = 3)
            assertFailure(tiny.send(f.spec("/exact"), Token()), "bound")
        }
    }
    @Test fun rawGzipIsNotDecodedAndCallerHeadersAreHonored() = runBlocking {
        Fixture().use { f ->
            val raw = byteArrayOf(31, -117, 8, 0, 0, 0)
            f.at("/") { e -> assertEquals("gzip", e.requestHeaders.getFirst("Accept-Encoding")); assertEquals("application/octet-stream", e.requestHeaders.getFirst("Accept")); e.responseHeaders.add("Content-Encoding", "gzip"); e.sendResponseHeaders(200, raw.size.toLong()); e.responseBody.write(raw) }
            val result = client().send(f.spec(headers = listOf(HttpHeader("Accept-Encoding", "gzip"), HttpHeader("Accept", "application/octet-stream"))), Token()) as Outcome.Success
            assertArrayEquals(raw, result.value.body.copyBytes()); assertTrue(result.value.metadata.headers.any { it.name.equals("Content-Encoding", true) && it.value == "gzip" })
        }
    }
    @Test fun forbiddenOriginsHeadersSecretsAndUriCredentialsAreRejected() = runBlocking {
        Fixture().use { f ->
            val calls = AtomicInteger(); f.at("/") { calls.incrementAndGet(); it.sendResponseHeaders(204, -1) }
            val base = f.spec()
            val specs = listOf(base.copy(endpoint = base.endpoint.copy(origin = Origin("https", "example.invalid", 443))), base.copy(request = base.request.copy(uri = ResourceUri("http://user:credential@127.0.0.1:${f.origin.port}/"))), base.copy(request = base.request.copy(headers = SnapshotList.of(HttpHeader("Host", "override")))))
            specs.forEach { assertFailure(client().send(it, Token()), "Origin".takeIf { _ -> it != specs.last() } ?: "header") }
            assertFailure(OkHttpNetworkClient(TestClock()).send(base, Token()), "HTTPS")
            val secret = SecretRef(EntityId("secret"), SecretScope(EntityId("fixture"), f.origin, "test"))
            assertTrue(client().send(base.copy(endpoint = base.endpoint.copy(secret = secret)), Token()) is Outcome.Unavailable)
            assertEquals(0, calls.get())
        }
    }
    @Test fun redirectsValidateAndStripEveryNonPublicHeaderAcrossOrigins() = runBlocking {
        Fixture().use { first -> Fixture().use { second ->
            val calls = AtomicInteger()
            second.at("/") { e -> calls.incrementAndGet(); assertNull(e.requestHeaders.getFirst("Authorization")); assertNull(e.requestHeaders.getFirst("Cookie")); assertNull(e.requestHeaders.getFirst("X-Api-Key")); assertNull(e.requestHeaders.getFirst("Range")); e.sendResponseHeaders(204, -1) }
            first.at("/") { e -> e.responseHeaders.add("Location", "http://127.0.0.1:${second.origin.port}/"); e.sendResponseHeaders(302, -1) }
            val headers = listOf("Authorization", "Cookie", "X-Api-Key", "Range").map { HttpHeader(it, "private") }
            assertFailure(client().send(first.spec(headers = headers), Token()), "Origin"); assertEquals(0, calls.get())
            assertTrue(client().send(first.spec(headers = headers, redirects = listOf(second.origin)), Token()) is Outcome.Success); assertEquals(1, calls.get())
        } }
    }
    @Test fun redirectLoopIsBoundedAndSameOriginAuthIsPreserved() = runBlocking {
        Fixture().use { f ->
            val calls = AtomicInteger()
            f.at("/loop") { calls.incrementAndGet(); it.responseHeaders.add("Location", "/loop"); it.sendResponseHeaders(302, -1) }
            f.at("/start") { it.responseHeaders.add("Location", "/end"); it.sendResponseHeaders(307, -1) }
            f.at("/end") { assertEquals("private", it.requestHeaders.getFirst("Authorization")); it.sendResponseHeaders(204, -1) }
            assertFailure(client().send(f.spec("/loop"), Token()), "Redirect"); assertEquals(6, calls.get())
            assertTrue(client().send(f.spec("/start", headers = listOf(HttpHeader("Authorization", "private"))), Token()) is Outcome.Success)
        }
    }
    @Test fun safeRetriesHonorRetryAfterAndNeverReplayChargedOrPostOrAuthFailure() = runBlocking {
        Fixture().use { f ->
            val calls = AtomicInteger(); f.at("/") { e -> val attempt = calls.incrementAndGet(); if (attempt == 1) { e.responseHeaders.add("Retry-After", "2"); e.sendResponseHeaders(503, -1) } else e.sendResponseHeaders(204, -1) }
            val pauses = mutableListOf<Duration>()
            assertTrue(client(pauses = pauses).send(f.spec(attempts = 3), Token()) is Outcome.Success); assertEquals(2, calls.get()); assertTrue(pauses.contains(2.seconds))
            for ((method, charge) in listOf(HttpMethod.POST to RequestCharge.NONCHARGED, HttpMethod.GET to RequestCharge.POTENTIALLY_CHARGED)) { calls.set(0); assertFailure(client().send(f.spec(method = method, charge = charge, attempts = 3), Token()), "503"); assertEquals(1, calls.get()) }
            f.at("/auth") { calls.incrementAndGet(); it.sendResponseHeaders(401, -1) }; calls.set(0); assertFailure(client().send(f.spec("/auth", attempts = 3), Token()), "401"); assertEquals(1, calls.get())
        }
    }
    @Test fun retryAfterBeyondBudgetIsNotShortened() = runBlocking {
        Fixture().use { f ->
            val calls = AtomicInteger(); f.at("/") { calls.incrementAndGet(); it.responseHeaders.add("Retry-After", "120"); it.sendResponseHeaders(429, -1) }
            val pauses = mutableListOf<Duration>(); assertFailure(client(pauses = pauses).send(f.spec(attempts = 3), Token()), "budget"); assertEquals(1, calls.get()); assertTrue(pauses.isEmpty())
        }
    }
    @Test fun noRetryAfterStreamingBytesAndSourceCapCannotBeBypassedByProfileId() = runBlocking {
        Fixture().use { f ->
            val calls = AtomicInteger(); f.at("/broken") { calls.incrementAndGet(); it.sendResponseHeaders(200, 100); it.responseBody.write(byteArrayOf(1,2,3)); it.responseBody.flush() }; f.at("/empty") { it.sendResponseHeaders(204, -1) }
            val values = client().stream(f.spec("/broken", attempts = 3), Token()).toList(); assertTrue(values.any { it is Outcome.Success }); assertTrue(values.last() is Outcome.Failure); assertEquals(1, calls.get())
            val pauses = mutableListOf<Duration>(); val clock = TestClock(); val c = client(clock, pauses)
            repeat(3) { index -> val spec = f.spec("/empty"); c.send(spec.copy(endpoint = spec.endpoint.copy(id = EntityId("profile$index"), requestsPerSecond = 100)), Token()) }
            assertEquals(listOf(125.milliseconds,125.milliseconds), pauses)
        }
    }
    @Test fun scryfallSearchUsesTwoPerSecondAnd429WaitsAtLeastThirtySeconds() = runBlocking {
        Fixture().use { f ->
            val calls = AtomicInteger()
            f.at("/") { e -> val n = calls.incrementAndGet(); if (n == 3) { e.responseHeaders.add("Retry-After", "1"); e.sendResponseHeaders(429, -1) } else e.sendResponseHeaders(204, -1) }
            val clock = TestClock(); val pauses = mutableListOf<Duration>()
            val c = OkHttpNetworkClient(clock, pause = { pauses.add(it); clock.now += it })
            val rewriteEngine = okhttp3.OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).build()
            c.callFactoryForTests = { rewriteEngine.newCall(it.newBuilder().url("http://127.0.0.1:${f.origin.port}/").build()) }
            val base = f.spec(attempts = 3)
            val spec = base.copy(endpoint = base.endpoint.copy(origin = Origin("https", "api.scryfall.com", 443), requestsPerSecond = 8, retry = base.endpoint.retry.copy(honorRetryAfter = false)), request = base.request.copy(uri = ResourceUri("https://api.scryfall.com/cards/search?q=synthetic")), timeout = 60.seconds)
            repeat(3) { assertTrue(c.send(spec, Token()) is Outcome.Success) }
            assertEquals(4, calls.get()); assertEquals(listOf(600.milliseconds, 600.milliseconds, 30.seconds), pauses)
        }
    }
    @Test fun httpDateRetryAfterAndOverallTimeoutAndReadTimeout() = runBlocking {
        Fixture().use { f ->
            f.at("/date") { e -> e.responseHeaders.add("Retry-After", "Tue, 14 Nov 2023 22:13:22 GMT"); e.sendResponseHeaders(204, -1) }
            val value = client().send(f.spec("/date"), Token()) as Outcome.Success
            assertEquals(Knowledge.Known(2.seconds), value.value.metadata.retryAfter)
            f.at("/stall") { e -> e.sendResponseHeaders(200, 0); e.responseBody.write(byteArrayOf(1)); e.responseBody.flush(); try { Thread.sleep(1500) } catch (_: InterruptedException) {} }
            val real = TestClock(real = true)
            val overall = OkHttpNetworkClient(real, allowLoopbackHttpForTests = true)
            val results = overall.stream(f.spec("/stall").copy(timeout = 150.milliseconds), Token()).toList()
            assertFailure(results.last(), "overall timeout"); assertFalse(results.filterIsInstance<Outcome.Success<ByteChunk>>().any { it.value.final })
            val read = OkHttpNetworkClient(real, allowLoopbackHttpForTests = true, readTimeout = 100.milliseconds)
            assertFailure(read.stream(f.spec("/stall"), Token()).toList().last(), "timeout")
        }
    }
    @Test fun coroutineAndTokenCancelBlockedReadWithinOneSecond() = runBlocking {
        for (tokenOnly in listOf(false, true)) Fixture().use { f ->
            val ready = CountDownLatch(1); val release = CountDownLatch(1)
            f.at("/") { e -> e.sendResponseHeaders(200, 0); e.responseBody.write(byteArrayOf(1)); e.responseBody.flush(); ready.countDown(); release.await(8, TimeUnit.SECONDS) }
            val token = Token(); val c = OkHttpNetworkClient(TestClock(real = true), allowLoopbackHttpForTests = true)
            val job = launch(Dispatchers.Default) { c.stream(f.spec(), token).collect {} }
            assertTrue(withContext(Dispatchers.IO) { ready.await(2, TimeUnit.SECONDS) }); delay(50)
            val start = System.nanoTime(); if (tokenOnly) token.cancelled = true else job.cancel()
            try { withTimeout(950) { job.join() }; assertTrue(job.isCancelled); assertTrue((System.nanoTime() - start) / 1_000_000 < 1000) } finally { release.countDown(); c.close() }
        }
    }
    @Test fun closeAbortsBlockedCallsAndPreventsNewRequests() = runBlocking {
        Fixture().use { f ->
            val ready = CountDownLatch(1); val release = CountDownLatch(1)
            f.at("/") { e -> e.sendResponseHeaders(200, 0); e.responseBody.write(byteArrayOf(1)); e.responseBody.flush(); ready.countDown(); release.await(8, TimeUnit.SECONDS) }
            val c = OkHttpNetworkClient(TestClock(real = true), allowLoopbackHttpForTests = true)
            val result = async(Dispatchers.Default) { c.stream(f.spec(), Token()).toList() }
            assertTrue(withContext(Dispatchers.IO) { ready.await(2, TimeUnit.SECONDS) }); delay(50)
            try { withTimeout(950) { c.close(); assertFailure(result.await().last(), "closed") }; c.close(); assertFailure(c.send(f.spec(), Token()), "closed") } finally { release.countDown() }
        }
    }
    @Test fun healthySlowHeadersAndBodyGapsUseConfiguredBudgets() = runBlocking {
        Fixture().use { f ->
            f.at("/headers") { e -> Thread.sleep(750); e.sendResponseHeaders(200, 2); e.responseBody.write(byteArrayOf(1, 2)) }
            f.at("/body") { e -> e.sendResponseHeaders(200, 2); e.responseBody.write(byteArrayOf(1)); e.responseBody.flush(); Thread.sleep(750); e.responseBody.write(byteArrayOf(2)) }
            val c = OkHttpNetworkClient(TestClock(real = true), allowLoopbackHttpForTests = true)
            try { for (path in listOf("/headers", "/body")) {
                val result = c.send(f.spec(path), Token()) as Outcome.Success
                assertArrayEquals(byteArrayOf(1, 2), result.value.body.copyBytes())
            } } finally { c.close() }
        }
    }

    @Test fun headerWaitCancellationAndCloseFinishWithinOneSecond() = runBlocking {
        for (mode in listOf("coroutine", "token", "close")) Fixture().use { f ->
            val ready = CountDownLatch(1); val release = CountDownLatch(1)
            f.at("/") { ready.countDown(); release.await(8, TimeUnit.SECONDS); it.sendResponseHeaders(204, -1) }
            val token = Token(); val c = OkHttpNetworkClient(TestClock(real = true), allowLoopbackHttpForTests = true)
            val result = async(Dispatchers.Default) { c.send(f.spec(), token) }
            assertTrue(withContext(Dispatchers.IO) { ready.await(2, TimeUnit.SECONDS) })
            val start = System.nanoTime()
            try {
                when (mode) { "coroutine" -> result.cancel(); "token" -> token.cancelled = true; else -> c.close() }
                withTimeout(950) {
                    if (mode == "close") assertFailure(result.await(), "closed") else { result.join(); assertTrue(result.isCancelled) }
                }
                assertTrue((System.nanoTime() - start) / 1_000_000 < 1000)
            } finally { release.countDown(); c.close() }
        }
    }

    @Test fun validLongRetryAfterRetainsCooldownEvenWhenHonorFlagIsFalse() = runBlocking {
        for (value in listOf("172800", "Thu, 16 Nov 2023 22:13:20 GMT", "99999999999999999999999999999")) Fixture().use { f ->
            val calls = AtomicInteger()
            f.at("/") { calls.incrementAndGet(); it.responseHeaders.add("Retry-After", value); it.sendResponseHeaders(503, -1) }
            val clock = TestClock(); val pauses = mutableListOf<Duration>(); val c = client(clock, pauses)
            val base = f.spec(attempts = 3)
            val spec = base.copy(endpoint = base.endpoint.copy(retry = base.endpoint.retry.copy(honorRetryAfter = false)))
            assertFailure(c.send(spec, Token()), "budget")
            assertFailure(c.send(spec, Token()), "cooldown")
            assertEquals(1, calls.get()); assertTrue(pauses.isEmpty()); c.close()
        }
    }

    @Test fun malformedRetryAfterFailsClosedWithoutReplay() = runBlocking {
        for (value in listOf("-1", "1.2", "Tue, 14 Nov 2023 22:13:22 GMT junk")) Fixture().use { f ->
            val calls = AtomicInteger()
            f.at("/") { calls.incrementAndGet(); it.responseHeaders.add("Retry-After", value); it.sendResponseHeaders(503, -1) }
            assertFailure(client().send(f.spec(attempts = 3), Token()), "Retry-After"); assertEquals(1, calls.get())
        }
    }

    @Test fun zeroRetryAfterCannotTriggerHiddenPostOrChargedReplay() = runBlocking {
        Fixture().use { f ->
            val calls = AtomicInteger()
            f.at("/") { e -> calls.incrementAndGet(); e.requestBody.readBytes(); e.responseHeaders.add("Retry-After", "0"); e.sendResponseHeaders(503, -1) }
            for ((method, charge) in listOf(HttpMethod.POST to RequestCharge.NONCHARGED, HttpMethod.POST to RequestCharge.POTENTIALLY_CHARGED, HttpMethod.GET to RequestCharge.POTENTIALLY_CHARGED)) {
                calls.set(0)
                val base = f.spec(method = method, charge = charge, attempts = 4)
                val spec = if (method == HttpMethod.POST) base.copy(request = base.request.copy(body = SnapshotBytes("generation".toByteArray()))) else base
                assertFailure(client().send(spec, Token()), "503"); assertEquals(1, calls.get())
            }
            calls.set(0)
            assertFailure(client().send(f.spec(attempts = 3), Token()), "503"); assertEquals(3, calls.get())
        }
    }

    @Test fun misdirectedStatusIsPreservedWithoutReplayAndWireGuardRejectsSecondProceed() = runBlocking {
        Fixture().use { f ->
            val calls = AtomicInteger()
            f.at("/") { calls.incrementAndGet(); it.sendResponseHeaders(421, -1) }
            val c = client()
            try { assertFailure(c.send(f.spec(attempts = 4), Token()), "421"); assertEquals(1, calls.get()) }
            finally { c.close() }
            calls.set(0)
            val guarded = client()
            // Simulate an unexpected engine follow-up independently of the status mask.
            guarded.configureEngineForTests { it.addInterceptor { chain ->
                chain.proceed(chain.request()).close()
                chain.proceed(chain.request())
            } }
            try {
                assertFailure(guarded.send(f.spec(method = HttpMethod.POST, charge = RequestCharge.POTENTIALLY_CHARGED, attempts = 4), Token()), "transport failed")
                assertEquals(1, calls.get())
            } finally { guarded.close() }
        }
    }

    @Test fun stalledDnsCancellationAndDeadlineReturnBeforeResolverAndNeverReachWire() = runBlocking {
        for (mode in listOf("coroutine", "token", "close", "deadline")) Fixture().use { f ->
            val calls = AtomicInteger(); f.at("/") { calls.incrementAndGet(); it.sendResponseHeaders(204, -1) }
            val entered = CountDownLatch(1); val release = CountDownLatch(1); val returned = CountDownLatch(1)
            val c = OkHttpNetworkClient(TestClock(real = true), allowLoopbackHttpForTests = true)
            c.configureEngineForTests { it.dns {
                entered.countDown(); release.await(8, TimeUnit.SECONDS)
                returned.countDown(); listOf(java.net.InetAddress.getByName("127.0.0.1"))
            } }
            val token = Token(); val base = f.spec()
            val spec = base.copy(endpoint = base.endpoint.copy(origin = Origin("http", "localhost", f.origin.port)),
                request = base.request.copy(uri = ResourceUri("http://localhost:${f.origin.port}/")),
                timeout = if (mode == "deadline") 350.milliseconds else 5.seconds)
            val result = async(Dispatchers.Default) { c.send(spec, token) }
            assertTrue(withContext(Dispatchers.IO) { entered.await(2, TimeUnit.SECONDS) })
            try {
                val start = System.nanoTime()
                when (mode) { "coroutine" -> result.cancel(); "token" -> token.cancelled = true; "close" -> c.close() }
                withTimeout(950) {
                    if (mode in listOf("coroutine", "token")) { result.join(); assertTrue(result.isCancelled) }
                    else assertFailure(result.await(), if (mode == "close") "closed" else "timeout")
                }
                assertTrue((System.nanoTime() - start) / 1_000_000 < 1000)
                assertEquals(1L, returned.count)
            } finally { release.countDown(); c.close() }
            assertTrue(withContext(Dispatchers.IO) { returned.await(2, TimeUnit.SECONDS) })
            delay(50); assertEquals(0, calls.get())
        }
    }

    @Test fun stalledUploadHasWriteBudgetAndCancellationNeverReplaysChargedBody() = runBlocking {
        for (mode in listOf("coroutine", "token", "close", "write-timeout")) {
            val server = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
            val accepted = CountDownLatch(1); val release = CountDownLatch(1)
            val executor = Executors.newSingleThreadExecutor()
            executor.submit { server.accept().use { accepted.countDown(); release.await(8, TimeUnit.SECONDS) } }
            val c = OkHttpNetworkClient(TestClock(real = true), allowLoopbackHttpForTests = true,
                writeTimeout = if (mode == "write-timeout") 350.milliseconds else 30.seconds)
            val token = Token()
            val spec = RequestSpec(EndpointProfile(EntityId("upload"), Origin("http", "127.0.0.1", server.localPort), SnapshotList.empty(), null, 8,
                RetryPolicy(4, 10.milliseconds, 1.seconds, true)),
                Request(HttpMethod.POST, ResourceUri("http://127.0.0.1:${server.localPort}/"), SnapshotList.empty(), SnapshotBytes(ByteArray(16 * 1024 * 1024))),
                5.seconds, RequestCharge.POTENTIALLY_CHARGED, RetryClassification.NEVER_AUTOMATIC, null, 10)
            val result = async(Dispatchers.Default) { c.send(spec, token) }
            try {
                assertTrue(withContext(Dispatchers.IO) { accepted.await(2, TimeUnit.SECONDS) }); delay(50)
                when (mode) { "coroutine" -> result.cancel(); "token" -> token.cancelled = true; "close" -> c.close() }
                withTimeout(950) {
                    if (mode in listOf("coroutine", "token")) { result.join(); assertTrue(result.isCancelled) }
                    else assertFailure(result.await(), if (mode == "close") "closed" else "timeout")
                }
                server.soTimeout = 100
                try { server.accept().close(); fail("Charged upload replayed") } catch (_: java.net.SocketTimeoutException) {}
            } finally { release.countDown(); c.close(); server.close(); executor.shutdownNow() }
        }
    }

    @Test fun syntheticTlsSlowHandshakeSucceedsAndCancellationAbortsHandshake() = runBlocking {
        // Generate a disposable loopback identity at runtime; no key/signing material
        // is checked in. The fixture uses the JDK keytool already hosting JVM tests.
        val directory = java.nio.file.Files.createTempDirectory("network-tls-fixture")
        val storePath = directory.resolve("loopback.p12")
        val password = "synthetic-fixture-only"
        try {
            val process = ProcessBuilder(java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "loopback", "-keyalg", "RSA", "-keysize", "2048", "-validity", "1",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-storetype", "PKCS12",
                "-keystore", storePath.toString(), "-storepass", password, "-keypass", password, "-noprompt")
                .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start()
            assertTrue("Fixture keytool did not finish", process.waitFor(20, TimeUnit.SECONDS))
            assertEquals("Fixture keytool failed", 0, process.exitValue())
            val store = java.security.KeyStore.getInstance("PKCS12")
            java.nio.file.Files.newInputStream(storePath).use { store.load(it, password.toCharArray()) }
            val keys = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, password.toCharArray()) }
            val trusts = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            val trust = trusts.trustManagers.filterIsInstance<javax.net.ssl.X509TrustManager>().single()
            val context = javax.net.ssl.SSLContext.getInstance("TLS").apply { init(keys.keyManagers, arrayOf(trust), java.security.SecureRandom()) }
            for (mode in listOf("healthy", "coroutine", "token", "close")) {
                val server = context.serverSocketFactory.createServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
                val entered = CountDownLatch(1); val release = CountDownLatch(1)
                val executor = Executors.newSingleThreadExecutor()
                val serving = executor.submit {
                    server.accept().use { socket ->
                        entered.countDown()
                        if (mode == "healthy") Thread.sleep(750) else release.await(8, TimeUnit.SECONDS)
                        (socket as javax.net.ssl.SSLSocket).startHandshake()
                        val input = socket.inputStream.bufferedReader()
                        while (!input.readLine().isNullOrEmpty()) {}
                        socket.outputStream.write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        socket.outputStream.flush()
                    }
                }
                val c = OkHttpNetworkClient(TestClock(real = true))
                c.configureEngineForTests { it.sslSocketFactory(context.socketFactory, trust) }
                val token = Token()
                val spec = RequestSpec(EndpointProfile(EntityId("tls"), Origin("https", "localhost", server.localPort), SnapshotList.empty(), null, 8,
                    RetryPolicy(1, Duration.ZERO, 1.seconds, true)),
                    Request(HttpMethod.GET, ResourceUri("https://localhost:${server.localPort}/"), SnapshotList.empty(), null),
                    5.seconds, RequestCharge.NONCHARGED, RetryClassification.SAFE_IDEMPOTENT, null, 10)
                val result = async(Dispatchers.Default) { c.send(spec, token) }
                try {
                    assertTrue(withContext(Dispatchers.IO) { entered.await(2, TimeUnit.SECONDS) })
                    when (mode) {
                        "healthy" -> { assertTrue(result.await() is Outcome.Success); serving.get(2, TimeUnit.SECONDS) }
                        else -> {
                            when (mode) { "coroutine" -> result.cancel(); "token" -> token.cancelled = true; else -> c.close() }
                            withTimeout(950) {
                                if (mode == "close") assertFailure(result.await(), "closed") else { result.join(); assertTrue(result.isCancelled) }
                            }
                        }
                    }
                } finally { release.countDown(); c.close(); server.close(); executor.shutdownNow() }
            }
        } finally {
            java.nio.file.Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { java.nio.file.Files.deleteIfExists(it) } }
        }
    }

    @Test fun rawGzipRangeAndValidatorRemainVisible() = runBlocking {
        Fixture().use { f ->
            val output = java.io.ByteArrayOutputStream()
            java.util.zip.GZIPOutputStream(output).use { it.write("synthetic gzip representation".toByteArray()) }
            val raw = output.toByteArray(); val suffix = raw.copyOfRange(4, raw.size)
            f.at("/") { e ->
                assertEquals("bytes=4-", e.requestHeaders.getFirst("Range")); assertEquals("strong-validator", e.requestHeaders.getFirst("If-Range"))
                assertEquals("gzip", e.requestHeaders.getFirst("Accept-Encoding"))
                e.responseHeaders.add("Content-Encoding", "gzip"); e.responseHeaders.add("Content-Range", "bytes 4-${raw.size - 1}/${raw.size}")
                e.sendResponseHeaders(206, suffix.size.toLong()); e.responseBody.write(suffix)
            }
            val result = client().send(f.spec(headers = listOf(HttpHeader("Range", "bytes=4-"), HttpHeader("If-Range", "strong-validator"), HttpHeader("Accept-Encoding", "gzip"))), Token()) as Outcome.Success
            assertArrayEquals(suffix, result.value.body.copyBytes()); assertEquals(206, result.value.metadata.statusCode)
            assertTrue(result.value.metadata.headers.any { it.name.equals("Content-Range", true) })
        }
    }

    @Test fun globalCookieHandlerAndAuthenticatorNeverSupplyCredentials() = runBlocking {
        val oldCookies = java.net.CookieHandler.getDefault()
        // This fixture runs on the host JDK. Android's compile stubs omit the
        // Java 9 getter, so resolve it reflectively while preserving restoration.
        val oldAuth = java.net.Authenticator::class.java.getMethod("getDefault").invoke(null) as java.net.Authenticator?
        val authCalls = AtomicInteger()
        try {
            java.net.CookieHandler.setDefault(object : java.net.CookieHandler() {
                override fun get(uri: java.net.URI, headers: Map<String, List<String>>) = mapOf("Cookie" to listOf("global=private"))
                override fun put(uri: java.net.URI, headers: Map<String, List<String>>) {}
            })
            java.net.Authenticator.setDefault(object : java.net.Authenticator() {
                override fun getPasswordAuthentication(): java.net.PasswordAuthentication {
                    authCalls.incrementAndGet(); return java.net.PasswordAuthentication("global", "private".toCharArray())
                }
            })
            Fixture().use { f ->
                val calls = AtomicInteger()
                f.at("/") { e -> calls.incrementAndGet(); assertNull(e.requestHeaders.getFirst("Cookie")); assertNull(e.requestHeaders.getFirst("Authorization")); e.responseHeaders.add("WWW-Authenticate", "Basic realm=synthetic"); e.sendResponseHeaders(401, -1) }
                assertFailure(client().send(f.spec(attempts = 3), Token()), "401"); assertEquals(1, calls.get()); assertEquals(0, authCalls.get())
            }
        } finally { java.net.CookieHandler.setDefault(oldCookies); java.net.Authenticator.setDefault(oldAuth) }
    }

    @Test fun responseHeaderBoundsApplyToRedirectAndErrorPaths() = runBlocking {
        for (status in listOf(200, 302, 503)) Fixture().use { f ->
            f.at("/") { e -> repeat(140) { e.responseHeaders.add("X-Repeated", "bounded") }; e.responseHeaders.add("Location", "/next"); e.sendResponseHeaders(status, -1) }
            assertFailure(client().send(f.spec(attempts = 3), Token()), "headers exceed bound")
        }
    }

    @Test fun finiteBudgetsAreRequiredAtAdapterBoundary() = runBlocking {
        try { OkHttpNetworkClient(TestClock(), connectTimeout = Duration.INFINITE); fail("Infinite connect accepted") } catch (_: IllegalArgumentException) {}
        try { OkHttpNetworkClient(TestClock(), maximumOverallTimeout = Duration.INFINITE); fail("Infinite overall accepted") } catch (_: IllegalArgumentException) {}
        Fixture().use { f ->
            assertFailure(client().send(f.spec().copy(timeout = Duration.INFINITE), Token()), "Finite")
            val base = f.spec(); assertFailure(client().send(base.copy(endpoint = base.endpoint.copy(retry = base.endpoint.retry.copy(maximumDelay = Duration.INFINITE))), Token()), "Finite")
        }
    }

    @Test fun scryfallBulkMetadataHasTenPerMinuteOriginSharedCap() = runBlocking {
        Fixture().use { f ->
            f.at("/") { it.sendResponseHeaders(204, -1) }
            val clock = TestClock(); val pauses = mutableListOf<Duration>()
            val c = OkHttpNetworkClient(clock, pause = { pauses.add(it); clock.now += it })
            val rewrite = okhttp3.OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).build()
            c.callFactoryForTests = { rewrite.newCall(it.newBuilder().url("http://127.0.0.1:${f.origin.port}/").build()) }
            val base = f.spec(); val spec = base.copy(endpoint = base.endpoint.copy(origin = Origin("https", "api.scryfall.com", 443)), request = base.request.copy(uri = ResourceUri("https://api.scryfall.com/bulk-data")), timeout = 30.seconds)
            repeat(3) { index -> assertTrue(c.send(spec.copy(endpoint = spec.endpoint.copy(id = EntityId("metadata$index"))), Token()) is Outcome.Success) }
            assertEquals(listOf(6.seconds, 6.seconds), pauses); c.close()
        }
    }

    @Test fun tokenCancellationWhileWaitingForRateSlotDoesNotStartSecondRequest() = runBlocking {
        Fixture().use { f ->
            val calls = AtomicInteger(); f.at("/") { calls.incrementAndGet(); it.sendResponseHeaders(204, -1) }
            val waiting = CountDownLatch(1)
            val c = OkHttpNetworkClient(TestClock(real = true), allowLoopbackHttpForTests = true, pause = { waiting.countDown(); delay(30.seconds) })
            assertTrue(c.send(f.spec(), Token()) is Outcome.Success)
            val token = Token(); val result = async(Dispatchers.Default) { c.send(f.spec(), token) }
            assertTrue(withContext(Dispatchers.IO) { waiting.await(2, TimeUnit.SECONDS) })
            token.cancelled = true; withTimeout(950) { result.join() }; assertTrue(result.isCancelled); assertEquals(1, calls.get()); c.close()
        }
    }

}
