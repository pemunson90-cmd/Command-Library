package com.architectpsg.commandlibrary.data.storage.collection.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.HostLifeDatabase
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CancellationException

/** Actual bundled host SQLite, accepted schema13 inventory tables; no Room/native evidence. */
internal class InventoryHostDatabase(file: File,initialize: Boolean=true): Database,Closeable {
    val host=HostLifeDatabase(file,initialize=false)
    var loseAcknowledgement=false
    val maxParameters:Int=host.connection.prepare("PRAGMA compile_options").use { statement ->
        var maximum:Int?=null
        while(statement.step()) { val option=statement.getText(0);if(option.startsWith("MAX_VARIABLE_NUMBER="))maximum=option.substringAfter('=').toInt() }
        requireNotNull(maximum) { "Actual SQLite variable bound unavailable" }
    }
    var capabilityCalls=0
    private var inTransaction=false
    init {
        if(initialize) javaClass.getResourceAsStream("/inventory-schema13.sql")!!.bufferedReader().useLines { lines ->
            lines.filter { it.startsWith("CREATE TABLE IF NOT EXISTS `inventory_") || it.startsWith("CREATE UNIQUE INDEX IF NOT EXISTS `index_inventory_") || it.startsWith("CREATE INDEX IF NOT EXISTS `index_inventory_") }.forEach { host.sql(it) }
        }
    }
    override suspend fun query(request: SqlRequest,cancel: CancelToken)=host.query(request,cancel)
    override suspend fun <T> transaction(action: suspend (DbTransaction)->T,cancel: CancelToken): Outcome<T> {
        check(!inTransaction);inTransaction=true
        val result=try { host.transaction(action,cancel) } finally { inTransaction=false }
        if(loseAcknowledgement && result is Outcome.Success)throw CancellationException("Lost commit acknowledgement")
        return result
    }
    override suspend fun capabilities(cancel: CancelToken): Outcome<DbCapabilities> { check(!inTransaction) { "Nested capability transaction" };capabilityCalls++;cancel.throwIfCancelled();return Outcome.Success(DbCapabilities("host bundled SQLite",13,false,true,true,maxParameters)) }
    fun sql(sql: String)=host.sql(sql)
    fun number(sql: String)=host.scalar(sql)
    fun bytes(sql: String): ByteArray=host.connection.prepare(sql).use { check(it.step());it.getBlob(0) }
    fun state(): Map<String,List<List<String>>> = (TABLES+"sqlite_sequence").associateWith { table ->
        host.connection.prepare("SELECT * FROM $table ORDER BY 1"+(if(table=="inventory_change")",2" else "")).use { s -> buildList { while(s.step())add((0 until s.getColumnCount()).map { i -> when(s.getColumnType(i)) {1->"n:${s.getLong(i)}";3->"t:${s.getText(i)}";4->"b:${hex(s.getBlob(i))}";5->"null";else->"r:${s.getDouble(i)}"} }) } }
    }
    override fun close()=host.close()
    companion object {
        val TABLES=listOf("inventory_state","inventory_lot","inventory_operation","inventory_change","inventory_group")
        fun hex(bytes: ByteArray):String {
            val digits="0123456789abcdef";val chars=CharArray(bytes.size*2)
            for(i in bytes.indices) { val value=bytes[i].toInt() and 255;chars[i*2]=digits[value ushr 4];chars[i*2+1]=digits[value and 15] }
            return String(chars)
        }
    }
}
