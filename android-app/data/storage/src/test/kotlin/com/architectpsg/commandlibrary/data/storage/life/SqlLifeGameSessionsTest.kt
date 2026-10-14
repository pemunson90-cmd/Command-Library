package com.architectpsg.commandlibrary.data.storage.life

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.feature.life.persistence.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SqlLifeGameSessionsTest {
    @get:Rule val folder=TemporaryFolder()
    private val f=LifeMutationFixtures
    private val codec=LifePayloadCodec()
    private class Ids:IdSource { var n=0; override fun nextId()=if(++n==1) "game" else "session-id-$n" }
    private class Random:RandomSource { var calls=0; override fun nextInt(upperBound:Int):Int { calls++; return 0 } }
    private class Time:Clock { var time=0L; override fun utcNow()=UtcTimestamp(1000+time); override fun monotonicNow()=time.seconds }
    private fun sessions(db:Database, ids:Ids=Ids(), random:Random=Random(), time:Time=Time())=SqlLifeGameSessions(db,codec,ids,random,time,f.process,f.provider)
    private fun <T> ok(o:Outcome<T>):T { assertTrue("Expected success, got $o",o is Outcome.Success); return (o as Outcome.Success).value }
    private fun failed(o:Outcome<*>,code:FailureCode) { assertTrue("Expected $code, got $o",o is Outcome.Failure); assertEquals(code,(o as Outcome.Failure).error.code) }
    private fun set(value:Int)=GameAction.SetCounter(f.seat,EntityId("life"),null,value)
    @Test fun watcherInitialCannotBeOvertakenAndCommittedLatestSurvivesSlowCollector()=runTest {
        HostLifeDatabase(folder.newFile()).use { db ->
            val s=sessions(db); val initial=ok(s.create(f.input(),f.token)); val seen=mutableListOf<StoredLifeGame>(); val initialEntered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
            val watcher=launch(UnconfinedTestDispatcher(testScheduler)) { s.watch(f.id,f.token).take(2).collect { value -> seen+=ok(value); if(seen.size==1) { initialEntered.complete(Unit); release.await() } } }
            initialEntered.await()
            val first=ok(s.dispatch(f.id,set(15),Revision(0),f.token)); val second=ok(s.dispatch(f.id,set(10),Revision(1),f.token))
            assertEquals(listOf(initial),seen); release.complete(Unit); watcher.join()
            assertEquals(listOf(initial,second),seen); assertEquals(Revision(2),second.checkpoint.snapshot.revision)
            assertEquals(second,ok(s.resume(f.id,f.token))); assertEquals(Revision(1),first.checkpoint.snapshot.revision)
        }
    }
    @Test fun failedCommandPublishesOnlyCommittedRecoveryAndNeverProposedCounter()=runTest {
        HostLifeDatabase(folder.newFile()).use { db ->
            val s=sessions(db); val initial=ok(s.create(f.input(),f.token)); val seen=mutableListOf<StoredLifeGame>()
            val watcher=launch(UnconfinedTestDispatcher(testScheduler)) { s.watch(f.id,f.token).take(2).collect { seen+=ok(it) } }
            val bad=GameAction.Group(SnapshotList.of(set(1),GameAction.RenameSeat(EntityId("missing"),"No")),EntityId("bad"))
            failed(s.dispatch(f.id,bad,Revision(0),f.token),FailureCode.INVALID_INPUT); watcher.join()
            assertEquals(2,seen.size); assertEquals(initial.checkpoint.snapshot,seen.last().checkpoint.snapshot)
            assertEquals(initial.sequence+1,seen.last().sequence); assertEquals(0L,db.scalar("SELECT count(*) FROM life_event"))
            assertEquals(seen.last(),ok(s.resume(f.id,f.token)))
        }
    }
    @Test fun sameGameQueuedCommandsSerializeBeforeDatabaseAndStaleRequestNeverDraws()=runTest {
        HostLifeDatabase(folder.newFile()).use { db ->
            var block=false; var calls=0; val entered=CompletableDeferred<Unit>(); val allow=CompletableDeferred<Unit>()
            val delayed=object:Database by db {
                override suspend fun <T> transaction(action:suspend(DbTransaction)->T,cancel:CancelToken):Outcome<T> { calls++; if(block) { block=false; entered.complete(Unit); allow.await() }; return db.transaction(action,cancel) }
            }
            val random=Random(); val s=sessions(delayed,random=random); ok(s.create(f.input(),f.token)); block=true
            val first=async { s.dispatch(f.id,set(15),Revision(0),f.token) }; runCurrent(); entered.await(); val before=calls
            val second=async { s.dispatch(f.id,GameAction.RequestRandom(RandomRequest.Coin(EntityId("stale"))),Revision(0),f.token) }; runCurrent()
            assertEquals(before,calls); allow.complete(Unit); ok(first.await()); failed(second.await(),FailureCode.STALE_REVISION); assertEquals(0,random.calls)
            assertEquals(Revision(1),ok(s.resume(f.id,f.token)).checkpoint.snapshot.revision)
        }
    }
    @Test fun repeatedCreateReturnsLatestDurableStateInsteadOfOriginalReceipt()=runTest {
        val file=folder.newFile(); lateinit var edited:StoredLifeGame
        HostLifeDatabase(file).use { db ->
            val ids=Ids(); val s=sessions(db,ids); ok(s.create(f.input(),f.token)); edited=ok(s.dispatch(f.id,set(12),Revision(0),f.token)); val calls=ids.n
            assertEquals(edited,ok(s.create(f.input(),f.token))); assertEquals(calls,ids.n)
        }
        HostLifeDatabase(file,false).use { db -> assertEquals(edited,ok(sessions(db).resume(f.id,f.token))) }
    }
    @Test fun missingWatcherReturnsTypedInitialFailureThenLaterCreatedGameLoadsNormally()=runTest {
        HostLifeDatabase(folder.newFile()).use { db ->
            val s=sessions(db); val missing=s.watch(f.id,f.token).toList(); assertEquals(1,missing.size); assertTrue(missing.single() is Outcome.Unavailable)
            val created=ok(s.create(f.input(),f.token)); assertEquals(created,ok(s.watch(f.id,f.token).first()))
            assertEquals(created,ok(s.watch(f.id,f.token).first()))
        }
    }
    @Test fun recoveryCommitIsPublishedEvenWhenCallerTokenCancelsBeforeUserCommand()=runTest {
        HostLifeDatabase(folder.newFile()).use { db ->
            var armed=false; var stopped=false
            val token=object:CancelToken { override val isCancelled get()=stopped; override fun throwIfCancelled() { if(stopped) throw CancellationException("Synthetic post-recovery cancellation") } }
            val adapter=object:Database by db {
                override suspend fun <T> transaction(action:suspend(DbTransaction)->T,cancel:CancelToken):Outcome<T> {
                    val result=db.transaction(action,cancel); if(armed) { armed=false; stopped=true }; return result
                }
            }
            val s=sessions(adapter); val initial=ok(s.create(f.input(),f.token)); val seen=mutableListOf<StoredLifeGame>()
            val watcher=launch(UnconfinedTestDispatcher(testScheduler)) { s.watch(f.id,f.token).take(2).collect { seen+=ok(it) } }
            armed=true
            try { s.dispatch(f.id,set(1),Revision(0),token); fail("Cancellation swallowed") } catch(expected:CancellationException) { }
            watcher.join(); assertEquals(2,seen.size); assertEquals(initial.sequence+1,seen.last().sequence)
            assertEquals(initial.checkpoint.snapshot,seen.last().checkpoint.snapshot); assertEquals(0L,db.scalar("SELECT count(*) FROM life_event"))
            assertEquals(seen.last(),ok(s.resume(f.id,f.token)))
        }
    }
    @Test fun differentGamesCanProgressWhileOtherGameWaitsBeforeDatabaseTransaction()=runTest {
        HostLifeDatabase(folder.newFile()).use { db ->
            var block=false; val entered=CompletableDeferred<Unit>(); val allow=CompletableDeferred<Unit>()
            val delayed=object:Database by db {
                override suspend fun <T> transaction(action:suspend(DbTransaction)->T,cancel:CancelToken):Outcome<T> { if(block) { block=false; entered.complete(Unit); allow.await() }; return db.transaction(action,cancel) }
            }
            val random=Random(); val s=sessions(delayed,random=random); val first=ok(s.create(f.input(),f.token)); val second=ok(s.create(f.input().copy(operationId=EntityId("second-creation")),f.token))
            assertNotEquals(first.checkpoint.snapshot.id,second.checkpoint.snapshot.id)
            block=true; val request=GameAction.RequestRandom(RandomRequest.Coin(EntityId("same-request")))
            val waiting=async { s.dispatch(first.checkpoint.snapshot.id,request,Revision(0),f.token) }; runCurrent(); entered.await()
            val other=ok(s.dispatch(second.checkpoint.snapshot.id,request,Revision(0),f.token)); assertEquals(Revision(1),other.checkpoint.snapshot.revision)
            assertFalse(waiting.isCompleted); allow.complete(Unit); ok(waiting.await()); assertEquals(2,random.calls)
            assertEquals(2L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun cancellingWatcherWithQueuedOperationsNeverSplitsGameLock()=runTest {
        HostLifeDatabase(folder.newFile()).use { db ->
            var block=false; var calls=0; val entered=CompletableDeferred<Unit>(); val allow=CompletableDeferred<Unit>()
            val adapter=object:Database by db {
                override suspend fun <T> transaction(action:suspend(DbTransaction)->T,cancel:CancelToken):Outcome<T> { calls++; if(block) { block=false; entered.complete(Unit); allow.await() }; return db.transaction(action,cancel) }
            }
            val s=sessions(adapter); ok(s.create(f.input(),f.token)); val watcher=launch(UnconfinedTestDispatcher(testScheduler)) { s.watch(f.id,f.token).collect { ok(it) } }
            block=true; val first=async { s.dispatch(f.id,set(15),Revision(0),f.token) }; runCurrent(); entered.await()
            watcher.cancel(); runCurrent(); val before=calls
            val next=async { s.dispatch(f.id,set(10),Revision(0),f.token) }; runCurrent(); assertEquals(before,calls)
            allow.complete(Unit); ok(first.await()); failed(next.await(),FailureCode.STALE_REVISION); watcher.join()
            assertEquals(Revision(1),ok(s.watch(f.id,f.token).first()).checkpoint.snapshot.revision)
        }
    }
}
