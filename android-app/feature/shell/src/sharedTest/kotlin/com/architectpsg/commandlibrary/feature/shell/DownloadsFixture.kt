package com.architectpsg.commandlibrary.feature.shell

import com.architectpsg.commandlibrary.core.model.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Synthetic UI/controller fixture only: no network, publisher corpus or verified file bytes. */
internal class DownloadsFixture : DownloadService, DownloadRunner, CatalogStatus {
    val rows = ConcurrentHashMap<EntityId, AcquisitionSnapshot>()
    private val signals = ConcurrentHashMap<EntityId, MutableStateFlow<Outcome<AcquisitionSnapshot>>>()
    private val tasks = ConcurrentHashMap<EntityId, Deferred<Outcome<AcquisitionSnapshot>>>()
    private val sequence = AtomicInteger()
    val observers = AtomicInteger()
    val settingsBase = SyntheticSettings()
    private val approvalMutex = Mutex()
    var approvalFailure: Outcome<Nothing>? = null
    var accessFailure: Outcome<Nothing>? = null
    var installFailure: Outcome<Nothing>? = null
    var runGate = CompletableDeferred<Unit>()
    var installGate: CompletableDeferred<Unit>? = null
    var installAfterCommitGate: CompletableDeferred<Unit>? = null
    private val hash = ContentHash("SHA-256", "a".repeat(64))
    var catalog = LocalCatalogStatus(StorageMode.FULL, LanguageTag.ENGLISH, SnapshotList.empty(), Knowledge.Absent("Synthetic"),
        Knowledge.Unknown("Synthetic", SnapshotList.empty()), Knowledge.Unknown("Synthetic", SnapshotList.empty()), SnapshotList.empty(), false)
    val settings = object : SettingsRepository by settingsBase {
        override suspend fun approve(intent: DownloadIntent, cancel: CancelToken): Outcome<ConsentRecord> = approvalMutex.withLock {
            cancel.throwIfCancelled(); approvalFailure?.let { return@withLock it }
            settingsBase.stored.consents.firstOrNull { it.permits(intent) }?.let { return@withLock Outcome.Success(it) }
            val record = ConsentRecord(EntityId("consent-${sequence.incrementAndGet()}"), intent, SYNTHETIC_TIME, null, true)
            settingsBase.stored = settingsBase.stored.copy(revision = settingsBase.stored.revision.next(), consents = SnapshotList.copyOf(settingsBase.stored.consents + record))
            Outcome.Success(record)
        }
    }
    val access = EnglishCatalogAccess { token -> token.throwIfCancelled(); accessFailure ?: Outcome.Success(session) }
    private val session = EnglishCatalogDownloads(this, this, CatalogBulkImportLimits(), ::request) { id, progress, token ->
        progress(JobProgress(id, JobPhase.STAGING, 1, Knowledge.Known(1), "Synthetic progress", null))
        installGate?.await(); token.throwIfCancelled()
        installFailure ?: Outcome.Success(result(id)).also {
            val generation = result(id).installed.generation
            catalog = catalog.copy(initialized = true, activeGenerations = SnapshotList.of(generation),
                source = Knowledge.Known(ProviderId("scryfall")), fetchedAt = Knowledge.Known(SYNTHETIC_TIME), installedLanguages = SnapshotList.of(LanguageTag.ENGLISH))
            installAfterCommitGate?.await()
        }
    }
    private fun request(name: String, wifi: Boolean): AcquisitionRequest {
        val uri = ResourceUri("https://api.scryfall.com/cards/search?q=lang%3Aen&unique=prints")
        val unknown = Knowledge.Unknown("Synthetic source metadata", SnapshotList.empty())
        return AcquisitionRequest(DownloadIntent(EntityId("synthetic-english-intent"), DownloadKind.CATALOG, ProviderId("scryfall"), uri,
            "synthetic", LanguageTag.ENGLISH, null, null, unknown), name, AcquisitionSource.EnglishSearch(uri, "synthetic"),
            AcquisitionLanguageScope.ENGLISH_CARDS, unknown, unknown, unknown,
            AcquisitionLimits("synthetic", 2048, 2048, 2048, 100, 10, 2048, 2048, 1024, 32), wifi)
    }
    override suspend fun plan(request: AcquisitionRequest, cancel: CancelToken): Outcome<AcquisitionSnapshot> {
        cancel.throwIfCancelled()
        val n = sequence.incrementAndGet(); val id = EntityId("job-$n")
        val row = AcquisitionSnapshot(DownloadPlan(id, request, hash, UtcTimestamp(SYNTHETIC_TIME.epochSeconds + n)), Revision(0), 0,
            AcquisitionState.PLANNED, AcquisitionPhase.TRANSFERRING, 0, 0, 0, 0, Knowledge.Unknown("Synthetic", SnapshotList.empty()), null,
            SYNTHETIC_TIME, Knowledge.Unknown("Synthetic", SnapshotList.empty()), null)
        rows[id] = row; signals[id] = MutableStateFlow(Outcome.Success(row)); return Outcome.Success(row)
    }
    suspend fun seed(name: String = "Synthetic English catalog"): AcquisitionSnapshot =
        (plan(request(name, true), NoCancel) as Outcome.Success).value
    fun publish(id: EntityId, change: (AcquisitionSnapshot) -> AcquisitionSnapshot): AcquisitionSnapshot {
        val row = synchronized(rows) {
            val old = rows.getValue(id)
            change(old).copy(revision = old.revision.next()).also { rows[id] = it }
        }
        signals.getValue(id).value = Outcome.Success(row); return row
    }
    fun emitObserved(row: AcquisitionSnapshot) { signals.getValue(row.plan.jobId).value = Outcome.Success(row) }
    override suspend fun status(jobId: EntityId, cancel: CancelToken): Outcome<AcquisitionSnapshot> {
        cancel.throwIfCancelled(); return rows[jobId]?.let { Outcome.Success(it) } ?: Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Synthetic")
    }
    override suspend fun status(cancel: CancelToken): Outcome<LocalCatalogStatus> = Outcome.Success(catalog)
    override suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<AcquisitionSnapshot>> {
        cancel.throwIfCancelled()
        val start = page.cursor?.removePrefix("offset:")?.toInt() ?: 0
        val all = rows.values.sortedByDescending { it.plan.createdAt.epochSeconds }
        val values = all.drop(start).take(page.limit)
        return Outcome.Success(Page(SnapshotList.copyOf(values), if (start + values.size < all.size) "offset:${start + values.size}" else null,
            SnapshotReference(SYNTHETIC_PROVIDER, null, null, LanguageTag.ENGLISH)))
    }
    override fun observe(jobId: EntityId, cancel: CancelToken): Flow<Outcome<AcquisitionSnapshot>> =
        signals.getValue(jobId).onStart { observers.incrementAndGet() }.onCompletion { observers.decrementAndGet() }
    override suspend fun start(jobId: EntityId, expected: Revision, consentId: EntityId, cancel: CancelToken) = queue(jobId, expected, consentId, cancel)
    override suspend fun resume(jobId: EntityId, expected: Revision, consentId: EntityId, cancel: CancelToken) = queue(jobId, expected, consentId, cancel)
    private fun queue(id: EntityId, revision: Revision, consentId: EntityId, cancel: CancelToken): Outcome<AcquisitionSnapshot> {
        cancel.throwIfCancelled(); val row = rows.getValue(id)
        if (row.revision != revision) return failure(FailureCode.STALE_REVISION)
        if (settingsBase.stored.consents.none { it.id == consentId && it.permits(row.plan.request.intent) }) return Outcome.Unavailable(UnavailableReason.CONSENT_REQUIRED, "Synthetic")
        return Outcome.Success(publish(id) { it.copy(state = AcquisitionState.QUEUED, consentId = consentId) })
    }
    override suspend fun pause(jobId: EntityId, expected: Revision, cancel: CancelToken) = stop(jobId, expected, AcquisitionState.PAUSED, cancel)
    override suspend fun cancel(jobId: EntityId, expected: Revision, cancel: CancelToken) = stop(jobId, expected, AcquisitionState.CANCELLED, cancel)
    private suspend fun stop(id: EntityId, revision: Revision, state: AcquisitionState, cancel: CancelToken): Outcome<AcquisitionSnapshot> {
        cancel.throwIfCancelled(); if (rows.getValue(id).revision != revision) return failure(FailureCode.STALE_REVISION)
        tasks[id]?.cancelAndJoin()
        return Outcome.Success(publish(id) { it.copy(state = state) })
    }
    override suspend fun run(jobId: EntityId, cancel: CancelToken): Outcome<AcquisitionSnapshot> = coroutineScope {
        val task = async(start = CoroutineStart.LAZY) {
            publish(jobId) { it.copy(state = AcquisitionState.RUNNING) }
            try {
                runGate.await(); cancel.throwIfCancelled()
                Outcome.Success(publish(jobId) { it.copy(state = AcquisitionState.COMPLETED, phase = AcquisitionPhase.COMPLETE,
                    transferredBytes = 2, durableArtifactBytes = 2, acquiredRecords = 1, acquiredPages = 1, expectedRecords = Knowledge.Known(1),
                    artifact = Knowledge.Known(artifact(jobId))) })
            } catch (cancelled: CancellationException) {
                if (rows[jobId]?.state == AcquisitionState.RUNNING) publish(jobId) { it.copy(state = AcquisitionState.INTERRUPTED) }
                throw cancelled
            }
        }
        tasks[jobId] = task; task.start()
        try { task.await() } finally { tasks.remove(jobId, task) }
    }
    override suspend fun recover(cancel: CancelToken): Outcome<Unit> = Outcome.Success(Unit)
    override fun openArtifact(jobId: EntityId, cancel: CancelToken): Flow<ByteChunk> = error("Synthetic UI fixture has no artifact bytes")
    private fun artifact(id: EntityId): VerifiedArtifact {
        val request = rows.getValue(id).plan.request; val unknown = Knowledge.Unknown("Synthetic", SnapshotList.empty())
        return VerifiedArtifact(id, ArtifactFraming.JSONL, ArtifactCompression.NONE, 2, hash, false, Knowledge.Known(1), 1,
            AcquisitionProvenance(ProviderId("scryfall"), request.intent.resource, request.intent.resource, unknown, unknown, SYNTHETIC_TIME,
                SYNTHETIC_TIME, unknown, unknown, AcquisitionConsistency.NON_ATOMIC_TRAVERSAL, AcquisitionLanguageScope.ENGLISH_CARDS, unknown))
    }
    private fun result(id: EntityId): CatalogBulkImportResult {
        val generationId = GenerationId("synthetic-installed-${id.value}")
        val provenance = Provenance(ProviderId("scryfall"), rows.getValue(id).plan.request.intent.resource, Knowledge.Absent("Synthetic"),
            Knowledge.Absent("Synthetic"), SYNTHETIC_TIME, Knowledge.Absent("Synthetic"), generationId, hash, null)
        val counts = CatalogCounts(1, 1, 0, 0)
        val generation = CatalogGeneration(generationId, provenance, SnapshotList.of(LanguageTag.ENGLISH), "synthetic", counts, GenerationStatus.ACTIVE)
        return CatalogBulkImportResult(CatalogImportResult(generation, counts, provenance,
            CatalogImportVerification(2, hash, ChecksumVerification.COMPUTED_WITHOUT_EXPECTATION, true, 0), SYNTHETIC_TIME),
            2, hash, 1, Knowledge.Known(artifact(id)))
    }
    private object NoCancel : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
}
