package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Retained application-owned unresolved import. No stream reuse, catalog resolution or draft port. */
internal class PlainDeckImportController(
    private val scope: CoroutineScope,
    private val codec: PlainDeckListCodec,
    private val decks: LocalDeckLibrary,
    private val dispatcher: CoroutineDispatcher,
    private val nextEntityId: () -> EntityId,
    private val now: () -> UtcTimestamp,
    private val validate: (DeckSnapshot, CancelToken) -> Outcome<Unit>,
    private val onCommitted: (DeckSnapshot) -> Unit,
) {
    private val mutable = MutableStateFlow(PlainDeckImportState())
    val state: StateFlow<PlainDeckImportState> = mutable.asStateFlow()
    private var serial = 0L
    private var parseTicket = 0L
    private var previewTicket = 0L
    private var parseJob: Job? = null
    private var previewJob: Job? = null
    private var mutationJob: Job? = null
    private data class Request(val snapshot: DeckSnapshot, val operation: EntityId)
    private var request: Request? = null
    private var known: DeckSnapshot? = null
    private var uncertain = false

    private fun retained(): Boolean = request != null || known != null || mutable.value.submitting
    private fun invalidatePreview() {
        ++previewTicket; previewJob?.cancel()
        mutable.value = mutable.value.copy(preview = null, previewing = false)
    }
    fun parse(input: TransferInput) {
        if (retained()) return
        ++parseTicket; parseJob?.cancel(); invalidatePreview()
        val ticket = parseTicket
        val name = mutable.value.name
        mutable.value = PlainDeckImportState(formTicket = ++serial, visible = true, inputName = input.displayName, name = name, parsing = true)
        parseJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result: PortResult<PlainDeckList> = callPort(dispatcher) { cancel ->
                    val admitted = checked(FailureCode.INVALID_INPUT) {
                        val check = ImportCheck(cancel)
                        check.text(input.id.value, PlainDeckListLimits.MAX_IDENTIFIER_BYTES, nonblank = true)
                        check.text(input.displayName, PlainDeckListLimits.MAX_DISPLAY_NAME_BYTES)
                        val expected = (input.expectedBytes as? Knowledge.Known)?.value
                        requireImport(expected == null || expected in 0L..PlainDeckListLimits.MAX_BYTES.toLong())
                    }
                    when (admitted) {
                        is Outcome.Success -> when (val parsed = codec.parse(input, cancel)) {
                            is Outcome.Success -> checked(FailureCode.CORRUPT_DATA) {
                                checkParsed(parsed.value, input.id, input.displayName, ImportCheck(cancel))
                                parsed.value
                            }
                            is Outcome.Failure -> parsed; is Outcome.Unavailable -> parsed; is Outcome.Unknown -> parsed
                        }
                        is Outcome.Failure -> admitted; is Outcome.Unavailable -> admitted; is Outcome.Unknown -> admitted
                    }
                }
                currentCoroutineContext().ensureActive()
                if (ticket != parseTicket) return@launch
                when (result) {
                    is PortResult.Ok -> mutable.value = mutable.value.copy(input = result.value, problem = null)
                    is PortResult.Failed -> mutable.value = mutable.value.copy(problem = result.problem)
                }
            } finally { if (ticket == parseTicket) mutable.value = mutable.value.copy(parsing = false) }
        }
    }
    fun name(value: String) {
        if (retained() || !mutable.value.visible) return
        invalidatePreview()
        mutable.value = mutable.value.copy(name = value, problem = null)
    }
    fun preview() {
        val s = mutable.value
        if (retained() || !s.visible || s.parsing || s.previewing) return
        val input = s.input ?: return
        val source = s.inputName ?: return
        invalidatePreview()
        val ticket = previewTicket
        mutable.value = mutable.value.copy(previewing = true, problem = null)
        previewJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = callPort(dispatcher) { cancel -> checked(FailureCode.INVALID_INPUT) {
                    ready(input, source, s.name, ImportCheck(cancel))
                    PlainDeckImportPreview(input, s.name)
                } }
                currentCoroutineContext().ensureActive()
                if (ticket != previewTicket) return@launch
                when (result) {
                    is PortResult.Ok -> mutable.value = mutable.value.copy(preview = result.value, problem = null)
                    is PortResult.Failed -> mutable.value = mutable.value.copy(problem = result.problem)
                }
            } finally { if (ticket == previewTicket) mutable.value = mutable.value.copy(previewing = false) }
        }
    }
    fun confirm(expected: PlainDeckImportPreview) {
        val s = mutable.value
        if (retained() || !s.visible || s.parsing || s.previewing || s.preview !== expected) return
        val source = s.inputName ?: return
        mutable.value = s.copy(submitting = true, problem = null)
        mutationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                currentCoroutineContext().ensureActive()
                val built = callPort(dispatcher) { cancel ->
                    val made = checked(FailureCode.INVALID_INPUT) { build(expected, source, ImportCheck(cancel)) }
                    if (made !is Outcome.Success) made else when (val result = validate(made.value.snapshot, cancel)) {
                        is Outcome.Success -> { cancel.throwIfCancelled(); made }
                        is Outcome.Failure -> result; is Outcome.Unavailable -> result; is Outcome.Unknown -> result
                    }
                }
                currentCoroutineContext().ensureActive()
                when (built) {
                    is PortResult.Failed -> mutable.value = mutable.value.copy(problem = built.problem)
                    is PortResult.Ok -> {
                        // Freeze before the first create suspension. Neither retry nor hide rebuilds it.
                        request = built.value
                        uncertain = false
                        create(built.value)
                    }
                }
            } catch (e: CancellationException) {
                interrupted(); throw e
            } catch (_: Exception) { interrupted() }
            finally { if (mutable.value.formTicket == s.formTicket) mutable.value = mutable.value.copy(submitting = false) }
        }
    }
    fun retry() {
        val frozen = request ?: return // Parsing has consumed its stream; never retry that flow.
        if (mutable.value.submitting || mutationJob?.isActive == true) return
        val ticket = mutable.value.formTicket
        mutable.value = mutable.value.copy(submitting = true, problem = null)
        mutationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                currentCoroutineContext().ensureActive()
                if (known != null) readFresh() else create(frozen)
            } catch (e: CancellationException) { interrupted(); throw e }
            catch (_: Exception) { interrupted() }
            finally { if (mutable.value.formTicket == ticket) mutable.value = mutable.value.copy(submitting = false) }
        }
    }
    private fun interrupted() {
        if (known != null) mutable.value = mutable.value.copy(awaitingFresh = true, uncertain = false, problem = Problem.UNKNOWN_RESULT)
        else if (request != null) {
            uncertain = true
            mutable.value = mutable.value.copy(uncertain = true, problem = Problem.UNKNOWN_RESULT)
        } else mutable.value = mutable.value.copy(problem = Problem.UNKNOWN_RESULT)
    }
    private suspend fun create(frozen: Request) {
        val result = callPort(dispatcher) { decks.create(frozen.snapshot, frozen.operation, it) }
        currentCoroutineContext().ensureActive()
        when (result) {
            is PortResult.Failed -> {
                uncertain = uncertain || result.problem in setOf(Problem.PERSISTENCE, Problem.TRANSPORT, Problem.INTERNAL,
                    Problem.UNKNOWN_RESULT, Problem.UNEXPECTED, Problem.CORRUPT_DATA)
                if (!uncertain) request = null
                mutable.value = mutable.value.copy(uncertain = uncertain, problem = result.problem)
            }
            is PortResult.Ok -> {
                if (result.value != frozen.snapshot) {
                    uncertain = true
                    mutable.value = mutable.value.copy(uncertain = true, problem = Problem.CORRUPT_DATA)
                } else {
                    known = frozen.snapshot; uncertain = false
                    mutable.value = mutable.value.copy(acknowledgedId = frozen.snapshot.id, uncertain = false, awaitingFresh = true, problem = null)
                    readFresh()
                }
            }
        }
    }
    private suspend fun readFresh() {
        val ack = known ?: return
        val result: PortResult<DeckSnapshot> = callPort(dispatcher) { cancel -> when (val loaded = decks.load(ack.id, cancel)) {
            is Outcome.Success -> {
                val head = loaded.value
                val scalars = checked(FailureCode.CORRUPT_DATA) {
                    val check = ImportCheck(cancel)
                    requireImport(head.id == ack.id && head.createdAt == ack.createdAt && head.revision.value >= ack.revision.value &&
                        compareTime(head.modifiedAt, ack.modifiedAt) >= 0 && head.entries.size <= DeckPayloadLimits.MAX_ENTRIES)
                    check.text(head.name, LocalDeckLibraryLimits.MAX_NAME_BYTES, nonblank = true)
                }
                when (scalars) {
                    is Outcome.Success -> when (validate(head, cancel)) {
                        is Outcome.Success -> checked(FailureCode.CORRUPT_DATA) {
                            cancel.throwIfCancelled()
                            requireImport(head.revision != ack.revision || head == ack)
                            head
                        }
                        else -> corrupt()
                    }
                    is Outcome.Failure -> scalars; is Outcome.Unavailable -> scalars; is Outcome.Unknown -> scalars
                }
            }
            is Outcome.Failure -> loaded; is Outcome.Unavailable -> loaded; is Outcome.Unknown -> loaded
        } }
        currentCoroutineContext().ensureActive()
        when (result) {
            is PortResult.Failed -> mutable.value = mutable.value.copy(awaitingFresh = true, uncertain = false, problem = result.problem)
            is PortResult.Ok -> {
                request = null; known = null; uncertain = false
                mutable.value = PlainDeckImportState(formTicket = ++serial, acknowledgedId = ack.id, currentHead = result.value)
                // Clear authority first: callback failure cannot cause a second create or notification.
                try { onCommitted(result.value) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { mutable.value = mutable.value.copy(problem = Problem.INTERNAL) }
            }
        }
    }
    fun close() { mutable.value = mutable.value.copy(visible = false) }
    fun resume() {
        val s = mutable.value
        if (s.inputName != null || s.input != null || s.parsing || retained()) mutable.value = s.copy(visible = true)
    }
    fun cancelParse() {
        ++parseTicket; parseJob?.cancel()
        mutable.value = mutable.value.copy(parsing = false)
    }
    fun discardInput() {
        if (retained()) return
        cancelParse(); invalidatePreview()
        mutable.value = PlainDeckImportState(formTicket = ++serial)
    }

    private fun build(preview: PlainDeckImportPreview, source: String, check: ImportCheck): Request {
        ready(preview.input, source, preview.name, check)
        val generated = HashSet<EntityId>()
        fun id(): EntityId {
            check.check()
            val value = nextEntityId()
            check.text(value.value, LocalDeckLibraryLimits.MAX_ID_BYTES, nonblank = true)
            requireImport(generated.add(value))
            return value
        }
        val deckId = DeckId(LocalDeckLibraryLimits.SOURCE, id().value)
        val entries = ArrayList<DeckEntry>()
        for (row in preview.input.rows) {
            check.check()
            val entry = row.entry ?: continue
            entries.add(DeckEntry(id(), CardIdentity(
                Knowledge.Unknown("Plain list does not identify an Oracle card", SnapshotList.empty()),
                Knowledge.Unknown("Plain list does not identify a printing", SnapshotList.empty()), null,
                Knowledge.Known(entry.name), LanguageTag("und")), entry.quantity, entry.board,
                Knowledge.Unknown("Physical finish not supplied by plain list", SnapshotList.empty()), "", SnapshotList.empty(), SnapshotList.empty()))
        }
        val operation = id()
        check.check()
        val time = now()
        val snapshot = DeckSnapshot(deckId, preview.name, Knowledge.Absent("No format selected"), "", Knowledge.Absent("No folder selected"),
            SnapshotList.empty(), time, time, Revision(0), SnapshotList.copyOf(entries), Knowledge.Absent("No budget set"), null,
            SnapshotList.empty(), SnapshotList.empty(), categoryOrder = SnapshotList.empty())
        check.check()
        return Request(snapshot, operation)
    }
    private fun ready(input: PlainDeckList, source: String, name: String, check: ImportCheck) {
        checkParsed(input, input.inputId, source, check)
        check.text(name, LocalDeckLibraryLimits.MAX_NAME_BYTES, nonblank = true)
        requireImport(input.complete && input.rows.any { it.entry != null } && input.rows.none { row -> row.issues.any { it.severity == IssueSeverity.ERROR } })
        check.check()
    }
    private fun checkParsed(input: PlainDeckList, expectedId: EntityId, source: String, check: ImportCheck) {
        check.check()
        requireImport(input.inputId == expectedId && input.rows.size <= PlainDeckListLimits.MAX_LINES)
        check.text(input.inputId.value, PlainDeckListLimits.MAX_IDENTIFIER_BYTES, nonblank = true)
        check.text(source, PlainDeckListLimits.MAX_DISPLAY_NAME_BYTES)
        // Coarse separate body/name/issue budgets precede all repeated string scans.
        var rawUnits = 0L; var nameUnits = 0L; var issueUnits = 0L; var count = 0
        for (row in input.rows) {
            check.check()
            rawUnits += row.rawText.length
            nameUnits += row.entry?.name?.length ?: 0
            if (row.entry != null) count++
            requireImport(row.issues.size <= 1)
            for (issue in row.issues) issueUnits += issue.description.length.toLong()
            requireImport(rawUnits <= PlainDeckListLimits.MAX_BYTES && nameUnits <= PlainDeckListLimits.MAX_BYTES && issueUnits <= PlainDeckListLimits.MAX_BYTES && count <= PlainDeckListLimits.MAX_ENTRIES)
        }
        var bytes = 0L
        var previousEnd = -1L
        var board = BoardRole.MAIN
        input.rows.forEachIndexed { index, row ->
            check.check()
            requireImport(row.location.sourceName == source && row.location.rowNumber == index + 1L && row.location.fieldName == null)
            val offset = row.location.byteOffset
            requireImport(offset != null && offset in 0L..PlainDeckListLimits.MAX_BYTES.toLong() && offset > previousEnd)
            val length = check.text(row.rawText, PlainDeckListLimits.MAX_LINE_BYTES, singleLine = true)
            bytes += length
            requireImport(bytes + index <= PlainDeckListLimits.MAX_BYTES)
            previousEnd = checkNotNull(offset) + length
            requireImport(previousEnd <= PlainDeckListLimits.MAX_BYTES)
            val entry = row.entry
            val heading = headings.firstOrNull { it.first == row.rawText }
            if (entry != null) {
                requireImport(row.issues.isEmpty() && entry.quantity > 0 && entry.board == board)
                check.text(entry.name, PlainDeckListLimits.MAX_NAME_BYTES, nonblank = true, singleLine = true)
                requireImport(entry.name.first() != ' ' && entry.name.first() != '\t')
                val quantity = entry.quantity.toString()
                requireImport(row.rawText.startsWith(quantity) && row.rawText.length > quantity.length)
                var start = quantity.length
                requireImport(row.rawText[start] == ' ' || row.rawText[start] == '\t')
                while (start < row.rawText.length && (row.rawText[start] == ' ' || row.rawText[start] == '\t')) { check.visit(); start++ }
                requireImport(row.rawText.length - start == entry.name.length && row.rawText.regionMatches(start, entry.name, 0, entry.name.length))
            } else if (row.issues.isEmpty()) {
                requireImport(heading != null || row.rawText.isBlank() || (index == 0 && row.rawText == PlainDeckListLimits.MARKER))
                if (heading != null) board = heading.second
            }
            for (issue in row.issues) {
                check.check()
                requireImport(issue.code.length <= 64)
                requireImport(issue.location == row.location && issue.severity == IssueSeverity.ERROR && issue.code in issueCodes)
                check.text(issue.description, PlainDeckListLimits.MAX_LINE_BYTES)
                val resolution = issue.resolution as? Knowledge.Absent ?: throw InvalidPlainImport()
                check.text(resolution.reason, PlainDeckListLimits.MAX_DISPLAY_NAME_BYTES, nonblank = true)
            }
        }
        check.check()
    }
    private class InvalidPlainImport : RuntimeException()
    private fun requireImport(value: Boolean) { if (!value) throw InvalidPlainImport() }
    private fun <T> checked(code: FailureCode, action: () -> T): Outcome<T> = try { Outcome.Success(action()) }
        catch (_: InvalidPlainImport) { Outcome.Failure(DomainFailure(code, "Invalid plain deck import", SnapshotList.empty())) }
    private fun corrupt(): Outcome<Nothing> = Outcome.Failure(DomainFailure(FailureCode.CORRUPT_DATA, "Invalid imported deck head", SnapshotList.empty()))
    private fun compareTime(a: UtcTimestamp, b: UtcTimestamp): Int = a.epochSeconds.compareTo(b.epochSeconds).takeIf { it != 0 } ?: a.nanoAdjustment.compareTo(b.nanoAdjustment)
    private inner class ImportCheck(private val cancel: CancelToken) {
        private var left = 1_024
        fun check() { cancel.throwIfCancelled() }
        fun visit() { if (--left == 0) { check(); left = 1_024 } }
        fun text(value: String, maximum: Int, nonblank: Boolean = false, singleLine: Boolean = false): Int {
            check(); requireImport(value.length <= maximum)
            var bytes = 0; var index = 0; var visible = false
            while (index < value.length) {
                visit()
                val c = value[index++]
                if (!c.isWhitespace()) visible = true
                requireImport(!singleLine || c != '\r' && c != '\n')
                bytes += when {
                    c.code < 128 -> 1
                    c.code < 2048 -> 2
                    c.isHighSurrogate() -> { requireImport(index < value.length && value[index].isLowSurrogate()); visit(); index++; 4 }
                    c.isLowSurrogate() -> throw InvalidPlainImport()
                    else -> 3
                }
                requireImport(bytes <= maximum)
            }
            requireImport(!nonblank || visible)
            check(); return bytes
        }
    }
    private val headings = listOf("Commander" to BoardRole.COMMANDER, "Partner" to BoardRole.PARTNER, "Background" to BoardRole.BACKGROUND,
        "Companion" to BoardRole.COMPANION, "Deck" to BoardRole.MAIN, "Sideboard" to BoardRole.SIDEBOARD, "Maybeboard" to BoardRole.MAYBEBOARD)
    private val issueCodes = setOf(PlainDeckListIssueCodes.INVALID_QUANTITY, PlainDeckListIssueCodes.INVALID_ROW, PlainDeckListIssueCodes.INVALID_MARKER)
}
