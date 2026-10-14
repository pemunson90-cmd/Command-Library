package com.architectpsg.commandlibrary.data.storage.life

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.feature.life.persistence.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SqlLifeGameLifecycleTest {
    @get:Rule val folder = TemporaryFolder()
    private val f = LifeMutationFixtures
    private val codec = LifePayloadCodec()
    private class Ids : IdSource { var calls = 0; override fun nextId(): String = if(++calls == 1) "game" else "allocated-$calls" }
    private fun <T> ok(outcome: Outcome<T>): T { assertTrue("Expected success, got $outcome", outcome is Outcome.Success); return (outcome as Outcome.Success).value }
    private fun failed(outcome: Outcome<*>, code: FailureCode) { assertTrue("Expected $code, got $outcome", outcome is Outcome.Failure); assertEquals(code, (outcome as Outcome.Failure).error.code) }
    private fun reading(time: Long = 0, wall: Long = 1000, process: EntityId = f.process) = GameTimeObservation(time.seconds, UtcTimestamp(wall), process)
    private fun lifecycle(db: HostLifeDatabase, ids: IdSource = Ids()) = SqlLifeGameLifecycle(db, codec, ids, f.provider)
    private fun clockInput(initial: Long = 20, policy: ClockZeroPolicy = ClockZeroPolicy.ALERT): GameCreation {
        val input = f.input()
        return input.copy(settings = input.settings.copy(clockMode = SeatClockMode.CHESS), clock = input.clock.copy(mode = SeatClockMode.CHESS,
            seats = SnapshotList.copyOf(input.seats.map { SeatTime(it.id, initial.seconds, initial.seconds) }), zeroPolicy = policy))
    }
    private suspend fun start(db: HostLifeDatabase, current: StoredLifeGame): StoredLifeGame = ok(SqlLifeGameMutator(db, codec).action(f.id, current.checkpoint.snapshot.revision, current.sequence, { old, event ->
        val anchor = ClockAnchor(f.stamp, 0.seconds, f.process)
        val action = GameAction.Group(SnapshotList.of(GameAction.SwitchTurn(f.seat), GameAction.ClockPause(false), GameAction.SetElapsedPaused(false)), EntityId("group-$event"))
        Outcome.Success(f.action(old, event, action) { it.copy(status = GameStatus.ACTIVE, activeSeatId = f.seat, turnNumber = 1,
            clock = it.clock.copy(activeSeat = f.seat, paused = false, anchor = anchor),
            elapsed = it.elapsed.copy(paused = false, anchor = anchor, turnLineageId = Knowledge.Known(EntityId("active-turn")))) })
    }, f.token))
    @Test fun actualInitializerCreatesKnownLineageCountersAndDurableIdempotentReopenWithoutMoreIds() = runBlocking<Unit> {
        val file = folder.newFile(); val ids = Ids(); lateinit var original: StoredLifeGame
        HostLifeDatabase(file).use { db ->
            val lifecycle = lifecycle(db, ids); original = ok(lifecycle.create(f.input(), reading(), f.token))
            assertEquals(2, ids.calls); assertEquals(f.id, original.checkpoint.snapshot.id)
            assertEquals(Knowledge.Known(EntityId("allocated-2")), original.checkpoint.snapshot.elapsed.turnLineageId)
            assertNull(original.checkpoint.snapshot.elapsed.anchor); assertEquals(2, original.checkpoint.snapshot.counters.size)
            assertTrue(original.checkpoint.snapshot.counters.all { it.value == 20 && !it.hidden })
            assertEquals(original, ok(lifecycle.create(f.input(), reading(999, -1), f.token))); assertEquals(2, ids.calls)
            failed(lifecycle.create(f.input().copy(settings = f.input().settings.copy(adjustmentStep = 2)), reading(), f.token), FailureCode.INVALID_INPUT)
            assertEquals(2, ids.calls)
        }
        HostLifeDatabase(file, false).use { db -> assertEquals(original, ok(lifecycle(db).read(f.id, f.token))) }
    }
    @Test fun invalidOrFailingIdSourceIsTypedAndCancellationNeverCommitsGame() = runBlocking<Unit> {
        for(value in listOf("", "x".repeat(65_537), charArrayOf(0xd800.toChar()).concatToString())) HostLifeDatabase(folder.newFile()).use { db ->
            val source = object : IdSource { override fun nextId() = value }
            failed(lifecycle(db, source).create(f.input(), reading(), f.token), FailureCode.INTERNAL)
            assertEquals(0L, db.scalar("SELECT count(*) FROM life_game"))
        }
        HostLifeDatabase(folder.newFile()).use { db ->
            val broken = object : IdSource { override fun nextId(): String = error("Synthetic source failure") }
            failed(lifecycle(db, broken).create(f.input(), reading(), f.token), FailureCode.INTERNAL)
            val cancelled = object : IdSource { override fun nextId(): String = throw CancellationException("Synthetic ID cancellation") }
            try { lifecycle(db, cancelled).create(f.input(), reading(), f.token); fail("Cancellation swallowed") } catch(expected: CancellationException) { }
            assertEquals(0L, db.scalar("SELECT count(*) FROM life_game"))
        }
    }
    @Test fun sameProcessSettlementPersistsOccurrenceOnceWithoutChangingRevisionOrArchive() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val lifecycle = lifecycle(db); val active = start(db, ok(lifecycle.create(clockInput(1, ClockZeroPolicy.ELIMINATE), reading(), f.token)))
            val archive = ok(SqlLifeEventReader(db, codec).archive(f.id, 0, f.token))
            val settled = ok(lifecycle.resume(f.id, reading(2, 1002), f.token))
            assertEquals(active.checkpoint.snapshot.revision, settled.checkpoint.snapshot.revision); assertEquals(active.sequence + 1, settled.sequence)
            assertEquals(2.seconds, settled.checkpoint.snapshot.elapsed.gameElapsed); assertEquals(2.seconds, settled.checkpoint.snapshot.elapsed.turnElapsed)
            assertEquals(1, settled.checkpoint.snapshot.alerts.size); assertTrue(settled.checkpoint.snapshot.clock.paused)
            assertTrue(settled.checkpoint.snapshot.seats.all { it.participation == SeatParticipation.Active })
            assertEquals(settled, ok(lifecycle.resume(f.id, reading(2, 1002), f.token)))
            assertEquals(archive, ok(SqlLifeEventReader(db, codec).archive(f.id, 0, f.token))); assertEquals(1L, db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun newProcessRecoveryPausesBothAtDurableBalancesAndMarkerNoopDoesNotChargeWallGap() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val lifecycle = lifecycle(db); start(db, ok(lifecycle.create(clockInput(), reading(), f.token)))
            val durable = ok(lifecycle.resume(f.id, reading(5, 1005), f.token))
            val nextProcess = EntityId("new-process")
            val resumed = ok(lifecycle.resume(f.id, reading(5000, 1, nextProcess), f.token))
            assertTrue(resumed.checkpoint.snapshot.clock.paused); assertTrue(resumed.checkpoint.snapshot.elapsed.paused)
            assertEquals(durable.checkpoint.snapshot.clock.seats, resumed.checkpoint.snapshot.clock.seats)
            assertEquals(5.seconds, resumed.checkpoint.snapshot.elapsed.gameElapsed); assertEquals(5.seconds, resumed.checkpoint.snapshot.elapsed.turnElapsed)
            assertEquals(nextProcess, resumed.checkpoint.recoveredProcessSessionId)
            assertEquals(resumed, ok(lifecycle.resume(f.id, reading(6000, 2, nextProcess), f.token)))
            assertEquals(durable.checkpoint.snapshot.revision, resumed.checkpoint.snapshot.revision)
        }
    }
    @Test fun exhaustedSequenceIsReadableAndNoopSystemDoesNotEncodeOrWriteButChangesFail() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val lifecycle = lifecycle(db); ok(lifecycle.create(f.input(), reading(), f.token)); ok(lifecycle.resume(f.id, reading(), f.token))
            db.sql("UPDATE life_game SET checkpoint_sequence=9223372036854775807")
            val current = ok(lifecycle.read(f.id, f.token)); assertEquals(Long.MAX_VALUE, current.sequence)
            assertEquals(current, ok(lifecycle.resume(f.id, reading(), f.token)))
            var encodes = 0; var writes = 0
            val spy = object : LifePayloadCodecPort by codec {
                override fun encodeCheckpoint(value: GameCheckpoint): Outcome<SnapshotBytes> { encodes++; return codec.encodeCheckpoint(value) }
            }
            db.afterWrite = { writes++ }
            val m = SqlLifeGameMutator(db, spy)
            assertEquals(current, ok(m.systemCurrent(f.id, f.stamp, { Outcome.Success(it.checkpoint) }, f.token)))
            failed(m.systemCurrent(f.id, f.stamp, { Outcome.Success(it.checkpoint.copy(recoveredProcessSessionId = EntityId("other"))) }, f.token), FailureCode.INVALID_INPUT)
            assertEquals(0, encodes); assertEquals(0, writes); db.afterWrite = null
            assertEquals(current, ok(lifecycle.read(f.id, f.token)))
        }
    }
    @Test fun checkedReadAndNoopCannotHideBrokenCurrentSpineOrOperationalProjection() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val lifecycle = lifecycle(db); val created = ok(lifecycle.create(f.input(), reading(), f.token))
            val finished = ok(SqlLifeGameMutator(db, codec).action(f.id, Revision(0), created.sequence, { old, event -> Outcome.Success(f.finish(old, event)) }, f.token))
            assertEquals(finished, ok(lifecycle.read(f.id, f.token)))
            db.sql("DELETE FROM life_result")
            failed(lifecycle.read(f.id, f.token), FailureCode.CORRUPT_DATA)
            failed(SqlLifeGameMutator(db, codec).systemCurrent(f.id, f.stamp, { Outcome.Success(it.checkpoint) }, f.token), FailureCode.CORRUPT_DATA)
        }
    }
    @Test fun explicitCheckpointStillRejectsStaleSequenceAndMonotonicReversal() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val lifecycle = lifecycle(db); val active = start(db, ok(lifecycle.create(clockInput(), reading(), f.token)))
            val settled = ok(lifecycle.checkpoint(f.id, active.checkpoint.snapshot.revision, active.sequence, reading(3, 1003), f.token))
            failed(lifecycle.checkpoint(f.id, active.checkpoint.snapshot.revision, active.sequence, reading(4, 1004), f.token), FailureCode.STALE_REVISION)
            failed(lifecycle.resume(f.id, reading(2, 1002), f.token), FailureCode.INVALID_INPUT)
            assertEquals(settled, ok(lifecycle.read(f.id, f.token)))
        }
    }
    @Test fun lateAlertSqlFailureRollsBackSystemParentSequenceAndClockSettlement() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val lifecycle = lifecycle(db); val active = start(db, ok(lifecycle.create(clockInput(1, ClockZeroPolicy.ELIMINATE), reading(), f.token)))
            db.sql("CREATE TRIGGER fail_lifecycle_alert BEFORE INSERT ON life_alert BEGIN SELECT RAISE(ABORT,'synthetic lifecycle failure'); END")
            failed(lifecycle.resume(f.id, reading(2, 1002), f.token), FailureCode.PERSISTENCE)
            assertEquals(active, ok(lifecycle.read(f.id, f.token))); assertEquals(0L, db.scalar("SELECT count(*) FROM life_alert"))
        }
    }
    @Test fun actualSQLiteInitializationRetainsPartnerScopeAndConfiguredInitials() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val base = f.input()
            val card = CardIdentity(Knowledge.Absent("Synthetic oracle absence"), Knowledge.Absent("Synthetic printing absence"),
                ProviderIdentity(f.provider, "card", "synthetic"), Knowledge.Known("Synthetic"), LanguageTag.ENGLISH)
            val a = EntityId("slot-a"); val b1 = EntityId("slot-b1"); val b2 = EntityId("slot-b2")
            val seats = SnapshotList.of(base.seats[0].copy(commanders = SnapshotList.of(CommanderSlot(a, card, "Same"))),
                base.seats[1].copy(commanders = SnapshotList.of(CommanderSlot(b1, card, "Same"), CommanderSlot(b2, card, "Same"))))
            val damage = CounterDefinition(EntityId("damage"), "Damage", 0, null, 3, CounterLossPolicy.None, false, CounterRole.COMMANDER_DAMAGE)
            val tax = CounterDefinition(EntityId("tax"), "Tax", 0, null, 2, CounterLossPolicy.None, false, CounterRole.COMMANDER_TAX)
            val input = base.copy(seats = seats, settings = base.settings.copy(counterDefinitions = SnapshotList.copyOf(base.settings.counterDefinitions + listOf(damage, tax))))
            val lifecycle = lifecycle(db); val created = ok(lifecycle.create(input, reading(), f.token))
            assertEquals(input.settings, created.checkpoint.snapshot.settings); assertEquals(seats, created.checkpoint.snapshot.seats)
            val states = created.checkpoint.snapshot.counters
            assertEquals(8, states.size)
            assertEquals(setOf(b1, b2), states.filter { it.definitionId == damage.id && it.seatId == f.seat }.map { it.commanderSlotId }.toSet())
            assertEquals(setOf(a), states.filter { it.definitionId == damage.id && it.seatId == f.other }.map { it.commanderSlotId }.toSet())
            assertTrue(states.filter { it.definitionId == damage.id }.all { it.value == 3 && !it.hidden })
            assertEquals(setOf(b1, b2), states.filter { it.definitionId == tax.id && it.seatId == f.other }.map { it.commanderSlotId }.toSet())
            assertEquals(created, ok(lifecycle.read(f.id, f.token)))
        }
    }
    @Test fun maximumRevisionRemainsReadableAndAllowsSystemMarkerWithoutNewEvent() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val lifecycle = lifecycle(db); val original = ok(lifecycle.create(f.input(), reading(), f.token))
            val before = original.checkpoint.snapshot.copy(revision = Revision(Long.MAX_VALUE - 1))
            val after = before.copy(revision = Revision(Long.MAX_VALUE), lastEventId = Knowledge.Known(0L))
            val event = GameEvent(0, f.id, EntityId("maximum-revision"), GameAction.SetAdjustmentStep(1), f.stamp, Knowledge.Absent("Original"), after.revision)
            val archive = GameEventArchive(1, event, GameActionProvenance(0, before, after, reading()))
            db.connection.prepare("INSERT INTO life_event VALUES ('synthetic-mutations','game',0,9223372036854775807,'maximum-revision',1000,0,NULL,?)").use {
                it.bindBlob(1, ok(codec.encodeArchive(archive)).copyBytes()); it.step()
            }
            db.connection.prepare("UPDATE life_game SET revision=9223372036854775807,checkpoint_sequence=1,checkpoint_payload=?").use {
                it.bindBlob(1, ok(codec.encodeCheckpoint(GameCheckpoint(after, Knowledge.Known(0L), null))).copyBytes()); it.step()
            }
            val loaded = ok(lifecycle.read(f.id, f.token)); assertEquals(Revision(Long.MAX_VALUE), loaded.checkpoint.snapshot.revision)
            val resumed = ok(lifecycle.resume(f.id, reading(), f.token))
            assertEquals(Revision(Long.MAX_VALUE), resumed.checkpoint.snapshot.revision); assertEquals(2L, resumed.sequence)
            assertEquals(1L, db.scalar("SELECT count(*) FROM life_event")); assertEquals(archive, ok(SqlLifeEventReader(db, codec).archive(f.id, 0, f.token)))
        }
    }
    @Test fun recoveryPreservesAcknowledgmentDeliveryBitsAndCompletedResultAcrossHugeGap() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val lifecycle = lifecycle(db); start(db, ok(lifecycle.create(clockInput(1, ClockZeroPolicy.ELIMINATE), reading(), f.token)))
            val zero = ok(lifecycle.resume(f.id, reading(2, 1002), f.token)); val alert = zero.checkpoint.snapshot.alerts.single()
            val m = SqlLifeGameMutator(db, codec); ok(m.markDelivered(f.id, alert.id, 7, f.token))
            val ack = ok(m.action(f.id, zero.checkpoint.snapshot.revision, zero.sequence, { old, event ->
                Outcome.Success(f.action(old, event, GameAction.AcknowledgeGameAlert(alert.id)) { it.copy(alerts = SnapshotList.of(alert.copy(acknowledgedAt = UtcTimestamp(1003)))) })
            }, f.token))
            val finished = ok(m.action(f.id, ack.checkpoint.snapshot.revision, ack.sequence, { old, event -> Outcome.Success(f.finish(old, event)) }, f.token))
            val archive = ok(SqlLifeEventReader(db, codec).archive(f.id, (finished.checkpoint.snapshot.lastEventId as Knowledge.Known).value, f.token))
            val resumed = ok(lifecycle.resume(f.id, reading(9999, -50, EntityId("new-process")), f.token))
            assertEquals(finished.checkpoint.snapshot.result, resumed.checkpoint.snapshot.result)
            assertEquals(finished.checkpoint.snapshot.alerts, resumed.checkpoint.snapshot.alerts)
            assertEquals(finished.checkpoint.snapshot.elapsed.gameElapsed, resumed.checkpoint.snapshot.elapsed.gameElapsed)
            assertEquals(7L, db.scalar("SELECT delivery_mask FROM life_alert")); assertEquals(1L, db.scalar("SELECT count(*) FROM life_result"))
            assertEquals(archive, ok(SqlLifeEventReader(db, codec).archive(f.id, archive.event.id, f.token)))
        }
    }
    @Test fun unsafeRunningLineagePausesForRepairWithoutInventingIdentity() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val lifecycle = lifecycle(db); val initial = ok(lifecycle.create(f.input(), reading(), f.token))
            val missing = Knowledge.Unknown("Legacy turn unknown", SnapshotList.empty())
            ok(SqlLifeGameMutator(db, codec).action(f.id, Revision(0), initial.sequence, { old, event ->
                Outcome.Success(f.action(old, event, GameAction.SetElapsedPaused(false)) { it.copy(elapsed = it.elapsed.copy(paused = false,
                    anchor = ClockAnchor(f.stamp, 0.seconds, f.process), turnLineageId = missing)) })
            }, f.token))
            val recovered = ok(lifecycle.resume(f.id, reading(10, 1010), f.token))
            assertTrue(recovered.checkpoint.snapshot.elapsed.paused); assertEquals(missing, recovered.checkpoint.snapshot.elapsed.turnLineageId)
            assertEquals(0.seconds, recovered.checkpoint.snapshot.elapsed.gameElapsed)
        }
    }
    @Test fun cancellationAtSystemAlertWriteRollsBackAndInvalidProcessMetadataDoesNotWrite() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val lifecycle = lifecycle(db); val active = start(db, ok(lifecycle.create(clockInput(1, ClockZeroPolicy.ELIMINATE), reading(), f.token)))
            var stopped = false; var writes = 0
            val token = object : CancelToken { override val isCancelled get() = stopped; override fun throwIfCancelled() { if(stopped) throw CancellationException("Synthetic system cancellation") } }
            db.afterWrite = { if(++writes == 2) stopped = true }
            try { lifecycle.resume(f.id, reading(2, 1002), token); fail("Cancellation swallowed") } catch(expected: CancellationException) { }
            db.afterWrite = null
            assertEquals(2, writes); assertEquals(active, ok(lifecycle.read(f.id, f.token)))
            failed(lifecycle.resume(f.id, reading(2, 1002, EntityId("p".repeat(65_537))), f.token), FailureCode.INVALID_INPUT)
            assertEquals(active, ok(lifecycle.read(f.id, f.token))); assertEquals(0L, db.scalar("SELECT count(*) FROM life_alert"))
        }
    }
}
