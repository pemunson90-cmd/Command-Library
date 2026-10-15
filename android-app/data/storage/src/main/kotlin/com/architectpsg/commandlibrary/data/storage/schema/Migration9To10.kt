package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Additive empty local deck tables only; no source/user-data rewrite or synthetic receipts. */
internal object Migration9To10 : Migration(9, 10) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.prepare("""CREATE TABLE IF NOT EXISTS `deck_head` (`provider` TEXT NOT NULL, `deck_id` TEXT NOT NULL, `revision` INTEGER NOT NULL, `name` TEXT NOT NULL, `created_seconds` INTEGER NOT NULL, `created_nano` INTEGER NOT NULL, `modified_seconds` INTEGER NOT NULL, `modified_nano` INTEGER NOT NULL, `entry_count` INTEGER NOT NULL, `total_quantity` INTEGER NOT NULL, PRIMARY KEY(`provider`, `deck_id`))""").use { it.step() }
        connection.prepare("""CREATE TABLE IF NOT EXISTS `deck_revision` (`provider` TEXT NOT NULL, `deck_id` TEXT NOT NULL, `revision` INTEGER NOT NULL, `name` TEXT NOT NULL, `created_seconds` INTEGER NOT NULL, `created_nano` INTEGER NOT NULL, `modified_seconds` INTEGER NOT NULL, `modified_nano` INTEGER NOT NULL, `entry_count` INTEGER NOT NULL, `total_quantity` INTEGER NOT NULL, `payload_bytes` INTEGER NOT NULL, `payload_sha256` TEXT NOT NULL, `payload` BLOB NOT NULL, PRIMARY KEY(`provider`, `deck_id`, `revision`), FOREIGN KEY(`provider`, `deck_id`) REFERENCES `deck_head`(`provider`, `deck_id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""").use { it.step() }
        connection.prepare("""CREATE TABLE IF NOT EXISTS `deck_operation` (`sequence` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `operation_id` TEXT NOT NULL, `request_version` INTEGER NOT NULL, `request_sha256` TEXT NOT NULL, `kind` TEXT NOT NULL, `provider` TEXT NOT NULL, `deck_id` TEXT NOT NULL, `result_revision` INTEGER NOT NULL, FOREIGN KEY(`provider`, `deck_id`, `result_revision`) REFERENCES `deck_revision`(`provider`, `deck_id`, `revision`) ON UPDATE NO ACTION ON DELETE NO ACTION )""").use { it.step() }
        connection.prepare("""CREATE UNIQUE INDEX IF NOT EXISTS `index_deck_operation_id` ON `deck_operation` (`operation_id`)""").use { it.step() }
        connection.prepare("""CREATE INDEX IF NOT EXISTS `index_deck_operation_result` ON `deck_operation` (`provider`, `deck_id`, `result_revision`)""").use { it.step() }
    }
}
