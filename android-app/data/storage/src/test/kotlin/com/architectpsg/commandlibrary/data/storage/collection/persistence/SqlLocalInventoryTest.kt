package com.architectpsg.commandlibrary.data.storage.collection.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.collection.codec.OwnedLotPayloadCodec
import com.architectpsg.commandlibrary.data.storage.collection.codec.LocalInventoryPatchCodec
import com.architectpsg.commandlibrary.feature.collection.persistence.SqlLocalInventory
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

/** Inherits all five root cases against real SQLite, then exercises durability/hostile storage. */
class SqlLocalInventoryTest: LocalInventoryContractCases() {
    private val codec=OwnedLotPayloadCodec()
    private val patchCodec=LocalInventoryPatchCodec()
    private val file=File.createTempFile("inventory-",".sqlite",File("/tmp/cl-inventory-store-01/tmp").also { it.mkdirs() }).also { it.deleteOnExit() }
    private val db=InventoryHostDatabase(file)
    private var calls=0
    private fun clock(action: ()->UtcTimestamp)=object:Clock { override fun utcNow()=action();override fun monotonicNow()=Duration.ZERO }
    private val time=clock { calls++;UtcTimestamp(100+calls.toLong(),123) }
    override val inventory:LocalInventory=adapter(db)
    private fun adapter(db:InventoryHostDatabase,time:Clock=this.time,codec:OwnedLotPayloadCodecPort=this.codec):LocalInventory=SqlLocalInventory(db,codec,patchCodec,time,db.maxParameters)
    @After fun close()=db.close()
    private fun failure(result:Outcome<*>,code:FailureCode) { assertTrue(result.toString(),result is Outcome.Failure);assertEquals(code,(result as Outcome.Failure).error.code) }
    private fun commit(result:Outcome<InventoryApplyResult>):InventoryCommit=(success(result) as InventoryApplyResult.Committed).commit
    private suspend fun apply(lots:LocalInventory,patch:CollectionPatch,revision:Long,id:String)=commit(lots.apply(patch,Revision(revision),EntityId(id),token))
    private fun known(id:String,quantity:Int)=lot(id,quantity).copy(identity=lot(id).identity.copy(printingId=Knowledge.Known(CardId(ProviderId("foreign"),"printing/界"))),finish=Knowledge.Known(Finish.FOIL))

    @Test fun completeMoreThan101RowsAndParameterBoundedReservationsAreConsistent()=runBlocking {
        val lots=(0..204).map { lot("id-%03d".format(it),it) }
        apply(inventory,CollectionPatch(SnapshotList.copyOf(lots.map { CollectionEdit.Add(it) })),0,"bulk")
        val snapshot=success(inventory.snapshot(token));assertEquals(205,snapshot.lots.size)
        assertEquals(lots.map { it.copy(revision=Revision(1)) },snapshot.lots);assertEquals(1,calls)
        val first=snapshot.lots.first()
        apply(inventory,patch(CollectionEdit.Adjust(first.id,0,"zero")),1,"zero")
        assertEquals(Revision(2),success(inventory.snapshot(token)).lots.first().revision)
        assertEquals(Revision(1),success(inventory.snapshot(token)).lots.last().revision)
    }
    @Test fun immutableActualParameterCapabilityIsObtainedOutsideInventoryTransactions()=runBlocking {
        val actual=success(db.capabilities(token));assertEquals(db.maxParameters,actual.maxBoundParameters)
        val before=db.capabilityCalls
        val service=SqlLocalInventory(db,codec,patchCodec,time,actual.maxBoundParameters)
        apply(service,patch(CollectionEdit.Add(lot("a",1))),0,"add")
        success(service.snapshot(token));success(service.undo(Revision(1),EntityId("undo"),token))
        assertEquals(before,db.capabilityCalls)
    }
    @Test fun existingMergeReservesAbsorbedIdentityAndUndoDoesNotRewriteReservation()=runBlocking {
        apply(inventory,patch(CollectionEdit.Add(known("a",2))),0,"first")
        val merged=apply(inventory,patch(CollectionEdit.Add(known("z",3))),1,"merge")
        assertEquals(5,merged.projection.changes.single().after!!.quantity)
        val reserved=db.bytes("SELECT payload FROM inventory_lot WHERE lot_id='z'")
        success(inventory.undo(Revision(2),EntityId("undo"),token))
        assertArrayEquals(reserved,db.bytes("SELECT payload FROM inventory_lot WHERE lot_id='z'"))
        assertEquals(2,success(inventory.snapshot(token)).lots.single().quantity)
        val rejected=success(inventory.apply(patch(CollectionEdit.Add(known("z",4))),Revision(3),EntityId("reuse"),token)) as InventoryApplyResult.Rejected
        assertEquals(listOf(InventoryIssueCodes.LOT_ALREADY_EXISTS),rejected.issues.map { it.code })
    }
    @Test fun removeKeepsFullInactivePayloadAndUndoRestoresAtNewRevision()=runBlocking {
        apply(inventory,patch(CollectionEdit.Add(lot("a",0))),0,"first")
        apply(inventory,patch(CollectionEdit.Remove(EntityId("a"),"reason")),1,"remove")
        assertTrue(success(inventory.snapshot(token)).lots.isEmpty());assertEquals(1L,db.number("SELECT count(*) FROM inventory_lot"))
        val restored=success(inventory.undo(Revision(2),EntityId("undo"),token));assertEquals(lot("a",0).copy(revision=Revision(3)),restored.projection.changes.single().after)
    }
    @Test fun immutableReceiptsChangesGroupsAndInputObjectsSurviveUndoAndBranch()=runBlocking {
        val input=patch(CollectionEdit.Add(known("a",2)),CollectionEdit.Add(known("z",3)))
        val original=input.copy(edits=SnapshotList.copyOf(input.edits))
        val first=apply(inventory,input,0,"merge");val before=db.state()
        success(inventory.undo(Revision(1),EntityId("undo"),token))
        apply(inventory,patch(CollectionEdit.Add(lot("branch",1))),2,"branch")
        val after=db.state()
        for(table in listOf("inventory_operation","inventory_change","inventory_group"))assertEquals(table,before.getValue(table),after.getValue(table).take(before.getValue(table).size))
        assertEquals(original,input);assertEquals(first,apply(inventory,input,0,"merge"))
    }
    @Test fun undoBranchKeepsPriorGroupsAndNeverToggles()=runBlocking {
        apply(inventory,patch(CollectionEdit.Add(lot("a",1))),0,"a")
        apply(inventory,patch(CollectionEdit.Adjust(EntityId("a"),1,"b")),1,"b")
        success(inventory.undo(Revision(2),EntityId("u1"),token))
        apply(inventory,patch(CollectionEdit.Adjust(EntityId("a"),5,"branch")),3,"branch")
        success(inventory.undo(Revision(4),EntityId("u2"),token));assertEquals(1,success(inventory.snapshot(token)).lots.single().quantity)
        success(inventory.undo(Revision(5),EntityId("u3"),token));assertTrue(success(inventory.snapshot(token)).lots.isEmpty())
        assertEquals(3L,db.number("SELECT count(*) FROM inventory_group"))
    }
    @Test fun reopenHistoricalReplayAndCrossConnectionCasDoNotReadClock()=runBlocking {
        val first=apply(inventory,patch(CollectionEdit.Add(lot("a",2))),0,"first")
        val undo=success(inventory.undo(Revision(1),EntityId("undo"),token))
        InventoryHostDatabase(file,false).use { other ->
            val noClock=adapter(other,clock { error("replay/snapshot clock") })
            assertEquals(first,apply(noClock,patch(CollectionEdit.Add(lot("a",2))),0,"first"))
            assertEquals(undo,success(noClock.undo(Revision(1),EntityId("undo"),token)))
            assertEquals(Revision(2),success(noClock.snapshot(token)).revision)
            val state=other.state();failure(noClock.apply(patch(),Revision(1),EntityId("stale"),token),FailureCode.STALE_REVISION)
            failure(noClock.undo(Revision(1),EntityId("stale-u"),token),FailureCode.STALE_REVISION);assertEquals(state,other.state())
        }
    }
    @Test fun simultaneousIndependentWritersSerializeAndExactlyOneExpectedCasCommits()=runBlocking {
        apply(inventory,patch(),0,"seed")
        InventoryHostDatabase(file,false).use { other ->
            db.sql("PRAGMA busy_timeout=5000");other.sql("PRAGMA busy_timeout=5000")
            val ready=java.util.concurrent.CountDownLatch(2);val start=java.util.concurrent.CountDownLatch(1)
            val tasks=listOf(db to "a",other to "b").map { (store,id) -> async(Dispatchers.Default) {
                ready.countDown();check(start.await(5,java.util.concurrent.TimeUnit.SECONDS))
                adapter(store).apply(patch(CollectionEdit.Add(lot(id,1))),Revision(0),EntityId("writer-$id"),token)
            } }
            assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));start.countDown()
            val results=tasks.map { it.await() }
            assertEquals(1,results.count { it is Outcome.Success && it.value is InventoryApplyResult.Committed })
            assertEquals(1,results.count { it is Outcome.Failure && it.error.code==FailureCode.STALE_REVISION })
            assertEquals(Revision(1),success(inventory.snapshot(token)).revision);assertEquals(1,success(inventory.snapshot(token)).lots.size)
            assertEquals(2L,db.number("SELECT count(*) FROM inventory_operation"))
        }
    }
    @Test fun lostInactiveReservationAfterUndoAndEmptyReceiptCannotReleasePermanentId()=runBlocking {
        apply(inventory,patch(CollectionEdit.Add(lot("a",1))),0,"add")
        success(inventory.undo(Revision(1),EntityId("undo"),token));apply(inventory,patch(),2,"empty")
        db.sql("DELETE FROM inventory_lot WHERE lot_id='a'")
        assertEquals(0L,db.number("SELECT count(*) FROM pragma_foreign_key_check"));val state=db.state()
        failure(inventory.apply(patch(CollectionEdit.Add(lot("a",1))),Revision(2),EntityId("reuse"),token),FailureCode.CORRUPT_DATA)
        assertEquals(state,db.state())
    }
    @Test fun unknownAcknowledgementReconcilesApplyAndUndoExactlyOnce()=runBlocking {
        db.loseAcknowledgement=true
        try { inventory.apply(patch(CollectionEdit.Add(lot("a",1))),Revision(0),EntityId("first"),token);fail() }catch(_:CancellationException){}
        db.loseAcknowledgement=false;val state=db.state();apply(inventory,patch(CollectionEdit.Add(lot("a",1))),0,"first");assertEquals(state,db.state())
        db.loseAcknowledgement=true
        try { inventory.undo(Revision(1),EntityId("undo"),token);fail() }catch(_:CancellationException){}
        db.loseAcknowledgement=false;val undone=db.state();success(inventory.undo(Revision(1),EntityId("undo"),token));assertEquals(undone,db.state())
    }
    @Test fun receiptReplayPrecedesCorruptCurrentAndCrossMethodReuseIsInvalid()=runBlocking {
        val added=apply(inventory,patch(CollectionEdit.Add(lot("a",1))),0,"first")
        val undone=success(inventory.undo(Revision(1),EntityId("undo"),token))
        db.sql("UPDATE inventory_state SET revision=99");val state=db.state()
        assertEquals(added,apply(inventory,patch(CollectionEdit.Add(lot("a",1))),0,"first"));assertEquals(undone,success(inventory.undo(Revision(1),EntityId("undo"),token)))
        failure(inventory.undo(Revision(0),EntityId("first"),token),FailureCode.INVALID_INPUT)
        failure(inventory.apply(patch(),Revision(0),EntityId("undo"),token),FailureCode.INVALID_INPUT);assertEquals(state,db.state())
    }
    @Test fun emptyReceiptAndRowRejectionNeverReadClockOrWriteRejectedSubset()=runBlocking {
        val noClock=adapter(db,clock { error("no clock") })
        apply(noClock,patch(),0,"empty");val state=db.state()
        val result=success(noClock.apply(patch(CollectionEdit.Add(lot("a",1)),CollectionEdit.Remove(EntityId("missing"),"reason")),Revision(0),EntityId("reject"),token))
        assertTrue(result is InventoryApplyResult.Rejected);assertEquals(state,db.state());assertEquals(0,calls)
    }
    @Test fun missingStateAfterEmptyReceiptAndDeletedActiveRowsFailClosed()=runBlocking {
        apply(inventory,patch(),0,"empty");db.sql("DELETE FROM inventory_state")
        failure(inventory.snapshot(token),FailureCode.CORRUPT_DATA)
        db.sql("DELETE FROM inventory_operation");db.sql("DELETE FROM sqlite_sequence")
        apply(inventory,patch(CollectionEdit.Add(lot("a",1))),0,"first")
        db.sql("DELETE FROM inventory_lot");failure(inventory.snapshot(token),FailureCode.CORRUPT_DATA)
    }
    @Test fun identicalReplaceCannotMaskValidForeignKeyStalePointer()=runBlocking {
        apply(inventory,patch(CollectionEdit.Add(lot("a",1))),0,"first")
        val current=success(inventory.snapshot(token)).lots.single()
        apply(inventory,patch(CollectionEdit.Replace(current)),1,"same")
        db.sql("UPDATE inventory_state SET current_group_sequence=1")
        assertEquals(0L,db.number("SELECT count(*) FROM pragma_foreign_key_check"));val state=db.state()
        failure(inventory.snapshot(token),FailureCode.CORRUPT_DATA);failure(inventory.undo(Revision(2),EntityId("bad"),token),FailureCode.CORRUPT_DATA);assertEquals(state,db.state())
    }
    @Test fun validForeignKeyWriterSwapToEmptyReceiptAtSameRevisionFailsClosed()=runBlocking {
        apply(inventory,patch(CollectionEdit.Add(lot("a",1))),0,"add")
        apply(inventory,patch(),1,"empty")
        apply(inventory,patch(CollectionEdit.Add(lot("b",1))),1,"later")
        db.sql("UPDATE inventory_lot SET last_operation_sequence=2 WHERE lot_id='a'")
        assertEquals(0L,db.number("SELECT count(*) FROM pragma_foreign_key_check"));val state=db.state()
        failure(inventory.snapshot(token),FailureCode.CORRUPT_DATA)
        failure(inventory.apply(patch(),Revision(2),EntityId("bad"),token),FailureCode.CORRUPT_DATA)
        assertEquals(state,db.state())
    }
    @Test fun descriptorAndPhysicalImageGoldenEmptyEncodingIsIndependentlyFixed()=runBlocking {
        apply(inventory,patch(),0,"empty")
        assertEquals("434c49440001000000000000000000000000",InventoryHostDatabase.hex(db.bytes("SELECT descriptor FROM inventory_operation")))
        // Independently calculated SHA256 of CLIH1 + signed u32 zero (43 4c 49 48 00 01 00 00 00 00).
        db.host.connection.prepare("SELECT changes_sha256,content_sha256 FROM inventory_operation").use { assertTrue(it.step());assertEquals("80d68b5db2727f4071b8b9ae444f313fed96b77d6097b1fee0e0c3323da9fa55",it.getText(0));assertEquals("2e0e4d9dd52229b05360632f433de6801084f8f4da2074e1d225163c3ed954e7",it.getText(1)) }
        apply(inventory,patch(CollectionEdit.Add(lot("a",1))),0,"add")
        assertEquals("434c4944000100000001000000016100000000000000010000000161",InventoryHostDatabase.hex(db.bytes("SELECT descriptor FROM inventory_operation WHERE operation_id='add'")))
    }
    @Test fun faultsAtEveryWritePreserveAllFiveTablesAndAutoincrementSequence()=runBlocking { atomic(false) }
    @Test fun cancellationAtEveryWriteEscapesAndPreservesAllFiveTablesAndSequence()=runBlocking { atomic(true) }
    private suspend fun atomic(cancellation:Boolean) {
        // Include multi-row merge reservation effects and multi-row undo, not just one-row shapes.
        for(mode in 0..4) for(write in 1..listOf(5,4,2,7,6)[mode]) {
            val local=File.createTempFile("inventory-fault-",".sqlite",file.parentFile).also { it.deleteOnExit() }
            InventoryHostDatabase(local).use { store ->
                val service=adapter(store)
                if(mode==1)apply(service,patch(CollectionEdit.Add(lot("a",1))),0,"seed")
                if(mode==4)apply(service,patch(CollectionEdit.Add(lot("a",1)),CollectionEdit.Add(lot("b",1))),0,"seed")
                val state=store.state();var writes=0;val cancelled=CancellationException("injected")
                store.host.afterWrite={ if(++writes==write) { if(cancellation)throw cancelled else throw IllegalStateException("injected") } }
                try {
                    val result=if(mode==1 || mode==4)service.undo(Revision(1),EntityId("fault"),token)
                        else service.apply(if(mode==2)patch() else if(mode==3)patch(CollectionEdit.Add(known("a",2)),CollectionEdit.Add(known("z",3))) else patch(CollectionEdit.Add(lot("b",1))),Revision(0),EntityId("fault"),token)
                    if(cancellation)fail("Expected cancellation") else failure(result,FailureCode.PERSISTENCE)
                }catch(actual:CancellationException) { if(!cancellation)throw actual;assertSame(cancelled,actual) }
                store.host.afterWrite=null;assertEquals("mode=$mode write=$write",state,store.state())
            }
        }
    }
    @Test fun corruptTypeLengthDigestCountAndDescriptorBudgetFailBeforeHistoricalBlobFetch()=runBlocking {
        val corruptions=listOf("UPDATE inventory_operation SET descriptor_length=4194305","UPDATE inventory_operation SET change_count=2049","UPDATE inventory_change SET before_present=2",
            "UPDATE inventory_change SET after_payload_length=8388609","UPDATE inventory_operation SET descriptor_sha256='invalid'","UPDATE inventory_change SET after_payload_sha256='invalid'","UPDATE inventory_lot SET payload_length=8388609")
        for(sql in corruptions) {
            val local=File.createTempFile("inventory-corrupt-",".sqlite",file.parentFile).also { it.deleteOnExit() }
            InventoryHostDatabase(local).use { store ->
                val service=adapter(store);apply(service,patch(CollectionEdit.Add(lot("a",1))),0,"seed")
                store.sql(sql);val state=store.state();store.host.blobQueries=0
                failure(service.snapshot(token),FailureCode.CORRUPT_DATA)
                if(!sql.contains("inventory_lot"))assertEquals(sql,0,store.host.blobQueries)
                assertEquals(state,store.state())
            }
        }
    }
    @Test fun bothHistorySidesAndDescriptorShareOneAggregateBudgetBeforeAnyBlobRead()=runBlocking {
        apply(inventory,patch(CollectionEdit.Add(lot("a",1)),CollectionEdit.Add(lot("b",1))),0,"add")
        apply(inventory,patch(CollectionEdit.Adjust(EntityId("a"),0,"same"),CollectionEdit.Adjust(EntityId("b"),0,"same")),1,"adjust")
        // Four individually permitted 4MiB SQL image sizes plus the descriptor exceed16MiB.
        db.sql("UPDATE inventory_change SET before_payload_length=4194304,before_payload=zeroblob(4194304),after_payload_length=4194304,after_payload=zeroblob(4194304) WHERE operation_sequence=2")
        val state=db.state();db.host.blobQueries=0
        failure(inventory.snapshot(token),FailureCode.CORRUPT_DATA);assertEquals(0,db.host.blobQueries);assertEquals(state,db.state())
        db.host.blobQueries=0
        failure(inventory.apply(patch(CollectionEdit.Adjust(EntityId("a"),0,"same"),CollectionEdit.Adjust(EntityId("b"),0,"same")),Revision(1),EntityId("adjust"),token),FailureCode.CORRUPT_DATA)
        assertEquals(0,db.host.blobQueries)
    }
    @Test fun clockFailureBackwardEqualAndCancellationHaveExactPolicy()=runBlocking {
        apply(inventory,patch(CollectionEdit.Add(lot("a",1))),0,"seed");val state=db.state()
        for(bad in listOf(clock { throw IllegalStateException("private clock") },clock { UtcTimestamp(0) })) {
            failure(adapter(db,bad).apply(patch(CollectionEdit.Adjust(EntityId("a"),0,"reason")),Revision(1),EntityId("clock"),token),FailureCode.INTERNAL);assertEquals(state,db.state())
            failure(adapter(db,bad).undo(Revision(1),EntityId("clock-u"),token),FailureCode.INTERNAL);assertEquals(state,db.state())
        }
        val cancelled=CancellationException("clock")
        try { adapter(db,clock { throw cancelled }).undo(Revision(1),EntityId("cancel"),token);fail() }catch(actual:CancellationException) { assertSame(cancelled,actual) };assertEquals(state,db.state())
        val equal=success(inventory.snapshot(token)).capturedAt
        assertEquals(equal,apply(adapter(db,clock { equal }),patch(CollectionEdit.Adjust(EntityId("a"),0,"same")),1,"equal").modifiedAt)
    }
    @Test fun codecFailureAndCancellationNeverCommitPartialRows()=runBlocking {
        val broken=object:OwnedLotPayloadCodecPort { override fun encodeOwnedLot(value:OwnedLot):Outcome<SnapshotBytes> = Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT,"Codec unavailable.",SnapshotList.empty()));override fun decodeOwnedLot(payload:SnapshotBytes)=codec.decodeOwnedLot(payload) }
        val state=db.state();failure(adapter(db,codec=broken).apply(patch(CollectionEdit.Add(lot("a",1))),Revision(0),EntityId("bad"),token),FailureCode.INVALID_INPUT);assertEquals(state,db.state())
        val cancelled=CancellationException("codec")
        val cancelCodec=object:OwnedLotPayloadCodecPort { override fun encodeOwnedLot(value:OwnedLot):Outcome<SnapshotBytes> = throw cancelled;override fun decodeOwnedLot(payload:SnapshotBytes)=codec.decodeOwnedLot(payload) }
        try { adapter(db,codec=cancelCodec).apply(patch(CollectionEdit.Add(lot("a",1))),Revision(0),EntityId("cancel"),token);fail() }catch(actual:CancellationException) { assertSame(cancelled,actual) };assertEquals(state,db.state())
    }
    @Test fun globalRevisionExhaustionAndEmptyAtMaxHaveNoClockOrOverflow()=runBlocking {
        apply(inventory,patch(),0,"empty")
        db.sql("UPDATE inventory_operation SET expected_revision=9223372036854775807,result_revision=9223372036854775807")
        db.sql("UPDATE inventory_state SET revision=9223372036854775807")
        val noClock=adapter(db,clock { error("max clock") });val state=db.state()
        failure(noClock.apply(patch(CollectionEdit.Add(lot("a",1))),Revision(Long.MAX_VALUE),EntityId("max"),token),FailureCode.PERSISTENCE)
        assertEquals(state,db.state());assertEquals(Revision(Long.MAX_VALUE),apply(noClock,patch(),Long.MAX_VALUE,"new-empty").projection.resultRevision)
    }
    @Test fun cancellationRaisedByTokenImmediatelyAfterCodecEscapesWithoutWrites()=runBlocking {
        var cancelled=false;val exception=CancellationException("after codec")
        val token=object:CancelToken { override val isCancelled get()=cancelled;override fun throwIfCancelled() { if(cancelled)throw exception } }
        val port=object:OwnedLotPayloadCodecPort {
            override fun encodeOwnedLot(value:OwnedLot):Outcome<SnapshotBytes> = codec.encodeOwnedLot(value).also { cancelled=true }
            override fun decodeOwnedLot(payload:SnapshotBytes)=codec.decodeOwnedLot(payload)
        }
        val state=db.state()
        try { adapter(db,codec=port).apply(patch(CollectionEdit.Add(lot("a",1))),Revision(0),EntityId("cancel"),token);fail() }catch(actual:CancellationException) { assertSame(exception,actual) }
        assertEquals(state,db.state())
    }
    @Test fun sequenceExhaustionDoesNotConsumeInputOrClock()=runBlocking {
        apply(inventory,patch(),0,"empty");db.sql("UPDATE sqlite_sequence SET seq=9223372036854775807 WHERE name='inventory_operation'")
        val state=db.state();val noClock=adapter(db,clock { error("exhausted clock") })
        failure(noClock.apply(patch(CollectionEdit.Add(lot("a",1))),Revision(0),EntityId("new"),token),FailureCode.PERSISTENCE)
        assertEquals(state,db.state());assertEquals(Revision(0),apply(noClock,patch(),0,"empty").projection.resultRevision)
    }
}
