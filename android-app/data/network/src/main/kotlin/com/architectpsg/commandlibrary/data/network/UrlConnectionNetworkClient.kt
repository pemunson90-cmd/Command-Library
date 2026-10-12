package com.architectpsg.commandlibrary.data.network

import com.architectpsg.commandlibrary.core.model.*
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Raw-representation transport. Higher-level callers supply already-resolved, origin-scoped auth.
 * Non-null secret references are rejected: this adapter has no vault dependency.
 * Non-2xx responses are failures; error bodies are never exposed or logged.
 */
public class UrlConnectionNetworkClient(
    private val clock: Clock,
    private val userAgent: String = "CommandLibrary/1 Android (offline catalog acquisition)",
    private val allowLoopbackHttpForTests: Boolean = false,
    private val pause: suspend (Duration) -> Unit = { delay(it) },
    private val connectTimeout: Duration = 15.seconds,
    private val readTimeout: Duration = 30.seconds,
    private val maximumOverallTimeout: Duration = 10.minutesCompat(),
    private val maximumBufferedBytes: Int = 8 * 1024 * 1024,
    private val connectionFactory: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection },
) : NetworkClient {
    private val closed = AtomicBoolean(false)
    private val live = java.util.Collections.newSetFromMap(ConcurrentHashMap<Call, Boolean>())
    private val limiters = ConcurrentHashMap<Origin, Mutex>()
    private val slots = ConcurrentHashMap<Origin, Duration>()
    private val cooldowns = ConcurrentHashMap<Origin, Duration>()
    init {
        require(validHeader("User-Agent", userAgent) && userAgent.isNotBlank())
        require(connectTimeout > Duration.ZERO && readTimeout > Duration.ZERO && maximumOverallTimeout > Duration.ZERO)
        require(maximumBufferedBytes > 0)
    }

    override suspend fun send(request: RequestSpec, cancel: CancelToken): Outcome<ByteResponse> {
        val output = ByteArrayOutputStream()
        var metadata: HttpMetadata? = null
        var failure: Outcome<Nothing>? = null
        // send has a separate allocation ceiling, even if streaming permits multi-GiB artifacts.
        stream(request.copy(maxResponseBytes = minOf(request.maxResponseBytes, maximumBufferedBytes.toLong())), cancel).collect { value ->
            when (value) {
                is Outcome.Success -> { metadata = value.value.metadata ?: metadata; output.write(value.value.bytes.copyBytes()) }
                is Outcome.Failure -> failure = value
                is Outcome.Unavailable -> failure = value
                is Outcome.Unknown -> failure = value
            }
        }
        return failure ?: Outcome.Success(ByteResponse(checkNotNull(metadata), SnapshotBytes(output.toByteArray())))
    }

    override fun stream(request: RequestSpec, cancel: CancelToken): Flow<Outcome<ByteChunk>> = flow {
        coroutineScope {
            val owner = currentCoroutineContext().job
            val deadline = clock.monotonicNow() + minOf(request.timeout, maximumOverallTimeout)
            val call = Call(deadline)
            val done = AtomicBoolean(false)
            live.add(call)
            val monitor = launch(Dispatchers.Default + NonCancellable) {
                while (!done.get()) {
                    if (!owner.isActive || cancel.isCancelled || closed.get() || clock.monotonicNow() >= deadline) {
                        call.expired = clock.monotonicNow() >= deadline
                        call.disconnect()
                        if (cancel.isCancelled) owner.cancel(CancellationException("Network request cancelled"))
                        break
                    }
                    delay(20)
                }
            }
            try {
                checkCancellation(cancel)
                if (closed.get()) throw TransportFailure("Network client closed")
                if (request.endpoint.secret != null) {
                    emit(Outcome.Unavailable(UnavailableReason.KEY_MISSING, "Resolve scoped credentials before transport"))
                    return@coroutineScope
                }
                validateRequest(request)
                var attempt = 0
                var emitted = false
                while (true) {
                    attempt++
                    var retryDelay: Duration? = null
                    try {
                        var uri = URI(request.request.uri.value)
                        var headers = request.request.headers.toList()
                        var redirects = 0
                        while (true) {
                            checkCall(call, cancel)
                            validateUri(uri, request.endpoint, initial = redirects == 0)
                            reserve(uri, request.endpoint.requestsPerSecond, call, cancel)
                            checkCall(call, cancel)
                            val connection = connectionFactory(uri)
                            call.connection = connection
                            checkCall(call, cancel)
                            connection.instanceFollowRedirects = false
                            connection.useCaches = false
                            connection.connectTimeout = timeoutMillis(minOf(connectTimeout, 500.milliseconds))
                            connection.readTimeout = timeoutMillis(minOf(readTimeout, 500.milliseconds))
                            connection.requestMethod = request.request.method.name
                            connection.setRequestProperty("User-Agent", userAgent)
                            connection.setRequestProperty("Accept", "application/json")
                            connection.setRequestProperty("Accept-Encoding", "identity")
                            headers.forEach { connection.setRequestProperty(it.name, it.value) }
                            request.request.body?.let {
                                connection.doOutput = true
                                connection.setFixedLengthStreamingMode(it.size)
                                connection.outputStream.use { stream -> stream.write(it.copyBytes()) }
                            }
                            val status = connection.responseCode
                            checkCall(call, cancel)
                            if (status !in 100..599) throw TransportFailure("Invalid HTTP status")
                            if (status in listOf(301, 302, 303, 307, 308)) {
                                if (++redirects > 5 || request.request.method !in listOf(HttpMethod.GET, HttpMethod.HEAD)) throw TransportFailure("Redirect rejected")
                                val location = connection.getHeaderField("Location") ?: throw TransportFailure("Redirect location missing")
                                val next = try { uri.resolve(location) } catch (_: Exception) { throw TransportFailure("Redirect rejected") }
                                validateUri(next, request.endpoint, initial = false)
                                if (origin(uri) != origin(next)) headers = headers.filter { it.name.lowercase() in setOf("accept", "accept-encoding", "user-agent") }
                                call.disconnect()
                                uri = next
                                continue
                            }
                            val retryAfter = retryAfter(connection.getHeaderField("Retry-After"))
                            if (status !in 200..299) {
                                if (status == 429 || status in listOf(500, 502, 503, 504)) {
                                    retryDelay = retryAfter
                                    if (status == 429 && uri.host.equals("api.scryfall.com", true)) retryDelay = maxOf(retryDelay ?: Duration.ZERO, 30.seconds)
                                    val mandatory = status == 429 && uri.host.equals("api.scryfall.com", true)
                                    if (request.endpoint.retry.honorRetryAfter || mandatory) retryDelay?.let { holdOrigin(uri, it) }
                                    throw RetryableFailure("HTTP $status", retryDelay, mandatory)
                                }
                                throw TransportFailure("HTTP $status")
                            }
                            if (connection.headerFields.size > 128 || connection.headerFields.entries.sumOf { (name, values) -> (name?.length ?: 0).toLong() + values.sumOf { it.length.toLong() } } > 65536) throw TransportFailure("Response headers exceed bound")
                            val responseHeaders = connection.headerFields.entries.filter { it.key != null }.flatMap { (name, values) ->
                                values.map { value -> if (!validHeader(name, value)) throw TransportFailure("Invalid response header"); HttpHeader(name, value) }
                            }
                            val metadata = HttpMetadata(status, SnapshotList.copyOf(responseHeaders), ResourceUri(uri.toASCIIString()), clock.utcNow(), retryAfter?.let { Knowledge.Known(it) } ?: Knowledge.Absent("No Retry-After"), Knowledge.Unknown("Transport cannot determine charge", SnapshotList.empty()))
                            val rawLength = connection.getHeaderField("Content-Length")
                            val length = rawLength?.toLongOrNull() ?: if (rawLength == null) -1L else throw TransportFailure("Invalid response length")
                            if (length < -1 || rawLength != null && length < 0) throw TransportFailure("Invalid response length")
                            if (length > request.maxResponseBytes) throw TransportFailure("Response exceeds byte bound")
                            var offset = 0L
                            var sequence = 0L
                            if (request.request.method != HttpMethod.HEAD) connection.inputStream.use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    checkCall(call, cancel)
                                    val count = readBounded(input, buffer, call, cancel)
                                    checkCall(call, cancel)
                                    if (count < 0) break
                                    if (count == 0) continue
                                    if (count.toLong() > request.maxResponseBytes - offset) throw TransportFailure("Response exceeds byte bound")
                                    emitted = true
                                    emit(Outcome.Success(ByteChunk(sequence++, offset, SnapshotBytes(buffer.copyOf(count)), if (offset == 0L) metadata else null, false)))
                                    offset += count
                                }
                                if (length >= 0 && offset != length) throw TransportFailure("Truncated response")
                            }
                            emit(Outcome.Success(ByteChunk(sequence, offset, SnapshotBytes(byteArrayOf()), metadata, true)))
                            return@coroutineScope
                        }
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) {
                        checkCall(call, cancel)
                        val retryable = e is RetryableFailure || e is java.io.IOException
                        if (!retryable || emitted || !safeRetry(request) || attempt >= minOf(request.endpoint.retry.maxAttempts, 5)) {
                            throw if (e is TransportFailure) e else TransportFailure(if (e is SocketTimeoutException) "Network timeout" else "Network transport failed")
                        }
                        val policy = request.endpoint.retry
                        var backoff = policy.initialDelay
                        repeat(attempt - 1) { backoff = minOf(backoff * 2, policy.maximumDelay) }
                        retryDelay = if (policy.honorRetryAfter || (e as? RetryableFailure)?.mandatory == true) maxOf(backoff, (e as? RetryableFailure)?.wait ?: Duration.ZERO) else backoff
                        // Never violate Retry-After by clipping it to the policy ceiling.
                        if (retryDelay > policy.maximumDelay || clock.monotonicNow() + retryDelay >= deadline) throw TransportFailure("Retry delay exceeds request budget")
                    } finally { call.disconnect() }
                    waitCancelable(checkNotNull(retryDelay), call, cancel)
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                checkCancellation(cancel)
                emit(Outcome.Failure(DomainFailure(if (e is InvalidRequest) FailureCode.INVALID_INPUT else FailureCode.TRANSPORT, (e as? TransportFailure)?.message ?: "Network transport failed", SnapshotList.empty())))
            } finally {
                done.set(true)
                call.disconnect()
                withContext(NonCancellable) { monitor.join(); live.remove(call) }
            }
        }
    }.flowOn(Dispatchers.IO).buffer(0)

    override suspend fun close() {
        closed.set(true)
        withContext(Dispatchers.IO) { live.map { call -> async { call.disconnect() } }.awaitAll() }
    }

    // HttpURLConnection implementations may defer disconnect until a read returns. A 500ms
    // socket timeout provides an independent cancellation backstop. Body idle time still obeys
    // the configured readTimeout; response-header/connect waits have the stricter 500ms cap.
    private suspend fun readBounded(input: java.io.InputStream, buffer: ByteArray, call: Call, cancel: CancelToken): Int {
        val started = System.nanoTime()
        while (true) {
            try { return input.read(buffer) } catch (e: SocketTimeoutException) {
                checkCall(call, cancel)
                if ((System.nanoTime() - started) / 1_000_000 >= readTimeout.inWholeMilliseconds) throw e
            }
        }
    }

    private fun holdOrigin(uri: URI, duration: Duration) {
        val key = origin(uri)
        val until = clock.monotonicNow() + duration
        synchronized(cooldowns) { cooldowns[key] = maxOf(cooldowns[key] ?: Duration.ZERO, until) }
    }

    private suspend fun reserve(uri: URI, requested: Int, call: Call, cancel: CancelToken) {
        val key = origin(uri)
        val mutex = synchronized(limiters) { limiters[key] ?: Mutex().also { limiters[key] = it } }
        while (!mutex.tryLock()) { delay(20); checkCall(call, cancel) }
        try {
            checkCall(call, cancel)
            val rate = minOf(requested, if (uri.host.equals("api.scryfall.com", true) && uri.path.trimEnd('/') == "/cards/search") 2 else 8)
            val now = clock.monotonicNow()
            val next = maxOf(now, slots[key] ?: now, cooldowns[key] ?: now)
            if (next > now) waitCancelable(next - now, call, cancel)
            while ((cooldowns[key] ?: Duration.ZERO) > clock.monotonicNow()) waitCancelable(cooldowns.getValue(key) - clock.monotonicNow(), call, cancel)
            checkCall(call, cancel)
            slots[key] = maxOf(clock.monotonicNow(), next) + (1000.0 / rate).milliseconds
        } finally { mutex.unlock() }
    }
    private suspend fun waitCancelable(duration: Duration, call: Call, cancel: CancelToken) = coroutineScope {
        checkCall(call, cancel)
        val waiting = async { pause(duration) }
        while (!waiting.isCompleted) { delay(20); checkCall(call, cancel) }
        waiting.await()
        checkCall(call, cancel)
    }
    private fun checkCancellation(cancel: CancelToken) { if (cancel.isCancelled) throw CancellationException("Network request cancelled"); cancel.throwIfCancelled() }
    private suspend fun checkCall(call: Call, cancel: CancelToken) { currentCoroutineContext().ensureActive(); checkCancellation(cancel); if (closed.get()) throw TransportFailure("Network client closed"); if (call.expired || clock.monotonicNow() >= call.deadline) throw TransportFailure("Network overall timeout") }
    private fun validateRequest(spec: RequestSpec) {
        if (spec.request.headers.size > 128 || spec.request.headers.sumOf { it.name.length.toLong() + it.value.length } > 65536 || spec.request.headers.any { !validHeader(it.name, it.value) || it.name.lowercase() in setOf("user-agent", "accept") && it.value.isBlank() || it.name.lowercase() in setOf("host", "content-length", "transfer-encoding", "connection") }) throw InvalidRequest("Invalid request header")
        if (spec.request.body != null && spec.request.method in listOf(HttpMethod.GET, HttpMethod.HEAD)) throw InvalidRequest("Request body rejected")
        validateUri(try { URI(spec.request.uri.value) } catch (_: Exception) { throw InvalidRequest("Invalid request URI") }, spec.endpoint, true)
    }
    private fun validateUri(uri: URI, profile: EndpointProfile, initial: Boolean) {
        if (uri.rawUserInfo != null || uri.rawFragment != null || uri.host == null || uri.scheme == null) throw InvalidRequest("Origin rejected")
        val actual = try { origin(uri) } catch (_: Exception) { throw InvalidRequest("Origin rejected") }
        val normalized = { value: Origin -> Origin(value.scheme.lowercase(), value.host.lowercase(), value.port) }
        if (actual.scheme != "https" && !(allowLoopbackHttpForTests && actual.scheme == "http" && actual.host in setOf("127.0.0.1", "localhost", "::1"))) throw InvalidRequest("HTTPS required")
        if (actual != normalized(profile.origin) && (initial || profile.allowedRedirectOrigins.none { normalized(it) == actual })) throw InvalidRequest("Origin rejected")
    }
    private fun retryAfter(value: String?): Duration? {
        if (value == null) return null
        value.trim().toLongOrNull()?.let { return if (it in 0..86400) it.seconds else null }
        return try {
            val parser = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US).apply { isLenient = false; timeZone = java.util.TimeZone.getTimeZone("GMT") }
            val millis = parser.parse(value)?.time ?: return null
            val delta = millis - clock.utcNow().epochSeconds * 1000
            if (delta in 0..86_400_000) delta.milliseconds else null
        } catch (_: Exception) { null }
    }
    private class Call(val deadline: Duration) { @Volatile var connection: HttpURLConnection? = null; @Volatile var expired: Boolean = false; fun disconnect() { connection?.disconnect(); connection = null } }
    private open class TransportFailure(message: String) : Exception(message)
    private class InvalidRequest(message: String) : TransportFailure(message)
    private class RetryableFailure(message: String, val wait: Duration?, val mandatory: Boolean) : TransportFailure(message)
    private companion object {
        fun validHeader(name: String, value: String): Boolean = name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) && value.none { it == '\r' || it == '\n' || it.code < 32 && it != '\t' || it.code == 127 }
        fun origin(uri: URI): Origin = Origin(uri.scheme.lowercase(), uri.host.lowercase(), if (uri.port >= 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80)
        fun timeoutMillis(value: Duration): Int = value.inWholeMilliseconds.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
        fun safeRetry(spec: RequestSpec): Boolean = spec.request.method in listOf(HttpMethod.GET, HttpMethod.HEAD) && spec.charge == RequestCharge.NONCHARGED && spec.retryClassification == RetryClassification.SAFE_IDEMPOTENT
        fun Int.minutesCompat(): Duration = (this * 60).seconds
    }
}
