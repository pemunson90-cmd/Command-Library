package com.architectpsg.commandlibrary.core.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Local event DTO coherence only; these fixtures do not prove persisted replay or undo lineage. */
class GameEventContractsTest {
    private val requestId = EntityId("synthetic-request")
    private val seatId = EntityId("synthetic-seat")
    private val gameId = GameId(ProviderId("synthetic"), "game")
    private fun snapshot(revision: Long = 0, id: GameId = gameId): GameSnapshot {
        val settings = GameSettings(Knowledge.Unknown("No format", SnapshotList.empty()), 20, SnapshotList.empty(), SnapshotList.empty(), SeatClockMode.NONE, 0)
        val clock = SeatClockState(SeatClockMode.NONE, SnapshotList.empty(), null, null, true, ClockZeroPolicy.ALERT, ClockUndoPolicy.KEEP_ELAPSED, SnapshotList.empty())
        return GameSnapshot(id, 1, Revision(revision), settings, GameStatus.ACTIVE, UtcTimestamp(1000), Knowledge.Absent("Not ended"),
            SnapshotList.empty(), SnapshotList.empty(), null, 0, clock, SnapshotList.empty(), Knowledge.Absent("No event"), Knowledge.Absent("No result"))
    }
    private fun reading(monotonic: Duration = Duration.ZERO) = GameTimeObservation(monotonic, UtcTimestamp(1000), EntityId("synthetic-process"))
    private fun provenance() = GameActionProvenance(7, snapshot(), snapshot(1), reading())
    private fun event() = GameEvent(7, gameId, EntityId("synthetic-group"), GameAction.RenameSeat(seatId, "Synthetic"), UtcTimestamp(1000), Knowledge.Absent("Not undo"), Revision(1))
    private fun dice(sides: Int = 6, faces: SnapshotList<Int> = SnapshotList.of(1, 2), sum: Long = 3,
        requested: Long = 2, batchIndex: Long = 0, remaining: Long = 0) =
        RecordedRandomValue.Dice(sides, faces, sum, requested, batchIndex, remaining)

    @Test fun randomRequestsValidatePositiveDiceBoundsLongContinuationAndOpponentAsker() {
        assertEquals(Long.MAX_VALUE, RandomRequest.Dice(requestId, Long.MAX_VALUE, Int.MAX_VALUE).requested)
        for (count in listOf(0L, -1L, Long.MIN_VALUE)) assertThrows(IllegalArgumentException::class.java) { RandomRequest.Dice(requestId, count, 6) }
        for (sides in listOf(0, -1, Int.MIN_VALUE)) assertThrows(IllegalArgumentException::class.java) { RandomRequest.Dice(requestId, 1, sides) }
        assertEquals(Int.MAX_VALUE.toLong() + 1, RandomRequest.ContinueDice(requestId, Int.MAX_VALUE.toLong() + 1).batchIndex)
        assertEquals(0L, RandomRequest.ContinueDice(requestId, 0).batchIndex)
        assertThrows(IllegalArgumentException::class.java) { RandomRequest.ContinueDice(requestId, -1) }
        assertEquals(seatId, RandomRequest.SeatPick(requestId, seatId, true).asker)
        assertNull(RandomRequest.SeatPick(requestId, null, false).asker)
        assertThrows(IllegalArgumentException::class.java) { RandomRequest.SeatPick(requestId, null, true) }
    }
    @Test fun recordedDiceValidateFacesAndUseLongSumAtMaximumBatchSize() {
        val faces = SnapshotList.copyOf(List(10_000) { Int.MAX_VALUE })
        val expected = Int.MAX_VALUE.toLong() * 10_000
        assertEquals(expected, dice(Int.MAX_VALUE, faces, expected, 10_000).sum)
        assertThrows(IllegalArgumentException::class.java) { dice(faces = SnapshotList.empty(), sum = 0) }
        assertThrows(IllegalArgumentException::class.java) { dice(1, SnapshotList.copyOf(List(10_001) { 1 }), 10_001, 10_001) }
        assertThrows(IllegalArgumentException::class.java) { dice(sides = 0) }
        assertThrows(IllegalArgumentException::class.java) { dice(faces = SnapshotList.of(0, 2), sum = 2) }
        assertThrows(IllegalArgumentException::class.java) { dice(faces = SnapshotList.of(1, 7), sum = 8) }
        assertThrows(IllegalArgumentException::class.java) { dice(sum = 4) }
        assertThrows(IllegalArgumentException::class.java) { dice(sum = -1) }
    }
    @Test fun firstDiceBatchHasExactRemainingAndPriorVariableBatchesAreLocallyPlausible() {
        assertEquals(18L, dice(requested = 20, remaining = 18).remaining)
        assertEquals(3L, dice(requested = 20, batchIndex = 3, remaining = 8).batchIndex)
        assertThrows(IllegalArgumentException::class.java) { dice(requested = 20, remaining = 17) }
        assertThrows(IllegalArgumentException::class.java) { dice(requested = 20, batchIndex = 1, remaining = 18) }
        assertThrows(IllegalArgumentException::class.java) { dice(requested = 20, batchIndex = 11, remaining = 8) }
        assertThrows(IllegalArgumentException::class.java) { dice(requested = 20, batchIndex = -1, remaining = 18) }
    }
    @Test fun dicePriorBatchCountCannotClaimMoreThanTenThousandPerBatch() {
        val valid = dice(faces = SnapshotList.of(1), sum = 1, requested = 10_002, batchIndex = 2, remaining = 0)
        assertEquals(2L, valid.batchIndex)
        assertThrows(IllegalArgumentException::class.java) { dice(faces = SnapshotList.of(1), sum = 1, requested = 10_002, batchIndex = 1, remaining = 0) }
    }
    @Test fun recordedDiceRejectImpossibleRequestRemainderBeforeSubtraction() {
        for (requested in listOf(0L, -1L, Long.MIN_VALUE, 1L)) {
            assertThrows(IllegalArgumentException::class.java) { dice(requested = requested) }
        }
        for (remaining in listOf(-1L, Long.MIN_VALUE, 1L, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { dice(remaining = remaining) }
        }
    }
    @Test fun longRequestsAndBatchIndicesRemainRepresentableWithoutOverflow() {
        val index = Int.MAX_VALUE.toLong() + 1
        val remainder = Long.MAX_VALUE - 1 - index
        assertEquals(index, dice(faces = SnapshotList.of(1), sum = 1, requested = Long.MAX_VALUE, batchIndex = index, remaining = remainder).batchIndex)
        assertEquals(Long.MAX_VALUE - 1, dice(faces = SnapshotList.of(1), sum = 1, requested = Long.MAX_VALUE,
            batchIndex = Long.MAX_VALUE - 1, remaining = 0).batchIndex)
        assertThrows(IllegalArgumentException::class.java) { dice(faces = SnapshotList.of(1), sum = 1, requested = Long.MAX_VALUE, batchIndex = Long.MAX_VALUE, remaining = 0) }
    }
    @Test fun recordedSeatSelectionRequiresUniqueEligibleSetAndCoherentSelection() {
        assertNull(RecordedRandomValue.SeatPick(null, SnapshotList.empty()).selected)
        assertEquals(seatId, RecordedRandomValue.SeatPick(seatId, SnapshotList.of(seatId)).selected)
        assertThrows(IllegalArgumentException::class.java) { RecordedRandomValue.SeatPick(seatId, SnapshotList.of(seatId, seatId)) }
        assertThrows(IllegalArgumentException::class.java) { RecordedRandomValue.SeatPick(null, SnapshotList.of(seatId)) }
        assertThrows(IllegalArgumentException::class.java) { RecordedRandomValue.SeatPick(seatId, SnapshotList.empty()) }
        assertThrows(IllegalArgumentException::class.java) { RecordedRandomValue.SeatPick(EntityId("foreign"), SnapshotList.of(seatId)) }
        assertEquals(SnapshotList.of(seatId), RecordedRandomValue.SeatOrder(SnapshotList.of(seatId)).seatIds)
        assertTrue(RecordedRandomValue.SeatOrder(SnapshotList.empty()).seatIds.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { RecordedRandomValue.SeatOrder(SnapshotList.of(seatId, seatId)) }
    }
    @Test fun timeObservationAcceptsSignedFiniteTicksWithoutChangingWallOrSession() {
        val observation = reading((-5).seconds)
        assertEquals((-5).seconds, observation.monotonic); assertEquals(UtcTimestamp(1000), observation.wall)
        assertEquals(EntityId("synthetic-process"), observation.processSessionId)
        for (duration in listOf(Duration.INFINITE, -Duration.INFINITE)) {
            assertThrows(IllegalArgumentException::class.java) { reading(duration) }
        }
    }
    @Test fun provenancePreservesImmutableBeforeAfterAndRejectsCrossGameOrWrongRevision() {
        val provenance = provenance()
        assertEquals(Revision(0), provenance.before.revision); assertEquals(Revision(1), provenance.afterAction.revision)
        assertEquals(7L, provenance.eventId)
        assertThrows(IllegalArgumentException::class.java) { provenance.copy(eventId = -1) }
        assertThrows(IllegalArgumentException::class.java) { provenance.copy(afterAction = snapshot(1, GameId(ProviderId("synthetic"), "other-game"))) }
        assertThrows(IllegalArgumentException::class.java) { provenance.copy(afterAction = snapshot(0)) }
        assertThrows(IllegalArgumentException::class.java) { provenance.copy(afterAction = snapshot(2)) }
    }
    @Test fun provenanceRejectsRevisionOverflowAndAcceptsLastRepresentableIncrement() {
        assertEquals(Revision(Long.MAX_VALUE), GameActionProvenance(7, snapshot(Long.MAX_VALUE - 1), snapshot(Long.MAX_VALUE), reading()).afterAction.revision)
        assertThrows(IllegalArgumentException::class.java) { GameActionProvenance(7, snapshot(Long.MAX_VALUE), snapshot(0), reading()) }
        assertThrows(IllegalArgumentException::class.java) { GameActionProvenance(7, snapshot(Long.MAX_VALUE), snapshot(Long.MAX_VALUE), reading()) }
    }
    @Test fun archiveChecksVersionAndEventIdentityGameAndRevisionAliases() {
        val event = event(); val provenance = provenance()
        val archive = GameEventArchive(1, event, provenance)
        assertEquals(event, archive.event); assertEquals(provenance, archive.provenance)
        assertThrows(IllegalArgumentException::class.java) { archive.copy(version = 0) }
        assertThrows(IllegalArgumentException::class.java) { archive.copy(version = -1) }
        assertThrows(IllegalArgumentException::class.java) { archive.copy(event = event.copy(id = 8)) }
        assertThrows(IllegalArgumentException::class.java) { archive.copy(event = event.copy(gameId = GameId(ProviderId("synthetic"), "other-game"))) }
        assertThrows(IllegalArgumentException::class.java) { archive.copy(event = event.copy(revision = Revision(2))) }
    }
    @Test fun legacyEventResultAndExchangeDefaultsDoNotInventRandomOutcomesOrArchives() {
        val event = event(); assertTrue(event.randomResults.isEmpty())
        val result = GameResult(EntityId("synthetic-result"), gameId, SnapshotList.empty(), UtcTimestamp(1000), "Synthetic draw", Revision(1), false)
        assertTrue(result.seatResults.isEmpty())
        val exchange = GameExchange(InterchangeVersion(1, "synthetic"), snapshot(), SnapshotList.of(event), Knowledge.Known(result), SnapshotList.empty())
        assertTrue(exchange.actionArchives.isEmpty())
        val recorded = RecordedRandomResult(requestId, UtcTimestamp(1000), RecordedRandomValue.Coin(true))
        assertEquals(SnapshotList.of(recorded), event.copy(randomResults = SnapshotList.of(recorded)).randomResults)
        assertTrue(event.randomResults.isEmpty())
        assertEquals(UtcTimestamp(1000), recorded.at); assertEquals(requestId, recorded.requestId)
    }
    @Test fun checkpointRetainsUnresolvedLegacyPointersAndActionStepRequiresPositiveValue() {
        val pointer = Knowledge.Unknown("Synthetic legacy pointer", SnapshotList.empty())
        val checkpoint = GameCheckpoint(snapshot(), pointer, null)
        assertEquals(pointer, checkpoint.lastSettledEventId); assertNull(checkpoint.recoveredProcessSessionId)
        assertTrue(GameCheckpoint(snapshot(), Knowledge.Absent("No settled event"), null).lastSettledEventId is Knowledge.Absent)
        assertEquals(5, GameAction.SetAdjustmentStep(5).step)
        for (step in listOf(0, -1, Int.MIN_VALUE)) assertThrows(IllegalArgumentException::class.java) { GameAction.SetAdjustmentStep(step) }
    }
}
