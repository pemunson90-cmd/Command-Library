@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.syntheticProvenance
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Bounded synthetic port responses test presentation only, never backend/reducer acceptance. */
class CollectionControllerTest {
    private val unresolved = OwnedLot(EntityId("a"),CardIdentity(Knowledge.Unknown("oracle unresolved",SnapshotList.of(Evidence("original evidence",null,null))),
        Knowledge.Absent("unresolved printing"),ProviderIdentity(ProviderId("foreign"),"card","original raw"),Knowledge.Known("Foreign card"),LanguageTag("ja")),
        Knowledge.Unknown("finish unresolved",SnapshotList.empty()),CardCondition.UNKNOWN,"original shelf",2,"original notes",Revision(1),Knowledge.Absent("not recorded"))
    private fun printing() = PrintingRecord(CardId(ProviderId("cards"),"printing"),Knowledge.Known(OracleId(ProviderId("cards"),"oracle")),"tst",Knowledge.Known("Test set"),"1",
        LanguageTag.ENGLISH,Knowledge.Known("common"),Knowledge.Known(SnapshotList.of(Finish.NONFOIL)),Knowledge.Absent("no art"),
        SnapshotList.empty(),SnapshotList.empty(),SnapshotList.empty(),SnapshotList.empty(),SnapshotList.empty(),syntheticProvenance(GenerationId("collection-test")))
    private fun deck(id:String="deck", revision:Long=2) = DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE,id),"Deck $id",Knowledge.Absent("none"),"",Knowledge.Absent("none"),SnapshotList.empty(),
        UtcTimestamp(1),UtcTimestamp(2),Revision(revision),SnapshotList.of(DeckEntry(EntityId("entry-$id"),unresolved.identity,5,BoardRole.MAIN,unresolved.finish,"",SnapshotList.empty(),SnapshotList.empty())),
        Knowledge.Absent("none"),null,SnapshotList.empty(),SnapshotList.empty())
    private fun failure(code:FailureCode) = Outcome.Failure(DomainFailure(code,"Synthetic failure",SnapshotList.empty()))
    private inner class Fixture : LocalInventory, LocalDeckLibrary, CatalogPrintingLookup, OwnedQuantityAllocator {
        var stored=InventorySnapshot(Revision(3),UtcTimestamp(3),SnapshotList.of(unresolved,unresolved.copy(id=EntityId("b"))),true)
        var currentPrinting=printing()
        var ids=0
        val calls=mutableListOf<Triple<CollectionPatch,Revision,EntityId>>()
        val undos=mutableListOf<Pair<Revision,EntityId>>()
        val receipts=mutableMapOf<EntityId,InventoryCommit>()
        val projected=mutableListOf<CollectionPatch>()
        val catalogCalls=mutableListOf<CardId>()
        val allocations=mutableListOf<Triple<DeckSnapshot,InventorySnapshot,OwnedQuantityPolicy>>()
        val loadedDecks=mutableListOf<DeckId>()
        var snapshotCalls=0
        var snapshotGate:CompletableDeferred<Unit>?=null
        var catalogGate:CompletableDeferred<Unit>?=null
        var mutationGate:CompletableDeferred<Unit>?=null
        var deckGate:CompletableDeferred<Unit>?=null
        var ignoreReadCancellation=false
        var response:Outcome<InventoryApplyResult>?=null
        var commitBeforeResponse=true
        var snapshotResponse:Outcome<InventorySnapshot>?=null
        var catalogResponse:Outcome<PrintingRecord>?=null
        var projectionResponse:Outcome<InventoryProjection>?=null
        var allocationResponse:Outcome<OwnedQuantityPlan>?=null
        var onAllocate:(()->Unit)?=null
        var uncertainDemand=true
        var deckRevision=2L
        var pageCursor:String?=null
        var wrongCommit=false
        var undoResponse:Outcome<InventoryCommit>?=null
        override suspend fun snapshot(cancel:CancelToken):Outcome<InventorySnapshot> {
            snapshotCalls++;val value=snapshotResponse?:Outcome.Success(stored);val gate=snapshotGate
            if(ignoreReadCancellation)withContext(NonCancellable){gate?.await()} else gate?.await()
            return value
        }
        fun projection(base:InventorySnapshot,patch:CollectionPatch):InventoryProjection {
            val next=Revision(base.revision.value+1)
            val changes=patch.edits.map { edit -> when(edit) {
                is CollectionEdit.Add -> InventoryLotChange(edit.lot.id,null,edit.lot.copy(revision=next))
                is CollectionEdit.Replace -> InventoryLotChange(edit.lot.id,base.lots.first{it.id==edit.lot.id},edit.lot.copy(revision=next))
                is CollectionEdit.Remove -> InventoryLotChange(edit.lotId,base.lots.first{it.id==edit.lotId},null)
                is CollectionEdit.Adjust -> error("The presentation form never sends Adjust")
            } }
            return InventoryProjection(base.revision,next,SnapshotList.copyOf(changes),SnapshotList.empty(),SnapshotList.empty())
        }
        override suspend fun apply(patch:CollectionPatch,expected:Revision,operationId:EntityId,cancel:CancelToken):Outcome<InventoryApplyResult> {
            calls+=Triple(patch,expected,operationId)
            if(!commitBeforeResponse && response!=null)return response!!
            val commit=receipts.getOrPut(operationId){
                val delta=projection(stored,patch)
                val changed=stored.lots.toMutableList()
                for(row in delta.changes){changed.removeAll{it.id==row.lotId};row.after?.let{changed.add(it)}}
                stored=stored.copy(revision=delta.resultRevision,lots=SnapshotList.copyOf(changed),capturedAt=UtcTimestamp(4))
                InventoryCommit(delta,UtcTimestamp(4))
            }
            mutationGate?.await()
            return response?:Outcome.Success(InventoryApplyResult.Committed(if(wrongCommit)commit.copy(projection=commit.projection.copy(sourceRevision=Revision(0)))else commit))
        }
        override suspend fun undo(expected:Revision,operationId:EntityId,cancel:CancelToken):Outcome<InventoryCommit> {
            undos+=expected to operationId
            val receipt=receipts.getOrPut(operationId){
                val before=stored.lots.first{it.id==unresolved.id};val next=Revision(expected.value+1)
                val restored=unresolved.copy(revision=next)
                // Faithful stored last-modification time must match the undo receipt's time.
                stored=stored.copy(revision=next,capturedAt=UtcTimestamp(5),lots=SnapshotList.copyOf(stored.lots.filter{it.id!=restored.id}+restored))
                InventoryCommit(InventoryProjection(expected,next,SnapshotList.of(InventoryLotChange(before.id,before,restored)),SnapshotList.empty(),SnapshotList.empty()),UtcTimestamp(5))
            }
            mutationGate?.await()
            undoResponse?.let { return it }
            val r=response
            return when(r){is Outcome.Failure->r;is Outcome.Unknown->r;is Outcome.Unavailable->r;else->Outcome.Success(receipt)}
        }
        override suspend fun printing(id:CardId,cancel:CancelToken):Outcome<PrintingRecord> {
            catalogCalls+=id;val result=catalogResponse?:Outcome.Success(currentPrinting);val gate=catalogGate
            if(ignoreReadCancellation)withContext(NonCancellable){gate?.await()}else gate?.await()
            return result
        }
        override suspend fun printings(id:OracleId,page:PageRequest,cancel:CancelToken):Outcome<Page<PrintingRecord>> = error("No catalog page in this workflow")
        override suspend fun list(page:PageRequest,cancel:CancelToken):Outcome<Page<DeckSummary>> = Outcome.Success(Page(SnapshotList.of(DeckSummary(deck().id,"Stored deck",Revision(deckRevision),UtcTimestamp(1),UtcTimestamp(2),1,5)),pageCursor,SnapshotReference(LocalDeckLibraryLimits.SOURCE,null,Revision(2),null)))
        override suspend fun load(id:DeckId,cancel:CancelToken):Outcome<DeckSnapshot> {
            loadedDecks+=id;val value=deck(id.raw,deckRevision);val gate=deckGate
            if(ignoreReadCancellation)withContext(NonCancellable){gate?.await()}else gate?.await()
            return Outcome.Success(value)
        }
        override suspend fun create(initial:DeckSnapshot,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> = error("No deck writes")
        override suspend fun duplicate(id:DeckId,expected:Revision,name:String,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> = error("No deck writes")
        override fun allocate(deck:DeckSnapshot,inventory:InventorySnapshot,policy:OwnedQuantityPolicy,cancel:CancelToken):Outcome<OwnedQuantityPlan> {
            allocations+=Triple(deck,inventory,policy);onAllocate?.invoke()
            return allocationResponse?:Outcome.Success(OwnedQuantityPlan(deck.id,deck.revision,inventory.revision,policy,SnapshotList.empty(),
                SnapshotList.of(OwnedQuantityDemand(deck.entries.single().id,5,0,5,!uncertainDemand)),
                if(uncertainDemand)SnapshotList.of(deck.entries.single().id)else SnapshotList.empty(),!uncertainDemand))
        }
    }
    private fun TestScope.controller(f:Fixture, owner:CoroutineScope=ownerScope(), gate:InventoryMutationGate=InventoryMutationGate())=CollectionController(owner,f,f,f,f,{base,patch,_,cancel->
        cancel.throwIfCancelled();f.projected+=patch;f.projectionResponse?:Outcome.Success(f.projection(base,patch))
    },StandardTestDispatcher(testScheduler),{EntityId("generated-${++f.ids}")},inventoryMutationGate=gate)
    private fun TestScope.loaded(f:Fixture)=controller(f).also{it.ensureLoaded();advanceUntilIdle()}
    private fun TestScope.preview(c:CollectionController,id:EntityId=unresolved.id,remove:Boolean=false){c.beginEdit(id,remove);c.previewEdit();advanceUntilIdle()}

    @Test fun externalCommitFloorPreservesRawAndRefusesOldReadsRebaseAndNewOperationIds()=runTest {
        val f=Fixture();val c=loaded(f)
        c.beginEdit(unresolved.id);c.notes("Keep this exact raw input");c.previewEdit();advanceUntilIdle()
        assertNotNull(c.state.value.preview)
        val notice=f.stored.copy(revision=Revision(9),capturedAt=UtcTimestamp(9))
        c.onInventoryChanged(notice);advanceUntilIdle()
        assertEquals(Revision(3),c.state.value.inventory!!.revision)
        assertNull(c.state.value.preview);assertEquals("Keep this exact raw input",c.state.value.form!!.notes)
        c.useRefreshedInventory();c.previewEdit();c.confirmPreview();c.undo();advanceUntilIdle()
        assertEquals(0,f.ids);assertTrue(f.calls.isEmpty());assertTrue(f.undos.isEmpty())
        assertEquals(Problem.STALE_REVISION,c.state.value.formProblem)
        f.snapshotResponse=Outcome.Success(notice.copy(capturedAt=UtcTimestamp(8)))
        c.refresh();advanceUntilIdle();assertEquals(Revision(3),c.state.value.inventory!!.revision)
        f.snapshotResponse=Outcome.Success(notice.copy(revision=Revision(10),capturedAt=UtcTimestamp(10)))
        c.refresh();advanceUntilIdle();c.useRefreshedInventory()
        assertEquals(Revision(10),c.state.value.form!!.base.revision)
        assertEquals("Keep this exact raw input",c.state.value.form!!.notes)
    }

    @Test fun externalNoticeDuringCatalogLookupSettlesLoadingWithoutStaleFormOrId()=runTest {
        val f=Fixture();val c=loaded(f);val blocked=CompletableDeferred<Unit>();f.catalogGate=blocked
        c.beginAdd(f.currentPrinting);runCurrent();assertTrue(c.state.value.checkingCatalog)
        f.stored=f.stored.copy(revision=Revision(9),capturedAt=UtcTimestamp(9))
        c.onInventoryChanged(f.stored);advanceUntilIdle()
        assertFalse(c.state.value.checkingCatalog);assertNull(c.state.value.form);assertEquals(0,f.ids)
        blocked.complete(Unit);advanceUntilIdle();assertNull(c.state.value.form)
        f.catalogGate=null;c.beginAdd(f.currentPrinting);advanceUntilIdle()
        assertNotNull(c.state.value.form);assertEquals(Revision(9),c.state.value.form!!.base.revision)
        assertEquals(1,f.ids)
    }

    @Test fun externalNoticeDuringAllocationRefusesOldPublicationAndDuplicateNoticeDoesNotReread()=runTest {
        val f=Fixture();val c=loaded(f);c.selectDeck(deck().id);advanceUntilIdle()
        val notice=f.stored.copy(revision=Revision(9),capturedAt=UtcTimestamp(9))
        f.onAllocate={ c.onInventoryChanged(notice) }
        c.calculateAllocation();advanceUntilIdle()
        assertNull(c.state.value.allocation);assertEquals(Revision(3),c.state.value.inventory!!.revision)
        val reads=f.snapshotCalls;c.onInventoryChanged(notice);advanceUntilIdle();assertEquals(reads,f.snapshotCalls)
        f.onAllocate=null;f.snapshotResponse=Outcome.Success(notice)
        c.calculateAllocation();advanceUntilIdle()
        assertEquals(Revision(9),c.state.value.inventory!!.revision)
        assertEquals(Revision(9),c.state.value.allocation!!.inventoryRevision)
    }

    @Test fun externalFloorCannotReleaseKnownCommitUntilNewEnoughReadAndRetryRemainsReadonly()=runTest {
        val f=Fixture();val gate=InventoryMutationGate();val c=controller(f,gate=gate)
        c.ensureLoaded();advanceUntilIdle();preview(c)
        f.snapshotResponse=Outcome.Unknown("Read unavailable",SnapshotList.empty())
        c.confirmPreview();advanceUntilIdle();val frozen=f.calls.single();val ids=f.ids
        c.onInventoryChanged(f.stored.copy(revision=Revision(9),capturedAt=UtcTimestamp(9)));advanceUntilIdle()
        f.snapshotResponse=Outcome.Success(f.stored);c.retry();advanceUntilIdle()
        assertTrue(gate.busy.value);assertTrue(c.state.value.awaitingFresh)
        assertEquals(listOf(frozen),f.calls);assertEquals(ids,f.ids)
        f.snapshotResponse=Outcome.Success(f.stored.copy(revision=Revision(10),capturedAt=UtcTimestamp(10)))
        c.retry();advanceUntilIdle();assertFalse(gate.busy.value)
        assertEquals(Revision(10),c.state.value.inventory!!.revision);assertEquals(listOf(frozen),f.calls)
    }

    @Test fun externalNoticeDoesNotCancelSubmittedBulkAndUnknownRetainsExactReplay()=runTest {
        val f=Fixture();val gate=InventoryMutationGate();val c=controller(f,gate=gate)
        c.ensureLoaded();advanceUntilIdle();c.beginBulkSelection();advanceUntilIdle()
        c.toggleBulkLot(unresolved.id);c.beginBulkEdit()
        c.bulkDraft(CollectionBulkDraft(notes=BulkValue.Set("Retained submitted notes")))
        c.previewBulkEdit();advanceUntilIdle()
        val blocked=CompletableDeferred<Unit>();f.mutationGate=blocked
        f.response=Outcome.Unknown("Lost acknowledgement",SnapshotList.empty())
        c.confirmBulkPreview();runCurrent();val frozen=f.calls.single();val ids=f.ids
        c.onInventoryChanged(f.stored.copy(revision=Revision(9),capturedAt=UtcTimestamp(9)));runCurrent()
        assertTrue(c.state.value.mutationBusy);assertTrue(gate.busy.value)
        blocked.complete(Unit);advanceUntilIdle()
        assertTrue(c.state.value.uncertain);assertTrue(gate.busy.value)
        f.mutationGate=null;f.response=null;c.retry();advanceUntilIdle()
        assertEquals(listOf(frozen,frozen),f.calls);assertEquals(ids,f.ids)
        assertTrue(c.state.value.awaitingFresh);assertTrue(gate.busy.value)
        f.snapshotResponse=Outcome.Success(f.stored.copy(revision=Revision(10),capturedAt=UtcTimestamp(10)))
        c.retry();advanceUntilIdle();assertFalse(gate.busy.value)
        assertEquals(listOf(frozen,frozen),f.calls)
    }

    @Test fun loadExplicitPreviewSavePreservesUnknownMetadataAndZeroQuantity()=runTest {
        val f=Fixture();val c=loaded(f);c.beginEdit(unresolved.id);c.quantity("0");c.notes("changed notes");c.location("new shelf");c.condition(CardCondition.DAMAGED)
        assertTrue(f.calls.isEmpty());c.previewEdit();advanceUntilIdle();assertTrue(f.calls.isEmpty());assertNotNull(c.state.value.preview)
        val lot=(f.projected.single().edits.single() as CollectionEdit.Replace).lot
        assertEquals(unresolved.identity,lot.identity);assertEquals(unresolved.finish,lot.finish);assertEquals(unresolved.acquiredAt,lot.acquiredAt)
        assertEquals(0,lot.quantity);assertEquals(CardCondition.DAMAGED,lot.condition);c.confirmPreview();advanceUntilIdle()
        assertEquals(Revision(3),f.calls.single().second);assertEquals(Revision(4),c.state.value.inventory!!.revision)
        assertEquals(Revision(4),c.state.value.savedRevision);assertNull(c.state.value.form);assertFalse(c.state.value.pending);assertEquals(2,f.snapshotCalls)
    }
    @Test fun invalidAndCancelledPreviewNeverWritesOrGuessesQuantity()=runTest {
        val f=Fixture();val c=loaded(f);c.beginEdit(unresolved.id)
        for(q in listOf("","-1","2147483648","1.5","9".repeat(1000))){c.quantity(q);c.previewEdit();advanceUntilIdle();assertTrue(c.state.value.invalidForm)}
        assertTrue(f.projected.isEmpty());assertTrue(f.calls.isEmpty())
        c.quantity("2");c.previewEdit();advanceUntilIdle();c.cancelPreview();c.confirmPreview();advanceUntilIdle();assertTrue(f.calls.isEmpty())
    }
    @Test fun canonicalPreviewIssuesAndBackendRejectionNeverAppearAsSaved()=runTest {
        val f=Fixture();val issue=DomainIssue(InventoryIssueCodes.QUANTITY_OVERFLOW,IssueSeverity.ERROR,"Synthetic",unresolved.id,SnapshotList.empty())
        f.projectionResponse=Outcome.Success(InventoryProjection(Revision(3),Revision(4),SnapshotList.empty(),SnapshotList.empty(),SnapshotList.of(issue)))
        val c=loaded(f);preview(c);c.confirmPreview();advanceUntilIdle();assertTrue(f.calls.isEmpty());assertEquals(SnapshotList.of(issue),c.state.value.issues)
        f.projectionResponse=null;c.cancelPreview();c.previewEdit();advanceUntilIdle();f.commitBeforeResponse=false
        f.response=Outcome.Success(InventoryApplyResult.Rejected(SnapshotList.of(issue),Revision(3)));c.confirmPreview();advanceUntilIdle()
        assertNull(c.state.value.savedRevision);assertFalse(c.state.value.pending);assertTrue(c.state.value.preview==null);assertEquals(1,f.calls.size);assertEquals(SnapshotList.of(issue),c.state.value.issues)
    }
    @Test fun unknownAcknowledgementLatchesAcrossFailureAndRejectedResponsesAndRetriesExactRequestAfterClose()=runTest {
        val f=Fixture();val c=loaded(f);preview(c);f.response=Outcome.Unknown("Synthetic acknowledgement",SnapshotList.empty());c.confirmPreview();advanceUntilIdle()
        val request=f.calls.single();assertTrue(c.state.value.pending);assertTrue(c.state.value.uncertain);c.requestCloseForm();assertNull(c.state.value.form)
        c.selectLot(EntityId("b"));c.beginEdit(EntityId("b"));assertNull(c.state.value.form)
        f.response=failure(FailureCode.STALE_REVISION);c.retry();advanceUntilIdle();assertTrue(c.state.value.pending)
        f.response=Outcome.Success(InventoryApplyResult.Rejected(SnapshotList.empty(),Revision(4)));c.retry();advanceUntilIdle();assertTrue(c.state.value.pending)
        f.response=null;f.stored=f.stored.copy(revision=Revision(9),lots=SnapshotList.of(unresolved.copy(id=EntityId("b"),notes="newer actual head",revision=Revision(9))))
        c.retry();advanceUntilIdle()
        assertEquals(List(4){request},f.calls);assertEquals(1,f.ids);assertFalse(c.state.value.pending)
        assertEquals(Revision(9),c.state.value.inventory!!.revision);assertEquals("newer actual head",c.state.value.inventory!!.lots.single().notes);assertEquals(EntityId("b"),c.state.value.selectedLotId)
    }
    @Test fun allMutationCommandsSerializeAndUndoRetriesItsOwnFrozenOperation()=runTest {
        val f=Fixture();val c=loaded(f);preview(c);f.mutationGate=CompletableDeferred();c.confirmPreview();runCurrent()
        c.confirmPreview();c.undo();c.retry();c.beginAdd(printing());assertEquals(1,f.calls.size);assertEquals(1,f.ids)
        f.mutationGate!!.complete(Unit);advanceUntilIdle();f.mutationGate=null;f.response=failure(FailureCode.PERSISTENCE)
        c.undo();advanceUntilIdle();val frozen=f.undos.single();assertTrue(c.state.value.pending)
        f.response=null;c.retry();advanceUntilIdle();assertEquals(listOf(frozen,frozen),f.undos);assertFalse(c.state.value.pending);assertEquals(2,f.ids)
    }
    @Test fun definitiveFailureAllowsExplicitResubmissionButUnknownCanOnlyBeAbandonedWithExplicitCommand()=runTest {
        val f=Fixture();val c=loaded(f);preview(c);f.commitBeforeResponse=false;f.response=failure(FailureCode.STALE_REVISION)
        c.confirmPreview();advanceUntilIdle();assertFalse(c.state.value.pending);assertNotNull(c.state.value.form)
        f.commitBeforeResponse=true;f.response=failure(FailureCode.PERSISTENCE);c.confirmPreview();advanceUntilIdle();assertTrue(c.state.value.pending)
        c.requestCloseForm();assertTrue(c.state.value.pending);c.abandonConfirmed();advanceUntilIdle();assertFalse(c.state.value.pending);assertNull(c.state.value.form)
        assertEquals(2,f.calls.size)
    }
    @Test fun actualCatalogRecordIsRereadAndOnlyProvenFinishesCanBeChosen()=runTest {
        val f=Fixture();val c=loaded(f);c.beginAdd(printing());advanceUntilIdle();assertEquals(listOf(printing().id),f.catalogCalls);assertEquals(1,f.ids)
        assertNull(c.state.value.form!!.finish);c.finish(Finish.FOIL);c.previewEdit();advanceUntilIdle();assertTrue(c.state.value.invalidForm)
        c.finish(Finish.NONFOIL);c.quantity("0");c.previewEdit();advanceUntilIdle();assertTrue(f.calls.isEmpty())
        val lot=(f.projected.single().edits.single() as CollectionEdit.Add).lot
        assertEquals(printing().id,(lot.identity.printingId as Knowledge.Known).value);assertEquals(printing().oracleId,lot.identity.oracleId);assertEquals(printing().language,lot.identity.language)
        assertNull(lot.identity.raw);assertTrue(lot.identity.originalName is Knowledge.Absent);assertEquals(Knowledge.Known(Finish.NONFOIL),lot.finish);assertEquals(0,lot.quantity)
        c.confirmPreview();advanceUntilIdle();assertEquals(2,f.catalogCalls.size);assertEquals(2,f.ids);assertEquals(1,f.calls.size)
    }
    @Test fun unknownAbsentOrUnsupportedCurrentFinishesNeverOpenAnAddOrAllocateAnId()=runTest {
        for(finishes in listOf<Knowledge<SnapshotList<Finish>>>(Knowledge.Unknown("unknown",SnapshotList.empty()),Knowledge.Absent("none"),Knowledge.Known(SnapshotList.empty()))){
            val f=Fixture();f.currentPrinting=f.currentPrinting.copy(finishes=finishes);val c=loaded(f);c.beginAdd(f.currentPrinting);advanceUntilIdle()
            assertNull(c.state.value.form);assertEquals(Problem.UNSUPPORTED,c.state.value.formProblem);assertEquals(0,f.ids);assertTrue(f.calls.isEmpty())
        }
    }
    @Test fun changedPrintingLanguageIdentityAndSupportAreRejectedAtOpenAndBeforeConfirmation()=runTest {
        val variants=listOf<(PrintingRecord)->PrintingRecord>({it.copy(id=CardId(it.id.source,"other"))},{it.copy(language=LanguageTag("fr"))},{it.copy(oracleId=Knowledge.Absent("changed"))})
        for(variant in variants){val f=Fixture();val c=loaded(f);f.currentPrinting=variant(printing());c.beginAdd(printing());advanceUntilIdle();assertNull(c.state.value.form);assertEquals(Problem.STALE_GENERATION,c.state.value.formProblem)}
        val f=Fixture();val c=loaded(f);c.beginAdd(printing());advanceUntilIdle();c.finish(Finish.NONFOIL);c.previewEdit();advanceUntilIdle()
        f.currentPrinting=f.currentPrinting.copy(finishes=Knowledge.Known(SnapshotList.of(Finish.FOIL)));c.confirmPreview();advanceUntilIdle()
        assertEquals(Problem.STALE_GENERATION,c.state.value.formProblem);assertTrue(f.calls.isEmpty());assertEquals(1,f.ids);assertFalse(c.state.value.mutationBusy)
    }
    @Test fun changedCatalogDoesNotBlockExactReconciliationOfAnAlreadySubmittedAdd()=runTest {
        val f=Fixture();val c=loaded(f);c.beginAdd(printing());advanceUntilIdle();c.finish(Finish.NONFOIL);c.previewEdit();advanceUntilIdle()
        f.response=failure(FailureCode.PERSISTENCE);c.confirmPreview();advanceUntilIdle();val frozen=f.calls.single();val reads=f.catalogCalls.size
        f.catalogResponse=Outcome.Unavailable(UnavailableReason.NOT_FOUND,"Now missing");f.response=null;c.retry();advanceUntilIdle()
        assertEquals(listOf(frozen,frozen),f.calls);assertEquals(reads,f.catalogCalls.size);assertFalse(c.state.value.pending)
    }
    @Test fun lateReadsAndCatalogResponsesCannotOverwriteNewerSelectionOrReopenDiscardedForm()=runTest {
        val f=Fixture();val c=loaded(f);f.ignoreReadCancellation=true;val old=CompletableDeferred<Unit>();f.snapshotGate=old;c.refresh();runCurrent()
        f.stored=f.stored.copy(revision=Revision(8));f.snapshotGate=null;c.refresh();advanceUntilIdle();c.selectLot(EntityId("b"));old.complete(Unit);advanceUntilIdle()
        assertEquals(Revision(8),c.state.value.inventory!!.revision);assertEquals(EntityId("b"),c.state.value.selectedLotId)
        val catalog=CompletableDeferred<Unit>();f.catalogGate=catalog;c.beginAdd(printing());runCurrent();c.discardForm();catalog.complete(Unit);advanceUntilIdle()
        assertNull(c.state.value.form);assertFalse(c.state.value.checkingCatalog);assertEquals(0,f.ids)
    }
    @Test fun refreshedBeforeImageRequiresExplicitRebaseAndKeepsDraftValues()=runTest {
        val f=Fixture();val c=loaded(f);c.beginEdit(unresolved.id);c.notes("draft notes");c.quantity("7")
        f.stored=f.stored.copy(revision=Revision(7),lots=SnapshotList.of(unresolved.copy(revision=Revision(6),location="external shelf")))
        c.refresh();advanceUntilIdle();c.previewEdit();assertEquals(Problem.STALE_REVISION,c.state.value.formProblem);assertEquals("draft notes",c.state.value.form!!.notes)
        c.useRefreshedInventory();c.previewEdit();advanceUntilIdle();val replacement=(f.projected.single().edits.single() as CollectionEdit.Replace).lot
        assertEquals(Revision(6),replacement.revision);assertEquals("draft notes",replacement.notes);assertEquals(7,replacement.quantity);assertEquals(Revision(7),c.state.value.preview!!.sourceRevision)
    }
    @Test fun discardPromptAndRemoveAreExplicitAndPreserveCompleteBeforeImage()=runTest {
        val f=Fixture();val c=loaded(f);c.beginEdit(unresolved.id);c.notes("draft");c.requestCloseForm();assertTrue(c.state.value.discardPrompt);assertNotNull(c.state.value.form)
        c.keepForm();assertFalse(c.state.value.discardPrompt);c.discardForm();assertNull(c.state.value.form);assertTrue(f.calls.isEmpty())
        preview(c,remove=true);assertEquals(unresolved,c.state.value.preview!!.changes.single().before);assertNull(c.state.value.preview!!.changes.single().after)
        c.confirmPreview();advanceUntilIdle();assertFalse(c.state.value.inventory!!.lots.any{it.id==unresolved.id})
    }
    @Test fun malformedCommitIsUncertainAndMissingOrIncompleteSnapshotsFailExplicitly()=runTest {
        val f=Fixture();val c=loaded(f);preview(c);f.wrongCommit=true;c.confirmPreview();advanceUntilIdle()
        assertTrue(c.state.value.pending);assertEquals(Problem.CORRUPT_DATA,c.state.value.mutationProblem);assertNull(c.state.value.savedRevision)
        val other=Fixture();other.snapshotResponse=Outcome.Success(other.stored.copy(complete=false));val empty=loaded(other)
        assertNull(empty.state.value.inventory);assertEquals(Problem.CORRUPT_DATA,empty.state.value.loadProblem)
    }
    @Test fun cancellationAfterSubmittedMutationLatchesRequestInsteadOfClaimingRollback()=runTest {
        val f=Fixture();val owner=ownerScope();val c=controller(f,owner);c.refresh();advanceUntilIdle();preview(c)
        f.mutationGate=CompletableDeferred();c.confirmPreview();runCurrent();owner.cancel();advanceUntilIdle()
        assertEquals(1,f.calls.size);assertTrue(c.state.value.pending);assertTrue(c.state.value.uncertain);assertFalse(c.state.value.mutationBusy);assertEquals(Revision(4),f.stored.revision)
    }
    @Test fun freshStoredDeckAllocationCarriesRevisionsUncertaintyAndExplicitBoardPolicy()=runTest {
        val f=Fixture();val c=loaded(f);c.selectDeck(deck().id);advanceUntilIdle()
        val first=f.allocations.single();assertEquals(Revision(2),first.first.revision);assertEquals(Revision(3),first.second.revision)
        assertEquals(OwnershipPolicy.SAME_ORACLE,first.third.ownership);assertEquals(SnapshotList.of(BoardRole.MAIN),first.third.includeBoards);assertTrue(first.third.pins.isEmpty())
        assertFalse(c.state.value.allocation!!.demands.single().complete);assertEquals(5,c.state.value.allocation!!.demands.single().remainingQuantity)
        c.includeBoard(BoardRole.SIDEBOARD,true);c.ownership(OwnershipPolicy.EXACT_PRINTING);assertNull(c.state.value.allocation)
        f.stored=f.stored.copy(revision=Revision(8));f.deckRevision=9;c.calculateAllocation();advanceUntilIdle()
        assertEquals(Revision(9),c.state.value.allocation!!.deckRevision);assertEquals(Revision(8),c.state.value.allocation!!.inventoryRevision)
        assertEquals(SnapshotList.of(BoardRole.MAIN,BoardRole.SIDEBOARD),f.allocations.last().third.includeBoards);assertEquals(2,f.loadedDecks.size)
    }
    @Test fun staleAllocationResponsesAndPolicyChangesCannotRestoreAnOldPlan()=runTest {
        val f=Fixture();val c=loaded(f);f.ignoreReadCancellation=true;val held=CompletableDeferred<Unit>();f.deckGate=held
        c.selectDeck(deck("old").id);runCurrent();f.deckGate=null;c.selectDeck(deck("new").id);advanceUntilIdle();held.complete(Unit);advanceUntilIdle()
        assertEquals(deck("new").id,c.state.value.selectedDeckId);assertEquals(deck("new").id,c.state.value.allocation!!.deckId)
        f.onAllocate={c.includeBoard(BoardRole.MAYBEBOARD,true)};c.calculateAllocation();advanceUntilIdle();assertNull(c.state.value.allocation)
    }
    @Test fun allocationLimitsRemainExplicitAndMutationInvalidatesPlans()=runTest {
        val f=Fixture();val c=loaded(f);f.stored=f.stored.copy(lots=SnapshotList.copyOf(List(10_001){unresolved.copy(id=EntityId("lot-$it"))}))
        f.allocationResponse=failure(FailureCode.INVALID_INPUT);c.selectDeck(deck().id);advanceUntilIdle()
        assertEquals(10_001,f.allocations.single().second.lots.size);assertNull(c.state.value.allocation);assertEquals(Problem.INVALID_INPUT,c.state.value.allocationProblem)
        f.stored=f.stored.copy(lots=SnapshotList.of(unresolved));f.allocationResponse=null;c.calculateAllocation();advanceUntilIdle();assertNotNull(c.state.value.allocation)
        preview(c);c.confirmPreview();advanceUntilIdle();assertNull(c.state.value.allocation)
    }
    @Test fun refreshCompletingDuringAllocationCannotRegressSupplyOrPublishAnOlderPlan()=runTest {
        val f=Fixture();val c=loaded(f)
        val refresh=CompletableDeferred<Unit>();f.stored=f.stored.copy(revision=Revision(6));f.snapshotGate=refresh
        c.refresh();runCurrent()
        val allocation=CompletableDeferred<Unit>();f.snapshotGate=null;f.stored=f.stored.copy(revision=Revision(5));f.deckGate=allocation
        c.selectDeck(deck().id);runCurrent();assertTrue(c.state.value.allocating)
        refresh.complete(Unit);runCurrent();assertEquals(Revision(6),c.state.value.inventory!!.revision)
        allocation.complete(Unit);advanceUntilIdle()
        assertEquals(Revision(6),c.state.value.inventory!!.revision);assertNull(c.state.value.allocation);assertEquals(Problem.STALE_REVISION,c.state.value.allocationProblem)
    }
    @Test fun selectionChangedDuringConfirmationCatalogReadSurvivesAddAcknowledgement()=runTest {
        val f=Fixture();val c=loaded(f);c.beginAdd(printing());advanceUntilIdle();c.finish(Finish.NONFOIL);c.previewEdit();advanceUntilIdle()
        val gate=CompletableDeferred<Unit>();f.catalogGate=gate;c.confirmPreview();runCurrent()
        c.selectLot(EntityId("b"));gate.complete(Unit);advanceUntilIdle()
        assertEquals(EntityId("b"),c.state.value.selectedLotId);assertEquals(1,f.calls.size)
    }
    @Test fun acknowledgedCommitWithFailedReloadKeepsOldSnapshotAndReportsReadFailure()=runTest {
        val f=Fixture();val c=loaded(f);preview(c);f.snapshotResponse=failure(FailureCode.PERSISTENCE)
        c.confirmPreview();advanceUntilIdle()
        assertEquals(Revision(4),c.state.value.savedRevision);assertEquals(Revision(3),c.state.value.inventory!!.revision);assertEquals(Problem.PERSISTENCE,c.state.value.loadProblem)
    }

    @Test fun deckChangeFenceInvalidatesOnlyNewerSelectedDeckAndExplicitRecalculationLoadsCurrentHead()=runTest {
        val f=Fixture();f.deckRevision=5;val c=loaded(f);c.selectDeck(deck().id);advanceUntilIdle()
        val plan=c.state.value.allocation
        c.onDeckChanged(deck("other").id,Revision(20));c.onDeckChanged(deck().id,Revision(4));c.onDeckChanged(deck().id,Revision(5))
        assertSame(plan,c.state.value.allocation)
        c.onDeckChanged(deck().id,Revision(6));assertNull(c.state.value.allocation);assertNull(c.state.value.selectedDeck)
        c.calculateAllocation();advanceUntilIdle();assertNull(c.state.value.allocation);assertEquals(Problem.STALE_REVISION,c.state.value.allocationProblem)
        f.deckRevision=6;c.calculateAllocation();advanceUntilIdle();assertEquals(Revision(6),c.state.value.allocation!!.deckRevision)
        c.onDeckChanged(deck().id,Revision(0));assertNotNull(c.state.value.allocation)
    }
    @Test fun deckRevisionFenceSurvivesLateNoncooperativeLoadAndDoesNotGrowAcrossSelections()=runTest {
        val f=Fixture();val c=loaded(f);f.ignoreReadCancellation=true;val held=CompletableDeferred<Unit>();f.deckGate=held
        c.selectDeck(deck().id);runCurrent();c.onDeckChanged(deck().id,Revision(7));held.complete(Unit);advanceUntilIdle()
        assertNull(c.state.value.allocation);assertEquals(Problem.STALE_REVISION,c.state.value.allocationProblem)
        f.deckGate=null;f.deckRevision=1;c.selectDeck(deck("other").id);advanceUntilIdle()
        assertEquals(deck("other").id,c.state.value.allocation!!.deckId);assertEquals(Revision(1),c.state.value.allocation!!.deckRevision)
    }

    @Test fun directDetailAddLoadsCompleteInventoryBeforeCatalogAndRetainsIntentWithoutPriorCollectionVisit()=runTest {
        val f=Fixture();val c=controller(f);c.beginAdd(printing());advanceUntilIdle()
        assertNotNull(c.state.value.form);assertEquals(CollectionFormMode.ADD,c.state.value.form!!.mode)
        assertEquals(Revision(3),c.state.value.form!!.base.revision);assertEquals(listOf(printing().id),f.catalogCalls);assertEquals(1,f.snapshotCalls)
        assertEquals(1,f.ids);assertTrue(f.calls.isEmpty())
        val failed=Fixture();failed.snapshotResponse=failure(FailureCode.PERSISTENCE);val other=controller(failed)
        other.beginAdd(printing());advanceUntilIdle();assertNull(other.state.value.form);assertEquals(Problem.PERSISTENCE,other.state.value.formProblem)
        assertEquals(0,failed.ids);assertTrue(failed.catalogCalls.isEmpty())
    }
    @Test fun lateProjectionCannotRestorePreviewAfterInventoryRefresh()=runTest {
        val f=Fixture();val owner=ownerScope()
        val entered=CompletableDeferred<Unit>();val release=CountDownLatch(1)
        val raced=CollectionController(owner,f,f,f,f,{base,patch,_,_->
            entered.complete(Unit)
            check(release.await(30,TimeUnit.SECONDS)) { "Test failed to release projection" }
            Outcome.Success(f.projection(base,patch))
        },Dispatchers.Default,{EntityId("late-preview")})
        try {
            raced.refresh();raced.state.first{it.inventory!=null&&!it.loading}
            raced.beginEdit(unresolved.id);raced.previewEdit();entered.await()
            f.stored=f.stored.copy(revision=Revision(9));raced.refresh()
            raced.state.first{it.inventory?.revision==Revision(9)&&!it.loading}
            release.countDown();raced.state.first{!it.previewing}
            assertEquals(Revision(9),raced.state.value.inventory!!.revision);assertNull(raced.state.value.preview)
            assertEquals(Problem.STALE_REVISION,raced.state.value.formProblem);assertTrue(f.calls.isEmpty())
        } finally { release.countDown();owner.cancel() }
    }

    @Test fun sharedOwnerBlocksInitialApplyBulkAndUndoWithoutCreatingOperations()=runTest {
        val f=Fixture();val gate=InventoryMutationGate();val c=controller(f,gate=gate)
        c.ensureLoaded();advanceUntilIdle();preview(c)
        val other=checkNotNull(gate.tryAcquire())
        c.confirmPreview();advanceUntilIdle()
        assertTrue(f.calls.isEmpty());assertEquals(0,f.ids);assertNotNull(c.state.value.form)
        c.discardForm();c.undo();advanceUntilIdle()
        assertTrue(f.undos.isEmpty());assertEquals(0,f.ids)
        c.beginBulkSelection();advanceUntilIdle();c.toggleBulkLot(unresolved.id);c.beginBulkEdit()
        c.bulkDraft(CollectionBulkDraft(location=BulkValue.Set("Additional shelf")))
        c.previewBulkEdit();advanceUntilIdle();assertNotNull(c.state.value.bulkPreview)
        c.confirmBulkPreview();advanceUntilIdle()
        assertTrue(f.calls.isEmpty());assertEquals(0,f.ids);assertTrue(other.owns)
        other.release();c.confirmBulkPreview();advanceUntilIdle()
        assertEquals(1,f.calls.size);assertFalse(gate.busy.value)
    }

    @Test fun initialAddOwnsBeforeCatalogAwaitAndReleasesOnPreflightRefusal()=runTest {
        val f=Fixture();val gate=InventoryMutationGate();val c=controller(f,gate=gate)
        c.ensureLoaded();advanceUntilIdle();c.beginAdd(f.currentPrinting);advanceUntilIdle();c.finish(Finish.NONFOIL);c.previewEdit();advanceUntilIdle()
        val proofGate=CompletableDeferred<Unit>();f.catalogGate=proofGate
        f.catalogResponse=failure(FailureCode.STALE_GENERATION)
        c.confirmPreview()
        assertTrue(gate.busy.value);assertNull(gate.tryAcquire())
        runCurrent();assertTrue(f.calls.isEmpty());assertTrue(c.state.value.mutationBusy)
        proofGate.complete(Unit);advanceUntilIdle()
        assertFalse(gate.busy.value);assertFalse(c.hasMutationBarrier)
        assertEquals(Problem.STALE_GENERATION,c.state.value.formProblem)
        assertTrue(f.calls.isEmpty());assertEquals(1,f.ids)
    }

    @Test fun knownCommitWithFailedFreshReadKeepsOwnerAndRetriesReadsOnlyUntilActualNewerSnapshot()=runTest {
        val f=Fixture();val gate=InventoryMutationGate();val c=controller(f,gate=gate)
        c.ensureLoaded();advanceUntilIdle();preview(c);c.notes("Explicit content");c.previewEdit();advanceUntilIdle()
        f.snapshotResponse=Outcome.Unknown("Synthetic read acknowledgement",SnapshotList.empty())
        c.confirmPreview();advanceUntilIdle()
        val frozen=f.calls.single();val ids=f.ids
        assertTrue(gate.busy.value);assertTrue(c.state.value.awaitingFresh);assertTrue(c.state.value.pending)
        assertFalse(c.state.value.uncertain);assertEquals(Revision(4),c.state.value.savedRevision)
        assertEquals(Revision(3),c.state.value.inventory!!.revision)
        c.selectDeck(deck().id);advanceUntilIdle()
        val readsBeforeAllocation=f.snapshotCalls
        f.snapshotResponse=Outcome.Success(f.stored.copy(revision=Revision(3),capturedAt=UtcTimestamp(3)))
        c.calculateAllocation();advanceUntilIdle()
        assertEquals(readsBeforeAllocation,f.snapshotCalls);assertTrue(f.allocations.isEmpty())
        assertEquals(Revision(3),c.state.value.inventory!!.revision)
        f.snapshotResponse=Outcome.Unknown("Still unavailable",SnapshotList.empty())
        c.abandonConfirmed();c.undo();c.retry();advanceUntilIdle();c.retry();advanceUntilIdle()
        assertEquals(listOf(frozen),f.calls);assertTrue(f.undos.isEmpty());assertEquals(ids,f.ids)
        assertTrue(gate.busy.value);assertNull(gate.tryAcquire())
        f.stored=f.stored.copy(revision=Revision(9),capturedAt=UtcTimestamp(9),lots=SnapshotList.of(unresolved.copy(notes="Actual later inventory",revision=Revision(9))))
        f.snapshotResponse=null;c.retry();advanceUntilIdle()
        assertEquals(Revision(9),c.state.value.inventory!!.revision)
        assertEquals("Actual later inventory",c.state.value.inventory!!.lots.single().notes)
        assertEquals(Revision(4),c.state.value.savedRevision)
        assertFalse(c.state.value.pending);assertFalse(c.state.value.awaitingFresh);assertFalse(gate.busy.value)
        assertEquals(listOf(frozen),f.calls);assertEquals(ids,f.ids)
    }

    @Test fun exactSourceAndRevisionButForeignSingleApplyProjectionIsHeldAsMalformedAcknowledgement()=runTest {
        val f=Fixture();val gate=InventoryMutationGate();val c=controller(f,gate=gate)
        c.ensureLoaded();advanceUntilIdle();preview(c)
        val expected=f.projection(f.stored,f.projected.single())
        val changed=expected.changes.single()
        val malformed=expected.copy(changes=SnapshotList.of(changed.copy(after=checkNotNull(changed.after).copy(notes="Foreign acknowledgement content"))))
        f.commitBeforeResponse=false
        f.response=Outcome.Success(InventoryApplyResult.Committed(InventoryCommit(malformed,UtcTimestamp(4))))
        c.confirmPreview();advanceUntilIdle()
        assertTrue(c.state.value.pending);assertTrue(c.state.value.uncertain);assertTrue(gate.busy.value)
        assertEquals(Problem.CORRUPT_DATA,c.state.value.mutationProblem)
        assertNull(c.state.value.savedRevision);assertEquals(Revision(3),c.state.value.inventory!!.revision)
        assertEquals(1,f.calls.size);assertEquals(1,f.ids)
    }

    @Test fun warningConfirmedUnknownAbandonRequiresNewReadAndOldRefreshCannotReleaseItsGrant()=runTest {
        val f=Fixture();val gate=InventoryMutationGate();val c=controller(f,gate=gate)
        c.ensureLoaded();advanceUntilIdle();preview(c)
        f.response=Outcome.Unknown("Lost mutation acknowledgement",SnapshotList.empty())
        c.confirmPreview();advanceUntilIdle();val original=f.calls.single();val ids=f.ids
        assertTrue(c.state.value.uncertain);assertTrue(gate.busy.value)
        val old=CompletableDeferred<Unit>();f.snapshotGate=old;f.ignoreReadCancellation=true
        c.refresh();runCurrent()
        val fresh=CompletableDeferred<Unit>();f.snapshotGate=fresh
        c.abandonConfirmed();runCurrent()
        assertTrue(c.state.value.awaitingFresh);assertFalse(c.state.value.uncertain);assertTrue(gate.busy.value)
        old.complete(Unit);runCurrent()
        assertTrue(gate.busy.value);assertNull(gate.tryAcquire())
        assertEquals(listOf(original),f.calls);assertEquals(ids,f.ids)
        fresh.complete(Unit);advanceUntilIdle()
        assertFalse(gate.busy.value);assertFalse(c.state.value.pending);assertNull(c.state.value.savedRevision)
        assertEquals(listOf(original),f.calls);assertEquals(ids,f.ids)
    }

    @Test fun knownReceiptCannotBeReleasedByStaleOrDuplicateFreshInventory()=runTest {
        val f=Fixture();val gate=InventoryMutationGate();val c=controller(f,gate=gate)
        c.ensureLoaded();advanceUntilIdle();val before=f.stored;preview(c)
        f.snapshotResponse=Outcome.Success(before)
        c.confirmPreview();advanceUntilIdle()
        assertTrue(c.state.value.awaitingFresh);assertEquals(Problem.STALE_REVISION,c.state.value.loadProblem)
        assertTrue(gate.busy.value)
        f.snapshotResponse=Outcome.Success(f.stored.copy(capturedAt=UtcTimestamp(3)))
        c.retry();advanceUntilIdle()
        assertTrue(gate.busy.value);assertEquals(Problem.STALE_REVISION,c.state.value.loadProblem)
        f.snapshotResponse=Outcome.Success(f.stored.copy(lots=SnapshotList.of(unresolved,unresolved)))
        c.retry();advanceUntilIdle()
        assertTrue(gate.busy.value);assertEquals(Problem.CORRUPT_DATA,c.state.value.loadProblem)
        f.snapshotResponse=null;c.retry();advanceUntilIdle()
        assertFalse(gate.busy.value);assertEquals(1,f.calls.size)
    }

    @Test fun malformedUndoGraphsCannotReleaseOwnershipOrAppearSaved()=runTest {
        for (kind in 0..4) {
            val f=Fixture();val gate=InventoryMutationGate();val c=controller(f,gate=gate)
            c.ensureLoaded();advanceUntilIdle()
            val base=f.stored;val next=Revision(base.revision.value+1)
            val row=InventoryLotChange(unresolved.id,unresolved,unresolved.copy(revision=next))
            val graph=InventoryProjection(base.revision,next,SnapshotList.of(row),SnapshotList.empty(),SnapshotList.empty())
            val malformed=when(kind) {
                0 -> graph.copy(changes=SnapshotList.empty())
                1 -> graph.copy(changes=SnapshotList.of(row,row))
                2 -> graph.copy(changes=SnapshotList.of(row.copy(before=unresolved.copy(notes="Foreign before image"))))
                3 -> graph.copy(changes=SnapshotList.of(row.copy(after=unresolved)))
                else -> graph.copy(merges=SnapshotList.of(InventoryMerge(EntityId("foreign-request"),unresolved.id,1)))
            }
            f.undoResponse=Outcome.Success(InventoryCommit(malformed,UtcTimestamp(5)))
            c.undo();advanceUntilIdle()
            assertTrue("case $kind",gate.busy.value)
            assertTrue(c.state.value.uncertain);assertTrue(c.state.value.pending)
            assertEquals(Problem.CORRUPT_DATA,c.state.value.mutationProblem)
            assertNull(c.state.value.savedRevision);assertEquals(1,f.undos.size)
            c.abandonConfirmed();advanceUntilIdle();assertFalse(gate.busy.value)
        }
    }

    @Test fun malformedRejectionRevisionSeverityIdAndCodeKeepExactRequestHeld()=runTest {
        for (kind in 0..3) {
            val f=Fixture();val gate=InventoryMutationGate();val c=controller(f,gate=gate)
            c.ensureLoaded();advanceUntilIdle();preview(c);f.commitBeforeResponse=false
            val valid=DomainIssue(InventoryIssueCodes.QUANTITY_OVERFLOW,IssueSeverity.ERROR,"Synthetic rejection",unresolved.id,SnapshotList.empty())
            val row=when(kind) {
                1 -> valid.copy(severity=IssueSeverity.INFORMATION)
                2 -> valid.copy(entryId=EntityId("not-in-request"))
                3 -> valid.copy(code="NOT_A_TYPED_INVENTORY_ISSUE")
                else -> valid
            }
            f.response=Outcome.Success(InventoryApplyResult.Rejected(SnapshotList.of(row),Revision(if(kind==0)4 else 3)))
            c.confirmPreview();advanceUntilIdle()
            assertTrue("case $kind",gate.busy.value);assertTrue(c.state.value.uncertain)
            assertEquals(Problem.CORRUPT_DATA,c.state.value.mutationProblem)
            assertEquals(1,f.calls.size);assertNull(c.state.value.savedRevision)
            c.abandonConfirmed();advanceUntilIdle();assertFalse(gate.busy.value)
        }
    }

}
