package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*

/** Field merge only. Caller must prove this is the same live session's own acknowledged commit. */
internal object DeckDraftQueuedInput {
    /** Null means no additional recoverable raw delta, not an instruction to close the editor. */
    fun afterOwnCommit(captured: DeckDraftBody, residual: DeckDraftBody?, queued: DeckDraftBody?,
        result: DeckSnapshot, addedEntryId: EntityId?, cancel: CancelToken): Outcome<DeckDraftBody?> {
        cancel.throwIfCancelled()
        val current = queued ?: captured
        if (current.baseRevision != captured.baseRevision) return stale()
        fun <T> pick(before: T, current: T, consumed: T): T {
            cancel.throwIfCancelled()
            return if (current == before) consumed else current
        }
        val merged: DeckDraftBody
        val canonical: DeckDraftBody
        when (captured) {
            is DeckDraftBody.Details -> {
                if (current !is DeckDraftBody.Details || addedEntryId != null) return stale()
                canonical = DeckDraftBody.Details(result.revision, result.name, result.description, result.primer)
                val remaining = residual ?: canonical
                if (remaining !is DeckDraftBody.Details || remaining.baseRevision != result.revision) return corrupt()
                merged = current.copy(baseRevision=result.revision,
                    name=pick(captured.name,current.name,remaining.name),
                    description=pick(captured.description,current.description,remaining.description),
                    primer=pick(captured.primer,current.primer,remaining.primer))
            }
            is DeckDraftBody.Entry -> {
                if (current !is DeckDraftBody.Entry || current.entryId != captured.entryId || addedEntryId != null) return stale()
                val entry = result.entries.firstOrNull { it.id == captured.entryId } ?: return corrupt()
                canonical = body(result,entry)
                val remaining = residual ?: canonical
                if (remaining !is DeckDraftBody.Entry || remaining.entryId != captured.entryId || remaining.baseRevision != result.revision) return corrupt()
                merged = current.copy(baseRevision=result.revision,
                    quantity=pick(captured.quantity,current.quantity,remaining.quantity), notes=pick(captured.notes,current.notes,remaining.notes),
                    board=pick(captured.board,current.board,remaining.board), finish=pick(captured.finish,current.finish,remaining.finish),
                    labels=pick(captured.labels,current.labels,remaining.labels))
            }
            is DeckDraftBody.Add -> {
                if (current !is DeckDraftBody.Add || current.selection != captured.selection) return stale()
                if (residual != null || addedEntryId == null) return corrupt()
                val entry = result.entries.firstOrNull { it.id == addedEntryId } ?: return corrupt()
                val committed = body(result,entry)
                canonical = committed
                // This owner's successful ADD becomes editing the one frozen created identity.
                merged = committed.copy(
                    quantity=pick(captured.quantity,current.quantity,committed.quantity), notes=pick(captured.notes,current.notes,committed.notes),
                    board=pick(captured.board,current.board,committed.board), finish=pick(captured.finish,current.finish,committed.finish),
                    labels=pick(captured.labels,current.labels,committed.labels))
            }
            is DeckDraftBody.Remove -> {
                if (current !is DeckDraftBody.Remove || current.entryId != captured.entryId) return stale()
                if (residual != null || addedEntryId != null || result.entries.any { it.id == captured.entryId }) return corrupt()
                return Outcome.Success(null)
            }
        }
        cancel.throwIfCancelled()
        return Outcome.Success(merged.takeUnless { it == canonical })
    }
    private fun body(deck: DeckSnapshot, entry: DeckEntry) = DeckDraftBody.Entry(deck.revision, entry.id,
        entry.quantity.toString(), entry.notes, entry.board, entry.finish, entry.labels)
    private fun stale(): Outcome<Nothing> = Outcome.Failure(DomainFailure(FailureCode.STALE_REVISION,
        "Queued draft no longer belongs to the acknowledged editor", SnapshotList.empty()))
    private fun corrupt(): Outcome<Nothing> = Outcome.Failure(DomainFailure(FailureCode.CORRUPT_DATA,
        "Invalid acknowledged draft projection", SnapshotList.empty()))
}
