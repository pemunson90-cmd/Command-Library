package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.CatalogImport
import com.architectpsg.commandlibrary.core.model.CatalogImportLimits
import com.architectpsg.commandlibrary.core.model.CatalogImportResult
import com.architectpsg.commandlibrary.core.model.CatalogImportSource
import com.architectpsg.commandlibrary.core.model.JobPhase
import com.architectpsg.commandlibrary.core.model.JobProgress
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.LanguageTag
import com.architectpsg.commandlibrary.core.model.ProviderId
import com.architectpsg.commandlibrary.core.model.ResourceUri
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.feature.shell.document.DocumentChunkStream
import com.architectpsg.commandlibrary.feature.shell.document.DocumentDescription
import com.architectpsg.commandlibrary.feature.shell.document.DocumentRef
import com.architectpsg.commandlibrary.feature.shell.document.DocumentSource
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface ImportFailure {
    /** The file reports, or was read past, more bytes than [limitBytes]. */
    data class TooLarge(val limitBytes: Long, val reportedBytes: Long?) : ImportFailure

    /** The document could not be opened or read. */
    data object ReadFailed : ImportFailure

    /** The document provider refused access. */
    data object PermissionDenied : ImportFailure

    /** The importer rejected the file or could not store it. */
    data class Rejected(val problem: Problem) : ImportFailure
}

internal sealed interface ImportUiState {
    data object Idle : ImportUiState

    /**
     * [fileName] is null while the document is being described. [reportedBytes] is the provider's
     * unverified size. [bytesRead] counts bytes actually read from the document. [phase] and
     * [recordsStaged] are the importer's own last progress report, absent until it reports.
     */
    data class Running(
        val fileName: String?,
        val reportedBytes: Long?,
        val bytesRead: Long,
        val phase: JobPhase?,
        val recordsStaged: Long?,
        val cancelling: Boolean,
    ) : ImportUiState

    data class Succeeded(val fileName: String, val result: CatalogImportResult) : ImportUiState
    data class Failed(val fileName: String?, val failure: ImportFailure) : ImportUiState
    data class Cancelled(val fileName: String?) : ImportUiState
}

/**
 * Runs one user-selected local file import at a time inside [scope]; cancelling the scope cancels
 * the import. Once cancellation is requested the result is reported as cancelled, whatever the
 * importer returns afterwards. Completion of any kind triggers [onFinished] so committed catalog
 * status is read again: a cancellation that arrives after the importer's durable commit cannot undo
 * it, and only that fresh status read is authoritative.
 */
internal class ImportController(
    private val scope: CoroutineScope,
    private val port: CatalogImport,
    private val documents: DocumentSource,
    private val limits: CatalogImportLimits,
    private val ioDispatcher: CoroutineDispatcher,
    private val onFinished: () -> Unit,
) {
    private val mutableState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    /** Starts importing [ref]; returns false without side effects while another import runs. */
    fun start(ref: DocumentRef): Boolean {
        if (isRunning) return false
        mutableState.value = ImportUiState.Running(
            fileName = null,
            reportedBytes = null,
            bytesRead = 0,
            phase = null,
            recordsStaged = null,
            cancelling = false,
        )
        val launched = scope.launch {
            try {
                mutableState.value = runImport(ref)
            } catch (cancelled: CancellationException) {
                mutableState.value = ImportUiState.Cancelled((mutableState.value as? ImportUiState.Running)?.fileName)
                throw cancelled
            }
        }
        launched.invokeOnCompletion { onFinished() }
        job = launched
        return true
    }

    fun cancel() {
        val running = job ?: return
        if (!running.isActive) return
        mutableState.update { if (it is ImportUiState.Running) it.copy(cancelling = true) else it }
        running.cancel()
    }

    /** Clears a finished result; has no effect while an import runs. */
    fun dismiss() {
        if (isRunning) return
        mutableState.value = ImportUiState.Idle
    }

    private suspend fun runImport(ref: DocumentRef): ImportUiState {
        val description = describe(ref)
        val fileName = description.displayName ?: fallbackName(ref)
        updateRunning { it.copy(fileName = fileName, reportedBytes = description.reportedBytes) }
        val reported = description.reportedBytes
        if (reported != null && reported > limits.maxInputBytes) {
            return ImportUiState.Failed(fileName, ImportFailure.TooLarge(limits.maxInputBytes, reported))
        }
        val input = when (val opened = open(ref)) {
            is Opened.Stream -> opened.input
            is Opened.Refused -> return ImportUiState.Failed(fileName, opened.failure)
        }
        val stream = DocumentChunkStream(input, limits.maxInputBytes, ioDispatcher) { bytes ->
            updateRunning { it.copy(bytesRead = bytes) }
        }
        try {
            val source = importSource(ref, fileName)
            val result = callPort(ioDispatcher) { cancel -> port.import(source, stream.chunks, ::onProgress, cancel) }
            return when (result) {
                is PortResult.Ok -> ImportUiState.Succeeded(fileName, result.value)
                is PortResult.Failed -> ImportUiState.Failed(
                    fileName,
                    when (stream.failure) {
                        DocumentChunkStream.Failure.TOO_LARGE -> ImportFailure.TooLarge(limits.maxInputBytes, reported)
                        DocumentChunkStream.Failure.READ_ERROR -> ImportFailure.ReadFailed
                        null -> ImportFailure.Rejected(result.problem)
                    },
                )
            }
        } finally {
            // The importer may return without reading; the stream is closed either way.
            withContext(NonCancellable + ioDispatcher) { stream.close() }
        }
    }

    private suspend fun describe(ref: DocumentRef): DocumentDescription =
        try {
            withContext(ioDispatcher) { documents.describe(ref) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Metadata is optional; opening the document reports any real access failure.
            DocumentDescription(displayName = null, reportedBytes = null)
        }

    private sealed interface Opened {
        class Stream(val input: InputStream) : Opened
        class Refused(val failure: ImportFailure) : Opened
    }

    private suspend fun open(ref: DocumentRef): Opened =
        try {
            Opened.Stream(withContext(ioDispatcher) { documents.open(ref) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            Opened.Refused(ImportFailure.PermissionDenied)
        } catch (_: Exception) {
            Opened.Refused(ImportFailure.ReadFailed)
        }

    private fun onProgress(progress: JobProgress) {
        updateRunning { it.copy(phase = progress.phase, recordsStaged = progress.completedUnits) }
    }

    private inline fun updateRunning(crossinline change: (ImportUiState.Running) -> ImportUiState.Running) {
        mutableState.update { if (it is ImportUiState.Running) change(it) else it }
    }

    /**
     * Only facts the shell actually knows: the picked content URI and its display name. Source date,
     * origin URI, checksum, byte count and notice are not supplied by a local file and stay unknown.
     */
    private fun importSource(ref: DocumentRef, fileName: String): CatalogImportSource =
        CatalogImportSource(
            contentUri = ResourceUri(ref.uri),
            displayName = boundedDisplayName(fileName),
            provider = SCRYFALL,
            language = LanguageTag.ENGLISH,
            sourceDate = Knowledge.Unknown("The selected file does not supply a source date", SnapshotList.empty()),
            sourceUri = Knowledge.Unknown("The selected file does not supply its origin", SnapshotList.empty()),
            expectedChecksum = Knowledge.Unknown("No expected checksum was supplied with the file", SnapshotList.empty()),
            expectedBytes = Knowledge.Unknown("The provider-reported size is not a verified byte count", SnapshotList.empty()),
            notice = Knowledge.Unknown("No source notice was supplied with the file", SnapshotList.empty()),
        )

    internal companion object {
        val SCRYFALL: ProviderId = ProviderId("scryfall")
        const val MAX_DISPLAY_NAME_BYTES: Int = 512
        private const val ELLIPSIS = "…"

        fun fallbackName(ref: DocumentRef): String =
            ref.uri.trimEnd('/').substringAfterLast('/').ifBlank { ref.uri }

        /** Shortens a display name at a code point boundary so it fits the importer's UTF-8 bound. */
        fun boundedDisplayName(name: String): String {
            if (name.toByteArray(Charsets.UTF_8).size <= MAX_DISPLAY_NAME_BYTES) return name
            val budget = MAX_DISPLAY_NAME_BYTES - ELLIPSIS.toByteArray(Charsets.UTF_8).size
            val result = StringBuilder()
            var used = 0
            var index = 0
            while (index < name.length) {
                val codePoint = name.codePointAt(index)
                val text = String(Character.toChars(codePoint))
                val size = text.toByteArray(Charsets.UTF_8).size
                if (used + size > budget) break
                result.append(text)
                used += size
                index += Character.charCount(codePoint)
            }
            return result.append(ELLIPSIS).toString()
        }
    }
}
