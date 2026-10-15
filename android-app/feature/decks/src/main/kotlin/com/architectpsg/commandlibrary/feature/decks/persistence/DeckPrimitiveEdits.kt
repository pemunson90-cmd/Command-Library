package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.decks.DeckPatchReducer

/**
 * Fresh primitive mutation in the caller's transaction. Caller owns input/fingerprint/replay/epoch
 * admission and must write its receipt atomically with these writes before publishing success.
 * This helper never opens transactions, catches failures, or grants package edit authority.
 */
internal class DeckPrimitiveEdits(private val store: DeckSnapshotStore) {
    private val undoRows = DeckUndoRows(store)

    suspend fun apply(tx: DbTransaction, id: DeckId, patch: DeckPatch, expected: Revision,
        cancel: CancelToken, now: () -> UtcTimestamp): DeckSnapshot = with(store) {
        val source = current(tx, id, cancel)
        if (source.revision != expected) reject(FailureCode.STALE_REVISION, "Deck changed")
        validate(source, FailureCode.CORRUPT_DATA, cancel)
        if (source.packageInstances.isNotEmpty()) throw DeckLibraryProblem(Outcome.Unavailable(
            UnavailableReason.UNSUPPORTED, "Package mutations require a contribution-safe edit policy"))
        val state = undoRows.state(tx, source, cancel)
        if (patch.edits.isEmpty()) {
            return@with source
        }
        if (source.revision.value == Long.MAX_VALUE) reject(FailureCode.PERSISTENCE, "Deck revision is exhausted")
        val candidate = value(DeckPatchReducer.propose(source, patch, expected, now()))
        validate(candidate, FailureCode.INVALID_INPUT, cancel)
        val payload = encode(candidate)
        insertRevision(tx, candidate, payload, cancel)
        updateHead(tx, candidate, expected, cancel)
        undoRows.append(tx, id, candidate.revision.value, expected.value, state.pointer, cancel)
        undoRows.advance(tx, id, expected, candidate.revision, state, candidate.revision.value, cancel)
        candidate
    }
}
