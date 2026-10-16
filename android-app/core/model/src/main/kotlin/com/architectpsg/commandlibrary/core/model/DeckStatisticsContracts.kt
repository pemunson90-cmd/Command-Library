package com.architectpsg.commandlibrary.core.model

import java.math.BigDecimal

/** Explicit local analysis budgets; reject over-limit input before unbounded aggregation. */
public object DeckStatisticsLimits {
    public const val MAX_ENTRIES: Int = 10_000
    public const val MAX_RAW_RECORD_BYTES: Int = 8 * 1024 * 1024
    public const val MAX_AGGREGATE_FACT_BYTES: Int = 32 * 1024 * 1024
    public const val MAX_COST_BYTES: Int = 16 * 1024
    public const val MAX_FACES: Int = 16
    public const val MAX_RELATED_PARTS: Int = 128
    public const val MAX_TYPE_TOKENS: Int = 64
    public const val MAX_TYPE_BYTES: Int = 256
    public const val MAX_MANA_PRECISION: Int = 64
    public const val MAX_MANA_SCALE: Int = 32
}

/** Supplied relationship, not a claim about creation direction, number of tokens or trigger. */
public data class ReportedCardPart(
    public val printingId: CardId,
    public val component: String,
    public val name: Knowledge<String>,
    public val typeLine: Knowledge<String>,
)

/** Additional source-reported facts never rewrite CardRecord or its persisted canonical digest. */
public data class CatalogDeckFacts(
    public val card: CardRecord,
    public val snapshot: SnapshotReference,
    /** Exact selected printing, or null when using the first retained Oracle source object. */
    public val printingId: CardId?,
    /** W/U/B/R/G/C possibilities reported by the source, not unconditional usable mana. */
    public val reportedProduction: Knowledge<SnapshotList<String>>,
    public val reportedParts: Knowledge<SnapshotList<ReportedCardPart>>,
)

/**
 * Local installed English facts only; no acquisition, inference or data rewrite. Snapshot requires
 * an exact generation and source. Selected printing must belong to the requested Oracle/source.
 * Read and validate current canonical/printing projections before extracting optional raw fields.
 * Missing/null optional fields stay Unknown; reported empty arrays stay Known(empty). Unknown
 * future symbols/components are UNSUPPORTED, malformed/contradictory retained facts CORRUPT_DATA.
 * Do not merge printing-specific extras from different objects or treat OTHER as a token.
 * Require generation consistency across reads; changed active generation is STALE_GENERATION.
 * Returned snapshot identifies the read; it does not promise that generation remains active later.
 */
public interface CatalogDeckFactsLookup {
    public suspend fun facts(oracleId: OracleId, printingId: CardId?, snapshot: SnapshotReference, cancel: CancelToken): Outcome<CatalogDeckFacts>
}

public data class DeckStatisticsFilter(
    public val boards: SnapshotList<BoardRole>,
    /** Null selects every entry on selected boards; empty selects none. */
    public val entryIds: SnapshotList<EntityId>?,
    public val tagIds: SnapshotList<EntityId>,
    public val requireAllTags: Boolean,
)
public data class DeckStatisticsRequest(public val deck: DeckSnapshot, public val snapshot: SnapshotReference, public val filter: DeckStatisticsFilter)
public data class StatisticCount(public val name: String, public val copies: Long)
public data class StatisticManaBucket(public val manaValue: BigDecimal, public val copies: Long)
public data class StatisticTagCoverage(public val label: EntryLabel, public val entries: Int, public val copies: Long)
public data class EntryDeckFacts(public val entryId: EntityId, public val facts: CatalogDeckFacts?)

/** Weighted means expose their actual known-value denominator, never an assumed full-deck mean. */
public data class StatisticAverage(public val value: Knowledge<BigDecimal>, public val copies: Long)
public data class DeckStatisticsResult(
    public val deckId: DeckId,
    public val deckRevision: Revision,
    public val snapshot: SnapshotReference,
    public val filter: DeckStatisticsFilter,
    public val entryIds: SnapshotList<EntityId>,
    public val copies: Long,
    public val unresolvedCopies: Long,
    public val manaCurve: SnapshotList<StatisticManaBucket>,
    public val unknownManaValueCopies: Long,
    public val averageManaValue: StatisticAverage,
    public val averageNonlandManaValue: StatisticAverage,
    public val types: SnapshotList<StatisticCount>,
    public val unknownTypeCopies: Long,
    /** Exact printed cost symbols, including hybrid/Phyrexian/variable symbols as alternatives. */
    public val costSymbols: SnapshotList<StatisticCount>,
    public val unknownCostCopies: Long,
    /** Counts lands reporting a possible production symbol once each, not guaranteed mana output. */
    public val reportedLandProduction: SnapshotList<StatisticCount>,
    public val unknownLandProductionCopies: Long,
    /** Only supplied component=token references; no inferred creation amount/direction. */
    public val tokenReferences: SnapshotList<ReportedCardPart>,
    public val unknownTokenReferenceCopies: Long,
    /** Coverage overlaps intentionally; headline physical copies are never summed from tag totals. */
    public val tags: SnapshotList<StatisticTagCoverage>,
    public val issues: SnapshotList<DomainIssue>,
)

/**
 * Captured, read-only deck statistics. Board/entry/tag AND/OR filters count each entry once.
 * Top-level source mana value counts once per quantity; no summing face values or invented face MV.
 * Cost symbols use top-level cost where supplied. For multiface layouts without a single cost,
 * retain the alternatives separately in issues rather than adding faces into simultaneous demand.
 * Types overlap by printed type; average nonland uses only known nonland type+MV values. Empty
 * denominators yield Unknown, known zero stays zero. Means use DECIMAL128 and disclose denominator.
 * Source-reported production is explicitly conditionality-unclassified; no colors/identity→sources
 * inference, fetch-target assumption, unconditional-source guarantee or land-count prescription.
 * Missing Oracle facts contribute to unknown counters; no name-based identity guessing. Malformed
 * inputs/duplicate selections/unsupported budgets fail before IO; contradictory returned identities
 * or generations fail rather than mix facts. All arithmetic checked, no silent overflow/truncation.
 * Cancellation propagates. No prices, provider, recommendations, deck writes or hypothetical Apply.
 */
public interface LocalDeckStatistics {
    public suspend fun analyze(request: DeckStatisticsRequest, cancel: CancelToken): Outcome<DeckStatisticsResult>
}
