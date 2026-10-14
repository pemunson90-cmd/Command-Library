package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import kotlin.io.encoding.Base64
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/** Checked, transactional reusable profiles. No game/history rows are changed by this repository. */
public class SqlPlayerProfileRepository(
    private val db: Database,
    private val codec: LifePayloadCodecPort,
    private val clock: Clock,
    private val ids: IdSource,
) : PlayerProfileRepository {
    public override suspend fun create(value: PlayerProfileDraft, cancel: CancelToken): Outcome<PlayerProfileSnapshot> =
        operation(cancel) { tx ->
            val raw = try { ids.nextId() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                reject(FailureCode.INTERNAL, "Profile ID source failed")
            }
            checkedId(raw, FailureCode.INTERNAL)
            val payload = success(codec.encodeProfile(value))
            checkPayload(payload, FailureCode.INVALID_INPUT)
            val now = clock.utcNow()
            val row = PlayerProfileSnapshot(EntityId(raw), Revision(0), value)
            exactlyOne(success(tx.execute(request("""INSERT INTO life_profile
                (profile_id,revision,name,name_folded,payload,updated_seconds,updated_nano)
                VALUES (:id,0,:name,:folded,:payload,:seconds,:nano)""",
                "id" to text(raw), "name" to text(value.name), "folded" to text(fold(value.name)),
                "payload" to SqlValue.Blob(payload), "seconds" to number(now.epochSeconds), "nano" to number(now.nanoAdjustment.toLong())), cancel)))
            row
        }

    public override suspend fun read(id: EntityId, cancel: CancelToken): Outcome<PlayerProfileSnapshot> = operation(cancel) { tx ->
        checkedId(id.value, FailureCode.INVALID_INPUT)
        load(tx, metadata(tx, id, cancel), cancel)
    }

    public override suspend fun update(id: EntityId, value: PlayerProfileDraft, expected: Revision, cancel: CancelToken): Outcome<PlayerProfileSnapshot> =
        operation(cancel) { tx ->
            checkedId(id.value, FailureCode.INVALID_INPUT)
            val old = load(tx, metadata(tx, id, cancel), cancel)
            expected(old, expected)
            if (expected.value == Long.MAX_VALUE) reject(FailureCode.INVALID_INPUT, "Profile revision is exhausted")
            val revision = expected.next()
            val payload = success(codec.encodeProfile(value))
            checkPayload(payload, FailureCode.INVALID_INPUT)
            val now = clock.utcNow()
            exactlyOne(success(tx.execute(request("""UPDATE life_profile SET revision=:next,name=:name,name_folded=:folded,
                payload=:payload,updated_seconds=:seconds,updated_nano=:nano WHERE profile_id=:id AND revision=:expected""",
                "next" to number(revision.value), "name" to text(value.name), "folded" to text(fold(value.name)),
                "payload" to SqlValue.Blob(payload), "seconds" to number(now.epochSeconds), "nano" to number(now.nanoAdjustment.toLong()),
                "id" to text(id.value), "expected" to number(expected.value)), cancel)))
            PlayerProfileSnapshot(id, revision, value)
        }

    public override suspend fun delete(id: EntityId, expected: Revision, cancel: CancelToken): Outcome<Unit> = operation(cancel) { tx ->
        checkedId(id.value, FailureCode.INVALID_INPUT)
        val old = load(tx, metadata(tx, id, cancel), cancel)
        expected(old, expected)
        exactlyOne(success(tx.execute(request("DELETE FROM life_profile WHERE profile_id=:id AND revision=:expected",
            "id" to text(id.value), "expected" to number(expected.value)), cancel)))
    }

    public override suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<PlayerProfileSnapshot>> = operation(cancel) { tx ->
        val after = page.cursor?.let(::decodeCursor)
        val predicate = if (after == null) "" else "WHERE life_profile.profile_id COLLATE BINARY > :after"
        val parameters = mutableListOf("limit" to number(page.limit.toLong() + 1))
        if (after != null) parameters += "after" to text(after)
        val rows = success(tx.query(request("SELECT $METADATA FROM life_profile $predicate ORDER BY life_profile.profile_id COLLATE BINARY LIMIT :limit",
            *parameters.toTypedArray(), maxRows = page.limit + 1), cancel)).map(::checkedMetadata)
        val selected = mutableListOf<Metadata>()
        var bytes = 0L
        for (row in rows) {
            if (selected.size == page.limit || bytes + row.length > PAGE_BYTES) break
            selected += row
            bytes += row.length
        }
        val items = selected.map { load(tx, it, cancel) }
        val cursor = if (selected.size < rows.size) encodeCursor(selected.last().id.value) else null
        Page(SnapshotList.copyOf(items), cursor, SnapshotReference(ProviderId("local-life-profiles"), null, null, null))
    }

    private data class Metadata(val id: EntityId, val revision: Revision, val name: String, val folded: String, val length: Long)

    private suspend fun metadata(tx: DbTransaction, id: EntityId, cancel: CancelToken): Metadata {
        val rows = success(tx.query(request("SELECT $METADATA FROM life_profile WHERE profile_id=:id", "id" to text(id.value), maxRows = 1), cancel))
        if (rows.isEmpty()) throw Problem(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Player profile not found"))
        corruptUnless(rows.size == 1, "Duplicate profile identity")
        return checkedMetadata(rows.single()).also { corruptUnless(it.id == id, "Profile identity mismatch") }
    }

    private fun checkedMetadata(row: RowData): Metadata {
        val raw = row.utf8("profile_id")
        checkedId(raw, FailureCode.CORRUPT_DATA)
        val revision = row.long("revision")
        corruptUnless(revision >= 0, "Negative profile revision")
        row.long("updated_seconds") // Require a complete, typed timestamp pair even though the public port omits it.
        corruptUnless(row.long("updated_nano") in 0..999_999_999, "Invalid profile timestamp")
        val length = row.long("payload_length")
        corruptUnless(row.string("payload_type") == "blob" && length in 1..PROFILE_BYTES, "Invalid profile payload length/type")
        val name = row.utf8("name")
        val folded = row.utf8("name_folded")
        corruptUnless(folded == fold(name), "Profile folded-name mismatch")
        return Metadata(EntityId(raw), Revision(revision), name, folded, length)
    }

    private suspend fun load(tx: DbTransaction, meta: Metadata, cancel: CancelToken): PlayerProfileSnapshot {
        val rows = success(tx.query(request("SELECT payload FROM life_profile WHERE profile_id=:id", "id" to text(meta.id.value), maxRows = 1), cancel))
        corruptUnless(rows.size == 1, "Profile payload missing")
        val payload = (rows.single().column("payload") as? SqlValue.Blob)?.value
            ?: reject(FailureCode.CORRUPT_DATA, "Invalid profile blob")
        corruptUnless(payload.size.toLong() == meta.length, "Profile payload length mismatch")
        val draft = success(codec.decodeProfile(payload))
        corruptUnless(draft.name == meta.name && fold(draft.name) == meta.folded, "Profile payload/projection mismatch")
        return PlayerProfileSnapshot(meta.id, meta.revision, draft)
    }

    private fun expected(old: PlayerProfileSnapshot, expected: Revision) {
        if (old.revision != expected) reject(FailureCode.STALE_REVISION, "Player profile changed")
    }
    private fun exactlyOne(result: DbWriteResult) {
        if (result.affectedRows != 1L) reject(FailureCode.PERSISTENCE, "Profile write did not affect exactly one row")
    }
    private fun checkPayload(payload: SnapshotBytes, code: FailureCode) {
        if (payload.size.toLong() !in 1..PROFILE_BYTES) reject(code, "Profile payload exceeds byte budget")
    }
    private fun checkedId(id: String, code: FailureCode) {
        if (id.length > ID_BYTES || id.isBlank() || strictBytes(id, code).size > ID_BYTES) reject(code, "Invalid profile identity")
    }
    private fun strictBytes(value: String, code: FailureCode): ByteArray = try {
        val buffer = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value))
        ByteArray(buffer.remaining()).also { buffer.get(it) }
    } catch (e: CharacterCodingException) { reject(code, "Invalid UTF8 profile text") }
    private fun encodeCursor(id: String): String = "lp1:" + CURSOR_BASE64.encode(strictBytes(id, FailureCode.CORRUPT_DATA))
    private fun decodeCursor(cursor: String): String {
        if (!cursor.startsWith("lp1:") || cursor.length > PageRequest.MAX_CURSOR_BYTES) reject(FailureCode.INVALID_INPUT, "Invalid profile cursor")
        val bytes = try { CURSOR_BASE64.decode(cursor.substring(4)) }
        catch (e: IllegalArgumentException) { reject(FailureCode.INVALID_INPUT, "Invalid profile cursor") }
        val id = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) { reject(FailureCode.INVALID_INPUT, "Invalid profile cursor UTF8") }
        checkedId(id, FailureCode.INVALID_INPUT)
        if (encodeCursor(id) != cursor) reject(FailureCode.INVALID_INPUT, "Noncanonical profile cursor")
        return id
    }
    private fun fold(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFC).lowercase(Locale.ROOT)
    private fun RowData.column(name: String): SqlValue = columns.singleOrNull { it.name == name }?.value
        ?: reject(FailureCode.CORRUPT_DATA, "Missing profile column")
    private fun RowData.string(name: String): String = (column(name) as? SqlValue.Text)?.value
        ?: reject(FailureCode.CORRUPT_DATA, "Invalid profile text column")
    private fun RowData.utf8(name: String): String {
        val bytes = (column(name) as? SqlValue.Blob)?.value
            ?: reject(FailureCode.CORRUPT_DATA, "Invalid profile text bytes")
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.copyBytes())).toString()
        } catch (e: CharacterCodingException) { reject(FailureCode.CORRUPT_DATA, "Invalid stored profile UTF8") }
    }
    private fun RowData.long(name: String): Long = (column(name) as? SqlValue.Integer)?.value
        ?: reject(FailureCode.CORRUPT_DATA, "Invalid profile integer column")

    private class Problem(val status: Outcome<Nothing>) : RuntimeException()
    private fun reject(code: FailureCode, message: String): Nothing = throw Problem(Outcome.Failure(DomainFailure(code, message, SnapshotList.empty())))
    private fun corruptUnless(valid: Boolean, message: String) { if (!valid) reject(FailureCode.CORRUPT_DATA, message) }
    private fun <T> success(outcome: Outcome<T>): T = when (outcome) {
        is Outcome.Success -> outcome.value
        is Outcome.Failure -> throw Problem(outcome)
        is Outcome.Unavailable -> throw Problem(outcome)
        is Outcome.Unknown -> throw Problem(outcome)
    }
    private suspend fun <T> operation(cancel: CancelToken, action: suspend (DbTransaction) -> T): Outcome<T> {
        cancel.throwIfCancelled()
        return when (val result = db.transaction({ tx ->
            try { Outcome.Success(action(tx)) } catch (problem: Problem) { problem.status }
        }, cancel)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> result
            is Outcome.Unavailable -> result
            is Outcome.Unknown -> result
        }
    }
    private fun request(sql: String, vararg parameters: Pair<String, SqlValue>, maxRows: Int = 1): SqlRequest =
        SqlRequest(sql, SnapshotList.copyOf(parameters.map { SqlParameter(it.first, it.second) }), maxRows)
    private fun text(value: String): SqlValue = SqlValue.Text(value)
    private fun number(value: Long): SqlValue = SqlValue.Integer(value)

    private companion object {
        const val PROFILE_BYTES = 4L * 1024 * 1024
        const val PAGE_BYTES = 8L * 1024 * 1024
        const val ID_BYTES = 256
        val CURSOR_BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        // Bound variable metadata in SQL, before the driver can allocate a hostile stored value.
        // Folded NFC+ROOT text may expand; four times the codec string bound is conservative.
        val METADATA = listOf(
            boundedText("profile_id", ID_BYTES), boundedText("name", 65_536), boundedText("name_folded", 262_144),
            typedInteger("revision"), typedInteger("updated_seconds"), typedInteger("updated_nano"),
            "CASE WHEN typeof(payload)='blob' THEN length(payload) ELSE NULL END AS payload_length",
            "typeof(payload) AS payload_type",
        ).joinToString(",")
        fun boundedText(column: String, bytes: Int): String =
            "CASE WHEN typeof($column)='text' AND length(CAST($column AS BLOB))<=$bytes THEN CAST($column AS BLOB) ELSE NULL END AS $column"
        fun typedInteger(column: String): String = "CASE WHEN typeof($column)='integer' THEN $column ELSE NULL END AS $column"
    }
}
