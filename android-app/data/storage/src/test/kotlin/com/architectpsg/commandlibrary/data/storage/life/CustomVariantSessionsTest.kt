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

/** Synthetic user-authored mechanics; no official module/source or manifest import evidence. */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomVariantSessionsTest {
    @get:Rule val folder=TemporaryFolder()
    private val f=LifeMutationFixtures
    private val codec=LifePayloadCodec()
    private val absent=Knowledge.Absent("Synthetic")
    private val provenance=Provenance(f.provider,null,absent,absent,UtcTimestamp(0),absent,null,null,null)
    private val a=ProviderIdentity(f.provider,"card","a")
    private val b=ProviderIdentity(f.provider,"card","b")
    private val definition=VariantDefinition(ProviderIdentity(f.provider,"variant","custom"),VariantNamespace.CUSTOM,"v1","Authored synthetic rules",
        SnapshotList.of(VariantActionDefinition("move","Move","Move one","deck","table",null),VariantActionDefinition("shuffle","Shuffle","Reorder deck","deck","deck",null)),
        SnapshotList.of(VariantZoneDefinition("table","Table",10,true),VariantZoneDefinition("deck","Deck",10,true)),SnapshotList.of("ready"),
        SnapshotList.of(VariantCard(a,"A","Synthetic",absent,SnapshotList.empty(),provenance),VariantCard(b,"B","Synthetic",absent,SnapshotList.empty(),provenance)),
        provenance,SourceNotice(f.provider,"Synthetic",absent,"User-authored test",null))
    private val initial=VariantState(definition.id,definition.version,SnapshotList.of(VariantZone("table",SnapshotList.empty()),VariantZone("deck",SnapshotList.of(a,a,b))),
        SnapshotList.empty(),SnapshotList.empty(),"ready",SnapshotList.empty(),77,SnapshotList.empty())
    private val manifest=CustomVariantManifest(definition,initial,SnapshotList.of(
        CustomVariantTransition("move",SnapshotList.of("ready"),"deck","table",CustomVariantSelection.Top(1),appendDrawn=true),
        CustomVariantTransition("shuffle",SnapshotList.of("ready"),"deck","deck",CustomVariantSelection.Shuffle)))
    private class Ids:IdSource { var calls=0; override fun nextId()=if(++calls==1) "game" else "custom-id-$calls" }
    private class Random:RandomSource { var calls=0; override fun nextInt(upperBound:Int):Int { calls++; return 0 } }
    private class Time:Clock { var calls=0; override fun utcNow():UtcTimestamp { calls++; return UtcTimestamp(1000) }; override fun monotonicNow()=0.seconds.also { calls++ } }
    private fun <T> ok(value:Outcome<T>):T { assertTrue("Expected success, got $value",value is Outcome.Success); return (value as Outcome.Success).value }
    private fun failed(value:Outcome<*>) { assertTrue("Expected INVALID_INPUT, got $value",value is Outcome.Failure); assertEquals(FailureCode.INVALID_INPUT,(value as Outcome.Failure).error.code) }
    private fun factory(db:Database,ids:Ids=Ids(),random:Random=Random(),time:Time=Time(),config:SnapshotList<CustomVariantManifest> = SnapshotList.of(manifest))=
        SqlLifeGameSessions.withCustomVariants(db,codec,ids,random,time,f.process,f.provider,config)
    private fun input()=f.input().copy(settings=f.input().settings.copy(variantDefinitions=SnapshotList.of(definition)))
    private fun shuffle(id:String,state:VariantState):GameAction = GameAction.RequestRandom(RandomRequest.VariantZoneShuffle(EntityId(id),definition.id,"shuffle","deck",
        "command-library.custom-policy-v1",(state.properties.single { it.key=="command-library.custom-policy-v1" }.value as VariantValue.Text).value))
    @Test fun publicFactoryComposesMoveShuffleUndoAndExactReopenedPolicyWithoutRedraw()=runBlocking<Unit> {
        val file=folder.newFile(); val ids=Ids(); val random=Random(); lateinit var saved:VariantState
        HostLifeDatabase(file).use { db ->
            val sessions=ok(factory(db,ids,random)); var current=ok(sessions.create(input(),f.token))
            assertTrue(initial.properties.isEmpty())
            assertEquals(VariantValue.Text("sha256:17b256ece4185d17f429e461229a1142a175ce949d39884044b3e76f6d21e8f8"),
                current.checkpoint.snapshot.variants.single().properties.single().value)
            current=ok(sessions.dispatch(f.id,GameAction.Variant(definition.id,"move",SnapshotList.empty()),current.checkpoint.snapshot.revision,f.token))
            val before=current.checkpoint.snapshot.variants.single()
            assertEquals(listOf(a,b),before.zones.single { it.zoneId=="deck" }.cardIds)
            val request=shuffle("shuffle-once",before)
            current=ok(sessions.dispatch(f.id,request,current.checkpoint.snapshot.revision,f.token)); assertEquals(1,random.calls)
            val archive=ok(SqlLifeEventReader(db,codec).archive(f.id,1,f.token))
            val result=archive.event.randomResults.single().value as RecordedRandomValue.VariantZoneShuffle
            assertEquals(listOf(a,b),result.beforeOrder); assertEquals(listOf(b,a),result.afterOrder)
            current=ok(sessions.undo(f.id,f.token)); saved=current.checkpoint.snapshot.variants.single()
            assertEquals(before,saved); assertEquals(1,random.calls)
            failed(sessions.dispatch(f.id,request,current.checkpoint.snapshot.revision,f.token)); assertEquals(1,random.calls)
        }
        HostLifeDatabase(file,false).use { db ->
            val sessions=ok(factory(db,ids,random)); var current=ok(sessions.resume(f.id,f.token))
            assertEquals(saved,current.checkpoint.snapshot.variants.single())
            current=ok(sessions.dispatch(f.id,shuffle("after-reopen",saved),current.checkpoint.snapshot.revision,f.token))
            assertEquals(2,random.calls)
            val changedConfigs=listOf(
                manifest.copy(initialState=initial.copy(seed=78)),
                manifest.copy(transitions=SnapshotList.copyOf(manifest.transitions.reversed())),
                manifest.copy(transitions=SnapshotList.copyOf(manifest.transitions.map { if(it.actionId=="move") it.copy(insertion=CustomVariantInsertion.TOP) else it })),
                manifest.copy(initialState=initial.copy(zones=SnapshotList.of(initial.zones[0],initial.zones[1].copy(cardIds=SnapshotList.of(a,b,a))))),
                manifest.copy(definition=definition.copy(instructions="Different explicit authored rules"))
            )
            for(config in changedConfigs) {
                val changed=ok(factory(db,ids,random,config=SnapshotList.of(config)))
                val unavailable=changed.dispatch(f.id,shuffle("changed-policy",current.checkpoint.snapshot.variants.single()),current.checkpoint.snapshot.revision,f.token)
                assertTrue("Expected unsupported changed configuration, got $unavailable",unavailable is Outcome.Unavailable)
                assertEquals(2,random.calls)
            }
            val plain=SqlLifeGameSessions(db,codec,ids,random,Time(),f.process,f.provider)
            assertTrue(plain.dispatch(f.id,shuffle("plain",current.checkpoint.snapshot.variants.single()),current.checkpoint.snapshot.revision,f.token) is Outcome.Unavailable)
            assertEquals(2,random.calls)
        }
    }
    @Test fun invalidConfigIsTypedBeforeClockIdsRandomOrDatabaseUse() {
        HostLifeDatabase(folder.newFile()).use { real ->
            var touched=false
            val db=object:Database by real { override suspend fun <T> transaction(action:suspend(DbTransaction)->T,cancel:CancelToken):Outcome<T> { touched=true; error("Must not touch database") } }
            val ids=Ids();val random=Random();val time=Time()
            val reserved=VariantProperty("command-library.custom-policy-v1",VariantValue.Flag(true))
            val badPhase=String(charArrayOf('\uD800'))
            val invalid=listOf(
                SnapshotList.of(manifest,manifest),
                SnapshotList.of(manifest.copy(transitions=SnapshotList.empty())),
                SnapshotList.of(manifest.copy(initialState=initial.copy(properties=SnapshotList.of(reserved)))),
                SnapshotList.of(manifest.copy(initialState=initial.copy(phase=badPhase))),
                SnapshotList.of(manifest.copy(definition=definition.copy(phases=SnapshotList.of("ready",badPhase)))),
                SnapshotList.of(manifest.copy(definition=definition.copy(cards=SnapshotList.copyOf(definition.cards+
                    definition.cards[0].copy(id=ProviderIdentity(f.provider,"card",badPhase)))))),
                SnapshotList.of(manifest.copy(transitions=SnapshotList.copyOf(manifest.transitions.map { it.copy(properties=SnapshotList.of(reserved)) }))),
                SnapshotList.of(manifest.copy(initialState=initial.copy(properties=SnapshotList.of(VariantProperty("huge",VariantValue.Text("x".repeat(65537))))))),
                SnapshotList.of(manifest.copy(initialState=initial.copy(properties=SnapshotList.copyOf((1..128).map { VariantProperty("property-$it",VariantValue.Text("x".repeat(65536))) })))),
                SnapshotList.copyOf((1..129).map { manifest.copy(definition=definition.copy(version="v$it")) })
            )
            for(config in invalid) failed(factory(db,ids,random,time,config))
            assertTrue(factory(db,ids,random,time,SnapshotList.of(manifest.copy(definition=definition.copy(namespace=VariantNamespace.PLANECHASE)))) is Outcome.Unavailable)
            assertFalse(touched);assertEquals(0,ids.calls);assertEquals(0,random.calls);assertEquals(0,time.calls)
        }
    }
    @Test fun authorizedDrawsRollBackOnSqlFailureAndCancellationAndRetryWithoutReservation()=runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val ids=Ids();val random=Random();val sessions=ok(factory(db,ids,random))
            ok(sessions.create(input(),f.token)); var current=ok(sessions.resume(f.id,f.token))
            val first=shuffle("sql-retry",current.checkpoint.snapshot.variants.single())
            db.sql("CREATE TRIGGER fail_custom BEFORE INSERT ON life_event BEGIN SELECT RAISE(ABORT,'synthetic custom event failure'); END")
            val rejected=sessions.dispatch(f.id,first,current.checkpoint.snapshot.revision,f.token)
            assertTrue(rejected is Outcome.Failure);assertEquals(FailureCode.PERSISTENCE,(rejected as Outcome.Failure).error.code)
            assertEquals(2,random.calls);assertEquals(current,ok(sessions.resume(f.id,f.token)))
            assertEquals(0L,db.scalar("SELECT count(*) FROM life_event"))
            db.sql("DROP TRIGGER fail_custom")
            current=ok(sessions.dispatch(f.id,first,current.checkpoint.snapshot.revision,f.token));assertEquals(4,random.calls)
            var stopped=false;var writes=0
            val token=object:CancelToken { override val isCancelled get()=stopped;override fun throwIfCancelled() { if(stopped) throw CancellationException("Synthetic custom cancellation") } }
            db.afterWrite={ if(++writes==2) stopped=true }
            val second=shuffle("cancel-retry",current.checkpoint.snapshot.variants.single())
            try { sessions.dispatch(f.id,second,current.checkpoint.snapshot.revision,token);fail("Cancellation swallowed") }
            catch(expected:CancellationException) { }
            db.afterWrite=null
            assertEquals(2,writes);assertEquals(6,random.calls)
            assertEquals(current,ok(sessions.resume(f.id,f.token)))
            assertEquals(1L,db.scalar("SELECT count(*) FROM life_event"))
            ok(sessions.dispatch(f.id,second,current.checkpoint.snapshot.revision,f.token));assertEquals(8,random.calls)
            assertEquals(2L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun customFactoryWatchPublishesCommittedLatestAfterSlowInitialCollector()=runTest {
        HostLifeDatabase(folder.newFile()).use { db ->
            val sessions=ok(factory(db));val initial=ok(sessions.create(input(),f.token))
            val release=CompletableDeferred<Unit>();val entered=CompletableDeferred<Unit>();val seen=mutableListOf<StoredLifeGame>()
            val watcher=launch(UnconfinedTestDispatcher(testScheduler)) {
                sessions.watch(f.id,f.token).take(2).collect { seen+=ok(it);if(seen.size==1) { entered.complete(Unit);release.await() } }
            }
            entered.await()
            val moved=ok(sessions.dispatch(f.id,GameAction.Variant(definition.id,"move",SnapshotList.empty()),Revision(0),f.token))
            val shuffled=ok(sessions.dispatch(f.id,shuffle("watched",moved.checkpoint.snapshot.variants.single()),Revision(1),f.token))
            assertEquals(listOf(initial),seen);release.complete(Unit);watcher.join()
            assertEquals(listOf(initial,shuffled),seen)
            val stored=ok(SqlLifeGameLifecycle(db,codec,Ids(),f.provider).read(f.id,f.token))
            assertEquals(stored,seen.last())
        }
    }

}
