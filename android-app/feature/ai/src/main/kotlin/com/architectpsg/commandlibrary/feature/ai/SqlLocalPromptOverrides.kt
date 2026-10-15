package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Raw strict UTF8, retained reset rows and per-mode CAS. Cancellation may require explicit reload. */
public class SqlLocalPromptOverrides(private val db: Database, private val clock: Clock) : LocalPromptOverrides {
    public override suspend fun load(mode: OfflineLabMode, cancel: CancelToken): Outcome<StoredPromptOverride?> =
        operation(cancel) { tx -> read(tx, mode, cancel) }

    public override suspend fun save(mode: OfflineLabMode, expected: Revision?, body: String, cancel: CancelToken): Outcome<StoredPromptOverride> {
        // The engine's exact grammar and strict Unicode bound run before opening a transaction.
        cancel.throwIfCancelled()
        try { validate(body, cancel) } catch (_: OfflineInputException) {
            return failure(FailureCode.INVALID_INPUT, "Invalid prompt override")
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        return operation(cancel) { tx ->
            val old = read(tx, mode, cancel)
            expected(old, expected)
            if (old != null && old.shippedVersion == mode.shippedVersion && old.body == body) old
            else {
                val stored = StoredPromptOverride(mode, mode.shippedVersion, next(old), body, clock.utcNow())
                write(tx, stored, expected, bytes, cancel)
                stored
            }
        }
    }

    public override suspend fun reset(mode: OfflineLabMode, expected: Revision?, cancel: CancelToken): Outcome<StoredPromptOverride?> =
        operation(cancel) { tx ->
            val old = read(tx, mode, cancel)
            expected(old, expected)
            if (old == null || (old.body == null && old.shippedVersion == mode.shippedVersion)) old
            else {
                val stored = StoredPromptOverride(mode, mode.shippedVersion, next(old), null, clock.utcNow())
                write(tx, stored, expected, byteArrayOf(), cancel)
                stored
            }
        }

    private suspend fun read(tx: DbTransaction, mode: OfflineLabMode, cancel: CancelToken): StoredPromptOverride? {
        val rows = query(tx, "SELECT $METADATA FROM prompt_override WHERE mode=:mode", mode, cancel)
        if (rows.isEmpty()) return null
        if (rows.size != 1) corrupt()
        val row = rows.single()
        if (row.utf8("mode") != tag(mode)) corrupt()
        val format = row.long("format_version")
        if (format > 1) throw Problem(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Newer prompt override format"))
        if (format != 1L) corrupt()
        val version = row.long("template_version")
        val revision = row.long("revision")
        val nanos = row.long("modified_nanos")
        val seconds = row.long("modified_seconds")
        if (version !in 1..Int.MAX_VALUE.toLong() || revision <= 0 || nanos !in 0..999_999_999L) corrupt()
        val length = row.long("body_length")
        val expectedHash = row.utf8("body_sha256")
        if (expectedHash.length != 64 || expectedHash.any { it !in "0123456789abcdef" }) corrupt()
        val actualLength = row.long("actual_length")
        val type = (row.column("body_type") as? SqlValue.Text)?.value ?: corrupt()
        if (length !in 0..OfflinePromptText.USER_BYTES.toLong() || actualLength != length) corrupt()
        val body = when (type) {
            "null" -> { if (length != 0L || expectedHash != EMPTY_HASH) corrupt(); null }
            "blob" -> {
                val bodies = query(tx, "SELECT body FROM prompt_override WHERE mode=:mode", mode, cancel)
                if (bodies.size != 1) corrupt()
                val blob = (bodies.single().column("body") as? SqlValue.Blob)?.value ?: corrupt()
                if (blob.size.toLong() != length) corrupt()
                val bytes = blob.copyBytes()
                cancel.throwIfCancelled()
                if (hash(bytes) != expectedHash) corrupt()
                val decoded = decode(bytes)
                if (version == mode.shippedVersion.toLong()) {
                    try { validate(decoded, cancel) } catch (_: OfflineInputException) { corrupt() }
                }
                decoded
            }
            else -> corrupt()
        }
        cancel.throwIfCancelled()
        return StoredPromptOverride(mode, version.toInt(), Revision(revision), body, UtcTimestamp(seconds, nanos.toInt()))
    }

    private suspend fun write(tx: DbTransaction, stored: StoredPromptOverride, expected: Revision?, bytes: ByteArray, cancel: CancelToken) {
        cancel.throwIfCancelled()
        val params = mutableListOf("mode" to text(tag(stored.mode)), "version" to number(stored.shippedVersion.toLong()),
            "revision" to number(stored.revision.value), "seconds" to number(stored.modifiedAt.epochSeconds),
            "nanos" to number(stored.modifiedAt.nanoAdjustment.toLong()), "length" to number(bytes.size.toLong()),
            "hash" to text(hash(bytes)), "body" to if (stored.body == null) SqlValue.Null else SqlValue.Blob(SnapshotBytes(bytes)))
        val sql = if (expected == null) """INSERT INTO prompt_override
            (mode,format_version,template_version,revision,modified_seconds,modified_nanos,body_length,body_sha256,body)
            VALUES (:mode,1,:version,:revision,:seconds,:nanos,:length,:hash,:body)"""
        else {
            params += "expected" to number(expected.value)
            """UPDATE prompt_override SET format_version=1,template_version=:version,revision=:revision,
                modified_seconds=:seconds,modified_nanos=:nanos,body_length=:length,body_sha256=:hash,body=:body
                WHERE mode=:mode AND revision=:expected"""
        }
        val result = value(tx.execute(request(sql, *params.toTypedArray()), cancel))
        if (result.affectedRows != 1L) reject(FailureCode.STALE_REVISION, "Prompt override changed; reload")
        cancel.throwIfCancelled()
    }

    private fun expected(old: StoredPromptOverride?, expected: Revision?) {
        if (old?.revision != expected) reject(FailureCode.STALE_REVISION, "Prompt override changed; reload")
    }
    private fun next(old: StoredPromptOverride?): Revision {
        val revision = old?.revision?.value ?: 0L
        if (revision == Long.MAX_VALUE) reject(FailureCode.INVALID_INPUT, "Prompt override revision exhausted")
        return Revision(revision + 1)
    }
    private fun validate(body: String, cancel: CancelToken) {
        OfflinePromptText.utf8Size(body, OfflinePromptText.USER_BYTES, cancel)
        OfflinePromptText.bind(body, mapOf("deck" to "", "constraints" to "", "request" to ""), cancel)
    }
    private suspend fun query(tx: DbTransaction, sql: String, mode: OfflineLabMode, cancel: CancelToken): SnapshotList<RowData> {
        cancel.throwIfCancelled()
        return value(tx.query(request(sql, "mode" to text(tag(mode))), cancel))
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
    private fun RowData.column(name: String): SqlValue = columns.singleOrNull { it.name == name }?.value ?: corrupt()
    private fun RowData.long(name: String): Long = (column(name) as? SqlValue.Integer)?.value ?: corrupt()
    private fun RowData.utf8(name: String): String = decode((column(name) as? SqlValue.Blob)?.value?.copyBytes() ?: corrupt())
    private fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) { corrupt() }
    private class Problem(val status: Outcome<Nothing>) : RuntimeException()
    private fun <T> value(result: Outcome<T>): T = when (result) {
        is Outcome.Success -> result.value
        is Outcome.Failure -> throw Problem(result)
        is Outcome.Unavailable -> throw Problem(result)
        is Outcome.Unknown -> throw Problem(result)
    }
    private fun failure(code: FailureCode, message: String): Outcome.Failure =
        Outcome.Failure(DomainFailure(code, message, SnapshotList.empty()))
    private fun reject(code: FailureCode, message: String): Nothing = throw Problem(failure(code, message))
    private fun corrupt(): Nothing = reject(FailureCode.CORRUPT_DATA, "Invalid stored prompt override")
    private fun request(sql: String, vararg params: Pair<String, SqlValue>): SqlRequest =
        SqlRequest(sql, SnapshotList.copyOf(params.map { SqlParameter(it.first, it.second) }), 1)
    private fun text(value: String): SqlValue = SqlValue.Text(value)
    private fun number(value: Long): SqlValue = SqlValue.Integer(value)
    private companion object {
        fun tag(mode: OfflineLabMode): String = when (mode) {
            OfflineLabMode.ROAST -> "ROAST"
            OfflineLabMode.CHAT -> "CHAT"
            OfflineLabMode.UPGRADE -> "UPGRADE"
            OfflineLabMode.SURPRISE -> "SURPRISE"
        }
        fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val EMPTY_HASH = hash(byteArrayOf())
        fun boundedText(column: String, max: Int): String =
            "CASE WHEN typeof($column)='text' AND length(CAST($column AS BLOB))<=$max THEN CAST($column AS BLOB) ELSE NULL END AS $column"
        fun integer(column: String): String = "CASE WHEN typeof($column)='integer' THEN $column ELSE NULL END AS $column"
        val METADATA = (listOf(boundedText("mode", 8), boundedText("body_sha256", 64)) +
            listOf("format_version", "template_version", "revision", "modified_seconds", "modified_nanos", "body_length").map(::integer) +
            listOf("typeof(body) AS body_type", "CASE WHEN typeof(body)='blob' THEN length(body) WHEN body IS NULL THEN 0 ELSE NULL END AS actual_length")).joinToString(",")
    }
}
