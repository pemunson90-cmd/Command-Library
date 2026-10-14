package com.architectpsg.commandlibrary.core.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class LifeEventContractsTest {
    private val game = GameId(ProviderId("synthetic"), "game")
    private val reading = GameTimeObservation(12.seconds, UtcTimestamp(1000), EntityId("synthetic-process"))
    private fun snapshot(revision: Long = 0) = GameSnapshot(game, 1, Revision(revision),
        GameSettings(Knowledge.Absent("No format"), 20, SnapshotList.empty(), SnapshotList.empty(), SeatClockMode.NONE, 0),
        GameStatus.PAUSED, UtcTimestamp(0), Knowledge.Absent("Not finished"), SnapshotList.empty(), SnapshotList.empty(),
        null, 0, SeatClockState(SeatClockMode.NONE, SnapshotList.empty(), null, null, true,
            ClockZeroPolicy.ALERT, ClockUndoPolicy.KEEP_ELAPSED, SnapshotList.empty()), SnapshotList.empty(),
        Knowledge.Absent("No events"), Knowledge.Absent("No result"))
    private fun provenance() = GameActionProvenance(7, snapshot(), snapshot(1).copy(lastEventId = Knowledge.Known(7L)), reading)
    private fun event() = GameEvent(7, game, EntityId("synthetic-group"), GameAction.ClockPause(true),
        reading.wall, Knowledge.Absent("Not undo"), Revision(1))

    @Test fun rejectsNonfiniteAndNegativeMonotonicObservations() {
        assertEquals(Duration.ZERO, reading.copy(monotonic = Duration.ZERO).monotonic)
        for (value in listOf((-1).seconds, Duration.INFINITE, -Duration.INFINITE)) {
            assertThrows(IllegalArgumentException::class.java) { reading.copy(monotonic = value) }
        }
    }
    @Test fun preservesImmutableActionTimeAndPermitsRevisionMaximumBoundary() {
        val before = snapshot(Long.MAX_VALUE - 1)
        val after = snapshot(Long.MAX_VALUE).copy(lastEventId = Knowledge.Known(7L))
        val value = GameActionProvenance(7, before, after, reading)
        assertEquals(before, value.before); assertEquals(after, value.afterAction); assertEquals(reading, value.reading)
        assertThrows(IllegalArgumentException::class.java) { value.copy(before = after) }
    }
    @Test fun rejectsMismatchedProvenanceIdentityRevisionAndEventPointer() {
        val value = provenance()
        assertThrows(IllegalArgumentException::class.java) { value.copy(eventId = -1) }
        assertThrows(IllegalArgumentException::class.java) { value.copy(afterAction = value.afterAction.copy(id = game.copy(raw = "other"))) }
        assertThrows(IllegalArgumentException::class.java) { value.copy(afterAction = value.afterAction.copy(revision = Revision(2))) }
        assertThrows(IllegalArgumentException::class.java) { value.copy(afterAction = value.afterAction.copy(lastEventId = Knowledge.Known(8L))) }
        assertThrows(IllegalArgumentException::class.java) { value.copy(afterAction = value.afterAction.copy(lastEventId = Knowledge.Absent("Missing"))) }
    }
    @Test fun archivesRejectWrongVersionGameEventAndRevision() {
        val value = GameEventArchive(1, event(), provenance())
        assertEquals(provenance(), value.provenance)
        assertThrows(IllegalArgumentException::class.java) { value.copy(version = 0) }
        assertThrows(IllegalArgumentException::class.java) { value.copy(event = event().copy(id = 8)) }
        assertThrows(IllegalArgumentException::class.java) { value.copy(event = event().copy(gameId = game.copy(raw = "other"))) }
        assertThrows(IllegalArgumentException::class.java) { value.copy(event = event().copy(revision = Revision(2))) }
    }
    @Test fun checkpointsRejectNegativeSettledEventIdsWithoutInventingUnknownData() {
        val checkpoint = GameCheckpoint(snapshot(), Knowledge.Unknown("Legacy lineage", SnapshotList.empty()), null)
        assertTrue(checkpoint.lastSettledEventId is Knowledge.Unknown)
        assertThrows(IllegalArgumentException::class.java) { checkpoint.copy(lastSettledEventId = Knowledge.Known(-1L)) }
        assertEquals(Long.MAX_VALUE, (checkpoint.copy(lastSettledEventId = Knowledge.Known(Long.MAX_VALUE)).lastSettledEventId as Knowledge.Known).value)
    }
    @Test fun legacyConstructorsRetainEmptyArchivesAndResults() {
        assertTrue(event().randomResults.isEmpty())
        val exchange = GameExchange(InterchangeVersion(1, "synthetic"), snapshot(), SnapshotList.of(event()),
            Knowledge.Absent("Not finished"), SnapshotList.empty())
        assertTrue(exchange.actionArchives.isEmpty())
        assertEquals(SnapshotList.of(GameEventArchive(1, event(), provenance())),
            exchange.copy(actionArchives = SnapshotList.of(GameEventArchive(1, event(), provenance()))).actionArchives)
    }
}
