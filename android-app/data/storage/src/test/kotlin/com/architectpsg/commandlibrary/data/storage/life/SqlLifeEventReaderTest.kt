package com.architectpsg.commandlibrary.data.storage.life

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.feature.life.persistence.SqlLifeEventReader
import com.architectpsg.commandlibrary.feature.life.persistence.SqlLifeGameStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration

class SqlLifeEventReaderTest {
    @get:Rule val folder=TemporaryFolder()
    private val codec=LifePayloadCodec()
    private val provider=ProviderId("synthetic-events")
    private val gameId=GameId(provider,"game")
    private val seatId=EntityId("seat")
    private val stamp=UtcTimestamp(10,17)
    private val token=object:CancelToken {
        override val isCancelled=false
        override fun throwIfCancelled()=Unit
    }
    private fun <T> ok(o:Outcome<T>):T {assertTrue("Expected Success, got $o",o is Outcome.Success);return(o as Outcome.Success<T>).value}
    private fun fails(o:Outcome<*>,code:FailureCode) {assertTrue("Expected $code, got $o",o is Outcome.Failure);assertEquals(code,(o as Outcome.Failure).error.code)}
    private fun creation(operation:String="operation"):GameCreation {
        val life=CounterDefinition(EntityId("life"),"Life",null,null,20,CounterLossPolicy.None,false,CounterRole.LIFE)
        val settings=GameSettings(Knowledge.Absent("Synthetic"),20,SnapshotList.of(life),SnapshotList.empty(),SeatClockMode.NONE,0)
        val seats=SnapshotList.of(Seat(seatId,"Synthetic",null,Knowledge.Absent("None"),0,SnapshotList.empty()))
        return GameCreation(EntityId(operation),settings,seats,SeatClockState(SeatClockMode.NONE,SnapshotList.empty(),null,null,true,ClockZeroPolicy.ALERT,ClockUndoPolicy.KEEP_ELAPSED,SnapshotList.empty()))
    }
    private fun initial(input:GameCreation,id:GameId)=GameSnapshot(id,1,Revision(0),input.settings,GameStatus.PAUSED,stamp,Knowledge.Absent("None"),input.seats,SnapshotList.empty(),null,0,input.clock,SnapshotList.empty(),Knowledge.Absent("None"),Knowledge.Absent("None"),elapsed=ElapsedTimerState(Duration.ZERO,Duration.ZERO,true,null,Knowledge.Absent("None")))
    private fun archive(id:Long,revision:Long=1,game:GameId=gameId,undo:Knowledge<Long> = Knowledge.Absent("Not undo"),action:GameAction=GameAction.SetAdjustmentStep(2)):GameEventArchive {
        val input=creation();val before=initial(input,game).copy(revision=Revision(revision-1),settings=input.settings.copy(counterDefinitions=SnapshotList.copyOf(input.settings.counterDefinitions.map{it.copy(role=CounterRole.OTHER)})))
        val after=before.copy(revision=Revision(revision),lastEventId=Knowledge.Known(id))
        val event=GameEvent(id,game,EntityId("group-$revision"),action,stamp,undo,Revision(revision))
        return GameEventArchive(1,event,GameActionProvenance(id,before,after,GameTimeObservation(Duration.ZERO,UtcTimestamp(9),EntityId("session"))))
    }
    private suspend fun seed(db:HostLifeDatabase,archives:List<GameEventArchive>,id:GameId=gameId,operation:String="operation") {
        val input=creation(operation);val first=initial(input,id)
        ok(SqlLifeGameStore(db,codec).create(input,{Outcome.Success(GameCheckpoint(first,Knowledge.Absent("None"),null))},token))
        for(a in archives) insert(db,a)
        val head=archives.maxByOrNull{it.event.revision.value}?.provenance?.afterAction
        if(head!=null) db.connection.prepare("UPDATE life_game SET revision=?,checkpoint_payload=? WHERE game_source=? AND game_id=?").use {it.bindLong(1,head.revision.value);it.bindBlob(2,ok(codec.encodeCheckpoint(GameCheckpoint(head,Knowledge.Known(head.lastEventId.let{(it as Knowledge.Known).value}),null))).copyBytes());it.bindText(3,id.source.value);it.bindText(4,id.raw);it.step()}
    }
    private fun insert(db:HostLifeDatabase,a:GameEventArchive) {
        val e=a.event
        db.connection.prepare("INSERT INTO life_event VALUES (?,?,?,?,?,?,?,?,?)").use {
            it.bindText(1,e.gameId.source.value);it.bindText(2,e.gameId.raw);it.bindLong(3,e.id);it.bindLong(4,e.revision.value);it.bindText(5,e.groupId.value)
            it.bindLong(6,e.at.epochSeconds);it.bindLong(7,e.at.nanoAdjustment.toLong())
            val undo=(e.undoOfEventId as? Knowledge.Known)?.value;if(undo==null)it.bindNull(8)else it.bindLong(8,undo)
            it.bindBlob(9,ok(codec.encodeArchive(a)).copyBytes());it.step()
        }
    }
    private fun overwrite(db:HostLifeDatabase,a:GameEventArchive) {db.connection.prepare("UPDATE life_event SET archive_payload=?").use{it.bindBlob(1,ok(codec.encodeArchive(a)).copyBytes());it.step()}}

    @Test fun reopenPreservesLegacyProvenanceAndDistinctClockObservation()=runBlocking {
        val file=folder.newFile();val request=EntityId("request")
        val original=archive(100,undo=Knowledge.Unknown("Legacy",SnapshotList.empty()),action=GameAction.RequestRandom(RandomRequest.Coin(request)))
        val a=original.copy(event=original.event.copy(randomResults=SnapshotList.of(RecordedRandomResult(request,stamp,RecordedRandomValue.Coin(true)))))
        HostLifeDatabase(file).use {seed(it,listOf(a))}
        HostLifeDatabase(file,false).use {db -> assertEquals(a,ok(SqlLifeEventReader(db,codec).archive(gameId,100,token)))}
    }
    @Test fun revisionOrderAndLastReturnedCursorNeverAssumeMonotonicEventIds()=runBlocking {
        val events=listOf(archive(100,1),archive(5,2),archive(77,3))
        HostLifeDatabase(folder.newFile()).use {db ->
            seed(db,events);val reader=SqlLifeEventReader(db,codec)
            val first=ok(reader.events(gameId,PageRequest(2),token));assertEquals(listOf(100L,5L),first.items.map{it.id});assertEquals(Revision(3),first.revision);assertNotNull(first.nextCursor)
            val next=ok(reader.events(gameId,PageRequest(2,first.nextCursor),token));assertEquals(listOf(77L),next.items.map{it.id});assertNull(next.nextCursor)
            assertEquals(events.map{it.event},ok(reader.events(gameId,PageRequest(100),token)).items)
        }
    }
    @Test fun missingAndEmptyHistoryAreTypedWithoutInventedSuccess()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val reader=SqlLifeEventReader(db,codec)
            assertEquals(UnavailableReason.NOT_FOUND,(reader.archive(gameId,1,token) as Outcome.Unavailable).reason)
            assertEquals(UnavailableReason.NOT_FOUND,(reader.events(gameId,PageRequest(1),token) as Outcome.Unavailable).reason)
            seed(db,emptyList());assertTrue(ok(reader.events(gameId,PageRequest(1),token)).items.isEmpty())
            assertEquals(UnavailableReason.NOT_FOUND,(reader.archive(gameId,1,token) as Outcome.Unavailable).reason)
            fails(reader.archive(gameId,-1,token),FailureCode.INVALID_INPUT)
        }
    }
    @Test fun invalidMetadataFailsBeforeAnyArchiveBlobFetch()=runBlocking {
        for(sql in listOf("UPDATE life_event SET group_id=CAST(X'FF' AS TEXT)","UPDATE life_event SET group_id=CAST(zeroblob(65537) AS TEXT)","UPDATE life_event SET at_nano=1000000000","UPDATE life_event SET at_seconds=X'FF'","UPDATE life_event SET undo_of_event_id=X'FF'","UPDATE life_event SET undo_of_event_id=-1","UPDATE life_event SET undo_of_event_id=100","UPDATE life_event SET archive_payload=zeroblob(16777217)","UPDATE life_event SET archive_payload='bad'","UPDATE life_event SET revision=0","UPDATE life_event SET revision=2","UPDATE life_game SET revision=X'FF'","UPDATE life_game SET revision=-1")) HostLifeDatabase(folder.newFile()).use {db ->
            seed(db,listOf(archive(100)));db.sql(sql);db.blobQueries=0
            fails(SqlLifeEventReader(db,codec).events(gameId,PageRequest(1),token),FailureCode.CORRUPT_DATA);assertEquals(0,db.blobQueries)
        }
    }
    @Test fun everyEventProjectionMustMatchDecodedArchive()=runBlocking {
        for(sql in listOf("UPDATE life_event SET event_id=99","UPDATE life_event SET group_id='wrong'","UPDATE life_event SET at_seconds=11","UPDATE life_event SET at_nano=18","UPDATE life_event SET undo_of_event_id=99")) HostLifeDatabase(folder.newFile()).use {db ->
            seed(db,listOf(archive(100)));db.sql(sql)
            fails(SqlLifeEventReader(db,codec).events(gameId,PageRequest(1),token),FailureCode.CORRUPT_DATA)
        }
        HostLifeDatabase(folder.newFile()).use {db ->
            seed(db,listOf(archive(100)));overwrite(db,archive(100,game=GameId(provider,"other")))
            fails(SqlLifeEventReader(db,codec).archive(gameId,100,token),FailureCode.CORRUPT_DATA)
        }
    }
    @Test fun knownUndoTargetRequiresEarlierRevisionInSameGameNotSmallerEventId()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val target=archive(100,1);val undo=archive(5,2,undo=Knowledge.Known(100L));seed(db,listOf(target,undo))
            assertEquals(undo,ok(SqlLifeEventReader(db,codec).archive(gameId,5,token)))
        }
        for(target in listOf<Long?>(null,3)) HostLifeDatabase(folder.newFile()).use {db ->
            val records=mutableListOf(archive(5,2,undo=Knowledge.Known(100L)))
            if(target!=null)records+=archive(100,target)
            seed(db,records);fails(SqlLifeEventReader(db,codec).archive(gameId,5,token),FailureCode.CORRUPT_DATA)
        }
    }
    @Test fun archiveVersionAndAtomicGroupIdentityAreChecked()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            seed(db,listOf(archive(100)));overwrite(db,archive(100).copy(version=2))
            assertEquals(UnavailableReason.UNSUPPORTED,(SqlLifeEventReader(db,codec).archive(gameId,100,token) as Outcome.Unavailable).reason)
        }
        HostLifeDatabase(folder.newFile()).use {db ->
            val a=archive(100,action=GameAction.Group(SnapshotList.of(GameAction.SetAdjustmentStep(2)),EntityId("wrong")));seed(db,listOf(a))
            fails(SqlLifeEventReader(db,codec).archive(gameId,100,token),FailureCode.CORRUPT_DATA)
        }
    }
    @Test fun cursorIsCanonicalAndBoundToExactCompositeGameIdentity()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val id1=GameId(ProviderId("a:b"),"c';--");val id2=GameId(ProviderId("a"),"b:c';--")
            seed(db,listOf(archive(100,1,id1),archive(5,2,id1)),id1,"one")
            seed(db,listOf(archive(100,1,id2),archive(5,2,id2)),id2,"two")
            val reader=SqlLifeEventReader(db,codec);val cursor=ok(reader.events(id1,PageRequest(1),token)).nextCursor!!
            fails(reader.events(id2,PageRequest(1,cursor),token),FailureCode.INVALID_INPUT)
            for(invalid in listOf("bad",cursor+":extra",cursor.replace(":1:",":01:"),cursor.uppercase())) fails(reader.events(id1,PageRequest(1,invalid),token),FailureCode.INVALID_INPUT)
            assertEquals(5L,ok(reader.events(id1,PageRequest(1,cursor),token)).items.single().id)
        }
    }
    @Test fun byteBoundedPageResumesAfterLastReturnedInsteadOfSkippingCandidate()=runBlocking {
        val name="x".repeat(60_000)
        val records=(1L..3L).map { revision -> archive(revision,revision,action=GameAction.Group(SnapshotList.copyOf(List(190){GameAction.RenameSeat(seatId,name)}),EntityId("group-$revision"))) }
        HostLifeDatabase(folder.newFile()).use {db ->
            seed(db,records);assertTrue(db.scalar("SELECT sum(length(archive_payload)) FROM life_event")>32L*1024*1024);assertTrue(db.scalar("SELECT max(length(archive_payload)) FROM life_event")<=16L*1024*1024)
            val reader=SqlLifeEventReader(db,codec);db.blobQueries=0
            val first=ok(reader.events(gameId,PageRequest(100),token));assertEquals(listOf(1L,2L),first.items.map{it.id});assertEquals(2,db.blobQueries)
            val next=ok(reader.events(gameId,PageRequest(100,first.nextCursor),token));assertEquals(listOf(3L),next.items.map{it.id});assertNull(next.nextCursor)
        }
    }
    @Test fun corruptLookaheadMetadataIsRejectedBeforeFetchingFirstSelectedBlob()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            seed(db,listOf(archive(100,1),archive(5,2)));db.sql("UPDATE life_event SET group_id=CAST(X'FF' AS TEXT) WHERE event_id=5");db.blobQueries=0
            fails(SqlLifeEventReader(db,codec).events(gameId,PageRequest(1),token),FailureCode.CORRUPT_DATA)
            assertEquals(0,db.blobQueries)
        }
    }
    @Test fun importedMaximumUtf8IdentitiesFitBoundedGameBoundCursor()=runBlocking {
        val id=GameId(ProviderId("s".repeat(65_536)),"界".repeat(21_845)+"a")
        HostLifeDatabase(folder.newFile()).use {db ->
            seed(db,listOf(archive(100,1,id),archive(5,2,id)),id)
            val reader=SqlLifeEventReader(db,codec);val first=ok(reader.events(id,PageRequest(1),token))
            assertTrue(first.nextCursor!!.toByteArray(Charsets.UTF_8).size<=PageRequest.MAX_CURSOR_BYTES)
            assertEquals(5L,ok(reader.events(id,PageRequest(1,first.nextCursor),token)).items.single().id)
        }
    }
    @Test fun plausibleProjectionRevisionStillMustMatchArchiveRevision()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            seed(db,listOf(archive(100)));db.sql("UPDATE life_game SET revision=2");db.sql("UPDATE life_event SET revision=2")
            fails(SqlLifeEventReader(db,codec).archive(gameId,100,token),FailureCode.CORRUPT_DATA)
        }
    }
    @Test fun cancellationDuringMetadataOrArchiveFetchEscapesTransaction()=runBlocking {
        for(afterBlob in listOf(false,true)) HostLifeDatabase(folder.newFile()).use {db ->
            seed(db,listOf(archive(100)))
            var stopped=false
            val cancel=object:CancelToken {override val isCancelled get()=stopped;override fun throwIfCancelled(){if(stopped)throw java.util.concurrent.CancellationException("midread")}}
            val observing=object:Database by db {
                override suspend fun <T> transaction(action:suspend(DbTransaction)->T,cancel:CancelToken):Outcome<T> = db.transaction({tx ->
                    action(object:DbTransaction by tx {
                        override suspend fun query(request:SqlRequest,cancel:CancelToken):Outcome<SnapshotList<RowData>> {
                            val result=tx.query(request,cancel)
                            if(request.statement.contains("FROM life_event") && request.statement.startsWith("SELECT archive_payload")==afterBlob)stopped=true
                            return result
                        }
                    })
                },cancel)
            }
            db.blobQueries=0
            try {SqlLifeEventReader(observing,codec).events(gameId,PageRequest(1),cancel);fail("Midread cancellation swallowed")}catch(expected:java.util.concurrent.CancellationException){}
            assertTrue(stopped);assertEquals(if(afterBlob)1 else 0,db.blobQueries)
            assertEquals(1L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun oversizedCallerIdentitiesFailBeforePayloadFetch()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            seed(db,listOf(archive(100)));db.blobQueries=0
            for(id in listOf(GameId(ProviderId("s".repeat(65_537)),"game"),GameId(provider,"界".repeat(21_846)))) fails(SqlLifeEventReader(db,codec).archive(id,100,token),FailureCode.INVALID_INPUT)
            assertEquals(0,db.blobQueries)
        }
    }
    @Test fun knownUndoTargetMetadataIsCheckedWithoutDecodingItsArchive()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val undo=archive(5,2,undo=Knowledge.Known(100L));seed(db,listOf(archive(100,1),undo))
            db.sql("UPDATE life_event SET archive_payload=X'00' WHERE event_id=100");db.blobQueries=0
            assertEquals(undo,ok(SqlLifeEventReader(db,codec).archive(gameId,5,token)))
            assertEquals(1,db.blobQueries)
            db.sql("UPDATE life_event SET at_nano=1000000000 WHERE event_id=100")
            fails(SqlLifeEventReader(db,codec).archive(gameId,5,token),FailureCode.CORRUPT_DATA)
        }
    }
    @Test fun cancellationPropagatesWithoutChangingStoredHistory()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            seed(db,listOf(archive(100)))
            val cancelled=object:CancelToken {override val isCancelled=true;override fun throwIfCancelled(){throw java.util.concurrent.CancellationException("test")}}
            try {SqlLifeEventReader(db,codec).events(gameId,PageRequest(1),cancelled);fail("Cancellation swallowed")}catch(expected:java.util.concurrent.CancellationException){}
            assertEquals(1L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }
}
