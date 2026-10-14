package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Canonical identity binding, not a secret/authenticated capability or cross-page snapshot token. */
internal class LifeEventCursor(id: GameId) {
    private val digest = MessageDigest.getInstance("SHA-256").run {
        update("command-library-life-events-v1\u0000".toByteArray(Charsets.UTF_8))
        for (part in listOf(id.source.value, id.raw)) {
            val bytes = part.toByteArray(Charsets.UTF_8) // caller's identity has already passed strict UTF8/bounds
            update(ByteBuffer.allocate(4).putInt(bytes.size).array()); update(bytes)
        }
        digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }
    fun encode(key: LifeEventKey): String = "le1:$digest:${key.revision}:${key.eventId}"
    fun decode(value: String): LifeEventKey {
        requireGame(value.length <= PageRequest.MAX_CURSOR_BYTES, FailureCode.INVALID_INPUT, "Oversized event cursor")
        val parts = value.split(':')
        requireGame(parts.size == 4 && parts[0] == "le1" && parts[1] == digest, FailureCode.INVALID_INPUT, "Wrong event cursor version/game")
        val revision = parts[2].toLongOrNull()
        val event = parts[3].toLongOrNull()
        requireGame(revision != null && revision >= 1 && event != null && event >= 0, FailureCode.INVALID_INPUT, "Invalid event cursor key")
        val key = LifeEventKey(revision!!, event!!)
        requireGame(encode(key) == value, FailureCode.INVALID_INPUT, "Noncanonical event cursor")
        return key
    }
}
