package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Source-qualified installed identities. Reads need neither artifact bytes nor acquisition jobs. */
internal class LocalCatalogIdentities(private val database: Database) : CatalogPrintingLookup, CatalogOracleLookup {
    override suspend fun printing(id: CardId, cancel: CancelToken): Outcome<PrintingRecord> = bulkAttempt {
        val active = active(cancel) ?: throw BulkFailure(Outcome.Unavailable(UnavailableReason.NOT_INSTALLED, "No catalog installed"))
        val row = database.query(bulkSql("SELECT * FROM printing WHERE generation_id=:g AND provider=:p AND printing_id=:id", 1,
            "g" to active.bulkText("generation_id"), "p" to id.source.value, "id" to id.raw), cancel).bulkValue().firstOrNull()
            ?: throw BulkFailure(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Printing not installed"))
        readPrinting(active, row, cancel).also { require(it.id == id) }
    }
    override suspend fun oracle(id: OracleId, cancel: CancelToken): Outcome<CardRecord> = bulkAttempt {
        val active = active(cancel) ?: throw BulkFailure(Outcome.Unavailable(UnavailableReason.NOT_INSTALLED, "No catalog installed"))
        val row = database.query(bulkSql("SELECT * FROM oracle_card WHERE generation_id=:g AND provider=:p AND oracle_id=:id AND language='en'", 1,
            "g" to active.bulkText("generation_id"), "p" to id.source.value, "id" to id.raw), cancel).bulkValue().firstOrNull()
            ?: throw BulkFailure(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Oracle not installed"))
        val state = active.bulkInstalledSource()
        require(row.bulkText("record_payload").toByteArray(Charsets.UTF_8).size <= 8 * 1024 * 1024)
        val card = requireNotNull(state.source.map(row.bulkText("record_payload"), state.generation, state.observed, state.hash, 32).first)
        require(card.oracleId == id && row.bulkText("provider") == id.source.value && row.bulkText("oracle_id") == id.raw && row.bulkText("language") == card.language.value)
        require(row.bulkText("name") == card.name && row.bulkOptionalText("original_text") == card.originalText.bulkKnown() && row.bulkText("normalized_name") == ScryfallCodec.normalize(card.name) && row.bulkText("normalized_search_text") == card.normalizedSearchText && row.bulkText("canonical_digest") == canonicalDigest(card))
        val count = database.query(bulkSql("SELECT count(*) AS n FROM card_face WHERE generation_id=:g AND provider=:p AND oracle_id=:id AND language='en'", 1,
            "g" to state.generation.value, "p" to id.source.value, "id" to id.raw), cancel).bulkValue().single().bulkLong("n")
        require(count == card.faces.size.toLong())
        for (face in card.faces) {
            cancel.throwIfCancelled()
            val f = database.query(bulkSql("SELECT * FROM card_face WHERE generation_id=:g AND provider=:p AND oracle_id=:id AND language='en' AND face_index=:i", 1,
                "g" to state.generation.value, "p" to id.source.value, "id" to id.raw, "i" to face.identity.index), cancel).bulkValue().single()
            require(f.bulkText("face_payload") == row.bulkText("record_payload") && f.bulkText("name") == face.name && f.bulkOptionalText("provider_face_id") == face.identity.providerFaceId.bulkKnown())
        }
        card
    }
    override suspend fun printings(id: OracleId, page: PageRequest, cancel: CancelToken): Outcome<Page<PrintingRecord>> = bulkAttempt {
        val active = active(cancel)
        val cursor = try { page.cursor?.let(::decodeIdentityCursor) } catch (_: Exception) { throw BulkFailure(Outcome.Failure(bulkError(FailureCode.INVALID_INPUT, "Malformed printing cursor"))) }
        val generation = active?.bulkText("generation_id")
        if (cursor != null && cursor.generation != generation) throw BulkFailure(Outcome.Failure(bulkError(FailureCode.STALE_GENERATION, "Catalog generation changed")))
        val query = identityQuery(id)
        if (cursor != null && cursor.query != query) throw BulkFailure(Outcome.Failure(bulkError(FailureCode.INVALID_INPUT, "Cursor belongs to another Oracle")))
        val reference = SnapshotReference(ProviderId("scryfall"), generation?.let(::GenerationId), null, LanguageTag.ENGLISH)
        if (active == null) return@bulkAttempt Page(SnapshotList.empty(), null, reference)
        // Identity page contains no raw payloads; load at most one bounded printing at a time.
        val rows = database.query(bulkSql("""SELECT DISTINCT printing_provider,printing_id FROM printing_oracle_membership WHERE generation_id=:g AND oracle_provider=:op AND oracle_id=:oi AND oracle_language='en' AND (printing_provider,printing_id)>(:p,:id) ORDER BY printing_provider,printing_id LIMIT :limit""", page.limit + 1,
            "g" to generation, "op" to id.source.value, "oi" to id.raw, "p" to (cursor?.provider ?: ""), "id" to (cursor?.printing ?: ""), "limit" to page.limit + 1), cancel).bulkValue()
        val shown = rows.take(page.limit)
        val records = ArrayList<PrintingRecord>()
        var retainedBytes = 0
        var lastIdentity: RowData? = null
        for (identity in shown) {
            val row = database.query(bulkSql("SELECT * FROM printing WHERE generation_id=:g AND provider=:p AND printing_id=:id", 1,
                "g" to generation, "p" to identity.bulkText("printing_provider"), "id" to identity.bulkText("printing_id")), cancel).bulkValue().single()
            val size = row.bulkText("metadata_payload").toByteArray(Charsets.UTF_8).size
            if (records.isNotEmpty() && size > BoundedRecordBatches.MAX_BATCH_BYTES - retainedBytes) break
            records.add(readPrinting(active, row, cancel).also { printing ->
                require(printing.oracleId.bulkKnown() == id || printing.faceAssets.any { asset -> asset.relationships.any { it.relatedOracle.bulkKnown() == id } })
            })
            retainedBytes += size
            lastIdentity = identity
        }
        val next = if (rows.size > records.size) requireNotNull(lastIdentity).let { encodeIdentityCursor(IdentityCursor(requireNotNull(generation), query, it.bulkText("printing_provider"), it.bulkText("printing_id"))) } else null
        Page(SnapshotList.copyOf(records), next, reference)
    }
    private suspend fun active(cancel: CancelToken): RowData? = database.query(bulkSql("SELECT g.* FROM catalog_active a JOIN catalog_generation g ON g.generation_id=a.generation_id WHERE a.dataset_key=:key", 1, "key" to BULK_DATASET), cancel).bulkValue().firstOrNull()
    private suspend fun readPrinting(active: RowData, row: RowData, cancel: CancelToken): PrintingRecord {
        val state = active.bulkInstalledSource()
        val raw = row.bulkText("metadata_payload")
        require(raw.toByteArray(Charsets.UTF_8).size <= 8 * 1024 * 1024)
        val printing = state.source.map(raw, state.generation, state.observed, state.hash, 32).second
        require(row.bulkText("generation_id") == state.generation.value && row.bulkText("provider") == printing.id.source.value && row.bulkText("printing_id") == printing.id.raw)
        val oracle = printing.oracleId.bulkKnown()
        require(row.bulkOptionalText("oracle_provider") == oracle?.source?.value && row.bulkOptionalText("oracle_id") == oracle?.raw && row.bulkOptionalText("oracle_language") == oracle?.let { "en" })
        require(row.bulkText("language") == printing.language.value && row.bulkText("set_code") == printing.setCode && row.bulkOptionalText("set_name") == printing.setName.bulkKnown() && row.bulkText("collector_number") == printing.collectorNumber && row.bulkOptionalText("rarity") == printing.rarity.bulkKnown() && row.bulkOptionalText("finishes") == printing.finishes.bulkKnown()?.joinToString(",") { it.name } && row.bulkOptionalText("image_uri") == printing.imageUri.bulkKnown()?.value)
        val expected = oracle?.let { listOf(-1 to it) } ?: printing.faceAssets.map { it.identity.index to requireNotNull(it.relationships.single().relatedOracle.bulkKnown()) }
        val counts = database.query(bulkSql("SELECT (SELECT count(*) FROM printing_face_asset WHERE generation_id=:g AND provider=:p AND printing_id=:id) AS assets,(SELECT count(*) FROM printing_oracle_membership WHERE generation_id=:g AND printing_provider=:p AND printing_id=:id) AS memberships", 1,
            "g" to state.generation.value, "p" to printing.id.source.value, "id" to printing.id.raw), cancel).bulkValue().single()
        require(counts.bulkLong("assets") == printing.faceAssets.size.toLong() && counts.bulkLong("memberships") == expected.size.toLong())
        for (asset in printing.faceAssets) {
            val f = database.query(bulkSql("SELECT * FROM printing_face_asset WHERE generation_id=:g AND provider=:p AND printing_id=:id AND face_index=:i", 1,
                "g" to state.generation.value, "p" to printing.id.source.value, "id" to printing.id.raw, "i" to asset.identity.index), cancel).bulkValue().single()
            require(f.bulkText("asset_payload") == raw && f.bulkOptionalText("provider_face_id") == asset.identity.providerFaceId.bulkKnown() && f.bulkText("language") == asset.language.value && f.bulkOptionalText("printed_name") == asset.printedName.bulkKnown())
        }
        for ((slot, target) in expected) {
            val member = database.query(bulkSql("SELECT m.*,c.oracle_id AS target_id FROM printing_oracle_membership m JOIN oracle_card c ON c.generation_id=m.generation_id AND c.provider=m.oracle_provider AND c.oracle_id=m.oracle_id AND c.language=m.oracle_language WHERE m.generation_id=:g AND m.printing_provider=:p AND m.printing_id=:id AND m.source_slot=:slot", 1,
                "g" to state.generation.value, "p" to printing.id.source.value, "id" to printing.id.raw, "slot" to slot), cancel).bulkValue().single()
            require(member.bulkText("source_kind") == if (slot == -1) "WHOLE" else "FACE")
            require(member.bulkText("oracle_provider") == target.source.value && member.bulkText("oracle_id") == target.raw && member.bulkText("oracle_language") == "en")
        }
        return printing
    }
}
internal data class BulkInstalledSource(val source: CatalogBulkImportSource, val generation: GenerationId, val observed: UtcTimestamp, val hash: ContentHash)
internal fun RowData.bulkInstalledSource(): BulkInstalledSource {
    require(bulkText("status") == "ACTIVE" && bulkText("language_scope") == "en" && bulkLong("payload_schema_version") == ScryfallCodec.VERSION.toLong() && bulkText("parser_version") == ScryfallCodec.PARSER && bulkText("format") == ScryfallCodec.FORMAT)
    require(bulkLong("observed_bytes") in 1..2147483648L)
    require(bulkText("generation_id").toByteArray(Charsets.UTF_8).size <= 128 && '\u0000' !in bulkText("generation_id"))
    require(bulkLong("printing_count") in 1..500_000L && bulkLong("oracle_count") in 0..bulkLong("printing_count") && bulkLong("face_count") in 0..2147483648L)
    bulkTimestamp("activated_at")
    bulkOptionalLong("source_date_nano")?.let { require(it in 0..999_999_999L) }
    val hash = ContentHash("SHA-256", bulkText("content_sha256")); require(hash.hex.length == 64)
    val payload = bulkOptionalText("source_payload")
    val source = if (payload != null) {
        val stored = BulkSourceEnvelope.decode(payload)
        require(stored.proof.rawHash == hash && stored.proof.rawBytes == bulkLong("observed_bytes"))
        stored.source
    } else {
        require(bulkText("input_kind") == "USER_CONTENT_URI" && bulkOptionalText("artifact_job_id") == null)
        CatalogBulkImportSource(ResourceUri(bulkText("content_uri")), bulkText("display_name"), ProviderId(bulkText("provider")), LanguageTag.ENGLISH,
            SourceMetadataCodec.sourceDate(bulkOptionalText("notice_payload")), bulkOptionalText("source_uri")?.let { Knowledge.Known(ResourceUri(it)) } ?: Knowledge.Absent("Not supplied"),
            Knowledge.Known(hash), Knowledge.Known(bulkLong("observed_bytes")), SourceMetadataCodec.notice(bulkOptionalText("notice_payload"))?.let { Knowledge.Known(it) } ?: Knowledge.Absent("Not supplied"), ArtifactFraming.JSON_ARRAY, ArtifactCompression.NONE)
    }
    require(source.contentUri.value == bulkText("content_uri") && source.displayName == bulkText("display_name") && source.provider.value == bulkText("provider") && source.sourceUri.bulkKnown()?.value == bulkOptionalText("source_uri") && source.inputKind.name == bulkText("input_kind") && source.verifiedArtifact?.jobId?.value == bulkOptionalText("artifact_job_id"))
    require(source.sourceDate.bulkKnown()?.epochSeconds == bulkOptionalLong("source_date_epoch_seconds") && source.sourceDate.bulkKnown()?.nanoAdjustment?.toLong() == bulkOptionalLong("source_date_nano"))
    require(source.verifiedArtifact?.provenance?.sourceVersion?.bulkKnown() == bulkOptionalText("source_version"))
    require(source.sourceDate == SourceMetadataCodec.sourceDate(bulkOptionalText("notice_payload")) && source.notice.bulkKnown() == SourceMetadataCodec.notice(bulkOptionalText("notice_payload")))
    return BulkInstalledSource(source, GenerationId(bulkText("generation_id")), bulkTimestamp("observed_at"), hash)
}
private data class IdentityCursor(val generation: String, val query: String, val provider: String, val printing: String)
private fun identityQuery(id: OracleId): String = MessageDigest.getInstance("SHA-256").digest("${id.source.value.length}:${id.source.value}${id.raw.length}:${id.raw}".toByteArray(Charsets.UTF_8)).hex()
private fun encodeIdentityCursor(c: IdentityCursor): String = listOf("1", c.generation, c.query, c.provider, c.printing).joinToString("\u0000").toByteArray(Charsets.UTF_8).hex().also { require(it.length <= PageRequest.MAX_CURSOR_BYTES) }
private fun decodeIdentityCursor(value: String): IdentityCursor {
    require(value.length in 2..PageRequest.MAX_CURSOR_BYTES && value.length % 2 == 0 && value.matches(Regex("[0-9a-f]+")))
    val bytes = ByteArray(value.length / 2) { value.substring(it*2,it*2+2).toInt(16).toByte() }
    val parts = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().split('\u0000')
    require(parts.size == 5 && parts[0] == "1" && parts.drop(1).all { it.isNotBlank() } && parts[2].matches(Regex("[0-9a-f]{64}")))
    require(parts.filterIndexed { i,_ -> i in listOf(1,3,4) }.all { it.toByteArray(Charsets.UTF_8).size <= 128 })
    return IdentityCursor(parts[1],parts[2],parts[3],parts[4])
}

/** Validate SQL's full64-bit value before narrowing: wrapped corrupt nanos must never look valid. */
internal fun RowData.bulkTimestamp(prefix: String): UtcTimestamp {
    val nano = bulkLong("${prefix}_nano")
    require(nano in 0..999_999_999L)
    return UtcTimestamp(bulkLong("${prefix}_epoch_seconds"), nano.toInt())
}
