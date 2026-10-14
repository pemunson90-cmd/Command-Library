package com.architectpsg.commandlibrary.data.storage.life

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.feature.life.persistence.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration.Companion.seconds

class SqlLifeGameMutatorTest {
    @get:Rule val folder=TemporaryFolder()
    private val f=LifeMutationFixtures
    private val codec=LifePayloadCodec()
    private val token=f.token
    private fun <T> ok(o:Outcome<T>):T {assertTrue("Expected success, got $o",o is Outcome.Success);return(o as Outcome.Success<T>).value}
    private fun fails(o:Outcome<*>,code:FailureCode){assertTrue("Expected $code, got $o",o is Outcome.Failure);assertEquals(code,(o as Outcome.Failure).error.code)}
    private suspend fun create(db:HostLifeDatabase,input:GameCreation=f.input())=ok(SqlLifeGameStore(db,codec).create(input,{Outcome.Success(f.initial(input))},token))
    private suspend fun action(m:SqlLifeGameMutator,current:StoredLifeGame,build:(StoredLifeGame,Long)->LifeActionWrite={a,b->f.action(a,b)})=
        ok(m.action(f.id,current.checkpoint.snapshot.revision,current.sequence,{a,b->Outcome.Success(build(a,b))},token))

    @Test fun actionAndDatabaseReopenPreserveCreationAndImmutableArchive()=runBlocking {
        val file=folder.newFile()
        lateinit var committed:StoredLifeGame
        HostLifeDatabase(file).use {db ->
            val original=create(db);committed=action(SqlLifeGameMutator(db,codec),original)
            assertEquals(original.creation,committed.creation);assertEquals(Revision(1),committed.checkpoint.snapshot.revision);assertEquals(1L,committed.sequence)
            assertEquals(2,committed.checkpoint.snapshot.settings.adjustmentStep)
            val archive=ok(SqlLifeEventReader(db,codec).archive(f.id,0,token))
            assertEquals(original.checkpoint.snapshot,archive.provenance.before);assertEquals(committed.checkpoint.snapshot,archive.provenance.afterAction)
        }
        HostLifeDatabase(file,false).use {db ->assertEquals(committed,ok(SqlLifeGameStore(db,codec).read(f.id,token)))}
    }
    @Test fun staleRevisionAndSequenceRejectBeforeCallingEngine()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val current=action(SqlLifeGameMutator(db,codec),create(db));var called=false;val mutator=SqlLifeGameMutator(db,codec)
            for(pair in listOf(Revision(0) to current.sequence,current.checkpoint.snapshot.revision to 0L)) {
                fails(mutator.action(f.id,pair.first,pair.second,{a,b->called=true;Outcome.Success(f.action(a,b))},token),FailureCode.STALE_REVISION)
            }
            assertFalse(called);assertEquals(1L,db.scalar("SELECT count(*) FROM life_event"));assertEquals(current,ok(SqlLifeGameStore(db,codec).read(f.id,token)))
        }
    }
    @Test fun callbackFailureAndInvalidPacketDoNotWriteAnything()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val current=create(db);val m=SqlLifeGameMutator(db,codec)
            assertEquals(UnavailableReason.UNSUPPORTED,(m.action(f.id,Revision(0),0,{_,_->Outcome.Unavailable(UnavailableReason.UNSUPPORTED,"Engine unavailable")},token) as Outcome.Unavailable).reason)
            fails(m.action(f.id,Revision(0),0,{a,b->val p=f.action(a,b);Outcome.Success(p.copy(checkpoint=p.checkpoint.copy(snapshot=a.checkpoint.snapshot)))},token),FailureCode.INVALID_INPUT)
            assertEquals(0L,db.scalar("SELECT count(*) FROM life_event"));assertEquals(current,ok(SqlLifeGameStore(db,codec).read(f.id,token)))
        }
    }
    @Test fun timingCheckpointHasOwnSequenceAndLeavesOriginalActionAfterStateUntouched()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val m=SqlLifeGameMutator(db,codec);val start=action(m,create(db)){a,b->f.action(a,b,GameAction.SetElapsedPaused(false)){s->s.copy(elapsed=s.elapsed.copy(paused=false,anchor=ClockAnchor(f.stamp,0.seconds,f.process)))}}
            val archive=ok(SqlLifeEventReader(db,codec).archive(f.id,0,token))
            val checkpoint=ok(m.checkpoint(f.id,Revision(1),start.sequence,UtcTimestamp(1_010),{old->Outcome.Success(old.checkpoint.copy(snapshot=old.checkpoint.snapshot.copy(elapsed=old.checkpoint.snapshot.elapsed.copy(gameElapsed=5.seconds,anchor=ClockAnchor(UtcTimestamp(1_010),5.seconds,f.process)))))},token))
            assertEquals(Revision(1),checkpoint.checkpoint.snapshot.revision);assertEquals(2L,checkpoint.sequence);assertEquals(5.seconds,checkpoint.checkpoint.snapshot.elapsed.gameElapsed)
            assertEquals(archive,ok(SqlLifeEventReader(db,codec).archive(f.id,0,token)));assertEquals(1L,db.scalar("SELECT count(*) FROM life_event"))
            fails(m.action(f.id,Revision(1),start.sequence,{a,b->Outcome.Success(f.action(a,b))},token),FailureCode.STALE_REVISION)
        }
    }
    @Test fun resultRetractionAndRefinishKeepOneStableIdentity()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val m=SqlLifeGameMutator(db,codec);val first=action(m,create(db)){a,b->f.finish(a,b)}
            val reopened=action(m,first){a,b->f.reopen(a,b)}
            val again=action(m,reopened){a,b->f.finish(a,b)}
            assertEquals(1L,db.scalar("SELECT count(*) FROM life_result"));assertEquals(0L,db.scalar("SELECT retracted FROM life_result"))
            assertEquals((first.checkpoint.snapshot.result as Knowledge.Known).value.id,(again.checkpoint.snapshot.result as Knowledge.Known).value.id)
            assertTrue((reopened.checkpoint.snapshot.result as Knowledge.Known).value.retracted)
        }
    }
    @Test fun deliveredBitsAndAcknowledgmentSurviveUndoAndCheckpoint()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val m=SqlLifeGameMutator(db,codec);val finished=action(m,create(db)){a,b->f.finish(a,b,true)}
            ok(m.markDelivered(f.id,EntityId("alert"),3,token));ok(m.markDelivered(f.id,EntityId("alert"),4,token));ok(m.markDelivered(f.id,EntityId("alert"),1,token))
            assertEquals(7L,db.scalar("SELECT delivery_mask FROM life_alert"));assertEquals(finished,ok(SqlLifeGameStore(db,codec).read(f.id,token)))
            val ack=action(m,finished){a,b->f.action(a,b,GameAction.AcknowledgeGameAlert(EntityId("alert"))){s->s.copy(alerts=SnapshotList.of(s.alerts.single().copy(acknowledgedAt=UtcTimestamp(999))))}}
            val undone=action(m,ack){a,b->f.action(a,b,GameAction.AcknowledgeGameAlert(EntityId("alert")),Knowledge.Known(1L)){it}}
            val checkpoint=ok(m.checkpoint(f.id,undone.checkpoint.snapshot.revision,undone.sequence,UtcTimestamp(1_020),{Outcome.Success(it.checkpoint)},token))
            assertEquals(UtcTimestamp(999),checkpoint.checkpoint.snapshot.alerts.single().acknowledgedAt)
            assertEquals(999L,db.scalar("SELECT acknowledged_seconds FROM life_alert"));assertEquals(7L,db.scalar("SELECT delivery_mask FROM life_alert"))
        }
    }
    @Test fun duplicateUndoCannotTargetSameCommittedEventTwice()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val m=SqlLifeGameMutator(db,codec);val first=action(m,create(db))
            val undone=action(m,first){a,b->f.action(a,b,GameAction.SetAdjustmentStep(2),Knowledge.Known(0L)){s->s.copy(settings=s.settings.copy(adjustmentStep=1))}}
            fails(m.action(f.id,undone.checkpoint.snapshot.revision,undone.sequence,{a,b->Outcome.Success(f.action(a,b,undo=Knowledge.Known(0L)))},token),FailureCode.INVALID_INPUT)
            assertEquals(2L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun cancellationAtEveryAtomicWriteRollsBackAllTables()=runBlocking {
        for(stopAt in 1..6) HostLifeDatabase(folder.newFile()).use {db ->
            val original=create(db);var stopped=false;var writes=0
            val cancel=object:CancelToken {override val isCancelled get()=stopped;override fun throwIfCancelled(){if(stopped)throw java.util.concurrent.CancellationException("write cancellation")}}
            db.afterWrite={if(++writes==stopAt)stopped=true}
            try {SqlLifeGameMutator(db,codec).action(f.id,Revision(0),0,{a,b->Outcome.Success(f.finish(a,b,true))},cancel);fail("Cancellation swallowed at $stopAt")}catch(expected:java.util.concurrent.CancellationException){}
            db.afterWrite=null;assertEquals(stopAt,writes)
            assertEquals(original,ok(SqlLifeGameStore(db,codec).read(f.id,token)))
            for(table in listOf("life_event","life_result","life_alert"))assertEquals(0L,db.scalar("SELECT count(*) FROM $table"))
        }
    }
    @Test fun lateActualSqlFailureRollsBackParentEventSeatsAndResult()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val original=create(db);db.sql("CREATE TRIGGER fail_alert BEFORE INSERT ON life_alert BEGIN SELECT RAISE(ABORT,'synthetic late failure'); END")
            fails(SqlLifeGameMutator(db,codec).action(f.id,Revision(0),0,{a,b->Outcome.Success(f.finish(a,b,true))},token),FailureCode.PERSISTENCE)
            assertEquals(original,ok(SqlLifeGameStore(db,codec).read(f.id,token)))
            for(table in listOf("life_event","life_result","life_alert"))assertEquals(0L,db.scalar("SELECT count(*) FROM $table"))
        }
    }
    @Test fun checkpointCannotAlterNonTimingStateResumeOrChargePausedElapsed()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val current=create(db);val m=SqlLifeGameMutator(db,codec)
            val original=current.checkpoint
            val invalid=listOf(original.copy(snapshot=original.snapshot.copy(settings=original.snapshot.settings.copy(adjustmentStep=3))),
                original.copy(snapshot=original.snapshot.copy(elapsed=original.snapshot.elapsed.copy(paused=false,anchor=ClockAnchor(f.stamp,0.seconds,f.process)))),
                original.copy(snapshot=original.snapshot.copy(elapsed=original.snapshot.elapsed.copy(gameElapsed=1.seconds))),
                original.copy(snapshot=original.snapshot.copy(lastEventId=Knowledge.Known(4L))))
            for(next in invalid) fails(m.checkpoint(f.id,Revision(0),0,f.stamp,{Outcome.Success(next)},token),FailureCode.INVALID_INPUT)
            assertEquals(current,ok(SqlLifeGameStore(db,codec).read(f.id,token)))
        }
    }
    @Test fun actionCannotRefundAccumulatedGameTimeAfterInterveningCheckpoint()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val m=SqlLifeGameMutator(db,codec);val start=action(m,create(db)){a,b->f.action(a,b,GameAction.SetElapsedPaused(false)){s->s.copy(elapsed=s.elapsed.copy(paused=false,anchor=ClockAnchor(f.stamp,0.seconds,f.process)))}}
            val current=ok(m.checkpoint(f.id,Revision(1),start.sequence,UtcTimestamp(1_010),{old->Outcome.Success(old.checkpoint.copy(snapshot=old.checkpoint.snapshot.copy(elapsed=old.checkpoint.snapshot.elapsed.copy(gameElapsed=5.seconds))))},token))
            fails(m.action(f.id,Revision(1),current.sequence,{a,b->Outcome.Success(f.action(a,b){state->state.copy(elapsed=state.elapsed.copy(gameElapsed=0.seconds))})},token),FailureCode.INVALID_INPUT)
            assertEquals(current,ok(SqlLifeGameStore(db,codec).read(f.id,token)))
        }
    }
    @Test fun liveUnknownEventLineageRequiresExplicitRepair()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val old=create(db);val unknown=Knowledge.Unknown("Legacy",SnapshotList.empty())
            val changed=old.checkpoint.copy(snapshot=old.checkpoint.snapshot.copy(lastEventId=unknown),lastSettledEventId=unknown)
            db.connection.prepare("UPDATE life_game SET checkpoint_payload=?").use{it.bindBlob(1,ok(codec.encodeCheckpoint(changed)).copyBytes());it.step()}
            var called=false
            val result=SqlLifeGameMutator(db,codec).action(f.id,Revision(0),0,{a,b->called=true;Outcome.Success(f.action(a,b))},token)
            assertEquals(UnavailableReason.UNSUPPORTED,(result as Outcome.Unavailable).reason);assertFalse(called)
            assertEquals(0L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun currentOperationalCorruptionBlocksEngineBeforeAnyWrite()=runBlocking {
        for(sql in listOf("UPDATE life_result SET reason=CAST(X'FF' AS TEXT)","UPDATE life_result SET revision=0","UPDATE life_result SET finished_nano=1000000000","DELETE FROM life_result",
            "UPDATE life_alert SET target_kind='wrong'","UPDATE life_alert SET delivery_mask=8","UPDATE life_alert SET target_seat_id=X'FF'","UPDATE life_alert SET fired_nano=1000000000","DELETE FROM life_alert")) HostLifeDatabase(folder.newFile()).use {db ->
            val m=SqlLifeGameMutator(db,codec);val current=action(m,create(db)){a,b->f.finish(a,b,true)};db.sql(sql)
            var called=false
            fails(m.action(f.id,current.checkpoint.snapshot.revision,current.sequence,{a,b->called=true;Outcome.Success(f.action(a,b))},token),FailureCode.CORRUPT_DATA)
            assertFalse(called);assertEquals(1L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun initialResultCannotBeInventedAlreadyRetracted()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            create(db)
            fails(SqlLifeGameMutator(db,codec).action(f.id,Revision(0),0,{a,b->
                val packet=f.finish(a,b);val after=packet.checkpoint.snapshot.copy(status=GameStatus.PAUSED,endedAt=Knowledge.Absent("None"),result=Knowledge.Known((packet.checkpoint.snapshot.result as Knowledge.Known).value.copy(retracted=true)))
                Outcome.Success(packet.copy(archive=packet.archive.copy(provenance=packet.archive.provenance.copy(afterAction=after)),checkpoint=packet.checkpoint.copy(snapshot=after)))
            },token),FailureCode.INVALID_INPUT)
            assertEquals(0L,db.scalar("SELECT count(*) FROM life_result"))
        }
    }
    @Test fun actionCannotDropRosterDefinitionIdentityOrOperationalAlertFacts()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val m=SqlLifeGameMutator(db,codec);val current=action(m,create(db)){a,b->f.finish(a,b,true)}
            val mutations=listOf<(GameSnapshot)->GameSnapshot>(
                {it.copy(alerts=SnapshotList.empty())},
                {it.copy(seats=SnapshotList.copyOf(it.seats.map{seat->if(seat.id==f.seat)seat.copy(id=EntityId("replacement"))else seat}))},
                {it.copy(result=Knowledge.Absent("Dropped"))},
                {it.copy(settings=it.settings.copy(counterDefinitions=SnapshotList.of(it.settings.counterDefinitions.single().copy(id=EntityId("new-life")))))} )
            for(change in mutations) fails(m.action(f.id,current.checkpoint.snapshot.revision,current.sequence,{a,b->Outcome.Success(f.action(a,b,change=change))},token),FailureCode.INVALID_INPUT)
            assertEquals(current,ok(SqlLifeGameStore(db,codec).read(f.id,token)))
        }
    }

    @Test fun corruptHistoricalEventIdAggregateIsTypeGuardedBeforeDriverTextRetrieval()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val m=SqlLifeGameMutator(db,codec);val first=action(m,create(db));val current=action(m,first)
            db.sql("UPDATE life_event SET event_id=CAST(zeroblob(65537) AS TEXT) WHERE revision=1")
            db.maxDirectTextBytes=0;var called=false
            fails(m.action(f.id,current.checkpoint.snapshot.revision,current.sequence,{a,b->called=true;Outcome.Success(f.action(a,b))},token),FailureCode.CORRUPT_DATA)
            assertFalse(called);assertTrue("Unsafe direct TEXT bytes ${db.maxDirectTextBytes}",db.maxDirectTextBytes<=16)
        }
    }

    @Test fun deliveryDoesNotTrustDisagreeingCurrentAlertProjection()=runBlocking {
        for(sql in listOf("UPDATE life_alert SET acknowledged_seconds=900,acknowledged_nano=0","UPDATE life_alert SET target_kind='seat_clock',target_seat_id='seat-b'","UPDATE life_alert SET fired_seconds=999")) HostLifeDatabase(folder.newFile()).use {db ->
            val m=SqlLifeGameMutator(db,codec);action(m,create(db)){a,b->f.finish(a,b,true)};db.sql(sql)
            fails(m.markDelivered(f.id,EntityId("alert"),1,token),FailureCode.CORRUPT_DATA)
            assertEquals(0L,db.scalar("SELECT delivery_mask FROM life_alert"))
        }
    }

    @Test fun legacyUnknownUndoTargetIsUnsupportedRatherThanGuessed()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val m=SqlLifeGameMutator(db,codec);val current=action(m,create(db))
            val original=ok(SqlLifeEventReader(db,codec).archive(f.id,0,token))
            val legacy=original.copy(event=original.event.copy(undoOfEventId=Knowledge.Unknown("Legacy undo intent",SnapshotList.empty())))
            db.connection.prepare("UPDATE life_event SET archive_payload=?").use{it.bindBlob(1,ok(codec.encodeArchive(legacy)).copyBytes());it.step()}
            val result=m.action(f.id,current.checkpoint.snapshot.revision,current.sequence,{a,b->Outcome.Success(f.action(a,b,undo=Knowledge.Known(0L)))},token)
            assertTrue("Unknown undo intent requires explicit repair: $result",result is Outcome.Unavailable)
            assertEquals(UnavailableReason.UNSUPPORTED,(result as Outcome.Unavailable).reason)
            assertEquals(1L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }

    @Test fun currentTimingSpineCannotSilentlyChangeOwnerConfigurationOrTurnLineage()=runBlocking {
        val base=f.input()
        val clock=base.clock.copy(mode=SeatClockMode.CHESS,seats=SnapshotList.copyOf(base.seats.map{SeatTime(it.id,60.seconds,60.seconds)}))
        val input=base.copy(settings=base.settings.copy(clockMode=SeatClockMode.CHESS),clock=clock)
        val mutations=listOf<(GameSnapshot)->GameSnapshot>(
            {it.copy(clock=it.clock.copy(activeSeat=f.other))},
            {it.copy(clock=it.clock.copy(seats=SnapshotList.copyOf(it.clock.seats.map{bank->bank.copy(initial=61.seconds)})))},
            {it.copy(clock=it.clock.copy(seats=SnapshotList.copyOf(it.clock.seats.map{bank->bank.copy(remaining=61.seconds)})))},
            {it.copy(clock=it.clock.copy(zeroPolicy=ClockZeroPolicy.PAUSE))},
            {it.copy(elapsed=it.elapsed.copy(turnLineageId=Knowledge.Known(EntityId("invented-lineage"))))})
        for(change in mutations) HostLifeDatabase(folder.newFile()).use {db ->
            val m=SqlLifeGameMutator(db,codec)
            val current=action(m,create(db,input)){a,b->f.action(a,b,GameAction.PassClock(f.seat)){state->state.copy(clock=state.clock.copy(activeSeat=f.seat,paused=false,anchor=ClockAnchor(UtcTimestamp(1_001),0.seconds,f.process)))}}
            val corrupted=current.checkpoint.copy(snapshot=change(current.checkpoint.snapshot))
            db.connection.prepare("UPDATE life_game SET checkpoint_payload=?").use{it.bindBlob(1,ok(codec.encodeCheckpoint(corrupted)).copyBytes());it.step()}
            var called=false
            fails(m.action(f.id,Revision(1),current.sequence,{a,b->called=true;Outcome.Success(f.action(a,b))},token),FailureCode.CORRUPT_DATA)
            assertFalse(called)
        }
    }
    @Test fun sequenceAndEventIdentityExhaustionAreVisibleBeforeCallback()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            create(db);db.sql("UPDATE life_game SET checkpoint_sequence=9223372036854775807")
            var called=false
            fails(SqlLifeGameMutator(db,codec).action(f.id,Revision(0),Long.MAX_VALUE,{a,b->called=true;Outcome.Success(f.action(a,b))},token),FailureCode.INVALID_INPUT)
            assertFalse(called);assertEquals(0L,db.scalar("SELECT count(*) FROM life_event"))
        }
        HostLifeDatabase(folder.newFile()).use {db ->
            val m=SqlLifeGameMutator(db,codec);val original=create(db);val current=action(m,original)
            val replacement=f.action(original,Long.MAX_VALUE)
            db.connection.prepare("UPDATE life_event SET event_id=?,group_id=?,archive_payload=?").use{it.bindLong(1,Long.MAX_VALUE);it.bindText(2,replacement.archive.event.groupId.value);it.bindBlob(3,ok(codec.encodeArchive(replacement.archive)).copyBytes());it.step()}
            db.connection.prepare("UPDATE life_game SET checkpoint_payload=?").use{it.bindBlob(1,ok(codec.encodeCheckpoint(replacement.checkpoint)).copyBytes());it.step()}
            var called=false
            fails(m.action(f.id,Revision(1),current.sequence,{a,b->called=true;Outcome.Success(f.action(a,b))},token),FailureCode.INVALID_INPUT)
            assertFalse(called);assertEquals(1L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun encodedArchiveAndNestedSnapshotLimitsFailBeforeFirstWrite()=runBlocking {
        for(oversizedArchive in listOf(false,true)) HostLifeDatabase(folder.newFile()).use {db ->
            create(db);var writes=0;db.afterWrite={writes++}
            fails(SqlLifeGameMutator(db,codec).action(f.id,Revision(0),0,{a,b->
                if(oversizedArchive) {
                    val group=GameAction.Group(SnapshotList.copyOf(List(300){GameAction.RenameSeat(f.seat,"x".repeat(65_536))}),EntityId("group-$b"))
                    Outcome.Success(f.action(a,b,group){it})
                } else Outcome.Success(f.action(a,b){state->state.copy(alerts=SnapshotList.copyOf(List(80){index->GameAlert(EntityId("$index-"+"x".repeat(60_000)),f.id,GameAlertTarget.GameTimer,f.stamp,null)}))})
            },token),FailureCode.INVALID_INPUT)
            assertEquals(0,writes);assertEquals(0L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun undoOfAnUndoIsRejectedWithoutChangingHistory()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val m=SqlLifeGameMutator(db,codec);val first=action(m,create(db));val undo=action(m,first){a,b->f.action(a,b,undo=Knowledge.Known(0L))}
            fails(m.action(f.id,undo.checkpoint.snapshot.revision,undo.sequence,{a,b->Outcome.Success(f.action(a,b,undo=Knowledge.Known(1L)))},token),FailureCode.INVALID_INPUT)
            assertEquals(2L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }

    @Test fun maximumRevisionCannotOverflowOrInvokeCallback()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val original=create(db);val before=original.checkpoint.snapshot.copy(revision=Revision(Long.MAX_VALUE-1))
            val after=before.copy(revision=Revision(Long.MAX_VALUE),lastEventId=Knowledge.Known(0L))
            val event=GameEvent(0,f.id,EntityId("maximum-revision"),GameAction.SetAdjustmentStep(2),f.stamp,Knowledge.Absent("Not undo"),after.revision)
            val archive=GameEventArchive(1,event,GameActionProvenance(0,before,after,GameTimeObservation(0.seconds,f.stamp,f.process)))
            db.connection.prepare("INSERT INTO life_event VALUES ('synthetic-mutations','game',0,9223372036854775807,'maximum-revision',1000,0,NULL,?)").use{it.bindBlob(1,ok(codec.encodeArchive(archive)).copyBytes());it.step()}
            db.connection.prepare("UPDATE life_game SET revision=9223372036854775807,checkpoint_sequence=1,checkpoint_payload=?").use{it.bindBlob(1,ok(codec.encodeCheckpoint(GameCheckpoint(after,Knowledge.Known(0L),null))).copyBytes());it.step()}
            var called=false
            fails(SqlLifeGameMutator(db,codec).action(f.id,after.revision,1,{a,b->called=true;Outcome.Success(f.action(a,b))},token),FailureCode.INVALID_INPUT)
            assertFalse(called);assertEquals(1L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun retainedHistoricalOccurrenceCanBeDeliveredButCannotBeRecycled()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val m=SqlLifeGameMutator(db,codec);val current=action(m,create(db)){a,b->f.finish(a,b,true)}
            db.sql("INSERT INTO life_alert SELECT game_source,game_id,'retained',target_kind,target_seat_id,fired_seconds,fired_nano,acknowledged_seconds,acknowledged_nano,delivery_mask FROM life_alert WHERE occurrence_id='alert'")
            ok(m.markDelivered(f.id,EntityId("retained"),5,token))
            assertEquals(5L,db.scalar("SELECT delivery_mask FROM life_alert WHERE occurrence_id='retained'"))
            fails(m.action(f.id,current.checkpoint.snapshot.revision,current.sequence,{a,b->Outcome.Success(f.action(a,b){state->state.copy(alerts=SnapshotList.copyOf(state.alerts+GameAlert(EntityId("retained"),f.id,GameAlertTarget.GameTimer,f.stamp,null)))})},token),FailureCode.INVALID_INPUT)
            assertEquals(2L,db.scalar("SELECT count(*) FROM life_alert"));assertEquals(5L,db.scalar("SELECT delivery_mask FROM life_alert WHERE occurrence_id='retained'"))
        }
    }
    @Test fun acknowledgedOccurrenceCannotBeUnacknowledgedOrRewritten()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val m=SqlLifeGameMutator(db,codec);val finished=action(m,create(db)){a,b->f.finish(a,b,true)}
            val acknowledged=action(m,finished){a,b->f.action(a,b,GameAction.AcknowledgeGameAlert(EntityId("alert"))){state->state.copy(alerts=SnapshotList.of(state.alerts.single().copy(acknowledgedAt=UtcTimestamp(999))))}}
            for(value in listOf(null,UtcTimestamp(998))) fails(m.action(f.id,acknowledged.checkpoint.snapshot.revision,acknowledged.sequence,{a,b->Outcome.Success(f.action(a,b){state->state.copy(alerts=SnapshotList.of(state.alerts.single().copy(acknowledgedAt=value)))})},token),FailureCode.INVALID_INPUT)
            assertEquals(999L,db.scalar("SELECT acknowledged_seconds FROM life_alert"));assertEquals(2L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }

    @Test fun zeroCasRowCountRollsBackAndNeverReportsSuccess()=runBlocking {
        for(system in listOf(false,true)) HostLifeDatabase(folder.newFile()).use {db ->
            val original=create(db)
            val mismatched=object:Database by db {
                override suspend fun <T> transaction(action:suspend(DbTransaction)->T,cancel:CancelToken):Outcome<T> = db.transaction({tx->
                    action(object:DbTransaction by tx {
                        override suspend fun execute(request:SqlRequest,cancel:CancelToken):Outcome<DbWriteResult> {
                            val actual=tx.execute(request,cancel)
                            return if(request.statement.startsWith("UPDATE life_game SET") && actual is Outcome.Success)Outcome.Success(DbWriteResult(0))else actual
                        }
                    })
                },cancel)
            }
            val m=SqlLifeGameMutator(mismatched,codec)
            val result=if(system)m.checkpoint(f.id,Revision(0),0,f.stamp,{Outcome.Success(it.checkpoint)},token)
                else m.action(f.id,Revision(0),0,{a,b->Outcome.Success(f.action(a,b))},token)
            fails(result,FailureCode.STALE_REVISION)
            assertEquals(original,ok(SqlLifeGameStore(db,codec).read(f.id,token)));assertEquals(0L,db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun nextIdentityUsesMaximumImportedEventIdInsteadOfCurrentRevisionOrHeadId()=runBlocking {
        HostLifeDatabase(folder.newFile()).use {db ->
            val m=SqlLifeGameMutator(db,codec);val original=create(db);val first=action(m,original);action(m,first)
            val importedFirst=f.action(original,100)
            val importedSecond=f.action(StoredLifeGame(original.creation,importedFirst.checkpoint,1),5)
            for(packet in listOf(importedFirst,importedSecond))db.connection.prepare("UPDATE life_event SET event_id=?,group_id=?,archive_payload=? WHERE revision=?").use{
                it.bindLong(1,packet.archive.event.id);it.bindText(2,packet.archive.event.groupId.value);it.bindBlob(3,ok(codec.encodeArchive(packet.archive)).copyBytes());it.bindLong(4,packet.archive.event.revision.value);it.step()
            }
            db.connection.prepare("UPDATE life_game SET checkpoint_payload=?").use{it.bindBlob(1,ok(codec.encodeCheckpoint(importedSecond.checkpoint)).copyBytes());it.step()}
            val current=ok(SqlLifeGameStore(db,codec).read(f.id,token));var allocated=-1L
            action(m,current){a,b->allocated=b;f.action(a,b)}
            assertEquals(101L,allocated)
            assertEquals(listOf(100L,5L,101L),ok(SqlLifeEventReader(db,codec).events(f.id,PageRequest(100),token)).items.map{it.id})
        }
    }

}
