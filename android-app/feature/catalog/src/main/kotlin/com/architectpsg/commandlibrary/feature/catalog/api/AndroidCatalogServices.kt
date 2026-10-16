package com.architectpsg.commandlibrary.feature.catalog.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.architectpsg.commandlibrary.core.common.ConsentCoordinator
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.catalog.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Shared catalog acquisition assembly used by the root's lazy EnglishCatalogAccess binding.
 * Database and settings are caller-owned. Closing disables this factory's wire authorizer and
 * closes only the transport it created, not those stores or the exposed offline lookup ports.
 */
public class AndroidCatalogServices private constructor(
    public val downloads: DownloadService,
    public val runner: DownloadRunner,
    public val bulkImport: CatalogBulkImport,
    public val printingLookup: CatalogPrintingLookup,
    public val oracleLookup: CatalogOracleLookup,
    public val rulingsImport: CatalogRulingsImport,
    public val rulings: CatalogRulings,
    private val rulingsAdmission: AndroidRulingsSourceAdmission,
    private val binding: AndroidCatalogVerifiedInputBinding,
    private val transport: NetworkClient,
    private val guard: FactoryWireGuard,
) {
    public fun englishSearchRequest(displayName: String, wifiOnly: Boolean): AcquisitionRequest =
        TrustedEnglishSearchProfile.request(displayName, wifiOnly)

    /** Planning never fetches metadata or grants execution consent. */
    public fun rulingsDiscoveryRequest(displayName: String, wifiOnly: Boolean): AcquisitionRequest =
        TrustedRulingsProfiles.discovery(displayName, wifiOnly)

    /** Pins completed metadata and durably admits its exact file; the file needs its own approval. */
    public suspend fun rulingsFileRequest(metadataJobId: EntityId, displayName: String, wifiOnly: Boolean, cancel: CancelToken): Outcome<AcquisitionRequest> =
        rulingsAdmission.fileRequest(metadataJobId, displayName, wifiOnly, cancel)

    /** Metadata survives this call; its private content URI requires a new trusted pin to read. */
    public suspend fun catalogSource(jobId: EntityId, cancel: CancelToken): Outcome<CatalogBulkImportSource> =
        binding.catalogSource(jobId, cancel)

    public suspend fun close(): Unit {
        if (guard.disable()) withContext(NonCancellable) { transport.close() }
    }

    public companion object {
        /** Pure local capability: no transport creation, receipt rehydration or job recovery. */
        public fun localRulings(database: Database): CatalogRulings = LocalCatalogRulings(database)

        /** Pure installed-printing reader; no transport, acquisition or job recovery. */
        public fun localPrintings(database: Database): CatalogPrintingLookup = LocalCatalogIdentities(database)

        /** Pure installed Oracle reader; no acquisition, transport or projection mutation. */
        public fun localOracles(database: Database): CatalogOracleLookup = LocalCatalogIdentities(database)

        /** Qualified source-reported extras over actual validated local identities; no wire work. */
        public fun localDeckFacts(database: Database): CatalogDeckFactsLookup = LocalCatalogDeckFacts(database)

        /** Source-reported format consensus over validated local READY evidence; no wire or writes. */
        public fun localFormatLegalities(database: Database): CatalogFormatLegalityLookup = LocalCatalogFormatLegality(database)

        /** Pure local bounded preparation. Caller owns database/clock; no transport/job recovery. */
        public fun localCardQuery(database: Database, compiler: CatalogQueryCompiler): LocalCardQuery =
            LocalPreparedCardQuery(database, compiler)

        public fun localSearchPreparation(database: Database, clock: Clock): CatalogSearchPreparation =
            LocalCatalogSearchPreparation(database, clock)

        /** The builder must wire the supplied authorizer to every physical acquisition enqueue.
         * Only the root-owned AppGraph expression can establish that concrete transport behavior.
         */
        public suspend fun create(
            context: Context,
            database: Database,
            settings: SettingsRepository,
            consentCoordinator: ConsentCoordinator,
            clock: Clock,
            ids: IdSource,
            transportBuilder: (suspend (RequestSpec, ResourceUri, CancelToken, () -> Unit) -> Unit) -> NetworkClient,
            bulkLimits: CatalogBulkImportLimits = CatalogBulkImportLimits(),
        ): AndroidCatalogServices {
            val guard = FactoryWireGuard()
            var owned: NetworkClient? = null
            try {
                return withContext(Dispatchers.IO) {
                    val app = context.applicationContext
                    val files = androidArtifactFiles(privateArtifactChild(app))
                    val receipts = RulingsReceiptStore(database)
                    val profiles = TrustedCatalogProfiles(receipts.loadFacts(BULK_NO_CANCEL).bulkValue())
                    val transport = transportBuilder(guard::authorize)
                    owned = transport
                    currentCoroutineContext().ensureActive()
                    guard.checkNoPrematureCall()
                    val engine = LocalAcquisition(database, transport, settings, consentCoordinator, clock, ids, files,
                        profiles::validate, profiles::endpointFor,
                        { currentConnection(app) })
                    val binding = AndroidCatalogVerifiedInputBinding(app, engine)
                    val bulk = LocalBulkCatalog(database, clock, ids, binding, bulkLimits)
                    val identities = LocalCatalogIdentities(database)
                    val rulingsBinding = AndroidRulingsVerifiedInputBinding(app, engine, receipts, profiles)
                    val rulingsImporter = LocalRulingsImport(database, clock, ids, rulingsBinding)
                    val admission = AndroidRulingsSourceAdmission(app, engine, receipts, profiles, clock, ids)
                    val facade = AndroidCatalogServices(engine, engine, bulk, identities, identities, rulingsImporter,
                        LocalCatalogRulings(database), admission, binding, transport, guard)
                    guard.ready(engine)
                    currentCoroutineContext().ensureActive()
                    facade
                }
            } catch (primary: Throwable) {
                guard.disable()
                val transport = owned
                if (transport != null) withContext(NonCancellable) {
                    try { transport.close() }
                    catch (close: Throwable) { if (close !== primary) primary.addSuppressed(close) }
                }
                throw primary
            }
        }
    }
}

/** The final check is synchronous with enqueue, so close cannot invalidate a snapshot between
 * engine authorization and the physical handoff. No guard monitor spans suspended work.
 */
private class FactoryWireGuard {
    private sealed interface State {
        data object Creating : State
        data class Ready(val engine: LocalAcquisition) : State
        data object Failed : State
    }
    private val monitor = Any()
    private var state: State = State.Creating
    private var premature = false

    fun checkNoPrematureCall(): Unit = synchronized(monitor) {
        check(state == State.Creating && !premature) { "Acquisition transport authorizer invoked during creation" }
    }

    fun ready(engine: LocalAcquisition): Unit = synchronized(monitor) {
        check(state == State.Creating && !premature) { "Acquisition transport authorizer invoked during creation" }
        state = State.Ready(engine)
    }

    fun disable(): Boolean = synchronized(monitor) {
        if (state == State.Failed) false else { state = State.Failed; true }
    }

    suspend fun authorize(spec: RequestSpec, uri: ResourceUri, cancel: CancelToken, enqueue: () -> Unit): Unit {
        val engine = synchronized(monitor) {
            when (val current = state) {
                State.Creating -> { premature = true; throw IllegalStateException("Acquisition authorizer called before creation") }
                State.Failed -> throw IllegalStateException("Acquisition transport closed")
                is State.Ready -> current.engine
            }
        }
        engine.authorizeWireDispatch(spec, uri, cancel) {
            synchronized(monitor) {
                check((state as? State.Ready)?.engine === engine) { "Acquisition transport closed" }
                enqueue()
            }
        }
    }
}

/** A fixed final child of the application's private files root. Existing symlinks fail closed. */
private fun privateArtifactChild(context: Context): File {
    val root = context.filesDir.canonicalFile
    if (!root.isDirectory) throw IOException("Private artifact root unavailable")
    val child = File(context.filesDir, "acquisition-artifacts")
    val stat = try { Os.lstat(child.absolutePath) } catch (missing: ErrnoException) {
        if (missing.errno == OsConstants.ENOENT) null else throw missing
    }
    if (stat != null && !OsConstants.S_ISDIR(stat.st_mode)) throw IOException("Private artifact child unavailable")
    if (child.canonicalFile != File(root, "acquisition-artifacts")) throw IOException("Private artifact child changed")
    return child
}

/** API23 network capabilities are advisory admission data; engine rechecks before dispatch. */
private fun currentConnection(context: Context): AcquisitionConnection {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return AcquisitionConnection(false, false)
    val capabilities = try { manager.activeNetwork?.let(manager::getNetworkCapabilities) }
        catch (_: SecurityException) { null }
        ?: return AcquisitionConnection(false, false)
    return AcquisitionConnection(
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
    )
}
