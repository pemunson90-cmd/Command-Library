package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.CancellationException

/** Bounded local snapshots only. No import, recovery, network or implicit dataset activation. */
internal class LocalCatalogRulings(private val database: Database) : CatalogRulings {
    override suspend fun status(cancel: CancelToken): Outcome<RulingsStatus> = read(cancel) { tx ->
        RulingsStatus(active(tx, cancel))
    }

    override suspend fun rulings(oracleId: OracleId, page: PageRequest, cancel: CancelToken): Outcome<Page<StoredRuling>> {
        cancel.throwIfCancelled()
        val cursor = try {
            require(oracleId.source == PROVIDER && ORACLE.matches(oracleId.raw))
            page.cursor?.let(::decodeCursor)?.also { require(it.oracle == oracleId.raw) }
        } catch (_: Exception) { return Outcome.Failure(bulkError(FailureCode.INVALID_INPUT, "Invalid ruling Oracle or cursor")) }
        return read(cancel) { tx ->
            val generation = active(tx, cancel)
                ?: throw BulkFailure(Outcome.Unavailable(UnavailableReason.NOT_INSTALLED, "No rulings installed"))
            if (cursor != null && cursor.generation != generation.id.value)
                throw BulkFailure(Outcome.Failure(bulkError(FailureCode.STALE_GENERATION, "Ruling generation changed")))
            val identities = tx.query(bulkSql("""SELECT ordinal,
                CASE WHEN length(CAST(published_day AS BLOB))=10 THEN published_day END AS published_day
                FROM ruling_record r WHERE generation_id=:g AND oracle_provider='scryfall' AND oracle_id=:o
                AND (:first=1 OR published_day<:day OR (published_day=:day AND ordinal>:ordinal))
                ORDER BY r.published_day DESC,r.ordinal ASC LIMIT :limit""", page.limit + 1,
                "g" to generation.id.value, "o" to oracleId.raw, "first" to if(cursor == null) 1 else 0, "day" to (cursor?.day ?: ""),
                "ordinal" to (cursor?.ordinal ?: 0L), "limit" to page.limit + 1), cancel).bulkValue()
            // A bounded page cannot prove the whole count, but must reject an observed contradiction.
            require(identities.size.toLong() <= generation.recordCount)
            val result = ArrayList<StoredRuling>()
            var bytes = 0
            for (identity in identities.take(page.limit)) {
                cancel.throwIfCancelled()
                val ordinal = identity.bulkLong("ordinal").also { require(it > 0) }
                val day = RulingPublicationDay(identity.bulkText("published_day"))
                val row = tx.query(bulkSql("""SELECT
                    CASE WHEN length(CAST(record_payload AS BLOB))<=:max THEN record_payload END AS record_payload,
                    length(CAST(record_payload AS BLOB)) AS payload_bytes,
                    CASE WHEN length(CAST(publisher AS BLOB))<=8 THEN publisher END AS publisher
                    FROM ruling_record WHERE generation_id=:g AND ordinal=:ordinal""", 1,
                    "max" to ScryfallRulingRecordCodec.MAX_RECORD_BYTES, "g" to generation.id.value, "ordinal" to ordinal), cancel).bulkValue().single()
                val size = row.bulkLong("payload_bytes").also { require(it in 1..ScryfallRulingRecordCodec.MAX_RECORD_BYTES.toLong()) }.toInt()
                if (result.isNotEmpty() && size > BoundedRecordBatches.MAX_BATCH_BYTES - bytes) break
                val content = ScryfallRulingRecordCodec.decode(row.bulkText("record_payload"))
                require(content.oracleId == oracleId && content.publishedAt == day && content.publisher.upstreamValue == row.bulkText("publisher"))
                result.add(StoredRuling(RulingId(generation.id, ordinal), content, generation.provenance))
                bytes += size
            }
            val next = if (identities.size > result.size) result.last().let {
                encodeCursor(RulingCursor(generation.id.value, oracleId.raw, it.content.publishedAt.value, it.id.ordinal))
            } else null
            Page(SnapshotList.copyOf(result), next, SnapshotReference(PROVIDER, generation.id, null, null))
        }
    }

    private suspend fun active(tx: DbTransaction, cancel: CancelToken): RulingsGeneration? {
        // A join would hide a corrupt dangling pointer as an absent corpus.
        val pointers = tx.query(bulkSql("""SELECT singleton_id,
            CASE WHEN length(CAST(generation_id AS BLOB))<=512 THEN generation_id END AS generation_id
            FROM rulings_active LIMIT 2""", 2), cancel).bulkValue()
        if (pointers.isEmpty()) return null
        val pointer = pointers.single()
        require(pointer.bulkLong("singleton_id") == 1L)
        val id = GenerationId(pointer.bulkText("generation_id"))
        val row = tx.query(bulkSql("""SELECT
            CASE WHEN length(CAST(provider AS BLOB))<=64 THEN provider END AS provider,
            CASE WHEN length(CAST(status AS BLOB))<=64 THEN status END AS status,
            CASE WHEN length(CAST(language_scope AS BLOB))<=64 THEN language_scope END AS language_scope,
            CASE WHEN length(CAST(provenance_payload AS BLOB))<=:max THEN provenance_payload END AS provenance_payload,
            record_count,payload_schema_version FROM rulings_generation WHERE generation_id=:g""", 1,
            "max" to MAX_METADATA_BYTES, "g" to id.value), cancel).bulkValue().single()
        require(row.bulkText("provider") == PROVIDER.value && row.bulkLong("payload_schema_version") == 1L)
        val result = RulingsGeneration(id, AcquisitionPayloadCodec.decodeStoredProvenance(row.bulkText("provenance_payload")),
            row.bulkLong("record_count"), GenerationStatus.valueOf(row.bulkText("status")),
            AcquisitionLanguageScope.valueOf(row.bulkText("language_scope")))
        require(result.status == GenerationStatus.ACTIVE)
        return result
    }

    private suspend fun <T> read(cancel: CancelToken, action: suspend (DbTransaction) -> T): Outcome<T> {
        cancel.throwIfCancelled()
        // Validation is inside the callback: otherwise storage would classify corrupt data as input error.
        return when (val result = database.transaction({ tx ->
            try { Outcome.Success(action(tx)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: BulkFailure) { failure.outcome }
            catch (_: Exception) { Outcome.Failure(bulkError(FailureCode.CORRUPT_DATA, "Invalid stored ruling data")) }
        }, cancel)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> result
            is Outcome.Unavailable -> result
            is Outcome.Unknown -> result
        }
    }

    internal companion object {
        const val MAX_METADATA_BYTES = 128 * 1024
        private val PROVIDER = ProviderId("scryfall")
        private val ORACLE = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}

private data class RulingCursor(val generation: String, val oracle: String, val day: String, val ordinal: Long)
private fun encodeCursor(value: RulingCursor): String = "r1|${value.generation.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }}|scryfall|${value.oracle}|${value.day}|${value.ordinal}"
private fun decodeCursor(raw: String): RulingCursor {
    val parts = raw.split('|')
    require(parts.size == 6 && parts[0] == "r1" && parts[2] == "scryfall")
    val hex = parts[1]
    require(hex.length in 2..1024 && hex.length % 2 == 0 && hex.all { it in "0123456789abcdef" })
    val bytes = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    val generation = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    GenerationId(generation)
    RulingPublicationDay(parts[4])
    require(parts[5].matches(Regex("[1-9][0-9]*")))
    return RulingCursor(generation, parts[3], parts[4], parts[5].toLong().also { require(it > 0) })
}
