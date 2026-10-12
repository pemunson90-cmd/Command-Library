package com.architectpsg.commandlibrary.core.model

import kotlinx.coroutines.flow.Flow

public enum class ArtifactFraming { TEXT, JSON_ARRAY, JSONL }
public enum class ArtifactCompression { NONE, GZIP }
public enum class AcquisitionLanguageScope {
    ENGLISH_CARDS, ENGLISH_RULES, RULINGS_LANGUAGE_NOT_ASSERTED
}
public enum class AcquisitionState {
    PLANNED, QUEUED, RUNNING, PAUSED, WAITING_NETWORK, WAITING_WIFI,
    WAITING_CONSENT, INTERRUPTED, FAILED, CANCELLED, COMPLETED
}
public enum class AcquisitionPhase { TRANSFERRING, VERIFYING, COMPLETE }
public enum class AcquisitionConsistency { FILE_REPRESENTATION, NON_ATOMIC_TRAVERSAL }

public data class AcquisitionLimits(
    public val profileVersion: String,
    public val maxTransferBytes: Long,
    public val maxArtifactBytes: Long,
    public val maxDecodedBytes: Long,
    public val maxRecords: Long,
    public val maxPages: Long,
    public val maxPageBytes: Int,
    public val maxRecordBytes: Int,
    public val maxChunkBytes: Int,
    public val maxNesting: Int,
) {
    init {
        require(profileVersion.isNotBlank() && profileVersion.length <= 128)
        require(maxTransferBytes > 0 && maxArtifactBytes > 0 && maxDecodedBytes > 0)
        require(maxRecords > 0 && maxPages > 0 && maxPageBytes > 0)
        require(maxRecordBytes > 0 && maxChunkBytes > 0 && maxNesting > 0)
        require(maxRecordBytes <= maxPageBytes)
        require(maxChunkBytes <= 64 * 1024 && maxNesting <= 128)
    }
}

public sealed interface AcquisitionSource {
    public data class RemoteFile(
        public val uri: ResourceUri,
        public val allowedRedirectOrigins: SnapshotList<Origin>,
        public val framing: ArtifactFraming,
        public val compression: ArtifactCompression,
        public val expectedBytes: Knowledge<Long>,
        public val expectedSha256: Knowledge<ContentHash>,
    ) : AcquisitionSource

    /** Output is UTF-8 JSONL with LF, uncompressed; not original page-response bytes. */
    public data class EnglishSearch(
        public val firstPage: ResourceUri,
        public val queryPolicyVersion: String,
    ) : AcquisitionSource
}

public data class AcquisitionRequest(
    public val intent: DownloadIntent,
    public val displayName: String,
    public val source: AcquisitionSource,
    public val languageScope: AcquisitionLanguageScope,
    public val sourceVersion: Knowledge<String>,
    public val sourceDate: Knowledge<UtcTimestamp>,
    public val notice: Knowledge<SourceNotice>,
    public val limits: AcquisitionLimits,
    public val wifiOnly: Boolean,
) {
    init {
        require(displayName.isNotBlank() && displayName.length <= 512)
        if (source is AcquisitionSource.RemoteFile) {
            if (source.expectedBytes is Knowledge.Known) require(source.expectedBytes.value in 1..limits.maxTransferBytes)
            if (source.expectedSha256 is Knowledge.Known) requireSha256(source.expectedSha256.value)
        }
    }
}

public data class DownloadPlan(
    public val jobId: EntityId,
    public val request: AcquisitionRequest,
    public val planHash: ContentHash,
    public val createdAt: UtcTimestamp,
) { init { requireSha256(planHash) } }

public data class AcquisitionProvenance(
    public val provider: ProviderId,
    public val requestedUri: ResourceUri,
    public val finalUri: ResourceUri,
    public val sourceVersion: Knowledge<String>,
    public val sourceDate: Knowledge<UtcTimestamp>,
    public val retrievalStartedAt: UtcTimestamp,
    public val retrievalCompletedAt: UtcTimestamp,
    public val etag: Knowledge<String>,
    public val lastModified: Knowledge<String>,
    public val consistency: AcquisitionConsistency,
    public val languageScope: AcquisitionLanguageScope,
    public val notice: Knowledge<SourceNotice>,
)

public data class VerifiedArtifact(
    public val jobId: EntityId,
    public val framing: ArtifactFraming,
    public val compression: ArtifactCompression,
    public val bytes: Long,
    public val sha256: ContentHash,
    public val upstreamChecksumMatched: Boolean,
    public val recordCount: Knowledge<Long>,
    public val pageCount: Long,
    public val provenance: AcquisitionProvenance,
) {
    init {
        require(bytes >= 0 && pageCount >= 0)
        requireSha256(sha256)
        if (recordCount is Knowledge.Known) require(recordCount.value >= 0)
    }
}

public data class AcquisitionSnapshot(
    public val plan: DownloadPlan,
    public val revision: Revision,
    public val attempt: Int,
    public val state: AcquisitionState,
    public val phase: AcquisitionPhase,
    public val transferredBytes: Long,
    public val durableArtifactBytes: Long,
    public val acquiredRecords: Long,
    public val acquiredPages: Long,
    public val expectedRecords: Knowledge<Long>,
    public val consentId: EntityId?,
    public val updatedAt: UtcTimestamp,
    public val artifact: Knowledge<VerifiedArtifact>,
    public val failure: DomainFailure?,
) {
    init {
        require(attempt >= 0 && transferredBytes >= 0 && durableArtifactBytes >= 0 && acquiredRecords >= 0 && acquiredPages >= 0)
        if (expectedRecords is Knowledge.Known) require(expectedRecords.value >= 0)
    }
}

private fun requireSha256(hash: ContentHash): Unit {
    require(hash.algorithm.equals("SHA-256", ignoreCase = true) && hash.hex.length == 64)
}

/** Indicates artifact lookup/read/integrity failure on the requested Flow<ByteChunk> surface. */
public class ArtifactReadException(public val failure: DomainFailure) : RuntimeException(failure.message)

public interface DownloadService {
    /** Validates and persists PLANNED only; never contacts the network or grants consent. */
    public suspend fun plan(request: AcquisitionRequest, cancel: CancelToken): Outcome<AcquisitionSnapshot>
    /** QUEUED after durable exact-intent consent validation; completion means acquisition only. */
    public suspend fun start(jobId: EntityId, expected: Revision, consentId: EntityId, cancel: CancelToken): Outcome<AcquisitionSnapshot>
    public suspend fun pause(jobId: EntityId, expected: Revision, cancel: CancelToken): Outcome<AcquisitionSnapshot>
    public suspend fun resume(jobId: EntityId, expected: Revision, consentId: EntityId, cancel: CancelToken): Outcome<AcquisitionSnapshot>
    public suspend fun cancel(jobId: EntityId, expected: Revision, cancel: CancelToken): Outcome<AcquisitionSnapshot>
    public suspend fun status(jobId: EntityId, cancel: CancelToken): Outcome<AcquisitionSnapshot>
    public suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<AcquisitionSnapshot>>
    public fun observe(jobId: EntityId, cancel: CancelToken): Flow<Outcome<AcquisitionSnapshot>>
    /** Cold/reopenable verified bytes; zero-based contiguous chunks, one final chunk, no paths. */
    public fun openArtifact(jobId: EntityId, cancel: CancelToken): Flow<ByteChunk>
}

/** Separate worker boundary: execution belongs to caller/scheduler structured coroutine scope. */
public interface DownloadRunner {
    public suspend fun run(jobId: EntityId, cancel: CancelToken): Outcome<AcquisitionSnapshot>
    /** Reconcile files/DB and interrupted attempts on app startup; does not create consent. */
    public suspend fun recover(cancel: CancelToken): Outcome<Unit>
}
