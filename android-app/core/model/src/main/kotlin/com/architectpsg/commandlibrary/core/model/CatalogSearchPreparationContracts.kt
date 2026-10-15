package com.architectpsg.commandlibrary.core.model

public enum class CatalogSearchPreparationReadiness { NOT_PREPARED, PREPARING, READY, FAILED }
public enum class CatalogSearchPreparationPhase {
    CARDS, PRINTINGS, VERIFY_CARDS, VERIFY_FACES, VERIFY_PRINTINGS,
    VERIFY_TEXT, VERIFY_LEGALITIES, VERIFY_TEXT_MIRRORS,
}

/** Complete committed materialized facts, not upstream totals, owned quantities or percentages. */
public data class CatalogSearchPreparationCounts(
    public val cards: Long = 0,
    public val faces: Long = 0,
    public val printings: Long = 0,
    public val textRows: Long = 0,
    public val legalityRows: Long = 0,
) {
    init { require(listOf(cards, faces, printings, textRows, legalityRows).all { it >= 0 }) }
}

public data class CatalogSearchPreparationState(
    public val generation: GenerationId,
    public val projectionVersion: Int,
    public val readiness: CatalogSearchPreparationReadiness,
    public val phase: CatalogSearchPreparationPhase?,
    public val processed: CatalogSearchPreparationCounts,
    public val failure: DomainFailure?,
) {
    init {
        require(generation.value.length <= 128 && '\u0000' !in generation.value && generation.value.toByteArray(Charsets.UTF_8).size <= 128)
        require(projectionVersion > 0)
        require((readiness == CatalogSearchPreparationReadiness.NOT_PREPARED) == (phase == null))
        require((readiness == CatalogSearchPreparationReadiness.FAILED) == (failure != null))
        require(readiness != CatalogSearchPreparationReadiness.NOT_PREPARED || processed == CatalogSearchPreparationCounts())
        require(readiness != CatalogSearchPreparationReadiness.READY || phase == CatalogSearchPreparationPhase.VERIFY_TEXT_MIRRORS)
    }
}

/** Explicit installed English Scryfall generation only; missing generations never rebind to active.
 * No network, startup backfill, reset or source deletion. NOT_PREPARED is not an empty ready index.
 * Invalid generation input fails INVALID_INPUT before database access. status is read-only;
 * advance performs one bounded atomic unit, or reads terminal READY/FAILED without new writes.
 * A known committed result may survive postcommit cancellation after exact acknowledgement;
 * an unknown acknowledgement is not replayed within that operation.
 */
public interface CatalogSearchPreparation {
    public suspend fun status(generation: GenerationId, cancel: CancelToken): Outcome<CatalogSearchPreparationState>
    public suspend fun advance(generation: GenerationId, cancel: CancelToken): Outcome<CatalogSearchPreparationState>
}
