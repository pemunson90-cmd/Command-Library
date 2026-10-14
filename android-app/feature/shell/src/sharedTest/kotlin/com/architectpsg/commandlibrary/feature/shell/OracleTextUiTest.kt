package com.architectpsg.commandlibrary.feature.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.CatalogTextField
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OracleTextUiTest {
    @get:Rule val rule = createComposeRule()
    private var shell: ShellUiHarness? = null
    @Before fun budget() = profileHostIdleBudget(300)
    @After fun close() { shell?.close(); profileHostIdleBudget(60) }
    @Test fun localOracleTextSelectionSubmitsExactDraftAndDisplaysActualPortResultAtLargeFont() {
        val s = ShellUiHarness(rule, indexedText = true); shell = s
        s.show(width = 360.dp, height = 800.dp, fontScale = 2f)
        rule.onNodeWithTag("search-field-ORACLE_TEXT").performScrollTo().assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
        s.text(s.string(R.string.search_action)).performScrollTo().performClick()
        s.text(s.string(R.string.search_oracle_text_empty)).performScrollTo().assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performScrollTo().performTextInput("draw")
        s.text(s.string(R.string.search_action)).performScrollTo().performClick()
        s.awaitState { !s.controllers.search.state.value.results.isEmpty() }
        assertEquals(listOf(CatalogTextField.ORACLE_TEXT to "draw"), s.textLookup.requests)
        s.text("Synthetic text-only match").performScrollTo().assertIsDisplayed()
        assertEquals(0, s.catalog.importCalls.get())
    }
    @Test fun absentCapabilityRetainsNameSearchWithoutInertFieldCommands() {
        val s = ShellUiHarness(rule); shell = s; s.show()
        rule.onNodeWithTag("search-field-ORACLE_TEXT").assertDoesNotExist()
        assertFalse(s.controllers.search.supportsOracleText)
    }
}
