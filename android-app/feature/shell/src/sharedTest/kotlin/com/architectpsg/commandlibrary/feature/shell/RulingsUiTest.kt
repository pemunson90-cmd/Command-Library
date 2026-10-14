package com.architectpsg.commandlibrary.feature.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.ui.DownloadTags
import com.architectpsg.commandlibrary.feature.shell.ui.RulingTags
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real Compose/controllers with synthetic ports; no wire/source/device authenticity assertion. */
@RunWith(AndroidJUnit4::class)
class RulingsUiTest {
    @get:Rule val rule = createComposeRule()
    private val rulings = RulingsFixture()
    private val shell = ShellUiHarness(rule, downloads = rulings.downloads, rulings = rulings)
    @Before fun budget() = profileHostIdleBudget(300)
    @After fun close() { shell.close(); profileHostIdleBudget(60) }
    private fun click(tag: String) { rule.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun openDownloads() {
        rule.onNodeWithTag("shell-nav-catalog").performClick(); click("catalog-downloads-open")
        shell.awaitState { shell.controllers.downloads!!.state.value.ready && !shell.controllers.downloads!!.state.value.loading }
    }
    @Test fun narrowLargeFontTwoStageApprovalsNeverInstallMetadataOrRunDismissedFile() {
        shell.show(width = 360.dp, height = 640.dp, fontScale = 2f); openDownloads()
        rule.onNodeWithTag(DownloadTags.LIST).performScrollToNode(hasTestTag(DownloadTags.RULINGS_NEW))
        rule.onNodeWithTag(DownloadTags.RULINGS_NEW).assertHeightIsAtLeast(48.dp).performClick()
        shell.awaitState { shell.controllers.downloads!!.state.value.approval != null }
        shell.text(shell.string(R.string.downloads_rulings_metadata_approve)).assertExists()
        shell.text(shell.string(R.string.rulings_language_unknown)).assertExists()
        rule.onNodeWithTag(DownloadTags.APPROVE).performClick()
        shell.awaitState { shell.controllers.downloads!!.state.value.selected?.state == AcquisitionState.RUNNING }
        rulings.downloads.runGate.complete(Unit)
        shell.awaitState { shell.controllers.downloads!!.state.value.selected?.state == AcquisitionState.COMPLETED }
        assertEquals(0, rulings.installs); rule.onNodeWithTag(DownloadTags.INSTALL).assertDoesNotExist()
        click(DownloadTags.RULINGS_REVIEW)
        shell.awaitState { shell.controllers.downloads!!.state.value.approval != null }
        shell.text(shell.string(R.string.downloads_rulings_file_approve)).assertExists()
        val file = shell.controllers.downloads!!.state.value.approval!!.plan.jobId
        rule.onNodeWithTag(DownloadTags.CLOSE_APPROVAL).performClick()
        assertEquals(1, rulings.downloads.settingsBase.stored.consents.size)
        assertEquals(AcquisitionState.PLANNED, rulings.downloads.rows.getValue(file).state)
        click(DownloadTags.BACK)
        rule.onNodeWithTag(DownloadTags.LIST).performScrollToNode(hasTestTag(DownloadTags.row(file)))
        rule.onNodeWithTag(DownloadTags.row(file)).performClick()
        shell.awaitState { shell.controllers.downloads!!.state.value.selected?.plan?.jobId == file }
        click(DownloadTags.START); shell.awaitState { shell.controllers.downloads!!.state.value.approval != null }
        rule.onNodeWithTag(DownloadTags.APPROVE).performClick()
        shell.awaitState { shell.controllers.downloads!!.state.value.rulingsInstalled != null }
        assertEquals(2, rulings.downloads.settingsBase.stored.consents.size); assertEquals(1, rulings.installs)
        assertFalse(rulings.downloads.catalog.initialized)
    }
    private fun selectCard() {
        val card = syntheticCard("Synthetic ruling card", shell.catalog.active?.id).copy(oracleId = RULINGS_UI_ORACLE)
        rule.runOnIdle { shell.controllers.search.select(card) }
        shell.awaitState { shell.controllers.rulings!!.state.value.let { it.oracle == RULINGS_UI_ORACLE && it.status != null && !it.loading } }
    }
    @Test fun localCardPageRetainsLiteralDayPublisherAndCommentAndReplacesNextPageWithoutDownloads() {
        rulings.contents = List(11) { rulings.contents.single().copy(comment = "  Literal café 😀 $it \n") }; rulings.installed()
        shell.show(width = 360.dp, height = 640.dp, fontScale = 2f); selectCard()
        val first = rulings.contents.first().comment
        shell.text(first).performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("2026-10-14").assertCountEquals(10)
        rule.onAllNodesWithText("wotc").assertCountEquals(10)
        click(RulingTags.NEXT)
        shell.waitForText(rulings.contents.last().comment); shell.text(first).assertDoesNotExist()
        assertEquals(1, shell.controllers.rulings!!.state.value.page!!.items.size)
        assertEquals(0, rulings.metadataAdmissions); assertEquals(0, rulings.installs); assertTrue(rulings.downloads.rows.isEmpty())
    }
    @Test fun missingRulingsNavigateToActualCatalogManagementWithoutGrantingConsent() {
        shell.show(); selectCard()
        shell.text(shell.string(R.string.rulings_not_installed)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(RulingTags.MANAGE).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag("catalog-downloads-open").assertExists()
        assertTrue(rulings.downloads.settingsBase.stored.consents.isEmpty()); assertTrue(rulings.downloads.rows.isEmpty())
    }
}
