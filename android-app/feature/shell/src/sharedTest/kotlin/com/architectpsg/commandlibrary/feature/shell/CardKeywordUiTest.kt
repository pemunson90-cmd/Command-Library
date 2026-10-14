package com.architectpsg.commandlibrary.feature.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.presentation.Problem
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardKeywordUiTest {
    @get:Rule val rule = createComposeRule()
    private var shell: ShellUiHarness? = null
    @Before fun budget() = profileHostIdleBudget(300)
    @After fun close() { shell?.close(); profileHostIdleBudget(60) }
    private fun show(rules: KeywordRulesFixture?): ShellUiHarness {
        val s = ShellUiHarness(rule, localRules = rules); shell = s
        s.show(width = 360.dp, height = 640.dp, fontScale = 2f)
        val card = syntheticCard("Synthetic keyword card", s.catalog.active?.id).copy(keywords = Knowledge.Known(SnapshotList.of("Flying")))
        rule.runOnIdle { s.controllers.search.select(card) }
        return s
    }
    @Test fun knownKeywordOpensActualLocalRulesSearchWithOriginalQueryAndNoImport() {
        val fixture = KeywordRulesFixture(); val s = show(fixture)
        rule.onNodeWithTag("card-keyword-0").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        s.awaitState { s.controllers.rules!!.state.value.let { it.query == "Flying" && !it.searchLoading } }
        rule.onNodeWithTag("shell-nav-rules").assertIsSelected()
        assertEquals("Flying", fixture.queries.single().text)
        assertEquals("Flying", s.controllers.rules!!.queryInput)
        assertEquals(1, s.controllers.rules!!.state.value.results.size); assertEquals(0, fixture.imports)
    }
    @Test fun missingInstalledCorpusRemainsNotInstalledWithoutInventedRuleOrImport() {
        val fixture = KeywordRulesFixture().also { it.installed = false }; val s = show(fixture)
        rule.onNodeWithTag("card-keyword-0").performScrollTo().performClick()
        s.awaitState { s.controllers.rules!!.state.value.searchProblem == Problem.NOT_INSTALLED }
        rule.onNodeWithTag("shell-nav-rules").assertIsSelected()
        assertTrue(s.controllers.rules!!.state.value.results.isEmpty()); assertEquals(0, fixture.imports)
    }
    @Test fun absentRulesCapabilityRetainsLiteralKeywordWithoutAnInertCommand() {
        val s = show(null)
        s.text("Flying").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("card-keyword-0").assertDoesNotExist()
        rule.onNodeWithTag("shell-nav-rules").assertDoesNotExist()
    }
}
