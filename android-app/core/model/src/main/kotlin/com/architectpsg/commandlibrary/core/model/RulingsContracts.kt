package com.architectpsg.commandlibrary.core.model

/** Literal source day, proleptic Gregorian years0001–9999; never an instant or freshness claim. */
@JvmInline
public value class RulingPublicationDay(public val value: String) {
    init {
        val parts = requireNotNull(rulingDayPattern.matchEntire(value)) { "Invalid ruling publication day" }.groupValues
        val year = parts[1].toInt(); val month = parts[2].toInt(); val day = parts[3].toInt()
        require(year in 1..9999 && month in 1..12) { "Invalid ruling publication day" }
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val days = when (month) { 2 -> if (leap) 29 else 28; 4, 6, 9, 11 -> 30; else -> 31 }
        require(day in 1..days) { "Invalid ruling publication day" }
    }
}

/** The feed's publisher label, separately from retrieval provider; not an authenticity assertion. */
public enum class RulingPublisher(public val upstreamValue: String) { WOTC("wotc"), SCRYFALL("scryfall") }

/** Literal decoded fields without invented provider ruling identity, language or retrieval facts. */
public data class RulingContent(
    public val oracleId: OracleId,
    public val publisher: RulingPublisher,
    public val publishedAt: RulingPublicationDay,
    public val comment: String,
) {
    init {
        require(oracleId.source == rulingProvider && rulingOraclePattern.matches(oracleId.raw)) { "Invalid ruling Oracle identity" }
        require(comment.isNotBlank()) { "Ruling comment must not be blank" }
        var index = 0
        while (index < comment.length) {
            val char = comment[index]
            when {
                char.isHighSurrogate() -> {
                    require(index + 1 < comment.length && comment[index + 1].isLowSurrogate()) { "Invalid ruling Unicode" }
                    index += 2
                }
                char.isLowSurrogate() -> throw IllegalArgumentException("Invalid ruling Unicode")
                else -> index++
            }
        }
    }
}

/** Local immutable generation/row identity, not a publisher-supplied ruling ID. */
public data class RulingId(public val generation: GenerationId, public val ordinal: Long) {
    init { require(ordinal >= 1) { "Ruling row ordinal must be positive" } }
}

public data class StoredRuling(public val id: RulingId, public val content: RulingContent, public val provenance: Provenance) {
    init { require(provenance.provider == rulingProvider && provenance.generation == id.generation) { "Ruling provenance disagrees with local identity" } }
}

public data class RulingsGeneration(
    public val id: GenerationId,
    public val provenance: Provenance,
    public val recordCount: Long,
    public val status: GenerationStatus,
    public val languageScope: AcquisitionLanguageScope = AcquisitionLanguageScope.RULINGS_LANGUAGE_NOT_ASSERTED,
) {
    init {
        require(recordCount >= 0) { "Ruling record count must not be negative" }
        require(provenance.provider == rulingProvider && provenance.generation == id) { "Ruling generation provenance disagrees" }
        require(languageScope == AcquisitionLanguageScope.RULINGS_LANGUAGE_NOT_ASSERTED) { "Ruling language is not asserted by this source" }
    }
}

/** Null means an actual absent corpus. Read failures remain Outcome failures, not empty success. */
public data class RulingsStatus(public val active: RulingsGeneration?) {
    init { require(active == null || active.status == GenerationStatus.ACTIVE) { "Ruling status requires an active generation" } }
}

/** Local reads only. Implementations never initialize, acquire or install merely by reading. */
public interface CatalogRulings {
    /** No installed corpus is Success(RulingsStatus(null)); preserve real read errors. */
    public suspend fun status(cancel: CancelToken): Outcome<RulingsStatus>

    /**
     * Canonical Scryfall Oracle required, otherwise INVALID_INPUT. No corpus is NOT_INSTALLED;
     * an installed corpus with no matching rows returns a genuine empty page.
     * Order: publication day descending, then local ordinal ascending. Cursor binds generation,
     * requested Oracle and full order key: old generation is STALE_GENERATION, malformed/wrong
     * Oracle is INVALID_INPUT. Snapshot source=scryfall, actual generation, revision/language=null.
     */
    public suspend fun rulings(oracleId: OracleId, page: PageRequest, cancel: CancelToken): Outcome<Page<StoredRuling>>
}

private val rulingProvider = ProviderId("scryfall")
private val rulingOraclePattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
private val rulingDayPattern = Regex("([0-9]{4})-([0-9]{2})-([0-9]{2})")
