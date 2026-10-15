@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.syntheticProvenance
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Presentation oracles over bounded synthetic ports; root owns actual Room/atomicity acceptance. */
class CollectionBulkControllerTest {
    private val gen = GenerationId("bulk-generation")
    private fun printing(raw: String = "printing") = PrintingRecord(CardId(ProviderId("cards"),raw),Knowledge.Known(OracleId(ProviderId("cards"),"oracle")),"tst",Knowledge.Known("Set"),"1",LanguageTag.ENGLISH,
        Knowledge.Absent("rarity"),Knowledge.Known(SnapshotList.of(Finish.NONFOIL,Finish.FOIL)),Knowledge.Absent("art"),SnapshotList.empty(),SnapshotList.empty(),SnapshotList.empty(),SnapshotList.empty(),SnapshotList.empty(),syntheticProvenance(gen).copy(provider=ProviderId("cards")))
    private fun lot(id: String, known: Boolean = true) = OwnedLot(EntityId(id),CardIdentity(Knowledge.Known(OracleId(ProviderId("cards"),"oracle")),if(known) Knowledge.Known(printing().id) else Knowledge.Unknown("printing unresolved",SnapshotList.of(Evidence("retained foreign evidence",null,null))),ProviderIdentity(ProviderId("foreign"),"arbitrary kind","raw $id"),Knowledge.Known("Card $id"),LanguageTag.ENGLISH),
        if(known) Knowledge.Known(Finish.NONFOIL) else Knowledge.Unknown("finish unresolved",SnapshotList.of(Evidence("finish evidence",null,null))),if(id=="a") CardCondition.MINT else CardCondition.DAMAGED,"shelf $id",if(id=="a") 3 else 5,"notes $id",Revision(2),Knowledge.Unknown("acquisition unresolved",SnapshotList.of(Evidence("acquired evidence",null,null))))
    private fun failed(code: FailureCode) = Outcome.Failure(DomainFailure(code,"Synthetic failure",SnapshotList.empty()))
    private inner class Fixture : LocalInventory,LocalDeckLibrary,CatalogPrintingLookup,OwnedQuantityAllocator {
        val original=InventorySnapshot(Revision(7),UtcTimestamp(7),SnapshotList.of(lot("a"),lot("b"),lot("c",false)),true)
        var stored=original
        var active: GenerationId?=gen
        var record=printing()
        var ids=0
        val catalogCalls=mutableListOf<CardId>()
        val calls=mutableListOf<Triple<CollectionPatch,Revision,EntityId>>()
        val undos=mutableListOf<Pair<Revision,EntityId>>()
        val receipts=mutableMapOf<EntityId,InventoryCommit>()
        val records=mutableMapOf<CardId,PrintingRecord>()
        var result: Outcome<InventoryApplyResult>?=null
        var catalogGate: CompletableDeferred<Unit>?=null
        var applyGate: CompletableDeferred<Unit>?=null
        var ignoreCancellation=false
        var projectionIssues=SnapshotList.empty<DomainIssue>()
        fun project(base: InventorySnapshot,patch: CollectionPatch):InventoryProjection {
            val next=Revision(base.revision.value+1)
            if(projectionIssues.isNotEmpty())return InventoryProjection(base.revision,next,SnapshotList.empty(),SnapshotList.empty(),projectionIssues)
            return InventoryProjection(base.revision,next,SnapshotList.copyOf(patch.edits.map { edit ->
                val replacement=(edit as CollectionEdit.Replace).lot
                InventoryLotChange(replacement.id,base.lots.first{it.id==replacement.id},replacement.copy(revision=next))
            }),SnapshotList.empty(),projectionIssues)
        }
        override suspend fun snapshot(cancel:CancelToken)=Outcome.Success(stored)
        override suspend fun apply(patch:CollectionPatch,expected:Revision,operationId:EntityId,cancel:CancelToken):Outcome<InventoryApplyResult> {
            calls+=Triple(patch,expected,operationId)
            val early=result
            if(early is Outcome.Success && early.value is InventoryApplyResult.Rejected)return early
            val receipt=receipts.getOrPut(operationId){
                val delta=project(stored,patch);val after=delta.changes.associateBy{it.lotId}
                stored=stored.copy(revision=delta.resultRevision,capturedAt=UtcTimestamp(8),lots=SnapshotList.copyOf(stored.lots.map{after[it.id]?.after?:it}))
                InventoryCommit(delta,UtcTimestamp(8))
            }
            applyGate?.await()
            return result?:Outcome.Success(InventoryApplyResult.Committed(receipt))
        }
        override suspend fun undo(expected:Revision,operationId:EntityId,cancel:CancelToken):Outcome<InventoryCommit> {
            undos+=expected to operationId
            val next=Revision(expected.value+1);val before=stored;val selected=receipts.values.last().projection.changes.map{it.lotId}.toSet();stored=original.copy(revision=next,capturedAt=UtcTimestamp(9),lots=SnapshotList.copyOf(original.lots.map{if(it.id in selected)it.copy(revision=next)else it}))
            return Outcome.Success(InventoryCommit(InventoryProjection(expected,next,SnapshotList.copyOf(before.lots.filter{it.id in selected}.map{row->InventoryLotChange(row.id,row,stored.lots.first{it.id==row.id})}),SnapshotList.empty(),SnapshotList.empty()),UtcTimestamp(9)))
        }
        override suspend fun printing(id:CardId,cancel:CancelToken):Outcome<PrintingRecord> {
            catalogCalls+=id;val value=records[id]?:record;val gate=catalogGate
            if(ignoreCancellation)withContext(NonCancellable){gate?.await()}else gate?.await()
            return Outcome.Success(value)
        }
        override suspend fun printings(id:OracleId,page:PageRequest,cancel:CancelToken):Outcome<Page<PrintingRecord>> = error("No catalog pages")
        override suspend fun list(page:PageRequest,cancel:CancelToken)=Outcome.Success(Page(SnapshotList.empty<DeckSummary>(),null,SnapshotReference(LocalDeckLibraryLimits.SOURCE,null,Revision(0),null)))
        override suspend fun load(id:DeckId,cancel:CancelToken):Outcome<DeckSnapshot> = error("No allocation deck")
        override suspend fun create(initial:DeckSnapshot,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> = error("No deck create")
        override suspend fun duplicate(id:DeckId,expected:Revision,name:String,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> = error("No deck duplicate")
        override fun allocate(deck:DeckSnapshot,inventory:InventorySnapshot,policy:OwnedQuantityPolicy,cancel:CancelToken):Outcome<OwnedQuantityPlan> = error("No allocation")
    }
    private fun TestScope.controller(f:Fixture,owner:CoroutineScope=ownerScope()) = CollectionController(owner,f,f,f,f,{base,patch,_,cancel ->
        cancel.throwIfCancelled();Outcome.Success(f.project(base,patch))
    },StandardTestDispatcher(testScheduler),{EntityId("operation-${++f.ids}")},{f.active})
    private fun TestScope.open(f:Fixture,ids:List<String> = listOf("a","b","c"))=controller(f).also { c-> c.ensureLoaded();advanceUntilIdle();c.beginBulkSelection();ids.forEach{c.toggleBulkLot(EntityId(it))};c.beginBulkEdit() }
    private fun finishDraft()=CollectionBulkDraft(quantity=BulkValue.Set("0"),condition=BulkValue.Set(CardCondition.UNKNOWN),location=BulkValue.Set(""),finish=BulkValue.Set(Finish.FOIL))
    @Test fun allRowsBlockThenExplicitExclusionAndNewPreviewProduceOneAtomicReplacementAndUndo()=runTest {
        val f=Fixture();val c=open(f);c.bulkDraft(finishDraft());c.previewBulkEdit();advanceUntilIdle()
        assertEquals(3,c.state.value.bulkPreview!!.rows.size);assertTrue(c.state.value.bulkPreview!!.blocked);assertEquals(1,f.catalogCalls.size)
        c.confirmBulkPreview();advanceUntilIdle();assertEquals(0,f.ids);assertTrue(f.calls.isEmpty())
        c.excludeBulkLot(EntityId("c"));assertNull(c.state.value.bulkPreview);c.confirmBulkPreview();assertTrue(f.calls.isEmpty())
        c.previewBulkEdit();advanceUntilIdle();assertFalse(c.state.value.bulkPreview!!.blocked);c.confirmBulkPreview();advanceUntilIdle()
        val patch=f.calls.single().first;assertEquals(2,patch.edits.size);assertTrue(patch.edits.all{it is CollectionEdit.Replace});assertEquals(3,f.catalogCalls.size)
        for (edit in patch.edits) { val row=(edit as CollectionEdit.Replace).lot;val original=f.original.lots.first{it.id==row.id};assertEquals(original.copy(quantity=0,condition=CardCondition.UNKNOWN,location="",finish=Knowledge.Known(Finish.FOIL)),row) }
        assertEquals(lot("c",false),f.stored.lots.first{it.id==EntityId("c")});assertEquals(Revision(8),f.stored.revision)
        c.undo();advanceUntilIdle();assertEquals(1,f.undos.size);assertEquals(f.original.lots.map{if(it.id==EntityId("c"))it else it.copy(revision=Revision(9))},f.stored.lots)
    }
    @Test fun keepFinishEditsUnresolvedLotsWithoutCatalogAndPreservesEveryUntouchedField()=runTest {
        val f=Fixture();f.active=null;val c=open(f);c.previewBulkEdit();advanceUntilIdle();assertNull(c.state.value.bulkPreview);assertTrue(f.calls.isEmpty())
        c.bulkDraft(CollectionBulkDraft(quantity=BulkValue.Set("0"),location=BulkValue.Set("")));c.previewBulkEdit();advanceUntilIdle();c.confirmBulkPreview();advanceUntilIdle()
        assertTrue(f.catalogCalls.isEmpty());for(edit in f.calls.single().first.edits){val row=(edit as CollectionEdit.Replace).lot;assertEquals(f.original.lots.first{it.id==row.id}.copy(quantity=0,location=""),row)}
    }
    @Test fun explicitSameFinishStillRequiresEvidenceAndMissingActiveGenerationFailsClosed()=runTest {
        val f=Fixture();f.active=null;val c=open(f,listOf("a"));c.bulkDraft(CollectionBulkDraft(finish=BulkValue.Set(Finish.NONFOIL)));c.previewBulkEdit();advanceUntilIdle()
        assertTrue(c.state.value.bulkPreview!!.blocked);assertEquals(Problem.STALE_GENERATION,c.state.value.bulkPreview!!.rows.single().catalogProblem);assertTrue(f.catalogCalls.isEmpty())
        f.active=gen;c.previewBulkEdit();advanceUntilIdle();assertEquals(1,f.catalogCalls.size);assertFalse(c.state.value.bulkPreview!!.blocked)
    }
    @Test fun catalogProofRejectsQualifiedIdentityLanguageOracleProvenanceAndUnknownAvailability()=runTest {
        val variants=listOf(printing().copy(id=CardId(ProviderId("other"),"printing")),printing().copy(language=LanguageTag("ja")),printing().copy(oracleId=Knowledge.Known(OracleId(ProviderId("cards"),"other"))),
            printing().copy(provenance=syntheticProvenance(GenerationId("other")).copy(provider=ProviderId("cards"))),printing().copy(provenance=printing().provenance.copy(provider=ProviderId("other"))),
            printing().copy(finishes=Knowledge.Unknown("unknown",SnapshotList.empty())),printing().copy(finishes=Knowledge.Absent("absent")),printing().copy(finishes=Knowledge.Known(SnapshotList.of(Finish.NONFOIL))))
        for(record in variants){val f=Fixture();f.record=record;val c=open(f,listOf("a","b"));c.bulkDraft(finishDraft());c.previewBulkEdit();advanceUntilIdle();assertTrue(c.state.value.bulkPreview!!.blocked);assertTrue(c.state.value.bulkPreview!!.rows.all{it.blocked});assertEquals(1,f.catalogCalls.size);c.confirmBulkPreview();assertEquals(0,f.ids)}
    }
    @Test fun generationChangedDuringPreviewIsRejectedEvenWithoutObserver()=runTest {
        val f=Fixture();val c=open(f,listOf("a"));val gate=CompletableDeferred<Unit>();f.catalogGate=gate;c.bulkDraft(finishDraft());c.previewBulkEdit();runCurrent()
        f.active=GenerationId("new");gate.complete(Unit);advanceUntilIdle();assertNull(c.state.value.bulkPreview);assertEquals(Problem.STALE_GENERATION,c.state.value.bulkProblem);assertEquals(0,f.ids)
    }
    @Test fun observerCancelsFinishPreviewButPreservesKeepFinishDraftAndProofDoesNotRepublish()=runTest {
        val f=Fixture();val c=open(f,listOf("a"));val gate=CompletableDeferred<Unit>();f.ignoreCancellation=true;f.catalogGate=gate;c.bulkDraft(finishDraft());c.previewBulkEdit();runCurrent()
        f.active=GenerationId("new");c.onActiveGeneration(f.active);gate.complete(Unit);advanceUntilIdle();assertNull(c.state.value.bulkPreview);assertFalse(c.state.value.bulkPreviewing)
        c.bulkDraft(CollectionBulkDraft(location=BulkValue.Set("exact")));c.previewBulkEdit();advanceUntilIdle();val p=c.state.value.bulkPreview;c.onActiveGeneration(null);assertSame(p,c.state.value.bulkPreview)
    }
    @Test fun confirmRechecksEvidenceAndDirectGenerationBeforeGeneratingOperationId()=runTest {
        val f=Fixture();val c=open(f,listOf("a"));c.bulkDraft(finishDraft());c.previewBulkEdit();advanceUntilIdle();val gate=CompletableDeferred<Unit>();f.catalogGate=gate
        c.confirmBulkPreview();runCurrent();f.active=GenerationId("new");gate.complete(Unit);advanceUntilIdle();assertEquals(0,f.ids);assertTrue(f.calls.isEmpty());assertEquals(Problem.STALE_GENERATION,c.state.value.bulkProblem)
        f.active=gen;f.catalogGate=null;c.previewBulkEdit();advanceUntilIdle();f.record=printing().copy(finishes=Knowledge.Known(SnapshotList.of(Finish.NONFOIL)));c.confirmBulkPreview();advanceUntilIdle();assertTrue(c.state.value.bulkPreview!!.blocked);assertEquals(0,f.ids)
    }
    @Test fun inventoryRefreshRequiresExplicitRebaseAndMissingRowsRemainVisibleUntilExcluded()=runTest {
        val f=Fixture();val c=open(f);c.bulkDraft(CollectionBulkDraft(quantity=BulkValue.Set("1")));c.previewBulkEdit();advanceUntilIdle()
        f.stored=f.stored.copy(revision=Revision(8),lots=SnapshotList.copyOf(f.stored.lots.filter{it.id!=EntityId("b")}));c.refresh();advanceUntilIdle();assertNull(c.state.value.bulkPreview)
        c.previewBulkEdit();assertEquals(Problem.STALE_REVISION,c.state.value.bulkProblem);c.useRefreshedBulkInventory();assertEquals(3,c.state.value.bulkForm!!.ids.size)
        c.previewBulkEdit();advanceUntilIdle();assertTrue(c.state.value.bulkPreview!!.rows.first{it.id==EntityId("b")}.blocked);c.confirmBulkPreview();assertTrue(f.calls.isEmpty())
        c.excludeBulkLot(EntityId("b"));c.previewBulkEdit();advanceUntilIdle();assertFalse(c.state.value.bulkPreview!!.blocked)
    }
    @Test fun uncertainBulkRetryKeepsExactPatchExpectedAndIdAfterCloseAndCatalogChange()=runTest {
        val f=Fixture();val c=open(f,listOf("a","b"));c.bulkDraft(finishDraft());c.previewBulkEdit();advanceUntilIdle();f.result=Outcome.Unknown("lost",SnapshotList.empty());c.confirmBulkPreview();advanceUntilIdle()
        val call=f.calls.single();val reads=f.catalogCalls.size;assertTrue(c.state.value.pending);c.closeBulkForm();assertNull(c.state.value.bulkForm)
        f.active=null;c.onActiveGeneration(null);f.result=failed(FailureCode.STALE_REVISION);c.retry();advanceUntilIdle();assertTrue(c.state.value.pending)
        f.stored=f.stored.copy(revision=Revision(20));f.result=null;c.retry();advanceUntilIdle();assertEquals(List(3){call},f.calls);assertEquals(reads,f.catalogCalls.size);assertEquals(1,f.ids);assertEquals(Revision(20),c.state.value.inventory!!.revision)
    }
    @Test fun bulkSelectionUsesStableDisplayOrderRangeAnchorAndRejectsOversizeWithoutTruncation()=runTest {
        val f=Fixture();f.stored=f.stored.copy(lots=SnapshotList.copyOf((0..1024).map{lot(it.toString())}));val c=controller(f);c.ensureLoaded();advanceUntilIdle();c.selectLot(EntityId("500"));c.beginBulkSelection();c.toggleBulkLot(EntityId("0"));c.toggleBulkLot(EntityId("1023"),true)
        assertEquals(1024,c.state.value.bulkIds.size);val before=c.state.value.bulkIds;c.toggleBulkLot(EntityId("1024"));assertEquals(before,c.state.value.bulkIds);assertEquals(Problem.INVALID_INPUT,c.state.value.bulkSelectionProblem)
        c.toggleBulkLot(EntityId("1024"),true);assertEquals(before,c.state.value.bulkIds);assertEquals(EntityId("500"),c.state.value.selectedLotId)
        f.stored=f.stored.copy(revision=Revision(8),lots=SnapshotList.copyOf(f.stored.lots.reversed()));c.refresh();advanceUntilIdle();assertEquals(EntityId("0"),c.state.value.bulkSelectionBase!!.lots.first().id)
    }
    @Test fun invalidQuantityAndStructuralIssuesBlockEveryWriteWithoutChangingSelection()=runTest {
        val f=Fixture();val c=open(f);for(q in listOf("","-1","2147483648","1.5")){c.bulkDraft(CollectionBulkDraft(quantity=BulkValue.Set(q)));c.previewBulkEdit();advanceUntilIdle();assertTrue(c.state.value.bulkPreview!!.rows.all{it.issues.contains(CollectionBulkIssue.QUANTITY)});c.confirmBulkPreview();assertEquals(0,f.ids)}
        f.projectionIssues=SnapshotList.of(DomainIssue("fixture-row-error",IssueSeverity.ERROR,"Synthetic row rejection",EntityId("a"),SnapshotList.empty()))
        c.bulkDraft(CollectionBulkDraft(location=BulkValue.Set("new")));c.previewBulkEdit();advanceUntilIdle();assertTrue(c.state.value.bulkPreview!!.blocked);assertEquals(1,c.state.value.bulkPreview!!.rows.first().structuralIssues.size);c.confirmBulkPreview();assertTrue(f.calls.isEmpty())
    }
    @Test fun draftAndPreviewCancellationNeverWriteAndDiscardRequiresExplicitConsent()=runTest {
        val f=Fixture();val c=open(f);c.bulkDraft(CollectionBulkDraft(location=BulkValue.Set(" draft ")));c.previewBulkEdit();advanceUntilIdle();c.cancelBulkPreview();assertNull(c.state.value.bulkPreview)
        c.closeBulkForm();assertTrue(c.state.value.bulkDiscardPrompt);c.keepBulkForm();assertEquals(BulkValue.Set(" draft "),c.state.value.bulkForm!!.draft.location)
        c.closeBulkForm();c.discardBulkForm();assertNull(c.state.value.bulkForm);assertEquals(3,c.state.value.bulkIds.size);assertEquals(0,f.ids);assertTrue(f.calls.isEmpty())
    }
    @Test fun oneQueueBlocksSingleEditAddAndUndoWhileBulkSubmittedAndRefreshDoesNotCancelIt()=runTest {
        val f=Fixture();val c=open(f,listOf("a"));c.bulkDraft(CollectionBulkDraft(quantity=BulkValue.Set("0")));c.previewBulkEdit();advanceUntilIdle();val gate=CompletableDeferred<Unit>();f.applyGate=gate;c.confirmBulkPreview();runCurrent()
        c.beginEdit(EntityId("b"));c.beginAdd(printing());c.undo();c.confirmBulkPreview();assertEquals(1,f.ids);assertEquals(1,f.calls.size);assertNull(c.state.value.form)
        c.refresh();runCurrent();assertTrue(c.state.value.mutationBusy);gate.complete(Unit);advanceUntilIdle();assertFalse(c.state.value.pending);assertEquals(Revision(8),c.state.value.savedRevision)
    }
    @Test fun inventoryChangedDuringPreviewAndConfirmationCannotPublishOrSubmitOldOriginals()=runTest {
        for(confirm in listOf(false,true)) {
            val f=Fixture();val c=open(f,listOf("a"));c.bulkDraft(finishDraft())
            if(confirm){c.previewBulkEdit();advanceUntilIdle()}
            val gate=CompletableDeferred<Unit>();f.ignoreCancellation=true;f.catalogGate=gate
            if(confirm)c.confirmBulkPreview()else c.previewBulkEdit();runCurrent()
            f.stored=f.stored.copy(revision=Revision(8));c.refresh();runCurrent();gate.complete(Unit);advanceUntilIdle()
            assertNull(c.state.value.bulkPreview);assertEquals(0,f.ids);assertTrue(f.calls.isEmpty());assertFalse(c.state.value.pending);assertFalse(c.state.value.mutationBusy)
            assertEquals(Revision(7),c.state.value.bulkForm!!.base.revision);assertEquals(Revision(8),c.state.value.inventory!!.revision)
        }
    }
    @Test fun eachDistinctKnownPrintingIsReadOnceAndLanguageIsCheckedForEachLot()=runTest {
        val f=Fixture();val other=printing("other");f.records[other.id]=other
        f.stored=f.stored.copy(lots=SnapshotList.of(lot("a"),lot("b").copy(identity=lot("b").identity.copy(printingId=Knowledge.Known(other.id))),lot("c",false)))
        val c=open(f,listOf("a","b"));c.bulkDraft(finishDraft());c.previewBulkEdit();advanceUntilIdle();assertFalse(c.state.value.bulkPreview!!.blocked);assertEquals(listOf(printing().id,other.id),f.catalogCalls)
        f.stored=f.stored.copy(revision=Revision(8),lots=SnapshotList.of(lot("a"),lot("b").copy(identity=lot("b").identity.copy(language=LanguageTag("ja")))))
        c.refresh();advanceUntilIdle();c.useRefreshedBulkInventory();f.catalogCalls.clear();c.previewBulkEdit();advanceUntilIdle();assertEquals(1,f.catalogCalls.size);assertFalse(c.state.value.bulkPreview!!.rows.first().blocked);assertTrue(c.state.value.bulkPreview!!.rows.last().blocked)
    }
    @Test fun explicitChangedDraftAndFreshPreviewClearAnObsoleteBackendRejection()=runTest {
        val f=Fixture();val c=open(f,listOf("a"));c.bulkDraft(CollectionBulkDraft(quantity=BulkValue.Set("0")));c.previewBulkEdit();advanceUntilIdle()
        val issues=SnapshotList.of(DomainIssue(InventoryIssueCodes.QUANTITY_RANGE,IssueSeverity.ERROR,"Synthetic row rejection",EntityId("a"),SnapshotList.empty()))
        f.result=Outcome.Success(InventoryApplyResult.Rejected(issues,f.stored.revision));c.confirmBulkPreview();advanceUntilIdle();assertEquals(issues,c.state.value.issues);assertEquals(f.original,f.stored)
        c.bulkDraft(CollectionBulkDraft(location=BulkValue.Set("revised")));assertTrue(c.state.value.issues.isEmpty());c.previewBulkEdit();advanceUntilIdle();assertFalse(c.state.value.bulkPreview!!.blocked);assertTrue(c.state.value.issues.isEmpty())
    }
    @Test fun wrongSuccessfulBulkDeltaIsUncertainEvenWhenRevisionAndSizesLookValid()=runTest {
        val f=Fixture();val c=open(f,listOf("a"));c.bulkDraft(CollectionBulkDraft(quantity=BulkValue.Set("0")));c.previewBulkEdit();advanceUntilIdle()
        f.result=Outcome.Success(InventoryApplyResult.Committed(InventoryCommit(c.state.value.bulkPreview!!.projection!!.copy(changes=SnapshotList.empty()),UtcTimestamp(8))))
        c.confirmBulkPreview();advanceUntilIdle();assertTrue(c.state.value.pending);assertEquals(Problem.CORRUPT_DATA,c.state.value.mutationProblem);assertNull(c.state.value.savedRevision)
    }
    @Test fun missingSelectedIdSurvivesRebaseDiscardAndFurtherToggleWithoutCrashOrSilentExclusion()=runTest {
        val f=Fixture();val c=open(f);c.bulkDraft(CollectionBulkDraft(location=BulkValue.Set("intent")))
        f.stored=f.stored.copy(revision=Revision(8),lots=SnapshotList.copyOf(f.stored.lots.filter{it.id!=EntityId("b")}));c.refresh();advanceUntilIdle();c.useRefreshedBulkInventory()
        c.closeBulkForm();assertTrue(c.state.value.bulkDiscardPrompt);c.discardBulkForm();assertEquals(listOf(EntityId("b")),c.missingBulkIds())
        c.toggleBulkLot(EntityId("c"));assertEquals(listOf(EntityId("a"),EntityId("b")),c.state.value.bulkIds);assertEquals(listOf(EntityId("b")),c.missingBulkIds())
        c.toggleBulkLot(EntityId("a"));c.toggleBulkLot(EntityId("c"),true);assertEquals(listOf(EntityId("a"),EntityId("c"),EntityId("b")),c.state.value.bulkIds)
        c.beginBulkEdit();c.bulkDraft(CollectionBulkDraft(quantity=BulkValue.Set("0")));c.previewBulkEdit();advanceUntilIdle();assertTrue(c.state.value.bulkPreview!!.rows.first{it.id==EntityId("b")}.blocked);assertTrue(f.calls.isEmpty());assertEquals(0,f.ids)
    }
    @Test fun cancellationBeforeSubmissionCreatesNoRequestButAfterSubmissionRetainsUncertainty()=runTest {
        val f=Fixture();val owner=ownerScope();val c=controller(f,owner);c.ensureLoaded();advanceUntilIdle();c.beginBulkSelection();c.toggleBulkLot(EntityId("a"));c.beginBulkEdit();c.bulkDraft(finishDraft());c.previewBulkEdit();advanceUntilIdle()
        val gate=CompletableDeferred<Unit>();f.catalogGate=gate;c.confirmBulkPreview();runCurrent();owner.cancel();advanceUntilIdle();assertEquals(0,f.ids);assertFalse(c.state.value.pending)
        val g=Fixture();val other=ownerScope();val d=controller(g,other);d.ensureLoaded();advanceUntilIdle();d.beginBulkSelection();d.toggleBulkLot(EntityId("a"));d.beginBulkEdit();d.bulkDraft(CollectionBulkDraft(quantity=BulkValue.Set("0")));d.previewBulkEdit();advanceUntilIdle();g.applyGate=CompletableDeferred();d.confirmBulkPreview();runCurrent();other.cancel();advanceUntilIdle();assertTrue(d.state.value.pending);assertTrue(d.state.value.uncertain);assertEquals(1,g.calls.size)
    }
}
