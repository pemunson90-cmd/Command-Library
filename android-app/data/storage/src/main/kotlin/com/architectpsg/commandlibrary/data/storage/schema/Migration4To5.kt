package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Additive throttle state only; all schema4 data and generation identities are retained. */
internal object Migration4To5 : Migration(4, 5) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.prepare("""CREATE TABLE `acquisition_origin_cooldown` (
            `origin` TEXT NOT NULL,
            `revision` INTEGER NOT NULL,
            `interval_seconds` INTEGER,
            `interval_nano` INTEGER,
            PRIMARY KEY(`origin`)
        )""").use { it.step() }
    }
}
