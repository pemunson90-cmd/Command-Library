package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.*
import org.junit.Test

class LifeTimingEngineTest {
    private val session = EntityId("session")
    private fun reading(seconds: Long, wall: Long = seconds) = GameTimeObservation(seconds.seconds, UtcTimestamp(wall), session)
    private fun game(zero: ClockZeroPolicy = ClockZeroPolicy.ALERT): GameSnapshot {
        val definitions = LifeFixtures.definitions().map { definition -> definition.copy(role = when (definition.id) {
            LifeFixtures.life -> CounterRole.LIFE
            LifeFixtures.commanderDamage -> CounterRole.COMMANDER_DAMAGE
            LifeFixtures.commanderTax -> CounterRole.COMMANDER_TAX
            else -> CounterRole.OTHER
        }) }
        val clock = SeatClockState(SeatClockMode.SHARED, SnapshotList.empty(), null,
            ClockAnchor(UtcTimestamp(100), 100.seconds, session), false, zero, ClockUndoPolicy.KEEP_ELAPSED,
            SnapshotList.empty(), shared = SharedCountdown(10.seconds,10.seconds))
        val base = LifeFixtures.snapshot(definitions=SnapshotList.copyOf(definitions),clock=clock,activeSeatId=LifeFixtures.seatA,turnNumber=1)
        return base.copy(settings=base.settings.copy(elapsedTimers=ElapsedTimerSettings(true,5.seconds,8.seconds)),
            elapsed=ElapsedTimerState(5.seconds,2.seconds,false,clock.anchor,Knowledge.Known(EntityId("turn-one"))))
    }
    private fun <T> ok(outcome: Outcome<T>): T {
        assertTrue("Expected success, got $outcome", outcome is Outcome.Success)
        return (outcome as Outcome.Success<T>).value
    }
    @Test fun sharedAndElapsedSettleTogetherAndWarningsDoNotRepeat() {
        val input=game()
        val settled=ok(LifeTimingEngine.settle(input,reading(104, -900)))
        assertEquals(6.seconds,settled.snapshot.clock.shared!!.remaining)
        assertEquals(9.seconds,settled.snapshot.elapsed.gameElapsed)
        assertEquals(6.seconds,settled.snapshot.elapsed.turnElapsed)
        assertEquals(setOf(GameAlertTarget.TurnTimer,GameAlertTarget.GameTimer),settled.alerts.map { it.target }.toSet())
        assertTrue(settled.alerts.all { it.firedAt==UtcTimestamp(-900) })
        val again=ok(LifeTimingEngine.settle(settled.snapshot,reading(104, 700)))
        assertTrue(again.alerts.isEmpty())
        assertEquals(settled.snapshot.alerts,again.snapshot.alerts)
        assertEquals(input.revision,again.snapshot.revision)
        assertEquals(input.lastEventId,again.snapshot.lastEventId)
    }
    @Test fun sharedZeroPoliciesProduceOneGameCandidateWithoutEliminatingSeats() {
        for(policy in ClockZeroPolicy.entries) {
            val input=game(policy)
            val settled=ok(LifeTimingEngine.settle(input,reading(112)))
            assertEquals(if(policy==ClockZeroPolicy.ALERT) (-2).seconds else Duration.ZERO,settled.snapshot.clock.shared!!.remaining)
            assertEquals(policy!=ClockZeroPolicy.ALERT,settled.snapshot.clock.paused)
            assertEquals(policy==ClockZeroPolicy.ELIMINATE,settled.sharedTimedOut)
            assertTrue(settled.timedOutSeats.isEmpty())
            assertEquals(input.seats,settled.snapshot.seats)
            assertEquals(input.result,settled.snapshot.result)
            assertEquals(1,settled.alerts.count { it.target==GameAlertTarget.SharedCountdown })
            assertTrue(ok(LifeTimingEngine.settle(settled.snapshot,reading(113))).alerts.isEmpty())
        }
    }
    @Test fun liveElapsedRequiresKnownLineageButPausedLegacyCanBeRead() {
        for(lineage in listOf<Knowledge<EntityId>>(Knowledge.Absent("No turn"),Knowledge.Unknown("Legacy",SnapshotList.empty()))) {
            val input=game().let { it.copy(elapsed=it.elapsed.copy(turnLineageId=lineage)) }
            val rejected=LifeTimingEngine.settle(input,reading(101))
            assertTrue(rejected is Outcome.Unavailable)
            assertEquals(UnavailableReason.UNSUPPORTED,(rejected as Outcome.Unavailable).reason)
            val paused=input.copy(elapsed=input.elapsed.copy(paused=true))
            assertEquals(paused.elapsed,ok(LifeTimingEngine.settle(paused,reading(101))).snapshot.elapsed)
        }
    }
    @Test fun unownedOpeningLineageChargesGameOnlyAndInvalidTimeDoesNotPartiallyCharge() {
        val input=game().copy(activeSeatId=null,turnNumber=0)
        val settled=ok(LifeTimingEngine.settle(input,reading(103)))
        assertEquals(8.seconds,settled.snapshot.elapsed.gameElapsed)
        assertEquals(input.elapsed.turnElapsed,settled.snapshot.elapsed.turnElapsed)
        assertTrue(LifeTimingEngine.settle(input,reading(99)) is Outcome.Failure)
        val other=reading(103).copy(processSessionId=EntityId("other-session"))
        assertTrue(LifeTimingEngine.settle(input,other) is Outcome.Failure)
        assertEquals(10.seconds,input.clock.shared!!.remaining)
    }
    @Test fun failedSecondLedgerCannotReturnPartiallySettledCountdown() {
        val input=game()
        val invalidElapsed=input.copy(elapsed=input.elapsed.copy(anchor=input.elapsed.anchor!!.copy(monotonicElapsed=110.seconds)))
        assertTrue(LifeTimingEngine.settle(invalidElapsed,reading(105)) is Outcome.Failure)
        assertEquals(10.seconds,invalidElapsed.clock.shared!!.remaining)
        assertTrue(invalidElapsed.alerts.isEmpty())
        val huge=input.copy(elapsed=input.elapsed.copy(gameElapsed=4_000_000_000_000_000L.seconds))
        assertTrue(LifeTimingEngine.settle(huge,reading(1_000_000_000_000_000L)) is Outcome.Failure)
        assertEquals(10.seconds,huge.clock.shared!!.remaining)
    }
    @Test fun simultaneousWarningsAndZeroRetainAcknowledgmentAndHistoricalLineageWarning() {
        val first=ok(LifeTimingEngine.settle(game(),reading(112)))
        assertEquals(3,first.alerts.size)
        val turn=first.alerts.single { it.target==GameAlertTarget.TurnTimer }
        val acknowledged=turn.copy(acknowledgedAt=UtcTimestamp(1))
        val restored=first.snapshot.copy(elapsed=first.snapshot.elapsed.copy(turnElapsed=Duration.ZERO),
            alerts=SnapshotList.copyOf(first.snapshot.alerts.map { if(it.id==turn.id) acknowledged else it }))
        val again=ok(LifeTimingEngine.settle(restored,reading(118)))
        assertTrue(again.alerts.isEmpty())
        assertEquals(acknowledged,again.snapshot.alerts.single { it.id==turn.id })
    }
    @Test fun hugeThresholdsHaveDistinctWarningIdsAndLongLineageStillFits() {
        val ids=ArrayList<EntityId>()
        for(seconds in listOf(17_280_000_000L,17_280_086_400L)) {
            val input=game().let { it.copy(settings=it.settings.copy(elapsedTimers=ElapsedTimerSettings(true,null,seconds.seconds)),
                elapsed=it.elapsed.copy(gameElapsed=(seconds-1).seconds,turnLineageId=Knowledge.Known(EntityId("界".repeat(21845))))) }
            val alert=ok(LifeTimingEngine.settle(input,reading(102))).alerts.single()
            assertTrue(alert.id.value.toByteArray(Charsets.UTF_8).size<=65536)
            ids+=alert.id
        }
        assertFalse(ids[0]==ids[1])
    }
    @Test fun switchAdvancesTurnAndResetsOnlyTurnElapsedWhilePassPreservesTurnAndBank() {
        val input=game().let { g -> g.copy(clock=g.clock.copy(mode=SeatClockMode.PER_TURN,shared=null,activeSeat=LifeFixtures.seatA,
            seats=SnapshotList.copyOf(g.seats.map { SeatTime(it.id,30.seconds,10.seconds) })),settings=g.settings.copy(clockMode=SeatClockMode.PER_TURN)) }
        val passed=ok(LifeTimingEngine.passClock(input,LifeFixtures.seatB,reading(102)))
        assertEquals(input.turnNumber,passed.snapshot.turnNumber)
        assertEquals(input.activeSeatId,passed.snapshot.activeSeatId)
        assertEquals(LifeFixtures.seatB,passed.snapshot.clock.activeSeat)
        assertEquals(10.seconds,passed.snapshot.clock.seats.single { it.seatId==LifeFixtures.seatB }.remaining)
        assertEquals(4.seconds,passed.snapshot.elapsed.turnElapsed)
        assertEquals(input.elapsed.turnLineageId,passed.snapshot.elapsed.turnLineageId)
        val turned=ok(LifeTimingEngine.switchTurn(passed.snapshot,LifeFixtures.seatB,EntityId("fresh-turn"),reading(104)))
        assertEquals(input.turnNumber+1,turned.snapshot.turnNumber)
        assertEquals(LifeFixtures.seatB,turned.snapshot.activeSeatId)
        assertEquals(30.seconds,turned.snapshot.clock.seats.single { it.seatId==LifeFixtures.seatB }.remaining)
        assertEquals(Duration.ZERO,turned.snapshot.elapsed.turnElapsed)
        assertEquals(9.seconds,turned.snapshot.elapsed.gameElapsed)
        assertEquals(Knowledge.Known(EntityId("fresh-turn")),turned.snapshot.elapsed.turnLineageId)
    }
    @Test fun sharedTurnSwitchHasNoSeatClockOwnerAndSameSeatStillGetsFreshLineage() {
        val input=game()
        val turned=ok(LifeTimingEngine.switchTurn(input,LifeFixtures.seatA,EntityId("next"),reading(102))).snapshot
        assertEquals(2L,turned.turnNumber)
        assertEquals(8.seconds,turned.clock.shared!!.remaining)
        assertNull(turned.clock.activeSeat)
        assertEquals(Duration.ZERO,turned.elapsed.turnElapsed)
        assertTrue(LifeTimingEngine.passClock(input,LifeFixtures.seatB,reading(102)) is Outcome.Failure)
        assertTrue(LifeTimingEngine.switchTurn(input,LifeFixtures.seatA,EntityId("turn-one"),reading(102)) is Outcome.Failure)
    }
    @Test fun pausingLedgersIsIndependentAndResumeNeverChargesPausedInterval() {
        val paused=ok(LifeTimingEngine.setElapsedPaused(game(),true,reading(102))).snapshot
        assertEquals(7.seconds,paused.elapsed.gameElapsed)
        assertFalse(paused.clock.paused)
        val resumed=ok(LifeTimingEngine.setElapsedPaused(paused,false,reading(104))).snapshot
        assertEquals(7.seconds,resumed.elapsed.gameElapsed)
        assertEquals(6.seconds,resumed.clock.shared!!.remaining)
        val countdownPaused=ok(LifeTimingEngine.setCountdownPaused(resumed,true,reading(105))).snapshot
        assertTrue(countdownPaused.clock.paused)
        assertFalse(countdownPaused.elapsed.paused)
        val countdownResumed=ok(LifeTimingEngine.setCountdownPaused(countdownPaused,false,reading(110))).snapshot
        assertEquals(5.seconds,countdownResumed.clock.shared!!.remaining)
        assertEquals(13.seconds,countdownResumed.elapsed.gameElapsed)
        assertEquals(4.seconds,ok(LifeTimingEngine.settle(countdownResumed,reading(111))).snapshot.clock.shared!!.remaining)
    }
    @Test fun recoveryPausesBothDurableLedgersWithoutWallGapAndCanPauseMissingLineage() {
        val input=game()
        val next=GameTimeObservation(Duration.ZERO,UtcTimestamp(9_000_000),EntityId("new-process"))
        val recovered=ok(LifeTimingEngine.recover(input,next))
        assertTrue(recovered.pausedByRecovery)
        assertEquals(input.clock.shared,recovered.snapshot.clock.shared)
        assertEquals(input.elapsed.gameElapsed,recovered.snapshot.elapsed.gameElapsed)
        assertTrue(recovered.snapshot.clock.paused)
        assertTrue(recovered.snapshot.elapsed.paused)
        assertFalse(ok(LifeTimingEngine.recover(recovered.snapshot,next)).changed)
        val legacy=input.copy(elapsed=input.elapsed.copy(turnLineageId=Knowledge.Unknown("Legacy",SnapshotList.empty())))
        val legacyPaused=ok(LifeTimingEngine.recover(legacy,next)).snapshot
        assertTrue(legacyPaused.elapsed.paused)
        assertEquals(legacy.elapsed.turnLineageId,legacyPaused.elapsed.turnLineageId)
        assertTrue(LifeTimingEngine.setElapsedPaused(legacyPaused,false,next) is Outcome.Unavailable)
    }
    private fun provenance(before: GameSnapshot, after: GameSnapshot, at: GameTimeObservation): GameActionProvenance =
        GameActionProvenance(77,before,after.copy(revision=Revision(before.revision.value+1),lastEventId=Knowledge.Known(77L)),at)

    private fun perTurnGame(): GameSnapshot = game().let { g -> g.copy(
        clock=g.clock.copy(mode=SeatClockMode.PER_TURN,shared=null,activeSeat=LifeFixtures.seatA,
            seats=SnapshotList.copyOf(g.seats.map { SeatTime(it.id,30.seconds,10.seconds) })),settings=g.settings.copy(clockMode=SeatClockMode.PER_TURN)) }

    @Test fun undoSameSeatTurnAfterCheckpointKeepsGameElapsedAndAuditsUndoneTurnCharge() {
        val before=perTurnGame()
        val action=GameAction.SwitchTurn(LifeFixtures.seatA)
        val after=ok(LifeTimingEngine.switchTurn(before,LifeFixtures.seatA,EntityId("next"),reading(102))).snapshot
        val original=provenance(before,after,reading(102))
        val current=ok(LifeTimingEngine.settle(original.afterAction,reading(107))).snapshot
        val undone=ok(LifeTimingEngine.undo(original,action,current,reading(108)))
        assertEquals(6.seconds,undone.settledCurrent.elapsed.turnElapsed)
        assertEquals(13.seconds,undone.settledCurrent.elapsed.gameElapsed)
        val restored=ok(undone.compose(current)).snapshot
        assertEquals(13.seconds,restored.elapsed.gameElapsed)
        assertEquals(4.seconds,restored.elapsed.turnElapsed)
        assertEquals(before.elapsed.turnLineageId,restored.elapsed.turnLineageId)
        assertEquals(before.turnNumber,restored.turnNumber)
        assertEquals(2.seconds,restored.clock.seats.single { it.seatId==LifeFixtures.seatA }.remaining)
        assertEquals(current.revision,restored.revision)
        assertEquals(current.lastEventId,restored.lastEventId)
        assertTrue(restored.alerts.containsAll(current.alerts))
        val next=ok(LifeTimingEngine.settle(restored,reading(109)))
        assertEquals(1,next.alerts.count { it.target==GameAlertTarget.TurnTimer })
    }
    @Test fun sharedPauseUndoHonorsBothCountdownPoliciesAndKeepsCurrentElapsed() {
        for(policy in ClockUndoPolicy.entries) {
            val before=game().let { it.copy(clock=it.clock.copy(undoPolicy=policy)) }
            val at=reading(102)
            val after=ok(LifeTimingEngine.setCountdownPaused(before,true,at)).snapshot
            val original=provenance(before,after,at)
            val current=ok(LifeTimingEngine.settle(original.afterAction,reading(105))).snapshot
            val undone=ok(LifeTimingEngine.undo(original,GameAction.ClockPause(true),current,reading(106)))
            assertEquals(11.seconds,ok(undone.compose(current)).snapshot.elapsed.gameElapsed)
            assertEquals(8.seconds,ok(undone.compose(current)).snapshot.elapsed.turnElapsed)
            assertEquals(if(policy==ClockUndoPolicy.KEEP_ELAPSED) 8.seconds else 4.seconds,ok(undone.compose(current)).snapshot.clock.shared!!.remaining)
            assertFalse(ok(undone.compose(current)).snapshot.clock.paused)
        }
    }
    @Test fun nonTurnUndoDoesNotRefundTurnElapsedOrOverwriteCurrentNames() {
        val before=game()
        val after=ok(LifeTimingEngine.setElapsedPaused(before,true,reading(102))).snapshot
        val original=provenance(before,after,reading(102))
        val current=original.afterAction.copy(seats=SnapshotList.copyOf(original.afterAction.seats.map { it.copy(playerName="Current ${it.order}") }))
        val restored=ok(LifeTimingEngine.undo(original,GameAction.SetElapsedPaused(true),current,reading(108))).let { ok(it.compose(current)).snapshot }
        assertEquals(7.seconds,restored.elapsed.gameElapsed)
        assertEquals(4.seconds,restored.elapsed.turnElapsed)
        assertFalse(restored.elapsed.paused)
        assertEquals(current.seats,restored.seats)
        assertEquals(8.seconds,ok(LifeTimingEngine.settle(restored,reading(109))).snapshot.elapsed.gameElapsed)
    }
    @Test fun undoAfterRestartKeepsDurableChargeAndRecoveryPause() {
        val before=perTurnGame()
        val after=ok(LifeTimingEngine.switchTurn(before,LifeFixtures.seatA,EntityId("next"),reading(102))).snapshot
        val original=provenance(before,after,reading(102))
        val checkpoint=ok(LifeTimingEngine.settle(original.afterAction,reading(107))).snapshot
        val restarted=GameTimeObservation(Duration.ZERO,UtcTimestamp(9_000_000),EntityId("new-process"))
        val current=ok(LifeTimingEngine.recover(checkpoint,restarted)).snapshot
        val undone=ok(LifeTimingEngine.undo(original,GameAction.SwitchTurn(LifeFixtures.seatA),current,restarted.copy(monotonic=5.seconds))).let { ok(it.compose(current)).snapshot }
        assertTrue(undone.clock.paused)
        assertTrue(undone.elapsed.paused)
        assertEquals(12.seconds,undone.elapsed.gameElapsed)
        assertEquals(4.seconds,undone.elapsed.turnElapsed)
        assertEquals(3.seconds,undone.clock.seats.single { it.seatId==LifeFixtures.seatA }.remaining)
    }
    @Test fun undoRejectsAmbiguousGroupsAndMismatchedTurnTimeline() {
        val before=game()
        val after=ok(LifeTimingEngine.switchTurn(before,LifeFixtures.seatA,EntityId("next"),reading(102))).snapshot
        val original=provenance(before,after,reading(102))
        val multi=GameAction.Group(SnapshotList.of(GameAction.SwitchTurn(LifeFixtures.seatA),GameAction.SwitchTurn(LifeFixtures.seatB)),EntityId("group"))
        assertTrue(LifeTimingEngine.undo(original,multi,original.afterAction,reading(103)) is Outcome.Unavailable)
        assertTrue(LifeTimingEngine.undo(original,GameAction.PassClock(LifeFixtures.seatA),original.afterAction,reading(103)) is Outcome.Failure)
        assertTrue(LifeTimingEngine.undo(original,GameAction.SwitchTurn(LifeFixtures.seatA),original.afterAction.copy(turnNumber=9),reading(103)) is Outcome.Failure)
    }
    @Test fun groupedTurnThenPriorityUndoReversesActualTurnBankAndRetainsPriorityCharge() {
        val before=perTurnGame()
        val at=reading(102)
        val switched=ok(LifeTimingEngine.switchTurn(before,LifeFixtures.seatB,EntityId("next"),at)).snapshot
        val passed=ok(LifeTimingEngine.passClock(switched,LifeFixtures.seatC,at)).snapshot
        val original=provenance(before,passed,at)
        val action=GameAction.Group(SnapshotList.of(GameAction.SwitchTurn(LifeFixtures.seatB),GameAction.PassClock(LifeFixtures.seatC)),EntityId("group"))
        val current=ok(LifeTimingEngine.settle(original.afterAction,reading(105))).snapshot
        val undone=ok(LifeTimingEngine.undo(original,action,current,reading(106)))
        val restored=ok(undone.compose(current)).snapshot
        assertEquals(LifeFixtures.seatA,restored.clock.activeSeat)
        assertEquals(8.seconds,restored.clock.seats.single { it.seatId==LifeFixtures.seatA }.remaining)
        assertEquals(10.seconds,restored.clock.seats.single { it.seatId==LifeFixtures.seatB }.remaining)
        assertEquals(6.seconds,restored.clock.seats.single { it.seatId==LifeFixtures.seatC }.remaining)
        assertEquals(4.seconds,restored.elapsed.turnElapsed)
        assertEquals(11.seconds,restored.elapsed.gameElapsed)
    }
    @Test fun undoFinishRequiresSemanticRestorationBeforePatchCanBecomeSnapshot() {
        val before=game()
        val at=reading(102)
        val settled=ok(LifeTimingEngine.settle(before,at)).snapshot
        val result=GameResult(EntityId("result"),before.id,SnapshotList.of(LifeFixtures.seatA),at.wall,"Synthetic finish",Revision(8),false)
        val finished=settled.copy(status=GameStatus.COMPLETED,clock=settled.clock.copy(paused=true),elapsed=settled.elapsed.copy(paused=true),
            result=Knowledge.Known(result),endedAt=Knowledge.Known(at.wall))
        val original=provenance(before,finished,at)
        val current=original.afterAction
        val undone=ok(LifeTimingEngine.undo(original,GameAction.Finish(SnapshotList.of(LifeFixtures.seatA),"Synthetic finish"),current,reading(106)))
        assertTrue(undone.compose(current) is Outcome.Failure)
        val semantic=current.copy(status=GameStatus.ACTIVE,revision=Revision(9),lastEventId=Knowledge.Known(78L),
            result=Knowledge.Known(result.copy(retracted=true,revision=Revision(9))),endedAt=Knowledge.Absent("Reopened"))
        val composed=ok(undone.compose(semantic)).snapshot
        assertEquals(semantic.result,composed.result)
        assertEquals(semantic.revision,composed.revision)
        assertEquals(semantic.lastEventId,composed.lastEventId)
        assertFalse(composed.elapsed.paused)
        assertFalse(composed.clock.paused)
        assertTrue(undone.compose(semantic.copy(id=GameId(semantic.id.source,"wrong"))) is Outcome.Failure)
    }
    @Test fun undoToAbsentLegacyLineageKeepsElapsedPausedAndDoesNotInventIdentity() {
        val before=game().let { it.copy(elapsed=it.elapsed.copy(paused=true,turnLineageId=Knowledge.Absent("Historical absence"))) }
        val at=reading(102)
        val after=ok(LifeTimingEngine.switchTurn(before,LifeFixtures.seatA,EntityId("next"),at)).snapshot
        val original=provenance(before,after,at)
        val restored=ok(ok(LifeTimingEngine.undo(original,GameAction.SwitchTurn(LifeFixtures.seatA),original.afterAction,reading(103))).compose(original.afterAction)).snapshot
        assertTrue(restored.elapsed.paused)
        assertEquals(before.elapsed.turnLineageId,restored.elapsed.turnLineageId)
        assertTrue(LifeTimingEngine.setElapsedPaused(restored,false,reading(104)) is Outcome.Unavailable)
    }
    @Test fun sharedRestoreTimeUndoZeroFiresOnceAndNeverAutomaticallyEliminates() {
        for(policy in ClockZeroPolicy.entries) {
            val before=game(policy).let { it.copy(clock=it.clock.copy(undoPolicy=ClockUndoPolicy.RESTORE_TIME)) }
            val at=reading(102)
            val after=ok(LifeTimingEngine.setCountdownPaused(before,true,at)).snapshot
            val original=provenance(before,after,at)
            val current=ok(LifeTimingEngine.settle(original.afterAction,reading(115))).snapshot
            val undone=ok(LifeTimingEngine.undo(original,GameAction.ClockPause(true),current,reading(116)))
            val composed=ok(undone.compose(current))
            assertEquals(1,composed.alerts.count { it.target==GameAlertTarget.SharedCountdown })
            assertEquals(policy==ClockZeroPolicy.ELIMINATE,composed.sharedTimedOut)
            assertEquals(before.seats,composed.snapshot.seats)
            assertTrue(ok(LifeTimingEngine.settle(composed.snapshot,reading(117))).alerts.isEmpty())
        }
    }
    @Test fun sameSeatTurnThenPriorityUndoKeepsOutgoingChargeWithoutRefundingPriority() {
        val before=perTurnGame()
        val at=reading(102)
        val switched=ok(LifeTimingEngine.switchTurn(before,LifeFixtures.seatA,EntityId("next"),at)).snapshot
        val passed=ok(LifeTimingEngine.passClock(switched,LifeFixtures.seatC,at)).snapshot
        val original=provenance(before,passed,at)
        val action=GameAction.Group(SnapshotList.of(GameAction.SwitchTurn(LifeFixtures.seatA),GameAction.PassClock(LifeFixtures.seatC)),EntityId("group"))
        val current=ok(LifeTimingEngine.settle(original.afterAction,reading(105))).snapshot
        val restored=ok(ok(LifeTimingEngine.undo(original,action,current,reading(106))).compose(current)).snapshot
        assertEquals(8.seconds,restored.clock.seats.single { it.seatId==LifeFixtures.seatA }.remaining)
        assertEquals(6.seconds,restored.clock.seats.single { it.seatId==LifeFixtures.seatC }.remaining)
    }
    @Test fun sharedRestoreTimeReplayRetainsAcknowledgedZeroWithoutFiringAgain() {
        val before=game().let { it.copy(clock=it.clock.copy(undoPolicy=ClockUndoPolicy.RESTORE_TIME)) }
        val at=reading(102)
        val after=ok(LifeTimingEngine.switchTurn(before,LifeFixtures.seatA,EntityId("next"),at)).snapshot
        val original=provenance(before,after,at)
        val charged=ok(LifeTimingEngine.settle(original.afterAction,reading(112))).snapshot
        val zero=charged.alerts.single { it.target==GameAlertTarget.SharedCountdown }.copy(acknowledgedAt=UtcTimestamp(1))
        val current=charged.copy(alerts=SnapshotList.copyOf(charged.alerts.map { if(it.id==zero.id) zero else it }))
        val undone=ok(LifeTimingEngine.undo(original,GameAction.SwitchTurn(LifeFixtures.seatA),current,reading(114)))
        val restored=ok(undone.compose(current)).snapshot
        assertFalse(undone.alerts.any { it.target==GameAlertTarget.SharedCountdown })
        assertEquals(zero,restored.alerts.single { it.id==zero.id })
        assertEquals(current.clock.firedAlertIds,restored.clock.firedAlertIds)
    }
}
