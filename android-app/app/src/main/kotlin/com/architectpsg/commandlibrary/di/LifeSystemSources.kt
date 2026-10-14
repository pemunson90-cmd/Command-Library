package com.architectpsg.commandlibrary.di

import android.os.SystemClock
import com.architectpsg.commandlibrary.core.model.Clock
import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.RandomSource
import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import java.security.SecureRandom
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/** Life timing includes device sleep; catalog timing retains its existing clock. */
internal object AndroidLifeClock : Clock {
    override fun utcNow(): UtcTimestamp = timestampFromEpochMillis(System.currentTimeMillis())
    override fun monotonicNow(): Duration = SystemClock.elapsedRealtimeNanos().nanoseconds
}

/** One identity per application process, never persisted or regenerated per game/graph. */
internal object ProcessLifeIdentity {
    val id: EntityId = EntityId(UUID.randomUUID().toString())
}

/** JDK bounded rejection sampling; the transactional service records outcomes for replay/undo. */
internal object SystemGameRandom : RandomSource {
    private val source = SecureRandom()
    override fun nextInt(upperBound: Int): Int = source.nextInt(upperBound)
}
