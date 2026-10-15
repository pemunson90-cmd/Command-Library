package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.decks.DeckPatchReducer
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.column
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.long
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.utf8
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
    public override suspend fun create(initial: DeckSnapshot, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> = operation(cancel) { tx ->
        id(initial.id); identifier(operationId.value)
        if (initial.id.source != LocalDeckLibraryLimits.SOURCE || initial.revision.value != 0L)
            reject(FailureCode.INVALID_INPUT, "Create requires a local revision zero deck")
        validate(initial, FailureCode.INVALID_INPUT)
        val payload = encode(initial)
        val fingerprint = checked(FailureCode.INVALID_INPUT) { DeckLibraryRequests.create(payload) }
        replay(tx, operationId, fingerprint, "CREATE", cancel)?.let { return@operation it }
        val epoch = epoch(tx, cancel)
        writableEpoch(epoch)
        if (exists(tx, initial.id, cancel)) reject(FailureCode.INVALID_INPUT, "Deck identity already exists")
        persist(tx, initial, payload, operationId, fingerprint, "CREATE", cancel)
        initial
    }

    public override suspend fun load(id: DeckId, cancel: CancelToken): Outcome<DeckSnapshot> = operation(cancel) { tx ->
        id(id); epoch(tx, cancel)
        current(tx, id, cancel)
    }

    public override suspend fun duplicate(id: DeckId, expected: Revision, name: String, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> = operation(cancel) { tx ->
        id(id); identifier(operationId.value); identifier(name)
        val fingerprint = checked(FailureCode.INVALID_INPUT) { DeckLibraryRequests.duplicate(id, expected, name) }
        replay(tx, operationId, fingerprint, "DUPLICATE", cancel)?.let { return@operation it }
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

    public override suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<DeckSummary>> = operation(cancel) { tx ->
        val after = page.cursor?.let { checked(FailureCode.INVALID_INPUT) { DeckLibraryRequests.cursor(it) } }
        val epoch = epoch(tx, cancel)
        if (after != null && after.epoch != epoch) reject(FailureCode.STALE_REVISION, "Deck library changed")
        val predicate = if (after == null) "" else "AND h.deck_id COLLATE BINARY > :after"
        val parameters = mutableListOf("provider" to text(LocalDeckLibraryLimits.SOURCE.value), "limit" to number(page.limit.toLong() + 1))
        if (after != null) parameters += "after" to text(after.after)
        val rows = value(tx.query(request("SELECT $CURRENT_METADATA FROM deck_head h LEFT JOIN deck_revision r ON h.provider=r.provider AND h.deck_id=r.deck_id AND h.revision=r.revision WHERE h.provider=:provider $predicate ORDER BY h.deck_id COLLATE BINARY LIMIT :limit",
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

    private suspend fun current(tx: DbTransaction, id: DeckId, cancel: CancelToken): DeckSnapshot {
        val rows = value(tx.query(request("SELECT $CURRENT_METADATA FROM deck_head h LEFT JOIN deck_revision r ON h.provider=r.provider AND h.deck_id=r.deck_id AND h.revision=r.revision WHERE h.provider=:provider AND h.deck_id=:id", *keys(id), maxRows = 1), cancel))
        if (rows.isEmpty()) throw DeckLibraryProblem(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Deck not found"))
        val h = DeckLibraryRows.metadata(rows.single(), "h_", false)
        val r = DeckLibraryRows.metadata(rows.single(), "r_", true)
        corrupt(h.summary == r.summary && h.summary.id == id)
        return payload(tx, r, cancel)
    }

    private suspend fun replay(tx: DbTransaction, operationId: EntityId, sha: String, kind: String, cancel: CancelToken): DeckSnapshot? {
        val columns = listOf(DeckLibraryRows.integer("sequence", "sequence"), DeckLibraryRows.integer("request_version", "request_version"),
            DeckLibraryRows.integer("result_revision", "result_revision"), DeckLibraryRows.text("request_sha256", 64, "request_sha256"),
            DeckLibraryRows.text("kind", 16, "kind"), DeckLibraryRows.text("provider", 1024, "provider"), DeckLibraryRows.text("deck_id", 1024, "deck_id")).joinToString(",")
        val rows = value(tx.query(request("SELECT $columns FROM deck_operation WHERE operation_id=:operation", "operation" to text(operationId.value)), cancel))
        if (rows.isEmpty()) return null
        val receipt = rows.single()
        val storedSha = receipt.utf8("request_sha256")
        val storedKind = receipt.utf8("kind")
        val revision = receipt.long("result_revision")
        corrupt(receipt.long("sequence") > 0 && receipt.long("request_version") == 1L && revision >= 0 && storedSha.length == 64 && storedSha.all { it in "0123456789abcdef" } && storedKind in setOf("CREATE", "DUPLICATE"))
        if (storedSha != sha || storedKind != kind) reject(FailureCode.INVALID_INPUT, "Operation identity was reused with different input")
        val id = DeckId(ProviderId(receipt.utf8("provider")), receipt.utf8("deck_id"))
        corrupt(id.source == LocalDeckLibraryLimits.SOURCE)
        val metadata = value(tx.query(request("SELECT ${DeckLibraryRows.projection("r", "r_", true)} FROM deck_revision r WHERE r.provider=:provider AND r.deck_id=:id AND r.revision=:revision",
            *keys(id), "revision" to number(revision)), cancel))
        corrupt(metadata.size == 1)
        val selected = DeckLibraryRows.metadata(metadata.single(), "r_", true)
        corrupt(selected.summary.id == id && selected.summary.revision.value == revision)
        return payload(tx, selected, cancel)
    }

    private suspend fun payload(tx: DbTransaction, meta: DeckLibraryRows.Metadata, cancel: CancelToken): DeckSnapshot {
        val rows = value(tx.query(request("SELECT payload FROM deck_revision WHERE provider=:provider AND deck_id=:id AND revision=:revision",
            *keys(meta.summary.id), "revision" to number(meta.summary.revision.value)), cancel))
        corrupt(rows.size == 1)
        val payload = (rows.single().column("payload") as? SqlValue.Blob)?.value ?: reject(FailureCode.CORRUPT_DATA, "Invalid deck payload")
        corrupt(payload.size.toLong() == meta.bytes && DeckLibraryRequests.sha256(payload.copyBytes()) == meta.sha)
        val decoded = when (val result = codec.decodeSnapshot(payload)) {
            is Outcome.Success -> result.value
            is Outcome.Unavailable -> if (result.reason == UnavailableReason.UNSUPPORTED) throw DeckLibraryProblem(result) else reject(FailureCode.CORRUPT_DATA, "Invalid stored deck encoding")
            else -> reject(FailureCode.CORRUPT_DATA, "Invalid stored deck encoding")
        }
        corrupt(DeckLibraryRows.summary(decoded) == meta.summary)
        // Validate the common graph without granting package mutation authority or stripping
        // lineage from the returned value. The reducer explicitly declines package operations.
        validate(decoded.copy(packageInstances = SnapshotList.empty()), FailureCode.CORRUPT_DATA)
        return decoded
    }

    private suspend fun persist(tx: DbTransaction, snapshot: DeckSnapshot, payload: SnapshotBytes, operation: EntityId, sha: String, kind: String, cancel: CancelToken) {
        val summary = DeckLibraryRows.summary(snapshot)
        val parameters = keys(snapshot.id) + arrayOf("revision" to number(snapshot.revision.value), "name" to text(snapshot.name),
            "created_seconds" to number(snapshot.createdAt.epochSeconds), "created_nano" to number(snapshot.createdAt.nanoAdjustment.toLong()),
            "modified_seconds" to number(snapshot.modifiedAt.epochSeconds), "modified_nano" to number(snapshot.modifiedAt.nanoAdjustment.toLong()),
            "entry_count" to number(summary.entryCount.toLong()), "total_quantity" to number(summary.totalQuantity))
        val columns = "provider,deck_id,revision,name,created_seconds,created_nano,modified_seconds,modified_nano,entry_count,total_quantity"
        val bindings = ":provider,:id,:revision,:name,:created_seconds,:created_nano,:modified_seconds,:modified_nano,:entry_count,:total_quantity"
        write(tx, request("INSERT INTO deck_head ($columns) VALUES ($bindings)", *parameters), cancel)
        write(tx, request("INSERT INTO deck_revision ($columns,payload_bytes,payload_sha256,payload) VALUES ($bindings,:bytes,:sha,:payload)",
            *parameters, "bytes" to number(payload.size.toLong()), "sha" to text(DeckLibraryRequests.sha256(payload.copyBytes())), "payload" to SqlValue.Blob(payload)), cancel)
        write(tx, request("INSERT INTO deck_operation (operation_id,request_version,request_sha256,kind,provider,deck_id,result_revision) VALUES (:operation,1,:sha,:kind,:provider,:id,:revision)",
            *keys(snapshot.id), "operation" to text(operation.value), "sha" to text(sha), "kind" to text(kind), "revision" to number(snapshot.revision.value)), cancel)
    }

    private suspend fun write(tx: DbTransaction, request: SqlRequest, cancel: CancelToken) {
        if (value(tx.execute(request, cancel)).affectedRows != 1L) reject(FailureCode.PERSISTENCE, "Deck write did not affect exactly one row")
    }
    private suspend fun exists(tx: DbTransaction, id: DeckId, cancel: CancelToken): Boolean = value(tx.query(request("SELECT 1 AS found FROM deck_head WHERE provider=:provider AND deck_id=:id", *keys(id)), cancel)).isNotEmpty()
    private suspend fun epoch(tx: DbTransaction, cancel: CancelToken): Long {
        val row = value(tx.query(request("SELECT MAX(sequence) AS epoch, EXISTS(SELECT 1 FROM deck_head) AS heads, EXISTS(SELECT 1 FROM deck_revision) AS revisions FROM deck_operation"), cancel)).single()
        if (row.column("epoch") == SqlValue.Null) {
            corrupt(row.long("heads") == 0L && row.long("revisions") == 0L)
            return 0
        }
        return row.long("epoch").also { corrupt(it > 0) }
    }
    private fun writableEpoch(epoch: Long) { if (epoch == Long.MAX_VALUE) reject(FailureCode.PERSISTENCE, "Deck operation sequence is exhausted") }
    private fun validate(snapshot: DeckSnapshot, code: FailureCode) {
        identifier(snapshot.name, code); id(snapshot.id, code)
        val result = DeckPatchReducer.propose(snapshot, DeckPatch(SnapshotList.empty(), ""), snapshot.revision, snapshot.modifiedAt)
        when (result) {
            is Outcome.Success -> Unit
            is Outcome.Unavailable -> throw DeckLibraryProblem(result)
            else -> reject(code, "Invalid deck graph")
        }
    }
    private fun encode(snapshot: DeckSnapshot): SnapshotBytes = value(codec.encodeSnapshot(snapshot)).also {
        if (it.size !in 1..DeckPayloadLimits.MAX_ENCODED_BYTES) reject(FailureCode.INVALID_INPUT, "Deck payload exceeds byte budget")
    }
    private fun identifier(raw: String, code: FailureCode = FailureCode.INVALID_INPUT) { checked(code) { DeckLibraryRequests.utf8(raw, 1024) } }
    private fun id(id: DeckId, code: FailureCode = FailureCode.INVALID_INPUT) { identifier(id.source.value, code); identifier(id.raw, code) }
    private fun generated(): String {
        val raw = try { ids.nextId() } catch (e: CancellationException) { throw e } catch (e: Exception) { reject(FailureCode.INTERNAL, "Deck identity source failed") }
        identifier(raw, FailureCode.INTERNAL)
        return raw
    }
    private fun keys(id: DeckId): Array<Pair<String, SqlValue>> = arrayOf("provider" to text(id.source.value), "id" to text(id.raw))
    private suspend fun <T> operation(cancel: CancelToken, action: suspend (DbTransaction) -> T): Outcome<T> {
        cancel.throwIfCancelled()
        return when (val result = db.transaction({ tx ->
            try { Outcome.Success(action(tx)) } catch (problem: DeckLibraryProblem) { problem.status }
        }, cancel)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> result
            is Outcome.Unavailable -> result
            is Outcome.Unknown -> result
        }
    }
    private companion object {
        val CURRENT_METADATA = DeckLibraryRows.projection("h", "h_", false) + "," + DeckLibraryRows.projection("r", "r_", true)
    }
}
