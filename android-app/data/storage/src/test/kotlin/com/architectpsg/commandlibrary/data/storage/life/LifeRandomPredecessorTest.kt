package com.architectpsg.commandlibrary.data.storage.life

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.feature.life.persistence.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LifeRandomPredecessorTest {
    @get:Rule val folder = TemporaryFolder()
    private val f = LifeMutationFixtures
    private val codec = LifePayloadCodec()
    private val requestId = EntityId("request")
    private val at = UtcTimestamp(-42)
    private fun <T> ok(outcome: Outcome<T>): T { assertTrue("Expected success, got $outcome", outcome is Outcome.Success); return (outcome as Outcome.Success).value }
    private suspend fun create(db: HostLifeDatabase) = ok(SqlLifeGameStore(db, codec).create(f.input(), { Outcome.Success(f.initial(f.input())) }, f.token))
    private suspend fun append(db: HostLifeDatabase, request: RandomRequest, value: RecordedRandomValue): StoredLifeGame {
        val current = ok(SqlLifeGameStore(db, codec).read(f.id, f.token))
        return ok(SqlLifeGameMutator(db, codec).action(f.id, current.checkpoint.snapshot.revision, current.sequence, { old, event ->
            val write = f.action(old, event, GameAction.RequestRandom(request)) { snapshot ->
                if(value is RecordedRandomValue.SeatOrder) {
                    val seats = snapshot.seats.associateBy { it.id }
                    snapshot.copy(seats = SnapshotList.copyOf(value.seatIds.mapIndexed { index, id -> seats.getValue(id).copy(order = index) }))
                } else snapshot
            }
            Outcome.Success(write.copy(archive = write.archive.copy(event = write.archive.event.copy(randomResults = SnapshotList.of(RecordedRandomResult(request.id(), at, value))))))
        }, f.token))
    }
    private fun RandomRequest.id(): EntityId = when(this) {
        is RandomRequest.Dice -> requestId
        is RandomRequest.ContinueDice -> requestId
        is RandomRequest.Coin -> requestId
        is RandomRequest.SeatPick -> requestId
        is RandomRequest.SeatOrder -> requestId
        is RandomRequest.VariantZoneShuffle -> requestId
    }
    private suspend fun probe(db: HostLifeDatabase, id: EntityId = requestId): Pair<Outcome<LifeRandomHistory?>, Outcome<StoredLifeGame>> {
        lateinit var observed: Outcome<LifeRandomHistory?>
        val commit = SqlLifeGameMutator(db, codec).actionWithContext(f.id, null, { context ->
            observed = context.randomPredecessor(id)
            Outcome.Success(f.action(context.current, context.eventId, GameAction.SetAdjustmentStep(1)) { it })
        }, f.token)
        return observed to commit
    }
    private fun firstDice() = RecordedRandomValue.Dice(6, SnapshotList.copyOf(List(10_000) { 1 }), 10_000, 10_001, 0, 1)
    @Test fun checkedAbsenceAndInitialThenTailReturnExactOriginalAndRecord() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); val empty = probe(db); assertNull(ok(empty.first)); ok(empty.second)
            val request = RandomRequest.Dice(requestId, 10_001, 6)
            append(db, request, firstDice())
            val initial = probe(db); val first = ok(initial.first)!!; ok(initial.second)
            assertEquals(request, first.original); assertEquals(RecordedRandomResult(requestId, at, firstDice()), first.last)
            val tail = RecordedRandomValue.Dice(6, SnapshotList.of(2), 2, 10_001, 1, 0)
            append(db, RandomRequest.ContinueDice(requestId, 1), tail)
            val result = probe(db); val history = ok(result.first)!!; ok(result.second)
            assertEquals(request, history.original); assertEquals(RecordedRandomResult(requestId, at, tail), history.last)
        }
    }
    @Test fun coinPickAndFullSeatOrderKeepExactOriginalAndAppliedTransition() = runBlocking<Unit> {
        val cases = listOf(RandomRequest.Coin(requestId) to RecordedRandomValue.Coin(true),
            RandomRequest.SeatPick(requestId, null, false) to RecordedRandomValue.SeatPick(f.other, SnapshotList.of(f.seat, f.other)),
            RandomRequest.SeatOrder(requestId) to RecordedRandomValue.SeatOrder(SnapshotList.of(f.other, f.seat)))
        for((request, value) in cases) HostLifeDatabase(folder.newFile()).use { db ->
            create(db); append(db, request, value)
            val observed = probe(db); val history = ok(observed.first)!!; ok(observed.second)
            assertEquals(request, history.original); assertEquals(RecordedRandomResult(requestId, at, value), history.last)
        }
    }
    @Test fun duplicateInitialAndMissingOrSkippedContinuationFailEvenIfBuilderIgnoresStatus() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); val request = RandomRequest.Coin(requestId)
            append(db, request, RecordedRandomValue.Coin(true)); append(db, request, RecordedRandomValue.Coin(false))
            val count = db.scalar("SELECT count(*) FROM life_event"); val result = probe(db)
            assertEquals(FailureCode.CORRUPT_DATA, (result.first as Outcome.Failure).error.code)
            assertEquals(FailureCode.CORRUPT_DATA, (result.second as Outcome.Failure).error.code)
            assertEquals(count, db.scalar("SELECT count(*) FROM life_event"))
        }
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db)
            append(db, RandomRequest.ContinueDice(requestId, 1), RecordedRandomValue.Dice(6, SnapshotList.of(1), 1, 10_001, 1, 0))
            assertTrue(probe(db).first is Outcome.Failure)
        }
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); val request = RandomRequest.Dice(requestId, 30_001, 6)
            append(db, request, RecordedRandomValue.Dice(6, SnapshotList.copyOf(List(10_000) { 1 }), 10_000, 30_001, 0, 20_001))
            append(db, RandomRequest.ContinueDice(requestId, 2), RecordedRandomValue.Dice(6, SnapshotList.copyOf(List(10_000) { 1 }), 10_000, 30_001, 2, 1))
            assertTrue(probe(db).first is Outcome.Failure)
        }
    }
    @Test fun undoneRandomRequestRemainsReservedAndClosedContextRejectsLookup() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); val original = RandomRequest.Coin(requestId); val current = append(db, original, RecordedRandomValue.Coin(true))
            ok(SqlLifeGameMutator(db, codec).action(f.id, current.checkpoint.snapshot.revision, current.sequence, { old, event ->
                Outcome.Success(f.action(old, event, GameAction.RequestRandom(original), Knowledge.Known(0L)) { it })
            }, f.token))
            lateinit var escaped: LifeActionContext
            ok(SqlLifeGameMutator(db, codec).actionWithContext(f.id, null, { context ->
                escaped = context; assertEquals(original, ok(context.randomPredecessor(requestId))!!.original)
                Outcome.Success(f.action(context.current, context.eventId, GameAction.SetAdjustmentStep(1)) { it })
            }, f.token))
            assertEquals(FailureCode.INVALID_INPUT, (escaped.randomPredecessor(requestId) as Outcome.Failure).error.code)
        }
    }
    private suspend fun replaceArchive(db: HostLifeDatabase, event: Long, change: (GameEventArchive) -> GameEventArchive) {
        val original = ok(SqlLifeEventReader(db, codec).archive(f.id, event, f.token))
        db.connection.prepare("UPDATE life_event SET archive_payload=? WHERE event_id=?").use {
            it.bindBlob(1, ok(codec.encodeArchive(change(original))).copyBytes()); it.bindLong(2, event); it.step()
        }
    }
    @Test fun recordedIdentityMismatchUnknownIntentAndLegacyRolesNeverBecomeFreshAbsence() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); append(db, RandomRequest.Coin(EntityId("different")), RecordedRandomValue.Coin(true))
            replaceArchive(db, 0) { it.copy(event = it.event.copy(randomResults = SnapshotList.of(RecordedRandomResult(requestId, at, RecordedRandomValue.Coin(true))))) }
            val result = probe(db); assertTrue(result.first is Outcome.Failure); assertTrue(result.second is Outcome.Failure)
        }
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); append(db, RandomRequest.Coin(requestId), RecordedRandomValue.Coin(true))
            replaceArchive(db, 0) { it.copy(event = it.event.copy(undoOfEventId = Knowledge.Unknown("Imported intent", SnapshotList.empty()))) }
            val result = probe(db); assertEquals(UnavailableReason.UNSUPPORTED, (result.first as Outcome.Unavailable).reason)
        }
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); append(db, RandomRequest.Coin(requestId), RecordedRandomValue.Coin(true))
            replaceArchive(db, 0) { archive ->
                val before = archive.provenance.before
                archive.copy(provenance = archive.provenance.copy(before = before.copy(settings = before.settings.copy(
                    counterDefinitions = SnapshotList.copyOf(before.settings.counterDefinitions.map { it.copy(role = CounterRole.OTHER) })))))
            }
            val result = probe(db); assertEquals(UnavailableReason.UNSUPPORTED, (result.first as Outcome.Unavailable).reason)
        }
    }
    @Test fun keysetScanCrossesHundredRowsWithoutDroppingOriginalOrTail() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); val request = RandomRequest.Dice(requestId, 10_001, 6); append(db, request, firstDice())
            repeat(102) {
                ok(SqlLifeGameMutator(db, codec).actionWithContext(f.id, null, { context ->
                    Outcome.Success(f.action(context.current, context.eventId, GameAction.SetAdjustmentStep(1)) { it })
                }, f.token))
            }
            val tail = RecordedRandomValue.Dice(6, SnapshotList.of(3), 3, 10_001, 1, 0)
            append(db, RandomRequest.ContinueDice(requestId, 1), tail)
            val result = probe(db); assertEquals(tail, ok(result.first)!!.last.value); ok(result.second)
        }
    }
    @Test fun aggregateByteSplitAdvancesFromProcessedPrefixAndOversizeFailsBeforeBlob() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); val padding = "p".repeat(60_000)
            repeat(3) {
                ok(SqlLifeGameMutator(db, codec).actionWithContext(f.id, null, { context ->
                    val group = GameAction.Group(SnapshotList.copyOf(List(200) { GameAction.RenameSeat(f.seat, padding) }), EntityId("group-${context.eventId}"))
                    Outcome.Success(f.action(context.current, context.eventId, group) { it })
                }, f.token))
            }
            assertTrue(db.scalar("SELECT sum(length(archive_payload)) FROM life_event") > 32L * 1024 * 1024)
            append(db, RandomRequest.Coin(requestId), RecordedRandomValue.Coin(true))
            val result = probe(db); assertEquals(RecordedRandomValue.Coin(true), ok(result.first)!!.last.value); ok(result.second)
            db.sql("UPDATE life_event SET archive_payload=zeroblob(16777217) WHERE event_id=0")
            val failed = SqlLifeGameMutator(db, codec).actionWithContext(f.id, null, { context ->
                val reads = db.blobQueries
                assertEquals(FailureCode.CORRUPT_DATA, (context.randomPredecessor(requestId) as Outcome.Failure).error.code)
                assertEquals(reads, db.blobQueries)
                Outcome.Success(f.action(context.current, context.eventId, GameAction.SetAdjustmentStep(1)) { it })
            }, f.token)
            assertEquals(FailureCode.CORRUPT_DATA, (failed as Outcome.Failure).error.code)
        }
    }
    @Test fun contradictoryParametersGroupedOriginalAndHiddenAfterMutationAreRejected() = runBlocking<Unit> {
        val request = RandomRequest.Dice(requestId, 2, 6)
        for(value in listOf(RecordedRandomValue.Dice(8, SnapshotList.of(1, 1), 2, 2, 0, 0),
            RecordedRandomValue.Dice(6, SnapshotList.of(1), 1, 1, 0, 0))) HostLifeDatabase(folder.newFile()).use { db ->
            create(db); append(db, request, value)
            assertEquals(FailureCode.CORRUPT_DATA, (probe(db).first as Outcome.Failure).error.code)
        }
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); append(db, RandomRequest.Coin(requestId), RecordedRandomValue.Coin(true))
            replaceArchive(db, 0) { archive -> archive.copy(event = archive.event.copy(action = GameAction.Group(
                SnapshotList.of(archive.event.action), archive.event.groupId))) }
            assertEquals(FailureCode.CORRUPT_DATA, (probe(db).first as Outcome.Failure).error.code)
        }
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); append(db, RandomRequest.Coin(requestId), RecordedRandomValue.Coin(true))
            ok(SqlLifeGameMutator(db, codec).actionWithContext(f.id, null, { context ->
                Outcome.Success(f.action(context.current, context.eventId, GameAction.SetAdjustmentStep(1)) { it })
            }, f.token))
            replaceArchive(db, 0) { archive -> val after = archive.provenance.afterAction
                archive.copy(provenance = archive.provenance.copy(afterAction = after.copy(settings = after.settings.copy(adjustmentStep = 9)))) }
            assertEquals(FailureCode.CORRUPT_DATA, (probe(db).first as Outcome.Failure).error.code)
        }
    }
    @Test fun cancellationDuringHistoryBlobReadRollsBackAndClosesEscapedContext() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); val current = append(db, RandomRequest.Coin(requestId), RecordedRandomValue.Coin(true))
            var cancelRead = false
            val wrapper = object : Database by db {
                override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> = db.transaction({ tx ->
                    action(object : DbTransaction by tx {
                        override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> {
                            if(cancelRead && request.statement.startsWith("SELECT archive_payload FROM life_event")) throw CancellationException("Synthetic history read cancellation")
                            return tx.query(request, cancel)
                        }
                    })
                }, cancel)
            }
            lateinit var escaped: LifeActionContext
            try {
                SqlLifeGameMutator(wrapper, codec).actionWithContext(f.id, null, { context ->
                    escaped = context; cancelRead = true; context.randomPredecessor(requestId)
                    Outcome.Success(f.action(context.current, context.eventId, GameAction.SetAdjustmentStep(1)) { it })
                }, f.token)
                fail("Cancellation swallowed")
            } catch(expected: CancellationException) { }
            assertEquals(FailureCode.INVALID_INPUT, (escaped.randomPredecessor(requestId) as Outcome.Failure).error.code)
            assertEquals(current, ok(SqlLifeGameStore(db, codec).read(f.id, f.token)))
        }
    }
}
