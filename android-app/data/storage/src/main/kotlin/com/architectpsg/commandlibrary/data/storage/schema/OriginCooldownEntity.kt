package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** Origin-wide throttle survives jobs. Zero interval is a generation-preserving tombstone. */
@Entity(tableName = "acquisition_origin_cooldown")
internal data class OriginCooldownEntity(
    @PrimaryKey @ColumnInfo(name = "origin") val origin: String,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "interval_seconds") val intervalSeconds: Long?,
    @ColumnInfo(name = "interval_nano") val intervalNano: Int?,
)
