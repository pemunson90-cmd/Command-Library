package com.architectpsg.commandlibrary.feature.collection.persistence

import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Frozen CLIR1, local inventory namespace, APPLY0/UNDO1; exact CLIP1 bytes, never a future default. */
internal object InventoryRequests {
    fun apply(expected: Revision, payload: SnapshotBytes, cancel: CancelToken): String {
        check(cancel)
        require(payload.size in 10..DeckPayloadLimits.MAX_ENCODED_BYTES)
        val bytes = payload.copyBytes()
        check(cancel)
        require(bytes[0] == 67.toByte() && bytes[1] == 76.toByte() && bytes[2] == 73.toByte() &&
            bytes[3] == 80.toByte() && bytes[4] == 0.toByte() && bytes[5] == 1.toByte())
        return digest(0, expected, bytes, cancel)
    }

    fun undo(expected: Revision, cancel: CancelToken): String = digest(1, expected, null, cancel)

    private fun digest(kind: Int, expected: Revision, patch: ByteArray?, cancel: CancelToken): String {
        check(cancel)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(byteArrayOf(67, 76, 73, 82, 0, 1, kind.toByte()))
        val source = "local-inventory".toByteArray(Charsets.US_ASCII)
        digest.update(ByteBuffer.allocate(4).putInt(source.size).array()); digest.update(source)
        digest.update(ByteBuffer.allocate(8).putLong(expected.value).array())
        if (patch != null) {
            digest.update(ByteBuffer.allocate(4).putInt(patch.size).array())
            var offset = 0
            while (offset < patch.size) {
                check(cancel)
                val count = minOf(1_024, patch.size - offset)
                digest.update(patch, offset, count); offset += count
            }
        }
        check(cancel)
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun check(cancel: CancelToken) {
        cancel.throwIfCancelled()
    }
}
