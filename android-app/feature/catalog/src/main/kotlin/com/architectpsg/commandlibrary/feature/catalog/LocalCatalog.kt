package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.common.sameCanonicalGameData
import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.io.IOException
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Owns only the local English Scryfall import, status and lookup capabilities. */
public class LocalCatalog(
    private val database: Database,
    private val clock: Clock,
    private val ids: IdSource,
    private val limits: CatalogImportLimits = CatalogImportLimits(),
) : CatalogImport, CatalogLookup, CatalogStatus {
    private companion object { val importMutex = Mutex() }
    private var initialized = false
    private val dataset = "scryfall:en"
    private val cleanupCancel = object : CancelToken {
        override val isCancelled: Boolean = false
        override fun throwIfCancelled(): Unit = Unit
    }

    override suspend fun status(cancel: CancelToken): Outcome<LocalCatalogStatus> {
        when (val ready = initialize(cancel)) { is Outcome.Success -> Unit; else -> return ready.nonSuccess() }
        val row = when (val result = database.query(sql("SELECT g.* FROM catalog_active a JOIN catalog_generation g ON g.generation_id=a.generation_id WHERE a.dataset_key=:key", 1, "key" to dataset), cancel)) {
            is Outcome.Success -> result.value.firstOrNull()
            else -> return result.nonSuccess()
        }
        if (row == null) return Outcome.Success(LocalCatalogStatus(StorageMode.FULL, LanguageTag.ENGLISH, SnapshotList.empty(),
            Knowledge.Absent("No catalog installed"), Knowledge.Unknown("No source date", SnapshotList.empty()),
            Knowledge.Unknown("No import observed", SnapshotList.empty()), SnapshotList.empty(), false))
        return try {
            val generation = row.generation()
            Outcome.Success(LocalCatalogStatus(StorageMode.FULL, LanguageTag.ENGLISH, SnapshotList.of(generation),
                Knowledge.Known(generation.provenance.provider), generation.provenance.sourceDate,
                Knowledge.Known(generation.provenance.fetchedAt), SnapshotList.of(LanguageTag.ENGLISH), true))
        } catch (failure: Exception) { corrupt(failure) }
    }

    override suspend fun lookupName(input: String, page: PageRequest, cancel: CancelToken): Outcome<Page<CardRecord>> {
        when (val ready = initialize(cancel)) { is Outcome.Success -> Unit; else -> return ready.nonSuccess() }
        val trimmed = input.trim()
        if (trimmed.codePointCount(0, trimmed.length) > 128) return invalid("Name query exceeds 128 code points")
        val active = when (val result = activeRow(cancel)) {
            is Outcome.Success -> result.value
            else -> return result.nonSuccess()
        }
        val cursor = try { page.cursor?.let(::decodeCursor) } catch (_: Exception) { return invalid("Malformed catalog cursor") }
        if (cursor != null && cursor.generation != active?.text("generation_id")) return failure(FailureCode.STALE_GENERATION, "Catalog generation changed")
        val reference = SnapshotReference(active?.let { ProviderId(it.text("provider")) } ?: ProviderId("scryfall"),
            active?.let { GenerationId(it.text("generation_id")) }, null, LanguageTag.ENGLISH)
        val words = Regex("[\\p{L}\\p{M}\\p{N}]+").findAll(ScryfallCodec.normalize(trimmed)).map { it.value }.toList()
        if (active == null || words.isEmpty()) return Outcome.Success(Page(SnapshotList.empty(), null, reference))
        val expression = "name : \"${words.joinToString(" ") { it.replace("\"", "\"\"") }}\"*"
        val queryKey = digest(expression).take(24)
        if (cursor != null && cursor.query != queryKey) return invalid("Cursor belongs to a different query")
        val rows = when (val result = database.query(sql("""SELECT c.record_payload AS payload,c.normalized_name AS sort_name,c.provider AS provider,c.oracle_id AS oracle_id FROM catalog_name_fts JOIN oracle_card c ON c.generation_id=catalog_name_fts.generation_id AND c.provider=catalog_name_fts.provider AND c.oracle_id=catalog_name_fts.oracle_id AND c.language=catalog_name_fts.language WHERE catalog_name_fts MATCH :match AND c.generation_id=:generation AND c.language='en' AND (c.normalized_name>:name OR (c.normalized_name=:name AND (c.provider>:provider OR (c.provider=:provider AND c.oracle_id>:oracle)))) ORDER BY c.normalized_name,c.provider,c.oracle_id LIMIT :limit""", page.limit + 1,
            "match" to expression, "generation" to active.text("generation_id"), "name" to (cursor?.name ?: ""), "provider" to (cursor?.provider ?: ""), "oracle" to (cursor?.oracle ?: ""), "limit" to (page.limit + 1).toLong()), cancel)) {
            is Outcome.Success -> result.value
            else -> return result.nonSuccess()
        }
        return try {
            val shown = rows.take(page.limit)
            val provenance = active.provenance()
            val cards = shown.map { row -> ScryfallCodec.mapped(row.text("payload"), provenance.provider, GenerationId(active.text("generation_id")),
                provenance.source, provenance.sourceDate, provenance.fetchedAt, provenance.checksum, provenance.notice, limits.maxNesting).first }
            val next = if (rows.size > page.limit) shown.last().let { encodeCursor(Cursor(active.text("generation_id"), queryKey, it.text("sort_name"), it.text("provider"), it.text("oracle_id"))) } else null
            Outcome.Success(Page(SnapshotList.copyOf(cards), next, reference))
        } catch (failure: Exception) { corrupt(failure) }
    }

    override suspend fun `import`(source: CatalogImportSource, chunks: Flow<ByteChunk>, onProgress: (JobProgress) -> Unit, cancel: CancelToken): Outcome<CatalogImportResult> = importMutex.withLock {
        when (val ready = initializeLocked(cancel)) { is Outcome.Success -> Unit; else -> return@withLock ready.nonSuccess() }
        if (source.provider.value != "scryfall" || source.language != LanguageTag.ENGLISH) return@withLock invalid("Only English Scryfall input is supported")
        if (!source.contentUri.value.startsWith("content://") || source.displayName.isBlank() || source.displayName.toByteArray(Charsets.UTF_8).size > 512)
            return@withLock invalid("Expected a named local content source")
        source.expectedBytes.knownOrNull()?.let { if (it < 0 || it > limits.maxInputBytes) return@withLock invalid("Expected byte count exceeds import limits") }
        source.expectedChecksum.knownOrNull()?.let { if (!it.algorithm.equals("SHA-256", true) || it.hex.length != 64) return@withLock invalid("Expected SHA-256 checksum") }
        val generation = GenerationId(ids.nextId())
        if (generation.value.toByteArray(Charsets.UTF_8).size > 128 || '\u0000' in generation.value) return@withLock invalid("Generation identity exceeds cursor limits")
        val job = EntityId(ids.nextId())
        val observed = clock.utcNow()
        var staging = false
        try {
            val stage = database.transaction({ tx ->
                tx.write("""INSERT INTO catalog_generation(generation_id,provider,format,content_uri,display_name,source_uri,source_version,source_date_epoch_seconds,source_date_nano,observed_at_epoch_seconds,observed_at_nano,content_sha256,observed_bytes,language_scope,payload_schema_version,parser_version,notice_payload,oracle_count,face_count,printing_count,status,activated_at_epoch_seconds,activated_at_nano) VALUES(:id,:provider,:format,:content,:display,:source,NULL,:date,:date_nano,:observed,:observed_nano,'',0,'en',:payload_version,:parser,:metadata,0,0,0,'STAGING',NULL,NULL)""", cancel,
                    "id" to generation.value, "provider" to source.provider.value, "format" to ScryfallCodec.FORMAT,
                    "content" to source.contentUri.value, "display" to source.displayName, "source" to source.sourceUri.knownOrNull()?.value,
                    "date" to source.sourceDate.knownOrNull()?.epochSeconds, "date_nano" to source.sourceDate.knownOrNull()?.nanoAdjustment?.toLong(),
                    "observed" to observed.epochSeconds, "observed_nano" to observed.nanoAdjustment.toLong(),
                    "payload_version" to ScryfallCodec.VERSION.toLong(), "parser" to ScryfallCodec.PARSER,
                    "metadata" to SourceMetadataCodec.encode(source))
            }, cancel)
            if (stage !is Outcome.Success) return@withLock stage.nonSuccess()
            staging = true
            val frame = JsonArrayFramer(limits)
            val seenPrintings = HashSet<CardId>()
            val canonical = HashMap<OracleId, CardRecord>()
            val batch = ArrayList<Triple<String, CardRecord, PrintingRecord>>()
            var faceCount = 0L
            suspend fun flush() {
                if (batch.isEmpty()) return
                val writing = database.transaction({ tx ->
                    for ((raw, card, printing) in batch) {
                        if (card.oracleId !in canonical || canonical[card.oracleId] === card) {
                            // The first representative alone owns canonical rows and FTS entries.
                            if (canonical[card.oracleId] === card) tx.insertCard(generation, card, raw, cancel)
                        }
                        tx.insertPrinting(generation, printing, raw, cancel)
                    }
                }, cancel)
                if (writing !is Outcome.Success) throw CatalogFailure(writing.nonSuccess())
                batch.clear()
                onProgress(JobProgress(job, JobPhase.STAGING, seenPrintings.size.toLong(), Knowledge.Unknown("Total count not supplied", SnapshotList.empty()), "Staging local records", null))
            }
            chunks.collect { chunk ->
                cancel.throwIfCancelled()
                frame.accept(chunk) { raw ->
                    val (card, printing) = ScryfallCodec.mapped(raw, source.provider, generation, source.sourceUri.knownOrNull(), source.sourceDate, observed, null, source.notice.knownOrNull(), limits.maxNesting)
                    require(seenPrintings.add(printing.id)) { "Duplicate printing identity" }
                    val first = canonical[card.oracleId]
                    if (first == null) canonical[card.oracleId] = card
                    require(first == null || sameCanonicalGameData(first, card)) { "Canonical game data conflict" }
                    if (first == null) faceCount += card.faces.size
                    batch.add(Triple(raw, card, printing))
                    if (batch.size == limits.batchSize) flush()
                }
            }
            frame.finish()
            flush()
            source.expectedBytes.knownOrNull()?.let { require(it == frame.observedBytes) { "Expected byte count mismatch" } }
            val hash = ContentHash("SHA-256", frame.sha256)
            source.expectedChecksum.knownOrNull()?.let { require(it.algorithm.equals("SHA-256", true) && it.hex.equals(hash.hex, true)) { "Expected checksum mismatch" } }
            val activated = clock.utcNow()
            val count = CatalogCounts(canonical.size.toLong(), seenPrintings.size.toLong(), faceCount, 0)
            val final = database.transaction({ tx ->
                val actual = tx.read(sql("SELECT (SELECT count(*) FROM oracle_card WHERE generation_id=:id) AS oracles,(SELECT count(*) FROM printing WHERE generation_id=:id) AS printings,(SELECT count(*) FROM card_face WHERE generation_id=:id) AS faces", 1, "id" to generation.value), cancel).single()
                if (actual.long("oracles") != count.oracleCards || actual.long("printings") != count.printings || actual.long("faces") != count.faces) tx.abort(DomainFailure(FailureCode.CORRUPT_DATA, "Staged counts differ", SnapshotList.empty()))
                tx.write("UPDATE catalog_generation SET content_sha256=:hash,observed_bytes=:bytes,oracle_count=:oracles,printing_count=:printings,face_count=:faces,status='ACTIVE',activated_at_epoch_seconds=:at,activated_at_nano=:nano WHERE generation_id=:id AND status='STAGING'", cancel,
                    "hash" to hash.hex, "bytes" to frame.observedBytes, "oracles" to count.oracleCards, "printings" to count.printings,
                    "faces" to count.faces, "at" to activated.epochSeconds, "nano" to activated.nanoAdjustment.toLong(), "id" to generation.value)
                tx.write("UPDATE catalog_generation SET status='RETIRED' WHERE generation_id=(SELECT generation_id FROM catalog_active WHERE dataset_key=:key)", cancel, "key" to dataset)
                tx.write("INSERT INTO catalog_active(dataset_key,generation_id) VALUES(:key,:id) ON CONFLICT(dataset_key) DO UPDATE SET generation_id=excluded.generation_id", cancel, "key" to dataset, "id" to generation.value)
            }, cancel)
            if (final !is Outcome.Success) throw CatalogFailure(final.nonSuccess())
            staging = false // Durable commit is authoritative; no subsequent cancellation boundary.
            val provenance = Provenance(source.provider, source.sourceUri.knownOrNull(), Knowledge.Unknown("Source version not supplied", SnapshotList.empty()), source.sourceDate,
                observed, Knowledge.Unknown("Effective date not supplied", SnapshotList.empty()), generation, hash, source.notice.knownOrNull())
            Outcome.Success(CatalogImportResult(CatalogGeneration(generation, provenance, SnapshotList.of(LanguageTag.ENGLISH), ScryfallCodec.FORMAT, count, GenerationStatus.ACTIVE),
                count, provenance, CatalogImportVerification(frame.observedBytes, hash, if (source.expectedChecksum is Knowledge.Known) ChecksumVerification.MATCHED_EXPECTED else ChecksumVerification.COMPUTED_WITHOUT_EXPECTATION, true, 0), activated))
        } catch (cancelled: CancellationException) {
            if (staging && cleanup(generation) !is Outcome.Success) initialized = false
            throw cancelled
        } catch (cause: Exception) {
            val cleaned = if (staging) cleanup(generation) else Outcome.Success(Unit)
            if (cleaned !is Outcome.Success) {
                initialized = false
                return@withLock failure(FailureCode.PERSISTENCE, "Import failed and staged cleanup remains pending")
            }
            when (cause) {
                is CatalogFailure -> cause.outcome
                is IllegalArgumentException, is IllegalStateException, is ArithmeticException, is IOException, is JsonParseException -> invalid(cause.message ?: "Invalid catalog input")
                else -> failure(FailureCode.INTERNAL, "Catalog import failed")
            }
        }
    }

    private suspend fun initialize(cancel: CancelToken): Outcome<Unit> = if (initialized) Outcome.Success(Unit)
        else importMutex.withLock { initializeLocked(cancel) }
    private suspend fun initializeLocked(cancel: CancelToken): Outcome<Unit> {
        if (initialized) return Outcome.Success(Unit)
        while (true) {
            val stale = when (val result = database.query(sql("SELECT generation_id FROM catalog_generation WHERE status='STAGING' LIMIT 101", 101), cancel)) {
                is Outcome.Success -> result.value
                else -> return result.nonSuccess()
            }
            if (stale.isEmpty()) break
            for (row in stale) when (val result = cleanup(GenerationId(row.text("generation_id")))) {
                is Outcome.Success -> Unit
                else -> return result.nonSuccess()
            }
        }
        initialized = true
        return Outcome.Success(Unit)
    }
    private suspend fun cleanup(id: GenerationId): Outcome<Unit> = withContext(NonCancellable) {
        try {
            withTimeout(1_000) { database.transaction({ tx ->
                tx.write("DELETE FROM catalog_name_fts WHERE generation_id=:id", cleanupCancel, "id" to id.value)
                tx.write("DELETE FROM catalog_generation WHERE generation_id=:id AND status='STAGING'", cleanupCancel, "id" to id.value)
            }, cleanupCancel) }
        } catch (_: CancellationException) { failure(FailureCode.PERSISTENCE, "Staged cleanup exceeded time limit") }
        catch (_: Exception) { failure(FailureCode.PERSISTENCE, "Staged cleanup failed") }
        }
    private suspend fun activeRow(cancel: CancelToken): Outcome<RowData?> = when (val result = database.query(sql("SELECT g.* FROM catalog_active a JOIN catalog_generation g ON a.generation_id=g.generation_id WHERE a.dataset_key=:key", 1, "key" to dataset), cancel)) {
        is Outcome.Success -> Outcome.Success(result.value.firstOrNull())
        else -> result.nonSuccess()
    }
}

private class CatalogFailure(val outcome: Outcome<Nothing>) : RuntimeException()
private fun <T> Outcome<T>.nonSuccess(): Outcome<Nothing> = when (this) {
    is Outcome.Failure -> this; is Outcome.Unavailable -> this; is Outcome.Unknown -> this
    is Outcome.Success -> error("Expected failure")
}
private fun invalid(message: String): Outcome.Failure = failure(FailureCode.INVALID_INPUT, message)
private fun failure(code: FailureCode, message: String): Outcome.Failure = Outcome.Failure(DomainFailure(code, message, SnapshotList.empty()))
private fun corrupt(cause: Exception): Outcome.Failure = failure(FailureCode.CORRUPT_DATA, "Stored catalog record cannot be decoded: ${cause.javaClass.simpleName}")
private fun <T> Knowledge<T>.knownOrNull(): T? = (this as? Knowledge.Known)?.value
private fun sql(statement: String, maxRows: Int = 100, vararg values: Pair<String, Any?>): SqlRequest =
    SqlRequest(statement, SnapshotList.copyOf(values.map { (name, value) -> SqlParameter(name, when (value) {
        null -> SqlValue.Null; is Int -> SqlValue.Integer(value.toLong()); is Long -> SqlValue.Integer(value)
        else -> SqlValue.Text(value.toString())
    }) }), maxRows)
private fun RowData.value(name: String): SqlValue = columns.single { it.name == name }.value
private fun RowData.text(name: String): String = (value(name) as SqlValue.Text).value
private fun RowData.optionalText(name: String): String? = when (val value = value(name)) { is SqlValue.Text -> value.value; SqlValue.Null -> null; else -> error("Invalid $name") }
private fun RowData.long(name: String): Long = (value(name) as SqlValue.Integer).value
private fun RowData.optionalLong(name: String): Long? = when (val value = value(name)) { is SqlValue.Integer -> value.value; SqlValue.Null -> null; else -> error("Invalid $name") }
private suspend fun DbTransaction.write(statement: String, cancel: CancelToken, vararg values: Pair<String, Any?>) {
    when (val result = execute(sql(statement, 1, *values), cancel)) {
        is Outcome.Success -> Unit
        is Outcome.Failure -> abort(result.error)
        else -> abort(DomainFailure(FailureCode.PERSISTENCE, "Catalog write failed", SnapshotList.empty()))
    }
}
private suspend fun DbTransaction.read(request: SqlRequest, cancel: CancelToken): SnapshotList<RowData> = when (val result = query(request, cancel)) {
    is Outcome.Success -> result.value
    is Outcome.Failure -> abort(result.error)
    else -> abort(DomainFailure(FailureCode.PERSISTENCE, "Catalog read failed", SnapshotList.empty()))
}
private fun RowData.provenance(): Provenance {
    val provider = ProviderId(text("provider"))
    val date = SourceMetadataCodec.sourceDate(optionalText("notice_payload"))
    return Provenance(provider, optionalText("source_uri")?.let(::ResourceUri), Knowledge.Unknown("Source version not supplied", SnapshotList.empty()), date,
        UtcTimestamp(long("observed_at_epoch_seconds"), long("observed_at_nano").toInt()), Knowledge.Unknown("Effective date not supplied", SnapshotList.empty()),
        GenerationId(text("generation_id")), ContentHash("SHA-256", text("content_sha256")), SourceMetadataCodec.notice(optionalText("notice_payload")))
}
private fun RowData.generation(): CatalogGeneration = CatalogGeneration(GenerationId(text("generation_id")), provenance(), SnapshotList.of(LanguageTag.ENGLISH),
    text("format"), CatalogCounts(long("oracle_count"), long("printing_count"), long("face_count"), 0), GenerationStatus.valueOf(text("status")))
private data class Cursor(val generation: String, val query: String, val name: String, val provider: String, val oracle: String)
private fun encodeCursor(cursor: Cursor): String = listOf(cursor.generation, cursor.query, cursor.name, cursor.provider, cursor.oracle)
    .joinToString("\u0000").toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }.also {
        require(it.length <= PageRequest.MAX_CURSOR_BYTES)
    }
private fun decodeCursor(value: String): Cursor {
    require(value.length in 2..PageRequest.MAX_CURSOR_BYTES && value.length % 2 == 0 && value.matches(Regex("[0-9a-f]+")))
    val bytes = ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    val decoded = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    val parts = decoded.split('\u0000')
    require(parts.size == 5 && parts.all { it.isNotEmpty() })
    return Cursor(parts[0], parts[1], parts[2], parts[3], parts[4])
}
private fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

private suspend fun DbTransaction.insertCard(generation: GenerationId, card: CardRecord, raw: String, cancel: CancelToken) {
    write("""INSERT INTO oracle_card(generation_id,provider,oracle_id,language,name,original_text,normalized_name,normalized_search_text,canonical_digest,record_payload) VALUES(:generation,:provider,:oracle,'en',:name,:text,:normalized,:search,:digest,:payload)""", cancel,
        "generation" to generation.value, "provider" to card.oracleId.source.value, "oracle" to card.oracleId.raw,
        "name" to card.name, "text" to card.originalText.knownOrNull(), "normalized" to ScryfallCodec.normalize(card.name),
        "search" to card.normalizedSearchText, "digest" to canonicalDigest(card), "payload" to raw)
    for (face in card.faces) write("""INSERT INTO card_face(generation_id,provider,oracle_id,language,face_index,provider_face_id,name,face_payload) VALUES(:generation,:provider,:oracle,'en',:index,:face_id,:name,:payload)""", cancel,
        "generation" to generation.value, "provider" to card.oracleId.source.value, "oracle" to card.oracleId.raw,
        "index" to face.identity.index.toLong(), "face_id" to face.identity.providerFaceId.knownOrNull(), "name" to face.name, "payload" to raw)
    write("""INSERT INTO catalog_name_fts(name,text,generation_id,provider,oracle_id,language) VALUES(:name,:text,:generation,:provider,:oracle,'en')""", cancel,
        "name" to card.name, "text" to (card.originalText.knownOrNull() ?: card.faces.joinToString(" ") { it.text.knownOrNull() ?: "" }),
        "generation" to generation.value, "provider" to card.oracleId.source.value, "oracle" to card.oracleId.raw)
}
private fun canonicalDigest(card: CardRecord): String {
    val fields = buildList {
        add(card.oracleId.source.value); add(card.oracleId.raw); add(card.language.value); add(card.name)
        fun <T> known(value: Knowledge<T>): String = when (value) {
            is Knowledge.Known -> "known:${value.value}"
            is Knowledge.Absent -> "absent"
            is Knowledge.Unknown -> "unknown"
        }
        add(known(card.originalText)); add(known(card.manaCost))
        add(when (val mana = card.manaValue) { is Knowledge.Known -> "known:${mana.value.stripTrailingZeros().toPlainString()}"; is Knowledge.Absent -> "absent"; is Knowledge.Unknown -> "unknown" })
        add(known(card.colors)); add(known(card.colorIdentity)); add(known(card.types))
        add(known(card.stats.power)); add(known(card.stats.toughness)); add(known(card.stats.loyalty)); add(known(card.stats.defense))
        add(known(card.keywords)); add(known(card.layout)); add(known(card.reserved))
        for (face in card.faces) {
            add(face.identity.oracleId.source.value); add(face.identity.oracleId.raw); add(face.identity.index.toString())
            add(face.name); add(known(face.text)); add(known(face.manaCost)); add(known(face.types))
            add(known(face.stats.power)); add(known(face.stats.toughness)); add(known(face.stats.loyalty)); add(known(face.stats.defense))
            for (relation in face.relationships) {
                add(relation.kind.name); add(known(relation.relatedOracle))
                add(when (val related = relation.relatedFace) {
                    is Knowledge.Known -> "known:${related.value.oracleId.source.value}:${related.value.oracleId.raw}:${related.value.index}"
                    is Knowledge.Absent -> "absent"; is Knowledge.Unknown -> "unknown"
                })
            }
        }
    }
    val bytes = fields.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
private suspend fun DbTransaction.insertPrinting(generation: GenerationId, printing: PrintingRecord, raw: String, cancel: CancelToken) {
    val oracle = printing.oracleId.knownOrNull()
    write("""INSERT INTO printing(generation_id,provider,printing_id,oracle_provider,oracle_id,oracle_language,set_code,set_name,collector_number,language,rarity,finishes,image_uri,metadata_payload) VALUES(:generation,:provider,:printing,:oracle_provider,:oracle,:oracle_language,:set,:set_name,:collector,:language,:rarity,:finishes,:image,:payload)""", cancel,
        "generation" to generation.value, "provider" to printing.id.source.value, "printing" to printing.id.raw,
        "oracle_provider" to oracle?.source?.value, "oracle" to oracle?.raw, "oracle_language" to oracle?.let { "en" },
        "set" to printing.setCode, "set_name" to printing.setName.knownOrNull(), "collector" to printing.collectorNumber,
        "language" to printing.language.value, "rarity" to printing.rarity.knownOrNull(),
        "finishes" to printing.finishes.knownOrNull()?.joinToString(",") { it.name }, "image" to printing.imageUri.knownOrNull()?.value, "payload" to raw)
    for (asset in printing.faceAssets) write("""INSERT INTO printing_face_asset(generation_id,provider,printing_id,face_index,provider_face_id,language,printed_name,asset_payload) VALUES(:generation,:provider,:printing,:index,:face_id,:language,:name,:payload)""", cancel,
        "generation" to generation.value, "provider" to printing.id.source.value, "printing" to printing.id.raw,
        "index" to asset.identity.index.toLong(), "face_id" to asset.identity.providerFaceId.knownOrNull(),
        "language" to asset.language.value, "name" to asset.printedName.knownOrNull(), "payload" to raw)
}
