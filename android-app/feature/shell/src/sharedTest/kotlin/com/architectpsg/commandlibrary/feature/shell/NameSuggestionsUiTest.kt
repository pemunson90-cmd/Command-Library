package com.architectpsg.commandlibrary.feature.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NameSuggestionsUiTest {
    @get:Rule val rule = createComposeRule()
    private var shell: ShellUiHarness? = null
    @Before fun budget() = profileHostIdleBudget(300)
    @After fun close() { shell?.close(); profileHostIdleBudget(60) }
    @Test fun typingOffersBoundedLocalNamesAndARealSuggestionOpensItsCardAtLargeFont() {
        val s = ShellUiHarness(rule); shell = s
        s.show(width = 360.dp, height = 800.dp, fontScale = 2f)
        rule.onNode(hasSetTextAction()).performScrollTo().performTextInput("synthetic")
        s.awaitState { s.controllers.search.state.value.suggestions.isNotEmpty() }
        rule.onNodeWithTag("name-suggestion-synthetic-oracle-synthetic-existing-card")
            .performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        s.awaitState { s.controllers.search.state.value.selected != null }
        assertEquals("Synthetic Existing Card", s.controllers.search.state.value.selected!!.name)
        assertEquals("synthetic", s.controllers.search.queryInput)
        assertFalse(s.controllers.search.state.value.searched)
        assertEquals(6, s.catalog.lookupRequests.single().second.limit)
        assertEquals(0, s.catalog.importCalls.get())
        s.text("Synthetic Existing Card").assertIsDisplayed()
    }
}
