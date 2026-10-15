package com.architectpsg.commandlibrary.feature.collection

import com.architectpsg.commandlibrary.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class InventoryPatchReducerTest : InventoryProjectionContractCases() {
    override fun propose(inventory: InventorySnapshot, patch: CollectionPatch, expected: Revision, cancel: CancelToken) =
        InventoryPatchReducer.propose(inventory, patch, expected, cancel)
    private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private fun lot(id: String, quantity: Int = 1) = OwnedLot(EntityId(id), CardIdentity(
        Knowledge.Known(OracleId(ProviderId("source"), "oracle")), Knowledge.Known(CardId(ProviderId("source"), "print")),
        ProviderIdentity(ProviderId("foreign"), "card", "raw"), Knowledge.Known("Name"), LanguageTag.ENGLISH),
        Knowledge.Known(Finish.NONFOIL), CardCondition.UNKNOWN, "shelf", quantity, "notes", Revision(0), Knowledge.Absent("unrecorded"))
    private fun inventory(vararg lots: OwnedLot, revision: Long = 5) = InventorySnapshot(Revision(revision), UtcTimestamp(0), SnapshotList.of(*lots), true)
    private fun patch(vararg edits: CollectionEdit) = CollectionPatch(SnapshotList.of(*edits))
    private fun project(i: InventorySnapshot, p: CollectionPatch): InventoryProjection = (propose(i, p, i.revision, token) as Outcome.Success).value
    private fun failure(i: InventorySnapshot, p: CollectionPatch = patch()): DomainFailure = (propose(i, p, i.revision, token) as Outcome.Failure).error

    @Test fun duplicateStoredIdsAndFutureStoredRevisionAreShapeFailures() {
        val a = lot("a")
        assertEquals(FailureCode.INVALID_INPUT, failure(inventory(a, a)).code)
        assertEquals(FailureCode.INVALID_INPUT, failure(inventory(a.copy(revision = Revision(6)))).code)
        assertEquals(FailureCode.INVALID_INPUT, failure(inventory().copy(complete = false)).code)
    }
    @Test fun duplicateSelectionsCollectDistinctCodesAndRejectAllRows() {
        val a = lot("a"); val invalid = a.copy(revision = Revision(4))
        val result = project(inventory(a), patch(CollectionEdit.Add(invalid), CollectionEdit.Add(invalid), CollectionEdit.Remove(EntityId("missing"), "reason")))
        assertTrue(result.changes.isEmpty()); assertTrue(result.merges.isEmpty())
        assertEquals(listOf("DUPLICATE_SELECTION", "INVALID_ADD_REVISION", "LOT_ALREADY_EXISTS", "LOT_NOT_FOUND"), result.issues.map { it.code })
        assertTrue(result.issues.all { it.severity == IssueSeverity.ERROR && it.evidence.isEmpty() })
        assertEquals(result, project(inventory(a), patch(CollectionEdit.Remove(EntityId("missing"), "reason"), CollectionEdit.Add(invalid), CollectionEdit.Add(invalid))))
    }
    @Test fun staleReplaceAndAdjustBothBoundsAreRowErrors() {
        val a = lot("a").copy(revision = Revision(2)); val b = lot("b", Int.MAX_VALUE); val c = lot("c")
        val result = project(inventory(a,b,c), patch(CollectionEdit.Replace(a.copy(revision = Revision(1))), CollectionEdit.Adjust(b.id, 1, "overflow"), CollectionEdit.Adjust(c.id, Int.MIN_VALUE, "underflow")))
        assertEquals(listOf("STALE_LOT", "QUANTITY_RANGE", "QUANTITY_RANGE"), result.issues.map { it.code }); assertTrue(result.changes.isEmpty())
    }
    @Test fun overlapIsReportedForEveryAddAndSelectedReplaceOrAdjustWithoutFallback() {
        for (selection in listOf<CollectionEdit>(CollectionEdit.Replace(lot("a")), CollectionEdit.Adjust(EntityId("a"), 0, "unchanged"))) {
            val i = inventory(lot("z"), lot("a"))
            val result = project(i, patch(selection, CollectionEdit.Add(lot("b")), CollectionEdit.Add(lot("c"))))
            assertEquals(listOf("a","b","c"), result.issues.filter { it.code == "MERGE_TARGET_SELECTED" }.map { it.entryId!!.value })
            assertTrue(result.changes.isEmpty()); assertTrue(result.merges.isEmpty())
        }
    }
    @Test fun newGroupOverflowIncludesItsMinimumNewSurvivorAndEachAbsorbedAdd() {
        val result = project(inventory(), patch(CollectionEdit.Add(lot("c",1)), CollectionEdit.Add(lot("a",Int.MAX_VALUE)), CollectionEdit.Add(lot("b",1))))
        assertEquals(listOf("a","b","c"), result.issues.map { it.entryId!!.value }); assertTrue(result.issues.all { it.code == "QUANTITY_OVERFLOW" })
    }
    @Test fun selectedZeroAndIdenticalReplaceTouchButRemoveIsNull() {
        val a=lot("a",0); val b=lot("b",0); val c=lot("c",0)
        val result=project(inventory(a,b,c),patch(CollectionEdit.Replace(a),CollectionEdit.Adjust(b.id,0,"zero"),CollectionEdit.Remove(c.id,"remove")))
        assertEquals(a.copy(revision=Revision(6)),result.changes[0].after)
        assertEquals(b.copy(revision=Revision(6)),result.changes[1].after); assertNull(result.changes[2].after)
    }
    @Test fun exactUnknownEvidenceRawAndAllRetainedFieldsParticipateInPhysicalIdentity() {
        val unknown = Knowledge.Unknown("unknown", SnapshotList.of(Evidence("evidence",ResourceUri("urn:test"),UtcTimestamp(11,2))))
        val base = lot("existing").let { it.copy(identity=it.identity.copy(oracleId=unknown,originalName=unknown),acquiredAt=unknown) }
        val changedEvidence = Knowledge.Unknown("unknown", SnapshotList.of(Evidence("evidence", ResourceUri("urn:test"), UtcTimestamp(11,3))))
        val fields = listOf(
            base.copy(identity=base.identity.copy(raw=base.identity.raw!!.copy(kind="other"))),
            base.copy(identity=base.identity.copy(raw=null)),
            base.copy(identity=base.identity.copy(oracleId=Knowledge.Absent("unknown"))),
            base.copy(identity=base.identity.copy(originalName=changedEvidence)),
            base.copy(acquiredAt=Knowledge.Unknown("unknown",SnapshotList.of(Evidence("changed",null,null)))),
            base.copy(condition=CardCondition.MINT), base.copy(location="shelf "), base.copy(notes="Notes"),
            base.copy(identity=base.identity.copy(language=LanguageTag("EN"))),
            base.copy(finish=Knowledge.Known(Finish.FOIL)),
            base.copy(identity=base.identity.copy(printingId=Knowledge.Known(CardId(ProviderId("another"),"print"))))
        )
        for (changed in fields) {
            val incoming=changed.copy(id=EntityId("new"))
            val result=project(inventory(base),patch(CollectionEdit.Add(incoming)))
            assertTrue(result.merges.isEmpty());assertEquals(incoming.copy(revision=Revision(6)),result.changes.single().after)
        }
        val result=project(inventory(base),patch(CollectionEdit.Add(base.copy(id=EntityId("new")))))
        assertEquals(base.copy(quantity=2,revision=Revision(6)),result.changes.single().after)
    }
    @Test fun absentOrUnknownIdentityNeverMergesEvenWithIdenticalRawFields() {
        for (printing in listOf<Knowledge<CardId>>(Knowledge.Absent("absent"), Knowledge.Unknown("unknown",SnapshotList.empty())))
            for (finish in listOf<Knowledge<Finish>>(Knowledge.Known(Finish.NONFOIL),Knowledge.Absent("absent"),Knowledge.Unknown("unknown",SnapshotList.empty()))) {
                val a=lot("a").let { it.copy(identity=it.identity.copy(printingId=printing),finish=finish) }
                val result=project(inventory(a),patch(CollectionEdit.Add(a.copy(id=EntityId("b"))),CollectionEdit.Add(a.copy(id=EntityId("c")))))
                assertEquals(2,result.changes.size);assertTrue(result.merges.isEmpty())
            }
    }
    @Test fun unsignedUtf8OrderingHandlesSupplementaryAndBmpIds() {
        val first="\ue000";val last="\ud800\udc00"
        val result=project(inventory(),patch(CollectionEdit.Add(lot(last)),CollectionEdit.Add(lot(first))))
        assertEquals(first,result.changes.single().lotId.value);assertEquals(last,result.merges.single().requestedLotId.value)
        val errors=project(inventory(),patch(CollectionEdit.Remove(EntityId(last),"why"),CollectionEdit.Remove(EntityId(first),"why")))
        assertEquals(listOf(first,last),errors.issues.map{it.entryId!!.value})
    }
    @Test fun exactIdAndScalarByteLimitsAndMalformedUtf16AreRejectedWithoutUserText() {
        assertTrue(project(inventory(lot("é".repeat(512))),patch()).issues.isEmpty())
        val badIds=listOf("é".repeat(513),"a".repeat(1025),"a\ud800")
        for(id in badIds) {
            val result=failure(inventory(lot(id)));assertEquals(FailureCode.INVALID_INPUT,result.code);assertTrue(result.evidence.isEmpty());assertFalse(result.message.contains(id))
        }
        val atLimit=lot("valid").copy(notes="x".repeat(InventoryProjectionLimits.MAX_TEXT_BYTES))
        assertTrue(project(inventory(atLimit),patch()).changes.isEmpty())
        assertEquals(FailureCode.INVALID_INPUT,failure(inventory(atLimit.copy(notes=atLimit.notes+"x"))).code)
    }
    @Test fun rawKindUsesOrdinaryTextLimitAndIsPreservedThroughPhysicalMerge() {
        val ordinaryKind = "k".repeat(1025)
        val base = lot("existing").let { it.copy(identity=it.identity.copy(raw=it.identity.raw!!.copy(kind=ordinaryKind))) }
        assertTrue(project(inventory(base),patch()).issues.isEmpty())
        val incoming = base.copy(id=EntityId("new"),quantity=2)
        val result = project(inventory(base),patch(CollectionEdit.Add(incoming)))
        assertEquals(base.copy(quantity=3,revision=Revision(6)),result.changes.single().after)
        assertEquals(ordinaryKind,result.changes.single().after!!.identity.raw!!.kind)
        assertEquals(InventoryMerge(incoming.id,base.id,2),result.merges.single())
        val badProvider = base.copy(identity=base.identity.copy(raw=base.identity.raw!!.copy(provider=ProviderId("p".repeat(1025)))))
        val badRawId = base.copy(identity=base.identity.copy(raw=base.identity.raw!!.copy(rawId="r".repeat(1025))))
        assertEquals(FailureCode.INVALID_INPUT,failure(inventory(badProvider)).code)
        assertEquals(FailureCode.INVALID_INPUT,failure(inventory(badRawId)).code)
    }
    @Test fun totalTextAndNestedItemLimitsAreFiniteAndTyped() {
        val notes="x".repeat(InventoryProjectionLimits.MAX_TEXT_BYTES)
        val many=List(64){lot("lot-$it").copy(notes=notes)}
        assertEquals(FailureCode.INVALID_INPUT,failure(inventory().copy(lots=SnapshotList.copyOf(many))).code)
        val e=Evidence("e",null,null)
        val heavy=lot("heavy").copy(acquiredAt=Knowledge.Unknown("reason",SnapshotList.copyOf(List(InventoryProjectionLimits.MAX_COLLECTION_ELEMENTS){e})))
        assertEquals(FailureCode.INVALID_INPUT,failure(inventory(heavy)).code)
    }
    @Test fun lotAndEditCountLimitsRejectBeforeTraversal() {
        val a=lot("a")
        assertEquals(FailureCode.INVALID_INPUT,failure(inventory().copy(lots=SnapshotList.copyOf(List(InventoryProjectionLimits.MAX_LOTS+1){a}))).code)
        assertEquals(FailureCode.INVALID_INPUT,failure(inventory(),CollectionPatch(SnapshotList.copyOf(List(InventoryProjectionLimits.MAX_EDITS+1){CollectionEdit.Add(a)}))).code)
    }
    @Test fun workBudgetExhaustionNeverReturnsPartialProjection() {
        val notes="x".repeat(InventoryProjectionLimits.MAX_TEXT_BYTES)
        val rows=List(50){lot("lot-$it").copy(notes=notes)}
        val result=failure(inventory().copy(lots=SnapshotList.copyOf(rows)),patch(CollectionEdit.Add(lot("new").copy(notes=notes))))
        assertEquals(FailureCode.INVALID_INPUT,result.code);assertEquals("Inventory work limit exceeded.",result.message)
    }
    @Test fun cancellationPropagatesInsideValidationIndexExactEqualitySortAndOutput() {
        val long=lot("original").copy(notes="x".repeat(12_000))
        val small=inventory(long)
        for(stage in listOf("lot","fingerprint","equivalent")) cancelAt(stage,small,patch(CollectionEdit.Add(long.copy(id=EntityId("new")))),2)
        val edits=List(1024){CollectionEdit.Add(lot("id-${it.toString().padStart(4,'0')}").copy(notes=it.toString()))}
        cancelAt("sorted",inventory(),CollectionPatch(SnapshotList.copyOf(edits)),2)
        cancelAt("freeze",small,patch(CollectionEdit.Adjust(long.id,0,"no-op")),1)
    }
    private fun cancelAt(stage:String,i:InventorySnapshot,p:CollectionPatch,n:Int) {
        var seen=0
        val cancel=object:CancelToken {
            override val isCancelled=false
            override fun throwIfCancelled() {
                if(Thread.currentThread().stackTrace.any{it.methodName==stage} && ++seen>=n) throw CancellationException("test cancellation")
            }
        }
        try { propose(i,p,i.revision,cancel);fail("Expected cancellation inside $stage; observed $seen checks") } catch(_:CancellationException) { assertTrue(seen>=n) }
    }
    @Test fun accountingCountersAcceptExactLimitsAndRejectTheNextUnit() {
        fun rejected(action:()->Unit) {
            try { action(); fail("Expected typed resource rejection") }
            catch(e:InventoryProjectionWork.Rejected) { assertEquals(FailureCode.INVALID_INPUT,e.code) }
        }
        val items=InventoryProjectionWork(token)
        items.items(InventoryProjectionLimits.MAX_COLLECTION_ELEMENTS)
        rejected { items.items(1) }
        val candidates=InventoryProjectionWork(token)
        repeat(InventoryProjectionLimits.MAX_CANDIDATE_COMPARISONS.toInt()) { candidates.candidate() }
        rejected { candidates.candidate() }
        val units=InventoryProjectionWork(token)
        units.spend(InventoryProjectionLimits.MAX_WORK_UNITS.toInt())
        rejected { units.spend() }
    }
    @Test fun workAccountingChecksCancellationAtTheDeclaredInterval() {
        var checks=0
        val cancel=object:CancelToken { override val isCancelled=false; override fun throwIfCancelled() { checks++ } }
        val work=InventoryProjectionWork(cancel)
        work.phase(); assertEquals(1,checks)
        work.spend(InventoryProjectionLimits.CANCEL_CHECK_INTERVAL); assertEquals(1,checks)
        work.spend(); assertEquals(2,checks)
        work.spend(InventoryProjectionLimits.CANCEL_CHECK_INTERVAL); assertEquals(3,checks)
    }
    @Test fun repeatedCallsAndEveryFailureLeaveInputsUnchanged() {
        val evidence=SnapshotList.of(Evidence("exact",null,UtcTimestamp(2)))
        val a=lot("a").copy(acquiredAt=Knowledge.Unknown("reason",evidence));val incoming=a.copy(id=EntityId("new"))
        val i=inventory(a);val p=patch(CollectionEdit.Add(incoming));val beforeI=i.copy();val beforeP=p.copy()
        val expected=project(i,p);assertEquals(expected,project(i,p));assertEquals(beforeI,i);assertEquals(beforeP,p)
        project(i,patch(CollectionEdit.Remove(a.id,"remove"),CollectionEdit.Add(incoming)))
        assertEquals(beforeI,i);assertEquals(beforeP,p);assertEquals("exact",evidence.single().description)
    }
}
