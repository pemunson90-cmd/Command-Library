package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.decks.PERSONAL_TAG_HEADER_BYTES
import com.architectpsg.commandlibrary.feature.decks.validatePersonalTags
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** One checksummed vocabulary CAS; never changes a deck or replays an uncertain write. */
public class SqlLocalPersonalTags(private val db: Database, private val clock: Clock) : LocalPersonalTags {
    public override suspend fun load(cancel: CancelToken): Outcome<PersonalTagPool> = operation(cancel) { tx -> read(tx, cancel) }

    public override suspend fun replace(expected: Revision, tags: SnapshotList<PersonalTag>, cancel: CancelToken): Outcome<PersonalTagPool> {
        cancel.throwIfCancelled()
        try { validatePersonalTags(tags, cancel) } catch (_: IllegalArgumentException) {
            return failure(FailureCode.INVALID_INPUT)
        }
        return operation(cancel) { tx ->
            val old = read(tx, cancel)
            if (old.revision != expected) reject(FailureCode.STALE_REVISION)
            val ids = tags.mapTo(HashSet()) { it.id }
            if (old.tags.any { it.id !in ids }) reject(FailureCode.INVALID_INPUT)
            if (old.tags == tags) return@operation old
            if (old.revision.value == Long.MAX_VALUE) reject(FailureCode.INVALID_INPUT)
            val now = try { clock.utcNow() } catch (stopped: CancellationException) { throw stopped }
            catch (_: Exception) { reject(FailureCode.INTERNAL) }
            old.modifiedAt?.let { previous ->
                if (now.epochSeconds < previous.epochSeconds ||
                    now.epochSeconds == previous.epochSeconds && now.nanoAdjustment < previous.nanoAdjustment) reject(FailureCode.INTERNAL)
            }
            val stored = PersonalTagPool(Revision(old.revision.value + 1), tags, now)
            val payload = PersonalTagPoolWire.encode(stored, cancel)
            val parameters = arrayOf("revision" to number(stored.revision.value), "seconds" to number(now.epochSeconds),
                "nanos" to number(now.nanoAdjustment.toLong()), "length" to number(payload.size.toLong()),
                "hash" to SqlValue.Text(PersonalTagPoolWire.hash(payload)), "payload" to SqlValue.Blob(SnapshotBytes(payload)))
            val sql = if (old.revision.value == 0L) """INSERT INTO personal_tag_pool
                (singleton_id,revision,modified_seconds,modified_nanos,payload_version,payload_length,payload_sha256,payload)
                VALUES (1,:revision,:seconds,:nanos,1,:length,:hash,:payload)"""
            else """UPDATE personal_tag_pool SET revision=:revision,modified_seconds=:seconds,modified_nanos=:nanos,
                payload_version=1,payload_length=:length,payload_sha256=:hash,payload=:payload
                WHERE singleton_id=1 AND revision=:expected"""
            val args = if (old.revision.value == 0L) parameters else parameters + ("expected" to number(expected.value))
            cancel.throwIfCancelled()
            val written = value(tx.execute(request(sql, *args), cancel))
            if (written.affectedRows != 1L) reject(FailureCode.STALE_REVISION)
            cancel.throwIfCancelled()
            stored
        }
    }

    private suspend fun read(tx: DbTransaction, cancel: CancelToken): PersonalTagPool {
        // No WHERE filter: unexpected singleton identities and duplicate rows must remain visible.
        val rows = value(tx.query(request("SELECT $METADATA FROM personal_tag_pool LIMIT 2", maxRows = 2), cancel))
        if (rows.isEmpty()) return PersonalTagPool(Revision(0), SnapshotList.empty(), null)
        if (rows.size != 1) corrupt()
        val row = rows.single()
        if (row.long("singleton_id") != 1L) corrupt()
        val revision = row.long("revision")
        val seconds = row.long("modified_seconds")
        val nanos = row.long("modified_nanos")
        if (revision <= 0 || nanos !in 0..999_999_999L) corrupt()
        val version = row.long("payload_version")
        if (version > PersonalTagPoolWire.VERSION) throw TagPoolProblem(Outcome.Unavailable(
            UnavailableReason.UNSUPPORTED, "Personal tag pool requires a newer format"))
        if (version != PersonalTagPoolWire.VERSION.toLong()) corrupt()
        val length = row.long("payload_length")
        val actualLength = row.long("actual_length")
        val hashBytes = (row.column("payload_sha256") as? SqlValue.Blob)?.value ?: corrupt()
        if (hashBytes.size != 64) corrupt()
        val hash = hashBytes.copyBytes()
        if (hash.any { it.toInt().toChar() !in "0123456789abcdef" }) corrupt()
        if (row.column("payload_type") != SqlValue.Text("blob") ||
            length !in PERSONAL_TAG_HEADER_BYTES.toLong()..PersonalTagLimits.MAX_PAYLOAD_BYTES.toLong() || actualLength != length) corrupt()
        val bodies = value(tx.query(request("SELECT payload FROM personal_tag_pool WHERE singleton_id=1"), cancel))
        if (bodies.size != 1) corrupt()
        val payload = (bodies.single().column("payload") as? SqlValue.Blob)?.value ?: corrupt()
        if (payload.size.toLong() != length) corrupt()
        cancel.throwIfCancelled()
        val bytes = payload.copyBytes()
        if (PersonalTagPoolWire.hash(bytes) != String(hash, Charsets.US_ASCII)) corrupt()
        val decoded = try { PersonalTagPoolWire.decode(bytes, cancel) }
        catch (_: FuturePersonalTagFormat) {
            throw TagPoolProblem(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Personal tag pool requires a newer format"))
        } catch (_: IllegalArgumentException) { corrupt() }
        catch (_: IOException) { corrupt() }
        if (decoded.revision.value != revision || decoded.modifiedAt != UtcTimestamp(seconds, nanos.toInt())) corrupt()
        cancel.throwIfCancelled()
        return decoded
    }

    private suspend fun <T> operation(cancel: CancelToken, action: suspend (DbTransaction) -> T): Outcome<T> {
        cancel.throwIfCancelled()
        val result = try {
            db.transaction({ tx ->
                try { Outcome.Success(action(tx)) }
                catch (stopped: CancellationException) { throw stopped }
                catch (problem: TagPoolProblem) { problem.outcome }
            }, cancel)
        } catch (stopped: CancellationException) { throw stopped }
        catch (_: Exception) { return failure(FailureCode.PERSISTENCE) }
        return when (result) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> failure(result.error.code)
            is Outcome.Unavailable -> Outcome.Unavailable(result.reason, "Personal tag storage is unavailable")
            is Outcome.Unknown -> Outcome.Unknown("Personal tag storage is uncertain; reload before editing", SnapshotList.empty())
        }
    }

    private fun RowData.column(name: String): SqlValue = columns.singleOrNull { it.name == name }?.value ?: corrupt()
    private fun RowData.long(name: String): Long = (column(name) as? SqlValue.Integer)?.value ?: corrupt()
    private fun <T> value(outcome: Outcome<T>): T = when (outcome) {
        is Outcome.Success -> outcome.value
        is Outcome.Failure -> throw TagPoolProblem(failure(outcome.error.code))
        is Outcome.Unavailable -> throw TagPoolProblem(Outcome.Unavailable(outcome.reason, "Personal tag storage is unavailable"))
        is Outcome.Unknown -> throw TagPoolProblem(Outcome.Unknown("Personal tag storage is uncertain; reload before editing", SnapshotList.empty()))
    }
    private fun reject(code: FailureCode): Nothing = throw TagPoolProblem(failure(code))
    private fun corrupt(): Nothing = reject(FailureCode.CORRUPT_DATA)
    private fun number(value: Long): SqlValue = SqlValue.Integer(value)
    private fun request(sql: String, vararg parameters: Pair<String, SqlValue>, maxRows: Int = 1): SqlRequest =
        SqlRequest(sql, SnapshotList.copyOf(parameters.map { SqlParameter(it.first, it.second) }), maxRows)
    private class TagPoolProblem(val outcome: Outcome<Nothing>) : RuntimeException()

    private companion object {
        fun failure(code: FailureCode): Outcome.Failure = Outcome.Failure(DomainFailure(code,
            when (code) {
                FailureCode.INVALID_INPUT -> "Invalid personal tag pool"
                FailureCode.STALE_REVISION -> "Personal tag pool changed; reload before editing"
                FailureCode.CORRUPT_DATA -> "Invalid stored personal tag pool"
                FailureCode.INTERNAL -> "Personal tag clock failed or moved backwards"
                else -> "Personal tag storage failed; reload before editing"
            }, SnapshotList.empty()))
        fun integer(name: String): String = "CASE WHEN typeof($name)='integer' THEN $name ELSE NULL END AS $name"
        val METADATA: String = (listOf("singleton_id", "revision", "modified_seconds", "modified_nanos", "payload_version", "payload_length").map(::integer) +
            listOf("CASE WHEN typeof(payload_sha256)='text' AND length(CAST(payload_sha256 AS BLOB))=64 THEN CAST(payload_sha256 AS BLOB) ELSE NULL END AS payload_sha256",
                "typeof(payload) AS payload_type", "CASE WHEN typeof(payload)='blob' THEN length(payload) ELSE NULL END AS actual_length")).joinToString(",")
    }
}
