package com.architectpsg.commandlibrary.feature.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.ui.decks.DeckLibraryTags
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real Compose/controllers over explicitly synthetic edit/library ports; not SQL/native evidence. */
@RunWith(AndroidJUnit4::class)
class DeckEditorUiTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var shell:ShellUiHarness
    private val initial=DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE,"edit-ui"),"Synthetic deck",Knowledge.Absent("none"),"description",
        Knowledge.Absent("none"),SnapshotList.empty(),UtcTimestamp(1),UtcTimestamp(1),Revision(0),SnapshotList.empty(),Knowledge.Absent("none"),null,SnapshotList.empty(),SnapshotList.empty())
    private val rows=ConcurrentHashMap<DeckId,DeckSnapshot>().apply { put(initial.id,initial) }
    private val receipts=ConcurrentHashMap<EntityId,DeckSnapshot>()
    private val attempts=CopyOnWriteArrayList<List<Any>>()
    @Volatile private var uncertain=false
    private val port=object:LocalDeckLibrary,LocalDeckEdits {
        override suspend fun create(initial:DeckSnapshot,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> = error("Not part of synthetic editor scenario")
        override suspend fun duplicate(id:DeckId,expected:Revision,name:String,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> = error("Not part of synthetic editor scenario")
        override suspend fun load(id:DeckId,cancel:CancelToken):Outcome<DeckSnapshot> = Outcome.Success(rows.getValue(id))
        override suspend fun list(page:PageRequest,cancel:CancelToken):Outcome<Page<DeckSummary>> = Outcome.Success(Page(SnapshotList.copyOf(rows.values.map {
            DeckSummary(it.id,it.name,it.revision,it.createdAt,it.modifiedAt,it.entries.size,it.entries.sumOf { e->e.quantity.toLong() })
        }),null,SnapshotReference(LocalDeckLibraryLimits.SOURCE,null,Revision(0),null)))
        override suspend fun apply(id:DeckId,patch:DeckPatch,expected:Revision,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> {
            attempts+=listOf(id,patch,expected,operationId)
            val saved=receipts.getOrPut(operationId) {
                var value=rows.getValue(id)
                for(edit in patch.edits) value=when(edit) {
                    is DeckEdit.Rename->value.copy(name=edit.name)
                    is DeckEdit.Description->value.copy(description=edit.text)
                    is DeckEdit.Primer->value.copy(primer=edit.markdown)
                    is DeckEdit.Add->value.copy(entries=SnapshotList.copyOf(value.entries+edit.entry))
                    else->error("Synthetic fixture unsupported patch")
                }
                value.copy(revision=Revision(expected.value+1),modifiedAt=UtcTimestamp(7)).also { rows[id]=it }
            }
            return if(uncertain) Outcome.Failure(DomainFailure(FailureCode.PERSISTENCE,"Synthetic lost acknowledgement",SnapshotList.empty())) else Outcome.Success(saved)
        }
        override suspend fun undo(id:DeckId,expected:Revision,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> = Outcome.Success(
            receipts.getOrPut(operationId){initial.copy(revision=Revision(expected.value+1),modifiedAt=UtcTimestamp(8)).also{rows[id]=it}})
    }
    @Before fun setup(){profileHostIdleBudget(300);shell=ShellUiHarness(rule,decks=port,deckEdits=port)}
    @After fun close(){shell.close();profileHostIdleBudget(60)}
    private fun open(){
        shell.show(400.dp,800.dp)
        rule.onNodeWithTag("shell-nav-decks").performClick()
        shell.awaitState{!shell.controllers.deckLibrary!!.state.value.loading}
        rule.onNodeWithTag("deck-open-${initial.id.raw}").performScrollTo().performClick()
        shell.awaitState{shell.controllers.deckLibrary!!.state.value.selected!=null}
    }
    @Test fun detailsDraftSurvivesResizeThenExplicitSaveAndUndoRestoreOriginalContent(){
        open()
        rule.onNodeWithTag(DeckLibraryTags.OVERVIEW).performScrollToNode(hasTestTag("deck-edit-details"))
        rule.onNodeWithTag("deck-edit-details").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag("deck-editor-name").performTextReplacement("Edited synthetic")
        assertTrue(attempts.isEmpty());assertEquals(initial,rows[initial.id])
        shell.resize(900.dp,800.dp,2f)
        rule.onNodeWithTag("deck-editor-name").assertTextContains("Edited synthetic")
        rule.onNodeWithTag("deck-editor-save").assertHeightIsAtLeast(48.dp).performClick()
        shell.awaitState{shell.controllers.deckLibrary!!.state.value.editor==null && !shell.controllers.deckLibrary!!.state.value.mutationBusy}
        assertEquals("Edited synthetic",rows.getValue(initial.id).name);assertEquals(1,attempts.size)
        rule.onNodeWithTag(DeckLibraryTags.OVERVIEW).performScrollToNode(hasTestTag("deck-undo"))
        rule.onNodeWithTag("deck-undo").performClick()
        shell.awaitState{shell.controllers.deckLibrary!!.state.value.selected?.revision==Revision(2)}
        assertEquals(initial.name,rows.getValue(initial.id).name);assertEquals(initial.description,rows.getValue(initial.id).description)
    }
    @Test fun pendingModalCanCloseWithoutLosingExactRetryOrTrappingUser(){
        open();rule.onNodeWithTag(DeckLibraryTags.OVERVIEW).performScrollToNode(hasTestTag("deck-edit-details"))
        rule.onNodeWithTag("deck-edit-details").performClick()
        rule.onNodeWithTag("deck-editor-name").performTextReplacement("Committed once")
        uncertain=true;rule.onNodeWithTag("deck-editor-save").performClick()
        shell.awaitState{shell.controllers.deckLibrary!!.state.value.pending && !shell.controllers.deckLibrary!!.state.value.mutationBusy}
        val first=attempts.single()
        rule.onNodeWithText(shell.string(R.string.decks_close)).performClick()
        shell.awaitState{shell.controllers.deckLibrary!!.state.value.editor==null}
        assertTrue(shell.controllers.deckLibrary!!.state.value.pending)
        uncertain=false;rule.onNodeWithTag(DeckLibraryTags.RETRY).performScrollTo().performClick()
        shell.awaitState{!shell.controllers.deckLibrary!!.state.value.pending && !shell.controllers.deckLibrary!!.state.value.mutationBusy}
        assertEquals(listOf(first,first),attempts);assertEquals(Revision(1),rows.getValue(initial.id).revision)
    }
    @Test fun installedCatalogSearchAddsOnlyExplicitlySelectedOracleIdentity(){
        open();rule.onNodeWithTag(DeckLibraryTags.OVERVIEW).performScrollToNode(hasTestTag("deck-add-entry"))
        rule.onNodeWithTag("deck-add-entry").performClick()
        rule.onNodeWithTag("deck-editor-search").performTextInput("Synthetic")
        rule.onNodeWithText(shell.string(R.string.deck_edit_search_action)).performClick()
        shell.awaitState{shell.controllers.deckEditorSearch!!.state.value.results.isNotEmpty() && shell.controllers.deckEditorSearch!!.state.value.loading==null}
        val chosen=shell.controllers.deckEditorSearch!!.state.value.results.first()
        rule.onNodeWithTag("deck-add-result-${chosen.oracleId.raw}").performScrollTo().performClick()
        assertTrue(attempts.isEmpty())
        rule.onNodeWithTag("deck-editor-save").performClick()
        shell.awaitState{shell.controllers.deckLibrary!!.state.value.editor==null && !shell.controllers.deckLibrary!!.state.value.mutationBusy}
        val entry=rows.getValue(initial.id).entries.single()
        assertEquals(Knowledge.Known(chosen.oracleId),entry.identity.oracleId);assertEquals(chosen.language,entry.identity.language)
        assertTrue(entry.identity.printingId is Knowledge.Absent);assertEquals(1,entry.quantity)
    }
}
