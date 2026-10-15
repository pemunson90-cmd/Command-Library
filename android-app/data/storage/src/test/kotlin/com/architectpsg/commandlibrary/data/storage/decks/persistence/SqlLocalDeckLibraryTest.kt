package com.architectpsg.commandlibrary.data.storage.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.decks.codec.DeckPayloadCodec
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckLibrary
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

class SqlLocalDeckLibraryTest {
    private val codec = DeckPayloadCodec()
    private val cancel = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private val clock = object : Clock {
        override fun utcNow() = UtcTimestamp(99, 123)
        override fun monotonicNow() = Duration.ZERO
    }
    private var nextId = 0
    private fun library(db: Database, ids: IdSource = object : IdSource { override fun nextId() = "generated-${nextId++}" }, time: Clock = clock): LocalDeckLibrary = SqlLocalDeckLibrary(db, codec, time, ids)
    private fun file() = File.createTempFile("deck-library-", ".sqlite").also { it.deleteOnExit() }
    private fun <T> ok(result: Outcome<T>): T { assertTrue(result.toString(), result is Outcome.Success); return (result as Outcome.Success).value }
    private fun failure(result: Outcome<*>, code: FailureCode) { assertTrue(result.toString(), result is Outcome.Failure); assertEquals(code, (result as Outcome.Failure).error.code) }
    private fun operation(raw: String = "create") = EntityId(raw)
    private fun deck(raw: String = "golden-deck") = DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE, raw), "Golden", Knowledge.Absent("none"), "",
        Knowledge.Absent("none"), SnapshotList.empty(), UtcTimestamp(1), UtcTimestamp(1), Revision(0), SnapshotList.empty(), Knowledge.Absent("none"), null, SnapshotList.empty(), SnapshotList.empty())
    private fun rich(): DeckSnapshot {
        val category = EntryLabel(EntityId("category/界"), "Category")
        val entry = DeckEntry(EntityId("old-entry"), CardIdentity(Knowledge.Known(OracleId(ProviderId("foreign"), "raw oracle/界")),
            Knowledge.Known(CardId(ProviderId("absent-source"), "raw printing")), ProviderIdentity(ProviderId("raw-source"), "kind", "raw/never-change"), Knowledge.Unknown("unknown", SnapshotList.empty()), LanguageTag("ja")),
            Int.MAX_VALUE, BoardRole.COMMANDER, Knowledge.Absent("none"), "notes", SnapshotList.of(category),
            SnapshotList.of(RoleOverride(EntityId("old-entry"), Knowledge.Absent("none"), "role", true, UtcTimestamp(1)), RoleOverride(null, Knowledge.Absent("none"), "global", false, UtcTimestamp(1))))
        return deck().copy(entries = SnapshotList.of(entry, entry.copy(id = EntityId("second"), roleOverrides = SnapshotList.empty())), categoryOrder = SnapshotList.of(category),
            description = "description", primer = "# Primer", tags = SnapshotList.of("a", "a", "界"), profileId = EntityId("foreign-profile"),
            catalog = SnapshotList.of(SnapshotReference(ProviderId("missing-catalog"), GenerationId("pinned"), Revision(9), LanguageTag("ja"))))
    }

    @Test fun createLoadReopenPreservesFullSnapshotAndLongQuantity() = runBlocking {
        val file = file(); val original = rich()
        HostDeckDatabase(file).use { db ->
            val library = library(db)
            assertEquals(original, ok(library.create(original, operation(), cancel)))
            assertEquals(original, ok(library.load(original.id, cancel)))
            val summary = ok(library.list(PageRequest(100), cancel)).items.single()
            assertEquals(2L * Int.MAX_VALUE, summary.totalQuantity)
            assertEquals(1L, db.count("deck_operation"))
        }
        HostDeckDatabase(file, false).use { assertEquals(original, ok(library(it).load(original.id, cancel))) }
    }

    @Test fun duplicateRemapsEntryOverridesAndPreservesForeignContent() = runBlocking {
        HostDeckDatabase(file()).use { db ->
            val library = library(db); val original = rich()
            ok(library.create(original, operation(), cancel))
            val copy = ok(library.duplicate(original.id, Revision(0), "Copy", operation("duplicate"), cancel))
            assertNotEquals(original.id, copy.id)
            assertEquals(LocalDeckLibraryLimits.SOURCE, copy.id.source)
            assertEquals(UtcTimestamp(99, 123), copy.createdAt); assertEquals(copy.createdAt, copy.modifiedAt)
            assertEquals(Revision(0), copy.revision)
            assertTrue(copy.entries.none { result -> original.entries.any { it.id == result.id } })
            for ((before, after) in original.entries.zip(copy.entries)) {
                assertEquals(before.copy(id = after.id, roleOverrides = SnapshotList.copyOf(before.roleOverrides.map { it.copy(entryId = it.entryId?.let { after.id }) })), after)
            }
            assertEquals(original.copy(id = copy.id, name = "Copy", createdAt = copy.createdAt, modifiedAt = copy.modifiedAt, entries = copy.entries), copy)
            assertEquals(copy, ok(library.load(copy.id, cancel)))
        }
    }

    @Test fun receiptReplayAndConflictPrecedeMutableSourceCasAndGeneration() = runBlocking {
        HostDeckDatabase(file()).use { db ->
            val library = library(db); val source = rich()
            ok(library.create(source, operation(), cancel))
            val copy = ok(library.duplicate(source.id, Revision(0), "Copy", operation("dup"), cancel))
            db.sql("UPDATE deck_head SET revision=9,name='changed' WHERE deck_id='golden-deck'")
            val noGeneration = library(db, object : IdSource { override fun nextId(): String = error("Replay generated an ID") })
            assertEquals(source, ok(noGeneration.create(source, operation(), cancel)))
            assertEquals(copy, ok(noGeneration.duplicate(source.id, Revision(0), "Copy", operation("dup"), cancel)))
            failure(noGeneration.duplicate(source.id, Revision(9), "Copy", operation("dup"), cancel), FailureCode.INVALID_INPUT)
            failure(noGeneration.create(source.copy(name = "Different"), operation(), cancel), FailureCode.INVALID_INPUT)
            assertEquals(2L, db.count("deck_operation"))
        }
    }

    @Test fun interruptedAcknowledgementReconcilesWithoutExtraRows() = runBlocking {
        HostDeckDatabase(file()).use { db ->
            val library = library(db); val original = rich(); db.loseAcknowledgement = true
            try { library.create(original, operation(), cancel); fail("Expected interrupted acknowledgement") } catch (_: CancellationException) { }
            db.loseAcknowledgement = false
            assertEquals(original, ok(library.create(original, operation(), cancel)))
            assertEquals(1L, db.count("deck_head")); assertEquals(1L, db.count("deck_revision")); assertEquals(1L, db.count("deck_operation"))
        }
    }

    @Test fun injectedAbortAfterEachWriteRollsBackAllRows() = runBlocking {
        for (write in 1..3) HostDeckDatabase(file()).use { db ->
            var writes = 0
            db.host.afterWrite = { if (++writes == write) throw IllegalStateException("injected") }
            failure(library(db).create(rich(), operation(), cancel), FailureCode.PERSISTENCE)
            assertEmpty(db)
        }
    }

    @Test fun cancellationAfterEachWriteEscapesUnchangedAndRollsBack() = runBlocking {
        for (write in 1..3) HostDeckDatabase(file()).use { db ->
            var writes = 0; val cancelled = CancellationException("cancel $write")
            db.host.afterWrite = { if (++writes == write) throw cancelled }
            try { library(db).create(rich(), operation(), cancel); fail("Expected cancellation") } catch (actual: CancellationException) { assertSame(cancelled, actual) }
            assertEmpty(db)
        }
    }

    @Test fun duplicateWriteFailuresPreserveSourceAndReceipts() = runBlocking {
        for (write in 1..3) HostDeckDatabase(file()).use { db ->
            val library = library(db); val original = rich(); ok(library.create(original, operation(), cancel))
            var writes = 0; db.host.afterWrite = { if (++writes == write) throw IllegalStateException("injected") }
            failure(library.duplicate(original.id, Revision(0), "Copy", operation("dup"), cancel), FailureCode.PERSISTENCE)
            db.host.afterWrite = null
            assertEquals(original, ok(library.load(original.id, cancel)))
            assertEquals(1L, db.count("deck_head")); assertEquals(1L, db.count("deck_revision")); assertEquals(1L, db.count("deck_operation"))
        }
    }

    @Test fun generatedInvalidAndCollidingIdsNeverWrite() = runBlocking {
        for (generated in listOf(listOf(" "), listOf("x".repeat(1025)), listOf("\uD800"), listOf("golden-deck"), listOf("new", "old-entry"), listOf("new", "same", "same"))) {
            HostDeckDatabase(file()).use { db ->
                val original = rich(); val library = library(db); ok(library.create(original, operation(), cancel))
                var n = 0
                val invalid = library(db, object : IdSource { override fun nextId() = generated[n++] })
                failure(invalid.duplicate(original.id, Revision(0), "Copy", operation("dup"), cancel), FailureCode.INTERNAL)
                assertEquals(1L, db.count("deck_head")); assertEquals(1L, db.count("deck_operation"))
            }
        }
    }

    @Test fun throwingClockIsStaticInternalFailureAndCancellationEscapesUnchanged() = runBlocking {
        for (clockFailure in listOf(IllegalArgumentException("private clock input"), IllegalStateException("private clock state"), CancellationException("clock cancellation"))) {
            HostDeckDatabase(file()).use { db ->
                val original = rich(); ok(library(db).create(original, operation(), cancel))
                val broken = object : Clock {
                    override fun utcNow(): UtcTimestamp = throw clockFailure
                    override fun monotonicNow() = Duration.ZERO
                }
                val library = library(db, time = broken)
                if (clockFailure is CancellationException) {
                    try { library.duplicate(original.id, Revision(0), "Copy", operation("dup"), cancel); fail("Expected cancellation") }
                    catch (actual: CancellationException) { assertSame(clockFailure, actual) }
                } else {
                    val result = library.duplicate(original.id, Revision(0), "Copy", operation("dup"), cancel)
                    failure(result, FailureCode.INTERNAL)
                    assertEquals("Deck clock failed", (result as Outcome.Failure).error.message)
                }
                assertEquals(1L, db.count("deck_head")); assertEquals(1L, db.count("deck_operation"))
            }
        }
    }

    @Test fun duplicateCancellationAfterEachWritePreservesSource() = runBlocking {
        for (write in 1..3) HostDeckDatabase(file()).use { db ->
            val library = library(db); val original = rich(); ok(library.create(original, operation(), cancel))
            var writes = 0; val cancelled = CancellationException("duplicate cancel $write")
            db.host.afterWrite = { if (++writes == write) throw cancelled }
            try { library.duplicate(original.id, Revision(0), "Copy", operation("dup"), cancel); fail("Expected cancellation") }
            catch (actual: CancellationException) { assertSame(cancelled, actual) }
            db.host.afterWrite = null
            assertEquals(original, ok(library.load(original.id, cancel)))
            assertEquals(1L, db.count("deck_head")); assertEquals(1L, db.count("deck_revision")); assertEquals(1L, db.count("deck_operation"))
        }
    }

    @Test fun invalidInputGraphAndIdentityBoundariesNeverWrite() = runBlocking {
        val original = rich()
        val candidates = listOf(original.copy(id = DeckId(ProviderId("foreign"), "id")), original.copy(revision = Revision(1)),
            original.copy(name = " "), original.copy(name = "界".repeat(342)), original.copy(name = "\uD800"),
            original.copy(id = DeckId(LocalDeckLibraryLimits.SOURCE, "x".repeat(1025))), original.copy(modifiedAt = UtcTimestamp(0)),
            original.copy(entries = SnapshotList.of(original.entries.first(), original.entries.first())), original.copy(categoryOrder = SnapshotList.empty()))
        for (candidate in candidates) HostDeckDatabase(file()).use { db ->
            failure(library(db).create(candidate, operation(), cancel), FailureCode.INVALID_INPUT); assertEmpty(db)
        }
        HostDeckDatabase(file()).use { db -> failure(library(db).create(original, operation("x".repeat(1025)), cancel), FailureCode.INVALID_INPUT); assertEmpty(db) }
    }

    @Test fun exactNameAndIdByteLimitsAndUnknownSourceBehavior() = runBlocking {
        HostDeckDatabase(file()).use { db ->
            val library = library(db); val original = deck("é".repeat(512)).copy(name = "é".repeat(512))
            assertEquals(original, ok(library.create(original, operation(), cancel)))
            val absent = library.load(DeckId(ProviderId("foreign"), original.id.raw), cancel)
            assertEquals(UnavailableReason.NOT_FOUND, (absent as Outcome.Unavailable).reason)
        }
    }

    @Test fun staleExpectedAndExistingIdentityRejectWithoutWrites() = runBlocking {
        HostDeckDatabase(file()).use { db ->
            val original = deck(); val library = library(db); ok(library.create(original, operation(), cancel))
            failure(library.duplicate(original.id, Revision(1), "Copy", operation("dup"), cancel), FailureCode.STALE_REVISION)
            failure(library.create(original, operation("other"), cancel), FailureCode.INVALID_INPUT)
            assertEquals(1L, db.count("deck_operation"))
        }
    }

    @Test fun packagesAreExplicitlyRejectedWithoutStripping() = runBlocking {
        HostDeckDatabase(file()).use { db ->
            val library = library(db); val base = deck()
            val packaged = base.copy(packageInstances = SnapshotList.of(PackageInstance(EntityId("instance"), EntityId("package"), EntityId("version"), PackageLinkPolicy.LINKED, SnapshotList.empty())))
            assertEquals(UnavailableReason.UNSUPPORTED, (library.create(packaged, operation(), cancel) as Outcome.Unavailable).reason)
            assertEmpty(db)
            ok(library.create(base, operation(), cancel)); replacePayload(db, packaged)
            assertEquals(UnavailableReason.UNSUPPORTED, (library.duplicate(base.id, Revision(0), "Copy", operation("dup"), cancel) as Outcome.Unavailable).reason)
            assertEquals(packaged, ok(library.load(base.id, cancel))); assertEquals(1L, db.count("deck_operation"))
        }
    }

    @Test fun packagedPayloadStillRequiresValidEntryAndCategoryGraph() = runBlocking {
        val original = rich()
        val packaged = original.copy(packageInstances = SnapshotList.of(PackageInstance(EntityId("instance"), EntityId("package"), EntityId("version"), PackageLinkPolicy.LINKED, SnapshotList.empty())))
        val first = original.entries.first()
        val corruptGraphs = listOf(packaged.copy(entries = SnapshotList.of(first, first)),
            packaged.copy(entries = SnapshotList.of(first.copy(roleOverrides = SnapshotList.of(first.roleOverrides.first().copy(entryId = EntityId("wrong")))))),
            packaged.copy(categoryOrder = SnapshotList.empty()))
        for (graph in corruptGraphs) HostDeckDatabase(file()).use { db ->
            val library = library(db); ok(library.create(original, operation(), cancel)); replacePayload(db, graph)
            failure(library.load(original.id, cancel), FailureCode.CORRUPT_DATA)
        }
        HostDeckDatabase(file()).use { db ->
            val library = library(db); ok(library.create(original, operation(), cancel)); replacePayload(db, packaged)
            assertEquals(packaged, ok(library.load(original.id, cancel)))
        }
    }

    @Test fun metadataOnlyHundredRowBinaryPagingAndReplayStableEpoch() = runBlocking {
        HostDeckDatabase(file()).use { db ->
            val library = library(db)
            val ids = (0..100).map { "id-%03d".format(it) } + listOf("a", "Z", "é", "界", "\uE000", "\uD800\uDC00")
            for ((n, id) in ids.withIndex()) ok(library.create(deck(id), operation("op$n"), cancel))
            db.host.blobQueries = 0
            val first = ok(library.list(PageRequest(100), cancel)); assertEquals(100, first.items.size); assertNotNull(first.nextCursor)
            val second = ok(library.list(PageRequest(100, first.nextCursor), cancel)); assertEquals(ids.size - 100, second.items.size); assertNull(second.nextCursor)
            val ordered = ids.sortedWith { a, b -> compareUtf8(a, b) }
            assertEquals(ordered, (first.items + second.items).map { it.id.raw }); assertEquals(0, db.host.blobQueries)
            ok(library.create(deck(ids.first()), operation("op0"), cancel))
            assertEquals(second, ok(library.list(PageRequest(100, first.nextCursor), cancel)))
            ok(library.create(deck("new"), operation("new"), cancel))
            failure(library.list(PageRequest(100, first.nextCursor), cancel), FailureCode.STALE_REVISION)
        }
    }

    @Test fun malformedNoncanonicalAndOversizedCursorRejects() = runBlocking {
        HostDeckDatabase(file()).use { db ->
            for (cursor in listOf("!", "a", "AAAA", "A".repeat(2048), "AQAAAAAAAAAAAAAAAA", "AQAAAAAAAAAAAQAAAGE="))
                failure(library(db).list(PageRequest(1, cursor), cancel), FailureCode.INVALID_INPUT)
        }
    }

    @Test fun corruptMetadataRejectedBeforePayloadAllocationForLoadAndList() = runBlocking {
        val edits = listOf("deck_head SET name=zeroblob(500000)", "deck_head SET name=CAST(x'ff' AS TEXT)",
            "deck_head SET revision='bad'", "deck_head SET created_nano=1000000000", "deck_head SET modified_seconds=0", "deck_head SET entry_count=-1",
            "deck_head SET total_quantity=1.5", "deck_revision SET payload_bytes=8388609", "deck_revision SET payload_bytes=1", "deck_revision SET payload=CAST('text' AS TEXT)",
            "deck_revision SET payload=zeroblob(8388609)", "deck_revision SET payload_sha256='ABC'", "deck_revision SET name='mismatch'", "deck_revision SET payload_bytes='bad'",
            "deck_revision SET created_seconds=CAST(1.5 AS REAL)")
        for (edit in edits) HostDeckDatabase(file()).use { db ->
            val library = library(db); val original = deck(); ok(library.create(original, operation(), cancel)); db.sql("UPDATE $edit")
            db.host.blobQueries = 0
            failure(library.load(original.id, cancel), FailureCode.CORRUPT_DATA)
            failure(library.list(PageRequest(100), cancel), FailureCode.CORRUPT_DATA)
            assertEquals(edit, 0, db.host.blobQueries)
            assertTrue(db.host.maxDirectTextBytes <= 7)
        }
    }

    @Test fun checksumAndProjectionMismatchAreDetectedAfterBoundedPayloadRead() = runBlocking {
        for (projection in listOf(false, true)) HostDeckDatabase(file()).use { db ->
            val library = library(db); val original = deck(); ok(library.create(original, operation(), cancel))
            if (projection) replacePayload(db, original.copy(name = "other")) else db.sql("UPDATE deck_revision SET payload=zeroblob(payload_bytes)")
            db.host.blobQueries = 0
            failure(library.load(original.id, cancel), FailureCode.CORRUPT_DATA); assertEquals(1, db.host.blobQueries)
        }
    }

    @Test fun codecFutureVersionIsUnsupportedAndNoReceiptEpochIsCorrupt() = runBlocking {
        HostDeckDatabase(file()).use { db ->
            val library = library(db); val original = deck(); ok(library.create(original, operation(), cancel))
            val bytes = ok(codec.encodeSnapshot(original)).copyBytes(); bytes[5] = 2
            storeBytes(db, bytes)
            assertEquals(UnavailableReason.UNSUPPORTED, (library.load(original.id, cancel) as Outcome.Unavailable).reason)
            db.sql("DELETE FROM deck_operation")
            failure(library.load(original.id, cancel), FailureCode.CORRUPT_DATA)
            failure(library.list(PageRequest(1), cancel), FailureCode.CORRUPT_DATA)
            failure(library.create(deck("new"), operation("new"), cancel), FailureCode.CORRUPT_DATA)
        }
    }

    @Test fun exhaustedReceiptSequenceAbortsBeforeAnyWrite() = runBlocking {
        HostDeckDatabase(file()).use { db ->
            val library = library(db); ok(library.create(deck(), operation(), cancel))
            db.sql("UPDATE deck_operation SET sequence=9223372036854775807")
            failure(library.create(deck("new"), operation("new"), cancel), FailureCode.PERSISTENCE)
            assertEquals(1L, db.count("deck_head")); assertEquals(1L, db.count("deck_operation"))
        }
    }

    @Test fun malformedHistoricalReceiptMetadataRejectedBeforeBlob() = runBlocking {
        for (edit in listOf("request_version=2", "request_sha256='invalid'", "kind='OTHER'", "result_revision='bad'", "deck_id=CAST(x'ff' AS TEXT)")) HostDeckDatabase(file()).use { db ->
            val library = library(db); val original = deck(); ok(library.create(original, operation(), cancel))
            // Foreign keys are deliberately disabled only for hostile storage-fixture corruption.
            db.sql("PRAGMA foreign_keys=OFF"); db.sql("UPDATE deck_operation SET $edit"); db.host.blobQueries = 0
            failure(library.create(original, operation(), cancel), FailureCode.CORRUPT_DATA); assertEquals(0, db.host.blobQueries)
        }
    }

    private fun replacePayload(db: HostDeckDatabase, snapshot: DeckSnapshot) = storeBytes(db, ok(codec.encodeSnapshot(snapshot)).copyBytes())
    private fun storeBytes(db: HostDeckDatabase, bytes: ByteArray) {
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val hex = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        db.sql("UPDATE deck_revision SET payload=x'$hex',payload_bytes=${bytes.size},payload_sha256='$sha'")
    }
    private fun assertEmpty(db: HostDeckDatabase) { for (table in listOf("deck_head", "deck_revision", "deck_operation")) assertEquals(table, 0L, db.count(table)) }
    private fun compareUtf8(a: String, b: String): Int {
        val x = a.toByteArray(); val y = b.toByteArray()
        for (i in 0 until minOf(x.size, y.size)) { val d = (x[i].toInt() and 255) - (y[i].toInt() and 255); if (d != 0) return d }
        return x.size - y.size
    }
}
