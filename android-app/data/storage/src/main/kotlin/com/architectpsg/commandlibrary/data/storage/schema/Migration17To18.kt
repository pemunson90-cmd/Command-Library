package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Additive local AI history; all earlier user/provider/template state remains untouched. */
internal object Migration17To18 : Migration(17, 18) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.prepare("""CREATE TABLE IF NOT EXISTS `ai_conversation` (`conversation_id` TEXT NOT NULL, `revision` INTEGER NOT NULL, `change_sequence` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `deck_source` TEXT NOT NULL, `deck_raw` TEXT NOT NULL, `mode` TEXT NOT NULL, `created_seconds` INTEGER NOT NULL, `created_nanos` INTEGER NOT NULL, `modified_seconds` INTEGER NOT NULL, `modified_nanos` INTEGER NOT NULL, `payload_version` INTEGER NOT NULL, `payload_length` INTEGER NOT NULL, `payload_sha256` TEXT NOT NULL, `payload` BLOB, PRIMARY KEY(`conversation_id`))""").use { it.step() }
        connection.prepare("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_conversation_change_sequence` ON `ai_conversation` (`change_sequence`)").use { it.step() }
        connection.prepare("CREATE INDEX IF NOT EXISTS `index_ai_conversation_deck` ON `ai_conversation` (`deck_source`, `deck_raw`, `deleted`, `conversation_id`)").use { it.step() }
        connection.prepare("""CREATE TABLE IF NOT EXISTS `ai_turn` (`turn_id` TEXT NOT NULL, `conversation_id` TEXT NOT NULL, `revision` INTEGER NOT NULL, `ordinal` INTEGER NOT NULL, `source` TEXT NOT NULL, `owner_epoch` TEXT, `status` TEXT NOT NULL, `deleted` INTEGER NOT NULL, `started_seconds` INTEGER NOT NULL, `started_nanos` INTEGER NOT NULL, `finished_seconds` INTEGER, `finished_nanos` INTEGER, `payload_version` INTEGER NOT NULL, `payload_length` INTEGER NOT NULL, `payload_sha256` TEXT NOT NULL, `payload` BLOB, PRIMARY KEY(`turn_id`))""").use { it.step() }
        connection.prepare("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_turn_conversation_ordinal` ON `ai_turn` (`conversation_id`, `ordinal`)").use { it.step() }
        connection.prepare("CREATE INDEX IF NOT EXISTS `index_ai_turn_recovery` ON `ai_turn` (`deleted`, `status`, `owner_epoch`)").use { it.step() }
    }
}
