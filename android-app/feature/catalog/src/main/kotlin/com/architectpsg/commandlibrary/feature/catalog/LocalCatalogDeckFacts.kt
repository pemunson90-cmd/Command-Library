package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CancellationException

/** Read-only, generation-qualified facts from actual installed canonical and printing projections. */
internal class LocalCatalogDeckFacts(private val database: Database) : CatalogDeckFactsLookup {
    override suspend fun facts(oracleId: OracleId, printingId: CardId?, snapshot: SnapshotReference,
        cancel: CancelToken): Outcome<CatalogDeckFacts> {
        val admission = bulkAttempt {
            cancel.throwIfCancelled()
            if (snapshot.source != SCRYFALL || oracleId.source != SCRYFALL ||
                printingId?.source?.let { it != SCRYFALL } == true)
                throw BulkFailure(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Catalog source is not supported"))
            if (snapshot.generation == null || snapshot.revision != null || snapshot.language != LanguageTag.ENGLISH)
                throw BulkFailure(Outcome.Failure(bulkError(FailureCode.INVALID_INPUT, "An exact English catalog snapshot is required")))
            identity(oracleId.raw, cancel)
            printingId?.let { identity(it.raw, cancel) }
            active(snapshot, false, cancel)
        }
        if (admission !is Outcome.Success) return unsuccessful(admission)
        val read = bulkAttempt {
            val oracleRaw = raw(oracleId, null, snapshot, cancel)
            val oracleObject = CatalogDeckFactsFields.parse(oracleRaw, cancel)
            CatalogDeckFactsFields.preflight(oracleObject, SCRYFALL, cancel)
            val selectedRaw = if (printingId == null) oracleRaw else raw(oracleId, printingId, snapshot, cancel)
            val selectedObject = if (printingId == null) oracleObject else CatalogDeckFactsFields.parse(selectedRaw, cancel)
            if (printingId != null) CatalogDeckFactsFields.preflight(selectedObject, SCRYFALL, cancel)
            // Preflight publishes nothing. These actual readers validate retained projection/digest/relationships.
            val lookup = LocalCatalogIdentities(database)
            val card = lookup.oracle(oracleId, cancel).bulkValue()
            require(card.oracleId == oracleId && card.language == LanguageTag.ENGLISH &&
                card.provenance.provider == SCRYFALL && card.provenance.generation == snapshot.generation)
            if (printingId != null) {
                val printing = lookup.printing(printingId, cancel).bulkValue()
                val target = printing.oracleId.bulkKnown() ?: throw BulkFailure(Outcome.Unavailable(
                    UnavailableReason.UNSUPPORTED, "Printing has no unique whole-card Oracle identity"))
                require(target == oracleId && printing.id == printingId && printing.language == LanguageTag.ENGLISH &&
                    printing.provenance.provider == SCRYFALL && printing.provenance.generation == snapshot.generation)
            }
            cancel.throwIfCancelled()
            // Source rows are immutable by policy. Fail if retained bytes changed across validation nonetheless.
            require(raw(oracleId, null, snapshot, cancel) == oracleRaw)
            if (printingId != null) require(raw(oracleId, printingId, snapshot, cancel) == selectedRaw)
            CatalogDeckFacts(card, snapshot, printingId, CatalogDeckFactsFields.production(selectedObject, cancel),
                CatalogDeckFactsFields.parts(selectedObject, SCRYFALL, cancel))
        }
        // Check even when a racing identity read failed: changed generations must not masquerade as missing facts.
        val final = bulkAttempt { active(snapshot, true, cancel) }
        return if (final is Outcome.Success) read else unsuccessful(final)
    }

    private suspend fun active(snapshot: SnapshotReference, hadActive: Boolean, cancel: CancelToken) {
        cancel.throwIfCancelled()
        val row = database.query(bulkSql("SELECT g.* FROM catalog_active a JOIN catalog_generation g ON g.generation_id=a.generation_id WHERE a.dataset_key=:key",
            1, "key" to BULK_DATASET), cancel).bulkValue().firstOrNull()
        if (row == null) {
            if (hadActive) stale()
            throw BulkFailure(Outcome.Unavailable(UnavailableReason.NOT_INSTALLED, "No English catalog installed"))
        }
        if (row.bulkText("generation_id") != snapshot.generation?.value) stale()
        val source = row.bulkInstalledSource()
        if (source.source.provider != SCRYFALL) throw BulkFailure(Outcome.Unavailable(
            UnavailableReason.UNSUPPORTED, "Catalog source is not supported"))
        cancel.throwIfCancelled()
    }

    private suspend fun raw(oracle: OracleId, printing: CardId?, snapshot: SnapshotReference, cancel: CancelToken): String {
        cancel.throwIfCancelled()
        val table = if (printing == null) "oracle_card" else "printing"
        val column = if (printing == null) "record_payload" else "metadata_payload"
        val key = if (printing == null) "oracle_id" else "printing_id"
        val language = if (printing == null) " AND language='en'" else ""
        val parameters = arrayOf("g" to snapshot.generation!!.value, "p" to SCRYFALL.value, "id" to (printing?.raw ?: oracle.raw))
        val predicate = "generation_id=:g AND provider=:p AND $key=:id$language"
        val size = database.query(bulkSql("SELECT length(CAST($column AS BLOB)) AS bytes FROM $table WHERE $predicate",
            1, *parameters), cancel).bulkValue().firstOrNull() ?: throw BulkFailure(Outcome.Unavailable(
            UnavailableReason.NOT_FOUND, "Catalog identity is not installed"))
        require(size.bulkLong("bytes") in 1..DeckStatisticsLimits.MAX_RAW_RECORD_BYTES.toLong())
        cancel.throwIfCancelled()
        val row = database.query(bulkSql("SELECT $column AS raw FROM $table WHERE $predicate AND length(CAST($column AS BLOB))<=:maximum",
            1, *parameters, "maximum" to DeckStatisticsLimits.MAX_RAW_RECORD_BYTES), cancel).bulkValue().single()
        return row.bulkText("raw").also { CatalogDeckFactsFields.textBytes(it, DeckStatisticsLimits.MAX_RAW_RECORD_BYTES, cancel) }
    }
    private fun identity(value: String, cancel: CancelToken) {
        try {
            CatalogDeckFactsFields.textBytes(value, 128, cancel)
            require(value.isNotBlank() && '\u0000' !in value)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { throw BulkFailure(Outcome.Failure(
            bulkError(FailureCode.INVALID_INPUT, "Invalid catalog identity"))) }
    }
    private fun stale(): Nothing = throw BulkFailure(Outcome.Failure(bulkError(FailureCode.STALE_GENERATION,
        "Catalog generation changed")))
    private fun <T> unsuccessful(result: Outcome<*>): Outcome<T> = when (result) {
        is Outcome.Failure -> result
        is Outcome.Unavailable -> result
        is Outcome.Unknown -> result
        is Outcome.Success -> error("Expected unsuccessful read")
    }
    private companion object { val SCRYFALL = ProviderId("scryfall") }
}
