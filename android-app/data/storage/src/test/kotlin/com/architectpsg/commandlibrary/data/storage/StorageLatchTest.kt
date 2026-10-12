package com.architectpsg.commandlibrary.data.storage

import com.architectpsg.commandlibrary.core.common.RollbackOnlyLatch
import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class StorageLatchTest {
    @Test fun ignoredFailureAndCaughtAbortCannotRestoreCommitPermission() {
        val latch = RollbackOnlyLatch()
        val first = Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "unsupported statement")
        latch.observe(first)
        assertThrows(TransactionAbort::class.java) {
            latch.abort(DomainFailure(FailureCode.INVALID_INPUT, "validation", SnapshotList.empty()))
        }
        latch.observe(Outcome.Success(Unit))
        assertSame(first, latch.commitPermission())
        assertEquals(DbTransactionState.RollbackOnly(first), latch.state)
    }

    @Test fun callbackStatusesPreserveTheirExactTypedStatus() {
        listOf<Outcome<Nothing>>(
            Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, "validation", SnapshotList.empty())),
            Outcome.Unavailable(UnavailableReason.NOT_FOUND, "missing"),
            Outcome.Unknown("unknown", SnapshotList.empty()),
        ).forEach { status ->
            val latch = RollbackOnlyLatch()
            latch.observe(status)
            assertSame(status, latch.commitPermission())
        }
    }
}
