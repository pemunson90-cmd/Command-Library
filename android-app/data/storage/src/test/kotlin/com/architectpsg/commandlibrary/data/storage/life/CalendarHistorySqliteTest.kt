package com.architectpsg.commandlibrary.data.storage.life

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.life.codec.LifePayloadCodec
import com.architectpsg.commandlibrary.feature.life.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration.Companion.seconds

class CalendarHistorySqliteTest {
    @get:Rule val folder=TemporaryFolder()
    private val f=LifeMutationFixtures
    private val codec=LifePayloadCodec()
    private fun <T> ok(value:Outcome<T>):T { assertTrue("Expected success, got $value",value is Outcome.Success);return (value as Outcome.Success).value }
    private suspend fun create(db:HostLifeDatabase,raw:String,time:UtcTimestamp) {
        val ids=object:IdSource { var n=0;override fun nextId()=if(++n==1)raw else "$raw-identity-$n" }
        ok(SqlLifeGameLifecycle(db,codec,ids,f.provider).create(f.input().copy(operationId=EntityId("create-$raw")),GameTimeObservation(0.seconds,time,f.process),f.token))
    }
    private fun stamp(text:String)=Instant.parse(text).let { UtcTimestamp(it.epochSecond,it.nano) }
    @Test fun inclusiveDayFilterIncludesBothEndDaysAcrossDstAndExcludesExactNextMidnight()=runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db,"before",stamp("2024-03-09T04:59:59.999999999Z"))
            create(db,"first",stamp("2024-03-09T05:00:00Z"))
            create(db,"last",stamp("2024-03-11T03:59:59.999999999Z"))
            create(db,"after",stamp("2024-03-11T04:00:00Z"))
            val applied=ok(CalendarGameHistoryFilter.create(GameHistoryFilter(playerName="A"),LocalDate.of(2024,3,9),LocalDate.of(2024,3,10),ZoneId.of("America/New_York")))
            val result=ok(SqlLifeGameHistory(db,codec).history(applied.query,PageRequest(10),f.token))
            assertEquals(listOf("last","first"),result.games.items.map { it.gameId.raw });assertEquals(2L,result.totalMatches)
            assertEquals(stamp("2024-03-11T03:59:59.999999999Z"),result.games.items[0].startedAt)
            assertEquals(47*3600L,applied.query.untilExclusive!!.epochSeconds-applied.query.fromInclusive!!.epochSeconds)
        }
    }
    @Test fun skippedDayEqualUtcBoundsReturnGenuineEmptyHistory()=runBlocking<Unit> {
        HostLifeDatabase(folder.newFile()).use { db ->
            create(db,"transition",stamp("2011-12-30T10:00:00Z"))
            val day=LocalDate.of(2011,12,30)
            val applied=ok(CalendarGameHistoryFilter.create(GameHistoryFilter(),day,day,ZoneId.of("Pacific/Apia")))
            val result=ok(SqlLifeGameHistory(db,codec).history(applied.query,PageRequest(10),f.token))
            assertEquals(0L,result.totalMatches);assertTrue(result.games.items.isEmpty());assertNull(result.games.nextCursor)
        }
    }
}
