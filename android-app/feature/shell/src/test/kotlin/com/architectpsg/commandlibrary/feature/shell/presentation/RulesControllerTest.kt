@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.MemoryDocuments
import com.architectpsg.commandlibrary.feature.shell.ShellPorts
import com.architectpsg.commandlibrary.feature.shell.SyntheticCatalog
import com.architectpsg.commandlibrary.feature.shell.SyntheticSettings
import com.architectpsg.commandlibrary.feature.shell.document.DocumentRef
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

/** Synthetic ports exercise shell ownership and snapshot behavior, not parser/source authenticity. */
class RulesControllerTest {
    private val uri = "content://synthetic.documents/rules.txt"
    private val provider = ProviderId("local-rules")
    private val generation = GenerationId("synthetic-rules-a")
    private val unknown = Knowledge.Unknown("Synthetic source has no supplied metadata", SnapshotList.empty())
    private val hash = ContentHash("SHA-256", "a".repeat(64))
    private val source = LocalRulesImportSource(ResourceUri(uri), "rules.txt", provider, LanguageTag.ENGLISH,
        unknown, unknown, unknown, unknown, unknown, Knowledge.Absent("No expectation"), Knowledge.Absent("No expectation"))
    private fun provenance(corpus: GenerationId) = Provenance(provider, null, unknown, unknown,
        UtcTimestamp(1), unknown, corpus, hash, null)
    private fun entry(raw: String, corpus: GenerationId = generation, references: List<RuleCrossReference> = emptyList()) =
        RuleEntry(RuleId(provider, raw), corpus, RuleEntryKind.NUMBERED, Knowledge.Known(raw), Knowledge.Absent("No title"),
            "Synthetic rule $raw", 1, SnapshotList.copyOf(references), provenance(corpus))
    private fun corpus() = LocalRulesCorpus(generation, source, provenance(generation), GenerationStatus.ACTIVE, 2, 1)
    private fun page(entries: List<RuleEntry>, cursor: String? = null, corpus: GenerationId = generation) =
        Page(SnapshotList.copyOf(entries), cursor, SnapshotReference(provider, corpus, null, LanguageTag.ENGLISH))
    private fun rejected(code: FailureCode): Outcome<Nothing> = Outcome.Failure(DomainFailure(code, "Synthetic failure", SnapshotList.empty()))

    private inner class Ports : LocalRulesImport, LocalRulesLookup, KeywordRulesLookup {
        var keywordAction: suspend (String) -> Outcome<KeywordRuleLink> = { Outcome.Unavailable(UnavailableReason.SOURCE_UNAVAILABLE, "Synthetic lookup not requested") }
        override suspend fun lookupKeyword(keyword: String, corpusId: GenerationId?, cancel: CancelToken) = keywordAction(keyword)
        var importAction: suspend (LocalRulesImportSource, () -> Flow<ByteChunk>) -> Outcome<LocalRulesImportResult> = { _, _ ->
            Outcome.Unavailable(UnavailableReason.NOT_INSTALLED, "Synthetic import not requested")
        }
        var searchAction: suspend (RulesQuery) -> Outcome<Page<RuleEntry>> = { Outcome.Success(page(listOf(entry("100.1")), "next")) }
        var entryAction: suspend (RuleId, GenerationId) -> Outcome<RuleEntry> = { id, corpus -> Outcome.Success(entry(id.raw, corpus)) }
        val entryRequests = mutableListOf<Pair<RuleId, GenerationId>>()
        var statusValue = LocalRulesStatus(Knowledge.Known(corpus()), Knowledge.Absent("No attempt"))
        override suspend fun `import`(source: LocalRulesImportSource, reopen: () -> Flow<ByteChunk>, onProgress: (JobProgress) -> Unit, cancel: CancelToken) =
            importAction(source, reopen)
        override suspend fun status(cancel: CancelToken) = Outcome.Success(statusValue)
        override suspend fun search(query: RulesQuery, cancel: CancelToken) = searchAction(query)
        override suspend fun entry(id: RuleId, corpusId: GenerationId, cancel: CancelToken): Outcome<RuleEntry> {
            entryRequests += id to corpusId
            return entryAction(id, corpusId)
        }
    }

    private fun TestScope.controller(ports: Ports, documents: MemoryDocuments = MemoryDocuments(), exactKeywords: Boolean = false) =
        RulesController(ownerScope(), ports, ports, documents, StandardTestDispatcher(testScheduler), ports.takeIf { exactKeywords })

    @Test fun editingClearsOldResultsDetailAndCursorWithoutLosingDraftOrInstalledStatus() = runTest {
        val ports = Ports(); val c = controller(ports)
        c.refresh(); c.queryInput = "old"; c.submit(); advanceUntilIdle()
        c.select(c.state.value.results.single()); advanceUntilIdle()
        assertNotNull(c.state.value.selected); assertNotNull(c.state.value.nextCursor)
        val status = c.state.value.status
        c.queryInput = "  new exact draft  "
        assertEquals("  new exact draft  ", c.queryInput)
        assertEquals(status, c.state.value.status)
        assertTrue(c.state.value.results.isEmpty()); assertNull(c.state.value.query)
        assertNull(c.state.value.selected); assertNull(c.state.value.nextCursor)
        assertNull(c.state.value.generation); assertFalse(c.state.value.canLoadMore)
        assertFalse(c.state.value.searchLoading); assertNull(c.state.value.searchProblem)
        c.submit(); advanceUntilIdle()
        assertEquals("new exact draft", c.state.value.query)
        assertEquals("  new exact draft  ", c.queryInput)
    }

    @Test fun editingSupersedesPendingKeywordLookupWithoutSubmittingAnotherSearch() = runTest {
        val ports = Ports(); val gate = CompletableDeferred<KeywordRuleLink>()
        ports.keywordAction = { Outcome.Success(gate.await()) }
        ports.searchAction = { error("Editing must not execute a search") }
        val c = controller(ports, exactKeywords = true)
        c.lookupKeyword("Flying"); runCurrent(); assertTrue(c.state.value.searchLoading)
        c.queryInput = "new draft"
        gate.complete(KeywordRuleLink("Flying", generation, Knowledge.Known(entry("old").id), SnapshotList.empty()))
        advanceUntilIdle()
        assertEquals("new draft", c.queryInput); assertNull(c.state.value.query)
        assertFalse(c.state.value.searchLoading); assertTrue(c.state.value.results.isEmpty())
        assertNull(c.state.value.selected); assertTrue(ports.entryRequests.isEmpty())
    }

    @Test fun editingSupersedesPendingPageAndDetailAndInvalidSubmissionCannotRestoreThem() = runTest {
        for (detail in listOf(false, true)) {
            val ports = Ports(); val c = controller(ports)
            c.queryInput = "old"; c.submit(); advanceUntilIdle()
            val old = c.state.value.results.single()
            val pageGate = CompletableDeferred<Page<RuleEntry>>()
            val entryGate = CompletableDeferred<RuleEntry>()
            if (detail) {
                ports.entryAction = { _, _ -> Outcome.Success(entryGate.await()) }
                c.select(old)
            } else {
                ports.searchAction = { Outcome.Success(pageGate.await()) }
                c.loadMore()
            }
            runCurrent()
            c.queryInput = " "; c.submit()
            pageGate.complete(page(listOf(entry("old-late"))))
            entryGate.complete(old); advanceUntilIdle()
            assertEquals(" ", c.queryInput); assertNull(c.state.value.query)
            assertEquals(Problem.INVALID_INPUT, c.state.value.searchProblem)
            assertTrue(c.state.value.results.isEmpty()); assertNull(c.state.value.selected)
            assertNull(c.state.value.nextCursor); assertFalse(c.state.value.searchLoading)
            assertFalse(c.state.value.detailLoading)
            c.queryInput = "corrected"
            assertNull(c.state.value.searchProblem)
        }
    }

    @Test fun knownExactKeywordReadsTheActualPinnedGlossaryAndKeepsItsRealRowOnBack() = runTest {
        val ports = Ports(); val glossary = entry("actual-stored-glossary").copy(kind = RuleEntryKind.GLOSSARY, number = Knowledge.Absent("No number"), title = Knowledge.Known("Flying"))
        ports.keywordAction = { Outcome.Success(KeywordRuleLink(it, generation, Knowledge.Known(glossary.id), SnapshotList.empty())) }
        ports.entryAction = { _, _ -> Outcome.Success(glossary) }
        ports.searchAction = { error("Exact target must not be invented by text search") }
        val c = controller(ports, exactKeywords = true)
        c.lookupKeyword("Flying"); advanceUntilIdle()
        assertEquals(glossary, c.state.value.selected); assertEquals(listOf(glossary), c.state.value.results)
        assertEquals(listOf(glossary.id to generation), ports.entryRequests)
        assertFalse(c.state.value.keywordFallback)
        c.clearSelection(); assertEquals(listOf(glossary), c.state.value.results)
    }

    @Test fun absentAndAmbiguousLinksAreVisiblyPinnedSearchFallbacksWithoutInventedTargets() = runTest {
        for (target in listOf(Knowledge.Absent("No exact title"), Knowledge.Unknown("Ambiguous title", SnapshotList.empty()))) {
            val ports = Ports(); var query: RulesQuery? = null
            ports.keywordAction = { Outcome.Success(KeywordRuleLink(it, generation, target, SnapshotList.empty())) }
            ports.searchAction = { query = it; Outcome.Success(page(emptyList())) }
            val c = controller(ports, exactKeywords = true)
            c.lookupKeyword("Flying"); advanceUntilIdle()
            assertEquals(generation, query!!.corpusId); assertEquals("Flying", query!!.text)
            assertTrue(c.state.value.keywordFallback); assertNull(c.state.value.selected); assertTrue(ports.entryRequests.isEmpty())
        }
    }

    @Test fun aNewSearchSupersedesPendingKeywordAuthorityAndWrongKeywordReplyIsRejected() = runTest {
        val ports = Ports(); val gate = CompletableDeferred<KeywordRuleLink>()
        ports.keywordAction = { gate.await().let { Outcome.Success(it) } }
        val c = controller(ports, exactKeywords = true)
        c.lookupKeyword("Flying"); runCurrent()
        c.queryInput = "New query"; c.submit(); advanceUntilIdle()
        gate.complete(KeywordRuleLink("Flying", generation, Knowledge.Known(entry("old").id), SnapshotList.empty())); advanceUntilIdle()
        assertEquals("New query", c.state.value.query); assertNull(c.state.value.selected); assertTrue(ports.entryRequests.isEmpty())
        ports.keywordAction = { Outcome.Success(KeywordRuleLink("Wrong keyword", generation, Knowledge.Absent("None"), SnapshotList.empty())) }
        c.lookupKeyword("Flying"); advanceUntilIdle()
        assertEquals(Problem.CORRUPT_DATA, c.state.value.searchProblem); assertNull(c.state.value.selected)
    }

    @Test fun anExactTargetCannotSubstituteAnotherCorpusOrANumberedEntry() = runTest {
        for (wrong in listOf(entry("target"), entry("target", GenerationId("other")).copy(kind = RuleEntryKind.GLOSSARY))) {
            val ports = Ports()
            ports.keywordAction = { Outcome.Success(KeywordRuleLink(it, generation, Knowledge.Known(entry("target").id), SnapshotList.empty())) }
            ports.entryAction = { _, _ -> Outcome.Success(wrong) }
            val c = controller(ports, exactKeywords = true)
            c.lookupKeyword("Flying"); advanceUntilIdle()
            assertEquals(Problem.STALE_GENERATION, c.state.value.searchProblem); assertNull(c.state.value.selected)
        }
    }

    @Test fun keywordHandoffClearsOldDetailEvenForInvalidInputThenUsesActualLocalSearch() = runTest {
        val ports = Ports(); val c = controller(ports)
        c.queryInput = "Old query"; c.submit(); advanceUntilIdle()
        c.select(c.state.value.results.first()); advanceUntilIdle(); assertNotNull(c.state.value.selected)
        c.lookupKeyword("x".repeat(129)); advanceUntilIdle()
        assertNull(c.state.value.selected); assertTrue(c.state.value.results.isEmpty()); assertEquals(Problem.INVALID_INPUT, c.state.value.searchProblem)
        var supplied: RulesQuery? = null
        ports.searchAction = { query -> supplied = query; Outcome.Success(page(listOf(entry("100.1")))) }
        c.lookupKeyword("Flying"); advanceUntilIdle()
        assertEquals("Flying", supplied!!.text); assertEquals("Flying", c.queryInput)
        assertEquals(generation, c.state.value.generation); assertNull(c.state.value.selected)
    }

    @Test
    fun twoPassImportReopensAndClosesIndependentStreamsWithOnlyKnownLocalFacts() = runTest {
        val ports = Ports()
        val documents = MemoryDocuments()
        val opens = AtomicInteger()
        val closes = AtomicInteger()
        val bytes = ByteArray(70_000) { (it % 127).toByte() }
        documents.put(uri, MemoryDocuments.Entry("rules.txt", bytes.size.toLong(), {
            opens.incrementAndGet()
            object : ByteArrayInputStream(bytes) { override fun close() { closes.incrementAndGet(); super.close() } }
        }))
        ports.importAction = { supplied, reopen ->
            assertEquals(provider, supplied.provider)
            assertEquals(LanguageTag.ENGLISH, supplied.language)
            assertTrue(supplied.version is Knowledge.Unknown)
            assertTrue(supplied.sourceDate is Knowledge.Unknown)
            assertTrue(supplied.notice is Knowledge.Unknown)
            assertTrue(supplied.expectedBytes is Knowledge.Absent)
            repeat(2) { pass ->
                var offset = 0L
                var sequence = 0L
                var finals = 0
                val received = mutableListOf<Byte>()
                reopen().collect { chunk ->
                    assertEquals(offset, chunk.offset)
                    assertEquals(sequence++, chunk.sequence)
                    received += chunk.bytes.copyBytes().toList()
                    offset += chunk.bytes.size
                    if (chunk.final) finals++
                }
                assertArrayEquals(bytes, received.toByteArray())
                assertEquals(1, finals)
                assertEquals(pass + 1, closes.get())
            }
            Outcome.Success(LocalRulesImportResult(corpus(), bytes.size.toLong(), hash,
                ChecksumVerification.COMPUTED_WITHOUT_EXPECTATION, UtcTimestamp(1)))
        }
        val controller = controller(ports, documents)
        assertTrue(controller.start(DocumentRef(uri)))
        advanceUntilIdle()
        assertEquals(2, opens.get())
        assertEquals(2, closes.get())
        assertEquals(2, controller.state.value.pass)
        assertEquals(RulesImportPhase.SUCCEEDED, controller.state.value.importPhase)
        assertEquals(generation, controller.state.value.active?.id)
    }

    @Test
    fun cancellationClosesTheCollectingDocumentAndRefreshesInstalledStatus() = runTest {
        val ports = Ports()
        val documents = MemoryDocuments()
        val closed = AtomicInteger()
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        documents.put(uri, MemoryDocuments.Entry("rules.txt", null, {
            object : ByteArrayInputStream(ByteArray(70_000)) { override fun close() { closed.incrementAndGet(); super.close() } }
        }))
        ports.importAction = { _, reopen ->
            reopen().collect { entered.complete(Unit); gate.await() }
            error("Cancelled import must not finish")
        }
        val controller = controller(ports, documents)
        controller.start(DocumentRef(uri))
        advanceUntilIdle()
        assertTrue(entered.isCompleted)
        assertFalse(controller.start(DocumentRef(uri)))
        controller.cancelImport()
        advanceUntilIdle()
        assertEquals(1, closed.get())
        assertEquals(RulesImportPhase.CANCELLED, controller.state.value.importPhase)
        assertEquals(generation, controller.state.value.active?.id)
    }

    @Test
    fun secondPassPermissionFailureCannotBecomeSuccess() = runTest {
        val ports = Ports()
        val documents = MemoryDocuments()
        val opens = AtomicInteger()
        val closes = AtomicInteger()
        documents.put(uri, MemoryDocuments.Entry("rules.txt", null, {
            if (opens.incrementAndGet() == 2) throw SecurityException("Synthetic revocation")
            object : ByteArrayInputStream(byteArrayOf(1)) { override fun close() { closes.incrementAndGet(); super.close() } }
        }))
        ports.importAction = { _, reopen ->
            repeat(2) { reopen().collect {} }
            error("Revoked second pass must fail")
        }
        val controller = controller(ports, documents)
        controller.start(DocumentRef(uri))
        advanceUntilIdle()
        assertEquals(1, closes.get())
        assertEquals(RulesImportPhase.FAILED, controller.state.value.importPhase)
        assertEquals(Problem.PERMISSION, controller.state.value.importProblem)
    }

    @Test
    fun selectedEntryAndResolvedCrossReferencePinTheSearchCorpus() = runTest {
        val ports = Ports()
        val reference = RuleCrossReference("100.2", Knowledge.Known(RuleId(provider, "100.2")), SnapshotList.empty())
        val first = entry("100.1", references = listOf(reference))
        ports.searchAction = { Outcome.Success(page(listOf(first))) }
        ports.entryAction = { id, corpus -> Outcome.Success(if (id == first.id) first else entry(id.raw, corpus)) }
        val controller = controller(ports)
        controller.queryInput = "synthetic"
        controller.submit()
        advanceUntilIdle()
        controller.select(controller.state.value.results.single())
        advanceUntilIdle()
        controller.follow(reference)
        advanceUntilIdle()
        assertEquals(listOf(first.id to generation, RuleId(provider, "100.2") to generation), ports.entryRequests)
        assertEquals("100.2", controller.state.value.selected?.id?.raw)
        assertEquals(generation, controller.state.value.selected?.corpusId)
    }

    @Test
    fun staleEntryClearsResultsSelectionAndPaginationInsteadOfSubstitutingActive() = runTest {
        val ports = Ports()
        val controller = controller(ports)
        controller.queryInput = "synthetic"
        controller.submit()
        advanceUntilIdle()
        ports.entryAction = { _, _ -> rejected(FailureCode.STALE_GENERATION) }
        controller.select(controller.state.value.results.single())
        advanceUntilIdle()
        assertTrue(controller.state.value.results.isEmpty())
        assertNull(controller.state.value.selected)
        assertNull(controller.state.value.nextCursor)
        assertEquals(Problem.STALE_GENERATION, controller.state.value.searchProblem)
    }

    @Test
    fun pagesArePinnedAndAccumulationStopsAtTheDisclosedBound() = runTest {
        val ports = Ports()
        val requests = mutableListOf<RulesQuery>()
        ports.searchAction = { query ->
            requests += query
            val base = (requests.size - 1) * RulesController.PAGE_SIZE
            Outcome.Success(page((1..query.page.limit).map { entry("${base + it}") }, "page${requests.size}"))
        }
        val controller = controller(ports)
        controller.queryInput = "synthetic"
        controller.submit()
        advanceUntilIdle()
        repeat(4) { controller.loadMore(); advanceUntilIdle() }
        assertEquals(4, requests.size)
        assertNull(requests.first().corpusId)
        assertTrue(requests.drop(1).all { it.corpusId == generation })
        assertEquals(200, controller.state.value.results.size)
        assertFalse(controller.state.value.canLoadMore)
    }

    @Test
    fun mismatchedPageSnapshotClearsSafelyAndRefusesEntryReads() = runTest {
        val ports = Ports()
        ports.searchAction = { Outcome.Success(page(listOf(entry("100.1", GenerationId("other"))))) }
        val controller = controller(ports)
        controller.queryInput = "synthetic"
        controller.submit()
        advanceUntilIdle()
        assertTrue(controller.state.value.results.isEmpty())
        assertNull(controller.state.value.generation)
        assertEquals(Problem.STALE_GENERATION, controller.state.value.searchProblem)
        controller.select(entry("100.1"))
        assertTrue(ports.entryRequests.isEmpty())
    }
    @Test
    fun routeControllerRequiresBothRealCapabilities() = runTest {
        val ports = Ports()
        val catalog = SyntheticCatalog()
        fun shell(importer: LocalRulesImport?, lookup: LocalRulesLookup?) = ShellControllers(ownerScope(),
            ShellPorts(catalog, catalog, catalog, SyntheticSettings(), rulesImport = importer, rulesLookup = lookup),
            MemoryDocuments(), StandardTestDispatcher(testScheduler))
        assertNull(shell(null, null).rules)
        assertNull(shell(ports, null).rules)
        assertNull(shell(null, ports).rules)
        assertNotNull(shell(ports, ports).rules)
        advanceUntilIdle()
    }

    @Test
    fun replacementStatusClearsPreviouslySelectedSnapshot() = runTest {
        val ports = Ports()
        val controller = controller(ports)
        controller.refresh()
        controller.queryInput = "synthetic"
        controller.submit()
        advanceUntilIdle()
        controller.select(controller.state.value.results.single())
        advanceUntilIdle()
        assertNotNull(controller.state.value.selected)
        ports.statusValue = LocalRulesStatus(Knowledge.Known(corpus().copy(id = GenerationId("new"))), Knowledge.Absent("No attempt"))
        controller.refresh()
        advanceUntilIdle()
        assertNull(controller.state.value.selected)
        assertTrue(controller.state.value.results.isEmpty())
        assertNull(controller.state.value.nextCursor)
        assertEquals(Problem.STALE_GENERATION, controller.state.value.searchProblem)
    }

    @Test
    fun oversizedProviderReportRefusesOpenAndImport() = runTest {
        val ports = Ports()
        val documents = MemoryDocuments()
        documents.put(uri, MemoryDocuments.Entry("rules.txt", RulesController.MAX_BYTES + 1, { error("Must not open oversized input") }))
        ports.importAction = { _, _ -> error("Must not import oversized input") }
        val controller = controller(ports, documents)
        controller.start(DocumentRef(uri))
        advanceUntilIdle()
        assertEquals(RulesImportPhase.FAILED, controller.state.value.importPhase)
        assertTrue(controller.state.value.importTooLarge)
    }

    @Test
    fun repeatedSparsePagesStillStopAtAFiniteRequestBound() = runTest {
        val ports = Ports()
        var requests = 0
        ports.searchAction = {
            requests++
            Outcome.Success(page(listOf(entry("100.1")), "same-cursor"))
        }
        val controller = controller(ports)
        controller.queryInput = "synthetic"
        controller.submit()
        advanceUntilIdle()
        repeat(6) { controller.loadMore(); advanceUntilIdle() }
        assertEquals(RulesController.MAX_PAGES, requests)
        assertEquals(1, controller.state.value.results.size)
        assertFalse(controller.state.value.canLoadMore)
    }

}
