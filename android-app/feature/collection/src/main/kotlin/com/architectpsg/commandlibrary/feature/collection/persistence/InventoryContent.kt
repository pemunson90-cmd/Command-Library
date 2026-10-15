package com.architectpsg.commandlibrary.feature.collection.persistence

import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Version-pinned private consistency digests. CLOL1 revision is normalized, nothing else. */
internal class InventoryContent(private val codec: OwnedLotPayloadCodecPort, private val cancel: CancelToken) {
    fun encode(lot: OwnedLot): SnapshotBytes {
        cancel.throwIfCancelled()
        val bytes = inventoryValue(codec.encodeOwnedLot(lot))
        cancel.throwIfCancelled()
        inventoryRequire(bytes.size in 6..DeckPayloadLimits.MAX_ENCODED_BYTES, FailureCode.INVALID_INPUT)
        val header = bytes.copyBytes()
        inventoryRequire(header.take(6) == listOf<Byte>(67,76,79,76,0,1), FailureCode.INVALID_INPUT)
        return bytes
    }
    fun decode(bytes: SnapshotBytes): OwnedLot {
        cancel.throwIfCancelled()
        val lot = when (val result = codec.decodeOwnedLot(bytes)) {
            is Outcome.Success -> result.value
            is Outcome.Unavailable -> if (result.reason == UnavailableReason.UNSUPPORTED) throw InventoryProblem(result) else inventoryFail(FailureCode.CORRUPT_DATA)
            else -> inventoryFail(FailureCode.CORRUPT_DATA)
        }
        cancel.throwIfCancelled()
        return lot
    }
    fun digest(lots: Collection<OwnedLot>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(byteArrayOf(67,76,73,67,0,1)); digest.update(int(lots.size))
        for (lot in lots.sortedWith { a,b -> compare(a.id.value,b.id.value,cancel) }) {
            cancel.throwIfCancelled()
            val bytes = encode(lot.copy(revision = Revision(0))).copyBytes()
            digest.update(int(bytes.size)); update(digest, bytes, cancel)
        }
        cancel.throwIfCancelled()
        return hex(digest.digest())
    }
    companion object {
        fun int(value: Int): ByteArray = ByteBuffer.allocate(4).putInt(value).array()
        fun utf8(value: String): ByteArray = value.toByteArray(Charsets.UTF_8)
        fun id(value: String, code: FailureCode = FailureCode.INVALID_INPUT): ByteArray {
            try {
                inventoryRequire(value.length <= 1024 && value.isNotBlank(), code)
                val b = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
                inventoryRequire(b.remaining() in 1..1024, code)
                return ByteArray(b.remaining()).also { b.get(it) }
            } catch (_: java.nio.charset.CharacterCodingException) { inventoryFail(code) }
        }
        fun compare(a: String,b: String,cancel: CancelToken): Int {
            cancel.throwIfCancelled(); val x=utf8(a);val y=utf8(b)
            for(i in 0 until minOf(x.size,y.size)) { if(i % 1024 == 0) cancel.throwIfCancelled(); val d=(x[i].toInt() and 255)-(y[i].toInt() and 255);if(d!=0)return d }
            return x.size-y.size
        }
        fun update(digest: MessageDigest, bytes: ByteArray, cancel: CancelToken) {
            var offset=0
            while(offset<bytes.size) { cancel.throwIfCancelled();val count=minOf(1024,bytes.size-offset);digest.update(bytes,offset,count);offset+=count }
        }
        fun sha(bytes: ByteArray,cancel: CancelToken): String = MessageDigest.getInstance("SHA-256").let { update(it,bytes,cancel);hex(it.digest()) }
        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        fun same(a: OwnedLot?,b: OwnedLot?): Boolean = if(a==null||b==null) a==b else a.copy(revision=b.revision)==b
    }
}
internal class InventoryProblem(val result: Outcome<Nothing>): RuntimeException()
internal fun inventoryFail(code: FailureCode): Nothing = throw InventoryProblem(Outcome.Failure(DomainFailure(code,"Invalid inventory data or operation.",SnapshotList.empty())))
internal fun inventoryRequire(valid: Boolean,code: FailureCode=FailureCode.CORRUPT_DATA) { if(!valid) inventoryFail(code) }
internal fun <T> inventoryValue(outcome: Outcome<T>): T = when(outcome) {
    is Outcome.Success -> outcome.value
    is Outcome.Failure -> throw InventoryProblem(outcome)
    is Outcome.Unavailable -> throw InventoryProblem(outcome)
    is Outcome.Unknown -> throw InventoryProblem(outcome)
}
