package com.architectpsg.commandlibrary.data.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.feature.life.persistence.SqlPersistedGameService
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/** Actual Room3/SQLite port composition; compilation alone is not a runtime pass. Synthetic game data. */
@RunWith(AndroidJUnit4::class)
class PersistedLifeServiceIntegrationTest {
    private fun <T> ok(value:Outcome<T>):T { assertTrue("Expected success, got $value",value is Outcome.Success);return (value as Outcome.Success).value }
    @Test fun actualRoomCheckpointRecoveryAndConditionalUndoSurviveFileReopen()=runBlocking<Unit> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file="life-service-${UUID.randomUUID()}.db";var database:StorageDatabase?=null
        val source=ProviderId("synthetic-native-life");val id=GameId(source,"game");val seat=EntityId("a");val other=EntityId("b")
        val ids=object:IdSource { var n=0;override fun nextId()=if(++n==1)"game" else "native-$n" }
        val random=object:RandomSource { override fun nextInt(upperBound:Int):Int=error("Unexpected native random draw") }
        var now=0L
        val clock=object:Clock { override fun utcNow()=UtcTimestamp(1000+now);override fun monotonicNow()=now.seconds }
        val token=object:CancelToken { override val isCancelled=false;override fun throwIfCancelled()=Unit }
        val life=CounterDefinition(EntityId("life"),"Life",null,null,20,CounterLossPolicy.None,false,CounterRole.LIFE)
        val settings=GameSettings(Knowledge.Absent("Synthetic"),20,SnapshotList.of(life),SnapshotList.empty(),SeatClockMode.NONE,0)
        val seats=SnapshotList.of(Seat(seat,"A",null,Knowledge.Absent("None"),0,SnapshotList.empty()),Seat(other,"B",null,Knowledge.Absent("None"),1,SnapshotList.empty()))
        val input=GameCreation(EntityId("create"),settings,seats,SeatClockState(SeatClockMode.NONE,SnapshotList.empty(),null,null,true,ClockZeroPolicy.ALERT,ClockUndoPolicy.KEEP_ELAPSED,SnapshotList.empty()))
        try {
            val firstDb=ok(StorageDatabase.open(context,file));database=firstDb
            val first:PersistedGameService=SqlPersistedGameService(firstDb,LifePayloadCodec(),ids,random,clock,EntityId("process-a"),source)
            var current=ok(first.create(input,token))
            current=ok(first.dispatch(id,GameAction.Group(SnapshotList.of(GameAction.SwitchTurn(seat),GameAction.SetElapsedPaused(false)),EntityId("start")),current.revision,token))
            val lineage=current.elapsed.turnLineageId
            now=5;current=ok(first.dispatch(id,GameAction.SwitchTurn(other),current.revision,token))
            now=9;current=ok(first.resume(id,token));assertEquals(9.seconds,current.elapsed.gameElapsed)
            firstDb.close();database=null
            val reopenedDb=ok(StorageDatabase.open(context,file));database=reopenedDb;now=0
            val reopened:PersistedGameService=SqlPersistedGameService(reopenedDb,LifePayloadCodec(),ids,random,clock,EntityId("process-b"),source)
            val recovered=ok(reopened.resume(id,token));assertTrue(recovered.elapsed.paused);assertEquals(9.seconds,recovered.elapsed.gameElapsed)
            val undone=ok(reopened.undo(id,recovered.revision,token))
            assertEquals(seat,undone.activeSeatId);assertEquals(lineage,undone.elapsed.turnLineageId)
            assertEquals(5.seconds,undone.elapsed.turnElapsed);assertEquals(9.seconds,undone.elapsed.gameElapsed);assertTrue(undone.elapsed.paused)
            val events=ok(reopened.events(id,PageRequest(10),token)).items
            assertEquals(3,events.size);assertEquals(Knowledge.Known(1L),events.last().undoOfEventId)
            assertEquals(1L,ok(reopened.history(GameHistoryFilter(),PageRequest(10),token)).totalMatches)
            assertEquals(undone,ok(reopened.observe(id,token).first()))
        } finally { database?.close();context.deleteDatabase(file) }
    }
}
