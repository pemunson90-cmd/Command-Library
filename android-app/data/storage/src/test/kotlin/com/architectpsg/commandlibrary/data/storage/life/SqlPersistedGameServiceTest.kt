package com.architectpsg.commandlibrary.data.storage.life

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.feature.life.persistence.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SqlPersistedGameServiceTest {
    @get:Rule val folder=TemporaryFolder()
    private val f=LifeMutationFixtures
    private val codec=LifePayloadCodec()
    private class Ids:IdSource { var n=0;override fun nextId()=if(++n==1)"game" else "service-$n" }
    private class Time:Clock { var n=0L;var calls=0;var armed=false
        override fun utcNow():UtcTimestamp { calls++;check(!armed);return UtcTimestamp(1000+n) }
        override fun monotonicNow()=n.seconds.also { calls++;check(!armed) }
    }
    private val random=object:RandomSource { override fun nextInt(upperBound:Int):Int=error("Unexpected random draw") }
    private fun service(db:Database,ids:Ids=Ids(),time:Time=Time()):PersistedGameService=SqlPersistedGameService(db,codec,ids,random,time,f.process,f.provider)
    private fun <T> ok(value:Outcome<T>):T { assertTrue("Expected success, got $value",value is Outcome.Success);return (value as Outcome.Success).value }
    private fun failed(value:Outcome<*>,code:FailureCode) { assertTrue("Expected $code, got $value",value is Outcome.Failure);assertEquals(code,(value as Outcome.Failure).error.code) }
    private fun GameSnapshot.counter()=counters.single { it.seatId==f.seat }.value
    private fun set(value:Int)=GameAction.SetCounter(f.seat,EntityId("life"),null,value)
    @Test fun actualPortMethodsComposeCommandsBothUndoFormsPagesAndFileReopen()=runTest {
        val file=folder.newFile();lateinit var saved:GameSnapshot
        HostLifeDatabase(file).use { db ->
            val persisted=service(db);val legacy:GameService=persisted
            val initial=ok(persisted.create(f.input(),f.token));assertEquals(initial,ok(persisted.read(f.id,f.token)))
            val edited=ok(legacy.dispatch(f.id,set(14),initial.revision,f.token));assertEquals(14,edited.counter())
            failed(persisted.undo(f.id,initial.revision,f.token),FailureCode.STALE_REVISION)
            assertEquals(edited,ok(persisted.read(f.id,f.token)))
            val undone=ok(legacy.undo(f.id,f.token));assertEquals(20,undone.counter())
            val finished=ok(persisted.dispatch(f.id,GameAction.Finish(SnapshotList.empty(),"Draw"),undone.revision,f.token))
            saved=ok(persisted.undo(f.id,finished.revision,f.token))
            assertTrue((saved.result as Knowledge.Known).value.retracted)
            val first=ok(persisted.events(f.id,PageRequest(2),f.token));val second=ok(persisted.events(f.id,PageRequest(2,first.nextCursor!!),f.token))
            assertEquals(listOf(0L,1L,2L,3L),(first.items+second.items).map { it.id });assertNull(second.nextCursor)
            assertEquals(Knowledge.Known(0L),first.items[1].undoOfEventId);assertEquals(Knowledge.Known(2L),second.items[1].undoOfEventId)
            val history=ok(persisted.history(GameHistoryFilter(playerName="A"),PageRequest(10),f.token))
            assertEquals(1L,history.totalMatches);assertEquals(saved.result,history.games.items.single().result)
            assertEquals(saved,ok(persisted.observe(f.id,f.token).first()));assertEquals(saved,legacy.watch(f.id).first())
        }
        HostLifeDatabase(file,false).use { db ->
            val persisted=service(db);assertEquals(saved,ok(persisted.read(f.id,f.token)));assertEquals(saved,ok(persisted.resume(f.id,f.token)))
            assertEquals(1L,ok(persisted.history(GameHistoryFilter(),PageRequest(10),f.token)).totalMatches)
        }
    }
    @Test fun readDoesNotSampleClockSettleRecoverOrWriteAndResumeDoes()=runTest {
        HostLifeDatabase(folder.newFile()).use { db ->
            val time=Time();val persisted=service(db,time=time)
            val initial=ok(persisted.create(f.input(),f.token))
            val running=ok(persisted.dispatch(f.id,GameAction.SetElapsedPaused(false),initial.revision,f.token))
            val calls=time.calls;var writes=0;db.afterWrite={ writes++ };time.n=10;time.armed=true
            assertEquals(running,ok(persisted.read(f.id,f.token)));assertEquals(calls,time.calls);assertEquals(0,writes)
            time.armed=false;val resumed=ok(persisted.resume(f.id,f.token))
            assertEquals(calls+2,time.calls);assertTrue(writes>0);assertEquals(10.seconds,resumed.elapsed.gameElapsed)
            db.afterWrite=null
        }
    }
    @Test fun typedObserveAndLegacyWatchPreserveEveryNonSuccessIncludingUnknown()=runTest {
        HostLifeDatabase(folder.newFile()).use { real ->
            val missing=service(real)
            val missingOutcome=missing.observe(f.id,f.token).toList().single()
            assertTrue(missingOutcome is Outcome.Unavailable);assertEquals(UnavailableReason.NOT_FOUND,(missingOutcome as Outcome.Unavailable).reason)
            val evidence=SnapshotList.of(Evidence("Synthetic unknown evidence",null,UtcTimestamp(1)))
            for(status:Outcome<Nothing> in listOf(Outcome.Unknown("Synthetic unknown",evidence),
                Outcome.Failure(DomainFailure(FailureCode.PERSISTENCE,"Synthetic failure",SnapshotList.empty())),
                Outcome.Unavailable(UnavailableReason.NOT_INSTALLED,"Synthetic missing module"))) {
                val db=object:Database by real { override suspend fun <T> transaction(action:suspend(DbTransaction)->T,cancel:CancelToken):Outcome<T> = status }
                val persisted=service(db)
                assertSame(status,persisted.observe(f.id,f.token).toList().single())
                assertSame(status,persisted.read(f.id,f.token));assertSame(status,persisted.history(GameHistoryFilter(),PageRequest(1),f.token))
                assertSame(status,persisted.events(f.id,PageRequest(1),f.token))
                try { persisted.watch(f.id).toList();fail("Legacy watch swallowed typed non-success") }
                catch(expected:LifeGameObservationException) { assertSame(status,expected.status) }
            }
        }
    }
    @Test fun cancellationEscapesBothObservationFormsWithoutWrapping()=runTest {
        HostLifeDatabase(folder.newFile()).use { real ->
            val db=object:Database by real { override suspend fun <T> transaction(action:suspend(DbTransaction)->T,cancel:CancelToken):Outcome<T> = throw CancellationException("Synthetic database cancellation") }
            val persisted=service(db)
            try { persisted.observe(f.id,f.token).toList();fail("Typed observe swallowed cancellation") } catch(expected:CancellationException) { }
            try { persisted.watch(f.id).toList();fail("Legacy watch swallowed cancellation") } catch(expected:CancellationException) { }
        }
    }
    @Test fun slowPortObserverReceivesInitialThenLatestAndCancellingIdleWatchReleasesIt()=runTest {
        HostLifeDatabase(folder.newFile()).use { db ->
            val persisted=service(db);val initial=ok(persisted.create(f.input(),f.token))
            val release=CompletableDeferred<Unit>();val entered=CompletableDeferred<Unit>();val seen=mutableListOf<GameSnapshot>()
            val watcher=launch(UnconfinedTestDispatcher(testScheduler)) {
                persisted.observe(f.id,f.token).take(2).collect { seen+=ok(it);if(seen.size==1) { entered.complete(Unit);release.await() } }
            }
            entered.await();ok(persisted.dispatch(f.id,set(15),Revision(0),f.token));val latest=ok(persisted.dispatch(f.id,set(10),Revision(1),f.token))
            assertEquals(listOf(initial),seen);release.complete(Unit);watcher.join();assertEquals(listOf(initial,latest),seen)
            val idleEntered=CompletableDeferred<Unit>()
            val idle=launch(UnconfinedTestDispatcher(testScheduler)) { persisted.watch(f.id).collect { idleEntered.complete(Unit) } }
            idleEntered.await();idle.cancelAndJoin()
            assertEquals(latest,ok(persisted.read(f.id,f.token)))
            assertEquals(8,ok(persisted.dispatch(f.id,set(8),latest.revision,f.token)).counter())
        }
    }
    @Test fun concurrentConditionalUndosCommitOnceAndRejectStaleExpectedRevision()=runTest {
        HostLifeDatabase(folder.newFile()).use { real ->
            var block=false;var calls=0;val entered=CompletableDeferred<Unit>();val allow=CompletableDeferred<Unit>()
            val db=object:Database by real {
                override suspend fun <T> transaction(action:suspend(DbTransaction)->T,cancel:CancelToken):Outcome<T> {
                    calls++;if(block) { block=false;entered.complete(Unit);allow.await() };return real.transaction(action,cancel)
                }
            }
            val time=Time();val persisted=service(db,time=time);val initial=ok(persisted.create(f.input(),f.token))
            val edited=ok(persisted.dispatch(f.id,set(14),initial.revision,f.token));block=true
            val first=async { persisted.undo(f.id,edited.revision,f.token) };runCurrent();entered.await()
            val beforeCalls=calls;val beforeClock=time.calls
            val second=async { persisted.undo(f.id,edited.revision,f.token) };runCurrent()
            assertEquals(beforeCalls,calls);assertEquals(beforeClock,time.calls)
            allow.complete(Unit);val undone=ok(first.await());failed(second.await(),FailureCode.STALE_REVISION)
            assertEquals(20,undone.counter());assertEquals(Revision(2),undone.revision)
            assertEquals(undone,ok(persisted.read(f.id,f.token)))
            val events=ok(persisted.events(f.id,PageRequest(10),f.token)).items
            assertEquals(2,events.size);assertEquals(Knowledge.Known(0L),events[1].undoOfEventId)
            assertEquals(2L,real.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun publicCustomServiceFactoryPersistsAuthorizedShuffleUndoAndReservationAcrossReopen()=runTest {
        val absent=Knowledge.Absent("Synthetic custom fixture")
        val provenance=Provenance(f.provider,null,absent,absent,UtcTimestamp(0),absent,null,null,null)
        val a=ProviderIdentity(f.provider,"card","a");val b=ProviderIdentity(f.provider,"card","b")
        val definition=VariantDefinition(ProviderIdentity(f.provider,"variant","service-custom"),VariantNamespace.CUSTOM,"v1","Synthetic rules",
            SnapshotList.of(VariantActionDefinition("shuffle","Shuffle","Reorder","deck","deck",null)),
            SnapshotList.of(VariantZoneDefinition("deck","Deck",2,true)),SnapshotList.of("ready"),
            SnapshotList.of(VariantCard(a,"A","Synthetic",absent,SnapshotList.empty(),provenance),VariantCard(b,"B","Synthetic",absent,SnapshotList.empty(),provenance)),
            provenance,SourceNotice(f.provider,"Synthetic",absent,"User-authored mechanical fixture",null))
        val initial=VariantState(definition.id,definition.version,SnapshotList.of(VariantZone("deck",SnapshotList.of(a,b))),SnapshotList.empty(),SnapshotList.empty(),
            "ready",SnapshotList.empty(),1,SnapshotList.empty())
        val manifest=CustomVariantManifest(definition,initial,SnapshotList.of(CustomVariantTransition("shuffle",SnapshotList.of("ready"),"deck","deck",CustomVariantSelection.Shuffle)))
        val file=folder.newFile();val ids=Ids();val time=Time();var draws=0
        val random=object:RandomSource { override fun nextInt(upperBound:Int):Int { draws++;return 0 } }
        fun factory(db:Database,configs:SnapshotList<CustomVariantManifest> = SnapshotList.of(manifest))=
            SqlPersistedGameService.withCustomVariants(db,codec,ids,random,time,f.process,f.provider,configs)
        fun request(id:String,state:VariantState):GameAction {
            val marker=(state.properties.single { it.key=="command-library.custom-policy-v1" }.value as VariantValue.Text).value
            return GameAction.RequestRandom(RandomRequest.VariantZoneShuffle(EntityId(id),definition.id,"shuffle","deck","command-library.custom-policy-v1",marker))
        }
        lateinit var saved:GameSnapshot
        HostLifeDatabase(file).use { db ->
            var writes=0;db.afterWrite={ writes++ }
            failed(factory(db,SnapshotList.of(manifest,manifest)),FailureCode.INVALID_INPUT)
            val persisted:PersistedGameService=ok(factory(db))
            assertEquals(0,time.calls);assertEquals(0,ids.n);assertEquals(0,draws);assertEquals(0,writes)
            val input=f.input().copy(settings=f.input().settings.copy(variantDefinitions=SnapshotList.of(definition)))
            val created=ok(persisted.create(input,f.token));val before=created.variants.single();val action=request("one",before)
            val shuffled=ok(persisted.dispatch(f.id,action,created.revision,f.token));assertEquals(1,draws)
            assertEquals(listOf(b,a),shuffled.variants.single().zones.single().cardIds)
            val recorded=ok(persisted.events(f.id,PageRequest(10),f.token)).items.single().randomResults.single().value as RecordedRandomValue.VariantZoneShuffle
            assertEquals(listOf(a,b),recorded.beforeOrder);assertEquals(listOf(b,a),recorded.afterOrder)
            saved=ok(persisted.undo(f.id,shuffled.revision,f.token));assertEquals(before,saved.variants.single());assertEquals(1,draws)
            failed(persisted.dispatch(f.id,action,saved.revision,f.token),FailureCode.INVALID_INPUT);assertEquals(1,draws)
            db.afterWrite=null
        }
        HostLifeDatabase(file,false).use { db ->
            val persisted:PersistedGameService=ok(factory(db));assertEquals(saved,ok(persisted.read(f.id,f.token)))
            val resumed=ok(persisted.resume(f.id,f.token))
            ok(persisted.dispatch(f.id,request("two",resumed.variants.single()),resumed.revision,f.token));assertEquals(2,draws)
            assertEquals(3,ok(persisted.events(f.id,PageRequest(10),f.token)).items.size)
        }
    }

}
