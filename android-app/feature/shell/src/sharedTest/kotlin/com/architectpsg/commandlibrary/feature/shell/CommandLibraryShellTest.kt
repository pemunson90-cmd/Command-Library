package com.architectpsg.commandlibrary.feature.shell

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.feature.shell.ShellDestination
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The public entry point builds its ViewModel from the supplied ports and reads committed state through them. */
@RunWith(AndroidJUnit4::class)
class CommandLibraryShellTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun publicEntryWiresTheSuppliedPorts() {
        val catalog = SyntheticCatalog()
        val settings = SyntheticSettings()
        rule.setContent { CommandLibraryShell(ShellPorts(catalog, catalog, catalog, settings)) }
        rule.onNodeWithTag(ShellDestination.CATALOG.tag).performClick()
        val emptyTitle = ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.catalog_empty_title)
        rule.waitUntil(10_000) { rule.onAllNodesWithText(emptyTitle).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(catalog.statusCalls.get() >= 1)
        assertTrue(settings.reads.get() >= 1)
    }
}
