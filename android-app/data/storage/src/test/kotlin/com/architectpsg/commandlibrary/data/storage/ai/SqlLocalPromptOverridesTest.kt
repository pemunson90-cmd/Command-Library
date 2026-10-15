package com.architectpsg.commandlibrary.data.storage.ai

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.HostLifeDatabase
import com.architectpsg.commandlibrary.data.storage.schema.Migration16To17
import com.architectpsg.commandlibrary.feature.ai.SqlLocalPromptOverrides
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real bundled SQLite and existing engine parser; these are not Android Room/UI acceptance. */
class SqlLocalPromptOverridesTest {
    @get:Rule val folder = TemporaryFolder()
    private val mode = OfflineLabMode.ROAST
    private val body = "Exact 日本語 😀\r\n{{deck}}\n{{constraints}}\n{{request}}"
    private val token = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private class Time : Clock {
        var calls = 0
        override fun utcNow() = UtcTimestamp(1000L + calls++, 23)
        override fun monotonicNow(): Duration = Duration.ZERO
    }
    private fun repo(db: Database, time: Time = Time()) = SqlLocalPromptOverrides(db, time)
    private suspend fun open(file: File): HostLifeDatabase = HostLifeDatabase(file, initialize = false).also {
        Migration16To17.migrate(it.connection)
    }
    private fun <T> ok(outcome: Outcome<T>): T {
        assertTrue("Expected success: $outcome", outcome is Outcome.Success)
        return (outcome as Outcome.Success).value
    }
    private fun failed(outcome: Outcome<*>, code: FailureCode) {
        assertTrue("Expected $code: $outcome", outcome is Outcome.Failure)
        assertEquals(code, (outcome as Outcome.Failure).error.code)
    }
    private fun unsupported(outcome: Outcome<*>) {
        assertTrue("Expected unsupported: $outcome", outcome is Outcome.Unavailable)
        assertEquals(UnavailableReason.UNSUPPORTED, (outcome as Outcome.Unavailable).reason)
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun replaceBody(db: HostLifeDatabase, bytes: ByteArray, version: Int = 1) {
        db.connection.prepare("UPDATE prompt_override SET body=?,body_length=?,body_sha256=?,template_version=?").use {
            it.bindBlob(1, bytes); it.bindLong(2, bytes.size.toLong()); it.bindText(3, hash(bytes)); it.bindLong(4, version.toLong()); it.step()
        }
    }
    /** Observe requests, preserving the actual host adapter and its transaction/rollback semantics. */
    private class Observed(private val db: Database) : Database by db {
        var bodyReads = 0
        override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> =
            db.transaction({ tx -> action(object : DbTransaction by tx {
                override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> {
                    if (request.statement.startsWith("SELECT body FROM")) bodyReads++
                    assertEquals(1, request.maxRows)
                    return tx.query(request, cancel)
                }
            }) }, cancel)
    }

    @Test fun invalidGrammarUnicodeAndByteLimitRejectBeforeAnyDatabaseAccess() = runBlocking {
        val noIo = object : Database {
            override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> = error("No database read")
            override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> = error("No transaction")
            override suspend fun capabilities(cancel: CancelToken): Outcome<DbCapabilities> = error("No capability read")
        }
        for (invalid in listOf("{{deck}}{{request}}", "{{deck}}{{constraints}}{{request}}{{deck}}",
            "{{deck}}{{constraints}}{{request}}{{unknown}}", "{{ deck}}{{constraints}}{{request}}",
            "{{deck}}{{constraints}}{{request}}{", "{{deck}}{{constraints}}{{request}}\uD800",
            "{{deck}}{{constraints}}{{request}}\uDC00", "x".repeat(32769), "😀".repeat(8192) + body)) {
            failed(repo(noIo).save(mode, null, invalid, token), FailureCode.INVALID_INPUT)
        }
    }

    @Test fun exactStrictUtf8ByteLimitRoundTripsWithoutNormalization() = runBlocking {
        open(folder.newFile()).use { db ->
            val exact = "x".repeat(32768 - body.toByteArray(Charsets.UTF_8).size) + body
            assertEquals(32768, exact.toByteArray(Charsets.UTF_8).size)
            val r = repo(db)
            val stored = ok(r.save(mode, null, exact, token))
            assertEquals(exact, ok(r.load(mode, token))!!.body)
            failed(r.save(mode, stored.revision, exact + "x", token), FailureCode.INVALID_INPUT)
            assertEquals(stored, ok(r.load(mode, token)))
        }
    }

    @Test fun allModesRoundTripReopenAndRetainedResetPreserveOtherModesAndOldRows() = runBlocking {
        val file = folder.newFile()
        val expected = mutableMapOf<OfflineLabMode, StoredPromptOverride>()
        open(file).use { db ->
            db.sql("CREATE TABLE unrelated_quantity (id TEXT PRIMARY KEY, quantity INTEGER NOT NULL)")
            db.sql("INSERT INTO unrelated_quantity VALUES ('keep',42)")
            val r = repo(db)
            for (m in OfflineLabMode.entries) {
                assertNull(ok(r.load(m, token)))
                expected[m] = ok(r.save(m, null, "$m $body", token))
            }
        }
        HostLifeDatabase(file, initialize = false).use { db ->
            val r = repo(db)
            for ((m, stored) in expected) assertEquals(stored, ok(r.load(m, token)))
            val original = expected.getValue(mode)
            val reset = ok(r.reset(mode, original.revision, token))!!
            assertNull(reset.body)
            assertEquals(Revision(2), reset.revision)
            assertEquals(mode.shippedVersion, reset.shippedVersion)
            assertEquals(reset, ok(r.load(mode, token)))
            failed(r.save(mode, null, body, token), FailureCode.STALE_REVISION)
            failed(r.save(mode, original.revision, body, token), FailureCode.STALE_REVISION)
            assertEquals(Revision(3), ok(r.save(mode, reset.revision, body, token)).revision)
            for ((m, stored) in expected) if (m != mode) assertEquals(stored, ok(r.load(m, token)))
            assertEquals(42L, db.scalar("SELECT quantity FROM unrelated_quantity WHERE id='keep'"))
            assertEquals(4L, db.scalar("SELECT count(*) FROM prompt_override"))
        }
    }

    @Test fun casPrecedesEveryNoopAndNoopsDoNotReadClock() = runBlocking {
        open(folder.newFile()).use { db ->
            val time = Time(); val r = repo(db, time)
            assertNull(ok(r.reset(mode, null, token)))
            failed(r.reset(mode, Revision(1), token), FailureCode.STALE_REVISION)
            assertEquals(0, time.calls)
            val first = ok(r.save(mode, null, body, token))
            assertEquals(first, ok(r.save(mode, first.revision, body, token)))
            failed(r.save(mode, null, body, token), FailureCode.STALE_REVISION)
            assertEquals(1, time.calls)
            val reset = ok(r.reset(mode, first.revision, token))!!
            failed(r.reset(mode, first.revision, token), FailureCode.STALE_REVISION)
            assertEquals(reset, ok(r.reset(mode, reset.revision, token)))
            failed(r.reset(mode, null, token), FailureCode.STALE_REVISION)
            assertEquals(2, time.calls)
            assertEquals(1L, db.scalar("SELECT count(*) FROM prompt_override WHERE body IS NULL AND body_length=0 AND revision=2"))
        }
    }

    @Test fun futureTemplateIsPreservedForReviewWhileCurrentGrammarAndStrictUtf8AreChecked() = runBlocking {
        open(folder.newFile()).use { db ->
            val r = repo(db); val initial = ok(r.save(mode, null, body, token))
            val futureBody = "future grammar {different} 日本語"
            replaceBody(db, futureBody.toByteArray(Charsets.UTF_8), mode.shippedVersion + 1)
            val future = ok(r.load(mode, token))!!
            assertEquals(futureBody, future.body)
            assertEquals(mode.shippedVersion + 1, future.shippedVersion)
            assertEquals(initial.revision, future.revision)
            failed(r.save(mode, null, body, token), FailureCode.STALE_REVISION)
            assertEquals(future, ok(r.load(mode, token)))
            // Explicit valid save is the only adoption, and uses the current shipped version.
            val adopted = ok(r.save(mode, future.revision, body, token))
            assertEquals(Revision(2), adopted.revision)
            assertEquals(mode.shippedVersion, adopted.shippedVersion)
            replaceBody(db, futureBody.toByteArray(Charsets.UTF_8))
            failed(r.load(mode, token), FailureCode.CORRUPT_DATA)
            replaceBody(db, byteArrayOf(0xc3.toByte(), 0x28), mode.shippedVersion + 1)
            failed(r.load(mode, token), FailureCode.CORRUPT_DATA)
        }
    }

    @Test fun newerWireFormatRejectsAllOperationsWithoutChangingTheStoredRow() = runBlocking {
        open(folder.newFile()).use { db ->
            val r = repo(db); val initial = ok(r.save(mode, null, body, token))
            db.sql("UPDATE prompt_override SET format_version=2")
            val observer = Observed(db); val guarded = repo(observer)
            unsupported(guarded.load(mode, token))
            unsupported(guarded.save(mode, initial.revision, body, token))
            unsupported(guarded.reset(mode, initial.revision, token))
            assertEquals(0, observer.bodyReads)
            assertEquals(1L, db.scalar("SELECT count(*) FROM prompt_override WHERE format_version=2 AND revision=1 AND body IS NOT NULL"))
        }
    }

    @Test fun metadataBoundsTypesAndHashShapeAreCheckedBeforeBlobRead() = runBlocking {
        for (mutation in listOf("body=zeroblob(32769),body_length=32769", "body_length='wrong'", "body_length=2",
            "body='text'", "body_sha256='bad'", "body_sha256=CAST(zeroblob(65) AS TEXT)",
            "revision=0", "revision='wrong'", "template_version=0", "template_version=2147483648",
            "modified_nanos=1000000000", "modified_seconds='wrong'", "format_version=0",
            "body=NULL", "body_length=-1")) {
            open(folder.newFile()).use { db ->
                ok(repo(db).save(mode, null, body, token)); db.sql("UPDATE prompt_override SET $mutation")
                val observer = Observed(db)
                failed(repo(observer).load(mode, token), FailureCode.CORRUPT_DATA)
                assertEquals("Fetched body after $mutation", 0, observer.bodyReads)
            }
        }
        open(folder.newFile()).use { db ->
            val r = repo(db); val initial = ok(r.save(mode, null, body, token))
            db.sql("UPDATE prompt_override SET body_sha256='${"0".repeat(64)}'")
            val observer = Observed(db)
            failed(repo(observer).load(mode, token), FailureCode.CORRUPT_DATA)
            assertEquals(1, observer.bodyReads)
            failed(r.reset(mode, initial.revision, token), FailureCode.CORRUPT_DATA)
            assertEquals(1L, db.scalar("SELECT revision FROM prompt_override"))
        }
    }

    @Test fun revisionOverflowRejectsBeforeClockOrWriteButAnExactNoopStillSucceeds() = runBlocking {
        open(folder.newFile()).use { db ->
            val time = Time(); val r = repo(db, time)
            ok(r.save(mode, null, body, token))
            db.sql("UPDATE prompt_override SET revision=9223372036854775807")
            val exhausted = Revision(Long.MAX_VALUE)
            assertEquals(exhausted, ok(r.save(mode, exhausted, body, token)).revision)
            failed(r.save(mode, exhausted, "changed $body", token), FailureCode.INVALID_INPUT)
            failed(r.reset(mode, exhausted, token), FailureCode.INVALID_INPUT)
            assertEquals(1, time.calls)
            assertEquals(Long.MAX_VALUE, db.scalar("SELECT revision FROM prompt_override"))
        }
    }

    @Test fun unexpectedAffectedCountIsStaleAndRollsBackTheActualWrite() = runBlocking {
        open(folder.newFile()).use { db ->
            val guarded = object : Database by db {
                override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> =
                    db.transaction({ tx -> action(object : DbTransaction by tx {
                        override suspend fun execute(request: SqlRequest, cancel: CancelToken): Outcome<DbWriteResult> {
                            val result = tx.execute(request, cancel)
                            return if (result is Outcome.Success) Outcome.Success(DbWriteResult(0)) else result
                        }
                    }) }, cancel)
            }
            failed(repo(guarded).save(mode, null, body, token), FailureCode.STALE_REVISION)
            assertEquals(0L, db.scalar("SELECT count(*) FROM prompt_override"))
        }
    }

    @Test fun cancellationBeforeIoAndDuringWriteEscapesAndAfterCommitRequiresExplicitReload() = runBlocking {
        val file = folder.newFile()
        open(file).use { db ->
            var stopped = true
            val cancel = object : CancelToken {
                override val isCancelled get() = stopped
                override fun throwIfCancelled() { if (stopped) throw CancellationException("Synthetic cancellation") }
            }
            val r = repo(db)
            try { r.save(mode, null, body, cancel); fail("Expected cancellation") } catch (_: CancellationException) { }
            assertEquals(0L, db.scalar("SELECT count(*) FROM prompt_override"))
            stopped = false
            db.afterWrite = { stopped = true }
            try { r.save(mode, null, body, cancel); fail("Expected cancellation") } catch (_: CancellationException) { }
            assertEquals(0L, db.scalar("SELECT count(*) FROM prompt_override"))
            db.afterWrite = null
            val afterCommit = object : Database by db {
                override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
                    val result = db.transaction(action, cancel)
                    if (result is Outcome.Success) throw CancellationException("Synthetic cancellation after commit")
                    return result
                }
            }
            try { repo(afterCommit).save(mode, null, body, token); fail("Expected cancellation") } catch (_: CancellationException) { }
        }
        HostLifeDatabase(file, initialize = false).use { db ->
            val r = repo(db); val committed = ok(r.load(mode, token))!!
            assertEquals(Revision(1), committed.revision)
            assertEquals(body, committed.body)
            failed(r.save(mode, null, body, token), FailureCode.STALE_REVISION)
        }
    }
}
