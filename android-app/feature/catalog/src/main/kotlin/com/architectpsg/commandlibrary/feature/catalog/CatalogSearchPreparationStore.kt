package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

internal enum class SearchPreparationPhase {
    CARD, PRINTING, VERIFY, VERIFY_CARD, VERIFY_FACE, VERIFY_PRINTING, VERIFY_TEXT, VERIFY_LEGALITY, VERIFY_FTS;
    val verifying: Boolean get() = ordinal >= VERIFY_CARD.ordinal
}
internal enum class SearchPreparationStatus { PREPARING, READY, FAILED }
internal data class SearchPreparationCheckpoint(
    val generation: GenerationId,
    val phase: SearchPreparationPhase = SearchPreparationPhase.CARD,
    val provider: String? = null,
    val id: String? = null,
    val language: String? = null,
    val cards: Long = 0,
    val faces: Long = 0,
    val printings: Long = 0,
    val texts: Long = 0,
    val legalities: Long = 0,
    val status: SearchPreparationStatus = SearchPreparationStatus.PREPARING,
    val diagnostic: String? = null,
) {
    init {
        require(listOf(cards, faces, printings, texts, legalities).all { it >= 0 })
        if (phase.verifying) {
            SearchVerificationPosition.decode(requireNotNull(provider), id)
            require(language == null)
        } else {
            require((provider == null) == (id == null))
            require(language == if (provider != null && phase == SearchPreparationPhase.CARD) "en" else null)
            require(phase != SearchPreparationPhase.VERIFY || provider == null)
        }
        require(status != SearchPreparationStatus.READY || phase == SearchPreparationPhase.VERIFY_FTS)
        require(if (status == SearchPreparationStatus.FAILED) diagnostic == "INVALID_SOURCE_OR_PROJECTION" else diagnostic == null)
        for (value in listOfNotNull(generation.value, provider, id))
            require(value.isNotBlank() && value.length <= 128 && '\u0000' !in value && value.toByteArray(Charsets.UTF_8).size <= 128)
    }
}

/** Internal bounded materialization and independent readiness verification; no query/DI port.
 * Each call holds the shared owner through exact commit reconciliation. Source is read inside
 * the transaction; callers cannot supply projected rows, provenance or a source payload.
 */
internal class CatalogSearchPreparationStore(private val database: Database, private val clock: Clock) {
    /** NULL means an installed generation has no build; it never means an empty READY index. */
    suspend fun status(generation: GenerationId, cancel: CancelToken): Outcome<SearchPreparationCheckpoint?> = withCatalogImportOwner(cancel) {
        flatten(database.transaction({ tx -> bulkAttempt { tx.source(generation, cancel); tx.checkpoint(generation, cancel) } }, cancel))
    }

    suspend fun advance(generation: GenerationId, cancel: CancelToken): Outcome<SearchPreparationCheckpoint> =
        withCatalogImportOwner(cancel) {
            var prior: SearchPreparationCheckpoint? = null
            var candidate: SearchPreparationCheckpoint? = null
            var checkpointRead = false
            try {
                val outer = database.transaction({ tx -> bulkAttempt {
                    val installed = tx.source(generation, cancel)
                    prior = tx.checkpoint(generation, cancel)
                    checkpointRead = true
                    val start = prior ?: SearchPreparationCheckpoint(generation)
                    if (start.status != SearchPreparationStatus.PREPARING) return@bulkAttempt start
                    if (prior == null) tx.exactWrite("INSERT INTO catalog_search_build(generation_id,projection_version,status,phase,card_count,face_count,printing_count,text_count,legality_count) VALUES(:g,1,'PREPARING','CARD',0,0,0,0,0)", cancel, "g" to generation.value)
                    val next = when {
                        start.phase == SearchPreparationPhase.VERIFY -> start.copy(phase = SearchPreparationPhase.VERIFY_CARD, provider = SearchVerificationCounts().encode())
                        start.phase.verifying -> tx.verify(installed, start, cancel)
                        else -> tx.append(installed, start, if (prior == null) 8 else 7, cancel)
                    }
                    require(next != prior)
                    tx.update(start, next, cancel)
                    check(tx.state == DbTransactionState.Active)
                    candidate = next
                    next
                } }, cancel)
                val result = flatten(outer)
                if (result is Outcome.Success) return@withCatalogImportOwner result
                if (result is Outcome.Failure && result.error.code == FailureCode.CORRUPT_DATA && candidate == null && checkpointRead)
                    return@withCatalogImportOwner markFailed(generation, prior, cancel)
                val intended = candidate ?: return@withCatalogImportOwner result
                when (acknowledge(generation, prior, intended)) {
                    Acknowledgement.COMMITTED -> Outcome.Success(intended)
                    Acknowledgement.PRIOR -> result
                    Acknowledgement.UNKNOWN -> Outcome.Unknown("Search preparation commit acknowledgement unavailable", SnapshotList.empty())
                }
            } catch (error: CancellationException) {
                val intended = candidate ?: throw error
                when (acknowledge(generation, prior, intended)) {
                    Acknowledgement.COMMITTED -> Outcome.Success(intended)
                    Acknowledgement.PRIOR -> throw error
                    Acknowledgement.UNKNOWN -> Outcome.Unknown("Search preparation commit acknowledgement unavailable", SnapshotList.empty())
                }
            }
        }

    private suspend fun markFailed(generation: GenerationId, prior: SearchPreparationCheckpoint?, cancel: CancelToken): Outcome<SearchPreparationCheckpoint> {
        var candidate: SearchPreparationCheckpoint? = null
        try {
            val result = flatten(database.transaction({ tx -> bulkAttempt {
                require(tx.checkpoint(generation, cancel) == prior)
                val failed = (prior ?: SearchPreparationCheckpoint(generation)).copy(status = SearchPreparationStatus.FAILED, diagnostic = "INVALID_SOURCE_OR_PROJECTION")
                if (prior == null) tx.exactWrite("INSERT INTO catalog_search_build(generation_id,projection_version,status,phase,card_count,face_count,printing_count,text_count,legality_count,diagnostic) VALUES(:g,1,'FAILED','CARD',0,0,0,0,0,'INVALID_SOURCE_OR_PROJECTION')", cancel, "g" to generation.value)
                else tx.update(prior, failed, cancel)
                check(tx.state == DbTransactionState.Active)
                candidate = failed
                failed
            } }, cancel))
            if (result is Outcome.Success) return result
            val intended = candidate ?: return result
            return when (acknowledge(generation, prior, intended)) {
                Acknowledgement.COMMITTED -> Outcome.Success(intended)
                Acknowledgement.PRIOR -> result
                Acknowledgement.UNKNOWN -> Outcome.Unknown("Search preparation failure acknowledgement unavailable", SnapshotList.empty())
            }
        } catch (error: CancellationException) {
            val intended = candidate ?: throw error
            return when (acknowledge(generation, prior, intended)) {
                Acknowledgement.COMMITTED -> Outcome.Success(intended)
                Acknowledgement.PRIOR -> throw error
                Acknowledgement.UNKNOWN -> Outcome.Unknown("Search preparation failure acknowledgement unavailable", SnapshotList.empty())
            }
        }
    }

    private fun <T> flatten(result: Outcome<Outcome<T>>): Outcome<T> = when (result) {
        is Outcome.Success -> result.value; is Outcome.Failure -> result; is Outcome.Unavailable -> result; is Outcome.Unknown -> result
    }

    private enum class Acknowledgement { COMMITTED, PRIOR, UNKNOWN }
    private suspend fun acknowledge(generation: GenerationId, prior: SearchPreparationCheckpoint?, intended: SearchPreparationCheckpoint): Acknowledgement = withContext(NonCancellable) {
        try {
            withTimeout(500) {
                val actual = database.query(checkpointQuery(generation), BULK_NO_CANCEL).bulkValue().firstOrNull()?.decodeCheckpoint(generation)
                when (actual) { intended -> Acknowledgement.COMMITTED; prior -> Acknowledgement.PRIOR; else -> Acknowledgement.UNKNOWN }
            }
        } catch (_: Exception) { Acknowledgement.UNKNOWN }
    }

    private suspend fun DbTransaction.checkpoint(generation: GenerationId, cancel: CancelToken): SearchPreparationCheckpoint? =
        bulkRead(checkpointQuery(generation), cancel).firstOrNull()?.decodeCheckpoint(generation)

    private fun checkpointQuery(generation: GenerationId): SqlRequest = guardedSelect("catalog_search_build", listOf(
        "generation_id" to 128, "status" to 16, "phase" to 16, "cursor_provider" to 128,
        "cursor_id" to 128, "cursor_language" to 16, "diagnostic" to 512),
        listOf("projection_version", "card_count", "face_count", "printing_count", "text_count", "legality_count"),
        "generation_id=:g AND projection_version=1", "g" to generation.value)

    private fun RowData.decodeCheckpoint(generation: GenerationId): SearchPreparationCheckpoint {
        require(bulkLong("oversize") == 0L && bulkText("generation_id") == generation.value && bulkLong("projection_version") == 1L)
        return SearchPreparationCheckpoint(generation, SearchPreparationPhase.valueOf(bulkText("phase")),
            bulkOptionalText("cursor_provider"), bulkOptionalText("cursor_id"), bulkOptionalText("cursor_language"),
            bulkLong("card_count"), bulkLong("face_count"), bulkLong("printing_count"), bulkLong("text_count"), bulkLong("legality_count"),
            SearchPreparationStatus.valueOf(bulkText("status")), bulkOptionalText("diagnostic"))
    }

    private suspend fun DbTransaction.source(generation: GenerationId, cancel: CancelToken): BulkInstalledSource {
        val row = bulkRead(guardedSelect("catalog_generation", listOf(
            "generation_id" to 128, "provider" to 128, "format" to 128, "content_uri" to AcquisitionPayloadCodec.MAX_BYTES,
            "display_name" to 2048, "source_uri" to AcquisitionPayloadCodec.MAX_BYTES, "source_version" to AcquisitionPayloadCodec.MAX_BYTES, "content_sha256" to 64,
            "language_scope" to 16, "parser_version" to 128, "notice_payload" to 128 * 1024,
            "status" to 16, "input_kind" to 64, "artifact_job_id" to 128, "source_payload" to BulkSourceEnvelope.MAX_BYTES),
            listOf("source_date_epoch_seconds", "source_date_nano", "observed_at_epoch_seconds", "observed_at_nano",
                "observed_bytes", "payload_schema_version", "oracle_count", "face_count", "printing_count", "activated_at_epoch_seconds", "activated_at_nano"),
            "generation_id=:g", "g" to generation.value), cancel).firstOrNull()
            ?: throw BulkFailure(Outcome.Unavailable(UnavailableReason.NOT_INSTALLED, "Requested catalog generation is not installed"))
        require(row.bulkLong("oversize") == 0L && row.bulkText("provider") == "scryfall")
        require(row.bulkText("status") in listOf("ACTIVE", "RETIRED"))
        // Reuse the existing installed-proof validation; only status eligibility is wider here.
        val activeView = RowData(SnapshotList.copyOf(row.columns.map { if (it.name == "status") RowColumn(it.name, SqlValue.Text("ACTIVE")) else it }))
        return activeView.bulkInstalledSource().also { require(it.generation == generation) }
    }

    private suspend fun DbTransaction.append(source: BulkInstalledSource, start: SearchPreparationCheckpoint, overhead: Int, cancel: CancelToken): SearchPreparationCheckpoint {
        var next = start
        var records = 0
        var rawBytes = 0L
        var derivedBytes = 0L
        var rows = 0
        var statements = overhead // source/checkpoint reads, creation, final CAS and exhaustion lookahead
        val started = clock.monotonicNow()
        while (records < 16) {
            cancel.throwIfCancelled()
            if (records > 0 && clock.monotonicNow() - started >= 250.milliseconds) break
            val cardPhase = start.phase == SearchPreparationPhase.CARD
            val table = if (cardPhase) "oracle_card" else "printing"
            val idColumn = if (cardPhase) "oracle_id" else "printing_id"
            val payloadColumn = if (cardPhase) "record_payload" else "metadata_payload"
            val cursor = if (next.provider == null) "" else if (cardPhase) " AND (provider,$idColumn,language)>(:p,:id,:l)" else " AND (provider,$idColumn)>(:p,:id)"
            val parameters = arrayListOf<Pair<String, Any?>>("g" to start.generation.value)
            if (next.provider != null) { parameters.add("p" to next.provider); parameters.add("id" to next.id); if (cardPhase) parameters.add("l" to next.language) }
            val remainingRaw = minOf(8L * 1024 * 1024, 16L * 1024 * 1024 - rawBytes)
            val sourceRow = bulkRead(guardedSelect(table, listOf("provider" to 128, idColumn to 128, "language" to 16,
                payloadColumn to remainingRaw.toInt()) + if (cardPhase) listOf("canonical_digest" to 64) else listOf("oracle_provider" to 128, "oracle_id" to 128, "oracle_language" to 16),
                listOf("CASE WHEN length(CAST($payloadColumn AS BLOB))>8388608 THEN 1 ELSE 0 END AS source_oversize",
                    "CASE WHEN length(CAST($payloadColumn AS BLOB))>$remainingRaw THEN 1 ELSE 0 END AS batch_exhausted"),
                "generation_id=:g$cursor ORDER BY provider,$idColumn${if (cardPhase) ",language" else ""} LIMIT 1", *parameters.toTypedArray()), cancel).firstOrNull()
            if (sourceRow == null) return next.copy(phase = if (cardPhase) SearchPreparationPhase.PRINTING else SearchPreparationPhase.VERIFY, provider = null, id = null, language = null)
            require(sourceRow.bulkLong("source_oversize") == 0L)
            if (sourceRow.bulkLong("batch_exhausted") != 0L) { check(records > 0); break }
            require(sourceRow.bulkLong("oversize") == 0L)
            val raw = sourceRow.bulkText(payloadColumn)
            val size = raw.toByteArray(Charsets.UTF_8).size.toLong()
            if (rawBytes + size > 16L * 1024 * 1024 && records > 0) break
            val projection = CatalogSearchProjector.project(raw, source.source.provenance(source.generation, source.observed, source.hash), cancel)
            require(projection.generation == source.generation && projection.provider.value == sourceRow.bulkText("provider") && sourceRow.bulkText("language") == "en")
            val selected = projection.rows.filter { if (cardPhase) it.table in CARD_TABLES else it.table in PRINTING_TABLES }
            val bytes = selected.sumOf { row -> row.data.columns.sumOf { (it.value as? SqlValue.Text)?.value?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L } }
            // Reserve validation and rowid reads before performing them. A rejected lookahead
            // costs only its guarded source read, covered by the fixed overhead.
            val statementCost = selected.size * 2 + 8
            if (rows + selected.size > 4096 || derivedBytes + bytes > 16L * 1024 * 1024 || statements + statementCost > 4096) { check(records > 0); break }
            validateSourceProjection(source, sourceRow, raw, projection, cardPhase, cancel)
            var textRowId: Long? = null
            for (row in selected) {
                cancel.throwIfCancelled()
                val columns = row.data.columns
                val mirror = row.table == CatalogProjectionTable.TEXT_FTS
                val request = SqlRequest("INSERT INTO ${row.table.sqlName} (${if (mirror) "rowid," else ""}${columns.joinToString(",") { it.name }}) VALUES (${if (mirror) ":mirror," else ""}${columns.indices.joinToString(",") { ":v$it" }})",
                    SnapshotList.copyOf(columns.mapIndexed { index, column -> SqlParameter("v$index", column.value) } + if (mirror) listOf(SqlParameter("mirror", SqlValue.Integer(requireNotNull(textRowId)))) else emptyList()), 1)
                require(execute(request, cancel).bulkValue().affectedRows == 1L)
                if (row.table == CatalogProjectionTable.TEXT) {
                    textRowId = bulkRead(bulkSql("SELECT last_insert_rowid() AS id"), cancel).single().bulkLong("id")
                    require(textRowId > 0)
                } else if (mirror) textRowId = null
            }
            fun count(tableName: CatalogProjectionTable) = selected.count { it.table == tableName }.toLong()
            next = next.copy(provider = projection.provider.value, id = sourceRow.bulkText(idColumn), language = if (cardPhase) "en" else null,
                cards = Math.addExact(next.cards, count(CatalogProjectionTable.CARD)), faces = Math.addExact(next.faces, count(CatalogProjectionTable.FACE)),
                printings = Math.addExact(next.printings, count(CatalogProjectionTable.PRINTING)), texts = Math.addExact(next.texts, count(CatalogProjectionTable.TEXT)), legalities = Math.addExact(next.legalities, count(CatalogProjectionTable.LEGALITY)))
            records++; rawBytes += size; derivedBytes += bytes; rows += selected.size; statements += statementCost
        }
        return next
    }

    private suspend fun DbTransaction.validateSourceProjection(source: BulkInstalledSource, sourceRow: RowData, raw: String, projection: CatalogSearchProjection, cardPhase: Boolean, cancel: CancelToken) {
        require(projection.generation == source.generation && projection.provider.value == sourceRow.bulkText("provider") && sourceRow.bulkText("language") == "en")
        if (cardPhase) {
            require(projection.oracleId?.raw == sourceRow.bulkText(if (cardPhase) "oracle_id" else "printing_id"))
            val card = requireNotNull(source.source.map(raw, source.generation, source.observed, source.hash, 32).first)
            require(canonicalDigest(card) == sourceRow.bulkText("canonical_digest"))
            val count = bulkRead(bulkSql("SELECT count(*) AS n FROM card_face WHERE generation_id=:g AND provider=:p AND oracle_id=:id AND language='en'", 1,
                "g" to source.generation.value, "p" to projection.provider.value, "id" to projection.oracleId.raw), cancel).single().bulkLong("n")
            require(count == card.faces.size.toLong())
            for (face in card.faces) {
                cancel.throwIfCancelled()
                // Compare payload in SQLite to avoid loading a second 8MiB copy per face.
                validateScalars("card_face", "generation_id=:g AND provider=:p AND oracle_id=:id AND language='en' AND face_index=:i",
                    listOf("g" to source.generation.value, "p" to projection.provider.value, "id" to projection.oracleId.raw, "i" to face.identity.index),
                    listOf("provider_face_id" to face.identity.providerFaceId.bulkKnown(), "name" to face.name, "face_payload" to raw), cancel)
            }
        } else {
            require(projection.printingId.raw == sourceRow.bulkText(if (cardPhase) "oracle_id" else "printing_id"))
            require(sourceRow.bulkOptionalText("oracle_provider") == projection.oracleId?.source?.value && sourceRow.bulkOptionalText("oracle_id") == projection.oracleId?.raw && sourceRow.bulkOptionalText("oracle_language") == projection.oracleId?.let { "en" })
            val printing = source.source.map(raw, source.generation, source.observed, source.hash, 32).second
            validateScalars("printing", "generation_id=:g AND provider=:p AND printing_id=:id",
                listOf("g" to source.generation.value, "p" to projection.provider.value, "id" to projection.printingId.raw),
                listOf("set_code" to printing.setCode, "rarity" to printing.rarity.bulkKnown(), "finishes" to printing.finishes.bulkKnown()?.joinToString(",") { it.name },
                    "set_name" to printing.setName.bulkKnown(), "collector_number" to printing.collectorNumber, "image_uri" to printing.imageUri.bulkKnown()?.value), cancel)
        }
    }

    private suspend fun DbTransaction.verify(source: BulkInstalledSource, start: SearchPreparationCheckpoint, cancel: CancelToken): SearchPreparationCheckpoint {
        var position = SearchVerificationPosition.decode(requireNotNull(start.provider), start.id)
        val phase = start.phase
        val sourcePhase = phase == SearchPreparationPhase.VERIFY_CARD || phase == SearchPreparationPhase.VERIFY_PRINTING
        val table = when (phase) {
            SearchPreparationPhase.VERIFY_CARD -> "oracle_card"
            SearchPreparationPhase.VERIFY_FACE -> "card_face"
            SearchPreparationPhase.VERIFY_PRINTING -> "printing"
            SearchPreparationPhase.VERIFY_TEXT -> "catalog_search_text_fact"
            SearchPreparationPhase.VERIFY_LEGALITY -> "catalog_search_legality_fact"
            SearchPreparationPhase.VERIFY_FTS -> "catalog_search_text_fts"
            else -> error("Not a verification phase")
        }
        val target = "generation_id=:g${if (sourcePhase || phase == SearchPreparationPhase.VERIFY_FACE) "" else " AND projection_version=1"}"
        val started = clock.monotonicNow()
        var records = 0; var inspected = 0; var statements = 72; var derivedRows = 0
        var rawBytes = 0L; var derivedBytes = 0L
        while (inspected < 64 && records < 16) {
            cancel.throwIfCancelled()
            if (inspected > 0 && clock.monotonicNow() - started >= 250.milliseconds) break
            val textColumns = if (sourcePhase) {
                val payload = if (phase == SearchPreparationPhase.VERIFY_CARD) "record_payload" else "metadata_payload"
                listOf("provider" to 128, "language" to 16, payload to minOf(8388608L, 16777216L - rawBytes).toInt()) +
                    if (phase == SearchPreparationPhase.VERIFY_CARD) listOf("oracle_id" to 128, "canonical_digest" to 64)
                    else listOf("printing_id" to 128, "oracle_provider" to 128, "oracle_id" to 128, "oracle_language" to 16)
            } else if (phase == SearchPreparationPhase.VERIFY_FACE) listOf("provider" to 128, "oracle_id" to 128, "language" to 16) else emptyList()
            val parameters = arrayListOf<Pair<String, Any?>>("g" to start.generation.value)
            val after = position.watermark?.let { parameters.add("after" to it); "WHERE rowid>:after" } ?: ""
            val fields = textColumns.map { (name, limit) -> "CASE WHEN ($target) AND length(CAST($name AS BLOB))<=$limit THEN $name ELSE NULL END AS $name" } +
                if (phase == SearchPreparationPhase.VERIFY_FACE) listOf("CASE WHEN $target THEN face_index ELSE NULL END AS face_index") else emptyList()
            val oversize = textColumns.joinToString(" OR ") { (name, limit) -> "length(CAST($name AS BLOB))>$limit" }.ifEmpty { "0" }
            val rawPayload = if (phase == SearchPreparationPhase.VERIFY_CARD) "record_payload" else "metadata_payload"
            val rawFlags = if (sourcePhase) ",CASE WHEN ($target) AND length(CAST($rawPayload AS BLOB))>8388608 THEN 1 ELSE 0 END AS source_oversize,CASE WHEN ($target) AND length(CAST($rawPayload AS BLOB))>${16777216L - rawBytes} THEN 1 ELSE 0 END AS batch_exhausted" else ""
            val row = bulkRead(bulkSql("SELECT rowid AS scan_rowid,CASE WHEN $target THEN 1 ELSE 0 END AS relevant,CASE WHEN ($target) AND ($oversize) THEN 1 ELSE 0 END AS oversize$rawFlags${if (fields.isEmpty()) "" else "," + fields.joinToString(",")} FROM $table $after ORDER BY rowid LIMIT 1", 1, *parameters.toTypedArray()), cancel).firstOrNull()
                ?: return finishVerification(start.copy(provider = position.counts.encode(), id = position.watermark?.toString()), position.counts, cancel)
            val rowid = row.bulkLong("scan_rowid")
            if (row.bulkLong("relevant") == 0L) {
                position = position.copy(watermark = rowid); inspected++; continue
            }
            require(rowid > 0)
            var delta = SearchVerificationCounts()
            if (sourcePhase) {
                val cardPhase = phase == SearchPreparationPhase.VERIFY_CARD
                val payload = if (cardPhase) "record_payload" else "metadata_payload"
                require(row.bulkLong("source_oversize") == 0L)
                if (row.bulkLong("batch_exhausted") != 0L) { check(records > 0); break }
                require(row.bulkLong("oversize") == 0L)
                val raw = row.bulkText(payload)
                val projection = CatalogSearchProjector.project(raw, source.source.provenance(source.generation, source.observed, source.hash), cancel)
                val phaseRows = projection.rows.filter { if (cardPhase) it.table in CARD_TABLES else it.table in PRINTING_TABLES }
                val selected = phaseRows.filter { it.table != CatalogProjectionTable.TEXT_FTS }
                val bytes = phaseRows.sumOf { fact -> fact.data.columns.sumOf { (it.value as? SqlValue.Text)?.value?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L } }
                val cost = 2 * phaseRows.size + 8
                if (statements + cost > 4096 || derivedRows + phaseRows.size > 4096 || derivedBytes + bytes > 16777216) { check(records > 0); break }
                validateSourceProjection(source, row, raw, projection, cardPhase, cancel)
                for (fact in selected) verifyExpectedFact(fact, cancel)
                fun count(kind: CatalogProjectionTable) = selected.count { it.table == kind }.toLong()
                delta = SearchVerificationCounts(count(CatalogProjectionTable.CARD), count(CatalogProjectionTable.FACE), count(CatalogProjectionTable.PRINTING), count(CatalogProjectionTable.TEXT), count(CatalogProjectionTable.LEGALITY))
                rawBytes += raw.toByteArray(Charsets.UTF_8).size
                derivedBytes += bytes; derivedRows += phaseRows.size; statements += cost; records++
            } else when (phase) {
                SearchPreparationPhase.VERIFY_FACE -> {
                    require(row.bulkLong("oversize") == 0L)
                    val found = bulkRead(bulkSql("SELECT face_index FROM catalog_search_face_fact WHERE generation_id=:g AND projection_version=1 AND provider=:p AND oracle_id=:id AND language=:l AND face_index=:i", 1,
                        "g" to start.generation.value, "p" to row.bulkText("provider"), "id" to row.bulkText("oracle_id"), "l" to row.bulkText("language"), "i" to row.bulkLong("face_index")), cancel)
                    require(found.size == 1)
                    delta = SearchVerificationCounts(faces = 1)
                }
                SearchPreparationPhase.VERIFY_TEXT, SearchPreparationPhase.VERIFY_FTS -> {
                    val fromFts = phase == SearchPreparationPhase.VERIFY_FTS
                    val pairs = listOf("generation_id", "projection_version", "provider", "oracle_id", "language", "scope_kind", "scope_index", "field").map { "t.$it IS f.$it" } + "t.normalized_value IS f.value"
                    val from = if (fromFts) "catalog_search_text_fts f LEFT JOIN catalog_search_text_fact t ON t.rowid=f.rowid" else "catalog_search_text_fact t LEFT JOIN catalog_search_text_fts f ON f.rowid=t.rowid"
                    val anchor = if (fromFts) "f" else "t"
                    val other = if (fromFts) "t" else "f"
                    val match = bulkRead(bulkSql("SELECT CASE WHEN $other.rowid IS NOT NULL AND ${pairs.joinToString(" AND ")} THEN 1 ELSE 0 END AS matches FROM $from WHERE $anchor.rowid=:r", 1, "r" to rowid), cancel).single()
                    require(match.bulkLong("matches") == 1L)
                    delta = SearchVerificationCounts(texts = 1)
                }
                SearchPreparationPhase.VERIFY_LEGALITY -> {
                    val valid = bulkRead(bulkSql("SELECT CASE WHEN status IN ('LEGAL','BANNED','RESTRICTED','NOT_LEGAL','UNKNOWN') THEN 1 ELSE 0 END AS valid FROM catalog_search_legality_fact WHERE rowid=:r", 1, "r" to rowid), cancel).single()
                    require(valid.bulkLong("valid") == 1L)
                    delta = SearchVerificationCounts(legalities = 1)
                }
                else -> error("Unexpected verification phase")
            }
            position = SearchVerificationPosition(position.counts.add(delta), rowid)
            inspected++
        }
        return start.copy(provider = position.counts.encode(), id = position.watermark?.toString())
    }

    private suspend fun DbTransaction.verifyExpectedFact(fact: CatalogProjectionRow, cancel: CancelToken) {
        val identity = when (fact.table) {
            CatalogProjectionTable.CARD -> setOf("generation_id", "projection_version", "provider", "oracle_id", "language")
            CatalogProjectionTable.FACE -> setOf("generation_id", "projection_version", "provider", "oracle_id", "language", "face_index")
            CatalogProjectionTable.PRINTING -> setOf("generation_id", "projection_version", "provider", "printing_id")
            CatalogProjectionTable.LEGALITY -> setOf("generation_id", "projection_version", "provider", "printing_id", "format")
            CatalogProjectionTable.TEXT -> setOf("generation_id", "projection_version", "provider", "oracle_id", "language", "scope_kind", "scope_index", "field")
            CatalogProjectionTable.TEXT_FTS -> error("FTS verified by indexed rowid mirror")
        }
        val columns = fact.data.columns
        val equalities = columns.mapIndexed { index, column -> "${column.name} IS :v$index" }
        val predicate = columns.mapIndexedNotNull { index, column -> if (column.name in identity) "${column.name} IS :v$index" else null }.joinToString(" AND ")
        val result = bulkRead(SqlRequest("SELECT CASE WHEN rowid>0 AND ${equalities.joinToString(" AND ")} THEN 1 ELSE 0 END AS matches FROM ${fact.table.sqlName} WHERE $predicate",
            SnapshotList.copyOf(columns.mapIndexed { index, column -> SqlParameter("v$index", column.value) }), 1), cancel).single()
        require(result.bulkLong("matches") == 1L)
    }

    private suspend fun DbTransaction.finishVerification(start: SearchPreparationCheckpoint, actual: SearchVerificationCounts, cancel: CancelToken): SearchPreparationCheckpoint {
        val original = bulkRead(bulkSql("SELECT oracle_count,face_count,printing_count FROM catalog_generation WHERE generation_id=:g", 1, "g" to start.generation.value), cancel).single()
        when (start.phase) {
            SearchPreparationPhase.VERIFY_CARD -> require(actual.cards == start.cards && actual.cards == original.bulkLong("oracle_count") && actual.faces == start.faces && actual.faces == original.bulkLong("face_count") && actual.texts == start.texts)
            SearchPreparationPhase.VERIFY_FACE -> require(actual.faces == start.faces && actual.faces == original.bulkLong("face_count"))
            SearchPreparationPhase.VERIFY_PRINTING -> require(actual.printings == start.printings && actual.printings == original.bulkLong("printing_count") && actual.legalities == start.legalities)
            SearchPreparationPhase.VERIFY_TEXT, SearchPreparationPhase.VERIFY_FTS -> require(actual.texts == start.texts)
            SearchPreparationPhase.VERIFY_LEGALITY -> require(actual.legalities == start.legalities)
            else -> error("Not a verification phase")
        }
        return if (start.phase == SearchPreparationPhase.VERIFY_FTS) start.copy(status = SearchPreparationStatus.READY, provider = actual.encode())
        else start.copy(phase = SearchPreparationPhase.entries[start.phase.ordinal + 1], provider = SearchVerificationCounts().encode(), id = null)
    }

    private suspend fun DbTransaction.update(prior: SearchPreparationCheckpoint, next: SearchPreparationCheckpoint, cancel: CancelToken) {
        fun fields(c: SearchPreparationCheckpoint) = listOf("status" to c.status.name, "diagnostic" to c.diagnostic, "phase" to c.phase.name, "cursor_provider" to c.provider, "cursor_id" to c.id, "cursor_language" to c.language,
            "card_count" to c.cards, "face_count" to c.faces, "printing_count" to c.printings, "text_count" to c.texts, "legality_count" to c.legalities)
        val before = fields(prior); val after = fields(next)
        exactWrite("UPDATE catalog_search_build SET ${after.joinToString(",") { "${it.first}=:n_${it.first}" }} WHERE generation_id=:g AND projection_version=1 AND status='PREPARING' AND diagnostic IS NULL AND ${before.joinToString(" AND ") { "${it.first} IS :p_${it.first}" }}", cancel,
            "g" to prior.generation.value, *after.map { "n_${it.first}" to it.second }.toTypedArray(), *before.map { "p_${it.first}" to it.second }.toTypedArray())
    }
    private suspend fun DbTransaction.exactWrite(statement: String, cancel: CancelToken, vararg values: Pair<String, Any?>) {
        if (bulkWrite(statement, cancel, *values) != 1L) abort(bulkError(FailureCode.PERSISTENCE, "Search preparation checkpoint write did not take effect"))
    }

    private suspend fun DbTransaction.validateScalars(table: String, predicate: String, keys: List<Pair<String, Any?>>, values: List<Pair<String, Any?>>, cancel: CancelToken) {
        val equalities = values.joinToString(" AND ") { "${it.first} IS :expected_${it.first}" }
        val row = bulkRead(bulkSql("SELECT CASE WHEN $equalities THEN 1 ELSE 0 END AS matches FROM $table WHERE $predicate", 1,
            *keys.toTypedArray(), *values.map { "expected_${it.first}" to it.second }.toTypedArray()), cancel).single()
        require(row.bulkLong("matches") == 1L)
    }

    private fun guardedSelect(table: String, texts: List<Pair<String, Int>>, integers: List<String>, predicate: String, vararg parameters: Pair<String, Any?>): SqlRequest {
        val values = texts.map { (name, limit) -> "CASE WHEN length(CAST($name AS BLOB))<=$limit THEN $name ELSE NULL END AS $name" } + integers
        val oversize = texts.joinToString(" OR ") { (name, limit) -> "length(CAST($name AS BLOB))>$limit" }
        return bulkSql("SELECT ${values.joinToString(",")},CASE WHEN $oversize THEN 1 ELSE 0 END AS oversize FROM $table WHERE $predicate", 1, *parameters)
    }
    private companion object {
        val CARD_TABLES = setOf(CatalogProjectionTable.CARD, CatalogProjectionTable.FACE, CatalogProjectionTable.TEXT, CatalogProjectionTable.TEXT_FTS)
        val PRINTING_TABLES = setOf(CatalogProjectionTable.PRINTING, CatalogProjectionTable.LEGALITY)
    }
}
