package com.architectpsg.commandlibrary.data.storage.life

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.feature.life.persistence.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LifeActionContextTest {
    @get:Rule val folder = TemporaryFolder()
    private val f = LifeMutationFixtures
    private val codec = LifePayloadCodec()
    private fun <T> ok(outcome: Outcome<T>): T { assertTrue("Expected success, got $outcome", outcome is Outcome.Success); return (outcome as Outcome.Success).value }
    private fun fails(outcome: Outcome<*>, code: FailureCode) { assertTrue("Expected $code, got $outcome", outcome is Outcome.Failure); assertEquals(code, (outcome as Outcome.Failure).error.code) }
    private suspend fun create(db: HostLifeDatabase) = ok(SqlLifeGameStore(db, codec).create(f.input(), { Outcome.Success(f.initial(f.input())) }, f.token))
    private suspend fun action(m: SqlLifeGameMutator, expected: Revision? = null) = ok(m.actionWithContext(f.id, expected, { context -> Outcome.Success(f.action(context.current, context.eventId)) }, f.token))
    @Test fun contextUsesCheckedCurrentAndLegacyRevisionInsideSameTransaction() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); val mutator = SqlLifeGameMutator(db, codec)
            val first = action(mutator, Revision(0)); val second = action(mutator)
            assertEquals(Revision(2), second.checkpoint.snapshot.revision); assertEquals(2L, second.sequence)
            var called = false
            fails(mutator.actionWithContext(f.id, first.checkpoint.snapshot.revision, { context -> called = true; Outcome.Success(f.action(context.current, context.eventId)) }, f.token), FailureCode.STALE_REVISION)
            assertFalse(called); assertEquals(second, ok(SqlLifeGameStore(db, codec).read(f.id, f.token)))
        }
    }
    @Test fun checkedArchiveLatestSelectionAndRepeatedUndoExcludeConsumedTargets() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); val mutator = SqlLifeGameMutator(db, codec); action(mutator); action(mutator)
            val undone = ok(mutator.actionWithContext(f.id, null, { context ->
                val target = ok(context.latestUndoTarget()); assertEquals(1L, target.event.id)
                assertEquals(target, ok(context.archive(target.event.id)))
                Outcome.Success(f.action(context.current, context.eventId, target.event.action, Knowledge.Known(target.event.id)) { it })
            }, f.token))
            val next = ok(mutator.actionWithContext(f.id, null, { context ->
                val target = ok(context.latestUndoTarget()); assertEquals(0L, target.event.id)
                assertEquals(undone, context.current)
                Outcome.Success(f.action(context.current, context.eventId, target.event.action, Knowledge.Known(target.event.id)) { it })
            }, f.token))
            assertEquals(Revision(4), next.checkpoint.snapshot.revision)
            assertEquals(4L, db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun ignoredCheckedReadFailureStillPreventsCommitAndEscapedContextIsClosed() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val original = create(db); val mutator = SqlLifeGameMutator(db, codec)
            lateinit var escaped: LifeActionContext
            val failed = mutator.actionWithContext(f.id, null, { context ->
                escaped = context
                assertTrue(context.archive(99) is Outcome.Unavailable)
                Outcome.Success(f.action(context.current, context.eventId))
            }, f.token)
            assertTrue(failed is Outcome.Unavailable)
            assertEquals(original, ok(SqlLifeGameStore(db, codec).read(f.id, f.token)))
            fails(escaped.archive(0), FailureCode.INVALID_INPUT); fails(escaped.latestUndoTarget(), FailureCode.INVALID_INPUT)
            assertEquals(0L, db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun successfulContextClosesBeforeReturnAndSuspendCallbackCanAwaitReads() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); val mutator = SqlLifeGameMutator(db, codec); action(mutator)
            lateinit var escaped: LifeActionContext
            val committed = ok(mutator.actionWithContext(f.id, null, { context ->
                escaped = context; yield(); assertEquals(0L, ok(context.latestUndoTarget()).event.id)
                Outcome.Success(f.action(context.current, context.eventId))
            }, f.token))
            fails(escaped.archive(0), FailureCode.INVALID_INPUT)
            assertEquals(committed, ok(SqlLifeGameStore(db, codec).read(f.id, f.token)))
        }
    }
    @Test fun noUndoTargetAndIgnoredInvalidArchiveDoNotInventMutationSuccess() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val original = create(db); val mutator = SqlLifeGameMutator(db, codec)
            val result = mutator.actionWithContext(f.id, null, { context ->
                assertTrue(context.latestUndoTarget() is Outcome.Unavailable)
                Outcome.Success(f.action(context.current, context.eventId))
            }, f.token)
            assertTrue(result is Outcome.Unavailable); assertEquals(original, ok(SqlLifeGameStore(db, codec).read(f.id, f.token)))
            val invalid = mutator.actionWithContext(f.id, null, { context ->
                fails(context.archive(-1), FailureCode.INVALID_INPUT)
                Outcome.Success(f.action(context.current, context.eventId))
            }, f.token)
            fails(invalid, FailureCode.INVALID_INPUT)
        }
    }
    @Test fun malformedHistoricalNonNullUndoProjectionCannotSilentlySelectEarlierAction() = runBlocking<Unit> {
        for(value in listOf("X'FF'", "-1", "999")) HostLifeDatabase(folder.newFile()).use { db ->
            create(db); val mutator = SqlLifeGameMutator(db, codec); repeat(3) { action(mutator) }
            val tail = ok(mutator.actionWithContext(f.id, null, { context ->
                Outcome.Success(f.action(context.current, context.eventId, undo = Knowledge.Known(2L)) { it })
            }, f.token))
            db.sql("UPDATE life_event SET undo_of_event_id=$value WHERE event_id=1")
            val result = mutator.actionWithContext(f.id, null, { context ->
                fails(context.latestUndoTarget(), FailureCode.CORRUPT_DATA)
                Outcome.Success(f.action(context.current, context.eventId))
            }, f.token)
            fails(result, FailureCode.CORRUPT_DATA)
            assertEquals(tail, ok(SqlLifeGameStore(db, codec).read(f.id, f.token)))
            assertEquals(4L, db.scalar("SELECT count(*) FROM life_event"))
        }
    }
    @Test fun unknownUndoIntentRequiresRepairAndOversizedHistoricalArchiveIsNotFetched() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); val mutator = SqlLifeGameMutator(db, codec); val first = action(mutator)
            val old = ok(SqlLifeEventReader(db, codec).archive(f.id, 0, f.token))
            val unknown = old.copy(event = old.event.copy(undoOfEventId = Knowledge.Unknown("Imported intent missing", SnapshotList.empty())))
            db.connection.prepare("UPDATE life_event SET archive_payload=? WHERE event_id=0").use { it.bindBlob(1, ok(codec.encodeArchive(unknown)).copyBytes()); it.step() }
            val result = mutator.actionWithContext(f.id, null, { context ->
                val outcome = context.latestUndoTarget(); assertTrue(outcome is Outcome.Unavailable)
                assertEquals(UnavailableReason.UNSUPPORTED, (outcome as Outcome.Unavailable).reason)
                Outcome.Success(f.action(context.current, context.eventId))
            }, f.token)
            assertTrue(result is Outcome.Unavailable); assertEquals(first, ok(SqlLifeGameStore(db, codec).read(f.id, f.token)))
        }
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); val mutator = SqlLifeGameMutator(db, codec); action(mutator); val current = action(mutator)
            db.sql("UPDATE life_event SET archive_payload=zeroblob(16777217) WHERE event_id=0")
            val result = mutator.actionWithContext(f.id, null, { context ->
                val reads = db.blobQueries; fails(context.archive(0), FailureCode.CORRUPT_DATA); assertEquals(reads, db.blobQueries)
                Outcome.Success(f.action(context.current, context.eventId))
            }, f.token)
            fails(result, FailureCode.CORRUPT_DATA); assertEquals(current, ok(SqlLifeGameStore(db, codec).read(f.id, f.token)))
        }
    }
    @Test fun importedNonmonotonicIdsSelectByRevisionAndAllocateAboveMaximum() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val original = create(db); val mutator = SqlLifeGameMutator(db, codec); action(mutator); action(mutator)
            val first = f.action(original, 100)
            val second = f.action(StoredLifeGame(original.creation, first.checkpoint, 1), 5)
            for(packet in listOf(first, second)) db.connection.prepare("UPDATE life_event SET event_id=?,group_id=?,archive_payload=? WHERE revision=?").use {
                it.bindLong(1, packet.archive.event.id); it.bindText(2, packet.archive.event.groupId.value)
                it.bindBlob(3, ok(codec.encodeArchive(packet.archive)).copyBytes()); it.bindLong(4, packet.archive.event.revision.value); it.step()
            }
            db.connection.prepare("UPDATE life_game SET checkpoint_payload=?").use { it.bindBlob(1, ok(codec.encodeCheckpoint(second.checkpoint)).copyBytes()); it.step() }
            ok(mutator.actionWithContext(f.id, null, { context ->
                assertEquals(101L, context.eventId); assertEquals(5L, ok(context.latestUndoTarget()).event.id)
                Outcome.Success(f.action(context.current, context.eventId))
            }, f.token))
        }
    }
    @Test fun cancellationAfterCheckedReadRollsBackAndClosesContext() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); val mutator = SqlLifeGameMutator(db, codec); val current = action(mutator)
            lateinit var escaped: LifeActionContext
            try {
                mutator.actionWithContext(f.id, null, { context ->
                    escaped = context; ok(context.archive(0)); throw CancellationException("Synthetic callback cancellation")
                }, f.token)
                fail("Cancellation swallowed")
            } catch(expected: CancellationException) { }
            fails(escaped.archive(0), FailureCode.INVALID_INPUT)
            assertEquals(current, ok(SqlLifeGameStore(db, codec).read(f.id, f.token)))
        }
    }
    @Test fun closureDrainsSuspendedUnstructuredReadBeforeAnyWrite() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db); action(SqlLifeGameMutator(db, codec))
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            var intercept = false; var readFinished = false
            val wrapper = object : Database by db {
                override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> = db.transaction({ tx ->
                    action(object : DbTransaction by tx {
                        override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> {
                            if(intercept && request.statement.startsWith("SELECT archive_payload FROM life_event")) {
                                intercept = false; entered.complete(Unit); release.await()
                                return tx.query(request, cancel).also { readFinished = true }
                            }
                            return tx.query(request, cancel)
                        }
                    })
                }, cancel)
            }
            val detached = CoroutineScope(coroutineContext + SupervisorJob())
            lateinit var late: Deferred<Outcome<GameEventArchive>>
            db.afterWrite = { assertTrue("Write overlapped suspended context read", readFinished) }
            try {
                val command = async {
                    SqlLifeGameMutator(wrapper, codec).actionWithContext(f.id, null, { context ->
                        intercept = true
                        late = detached.async(start = CoroutineStart.UNDISPATCHED) { context.archive(0) }
                        entered.await()
                        Outcome.Success(f.action(context.current, context.eventId))
                    }, f.token)
                }
                entered.await(); yield(); assertFalse(command.isCompleted)
                release.complete(Unit); ok(command.await()); ok(late.await())
                assertTrue(readFinished)
            } finally { release.complete(Unit); db.afterWrite = null; detached.cancel() }
        }
    }
}
