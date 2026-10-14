package com.architectpsg.commandlibrary.core.model

import kotlinx.coroutines.flow.Flow

/**
 * One user-selected local rules document. Selection permits reading this content URI for this
 * import; it is not consent to download from [sourceUri] or to import another language.
 * Unknown source version/date/notice values stay unknown rather than being inferred from a file.
 */
public data class LocalRulesImportSource(
    public val contentUri: ResourceUri,
    public val displayName: String,
    public val provider: ProviderId,
    public val language: LanguageTag = LanguageTag.ENGLISH,
    public val version: Knowledge<String>,
    public val effectiveAt: Knowledge<UtcTimestamp>,
    public val sourceDate: Knowledge<UtcTimestamp>,
    public val sourceUri: Knowledge<ResourceUri>,
    public val notice: Knowledge<SourceNotice>,
    public val expectedChecksum: Knowledge<ContentHash>,
    public val expectedBytes: Knowledge<Long>,
) {
    init {
        require(contentUri.value.startsWith("content://")) { "Rules input must be a selected content URI" }
        require(displayName.isNotBlank() && displayName.toByteArray(Charsets.UTF_8).size <= 512)
        require(language == LanguageTag.ENGLISH) { "Only English local rules input is supported" }
        if (version is Knowledge.Known) require(version.value.isNotBlank())
        if (expectedBytes is Knowledge.Known) require(expectedBytes.value >= 0)
        if (expectedChecksum is Knowledge.Known) {
            require(expectedChecksum.value.algorithm.equals("SHA-256", ignoreCase = true) && expectedChecksum.value.hex.length == 64)
        }
    }
}

/** Metadata for an installed generation; the accepted RulesCorpus remains a separate aggregate DTO. */
public data class LocalRulesCorpus(
    public val id: GenerationId,
    public val source: LocalRulesImportSource,
    public val provenance: Provenance,
    public val status: GenerationStatus,
    public val entryCount: Long,
    public val referenceCount: Long,
) {
    init { require(entryCount >= 0 && referenceCount >= 0) }
}

/** A durable attempt is separate from the last good active corpus. */
public data class LocalRulesImportAttempt(
    public val id: EntityId,
    public val source: LocalRulesImportSource,
    public val phase: JobPhase,
    public val startedAt: UtcTimestamp,
    public val finishedAt: Knowledge<UtcTimestamp>,
    public val failure: DomainFailure?,
)

public data class LocalRulesStatus(
    public val active: Knowledge<LocalRulesCorpus>,
    public val latestAttempt: Knowledge<LocalRulesImportAttempt>,
)

public data class LocalRulesImportResult(
    public val corpus: LocalRulesCorpus,
    public val observedBytes: Long,
    public val computedChecksum: ContentHash,
    public val checksumVerification: ChecksumVerification,
    public val activatedAt: UtcTimestamp,
) {
    init {
        require(observedBytes >= 0)
        require(corpus.status == GenerationStatus.ACTIVE)
        require(computedChecksum.algorithm.equals("SHA-256", ignoreCase = true) && computedChecksum.hex.length == 64)
    }
}

/**
 * Independently injectable local import. [reopen] supplies a fresh byte stream on each call so a
 * bounded two-pass parser can compare exact bytes; implementations must discard staging if either
 * pass or activation fails. Each call returns a fresh cold stream: sequence/offset restart at zero,
 * chunks are contiguous, exactly one is final, and no chunk follows it. Producers close their input
 * on completion, failure and cancellation; read/permission errors cannot become empty success.
 * Reopening must not retain the whole document in memory. Both passes must agree on observed bytes
 * and exact-byte SHA-256; known expected bytes/hash must match or return SOURCE_MISMATCH.
 *
 * Implementations enforce finite byte/chunk/line/entry/reference/index ceilings and bounded batches
 * with backpressure; exceeding a limit rejects input without truncation. Coroutine and token
 * cancellation propagate as CancellationException. Activation is atomic after complete validation.
 * Precommit failure/cancellation preserves the last good corpus. Cleanup can delete only proven
 * uncommitted staging; postcommit cancellation or progress failure must not remove active data.
 * Progress callbacks do not signal durable success. Reopened status resolves a cancellation/commit
 * race and recovers interrupted attempts separately from the installed corpus.
 */
public interface LocalRulesImport {
    public suspend fun `import`(
        source: LocalRulesImportSource,
        reopen: () -> Flow<ByteChunk>,
        onProgress: (JobProgress) -> Unit,
        cancel: CancelToken,
    ): Outcome<LocalRulesImportResult>
}

/**
 * Local capability only, not a partial RulesRepository implementation. A search with null
 * RulesQuery.corpusId uses the active corpus and returns its Page.snapshot. Entry lookup explicitly
 * pins the corpus; replacement must never silently substitute a newer entry with the same RuleId.
 * Null corpus selection binds the active generation atomically for that operation. Every successful
 * page snapshot has a nonnull generation matching all entries/provenance. Cursors bind generation,
 * query text, kinds and deterministic ordering; changed query returns INVALID_INPUT, changed active
 * generation returns STALE_GENERATION. Explicit retired-corpus reads return that retained generation
 * or STALE_GENERATION if unavailable; retention is not guaranteed. Missing IDs in an available
 * corpus return NOT_FOUND. Entry never falls back to active. Adapters document finite query bounds
 * and unsupported syntax, and enforce bounded deterministic pagination. Installed lookup uses
 * stored data and does not depend on retained content URI permission.
 */
public interface LocalRulesLookup {
    public suspend fun status(cancel: CancelToken): Outcome<LocalRulesStatus>
    public suspend fun entry(id: RuleId, corpusId: GenerationId, cancel: CancelToken): Outcome<RuleEntry>
    public suspend fun search(query: RulesQuery, cancel: CancelToken): Outcome<Page<RuleEntry>>
}
/** Unique exact normalized installed glossary title only; never invents a keyword-family mapping.
 * Null corpus binds active atomically; explicit retained corpus stays pinned. Completed absence
 * is Absent, ambiguity Unknown; an incomplete bounded scan is SOURCE_UNAVAILABLE, never no-match.
 */
public interface KeywordRulesLookup {
    public suspend fun lookupKeyword(keyword: String, corpusId: GenerationId?, cancel: CancelToken): Outcome<KeywordRuleLink>
}
