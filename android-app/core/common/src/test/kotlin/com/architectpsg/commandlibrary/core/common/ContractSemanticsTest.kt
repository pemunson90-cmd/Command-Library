package com.architectpsg.commandlibrary.core.common

import com.architectpsg.commandlibrary.core.model.*
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ContractSemanticsTest {
    @Test fun exactMultiplicationRetainsNativeUnitAndTixScale() {
        val policy = ExactDecimalPolicy()
        val usd = DecimalUnit.Fiat("USD")
        assertEquals(DecimalAmount.exact("0.30", usd), policy.multiply(DecimalAmount.exact("0.10", usd), 3))
        val tix = policy.multiply(DecimalAmount.exact("0.000125", DecimalUnit.Tix), 17)
        assertEquals(BigDecimal("0.002125"), tix.value)
        assertEquals(6, tix.scale)
        assertEquals(DecimalUnit.Tix, tix.unit)
        assertEquals(BigDecimal("0.000000"), policy.multiply(tix, 0).value)
    }

    @Test fun decimalDoesNotRoundLargeValuesOrAcceptNegativeQuantity() {
        val policy = ExactDecimalPolicy()
        val amount = DecimalAmount.exact("123456789012345678901234567890.123456789", DecimalUnit.Tix)
        assertEquals(BigDecimal("370370367037037036703703703670.370370367"), policy.multiply(amount, 3).value)
        assertThrows(IllegalArgumentException::class.java) { policy.multiply(amount, -1) }
    }

    @Test fun staleWriteCannotAdvanceAndMatchingCommitAdvancesExactlyOnce() {
        val current = Revision(12)
        val stale = revisionForCommittedEdit(Revision(11), current)
        assertTrue(stale is Outcome.Failure)
        assertEquals(FailureCode.STALE_REVISION, (stale as Outcome.Failure).error.code)
        assertEquals(Outcome.Success(Revision(13)), revisionForCommittedEdit(current, current))
        assertEquals(Revision(12), current)
        assertTrue(revisionForCommittedEdit(Revision(Long.MAX_VALUE), Revision(Long.MAX_VALUE)) is Outcome.Failure)
        assertThrows(IllegalArgumentException::class.java) { Revision(-1) }
    }

    @Test fun cancellationEscapesWithoutCallingFailureClassifier() = runBlocking {
        val cancellation = CancellationException("synthetic cancellation")
        var classified = false
        val token = CoroutineCancelToken(currentCoroutineContext())
        try {
            cancellableOutcome<Int>(token, { classified = true; DomainFailure(FailureCode.INTERNAL, "Synthetic", SnapshotList.empty()) }) { throw cancellation }
            fail("Cancellation must escape")
        } catch (caught: CancellationException) {
            assertSame(cancellation, caught)
        }
        assertFalse(classified)
    }

    @Test fun cancelledCallerTokenPreventsWorkAndCannotBecomeFailure() = runBlocking {
        val job = Job()
        job.cancel()
        val token = CoroutineCancelToken(job)
        var worked = false
        var classified = false
        assertTrue(token.isCancelled)
        try {
            cancellableOutcome(token, { classified = true; DomainFailure(FailureCode.INTERNAL, "Synthetic", SnapshotList.empty()) }) { worked = true; 1 }
            fail("Cancellation must escape")
        } catch (_: CancellationException) {
            assertFalse(worked)
            assertFalse(classified)
        }
    }

    @Test fun ordinaryFailureRemainsTypedAndSuccessRemainsSuccess() = runBlocking {
        val token = CoroutineCancelToken(currentCoroutineContext())
        val outcome = cancellableOutcome<Int>(token, { DomainFailure(FailureCode.PERSISTENCE, "Synthetic disk failure", SnapshotList.empty()) }) { throw IllegalStateException("synthetic") }
        assertEquals(FailureCode.PERSISTENCE, (outcome as Outcome.Failure).error.code)
        assertEquals(Outcome.Success(7), cancellableOutcome(token, { DomainFailure(FailureCode.INTERNAL, "Unexpected", SnapshotList.empty()) }) { 7 })
    }
}
