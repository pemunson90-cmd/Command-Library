package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.Clock
import com.architectpsg.commandlibrary.core.model.ClockAlert
import com.architectpsg.commandlibrary.core.model.ClockAnchor
import com.architectpsg.commandlibrary.core.model.ClockUndoPolicy
import com.architectpsg.commandlibrary.core.model.ClockZeroPolicy
import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.GameAction
import com.architectpsg.commandlibrary.core.model.GameId
import com.architectpsg.commandlibrary.core.model.GameSnapshot
import com.architectpsg.commandlibrary.core.model.GameStatus
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.SeatClockMode
import com.architectpsg.commandlibrary.core.model.SeatClockState
import com.architectpsg.commandlibrary.core.model.SeatTime
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.core.model.UnavailableReason
import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.gameId
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.seatA
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.seatB
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.seatC
import com.architectpsg.commandlibrary.feature.life.domain.SeatClockReducer.zeroAlertId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatClockReducerTest {
    private val process1 = EntityId("process-1")
    private val process2 = EntityId("process-2")
    private val twoSeats = SnapshotList.of(LifeFixtures.seat(seatA, 0), LifeFixtures.seat(seatB, 1))

    /** Monotonic seconds since an arbitrary process origin; wall time defaults to an unrelated synthetic epoch. */
    private fun reading(monotonicSeconds: Long, session: EntityId = process1, wallSeconds: Long = 50_000 + monotonicSeconds) =
        ClockReading(monotonicSeconds.seconds, UtcTimestamp(wallSeconds), session)

    private fun clockGame(
        aRemaining: Duration = 10.minutes,
        bRemaining: Duration = 10.minutes,
        initial: Duration = 10.minutes,
        active: EntityId? = seatA,
        paused: Boolean = false,
        mode: SeatClockMode = SeatClockMode.CHESS,
        zero: ClockZeroPolicy = ClockZeroPolicy.ALERT,
        undo: ClockUndoPolicy = ClockUndoPolicy.KEEP_ELAPSED,
        times: List<SeatTime> = listOf(SeatTime(seatA, initial, aRemaining), SeatTime(seatB, initial, bRemaining)),
    ): GameSnapshot = LifeFixtures.snapshot(
        seats = twoSeats,
        clock = SeatClockState(
            mode = mode,
            seats = SnapshotList.copyOf(times),
            activeSeat = active,
            anchor = ClockAnchor(UtcTimestamp(50_100), 100.seconds, process1),
            paused = paused,
            zeroPolicy = zero,
            undoPolicy = undo,
            firedAlertIds = SnapshotList.empty(),
        ),
        activeSeatId = active,
        turnNumber = 1,
    )

    private fun GameSnapshot.remaining(seat: EntityId): Duration = clock.seats.single { it.seatId == seat }.remaining

    @Test fun onlyTheActiveSeatIsChargedAndTheAnchorMovesToTheReading() {
        val input = clockGame()
        val settled = SeatClockReducer.settle(input, reading(160)).success()
        assertEquals(9.minutes, settled.snapshot.remaining(seatA))
        assertEquals(10.minutes, settled.snapshot.remaining(seatB))
        assertEquals(ClockAnchor(UtcTimestamp(50_160), 160.seconds, process1), settled.snapshot.clock.anchor)
        assertTrue(settled.alerts.isEmpty())
        assertEquals(input.revision, settled.snapshot.revision)
        assertEquals(10.minutes, input.remaining(seatA))
    }

    @Test fun switchingMovesChargingToTheNewSeatAndKeepsBalances() {
        val switched = SeatClockReducer.reduce(clockGame(), GameAction.SwitchTurn(seatB), reading(130)).success().snapshot
        assertEquals(9.minutes + 30.seconds, switched.remaining(seatA))
        assertEquals(10.minutes, switched.remaining(seatB))
        assertEquals(seatB, switched.clock.activeSeat)
        assertEquals(seatB, switched.activeSeatId)
        assertEquals(2L, switched.turnNumber)
        val later = SeatClockReducer.settle(switched, reading(150)).success().snapshot
        assertEquals(9.minutes + 30.seconds, later.remaining(seatA))
        assertEquals(9.minutes + 40.seconds, later.remaining(seatB))
    }

    @Test fun pauseStopsChargingAndResumeReanchorsWithoutCountingThePause() {
        val paused = SeatClockReducer.reduce(clockGame(), GameAction.ClockPause(true), reading(130)).success().snapshot
        assertTrue(paused.clock.paused)
        assertEquals(9.minutes + 30.seconds, paused.remaining(seatA))
        assertEquals(paused, SeatClockReducer.settle(paused, reading(500)).success().snapshot)
        SeatClockReducer.setPaused(paused, true, reading(501)).failure(FailureCode.INVALID_INPUT)
        val resumed = SeatClockReducer.reduce(paused, GameAction.ClockPause(false), reading(500)).success().snapshot
        assertFalse(resumed.clock.paused)
        assertEquals(9.minutes + 20.seconds, SeatClockReducer.settle(resumed, reading(510)).success().snapshot.remaining(seatA))
        SeatClockReducer.setPaused(resumed, false, reading(511)).failure(FailureCode.INVALID_INPUT)
        SeatClockReducer.setPaused(clockGame(active = null, paused = true), false, reading(1)).failure(FailureCode.INVALID_INPUT)
    }

    @Test fun wallClockJumpsNeverChangeElapsedTime() {
        val backwards = SeatClockReducer.settle(clockGame(), reading(105, wallSeconds = 50_100 - 3_600)).success().snapshot
        assertEquals(9.minutes + 55.seconds, backwards.remaining(seatA))
        val forwards = SeatClockReducer.switchTurn(backwards, seatB, reading(110, wallSeconds = 50_100 + 86_400)).success().snapshot
        assertEquals(9.minutes + 50.seconds, forwards.remaining(seatA))
        val settled = SeatClockReducer.settle(forwards, reading(115, wallSeconds = 0)).success().snapshot
        assertEquals(9.minutes + 55.seconds, settled.remaining(seatB))
        assertEquals(UtcTimestamp(0), settled.clock.anchor!!.wallTime)
    }

    @Test fun monotonicRegressionAndNonFiniteReadingsAreRejected() {
        val input = clockGame()
        SeatClockReducer.settle(input, reading(99)).failure(FailureCode.INVALID_INPUT)
        SeatClockReducer.switchTurn(input, seatB, reading(99)).failure(FailureCode.INVALID_INPUT)
        SeatClockReducer.settle(input, ClockReading(Duration.INFINITE, UtcTimestamp(1), process1)).failure(FailureCode.INVALID_INPUT)
        assertEquals(10.minutes, input.remaining(seatA))
    }

    @Test fun anotherProcessMonotonicOriginIsNeverReusedAndRecoveryKeepsDurableBalances() {
        val running = clockGame(aRemaining = 7.minutes)
        SeatClockReducer.settle(running, reading(200, process2)).failure(FailureCode.INVALID_INPUT)
        SeatClockReducer.switchTurn(running, seatB, reading(200, process2)).failure(FailureCode.INVALID_INPUT)
        SeatClockReducer.setPaused(running, true, reading(200, process2)).failure(FailureCode.INVALID_INPUT)
        SeatClockReducer.observe(running, reading(200, process2)).failure(FailureCode.INVALID_INPUT)
        // The new process's monotonic origin is unrelated (5s) and wall time says hours passed; neither is charged.
        val recovered = SeatClockReducer.recoverAfterRestart(running, reading(5, process2, wallSeconds = 90_000)).success()
        assertTrue(recovered.pausedByRecovery)
        assertTrue(recovered.snapshot.clock.paused)
        assertEquals(7.minutes, recovered.snapshot.remaining(seatA))
        assertEquals(ClockAnchor(UtcTimestamp(90_000), 5.seconds, process2), recovered.snapshot.clock.anchor)
        val resumed = SeatClockReducer.setPaused(recovered.snapshot, false, reading(10, process2)).success().snapshot
        assertEquals(6.minutes, SeatClockReducer.settle(resumed, reading(70, process2)).success().snapshot.remaining(seatA))
        val again = SeatClockReducer.recoverAfterRestart(resumed, reading(80, process2)).success()
        assertFalse(again.pausedByRecovery)
        assertEquals(resumed, again.snapshot)
        val idle = SeatClockReducer.recoverAfterRestart(clockGame(paused = true), reading(1, process2)).success()
        assertFalse(idle.pausedByRecovery)
        assertEquals(process2, idle.snapshot.clock.anchor!!.processSessionId)
    }

    @Test fun alertPolicyFiresOncePerTransitionAndKeepsCountingOvertime() {
        val crossed = SeatClockReducer.settle(clockGame(aRemaining = 10.seconds), reading(115)).success()
        assertEquals((-5).seconds, crossed.snapshot.remaining(seatA))
        val alert = ClockAlert(zeroAlertId(seatA, 1, 1), gameId, seatA, UtcTimestamp(50_115), null)
        assertEquals(listOf(alert), crossed.alerts)
        assertEquals(listOf(alert.id), crossed.snapshot.clock.firedAlertIds)
        assertFalse(crossed.snapshot.clock.paused)
        val again = SeatClockReducer.settle(crossed.snapshot, reading(120)).success()
        assertEquals((-10).seconds, again.snapshot.remaining(seatA))
        assertTrue(again.alerts.isEmpty())
        assertEquals(listOf(alert.id), again.snapshot.clock.firedAlertIds)
        assertEquals(1, SeatClockReducer.settle(clockGame(aRemaining = 10.seconds), reading(110)).success().alerts.size)
    }

    @Test fun pausePolicyStopsAtExactlyZero() {
        val crossed = SeatClockReducer.settle(clockGame(aRemaining = 10.seconds, zero = ClockZeroPolicy.PAUSE), reading(115)).success()
        assertEquals(Duration.ZERO, crossed.snapshot.remaining(seatA))
        assertTrue(crossed.snapshot.clock.paused)
        assertEquals(1, crossed.alerts.size)
        assertTrue(crossed.timedOutSeats.isEmpty())
        val later = SeatClockReducer.settle(crossed.snapshot, reading(300)).success()
        assertEquals(crossed.snapshot, later.snapshot)
        assertTrue(later.alerts.isEmpty())
    }

    @Test fun eliminatePolicyReportsTheTimeoutWithoutEliminatingTheSeat() {
        val input = clockGame(aRemaining = 10.seconds, zero = ClockZeroPolicy.ELIMINATE)
        val crossed = SeatClockReducer.settle(input, reading(130)).success()
        assertEquals(listOf(seatA), crossed.timedOutSeats)
        assertEquals(Duration.ZERO, crossed.snapshot.remaining(seatA))
        assertTrue(crossed.snapshot.clock.paused)
        assertEquals(input.seats, crossed.snapshot.seats)
        assertEquals(GameStatus.ACTIVE, crossed.snapshot.status)
    }

    @Test fun perTurnClockStartsEachTurnFromInitialAndAlertsPerTurn() {
        val start = clockGame(aRemaining = 10.seconds, initial = 60.seconds, bRemaining = 60.seconds, mode = SeatClockMode.PER_TURN)
        val crossed = SeatClockReducer.settle(start, reading(115)).success().snapshot
        val toB = SeatClockReducer.switchTurn(crossed, seatB, reading(120)).success().snapshot
        assertEquals((-10).seconds, toB.remaining(seatA))
        assertEquals(60.seconds, toB.remaining(seatB))
        val toA = SeatClockReducer.switchTurn(toB, seatA, reading(130)).success().snapshot
        assertEquals(50.seconds, toA.remaining(seatB))
        assertEquals(60.seconds, toA.remaining(seatA))
        assertEquals(3L, toA.turnNumber)
        val late = SeatClockReducer.settle(toA, reading(200)).success()
        assertEquals((-10).seconds, late.snapshot.remaining(seatA))
        assertEquals(listOf(zeroAlertId(seatA, 1, 1), zeroAlertId(seatA, 3, 1)), late.snapshot.clock.firedAlertIds)
    }

    @Test fun oneSeatPerTurnGameAdvancesItsOwnTurn() {
        val solo = LifeFixtures.snapshot(
            seats = SnapshotList.of(LifeFixtures.seat(seatA, 0)),
            clock = SeatClockState(SeatClockMode.PER_TURN, SnapshotList.of(SeatTime(seatA, 30.seconds, 12.seconds)), seatA, ClockAnchor(UtcTimestamp(1), 100.seconds, process1), false, ClockZeroPolicy.ALERT, ClockUndoPolicy.KEEP_ELAPSED, SnapshotList.empty()),
            activeSeatId = seatA,
            turnNumber = 1,
        )
        val next = SeatClockReducer.switchTurn(solo, seatA, reading(105)).success().snapshot
        assertEquals(30.seconds, next.remaining(seatA))
        assertEquals(2L, next.turnNumber)
    }

    @Test fun keepElapsedUndoRevertsTurnAssignmentButNotChargedTime() {
        val before = clockGame(undo = ClockUndoPolicy.KEEP_ELAPSED)
        val current = SeatClockReducer.switchTurn(before, seatB, reading(130)).success().snapshot.copy(revision = before.revision.next())
        val undone = SeatClockReducer.undo(before, current, reading(150)).success().snapshot
        assertEquals(seatA, undone.clock.activeSeat)
        assertEquals(seatA, undone.activeSeatId)
        assertEquals(1L, undone.turnNumber)
        assertEquals(9.minutes + 30.seconds, undone.remaining(seatA))
        assertEquals(9.minutes + 40.seconds, undone.remaining(seatB))
        assertEquals(150.seconds, undone.clock.anchor!!.monotonicElapsed)
        assertEquals(current.revision, undone.revision)
        assertEquals(9.minutes + 20.seconds, SeatClockReducer.settle(undone, reading(160)).success().snapshot.remaining(seatA))
    }

    @Test fun restoreTimeUndoContinuesThePreActionTimeline() {
        val before = clockGame(undo = ClockUndoPolicy.RESTORE_TIME)
        val current = SeatClockReducer.switchTurn(before, seatB, reading(130)).success().snapshot
        val undone = SeatClockReducer.undo(before, current, reading(150)).success().snapshot
        assertEquals(seatA, undone.clock.activeSeat)
        assertEquals(1L, undone.turnNumber)
        assertEquals(9.minutes + 10.seconds, undone.remaining(seatA))
        assertEquals(10.minutes, undone.remaining(seatB))
        assertFalse(undone.clock.paused)
    }

    @Test fun undoingAPauseFollowsTheExplicitPolicy() {
        val keep = clockGame(undo = ClockUndoPolicy.KEEP_ELAPSED)
        val keepPaused = SeatClockReducer.setPaused(keep, true, reading(130)).success().snapshot
        val keepUndone = SeatClockReducer.undo(keep, keepPaused, reading(190)).success().snapshot
        assertFalse(keepUndone.clock.paused)
        assertEquals(9.minutes + 30.seconds, keepUndone.remaining(seatA))
        assertEquals(9.minutes + 20.seconds, SeatClockReducer.settle(keepUndone, reading(200)).success().snapshot.remaining(seatA))
        val restore = clockGame(undo = ClockUndoPolicy.RESTORE_TIME)
        val restorePaused = SeatClockReducer.setPaused(restore, true, reading(130)).success().snapshot
        val restoreUndone = SeatClockReducer.undo(restore, restorePaused, reading(190)).success().snapshot
        assertFalse(restoreUndone.clock.paused)
        assertEquals(8.minutes + 30.seconds, restoreUndone.remaining(seatA))
    }

    @Test fun redoneTurnFiresAFreshAlertWhileTheUndoItselfFiresNothing() {
        val before = clockGame(aRemaining = 20.seconds, bRemaining = 5.seconds, undo = ClockUndoPolicy.RESTORE_TIME)
        val switched = SeatClockReducer.switchTurn(before, seatB, reading(105)).success().snapshot
        val crossed = SeatClockReducer.settle(switched, reading(115)).success()
        assertEquals(listOf(zeroAlertId(seatB, 2, 1)), crossed.alerts.map { it.id })
        val undone = SeatClockReducer.undo(before, crossed.snapshot, reading(116)).success()
        assertTrue(undone.alerts.isEmpty())
        assertEquals(4.seconds, undone.snapshot.remaining(seatA))
        assertEquals(5.seconds, undone.snapshot.remaining(seatB))
        assertEquals(listOf(zeroAlertId(seatB, 2, 1)), undone.snapshot.clock.firedAlertIds)
        val again = SeatClockReducer.switchTurn(undone.snapshot, seatB, reading(117)).success().snapshot
        val recrossed = SeatClockReducer.settle(again, reading(125)).success()
        assertEquals((-3).seconds, recrossed.snapshot.remaining(seatB))
        assertEquals(listOf(zeroAlertId(seatB, 2, 2)), recrossed.alerts.map { it.id })
        assertEquals(listOf(zeroAlertId(seatB, 2, 1), zeroAlertId(seatB, 2, 2)), recrossed.snapshot.clock.firedAlertIds)
    }

    @Test fun redoneTurnTimeoutIsReportedAgainUnderEliminate() {
        val before = clockGame(aRemaining = 20.seconds, bRemaining = 5.seconds, zero = ClockZeroPolicy.ELIMINATE, undo = ClockUndoPolicy.RESTORE_TIME)
        val crossed = SeatClockReducer.settle(SeatClockReducer.switchTurn(before, seatB, reading(105)).success().snapshot, reading(115)).success()
        assertEquals(listOf(seatB), crossed.timedOutSeats)
        val undone = SeatClockReducer.undo(before, crossed.snapshot, reading(116)).success()
        assertTrue(undone.timedOutSeats.isEmpty())
        val again = SeatClockReducer.switchTurn(undone.snapshot, seatB, reading(117)).success().snapshot
        val recrossed = SeatClockReducer.settle(again, reading(125)).success()
        assertEquals(listOf(seatB), recrossed.timedOutSeats)
        assertEquals(Duration.ZERO, recrossed.snapshot.remaining(seatB))
        assertTrue(recrossed.snapshot.clock.paused)
    }

    @Test fun undoReplayOfAnAlreadyFiredTransitionFiresAndReportsNothing() {
        val alerting = clockGame(aRemaining = 10.seconds, undo = ClockUndoPolicy.RESTORE_TIME)
        val crossedAtSwitch = SeatClockReducer.switchTurn(alerting, seatB, reading(115)).success()
        assertEquals(listOf(zeroAlertId(seatA, 1, 1)), crossedAtSwitch.alerts.map { it.id })
        assertEquals((-5).seconds, crossedAtSwitch.snapshot.remaining(seatA))
        val replayed = SeatClockReducer.undo(alerting, crossedAtSwitch.snapshot, reading(120)).success()
        assertTrue(replayed.alerts.isEmpty())
        assertEquals((-10).seconds, replayed.snapshot.remaining(seatA))
        assertEquals(listOf(zeroAlertId(seatA, 1, 1)), replayed.snapshot.clock.firedAlertIds)
        val eliminating = clockGame(aRemaining = 10.seconds, zero = ClockZeroPolicy.ELIMINATE, undo = ClockUndoPolicy.RESTORE_TIME)
        val timedOut = SeatClockReducer.switchTurn(eliminating, seatB, reading(115)).success()
        assertEquals(listOf(seatA), timedOut.timedOutSeats)
        val replayedTimeout = SeatClockReducer.undo(eliminating, timedOut.snapshot, reading(120)).success()
        assertTrue(replayedTimeout.timedOutSeats.isEmpty())
        assertTrue(replayedTimeout.alerts.isEmpty())
    }

    @Test fun restoredTimelineThatNewlyCrossesZeroFiresAndReports() {
        val before = clockGame(aRemaining = 10.seconds, zero = ClockZeroPolicy.ELIMINATE, undo = ClockUndoPolicy.RESTORE_TIME)
        val paused = SeatClockReducer.setPaused(before, true, reading(105)).success()
        assertTrue(paused.alerts.isEmpty())
        val undone = SeatClockReducer.undo(before, paused.snapshot, reading(120)).success()
        assertEquals(listOf(zeroAlertId(seatA, 1, 1)), undone.alerts.map { it.id })
        assertEquals(listOf(seatA), undone.timedOutSeats)
        assertEquals(Duration.ZERO, undone.snapshot.remaining(seatA))
        assertTrue(undone.snapshot.clock.paused)
    }

    @Test fun aSeatAlreadyAtZeroNeitherRefiresNorRunsBelowZeroUnderClampingPolicies() {
        val toB = SeatClockReducer.switchTurn(clockGame(aRemaining = 10.seconds), seatB, reading(110)).success()
        assertEquals(Duration.ZERO, toB.snapshot.remaining(seatA))
        assertEquals(listOf(zeroAlertId(seatA, 1, 1)), toB.alerts.map { it.id })
        val toA = SeatClockReducer.switchTurn(toB.snapshot, seatA, reading(120)).success().snapshot
        val overtime = SeatClockReducer.settle(toA, reading(125)).success()
        assertTrue(overtime.alerts.isEmpty())
        assertEquals((-5).seconds, overtime.snapshot.remaining(seatA))
        for (policy in listOf(ClockZeroPolicy.PAUSE, ClockZeroPolicy.ELIMINATE)) {
            val stopped = SeatClockReducer.settle(clockGame(aRemaining = 10.seconds, zero = policy), reading(115)).success().snapshot
            val resumed = SeatClockReducer.setPaused(stopped, false, reading(120)).success().snapshot
            assertEquals(Duration.ZERO, SeatClockReducer.observe(resumed, reading(130)).success().seats.first().remaining)
            val held = SeatClockReducer.settle(resumed, reading(150)).success()
            assertEquals(Duration.ZERO, held.snapshot.remaining(seatA))
            assertTrue(held.snapshot.clock.paused)
            assertTrue(held.alerts.isEmpty())
            assertTrue(held.timedOutSeats.isEmpty())
        }
    }

    @Test fun zeroCrossingInsideASwitchUsesTheOutgoingTurn() {
        val switched = SeatClockReducer.switchTurn(clockGame(aRemaining = 10.seconds), seatB, reading(115)).success()
        assertEquals(listOf(zeroAlertId(seatA, 1, 1)), switched.alerts.map { it.id })
        assertEquals((-5).seconds, switched.snapshot.remaining(seatA))
        assertEquals(2L, switched.snapshot.turnNumber)
    }

    @Test fun restoreTimeUndoAfterARestartKeepsDurableChargeAndStaysPaused() {
        val before = clockGame(undo = ClockUndoPolicy.RESTORE_TIME)
        val current = SeatClockReducer.switchTurn(before, seatB, reading(130)).success().snapshot
        val recovered = SeatClockReducer.recoverAfterRestart(current, reading(5, process2)).success().snapshot
        val undone = SeatClockReducer.undo(before, recovered, reading(10, process2)).success().snapshot
        assertEquals(1L, undone.turnNumber)
        assertEquals(seatA, undone.clock.activeSeat)
        assertTrue(undone.clock.paused)
        assertEquals(ClockAnchor(UtcTimestamp(50_010), 10.seconds, process2), undone.clock.anchor)
        assertEquals(9.minutes + 30.seconds, undone.remaining(seatA))
        assertEquals(10.minutes, undone.remaining(seatB))
    }

    @Test fun keepElapsedUndoAfterARestartKeepsTheRecoveryPause() {
        val before = clockGame(undo = ClockUndoPolicy.KEEP_ELAPSED)
        val current = SeatClockReducer.switchTurn(before, seatB, reading(130)).success().snapshot
        val recovered = SeatClockReducer.recoverAfterRestart(current, reading(5, process2)).success().snapshot
        val undone = SeatClockReducer.undo(before, recovered, reading(10, process2)).success().snapshot
        assertTrue(undone.clock.paused)
        assertEquals(seatA, undone.clock.activeSeat)
        assertEquals(9.minutes + 30.seconds, SeatClockReducer.settle(undone, reading(70, process2)).success().snapshot.remaining(seatA))
    }

    @Test fun perTurnKeepElapsedUndoRevertsTheRefill() {
        val solo = LifeFixtures.snapshot(
            seats = SnapshotList.of(LifeFixtures.seat(seatA, 0)),
            clock = SeatClockState(SeatClockMode.PER_TURN, SnapshotList.of(SeatTime(seatA, 30.seconds, 10.seconds)), seatA, ClockAnchor(UtcTimestamp(1), 100.seconds, process1), false, ClockZeroPolicy.ALERT, ClockUndoPolicy.KEEP_ELAPSED, SnapshotList.empty()),
            activeSeatId = seatA,
            turnNumber = 1,
        )
        val next = SeatClockReducer.switchTurn(solo, seatA, reading(100)).success().snapshot
        assertEquals(30.seconds, next.remaining(seatA))
        val undone = SeatClockReducer.undo(solo, next, reading(101)).success().snapshot
        assertEquals(1L, undone.turnNumber)
        assertEquals(9.seconds, undone.remaining(seatA))
    }

    @Test fun sameSeatRefillUndoKeepsChargesOnBothSidesOfTheSwitch() {
        for (policy in ClockZeroPolicy.entries) {
            for (solo in listOf(true, false)) {
                val before = clockGame(aRemaining = 10.seconds, initial = 30.seconds, mode = SeatClockMode.PER_TURN, zero = policy).let {
                    if (!solo) it else it.copy(seats = SnapshotList.of(LifeFixtures.seat(seatA, 0)), clock = it.clock.copy(seats = SnapshotList.of(it.clock.seats.first())))
                }
                val switched = SeatClockReducer.switchTurn(before, seatA, reading(105)).success().snapshot
                val undone = SeatClockReducer.undo(before, switched, reading(108)).success()
                assertEquals("$policy solo=$solo: keep five seconds before refill and three after", 2.seconds, undone.snapshot.remaining(seatA))
                assertEquals(1L, undone.snapshot.turnNumber)
                assertEquals(seatA, undone.snapshot.clock.activeSeat)
                assertTrue(undone.alerts.isEmpty())
            }
        }
    }

    @Test fun incomingRefillUndoUnderPauseClampsAndReportsANewZeroTransitionOnce() =
        checkIncomingRefillZeroUndo(ClockZeroPolicy.PAUSE)

    @Test fun incomingRefillUndoUnderEliminateClampsAndReportsANewZeroTransitionOnce() =
        checkIncomingRefillZeroUndo(ClockZeroPolicy.ELIMINATE)

    private fun checkIncomingRefillZeroUndo(policy: ClockZeroPolicy) {
        val before = clockGame(bRemaining = 10.seconds, initial = 30.seconds, mode = SeatClockMode.PER_TURN, zero = policy)
        val switched = SeatClockReducer.switchTurn(before, seatB, reading(100)).success().snapshot
        val undone = SeatClockReducer.undo(before, switched, reading(115)).success()
        assertEquals("$policy must clamp the reverted incoming bank", Duration.ZERO, undone.snapshot.remaining(seatB))
        assertTrue(undone.snapshot.clock.paused)
        assertEquals(listOf(zeroAlertId(seatB, 2, 1)), undone.alerts.map { it.id })
        assertEquals(listOf(zeroAlertId(seatB, 2, 1)), undone.snapshot.clock.firedAlertIds)
        assertEquals(if (policy == ClockZeroPolicy.ELIMINATE) listOf(seatB) else emptyList<EntityId>(), undone.timedOutSeats)
        assertEquals(seatA, undone.snapshot.activeSeatId)
        assertEquals(before.seats, undone.snapshot.seats)
        val settledAgain = SeatClockReducer.settle(undone.snapshot, reading(120)).success()
        assertTrue(settledAgain.alerts.isEmpty())
        assertTrue(settledAgain.timedOutSeats.isEmpty())
    }

    @Test fun incomingRefillUndoUnderAlertKeepsNegativeTimeAndFiresOnce() {
        val before = clockGame(bRemaining = 10.seconds, initial = 30.seconds, mode = SeatClockMode.PER_TURN)
        val switched = SeatClockReducer.switchTurn(before, seatB, reading(100)).success().snapshot
        val undone = SeatClockReducer.undo(before, switched, reading(115)).success()
        assertEquals((-5).seconds, undone.snapshot.remaining(seatB))
        assertFalse(undone.snapshot.clock.paused)
        assertEquals(listOf(zeroAlertId(seatB, 2, 1)), undone.alerts.map { it.id })
        assertTrue(undone.timedOutSeats.isEmpty())
    }

    @Test fun incomingRefillUndoDoesNotRepeatAnAlreadyReportedTimeout() {
        val before = clockGame(bRemaining = 10.seconds, initial = 30.seconds, mode = SeatClockMode.PER_TURN, zero = ClockZeroPolicy.ELIMINATE)
        val switched = SeatClockReducer.switchTurn(before, seatB, reading(100)).success().snapshot
        val crossed = SeatClockReducer.settle(switched, reading(135)).success()
        assertEquals(listOf(seatB), crossed.timedOutSeats)
        val undone = SeatClockReducer.undo(before, crossed.snapshot, reading(140)).success()
        assertEquals(Duration.ZERO, undone.snapshot.remaining(seatB))
        assertTrue(undone.alerts.isEmpty())
        assertTrue(undone.timedOutSeats.isEmpty())
        assertEquals(crossed.snapshot.clock.firedAlertIds, undone.snapshot.clock.firedAlertIds)
        val redone = SeatClockReducer.switchTurn(undone.snapshot, seatB, reading(145)).success().snapshot
        val resumed = SeatClockReducer.setPaused(redone, false, reading(145)).success().snapshot
        val recrossed = SeatClockReducer.settle(resumed, reading(180)).success()
        assertEquals(listOf(zeroAlertId(seatB, 2, 2)), recrossed.alerts.map { it.id })
        assertEquals(listOf(seatB), recrossed.timedOutSeats)
    }

    @Test fun sameSeatRefillUndoAfterCheckpointRequiresActionProvenance() {
        val before = clockGame(aRemaining = 10.seconds, initial = 30.seconds, mode = SeatClockMode.PER_TURN)
        val afterAction = SeatClockReducer.switchTurn(before, seatA, reading(105)).success().snapshot
        val checkpoint = SeatClockReducer.settle(afterAction, reading(107)).success().snapshot
        SeatClockReducer.undo(before, checkpoint, reading(108)).failure(FailureCode.INVALID_INPUT)
    }

    @Test fun sameSeatRefillUndoUsesTheOriginalActionAnchorAfterCheckpoint() {
        for (policy in ClockZeroPolicy.entries) {
            val before = clockGame(aRemaining = 10.seconds, initial = 30.seconds, mode = SeatClockMode.PER_TURN, zero = policy)
            val afterAction = SeatClockReducer.switchTurn(before, seatA, reading(105)).success().snapshot
            val checkpoint = SeatClockReducer.settle(afterAction, reading(107)).success().snapshot
            val undone = SeatClockReducer.undo(before, afterAction, checkpoint, reading(108)).success()
            assertEquals(2.seconds, undone.snapshot.remaining(seatA))
            assertTrue(undone.alerts.isEmpty())
            assertEquals(1L, undone.snapshot.turnNumber)
        }
    }

    @Test fun sameSeatRefillUndoKeepsOnlyTimeActuallyChargedBeforeAPause() {
        val before = clockGame(aRemaining = 10.seconds, initial = 30.seconds, mode = SeatClockMode.PER_TURN)
        val afterAction = SeatClockReducer.switchTurn(before, seatA, reading(105)).success().snapshot
        val paused = SeatClockReducer.setPaused(afterAction, true, reading(107)).success().snapshot
        val undone = SeatClockReducer.undo(before, afterAction, paused, reading(200)).success()
        assertEquals(3.seconds, undone.snapshot.remaining(seatA))
        assertFalse(undone.snapshot.clock.paused)
        assertTrue(undone.alerts.isEmpty())
    }

    @Test fun sameSeatRefillUndoKeepsDurableChargesAcrossRestartWithActionProvenance() {
        val before = clockGame(aRemaining = 10.seconds, initial = 30.seconds, mode = SeatClockMode.PER_TURN)
        val afterAction = SeatClockReducer.switchTurn(before, seatA, reading(105)).success().snapshot
        val checkpoint = SeatClockReducer.settle(afterAction, reading(107)).success().snapshot
        val recovered = SeatClockReducer.recoverAfterRestart(checkpoint, reading(5, process2)).success().snapshot
        SeatClockReducer.undo(before, recovered, reading(10, process2)).failure(FailureCode.INVALID_INPUT)
        val undone = SeatClockReducer.undo(before, afterAction, recovered, reading(10, process2)).success()
        assertEquals(3.seconds, undone.snapshot.remaining(seatA))
        assertTrue(undone.snapshot.clock.paused)
        assertEquals(reading(10, process2).anchor(), undone.snapshot.clock.anchor)
        assertTrue(undone.alerts.isEmpty())
    }

    @Test fun sameSeatRefillUndoReportsTheReconstructedCrossingAfterCheckpoint() {
        for (policy in ClockZeroPolicy.entries) {
            val before = clockGame(aRemaining = 10.seconds, initial = 30.seconds, mode = SeatClockMode.PER_TURN, zero = policy)
            val afterAction = SeatClockReducer.switchTurn(before, seatA, reading(105)).success().snapshot
            val checkpoint = SeatClockReducer.settle(afterAction, reading(108)).success().snapshot
            val undone = SeatClockReducer.undo(before, afterAction, checkpoint, reading(112)).success()
            assertEquals(if (policy == ClockZeroPolicy.ALERT) (-2).seconds else Duration.ZERO, undone.snapshot.remaining(seatA))
            assertEquals(policy != ClockZeroPolicy.ALERT, undone.snapshot.clock.paused)
            assertEquals(listOf(zeroAlertId(seatA, 2, 1)), undone.alerts.map { it.id })
            assertEquals(if (policy == ClockZeroPolicy.ELIMINATE) listOf(seatA) else emptyList<EntityId>(), undone.timedOutSeats)
            val repeated = SeatClockReducer.settle(undone.snapshot, reading(113)).success()
            assertTrue(repeated.alerts.isEmpty())
            assertTrue(repeated.timedOutSeats.isEmpty())
        }
    }

    @Test fun sameSeatRefillUndoDoesNotRefireTheOutgoingZeroTransition() {
        for (policy in listOf(ClockZeroPolicy.PAUSE, ClockZeroPolicy.ELIMINATE)) {
            val before = clockGame(aRemaining = 3.seconds, initial = 30.seconds, mode = SeatClockMode.PER_TURN, zero = policy)
            val afterAction = SeatClockReducer.switchTurn(before, seatA, reading(105)).success().snapshot
            val undone = SeatClockReducer.undo(before, afterAction, afterAction, reading(108)).success()
            assertEquals(Duration.ZERO, undone.snapshot.remaining(seatA))
            assertTrue(undone.snapshot.clock.paused)
            assertTrue(undone.alerts.isEmpty())
            assertTrue(undone.timedOutSeats.isEmpty())
            assertEquals(listOf(zeroAlertId(seatA, 1, 1)), undone.snapshot.clock.firedAlertIds)
        }
    }

    @Test fun undoRejectsActionProvenanceFromADifferentTimeline() {
        val before = clockGame(aRemaining = 10.seconds, initial = 30.seconds, mode = SeatClockMode.PER_TURN)
        val afterAction = SeatClockReducer.switchTurn(before, seatA, reading(105)).success().snapshot
        SeatClockReducer.undo(before, afterAction.copy(turnNumber = 9), afterAction, reading(108)).failure(FailureCode.INVALID_INPUT)
        SeatClockReducer.undo(before, afterAction.copy(id = GameId(LifeFixtures.provider, "other-game")), afterAction, reading(108)).failure(FailureCode.INVALID_INPUT)
    }

    @Test fun nonFiniteReadingsAreRejectedBeforeAnyChange() {
        val infinite = ClockReading(Duration.INFINITE, UtcTimestamp(1), process1)
        val paused = clockGame(paused = true)
        SeatClockReducer.setPaused(paused, false, infinite).failure(FailureCode.INVALID_INPUT)
        SeatClockReducer.switchTurn(paused, seatB, infinite).failure(FailureCode.INVALID_INPUT)
        SeatClockReducer.recoverAfterRestart(clockGame(), ClockReading(Duration.INFINITE, UtcTimestamp(1), process2)).failure(FailureCode.INVALID_INPUT)
        SeatClockReducer.observe(paused, infinite).failure(FailureCode.INVALID_INPUT)
        SeatClockReducer.settle(LifeFixtures.snapshot(), infinite).failure(FailureCode.INVALID_INPUT)
        val keep = clockGame(undo = ClockUndoPolicy.KEEP_ELAPSED)
        val keepPaused = SeatClockReducer.setPaused(keep, true, reading(130)).success().snapshot
        SeatClockReducer.undo(keep, keepPaused, infinite).failure(FailureCode.INVALID_INPUT)
    }

    @Test fun observeRejectsArithmeticThatSettleWouldReject() {
        val huge = (100L * 365 * 1_000_000).days
        val game = clockGame(aRemaining = -huge)
        val reading = ClockReading(huge + 100.seconds, UtcTimestamp(1), process1)
        SeatClockReducer.settle(game, reading).failure(FailureCode.INVALID_INPUT)
        SeatClockReducer.observe(game, reading).failure(FailureCode.INVALID_INPUT)
    }

    @Test fun undoRejectsMismatchedSnapshots() {
        val before = clockGame()
        val current = SeatClockReducer.switchTurn(before, seatB, reading(130)).success().snapshot
        SeatClockReducer.undo(before.copy(id = GameId(LifeFixtures.provider, "game-2")), current, reading(140)).failure(FailureCode.INVALID_INPUT)
        val perTurn = clockGame(mode = SeatClockMode.PER_TURN)
        SeatClockReducer.undo(perTurn, current, reading(140)).failure(FailureCode.INVALID_INPUT)
        val threeSeats = current.copy(
            seats = SnapshotList.copyOf(current.seats + LifeFixtures.seat(seatC, 2)),
            clock = current.clock.copy(seats = SnapshotList.copyOf(current.clock.seats + SeatTime(seatC, 10.minutes, 10.minutes))),
        )
        SeatClockReducer.undo(before, threeSeats, reading(140)).failure(FailureCode.INVALID_INPUT)
    }

    @Test fun observeIsReadOnlyAndReportsPendingZero() {
        val input = clockGame(aRemaining = 30.seconds)
        val view = SeatClockReducer.observe(input, reading(110)).success()
        assertEquals(listOf(SeatClockView(seatA, 20.seconds, true), SeatClockView(seatB, 10.minutes, false)), view.seats)
        assertEquals(20.seconds, view.untilZero)
        assertFalse(view.zeroPending)
        val pending = SeatClockReducer.observe(clockGame(aRemaining = 30.seconds, zero = ClockZeroPolicy.PAUSE), reading(140)).success()
        assertEquals(Duration.ZERO, pending.seats.first().remaining)
        assertTrue(pending.zeroPending)
        assertNull(pending.untilZero)
        assertTrue(input.clock.firedAlertIds.isEmpty())
        assertEquals((-10).seconds, SeatClockReducer.observe(input, reading(140)).success().seats.first().remaining)
        val settledOvertime = SeatClockReducer.settle(clockGame(aRemaining = 10.seconds), reading(115)).success().snapshot
        val afterSettle = SeatClockReducer.observe(settledOvertime, reading(120)).success()
        assertFalse(afterSettle.zeroPending)
        assertEquals((-10).seconds, afterSettle.seats.first().remaining)
    }

    @Test fun noClockModeTracksTurnsOnly() {
        val none = LifeFixtures.snapshot(seats = twoSeats, activeSeatId = seatA, turnNumber = 4)
        val switched = SeatClockReducer.switchTurn(none, seatB, reading(1)).success().snapshot
        assertEquals(seatB, switched.activeSeatId)
        assertEquals(5L, switched.turnNumber)
        assertEquals(none.clock, switched.clock)
        SeatClockReducer.setPaused(none, false, reading(1)).failure(FailureCode.INVALID_INPUT)
        assertEquals(none, SeatClockReducer.settle(none, reading(1)).success().snapshot)
        assertEquals(RestartRecovery(none, false), SeatClockReducer.recoverAfterRestart(none, reading(1, process2)).success())
        assertTrue(SeatClockReducer.observe(none, reading(1)).success().seats.isEmpty())
    }

    @Test fun sharedModeIsReportedUnsupportedRatherThanGuessed() {
        val outcome = SeatClockReducer.settle(clockGame(mode = SeatClockMode.SHARED), reading(110))
        assertTrue(outcome is Outcome.Unavailable && outcome.reason == UnavailableReason.UNSUPPORTED)
    }

    @Test fun inconsistentClocksAreReportedAsCorrupt() {
        val good = clockGame()
        SeatClockReducer.settle(good.copy(settings = good.settings.copy(clockMode = SeatClockMode.NONE)), reading(110)).failure(FailureCode.CORRUPT_DATA)
        SeatClockReducer.settle(clockGame(times = listOf(SeatTime(seatA, 1.minutes, 1.minutes))), reading(110)).failure(FailureCode.CORRUPT_DATA)
        SeatClockReducer.settle(clockGame(times = listOf(SeatTime(seatA, 1.minutes, 1.minutes), SeatTime(seatB, 1.minutes, 1.minutes), SeatTime(seatC, 1.minutes, 1.minutes))), reading(110)).failure(FailureCode.CORRUPT_DATA)
        SeatClockReducer.settle(clockGame(times = listOf(SeatTime(seatA, 1.minutes, 1.minutes), SeatTime(seatA, 1.minutes, 1.minutes), SeatTime(seatB, 1.minutes, 1.minutes))), reading(110)).failure(FailureCode.CORRUPT_DATA)
        SeatClockReducer.settle(clockGame(aRemaining = Duration.INFINITE), reading(110)).failure(FailureCode.CORRUPT_DATA)
        SeatClockReducer.settle(good.copy(clock = good.clock.copy(anchor = null)), reading(110)).failure(FailureCode.CORRUPT_DATA)
        SeatClockReducer.settle(good.copy(clock = good.clock.copy(activeSeat = null)), reading(110)).failure(FailureCode.CORRUPT_DATA)
        SeatClockReducer.settle(good.copy(clock = good.clock.copy(activeSeat = seatC)), reading(110)).failure(FailureCode.CORRUPT_DATA)
        SeatClockReducer.settle(good.copy(clock = good.clock.copy(anchor = ClockAnchor(UtcTimestamp(1), Duration.INFINITE, process1))), reading(110)).failure(FailureCode.CORRUPT_DATA)
        SeatClockReducer.settle(good.copy(status = GameStatus.COMPLETED), reading(110)).failure(FailureCode.CORRUPT_DATA)
        val pausedDone = clockGame(paused = true).copy(status = GameStatus.COMPLETED)
        assertEquals(pausedDone, SeatClockReducer.settle(pausedDone, reading(110)).success().snapshot)
    }

    @Test fun completedGamesUnknownSeatsAndExhaustedTurnsAreRejected() {
        val done = clockGame(paused = true).copy(status = GameStatus.COMPLETED)
        SeatClockReducer.switchTurn(done, seatB, reading(110)).failure(FailureCode.INVALID_INPUT)
        SeatClockReducer.setPaused(done, false, reading(110)).failure(FailureCode.INVALID_INPUT)
        SeatClockReducer.switchTurn(clockGame(), seatC, reading(110)).failure(FailureCode.INVALID_INPUT)
        SeatClockReducer.switchTurn(clockGame().copy(turnNumber = Long.MAX_VALUE), seatB, reading(110)).failure(FailureCode.INVALID_INPUT)
        SeatClockReducer.reduce(clockGame(), GameAction.RenameSeat(seatA, "Renamed"), reading(110)).failure(FailureCode.INVALID_INPUT)
    }

    @Test fun readingsComeFromTheInjectedClockPort() {
        val clock = object : Clock {
            override fun utcNow(): UtcTimestamp = UtcTimestamp(77)
            override fun monotonicNow(): Duration = 12.seconds
        }
        assertEquals(ClockReading(12.seconds, UtcTimestamp(77), process1), ClockReading.from(clock, process1))
    }
}
