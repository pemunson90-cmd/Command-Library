package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId

/** Applied inclusive calendar dates with a retained explicit zone; UI chooses the zone when applying. */
public class CalendarGameHistoryFilter private constructor(public val fromDay: LocalDate?, public val throughDay: LocalDate?,
    public val zone: ZoneId, public val query: GameHistoryFilter) {
    public companion object {
        /** Replaces both UTC bounds, preserving player/deck filters; null dates clear their prior bounds. */
        public fun create(base: GameHistoryFilter, fromDay: LocalDate?, throughDay: LocalDate?, zone: ZoneId): Outcome<CalendarGameHistoryFilter> {
            if (fromDay != null && throughDay != null && fromDay > throughDay) return invalid("History calendar dates are inverted")
            return try {
                val from = fromDay?.atStartOfDay(zone)?.toInstant()?.let { UtcTimestamp(it.epochSecond, it.nano) }
                val until = throughDay?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.let { UtcTimestamp(it.epochSecond, it.nano) }
                val query = base.copy(fromInclusive = from, untilExclusive = until)
                Outcome.Success(CalendarGameHistoryFilter(fromDay, throughDay, zone, query))
            } catch (error: DateTimeException) { invalid("History calendar boundary cannot be represented") }
            catch (error: ArithmeticException) { invalid("History calendar boundary overflow") }
            catch (error: IllegalArgumentException) { invalid("Invalid history calendar bounds") }
        }
        private fun invalid(message: String): Outcome.Failure = Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, message, SnapshotList.empty()))
    }
}
