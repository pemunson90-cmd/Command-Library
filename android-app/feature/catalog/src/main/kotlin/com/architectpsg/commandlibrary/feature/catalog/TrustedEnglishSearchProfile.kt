package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** Fixed authority for the user-triggered English printing search traversal. */
internal object TrustedEnglishSearchProfile {
    private val firstPage = ResourceUri(EnglishSearchPageCodec.INITIAL_URI)
    private val unknownVersion = Knowledge.Unknown("Upstream source version is not asserted", SnapshotList.empty())
    private val unknownDate = Knowledge.Unknown("Upstream source date is not asserted", SnapshotList.empty())
    private val unknownBytes = Knowledge.Unknown("Upstream expected bytes are not asserted", SnapshotList.empty())
    private val unknownNotice = Knowledge.Unknown("No reviewed source notice supplied", SnapshotList.empty())
    private val limits = AcquisitionLimits(
        profileVersion = "english-search-v1",
        maxTransferBytes = 2_147_483_648L,
        maxArtifactBytes = 2_147_483_648L,
        maxDecodedBytes = 2_147_483_648L,
        maxRecords = 500_000L,
        maxPages = 2_858L,
        maxPageBytes = 16_777_216,
        maxRecordBytes = 8_388_608,
        maxChunkBytes = 65_536,
        maxNesting = 32,
    )
    private val endpoint = EndpointProfile(
        id = EntityId("scryfall-search-v1"),
        origin = Origin("https", "api.scryfall.com", 443),
        allowedRedirectOrigins = SnapshotList.empty(),
        secret = null,
        requestsPerSecond = 2,
        retry = RetryPolicy(1, 30.seconds, 24.hours, true),
    )

    fun request(displayName: String, wifiOnly: Boolean): AcquisitionRequest {
        require(displayName.isNotBlank() && displayName.length <= 512 && displayName.toByteArray(Charsets.UTF_8).size <= 512)
        return AcquisitionRequest(
            intent = DownloadIntent(
                id = EntityId("scryfall-english-search-v1"),
                kind = DownloadKind.CATALOG,
                source = ProviderId("scryfall"),
                resource = firstPage,
                version = "ENGLISH_SEARCH_v1",
                language = LanguageTag.ENGLISH,
                packId = null,
                checksum = null,
                expectedBytes = unknownBytes,
            ),
            displayName = displayName,
            source = AcquisitionSource.EnglishSearch(firstPage, "english-search-query-v1"),
            languageScope = AcquisitionLanguageScope.ENGLISH_CARDS,
            sourceVersion = unknownVersion,
            sourceDate = unknownDate,
            notice = unknownNotice,
            limits = limits,
            wifiOnly = wifiOnly,
        )
    }

    /** Equality with a freshly generated request keeps caller metadata out of the policy. */
    fun validate(candidate: AcquisitionRequest): Outcome<Unit> {
        val canonical = runCatching { request(candidate.displayName, candidate.wifiOnly) }.getOrNull()
        return if (canonical == candidate) Outcome.Success(Unit)
        else Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, "Invalid English search request", SnapshotList.empty()))
    }

    /** Selects only bounded same-scope page targets; the engine separately rejects redirects. */
    fun endpointFor(candidate: AcquisitionRequest, uri: ResourceUri): EndpointProfile {
        require(validate(candidate) is Outcome.Success) { "Invalid English search request" }
        val page = EnglishSearchPageCodec.pageNumber(uri.value)
        require(page in 1..limits.maxPages.toInt()) { "English search page exceeds profile budget" }
        return endpoint
    }
}
