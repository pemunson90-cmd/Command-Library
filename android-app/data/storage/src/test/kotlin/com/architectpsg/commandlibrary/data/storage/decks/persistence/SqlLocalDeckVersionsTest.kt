package com.architectpsg.commandlibrary.data.storage.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.decks.codec.DeckPayloadCodec
import com.architectpsg.commandlibrary.data.storage.decks.codec.LocalDeckPatchCodec
import com.architectpsg.commandlibrary.feature.decks.DeckDocumentDiff
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckEdits
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckLibrary
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckVersions
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

class SqlLocalDeckVersionsTest {
    private val codec = DeckPayloadCodec()
    private val patchCodec = LocalDeckPatchCodec()
    private val cancel = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private var ticks = 10L
    private var generated = 0
    private val clock = time { UtcTimestamp(++ticks, 123) }
    private val ids = object : IdSource { override fun nextId() = "version-${++generated}" }
    private fun time(read: () -> UtcTimestamp) = object : Clock { override fun utcNow() = read(); override fun monotonicNow() = Duration.ZERO }
    private fun versions(db: Database, clock: Clock = this.clock, ids: IdSource = this.ids): LocalDeckVersions = SqlLocalDeckVersions(db, codec, clock, ids)
    private fun edits(db: Database) = SqlLocalDeckEdits(db, codec, patchCodec, clock)
    private fun library(db: Database) = SqlLocalDeckLibrary(db, codec, clock, object : IdSource { override fun nextId() = "copy-${++generated}" })
    private fun file() = File.createTempFile("deck-versions-", ".sqlite").also { it.deleteOnExit() }
    private fun op(value: String) = EntityId(value)
    private fun patch(vararg edits: DeckEdit) = DeckPatch(SnapshotList.copyOf(edits.asList()), "exact reason")
    private fun <T> ok(result: Outcome<T>): T { assertTrue(result.toString(), result is Outcome.Success); return (result as Outcome.Success).value }
    private fun failure(result: Outcome<*>, code: FailureCode) { assertTrue(result.toString(), result is Outcome.Failure); assertEquals(code, (result as Outcome.Failure).error.code) }
    private fun unavailable(result: Outcome<*>, reason: UnavailableReason) { assertTrue(result.toString(), result is Outcome.Unavailable); assertEquals(reason, (result as Outcome.Unavailable).reason) }
    private fun content(expected: DeckSnapshot, actual: DeckSnapshot) = assertEquals(expected.copy(revision = actual.revision, modifiedAt = actual.modifiedAt), actual)
    private fun deck(packages: Boolean = false): DeckSnapshot {
        val label = EntryLabel(op("category/界"), "Category")
        val entry = DeckEntry(op("entry"), CardIdentity(Knowledge.Known(OracleId(ProviderId("foreign"), "oracle/界")),
            Knowledge.Known(CardId(ProviderId("unknown-source"), "printing")), ProviderIdentity(ProviderId("raw-source"), "kind", "raw/unchanged"),
            Knowledge.Unknown("unknown finish", SnapshotList.empty()), LanguageTag("ja")), 9, BoardRole.COMMANDER, Knowledge.Known(Finish.FOIL), "notes", SnapshotList.of(label),
            SnapshotList.of(RoleOverride(op("entry"), Knowledge.Absent("none"), "role", true, UtcTimestamp(1))))
        val instances = if (!packages) SnapshotList.empty() else SnapshotList.of(
            PackageInstance(op("instance"), op("unknown-package"), op("unknown-version"), PackageLinkPolicy.LINKED, SnapshotList.of(PackageContribution(op("package-entry"), entry.id, 3))),
            PackageInstance(op("empty-instance"), op("unknown-package"), op("other-version"), PackageLinkPolicy.COPY, SnapshotList.empty()))
        return DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE, "deck"), "A", Knowledge.Absent("format"), "description", Knowledge.Absent("folder"), SnapshotList.of("a", "a", "界"),
            UtcTimestamp(1), UtcTimestamp(1), Revision(0), SnapshotList.of(entry), Knowledge.Absent("budget"), op("missing-profile"),
            SnapshotList.of(SnapshotReference(ProviderId("missing-source"), GenerationId("pinned"), Revision(9), LanguageTag("ja"))), instances, "# Primer", SnapshotList.of(label))
    }
    private suspend fun create(db: Database, packages: Boolean = false) = deck(packages).also { ok(library(db).create(it, op("create"), cancel)) }
    private suspend fun capture(db: Database, source: DeckSnapshot, operation: String = "capture", label: String = " exact 界 ") =
        ok(versions(db).capture(source.id, source.revision, label, op(operation), cancel))
    private suspend fun rename(db: Database, source: DeckSnapshot, name: String, operation: String = "edit-$name") =
        ok(edits(db).apply(source.id, patch(DeckEdit.Rename(name)), source.revision, op(operation), cancel))
    private suspend fun restore(db: Database, source: DeckSnapshot, target: DeckVersion, operation: String = "restore") =
        ok(versions(db).restore(source.id, target.id, source.revision, "Recovery", op(operation), cancel))

    @Test fun captureOnlyAddsReceiptAndMetadataDuplicateLabelsRemainDistinct() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val before = db.state(); val first = capture(db, a)
            val second = capture(db, a, "capture2")
            assertNotEquals(first.id, second.id); assertEquals(first.label, second.label); assertEquals(first.id, second.predecessorVersionId)
            assertEquals(a, first.manifest); assertEquals(a, second.manifest)
            assertEquals(before.filterKeys { it !in setOf("deck_operation", "deck_named_version", "sqlite_sequence") }, db.state().filterKeys { it !in setOf("deck_operation", "deck_named_version", "sqlite_sequence") })
            val list = ok(versions(db).list(a.id, PageRequest(100), cancel))
            assertEquals(listOf(second.id, first.id), list.items.map { it.id }); assertEquals(listOf(DeckVersionKind.USER, DeckVersionKind.USER), list.items.map { it.kind })
            assertEquals(Revision(3), list.revision)
        }
    }

    @Test fun checkpointBytesSurviveEditsDuplicateRestoreUndoAndBranch() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val checkpoint = capture(db, a); val bytes = db.state().getValue("deck_revision").first()
            val b = rename(db, a, "B"); val c = rename(db, b, "C")
            val copied = ok(library(db).duplicate(a.id, c.revision, "Copy", op("duplicate"), cancel)); assertNotEquals(a.id, copied.id)
            val r = restore(db, c, checkpoint); content(a, r.snapshot); assertEquals(c, r.recoveryVersion.manifest)
            assertEquals(Revision(3), r.snapshot.revision)
            val u = ok(edits(db).undo(a.id, r.snapshot.revision, op("undo-restore"), cancel)); content(c, u)
            val branch = rename(db, u, "D"); val u2 = ok(edits(db).undo(a.id, branch.revision, op("undo-branch"), cancel)); content(c, u2)
            val u3 = ok(edits(db).undo(a.id, u2.revision, op("undo-C"), cancel)); content(b, u3)
            assertEquals(checkpoint, ok(versions(db).load(a.id, checkpoint.id, cancel)))
            assertEquals(bytes, db.state().getValue("deck_revision").single { it[1] == "text:deck" && it[2] == "integer:0" })
            val listed = ok(versions(db).list(a.id, PageRequest(100), cancel)).items
            assertEquals(DeckVersionKind.RECOVERY, listed.first().kind); assertEquals(checkpoint.id, listed.first().restoredTargetVersionId)
            val recoveryRestore = restore(db, u3, r.recoveryVersion, "restore-recovery"); content(c, recoveryRestore.snapshot)
            assertEquals(r.recoveryVersion, ok(versions(db).load(a.id, r.recoveryVersion.id, cancel)))
        }
    }

    @Test fun previewUsesActualCurrentToHistoricalCompleteDiffAndDoesNotMutate() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val checkpoint = capture(db, a)
            val changed = ok(edits(db).apply(a.id, patch(DeckEdit.Rename("B"), DeckEdit.Description("changed"), DeckEdit.Primer("primer B"),
                DeckEdit.Categories(SnapshotList.empty()), DeckEdit.Format(Knowledge.Known(FormatId(ProviderId("foreign"), "format"))),
                DeckEdit.Metadata(Knowledge.Known("folder"), SnapshotList.of("new"), Knowledge.Known(DecimalAmount.exact("3.25", DecimalUnit.Fiat("USD"))), null),
                DeckEdit.Replace(a.entries.single().copy(quantity = 2, board = BoardRole.SIDEBOARD, notes = "other", finish = Knowledge.Absent("unknown"), labels = SnapshotList.empty(),
                    identity = a.entries.single().identity.copy(printingId = Knowledge.Absent("printing missing"))))), a.revision, op("edit"), cancel))
            val before = db.state(); val ticksBefore = ticks; val idsBefore = generated
            val preview = ok(versions(db).previewRestore(a.id, checkpoint.id, changed.revision, cancel))
            assertEquals(changed, preview.current); assertEquals(checkpoint, preview.target)
            assertEquals(ok(DeckDocumentDiff.compare(changed, a)), preview.difference)
            assertEquals(before, db.state()); assertEquals(ticksBefore, ticks); assertEquals(idsBefore, generated)
            val restored = restore(db, changed, checkpoint); content(a, restored.snapshot)
        }
    }

    @Test fun completePackageGraphUnknownEvidenceAndEmptyInstancesSurviveRestoreAndUndo() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db, true); val checkpoint = capture(db, a)
            // Seed a complete historical graph as an oracle; primitive package APPLY remains unsupported.
            val changed = a.copy(name = "B", revision = Revision(1), modifiedAt = UtcTimestamp(20), primer = "other", categoryOrder = SnapshotList.empty(),
                entries = SnapshotList.of(a.entries.single().copy(labels = SnapshotList.empty(), board = BoardRole.SIDEBOARD, notes = "different")),
                packageInstances = SnapshotList.of(a.packageInstances.first().copy(policy = PackageLinkPolicy.COPY)))
            seed(db, changed); ticks = 20
            val currentCheckpoint = capture(db, changed, "B"); val copied = ok(library(db).duplicate(a.id, changed.revision, "Copy", op("duplicate"), cancel))
            assertEquals(copied.entries.single().id, copied.packageInstances.single().contributions.single().deckEntryId)
            val preview = ok(versions(db).previewRestore(a.id, checkpoint.id, changed.revision, cancel)); assertTrue(DeckChangedField.PACKAGES in preview.difference.changedFields)
            val restored = restore(db, changed, checkpoint); content(a, restored.snapshot)
            assertEquals(a.packageInstances, restored.snapshot.packageInstances); assertEquals(2, restored.snapshot.packageInstances.size)
            val undone = ok(edits(db).undo(a.id, restored.snapshot.revision, op("undo"), cancel)); content(changed, undone)
            assertEquals(currentCheckpoint, ok(versions(db).load(a.id, currentCheckpoint.id, cancel)))
            val fromRecovery = restore(db, undone, restored.recoveryVersion, "recovery-target"); content(changed, fromRecovery.snapshot)
        }
    }

    @Test fun identicalRestoreStillCreatesRecoveryRevisionAndUndoGroup() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a); val r = restore(db, a, v)
            content(a, r.snapshot); assertEquals(Revision(1), r.snapshot.revision); assertEquals(1L, db.count("deck_undo_link"))
            content(a, ok(edits(db).undo(a.id, r.snapshot.revision, op("undo"), cancel)))
        }
    }

    @Test fun replayPrecedesCorruptCurrentCasClockIdsAndLaterCheckpoints() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a); val b = rename(db, a, "B"); val r = restore(db, b, v)
            capture(db, r.snapshot, "later"); db.sql("UPDATE deck_head SET name='corrupt'")
            val reader = versions(db, time { error("replay clock") }, object : IdSource { override fun nextId(): String = error("replay ID") })
            val before = db.state(); db.queries.clear()
            assertEquals(v, ok(reader.capture(a.id, a.revision, v.label, op("capture"), cancel)))
            assertEquals(r, ok(reader.restore(a.id, v.id, b.revision, "Recovery", op("restore"), cancel)))
            assertFalse(db.queries.any { it.statement.contains("FROM deck_head") || it.statement.contains("o.kind=:kind") })
            assertEquals(before, db.state())
            failure(reader.capture(a.id, a.revision, "different", op("capture"), cancel), FailureCode.INVALID_INPUT)
            failure(reader.restore(a.id, v.id, Revision(0), "Recovery", op("restore"), cancel), FailureCode.INVALID_INPUT)
        }
    }

    @Test fun lostAcknowledgementsReopenAndTwoConnectionCasRetainOriginalGeneratedIds() = runBlocking {
        val file = file(); lateinit var a: DeckSnapshot; lateinit var v: DeckVersion; lateinit var r: DeckRestoreResult
        HostDeckVersionDatabase(file).use { db ->
            a = create(db); db.loseAcknowledgement = true
            try { versions(db).capture(a.id, a.revision, "saved", op("capture"), cancel); fail("lost ack") } catch (_: CancellationException) { }
            db.loseAcknowledgement = false; val before = db.state(); v = ok(versions(db).capture(a.id, a.revision, "saved", op("capture"), cancel)); assertEquals(before, db.state())
            val b = rename(db, a, "B"); db.loseAcknowledgement = true
            try { versions(db).restore(a.id, v.id, b.revision, "Recovery", op("restore"), cancel); fail("lost ack") } catch (_: CancellationException) { }
            db.loseAcknowledgement = false; r = ok(versions(db).restore(a.id, v.id, b.revision, "Recovery", op("restore"), cancel))
        }
        HostDeckVersionDatabase(file, false).use { first -> HostDeckVersionDatabase(file, false).use { second ->
            assertEquals(v, ok(versions(first).load(a.id, v.id, cancel))); assertEquals(r, ok(versions(second).restore(a.id, v.id, Revision(1), "Recovery", op("restore"), cancel)))
            val next = rename(first, r.snapshot, "branch"); val before = second.state()
            failure(versions(second).restore(a.id, v.id, r.snapshot.revision, "stale", op("stale"), cancel), FailureCode.STALE_REVISION)
            failure(versions(second).capture(a.id, r.snapshot.revision, "stale", op("stale-c"), cancel), FailureCode.STALE_REVISION)
            assertEquals(before, second.state()); assertEquals(next, ok(library(second).load(a.id, cancel)))
        } }
    }

    @Test fun allSixMutationMethodsShareGlobalOperationNamespace() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a); val b = rename(db, a, "B", "apply"); val r = restore(db, b, v)
            val u = ok(edits(db).undo(a.id, r.snapshot.revision, op("undo"), cancel)); ok(library(db).duplicate(a.id, u.revision, "Copy", op("duplicate"), cancel))
            val before = db.state()
            for (operation in listOf("create", "capture", "apply", "restore", "undo", "duplicate")) {
                if (operation != "capture") failure(versions(db).capture(a.id, Revision(0), v.label, op(operation), cancel), FailureCode.INVALID_INPUT)
                if (operation != "restore") failure(versions(db).restore(a.id, v.id, b.revision, "Recovery", op(operation), cancel), FailureCode.INVALID_INPUT)
                if (operation == "capture" || operation == "restore") {
                    failure(library(db).create(a, op(operation), cancel), FailureCode.INVALID_INPUT)
                    failure(library(db).duplicate(a.id, u.revision, "Copy", op(operation), cancel), FailureCode.INVALID_INPUT)
                    failure(edits(db).apply(a.id, patch(), u.revision, op(operation), cancel), FailureCode.INVALID_INPUT)
                    failure(edits(db).undo(a.id, u.revision, op(operation), cancel), FailureCode.INVALID_INPUT)
                }
            }
            assertEquals(before, db.state())
        }
    }

    @Test fun absentDeckEmptyHistoryAndWrongDeckTargetsHaveDistinctNotFoundSemantics() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val missing = deck().id.copy(raw = "missing")
            unavailable(versions(db).list(missing, PageRequest(10), cancel), UnavailableReason.NOT_FOUND)
            val a = create(db); assertTrue(ok(versions(db).list(a.id, PageRequest(10), cancel)).items.isEmpty())
            unavailable(versions(db).load(a.id, op("missing"), cancel), UnavailableReason.NOT_FOUND)
            val v = capture(db, a); val copy = ok(library(db).duplicate(a.id, a.revision, "Copy", op("copy"), cancel))
            unavailable(versions(db).load(copy.id, v.id, cancel), UnavailableReason.NOT_FOUND)
            unavailable(versions(db).restore(copy.id, v.id, copy.revision, "Recovery", op("wrong"), cancel), UnavailableReason.NOT_FOUND)
        }
    }

    @Test fun pagesBeyond101UseScopedCanonicalGlobalEpochCursors() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val saved = (1..105).map { capture(db, a, "capture-$it", "same") }
            db.queries.clear(); val first = ok(versions(db).list(a.id, PageRequest(100), cancel))
            assertEquals(saved.reversed().take(100).map { it.id }, first.items.map { it.id }); assertEquals(72, first.nextCursor!!.length)
            val second = ok(versions(db).list(a.id, PageRequest(100, first.nextCursor), cancel)); assertEquals(saved.take(5).reversed().map { it.id }, second.items.map { it.id }); assertNull(second.nextCursor)
            assertTrue(db.queries.all { it.maxRows <= 101 }); assertFalse(db.queries.any { it.statement.startsWith("SELECT payload") })
            failure(versions(db).list(a.id, PageRequest(10, first.nextCursor + "="), cancel), FailureCode.INVALID_INPUT)
            failure(versions(db).list(a.id.copy(raw = "other"), PageRequest(10, first.nextCursor), cancel), FailureCode.INVALID_INPUT)
            ok(edits(db).apply(a.id, patch(), a.revision, op("empty"), cancel)); db.queries.clear()
            failure(versions(db).list(a.id, PageRequest(10, first.nextCursor), cancel), FailureCode.STALE_REVISION)
            assertFalse(db.queries.any { it.statement.contains("n.creating_sequence=:sequence") })
        }
    }

    @Test fun clockEqualAllowedAndLatestCaptureTimeGatesBothWritesBeforeIds() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val at100 = versions(db, time { UtcTimestamp(100) })
            val v = ok(at100.capture(a.id, a.revision, "100", op("capture"), cancel)); val before = db.state(); val idsBefore = generated
            val at50 = versions(db, time { UtcTimestamp(50) })
            failure(at50.capture(a.id, a.revision, "bad", op("bad-c"), cancel), FailureCode.INTERNAL)
            failure(at50.restore(a.id, v.id, a.revision, "bad", op("bad-r"), cancel), FailureCode.INTERNAL)
            assertEquals(before, db.state()); assertEquals(idsBefore, generated)
            val r = ok(at100.restore(a.id, v.id, a.revision, "equal", op("equal"), cancel)); assertEquals(UtcTimestamp(100), r.snapshot.modifiedAt); assertEquals(r.snapshot.modifiedAt, r.recoveryVersion.createdAt)
        }
    }

    @Test fun invalidStaleClockAndIdExceptionsNeverPartiallyWrite() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a); val before = db.state()
            failure(versions(db).capture(a.id, a.revision, " ", op("blank"), cancel), FailureCode.INVALID_INPUT)
            failure(versions(db).capture(a.id, a.revision, "\uD800", op("surrogate"), cancel), FailureCode.INVALID_INPUT)
            failure(versions(db).capture(a.id, a.revision, "界".repeat(342), op("long"), cancel), FailureCode.INVALID_INPUT)
            val noClock = versions(db, time { error("unexpected clock") }, object : IdSource { override fun nextId(): String = error("unexpected ID") })
            failure(noClock.capture(a.id, Revision(1), "stale", op("stale"), cancel), FailureCode.STALE_REVISION)
            failure(noClock.previewRestore(a.id, v.id, Revision(1), cancel), FailureCode.STALE_REVISION)
            for (restore in listOf(false, true)) {
                val badClock = versions(db, time { throw IllegalStateException("private") })
                val result = if (restore) badClock.restore(a.id, v.id, a.revision, "R", op("clock"), cancel) else badClock.capture(a.id, a.revision, "C", op("clock"), cancel)
                failure(result, FailureCode.INTERNAL); assertFalse((result as Outcome.Failure).error.message.contains("private"))
                val badIds = versions(db, ids = object : IdSource { override fun nextId(): String = error("private") })
                failure(if (restore) badIds.restore(a.id, v.id, a.revision, "R", op("ids"), cancel) else badIds.capture(a.id, a.revision, "C", op("ids"), cancel), FailureCode.INTERNAL)
            }
            assertEquals(before, db.state())
        }
    }

    @Test fun generatedVersionCollisionFailsOnceWithoutRetry() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a); var calls = 0
            val duplicate = versions(db, ids = object : IdSource { override fun nextId(): String { calls++; return v.id.value } }); val before = db.state()
            failure(duplicate.capture(a.id, a.revision, "C", op("collision-c"), cancel), FailureCode.INTERNAL)
            failure(duplicate.restore(a.id, v.id, a.revision, "R", op("collision-r"), cancel), FailureCode.INTERNAL)
            assertEquals(2, calls); assertEquals(before, db.state())
        }
    }

    @Test fun sequenceExhaustionPrecedesClockIdsAndWrites() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a); db.sql("UPDATE sqlite_sequence SET seq=9223372036854775807 WHERE name='deck_operation'")
            val before = db.state(); val reader = versions(db, time { error("clock") }, object : IdSource { override fun nextId(): String = error("ID") })
            failure(reader.capture(a.id, a.revision, "C", op("max-c"), cancel), FailureCode.PERSISTENCE)
            failure(reader.restore(a.id, v.id, a.revision, "R", op("max-r"), cancel), FailureCode.PERSISTENCE)
            assertEquals(before, db.state())
        }
    }

    @Test fun revisionExhaustionIsCheckedBeforeClockOrGeneratedRecoveryId() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a)
            db.sql("INSERT INTO deck_revision SELECT provider,deck_id,9223372036854775807,name,created_seconds,created_nano,modified_seconds,modified_nano,entry_count,total_quantity,payload_bytes,payload_sha256,payload FROM deck_revision WHERE revision=0")
            val terminal = a.copy(revision = Revision(Long.MAX_VALUE)); replaceRevision(db, terminal)
            db.sql("UPDATE deck_head SET revision=9223372036854775807")
            val before = db.state(); val reader = versions(db, time { error("clock") }, object : IdSource { override fun nextId(): String = error("ID") })
            failure(reader.restore(a.id, v.id, terminal.revision, "R", op("max"), cancel), FailureCode.PERSISTENCE)
            assertEquals(before, db.state())
        }
    }

    @Test fun ignoredWritesAreTypedFailuresAndRollbackEarlierPrefixes() = runBlocking {
        for (table in listOf("deck_operation", "deck_named_version", "deck_undo_link", "deck_undo_state")) HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a); val before = db.state()
            db.sql("CREATE TRIGGER reject_version_write BEFORE INSERT ON $table BEGIN SELECT RAISE(IGNORE); END")
            failure(versions(db).restore(a.id, v.id, a.revision, "R", op("ignored"), cancel), FailureCode.PERSISTENCE)
            assertEquals(before, db.state())
        }
    }

    @Test fun allWritePrefixesFailAtomicallyIncludingAutoincrement() = runBlocking { atomicPrefixes(false) }
    @Test fun allWritePrefixesCancelAtomicallyAndEscapeUnchanged() = runBlocking { atomicPrefixes(true) }
    private suspend fun atomicPrefixes(cancellation: Boolean) {
        for (mode in 0..2) for (prefix in 1..if (mode == 0) 2 else 6) HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a); val source = if (mode == 2) rename(db, a, "B") else a
            val before = db.state(); var writes = 0; val injected = CancellationException("injected")
            db.host.afterWrite = { if (++writes == prefix) { if (cancellation) throw injected else error("injected") } }
            try {
                val result = if (mode == 0) versions(db).capture(a.id, source.revision, "next", op("fault"), cancel) else versions(db).restore(a.id, v.id, source.revision, "Recovery", op("fault"), cancel)
                if (cancellation) fail("expected cancellation") else failure(result, FailureCode.PERSISTENCE)
            } catch (actual: CancellationException) { assertTrue(cancellation); assertSame(injected, actual) }
            db.host.afterWrite = null; assertEquals("mode=$mode prefix=$prefix", before, db.state())
        }
    }

    @Test fun cancelledReadLoopsAndClockIdCancellationPropagateWithoutWrites() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a); val before = db.state(); val thrown = CancellationException("exact")
            val token = object : CancelToken { var checks = 0; override val isCancelled get() = checks > 8; override fun throwIfCancelled() { if (++checks > 8) throw thrown } }
            try { versions(db).load(a.id, v.id, token); fail("cancel") } catch (actual: CancellationException) { assertSame(thrown, actual) }
            for (reader in listOf(versions(db, time { throw thrown }), versions(db, ids = object : IdSource { override fun nextId(): String = throw thrown }))) {
                try { reader.capture(a.id, a.revision, "C", op("cancel-c"), cancel); fail("cancel") } catch (actual: CancellationException) { assertSame(thrown, actual) }
                try { reader.restore(a.id, v.id, a.revision, "R", op("cancel-r"), cancel); fail("cancel") } catch (actual: CancellationException) { assertSame(thrown, actual) }
            }
            assertEquals(before, db.state())
        }
    }

    @Test fun malformedNamedMetadataIsRejectedBeforeAnySelectedPayloadFetchOnLoadAndList() = runBlocking {
        for (corruption in listOf("label=CAST(x'80' AS TEXT)", "label='${"x".repeat(1025)}'", "created_nano=1000000000", "source_revision='wrong'", "metadata_sha256='bad'", "kind='OTHER'", "metadata_version=0")) HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a); db.sql("PRAGMA foreign_keys=OFF"); db.sql("UPDATE deck_named_version SET $corruption"); db.sql("PRAGMA foreign_keys=ON")
            val before = db.state(); db.queries.clear()
            failure(versions(db).load(a.id, v.id, cancel), FailureCode.CORRUPT_DATA); assertEquals(0, db.payloadReads(0))
            failure(versions(db).list(a.id, PageRequest(10), cancel), FailureCode.CORRUPT_DATA); assertEquals(0, db.payloadReads(0))
            assertEquals(before, db.state()); assertTrue(db.queries.any { it.statement.contains("FROM deck_named_version") })
        }
    }

    @Test fun newerMetadataIsUnsupportedWithoutSelectedBlobFetch() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a); db.sql("UPDATE deck_named_version SET metadata_version=2"); db.queries.clear()
            unavailable(versions(db).load(a.id, v.id, cancel), UnavailableReason.UNSUPPORTED)
            unavailable(versions(db).list(a.id, PageRequest(10), cancel), UnavailableReason.UNSUPPORTED)
            assertEquals(0, db.payloadReads(0))
        }
    }

    @Test fun validFkSourceSwapWithRecomputedMetadataStillFailsCreatingRequestBinding() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); capture(db, a); val b = rename(db, a, "B"); val v = capture(db, b, "second")
            db.sql("UPDATE deck_named_version SET source_revision=0 WHERE version_id=${q(v.id.value)}"); rehash(db, v.id)
            assertEquals(0L, db.host.scalar("SELECT count(*) FROM pragma_foreign_key_check")); val before = db.state(); db.queries.clear()
            failure(versions(db).load(a.id, v.id, cancel), FailureCode.CORRUPT_DATA); assertEquals(0, db.payloadReads(0)); assertEquals(before, db.state())
        }
    }

    @Test fun validFkPredecessorSwapWithValidHashFailsActualImmediatePredecessorCheck() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val first = capture(db, a, "first"); capture(db, a, "second"); val third = capture(db, a, "third")
            db.sql("UPDATE deck_named_version SET predecessor_version_id=${q(first.id.value)} WHERE version_id=${q(third.id.value)}"); rehash(db, third.id)
            val before = db.state(); db.queries.clear(); failure(versions(db).load(a.id, third.id, cancel), FailureCode.CORRUPT_DATA)
            assertEquals(0, db.payloadReads(0)); assertEquals(before, db.state()); assertEquals(0L, db.host.scalar("SELECT count(*) FROM pragma_foreign_key_check"))
        }
    }

    @Test fun validFkRecoveryTargetSwapAndReceiptSwapRemainCorruptWithValidMetadataHashes() = runBlocking {
        for (swapTarget in listOf(false, true)) HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val first = capture(db, a, "first"); val b = rename(db, a, "B"); val second = capture(db, b, "second"); val r = restore(db, b, first)
            if (swapTarget) db.sql("UPDATE deck_named_version SET restored_target_version_id=${q(second.id.value)} WHERE version_id=${q(r.recoveryVersion.id.value)}")
            else { db.sql("UPDATE deck_named_version SET creating_sequence=(SELECT sequence FROM deck_operation WHERE operation_id='edit-B') WHERE version_id=${q(r.recoveryVersion.id.value)}") }
            rehash(db, r.recoveryVersion.id); val before = db.state(); db.queries.clear()
            failure(versions(db).load(a.id, r.recoveryVersion.id, cancel), FailureCode.CORRUPT_DATA); assertEquals(0, db.payloadReads(b.revision.value)); assertEquals(before, db.state())
            assertEquals(0L, db.host.scalar("SELECT count(*) FROM pragma_foreign_key_check"))
        }
    }

    @Test fun newestMissingAssociationNeverFallsBackToAnOlderCheckpoint() = runBlocking {
        for (restore in listOf(false, true)) HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a)
            val source = if (restore) restore(db, a, v).snapshot else a
            if (!restore) capture(db, a, "newest")
            db.sql("DELETE FROM deck_named_version WHERE creating_sequence=(SELECT MAX(creating_sequence) FROM deck_named_version)")
            val before = db.state()
            failure(versions(db).list(a.id, PageRequest(10), cancel), FailureCode.CORRUPT_DATA)
            failure(versions(db).load(a.id, v.id, cancel), FailureCode.CORRUPT_DATA)
            failure(versions(db).capture(a.id, source.revision, "new", op("new"), cancel), FailureCode.CORRUPT_DATA)
            failure(versions(db).restore(a.id, v.id, source.revision, "R", op("new-r"), cancel), FailureCode.CORRUPT_DATA)
            // Exact older receipt replay deliberately does not consult the latest fence.
            assertEquals(v, ok(versions(db).capture(a.id, a.revision, v.label, op("capture"), cancel)))
            assertEquals(before, db.state())
        }
    }

    @Test fun selectedPayloadHashBytesAndMetadataFailWithoutMutation() = runBlocking {
        for (corruption in listOf("payload=x'00'", "payload_sha256='${"0".repeat(64)}'", "payload_bytes=9000000", "payload_bytes='wrong'", "payload=CAST('bad' AS TEXT)")) HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a); db.sql("UPDATE deck_revision SET $corruption WHERE revision=0")
            val before = db.state(); failure(versions(db).load(a.id, v.id, cancel), FailureCode.CORRUPT_DATA); assertEquals(before, db.state())
        }
    }

    @Test fun historicalCreatedTimeContradictionCannotBeHiddenByRestoreCopy() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val b = rename(db, a, "B"); val v = capture(db, b)
            replaceRevision(db, b.copy(createdAt = UtcTimestamp(2)))
            val before = db.state(); db.queries.clear()
            failure(versions(db).load(a.id, v.id, cancel), FailureCode.CORRUPT_DATA); assertEquals(0, db.payloadReads(1))
            failure(versions(db).restore(a.id, v.id, b.revision, "R", op("bad"), cancel), FailureCode.CORRUPT_DATA); assertEquals(before, db.state())
        }
    }

    @Test fun recoveryCommittedResultMustEqualDirectTargetEvenWithValidPayloadHash() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a); val b = rename(db, a, "B"); val r = restore(db, b, v)
            replaceRevision(db, r.snapshot.copy(primer = "wrong committed target")); val before = db.state()
            failure(versions(db).load(a.id, r.recoveryVersion.id, cancel), FailureCode.CORRUPT_DATA)
            failure(versions(db).restore(a.id, v.id, b.revision, "Recovery", op("restore"), cancel), FailureCode.CORRUPT_DATA)
            assertEquals(before, db.state())
        }
    }

    @Test fun replayReceiptValidFkWrongResultAndWrongDeckAreNotAccepted() = runBlocking {
        for (wrongDeck in listOf(false, true)) HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a); val b = rename(db, a, "B"); val copy = ok(library(db).duplicate(a.id, b.revision, "Copy", op("duplicate"), cancel))
            db.sql(if (wrongDeck) "UPDATE deck_operation SET deck_id=${q(copy.id.raw)},result_revision=0 WHERE operation_id='capture'" else "UPDATE deck_operation SET result_revision=1 WHERE operation_id='capture'")
            val before = db.state(); db.queries.clear()
            failure(versions(db).capture(a.id, a.revision, v.label, op("capture"), cancel), FailureCode.CORRUPT_DATA)
            assertFalse(db.queries.any { it.statement.contains("FROM deck_head") }); assertEquals(before, db.state())
            assertEquals(0L, db.host.scalar("SELECT count(*) FROM pragma_foreign_key_check"))
        }
    }

    @Test fun coherentOlderHeadCannotCreateVersionsBehindLatestNamedReceipt() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val b = rename(db, a, "B"); val c = rename(db, b, "C"); val v = capture(db, c)
            db.sql("UPDATE deck_head SET revision=1,name='B',modified_seconds=${b.modifiedAt.epochSeconds},modified_nano=${b.modifiedAt.nanoAdjustment}")
            db.sql("UPDATE deck_undo_state SET head_revision=1,undo_link_revision=1")
            val before = db.state(); val noGenerated = versions(db, time { error("clock") }, object : IdSource { override fun nextId(): String = error("ID") })
            failure(noGenerated.capture(a.id, b.revision, "C", op("back-c"), cancel), FailureCode.CORRUPT_DATA)
            failure(noGenerated.restore(a.id, v.id, b.revision, "R", op("back-r"), cancel), FailureCode.CORRUPT_DATA)
            assertEquals(before, db.state()); assertEquals(0L, db.host.scalar("SELECT count(*) FROM pragma_foreign_key_check"))
        }
    }

    @Test fun currentMustBeHighestImmutableRevisionEvenWithoutANewerNamedCheckpoint() = runBlocking {
        for (orphan in listOf(false, true)) HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a); val b = rename(db, a, "B"); val c = rename(db, b, "C")
            if (orphan) {
                db.sql("INSERT INTO deck_revision SELECT provider,deck_id,3,name,created_seconds,created_nano,modified_seconds,modified_nano,entry_count,total_quantity,payload_bytes,payload_sha256,payload FROM deck_revision WHERE revision=2")
                replaceRevision(db, c.copy(revision = Revision(3)))
            } else {
                db.sql("UPDATE deck_head SET revision=1,name='B',modified_seconds=${b.modifiedAt.epochSeconds},modified_nano=${b.modifiedAt.nanoAdjustment}")
                db.sql("UPDATE deck_undo_state SET head_revision=1,undo_link_revision=1")
            }
            val current = if (orphan) c else b; val before = db.state()
            val noGenerated = versions(db, time { error("clock") }, object : IdSource { override fun nextId(): String = error("ID") })
            failure(noGenerated.capture(a.id, current.revision, "C", op("old-c"), cancel), FailureCode.CORRUPT_DATA)
            failure(noGenerated.restore(a.id, v.id, current.revision, "R", op("old-r"), cancel), FailureCode.CORRUPT_DATA)
            failure(noGenerated.previewRestore(a.id, v.id, current.revision, cancel), FailureCode.CORRUPT_DATA)
            assertEquals(v, ok(noGenerated.load(a.id, v.id, cancel)))
            assertEquals(v.id, ok(noGenerated.list(a.id, PageRequest(10), cancel)).items.single().id)
            assertEquals(before, db.state()); assertEquals(0L, db.host.scalar("SELECT count(*) FROM pragma_foreign_key_check"))
        }
    }

    @Test fun predecessorAndDirectTargetRecoveryResultMustNotExceedSelectedSource() = runBlocking {
        for (testTarget in listOf(false, true)) HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val v = capture(db, a); val r = restore(db, a, v)
            val bridge = if (testTarget) capture(db, r.snapshot, "bridge", "bridge") else null
            val next = capture(db, r.snapshot, "next", "same")
            if (bridge != null) {
                db.sql("UPDATE deck_named_version SET source_revision=0 WHERE version_id=${q(bridge.id.value)}"); rehash(db, bridge.id)
                db.sql("UPDATE deck_operation SET result_revision=0,request_sha256='${requestFingerprint(a.id, Revision(0), "bridge")}' WHERE operation_id='bridge'")
            }
            // Both source revisions can be zero while a preceding recovery receipt wrote revision one.
            db.sql("UPDATE deck_named_version SET source_revision=0 WHERE version_id=${q(next.id.value)}"); rehash(db, next.id)
            val forged = requestFingerprint(a.id, Revision(0), "same")
            db.sql("UPDATE deck_operation SET result_revision=0,request_sha256='$forged' WHERE operation_id='next'")
            if (testTarget) {
                // A recovery whose direct target is the earlier recovery must still respect its result revision.
                db.sql("UPDATE deck_named_version SET kind='RECOVERY',restored_target_version_id=${q(r.recoveryVersion.id.value)} WHERE version_id=${q(next.id.value)}")
                db.sql("UPDATE deck_operation SET kind='RESTORE',result_revision=1,request_sha256='${requestFingerprint(a.id, Revision(0), "same", r.recoveryVersion.id)}' WHERE operation_id='next'")
                rehash(db, next.id)
            }
            val before = db.state(); db.queries.clear()
            failure(versions(db).load(a.id, next.id, cancel), FailureCode.CORRUPT_DATA)
            assertEquals(0, db.payloadReads(0)); assertEquals(before, db.state())
        }
    }

    @Test fun retainedCursorAnchorDeletionIsCorruptButFabricatedNonNamedAnchorIsInvalid() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val saved = (1..6).map { capture(db, a, "capture-$it") }
            val first = ok(versions(db).list(a.id, PageRequest(4), cancel)); assertEquals(saved[2].id, first.items.last().id)
            db.sql("PRAGMA foreign_keys=OFF"); db.sql("DELETE FROM deck_named_version WHERE version_id=${q(saved[2].id.value)}"); db.sql("PRAGMA foreign_keys=ON")
            val before = db.state(); db.queries.clear()
            failure(versions(db).list(a.id, PageRequest(2, first.nextCursor), cancel), FailureCode.CORRUPT_DATA)
            assertEquals(before, db.state()); assertTrue(db.queries.any { it.statement.contains("FROM deck_operation o WHERE o.sequence=:sequence") })
            // Canonical scoped cursor pointing to CREATE rather than named receipt is caller-invalid.
            val bytes = java.util.Base64.getUrlDecoder().decode(first.nextCursor)
            java.nio.ByteBuffer.wrap(bytes).putLong(14, 1L)
            val invented = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            failure(versions(db).list(a.id, PageRequest(2, invented), cancel), FailureCode.INVALID_INPUT)
        }
    }

    @Test fun actualSqlitePlansUseFixedKindAndNamedAssociationAndPredecessorIndexes() = runBlocking {
        HostDeckVersionDatabase(file()).use { db ->
            val a = create(db); val first = capture(db, a); capture(db, a, "next"); ok(versions(db).capture(a.id, a.revision, first.label, op("capture"), cancel))
            val plans = db.plans()
            val evidence = File("/tmp/cl-deck-versions-store-01/evidence"); evidence.mkdirs(); File(evidence, "sqlite-query-plans.txt").writeText(plans.joinToString("\n\n"))
            assertTrue(plans.toString(), plans.size >= 3)
            assertTrue(plans.toString(), plans.any { it.startsWith("latest receipt:") && it.contains("index_deck_operation_version_latest") })
            assertTrue(plans.toString(), plans.any { it.startsWith("exact receipt association:") && (it.contains("index_deck_named_version_receipt") || it.contains("index_deck_named_version_order")) })
            assertTrue(plans.toString(), plans.any { it.startsWith("immediate predecessor:") && it.contains("index_deck_named_version_order") })
            assertFalse(plans.joinToString("\n").contains("SCAN ")); assertFalse(plans.joinToString("\n").contains("TEMP B-TREE"))
        }
    }

    private fun q(text: String) = "'${text.replace("'", "''")}'"
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun replaceRevision(db: HostDeckVersionDatabase, snapshot: DeckSnapshot) {
        val bytes = ok(codec.encodeSnapshot(snapshot)).copyBytes()
        db.sql("UPDATE deck_revision SET name=${q(snapshot.name)},created_seconds=${snapshot.createdAt.epochSeconds},created_nano=${snapshot.createdAt.nanoAdjustment},modified_seconds=${snapshot.modifiedAt.epochSeconds},modified_nano=${snapshot.modifiedAt.nanoAdjustment},entry_count=${snapshot.entries.size},total_quantity=${snapshot.entries.sumOf { it.quantity.toLong() }},payload_bytes=${bytes.size},payload_sha256='${sha(bytes)}',payload=x'${hex(bytes)}' WHERE deck_id=${q(snapshot.id.raw)} AND revision=${snapshot.revision.value}")
    }
    /** Explicit SQL oracle for a complete historical group, not a package edit implementation. */
    private fun seed(db: HostDeckVersionDatabase, snapshot: DeckSnapshot) {
        val source = snapshot.revision.value - 1
        db.sql("INSERT INTO deck_revision SELECT provider,deck_id,${snapshot.revision.value},name,created_seconds,created_nano,modified_seconds,modified_nano,entry_count,total_quantity,payload_bytes,payload_sha256,payload FROM deck_revision WHERE deck_id=${q(snapshot.id.raw)} AND revision=$source")
        replaceRevision(db, snapshot)
        db.sql("UPDATE deck_head SET revision=${snapshot.revision.value},name=${q(snapshot.name)},modified_seconds=${snapshot.modifiedAt.epochSeconds},modified_nano=${snapshot.modifiedAt.nanoAdjustment} WHERE deck_id=${q(snapshot.id.raw)}")
        db.sql("INSERT INTO deck_operation(operation_id,request_version,request_sha256,kind,provider,deck_id,result_revision) VALUES ('oracle',1,'${"a".repeat(64)}','APPLY',${q(snapshot.id.source.value)},${q(snapshot.id.raw)},${snapshot.revision.value})")
        db.sql("INSERT INTO deck_undo_link VALUES (${q(snapshot.id.source.value)},${q(snapshot.id.raw)},${snapshot.revision.value},$source,NULL)")
        db.sql("INSERT INTO deck_undo_state VALUES (${q(snapshot.id.source.value)},${q(snapshot.id.raw)},${snapshot.revision.value},${snapshot.revision.value})")
    }
    /** Independent CLDR bytes for semantic-corruption fixtures, not production-generated expectations. */
    private fun requestFingerprint(id: DeckId, revision: Revision, label: String, target: EntityId? = null): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            fun field(value: String) { val encoded = value.toByteArray(Charsets.UTF_8); out.writeInt(encoded.size); out.write(encoded) }
            out.write(byteArrayOf(67,76,68,82,0,1)); out.writeByte(if (target == null) 4 else 5)
            field(id.source.value); field(id.raw); out.writeLong(revision.value); if (target != null) field(target.value); field(label)
        }
        return sha(bytes.toByteArray())
    }

    /** Independent CLNV encoder lets valid-hash FK substitutions reach association checks. */
    private fun rehash(db: HostDeckVersionDatabase, id: EntityId) {
        val bytes = ByteArrayOutputStream()
        db.host.connection.prepare("SELECT version_id,provider,deck_id,label,created_seconds,created_nano,source_revision,predecessor_version_id,kind,restored_target_version_id,creating_sequence FROM deck_named_version WHERE version_id=${q(id.value)}").use { row ->
            check(row.step()); DataOutputStream(bytes).use { out ->
                fun field(index: Int) { val value = row.getText(index).toByteArray(Charsets.UTF_8); out.writeInt(value.size); out.write(value) }
                fun optional(index: Int) { if (row.getColumnType(index) == 5) out.writeByte(0) else { out.writeByte(1); field(index) } }
                out.write(byteArrayOf(67,76,78,86,0,1)); (0..3).forEach(::field)
                out.writeLong(row.getLong(4)); out.writeInt(row.getLong(5).toInt()); out.writeLong(row.getLong(6)); optional(7)
                out.writeByte(if (row.getText(8) == "USER") 0 else 1); optional(9); out.writeLong(row.getLong(10))
            }
        }
        db.sql("UPDATE deck_named_version SET metadata_sha256='${sha(bytes.toByteArray())}' WHERE version_id=${q(id.value)}")
    }
}
