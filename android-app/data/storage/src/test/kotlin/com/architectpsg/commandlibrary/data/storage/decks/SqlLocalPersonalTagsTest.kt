package com.architectpsg.commandlibrary.data.storage.decks

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.HostLifeDatabase
import com.architectpsg.commandlibrary.data.storage.schema.Migration18To19
import com.architectpsg.commandlibrary.feature.decks.PersonalTagPolicy
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalPersonalTags
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlin.time.Duration
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Actual bundled SQLite/new schema19 table via existing adapter; not full Room/native acceptance. */
class SqlLocalPersonalTagsTest {
    @get:Rule val folder = TemporaryFolder()
    private val token = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private class Time : Clock {
        var calls = 0
        var now = UtcTimestamp(1000, 23)
        override fun utcNow(): UtcTimestamp { calls++; return now }
        override fun monotonicNow(): Duration = Duration.ZERO
    }
    private fun tag(id: String = "tag", name: String = "Removal", retired: Boolean = false) = PersonalTag(EntityId(id), name, retired)
    private fun tags(vararg values: PersonalTag) = SnapshotList.of(*values)
    private fun repo(db: Database, clock: Clock = Time()) = SqlLocalPersonalTags(db, clock)
    private suspend fun open(file: File = folder.newFile()): HostLifeDatabase = HostLifeDatabase(file, initialize = false).also {
        Migration18To19.migrate(it.connection)
        it.sql("PRAGMA busy_timeout=3000")
    }
    private fun <T> ok(result: Outcome<T>): T {
        assertTrue("Expected success: $result", result is Outcome.Success)
        return (result as Outcome.Success).value
    }
    private fun failed(result: Outcome<*>, code: FailureCode) {
        assertTrue("Expected $code: $result", result is Outcome.Failure)
        assertEquals(code, (result as Outcome.Failure).error.code)
    }
    private fun unsupported(result: Outcome<*>) {
        assertTrue(result is Outcome.Unavailable)
        assertEquals(UnavailableReason.UNSUPPORTED, (result as Outcome.Unavailable).reason)
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun payload(db: HostLifeDatabase): ByteArray = db.connection.prepare("SELECT payload FROM personal_tag_pool").use {
        check(it.step()); it.getBlob(0)
    }
    private fun rewrite(db: HostLifeDatabase, bytes: ByteArray) {
        db.connection.prepare("UPDATE personal_tag_pool SET payload=?,payload_length=?,payload_sha256=?").use {
            it.bindBlob(1, bytes); it.bindLong(2, bytes.size.toLong()); it.bindText(3, hash(bytes)); it.step()
        }
    }

    @Test fun neverStoredAndIdenticalEmptyReplaceHaveNoClockOrWrite() = runBlocking {
        open().use { db ->
            val clock = Time(); val r = repo(db, clock)
            val empty = PersonalTagPool(Revision(0), SnapshotList.empty(), null)
            assertEquals(empty, ok(r.load(token)))
            assertEquals(empty, ok(r.replace(Revision(0), SnapshotList.empty(), token)))
            assertEquals(0, clock.calls); assertEquals(0L, db.scalar("SELECT count(*) FROM personal_tag_pool"))
            failed(r.replace(Revision(1), SnapshotList.empty(), token), FailureCode.STALE_REVISION)
        }
    }
    @Test fun createRenameReorderRetireReactivateAndReopenKeepExactValuesAndIds() = runBlocking {
        val file = folder.newFile()
        lateinit var savedPool: PersonalTagPool
        val a = tag("Ａ/界", " Artifact Synergy 😀 "); val b = tag("A/界", "Removal")
        open(file).use { db ->
            val clock = Time(); val r = repo(db, clock)
            val created = ok(r.replace(Revision(0), tags(a, b), token))
            assertEquals(Revision(1), created.revision); assertEquals(tags(a, b), created.tags)
            clock.now = UtcTimestamp(1001)
            val renamed = ok(r.replace(created.revision, tags(a.copy(name = "Changed"), b), token))
            val reordered = ok(r.replace(renamed.revision, SnapshotList.copyOf(renamed.tags.reversed()), token))
            val retired = ok(r.replace(reordered.revision, SnapshotList.copyOf(reordered.tags.map { it.copy(retired = true) }), token))
            assertEquals(listOf(b.id, a.id), retired.tags.map { it.id })
            savedPool = ok(r.replace(retired.revision, tags(b, a.copy(name = "Changed")), token))
            assertEquals(Revision(5), savedPool.revision)
            assertEquals(1L, db.scalar("SELECT count(*) FROM personal_tag_pool WHERE singleton_id=1"))
        }
        HostLifeDatabase(file, initialize = false).use { db -> assertEquals(savedPool, ok(repo(db).load(token))) }
    }
    @Test fun casPrecedesNoOpAndNoOpPreservesTimestampWithoutClock() = runBlocking {
        open().use { db ->
            val clock = Time(); val r = repo(db, clock)
            val first = ok(r.replace(Revision(0), tags(tag()), token))
            val calls = clock.calls
            failed(r.replace(Revision(0), first.tags, token), FailureCode.STALE_REVISION)
            clock.now = UtcTimestamp(Long.MIN_VALUE)
            assertEquals(first, ok(r.replace(first.revision, first.tags, token)))
            assertEquals(calls, clock.calls); assertEquals(first, ok(r.load(token)))
        }
    }
    @Test fun retiredIdentitiesArePermanentAndCannotBeDroppedByReplacement() = runBlocking {
        open().use { db ->
            val r = repo(db)
            val first = ok(r.replace(Revision(0), tags(tag(), tag("retired", "Older", true)), token))
            failed(r.replace(first.revision, tags(tag()), token), FailureCode.INVALID_INPUT)
            failed(r.replace(first.revision, tags(tag("new", "New")), token), FailureCode.INVALID_INPUT)
            failed(r.replace(first.revision, SnapshotList.empty(), token), FailureCode.INVALID_INPUT)
            assertEquals(first, ok(r.load(token)))
        }
    }
    @Test fun searchUsesExactNfkcTrimRootLowercaseAndActiveNamesAreUnique() = runBlocking {
        assertEquals("ramp", PersonalTagPolicy.searchKey(" ＲＡＭＰ "))
        assertEquals("é", PersonalTagPolicy.searchKey(" e\u0301 "))
        assertEquals("i", PersonalTagPolicy.searchKey("I"))
        assertEquals("", PersonalTagPolicy.searchKey(" \t "))
        val source = listOf(tag("1", " Ramp "), tag("2", "Draw"), tag("3", "RAMP", true))
        assertEquals(listOf(EntityId("1")), source.filter { !it.retired && PersonalTagPolicy.searchKey(it.name)
            .contains(PersonalTagPolicy.searchKey(" ＡＭ ")) }.map { it.id })
        open().use { db ->
            val r = repo(db); val initial = ok(r.replace(Revision(0), SnapshotList.copyOf(source), token))
            val activeCollision = SnapshotList.copyOf(source.map { it.copy(retired = false) })
            failed(r.replace(initial.revision, activeCollision, token), FailureCode.INVALID_INPUT)
            failed(r.replace(initial.revision, tags(source[0], source[1].copy(name = "ＲＡＭＰ"), source[2]), token), FailureCode.INVALID_INPUT)
            assertEquals(initial, ok(r.load(token)))
        }
    }
    @Test fun malformedAndOversizedInputRejectBeforeAnyDatabaseIo() = runBlocking {
        val noIo = object : Database {
            override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> = error("Unexpected database IO")
            override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> = error("Unexpected transaction")
            override suspend fun capabilities(cancel: CancelToken): Outcome<DbCapabilities> = error("Unexpected capabilities IO")
        }
        val r = repo(noIo)
        val invalid = listOf(tags(tag(name = "")), tags(tag(name = "  ")), tags(tag(name = "bad\u0000name")),
            tags(tag(name = "bad\u0085name")), tags(tag(name = "bad\nname")), tags(tag(name = "\uD800")),
            tags(tag(name = "\uDC00")), tags(tag(name = "é".repeat(129))), tags(tag("x".repeat(1025))),
            tags(tag("\uD800")), tags(tag(), tag(name = "Another")), tags(tag("1", "é"), tag("2", "e\u0301")),
            SnapshotList.copyOf(List(4097) { tag("id$it", "Name $it", true) }),
            SnapshotList.copyOf(List(4096) { tag("$it".padEnd(1024, 'x'), "Name $it") }))
        for (input in invalid) failed(r.replace(Revision(0), input, token), FailureCode.INVALID_INPUT)
    }
    @Test fun exactIdNameUtf8LimitsAndAll4096ReservedIdentitiesRoundTrip() = runBlocking {
        open().use { db ->
            val r = repo(db)
            val exact = tag("😀".repeat(256), "é".repeat(128))
            val first = ok(r.replace(Revision(0), tags(exact), token))
            assertEquals(exact, ok(r.load(token)).tags.single())
            val full = SnapshotList.copyOf(listOf(exact) + List(4095) { tag("id$it", "Name $it", true) })
            val saved = ok(r.replace(first.revision, full, token))
            assertEquals(full, ok(r.load(token)).tags); assertEquals(4096, saved.tags.size)
        }
    }
    @Test fun exactTwoMiBPayloadFitsAndOneAdditionalByteRejectsWithoutWrite() = runBlocking {
        open().use { db ->
            val values = List(2048) { index ->
                tag(index.toString().padEnd(if (index == 0) 968 else 1000, 'x'), "N$index".padEnd(15, 'n'))
            }
            val r = repo(db); val saved = ok(r.replace(Revision(0), SnapshotList.copyOf(values), token))
            assertEquals(PersonalTagLimits.MAX_PAYLOAD_BYTES.toLong(), db.scalar("SELECT payload_length FROM personal_tag_pool"))
            assertEquals(saved, ok(r.load(token)))
            val bigger = SnapshotList.copyOf(values.mapIndexed { index, value ->
                if (index == 0) value.copy(name = value.name + "x") else value
            })
            failed(r.replace(saved.revision, bigger, token), FailureCode.INVALID_INPUT)
            assertEquals(saved, ok(r.load(token)))
        }
    }
    @Test fun badScalarMetadataAndOversizedPayloadRejectBeforeBlobFetch() = runBlocking {
        val mutations = listOf("singleton_id=2", "revision=0", "revision=1.5", "modified_nanos=1000000000",
            "modified_nanos=-1", "modified_seconds='wrong'", "payload_version=0", "payload_length='wrong'",
            "payload_sha256='bad'", "payload_sha256=zeroblob(64)", "payload='not-a-blob'",
            "payload=zeroblob(2097153),payload_length=2097153", "payload_length=1")
        for (mutation in mutations) open().use { db ->
            val r = repo(db); ok(r.replace(Revision(0), tags(tag()), token)); val reads = db.blobQueries
            db.sql("UPDATE personal_tag_pool SET $mutation")
            failed(r.load(token), FailureCode.CORRUPT_DATA)
            assertEquals(reads, db.blobQueries)
        }
    }
    @Test fun unexpectedSecondSingletonRowIsNeverIgnored() = runBlocking {
        open().use { db ->
            val r = repo(db); ok(r.replace(Revision(0), tags(tag()), token)); val reads = db.blobQueries
            db.sql("INSERT INTO personal_tag_pool SELECT 2,revision,modified_seconds,modified_nanos,payload_version,payload_length,payload_sha256,payload FROM personal_tag_pool")
            failed(r.load(token), FailureCode.CORRUPT_DATA)
            failed(r.replace(Revision(1), tags(tag()), token), FailureCode.CORRUPT_DATA)
            assertEquals(reads, db.blobQueries)
        }
    }
    @Test fun checksumAndRevisionTimestampProjectionContradictionsAreCorrupt() = runBlocking {
        for (mutation in listOf("payload_sha256='" + "0".repeat(64) + "'", "revision=2",
            "modified_seconds=2000", "modified_nanos=24")) open().use { db ->
            val r = repo(db); ok(r.replace(Revision(0), tags(tag()), token))
            db.sql("UPDATE personal_tag_pool SET $mutation")
            failed(r.load(token), FailureCode.CORRUPT_DATA)
        }
    }
    @Test fun checksummedMalformedUnicodeLengthsFlagsAndTrailingBytesRemainCorrupt() = runBlocking {
        val mutations: List<(ByteArray) -> ByteArray> = listOf(
            { it.copyOf().also { b -> ByteBuffer.wrap(b).putInt(0, 0) } },
            { it.copyOf().also { b -> ByteBuffer.wrap(b).putInt(28, -1) } },
            { it.copyOf().also { b -> ByteBuffer.wrap(b).putInt(28, 4097) } },
            { it.copyOf().also { b -> ByteBuffer.wrap(b).putInt(32, Int.MAX_VALUE) } },
            { it.copyOf().also { b -> b[36] = 0x80.toByte() } },
            { it.copyOf().also { b -> b[b.lastIndex] = 2 } },
            { it + byteArrayOf(0) }, { it.copyOf(it.size - 1) })
        for (mutate in mutations) open().use { db ->
            val r = repo(db); ok(r.replace(Revision(0), tags(tag("a", "Name")), token))
            rewrite(db, mutate(payload(db)))
            failed(r.load(token), FailureCode.CORRUPT_DATA)
        }
    }
    @Test fun storedDuplicateIdsAndNormalizedActiveNamesAreRejectedEvenWithCorrectChecksum() = runBlocking {
        for (sameId in listOf(true, false)) open().use { db ->
            val r = repo(db)
            ok(r.replace(Revision(0), tags(tag("a", "One"), tag("b", "Two")), token))
            val bytes = payload(db)
            // First record: 4+1+4+3+1 = 13 bytes; second starts at45.
            if (sameId) bytes[49] = 'a'.code.toByte()
            else "One".toByteArray(Charsets.UTF_8).copyInto(bytes, 54)
            rewrite(db, bytes)
            failed(r.load(token), FailureCode.CORRUPT_DATA)
        }
    }
    @Test fun futureScalarOrWireVersionReturnsUnsupportedWithoutRewriting() = runBlocking {
        for (scalar in listOf(true, false)) open().use { db ->
            val r = repo(db); ok(r.replace(Revision(0), tags(tag()), token)); val reads = db.blobQueries
            if (scalar) db.sql("UPDATE personal_tag_pool SET payload_version=2")
            else rewrite(db, payload(db).also { ByteBuffer.wrap(it).putInt(4, 2) })
            unsupported(r.load(token)); unsupported(r.replace(Revision(1), tags(tag("new", "New")), token))
            assertEquals(1L, db.scalar("SELECT revision FROM personal_tag_pool"))
            if (scalar) assertEquals(reads, db.blobQueries)
        }
    }
    @Test fun revisionExhaustionFailsBeforeClockAndWriteButAllowsIdenticalNoOp() = runBlocking {
        open().use { db ->
            val clock = Time(); val r = repo(db, clock); val initial = ok(r.replace(Revision(0), tags(tag()), token))
            rewrite(db, payload(db).also { ByteBuffer.wrap(it).putLong(8, Long.MAX_VALUE) })
            db.sql("UPDATE personal_tag_pool SET revision=9223372036854775807")
            val before = clock.calls
            assertEquals(Revision(Long.MAX_VALUE), ok(r.replace(Revision(Long.MAX_VALUE), initial.tags, token)).revision)
            failed(r.replace(Revision(Long.MAX_VALUE), tags(tag(name = "Changed")), token), FailureCode.INVALID_INPUT)
            assertEquals(before, clock.calls); assertEquals(Long.MAX_VALUE, db.scalar("SELECT revision FROM personal_tag_pool"))
        }
    }
    @Test fun backwardClockAndWrappedTimestampNeverWrite() = runBlocking {
        for (wrapped in listOf(false, true)) open().use { db ->
            val clock = Time(); if (wrapped) clock.now = UtcTimestamp(Long.MAX_VALUE, 999999999)
            val r = repo(db, clock); val initial = ok(r.replace(Revision(0), tags(tag()), token))
            clock.now = if (wrapped) UtcTimestamp(Long.MIN_VALUE) else UtcTimestamp(1000, 22)
            failed(r.replace(initial.revision, tags(tag(name = "Changed")), token), FailureCode.INTERNAL)
            assertEquals(initial, ok(r.load(token)))
        }
    }
    @Test fun cancelledBeforeIoAndDuringWriteRollBackWhileLostAckRequiresExplicitReload() = runBlocking {
        val file = folder.newFile()
        open(file).use { db ->
            var stopped = true
            val cancel = object : CancelToken {
                override val isCancelled get() = stopped
                override fun throwIfCancelled() { if (stopped) throw CancellationException("Synthetic cancellation") }
            }
            val r = repo(db)
            try { r.replace(Revision(0), tags(tag()), cancel); fail("Expected cancellation") } catch (_: CancellationException) { }
            assertEquals(0L, db.scalar("SELECT count(*) FROM personal_tag_pool"))
            stopped = false; db.afterWrite = { stopped = true }
            try { r.replace(Revision(0), tags(tag()), cancel); fail("Expected cancellation") } catch (_: CancellationException) { }
            assertEquals(0L, db.scalar("SELECT count(*) FROM personal_tag_pool"))
            db.afterWrite = null
            var transactions = 0
            val lostAck = object : Database by db {
                override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
                    transactions++
                    val result = db.transaction(action, cancel)
                    if (result is Outcome.Success) throw CancellationException("Synthetic cancellation after commit")
                    return result
                }
            }
            try { repo(lostAck).replace(Revision(0), tags(tag()), token); fail("Expected cancellation") } catch (_: CancellationException) { }
            assertEquals(1, transactions)
        }
        HostLifeDatabase(file, initialize = false).use { db ->
            val r = repo(db); val loaded = ok(r.load(token))
            assertEquals(Revision(1), loaded.revision); assertEquals(tags(tag()), loaded.tags)
            failed(r.replace(Revision(0), loaded.tags, token), FailureCode.STALE_REVISION)
            assertEquals(loaded, ok(r.load(token)))
        }
    }
    @Test fun databaseDiagnosticsRemainStaticAndCannotLeakPersonalNames() = runBlocking {
        open().use { db ->
            val broken = object : Database by db {
                override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> =
                    Outcome.Failure(DomainFailure(FailureCode.PERSISTENCE, "Private tag name Removal", SnapshotList.empty()))
            }
            val result = repo(broken).replace(Revision(0), tags(tag()), token)
            failed(result, FailureCode.PERSISTENCE); assertFalse(result.toString().contains("Removal"))
        }
    }
    @Test fun independentConnectionsHaveExactlyOneWinnerForSameRevision() = runBlocking {
        val file = folder.newFile()
        open(file).use { first ->
            val created = ok(repo(first).replace(Revision(0), tags(tag()), token))
            HostLifeDatabase(file, initialize = false).use { second ->
                second.sql("PRAGMA busy_timeout=3000")
                val start = CompletableDeferred<Unit>()
                val calls = listOf(first, second).mapIndexed { index, db -> async(Dispatchers.IO) {
                    start.await(); repo(db).replace(created.revision, tags(tag(name = "Writer $index")), token)
                } }
                start.complete(Unit)
                val results = calls.awaitAll()
                assertEquals(1, results.count { it is Outcome.Success })
                assertEquals(1, results.count { it is Outcome.Failure && it.error.code == FailureCode.STALE_REVISION })
                assertEquals(Revision(2), ok(repo(first).load(token)).revision)
            }
        }
    }
}
