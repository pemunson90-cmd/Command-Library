package com.architectpsg.commandlibrary.core.model

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

public data class Origin(public val scheme: String, public val host: String, public val port: Int) { init { require(scheme in listOf("https", "http")); require(host.isNotBlank()); require(port in 1..65535) } }
public enum class HttpMethod { GET, HEAD, POST, PUT, PATCH, DELETE }
public enum class RequestCharge { NONCHARGED, POTENTIALLY_CHARGED }
public enum class RetryClassification { SAFE_IDEMPOTENT, EXPLICIT_IDEMPOTENCY_KEY, NEVER_AUTOMATIC }
public data class HttpHeader(public val name: String, public val value: String) { init { require(name.isNotBlank()); require(!name.contains('\n') && !value.contains('\n') && !value.contains('\r')) } }
public data class RetryPolicy(
    public val maxAttempts: Int,
    public val initialDelay: Duration,
    public val maximumDelay: Duration,
    public val honorRetryAfter: Boolean,
) { init { require(maxAttempts >= 1); require(initialDelay >= Duration.ZERO && maximumDelay >= initialDelay) } }
public data class EndpointProfile(
    public val id: EntityId,
    public val origin: Origin,
    public val allowedRedirectOrigins: SnapshotList<Origin>,
    public val secret: SecretRef?,
    public val requestsPerSecond: Int,
    public val retry: RetryPolicy,
) { init { require(requestsPerSecond > 0) } }
public data class Request(
    public val method: HttpMethod,
    public val uri: ResourceUri,
    public val headers: SnapshotList<HttpHeader>,
    public val body: SnapshotBytes?,
)
public data class RequestSpec(
    public val endpoint: EndpointProfile,
    public val request: Request,
    public val timeout: Duration,
    public val charge: RequestCharge,
    public val retryClassification: RetryClassification,
    public val idempotencyKey: String?,
    public val maxResponseBytes: Long,
) {
    init { require(timeout > Duration.ZERO && maxResponseBytes > 0); require(charge != RequestCharge.POTENTIALLY_CHARGED || retryClassification == RetryClassification.NEVER_AUTOMATIC) }
}
public data class HttpMetadata(
    public val statusCode: Int,
    public val headers: SnapshotList<HttpHeader>,
    public val finalUri: ResourceUri,
    public val receivedAt: UtcTimestamp,
    public val retryAfter: Knowledge<Duration>,
    public val chargeStatus: Knowledge<Boolean>,
) { init { require(statusCode in 100..599) } }
public data class ByteResponse(public val metadata: HttpMetadata, public val body: SnapshotBytes)
public data class ByteChunk(
    public val sequence: Long,
    public val offset: Long,
    public val bytes: SnapshotBytes,
    public val metadata: HttpMetadata?,
    public val final: Boolean,
) { init { require(sequence >= 0 && offset >= 0) } }
public interface NetworkClient {
    public suspend fun send(request: RequestSpec, cancel: CancelToken): Outcome<ByteResponse>
    public fun stream(request: RequestSpec, cancel: CancelToken): Flow<Outcome<ByteChunk>>
    public suspend fun close(): Unit
}

public sealed interface SqlValue {
    public data object Null : SqlValue
    public data class Text(public val value: String) : SqlValue
    public data class Integer(public val value: Long) : SqlValue
    public data class DecimalText(public val value: String) : SqlValue { init { java.math.BigDecimal(value) } }
    public data class Blob(public val value: SnapshotBytes) : SqlValue
}
public data class SqlParameter(public val name: String, public val value: SqlValue) { init { require(name.isNotBlank()) } }
/** Internal bounded query permits one lookahead row; public pages still contain at most 100 items. */
public data class SqlRequest(
    public val statement: String,
    public val parameters: SnapshotList<SqlParameter>,
    public val maxRows: Int = PageRequest.MAX_LIMIT,
) {
    init {
        require(statement.isNotBlank())
        require(maxRows in 1..MAX_ROWS)
    }
    public companion object { public const val MAX_ROWS: Int = PageRequest.MAX_LIMIT + 1 }
}
public data class RowColumn(public val name: String, public val value: SqlValue)
public data class RowData(public val columns: SnapshotList<RowColumn>) { init { require(columns.map { it.name }.distinct().size == columns.size) } }
public data class DbCapabilities(
    public val engineVersion: String,
    public val schemaVersion: Int,
    public val fts5: Boolean,
    public val transactions: Boolean,
    public val foreignKeys: Boolean,
    public val maxBoundParameters: Int,
) { init { require(schemaVersion >= 0 && maxBoundParameters > 0) } }
public data class MigrationMetadata(
    public val fromVersion: Int,
    public val toVersion: Int,
    public val checksum: ContentHash,
    public val destructive: Boolean,
    public val reversible: Boolean,
)
public data class MigrationEvidence(
    public val migration: MigrationMetadata,
    public val appliedAt: UtcTimestamp,
    public val validated: Boolean,
    public val evidence: SnapshotList<Evidence>,
)
public data class GenerationMetadata(
    public val dataset: String,
    public val activeGeneration: Knowledge<GenerationId>,
    public val stagedGeneration: Knowledge<GenerationId>,
    public val schemaVersion: Int,
)
public data class DbWriteResult(public val affectedRows: Long) { init { require(affectedRows >= 0) } }
/** The rollback latch is read-only to callbacks and irreversible for the transaction's lifetime. */
public sealed interface DbTransactionState {
    public data object Active : DbTransactionState
    public data class RollbackOnly(public val status: Outcome<Nothing>) : DbTransactionState {
        init { require(status !is Outcome.Success) }
    }
}
/** Validation rejection is typed; catching this exception never clears the rollback latch. */
public class TransactionAbort(public val failure: DomainFailure) : RuntimeException(failure.message)

/**
 * query/execute must latch their first non-success Outcome before returning it. The latch cannot
 * be reset, including when the callback catches an abort or ignores a returned failure.
 * abort first latches Failure(error), then throws TransactionAbort(error).
 * Implementations must check caller cancellation and never convert CancellationException to an
 * Outcome; cancellation rolls back and escapes unchanged.
 */
public interface DbTransaction {
    public val state: DbTransactionState
    public suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>>
    public suspend fun execute(request: SqlRequest, cancel: CancelToken): Outcome<DbWriteResult>
    public fun abort(error: DomainFailure): Nothing
}
public interface Database {
    public suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>>
    /**
     * Commit only after action returns normally, caller cancellation has been checked, and the
     * transaction's irreversible latch is Active. A returned Failure/Unavailable/Unknown Outcome
     * also latches rollback, even though action returned normally. Rollback returns the matching
     * outer Failure/Unavailable/Unknown (first latched status wins), never Success(non-success).
     * TransactionAbort rolls back and yields its typed Failure unless an earlier status latched.
     * Other thrown exceptions roll back and are classified by the adapter. Cancellation always
     * rolls back and is rethrown unchanged; it takes precedence over all typed statuses.
     */
    public suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T>
    public suspend fun capabilities(cancel: CancelToken): Outcome<DbCapabilities>
}

public data class SecretScope(public val providerProfileId: EntityId, public val origin: Origin, public val purpose: String)
public data class SecretRef(public val id: EntityId, public val scope: SecretScope)
public interface SecretVault {
    public suspend fun put(scope: SecretScope, value: String, cancel: CancelToken): Outcome<SecretRef>
    public suspend fun read(ref: SecretRef, cancel: CancelToken): Outcome<String>
    public suspend fun remove(ref: SecretRef, cancel: CancelToken): Outcome<Unit>
}
public enum class ThemePreference { SYSTEM, LIGHT, DARK }
public enum class DownloadKind { CATALOG, LANGUAGE_PACK, RULES, IMAGES, MODEL, VARIANT }
public data class DownloadIntent(
    public val id: EntityId,
    public val kind: DownloadKind,
    public val source: ProviderId,
    public val resource: ResourceUri,
    public val version: String,
    public val language: LanguageTag?,
    public val packId: EntityId?,
    public val checksum: ContentHash?,
    public val expectedBytes: Knowledge<Long>,
) {
    init { require(version.isNotBlank()); require(kind != DownloadKind.LANGUAGE_PACK || language != null) }
}
/** Only approve() or an explicit approval flow creates this record; restoring preferences does not. */
public data class ConsentRecord(
    public val id: EntityId,
    public val intent: DownloadIntent,
    public val approvedAt: UtcTimestamp,
    public val revokedAt: UtcTimestamp?,
    public val explicitUserApproval: Boolean,
) {
    init { require(explicitUserApproval) }
    public fun permits(candidate: DownloadIntent): Boolean = revokedAt == null && intent == candidate
}
public data class CachePolicy(public val imageBytes: Long, public val retainRulings: Boolean, public val wifiOnlyDownloads: Boolean) { init { require(imageBytes >= 0) } }
public data class SettingsSnapshot(
    public val revision: Revision,
    public val dataLanguage: LanguageTag = LanguageTag.ENGLISH,
    public val theme: ThemePreference = ThemePreference.SYSTEM,
    public val storageMode: StorageMode = StorageMode.FULL,
    public val cache: CachePolicy,
    public val budget: Knowledge<DecimalAmount>,
    public val providerProfileIds: SnapshotList<EntityId>,
    public val sourceNotices: SnapshotList<SourceNotice>,
    public val consents: SnapshotList<ConsentRecord>,
    public val updatedAt: UtcTimestamp,
)
public sealed interface SettingChange {
    public data class DataLanguage(public val language: LanguageTag) : SettingChange
    public data class Theme(public val theme: ThemePreference) : SettingChange
    public data class Storage(public val mode: StorageMode) : SettingChange
    public data class Cache(public val policy: CachePolicy) : SettingChange
    public data class Budget(public val amount: Knowledge<DecimalAmount>) : SettingChange
    public data class ProviderProfiles(public val ids: SnapshotList<EntityId>) : SettingChange
    public data class RevokeConsent(public val id: EntityId, public val removeDownloadedData: Boolean) : SettingChange
}
public data class SettingsPatch(public val changes: SnapshotList<SettingChange>)
public interface SettingsRepository {
    public suspend fun read(cancel: CancelToken): Outcome<SettingsSnapshot>
    public suspend fun update(patch: SettingsPatch, expected: Revision, cancel: CancelToken): Outcome<SettingsSnapshot>
    public suspend fun approve(intent: DownloadIntent, cancel: CancelToken): Outcome<ConsentRecord>
}
