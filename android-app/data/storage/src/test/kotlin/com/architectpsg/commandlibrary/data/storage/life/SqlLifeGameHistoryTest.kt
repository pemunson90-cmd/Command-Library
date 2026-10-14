package com.architectpsg.commandlibrary.data.storage.life

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.feature.life.persistence.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SqlLifeGameHistoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val f = LifeMutationFixtures
    private val codec = LifePayloadCodec()
    private fun <T> ok(outcome: Outcome<T>): T { assertTrue("Expected success, got $outcome", outcome is Outcome.Success); return (outcome as Outcome.Success).value }
    private fun failed(outcome: Outcome<*>, code: FailureCode) { assertTrue("Expected $code, got $outcome", outcome is Outcome.Failure); assertEquals(code, (outcome as Outcome.Failure).error.code) }
    private suspend fun create(db: HostLifeDatabase, raw: String, start: UtcTimestamp, player: String = "Alice", deck: Knowledge<DeckReference> = Knowledge.Absent("Unlinked"),
        settings: GameSettings = f.input().settings): StoredLifeGame {
        val input = f.input().copy(operationId = EntityId("creation-${raw.hashCode()}-${start.epochSeconds}-${start.nanoAdjustment}"), settings = settings,
            seats = SnapshotList.copyOf(f.input().seats.mapIndexed { index, seat -> if(index == 0) seat.copy(playerName = player) else seat.copy(playerName = "Bob", deck = deck) }))
        val initial = f.initial(input).copy(snapshot = f.initial(input).snapshot.copy(id = GameId(f.provider, raw), startedAt = start))
        return ok(SqlLifeGameStore(db, codec).create(input, { Outcome.Success(initial) }, f.token))
    }
    private fun deck(name: String = "Stored Deck", tombstoned: Boolean = false, raw: String = "deck") = Knowledge.Known(DeckReference(DeckId(f.provider, raw), Revision(1), name, tombstoned))
    private suspend fun history(db: HostLifeDatabase, filter: GameHistoryFilter = GameHistoryFilter(), page: PageRequest = PageRequest(100)) = ok(SqlLifeGameHistory(db, codec).history(filter, page, f.token))
    private fun ids(page: GameHistoryPage) = page.games.items.map { it.gameId.raw }
    @Test fun playerDeckDateFiltersAndCountsCombineAtGameLevelWithStoredTombstonedNames() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db, "one", UtcTimestamp(1), "Alice", deck("Archived Deck", true))
            create(db, "two", UtcTimestamp(2), "Carol", deck("Other", raw = "other"))
            create(db, "three", UtcTimestamp(3), "Alice")
            assertEquals(listOf("three", "two", "one"), ids(history(db)))
            val player = history(db, GameHistoryFilter(playerName = "alice")); assertEquals(listOf("three", "one"), ids(player)); assertEquals(2L, player.totalMatches)
            val combined = history(db, GameHistoryFilter(playerName = "Alice", deckId = DeckId(f.provider, "deck"), deckName = "archived", fromInclusive = UtcTimestamp(1), untilExclusive = UtcTimestamp(2)))
            assertEquals(listOf("one"), ids(combined)); assertEquals(1L, combined.totalMatches)
            assertTrue(((combined.games.items.single().seats[1].deck as Knowledge.Known).value).tombstoned)
            assertTrue(history(db, GameHistoryFilter(playerName = "Nobody")).games.items.isEmpty())
            assertEquals(0L, history(db, GameHistoryFilter(playerName = "Nobody")).totalMatches)
        }
    }
    @Test fun unicodeLiteralMatchingAndHalfOpenSubsecondBoundsRemainExact() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db, "one", UtcTimestamp(1, 100), "Élodie %_", deck("", true))
            create(db, "two", UtcTimestamp(1, 200), "Elodie xx")
            assertEquals(listOf("one"), ids(history(db, GameHistoryFilter(playerName = "E\u0301LODIE"))))
            assertEquals(listOf("one"), ids(history(db, GameHistoryFilter(playerName = "%_"))))
            assertEquals(listOf("one"), ids(history(db, GameHistoryFilter(deckName = ""))))
            assertEquals(listOf("one"), ids(history(db, GameHistoryFilter(fromInclusive = UtcTimestamp(1, 100), untilExclusive = UtcTimestamp(1, 200)))))
            assertEquals(0L, history(db, GameHistoryFilter(fromInclusive = UtcTimestamp(1, 100), untilExclusive = UtcTimestamp(1, 100))).totalMatches)
        }
    }
    @Test fun paginationUsesDescendingStableKeysAndFullCountWithFilterBoundCanonicalCursor() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            for(raw in listOf("a", "c", "b")) create(db, raw, UtcTimestamp(1))
            val first = history(db, GameHistoryFilter(playerName = "ALICE"), PageRequest(1)); assertEquals(listOf("c"), ids(first)); assertEquals(3L, first.totalMatches)
            val cursor = first.games.nextCursor!!
            val second = history(db, GameHistoryFilter(playerName = "alice"), PageRequest(2, cursor)); assertEquals(listOf("b", "a"), ids(second)); assertEquals(3L, second.totalMatches)
            assertNull(second.games.nextCursor)
            failed(SqlLifeGameHistory(db, codec).history(GameHistoryFilter(playerName = "Bob"), PageRequest(1, cursor), f.token), FailureCode.INVALID_INPUT)
            failed(SqlLifeGameHistory(db, codec).history(GameHistoryFilter(playerName = "alice"), PageRequest(1, cursor + ":extra"), f.token), FailureCode.INVALID_INPUT)
        }
    }
    @Test fun longUnicodeIdentityFitsCompactCursorAndEditedAnchorNeedNotStillMatch() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db, "a", UtcTimestamp(1))
            val longId = "長".repeat(20_000)
            val anchor = create(db, longId, UtcTimestamp(1))
            val first = history(db, GameHistoryFilter(playerName = "Alice"), PageRequest(1))
            assertEquals(listOf(longId), ids(first)); val cursor = first.games.nextCursor!!; assertTrue(cursor.toByteArray().size <= PageRequest.MAX_CURSOR_BYTES)
            ok(SqlLifeGameMutator(db, codec).action(anchor.checkpoint.snapshot.id, Revision(0), 0, { old, event ->
                Outcome.Success(f.action(old, event, GameAction.RenameSeat(f.seat, "Renamed")) { snapshot -> snapshot.copy(seats = SnapshotList.copyOf(snapshot.seats.map { if(it.id == f.seat) it.copy(playerName = "Renamed") else it })) })
            }, f.token))
            val second = history(db, GameHistoryFilter(playerName = "Alice"), PageRequest(1, cursor)); assertEquals(listOf("a"), ids(second)); assertEquals(1L, second.totalMatches)
        }
    }
    @Test fun deletedAnchorIsStaleAndSelectedOversizedMetadataFailsBeforePayload() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db, "a", UtcTimestamp(1)); create(db, "b", UtcTimestamp(2))
            val first = history(db, page = PageRequest(1)); val cursor = first.games.nextCursor!!
            db.sql("DELETE FROM life_game WHERE game_id='b'")
            failed(SqlLifeGameHistory(db, codec).history(GameHistoryFilter(), PageRequest(1, cursor), f.token), FailureCode.STALE_REVISION)
            db.sql("UPDATE life_game SET checkpoint_payload=zeroblob(4259841) WHERE game_id='a'")
            val reads = db.blobQueries
            failed(SqlLifeGameHistory(db, codec).history(GameHistoryFilter(), PageRequest(1), f.token), FailureCode.CORRUPT_DATA)
            assertEquals(reads, db.blobQueries)
        }
    }
    @Test fun byteBudgetReturnsAdvancingPrefixWithoutChangingFilteredTotal() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val life = f.input().settings.counterDefinitions.single()
            val labels = "p".repeat(60_000)
            val defs = SnapshotList.copyOf(listOf(life) + (1..60).map { CounterDefinition(EntityId("extra-$it"), labels, null, null, 0, CounterLossPolicy.None, true) })
            val settings = f.input().settings.copy(counterDefinitions = defs)
            repeat(5) { create(db, "game-$it", UtcTimestamp(it.toLong()), settings = settings) }
            assertTrue(db.scalar("SELECT sum(length(creation_payload)+length(checkpoint_payload)) FROM life_game") > 32L * 1024 * 1024)
            val first = history(db); assertEquals(5L, first.totalMatches); assertTrue(first.games.items.size in 1..4)
            val next = history(db, page = PageRequest(100, first.games.nextCursor!!))
            assertEquals(5L, next.totalMatches); assertEquals(5, (ids(first) + ids(next)).toSet().size); assertNull(next.games.nextCursor)
        }
    }
    @Test fun largeTimestampBucketAndNonBmpOrderingCrossAnchorLookupPages() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db, "😀", UtcTimestamp(1)); create(db, "\uE000", UtcTimestamp(1))
            repeat(102) { create(db, "bucket-${it.toString().padStart(3, '0')}", UtcTimestamp(1)) }
            val first = history(db, page = PageRequest(100))
            assertEquals(listOf("😀", "\uE000"), ids(first).take(2)); assertEquals(104L, first.totalMatches)
            val second = history(db, page = PageRequest(1, first.games.nextCursor!!))
            val third = history(db, page = PageRequest(100, second.games.nextCursor!!))
            assertEquals(104, (ids(first) + ids(second) + ids(third)).toSet().size); assertNull(third.games.nextCursor)
        }
    }
    @Test fun drawRetractionAndUnknownDeckKnowledgeRemainExactWithoutDuplicateGameCounts() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val unknownDeck = Knowledge.Unknown("Unresolved historical deck", SnapshotList.empty())
            val original = create(db, "draw", UtcTimestamp(1), "Bob", unknownDeck)
            val outcomes = SnapshotList.copyOf(original.checkpoint.snapshot.seats.map { SeatResult(it.id, SeatResultKind.DRAW, null) })
            val finished = ok(SqlLifeGameMutator(db, codec).action(original.checkpoint.snapshot.id, Revision(0), 0, { old, event ->
                val result = GameResult(EntityId("result"), old.checkpoint.snapshot.id, SnapshotList.empty(), UtcTimestamp(1001), "Exact draw", Revision(1), false, outcomes)
                Outcome.Success(f.action(old, event, GameAction.FinishWithOutcomes(outcomes, "Exact draw")) {
                    it.copy(status = GameStatus.COMPLETED, endedAt = Knowledge.Known(result.finishedAt), result = Knowledge.Known(result)) })
            }, f.token))
            val page = history(db, GameHistoryFilter(playerName = "bob"))
            assertEquals(1L, page.totalMatches); assertEquals(1, page.games.items.size)
            assertEquals(finished.checkpoint.snapshot.result, page.games.items.single().result)
            assertEquals(finished.checkpoint.snapshot.endedAt, page.games.items.single().endedAt)
            assertEquals(unknownDeck, page.games.items.single().seats[1].deck)
            assertEquals(0L, history(db, GameHistoryFilter(deckName = "")).totalMatches)
            val reopened = ok(SqlLifeGameMutator(db, codec).action(original.checkpoint.snapshot.id, Revision(1), finished.sequence, { old, event ->
                Outcome.Success(f.action(old, event, GameAction.Reopen("Correction")) {
                    it.copy(status = GameStatus.PAUSED, endedAt = Knowledge.Absent("Reopened"), result = Knowledge.Known((it.result as Knowledge.Known).value.copy(revision = Revision(2), retracted = true))) })
            }, f.token))
            val latest = history(db).games.items.single()
            assertEquals(reopened.checkpoint.snapshot.result, latest.result); assertEquals(reopened.checkpoint.snapshot.endedAt, latest.endedAt)
        }
    }
    @Test fun corruptLookaheadMetadataFailsBeforeAnyPayloadAndNullEmptyFilterBindingDiffers() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db, "a", UtcTimestamp(1)); create(db, "b", UtcTimestamp(2))
            val cursor = history(db, page = PageRequest(1)).games.nextCursor!!
            failed(SqlLifeGameHistory(db, codec).history(GameHistoryFilter(playerName = ""), PageRequest(1, cursor), f.token), FailureCode.INVALID_INPUT)
            db.sql("UPDATE life_game SET creation_operation_id=CAST(X'FF' AS TEXT) WHERE game_id='a'")
            val reads = db.blobQueries
            failed(SqlLifeGameHistory(db, codec).history(GameHistoryFilter(), PageRequest(1), f.token), FailureCode.CORRUPT_DATA)
            assertEquals(reads, db.blobQueries)
        }
    }
    @Test fun cancellationDuringSelectedPayloadReadPropagatesWithoutPartialPage() = runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val original = create(db, "one", UtcTimestamp(1))
            val wrapper = object : Database by db {
                override suspend fun <T> transaction(action: suspend (DbTransaction) -> T, cancel: CancelToken): Outcome<T> = db.transaction({ tx ->
                    action(object : DbTransaction by tx {
                        override suspend fun query(request: SqlRequest, cancel: CancelToken): Outcome<SnapshotList<RowData>> {
                            if(request.statement.startsWith("SELECT creation_payload FROM life_game")) throw CancellationException("Synthetic history payload cancellation")
                            return tx.query(request, cancel)
                        }
                    })
                }, cancel)
            }
            try { SqlLifeGameHistory(wrapper, codec).history(GameHistoryFilter(), PageRequest(100), f.token); fail("Cancellation swallowed") }
            catch(expected: CancellationException) { }
            assertEquals(original, ok(SqlLifeGameStore(db, codec).read(original.checkpoint.snapshot.id, f.token)))
        }
    }
}
