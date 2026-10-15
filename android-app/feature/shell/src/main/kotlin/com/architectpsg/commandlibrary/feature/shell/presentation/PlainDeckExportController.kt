package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.common.CoroutineCancelToken
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.document.DocumentRef
import com.architectpsg.commandlibrary.feature.shell.document.PlainDeckWriteResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Retained, application-owned all-board export. Commands belong to the presentation owner. */
internal class PlainDeckExportController(
    private val scope: CoroutineScope,
    private val library: LocalDeckLibrary,
    private val codec: PlainDeckListCodec,
    private val validate: (DeckSnapshot, CancelToken) -> Outcome<Unit>,
    private val write: suspend (DocumentRef, SnapshotBytes, CancelToken) -> PlainDeckWriteResult,
    private val dispatcher: CoroutineDispatcher,
    private val pickerOwnerNonce: String,
) {
    init { require(pickerOwnerNonce.length <= 256 && pickerOwnerNonce.isNotBlank()) }

    private val mutable = MutableStateFlow(PlainDeckExportState())
    val state: StateFlow<PlainDeckExportState> = mutable.asStateFlow()
    private var formSerial = 0L
    private var pickerSerial = 0L
    private var preparation: Any? = null
    private var preparationJob: Job? = null
    private val destinations = HashSet<String>()
    private class WriteOwner(val preview: PlainDeckExportPreview, val destination: DocumentRef) {
        var job: Job? = null
    }
    private var owner: WriteOwner? = null

    fun prepare(id: DeckId, expectedRevision: Revision) {
        val s = mutable.value
        if (s.preview != null || s.lastWrite != null || s.pendingPicker != null || owner != null) return
        if (formSerial == Long.MAX_VALUE) {
            mutable.value = s.copy(problem = Problem.INVALID_INPUT)
            return
        }
        preparation = null
        preparationJob?.cancel()
        val ticket = Any()
        preparation = ticket
        mutable.value = PlainDeckExportState(formTicket = ++formSerial, visible = true, preparing = true)
        if (id.source != LocalDeckLibraryLimits.SOURCE) {
            preparation = null
            mutable.value = mutable.value.copy(preparing = false, problem = Problem.INVALID_INPUT)
            return
        }
        preparationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = callPort(dispatcher) { cancel -> prepared(id, expectedRevision, cancel) }
                currentCoroutineContext().ensureActive()
                if (preparation !== ticket) return@launch
                mutable.value = when (result) {
                    is PortResult.Ok -> mutable.value.copy(preview = result.value, problem = null)
                    is PortResult.Failed -> mutable.value.copy(problem = result.problem)
                }
            } finally {
                if (preparation === ticket) {
                    preparation = null
                    mutable.value = mutable.value.copy(preparing = false)
                }
            }
        }
    }

    private suspend fun prepared(id: DeckId, expected: Revision, cancel: CancelToken): Outcome<PlainDeckExportPreview> {
        val source = when (val loaded = library.load(id, cancel)) {
            is Outcome.Success -> loaded.value
            is Outcome.Failure -> return loaded
            is Outcome.Unavailable -> return loaded
            is Outcome.Unknown -> return loaded
        }
        cancel.throwIfCancelled()
        if (source.id != id) return refused(FailureCode.CORRUPT_DATA)
        if (source.revision != expected) return refused(FailureCode.STALE_REVISION)
        when (val validated = validate(source, cancel)) {
            is Outcome.Success -> Unit
            is Outcome.Failure -> return validated
            is Outcome.Unavailable -> return validated
            is Outcome.Unknown -> return validated
        }
        cancel.throwIfCancelled()
        val rendered = when (val result = codec.render(source, cancel)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return result
            is Outcome.Unavailable -> return result
            is Outcome.Unknown -> return result
        }
        if (rendered.bytes.size !in 1..PlainDeckListLimits.MAX_BYTES || rendered.losses.size > PlainDeckListLimits.MAX_LOSSES ||
            source.entries.size > PlainDeckListLimits.MAX_ENTRIES) return refused(FailureCode.CORRUPT_DATA)
        val ids = HashSet<EntityId>()
        for (entry in source.entries) {
            cancel.throwIfCancelled()
            if (!ids.add(entry.id)) return refused(FailureCode.CORRUPT_DATA)
        }
        val seen = HashSet<Pair<EntityId?, PlainDeckListLossField>>()
        for (loss in rendered.losses) {
            cancel.throwIfCancelled()
            val entryId = loss.entryId
            val deckWide = when (loss.field) {
                PlainDeckListLossField.DECK_ID, PlainDeckListLossField.DECK_NAME, PlainDeckListLossField.FORMAT,
                PlainDeckListLossField.DESCRIPTION, PlainDeckListLossField.FOLDER, PlainDeckListLossField.TAGS,
                PlainDeckListLossField.CREATED_AT, PlainDeckListLossField.MODIFIED_AT, PlainDeckListLossField.REVISION,
                PlainDeckListLossField.BUDGET, PlainDeckListLossField.PROFILE_ID, PlainDeckListLossField.CATALOG_REFERENCES,
                PlainDeckListLossField.PACKAGE_INSTANCES, PlainDeckListLossField.PRIMER,
                PlainDeckListLossField.CATEGORY_ORDER, PlainDeckListLossField.ENTRY_ORDER -> true
                PlainDeckListLossField.ENTRY_ID, PlainDeckListLossField.ORACLE_ID, PlainDeckListLossField.PRINTING_ID,
                PlainDeckListLossField.RAW_IDENTITY, PlainDeckListLossField.LANGUAGE, PlainDeckListLossField.FINISH,
                PlainDeckListLossField.NOTES, PlainDeckListLossField.LABELS, PlainDeckListLossField.ROLE_OVERRIDES -> false
            }
            if ((if (deckWide) entryId != null else entryId == null || entryId !in ids) || !seen.add(entryId to loss.field))
                return refused(FailureCode.CORRUPT_DATA)
        }
        cancel.throwIfCancelled()
        return Outcome.Success(PlainDeckExportPreview(source, rendered))
    }

    private fun refused(code: FailureCode): Outcome.Failure =
        Outcome.Failure(DomainFailure(code, "Invalid plain deck export", SnapshotList.empty()))

    fun requestDestination(expected: PlainDeckExportPreview): String? {
        val s = mutable.value
        if (!s.visible || s.preview !== expected || s.preparing || s.pendingPicker != null || owner != null) return null
        if (destinations.size >= PlainDeckExportLimits.MAX_DESTINATION_ATTEMPTS || pickerSerial == Long.MAX_VALUE) {
            mutable.value = s.copy(problem = Problem.INVALID_INPUT)
            return null
        }
        val ticket = "$pickerOwnerNonce:${++pickerSerial}"
        mutable.value = s.copy(pendingPicker = ticket, pickerAuthorized = true, problem = null)
        return ticket
    }

    fun pickerResult(ticket: String, destination: DocumentRef?) {
        val s = mutable.value
        if (s.pendingPicker != ticket) return
        // Consume even revoked tickets before scheduling anything. Resume never reauthorizes them.
        mutable.value = s.copy(pendingPicker = null, pickerAuthorized = false)
        if (!s.pickerAuthorized || !s.visible || destination == null) return
        val preview = s.preview ?: return
        if (destination.uri.length > PlainDeckExportLimits.MAX_URI_CODE_UNITS ||
            destinations.size >= PlainDeckExportLimits.MAX_DESTINATION_ATTEMPTS || !destinations.add(destination.uri)) {
            mutable.value = mutable.value.copy(problem = Problem.INVALID_INPUT)
            return
        }
        val active = WriteOwner(preview, destination)
        owner = active
        mutable.value = mutable.value.copy(writePhase = PlainDeckExportWritePhase.WRITING, attempts = destinations.size, problem = null)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Entry/finally are guaranteed even if the supplied scope is already cancelled.
            var result: PlainDeckWriteResult = PlainDeckWriteResult.PossiblyModified(Problem.UNKNOWN_RESULT)
            try {
                active.job = currentCoroutineContext().job
                currentCoroutineContext().ensureActive()
                result = try {
                    val reported = withContext(dispatcher) {
                        write(active.destination, active.preview.export.bytes, CoroutineCancelToken(currentCoroutineContext()))
                    }
                    currentCoroutineContext().ensureActive()
                    reported
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    PlainDeckWriteResult.PossiblyModified(Problem.UNEXPECTED)
                }
            } finally {
                // Job.isActive is never an ownership test: writer cleanup may still be running.
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
        val s = mutable.value
        if (s.pendingPicker != ticket) return
        mutable.value = s.copy(pendingPicker = null, pickerAuthorized = false,
            problem = if (s.pickerAuthorized && s.visible) problem else s.problem)
    }

    fun cancelPreparation() {
        preparation = null
        preparationJob?.cancel()
        mutable.value = mutable.value.copy(preparing = false)
    }

    fun cancelWrite() {
        val active = owner ?: return
        mutable.value = mutable.value.copy(writePhase = PlainDeckExportWritePhase.CANCELLING)
        active.job?.cancel()
    }

    fun hide() { mutable.value = mutable.value.copy(visible = false, pickerAuthorized = false) }
    fun resume() {
        val s = mutable.value
        if (s.preparing || s.preview != null || s.pendingPicker != null || owner != null || s.problem != null)
            mutable.value = s.copy(visible = true)
    }

    fun discard(expected: PlainDeckExportPreview) {
        val s = mutable.value
        if (s.preview !== expected || s.preparing || s.pendingPicker != null || owner != null) return
        destinations.clear()
        if (formSerial != Long.MAX_VALUE) formSerial++
        mutable.value = PlainDeckExportState(formTicket = formSerial)
    }
}
