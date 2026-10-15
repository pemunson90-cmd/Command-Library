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
        InventoryOperationEntity::class,
        InventoryGroupEntity::class,
        InventoryStateEntity::class,
        InventoryLotEntity::class,
        InventoryChangeEntity::class,
        DeckUndoLinkEntity::class,
        DeckUndoStateEntity::class,
        DeckHeadEntity::class,
        DeckRevisionEntity::class,
        DeckOperationEntity::class,
        CatalogGenerationEntity::class,
        CatalogActiveEntity::class,
        OracleCardEntity::class,
        CardFaceEntity::class,
        PrintingEntity::class,
        PrintingOracleMembershipEntity::class,
        PrintingFaceAssetEntity::class,
        CatalogNameFtsEntity::class,
        RulesCorpusEntity::class,
        RulesActiveEntity::class,
        RuleEntryEntity::class,
        RuleReferenceEntity::class,
        RulesImportAttemptEntity::class,
        RulesTextFtsEntity::class,
        AcquisitionJobEntity::class,
        AcquisitionArtifactEntity::class,
        AcquisitionSeenIdEntity::class,
        OriginCooldownEntity::class,
        LifeGameEntity::class,
        LifeEventEntity::class,
        LifeSeatSearchEntity::class,
        LifeResultEntity::class,
        LifeAlertEntity::class,
        LifeProfileEntity::class,
        RulingsGenerationEntity::class,
        RulingRecordEntity::class,
        RulingsActiveEntity::class,
        CatalogSourceReceiptEntity::class,
        CatalogSearchBuildEntity::class,
        CatalogSearchCardFactEntity::class,
        CatalogSearchFaceFactEntity::class,
        CatalogSearchPrintingFactEntity::class,
        CatalogSearchLegalityFactEntity::class,
        CatalogSearchTextFactEntity::class,
        CatalogSearchTextFtsEntity::class,
    ],
    version = StorageSchema.VERSION,
    exportSchema = true,
)
internal abstract class StorageSchemaDatabase : RoomDatabase()

internal object StorageSchema {
    /** Must equal the @Database version above and the highest exported schemas/**/N.json. */
    const val VERSION: Int = 12
}
