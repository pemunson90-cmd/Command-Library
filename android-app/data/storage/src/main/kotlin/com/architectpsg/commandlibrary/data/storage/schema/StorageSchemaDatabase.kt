package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * Room3 owner of the physical schema. It declares no DAOs on purpose: every statement runs through
 * the core Database port as named-parameter SQL, so there is exactly one execution path to audit.
 * The destructive-migration fallbacks are never enabled for this class.
 */
@Database(
    entities = [
        CatalogGenerationEntity::class,
        CatalogActiveEntity::class,
        OracleCardEntity::class,
        CardFaceEntity::class,
        PrintingEntity::class,
        PrintingFaceAssetEntity::class,
        CatalogNameFtsEntity::class,
    ],
    version = StorageSchema.VERSION,
    exportSchema = true,
)
internal abstract class StorageSchemaDatabase : RoomDatabase()

internal object StorageSchema {
    /** Must equal the @Database version above and the highest exported schemas/**/N.json. */
    const val VERSION: Int = 1
}
