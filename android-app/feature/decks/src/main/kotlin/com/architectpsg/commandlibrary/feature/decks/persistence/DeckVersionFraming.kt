package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.io.encoding.Base64

/**
 * Frozen CLVD1/CLNV1/CLVC1 framing. Pure metadata consistency helpers, not storage certification.
 * Callers retain metadata-version admission, receipt/payload/lineage validation, epoch comparison
 * and indexed cursor-anchor checks. Invalid inputs throw for caller-owned contextual mapping.
 */
internal object DeckVersionFraming {
    private const val MAX_FIELD_BYTES = 1_024
    private const val CURSOR_BYTES = 54
    private const val CURSOR_CHARACTERS = 72
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    fun scope(id: DeckId): SnapshotBytes {
        lengths(id.source.value, id.raw)
        val provider = field(id.source.value)
        val deck = field(id.raw)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(byteArrayOf(67, 76, 86, 68, 0, 1)) // CLVD, u16 version1
        digest.frame(provider); digest.frame(deck)
        return SnapshotBytes(digest.digest())
    }

    fun metadata(summary: DeckVersionSummary, creatingSequence: Long): String {
        require(creatingSequence > 0)
        val fields = validated(summary)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(byteArrayOf(67, 76, 78, 86, 0, 1)) // CLNV, u16 version1
        digest.frame(fields[0]!!); digest.frame(fields[1]!!)
        digest.frame(fields[2]!!); digest.frame(fields[3]!!)
        digest.update(ByteBuffer.allocate(20).putLong(summary.createdAt.epochSeconds)
            .putInt(summary.createdAt.nanoAdjustment).putLong(summary.sourceRevision.value).array())
        digest.optional(fields[4])
        digest.update(when (summary.kind) { DeckVersionKind.USER -> 0.toByte(); DeckVersionKind.RECOVERY -> 1.toByte() })
        digest.optional(fields[5])
        digest.update(ByteBuffer.allocate(8).putLong(creatingSequence).array())
        return hex(digest.digest())
    }

    /** Recompute the creating CLDR request independently of generated checkpoint metadata. */
    fun requestFingerprint(summary: DeckVersionSummary): String {
        validated(summary)
        return when (summary.kind) {
            DeckVersionKind.USER -> DeckLibraryRequests.capture(summary.deckId, summary.sourceRevision, summary.label)
            DeckVersionKind.RECOVERY -> DeckLibraryRequests.restore(summary.deckId, summary.sourceRevision,
                requireNotNull(summary.restoredTargetVersionId), summary.label)
        }
    }

    fun cursor(epoch: Long, afterSequence: Long, id: DeckId): String {
        ranges(epoch, afterSequence)
        val scoped = scope(id).copyBytes()
        return base64.encode(ByteBuffer.allocate(CURSOR_BYTES).putInt(0x434c5643).putShort(1)
            .putLong(epoch).putLong(afterSequence).put(scoped).array())
    }

    fun cursor(raw: String, id: DeckId): Cursor {
        // Exact fixed length precedes alphabet scanning and Base64 allocation.
        require(raw.length == CURSOR_CHARACTERS)
        require(raw.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' })
        val bytes = base64.decode(raw)
        require(bytes.size == CURSOR_BYTES && base64.encode(bytes) == raw)
        val buffer = ByteBuffer.wrap(bytes)
        require(buffer.int == 0x434c5643 && buffer.short == 1.toShort())
        val epoch = buffer.long
        val after = buffer.long
        ranges(epoch, after)
        val storedScope = ByteArray(32).also { buffer.get(it) }
        require(MessageDigest.isEqual(storedScope, scope(id).copyBytes()))
        return Cursor(epoch, after)
    }

    internal data class Cursor(val epoch: Long, val afterSequence: Long)

    /** Every field's UTF16 size is checked before any field encoding or string equality. */
    private fun validated(summary: DeckVersionSummary): List<ByteArray?> {
        val values = listOf(summary.id.value, summary.deckId.source.value, summary.deckId.raw, summary.label,
            summary.predecessorVersionId?.value, summary.restoredTargetVersionId?.value)
        lengths(*values.toTypedArray())
        val fields = values.map { it?.let(::field) }
        require(summary.predecessorVersionId != summary.id && summary.restoredTargetVersionId != summary.id)
        when (summary.kind) {
            DeckVersionKind.USER -> require(summary.restoredTargetVersionId == null)
            DeckVersionKind.RECOVERY -> {
                require(summary.restoredTargetVersionId != null)
                require(summary.sourceRevision.value < Long.MAX_VALUE)
            }
        }
        return fields
    }

    private fun ranges(epoch: Long, after: Long) { require(epoch >= 0 && after > 0 && after <= epoch) }
    private fun lengths(vararg values: String?) { values.forEach { require(it == null || it.length <= MAX_FIELD_BYTES) } }
    private fun field(value: String): ByteArray = DeckLibraryRequests.utf8(value, MAX_FIELD_BYTES)
    private fun MessageDigest.frame(bytes: ByteArray) { update(ByteBuffer.allocate(4).putInt(bytes.size).array()); update(bytes) }
    private fun MessageDigest.optional(bytes: ByteArray?) {
        update(if (bytes == null) 0.toByte() else 1.toByte())
        if (bytes != null) frame(bytes)
    }
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
