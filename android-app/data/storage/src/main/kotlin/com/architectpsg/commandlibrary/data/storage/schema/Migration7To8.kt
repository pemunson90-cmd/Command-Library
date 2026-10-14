package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Additive source evidence only; no existing schema or retained user/source payload is rewritten. */
internal object Migration7To8 : Migration(7, 8) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.prepare("""CREATE TABLE `catalog_source_receipt` (`receipt_id` TEXT NOT NULL,
            `metadata_job_id` TEXT NOT NULL, `payload_schema_version` INTEGER NOT NULL,
            `receipt_payload` TEXT NOT NULL, PRIMARY KEY(`receipt_id`))""").use { it.step() }
        connection.prepare("CREATE UNIQUE INDEX `index_catalog_source_receipt_metadata_job` ON `catalog_source_receipt` (`metadata_job_id`)").use { it.step() }
    }
}
