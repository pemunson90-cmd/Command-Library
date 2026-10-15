package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlin.io.encoding.Base64

/** Transactional local history. Content is immutable except explicit cumulative checkpoints/deletion. */
public class SqlLocalAiHistory(
    private val db: Database,
    private val codec: AiHistoryPayloadCodecPort,
    private val clock: Clock,
) : LocalAiHistory {
    public override suspend fun createConversation(input: NewConversation, cancel: CancelToken): Outcome<Conversation> = operation(cancel, {
        id(input.id); deck(input.deckId); utf8(input.title, AiHistoryLimits.MAX_TITLE_BYTES)
    }) { tx ->
        if (exists(tx, "ai_conversation", "conversation_id", input.id, cancel)) stale()
        val sequence = next(epoch(tx, cancel))
        val now = clock.utcNow()
        val bytes = utf8(input.title, AiHistoryLimits.MAX_TITLE_BYTES)
        val result = Conversation(input.id, input.deckId, input.mode, input.title, Revision(1), now, now)
        execute(tx, """INSERT INTO ai_conversation
            (conversation_id,revision,change_sequence,deleted,deck_source,deck_raw,mode,created_seconds,created_nanos,modified_seconds,modified_nanos,payload_version,payload_length,payload_sha256,payload)
            VALUES (:id,1,:sequence,0,:source,:raw,:mode,:seconds,:nanos,:seconds,:nanos,1,:length,:hash,:payload)""", cancel, 1,
            "id" to text(input.id.value), "sequence" to num(sequence), "source" to text(input.deckId.source.value),
            "raw" to text(input.deckId.raw), "mode" to text(input.mode.name), *time(now), *payload(bytes))
        result
    }

    public override suspend fun getConversation(id: EntityId, cancel: CancelToken): Outcome<Conversation> =
        operation(cancel, { id(id) }) { tx -> conversation(tx, header(tx, id, cancel), cancel) }

    public override suspend fun pageConversations(deckId: DeckId, page: PageRequest, cancel: CancelToken): Outcome<Page<Conversation>> =
        operation(cancel, { deck(deckId); page.cursor?.let(::cursor) }) { tx ->
            val epoch = epoch(tx, cancel)
            val frame = page.cursor?.let(::cursor)
            if (frame != null && (frame.kind != 1 || frame.scope != listOf(deckId.source.value, deckId.raw) || frame.epoch != epoch)) stale()
            val params = mutableListOf("source" to text(deckId.source.value), "raw" to text(deckId.raw), "limit" to num(page.limit + 1L))
            val after = if (frame == null) "" else "AND conversation_id COLLATE BINARY>:after".also { params += "after" to text(frame.last) }
            val rows = query(tx, "SELECT $HEADER FROM ai_conversation WHERE deck_source=:source AND deck_raw=:raw AND (typeof(deleted)!='integer' OR deleted<>1) $after ORDER BY conversation_id COLLATE BINARY LIMIT :limit", cancel, page.limit + 1, *params.toTypedArray())
            val metas = rows.map(::header)
            val items = metas.take(page.limit).map { meta ->
                if (meta.deck != deckId) corrupt()
                conversation(tx, meta, cancel)
            }
            Page(SnapshotList.copyOf(items), if (metas.size > page.limit) Cursor(1, listOf(deckId.source.value, deckId.raw), epoch, items.last().id.value).encode() else null, snapshot(epoch))
        }

    public override suspend fun pageTurns(conversationId: EntityId, page: PageRequest, cancel: CancelToken): Outcome<Page<HistoryTurnSummary>> =
        operation(cancel, { id(conversationId); page.cursor?.let(::cursor) }) { tx ->
            val parent = header(tx, conversationId, cancel)
            val frame = page.cursor?.let(::cursor)
            if (frame != null && (frame.kind != 2 || frame.scope != listOf(conversationId.value) || frame.epoch != parent.revision.value)) stale()
            val after = frame?.last?.let { canonicalLong(it, positive = true) } ?: 0L
            val rows = query(tx, "SELECT $TURN FROM ai_turn WHERE conversation_id=:id AND (typeof(deleted)!='integer' OR deleted<>1) AND (typeof(ordinal)!='integer' OR ordinal<=0 OR ordinal>:after) ORDER BY ordinal LIMIT :limit", cancel, page.limit + 1,
                "id" to text(conversationId.value), "after" to num(after), "limit" to num(page.limit + 1L))
            val metas = rows.map(::turnMetadata)
            if (metas.any { it.value.conversationId != conversationId }) corrupt()
            val items = metas.take(page.limit).map { m -> m.value.let { HistoryTurnSummary(it.id, it.conversationId, it.revision, it.ordinal, it.source, it.status, it.startedAt, it.finishedAt) } }
            Page(SnapshotList.copyOf(items), if (metas.size > page.limit) Cursor(2, listOf(conversationId.value), parent.revision.value, items.last().ordinal.toString()).encode() else null, snapshot(parent.revision.value))
        }

    public override suspend fun getTurn(id: EntityId, cancel: CancelToken): Outcome<Turn> = operation(cancel, { id(id) }) { tx ->
        val meta = turnMetadata(tx, id, cancel)
        turn(tx, meta, header(tx, meta.value.conversationId, cancel), cancel)
    }

    public override suspend fun appendTurn(conversationId: EntityId, expectedRevision: Revision, input: NewHistoryTurn, cancel: CancelToken): Outcome<Turn> =
        operation(cancel, {
            id(conversationId); id(input.id); input.ownerEpoch?.let(::id); input.providerProfileId?.let(::id); input.retryOf?.let(::id)
            val mode = OfflineLabMode.entries.singleOrNull { it.templateId == input.prompt.templateId } ?: invalid()
            val preview = initial(conversationId, mode, input, UtcTimestamp(0))
            value(codec.encodeTurn(preview))
        }) { tx ->
            val parent = header(tx, conversationId, cancel); expected(parent.revision, expectedRevision)
            if (exists(tx, "ai_turn", "turn_id", input.id, cancel)) stale()
            if (input.prompt.deckId != parent.deck || input.prompt.templateId != parent.mode.templateId) invalid()
            input.retryOf?.let { retryId ->
                val retry = turnMetadata(tx, retryId, cancel).value
                if (retry.conversationId != conversationId || retry.source != HistoryTurnSource.PROVIDER_GENERATION || !terminal(retry.status)) invalid()
            }
            val ordinalRow = query(tx, """SELECT CASE WHEN COUNT(*)=0 THEN 0 WHEN MIN(CASE WHEN typeof(ordinal)='integer' AND ordinal>0 THEN 1 ELSE 0 END)=1 THEN MAX(ordinal) ELSE NULL END AS maximum FROM ai_turn WHERE conversation_id=:id""", cancel, 1, "id" to text(conversationId.value)).single()
            val ordinal = next(ordinalRow.long("maximum"))
            val revision = next(parent.revision.value); val sequence = next(epoch(tx, cancel))
            val now = clock.utcNow(); val result = initial(conversationId, parent.mode, input, now).copy(ordinal = ordinal)
            val bytes = value(codec.encodeTurn(result)).copyBytes()
            execute(tx, """INSERT INTO ai_turn
                (turn_id,conversation_id,revision,ordinal,source,owner_epoch,status,deleted,started_seconds,started_nanos,finished_seconds,finished_nanos,payload_version,payload_length,payload_sha256,payload)
                VALUES (:id,:parent,1,:ordinal,:source,:owner,:status,0,:seconds,:nanos,:finished,:finishedNano,1,:length,:hash,:payload)""", cancel, 1,
                "id" to text(result.id.value), "parent" to text(conversationId.value), "ordinal" to num(ordinal), "source" to text(result.source.name),
                "owner" to nullable(result.ownerEpoch?.value), "status" to text(result.status.name), *time(now), *finished(result.finishedAt), *payload(bytes))
            touch(tx, parent, revision, sequence, now, cancel)
            result
        }

    public override suspend fun checkpoint(turnId: EntityId, expectedRevision: Revision, ownerEpoch: EntityId, progress: HistoryProgress, cancel: CancelToken): Outcome<Turn> =
        operation(cancel, { id(turnId); id(ownerEpoch); utf8(progress.rawContent, AiHistoryLimits.MAX_REPLY_BYTES) }) { tx ->
            val meta = turnMetadata(tx, turnId, cancel); val parent = header(tx, meta.value.conversationId, cancel)
            expected(meta.value.revision, expectedRevision)
            if (meta.value.ownerEpoch != ownerEpoch || meta.value.source != HistoryTurnSource.PROVIDER_GENERATION || terminal(meta.value.status)) stale()
            if (progress.status == TurnStatus.PENDING && meta.value.status != TurnStatus.PENDING) invalid()
            val old = turn(tx, meta, parent, cancel)
            if (!progress.rawContent.startsWith(old.rawContent)) invalid()
            val candidate = old.copy(status = progress.status, rawContent = progress.rawContent, usage = progress.usage,
                finishedAt = if (terminal(progress.status)) Knowledge.Known(UtcTimestamp(0)) else unfinished())
            // Validates usage, Unicode and aggregate content budget before no-op and before the clock.
            value(codec.encodeTurn(candidate))
            if (old.status == progress.status && old.rawContent == progress.rawContent && old.usage == progress.usage) old
            else {
                if (progress.status == TurnStatus.PENDING) invalid()
                val revision = next(old.revision.value); val parentRevision = next(parent.revision.value); val sequence = next(epoch(tx, cancel))
                val now = clock.utcNow()
                val result = candidate.copy(revision = Revision(revision), finishedAt = if (terminal(progress.status)) Knowledge.Known(now) else unfinished())
                val bytes = value(codec.encodeTurn(result)).copyBytes()
                execute(tx, """UPDATE ai_turn SET revision=:revision,status=:status,finished_seconds=:finished,finished_nanos=:finishedNano,
                    payload_version=1,payload_length=:length,payload_sha256=:hash,payload=:payload
                    WHERE turn_id=:id AND revision=:expected AND owner_epoch=:owner AND deleted=0 AND status=:oldStatus""", cancel, 1,
                    "revision" to num(revision), "status" to text(result.status.name), *finished(result.finishedAt), *payload(bytes),
                    "id" to text(turnId.value), "expected" to num(expectedRevision.value), "owner" to text(ownerEpoch.value), "oldStatus" to text(old.status.name))
                touch(tx, parent, parentRevision, sequence, now, cancel)
                result
            }
        }

    public override suspend fun deleteConversation(id: EntityId, expectedRevision: Revision, cancel: CancelToken): Outcome<Unit> =
        operation(cancel, { id(id) }) { tx ->
            val parent = header(tx, id, cancel); expected(parent.revision, expectedRevision)
            val revision = next(parent.revision.value); val sequence = next(epoch(tx, cancel))
            // Bounded metadata scan checks future/corrupt live rows without loading any private content.
            scanTurns(tx, id, null, cancel) { }
            val count = query(tx, "SELECT COUNT(*) AS count FROM ai_turn WHERE conversation_id=:id", cancel, 1, "id" to text(id.value)).single().long("count")
            val now = clock.utcNow()
            execute(tx, """UPDATE ai_turn SET deleted=1,source='',owner_epoch=NULL,status='',finished_seconds=NULL,finished_nanos=NULL,
                payload_version=1,payload_length=0,payload_sha256=:hash,payload=NULL WHERE conversation_id=:id""", cancel, count,
                "hash" to text(EMPTY_HASH), "id" to text(id.value))
            execute(tx, """UPDATE ai_conversation SET revision=:revision,change_sequence=:sequence,deleted=1,deck_source='',deck_raw='',mode='',
                modified_seconds=:seconds,modified_nanos=:nanos,payload_version=1,payload_length=0,payload_sha256=:hash,payload=NULL
                WHERE conversation_id=:id AND revision=:expected AND deleted=0""", cancel, 1,
                "revision" to num(revision), "sequence" to num(sequence), *time(now), "hash" to text(EMPTY_HASH), "id" to text(id.value), "expected" to num(expectedRevision.value))
        }

    public override suspend fun recover(ownerEpoch: EntityId, cancel: CancelToken): Outcome<Long> = operation(cancel, { id(ownerEpoch) }) { tx ->
        // First pass is read-only: no payload read, clock or write until every relevant row is valid.
        var changed = 0L; var parents = 0L
        scanParents(tx, cancel) { parent ->
            var count = 0L
            scanTurns(tx, parent.id, ownerEpoch, cancel) { meta -> next(meta.value.revision.value); count = next(count) }
            if (count > 0) { next(parent.revision.value); parents = next(parents); if (Long.MAX_VALUE - changed < count) invalid(); changed += count }
        }
        if (changed == 0L) 0L else {
            var sequence = epoch(tx, cancel)
            if (Long.MAX_VALUE - sequence < parents) invalid()
            val now = clock.utcNow()
            scanParents(tx, cancel) { parent ->
                var count = 0L
                scanTurns(tx, parent.id, ownerEpoch, cancel) { meta ->
                    val turn = meta.value
                    execute(tx, """UPDATE ai_turn SET revision=:revision,status='INTERRUPTED',finished_seconds=:seconds,finished_nanos=:nanos
                        WHERE turn_id=:id AND deleted=0 AND revision=:expected AND owner_epoch=:owner AND status=:status""", cancel, 1,
                        "revision" to num(turn.revision.value + 1), *time(now), "id" to text(turn.id.value), "expected" to num(turn.revision.value),
                        "owner" to nullable(turn.ownerEpoch?.value), "status" to text(turn.status.name))
                    count++
                }
                if (count > 0) { sequence++; touch(tx, parent, parent.revision.value + 1, sequence, now, cancel) }
            }
            changed
        }
    }

    private fun initial(parent: EntityId, mode: OfflineLabMode, input: NewHistoryTurn, now: UtcTimestamp): Turn {
        val offline = input.source == HistoryTurnSource.OFFLINE_CAPTURE
        val modelId = input.modelId
        if (offline) {
            if (input.providerProfileId != null || modelId != null || input.retryOf != null || input.ownerEpoch != null) invalid()
        } else {
            if (input.providerProfileId == null || input.ownerEpoch == null || modelId.isNullOrBlank() || input.rawContent.isNotEmpty()) invalid()
            utf8(modelId, AiHistoryLimits.MAX_METADATA_BYTES)
        }
        return Turn(input.id, parent, Revision(1), 1, input.source, input.prompt, input.provenance, mode,
            input.providerProfileId, modelId, input.retryOf, input.ownerEpoch,
            if (offline) TurnStatus.COMPLETE else TurnStatus.PENDING, input.rawContent,
            Knowledge.Unknown(if (offline) "Offline capture" else AiHistoryLimits.UNFINISHED_REASON, SnapshotList.empty()), now,
            if (offline) Knowledge.Known(now) else unfinished())
    }

    private data class Header(val id: EntityId, val revision: Revision, val sequence: Long, val deck: DeckId, val mode: OfflineLabMode,
        val created: UtcTimestamp, val modified: UtcTimestamp, val length: Long, val hash: String)
    private data class Meta(val value: HistoryTurnMetadata, val length: Long, val hash: String)
    private suspend fun header(tx: DbTransaction, id: EntityId, cancel: CancelToken): Header {
        val row = query(tx, "SELECT $HEADER FROM ai_conversation WHERE conversation_id=:id", cancel, 1, "id" to text(id.value)).singleOrNull() ?: missing()
        return header(row).also { if (it.id != id) corrupt() }
    }
    private fun header(row: RowData): Header {
        val id = storedId(row.utf8("conversation_id")); val revision = positive(row.long("revision")); val sequence = positive(row.long("change_sequence"))
        deleted(row)
        val length = preflight(row, AiHistoryLimits.MAX_TITLE_BYTES, allowEmpty = true)
        val deck = DeckId(ProviderId(storedText(row.utf8("deck_source"), ID_BYTES)), storedText(row.utf8("deck_raw"), ID_BYTES))
        val mode = OfflineLabMode.entries.singleOrNull { it.name == row.utf8("mode") } ?: corrupt()
        return Header(id, Revision(revision), sequence, deck, mode, timestamp(row, "created"), timestamp(row, "modified"), length, row.utf8("payload_sha256"))
    }
    private suspend fun conversation(tx: DbTransaction, meta: Header, cancel: CancelToken): Conversation =
        Conversation(meta.id, meta.deck, meta.mode, decode(blob(tx, "ai_conversation", "conversation_id", meta.id, meta.length, meta.hash, cancel)), meta.revision, meta.created, meta.modified)
    private suspend fun turnMetadata(tx: DbTransaction, id: EntityId, cancel: CancelToken): Meta {
        val row = query(tx, "SELECT $TURN FROM ai_turn WHERE turn_id=:id", cancel, 1, "id" to text(id.value)).singleOrNull() ?: missing()
        return turnMetadata(row).also { if (it.value.id != id) corrupt() }
    }
    private fun turnMetadata(row: RowData): Meta {
        val id = storedId(row.utf8("turn_id")); val parent = storedId(row.utf8("conversation_id"))
        val revision = positive(row.long("revision")); val ordinal = positive(row.long("ordinal")); deleted(row)
        val length = preflight(row, AiHistoryLimits.MAX_PAYLOAD_BYTES, allowEmpty = false)
        val source = HistoryTurnSource.entries.singleOrNull { it.name == row.utf8("source") } ?: corrupt()
        val status = TurnStatus.entries.singleOrNull { it.name == row.utf8("status") } ?: corrupt()
        val owner = if (row.column("owner_epoch") == SqlValue.Null) null else storedId(row.utf8("owner_epoch"))
        if (source == HistoryTurnSource.OFFLINE_CAPTURE && (owner != null || status != TurnStatus.COMPLETE) || source == HistoryTurnSource.PROVIDER_GENERATION && owner == null) corrupt()
        val finished = if (terminal(status)) Knowledge.Known(timestamp(row, "finished")) else {
            if (row.column("finished_seconds") != SqlValue.Null || row.column("finished_nanos") != SqlValue.Null) corrupt()
            unfinished()
        }
        return Meta(HistoryTurnMetadata(id, parent, Revision(revision), ordinal, source, owner, status, timestamp(row, "started"), finished), length, row.utf8("payload_sha256"))
    }
    private suspend fun turn(tx: DbTransaction, meta: Meta, parent: Header, cancel: CancelToken): Turn {
        val result = value(codec.decodeTurn(SnapshotBytes(blob(tx, "ai_turn", "turn_id", meta.value.id, meta.length, meta.hash, cancel)), meta.value))
        val m = meta.value
        if (result.id != m.id || result.conversationId != m.conversationId || result.revision != m.revision || result.ordinal != m.ordinal || result.source != m.source ||
            result.ownerEpoch != m.ownerEpoch || result.status != m.status || result.startedAt != m.startedAt || result.finishedAt != m.finishedAt ||
            result.mode != parent.mode || result.prompt.deckId != parent.deck || result.prompt.templateId != parent.mode.templateId) corrupt()
        return result
    }
    private fun preflight(row: RowData, limit: Int, allowEmpty: Boolean): Long {
        val version = row.long("payload_version")
        if (version > 1) throw Problem(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Newer history format"))
        if (version != 1L) corrupt()
        val length = row.long("payload_length"); val hash = row.utf8("payload_sha256")
        if (hash.length != 64 || hash.any { it !in "0123456789abcdef" } || length !in (if (allowEmpty) 0L else 1L)..limit.toLong() ||
            row.long("actual_length") != length || (row.column("payload_type") as? SqlValue.Text)?.value != "blob") corrupt()
        return length
    }
    private fun deleted(row: RowData) { when (row.long("deleted")) { 0L -> Unit; 1L -> missing(); else -> corrupt() } }
    private suspend fun blob(tx: DbTransaction, table: String, key: String, id: EntityId, length: Long, expectedHash: String, cancel: CancelToken): ByteArray {
        val row = query(tx, "SELECT payload FROM $table WHERE $key=:id AND deleted=0", cancel, 1, "id" to text(id.value)).singleOrNull() ?: corrupt()
        val bytes = (row.column("payload") as? SqlValue.Blob)?.value ?: corrupt()
        if (bytes.size.toLong() != length) corrupt()
        cancel.throwIfCancelled()
        return bytes.copyBytes().also { if (hash(it) != expectedHash) corrupt() }
    }
    private suspend fun touch(tx: DbTransaction, parent: Header, revision: Long, sequence: Long, now: UtcTimestamp, cancel: CancelToken) {
        execute(tx, "UPDATE ai_conversation SET revision=:revision,change_sequence=:sequence,modified_seconds=:seconds,modified_nanos=:nanos WHERE conversation_id=:id AND revision=:expected AND deleted=0", cancel, 1,
            "revision" to num(revision), "sequence" to num(sequence), *time(now), "id" to text(parent.id.value), "expected" to num(parent.revision.value))
    }
    /** Batches remain bounded even for arbitrarily long histories; no content BLOBs are selected. */
    private suspend fun scanParents(tx: DbTransaction, cancel: CancelToken, action: suspend (Header) -> Unit) {
        var after = ""
        while (true) {
            val rows = query(tx, "SELECT $HEADER FROM ai_conversation WHERE (typeof(deleted)!='integer' OR deleted<>1) AND conversation_id COLLATE BINARY>:after ORDER BY conversation_id COLLATE BINARY LIMIT 100", cancel, 100, "after" to text(after))
            if (rows.isEmpty()) return
            for (row in rows) { val parent = header(row); action(parent); after = parent.id.value }
        }
    }
    private suspend fun scanTurns(tx: DbTransaction, parent: EntityId, foreignEpoch: EntityId?, cancel: CancelToken, action: suspend (Meta) -> Unit) {
        var after = 0L
        val filter = if (foreignEpoch == null) "" else "AND status IN ('PENDING','STREAMING') AND (owner_epoch IS NULL OR owner_epoch<>:epoch)"
        val params = mutableListOf("id" to text(parent.value))
        if (foreignEpoch != null) params += "epoch" to text(foreignEpoch.value)
        while (true) {
            val rows = query(tx, "SELECT $TURN FROM ai_turn WHERE conversation_id=:id AND (typeof(deleted)!='integer' OR deleted<>1) AND (typeof(ordinal)!='integer' OR ordinal<=0 OR ordinal>:after) $filter ORDER BY ordinal LIMIT 100", cancel, 100, *params.toTypedArray(), "after" to num(after))
            if (rows.isEmpty()) return
            for (row in rows) { val meta = turnMetadata(row); if (meta.value.conversationId != parent) corrupt(); action(meta); after = meta.value.ordinal }
        }
    }
    private suspend fun exists(tx: DbTransaction, table: String, key: String, id: EntityId, cancel: CancelToken): Boolean =
        query(tx, "SELECT 1 AS present FROM $table WHERE $key=:id", cancel, 1, "id" to text(id.value)).isNotEmpty()
    private suspend fun epoch(tx: DbTransaction, cancel: CancelToken): Long = query(tx,
        "SELECT CASE WHEN COUNT(*)=0 THEN 0 WHEN MIN(CASE WHEN typeof(change_sequence)='integer' AND change_sequence>0 THEN 1 ELSE 0 END)=1 THEN MAX(change_sequence) ELSE NULL END AS epoch FROM ai_conversation", cancel, 1).single().long("epoch").also { if (it < 0) corrupt() }
    private suspend fun query(tx: DbTransaction, sql: String, cancel: CancelToken, maxRows: Int, vararg params: Pair<String, SqlValue>): SnapshotList<RowData> {
        cancel.throwIfCancelled(); return value(tx.query(request(sql, maxRows, *params), cancel))
    }
    private suspend fun execute(tx: DbTransaction, sql: String, cancel: CancelToken, count: Long, vararg params: Pair<String, SqlValue>) {
        cancel.throwIfCancelled()
        if (value(tx.execute(request(sql, 1, *params), cancel)).affectedRows != count) stale()
        cancel.throwIfCancelled()
    }
    private suspend fun <T> operation(cancel: CancelToken, validate: () -> Unit, action: suspend (DbTransaction) -> T): Outcome<T> {
        cancel.throwIfCancelled()
        try { validate() } catch (problem: Problem) { return problem.status }
        return when (val result = db.transaction({ tx -> try { Outcome.Success(action(tx)) } catch (problem: Problem) { problem.status } }, cancel)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> Outcome.Failure(DomainFailure(result.error.code, "History operation failed", SnapshotList.empty()))
            is Outcome.Unavailable -> Outcome.Unavailable(result.reason, "History unavailable")
            is Outcome.Unknown -> Outcome.Unknown("History outcome uncertain; reload", SnapshotList.empty())
        }
    }
    private class Problem(val status: Outcome<Nothing>) : RuntimeException()
    private fun <T> value(outcome: Outcome<T>): T = when (outcome) {
        is Outcome.Success -> outcome.value
        is Outcome.Failure -> throw Problem(Outcome.Failure(DomainFailure(outcome.error.code, "History operation failed", SnapshotList.empty())))
        is Outcome.Unavailable -> throw Problem(Outcome.Unavailable(outcome.reason, "History unavailable"))
        is Outcome.Unknown -> throw Problem(Outcome.Unknown("History outcome uncertain; reload", SnapshotList.empty()))
    }
    private fun invalid(): Nothing = reject(FailureCode.INVALID_INPUT)
    private fun stale(): Nothing = reject(FailureCode.STALE_REVISION)
    private fun corrupt(): Nothing = reject(FailureCode.CORRUPT_DATA)
    private fun reject(code: FailureCode): Nothing = throw Problem(Outcome.Failure(DomainFailure(code, "Invalid history operation", SnapshotList.empty())))
    private fun missing(): Nothing = throw Problem(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "History not found"))
    private fun expected(actual: Revision, expected: Revision) { if (actual != expected) stale() }
    private fun next(value: Long): Long { if (value == Long.MAX_VALUE) invalid(); return value + 1 }
    private fun positive(value: Long): Long = value.also { if (it <= 0) corrupt() }
    private fun id(value: EntityId) { inputText(value.value, ID_BYTES) }
    private fun deck(value: DeckId) { inputText(value.source.value, ID_BYTES); inputText(value.raw, ID_BYTES) }
    private fun inputText(value: String, max: Int) { if (value.isBlank() || value.any { it == '\u0000' }) invalid(); utf8(value, max) }
    private fun storedText(value: String, max: Int): String { try { inputText(value, max) } catch (_: Problem) { corrupt() }; return value }
    private fun storedId(value: String): EntityId = EntityId(storedText(value, ID_BYTES))
    private fun utf8(value: String, max: Int): ByteArray {
        if (value.length > max) invalid()
        return try {
            val buffer = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
            if (buffer.remaining() > max) invalid()
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        } catch (_: CharacterCodingException) { invalid() }
    }
    private fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) { corrupt() }
    private fun timestamp(row: RowData, prefix: String): UtcTimestamp {
        val nanos = row.long("${prefix}_nanos"); if (nanos !in 0..999_999_999L) corrupt()
        return UtcTimestamp(row.long("${prefix}_seconds"), nanos.toInt())
    }
    private fun RowData.column(name: String): SqlValue = columns.singleOrNull { it.name == name }?.value ?: corrupt()
    private fun RowData.long(name: String): Long = (column(name) as? SqlValue.Integer)?.value ?: corrupt()
    private fun RowData.utf8(name: String): String = decode((column(name) as? SqlValue.Blob)?.value?.copyBytes() ?: corrupt())
    private fun canonicalLong(value: String, positive: Boolean): Long = value.toLongOrNull()?.also { if (it < (if (positive) 1 else 0) || it.toString() != value) invalid() } ?: invalid()
    private data class Cursor(val kind: Int, val scope: List<String>, val epoch: Long, val last: String) {
        fun encode(): String {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                out.writeByte(1); out.writeByte(kind); out.writeLong(epoch); out.writeByte(scope.size)
                for (text in scope + last) { val raw = text.toByteArray(Charsets.UTF_8); out.writeInt(raw.size); out.write(raw) }
            }
            return "ah1:" + BASE64.encode(bytes.toByteArray())
        }
    }
    private fun cursor(value: String): Cursor {
        if (value.length > PageRequest.MAX_CURSOR_BYTES || !value.startsWith("ah1:")) invalid()
        return try {
            val bytes = BASE64.decode(value.substring(4)); val input = DataInputStream(ByteArrayInputStream(bytes))
            if (input.readUnsignedByte() != 1) invalid()
            val kind = input.readUnsignedByte(); val epoch = input.readLong(); val count = input.readUnsignedByte()
            if (kind !in 1..2 || epoch < 0 || count != (if (kind == 1) 2 else 1)) invalid()
            val strings = (0..count).map {
                val length = input.readInt(); if (length !in 1..ID_BYTES || length > input.available()) invalid()
                val raw = ByteArray(length); input.readFully(raw)
                val text = try { decode(raw) } catch (_: Problem) { invalid() }
                inputText(text, ID_BYTES); text
            }
            if (input.available() != 0) invalid()
            Cursor(kind, strings.take(count), epoch, strings.last()).also {
                if (it.encode() != value) invalid()
                if (kind == 2) canonicalLong(it.last, true)
            }
        } catch (_: java.io.IOException) { invalid() } catch (_: IllegalArgumentException) { invalid() }
    }
    private companion object {
        const val ID_BYTES = 256
        val BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        fun text(value: String): SqlValue = SqlValue.Text(value)
        fun num(value: Long): SqlValue = SqlValue.Integer(value)
        fun nullable(value: String?): SqlValue = value?.let(::text) ?: SqlValue.Null
        fun terminal(status: TurnStatus): Boolean = status != TurnStatus.PENDING && status != TurnStatus.STREAMING
        fun unfinished(): Knowledge.Unknown = Knowledge.Unknown(AiHistoryLimits.UNFINISHED_REASON, SnapshotList.empty())
        fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val EMPTY_HASH = hash(byteArrayOf())
        fun snapshot(epoch: Long): SnapshotReference = SnapshotReference(ProviderId("local-ai-history"), null, Revision(epoch), null)
        fun time(now: UtcTimestamp): Array<Pair<String, SqlValue>> = arrayOf("seconds" to num(now.epochSeconds), "nanos" to num(now.nanoAdjustment.toLong()))
        fun finished(value: Knowledge<UtcTimestamp>): Array<Pair<String, SqlValue>> = arrayOf(
            "finished" to ((value as? Knowledge.Known)?.value?.epochSeconds?.let(::num) ?: SqlValue.Null),
            "finishedNano" to ((value as? Knowledge.Known)?.value?.nanoAdjustment?.toLong()?.let(::num) ?: SqlValue.Null))
        fun payload(bytes: ByteArray): Array<Pair<String, SqlValue>> = arrayOf("length" to num(bytes.size.toLong()), "hash" to text(hash(bytes)), "payload" to SqlValue.Blob(SnapshotBytes(bytes)))
        fun request(sql: String, maxRows: Int, vararg params: Pair<String, SqlValue>): SqlRequest = SqlRequest(sql, SnapshotList.copyOf(params.map { SqlParameter(it.first, it.second) }), maxRows)
        fun bounded(column: String, max: Int): String = "CASE WHEN typeof($column)='text' AND length(CAST($column AS BLOB))<=$max THEN CAST($column AS BLOB) ELSE NULL END AS $column"
        fun integer(column: String): String = "CASE WHEN typeof($column)='integer' THEN $column ELSE NULL END AS $column"
        val PAYLOAD = listOf(integer("payload_version"), integer("payload_length"), bounded("payload_sha256", 64), "typeof(payload) AS payload_type", "CASE WHEN typeof(payload)='blob' THEN length(payload) WHEN payload IS NULL THEN 0 ELSE NULL END AS actual_length")
        val HEADER = (listOf(bounded("conversation_id", ID_BYTES), bounded("deck_source", ID_BYTES), bounded("deck_raw", ID_BYTES), bounded("mode", 8)) +
            listOf("revision", "change_sequence", "deleted", "created_seconds", "created_nanos", "modified_seconds", "modified_nanos").map(::integer) + PAYLOAD).joinToString(",")
        // Invalid nullable storage types must remain distinguishable from canonical SQL NULL.
        val OWNER = "CASE WHEN owner_epoch IS NULL THEN NULL WHEN typeof(owner_epoch)='text' AND length(CAST(owner_epoch AS BLOB))<=$ID_BYTES THEN CAST(owner_epoch AS BLOB) ELSE 0 END AS owner_epoch"
        fun nullableInteger(column: String): String = "CASE WHEN $column IS NULL THEN NULL WHEN typeof($column)='integer' THEN $column ELSE '' END AS $column"
        val TURN = (listOf(bounded("turn_id", ID_BYTES), bounded("conversation_id", ID_BYTES), bounded("source", 20), OWNER, bounded("status", 12)) +
            listOf("revision", "ordinal", "deleted", "started_seconds", "started_nanos").map(::integer) + listOf(nullableInteger("finished_seconds"), nullableInteger("finished_nanos")) + PAYLOAD).joinToString(",")
    }
}
