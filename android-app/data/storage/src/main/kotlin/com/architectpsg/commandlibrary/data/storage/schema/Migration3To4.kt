package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Adds full-input provenance and memberships. Room owns the transaction, validation and identity. */
internal object Migration3To4 : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Composite SQLite FKs permit partial-null triples, but those cannot identify an Oracle.
        // Fail before publishing any additions rather than silently omitting or guessing a relation.
        connection.prepare("""SELECT 1 FROM printing WHERE NOT (
            (oracle_provider IS NULL AND oracle_id IS NULL AND oracle_language IS NULL) OR
            (oracle_provider IS NOT NULL AND oracle_id IS NOT NULL AND oracle_language IS NOT NULL)
        ) LIMIT 1""").use { statement ->
            check(!statement.step()) { "Cannot migrate a printing with a partial Oracle identity" }
        }
        // Room enables FK enforcement in onOpen, after migrations. Do not depend on an INSERT
        // constraint to reject a complete but dangling legacy relation on that initial connection.
        connection.prepare("""SELECT 1 FROM printing AS p
            WHERE p.oracle_provider IS NOT NULL AND p.oracle_id IS NOT NULL AND p.oracle_language IS NOT NULL
            AND NOT EXISTS (SELECT 1 FROM oracle_card AS o
                WHERE o.generation_id=p.generation_id AND o.provider=p.oracle_provider
                AND o.oracle_id=p.oracle_id AND o.language=p.oracle_language)
            LIMIT 1""").use { statement ->
            check(!statement.step()) { "Cannot migrate a printing with a missing Oracle target" }
        }
        for (sql in statements) connection.prepare(sql).use { it.step() }
    }

    private val statements = listOf(
        """ALTER TABLE `catalog_generation` ADD COLUMN `input_kind` TEXT NOT NULL DEFAULT 'USER_CONTENT_URI'""",
        """ALTER TABLE `catalog_generation` ADD COLUMN `artifact_job_id` TEXT""",
        """ALTER TABLE `catalog_generation` ADD COLUMN `source_payload` TEXT""",
        """ALTER TABLE `rules_corpus` ADD COLUMN `input_kind` TEXT NOT NULL DEFAULT 'USER_CONTENT_URI'""",
        """ALTER TABLE `rules_corpus` ADD COLUMN `artifact_job_id` TEXT""",
        """CREATE TABLE `printing_oracle_membership` (
            `generation_id` TEXT NOT NULL, `printing_provider` TEXT NOT NULL, `printing_id` TEXT NOT NULL,
            `source_slot` INTEGER NOT NULL, `source_kind` TEXT NOT NULL, `oracle_provider` TEXT NOT NULL,
            `oracle_id` TEXT NOT NULL, `oracle_language` TEXT NOT NULL,
            PRIMARY KEY(`generation_id`, `printing_provider`, `printing_id`, `source_slot`),
            FOREIGN KEY(`generation_id`, `printing_provider`, `printing_id`)
                REFERENCES `printing`(`generation_id`, `provider`, `printing_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(`generation_id`, `oracle_provider`, `oracle_id`, `oracle_language`)
                REFERENCES `oracle_card`(`generation_id`, `provider`, `oracle_id`, `language`) ON UPDATE NO ACTION ON DELETE NO ACTION
        )""",
        """CREATE INDEX `index_printing_oracle_membership_lookup` ON `printing_oracle_membership`
            (`generation_id`, `oracle_provider`, `oracle_id`, `oracle_language`, `printing_provider`, `printing_id`)""",
        """INSERT INTO `printing_oracle_membership`
            (`generation_id`, `printing_provider`, `printing_id`, `source_slot`, `source_kind`, `oracle_provider`, `oracle_id`, `oracle_language`)
            SELECT `generation_id`, `provider`, `printing_id`, -1, 'WHOLE', `oracle_provider`, `oracle_id`, `oracle_language`
            FROM `printing` WHERE `oracle_provider` IS NOT NULL AND `oracle_id` IS NOT NULL AND `oracle_language` IS NOT NULL""",
    )
}
