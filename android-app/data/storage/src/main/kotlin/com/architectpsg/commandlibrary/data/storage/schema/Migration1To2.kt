package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Adds only rules tables and indices. Room owns the transaction, schema validation and identity. */
internal object Migration1To2 : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        for (sql in statements) connection.prepare(sql).use { it.step() }
    }

    private val statements = listOf(
        """CREATE TABLE IF NOT EXISTS `rules_corpus` (`generation_id` TEXT NOT NULL, `provider` TEXT NOT NULL, `language` TEXT NOT NULL, `status` TEXT NOT NULL, `content_uri` TEXT NOT NULL, `display_name` TEXT NOT NULL, `source_payload` TEXT NOT NULL, `provenance_payload` TEXT NOT NULL, `upstream_version` TEXT, `effective_at_epoch_seconds` INTEGER, `effective_at_nano` INTEGER, `source_date_epoch_seconds` INTEGER, `source_date_nano` INTEGER, `source_uri` TEXT, `observed_at_epoch_seconds` INTEGER NOT NULL, `observed_at_nano` INTEGER NOT NULL, `content_sha256` TEXT NOT NULL, `observed_bytes` INTEGER NOT NULL, `entry_count` INTEGER NOT NULL, `reference_count` INTEGER NOT NULL, `payload_schema_version` INTEGER NOT NULL, `activated_at_epoch_seconds` INTEGER, `activated_at_nano` INTEGER, PRIMARY KEY(`generation_id`))""",
        """CREATE INDEX IF NOT EXISTS `index_rules_corpus_provider_status` ON `rules_corpus` (`provider`, `status`)""",
        """CREATE TABLE IF NOT EXISTS `rules_active` (`singleton_id` INTEGER NOT NULL, `generation_id` TEXT NOT NULL, PRIMARY KEY(`singleton_id`), FOREIGN KEY(`generation_id`) REFERENCES `rules_corpus`(`generation_id`) ON UPDATE NO ACTION ON DELETE NO ACTION)""",
        """CREATE INDEX IF NOT EXISTS `index_rules_active_generation_id` ON `rules_active` (`generation_id`)""",
        """CREATE TABLE IF NOT EXISTS `rule_entry` (`corpus_id` TEXT NOT NULL, `provider` TEXT NOT NULL, `local_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `number` TEXT, `title` TEXT, `text` TEXT NOT NULL, `ordinal` INTEGER NOT NULL, `entry_payload` TEXT NOT NULL, PRIMARY KEY(`corpus_id`, `provider`, `local_id`), FOREIGN KEY(`corpus_id`) REFERENCES `rules_corpus`(`generation_id`) ON UPDATE NO ACTION ON DELETE CASCADE)""",
        """CREATE UNIQUE INDEX IF NOT EXISTS `index_rule_entry_corpus_ordinal` ON `rule_entry` (`corpus_id`, `ordinal`)""",
        """CREATE TABLE IF NOT EXISTS `rule_reference` (`corpus_id` TEXT NOT NULL, `from_provider` TEXT NOT NULL, `from_local_id` TEXT NOT NULL, `ref_ordinal` INTEGER NOT NULL, `raw_target` TEXT NOT NULL, `resolved_provider` TEXT, `resolved_local_id` TEXT, `evidence_payload` TEXT NOT NULL, PRIMARY KEY(`corpus_id`, `from_provider`, `from_local_id`, `ref_ordinal`), FOREIGN KEY(`corpus_id`, `from_provider`, `from_local_id`) REFERENCES `rule_entry`(`corpus_id`, `provider`, `local_id`) ON UPDATE NO ACTION ON DELETE CASCADE)""",
        """CREATE TABLE IF NOT EXISTS `rules_import_attempt` (`attempt_id` TEXT NOT NULL, `generation_id` TEXT, `source_payload` TEXT NOT NULL, `attempt_payload` TEXT NOT NULL, `phase` TEXT NOT NULL, `started_at_epoch_seconds` INTEGER NOT NULL, `started_at_nano` INTEGER NOT NULL, `finished_at_epoch_seconds` INTEGER, `finished_at_nano` INTEGER, `failure_payload` TEXT, `payload_schema_version` INTEGER NOT NULL, PRIMARY KEY(`attempt_id`))""",
        """CREATE INDEX IF NOT EXISTS `index_rules_import_attempt_started` ON `rules_import_attempt` (`started_at_epoch_seconds`, `started_at_nano`, `attempt_id`)""",
        """CREATE VIRTUAL TABLE IF NOT EXISTS `rules_text_fts` USING FTS5(`title`, `text`, `corpus_id` UNINDEXED, `provider` UNINDEXED, `local_id` UNINDEXED, tokenize=`unicode61 remove_diacritics 2`)""",
    )
}
