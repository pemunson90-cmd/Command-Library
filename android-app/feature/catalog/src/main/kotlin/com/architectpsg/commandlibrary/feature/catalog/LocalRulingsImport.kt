package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** A required trusted platform consumer owns job/source/consent checks and keeps its pin alive. */
internal fun interface RulingsVerifiedInputBinding {
    suspend fun withPinnedInput(jobId: EntityId, cancel: CancelToken,
        consume: suspend (VerifiedArtifact, EntityId, Flow<ByteChunk>) -> RulingsImportResult): RulingsImportResult
}

/** Safety ceilings, not source estimates; callers cannot change language/source admission. */
internal data class RulingsImportPolicy(
    val maxRawBytes: Long = 2L * 1024 * 1024 * 1024,
    val maxDecodedBytes: Long = 2L * 1024 * 1024 * 1024,
    val maxRecords: Long = 500_000,
    val maxBatchBytes: Int = 2 * 1024 * 1024,
    val maxBatchRecords: Int = 1_000,
    val maxChunkBytes: Int = 64 * 1024,
) {
    init {
        require(maxRawBytes in 1..2L * 1024 * 1024 * 1024 && maxDecodedBytes in 1..2L * 1024 * 1024 * 1024)
        require(maxRecords in 1..500_000 && maxBatchBytes in 128 * 1024..2 * 1024 * 1024)
        require(maxBatchRecords in 1..1_000 && maxChunkBytes in 1..64 * 1024)
    }
}

/** Real atomic storage path, deliberately unbound until a reviewed trusted source adapter exists. */
internal class LocalRulingsImport(
    private val database: Database, private val clock: Clock, private val ids: IdSource,
    private val binding: RulingsVerifiedInputBinding, private val policy: RulingsImportPolicy = RulingsImportPolicy(),
) : CatalogRulingsImport {
    override suspend fun `import`(jobId: EntityId, onProgress: (JobProgress) -> Unit, cancel: CancelToken): Outcome<RulingsImportResult> =
        withCatalogImportOwner(cancel) { owner ->
            var staged: GenerationId? = null
            var committed: RulingsImportResult? = null
            var candidate: RulingsImportResult? = null
            var envelope: String? = null
            var acknowledgement: RulingActivation? = null
            var invoked = false
            try {
                boundedIdentity(jobId.value)
                val returned = binding.withPinnedInput(jobId, cancel) { artifact, consentId, chunks ->
                    check(!invoked); invoked = true
                    try {
                        require(artifact.jobId == jobId && artifact.bytes in 1..policy.maxRawBytes)
                        require(artifact.framing == ArtifactFraming.JSONL && artifact.compression == ArtifactCompression.GZIP)
                        val source = artifact.provenance
                        require(source.provider == ProviderId("scryfall") && source.languageScope == AcquisitionLanguageScope.RULINGS_LANGUAGE_NOT_ASSERTED)
                        require(source.consistency == AcquisitionConsistency.FILE_REPRESENTATION)
                        require(RULINGS_URI.matches(source.requestedUri.value) && RULINGS_URI.matches(source.finalUri.value))
                        boundedIdentity(consentId.value)
                        val generationId = GenerationId(ids.nextId().also(::boundedIdentity))
                        val prior = LocalCatalogRulings(database).status(cancel).bulkValue().active?.id
                        val provenance = Provenance(source.provider, source.requestedUri, source.sourceVersion, source.sourceDate,
                            source.retrievalCompletedAt, Knowledge.Unknown("Effective instant not supplied", SnapshotList.empty()),
                            generationId, artifact.sha256, source.notice.bulkKnown())
                        val stagingPayload = AcquisitionPayloadCodec.encodeStoredProvenance(provenance)
                        requireMetadata(stagingPayload)
                        database.transaction({ tx ->
                            if (tx.bulkWrite("""INSERT INTO rulings_generation(generation_id,provider,status,language_scope,provenance_payload,record_count,payload_schema_version)
                                VALUES (:g,'scryfall','STAGING','RULINGS_LANGUAGE_NOT_ASSERTED',:payload,0,1)""", cancel,
                                "g" to generationId.value, "payload" to stagingPayload) != 1L) tx.abort(bulkError(FailureCode.PERSISTENCE, "Ruling staging insert failed"))
                        }, cancel).bulkValue()
                        // An unacknowledged initial INSERT remains recoverable; never claim a collided ID.
                        staged = generationId
                        val batch = ArrayList<Pair<String, RulingContent>>()
                        var batchBytes = 0
                        var records = 0L
                        suspend fun flush() {
                            if (batch.isEmpty()) return
                            val first = records - batch.size + 1
                            database.transaction({ tx ->
                                for ((index, item) in batch.withIndex()) {
                                    cancel.throwIfCancelled(); currentCoroutineContext().ensureActive()
                                    val (raw, value) = item
                                    val changed = tx.bulkWrite("""INSERT INTO ruling_record(generation_id,ordinal,oracle_provider,oracle_id,publisher,published_day,record_payload)
                                        SELECT :g,:ordinal,'scryfall',:oracle,:publisher,:day,:payload
                                        WHERE EXISTS (SELECT 1 FROM rulings_generation WHERE generation_id=:g AND status='STAGING')
                                        AND NOT EXISTS (SELECT 1 FROM rulings_active WHERE generation_id=:g)""", cancel,
                                        "g" to generationId.value, "ordinal" to first + index, "oracle" to value.oracleId.raw,
                                        "publisher" to value.publisher.upstreamValue, "day" to value.publishedAt.value, "payload" to raw)
                                    if (changed != 1L) tx.abort(bulkError(FailureCode.STALE_GENERATION, "Ruling staging generation changed"))
                                }
                            }, cancel).bulkValue()
                            batch.clear(); batchBytes = 0
                            onProgress(JobProgress(jobId, JobPhase.STAGING, records, artifact.recordCount, "Staging rulings", null))
                        }
                        val lines = JsonLinesFramer(JsonLinesLimits(policy.maxDecodedBytes, ScryfallRulingRecordCodec.MAX_RECORD_BYTES,
                            policy.maxRecords, ScryfallRulingRecordCodec.MAX_NESTING), cancel)
                        val proof = FullCatalogDecodedInput(ArtifactDecodeLimits(policy.maxRawBytes, policy.maxDecodedBytes, policy.maxChunkBytes), cancel)
                            .consume(ArtifactCompression.GZIP, chunks) { chunk ->
                                lines.accept(chunk) { raw ->
                                    val content = ScryfallRulingRecordCodec.decode(raw)
                                    val bytes = raw.toByteArray(Charsets.UTF_8).size
                                    if (batch.isNotEmpty() && (batch.size == policy.maxBatchRecords || bytes > policy.maxBatchBytes - batchBytes)) flush()
                                    batch.add(raw to content); batchBytes += bytes; records++
                                    if (batch.size == policy.maxBatchRecords || batchBytes == policy.maxBatchBytes) flush()
                                }
                            }
                        lines.finish(); flush()
                        require(records > 0 && records == lines.recordCount && proof.decodedBytes == lines.observedBytes && proof.decodedHash.hex == lines.sha256)
                        require(proof.rawBytes == artifact.bytes && proof.rawHash.hex.equals(artifact.sha256.hex, true))
                        artifact.recordCount.bulkKnown()?.let { require(it == records) }
                        val actual = database.query(bulkSql("SELECT count(*) AS n,min(ordinal) AS first,max(ordinal) AS last FROM ruling_record WHERE generation_id=:g", 1,
                            "g" to generationId.value), cancel).bulkValue().single()
                        require(actual.bulkLong("n") == records && actual.bulkLong("first") == 1L && actual.bulkLong("last") == records)
                        val generation = RulingsGeneration(generationId, provenance.copy(checksum = proof.rawHash), records, GenerationStatus.ACTIVE)
                        val result = RulingsImportResult(generation, proof.rawBytes, proof.rawHash, proof.decodedBytes, proof.decodedHash, clock.utcNow(), artifact)
                        val payload = AcquisitionPayloadCodec.encodeRulingsInstallation(result, consentId).also(::requireMetadata)
                        cancel.throwIfCancelled(); currentCoroutineContext().ensureActive()
                        candidate = result; envelope = payload
                        database.transaction({ tx ->
                            val pointers = tx.query(bulkSql("SELECT singleton_id,generation_id FROM rulings_active LIMIT 2", 2), cancel).bulkValue()
                            val current = pointers.singleOrNull()?.also { require(it.bulkLong("singleton_id") == 1L) }?.bulkText("generation_id")
                            if (pointers.size > 1 || current != prior?.value) tx.abort(bulkError(FailureCode.STALE_GENERATION, "Active ruling generation changed"))
                            if (tx.bulkWrite("""UPDATE rulings_generation SET status='ACTIVE',provenance_payload=:payload,record_count=:count,payload_schema_version=2
                                WHERE generation_id=:g AND status='STAGING' AND NOT EXISTS (SELECT 1 FROM rulings_active WHERE generation_id=:g)""", cancel,
                                "payload" to payload, "count" to records, "g" to generationId.value) != 1L) tx.abort(bulkError(FailureCode.STALE_GENERATION, "Ruling staging generation changed"))
                            if (prior != null && tx.bulkWrite("UPDATE rulings_generation SET status='RETIRED' WHERE generation_id=:g AND status='ACTIVE'", cancel,
                                    "g" to prior.value) != 1L) tx.abort(bulkError(FailureCode.CORRUPT_DATA, "Previous ruling generation inconsistent"))
                            val changed = if (prior == null) tx.bulkWrite("INSERT INTO rulings_active(singleton_id,generation_id) SELECT 1,:g WHERE NOT EXISTS (SELECT 1 FROM rulings_active)", cancel, "g" to generationId.value)
                            else tx.bulkWrite("UPDATE rulings_active SET generation_id=:g WHERE singleton_id=1 AND generation_id=:prior", cancel, "g" to generationId.value, "prior" to prior.value)
                            if (changed != 1L) tx.abort(bulkError(FailureCode.STALE_GENERATION, "Ruling active pointer changed"))
                        }, cancel).bulkValue()
                        committed = result; staged = null
                        result
                    } catch (failure: Exception) {
                        // Still inside the trusted pin: the binding may release it only after this finishes.
                        if (committed == null) candidate?.let { acknowledgement = acknowledge(it, requireNotNull(envelope)) }
                        throw failure
                    }
                }
                check(invoked && returned == committed)
                Outcome.Success(requireNotNull(committed))
            } catch (failure: Exception) {
                committed?.let { return@withCatalogImportOwner Outcome.Success(it) }
                candidate?.let {
                    when (acknowledgement ?: RulingActivation.INCONCLUSIVE) {
                        RulingActivation.INSTALLED -> return@withCatalogImportOwner Outcome.Success(it)
                        RulingActivation.ROLLED_BACK -> Unit
                        RulingActivation.INCONCLUSIVE -> {
                            if (failure is CancellationException) throw failure
                            return@withCatalogImportOwner Outcome.Unknown("Ruling activation acknowledgement unavailable", SnapshotList.empty())
                        }
                    }
                }
                staged?.let { id ->
                    withContext(NonCancellable) {
                        try { withTimeout(500) { RulingsGenerationCleanup(database).reclaimOwned(id, owner, BULK_NO_CANCEL, 4) } }
                        catch (_: Exception) { /* Left nonactive for later bounded recovery. */ }
                    }
                }
                if (failure is CancellationException) throw failure
                when (failure) {
                    is BulkFailure -> failure.outcome
                    is ArtifactReadException -> Outcome.Failure(bulkError(failure.failure.code, "Verified ruling input unavailable"))
                    else -> Outcome.Failure(bulkError(FailureCode.INVALID_INPUT, "Ruling input or import validation failed"))
                }
            }
        }

    private suspend fun acknowledge(candidate: RulingsImportResult, payload: String): RulingActivation = withContext(NonCancellable) {
        try {
            withTimeout(500) {
                val row = database.query(bulkSql("""SELECT status,payload_schema_version,record_count,
                    CASE WHEN length(CAST(provenance_payload AS BLOB))<=:max THEN provenance_payload END AS provenance_payload,
                    (SELECT count(*) FROM rulings_active) AS pointers,
                    (SELECT generation_id FROM rulings_active WHERE singleton_id=1) AS active_generation
                    FROM rulings_generation WHERE generation_id=:g""", 1,
                    "max" to LocalCatalogRulings.MAX_METADATA_BYTES, "g" to candidate.generation.id.value), BULK_NO_CANCEL).bulkValue().singleOrNull()
                    ?: return@withTimeout RulingActivation.INCONCLUSIVE
                if (row.bulkLong("pointers") > 1) return@withTimeout RulingActivation.INCONCLUSIVE
                val active = row.bulkOptionalText("active_generation")
                if (active != candidate.generation.id.value && row.bulkText("status") in setOf("STAGING", "FAILED")) RulingActivation.ROLLED_BACK
                else if (active == candidate.generation.id.value && row.bulkText("status") == "ACTIVE" && row.bulkLong("payload_schema_version") == 2L &&
                    row.bulkLong("record_count") == candidate.generation.recordCount && row.bulkText("provenance_payload") == payload &&
                    AcquisitionPayloadCodec.decodeRulingsInstallation(payload).result == candidate) RulingActivation.INSTALLED
                else RulingActivation.INCONCLUSIVE
            }
        } catch (_: TimeoutCancellationException) { RulingActivation.INCONCLUSIVE }
        catch (_: Exception) { RulingActivation.INCONCLUSIVE }
    }
}

private enum class RulingActivation { INSTALLED, ROLLED_BACK, INCONCLUSIVE }
private fun boundedIdentity(value: String) { require(value.isNotBlank() && value.toByteArray(Charsets.UTF_8).size <= 128 && '\u0000' !in value) }
private fun requireMetadata(payload: String) { require(payload.toByteArray(Charsets.UTF_8).size <= LocalCatalogRulings.MAX_METADATA_BYTES) }
private val RULINGS_URI = Regex("https://data\\.scryfall\\.io(?::443)?/rulings/[A-Za-z0-9][A-Za-z0-9._-]*\\.jsonl\\.gz")
