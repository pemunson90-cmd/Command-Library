package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.SnapshotList
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Single-coroutine raw-record staging; caller/framer validates JSON, source totals and delimiters.
 * Ordinary batches retain at most 2MiB or 1,000 records. A larger valid record (at most8MiB)
 * goes directly as a singleton after the prior batch flushes. No mapped graphs are retained.
 * Failure/cancellation poisons the helper: an ambiguous callback can never be replayed.
 */
internal class BoundedRecordBatches(
    private val cancel: CancelToken,
    private val onBatch: suspend (List<String>) -> Unit,
) {
    companion object {
        const val MAX_BATCH_BYTES = 2 * 1024 * 1024
        const val MAX_BATCH_RECORDS = 1000
        const val MAX_RECORD_BYTES = 8 * 1024 * 1024
    }
    private val records = ArrayList<String>()
    private var bytes = 0
    private var busy = false
    private var closed = false
    private var poisoned = false
    val retainedRecords: Int get() = records.size
    val retainedBytes: Int get() = bytes

    suspend fun add(raw: String) = operation {
        require(raw.isNotEmpty() && raw.length <= MAX_RECORD_BYTES) { "Record exceeds byte limit" }
        val size = raw.toByteArray(Charsets.UTF_8).size
        require(size <= MAX_RECORD_BYTES) { "Record exceeds byte limit" }
        if (size > MAX_BATCH_BYTES) {
            flush()
            deliver(SnapshotList.of(raw))
        } else {
            if (records.size == MAX_BATCH_RECORDS || size > MAX_BATCH_BYTES - bytes) flush()
            records.add(raw)
            bytes += size
            if (bytes == MAX_BATCH_BYTES || records.size == MAX_BATCH_RECORDS) flush()
        }
    }

    suspend fun finish() = operation {
        flush()
        closed = true
    }

    /** Use when upstream framing/verification fails before finish; drops pending records. */
    fun abort() {
        poisoned = true
        records.clear()
        bytes = 0
    }

    private suspend fun flush() {
        if (records.isEmpty()) return
        val batch = SnapshotList.copyOf(records)
        records.clear()
        bytes = 0
        deliver(batch)
    }

    private suspend fun deliver(batch: List<String>) {
        checkCancellation()
        onBatch(batch)
        checkCancellation()
    }

    private suspend fun checkCancellation() {
        currentCoroutineContext().ensureActive()
        cancel.throwIfCancelled()
        check(!poisoned) { "Record batches cannot be reused" }
    }

    private suspend fun operation(action: suspend () -> Unit) {
        check(!poisoned && !closed && !busy) { "Record batches cannot be reused" }
        busy = true
        try {
            checkCancellation()
            action()
            checkCancellation()
        } catch (failure: Throwable) {
            abort()
            throw failure
        } finally {
            busy = false
        }
    }
}
