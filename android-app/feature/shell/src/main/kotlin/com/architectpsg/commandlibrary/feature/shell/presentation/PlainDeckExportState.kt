package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.DeckSnapshot
import com.architectpsg.commandlibrary.core.model.PlainDeckListExport
import com.architectpsg.commandlibrary.feature.shell.document.PlainDeckWriteResult

/** This exact instance is acknowledgement authority for every retained loss and frozen byte. */
internal data class PlainDeckExportPreview(val source: DeckSnapshot, val export: PlainDeckListExport)

internal enum class PlainDeckExportWritePhase { IDLE, WRITING, CANCELLING }

internal data class PlainDeckExportState(
    val formTicket: Long = 0,
    val visible: Boolean = false,
    val preparing: Boolean = false,
    val problem: Problem? = null,
    val preview: PlainDeckExportPreview? = null,
    val pendingPicker: String? = null,
    val pickerAuthorized: Boolean = false,
    val writePhase: PlainDeckExportWritePhase = PlainDeckExportWritePhase.IDLE,
    val lastWrite: PlainDeckWriteResult? = null,
    val attempts: Int = 0,
)

internal object PlainDeckExportLimits {
    const val MAX_DESTINATION_ATTEMPTS: Int = 32
    const val MAX_URI_CODE_UNITS: Int = 16_384
}
