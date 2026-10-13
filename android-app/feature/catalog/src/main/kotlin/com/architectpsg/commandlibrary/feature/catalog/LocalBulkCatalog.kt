package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.common.sameCanonicalGameData
import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Trusted adapter holds the immutable completed artifact/URI binding through the entire block. */
internal fun interface CatalogVerifiedInputBinding {
    suspend fun withPinnedInput(source: CatalogBulkImportSource, cancel: CancelToken,
        consume: suspend (Flow<ByteChunk>, EntityId) -> CatalogBulkImportResult): CatalogBulkImportResult
}

/** Complete bulk capability; deliberately internal and unbound until reviewed platform factories exist. */
internal class LocalBulkCatalog(
    private val database: Database, private val clock: Clock, private val ids: IdSource,
    private val verifiedInput: CatalogVerifiedInputBinding,
    private val limits: CatalogBulkImportLimits = CatalogBulkImportLimits(),
) : CatalogBulkImport {
    override suspend fun `import`(source: CatalogBulkImportSource, chunks: Flow<ByteChunk>, onProgress: (JobProgress) -> Unit,
        cancel: CancelToken): Outcome<CatalogBulkImportResult> {
        return withCatalogImportOwner(cancel) { owner ->
        var committed: CatalogBulkImportResult? = null
        var staged: GenerationId? = null
        var activationCandidate: CatalogBulkImportResult? = null
        var activationEnvelope: String? = null
        try {
            require(source.contentUri.value.toByteArray(Charsets.UTF_8).size <= 4096)
            require(source.displayName.toByteArray(Charsets.UTF_8).size <= 512)
            require(source.expectedBytes.bulkKnown()?.let { it <= limits.maxRawBytes } != false)
            val sourcePayload = AcquisitionPayloadCodec.encodeCatalogSource(source)
            val generation = GenerationId(ids.nextId())
            require(generation.value.toByteArray(Charsets.UTF_8).size <= 128 && '\u0000' !in generation.value)
            val job = EntityId(ids.nextId()); val observed = clock.utcNow()
            val prior = database.query(bulkSql("SELECT generation_id FROM catalog_active WHERE dataset_key=:key", 1, "key" to BULK_DATASET), cancel).bulkValue().firstOrNull()?.bulkText("generation_id")
            suspend fun consume(input: Flow<ByteChunk>, consentId: EntityId?): CatalogBulkImportResult {
                require((source.inputKind == SourceInputKind.VERIFIED_ARTIFACT) == (consentId != null))
                consentId?.let { require(it.value.toByteArray(Charsets.UTF_8).size <= 128 && '\u0000' !in it.value) }
                check(staged == null) // Trusted callback is single-use, never retries ambiguous work.
                database.transaction({ tx ->
                    val n = tx.bulkWrite("""INSERT INTO catalog_generation(generation_id,provider,format,content_uri,display_name,source_uri,source_version,source_date_epoch_seconds,source_date_nano,observed_at_epoch_seconds,observed_at_nano,content_sha256,observed_bytes,language_scope,payload_schema_version,parser_version,notice_payload,oracle_count,face_count,printing_count,status,activated_at_epoch_seconds,activated_at_nano,input_kind,artifact_job_id,source_payload) VALUES(:id,:provider,:format,:content,:display,:source,:source_version,:date,:date_nano,:observed,:observed_nano,'',0,'en',:version,:parser,:notice,0,0,0,'STAGING',NULL,NULL,:kind,:job,:payload)""", cancel,
                        "id" to generation.value, "provider" to source.provider.value, "format" to ScryfallCodec.FORMAT,
                        "content" to source.contentUri.value, "display" to source.displayName, "source" to source.sourceUri.bulkKnown()?.value,
                        "source_version" to source.verifiedArtifact?.provenance?.sourceVersion?.bulkKnown(),
                        "date" to source.sourceDate.bulkKnown()?.epochSeconds, "date_nano" to source.sourceDate.bulkKnown()?.nanoAdjustment,
                        "observed" to observed.epochSeconds, "observed_nano" to observed.nanoAdjustment,
                        "version" to ScryfallCodec.VERSION, "parser" to ScryfallCodec.PARSER,
                        "notice" to SourceMetadataCodec.encode(source.legacySource()), "kind" to source.inputKind.name,
                        "job" to source.verifiedArtifact?.jobId?.value, "payload" to sourcePayload)
                    if (n != 1L) tx.abort(bulkError(FailureCode.PERSISTENCE, "Generation insert did not take effect"))
                }, cancel).bulkValue()
                staged = generation
                var oracles = 0L; var faces = 0L; var printings = 0L; var assets = 0L
                val batch = ArrayList<String>(); var batchBytes = 0
                suspend fun flush() {
                    if (batch.isEmpty()) return
                    val delta = database.transaction({ tx ->
                        var oc = 0L; var fc = 0L; var ac = 0L
                        for (raw in batch) {
                            cancel.throwIfCancelled(); currentCoroutineContext().ensureActive()
                            val (card, printing) = source.map(raw, generation, observed, null, limits.maxNesting)
                            if (tx.bulkRead(bulkSql("SELECT printing_id FROM printing WHERE generation_id=:g AND provider=:p AND printing_id=:id", 1,
                                    "g" to generation.value, "p" to printing.id.source.value, "id" to printing.id.raw), cancel).isNotEmpty()) tx.abort(bulkError(FailureCode.INVALID_INPUT, "Duplicate printing identity"))
                            if (card != null) {
                                val row = tx.bulkRead(bulkSql("SELECT canonical_digest,record_payload FROM oracle_card WHERE generation_id=:g AND provider=:p AND oracle_id=:id AND language='en'", 1,
                                    "g" to generation.value, "p" to card.oracleId.source.value, "id" to card.oracleId.raw), cancel).firstOrNull()
                                if (row == null) { tx.insertCard(generation, card, raw, cancel); oc++; fc += card.faces.size }
                                else {
                                    val first = source.map(row.bulkText("record_payload"), generation, observed, null, limits.maxNesting).first
                                    if (first == null || !sameCanonicalGameData(first, card)) tx.abort(bulkError(FailureCode.INVALID_INPUT, "Canonical game data conflict"))
                                }
                            }
                            tx.insertPrinting(generation, printing, raw, cancel); ac += printing.faceAssets.size
                        }
                        Triple(oc,fc,ac)
                    }, cancel).bulkValue()
                    oracles += delta.first; faces += delta.second; assets += delta.third; printings += batch.size
                    batch.clear(); batchBytes = 0
                    onProgress(JobProgress(job, JobPhase.STAGING, printings, source.verifiedArtifact?.recordCount ?: Knowledge.Unknown("Count not supplied", SnapshotList.empty()), "Staging catalog", null))
                }
                suspend fun record(raw: String) {
                    val size = raw.toByteArray(Charsets.UTF_8).size
                    require(size <= limits.maxRecordBytes)
                    if (size > limits.maxOrdinaryBatchBytes) { flush(); batch.add(raw); flush() }
                    else {
                        if (batch.size == limits.maxBatchRecords || size > limits.maxOrdinaryBatchBytes - batchBytes) flush()
                        batch.add(raw); batchBytes += size
                        if (batch.size == limits.maxBatchRecords || batchBytes == limits.maxOrdinaryBatchBytes) flush()
                    }
                }
                val framingLimits = FullFeedFramingLimits(limits.maxDecodedBytes, limits.maxPrintingRecords, limits.maxRecordBytes, limits.maxNesting)
                val array = if (source.framing == ArtifactFraming.JSON_ARRAY) JsonArrayFramer(framingLimits, cancel) else null
                val lines = if (array == null) JsonLinesFramer(framingLimits, cancel) else null
                val proof = FullCatalogDecodedInput(limits, cancel).consume(source.compression, input) { chunk ->
                    if (array != null) array.accept(chunk, ::record) else requireNotNull(lines).accept(chunk, ::record)
                }
                if (array != null) array.finish() else requireNotNull(lines).finish()
                flush()
                require(printings > 0)
                require(source.expectedBytes.bulkKnown()?.let { it == proof.rawBytes } != false)
                require(source.expectedChecksum.bulkKnown()?.let { it.hex.equals(proof.rawHash.hex, true) } != false)
                source.verifiedArtifact?.recordCount?.bulkKnown()?.let { require(it == printings) }
                val memberships = resolveMemberships(source, generation, observed, cancel)
                // All expensive scans happen before the short pointer transaction.
                val actual = database.query(bulkSql("""SELECT (SELECT count(*) FROM oracle_card WHERE generation_id=:g) AS oracles,(SELECT count(*) FROM card_face WHERE generation_id=:g) AS faces,(SELECT count(*) FROM printing WHERE generation_id=:g) AS printings,(SELECT count(*) FROM printing_face_asset WHERE generation_id=:g) AS assets,(SELECT count(*) FROM printing_oracle_membership WHERE generation_id=:g) AS memberships,(SELECT count(*) FROM catalog_name_fts WHERE generation_id=:g) AS fts""", 1, "g" to generation.value), cancel).bulkValue().single()
                require(actual.bulkLong("oracles") == oracles && actual.bulkLong("faces") == faces && actual.bulkLong("printings") == printings && actual.bulkLong("assets") == assets && actual.bulkLong("memberships") == memberships && actual.bulkLong("fts") == oracles)
                val invalidFts = database.query(bulkSql("""WITH indexed AS (SELECT provider,oracle_id,language,count(*) AS n FROM catalog_name_fts WHERE generation_id=:g GROUP BY provider,oracle_id,language) SELECT count(*) AS n FROM indexed i LEFT JOIN oracle_card c ON c.generation_id=:g AND c.provider=i.provider AND c.oracle_id=i.oracle_id AND c.language=i.language WHERE c.oracle_id IS NULL OR i.n<>1""", 1, "g" to generation.value), cancel).bulkValue().single().bulkLong("n")
                if (invalidFts != 0L) throw BulkFailure(Outcome.Failure(bulkError(FailureCode.CORRUPT_DATA, "Staged search coverage differs")))
                val activated = clock.utcNow(); val counts = CatalogCounts(oracles, printings, faces, 0)
                val provenance = source.provenance(generation, observed, proof.rawHash)
                val result = CatalogBulkImportResult(CatalogImportResult(CatalogGeneration(generation, provenance, SnapshotList.of(LanguageTag.ENGLISH), ScryfallCodec.FORMAT, counts, GenerationStatus.ACTIVE), counts, provenance,
                    CatalogImportVerification(proof.rawBytes, proof.rawHash, if (source.expectedChecksum is Knowledge.Known) ChecksumVerification.MATCHED_EXPECTED else ChecksumVerification.COMPUTED_WITHOUT_EXPECTATION, true, 0), activated),
                    proof.decodedBytes, proof.decodedHash, memberships, source.verifiedArtifact?.let { Knowledge.Known(it) } ?: Knowledge.Absent("Selected input"))
                val envelope = BulkSourceEnvelope.encode(source, proof, memberships, consentId)
                cancel.throwIfCancelled(); currentCoroutineContext().ensureActive()
                // The adapter checks the original caller before shielding its actual COMMIT.
                // Only an ambiguous acknowledgement is reconciled in NonCancellable below.
                activationCandidate = result; activationEnvelope = envelope
                database.transaction({ tx ->
                        val current = tx.bulkRead(bulkSql("SELECT generation_id FROM catalog_active WHERE dataset_key=:key", 1, "key" to BULK_DATASET), cancel).firstOrNull()?.bulkText("generation_id")
                        if (current != prior) tx.abort(bulkError(FailureCode.STALE_GENERATION, "Active catalog changed"))
                        if (tx.bulkWrite("UPDATE catalog_generation SET status='ACTIVE',content_sha256=:hash,observed_bytes=:bytes,oracle_count=:oracles,face_count=:faces,printing_count=:printings,source_payload=:payload,activated_at_epoch_seconds=:at,activated_at_nano=:nano WHERE generation_id=:g AND status='STAGING'", cancel,
                                "hash" to proof.rawHash.hex, "bytes" to proof.rawBytes, "oracles" to oracles, "faces" to faces, "printings" to printings, "payload" to envelope, "at" to activated.epochSeconds, "nano" to activated.nanoAdjustment, "g" to generation.value) != 1L) tx.abort(bulkError(FailureCode.STALE_GENERATION, "Staging generation changed"))
                        if (prior != null && tx.bulkWrite("UPDATE catalog_generation SET status='RETIRED' WHERE generation_id=:g AND status='ACTIVE'", cancel, "g" to prior) != 1L) tx.abort(bulkError(FailureCode.CORRUPT_DATA, "Previous active catalog inconsistent"))
                        val changed = if (prior == null) tx.bulkWrite("INSERT INTO catalog_active(dataset_key,generation_id) SELECT :key,:g WHERE NOT EXISTS (SELECT 1 FROM catalog_active WHERE dataset_key=:key)", cancel, "key" to BULK_DATASET, "g" to generation.value)
                            else tx.bulkWrite("UPDATE catalog_active SET generation_id=:g WHERE dataset_key=:key AND generation_id=:prior", cancel, "g" to generation.value, "key" to BULK_DATASET, "prior" to prior)
                        if (changed != 1L) tx.abort(bulkError(FailureCode.STALE_GENERATION, "Active catalog pointer changed"))
                        cancel.throwIfCancelled(); currentCoroutineContext().ensureActive()
                    }, cancel).bulkValue()
                committed = result; staged = null
                return result
            }
            if (source.inputKind == SourceInputKind.VERIFIED_ARTIFACT) verifiedInput.withPinnedInput(source, cancel) { input, consentId -> consume(input, consentId) } else consume(chunks, null)
            Outcome.Success(requireNotNull(committed))
        } catch (e: Exception) {
            committed?.let { return@withCatalogImportOwner Outcome.Success(it) }
            activationCandidate?.let { candidate ->
                val acknowledgement = withContext(NonCancellable) {
                    activationAcknowledgement(candidate, requireNotNull(activationEnvelope))
                }
                when (acknowledgement) {
                    ActivationAcknowledgement.INSTALLED -> return@withCatalogImportOwner Outcome.Success(candidate)
                    ActivationAcknowledgement.ROLLED_BACK -> Unit
                    ActivationAcknowledgement.INCONCLUSIVE -> {
                        // No cleanup when an ACTIVE generation or unacknowledged COMMIT is possible.
                        if (e is CancellationException) throw e
                        return@withCatalogImportOwner Outcome.Unknown("Catalog activation acknowledgement unavailable", SnapshotList.empty())
                    }
                }
            }
            staged?.let { id -> withContext(NonCancellable) { CatalogGenerationCleanup(database).reclaimOwned(id, BULK_NO_CANCEL, owner) } }
            if (e is CancellationException) throw e
            when (e) {
                is BulkFailure -> e.outcome
                is ArtifactReadException -> Outcome.Failure(bulkError(e.failure.code, "Verified artifact read failed"))
                else -> Outcome.Failure(bulkError(FailureCode.INVALID_INPUT, "Catalog input or import validation failed"))
            }
        }
    }

    }

    private suspend fun activationAcknowledgement(candidate: CatalogBulkImportResult, envelope: String): ActivationAcknowledgement {
        val generation = candidate.installed.generation.id.value
        val rows = try { when (val result = database.query(bulkSql("SELECT g.*,(SELECT generation_id FROM catalog_active WHERE dataset_key=:key) AS active_generation FROM catalog_generation g WHERE g.generation_id=:g", 1,
                "key" to BULK_DATASET, "g" to generation), BULK_NO_CANCEL)) {
            is Outcome.Success -> result.value
            else -> return ActivationAcknowledgement.INCONCLUSIVE
        } } catch (_: Exception) { return ActivationAcknowledgement.INCONCLUSIVE }
        val row = rows.singleOrNull() ?: return ActivationAcknowledgement.INCONCLUSIVE
        return try {
            val pointer = row.bulkOptionalText("active_generation")
            if (pointer != generation && row.bulkText("status") in setOf("STAGING", "FAILED")) ActivationAcknowledgement.ROLLED_BACK
            else {
                val installed = row.bulkInstalledSource()
                val expected = candidate.installed
                require(pointer == generation && installed.generation == expected.generation.id && installed.source == BulkSourceEnvelope.decode(envelope).source)
                require(row.bulkText("source_payload") == envelope && installed.hash == expected.verification.computedChecksum && row.bulkLong("observed_bytes") == expected.verification.observedBytes)
                require(row.bulkLong("oracle_count") == expected.counts.oracleCards && row.bulkLong("printing_count") == expected.counts.printings && row.bulkLong("face_count") == expected.counts.faces)
                require(row.bulkTimestamp("activated_at") == expected.activatedAt && installed.observed == expected.provenance.fetchedAt)
                ActivationAcknowledgement.INSTALLED
            }
        } catch (_: Exception) { ActivationAcknowledgement.INCONCLUSIVE }
    }

    private suspend fun resolveMemberships(source: CatalogBulkImportSource, generation: GenerationId, observed: UtcTimestamp, cancel: CancelToken): Long {
        var provider = ""; var id = ""; var count = 0L
        while (true) {
            // Payload pages are byte bounded too: one <=8MiB record per read, no 100-record graphs.
            val rows = database.query(bulkSql("SELECT provider,printing_id,metadata_payload FROM printing WHERE generation_id=:g AND (provider,printing_id)>(:p,:id) ORDER BY provider,printing_id LIMIT 1", 1,
                "g" to generation.value, "p" to provider, "id" to id), cancel).bulkValue()
            if (rows.isEmpty()) break
            val row = rows.single(); val printing = source.map(row.bulkText("metadata_payload"), generation, observed, null, limits.maxNesting).second
            require(printing.id.source.value == row.bulkText("provider") && printing.id.raw == row.bulkText("printing_id"))
            val targets = printing.oracleId.bulkKnown()?.let { listOf(-1 to it) } ?: printing.faceAssets.map { asset ->
                require(asset.identity.printingId == printing.id)
                asset.identity.index to requireNotNull(asset.relationships.single().relatedOracle.bulkKnown())
            }
            database.transaction({ tx ->
                for ((slot, oracle) in targets) {
                    if (tx.bulkRead(bulkSql("SELECT oracle_id FROM oracle_card WHERE generation_id=:g AND provider=:p AND oracle_id=:id AND language='en'", 1,
                            "g" to generation.value, "p" to oracle.source.value, "id" to oracle.raw), cancel).size != 1) tx.abort(bulkError(FailureCode.INVALID_INPUT, "Unresolved source Oracle membership"))
                    if (tx.bulkWrite("INSERT INTO printing_oracle_membership(generation_id,printing_provider,printing_id,source_slot,source_kind,oracle_provider,oracle_id,oracle_language) VALUES(:g,:p,:id,:slot,:kind,:op,:oi,'en')", cancel,
                            "g" to generation.value, "p" to printing.id.source.value, "id" to printing.id.raw, "slot" to slot, "kind" to if (slot == -1) "WHOLE" else "FACE", "op" to oracle.source.value, "oi" to oracle.raw) != 1L) tx.abort(bulkError(FailureCode.PERSISTENCE, "Membership insert failed"))
                }
            }, cancel).bulkValue()
            count += targets.size; provider = printing.id.source.value; id = printing.id.raw
        }
        return count
    }
}

private enum class ActivationAcknowledgement { INSTALLED, ROLLED_BACK, INCONCLUSIVE }

/** Fresh owner tokens, no leaked lock, and token-only wait cancellation observed at most25ms apart. */
internal suspend fun <T> withCatalogImportOwner(cancel: CancelToken, action: suspend (Any) -> T): T {
    val owner = Any()
    while (true) {
        currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
        if (CatalogImportOwnership.mutex.tryLock(owner)) break
        delay(25)
    }
    try {
        currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
        return action(owner)
    } finally { CatalogImportOwnership.mutex.unlock(owner) }
}

internal const val BULK_DATASET = "scryfall:en"
internal val BULK_NO_CANCEL = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() {} }
internal class BulkFailure(val outcome: Outcome<Nothing>) : RuntimeException()
internal fun bulkError(code: FailureCode, message: String) = DomainFailure(code, message, SnapshotList.empty())
internal fun <T> Outcome<T>.bulkValue(): T = when (this) { is Outcome.Success -> value; else -> throw BulkFailure(when (this) { is Outcome.Failure -> this; is Outcome.Unavailable -> this; is Outcome.Unknown -> this; else -> error("Unreachable") }) }
internal suspend fun <T> bulkAttempt(action: suspend () -> T): Outcome<T> = try { Outcome.Success(action()) } catch (e: CancellationException) { throw e } catch (e: BulkFailure) { e.outcome } catch (_: Exception) { Outcome.Failure(bulkError(FailureCode.CORRUPT_DATA, "Stored catalog validation failed")) }
internal fun <T> Knowledge<T>.bulkKnown(): T? = (this as? Knowledge.Known)?.value
internal fun bulkSql(statement: String, maxRows: Int = 1, vararg values: Pair<String, Any?>) = SqlRequest(statement, SnapshotList.copyOf(values.map { (name, value) -> SqlParameter(name, when (value) { null -> SqlValue.Null; is Long -> SqlValue.Integer(value); is Int -> SqlValue.Integer(value.toLong()); else -> SqlValue.Text(value.toString()) }) }), maxRows)
internal fun RowData.bulkValue(name: String): SqlValue = columns.single { it.name == name }.value
internal fun RowData.bulkText(name: String): String = (bulkValue(name) as SqlValue.Text).value
internal fun RowData.bulkOptionalText(name: String): String? = when (val v = bulkValue(name)) { SqlValue.Null -> null; is SqlValue.Text -> v.value; else -> error("Invalid column type") }
internal fun RowData.bulkLong(name: String): Long = (bulkValue(name) as SqlValue.Integer).value
internal fun RowData.bulkOptionalLong(name: String): Long? = when (val v = bulkValue(name)) { SqlValue.Null -> null; is SqlValue.Integer -> v.value; else -> error("Invalid column type") }
internal suspend fun DbTransaction.bulkWrite(statement: String, cancel: CancelToken, vararg values: Pair<String, Any?>): Long = execute(bulkSql(statement, 1, *values), cancel).bulkValue().affectedRows
internal suspend fun DbTransaction.bulkRead(request: SqlRequest, cancel: CancelToken): SnapshotList<RowData> = query(request, cancel).bulkValue()
internal fun CatalogBulkImportSource.legacySource() = CatalogImportSource(contentUri, displayName, provider, language, sourceDate, sourceUri, expectedChecksum, expectedBytes, notice)
internal fun CatalogBulkImportSource.provenance(g: GenerationId, observed: UtcTimestamp, hash: ContentHash?) = Provenance(provider, sourceUri.bulkKnown(), verifiedArtifact?.provenance?.sourceVersion ?: Knowledge.Unknown("Source version not supplied", SnapshotList.empty()), sourceDate, observed, Knowledge.Unknown("Effective date not supplied", SnapshotList.empty()), g, hash, notice.bulkKnown())
internal fun CatalogBulkImportSource.map(raw: String, g: GenerationId, observed: UtcTimestamp, hash: ContentHash?, nesting: Int): Pair<CardRecord?, PrintingRecord> {
    val (card, printing) = ScryfallCodec.mappedSource(raw, provider, g, sourceUri.bulkKnown(), sourceDate, observed, hash, notice.bulkKnown(), nesting)
    val provenance = provenance(g, observed, hash)
    return card?.copy(provenance = provenance) to printing.copy(provenance = provenance, suppliedLinks = SnapshotList.copyOf(printing.suppliedLinks.map { it.copy(provenance = provenance) }))
}
internal object BulkSourceEnvelope {
    const val MAX_BYTES = 2 * AcquisitionPayloadCodec.MAX_BYTES + 4096
    data class Stored(val source: CatalogBulkImportSource, val proof: FullCatalogDecodedInput.Proof, val memberships: Long, val consentId: EntityId?)
    fun encode(source: CatalogBulkImportSource, proof: FullCatalogDecodedInput.Proof, memberships: Long, consentId: EntityId?): String = JsonObject().apply {
        addProperty("version", 1); addProperty("consentId", consentId?.value); addProperty("source", AcquisitionPayloadCodec.encodeCatalogSource(source))
        addProperty("rawBytes", proof.rawBytes); addProperty("rawHash", proof.rawHash.hex)
        addProperty("decodedBytes", proof.decodedBytes); addProperty("decodedHash", proof.decodedHash.hex); addProperty("memberships", memberships)
    }.toString().also { decode(it) }
    fun decode(payload: String): Stored = try { read(payload) } catch (_: Exception) { throw IllegalArgumentException("Invalid catalog source proof") }
    private fun read(payload: String): Stored {
        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val v = ScryfallCodec.parse(payload, 8)
        require(v.keySet() == setOf("version","source","rawBytes","rawHash","decodedBytes","decodedHash","memberships","consentId"))
        fun number(name: String): Long { val n = v[name]; require(n.isJsonPrimitive && n.asJsonPrimitive.isNumber); return n.asBigDecimal.longValueExact() }
        fun text(name: String): String { val n = v[name]; require(n.isJsonPrimitive && n.asJsonPrimitive.isString); return n.asString }
        require(number("version") == 1L)
        val source = AcquisitionPayloadCodec.decodeCatalogSource(text("source"))
        val proof = FullCatalogDecodedInput.Proof(number("rawBytes"), ContentHash("SHA-256", text("rawHash")), number("decodedBytes"), ContentHash("SHA-256", text("decodedHash")))
        require(proof.rawBytes in 1..2147483648L && proof.decodedBytes in 1..2147483648L && proof.rawHash.hex.length == 64 && proof.decodedHash.hex.length == 64)
        require(number("memberships") in 1..1000000L)
        require(source.expectedBytes.bulkKnown()?.let { it == proof.rawBytes } != false && source.expectedChecksum.bulkKnown()?.let { it.hex.equals(proof.rawHash.hex, true) } != false)
        val consentId = if (v["consentId"].isJsonNull) null else EntityId(text("consentId"))
        require((source.inputKind == SourceInputKind.VERIFIED_ARTIFACT) == (consentId != null))
        consentId?.let { require(it.value.toByteArray(Charsets.UTF_8).size <= 128 && '\u0000' !in it.value) }
        return Stored(source, proof, number("memberships"), consentId)
    }
}
