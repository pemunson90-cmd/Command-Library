package com.architectpsg.commandlibrary.di

import android.content.Context
import com.architectpsg.commandlibrary.core.common.ConsentCoordinator
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.network.OkHttpNetworkClient
import com.architectpsg.commandlibrary.data.network.WireDispatchGuard
import com.architectpsg.commandlibrary.feature.catalog.api.AndroidCatalogServices
import com.architectpsg.commandlibrary.feature.shell.EnglishCatalogAccess
import com.architectpsg.commandlibrary.feature.shell.EnglishCatalogDownloads
import com.architectpsg.commandlibrary.feature.shell.RulingsCatalogAccess
import com.architectpsg.commandlibrary.feature.shell.RulingsCatalogDownloads
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One lazy process owner for both source families, independent of AppGraph's offline cache. */
internal class AppEnglishCatalogAccess(
    context: Context,
    private val database: Database,
    private val settings: SettingsRepository,
    private val consent: ConsentCoordinator,
    private val limits: CatalogBulkImportLimits = CatalogBulkImportLimits(),
) : EnglishCatalogAccess, RulingsCatalogAccess {
    private val app = context.applicationContext
    private val mutex = Mutex()
    private data class Ready(val services: AndroidCatalogServices, val english: EnglishCatalogDownloads, val rulings: RulingsCatalogDownloads)
    private var ready: Ready? = null
    private var closed = false

    override suspend fun open(cancel: CancelToken): Outcome<EnglishCatalogDownloads> = when (val result = acquire(cancel)) {
        is Outcome.Success -> Outcome.Success(result.value.english)
        is Outcome.Failure -> result
        is Outcome.Unavailable -> result
        is Outcome.Unknown -> result
    }

    override suspend fun openRulings(cancel: CancelToken): Outcome<RulingsCatalogDownloads> = when (val result = acquire(cancel)) {
        is Outcome.Success -> Outcome.Success(result.value.rulings)
        is Outcome.Failure -> result
        is Outcome.Unavailable -> result
        is Outcome.Unknown -> result
    }

    private suspend fun acquire(cancel: CancelToken): Outcome<Ready> = mutex.withLock {
        cancel.throwIfCancelled(); currentCoroutineContext().ensureActive()
        if (closed) return@withLock Outcome.Unavailable(UnavailableReason.SOURCE_UNAVAILABLE, "Download access has been closed")
        ready?.let { return@withLock Outcome.Success(it) }
        var creating: AndroidCatalogServices? = null
        try {
            val services = AndroidCatalogServices.create(app, database, settings, consent, SystemUtcClock, RandomUuidIdSource,
                transportBuilder = { authorize ->
                    OkHttpNetworkClient(SystemUtcClock,
                        dispatchGuard = WireDispatchGuard { request, destination, token, enqueue -> authorize(request, destination, token, enqueue) },
                        exposeAcquisition429Metadata = true)
                }, bulkLimits = limits)
            creating = services
            // Never recover an already exposed session while this process may have active workers.
            when (val recovered = services.runner.recover(cancel)) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> return@withLock recovered
                is Outcome.Unavailable -> return@withLock recovered
                is Outcome.Unknown -> return@withLock recovered
            }
            val english = EnglishCatalogDownloads(services.downloads, services.runner, limits, services::englishSearchRequest) { id, progress, token ->
                when (val source = services.catalogSource(id, token)) {
                    is Outcome.Success -> {
                        if (source.value.inputKind != SourceInputKind.VERIFIED_ARTIFACT) rejectedSource()
                        // VERIFIED_ARTIFACT reads actual bytes through this factory's pinned binding.
                        else services.bulkImport.`import`(source.value, emptyFlow(), progress, token)
                    }
                    is Outcome.Failure -> source
                    is Outcome.Unavailable -> source
                    is Outcome.Unknown -> source
                }
            }
            val rulings = RulingsCatalogDownloads(services.downloads, services.runner, services::rulingsDiscoveryRequest,
                services::rulingsFileRequest, services.rulingsImport::`import`)
            val session = Ready(services, english, rulings)
            cancel.throwIfCancelled(); currentCoroutineContext().ensureActive()
            ready = session; creating = null
            Outcome.Success(session)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
            val code = when (failure) { is SecurityException -> FailureCode.PERMISSION; is IOException -> FailureCode.PERSISTENCE; else -> FailureCode.INTERNAL }
            Outcome.Failure(DomainFailure(code, "Catalog download access could not be initialized", SnapshotList.empty()))
        } finally {
            creating?.close()
        }
    }

    /** Disposes this owner's transport only; never closes shared storage/settings or grants consent. */
    suspend fun close() = mutex.withLock {
        closed = true
        val previous = ready; ready = null
        previous?.services?.close()
    }

    private fun rejectedSource(): Outcome<Nothing> = Outcome.Failure(DomainFailure(
        FailureCode.SOURCE_MISMATCH, "Catalog installation requires this session's verified artifact", SnapshotList.empty()))
}
