package com.architectpsg.commandlibrary.feature.shell

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.presentation.CollectionController
import com.architectpsg.commandlibrary.feature.shell.ui.WidthClass
import com.architectpsg.commandlibrary.feature.shell.ui.collection.CollectionScreen
import com.architectpsg.commandlibrary.feature.shell.ui.collection.CollectionTags
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Actual collection controller/Compose; honest synthetic ports only, no stored-backend claim. */
@RunWith(AndroidJUnit4::class)
class CollectionUiTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var owner:CoroutineScope
    private val width=mutableStateOf(360.dp)
    private val font=mutableFloatStateOf(2f)
    private val ids=AtomicInteger()
    private val attempts=CopyOnWriteArrayList<Triple<CollectionPatch,Revision,EntityId>>()
    private val receipts=ConcurrentHashMap<EntityId,InventoryCommit>()
    @Volatile private var unknown=false
    @Volatile private var catalogCalls=0
    private val lot=OwnedLot(EntityId("original"),CardIdentity(Knowledge.Absent("unresolved oracle"),Knowledge.Absent("unresolved printing"),
        ProviderIdentity(ProviderId("foreign"),"card","retained raw"),Knowledge.Known("Stored foreign card"),LanguageTag("ja")),
        Knowledge.Unknown("unresolved finish",SnapshotList.empty()),CardCondition.UNKNOWN,"shelf",2,"original notes",Revision(0),Knowledge.Absent("not recorded"))
    @Volatile private var stored=InventorySnapshot(Revision(1),UtcTimestamp(1),SnapshotList.of(lot),true)
    private val record=PrintingRecord(CardId(ProviderId("cards"),"printing"),Knowledge.Known(OracleId(ProviderId("cards"),"oracle")),"tst",Knowledge.Known("Test set"),"1",LanguageTag.ENGLISH,
        Knowledge.Known("common"),Knowledge.Known(SnapshotList.of(Finish.NONFOIL,Finish.FOIL)),Knowledge.Absent("no image"),SnapshotList.empty(),SnapshotList.empty(),SnapshotList.empty(),
        SnapshotList.empty(),SnapshotList.empty(),syntheticProvenance(GenerationId("collection-ui")))
    private val deck=DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE,"stored-deck"),"Stored allocation deck",Knowledge.Absent("none"),"",Knowledge.Absent("none"),SnapshotList.empty(),
        UtcTimestamp(1),UtcTimestamp(1),Revision(4),SnapshotList.of(DeckEntry(EntityId("demand"),lot.identity,5,BoardRole.MAIN,lot.finish,"",SnapshotList.empty(),SnapshotList.empty())),
        Knowledge.Absent("none"),null,SnapshotList.empty(),SnapshotList.empty())
    private val port=object:LocalInventory,LocalDeckLibrary,CatalogPrintingLookup,OwnedQuantityAllocator {
        override suspend fun snapshot(cancel:CancelToken)=Outcome.Success(stored)
        override suspend fun apply(patch:CollectionPatch,expected:Revision,operationId:EntityId,cancel:CancelToken):Outcome<InventoryApplyResult> {
            attempts+=Triple(patch,expected,operationId)
            val receipt=receipts.getOrPut(operationId){
                val p=project(stored,patch);val next=stored.lots.toMutableList()
                p.changes.forEach{change->next.removeAll{it.id==change.lotId};change.after?.let{next.add(it)}}
                stored=stored.copy(revision=p.resultRevision,capturedAt=UtcTimestamp(2),lots=SnapshotList.copyOf(next))
                InventoryCommit(p,UtcTimestamp(2))
            }
            return if(unknown)Outcome.Unknown("Synthetic unknown acknowledgement",SnapshotList.empty())else Outcome.Success(InventoryApplyResult.Committed(receipt))
        }
        override suspend fun undo(expected:Revision,operationId:EntityId,cancel:CancelToken):Outcome<InventoryCommit> = Outcome.Unavailable(UnavailableReason.NOT_FOUND,"No fixture group")
        override suspend fun list(page:PageRequest,cancel:CancelToken)=Outcome.Success(Page(SnapshotList.of(DeckSummary(deck.id,deck.name,deck.revision,UtcTimestamp(1),UtcTimestamp(1),1,5)),null,SnapshotReference(LocalDeckLibraryLimits.SOURCE,null,Revision(1),null)))
        override suspend fun load(id:DeckId,cancel:CancelToken)=Outcome.Success(deck)
        override suspend fun create(initial:DeckSnapshot,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> = error("No deck writes")
        override suspend fun duplicate(id:DeckId,expected:Revision,name:String,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> = error("No deck writes")
        override suspend fun printing(id:CardId,cancel:CancelToken):Outcome<PrintingRecord>{catalogCalls++;return Outcome.Success(record)}
        override suspend fun printings(id:OracleId,page:PageRequest,cancel:CancelToken):Outcome<Page<PrintingRecord>> = error("No catalog pages")
        override fun allocate(deck:DeckSnapshot,inventory:InventorySnapshot,policy:OwnedQuantityPolicy,cancel:CancelToken)=Outcome.Success(OwnedQuantityPlan(deck.id,deck.revision,inventory.revision,policy,
            SnapshotList.empty(),SnapshotList.of(OwnedQuantityDemand(EntityId("demand"),5,0,5,false)),SnapshotList.of(EntityId("demand")),false))
    }
    private lateinit var controller:CollectionController
    private val context:Context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id:Int,vararg args:Any)=context.getString(id,*args)
    private fun project(base:InventorySnapshot,patch:CollectionPatch):InventoryProjection {
        val next=Revision(base.revision.value+1)
        val changes=patch.edits.map { when(it) {
            is CollectionEdit.Add->InventoryLotChange(it.lot.id,null,it.lot.copy(revision=next))
            is CollectionEdit.Replace->InventoryLotChange(it.lot.id,base.lots.first{lot->lot.id==it.lot.id},it.lot.copy(revision=next))
            is CollectionEdit.Remove->InventoryLotChange(it.lotId,base.lots.first{lot->lot.id==it.lotId},null)
            is CollectionEdit.Adjust->error("No Adjust from this form")
        } }
        return InventoryProjection(base.revision,next,SnapshotList.copyOf(changes),SnapshotList.empty(),SnapshotList.empty())
    }
    @Before fun setup(){profileHostIdleBudget(300);owner=CoroutineScope(SupervisorJob()+Dispatchers.Main);controller=CollectionController(owner,port,port,port,port,{base,patch,_,_->Outcome.Success(project(base,patch))},Dispatchers.Default,{EntityId("ui-${ids.incrementAndGet()}")})}
    @After fun close(){owner.cancel();profileHostIdleBudget(60)}
    private fun show(browse:()->Unit={}) {
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.value,800.dp)) then DeviceConfigurationOverride.FontScale(font.floatValue)) {
                MaterialTheme { CollectionScreen(controller,WidthClass.of(width.value),browse) }
            }
        }
        await{controller.state.value.inventory!=null&&!controller.state.value.loading}
    }
    private fun await(check:()->Boolean)=rule.waitUntil(10_000){rule.waitForIdle();check()}
    @Test fun explicitPreviewUnknownReconciliationSurvivesCloseAndResizeWithoutFalsePrecommitClaim() {
        unknown=true;show()
        rule.onNodeWithTag("collection-lot-original").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag("collection-edit").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag(CollectionTags.NOTES).performScrollTo().performTextReplacement("retained draft")
        assertTrue(attempts.isEmpty());rule.onNodeWithTag(CollectionTags.PREVIEW).performClick();await{controller.state.value.preview!=null}
        val expectedPreview = text(R.string.collection_preview_summary,
            context.resources.getQuantityString(R.plurals.collection_preview_changes, 1, 1),
            context.resources.getQuantityString(R.plurals.collection_preview_merges, 0, 0))
        rule.onNodeWithText(expectedPreview).assertExists()
        assertTrue(attempts.isEmpty());rule.onNodeWithTag(CollectionTags.CONFIRM).performClick();await{controller.state.value.pending&&!controller.state.value.mutationBusy}
        rule.onAllNodesWithText(expectedPreview).assertCountEquals(0)
        rule.onNodeWithTag("collection-close-form").performClick();assertTrue(controller.state.value.pending)
        rule.runOnIdle{width.value=900.dp;font.floatValue=1f};rule.waitForIdle();assertEquals(1,attempts.size);assertEquals(1,ids.get())
        unknown=false;rule.onNodeWithTag(CollectionTags.RETRY).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        await{!controller.state.value.pending&&controller.state.value.inventory?.revision==Revision(2)}
        assertEquals(listOf(attempts.first(),attempts.first()),attempts.toList());assertEquals("retained draft",stored.lots.single().notes)
    }
    @Test fun onlyActualSupportedFinishesAreOfferedAndSelectionIsVisibleSemanticAndRetained() {
        show();rule.runOnIdle{controller.beginAdd(record)};await{controller.state.value.form!=null}
        rule.onNodeWithTag("collection-finish-2").assertDoesNotExist()
        rule.onNodeWithTag("collection-finish-0").performScrollTo().assertIsNotSelected().assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
        rule.onNodeWithText(text(R.string.collection_selected_finish,text(R.string.collection_nonfoil))).performScrollTo().assertIsDisplayed()
        rule.runOnIdle{width.value=900.dp;font.floatValue=1f};rule.waitForIdle();rule.onNodeWithTag("collection-finish-0").assertIsSelected()
        rule.onNodeWithTag(CollectionTags.QUANTITY).performScrollTo().performTextReplacement("0")
        rule.onNodeWithTag(CollectionTags.PREVIEW).performClick();await{controller.state.value.preview!=null};rule.onNodeWithTag(CollectionTags.CONFIRM).performClick()
        await{!controller.state.value.mutationBusy&&controller.state.value.savedRevision!=null}
        assertEquals(2,catalogCalls);assertEquals(2,ids.get());assertEquals(0,(attempts.single().first.edits.single() as CollectionEdit.Add).lot.quantity)
    }
    @Test fun allocationDisplaysUncertainRemainingAndExactRevisionProvenance() {
        show();rule.onNodeWithTag("collection-deck-tab").performClick()
        rule.onNodeWithTag("collection-deck-stored-deck").performScrollTo().assertHeightIsAtLeast(48.dp).performClick();await{controller.state.value.allocation!=null}
        rule.onNodeWithText(text(R.string.collection_plan_revision,deck.name,4L,1L)).performScrollTo().assertIsDisplayed()
        val demand=text(R.string.collection_demand_uncertain,"Stored foreign card",5,0,5)
        rule.onNodeWithTag(CollectionTags.ALLOCATION).performScrollToNode(hasText(demand))
        rule.onNodeWithText(demand).assertIsDisplayed()
        assertTrue(attempts.isEmpty());assertEquals(0,ids.get())
    }
    @Test fun emptyInventoryOffersActualBrowseCallbackWithoutCreatingRequests() {
        stored=stored.copy(lots=SnapshotList.empty());var browsed=0;show{browsed++}
        rule.onNodeWithTag("collection-browse-cards").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1,browsed);assertTrue(attempts.isEmpty());assertEquals(0,ids.get())
        rule.onNodeWithText(text(R.string.collection_empty)).performScrollTo().assertIsDisplayed()
    }
}
