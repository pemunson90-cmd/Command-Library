package com.architectpsg.commandlibrary.core.model

/**
 * Atomic local primitive patches and persistent grouped undo, independent of named versions.
 * Each nonempty accepted patch is one group and one new revision. Empty patches only record
 * an operation receipt. Undo appends a new revision and consumes the latest logical group.
 * Package-bearing content and version/package edits remain explicitly unsupported.
 * Reconcile the same operation ID and exact input before mutable-state/CAS/clock checks.
 * Replays return the historical result; conflicting operation reuse is INVALID_INPUT.
 * Cancellation propagates and cannot establish rollback after a durable commit.
 */
public interface LocalDeckEdits {
    public suspend fun apply(id: DeckId, patch: DeckPatch, expected: Revision, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot>
    public suspend fun undo(id: DeckId, expected: Revision, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot>
}

/** Complete bounded serialization of the primitive local edit capability, not package/version operations. */
public interface LocalDeckPatchCodecPort {
    public fun encodePatch(value: DeckPatch): Outcome<SnapshotBytes>
    public fun decodePatch(payload: SnapshotBytes): Outcome<DeckPatch>
}

public object LocalDeckEditLimits {
    public const val MAX_EDITS: Int = 1_024
}
