package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.EntityId
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Stable replay identity for valid persisted seat identities; normal legacy spellings remain unchanged. */
internal class ClockZeroIdentity(seat: EntityId, turn: Long) {
    private val legacyPrefix = "clock-zero/${seat.value}/turn-$turn/"
    private val legacyBytes = legacyPrefix.toByteArray(Charsets.UTF_8).size
    // Separate namespace prevents a literal seat name from impersonating a hashed identity.
    private val boundedPrefix by lazy {
        val bytes = seat.value.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").run {
            update("command-library-clock-zero-seat-v1\u0000".toByteArray(Charsets.UTF_8))
            update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            update(bytes)
            digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        }
        "clock-zero-hashed-v1/$digest/turn-$turn/"
    }

    fun id(occurrence: Int): EntityId {
        require(occurrence > 0)
        val suffix = occurrence.toString()
        return EntityId((if (legacyBytes + suffix.length <= 65_536) legacyPrefix else boundedPrefix) + suffix)
    }

    fun matches(id: EntityId): Boolean {
        val suffix = id.value.substringAfterLast('/')
        val occurrence = suffix.toIntOrNull() ?: return false
        return occurrence > 0 && suffix == occurrence.toString() && id == id(occurrence)
    }
}
