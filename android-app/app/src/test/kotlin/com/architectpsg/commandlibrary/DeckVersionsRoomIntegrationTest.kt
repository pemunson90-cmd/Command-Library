package com.architectpsg.commandlibrary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.data.storage.decks.codec.DeckPayloadCodec
import com.architectpsg.commandlibrary.di.AppGraph
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckVersions
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration

/** Actual app adapters and independent host Room connection; synthetic identity, not device/source proof. */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class DeckVersionsRoomIntegrationTest {
    private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private fun <T> ok(result: Outcome<T>): T { assertTrue(result.toString(), result is Outcome.Success); return (result as Outcome.Success).value }
    private fun op(prefix: String, suffix: String) = EntityId("$prefix-$suffix")
    private fun deck(suffix: String): DeckSnapshot {
        val identity = CardIdentity(Knowledge.Unknown("Oracle not acquired", SnapshotList.empty()),
            Knowledge.Known(CardId(ProviderId("synthetic-host"), "printing-$suffix")),
            ProviderIdentity(ProviderId("foreign-source"), "raw", "retained-$suffix"), Knowledge.Known("Synthetic host fixture"), LanguageTag.ENGLISH)
        val entry = DeckEntry(op("entry", suffix), identity, 3, BoardRole.MAIN, Knowledge.Known(Finish.NONFOIL),
            "Original notes", SnapshotList.empty(), SnapshotList.empty())
        return DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE, "versions-$suffix"), "Original deck", Knowledge.Absent("none"), "Description",
            Knowledge.Absent("none"), SnapshotList.of("tag"), UtcTimestamp(1), UtcTimestamp(1), Revision(0), SnapshotList.of(entry),
            Knowledge.Unknown("No budget", SnapshotList.empty()), null, SnapshotList.empty(), SnapshotList.empty(), "Original primer")
    }
    private fun content(expected: DeckSnapshot, actual: DeckSnapshot) =
        assertEquals(expected.copy(revision = actual.revision, modifiedAt = actual.modifiedAt), actual)

    @Test fun actualGraphCaptureDiffRestoreUndoRecoveryAndIndependentReplayPreserveCompleteSnapshots() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val graph = (context as CommandLibraryApplication).graph
        val ports = ok(graph.ports()); val library = checkNotNull(ports.localDecks)
        val edits = checkNotNull(ports.localDeckEdits); val versions = checkNotNull(ports.localDeckVersions)
        assertSame(versions, ok(graph.ports()).localDeckVersions)
        val suffix = UUID.randomUUID().toString(); val original = deck(suffix)
        ok(library.create(original, op("create", suffix), token))
        val first = ok(versions.capture(original.id, Revision(0), "Initial Ω", op("capture-initial", suffix), token))
        assertEquals(original, first.manifest); assertEquals(Revision(0), ok(library.load(original.id, token)).revision)
        val entry = original.entries.single()
        val changedEntry = entry.copy(board = BoardRole.SIDEBOARD, finish = Knowledge.Known(Finish.FOIL), notes = "Changed notes",
            identity = entry.identity.copy(printingId = Knowledge.Known(CardId(ProviderId("synthetic-host"), "other-printing-$suffix"))))
        val changed = ok(edits.apply(original.id, DeckPatch(SnapshotList.of(DeckEdit.Rename("Changed deck"), DeckEdit.Primer("Changed primer"),
            DeckEdit.Replace(changedEntry)), "Version integration"), Revision(0), op("edit", suffix), token))
        val second = ok(versions.capture(original.id, changed.revision, "Revised", op("capture-revised", suffix), token))
        assertEquals(first, ok(versions.load(original.id, first.id, token)))
        val preview = ok(versions.previewRestore(original.id, first.id, changed.revision, token))
        assertEquals(changed, preview.current); assertEquals(first, preview.target)
        assertEquals(changed, preview.difference.before); assertEquals(original, preview.difference.after)
        assertTrue(preview.difference.changedFields.contains(DeckChangedField.NAME))
        assertTrue(preview.difference.changedFields.contains(DeckChangedField.PRIMER))
        assertEquals(changedEntry, preview.difference.entries.single().before)
        assertEquals(entry, preview.difference.entries.single().after)
        val restoreOperation = op("restore", suffix)
        val restored = ok(versions.restore(original.id, first.id, changed.revision, "Before restore", restoreOperation, token))
        assertEquals(Revision(2), restored.snapshot.revision); content(original, restored.snapshot)
        assertEquals(changed, restored.recoveryVersion.manifest)
        assertEquals(changed.revision, restored.recoveryVersion.sourceRevision)
        assertEquals(second.id, restored.recoveryVersion.predecessorVersionId)
        val recoverySummary = ok(versions.list(original.id, PageRequest(100), token)).items.first()
        assertEquals(restored.recoveryVersion.id, recoverySummary.id)
        assertEquals(DeckVersionKind.RECOVERY, recoverySummary.kind); assertEquals(first.id, recoverySummary.restoredTargetVersionId)
        val undone = ok(edits.undo(original.id, restored.snapshot.revision, op("undo-restore", suffix), token))
        assertEquals(Revision(3), undone.revision); content(changed, undone)
        assertEquals(restored.recoveryVersion, ok(versions.load(original.id, restored.recoveryVersion.id, token)))
        ok(StorageDatabase.open(context, AppGraph.DATABASE_FILE)).use { db ->
            val reader = SqlLocalDeckVersions(db, DeckPayloadCodec(), object : Clock {
                override fun utcNow(): UtcTimestamp = error("Historical replay/read used clock")
                override fun monotonicNow(): Duration = error("Versions do not use monotonic clock")
            }, object : IdSource { override fun nextId(): String = error("Historical replay/read generated ID") })
            assertEquals(first, ok(reader.load(original.id, first.id, token)))
            assertEquals(restored, ok(reader.restore(original.id, first.id, changed.revision, "Before restore", restoreOperation, token)))
        }
        assertEquals(undone, ok(library.load(original.id, token)))
        assertEquals(first.manifest, ok(versions.load(original.id, first.id, token)).manifest)
        assertEquals(second.manifest, ok(versions.load(original.id, second.id, token)).manifest)
        assertTrue(ok(ports.settings.read(token)).consents.isEmpty())
    }

    @Test fun actualGraphPackageRecoveryCanBeRestoredUndoneAndDuplicatedWithoutDanglingContributions() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val ports = ok((context as CommandLibraryApplication).graph.ports())
        val library = checkNotNull(ports.localDecks); val versions = checkNotNull(ports.localDeckVersions); val edits = checkNotNull(ports.localDeckEdits)
        val suffix = UUID.randomUUID().toString(); val initial = deck(suffix)
        val instance = PackageInstance(op("instance", suffix), op("package", suffix), op("package-version", suffix), PackageLinkPolicy.LINKED,
            SnapshotList.of(PackageContribution(op("package-entry", suffix), initial.entries.single().id, 2)))
        val original = initial.copy(packageInstances = SnapshotList.of(instance))
        ok(library.create(original, op("create", suffix), token))
        val named = ok(versions.capture(original.id, Revision(0), "Package checkpoint", op("capture", suffix), token))
        val first = ok(versions.restore(original.id, named.id, Revision(0), "Before no-op restore", op("restore", suffix), token))
        assertEquals(Revision(1), first.snapshot.revision); content(original, first.snapshot)
        val undone = ok(edits.undo(original.id, first.snapshot.revision, op("undo", suffix), token))
        assertEquals(Revision(2), undone.revision); content(original, undone)
        val recover = ok(versions.restore(original.id, first.recoveryVersion.id, undone.revision, "Before restoring recovery", op("restore-recovery", suffix), token))
        assertEquals(Revision(3), recover.snapshot.revision); content(original, recover.snapshot)
        val summaries = ok(versions.list(original.id, PageRequest(100), token)).items
        assertEquals(first.recoveryVersion.id, summaries.first().restoredTargetVersionId)
        assertTrue(summaries.any { it.id == first.recoveryVersion.id && it.kind == DeckVersionKind.RECOVERY })
        assertEquals(first.recoveryVersion, ok(versions.load(original.id, first.recoveryVersion.id, token)))
        val copy = ok(library.duplicate(original.id, recover.snapshot.revision, "Package copy", op("duplicate", suffix), token))
        assertNotEquals(original.id, copy.id); assertNotEquals(original.entries.single().id, copy.entries.single().id)
        val copiedInstance = copy.packageInstances.single()
        assertNotEquals(instance.id, copiedInstance.id)
        assertEquals(instance.copy(id = copiedInstance.id, contributions = SnapshotList.of(instance.contributions.single().copy(deckEntryId = copy.entries.single().id))), copiedInstance)
        assertEquals(original.entries.single().identity, copy.entries.single().identity)
        assertEquals(original.packageInstances, ok(library.load(original.id, token)).packageInstances)
    }
}
