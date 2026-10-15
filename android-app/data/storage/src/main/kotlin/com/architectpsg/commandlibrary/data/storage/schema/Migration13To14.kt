package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Add immutable named references and indexed reverse receipt association without rewriting old data. */
internal object Migration13To14 : Migration(13, 14) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.prepare("""CREATE TABLE IF NOT EXISTS `deck_named_version` (`version_id` TEXT NOT NULL, `metadata_version` INTEGER NOT NULL, `provider` TEXT NOT NULL, `deck_id` TEXT NOT NULL, `label` TEXT NOT NULL, `created_seconds` INTEGER NOT NULL, `created_nano` INTEGER NOT NULL, `source_revision` INTEGER NOT NULL, `creating_sequence` INTEGER NOT NULL, `predecessor_version_id` TEXT, `restored_target_version_id` TEXT, `kind` TEXT NOT NULL, `metadata_sha256` TEXT NOT NULL, PRIMARY KEY(`version_id`), FOREIGN KEY(`provider`, `deck_id`, `source_revision`) REFERENCES `deck_revision`(`provider`, `deck_id`, `revision`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`creating_sequence`) REFERENCES `deck_operation`(`sequence`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`provider`, `deck_id`, `predecessor_version_id`) REFERENCES `deck_named_version`(`provider`, `deck_id`, `version_id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`provider`, `deck_id`, `restored_target_version_id`) REFERENCES `deck_named_version`(`provider`, `deck_id`, `version_id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""").use { it.step() }
        connection.prepare("""CREATE UNIQUE INDEX IF NOT EXISTS `index_deck_named_version_identity` ON `deck_named_version` (`provider`, `deck_id`, `version_id`)""").use { it.step() }
        connection.prepare("""CREATE UNIQUE INDEX IF NOT EXISTS `index_deck_named_version_receipt` ON `deck_named_version` (`creating_sequence`)""").use { it.step() }
        connection.prepare("""CREATE INDEX IF NOT EXISTS `index_deck_named_version_order` ON `deck_named_version` (`provider`, `deck_id`, `creating_sequence`)""").use { it.step() }
        connection.prepare("""CREATE INDEX IF NOT EXISTS `index_deck_named_version_source` ON `deck_named_version` (`provider`, `deck_id`, `source_revision`)""").use { it.step() }
        connection.prepare("""CREATE INDEX IF NOT EXISTS `index_deck_named_version_predecessor` ON `deck_named_version` (`provider`, `deck_id`, `predecessor_version_id`)""").use { it.step() }
        connection.prepare("""CREATE INDEX IF NOT EXISTS `index_deck_named_version_target` ON `deck_named_version` (`provider`, `deck_id`, `restored_target_version_id`)""").use { it.step() }
        connection.prepare("""CREATE INDEX IF NOT EXISTS `index_deck_operation_version_latest` ON `deck_operation` (`provider`, `deck_id`, `kind`, `sequence`)""").use { it.step() }
    }
}
