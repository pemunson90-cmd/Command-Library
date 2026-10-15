package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Read-only prepared projection consumer. The injected compiler is trusted application code;
 * this class never accepts serialized plans and never initializes, prepares or acquires data. */
internal class LocalPreparedCardQuery(
    private val database: Database,
    private val compiler: CatalogQueryCompiler,
) : LocalCardQuery {
    override suspend fun local(query: QueryAst, context: SearchContext, page: PageRequest, cancel: CancelToken): Outcome<Page<CardRecord>> {
        checkCancellation(cancel)
        val reference = context.snapshots.singleOrNull()
            ?: return unsupported("Local query requires one explicit catalog snapshot")
        val generation = reference.generation
        if (reference.source.value != "scryfall" || generation == null || reference.revision != null ||
            reference.language != LanguageTag.ENGLISH || context.languages.toList() != listOf(LanguageTag.ENGLISH))
            return unsupported("Local query requires an exact English Scryfall generation")
        try { queryIdentity(reference.source.value); queryIdentity(generation.value) }
        catch (_: Exception) { return failed(FailureCode.INVALID_INPUT, "Invalid catalog snapshot identity") }
        if (page.limit !in 1..PageRequest.MAX_LIMIT) return failed(FailureCode.INVALID_INPUT, "Invalid page limit")
        val cursor = try { page.cursor?.let(CatalogQueryCursor::decode) }
        catch (_: Exception) { return failed(FailureCode.INVALID_INPUT, "Malformed catalog query cursor") }
        val plan = when (val result = compiler.compile(query, context.facePolicy, cancel)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return result
            is Outcome.Unavailable -> return result
            is Outcome.Unknown -> return result
        }
        checkCancellation(cancel)
        try { validatePlan(plan, context.facePolicy) }
        catch (_: Exception) { return failed(FailureCode.INVALID_INPUT, "Invalid compiled catalog query") }
        if (cursor != null) {
            if (cursor.provider != reference.source.value || cursor.generation != generation.value || cursor.language != "en")
                return failed(FailureCode.STALE_GENERATION, "Cursor catalog snapshot differs")
            if (cursor.facePolicy != context.facePolicy || cursor.fingerprint != plan.fingerprint.hex ||
                cursor.compilerVersion != plan.compilerVersion || cursor.projectionVersion != plan.projectionVersion)
                return failed(FailureCode.INVALID_INPUT, "Cursor belongs to another query or policy")
        }
        val capabilities = when (val result = database.capabilities(cancel)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return result
            is Outcome.Unavailable -> return result
            is Outcome.Unknown -> return result
        }
        if (capabilities.schemaVersion < 9 || !capabilities.transactions)
            return unsupported("Prepared catalog queries require schema9 and transactions")
        val request = pageRequest(generation, plan, page.limit, cursor)
        try { validateBindings(request, capabilities.maxBoundParameters) }
        catch (_: Exception) { return failed(FailureCode.INVALID_INPUT, "Compiled query exceeds database parameter bounds") }
        // One transaction owns source proof, READY observation, keyset and every original decode.
        // The Database interface has no read-only transaction mode; this callback only queries.
        return try {
            val outer = database.transaction({ tx -> bulkAttempt {
                checkCancellation(cancel)
                val installed = tx.installed(generation, capabilities.maxBoundParameters, cancel)
                val ready = tx.readChecked(bulkSql("SELECT CASE WHEN status='READY' THEN 'READY' ELSE NULL END AS status,projection_version FROM catalog_search_build WHERE generation_id=:g AND projection_version=1", 1,
                    "g" to generation.value), capabilities.maxBoundParameters, cancel).singleOrNull()
                if (ready == null || ready.bulkOptionalText("status") != "READY" || ready.bulkLong("projection_version") != 1L)
                    throw BulkFailure(Outcome.Unavailable(UnavailableReason.SOURCE_UNAVAILABLE, "Requested catalog generation is not prepared"))
                val keys = tx.readChecked(request, capabilities.maxBoundParameters, cancel)
                require(keys.size <= page.limit + 1)
                val shown = keys.take(page.limit)
                var bytes = 0L
                for (key in shown) {
                    checkCancellation(cancel)
                    if (key.bulkLong("oversize") != 0L) throw pageBudget()
                    queryIdentity(key.bulkText("provider")); queryIdentity(key.bulkText("oracle_id"))
                    require(key.bulkText("provider") == reference.source.value)
                    val length = key.bulkLong("payload_bytes")
                    if (length !in 1..MAX_RECORD_BYTES) throw pageBudget()
                    bytes = Math.addExact(bytes, length)
                    if (bytes > MAX_PAGE_BYTES) throw pageBudget()
                }
                // Calculate continuation before payload reads. Overflow never silently truncates a page.
                val next = if (keys.size > page.limit) shown.last().let { key ->
                    try { CatalogQueryCursor(reference.source.value, generation.value, "en", plan.projectionVersion,
                        plan.compilerVersion, context.facePolicy, plan.fingerprint.hex, key.bulkText("normalized_name"),
                        key.bulkText("provider"), key.bulkText("oracle_id")).encode() }
                    catch (_: Exception) { throw BulkFailure(Outcome.Unavailable(UnavailableReason.SOURCE_UNAVAILABLE, "Catalog query cursor exceeds encoding bounds")) }
                } else null
                val cards = ArrayList<CardRecord>(shown.size)
                for (key in shown) {
                    checkCancellation(cancel)
                    cards.add(tx.original(installed, key, capabilities.maxBoundParameters, cancel))
                }
                checkCancellation(cancel)
                Page(SnapshotList.copyOf(cards), next, reference)
            } }, cancel)
            when (outer) {
                is Outcome.Success -> outer.value
                is Outcome.Failure -> outer
                is Outcome.Unavailable -> outer
                is Outcome.Unknown -> outer
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { failed(FailureCode.PERSISTENCE, "Catalog query transaction failed") }
    }

    private fun pageRequest(generation: GenerationId, plan: CompiledCatalogQuery, limit: Int, cursor: CatalogQueryCursor?): SqlRequest {
        val seek = if (cursor == null) "" else " AND (oc.normalized_name,oc.provider,oc.oracle_id)>(:rq_name,:rq_provider,:rq_oracle)"
        val parameters = arrayListOf(SqlParameter("rq_generation", SqlValue.Text(generation.value)), SqlParameter("rq_limit", SqlValue.Integer(limit.toLong() + 1)))
        if (cursor != null) parameters.addAll(listOf(SqlParameter("rq_name", SqlValue.Text(cursor.name)),
            SqlParameter("rq_provider", SqlValue.Text(cursor.lastProvider)), SqlParameter("rq_oracle", SqlValue.Text(cursor.oracle))))
        parameters.addAll(plan.parameters)
        // No original payload is selected here, including the single lookahead row.
        return SqlRequest("""SELECT CASE WHEN length(CAST(oc.normalized_name AS BLOB))<=2048 THEN oc.normalized_name ELSE NULL END AS normalized_name,
            CASE WHEN length(CAST(oc.provider AS BLOB))<=128 THEN oc.provider ELSE NULL END AS provider,
            CASE WHEN length(CAST(oc.oracle_id AS BLOB))<=128 THEN oc.oracle_id ELSE NULL END AS oracle_id,
            length(CAST(oc.record_payload AS BLOB)) AS payload_bytes,
            CASE WHEN length(CAST(oc.normalized_name AS BLOB))>2048 OR length(CAST(oc.provider AS BLOB))>128 OR length(CAST(oc.oracle_id AS BLOB))>128 THEN 1 ELSE 0 END AS oversize
            FROM oracle_card oc JOIN catalog_search_card_fact c ON c.generation_id=oc.generation_id AND c.provider=oc.provider AND c.oracle_id=oc.oracle_id AND c.language=oc.language AND c.projection_version=1
            WHERE oc.generation_id=:rq_generation AND oc.provider='scryfall' AND oc.language='en' AND (${plan.predicateSql}) IS TRUE$seek
            ORDER BY oc.normalized_name,oc.provider,oc.oracle_id LIMIT :rq_limit""".trimIndent(), SnapshotList.copyOf(parameters), limit + 1)
    }

    private suspend fun DbTransaction.installed(generation: GenerationId, maxParameters: Int, cancel: CancelToken): BulkInstalledSource {
        val texts = listOf("generation_id" to 128, "provider" to 128, "format" to 128, "content_uri" to AcquisitionPayloadCodec.MAX_BYTES,
            "display_name" to 2048, "source_uri" to AcquisitionPayloadCodec.MAX_BYTES, "source_version" to AcquisitionPayloadCodec.MAX_BYTES,
            "content_sha256" to 64, "language_scope" to 16, "parser_version" to 128, "notice_payload" to 128 * 1024,
            "status" to 16, "input_kind" to 64, "artifact_job_id" to 128, "source_payload" to BulkSourceEnvelope.MAX_BYTES)
        val integers = listOf("source_date_epoch_seconds", "source_date_nano", "observed_at_epoch_seconds", "observed_at_nano", "observed_bytes",
            "payload_schema_version", "oracle_count", "face_count", "printing_count", "activated_at_epoch_seconds", "activated_at_nano")
        val fields = texts.map { (name, limit) -> "CASE WHEN length(CAST($name AS BLOB))<=$limit THEN $name ELSE NULL END AS $name" } + integers
        val oversize = texts.joinToString(" OR ") { (name, limit) -> "length(CAST($name AS BLOB))>$limit" }
        val row = readChecked(bulkSql("SELECT ${fields.joinToString(",")},CASE WHEN $oversize THEN 1 ELSE 0 END AS oversize FROM catalog_generation WHERE generation_id=:g", 1,
            "g" to generation.value), maxParameters, cancel).singleOrNull()
            ?: throw BulkFailure(failed(FailureCode.STALE_GENERATION, "Requested catalog generation is no longer installed"))
        require(row.bulkLong("oversize") == 0L && row.bulkText("generation_id") == generation.value)
        if (row.bulkText("provider") != "scryfall" || row.bulkText("language_scope") != "en" ||
            row.bulkLong("payload_schema_version") != ScryfallCodec.VERSION.toLong() ||
            row.bulkText("parser_version") != ScryfallCodec.PARSER || row.bulkText("format") != ScryfallCodec.FORMAT)
            throw BulkFailure(failed(FailureCode.STALE_GENERATION, "Requested catalog generation is incompatible"))
        if (row.bulkText("status") !in listOf("ACTIVE", "RETIRED"))
            throw BulkFailure(failed(FailureCode.STALE_GENERATION, "Requested catalog generation is no longer readable"))
        val view = RowData(SnapshotList.copyOf(row.columns.map { if (it.name == "status") RowColumn("status", SqlValue.Text("ACTIVE")) else it }))
        return view.bulkInstalledSource().also { require(it.generation == generation) }
    }

    private suspend fun DbTransaction.original(source: BulkInstalledSource, key: RowData, maxParameters: Int, cancel: CancelToken): CardRecord {
        val provider = key.bulkText("provider"); val oracle = key.bulkText("oracle_id")
        val identity = arrayOf("g" to source.generation.value, "p" to provider, "id" to oracle)
        val row = readChecked(bulkSql("SELECT CASE WHEN length(CAST(record_payload AS BLOB)) BETWEEN 1 AND 8388608 THEN record_payload ELSE NULL END AS record_payload,length(CAST(record_payload AS BLOB)) AS payload_bytes FROM oracle_card WHERE generation_id=:g AND provider=:p AND oracle_id=:id AND language='en'", 1, *identity), maxParameters, cancel).single()
        require(row.bulkLong("payload_bytes") == key.bulkLong("payload_bytes"))
        val raw = row.bulkText("record_payload")
        require(queryUtf8(raw).size.toLong() == row.bulkLong("payload_bytes"))
        checkCancellation(cancel)
        val card = requireNotNull(source.source.map(raw, source.generation, source.observed, source.hash, 32).first)
        checkCancellation(cancel)
        require(card.oracleId == OracleId(ProviderId(provider), oracle) && card.language == LanguageTag.ENGLISH)
        require(card.provenance == source.source.provenance(source.generation, source.observed, source.hash))
        require(ScryfallCodec.normalize(card.name) == key.bulkText("normalized_name"))
        val values = listOf("name" to card.name, "original_text" to card.originalText.bulkKnown(), "normalized_name" to ScryfallCodec.normalize(card.name),
            "normalized_search_text" to card.normalizedSearchText, "canonical_digest" to canonicalDigest(card))
        val matches = values.joinToString(" AND ") { "${it.first} IS :expected_${it.first}" }
        val proof = readChecked(bulkSql("SELECT CASE WHEN $matches THEN 1 ELSE 0 END AS matches,(SELECT count(*) FROM card_face WHERE generation_id=:g AND provider=:p AND oracle_id=:id AND language='en') AS faces FROM oracle_card WHERE generation_id=:g AND provider=:p AND oracle_id=:id AND language='en'", 1,
            *identity, *values.map { "expected_${it.first}" to it.second }.toTypedArray()), maxParameters, cancel).single()
        require(proof.bulkLong("matches") == 1L && proof.bulkLong("faces") == card.faces.size.toLong())
        for (face in card.faces) {
            checkCancellation(cancel)
            require(face.identity.oracleId == card.oracleId)
            val proofFace = readChecked(bulkSql("SELECT CASE WHEN face_payload IS :raw AND name IS :name AND provider_face_id IS :face THEN 1 ELSE 0 END AS matches FROM card_face WHERE generation_id=:g AND provider=:p AND oracle_id=:id AND language='en' AND face_index=:i", 1,
                *identity, "i" to face.identity.index, "raw" to raw, "name" to face.name, "face" to face.identity.providerFaceId.bulkKnown()), maxParameters, cancel).single()
            require(proofFace.bulkLong("matches") == 1L)
        }
        return card
    }

    private suspend fun DbTransaction.readChecked(request: SqlRequest, maxParameters: Int, cancel: CancelToken): SnapshotList<RowData> {
        checkCancellation(cancel)
        validateBindings(request, maxParameters)
        return bulkRead(request, cancel).also { checkCancellation(cancel) }
    }

    private fun validatePlan(plan: CompiledCatalogQuery, policy: FaceMatchPolicy) {
        require(plan.facePolicy == policy && plan.compilerVersion == 1 && plan.projectionVersion == 1)
        require(plan.fingerprint.algorithm == "SHA-256" && plan.fingerprint.hex.length == 64 && plan.fingerprint.hex.matches(Regex("[0-9a-f]{64}")))
        require(plan.predicateSql.length in 1..262144)
        require(queryUtf8(plan.predicateSql).size <= 262144 && plan.predicateSql.isNotBlank())
        require(plan.parameters.size <= 512)
        for (parameter in plan.parameters) {
            require(parameter.name.length in 3..5)
            require(parameter.name.matches(Regex("qp(?:0|[1-9][0-9]{0,2})")) && parameter.name.drop(2).toInt() <= 511)
            when (val value = parameter.value) {
                is SqlValue.Text -> {
                    require(value.value.length <= 32768)
                    require(queryUtf8(value.value).size <= 32768 && '\u0000' !in value.value)
                }
                is SqlValue.Integer, SqlValue.Null -> Unit
                else -> error("Unsupported compiled parameter type")
            }
        }
        validateBindings(SqlRequest(plan.predicateSql, plan.parameters, 1), 512)
    }

    private companion object {
        const val MAX_RECORD_BYTES = 8L * 1024 * 1024
        const val MAX_PAGE_BYTES = 16L * 1024 * 1024
        fun failed(code: FailureCode, message: String) = Outcome.Failure(bulkError(code, message))
        fun unsupported(message: String) = Outcome.Unavailable(UnavailableReason.UNSUPPORTED, message)
        fun pageBudget() = BulkFailure(Outcome.Unavailable(UnavailableReason.SOURCE_UNAVAILABLE, "Complete catalog page exceeds bounded payload or identity limits"))
    }
}

private suspend fun checkCancellation(cancel: CancelToken) { currentCoroutineContext().ensureActive(); cancel.throwIfCancelled() }

/** A structural check for trusted compiler output, not an arbitrary-SQL authorization parser.
 * Quoted literals are skipped so a literal ':qp1' cannot claim a binding. */
private fun validateBindings(request: SqlRequest, maxParameters: Int) {
    val names = request.parameters.map { it.name }
    require(names.size == names.distinct().size && names.size <= maxParameters)
    val used = linkedSetOf<String>()
    val sql = request.statement
    var index = 0; var depth = 0
    while (index < sql.length) {
        val char = sql[index]
        when {
            char == '\'' || char == '"' || char == '`' -> {
                val quote = char; index++
                var closed = false
                while (index < sql.length) {
                    if (sql[index++] == quote) {
                        if (index < sql.length && sql[index] == quote) index++ else { closed = true; break }
                    }
                }
                require(closed)
            }
            char == ':' -> {
                val start = ++index
                while (index < sql.length && (sql[index] in 'a'..'z' || sql[index] in 'A'..'Z' || sql[index] in '0'..'9' || sql[index] == '_')) index++
                require(index > start); used.add(sql.substring(start, index))
            }
            char == '(' -> { depth++; index++ }
            char == ')' -> { require(--depth >= 0); index++ }
            else -> {
                require(char != ';' && char != '\u0000' && char != '?' && char != '$' && char != '@')
                require(!(char == '-' && sql.getOrNull(index + 1) == '-') && !(char == '/' && sql.getOrNull(index + 1) == '*'))
                index++
            }
        }
    }
    require(depth == 0 && used == names.toSet())
}
