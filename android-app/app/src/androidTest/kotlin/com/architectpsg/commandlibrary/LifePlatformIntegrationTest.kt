package com.architectpsg.commandlibrary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.di.AndroidLifeClock
import com.architectpsg.commandlibrary.di.ProcessLifeIdentity
import com.architectpsg.commandlibrary.di.RandomUuidIdSource
import com.architectpsg.commandlibrary.di.SystemGameRandom
import com.architectpsg.commandlibrary.feature.life.persistence.SqlPersistedGameService
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit unregistered port using actual Android providers and Room; no AppGraph binding. */
@RunWith(AndroidJUnit4::class)
class LifePlatformIntegrationTest {
    private fun <T> ok(value: Outcome<T>): T {
        assertTrue("Expected success, got $value", value is Outcome.Success)
        return (value as Outcome.Success).value
    }

    @Test fun realPlatformProvidersPersistRecordedDiceAndUndoWithoutChangingProcessIdentity() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = "life-platform-${UUID.randomUUID()}.db"
        var db: StorageDatabase? = null
        val processId = ProcessLifeIdentity.id
        val token = object : CancelToken {
            override val isCancelled = false
            override fun throwIfCancelled() = Unit
        }
        try {
            val opened = ok(StorageDatabase.open(context,file)); db = opened
            val service: PersistedGameService = SqlPersistedGameService(opened,LifePayloadCodec(),RandomUuidIdSource,
                SystemGameRandom,AndroidLifeClock,processId,ProviderId("synthetic-platform-life"))
            val seat = EntityId("a")
            val life = CounterDefinition(EntityId("life"),"Life",null,null,20,CounterLossPolicy.None,false,CounterRole.LIFE)
            val settings = GameSettings(Knowledge.Absent("Synthetic"),20,SnapshotList.of(life),SnapshotList.empty(),SeatClockMode.NONE,0)
            val input = GameCreation(EntityId(UUID.randomUUID().toString()),settings,
                SnapshotList.of(Seat(seat,"A",null,Knowledge.Absent("None"),0,SnapshotList.empty())),
                SeatClockState(SeatClockMode.NONE,SnapshotList.empty(),null,null,true,ClockZeroPolicy.ALERT,ClockUndoPolicy.KEEP_ELAPSED,SnapshotList.empty()))
            val created = ok(service.create(input,token))
            assertEquals(created,ok(service.read(created.id,token)))
            val request = RandomRequest.Dice(EntityId(UUID.randomUUID().toString()),4,6)
            val rolled = ok(service.dispatch(created.id,GameAction.RequestRandom(request),created.revision,token))
            val events = ok(service.events(created.id,PageRequest(10),token)).items
            assertEquals(1,events.size)
            val result = events.single().randomResults.single()
            assertEquals(request.requestId,result.requestId)
            val dice = result.value as RecordedRandomValue.Dice
            assertEquals(4,dice.faces.size)
            assertTrue(dice.faces.all { it in 1..6 })
            assertEquals(dice.faces.sumOf { it.toLong() },dice.sum)
            val undone = ok(service.undo(created.id,rolled.revision,token))
            assertEquals(created.counters,undone.counters)
            assertEquals(created.elapsed.gameElapsed,undone.elapsed.gameElapsed)
            assertEquals(created.elapsed.turnElapsed,undone.elapsed.turnElapsed)
            assertEquals(created.elapsed.turnLineageId,undone.elapsed.turnLineageId)
            assertTrue(undone.elapsed.paused)
            val undoAnchor = requireNotNull(undone.elapsed.anchor)
            assertEquals(processId,undoAnchor.processSessionId)
            assertTrue(undoAnchor.monotonicElapsed.isFinite())
            assertSame(processId,ProcessLifeIdentity.id)
            assertEquals(2,ok(service.events(created.id,PageRequest(10),token)).items.size)
            opened.close(); db = null
            val reopened = ok(StorageDatabase.open(context,file)); db = reopened
            val reader: PersistedGameService = SqlPersistedGameService(reopened,LifePayloadCodec(),RandomUuidIdSource,
                SystemGameRandom,AndroidLifeClock,ProcessLifeIdentity.id,ProviderId("synthetic-platform-life"))
            assertEquals(undone,ok(reader.read(created.id,token)))
            assertEquals(result,ok(reader.events(created.id,PageRequest(10),token)).items.first().randomResults.single())
        } finally { db?.close();context.deleteDatabase(file) }
    }
}
