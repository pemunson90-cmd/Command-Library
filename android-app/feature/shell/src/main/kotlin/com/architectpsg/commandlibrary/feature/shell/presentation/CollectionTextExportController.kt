package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.common.CoroutineCancelToken
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.document.DocumentRef
import com.architectpsg.commandlibrary.feature.shell.document.PlainDeckWriteResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class CollectionTextExportPreview(
    val source: InventorySnapshot, val artifact: ByteArtifact,
    val includeNotes: Boolean, val includeLocations: Boolean,
)
internal data class CollectionTextExportState(
    val visible: Boolean = false,
    val includeNotes: Boolean = false,
    val includeLocations: Boolean = false,
    val preparing: Boolean = false,
    val preview: CollectionTextExportPreview? = null,
    val problem: Problem? = null,
    val pendingPicker: String? = null,
    val pickerAuthorized: Boolean = false,
    val writePhase: PlainDeckExportWritePhase = PlainDeckExportWritePhase.IDLE,
    val lastWrite: PlainDeckWriteResult? = null,
)

/** Frozen text preview and once-only explicitly selected destination; no inventory mutation. */
internal class CollectionTextExportController(
    private val scope: CoroutineScope,
    private val inventory: LocalInventory,
    private val render: (InventorySnapshot, Boolean, Boolean, CancelToken) -> Outcome<ByteArtifact>,
    private val write: suspend (DocumentRef, SnapshotBytes, CancelToken) -> PlainDeckWriteResult,
    private val dispatcher: CoroutineDispatcher,
    private val pickerOwnerNonce: String,
    private val inventoryReady: () -> Boolean = { true },
) {
    init { require(pickerOwnerNonce.isNotBlank() && pickerOwnerNonce.length <= 256) }
    private val mutable = MutableStateFlow(CollectionTextExportState())
    val state: StateFlow<CollectionTextExportState> = mutable.asStateFlow()
    private var preparation: Any? = null
    private var preparationJob: Job? = null
    private var pickerSerial = 0L
    private val destinations = HashSet<String>()
    private class WriteOwner(val preview: CollectionTextExportPreview, val destination: DocumentRef) { var job: Job? = null }
    private var owner: WriteOwner? = null
    fun rendered(frame: CollectionTextExportState, action: () -> Unit) { if (mutable.value === frame && frame.visible) action() }
    fun open() { mutable.value = mutable.value.copy(visible = true) }
    private fun editable(): Boolean = state.value.visible && !state.value.preparing && state.value.preview == null &&
        state.value.pendingPicker == null && owner == null && state.value.lastWrite == null
    fun notes(value: Boolean) { if (editable()) mutable.value = state.value.copy(includeNotes = value) }
    fun locations(value: Boolean) { if (editable()) mutable.value = state.value.copy(includeLocations = value) }
    fun prepare() {
        if (!editable() || !inventoryReady()) return
        val s = state.value
        val ticket = Any(); preparation = ticket
        mutable.value = s.copy(preparing = true, problem = null)
        preparationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = callPort(dispatcher) { cancel -> prepared(s.includeNotes, s.includeLocations, cancel) }
                currentCoroutineContext().ensureActive()
                if (preparation !== ticket) return@launch
                mutable.value = when (result) {
                    is PortResult.Ok -> mutable.value.copy(preview = result.value, problem = null)
                    is PortResult.Failed -> mutable.value.copy(problem = result.problem)
                }
            } finally {
                if (preparation === ticket) { preparation = null; mutable.value = mutable.value.copy(preparing = false) }
            }
        }
    }
    private suspend fun prepared(notes: Boolean, locations: Boolean, cancel: CancelToken): Outcome<CollectionTextExportPreview> {
        if (!inventoryReady()) return blocked()
        val source = when (val r = inventory.snapshot(cancel)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return r
            is Outcome.Unavailable -> return r
            is Outcome.Unknown -> return r
        }
        cancel.throwIfCancelled()
        if (!inventoryReady()) return blocked()
        if (!source.complete || source.revision.value < 0) return invalid()
        val artifact = when (val r = render(source, notes, locations, cancel)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return r
            is Outcome.Unavailable -> return r
            is Outcome.Unknown -> return r
        }
        cancel.throwIfCancelled()
        if (artifact.bytes.size !in 1..PlainDeckListLimits.MAX_BYTES || artifact.manifest != null ||
            artifact.losses.size > 64 || artifact.losses.any { it.length > 4096 } ||
            artifact.fileName != "command-library-collection-r${source.revision.value}.txt" ||
            artifact.mediaType != "text/plain;charset=utf-8") return invalid()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(artifact.bytes.copyBytes())
            .joinToString("") { "%02x".format(java.util.Locale.ROOT, it.toInt() and 0xff) }
        cancel.throwIfCancelled()
        if (artifact.checksum != ContentHash("SHA-256", digest)) return invalid()
        if (!inventoryReady()) return blocked()
        return Outcome.Success(CollectionTextExportPreview(source, artifact, notes, locations))
    }
    private fun blocked(): Outcome.Failure = Outcome.Failure(DomainFailure(FailureCode.STALE_REVISION,
        "Reconcile pending inventory changes before exporting", SnapshotList.empty()))
    private fun invalid(): Outcome.Failure = Outcome.Failure(DomainFailure(FailureCode.CORRUPT_DATA,
        "Invalid collection text export", SnapshotList.empty()))
    fun requestDestination(expected: CollectionTextExportPreview): String? {
        val s = state.value
        if (!s.visible || s.preview !== expected || s.preparing || s.pendingPicker != null || owner != null || !inventoryReady()) return null
        if (destinations.size >= PlainDeckExportLimits.MAX_DESTINATION_ATTEMPTS || pickerSerial == Long.MAX_VALUE) {
            mutable.value = s.copy(problem = Problem.INVALID_INPUT); return null
        }
        val ticket = "$pickerOwnerNonce:${++pickerSerial}"
        mutable.value = s.copy(pendingPicker = ticket, pickerAuthorized = true, problem = null)
        return ticket
    }
    fun pickerResult(ticket: String, destination: DocumentRef?) {
        val s = state.value
        if (s.pendingPicker != ticket) return
        mutable.value = s.copy(pendingPicker = null, pickerAuthorized = false)
        if (!s.pickerAuthorized || !s.visible || destination == null) return
        val preview = s.preview ?: return
        if (!inventoryReady()) { mutable.value = mutable.value.copy(problem = Problem.STALE_REVISION); return }
        if (destination.uri.length > PlainDeckExportLimits.MAX_URI_CODE_UNITS ||
            destinations.size >= PlainDeckExportLimits.MAX_DESTINATION_ATTEMPTS || !destinations.add(destination.uri)) {
            mutable.value = mutable.value.copy(problem = Problem.INVALID_INPUT); return
        }
        val active = WriteOwner(preview, destination); owner = active
        mutable.value = mutable.value.copy(writePhase = PlainDeckExportWritePhase.WRITING, problem = null)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var result: PlainDeckWriteResult = PlainDeckWriteResult.PossiblyModified(Problem.UNKNOWN_RESULT)
            try {
                active.job = currentCoroutineContext().job
                currentCoroutineContext().ensureActive()
                result = try {
                    val reported = withContext(dispatcher) {
                        write(active.destination, active.preview.artifact.bytes, CoroutineCancelToken(currentCoroutineContext()))
                    }
                    currentCoroutineContext().ensureActive(); reported
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    currentCoroutineContext().ensureActive(); PlainDeckWriteResult.PossiblyModified(Problem.UNEXPECTED)
                }
            } finally {
                if (owner === active) {
                    owner = null
                    val problem = when (val completed = result) {
                        PlainDeckWriteResult.Written -> null
                        is PlainDeckWriteResult.NotStarted -> completed.problem
                        is PlainDeckWriteResult.PossiblyModified -> completed.problem
                    }
                    mutable.value = mutable.value.copy(writePhase = PlainDeckExportWritePhase.IDLE, lastWrite = result, problem = problem)
                }
            }
        }
    }
    fun pickerFailed(ticket: String, problem: Problem) {
        val s = state.value
        if (s.pendingPicker != ticket) return
        mutable.value = s.copy(pendingPicker = null, pickerAuthorized = false,
            problem = if (s.pickerAuthorized && s.visible) problem else s.problem)
    }
    fun cancelPreparation() {
        preparation = null; preparationJob?.cancel(); mutable.value = mutable.value.copy(preparing = false)
    }
    fun cancelWrite() {
        val active = owner ?: return
        mutable.value = mutable.value.copy(writePhase = PlainDeckExportWritePhase.CANCELLING); active.job?.cancel()
    }
    fun hide() { mutable.value = mutable.value.copy(visible = false, pickerAuthorized = false) }
    fun discard(expected: CollectionTextExportPreview) {
        val s = state.value
        if (s.preview !== expected || s.preparing || s.pendingPicker != null || owner != null) return
        destinations.clear(); mutable.value = CollectionTextExportState()
    }
}

/** Export saved state only after pending collection edits and uncertainty are reconciled. */
internal fun CollectionUiState.canExportCollectionText(inventoryWriterBusy: Boolean = false): Boolean = !inventoryWriterBusy && inventory?.complete == true && !loading &&
    !mutationBusy && !pending && !awaitingFresh && !uncertain && form == null && bulkForm == null &&
    !previewing && !bulkPreviewing && !bulkSelectionPreparing
