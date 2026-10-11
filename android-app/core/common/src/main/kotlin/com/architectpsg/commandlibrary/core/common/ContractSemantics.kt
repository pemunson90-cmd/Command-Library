package com.architectpsg.commandlibrary.core.common

import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.DecimalAmount
import com.architectpsg.commandlibrary.core.model.DecimalPolicy
import com.architectpsg.commandlibrary.core.model.DomainFailure
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.Revision
import com.architectpsg.commandlibrary.core.model.SnapshotList
import java.math.BigDecimal
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

public class ExactDecimalPolicy : DecimalPolicy {
    public override fun multiply(amount: DecimalAmount, quantity: Int): DecimalAmount {
        require(quantity >= 0) { "Quantity must be nonnegative" }
        return DecimalAmount(amount.value.multiply(BigDecimal.valueOf(quantity.toLong())), amount.unit)
    }
}

/** Adapter token observes its caller's structured coroutine context; it never owns a detached job. */
public class CoroutineCancelToken(private val context: CoroutineContext) : CancelToken {
    public override val isCancelled: Boolean get() = !context.isActive
    public override fun throwIfCancelled(): Unit = context.ensureActive()
}

public fun requireCurrentRevision(expected: Revision, actual: Revision): Outcome<Unit> =
    if (expected == actual) Outcome.Success(Unit) else Outcome.Failure(
        DomainFailure(FailureCode.STALE_REVISION, "Expected revision ${expected.value}, actual ${actual.value}", SnapshotList.empty()),
    )

/** Call within the actual mutation transaction; failed/stale/abandoned edits do not advance state. */
public fun revisionForCommittedEdit(expected: Revision, actual: Revision): Outcome<Revision> =
    when (val checked = requireCurrentRevision(expected, actual)) {
        is Outcome.Success -> if (actual.value == Long.MAX_VALUE) Outcome.Failure(
            DomainFailure(FailureCode.PERSISTENCE, "Revision exhausted", SnapshotList.empty()),
        ) else Outcome.Success(actual.next())
        is Outcome.Failure -> checked
        is Outcome.Unavailable -> checked
        is Outcome.Unknown -> checked
    }

/** Exception classification belongs to the adapter; cancellation always escapes unchanged. */
public suspend fun <T> cancellableOutcome(
    cancel: CancelToken,
    classify: (Exception) -> DomainFailure,
    action: suspend () -> T,
): Outcome<T> {
    return try {
        currentCoroutineContext().ensureActive()
        cancel.throwIfCancelled()
        val result = action()
        currentCoroutineContext().ensureActive()
        cancel.throwIfCancelled()
        Outcome.Success(result)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        currentCoroutineContext().ensureActive()
        cancel.throwIfCancelled()
        Outcome.Failure(classify(failure))
    }
}
