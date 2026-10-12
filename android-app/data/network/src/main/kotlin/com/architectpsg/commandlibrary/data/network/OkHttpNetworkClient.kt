package com.architectpsg.commandlibrary.data.network

import com.architectpsg.commandlibrary.core.model.*
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Callback
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
public class OkHttpNetworkClient(
    private val clock: Clock,
    private val userAgent: String = "CommandLibrary/1 Android (offline catalog acquisition)",
    private val allowLoopbackHttpForTests: Boolean = false,
    private val pause: suspend (Duration) -> Unit = { delay(it) },
    private val connectTimeout: Duration = 15.seconds,
    private val readTimeout: Duration = 30.seconds,
    private val writeTimeout: Duration = 30.seconds,
    private val maximumOverallTimeout: Duration = 10.minutesCompat(),
    private val maximumBufferedBytes: Int = 8 * 1024 * 1024,
) : NetworkClient {
    private val closed = AtomicBoolean(false)
    private val live = java.util.Collections.newSetFromMap(ConcurrentHashMap<Operation, Boolean>())
    private val limiters = ConcurrentHashMap<Origin, Mutex>()
    private val slots = ConcurrentHashMap<Origin, Duration>()
    private val cooldowns = ConcurrentHashMap<Origin, Duration>()
    init {
        require(validHeader("User-Agent", userAgent) && userAgent.isNotBlank())
        require(listOf(connectTimeout, readTimeout, writeTimeout, maximumOverallTimeout).all { it.isFinite() && it > Duration.ZERO })
        require(maximumBufferedBytes > 0)
    }

    // The parser itself has OkHttp's finite HTTP/1 header budget (256 KiB). Exposed
    // headers are capped separately for every response, including retries/redirects.
    private var engine: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutMillis(connectTimeout).toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis(readTimeout).toLong(), TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMillis(writeTimeout).toLong(), TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
        .cache(null).cookieJar(CookieJar.NO_COOKIES)
        .authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val state = checkNotNull(chain.request().tag(ExchangeState::class.java))
            state.originalStatus?.let { response.newBuilder().code(it).build() } ?: response
        }
        .addNetworkInterceptor { chain ->
            val state = checkNotNull(chain.request().tag(ExchangeState::class.java))
            // Defense in depth: at most one wire attempt for each manually owned Call.
            if (state.started) throw java.io.IOException("Automatic replay rejected")
            state.started = true
            val response = chain.proceed(chain.request())
            // OkHttp 5.5 still follows 503 Retry-After:0 and coalesced 421 even with
            // retryOnConnectionFailure(false). Hide these codes from its follow-up
            // interceptor, then restore the exact code before returning to our policy.
            if (response.code == 503 || response.code == 421) {
                state.originalStatus = response.code
                response.newBuilder().code(599).build()
            } else response
        }.build()

    /** Module-private synthetic wire rewrite; production requests use only the configured engine. */
    internal var callFactoryForTests: ((okhttp3.Request) -> okhttp3.Call)? = null

    /** Synthetic TLS/DNS fixtures retain all other configured engine policies. */
    internal fun configureEngineForTests(configure: (OkHttpClient.Builder) -> Unit) {
        check(live.isEmpty())
        engine = engine.newBuilder().also(configure).build()
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
            val call = Operation(deadline)
            val done = AtomicBoolean(false)
            live.add(call)
            val monitor = launch(Dispatchers.Default + NonCancellable) {
                while (!done.get()) {
                    if (!owner.isActive || cancel.isCancelled || closed.get() || clock.monotonicNow() >= deadline) {
                        call.expired = clock.monotonicNow() >= deadline
                        if (cancel.isCancelled) owner.cancel(CancellationException("Network request cancelled"))
                        call.cancel()
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
                            val builder = okhttp3.Request.Builder().url(uri.toASCIIString())
                                .tag(ExchangeState::class.java, ExchangeState())
                                .header("User-Agent", userAgent).header("Accept", "application/json")
                                .header("Accept-Encoding", "identity")
                            headers.forEach { builder.header(it.name, it.value) }
                            val body = request.request.body?.copyBytes()?.toRequestBody()
                                ?: if (request.request.method in listOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)) byteArrayOf().toRequestBody() else null
                            builder.method(request.request.method.name, body)
                            val wire = builder.build()
                            val active = callFactoryForTests?.invoke(wire) ?: engine.newCall(wire)
                            active.timeout().timeout(timeoutMillis(deadline - clock.monotonicNow()).toLong(), TimeUnit.MILLISECONDS)
                            call.active.set(active)
                            checkCall(call, cancel)
                            // Cancelling Call closes sockets, but a platform DNS resolver may ignore
                            // interruption. Bound the consumer wait independently; no socket or
                            // request is created after that canceled resolver eventually returns.
                            val response = coroutineScope {
                                val pending = async { awaitResponse(active) }
                                while (!pending.isCompleted) { delay(20); checkCall(call, cancel) }
                                pending.await()
                            }
                            response.use {
                                checkCall(call, cancel)
                                val status = response.code
                                if (status !in 100..599) throw TransportFailure("Invalid HTTP status")
                                val responseHeaders = validatedHeaders(response)
                                if (status in listOf(301, 302, 303, 307, 308)) {
                                    if (++redirects > 5 || request.request.method !in listOf(HttpMethod.GET, HttpMethod.HEAD)) throw TransportFailure("Redirect rejected")
                                    val location = response.header("Location") ?: throw TransportFailure("Redirect location missing")
                                    val next = try { uri.resolve(location) } catch (_: Exception) { throw TransportFailure("Redirect rejected") }
                                    validateUri(next, request.endpoint, initial = false)
                                    if (origin(uri) != origin(next)) headers = headers.filter { it.name.lowercase() in setOf("accept", "accept-encoding", "user-agent") }
                                    call.cancel()
                                    uri = next
                                    continue
                                }
                                val retryAfter = retryAfter(response.header("Retry-After"))
                                if (status !in 200..299) {
                                    if (status == 429 || status in listOf(500, 502, 503, 504)) {
                                        retryDelay = retryAfter
                                        if (status == 429 && uri.host.equals("api.scryfall.com", true)) retryDelay = maxOf(retryDelay ?: Duration.ZERO, 30.seconds)
                                        val mandatory = retryAfter != null || status == 429 && uri.host.equals("api.scryfall.com", true)
                                        if (request.endpoint.retry.honorRetryAfter || mandatory) retryDelay?.let { holdOrigin(uri, it) }
                                        throw RetryableFailure("HTTP $status", retryDelay, mandatory)
                                    }
                                    throw TransportFailure("HTTP $status")
                                }
                                val metadata = HttpMetadata(status, SnapshotList.copyOf(responseHeaders), ResourceUri(uri.toASCIIString()), clock.utcNow(), retryAfter?.let { Knowledge.Known(it) } ?: Knowledge.Absent("No Retry-After"), Knowledge.Unknown("Transport cannot determine charge", SnapshotList.empty()))
                                val rawLength = response.header("Content-Length")
                                val length = rawLength?.toLongOrNull() ?: if (rawLength == null) -1L else throw TransportFailure("Invalid response length")
                                if (length < -1 || rawLength != null && length < 0) throw TransportFailure("Invalid response length")
                                if (length > request.maxResponseBytes) throw TransportFailure("Response exceeds byte bound")
                                var offset = 0L
                                var sequence = 0L
                                if (request.request.method != HttpMethod.HEAD) response.body.byteStream().use { input ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        checkCall(call, cancel)
                                        val count = input.read(buffer)
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
                    } finally { call.cancel() }
                    waitCancelable(checkNotNull(retryDelay), call, cancel)
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                checkCancellation(cancel)
                emit(Outcome.Failure(DomainFailure(if (e is InvalidRequest) FailureCode.INVALID_INPUT else FailureCode.TRANSPORT, (e as? TransportFailure)?.message ?: "Network transport failed", SnapshotList.empty())))
            } finally {
                done.set(true)
                call.cancel()
                withContext(NonCancellable) { monitor.join(); live.remove(call) }
            }
        }
    }.flowOn(Dispatchers.IO).buffer(0)

    override suspend fun close() {
        closed.set(true)
        live.forEach { it.cancel() }
        engine.connectionPool.evictAll()
    }

    private suspend fun awaitResponse(call: okhttp3.Call): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: okhttp3.Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    private fun validatedHeaders(response: Response): List<HttpHeader> {
        val headers = response.headers
        if (headers.size > 128 || (0 until headers.size).sumOf { index ->
                headers.name(index).toByteArray(Charsets.UTF_8).size.toLong() + headers.value(index).toByteArray(Charsets.UTF_8).size + 4
            } > 65536) throw TransportFailure("Response headers exceed bound")
        return (0 until headers.size).map { index ->
            val name = headers.name(index); val value = headers.value(index)
            if (!validHeader(name, value)) throw TransportFailure("Invalid response header")
            HttpHeader(name, value)
        }
    }

    private fun holdOrigin(uri: URI, duration: Duration) {
        val key = origin(uri)
        val until = clock.monotonicNow() + duration
        synchronized(cooldowns) { cooldowns[key] = maxOf(cooldowns[key] ?: Duration.ZERO, until) }
    }

    private suspend fun reserve(uri: URI, requested: Int, call: Operation, cancel: CancelToken) {
        val key = origin(uri)
        val mutex = synchronized(limiters) { limiters[key] ?: Mutex().also { limiters[key] = it } }
        while (!mutex.tryLock()) { delay(20); checkCall(call, cancel) }
        try {
            checkCall(call, cancel)
            val interval = if (uri.host.equals("api.scryfall.com", true)) {
                when (uri.path.trimEnd('/')) {
                    "/cards/search" -> maxOf(600.milliseconds, (1000.0 / minOf(requested, 2)).milliseconds)
                    "/bulk-data" -> 6.seconds
                    else -> (1000.0 / minOf(requested, 8)).milliseconds
                }
            } else (1000.0 / minOf(requested, 8)).milliseconds
            val now = clock.monotonicNow()
            val next = maxOf(now, slots[key] ?: now, cooldowns[key] ?: now)
            if (next >= call.deadline) throw TransportFailure("Origin cooldown exceeds request budget")
            if (next > now) waitCancelable(next - now, call, cancel)
            while ((cooldowns[key] ?: Duration.ZERO) > clock.monotonicNow()) {
                if (cooldowns.getValue(key) >= call.deadline) throw TransportFailure("Origin cooldown exceeds request budget")
                waitCancelable(cooldowns.getValue(key) - clock.monotonicNow(), call, cancel)
            }
            checkCall(call, cancel)
            slots[key] = maxOf(clock.monotonicNow(), next) + interval
        } finally { mutex.unlock() }
    }
    private suspend fun waitCancelable(duration: Duration, call: Operation, cancel: CancelToken) = coroutineScope {
        checkCall(call, cancel)
        val waiting = async { pause(duration) }
        while (!waiting.isCompleted) { delay(20); checkCall(call, cancel) }
        waiting.await()
        checkCall(call, cancel)
    }
    private fun checkCancellation(cancel: CancelToken) { if (cancel.isCancelled) throw CancellationException("Network request cancelled"); cancel.throwIfCancelled() }
    private suspend fun checkCall(call: Operation, cancel: CancelToken) { currentCoroutineContext().ensureActive(); checkCancellation(cancel); if (closed.get()) throw TransportFailure("Network client closed"); if (call.expired || clock.monotonicNow() >= call.deadline) throw TransportFailure("Network overall timeout") }
    private fun validateRequest(spec: RequestSpec) {
        if (!spec.timeout.isFinite() || !spec.endpoint.retry.initialDelay.isFinite() || !spec.endpoint.retry.maximumDelay.isFinite()) throw InvalidRequest("Finite request budget required")
        if (spec.request.headers.size > 128 || spec.request.headers.sumOf { it.name.toByteArray(Charsets.UTF_8).size.toLong() + it.value.toByteArray(Charsets.UTF_8).size + 4 } > 65536 || spec.request.headers.any { !validHeader(it.name, it.value) || it.name.lowercase() in setOf("user-agent", "accept") && it.value.isBlank() || it.name.lowercase() in setOf("host", "content-length", "transfer-encoding", "connection", "proxy-authorization") }) throw InvalidRequest("Invalid request header")
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
        val text = value.trim()
        if (text.matches(Regex("[0-9]+"))) {
            // A saturating infinite cooldown is intentional for unrepresentably large
            // valid values: the finite job fails closed rather than retrying early.
            return text.toLongOrNull()?.seconds ?: Duration.INFINITE
        }
        val parser = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US).apply {
            isLenient = false; timeZone = java.util.TimeZone.getTimeZone("GMT")
        }
        val position = java.text.ParsePosition(0)
        val millis = parser.parse(text, position)?.time
        if (millis == null || position.index != text.length) throw TransportFailure("Invalid Retry-After")
        val now = clock.utcNow().epochSeconds.toDouble() * 1000
        return maxOf(0.0, millis.toDouble() - now).milliseconds
    }
    private class Operation(val deadline: Duration) {
        val active: AtomicReference<okhttp3.Call?> = AtomicReference(null)
        @Volatile var expired: Boolean = false
        fun cancel() { active.getAndSet(null)?.cancel() }
    }
    private class ExchangeState { var started: Boolean = false; var originalStatus: Int? = null }
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
