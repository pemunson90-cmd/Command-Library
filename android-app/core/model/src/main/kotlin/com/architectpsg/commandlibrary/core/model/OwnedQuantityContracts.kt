package com.architectpsg.commandlibrary.core.model

/** Pinning is explicit; selecting a printing does not implicitly pin an entry. */
public data class OwnedQuantityPolicy(
    public val includeBoards: SnapshotList<BoardRole>,
    public val pins: SnapshotList<PrintingPin> = SnapshotList.empty(),
    public val ownership: OwnershipPolicy = OwnershipPolicy.SAME_ORACLE,
)

/** Remaining is uncovered by proven allocations; it is exact missing quantity only when complete. */
public data class OwnedQuantityDemand(
    public val entryId: EntityId,
    public val requestedQuantity: Int,
    public val ownedQuantity: Int,
    public val remainingQuantity: Int,
    public val complete: Boolean,
) {
    init {
        require(requestedQuantity >= 0 && ownedQuantity in 0..requestedQuantity)
        require(remainingQuantity == requestedQuantity - ownedQuantity)
        require(remainingQuantity > 0 || complete)
    }
}

/** Pure what-if allocation; it makes no global reservation, price or inventory-integrity claim. */
public data class OwnedQuantityPlan(
    public val deckId: DeckId,
    public val deckRevision: Revision,
    public val inventoryRevision: Revision,
    public val policy: OwnedQuantityPolicy,
    public val allocations: SnapshotList<OwnedAllocation>,
    public val demands: SnapshotList<OwnedQuantityDemand>,
    public val incompleteEntryIds: SnapshotList<EntityId>,
    public val complete: Boolean,
)

public object OwnedQuantityLimits {
    public const val MAX_ENTRIES: Int = 10_000
    public const val MAX_LOTS: Int = 10_000
    public const val MAX_ID_BYTES: Int = 1_024
    public const val MAX_COMPATIBILITY_EDGES: Int = 100_000
    public const val MAX_ALLOCATIONS: Int = 100_000
    public const val MAX_CANDIDATE_COMPARISONS: Long = 1_000_000
    public const val MAX_RESIDUAL_EDGE_VISITS: Long = 10_000_000
    public const val CANCEL_CHECK_INTERVAL: Int = 1_024
}

/**
 * Complete bounded quantity capability, without quotes or storage. Maximizes proven-compatible
 * owned quantity with deterministic traversal, not a secondary cost or fairness optimum.
 * Returned policy/output is canonical. Unknown compatibility is never allocated as proven.
 * IGNORE bypasses inventory-lot validation/traversal; its revision is traceability only.
 * Exceeding a budget fails INVALID_INPUT without a partial plan; cancellation propagates.
 */
public interface OwnedQuantityAllocator {
    public fun allocate(deck: DeckSnapshot, inventory: InventorySnapshot, policy: OwnedQuantityPolicy, cancel: CancelToken): Outcome<OwnedQuantityPlan>
}
