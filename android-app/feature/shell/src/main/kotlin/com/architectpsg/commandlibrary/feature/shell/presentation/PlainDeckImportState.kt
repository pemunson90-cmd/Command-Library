package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*

/** Review authority contains no generated identities/time and never drops physical parser rows. */
internal data class PlainDeckImportPreview(val input: PlainDeckList, val name: String)

internal data class PlainDeckImportState(
    val formTicket: Long = 0,
    val visible: Boolean = false,
    val inputName: String? = null,
    val input: PlainDeckList? = null,
    val name: String = "",
    val parsing: Boolean = false,
    val previewing: Boolean = false,
    val preview: PlainDeckImportPreview? = null,
    val submitting: Boolean = false,
    val uncertain: Boolean = false,
    val awaitingFresh: Boolean = false,
    val problem: Problem? = null,
    /** Historical create acknowledgement is not the mutable current head. */
    val acknowledgedId: DeckId? = null,
    val currentHead: DeckSnapshot? = null,
)
