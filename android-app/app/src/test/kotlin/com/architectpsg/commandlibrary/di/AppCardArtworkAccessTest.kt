package com.architectpsg.commandlibrary.di

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.architectpsg.commandlibrary.core.common.ConsentCoordinator
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.network.WireDispatchGuard
import com.architectpsg.commandlibrary.feature.shell.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration

/** Entirely synthetic metadata/bytes. Native codec and real upstream verification remain separate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppCardArtworkAccessTest {
    @Test fun parentOffersUseExactNormalLinkAndImmutableGenerationIntent() = runBlocking {
        val f = Fixture(); val offer = f.offer()
        assertNull(offer.faceIndex); assertEquals("normal", offer.variant)
        assertEquals(f.record.images.single().uri, offer.intent.resource)
        assertEquals(DownloadKind.IMAGES, offer.intent.kind)
        assertEquals(LanguageTag.ENGLISH, offer.intent.language)
        assertEquals(f.offer(), offer); assertEquals(0, f.sends)
        assertNull(offer.intent.packId); assertNull(offer.intent.checksum)
        assertTrue(offer.intent.expectedBytes is Knowledge.Unknown)
    }

    @Test fun actualFaceIndicesAndArtistsArePreservedWithoutGuessingSides() = runBlocking {
        val f = Fixture()
        fun face(index: Int) = PrintingFaceAsset(PrintingFaceIdentity(f.id, index, absent), LanguageTag.ENGLISH,
            absent, absent, absent, absent, Knowledge.Known("Synthetic Artist $index"), absent,
            SnapshotList.of(f.asset("grid", "https://cards.scryfall.io/grid/$index.webp")), SnapshotList.empty())
        f.record = f.record.copy(images = SnapshotList.empty(), faceAssets = SnapshotList.of(face(4), face(9)))
        val offers = ok(f.access.prepare(f.id, f.generation, token))
        assertEquals(listOf(4, 9), offers.map { it.faceIndex })
        assertEquals(Knowledge.Known("Synthetic Artist 9"), offers[1].artist)
        assertNotEquals(offers[0].intent.id, offers[1].intent.id)
    }

    @Test fun untrustedOrAmbiguousNormalCannotFallBackToGrid() = runBlocking {
        for (uri in listOf("http://cards.scryfall.io/a.jpg", "https://cards.scryfall.io.evil/a.jpg",
            "https://user@cards.scryfall.io/a.jpg", "https://cards.scryfall.io:444/a.jpg",
            "https://cards.scryfall.io/a.jpg#f", "https://cards.scryfall.io/../a.jpg",
            "https://cards.scryfall.io/%2e%2e/a.jpg", "https://cards.scryfall.io/a%2fb.jpg")) {
            val f = Fixture(); f.record = f.record.copy(images = SnapshotList.of(f.asset("normal", uri), f.asset("grid", "https://cards.scryfall.io/grid/a.webp")))
            assertTrue(uri, f.access.prepare(f.id, f.generation, token) is Outcome.Failure)
        }
        val f = Fixture(); f.record = f.record.copy(images = SnapshotList.of(f.record.images[0], f.record.images[0]))
        assertTrue(f.access.prepare(f.id, f.generation, token) is Outcome.Failure)
    }

    @Test fun missingMetadataAndWrongGenerationNeverInventOffers() = runBlocking {
        val f = Fixture(); f.record = f.record.copy(images = SnapshotList.empty())
        assertTrue(ok(f.access.prepare(f.id, f.generation, token)).isEmpty())
        f.record = f.record.copy(provenance = f.record.provenance.copy(generation = null))
        assertTrue(f.access.prepare(f.id, f.generation, token) is Outcome.Failure)
    }

    @Test fun preparationAndPermissionAreLocalAndApprovalIsNeverImplicit() = runBlocking {
        val f = Fixture(); val offer = f.offer()
        assertFalse(ok(f.access.permitted(offer, f.consentId, token)))
        assertTrue(f.access.load(offer, f.consentId, token) is Outcome.Unavailable)
        assertEquals(0, f.sends); assertEquals(0, f.approvals)
    }

    @Test fun tamperedOfferAndWifiPolicyCannotAuthorizeWire() = runBlocking {
        val f = Fixture(); val offer = f.offer(); f.approve(offer)
        assertFalse(ok(f.access.permitted(offer.copy(variant = "grid"), f.consentId, token)))
        f.wifi = false
        assertTrue(f.access.load(offer, f.consentId, token) is Outcome.Unavailable)
        assertEquals(0, f.enqueues)
        // Connectivity controls acquisition, not local permission to display existing pixels.
        assertTrue(ok(f.access.permitted(offer, f.consentId, token)))
    }

    @Test fun identicalSecondDispatchAndEqualCopiedSpecAreRejected() = runBlocking {
        for (copy in listOf(false, true)) {
            val f = Fixture(); val offer = f.offer(); f.approve(offer); f.secondDispatch = true; f.copySpec = copy
            assertTrue(f.access.load(offer, f.consentId, token) is Outcome.Failure)
            assertEquals(if (copy) 0 else 1, f.enqueues); assertEquals(1, f.closes)
        }
    }

    @Test fun responseHeadersBodyAndSignatureFailClosedBeforeDecoder() = runBlocking {
        val f = Fixture(); val offer = f.offer(); f.approve(offer)
        val invalid = listOf(
            f.response(byteArrayOf(1, 2, 3)),
            f.response(byteArrayOf(), headers = listOf(HttpHeader("Content-Type", "image/jpeg"))),
            f.response(byteArrayOf(-1, -40, -1, -39), headers = listOf(HttpHeader("Content-Type", "image/webp"))),
            f.response(byteArrayOf(-1, -40, -1, -39), headers = listOf(HttpHeader("Content-Type", "image/jpeg"), HttpHeader("Content-Type", "image/jpeg"))),
            f.response(byteArrayOf(-1, -40, -1, -39), headers = listOf(HttpHeader("Content-Type", "image/jpeg"), HttpHeader("Content-Encoding", "gzip"))),
            f.response(ByteArray(CardArtworkAccess.MAX_COMPRESSED_BYTES.toInt() + 1)),
        )
        for (response in invalid) {
            f.reply = response
            assertTrue(f.access.load(offer, f.consentId, token) is Outcome.Failure)
        }
    }

    @Test fun revocationDuringTransferCancelsAndClosesWithoutPublication() = runBlocking {
        val f = Fixture(); val offer = f.offer(); f.approve(offer); f.hold = CompletableDeferred()
        val loading = async { f.access.load(offer, f.consentId, token) }
        withTimeout(2_000) { f.entered.await() }; f.records = SnapshotList.empty()
        assertTrue(withTimeout(2_000) { loading.await() } is Outcome.Unavailable)
        assertEquals(1, f.closes)
    }

    @Test fun cancellationWaitsForNoncooperativeDecodeBeforeSecondOwner() = runBlocking {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var decodes = 0
        val f = Fixture(decoder = { _, _, _ ->
            decodes++; started.complete(Unit)
            withContext(NonCancellable) { release.await() }
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        })
        val offer = f.offer(); f.approve(offer); f.reply = f.response(byteArrayOf(-1, -40, -1, -39))
        val first = launch { f.access.load(offer, f.consentId, token) }
        started.await(); first.cancel()
        val second = launch { f.access.load(offer, f.consentId, token) }
        delay(150); assertEquals(1, decodes)
        release.complete(Unit); first.join(); second.join(); assertEquals(2, decodes)
    }

    @Test fun sourceChangesOrSettingsFailuresDuringTransferPreventPublication() = runBlocking {
        for (settingsFailure in listOf(false, true)) {
            val f = Fixture(); val offer = f.offer(); f.approve(offer); f.hold = CompletableDeferred()
            val loading = async { f.access.load(offer, f.consentId, token) }
            withTimeout(2_000) { f.entered.await() }
            if (settingsFailure) f.settingsFailure = true
            else f.record = f.record.copy(provenance = f.record.provenance.copy(generation = GenerationId("replacement")))
            assertTrue(withTimeout(2_000) { loading.await() } is Outcome.Failure)
            assertEquals(1, f.closes)
        }
    }

    @Test fun dispatchRechecksApprovalAfterPreflightAndRejectsChangedDestination() = runBlocking {
        val f = Fixture(); val offer = f.offer(); f.approve(offer)
        f.beforeGuard = { f.records = SnapshotList.empty() }
        assertTrue(f.access.load(offer, f.consentId, token) is Outcome.Unavailable)
        assertEquals(0, f.enqueues)
        f.beforeGuard = {}; f.approve(offer); f.dispatchUri = ResourceUri("https://cards.scryfall.io/other.jpg")
        assertTrue(f.access.load(offer, f.consentId, token) is Outcome.Failure)
        assertEquals(0, f.enqueues)
    }

    @Test fun successfulAcquisitionMakesOneBoundedRequestAndDoesNotRecycleExposedBitmap() = runBlocking {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val f = Fixture(decoder = { _, _, _ -> bitmap }); val offer = f.offer(); f.approve(offer)
        f.reply = f.response(byteArrayOf(-1, -40, -1, -39))
        val result = ok(f.access.load(offer, f.consentId, token))
        assertSame(bitmap, result.bitmap); assertFalse(bitmap.isRecycled)
        assertEquals(1, f.sends); assertEquals(1, f.enqueues); assertEquals(1, f.closes)
        val spec = requireNotNull(f.lastSpec)
        assertEquals(HttpMethod.GET, spec.request.method); assertNull(spec.request.body)
        assertEquals(1, spec.endpoint.retry.maxAttempts)
        assertEquals(RetryClassification.NEVER_AUTOMATIC, spec.retryClassification)
        assertEquals(CardArtworkAccess.MAX_COMPRESSED_BYTES, spec.maxResponseBytes)
        assertNull(spec.endpoint.secret); assertTrue(spec.endpoint.allowedRedirectOrigins.isEmpty())
        assertEquals(listOf("Accept", "Accept-Encoding", "User-Agent"), spec.request.headers.map { it.name })
        assertEquals("identity", spec.request.headers[1].value)
        bitmap.recycle()
    }

    @Test fun duplicateFacesWrongPrintingAndOversizedMetadataAreRejected() = runBlocking {
        val f = Fixture()
        fun face(id: CardId = f.id) = PrintingFaceAsset(PrintingFaceIdentity(id, 7, absent), LanguageTag.ENGLISH,
            absent, absent, absent, absent, absent, absent, SnapshotList.empty(), SnapshotList.empty())
        f.record = f.record.copy(faceAssets = SnapshotList.of(face(), face()))
        assertTrue(f.access.prepare(f.id, f.generation, token) is Outcome.Failure)
        f.record = f.record.copy(faceAssets = SnapshotList.of(face(CardId(ProviderId("scryfall"), "different"))))
        assertTrue(f.access.prepare(f.id, f.generation, token) is Outcome.Failure)
        f.record = f.record.copy(faceAssets = SnapshotList.empty(), images = SnapshotList.copyOf((0..32).map {
            f.asset("variant-$it", "https://cards.scryfall.io/image-$it.jpg")
        }))
        assertTrue(f.access.prepare(f.id, f.generation, token) is Outcome.Failure)
    }

    @Test fun staticWebpChunkProfileRejectsAnimationUnknownChunksAndLengthMismatch() {
        fun riff(chunk: String, payload: ByteArray): ByteArray {
            val padded = payload + if (payload.size % 2 == 1) byteArrayOf(0) else byteArrayOf()
            val body = "WEBP".toByteArray() + chunk.toByteArray() + byteArrayOf(payload.size.toByte(), 0, 0, 0) + padded
            return "RIFF".toByteArray() + byteArrayOf(body.size.toByte(), 0, 0, 0) + body
        }
        assertTrue(LocalArtworkDecoder.signature(riff("VP8 ", byteArrayOf(1, 2)), "grid"))
        assertFalse(LocalArtworkDecoder.signature(riff("ANIM", byteArrayOf(1, 2)), "grid"))
        assertFalse(LocalArtworkDecoder.signature(riff("EXIF", byteArrayOf(1, 2)), "grid"))
        assertFalse(LocalArtworkDecoder.signature(riff("VP8 ", byteArrayOf(1, 2)).dropLast(1).toByteArray(), "grid"))
    }

    @Test fun boundsAndActualCodecRejectOversizedOrMalformedPixels() = runBlocking {
        val image = Bitmap.createBitmap(2049, 1, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream(); image.compress(Bitmap.CompressFormat.JPEG, 80, stream); image.recycle()
        assertTrue(runCatching { LocalArtworkDecoder.decode(stream.toByteArray(), "normal", token) }.isFailure)
        assertTrue(runCatching { LocalArtworkDecoder.decode(byteArrayOf(-1, -40, -1, -39), "normal", token) }.isFailure)
    }

    private class Fixture(decoder: suspend (ByteArray, String, CancelToken) -> Bitmap = LocalArtworkDecoder::decode) {
        val id = CardId(ProviderId("scryfall"), "synthetic-printing")
        val generation = GenerationId("synthetic-generation")
        val consentId = EntityId("synthetic-consent")
        @Volatile var record = PrintingRecord(id, absent, "test", absent, "1", LanguageTag.ENGLISH, absent, absent, absent,
            SnapshotList.of(asset("normal", "https://cards.scryfall.io/normal/synthetic.jpg?1")), SnapshotList.empty(), SnapshotList.empty(),
            SnapshotList.empty(), SnapshotList.empty(), Provenance(ProviderId("scryfall"), null, Knowledge.Known("test-v1"), absent,
                UtcTimestamp(0), absent, generation, null, null))
        @Volatile var records = SnapshotList.empty<ConsentRecord>(); var wifi = true
        var sends = 0; var enqueues = 0; var closes = 0; var approvals = 0
        var secondDispatch = false; var copySpec = false; @Volatile var settingsFailure = false
        var beforeGuard: () -> Unit = {}; var dispatchUri: ResourceUri? = null; var lastSpec: RequestSpec? = null
        var hold: CompletableDeferred<Unit>? = null; val entered = CompletableDeferred<Unit>()
        var reply: ByteResponse? = null
        val access = AppCardArtworkAccess(ApplicationProvider.getApplicationContext(), object : CatalogPrintingLookup {
            override suspend fun printing(id: CardId, cancel: CancelToken) = Outcome.Success(record)
            override suspend fun printings(id: OracleId, page: PageRequest, cancel: CancelToken): Outcome<Page<PrintingRecord>> = error("unused")
        }, object : SettingsRepository {
            override suspend fun read(cancel: CancelToken): Outcome<SettingsSnapshot> = if (settingsFailure) Outcome.Failure(DomainFailure(FailureCode.PERSISTENCE, "Synthetic read failure", SnapshotList.empty())) else Outcome.Success(SettingsSnapshot(Revision(0), cache = CachePolicy(0, true, true),
                budget = absent, providerProfileIds = SnapshotList.empty(), sourceNotices = SnapshotList.empty(), consents = records, updatedAt = UtcTimestamp(0)))
            override suspend fun update(patch: SettingsPatch, expected: Revision, cancel: CancelToken): Outcome<SettingsSnapshot> = error("no writes")
            override suspend fun approve(intent: DownloadIntent, cancel: CancelToken): Outcome<ConsentRecord> { approvals++; error("no implicit approval") }
        }, ConsentCoordinator(), object : Clock {
            override fun utcNow() = UtcTimestamp(0)
            override fun monotonicNow() = Duration.ZERO
        }, transportFactory = { guard -> object : NetworkClient {
            override suspend fun send(request: RequestSpec, cancel: CancelToken): Outcome<ByteResponse> {
                sends++; lastSpec = request; beforeGuard()
                guard.dispatch(if (copySpec) request.copy() else request, dispatchUri ?: request.request.uri, cancel) { enqueues++ }
                if (secondDispatch) guard.dispatch(request, request.request.uri, cancel) { enqueues++ }
                entered.complete(Unit); hold?.await()
                return Outcome.Success(reply ?: response(byteArrayOf(1)))
            }
            override fun stream(request: RequestSpec, cancel: CancelToken): Flow<Outcome<ByteChunk>> = emptyFlow()
            override suspend fun close() { closes++ }
        } }, connection = { ArtworkConnection(true, wifi) }, decoder = decoder)
        fun asset(variant: String, uri: String) = PrintingImageAsset(variant, ResourceUri(uri), absent, absent)
        suspend fun offer() = ok(access.prepare(id, generation, token)).single()
        fun approve(offer: CardArtworkOffer) { records = SnapshotList.of(ConsentRecord(consentId, offer.intent, UtcTimestamp(0), null, true)) }
        fun response(bytes: ByteArray, headers: List<HttpHeader> = listOf(HttpHeader("Content-Type", "image/jpeg"))) = ByteResponse(
            HttpMetadata(200, SnapshotList.copyOf(headers), record.images[0].uri, UtcTimestamp(0), absent, absent), SnapshotBytes(bytes))
    }

    companion object {
        private val absent = Knowledge.Absent("Synthetic source does not supply this field")
        private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
        private fun <T> ok(value: Outcome<T>): T = (value as Outcome.Success).value
    }
}
