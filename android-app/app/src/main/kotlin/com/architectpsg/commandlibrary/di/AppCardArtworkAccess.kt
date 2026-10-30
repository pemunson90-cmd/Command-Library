package com.architectpsg.commandlibrary.di

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.architectpsg.commandlibrary.core.common.ConsentCoordinator
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.network.OkHttpNetworkClient
import com.architectpsg.commandlibrary.data.network.WireDispatchGuard
import com.architectpsg.commandlibrary.feature.shell.CardArtworkAccess
import com.architectpsg.commandlibrary.feature.shell.CardArtworkImage
import com.architectpsg.commandlibrary.feature.shell.CardArtworkOffer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class ArtworkConnection(val online: Boolean, val wifi: Boolean)

/** One bounded in-memory owner. No startup I/O, approval, cache, persistent image or replay. */
internal class AppCardArtworkAccess(
    context: Context,
    private val printings: CatalogPrintingLookup,
    private val settings: SettingsRepository,
    private val consent: ConsentCoordinator,
    private val clock: Clock,
    private val transportFactory: (WireDispatchGuard) -> NetworkClient = { guard -> OkHttpNetworkClient(clock, dispatchGuard = guard) },
    private val connection: () -> ArtworkConnection = { currentArtworkConnection(context.applicationContext) },
    private val decoder: suspend (ByteArray, String, CancelToken) -> Bitmap = LocalArtworkDecoder::decode,
) : CardArtworkAccess {
    private val owner = Mutex()

    override suspend fun prepare(printingId: CardId, generation: GenerationId, cancel: CancelToken): Outcome<SnapshotList<CardArtworkOffer>> =
        bounded(cancel) {
            checkCancellation(cancel)
            when (val found = printings.printing(printingId, cancel)) {
                is Outcome.Success -> Outcome.Success(OrdinaryArtworkProfile.offers(found.value, printingId, generation, cancel))
                is Outcome.Failure -> found
                is Outcome.Unavailable -> found
                is Outcome.Unknown -> found
            }
        }

    /** Connectivity is intentionally irrelevant to already displayed, previously acquired pixels. */
    override suspend fun permitted(offer: CardArtworkOffer, consentId: EntityId, cancel: CancelToken): Outcome<Boolean> = bounded(cancel) {
        when (val fresh = prepare(offer.printingId, offer.generation, cancel)) {
            is Outcome.Success -> {
                if (offer !in fresh.value) Outcome.Success(false)
                else when (val current = settings.read(cancel)) {
                    is Outcome.Success -> Outcome.Success(current.value.consents.any { it.id == consentId && it.permits(offer.intent) })
                    is Outcome.Failure -> current
                    is Outcome.Unavailable -> current
                    is Outcome.Unknown -> current
                }
            }
            is Outcome.Failure -> fresh
            is Outcome.Unavailable -> fresh
            is Outcome.Unknown -> fresh
        }
    }

    override suspend fun load(offer: CardArtworkOffer, consentId: EntityId, cancel: CancelToken): Outcome<CardArtworkImage> = bounded(cancel) {
        // Token-aware bounded waits. Keep ownership until native decode and every child unwind.
        while (!owner.tryLock()) { checkCancellation(cancel); delay(50) }
        var client: NetworkClient? = null
        var pixels: Bitmap? = null
        var exposed = false
        try {
            requirePermission(offer, consentId, cancel)
            coroutineScope {
                var stop: Outcome<Nothing>? = null
                val spec = request(offer)
                var dispatched = false
                val guard = WireDispatchGuard { request, uri, token, enqueue ->
                    consent.withExclusive(token) {
                        if (request !== spec || uri != spec.request.uri || dispatched || !OrdinaryArtworkProfile.trusted(uri))
                            throw Stop(rejected(FailureCode.PERMISSION))
                        // Fresh source, effective consent, connection and physical enqueue share the writer gate.
                        requirePermission(offer, consentId, token)
                        val current = requireSuccess(settings.read(token))
                        if (!current.consents.any { it.id == consentId && it.permits(offer.intent) }) throw Stop(unapproved())
                        val live = connection()
                        if (!live.online || (current.cache.wifiOnlyDownloads && !live.wifi))
                            throw Stop(Outcome.Unavailable(UnavailableReason.OFFLINE, "Artwork download requires an allowed connection"))
                        checkCancellation(token)
                        dispatched = true
                        enqueue()
                    }
                }
                val work = async(Dispatchers.IO) {
                    checkCancellation(cancel)
                    client = transportFactory(guard)
                    val response = requireSuccess(requireNotNull(client).send(spec, cancel))
                    // Injectable transports still must pass the physical-dispatch guard.
                    if (!dispatched) throw Stop(rejected(FailureCode.PERMISSION))
                    requirePermission(offer, consentId, cancel)
                    val bytes = validatedBytes(response, offer)
                    checkCancellation(cancel)
                    // Assign before leaving the decode context: prompt cancellation must not lose pixels.
                    pixels = decoder(bytes, offer.variant, cancel)
                    checkCancellation(cancel)
                }
                val monitor = launch {
                    while (work.isActive) {
                        delay(100)
                        checkCancellation(cancel)
                        val permission = permitted(offer, consentId, cancel)
                        val blocked = when (permission) {
                            is Outcome.Success -> if (permission.value) null else unapproved()
                            is Outcome.Failure -> permission
                            is Outcome.Unavailable -> permission
                            is Outcome.Unknown -> permission
                        }
                        if (blocked != null) {
                            stop = blocked
                            work.cancel(CancellationException("Artwork permission changed"))
                            return@launch
                        }
                    }
                }
                try {
                    try { work.await() } catch (failure: CancellationException) {
                        // External cancellation always wins; only our monitor's cancellation maps to policy.
                        currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
                        stop?.let { throw Stop(it) }
                        throw failure
                    }
                } finally {
                    withContext(NonCancellable) {
                        monitor.cancelAndJoin()
                        work.cancelAndJoin()
                    }
                }
            }
            // No transfer/monitor is active when the per-request client is closed.
            withContext(NonCancellable) { client?.close(); client = null }
            consent.withExclusive(cancel) {
                requirePermission(offer, consentId, cancel)
                checkCancellation(cancel)
                Outcome.Success(CardArtworkImage(offer, requireNotNull(pixels))).also { exposed = true }
            }
        } finally {
            try {
                client?.let { remaining -> withContext(NonCancellable) { remaining.close() } }
            } finally {
                if (!exposed) pixels?.recycle()
                owner.unlock()
            }
        }
    }

    private suspend fun requirePermission(offer: CardArtworkOffer, consentId: EntityId, cancel: CancelToken) {
        if (!requireSuccess(permitted(offer, consentId, cancel))) throw Stop(unapproved())
    }

    private fun request(offer: CardArtworkOffer): RequestSpec {
        require(OrdinaryArtworkProfile.trusted(offer.intent.resource))
        return RequestSpec(EndpointProfile(EntityId("ordinary-artwork"), Origin("https", "cards.scryfall.io", 443),
            SnapshotList.empty(), null, 1, RetryPolicy(1, Duration.ZERO, Duration.ZERO, false)),
            Request(HttpMethod.GET, offer.intent.resource, SnapshotList.of(
                HttpHeader("Accept", if (offer.variant == "normal") "image/jpeg" else "image/webp"),
                HttpHeader("Accept-Encoding", "identity"),
                HttpHeader("User-Agent", "CommandLibrary/1.0 (personal Android card library)")), null),
            30.seconds, RequestCharge.NONCHARGED, RetryClassification.NEVER_AUTOMATIC, null, CardArtworkAccess.MAX_COMPRESSED_BYTES)
    }

    private fun validatedBytes(response: ByteResponse, offer: CardArtworkOffer): ByteArray {
        require(response.metadata.statusCode == 200 && response.metadata.finalUri == offer.intent.resource)
        require(response.body.size.toLong() in 1..CardArtworkAccess.MAX_COMPRESSED_BYTES)
        val headers = response.metadata.headers
        require(headers.size <= 64 && headers.all { it.name.length <= 128 && it.value.length <= 4096 })
        val type = headers.filter { it.name.equals("Content-Type", true) }
        val encoding = headers.filter { it.name.equals("Content-Encoding", true) }
        require(type.size == 1 && encoding.size <= 1)
        require(encoding.isEmpty() || encoding.single().value.trim().equals("identity", true))
        val expected = if (offer.variant == "normal") "image/jpeg" else "image/webp"
        require(type.single().value.substringBefore(';').trim().equals(expected, true))
        val bytes = response.body.copyBytes()
        require(LocalArtworkDecoder.signature(bytes, offer.variant))
        return bytes
    }

    private suspend fun checkCancellation(cancel: CancelToken) { currentCoroutineContext().ensureActive(); cancel.throwIfCancelled() }

    private suspend fun <T> bounded(cancel: CancelToken, action: suspend () -> Outcome<T>): Outcome<T> {
        return try { checkCancellation(cancel); action() }
        catch (failure: CancellationException) { throw failure }
        catch (failure: Stop) { checkCancellation(cancel); failure.outcome }
        catch (_: Exception) { checkCancellation(cancel); rejected(FailureCode.CORRUPT_DATA) }
    }

    private fun <T> requireSuccess(result: Outcome<T>): T = when (result) {
        is Outcome.Success -> result.value
        is Outcome.Failure -> throw Stop(result)
        is Outcome.Unavailable -> throw Stop(result)
        is Outcome.Unknown -> throw Stop(result)
    }
    private class Stop(val outcome: Outcome<Nothing>) : RuntimeException()
    private fun unapproved() = Outcome.Unavailable(UnavailableReason.CONSENT_REQUIRED, "Exact artwork approval is required")
    private fun rejected(code: FailureCode) = Outcome.Failure(DomainFailure(code, "Artwork access was rejected", SnapshotList.empty()))
}

private fun currentArtworkConnection(context: Context): ArtworkConnection {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return ArtworkConnection(false, false)
    val capabilities = try { manager.activeNetwork?.let(manager::getNetworkCapabilities) } catch (_: SecurityException) { null }
        ?: return ArtworkConnection(false, false)
    return ArtworkConnection(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED), capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
}
