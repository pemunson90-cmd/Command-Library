package com.architectpsg.commandlibrary.feature.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.presentation.*
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

/** Real adaptive bulk presentation over synthetic ports; no backend durability acceptance claim. */
@RunWith(AndroidJUnit4::class)
class CollectionBulkUiTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var owner:CoroutineScope
    private lateinit var controller:CollectionController
    private val width=mutableStateOf(360.dp)
    private val font=mutableFloatStateOf(2f)
    private val generation=GenerationId("bulk-ui")
    private val ids=AtomicInteger()
    private val calls=CopyOnWriteArrayList<Triple<CollectionPatch,Revision,EntityId>>()
    private val receipts=ConcurrentHashMap<EntityId,InventoryCommit>()
    @Volatile private var unknown=false
    private val record=PrintingRecord(CardId(ProviderId("cards"),"print"),Knowledge.Absent("oracle"),"tst",Knowledge.Absent("set"),"1",LanguageTag.ENGLISH,Knowledge.Absent("rarity"),Knowledge.Known(SnapshotList.of(Finish.NONFOIL,Finish.FOIL)),Knowledge.Absent("art"),SnapshotList.empty(),SnapshotList.empty(),SnapshotList.empty(),SnapshotList.empty(),SnapshotList.empty(),syntheticProvenance(generation).copy(provider=ProviderId("cards")))
    private fun lot(id:String,known:Boolean)=OwnedLot(EntityId(id),CardIdentity(Knowledge.Absent("oracle"),if(known) Knowledge.Known(record.id) else Knowledge.Absent("printing"),ProviderIdentity(ProviderId("foreign"),"card","raw $id"),Knowledge.Known("Lot $id"),LanguageTag.ENGLISH),Knowledge.Unknown("finish",SnapshotList.empty()),CardCondition.UNKNOWN,"Shelf $id",2,"Notes $id",Revision(0),Knowledge.Absent("acquired"))
    private val original=InventorySnapshot(Revision(1),UtcTimestamp(1),SnapshotList.of(lot("a",true),lot("b",true),lot("c",false)),true)
    @Volatile private var stored=original
    private fun project(base:InventorySnapshot,patch:CollectionPatch):InventoryProjection {
        val next=Revision(base.revision.value+1)
        return InventoryProjection(base.revision,next,SnapshotList.copyOf(patch.edits.map{val row=(it as CollectionEdit.Replace).lot;InventoryLotChange(row.id,base.lots.first{lot->lot.id==row.id},row.copy(revision=next))}),SnapshotList.empty(),SnapshotList.empty())
    }
    private val port=object:LocalInventory,LocalDeckLibrary,CatalogPrintingLookup,OwnedQuantityAllocator {
        override suspend fun snapshot(cancel:CancelToken)=Outcome.Success(stored)
        override suspend fun apply(patch:CollectionPatch,expected:Revision,operationId:EntityId,cancel:CancelToken):Outcome<InventoryApplyResult> {
            calls+=Triple(patch,expected,operationId)
            val receipt=receipts.getOrPut(operationId){val p=project(stored,patch);val changed=p.changes.associateBy{it.lotId};stored=stored.copy(revision=p.resultRevision,lots=SnapshotList.copyOf(stored.lots.map{changed[it.id]?.after?:it}));InventoryCommit(p,UtcTimestamp(2))}
            return if(unknown)Outcome.Unknown("Synthetic lost acknowledgement",SnapshotList.empty())else Outcome.Success(InventoryApplyResult.Committed(receipt))
        }
        override suspend fun undo(expected:Revision,operationId:EntityId,cancel:CancelToken):Outcome<InventoryCommit> {
            val prior=receipts.values.single().projection;val next=Revision(expected.value+1);val selected=prior.changes.map{it.lotId}.toSet();val before=stored
            stored=original.copy(revision=next,lots=SnapshotList.copyOf(original.lots.map{if(it.id in selected)it.copy(revision=next)else it}))
            return Outcome.Success(InventoryCommit(InventoryProjection(expected,next,SnapshotList.copyOf(prior.changes.map{InventoryLotChange(it.lotId,before.lots.first{lot->lot.id==it.lotId},stored.lots.first{lot->lot.id==it.lotId})}),SnapshotList.empty(),SnapshotList.empty()),UtcTimestamp(3)))
        }
        override suspend fun printing(id:CardId,cancel:CancelToken)=Outcome.Success(record)
        override suspend fun printings(id:OracleId,page:PageRequest,cancel:CancelToken):Outcome<Page<PrintingRecord>> = error("No catalog page")
        override suspend fun list(page:PageRequest,cancel:CancelToken)=Outcome.Success(Page(SnapshotList.empty<DeckSummary>(),null,SnapshotReference(LocalDeckLibraryLimits.SOURCE,null,Revision(0),null)))
        override suspend fun load(id:DeckId,cancel:CancelToken):Outcome<DeckSnapshot> = error("No deck")
        override suspend fun create(initial:DeckSnapshot,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> = error("No create")
        override suspend fun duplicate(id:DeckId,expected:Revision,name:String,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> = error("No duplicate")
        override fun allocate(deck:DeckSnapshot,inventory:InventorySnapshot,policy:OwnedQuantityPolicy,cancel:CancelToken):Outcome<OwnedQuantityPlan> = error("No allocation")
    }
    private fun text(id:Int,vararg args:Any)=ApplicationProvider.getApplicationContext<android.content.Context>().getString(id,*args)
    @Before fun setup(){profileHostIdleBudget(300);owner=CoroutineScope(SupervisorJob()+Dispatchers.Main);controller=CollectionController(owner,port,port,port,port,{base,patch,_,_->Outcome.Success(project(base,patch))},Dispatchers.Default,{EntityId("operation-${ids.incrementAndGet()}")},{generation})}
    @After fun close(){owner.cancel();profileHostIdleBudget(60)}
    private fun await(check:()->Boolean)=rule.waitUntil(10_000){rule.waitForIdle();check()}
    private fun show(){rule.setContent{DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.value,800.dp)) then DeviceConfigurationOverride.FontScale(font.floatValue)){MaterialTheme{CollectionScreen(controller,WidthClass.of(width.value))}}};await{controller.state.value.inventory!=null}}
    private fun openAll(){rule.runOnIdle{controller.beginBulkSelection();controller.toggleBulkLot(EntityId("a"));controller.toggleBulkLot(EntityId("c"),true);controller.beginBulkEdit()};rule.waitForIdle()}
    private fun scroll(tag:String){rule.onNodeWithTag("collection-bulk-dialog-list").performScrollToNode(hasTestTag(tag))}
    @Test fun keyboardToggleRangeAndKeepSetZeroPersistThroughRealResizeAtTwoHundredPercent() {
        show();rule.onNodeWithTag("collection-bulk-select").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag(CollectionTags.INVENTORY).performScrollToNode(hasTestTag("collection-bulk-lot-a"));rule.onNodeWithTag("collection-bulk-lot-a").performSemanticsAction(SemanticsActions.RequestFocus){it()}.performKeyInput{pressKey(Key.Spacebar)}
        rule.onNodeWithTag(CollectionTags.INVENTORY).performScrollToNode(hasTestTag("collection-bulk-lot-c"));rule.onNodeWithTag("collection-bulk-lot-c").performSemanticsAction(SemanticsActions.RequestFocus){it()}.performKeyInput{keyDown(Key.ShiftLeft);pressKey(Key.Enter);keyUp(Key.ShiftLeft)}
        assertEquals(listOf(EntityId("a"),EntityId("b"),EntityId("c")),controller.state.value.bulkIds)
        rule.onNodeWithTag("collection-bulk-edit").performScrollTo().performClick();rule.onNodeWithTag("collection-bulk-preview").assertIsNotEnabled()
        rule.onNodeWithTag("collection-bulk-dialog-list").performScrollToNode(hasText(text(R.string.collection_bulk_mixed)));rule.onNodeWithText(text(R.string.collection_bulk_mixed)).assertIsDisplayed()
        scroll("bulk-set-${R.string.collection_quantity}");rule.onNodeWithTag("bulk-set-${R.string.collection_quantity}").assertIsNotSelected().performClick().assertIsSelected()
        scroll("collection-bulk-quantity");rule.onNodeWithTag("collection-bulk-quantity").performTextInput("0")
        rule.runOnIdle{width.value=900.dp;font.floatValue=1f};rule.waitForIdle();assertEquals(BulkValue.Set("0"),controller.state.value.bulkForm!!.draft.quantity);assertTrue(calls.isEmpty());assertEquals(0,ids.get())
        rule.onNodeWithTag("collection-bulk-preview").performClick();await{controller.state.value.bulkPreview!=null};assertFalse(controller.state.value.bulkPreview!!.blocked)
        rule.onNodeWithTag("collection-bulk-confirm").assertIsEnabled()
        rule.runOnIdle { controller.cancelBulkPreview();stored=stored.copy(revision=Revision(2),lots=SnapshotList.copyOf(stored.lots.filter{it.id!=EntityId("b")}));controller.refresh() };await{controller.state.value.inventory?.revision==Revision(2)}
        rule.runOnIdle{controller.useRefreshedBulkInventory()};rule.waitForIdle()
        rule.onNodeWithTag("collection-bulk-dialog-list").performScrollToNode(hasText(text(R.string.collection_bulk_missing_id,"b")));rule.onNodeWithText(text(R.string.collection_bulk_missing_id,"b")).assertIsDisplayed();assertTrue(calls.isEmpty())
    }
    @Test fun allRowFinishIssuesBlockThenExplicitExclusionRepreviewApplyAndUndoPreserveUnselectedLot() {
        show();openAll();scroll("bulk-set-${R.string.collection_finish}");rule.onNodeWithTag("bulk-set-${R.string.collection_finish}").performClick()
        scroll("collection-bulk-finish-FOIL");rule.onNodeWithTag("collection-bulk-finish-FOIL").performClick().assertIsSelected();rule.onNodeWithTag("collection-bulk-preview").performClick();await{controller.state.value.bulkPreview!=null}
        assertEquals(3,controller.state.value.bulkPreview!!.rows.size);rule.onNodeWithTag("collection-bulk-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("collection-bulk-dialog-list").performScrollToNode(hasText(text(R.string.collection_bulk_printing_issue)));rule.onNodeWithText(text(R.string.collection_bulk_printing_issue)).assertIsDisplayed()
        scroll("collection-bulk-exclude-c");rule.onNodeWithTag("collection-bulk-exclude-c").assertHeightIsAtLeast(48.dp).performClick();assertNull(controller.state.value.bulkPreview);assertTrue(calls.isEmpty())
        rule.onNodeWithTag("collection-bulk-preview").performClick();await{controller.state.value.bulkPreview!=null};rule.onNodeWithTag("collection-bulk-confirm").performClick();await{!controller.state.value.pending&&controller.state.value.bulkForm==null}
        assertEquals(2,calls.single().first.edits.size);assertEquals(original.lots.last(),stored.lots.last())
        rule.onNodeWithTag("collection-undo").performScrollTo().performClick();await{controller.state.value.savedRevision==Revision(3)};assertEquals(original.lots.last(),stored.lots.last())
    }
    @Test fun unknownBulkReconciliationSurvivesFormCloseResizeAndRetriesFrozenRequest() {
        unknown=true;show();openAll();rule.runOnIdle{controller.bulkDraft(CollectionBulkDraft(quantity=BulkValue.Set("0")))};rule.onNodeWithTag("collection-bulk-preview").performClick();await{controller.state.value.bulkPreview!=null}
        rule.onNodeWithTag("collection-bulk-confirm").performClick();await{controller.state.value.pending&&!controller.state.value.mutationBusy};val call=calls.single()
        rule.onAllNodesWithText(text(R.string.collection_bulk_preview_notice)).assertCountEquals(0);rule.onNodeWithTag("collection-bulk-close").performClick()
        rule.runOnIdle{width.value=900.dp;font.floatValue=1f};rule.waitForIdle();assertEquals(1,ids.get());assertEquals(1,calls.size)
        unknown=false;rule.onNodeWithTag(CollectionTags.RETRY).performScrollTo().performClick();await{!controller.state.value.pending};assertEquals(listOf(call,call),calls.toList());assertTrue(stored.lots.all{it.quantity==0})
    }
}
