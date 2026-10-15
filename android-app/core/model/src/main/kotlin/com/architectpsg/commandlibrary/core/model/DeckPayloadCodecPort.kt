package com.architectpsg.commandlibrary.core.model

/** Independent complete snapshot codec; decoding alone grants no mutation/source authority. */
public interface DeckPayloadCodecPort {
    public fun encodeSnapshot(value: DeckSnapshot): Outcome<SnapshotBytes>
    public fun decodeSnapshot(payload: SnapshotBytes): Outcome<DeckSnapshot>
}

/** Finite wire budgets, never destructive truncation or a claim of identical heap allocation. */
public object DeckPayloadLimits {
    public const val MAX_ENCODED_BYTES: Int = 8 * 1024 * 1024
    public const val MAX_ENTRIES: Int = 10_000
    public const val MAX_CATEGORIES: Int = 1_024
    public const val MAX_LIST_ITEMS: Int = 10_000
    public const val MAX_COLLECTION_ELEMENTS: Int = 100_000
    public const val MAX_TEXT_BYTES: Int = 1024 * 1024
    public const val MAX_ID_BYTES: Int = 1_024
    public const val MAX_DECIMAL_PRECISION: Int = 4_096
}
