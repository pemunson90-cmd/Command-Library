package com.architectpsg.commandlibrary.feature.shell.document

import com.architectpsg.commandlibrary.core.model.ByteChunk
import com.architectpsg.commandlibrary.core.model.SnapshotBytes
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

internal const val DOCUMENT_CHUNK_BYTES: Int = 32_768

/** Raised into the import stream once the document grows past the import byte limit. */
internal class DocumentTooLargeException(val limitBytes: Long) : IOException("Document exceeds $limitBytes bytes")

/**
 * Presents one open document as a single-use stream of contiguous [ByteChunk]s of at most
 * [DOCUMENT_CHUNK_BYTES] bytes, read on [ioDispatcher]. At most two chunk buffers are held; the
 * file is never loaded whole. The last chunk carries `final = true`; an empty file yields one empty
 * final chunk.
 *
 * Reading stops as soon as more than [maxBytes] bytes have been read. Cancelling the collector closes
 * the input immediately, which also releases a read blocked inside the document provider.
 */
internal class DocumentChunkStream(
    private val input: InputStream,
    private val maxBytes: Long,
    private val ioDispatcher: CoroutineDispatcher,
    private val onBytesRead: (Long) -> Unit,
) : Closeable {
    enum class Failure { TOO_LARGE, READ_ERROR }

    private val collected = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    @Volatile
    var failure: Failure? = null
        private set

    val isClosed: Boolean get() = closed.get()

    val chunks: Flow<ByteChunk> = flow {
        check(collected.compareAndSet(false, true)) { "A document stream can be read only once" }
        coroutineScope {
            val closeOnCancel = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    close()
                }
            }
            try {
                emitDocument()
            } finally {
                closeOnCancel.cancel()
            }
        }
    }.buffer(Channel.RENDEZVOUS).flowOn(ioDispatcher)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                input.close()
            } catch (_: IOException) {
                // Closing is best effort; the stream holds nothing else to release.
            }
        }
    }

    private suspend fun FlowCollector<ByteChunk>.emitDocument() {
        val buffers = arrayOf(ByteArray(DOCUMENT_CHUNK_BYTES), ByteArray(DOCUMENT_CHUNK_BYTES))
        var currentIndex = 0
        var total = 0L
        var currentSize = fill(buffers[currentIndex])
        total = record(total, currentSize)
        var sequence = 0L
        var offset = 0L
        while (true) {
            if (currentSize < DOCUMENT_CHUNK_BYTES) {
                emit(chunk(sequence, offset, buffers[currentIndex], currentSize, final = true))
                return
            }
            val nextIndex = 1 - currentIndex
            val nextSize = fill(buffers[nextIndex])
            total = record(total, nextSize)
            if (nextSize == 0) {
                emit(chunk(sequence, offset, buffers[currentIndex], currentSize, final = true))
                return
            }
            emit(chunk(sequence, offset, buffers[currentIndex], currentSize, final = false))
            sequence++
            offset += currentSize
            currentIndex = nextIndex
            currentSize = nextSize
        }
    }

    private fun record(total: Long, added: Int): Long {
        val updated = total + added
        if (updated > maxBytes) {
            failure = Failure.TOO_LARGE
            throw DocumentTooLargeException(maxBytes)
        }
        onBytesRead(updated)
        return updated
    }

    /** Fills [buffer] completely unless the document ends first; returns the byte count. */
    private suspend fun fill(buffer: ByteArray): Int {
        var filled = 0
        while (filled < buffer.size) {
            currentCoroutineContext().ensureActive()
            val read = try {
                input.read(buffer, filled, buffer.size - filled)
            } catch (error: IOException) {
                // A read released by close-on-cancel reports cancellation, not a read failure.
                currentCoroutineContext().ensureActive()
                failure = Failure.READ_ERROR
                throw error
            }
            if (read < 0) break
            filled += read
        }
        return filled
    }

    private fun chunk(sequence: Long, offset: Long, buffer: ByteArray, size: Int, final: Boolean): ByteChunk =
        ByteChunk(
            sequence = sequence,
            offset = offset,
            bytes = SnapshotBytes(if (size == buffer.size) buffer else buffer.copyOf(size)),
            metadata = null,
            final = final,
        )
}
