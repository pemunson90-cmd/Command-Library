package com.architectpsg.commandlibrary.core.common

import com.architectpsg.commandlibrary.core.model.CancelToken
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex

/** One instance per application graph, shared by every consent writer and acquisition dispatcher.
 * Hold only for authoritative consent checks plus bounded dispatch/commit handoffs, never transfers.
 * The action must not call a consent-writing method: this guard is deliberately non-reentrant.
 */
public class ConsentCoordinator {
    private val mutex: Mutex = Mutex()

    public suspend fun <T> withExclusive(cancel: CancelToken, action: suspend () -> T): T {
        while (true) {
            currentCoroutineContext().ensureActive()
            cancel.throwIfCancelled()
            if (mutex.tryLock()) break
            delay(50)
        }
        try {
            currentCoroutineContext().ensureActive()
            cancel.throwIfCancelled()
            return action()
        } finally {
            mutex.unlock()
        }
    }
}
