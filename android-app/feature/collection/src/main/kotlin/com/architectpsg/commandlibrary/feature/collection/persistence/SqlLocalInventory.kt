package com.architectpsg.commandlibrary.feature.collection.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.collection.InventoryPatchReducer
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.long
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.hash
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.nullableLong
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.integer
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.nullableProjection
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.boundedText
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.num
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.text
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.nullable
import kotlin.coroutines.cancellation.CancellationException

/** Complete local inventory capability; journal and permanent identities share one immediate transaction. */
public class SqlLocalInventory(
    private val db: Database,
    private val codec: OwnedLotPayloadCodecPort,
    private val patchCodec: LocalInventoryPatchCodecPort,
    private val clock: Clock,
    private val maxBoundParameters: Int,
) : LocalInventory {
    init { require(maxBoundParameters > 0) }
    private data class State(val revision: Long,val time: UtcTimestamp,val latest: Long?,val pointer: Long?,val hash: String,val active: LinkedHashMap<String,InventoryRows.Image>)
    private class Session(val rows: InventoryRows) { val journal=InventoryJournal(rows);val content get()=rows.content;val cancel get()=rows.cancel }
    private suspend fun <T> transaction(cancel: CancelToken,action: suspend Session.()->T): Outcome<T> {
        cancel.throwIfCancelled()
        val result=db.transaction({ tx ->
            try { val value=Session(InventoryRows(tx,InventoryContent(codec,cancel),cancel)).action();cancel.throwIfCancelled();Outcome.Success(value) }
            catch(p: InventoryProblem) { cancel.throwIfCancelled();p.result }
        },cancel)
        cancel.throwIfCancelled()
        return when(result) { is Outcome.Success -> result.value;is Outcome.Failure -> result;is Outcome.Unavailable -> result;is Outcome.Unknown -> result }
    }
    public override suspend fun snapshot(cancel: CancelToken): Outcome<InventorySnapshot> = transaction(cancel) {
        val state=read();InventorySnapshot(Revision(state.revision),state.time,SnapshotList.copyOf(state.active.values.map { cancel.throwIfCancelled();it.lot }.sortedWith { a,b -> InventoryContent.compare(a.id.value,b.id.value,cancel) }),true)
    }
    public override suspend fun apply(patch: CollectionPatch,expected: Revision,operationId: EntityId,cancel: CancelToken): Outcome<InventoryApplyResult> = transaction(cancel) {
        InventoryContent.id(operationId.value);cancel.throwIfCancelled()
        val bytes=inventoryValue(patchCodec.encodePatch(patch));cancel.throwIfCancelled()
        val request=try { InventoryRequests.apply(expected,bytes,cancel) } catch(_:IllegalArgumentException) { inventoryFail(FailureCode.INVALID_INPUT) }
        journal.replay(operationId.value,"APPLY",request,expected.value)?.let { saved ->
            inventoryRequire(if(patch.edits.isEmpty()) saved.revision==expected.value else expected.value<Long.MAX_VALUE && saved.revision==expected.value+1)
            return@transaction InventoryApplyResult.Committed(saved.commit())
        }
        val state=read();inventoryRequire(state.revision==expected.value,FailureCode.STALE_REVISION);writable(state)
        val snapshot=InventorySnapshot(expected,state.time,SnapshotList.copyOf(state.active.values.map { cancel.throwIfCancelled();it.lot }.sortedWith { a,b -> InventoryContent.compare(a.id.value,b.id.value,cancel) }),true)
        val initial=inventoryValue(InventoryPatchReducer.propose(snapshot,patch,expected,cancel))
        val adds=patch.edits.filterIsInstance<CollectionEdit.Add>()
        val existing=rows.existing(adds.map { it.lot.id.value },maxBoundParameters)
        val missing=adds.map { it.lot.id.value }.filter { it !in existing }
        inventoryRequire(rows.introduced(missing,maxBoundParameters).isEmpty())
        val inactive=existing.filter { it !in state.active }.toSet()
        val proposal=if(inactive.isEmpty())initial else {
            val filtered=CollectionPatch(SnapshotList.copyOf(patch.edits.filter { it !is CollectionEdit.Add || it.lot.id.value !in inactive }))
            val others=inventoryValue(InventoryPatchReducer.propose(snapshot,filtered,expected,cancel))
            val issues=others.issues.toMutableList()
            issues.addAll(initial.issues.filter { it.entryId?.value in inactive && it.code in setOf(InventoryIssueCodes.DUPLICATE_SELECTION,InventoryIssueCodes.INVALID_ADD_REVISION) })
            for(id in inactive) issues.add(DomainIssue(InventoryIssueCodes.LOT_ALREADY_EXISTS,IssueSeverity.ERROR,"The requested lot identifier already exists.",EntityId(id),SnapshotList.empty()))
            val canonical=issues.distinctBy { it.entryId to it.code }.sortedWith { a,b -> InventoryContent.compare(a.entryId!!.value,b.entryId!!.value,cancel).takeIf { it!=0 }?:a.code.compareTo(b.code) }
            others.copy(changes=SnapshotList.empty(),merges=SnapshotList.empty(),issues=SnapshotList.copyOf(canonical))
        }
        if(proposal.issues.isNotEmpty()) return@transaction InventoryApplyResult.Rejected(proposal.issues,expected)
        val changes=mutableListOf<InventoryJournal.Change>()
        for(c in proposal.changes) {
            cancel.throwIfCancelled();val before=state.active[c.lotId.value]
            inventoryRequire(before?.lot==c.before)
            val after=c.after?.let { rows.image(it,true) }?:rows.image(before!!.lot.copy(revision=proposal.resultRevision),false)
            changes.add(InventoryJournal.Change(c.lotId.value,before,after))
        }
        for(add in adds) if(changes.none { it.id==add.lot.id.value }) changes.add(InventoryJournal.Change(add.lot.id.value,null,rows.image(add.lot.copy(revision=proposal.resultRevision),false)))
        changes.sortWith { a,b -> InventoryContent.compare(a.id,b.id,cancel) }
        for(c in changes) if(c.after.active) state.active[c.id]=c.after else state.active.remove(c.id)
        activeBudget(state.active);val hash=content.digest(state.active.values.map { cancel.throwIfCancelled();it.lot });journal.budget(changes,proposal.merges)
        val time=if(patch.edits.isEmpty())state.time else now(state.time)
        val seq=journal.insert(operationId.value,"APPLY",request,proposal,time,hash,state.pointer,null,changes,proposal.merges)
        if(patch.edits.isNotEmpty()) rows.write("INSERT INTO inventory_group (sequence,previous_group_sequence,before_sha256,after_sha256) VALUES (:sequence,:previous,:before,:after)","sequence" to num(seq),"previous" to nullable(state.pointer),"before" to text(state.hash),"after" to text(hash))
        for(c in changes) rows.persist(c.after,seq,c.before!=null)
        save(state,proposal.resultRevision.value,time,seq,if(patch.edits.isEmpty())state.pointer else seq,hash)
        InventoryApplyResult.Committed(InventoryCommit(proposal,time))
    }
    public override suspend fun undo(expected: Revision,operationId: EntityId,cancel: CancelToken): Outcome<InventoryCommit> = transaction(cancel) {
        InventoryContent.id(operationId.value)
        val request=InventoryRequests.undo(expected,cancel)
        journal.replay(operationId.value,"UNDO",request,expected.value)?.let { inventoryRequire(expected.value<Long.MAX_VALUE && it.revision==expected.value+1);return@transaction it.commit() }
        val state=read();inventoryRequire(state.revision==expected.value,FailureCode.STALE_REVISION);writable(state)
        val pointer=state.pointer?:throw InventoryProblem(Outcome.Unavailable(UnavailableReason.NOT_FOUND,"No inventory edit to undo."))
        inventoryRequire(expected.value<Long.MAX_VALUE,FailureCode.PERSISTENCE)
        val selected=journal.group(pointer);val revision=Revision(expected.value+1)
        val changes=mutableListOf<InventoryJournal.Change>()
        for(c in selected.operation.changes) {
            cancel.throwIfCancelled()
            if(c.before==null && !c.after.active) continue // absorbed Add reservation is immutable across undo
            val current=state.active[c.id]?:rows.current(c.id)?:inventoryFail(FailureCode.CORRUPT_DATA)
            val after=if(c.before?.active==true) rows.image(c.before.lot.copy(revision=revision),true)
                else rows.image((c.before?.lot?:current.lot).copy(revision=revision),false)
            changes.add(InventoryJournal.Change(c.id,current,after))
        }
        for(c in changes) if(c.after.active) state.active[c.id]=c.after else state.active.remove(c.id)
        activeBudget(state.active);val hash=content.digest(state.active.values.map { cancel.throwIfCancelled();it.lot });inventoryRequire(hash==selected.before)
        journal.budget(changes,emptyList())
        val projection=InventoryProjection(expected,revision,SnapshotList.copyOf(changes.filter { it.before?.active==true || it.after.active }.map { InventoryLotChange(EntityId(it.id),it.before?.takeIf { b -> b.active }?.lot,it.after.takeIf { a -> a.active }?.lot) }),SnapshotList.empty(),SnapshotList.empty())
        val time=now(state.time);val seq=journal.insert(operationId.value,"UNDO",request,projection,time,hash,pointer,pointer,changes,emptyList())
        for(c in changes) rows.persist(c.after,seq,true)
        save(state,revision.value,time,seq,selected.previous,hash)
        InventoryCommit(projection,time)
    }
    private suspend fun Session.read(): State {
        val headers=rows.query("SELECT ${integer("singleton")},${integer("revision")},${integer("modified_seconds")},${integer("modified_nanos")},${integer("latest_operation_sequence","latest")},${nullableProjection("current_group_sequence","pointer")},${boundedText("content_sha256","hash")} FROM inventory_state LIMIT 2",limit=2)
        val empty=content.digest(emptyList())
        if(headers.isEmpty()) {
            for(table in listOf("inventory_lot","inventory_operation","inventory_group","inventory_change")) inventoryRequire(rows.query("SELECT EXISTS(SELECT 1 FROM $table) AS found").single().long("found")==0L)
            return State(0,UtcTimestamp(0),null,null,empty,linkedMapOf())
        }
        inventoryRequire(headers.size==1);val h=headers.single();val revision=h.long("revision");val latest=h.long("latest");val nanos=h.long("modified_nanos")
        inventoryRequire(h.long("singleton")==1L && revision>=0 && latest>0 && nanos in 0..999999999)
        val time=UtcTimestamp(h.long("modified_seconds"),nanos.toInt());val hash=h.hash("hash");val pointer=h.nullableLong("pointer")
        inventoryRequire(rows.query("SELECT MAX(sequence) AS latest FROM inventory_operation").single().long("latest")==latest)
        val op=journal.load(latest)
        inventoryRequire(op.revision==revision && op.time==time && op.hash==hash && journal.pointer(op)==pointer)
        val active=rows.active(revision)
        inventoryRequire(content.digest(active.values.map { cancel.throwIfCancelled();it.lot })==hash)
        for(change in op.changes) {
            cancel.throwIfCancelled()
            val image=active[change.id]?:rows.current(change.id)
            inventoryRequire(image!=null && image.active==change.after.active && image.lot==change.after.lot && image.payload==change.after.payload)
        }
        if(pointer==null) inventoryRequire(hash==empty) else {
            val selected=journal.group(pointer)
            inventoryRequire(selected.operation.revision<=revision)
            validateGroup(active,selected,0)
        }
        return State(revision,time,latest,pointer,hash,active)
    }
    /** Rewind only touched map entries temporarily. No persisted full historical snapshots or chain walk. */
    private suspend fun Session.validateGroup(active: LinkedHashMap<String,InventoryRows.Image>,group: InventoryJournal.Group,depth: Int) {
        val predecessor=group.previous?.let { journal.group(it) }
        inventoryRequire(predecessor==null || predecessor.operation.revision<=group.operation.expected)
        inventoryRequire(group.before==(predecessor?.after?:content.digest(emptyList())))
        inventoryRequire(content.digest(active.values.map { cancel.throwIfCancelled();it.lot })==group.after)
        val restore=mutableListOf<Pair<String,InventoryRows.Image?>>()
        for(c in group.operation.changes) {
            cancel.throwIfCancelled();val current=active[c.id]?:rows.current(c.id)
            inventoryRequire(current!=null && current.active==c.after.active && InventoryContent.same(current.lot,c.after.lot))
            if(c.before==null && !c.after.active) inventoryRequire(current!!.payload==c.after.payload)
            restore.add(c.id to active[c.id])
            if(c.before?.active==true) active[c.id]=c.before else active.remove(c.id)
        }
        inventoryRequire(content.digest(active.values.map { cancel.throwIfCancelled();it.lot })==group.before)
        if(depth==0 && predecessor!=null) validateGroup(active,predecessor,1)
        for((id,image) in restore) if(image==null)active.remove(id) else active[id]=image
    }
    private fun Session.activeBudget(active: Map<String,InventoryRows.Image>) {
        inventoryRequire(active.size<=InventoryProjectionLimits.MAX_LOTS,FailureCode.INVALID_INPUT)
        var total=0L;for(image in active.values) { cancel.throwIfCancelled();total+=image.payload.size;inventoryRequire(total<=LocalInventoryLimits.MAX_ACTIVE_ENCODED_BYTES,FailureCode.INVALID_INPUT) }
    }
    private suspend fun Session.writable(state: State) {
        inventoryRequire(state.latest!=Long.MAX_VALUE,FailureCode.PERSISTENCE)
        val seq=rows.query("SELECT COALESCE(MAX(seq),0) AS sequence FROM sqlite_sequence WHERE name='inventory_operation'").single().long("sequence")
        inventoryRequire(seq<Long.MAX_VALUE,FailureCode.PERSISTENCE)
    }
    private fun now(previous: UtcTimestamp): UtcTimestamp {
        val time=try { clock.utcNow() } catch(e:CancellationException) { throw e } catch(_:Exception) { inventoryFail(FailureCode.INTERNAL) }
        inventoryRequire(time.epochSeconds>previous.epochSeconds || time.epochSeconds==previous.epochSeconds && time.nanoAdjustment>=previous.nanoAdjustment,FailureCode.INTERNAL)
        return time
    }
    private suspend fun Session.save(old: State,revision: Long,time: UtcTimestamp,seq: Long,pointer: Long?,hash: String) {
        val p=arrayOf("revision" to num(revision),"seconds" to num(time.epochSeconds),"nanos" to num(time.nanoAdjustment.toLong()),"latest" to num(seq),"pointer" to nullable(pointer),"hash" to text(hash))
        if(old.latest==null) rows.write("INSERT INTO inventory_state (singleton,revision,modified_seconds,modified_nanos,latest_operation_sequence,current_group_sequence,content_sha256) VALUES (1,:revision,:seconds,:nanos,:latest,:pointer,:hash)",*p)
        else rows.write("UPDATE inventory_state SET revision=:revision,modified_seconds=:seconds,modified_nanos=:nanos,latest_operation_sequence=:latest,current_group_sequence=:pointer,content_sha256=:hash WHERE singleton=1 AND revision=:expected AND latest_operation_sequence=:old AND current_group_sequence IS :oldpointer",*p,"expected" to num(old.revision),"old" to num(old.latest),"oldpointer" to nullable(old.pointer))
    }
}
