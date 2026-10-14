package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.life.persistence.CalendarGameHistoryFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class CalendarGameHistoryFilterTest {
    private fun date(value:String)=LocalDate.parse(value)
    private fun stamp(value:String)=Instant.parse(value).let { UtcTimestamp(it.epochSecond,it.nano) }
    private fun ok(value:Outcome<CalendarGameHistoryFilter>):CalendarGameHistoryFilter {
        assertTrue("Expected success, got $value",value is Outcome.Success);return (value as Outcome.Success).value
    }
    private fun range(from:String,through:String,zone:String)=ok(CalendarGameHistoryFilter.create(GameHistoryFilter(),date(from),date(through),ZoneId.of(zone)))
    @Test fun includesBothSelectedUtcDaysAndRetainsOriginalNonDateFields() {
        val deck=DeckId(ProviderId("synthetic"),"deck")
        val base=GameHistoryFilter("A",deck,"Stored deck",UtcTimestamp(1),UtcTimestamp(2))
        val applied=ok(CalendarGameHistoryFilter.create(base,date("2024-02-28"),date("2024-02-29"),ZoneId.of("UTC")))
        assertEquals(base.copy(fromInclusive=stamp("2024-02-28T00:00:00Z"),untilExclusive=stamp("2024-03-01T00:00:00Z")),applied.query)
        assertEquals(date("2024-02-28"),applied.fromDay);assertEquals(date("2024-02-29"),applied.throughDay)
        assertEquals(UtcTimestamp(1),base.fromInclusive)
    }
    @Test fun nullDatesClearOldBoundsAndOneSidedDatesRemainUnbounded() {
        val base=GameHistoryFilter(playerName="Alice",fromInclusive=UtcTimestamp(1),untilExclusive=UtcTimestamp(2));val zone=ZoneId.of("UTC")
        assertEquals(base.copy(fromInclusive=null,untilExclusive=null),ok(CalendarGameHistoryFilter.create(base,null,null,zone)).query)
        val lower=ok(CalendarGameHistoryFilter.create(base,date("2024-01-01"),null,zone)).query
        assertEquals(stamp("2024-01-01T00:00:00Z"),lower.fromInclusive);assertNull(lower.untilExclusive)
        val upper=ok(CalendarGameHistoryFilter.create(base,null,date("2024-01-01"),zone)).query
        assertNull(upper.fromInclusive);assertEquals(stamp("2024-01-02T00:00:00Z"),upper.untilExclusive)
    }
    @Test fun springAndFallUseActualLocalDayLengthsRatherThanFixedSeconds() {
        val spring=range("2024-03-10","2024-03-10","America/New_York").query
        assertEquals(stamp("2024-03-10T05:00:00Z"),spring.fromInclusive);assertEquals(stamp("2024-03-11T04:00:00Z"),spring.untilExclusive)
        assertEquals(23*3600L,spring.untilExclusive!!.epochSeconds-spring.fromInclusive!!.epochSeconds)
        val fall=range("2024-11-03","2024-11-03","America/New_York").query
        assertEquals(stamp("2024-11-03T04:00:00Z"),fall.fromInclusive);assertEquals(stamp("2024-11-04T05:00:00Z"),fall.untilExclusive)
        assertEquals(25*3600L,fall.untilExclusive!!.epochSeconds-fall.fromInclusive!!.epochSeconds)
    }
    @Test fun skippedCalendarDayProducesEmptyIntervalWithoutInventingUtcTime() {
        val skipped=range("2011-12-30","2011-12-30","Pacific/Apia").query
        assertEquals(stamp("2011-12-30T10:00:00Z"),skipped.fromInclusive);assertEquals(skipped.fromInclusive,skipped.untilExclusive)
    }
    @Test fun midnightGapUsesFirstValidInstantAndOverlapUsesEarlierOffset() {
        val gap=range("2018-11-04","2018-11-04","America/Sao_Paulo").query
        assertEquals(stamp("2018-11-04T03:00:00Z"),gap.fromInclusive)
        assertEquals(stamp("2018-11-05T02:00:00Z"),gap.untilExclusive)
        val overlap=range("2015-11-01","2015-11-01","America/Havana").query
        assertEquals(stamp("2015-11-01T04:00:00Z"),overlap.fromInclusive)
        assertEquals(stamp("2015-11-02T05:00:00Z"),overlap.untilExclusive)
    }
    @Test fun invertedDateOrderAndUnrepresentableSuccessorAreTypedInvalidInput() {
        for(pair in listOf(date("2024-01-02") to date("2024-01-01"),null to LocalDate.MAX)) {
            val result=CalendarGameHistoryFilter.create(GameHistoryFilter(),pair.first,pair.second,ZoneId.of("UTC"))
            assertTrue(result is Outcome.Failure);assertEquals(FailureCode.INVALID_INPUT,(result as Outcome.Failure).error.code)
        }
    }
    @Test fun explicitZoneAndBoundsSurviveSystemDefaultZoneChanges() {
        val previous=TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            val selected=range("2024-03-10","2024-03-10","America/New_York");val query=selected.query
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            assertEquals(ZoneId.of("America/New_York"),selected.zone);assertEquals(query,selected.query)
            assertEquals(stamp("2024-03-10T05:00:00Z"),selected.query.fromInclusive)
            assertEquals(query,range("2024-03-10","2024-03-10","America/New_York").query)
        } finally { TimeZone.setDefault(previous) }
    }
}
