@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.syntheticCard
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class AdvancedQueryControllerTest {
    private class Ports : CatalogSearchPreparation, LocalCardQuery {
        val generation = GenerationId("synthetic-typed-query")
        val reference = SnapshotReference(ProviderId("scryfall"), generation, null, LanguageTag.ENGLISH)
        val card = syntheticCard("Synthetic typed result", generation)
        var statuses = 0; var advances = 0
        var ready = false
        var gate: CompletableDeferred<Unit>? = null
        var answer: Outcome<Page<CardRecord>> = Outcome.Success(Page(SnapshotList.of(card), null, reference))
        val requests = mutableListOf<Pair<SearchContext, PageRequest>>()
        fun preparation(ready: Boolean) = CatalogSearchPreparationState(generation, 1,
            if (ready) CatalogSearchPreparationReadiness.READY else CatalogSearchPreparationReadiness.NOT_PREPARED,
            if (ready) CatalogSearchPreparationPhase.VERIFY_TEXT_MIRRORS else null, if (ready) CatalogSearchPreparationCounts(cards = 1, printings = 1) else CatalogSearchPreparationCounts(), null)
        override suspend fun status(generation: GenerationId, cancel: CancelToken): Outcome<CatalogSearchPreparationState> {
            statuses++; return Outcome.Success(preparation(ready))
        }
        override suspend fun advance(generation: GenerationId, cancel: CancelToken): Outcome<CatalogSearchPreparationState> {
            advances++
            if (advances >= 2) { ready = true; return Outcome.Success(preparation(true)) }
            return Outcome.Success(CatalogSearchPreparationState(generation, 1, CatalogSearchPreparationReadiness.PREPARING,
                CatalogSearchPreparationPhase.CARDS, CatalogSearchPreparationCounts(cards = 1), null))
        }
        override suspend fun local(query: QueryAst, context: SearchContext, page: PageRequest, cancel: CancelToken): Outcome<Page<CardRecord>> {
            requests += context to page
            val captured = answer
            gate?.let { withContext(NonCancellable) { it.await() } }
            return captured
        }
        fun controller(scope: TestScope) = AdvancedQueryController(scope, this, this, { raw ->
            if (raw == "(") CardQueryParseResult.Invalid(SnapshotList.of(QueryDiagnostic(0, 1, "Synthetic syntax failure")))
            else CardQueryParseResult.Valid(QueryAst.Text(SearchTextField.NAME, raw, false))
        }, StandardTestDispatcher(scope.testScheduler))
    }
    @Test fun openingReadsStatusOnlyAndExplicitPreparationContinuesToRealTerminalState() = runTest {
        val f = Ports(); val c = f.controller(this)
        c.open(f.reference, "synthetic-v1"); runCurrent()
        assertEquals(1, f.statuses); assertEquals(0, f.advances); assertTrue(f.requests.isEmpty())
        c.submit(); runCurrent(); assertTrue(f.requests.isEmpty())
        c.prepare(); advanceUntilIdle()
        assertEquals(2, f.advances); assertEquals(CatalogSearchPreparationReadiness.READY, c.state.value.preparation?.readiness)
        assertFalse(c.state.value.busy)
        c.hide(); c.resume(); runCurrent()
        assertEquals(1, f.statuses); assertEquals(2, f.advances)
    }
    @Test fun invalidAndUnsupportedQueriesDoNotProduceMisleadingSuccessfulEmptyPages() = runTest {
        val f = Ports().apply { ready = true }; val c = f.controller(this)
        c.open(f.reference, null); runCurrent(); c.input("("); c.submit(); runCurrent()
        assertEquals(1, c.state.value.diagnostics.size); assertTrue(f.requests.isEmpty()); assertNull(c.state.value.page)
        c.input("owned>0"); f.answer = Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Synthetic unavailable context")
        c.submit(); runCurrent()
        assertEquals(Problem.UNSUPPORTED, c.state.value.problem); assertNull(c.state.value.page)
        c.input("x".repeat(4097)); assertEquals(Problem.INVALID_INPUT, c.state.value.problem)
        assertEquals("owned>0", c.state.value.input)
    }
    @Test fun editsPolicyHideAndSnapshotChangesFenceNoncooperativeOldReadCompletions() = runTest {
        for (change in 0..3) {
            val f = Ports().apply { ready = true; gate = CompletableDeferred() }; val c = f.controller(this)
            c.open(f.reference, null); runCurrent(); c.input("old"); c.submit(); runCurrent()
            assertEquals(1, f.requests.size)
            when (change) {
                0 -> c.input("new")
                1 -> c.policy(FaceMatchPolicy.SAME_FACE)
                2 -> c.hide()
                else -> c.open(f.reference.copy(generation = GenerationId("different")), "new-source")
            }
            f.gate!!.complete(Unit); advanceUntilIdle()
            assertNull(c.state.value.page); assertNull(c.state.value.selected); assertFalse(c.state.value.busy)
        }
    }
    @Test fun responseIdentityDuplicateAndCursorFencesRejectMalformedPagesThenAllowActualCurrentSelection() = runTest {
        val f = Ports().apply { ready = true }; val c = f.controller(this)
        c.open(f.reference, null); runCurrent(); c.input("result")
        val malformed = listOf(
            Page(SnapshotList.of(f.card), null, f.reference.copy(generation = GenerationId("foreign"))),
            Page(SnapshotList.of(f.card, f.card), null, f.reference),
            Page(SnapshotList.of(f.card.copy(language = LanguageTag("ja"))), null, f.reference),
            Page(SnapshotList.of(f.card.copy(provenance = f.card.provenance.copy(provider = ProviderId("foreign")))), null, f.reference))
        for (page in malformed) {
            f.answer = Outcome.Success(page); c.submit(); runCurrent()
            assertEquals(Problem.SOURCE_MISMATCH, c.state.value.problem); assertNull(c.state.value.page)
        }
        f.answer = Outcome.Success(Page(SnapshotList.of(f.card), "opaque-1", f.reference)); c.submit(); runCurrent()
        c.select(f.card); assertEquals(f.card, c.state.value.selected)
        c.next(); runCurrent(); assertEquals("opaque-1", f.requests.last().second.cursor)
        assertEquals(Problem.SOURCE_MISMATCH, c.state.value.problem) // Same next cursor cannot progress.
        assertNull(c.state.value.page); assertNull(c.state.value.selected)
    }
}
