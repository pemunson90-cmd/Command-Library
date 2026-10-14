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
import kotlinx.coroutines.flow.collect
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Real loopback HTTP fixtures; host rewrites below test policy, not external TLS or composition. */
class Acquisition429MetadataTest {
    private class Token : CancelToken {
        @Volatile var cancelled = false
        override val isCancelled get() = cancelled
        override fun throwIfCancelled() { if (cancelled) throw CancellationException("Synthetic token cancelled") }
    }
    private class TestClock(var now: Duration = Duration.ZERO) : Clock {
        override fun utcNow() = UtcTimestamp(1_700_000_000)
        override fun monotonicNow() = now
    }
    private class Fixture : AutoCloseable {
        private val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = AtomicInteger()
        val origin get() = Origin("http", "127.0.0.1", server.address.port)
        init { server.executor = executor; server.start() }
        fun at(path: String = "/", action: (com.sun.net.httpserver.HttpExchange) -> Unit) {
            server.createContext(path) { exchange ->
                calls.incrementAndGet()
                try { action(exchange) } finally { exchange.close() }
            }
        }
        fun spec(path: String = "/") = RequestSpec(
            EndpointProfile(EntityId("acquisition-fixture"), origin, SnapshotList.empty(), null, 8,
                RetryPolicy(1, Duration.ZERO, Duration.ZERO, true)),
            Request(HttpMethod.GET, ResourceUri("http://127.0.0.1:${origin.port}$path"), SnapshotList.empty(), null),
            5.seconds, RequestCharge.NONCHARGED, RetryClassification.NEVER_AUTOMATIC, null, 1)
        override fun close() { server.stop(0); executor.shutdownNow() }
    }
    private fun client(optIn: Boolean = true, clock: TestClock = TestClock(), pauses: MutableList<Duration> = mutableListOf()) =
        OkHttpNetworkClient(clock, allowLoopbackHttpForTests = true, pause = { pauses += it; clock.now += it },
            readTimeout = 100.milliseconds, exposeAcquisition429Metadata = optIn)
    private fun terminal(values: List<Outcome<ByteChunk>>): ByteChunk {
        assertEquals(1, values.size)
        assertTrue(values.single().toString(), values.single() is Outcome.Success)
        val chunk = (values.single() as Outcome.Success).value
        assertEquals(0L, chunk.sequence); assertEquals(0L, chunk.offset)
        assertEquals(0, chunk.bytes.size); assertTrue(chunk.final)
        assertEquals(429, chunk.metadata!!.statusCode)
        return chunk
    }
    private fun failure(value: Outcome<*>, code: FailureCode = FailureCode.TRANSPORT) {
        assertTrue(value.toString(), value is Outcome.Failure)
        assertEquals(code, (value as Outcome.Failure).error.code)
        assertFalse(value.toString().contains("private-error-content"))
    }

    @Test fun defaultStreamAndBufferedSendKeep429Failure() = runBlocking {
        Fixture().use { f ->
            f.at { e -> val body = "private-error-content".toByteArray(); e.sendResponseHeaders(429, body.size.toLong()); e.responseBody.write(body) }
            client(optIn = false).useClient { c -> failure(c.stream(f.spec(), Token()).toList().single()) }
            client().useClient { c -> failure(c.send(f.spec(), Token())) }
            assertEquals(2, f.calls.get())
        }
    }
    @Test fun optInPreservesActual429HeadersUriAndRetryAfterWithoutErrorBytes() = runBlocking {
        Fixture().use { f ->
            f.at { e -> e.responseHeaders.add("Retry-After", "2"); e.responseHeaders.add("X-Fixture", "actual-response")
                val body = "private-error-content".toByteArray(); e.sendResponseHeaders(429, body.size.toLong()); e.responseBody.write(body) }
            client().useClient { c ->
                val metadata = terminal(c.stream(f.spec(), Token()).toList()).metadata!!
                assertEquals(f.spec().request.uri, metadata.finalUri)
                assertEquals(UtcTimestamp(1_700_000_000), metadata.receivedAt)
                assertEquals(Knowledge.Known(2.seconds), metadata.retryAfter)
                assertTrue(metadata.headers.any { it.name.equals("X-Fixture", true) && it.value == "actual-response" })
            }
            assertEquals(1, f.calls.get())
        }
    }
    @Test fun absentAndHttpDateRetryAfterRetainTheirMeaning() = runBlocking {
        Fixture().use { f ->
            f.at("/absent") { it.sendResponseHeaders(429, -1) }
            f.at("/date") { it.responseHeaders.add("Retry-After", "Tue, 14 Nov 2023 22:13:22 GMT"); it.sendResponseHeaders(429, -1) }
            client().useClient { assertTrue(terminal(it.stream(f.spec("/absent"), Token()).toList()).metadata!!.retryAfter is Knowledge.Absent) }
            client().useClient { assertEquals(Knowledge.Known(2.seconds), terminal(it.stream(f.spec("/date"), Token()).toList()).metadata!!.retryAfter) }
            assertEquals(2, f.calls.get())
        }
    }
    @Test fun stalled429BodyDoesNotBecomeAReadTimeoutOrArtifactLengthFailure() = runBlocking {
        Fixture().use { f ->
            val release = CountDownLatch(1)
            f.at { e -> e.sendResponseHeaders(429, 100_000); e.responseBody.flush(); release.await(3, TimeUnit.SECONDS) }
            try {
                client().useClient { c -> terminal(withTimeout(1500) { c.stream(f.spec(), Token()).toList() }) }
                assertEquals(1, f.calls.get())
            } finally { release.countDown() }
        }
    }
    @Test fun ineligibleOptedStreamsAreRejectedBeforeWire() = runBlocking {
        Fixture().use { f ->
            f.at { it.sendResponseHeaders(429, -1) }
            val base = f.spec()
            val requests = listOf(
                base.copy(request = base.request.copy(method = HttpMethod.HEAD)),
                base.copy(request = base.request.copy(method = HttpMethod.POST)),
                base.copy(request = base.request.copy(body = SnapshotBytes(byteArrayOf(1)))),
                base.copy(retryClassification = RetryClassification.SAFE_IDEMPOTENT),
                base.copy(retryClassification = RetryClassification.EXPLICIT_IDEMPOTENCY_KEY),
                base.copy(endpoint = base.endpoint.copy(retry = base.endpoint.retry.copy(maxAttempts = 2))))
            client().useClient { c -> requests.forEach { failure(c.stream(it, Token()).toList().single(), FailureCode.INVALID_INPUT) } }
            assertEquals(0, f.calls.get())
        }
    }
    @Test fun optInDoesNotExpose403Or503AsMetadata() = runBlocking {
        Fixture().use { f ->
            f.at("/forbidden") { it.sendResponseHeaders(403, -1) }
            f.at("/unavailable") { it.responseHeaders.add("Retry-After", "0"); it.sendResponseHeaders(503, -1) }
            client().useClient { c ->
                failure(c.stream(f.spec("/forbidden"), Token()).toList().single())
                failure(c.stream(f.spec("/unavailable"), Token()).toList().single())
            }
            assertEquals(2, f.calls.get())
        }
    }
    @Test fun malformedRetryAfterAndOversizedHeadersFailClosed() = runBlocking {
        Fixture().use { f ->
            f.at("/invalid") { it.responseHeaders.add("Retry-After", "tomorrow"); it.sendResponseHeaders(429, -1) }
            f.at("/headers") { e -> repeat(129) { e.responseHeaders.add("X-Fixture-$it", "bounded") }; e.sendResponseHeaders(429, -1) }
            client().useClient { c ->
                failure(c.stream(f.spec("/invalid"), Token()).toList().single())
                failure(c.stream(f.spec("/headers"), Token()).toList().single())
            }
            assertEquals(2, f.calls.get())
        }
    }
    @Test fun scryfallCooldownRetainsThirtySecondMinimumAndLongerRetryAfter() = runBlocking {
        for ((header, expected) in listOf("1" to 30.seconds, "45" to 45.seconds)) Fixture().use { f ->
            f.at { e -> if (f.calls.get() == 1) { e.responseHeaders.add("Retry-After", header); e.sendResponseHeaders(429, -1) } else e.sendResponseHeaders(204, -1) }
            val clock = TestClock(); val pauses = mutableListOf<Duration>()
            client(clock = clock, pauses = pauses).useClient { c ->
                val rewrite = okhttp3.OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).build()
                c.callFactoryForTests = { rewrite.newCall(it.newBuilder().url("http://127.0.0.1:${f.origin.port}/").build()) }
                val base = f.spec()
                val spec = base.copy(endpoint = base.endpoint.copy(origin = Origin("https", "api.scryfall.com", 443), retry = base.endpoint.retry.copy(honorRetryAfter = false)),
                    request = base.request.copy(uri = ResourceUri("https://api.scryfall.com/cards/search?q=synthetic")), timeout = 60.seconds)
                terminal(c.stream(spec, Token()).toList())
                assertEquals(1, f.calls.get()); assertTrue(pauses.isEmpty())
                assertTrue(c.stream(spec, Token()).toList().single() is Outcome.Success)
                assertEquals(listOf(expected), pauses); assertEquals(2, f.calls.get())
            }
        }
    }
    @Test fun overflowingRetryAfterPreservesMetadataAndBlocksSubsequentDispatch() = runBlocking {
        Fixture().use { f ->
            f.at { it.responseHeaders.add("Retry-After", "9999999999999999999999999999"); it.sendResponseHeaders(429, -1) }
            val pauses = mutableListOf<Duration>()
            client(pauses = pauses).useClient { c ->
                assertEquals(Knowledge.Known(Duration.INFINITE), terminal(c.stream(f.spec(), Token()).toList()).metadata!!.retryAfter)
                failure(c.stream(f.spec(), Token()).toList().single())
                assertTrue(pauses.isEmpty()); assertEquals(1, f.calls.get())
            }
        }
    }
    /** Synthetic call/body instrumentation observes application reads and close ordering only. */
    private class ObservedCall(private val wire: okhttp3.Request, private val events: MutableList<String>,
        private val response: () -> okhttp3.Response) : okhttp3.Call by okhttp3.OkHttpClient().newCall(wire) {
        private var cancelled = false
        private val timeoutValue = okio.Timeout()
        override fun request() = wire
        override fun enqueue(responseCallback: okhttp3.Callback) { responseCallback.onResponse(this, response()) }
        override fun cancel() { cancelled = true; events += "cancel" }
        override fun isCanceled() = cancelled
        override fun timeout() = timeoutValue
    }
    private fun observedResponse(wire: okhttp3.Request, events: MutableList<String>, onClose: () -> Unit = {}): okhttp3.Response {
        val body = object : okhttp3.ResponseBody() {
            private val sourceValue = object : okio.ForwardingSource(okio.Buffer().writeUtf8("private-error-content")) {
                override fun read(sink: okio.Buffer, byteCount: Long): Long { events += "read"; return super.read(sink, byteCount) }
                override fun close() { events += "close"; onClose(); super.close() }
            }.buffer()
            override fun contentType(): okhttp3.MediaType? = null
            override fun contentLength() = 21L
            override fun source() = sourceValue
        }
        return okhttp3.Response.Builder().request(wire).protocol(okhttp3.Protocol.HTTP_1_1).code(429)
            .message("Synthetic rate limit").body(body).build()
    }
    @Test fun synthetic429CallIsCancelledBeforeCloseAndMetadataIsEmittedAfterCloseWithoutRead() = runBlocking {
        Fixture().use { f ->
            val events = mutableListOf<String>()
            client().useClient { c ->
                c.callFactoryForTests = { wire -> ObservedCall(wire, events) { observedResponse(wire, events) } }
                val values = mutableListOf<Outcome<ByteChunk>>()
                c.stream(f.spec(), Token()).collect { values += it; events += "emit" }
                terminal(values)
                assertFalse(events.toString(), events.contains("read"))
                assertTrue(events.toString(), events.indexOf("cancel") >= 0 && events.indexOf("cancel") < events.indexOf("close"))
                assertTrue(events.toString(), events.indexOf("close") < events.indexOf("emit"))
            }
            assertEquals(0, f.calls.get())
        }
    }
    @Test fun syntheticCloseBoundaryCancellationOrDeadlineSuppresses429Metadata() = runBlocking {
        for (tokenCancellation in listOf(true, false)) Fixture().use { f ->
            val events = mutableListOf<String>(); val clock = TestClock(); val token = Token()
            val values = mutableListOf<Outcome<ByteChunk>>()
            client(clock = clock).useClient { c ->
                c.callFactoryForTests = { wire -> ObservedCall(wire, events) {
                    observedResponse(wire, events) { if (tokenCancellation) token.cancelled = true else clock.now = 6.seconds }
                } }
                if (tokenCancellation) {
                    try { c.stream(f.spec(), token).collect { values += it }; fail("Close cancellation ignored") }
                    catch (_: CancellationException) { }
                } else {
                    c.stream(f.spec(), token).collect { values += it }
                    failure(values.single())
                }
                assertFalse(values.toString(), values.any { it is Outcome.Success })
                assertFalse(events.toString(), events.contains("read")); assertTrue(events.contains("close"))
            }
        }
    }
    private suspend fun <T> OkHttpNetworkClient.useClient(block: suspend (OkHttpNetworkClient) -> T): T =
        try { block(this) } finally { close() }
}
