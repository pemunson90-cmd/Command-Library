package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.CancelToken
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BoundedRecordBatchesTest {
    private class Token : CancelToken {
        var cancelled = false
        override val isCancelled get() = cancelled
        override fun throwIfCancelled() { if (cancelled) throw CancellationException() }
    }
    private fun raw(size: Int) = "{\"x\":\"" + "x".repeat(size - 8) + "\"}"
    @Test fun exactByteThresholdAndPreflushBeforeOneMoreByte(): Unit = runBlocking {
        val batches = mutableListOf<List<Int>>()
        val b = BoundedRecordBatches(Token()) { batch -> batches.add(batch.map { it.toByteArray().size }) }
        val half = raw(1024 * 1024)
        b.add(half); assertEquals(1024 * 1024, b.retainedBytes)
        b.add(half)
        assertEquals(listOf(listOf(1024 * 1024, 1024 * 1024)), batches)
        assertEquals(0, b.retainedBytes)
        b.add(raw(2 * 1024 * 1024 - 1)); b.add("{}")
        assertEquals(listOf(2 * 1024 * 1024 - 1), batches.last())
        assertEquals(2, b.retainedBytes)
        b.finish()
        assertEquals(listOf(2), batches.last())
        assertEquals(0, b.retainedRecords)
        assertThrows(IllegalStateException::class.java) { runBlocking { b.finish() } }
    }
    @Test fun countThresholdDoesNotRetainOneThousandAndOneRecords(): Unit = runBlocking {
        val sizes = mutableListOf<Int>()
        val b = BoundedRecordBatches(Token()) { sizes.add(it.size) }
        repeat(999) { b.add("{}") }
        assertEquals(999, b.retainedRecords); assertTrue(sizes.isEmpty())
        b.add("{}"); assertEquals(listOf(1000), sizes)
        b.add("{}"); b.finish()
        assertEquals(listOf(1000, 1), sizes)
    }
    @Test fun utf8BytesCountWithoutDelimiterBytes(): Unit = runBlocking {
        val b = BoundedRecordBatches(Token()) {}
        b.add("{\"x\":\"É😀\"}")
        assertEquals(14, b.retainedBytes)
        b.finish()
    }
    @Test fun oversizedRecordsAreImmediateOrderedSingletons(): Unit = runBlocking {
        val sizes = mutableListOf<List<Int>>()
        val b = BoundedRecordBatches(Token()) { sizes.add(it.map(String::length)) }
        b.add("{}")
        b.add(raw(2 * 1024 * 1024 + 1))
        assertEquals(listOf(listOf(2), listOf(2 * 1024 * 1024 + 1)), sizes)
        assertEquals(0, b.retainedRecords)
        b.add(raw(8 * 1024 * 1024)); b.add("{}"); b.finish()
        assertEquals(listOf(listOf(2), listOf(2 * 1024 * 1024 + 1), listOf(8 * 1024 * 1024), listOf(2)), sizes)
    }
    @Test fun oneOverRecordBoundPoisonsAndReleasesPriorRecords(): Unit = runBlocking {
        var calls = 0
        val b = BoundedRecordBatches(Token()) { calls++ }
        b.add("{}")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { b.add(raw(8 * 1024 * 1024 + 1)) } }
        assertEquals(0, b.retainedRecords); assertEquals(0, b.retainedBytes); assertEquals(0, calls)
        assertThrows(IllegalStateException::class.java) { runBlocking { b.finish() } }
    }
    @Test fun callbackFailureCannotBeReplayed(): Unit = runBlocking {
        val failure = IllegalStateException("Synthetic failure")
        var calls = 0
        val b = BoundedRecordBatches(Token()) { calls++; throw failure }
        b.add("{}")
        try { b.finish(); fail("Expected failure") } catch (actual: IllegalStateException) { assertSame(failure, actual) }
        assertEquals(0, b.retainedRecords); assertEquals(0, b.retainedBytes)
        assertThrows(IllegalStateException::class.java) { runBlocking { b.add("{}") } }
        assertThrows(IllegalStateException::class.java) { runBlocking { b.finish() } }
        assertEquals(1, calls)
    }
    @Test fun singletonFlushFailureAndTokenCancellationCannotBeReplayed(): Unit = runBlocking {
        var calls = 0
        val b = BoundedRecordBatches(Token()) { calls++; throw CancellationException() }
        assertThrows(CancellationException::class.java) { runBlocking { b.add(raw(2 * 1024 * 1024 + 1)) } }
        assertEquals(0, b.retainedRecords)
        assertThrows(IllegalStateException::class.java) { runBlocking { b.finish() } }
        assertEquals(1, calls)
        val token = Token()
        val c = BoundedRecordBatches(token) { fail("No flush after cancellation") }
        c.add("{}"); token.cancelled = true
        assertThrows(CancellationException::class.java) { runBlocking { c.finish() } }
        assertEquals(0, c.retainedBytes)
        assertThrows(IllegalStateException::class.java) { runBlocking { c.add("{}") } }
    }
    @Test fun suspendedCallbackAppliesBackpressureAndCoroutineCancellationReleases(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var calls = 0
        val b = BoundedRecordBatches(Token()) { calls++; entered.complete(Unit); awaitCancellation() }
        b.add("{}")
        val job = launch { b.finish() }
        entered.await()
        assertEquals(1, calls); assertEquals(0, b.retainedRecords)
        job.cancelAndJoin()
        assertEquals(0, b.retainedBytes)
        assertThrows(IllegalStateException::class.java) { runBlocking { b.finish() } }
    }
    @Test fun abortReleasesUpstreamFailureAndImmutableBatchCannotChange(): Unit = runBlocking {
        val b = BoundedRecordBatches(Token()) { batch ->
            assertThrows(ClassCastException::class.java) { (batch as MutableList<String>).add("{}") }
        }
        b.add("{}"); b.finish()
        val c = BoundedRecordBatches(Token()) { fail("No callback after abort") }
        c.add("{}"); c.abort()
        assertEquals(0, c.retainedRecords); assertEquals(0, c.retainedBytes)
        assertThrows(IllegalStateException::class.java) { runBlocking { c.finish() } }
    }
}
