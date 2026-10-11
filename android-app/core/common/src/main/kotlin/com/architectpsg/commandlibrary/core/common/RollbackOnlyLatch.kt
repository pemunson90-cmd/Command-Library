package com.architectpsg.commandlibrary.core.common

import com.architectpsg.commandlibrary.core.model.DbTransactionState
import com.architectpsg.commandlibrary.core.model.DomainFailure
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.TransactionAbort

/** Pure transaction guard; the database adapter still owns actual atomic commit/rollback. */
public class RollbackOnlyLatch {
    private var current: DbTransactionState = DbTransactionState.Active
    public val state: DbTransactionState get() = current

    /** Observe query/execute results and a callback's returned Outcome before a commit attempt. */
    public fun <T> observe(outcome: Outcome<T>): Outcome<T> {
        when (outcome) {
            is Outcome.Success -> Unit
            is Outcome.Failure -> latch(outcome)
            is Outcome.Unavailable -> latch(outcome)
            is Outcome.Unknown -> latch(outcome)
        }
        return outcome
    }

    public fun abort(error: DomainFailure): Nothing {
        latch(Outcome.Failure(error))
        throw TransactionAbort(error)
    }

    /** No operation on this guard can return rollback-only state to Active. */
    public fun commitPermission(): Outcome<Unit> = when (val observed = current) {
        is DbTransactionState.Active -> Outcome.Success(Unit)
        is DbTransactionState.RollbackOnly -> observed.status
    }

    private fun latch(status: Outcome<Nothing>): Unit {
        if (current is DbTransactionState.Active) current = DbTransactionState.RollbackOnly(status)
    }
}
