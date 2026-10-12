package com.architectpsg.commandlibrary.feature.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration

/** SYNTHETIC text only; verifies real SQLite persistence/FTS/activation, not genuine corpus coverage. */
@RunWith(AndroidJUnit4::class)
class LocalRulesInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val clock = object : Clock { private var now = 100L; override fun utcNow(): UtcTimestamp = UtcTimestamp(now++); override fun monotonicNow(): Duration = Duration.ZERO }
    private val ids = object : IdSource { override fun nextId(): String = UUID.randomUUID().toString() }
    private class Token : CancelToken { var cancelled = false; override val isCancelled: Boolean get() = cancelled; override fun throwIfCancelled() { if (cancelled) throw CancellationException("synthetic cancellation") } }
    private val provider = ProviderId("synthetic")
    private val unknownDate = Knowledge.Unknown("Synthetic unknown date", SnapshotList.empty())
    private fun source(bytes: ByteArray) = LocalRulesImportSource(ResourceUri("content://synthetic/rules"), "Synthetic rules", provider,
        version = Knowledge.Unknown("Synthetic unknown version", SnapshotList.empty()), effectiveAt = unknownDate, sourceDate = unknownDate,
        sourceUri = Knowledge.Absent("No upstream"), notice = Knowledge.Unknown("Unknown notice", SnapshotList.empty()), expectedChecksum = Knowledge.Absent("No expected hash"), expectedBytes = Knowledge.Known(bytes.size.toLong()))
    private fun text(word: String = "café") = ("SYNTHETIC RULES\n\n100. Synthetic General\n\n100.1. A $word synthetic rule. See rule 100.2.\n\n100.2. Another synthetic rule.\n\n100.3. Final synthetic rule.\n").toByteArray(Charsets.UTF_8)
    private fun chunks(bytes: ByteArray) = flowOf(ByteChunk(0, 0, SnapshotBytes(bytes), null, true))
    private suspend fun open(name: String): StorageDatabase = (StorageDatabase.open(context, name) as Outcome.Success).value
    private suspend fun install(adapter: LocalRules, bytes: ByteArray = text()): LocalRulesImportResult = (adapter.`import`(source(bytes), { chunks(bytes) }, {}, Token()) as Outcome.Success).value
    private fun query(text: String = "", corpus: GenerationId? = null, limit: Int = 100, cursor: String? = null) = RulesQuery(text, corpus, SnapshotList.empty(), PageRequest(limit, cursor))

    @Test fun installFtsPaginationReplacementAndCloseReopen() = runBlocking {
        val name = "rules-${UUID.randomUUID()}.db"; var db = open(name)
        try {
            var adapter = LocalRules(db, clock, ids)
            assertTrue(adapter.search(query(), Token()) is Outcome.Unavailable)
            val first = install(adapter)
            val found = (adapter.search(query("CAFE"), Token()) as Outcome.Success).value
            assertEquals(1, found.items.size); assertEquals(first.corpus.id, found.snapshot.generation)
            assertEquals("100.1", found.items.single().id.raw)
            val page = (adapter.search(query(limit = 1), Token()) as Outcome.Success).value
            assertNotNull(page.nextCursor)
            val second = (adapter.search(query(limit = 1, cursor = page.nextCursor), Token()) as Outcome.Success).value
            assertTrue(second.items.single().ordinal > page.items.single().ordinal)
            val changedQuery = adapter.search(query("synthetic", limit = 1, cursor = page.nextCursor), Token()) as Outcome.Failure
            assertEquals(FailureCode.INVALID_INPUT, changedQuery.error.code)
            val replacement = install(adapter, text("naïve"))
            assertNotEquals(first.corpus.id, replacement.corpus.id)
            assertEquals(FailureCode.STALE_GENERATION, (adapter.search(query(limit = 1, cursor = page.nextCursor), Token()) as Outcome.Failure).error.code)
            assertEquals(first.corpus.id, (adapter.entry(found.items.single().id, first.corpus.id, Token()) as Outcome.Success).value.corpusId)
            assertEquals(UnavailableReason.NOT_FOUND, (adapter.entry(RuleId(provider, "999.9"), replacement.corpus.id, Token()) as Outcome.Unavailable).reason)
            db.close(); db = open(name); adapter = LocalRules(db, clock, ids)
            val status = (adapter.status(Token()) as Outcome.Success).value
            assertEquals(replacement.corpus, (status.active as Knowledge.Known).value)
            assertEquals(JobPhase.COMPLETED, (status.latestAttempt as Knowledge.Known).value.phase)
            assertTrue((status.active as Knowledge.Known).value.source.version is Knowledge.Unknown)
            assertEquals(1, (adapter.search(query("NAIVE"), Token()) as Outcome.Success).value.items.size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun changedReplayAndExpectedMismatchPreserveActiveAndRecordFailure() = runBlocking {
        val name = "rules-${UUID.randomUUID()}.db"; val db = open(name)
        try {
            val adapter = LocalRules(db, clock, ids); val installed = install(adapter); val bytes = text(); var opens = 0
            val changed = adapter.`import`(source(bytes), { opens++; chunks(if (opens == 1) bytes else text("other")) }, {}, Token()) as Outcome.Failure
            assertEquals(FailureCode.SOURCE_MISMATCH, changed.error.code)
            assertEquals(installed.corpus.id, ((adapter.status(Token()) as Outcome.Success).value.active as Knowledge.Known).value.id)
            val expected = adapter.`import`(source(bytes).copy(expectedBytes = Knowledge.Known(1)), { chunks(bytes) }, {}, Token()) as Outcome.Failure
            assertEquals(FailureCode.SOURCE_MISMATCH, expected.error.code)
            val hash = adapter.`import`(source(bytes).copy(expectedChecksum = Knowledge.Known(ContentHash("SHA-256", "0".repeat(64)))), { chunks(bytes) }, {}, Token()) as Outcome.Failure
            assertEquals(FailureCode.SOURCE_MISMATCH, hash.error.code)
            val status = (adapter.status(Token()) as Outcome.Success).value
            assertEquals(JobPhase.FAILED, (status.latestAttempt as Knowledge.Known).value.phase)
            assertEquals(0, (db.query(rulesSql("SELECT generation_id FROM rules_corpus WHERE status='STAGING'", 100), Token()) as Outcome.Success).value.size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun cancellationDuringStagingAndBeforeActivationRollsBack() = runBlocking {
        val name = "rules-${UUID.randomUUID()}.db"; val db = open(name)
        try {
            val adapter = LocalRules(db, clock, ids); val installed = install(adapter); val bytes = text()
            for (phase in listOf(JobPhase.STAGING, JobPhase.ACTIVATING)) {
                val token = Token()
                try { adapter.`import`(source(bytes), { chunks(bytes) }, { if (it.phase == phase) token.cancelled = true }, token); fail("Expected cancellation") } catch (_: CancellationException) { }
                val status = (adapter.status(Token()) as Outcome.Success).value
                assertEquals(installed.corpus.id, (status.active as Knowledge.Known).value.id)
                assertEquals(JobPhase.CANCELLED, (status.latestAttempt as Knowledge.Known).value.phase)
                assertEquals(0, (db.query(rulesSql("SELECT generation_id FROM rules_corpus WHERE status='STAGING'", 100), Token()) as Outcome.Success).value.size)
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private class RejectCleanup(private val real: Database) : Database by real {
        override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> = real.transaction({ tx -> action(object : DbTransaction by tx {
            override suspend fun execute(request: SqlRequest, cancel: CancelToken): Outcome<DbWriteResult> =
                if (request.statement.startsWith("DELETE FROM rules_text_fts")) Outcome.Failure(DomainFailure(FailureCode.PERSISTENCE, "Synthetic deferred cleanup", SnapshotList.empty())) else tx.execute(request, cancel)
        }) }, cancel)
    }
    @Test fun reopeningRecoversInterruptedStagingAfterCleanupFailure() = runBlocking {
        val name = "rules-${UUID.randomUUID()}.db"; var db = open(name)
        try {
            val first = install(LocalRules(db, clock, ids)); val bytes = text(); val token = Token()
            try { LocalRules(RejectCleanup(db), clock, ids).`import`(source(bytes), { chunks(bytes) }, { if (it.phase == JobPhase.ACTIVATING) token.cancelled = true }, token); fail("Expected cancellation") } catch (_: CancellationException) { }
            assertEquals(1, (db.query(rulesSql("SELECT generation_id FROM rules_corpus WHERE status='STAGING'", 100), Token()) as Outcome.Success).value.size)
            db.close(); db = open(name)
            val recovered = (LocalRules(db, clock, ids).status(Token()) as Outcome.Success).value
            assertEquals(first.corpus.id, (recovered.active as Knowledge.Known).value.id)
            assertEquals(JobPhase.FAILED, (recovered.latestAttempt as Knowledge.Known).value.phase)
            assertEquals(FailureCode.PERSISTENCE, (recovered.latestAttempt as Knowledge.Known).value.failure!!.code)
            assertEquals(0, (db.query(rulesSql("SELECT generation_id FROM rules_corpus WHERE status='STAGING'", 100), Token()) as Outcome.Success).value.size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private class LateCommitCancellation(private val real: Database) : Database by real {
        override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
            var activates = false
            val result = real.transaction({ tx -> action(object : DbTransaction by tx {
                override suspend fun execute(request: SqlRequest, cancel: CancelToken): Outcome<DbWriteResult> {
                    if (request.statement.startsWith("INSERT INTO rules_active")) activates = true
                    return tx.execute(request, cancel)
                }
            }) }, cancel)
            if (activates && result is Outcome.Success) throw CancellationException("Synthetic late committed cancellation")
            return result
        }
    }
    @Test fun lateCommitCancellationNeverDeletesActiveCorpusOrFts() = runBlocking {
        val name = "rules-${UUID.randomUUID()}.db"; var db = open(name)
        try {
            val bytes = text(); val adapter = LocalRules(LateCommitCancellation(db), clock, ids)
            try { adapter.`import`(source(bytes), { chunks(bytes) }, {}, Token()); fail("Expected late cancellation") } catch (_: CancellationException) { }
            db.close(); db = open(name)
            val reopened = LocalRules(db, clock, ids); val status = (reopened.status(Token()) as Outcome.Success).value
            assertTrue(status.active is Knowledge.Known)
            assertEquals(JobPhase.COMPLETED, (status.latestAttempt as Knowledge.Known).value.phase)
            assertEquals(1, (reopened.search(query("cafe"), Token()) as Outcome.Success).value.items.size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
