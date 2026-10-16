package com.architectpsg.commandlibrary.core.model

/** Source-reported card eligibility, separate from a deck-construction rules assessment. */
public data class CatalogFormatLegality(
    public val oracleId: OracleId,
    public val formatId: FormatId,
    public val snapshot: SnapshotReference,
    public val status: Knowledge<LegalityStatus>,
    public val provenance: Provenance,
)

/**
 * Read-only installed English Scryfall legality consensus for one explicit format and Oracle.
 * Requires exact active generation, no revision. Validate canonical identity and READY search
 * projection. Every relevant English printing must report the same supported status; missing,
 * unknown or conflicting printing evidence yields Unknown, never inferred legal/not-legal.
 * A missing identity is NOT_FOUND, absent prepared projection SOURCE_UNAVAILABLE, generation
 * changes STALE_GENERATION. Never acquire, prepare or rewrite data as a side effect of this read.
 */
public interface CatalogFormatLegalityLookup {
    public suspend fun legality(oracleId: OracleId, formatId: FormatId, snapshot: SnapshotReference,
        cancel: CancelToken): Outcome<CatalogFormatLegality>
}

/** Explicit user selection; an absent deck format must never silently mean Commander. */
public enum class BasicDeckFormat { COMMANDER }
public data class BasicDeckValidationRequest(
    public val deck: DeckSnapshot,
    public val snapshot: SnapshotReference,
    public val format: BasicDeckFormat,
)
public data class BasicDeckValidationResult(
    public val deckId: DeckId,
    public val deckRevision: Revision,
    public val snapshot: SnapshotReference,
    public val format: BasicDeckFormat,
    /** Basic checks do not assert exhaustive tournament/exception/bracket compliance. */
    public val status: LegalityStatus,
    public val checks: SnapshotList<BasicDeckValidationCheck>,
    public val sourceLegalities: SnapshotList<CatalogFormatLegality>,
    public val rulesVersion: String,
)
public data class BasicDeckValidationCheck(
    public val id: String,
    public val status: LegalityStatus,
    public val entryIds: SnapshotList<EntityId>,
    public val message: String,
    public val ruleReferences: SnapshotList<String>,
)

/** Captured, cancellable basic checks only; no deck mutation, auto-format or fake full legality. */
public interface LocalBasicDeckValidation {
    public suspend fun analyze(request: BasicDeckValidationRequest, cancel: CancelToken): Outcome<BasicDeckValidationResult>
}
