package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Additive nonsecret provider configuration and permanent identity tombstones. */
internal object Migration15To16 : Migration(15, 16) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.prepare("""CREATE TABLE IF NOT EXISTS `provider_profile` (`profile_id` TEXT NOT NULL, `revision` INTEGER NOT NULL, `change_sequence` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `created_seconds` INTEGER NOT NULL, `created_nanos` INTEGER NOT NULL, `modified_seconds` INTEGER NOT NULL, `modified_nanos` INTEGER NOT NULL, `payload_version` INTEGER NOT NULL, `payload_length` INTEGER NOT NULL, `payload_sha256` TEXT NOT NULL, `payload` BLOB, PRIMARY KEY(`profile_id`))""").use { it.step() }
        connection.prepare("""CREATE UNIQUE INDEX IF NOT EXISTS `index_provider_profile_change_sequence` ON `provider_profile` (`change_sequence`)""").use { it.step() }
    }
}
