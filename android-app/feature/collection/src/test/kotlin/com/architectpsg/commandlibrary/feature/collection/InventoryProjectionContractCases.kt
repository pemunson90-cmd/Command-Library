package com.architectpsg.commandlibrary.feature.collection

import com.architectpsg.commandlibrary.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

/** Root-owned spec-derived examples; concrete worker tests must execute these with the real engine. */
abstract class InventoryProjectionContractCases {
    protected abstract fun propose(inventory: InventorySnapshot, patch: CollectionPatch, expected: Revision, cancel: CancelToken): Outcome<InventoryProjection>
    private val token=object:CancelToken { override val isCancelled=false; override fun throwIfCancelled()=Unit }
    private fun lot(id:String,quantity:Int=1)=OwnedLot(EntityId(id),CardIdentity(Knowledge.Known(OracleId(ProviderId("cards"),"oracle")),
        Knowledge.Known(CardId(ProviderId("cards"),"printing")),null,Knowledge.Known("Synthetic card"),LanguageTag.ENGLISH),
        Knowledge.Known(Finish.NONFOIL),CardCondition.NEAR_MINT,"Shelf",quantity,"notes",Revision(0),Knowledge.Absent("none"))
    private fun inventory(vararg lots:OwnedLot,revision:Long=7)=InventorySnapshot(Revision(revision),UtcTimestamp(1),SnapshotList.of(*lots),true)
    private fun patch(vararg edits:CollectionEdit)=CollectionPatch(SnapshotList.of(*edits))
    private fun project(i:InventorySnapshot,p:CollectionPatch)=(propose(i,p,i.revision,token) as Outcome.Success).value
    @Test fun originalCanonicalExistingSurvivorAndOutputAreIndependentOfInputOrder() {
        val a=lot("a",2).copy(revision=Revision(2));val z=lot("z",4);val incoming=lot("incoming",3)
        val first=project(inventory(z,a),patch(CollectionEdit.Add(incoming)))
        assertEquals(Revision(7),first.sourceRevision);assertEquals(Revision(8),first.resultRevision)
        assertEquals(SnapshotList.of(InventoryLotChange(a.id,a,a.copy(quantity=5,revision=Revision(8)))),first.changes)
        assertEquals(SnapshotList.of(InventoryMerge(incoming.id,a.id,3)),first.merges);assertTrue(first.issues.isEmpty())
        assertEquals(first,project(inventory(a,z),patch(CollectionEdit.Add(incoming))))
        assertEquals(2,a.quantity);assertEquals(4,z.quantity);assertEquals(3,incoming.quantity)
    }
    @Test fun unknownPrintingOrFinishNeverProvesMergeAndDifferentRetainedMetadataIsNotDiscarded() {
        val known=lot("existing")
        val unknown=Knowledge.Unknown("unresolved",SnapshotList.of(Evidence("synthetic evidence",null,null)))
        val variants=listOf(known.copy(finish=unknown),known.copy(finish=Knowledge.Absent("none")),
            known.copy(identity=known.identity.copy(printingId=unknown,raw=ProviderIdentity(ProviderId("foreign"),"printing","raw"))),
            known.copy(notes="Different notes"),known.copy(acquiredAt=Knowledge.Known(UtcTimestamp(9))),
            known.copy(identity=known.identity.copy(language=LanguageTag("ja"))),
            known.copy(identity=known.identity.copy(printingId=Knowledge.Known(CardId(ProviderId("other"),"printing")))))
        for(original in variants){
            val incoming=original.copy(id=EntityId("new"))
            val base=if(original.finish is Knowledge.Known && original.identity.printingId is Knowledge.Known)known else original
            val result=project(inventory(base),patch(CollectionEdit.Add(incoming)))
            assertTrue(result.merges.isEmpty());assertEquals(incoming.copy(revision=Revision(8)),result.changes.single().after)
            assertNull(result.changes.single().before)
        }
        val unresolvedMetadata=known.copy(identity=known.identity.copy(oracleId=unknown,originalName=unknown),acquiredAt=unknown)
        val compatible=project(inventory(unresolvedMetadata),patch(CollectionEdit.Add(unresolvedMetadata.copy(id=EntityId("compatible")))))
        assertEquals(unresolvedMetadata.copy(quantity=2,revision=Revision(8)),compatible.changes.single().after)
        assertEquals(1,compatible.merges.size) // Known printing/finish proves identity; identical retained unknown metadata stays intact.
    }
    @Test fun lowestNewIdSurvivesAndExplicitZeroQuantityAddStillTouchesExistingSurvivor() {
        val a=lot("a",3);val b=lot("b",4)
        val first=project(inventory(),patch(CollectionEdit.Add(b),CollectionEdit.Add(a)))
        assertEquals(SnapshotList.of(InventoryLotChange(a.id,null,a.copy(quantity=7,revision=Revision(8)))),first.changes)
        assertEquals(SnapshotList.of(InventoryMerge(b.id,a.id,4)),first.merges)
        assertEquals(first,project(inventory(),patch(CollectionEdit.Add(a),CollectionEdit.Add(b))))
        val existing=lot("existing",Int.MAX_VALUE);val zero=lot("zero",0)
        val noQuantity=project(inventory(existing),patch(CollectionEdit.Add(zero)))
        assertEquals(existing.copy(revision=Revision(8)),noQuantity.changes.single().after)
        assertEquals(0,noQuantity.merges.single().quantityAdded)
    }
    @Test fun selectedReplaceAdjustAndRemoveKeepCompleteBeforeImagesAndDoNotMergeUnselectedRows() {
        val a=lot("a",2).copy(revision=Revision(3));val b=lot("b",3);val c=lot("c",4)
        val result=project(inventory(c,b,a),patch(CollectionEdit.Remove(c.id,"remove"),CollectionEdit.Adjust(b.id,-3,"zero"),
            CollectionEdit.Replace(a.copy(notes="revised"))))
        assertEquals(listOf(a.id,b.id,c.id),result.changes.map{it.lotId});assertEquals(a,result.changes[0].before)
        assertEquals(a.copy(notes="revised",revision=Revision(8)),result.changes[0].after)
        assertEquals(b.copy(quantity=0,revision=Revision(8)),result.changes[1].after)
        assertEquals(c,result.changes[2].before);assertNull(result.changes[2].after);assertTrue(result.merges.isEmpty())
        val noOp=project(inventory(a),patch(CollectionEdit.Adjust(a.id,0,"no quantity change")))
        assertEquals(a.copy(revision=Revision(8)),noOp.changes.single().after)
    }
    @Test fun rowIssuesNeverYieldPartialChangesAndAreDeterministicAcrossPatchOrder() {
        val a=lot("a",2);val missing=EntityId("missing")
        val one=CollectionEdit.Adjust(a.id,-3,"negative");val two=CollectionEdit.Remove(missing,"missing")
        val result=project(inventory(a),patch(two,one))
        assertTrue(result.changes.isEmpty());assertTrue(result.merges.isEmpty())
        assertEquals(listOf(a.id,missing),result.issues.map{it.entryId})
        assertEquals(listOf(InventoryIssueCodes.QUANTITY_RANGE,InventoryIssueCodes.LOT_NOT_FOUND),result.issues.map{it.code})
        assertTrue(result.issues.all{it.severity==IssueSeverity.ERROR && it.evidence.isEmpty()})
        assertEquals(result,project(inventory(a),patch(one,two)))
    }
    @Test fun selectedLowestOriginalSurvivorIsAnOverlapErrorWithoutFallbackToAnotherExistingRow() {
        val a=lot("a");val z=lot("z");val incoming=lot("incoming",2)
        val result=project(inventory(z,a),patch(CollectionEdit.Remove(a.id,"selected"),CollectionEdit.Add(incoming)))
        assertTrue(result.changes.isEmpty());assertTrue(result.merges.isEmpty())
        assertEquals(listOf(a.id,incoming.id),result.issues.filter{it.code==InventoryIssueCodes.MERGE_TARGET_SELECTED}.map{it.entryId})
    }
    @Test fun mergedOverflowIsAttributedToEveryIncomingAddAndNoOriginalRowIsChanged() {
        val original=lot("existing",Int.MAX_VALUE);val a=lot("a");val b=lot("b")
        val result=project(inventory(original),patch(CollectionEdit.Add(b),CollectionEdit.Add(a)))
        assertTrue(result.changes.isEmpty());assertTrue(result.merges.isEmpty())
        assertEquals(listOf(a.id,b.id),result.issues.filter{it.code==InventoryIssueCodes.QUANTITY_OVERFLOW}.map{it.entryId})
        assertEquals(Int.MAX_VALUE,original.quantity)
    }
    @Test fun emptyPatchDoesNotAdvanceMaxRevisionAndCancellationPrecedesAnyInputWork() {
        val source=inventory(revision=Long.MAX_VALUE)
        val empty=project(source,patch());assertEquals(source.revision,empty.resultRevision);assertTrue(empty.changes.isEmpty());assertTrue(empty.issues.isEmpty())
        val exhausted=propose(source,patch(CollectionEdit.Add(lot("new"))),source.revision,token) as Outcome.Failure
        assertEquals(FailureCode.PERSISTENCE,exhausted.error.code)
        val cancelled=object:CancelToken {override val isCancelled=true;override fun throwIfCancelled(){throw CancellationException("Synthetic canceled")}}
        try{propose(source.copy(complete=false),patch(),source.revision,cancelled);fail("Cancellation must propagate")}catch(_:CancellationException){}
        val stale=propose(inventory(),patch(),Revision(0),token) as Outcome.Failure
        assertEquals(FailureCode.STALE_REVISION,stale.error.code)
    }
}
