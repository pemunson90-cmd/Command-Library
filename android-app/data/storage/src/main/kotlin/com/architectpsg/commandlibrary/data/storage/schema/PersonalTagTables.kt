package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** Singleton ordered vocabulary; retired IDs stay in its checksummed versioned payload. */
@Entity(tableName = "personal_tag_pool")
internal data class PersonalTagPoolEntity(
    @PrimaryKey @ColumnInfo(name = "singleton_id") val singleton_id: Int,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "modified_seconds") val modified_seconds: Long,
    @ColumnInfo(name = "modified_nanos") val modified_nanos: Int,
    @ColumnInfo(name = "payload_version") val payload_version: Int,
    @ColumnInfo(name = "payload_length") val payload_length: Int,
    @ColumnInfo(name = "payload_sha256") val payload_sha256: String,
    @ColumnInfo(name = "payload") val payload: ByteArray,
)
