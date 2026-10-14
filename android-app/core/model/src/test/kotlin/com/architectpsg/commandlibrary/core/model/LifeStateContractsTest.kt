package com.architectpsg.commandlibrary.core.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** State DTO invariants only; legacy constructors do not certify a complete persisted game. */
class LifeStateContractsTest {
    private val seatId = EntityId("synthetic-seat")
    private fun counter(label: String = "Life") = CounterDefinition(EntityId("synthetic-counter"), label,
        null, null, 20, CounterLossPolicy.None, false)
    private fun seat() = Seat(seatId, "Synthetic", null, Knowledge.Absent("No linked deck"), 0, SnapshotList.empty())
    private fun settings() = GameSettings(Knowledge.Unknown("No format", SnapshotList.empty()), 20,
        SnapshotList.of(counter()), SnapshotList.empty(), SeatClockMode.NONE, 0)
    private fun clock() = SeatClockState(SeatClockMode.NONE, SnapshotList.empty(), null, null, true,
        ClockZeroPolicy.ALERT, ClockUndoPolicy.KEEP_ELAPSED, SnapshotList.empty())
    private fun snapshot() = GameSnapshot(GameId(ProviderId("synthetic"), "game"), 1, Revision(0), settings(),
        GameStatus.ACTIVE, UtcTimestamp(1000), Knowledge.Absent("Not finished"), SnapshotList.of(seat()),
        SnapshotList.empty(), null, 0, clock(), SnapshotList.empty(), Knowledge.Absent("No events"), Knowledge.Absent("No result"))

    @Test fun displayColorPreservesUnsignedArgbBoundariesAndRejectsOutsideValues() {
        for (value in listOf(0L, 0x80000000L, 0xffffffffL)) assertEquals(value, DisplayColor(value).argb)
        for (value in listOf(-1L, Long.MIN_VALUE, 0x100000000L, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { DisplayColor(value) }
        }
    }
    @Test fun cropAllowsFullAndPartialNormalizedRectangles() {
        assertEquals(ArtCrop(0.0, 0.0, 1.0, 1.0), ArtCrop(0.0, 0.0, 1.0, 1.0))
        val crop = ArtCrop(0.25, 0.125, 0.75, 0.875)
        assertEquals(0.25, crop.left, 0.0); assertEquals(0.875, crop.bottom, 0.0)
    }
    @Test fun cropRejectsNonfiniteOutOfRangeAndEmptyOrInvertedRectangles() {
        val invalid = listOf(
            doubleArrayOf(-0.01, 0.0, 1.0, 1.0), doubleArrayOf(0.0, -0.01, 1.0, 1.0),
            doubleArrayOf(0.0, 0.0, 1.01, 1.0), doubleArrayOf(0.0, 0.0, 1.0, 1.01),
            doubleArrayOf(0.5, 0.0, 0.5, 1.0), doubleArrayOf(0.0, 0.5, 1.0, 0.5),
            doubleArrayOf(0.75, 0.0, 0.25, 1.0), doubleArrayOf(0.0, 0.75, 1.0, 0.25))
        invalid.forEach { v -> assertThrows(IllegalArgumentException::class.java) { ArtCrop(v[0], v[1], v[2], v[3]) } }
        for (value in listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)) {
            for (index in 0..3) {
                val v = doubleArrayOf(0.0, 0.0, 1.0, 1.0); v[index] = value
                assertThrows(IllegalArgumentException::class.java) { ArtCrop(v[0], v[1], v[2], v[3]) }
            }
        }
    }
    @Test fun sharedCountdownAcceptsFiniteOvertimeAndRejectsInvalidDurations() {
        assertEquals((-5).seconds, SharedCountdown(30.seconds, (-5).seconds).remaining)
        assertEquals(Duration.ZERO, SharedCountdown(Duration.ZERO, Duration.ZERO).initial)
        for (initial in listOf((-1).milliseconds, Duration.INFINITE, -Duration.INFINITE)) {
            assertThrows(IllegalArgumentException::class.java) { SharedCountdown(initial, Duration.ZERO) }
        }
        for (remaining in listOf(Duration.INFINITE, -Duration.INFINITE)) {
            assertThrows(IllegalArgumentException::class.java) { SharedCountdown(30.seconds, remaining) }
        }
    }
    @Test fun elapsedTimersRetainKnownLineageWithoutInventingLegacyIdentity() {
        val legacy = ElapsedTimerState(Duration.ZERO, Duration.ZERO, true, null)
        assertTrue(legacy.turnLineageId is Knowledge.Unknown)
        assertNull(legacy.anchor)
        val lineage = Knowledge.Known(EntityId("synthetic-turn"))
        val anchor = ClockAnchor(UtcTimestamp(1000), 10.seconds, EntityId("synthetic-process"))
        val state = ElapsedTimerState(12.seconds, 2.seconds, false, anchor, lineage)
        assertEquals(lineage, state.turnLineageId); assertEquals(anchor, state.anchor)
        assertEquals(12.seconds, state.gameElapsed); assertEquals(2.seconds, state.turnElapsed)
    }
    @Test fun elapsedTimersRejectNegativeOrInfiniteAccumulatedTime() {
        for (bad in listOf((-1).milliseconds, Duration.INFINITE, -Duration.INFINITE)) {
            assertThrows(IllegalArgumentException::class.java) { ElapsedTimerState(bad, Duration.ZERO, true, null) }
            assertThrows(IllegalArgumentException::class.java) { ElapsedTimerState(Duration.ZERO, bad, true, null) }
        }
    }
    @Test fun timerWarningsAreAbsentOrPositiveFiniteDurations() {
        val defaults = ElapsedTimerSettings()
        assertTrue(defaults.enabled); assertNull(defaults.turnWarning); assertNull(defaults.gameWarning)
        val configured = ElapsedTimerSettings(false, 1.milliseconds, 30.seconds)
        assertFalse(configured.enabled); assertEquals(1.milliseconds, configured.turnWarning)
        for (bad in listOf(Duration.ZERO, (-1).milliseconds, Duration.INFINITE, -Duration.INFINITE)) {
            assertThrows(IllegalArgumentException::class.java) { ElapsedTimerSettings(turnWarning = bad) }
            assertThrows(IllegalArgumentException::class.java) { ElapsedTimerSettings(gameWarning = bad) }
        }
    }
    @Test fun legacyCounterDefaultsDoNotInferRoleFromLabel() {
        for (label in listOf("Life", "Commander Damage", "Poison", "Synthetic custom")) {
            val definition = counter(label)
            assertEquals(CounterRole.OTHER, definition.role); assertNull(definition.color); assertNull(definition.preset)
            assertEquals(CounterResetPolicy.MANUAL, definition.resetPolicy)
        }
        val explicit = counter().copy(role = CounterRole.LIFE, color = DisplayColor(0xff112233))
        assertEquals(CounterRole.LIFE, explicit.role)
        val preset = counter("Mana").copy(preset = CounterPreset.MANA, resetPolicy = CounterResetPolicy.ON_TURN_START)
        assertEquals(CounterPreset.MANA, preset.preset); assertEquals(CounterResetPolicy.ON_TURN_START, preset.resetPolicy)
        assertEquals(setOf(CounterRole.LIFE, CounterRole.COMMANDER_DAMAGE, CounterRole.COMMANDER_TAX, CounterRole.OTHER), CounterRole.entries.toSet())
    }
    @Test fun legacySeatAndSettingsUseAbsentPresentationAndActiveParticipationDefaults() {
        val seat = seat()
        assertEquals(SeatParticipation.Active, seat.participation)
        assertNull(seat.appearance.color); assertNull(seat.appearance.art); assertNull(seat.appearance.crop)
        assertEquals(ArtFit.COVER, seat.appearance.fit); assertEquals(SeatOrientation.UPRIGHT, seat.appearance.orientation)
        val settings = settings()
        assertEquals(GameLayoutMode.TABLETOP, settings.presentation.layout)
        assertNull(settings.presentation.arrangementId); assertFalse(settings.presentation.keepAwake)
        assertEquals(1, settings.adjustmentStep); assertFalse(settings.deductLifeForCommanderDamage)
        assertEquals(ElapsedTimerSettings(), settings.elapsedTimers)
        assertEquals(GameAlertPreferences(), settings.alertPreferences)
        assertTrue(settings.alertPreferences.visual); assertFalse(settings.alertPreferences.audio); assertFalse(settings.alertPreferences.haptic)
        for (step in listOf(0, -1, Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { settings.copy(adjustmentStep = step) }
        }
    }
    @Test fun legacySnapshotAndClockDefaultsDoNotInventTimingResultsOrDesignations() {
        val snapshot = snapshot()
        assertNull(snapshot.clock.shared)
        assertEquals(GameDesignations(), snapshot.designations)
        assertNull(snapshot.designations.monarch); assertNull(snapshot.designations.initiative)
        assertEquals(DayNightState.UNSET, snapshot.designations.dayNight); assertTrue(snapshot.designations.blessedSeats.isEmpty())
        assertEquals(Duration.ZERO, snapshot.elapsed.gameElapsed); assertEquals(Duration.ZERO, snapshot.elapsed.turnElapsed)
        assertTrue(snapshot.elapsed.paused); assertNull(snapshot.elapsed.anchor)
        assertTrue(snapshot.elapsed.turnLineageId is Knowledge.Unknown); assertTrue(snapshot.alerts.isEmpty())
        assertTrue(snapshot.result is Knowledge.Absent); assertTrue(snapshot.endedAt is Knowledge.Absent)
        // Existing constructors still permit incomplete legacy role bindings. Creation validation is separate.
        assertEquals(CounterRole.OTHER, snapshot.settings.counterDefinitions.single().role)
    }
    @Test fun gameWideAlertTargetNeedsNoInventedSeatAndRetainsAcknowledgement() {
        val alert = GameAlert(EntityId("synthetic-alert"), GameId(ProviderId("synthetic"), "game"),
            GameAlertTarget.SharedCountdown, UtcTimestamp(1000), null)
        assertEquals(GameAlertTarget.SharedCountdown, alert.target); assertNull(alert.acknowledgedAt)
        val acknowledged = alert.copy(acknowledgedAt = UtcTimestamp(1001))
        assertEquals(UtcTimestamp(1001), acknowledged.acknowledgedAt); assertNull(alert.acknowledgedAt)
    }
}
