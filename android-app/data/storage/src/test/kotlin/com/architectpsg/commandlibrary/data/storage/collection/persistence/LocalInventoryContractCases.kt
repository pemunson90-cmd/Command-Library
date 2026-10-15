package com.architectpsg.commandlibrary.data.storage.collection.persistence

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Root-owned consumer examples; concrete backend inherits these against actual SQLite/codecs. */
abstract class LocalInventoryContractCases {
    protected abstract val inventory: LocalInventory
    protected val token:CancelToken=object:CancelToken{override val isCancelled=false;override fun throwIfCancelled()=Unit}
    protected fun <T> success(value:Outcome<T>):T=(value as Outcome.Success).value
    protected fun lot(id:String,quantity:Int=0)=OwnedLot(EntityId(id),CardIdentity(Knowledge.Absent("none"),Knowledge.Absent("none"),
        ProviderIdentity(ProviderId("foreign"),"raw","preserved"),Knowledge.Known("Foreign card"),LanguageTag("ja")),
        Knowledge.Unknown("unresolved finish",SnapshotList.empty()),CardCondition.UNKNOWN,"Shelf",quantity,"notes",Revision(0),Knowledge.Absent("unknown date"))
    protected suspend fun committed(patch:CollectionPatch,expected:Long,id:String):InventoryCommit=
        (success(inventory.apply(patch,Revision(expected),EntityId(id),token)) as InventoryApplyResult.Committed).commit
    protected fun patch(vararg edits:CollectionEdit)=CollectionPatch(SnapshotList.of(*edits))
    @Test fun initialEmptyReceiptIsReplayableWithoutCreatingAnUndoGroup()=runBlocking{
        val initial=success(inventory.snapshot(token));assertTrue(initial.complete);assertTrue(initial.lots.isEmpty());assertEquals(Revision(0),initial.revision)
        val empty=committed(patch(),0,"empty");assertEquals(Revision(0),empty.projection.resultRevision);assertEquals(UtcTimestamp(0),empty.modifiedAt)
        assertEquals(empty,committed(patch(),0,"empty"))
        assertEquals(UnavailableReason.NOT_FOUND,(inventory.undo(Revision(0),EntityId("nothing"),token) as Outcome.Unavailable).reason)
        assertEquals(FailureCode.INVALID_INPUT,(inventory.undo(Revision(0),EntityId("empty"),token) as Outcome.Failure).error.code)
        assertEquals(initial,success(inventory.snapshot(token)))
    }
    @Test fun undoPreservesPermanentIdReservationsAndAllowsExplicitNewSubmissionAfterRejection()=runBlocking{
        val added=committed(patch(CollectionEdit.Add(lot("a"))),0,"add")
        assertEquals(0,added.projection.changes.single().after!!.quantity)
        val undone=success(inventory.undo(Revision(1),EntityId("undo"),token));assertEquals(Revision(2),undone.projection.resultRevision)
        assertTrue(success(inventory.snapshot(token)).lots.isEmpty())
        val rejected=success(inventory.apply(patch(CollectionEdit.Add(lot("a"))),Revision(2),EntityId("resubmit"),token)) as InventoryApplyResult.Rejected
        assertEquals(Revision(2),rejected.actualRevision);assertTrue(rejected.issues.any{it.entryId==EntityId("a")&&it.code==InventoryIssueCodes.LOT_ALREADY_EXISTS})
        val accepted=committed(patch(CollectionEdit.Add(lot("b",1))),2,"resubmit")
        assertEquals(Revision(3),accepted.projection.resultRevision)
    }
    @Test fun identicalContentGroupsHaveDistinctUndoPointersAndHistoricalReplayNeverRewindsHead()=runBlocking{
        val first=committed(patch(CollectionEdit.Add(lot("a",2))),0,"first")
        val current=success(inventory.snapshot(token)).lots.single()
        committed(patch(CollectionEdit.Replace(current)),1,"identical")
        success(inventory.undo(Revision(2),EntityId("undo-once"),token))
        assertEquals(2,success(inventory.snapshot(token)).lots.single().quantity)
        success(inventory.undo(Revision(3),EntityId("undo-twice"),token))
        assertTrue(success(inventory.snapshot(token)).lots.isEmpty())
        assertEquals(first,committed(patch(CollectionEdit.Add(lot("a",2))),0,"first"))
        assertEquals(Revision(4),success(inventory.snapshot(token)).revision)
    }
    @Test fun rowIssuesRejectTheWholePatchAndDoNotConsumeOperationId()=runBlocking{
        val outcome=success(inventory.apply(patch(CollectionEdit.Add(lot("a",1)),CollectionEdit.Adjust(EntityId("missing"),1,"reason")),Revision(0),EntityId("same-id"),token))
        val rejected=outcome as InventoryApplyResult.Rejected
        assertTrue(rejected.issues.any{it.code==InventoryIssueCodes.LOT_NOT_FOUND});assertEquals(Revision(0),success(inventory.snapshot(token)).revision)
        assertTrue(success(inventory.snapshot(token)).lots.isEmpty())
        assertEquals(Revision(1),committed(patch(CollectionEdit.Add(lot("a",1))),0,"same-id").projection.resultRevision)
    }
    @Test fun absorbedNewAddIdNeverBecomesSupplyOrReusableAfterUndo()=runBlocking{
        fun known(id:String,qty:Int)=lot(id,qty).copy(identity=lot(id).identity.copy(printingId=Knowledge.Known(CardId(ProviderId("catalog"),"p"))),finish=Knowledge.Known(Finish.FOIL))
        val result=committed(patch(CollectionEdit.Add(known("z",2)),CollectionEdit.Add(known("a",3))),0,"merge")
        assertEquals(EntityId("a"),result.projection.merges.single().survivorLotId)
        val active=success(inventory.snapshot(token)).lots.single();assertEquals(EntityId("a"),active.id);assertEquals(5,active.quantity)
        success(inventory.undo(Revision(1),EntityId("undo-merge"),token))
        val rejected=success(inventory.apply(patch(CollectionEdit.Add(known("z",1))),Revision(2),EntityId("reserved"),token)) as InventoryApplyResult.Rejected
        assertTrue(rejected.issues.any{it.entryId==EntityId("z")&&it.code==InventoryIssueCodes.LOT_ALREADY_EXISTS})
        assertTrue(success(inventory.snapshot(token)).lots.isEmpty())
    }
}
