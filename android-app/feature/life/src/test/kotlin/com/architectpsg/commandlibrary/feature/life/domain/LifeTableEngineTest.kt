package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.*
import org.junit.Test

class LifeTableEngineTest {
    private val session=EntityId("process")
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
            ClockZeroPolicy.ALERT,ClockUndoPolicy.KEEP_ELAPSED,SnapshotList.of(EntityId("retained-fired")))
        return LifeFixtures.snapshot(definitions=SnapshotList.copyOf(definitions),clock=clock,activeSeatId=LifeFixtures.seatA,turnNumber=1).copy(
            elapsed=ElapsedTimerState(5.seconds,2.seconds,false,anchor,Knowledge.Known(EntityId("turn"))))
    }
    private fun <T> ok(outcome: Outcome<T>): T {assertTrue("Expected success, got $outcome",outcome is Outcome.Success);return(outcome as Outcome.Success<T>).value}
    private fun invalid(outcome: Outcome<*>) {assertTrue("Expected invalid input, got $outcome",outcome is Outcome.Failure);assertEquals(FailureCode.INVALID_INPUT,(outcome as Outcome.Failure).error.code)}
    private fun eliminate(seat: EntityId)=GameAction.EliminateSeat(seat,SeatExitKind.CONCEDED,"Explicit concession",null)
    private fun GameSnapshot.seat(id: EntityId)=seats.single { it.id==id }

    @Test fun eliminatingClockOwnerSettlesBeforePauseAndRestorationDoesNotResume() {
        val input=game().copy(counters=SnapshotList.of(CounterState(LifeFixtures.life,LifeFixtures.seatB,null,17,true),CounterState(LifeFixtures.commanderDamage,LifeFixtures.seatB,LifeFixtures.slotA,4,false)))
        val reduced=ok(LifeTableEngine.reduce(input,eliminate(LifeFixtures.seatB),reading()))
        assertEquals(8.seconds,reduced.settledBefore.clock.seats.single { it.seatId==LifeFixtures.seatB }.remaining)
        assertTrue(reduced.snapshot.clock.paused)
        assertFalse(reduced.snapshot.elapsed.paused)
        assertEquals(7.seconds,reduced.snapshot.elapsed.gameElapsed)
        val exit=reduced.snapshot.seat(LifeFixtures.seatB).participation as SeatParticipation.Eliminated
        assertEquals(SeatExitKind.CONCEDED,exit.kind);assertEquals(UtcTimestamp(102),exit.at)
        assertEquals(input.counters,reduced.snapshot.counters)
        assertEquals(input.result,reduced.snapshot.result)
        val restored=ok(LifeTableEngine.reduce(reduced.snapshot,GameAction.RestoreSeat(LifeFixtures.seatB,"Correction"),reading(105))).snapshot
        assertTrue(restored.clock.paused)
        assertEquals(8.seconds,restored.clock.seats.single { it.seatId==LifeFixtures.seatB }.remaining)
        assertEquals(input.seat(LifeFixtures.seatB),restored.seat(LifeFixtures.seatB))
        assertEquals(input.counters,restored.counters)
        assertEquals(10.seconds,restored.elapsed.gameElapsed)
    }
    @Test fun eliminatingTurnOwnerPausesElapsedButDoesNotChargeSharedCountdownTwiceOrFinish() {
        val input=game().let { it.copy(clock=it.clock.copy(mode=SeatClockMode.SHARED,seats=SnapshotList.empty(),activeSeat=null,shared=SharedCountdown(10.seconds,10.seconds)),settings=it.settings.copy(clockMode=SeatClockMode.SHARED)) }
        val reduced=ok(LifeTableEngine.reduce(input,eliminate(LifeFixtures.seatA),reading())).snapshot
        assertEquals(8.seconds,reduced.clock.shared!!.remaining)
        assertFalse(reduced.clock.paused)
        assertTrue(reduced.elapsed.paused)
        assertEquals(input.activeSeatId,reduced.activeSeatId)
        assertEquals(input.turnNumber,reduced.turnNumber)
        assertEquals(input.result,reduced.result)
        assertEquals(GameStatus.ACTIVE,reduced.status)
    }
    @Test fun designationChangesRemainIndependentAndClearingBlessingAllowsEliminatedSeat() {
        val initial=game().copy(designations=GameDesignations(monarch=LifeFixtures.seatB,blessedSeats=SnapshotList.of(LifeFixtures.seatB)))
        val eliminated=ok(LifeTableEngine.reduce(initial,eliminate(LifeFixtures.seatB),reading())).snapshot
        assertEquals(initial.designations,eliminated.designations)
        invalid(LifeTableEngine.reduce(eliminated,GameAction.SetMonarch(LifeFixtures.seatB),reading(103)))
        invalid(LifeTableEngine.reduce(eliminated,GameAction.SetBlessing(LifeFixtures.seatB,true),reading(103)))
        val group=GameAction.Group(SnapshotList.of(GameAction.SetMonarch(null),GameAction.SetBlessing(LifeFixtures.seatB,false),
            GameAction.SetInitiative(LifeFixtures.seatC,"原文 note"),GameAction.SetDayNight(DayNightState.NIGHT),GameAction.SetBlessing(LifeFixtures.seatA,true)),EntityId("group"))
        val changed=ok(LifeTableEngine.reduce(eliminated,group,reading(103))).snapshot.designations
        assertNull(changed.monarch);assertEquals(LifeFixtures.seatC,changed.initiative)
        assertEquals("原文 note",changed.initiativeNote);assertEquals(DayNightState.NIGHT,changed.dayNight)
        assertEquals(SnapshotList.of(LifeFixtures.seatA),changed.blessedSeats)
    }
    @Test fun counterEvidenceAndDismissalBindExactObservationWithoutPermanentSuppression() {
        val input=game().copy(counters=SnapshotList.of(CounterState(LifeFixtures.life,LifeFixtures.seatA,null,0,false)))
        val evidence=LossEvidence(LifeFixtures.life,null,0,CounterLossPolicy.AtOrBelow(0),Knowledge.Unknown("No source version supplied",SnapshotList.empty()),"Observed synthetic life")
        val dismissal=GameAction.DismissLossCandidate(evidence,LifeFixtures.seatA,"Prevention effect")
        val dismissed=ok(LifeTableEngine.reduce(input,dismissal,reading())).snapshot
        assertEquals(input.seats,dismissed.seats);assertEquals(input.counters,dismissed.counters)
        invalid(LifeTableEngine.reduce(input,dismissal.copy(candidate=evidence.copy(observedValue=1)),reading()))
        val eliminated=ok(LifeTableEngine.reduce(input,GameAction.EliminateSeat(LifeFixtures.seatA,SeatExitKind.CONFIRMED_LOSS,"Confirmed",evidence),reading())).snapshot
        assertEquals(evidence,(eliminated.seat(LifeFixtures.seatA).participation as SeatParticipation.Eliminated).evidence)
        val above=ok(LifeCounterEngine.reduce(dismissed,GameAction.AdjustCounter(LifeFixtures.seatA,LifeFixtures.life,null,2))).snapshot
        assertEquals(1,ok(LifeCounterEngine.reduce(above,GameAction.AdjustCounter(LifeFixtures.seatA,LifeFixtures.life,null,-2))).newlyReached.size)
    }
    @Test fun manualEliminationPreservesDeclaredUnknownEvidenceAndNeverGuessesResult() {
        val input=game()
        val evidence=LossEvidence(null,null,null,null,Knowledge.Unknown("Manual condition",SnapshotList.empty()),"External condition confirmed")
        val reduced=ok(LifeTableEngine.reduce(input,GameAction.EliminateSeat(LifeFixtures.seatC,SeatExitKind.MANUAL,"Manual confirmation",evidence),reading())).snapshot
        assertEquals(evidence,(reduced.seat(LifeFixtures.seatC).participation as SeatParticipation.Eliminated).evidence)
        assertEquals(input.result,reduced.result)
        assertEquals(input.counters,reduced.counters)
        invalid(LifeTableEngine.reduce(reduced,eliminate(LifeFixtures.seatC),reading(103)))
        invalid(LifeTableEngine.reduce(input,GameAction.RestoreSeat(LifeFixtures.seatC,"Already active"),reading()))
    }
    @Test fun acknowledgmentsAreIdempotentAcrossWallReversalAndPreserveFiredHistory() {
        val input=game().let { it.copy(alerts=SnapshotList.of(GameAlert(EntityId("alert"),it.id,GameAlertTarget.GameTimer,UtcTimestamp(200),null))) }
        val action=GameAction.AcknowledgeGameAlert(EntityId("alert"))
        val acknowledged=ok(LifeTableEngine.reduce(input,action,reading(102,5))).snapshot
        assertEquals(UtcTimestamp(5),acknowledged.alerts.single().acknowledgedAt)
        val retry=ok(LifeTableEngine.reduce(acknowledged,action,reading(103,9))).snapshot
        assertEquals(acknowledged.alerts,retry.alerts)
        assertEquals(input.clock.firedAlertIds,retry.clock.firedAlertIds)
        val missing=LifeTableEngine.reduce(input,GameAction.AcknowledgeGameAlert(EntityId("missing")),reading())
        assertTrue(missing is Outcome.Unavailable);assertEquals(UnavailableReason.NOT_FOUND,(missing as Outcome.Unavailable).reason)
    }
    @Test fun completedGamesAcceptAcknowledgmentOnlyGroupsAndRejectMixedEditsAtomically() {
        val initial=game().let { it.copy(clock=it.clock.copy(paused=true),elapsed=it.elapsed.copy(paused=true),status=GameStatus.COMPLETED,
            alerts=SnapshotList.of(GameAlert(EntityId("alert"),it.id,GameAlertTarget.GameTimer,UtcTimestamp(200),null))) }
        val ack=GameAction.AcknowledgeGameAlert(EntityId("alert"))
        val group=GameAction.Group(SnapshotList.of(ack,ack),EntityId("group"))
        val acknowledged=ok(LifeTableEngine.reduce(initial,group,reading())).snapshot
        assertEquals(UtcTimestamp(102),acknowledged.alerts.single().acknowledgedAt)
        invalid(LifeTableEngine.reduce(initial,group.copy(actions=SnapshotList.of(ack,GameAction.SetDayNight(DayNightState.DAY))),reading()))
        assertNull(initial.alerts.single().acknowledgedAt)
    }
    @Test fun failedGroupDoesNotPublishPartialParticipationOrDesignation() {
        val input=game()
        val group=GameAction.Group(SnapshotList.of(eliminate(LifeFixtures.seatB),GameAction.SetMonarch(LifeFixtures.seatB)),EntityId("group"))
        invalid(LifeTableEngine.reduce(input,group,reading()))
        assertTrue(input.seat(LifeFixtures.seatB).participation is SeatParticipation.Active)
        assertNull(input.designations.monarch)
        assertEquals(10.seconds,input.clock.seats.single { it.seatId==LifeFixtures.seatB }.remaining)
    }
    @Test fun renameReorderAndPresentationPreserveExactIdentitiesAndReferences() {
        val input=game()
        val art=GameArtReference.CachedCard(LifeFixtures.identity("art"),EntityId("cached"))
        val appearance=SeatAppearance(DisplayColor(0xff123456),art,ArtFit.CONTAIN,ArtCrop(0.1,0.2,0.8,0.9),SeatOrientation.INVERTED)
        val presentation=GamePresentation(GameLayoutMode.OPERATOR,"layout-v1",true)
        val group=GameAction.Group(SnapshotList.of(GameAction.RenameSeat(LifeFixtures.seatA,""),GameAction.ReorderSeats(SnapshotList.of(LifeFixtures.seatC,LifeFixtures.seatA,LifeFixtures.seatB)),
            GameAction.SetSeatAppearance(LifeFixtures.seatA,appearance),GameAction.SetGamePresentation(presentation),GameAction.SetAlertPreferences(GameAlertPreferences(false,true,true))),EntityId("group"))
        val changed=ok(LifeTableEngine.reduce(input,group,reading())).snapshot
        assertEquals("",changed.seat(LifeFixtures.seatA).playerName)
        assertEquals(appearance,changed.seat(LifeFixtures.seatA).appearance)
        assertEquals(input.seat(LifeFixtures.seatA).commanders,changed.seat(LifeFixtures.seatA).commanders)
        assertEquals(input.seat(LifeFixtures.seatA).deck,changed.seat(LifeFixtures.seatA).deck)
        assertEquals(presentation,changed.settings.presentation)
        assertEquals(GameAlertPreferences(false,true,true),changed.settings.alertPreferences)
        assertEquals(listOf(LifeFixtures.seatC,LifeFixtures.seatA,LifeFixtures.seatB),changed.seats.sortedBy { it.order }.map { it.id })
        assertEquals(input.revision,changed.revision);assertEquals(input.lastEventId,changed.lastEventId)
    }
    @Test fun newlySettledOccurrenceAcknowledgedInSameActionReturnsFinalAckState() {
        val input=game().let { it.copy(clock=it.clock.copy(seats=SnapshotList.copyOf(it.clock.seats.map { bank -> if(bank.seatId==LifeFixtures.seatB) bank.copy(remaining=1.seconds) else bank }))) }
        val id=SeatClockReducer.zeroAlertId(LifeFixtures.seatB,1,1)
        val reduced=ok(LifeTableEngine.reduce(input,GameAction.AcknowledgeGameAlert(id),reading()))
        assertEquals(1,reduced.alerts.size)
        assertEquals(UtcTimestamp(102),reduced.alerts.single().acknowledgedAt)
        assertEquals(reduced.alerts.single(),reduced.snapshot.alerts.single())
        assertNull(reduced.settledBefore.alerts.single().acknowledgedAt)
    }
}
