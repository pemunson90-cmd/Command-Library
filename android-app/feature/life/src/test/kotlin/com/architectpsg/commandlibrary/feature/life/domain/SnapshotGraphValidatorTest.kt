package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SnapshotGraphValidatorTest {
    private fun base(): GameSnapshot {
        val s = LifeFixtures.snapshot()
        return s.copy(settings = s.settings.copy(counterDefinitions = SnapshotList.copyOf(s.settings.counterDefinitions.map {
            it.copy(role = when (it.id) {
                LifeFixtures.life -> CounterRole.LIFE
                LifeFixtures.commanderDamage -> CounterRole.COMMANDER_DAMAGE
                LifeFixtures.commanderTax -> CounterRole.COMMANDER_TAX
                else -> CounterRole.OTHER
            })
        })))
    }
    private val foreign = EntityId("unknown-seat")
    private val anchor = ClockAnchor(UtcTimestamp(0), 10.seconds, EntityId("session"))
    private fun bad(s: GameSnapshot) { SnapshotGraphValidator.validate(s).failure(FailureCode.CORRUPT_DATA) }
    private fun ok(s: GameSnapshot) = SnapshotGraphValidator.validate(s).success()
    private fun clock(s: GameSnapshot, c: SeatClockState) = s.copy(clock = c, settings = s.settings.copy(clockMode = c.mode))
    private fun chess(s: GameSnapshot) = s.clock.copy(mode = SeatClockMode.CHESS, seats = SnapshotList.copyOf(s.seats.map { SeatTime(it.id, 60.seconds, 20.seconds) }), activeSeat = LifeFixtures.seatA, anchor = anchor, paused = false)

    @Test fun explicitRolesAreReturnedWithoutGuessingLabels() {
        val s = base(); assertEquals(LifeFixtures.roles, ok(s))
        bad(LifeFixtures.snapshot())
        bad(s.copy(settings = s.settings.copy(counterDefinitions = SnapshotList.copyOf(s.settings.counterDefinitions.map { if (it.id == LifeFixtures.poison) it.copy(role = CounterRole.LIFE) else it }))))
        bad(s.copy(settings = s.settings.copy(initialLife = 41)))
    }
    @Test fun seatAndCounterIdentityScopeBoundsAreChecked() {
        val s = base()
        bad(s.copy(seats = SnapshotList.of(s.seats[0], s.seats[0])))
        bad(s.copy(seats = SnapshotList.copyOf(s.seats.map { it.copy(order = 0) })))
        bad(s.copy(seats = SnapshotList.copyOf(s.seats.map { if (it.id == LifeFixtures.seatC) it.copy(commanders = s.seats[0].commanders) else it })))
        bad(s.copy(activeSeatId = foreign))
        val valid = LifeFixtures.state(LifeFixtures.commanderDamage, LifeFixtures.seatA, 21, LifeFixtures.slotA)
        ok(s.copy(counters = SnapshotList.of(valid, LifeFixtures.state(LifeFixtures.life, LifeFixtures.seatA, -15))))
        for (state in listOf(valid.copy(seatId = foreign), valid.copy(definitionId = foreign), valid.copy(commanderSlotId = foreign), valid.copy(value = -1), valid.copy(definitionId = LifeFixtures.commanderTax, commanderSlotId = LifeFixtures.slotB1), valid.copy(definitionId = LifeFixtures.life))) bad(s.copy(counters = SnapshotList.of(state)))
        bad(s.copy(counters = SnapshotList.of(valid, valid)))
        bad(s.copy(counters = SnapshotList.of(LifeFixtures.state(LifeFixtures.charge, LifeFixtures.seatA, 6))))
    }
    @Test fun legacyUnknownEliminationAndNoncontiguousOrderingArePreserved() {
        val s = base(); val evidence = LossEvidence(foreign, foreign, -1, null, Knowledge.Unknown("old rules", SnapshotList.empty()), "historical")
        val seats = SnapshotList.copyOf(s.seats.map { it.copy(order = it.order + 10, participation = SeatParticipation.Eliminated(SeatExitKind.MANUAL, "manual", evidence, UtcTimestamp(2000))) })
        val saved = s.copy(seats = seats, designations = GameDesignations(monarch = LifeFixtures.seatA, blessedSeats = SnapshotList.of(LifeFixtures.seatA)))
        ok(saved); assertTrue(saved.elapsed.turnLineageId is Knowledge.Unknown)
    }
    @Test fun seatClockBanksAndRunningOwnersAreChecked() {
        val s = base(); val c = chess(s)
        ok(clock(s.copy(activeSeatId = LifeFixtures.seatB), c))
        ok(clock(s, c.copy(seats = SnapshotList.copyOf(c.seats.map { it.copy(remaining = (-1).seconds) }))))
        for (invalid in listOf(c.copy(seats = SnapshotList.empty()), c.copy(seats = SnapshotList.of(c.seats[0], c.seats[0], c.seats[2])), c.copy(activeSeat = foreign), c.copy(activeSeat = null), c.copy(anchor = null), c.copy(shared = SharedCountdown(1.seconds, 1.seconds)), c.copy(anchor = anchor.copy(monotonicElapsed = Duration.INFINITE)), c.copy(seats = SnapshotList.copyOf(c.seats.map { it.copy(remaining = Duration.INFINITE) })))) bad(clock(s, invalid))
        bad(s.copy(clock = c))
        val eliminated = s.copy(seats = SnapshotList.copyOf(s.seats.map { if (it.id == c.activeSeat) it.copy(participation = SeatParticipation.Eliminated(SeatExitKind.CONCEDED, "left", null, UtcTimestamp(0))) else it }))
        bad(clock(eliminated, c)); ok(clock(eliminated, c.copy(paused = true)))
    }
    @Test fun sharedAndNoneClockShapesAreCheckedWithoutLegacyReducerRejection() {
        val s = base(); val shared = s.clock.copy(mode = SeatClockMode.SHARED, shared = SharedCountdown(5.seconds, (-1).seconds), paused = false, anchor = anchor)
        ok(clock(s, shared)); bad(clock(s, shared.copy(shared = null)))
        bad(clock(s, shared.copy(seats = chess(s).seats)))
        bad(clock(s, shared.copy(anchor = null)))
        for (invalid in listOf(s.clock.copy(paused = false), s.clock.copy(anchor = anchor), s.clock.copy(activeSeat = LifeFixtures.seatA), s.clock.copy(seats = chess(s).seats), s.clock.copy(shared = shared.shared))) bad(s.copy(clock = invalid))
        val fired = SnapshotList.of(EntityId("old-alert")); ok(clock(s, shared.copy(firedAlertIds = fired)))
        bad(clock(s, shared.copy(firedAlertIds = SnapshotList.of(fired[0], fired[0]))))
    }
    @Test fun elapsedRequiresValidAnchorButPreservesUnknownLineage() {
        val s = base(); val e = s.elapsed.copy(paused = false, anchor = anchor)
        ok(s.copy(elapsed = e)); ok(s.copy(elapsed = e.copy(paused = true)))
        bad(s.copy(elapsed = e.copy(anchor = null)))
        bad(s.copy(elapsed = e.copy(anchor = anchor.copy(monotonicElapsed = (-1).seconds))))
        bad(s.copy(elapsed = e, settings = s.settings.copy(elapsedTimers = ElapsedTimerSettings(enabled = false))))
        bad(s.copy(status = GameStatus.COMPLETED, elapsed = e))
        bad(clock(s.copy(status = GameStatus.COMPLETED), chess(s)))
        ok(s.copy(status = GameStatus.COMPLETED))
    }
    @Test fun designationsAndHistoricalAlertsHaveCheckedIdentityWithoutWallClockOrdering() {
        val s = base(); val alert = GameAlert(EntityId("alert"), s.id, GameAlertTarget.SharedCountdown, UtcTimestamp(2000), UtcTimestamp(1000))
        ok(s.copy(alerts = SnapshotList.of(alert)))
        bad(s.copy(alerts = SnapshotList.of(alert, alert)))
        bad(s.copy(alerts = SnapshotList.of(alert.copy(gameId = GameId(LifeFixtures.provider, "other")))))
        bad(s.copy(alerts = SnapshotList.of(alert.copy(target = GameAlertTarget.SeatClock(foreign)))))
        for (d in listOf(GameDesignations(monarch = foreign), GameDesignations(initiative = foreign), GameDesignations(blessedSeats = SnapshotList.of(foreign)), GameDesignations(blessedSeats = SnapshotList.of(LifeFixtures.seatA, LifeFixtures.seatA)))) bad(s.copy(designations = d))
    }
    @Test fun knownResultIdentityRevisionAndCompletionAreCheckedWithoutInventingLegacyOutcomes() {
        val s = base(); val r = GameResult(EntityId("result"), s.id, SnapshotList.of(LifeFixtures.seatA), UtcTimestamp(500), "confirmed", Revision(7), false)
        val completed = s.copy(status = GameStatus.COMPLETED, endedAt = Knowledge.Known(r.finishedAt), result = Knowledge.Known(r))
        ok(completed)
        for (invalid in listOf(r.copy(gameId = GameId(LifeFixtures.provider, "other")), r.copy(winners = SnapshotList.of(foreign)), r.copy(revision = Revision(8)), r.copy(retracted = true))) bad(completed.copy(result = Knowledge.Known(invalid)))
        bad(s.copy(result = Knowledge.Known(r)))
        bad(completed.copy(endedAt = Knowledge.Known(UtcTimestamp(501))))
        ok(s.copy(result = Knowledge.Known(r.copy(retracted = true))))
        bad(s.copy(lastEventId = Knowledge.Known(-1L)))
    }
    @Test fun futureSnapshotVersionIsUnsupported() {
        val result = SnapshotGraphValidator.validate(base().copy(version = 2))
        assertTrue(result is Outcome.Unavailable); assertEquals(UnavailableReason.UNSUPPORTED, (result as Outcome.Unavailable).reason)
    }
}
