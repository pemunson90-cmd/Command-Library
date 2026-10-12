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
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.view.WindowCompat
import android.os.Build
import com.architectpsg.commandlibrary.core.common.CoroutineCancelToken
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.ThemePreference
import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        rule.runOnIdle {
            val bars = WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView)
            assertFalse(bars.isAppearanceLightStatusBars)
            if (Build.VERSION.SDK_INT >= 26) assertFalse(bars.isAppearanceLightNavigationBars)
        }

        val lightOption = hasText(string(ShellR.string.settings_theme_light)) and
            SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected)
        rule.onAllNodes(lightOption).onFirst().performScrollTo().performClick()
        rule.waitUntil(20_000) {
            rule.onAllNodes(lightOption).fetchSemanticsNodes().any {
                it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Selected) { false }
            } && rule.onAllNodes(hasText(string(ShellR.string.settings_saving))).fetchSemanticsNodes().isEmpty()
        }
        rule.runOnIdle {
            val bars = WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView)
            assertTrue(bars.isAppearanceLightStatusBars)
            if (Build.VERSION.SDK_INT >= 26) assertTrue(bars.isAppearanceLightNavigationBars)
        }
    }

    @Test
    fun realRulesImportIsSearchableInTheAppWithoutChangingConsent() {
        // Authored synthetic content exercises the real graph and SQLite, never source coverage.
        val bytes = "100.1. Synthetic local rule for the application integration test.\n".toByteArray()
        val unknown = Knowledge.Unknown("Synthetic input has no upstream metadata", SnapshotList.empty())
        val source = LocalRulesImportSource(
            ResourceUri("content://synthetic-app/rules"), "synthetic-rules.txt", ProviderId("synthetic-app"),
            LanguageTag.ENGLISH, unknown, unknown, unknown, unknown, unknown,
            Knowledge.Absent("No expected hash"), Knowledge.Known(bytes.size.toLong()),
        )
        val graph = (rule.activity.application as CommandLibraryApplication).graph
        runBlocking {
            val ports = (graph.ports() as Outcome.Success).value
            val token = CoroutineCancelToken(currentCoroutineContext())
            val before = (ports.settings.read(token) as Outcome.Success).value.consents
            val imported = checkNotNull(ports.rulesImport).import(source,
                { flowOf(ByteChunk(0, 0, SnapshotBytes(bytes), null, true)) }, {}, token)
            check(imported is Outcome.Success) { "Real rules import failed: $imported" }
            assertEquals(before, (ports.settings.read(token) as Outcome.Success).value.consents)
        }
        waitForText(string(ShellR.string.rules_title))
        rule.onNodeWithTag("shell-nav-rules").performClick()
        waitForText(string(ShellR.string.rules_choose))
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("synthetic")
        rule.onAllNodes(hasText(string(ShellR.string.search_action)) and hasClickAction()).onFirst().performClick()
        waitForText("100.1")
        rule.onAllNodes(hasText("100.1") and hasClickAction()).onFirst().performScrollTo().performClick()
        rule.waitUntil(20_000) {
            rule.onAllNodes(hasText("Synthetic local rule", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
