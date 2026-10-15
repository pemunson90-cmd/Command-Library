package com.architectpsg.commandlibrary.core.model

/** Application-owned text projection, not a vendor format or lossless native backup. */
public interface PlainDeckListCodec {
    /** Fully consumes the bounded stream, retaining unresolved names and physical row errors. */
    public suspend fun parse(input: TransferInput, cancel: CancelToken): Outcome<PlainDeckList>
    /** Fails unrepresentable names; every omitted field is disclosed in deterministic losses. */
    public fun render(deck: DeckSnapshot, cancel: CancelToken): Outcome<PlainDeckListExport>
}

public data class PlainDeckListEntry(
    public val quantity: Int,
    public val name: String,
    public val board: BoardRole,
)
public data class PlainDeckListRow(
    public val location: RowLocation,
    public val rawText: String,
    public val entry: PlainDeckListEntry?,
    public val issues: SnapshotList<ImportIssue>,
)
public data class PlainDeckList(
    public val inputId: EntityId,
    public val rows: SnapshotList<PlainDeckListRow>,
    /** Entire input consumed without row syntax errors; never identity/import readiness. */
    public val complete: Boolean,
)

/** Declaration order is the documented stable reporting order, not a binary wire ordinal. */
public enum class PlainDeckListLossField {
    DECK_ID, DECK_NAME, FORMAT, DESCRIPTION, FOLDER, TAGS, CREATED_AT, MODIFIED_AT,
    REVISION, BUDGET, PROFILE_ID, CATALOG_REFERENCES, PACKAGE_INSTANCES, PRIMER,
    CATEGORY_ORDER, ENTRY_ORDER,
    ENTRY_ID, ORACLE_ID, PRINTING_ID, RAW_IDENTITY, LANGUAGE, FINISH, NOTES, LABELS, ROLE_OVERRIDES,
}
public data class PlainDeckListLoss(
    /** Null for deck-wide losses; otherwise the original uniquely attributed entry. */
    public val entryId: EntityId?,
    public val field: PlainDeckListLossField,
)
public data class PlainDeckListExport(
    public val bytes: SnapshotBytes,
    public val losses: SnapshotList<PlainDeckListLoss>,
)

/** Admission ceilings, not measured performance claims. Grammar version and marker are exact. */
public object PlainDeckListLimits {
    public const val MARKER: String = "Command Library Deck List 1"
    public const val MAX_BYTES: Int = 8 * 1024 * 1024
    public const val MAX_ENTRIES: Int = 10_000
    public const val MAX_LINES: Int = 20_000
    public const val MAX_LINE_BYTES: Int = 64 * 1024
    public const val MAX_NAME_BYTES: Int = 16 * 1024
    public const val MAX_DISPLAY_NAME_BYTES: Int = 1024
    public const val MAX_IDENTIFIER_BYTES: Int = 1024
    public const val MAX_LOSSES: Int = 16 + 9 * MAX_ENTRIES
}
public object PlainDeckListIssueCodes {
    public const val INVALID_QUANTITY: String = "INVALID_QUANTITY"
    public const val INVALID_ROW: String = "INVALID_ROW"
    public const val INVALID_MARKER: String = "INVALID_MARKER"
}
