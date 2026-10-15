package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoardConstraintControllerTest {
    private fun deck() = DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE,"board-check"), "Synthetic counts", Knowledge.Absent("none"), "", Knowledge.Absent("none"), SnapshotList.empty(), UtcTimestamp(1), UtcTimestamp(1), Revision(0),
        SnapshotList.copyOf(BoardRole.entries.mapIndexed { i, role -> DeckEntry(EntityId("entry-$i"), CardIdentity(Knowledge.Absent("oracle"), Knowledge.Absent("printing"), null, Knowledge.Known("Synthetic card $i"), LanguageTag.ENGLISH), i+1, role, Knowledge.Absent("none"), "", SnapshotList.empty(), SnapshotList.empty()) }), Knowledge.Absent("none"), null, SnapshotList.empty(), SnapshotList.empty())
    @Test fun exactUserLimitsRetainAcrossRecreationEquivalentResumeWithoutMutation() = runTest {
        val d = deck()
        val c = BoardConstraintController(this, StandardTestDispatcher(testScheduler)) { _, _ -> Outcome.Success(Unit) }
        c.open(d); c.edit(BoardRole.MAIN, true, "1"); c.edit(BoardRole.MAIN, false, "1")
        c.edit(BoardRole.SIDEBOARD, false, "1"); c.assess(); runCurrent()
        val result = checkNotNull(c.state.value.result)
        assertEquals(d.id, result.deckId); assertEquals(d.revision, result.deckRevision)
        assertEquals(listOf(true, false), result.findings.map { it.satisfied })
        assertEquals(listOf(1L,2L), result.findings.map { it.quantity })
        c.hide(); c.open(d); assertSame(result, c.state.value.result)
        assertEquals("1", c.state.value.limits.getValue(BoardRole.MAIN).minimum)
        assertEquals(d, c.state.value.deck)
    }
    @Test fun blankInvalidOverflowAndGraphFailuresNeverBecomePasses() = runTest {
        var valid = true
        val c = BoardConstraintController(this, StandardTestDispatcher(testScheduler)) { _, _ -> if (valid) Outcome.Success(Unit) else Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT,"Synthetic rejected graph",SnapshotList.empty())) }
        c.open(deck()); c.assess(); assertTrue(c.state.value.invalid); assertNull(c.state.value.result)
        for (input in listOf("-1", "2.5", "2147483648", " ")) {
            c.edit(BoardRole.MAIN, true, input); c.assess(); runCurrent(); assertTrue(input, c.state.value.invalid); assertNull(c.state.value.result)
        }
        c.edit(BoardRole.MAIN, true, "2"); c.edit(BoardRole.MAIN, false, "1"); c.assess(); assertTrue(c.state.value.invalid)
        c.edit(BoardRole.MAIN, true, "0"); c.assess(); runCurrent(); assertFalse(c.state.value.invalid); assertTrue(checkNotNull(c.state.value.result).findings.single().satisfied)
        valid = false; c.assess(); runCurrent(); assertTrue(c.state.value.invalid); assertNull(c.state.value.result)
    }
    @Test fun editsHideAndChangedSavedRevisionFenceQueuedResults() = runTest {
        val c = BoardConstraintController(this, StandardTestDispatcher(testScheduler)) { _, _ -> Outcome.Success(Unit) }
        val d = deck(); c.open(d); c.edit(BoardRole.MAIN, true,"1"); c.assess(); c.edit(BoardRole.MAIN, true,"2"); runCurrent(); assertNull(c.state.value.result)
        c.assess(); c.hide(); runCurrent(); assertFalse(c.state.value.visible); assertFalse(c.state.value.busy); assertNull(c.state.value.result)
        c.open(d); c.assess(); c.observe(d.copy(revision=Revision(1))); runCurrent(); assertFalse(c.state.value.visible); assertNull(c.state.value.deck); assertNull(c.state.value.result)
        c.open(d.copy(revision=Revision(1))); assertTrue(c.state.value.limits.values.all { it.minimum.isEmpty() && it.maximum.isEmpty() })
    }
}
