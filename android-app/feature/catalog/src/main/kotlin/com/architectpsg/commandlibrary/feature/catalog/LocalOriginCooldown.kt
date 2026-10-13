package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/** Retained per-origin interval; each new engine replays its full duration after restart. */
internal class LocalOriginCooldown(private val database: Database, private val clock: Clock) {
    private data class Row(val revision: Long, val interval: Duration)
    private data class Anchor(val revision: Long, val started: Duration)

    private val mutex = Mutex()
    private val anchors = mutableMapOf<String, Anchor>()
    private val failedRecords = mutableSetOf<String>()

    suspend fun record(uri: ResourceUri, retryAfter: Knowledge<Duration>, cancel: CancelToken): Outcome<Unit> {
        check(cancel)
        val key = try { origin(uri) } catch (_: Exception) { return failure(FailureCode.INVALID_INPUT) }
        return locked(cancel) {
            if (key in failedRecords) return@locked failure(FailureCode.PERSISTENCE)
            val requested = maxOf(30.seconds, (retryAfter as? Knowledge.Known)?.value ?: Duration.ZERO)
            var enteredStorage = false
            try {
                check(cancel)
                enteredStorage = true
                val result = database.transaction({ tx ->
                    val old = read(key, cancel, tx).valueOrAbort(tx)
                    if (old?.revision == Long.MAX_VALUE) tx.abort(problem(FailureCode.PERSISTENCE))
                    val next = maxOf(old?.interval ?: Duration.ZERO, requested)
                    val (seconds, nano) = parts(next)
                    val revision = (old?.revision ?: 0L) + 1L
                    val write = if (old == null) tx.execute(sql("""INSERT INTO acquisition_origin_cooldown
                        (origin,revision,interval_seconds,interval_nano) VALUES (:origin,:revision,:interval_seconds,:interval_nano)""",
                        "origin" to SqlValue.Text(key), "revision" to SqlValue.Integer(revision),
                        "interval_seconds" to seconds, "interval_nano" to nano), cancel)
                    else tx.execute(sql("""UPDATE acquisition_origin_cooldown SET
                        revision=:new_revision,interval_seconds=:interval_seconds,interval_nano=:interval_nano
                        WHERE origin=:origin AND revision=:expected_revision""",
                        "origin" to SqlValue.Text(key), "new_revision" to SqlValue.Integer(revision),
                        "interval_seconds" to seconds, "interval_nano" to nano,
                        "expected_revision" to SqlValue.Integer(old.revision)), cancel)
                    if (write.valueOrAbort(tx).affectedRows != 1L) tx.abort(problem(FailureCode.PERSISTENCE))
                    Row(revision, next)
                }, cancel)
                when (result) {
                    is Outcome.Success -> {
                        val now = clock.monotonicNow()
                        if (!now.isFinite()) {
                            failedRecords += key
                            failure(FailureCode.CORRUPT_DATA)
                        } else {
                            anchors[key] = Anchor(result.value.revision, now)
                            Outcome.Success(Unit)
                        }
                    }
                    else -> { failedRecords += key; result.nonSuccess() }
                }
            } catch (cancelled: CancellationException) {
                if (enteredStorage) failedRecords += key
                throw cancelled
            } catch (_: Exception) {
                failedRecords += key
                failure(FailureCode.PERSISTENCE)
            }
        }
    }

    suspend fun remaining(uri: ResourceUri, cancel: CancelToken): Outcome<Duration> {
        check(cancel)
        val key = try { origin(uri) } catch (_: Exception) { return failure(FailureCode.INVALID_INPUT) }
        return locked(cancel) {
            if (key in failedRecords) return@locked Outcome.Success(Duration.INFINITE)
            var seen = key in anchors
            var failedClearRevision: Long? = null
            while (true) {
                check(cancel)
                val loaded = read(key, cancel)
                val row = when (loaded) {
                    is Outcome.Success -> loaded.value
                    else -> return@locked loaded.nonSuccess()
                }
                if (row == null) {
                    anchors.remove(key)
                    return@locked if (seen) failure(FailureCode.CORRUPT_DATA) else Outcome.Success(Duration.ZERO)
                }
                if (row.revision == failedClearRevision) return@locked failure(FailureCode.PERSISTENCE)
                seen = true
                if (row.interval == Duration.ZERO) {
                    anchors.remove(key)
                    return@locked Outcome.Success(Duration.ZERO)
                }
                if (row.interval == Duration.INFINITE) {
                    anchors.remove(key)
                    return@locked Outcome.Success(Duration.INFINITE)
                }
                val now = clock.monotonicNow()
                if (!now.isFinite()) return@locked failure(FailureCode.CORRUPT_DATA)
                val anchor = anchors[key]?.takeIf { it.revision == row.revision }
                    ?: Anchor(row.revision, now).also { anchors[key] = it }
                val elapsed = now - anchor.started
                if (!elapsed.isFinite() || elapsed < Duration.ZERO) return@locked failure(FailureCode.CORRUPT_DATA)
                val left = row.interval - elapsed
                if (left > Duration.ZERO) return@locked Outcome.Success(left)
                if (row.revision == Long.MAX_VALUE) return@locked failure(FailureCode.PERSISTENCE)
                // Keep a zero-interval tombstone; never reuse a revision after expiry.
                val cleared = database.transaction({ tx ->
                    tx.execute(sql("""UPDATE acquisition_origin_cooldown SET
                        revision=revision+1,interval_seconds=0,interval_nano=0
                        WHERE origin=:origin AND revision=:expected_revision""",
                        "origin" to SqlValue.Text(key),
                        "expected_revision" to SqlValue.Integer(row.revision)), cancel).valueOrAbort(tx).affectedRows
                }, cancel)
                when (cleared) {
                    is Outcome.Success -> {
                        if (cleared.value == 1L) {
                            anchors.remove(key)
                            return@locked Outcome.Success(Duration.ZERO)
                        }
                        if (cleared.value != 0L) return@locked failure(FailureCode.CORRUPT_DATA)
                        failedClearRevision = row.revision
                    }
                    else -> return@locked cleared.nonSuccess()
                }
                // A newer 429 won the race. Reload its revision and anchor the full interval.
            }
            @Suppress("UNREACHABLE_CODE")
            Outcome.Success(Duration.ZERO)
        }
    }

    private suspend fun read(key: String, cancel: CancelToken, tx: DbTransaction? = null): Outcome<Row?> {
        val request = sql("""SELECT revision,interval_seconds,interval_nano FROM acquisition_origin_cooldown
            WHERE origin=:origin""", "origin" to SqlValue.Text(key))
        val result = if (tx == null) database.query(request, cancel) else tx.query(request, cancel)
        if (result !is Outcome.Success) return result.nonSuccess()
        return try {
            if (result.value.size > 1) return failure(FailureCode.CORRUPT_DATA)
            val values = result.value.singleOrNull()?.columns?.associate { it.name to it.value }
                ?: return Outcome.Success(null)
            val revision = (values["revision"] as? SqlValue.Integer)?.value
            val seconds = values["interval_seconds"]
            val nano = values["interval_nano"]
            if (revision == null || revision < 1L) return failure(FailureCode.CORRUPT_DATA)
            val interval = when {
                seconds == SqlValue.Null && nano == SqlValue.Null -> Duration.INFINITE
                seconds is SqlValue.Integer && nano is SqlValue.Integer -> {
                    val s = seconds.value
                    val n = nano.value
                    if (n !in 0..999_999_999 || s < 0 || s in 1..29 || s == 0L && n != 0L)
                        return failure(FailureCode.CORRUPT_DATA)
                    if (s == 0L) Duration.ZERO else s.seconds + n.nanoseconds
                }
                else -> return failure(FailureCode.CORRUPT_DATA)
            }
            Outcome.Success(Row(revision, interval))
        } catch (_: Exception) { failure(FailureCode.CORRUPT_DATA) }
    }

    private fun parts(interval: Duration): Pair<SqlValue, SqlValue> {
        if (!interval.isFinite()) return SqlValue.Null to SqlValue.Null
        val seconds = interval.inWholeSeconds
        if (seconds == Long.MAX_VALUE) return SqlValue.Null to SqlValue.Null
        val nano = (interval - seconds.seconds).inWholeNanoseconds
        if (nano !in 0..999_999_999) return SqlValue.Null to SqlValue.Null
        return SqlValue.Integer(seconds) to SqlValue.Integer(nano)
    }

    private fun origin(uri: ResourceUri): String {
        val parsed = URI(uri.value)
        require(!parsed.isOpaque && parsed.scheme.equals("https", true) && !parsed.host.isNullOrBlank() &&
            parsed.rawUserInfo == null && parsed.rawFragment == null &&
            (parsed.port == -1 || parsed.port in 1..65535))
        val key = "https://${parsed.host.lowercase(Locale.ROOT)}:${if (parsed.port == -1) 443 else parsed.port}"
        require(key.toByteArray(Charsets.UTF_8).size <= 512)
        return key
    }

    private suspend fun <T> locked(cancel: CancelToken, action: suspend () -> Outcome<T>): Outcome<T> {
        while (true) {
            check(cancel)
            if (mutex.tryLock()) break
            delay(25)
        }
        try { check(cancel); return action() } finally { mutex.unlock() }
    }

    private suspend fun check(cancel: CancelToken) {
        currentCoroutineContext().ensureActive()
        cancel.throwIfCancelled()
    }

    private fun sql(statement: String, vararg parameters: Pair<String, SqlValue>) =
        SqlRequest(statement, SnapshotList.copyOf(parameters.map { SqlParameter(it.first, it.second) }), 1)
    private fun problem(code: FailureCode) = DomainFailure(code, "Origin cooldown unavailable", SnapshotList.empty())
    private fun <T> failure(code: FailureCode): Outcome<T> = Outcome.Failure(problem(code))
    private fun <T> Outcome<*>.nonSuccess(): Outcome<T> = when (this) {
        is Outcome.Failure -> this
        is Outcome.Unavailable -> this
        is Outcome.Unknown -> this
        is Outcome.Success -> error("Expected non-success outcome")
    }
    private fun <T> Outcome<T>.valueOrAbort(tx: DbTransaction): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> tx.abort(error)
        is Outcome.Unavailable -> tx.abort(problem(FailureCode.PERSISTENCE))
        is Outcome.Unknown -> tx.abort(problem(FailureCode.CORRUPT_DATA))
    }
}
