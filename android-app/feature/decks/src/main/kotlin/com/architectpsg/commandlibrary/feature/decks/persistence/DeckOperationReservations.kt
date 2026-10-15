package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.request
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.text

/** All terminal and pending intents reserve their ID across the entire deck operation namespace. */
internal object DeckOperationReservations {
    /** Caller must hold the same transaction used for legacy replay and subsequent mutation. */
    suspend fun requireUnreserved(tx: DbTransaction, operation: EntityId, cancel: CancelToken) {
        val reserved = value(tx.query(request(
            "SELECT 1 AS reserved FROM deck_draft_intent WHERE operation_id=:operation LIMIT 1",
            "operation" to text(operation.value), maxRows = 1,
        ), cancel))
        if (reserved.isNotEmpty()) reject(FailureCode.INVALID_INPUT, "Operation identity is reserved for a deck draft")
    }
}
