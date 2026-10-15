@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Honest bounded port doubles; no database or version wire/durability acceptance evidence. */
class DeckVersionsControllerTest {
    private fun deck(raw:String="deck",revision:Long=4,name:String="Current")=DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE,raw),name,Knowledge.Absent("format"),"description",
        Knowledge.Unknown("folder",SnapshotList.of(Evidence("retained",null,null))),SnapshotList.of("tag"),UtcTimestamp(1),UtcTimestamp(revision+2),Revision(revision),
        SnapshotList.of(DeckEntry(EntityId("entry"),CardIdentity(Knowledge.Absent("oracle"),Knowledge.Absent("printing"),ProviderIdentity(ProviderId("foreign"),"kind","raw"),Knowledge.Known("Card"),LanguageTag("ja")),
            if(revision<4)1 else 2,if(revision<4)BoardRole.MAIN else BoardRole.SIDEBOARD,Knowledge.Unknown("finish",SnapshotList.empty()),if(revision<4)"historical notes"else"notes",SnapshotList.of(EntryLabel(EntityId("category"),"Category")),SnapshotList.empty())),
        Knowledge.Absent("budget"),null,SnapshotList.empty(),SnapshotList.of(PackageInstance(EntityId("instance"),EntityId("package"),EntityId(if(revision<4)"historical-package-version"else"current-package-version"),PackageLinkPolicy.LINKED,
            SnapshotList.of(PackageContribution(EntityId("package-entry"),EntityId("entry"),1)))),"primer",SnapshotList.of(EntryLabel(EntityId("category"),"Category")))
    private fun version(id:String="old",raw:String="deck"):DeckVersion { val revision=if(id=="recovery")2L else 1L;return DeckVersion(EntityId(id),deck(raw).id,"Named $id",UtcTimestamp(10),Revision(revision),deck(raw,revision,"Historical $id"),null) }
    private fun failure(code:FailureCode)=Outcome.Failure(DomainFailure(code,"Synthetic failure",SnapshotList.empty()))
    private inner class Fixture:LocalDeckVersions,LocalDeckLibrary {
        var current=deck()
        val rows=linkedMapOf(version().id to version(),version("recovery").id to version("recovery"))
        val kinds=mutableMapOf(EntityId("old") to DeckVersionKind.USER,EntityId("recovery") to DeckVersionKind.RECOVERY)
        val recoveryTargets=mutableMapOf(EntityId("recovery") to EntityId("old"))
        val captureCalls=mutableListOf<List<Any>>()
        val restoreCalls=mutableListOf<List<Any>>()
        val listCalls=mutableListOf<Pair<DeckId,PageRequest>>()
        val previewCalls=mutableListOf<List<Any>>()
        val events=mutableListOf<String>()
        val captureReceipts=mutableMapOf<EntityId,DeckVersion>()
        val restoreReceipts=mutableMapOf<EntityId,DeckRestoreResult>()
        var ids=0;var generated=0
        var epoch=1L
        var cursor:String?=null
        var pageItems:List<DeckVersionSummary>?=null
        var readGate:CompletableDeferred<Unit>?=null
        var targetGate:CompletableDeferred<Unit>?=null
        var previewGate:CompletableDeferred<Unit>?=null
        var mutationGate:CompletableDeferred<Unit>?=null
        var ignoreCancellation=false
        var readFailure:Outcome<Nothing>?=null
        var listFailure:Outcome<Nothing>?=null
        var failureOnce=false
        var mutationFailure:Outcome<Nothing>?=null
        var commitBeforeFailure=true
        var wrongCapture=false
        var wrongRestore=false
        fun summary(v:DeckVersion)=DeckVersionSummary(v.id,v.deckId,v.label,v.createdAt,v.sourceRevision,v.predecessorVersionId,kinds[v.id]?:DeckVersionKind.USER,recoveryTargets[v.id])
        override suspend fun load(id:DeckId,cancel:CancelToken):Outcome<DeckSnapshot> {
            events+="head";val value=readFailure?:Outcome.Success(current.copy(id=id));val gate=readGate
            if(ignoreCancellation)withContext(NonCancellable){gate?.await()}else gate?.await()
            return value
        }
        override suspend fun list(id:DeckId,page:PageRequest,cancel:CancelToken):Outcome<Page<DeckVersionSummary>> {
            listCalls+=id to page;val failed=listFailure
            if(failureOnce)listFailure=null
            return failed?:Outcome.Success(Page(SnapshotList.copyOf(pageItems?:rows.values.filter{it.deckId==id}.asReversed().map(::summary)),cursor,SnapshotReference(LocalDeckLibraryLimits.SOURCE,null,Revision(epoch),null)))
        }
        override suspend fun load(id:DeckId,versionId:EntityId,cancel:CancelToken):Outcome<DeckVersion> {
            val result=rows[versionId]?.let{Outcome.Success(it)}?:Outcome.Unavailable(UnavailableReason.NOT_FOUND,"missing")
            val gate=targetGate;if(ignoreCancellation)withContext(NonCancellable){gate?.await()}else gate?.await();return result
        }
        override suspend fun previewRestore(id:DeckId,versionId:EntityId,expected:Revision,cancel:CancelToken):Outcome<DeckRestorePreview> {
            previewCalls+=listOf(id,versionId,expected)
            val target=rows.getValue(versionId);val p=DeckRestorePreview(current,target,DeckDifference(current,target.manifest,SnapshotList.of(DeckChangedField.NAME,DeckChangedField.MODIFIED_AT,DeckChangedField.REVISION,DeckChangedField.PACKAGES),
                SnapshotList.of(CardChange(EntityId("entry"),current.entries.single(),target.manifest.entries.single(),"Retained diff reason",SnapshotList.empty()))))
            val gate=previewGate;if(ignoreCancellation)withContext(NonCancellable){gate?.await()}else gate?.await();return Outcome.Success(p)
        }
        override suspend fun capture(id:DeckId,expected:Revision,label:String,operationId:EntityId,cancel:CancelToken):Outcome<DeckVersion> {
            captureCalls+=listOf(id,expected,label,operationId)
            if(!commitBeforeFailure&&mutationFailure!=null)return mutationFailure!!
            val v=captureReceipts.getOrPut(operationId){DeckVersion(EntityId("captured-${++generated}"),id,label,UtcTimestamp(30),expected,current,rows.values.lastOrNull()?.id).also{rows[it.id]=it;kinds[it.id]=DeckVersionKind.USER;epoch++}}
            mutationGate?.await()
            return mutationFailure?:Outcome.Success(if(wrongCapture)v.copy(label="wrong")else v)
        }
        override suspend fun restore(id:DeckId,versionId:EntityId,expected:Revision,recoveryLabel:String,operationId:EntityId,cancel:CancelToken):Outcome<DeckRestoreResult> {
            restoreCalls+=listOf(id,versionId,expected,recoveryLabel,operationId)
            if(!commitBeforeFailure&&mutationFailure!=null)return mutationFailure!!
            val result=restoreReceipts.getOrPut(operationId){
                val before=current;val target=rows.getValue(versionId)
                val recovery=DeckVersion(EntityId("recovery-${++generated}"),id,recoveryLabel,UtcTimestamp(40),expected,before,rows.values.lastOrNull()?.id)
                current=target.manifest.copy(revision=Revision(expected.value+1),modifiedAt=UtcTimestamp(40))
                rows[recovery.id]=recovery;kinds[recovery.id]=DeckVersionKind.RECOVERY;recoveryTargets[recovery.id]=versionId;epoch++
                DeckRestoreResult(current,recovery)
            }
            mutationGate?.await()
            return mutationFailure?:Outcome.Success(if(wrongRestore)result.copy(recoveryVersion=result.recoveryVersion.copy(sourceRevision=Revision(0)))else result)
        }
        override suspend fun list(page:PageRequest,cancel:CancelToken):Outcome<Page<DeckSummary>> = error("Not used by versions presentation")
        override suspend fun create(initial:DeckSnapshot,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> = error("No deck create")
        override suspend fun duplicate(id:DeckId,expected:Revision,name:String,operationId:EntityId,cancel:CancelToken):Outcome<DeckSnapshot> = error("No deck duplicate")
    }
    private fun TestScope.controller(f:Fixture,owner:CoroutineScope=ownerScope(),callback:(DeckId,Revision)->Unit={id,rev->f.events+="callback:${id.raw}:${rev.value}"})=
        DeckVersionsController(owner,f,f,StandardTestDispatcher(testScheduler),{EntityId("operation-${++f.ids}")},callback)
    private fun TestScope.opened(f:Fixture)=controller(f).also{it.openDeck(deck().id);advanceUntilIdle()}
    private fun TestScope.preview(c:DeckVersionsController,id:EntityId=EntityId("old")){c.selectVersion(id);advanceUntilIdle();c.previewRestore();advanceUntilIdle()}
    @Test fun capturePreservesExactLabelFullManifestAndContentRevisionWithoutCommitCallback()=runTest {
        val f=Fixture();val c=opened(f);c.beginCapture();c.label("  exact é label  ");assertTrue(f.captureCalls.isEmpty());c.capture();advanceUntilIdle()
        assertEquals("  exact é label  ",c.state.value.captured!!.label);assertEquals(deck(),c.state.value.captured!!.manifest)
        assertEquals(Revision(4),c.state.value.head!!.revision);assertEquals(1,f.ids);assertFalse(f.events.any{it.startsWith("callback")});assertFalse(c.state.value.pending)
        c.beginCapture();c.label("  exact é label  ");c.capture();advanceUntilIdle();assertEquals(2,f.captureCalls.size)
    }
    @Test fun labelsAreStrictUtf8BoundedNonblankAndNeverTrimmed()=runTest {
        val f=Fixture();val c=opened(f);c.beginCapture()
        for(label in listOf(""," ","é".repeat(513),"x".repeat(1025),"bad\ud800")){c.label(label);c.capture();advanceUntilIdle();assertTrue(c.state.value.invalidLabel);assertTrue(f.captureCalls.isEmpty())}
        c.label("é".repeat(512));c.capture();advanceUntilIdle();assertEquals(1,f.captureCalls.size)
        preview(c);c.recoveryLabel(" ");c.confirmRestore();assertTrue(c.state.value.invalidLabel);assertTrue(f.restoreCalls.isEmpty())
    }
    @Test fun recoveryCheckpointIsDirectlySelectablePreviewableAndRestorableWithoutWritingOnPreview()=runTest {
        val f=Fixture();val c=opened(f);assertTrue(c.state.value.items.any{it.kind==DeckVersionKind.RECOVERY})
        preview(c,EntityId("recovery"));assertTrue(f.restoreCalls.isEmpty());assertTrue(f.captureCalls.isEmpty())
        assertEquals(version("recovery"),c.state.value.preview!!.target);assertEquals(deck(),c.state.value.preview!!.difference.before)
        assertEquals(version("recovery").manifest.packageInstances,c.state.value.preview!!.difference.after.packageInstances)
        c.recoveryLabel("Recovery of recovery");c.confirmRestore();advanceUntilIdle();assertEquals(EntityId("recovery"),f.restoreCalls.single()[1]);assertNotNull(c.state.value.restored)
        assertEquals(EntityId("recovery"),c.state.value.items.first{it.id==c.state.value.restored!!.recoveryVersion.id}.restoredTargetVersionId)
    }
    @Test fun restoreValidatesWholeTargetAndRecoveryThenCallsBackBeforeReloadEvenIfReloadFails()=runTest {
        val f=Fixture();val c=opened(f);preview(c);c.recoveryLabel("Safety copy");f.events.clear();f.readFailure=failure(FailureCode.PERSISTENCE)
        c.confirmRestore();advanceUntilIdle()
        assertEquals("callback:deck:5",f.events.first());assertEquals("head",f.events[1]);assertEquals(Problem.PERSISTENCE,c.state.value.headProblem)
        val result=c.state.value.restored!!;assertEquals(deck(),result.recoveryVersion.manifest);assertEquals("Safety copy",result.recoveryVersion.label)
        assertEquals(version().manifest.copy(revision=Revision(5),modifiedAt=UtcTimestamp(40)),result.snapshot)
        assertEquals(version().manifest.packageInstances,result.snapshot.packageInstances);assertFalse(c.state.value.pending)
    }
    @Test fun lostCaptureAcknowledgementStaysLatchedAcrossLaterFailureAndRetriesExactFrozenInputAfterClose()=runTest {
        val f=Fixture();val c=opened(f);c.beginCapture();c.label(" exact ");f.mutationFailure=Outcome.Unknown("lost",SnapshotList.empty());c.capture();advanceUntilIdle()
        val original=f.captureCalls.single();assertTrue(c.state.value.pending);c.closeCapture();assertFalse(c.state.value.captureForm)
        c.label("different");c.beginCapture();c.capture();assertEquals(1,f.ids);assertTrue(c.requestClose())
        f.mutationFailure=failure(FailureCode.STALE_REVISION);c.retry();advanceUntilIdle();assertTrue(c.state.value.pending)
        f.mutationFailure=null;c.retry();advanceUntilIdle();assertEquals(List(3){original},f.captureCalls);assertFalse(c.state.value.pending);assertEquals(1,f.generated)
        c.openDeck(deck().id);advanceUntilIdle();assertEquals(1,f.generated)
    }
    @Test fun restoreReplayReturnsOriginalRecoveryAndReloadsActualNewerHead()=runTest {
        val f=Fixture();val c=opened(f);preview(c);c.recoveryLabel("recover");f.mutationFailure=failure(FailureCode.PERSISTENCE);c.confirmRestore();advanceUntilIdle()
        val call=f.restoreCalls.single();val original=f.restoreReceipts.values.single();f.current=f.current.copy(revision=Revision(12),name="Newer actual head")
        f.mutationFailure=Outcome.Unavailable(UnavailableReason.UNSUPPORTED,"later");c.retry();advanceUntilIdle();assertTrue(c.state.value.pending)
        f.mutationFailure=null;c.retry();advanceUntilIdle();assertEquals(List(3){call},f.restoreCalls);assertEquals(original,c.state.value.restored)
        assertEquals(Revision(12),c.state.value.head!!.revision);assertEquals("Newer actual head",c.state.value.head!!.name);assertEquals(1,f.generated)
    }
    @Test fun wrongCaptureOrRestoreSuccessRemainsUncertainWithoutCallback()=runTest {
        val f=Fixture();val c=opened(f);c.beginCapture();c.label("name");f.wrongCapture=true;c.capture();advanceUntilIdle()
        assertTrue(c.state.value.pending);assertEquals(Problem.CORRUPT_DATA,c.state.value.mutationProblem);assertNull(c.state.value.captured)
        c.abandonConfirmed();advanceUntilIdle();preview(c);c.recoveryLabel("recovery");f.wrongRestore=true;c.confirmRestore();advanceUntilIdle()
        assertTrue(c.state.value.pending);assertNull(c.state.value.restored);assertFalse(f.events.any{it.startsWith("callback")})
    }
    @Test fun oneMutationQueuePreventsSecondOperationAndCancellationDoesNotEstablishRollback()=runTest {
        val f=Fixture();val owner=ownerScope();val c=controller(f,owner);c.openDeck(deck().id);advanceUntilIdle();c.beginCapture();c.label("name");f.mutationGate=CompletableDeferred()
        c.capture();runCurrent();c.capture();c.beginCapture();c.confirmRestore();c.retry();assertEquals(1,f.captureCalls.size);assertEquals(1,f.ids)
        owner.cancel();advanceUntilIdle();assertTrue(c.state.value.pending);assertFalse(c.state.value.mutationBusy);assertEquals(1,f.generated)
    }
    @Test fun staleRestoreNeverSubstitutesRevisionOrAutomaticallyApplies()=runTest {
        val f=Fixture();val c=opened(f);preview(c);c.recoveryLabel("recover");f.commitBeforeFailure=false;f.mutationFailure=failure(FailureCode.STALE_REVISION)
        c.confirmRestore();advanceUntilIdle();assertFalse(c.state.value.pending);assertNull(c.state.value.preview);assertNull(c.state.value.restored)
        c.confirmRestore();c.retry();advanceUntilIdle();assertEquals(1,f.restoreCalls.size);assertEquals(Revision(4),f.restoreCalls.single()[2])
    }
    @Test fun lateDeckOpenAndVersionLoadCannotOverwriteNewerLogicalSelection()=runTest {
        val f=Fixture();val c=opened(f);f.ignoreCancellation=true;val held=CompletableDeferred<Unit>();f.readGate=held;c.openDeck(deck("older").id);runCurrent()
        f.readGate=null;c.openDeck(deck("newer").id);advanceUntilIdle();held.complete(Unit);advanceUntilIdle();assertEquals(deck("newer").id,c.state.value.head!!.id)
        c.openDeck(deck().id);advanceUntilIdle();val target=CompletableDeferred<Unit>();f.targetGate=target;c.selectVersion(EntityId("old"));runCurrent()
        f.targetGate=null;c.selectVersion(EntityId("recovery"));advanceUntilIdle();target.complete(Unit);advanceUntilIdle();assertEquals(EntityId("recovery"),c.state.value.selected!!.id)
    }
    @Test fun latePreviewOrClosedReadsCannotRestoreStaleContent()=runTest {
        val f=Fixture();val c=opened(f);c.selectVersion(EntityId("old"));advanceUntilIdle();f.ignoreCancellation=true;val gate=CompletableDeferred<Unit>();f.previewGate=gate
        c.previewRestore();runCurrent();c.selectVersion(EntityId("recovery"));advanceUntilIdle();gate.complete(Unit);advanceUntilIdle();assertNull(c.state.value.preview)
        val read=CompletableDeferred<Unit>();f.readGate=read;c.refreshHead();runCurrent();assertTrue(c.requestClose());read.complete(Unit);advanceUntilIdle();assertFalse(c.state.value.loadingHead)
    }
    @Test fun loadMoreUsesCursorAppendsWithoutDuplicateRowsAndStaleEpochReloadsFirstPageOnce()=runTest {
        val f=Fixture();f.pageItems=listOf(f.summary(version()));f.cursor="next";val c=opened(f)
        f.pageItems=listOf(f.summary(version("recovery")));f.cursor=null;c.loadMore();advanceUntilIdle();assertEquals(listOf(EntityId("old"),EntityId("recovery")),c.state.value.items.map{it.id})
        assertEquals("next",f.listCalls.last().second.cursor)
        f.pageItems=listOf(f.summary(version()));f.cursor="again";c.refreshVersions();advanceUntilIdle()
        f.listFailure=failure(FailureCode.STALE_REVISION);f.failureOnce=true;f.cursor=null;c.loadMore();advanceUntilIdle()
        assertNull(f.listCalls.last().second.cursor);assertEquals(listOf(EntityId("old")),c.state.value.items.map{it.id});assertNull(c.state.value.listProblem)
    }
    @Test fun emptyVersionPageAndAbsentDeckAreDifferentAndMalformedDuplicatePageIsRejected()=runTest {
        val f=Fixture();f.pageItems=emptyList();val c=opened(f);assertTrue(c.state.value.items.isEmpty());assertNotNull(c.state.value.head);assertNull(c.state.value.listProblem)
        f.listFailure=Outcome.Unavailable(UnavailableReason.NOT_FOUND,"missing");c.refreshVersions();advanceUntilIdle();assertEquals(Problem.NOT_FOUND,c.state.value.listProblem)
        f.listFailure=null;f.pageItems=listOf(f.summary(version()),f.summary(version()));c.refreshVersions();advanceUntilIdle();assertEquals(Problem.CORRUPT_DATA,c.state.value.listProblem)
    }
    @Test fun localNamesNeedExplicitDiscardAndSameDeckReentryDoesNotGenerateIdsOrLoseDraft()=runTest {
        val f=Fixture();val c=opened(f);c.beginCapture();c.label("draft");c.beginCapture();assertEquals("draft",c.state.value.label);c.openDeck(deck().id);advanceUntilIdle();assertEquals("draft",c.state.value.label);assertEquals(0,f.ids)
        assertFalse(c.requestClose());assertTrue(c.state.value.discardPrompt);c.keepDraft();assertEquals("draft",c.state.value.label)
        c.openDeck(deck("next").id);assertTrue(c.state.value.discardPrompt);assertFalse(c.discardDraft());advanceUntilIdle();assertEquals(deck("next").id,c.state.value.deckId)
    }
    @Test fun unsavedRecoveryNameCannotBeSilentlyDroppedByChangingTarget()=runTest {
        val f=Fixture();val c=opened(f);preview(c);c.recoveryLabel("draft recovery");c.selectVersion(EntityId("recovery"));assertTrue(c.state.value.discardPrompt)
        assertEquals(EntityId("old"),c.state.value.selectedId);c.keepDraft();assertEquals("draft recovery",c.state.value.recoveryLabel)
        c.selectVersion(EntityId("old"));assertEquals("draft recovery",c.state.value.recoveryLabel)
        c.cancelPreview();assertTrue(c.state.value.discardPrompt);c.keepDraft();assertNotNull(c.state.value.preview)
        c.selectVersion(EntityId("recovery"));c.discardDraft();advanceUntilIdle();assertEquals(EntityId("recovery"),c.state.value.selectedId);assertEquals("",c.state.value.recoveryLabel)
    }
    @Test fun captureTransitionCannotDiscardAnUnrelatedRecoveryDraftWithoutConsent()=runTest {
        val f=Fixture();val c=opened(f);preview(c);c.recoveryLabel("Retained recovery")
        c.beginCapture();assertTrue(c.state.value.discardPrompt);assertFalse(c.state.value.captureForm)
        c.keepDraft();assertEquals("Retained recovery",c.state.value.recoveryLabel);assertNotNull(c.state.value.preview)
        c.beginCapture();assertFalse(c.discardDraft());assertTrue(c.state.value.captureForm);assertEquals("",c.state.value.recoveryLabel)
        c.label("Unsaved capture");c.closeCapture();assertTrue(c.state.value.discardPrompt);c.discardDraft()
        assertFalse(c.state.value.captureForm);assertTrue(f.captureCalls.isEmpty());assertEquals(0,f.ids)
    }
    @Test fun captureSuccessRequiresExplicitCrossFormDiscardAndCannotAcceptHiddenRecoveryEdits()=runTest {
        val f=Fixture();val c=opened(f);preview(c);c.recoveryLabel("Retained recovery");c.beginCapture()
        c.label("Not an open form");c.capture();advanceUntilIdle();assertTrue(f.captureCalls.isEmpty());assertEquals("Retained recovery",c.state.value.recoveryLabel)
        c.discardDraft();c.label("Confirmed capture");c.recoveryLabel("Hidden edit");assertEquals("",c.state.value.recoveryLabel)
        c.capture();advanceUntilIdle();assertEquals("Confirmed capture",c.state.value.captured!!.label);assertEquals(1,f.captureCalls.size)
        assertEquals(Revision(4),c.state.value.head!!.revision);assertTrue(f.restoreCalls.isEmpty())
    }
    @Test fun acknowledgementCallbackFailureDoesNotInventAnUnretryablePendingWrite()=runTest {
        val f=Fixture();val c=controller(f,callback={_,_->error("Synthetic observer failure")});c.openDeck(deck().id);advanceUntilIdle();preview(c);c.recoveryLabel("recovery")
        c.confirmRestore();advanceUntilIdle();assertNotNull(c.state.value.restored);assertFalse(c.state.value.pending);assertFalse(c.state.value.mutationBusy)
    }
}
