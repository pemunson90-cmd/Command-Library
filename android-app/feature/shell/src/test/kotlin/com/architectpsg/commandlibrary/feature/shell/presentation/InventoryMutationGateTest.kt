package com.architectpsg.commandlibrary.feature.shell.presentation

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class InventoryMutationGateTest {
    @Test fun retainedGrantBlocksCompetingLaneUntilExplicitReleaseAndOldCallbackCannotReleaseSuccessor() {
        val gate=InventoryMutationGate()
        val collection=checkNotNull(gate.tryAcquire())
        assertTrue(collection.owns)
        assertTrue(gate.busy.value)
        assertNull(gate.tryAcquire())
        // The same grant survives submission, uncertain ACK and fresh-read retry: no reacquire.
        assertTrue(collection.owns)
        collection.release()
        assertFalse(collection.owns)
        assertFalse(gate.busy.value)
        val markOwned=checkNotNull(gate.tryAcquire())
        assertNotSame(collection,markOwned)
        collection.release() // A delayed callback from the previous operation.
        assertTrue(markOwned.owns)
        assertTrue(gate.busy.value)
        assertNull(gate.tryAcquire())
        markOwned.release()
        markOwned.release()
        assertFalse(gate.busy.value)
    }

    @Test fun simultaneousInitialConfirmationsAdmitExactlyOneWriter() {
        val gate=InventoryMutationGate()
        val ready=CountDownLatch(2)
        val start=CountDownLatch(1)
        val pool=Executors.newFixedThreadPool(2)
        try {
            val attempts=(1..2).map {pool.submit<InventoryMutationGate.Lease?> {
                ready.countDown()
                check(start.await(5,TimeUnit.SECONDS))
                gate.tryAcquire()
            }}
            assertTrue(ready.await(5,TimeUnit.SECONDS))
            start.countDown()
            val grants=attempts.mapNotNull {it.get(5,TimeUnit.SECONDS)}
            assertEquals(1,grants.size)
            assertTrue(grants.single().owns)
            assertTrue(gate.busy.value)
            grants.single().release()
            assertFalse(gate.busy.value)
        } finally {
            start.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS))
        }
    }
}
