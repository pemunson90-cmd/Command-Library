package com.architectpsg.commandlibrary.core.common

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class RollbackOnlyLatchTest {
    private val failure = DomainFailure(FailureCode.INVALID_INPUT, "Synthetic rejection", SnapshotList.empty())

    @Test fun ignoredFailureAndLaterSuccessCannotClearRollbackLatch() {
        val guard = RollbackOnlyLatch()
        guard.observe(Outcome.Failure(failure))
        guard.observe(Outcome.Success("Callback returned normally"))
        assertEquals(Outcome.Failure(failure), guard.commitPermission())
        assertTrue(guard.state is DbTransactionState.RollbackOnly)
    }

    @Test fun caughtTypedAbortStillPreventsCommit() {
        val guard = RollbackOnlyLatch()
        try {
            guard.abort(failure)
        } catch (abort: TransactionAbort) {
            assertSame(failure, abort.failure)
        }
        guard.observe(Outcome.Success(Unit))
        assertEquals(Outcome.Failure(failure), guard.commitPermission())
    }

    @Test fun firstUnavailableOrUnknownStatusPropagatesWithoutSuccessWrapping() {
        val unavailable = Outcome.Unavailable(UnavailableReason.NOT_INSTALLED, "Synthetic missing data")
        val unknown = Outcome.Unknown("Synthetic unknown", SnapshotList.empty())
        val first = RollbackOnlyLatch()
        first.observe(unavailable)
        first.observe(Outcome.Failure(failure))
        assertSame(unavailable, first.commitPermission())
        val second = RollbackOnlyLatch()
        second.observe(unknown)
        assertSame(unknown, second.commitPermission())
        val active = RollbackOnlyLatch()
        active.observe(Outcome.Success(1))
        assertEquals(Outcome.Success(Unit), active.commitPermission())
    }
}
