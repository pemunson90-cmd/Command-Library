package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.common.CoroutineCancelToken
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.EnglishCatalogAccess
import com.architectpsg.commandlibrary.feature.shell.EnglishCatalogDownloads
import com.architectpsg.commandlibrary.feature.shell.RulingsCatalogAccess
import com.architectpsg.commandlibrary.feature.shell.RulingsCatalogDownloads
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal val ResumableDownloads = setOf(AcquisitionState.PAUSED, AcquisitionState.WAITING_NETWORK, AcquisitionState.WAITING_WIFI,
    AcquisitionState.WAITING_CONSENT, AcquisitionState.INTERRUPTED, AcquisitionState.FAILED)
internal data class DownloadsUiState(
    val ready: Boolean = false, val loading: Boolean = false, val planning: Boolean = false,
    val wifiOnly: Boolean = true, val items: SnapshotList<AcquisitionSnapshot> = SnapshotList.empty(), val nextCursor: String? = null,
    val selectedId: EntityId? = null, val selected: AcquisitionSnapshot? = null, val detailLoading: Boolean = false,
    val approval: AcquisitionSnapshot? = null, val pendingCancel: EntityId? = null,
    val controls: Set<EntityId> = emptySet(), val running: Set<EntityId> = emptySet(), val installing: Set<EntityId> = emptySet(),
    val queuedInstalls: Set<EntityId> = emptySet(), val installProgress: Map<EntityId, JobProgress> = emptyMap(), val installed: CatalogBulkImportResult? = null,
    val catalog: LocalCatalogStatus? = null, val problem: Problem? = null,
    val rulings: RulingsStatus? = null, val rulingsInstalled: RulingsImportResult? = null,
)

/** Display routing only; trusted source/consent admission remains with the actual root ports. */
internal enum class CatalogDownloadStage { ENGLISH, RULINGS_METADATA, RULINGS_FILE }
internal fun catalogDownloadStage(row: AcquisitionSnapshot): CatalogDownloadStage? = row.plan.request.let { r ->
    if(r.intent.source != ProviderId("scryfall") || r.intent.kind != DownloadKind.CATALOG) return@let null
    when(val source = r.source) {
        is AcquisitionSource.EnglishSearch -> if(r.languageScope == AcquisitionLanguageScope.ENGLISH_CARDS && r.intent.language == LanguageTag.ENGLISH) CatalogDownloadStage.ENGLISH else null
        is AcquisitionSource.RemoteFile -> if(r.languageScope != AcquisitionLanguageScope.RULINGS_LANGUAGE_NOT_ASSERTED || r.intent.language != null) null
            else when { source.framing == ArtifactFraming.TEXT && source.compression == ArtifactCompression.NONE -> CatalogDownloadStage.RULINGS_METADATA
                source.framing == ArtifactFraming.JSONL && source.compression == ArtifactCompression.GZIP -> CatalogDownloadStage.RULINGS_FILE
                else -> null }
    }
}

/** One observed detail, bounded real pages, and independently controlled structured workers. */
internal class DownloadsController(
    private val scope: CoroutineScope,
    private val access: EnglishCatalogAccess,
    private val settings: SettingsRepository,
    private val catalogStatus: CatalogStatus,
    private val dispatcher: CoroutineDispatcher,
    private val refreshCatalog: () -> Unit,
    private val rulingsAccess: RulingsCatalogAccess? = null,
    private val rulingsPort: CatalogRulings? = null,
) {
    val rulingsEnabled: Boolean = rulingsAccess != null && rulingsPort != null
    private val mutableState = MutableStateFlow(DownloadsUiState())
    val state: StateFlow<DownloadsUiState> = mutableState.asStateFlow()
    private var session: EnglishCatalogDownloads? = null
    private var loadJob: Job? = null
    private var detailJob: Job? = null
    @Volatile private var detailTicket = 0L
    private var catalogTicket = 0L
    private val workers = mutableMapOf<EntityId, Job>()
    private val installers = mutableMapOf<EntityId, Job>()
    private val stopping = mutableSetOf<EntityId>()

    private suspend fun <T : Any> port(action: suspend (CancelToken) -> Outcome<T>): T? = when (val result = callPort(dispatcher, action)) {
        is PortResult.Ok -> result.value
        is PortResult.Failed -> { problem(result.problem); null }
    }
    private fun problem(value: Problem) = mutableState.update { it.copy(problem = value) }
    fun dismissProblem() = mutableState.update { it.copy(problem = null, installed = null, rulingsInstalled = null) }
    fun changeWifiOnly(value: Boolean) {
        if (!state.value.planning && state.value.approval == null) mutableState.update { it.copy(wifiOnly = value) }
    }
    fun load() = loadPage(null)
    fun loadMore() { if (!state.value.loading) state.value.nextCursor?.let(::loadPage) }
    private fun loadPage(cursor: String?) {
        if (loadJob?.isActive == true) return
        mutableState.update { it.copy(loading = true, problem = null) }
        loadJob = scope.launch {
            try {
                val current = session ?: port { access.open(it) }?.also { session = it } ?: return@launch
                mutableState.update { it.copy(ready = true) }
                val page = port { current.jobs.list(PageRequest(20, cursor), it) } ?: return@launch
                mutableState.update { old ->
                    val rows = linkedMapOf<EntityId, AcquisitionSnapshot>()
                    if (cursor != null) old.items.forEach { rows[it.plan.jobId] = it }
                    page.items.filter(::supported).forEach { value ->
                        val latest = old.items.find { it.plan.jobId == value.plan.jobId }
                        rows[value.plan.jobId] = if (latest != null && latest.revision.value > value.revision.value) latest else value
                    }
                    old.copy(items = SnapshotList.copyOf(rows.values), nextCursor = page.nextCursor)
                }
                readCatalog()
            } finally { mutableState.update { it.copy(loading = false) } }
        }
    }
    private suspend fun readCatalog() {
        val ticket = ++catalogTicket
        mutableState.update { it.copy(catalog = null, rulings = null) }
        val latest = port { catalogStatus.status(it) }
        if (ticket == catalogTicket) mutableState.update { it.copy(catalog = latest) }
        if(rulingsEnabled) {
            val rulingStatus = port { requireNotNull(rulingsPort).status(it) }
            if(ticket == catalogTicket) mutableState.update { it.copy(rulings = rulingStatus) }
        }
    }
    private fun supported(row: AcquisitionSnapshot): Boolean = when(catalogDownloadStage(row)) {
        CatalogDownloadStage.ENGLISH -> true
        CatalogDownloadStage.RULINGS_METADATA, CatalogDownloadStage.RULINGS_FILE -> rulingsEnabled
        null -> false
    }
    private fun merge(row: AcquisitionSnapshot, insert: Boolean = false) {
        if (!supported(row)) return
        mutableState.update { old ->
            val id = row.plan.jobId
            val rows = old.items.toMutableList()
            val index = rows.indexOfFirst { it.plan.jobId == id }
            if (index >= 0 && rows[index].revision.value <= row.revision.value) rows[index] = row
            else if (index < 0 && insert) rows.add(0, row)
            val selected = if (old.selectedId == id && (old.selected == null || old.selected.revision.value <= row.revision.value)) row else old.selected
            old.copy(items = SnapshotList.copyOf(rows), selected = selected)
        }
    }
    fun select(id: EntityId) {
        val current = session ?: return
        detailJob?.cancel(); val ticket = ++detailTicket
        mutableState.update { it.copy(selectedId = id, selected = null, detailLoading = true, problem = null) }
        detailJob = scope.launch {
            try {
                val row = port { current.jobs.status(id, it) } ?: return@launch
                if (!supported(row)) { problem(Problem.SOURCE_MISMATCH); return@launch }
                if (ticket != detailTicket) return@launch
                merge(row); mutableState.update { it.copy(detailLoading = false) }
                withContext(dispatcher) {
                    val token = CoroutineCancelToken(currentCoroutineContext())
                    current.jobs.observe(id, token).collect { result ->
                        token.throwIfCancelled()
                        if (ticket != detailTicket) return@collect
                        when (result) {
                            is Outcome.Success -> merge(result.value)
                            is Outcome.Failure -> problem(result.error.code.toProblem())
                            is Outcome.Unavailable -> problem(result.reason.toProblem())
                            is Outcome.Unknown -> problem(Problem.UNKNOWN_RESULT)
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (ticket == detailTicket) problem(Problem.UNEXPECTED) }
            finally { if (ticket == detailTicket) mutableState.update { it.copy(detailLoading = false) } }
        }
    }
    fun closeDetail() {
        detailJob?.cancel(); ++detailTicket
        mutableState.update { it.copy(selectedId = null, selected = null, detailLoading = false) }
    }
    fun plan(displayName: String) {
        val current = session ?: return
        val wifi = state.value.wifiOnly
        planRequest { Outcome.Success(current.request(displayName, wifi)) }
    }
    fun planRulings(displayName: String) {
        if(!rulingsEnabled) return
        val wifi = state.value.wifiOnly
        planRequest { token -> when(val current = rulingSession(token)) {
            is Outcome.Success -> Outcome.Success(current.value.discoveryRequest(displayName, wifi))
            is Outcome.Failure -> current
            is Outcome.Unavailable -> current
            is Outcome.Unknown -> current
        } }
    }
    fun planRulingsFile(metadataJobId: EntityId, displayName: String) {
        if(!rulingsEnabled) return
        val wifi = state.value.wifiOnly
        planRequest { token -> when(val current = rulingSession(token)) {
            is Outcome.Success -> current.value.fileRequest(metadataJobId, displayName, wifi, token)
            is Outcome.Failure -> current
            is Outcome.Unavailable -> current
            is Outcome.Unknown -> current
        } }
    }
    private suspend fun rulingSession(cancel: CancelToken): Outcome<RulingsCatalogDownloads> = when(val result = requireNotNull(rulingsAccess).openRulings(cancel)) {
        is Outcome.Success -> if(result.value.jobs === session?.jobs && result.value.runner === session?.runner) result
            else Outcome.Failure(DomainFailure(FailureCode.SOURCE_MISMATCH, "Catalog download owners differ", SnapshotList.empty()))
        is Outcome.Failure -> result
        is Outcome.Unavailable -> result
        is Outcome.Unknown -> result
    }
    private fun planRequest(build: suspend (CancelToken) -> Outcome<AcquisitionRequest>) {
        val current = session ?: return
        if (state.value.planning || state.value.approval != null) return
        mutableState.update { it.copy(planning = true, problem = null, installed = null, rulingsInstalled = null) }
        scope.launch {
            try {
                val request = port(build) ?: return@launch
                val row = port { token -> current.jobs.plan(request, token) } ?: return@launch
                if (!supported(row) || row.plan.request != request) { problem(Problem.SOURCE_MISMATCH); return@launch }
                merge(row, true); mutableState.update { it.copy(approval = row) }
            } finally { mutableState.update { it.copy(planning = false) } }
        }
    }
    fun prepare(id: EntityId) = control(id) { _, row ->
        if (row.state != AcquisitionState.PLANNED && row.state !in ResumableDownloads) { problem(Problem.INVALID_INPUT); return@control }
        mutableState.update { it.copy(approval = row) }
    }
    /** Dismissing a modal never rolls back an explicitly confirmed in-flight approval/start. */
    fun dismissApproval() = mutableState.update { it.copy(approval = null) }
    fun confirmApproval() {
        val shown = state.value.approval ?: return
        control(shown.plan.jobId) { current, actual ->
            if (actual.plan != shown.plan || actual.revision != shown.revision) { problem(Problem.STALE_REVISION); return@control }
            if (actual.state != AcquisitionState.PLANNED && actual.state !in ResumableDownloads) { problem(Problem.INVALID_INPUT); return@control }
            val consent = port { settings.approve(actual.plan.request.intent, it) } ?: return@control
            val queued = port { token ->
                if (actual.state == AcquisitionState.PLANNED) current.jobs.start(actual.plan.jobId, actual.revision, consent.id, token)
                else current.jobs.resume(actual.plan.jobId, actual.revision, consent.id, token)
            } ?: return@control
            merge(queued, true)
            mutableState.update { if (it.approval?.plan?.jobId == actual.plan.jobId) it.copy(approval = null) else it }
            select(actual.plan.jobId); run(actual.plan.jobId)
        }
    }
    private fun control(id: EntityId, action: suspend (EnglishCatalogDownloads, AcquisitionSnapshot) -> Unit) {
        val current = session ?: return
        if (id in state.value.controls || id in state.value.installing) return
        mutableState.update { it.copy(controls = it.controls + id, problem = null) }
        scope.launch {
            try {
                val row = port { current.jobs.status(id, it) } ?: return@launch
                if (!supported(row)) { problem(Problem.SOURCE_MISMATCH); return@launch }
                merge(row); action(current, row)
            } finally { mutableState.update { it.copy(controls = it.controls - id) } }
        }
    }
    fun pause(id: EntityId) = stop(id, false)
    fun requestCancel(id: EntityId) { if (id !in state.value.installing) mutableState.update { it.copy(pendingCancel = id) } }
    fun dismissCancel() = mutableState.update { it.copy(pendingCancel = null) }
    fun confirmCancel() { val id = state.value.pendingCancel ?: return; stop(id, true) }
    private fun stop(id: EntityId, cancel: Boolean) = control(id) { current, row ->
        stopping += id
        try {
            val stopped = port { token -> if (cancel) current.jobs.cancel(id, row.revision, token) else current.jobs.pause(id, row.revision, token) } ?: return@control
            merge(stopped)
            if (cancel) mutableState.update { if (it.pendingCancel == id) it.copy(pendingCancel = null) else it }
        } finally { stopping -= id }
    }
    private fun run(id: EntityId) {
        val current = session ?: return
        if (workers[id]?.isActive == true) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                when (val result = callPort(dispatcher) { current.runner.run(id, it) }) {
                    is PortResult.Ok -> { merge(result.value); if (result.value.state == AcquisitionState.COMPLETED && catalogDownloadStage(result.value) != CatalogDownloadStage.RULINGS_METADATA) beginInstall(id, true) }
                    is PortResult.Failed -> {
                        // The engine's deliberately cancelled worker may surface interruption to its still-live caller.
                        if (result.problem == Problem.UNEXPECTED) {
                            when (val latest = callPort(dispatcher) { current.jobs.status(id, it) }) {
                                is PortResult.Ok -> {
                                    merge(latest.value)
                                    val expectedStop = latest.value.state in setOf(AcquisitionState.PAUSED, AcquisitionState.CANCELLED) ||
                                        (id in stopping && latest.value.state == AcquisitionState.INTERRUPTED)
                                    if (!expectedStop) problem(result.problem)
                                }
                                is PortResult.Failed -> problem(latest.problem)
                            }
                        } else problem(result.problem)
                    }
                }
            } finally { workers.remove(id); mutableState.update { it.copy(running = it.running - id) } }
        }
        workers[id] = job; mutableState.update { it.copy(running = it.running + id) }; job.start()
    }
    fun install(id: EntityId) = beginInstall(id, false)
    private fun beginInstall(id: EntityId, automatic: Boolean) {
        val current = session ?: return
        if (id in state.value.installing || (!automatic && id in state.value.controls)) return
        if (state.value.installing.isNotEmpty()) {
            if (automatic) mutableState.update { it.copy(queuedInstalls = it.queuedInstalls + id) }
            return
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val row = port { current.jobs.status(id, it) } ?: return@launch
                val stage = catalogDownloadStage(row)
                if(!supported(row) || stage == CatalogDownloadStage.RULINGS_METADATA || row.state != AcquisitionState.COMPLETED) {
                    problem(Problem.INVALID_INPUT); return@launch
                }
                val progress: (JobProgress) -> Unit = { progress ->
                    mutableState.update { if (id in it.installing) it.copy(installProgress = it.installProgress + (id to progress)) else it }
                }
                if(stage == CatalogDownloadStage.ENGLISH) {
                    val installed = port { current.install(id, progress, it) }
                    if(installed != null) { mutableState.update { it.copy(installed = installed) }; refreshCatalog() }
                } else {
                    val ruling = port { rulingSession(it) } ?: return@launch
                    val installed = port { ruling.install(id, progress, it) }
                    if(installed != null) { mutableState.update { it.copy(rulingsInstalled = installed) }; refreshCatalog() }
                }
                readCatalog()
            } finally {
                installers.remove(id)
                mutableState.update { it.copy(installing = it.installing - id, installProgress = it.installProgress - id) }
                if (scope.isActive) state.value.queuedInstalls.firstOrNull()?.let { next ->
                    mutableState.update { it.copy(queuedInstalls = it.queuedInstalls - next) }
                    beginInstall(next, true)
                }
            }
        }
        installers[id] = job
        mutableState.update { it.copy(installing = it.installing + id, problem = null, installed = null, rulingsInstalled = null) }; job.start()
    }
    fun stopInstall(id: EntityId) {
        if (id in state.value.queuedInstalls) {
            mutableState.update { it.copy(queuedInstalls = it.queuedInstalls - id) }; return
        }
        val job = installers[id] ?: return
        scope.launch {
            job.cancelAndJoin()
            // Re-read durable truth after cancellation; this is not an installation-success claim.
            refreshCatalog(); readCatalog()
        }
    }
}
