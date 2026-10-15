package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Frozen v1 fingerprints; canonical payload/receipt admission remains the caller's responsibility. */
internal object DeckDraftFrames {
    data class Payload(val version: Int, val length: Long, val sha256: String)
    data class Raw(val baseRevision: Long, val payload: Payload)
    enum class Mutation(val sql: String, val tag: Int) {
        WRITE("WRITE", 0), DISCARD_INPUT("DISCARD_INPUT", 1), DISCARD_PREP("DISCARD_PREP", 2), COMMIT("COMMIT", 3),
    }
    sealed interface Status {
        data object Prepared : Status
        data object Abandoned : Status
        data class Committed(val receiptSequence: Long, val resultRevision: Long, val effect: DeckDraftSlotEffect) : Status
    }

    fun prepared(source: DeckDraftToken, scope: DeckDraftCommitScope, patch: SnapshotBytes, cancel: CancelToken): String {
        token(source)
        return digest("CLDR", cancel) {
            byte(6); deck(source.deckId); long(source.generation); long(source.baseRevision.value)
            hash(source.rawSha256); scope(scope); encoded(patch, "CLDP", DeckPayloadLimits.MAX_ENCODED_BYTES)
        }
    }
    fun write(id: DeckId, expectedGeneration: Long, raw: SnapshotBytes, cancel: CancelToken): String {
        require(expectedGeneration >= 0)
        return digest("CLDM", cancel) {
            byte(Mutation.WRITE.tag); deck(id); long(expectedGeneration); encoded(raw, "CLDF", DeckDraftLimits.MAX_ENCODED_BYTES)
        }
    }
    fun discard(id: DeckId, expectedGeneration: Long, pending: EntityId?, mode: DeckDraftDiscardMode, cancel: CancelToken): String {
        require(expectedGeneration >= 0 && (mode != DeckDraftDiscardMode.PREPARATION_ONLY || pending != null))
        return digest("CLDM", cancel) {
            byte(if (mode == DeckDraftDiscardMode.INPUT) Mutation.DISCARD_INPUT.tag else Mutation.DISCARD_PREP.tag)
            deck(id); long(expectedGeneration); nullable(pending) { identifier(it.value) }
        }
    }
    fun commitMutation(source: DeckDraftToken, operation: EntityId, preparedSha256: String, cancel: CancelToken): String {
        token(source)
        return digest("CLDM", cancel) {
            byte(Mutation.COMMIT.tag); deck(source.deckId); long(source.generation); identifier(operation.value); hash(preparedSha256)
        }
    }
    fun slot(id: DeckId, generation: Long, raw: Raw?, latest: EntityId?, mutation: Mutation, mutationSha256: String, cancel: CancelToken): String {
        require(generation > 0)
        return digest("CLDS", cancel) {
            deck(id); long(generation); nullable(raw) { raw(it) }; nullable(latest) { identifier(it.value) }
            byte(mutation.tag); hash(mutationSha256)
        }
    }
    fun intent(id: DeckId, operation: EntityId, sourceGeneration: Long, raw: Raw, scope: DeckDraftCommitScope,
        patch: Payload, requestSha256: String, status: Status, cancel: CancelToken): String {
        require(sourceGeneration > 0)
        return digest("CLDI", cancel) {
            identifier(operation.value); deck(id); long(sourceGeneration); raw(raw); scope(scope)
            payload(patch); short(1); hash(requestSha256)
            when (status) {
                Status.Prepared -> byte(0)
                Status.Abandoned -> byte(2)
                is Status.Committed -> {
                    require(status.receiptSequence > 0 && status.resultRevision >= 0)
                    require(status.resultRevision == raw.baseRevision ||
                        raw.baseRevision < Long.MAX_VALUE && status.resultRevision == raw.baseRevision + 1)
                    byte(1); long(status.receiptSequence); long(status.resultRevision)
                    when (val effect = status.effect) {
                        is DeckDraftSlotEffect.Cleared -> {
                            require(sourceGeneration < Long.MAX_VALUE && effect.resultGeneration == sourceGeneration + 1)
                            byte(0); long(effect.resultGeneration)
                        }
                        is DeckDraftSlotEffect.Residual -> {
                            token(effect.token); require(effect.token.deckId == id && sourceGeneration < Long.MAX_VALUE &&
                                effect.token.generation == sourceGeneration + 1 && effect.token.baseRevision.value == status.resultRevision)
                            byte(1); writeEffect(effect.token)
                        }
                        is DeckDraftSlotEffect.PreservedLater -> {
                            token(effect.observedToken); require(effect.observedToken.deckId == id && effect.observedToken.generation > sourceGeneration)
                            byte(2); writeEffect(effect.observedToken)
                        }
                    }
                }
            }
        }
    }

    private fun token(value: DeckDraftToken) {
        require(value.generation > 0 && value.baseRevision.value >= 0)
        require(value.rawSha256.length == 64 && value.rawSha256.all { it in "0123456789abcdef" })
    }
    private fun digest(domain: String, cancel: CancelToken, action: Frame.() -> Unit): String {
        cancel.throwIfCancelled()
        return Frame(cancel).run {
            bytes(domain.toByteArray(Charsets.US_ASCII)); short(1); action(); finish()
        }
    }
    private class Frame(private val cancel: CancelToken) {
        private val digest = MessageDigest.getInstance("SHA-256")
        fun bytes(value: ByteArray) {
            var offset = 0
            while (offset < value.size) {
                cancel.throwIfCancelled()
                val count = minOf(4096, value.size - offset); digest.update(value, offset, count); offset += count
            }
        }
        fun byte(value: Int) { require(value in 0..255); bytes(byteArrayOf(value.toByte())) }
        fun short(value: Int) { require(value in 0..65535); bytes(ByteBuffer.allocate(2).putShort(value.toShort()).array()) }
        fun long(value: Long) { bytes(ByteBuffer.allocate(8).putLong(value).array()) }
        fun identifier(value: String) { frame(DeckLibraryRequests.utf8(value, 1024)) }
        fun deck(value: DeckId) {
            require(value.source == LocalDeckLibraryLimits.SOURCE)
            identifier(value.source.value); identifier(value.raw)
        }
        fun hash(value: String) {
            require(value.length == 64 && value.all { it in "0123456789abcdef" })
            bytes(ByteArray(32) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() })
        }
        fun scope(value: DeckDraftCommitScope) = byte(when (value) {
            DeckDraftCommitScope.TEXT_AUTOSAVE -> 0
            DeckDraftCommitScope.EXPLICIT_CONFIRMED -> 1
        })
        fun <T> nullable(value: T?, write: Frame.(T) -> Unit) {
            byte(if (value == null) 0 else 1); if (value != null) write(value)
        }
        fun payload(value: Payload) {
            require(value.version == 1 && value.length in 6..DeckPayloadLimits.MAX_ENCODED_BYTES.toLong())
            short(value.version); bytes(ByteBuffer.allocate(4).putInt(value.length.toInt()).array()); hash(value.sha256)
        }
        fun raw(value: Raw) { require(value.baseRevision >= 0); long(value.baseRevision); payload(value.payload) }
        fun writeEffect(value: DeckDraftToken) { long(value.generation); long(value.baseRevision.value); hash(value.rawSha256) }
        fun encoded(value: SnapshotBytes, magic: String, maximum: Int) {
            require(value.size in 6..maximum)
            cancel.throwIfCancelled()
            val raw = value.copyBytes()
            require(raw.take(4).toByteArray().contentEquals(magic.toByteArray(Charsets.US_ASCII)) && raw[4] == 0.toByte() && raw[5] == 1.toByte())
            frame(raw)
        }
        private fun frame(value: ByteArray) { bytes(ByteBuffer.allocate(4).putInt(value.size).array()); bytes(value) }
        fun finish(): String { cancel.throwIfCancelled(); return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } }
    }
}
