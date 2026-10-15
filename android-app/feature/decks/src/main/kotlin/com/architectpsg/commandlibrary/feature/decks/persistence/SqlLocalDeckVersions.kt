package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.decks.DeckDocumentDiff
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.long
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.number
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.request
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.text
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckNamedVersionIntegrity.Companion.precedes
import kotlin.coroutines.cancellation.CancellationException

/** Durable complete named checkpoints and whole-manifest restore over immutable deck history. */
public class SqlLocalDeckVersions(db: Database, codec: DeckPayloadCodecPort, private val clock: Clock, private val ids: IdSource) : LocalDeckVersions {
    private val store = DeckSnapshotStore(db, codec)
    private val rows = DeckNamedVersionRows(store)
    private val integrity = DeckNamedVersionIntegrity(store, rows)
    private val undo = DeckUndoRows(store)

    public override suspend fun capture(id: DeckId, expected: Revision, label: String, operationId: EntityId, cancel: CancelToken): Outcome<DeckVersion> = store.operation(cancel) { tx ->
        input(id, operationId, label)
        val sha = checked(FailureCode.INVALID_INPUT) { DeckLibraryRequests.capture(id, expected, label) }
        replay(tx, id, expected, operationId, sha, "CAPTURE", cancel)?.let {
            return@operation integrity.version(tx, integrity.admit(tx, it, cancel), cancel)
        }
        writable(tx, cancel)
        val source = current(tx, id, cancel)
        cas(source, expected)
        integrity.current(tx, source, cancel)
        undo.state(tx, source, cancel)
        val latest = integrity.latest(tx, id, cancel)
        corrupt(latest == null || latest.receipt.revision <= source.revision.value)
        val now = now(source, latest)
        val versionId = generated(tx, cancel)
        val summary = DeckVersionSummary(versionId, id, label, now, expected, latest?.summary?.id, DeckVersionKind.USER, null)
        receipt(tx, source, operationId, sha, "CAPTURE", cancel)
        val receipt = rows.receipt(tx, operationId, cancel)!!
        rows.insert(tx, summary, receipt.sequence, cancel)
        integrity.version(summary, source)
    }

    public override suspend fun list(id: DeckId, page: PageRequest, cancel: CancelToken): Outcome<Page<DeckVersionSummary>> = store.operation(cancel) { tx ->
        id(id)
        val cursor = page.cursor?.let { checked(FailureCode.INVALID_INPUT) { DeckVersionFraming.cursor(it, id) } }
        val epoch = epoch(tx, cancel)
        if (cursor != null && cursor.epoch != epoch) reject(FailureCode.STALE_REVISION, "Deck version list changed")
        requireDeck(tx, id, cancel)
        integrity.latest(tx, id, cancel)
        if (cursor != null) {
            val anchor = rows.at(tx, id, cursor.afterSequence, cancel)
            if (anchor == null) {
                val retained = rows.receiptAt(tx, cursor.afterSequence, cancel)
                corrupt(retained == null || retained.deck != id || retained.kind !in setOf("CAPTURE", "RESTORE"))
                reject(FailureCode.INVALID_INPUT, "Invalid deck version cursor anchor")
            }
            integrity.admit(tx, anchor, cancel)
        }
        val selected = rows.select(tx, id, if (cursor == null) "" else "AND n.creating_sequence<:after",
            if (cursor == null) emptyArray() else arrayOf("after" to number(cursor.afterSequence)), cancel, page.limit + 1)
        selected.forEach { cancel.throwIfCancelled(); corrupt(it.sequence <= epoch); integrity.admit(tx, it, cancel) }
        val shown = selected.take(page.limit)
        Page(SnapshotList.copyOf(shown.map { it.summary }),
            if (selected.size > page.limit) DeckVersionFraming.cursor(epoch, shown.last().sequence, id) else null,
            SnapshotReference(LocalDeckLibraryLimits.SOURCE, null, Revision(epoch), null))
    }

    public override suspend fun load(id: DeckId, versionId: EntityId, cancel: CancelToken): Outcome<DeckVersion> = store.operation(cancel) { tx ->
        id(id); identifier(versionId.value)
        epoch(tx, cancel); requireDeck(tx, id, cancel); integrity.latest(tx, id, cancel)
        integrity.version(tx, integrity.admit(tx, selected(tx, id, versionId, cancel), cancel), cancel)
    }

    public override suspend fun previewRestore(id: DeckId, versionId: EntityId, expected: Revision, cancel: CancelToken): Outcome<DeckRestorePreview> = store.operation(cancel) { tx ->
        id(id); identifier(versionId.value)
        epoch(tx, cancel)
        val source = current(tx, id, cancel); cas(source, expected)
        integrity.current(tx, source, cancel)
        integrity.latest(tx, id, cancel)
        val target = integrity.version(tx, integrity.admit(tx, selected(tx, id, versionId, cancel), cancel), cancel)
        corrupt(target.manifest.createdAt == source.createdAt)
        DeckRestorePreview(source, target, value(DeckDocumentDiff.compare(source, target.manifest)))
    }

    public override suspend fun restore(id: DeckId, versionId: EntityId, expected: Revision, recoveryLabel: String, operationId: EntityId, cancel: CancelToken): Outcome<DeckRestoreResult> = store.operation(cancel) { tx ->
        input(id, operationId, recoveryLabel); identifier(versionId.value)
        val sha = checked(FailureCode.INVALID_INPUT) { DeckLibraryRequests.restore(id, expected, versionId, recoveryLabel) }
        replay(tx, id, expected, operationId, sha, "RESTORE", cancel)?.let {
            corrupt(it.summary.restoredTargetVersionId == versionId)
            val admitted = integrity.admit(tx, it, cancel)
            val recovery = integrity.version(tx, admitted, cancel)
            return@operation DeckRestoreResult(payload(tx, admitted.result!!, cancel), recovery)
        }
        writable(tx, cancel)
        val source = current(tx, id, cancel); cas(source, expected)
        integrity.current(tx, source, cancel)
        if (expected.value == Long.MAX_VALUE) reject(FailureCode.PERSISTENCE, "Deck revision is exhausted")
        val state = undo.state(tx, source, cancel)
        val latest = integrity.latest(tx, id, cancel)
        corrupt(latest == null || latest.receipt.revision <= source.revision.value)
        val target = integrity.version(tx, integrity.admit(tx, selected(tx, id, versionId, cancel), cancel), cancel)
        corrupt(target.manifest.createdAt == source.createdAt)
        val now = now(source, latest)
        val recoveryId = generated(tx, cancel)
        val candidate = target.manifest.copy(id = source.id, createdAt = source.createdAt, revision = Revision(expected.value + 1), modifiedAt = now)
        validate(candidate, FailureCode.CORRUPT_DATA, cancel)
        val encoded = encode(candidate)
        val recovery = DeckVersionSummary(recoveryId, id, recoveryLabel, now, expected, latest?.summary?.id, DeckVersionKind.RECOVERY, versionId)
        insertRevision(tx, candidate, encoded, cancel)
        receipt(tx, candidate, operationId, sha, "RESTORE", cancel)
        rows.insert(tx, recovery, rows.receipt(tx, operationId, cancel)!!.sequence, cancel)
        undo.append(tx, id, candidate.revision.value, expected.value, state.pointer, cancel)
        updateHead(tx, candidate, expected, cancel)
        undo.advance(tx, id, expected, candidate.revision, state, candidate.revision.value, cancel)
        DeckRestoreResult(candidate, integrity.version(recovery, source))
    }

    private fun input(id: DeckId, operation: EntityId, label: String) { store.id(id); store.identifier(operation.value); store.identifier(label) }
    private fun cas(source: DeckSnapshot, expected: Revision) { if (source.revision != expected) reject(FailureCode.STALE_REVISION, "Deck changed") }
    private suspend fun requireDeck(tx: DbTransaction, id: DeckId, cancel: CancelToken) {
        if (!store.exists(tx, id, cancel)) throw DeckLibraryProblem(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Deck not found"))
    }
    private suspend fun selected(tx: DbTransaction, id: DeckId, version: EntityId, cancel: CancelToken): DeckNamedVersionRows.Named =
        rows.find(tx, id, version, cancel) ?: throw DeckLibraryProblem(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Deck version not found"))

    private suspend fun replay(tx: DbTransaction, id: DeckId, expected: Revision, operation: EntityId, sha: String, kind: String, cancel: CancelToken): DeckNamedVersionRows.Named? {
        DeckOperationReservations.requireUnreserved(tx, operation, cancel)
        val receipt = rows.receipt(tx, operation, cancel) ?: return null
        if (receipt.sha != sha || receipt.kind != kind) reject(FailureCode.INVALID_INPUT, "Operation identity was reused with different input")
        corrupt(receipt.deck == id)
        if (kind == "RESTORE") corrupt(expected.value < Long.MAX_VALUE)
        corrupt(receipt.revision == if (kind == "CAPTURE") expected.value else expected.value + 1)
        val named = rows.at(tx, id, receipt.sequence, cancel)
        corrupt(named != null && named.receipt == receipt && named.summary.sourceRevision == expected)
        return named
    }

    private suspend fun writable(tx: DbTransaction, cancel: CancelToken) {
        val epoch = store.epoch(tx, cancel); store.writableEpoch(epoch)
        val sequence = value(tx.query(request("SELECT ${DeckLibraryRows.integer("seq", "sequence")} FROM sqlite_sequence WHERE name='deck_operation'"), cancel))
        if (sequence.isNotEmpty()) {
            val value = sequence.single().long("sequence"); corrupt(value >= epoch)
            store.writableEpoch(value)
        }
    }
    private fun now(source: DeckSnapshot, latest: DeckNamedVersionRows.Named?): UtcTimestamp {
        val now = try { clock.utcNow() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { reject(FailureCode.INTERNAL, "Deck clock failed") }
        if (now.precedes(source.createdAt) || now.precedes(source.modifiedAt) || latest != null && now.precedes(latest.summary.createdAt))
            reject(FailureCode.INTERNAL, "Deck clock moved backwards")
        return now
    }
    private suspend fun generated(tx: DbTransaction, cancel: CancelToken): EntityId {
        val raw = try { ids.nextId() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { reject(FailureCode.INTERNAL, "Deck identity source failed") }
        store.identifier(raw, FailureCode.INTERNAL)
        val exists = value(tx.query(request("SELECT 1 AS found FROM deck_named_version WHERE version_id=:version", "version" to text(raw)), cancel))
        if (exists.isNotEmpty()) reject(FailureCode.INTERNAL, "Generated version identity collision")
        return EntityId(raw)
    }
}
