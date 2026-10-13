package com.architectpsg.commandlibrary.core.model

import java.net.URI
import kotlinx.coroutines.flow.Flow

public enum class SourceInputKind { USER_CONTENT_URI, VERIFIED_ARTIFACT }

/** Explicit full English capability; the small CatalogImport contract keeps its existing limits. */
public data class CatalogBulkImportLimits(
    public val profileVersion: String = "full-english-catalog-v1",
    public val maxRawBytes: Long = 2L * 1024 * 1024 * 1024,
    public val maxDecodedBytes: Long = 2L * 1024 * 1024 * 1024,
    public val maxPrintingRecords: Int = 500_000,
    public val maxRecordBytes: Int = 8 * 1024 * 1024,
    public val maxOrdinaryBatchBytes: Int = 2 * 1024 * 1024,
    public val maxBatchRecords: Int = 1_000,
    public val maxChunkBytes: Int = 64 * 1024,
    public val maxNesting: Int = 32,
) {
    init {
        require(profileVersion.isNotBlank() && profileVersion.length <= 128)
        require(maxRawBytes in 1..2L * 1024 * 1024 * 1024)
        require(maxDecodedBytes in 1..2L * 1024 * 1024 * 1024)
        require(maxPrintingRecords in 1..500_000 && maxRecordBytes in 1..8 * 1024 * 1024)
        require(maxOrdinaryBatchBytes in 1..2 * 1024 * 1024 && maxBatchRecords in 1..1_000)
        require(maxChunkBytes in 1..64 * 1024 && maxNesting in 1..32)
    }
}

/** URI is a real local locator, not upstream origin or proof of download consent.
 * VERIFIED_ARTIFACT must be rebound to the current completed job and private provider before use.
 */
public data class CatalogBulkImportSource(
    public val contentUri: ResourceUri,
    public val displayName: String,
    public val provider: ProviderId,
    public val language: LanguageTag,
    public val sourceDate: Knowledge<UtcTimestamp>,
    public val sourceUri: Knowledge<ResourceUri>,
    public val expectedChecksum: Knowledge<ContentHash>,
    public val expectedBytes: Knowledge<Long>,
    public val notice: Knowledge<SourceNotice>,
    public val framing: ArtifactFraming,
    public val compression: ArtifactCompression,
    public val inputKind: SourceInputKind = SourceInputKind.USER_CONTENT_URI,
    public val verifiedArtifact: VerifiedArtifact? = null,
) {
    init {
        val uri = URI(contentUri.value)
        require(uri.scheme.equals("content", true) && !uri.rawAuthority.isNullOrBlank() && uri.rawUserInfo == null && uri.rawFragment == null)
        require(displayName.isNotBlank() && displayName.length <= 512)
        require(provider == ProviderId("scryfall") && language == LanguageTag.ENGLISH)
        require(framing == ArtifactFraming.JSON_ARRAY || framing == ArtifactFraming.JSONL)
        if (expectedBytes is Knowledge.Known) require(expectedBytes.value > 0)
        if (expectedChecksum is Knowledge.Known) requireFullSha256(expectedChecksum.value)
        when (inputKind) {
            SourceInputKind.USER_CONTENT_URI -> require(verifiedArtifact == null)
            SourceInputKind.VERIFIED_ARTIFACT -> {
                val artifact = requireNotNull(verifiedArtifact)
                require(artifact.provenance.provider == provider && artifact.provenance.languageScope == AcquisitionLanguageScope.ENGLISH_CARDS)
                require(artifact.framing == framing && artifact.compression == compression)
                require(expectedBytes == Knowledge.Known(artifact.bytes) && expectedChecksum == Knowledge.Known(artifact.sha256))
                require(sourceDate == artifact.provenance.sourceDate && sourceUri == Knowledge.Known(artifact.provenance.requestedUri))
                require(notice == artifact.provenance.notice)
            }
        }
    }
}

public data class CatalogBulkImportResult(
    public val installed: CatalogImportResult,
    public val decodedBytes: Long,
    public val decodedSha256: ContentHash,
    public val printingMembershipRows: Long,
    public val acquiredArtifact: Knowledge<VerifiedArtifact>,
) {
    init {
        require(decodedBytes >= 0 && printingMembershipRows >= 0)
        requireFullSha256(decodedSha256)
    }
}

public interface CatalogBulkImport {
    /** Caller owns execution; acquisition/EOF/raw+decoded validation precedes atomic activation. */
    public suspend fun `import`(
        source: CatalogBulkImportSource,
        chunks: Flow<ByteChunk>,
        onProgress: (JobProgress) -> Unit,
        cancel: CancelToken,
    ): Outcome<CatalogBulkImportResult>
}

public interface CatalogPrintingLookup {
    /** Includes printing-only/reversible records without fabricating a whole-card Oracle. */
    public suspend fun printing(id: CardId, cancel: CancelToken): Outcome<PrintingRecord>
    /** Generation-bound cursor; de-duplicates printings with multiple matching source faces. */
    public suspend fun printings(id: OracleId, page: PageRequest, cancel: CancelToken): Outcome<Page<PrintingRecord>>
}

public interface CatalogOracleLookup {
    public suspend fun oracle(id: OracleId, cancel: CancelToken): Outcome<CardRecord>
}

private fun requireFullSha256(hash: ContentHash): Unit {
    require(hash.algorithm.equals("SHA-256", true) && hash.hex.length == 64)
}
