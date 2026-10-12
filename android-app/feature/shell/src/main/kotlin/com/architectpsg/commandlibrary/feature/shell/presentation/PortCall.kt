package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.common.CoroutineCancelToken
import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.UnavailableReason
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The presentation-level reason an operation did not succeed. Screens map each value to a plain
 * English resource; domain messages and evidence never reach the UI.
 */
internal enum class Problem {
    INVALID_INPUT,
    STALE_REVISION,
    STALE_GENERATION,
    CORRUPT_DATA,
    PERSISTENCE,
    PERMISSION,
    SOURCE_MISMATCH,
    TRANSPORT,
    INTERNAL,
    OFFLINE,
    NOT_INSTALLED,
    NOT_FOUND,
    CONSENT_REQUIRED,
    REVOKED,
    UNSUPPORTED,
    SOURCE_UNAVAILABLE,
    KEY_MISSING,

    /** The port reported that the outcome itself is unknown. */
    UNKNOWN_RESULT,

    /** The port threw instead of returning an Outcome. */
    UNEXPECTED,
}

internal sealed interface PortResult<out T> {
    data class Ok<T>(val value: T) : PortResult<T>
    data class Failed(val problem: Problem) : PortResult<Nothing>
}

internal fun FailureCode.toProblem(): Problem = when (this) {
    FailureCode.INVALID_INPUT -> Problem.INVALID_INPUT
    FailureCode.STALE_REVISION -> Problem.STALE_REVISION
    FailureCode.STALE_GENERATION -> Problem.STALE_GENERATION
    FailureCode.CORRUPT_DATA -> Problem.CORRUPT_DATA
    FailureCode.TRANSPORT -> Problem.TRANSPORT
    FailureCode.PERSISTENCE -> Problem.PERSISTENCE
    FailureCode.PERMISSION -> Problem.PERMISSION
    FailureCode.SOURCE_MISMATCH -> Problem.SOURCE_MISMATCH
    FailureCode.INTERNAL -> Problem.INTERNAL
}

internal fun UnavailableReason.toProblem(): Problem = when (this) {
    UnavailableReason.OFFLINE -> Problem.OFFLINE
    UnavailableReason.NOT_INSTALLED -> Problem.NOT_INSTALLED
    UnavailableReason.NOT_FOUND -> Problem.NOT_FOUND
    UnavailableReason.CONSENT_REQUIRED -> Problem.CONSENT_REQUIRED
    UnavailableReason.REVOKED -> Problem.REVOKED
    UnavailableReason.UNSUPPORTED -> Problem.UNSUPPORTED
    UnavailableReason.SOURCE_UNAVAILABLE -> Problem.SOURCE_UNAVAILABLE
    UnavailableReason.KEY_MISSING -> Problem.KEY_MISSING
}

/**
 * Calls a core port with a cancel token bound to the calling coroutine. Caller cancellation always
 * propagates. A port that throws, or leaks a cancellation the caller did not request, yields
 * [Problem.UNEXPECTED] instead of silently ending the operation.
 */
internal suspend fun <T> callPort(action: suspend (CancelToken) -> Outcome<T>): PortResult<T> {
    val context = currentCoroutineContext()
    val outcome = try {
        action(CoroutineCancelToken(context))
    } catch (error: Exception) {
        // CancellationException is an Exception: it is rethrown here only when this caller was cancelled.
        context.ensureActive()
        return PortResult.Failed(Problem.UNEXPECTED)
    }
    return when (outcome) {
        is Outcome.Success -> PortResult.Ok(outcome.value)
        is Outcome.Failure -> PortResult.Failed(outcome.error.code.toProblem())
        is Outcome.Unavailable -> PortResult.Failed(outcome.reason.toProblem())
        is Outcome.Unknown -> PortResult.Failed(Problem.UNKNOWN_RESULT)
    }
}
