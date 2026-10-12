> Root integration corrections: new plan type is DownloadPlan (pricing already owns AcquisitionPlan); use SHA-256 spelling and explicit snapshot attempt. See ACQUISITION-INDEPENDENT-REVIEW.md for mandatory consent/file/commit/budget protocols before binding.

# Proposed acquisition contracts and additive schema 3

Read-only proposal for root review, based on f4760ba and `/workspace/scratch/k3-personal-source-review.md`. No implementation, migration or tests run. Source review supersedes the previous generic 8/sec suggestion: English search is sequential, at least 600 ms between starts; bulk metadata at most 10/minute; 429 pauses at least 30 seconds or longer Retry-After. Full Android/64 requirements/213 parity remain unchanged. Schema3 finishes durable acquisition infrastructure, not catalog/rulings/search acceptance.

## Exact proposed core file: AcquisitionContracts.kt

The following is the intended small capability surface. All existing aggregate contracts remain intact. Production construction must validate known provider/URI/source-policy combinations, not trust arbitrary caller labels. Policy constructor validation enforces finite positive values; measured limits are supplied by reviewed plan factories rather than guessed defaults.

```kotlin
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
        require(profileVersion.isNotBlank())
        require(maxTransferBytes > 0 && maxArtifactBytes > 0 && maxDecodedBytes > 0)
        require(maxRecords > 0 && maxPages > 0 && maxPageBytes > 0)
        require(maxRecordBytes > 0 && maxChunkBytes > 0 && maxNesting > 0)
        require(maxRecordBytes <= maxPageBytes)
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
)

public data class AcquisitionPlan(
    public val jobId: EntityId,
    public val request: AcquisitionRequest,
    public val planHash: ContentHash,
    public val createdAt: UtcTimestamp,
)

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
    init { require(bytes >= 0 && pageCount >= 0) }
}

public data class AcquisitionSnapshot(
    public val plan: AcquisitionPlan,
    public val revision: Revision,
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
)

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
```

Validate `DomainFailure.message` against current Values.kt at integration (it currently has this property). `planHash` is SHA-256 of a canonical versioned plan encoding excluding job ID/time; plan fields become immutable once created. Changing URI/query/version/size/language scope requires a new plan and exact-intent consent. `DownloadIntent.version` must identify that immutable acquisition intent (e.g. local `plan-v1:<fingerprint>`), **not** claim an unknown upstream version. The plan factory computes it before `plan()`; validate resource/source/kind/language/checksum/expectedBytes and fingerprint consistency. Hash input excludes that self-referential version field. RemoteFile currently admits only reviewed official TXT/rulings sources, and EnglishSearch only the fixed reviewed Scryfall query. No arbitrary/mixed-language bulk route is admitted by these three scope values. Future optional-language support adds a reviewed explicit scope rather than relabeling it English.

Implementation injects core `Database`, `NetworkClient`, `SettingsRepository`, `Clock`, `IdSource` and an internal platform artifact store/connectivity adapter. It has **no storage module dependency**, no catalog/rules imports and no catalog/rules parsing. EnglishSearch necessarily validates the HTTP list envelope plus each object's `id`/`lang`, and writes each JSON object; other fields remain opaque. `SettingsRepository.read()` supplies authoritative persisted current consent. start/resume and each new request/checkpoint check `explicitUserApproval`, nonrevocation and `permits(plan.request.intent)`. Never accept caller-created consent as sufficient. Revocation observation stops an in-flight response; check again before publishing artifact. Preserve already downloaded bytes without further transfer unless removal was explicitly chosen. Scheduler interruption is INTERRUPTED; user cancel is CANCELLED and does not auto-restart. PAUSED preserves partial bytes. Resume FAILED only for documented retriable failures with unchanged source/plan; CANCELLED requires a new job/start action. COMPLETED means artifact acquired, never catalog installed.

`observe()` terminal failure is truthful Outcome; coroutine cancellation propagates. `openArtifact()` throws ArtifactReadException for unavailable/corrupt/read failures and CancellationException unchanged; no empty success. Each open checks persisted COMPLETED + matching artifact metadata, streams bounded bytes, verifies length/hash and only emits final after those checks. Concurrent deletion uses reader pins; no public path, no URI fabricated from a private path. Raw gzip is returned unchanged; parser owns bounded inflation/CRC and decoded-byte ceiling. RemoteFile COMPLETE_TRANSFER does not certify parse validity. JSONL English traversal is already envelope/count validated but not canonical-card validated. `upstreamChecksumMatched=false` means none was supplied (a supplied mismatch is a failure); computed SHA is never an upstream signature.

Progress counters are Long, monotonic within an attempt. Job revision advances at committed mutations. list() keyset cursor binds descending immutable `(created_seconds,created_nano,job_id)` and an initial high-water mark; snapshot provider `command-library`, generation null and revision unknown/null: a listing is not an atomic snapshot across mutable job states. Numeric limits are measured profile values and every public count/length/string/URI/payload receives finite adapter validation.

## Import source changes needed with this boundary

Keep existing `CatalogImport` signature and add defaulted fields to `CatalogImportSource`, changing its current contentUri to nullable:

```kotlin
public enum class ImportInputKind { USER_CONTENT_URI, VERIFIED_ARTIFACT }
// Within CatalogImportSource, replace:
public val contentUri: ResourceUri?,
// Append, preserving existing constructors through defaults:
public val inputKind: ImportInputKind = ImportInputKind.USER_CONTENT_URI,
public val artifactJobId: EntityId? = null,
public val framing: ArtifactFraming = ArtifactFraming.JSON_ARRAY,
public val compression: ArtifactCompression = ArtifactCompression.NONE,
public val acquisition: AcquisitionProvenance? = null,
```

Invariant: USER_CONTENT_URI has a `content://` contentUri and null artifactJobId/acquisition; VERIFIED_ARTIFACT has null contentUri, nonnull artifactJobId and acquisition. Parser supports the declared framing; framing=TEXT is invalid for cards. Core data constructors validate these combinations. Shell/controller obtains VerifiedArtifact from status, constructs source from its metadata, and passes `downloadService.openArtifact(id,cancel)`; no fake URI. expectedBytes/hash describe exact openArtifact representation, with separate decoded measurement. Persist input kind/job provenance in SourceMetadataCodec and expose retrieval interval rather than manufacturing sourceDate. This change touches lookup/error paths and codecs that currently assume nonnull contentUri and requires a compatibility review.

Current catalog_generation.content_uri is NOT NULL. Schema3 can remain jobs-only: for artifact imports store empty legacy content_uri sentinel **only paired with explicit input kind and artifact provenance in the versioned metadata payload**, and never parse/display that sentinel as a URI. Better subsequent catalog schema migration makes content_uri nullable and adds input_kind/artifact_job_id; root should schedule that alongside full importer changes. Do not implement fake `content://` storage. The earlier current-schema local lookup must continue decoding old version payloads. CatalogImportLimits also needs measured profile ceilings instead of 8 MiB/10k; these values remain pending the full crawl.

The identical real-input distinction is required for `LocalRulesImportSource` (nullable contentUri, defaulted inputKind/artifactJobId/acquisition fields with updated constructor validation). Its existing TEXT/NONE parser can keep framing implicit, and `reopen = { openArtifact(id,cancel) }` supplies two verified passes. Existing rules `source_payload` is versioned and rules_corpus.content_uri has the same legacy nullability issue; schedule explicit nullable/input-kind migration with importer wiring, or the reviewed non-URI sentinel convention above. Do not suppress the current content:// guard while leaving a misleading public source type.

## Exact SQL schema 3 proposal

Three tables only; no card/ruling/search projection tables yet. Strings named `*_payload` are bounded, versioned, strict typed JSON codecs (unknown schema version is typed unavailable/corrupt, never ignored). No headers, credentials, bodies, paths or secrets in plan/failure payloads. Job's plan payload stores the immutable full AcquisitionPlan. Timestamps are UTC seconds+nanos; payloads retain all Knowledge states. `partial_key`/`artifact_key` are random private basenames generated by the store, never external paths.

```sql
CREATE TABLE acquisition_job (
 job_id TEXT NOT NULL PRIMARY KEY,
 payload_version INTEGER NOT NULL,
 plan_payload TEXT NOT NULL,
 plan_sha256 TEXT NOT NULL,
 state TEXT NOT NULL,
 phase TEXT NOT NULL,
 revision INTEGER NOT NULL,
 attempt INTEGER NOT NULL,
 owner_token TEXT,
 created_seconds INTEGER NOT NULL,
 created_nano INTEGER NOT NULL,
 updated_seconds INTEGER NOT NULL,
 updated_nano INTEGER NOT NULL,
 started_seconds INTEGER,
 started_nano INTEGER,
 consent_id TEXT,
 partial_key TEXT NOT NULL,
 durable_offset INTEGER NOT NULL,
 transferred_bytes INTEGER NOT NULL,
 record_count INTEGER NOT NULL,
 page_count INTEGER NOT NULL,
 expected_records INTEGER,
 next_page_uri TEXT,
 first_page_sha256 TEXT,
 final_uri TEXT,
 strong_etag TEXT,
 last_modified TEXT,
 expected_representation_bytes INTEGER,
 response_encoding TEXT,
 failure_payload TEXT
);
CREATE INDEX index_acquisition_job_state_created ON acquisition_job(state,created_seconds,created_nano,job_id);
CREATE INDEX index_acquisition_job_created ON acquisition_job(created_seconds,created_nano,job_id);
CREATE UNIQUE INDEX index_acquisition_job_partial_key ON acquisition_job(partial_key);

CREATE TABLE acquisition_artifact (
 job_id TEXT NOT NULL PRIMARY KEY,
 artifact_key TEXT NOT NULL,
 byte_count INTEGER NOT NULL,
 sha256 TEXT NOT NULL,
 framing TEXT NOT NULL,
 compression TEXT NOT NULL,
 verified_payload TEXT NOT NULL,
 payload_version INTEGER NOT NULL,
 completed_seconds INTEGER NOT NULL,
 completed_nano INTEGER NOT NULL,
 FOREIGN KEY(job_id) REFERENCES acquisition_job(job_id) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE UNIQUE INDEX index_acquisition_artifact_key ON acquisition_artifact(artifact_key);

CREATE TABLE acquisition_seen_id (
 job_id TEXT NOT NULL,
 raw_id TEXT NOT NULL,
 page_number INTEGER NOT NULL,
 PRIMARY KEY(job_id,raw_id),
 FOREIGN KEY(job_id) REFERENCES acquisition_job(job_id) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX index_acquisition_seen_id_page ON acquisition_seen_id(job_id,page_number);
```

Exact proposed Room entities (in the existing storage schema package; these mirror the DDL):

```kotlin
package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "acquisition_job", indices = [
    Index(value = ["state", "created_seconds", "created_nano", "job_id"], name = "index_acquisition_job_state_created"),
    Index(value = ["created_seconds", "created_nano", "job_id"], name = "index_acquisition_job_created"),
    Index(value = ["partial_key"], name = "index_acquisition_job_partial_key", unique = true),
])
internal data class AcquisitionJobEntity(
    @PrimaryKey val job_id: String,
    val payload_version: Int,
    val plan_payload: String,
    val plan_sha256: String,
    val state: String,
    val phase: String,
    val revision: Long,
    val attempt: Int,
    val owner_token: String?,
    val created_seconds: Long,
    val created_nano: Int,
    val updated_seconds: Long,
    val updated_nano: Int,
    val started_seconds: Long?,
    val started_nano: Int?,
    val consent_id: String?,
    val partial_key: String,
    val durable_offset: Long,
    val transferred_bytes: Long,
    val record_count: Long,
    val page_count: Long,
    val expected_records: Long?,
    val next_page_uri: String?,
    val first_page_sha256: String?,
    val final_uri: String?,
    val strong_etag: String?,
    val last_modified: String?,
    val expected_representation_bytes: Long?,
    val response_encoding: String?,
    val failure_payload: String?,
)

@Entity(tableName = "acquisition_artifact", foreignKeys = [ForeignKey(
    entity = AcquisitionJobEntity::class, parentColumns = ["job_id"],
    childColumns = ["job_id"], onDelete = ForeignKey.CASCADE,
)], indices = [Index(value = ["artifact_key"], name = "index_acquisition_artifact_key", unique = true)])
internal data class AcquisitionArtifactEntity(
    @PrimaryKey val job_id: String,
    val artifact_key: String,
    val byte_count: Long,
    val sha256: String,
    val framing: String,
    val compression: String,
    val verified_payload: String,
    val payload_version: Int,
    val completed_seconds: Long,
    val completed_nano: Int,
)

@Entity(tableName = "acquisition_seen_id", primaryKeys = ["job_id", "raw_id"],
    foreignKeys = [ForeignKey(entity = AcquisitionJobEntity::class,
        parentColumns = ["job_id"], childColumns = ["job_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["job_id", "page_number"], name = "index_acquisition_seen_id_page")])
internal data class AcquisitionSeenIdEntity(
    val job_id: String,
    val raw_id: String,
    val page_number: Long,
)
```

Do not add defaults or schema CHECK clauses unless fresh Room create and migration SQL match exactly. Strict domain codecs enforce enum/range/pair invariants on read and write. AcquisitionService uses these table columns via Database/parameterized SQL, without importing entities or the storage module.

Migration2To3 follows the existing suspend Migration1To2 pattern: execute these statements under Room's migration transaction; add the three entities to StorageSchemaDatabase; bump version to3; register both migrations; generate real KSP `3.json` from compilation. No changes to any existing entity, indexes or SQL data. Test 2->3 and 1->2->3 preservation/reopen/integrity and interrupted migration; no destructive fallback and no hand-authored export claiming generated evidence. Foreign keys intentionally do not reference settings (DataStore) or catalog generations. Artifact retention cannot cascade-delete imported card/rules data.

## State/CAS and file recovery protocol

`start` transaction reads expected revision/state, validates authoritative consent immediately before transaction, then `UPDATE acquisition_job SET state='QUEUED', consent_id=:consent, revision=revision+1,... WHERE job_id=:id AND revision=:expected AND state='PLANNED'`; verify exactly one affected row. Existing DbTransaction.execute exposes affected rows; if uncertain use readback inside the same writer transaction. A runner claims only QUEUED using a fresh unguessable owner_token and CAS; every checkpoint/update requires matching owner_token/state/revision. Commands pause/cancel clear owner_token and advance revision, so stale workers cannot checkpoint or complete.

Use one application-scoped runner coordinator/scheduler process; on startup, after proving no existing runner owns this app process, RUNNING becomes INTERRUPTED and owner_token clears. A wall-clock expiry alone cannot prove a worker dead. Never let recover() run concurrently with live transfers. Queue scheduling uses job ID uniqueness. Background execution is caller-owned structured work (Android scheduler), not a global detached coroutine in the service.

File/DB protocol: append bounded bytes to partial file; flush/fsync before transaction advancing durable_offset and page/ID checkpoint. After crash, truncate uncheckpointed file tail; if actual file is shorter than durable_offset, invalidate/restart attempt rather than invent bytes. A fresh attempt resets offsets, expected totals, IDs and provenance start atomically; old file cleanup is deferred. Retried request bytes count toward bounded transfer accounting, never silently bypass the budget.

Completion: verify file length/hash and transfer invariants; prepare final private immutable filename; flush/fsync then rename on same filesystem; transaction inserts acquisition_artifact and moves job to COMPLETED together under owner/state CAS. If crash between rename and transaction, recovery finds named candidate deterministically from partial_key/job ID, rechecks bytes, then safely reconciles or restores partial; never treats mere filename existence as verified success. Commit before success emission. On open, missing/corrupt artifact is typed failure and recoverable interrupted/failed state, not fabricated data. Metadata payload records retrieval completion only once artifact validation completes. Noncancellable sections are confined to bounded cleanup/commit handoff, not whole transfer/import. Cancel before final DB commit wins; after commit status remains COMPLETED (cancelling completed jobs is stale/invalid, not retroactive undo).

## Source-specific invariants

RemoteFile: record exact representation Content-Encoding, length and strong ETag. Range/If-Range resume is permitted only for identical URI/final origin/encoding/validator; validate 206 start/end/total before append. 200 means restart, never append. Weak/missing validator means restart after interruption. 416 is failure unless independent full length/hash validation proves complete. A source with no expected checksum can complete with computed SHA and truthful `upstreamChecksumMatched=false`; content hash is identity not authenticity or parser validity. Initial rules TXT and rulings gzip are whole-file transfers only; no arbitrary URLs are resolved from source data.

EnglishSearch: fixed `q=lang:en`, `unique=prints`, `include_extras=true`, `include_variations=true`, `include_multilingual=false`, `order=set`, `dir=asc`; first page=1. Parse URI parameters structurally, require unique expected parameters, no userinfo/fragment, HTTPS/api.scryfall.com/cards/search. next_page must preserve every semantic query parameter and increment page by exactly1; reject alternate origin/path/extra semantic parameter. Validate final response URI too. Require object=list, bounded positive `total_cards` constant across every page, no warnings, correctly typed has_more/data/next_page; every item object=card, nonempty bounded unique producer `id` and lang=en. Never silently skip foreign/malformed objects. Bound each page, object and total pages/bytes. Persist one completed page atomically with all its IDs and the next cursor/offset after artifact append is durable. Unique(job_id,raw_id) rejects duplicates; reconcile total final count equals total_cards and expected pages; current producer page size175 means full intermediate pages175 and final remaining count, as a versioned validated source policy rather than generic parser law. Record observed total, not hardcoded116009. Count drift/duplicates/warnings/invalid cursor => SOURCE_MISMATCH and no verified artifact.

Resume only a persisted page boundary with the exact same plan; revalidate initial page total and `first_page_sha256` before continuing next_page_uri. The digest is over ordered item JSONL bytes using one fixed versioned serializer, and is stored atomically at the first-page checkpoint. This catches first-page changes but not every mid-crawl mutation. Unknown/mismatched digest or count invalidates page resume and restarts a new attempt with all old ID/offset checkpoints cleared. The nullable first_page_sha256 column is included in the exact DDL/entity above (null for RemoteFile or before first page). Persisted transferred_bytes includes revalidation traffic and obeys the total transfer budget. No immutable upstream token exists: even unchanged totals and unique IDs cannot prove a point-in-time source snapshot. On success provenance remains NON_ATOMIC_TRAVERSAL with retrieval interval and unknown sourceDate/sourceVersion; comparison to bulk updated_at is forbidden. Imports activate the locally validated traversal atomically while preserving this upstream limitation. Keep previous installed generation on any acquisition/import failure.

## Review decisions/root next action

Approve the exact capability names, required current-consent lookup, raw artifact byte semantics and schema3 ownership. Implement page resume using the included first_page_sha256 checkpoint; invalidation restarts the attempt explicitly. Prefer explicit nullable/input-kind catalog/rules migration with full importer work over legacy sentinel storage. Replace proposed finite limit profile with actual crawl measurements before production full-corpus claims. Then delegate network acquisition and storage migration under disjoint locks; root owns core contract integration and app/scheduler/DI wiring. Runtime tests not_run; this scratch proposal is the continuation artifact.

Transport integration detail: existing `EndpointProfile.requestsPerSecond` is integral. Use the reviewed search cap2 with an acquisition-layer monotonic minimum600ms start spacing (not profile1, which would unnecessarily force >=11-minute spacing alone at663 pages). Apply 30-second429 cooldown and longer Retry-After globally for the Scryfall source. The source's cache guidance is at least24hours; show installed age and reuse cached data rather than creating implicit launch-time/per-query refresh jobs. Full transfer time and local import time are measured separately and both reported honestly.
