package com.architectpsg.commandlibrary.feature.collection.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.integer
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.boundedText
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.nullableProjection
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.nullable
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.num
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.text
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.long
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.string
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.hash
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.blob
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.nullableLong
import com.architectpsg.commandlibrary.feature.collection.persistence.InventoryRows.Companion.column
import java.io.*
import java.security.DigestOutputStream
import java.security.MessageDigest

/** CLID1 descriptor selects public deltas/merges/introduced IDs; CLIH1 binds complete physical images. */
internal class InventoryJournal(private val rows: InventoryRows) {
    data class Change(val id: String,val before: InventoryRows.Image?,val after: InventoryRows.Image)
    data class Descriptor(val ids: List<String>,val merges: List<InventoryMerge>,val introduced: List<String>)
    data class Operation(val sequence: Long,val kind: String,val expected: Long,val revision: Long,val time: UtcTimestamp,val hash: String,val base: Long?,val target: Long?,val changes: List<Change>,val descriptor: Descriptor) {
        fun commit(): InventoryCommit = InventoryCommit(InventoryProjection(Revision(expected),Revision(revision),SnapshotList.copyOf(changes.filter { it.id in descriptor.ids }.map { InventoryLotChange(EntityId(it.id),it.before?.takeIf { b -> b.active }?.lot,it.after.takeIf { a -> a.active }?.lot) }),SnapshotList.copyOf(descriptor.merges),SnapshotList.empty()),time)
    }
    data class Group(val sequence: Long,val previous: Long?,val before: String,val after: String,val operation: Operation)
    private val cancel get()=rows.cancel
    suspend fun replay(id: String,kind: String,sha: String,expected: Long): Operation? {
        val found=rows.query("SELECT ${integer("sequence")} FROM inventory_operation WHERE operation_id=:id","id" to text(id)).singleOrNull()?:return null
        val seq=found.long("sequence")
        val header=header(seq)
        if(header.string("kind")!=kind || header.hash("request_sha256")!=sha) inventoryFail(FailureCode.INVALID_INPUT)
        inventoryRequire(header.long("expected_revision")==expected)
        val op=load(seq,header)
        if(op.kind=="APPLY" && op.base!=null) inventoryRequire(group(op.base).operation.revision<=op.expected)
        if(op.kind=="UNDO") pointer(op)
        else if(op.revision>op.expected) {
            val group=group(op.sequence)
            inventoryRequire(group.before==(group.previous?.let { group(it).after }?:rows.content.digest(emptyList())))
        }
        return op
    }
    private suspend fun header(seq: Long): RowData {
        val scalars=listOf("sequence","request_version","expected_revision","result_revision","modified_seconds","modified_nanos","change_count","merge_count","reservation_count","descriptor_length","descriptor_version")
        val hashes=listOf("request_sha256","content_sha256","changes_sha256","descriptor_sha256")
        val row=rows.query("SELECT "+scalars.joinToString(",") { integer(it) }+","+hashes.joinToString(",") { boundedText(it) }+","+boundedText("kind","kind",8)+","+boundedText("operation_id","operation_id",1024)+","+nullableProjection("base_group_sequence","base")+","+nullableProjection("undo_target_sequence","target")+",CASE WHEN typeof(descriptor)='blob' THEN length(descriptor) ELSE NULL END AS actual FROM inventory_operation WHERE sequence=:sequence","sequence" to num(seq)).singleOrNull()?:inventoryFail(FailureCode.CORRUPT_DATA)
        inventoryRequire(seq>0 && row.long("sequence")==seq && row.long("request_version")==1L && row.long("descriptor_version")==1L)
        hashes.forEach { row.hash(it) };row.string("operation_id")
        inventoryRequire(row.string("kind") in setOf("APPLY","UNDO") && row.long("expected_revision")>=0 && row.long("result_revision")>=0 && row.long("modified_nanos") in 0..999999999)
        inventoryRequire(row.long("change_count") in 0..2048 && row.long("merge_count") in 0..1024 && row.long("reservation_count") in 0..1024)
        inventoryRequire(row.long("descriptor_length") in 18..LocalInventoryLimits.MAX_DESCRIPTOR_BYTES.toLong() && row.long("actual")==row.long("descriptor_length"))
        for(p in listOf("base","target")) inventoryRequire(row.nullableLong(p)?.let { it>0 && it<seq }?:true)
        val expected=row.long("expected_revision");val revision=row.long("result_revision");val empty=row.long("change_count")==0L
        inventoryRequire(row.long("modified_seconds")>=0)
        inventoryRequire(if(empty) row.string("kind")=="APPLY" && revision==expected && row.long("merge_count")==0L && row.long("reservation_count")==0L else expected<Long.MAX_VALUE && revision==expected+1)
        inventoryRequire(if(row.string("kind")=="APPLY") row.nullableLong("target")==null else row.nullableLong("target")!=null && row.nullableLong("target")==row.nullableLong("base"))
        return row
    }
    suspend fun load(seq: Long): Operation=load(seq,header(seq))
    private suspend fun load(seq: Long,h: RowData): Operation {
        val metadata=mutableListOf<RowData>();var after: String?=null;var total=h.long("descriptor_length")
        do {
            val batch=rows.query("SELECT ${boundedText("lot_id","id",1024)},${sideProjection("before")},${sideProjection("after")} FROM inventory_change WHERE operation_sequence=:sequence"+(if(after==null)"" else " AND lot_id COLLATE BINARY > :after")+" ORDER BY lot_id COLLATE BINARY LIMIT 100",*(arrayOf("sequence" to num(seq))+if(after==null) emptyArray() else arrayOf("after" to text(after))))
            for(row in batch) {
                cancel.throwIfCancelled();row.string("id");inventoryRequire(row.long("after_present")==1L)
                for(side in listOf("before","after")) total+=sideMeta(row,side)?.length?:0
                inventoryRequire(total<=LocalInventoryLimits.MAX_OPERATION_ENCODED_BYTES && metadata.size<2048)
                metadata.add(row)
            }
            after=batch.lastOrNull()?.string("id")
        } while(batch.size==100)
        inventoryRequire(metadata.size.toLong()==h.long("change_count"))
        val descriptorBytes=rows.query("SELECT descriptor AS payload FROM inventory_operation WHERE sequence=:sequence","sequence" to num(seq)).single().blob("payload")
        inventoryRequire(descriptorBytes.size.toLong()==h.long("descriptor_length") && InventoryContent.sha(descriptorBytes.copyBytes(),cancel)==h.hash("descriptor_sha256"))
        val descriptor=decode(descriptorBytes)
        inventoryRequire(descriptor.merges.size.toLong()==h.long("merge_count") && descriptor.introduced.size.toLong()==h.long("reservation_count"))
        val changes=metadata.map { row ->
            cancel.throwIfCancelled();val id=row.string("id")
            suspend fun image(side: String): InventoryRows.Image?=sideMeta(row,side)?.let { rows.load(it,"SELECT ${side}_payload AS payload FROM inventory_change WHERE operation_sequence=:sequence AND lot_id=:id","sequence" to num(seq),"id" to text(id)) }
            Change(id,image("before"),image("after")?:inventoryFail(FailureCode.CORRUPT_DATA))
        }
        inventoryRequire(changesHash(changes)==h.hash("changes_sha256"))
        val expected=h.long("expected_revision");val revision=h.long("result_revision");val kind=h.string("kind")
        val empty=changes.isEmpty()
        inventoryRequire(if(empty) kind=="APPLY" && revision==expected else expected<Long.MAX_VALUE && revision==expected+1)
        val base=h.nullableLong("base");val target=h.nullableLong("target")
        inventoryRequire(if(kind=="APPLY") target==null else target!=null && base==target && !empty)
        val publicIds=changes.filter { it.before?.active==true || it.after.active }.map { it.id }
        inventoryRequire(descriptor.ids==publicIds && descriptor.introduced==changes.filter { it.before==null }.map { it.id })
        inventoryRequire(kind=="APPLY" || descriptor.merges.isEmpty() && descriptor.introduced.isEmpty())
        inventoryRequire(kind!="APPLY" || changes.all { it.before==null || it.before.active })
        for(c in changes) inventoryRequire(c.after.lot.revision.value==revision && (c.before?.lot?.revision?.value?:0)<=expected)
        for(m in descriptor.merges) {
            val requested=changes.singleOrNull { it.id==m.requestedLotId.value }?:inventoryFail(FailureCode.CORRUPT_DATA)
            val survivor=changes.singleOrNull { it.id==m.survivorLotId.value }?:inventoryFail(FailureCode.CORRUPT_DATA)
            inventoryRequire(requested.before==null && !requested.after.active && requested.after.lot.quantity==m.quantityAdded && survivor.after.active && m.quantityAdded>=0)
        }
        return Operation(seq,kind,expected,revision,UtcTimestamp(h.long("modified_seconds"),h.long("modified_nanos").toInt()),h.hash("content_sha256"),base,target,changes,descriptor)
    }
    suspend fun group(seq: Long): Group {
        val row=rows.query("SELECT ${integer("sequence")},${nullableProjection("previous_group_sequence","previous")},${boundedText("before_sha256","before")},${boundedText("after_sha256","after")} FROM inventory_group WHERE sequence=:sequence","sequence" to num(seq)).singleOrNull()?:inventoryFail(FailureCode.CORRUPT_DATA)
        val op=load(seq);val previous=row.nullableLong("previous")
        inventoryRequire(row.long("sequence")==seq && op.kind=="APPLY" && op.revision>op.expected && previous==op.base && (previous?.let { it>0 && it<seq }?:true) && row.hash("after")==op.hash)
        return Group(seq,previous,row.hash("before"),row.hash("after"),op)
    }
    suspend fun pointer(op: Operation): Long? = when {
        op.kind=="UNDO" -> {
            val target=group(op.target?:inventoryFail(FailureCode.CORRUPT_DATA))
            inventoryRequire(op.hash==target.before && target.operation.revision<=op.expected)
            val inverse=target.operation.changes.filterNot { it.before==null && !it.after.active }
            inventoryRequire(op.changes.map { it.id }==inverse.map { it.id })
            for((restored,original) in op.changes.zip(inverse)) {
                inventoryRequire(restored.before!=null && restored.before.active==original.after.active && InventoryContent.same(restored.before.lot,original.after.lot))
                inventoryRequire(restored.after.active==(original.before?.active==true) && InventoryContent.same(restored.after.lot,original.before?.lot?:original.after.lot))
            }
            target.previous
        }
        op.revision==op.expected -> op.base
        else -> op.sequence
    }
    fun budget(changes: List<Change>,merges: List<InventoryMerge>) {
        val descriptor=Descriptor(changes.filter { it.before?.active==true || it.after.active }.map { it.id },merges,changes.filter { it.before==null }.map { it.id })
        var total=encode(descriptor).size.toLong()
        for(c in changes) { cancel.throwIfCancelled();total+=c.after.payload.size.toLong()+(c.before?.payload?.size?:0);inventoryRequire(total<=LocalInventoryLimits.MAX_OPERATION_ENCODED_BYTES,FailureCode.INVALID_INPUT) }
    }
    suspend fun insert(id: String,kind: String,sha: String,projection: InventoryProjection,time: UtcTimestamp,hash: String,base: Long?,target: Long?,changes: List<Change>,merges: List<InventoryMerge>): Long {
        val descriptor=Descriptor(changes.filter { it.before?.active==true || it.after.active }.map { it.id },merges,changes.filter { it.before==null }.map { it.id })
        val bytes=encode(descriptor)
        var total=bytes.size.toLong()
        for(c in changes) { cancel.throwIfCancelled();total+=c.after.payload.size.toLong()+(c.before?.payload?.size?:0);inventoryRequire(total<=LocalInventoryLimits.MAX_OPERATION_ENCODED_BYTES,FailureCode.INVALID_INPUT) }
        rows.write("INSERT INTO inventory_operation (operation_id,kind,request_version,request_sha256,expected_revision,result_revision,modified_seconds,modified_nanos,content_sha256,change_count,merge_count,reservation_count,changes_sha256,descriptor_length,descriptor_version,descriptor_sha256,descriptor,base_group_sequence,undo_target_sequence) VALUES (:id,:kind,1,:request,:expected,:revision,:seconds,:nanos,:content,:count,:merges,:reserved,:changes,:length,1,:sha,:descriptor,:base,:target)",
            "id" to text(id),"kind" to text(kind),"request" to text(sha),"expected" to num(projection.sourceRevision.value),"revision" to num(projection.resultRevision.value),"seconds" to num(time.epochSeconds),"nanos" to num(time.nanoAdjustment.toLong()),"content" to text(hash),"count" to num(changes.size.toLong()),"merges" to num(merges.size.toLong()),"reserved" to num(descriptor.introduced.size.toLong()),"changes" to text(changesHash(changes)),"length" to num(bytes.size.toLong()),"sha" to text(InventoryContent.sha(bytes.copyBytes(),cancel)),"descriptor" to SqlValue.Blob(bytes),"base" to nullable(base),"target" to nullable(target))
        val seq=rows.query("SELECT last_insert_rowid() AS sequence").single().long("sequence");inventoryRequire(seq>0)
        for(c in changes) {
            fun params(side: String,image: InventoryRows.Image?): List<Pair<String,SqlValue>> = if(image==null) listOf("${side}_present" to num(0))+listOf("active","revision","quantity","payload_length","payload_version","payload_sha256","payload").map { "${side}_$it" to SqlValue.Null }
            else listOf("${side}_present" to num(1),"${side}_active" to num(if(image.active)1 else 0),"${side}_revision" to num(image.lot.revision.value),"${side}_quantity" to num(image.lot.quantity.toLong()),"${side}_payload_length" to num(image.payload.size.toLong()),"${side}_payload_version" to num(1),"${side}_payload_sha256" to text(InventoryContent.sha(image.payload.copyBytes(),cancel)),"${side}_payload" to SqlValue.Blob(image.payload))
            val p=arrayOf("operation_sequence" to num(seq),"lot_id" to text(c.id))+(params("before",c.before)+params("after",c.after)).toTypedArray()
            rows.write("INSERT INTO inventory_change (${p.joinToString(",") { it.first }}) VALUES (${p.joinToString(",") { ":${it.first}" }})",*p)
        }
        return seq
    }
    fun encode(d: Descriptor): SnapshotBytes {
        val buffer=ByteArrayOutputStream()
        val bounded=object:OutputStream() { override fun write(b: Int) { inventoryRequire(buffer.size()<LocalInventoryLimits.MAX_DESCRIPTOR_BYTES,FailureCode.INVALID_INPUT);buffer.write(b) };override fun write(b: ByteArray,off: Int,len: Int) { inventoryRequire(len<=LocalInventoryLimits.MAX_DESCRIPTOR_BYTES-buffer.size(),FailureCode.INVALID_INPUT);buffer.write(b,off,len) } }
        DataOutputStream(bounded).use { out ->
            out.write(byteArrayOf(67,76,73,68,0,1))
            fun id(s: String) { cancel.throwIfCancelled();val bytes=InventoryContent.id(s);out.writeInt(bytes.size);out.write(bytes) }
            out.writeInt(d.ids.size);d.ids.forEach(::id)
            out.writeInt(d.merges.size);for(m in d.merges) { id(m.requestedLotId.value);id(m.survivorLotId.value);out.writeInt(m.quantityAdded) }
            out.writeInt(d.introduced.size);d.introduced.forEach(::id)
        }
        cancel.throwIfCancelled();return SnapshotBytes(buffer.toByteArray())
    }
    private fun decode(bytes: SnapshotBytes): Descriptor = try {
        val input=DataInputStream(ByteArrayInputStream(bytes.copyBytes()))
        inventoryRequire(input.readInt()==0x434c4944 && input.readUnsignedShort()==1)
        fun count(): Int=input.readInt().also { inventoryRequire(it in 0..1024) }
        fun id(): String { cancel.throwIfCancelled();val length=input.readInt();inventoryRequire(length in 1..1024 && length<=input.available());val raw=ByteArray(length);input.readFully(raw);val s=Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(raw)).toString();InventoryContent.id(s,FailureCode.CORRUPT_DATA);return s }
        val ids=List(count()) { id() };val merges=List(count()) { InventoryMerge(EntityId(id()),EntityId(id()),input.readInt()) };val introduced=List(count()) { id() }
        inventoryRequire(input.available()==0)
        for(list in listOf(ids,merges.map { it.requestedLotId.value },introduced)) inventoryRequire(list.zipWithNext().all { InventoryContent.compare(it.first,it.second,cancel)<0 })
        Descriptor(ids,merges,introduced)
    } catch(_:IOException) { inventoryFail(FailureCode.CORRUPT_DATA) } catch(_:IllegalArgumentException) { inventoryFail(FailureCode.CORRUPT_DATA) }
    fun changesHash(changes: List<Change>): String {
        val digest=MessageDigest.getInstance("SHA-256")
        DataOutputStream(DigestOutputStream(object:OutputStream() { override fun write(b: Int)=Unit;override fun write(b:ByteArray,off:Int,len:Int)=Unit },digest)).use { out ->
            out.write(byteArrayOf(67,76,73,72,0,1));out.writeInt(changes.size)
            for(c in changes) {
                cancel.throwIfCancelled();val id=InventoryContent.id(c.id);out.writeInt(id.size);out.write(id)
                for(image in listOf(c.before,c.after)) {
                    out.writeByte(if(image==null)0 else 1)
                    if(image!=null) { out.writeByte(if(image.active)1 else 0);out.writeLong(image.lot.revision.value);out.writeInt(image.lot.quantity);out.writeInt(image.payload.size);out.writeShort(1);out.write(InventoryContent.sha(image.payload.copyBytes(),cancel).toByteArray(Charsets.US_ASCII)) }
                }
            }
        }
        return InventoryContent.hex(digest.digest())
    }
    private fun sideProjection(side: String): String = integer("${side}_present")+","+listOf("active","revision","quantity","payload_length","payload_version").joinToString(",") { nullableProjection("${side}_$it") }+","+boundedText("${side}_payload_sha256")+",CASE WHEN typeof(${side}_payload)='blob' THEN length(${side}_payload) ELSE NULL END AS ${side}_actual,CASE WHEN ${side}_payload_sha256 IS NULL AND ${side}_payload IS NULL THEN 1 ELSE 0 END AS ${side}_null"
    private fun sideMeta(row: RowData,side: String): InventoryRows.Meta? {
        val present=row.long("${side}_present");inventoryRequire(present in 0..1)
        val fields=listOf("active","revision","quantity","payload_length","payload_version").associateWith { row.nullableLong("${side}_$it") }
        if(present==0L) { inventoryRequire(fields.values.all { it==null } && row.long("${side}_null")==1L);return null }
        val active=fields["active"];val revision=fields["revision"];val qty=fields["quantity"];val length=fields["payload_length"]
        inventoryRequire(active!=null && active in 0L..1L && revision!=null && revision>=0 && qty!=null && qty in 0..Int.MAX_VALUE.toLong() && length!=null && length in 6..DeckPayloadLimits.MAX_ENCODED_BYTES.toLong() && fields["payload_version"]==1L && row.long("${side}_actual")==length)
        return InventoryRows.Meta(row.string("id"),active==1L,revision!!,qty!!.toInt(),length!!.toInt(),row.hash("${side}_payload_sha256"))
    }
}
