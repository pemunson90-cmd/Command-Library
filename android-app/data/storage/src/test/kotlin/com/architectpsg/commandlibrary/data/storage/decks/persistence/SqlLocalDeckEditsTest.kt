package com.architectpsg.commandlibrary.data.storage.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.decks.codec.DeckPayloadCodec
import com.architectpsg.commandlibrary.data.storage.decks.codec.LocalDeckPatchCodec
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckEdits
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckLibrary
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

class SqlLocalDeckEditsTest {
    private val codec = DeckPayloadCodec()
    private val patchCodec = LocalDeckPatchCodec()
    private val cancel = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private var ticks = 0
    private val clock = time { ticks++; UtcTimestamp(100 + ticks.toLong(), 123) }
    private fun time(read: () -> UtcTimestamp) = object : Clock { override fun utcNow() = read(); override fun monotonicNow() = Duration.ZERO }
    private fun edits(db: Database, clock: Clock = this.clock): LocalDeckEdits = SqlLocalDeckEdits(db, codec, patchCodec, clock)
    private fun library(db: Database): LocalDeckLibrary = SqlLocalDeckLibrary(db, codec, clock, object : IdSource { override fun nextId() = "copy" })
    private fun file() = File.createTempFile("deck-edits-", ".sqlite").also { it.deleteOnExit() }
    private fun op(raw: String) = EntityId(raw)
    private fun patch(vararg edits: DeckEdit, reason: String = "exact reason/界") = DeckPatch(SnapshotList.copyOf(edits.asList()), reason)
    private fun <T> ok(result: Outcome<T>): T { assertTrue(result.toString(), result is Outcome.Success); return (result as Outcome.Success).value }
    private fun failure(result: Outcome<*>, code: FailureCode) { assertTrue(result.toString(), result is Outcome.Failure); assertEquals(code, (result as Outcome.Failure).error.code) }
    private fun unavailable(result: Outcome<*>, reason: UnavailableReason) { assertTrue(result.toString(), result is Outcome.Unavailable); assertEquals(reason, (result as Outcome.Unavailable).reason) }
    private fun deck() : DeckSnapshot {
        val label = EntryLabel(EntityId("category/界"), "Category")
        val entry = DeckEntry(EntityId("entry"), CardIdentity(Knowledge.Known(OracleId(ProviderId("foreign"), "oracle/界")),
            Knowledge.Known(CardId(ProviderId("absent-source"), "printing")), ProviderIdentity(ProviderId("raw-source"), "kind", "raw/unchanged"),
            Knowledge.Unknown("finish unknown", SnapshotList.empty()), LanguageTag("ja")), Int.MAX_VALUE, BoardRole.COMMANDER, Knowledge.Absent("none"), "notes", SnapshotList.of(label),
            SnapshotList.of(RoleOverride(EntityId("entry"), Knowledge.Absent("none"), "role", true, UtcTimestamp(1))))
        return DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE, "deck"), "A", Knowledge.Absent("none"), "description", Knowledge.Absent("none"), SnapshotList.of("a", "a", "界"),
            UtcTimestamp(1), UtcTimestamp(1), Revision(0), SnapshotList.of(entry), Knowledge.Absent("none"), EntityId("missing-profile"),
            SnapshotList.of(SnapshotReference(ProviderId("missing-source"), GenerationId("pinned"), Revision(9), LanguageTag("ja"))), SnapshotList.empty(), "# Primer", SnapshotList.of(label))
    }
    private suspend fun create(db: Database): DeckSnapshot = deck().also { ok(library(db).create(it, op("create"), cancel)) }
    private suspend fun rename(db: Database, source: DeckSnapshot, name: String, operation: String = "edit-$name") =
        ok(edits(db).apply(source.id, patch(DeckEdit.Rename(name)), source.revision, op(operation), cancel))
    private suspend fun undo(db: Database, source: DeckSnapshot, operation: String) = ok(edits(db).undo(source.id, source.revision, op(operation), cancel))
    private fun content(expected: DeckSnapshot, actual: DeckSnapshot) { assertEquals(expected.copy(revision = actual.revision, modifiedAt = actual.modifiedAt), actual) }

    @Test fun multiEditIsOneImmutableRevisionAndOneUndoGroupWithExactRichRestoration() = runBlocking {
        HostDeckEditDatabase(file()).use { db ->
            val original = create(db); val initial = db.state().getValue("deck_revision")
            val entry = original.entries.single()
            val changed = ok(edits(db).apply(original.id, patch(DeckEdit.Rename("B"), DeckEdit.Description("other"), DeckEdit.Primer("new primer"),
                DeckEdit.Replace(entry.copy(quantity = 7, notes = "changed"))), Revision(0), op("apply"), cancel))
            assertEquals(Revision(1), changed.revision); assertEquals(1L, db.count("deck_undo_link")); assertEquals(2L, db.count("deck_revision"))
            val restored = undo(db, changed, "undo")
            content(original, restored); assertEquals(Revision(2), restored.revision); assertEquals(original.createdAt, restored.createdAt)
            assertEquals(initial.single(), db.state().getValue("deck_revision").first())
            assertEquals(1L, db.count("deck_undo_link")); assertEquals(0L, db.host.scalar("SELECT count(*) FROM pragma_foreign_key_check"))
        }
    }

    @Test fun monotonicUndoSequenceConsumesGroupsAndNewBranchDoesNotToggle() = runBlocking {
        HostDeckEditDatabase(file()).use { db ->
            val a = create(db); val b = rename(db, a, "B"); val c = rename(db, b, "C")
            val b3 = undo(db, c, "u1"); content(b, b3)
            val a4 = undo(db, b3, "u2"); content(a, a4)
            val d5 = rename(db, a4, "D"); val a6 = undo(db, d5, "u3"); content(a, a6)
            assertEquals(listOf(0L,1L,2L,3L,4L,5L,6L), listOf(a,b,c,b3,a4,d5,a6).map { it.revision.value })
            val state = db.state(); unavailable(edits(db).undo(a.id, a6.revision, op("none"), cancel), UnavailableReason.NOT_FOUND); assertEquals(state, db.state())
        }
    }

    @Test fun branchAfterSingleUndoRetainsPreviousLogicalGroup() = runBlocking {
        HostDeckEditDatabase(file()).use { db ->
            val a = create(db); val b = rename(db, a, "B"); val c = rename(db, b, "C")
            val b3 = undo(db, c, "u1"); val d4 = rename(db, b3, "D")
            val b5 = undo(db, d4, "u2"); val a6 = undo(db, b5, "u3")
            content(b, b5); content(a, a6); assertEquals(Revision(6), a6.revision)
        }
    }

    @Test fun netEqualNonemptyPatchStillCreatesOneGroup() = runBlocking {
        HostDeckEditDatabase(file()).use { db ->
            val a = create(db)
            val same = ok(edits(db).apply(a.id, patch(DeckEdit.Rename("B"), DeckEdit.Rename("A")), a.revision, op("net"), cancel))
            content(a, same); assertEquals(Revision(1), same.revision); assertEquals(1L, db.count("deck_undo_link"))
            assertEquals(Revision(2), undo(db, same, "undo").revision)
        }
    }

    @Test fun emptyPatchOnlyWritesReceiptAndNeverReadsClockOrConsumesUndo() = runBlocking {
        HostDeckEditDatabase(file()).use { db ->
            val a = create(db); val noClock = edits(db, time { error("empty read clock") })
            val before = db.state()
            assertEquals(a, ok(noClock.apply(a.id, patch(), a.revision, op("empty0"), cancel)))
            assertEquals(before.filterKeys { it != "deck_operation" }, db.state().filterKeys { it != "deck_operation" })
            val b = rename(db, a, "B"); val state = db.state()
            assertEquals(b, ok(noClock.apply(a.id, patch(), b.revision, op("empty1"), cancel)))
            assertEquals(state.filterKeys { it != "deck_operation" }, db.state().filterKeys { it != "deck_operation" })
            content(a, undo(db, b, "undo"))
            assertEquals(a, ok(noClock.apply(a.id, patch(), a.revision, op("empty0"), cancel)))
        }
    }

    @Test fun historicalApplyAndUndoReplaysPrecedeCorruptCurrentCasClockAndAvailability() = runBlocking {
        HostDeckEditDatabase(file()).use { db ->
            val a = create(db); val b = rename(db, a, "B", "apply"); val a2 = undo(db, b, "undo")
            rename(db, a2, "D"); db.sql("UPDATE deck_head SET name='corrupt'")
            val replay = edits(db, time { error("replay clock") }); val state = db.state()
            assertEquals(b, ok(replay.apply(a.id, patch(DeckEdit.Rename("B")), Revision(0), op("apply"), cancel)))
            assertEquals(a2, ok(replay.undo(a.id, Revision(1), op("undo"), cancel)))
            assertEquals(state, db.state())
            failure(replay.apply(a.id, patch(DeckEdit.Rename("B"), reason = "changed reason"), Revision(0), op("apply"), cancel), FailureCode.INVALID_INPUT)
            failure(replay.undo(a.id, Revision(2), op("undo"), cancel), FailureCode.INVALID_INPUT)
        }
    }

    @Test fun allFourMethodsShareGlobalOperationNamespace() = runBlocking {
        HostDeckEditDatabase(file()).use { db ->
            val a = create(db); val b = rename(db, a, "B", "apply"); undo(db, b, "undo")
            ok(library(db).duplicate(a.id, Revision(2), "Copy", op("duplicate"), cancel))
            val state = db.state()
            for (operation in listOf("create", "duplicate", "apply", "undo")) {
                if (operation != "create") failure(library(db).create(a, op(operation), cancel), FailureCode.INVALID_INPUT)
                if (operation != "duplicate") failure(library(db).duplicate(a.id, Revision(2), "Copy", op(operation), cancel), FailureCode.INVALID_INPUT)
                if (operation != "apply") failure(edits(db).apply(a.id, patch(DeckEdit.Rename("B")), Revision(0), op(operation), cancel), FailureCode.INVALID_INPUT)
                if (operation != "undo") failure(edits(db).undo(a.id, Revision(1), op(operation), cancel), FailureCode.INVALID_INPUT)
            }
            assertEquals(state, db.state())
        }
    }

    @Test fun reopenAndNewConnectionPreserveGroupsAndCrossConnectionCas() = runBlocking {
        val file = file(); lateinit var a: DeckSnapshot; lateinit var b: DeckSnapshot; lateinit var c: DeckSnapshot
        HostDeckEditDatabase(file).use { db -> a = create(db); b = rename(db, a, "B"); c = rename(db, b, "C") }
        HostDeckEditDatabase(file, false).use { first -> HostDeckEditDatabase(file, false).use { second ->
            val b3 = undo(first, c, "undo1"); content(b, b3)
            val state = second.state()
            failure(edits(second).apply(a.id, patch(DeckEdit.Rename("stale")), c.revision, op("stale"), cancel), FailureCode.STALE_REVISION)
            failure(edits(second).undo(a.id, c.revision, op("stale-u"), cancel), FailureCode.STALE_REVISION)
            assertEquals(state, second.state()); content(a, undo(second, b3, "undo2"))
            assertEquals(c, ok(edits(second).apply(a.id, patch(DeckEdit.Rename("C")), b.revision, op("edit-C"), cancel)))
        } }
    }

    @Test fun lostAcknowledgementOfApplyAndUndoReconcilesWithoutExtraMutation() = runBlocking {
        HostDeckEditDatabase(file()).use { db ->
            val a = create(db); db.loseAcknowledgement = true
            try { edits(db).apply(a.id, patch(DeckEdit.Rename("B")), a.revision, op("apply"), cancel); fail("expected cancellation") } catch (_: CancellationException) { }
            db.loseAcknowledgement = false; val state = db.state()
            val b = ok(edits(db).apply(a.id, patch(DeckEdit.Rename("B")), a.revision, op("apply"), cancel)); assertEquals(state, db.state())
            db.loseAcknowledgement = true
            try { edits(db).undo(a.id, b.revision, op("undo"), cancel); fail("expected cancellation") } catch (_: CancellationException) { }
            db.loseAcknowledgement = false; val undone = db.state()
            content(a, ok(edits(db).undo(a.id, b.revision, op("undo"), cancel))); assertEquals(undone, db.state())
        }
    }

    @Test fun failureAfterEveryMutationRollsBackExactFiveTableState() = runBlocking { atomicFailures(false) }
    @Test fun cancellationAfterEveryMutationEscapesAndRollsBackExactFiveTableState() = runBlocking { atomicFailures(true) }
    private suspend fun atomicFailures(cancellation: Boolean) {
        // First and subsequent apply exercise INSERT and UPDATE state; undo has four writes; empty one.
        for (mode in 0..3) for (write in 1..if (mode < 2) 5 else if (mode == 2) 4 else 1) HostDeckEditDatabase(file()).use { db ->
            val a = create(db); val source = if (mode == 0 || mode == 3) a else rename(db, a, "B")
            val before = db.state(); var count = 0; val cancelled = CancellationException("injected")
            db.host.afterWrite = { if (++count == write) { if (cancellation) throw cancelled else throw IllegalStateException("injected") } }
            try {
                val result = if (mode == 2) edits(db).undo(a.id, source.revision, op("fault"), cancel)
                    else edits(db).apply(a.id, if (mode == 3) patch() else patch(DeckEdit.Rename("C")), source.revision, op("fault"), cancel)
                if (cancellation) fail("Expected cancellation") else failure(result, FailureCode.PERSISTENCE)
            } catch (actual: CancellationException) { if (!cancellation) throw actual; assertSame(cancelled, actual) }
            db.host.afterWrite = null; assertEquals("mode=$mode write=$write", before, db.state())
        }
    }

    @Test fun staleCasNoUndoAndInvalidPatchDoNotConsumeClockOrReceipt() = runBlocking {
        HostDeckEditDatabase(file()).use { db ->
            val a = create(db); val editor = edits(db, time { error("unexpected clock") }); val state = db.state()
            unavailable(editor.undo(a.id, a.revision, op("none"), cancel), UnavailableReason.NOT_FOUND)
            failure(editor.apply(a.id, patch(DeckEdit.Rename("B")), Revision(1), op("cas"), cancel), FailureCode.STALE_REVISION)
            failure(editor.undo(a.id, Revision(1), op("ucas"), cancel), FailureCode.STALE_REVISION)
            failure(edits(db).apply(a.id, patch(DeckEdit.Rename(" ")), Revision(0), op("invalid"), cancel), FailureCode.INVALID_INPUT)
            assertEquals(state, db.state())
        }
    }

    @Test fun clockExceptionsBackwardsTimeAndCancellationLeaveExactState() = runBlocking {
        for (undo in listOf(false,true)) HostDeckEditDatabase(file()).use { db ->
            val a = create(db); val b = rename(db, a, "B"); val state = db.state()
            for (bad in listOf(time { UtcTimestamp(1) }, time { throw IllegalStateException("private clock state") }, time { throw CancellationException("clock") })) {
                try {
                    val result = if (undo) edits(db,bad).undo(a.id,b.revision,op("clock"),cancel) else edits(db,bad).apply(a.id,patch(DeckEdit.Rename("C")),b.revision,op("clock"),cancel)
                    assertTrue(result is Outcome.Failure)
                    val error = (result as Outcome.Failure).error
                    assertTrue(error.code == FailureCode.INTERNAL || error.code == FailureCode.INVALID_INPUT); assertFalse(error.message.contains("private"))
                } catch (_: CancellationException) { }
                assertEquals(state,db.state())
            }
        }
    }

    @Test fun validForeignKeyWrongPointersAndSkippedBeforeRevisionFailClosed() = runBlocking {
        val corruptions = listOf("UPDATE deck_undo_state SET undo_link_revision=1", "UPDATE deck_undo_state SET undo_link_revision=NULL",
            "UPDATE deck_undo_link SET before_revision=0 WHERE link_revision=2", "UPDATE deck_undo_link SET previous_link_revision=NULL WHERE link_revision=2",
            "UPDATE deck_undo_state SET head_revision=1", "DELETE FROM deck_undo_state")
        for (sql in corruptions) HostDeckEditDatabase(file()).use { db ->
            val a = create(db); val b = rename(db,a,"B"); val c = rename(db,b,"C"); db.sql(sql)
            assertEquals(0L, db.host.scalar("SELECT count(*) FROM pragma_foreign_key_check")); val state=db.state()
            failure(edits(db).undo(a.id,c.revision,op("bad-u"),cancel), FailureCode.CORRUPT_DATA)
            failure(edits(db).apply(a.id,patch(DeckEdit.Rename("D")),c.revision,op("bad-a"),cancel),FailureCode.CORRUPT_DATA)
            assertEquals(state,db.state())
        }
    }

    @Test fun undoChecksNewlyExposedPredecessorBeforePublishingIt() = runBlocking {
        HostDeckEditDatabase(file()).use { db ->
            val a=create(db); val b=rename(db,a,"B"); val c=rename(db,b,"C"); val d=rename(db,c,"D")
            db.sql("UPDATE deck_undo_link SET previous_link_revision=NULL WHERE link_revision=2")
            val state=db.state(); failure(edits(db).undo(a.id,d.revision,op("bad"),cancel),FailureCode.CORRUPT_DATA); assertEquals(state,db.state())
        }
    }

    @Test fun missingStateOnUntouchedDeckWithOrphanLinkIsCorrupt() = runBlocking {
        HostDeckEditDatabase(file()).use { db ->
            val a=create(db); insertRevision(db,a.copy(revision=Revision(1))); db.sql("INSERT INTO deck_undo_link VALUES('local-decks','deck',1,0,NULL)")
            val state=db.state(); failure(edits(db).apply(a.id,patch(),a.revision,op("empty"),cancel),FailureCode.CORRUPT_DATA); assertEquals(state,db.state())
        }
    }

    @Test fun malformedUndoMetadataAndHistoricalBoundsFailBeforeUnsafeAllocation() = runBlocking {
        for (sql in listOf("UPDATE deck_undo_state SET undo_link_revision='bad'", "UPDATE deck_undo_link SET before_revision='bad'", "UPDATE deck_undo_link SET previous_link_revision=-1",
            "UPDATE deck_revision SET payload_bytes=8388609 WHERE revision=0", "UPDATE deck_revision SET name=zeroblob(500000) WHERE revision=0")) HostDeckEditDatabase(file()).use { db ->
            val a=create(db); val b=rename(db,a,"B"); db.sql("PRAGMA foreign_keys=OFF"); db.sql(sql); val state=db.state()
            failure(edits(db).undo(a.id,b.revision,op("bad"),cancel),FailureCode.CORRUPT_DATA); assertEquals(state,db.state())
        }
    }

    @Test fun exactCreationIdentityAcrossReachedHistoryIsMandatory() = runBlocking {
        for (changeId in listOf(false,true)) HostDeckEditDatabase(file()).use { db ->
            val a=create(db); val b=rename(db,a,"B")
            val corrupt = if (changeId) a.copy(id=DeckId(LocalDeckLibraryLimits.SOURCE,"other")) else a.copy(createdAt=UtcTimestamp(0))
            replaceRevision(db,corrupt,0); val state=db.state()
            failure(edits(db).undo(a.id,b.revision,op("bad"),cancel),FailureCode.CORRUPT_DATA); assertEquals(state,db.state())
        }
    }

    @Test fun wholeUndoRestoresCompletePackageTargetWithoutMixingGraphs() = runBlocking {
        for (target in listOf(false,true)) HostDeckEditDatabase(file()).use { db ->
            val a=create(db); val b=rename(db,a,"B")
            val packageInstance=PackageInstance(EntityId("instance"),EntityId("package"),EntityId("version"),PackageLinkPolicy.LINKED,SnapshotList.empty())
            val before = if(target) a.copy(packageInstances=SnapshotList.of(packageInstance)) else a
            val current = if(target) b else b.copy(packageInstances=SnapshotList.of(packageInstance))
            replaceRevision(db,before,0); replaceRevision(db,current,1)
            val state=db.state()
            if(!target) {
                unavailable(edits(db).apply(a.id,patch(),b.revision,op("package-a"),cancel),UnavailableReason.UNSUPPORTED)
                assertEquals(state,db.state())
            }
            // Supersedes only the former whole-undo UNSUPPORTED expectation: a complete
            // historical graph can now be restored, while primitive APPLY still cannot edit it.
            val restored = ok(edits(db).undo(a.id,b.revision,op("package-u"),cancel))
            content(before,restored)
            assertEquals(before.packageInstances,restored.packageInstances)
        }
    }

    @Test fun wholeUndoPreservesPackageGraphButPrimitiveEvenEmptyApplyRemainsUnsupported() = runBlocking {
        HostDeckEditDatabase(file()).use { db ->
            val a = create(db); val b = rename(db, a, "B")
            val instance = PackageInstance(EntityId("instance"), EntityId("package"), EntityId("version"), PackageLinkPolicy.LINKED,
                SnapshotList.of(PackageContribution(EntityId("package-entry"), a.entries.single().id, 2)))
            // Seed a consistent complete historical graph, retaining the actual persisted
            // revision/link/state machinery. This is an undo oracle, not package-edit acceptance.
            val before = a.copy(packageInstances = SnapshotList.of(instance))
            val current = b.copy(packageInstances = before.packageInstances)
            replaceRevision(db, before, 0); replaceRevision(db, current, 1)
            val frozen = db.state()
            unavailable(edits(db).apply(a.id, patch(), b.revision, op("empty-package"), cancel), UnavailableReason.UNSUPPORTED)
            assertEquals(frozen, db.state())
            val restored = ok(edits(db).undo(a.id, b.revision, op("package-undo"), cancel))
            content(before, restored); assertEquals(Revision(2), restored.revision)
            assertEquals(before.packageInstances, restored.packageInstances)
            assertEquals(restored, ok(library(db).load(a.id, cancel)))
            val noClock = edits(db, time { error("replay clock") })
            assertEquals(restored, ok(noClock.undo(a.id, b.revision, op("package-undo"), cancel)))
            assertEquals(1L, db.count("deck_undo_link"))
        }
    }

    @Test fun exhaustedSequencePreventsEditsButDoesNotPreventHistoricalReplay() = runBlocking {
        HostDeckEditDatabase(file()).use { db ->
            val a=create(db); val b=rename(db,a,"B"); db.sql("UPDATE deck_operation SET sequence=9223372036854775807 WHERE kind='APPLY'"); val state=db.state()
            failure(edits(db).undo(a.id,b.revision,op("new"),cancel),FailureCode.PERSISTENCE)
            failure(edits(db).apply(a.id,patch(),b.revision,op("empty"),cancel),FailureCode.PERSISTENCE)
            assertEquals(b,ok(edits(db).apply(a.id,patch(DeckEdit.Rename("B")),a.revision,op("edit-B"),cancel))); assertEquals(state,db.state())
        }
    }

    @Test fun revisionOverflowFailsBeforeClockAndEmptyPatchStillReceipts() = runBlocking {
        HostDeckEditDatabase(file()).use { db ->
            val a=create(db); val before=a.copy(revision=Revision(Long.MAX_VALUE-1)); val current=a.copy(name="Z",revision=Revision(Long.MAX_VALUE))
            insertRevision(db,before); insertRevision(db,current)
            db.sql("UPDATE deck_head SET revision=9223372036854775807,name='Z'")
            db.sql("INSERT INTO deck_undo_link VALUES('local-decks','deck',9223372036854775807,9223372036854775806,NULL)")
            db.sql("INSERT INTO deck_undo_state VALUES('local-decks','deck',9223372036854775807,9223372036854775807)")
            val editor=edits(db,time { error("overflow clock") }); val state=db.state()
            failure(editor.apply(a.id,patch(DeckEdit.Rename("B")),current.revision,op("overflow"),cancel),FailureCode.PERSISTENCE)
            failure(editor.undo(a.id,current.revision,op("overflow-u"),cancel),FailureCode.PERSISTENCE); assertEquals(state,db.state())
            assertEquals(current,ok(editor.apply(a.id,patch(),current.revision,op("empty"),cancel)))
        }
    }

    @Test fun validForeignKeyReceiptSwapsAreBoundToRequestedDeckAndResultRevision() = runBlocking {
        for (method in listOf("apply", "empty", "undo")) for (otherDeck in listOf(false, true)) HostDeckEditDatabase(file()).use { db ->
            val a = create(db)
            ok(edits(db).apply(a.id, patch(), a.revision, op("empty"), cancel))
            val b = rename(db, a, "B", "apply"); undo(db, b, "undo")
            val other = a.copy(id = DeckId(LocalDeckLibraryLimits.SOURCE, "other"), name = "Other")
            ok(library(db).create(other, op("other-create"), cancel))
            val other1 = rename(db, other, "Other1", "other1"); rename(db, other1, "Other2", "other2")
            val result = if (method == "empty") 0 else if (method == "apply") 1 else 2
            val changed = if (otherDeck) "deck_id='other',result_revision=$result" else "result_revision=${if (result == 0) 1 else 0}"
            db.sql("UPDATE deck_operation SET $changed WHERE operation_id='$method'")
            assertEquals(0L, db.host.scalar("SELECT count(*) FROM pragma_foreign_key_check"))
            db.sql("UPDATE deck_head SET name='corrupt current'")
            val state = db.state(); val replay = edits(db, time { error("replay clock") })
            val status = when (method) {
                "undo" -> replay.undo(a.id, b.revision, op(method), cancel)
                "empty" -> replay.apply(a.id, patch(), a.revision, op(method), cancel)
                else -> replay.apply(a.id, patch(DeckEdit.Rename("B")), a.revision, op(method), cancel)
            }
            failure(status, FailureCode.CORRUPT_DATA); assertEquals(state, db.state())
        }
    }

    @Test fun revisionZeroCannotHaveSyntheticUndoStateForAnyEditMethod() = runBlocking {
        HostDeckEditDatabase(file()).use { db ->
            val a = create(db)
            db.sql("INSERT INTO deck_undo_state VALUES('local-decks','deck',0,NULL)")
            assertEquals(0L, db.host.scalar("SELECT count(*) FROM pragma_foreign_key_check"))
            val state = db.state(); val editor = edits(db, time { error("corrupt state clock") })
            failure(editor.apply(a.id, patch(), a.revision, op("empty"), cancel), FailureCode.CORRUPT_DATA)
            failure(editor.apply(a.id, patch(DeckEdit.Rename("B")), a.revision, op("apply"), cancel), FailureCode.CORRUPT_DATA)
            failure(editor.undo(a.id, a.revision, op("undo"), cancel), FailureCode.CORRUPT_DATA)
            assertEquals(state, db.state())
        }
    }

    @Test fun immediatePredecessorReferencesMustExistEvenForApply() = runBlocking {
        for (sql in listOf("DELETE FROM deck_revision WHERE revision=1", "DELETE FROM deck_undo_link WHERE link_revision=1")) HostDeckEditDatabase(file()).use { db ->
            val a = create(db); val b = rename(db, a, "B"); val c = rename(db, b, "C"); val d = rename(db, c, "D")
            db.sql("PRAGMA foreign_keys=OFF"); db.sql(sql); val state = db.state()
            failure(edits(db).apply(a.id, patch(DeckEdit.Rename("E")), d.revision, op("bad"), cancel), FailureCode.CORRUPT_DATA)
            assertEquals(state, db.state())
        }
    }

    @Test fun libraryCreateReplayRequiresExactInitialSnapshotEvenWithValidForeignKey() = runBlocking {
        HostDeckEditDatabase(file()).use { db ->
            val a = create(db); val other = a.copy(id = DeckId(LocalDeckLibraryLimits.SOURCE, "other"))
            ok(library(db).create(other, op("other"), cancel))
            db.sql("UPDATE deck_operation SET deck_id='other' WHERE operation_id='create'")
            db.sql("UPDATE deck_head SET name='corrupt current'")
            val state = db.state(); failure(library(db).create(a, op("create"), cancel), FailureCode.CORRUPT_DATA); assertEquals(state, db.state())
        }
    }

    @Test fun libraryDuplicateReplayRejectsSourceIdentityWrongNameOrNonzeroRevision() = runBlocking {
        for (target in listOf("source", "name", "revision")) HostDeckEditDatabase(file()).use { db ->
            val a = create(db)
            val copy = ok(library(db).duplicate(a.id, a.revision, "Copy", op("duplicate"), cancel))
            val wrong = a.copy(id = DeckId(LocalDeckLibraryLimits.SOURCE, "wrong"), name = "Wrong")
            ok(library(db).create(wrong, op("wrong"), cancel)); rename(db, copy, "Copy", "copy-edit")
            val change = when (target) { "source" -> "deck_id='deck',result_revision=0"; "name" -> "deck_id='wrong',result_revision=0"; else -> "result_revision=1" }
            db.sql("UPDATE deck_operation SET $change WHERE operation_id='duplicate'")
            assertEquals(0L, db.host.scalar("SELECT count(*) FROM pragma_foreign_key_check"))
            db.sql("UPDATE deck_head SET name='corrupt current'")
            val state = db.state()
            val noGeneratedValues = SqlLocalDeckLibrary(db, codec, time { error("replay clock") }, object : IdSource { override fun nextId(): String = error("replay id") })
            failure(noGeneratedValues.duplicate(a.id, a.revision, "Copy", op("duplicate"), cancel), FailureCode.CORRUPT_DATA); assertEquals(state, db.state())
        }
    }

    @Test fun impossibleSuccessfulMaxRevisionReplayIsCorruptionWithoutClockOrCas() = runBlocking {
        for (undoMethod in listOf(false, true)) HostDeckEditDatabase(file()).use { db ->
            val a = create(db); val b = rename(db, a, "B", "apply"); undo(db, b, "undo")
            // Hostile receipt fixture, independently framed CLDR1 input; not a golden or production encoder.
            val bytes = java.io.ByteArrayOutputStream()
            java.io.DataOutputStream(bytes).use { out ->
                out.write(byteArrayOf(67, 76, 68, 82, 0, 1, (if (undoMethod) 3 else 2).toByte()))
                for (part in listOf(a.id.source.value.toByteArray(), a.id.raw.toByteArray())) { out.writeInt(part.size); out.write(part) }
                out.writeLong(Long.MAX_VALUE)
                if (!undoMethod) { val part = ok(patchCodec.encodePatch(patch(DeckEdit.Rename("B")))).copyBytes(); out.writeInt(part.size); out.write(part) }
            }
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
            val operation = if (undoMethod) "undo" else "apply"
            db.sql("UPDATE deck_operation SET request_sha256='$sha' WHERE operation_id='$operation'")
            db.sql("UPDATE deck_head SET name='corrupt current'"); val state = db.state()
            val replay = edits(db, time { error("impossible replay clock") })
            val result = if (undoMethod) replay.undo(a.id, Revision(Long.MAX_VALUE), op(operation), cancel)
                else replay.apply(a.id, patch(DeckEdit.Rename("B")), Revision(Long.MAX_VALUE), op(operation), cancel)
            failure(result, FailureCode.CORRUPT_DATA); assertEquals(state, db.state())
        }
    }

    private fun encoded(snapshot: DeckSnapshot): Pair<String,String> {
        val bytes=ok(codec.encodeSnapshot(snapshot)).copyBytes()
        return bytes.joinToString("") { "%02x".format(it.toInt() and 255) } to MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun insertRevision(db:HostDeckEditDatabase,snapshot:DeckSnapshot) {
        val (hex,sha)=encoded(snapshot)
        db.sql("INSERT INTO deck_revision VALUES('local-decks','deck',${snapshot.revision.value},'${snapshot.name}',${snapshot.createdAt.epochSeconds},${snapshot.createdAt.nanoAdjustment},${snapshot.modifiedAt.epochSeconds},${snapshot.modifiedAt.nanoAdjustment},${snapshot.entries.size},${snapshot.entries.sumOf { it.quantity.toLong() }},${hex.length/2},'$sha',x'$hex')")
    }
    private fun replaceRevision(db:HostDeckEditDatabase,snapshot:DeckSnapshot,revision:Long) {
        val (hex,sha)=encoded(snapshot)
        db.sql("UPDATE deck_revision SET name='${snapshot.name}',created_seconds=${snapshot.createdAt.epochSeconds},created_nano=${snapshot.createdAt.nanoAdjustment},payload=x'$hex',payload_bytes=${hex.length/2},payload_sha256='$sha' WHERE revision=$revision")
    }
}
