package com.architectpsg.commandlibrary.feature.collection

import com.architectpsg.commandlibrary.core.model.*
import java.util.Random
import org.junit.Assert.*
import org.junit.Test

/** Small deliberately quadratic oracle, independent of the production index and accounting helpers. */
class InventoryProjectionPropertyTest {
    private val token=object:CancelToken {override val isCancelled=false;override fun throwIfCancelled()=Unit}
    private val utf8=Comparator<String> {a,b->
        val x=a.toByteArray(Charsets.UTF_8);val y=b.toByteArray(Charsets.UTF_8)
        var result=0;var i=0
        while(result==0 && i<minOf(x.size,y.size)) {result=(x[i].toInt() and 255).compareTo(y[i].toInt() and 255);i++}
        if(result==0)x.size.compareTo(y.size) else result
    }
    private fun lot(id:String,group:Int,quantity:Int)=OwnedLot(EntityId(id),CardIdentity(
        Knowledge.Known(OracleId(ProviderId("source-${group%2}"),"oracle")),
        Knowledge.Known(CardId(ProviderId("source-${group%2}"),"printing-$group")),
        ProviderIdentity(ProviderId("original"),"card","raw-$group"),Knowledge.Known("Name $group"),LanguageTag("language-$group")),
        Knowledge.Known(if(group%2==0)Finish.NONFOIL else Finish.FOIL),CardCondition.entries[group%CardCondition.entries.size],
        "location-$group",quantity,"notes-$group",Revision(0),Knowledge.Known(UtcTimestamp(group.toLong(),group)))
    private fun projection(i:InventorySnapshot,p:CollectionPatch)=(InventoryPatchReducer.propose(i,p,i.revision,token) as Outcome.Success).value
    private fun compatible(a:OwnedLot,b:OwnedLot):Boolean {
        if(a.identity.printingId !is Knowledge.Known || b.identity.printingId !is Knowledge.Known || a.finish !is Knowledge.Known || b.finish !is Knowledge.Known)return false
        return a.copy(id=EntityId("comparison"),quantity=0,revision=Revision(0))==b.copy(id=EntityId("comparison"),quantity=0,revision=Revision(0))
    }
    private fun oracle(i:InventorySnapshot,p:CollectionPatch):InventoryProjection {
        val revision=if(p.edits.isEmpty())i.revision else Revision(i.revision.value+1)
        val original=i.lots.associateBy{it.id}
        val final=original.toMutableMap()
        val touched=mutableSetOf<EntityId>()
        val newSurvivors=mutableListOf<OwnedLot>()
        val merges=mutableListOf<InventoryMerge>()
        fun id(e:CollectionEdit)=when(e){is CollectionEdit.Add->e.lot.id;is CollectionEdit.Replace->e.lot.id;is CollectionEdit.Adjust->e.lotId;is CollectionEdit.Remove->e.lotId}
        val edits=p.edits.sortedWith(Comparator{a,b->utf8.compare(id(a).value,id(b).value)})
        for(e in edits) when(e) {
            is CollectionEdit.Replace->{final[e.lot.id]=e.lot.copy(revision=revision);touched.add(e.lot.id)}
            is CollectionEdit.Adjust->{val old=original.getValue(e.lotId);final[e.lotId]=old.copy(quantity=old.quantity+e.delta,revision=revision);touched.add(e.lotId)}
            is CollectionEdit.Remove->{final.remove(e.lotId);touched.add(e.lotId)}
            is CollectionEdit.Add->{
                val incoming=e.lot
                val candidate=i.lots.filter{compatible(it,incoming)}.minWithOrNull(Comparator{a,b->utf8.compare(a.id.value,b.id.value)})
                    ?:newSurvivors.firstOrNull{compatible(it,incoming)}
                if(candidate==null){newSurvivors.add(incoming);final[incoming.id]=incoming.copy(revision=revision);touched.add(incoming.id)}
                else {val old=final.getValue(candidate.id);final[candidate.id]=old.copy(quantity=old.quantity+incoming.quantity,revision=revision);touched.add(candidate.id);merges.add(InventoryMerge(incoming.id,candidate.id,incoming.quantity))}
            }
        }
        val changes=touched.sortedWith(Comparator{a,b->utf8.compare(a.value,b.value)}).map{InventoryLotChange(it,original[it],final[it])}
        return InventoryProjection(i.revision,revision,SnapshotList.copyOf(changes),SnapshotList.copyOf(merges.sortedWith(Comparator{a,b->utf8.compare(a.requestedLotId.value,b.requestedLotId.value)})),SnapshotList.empty())
    }
    @Test fun generatedMixedEditsMatchIndependentOracleConserveQuantitiesAndPreserveInputsUnderPermutation() {
        val random=Random(0x51ace)
        repeat(120){seed->
            val originals=MutableList(6){n->lot("old-$n",n%3,random.nextInt(20)).copy(revision=Revision((n%5).toLong()))}
            val replacement=lot("replace",100,5).copy(revision=Revision(2));val adjusted=lot("adjust",101,10);val removed=lot("remove",102,3)
            originals.addAll(listOf(replacement,adjusted,removed))
            val additions=List(9){n->
                val group=random.nextInt(6)
                val source=lot("new-$n",group,random.nextInt(20))
                if((seed+n)%4==0)source.copy(identity=source.identity.copy(printingId=Knowledge.Unknown("unresolved",SnapshotList.of(Evidence("reason-$n",null,null)))) )else source
            }
            val delta=random.nextInt(11)-10
            val edits=additions.map{CollectionEdit.Add(it)}+listOf(CollectionEdit.Replace(replacement.copy(notes="replacement-$seed",quantity=7)),CollectionEdit.Adjust(adjusted.id,delta,"adjustment"),CollectionEdit.Remove(removed.id,"removal"))
            val inventory=InventorySnapshot(Revision(9),UtcTimestamp(seed.toLong()),SnapshotList.copyOf(originals),true)
            val patch=CollectionPatch(SnapshotList.copyOf(edits))
            val inventoryBefore=inventory.copy();val patchBefore=patch.copy()
            val expected=oracle(inventory,patch);val actual=projection(inventory,patch)
            assertEquals("seed $seed",expected,actual)
            assertTrue(actual.issues.isEmpty())
            val net=actual.changes.sumOf{(it.after?.quantity?:0).toLong()-(it.before?.quantity?:0).toLong()}
            assertEquals(additions.sumOf{it.quantity.toLong()}+delta+2-3,net)
            for(change in actual.changes) {
                assertEquals(originals.firstOrNull{it.id==change.lotId},change.before)
                change.after?.let{assertEquals(Revision(10),it.revision)}
            }
            repeat(3){
                val shuffledInventory=inventory.copy(lots=SnapshotList.copyOf(originals.shuffled(kotlin.random.Random(seed*7+it))))
                val shuffledPatch=CollectionPatch(SnapshotList.copyOf(edits.shuffled(kotlin.random.Random(seed*11+it))))
                assertEquals("permutation $seed/$it",expected,projection(shuffledInventory,shuffledPatch))
            }
            assertEquals(inventoryBefore,inventory);assertEquals(patchBefore,patch)
        }
    }
    @Test fun generatedMetadataConflictsAndSourceQualifiedPrintingNeverLoseInformation() {
        repeat(80){seed->
            val unknown=Knowledge.Unknown("unknown-$seed",SnapshotList.of(Evidence("evidence",ResourceUri("urn:source:$seed"),UtcTimestamp(seed.toLong()))))
            val base=lot("existing",seed%5,seed).let{it.copy(identity=it.identity.copy(oracleId=unknown,originalName=unknown),acquiredAt=unknown)}
            val same=base.copy(id=EntityId("a-same"),quantity=2,revision=Revision(0))
            val different=base.copy(id=EntityId("b-different"),notes="distinct-$seed",quantity=3,revision=Revision(0))
            val foreign=base.copy(id=EntityId("c-foreign"),identity=base.identity.copy(printingId=Knowledge.Known(CardId(ProviderId("foreign"),"printing-${seed%5}"))),quantity=4,revision=Revision(0))
            val i=InventorySnapshot(Revision(3),UtcTimestamp(0),SnapshotList.of(base),true)
            val p=CollectionPatch(SnapshotList.of(CollectionEdit.Add(foreign),CollectionEdit.Add(different),CollectionEdit.Add(same)))
            val actual=projection(i,p)
            assertEquals(oracle(i,p),actual);assertEquals(1,actual.merges.size)
            assertEquals(base.copy(quantity=seed+2,revision=Revision(4)),actual.changes.first{it.lotId==base.id}.after)
            assertEquals(different.copy(revision=Revision(4)),actual.changes.first{it.lotId==different.id}.after)
            assertEquals(foreign.copy(revision=Revision(4)),actual.changes.first{it.lotId==foreign.id}.after)
        }
    }
    @Test fun generatedOverflowAndOverlapReturnAllAttributionsIndependentlyOfOrder() {
        repeat(40){seed->
            val existing=lot("a-existing",0,Int.MAX_VALUE-seed)
            val duplicate=existing.copy(id=EntityId("z-existing"))
            val incoming=List(4){lot("incoming-$it",0,seed+1)}
            val i=InventorySnapshot(Revision(2),UtcTimestamp(0),SnapshotList.of(duplicate,existing),true)
            val edits=incoming.map{CollectionEdit.Add(it)}+CollectionEdit.Remove(existing.id,"remove")
            val result=projection(i,CollectionPatch(SnapshotList.copyOf(edits)))
            assertTrue(result.changes.isEmpty());assertTrue(result.merges.isEmpty())
            assertEquals(listOf(existing.id)+incoming.map{it.id},result.issues.filter{it.code=="MERGE_TARGET_SELECTED"}.map{it.entryId})
            assertEquals(incoming.map{it.id},result.issues.filter{it.code=="QUANTITY_OVERFLOW"}.map{it.entryId})
            assertEquals(result,projection(i.copy(lots=SnapshotList.of(existing,duplicate)),CollectionPatch(SnapshotList.copyOf(edits.reversed()))))
        }
    }
}
