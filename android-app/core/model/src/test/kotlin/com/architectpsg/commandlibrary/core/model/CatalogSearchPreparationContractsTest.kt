package com.architectpsg.commandlibrary.core.model

import org.junit.Assert.*
import org.junit.Test

class CatalogSearchPreparationContractsTest {
    private val absent = CatalogSearchPreparationState(GenerationId("installed"), 1,
        CatalogSearchPreparationReadiness.NOT_PREPARED, null, CatalogSearchPreparationCounts(), null)
    private val failure = DomainFailure(FailureCode.CORRUPT_DATA, "Validation failed", SnapshotList.empty())

    @Test fun absentIsExplicitAndCannotClaimCompletedWorkOrPhase() {
        assertThrows(IllegalArgumentException::class.java) { absent.copy(phase = CatalogSearchPreparationPhase.CARDS) }
        assertThrows(IllegalArgumentException::class.java) { absent.copy(processed = CatalogSearchPreparationCounts(cards = 1)) }
        assertThrows(IllegalArgumentException::class.java) { absent.copy(readiness = CatalogSearchPreparationReadiness.PREPARING) }
    }
    @Test fun onlyFailedHasFailureAndReadyRequiresFinalVerificationPhase() {
        assertThrows(IllegalArgumentException::class.java) { absent.copy(failure = failure) }
        val working = absent.copy(readiness = CatalogSearchPreparationReadiness.PREPARING, phase = CatalogSearchPreparationPhase.CARDS)
        assertThrows(IllegalArgumentException::class.java) { working.copy(failure = failure) }
        assertThrows(IllegalArgumentException::class.java) { working.copy(readiness = CatalogSearchPreparationReadiness.FAILED) }
        assertEquals(failure, working.copy(readiness = CatalogSearchPreparationReadiness.FAILED, failure = failure).failure)
        assertThrows(IllegalArgumentException::class.java) { working.copy(readiness = CatalogSearchPreparationReadiness.READY) }
        assertEquals(CatalogSearchPreparationReadiness.READY, working.copy(readiness = CatalogSearchPreparationReadiness.READY, phase = CatalogSearchPreparationPhase.VERIFY_TEXT_MIRRORS).readiness)
    }
    @Test fun countsAndVersionNeverWrapOrPretendUnknownAsNegative() {
        for (counts in listOf(
            { CatalogSearchPreparationCounts(cards = -1) }, { CatalogSearchPreparationCounts(faces = -1) },
            { CatalogSearchPreparationCounts(printings = -1) }, { CatalogSearchPreparationCounts(textRows = -1) },
            { CatalogSearchPreparationCounts(legalityRows = -1) })) assertThrows(IllegalArgumentException::class.java) { counts() }
        assertEquals(Long.MAX_VALUE, CatalogSearchPreparationCounts(cards = Long.MAX_VALUE).cards)
        assertThrows(IllegalArgumentException::class.java) { absent.copy(projectionVersion = 0) }
    }
    @Test fun identityBoundIsUtf8AndRejectsNulBeforeStorageUse() {
        for (identity in listOf("x".repeat(129), "é".repeat(65), "bad\u0000id"))
            assertThrows(IllegalArgumentException::class.java) { absent.copy(generation = GenerationId(identity)) }
        assertEquals(128, absent.copy(generation = GenerationId("é".repeat(64))).generation.value.toByteArray(Charsets.UTF_8).size)
    }
}
