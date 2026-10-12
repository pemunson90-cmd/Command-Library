@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.ThemePreference
import com.architectpsg.commandlibrary.feature.shell.MemoryDocuments
import com.architectpsg.commandlibrary.feature.shell.ShellPorts
import com.architectpsg.commandlibrary.feature.shell.SyntheticCatalog
import com.architectpsg.commandlibrary.feature.shell.SyntheticSettings
import com.architectpsg.commandlibrary.feature.shell.document.DOCUMENT_CHUNK_BYTES
import com.architectpsg.commandlibrary.feature.shell.document.DocumentRef
import com.architectpsg.commandlibrary.feature.shell.failure
import com.architectpsg.commandlibrary.feature.shell.syntheticBytes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One ShellControllers instance stands for one ViewModel; a new instance stands for process recreation. */
class ShellControllersTest {
    private val uri = "content://synthetic.documents/document/catalog.json"
    private val catalog = SyntheticCatalog(listOf("Synthetic Existing Card"))
    private val settings = SyntheticSettings(ThemePreference.DARK)
    private val documents = MemoryDocuments()
    private val ports = ShellPorts(catalog, catalog, catalog, settings)

    private fun TestScope.owner(scope: CoroutineScope = ownerScope()) =
        ShellControllers(scope, ports, documents, StandardTestDispatcher(testScheduler))

    @Test
    fun freshInstanceReadsCommittedStateOnly() = runTest {
        val controllers = owner()
        advanceUntilIdle()
        assertEquals(1, catalog.statusCalls.get())
        assertEquals(1, settings.reads.get())
        assertEquals(catalog.active!!.id, controllers.status.state.value.status!!.activeGeneration()!!.id)
        assertEquals(ThemePreference.DARK, controllers.settings.state.value.theme)
        assertEquals(ImportUiState.Idle, controllers.catalogImport.state.value)
    }

    @Test
    fun emptyCatalogStatusIsTruthful() = runTest {
        val empty = SyntheticCatalog()
        val controllers = ShellControllers(ownerScope(), ShellPorts(empty, empty, empty, settings), documents, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        val status = controllers.status.state.value.status!!
        assertTrue(!status.installed)
        assertNull(status.activeGeneration())
        assertTrue(status.installedLanguages.isEmpty())
    }

    @Test
    fun statusFailureIsReportedWithoutInventingAStatus() = runTest {
        catalog.statusOverride = failure(FailureCode.CORRUPT_DATA)
        val controllers = owner()
        advanceUntilIdle()
        assertEquals(Problem.CORRUPT_DATA, controllers.status.state.value.problem)
        assertNull(controllers.status.state.value.status)
        catalog.statusOverride = null
        controllers.status.refresh()
        advanceUntilIdle()
        assertNull(controllers.status.state.value.problem)
    }

    @Test
    fun recreationAfterACancelledImportShowsThePreviousCatalog() = runTest {
        val previous = catalog.active!!.id
        documents.putBytes(uri, "catalog.json", syntheticBytes(4 * DOCUMENT_CHUNK_BYTES))
        catalog.importGate = CompletableDeferred()
        val ownerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val first = owner(ownerScope)
        advanceUntilIdle()
        first.catalogImport.start(DocumentRef(uri))
        advanceUntilIdle()
        assertTrue(first.catalogImport.state.value is ImportUiState.Running)

        ownerScope.cancel()
        advanceUntilIdle()
        assertTrue("clearing the owner cancels the import", catalog.importCancelled.get())

        val recreated = owner()
        advanceUntilIdle()
        assertEquals(ImportUiState.Idle, recreated.catalogImport.state.value)
        assertEquals(previous, recreated.status.state.value.status!!.activeGeneration()!!.id)
    }

    @Test
    fun recreationAfterASuccessfulImportReadsTheCommittedGenerationWithoutReplayingSuccess() = runTest {
        documents.putBytes(uri, "catalog.json", syntheticBytes(1_000))
        val first = owner()
        advanceUntilIdle()
        first.catalogImport.start(DocumentRef(uri))
        advanceUntilIdle()
        val imported = (first.catalogImport.state.value as ImportUiState.Succeeded).result.generation.id
        assertEquals("status re-read after import", imported, first.status.state.value.status!!.activeGeneration()!!.id)

        val recreated = owner()
        advanceUntilIdle()
        assertEquals(ImportUiState.Idle, recreated.catalogImport.state.value)
        assertEquals(imported, recreated.status.state.value.status!!.activeGeneration()!!.id)
    }

    @Test
    fun importingANewCatalogMarksEarlierSearchResultsStale() = runTest {
        documents.putBytes(uri, "catalog.json", syntheticBytes(1_000))
        catalog.namesForNextImport = listOf("Synthetic Replacement Card")
        val controllers = owner()
        advanceUntilIdle()
        controllers.search.search("synthetic")
        advanceUntilIdle()
        assertTrue(!controllers.search.state.value.stale)

        controllers.catalogImport.start(DocumentRef(uri))
        advanceUntilIdle()

        assertTrue(controllers.search.state.value.stale)
        controllers.search.searchAgain()
        advanceUntilIdle()
        assertEquals(listOf("Synthetic Replacement Card"), controllers.search.state.value.results.map { it.name })
        assertTrue(!controllers.search.state.value.stale)
    }
}
