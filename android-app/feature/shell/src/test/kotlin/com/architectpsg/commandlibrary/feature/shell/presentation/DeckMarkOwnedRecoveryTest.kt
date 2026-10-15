@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class DeckMarkOwnedRecoveryTest {
    @Test fun lostApplyThenUndoDeckAndCatalogChangeReplaysExactHistoricalReceipt() = runTest {
        val f=MarkOwnedFixture(this);f.ready(known=true);f.afterUnknown=true
        f.controller.confirm();runCurrent()
        assertTrue(f.controller.state.value.uncertain);assertTrue(f.gate.busy.value)
        val original=f.calls.single();val ids=f.ids;val receipt=f.receipts.getValue(original.operation).second
        // Independent later undo changes current supply; it cannot erase the historical receipt.
        f.stored=InventorySnapshot(Revision(5),UtcTimestamp(5),SnapshotList.of(f.supply.copy(revision=Revision(5))),true)
        f.head=f.head.copy(name="New deck",revision=Revision(8));f.active=null
        f.controller.onDeckChanged(f.head.id,f.head.revision);f.controller.onInventoryChanged(f.stored.revision);f.controller.onActiveGeneration(null)
        val catalogCount=f.catalogCalls.size
        f.controller.close();assertFalse(f.controller.state.value.visible);assertNull(f.gate.tryAcquire())
        f.controller.retry();runCurrent()
        assertEquals(listOf(original,original),f.calls);assertEquals(ids,f.ids);assertEquals(catalogCount,f.catalogCalls.size)
        assertEquals(1,f.groups);assertEquals(Revision(4),receipt.projection.resultRevision)
        assertEquals(f.stored,f.notices.single());assertEquals(Revision(5),f.controller.state.value.inventory!!.revision)
        assertFalse(f.gate.busy.value);assertFalse(f.controller.state.value.uncertain)
    }
    @Test fun validAckThenFailedFreshReadRetriesOnlyReadsAndCannotAbandonKnownCommit() = runTest {
        val f=MarkOwnedFixture(this);f.ready()
        f.snapshotResponse=Outcome.Unknown("Fresh read failed",SnapshotList.empty())
        f.controller.confirm();runCurrent()
        assertTrue(f.controller.state.value.awaitingFresh);assertFalse(f.controller.state.value.uncertain)
        assertEquals(1,f.calls.size);assertTrue(f.gate.busy.value);assertTrue(f.notices.isEmpty())
        assertEquals(Revision(3),f.controller.state.value.inventory!!.revision)
        val before=f.controller.state.value.rows;val ids=f.ids
        f.controller.discardInput();f.controller.refreshSources();f.controller.open(DeckId(LocalDeckLibraryLimits.SOURCE,"different"),f.policy)
        assertEquals(before,f.controller.state.value.rows);assertTrue(f.gate.busy.value)
        f.controller.close();f.controller.retry();runCurrent()
        assertEquals(1,f.calls.size);assertTrue(f.controller.state.value.awaitingFresh);assertEquals(ids,f.ids)
        f.snapshotResponse=null;f.controller.retry();runCurrent()
        assertEquals(1,f.calls.size);assertEquals(f.stored,f.notices.single());assertFalse(f.gate.busy.value)
    }
    @Test fun lateFreshReadBelowHighestNotificationCannotPublishOrReleaseGrant() = runTest {
        val f=MarkOwnedFixture(this);f.ready()
        val gate=f.gated("snapshot");f.controller.confirm();runCurrent()
        assertTrue(f.controller.state.value.awaitingFresh)
        f.controller.onInventoryChanged(Revision(7));gate.complete(Unit);runCurrent()
        assertTrue(f.controller.state.value.awaitingFresh);assertFalse(f.controller.state.value.uncertain)
        assertEquals(Problem.STALE_REVISION,f.controller.state.value.problem);assertTrue(f.gate.busy.value)
        assertTrue(f.notices.isEmpty());assertEquals(Revision(3),f.controller.state.value.inventory!!.revision)
        f.stored=f.stored.copy(revision=Revision(7),capturedAt=UtcTimestamp(7));f.controller.retry();runCurrent()
        assertEquals(1,f.calls.size);assertEquals(Revision(7),f.notices.single().revision);assertFalse(f.gate.busy.value)
    }
    @Test fun incompleteOrCorruptFreshSnapshotStaysReadOnlyPending() = runTest {
        for(incomplete in listOf(false,true)) {
            val f=MarkOwnedFixture(this);f.ready()
            val before=f.stored
            f.snapshotResponse=Outcome.Success(if(incomplete) before.copy(revision=Revision(4),complete=false) else before.copy(revision=Revision(4),lots=SnapshotList.of(f.supply,f.supply)))
            f.controller.confirm();runCurrent()
            assertTrue(f.controller.state.value.awaitingFresh);assertTrue(f.gate.busy.value)
            assertFalse(f.controller.state.value.uncertain);assertTrue(f.notices.isEmpty())
            f.snapshotResponse=null;f.controller.retry();runCurrent()
            assertEquals(1,f.calls.size);assertEquals(1,f.groups);assertFalse(f.gate.busy.value)
        }
    }
    @Test fun malformedAckEveryProjectionComponentOrTimeRemainsExactAndHeld() = runTest {
        for(mode in 0..6) {
            val f=MarkOwnedFixture(this);f.ready()
            f.alterCommit={ commit -> when(mode) {
                0->commit.copy(projection=commit.projection.copy(sourceRevision=Revision(2)))
                1->commit.copy(projection=commit.projection.copy(resultRevision=Revision(5)))
                2->commit.copy(projection=commit.projection.copy(changes=SnapshotList.empty()))
                3->commit.copy(projection=commit.projection.copy(changes=SnapshotList.copyOf(commit.projection.changes.map { it.copy(after=it.after!!.copy(notes="Unexpected content")) })))
                4->commit.copy(projection=commit.projection.copy(merges=SnapshotList.of(InventoryMerge(EntityId("wrong"),EntityId("supply"),2))))
                5->commit.copy(projection=commit.projection.copy(issues=SnapshotList.of(DomainIssue("WRONG",IssueSeverity.ERROR,"Malformed",null,SnapshotList.empty()))))
                else->commit.copy(modifiedAt=UtcTimestamp(2))
            } }
            f.controller.confirm();runCurrent()
            val first=f.calls.single();val rows=f.controller.state.value.rows;val ids=f.ids
            assertEquals(Problem.CORRUPT_DATA,f.controller.state.value.problem);assertTrue(f.controller.state.value.uncertain)
            assertTrue(f.gate.busy.value);assertFalse(f.controller.state.value.awaitingFresh)
            f.controller.discardInput();f.controller.close();f.controller.retry();runCurrent()
            assertEquals(listOf(first,first),f.calls);assertEquals(rows,f.controller.state.value.rows);assertEquals(ids,f.ids)
            assertTrue(f.notices.isEmpty());assertTrue(f.gate.busy.value)
            f.alterCommit={it};f.controller.retry();runCurrent()
            assertEquals(3,f.calls.size);assertEquals(1,f.groups);assertFalse(f.gate.busy.value)
        }
    }
    @Test fun malformedRejectedRowsOrRevisionCannotReleaseUnknownRequest() = runTest {
        for(mode in 0..4) {
            val f=MarkOwnedFixture(this);f.ready();f.beforeUnknown=true
            f.controller.confirm();runCurrent();val first=f.calls.single()
            val id=(first.patch.edits.single() as CollectionEdit.Add).lot.id
            val issue=DomainIssue(InventoryIssueCodes.LOT_ALREADY_EXISTS,IssueSeverity.ERROR,"Synthetic",id,SnapshotList.empty())
            f.response=Outcome.Success(InventoryApplyResult.Rejected(when(mode){
                0->SnapshotList.empty()
                1->SnapshotList.of(issue.copy(entryId=EntityId("unrelated")))
                2->SnapshotList.of(issue.copy(severity=IssueSeverity.WARNING))
                else->SnapshotList.of(issue)
            },when(mode){3->Revision(2);4->Revision(4);else->Revision(3)}))
            f.controller.retry();runCurrent()
            assertEquals(Problem.CORRUPT_DATA,f.controller.state.value.problem);assertTrue(f.controller.state.value.uncertain)
            assertTrue(f.gate.busy.value);assertEquals(listOf(first,first),f.calls);assertEquals(0,f.groups)
        }
    }
    @Test fun definitiveRowRejectionAfterUnknownReleasesWithoutReplacementRequest() = runTest {
        val f=MarkOwnedFixture(this);f.ready();f.beforeUnknown=true
        f.controller.confirm();runCurrent();val first=f.calls.single();val rows=f.controller.state.value.rows
        val id=(first.patch.edits.single() as CollectionEdit.Add).lot.id
        val issues=SnapshotList.of(DomainIssue(InventoryIssueCodes.LOT_ALREADY_EXISTS,IssueSeverity.ERROR,"Synthetic rejection",id,SnapshotList.empty()))
        f.response=Outcome.Success(InventoryApplyResult.Rejected(issues,f.stored.revision))
        f.controller.retry();runCurrent()
        assertEquals(listOf(first,first),f.calls);assertFalse(f.gate.busy.value);assertFalse(f.controller.state.value.uncertain)
        assertNull(f.controller.state.value.preview);assertEquals(rows,f.controller.state.value.rows);assertEquals(issues,f.controller.state.value.issues)
        f.controller.retry();runCurrent();assertEquals(2,f.calls.size);assertEquals(0,f.groups)
    }
    @Test fun unknownBeforeEffectThenExternalInventoryCasIsDefinitiveAndKeepsRaw() = runTest {
        val f=MarkOwnedFixture(this);f.ready();f.beforeUnknown=true
        f.controller.confirm();runCurrent();val first=f.calls.single();val raw=f.controller.state.value.rows
        f.stored=f.stored.copy(revision=Revision(9));f.controller.onInventoryChanged(Revision(9))
        f.controller.retry();runCurrent()
        assertEquals(listOf(first,first),f.calls);assertEquals(0,f.groups);assertFalse(f.gate.busy.value)
        assertFalse(f.controller.state.value.uncertain);assertEquals(Problem.STALE_REVISION,f.controller.state.value.problem)
        assertEquals(raw,f.controller.state.value.rows);assertNull(f.controller.state.value.preview)
    }
    @Test fun gateAcquiredBeforeFirstAwaitAndHiddenPendingBlocksCompetingWriter() = runTest {
        val f=MarkOwnedFixture(this);f.ready();val deck=f.gated("deck")
        f.controller.confirm();assertTrue(f.gate.busy.value);assertNull(f.gate.tryAcquire())
        runCurrent();f.controller.close();assertFalse(f.controller.state.value.visible)
        f.controller.discardInput();assertNotNull(f.controller.state.value.deckId)
        f.controller.confirm();assertEquals(0,f.calls.size)
        deck.complete(Unit);runCurrent();assertEquals(1,f.calls.size);assertFalse(f.gate.busy.value)
    }
    @Test fun competingGrantRejectsBeforeAnyPreflightOrOperationId() = runTest {
        val f=MarkOwnedFixture(this);f.ready();val owner=f.gate.tryAcquire()!!;val ids=f.ids
        f.controller.confirm();runCurrent()
        assertTrue(f.calls.isEmpty());assertEquals(ids,f.ids);assertTrue(owner.owns)
        assertNotNull(f.controller.state.value.preview);owner.release()
        f.controller.confirm();runCurrent();assertEquals(1,f.groups)
    }
    @Test fun sourceNotificationsInvalidatePreviewButNeverRewriteFrozenRequest() = runTest {
        val f=MarkOwnedFixture(this);f.ready();val old=f.controller.state.value.preview
        f.controller.onInventoryChanged(Revision(4));assertNull(f.controller.state.value.preview)
        assertEquals(old!!.rows.single().input,f.row("b").input)
        f.controller.discardInput();f.stored=f.stored.copy(revision=Revision(4));f.ready()
        val gate=f.gated("apply.after");f.controller.confirm();runCurrent()
        val frozen=f.calls.single();val rows=f.controller.state.value.rows
        f.controller.onInventoryChanged(Revision(8));f.controller.onDeckChanged(f.head.id,Revision(8));f.controller.onActiveGeneration(null)
        assertEquals(rows,f.controller.state.value.rows);assertEquals(frozen,f.calls.single())
        gate.complete(Unit);runCurrent()
        assertTrue(f.controller.state.value.awaitingFresh);assertTrue(f.gate.busy.value)
        f.stored=f.stored.copy(revision=Revision(8));f.controller.retry();runCurrent()
        assertEquals(1,f.calls.size);assertEquals(Revision(8),f.notices.single().revision)
    }
    @Test fun cancellationAfterKnownReceiptKeepsReadOnlyAuthority() = runTest {
        val f=MarkOwnedFixture(this);f.ready();f.cancelNextSnapshot=true
        f.controller.confirm();runCurrent()
        assertTrue(f.controller.state.value.awaitingFresh);assertFalse(f.controller.state.value.uncertain)
        assertTrue(f.gate.busy.value);assertEquals(1,f.calls.size);assertTrue(f.notices.isEmpty())
        f.controller.retry();runCurrent()
        assertEquals(1,f.calls.size);assertEquals(1,f.notices.size);assertFalse(f.gate.busy.value)
    }

    @Test fun cancelledOwnerBeforeInitialDispatchReleasesUnsubmittedGrant() = runTest {
        val owner=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        val f=MarkOwnedFixture(this,owner);f.ready();val ids=f.ids
        owner.cancel();f.controller.confirm();runCurrent()
        assertFalse(f.gate.busy.value);assertFalse(f.controller.state.value.submitting)
        assertFalse(f.controller.state.value.uncertain);assertEquals(ids,f.ids);assertTrue(f.calls.isEmpty())
    }
    @Test fun cancellationAfterDurableApplyRetainsExactRequestAndGrant() = runTest {
        val f=MarkOwnedFixture(this);f.ready();f.cancelNextApplyAck=true
        f.controller.confirm();runCurrent()
        val exact=f.calls.single();val ids=f.ids
        assertTrue(f.controller.state.value.uncertain);assertTrue(f.gate.busy.value);assertEquals(1,f.groups)
        f.controller.close();f.controller.retry();runCurrent()
        assertEquals(listOf(exact,exact),f.calls);assertEquals(ids,f.ids)
        assertEquals(1,f.groups);assertFalse(f.gate.busy.value)
    }
    @Test fun freshReceiptTimeAndPreviouslyAdmittedTimeCannotRegress() = runTest {
        for(newer in listOf(false,true)) {
            val f=MarkOwnedFixture(this);f.ready()
            f.snapshotResponse=Outcome.Success(f.stored.copy(revision=Revision(if(newer) 8 else 4),capturedAt=UtcTimestamp(3)))
            f.controller.confirm();runCurrent()
            assertTrue(f.controller.state.value.awaitingFresh);assertFalse(f.controller.state.value.uncertain)
            assertTrue(f.gate.busy.value);assertTrue(f.notices.isEmpty());assertEquals(1,f.calls.size)
            f.snapshotResponse=null;f.controller.retry();runCurrent()
            assertEquals(1,f.calls.size);assertFalse(f.gate.busy.value)
            f.stored=f.stored.copy(revision=Revision(9),capturedAt=UtcTimestamp(2));f.controller.open(f.head.id,f.policy);runCurrent()
            assertEquals(Problem.STALE_REVISION,f.controller.state.value.problem);assertTrue(f.controller.state.value.rows.isEmpty())
            assertEquals(1,f.calls.size)
        }
    }

}
