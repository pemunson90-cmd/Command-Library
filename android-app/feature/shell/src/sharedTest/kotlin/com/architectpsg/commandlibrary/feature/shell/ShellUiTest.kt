package com.architectpsg.commandlibrary.feature.shell

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.Revision
import com.architectpsg.commandlibrary.core.model.ThemePreference
import com.architectpsg.commandlibrary.feature.shell.ShellUiHarness.Companion.PICKED_URI
import com.architectpsg.commandlibrary.feature.shell.document.DOCUMENT_CHUNK_BYTES
import com.architectpsg.commandlibrary.feature.shell.ui.ShellDestination
import com.architectpsg.commandlibrary.feature.shell.ui.ShellTags
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose UI over synthetic, test-only ports. Runs under Robolectric on the JVM and as emulator instrumentation. */
@RunWith(AndroidJUnit4::class)
class ShellUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val shell = ShellUiHarness(rule)

    @After
    fun close() = shell.close()

    private fun open(destination: ShellDestination) = rule.onNodeWithTag(destination.tag).performClick()

    /** Brings a lazily composed result-list item into the tree before it is asserted or clicked. */
    private fun scrollResultsTo(text: String) {
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }

    private fun search(query: String) {
        // Set the field through Compose semantics rather than the emulator IME. Hosted 16 KiB
        // images can drop/delay InputConnection text events under load, making this UI test flaky.
        rule.onNode(hasSetTextAction()).performTextReplacement(query)
        rule.waitForIdle()
        rule.onNode(hasText(shell.string(R.string.search_action)) and hasClickAction()).performClick()
    }

    @Test
    fun emptyCatalogShowsTruthfulStatusAndVisibleLimits() {
        shell.catalog.active = null
        shell.show(width = 360.dp)
        open(ShellDestination.CATALOG)

        shell.waitForText(shell.string(R.string.catalog_empty_title))
        shell.text(shell.string(R.string.catalog_title)).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        shell.text(shell.string(R.string.shell_language_english)).assertIsDisplayed()
        listOf(
            shell.string(R.string.import_limit_size, shell.string(R.string.shell_size_mib, "8")),
            shell.string(R.string.import_limit_records, "10,000"),
            shell.string(R.string.import_limit_record_size, shell.string(R.string.shell_size_kib, "128")),
            shell.string(R.string.import_limit_nesting, 32),
            shell.string(R.string.import_limit_language),
        ).forEach { line -> rule.onNode(hasText(line, substring = true)).assertExists(line) }
        shell.text(shell.string(R.string.import_action_choose)).performScrollTo().assertHasClickAction().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun importShowsRealProgressAndCancelKeepsTheInstalledCatalog() {
        val size = 5 * DOCUMENT_CHUNK_BYTES
        shell.documents.putBytes(PICKED_URI, "catalog.json", syntheticBytes(size))
        shell.catalog.importGate = CompletableDeferred()
        val previous = shell.catalog.active!!.id
        shell.show()
        open(ShellDestination.CATALOG)

        shell.text(shell.string(R.string.import_action_choose)).performScrollTo().performClick()

        assertTrue("picker offered JSON documents", (shell.picker.launchedInputs.single() as Array<*>).contains("application/json"))
        shell.waitForText(shell.string(R.string.import_running_title, "catalog.json"))
        shell.waitForText(shell.plural(R.plurals.import_records_saved, 1, "1"))
        shell.text(shell.string(R.string.import_phase_staging)).assertExists()
        rule.onNode(hasText("160 KiB", substring = true)).assertExists()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and
            SemanticsMatcher("has description") { it.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.contains(shell.string(R.string.import_progress_description)) == true })
            .assertExists()

        shell.text(shell.string(R.string.import_action_cancel)).performScrollTo().performClick()

        shell.waitForText(shell.string(R.string.import_cancelled_title))
        assertTrue(shell.catalog.importCancelled.get())
        assertEquals(previous, shell.catalog.active!!.id)
        shell.text(shell.string(R.string.import_cancelled_body, "catalog.json")).assertExists()
        shell.text(shell.string(R.string.import_action_choose)).assertHasClickAction()
    }

    @Test
    fun successfulImportReportsResultAndRefreshesStatus() {
        shell.catalog.active = null
        shell.catalog.namesForNextImport = listOf("Synthetic One", "Synthetic Two", "Synthetic Three")
        shell.documents.putBytes(PICKED_URI, "catalog.json", syntheticBytes(4_000))
        shell.show()
        open(ShellDestination.CATALOG)
        shell.text(shell.string(R.string.import_action_choose)).performScrollTo().performClick()

        shell.waitForText(shell.string(R.string.import_succeeded_title))
        shell.text(shell.string(R.string.import_succeeded_body, "catalog.json")).assertExists()
        shell.text(shell.string(R.string.import_result_checksum_computed)).assertExists()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(shell.string(R.string.catalog_empty_title)).fetchSemanticsNodes().isEmpty() }
        shell.text(shell.string(R.string.catalog_source_date_unknown_note)).assertExists()
        shell.text(shell.catalog.active!!.id.value).assertExists()
    }

    @Test
    fun failedImportExplainsThePlainReason() {
        shell.documents.putBytes(PICKED_URI, "catalog.json", syntheticBytes(100))
        shell.catalog.importOutcomeOverride = failure(FailureCode.INVALID_INPUT)
        shell.show()
        open(ShellDestination.CATALOG)
        shell.text(shell.string(R.string.import_action_choose)).performScrollTo().performClick()

        shell.waitForText(shell.string(R.string.import_failed_title))
        shell.text(shell.string(R.string.import_failed_invalid)).assertExists()
        shell.text(shell.string(R.string.import_failed_unchanged)).assertExists()
        rule.onAllNodesWithText("synthetic failure", substring = true).assertCountEquals(0)
    }

    @Test
    fun searchPagesThroughResultsAndOpensDetailsOnCompactWidth() {
        shell.catalog.active = shell.catalog.newGeneration((1..70).map { "Synthetic Card %02d".format(it) }, bytes = 0)
        shell.show(width = 360.dp)
        assertTrue(shell.exists(ShellTags.NAVIGATION_BAR))

        search("synthetic")
        shell.waitForText(shell.plural(R.plurals.search_results_count, 50, "50"))
        shell.text(shell.string(R.string.search_more_available)).assertExists()
        scrollResultsTo(shell.string(R.string.search_load_more))
        shell.text(shell.string(R.string.search_load_more)).performClick()
        shell.awaitState { shell.controllers.search.state.value.results.size == 70 }
        scrollResultsTo("Synthetic Card 70")
        rule.onAllNodesWithText(shell.string(R.string.search_load_more)).assertCountEquals(0)
        scrollResultsTo(shell.plural(R.plurals.search_results_count, 70, "70"))

        scrollResultsTo("Synthetic Card 03")
        shell.text("Synthetic Card 03").assert(SemanticsMatcher("opens details") {
            it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.OnClick) { null }?.label == shell.string(R.string.search_result_open)
        }).performClick()
        rule.onNodeWithTag(ShellTags.DETAIL_PANE).assertIsDisplayed()
        shell.text(shell.string(R.string.detail_mana_cost)).assertExists()
        shell.text("{1}{U}").assertExists()
        shell.text("Synthetic rules text for Synthetic Card 03.").assertExists()

        shell.text(shell.string(R.string.detail_back)).performClick()
        rule.onNodeWithTag(ShellTags.RESULTS_PANE).assertIsDisplayed()
        shell.text("Synthetic Card 03").assertExists()
    }

    @Test
    fun staleCursorOffersSearchAgainWithoutMixingSnapshots() {
        shell.catalog.active = shell.catalog.newGeneration((1..60).map { "Synthetic Card %02d".format(it) }, bytes = 0)
        shell.show()
        search("synthetic")
        shell.waitForText(shell.plural(R.plurals.search_results_count, 50, "50"))

        shell.catalog.active = shell.catalog.newGeneration(listOf("Synthetic Replacement"), bytes = 0)
        scrollResultsTo(shell.string(R.string.search_load_more))
        shell.text(shell.string(R.string.search_load_more)).performClick()

        shell.awaitState { shell.controllers.search.state.value.stale }
        scrollResultsTo(shell.string(R.string.search_stale_title))
        shell.text(shell.plural(R.plurals.search_results_count, 50, "50")).assertExists()
        assertEquals("no page from the new snapshot mixed in", 50, shell.controllers.search.state.value.results.size)
        assertFalse(shell.controllers.search.state.value.canLoadMore)
        shell.text(shell.string(R.string.search_again)).performClick()
        shell.waitForText("Synthetic Replacement")
        rule.onAllNodesWithText(shell.string(R.string.search_stale_title)).assertCountEquals(0)
    }

    @Test
    fun searchWithoutACatalogLeadsToImport() {
        shell.catalog.active = null
        shell.show()
        shell.waitForText(shell.string(R.string.search_no_catalog))
        shell.text(shell.string(R.string.search_action_open_catalog)).performClick()
        shell.waitForText(shell.string(R.string.catalog_title))
        shell.text(shell.string(R.string.catalog_empty_title)).assertExists()
    }

    @Test
    fun emptyQueryIsExplainedAtTheField() {
        shell.show()
        rule.onNode(hasText(shell.string(R.string.search_action)) and hasClickAction()).performClick()
        shell.waitForText(shell.string(R.string.search_problem_empty))
        assertTrue(shell.catalog.lookupRequests.isEmpty())
    }

    @Test
    fun themeSelectionPersistsAgainstTheReadRevisionAndReportsStaleChanges() {
        shell.show()
        open(ShellDestination.SETTINGS)
        shell.waitForText(shell.string(R.string.settings_theme_title))
        shell.text(shell.string(R.string.settings_theme_system)).assertIsSelected()

        shell.text(shell.string(R.string.settings_theme_dark)).performClick()
        shell.awaitState { shell.settings.stored.theme == ThemePreference.DARK }
        rule.waitForIdle()
        assertEquals(Revision(0), shell.settings.updates.single().second)
        shell.text(shell.string(R.string.settings_theme_dark)).assertIsSelected()

        shell.settings.changeElsewhere(ThemePreference.LIGHT)
        shell.text(shell.string(R.string.settings_theme_system)).performClick()
        shell.waitForText(shell.string(R.string.settings_changed_elsewhere))
        shell.text(shell.string(R.string.settings_theme_light)).assertIsSelected()
        assertEquals(ThemePreference.LIGHT, shell.settings.stored.theme)
    }

    @Test
    fun themeControlsLockWhileSaving() {
        shell.settings.updateGate = CompletableDeferred()
        shell.show()
        open(ShellDestination.SETTINGS)
        shell.waitForText(shell.string(R.string.settings_theme_title))
        shell.text(shell.string(R.string.settings_theme_dark)).performClick()
        shell.waitForText(shell.string(R.string.settings_saving))
        shell.text(shell.string(R.string.settings_theme_light)).assertIsNotEnabled()
        shell.settings.updateGate!!.complete(Unit)
        rule.waitUntil(10_000) { rule.onAllNodesWithText(shell.string(R.string.settings_saving)).fetchSemanticsNodes().isEmpty() }
        shell.text(shell.string(R.string.settings_theme_light)).assertIsEnabled()
    }

    @Test
    fun compactWidthUsesBottomNavigation() {
        shell.show(width = 599.dp)
        assertTrue(shell.exists(ShellTags.NAVIGATION_BAR))
        assertFalse(shell.exists(ShellTags.NAVIGATION_RAIL))
        ShellDestination.entries.filter { it != ShellDestination.RULES }.forEach {
            rule.onNodeWithTag(it.tag).assertHasClickAction().assertHeightIsAtLeast(48.dp)
        }
        rule.onNodeWithTag(ShellDestination.RULES.tag).assertDoesNotExist()
    }

    @Test
    fun mediumWidthUsesARailAndOnePane() {
        shell.show(width = 600.dp)
        assertTrue(shell.exists(ShellTags.NAVIGATION_RAIL))
        assertFalse(shell.exists(ShellTags.NAVIGATION_BAR))
        search("synthetic")
        shell.waitForText("Synthetic Existing Card")
        shell.text("Synthetic Existing Card").performClick()
        rule.onNodeWithTag(ShellTags.DETAIL_PANE).assertIsDisplayed()
        assertFalse("details replace results", shell.exists(ShellTags.RESULTS_PANE))
    }

    @Test
    fun widthJustBelowExpandedStaysSinglePane() {
        shell.show(width = 839.dp, height = 700.dp)
        assertTrue(shell.exists(ShellTags.NAVIGATION_RAIL))
        assertFalse(shell.exists(ShellTags.DETAIL_PANE))
        rule.onAllNodesWithText(shell.string(R.string.detail_select_prompt)).assertCountEquals(0)
    }

    @Test
    fun configurationChangeKeepsDestinationAndRunningImport() {
        shell.documents.putBytes(PICKED_URI, "catalog.json", syntheticBytes(3 * DOCUMENT_CHUNK_BYTES))
        shell.catalog.importGate = CompletableDeferred()
        val restoration = StateRestorationTester(rule)
        shell.showWith(restoration)
        open(ShellDestination.CATALOG)
        shell.text(shell.string(R.string.import_action_choose)).performScrollTo().performClick()
        shell.waitForText(shell.string(R.string.import_running_title, "catalog.json"))

        restoration.emulateSavedInstanceStateRestore()

        shell.waitForText(shell.string(R.string.import_running_title, "catalog.json"))
        shell.text(shell.string(R.string.import_action_cancel)).performScrollTo().assertHasClickAction()
        assertFalse("import was not restarted", shell.catalog.importCancelled.get())
        assertEquals(1, shell.catalog.importCalls.get())
        shell.catalog.importGate!!.complete(Unit)
        shell.waitForText(shell.string(R.string.import_succeeded_title))
    }

    @Test
    fun expandedWidthShowsResultsAndDetailsTogether() {
        shell.show(width = 840.dp, height = 700.dp)
        assertTrue(shell.exists(ShellTags.NAVIGATION_RAIL))
        shell.text(shell.string(R.string.detail_select_prompt)).assertIsDisplayed()
        search("synthetic")
        fun evidence(): String {
            val requests = synchronized(shell.catalog.lookupRequests) { shell.catalog.lookupRequests.toList() }
            return "queryInput=${shell.controllers.search.queryInput}, lookupRequests=$requests, " +
                "state=${shell.controllers.search.state.value}, semantics=${rule.onRoot().printToString()}"
        }
        val afterSubmit = evidence()
        try {
            shell.awaitState {
                shell.controllers.search.state.value.results.any { it.name == "Synthetic Existing Card" }
            }
            // At this width the result row may be below the lazy list's composed viewport.
            scrollResultsTo("Synthetic Existing Card")
        } catch (failure: Exception) {
            throw AssertionError("Expanded search failed. After submit: $afterSubmit; at failure: ${evidence()}", failure)
        }
        assertEquals("synthetic", shell.controllers.search.queryInput)
        assertEquals("synthetic", synchronized(shell.catalog.lookupRequests) { shell.catalog.lookupRequests.last().first })
        shell.text("Synthetic Existing Card").performClick()
        rule.onNodeWithTag(ShellTags.RESULTS_PANE).assertIsDisplayed()
        rule.onNodeWithTag(ShellTags.DETAIL_PANE).assertIsDisplayed()
        rule.onNode(hasText("Synthetic Existing Card") and hasClickAction()).assertIsSelected()
        shell.text(shell.string(R.string.detail_mana_value)).assertExists()
    }

    @Test
    fun doubledTextKeepsEveryControlReachable() {
        shell.show(width = 360.dp, height = 640.dp, fontScale = 2f)
        rule.onNode(hasText(shell.string(R.string.search_action)) and hasClickAction()).performScrollTo().assertIsDisplayed()
        open(ShellDestination.CATALOG)
        shell.waitForText(shell.string(R.string.catalog_title))
        shell.text(shell.string(R.string.import_action_choose)).performScrollTo().assertIsDisplayed().assertHasClickAction()
        open(ShellDestination.SETTINGS)
        shell.waitForText(shell.string(R.string.settings_theme_title))
        shell.text(shell.string(R.string.settings_theme_dark)).performScrollTo().assertIsDisplayed().assertHasClickAction()
    }
}
