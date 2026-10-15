package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*

/** Complete local adapter over the shared durable owner, with no acquisition/recovery dependency. */
internal class LocalCatalogSearchPreparation(database: Database, clock: Clock) : CatalogSearchPreparation {
    private val store = CatalogSearchPreparationStore(database, clock)

    override suspend fun status(generation: GenerationId, cancel: CancelToken): Outcome<CatalogSearchPreparationState> {
        invalidGeneration(generation)?.let { return it }
        return when (val result = store.status(generation, cancel)) {
            is Outcome.Success -> Outcome.Success(result.value?.publicState() ?: CatalogSearchPreparationState(generation, 1,
                CatalogSearchPreparationReadiness.NOT_PREPARED, null, CatalogSearchPreparationCounts(), null))
            is Outcome.Failure -> result; is Outcome.Unavailable -> result; is Outcome.Unknown -> result
        }
    }

    override suspend fun advance(generation: GenerationId, cancel: CancelToken): Outcome<CatalogSearchPreparationState> {
        invalidGeneration(generation)?.let { return it }
        return when (val result = store.advance(generation, cancel)) {
            is Outcome.Success -> Outcome.Success(result.value.publicState())
            is Outcome.Failure -> result; is Outcome.Unavailable -> result; is Outcome.Unknown -> result
        }
    }

    private fun invalidGeneration(generation: GenerationId): Outcome.Failure? = if (generation.value.length > 128 || '\u0000' in generation.value || generation.value.toByteArray(Charsets.UTF_8).size > 128)
        Outcome.Failure(bulkError(FailureCode.INVALID_INPUT, "Catalog generation identity exceeds local search bounds")) else null

    private fun SearchPreparationCheckpoint.publicState(): CatalogSearchPreparationState = CatalogSearchPreparationState(
        generation, 1, when (status) {
            SearchPreparationStatus.PREPARING -> CatalogSearchPreparationReadiness.PREPARING
            SearchPreparationStatus.READY -> CatalogSearchPreparationReadiness.READY
            SearchPreparationStatus.FAILED -> CatalogSearchPreparationReadiness.FAILED
        }, when (phase) {
            SearchPreparationPhase.CARD -> CatalogSearchPreparationPhase.CARDS
            SearchPreparationPhase.PRINTING -> CatalogSearchPreparationPhase.PRINTINGS
            SearchPreparationPhase.VERIFY, SearchPreparationPhase.VERIFY_CARD -> CatalogSearchPreparationPhase.VERIFY_CARDS
            SearchPreparationPhase.VERIFY_FACE -> CatalogSearchPreparationPhase.VERIFY_FACES
            SearchPreparationPhase.VERIFY_PRINTING -> CatalogSearchPreparationPhase.VERIFY_PRINTINGS
            SearchPreparationPhase.VERIFY_TEXT -> CatalogSearchPreparationPhase.VERIFY_TEXT
            SearchPreparationPhase.VERIFY_LEGALITY -> CatalogSearchPreparationPhase.VERIFY_LEGALITIES
            SearchPreparationPhase.VERIFY_FTS -> CatalogSearchPreparationPhase.VERIFY_TEXT_MIRRORS
        }, CatalogSearchPreparationCounts(cards, faces, printings, texts, legalities),
        if (status == SearchPreparationStatus.FAILED) bulkError(FailureCode.CORRUPT_DATA, "Stored catalog search preparation failed validation") else null)
}
