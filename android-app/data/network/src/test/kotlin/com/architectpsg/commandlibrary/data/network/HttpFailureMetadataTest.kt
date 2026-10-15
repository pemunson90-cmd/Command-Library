package com.architectpsg.commandlibrary.data.network

import com.architectpsg.commandlibrary.core.model.*
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Synthetic real-loopback fixtures; no external provider or billing certification. */
class HttpFailureMetadataTest {
    private class Token : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
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
        fun respond(status: Int, retryAfter: String? = null) {
            server.createContext("/") { exchange ->
                calls.incrementAndGet()
                try {
                    exchange.requestBody.readBytes()
                    retryAfter?.let { exchange.responseHeaders.add("Retry-After", it) }
                    exchange.responseHeaders.add("X-Private-Fixture", "header-secret-sentinel")
                    val body = "body-secret-sentinel".toByteArray()
                    exchange.sendResponseHeaders(status, body.size.toLong())
                    exchange.responseBody.write(body)
                } finally { exchange.close() }
            }
        }
        fun spec() = RequestSpec(
            EndpointProfile(EntityId("http-failure-fixture"), origin, SnapshotList.empty(), null, 8,
                RetryPolicy(4, 10.milliseconds, 1.seconds, true)),
            Request(HttpMethod.POST, ResourceUri("http://127.0.0.1:${origin.port}/uri-secret-sentinel"),
                SnapshotList.copyOf(listOf(HttpHeader("Authorization", "Bearer credential-secret-sentinel"))),
                SnapshotBytes("synthetic charged request".toByteArray())),
            5.seconds, RequestCharge.POTENTIALLY_CHARGED, RetryClassification.NEVER_AUTOMATIC, null, 1024)
        override fun close() { server.stop(0); executor.shutdownNow() }
    }
    private suspend fun <T> withClient(block: suspend (OkHttpNetworkClient, MutableList<Duration>) -> T): T {
        val clock = TestClock()
        val pauses = mutableListOf<Duration>()
        val client = OkHttpNetworkClient(clock, allowLoopbackHttpForTests = true,
            pause = { pauses += it; clock.now += it })
        return try { block(client, pauses) } finally { client.close() }
    }
    private fun failure(value: Outcome<*>): DomainFailure {
        assertTrue(value.toString(), value is Outcome.Failure)
        val failure = (value as Outcome.Failure).error
        assertEquals(FailureCode.TRANSPORT, failure.code)
        assertTrue(failure.evidence.isEmpty())
        for (secret in listOf("body-secret-sentinel", "header-secret-sentinel", "uri-secret-sentinel", "credential-secret-sentinel")) {
            assertFalse(value.toString(), value.toString().contains(secret))
        }
        return failure
    }
    private fun metadata(value: Outcome<*>, status: Int): HttpFailureMetadata {
        val metadata = checkNotNull(failure(value).httpFailure)
        assertEquals(status, metadata.statusCode)
        assertTrue(metadata.chargeStatus is Knowledge.Unknown)
        return metadata
    }

    @Test fun chargedPostSend401KeepsAbsentRetryAfterAndNeverReplays() = runBlocking {
        Fixture().use { f ->
            f.respond(401)
            withClient { client, pauses ->
                assertTrue(metadata(client.send(f.spec(), Token()), 401).retryAfter is Knowledge.Absent)
                assertTrue(pauses.isEmpty())
            }
            assertEquals(1, f.calls.get())
        }
    }

    @Test fun chargedPostStream503ZeroRetryAfterKeepsOriginalStatusAndNeverReplays() = runBlocking {
        Fixture().use { f ->
            f.respond(503, "0")
            withClient { client, pauses ->
                val values = client.stream(f.spec(), Token()).toList()
                assertEquals(1, values.size)
                assertEquals(Knowledge.Known(Duration.ZERO), metadata(values.single(), 503).retryAfter)
                assertTrue(pauses.isEmpty())
            }
            assertEquals(1, f.calls.get())
        }
    }

    @Test fun stream429KeepsKnownDelayIncludingOverflowWithoutRetry() = runBlocking {
        for ((header, expected) in listOf("2" to 2.seconds, "999999999999999999999999999999" to Duration.INFINITE)) {
            Fixture().use { f ->
                f.respond(429, header)
                withClient { client, pauses ->
                    assertEquals(Knowledge.Known(expected), metadata(client.stream(f.spec(), Token()).toList().single(), 429).retryAfter)
                    assertTrue(pauses.isEmpty())
                }
                assertEquals(1, f.calls.get())
            }
        }
    }

    @Test fun safeGetBudgetRejectionRetainsActualStatusAndRetryAfter() = runBlocking {
        for ((header, expected) in listOf(null to null, "2" to 2.seconds, "999999999999999999999999999999" to Duration.INFINITE)) {
            Fixture().use { f ->
                f.respond(503, header)
                val base = f.spec()
                val spec = base.copy(request = base.request.copy(method = HttpMethod.GET, body = null),
                    endpoint = base.endpoint.copy(retry = RetryPolicy(4, 6.seconds, 6.seconds, true)),
                    charge = RequestCharge.NONCHARGED, retryClassification = RetryClassification.SAFE_IDEMPOTENT,
                    timeout = 5.seconds)
                withClient { client, pauses ->
                    val result = client.send(spec, Token())
                    val retryAfter = metadata(result, 503).retryAfter
                    if (expected == null) assertTrue(retryAfter is Knowledge.Absent)
                    else assertEquals(Knowledge.Known(expected), retryAfter)
                    assertEquals("Retry delay exceeds request budget", failure(result).message)
                    assertTrue(pauses.isEmpty())
                }
                assertEquals(1, f.calls.get())
            }
        }
    }

    @Test fun malformedRetryAfterFailsClosedWithoutInventingZeroOrStatus() = runBlocking {
        Fixture().use { f ->
            f.respond(429, "tomorrow-secret-sentinel")
            withClient { client, pauses ->
                val result = client.send(f.spec(), Token())
                assertEquals("Invalid Retry-After", failure(result).message)
                assertNull(failure(result).httpFailure)
                assertFalse(result.toString().contains("tomorrow-secret-sentinel"))
                assertTrue(pauses.isEmpty())
            }
            assertEquals(1, f.calls.get())
        }
    }

    @Test fun invalidInputHasNoHttpFactsAndNeverDispatches() = runBlocking {
        Fixture().use { f ->
            f.respond(401)
            val base = f.spec()
            withClient { client, _ ->
                val result = client.send(base.copy(request = base.request.copy(
                    headers = SnapshotList.copyOf(listOf(HttpHeader("X-Fixture", "invalid\u0001header"))))), Token())
                assertTrue(result is Outcome.Failure)
                val error = (result as Outcome.Failure).error
                assertEquals(FailureCode.INVALID_INPUT, error.code)
                assertNull(error.httpFailure)
            }
            assertEquals(0, f.calls.get())
        }
    }

    @Test fun connectionClosedBeforeResponseDoesNotForgeHttpFactsOrReplayPost() = runBlocking {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 2000
            val accepted = AtomicInteger()
            val executor = Executors.newSingleThreadExecutor()
            val peer = executor.submit {
                server.accept().use { accepted.incrementAndGet() }
            }
            try {
                Fixture().use { f ->
                    val base = f.spec()
                    val origin = Origin("http", "127.0.0.1", server.localPort)
                    val spec = base.copy(endpoint = base.endpoint.copy(origin = origin),
                        request = base.request.copy(uri = ResourceUri("http://127.0.0.1:${server.localPort}/")))
                    withClient { client, pauses ->
                        assertNull(failure(client.send(spec, Token())).httpFailure)
                        assertTrue(pauses.isEmpty())
                    }
                }
                peer.get(2, java.util.concurrent.TimeUnit.SECONDS)
                assertEquals(1, accepted.get())
                server.soTimeout = 100
                try { server.accept().close(); fail("Charged POST replayed") }
                catch (_: java.net.SocketTimeoutException) { }
            } finally { executor.shutdownNow() }
        }
    }
}
