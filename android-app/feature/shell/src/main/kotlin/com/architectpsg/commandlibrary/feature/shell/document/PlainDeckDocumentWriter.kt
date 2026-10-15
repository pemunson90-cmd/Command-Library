package com.architectpsg.commandlibrary.feature.shell.document

import android.content.ContentResolver
import androidx.core.net.toUri
import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.PlainDeckListLimits
import com.architectpsg.commandlibrary.core.model.SnapshotBytes
import com.architectpsg.commandlibrary.feature.shell.presentation.Problem
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal sealed interface PlainDeckWriteResult {
    /** Exact bytes, flush and close succeeded; this does not certify durable or remote storage. */
    data object Written : PlainDeckWriteResult
    data class NotStarted(val problem: Problem) : PlainDeckWriteResult
    data class PossiblyModified(val problem: Problem) : PlainDeckWriteResult
}

/**
 * Writes frozen, already-reviewed bytes to one explicitly selected destination, once.
 * Cancellation always propagates after closure. A cancelled call supplies no NotStarted proof:
 * consumers must treat its destination as possibly modified and request a new selection.
 * Blocking provider calls need not respond immediately to cancellation.
 *
 * The internal opener is solely a stream-fault test seam; both constructors use this same path.
 */
internal class PlainDeckDocumentWriter(
    private val dispatcher: CoroutineDispatcher,
    private val openStream: (DocumentRef) -> OutputStream?,
) {
    constructor(resolver: ContentResolver, dispatcher: CoroutineDispatcher) : this(
        dispatcher, { destination -> resolver.openOutputStream(destination.uri.toUri(), "wt") },
    )

    suspend fun write(destination: DocumentRef, bytes: SnapshotBytes, cancel: CancelToken): PlainDeckWriteResult =
        withContext(dispatcher) {
            val context = currentCoroutineContext()
            fun checkCancellation() {
                context.ensureActive()
                cancel.throwIfCancelled()
                if (cancel.isCancelled) throw CancellationException("Document write cancelled")
            }

            var started = false
            var stream: OutputStream? = null
            var failure: Throwable? = null
            fun record(error: Throwable) {
                val previous = failure
                when {
                    previous == null -> failure = error
                    previous === error -> Unit
                    error is CancellationException && previous !is CancellationException -> {
                        error.addSuppressed(previous)
                        failure = error
                    }
                    else -> previous.addSuppressed(error)
                }
            }

            try {
                checkCancellation()
                if (bytes.size > PlainDeckListLimits.MAX_BYTES) {
                    checkCancellation()
                    return@withContext PlainDeckWriteResult.NotStarted(Problem.INVALID_INPUT)
                }
                val snapshot = bytes.copyBytes()
                checkCancellation()
                // A provider may truncate or otherwise modify the destination even if open fails.
                started = true
                val output = openStream(destination)
                stream = output
                checkCancellation()
                if (output == null) throw IOException("Document provider returned no output stream")
                var offset = 0
                while (offset < snapshot.size) {
                    checkCancellation()
                    val count = minOf(DOCUMENT_CHUNK_BYTES, snapshot.size - offset)
                    output.write(snapshot, offset, count)
                    offset += count
                    checkCancellation()
                }
                checkCancellation()
                output.flush()
                checkCancellation()
            } catch (error: Throwable) {
                record(error)
            } finally {
                // OutputStream.close is blocking: no suspension/dispatcher hop can lose ownership,
                // and an already cancelled Job cannot skip this exactly-once cleanup.
                try { stream?.close() } catch (closing: Throwable) { record(closing) }
            }
            try { checkCancellation() } catch (error: Throwable) { record(error) }
            when (val error = failure) {
                null -> PlainDeckWriteResult.Written
                is CancellationException -> throw error
                is Exception -> {
                    val problem = when (error) {
                        is SecurityException -> Problem.PERMISSION
                        is IOException -> Problem.PERSISTENCE
                        else -> Problem.UNEXPECTED
                    }
                    if (started) PlainDeckWriteResult.PossiblyModified(problem)
                    else PlainDeckWriteResult.NotStarted(problem)
                }
                else -> throw error
            }
        }
}
