package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Add only empty undo tables; existing deck content and receipts remain byte-identical. */
internal object Migration10To11 : Migration(10, 11) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.prepare("""CREATE TABLE IF NOT EXISTS `deck_undo_link` (`provider` TEXT NOT NULL, `deck_id` TEXT NOT NULL, `link_revision` INTEGER NOT NULL, `before_revision` INTEGER NOT NULL, `previous_link_revision` INTEGER, PRIMARY KEY(`provider`, `deck_id`, `link_revision`), FOREIGN KEY(`provider`, `deck_id`, `link_revision`) REFERENCES `deck_revision`(`provider`, `deck_id`, `revision`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`provider`, `deck_id`, `before_revision`) REFERENCES `deck_revision`(`provider`, `deck_id`, `revision`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`provider`, `deck_id`, `previous_link_revision`) REFERENCES `deck_undo_link`(`provider`, `deck_id`, `link_revision`) ON UPDATE NO ACTION ON DELETE NO ACTION )""").use { it.step() }
        connection.prepare("""CREATE INDEX IF NOT EXISTS `index_deck_undo_link_before` ON `deck_undo_link` (`provider`, `deck_id`, `before_revision`)""").use { it.step() }
        connection.prepare("""CREATE INDEX IF NOT EXISTS `index_deck_undo_link_previous` ON `deck_undo_link` (`provider`, `deck_id`, `previous_link_revision`)""").use { it.step() }
        connection.prepare("""CREATE TABLE IF NOT EXISTS `deck_undo_state` (`provider` TEXT NOT NULL, `deck_id` TEXT NOT NULL, `head_revision` INTEGER NOT NULL, `undo_link_revision` INTEGER, PRIMARY KEY(`provider`, `deck_id`), FOREIGN KEY(`provider`, `deck_id`, `head_revision`) REFERENCES `deck_revision`(`provider`, `deck_id`, `revision`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`provider`, `deck_id`, `undo_link_revision`) REFERENCES `deck_undo_link`(`provider`, `deck_id`, `link_revision`) ON UPDATE NO ACTION ON DELETE NO ACTION )""").use { it.step() }
        connection.prepare("""CREATE INDEX IF NOT EXISTS `index_deck_undo_state_head` ON `deck_undo_state` (`provider`, `deck_id`, `head_revision`)""").use { it.step() }
        connection.prepare("""CREATE INDEX IF NOT EXISTS `index_deck_undo_state_link` ON `deck_undo_state` (`provider`, `deck_id`, `undo_link_revision`)""").use { it.step() }
    }
}
