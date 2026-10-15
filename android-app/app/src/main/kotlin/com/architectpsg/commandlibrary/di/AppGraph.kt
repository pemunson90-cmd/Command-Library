package com.architectpsg.commandlibrary.di

import android.content.Context
import com.architectpsg.commandlibrary.core.common.ConsentCoordinator
import com.architectpsg.commandlibrary.core.model.CatalogImportLimits
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.data.storage.collection.codec.OwnedLotPayloadCodec
import com.architectpsg.commandlibrary.data.storage.collection.codec.LocalInventoryPatchCodec
import com.architectpsg.commandlibrary.feature.collection.InventoryPatchReducer
import com.architectpsg.commandlibrary.feature.collection.persistence.SqlLocalInventory
import com.architectpsg.commandlibrary.feature.pricing.LocalOwnedQuantityAllocator
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.data.storage.decks.codec.LocalDeckPatchCodec
import com.architectpsg.commandlibrary.data.storage.decks.codec.DeckPayloadCodec
import com.architectpsg.commandlibrary.data.storage.decks.codec.DeckDraftPayloadCodec
import com.architectpsg.commandlibrary.feature.decks.LocalDeckDraftPlanner
import com.architectpsg.commandlibrary.feature.decks.DeckPatchReducer
import com.architectpsg.commandlibrary.feature.decks.DeckSnapshotValidator
import com.architectpsg.commandlibrary.feature.transfer.PlainTextDeckListCodec
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckDrafts
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckEdits
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckVersions
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckLibrary
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.feature.life.persistence.SqlPlayerProfileRepository
import com.architectpsg.commandlibrary.feature.search.LocalCatalogQueryCompiler
import com.architectpsg.commandlibrary.feature.search.LocalCardQueryParser
import com.architectpsg.commandlibrary.feature.links.LocalCardLinks
import com.architectpsg.commandlibrary.feature.catalog.LocalCatalog
import com.architectpsg.commandlibrary.feature.catalog.api.AndroidCatalogServices
import com.architectpsg.commandlibrary.feature.rules.LocalRules
import com.architectpsg.commandlibrary.feature.settings.DataStoreSettingsRepository
import com.architectpsg.commandlibrary.feature.shell.ShellPorts
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide composition root. It creates each real adapter once: one settings repository for
 * its store file, catalog/rules/profiles over one opened database, and import limits shared by the
 * catalog importer and the shell so the disclosed limits are the enforced ones.
 */
internal class AppGraph(
    context: Context,
    private val inspectDatabase: suspend (StorageDatabase) -> Outcome<com.architectpsg.commandlibrary.core.model.DbCapabilities> = { db ->
        db.capabilities(object : com.architectpsg.commandlibrary.core.model.CancelToken {
            override val isCancelled = false
            override fun throwIfCancelled() = Unit
        })
    },
) {
    private val appContext = context.applicationContext
    private val limits = CatalogImportLimits()
    private val consentCoordinator = ConsentCoordinator()
    private val settings = DataStoreSettingsRepository.create(appContext, consentCoordinator)
    private val mutex = Mutex()
    private var ready: ShellPorts? = null

    /** Opens the shared database on first success and keeps the complete ports; a failed open is retried on the next call. */
    suspend fun ports(): Outcome<ShellPorts> = mutex.withLock {
        ready?.let { return@withLock Outcome.Success(it) }
        when (val opened = StorageDatabase.open(appContext, DATABASE_FILE)) {
            is Outcome.Success -> {
                var transferred = false
                try {
                    val capabilities = when (val result = inspectDatabase(opened.value)) {
                        is Outcome.Success -> result.value
                        is Outcome.Failure -> return@withLock result
                        is Outcome.Unavailable -> return@withLock result
                        is Outcome.Unknown -> return@withLock result
                    }
                    val catalog = LocalCatalog(opened.value, SystemUtcClock, RandomUuidIdSource, limits)
                    val rules = LocalRules(opened.value, SystemUtcClock, RandomUuidIdSource)
                    val profiles = SqlPlayerProfileRepository(opened.value, LifePayloadCodec(), SystemUtcClock, RandomUuidIdSource)
                    val deckCodec = DeckPayloadCodec()
                    val decks = SqlLocalDeckLibrary(opened.value, deckCodec, SystemUtcClock, RandomUuidIdSource)
                    val deckVersions = SqlLocalDeckVersions(opened.value, deckCodec, SystemUtcClock, RandomUuidIdSource)
                    val deckPatchCodec = LocalDeckPatchCodec()
                    val deckEdits = SqlLocalDeckEdits(opened.value, deckCodec, deckPatchCodec, SystemUtcClock)
                    val draftPlanner = LocalDeckDraftPlanner()
                    val deckDrafts = SqlLocalDeckDrafts(opened.value, deckCodec, deckPatchCodec, DeckDraftPayloadCodec(), draftPlanner, SystemUtcClock)
                    val inventory = SqlLocalInventory(opened.value, OwnedLotPayloadCodec(), LocalInventoryPatchCodec(), SystemUtcClock, capabilities.maxBoundParameters)
                    val downloads = AppEnglishCatalogAccess(appContext, opened.value, settings, consentCoordinator)
                    val cardQuery = AndroidCatalogServices.localCardQuery(opened.value, LocalCatalogQueryCompiler())
                    val ports = ShellPorts(catalog, catalog, catalog, settings, limits,
                        rulesImport = rules, rulesLookup = rules, playerProfiles = profiles,
                        englishCatalog = downloads, rulingsDownloads = downloads,
                        rulings = AndroidCatalogServices.localRulings(opened.value), catalogTextLookup = catalog, keywordRulesLookup = rules,
                        catalogPrintings = AndroidCatalogServices.localPrintings(opened.value), cardLinks = LocalCardLinks(), localDecks = decks, utcClock = SystemUtcClock, localDeckEdits = deckEdits,
                        localDeckVersions = deckVersions, localDeckDrafts = deckDrafts, deckDraftPlanner = draftPlanner,
                        localCardQuery = cardQuery,
                        offlineDeckPrompts = com.architectpsg.commandlibrary.feature.ai.LocalOfflineDeckPrompts(cardQuery, SystemUtcClock, DeckSnapshotValidator::validate),
                        catalogSearchPreparation = AndroidCatalogServices.localSearchPreparation(opened.value, SystemUtcClock),
                        cardQueryParser = LocalCardQueryParser()::parse,
                        plainDeckLists = PlainTextDeckListCodec(), deckSnapshotValidation = DeckSnapshotValidator::validate,
                        deckPatchProjection = DeckPatchReducer::propose, localInventory = inventory, ownedQuantity = LocalOwnedQuantityAllocator(), inventoryProjection = InventoryPatchReducer::propose)
                    ready = ports
                    transferred = true
                    Outcome.Success(ports)
                } finally {
                    if (!transferred) opened.value.close()
                }
            }
            is Outcome.Failure -> opened
            is Outcome.Unavailable -> opened
            is Outcome.Unknown -> opened
        }
    }

    internal companion object {
        const val DATABASE_FILE: String = "command_library.db"
    }
}
