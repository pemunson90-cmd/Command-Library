package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Additive versioned prompt overrides, without rewriting existing user/provider state. */
internal object Migration16To17 : Migration(16, 17) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.prepare("""CREATE TABLE IF NOT EXISTS `prompt_override` (`mode` TEXT NOT NULL, `format_version` INTEGER NOT NULL, `template_version` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `modified_seconds` INTEGER NOT NULL, `modified_nanos` INTEGER NOT NULL, `body_length` INTEGER NOT NULL, `body_sha256` TEXT NOT NULL, `body` BLOB, PRIMARY KEY(`mode`))""").use { it.step() }
    }
}
