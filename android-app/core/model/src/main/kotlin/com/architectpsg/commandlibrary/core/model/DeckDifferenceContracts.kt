package com.architectpsg.commandlibrary.core.model

public enum class DeckChangedField {
    NAME, FORMAT, DESCRIPTION, PRIMER, FOLDER, TAGS, CREATED_AT, MODIFIED_AT, REVISION,
    BUDGET, PROFILE, CATALOG_REFERENCES, CATEGORIES, PACKAGES, ENTRY_ORDER,
}

/** Report only. Restore must revalidate actual current revision and content inside its transaction. */
public data class DeckDifference(
    public val before: DeckSnapshot,
    public val after: DeckSnapshot,
    public val changedFields: SnapshotList<DeckChangedField>,
    public val entries: SnapshotList<CardChange>,
)
