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

    private inner class Ports : LocalRulesImport, LocalRulesLookup {
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

    private fun TestScope.controller(ports: Ports, documents: MemoryDocuments = MemoryDocuments()) =
        RulesController(ownerScope(), ports, ports, documents, StandardTestDispatcher(testScheduler))

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
