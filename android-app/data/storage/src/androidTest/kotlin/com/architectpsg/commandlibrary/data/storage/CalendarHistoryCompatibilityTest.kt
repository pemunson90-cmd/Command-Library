package com.architectpsg.commandlibrary.data.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.life.persistence.CalendarGameHistoryFilter
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Packaged java.time/core-library-desugaring probe; requires an actual device run for runtime evidence. */
@RunWith(AndroidJUnit4::class)
class CalendarHistoryCompatibilityTest {
    @Test fun packagedCalendarAdapterRetainsInclusiveDstDayWithoutApi26Requirement() {
        val day=LocalDate.of(2024,3,10)
        val result=CalendarGameHistoryFilter.create(GameHistoryFilter(playerName="A"),day,day,ZoneId.of("America/New_York"))
        assertTrue("Expected date conversion, got $result",result is Outcome.Success)
        val applied=(result as Outcome.Success).value
        assertEquals(UtcTimestamp(1710046800),applied.query.fromInclusive)
        assertEquals(UtcTimestamp(1710129600),applied.query.untilExclusive)
        assertEquals("America/New_York",applied.zone.id);assertEquals("A",applied.query.playerName)
    }
}
