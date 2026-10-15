package com.architectpsg.commandlibrary.core.model

/** Named checkpoint provenance; stable persistence tags belong to the codec, not enum ordinals. */
public enum class DeckVersionKind { USER, RECOVERY }

/** Metadata only; listing does not certify unread historical payload integrity. */
public data class DeckVersionSummary(
    public val id: EntityId,
    public val deckId: DeckId,
    public val label: String,
    public val createdAt: UtcTimestamp,
    public val sourceRevision: Revision,
    public val predecessorVersionId: EntityId?,
    public val kind: DeckVersionKind,
    public val restoredTargetVersionId: EntityId?,
)

/** Current-to-historical comparison, not authority to write or a prediction of commit time/revision. */
public data class DeckRestorePreview(
    public val current: DeckSnapshot,
    public val target: DeckVersion,
    public val difference: DeckDifference,
)

/** The recovery checkpoint refers to the exact pre-restore content and remains independently usable. */
public data class DeckRestoreResult(
    public val snapshot: DeckSnapshot,
    public val recoveryVersion: DeckVersion,
)

/**
 * Complete local named-checkpoint capability, independent of primitive edit and package mutation.
 * Labels are exact nonblank strict UTF8 <=1024 bytes; duplicate labels are allowed.
 * Capture records a checkpoint/receipt without changing the content revision or undo pointer.
 * Restore appends one new revision/group plus an enumerable exact pre-restore recovery checkpoint.
 * Both USER and RECOVERY checkpoints may be restored, preserving the entire package graph.
 * Reconcile the exact operation identity/input before mutable-head/CAS/clock/ID-source checks.
 * Replay returns its historical result and original generated IDs, never a new checkpoint.
 * Cancellation propagates; a lost acknowledgement does not establish rollback.
 * Malformed input is INVALID_INPUT, changed expected revision STALE_REVISION, contradictory
 * retained data CORRUPT_DATA, missing deck/version NOT_FOUND, newer metadata/wire UNSUPPORTED.
 */
public interface LocalDeckVersions {
    public suspend fun capture(id: DeckId, expected: Revision, label: String, operationId: EntityId, cancel: CancelToken): Outcome<DeckVersion>
    /** Newest checkpoint first; any successful deck operation invalidates the global-epoch cursor.
     * An existing deck with no checkpoints has an empty page; an absent deck is NOT_FOUND. */
    public suspend fun list(id: DeckId, page: PageRequest, cancel: CancelToken): Outcome<Page<DeckVersionSummary>>
    public suspend fun load(id: DeckId, versionId: EntityId, cancel: CancelToken): Outcome<DeckVersion>
    /** Reads consistent actual current/target snapshots; restore must independently revalidate. */
    public suspend fun previewRestore(id: DeckId, versionId: EntityId, expected: Revision, cancel: CancelToken): Outcome<DeckRestorePreview>
    public suspend fun restore(id: DeckId, versionId: EntityId, expected: Revision, recoveryLabel: String, operationId: EntityId, cancel: CancelToken): Outcome<DeckRestoreResult>
}
