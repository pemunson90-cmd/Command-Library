package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Additive life data only. Room/caller owns the transaction; no old table is rewritten. */
internal object Migration5To6 : Migration(5, 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
        val statements = listOf(
            """CREATE TABLE `life_game` (
                `game_source` TEXT NOT NULL, `game_id` TEXT NOT NULL, `revision` INTEGER NOT NULL,
                `creation_operation_id` TEXT NOT NULL, `creation_payload` BLOB NOT NULL, `creation_sha256` TEXT NOT NULL,
                `checkpoint_payload` BLOB NOT NULL, `checkpoint_sequence` INTEGER NOT NULL, `status` TEXT NOT NULL,
                `started_seconds` INTEGER NOT NULL, `started_nano` INTEGER NOT NULL, `ended_seconds` INTEGER, `ended_nano` INTEGER,
                `updated_seconds` INTEGER NOT NULL, `updated_nano` INTEGER NOT NULL, PRIMARY KEY(`game_source`, `game_id`))""",
            "CREATE UNIQUE INDEX `index_life_game_creation_operation` ON `life_game` (`creation_operation_id`)",
            "CREATE INDEX `index_life_game_status_started` ON `life_game` (`status`, `started_seconds`, `started_nano`, `game_source`, `game_id`)",
            "CREATE INDEX `index_life_game_started` ON `life_game` (`started_seconds`, `started_nano`, `game_source`, `game_id`)",
            """CREATE TABLE `life_event` (
                `game_source` TEXT NOT NULL, `game_id` TEXT NOT NULL, `event_id` INTEGER NOT NULL, `revision` INTEGER NOT NULL,
                `group_id` TEXT NOT NULL, `at_seconds` INTEGER NOT NULL, `at_nano` INTEGER NOT NULL, `undo_of_event_id` INTEGER,
                `archive_payload` BLOB NOT NULL, PRIMARY KEY(`game_source`, `game_id`, `event_id`),
                FOREIGN KEY(`game_source`, `game_id`) REFERENCES `life_game`(`game_source`, `game_id`) ON UPDATE NO ACTION ON DELETE CASCADE)""",
            "CREATE UNIQUE INDEX `index_life_event_revision` ON `life_event` (`game_source`, `game_id`, `revision`)",
            "CREATE UNIQUE INDEX `index_life_event_undo_target` ON `life_event` (`game_source`, `game_id`, `undo_of_event_id`)",
            """CREATE TABLE `life_seat_search` (
                `game_source` TEXT NOT NULL, `game_id` TEXT NOT NULL, `seat_id` TEXT NOT NULL,
                `player_name` TEXT NOT NULL, `player_name_folded` TEXT NOT NULL, `deck_source` TEXT, `deck_id` TEXT,
                `deck_name` TEXT, `deck_name_folded` TEXT, PRIMARY KEY(`game_source`, `game_id`, `seat_id`),
                FOREIGN KEY(`game_source`, `game_id`) REFERENCES `life_game`(`game_source`, `game_id`) ON UPDATE NO ACTION ON DELETE CASCADE)""",
            "CREATE INDEX `index_life_seat_player` ON `life_seat_search` (`player_name_folded`, `game_source`, `game_id`)",
            "CREATE INDEX `index_life_seat_deck` ON `life_seat_search` (`deck_source`, `deck_id`, `game_source`, `game_id`)",
            "CREATE INDEX `index_life_seat_deck_name` ON `life_seat_search` (`deck_name_folded`, `game_source`, `game_id`)",
            """CREATE TABLE `life_result` (
                `game_source` TEXT NOT NULL, `game_id` TEXT NOT NULL, `result_id` TEXT NOT NULL, `revision` INTEGER NOT NULL,
                `finished_seconds` INTEGER NOT NULL, `finished_nano` INTEGER NOT NULL, `reason` TEXT NOT NULL, `retracted` INTEGER NOT NULL,
                PRIMARY KEY(`game_source`, `game_id`),
                FOREIGN KEY(`game_source`, `game_id`) REFERENCES `life_game`(`game_source`, `game_id`) ON UPDATE NO ACTION ON DELETE CASCADE)""",
            "CREATE UNIQUE INDEX `index_life_result_identity` ON `life_result` (`result_id`)",
            """CREATE TABLE `life_alert` (
                `game_source` TEXT NOT NULL, `game_id` TEXT NOT NULL, `occurrence_id` TEXT NOT NULL, `target_kind` TEXT NOT NULL,
                `target_seat_id` TEXT, `fired_seconds` INTEGER NOT NULL, `fired_nano` INTEGER NOT NULL,
                `acknowledged_seconds` INTEGER, `acknowledged_nano` INTEGER, `delivery_mask` INTEGER NOT NULL,
                PRIMARY KEY(`game_source`, `game_id`, `occurrence_id`),
                FOREIGN KEY(`game_source`, `game_id`) REFERENCES `life_game`(`game_source`, `game_id`) ON UPDATE NO ACTION ON DELETE CASCADE)""",
            "CREATE INDEX `index_life_alert_acknowledgement` ON `life_alert` (`game_source`, `game_id`, `acknowledged_seconds`, `acknowledged_nano`)",
            """CREATE TABLE `life_profile` (
                `profile_id` TEXT NOT NULL, `revision` INTEGER NOT NULL, `name` TEXT NOT NULL, `name_folded` TEXT NOT NULL,
                `payload` BLOB NOT NULL, `updated_seconds` INTEGER NOT NULL, `updated_nano` INTEGER NOT NULL, PRIMARY KEY(`profile_id`))""",
            "CREATE INDEX `index_life_profile_name` ON `life_profile` (`name_folded`, `profile_id`)",
        )
        for (sql in statements) connection.prepare(sql).use { it.step() }
    }
}
