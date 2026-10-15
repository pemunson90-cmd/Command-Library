package com.architectpsg.commandlibrary

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R as ShellR
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Actual activity, durable actor, AppGraph and Room; host UI evidence, not native/device acceptance. */
@RunWith(AndroidJUnit4::class)
@Config(shadows=[RealHostDirectoryLinux::class,RealHostBlockGuardOs::class])
class DeckDraftAppGraphUiIntegrationTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val cancel=object:CancelToken {override val isCancelled=false;override fun throwIfCancelled()=Unit}
    private fun <T> ok(value:Outcome<T>):T {assertTrue(value.toString(),value is Outcome.Success);return(value as Outcome.Success).value}
    @Before fun hostBudget()=budget(300)
    @After fun resetBudget()=budget(60)
    private fun budget(seconds:Long) {
        Class.forName("androidx.test.espresso.IdlingPolicies")
            .getMethod("setMasterPolicyTimeout",Long::class.javaPrimitiveType,TimeUnit::class.java)
            .invoke(null,seconds,TimeUnit.SECONDS)
    }
    private fun await(check:()->Boolean)=rule.waitUntil(20_000) {rule.waitForIdle();check()}
    private fun awaitEnabled(tag:String) = await {
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) }
    }
    private fun replace(tag:String,value:String) {
        awaitEnabled(tag)
        rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    }
    private fun overviewClick(tag:String) {
        rule.onNodeWithTag("deck-library-overview").performScrollToNode(hasTestTag(tag))
        rule.onNodeWithTag(tag).performClick()
    }
    @Test fun actualUiAutosavesTextRetainsInvalidInputAcrossNavigationThenConfirmsAndUndoesOneGroup() {
        await {rule.onAllNodesWithTag("shell-nav-decks").fetchSemanticsNodes().isNotEmpty()}
        val ports=runBlocking {ok((rule.activity.application as CommandLibraryApplication).graph.ports())}
        val library=checkNotNull(ports.localDecks);val drafts=checkNotNull(ports.localDeckDrafts)
        val name="Actual draft ${UUID.randomUUID()}"
        rule.onNodeWithTag("shell-nav-decks").performClick()
        rule.onNodeWithTag("deck-library-new").performClick()
        rule.onNodeWithTag("deck-library-name").performTextReplacement(name)
        rule.onNodeWithTag("deck-library-confirm").performClick()
        awaitEnabled("deck-edit-details")
        val id=runBlocking {ok(library.list(PageRequest(20,null),cancel)).items.single {it.name==name}.id}
        overviewClick("deck-edit-details")
        replace("deck-editor-description","Text saved through actual UI")
        await {runBlocking {ok(library.load(id,cancel)).description=="Text saved through actual UI"}}
        val before=runBlocking {ok(library.load(id,cancel))}
        replace("deck-editor-name","")
        await {runBlocking {(ok(drafts.load(id,cancel)).draft?.body as? DeckDraftBody.Details)?.name==""}}
        assertEquals(before,runBlocking {ok(library.load(id,cancel))})
        rule.onNodeWithText(rule.activity.getString(ShellR.string.deck_draft_close)).performClick()
        await {rule.onAllNodesWithTag("deck-editor-name").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithTag("shell-nav-cards").performClick()
        rule.onNodeWithTag("deck-draft-resume").performClick()
        awaitEnabled("deck-editor-name")
        assertEquals("",rule.onNodeWithTag("deck-editor-name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        val corrected="$name corrected"
        replace("deck-editor-name",corrected)
        awaitEnabled("deck-editor-save")
        rule.onNodeWithTag("deck-editor-save").performClick()
        await {runBlocking {ok(library.load(id,cancel)).name==corrected && ok(drafts.load(id,cancel)).draft==null}}
        val saved=runBlocking {ok(library.load(id,cancel))}
        assertEquals(before.revision.value+1,saved.revision.value)
        rule.onNodeWithText(rule.activity.getString(ShellR.string.deck_draft_close)).performClick()
        await {rule.onAllNodesWithTag("deck-editor-name").fetchSemanticsNodes().isEmpty()}
        overviewClick("deck-undo")
        await {runBlocking {ok(library.load(id,cancel)).revision.value==saved.revision.value+1}}
        val undone=runBlocking {ok(library.load(id,cancel))}
        assertEquals(before.copy(revision=undone.revision,modifiedAt=undone.modifiedAt),undone)
        // Reopen the clean hidden actor against undo's real successor head, then mutate once.
        awaitEnabled("deck-edit-details")
        overviewClick("deck-edit-details")
        awaitEnabled("deck-editor-name")
        assertEquals(undone.name,rule.onNodeWithTag("deck-editor-name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        replace("deck-editor-description","Successor after undo")
        await {runBlocking {ok(library.load(id,cancel)).description=="Successor after undo"}}
        val successor=runBlocking {ok(library.load(id,cancel))}
        assertEquals(undone.revision.value+1,successor.revision.value)
        assertEquals(undone.name,successor.name)
        assertTrue(runBlocking {ok(ports.settings.read(cancel)).consents.isEmpty()})
    }
}
