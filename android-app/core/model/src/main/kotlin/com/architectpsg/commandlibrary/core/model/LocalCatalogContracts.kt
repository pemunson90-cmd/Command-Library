package com.architectpsg.commandlibrary.core.model

import kotlinx.coroutines.flow.Flow

/** Capabilities are independently injectable; an importer is never a partial CatalogRepository. */
public interface CatalogLookup {
    public suspend fun lookupName(input: String, page: PageRequest, cancel: CancelToken): Outcome<Page<CardRecord>>
}
public data class LocalCatalogStatus(
    public val mode: StorageMode,
    public val defaultLanguage: LanguageTag,
    public val activeGenerations: SnapshotList<CatalogGeneration>,
    public val source: Knowledge<ProviderId>,
    public val sourceDate: Knowledge<UtcTimestamp>,
    public val fetchedAt: Knowledge<UtcTimestamp>,
    public val installedLanguages: SnapshotList<LanguageTag>,
    public val initialized: Boolean,
)
public interface CatalogStatus {
    public suspend fun status(cancel: CancelToken): Outcome<LocalCatalogStatus>
}
/** sourceDate is a claimed source date, never inferred from local file modification time. */
public data class CatalogImportSource(
    public val contentUri: ResourceUri,
    public val displayName: String,
    public val provider: ProviderId,
    public val language: LanguageTag,
    public val sourceDate: Knowledge<UtcTimestamp>,
    public val sourceUri: Knowledge<ResourceUri>,
    public val expectedChecksum: Knowledge<ContentHash>,
    public val expectedBytes: Knowledge<Long>,
    public val notice: Knowledge<SourceNotice>,
)
public data class CatalogImportLimits(
    public val maxInputBytes: Long = 8L * 1024 * 1024,
    public val maxPrintingRecords: Int = 10_000,
    public val maxRecordBytes: Int = 128 * 1024,
    public val batchSize: Int = 128,
    public val maxNesting: Int = 32,
) {
    init {
        require(maxInputBytes in 1..8L * 1024 * 1024)
        require(maxPrintingRecords in 1..10_000)
        require(maxRecordBytes in 1..128 * 1024)
        require(batchSize in 1..128)
        require(maxNesting in 1..32)
    }
}
public enum class ChecksumVerification { MATCHED_EXPECTED, COMPUTED_WITHOUT_EXPECTATION }
public data class CatalogImportVerification(
    public val observedBytes: Long,
    public val computedChecksum: ContentHash,
    public val checksumVerification: ChecksumVerification,
    public val validatedRelationships: Boolean,
    public val rejectedRecords: Long,
) { init { require(observedBytes >= 0 && rejectedRecords >= 0) } }
public data class CatalogImportResult(
    public val generation: CatalogGeneration,
    public val counts: CatalogCounts,
    public val provenance: Provenance,
    public val verification: CatalogImportVerification,
    public val activatedAt: UtcTimestamp,
)
public interface CatalogImport {
    public suspend fun `import`(
        source: CatalogImportSource,
        chunks: Flow<ByteChunk>,
        onProgress: (JobProgress) -> Unit,
        cancel: CancelToken,
    ): Outcome<CatalogImportResult>
}
