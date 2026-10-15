package com.architectpsg.commandlibrary.feature.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real shell/presentation over labeled synthetic local ports; no wire/native/physical claim. */
@RunWith(AndroidJUnit4::class)
class PrintingSelectionUiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var shell: ShellUiHarness
    private var secondPage = false
    private val port = object : CatalogPrintingLookup {
        override suspend fun printing(id: CardId, cancel: CancelToken): Outcome<PrintingRecord> = error("page only")
        override suspend fun printings(id: OracleId, page: PageRequest, cancel: CancelToken): Outcome<Page<PrintingRecord>> {
            cancel.throwIfCancelled()
            val generation = shell.catalog.active!!.id
            secondPage = page.cursor != null
            val printing = PrintingRecord(CardId(id.source, if (secondPage) "second" else "first"), Knowledge.Known(id),
                "tst", Knowledge.Known("Synthetic set"), if (secondPage) "2" else "1", LanguageTag.ENGLISH,
                Knowledge.Known("common"), Knowledge.Known(SnapshotList.of(Finish.NONFOIL)), Knowledge.Absent("No art"),
                SnapshotList.empty(), SnapshotList.empty(), SnapshotList.empty(), SnapshotList.empty(), SnapshotList.empty(), syntheticProvenance(generation))
            return Outcome.Success(Page(SnapshotList.of(printing), if (secondPage) null else "second-page",
                SnapshotReference(id.source, generation, null, LanguageTag.ENGLISH)))
        }
    }
    private val links = object : CardLinks {
        override fun template(pattern: String, values: LinkTokenValues): Outcome<ResourceUri> = error("No template")
        override fun resolve(card: CardRecord, printing: PrintingRecord?, preferences: LinkPreferences): Outcome<SnapshotList<ResolvedLink>> {
            val target = preferences.destinations.single()
            if (target.id.value != "scryfall") return Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Synthetic missing supplied link")
            return Outcome.Success(SnapshotList.of(ResolvedLink(target, ResourceUri("https://scryfall.com/card/tst/1/synthetic"),
                LinkResolutionKind.SUPPLIED, printing!!.id, printing.provenance, Knowledge.Absent("No warning"))))
        }
    }
    @Before fun setup() {
        profileHostIdleBudget(300)
        shell = ShellUiHarness(rule, printings = port, cardLinks = links)
    }
    @After fun close() { shell.close(); profileHostIdleBudget(60) }
    @Test fun actualCardSelectionShowsPrintingIdentityAndNextPageDropsOldSelection() {
        shell.show(width = 360.dp, height = 640.dp, fontScale = 2f)
        val card = syntheticCard("Synthetic Existing Card", shell.catalog.active!!.id)
        rule.runOnIdle { shell.controllers.search.select(card) }
        shell.awaitState { shell.controllers.printings!!.state.value.page != null }
        rule.onNodeWithTag("printing-first").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        shell.awaitState { shell.controllers.printings!!.state.value.let { it.selected != null && !it.linksLoading } }
        shell.text(shell.string(R.string.printings_link_source)).performScrollTo().assertIsDisplayed()
        shell.text(shell.string(R.string.printings_link_retrieved)).performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText(shell.string(R.string.printings_link_missing)).assertCountEquals(7)
        rule.onNodeWithTag("printing-link-scryfall").performScrollTo().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("printing-link-gatherer").assertDoesNotExist()
        shell.text(shell.string(R.string.printings_qualified_id, card.oracleId.source.value, "first")).performScrollTo().assertIsDisplayed()
        shell.text(shell.string(R.string.printings_nonfoil)).performScrollTo().assertIsDisplayed()
        val selectedTicket = shell.controllers.printings!!.state.value.ticket
        shell.resize(900.dp, 800.dp, fontScale = 1f)
        shell.awaitState { shell.controllers.printings!!.state.value.selected?.id?.raw == "first" }
        assertEquals(selectedTicket, shell.controllers.printings!!.state.value.ticket)
        rule.onNodeWithTag("printings-next").performScrollTo().performClick()
        shell.awaitState { secondPage && shell.controllers.printings!!.state.value.page?.items?.singleOrNull()?.id?.raw == "second" }
        rule.onNodeWithTag("printing-first").assertDoesNotExist()
        assertNull(shell.controllers.printings!!.state.value.selected)
        rule.onNodeWithTag("printing-second").performScrollTo().performClick()
        shell.awaitState { shell.controllers.printings!!.state.value.selected?.id?.raw == "second" }
        rule.onNodeWithTag("shell-nav-settings").performClick()
        shell.awaitState { shell.controllers.printings!!.state.value.selected == null }
        assertTrue(shell.settings.stored.consents.isEmpty())
    }
}
