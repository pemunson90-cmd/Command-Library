package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Complete durable evidence, separate from the small synchronous profile cache. No wire dispatch. */
internal class RulingsReceiptStore(
    private val database: Database,
    private val maxReceipts: Int = RulingsReceiptBudgets.MAX_RECEIPTS,
    private val maxTotalBytes: Long = RulingsReceiptBudgets.MAX_TOTAL_BYTES,
) {
    init { require(maxReceipts in 1..RulingsReceiptBudgets.MAX_RECEIPTS && maxTotalBytes in 1..RulingsReceiptBudgets.MAX_TOTAL_BYTES) }
    private val admission = Mutex()
    suspend fun byMetadataJob(id: EntityId, cancel: CancelToken): Outcome<RulingsSourceReceipt?> = receiptAttempt {
        database.query(receiptQuery("metadata_job_id=:id", id.value), cancel).bulkValue().singleOrNull()?.let(::decode)
    }
    suspend fun byId(id: EntityId, cancel: CancelToken): Outcome<RulingsSourceReceipt?> = receiptAttempt {
        database.query(receiptQuery("receipt_id=:id", id.value), cancel).bulkValue().singleOrNull()?.let(::decode)
    }

    /** Caller publishes authority only after this returns validated durable Success. */
    suspend fun save(value: RulingsSourceReceipt, cancel: CancelToken): Outcome<RulingsSourceReceipt> =
        admission.withLock { saveLocked(value, cancel) }
    private suspend fun saveLocked(value: RulingsSourceReceipt, cancel: CancelToken): Outcome<RulingsSourceReceipt> {
        cancel.throwIfCancelled()
        val payload = try { RulingsSourceReceiptCodec.encode(value) }
        catch (_: Exception) { return Outcome.Failure(bulkError(FailureCode.INVALID_INPUT, "Invalid source receipt input")) }
        var attempted = false
        val result = try {
            flatten(database.transaction({ tx ->
                receiptAttempt {
                    val existing = tx.query(receiptQuery("metadata_job_id=:id", value.metadataPlan.jobId.value), cancel).bulkValue().singleOrNull()?.let(::decode)
                    if(existing != null) {
                        requireSameEvidence(existing, value)
                        return@receiptAttempt existing
                    }
                    val stats = tx.query(statistics(), cancel).bulkValue().single()
                    val count = stats.bulkLong("n"); val bytes = stats.bulkLong("bytes")
                    require(count in 0..maxReceipts.toLong() && bytes in 0..maxTotalBytes)
                    if(count == maxReceipts.toLong() || payload.toByteArray().size > maxTotalBytes - bytes)
                        throw BulkFailure(Outcome.Unavailable(UnavailableReason.SOURCE_UNAVAILABLE, "Source receipt capacity reached"))
                    attempted = true
                    if(tx.bulkWrite("INSERT INTO catalog_source_receipt(receipt_id,metadata_job_id,payload_schema_version,receipt_payload) VALUES (:id,:job,1,:payload)", cancel,
                            "id" to value.id.value, "job" to value.metadataPlan.jobId.value, "payload" to payload) != 1L)
                        tx.abort(bulkError(FailureCode.PERSISTENCE, "Source receipt insert failed"))
                    val stored = tx.query(receiptQuery("receipt_id=:id", value.id.value), cancel).bulkValue().single()
                    require(stored.bulkText("receipt_payload") == payload)
                    decode(stored).also { require(it == value) }
                }
            }, cancel))
        } catch (cancelled: CancellationException) {
            if(attempted) acknowledge(value)?.let { return Outcome.Success(it) }
            throw cancelled
        }
        if(result is Outcome.Success || !attempted) return result
        // A typed failure can also be an unacknowledged durable COMMIT; never publish speculatively.
        return when (val acknowledged = acknowledgeResult(value)) {
            is Outcome.Success -> acknowledged.value?.let { Outcome.Success(it) } ?: result
            else -> Outcome.Unknown("Source receipt acknowledgement unavailable", SnapshotList.empty())
        }
    }

    suspend fun loadFacts(cancel: CancelToken): Outcome<List<RulingsProfileFacts>> = admission.withLock { loadFactsLocked(cancel) }
    private suspend fun loadFactsLocked(cancel: CancelToken): Outcome<List<RulingsProfileFacts>> = receiptAttempt {
        val stats = database.query(statistics(), cancel).bulkValue().single()
        val expected = stats.bulkLong("n")
        require(expected in 0..maxReceipts.toLong() && stats.bulkLong("bytes") in 0..maxTotalBytes)
        val result = ArrayList<RulingsProfileFacts>()
        var last = ""
        while(true) {
            cancel.throwIfCancelled()
            val ids = database.query(bulkSql("""SELECT CASE WHEN length(CAST(receipt_id AS BLOB))<=128 THEN receipt_id END AS receipt_id
                FROM catalog_source_receipt r WHERE r.receipt_id>:last ORDER BY r.receipt_id LIMIT 100""", 100, "last" to last), cancel).bulkValue()
            if(ids.isEmpty()) break
            for(row in ids) {
                require(result.size < maxReceipts)
                val id = row.bulkText("receipt_id").also(TrustedRulingsProfiles::identity)
                val receipt = byId(EntityId(id), cancel).bulkValue() ?: error("Missing source receipt")
                result.add(receipt.facts()); last = id
            }
        }
        require(result.size.toLong() == expected)
        result
    }

    private suspend fun acknowledge(value: RulingsSourceReceipt): RulingsSourceReceipt? = when(val result = acknowledgeResult(value)) {
        is Outcome.Success -> result.value
        else -> null
    }
    private suspend fun acknowledgeResult(value: RulingsSourceReceipt): Outcome<RulingsSourceReceipt?> = withContext(NonCancellable) {
        try {
            withTimeout(500) {
                when(val found = byMetadataJob(value.metadataPlan.jobId, BULK_NO_CANCEL)) {
                    is Outcome.Success -> {
                        found.value?.let { requireSameEvidence(it, value) }
                        found
                    }
                    else -> found
                }
            }
        } catch (_: Exception) { Outcome.Unknown("Source receipt acknowledgement unavailable", SnapshotList.empty()) }
    }
    private fun decode(row: RowData): RulingsSourceReceipt {
        require(row.bulkLong("payload_schema_version") == 1L)
        return RulingsSourceReceiptCodec.decode(row.bulkText("receipt_payload")).also {
            require(it.id.value == row.bulkText("receipt_id") && it.metadataPlan.jobId.value == row.bulkText("metadata_job_id"))
        }
    }
    private fun requireSameEvidence(a: RulingsSourceReceipt, b: RulingsSourceReceipt) {
        require(a.metadataPlan == b.metadataPlan && a.metadataArtifact == b.metadataArtifact && a.metadataConsentId == b.metadataConsentId && a.metadataBody == b.metadataBody)
    }
    private fun receiptQuery(predicate: String, id: String) = bulkSql("""SELECT
        CASE WHEN length(CAST(receipt_id AS BLOB))<=128 THEN receipt_id END AS receipt_id,
        CASE WHEN length(CAST(metadata_job_id AS BLOB))<=128 THEN metadata_job_id END AS metadata_job_id,
        payload_schema_version,CASE WHEN length(CAST(receipt_payload AS BLOB))<=:max THEN receipt_payload END AS receipt_payload
        FROM catalog_source_receipt WHERE $predicate""", 1, "max" to RulingsSourceReceiptCodec.MAX_BYTES, "id" to id)
    private fun statistics() = bulkSql("SELECT count(*) AS n,coalesce(sum(length(CAST(receipt_payload AS BLOB))),0) AS bytes FROM catalog_source_receipt", 1)
}

private suspend fun <T> receiptAttempt(action: suspend () -> T): Outcome<T> = try { Outcome.Success(action()) }
catch (cancelled: CancellationException) { throw cancelled }
catch (failure: BulkFailure) { failure.outcome }
catch (aborted: TransactionAbort) { Outcome.Failure(aborted.failure) }
catch (_: Exception) { Outcome.Failure(bulkError(FailureCode.CORRUPT_DATA, "Invalid stored source receipt")) }

private fun <T> flatten(value: Outcome<Outcome<T>>): Outcome<T> = when(value) {
    is Outcome.Success -> value.value
    is Outcome.Failure -> value
    is Outcome.Unavailable -> value
    is Outcome.Unknown -> value
}
