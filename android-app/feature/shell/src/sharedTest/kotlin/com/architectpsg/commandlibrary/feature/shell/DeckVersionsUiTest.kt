package com.architectpsg.commandlibrary.feature.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.presentation.DeckVersionsController
import com.architectpsg.commandlibrary.feature.shell.ui.WidthClass
import com.architectpsg.commandlibrary.feature.shell.ui.decks.DeckVersionsScreen
import com.architectpsg.commandlibrary.feature.shell.ui.decks.DeckVersionsTags
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Actual retained controller and adaptive Compose over bounded synthetic ports, no backend claim. */
@RunWith(AndroidJUnit4::class)
class DeckVersionsUiTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var owner:CoroutineScope
    private lateinit var controller:DeckVersionsController
    private val width=mutableStateOf(360.dp)
    private val font=mutableFloatStateOf(2f)
    private val ids=AtomicInteger()
    private val calls=CopyOnWriteArrayList<List<Any>>()
    private val callbacks=CopyOnWriteArrayList<Pair<DeckId,Revision>>()
    private val receipts=ConcurrentHashMap<EntityId,DeckVersion>()
    @Volatile private var uncertain=false
    @Volatile private var empty=false
    private fun deck(rev:Long,name:String)=DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE,"ui-deck"),name,Knowledge.Absent("format"),"description",Knowledge.Absent("folder"),SnapshotList.empty(),
        UtcTimestamp(1),UtcTimestamp(rev+1),Revision(rev),SnapshotList.of(DeckEntry(EntityId("entry"),CardIdentity(Knowledge.Absent("oracle"),Knowledge.Absent("printing"),null,Knowledge.Known("Retained card"),LanguageTag("ja")),
            if(rev==1L)1 else 3,if(rev==1L)BoardRole.MAIN else BoardRole.SIDEBOARD,Knowledge.Unknown("finish",SnapshotList.empty()),if(rev==1L)"Historical note"else"Current note",SnapshotList.empty(),SnapshotList.empty())),
        Knowledge.Absent("budget"),null,SnapshotList.empty(),SnapshotList.of(PackageInstance(EntityId("instance"),EntityId("package"),EntityId(if(rev==1L)"historical-package"else"current-package"),PackageLinkPolicy.LINKED,
            SnapshotList.of(PackageContribution(EntityId("package-entry"),EntityId("entry"),1)))),"Complete historical primer",SnapshotList.empty())
    @Volatile private var head=deck(4,"Current deck")
    private val target=DeckVersion(EntityId("target"),head.id,"User checkpoint",UtcTimestamp(10),Revision(1),deck(1,"Historical deck"),null)
    private val recoveryTarget=target.copy(id=EntityId("recovery-target"),label="Recovery checkpoint",predecessorVersionId=target.id)
    private val rows=ConcurrentHashMap<EntityId,DeckVersion>().also{it[target.id]=target;it[recoveryTarget.id]=recoveryTarget}
    private val newestFirst=CopyOnWriteArrayList(listOf(recoveryTarget.id,target.id))
    private val recoveryTargets=ConcurrentHashMap<EntityId,EntityId>().also{it[recoveryTarget.id]=target.id}
    private val port=object:LocalDeckVersions,LocalDeckLibrary {
        override suspend fun capture(id:DeckId,expected:Revision,label:String,operationId:EntityId,cancel:CancelToken):Outcome<DeckVersion> {
            calls+=listOf(id,expected,label,operationId)
            val saved=receipts.getOrPut(operationId){DeckVersion(EntityId("captured"),id,label,UtcTimestamp(20),expected,head,newestFirst.firstOrNull()).also{rows[it.id]=it;newestFirst.add(0,it.id)}}
            return if(uncertain)Outcome.Unknown("Synthetic lost acknowledgement",SnapshotList.empty())else Outcome.Success(saved)
        }
        override suspend fun list(id:DeckId,page:PageRequest,cancel:CancelToken)=Outcome.Success(Page(if(empty)SnapshotList.empty()else SnapshotList.copyOf(newestFirst.map{rows.getValue(it)}.map{
            DeckVersionSummary(it.id,it.deckId,it.label,it.createdAt,it.sourceRevision,it.predecessorVersionId,if(recoveryTargets.containsKey(it.id))DeckVersionKind.RECOVERY else DeckVersionKind.USER,recoveryTargets[it.id])
        }),null,SnapshotReference(LocalDeckLibraryLimits.SOURCE,null,Revision(rows.size.toLong()),null)))
        override suspend fun load(id:DeckId,versionId:EntityId,cancel:CancelToken)=Outcome.Success(rows.getValue(versionId))
        override suspend fun previewRestore(id:DeckId,versionId:EntityId,expected:Revision,cancel:CancelToken):Outcome<DeckRestorePreview> {
            val v=rows.getValue(versionId)
            return Outcome.Success(DeckRestorePreview(head,v,DeckDifference(head,v.manifest,SnapshotList.of(DeckChangedField.NAME,DeckChangedField.MODIFIED_AT,DeckChangedField.REVISION,DeckChangedField.PACKAGES),
                SnapshotList.of(CardChange(EntityId("entry"),head.entries.single(),v.manifest.entries.single(),"Retained source diff",SnapshotList.empty())))))
        }
        override suspend fun restore(id:DeckId,versionId:EntityId,expected:Revision,recoveryLabel:String,operationId:EntityId,cancel:CancelToken):Outcome<DeckRestoreResult> {
            calls+=listOf(id,versionId,expected,recoveryLabel,operationId)
            val recovery=DeckVersion(EntityId("saved-recovery"),id,recoveryLabel,UtcTimestamp(30),expected,head,newestFirst.firstOrNull())
            head=rows.getValue(versionId).manifest.copy(revision=Revision(expected.value+1),modifiedAt=UtcTimestamp(30));rows[recovery.id]=recovery;recoveryTargets[recovery.id]=versionId;newestFirst.add(0,recovery.id)
            return Outcome.Success(DeckRestoreResult(head,recovery))
        }
        override suspend fun load(id:DeckId,cancel:CancelToken)=Outcome.Success(head)
        override suspend fun list(page:PageRequest,cancel:CancelToken):Outcome<Page<DeckSummary>> = error("No library pages")
        override suspend fun create(initial:DeckSnapshot,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> = error("No create")
        override suspend fun duplicate(id:DeckId,expected:Revision,name:String,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> = error("No duplicate")
    }
    private fun text(id:Int,vararg args:Any)=ApplicationProvider.getApplicationContext<android.content.Context>().getString(id,*args)
    @Before fun setup(){profileHostIdleBudget(300);owner=CoroutineScope(SupervisorJob()+Dispatchers.Main);controller=DeckVersionsController(owner,port,port,Dispatchers.Default,{EntityId("operation-${ids.incrementAndGet()}")},{id,rev->callbacks+=id to rev})}
    @After fun close(){owner.cancel();profileHostIdleBudget(60)}
    private fun await(check:()->Boolean)=rule.waitUntil(10_000){rule.waitForIdle();check()}
    private fun show(close:()->Unit={}) {
        rule.setContent { DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.value,800.dp)) then DeviceConfigurationOverride.FontScale(font.floatValue)) {
            MaterialTheme { DeckVersionsScreen(controller,WidthClass.of(width.value),close) }
        } }
        rule.runOnIdle{controller.openDeck(head.id)};await{controller.state.value.head!=null&&!controller.state.value.loadingList}
    }
    @Test fun captureUnknownRequestRetainsExactInputAcrossCloseAndRealResize() {
        uncertain=true;show();rule.onNodeWithTag("deck-versions-new").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag(DeckVersionsTags.LABEL).performTextInput(" Exact checkpoint ");rule.onNodeWithTag(DeckVersionsTags.CAPTURE).assertHeightIsAtLeast(48.dp).performClick()
        await{controller.state.value.pending&&!controller.state.value.mutationBusy};val original=calls.single()
        rule.onNodeWithText(text(R.string.deck_versions_cancel_form)).performClick();assertFalse(controller.state.value.captureForm)
        rule.runOnIdle{width.value=900.dp;font.floatValue=1f};rule.waitForIdle();assertEquals(1,ids.get());assertEquals(1,calls.size)
        uncertain=false;rule.onNodeWithTag(DeckVersionsTags.RETRY).performScrollTo().performClick();await{!controller.state.value.pending&&controller.state.value.captured!=null}
        assertEquals(listOf(original,original),calls.toList());assertEquals(Revision(4),controller.state.value.head!!.revision);assertTrue(callbacks.isEmpty())
    }
    @Test fun recoveryTargetPreviewShowsCompletePackageAndEntryChangesBeforeExplicitRestore() {
        show();rule.onNodeWithTag("deck-version-recovery-target").performScrollTo().assertHeightIsAtLeast(48.dp).performClick();await{controller.state.value.selected!=null}
        rule.onNodeWithTag(DeckVersionsTags.PREVIEW).performScrollTo().performClick();await{controller.state.value.preview!=null};assertTrue(calls.isEmpty())
        rule.onNodeWithTag(DeckVersionsTags.LIST).performScrollToNode(hasTestTag("deck-versions-field"));rule.onNodeWithTag("deck-versions-field").performClick()
        rule.onNodeWithText(text(R.string.deck_versions_changed,text(R.string.deck_versions_field_packages))).performScrollTo().performClick()
        rule.onNodeWithTag(DeckVersionsTags.LIST).performScrollToNode(hasText("historical-package"));rule.onNodeWithText("historical-package").assertIsDisplayed()
        rule.onNodeWithTag(DeckVersionsTags.LIST).performScrollToNode(hasText("Historical note"));rule.onNodeWithText("Historical note").assertIsDisplayed()
        rule.onNodeWithTag(DeckVersionsTags.LIST).performScrollToNode(hasTestTag(DeckVersionsTags.RECOVERY_LABEL));rule.onNodeWithTag(DeckVersionsTags.RECOVERY_LABEL).performTextInput("Before restore")
        rule.onNodeWithTag(DeckVersionsTags.RESTORE).performScrollTo().performClick();await{controller.state.value.restored!=null&&!controller.state.value.mutationBusy}
        assertEquals(listOf(head.id to Revision(5)),callbacks.toList());assertEquals(recoveryTarget.id,calls.single()[1]);assertEquals("Before restore",controller.state.value.restored!!.recoveryVersion.label)
    }
    @Test fun emptyExistingHistoryAndUnsavedCaptureDisclosureAreVisibleAtTwoHundredPercent() {
        empty=true;var closed=0;show{closed++}
        rule.onNodeWithText(text(R.string.deck_versions_empty)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("deck-versions-new").performScrollTo().performClick();rule.onNodeWithTag(DeckVersionsTags.LABEL).performTextInput("Unsaved")
        rule.onNodeWithText(text(R.string.deck_versions_cancel_form)).performClick();rule.onNodeWithText(text(R.string.deck_versions_discard_question)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.deck_versions_discard)).assertHeightIsAtLeast(48.dp).performClick();assertTrue(calls.isEmpty());assertEquals(0,ids.get());assertEquals(0,closed)
    }
}
