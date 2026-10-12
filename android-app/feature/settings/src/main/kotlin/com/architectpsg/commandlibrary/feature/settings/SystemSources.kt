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
        val millis = System.currentTimeMillis()
        return UtcTimestamp(Math.floorDiv(millis, 1000L), (Math.floorMod(millis, 1000L) * 1_000_000L).toInt())
    }

    override fun monotonicNow(): Duration = System.nanoTime().nanoseconds
}

/** Random version 4 UUID strings. */
internal object RandomUuidIdSource : IdSource {
    override fun nextId(): String = UUID.randomUUID().toString()
}
