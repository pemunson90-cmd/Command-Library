package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Index retained creation images so missing tombstones cannot silently release historical IDs. */
internal object Migration12To13 : Migration(12, 13) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.prepare("""CREATE INDEX IF NOT EXISTS `index_inventory_change_introduced_id` ON `inventory_change` (`lot_id`, `before_present`, `after_present`, `operation_sequence`)""").use { it.step() }
    }
}
