package com.architectpsg.commandlibrary

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.common.CoroutineCancelToken
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.ThemePreference
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.architectpsg.commandlibrary.feature.shell.R as ShellR

/**
 * Launches the real app: the activity, the process-wide graph, the Room3 database opened through
 * StorageDatabase, LocalCatalog and DataStoreSettingsRepository. No test doubles are installed.
 * This test APK never imports a catalog, so the app's catalog stays empty for every test here.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int): String = rule.activity.getString(id)

    private fun waitForText(text: String, timeoutMillis: Long = 20_000) {
        rule.waitUntil(timeoutMillis) {
            rule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openDestination(label: String) {
        waitForText(label)
        rule.onAllNodes(hasText(label) and hasClickAction()).onFirst().performClick()
    }

    @Test
    fun realCatalogOpensAndReportsNoCatalogInstalled() {
        openDestination(string(ShellR.string.shell_nav_catalog))
        waitForText(string(ShellR.string.catalog_empty_title))
    }

    @Test
    fun searchAgainstTheRealEmptyCatalogSaysNoCatalogIsInstalled() {
        openDestination(string(ShellR.string.shell_nav_cards))
        rule.waitUntil(20_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Lightning Bolt")
        rule.onAllNodes(hasText(string(ShellR.string.search_action)) and hasClickAction()).onFirst().performClick()
        waitForText(string(ShellR.string.search_no_catalog))
    }

    @Test
    fun themeChoiceIsSavedThroughTheRealSettingsRepository() {
        openDestination(string(ShellR.string.shell_nav_settings))
        val dark = string(ShellR.string.settings_theme_dark)
        waitForText(dark)
        val darkOption = hasText(dark) and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected)
        rule.waitUntil(20_000) { rule.onAllNodes(darkOption).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(darkOption).onFirst().performScrollTo().performClick()
        rule.waitUntil(20_000) {
            rule.onAllNodes(darkOption).fetchSemanticsNodes().any {
                it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Selected) { false }
            } && rule.onAllNodes(hasText(string(ShellR.string.settings_saving))).fetchSemanticsNodes().isEmpty()
        }
        rule.onAllNodes(darkOption).onFirst().assertIsSelected()

        val graph = (rule.activity.application as CommandLibraryApplication).graph
        val saved = runBlocking {
            val ports = graph.ports()
            check(ports is Outcome.Success) { "Real ports did not open: $ports" }
            ports.value.settings.read(CoroutineCancelToken(currentCoroutineContext()))
        }
        check(saved is Outcome.Success) { "Settings read failed: $saved" }
        assertEquals(ThemePreference.DARK, saved.value.theme)
    }
}
