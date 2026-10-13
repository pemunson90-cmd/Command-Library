package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Fts5
import androidx.room3.FtsOptions
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Additive schema 2 for a user-selected local rules document. SQL columns are stable identities
 * and query keys; complete payloads preserve Knowledge states, provenance and reference evidence.
 * Unknown metadata stays null in projection columns and retains its typed state in the payload.
 * Timestamps use epoch seconds plus nano, matching catalog schema 1. No ruling data is included.
 */
@Entity(
    tableName = "rules_corpus",
    indices = [Index(value = ["provider", "status"], name = "index_rules_corpus_provider_status")],
)
internal data class RulesCorpusEntity(
    @PrimaryKey @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "language") val language: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "content_uri") val contentUri: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "source_payload") val sourcePayload: String,
    @ColumnInfo(name = "provenance_payload") val provenancePayload: String,
    @ColumnInfo(name = "upstream_version") val upstreamVersion: String?,
    @ColumnInfo(name = "effective_at_epoch_seconds") val effectiveAtEpochSeconds: Long?,
    @ColumnInfo(name = "effective_at_nano") val effectiveAtNano: Int?,
    @ColumnInfo(name = "source_date_epoch_seconds") val sourceDateEpochSeconds: Long?,
    @ColumnInfo(name = "source_date_nano") val sourceDateNano: Int?,
    @ColumnInfo(name = "source_uri") val sourceUri: String?,
    @ColumnInfo(name = "observed_at_epoch_seconds") val observedAtEpochSeconds: Long,
    @ColumnInfo(name = "observed_at_nano") val observedAtNano: Int,
    @ColumnInfo(name = "content_sha256") val contentSha256: String,
    @ColumnInfo(name = "observed_bytes") val observedBytes: Long,
    @ColumnInfo(name = "entry_count") val entryCount: Long,
    @ColumnInfo(name = "reference_count") val referenceCount: Long,
    @ColumnInfo(name = "payload_schema_version") val payloadSchemaVersion: Int,
    @ColumnInfo(name = "activated_at_epoch_seconds") val activatedAtEpochSeconds: Long?,
    @ColumnInfo(name = "activated_at_nano") val activatedAtNano: Int?,
    @ColumnInfo(name = "input_kind", defaultValue = "'USER_CONTENT_URI'") val inputKind: String = "USER_CONTENT_URI",
    /** Provenance only; independent of acquisition job/artifact retention. */
    @ColumnInfo(name = "artifact_job_id") val artifactJobId: String? = null,
)

/** The repository writes singleton_id = 1; replacing its pointer is an atomic transaction. */
@Entity(
    tableName = "rules_active",
    foreignKeys = [ForeignKey(
        entity = RulesCorpusEntity::class,
        parentColumns = ["generation_id"], childColumns = ["generation_id"],
        onDelete = ForeignKey.NO_ACTION,
    )],
    indices = [Index(value = ["generation_id"], name = "index_rules_active_generation_id")],
)
internal data class RulesActiveEntity(
    @PrimaryKey @ColumnInfo(name = "singleton_id") val singletonId: Int,
    @ColumnInfo(name = "generation_id") val generationId: String,
)

@Entity(
    tableName = "rule_entry",
    primaryKeys = ["corpus_id", "provider", "local_id"],
    foreignKeys = [ForeignKey(
        entity = RulesCorpusEntity::class,
        parentColumns = ["generation_id"], childColumns = ["corpus_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["corpus_id", "ordinal"], unique = true, name = "index_rule_entry_corpus_ordinal")],
)
internal data class RuleEntryEntity(
    @ColumnInfo(name = "corpus_id") val corpusId: String,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "local_id") val localId: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "number") val number: String?,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "ordinal") val ordinal: Long,
    @ColumnInfo(name = "entry_payload") val entryPayload: String,
)

/** Resolved targets can be unknown or outside this corpus; only the owning entry is an FK. */
@Entity(
    tableName = "rule_reference",
    primaryKeys = ["corpus_id", "from_provider", "from_local_id", "ref_ordinal"],
    foreignKeys = [ForeignKey(
        entity = RuleEntryEntity::class,
        parentColumns = ["corpus_id", "provider", "local_id"],
        childColumns = ["corpus_id", "from_provider", "from_local_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
internal data class RuleReferenceEntity(
    @ColumnInfo(name = "corpus_id") val corpusId: String,
    @ColumnInfo(name = "from_provider") val fromProvider: String,
    @ColumnInfo(name = "from_local_id") val fromLocalId: String,
    @ColumnInfo(name = "ref_ordinal") val refOrdinal: Long,
    @ColumnInfo(name = "raw_target") val rawTarget: String,
    @ColumnInfo(name = "resolved_provider") val resolvedProvider: String?,
    @ColumnInfo(name = "resolved_local_id") val resolvedLocalId: String?,
    @ColumnInfo(name = "evidence_payload") val evidencePayload: String,
)

/**
 * Durable audit independent of corpus lifetime. No FK for generation_id: a failed or interrupted
 * import must remain inspectable after its staging corpus is removed. Payload owns failure detail.
 */
@Entity(
    tableName = "rules_import_attempt",
    indices = [Index(value = ["started_at_epoch_seconds", "started_at_nano", "attempt_id"], name = "index_rules_import_attempt_started")],
)
internal data class RulesImportAttemptEntity(
    @PrimaryKey @ColumnInfo(name = "attempt_id") val attemptId: String,
    @ColumnInfo(name = "generation_id") val generationId: String?,
    @ColumnInfo(name = "source_payload") val sourcePayload: String,
    @ColumnInfo(name = "attempt_payload") val attemptPayload: String,
    @ColumnInfo(name = "phase") val phase: String,
    @ColumnInfo(name = "started_at_epoch_seconds") val startedAtEpochSeconds: Long,
    @ColumnInfo(name = "started_at_nano") val startedAtNano: Int,
    @ColumnInfo(name = "finished_at_epoch_seconds") val finishedAtEpochSeconds: Long?,
    @ColumnInfo(name = "finished_at_nano") val finishedAtNano: Int?,
    @ColumnInfo(name = "failure_payload") val failurePayload: String?,
    @ColumnInfo(name = "payload_schema_version") val payloadSchemaVersion: Int,
)

/** FTS rows must be inserted/deleted with their corpus in the same repository transaction. */
@Fts5(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics", "2"],
    notIndexed = ["corpus_id", "provider", "local_id"],
)
@Entity(tableName = "rules_text_fts")
internal data class RulesTextFtsEntity(
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "corpus_id") val corpusId: String,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "local_id") val localId: String,
)
