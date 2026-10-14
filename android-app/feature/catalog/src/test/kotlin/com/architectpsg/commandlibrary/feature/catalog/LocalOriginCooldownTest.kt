package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** Real bundled SQLite rows; the clock and 429 metadata are synthetic. */
class LocalOriginCooldownTest {
    private val search = ResourceUri("https://api.scryfall.com/cards/search?page=1")
    private val bulk = ResourceUri("https://api.scryfall.com/bulk-data")
    private val other = ResourceUri("https://other.example/cards")

    private class MutableClock : Clock {
        var elapsed: Duration = Duration.ZERO
        var utc: UtcTimestamp = UtcTimestamp(1_700_000_000)
        override fun monotonicNow(): Duration = elapsed
        override fun utcNow(): UtcTimestamp = utc
    }

    private fun fixture(db: BulkSqlTestDatabase) {
        db.exec("""CREATE TABLE IF NOT EXISTS acquisition_origin_cooldown (
            origin TEXT NOT NULL PRIMARY KEY,
            revision INTEGER NOT NULL,
            interval_seconds INTEGER,
            interval_nano INTEGER
        )""")
    }

    private fun <T> value(result: Outcome<T>): T {
        assertTrue(result.toString(), result is Outcome.Success)
        return (result as Outcome.Success).value
    }

    private fun failed(result: Outcome<*>, code: FailureCode) {
        assertTrue(result.toString(), result is Outcome.Failure)
        assertEquals(code, (result as Outcome.Failure).error.code)
    }

    @Test fun minimumLaterShorterLongerAndInfiniteNeverShortenTheOrigin() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            fixture(db)
            val clock = MutableClock()
            val cooldown = LocalOriginCooldown(db, clock)
            value(cooldown.record(search, Knowledge.Absent("No Retry-After"), BULK_NO_CANCEL))
            assertEquals(30.seconds, value(cooldown.remaining(bulk, BULK_NO_CANCEL)))
            assertEquals(1L, db.rows("SELECT revision FROM acquisition_origin_cooldown").single().bulkLong("revision"))

            clock.elapsed = 10.seconds
            value(cooldown.record(search, Knowledge.Known(5.seconds), BULK_NO_CANCEL))
            assertEquals(30.seconds, value(cooldown.remaining(bulk, BULK_NO_CANCEL)))
            assertEquals(2L, db.rows("SELECT revision FROM acquisition_origin_cooldown").single().bulkLong("revision"))

            value(cooldown.record(search, Knowledge.Known(90.seconds), BULK_NO_CANCEL))
            assertEquals(90.seconds, value(cooldown.remaining(bulk, BULK_NO_CANCEL)))
            assertEquals(3L, db.rows("SELECT revision FROM acquisition_origin_cooldown").single().bulkLong("revision"))
            clock.elapsed = 25.seconds
            assertEquals(75.seconds, value(cooldown.remaining(search, BULK_NO_CANCEL)))

            value(cooldown.record(search, Knowledge.Known(Duration.INFINITE), BULK_NO_CANCEL))
            assertEquals(Duration.INFINITE, value(cooldown.remaining(bulk, BULK_NO_CANCEL)))
            val row = db.rows("SELECT revision,interval_seconds,interval_nano FROM acquisition_origin_cooldown").single()
            assertEquals(4L, row.bulkLong("revision"))
            assertNull(row.bulkOptionalLong("interval_seconds"))
            assertNull(row.bulkOptionalLong("interval_nano"))
        }
    }

    @Test fun canonicalSameOriginAndDifferentOriginRemainIsolated() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            fixture(db)
            val cooldown = LocalOriginCooldown(db, MutableClock())
            value(cooldown.record(ResourceUri("https://API.SCRYFALL.COM:443/cards/search"), Knowledge.Known(45.seconds), BULK_NO_CANCEL))
            assertEquals(45.seconds, value(cooldown.remaining(search, BULK_NO_CANCEL)))
            assertEquals(Duration.ZERO, value(cooldown.remaining(other, BULK_NO_CANCEL)))
            value(cooldown.record(other, Knowledge.Known(60.seconds), BULK_NO_CANCEL))
            assertEquals(2L, db.count("acquisition_origin_cooldown"))
            assertEquals(45.seconds, value(cooldown.remaining(bulk, BULK_NO_CANCEL)))
            assertEquals(60.seconds, value(cooldown.remaining(other, BULK_NO_CANCEL)))
        }
    }

    @Test fun realDatabaseReopenReplaysFullIntervalAndUtcJumpsNeverExpireIt() = runBlocking {
        val file = File.createTempFile("origin-cooldown", ".sqlite").apply { delete() }
        try {
            BulkSqlTestDatabase(file.path).use { db ->
                fixture(db)
                val clock = MutableClock()
                val cooldown = LocalOriginCooldown(db, clock)
                value(cooldown.record(search, Knowledge.Known(60.seconds), BULK_NO_CANCEL))
                clock.elapsed = 20.seconds
                clock.utc = UtcTimestamp(Long.MAX_VALUE)
                assertEquals(40.seconds, value(cooldown.remaining(search, BULK_NO_CANCEL)))
                clock.utc = UtcTimestamp(1)
                assertEquals(40.seconds, value(cooldown.remaining(search, BULK_NO_CANCEL)))
            }
            BulkSqlTestDatabase(file.path).use { db ->
                val clock = MutableClock()
                clock.utc = UtcTimestamp(Long.MAX_VALUE)
                val restarted = LocalOriginCooldown(db, clock)
                assertEquals(60.seconds, value(restarted.remaining(search, BULK_NO_CANCEL)))
                clock.elapsed = 59.seconds
                assertEquals(1.seconds, value(restarted.remaining(search, BULK_NO_CANCEL)))
                clock.utc = UtcTimestamp(1)
                clock.elapsed = 60.seconds
                assertEquals(Duration.ZERO, value(restarted.remaining(search, BULK_NO_CANCEL)))
                assertEquals(1L, db.count("acquisition_origin_cooldown"))
                val cleared = db.rows("SELECT revision,interval_seconds,interval_nano FROM acquisition_origin_cooldown").single()
                assertEquals(2L, cleared.bulkLong("revision"))
                assertEquals(0L, cleared.bulkLong("interval_seconds"))
                assertEquals(0L, cleared.bulkLong("interval_nano"))
            }
        } finally { file.delete() }
    }

    @Test fun finiteLongCooldownAndCorruptPersistedRowsFailClosed() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            fixture(db)
            val clock = MutableClock()
            value(LocalOriginCooldown(db, clock).record(search, Knowledge.Known(25.hours), BULK_NO_CANCEL))
            assertEquals(25.hours, value(LocalOriginCooldown(db, clock).remaining(search, BULK_NO_CANCEL)))
            for ((revision, seconds, nano) in listOf(
                Triple(0, "30", "0"),
                Triple(1, "NULL", "0"),
                Triple(1, "-1", "0"),
                Triple(1, "29", "0"),
                Triple(1, "0", "1"),
                Triple(1, "30", "1000000000"),
            )) {
                db.exec("DELETE FROM acquisition_origin_cooldown")
                db.exec("INSERT INTO acquisition_origin_cooldown VALUES ('https://api.scryfall.com:443',$revision,$seconds,$nano)")
                failed(LocalOriginCooldown(db, clock).remaining(search, BULK_NO_CANCEL), FailureCode.CORRUPT_DATA)
            }
        }
    }

    @Test fun cancellationDoesNotCreateOrClearACooldown() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            fixture(db)
            val clock = MutableClock()
            val cooldown = LocalOriginCooldown(db, clock)
            val cancelled = BulkMutableCancel().apply { this.cancelled = true }
            try { cooldown.record(search, Knowledge.Known(60.seconds), cancelled); fail("Record was cancelled") }
            catch (_: CancellationException) { }
            assertEquals(0L, db.count("acquisition_origin_cooldown"))
            value(cooldown.record(search, Knowledge.Known(60.seconds), BULK_NO_CANCEL))
            clock.elapsed = 60.seconds
            try { cooldown.remaining(search, cancelled); fail("Expiry was cancelled") }
            catch (_: CancellationException) { }
            assertEquals(1L, db.count("acquisition_origin_cooldown"))
        }
    }

    @Test fun failedSqlWriteLatchesAnInProcessIndefiniteDeny() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            fixture(db)
            db.exec("""CREATE TRIGGER fail_cooldown_insert BEFORE INSERT ON acquisition_origin_cooldown
                BEGIN SELECT RAISE(ABORT, 'injected write failure'); END""")
            val cooldown = LocalOriginCooldown(db, MutableClock())
            failed(cooldown.record(search, Knowledge.Known(60.seconds), BULK_NO_CANCEL), FailureCode.PERSISTENCE)
            db.exec("DROP TRIGGER fail_cooldown_insert")
            assertEquals(0L, db.count("acquisition_origin_cooldown"))
            assertEquals(Duration.INFINITE, value(cooldown.remaining(search, BULK_NO_CANCEL)))
            assertEquals(Duration.ZERO, value(cooldown.remaining(other, BULK_NO_CANCEL)))
        }
    }

    @Test fun expiredRevisionCannotClearANewer429() = runBlocking {
        BulkSqlTestDatabase().use { real ->
            fixture(real)
            val clock = MutableClock()
            value(LocalOriginCooldown(real, clock).record(search, Knowledge.Absent("No Retry-After"), BULK_NO_CANCEL))
            val intercepted = object : Database by real {
                var injectBeforeExpiryWrite = false
                var injected = false
                override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
                    return real.transaction({ tx ->
                        val observed = object : DbTransaction by tx {
                            override suspend fun execute(request: SqlRequest, cancel: CancelToken): Outcome<DbWriteResult> {
                                if (injectBeforeExpiryWrite && !injected &&
                                    request.statement.trimStart().startsWith("UPDATE acquisition_origin_cooldown", ignoreCase = true)) {
                                    injected = true
                                    assertEquals(1L, tx.execute(bulkSql("""UPDATE acquisition_origin_cooldown SET
                                        revision=revision+1, interval_seconds=60, interval_nano=0
                                        WHERE origin='https://api.scryfall.com:443'"""), cancel).bulkValue().affectedRows)
                                }
                                return tx.execute(request, cancel)
                            }
                        }
                        action(observed)
                    }, cancel)
                }
            }
            val older = LocalOriginCooldown(intercepted, clock)
            assertEquals(30.seconds, value(older.remaining(search, BULK_NO_CANCEL)))
            clock.elapsed = 30.seconds
            intercepted.injectBeforeExpiryWrite = true
            assertEquals(60.seconds, value(older.remaining(search, BULK_NO_CANCEL)))
            assertTrue("Expiry must attempt a revision-checked tombstone update", intercepted.injected)
            assertEquals(1L, real.count("acquisition_origin_cooldown"))
            assertEquals(2L, real.rows("SELECT revision FROM acquisition_origin_cooldown").single().bulkLong("revision"))
        }
    }

    @Test fun independentHelperRecreationNeverReusesTheExpiredGeneration() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            fixture(db)
            val clock = MutableClock()
            val first = LocalOriginCooldown(db, clock)
            val second = LocalOriginCooldown(db, clock)
            value(first.record(search, Knowledge.Absent("No Retry-After"), BULK_NO_CANCEL))
            assertEquals(30.seconds, value(second.remaining(search, BULK_NO_CANCEL)))
            clock.elapsed = 30.seconds
            assertEquals(Duration.ZERO, value(first.remaining(search, BULK_NO_CANCEL)))
            val tombstone = db.rows("SELECT revision,interval_seconds FROM acquisition_origin_cooldown").single()
            assertEquals(2L, tombstone.bulkLong("revision"))
            assertEquals(0L, tombstone.bulkLong("interval_seconds"))
            value(first.record(search, Knowledge.Known(60.seconds), BULK_NO_CANCEL))
            assertEquals(3L, db.rows("SELECT revision FROM acquisition_origin_cooldown").single().bulkLong("revision"))
            assertEquals(60.seconds, value(second.remaining(search, BULK_NO_CANCEL)))
            assertEquals(60.seconds, value(first.remaining(search, BULK_NO_CANCEL)))
        }
    }

    @Test fun unchangedRevisionAfterZeroRowExpiryWriteFailsClosedWithoutSpinning() = runBlocking {
        BulkSqlTestDatabase().use { real ->
            fixture(real)
            val clock = MutableClock()
            value(LocalOriginCooldown(real, clock).record(search, Knowledge.Absent("No Retry-After"), BULK_NO_CANCEL))
            var interceptedWrites = 0
            val zeroWrite = object : Database by real {
                override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> =
                    real.transaction({ tx ->
                        action(object : DbTransaction by tx {
                            override suspend fun execute(request: SqlRequest, cancel: CancelToken): Outcome<DbWriteResult> {
                                if (request.statement.trimStart().startsWith("UPDATE acquisition_origin_cooldown", ignoreCase = true)) {
                                    interceptedWrites++
                                    return Outcome.Success(DbWriteResult(0))
                                }
                                return tx.execute(request, cancel)
                            }
                        })
                    }, cancel)
            }
            val cooldown = LocalOriginCooldown(zeroWrite, clock)
            assertEquals(30.seconds, value(cooldown.remaining(search, BULK_NO_CANCEL)))
            clock.elapsed = 30.seconds
            val outcome = withTimeoutOrNull(1_000) { cooldown.remaining(search, BULK_NO_CANCEL) }
            assertNotNull("Unchanged revision must not make expiry loop indefinitely", outcome)
            failed(requireNotNull(outcome), FailureCode.PERSISTENCE)
            assertEquals(1, interceptedWrites)
            assertEquals(1L, real.rows("SELECT revision FROM acquisition_origin_cooldown").single().bulkLong("revision"))
        }
    }

    @Test fun negativeMonotonicOriginStillUsesNonnegativeElapsedTime() = runBlocking {
        BulkSqlTestDatabase().use { db ->
            fixture(db)
            val clock = MutableClock().apply { elapsed = -100.seconds }
            val cooldown = LocalOriginCooldown(db, clock)
            value(cooldown.record(search, Knowledge.Absent("No Retry-After"), BULK_NO_CANCEL))
            assertEquals(30.seconds, value(cooldown.remaining(search, BULK_NO_CANCEL)))
            clock.elapsed = -71.seconds
            assertEquals(1.seconds, value(cooldown.remaining(search, BULK_NO_CANCEL)))
            clock.elapsed = -70.seconds
            assertEquals(Duration.ZERO, value(cooldown.remaining(search, BULK_NO_CANCEL)))
        }
    }
}
