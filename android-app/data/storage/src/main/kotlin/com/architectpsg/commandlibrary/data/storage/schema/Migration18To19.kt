package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Additive vocabulary only; no existing deck, draft, collection, game or history row is rewritten. */
internal object Migration18To19 : Migration(18, 19) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.prepare("""CREATE TABLE IF NOT EXISTS `personal_tag_pool` (`singleton_id` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `modified_seconds` INTEGER NOT NULL, `modified_nanos` INTEGER NOT NULL, `payload_version` INTEGER NOT NULL, `payload_length` INTEGER NOT NULL, `payload_sha256` TEXT NOT NULL, `payload` BLOB NOT NULL, PRIMARY KEY(`singleton_id`))""").use { it.step() }
    }
}
