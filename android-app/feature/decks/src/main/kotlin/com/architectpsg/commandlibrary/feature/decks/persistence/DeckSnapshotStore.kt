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

/** Shared bounded immutable snapshot and globally unique receipt access. */
internal class DeckSnapshotStore(private val db: Database, private val codec: DeckPayloadCodecPort) {
    suspend fun current(tx: DbTransaction, id: DeckId, cancel: CancelToken): DeckSnapshot {
        val rows = value(tx.query(request("SELECT $CURRENT_METADATA FROM deck_head h LEFT JOIN deck_revision r ON h.provider=r.provider AND h.deck_id=r.deck_id AND h.revision=r.revision WHERE h.provider=:provider AND h.deck_id=:id", *keys(id), maxRows = 1), cancel))
        if (rows.isEmpty()) throw DeckLibraryProblem(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Deck not found"))
        val h = DeckLibraryRows.metadata(rows.single(), "h_", false)
        val r = DeckLibraryRows.metadata(rows.single(), "r_", true)
        corrupt(h.summary == r.summary && h.summary.id == id)
        return payload(tx, r, cancel)
    }

    suspend fun replay(tx: DbTransaction, operationId: EntityId, sha: String, kind: String, cancel: CancelToken): DeckSnapshot? {
        val columns = listOf(DeckLibraryRows.integer("sequence", "sequence"), DeckLibraryRows.integer("request_version", "request_version"),
            DeckLibraryRows.integer("result_revision", "result_revision"), DeckLibraryRows.text("request_sha256", 64, "request_sha256"),
            DeckLibraryRows.text("kind", 16, "kind"), DeckLibraryRows.text("provider", 1024, "provider"), DeckLibraryRows.text("deck_id", 1024, "deck_id")).joinToString(",")
        val rows = value(tx.query(request("SELECT $columns FROM deck_operation WHERE operation_id=:operation", "operation" to text(operationId.value)), cancel))
        if (rows.isEmpty()) return null
        val receipt = rows.single()
        val storedSha = receipt.utf8("request_sha256")
        val storedKind = receipt.utf8("kind")
        val revision = receipt.long("result_revision")
        corrupt(receipt.long("sequence") > 0 && receipt.long("request_version") == 1L && revision >= 0 && storedSha.length == 64 && storedSha.all { it in "0123456789abcdef" } && storedKind in RECEIPT_KINDS)
        if (storedSha != sha || storedKind != kind) reject(FailureCode.INVALID_INPUT, "Operation identity was reused with different input")
        val id = DeckId(ProviderId(receipt.utf8("provider")), receipt.utf8("deck_id"))
        corrupt(id.source == LocalDeckLibraryLimits.SOURCE)
        return historical(tx, id, revision, cancel)
    }

    suspend fun historical(tx: DbTransaction, id: DeckId, revision: Long, cancel: CancelToken): DeckSnapshot {
        val metadata = value(tx.query(request("SELECT ${DeckLibraryRows.projection("r", "r_", true)} FROM deck_revision r WHERE r.provider=:provider AND r.deck_id=:id AND r.revision=:revision",
            *keys(id), "revision" to number(revision)), cancel))
        corrupt(metadata.size == 1)
        val selected = DeckLibraryRows.metadata(metadata.single(), "r_", true)
        corrupt(selected.summary.id == id && selected.summary.revision.value == revision)
        return payload(tx, selected, cancel)
    }

    suspend fun payload(tx: DbTransaction, meta: DeckLibraryRows.Metadata, cancel: CancelToken): DeckSnapshot {
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

    fun parameters(snapshot: DeckSnapshot): Array<Pair<String, SqlValue>> {
        val summary = DeckLibraryRows.summary(snapshot)
        return keys(snapshot.id) + arrayOf("revision" to number(snapshot.revision.value), "name" to text(snapshot.name),
            "created_seconds" to number(snapshot.createdAt.epochSeconds), "created_nano" to number(snapshot.createdAt.nanoAdjustment.toLong()),
            "modified_seconds" to number(snapshot.modifiedAt.epochSeconds), "modified_nano" to number(snapshot.modifiedAt.nanoAdjustment.toLong()),
            "entry_count" to number(summary.entryCount.toLong()), "total_quantity" to number(summary.totalQuantity))
    }
    suspend fun persist(tx: DbTransaction, snapshot: DeckSnapshot, payload: SnapshotBytes, operation: EntityId, sha: String, kind: String, cancel: CancelToken) {
        write(tx, request("INSERT INTO deck_head ($COLUMNS) VALUES ($BINDINGS)", *parameters(snapshot)), cancel)
        insertRevision(tx, snapshot, payload, cancel)
        receipt(tx, snapshot, operation, sha, kind, cancel)
    }
    suspend fun insertRevision(tx: DbTransaction, snapshot: DeckSnapshot, payload: SnapshotBytes, cancel: CancelToken) {
        write(tx, request("INSERT INTO deck_revision ($COLUMNS,payload_bytes,payload_sha256,payload) VALUES ($BINDINGS,:bytes,:sha,:payload)",
            *parameters(snapshot), "bytes" to number(payload.size.toLong()), "sha" to text(DeckLibraryRequests.sha256(payload.copyBytes())), "payload" to SqlValue.Blob(payload)), cancel)
    }
    suspend fun updateHead(tx: DbTransaction, snapshot: DeckSnapshot, expected: Revision, cancel: CancelToken) {
        write(tx, request("UPDATE deck_head SET revision=:revision,name=:name,created_seconds=:created_seconds,created_nano=:created_nano,modified_seconds=:modified_seconds,modified_nano=:modified_nano,entry_count=:entry_count,total_quantity=:total_quantity WHERE provider=:provider AND deck_id=:id AND revision=:expected",
            *parameters(snapshot), "expected" to number(expected.value)), cancel)
    }
    suspend fun receipt(tx: DbTransaction, snapshot: DeckSnapshot, operation: EntityId, sha: String, kind: String, cancel: CancelToken) {
        write(tx, request("INSERT INTO deck_operation (operation_id,request_version,request_sha256,kind,provider,deck_id,result_revision) VALUES (:operation,1,:sha,:kind,:provider,:id,:revision)",
            *keys(snapshot.id), "operation" to text(operation.value), "sha" to text(sha), "kind" to text(kind), "revision" to number(snapshot.revision.value)), cancel)
    }

    suspend fun write(tx: DbTransaction, request: SqlRequest, cancel: CancelToken) {
        if (value(tx.execute(request, cancel)).affectedRows != 1L) reject(FailureCode.PERSISTENCE, "Deck write did not affect exactly one row")
    }
    suspend fun exists(tx: DbTransaction, id: DeckId, cancel: CancelToken): Boolean = value(tx.query(request("SELECT 1 AS found FROM deck_head WHERE provider=:provider AND deck_id=:id", *keys(id)), cancel)).isNotEmpty()
    suspend fun epoch(tx: DbTransaction, cancel: CancelToken): Long {
        val row = value(tx.query(request("SELECT MAX(sequence) AS epoch, EXISTS(SELECT 1 FROM deck_head) AS heads, EXISTS(SELECT 1 FROM deck_revision) AS revisions FROM deck_operation"), cancel)).single()
        if (row.column("epoch") == SqlValue.Null) {
            corrupt(row.long("heads") == 0L && row.long("revisions") == 0L)
            return 0
        }
        return row.long("epoch").also { corrupt(it > 0) }
    }
    fun writableEpoch(epoch: Long) { if (epoch == Long.MAX_VALUE) reject(FailureCode.PERSISTENCE, "Deck operation sequence is exhausted") }
    fun validate(snapshot: DeckSnapshot, code: FailureCode) {
        identifier(snapshot.name, code); id(snapshot.id, code)
        val result = DeckPatchReducer.propose(snapshot, DeckPatch(SnapshotList.empty(), ""), snapshot.revision, snapshot.modifiedAt)
        when (result) {
            is Outcome.Success -> Unit
            is Outcome.Unavailable -> throw DeckLibraryProblem(result)
            else -> reject(code, "Invalid deck graph")
        }
    }
    fun encode(snapshot: DeckSnapshot): SnapshotBytes = value(codec.encodeSnapshot(snapshot)).also {
        if (it.size !in 1..DeckPayloadLimits.MAX_ENCODED_BYTES) reject(FailureCode.INVALID_INPUT, "Deck payload exceeds byte budget")
    }
    fun identifier(raw: String, code: FailureCode = FailureCode.INVALID_INPUT) { checked(code) { DeckLibraryRequests.utf8(raw, 1024) } }
    fun id(id: DeckId, code: FailureCode = FailureCode.INVALID_INPUT) { identifier(id.source.value, code); identifier(id.raw, code) }
    fun keys(id: DeckId): Array<Pair<String, SqlValue>> = arrayOf("provider" to text(id.source.value), "id" to text(id.raw))
    suspend fun <T> operation(cancel: CancelToken, action: suspend DeckSnapshotStore.(DbTransaction) -> T): Outcome<T> {
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
    companion object {
        const val COLUMNS = "provider,deck_id,revision,name,created_seconds,created_nano,modified_seconds,modified_nano,entry_count,total_quantity"
        const val BINDINGS = ":provider,:id,:revision,:name,:created_seconds,:created_nano,:modified_seconds,:modified_nano,:entry_count,:total_quantity"
        val RECEIPT_KINDS = setOf("CREATE", "DUPLICATE", "APPLY", "UNDO")
        val CURRENT_METADATA = DeckLibraryRows.projection("h", "h_", false) + "," + DeckLibraryRows.projection("r", "r_", true)
    }
}
