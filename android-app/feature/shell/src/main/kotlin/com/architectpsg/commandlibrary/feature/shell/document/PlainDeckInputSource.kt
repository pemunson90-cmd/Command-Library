package com.architectpsg.commandlibrary.feature.shell.document

import com.architectpsg.commandlibrary.core.model.*
import java.io.InputStream
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CoderResult
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/** Explicit local inputs only. Construction opens nothing and generates no identities. */
internal class PlainDeckInputSource(
    private val documents: DocumentSource,
    private val dispatcher: CoroutineDispatcher,
) {
    /** Provider metadata does not certify byte size, content type or the list's card language. */
    fun document(ref: DocumentRef, id: EntityId, displayName: String): TransferInput = TransferInput(
        id, displayName, Knowledge.Unknown("Selected document type not verified", SnapshotList.empty()),
        Knowledge.Unknown("Selected document length not verified", SnapshotList.empty()),
        once {
            val stream = DocumentChunkStream(openSafely(ref), PlainDeckListLimits.MAX_BYTES.toLong(), dispatcher) { }
            try { emitAll(stream.chunks) }
            finally { withContext(NonCancellable + dispatcher) { stream.close() } }
        },
    )

    /** Strict UTF-8, bounded before emitting any bytes; malformed text is never replaced silently. */
    fun paste(text: String, id: EntityId, displayName: String): TransferInput = TransferInput(
        id, displayName, Knowledge.Known("text/plain"),
        Knowledge.Unknown("Encoded length not yet verified", SnapshotList.empty()),
        once {
            currentCoroutineContext().ensureActive()
            if (text.length > PlainDeckListLimits.MAX_BYTES) tooLarge()
            // UTF-8 needs at most three bytes per UTF-16 code unit. Cap allocation at the
            // accepted byte limit; encoder overflow rejects the entire input, without truncation.
            val bytes = ByteBuffer.allocate(minOf(PlainDeckListLimits.MAX_BYTES, text.length * 3))
            val encoder = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            checkEncoding(encoder.encode(CharBuffer.wrap(text), bytes, true))
            checkEncoding(encoder.flush(bytes))
            currentCoroutineContext().ensureActive()
            (bytes as Buffer).flip() // Use the original Buffer API on all supported Android versions.
            var sequence = 0L
            var offset = 0L
            if (!bytes.hasRemaining()) emit(ByteChunk(0, 0, SnapshotBytes(byteArrayOf()), null, true))
            while (bytes.hasRemaining()) {
                currentCoroutineContext().ensureActive()
                val chunk = ByteArray(minOf(DOCUMENT_CHUNK_BYTES, bytes.remaining()))
                bytes.get(chunk)
                emit(ByteChunk(sequence++, offset, SnapshotBytes(chunk), null, !bytes.hasRemaining()))
                offset += chunk.size
            }
        },
    )

    private fun once(read: suspend FlowCollector<ByteChunk>.() -> Unit): Flow<ByteChunk> {
        val consumed = AtomicBoolean(false)
        return flow {
            check(consumed.compareAndSet(false, true)) { "A selected input can be consumed only once" }
            currentCoroutineContext().ensureActive()
            read()
        }.buffer(Channel.RENDEZVOUS).flowOn(dispatcher)
    }

    private suspend fun openSafely(ref: DocumentRef): InputStream {
        // A cancelled dispatcher return can discard its value; keep ownership outside that hop.
        val opened = AtomicReference<InputStream?>()
        try {
            withContext(dispatcher) { opened.set(documents.open(ref)) }
            return checkNotNull(opened.getAndSet(null))
        } catch (failure: Throwable) {
            withContext(NonCancellable + dispatcher) {
                opened.getAndSet(null)?.let { input ->
                    try { input.close() } catch (closing: Exception) { failure.addSuppressed(closing) }
                }
            }
            throw failure
        }
    }

    private fun checkEncoding(result: CoderResult) {
        if (result.isOverflow) tooLarge()
        if (result.isError) result.throwException()
    }
    private fun tooLarge(): Nothing = throw DocumentTooLargeException(PlainDeckListLimits.MAX_BYTES.toLong())
}
