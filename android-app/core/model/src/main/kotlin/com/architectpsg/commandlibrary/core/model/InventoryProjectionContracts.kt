package com.architectpsg.commandlibrary.core.model

/** Complete exact before/after content for one row; null after means removal, not quantity zero. */
public data class InventoryLotChange(public val lotId: EntityId, public val before: OwnedLot?, public val after: OwnedLot?)
public data class InventoryMerge(public val requestedLotId: EntityId, public val survivorLotId: EntityId, public val quantityAdded: Int)

/** Pure proposal only: no write authority, catalog finish proof, clock reading or durable undo claim. */
public data class InventoryProjection(
    public val sourceRevision: Revision,
    public val resultRevision: Revision,
    public val changes: SnapshotList<InventoryLotChange>,
    public val merges: SnapshotList<InventoryMerge>,
    public val issues: SnapshotList<DomainIssue>,
)

/** Static ERROR issue codes, attributed to requested lot IDs; no user text in messages/evidence. */
public object InventoryIssueCodes {
    public const val DUPLICATE_SELECTION: String = "DUPLICATE_SELECTION"
    public const val LOT_ALREADY_EXISTS: String = "LOT_ALREADY_EXISTS"
    public const val LOT_NOT_FOUND: String = "LOT_NOT_FOUND"
    public const val STALE_LOT: String = "STALE_LOT"
    public const val INVALID_ADD_REVISION: String = "INVALID_ADD_REVISION"
    public const val QUANTITY_RANGE: String = "QUANTITY_RANGE"
    public const val MERGE_TARGET_SELECTED: String = "MERGE_TARGET_SELECTED"
    public const val QUANTITY_OVERFLOW: String = "QUANTITY_OVERFLOW"
}

/** Resource rejection limits, not measured performance or heap guarantees. */
public object InventoryProjectionLimits {
    public const val MAX_LOTS: Int = 100_000
    public const val MAX_EDITS: Int = 1_024
    public const val MAX_ID_BYTES: Int = 1_024
    public const val MAX_TEXT_BYTES: Int = 1_024 * 1_024
    public const val MAX_TOTAL_TEXT_BYTES: Long = 64L * 1_024 * 1_024
    public const val MAX_COLLECTION_ELEMENTS: Int = 200_000
    public const val MAX_CANDIDATE_COMPARISONS: Long = 1_000_000L
    public const val MAX_WORK_UNITS: Long = 200_000_000L
    public const val MAX_ISSUES: Int = 8_192
    public const val CANCEL_CHECK_INTERVAL: Int = 1_024
}
