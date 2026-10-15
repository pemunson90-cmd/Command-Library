package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*

/** Internal SQL projections: bounded variable fields and typed scalars before driver allocation. */
internal object DeckLibraryRows {
    private val integers = listOf("revision", "created_seconds", "created_nano", "modified_seconds", "modified_nano", "entry_count", "total_quantity")
    fun text(column: String, maximum: Int, alias: String): String =
        "CASE WHEN typeof($column)='text' AND length(CAST($column AS BLOB)) BETWEEN 1 AND $maximum THEN CAST($column AS BLOB) ELSE NULL END AS $alias"
    fun integer(column: String, alias: String): String = "CASE WHEN typeof($column)='integer' THEN $column ELSE NULL END AS $alias"
    fun projection(table: String, prefix: String, payload: Boolean): String = buildList {
        add(text("$table.provider", 1024, "${prefix}provider")); add(text("$table.deck_id", 1024, "${prefix}deck_id"))
        add(text("$table.name", 1024, "${prefix}name"))
        integers.forEach { add(integer("$table.$it", "$prefix$it")) }
        if (payload) {
            add(integer("$table.payload_bytes", "${prefix}payload_bytes"))
            add(text("$table.payload_sha256", 64, "${prefix}payload_sha256"))
            add("CASE WHEN typeof($table.payload)='blob' THEN length($table.payload) ELSE NULL END AS ${prefix}actual_bytes")
        }
    }.joinToString(",")

    data class Metadata(val summary: DeckSummary, val bytes: Long? = null, val sha: String? = null)
    fun metadata(row: RowData, prefix: String, payload: Boolean): Metadata {
        val source = row.utf8("${prefix}provider")
        val id = row.utf8("${prefix}deck_id")
        val name = row.utf8("${prefix}name")
        val revision = row.long("${prefix}revision")
        val createdSeconds = row.long("${prefix}created_seconds")
        val createdNano = row.long("${prefix}created_nano")
        val modifiedSeconds = row.long("${prefix}modified_seconds")
        val modifiedNano = row.long("${prefix}modified_nano")
        val count = row.long("${prefix}entry_count")
        val quantity = row.long("${prefix}total_quantity")
        corrupt(revision >= 0 && createdNano in 0..999_999_999 && modifiedNano in 0..999_999_999)
        corrupt(modifiedSeconds > createdSeconds || modifiedSeconds == createdSeconds && modifiedNano >= createdNano)
        corrupt(count in 0..DeckPayloadLimits.MAX_ENTRIES.toLong() && quantity in 0..count * Int.MAX_VALUE.toLong())
        corrupt((count == 0L && quantity == 0L) || (count > 0 && quantity >= count))
        val summary = DeckSummary(DeckId(ProviderId(source), id), name, Revision(revision), UtcTimestamp(createdSeconds, createdNano.toInt()),
            UtcTimestamp(modifiedSeconds, modifiedNano.toInt()), count.toInt(), quantity)
        if (!payload) return Metadata(summary)
        val bytes = row.long("${prefix}payload_bytes")
        val sha = row.utf8("${prefix}payload_sha256")
        corrupt(bytes in 1..DeckPayloadLimits.MAX_ENCODED_BYTES.toLong() && row.long("${prefix}actual_bytes") == bytes)
        corrupt(sha.length == 64 && sha.all { it in "0123456789abcdef" })
        return Metadata(summary, bytes, sha)
    }
    fun summary(snapshot: DeckSnapshot): DeckSummary = DeckSummary(snapshot.id, snapshot.name, snapshot.revision,
        snapshot.createdAt, snapshot.modifiedAt, snapshot.entries.size, snapshot.entries.sumOf { it.quantity.toLong() })
    fun RowData.column(name: String): SqlValue = columns.singleOrNull { it.name == name }?.value
        ?: reject(FailureCode.CORRUPT_DATA, "Missing stored deck column")
    fun RowData.long(name: String): Long = (column(name) as? SqlValue.Integer)?.value
        ?: reject(FailureCode.CORRUPT_DATA, "Invalid stored deck integer")
    fun RowData.utf8(name: String): String {
        val bytes = (column(name) as? SqlValue.Blob)?.value ?: reject(FailureCode.CORRUPT_DATA, "Invalid stored deck text")
        return checked(FailureCode.CORRUPT_DATA) {
            DeckLibraryRequests.decodeUtf8(bytes.copyBytes()).also { DeckLibraryRequests.utf8(it, 1024) }
        }
    }
    fun request(sql: String, vararg parameters: Pair<String, SqlValue>, maxRows: Int = 1): SqlRequest =
        SqlRequest(sql, SnapshotList.copyOf(parameters.map { SqlParameter(it.first, it.second) }), maxRows)
    fun text(value: String): SqlValue = SqlValue.Text(value)
    fun number(value: Long): SqlValue = SqlValue.Integer(value)
}
