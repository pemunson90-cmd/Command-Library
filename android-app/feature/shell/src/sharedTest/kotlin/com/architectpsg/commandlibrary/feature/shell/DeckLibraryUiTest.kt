package com.architectpsg.commandlibrary.feature.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.ui.decks.DeckLibraryTags
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Actual shell/controller/Compose over synthetic test-only library; Room has separate root probes. */
@RunWith(AndroidJUnit4::class)
class DeckLibraryUiTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var shell:ShellUiHarness
    private val rows=ConcurrentHashMap<DeckId,DeckSnapshot>()
    private val receipts=ConcurrentHashMap<EntityId,DeckSnapshot>()
    private val attempts=CopyOnWriteArrayList<Pair<DeckSnapshot,EntityId>>()
    private val epoch=AtomicLong()
    @Volatile private var uncertain=true
    private val port=object:LocalDeckLibrary {
        override suspend fun create(initial:DeckSnapshot,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> {
            attempts+=initial to operationId
            val result=receipts.getOrPut(operationId){rows[initial.id]=initial;epoch.incrementAndGet();initial}
            return if(uncertain) Outcome.Failure(DomainFailure(FailureCode.PERSISTENCE,"Synthetic lost acknowledgement",SnapshotList.empty())) else Outcome.Success(result)
        }
        override suspend fun duplicate(id:DeckId,expected:Revision,name:String,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> {
            val result=receipts.getOrPut(operationId){rows.getValue(id).copy(id=DeckId(LocalDeckLibraryLimits.SOURCE,"copy"),name=name).also{rows[it.id]=it;epoch.incrementAndGet()}}
            return Outcome.Success(result)
        }
        override suspend fun load(id:DeckId,cancel:CancelToken):Outcome<DeckSnapshot> = rows[id]?.let{Outcome.Success(it)} ?: Outcome.Unavailable(UnavailableReason.NOT_FOUND,"Synthetic missing")
        override suspend fun list(page:PageRequest,cancel:CancelToken):Outcome<Page<DeckSummary>> = Outcome.Success(Page(SnapshotList.copyOf(rows.values.sortedBy{it.id.raw}.map{
            DeckSummary(it.id,it.name,it.revision,it.createdAt,it.modifiedAt,it.entries.size,it.entries.sumOf{e->e.quantity.toLong()})}),null,
            SnapshotReference(LocalDeckLibraryLimits.SOURCE,null,Revision(epoch.get()),null)))
    }
    @Before fun setup() { profileHostIdleBudget(300);shell=ShellUiHarness(rule,profiles=UiProfilesFixture(),localRules=KeywordRulesFixture(),decks=port) }
    @After fun close() { shell.close();profileHostIdleBudget(60) }
    @Test fun sixDestinationOverflowAndPendingRequestSurviveRealResizeThenDuplicate() {
        shell.show(360.dp,800.dp,2f)
        rule.onNodeWithTag("shell-nav-decks").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        rule.onNodeWithTag("shell-nav-more").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag("shell-nav-settings").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag("shell-nav-decks").performClick()
        shell.awaitState { !shell.controllers.deckLibrary!!.state.value.loading }
        rule.onNodeWithTag(DeckLibraryTags.NEW).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag(DeckLibraryTags.NAME).performTextInput("Synthetic library deck")
        rule.onNodeWithTag(DeckLibraryTags.CONFIRM).assertHeightIsAtLeast(48.dp).performClick()
        shell.awaitState { shell.controllers.deckLibrary!!.state.value.let{it.pending&&!it.mutationBusy} }
        rule.onNodeWithTag(DeckLibraryTags.NAME).assertIsNotEnabled()
        val first=attempts.single()
        shell.resize(900.dp,800.dp,1f)
        assertTrue(shell.controllers.deckLibrary!!.state.value.pending)
        rule.onNodeWithText(shell.string(R.string.decks_close)).performClick()
        uncertain=false
        rule.onNodeWithTag(DeckLibraryTags.RETRY).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        shell.awaitState { shell.controllers.deckLibrary!!.state.value.let{!it.pending&&it.selected?.id==first.first.id} }
        assertEquals(listOf(first,first),attempts.toList());assertEquals(1,rows.size)
        rule.onNodeWithTag(DeckLibraryTags.DUPLICATE).performScrollTo().performClick()
        rule.onNodeWithTag(DeckLibraryTags.NAME).performTextReplacement("Synthetic duplicate")
        rule.onNodeWithTag(DeckLibraryTags.CONFIRM).performClick()
        shell.awaitState { shell.controllers.deckLibrary!!.state.value.selected?.name=="Synthetic duplicate" }
        assertEquals(2,rows.size)
        shell.resize(360.dp,800.dp,2f)
        rule.onNodeWithTag(DeckLibraryTags.OVERVIEW).assertIsDisplayed()
        rule.onNodeWithTag("shell-nav-more").performClick()
        rule.onNodeWithTag("shell-nav-profiles").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag("shell-nav-decks").performClick()
        assertEquals("Synthetic duplicate",shell.controllers.deckLibrary!!.state.value.selected?.name)
    }
    @Test fun populatedOverviewPreservesUnusedCategoryOrderAndPlainTextNotes() {
        val unused=EntryLabel(EntityId("unused"),"Unused category")
        val used=EntryLabel(EntityId("used"),"Used category")
        val note="Exact entry notes <script>not executable</script>"
        val entry=DeckEntry(EntityId("populated-entry"),CardIdentity(Knowledge.Absent("unknown"),Knowledge.Absent("unknown"),
            ProviderIdentity(ProviderId("foreign"),"raw","source-qualified-card"),Knowledge.Known("Synthetic populated card"),LanguageTag.ENGLISH),
            2,BoardRole.COMMANDER,Knowledge.Absent("unknown"),note,SnapshotList.of(used),SnapshotList.empty())
        val deck=DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE,"populated"),"Populated deck",Knowledge.Absent("none"),"description",
            Knowledge.Absent("none"),SnapshotList.empty(),UtcTimestamp(1),UtcTimestamp(1),Revision(0),SnapshotList.of(entry),Knowledge.Absent("none"),null,
            SnapshotList.empty(),SnapshotList.empty(),primer="<script>plain primer</script>",categoryOrder=SnapshotList.of(unused,used))
        rows[deck.id]=deck;epoch.incrementAndGet();uncertain=false
        shell.show(900.dp,800.dp)
        rule.onNodeWithTag("shell-nav-decks").performClick()
        shell.awaitState{shell.controllers.deckLibrary!!.state.value.items.isNotEmpty()}
        rule.onNodeWithTag("deck-open-populated").performScrollTo().performClick()
        shell.awaitState{shell.controllers.deckLibrary!!.state.value.selected?.id==deck.id}
        rule.onNodeWithText("Unused category").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("deck-categories-next").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag(DeckLibraryTags.OVERVIEW).performScrollToNode(hasText("<script>plain primer</script>"))
        rule.onNodeWithText("<script>plain primer</script>").performScrollTo().assertIsDisplayed().assertHasNoClickAction()
        rule.onNodeWithTag(DeckLibraryTags.OVERVIEW).performScrollToNode(hasText(note))
        rule.onNodeWithText(note).performScrollTo().assertIsDisplayed().assertHasNoClickAction()
        assertEquals(deck,shell.controllers.deckLibrary!!.state.value.selected)
    }

}
