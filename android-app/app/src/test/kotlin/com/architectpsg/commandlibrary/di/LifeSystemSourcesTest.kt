package com.architectpsg.commandlibrary.di

import android.app.Application
import android.os.SystemClock
import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import java.time.Duration as JavaDuration
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import kotlin.time.Duration.Companion.nanoseconds

/** Real provider behavior on the Android API shadow; not hardware sleep or native execution proof. */
@RunWith(RobolectricTestRunner::class)
// Match the existing shell SDK34 sandbox supported by the accepted JDK17.
@Config(sdk = [34], application = Application::class)
class LifeSystemSourcesTest {
    @Test fun monotonicTimeIncludesDeepSleepWhileUptimeDoesNot() {
        val elapsed = AndroidLifeClock.monotonicNow()
        val uptime = SystemClock.uptimeMillis()
        ShadowSystemClock.simulateDeepSleep(JavaDuration.ofSeconds(7))
        assertEquals(7_000_000_000L.nanoseconds, AndroidLifeClock.monotonicNow() - elapsed)
        assertEquals(uptime, SystemClock.uptimeMillis())
        assertEquals(SystemClock.elapsedRealtimeNanos().nanoseconds, AndroidLifeClock.monotonicNow())
    }

    @Test fun utcUsesSystemWallTimeAndFloorConversionHandlesBeforeEpoch() {
        val before = System.currentTimeMillis()
        val utc = AndroidLifeClock.utcNow()
        val after = System.currentTimeMillis()
        val millis = utc.epochSeconds * 1000L + utc.nanoAdjustment / 1_000_000L
        assertTrue(millis in before..after)
        assertEquals(UtcTimestamp(-1,999_000_000), timestampFromEpochMillis(-1))
        assertEquals(UtcTimestamp(-2,999_000_000), timestampFromEpochMillis(-1001))
        assertEquals(UtcTimestamp(0), timestampFromEpochMillis(0))
    }

    @Test fun processIdentityIsOneStableUuidAcrossIndependentReads() {
        val first = ProcessLifeIdentity.id
        repeat(100) { assertSame(first, ProcessLifeIdentity.id) }
        assertEquals(4, UUID.fromString(first.value).version())
        assertEquals(2, UUID.fromString(first.value).variant())
    }

    @Test fun boundedRandomHandlesOneLargeBoundsAndRejectsNonpositiveBounds() {
        repeat(32) {
            assertEquals(0, SystemGameRandom.nextInt(1))
            for (bound in listOf(2,6,7,10_000,Int.MAX_VALUE)) {
                assertTrue(SystemGameRandom.nextInt(bound) in 0 until bound)
            }
        }
        for (invalid in listOf(0,-1,Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { SystemGameRandom.nextInt(invalid) }
        }
    }
}
