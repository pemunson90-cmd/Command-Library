package com.architectpsg.commandlibrary.feature.shell.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.ui.collection.DeckMarkOwnedSurface
import com.architectpsg.commandlibrary.feature.shell.ui.collection.markOwnedConfirmAction
import com.architectpsg.commandlibrary.feature.shell.ui.collection.markOwnedFinishAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Host Compose and the actual controller over its accepted faithful port fixture; no SQL claim. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DeckMarkOwnedUiTest {
    @get:Rule val rule = createComposeRule()
    private val scope = TestScope()
    private val f = MarkOwnedFixture(scope)

    @After fun close() { scope.cancel(); scope.testScheduler.runCurrent() }
    private fun show() {
        rule.setContent { MaterialTheme { DeckMarkOwnedSurface(f.controller, f.gate.busy) } }
        rule.waitForIdle()
    }
    private fun pump() { rule.runOnIdle { scope.runCurrent() }; rule.waitForIdle() }
    private fun reveal(tag: String) {
        rule.onNodeWithTag("mark-owned-list").performScrollToNode(hasTestTag(tag))
        rule.waitForIdle()
    }
    private fun click(tag: String, inList: Boolean = false) {
        if (inList) reveal(tag)
        rule.onNodeWithTag(tag).assertIsEnabled().performClick()
        pump()
    }
    private fun type(tag: String, text: String) {
        reveal(tag)
        rule.onNodeWithTag(tag).assertIsEnabled().performTextReplacement(text)
        pump()
    }
    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun additionalUnknownPhysicalInputRequiresReviewedCurrentPreviewAndExactlyOneConfirmation() {
        f.open(); show()
        reveal("mark-owned-select-a"); rule.onNodeWithTag("mark-owned-select-a").assertIsOff()
        reveal("mark-owned-select-b"); rule.onNodeWithTag("mark-owned-select-b").assertIsOff()
        click("mark-owned-select-b", true)
        reveal("mark-owned-quantity-b")
        // Capture a real old text callback to model a field event delivered after another field.
        val oldQuantity = checkNotNull(rule.onNodeWithTag("mark-owned-quantity-b").fetchSemanticsNode().config[SemanticsActions.SetText].action)
        type("mark-owned-notes-b", "Physical copy note")
        type("mark-owned-location-b", "Trade binder")
        rule.runOnIdle { oldQuantity(AnnotatedString("003")) }; pump()
        assertEquals("003", f.row("b").input.quantity)
        assertEquals("Physical copy note", f.row("b").input.notes)
        assertEquals("Trade binder", f.row("b").input.location)
        assertTrue(f.row("b").input.finish is Knowledge.Unknown)
        assertEquals(CardCondition.UNKNOWN, f.row("b").input.condition)
        assertTrue(f.calls.isEmpty()); assertEquals(0, f.ids)

        click("mark-owned-preview")
        val first = checkNotNull(f.controller.state.value.preview)
        rule.onNodeWithTag("mark-owned-confirm").assertIsEnabled()
        // Material's semantics OnClick wraps a node and cannot run after detachment.
        // Capture the same rendered business callback the actual button receives.
        val oldConfirm = markOwnedConfirmAction(f.controller, f.controller.state.value.formTicket, first, f.gate.busy)
        assertTrue(f.calls.isEmpty())
        type("mark-owned-notes-b", "Latest physical copy note")
        assertNull(f.controller.state.value.preview)
        click("mark-owned-preview")
        val current = checkNotNull(f.controller.state.value.preview)
        assertNotSame(first, current)
        rule.runOnIdle { oldConfirm() }; pump()
        assertTrue(f.calls.isEmpty()) // An old button cannot authorize an unseen new preview.

        val lot = (current.patch.edits.single() as CollectionEdit.Add).lot
        assertEquals(f.identity, lot.identity)
        assertEquals(3, lot.quantity)
        assertTrue(lot.finish is Knowledge.Unknown)
        assertEquals(CardCondition.UNKNOWN, lot.condition)
        assertEquals("Trade binder", lot.location)
        assertEquals("Latest physical copy note", lot.notes)
        assertNotEquals(f.head.entries.last().notes, lot.notes)
        assertEquals(Revision(0), lot.revision)
        click("mark-owned-confirm")
        assertEquals(current.patch, f.calls.single().patch)
        assertEquals(current.inventory.revision, f.calls.single().expected)
        assertEquals(1, f.groups)
        assertEquals(f.stored, f.notices.single())
        assertNull(f.controller.state.value.deckId)
        rule.onNodeWithTag("mark-owned-resume").assertDoesNotExist()
    }

    @Test fun knownFinishChoicesRequireExplicitSupportedLookupAndRetirementPreservesRawChoice() {
        f.open(); show(); click("mark-owned-select-b", true)
        reveal("mark-owned-finish-UNKNOWN-b")
        rule.onNodeWithTag("mark-owned-finish-UNKNOWN-b").assertIsSelected()
        rule.onNodeWithTag("mark-owned-finish-FOIL-b").assertDoesNotExist()
        assertTrue(f.catalogCalls.isEmpty())
        click("mark-owned-finish-load-b", true)
        assertEquals(listOf((f.identity.printingId as Knowledge.Known).value), f.catalogCalls)
        reveal("mark-owned-finish-FOIL-b")
        rule.onNodeWithTag("mark-owned-finish-NONFOIL-b").assertExists()
        rule.onNodeWithTag("mark-owned-finish-ETCHED-b").assertDoesNotExist()
        click("mark-owned-finish-FOIL-b")
        assertEquals(Knowledge.Known(Finish.FOIL), f.row("b").input.finish)
        reveal("mark-owned-finish-FOIL-b")
        val oldFoil = markOwnedFinishAction(f.controller, f.controller.state.value.formTicket, f.row("b"), Finish.FOIL)
        // Controlled catalog notification, not an automatic UI downgrade or network refresh.
        rule.runOnIdle { f.active = null; f.controller.onActiveGeneration(null) }; pump()
        reveal("mark-owned-finish-UNKNOWN-b")
        rule.onNodeWithTag("mark-owned-finish-FOIL-b").assertDoesNotExist()
        assertEquals(Knowledge.Known(Finish.FOIL), f.row("b").input.finish)
        click("mark-owned-finish-UNKNOWN-b")
        rule.runOnIdle { oldFoil() }; pump()
        assertTrue(f.row("b").input.finish is Knowledge.Unknown)
        assertEquals(1, f.catalogCalls.size)
        assertTrue(f.calls.isEmpty()); assertEquals(0, f.ids)
    }

    @Test fun uncertainRequestCanHideResumeWithoutIoAndRetryExactDespiteOwnedBusyGate() {
        f.open(); f.afterUnknown = true; show()
        click("mark-owned-select-b", true)
        type("mark-owned-quantity-b", "2")
        click("mark-owned-preview"); click("mark-owned-confirm")
        assertTrue(f.controller.state.value.uncertain)
        assertTrue(f.gate.busy.value)
        val exact = f.calls.single()
        val rows = f.controller.state.value.rows
        val ids = f.ids
        val reads = f.snapshotCalls
        rule.onNodeWithTag("mark-owned-retry").assertIsEnabled()
        reveal("mark-owned-refresh"); rule.onNodeWithTag("mark-owned-refresh").assertIsNotEnabled()
        rule.onNodeWithTag("mark-owned-discard").assertIsNotEnabled()
        click("mark-owned-close")
        rule.onNodeWithTag("mark-owned-resume").assertIsEnabled()
        assertNull(f.gate.tryAcquire())
        click("mark-owned-resume")
        assertEquals(rows, f.controller.state.value.rows)
        assertEquals(reads, f.snapshotCalls)
        assertEquals(ids, f.ids)
        assertEquals(listOf(exact), f.calls)
        click("mark-owned-retry")
        assertEquals(listOf(exact, exact), f.calls)
        assertEquals(1, f.groups)
        assertEquals(ids, f.ids)
        assertFalse(f.gate.busy.value)
        assertNull(f.controller.state.value.deckId)
        rule.onNodeWithTag("mark-owned-resume").assertDoesNotExist()
    }

    @Test fun refreshedChangedEntryKeepsRawAndStaleClearWarningCannotRebindOrEraseNewerEntry() {
        f.open(); show(); click("mark-owned-select-b", true)
        type("mark-owned-notes-b", "Retained physical note")
        reveal("mark-owned-quantity-b")
        val oldQuantity = checkNotNull(rule.onNodeWithTag("mark-owned-quantity-b").fetchSemanticsNode().config[SemanticsActions.SetText].action)
        val original = f.row("b")
        val other = f.row("a")
        // Fixture source change; the UI still has to request its explicit read-only refresh.
        rule.runOnIdle {
            f.head = f.head.copy(revision = Revision(2), entries = SnapshotList.copyOf(f.head.entries.map {
                if (it.id == original.entry.id) it.copy(identity = it.identity.copy(language = LanguageTag("ja"))) else it
            }))
            f.controller.onDeckChanged(f.head.id, f.head.revision)
        }; pump()
        val ids = f.ids
        click("mark-owned-refresh", true)
        assertEquals(original.input, f.row("b").input)
        assertEquals(original.entry.identity, f.row("b").entry.identity)
        assertNotNull(f.row("b").sourceProblem)
        click("mark-owned-use-current-b", true)
        // A later source refresh races with the already displayed warning.
        rule.runOnIdle {
            f.head = f.head.copy(revision = Revision(3), entries = SnapshotList.copyOf(f.head.entries.map {
                if (it.id == original.entry.id) it.copy(identity = it.identity.copy(language = LanguageTag("de"))) else it
            }))
            f.controller.refreshSources()
        }; pump()
        rule.onNodeWithTag("mark-owned-clear-confirm").assertIsNotEnabled().performClick(); pump()
        assertEquals(original.input, f.row("b").input)
        assertEquals(original.entry.identity, f.row("b").entry.identity)
        click("mark-owned-clear-cancel")
        click("mark-owned-use-current-b", true)
        click("mark-owned-clear-confirm")
        assertEquals(LanguageTag("de"), f.row("b").entry.identity.language)
        assertNull(f.row("b").sourceProblem)
        assertFalse(f.row("b").selected)
        assertEquals("", f.row("b").input.notes)
        assertTrue(f.row("b").input.finish is Knowledge.Unknown)
        val accepted = f.row("b").input
        // Old-identity callbacks cannot modify the replacement, even with equal default quantity.
        assertEquals(original.input.quantity, accepted.quantity)
        rule.runOnIdle { oldQuantity(AnnotatedString("9")) }; pump()
        assertEquals(accepted, f.row("b").input)
        assertEquals(other.input, f.row("a").input)
        assertTrue(f.calls.isEmpty()); assertEquals(ids, f.ids)
    }

    @Test fun competingWriterExplainedInsideModalAndUnavailableEstimateStaysBlankUntilExplicitInput() {
        // Fixture allocation refusal and another lane's real gate grant are controlled setup.
        f.allocationFailure = Outcome.Unknown("Synthetic estimate unavailable", SnapshotList.empty())
        f.open()
        val owner = checkNotNull(f.gate.tryAcquire())
        try {
            show()
            rule.onNodeWithText(string(R.string.mark_owned_unavailable)).assertExists()
            reveal("mark-owned-busy"); rule.onNodeWithTag("mark-owned-busy").assertIsDisplayed()
            assertNull(f.row("b").estimate)
            assertEquals("", f.row("b").input.quantity)
            click("mark-owned-select-b", true)
            type("mark-owned-quantity-b", "1")
            rule.onNodeWithTag("mark-owned-preview").assertIsNotEnabled().performClick(); pump()
            assertTrue(f.calls.isEmpty()); assertEquals(0, f.ids)
            rule.runOnIdle { owner.release() }; pump()
            rule.onNodeWithTag("mark-owned-preview").assertIsEnabled()
            click("mark-owned-discard", true)
            assertEquals("1", f.row("b").input.quantity)
            click("mark-owned-discard-confirm")
            assertNull(f.controller.state.value.deckId)
            rule.onNodeWithTag("mark-owned-resume").assertDoesNotExist()
            assertTrue(f.calls.isEmpty()); assertEquals(0, f.ids)
        } finally { owner.release() }
    }
}
