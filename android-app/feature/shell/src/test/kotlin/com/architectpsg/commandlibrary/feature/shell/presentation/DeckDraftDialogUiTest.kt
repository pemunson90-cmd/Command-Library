package com.architectpsg.commandlibrary.feature.shell.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.ui.decks.DraftExitDialog
import com.architectpsg.commandlibrary.feature.shell.ui.decks.DurableDeckEditorDialog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Host Compose over the real retained actor and its faithful stateful test port; no SQL evidence. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DeckDraftDialogUiTest {
    @get:Rule val rule = createComposeRule()
    private val scope = TestScope()
    private val f = ActorFixture(scope)

    @After fun close() {
        scope.cancel()
        scope.testScheduler.runCurrent()
    }

    private fun show() {
        rule.setContent {
            MaterialTheme {
                DurableDeckEditorDialog(f.actor, null)
                DraftExitDialog(f.actor)
            }
        }
        rule.waitForIdle()
    }

    /** UI callbacks queue actor work on this owned scheduler; Compose owns its normal Main. */
    private fun pump() {
        rule.runOnIdle { f.pump() }
        rule.waitForIdle()
    }

    private fun click(tag: String) {
        rule.onNodeWithTag(tag).assertIsEnabled().performClick()
        pump()
    }

    private fun assertNoMutations() {
        assertTrue(f.writes.isEmpty())
        assertTrue(f.prepares.isEmpty())
        assertTrue(f.commits.isEmpty())
        assertTrue(f.discards.isEmpty())
        assertEquals(0, f.ids)
    }

    @Test fun bodyNullReadFailureHasDistinctRetryThatRecoversExactRawWithoutMutation() {
        // Fixture setup models a previously saved, even invalid, raw value and a failed read.
        val raw = DeckDraftBody.Details(Revision(0), "Saved raw", "Unpaired \uD800", "")
        f.raw(raw)
        val saved = f.slot.draft
        f.lose.add("load")
        f.open()
        val session = f.actor.state.value.session
        assertNull(f.actor.state.value.body)
        assertNotNull(f.actor.state.value.rawProblem)
        show()

        rule.onNodeWithTag("deck-editor-save").assertIsNotEnabled()
        click("deck-draft-retry")

        assertEquals(session, f.actor.state.value.session)
        assertEquals(raw, f.actor.state.value.body)
        assertEquals(saved, f.slot.draft)
        assertNull(f.actor.state.value.rawProblem)
        rule.onNodeWithTag("deck-editor-name").assertTextContains(raw.name)
        assertNoMutations()
    }

    @Test fun exitRetryKeepsItsTicketAndActionUntilRecoveredRawHasActuallyFlushed() {
        f.raw(DeckDraftBody.Details(Revision(0), "Flush before close", "", ""))
        val saved = checkNotNull(f.slot.draft)
        val originalHead = f.head
        f.lose.add("load")
        f.open()
        val session = f.actor.state.value.session
        // Gate the faithful port's real commit response, rather than replacing the editor.
        val commitGate = f.gate("commit.before")
        show()
        val closeText = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.deck_draft_close)
        rule.onNodeWithText(closeText).performClick()
        pump()
        val exit = checkNotNull(f.actor.state.value.exit)
        assertEquals(DeferredDeckAction.CloseEditor, exit.action)
        assertEquals(DraftExitPhase.NEEDS_CHOICE, exit.phase)
        assertNull(f.actor.takeReadyExit(exit.ticket))

        click("deck-draft-exit-retry")

        val waiting = checkNotNull(f.actor.state.value.exit)
        assertEquals(exit.ticket, waiting.ticket)
        assertEquals(exit.action, waiting.action)
        assertEquals(session, f.actor.state.value.session)
        // A failed-read exit can retain its NEEDS_CHOICE display phase during the retry.
        // Actual COMMITTING work and the unreleasable ticket establish the flush barrier.
        assertEquals(DraftPhase.COMMITTING, f.actor.state.value.phase)
        assertNull(f.actor.takeReadyExit(exit.ticket))
        assertEquals(originalHead, f.head)
        assertEquals(1, f.prepares.size)
        assertEquals(1, f.commits.size)
        val prepared = f.prepares.single()
        val intent = (f.slot.latestPreparation as DeckDraftPreparation.Pending).intent
        assertEquals(saved.body, prepared.body)
        assertEquals(saved.token, prepared.token)
        assertEquals(DeckDraftCommitScope.TEXT_AUTOSAVE, prepared.scope)
        assertEquals(intent.patch, prepared.patch)
        assertEquals(intent.operationId, prepared.operation)
        assertEquals(listOf(intent.operationId to intent.requestSha256), f.commits)
        assertEquals(saved, f.slot.draft)
        assertTrue(f.writes.isEmpty())
        assertTrue(f.discards.isEmpty())

        rule.runOnIdle { commitGate.complete(Unit) }
        pump()
        assertEquals("Flush before close", f.head.name)
        assertEquals(DraftExitPhase.READY, f.actor.state.value.exit!!.phase)
        assertEquals(exit.action, f.actor.takeReadyExit(exit.ticket))
        assertNull(f.actor.takeReadyExit(exit.ticket))
        assertFalse(f.actor.state.value.visible)
        assertEquals(1, f.prepares.size)
        assertEquals(1, f.commits.size)
    }

    @Test fun preparedDetailsRetryOnlyReadsThenExplicitSaveSubmitsOriginalImmutableIntentOnce() {
        val raw = DeckDraftBody.Details(Revision(0), "Explicit recovered", "Exact description", "")
        val intent = f.prepared(raw)
        val saved = f.slot.draft
        f.open()
        show()

        click("deck-draft-retry")

        assertEquals(raw, f.actor.state.value.body)
        assertEquals(saved, f.slot.draft)
        assertEquals(intent, f.slot.latestPreparation!!.intent)
        assertNoMutations()

        click("deck-editor-save")

        assertEquals(listOf(intent.operationId to intent.requestSha256), f.commits)
        assertTrue(f.prepares.isEmpty())
        assertTrue(f.writes.isEmpty())
        assertTrue(f.discards.isEmpty())
        assertEquals(0, f.ids)
        assertEquals(raw.name, f.head.name)
        assertEquals(raw.description, f.head.description)
        assertEquals(Revision(1), f.head.revision)
        assertNull(f.slot.draft)
        assertEquals(intent, (f.slot.latestPreparation as DeckDraftPreparation.Committed).intent)
        assertEquals(intent.sourceToken, f.acks.single().sourceToken)
        assertEquals(listOf(f.head.id to f.head.revision), f.notices)
        pump()
        assertEquals(1, f.commits.size)
    }

    @Test fun explicitPreparedConfirmationCannotInventMissingHistoricalSource() {
        val raw = DeckDraftBody.Details(Revision(0), "Pinned older input", "", "")
        val intent = f.prepared(raw)
        // The fixture exposes current head only; no historical snapshot is available to the actor.
        f.head = f.head.copy(revision = Revision(1), name = "A later head")
        val head = f.head
        val slot = f.slot
        f.open()
        show()
        click("deck-draft-retry")
        assertNull(f.actor.state.value.base)

        rule.onNodeWithTag("deck-editor-save").assertIsNotEnabled().performClick()
        pump()

        assertEquals(head, f.head)
        assertEquals(slot, f.slot)
        assertEquals(raw, f.actor.state.value.body)
        assertEquals(intent, f.slot.latestPreparation!!.intent)
        assertTrue(f.actor.state.value.conflict)
        assertNoMutations()
    }

    @Test fun recoveredPreparedAddWithoutFreshProofPreservesPinnedSelectionAndRefusesSave() {
        val selection = DeckDraftSelection(f.cardIdentity, GenerationId("active"))
        val raw = DeckDraftBody.Add(Revision(0), "2", "Pinned notes", BoardRole.MAIN,
            Knowledge.Absent("Exact finish absence"), SnapshotList.empty(), selection)
        val intent = f.prepared(raw, added = EntityId("already-reserved-entry"))
        val slot = f.slot
        // No current-card proof is provided: persisted selection alone is not fresh authority.
        f.open(DeckEditorTarget.Add)
        show()
        click("deck-draft-retry")
        assertNoMutations()

        click("deck-editor-save")

        assertEquals(slot, f.slot)
        assertEquals(raw, f.actor.state.value.body)
        assertEquals(selection, (f.actor.state.value.body as DeckDraftBody.Add).selection)
        assertEquals(Knowledge.Absent("Original absence reason"), selection.identity.printingId)
        assertEquals(intent, f.slot.latestPreparation!!.intent)
        assertEquals(Problem.STALE_GENERATION, f.actor.state.value.contentProblem)
        assertTrue(f.head.entries.isEmpty())
        assertNoMutations()
    }
}
