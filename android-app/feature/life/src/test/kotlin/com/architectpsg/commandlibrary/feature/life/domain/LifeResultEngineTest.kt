package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.*
import org.junit.Test

class LifeResultEngineTest {
    private val session=EntityId("process")
    private val resultId=EntityId("result")
    private fun reading(seconds: Long=102, wall: Long=seconds)=GameTimeObservation(seconds.seconds,UtcTimestamp(wall),session)
    private fun game(): GameSnapshot {
        val definitions=LifeFixtures.definitions().map { d -> d.copy(role=when(d.id) {
            LifeFixtures.life -> CounterRole.LIFE
            LifeFixtures.commanderDamage -> CounterRole.COMMANDER_DAMAGE
            LifeFixtures.commanderTax -> CounterRole.COMMANDER_TAX
            else -> CounterRole.OTHER
        }) }
        val anchor=ClockAnchor(UtcTimestamp(100),100.seconds,session)
        val clock=SeatClockState(SeatClockMode.CHESS,SnapshotList.copyOf(LifeFixtures.seats().map { SeatTime(it.id,10.seconds,10.seconds) }),LifeFixtures.seatB,anchor,false,
            ClockZeroPolicy.ALERT,ClockUndoPolicy.KEEP_ELAPSED,SnapshotList.empty())
        return LifeFixtures.snapshot(definitions=SnapshotList.copyOf(definitions),clock=clock,activeSeatId=LifeFixtures.seatA,turnNumber=1).copy(
            elapsed=ElapsedTimerState(5.seconds,2.seconds,false,anchor,Knowledge.Known(EntityId("turn"))))
    }
    private fun outcomes()=SnapshotList.of(SeatResult(LifeFixtures.seatB,SeatResultKind.LOSS,SeatExitKind.CONCEDED),
        SeatResult(LifeFixtures.seatA,SeatResultKind.WIN,null),SeatResult(LifeFixtures.seatC,SeatResultKind.LOSS,null))
    private fun allocation(snapshot: GameSnapshot,event: Long=77,fresh: EntityId?=resultId)=ResultEventAllocation(event,Revision(snapshot.revision.value+1),fresh)
    private fun <T> ok(outcome: Outcome<T>): T {assertTrue("Expected success, got $outcome",outcome is Outcome.Success);return(outcome as Outcome.Success<T>).value}
    private fun invalid(outcome: Outcome<*>) {assertTrue("Expected invalid input, got $outcome",outcome is Outcome.Failure);assertEquals(FailureCode.INVALID_INPUT,(outcome as Outcome.Failure).error.code)}
    private fun result(snapshot: GameSnapshot)=(snapshot.result as Knowledge.Known).value
    private fun finish(snapshot: GameSnapshot=game())=ok(LifeResultEngine.reduce(snapshot,GameAction.FinishWithOutcomes(outcomes(),"Explicit confirmation"),reading(),allocation(snapshot)))

    @Test fun finishSettlesThenReturnsCoherentSingleAllocationProposalWithoutChangingSeats() {
        val input=game()
        val at=reading(102,3)
        val action=GameAction.FinishWithOutcomes(outcomes(),"  原文 reason  ")
        val reduced=ok(LifeResultEngine.reduce(input,action,at,allocation(input)))
        val next=reduced.snapshot
        assertEquals(input.revision,reduced.settledBefore.revision)
        assertEquals(input.lastEventId,reduced.settledBefore.lastEventId)
        assertEquals(Revision(8),next.revision);assertEquals(Knowledge.Known(77L),next.lastEventId)
        assertEquals(next.revision,result(next).revision)
        assertEquals(outcomes(),result(next).seatResults);assertEquals(SnapshotList.of(LifeFixtures.seatA),result(next).winners)
        assertEquals("  原文 reason  ",result(next).reason)
        assertEquals(UtcTimestamp(3),result(next).finishedAt);assertEquals(Knowledge.Known(UtcTimestamp(3)),next.endedAt)
        assertEquals(8.seconds,next.clock.seats.single { it.seatId==LifeFixtures.seatB }.remaining)
        assertEquals(7.seconds,next.elapsed.gameElapsed);assertEquals(4.seconds,next.elapsed.turnElapsed)
        assertFalse(reduced.settledBefore.clock.paused);assertTrue(next.clock.paused);assertTrue(next.elapsed.paused)
        assertEquals(input.seats,next.seats);assertEquals(GameStatus.COMPLETED,next.status)
        assertTrue(SnapshotGraphValidator.validate(next) is Outcome.Success)
        GameActionProvenance(77,reduced.settledBefore,next,at)
    }
    @Test fun legacyFinishMapsEmptyToDrawAndMultipleWinnersWithoutInventingExits() {
        val input=game()
        for(winners in listOf(SnapshotList.empty(),SnapshotList.of(LifeFixtures.seatC,LifeFixtures.seatA))) {
            val reduced=ok(LifeResultEngine.reduce(input,GameAction.Finish(winners,"Legacy command"),reading(),allocation(input)))
            val rows=result(reduced.snapshot).seatResults
            assertEquals(3,rows.size);assertTrue(rows.all { it.exit==null })
            for(row in rows) assertEquals(if(winners.isEmpty()) SeatResultKind.DRAW else if(row.seatId in winners) SeatResultKind.WIN else SeatResultKind.LOSS,row.result)
        }
    }
    @Test fun reopenRetractsExactFactsAndRefinishReusesIdentityWithoutChargingCompletedGap() {
        val finished=finish().snapshot
        val original=result(finished)
        val reopened=ok(LifeResultEngine.reduce(finished,GameAction.Reopen("Correction"),reading(1_000_000),allocation(finished,78,null))).snapshot
        assertEquals(original.copy(revision=Revision(9),retracted=true),result(reopened))
        assertEquals(GameStatus.PAUSED,reopened.status);assertTrue(reopened.endedAt is Knowledge.Absent)
        assertEquals(finished.elapsed.gameElapsed,reopened.elapsed.gameElapsed)
        assertEquals(finished.clock.seats,reopened.clock.seats);assertTrue(reopened.clock.paused);assertTrue(reopened.elapsed.paused)
        val again=ok(LifeResultEngine.reduce(reopened,GameAction.Finish(SnapshotList.empty(),"New draw"),reading(2_000_000),allocation(reopened,79,null))).snapshot
        assertEquals(resultId,result(again).id);assertFalse(result(again).retracted)
        assertEquals(Revision(10),result(again).revision)
        assertEquals(finished.elapsed.gameElapsed,again.elapsed.gameElapsed)
        assertTrue(result(again).seatResults.all { it.result==SeatResultKind.DRAW })
    }
    @Test fun historicalWinnerOnlyResultReopensWithoutFabricatedOutcomesOrReasonRewrite() {
        val input=game()
        val legacy=GameResult(resultId,input.id,SnapshotList.of(LifeFixtures.seatA),UtcTimestamp(50),"",input.revision,false)
        val completed=input.copy(status=GameStatus.COMPLETED,result=Knowledge.Known(legacy),endedAt=Knowledge.Known(legacy.finishedAt),clock=input.clock.copy(paused=true),elapsed=input.elapsed.copy(paused=true))
        val reopened=ok(LifeResultEngine.reduce(completed,GameAction.Reopen("Explicit repair later"),reading(110),allocation(completed,78,null))).snapshot
        assertEquals(legacy.copy(revision=Revision(8),retracted=true),result(reopened))
        assertTrue(result(reopened).seatResults.isEmpty());assertEquals("",result(reopened).reason)
    }
    @Test fun invalidAllocationAndRevisionExhaustionNeverProduceProposedState() {
        val input=game();val action=GameAction.Finish(SnapshotList.empty(),"Draw")
        invalid(LifeResultEngine.reduce(input,action,reading(),ResultEventAllocation(-1,Revision(8),resultId)))
        invalid(LifeResultEngine.reduce(input,action,reading(),ResultEventAllocation(77,Revision(9),resultId)))
        invalid(LifeResultEngine.reduce(input.copy(revision=Revision(Long.MAX_VALUE)),action,reading(),ResultEventAllocation(77,Revision(0),resultId)))
        assertTrue(input.result is Knowledge.Absent)
    }
    @Test fun firstIdentityAndUnknownKnowledgeNeedExplicitAuthorityRatherThanInventedResult() {
        val input=game();val action=GameAction.Finish(SnapshotList.empty(),"Draw")
        invalid(LifeResultEngine.reduce(input,action,reading(),allocation(input,fresh=null)))
        val unknown=input.copy(result=Knowledge.Unknown("Legacy result incomplete",SnapshotList.empty()))
        val rejected=LifeResultEngine.reduce(unknown,action,reading(),allocation(unknown))
        assertTrue(rejected is Outcome.Unavailable);assertEquals(UnavailableReason.UNSUPPORTED,(rejected as Outcome.Unavailable).reason)
        val missing=input.copy(status=GameStatus.COMPLETED,clock=input.clock.copy(paused=true),elapsed=input.elapsed.copy(paused=true))
        val missingReopen=LifeResultEngine.reduce(missing,GameAction.Reopen("Cannot invent historical result"),reading(),allocation(missing,fresh=null))
        assertTrue(missingReopen is Outcome.Unavailable);assertEquals(UnavailableReason.UNSUPPORTED,(missingReopen as Outcome.Unavailable).reason)
        val completed=finish().snapshot
        val reopened=ok(LifeResultEngine.reduce(completed,GameAction.Reopen("Correction"),reading(103),allocation(completed,78,null))).snapshot
        invalid(LifeResultEngine.reduce(reopened,action,reading(104),allocation(reopened,79,EntityId("replacement"))))
    }
    @Test fun rosterWinnerReasonAndCompletedGuardsRejectInvalidNewRequests() {
        val input=game()
        invalid(LifeResultEngine.reduce(input,GameAction.FinishWithOutcomes(SnapshotList.of(outcomes()[0]),"Incomplete"),reading(),allocation(input)))
        val foreign=SnapshotList.of(outcomes()[0],outcomes()[1],SeatResult(EntityId("foreign"),SeatResultKind.LOSS,null))
        invalid(LifeResultEngine.reduce(input,GameAction.FinishWithOutcomes(foreign,"Foreign"),reading(),allocation(input)))
        invalid(LifeResultEngine.reduce(input,GameAction.Finish(SnapshotList.of(LifeFixtures.seatA,LifeFixtures.seatA),"Duplicate"),reading(),allocation(input)))
        invalid(LifeResultEngine.reduce(input,GameAction.Finish(SnapshotList.empty()," "),reading(),allocation(input)))
        invalid(LifeResultEngine.reduce(input,GameAction.Reopen("Not completed"),reading(),allocation(input)))
        val finished=finish().snapshot
        invalid(LifeResultEngine.reduce(finished,GameAction.Reopen(""),reading(103),allocation(finished,78,null)))
        invalid(LifeResultEngine.reduce(finished,GameAction.Finish(SnapshotList.empty(),"Again"),reading(103),allocation(finished,78,null)))
    }
    @Test fun explicitAllLossStaysExplicitAndDoesNotEliminateSeatsAutomatically() {
        val input=game()
        val all=SnapshotList.copyOf(input.seats.map { SeatResult(it.id,SeatResultKind.LOSS,null) })
        val finished=ok(LifeResultEngine.reduce(input,GameAction.FinishWithOutcomes(all,"Explicit all loss"),reading(),allocation(input))).snapshot
        assertTrue(result(finished).winners.isEmpty());assertEquals(all,result(finished).seatResults)
        assertEquals(input.seats,finished.seats)
    }
    @Test fun finishPreservesAcknowledgmentAndIncludesNewlySettledZeroOccurrence() {
        val input=game().let { it.copy(clock=it.clock.copy(seats=SnapshotList.copyOf(it.clock.seats.map { bank -> if(bank.seatId==LifeFixtures.seatB) bank.copy(remaining=1.seconds) else bank })),
            alerts=SnapshotList.of(GameAlert(EntityId("old"),it.id,GameAlertTarget.GameTimer,UtcTimestamp(200),UtcTimestamp(190)))) }
        val reduced=finish(input)
        assertEquals(input.alerts.single(),reduced.snapshot.alerts.single { it.id==EntityId("old") })
        assertEquals(1,reduced.alerts.size);assertEquals(GameAlertTarget.SeatClock(LifeFixtures.seatB),reduced.alerts.single().target)
        assertTrue(reduced.snapshot.clock.firedAlertIds.contains(reduced.alerts.single().id))
        assertTrue(reduced.snapshot.clock.paused)
    }
}
