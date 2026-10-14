@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class RulingsControllerTest {
    private fun TestScope.controller(f: RulingsFixture) = RulingsController(ownerScope(), f, StandardTestDispatcher(testScheduler))
    @Test fun absentCorpusAndInstalledEmptyAndCorruptDataAreDifferentStates() = runTest {
        val f = RulingsFixture(); val c = controller(f); c.select(RULINGS_UI_ORACLE); advanceUntilIdle()
        assertNull(c.state.value.status!!.active); assertNull(c.state.value.problem); assertTrue(f.reads.isEmpty())
        f.installed(); c.select(RULINGS_UI_OTHER); advanceUntilIdle()
        assertTrue(c.state.value.page!!.items.isEmpty()); assertNotNull(c.state.value.status!!.active)
        f.pageFailure = failure(FailureCode.CORRUPT_DATA); c.refresh(); advanceUntilIdle()
        assertEquals(Problem.CORRUPT_DATA, c.state.value.problem); assertNull(c.state.value.page)
        assertEquals(0, f.metadataAdmissions); assertEquals(0, f.installs)
    }
    @Test fun tenRowPagesReplacePriorTextAndGenerationMismatchRequiresExplicitReload() = runTest {
        val f = RulingsFixture(); f.contents = List(23) { f.contents.single().copy(comment = "Synthetic $it") }; f.installed()
        val c = controller(f); c.select(RULINGS_UI_ORACLE); advanceUntilIdle()
        assertEquals(10, c.state.value.page!!.items.size); assertEquals(10, f.reads.single().second.limit)
        c.next(); advanceUntilIdle(); assertEquals("Synthetic 10", c.state.value.page!!.items.first().content.comment)
        assertEquals(10, c.state.value.page!!.items.size)
        f.responseGeneration = GenerationId("other-generation"); c.next(); advanceUntilIdle()
        assertEquals(Problem.STALE_GENERATION, c.state.value.problem); assertNull(c.state.value.page)
        f.responseGeneration = null; c.refresh(); advanceUntilIdle(); assertEquals("Synthetic 0", c.state.value.page!!.items.first().content.comment)
    }
    @Test fun lateCancelledOracleReplyCannotReplaceAnotherSelectionOrRepopulateClosedReader() = runTest {
        val f = RulingsFixture(); f.contents = f.contents + f.contents.single().copy(oracleId = RULINGS_UI_OTHER, comment = "Other Oracle")
        f.installed(); f.ignoreCancellation = true; val held = CompletableDeferred<Unit>(); f.gates[RULINGS_UI_ORACLE] = held
        val c = controller(f); c.select(RULINGS_UI_ORACLE); advanceUntilIdle(); c.select(RULINGS_UI_OTHER); advanceUntilIdle()
        assertEquals("Other Oracle", c.state.value.page!!.items.single().content.comment)
        held.complete(Unit); advanceUntilIdle(); assertEquals(RULINGS_UI_OTHER, c.state.value.oracle)
        assertEquals("Other Oracle", c.state.value.page!!.items.single().content.comment)
        val late = CompletableDeferred<Unit>(); f.gates[RULINGS_UI_OTHER] = late
        c.refresh(); advanceUntilIdle(); assertTrue(c.state.value.loading)
        c.close(); late.complete(Unit); advanceUntilIdle(); assertNull(c.state.value.oracle); assertNull(c.state.value.page)
    }
}
