package com.architectpsg.commandlibrary.data.storage.life

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.feature.life.persistence.*
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SqlLifeGameCommandsTest {
    @get:Rule val folder = TemporaryFolder()
    private val f = LifeMutationFixtures
    private val codec = LifePayloadCodec()
    private class Ids : IdSource { var calls = 0; override fun nextId() = if(++calls == 1) "game" else "identity-$calls" }
    private class Random : RandomSource { var calls = 0; override fun nextInt(upperBound: Int): Int { calls++; return upperBound - 1 } }
    private fun reading(n: Long = 0, process: EntityId = f.process) = GameTimeObservation(n.seconds, UtcTimestamp(1000+n), process)
    private fun <T> ok(outcome: Outcome<T>): T { assertTrue("Expected success, got $outcome", outcome is Outcome.Success); return (outcome as Outcome.Success).value }
    private fun failed(outcome: Outcome<*>, code: FailureCode) { assertTrue("Expected $code, got $outcome", outcome is Outcome.Failure); assertEquals(code, (outcome as Outcome.Failure).error.code) }
    private fun life(db: HostLifeDatabase, ids: Ids) = SqlLifeGameLifecycle(db, codec, ids, f.provider)
    private fun StoredLifeGame.rev() = checkpoint.snapshot.revision
    private fun StoredLifeGame.counter() = checkpoint.snapshot.counters.single { it.seatId == f.seat }.value
    private fun set(n: Int) = GameAction.SetCounter(f.seat, EntityId("life"), null, n)
    @Test fun actualDispatchCanonicalGroupUndoAndFileReopenAreDurable() = runBlocking<Unit> {
        val file=folder.newFile(); lateinit var final: StoredLifeGame
        HostLifeDatabase(file).use { db ->
            val ids=Ids(); val life=life(db,ids); val commands=SqlLifeGameCommands(db,codec,ids,Random())
            val initial=ok(life.create(f.input(),reading(),f.token))
            val first=ok(commands.dispatch(f.id,set(14),initial.rev(),reading(1),f.token)); assertEquals(14,first.counter())
            val archived=ok(SqlLifeEventReader(db,codec).archive(f.id,0,f.token))
            assertEquals(GameAction.AdjustCounter(f.seat,EntityId("life"),null,-6),archived.event.action)
            assertEquals(Revision(0),archived.provenance.before.revision)
            val group=GameAction.Group(SnapshotList.of(set(8),GameAction.RenameSeat(f.seat,"Renamed")),EntityId("explicit-group"))
            val grouped=ok(commands.dispatch(f.id,group,first.rev(),reading(2),f.token)); assertEquals(8,grouped.counter())
            final=ok(commands.undo(f.id,null,reading(3),f.token)); assertEquals(14,final.counter()); assertEquals("A",final.checkpoint.snapshot.seats[0].playerName)
            val undo=ok(SqlLifeEventReader(db,codec).archive(f.id,2,f.token)); assertEquals(group.groupId,undo.event.groupId); assertEquals(Knowledge.Known(1L),undo.event.undoOfEventId)
        }
        HostLifeDatabase(file,false).use { db -> assertEquals(final,ok(life(db,Ids()).read(f.id,f.token))) }
    }
    @Test fun staleAndInvalidGroupNeverDrawOrCommitPartialEdits() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val ids=Ids(); val random=Random(); val life=life(db,ids); val c=SqlLifeGameCommands(db,codec,ids,random)
            val initial=ok(life.create(f.input(),reading(),f.token)); val changed=ok(c.dispatch(f.id,set(19),initial.rev(),reading(1),f.token)); val count=ids.calls
            val request=GameAction.RequestRandom(RandomRequest.Coin(EntityId("coin")))
            failed(c.dispatch(f.id,request,initial.rev(),reading(2),f.token),FailureCode.STALE_REVISION); assertEquals(count,ids.calls); assertEquals(0,random.calls)
            val invalid=GameAction.Group(SnapshotList.of(set(1),GameAction.RenameSeat(EntityId("missing"),"No")),EntityId("bad"))
            failed(c.dispatch(f.id,invalid,changed.rev(),reading(2),f.token),FailureCode.INVALID_INPUT)
            val mixed=GameAction.Group(SnapshotList.of(set(1),request),EntityId("mixed"))
            failed(c.dispatch(f.id,mixed,changed.rev(),reading(2),f.token),FailureCode.INVALID_INPUT); assertEquals(0,random.calls)
            assertEquals(changed,ok(life.read(f.id,f.token))); assertEquals(1L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun diceContinuationUsesCommittedHistoryAndUndoDoesNotReleaseRequestIdentity() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val ids=Ids(); val random=Random(); val life=life(db,ids); val c=SqlLifeGameCommands(db,codec,ids,random)
            var current=ok(life.create(f.input(),reading(),f.token)); val request=EntityId("dice")
            current=ok(c.dispatch(f.id,GameAction.RequestRandom(RandomRequest.Dice(request,10001,6)),current.rev(),reading(1),f.token))
            val first=ok(SqlLifeEventReader(db,codec).archive(f.id,0,f.token)).event.randomResults.single().value as RecordedRandomValue.Dice
            assertEquals(10000,first.faces.size); assertEquals(60000L,first.sum); assertEquals(1L,first.remaining)
            failed(c.dispatch(f.id,GameAction.RequestRandom(RandomRequest.ContinueDice(request,2)),current.rev(),reading(2),f.token),FailureCode.INVALID_INPUT); assertEquals(10000,random.calls)
            current=ok(c.dispatch(f.id,GameAction.RequestRandom(RandomRequest.ContinueDice(request,1)),current.rev(),reading(2),f.token)); assertEquals(10001,random.calls)
            val tail=ok(SqlLifeEventReader(db,codec).archive(f.id,1,f.token)).event.randomResults.single().value as RecordedRandomValue.Dice
            assertEquals(1,tail.faces.size); assertEquals(0L,tail.remaining)
            current=ok(c.undo(f.id,null,reading(3),f.token)); current=ok(c.undo(f.id,null,reading(4),f.token)); assertEquals(10001,random.calls)
            failed(c.dispatch(f.id,GameAction.RequestRandom(RandomRequest.Dice(request,10001,6)),current.rev(),reading(5),f.token),FailureCode.INVALID_INPUT)
            assertEquals(10001,random.calls); assertEquals(current,ok(life.read(f.id,f.token)))
        }
    }
    @Test fun finishUndoAndRefinishKeepOneResultIdentityAndExactEventRevision() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val ids=Ids(); val life=life(db,ids); val c=SqlLifeGameCommands(db,codec,ids,Random()); var current=ok(life.create(f.input(),reading(),f.token))
            current=ok(c.dispatch(f.id,GameAction.Finish(SnapshotList.of(f.seat),"Confirmed"),current.rev(),reading(1),f.token))
            val result=(current.checkpoint.snapshot.result as Knowledge.Known).value; assertEquals(current.rev(),result.revision)
            current=ok(c.undo(f.id,current.rev(),reading(2),f.token)); val retracted=(current.checkpoint.snapshot.result as Knowledge.Known).value
            assertEquals(result.id,retracted.id); assertTrue(retracted.retracted)
            val before=ids.calls
            current=ok(c.dispatch(f.id,GameAction.Finish(SnapshotList.empty(),"Draw"),current.rev(),reading(3),f.token))
            val confirmed=(current.checkpoint.snapshot.result as Knowledge.Known).value
            assertEquals(before+1,ids.calls); assertEquals(result.id,confirmed.id); assertEquals(current.rev(),confirmed.revision)
            assertTrue(confirmed.seatResults.all { it.result == SeatResultKind.DRAW }); assertEquals(1L,db.scalar("SELECT count(*) FROM life_result"))
        }
    }
    @Test fun randomDrawThenSqlFailureDoesNotReserveRequestAndRetryDrawsAgain() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val ids=Ids(); val random=Random(); val life=life(db,ids); val c=SqlLifeGameCommands(db,codec,ids,random)
            val initial=ok(life.create(f.input(),reading(),f.token)); val request=GameAction.RequestRandom(RandomRequest.Coin(EntityId("retry-coin")))
            db.sql("CREATE TRIGGER fail_command BEFORE INSERT ON life_event BEGIN SELECT RAISE(ABORT,'synthetic event failure'); END")
            failed(c.dispatch(f.id,request,initial.rev(),reading(1),f.token),FailureCode.PERSISTENCE)
            assertEquals(1,random.calls); assertEquals(initial,ok(life.read(f.id,f.token))); assertEquals(0L,db.scalar("SELECT count(*) FROM life_event"))
            db.sql("DROP TRIGGER fail_command")
            val committed=ok(c.dispatch(f.id,request,initial.rev(),reading(2),f.token)); assertEquals(2,random.calls)
            assertEquals(Revision(1),committed.rev()); assertEquals(1L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun groupedAddedDefinitionUndoRetainsFinalMetadataButHidesAllInitialStates() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val ids=Ids(); val life=life(db,ids); val c=SqlLifeGameCommands(db,codec,ids,Random()); val initial=ok(life.create(f.input(),reading(),f.token))
            val definition=CounterDefinition(EntityId("custom"),"First",0,100,3,CounterLossPolicy.None,true)
            val updated=definition.copy(label="Final",initial=4)
            val group=GameAction.Group(SnapshotList.of(GameAction.AddCounterDefinition(definition),GameAction.UpdateCounterDefinition(updated),
                GameAction.SetCounter(f.seat,definition.id,null,9)),EntityId("add-update-set"))
            val changed=ok(c.dispatch(f.id,group,initial.rev(),reading(1),f.token))
            val archive=ok(SqlLifeEventReader(db,codec).archive(f.id,0,f.token))
            assertTrue((archive.event.action as GameAction.Group).actions.last() is GameAction.AdjustCounter)
            val undone=ok(c.undo(f.id,changed.rev(),reading(2),f.token))
            assertEquals(updated,undone.checkpoint.snapshot.settings.counterDefinitions.last())
            assertTrue(undone.checkpoint.snapshot.counters.filter { it.definitionId==definition.id }.all { it.value==4 && it.hidden })
            assertEquals(2,undone.checkpoint.snapshot.counters.count { it.definitionId==definition.id })
        }
    }
    @Test fun foreignRunningProcessRequiresExplicitLifecycleRecoveryAndNeverChargesGap() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val ids=Ids(); val life=life(db,ids); val c=SqlLifeGameCommands(db,codec,ids,Random()); var current=ok(life.create(f.input(),reading(),f.token))
            current=ok(c.dispatch(f.id,GameAction.Group(SnapshotList.of(GameAction.SwitchTurn(f.seat),GameAction.SetElapsedPaused(false)),EntityId("start")),current.rev(),reading(1),f.token))
            current=ok(life.resume(f.id,reading(5),f.token)); val foreign=EntityId("foreign")
            failed(c.dispatch(f.id,set(10),current.rev(),reading(9000,foreign),f.token),FailureCode.INVALID_INPUT)
            assertEquals(current,ok(life.read(f.id,f.token)))
            val recovered=ok(life.resume(f.id,reading(9000,foreign),f.token)); assertTrue(recovered.checkpoint.snapshot.elapsed.paused)
            val edited=ok(c.dispatch(f.id,set(10),recovered.rev(),reading(9001,foreign),f.token))
            assertEquals(4.seconds,edited.checkpoint.snapshot.elapsed.gameElapsed); assertEquals(10,edited.counter())
        }
    }
    @Test fun invalidIdentityAndCancelledLateWriteRollbackEntireSemanticCommand() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val ids=Ids(); val life=life(db,ids); val initial=ok(life.create(f.input(),reading(),f.token))
            val broken=object:IdSource { override fun nextId()="" }
            failed(SqlLifeGameCommands(db,codec,broken,Random()).dispatch(f.id,set(1),initial.rev(),reading(1),f.token),FailureCode.INTERNAL)
            var stopped=false; var writes=0
            val token=object:CancelToken { override val isCancelled get()=stopped; override fun throwIfCancelled() { if(stopped) throw kotlinx.coroutines.CancellationException("Synthetic command cancellation") } }
            db.afterWrite={ if(++writes==2) stopped=true }
            try { SqlLifeGameCommands(db,codec,ids,Random()).dispatch(f.id,set(1),initial.rev(),reading(1),token); fail("Cancellation swallowed") }
            catch(expected:kotlinx.coroutines.CancellationException) { }
            db.afterWrite=null
            assertEquals(2,writes); assertEquals(initial,ok(life.read(f.id,f.token))); assertEquals(0L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun turnResetIsGlobalAndPriorityPassDoesNotRefillPerTurnBank() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val ids=Ids(); val life=life(db,ids); val c=SqlLifeGameCommands(db,codec,ids,Random()); val base=f.input()
            val reset=CounterDefinition(EntityId("reset"),"Reset",0,100,2,CounterLossPolicy.None,true,resetPolicy=CounterResetPolicy.ON_TURN_START)
            val input=base.copy(settings=base.settings.copy(clockMode=SeatClockMode.PER_TURN,counterDefinitions=SnapshotList.copyOf(base.settings.counterDefinitions+reset)),
                clock=base.clock.copy(mode=SeatClockMode.PER_TURN,seats=SnapshotList.copyOf(base.seats.map { SeatTime(it.id,20.seconds,20.seconds) })))
            var current=ok(life.create(input,reading(),f.token))
            val setup=GameAction.Group(SnapshotList.of(GameAction.SwitchTurn(f.seat),GameAction.ClockPause(false),GameAction.SetElapsedPaused(false),
                GameAction.SetCounter(f.seat,reset.id,null,9),GameAction.SetCounter(f.other,reset.id,null,8)),EntityId("setup"))
            current=ok(c.dispatch(f.id,setup,current.rev(),reading(1),f.token))
            current=ok(c.dispatch(f.id,GameAction.PassClock(f.other),current.rev(),reading(4),f.token))
            assertEquals(17.seconds,current.checkpoint.snapshot.clock.seats.single { it.seatId==f.seat }.remaining)
            assertEquals(listOf(9,8),current.checkpoint.snapshot.counters.filter { it.definitionId==reset.id }.map { it.value })
            current=ok(c.dispatch(f.id,GameAction.PassClock(f.seat),current.rev(),reading(6),f.token))
            assertEquals(17.seconds,current.checkpoint.snapshot.clock.seats.single { it.seatId==f.seat }.remaining)
            current=ok(c.dispatch(f.id,GameAction.SwitchTurn(f.other),current.rev(),reading(7),f.token))
            assertEquals(20.seconds,current.checkpoint.snapshot.clock.seats.single { it.seatId==f.other }.remaining)
            assertTrue(current.checkpoint.snapshot.counters.filter { it.definitionId==reset.id }.all { it.value==2 })
            assertEquals(6.seconds,current.checkpoint.snapshot.elapsed.gameElapsed); assertEquals(0.seconds,current.checkpoint.snapshot.elapsed.turnElapsed)
            assertEquals(4L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun acknowledgementAndDeliverySurviveFinishUndoWithoutChangingPriorResultRevision() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val ids=Ids(); val life=life(db,ids); val c=SqlLifeGameCommands(db,codec,ids,Random()); val base=f.input()
            val input=base.copy(settings=base.settings.copy(elapsedTimers=ElapsedTimerSettings(gameWarning=1.seconds)))
            var current=ok(life.create(input,reading(),f.token))
            current=ok(c.dispatch(f.id,GameAction.SetElapsedPaused(false),current.rev(),reading(),f.token))
            current=ok(life.resume(f.id,reading(2),f.token)); val alert=current.checkpoint.snapshot.alerts.single()
            ok(SqlLifeGameMutator(db,codec).markDelivered(f.id,alert.id,7,f.token))
            current=ok(c.dispatch(f.id,GameAction.Finish(SnapshotList.empty(),"Draw"),current.rev(),reading(2),f.token))
            val result=current.checkpoint.snapshot.result
            current=ok(c.dispatch(f.id,GameAction.AcknowledgeGameAlert(alert.id),current.rev(),reading(3),f.token))
            assertEquals(result,current.checkpoint.snapshot.result)
            current=ok(c.undo(f.id,null,reading(4),f.token))
            assertEquals(result,current.checkpoint.snapshot.result); assertNotNull(current.checkpoint.snapshot.alerts.single().acknowledgedAt)
            current=ok(c.undo(f.id,null,reading(5),f.token))
            assertTrue((current.checkpoint.snapshot.result as Knowledge.Known).value.retracted)
            assertEquals(2.seconds,current.checkpoint.snapshot.elapsed.gameElapsed); assertNotNull(current.checkpoint.snapshot.alerts.single().acknowledgedAt)
            assertEquals(7L,db.scalar("SELECT delivery_mask FROM life_alert")); assertEquals(1L,db.scalar("SELECT count(*) FROM life_alert"))
        }
    }
    @Test fun switchUndoAcrossLaterCheckpointPreservesElapsedAndUsesSelectedClockPolicy() = runBlocking<Unit> {
        for (mode in listOf(SeatClockMode.CHESS, SeatClockMode.PER_TURN)) {
            for (policy in ClockUndoPolicy.entries) {
                HostLifeDatabase(folder.newFile()).use { db ->
                    val ids=Ids(); val lifecycle=life(db,ids); val commands=SqlLifeGameCommands(db,codec,ids,Random())
                    val base=f.input()
                    val input=base.copy(settings=base.settings.copy(clockMode=mode),clock=base.clock.copy(mode=mode,
                        undoPolicy=policy,seats=SnapshotList.copyOf(base.seats.map { SeatTime(it.id,20.seconds,20.seconds) })))
                    var current=ok(lifecycle.create(input,reading(),f.token))
                    current=ok(commands.dispatch(f.id,GameAction.Group(SnapshotList.of(GameAction.SwitchTurn(f.other),
                        GameAction.ClockPause(false),GameAction.SetElapsedPaused(false)),EntityId("start")),current.rev(),reading(),f.token))
                    current=ok(commands.dispatch(f.id,GameAction.SwitchTurn(f.seat),current.rev(),reading(3),f.token))
                    val lineage=current.checkpoint.snapshot.elapsed.turnLineageId
                    current=ok(commands.dispatch(f.id,GameAction.SwitchTurn(f.other),current.rev(),reading(5),f.token))
                    val archive=ok(SqlLifeEventReader(db,codec).archive(f.id,2,f.token))
                    val switchedSequence=current.sequence
                    current=ok(lifecycle.resume(f.id,reading(9),f.token))
                    assertTrue(current.sequence>switchedSequence)
                    assertEquals(3L,db.scalar("SELECT count(*) FROM life_event"))
                    current=ok(commands.undo(f.id,current.rev(),reading(11),f.token))
                    val snapshot=current.checkpoint.snapshot
                    assertEquals(f.seat,snapshot.activeSeatId)
                    assertEquals(lineage,snapshot.elapsed.turnLineageId)
                    assertEquals(11.seconds,snapshot.elapsed.gameElapsed)
                    assertEquals(2.seconds,snapshot.elapsed.turnElapsed)
                    assertEquals(if(policy==ClockUndoPolicy.KEEP_ELAPSED)18.seconds else 12.seconds,
                        snapshot.clock.seats.single { it.seatId==f.seat }.remaining)
                    assertEquals(if(policy==ClockUndoPolicy.KEEP_ELAPSED)11.seconds else 17.seconds,
                        snapshot.clock.seats.single { it.seatId==f.other }.remaining)
                    assertEquals(archive,ok(SqlLifeEventReader(db,codec).archive(f.id,2,f.token)))
                    assertEquals(Knowledge.Known(2L),ok(SqlLifeEventReader(db,codec).archive(f.id,3,f.token)).event.undoOfEventId)
                }
            }
        }
    }
    @Test fun switchUndoAfterFileReopenAndForeignProcessRecoveryDoesNotChargeWallGap() = runBlocking<Unit> {
        val file=folder.newFile(); val ids=Ids(); lateinit var lineage: Knowledge<EntityId>
        HostLifeDatabase(file).use { db ->
            val lifecycle=life(db,ids); val commands=SqlLifeGameCommands(db,codec,ids,Random())
            var current=ok(lifecycle.create(f.input(),reading(),f.token))
            current=ok(commands.dispatch(f.id,GameAction.Group(SnapshotList.of(GameAction.SwitchTurn(f.seat),
                GameAction.SetElapsedPaused(false)),EntityId("start")),current.rev(),reading(),f.token))
            lineage=current.checkpoint.snapshot.elapsed.turnLineageId
            current=ok(commands.dispatch(f.id,GameAction.SwitchTurn(f.other),current.rev(),reading(5),f.token))
            ok(lifecycle.resume(f.id,reading(9),f.token))
        }
        HostLifeDatabase(file,false).use { db ->
            val lifecycle=life(db,ids); val commands=SqlLifeGameCommands(db,codec,ids,Random())
            val foreign=EntityId("restarted-process")
            val recovered=ok(lifecycle.resume(f.id,GameTimeObservation(0.seconds,UtcTimestamp(9_000_000),foreign),f.token))
            assertEquals(9.seconds,recovered.checkpoint.snapshot.elapsed.gameElapsed)
            val undone=ok(commands.undo(f.id,recovered.rev(),GameTimeObservation(1.seconds,UtcTimestamp(1),foreign),f.token))
            assertEquals(f.seat,undone.checkpoint.snapshot.activeSeatId)
            assertEquals(lineage,undone.checkpoint.snapshot.elapsed.turnLineageId)
            assertEquals(9.seconds,undone.checkpoint.snapshot.elapsed.gameElapsed)
            assertEquals(5.seconds,undone.checkpoint.snapshot.elapsed.turnElapsed)
            assertTrue(undone.checkpoint.snapshot.elapsed.paused)
            assertEquals(Knowledge.Known(1L),ok(SqlLifeEventReader(db,codec).archive(f.id,2,f.token)).event.undoOfEventId)
        }
    }
    @Test fun commandReopenUndoAndRefinishKeepStableResultAndPausedTimers() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val ids=Ids(); val lifecycle=life(db,ids); val commands=SqlLifeGameCommands(db,codec,ids,Random())
            var current=ok(lifecycle.create(f.input(),reading(),f.token))
            current=ok(commands.dispatch(f.id,GameAction.SetElapsedPaused(false),current.rev(),reading(),f.token))
            current=ok(commands.dispatch(f.id,GameAction.Finish(SnapshotList.of(f.seat),"First"),current.rev(),reading(5),f.token))
            val result=(current.checkpoint.snapshot.result as Knowledge.Known).value
            current=ok(commands.dispatch(f.id,GameAction.Reopen("Correction"),current.rev(),reading(9),f.token))
            assertTrue((current.checkpoint.snapshot.result as Knowledge.Known).value.retracted)
            assertTrue(current.checkpoint.snapshot.elapsed.paused); assertTrue(current.checkpoint.snapshot.clock.paused)
            assertEquals(5.seconds,current.checkpoint.snapshot.elapsed.gameElapsed)
            current=ok(commands.undo(f.id,current.rev(),reading(10),f.token))
            assertEquals(GameStatus.COMPLETED,current.checkpoint.snapshot.status)
            assertEquals(result.id,(current.checkpoint.snapshot.result as Knowledge.Known).value.id)
            assertEquals(result.copy(revision=current.rev()),(current.checkpoint.snapshot.result as Knowledge.Known).value)
            current=ok(commands.dispatch(f.id,GameAction.Reopen("Correction again"),current.rev(),reading(11),f.token))
            current=ok(commands.dispatch(f.id,set(12),current.rev(),reading(12),f.token))
            current=ok(commands.dispatch(f.id,GameAction.Finish(SnapshotList.empty(),"Corrected draw"),current.rev(),reading(13),f.token))
            val final=(current.checkpoint.snapshot.result as Knowledge.Known).value
            assertEquals(result.id,final.id); assertEquals(current.rev(),final.revision)
            assertEquals(5.seconds,current.checkpoint.snapshot.elapsed.gameElapsed)
            assertEquals(setOf(f.seat,f.other),final.seatResults.map { it.seatId }.toSet())
            assertEquals(2,final.seatResults.size)
            assertTrue(final.seatResults.all { it.result==SeatResultKind.DRAW })
            assertEquals(1L,db.scalar("SELECT count(*) FROM life_result"))
        }
    }
    @Test fun duplicateAcknowledgementPreservesFirstTimestampAndUnknownIdDoesNotCommit() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val ids=Ids(); val lifecycle=life(db,ids); val commands=SqlLifeGameCommands(db,codec,ids,Random())
            val base=f.input(); val input=base.copy(settings=base.settings.copy(elapsedTimers=ElapsedTimerSettings(gameWarning=1.seconds)))
            var current=ok(lifecycle.create(input,reading(),f.token))
            current=ok(commands.dispatch(f.id,GameAction.SetElapsedPaused(false),current.rev(),reading(),f.token))
            current=ok(lifecycle.resume(f.id,reading(2),f.token))
            val alert=current.checkpoint.snapshot.alerts.single()
            current=ok(commands.dispatch(f.id,GameAction.AcknowledgeGameAlert(alert.id),current.rev(),reading(3),f.token))
            val timestamp=current.checkpoint.snapshot.alerts.single().acknowledgedAt
            assertEquals(reading(3).wall,timestamp)
            current=ok(commands.dispatch(f.id,GameAction.AcknowledgeGameAlert(alert.id),current.rev(),reading(4),f.token))
            assertEquals(timestamp,current.checkpoint.snapshot.alerts.single().acknowledgedAt)
            assertEquals(1L,db.scalar("SELECT count(*) FROM life_alert"))
            val events=db.scalar("SELECT count(*) FROM life_event")
            val unknown=commands.dispatch(f.id,GameAction.AcknowledgeGameAlert(EntityId("unknown")),current.rev(),reading(5),f.token)
            assertTrue("Expected unavailable, got $unknown",unknown is Outcome.Unavailable)
            assertEquals(UnavailableReason.NOT_FOUND,(unknown as Outcome.Unavailable).reason)
            assertEquals(current,ok(lifecycle.read(f.id,f.token)))
            assertEquals(events,db.scalar("SELECT count(*) FROM life_event"))
        }
    }

    @Test fun countdownUndoAfterFileReopenPreservesDurableChargeAndPausesEveryModeAndPolicy() = runBlocking<Unit> {
        for(mode in listOf(SeatClockMode.CHESS,SeatClockMode.PER_TURN,SeatClockMode.SHARED)) {
            for(policy in ClockUndoPolicy.entries) {
                val file=folder.newFile();val ids=Ids();lateinit var lineage:Knowledge<EntityId>
                HostLifeDatabase(file).use { db ->
                    val lifecycle=life(db,ids);val commands=SqlLifeGameCommands(db,codec,ids,Random());val base=f.input()
                    val input=base.copy(settings=base.settings.copy(clockMode=mode),clock=base.clock.copy(mode=mode,undoPolicy=policy,
                        seats=if(mode==SeatClockMode.SHARED)SnapshotList.empty() else SnapshotList.copyOf(base.seats.map { SeatTime(it.id,20.seconds,20.seconds) }),
                        shared=if(mode==SeatClockMode.SHARED)SharedCountdown(20.seconds,20.seconds) else null))
                    var current=ok(lifecycle.create(input,reading(),f.token))
                    current=ok(commands.dispatch(f.id,GameAction.Group(SnapshotList.of(GameAction.SwitchTurn(f.other),GameAction.ClockPause(false),
                        GameAction.SetElapsedPaused(false)),EntityId("start")),current.rev(),reading(),f.token))
                    current=ok(commands.dispatch(f.id,GameAction.SwitchTurn(f.seat),current.rev(),reading(3),f.token))
                    lineage=current.checkpoint.snapshot.elapsed.turnLineageId
                    current=ok(commands.dispatch(f.id,GameAction.SwitchTurn(f.other),current.rev(),reading(5),f.token))
                    current=ok(lifecycle.resume(f.id,reading(9),f.token))
                    assertEquals(9.seconds,current.checkpoint.snapshot.elapsed.gameElapsed)
                    if(mode==SeatClockMode.SHARED)assertEquals(11.seconds,current.checkpoint.snapshot.clock.shared!!.remaining)
                }
                HostLifeDatabase(file,false).use { db ->
                    val lifecycle=life(db,ids);val commands=SqlLifeGameCommands(db,codec,ids,Random());val foreign=EntityId("foreign")
                    val recovered=ok(lifecycle.resume(f.id,GameTimeObservation(0.seconds,UtcTimestamp(9_000_000),foreign),f.token))
                    assertTrue(recovered.checkpoint.snapshot.clock.paused)
                    val undone=ok(commands.undo(f.id,recovered.rev(),GameTimeObservation(1.seconds,UtcTimestamp(1),foreign),f.token))
                    val snapshot=undone.checkpoint.snapshot
                    assertEquals(f.seat,snapshot.activeSeatId);assertEquals(lineage,snapshot.elapsed.turnLineageId)
                    assertEquals(9.seconds,snapshot.elapsed.gameElapsed);assertEquals(2.seconds,snapshot.elapsed.turnElapsed)
                    assertTrue(snapshot.clock.paused);assertTrue(snapshot.elapsed.paused)
                    assertEquals(foreign,snapshot.clock.anchor!!.processSessionId)
                    if(mode==SeatClockMode.SHARED)assertEquals(11.seconds,snapshot.clock.shared!!.remaining)
                    else {
                        assertEquals(18.seconds,snapshot.clock.seats.single { it.seatId==f.seat }.remaining)
                        assertEquals(if(policy==ClockUndoPolicy.KEEP_ELAPSED)13.seconds else 17.seconds,
                            snapshot.clock.seats.single { it.seatId==f.other }.remaining)
                    }
                    assertEquals(Knowledge.Known(2L),ok(SqlLifeEventReader(db,codec).archive(f.id,3,f.token)).event.undoOfEventId)
                    assertEquals(undone,ok(lifecycle.resume(f.id,GameTimeObservation(500.seconds,UtcTimestamp(800),foreign),f.token)))
                }
            }
        }
    }

}
