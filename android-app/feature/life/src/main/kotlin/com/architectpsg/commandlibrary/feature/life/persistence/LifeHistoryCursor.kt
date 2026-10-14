package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import java.security.MessageDigest

internal data class LifeHistoryKey(val started: UtcTimestamp, val id: GameId)
internal data class LifeHistoryAnchor(val started: UtcTimestamp, val identityHash: String)

/** Compact binding, not a secret capability or a cross-page snapshot token. */
internal class LifeHistoryCursor(filterParts: List<String?>) {
    private val filterHash = historyDigest("command-library-life-history-filter-v1", filterParts)
    fun encode(key: LifeHistoryKey): String = encode(LifeHistoryAnchor(key.started, identity(key.id)))
    private fun encode(anchor: LifeHistoryAnchor): String = "lh1:$filterHash:${anchor.started.epochSeconds}:${anchor.started.nanoAdjustment}:${anchor.identityHash}"
    fun decode(value: String): LifeHistoryAnchor {
        requireGame(value.length <= PageRequest.MAX_CURSOR_BYTES, FailureCode.INVALID_INPUT, "Oversized history cursor")
        val parts = value.split(':')
        requireGame(parts.size == 5 && parts[0] == "lh1" && parts[1] == filterHash, FailureCode.INVALID_INPUT, "Wrong history cursor version/filter")
        val seconds = parts[2].toLongOrNull(); val nano = parts[3].toIntOrNull()
        requireGame(seconds != null && nano != null && nano in 0..999_999_999 && parts[4].length == 64 && parts[4].all { it in '0'..'9' || it in 'a'..'f' }, FailureCode.INVALID_INPUT, "Invalid history cursor key")
        val anchor = LifeHistoryAnchor(UtcTimestamp(seconds!!, nano!!), parts[4])
        requireGame(encode(anchor) == value, FailureCode.INVALID_INPUT, "Noncanonical history cursor")
        return anchor
    }
    fun identity(id: GameId): String = historyDigest("command-library-life-history-game-v1", listOf(id.source.value, id.raw))
}
private fun historyDigest(domain: String, parts: List<String?>): String = MessageDigest.getInstance("SHA-256").run {
    update(domain.toByteArray(Charsets.UTF_8))
    for (part in parts) {
        val bytes = part?.toByteArray(Charsets.UTF_8)
        update(ByteBuffer.allocate(4).putInt(bytes?.size ?: -1).array())
        if (bytes != null) update(bytes)
    }
    digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
internal fun compareHistoryIds(left: GameId, right: GameId): Int = binaryText(left.source.value, right.source.value).takeIf { it != 0 } ?: binaryText(left.raw, right.raw)
private fun binaryText(left: String, right: String): Int {
    val a = left.toByteArray(Charsets.UTF_8); val b = right.toByteArray(Charsets.UTF_8)
    for (index in 0 until minOf(a.size, b.size)) {
        val difference = (a[index].toInt() and 255) - (b[index].toInt() and 255)
        if (difference != 0) return difference
    }
    return a.size.compareTo(b.size)
}
internal fun compareHistoryTimes(left: UtcTimestamp, right: UtcTimestamp): Int =
    left.epochSeconds.compareTo(right.epochSeconds).takeIf { it != 0 } ?: left.nanoAdjustment.compareTo(right.nanoAdjustment)
