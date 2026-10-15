package com.architectpsg.commandlibrary.core.model

/** A committed historical delta, never a caller-authorized preview or reconstructed old snapshot. */
public data class InventoryCommit(public val projection: InventoryProjection, public val modifiedAt: UtcTimestamp)

/** Row rejection is explicit and is not a committed/replayable operation. */
public sealed interface InventoryApplyResult {
    public data class Committed(public val commit: InventoryCommit) : InventoryApplyResult
    public data class Rejected(public val issues: SnapshotList<DomainIssue>, public val actualRevision: Revision) : InventoryApplyResult
}

/**
 * Complete local inventory, authoritative atomic patches and persistent once-only grouped undo.
 * Snapshot is complete and consistent; capturedAt is the stored last modification time.
 * Apply recomputes from stored content; pure projections confer no write authority.
 * Empty apply records a receipt without a clock reading/revision/group; nonempty commits form
 * one group. Undo appends a revision and consumes that group, never toggles it.
 * Every committed Add ID remains reserved, including absorbed merge IDs and removed rows.
 * Reconcile exact operation ID/input before mutable-state/CAS/clock checks. Replays return
 * historical deltas. Conflicting reuse is INVALID_INPUT. Rejected rows write no receipt.
 * Cancellation propagates; cancellation after durable commit cannot establish rollback.
 * Structural persistence does not establish catalog-proven finish eligibility.
 */
public interface LocalInventory {
    public suspend fun snapshot(cancel: CancelToken): Outcome<InventorySnapshot>
    public suspend fun apply(patch: CollectionPatch, expected: Revision, operationId: EntityId, cancel: CancelToken): Outcome<InventoryApplyResult>
    public suspend fun undo(expected: Revision, operationId: EntityId, cancel: CancelToken): Outcome<InventoryCommit>
}

/** Complete bounded serialization of all four CollectionEdit variants; no persistence authority. */
public interface LocalInventoryPatchCodecPort {
    public fun encodePatch(value: CollectionPatch): Outcome<SnapshotBytes>
    public fun decodePatch(payload: SnapshotBytes): Outcome<CollectionPatch>
}

/** Rejection thresholds, not measured heap/performance guarantees. */
public object LocalInventoryLimits {
    public const val MAX_ACTIVE_ENCODED_BYTES: Long = 64L * 1_024 * 1_024
    public const val MAX_OPERATION_ENCODED_BYTES: Long = 16L * 1_024 * 1_024
    public const val MAX_DESCRIPTOR_BYTES: Int = 4 * 1_024 * 1_024
}
