@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.LanguageTag
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.Page
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.core.model.SnapshotReference
import com.architectpsg.commandlibrary.core.model.UnavailableReason
import com.architectpsg.commandlibrary.feature.shell.SYNTHETIC_PROVIDER
import com.architectpsg.commandlibrary.feature.shell.SyntheticCatalog
import com.architectpsg.commandlibrary.feature.shell.failure
import com.architectpsg.commandlibrary.feature.shell.syntheticCard
import com.architectpsg.commandlibrary.feature.shell.OracleTextFixture
import com.architectpsg.commandlibrary.core.model.CatalogTextField
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchControllerTest {
    @Test fun optionalTextCapabilityRoutesSelectedFieldAndFieldSwitchCancelsOldWork() = runTest {
        val text = OracleTextFixture(catalog)
        val controller = SearchController(ownerScope(), catalog, StandardTestDispatcher(testScheduler), textLookup = text) {}
        controller.onQueryChange("draw")
        catalog.lookupGate = CompletableDeferred()
        controller.submit(); runCurrent()
        controller.onFieldChange(CatalogTextField.ORACLE_TEXT)
        assertTrue(catalog.lookupTokens.single().isCancelled)
        assertEquals("draw", controller.queryInput); assertFalse(controller.state.value.searched)
        controller.submit(); advanceUntilIdle()
        assertEquals(listOf(CatalogTextField.ORACLE_TEXT to "draw"), text.requests)
        assertEquals(CatalogTextField.ORACLE_TEXT, controller.state.value.queryField)
        assertEquals("Synthetic text-only match", controller.state.value.results.single().name)
        catalog.lookupGate!!.complete(Unit); advanceUntilIdle()
        assertEquals("Synthetic text-only match", controller.state.value.results.single().name)
        controller.onFieldChange(CatalogTextField.NAME)
        assertTrue(controller.state.value.results.isEmpty()); assertEquals("draw", controller.queryInput)
    }
    @Test fun absentTextCapabilityCannotSelectUnsupportedField() = runTest {
        val controller = controller()
        assertFalse(controller.supportsOracleText)
        controller.onFieldChange(CatalogTextField.ORACLE_TEXT)
        assertEquals(CatalogTextField.NAME, controller.fieldInput)
        assertTrue(catalog.lookupRequests.isEmpty())
    }
    @Test fun typingCancelsSubmittedWorkWithoutSubmittingTheDraft() = runTest {
        val controller = controller()
        controller.onActiveGeneration(catalog.active!!.id)
        catalog.lookupGate = CompletableDeferred()
        controller.search("synthetic")
        runCurrent()
        controller.onQueryChange("unrelated")
        assertTrue(catalog.lookupTokens.single().isCancelled)
        assertEquals("unrelated", controller.queryInput)
        assertFalse(controller.state.value.searched)
        assertNull(controller.state.value.loading)
        assertEquals(catalog.active!!.id, controller.state.value.activeGeneration)
        catalog.lookupGate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(controller.state.value.results.isEmpty())
        assertEquals(1, catalog.lookupRequests.size)
        controller.submit()
        advanceUntilIdle()
        assertEquals(listOf("Unrelated Name"), controller.state.value.results.map { it.name })
    }

    @Test fun editingCompletedResultsClearsSelectedDetailAndCursorButPreservesExactDraft() = runTest {
        val controller = controller()
        controller.search("synthetic")
        advanceUntilIdle()
        controller.select(controller.state.value.results.first())
        assertTrue(controller.state.value.canLoadMore)
        controller.onQueryChange("  next draft  ")
        assertEquals("  next draft  ", controller.queryInput)
        assertNull(controller.state.value.selected)
        assertTrue(controller.state.value.results.isEmpty())
        assertFalse(controller.state.value.canLoadMore)
        assertFalse(controller.state.value.searched)
        advanceUntilIdle()
        assertEquals(1, catalog.lookupRequests.size)
    }
    private val names = (1..120).map { "Synthetic Card %03d".format(it) } + "Unrelated Name"
    private val catalog = SyntheticCatalog(names)
    private val staleSignals = AtomicInteger(0)

    private fun TestScope.controller() = SearchController(ownerScope(), catalog, StandardTestDispatcher(testScheduler)) { staleSignals.incrementAndGet() }

    @Test
    fun emptyAndOverlongQueriesNeverCallTheCatalog() = runTest {
        val controller = controller()
        controller.search("   ")
        assertEquals(QueryProblem.EMPTY, controller.state.value.queryProblem)

        val allowed = "😀".repeat(SearchController.MAX_QUERY_CODE_POINTS)
        controller.search(allowed + "😀")
        assertEquals(QueryProblem.TOO_LONG, controller.state.value.queryProblem)
        advanceUntilIdle()
        assertTrue(catalog.lookupRequests.isEmpty())

        controller.onQueryChange("Synthetic")
        assertNull("editing clears the input problem", controller.state.value.queryProblem)
        controller.search(allowed)
        advanceUntilIdle()
        assertEquals("128 code points are accepted", 1, catalog.lookupRequests.size)
    }

    @Test
    fun firstPageThenCursorPagesUntilTheEnd() = runTest {
        val controller = controller()
        controller.search("  synthetic card ")
        assertEquals(SearchLoad.FIRST_PAGE, controller.state.value.loading)
        advanceUntilIdle()

        val first = controller.state.value
        assertEquals("synthetic card", first.query)
        assertEquals(SearchController.PAGE_SIZE, first.results.size)
        assertEquals("synthetic card" to null, catalog.lookupRequests[0].first to catalog.lookupRequests[0].second.cursor)
        assertEquals(SearchController.PAGE_SIZE, catalog.lookupRequests[0].second.limit)
        assertEquals(catalog.active!!.id, first.generation)
        assertTrue(first.canLoadMore)

        controller.loadMore()
        advanceUntilIdle()
        assertEquals(first.nextCursor, catalog.lookupRequests[1].second.cursor)
        assertEquals(100, controller.state.value.results.size)

        controller.loadMore()
        advanceUntilIdle()
        val last = controller.state.value
        assertEquals(120, last.results.size)
        assertNull(last.nextCursor)
        assertFalse(last.canLoadMore)
        assertEquals(names.take(120), last.results.map { it.name })

        controller.loadMore()
        advanceUntilIdle()
        assertEquals("no request past the end", 3, catalog.lookupRequests.size)
    }

    @Test
    fun staleCursorKeepsOneSnapshotAndOffersSearchAgain() = runTest {
        val controller = controller()
        controller.search("synthetic")
        advanceUntilIdle()
        val firstGeneration = controller.state.value.generation

        catalog.active = catalog.newGeneration(names, bytes = 0)
        controller.loadMore()
        advanceUntilIdle()

        val stale = controller.state.value
        assertTrue(stale.stale)
        assertTrue(stale.cursorStale)
        assertFalse(stale.canLoadMore)
        assertEquals("earlier page kept, nothing mixed in", SearchController.PAGE_SIZE, stale.results.size)
        assertEquals(firstGeneration, stale.generation)
        assertNull(stale.problem)
        assertEquals("status refresh requested", 1, staleSignals.get())

        controller.loadMore()
        advanceUntilIdle()
        assertEquals("stale results refuse more pages", 2, catalog.lookupRequests.size)

        controller.searchAgain()
        advanceUntilIdle()
        val fresh = controller.state.value
        assertFalse(fresh.stale)
        assertEquals(catalog.active!!.id, fresh.generation)
        assertNull(catalog.lookupRequests.last().second.cursor)
    }

    @Test
    fun nextPageFromADifferentSnapshotIsNeverAppended() = runTest {
        val controller = controller()
        controller.search("synthetic")
        advanceUntilIdle()
        catalog.lookupOverride = Outcome.Success(
            Page(
                SnapshotList.of(syntheticCard("Synthetic Other Snapshot", null)),
                null,
                SnapshotReference(SYNTHETIC_PROVIDER, com.architectpsg.commandlibrary.core.model.GenerationId("synthetic-elsewhere"), null, LanguageTag.ENGLISH),
            ),
        )
        controller.loadMore()
        advanceUntilIdle()
        assertTrue(controller.state.value.stale)
        assertEquals(SearchController.PAGE_SIZE, controller.state.value.results.size)
    }

    @Test
    fun activeGenerationChangeMarksResultsStale() = runTest {
        val controller = controller()
        controller.onActiveGeneration(catalog.active!!.id)
        controller.search("synthetic")
        advanceUntilIdle()
        assertFalse(controller.state.value.stale)

        controller.onActiveGeneration(com.architectpsg.commandlibrary.core.model.GenerationId("synthetic-new"))
        assertTrue(controller.state.value.stale)
        assertFalse(controller.state.value.canLoadMore)
    }

    @Test
    fun failuresAreShownAndRetryRepeatsTheFailedRequest() = runTest {
        val controller = controller()
        catalog.lookupOverride = failure(FailureCode.CORRUPT_DATA)
        controller.search("synthetic")
        advanceUntilIdle()
        assertEquals(Problem.CORRUPT_DATA, controller.state.value.problem)
        assertEquals(SearchLoad.FIRST_PAGE, controller.state.value.failedLoad)

        catalog.lookupOverride = null
        controller.retry()
        advanceUntilIdle()
        assertNull(controller.state.value.problem)
        assertEquals(SearchController.PAGE_SIZE, controller.state.value.results.size)

        catalog.lookupOverride = Outcome.Unavailable(UnavailableReason.NOT_INSTALLED, "synthetic")
        controller.loadMore()
        advanceUntilIdle()
        assertEquals(Problem.NOT_INSTALLED, controller.state.value.problem)
        assertEquals(SearchLoad.NEXT_PAGE, controller.state.value.failedLoad)
        assertEquals("first page stays visible", SearchController.PAGE_SIZE, controller.state.value.results.size)

        catalog.lookupOverride = null
        controller.retry()
        advanceUntilIdle()
        assertEquals(100, controller.state.value.results.size)
    }

    @Test
    fun newSearchCancelsTheOneInFlight() = runTest {
        val controller = controller()
        catalog.lookupGate = CompletableDeferred()
        controller.search("synthetic card 00")
        runCurrent()
        controller.search("unrelated")
        runCurrent()
        assertTrue("first lookup's token is cancelled", catalog.lookupTokens[0].isCancelled)
        catalog.lookupGate!!.complete(Unit)
        advanceUntilIdle()
        val state = controller.state.value
        assertEquals("unrelated", state.query)
        assertEquals(listOf("Unrelated Name"), state.results.map { it.name })
    }

    @Test
    fun emptyCatalogReturnsAnHonestEmptyResult() = runTest {
        val empty = SyntheticCatalog()
        val controller = SearchController(ownerScope(), empty, StandardTestDispatcher(testScheduler)) {}
        controller.search("anything")
        advanceUntilIdle()
        val state = controller.state.value
        assertTrue(state.searched)
        assertTrue(state.results.isEmpty())
        assertNull(state.problem)
        assertNull(state.generation)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun selectionFollowsTheUserAndResetsOnNewSearch() = runTest {
        val controller = controller()
        controller.search("synthetic")
        advanceUntilIdle()
        val card = controller.state.value.results[3]
        controller.select(card)
        assertEquals(card, controller.state.value.selected)
        controller.clearSelection()
        assertNull(controller.state.value.selected)
        controller.select(card)
        controller.search("unrelated")
        assertNull(controller.state.value.selected)
    }

    @Test
    fun duplicateIdentitiesFromAnAdapterAreShownOnce() = runTest {
        val controller = controller()
        val card = syntheticCard("Synthetic Duplicate", catalog.active!!.id)
        catalog.lookupOverride = Outcome.Success(
            Page(SnapshotList.of(card, card), null, SnapshotReference(SYNTHETIC_PROVIDER, catalog.active!!.id, null, LanguageTag.ENGLISH)),
        )
        controller.search("synthetic")
        advanceUntilIdle()
        assertEquals(1, controller.state.value.results.size)
    }
}
