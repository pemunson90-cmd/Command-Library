package com.architectpsg.commandlibrary.data.storage.life

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.feature.life.persistence.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration.Companion.seconds

/** Live pages intentionally expose latest committed state, not a cross-page snapshot. */
class LiveCommandHistoryTest {
    @get:Rule val folder=TemporaryFolder()
    private val f=LifeMutationFixtures
    private val codec=LifePayloadCodec()
    private class Ids(val raw:String):IdSource { var n=0;override fun nextId()=if(++n==1)raw else "$raw-identity-$n" }
    private val random=object:RandomSource { override fun nextInt(upperBound:Int):Int=error("History test must not draw") }
    private fun reading(n:Long)=GameTimeObservation(n.seconds,UtcTimestamp(1000+n),f.process)
    private fun <T> ok(value:Outcome<T>):T { assertTrue("Expected success, got $value",value is Outcome.Success);return (value as Outcome.Success).value }
    @Test fun keysetPagesSurviveFinishReopenAndUndoOfCursorAndUnvisitedGames()=runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            val ids=mutableMapOf<String,Ids>();val commands=mutableMapOf<String,SqlLifeGameCommands>();val games=mutableMapOf<String,StoredLifeGame>()
            for((index,raw) in listOf("a","b","c").withIndex()) {
                val source=Ids(raw);ids[raw]=source;commands[raw]=SqlLifeGameCommands(db,codec,source,random)
                games[raw]=ok(SqlLifeGameLifecycle(db,codec,source,f.provider).create(f.input().copy(operationId=EntityId("create-$raw")),reading(index+1L),f.token))
            }
            suspend fun command(raw:String,action:GameAction):StoredLifeGame {
                val before=games.getValue(raw)
                return ok(commands.getValue(raw).dispatch(before.checkpoint.snapshot.id,action,before.checkpoint.snapshot.revision,reading(10),f.token)).also { games[raw]=it }
            }
            suspend fun undo(raw:String):StoredLifeGame {
                val before=games.getValue(raw)
                return ok(commands.getValue(raw).undo(before.checkpoint.snapshot.id,before.checkpoint.snapshot.revision,reading(11),f.token)).also { games[raw]=it }
            }
            val history=SqlLifeGameHistory(db,codec);val filter=GameHistoryFilter(playerName="A")
            val finish=GameAction.Finish(SnapshotList.empty(),"Draw")
            command("c",finish)
            val first=ok(history.history(filter,PageRequest(1),f.token))
            assertEquals(listOf("c"),first.games.items.map { it.gameId.raw });assertEquals(3L,first.totalMatches)
            val firstResult=first.games.items.single().result
            command("c",GameAction.Reopen("Cursor game correction"));command("b",finish)
            val second=ok(history.history(filter,PageRequest(1,first.games.nextCursor!!),f.token))
            assertEquals(listOf("b"),second.games.items.map { it.gameId.raw });assertEquals(3L,second.totalMatches)
            assertEquals(games.getValue("b").checkpoint.snapshot.result,second.games.items.single().result)
            assertFalse((second.games.items.single().result as Knowledge.Known).value.retracted)
            undo("b");command("a",finish)
            val third=ok(history.history(filter,PageRequest(1,second.games.nextCursor!!),f.token))
            assertEquals(listOf("a"),third.games.items.map { it.gameId.raw });assertEquals(3L,third.totalMatches);assertNull(third.games.nextCursor)
            assertEquals(games.getValue("a").checkpoint.snapshot.result,third.games.items.single().result)
            val latest=ok(history.history(filter,PageRequest(10),f.token))
            assertEquals(listOf("c","b","a"),latest.games.items.map { it.gameId.raw });assertEquals(3L,latest.totalMatches)
            assertTrue((latest.games.items[0].result as Knowledge.Known).value.retracted)
            assertTrue((latest.games.items[1].result as Knowledge.Known).value.retracted)
            assertFalse((latest.games.items[2].result as Knowledge.Known).value.retracted)
            assertEquals(firstResult,first.games.items.single().result)
            assertFalse((firstResult as Knowledge.Known).value.retracted)
            assertEquals(3L,db.scalar("SELECT count(*) FROM life_result"))
            assertEquals(3,(first.games.items+second.games.items+third.games.items).map { it.gameId }.toSet().size)
        }
    }
}
