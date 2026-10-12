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
class UrlConnectionNetworkClientTest {
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
    private fun client(clock: TestClock = TestClock(), pauses: MutableList<Duration> = mutableListOf()) = UrlConnectionNetworkClient(clock, allowLoopbackHttpForTests = true, pause = { pauses.add(it); clock.now += it })
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
            val tiny = UrlConnectionNetworkClient(TestClock(), allowLoopbackHttpForTests = true, maximumBufferedBytes = 3)
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
            assertFailure(UrlConnectionNetworkClient(TestClock()).send(base, Token()), "HTTPS")
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
            val c = UrlConnectionNetworkClient(clock, pause = { pauses.add(it); clock.now += it }, connectionFactory = { java.net.URI("http://127.0.0.1:${f.origin.port}/").toURL().openConnection() as java.net.HttpURLConnection })
            val base = f.spec(attempts = 3)
            val spec = base.copy(endpoint = base.endpoint.copy(origin = Origin("https", "api.scryfall.com", 443), requestsPerSecond = 8, retry = base.endpoint.retry.copy(honorRetryAfter = false)), request = base.request.copy(uri = ResourceUri("https://api.scryfall.com/cards/search?q=synthetic")), timeout = 60.seconds)
            repeat(3) { assertTrue(c.send(spec, Token()) is Outcome.Success) }
            assertEquals(4, calls.get()); assertEquals(listOf(500.milliseconds, 500.milliseconds, 30.seconds), pauses)
        }
    }
    @Test fun httpDateRetryAfterAndOverallTimeoutAndReadTimeout() = runBlocking {
        Fixture().use { f ->
            f.at("/date") { e -> e.responseHeaders.add("Retry-After", "Tue, 14 Nov 2023 22:13:22 GMT"); e.sendResponseHeaders(204, -1) }
            val value = client().send(f.spec("/date"), Token()) as Outcome.Success
            assertEquals(Knowledge.Known(2.seconds), value.value.metadata.retryAfter)
            f.at("/stall") { e -> e.sendResponseHeaders(200, 0); e.responseBody.write(byteArrayOf(1)); e.responseBody.flush(); try { Thread.sleep(1500) } catch (_: InterruptedException) {} }
            val real = TestClock(real = true)
            val overall = UrlConnectionNetworkClient(real, allowLoopbackHttpForTests = true)
            val results = overall.stream(f.spec("/stall").copy(timeout = 150.milliseconds), Token()).toList()
            assertFailure(results.last(), "overall timeout"); assertFalse(results.filterIsInstance<Outcome.Success<ByteChunk>>().any { it.value.final })
            val read = UrlConnectionNetworkClient(real, allowLoopbackHttpForTests = true, readTimeout = 100.milliseconds)
            assertFailure(read.stream(f.spec("/stall"), Token()).toList().last(), "timeout")
        }
    }
    @Test fun coroutineAndTokenCancelBlockedReadWithinOneSecond() = runBlocking {
        for (tokenOnly in listOf(false, true)) Fixture().use { f ->
            val ready = CountDownLatch(1); val release = CountDownLatch(1)
            f.at("/") { e -> e.sendResponseHeaders(200, 0); e.responseBody.write(byteArrayOf(1)); e.responseBody.flush(); ready.countDown(); release.await(8, TimeUnit.SECONDS) }
            val token = Token(); val c = UrlConnectionNetworkClient(TestClock(real = true), allowLoopbackHttpForTests = true)
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
            val c = UrlConnectionNetworkClient(TestClock(real = true), allowLoopbackHttpForTests = true)
            val result = async(Dispatchers.Default) { c.stream(f.spec(), Token()).toList() }
            assertTrue(withContext(Dispatchers.IO) { ready.await(2, TimeUnit.SECONDS) }); delay(50)
            try { withTimeout(950) { c.close(); assertFailure(result.await().last(), "closed") }; c.close(); assertFailure(c.send(f.spec(), Token()), "closed") } finally { release.countDown() }
        }
    }
}
