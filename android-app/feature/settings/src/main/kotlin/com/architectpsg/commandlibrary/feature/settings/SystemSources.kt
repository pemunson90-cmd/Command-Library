package com.architectpsg.commandlibrary.feature.settings

import com.architectpsg.commandlibrary.core.model.Clock
import com.architectpsg.commandlibrary.core.model.IdSource
import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/** Wall-clock UTC from the system. Millisecond resolution is all java.lang.System offers on every API level. */
internal object SystemUtcClock : Clock {
    override fun utcNow(): UtcTimestamp {
        return timestampFromEpochMillis(System.currentTimeMillis())
    }

    override fun monotonicNow(): Duration = System.nanoTime().nanoseconds
}

/** Random version 4 UUID strings. */
internal object RandomUuidIdSource : IdSource {
    override fun nextId(): String = UUID.randomUUID().toString()
}

/** API-23-safe floor division, including wall-clock dates before the epoch. */
internal fun timestampFromEpochMillis(millis: Long): UtcTimestamp {
    val quotient = millis / 1000L
    val remainder = millis % 1000L
    val seconds = if (remainder < 0) quotient - 1 else quotient
    val positiveRemainder = if (remainder < 0) remainder + 1000L else remainder
    return UtcTimestamp(seconds, (positiveRemainder * 1_000_000L).toInt())
}
