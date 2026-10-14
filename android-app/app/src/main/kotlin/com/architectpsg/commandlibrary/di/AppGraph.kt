package com.architectpsg.commandlibrary.di

import android.content.Context
import com.architectpsg.commandlibrary.core.common.ConsentCoordinator
import com.architectpsg.commandlibrary.core.model.CatalogImportLimits
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.feature.life.persistence.SqlPlayerProfileRepository
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
internal class AppGraph(context: Context) {
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
                val catalog = LocalCatalog(opened.value, SystemUtcClock, RandomUuidIdSource, limits)
                val rules = LocalRules(opened.value, SystemUtcClock, RandomUuidIdSource)
                val profiles = SqlPlayerProfileRepository(opened.value, LifePayloadCodec(), SystemUtcClock, RandomUuidIdSource)
                val downloads = AppEnglishCatalogAccess(appContext, opened.value, settings, consentCoordinator)
                val ports = ShellPorts(catalog, catalog, catalog, settings, limits,
                    rulesImport = rules, rulesLookup = rules, playerProfiles = profiles,
                    englishCatalog = downloads, rulingsDownloads = downloads,
                    rulings = AndroidCatalogServices.localRulings(opened.value))
                ready = ports
                Outcome.Success(ports)
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
