package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Durable acquisition checkpoints only; typed payload codecs own domain validation. */
@Entity(tableName = "acquisition_job", indices = [
    Index(value = ["state", "created_seconds", "created_nano", "job_id"], name = "index_acquisition_job_state_created"),
    Index(value = ["created_seconds", "created_nano", "job_id"], name = "index_acquisition_job_created"),
    Index(value = ["partial_key"], name = "index_acquisition_job_partial_key", unique = true),
])
internal data class AcquisitionJobEntity(
    @PrimaryKey @ColumnInfo(name = "job_id") val jobId: String,
    @ColumnInfo(name = "payload_version") val payloadVersion: Int,
    @ColumnInfo(name = "plan_payload") val planPayload: String,
    @ColumnInfo(name = "plan_sha256") val planSha256: String,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "phase") val phase: String,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "attempt") val attempt: Int,
    @ColumnInfo(name = "owner_token") val ownerToken: String?,
    @ColumnInfo(name = "created_seconds") val createdSeconds: Long,
    @ColumnInfo(name = "created_nano") val createdNano: Int,
    @ColumnInfo(name = "updated_seconds") val updatedSeconds: Long,
    @ColumnInfo(name = "updated_nano") val updatedNano: Int,
    @ColumnInfo(name = "started_seconds") val startedSeconds: Long?,
    @ColumnInfo(name = "started_nano") val startedNano: Int?,
    @ColumnInfo(name = "consent_id") val consentId: String?,
    @ColumnInfo(name = "partial_key") val partialKey: String,
    @ColumnInfo(name = "durable_offset") val durableOffset: Long,
    @ColumnInfo(name = "transferred_bytes") val transferredBytes: Long,
    @ColumnInfo(name = "record_count") val recordCount: Long,
    @ColumnInfo(name = "page_count") val pageCount: Long,
    @ColumnInfo(name = "expected_records") val expectedRecords: Long?,
    @ColumnInfo(name = "next_page_uri") val nextPageUri: String?,
    @ColumnInfo(name = "first_page_sha256") val firstPageSha256: String?,
    @ColumnInfo(name = "final_uri") val finalUri: String?,
    @ColumnInfo(name = "strong_etag") val strongEtag: String?,
    @ColumnInfo(name = "last_modified") val lastModified: String?,
    @ColumnInfo(name = "expected_representation_bytes") val expectedRepresentationBytes: Long?,
    @ColumnInfo(name = "response_encoding") val responseEncoding: String?,
    @ColumnInfo(name = "failure_payload") val failurePayload: String?,
)

@Entity(tableName = "acquisition_artifact", foreignKeys = [ForeignKey(
    entity = AcquisitionJobEntity::class, parentColumns = ["job_id"],
    childColumns = ["job_id"], onDelete = ForeignKey.CASCADE,
)], indices = [Index(value = ["artifact_key"], name = "index_acquisition_artifact_key", unique = true)])
internal data class AcquisitionArtifactEntity(
    @PrimaryKey @ColumnInfo(name = "job_id") val jobId: String,
    @ColumnInfo(name = "artifact_key") val artifactKey: String,
    @ColumnInfo(name = "byte_count") val byteCount: Long,
    @ColumnInfo(name = "sha256") val sha256: String,
    @ColumnInfo(name = "framing") val framing: String,
    @ColumnInfo(name = "compression") val compression: String,
    @ColumnInfo(name = "verified_payload") val verifiedPayload: String,
    @ColumnInfo(name = "payload_version") val payloadVersion: Int,
    @ColumnInfo(name = "completed_seconds") val completedSeconds: Long,
    @ColumnInfo(name = "completed_nano") val completedNano: Int,
)

@Entity(tableName = "acquisition_seen_id", primaryKeys = ["job_id", "raw_id"],
    foreignKeys = [ForeignKey(entity = AcquisitionJobEntity::class,
        parentColumns = ["job_id"], childColumns = ["job_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["job_id", "page_number"], name = "index_acquisition_seen_id_page")])
internal data class AcquisitionSeenIdEntity(
    @ColumnInfo(name = "job_id") val jobId: String,
    @ColumnInfo(name = "raw_id") val rawId: String,
    @ColumnInfo(name = "page_number") val pageNumber: Long,
)
