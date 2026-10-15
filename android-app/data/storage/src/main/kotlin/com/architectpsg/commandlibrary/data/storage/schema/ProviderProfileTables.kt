package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Nonsecret provider configuration; deleted rows permanently reserve identity and cursor epoch. */
@Entity(tableName = "provider_profile", indices = [
    Index(value = ["change_sequence"], name = "index_provider_profile_change_sequence", unique = true),
])
internal data class ProviderProfileEntity(
    @PrimaryKey @ColumnInfo(name = "profile_id") val profile_id: String,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "change_sequence") val change_sequence: Long,
    @ColumnInfo(name = "deleted") val deleted: Boolean,
    @ColumnInfo(name = "created_seconds") val created_seconds: Long,
    @ColumnInfo(name = "created_nanos") val created_nanos: Int,
    @ColumnInfo(name = "modified_seconds") val modified_seconds: Long,
    @ColumnInfo(name = "modified_nanos") val modified_nanos: Int,
    @ColumnInfo(name = "payload_version") val payload_version: Int,
    @ColumnInfo(name = "payload_length") val payload_length: Int,
    @ColumnInfo(name = "payload_sha256") val payload_sha256: String,
    @ColumnInfo(name = "payload") val payload: ByteArray?,
)
