package com.architectpsg.commandlibrary.data.storage.ai

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.HostLifeDatabase
import com.architectpsg.commandlibrary.data.storage.schema.Migration17To18
import com.architectpsg.commandlibrary.data.storage.ai.codec.AiHistoryPayloadCodec
import com.architectpsg.commandlibrary.feature.ai.LocalOfflineDeckPrompts
import com.architectpsg.commandlibrary.feature.ai.SqlLocalAiHistory
import com.architectpsg.commandlibrary.feature.decks.DeckSnapshotValidator
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Actual bundled SQLite, actual content codec and actual offline render/validator. No Room/UI claim. */
class SqlLocalAiHistoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private val deckId = DeckId(ProviderId("local"), "deck")
    private val epoch = EntityId("process-1")
    private val unknown = Knowledge.Unknown("Unknown", SnapshotList.empty())
    private class Time : Clock {
        var calls = 0
        override fun utcNow() = UtcTimestamp(1000L - calls++, 42) // Backwards wall time must not affect order.
        override fun monotonicNow(): Duration = Duration.ZERO
    }
    private suspend fun open(file: File) = HostLifeDatabase(file, initialize = false).also { Migration17To18.migrate(it.connection) }
    private fun repo(db: Database, time: Time = Time()) = SqlLocalAiHistory(db, AiHistoryPayloadCodec(), time)
    private fun <T> ok(result: Outcome<T>): T { assertTrue("Expected success: $result", result is Outcome.Success); return (result as Outcome.Success).value }
    private fun failed(result: Outcome<*>, code: FailureCode) { assertTrue("Expected $code: $result", result is Outcome.Failure); assertEquals(code, (result as Outcome.Failure).error.code) }
    private fun missing(result: Outcome<*>) { assertTrue(result is Outcome.Unavailable); assertEquals(UnavailableReason.NOT_FOUND, (result as Outcome.Unavailable).reason) }
    private suspend fun create(r: LocalAiHistory, name: String = "c", deck: DeckId = deckId) = ok(r.createConversation(NewConversation(EntityId(name), deck, OfflineLabMode.CHAT, "Exact 日本語 😀\r\n"), token))
    private fun provider(frame: OfflinePromptResult, name: String, retry: EntityId? = null, owner: EntityId = epoch) = NewHistoryTurn(EntityId(name), HistoryTurnSource.PROVIDER_GENERATION,
        frame.artifact, provenance(frame), "", EntityId("deleted-historical-profile"), "model", retry, owner)
    private fun offline(frame: OfflinePromptResult, name: String = "t", reply: String = "") = NewHistoryTurn(EntityId(name), HistoryTurnSource.OFFLINE_CAPTURE, frame.artifact, provenance(frame), reply)
    private fun provenance(frame: OfflinePromptResult) = HistoryPromptProvenance(hash(frame.template.body.toByteArray(Charsets.UTF_8)), frame.overrideUsed, frame.snapshot)
    private fun hash(bytes: ByteArray) = ContentHash("SHA-256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    private fun progress(text: String, status: TurnStatus = TurnStatus.STREAMING): HistoryProgress = HistoryProgress(status, text,
        Knowledge.Unknown(AiHistoryLimits.UNFINISHED_REASON, SnapshotList.empty()))
    private suspend fun frame(large: Boolean = false): OfflinePromptResult {
        val source = ProviderId("scryfall")
        val snapshot = SnapshotReference(source, GenerationId("g1"), null, LanguageTag.ENGLISH)
        val stamp = UtcTimestamp(123)
        val card = CardRecord(OracleId(source, "o"), "Card", Knowledge.Known(if (large) "R".repeat(110 * 1024) else "Rules"), unknown, unknown, unknown, unknown, unknown,
            CardStats(unknown, unknown, unknown, unknown), unknown, unknown, unknown, "", LanguageTag.ENGLISH,
            SnapshotList.empty(), SnapshotList.empty(), Provenance(source, null, Knowledge.Known("v1"), unknown, stamp, unknown, snapshot.generation, null, null), null)
        val query = object : LocalCardQuery {
            override suspend fun local(query: QueryAst, context: SearchContext, page: PageRequest, cancel: CancelToken): Outcome<Page<CardRecord>> =
                Outcome.Success(Page(SnapshotList.of(card), null, snapshot))
        }
        val identity = CardIdentity(Knowledge.Known(card.oracleId), unknown, null, Knowledge.Known("Card"), LanguageTag.ENGLISH)
        val deck = DeckSnapshot(deckId, "Deck", unknown, if (large) "D".repeat(1900 * 1024) else "Private description", unknown,
            SnapshotList.empty(), stamp, stamp, Revision(3), SnapshotList.copyOf(listOf(BoardRole.MAIN, BoardRole.SIDEBOARD).mapIndexed { i, board ->
                DeckEntry(EntityId("entry$i"), identity, 1, board, unknown, "PRIVATE NOTE", SnapshotList.empty(), SnapshotList.empty())
            }), unknown, null, SnapshotList.empty(), SnapshotList.empty())
        val engine = LocalOfflineDeckPrompts(query, Time(), DeckSnapshotValidator::validate)
        return ok(engine.render(OfflinePromptRequest(deck, OfflineLabMode.CHAT, RoastIntensity.GENTLE, "Question 日本語 😀", "Budget", true, snapshot, null), token))
    }

    @Test fun actualMultiMibArtifactAndBindingsRoundTripReopenAndSourceQualifiedIsolation() = runBlocking {
        val artifact = frame(large = true)
        assertTrue(artifact.artifact.rendered.toByteArray(Charsets.UTF_8).size > 2 * 1024 * 1024)
        val file = folder.newFile(); lateinit var saved: Turn
        open(file).use { db ->
            val r = repo(db); val parent = create(r)
            create(r, "foreign", deckId.copy(source = ProviderId("foreign")))
            saved = ok(r.appendTurn(parent.id, parent.revision, offline(artifact, reply = "Exact reply 😀\r\n"), token))
            assertEquals(saved, ok(r.getTurn(saved.id, token)))
            assertEquals(artifact.artifact, saved.prompt)
            assertEquals(listOf(parent.id), ok(r.pageConversations(deckId, PageRequest(100), token)).items.map { it.id })
            assertEquals(1L, db.scalar("SELECT count(*) FROM ai_turn WHERE payload_length>4194304"))
        }
        HostLifeDatabase(file, initialize = false).use { db -> assertEquals(saved, ok(repo(db).getTurn(saved.id, token))) }
    }

    @Test fun headerAndMetadataOnlyTurnPagesFenceMutationAndCrossScopeEvenAfterEnd() = runBlocking {
        val frame = frame()
        open(folder.newFile()).use { db ->
            val r = repo(db); val a = create(r, "a"); create(r, "b"); val c = create(r, "c")
            val page = ok(r.pageConversations(deckId, PageRequest(1), token)); assertNotNull(page.nextCursor)
            failed(r.pageConversations(deckId.copy(source = ProviderId("foreign")), PageRequest(1, page.nextCursor), token), FailureCode.STALE_REVISION)
            val t1 = ok(r.appendTurn(a.id, a.revision, offline(frame, "t1"), token))
            val current = ok(r.getConversation(a.id, token)); ok(r.appendTurn(a.id, current.revision, offline(frame, "t2"), token))
            failed(r.pageConversations(deckId, PageRequest(1, page.nextCursor), token), FailureCode.STALE_REVISION)
            db.blobQueries = 0
            val turns = ok(r.pageTurns(a.id, PageRequest(1), token)); assertEquals(t1.id, turns.items.single().id); assertNotNull(turns.nextCursor)
            assertEquals(0, db.blobQueries)
            failed(r.pageTurns(c.id, PageRequest(1, turns.nextCursor), token), FailureCode.STALE_REVISION)
            assertEquals(EntityId("t2"), ok(r.pageTurns(a.id, PageRequest(1, turns.nextCursor), token)).items.single().id)
            ok(r.appendTurn(a.id, ok(r.getConversation(a.id, token)).revision, offline(frame, "t3"), token))
            failed(r.pageTurns(a.id, PageRequest(1, turns.nextCursor), token), FailureCode.STALE_REVISION)
            failed(r.pageTurns(a.id, PageRequest(1, "ah1:%%%"), token), FailureCode.INVALID_INPUT)
        }
    }

    @Test fun checkpointsEnforceCasPrefixImmutablePromptTerminalAndExplicitRetry() = runBlocking {
        val frame = frame()
        open(folder.newFile()).use { db ->
            val time = Time(); val r = repo(db, time); val c = create(r)
            val pending = ok(r.appendTurn(c.id, c.revision, provider(frame, "t"), token))
            failed(r.checkpoint(pending.id, pending.revision, epoch, progress("invalid pending", TurnStatus.PENDING), token), FailureCode.INVALID_INPUT)
            val streaming = ok(r.checkpoint(pending.id, pending.revision, epoch, progress("partial 😀"), token))
            val calls = time.calls
            failed(r.checkpoint(pending.id, pending.revision, epoch, progress("bad", TurnStatus.PENDING), token), FailureCode.STALE_REVISION)
            assertEquals(streaming, ok(r.checkpoint(streaming.id, streaming.revision, epoch, progress(streaming.rawContent), token)))
            assertEquals(calls, time.calls)
            failed(r.checkpoint(pending.id, pending.revision, epoch, progress(streaming.rawContent), token), FailureCode.STALE_REVISION)
            failed(r.checkpoint(streaming.id, streaming.revision, epoch, progress("rewritten"), token), FailureCode.INVALID_INPUT)
            failed(r.checkpoint(streaming.id, streaming.revision, epoch, progress("", TurnStatus.PENDING), token), FailureCode.INVALID_INPUT)
            val done = ok(r.checkpoint(streaming.id, streaming.revision, epoch, progress("partial 😀 done", TurnStatus.COMPLETE), token))
            assertEquals(pending.prompt, done.prompt); assertEquals(pending.provenance, done.provenance)
            failed(r.checkpoint(done.id, done.revision, epoch, progress(done.rawContent, TurnStatus.COMPLETE), token), FailureCode.STALE_REVISION)
            val parent = ok(r.getConversation(c.id, token))
            val retry = ok(r.appendTurn(c.id, parent.revision, provider(frame, "retry", done.id), token))
            assertEquals(2L, retry.ordinal); assertEquals(done.id, retry.retryOf); assertEquals(done, ok(r.getTurn(done.id, token)))
            failed(r.appendTurn(c.id, ok(r.getConversation(c.id, token)).revision, provider(frame, "badRetry", retry.id), token), FailureCode.INVALID_INPUT)
            failed(r.appendTurn(c.id, c.revision, offline(frame, "stale"), token), FailureCode.STALE_REVISION)
        }
    }

    @Test fun sameEpochRecoveryNoopsForeignRecoveryPreservesBytesAndRejectsOldCallbacks() = runBlocking {
        val frame = frame(); val file = folder.newFile(); lateinit var partial: Turn
        open(file).use { db ->
            val r = repo(db); val parent = create(r)
            val pending = ok(r.appendTurn(parent.id, parent.revision, provider(frame, "first"), token))
            partial = ok(r.checkpoint(pending.id, pending.revision, epoch, progress("exact partial 日本語 😀\r\n"), token))
            ok(r.appendTurn(parent.id, ok(r.getConversation(parent.id, token)).revision, provider(frame, "second"), token))
        }
        HostLifeDatabase(file, initialize = false).use { db ->
            val time = Time(); val r = repo(db, time); val before = ok(r.getConversation(partial.conversationId, token))
            assertEquals(0L, ok(r.recover(epoch, token))); assertEquals(0, time.calls)
            val bytes = payload(db, partial.id)
            db.blobQueries = 0
            assertEquals(2L, ok(r.recover(EntityId("process-2"), token))); assertEquals(0, db.blobQueries)
            assertEquals(1, time.calls); assertArrayEquals(bytes, payload(db, partial.id))
            val recovered = ok(r.getTurn(partial.id, token)); assertEquals(TurnStatus.INTERRUPTED, recovered.status)
            assertEquals(partial.rawContent, recovered.rawContent); assertEquals(partial.prompt, recovered.prompt)
            assertEquals(partial.revision.next(), recovered.revision)
            assertEquals(before.revision.next(), ok(r.getConversation(before.id, token)).revision)
            failed(r.checkpoint(partial.id, partial.revision, epoch, progress("exact partial 日本語 😀\r\nlate"), token), FailureCode.STALE_REVISION)
            assertEquals(0L, ok(r.recover(EntityId("process-2"), token))); assertEquals(1, time.calls)
        }
    }
    private fun payload(db: HostLifeDatabase, id: EntityId): ByteArray = db.connection.prepare("SELECT payload FROM ai_turn WHERE turn_id=?").use { it.bindText(1, id.value); assertTrue(it.step()); it.getBlob(0) }

    @Test fun deletionClearsEveryChildAndPrivateProjectionAndPermanentlyReservesIds() = runBlocking {
        val frame = frame()
        open(folder.newFile()).use { db ->
            val r = repo(db); val parent = create(r); val other = create(r, "other")
            val first = ok(r.appendTurn(parent.id, parent.revision, offline(frame, "first", "PRIVATE REPLY"), token))
            ok(r.appendTurn(parent.id, ok(r.getConversation(parent.id, token)).revision, provider(frame, "second"), token))
            val revision = ok(r.getConversation(parent.id, token)).revision
            failed(r.deleteConversation(parent.id, parent.revision, token), FailureCode.STALE_REVISION)
            ok(r.deleteConversation(parent.id, revision, token))
            missing(r.getConversation(parent.id, token)); missing(r.getTurn(first.id, token)); missing(r.pageTurns(parent.id, PageRequest(1), token))
            missing(r.checkpoint(first.id, first.revision, epoch, progress("late"), token))
            assertEquals(2L, db.scalar("SELECT count(*) FROM ai_turn WHERE deleted=1 AND payload IS NULL AND payload_length=0 AND source='' AND status='' AND owner_epoch IS NULL"))
            assertEquals(1L, db.scalar("SELECT count(*) FROM ai_conversation WHERE deleted=1 AND payload IS NULL AND payload_length=0 AND deck_source='' AND deck_raw='' AND mode=''"))
            failed(r.createConversation(NewConversation(parent.id, deckId, OfflineLabMode.CHAT, "reuse"), token), FailureCode.STALE_REVISION)
            failed(r.appendTurn(other.id, other.revision, offline(frame, "first"), token), FailureCode.STALE_REVISION)
            assertEquals(other, ok(r.getConversation(other.id, token))); assertEquals(0L, ok(r.recover(EntityId("new"), token)))
        }
    }

    @Test fun metadataCorruptionOversizeTypesAndFutureVersionFailBeforeBlobRead() = runBlocking {
        val frame = frame()
        for (mutation in listOf("payload=zeroblob(8388609),payload_length=8388609", "payload='text'", "payload_length='bad'", "payload_sha256='bad'", "revision=0", "ordinal=0", "owner_epoch=x'ff'", "finished_seconds=1", "payload_version=2")) {
            open(folder.newFile()).use { db ->
                val r = repo(db); val c = create(r); val t = ok(r.appendTurn(c.id, c.revision, provider(frame, "t"), token))
                db.sql("UPDATE ai_turn SET $mutation"); db.blobQueries = 0
                val result = r.getTurn(t.id, token)
                if (mutation == "payload_version=2") { assertTrue(result is Outcome.Unavailable); assertEquals(UnavailableReason.UNSUPPORTED, (result as Outcome.Unavailable).reason) }
                else failed(result, FailureCode.CORRUPT_DATA)
                assertEquals("Read BLOB after $mutation", 0, db.blobQueries)
            }
        }
        open(folder.newFile()).use { db ->
            val r = repo(db); val c = create(r); val t = ok(r.appendTurn(c.id, c.revision, offline(frame), token))
            db.sql("UPDATE ai_turn SET payload_sha256='${"0".repeat(64)}'"); db.blobQueries = 0
            failed(r.getTurn(t.id, token), FailureCode.CORRUPT_DATA); assertEquals(1, db.blobQueries)
            db.sql("UPDATE ai_conversation SET payload=zeroblob(4097),payload_length=4097"); db.blobQueries = 0
            failed(r.getConversation(c.id, token), FailureCode.CORRUPT_DATA); assertEquals(0, db.blobQueries)
        }
    }

    @Test fun overflowAndRecoveryPreflightDoNotWriteOrReadClockAndUnexpectedCountsRollback() = runBlocking {
        val frame = frame()
        open(folder.newFile()).use { db ->
            val time = Time(); val r = repo(db, time); val parent = create(r)
            val t = ok(r.appendTurn(parent.id, parent.revision, provider(frame, "t"), token)); val calls = time.calls
            db.sql("UPDATE ai_turn SET revision=9223372036854775807")
            failed(r.recover(EntityId("new"), token), FailureCode.INVALID_INPUT); assertEquals(calls, time.calls)
            assertEquals(1L, db.scalar("SELECT count(*) FROM ai_turn WHERE status='PENDING'"))
            db.sql("UPDATE ai_turn SET revision=1"); db.sql("UPDATE ai_conversation SET change_sequence=9223372036854775807")
            failed(r.checkpoint(t.id, t.revision, epoch, progress("x"), token), FailureCode.INVALID_INPUT); assertEquals(calls, time.calls)
            failed(r.deleteConversation(parent.id, Revision(2), token), FailureCode.INVALID_INPUT); assertEquals(calls, time.calls)
        }
        open(folder.newFile()).use { db ->
            val r = repo(db); val parent = create(r)
            val badCounts = object : Database by db {
                override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> = db.transaction({ tx ->
                    action(object : DbTransaction by tx {
                        override suspend fun execute(request: SqlRequest, cancel: CancelToken): Outcome<DbWriteResult> {
                            val result = tx.execute(request, cancel)
                            return if (result is Outcome.Success) Outcome.Success(DbWriteResult(0)) else result
                        }
                    })
                }, cancel)
            }
            failed(repo(badCounts).appendTurn(parent.id, parent.revision, offline(frame), token), FailureCode.STALE_REVISION)
            assertEquals(0L, db.scalar("SELECT count(*) FROM ai_turn")); assertEquals(parent, ok(r.getConversation(parent.id, token)))
        }
    }

    @Test fun invalidInputAndCancellationBeforeIoDuringWriteAndAfterCommitRequireExplicitReload() = runBlocking {
        val frame = frame(); val file = folder.newFile()
        val noIo = object : Database {
            override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> = error("Unexpected I/O")
            override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> = error("Unexpected transaction")
            override suspend fun capabilities(cancel: CancelToken): Outcome<DbCapabilities> = error("Unexpected capabilities")
        }
        failed(repo(noIo).createConversation(NewConversation(EntityId("bad"), deckId, OfflineLabMode.CHAT, "\uD800"), token), FailureCode.INVALID_INPUT)
        failed(repo(noIo).appendTurn(EntityId("c"), Revision(1), offline(frame).copy(rawContent = "x".repeat(262145)), token), FailureCode.INVALID_INPUT)
        open(file).use { db ->
            val r = repo(db); val c = create(r)
            var stopped = true
            val cancel = object : CancelToken { override val isCancelled get() = stopped; override fun throwIfCancelled() { if (stopped) throw CancellationException("Synthetic cancellation") } }
            try { r.appendTurn(c.id, c.revision, offline(frame), cancel); fail("Expected cancellation") } catch (_: CancellationException) { }
            stopped = false; db.afterWrite = { stopped = true }
            try { r.appendTurn(c.id, c.revision, offline(frame), cancel); fail("Expected cancellation") } catch (_: CancellationException) { }
            assertEquals(0L, db.scalar("SELECT count(*) FROM ai_turn")); assertEquals(c, ok(r.getConversation(c.id, token)))
            db.afterWrite = null
            val afterCommit = object : Database by db {
                override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> {
                    val result = db.transaction(action, cancel)
                    if (result is Outcome.Success) throw CancellationException("After commit")
                    return result
                }
            }
            try { repo(afterCommit).appendTurn(c.id, c.revision, offline(frame), token); fail("Expected cancellation") } catch (_: CancellationException) { }
        }
        HostLifeDatabase(file, initialize = false).use { db ->
            val r = repo(db); assertEquals(EntityId("t"), ok(r.getTurn(EntityId("t"), token)).id)
            failed(r.appendTurn(EntityId("c"), Revision(2), offline(frame), token), FailureCode.STALE_REVISION)
        }
    }
}
