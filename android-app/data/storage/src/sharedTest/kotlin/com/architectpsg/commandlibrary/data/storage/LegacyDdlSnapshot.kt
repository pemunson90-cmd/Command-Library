package com.architectpsg.commandlibrary.data.storage

import com.architectpsg.commandlibrary.core.model.SqlValue

/** Capture every original object, including FTS shadows and null-SQL autoindexes, before migration. */
internal const val LEGACY_DDL_SNAPSHOT_QUERY: String =
    "SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name != 'room_master_table' ORDER BY type,name"

/** Keep all original tables and their complete current object sets; genuinely new tables are separate.
 * Use the FULL snapshot query for rollback, so leaked new tables cannot disappear from that check.
 */
internal fun legacyDdlQueryFor(baseline: List<List<SqlValue>>): String {
    require(baseline.isNotEmpty()) { "Legacy DDL baseline is empty" }
    val tables = baseline.map { row ->
        require(row.size == 4)
        requireNotNull((row[2] as? SqlValue.Text)?.value).also { require(it.isNotEmpty()) }
    }.distinct()
    val names = tables.joinToString(",") { "'" + it.replace("'", "''") + "'" }
    return "SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name != 'room_master_table' AND tbl_name IN ($names) ORDER BY type,name"
}
