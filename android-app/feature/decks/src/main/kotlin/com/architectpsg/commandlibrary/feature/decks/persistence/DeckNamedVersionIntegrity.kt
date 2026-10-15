package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.long
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.number
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.request

/** Fixed-depth indexed integrity checks, independent of the mutable content head. */
internal class DeckNamedVersionIntegrity(private val store: DeckSnapshotStore, private val rows: DeckNamedVersionRows) {
    data class Admitted(val named: DeckNamedVersionRows.Named, val source: DeckLibraryRows.Metadata,
        val target: DeckLibraryRows.Metadata?, val result: DeckLibraryRows.Metadata?)

    suspend fun latest(tx: DbTransaction, id: DeckId, cancel: CancelToken): DeckNamedVersionRows.Named? {
        val capture = rows.latestReceipt(tx, id, "CAPTURE", cancel)
        val restore = rows.latestReceipt(tx, id, "RESTORE", cancel)
        val receipt = listOfNotNull(capture, restore).maxByOrNull { it.sequence }
        val newest = rows.select(tx, id, "", emptyArray(), cancel).singleOrNull()
        if (receipt == null) { corrupt(newest == null); return null }
        corrupt(newest != null && newest.sequence == receipt.sequence && newest.receipt == receipt)
        admit(tx, newest!!, cancel)
        return newest
    }

    /** Every legal content mutation appends a new revision; an older coherent head is corrupt. */
    suspend fun current(tx: DbTransaction, source: DeckSnapshot, cancel: CancelToken) {
        val latest = value(tx.query(request("SELECT ${DeckLibraryRows.integer("revision", "revision")} FROM deck_revision WHERE provider=:provider AND deck_id=:id ORDER BY revision DESC LIMIT 1", *store.keys(source.id)), cancel))
        corrupt(latest.size == 1 && latest.single().long("revision") == source.revision.value)
    }

    private suspend fun metadata(tx: DbTransaction, id: DeckId, revision: Long, cancel: CancelToken): DeckLibraryRows.Metadata {
        val selected = value(tx.query(request("SELECT ${DeckLibraryRows.projection("r", "r_", true)} FROM deck_revision r WHERE r.provider=:provider AND r.deck_id=:id AND r.revision=:revision", *store.keys(id), "revision" to number(revision)), cancel))
        corrupt(selected.size == 1)
        return DeckLibraryRows.metadata(selected.single(), "r_", true).also { corrupt(it.summary.id == id && it.summary.revision.value == revision) }
    }

    /** Validate immediate actual predecessor and direct target, never follow a target chain. */
    private suspend fun relations(tx: DbTransaction, named: DeckNamedVersionRows.Named, cancel: CancelToken): DeckNamedVersionRows.Named? {
        cancel.throwIfCancelled()
        val summary = named.summary
        val previous = rows.previous(tx, named, cancel)
        corrupt(summary.predecessorVersionId == previous?.summary?.id)
        if (previous != null) corrupt(previous.sequence < named.sequence && previous.receipt.revision <= summary.sourceRevision.value &&
            !summary.createdAt.precedes(previous.summary.createdAt))
        val target = summary.restoredTargetVersionId?.let {
            rows.find(tx, summary.deckId, it, cancel).also { found -> corrupt(found != null) }!!
        }
        if (target != null) corrupt(target.sequence < named.sequence && target.receipt.revision <= summary.sourceRevision.value &&
            !summary.createdAt.precedes(target.summary.createdAt))
        return target
    }

    suspend fun admit(tx: DbTransaction, named: DeckNamedVersionRows.Named, cancel: CancelToken): Admitted {
        val target = relations(tx, named, cancel)
        val summary = named.summary
        val source = metadata(tx, summary.deckId, summary.sourceRevision.value, cancel)
        val anchor = if (summary.sourceRevision.value == 0L) source else metadata(tx, summary.deckId, 0, cancel)
        corrupt(source.summary.createdAt == anchor.summary.createdAt && !summary.createdAt.precedes(source.summary.modifiedAt))
        val targetMetadata = target?.let {
            // Check direct target metadata/source; its own recovery result is not recursively loaded.
            relations(tx, it, cancel)
            metadata(tx, summary.deckId, it.summary.sourceRevision.value, cancel).also { meta ->
                corrupt(meta.summary.createdAt == anchor.summary.createdAt && !it.summary.createdAt.precedes(meta.summary.modifiedAt))
            }
        }
        val result = if (summary.kind == DeckVersionKind.RECOVERY) metadata(tx, summary.deckId, summary.sourceRevision.value + 1, cancel).also {
            corrupt(it.summary.createdAt == anchor.summary.createdAt && it.summary.modifiedAt == summary.createdAt)
        } else null
        return Admitted(named, source, targetMetadata, result)
    }

    suspend fun version(tx: DbTransaction, admitted: Admitted, cancel: CancelToken): DeckVersion {
        val source = store.payload(tx, admitted.source, cancel)
        if (admitted.result != null) {
            val target = store.payload(tx, admitted.target!!, cancel)
            val result = store.payload(tx, admitted.result, cancel)
            corrupt(result.copy(revision = target.revision, modifiedAt = target.modifiedAt) == target)
        }
        return version(admitted.named.summary, source)
    }

    fun version(summary: DeckVersionSummary, source: DeckSnapshot): DeckVersion = DeckVersion(summary.id, summary.deckId,
        summary.label, summary.createdAt, summary.sourceRevision, source, summary.predecessorVersionId)

    companion object {
        fun UtcTimestamp.precedes(other: UtcTimestamp): Boolean =
            epochSeconds < other.epochSeconds || epochSeconds == other.epochSeconds && nanoAdjustment < other.nanoAdjustment
    }
}
