package com.architectpsg.commandlibrary.feature.rules

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.rules.parser.*
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Offline English rules capability. Imports use finite parser limits and batches of at most 32 entries. */
public class LocalRules(private val database: Database, private val clock: Clock, private val ids: IdSource) : LocalRulesImport, LocalRulesLookup, KeywordRulesLookup {
    private companion object { val mutex = Mutex() }
    @Volatile private var initialized = false
    private val parser = RulesTextParser()
    private val cleanupCancel = object : CancelToken { override val isCancelled: Boolean = false; override fun throwIfCancelled(): Unit = Unit }

    override suspend fun `import`(source: LocalRulesImportSource, reopen: () -> Flow<ByteChunk>, onProgress: (JobProgress) -> Unit, cancel: CancelToken): Outcome<LocalRulesImportResult> = mutex.withLock {
        var generation: GenerationId? = null
        var attempt: EntityId? = null
        try {
            initializeLocked(cancel)
            val sourcePayload = RulesCodec.encode(RulesCodec.source(source))
            val generationId = GenerationId(ids.nextId().also { require(it.length <= 128 && '\u0000' !in it) })
            val attemptId = EntityId(ids.nextId())
            generation = generationId; attempt = attemptId
            val started = clock.utcNow()
            val durableAttempt = LocalRulesImportAttempt(attemptId, source, JobPhase.VALIDATING, started, Knowledge.Absent("Not finished"), null)
            database.transaction({ tx -> tx.write("INSERT INTO rules_import_attempt(attempt_id,generation_id,source_payload,attempt_payload,phase,started_at_epoch_seconds,started_at_nano,payload_schema_version) VALUES(:id,:generation,:source,:payload,'VALIDATING',:seconds,:nano,1)", cancel,
                "id" to attemptId.value, "generation" to generationId.value, "source" to sourcePayload, "payload" to RulesCodec.encode(RulesCodec.attempt(durableAttempt)), "seconds" to started.epochSeconds, "nano" to started.nanoAdjustment) }, cancel).requireSuccess()
            onProgress(JobProgress(attemptId, JobPhase.VALIDATING, 0, Knowledge.Unknown("Count unknown before validation", SnapshotList.empty()), "Validating local rules", null))
            cancel.check()
            val index = parsed(parser.index(reopen(), cancel))
            if (source.expectedBytes.known()?.let { it != index.byteCount } == true || source.expectedChecksum.known()?.let { !it.hex.equals(index.sha256.hex, true) } == true)
                throw RulesFailure(rulesFailure(FailureCode.SOURCE_MISMATCH, "Expected rules bytes or SHA-256 differ"))
            val provenance = Provenance(source.provider, source.sourceUri.known(), source.version, source.sourceDate, started, source.effectiveAt, generationId, index.sha256, source.notice.known())
            database.transaction({ tx -> tx.write("""INSERT INTO rules_corpus(generation_id,provider,language,status,content_uri,display_name,source_payload,provenance_payload,content_sha256,upstream_version,source_uri,effective_at_epoch_seconds,effective_at_nano,source_date_epoch_seconds,source_date_nano,observed_at_epoch_seconds,observed_at_nano,observed_bytes,entry_count,reference_count,payload_schema_version) VALUES(:id,:provider,'en','STAGING',:uri,:name,:source,:provenance,:hash,:version,:source_uri,:effective,:effective_nano,:date,:date_nano,:observed,:observed_nano,:bytes,0,0,1)""", cancel,
                "id" to generationId.value, "provider" to source.provider.value, "uri" to source.contentUri.value, "name" to source.displayName, "source" to sourcePayload,
                "provenance" to RulesCodec.encode(RulesCodec.provenance(provenance)), "hash" to index.sha256.hex, "version" to source.version.known(), "source_uri" to source.sourceUri.known()?.value,
                "effective" to source.effectiveAt.known()?.epochSeconds, "effective_nano" to source.effectiveAt.known()?.nanoAdjustment,
                "date" to source.sourceDate.known()?.epochSeconds, "date_nano" to source.sourceDate.known()?.nanoAdjustment,
                "observed" to started.epochSeconds, "observed_nano" to started.nanoAdjustment, "bytes" to index.byteCount)
                tx.write("UPDATE rules_import_attempt SET phase='STAGING',attempt_payload=:payload WHERE attempt_id=:id", cancel, "id" to attemptId.value, "payload" to RulesCodec.encode(RulesCodec.attempt(durableAttempt.copy(phase = JobPhase.STAGING))))
            }, cancel).requireSuccess()
            val batch = ArrayList<RuleEntry>(32)
            var staged = 0L
            suspend fun flush() {
                if (batch.isEmpty()) return
                database.transaction({ tx -> batch.forEach { insert(tx, it, cancel) } }, cancel).requireSuccess()
                staged += batch.size; batch.clear(); cancel.check()
                onProgress(JobProgress(attemptId, JobPhase.STAGING, staged, Knowledge.Known(index.identifiers.size.toLong()), "Staging local rules", null)); cancel.check()
            }
            val summary = parsed(parser.parse(reopen(), index, provenance, generationId, cancel) { batch.add(it); if (batch.size == 32) flush() })
            flush(); cancel.check()
            val activated = clock.utcNow()
            val corpus = LocalRulesCorpus(generationId, source, provenance, GenerationStatus.ACTIVE, summary.entries, summary.references)
            val result = LocalRulesImportResult(corpus, summary.byteCount, summary.sha256, if (source.expectedChecksum is Knowledge.Known) ChecksumVerification.MATCHED_EXPECTED else ChecksumVerification.COMPUTED_WITHOUT_EXPECTATION, activated)
            // Last callback is precommit. No callbacks or cancellation checkpoints follow successful commit.
            database.transaction({ tx -> tx.write("UPDATE rules_import_attempt SET phase='ACTIVATING',attempt_payload=:payload WHERE attempt_id=:id", cancel, "id" to attemptId.value, "payload" to RulesCodec.encode(RulesCodec.attempt(durableAttempt.copy(phase = JobPhase.ACTIVATING)))) }, cancel).requireSuccess()
            onProgress(JobProgress(attemptId, JobPhase.ACTIVATING, staged, Knowledge.Known(summary.entries), "Activating local rules", null)); cancel.check()
            database.transaction({ tx ->
                val counts = tx.query(rulesSql("SELECT (SELECT count(*) FROM rule_entry WHERE corpus_id=:id) AS entries,(SELECT count(*) FROM rule_reference WHERE corpus_id=:id) AS refs", 1, "id" to generationId.value), cancel).requireSuccess().single()
                if (counts.number("entries") != summary.entries || counts.number("refs") != summary.references) tx.abort(DomainFailure(FailureCode.PERSISTENCE, "Staged rules counts differ", SnapshotList.empty()))
                tx.write("UPDATE rules_corpus SET status='RETIRED' WHERE generation_id=(SELECT generation_id FROM rules_active WHERE singleton_id=1)", cancel)
                tx.write("UPDATE rules_corpus SET status='ACTIVE',entry_count=:entries,reference_count=:refs,activated_at_epoch_seconds=:seconds,activated_at_nano=:nano WHERE generation_id=:id AND status='STAGING'", cancel, "entries" to summary.entries, "refs" to summary.references, "seconds" to activated.epochSeconds, "nano" to activated.nanoAdjustment, "id" to generationId.value)
                tx.write("INSERT INTO rules_active(singleton_id,generation_id) VALUES(1,:id) ON CONFLICT(singleton_id) DO UPDATE SET generation_id=excluded.generation_id", cancel, "id" to generationId.value)
                tx.write("UPDATE rules_import_attempt SET phase='COMPLETED',attempt_payload=:payload,finished_at_epoch_seconds=:seconds,finished_at_nano=:nano WHERE attempt_id=:id", cancel, "seconds" to activated.epochSeconds, "nano" to activated.nanoAdjustment, "id" to attemptId.value, "payload" to RulesCodec.encode(RulesCodec.attempt(durableAttempt.copy(phase = JobPhase.COMPLETED, finishedAt = Knowledge.Known(activated)))))
            }, cancel).requireSuccess()
            Outcome.Success(result)
        } catch (cancelled: CancellationException) {
            if (generation != null && cleanup(generation, attempt, JobPhase.CANCELLED, null) !is Outcome.Success) initialized = false
            throw cancelled
        } catch (cause: Exception) {
            try { cancel.check() } catch (cancelled: CancellationException) {
                if (generation != null && cleanup(generation, attempt, JobPhase.CANCELLED, null) !is Outcome.Success) initialized = false
                throw cancelled
            }
            val outcome = classify(cause)
            if (generation != null && cleanup(generation, attempt, JobPhase.FAILED, (outcome as? Outcome.Failure)?.error) !is Outcome.Success) {
                initialized = false
                rulesFailure(FailureCode.PERSISTENCE, "Rules import failed; recovery cleanup pending")
            } else { cancel.check(); outcome }
        }
    }

    override suspend fun status(cancel: CancelToken): Outcome<LocalRulesStatus> = readOperation(cancel) {
        initialize(cancel)
        val active = database.query(rulesSql("SELECT g.* FROM rules_active a JOIN rules_corpus g ON g.generation_id=a.generation_id WHERE a.singleton_id=1"), cancel).requireSuccess().firstOrNull()
        val attempt = database.query(rulesSql("SELECT * FROM rules_import_attempt ORDER BY started_at_epoch_seconds DESC,started_at_nano DESC,rowid DESC LIMIT 1"), cancel).requireSuccess().firstOrNull()
        LocalRulesStatus(active?.let { Knowledge.Known(corpus(it).also { value -> stored { require(value.status == GenerationStatus.ACTIVE) } }) } ?: Knowledge.Absent("No rules installed"), attempt?.let { Knowledge.Known(attempt(it)) } ?: Knowledge.Absent("No rules import attempted"))
    }

    override suspend fun entry(id: RuleId, corpusId: GenerationId, cancel: CancelToken): Outcome<RuleEntry> = readOperation(cancel) {
        initialize(cancel)
        readTransaction(cancel) { tx ->
            val installed = tx.query(rulesSql("SELECT * FROM rules_corpus WHERE generation_id=:id AND status IN ('ACTIVE','RETIRED')", 1, "id" to corpusId.value), cancel).requireSuccess()
            if (installed.isEmpty()) throw RulesFailure(rulesFailure(FailureCode.STALE_GENERATION, "Rules snapshot unavailable"))
            val metadata = corpus(installed.single())
            val row = tx.query(rulesSql("SELECT * FROM rule_entry WHERE corpus_id=:corpus AND provider=:provider AND local_id=:id", 1, "corpus" to corpusId.value, "provider" to id.source.value, "id" to id.raw), cancel).requireSuccess().firstOrNull()
                ?: throw RulesFailure(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Rule not found in snapshot"))
            storedEntry(row, metadata).also { stored { require(it.id == id && it.corpusId == corpusId) } }
        }
    }

    override suspend fun lookupKeyword(keyword: String, corpusId: GenerationId?, cancel: CancelToken): Outcome<KeywordRuleLink> = readOperation(cancel) {
        require(keyword.isNotBlank() && keyword.codePointCount(0, keyword.length) <= 128) { "Keyword must contain at most 128 code points" }
        initialize(cancel)
        val key = keywordTitle(keyword)
        readTransaction(cancel) { tx ->
            // Guard both corpus bodies before materializing them, not after JSON decoding.
            val projection = keywordCorpusProjection()
            val selected = if (corpusId == null) tx.query(rulesSql("SELECT $projection FROM rules_active a JOIN rules_corpus g ON g.generation_id=a.generation_id WHERE a.singleton_id=1", 1), cancel).requireSuccess().firstOrNull()
                else tx.query(rulesSql("SELECT $projection FROM rules_corpus g WHERE g.generation_id=:id AND g.status IN ('ACTIVE','RETIRED')", 1, "id" to corpusId.value), cancel).requireSuccess().firstOrNull()
            if (selected == null) throw RulesFailure(if (corpusId == null) Outcome.Unavailable(UnavailableReason.NOT_INSTALLED, "No rules installed") else rulesFailure(FailureCode.STALE_GENERATION, "Rules snapshot unavailable"))
            stored { require(selected.number("keyword_projection_oversized") == 0L) }
            val metadata = corpus(selected)
            var after: Long? = null
            var count = 0
            var bytes = 0L
            var matches = 0
            var matchedOrdinal: Long? = null
            while (true) {
                cancel.check()
                // First page has no sentinel predicate: ordinal zero cannot disappear.
                val lower = if (after == null) "" else " AND ordinal>:after"
                val parameters = mutableListOf<Pair<String, Any?>>("corpus" to metadata.id.value)
                after?.let { parameters += "after" to it }
                val rows = tx.query(rulesSql("SELECT ordinal,length(CAST(title AS BLOB)) AS title_bytes,CASE WHEN length(CAST(title AS BLOB))<=4096 THEN title ELSE NULL END AS title FROM rule_entry WHERE corpus_id=:corpus AND kind='GLOSSARY'$lower ORDER BY ordinal LIMIT 100", 100, *parameters.toTypedArray()), cancel).requireSuccess()
                if (rows.isEmpty()) break
                for (row in rows) {
                    val ordinal = stored { row.number("ordinal").also { require(it >= 0 && (after == null || it > after!!)) } }
                    val titleBytes = stored { row.number("title_bytes").also { require(it > 0) } }
                    if (++count > 20_000 || titleBytes > 4096 || titleBytes > 8L * 1024 * 1024 - bytes)
                        throw RulesFailure(Outcome.Unavailable(UnavailableReason.SOURCE_UNAVAILABLE, "Exact glossary mapping exceeds its finite scan budget"))
                    val title = stored { row.text("title").also { require(it.isNotBlank() && it.toByteArray(Charsets.UTF_8).size.toLong() == titleBytes) } }
                    bytes += titleBytes
                    if (keywordTitle(title) == key) { matches++; if (matchedOrdinal == null) matchedOrdinal = ordinal }
                    after = ordinal
                }
            }
            val evidence = SnapshotList.of(Evidence("Exact normalized glossary-title lookup in installed corpus ${metadata.id.value}", metadata.provenance.source, metadata.provenance.fetchedAt))
            val target: Knowledge<RuleId> = when {
                matches == 0 -> Knowledge.Absent("No exact glossary title in this corpus")
                matches > 1 -> Knowledge.Unknown("Multiple glossary titles normalize to this keyword", evidence)
                else -> {
                    val columns = keywordTextColumns("e", listOf("corpus_id", "provider", "local_id", "kind", "number", "title", "text", "entry_payload")) + ",e.ordinal"
                    val row = tx.query(rulesSql("SELECT $columns FROM rule_entry e WHERE e.corpus_id=:corpus AND e.ordinal=:ordinal", 1,
                        "corpus" to metadata.id.value, "ordinal" to requireNotNull(matchedOrdinal)), cancel).requireSuccess().single()
                    stored { require(row.number("keyword_projection_oversized") == 0L) }
                    val entry = storedEntry(row, metadata)
                    stored { require(entry.kind == RuleEntryKind.GLOSSARY && keywordTitle(requireNotNull(entry.title.known())) == key) }
                    Knowledge.Known(entry.id)
                }
            }
            KeywordRuleLink(keyword, metadata.id, target, evidence)
        }
    }

    private fun keywordTitle(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT)

    private fun keywordTextColumns(alias: String, columns: List<String>): String {
        val oversized = columns.joinToString(" OR ") { "length(CAST($alias.$it AS BLOB))>2097152" }
        return columns.joinToString(",") {
            "CASE WHEN length(CAST($alias.$it AS BLOB))<=2097152 THEN $alias.$it ELSE NULL END AS $it"
        } + ",CASE WHEN ($oversized) THEN 1 ELSE 0 END AS keyword_projection_oversized"
    }

    private fun keywordCorpusProjection(): String = keywordTextColumns("g", listOf("generation_id", "provider", "language", "status", "content_uri", "display_name",
        "source_payload", "provenance_payload", "upstream_version", "source_uri", "content_sha256")) + "," +
        listOf("effective_at_epoch_seconds", "effective_at_nano", "source_date_epoch_seconds", "source_date_nano", "observed_at_epoch_seconds", "observed_at_nano", "observed_bytes", "entry_count", "reference_count", "payload_schema_version", "activated_at_epoch_seconds", "activated_at_nano").joinToString(",") { "g.$it" }

    /** Literal Unicode words, AND semantics; at most 256 code points and 32 words. Empty text lists entries. */
    override suspend fun search(query: RulesQuery, cancel: CancelToken): Outcome<Page<RuleEntry>> = readOperation(cancel) {
        initialize(cancel)
        val requestedCorpus = query.corpusId
        require(query.text.codePointCount(0, query.text.length) <= 256) { "Rules query exceeds 256 code points" }
        val tokens = RulesCursors.tokens(query.text); require(tokens.size <= 32) { "Rules query exceeds 32 words" }
        require(query.text.isBlank() || tokens.isNotEmpty()) { "Rules query needs a Unicode word" }
        val kinds = query.kinds.map { it.name }.distinct().sorted()
        val key = RulesCursors.key(tokens.joinToString(" "), kinds)
        val cursor = try { query.page.cursor?.let { RulesCursors.decode(it) } } catch (_: Exception) { throw RulesFailure(rulesFailure(FailureCode.INVALID_INPUT, "Malformed rules cursor")) }
        if (cursor != null && cursor.query != key) throw RulesFailure(rulesFailure(FailureCode.INVALID_INPUT, "Cursor query changed"))
        readTransaction(cancel) { tx ->
            val selected = if (requestedCorpus == null) tx.query(rulesSql("SELECT g.* FROM rules_active a JOIN rules_corpus g ON g.generation_id=a.generation_id WHERE a.singleton_id=1"), cancel).requireSuccess().firstOrNull()
                else tx.query(rulesSql("SELECT * FROM rules_corpus WHERE generation_id=:id AND status IN ('ACTIVE','RETIRED')", 1, "id" to requestedCorpus.value), cancel).requireSuccess().firstOrNull()
            if (selected == null) throw RulesFailure(if (requestedCorpus != null || cursor != null) rulesFailure(FailureCode.STALE_GENERATION, "Rules snapshot unavailable") else Outcome.Unavailable(UnavailableReason.NOT_INSTALLED, "No rules installed"))
            val metadata = corpus(selected)
            val generation = metadata.id.value
            if (cursor != null && cursor.generation != generation) throw RulesFailure(rulesFailure(FailureCode.STALE_GENERATION, "Rules snapshot changed"))
            val kindSql = if (kinds.isEmpty()) "" else " AND e.kind IN (${kinds.indices.joinToString(",") { ":kind$it" }})"
            val ftsJoin = if (tokens.isEmpty()) "" else " JOIN rules_text_fts ON rules_text_fts.corpus_id=e.corpus_id AND rules_text_fts.provider=e.provider AND rules_text_fts.local_id=e.local_id"
            val ftsSql = if (tokens.isEmpty()) "" else " AND rules_text_fts MATCH :match"
            val values = mutableListOf<Pair<String, Any?>>("generation" to generation, "ordinal" to (cursor?.ordinal ?: -1L), "limit" to query.page.limit + 1)
            kinds.forEachIndexed { i, value -> values.add("kind$i" to value) }; if (tokens.isNotEmpty()) values.add("match" to RulesCursors.match(tokens))
            val rows = tx.query(rulesSql("SELECT e.* FROM rule_entry e$ftsJoin WHERE e.corpus_id=:generation AND e.ordinal>:ordinal$kindSql$ftsSql ORDER BY e.ordinal LIMIT :limit", query.page.limit + 1, *values.toTypedArray()), cancel).requireSuccess()
            val entries = rows.take(query.page.limit).map { storedEntry(it, metadata) }
            stored { require(entries.zipWithNext().all { (first, second) -> first.ordinal < second.ordinal }) }
            val next = if (rows.size > query.page.limit) RulesCursors.encode(RulesCursor(generation, key, entries.last().ordinal)) else null
            Page(SnapshotList.copyOf(entries), next, SnapshotReference(metadata.source.provider, metadata.id, null, LanguageTag.ENGLISH))
        }
    }

    private suspend fun insert(tx: DbTransaction, e: RuleEntry, cancel: CancelToken) {
        tx.write("INSERT INTO rule_entry(corpus_id,provider,local_id,kind,number,title,text,ordinal,entry_payload) VALUES(:corpus,:provider,:id,:kind,:number,:title,:text,:ordinal,:payload)", cancel,
            "corpus" to e.corpusId.value, "provider" to e.id.source.value, "id" to e.id.raw, "kind" to e.kind.name, "number" to e.number.known(), "title" to e.title.known(), "text" to e.text, "ordinal" to e.ordinal, "payload" to RulesCodec.encode(RulesCodec.entry(e)))
        tx.write("INSERT INTO rules_text_fts(title,text,corpus_id,provider,local_id) VALUES(:title,:text,:corpus,:provider,:id)", cancel, "title" to e.title.known().orEmpty(), "text" to e.text, "corpus" to e.corpusId.value, "provider" to e.id.source.value, "id" to e.id.raw)
        e.references.forEachIndexed { i, r -> tx.write("INSERT INTO rule_reference(corpus_id,from_provider,from_local_id,ref_ordinal,raw_target,resolved_provider,resolved_local_id,evidence_payload) VALUES(:corpus,:provider,:id,:ordinal,:target,:resolved_provider,:resolved_id,:payload)", cancel,
            "corpus" to e.corpusId.value, "provider" to e.id.source.value, "id" to e.id.raw, "ordinal" to i, "target" to r.rawTarget, "resolved_provider" to r.resolved.known()?.source?.value, "resolved_id" to r.resolved.known()?.raw, "payload" to RulesCodec.encode(RulesCodec.reference(r))) }
    }
    /** Stored bytes/projections are corruption, never caller input errors. */
    private inline fun <T> stored(action: () -> T): T = try { action() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw RulesFailure(rulesFailure(FailureCode.CORRUPT_DATA, "Stored rules metadata or payload is inconsistent")) }
    private fun corpus(row: RowData): LocalRulesCorpus = stored {
        val source = RulesCodec.source(RulesCodec.decode(row.text("source_payload")))
        val provenance = RulesCodec.provenance(RulesCodec.decode(row.text("provenance_payload")))
        val value = LocalRulesCorpus(GenerationId(row.text("generation_id")), source, provenance, GenerationStatus.valueOf(row.text("status")), row.number("entry_count"), row.number("reference_count"))
        require(row.number("payload_schema_version") == 1L && value.status in setOf(GenerationStatus.ACTIVE, GenerationStatus.RETIRED))
        require(source.provider.value == row.text("provider") && source.language.value == row.text("language") && source.language == LanguageTag.ENGLISH)
        require(source.contentUri.value == row.text("content_uri") && source.displayName == row.text("display_name"))
        require(source.version.known() == row.optionalText("upstream_version") && source.sourceUri.known()?.value == row.optionalText("source_uri"))
        require(source.effectiveAt.known() == row.date("effective_at") && source.sourceDate.known() == row.date("source_date"))
        require(provenance.provider == source.provider && provenance.generation == value.id && provenance.source == source.sourceUri.known())
        require(provenance.version == source.version && provenance.sourceDate == source.sourceDate && provenance.effectiveAt == source.effectiveAt && provenance.notice == source.notice.known())
        require(provenance.fetchedAt == row.date("observed_at") && provenance.checksum == ContentHash("SHA-256", row.text("content_sha256")))
        require(row.number("observed_bytes") >= 0 && row.date("activated_at") != null)
        value
    }
    private fun attempt(row: RowData): LocalRulesImportAttempt = stored {
        val value = RulesCodec.attempt(RulesCodec.decode(row.text("attempt_payload")))
        require(row.number("payload_schema_version") == 1L && value.id.value == row.text("attempt_id") && value.phase.name == row.text("phase"))
        require(value.source == RulesCodec.source(RulesCodec.decode(row.text("source_payload"))))
        require(value.startedAt == row.date("started_at") && value.finishedAt.known() == row.date("finished_at"))
        require(value.failure == row.optionalText("failure_payload")?.let { RulesCodec.failure(RulesCodec.decode(it)) })
        value
    }
    private fun storedEntry(row: RowData, corpus: LocalRulesCorpus): RuleEntry = stored {
        val value = RulesCodec.entry(RulesCodec.decode(row.text("entry_payload")))
        require(value.id.source.value == row.text("provider") && value.id.raw == row.text("local_id") && value.ordinal == row.number("ordinal"))
        require(value.corpusId.value == row.text("corpus_id") && value.corpusId == corpus.id && value.id.source == corpus.source.provider)
        require(value.provenance == corpus.provenance && value.provenance.provider == value.id.source && value.provenance.generation == corpus.id)
        require(value.kind.name == row.text("kind") && value.number.known() == row.optionalText("number") && value.title.known() == row.optionalText("title") && value.text == row.text("text"))
        value
    }
    private suspend fun initialize(cancel: CancelToken) { if (!initialized) mutex.withLock { initializeLocked(cancel) } }
    private suspend fun initializeLocked(cancel: CancelToken) {
        cancel.check(); if (initialized) return
        while (true) {
            val rows = database.query(rulesSql("SELECT generation_id,attempt_id FROM rules_import_attempt WHERE phase NOT IN ('COMPLETED','FAILED','CANCELLED') LIMIT 100", 100), cancel).requireSuccess()
            if (rows.isEmpty()) break
            rows.forEach { row -> cleanup(row.optionalText("generation_id")?.let(::GenerationId), EntityId(row.text("attempt_id")), JobPhase.FAILED, DomainFailure(FailureCode.PERSISTENCE, "Interrupted rules import", SnapshotList.empty())).requireSuccess() }
        }
        initialized = true
    }
    private suspend fun cleanup(id: GenerationId?, attempt: EntityId?, phase: JobPhase, failure: DomainFailure?): Outcome<Unit> = withContext(NonCancellable) {
        try { withTimeout(2_000) {
            database.transaction({ tx ->
                // Every destructive predicate independently checks status AND the active pointer.
                tx.write("DELETE FROM rules_text_fts WHERE corpus_id=:id AND EXISTS (SELECT 1 FROM rules_corpus WHERE generation_id=:id AND status='STAGING') AND NOT EXISTS (SELECT 1 FROM rules_active WHERE generation_id=:id)", cleanupCancel, "id" to id?.value)
                tx.write("DELETE FROM rules_corpus WHERE generation_id=:id AND status='STAGING' AND NOT EXISTS (SELECT 1 FROM rules_active WHERE generation_id=:id)", cleanupCancel, "id" to id?.value)
                if (attempt != null) {
                    val now = clock.utcNow()
                    val row = tx.query(rulesSql("SELECT attempt_payload FROM rules_import_attempt WHERE attempt_id=:attempt", 1, "attempt" to attempt.value), cleanupCancel).requireSuccess().firstOrNull()
                    val payload = row?.let { stored { RulesCodec.attempt(RulesCodec.decode(it.text("attempt_payload"))) }.copy(phase = phase, finishedAt = Knowledge.Known(now), failure = failure) }?.let { RulesCodec.encode(RulesCodec.attempt(it)) }
                    tx.write("UPDATE rules_import_attempt SET phase=:phase,attempt_payload=:payload,finished_at_epoch_seconds=:seconds,finished_at_nano=:nano,failure_payload=:failure WHERE attempt_id=:attempt AND phase!='COMPLETED' AND NOT EXISTS (SELECT 1 FROM rules_active WHERE generation_id=:id)", cleanupCancel,
                        "phase" to phase.name, "payload" to payload, "seconds" to now.epochSeconds, "nano" to now.nanoAdjustment, "failure" to failure?.let { RulesCodec.encode(RulesCodec.failure(it)) }, "attempt" to attempt.value, "id" to id?.value)
                }
            }, cleanupCancel)
        } } catch (_: Exception) { initialized = false; rulesFailure(FailureCode.PERSISTENCE, "Rules cleanup pending") }
    }
    private suspend fun <T> readTransaction(cancel: CancelToken, action: suspend (DbTransaction) -> T): T = database.transaction({ tx ->
        try { Outcome.Success(action(tx)) } catch (failure: RulesFailure) { failure.outcome }
    }, cancel).requireSuccess().requireSuccess()
    private fun <T> parsed(result: RulesParseResult<T>): T = when (result) { is RulesParseResult.Ok -> result.value; is RulesParseResult.Failed -> throw RulesFailure(rulesFailure(if (result.failure is RulesParseFailure.SourceChanged) FailureCode.SOURCE_MISMATCH else FailureCode.INVALID_INPUT, "Rules parser rejected input: ${result.failure}")) }
    private fun classify(cause: Exception): Outcome<Nothing> = when (cause) {
        is RulesFailure -> cause.outcome
        is IOException, is SecurityException -> rulesFailure(FailureCode.PERMISSION, "Local rules input unavailable")
        is IllegalArgumentException -> rulesFailure(FailureCode.INVALID_INPUT, cause.message ?: "Invalid rules input")
        else -> rulesFailure(FailureCode.CORRUPT_DATA, "Rules operation failed: ${cause.javaClass.simpleName}")
    }
    private suspend fun <T> readOperation(cancel: CancelToken, action: suspend () -> T): Outcome<T> = try { cancel.check(); val value = action(); cancel.check(); Outcome.Success(value) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (cause: Exception) { cancel.check(); classify(cause) }
}
