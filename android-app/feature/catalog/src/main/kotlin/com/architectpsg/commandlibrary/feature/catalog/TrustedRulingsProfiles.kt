package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** Canonical policies only. A structural candidate must first become a verified durable receipt. */
internal object TrustedRulingsProfiles {
    const val DISCOVERY_VERSION = "RULINGS_DISCOVERY_v1"
    const val FILE_VERSION = "RULINGS_FILE_v1:"
    private val provider = ProviderId("scryfall")
    private val version = Knowledge.Unknown("Upstream source version is not asserted", SnapshotList.empty())
    private val date = Knowledge.Unknown("Upstream source date is not asserted", SnapshotList.empty())
    private val notice = Knowledge.Unknown("No reviewed source notice supplied", SnapshotList.empty())
    private val checksum = Knowledge.Unknown("Upstream checksum is not supplied", SnapshotList.empty())
    private val metadataBytes = Knowledge.Unknown("Upstream metadata length is not asserted", SnapshotList.empty())
    private val metadataLimits = AcquisitionLimits("rulings-discovery-v1", 1024 * 1024L, 1024 * 1024L, 1024 * 1024L,
        1, 1, 1024 * 1024, 1024 * 1024, 64 * 1024, 16)
    private val fileLimits = AcquisitionLimits("rulings-jsonl-gzip-v1", 2_147_483_648L, 2_147_483_648L, 2_147_483_648L,
        500_000, 1, 128 * 1024, 128 * 1024, 64 * 1024, 16)
    private val apiEndpoint = EndpointProfile(EntityId("scryfall-rulings-metadata-v1"), Origin("https", "api.scryfall.com", 443),
        SnapshotList.empty(), null, 10, RetryPolicy(1, 30.seconds, 24.hours, true))
    // Conservative client pacing; no claim that the direct file origin publishes this rate limit.
    private val fileEndpoint = EndpointProfile(EntityId("scryfall-rulings-file-v1"), Origin("https", "data.scryfall.io", 443),
        SnapshotList.empty(), null, 10, RetryPolicy(1, 30.seconds, 24.hours, true))

    fun discovery(displayName: String, wifiOnly: Boolean): AcquisitionRequest {
        label(displayName)
        val uri = ResourceUri(ScryfallRulingsMetadataCodec.SINGLE_ENDPOINT)
        return AcquisitionRequest(DownloadIntent(EntityId("scryfall-rulings-discovery-v1"), DownloadKind.CATALOG, provider, uri,
            DISCOVERY_VERSION, null, null, null, metadataBytes), displayName,
            AcquisitionSource.RemoteFile(uri, SnapshotList.empty(), ArtifactFraming.TEXT, ArtifactCompression.NONE, metadataBytes, checksum),
            AcquisitionLanguageScope.RULINGS_LANGUAGE_NOT_ASSERTED, version, date, notice, metadataLimits, wifiOnly)
    }
    fun file(facts: RulingsProfileFacts, displayName: String, wifiOnly: Boolean): AcquisitionRequest {
        label(displayName)
        val bytes = Knowledge.Known(facts.candidate.compressedBytes)
        return AcquisitionRequest(DownloadIntent(EntityId("scryfall-rulings-file-v1"), DownloadKind.CATALOG, provider,
            facts.candidate.downloadUri, FILE_VERSION + facts.receiptId.value, null, null, null, bytes), displayName,
            AcquisitionSource.RemoteFile(facts.candidate.downloadUri, SnapshotList.empty(), ArtifactFraming.JSONL, ArtifactCompression.GZIP, bytes, checksum),
            AcquisitionLanguageScope.RULINGS_LANGUAGE_NOT_ASSERTED, version, Knowledge.Known(facts.candidate.updatedAt), notice, fileLimits, wifiOnly)
    }
    fun isDiscovery(request: AcquisitionRequest): Boolean = runCatching { discovery(request.displayName, request.wifiOnly) == request }.getOrDefault(false)
    fun endpoint(request: AcquisitionRequest): EndpointProfile = if(isDiscovery(request)) apiEndpoint else fileEndpoint
    fun receiptId(request: AcquisitionRequest): EntityId? = request.intent.version.takeIf { it.startsWith(FILE_VERSION) }
        ?.removePrefix(FILE_VERSION)?.takeIf { receiptIdentity.matches(it) }?.let(::EntityId)
    fun identity(value: String) { require(receiptIdentity.matches(value)) }
    private fun label(value: String) { require(value.isNotBlank() && value.toByteArray(Charsets.UTF_8).size <= 512) }
    private val receiptIdentity = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
}

/** Small canonical facts only; complete metadata bodies never live in the synchronous wire cache. */
internal data class RulingsProfileFacts(val receiptId: EntityId, val candidate: ScryfallRulingsCandidate, val metadataHash: ContentHash) {
    init {
        TrustedRulingsProfiles.identity(receiptId.value)
        val size = receiptId.value.toByteArray().size + candidate.bulkId.value.toByteArray().size +
            candidate.metadataUri.value.toByteArray().size + candidate.downloadUri.value.toByteArray().size + metadataHash.hex.length + 128
        require(size <= 8 * 1024)
    }
}

/** One engine uses this dispatcher for both families; cache publication belongs to the receipt owner. */
internal class TrustedCatalogProfiles(initial: List<RulingsProfileFacts> = emptyList()) {
    private val facts = ConcurrentHashMap<String, RulingsProfileFacts>()
    init { require(initial.size <= RulingsReceiptBudgets.MAX_RECEIPTS); initial.forEach(::publishVerified) }
    @Synchronized fun publishVerified(value: RulingsProfileFacts) {
        val old = facts[value.receiptId.value]
        require(old == null || old == value) { "Conflicting source receipt" }
        require(old != null || facts.size < RulingsReceiptBudgets.MAX_RECEIPTS)
        facts[value.receiptId.value] = value
    }
    fun matchesVerified(value: RulingsProfileFacts): Boolean = facts[value.receiptId.value] == value
    fun validate(request: AcquisitionRequest): Outcome<Unit> {
        if(request.source is AcquisitionSource.EnglishSearch) return TrustedEnglishSearchProfile.validate(request)
        val canonical = runCatching {
            if(request.intent.version == TrustedRulingsProfiles.DISCOVERY_VERSION) TrustedRulingsProfiles.discovery(request.displayName, request.wifiOnly)
            else TrustedRulingsProfiles.receiptId(request)?.let { facts[it.value] }?.let { TrustedRulingsProfiles.file(it, request.displayName, request.wifiOnly) }
        }.getOrNull()
        return if(canonical != null && canonical == request) Outcome.Success(Unit)
        else Outcome.Failure(bulkError(FailureCode.INVALID_INPUT, "Unissued or invalid catalog source request"))
    }
    fun endpointFor(request: AcquisitionRequest, uri: ResourceUri): EndpointProfile {
        if(request.source is AcquisitionSource.EnglishSearch) return TrustedEnglishSearchProfile.endpointFor(request, uri)
        require(validate(request) is Outcome.Success && uri == request.intent.resource) { "Unadmitted catalog wire target" }
        return TrustedRulingsProfiles.endpoint(request)
    }
}

internal object RulingsReceiptBudgets {
    const val MAX_RECEIPTS = 4096
    const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
}
