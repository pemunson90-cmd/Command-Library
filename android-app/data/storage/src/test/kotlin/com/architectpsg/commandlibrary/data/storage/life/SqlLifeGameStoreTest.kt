package com.architectpsg.commandlibrary.data.storage.life

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.codec.encodeLifeFrame
import com.architectpsg.commandlibrary.data.storage.life.codec.snapshotWire
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.feature.life.persistence.SqlLifeGameStore
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration

class SqlLifeGameStoreTest {
    @get:Rule val folder=TemporaryFolder()
    private class Token:CancelToken {
        var stopped=false
        override val isCancelled get()=stopped
        override fun throwIfCancelled() {if(stopped)throw CancellationException("test cancellation")}
    }
    private val token=Token()
    private val codec=LifePayloadCodec()
    private val provider=ProviderId("synthetic-local")
    private val stamp=UtcTimestamp(10,17)
    private fun input(operation:String="operation"):GameCreation {
        val life=CounterDefinition(EntityId("life"),"Life",null,null,20,CounterLossPolicy.None,false,CounterRole.LIFE)
        val settings=GameSettings(Knowledge.Absent("Synthetic format absence"),20,SnapshotList.of(life),SnapshotList.empty(),SeatClockMode.NONE,7)
        val seats=SnapshotList.of(Seat(EntityId("seat"),"E\u0301 原文",null,Knowledge.Known(DeckReference(
            DeckId(provider,"deck"),Revision(3),"Recorded deck",false)),0,SnapshotList.empty()))
        val clock=SeatClockState(SeatClockMode.NONE,SnapshotList.empty(),null,null,true,ClockZeroPolicy.ALERT,ClockUndoPolicy.KEEP_ELAPSED,SnapshotList.empty())
        return GameCreation(EntityId(operation),settings,seats,clock)
    }
    private fun initial(input:GameCreation=input(),id:String="game"):GameCheckpoint = GameCheckpoint(GameSnapshot(
        GameId(provider,id),1,Revision(0),input.settings,GameStatus.PAUSED,stamp,Knowledge.Absent("Not ended"),input.seats,
        SnapshotList.empty(),null,0,input.clock,SnapshotList.empty(),Knowledge.Absent("No event"),Knowledge.Absent("No result"),
        elapsed=ElapsedTimerState(Duration.ZERO,Duration.ZERO,true,null,Knowledge.Absent("No initial turn"))),
        Knowledge.Absent("No settled event"),null)
    private fun <T> ok(o:Outcome<T>):T {assertTrue("Expected Success, got ${o.javaClass.simpleName}",o is Outcome.Success);return(o as Outcome.Success<T>).value}
    private fun fails(o:Outcome<*>,code:FailureCode){assertTrue("Expected $code, got ${o.javaClass.simpleName}",o is Outcome.Failure);assertEquals(code,(o as Outcome.Failure).error.code)}
    private suspend fun create(store:SqlLifeGameStore,input:GameCreation=input(),id:String="game")=
        ok(store.create(input,{Outcome.Success(initial(input,id))},token))

    @Test fun createAndReopenPreserveOriginalInputCheckpointAndFrozenNames()=runBlocking {
        val file=folder.newFile();val input=input();val checkpoint=initial(input)
        HostLifeDatabase(file).use { db ->
            val result=create(SqlLifeGameStore(db,codec),input)
            assertEquals(input,result.creation);assertEquals(checkpoint,result.checkpoint);assertEquals(0L,result.sequence)
            db.connection.prepare("SELECT player_name,player_name_folded,deck_name FROM life_seat_search").use {
                assertTrue(it.step());assertEquals("E\u0301 原文",it.getText(0));assertEquals("é 原文",it.getText(1));assertEquals("Recorded deck",it.getText(2))
            }
        }
        HostLifeDatabase(file,false).use { db ->
            val read=ok(SqlLifeGameStore(db,codec).read(checkpoint.snapshot.id,token))
            assertEquals(input,read.creation);assertEquals(checkpoint,read.checkpoint)
        }
    }
    @Test fun committedRetryReturnsCurrentCheckpointWithoutInitializingAgain()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            val store=SqlLifeGameStore(db,codec);val input=input();val first=create(store,input)
            val changed=first.checkpoint.snapshot.copy(revision=Revision(1),settings=input.settings.copy(adjustmentStep=5),lastEventId=Knowledge.Known(1L))
            val current=GameCheckpoint(changed,Knowledge.Known(1L),null)
            val event=GameEvent(1,changed.id,EntityId("group"),GameAction.SetAdjustmentStep(5),UtcTimestamp(20,17),Knowledge.Absent("Not undo"),Revision(1))
            val archive=GameEventArchive(1,event,GameActionProvenance(1,first.checkpoint.snapshot,changed,GameTimeObservation(Duration.ZERO,event.at,EntityId("process"))))
            db.connection.prepare("UPDATE life_game SET revision=1,checkpoint_sequence=1,checkpoint_payload=?,updated_seconds=20").use {it.bindBlob(1,ok(codec.encodeCheckpoint(current)).copyBytes());it.step()}
            db.connection.prepare("INSERT INTO life_event VALUES ('synthetic-local','game',1,1,'group',20,17,NULL,?)").use {it.bindBlob(1,ok(codec.encodeArchive(archive)).copyBytes());it.step()}
            var initialized=false
            val retry=ok(store.create(input,{initialized=true;Outcome.Success(initial(input,"wrong-game"))},token))
            assertFalse(initialized);assertEquals(current,retry.checkpoint);assertEquals(1L,retry.sequence);assertEquals(input,retry.creation)
            assertEquals(1L,db.scalar("SELECT count(*) FROM life_game"))
        }
    }
    @Test fun operationConflictAndGameIdCollisionNeverOverwriteExistingGame()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            val store=SqlLifeGameStore(db,codec);val input=input();val first=create(store,input)
            var initialized=false
            fails(store.create(input.copy(settings=input.settings.copy(adjustmentStep=2)),{initialized=true;Outcome.Success(initial())},token),FailureCode.INVALID_INPUT)
            assertFalse(initialized)
            fails(store.create(input("different-operation"),{Outcome.Success(initial(input("different-operation")))},token),FailureCode.PERSISTENCE)
            assertEquals(first,ok(store.read(first.checkpoint.snapshot.id,token)))
            assertEquals(1L,db.scalar("SELECT count(*) FROM life_seat_search"))
        }
    }
    @Test fun unknownGameIsTypedNotFound()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            val o=SqlLifeGameStore(db,codec).read(GameId(provider,"missing"),token)
            assertEquals(UnavailableReason.NOT_FOUND,(o as Outcome.Unavailable).reason)
        }
    }
    @Test fun invalidInitialSnapshotsNeverPersistPartialRows()=runBlocking {
        val original=initial()
        val invalid=listOf(original.copy(snapshot=original.snapshot.copy(revision=Revision(1))),
            original.copy(snapshot=original.snapshot.copy(settings=input().settings.copy(adjustmentStep=2))),
            original.copy(snapshot=original.snapshot.copy(lastEventId=Knowledge.Unknown("Incomplete setup",SnapshotList.empty()))),
            original.copy(snapshot=original.snapshot.copy(counters=SnapshotList.of(CounterState(EntityId("life"),EntityId("seat"),null,19,false)))),
            original.copy(snapshot=original.snapshot.copy(seats=SnapshotList.of(input().seats.single().copy(playerName="Wrong")))))
        for(checkpoint in invalid) HostLifeDatabase(folder.newFile()).use { db ->
            fails(SqlLifeGameStore(db,codec).create(input(),{Outcome.Success(checkpoint)},token),FailureCode.INVALID_INPUT)
            assertEquals(0L,db.scalar("SELECT count(*) FROM life_game"));assertEquals(0L,db.scalar("SELECT count(*) FROM life_seat_search"))
        }
    }
    @Test fun initializerUnavailablePropagatesWithoutCreatingRows()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            val o=SqlLifeGameStore(db,codec).create(input(),{Outcome.Unavailable(UnavailableReason.UNSUPPORTED,"Authentic variant initialization unavailable")},token)
            assertEquals(UnavailableReason.UNSUPPORTED,(o as Outcome.Unavailable).reason)
            assertEquals(0L,db.scalar("SELECT count(*) FROM life_game"))
        }
    }
    @Test fun cancellationAfterParentAndAfterSeatWriteRollsBackWholeCreation()=runBlocking {
        for(stopAt in listOf(1,2)) HostLifeDatabase(folder.newFile()).use { db ->
            var writes=0;db.afterWrite={if(++writes==stopAt)token.stopped=true}
            try {SqlLifeGameStore(db,codec).create(input(),{Outcome.Success(initial())},token);fail("Cancellation swallowed")}
            catch(expected:CancellationException){}
            token.stopped=false
            assertEquals(0L,db.scalar("SELECT count(*) FROM life_game"));assertEquals(0L,db.scalar("SELECT count(*) FROM life_seat_search"))
        }
    }
    @Test fun corruptCoreAndSeatProjectionsAreRejected()=runBlocking {
        for(mutation in listOf("UPDATE life_game SET creation_sha256='${"0".repeat(64)}'","UPDATE life_game SET checkpoint_payload=X'00'",
            "UPDATE life_game SET revision=1","UPDATE life_game SET checkpoint_sequence=-1","UPDATE life_game SET started_nano=1000000000",
            "UPDATE life_game SET ended_seconds=10","UPDATE life_seat_search SET player_name_folded='WRONG'",
            "UPDATE life_seat_search SET deck_id=NULL","DELETE FROM life_seat_search")) HostLifeDatabase(folder.newFile()).use { db ->
                val store=SqlLifeGameStore(db,codec);val game=create(store);db.sql(mutation)
                fails(store.read(game.checkpoint.snapshot.id,token),FailureCode.CORRUPT_DATA)
            }
    }
    @Test fun invalidUtf8AndOversizedMetadataAreRejectedBeforePayloadFetch()=runBlocking {
        for(mutation in listOf("UPDATE life_game SET status=CAST(X'FF' AS TEXT)","UPDATE life_game SET creation_operation_id=CAST(zeroblob(65537) AS TEXT)",
            "UPDATE life_game SET creation_payload=zeroblob(4194305)","UPDATE life_game SET checkpoint_payload=zeroblob(4259841)")) HostLifeDatabase(folder.newFile()).use { db ->
                val store=SqlLifeGameStore(db,codec);val game=create(store);db.sql(mutation);db.blobQueries=0
                fails(store.read(game.checkpoint.snapshot.id,token),FailureCode.CORRUPT_DATA)
                assertEquals(0,db.blobQueries)
            }
    }
    @Test fun retryDoesNotDependOnCurrentWriterEncodingOrInitializeAgain()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            val first=create(SqlLifeGameStore(db,codec))
            val readerOnly=object:LifePayloadCodecPort by codec {
                override fun encodeCreation(value:GameCreation):Outcome<SnapshotBytes> = throw AssertionError("Matching retry encoded again")
            }
            val retry=ok(SqlLifeGameStore(db,readerOnly).create(input(),{throw AssertionError("Matching retry initialized")},token))
            assertEquals(first,retry)
        }
    }
    @Test fun compositeIdentitiesAndSqlPunctuationNeverAlias()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            val store=SqlLifeGameStore(db,codec)
            val keys=listOf(GameId(ProviderId("a:b"),"c';--"),GameId(ProviderId("a"),"b:c';--"))
            for((index,key) in keys.withIndex()) {
                val input=input("operation'$index")
                val checkpoint=initial(input).let{it.copy(snapshot=it.snapshot.copy(id=key))}
                ok(store.create(input,{Outcome.Success(checkpoint)},token))
            }
            for(key in keys)assertEquals(key,ok(store.read(key,token)).checkpoint.snapshot.id)
            assertEquals(2L,db.scalar("SELECT count(*) FROM life_game"))
        }
    }
    @Test fun failureOnLaterSeatInsertRollsBackNewParentAndPreservesExistingGame()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            val store=SqlLifeGameStore(db,codec);val retained=create(store,input("retained"),"retained")
            db.sql("CREATE TRIGGER fail_second_seat BEFORE INSERT ON life_seat_search WHEN NEW.seat_id='seat2' BEGIN SELECT RAISE(ABORT,'synthetic failure'); END")
            val base=input("new-operation")
            val two=base.copy(seats=SnapshotList.of(base.seats.single(),base.seats.single().copy(id=EntityId("seat2"),order=1)))
            fails(store.create(two,{Outcome.Success(initial(two,"new-game"))},token),FailureCode.PERSISTENCE)
            assertEquals(1L,db.scalar("SELECT count(*) FROM life_game"));assertEquals(1L,db.scalar("SELECT count(*) FROM life_seat_search"))
            assertEquals(retained,ok(store.read(retained.checkpoint.snapshot.id,token)))
        }
    }
    @Test fun corruptOptionalAndUtf8SeatMetadataCannotMasqueradeAsAbsenceOrValidText()=runBlocking {
        for(mutation in listOf("UPDATE life_game SET ended_seconds=X'FF',ended_nano=X'FF'",
            "UPDATE life_seat_search SET deck_source=X'FF',deck_id=X'FF',deck_name=X'FF',deck_name_folded=X'FF'",
            "UPDATE life_seat_search SET player_name=CAST(X'FF' AS TEXT),player_name_folded=CAST(X'FF' AS TEXT)",
            "UPDATE life_seat_search SET deck_name_folded=CAST(zeroblob(262145) AS TEXT)")) HostLifeDatabase(folder.newFile()).use { db ->
                val base=input();val input=base.copy(seats=SnapshotList.of(base.seats.single().copy(playerName="\ufffd")))
                val store=SqlLifeGameStore(db,codec);val first=create(store,input);db.sql(mutation);db.blobQueries=0
                fails(store.read(first.checkpoint.snapshot.id,token),FailureCode.CORRUPT_DATA);assertEquals(0,db.blobQueries)
            }
    }
    @Test fun contradictoryCurrentGraphsBlockReadAndLazyRetry()=runBlocking {
        val original=initial().snapshot
        val missing=EntityId("missing")
        val invalid=listOf(
            original.copy(settings=original.settings.copy(counterDefinitions=SnapshotList.copyOf(original.settings.counterDefinitions.map{it.copy(role=CounterRole.OTHER)}))),
            original.copy(counters=SnapshotList.of(CounterState(EntityId("life"),missing,null,20,false))),
            original.copy(clock=original.clock.copy(paused=false)),
            original.copy(designations=GameDesignations(monarch=missing)),
            original.copy(result=Knowledge.Known(GameResult(EntityId("result"),original.id,SnapshotList.of(missing),stamp,"bad",Revision(0),false))),
            original.copy(elapsed=original.elapsed.copy(paused=false)))
        for(snapshot in invalid) HostLifeDatabase(folder.newFile()).use { db ->
            val store=SqlLifeGameStore(db,codec);create(store)
            val bytes=ok(codec.encodeCheckpoint(initial().copy(snapshot=snapshot))).copyBytes()
            db.connection.prepare("UPDATE life_game SET checkpoint_payload=?").use {it.bindBlob(1,bytes);it.step()}
            fails(store.read(original.id,token),FailureCode.CORRUPT_DATA)
            fails(store.create(input(),{throw AssertionError("Corrupt retry initialized")},token),FailureCode.CORRUPT_DATA)
            assertEquals(1L,db.scalar("SELECT count(*) FROM life_game"))
        }
    }
    @Test fun invalidVariantPhaseFromInitializerLeavesNoRows()=runBlocking {
        val (input,checkpoint)=variant()
        val bad=checkpoint.copy(snapshot=checkpoint.snapshot.copy(variants=SnapshotList.of(checkpoint.snapshot.variants.single().copy(phase="missing"))))
        HostLifeDatabase(folder.newFile()).use { db ->
            fails(SqlLifeGameStore(db,codec).create(input,{Outcome.Success(bad)},token),FailureCode.INVALID_INPUT)
            assertEquals(0L,db.scalar("SELECT count(*) FROM life_game"))
            assertEquals(0L,db.scalar("SELECT count(*) FROM life_seat_search"))
        }
    }
    @Test fun corruptedVariantGraphIsRejectedButUnknownElapsedLineageIsPreserved()=runBlocking {
        val (input,checkpoint)=variant()
        HostLifeDatabase(folder.newFile()).use { db ->
            val store=SqlLifeGameStore(db,codec);ok(store.create(input,{Outcome.Success(checkpoint)},token))
            val legacy=checkpoint.copy(snapshot=checkpoint.snapshot.copy(elapsed=checkpoint.snapshot.elapsed.copy(turnLineageId=Knowledge.Unknown("Legacy",SnapshotList.empty()))))
            fun write(value:GameCheckpoint) { db.connection.prepare("UPDATE life_game SET checkpoint_payload=?").use {it.bindBlob(1,ok(codec.encodeCheckpoint(value)).copyBytes());it.step()} }
            write(legacy);assertEquals(legacy,ok(store.read(checkpoint.snapshot.id,token)).checkpoint)
            val badState=legacy.snapshot.variants.single().copy(drawn=SnapshotList.of(ProviderIdentity(provider,"variant-card","missing")))
            write(legacy.copy(snapshot=legacy.snapshot.copy(variants=SnapshotList.of(badState))))
            fails(store.read(checkpoint.snapshot.id,token),FailureCode.CORRUPT_DATA)
        }
    }
    private fun variant():Pair<GameCreation,GameCheckpoint> {
        val unknown=Knowledge.Absent("Synthetic source absence")
        val provenance=Provenance(provider,null,unknown,unknown,stamp,unknown,null,null,null)
        val notice=SourceNotice(provider,"Synthetic variant",unknown,"Synthetic fixture",null)
        val card=VariantCard(ProviderIdentity(provider,"variant-card","card"),"Card","Text",unknown,SnapshotList.empty(),provenance)
        val definition=VariantDefinition(ProviderIdentity(provider,"variant","definition"),VariantNamespace.CUSTOM,"v1","Synthetic setup",
            SnapshotList.empty(),SnapshotList.of(VariantZoneDefinition("deck","Deck",1,true)),SnapshotList.of("setup"),SnapshotList.of(card),provenance,notice)
        val base=input();val input=base.copy(settings=base.settings.copy(variantDefinitions=SnapshotList.of(definition)))
        val state=VariantState(definition.id,"v1",SnapshotList.of(VariantZone("deck",SnapshotList.of(card.id))),SnapshotList.empty(),SnapshotList.empty(),
            "setup",SnapshotList.empty(),7,SnapshotList.empty())
        val initial=initial(input)
        return input to initial.copy(snapshot=initial.snapshot.copy(variants=SnapshotList.of(state)))
    }
    @Test fun variantSetupCannotDropRequiredStateZonesOrReferences()=runBlocking {
        val (input,checkpoint)=variant();val state=checkpoint.snapshot.variants.single()
        val invalid=listOf(SnapshotList.empty(),SnapshotList.of(state,state),SnapshotList.of(state.copy(version="wrong")),
            SnapshotList.of(state.copy(zones=SnapshotList.empty())),
            SnapshotList.of(state.copy(zones=SnapshotList.of(state.zones.single(),state.zones.single()))),
            SnapshotList.of(state.copy(zones=SnapshotList.of(VariantZone("unknown",SnapshotList.empty())))),
            SnapshotList.of(state.copy(zones=SnapshotList.of(VariantZone("deck",SnapshotList.of(ProviderIdentity(provider,"variant-card","unknown")))))),
            SnapshotList.of(state.copy(zones=SnapshotList.of(VariantZone("deck",SnapshotList.of(state.zones.single().cardIds.single(),state.zones.single().cardIds.single()))))))
        for(states in invalid) HostLifeDatabase(folder.newFile()).use { db ->
            fails(SqlLifeGameStore(db,codec).create(input,{Outcome.Success(checkpoint.copy(snapshot=checkpoint.snapshot.copy(variants=states)))},token),FailureCode.INVALID_INPUT)
            assertEquals(0L,db.scalar("SELECT count(*) FROM life_game"))
        }
        HostLifeDatabase(folder.newFile()).use { db ->
            val store=SqlLifeGameStore(db,codec);val saved=ok(store.create(input,{Outcome.Success(checkpoint)},token))
            assertEquals(checkpoint,ok(store.read(saved.checkpoint.snapshot.id,token)).checkpoint)
        }
    }
    @Test fun nestedSnapshotBudgetRemainsIndependentOfCheckpointEnvelope()=runBlocking {
        HostLifeDatabase(folder.newFile()).use { db ->
            val base=input()
            val extra=(1..90).map { CounterDefinition(EntityId("x".repeat(23_300)+it),"Extra",null,null,0,CounterLossPolicy.None,true) }
            val input=base.copy(settings=base.settings.copy(counterDefinitions=SnapshotList.copyOf(base.settings.counterDefinitions+extra)))
            assertTrue(codec.encodeCreation(input) is Outcome.Success)
            val checkpoint=initial(input).let { c -> c.copy(snapshot=c.snapshot.copy(counters=SnapshotList.copyOf(extra.map {
                CounterState(it.id,input.seats.single().id,null,0,false)
            }))) }
            // Build a deliberately oversized inner frame using the test-visible wire, not the public bounded codec.
            val inner=encodeLifeFrame(1,4*1024*1024+65_536,snapshotWire,checkpoint.snapshot)
            assertTrue(inner.size>4*1024*1024)
            assertTrue(inner.size<4*1024*1024+65_536-1024) // Outer frame/lineage metadata fits with room to spare.
            fails(SqlLifeGameStore(db,codec).create(input,{Outcome.Success(checkpoint)},token),FailureCode.INVALID_INPUT)
            assertEquals(0L,db.scalar("SELECT count(*) FROM life_game"))
        }
    }

}
