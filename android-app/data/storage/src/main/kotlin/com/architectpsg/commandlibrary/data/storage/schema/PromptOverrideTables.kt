package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** Four admitted mode keys; reset rows retain CAS history while discarding the custom body. */
@Entity(tableName = "prompt_override")
internal data class PromptOverrideEntity(
    @PrimaryKey @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "format_version") val format_version: Int,
    @ColumnInfo(name = "template_version") val template_version: Int,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "modified_seconds") val modified_seconds: Long,
    @ColumnInfo(name = "modified_nanos") val modified_nanos: Int,
    @ColumnInfo(name = "body_length") val body_length: Int,
    @ColumnInfo(name = "body_sha256") val body_sha256: String,
    @ColumnInfo(name = "body") val body: ByteArray?,
)
