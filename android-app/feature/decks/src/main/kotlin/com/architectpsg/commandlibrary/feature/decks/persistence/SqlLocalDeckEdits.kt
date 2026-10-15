package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import kotlin.coroutines.cancellation.CancellationException

/** Atomic primitive patches and restart-safe grouped undo over immutable local deck history. */
public class SqlLocalDeckEdits(
    db: Database,
    codec: DeckPayloadCodecPort,
    private val patchCodec: LocalDeckPatchCodecPort,
    private val clock: Clock,
) : LocalDeckEdits {
    private val store = DeckSnapshotStore(db, codec)
    private val undoRows = DeckUndoRows(store)
    private val primitive = DeckPrimitiveEdits(store)

    public override suspend fun apply(id: DeckId, patch: DeckPatch, expected: Revision, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> = store.operation(cancel) { tx ->
        id(id); identifier(operationId.value)
        val patchBytes = value(patchCodec.encodePatch(patch))
        val fingerprint = checked(FailureCode.INVALID_INPUT) { DeckLibraryRequests.apply(id, expected, patchBytes) }
        replay(tx, operationId, fingerprint, "APPLY", cancel)?.let { return@operation reconciled(it, id, expected, patch.edits.isNotEmpty()) }
        writableEpoch(epoch(tx, cancel))
        val candidate = primitive.apply(tx, id, patch, expected, cancel, ::now)
        receipt(tx, candidate, operationId, fingerprint, "APPLY", cancel)
        candidate
    }

    public override suspend fun undo(id: DeckId, expected: Revision, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> = store.operation(cancel) { tx ->
        id(id); identifier(operationId.value)
        val fingerprint = checked(FailureCode.INVALID_INPUT) { DeckLibraryRequests.undo(id, expected) }
        replay(tx, operationId, fingerprint, "UNDO", cancel)?.let { return@operation reconciled(it, id, expected, true) }
        writableEpoch(epoch(tx, cancel))
        val source = current(tx, id, cancel)
        if (source.revision != expected) reject(FailureCode.STALE_REVISION, "Deck changed")
        validate(source, FailureCode.CORRUPT_DATA, cancel)
        val state = undoRows.state(tx, source, cancel)
        val selected = undoRows.validatePointer(tx, source, state.pointer, source.revision.value, cancel)
            ?: throw DeckLibraryProblem(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "No deck edit to undo"))
        val target = historical(tx, id, selected.before, cancel)
        validate(target, FailureCode.CORRUPT_DATA, cancel)
        // Validate the pointer that will become visible before publishing it; bounded, no chain walk.
        undoRows.validatePointer(tx, target, selected.previous, selected.before, cancel)
        val revision = nextRevision(source)
        val modifiedAt = now()
        // Whole-snapshot undo preserves the entire package graph; primitive APPLY stays separate.
        if (modifiedAt.precedes(source.modifiedAt) || modifiedAt.precedes(source.createdAt))
            reject(FailureCode.INVALID_INPUT, "Modification time precedes the deck timestamps")
        val candidate = target.copy(id = source.id, createdAt = source.createdAt, revision = revision, modifiedAt = modifiedAt)
        validate(candidate, FailureCode.CORRUPT_DATA, cancel)
        val payload = encode(candidate)
        insertRevision(tx, candidate, payload, cancel)
        updateHead(tx, candidate, expected, cancel)
        undoRows.advance(tx, id, expected, revision, state, selected.previous, cancel)
        receipt(tx, candidate, operationId, fingerprint, "UNDO", cancel)
        candidate
    }

    private fun reconciled(saved: DeckSnapshot, id: DeckId, expected: Revision, changed: Boolean): DeckSnapshot {
        corrupt(saved.id == id)
        if (changed) corrupt(expected.value < Long.MAX_VALUE)
        val revision = if (changed) expected.value + 1 else expected.value
        corrupt(saved.revision.value == revision)
        return saved
    }

    private fun nextRevision(source: DeckSnapshot): Revision {
        if (source.revision.value == Long.MAX_VALUE) reject(FailureCode.PERSISTENCE, "Deck revision is exhausted")
        return Revision(source.revision.value + 1)
    }
    private fun UtcTimestamp.precedes(other: UtcTimestamp): Boolean =
        epochSeconds < other.epochSeconds || (epochSeconds == other.epochSeconds && nanoAdjustment < other.nanoAdjustment)

    private fun now(): UtcTimestamp = try { clock.utcNow() }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { reject(FailureCode.INTERNAL, "Deck clock failed") }
}
