package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Additive independent ruling corpus; Room/caller owns rollback. No existing row is rewritten. */
internal object Migration6To7 : Migration(6, 7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        val statements = listOf(
            """CREATE TABLE `rulings_generation` (`generation_id` TEXT NOT NULL, `provider` TEXT NOT NULL,
                `status` TEXT NOT NULL, `language_scope` TEXT NOT NULL, `provenance_payload` TEXT NOT NULL,
                `record_count` INTEGER NOT NULL, `payload_schema_version` INTEGER NOT NULL, PRIMARY KEY(`generation_id`))""",
            """CREATE TABLE `ruling_record` (`generation_id` TEXT NOT NULL, `ordinal` INTEGER NOT NULL,
                `oracle_provider` TEXT NOT NULL, `oracle_id` TEXT NOT NULL, `publisher` TEXT NOT NULL,
                `published_day` TEXT NOT NULL, `record_payload` TEXT NOT NULL, PRIMARY KEY(`generation_id`, `ordinal`),
                FOREIGN KEY(`generation_id`) REFERENCES `rulings_generation`(`generation_id`) ON UPDATE NO ACTION ON DELETE CASCADE)""",
            "CREATE INDEX `index_ruling_record_oracle_day_ordinal` ON `ruling_record` (`generation_id` ASC, `oracle_provider` ASC, `oracle_id` ASC, `published_day` DESC, `ordinal` ASC)",
            """CREATE TABLE `rulings_active` (`singleton_id` INTEGER NOT NULL, `generation_id` TEXT NOT NULL,
                PRIMARY KEY(`singleton_id`), FOREIGN KEY(`generation_id`) REFERENCES `rulings_generation`(`generation_id`) ON UPDATE NO ACTION ON DELETE NO ACTION)""",
            "CREATE INDEX `index_rulings_active_generation_id` ON `rulings_active` (`generation_id`)",
        )
        for (sql in statements) connection.prepare(sql).use { it.step() }
    }
}
