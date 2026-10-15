package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlin.io.encoding.Base64

/** Frozen CLDR1 framing. Never substitute a future default snapshot encoding for CLDK1. */
internal object DeckLibraryRequests {
    fun create(payload: SnapshotBytes): String {
        require(payload.size in 6..DeckPayloadLimits.MAX_ENCODED_BYTES)
        val bytes = payload.copyBytes()
        require(bytes.take(6) == listOf<Byte>(67, 76, 68, 75, 0, 1))
        return digest(0) { frame(bytes) }
    }

    fun duplicate(id: DeckId, expected: Revision, name: String): String = digest(1) {
        frame(utf8(id.source.value, 1024)); frame(utf8(id.raw, 1024))
        update(ByteBuffer.allocate(8).putLong(expected.value).array())
        frame(utf8(name, 1024))
    }

    fun apply(id: DeckId, expected: Revision, payload: SnapshotBytes): String {
        require(payload.size in 6..DeckPayloadLimits.MAX_ENCODED_BYTES)
        val bytes = payload.copyBytes()
        require(bytes.take(6) == listOf<Byte>(67, 76, 68, 80, 0, 1))
        return digest(2) {
            frame(utf8(id.source.value, 1024)); frame(utf8(id.raw, 1024))
            update(ByteBuffer.allocate(8).putLong(expected.value).array()); frame(bytes)
        }
    }

    fun undo(id: DeckId, expected: Revision): String = digest(3) {
        frame(utf8(id.source.value, 1024)); frame(utf8(id.raw, 1024))
        update(ByteBuffer.allocate(8).putLong(expected.value).array())
    }

    fun utf8(value: String, maximum: Int): ByteArray {
        require(value.isNotBlank() && value.length <= maximum)
        val buffer = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
        require(buffer.remaining() <= maximum)
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    fun decodeUtf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun digest(kind: Int, body: MessageDigest.() -> Unit): String = MessageDigest.getInstance("SHA-256").run {
        update(byteArrayOf(67, 76, 68, 82, 0, 1, kind.toByte())); body(); hex(digest())
    }
    private fun MessageDigest.frame(bytes: ByteArray) { update(ByteBuffer.allocate(4).putInt(bytes.size).array()); update(bytes) }
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }

    data class Cursor(val epoch: Long, val after: String)
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    fun cursor(epoch: Long, after: String): String {
        require(epoch >= 0)
        val raw = utf8(after, 1024)
        return base64.encode(ByteBuffer.allocate(13 + raw.size).put(1).putLong(epoch).putInt(raw.size).put(raw).array())
    }
    fun cursor(value: String): Cursor {
        require(value.length <= PageRequest.MAX_CURSOR_BYTES && value.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' })
        val bytes = base64.decode(value)
        require(bytes.size in 14..1037)
        val buffer = ByteBuffer.wrap(bytes)
        require(buffer.get() == 1.toByte())
        val epoch = buffer.long
        val length = buffer.int
        require(epoch >= 0 && length in 1..1024 && length == buffer.remaining())
        val after = decodeUtf8(ByteArray(length).also { buffer.get(it) })
        require(cursor(epoch, after) == value)
        return Cursor(epoch, after)
    }
}

internal class DeckLibraryProblem(val status: Outcome<Nothing>) : RuntimeException()
internal fun reject(code: FailureCode, message: String): Nothing =
    throw DeckLibraryProblem(Outcome.Failure(DomainFailure(code, message, SnapshotList.empty())))
internal fun corrupt(valid: Boolean) { if (!valid) reject(FailureCode.CORRUPT_DATA, "Invalid stored deck data") }
internal fun <T> value(outcome: Outcome<T>): T = when (outcome) {
    is Outcome.Success -> outcome.value
    is Outcome.Failure -> throw DeckLibraryProblem(outcome)
    is Outcome.Unavailable -> throw DeckLibraryProblem(outcome)
    is Outcome.Unknown -> throw DeckLibraryProblem(outcome)
}
internal inline fun <T> checked(code: FailureCode, action: () -> T): T = try { action() }
catch (e: CharacterCodingException) { reject(code, "Invalid deck UTF8") }
catch (e: IllegalArgumentException) { reject(code, "Invalid deck input") }
