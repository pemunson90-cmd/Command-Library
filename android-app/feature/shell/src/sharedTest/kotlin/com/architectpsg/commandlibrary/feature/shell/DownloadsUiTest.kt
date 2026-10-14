package com.architectpsg.commandlibrary.feature.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.ui.DownloadTags as Tags
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadsUiTest {
    @get:Rule val rule = createComposeRule()
    private val downloads = DownloadsFixture()
    private val shell = ShellUiHarness(rule, downloads = downloads)
    @Before fun budget() = profileHostIdleBudget(300)
    @After fun close() { shell.close(); profileHostIdleBudget(60) }
    private fun click(tag: String) { rule.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun open() {
        rule.onNodeWithTag("shell-nav-catalog").performClick()
        click("catalog-downloads-open"); shell.awaitState { shell.controllers.downloads!!.state.value.ready && !shell.controllers.downloads!!.state.value.loading }
    }
    private fun plan() {
        rule.onNodeWithTag(Tags.LIST).performScrollToNode(hasTestTag(Tags.NEW)); rule.onNodeWithTag(Tags.NEW).performClick()
        shell.awaitState { shell.controllers.downloads!!.state.value.approval != null }
    }

    @Test fun nestedCatalogEntryAndNotNowLeaveOnlyAnUnapprovedPersistedPlan() {
        shell.show(width = 900.dp); assertFalse(shell.controllers.downloads!!.state.value.ready)
        open(); plan(); rule.onNodeWithTag(Tags.APPROVE).assertHeightIsAtLeast(48.dp)
        shell.text("https://api.scryfall.com/cards/search?q=lang%3Aen&unique=prints").assertExists()
        rule.onNodeWithTag(Tags.CLOSE_APPROVAL).performClick()
        assertTrue(downloads.settingsBase.stored.consents.isEmpty()); assertEquals(AcquisitionState.PLANNED, downloads.rows.values.single().state)
        assertTrue(shell.controllers.downloads!!.state.value.running.isEmpty())
        rule.onNodeWithTag(Tags.LIST).performScrollToNode(hasTestTag(Tags.BACK)); rule.onNodeWithTag(Tags.BACK).performClick()
        rule.onNodeWithTag("catalog-downloads-open").assertExists()
    }

    @Test fun explicitApprovalShowsTransferAndPauseResumeAndConfirmedCancelWorkThroughActualScreen() {
        shell.show(); open(); plan(); rule.onNodeWithTag(Tags.APPROVE).performClick()
        shell.awaitState { shell.controllers.downloads!!.state.value.selected?.state == AcquisitionState.RUNNING }
        click(Tags.PAUSE); shell.awaitState { shell.controllers.downloads!!.state.value.selected?.state == AcquisitionState.PAUSED }
        assertNull(shell.controllers.downloads!!.state.value.problem)
        click(Tags.START); shell.awaitState { shell.controllers.downloads!!.state.value.approval != null }
        rule.onNodeWithTag(Tags.APPROVE).performClick()
        shell.awaitState { shell.controllers.downloads!!.state.value.selected?.state == AcquisitionState.RUNNING }
        click(Tags.CANCEL); rule.onNodeWithTag(Tags.CONFIRM_CANCEL).assertHeightIsAtLeast(48.dp).performClick()
        shell.awaitState { shell.controllers.downloads!!.state.value.selected?.state == AcquisitionState.CANCELLED }
        assertFalse(downloads.catalog.initialized); assertNull(shell.controllers.downloads!!.state.value.installed)
    }

    @Test fun narrowDoubleFontDialogScrollAndNestedBackDoNotStartUnconfirmedWork() {
        shell.show(width = 360.dp, height = 640.dp, fontScale = 2f); open(); plan()
        shell.text(shell.string(R.string.downloads_mutable_source)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(Tags.APPROVE).assertHeightIsAtLeast(48.dp)
        fun back() { Class.forName("androidx.test.espresso.Espresso").getMethod("pressBack").invoke(null); rule.waitForIdle() }
        back(); assertTrue(downloads.settingsBase.stored.consents.isEmpty())
        val id = downloads.rows.keys.single()
        rule.onNodeWithTag(Tags.LIST).performScrollToNode(hasTestTag(Tags.row(id))); rule.onNodeWithTag(Tags.row(id)).performClick()
        shell.awaitState { shell.controllers.downloads!!.state.value.selectedId == id }
        back(); rule.onNodeWithTag(Tags.LIST).assertExists()
        back(); rule.onNodeWithTag("catalog-downloads-open").assertExists()
        assertEquals(AcquisitionState.PLANNED, downloads.rows.getValue(id).state)
    }
}
