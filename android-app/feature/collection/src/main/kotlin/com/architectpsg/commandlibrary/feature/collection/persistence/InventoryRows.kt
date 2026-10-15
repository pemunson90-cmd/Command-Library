package com.architectpsg.commandlibrary.feature.collection.persistence

import com.architectpsg.commandlibrary.core.model.*

/** Metadata guards run for the whole selected set before any corresponding payload is fetched. */
internal class InventoryRows(val tx: DbTransaction,val content: InventoryContent,val cancel: CancelToken) {
    data class Image(val lot: OwnedLot,val active: Boolean,val payload: SnapshotBytes)
    data class Meta(val id: String,val active: Boolean,val revision: Long,val quantity: Int,val length: Int,val sha: String)
    suspend fun query(sql: String,vararg params: Pair<String,SqlValue>,limit: Int=100): SnapshotList<RowData> {
        cancel.throwIfCancelled();val rows=inventoryValue(tx.query(request(sql,*params,limit=limit),cancel));cancel.throwIfCancelled();return rows
    }
    suspend fun write(sql: String,vararg params: Pair<String,SqlValue>) {
        cancel.throwIfCancelled();inventoryRequire(inventoryValue(tx.execute(request(sql,*params),cancel)).affectedRows==1L,FailureCode.PERSISTENCE);cancel.throwIfCancelled()
    }
    suspend fun active(revision: Long): LinkedHashMap<String,Image> {
        val totals=query("SELECT COUNT(*) AS count,COALESCE(SUM(CASE WHEN typeof(payload_length)='integer' AND payload_length BETWEEN 6 AND 8388608 THEN payload_length ELSE 0 END),0) AS bytes FROM inventory_lot WHERE active=1").single()
        inventoryRequire(totals.long("count") in 0..InventoryProjectionLimits.MAX_LOTS.toLong() && totals.long("bytes")<=LocalInventoryLimits.MAX_ACTIVE_ENCODED_BYTES)
        val metas=ArrayList<Meta>();var after: String?=null;var bytes=0L
        do {
            val batch=query("SELECT ${lotProjection()} FROM inventory_lot WHERE active=1"+(if(after==null) "" else " AND lot_id COLLATE BINARY > :after")+" ORDER BY lot_id COLLATE BINARY LIMIT 100",*if(after==null) emptyArray() else arrayOf("after" to text(after)))
            for(row in batch) { cancel.throwIfCancelled();val m=meta(row);inventoryRequire(m.active && m.revision<=revision);metas.add(m);bytes+=m.length;inventoryRequire(bytes<=LocalInventoryLimits.MAX_ACTIVE_ENCODED_BYTES && metas.size<=InventoryProjectionLimits.MAX_LOTS) }
            after=batch.lastOrNull()?.string("id")
        } while(batch.size==100)
        inventoryRequire(metas.size.toLong()==totals.long("count") && bytes==totals.long("bytes"))
        val result=linkedMapOf<String,Image>()
        for(m in metas) { cancel.throwIfCancelled();result[m.id]=load(m,"SELECT payload FROM inventory_lot WHERE lot_id=:id","id" to text(m.id)) }
        return result
    }
    suspend fun existing(ids: List<String>,maximum: Int): Set<String> {
        val found=mutableSetOf<String>()
        for(batch in ids.distinct().chunked(minOf(100,maximum))) {
            cancel.throwIfCancelled()
            val params=batch.mapIndexed { i,s -> "i$i" to text(s) }.toTypedArray()
            for(row in query("SELECT ${boundedText("lot_id","id",1024)} FROM inventory_lot WHERE lot_id IN (${batch.indices.joinToString(",") { ":i$it" }})",*params)) found.add(row.string("id"))
        }
        return found
    }
    suspend fun introduced(ids: List<String>,maximum: Int): Set<String> {
        val found=mutableSetOf<String>()
        for(batch in ids.distinct().chunked(minOf(100,maximum))) {
            cancel.throwIfCancelled()
            val parameters=batch.mapIndexed { i,id -> "i$i" to text(id) }.toTypedArray()
            val matches=query("SELECT DISTINCT ${boundedText("lot_id","id",1024)} FROM inventory_change WHERE lot_id IN (${batch.indices.joinToString(",") { ":i$it" }}) AND before_present=0 AND after_present=1 LIMIT 100",*parameters)
            for(row in matches) { cancel.throwIfCancelled();found.add(row.string("id")) }
        }
        return found
    }
    suspend fun current(id: String): Image? {
        val rows=query("SELECT ${lotProjection()} FROM inventory_lot WHERE lot_id=:id","id" to text(id))
        return rows.singleOrNull()?.let { val m=meta(it);inventoryRequire(m.id==id);load(m,"SELECT payload FROM inventory_lot WHERE lot_id=:id","id" to text(id)) }
    }
    suspend fun load(meta: Meta,sql: String,vararg params: Pair<String,SqlValue>): Image {
        val bytes=query(sql,*params).single().blob("payload")
        inventoryRequire(bytes.size==meta.length && InventoryContent.sha(bytes.copyBytes(),cancel)==meta.sha)
        val lot=content.decode(bytes)
        inventoryRequire(lot.id.value==meta.id && lot.revision.value==meta.revision && lot.quantity==meta.quantity)
        return Image(lot,meta.active,bytes)
    }
    fun image(lot: OwnedLot,active: Boolean): Image = Image(lot,active,content.encode(lot))
    suspend fun persist(image: Image,sequence: Long,exists: Boolean) {
        val p=imageParameters(image)+arrayOf("id" to text(image.lot.id.value),"sequence" to num(sequence))
        if(exists) write("UPDATE inventory_lot SET active=:active,revision=:revision,quantity=:quantity,payload_length=:length,payload_version=1,payload_sha256=:sha,payload=:payload,last_operation_sequence=:sequence WHERE lot_id=:id",*p)
        else write("INSERT INTO inventory_lot (lot_id,active,revision,quantity,payload_length,payload_version,payload_sha256,payload,last_operation_sequence) VALUES (:id,:active,:revision,:quantity,:length,1,:sha,:payload,:sequence)",*p)
    }
    fun imageParameters(image: Image): Array<Pair<String,SqlValue>> = arrayOf("active" to num(if(image.active)1 else 0),"revision" to num(image.lot.revision.value),"quantity" to num(image.lot.quantity.toLong()),"length" to num(image.payload.size.toLong()),"sha" to text(InventoryContent.sha(image.payload.copyBytes(),cancel)),"payload" to SqlValue.Blob(image.payload))
    companion object {
        fun request(sql: String,vararg p: Pair<String,SqlValue>,limit: Int=100)=SqlRequest(sql,SnapshotList.copyOf(p.map { SqlParameter(it.first,it.second) }),limit)
        fun text(s: String): SqlValue=SqlValue.Text(s)
        fun num(n: Long): SqlValue=SqlValue.Integer(n)
        fun nullable(n: Long?): SqlValue=n?.let(::num)?:SqlValue.Null
        fun integer(column: String,alias: String=column): String="CASE WHEN typeof($column)='integer' THEN $column ELSE NULL END AS $alias"
        fun boundedText(column: String,alias: String=column,max: Int=64): String="CASE WHEN typeof($column)='text' AND length(CAST($column AS BLOB)) BETWEEN 1 AND $max THEN CAST($column AS BLOB) ELSE NULL END AS $alias"
        fun RowData.column(name: String): SqlValue=columns.singleOrNull { it.name==name }?.value?:inventoryFail(FailureCode.CORRUPT_DATA)
        fun RowData.long(name: String): Long=(column(name) as? SqlValue.Integer)?.value?:inventoryFail(FailureCode.CORRUPT_DATA)
        fun RowData.nullableLong(name: String): Long? { inventoryRequire(long("${name}_valid")==1L);return if(column(name)==SqlValue.Null)null else long(name) }
        fun nullableProjection(column: String,alias: String=column): String="${integer(column,alias)},CASE WHEN $column IS NULL OR typeof($column)='integer' THEN 1 ELSE 0 END AS ${alias}_valid"
        fun RowData.blob(name: String): SnapshotBytes=(column(name) as? SqlValue.Blob)?.value?:inventoryFail(FailureCode.CORRUPT_DATA)
        fun RowData.string(name: String): String = try { val value=Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(blob(name).copyBytes())).toString();InventoryContent.id(value,FailureCode.CORRUPT_DATA);value } catch(_:java.nio.charset.CharacterCodingException) { inventoryFail(FailureCode.CORRUPT_DATA) }
        fun RowData.hash(name: String): String=string(name).also { inventoryRequire(it.length==64 && it.all { c -> c in "0123456789abcdef" }) }
        fun lotProjection(): String=boundedText("lot_id","id",1024)+","+listOf("active","revision","quantity","payload_length","payload_version","last_operation_sequence").joinToString(",") { integer(it) }+","+boundedText("payload_sha256","sha")+",CASE WHEN typeof(payload)='blob' THEN length(payload) ELSE NULL END AS actual,EXISTS(SELECT 1 FROM inventory_operation o JOIN inventory_change c ON c.operation_sequence=o.sequence AND c.lot_id=inventory_lot.lot_id WHERE o.sequence=inventory_lot.last_operation_sequence AND typeof(o.result_revision)='integer' AND o.result_revision=inventory_lot.revision AND o.result_revision>0 AND typeof(o.expected_revision)='integer' AND o.expected_revision>=0 AND o.expected_revision<9223372036854775807 AND o.result_revision=o.expected_revision+1 AND typeof(o.change_count)='integer' AND o.change_count>0 AND o.kind IN ('APPLY','UNDO') AND typeof(c.after_present)='integer' AND c.after_present=1 AND typeof(c.after_active)='integer' AND c.after_active=inventory_lot.active AND typeof(c.after_revision)='integer' AND c.after_revision=inventory_lot.revision AND typeof(c.after_quantity)='integer' AND c.after_quantity=inventory_lot.quantity AND typeof(c.after_payload_version)='integer' AND c.after_payload_version=inventory_lot.payload_version AND typeof(c.after_payload_length)='integer' AND c.after_payload_length=inventory_lot.payload_length AND typeof(c.after_payload_sha256)='text' AND c.after_payload_sha256=inventory_lot.payload_sha256 AND typeof(c.after_payload)='blob' AND length(c.after_payload)=c.after_payload_length) AS writer_valid"
        fun meta(row: RowData): Meta {
            val id=row.string("id");val active=row.long("active");val rev=row.long("revision");val qty=row.long("quantity");val length=row.long("payload_length")
            inventoryRequire(active in 0..1 && rev>=0 && qty in 0..Int.MAX_VALUE.toLong() && length in 6..DeckPayloadLimits.MAX_ENCODED_BYTES.toLong() && row.long("payload_version")==1L && row.long("actual")==length && row.long("last_operation_sequence")>0 && row.long("writer_valid")==1L)
            return Meta(id,active==1L,rev,qty.toInt(),length.toInt(),row.hash("sha"))
        }
    }
}
