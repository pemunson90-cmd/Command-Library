package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.column
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.long
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.number
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.request
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.text
import com.architectpsg.commandlibrary.feature.decks.persistence.DeckLibraryRows.utf8

/** Allocation-bounded metadata/receipt access. No historical payload is selected here. */
internal class DeckNamedVersionRows(private val store: DeckSnapshotStore) {
    data class Receipt(val sequence: Long, val operation: EntityId, val deck: DeckId, val revision: Long, val kind: String, val sha: String)
    data class Named(val summary: DeckVersionSummary, val sequence: Long, val receipt: Receipt)

    private fun nullable(column: String, alias: String): String =
        "${DeckLibraryRows.text(column, 1024, alias)},CASE WHEN $column IS NULL THEN 1 ELSE 0 END AS ${alias}_null"
    private fun RowData.optional(name: String): EntityId? = if (long("${name}_null") == 1L) {
        corrupt(column(name) == SqlValue.Null); null
    } else EntityId(utf8(name))

    private val receiptProjection = listOf(
        DeckLibraryRows.integer("o.sequence", "o_sequence"), DeckLibraryRows.integer("o.request_version", "o_version"),
        DeckLibraryRows.integer("o.result_revision", "o_revision"), DeckLibraryRows.text("o.operation_id", 1024, "o_operation"),
        DeckLibraryRows.text("o.provider", 1024, "o_provider"), DeckLibraryRows.text("o.deck_id", 1024, "o_deck"),
        DeckLibraryRows.text("o.kind", 16, "o_kind"), DeckLibraryRows.text("o.request_sha256", 64, "o_sha")
    ).joinToString(",")
    private val projection = listOf(
        DeckLibraryRows.integer("n.metadata_version", "version"), DeckLibraryRows.integer("n.creating_sequence", "sequence"),
        DeckLibraryRows.integer("n.source_revision", "source"), DeckLibraryRows.integer("n.created_seconds", "seconds"),
        DeckLibraryRows.integer("n.created_nano", "nano"), DeckLibraryRows.text("n.version_id", 1024, "identity"),
        DeckLibraryRows.text("n.provider", 1024, "provider"), DeckLibraryRows.text("n.deck_id", 1024, "deck"),
        DeckLibraryRows.text("n.label", 1024, "label"), nullable("n.predecessor_version_id", "predecessor"),
        nullable("n.restored_target_version_id", "target"), DeckLibraryRows.text("n.kind", 16, "kind"),
        DeckLibraryRows.text("n.metadata_sha256", 64, "sha"), receiptProjection
    ).joinToString(",")

    private fun receipt(row: RowData): Receipt {
        val sequence = row.long("o_sequence"); val revision = row.long("o_revision")
        val sha = row.utf8("o_sha"); val kind = row.utf8("o_kind")
        corrupt(sequence > 0 && revision >= 0 && row.long("o_version") == 1L)
        corrupt(sha.length == 64 && sha.all { it in "0123456789abcdef" } && kind in DeckSnapshotStore.RECEIPT_KINDS)
        val deck = DeckId(ProviderId(row.utf8("o_provider")), row.utf8("o_deck"))
        corrupt(deck.source == LocalDeckLibraryLimits.SOURCE)
        return Receipt(sequence, EntityId(row.utf8("o_operation")), deck, revision, kind, sha)
    }

    private fun named(row: RowData): Named {
        val version = row.long("version")
        corrupt(version > 0)
        if (version > 1) throw DeckLibraryProblem(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Newer named deck metadata"))
        val source = row.long("source"); val nano = row.long("nano"); val sequence = row.long("sequence")
        corrupt(source >= 0 && sequence > 0 && nano in 0..999_999_999)
        val kind = when (row.utf8("kind")) {
            "USER" -> DeckVersionKind.USER
            "RECOVERY" -> DeckVersionKind.RECOVERY
            else -> reject(FailureCode.CORRUPT_DATA, "Invalid named deck kind")
        }
        val summary = DeckVersionSummary(EntityId(row.utf8("identity")), DeckId(ProviderId(row.utf8("provider")), row.utf8("deck")),
            row.utf8("label"), UtcTimestamp(row.long("seconds"), nano.toInt()), Revision(source), row.optional("predecessor"), kind, row.optional("target"))
        corrupt(summary.deckId.source == LocalDeckLibraryLimits.SOURCE)
        val expected = checked(FailureCode.CORRUPT_DATA) { DeckVersionFraming.metadata(summary, sequence) }
        corrupt(row.utf8("sha") == expected)
        val receipt = receipt(row)
        corrupt(receipt.sequence == sequence && receipt.deck == summary.deckId)
        corrupt(receipt.kind == if (kind == DeckVersionKind.USER) "CAPTURE" else "RESTORE")
        corrupt(receipt.revision == if (kind == DeckVersionKind.USER) source else source + 1)
        corrupt(receipt.sha == checked(FailureCode.CORRUPT_DATA) { DeckVersionFraming.requestFingerprint(summary) })
        return Named(summary, sequence, receipt)
    }

    suspend fun receipt(tx: DbTransaction, operation: EntityId, cancel: CancelToken): Receipt? =
        value(tx.query(request("SELECT $receiptProjection FROM deck_operation o WHERE o.operation_id=:operation", "operation" to text(operation.value)), cancel))
            .singleOrNull()?.let(::receipt)

    suspend fun receiptAt(tx: DbTransaction, sequence: Long, cancel: CancelToken): Receipt? =
        value(tx.query(request("SELECT $receiptProjection FROM deck_operation o WHERE o.sequence=:sequence", "sequence" to number(sequence)), cancel))
            .singleOrNull()?.let(::receipt)

    suspend fun latestReceipt(tx: DbTransaction, id: DeckId, kind: String, cancel: CancelToken): Receipt? =
        value(tx.query(request("SELECT $receiptProjection FROM deck_operation o WHERE o.provider=:provider AND o.deck_id=:id AND o.kind=:kind ORDER BY o.sequence DESC LIMIT 1", *store.keys(id), "kind" to text(kind)), cancel))
            .singleOrNull()?.let(::receipt)

    suspend fun select(tx: DbTransaction, id: DeckId, predicate: String, parameters: Array<Pair<String, SqlValue>>, cancel: CancelToken, limit: Int = 1): List<Named> =
        value(tx.query(request("SELECT $projection FROM deck_named_version n LEFT JOIN deck_operation o ON o.sequence=n.creating_sequence WHERE n.provider=:provider AND n.deck_id=:id $predicate ORDER BY n.creating_sequence DESC LIMIT :limit",
            *store.keys(id), *parameters, "limit" to number(limit.toLong()), maxRows = limit), cancel)).map { cancel.throwIfCancelled(); named(it).also { n -> corrupt(n.summary.deckId == id) } }

    suspend fun find(tx: DbTransaction, id: DeckId, version: EntityId, cancel: CancelToken): Named? =
        select(tx, id, "AND n.version_id=:version", arrayOf("version" to text(version.value)), cancel).singleOrNull()
    suspend fun at(tx: DbTransaction, id: DeckId, sequence: Long, cancel: CancelToken): Named? =
        select(tx, id, "AND n.creating_sequence=:sequence", arrayOf("sequence" to number(sequence)), cancel).singleOrNull()
    suspend fun previous(tx: DbTransaction, named: Named, cancel: CancelToken): Named? =
        select(tx, named.summary.deckId, "AND n.creating_sequence<:sequence", arrayOf("sequence" to number(named.sequence)), cancel).singleOrNull()

    suspend fun insert(tx: DbTransaction, summary: DeckVersionSummary, sequence: Long, cancel: CancelToken) = with(store) {
        write(tx, request("INSERT INTO deck_named_version (version_id,metadata_version,provider,deck_id,label,created_seconds,created_nano,source_revision,creating_sequence,predecessor_version_id,restored_target_version_id,kind,metadata_sha256) VALUES (:version,1,:provider,:id,:label,:seconds,:nano,:source,:sequence,:predecessor,:target,:kind,:sha)",
            *keys(summary.deckId), "version" to text(summary.id.value), "label" to text(summary.label), "seconds" to number(summary.createdAt.epochSeconds),
            "nano" to number(summary.createdAt.nanoAdjustment.toLong()), "source" to number(summary.sourceRevision.value), "sequence" to number(sequence),
            "predecessor" to (summary.predecessorVersionId?.let { text(it.value) } ?: SqlValue.Null),
            "target" to (summary.restoredTargetVersionId?.let { text(it.value) } ?: SqlValue.Null),
            "kind" to text(if (summary.kind == DeckVersionKind.USER) "USER" else "RECOVERY"), "sha" to text(DeckVersionFraming.metadata(summary, sequence))), cancel)
    }
}
