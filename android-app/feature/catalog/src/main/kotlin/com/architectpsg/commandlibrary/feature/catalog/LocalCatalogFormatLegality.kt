package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CancellationException

/** Query-only source consensus over the existing direct-or-membership English printing domain. */
internal class LocalCatalogFormatLegality(private val database: Database) : CatalogFormatLegalityLookup {
    override suspend fun legality(oracleId: OracleId, formatId: FormatId, snapshot: SnapshotReference,
        cancel: CancelToken): Outcome<CatalogFormatLegality> {
        CatalogLegalityEvidence.check(cancel)
        if (oracleId.source != SOURCE || formatId.source != SOURCE || snapshot.source != SOURCE)
            return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Catalog source is not supported")
        try {
            require(snapshot.generation != null && snapshot.revision == null && snapshot.language == LanguageTag.ENGLISH)
            CatalogLegalityEvidence.identity(oracleId.raw, cancel)
            CatalogLegalityEvidence.identity(requireNotNull(snapshot.generation).value, cancel)
            require(formatId.raw.length in 1..64 && formatId.raw.all { it in 'a'..'z' || it in '0'..'9' || it == '_' })
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { return Outcome.Failure(bulkError(FailureCode.INVALID_INPUT, "Invalid catalog legality request")) }

        val admitted = bulkAttempt { active(database, snapshot, false, cancel) }
        if (admitted !is Outcome.Success) return unsuccessful(admitted)
        val result = try {
            val outer = database.transaction({ tx -> bulkAttempt {
                val view = TransactionView(database, tx)
                val source = active(view, snapshot, true, cancel)
                ready(view, snapshot, cancel)
                read(view, source, oracleId, formatId, snapshot, cancel)
            } }, cancel)
            when (outer) {
                is Outcome.Success -> outer.value
                is Outcome.Failure -> outer
                is Outcome.Unavailable -> outer
                is Outcome.Unknown -> outer
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { Outcome.Failure(bulkError(FailureCode.PERSISTENCE, "Catalog legality read failed")) }
        // Failed inner lookups must not turn a changed generation into missing-card evidence.
        val final = bulkAttempt { active(database, snapshot, true, cancel) }
        return if (final is Outcome.Success) result else unsuccessful(final)
    }

    private suspend fun read(db: Database, source: BulkInstalledSource, oracle: OracleId, format: FormatId,
        snapshot: SnapshotReference, cancel: CancelToken): CatalogFormatLegality {
        val parameters = arrayOf("g" to source.generation.value, "o" to oracle.raw)
        val oracleRow = rows(db, bulkSql("SELECT length(CAST(record_payload AS BLOB)) AS bytes FROM oracle_card WHERE generation_id=:g AND provider='scryfall' AND oracle_id=:o AND language='en'",
            1, *parameters), cancel).singleOrNull() ?: throw BulkFailure(Outcome.Unavailable(
            UnavailableReason.NOT_FOUND, "Oracle is not installed"))
        val oracleBytes = oracleRow.bulkLong("bytes")
        require(oracleBytes in 1..DeckStatisticsLimits.MAX_RAW_RECORD_BYTES.toLong())
        val domain = domain()
        // Aggregate preflight precedes raw fetches and every legacy reader's per-face SQL.
        val sizes = rows(db, bulkSql("SELECT count(*) AS n,coalesce(sum(length(CAST(pr.metadata_payload AS BLOB))),0) AS bytes,coalesce(max(length(CAST(pr.metadata_payload AS BLOB))),0) AS largest,coalesce(min(length(CAST(pr.metadata_payload AS BLOB))),1) AS smallest FROM printing pr WHERE $domain",
            1, *parameters), cancel).single()
        val count = sizes.bulkLong("n")
        val bytes = sizes.bulkLong("bytes")
        require(count in 0..MAX_PRINTINGS.toLong() && sizes.bulkLong("smallest") >= 1 &&
            sizes.bulkLong("largest") <= DeckStatisticsLimits.MAX_RAW_RECORD_BYTES && bytes >= 0 &&
            bytes <= DeckStatisticsLimits.MAX_AGGREGATE_FACT_BYTES - oracleBytes)
        val orphans = rows(db, bulkSql("SELECT count(*) AS n FROM printing_oracle_membership pm LEFT JOIN printing pr ON pr.generation_id=pm.generation_id AND pr.provider=pm.printing_provider AND pr.printing_id=pm.printing_id WHERE pm.generation_id=:g AND pm.oracle_provider='scryfall' AND pm.oracle_id=:o AND pm.oracle_language='en' AND pr.printing_id IS NULL",
            1, *parameters), cancel).single().bulkLong("n")
        require(orphans == 0L)
        val canonicalRaw = raw(db, source.generation, null, oracle.raw, oracleBytes, cancel)
        CatalogLegalityEvidence.parse(canonicalRaw, cancel)
        val lookup = LocalCatalogIdentities(db)
        val card = lookup.oracle(oracle, cancel).bulkValue()
        val provenance = source.source.provenance(source.generation, source.observed, source.hash)
        require(card.oracleId == oracle && card.language == LanguageTag.ENGLISH && card.provenance == provenance)
        val cardFact = rows(db, bulkSql("SELECT count(*) AS n FROM catalog_search_card_fact WHERE generation_id=:g AND provider='scryfall' AND oracle_id=:o AND language='en' AND projection_version=1",
            1, *parameters), cancel).single().bulkLong("n")
        require(cardFact == 1L)
        require(raw(db, source.generation, null, oracle.raw, oracleBytes, cancel) == canonicalRaw)
        var last = ""
        var seen = 0L
        var observedBytes = 0L
        var consensus: LegalityStatus? = null
        var uncertain = count == 0L
        while (seen < count) {
            CatalogLegalityEvidence.check(cancel)
            val page = rows(db, bulkSql("SELECT CASE WHEN length(CAST(pr.printing_id AS BLOB))<=128 THEN pr.printing_id ELSE NULL END AS printing_id,length(CAST(pr.metadata_payload AS BLOB)) AS bytes FROM printing pr WHERE $domain AND pr.printing_id>:last ORDER BY pr.printing_id LIMIT :limit",
                PAGE, *parameters, "last" to last, "limit" to PAGE), cancel)
            require(page.isNotEmpty() && page.size <= PAGE && seen + page.size <= count)
            for (row in page) {
                CatalogLegalityEvidence.check(cancel)
                val id = row.bulkText("printing_id")
                CatalogLegalityEvidence.identity(id, cancel)
                // SQLite's BINARY UTF8 order is authoritative (not JVM UTF16 string order).
                require(id != last)
                val size = row.bulkLong("bytes")
                require(size in 1..DeckStatisticsLimits.MAX_RAW_RECORD_BYTES.toLong())
                observedBytes = Math.addExact(observedBytes, size)
                require(observedBytes <= bytes)
                val original = raw(db, source.generation, id, oracle.raw, size, cancel)
                val parsed = CatalogLegalityEvidence.parse(original, cancel)
                val evidence = CatalogLegalityEvidence.selected(parsed, format.raw, cancel)
                val printing = lookup.printing(CardId(SOURCE, id), cancel).bulkValue()
                require(printing.id == CardId(SOURCE, id) && printing.language == LanguageTag.ENGLISH && printing.provenance == provenance)
                require(printing.oracleId.bulkKnown() == oracle || printing.faceAssets.any { asset ->
                    asset.relationships.any { it.relatedOracle.bulkKnown() == oracle }
                })
                // Exact selected-format comparison; absent rows are valid only when source omitted that format.
                val fact = rows(db, bulkSql("SELECT legalities_known FROM catalog_search_printing_fact WHERE generation_id=:g AND provider='scryfall' AND printing_id=:id AND projection_version=1 AND language='en'",
                    1, "g" to source.generation.value, "id" to id), cancel).single()
                require(fact.bulkLong("legalities_known") == if (evidence.legalitiesKnown) 1L else 0L)
                val statusRows = rows(db, bulkSql("SELECT CASE WHEN length(CAST(status AS BLOB))<=32 THEN status ELSE NULL END AS status FROM catalog_search_legality_fact WHERE generation_id=:g AND provider='scryfall' AND printing_id=:id AND projection_version=1 AND format=:format",
                    1, "g" to source.generation.value, "id" to id, "format" to format.raw), cancel)
                require(statusRows.singleOrNull()?.bulkText("status") == evidence.projectedStatus)
                require(raw(db, source.generation, id, oracle.raw, size, cancel) == original)
                val status = evidence.status
                if (status == null) uncertain = true
                else if (consensus == null) consensus = status
                else if (consensus != status) uncertain = true
                last = id
                seen++
            }
        }
        require(seen == count && observedBytes == bytes)
        ready(db, snapshot, cancel)
        active(db, snapshot, true, cancel)
        return CatalogFormatLegality(oracle, format, snapshot,
            if (uncertain || consensus == null) CatalogLegalityEvidence.unknown() else Knowledge.Known(consensus), provenance)
    }

    private fun domain(): String = """pr.generation_id=:g AND pr.provider='scryfall' AND pr.language='en' AND
        ((pr.oracle_provider='scryfall' AND pr.oracle_id=:o AND pr.oracle_language='en') OR EXISTS
        (SELECT 1 FROM printing_oracle_membership pm WHERE pm.generation_id=pr.generation_id AND
        pm.printing_provider=pr.provider AND pm.printing_id=pr.printing_id AND pm.oracle_provider='scryfall' AND pm.oracle_id=:o AND pm.oracle_language='en'))""".trimIndent()

    private suspend fun raw(db: Database, generation: GenerationId, printing: String?, oracle: String,
        expectedBytes: Long, cancel: CancelToken): String {
        val table = if (printing == null) "oracle_card" else "printing"
        val field = if (printing == null) "record_payload" else "metadata_payload"
        val key = if (printing == null) "oracle_id" else "printing_id"
        val row = rows(db, bulkSql("SELECT CASE WHEN length(CAST($field AS BLOB)) BETWEEN 1 AND :maximum THEN $field ELSE NULL END AS raw,length(CAST($field AS BLOB)) AS bytes FROM $table WHERE generation_id=:g AND provider='scryfall' AND $key=:id AND language='en'",
            1, "g" to generation.value, "id" to (printing ?: oracle), "maximum" to DeckStatisticsLimits.MAX_RAW_RECORD_BYTES), cancel).single()
        require(row.bulkLong("bytes") == expectedBytes)
        return row.bulkText("raw").also {
            require(CatalogDeckFactsFields.textBytes(it, DeckStatisticsLimits.MAX_RAW_RECORD_BYTES, cancel).toLong() == expectedBytes)
        }
    }

    private suspend fun ready(db: Database, snapshot: SnapshotReference, cancel: CancelToken) {
        val row = rows(db, bulkSql("SELECT CASE WHEN status='READY' THEN 1 ELSE 0 END AS ready FROM catalog_search_build WHERE generation_id=:g AND projection_version=1",
            1, "g" to requireNotNull(snapshot.generation).value), cancel).singleOrNull()
        if (row == null || row.bulkLong("ready") != 1L) throw BulkFailure(Outcome.Unavailable(
            UnavailableReason.SOURCE_UNAVAILABLE, "Catalog legality projection is not prepared"))
    }

    private suspend fun active(db: Database, snapshot: SnapshotReference, hadActive: Boolean, cancel: CancelToken): BulkInstalledSource {
        // Fixed source-proof columns are bounded before materializing their text.
        val limits = listOf("generation_id" to 128, "provider" to 128, "format" to 128,
            "content_uri" to AcquisitionPayloadCodec.MAX_BYTES, "display_name" to 2048,
            "source_uri" to AcquisitionPayloadCodec.MAX_BYTES, "source_version" to AcquisitionPayloadCodec.MAX_BYTES,
            "content_sha256" to 64, "language_scope" to 16, "parser_version" to 128,
            "notice_payload" to 128 * 1024, "status" to 16, "input_kind" to 64,
            "artifact_job_id" to 128, "source_payload" to BulkSourceEnvelope.MAX_BYTES)
        val fields = limits.map { (name, size) -> "CASE WHEN length(CAST(g.$name AS BLOB))<=$size THEN g.$name ELSE NULL END AS $name" }
        val integers = listOf("source_date_epoch_seconds", "source_date_nano", "observed_at_epoch_seconds", "observed_at_nano", "observed_bytes",
            "payload_schema_version", "oracle_count", "face_count", "printing_count", "activated_at_epoch_seconds", "activated_at_nano")
        val oversized = limits.joinToString(" OR ") { (name, size) -> "length(CAST(g.$name AS BLOB))>$size" }
        val row = rows(db, bulkSql("SELECT ${fields.joinToString(",")},${integers.joinToString(",") { "g.$it" }},CASE WHEN $oversized THEN 1 ELSE 0 END AS oversized FROM catalog_active a JOIN catalog_generation g ON g.generation_id=a.generation_id WHERE a.dataset_key=:key",
            1, "key" to BULK_DATASET), cancel).singleOrNull()
        if (row == null) {
            if (hadActive) stale()
            throw BulkFailure(Outcome.Unavailable(UnavailableReason.NOT_INSTALLED, "No English catalog installed"))
        }
        require(row.bulkLong("oversized") == 0L)
        if (row.bulkText("generation_id") != snapshot.generation?.value) stale()
        return row.bulkInstalledSource().also { require(it.source.provider == SOURCE && it.generation == snapshot.generation) }
    }
    private suspend fun rows(db: Database, request: SqlRequest, cancel: CancelToken): SnapshotList<RowData> {
        CatalogLegalityEvidence.check(cancel)
        return db.query(request, cancel).bulkValue().also { CatalogLegalityEvidence.check(cancel) }
    }
    private fun stale(): Nothing = throw BulkFailure(Outcome.Failure(bulkError(FailureCode.STALE_GENERATION, "Catalog generation changed")))
    private fun <T> unsuccessful(value: Outcome<*>): Outcome<T> = when (value) {
        is Outcome.Failure -> value
        is Outcome.Unavailable -> value
        is Outcome.Unknown -> value
        is Outcome.Success -> error("Expected unsuccessful read")
    }
    /** Existing identity readers issue only queries, all pinned to this same transaction. */
    private class TransactionView(private val database: Database, private val tx: DbTransaction) : Database {
        override suspend fun query(request: SqlRequest, cancel: CancelToken) = tx.query(request, cancel)
        override suspend fun capabilities(cancel: CancelToken) = database.capabilities(cancel)
        override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> =
            error("Nested transaction is not supported by query-only view")
    }
    private companion object {
        val SOURCE = ProviderId("scryfall")
        const val MAX_PRINTINGS = 10_000
        const val PAGE = PageRequest.MAX_LIMIT
    }
}
