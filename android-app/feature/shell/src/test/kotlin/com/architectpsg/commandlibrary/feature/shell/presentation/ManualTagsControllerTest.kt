@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.ManualTagPlanning
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic acknowledgement/consent oracles; real SQLite and actual UI are separate gates. */
class ManualTagsControllerTest {
    private fun deck() = DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE, "deck"), "Deck",
        Knowledge.Absent("none"), "", Knowledge.Absent("none"), SnapshotList.empty(), UtcTimestamp(1), UtcTimestamp(1),
        Revision(0), SnapshotList.empty(), Knowledge.Absent("none"), null, SnapshotList.empty(), SnapshotList.empty())
    private class Pool : LocalPersonalTags {
        var value = PersonalTagPool(Revision(0), SnapshotList.empty(), null)
        var writes = 0
        var loseAck = false
        var stale = false
        override suspend fun load(cancel: CancelToken): Outcome<PersonalTagPool> = Outcome.Success(value)
        override suspend fun replace(expected: Revision, tags: SnapshotList<PersonalTag>, cancel: CancelToken): Outcome<PersonalTagPool> {
            writes++
            if (stale || expected != value.revision) return Outcome.Failure(DomainFailure(FailureCode.STALE_REVISION, "Synthetic stale", SnapshotList.empty()))
            value = PersonalTagPool(Revision(value.revision.value + 1), tags, UtcTimestamp(2))
            return if (loseAck) Outcome.Unknown("Synthetic lost acknowledgement", SnapshotList.empty()) else Outcome.Success(value)
        }
    }
    private val planning = object : ManualTagPlanning {
        override fun assign(deck: DeckSnapshot, pool: PersonalTagPool, entryIds: SnapshotList<EntityId>, tagIds: SnapshotList<EntityId>) = error("Not used")
        override fun remove(deck: DeckSnapshot, entryIds: SnapshotList<EntityId>, tagIds: SnapshotList<EntityId>) = error("Not used")
        override fun categories(deck: DeckSnapshot, categories: SnapshotList<EntryLabel>) = error("Not used")
    }
    @Test fun lostAcknowledgementRetainsDirtyInputAndReloadReconcilesWithoutReplay() = runTest {
        val pool = Pool().also { it.loseAck = true }; val d = deck()
        val c = ManualTagsController(backgroundScope, pool, planning, StandardTestDispatcher(testScheduler), { it == d }, { _, _ -> error("No deck mutation") }, { "tag" })
        c.open(d); runCurrent(); c.name("Removal"); c.putName()
        assertEquals(0, pool.writes); c.save(); runCurrent()
        assertTrue(c.state.value.uncertain); assertTrue(c.state.value.dirty)
        assertEquals("Removal", c.state.value.draft.single().name)
        c.save(); c.useSavedPool(); assertEquals(1, pool.writes); assertTrue(c.state.value.uncertain)
        c.reload(); runCurrent(); assertFalse(c.state.value.uncertain); assertFalse(c.state.value.conflict)
        assertFalse(c.state.value.dirty); assertEquals(Revision(1), c.state.value.pool!!.revision)
        assertEquals(1, pool.writes)
    }
    @Test fun staleSaveReloadDoesNotSilentlyOverwriteOthersOrDiscardInput() = runTest {
        val pool = Pool(); val d = deck()
        val c = ManualTagsController(backgroundScope, pool, planning, StandardTestDispatcher(testScheduler), { true }, { _, _ -> false }, { "mine" })
        c.open(d); runCurrent(); c.name("My tag"); c.putName()
        val theirs = PersonalTag(EntityId("theirs"), "Other tag", false)
        pool.value = PersonalTagPool(Revision(1), SnapshotList.of(theirs), UtcTimestamp(2))
        c.save(); runCurrent(); assertTrue(c.state.value.conflict)
        c.reload(); runCurrent(); assertTrue(c.state.value.conflict)
        assertEquals("My tag", c.state.value.draft.single().name)
        assertEquals("Other tag", c.state.value.pool!!.tags.single().name)
        c.save(); assertEquals(1, pool.writes)
        c.useSavedPool(); assertFalse(c.state.value.conflict); assertFalse(c.state.value.dirty)
        assertEquals(theirs, c.state.value.draft.single())
    }
    @Test fun disposedRenderedCallbacksCannotEditNewDialogAndCloseRequiresExplicitDiscard() = runTest {
        val pool = Pool(); val d = deck()
        val c = ManualTagsController(backgroundScope, pool, planning, StandardTestDispatcher(testScheduler), { true }, { _, _ -> false }, { "mine" })
        c.open(d); runCurrent(); val old = c.state.value
        c.name("Unsaved"); c.close(); assertTrue(c.state.value.closeRequested)
        c.keepEditing(); assertEquals("Unsaved", c.state.value.name)
        c.discardAndClose(); assertNull(c.state.value.deck)
        c.open(d); runCurrent()
        c.rendered(old) { c.name("Stale callback"); c.putName(); c.save() }
        assertEquals("", c.state.value.name); assertTrue(c.state.value.draft.isEmpty()); assertEquals(0, pool.writes)
        c.rendered(c.state.value) { c.name("Current") }; assertEquals("Current", c.state.value.name)
    }
}
