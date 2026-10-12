package com.architectpsg.commandlibrary.core.common

import com.architectpsg.commandlibrary.core.model.CancelToken
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ConsentCoordinatorTest {
    private class Token : CancelToken {
        val cancelled = AtomicBoolean(false)
        override val isCancelled: Boolean get() = cancelled.get()
        override fun throwIfCancelled() { if (isCancelled) throw kotlinx.coroutines.CancellationException() }
    }
    @Test fun revocationAndDispatchHaveOneOrderedCriticalSection(): Unit = runBlocking {
        val gate = ConsentCoordinator(); val token = Token()
        var consent = true; var dispatches = 0
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val revoke = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.withExclusive(token) { entered.complete(Unit); release.await(); consent = false }
        }
        entered.await()
        val dispatch = async(start = CoroutineStart.UNDISPATCHED) {
            gate.withExclusive(token) { if (consent) dispatches++ }
        }
        assertFalse(dispatch.isCompleted)
        release.complete(Unit); withTimeout(1000) { revoke.join(); dispatch.await() }
        assertEquals(0, dispatches)
    }
    @Test fun tokenOnlyCancellationCannotEnterBlockedCriticalSection(): Unit = runBlocking {
        val gate = ConsentCoordinator(); val token = Token(); var entered = false
        val release = CompletableDeferred<Unit>()
        val owner = launch(start = CoroutineStart.UNDISPATCHED) { gate.withExclusive(Token()) { release.await() } }
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { gate.withExclusive(token) { entered = true } }
        token.cancelled.set(true)
        try { withTimeout(1000) { waiter.await() }; fail("Cancellation expected") } catch (cancelled: kotlinx.coroutines.CancellationException) { assertFalse(cancelled is kotlinx.coroutines.TimeoutCancellationException) }
        assertFalse(entered); release.complete(Unit); owner.join()
        gate.withExclusive(Token()) { entered = true }; assertTrue(entered)
    }
    @Test fun coroutineCancellationAndFailureReleaseOwnership(): Unit = runBlocking {
        val gate = ConsentCoordinator(); val entered = CompletableDeferred<Unit>(); val never = CompletableDeferred<Unit>()
        val owner = launch { gate.withExclusive(Token()) { entered.complete(Unit); never.await() } }
        entered.await(); owner.cancelAndJoin()
        try { gate.withExclusive(Token()) { error("injected failure") }; fail("Failure expected") } catch (_: IllegalStateException) {}
        withTimeout(1000) { gate.withExclusive(Token()) {} }
    }
}
