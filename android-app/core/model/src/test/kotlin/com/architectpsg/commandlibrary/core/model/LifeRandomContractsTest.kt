package com.architectpsg.commandlibrary.core.model

import org.junit.Assert.*
import org.junit.Test

class LifeRandomContractsTest {
    private val request = EntityId("synthetic-request")
    private val a = EntityId("synthetic-a")
    private val b = EntityId("synthetic-b")
    private fun dice() = RecordedRandomValue.Dice(6, SnapshotList.of(2, 5), 7, 2, 0, 0)

    @Test fun rejectsInvalidRequestsBeforeAnyDraw() {
        assertThrows(IllegalArgumentException::class.java) { RandomRequest.Dice(request, 0, 6) }
        assertThrows(IllegalArgumentException::class.java) { RandomRequest.Dice(request, 1, 0) }
        assertThrows(IllegalArgumentException::class.java) { RandomRequest.ContinueDice(request, 0) }
        assertThrows(IllegalArgumentException::class.java) { RandomRequest.ContinueDice(request, -1) }
        assertThrows(IllegalArgumentException::class.java) { RandomRequest.SeatPick(request, null, true) }
        assertEquals(Long.MAX_VALUE, RandomRequest.Dice(request, Long.MAX_VALUE, Int.MAX_VALUE).requested)
        assertEquals(3_000_000_000L, RandomRequest.ContinueDice(request, 3_000_000_000L).batchIndex)
    }
    @Test fun preservesExactFacesAndRejectsInventedSumsOrFaces() {
        assertEquals(SnapshotList.of(2, 5), dice().faces)
        assertThrows(IllegalArgumentException::class.java) { dice().copy(sum = 8) }
        assertThrows(IllegalArgumentException::class.java) { dice().copy(sides = 0) }
        assertThrows(IllegalArgumentException::class.java) { dice().copy(faces = SnapshotList.of(0, 7)) }
        assertThrows(IllegalArgumentException::class.java) { dice().copy(faces = SnapshotList.empty(), sum = 0) }
    }
    @Test fun enforcesFullCappedBatchesWithoutLosingRemainder() {
        val first = RecordedRandomValue.Dice(1, SnapshotList.copyOf(List(10_000) { 1 }), 10_000, 10_002, 0, 2)
        assertEquals(2L, first.remaining)
        val last = RecordedRandomValue.Dice(1, SnapshotList.of(1, 1), 2, 10_002, 1, 0)
        assertEquals(0L, last.remaining)
        assertThrows(IllegalArgumentException::class.java) { first.copy(remaining = 0) }
        assertThrows(IllegalArgumentException::class.java) { last.copy(batchIndex = 0) }
        assertThrows(IllegalArgumentException::class.java) { last.copy(requested = 0) }
        assertThrows(IllegalArgumentException::class.java) { last.copy(batchIndex = -1) }
        assertThrows(IllegalArgumentException::class.java) { last.copy(remaining = -1) }
        assertThrows(IllegalArgumentException::class.java) {
            RecordedRandomValue.Dice(1, SnapshotList.copyOf(List(10_001) { 1 }), 10_001, 10_001, 0, 0)
        }
    }
    @Test fun supportsLongMaximumRequestFinalBatchWithoutOverflow() {
        val index = (Long.MAX_VALUE - 1) / 10_000
        val count = (Long.MAX_VALUE - index * 10_000).toInt()
        val value = RecordedRandomValue.Dice(Int.MAX_VALUE, SnapshotList.copyOf(List(count) { Int.MAX_VALUE }),
            count.toLong() * Int.MAX_VALUE, Long.MAX_VALUE, index, 0)
        assertEquals(index, value.batchIndex)
        assertEquals(0L, value.remaining)
        assertThrows(IllegalArgumentException::class.java) { value.copy(batchIndex = Long.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { value.copy(batchIndex = index + 1) }
    }
    @Test fun rejectsImpossibleSeatSelectionButKeepsEmptyResult() {
        assertNull(RecordedRandomValue.SeatPick(null, SnapshotList.empty()).selected)
        assertEquals(b, RecordedRandomValue.SeatPick(b, SnapshotList.of(a, b)).selected)
        assertThrows(IllegalArgumentException::class.java) { RecordedRandomValue.SeatPick(null, SnapshotList.of(a)) }
        assertThrows(IllegalArgumentException::class.java) { RecordedRandomValue.SeatPick(a, SnapshotList.empty()) }
        assertThrows(IllegalArgumentException::class.java) { RecordedRandomValue.SeatPick(request, SnapshotList.of(a, b)) }
        assertThrows(IllegalArgumentException::class.java) { RecordedRandomValue.SeatPick(a, SnapshotList.of(a, a)) }
    }
    @Test fun rejectsDuplicateSeatOrderAndPreservesRecordedTimestamp() {
        assertThrows(IllegalArgumentException::class.java) { RecordedRandomValue.SeatOrder(SnapshotList.of(a, a)) }
        val result = RecordedRandomResult(request, UtcTimestamp(Long.MIN_VALUE, 17), RecordedRandomValue.Coin(true))
        assertEquals(UtcTimestamp(Long.MIN_VALUE, 17), result.at)
        assertTrue((result.value as RecordedRandomValue.Coin).heads)
        assertEquals(RandomRequest.Coin(request), GameAction.RequestRandom(RandomRequest.Coin(request)).request)
    }
}
