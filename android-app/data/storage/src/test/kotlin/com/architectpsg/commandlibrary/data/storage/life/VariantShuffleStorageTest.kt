package com.architectpsg.commandlibrary.data.storage.life

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.feature.life.persistence.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Synthetic DTO packets exercise actual SQLite/codec/history, not positive public manager-factory composition. */
class VariantShuffleStorageTest {
    @get:Rule val folder=TemporaryFolder()
    private val f=LifeMutationFixtures
    private val codec=LifePayloadCodec()
    private val absent=Knowledge.Absent("Synthetic source absence")
    private val provenance=Provenance(f.provider,null,absent,absent,f.stamp,absent,null,null,null)
    private val a=ProviderIdentity(f.provider,"card","a"); private val b=ProviderIdentity(f.provider,"card","b")
    private val definition=VariantDefinition(ProviderIdentity(f.provider,"variant","synthetic-shuffle"),VariantNamespace.CUSTOM,"v1","Synthetic",
        SnapshotList.of(VariantActionDefinition("shuffle","Shuffle","Synthetic","deck","deck",3)),SnapshotList.of(VariantZoneDefinition("deck","Deck",3,true)),SnapshotList.of("ready"),
        SnapshotList.of(VariantCard(a,"Synthetic A","Synthetic",absent,SnapshotList.of("shuffle"),provenance),VariantCard(b,"Synthetic B","Synthetic",absent,SnapshotList.of("shuffle"),provenance)),
        provenance,SourceNotice(f.provider,"Synthetic",absent,"Mechanical fixture",null))
    private val initial=VariantState(definition.id,"v1",SnapshotList.of(VariantZone("deck",SnapshotList.of(a,a,b))),SnapshotList.empty(),SnapshotList.empty(),"ready",
        SnapshotList.of(VariantProperty("policy",VariantValue.Text("plan-v1"))),99,SnapshotList.empty())
    private val request=RandomRequest.VariantZoneShuffle(EntityId("request"),definition.id,"shuffle","deck","policy","plan-v1")
    private val action=GameAction.RequestRandom(request)
    private fun <T> ok(o:Outcome<T>):T { assertTrue("Expected success, got $o",o is Outcome.Success); return (o as Outcome.Success).value }
    private fun failed(o:Outcome<*>,code:FailureCode) { assertTrue("Expected $code, got $o",o is Outcome.Failure); assertEquals(code,(o as Outcome.Failure).error.code) }
    private suspend fun create(db:HostLifeDatabase):StoredLifeGame {
        val base=f.input(); val input=base.copy(settings=base.settings.copy(variantDefinitions=SnapshotList.of(definition)))
        return ok(SqlLifeGameStore(db,codec).create(input,{ Outcome.Success(f.initial(input).copy(snapshot=f.initial(input).snapshot.copy(variants=SnapshotList.of(initial)))) },f.token))
    }
    private fun packet(current:StoredLifeGame,event:Long):LifeActionWrite {
        val order=SnapshotList.of(b,a,a)
        val next=initial.copy(zones=SnapshotList.of(VariantZone("deck",order)),actionCounts=SnapshotList.of(VariantActionCount("shuffle",1)))
        val write=f.action(current,event,action) { it.copy(variants=SnapshotList.of(next)) }
        val value=RecordedRandomValue.VariantZoneShuffle(definition.id,"v1","shuffle","deck","policy","plan-v1",initial.zones[0].cardIds,order)
        return write.copy(archive=write.archive.copy(event=write.archive.event.copy(randomResults=SnapshotList.of(RecordedRandomResult(request.requestId,write.archive.event.at,value)))))
    }
    private suspend fun history(db:HostLifeDatabase):LifeRandomHistory? {
        var history:LifeRandomHistory?=null
        val result=SqlLifeGameMutator(db,codec).actionWithContext(f.id,null,{ context -> history=ok(context.randomPredecessor(request.requestId)); Outcome.Unavailable(UnavailableReason.NOT_FOUND,"Read-only synthetic callback") },f.token)
        assertTrue(result is Outcome.Unavailable); return history
    }
    @Test fun actualArchivePredecessorReopenAndUndoKeepRecordedOrderAndReserveIdentity()=runBlocking<Unit> {
        val file=folder.newFile(); lateinit var committed:StoredLifeGame
        HostLifeDatabase(file).use { db ->
            val initial=create(db); committed=ok(SqlLifeGameMutator(db,codec).action(f.id,Revision(0),initial.sequence,{ current,event -> Outcome.Success(packet(current,event)) },f.token))
            val archive=ok(SqlLifeEventReader(db,codec).archive(f.id,0,f.token)); assertEquals(listOf(b,a,a),(archive.event.randomResults.single().value as RecordedRandomValue.VariantZoneShuffle).afterOrder)
            assertEquals(request,history(db)!!.original)
        }
        HostLifeDatabase(file,false).use { db ->
            val m=SqlLifeGameMutator(db,codec); assertEquals(committed,ok(m.readCurrent(f.id,f.token)))
            val undone=ok(m.action(f.id,Revision(1),committed.sequence,{ current,event -> Outcome.Success(f.action(current,event,action,Knowledge.Known(0L)) { it.copy(variants=SnapshotList.of(initial)) }) },f.token))
            assertEquals(initial,undone.checkpoint.snapshot.variants.single()); assertEquals(request,history(db)!!.original)
            var draws=0; val random=object:RandomSource { override fun nextInt(upperBound:Int):Int { draws++; return 0 } }
            val ids=object:IdSource { override fun nextId()="unused-fresh" }
            failed(SqlLifeGameCommands(db,codec,ids,random).dispatch(f.id,GameAction.RequestRandom(RandomRequest.Coin(request.requestId)),Revision(2),GameTimeObservation(kotlin.time.Duration.ZERO,f.stamp,f.process),f.token),FailureCode.INVALID_INPUT)
            assertEquals(0,draws); assertEquals(2L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun corruptOldShuffleFailsHistoryBeforePublicSourceOrMissingAuthorityFallback()=runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val created=create(db); val m=SqlLifeGameMutator(db,codec)
            val shuffled=ok(m.action(f.id,Revision(0),created.sequence,{ current,event -> Outcome.Success(packet(current,event)) },f.token))
            val current=ok(m.action(f.id,Revision(1),shuffled.sequence,{ old,event -> Outcome.Success(f.action(old,event,GameAction.SetAdjustmentStep(2))) },f.token))
            val archive=ok(SqlLifeEventReader(db,codec).archive(f.id,0,f.token)); val record=archive.event.randomResults.single(); val value=record.value as RecordedRandomValue.VariantZoneShuffle
            val corrupt=archive.copy(event=archive.event.copy(randomResults=SnapshotList.of(record.copy(value=value.copy(beforeOrder=SnapshotList.of(a,b,a))))))
            db.connection.prepare("UPDATE life_event SET archive_payload=? WHERE event_id=0").use { it.bindBlob(1,ok(codec.encodeArchive(corrupt)).copyBytes()); it.step() }
            var draws=0; var identities=0
            val c=SqlLifeGameCommands(db,codec,object:IdSource { override fun nextId():String { identities++; return "unused" } },object:RandomSource { override fun nextInt(upperBound:Int):Int { draws++; return 0 } })
            failed(c.dispatch(f.id,action,Revision(2),GameTimeObservation(kotlin.time.Duration.ZERO,f.stamp,f.process),f.token),FailureCode.CORRUPT_DATA)
            assertEquals(0,draws); assertEquals(0,identities); assertEquals(current,ok(m.readCurrent(f.id,f.token)))
        }
    }
    @Test fun publicCommandWithoutInstalledAuthorityNeverDrawsOrWrites()=runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val initial=create(db); var draws=0
            val c=SqlLifeGameCommands(db,codec,object:IdSource { override fun nextId()="fresh-group" },object:RandomSource { override fun nextInt(upperBound:Int):Int { draws++; return 0 } })
            val result=c.dispatch(f.id,action,Revision(0),GameTimeObservation(kotlin.time.Duration.ZERO,f.stamp,f.process),f.token)
            assertTrue(result is Outcome.Unavailable); assertEquals(UnavailableReason.UNSUPPORTED,(result as Outcome.Unavailable).reason)
            assertEquals(0,draws); assertEquals(initial,ok(SqlLifeGameMutator(db,codec).readCurrent(f.id,f.token))); assertEquals(0L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun retryDoesNotReencodeCreationOrReplaceOriginalStoredBytesAndHash()=runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val created=create(db); var encodes=0
            val spy=object:LifePayloadCodecPort by codec {
                override fun encodeCreation(value:GameCreation):Outcome<SnapshotBytes> { encodes++; return Outcome.Failure(DomainFailure(FailureCode.INTERNAL,"Unexpected creation reencoding",SnapshotList.empty())) }
            }
            val retry=ok(SqlLifeGameStore(db,spy).create(created.creation,{ error("Committed retry initialized again") },f.token))
            assertEquals(created,retry); assertEquals(0,encodes); assertEquals(created,ok(SqlLifeGameMutator(db,codec).readCurrent(f.id,f.token)))
        }
    }
}
