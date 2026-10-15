@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class DeckLibraryControllerTest {
    private fun deck(id: String = "d", name: String = "Deck") = DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE,id),name,
        Knowledge.Absent("none"),"",Knowledge.Absent("none"),SnapshotList.empty(),UtcTimestamp(1),UtcTimestamp(1),Revision(0),
        SnapshotList.empty(),Knowledge.Absent("none"),null,SnapshotList.empty(),SnapshotList.empty())
    private fun summary(d: DeckSnapshot) = DeckSummary(d.id,d.name,d.revision,d.createdAt,d.modifiedAt,0,0)
    private fun fail(p: FailureCode) = Outcome.Failure(DomainFailure(p,"Synthetic failure",SnapshotList.empty()))
    private inner class Port : LocalDeckLibrary {
        val rows = linkedMapOf<DeckId,DeckSnapshot>()
        val creates = mutableListOf<Pair<DeckSnapshot,EntityId>>()
        val duplicates = mutableListOf<List<Any>>()
        val reads = mutableListOf<DeckId>()
        var page: Outcome<Page<DeckSummary>> = Outcome.Success(Page(SnapshotList.empty(),null,SnapshotReference(LocalDeckLibraryLimits.SOURCE,null,Revision(0),null)))
        var pageGate: CompletableDeferred<Unit>? = null
        var loadGate: CompletableDeferred<Unit>? = null
        var mutationGate: CompletableDeferred<Unit>? = null
        var mutationFailure: Outcome<Nothing>? = null
        var wrongLoadedId = false
        var ignoreReadCancellation = false
        private val receipts = mutableMapOf<EntityId,DeckSnapshot>()
        override suspend fun create(initial: DeckSnapshot, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> {
            creates += initial to operationId
            if ((mutationFailure as? Outcome.Failure)?.error?.code == FailureCode.INVALID_INPUT) return mutationFailure!!
            val saved = receipts.getOrPut(operationId) { rows[initial.id] = initial; initial }
            mutationGate?.await()
            return mutationFailure ?: Outcome.Success(saved)
        }
        override suspend fun duplicate(id: DeckId, expected: Revision, name: String, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> {
            duplicates += listOf(id,expected,name,operationId)
            val saved = receipts.getOrPut(operationId) { rows.getValue(id).copy(id=DeckId(LocalDeckLibraryLimits.SOURCE,"copy"),name=name,revision=Revision(0)).also { rows[it.id]=it } }
            mutationGate?.await()
            return mutationFailure ?: Outcome.Success(saved)
        }
        override suspend fun load(id: DeckId, cancel: CancelToken): Outcome<DeckSnapshot> {
            reads += id
            val result = rows[id]?.let { Outcome.Success(if(wrongLoadedId) it.copy(id=DeckId(LocalDeckLibraryLimits.SOURCE,"wrong")) else it) }
                ?: Outcome.Unavailable(UnavailableReason.NOT_FOUND,"Synthetic missing")
            val gate=loadGate
            if(ignoreReadCancellation) withContext(NonCancellable){gate?.await()} else gate?.await()
            return result
        }
        override suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<DeckSummary>> {
            val result=this.page;val gate=pageGate
            if(ignoreReadCancellation) withContext(NonCancellable){gate?.await()} else gate?.await()
            return result
        }
    }
    private fun TestScope.controller(p: Port): DeckLibraryController {
        var ids=0
        return DeckLibraryController(ownerScope(),p,StandardTestDispatcher(testScheduler),{"id-${++ids}"},{UtcTimestamp(7,8)})
    }
    @Test fun createsOnlyAfterConfirmationAndRetryReusesExactCommittedRequest() = runTest {
        val p=Port();val c=controller(p)
        c.create();c.name(" New deck ");assertTrue(p.creates.isEmpty())
        p.mutationFailure=fail(FailureCode.PERSISTENCE)
        c.confirm();advanceUntilIdle()
        val first=p.creates.single()
        assertEquals(" New deck ",first.first.name);assertEquals(UtcTimestamp(7,8),first.first.createdAt)
        assertEquals(SnapshotList.empty<EntryLabel>(),first.first.categoryOrder)
        assertTrue(c.state.value.pending);assertEquals(Problem.PERSISTENCE,c.state.value.mutationProblem)
        c.name("Changed");c.confirm();assertEquals(1,p.creates.size)
        p.mutationFailure=null;c.retry();advanceUntilIdle()
        assertEquals(listOf(first,first),p.creates);assertEquals(1,p.rows.size)
        assertFalse(c.state.value.pending);assertEquals(first.first.id,c.state.value.savedId)
        assertEquals(first.first,c.state.value.selected)
    }
    @Test fun uncertaintySurvivesLaterCorruptOrUnsupportedReplayUntilExplicitResolution() = runTest {
        for (later in listOf(fail(FailureCode.CORRUPT_DATA), Outcome.Unavailable(UnavailableReason.UNSUPPORTED,"Synthetic future codec"))) {
            val p=Port();val c=controller(p);c.create();c.name("Deck")
            p.mutationFailure=fail(FailureCode.PERSISTENCE);c.confirm();advanceUntilIdle()
            val first=p.creates.single();p.mutationFailure=later;c.retry();advanceUntilIdle()
            assertTrue(c.state.value.pending);c.name("Another");c.confirm();c.create()
            assertEquals(2,p.creates.size);assertEquals(first,p.creates.last())
            p.mutationFailure=null;c.retry();advanceUntilIdle()
            assertEquals(listOf(first,first,first),p.creates);assertFalse(c.state.value.pending)
            assertEquals(1,p.rows.size)
        }
    }

    @Test fun duplicateFreezesSourceRevisionNameAndOperationAcrossRetry() = runTest {
        val p=Port();val d=deck().copy(revision=Revision(3));p.rows[d.id]=d
        val c=controller(p);c.select(d.id);advanceUntilIdle();c.duplicate();c.name("Copy")
        p.mutationFailure=fail(FailureCode.PERSISTENCE);c.confirm();advanceUntilIdle()
        val first=p.duplicates.single();assertEquals(listOf(d.id,Revision(3),"Copy"),first.take(3))
        p.rows[d.id]=d.copy(revision=Revision(4));p.mutationFailure=null
        c.retry();advanceUntilIdle();assertEquals(listOf(first,first),p.duplicates)
        assertEquals(DeckId(LocalDeckLibraryLimits.SOURCE,"copy"),c.state.value.savedId)
    }
    @Test fun attemptsCannotOverlapAndAbandonRequiresTerminatedAttempt() = runTest {
        val p=Port();val c=controller(p);p.mutationGate=CompletableDeferred();p.mutationFailure=fail(FailureCode.PERSISTENCE)
        c.create();c.name("Deck");c.confirm();runCurrent()
        c.retry();c.confirm();c.abandonConfirmed();c.dismissForm();assertEquals(1,p.creates.size)
        assertTrue(c.state.value.mutationBusy);assertTrue(c.state.value.pending)
        p.mutationGate!!.complete(Unit);advanceUntilIdle();assertFalse(c.state.value.mutationBusy)
        c.dismissForm();assertTrue(c.state.value.pending)
        c.abandonConfirmed();advanceUntilIdle();assertFalse(c.state.value.pending)
        c.create();assertNotNull(c.state.value.form)
    }
    @Test fun selectionMadeBetweenAttemptsWinsAndAbandonRestartsRetainedDetailRead() = runTest {
        val p=Port();val a=deck("a");val b=deck("b");p.rows[a.id]=a;p.rows[b.id]=b
        val c=controller(p);c.select(a.id);advanceUntilIdle();c.duplicate();c.name("Copy")
        p.mutationFailure=fail(FailureCode.PERSISTENCE);c.confirm();advanceUntilIdle()
        c.select(b.id);advanceUntilIdle();p.mutationFailure=null;c.retry();advanceUntilIdle()
        assertEquals(b,c.state.value.selected)
        c.create();c.name("New");p.mutationFailure=fail(FailureCode.PERSISTENCE);c.confirm();advanceUntilIdle()
        p.ignoreReadCancellation=true;p.loadGate=CompletableDeferred();c.select(b.id);runCurrent()
        val old=p.loadGate!!;p.loadGate=null;c.abandonConfirmed();advanceUntilIdle()
        assertEquals(b,c.state.value.selected);assertFalse(c.state.value.loadingDetail)
        old.complete(Unit);advanceUntilIdle();assertEquals(b,c.state.value.selected)
    }

    @Test fun terminalRejectionAllowsCorrectedNewRequestButAcknowledgedSuccessSurvivesRefreshFailure() = runTest {
        val p=Port();val c=controller(p);c.create();c.name("Deck")
        p.mutationFailure=fail(FailureCode.INVALID_INPUT);c.confirm();advanceUntilIdle()
        assertFalse(c.state.value.pending);assertEquals(Problem.INVALID_INPUT,c.state.value.mutationProblem)
        c.name("Corrected");p.mutationFailure=null;p.page=fail(FailureCode.PERSISTENCE)
        c.confirm();advanceUntilIdle()
        assertNotNull(c.state.value.savedId);assertNull(c.state.value.mutationProblem)
        assertEquals(Problem.PERSISTENCE,c.state.value.listProblem)
    }
    @Test fun invalidNamesMakeNoRequestAndBlankComposerStateIsNotCommitted() = runTest {
        val p=Port();val c=controller(p);c.create()
        for(name in listOf(" ","é".repeat(513))) {
            c.name(name);c.confirm();advanceUntilIdle();assertTrue(c.state.value.invalidName)
        }
        c.dismissForm();assertNull(c.state.value.form);assertTrue(p.creates.isEmpty())
    }
    @Test fun latestSelectionWinsAgainstLateReadAndWrongLoadedIdentityIsRejected() = runTest {
        val p=Port();val a=deck("a");val b=deck("b");p.rows[a.id]=a;p.rows[b.id]=b
        val c=controller(p);p.ignoreReadCancellation=true;p.loadGate=CompletableDeferred()
        c.select(a.id);runCurrent();val old=p.loadGate!!
        p.loadGate=null;c.select(b.id);advanceUntilIdle();assertEquals(b,c.state.value.selected)
        old.complete(Unit);advanceUntilIdle();assertEquals(b,c.state.value.selected)
        p.wrongLoadedId=true;c.select(a.id);advanceUntilIdle()
        assertNull(c.state.value.selected);assertEquals(Problem.CORRUPT_DATA,c.state.value.detailProblem)
    }
    @Test fun pageFencesRejectForeignEpochLoopsOversizeAndKeepReplacementBounded() = runTest {
        val p=Port();val c=controller(p)
        fun page(rows:List<DeckSummary>,next:String?=null,epoch:Long=1,provider:ProviderId=LocalDeckLibraryLimits.SOURCE) =
            Outcome.Success(Page(SnapshotList.copyOf(rows),next,SnapshotReference(provider,null,Revision(epoch),null)))
        val first=(0..19).map{summary(deck("$it"))}
        p.page=page(first,"next");c.refresh();advanceUntilIdle();assertEquals(20,c.state.value.items.size)
        p.page=page(listOf(summary(deck("20"))),null);c.nextPage();advanceUntilIdle();assertEquals(1,c.state.value.items.size)
        for(bad in listOf(page(first+summary(deck("extra"))),page(listOf(first[0],first[0])),page(first,provider=ProviderId("foreign")))) {
            p.page=bad;c.refresh();advanceUntilIdle();assertEquals(Problem.CORRUPT_DATA,c.state.value.listProblem)
        }
        p.page=page(first,"next");c.refresh();advanceUntilIdle()
        p.page=page(first,"next");c.nextPage();advanceUntilIdle();assertEquals(Problem.CORRUPT_DATA,c.state.value.listProblem)
        p.page=page(first,"next");c.refresh();advanceUntilIdle()
        p.page=page(first,epoch=2);c.nextPage();advanceUntilIdle();assertEquals(Problem.CORRUPT_DATA,c.state.value.listProblem)
    }
    @Test fun mutationCompletionInvalidatesOldPagesAndPreservesNewSelectionContext() = runTest {
        val p=Port();val other=deck("other");p.rows[other.id]=other
        val c=controller(p);p.ignoreReadCancellation=true;p.pageGate=CompletableDeferred()
        c.refresh();runCurrent();val old=p.pageGate!!;p.pageGate=null
        p.mutationGate=CompletableDeferred();c.create();c.name("New");c.confirm();runCurrent()
        c.select(other.id);advanceUntilIdle();assertEquals(other,c.state.value.selected)
        p.page=fail(FailureCode.TRANSPORT);p.mutationGate!!.complete(Unit);advanceUntilIdle()
        assertEquals(other,c.state.value.selected);assertNotNull(c.state.value.savedId)
        old.complete(Unit);advanceUntilIdle();assertEquals(Problem.TRANSPORT,c.state.value.listProblem)
        assertNotNull(c.state.value.savedId)
    }
}
