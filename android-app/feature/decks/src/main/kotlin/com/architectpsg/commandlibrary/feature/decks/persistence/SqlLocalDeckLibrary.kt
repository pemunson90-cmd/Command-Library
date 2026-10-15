package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.number
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.request
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.text
import kotlin.coroutines.cancellation.CancellationException

/** Immutable local snapshots and durable operation receipts; no edit/version/package authority. */
public class SqlLocalDeckLibrary(
    private val db: Database,
    private val codec: DeckPayloadCodecPort,
    private val clock: Clock,
    private val ids: IdSource,
) : LocalDeckLibrary {
    private val store = DeckSnapshotStore(db, codec)
    public override suspend fun create(initial: DeckSnapshot, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> = store.operation(cancel) { tx ->
        id(initial.id); identifier(operationId.value)
        if (initial.id.source != LocalDeckLibraryLimits.SOURCE || initial.revision.value != 0L)
            reject(FailureCode.INVALID_INPUT, "Create requires a local revision zero deck")
        validate(initial, FailureCode.INVALID_INPUT)
        val payload = encode(initial)
        val fingerprint = checked(FailureCode.INVALID_INPUT) { DeckLibraryRequests.create(payload) }
        replay(tx, operationId, fingerprint, "CREATE", cancel)?.let {
            corrupt(it == initial)
            return@operation it
        }
        val epoch = epoch(tx, cancel)
        writableEpoch(epoch)
        if (exists(tx, initial.id, cancel)) reject(FailureCode.INVALID_INPUT, "Deck identity already exists")
        persist(tx, initial, payload, operationId, fingerprint, "CREATE", cancel)
        initial
    }

    public override suspend fun load(id: DeckId, cancel: CancelToken): Outcome<DeckSnapshot> = store.operation(cancel) { tx ->
        id(id); epoch(tx, cancel)
        current(tx, id, cancel)
    }

    public override suspend fun duplicate(id: DeckId, expected: Revision, name: String, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> = store.operation(cancel) { tx ->
        id(id); identifier(operationId.value); identifier(name)
        val fingerprint = checked(FailureCode.INVALID_INPUT) { DeckLibraryRequests.duplicate(id, expected, name) }
        replay(tx, operationId, fingerprint, "DUPLICATE", cancel)?.let {
            corrupt(it.id.source == LocalDeckLibraryLimits.SOURCE && it.id != id && it.revision.value == 0L && it.name == name)
            return@operation it
        }
        writableEpoch(epoch(tx, cancel))
        val source = current(tx, id, cancel)
        if (source.revision != expected) reject(FailureCode.STALE_REVISION, "Source deck changed")
        // Validate source before consuming generated values. Packages remain explicitly unsupported.
        validate(source, FailureCode.CORRUPT_DATA)
        val generatedDeck = DeckId(LocalDeckLibraryLimits.SOURCE, generated())
        if (generatedDeck == id || exists(tx, generatedDeck, cancel)) reject(FailureCode.INTERNAL, "Generated deck identity collision")
        val oldEntries = source.entries.map { it.id.value }.toSet()
        val newEntries = mutableSetOf<String>()
        val remap = source.entries.associate { entry ->
            val raw = generated()
            if (raw in oldEntries || !newEntries.add(raw)) reject(FailureCode.INTERNAL, "Generated entry identity collision")
            entry.id to EntityId(raw)
        }
        val now = try { clock.utcNow() } catch (e: CancellationException) { throw e } catch (e: Exception) {
            reject(FailureCode.INTERNAL, "Deck clock failed")
        }
        val candidate = source.copy(id = generatedDeck, name = name, revision = Revision(0), createdAt = now, modifiedAt = now,
            entries = SnapshotList.copyOf(source.entries.map { entry -> entry.copy(id = remap.getValue(entry.id),
                roleOverrides = SnapshotList.copyOf(entry.roleOverrides.map { override ->
                    override.copy(entryId = override.entryId?.let { remap.getValue(it) })
                })) }))
        validate(candidate, FailureCode.INTERNAL)
        persist(tx, candidate, encode(candidate), operationId, fingerprint, "DUPLICATE", cancel)
        candidate
    }

    public override suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<DeckSummary>> = store.operation(cancel) { tx ->
        val after = page.cursor?.let { checked(FailureCode.INVALID_INPUT) { DeckLibraryRequests.cursor(it) } }
        val epoch = epoch(tx, cancel)
        if (after != null && after.epoch != epoch) reject(FailureCode.STALE_REVISION, "Deck library changed")
        val predicate = if (after == null) "" else "AND h.deck_id COLLATE BINARY > :after"
        val parameters = mutableListOf("provider" to text(LocalDeckLibraryLimits.SOURCE.value), "limit" to number(page.limit.toLong() + 1))
        if (after != null) parameters += "after" to text(after.after)
        val rows = value(tx.query(request("SELECT ${DeckSnapshotStore.CURRENT_METADATA} FROM deck_head h LEFT JOIN deck_revision r ON h.provider=r.provider AND h.deck_id=r.deck_id AND h.revision=r.revision WHERE h.provider=:provider $predicate ORDER BY h.deck_id COLLATE BINARY LIMIT :limit",
            *parameters.toTypedArray(), maxRows = page.limit + 1), cancel))
        val summaries = rows.map { row ->
            val h = DeckLibraryRows.metadata(row, "h_", false)
            val r = DeckLibraryRows.metadata(row, "r_", true)
            corrupt(h.summary == r.summary && h.summary.id.source == LocalDeckLibraryLimits.SOURCE)
            h.summary
        }
        val selected = summaries.take(page.limit)
        Page(SnapshotList.copyOf(selected), if (summaries.size > page.limit) DeckLibraryRequests.cursor(epoch, selected.last().id.raw) else null,
            SnapshotReference(LocalDeckLibraryLimits.SOURCE, null, Revision(epoch), null))
    }

    private fun generated(): String {
        val raw = try { ids.nextId() } catch (e: CancellationException) { throw e } catch (e: Exception) { reject(FailureCode.INTERNAL, "Deck identity source failed") }
        store.identifier(raw, FailureCode.INTERNAL)
        return raw
    }
}
