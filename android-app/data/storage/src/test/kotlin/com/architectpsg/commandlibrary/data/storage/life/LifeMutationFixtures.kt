package com.architectpsg.commandlibrary.data.storage.life

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.life.persistence.LifeActionWrite
import com.architectpsg.commandlibrary.feature.life.persistence.StoredLifeGame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Synthetic local storage packets; real semantic reducer/timing authority is a separate service gate. */
internal object LifeMutationFixtures {
    val provider=ProviderId("synthetic-mutations")
    val id=GameId(provider,"game")
    val seat=EntityId("seat-a")
    val other=EntityId("seat-b")
    val process=EntityId("process")
    val stamp=UtcTimestamp(1_000)
    val token=object:CancelToken {override val isCancelled=false;override fun throwIfCancelled()=Unit}
    fun input():GameCreation {
        val life=CounterDefinition(EntityId("life"),"Life",null,null,20,CounterLossPolicy.None,false,CounterRole.LIFE)
        val settings=GameSettings(Knowledge.Absent("Synthetic"),20,SnapshotList.of(life),SnapshotList.empty(),SeatClockMode.NONE,0)
        val seats=SnapshotList.of(Seat(seat,"A",null,Knowledge.Absent("None"),0,SnapshotList.empty()),Seat(other,"B",null,Knowledge.Absent("None"),1,SnapshotList.empty()))
        return GameCreation(EntityId("creation"),settings,seats,SeatClockState(SeatClockMode.NONE,SnapshotList.empty(),null,null,true,ClockZeroPolicy.ALERT,ClockUndoPolicy.KEEP_ELAPSED,SnapshotList.empty()))
    }
    fun initial(input:GameCreation=input())=GameCheckpoint(GameSnapshot(id,1,Revision(0),input.settings,GameStatus.PAUSED,stamp,Knowledge.Absent("None"),input.seats,SnapshotList.empty(),null,0,input.clock,SnapshotList.empty(),Knowledge.Absent("None"),Knowledge.Absent("None"),elapsed=ElapsedTimerState(Duration.ZERO,Duration.ZERO,true,null,Knowledge.Absent("Not started"))),Knowledge.Absent("None"),null)
    fun action(current:StoredLifeGame,eventId:Long,action:GameAction=GameAction.SetAdjustmentStep(2),undo:Knowledge<Long> = Knowledge.Absent("Not undo"),change:(GameSnapshot)->GameSnapshot={it.copy(settings=it.settings.copy(adjustmentStep=2))}):LifeActionWrite {
        val before=current.checkpoint.snapshot
        val at=UtcTimestamp(1_001+current.sequence)
        val after=change(before).copy(revision=Revision(before.revision.value+1),lastEventId=Knowledge.Known(eventId))
        val event=GameEvent(eventId,before.id,EntityId("group-$eventId"),action,at,undo,after.revision)
        return LifeActionWrite(GameEventArchive(1,event,GameActionProvenance(eventId,before,after,GameTimeObservation(current.sequence.seconds,at,process))),GameCheckpoint(after,Knowledge.Known(eventId),current.checkpoint.recoveredProcessSessionId))
    }
    fun finish(current:StoredLifeGame,eventId:Long,alert:Boolean=false):LifeActionWrite {
        val at=UtcTimestamp(1_001+current.sequence)
        val outcomes=SnapshotList.of(SeatResult(seat,SeatResultKind.WIN,null),SeatResult(other,SeatResultKind.LOSS,null))
        val old=(current.checkpoint.snapshot.result as? Knowledge.Known)?.value
        val result=GameResult(old?.id ?: EntityId("result"),id,SnapshotList.of(seat),at,"Confirmed",Revision(current.checkpoint.snapshot.revision.value+1),false,outcomes)
        val finish=GameAction.FinishWithOutcomes(outcomes,"Confirmed")
        val operation=if(alert)GameAction.Group(SnapshotList.of(finish,GameAction.RenameSeat(seat,"A finished"),GameAction.RenameSeat(other,"B finished")),EntityId("group-$eventId"))else finish
        return action(current,eventId,operation) { before ->
            before.copy(status=GameStatus.COMPLETED,endedAt=Knowledge.Known(at),result=Knowledge.Known(result),
                seats=if(alert)SnapshotList.copyOf(before.seats.map{it.copy(playerName=if(it.id==seat)"A finished" else "B finished")})else before.seats,
                alerts=if(alert)SnapshotList.of(GameAlert(EntityId("alert"),id,GameAlertTarget.GameTimer,at,null))else before.alerts,
                clock=before.clock.copy(paused=true),elapsed=before.elapsed.copy(paused=true))
        }
    }
    fun reopen(current:StoredLifeGame,eventId:Long):LifeActionWrite = action(current,eventId,GameAction.Reopen("Resume")) { before ->
        before.copy(status=GameStatus.PAUSED,endedAt=Knowledge.Absent("Reopened"),result=Knowledge.Known((before.result as Knowledge.Known).value.copy(retracted=true,revision=Revision(before.revision.value+1))))
    }
}
