package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.GameAction
import com.architectpsg.commandlibrary.core.model.GameStatus
import com.architectpsg.commandlibrary.core.model.RandomSource
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.life
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.seatA
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.seatB
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.seatC
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.snapshot
import com.architectpsg.commandlibrary.feature.life.domain.LifeFixtures.state
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomToolsTest {
    /** Test-only source replaying scripted draws and recording every requested bound. */
    private class ScriptedRandom(vararg draws: Int) : RandomSource {
        private val queue = ArrayDeque(draws.toList())
        val bounds = mutableListOf<Int>()
        override fun nextInt(upperBound: Int): Int {
            bounds += upperBound
            return queue.removeFirst()
        }
    }

    /** Test-only source answering every draw with a function of its bound. */
    private class BoundRandom(private val answer: (Int) -> Int) : RandomSource {
        var calls = 0
        override fun nextInt(upperBound: Int): Int {
            calls++
            return answer(upperBound)
        }
    }

    /** Test-only seeded source; production code receives its RandomSource by injection and has no default. */
    private class SeededRandom(seed: Long) : RandomSource {
        private val random = Random(seed)
        override fun nextInt(upperBound: Int): Int = random.nextInt(upperBound)
    }

    private val at = UtcTimestamp(70_000)

    @Test fun diceCountIsValidatedToOneThroughTenThousand() {
        val anyFace = BoundRandom { 0 }
        RandomTools.rollDice(anyFace, 0, 6).failure(FailureCode.INVALID_INPUT)
        RandomTools.rollDice(anyFace, 10_001, 6).failure(FailureCode.INVALID_INPUT)
        RandomTools.rollDice(anyFace, -1, 6).failure(FailureCode.INVALID_INPUT)
        assertEquals(0, anyFace.calls)
        assertEquals(1, RandomTools.rollDice(anyFace, 1, 6).success().values.size)
        assertEquals(10_000, RandomTools.rollDice(anyFace, 10_000, 6).success().values.size)
    }

    @Test fun dieSidesMustBePositiveAndScriptedDrawsMapToFaces() {
        RandomTools.rollDice(BoundRandom { 0 }, 1, 0).failure(FailureCode.INVALID_INPUT)
        RandomTools.rollDice(BoundRandom { 0 }, 1, -6).failure(FailureCode.INVALID_INPUT)
        assertEquals(SnapshotList.of(1, 1, 1), RandomTools.rollDice(BoundRandom { 0 }, 3, 1).success().values)
        val source = ScriptedRandom(0, 5, 2, 19)
        val roll = RandomTools.rollDice(source, 4, 20).success()
        assertEquals(DiceRoll(20, SnapshotList.of(1, 6, 3, 20), 30L), roll)
        assertEquals(listOf(20, 20, 20, 20), source.bounds)
    }

    @Test fun sumUsesLongBeyondIntRange() {
        val roll = RandomTools.rollDice(BoundRandom { it - 1 }, 10_000, Int.MAX_VALUE).success()
        assertTrue(roll.values.all { it == Int.MAX_VALUE })
        assertEquals(10_000L * Int.MAX_VALUE, roll.sum)
    }

    @Test fun outOfRangeSourceValuesFailWithoutPartialResults() {
        RandomTools.rollDice(ScriptedRandom(1, 6), 2, 6).failure(FailureCode.INTERNAL)
        RandomTools.rollDice(ScriptedRandom(-1), 1, 6).failure(FailureCode.INTERNAL)
        RandomTools.flipCoin(ScriptedRandom(2)).failure(FailureCode.INTERNAL)
        RandomTools.shuffle(ScriptedRandom(3), listOf("a", "b", "c")).failure(FailureCode.INTERNAL)
        RandomTools.selectSeat(snapshot(), ScriptedRandom(3), SnapshotList.of(seatA, seatB, seatC), at).failure(FailureCode.INTERNAL)
    }

    @Test fun largeRequestsAreBatchedWithoutDroppingRemainingRolls() {
        val source = BoundRandom { 0 }
        var owed = 25_000L
        val batches = mutableListOf<Int>()
        while (owed > 0) {
            val batch = RandomTools.rollBatch(source, owed, 6).success()
            batches += batch.roll.values.size
            owed = batch.remaining
        }
        assertEquals(listOf(10_000, 10_000, 5_000), batches)
        assertEquals(25_000, source.calls)
        RandomTools.rollBatch(source, 0, 6).failure(FailureCode.INVALID_INPUT)
        assertEquals(DiceBatch(DiceRoll(6, SnapshotList.of(1), 1L), 0L), RandomTools.rollBatch(BoundRandom { 0 }, 1, 6).success())
    }

    @Test fun coinUsesTwoOutcomes() {
        val source = ScriptedRandom(0, 1)
        assertEquals(CoinFace.HEADS, RandomTools.flipCoin(source).success())
        assertEquals(CoinFace.TAILS, RandomTools.flipCoin(source).success())
        assertEquals(listOf(2, 2), source.bounds)
    }

    @Test fun shuffleIsFisherYatesAndLeavesItsInputAlone() {
        val input = listOf("a", "b", "c", "d")
        val source = ScriptedRandom(0, 2, 0)
        // i=3 swaps with 0 -> d b c a; i=2 keeps 2; i=1 swaps with 0 -> b d c a.
        assertEquals(SnapshotList.of("b", "d", "c", "a"), RandomTools.shuffle(source, input).success())
        assertEquals(listOf(4, 3, 2), source.bounds)
        assertEquals(listOf("a", "b", "c", "d"), input)
        val idle = BoundRandom { 0 }
        assertEquals(SnapshotList.empty<String>(), RandomTools.shuffle(idle, emptyList<String>()).success())
        assertEquals(SnapshotList.of("only"), RandomTools.shuffle(idle, listOf("only")).success())
        assertEquals(0, idle.calls)
    }

    @Test fun seatRandomizerPermutesOnceAndKeepsPlayerDataAttached() {
        val input = snapshot(listOf(state(life, seatB, 33)))
        val reduced = RandomTools.randomizeSeats(input, ScriptedRandom(0, 0)).success()
        assertEquals(GameAction.ReorderSeats(SnapshotList.of(seatB, seatC, seatA)), reduced.action)
        val byId = reduced.snapshot.seats.associateBy { it.id }
        assertEquals(listOf(0, 1, 2), listOf(seatB, seatC, seatA).map { byId.getValue(it).order })
        for (original in input.seats) {
            assertEquals(original.copy(order = byId.getValue(original.id).order), byId.getValue(original.id))
        }
        assertEquals(input.counters, reduced.snapshot.counters)
        assertEquals(input.revision, reduced.snapshot.revision)
        assertEquals(listOf(0, 1, 2), input.seats.map { it.order })
        val untouched = BoundRandom { 0 }
        RandomTools.randomizeSeats(snapshot(status = GameStatus.COMPLETED), untouched).failure(FailureCode.INVALID_INPUT)
        assertEquals("A rejected randomize consumes no draws", 0, untouched.calls)
    }

    @Test fun explicitReorderMustBeACompletePermutation() {
        val input = snapshot()
        RandomTools.reorderSeats(input, GameAction.ReorderSeats(SnapshotList.of(seatA, seatB))).failure(FailureCode.INVALID_INPUT)
        RandomTools.reorderSeats(input, GameAction.ReorderSeats(SnapshotList.of(seatA, seatA, seatB))).failure(FailureCode.INVALID_INPUT)
        RandomTools.reorderSeats(input, GameAction.ReorderSeats(SnapshotList.of(seatA, seatB, EntityId("seat-z")))).failure(FailureCode.INVALID_INPUT)
        val moved = RandomTools.reorderSeats(input, GameAction.ReorderSeats(SnapshotList.of(seatC, seatA, seatB))).success().snapshot
        assertEquals(mapOf(seatC to 0, seatA to 1, seatB to 2), moved.seats.associate { it.id to it.order })
    }

    @Test fun seatSelectionDrawsOnlyFromSuppliedEligibleSeatsInTableOrder() {
        val source = ScriptedRandom(1)
        val picked = RandomTools.selectSeat(snapshot(), source, SnapshotList.of(seatC, seatA), at).success()
        assertEquals(SeatSelection.Selected(seatC, SnapshotList.of(seatA, seatC), at), picked)
        assertEquals(listOf(2), source.bounds)
        val idle = BoundRandom { 0 }
        assertEquals(SeatSelection.Selected(seatB, SnapshotList.of(seatB), at), RandomTools.selectSeat(snapshot(), idle, SnapshotList.of(seatB), at).success())
        assertEquals(SeatSelection.NoEligibleSeat(SnapshotList.empty(), at), RandomTools.selectSeat(snapshot(), idle, SnapshotList.empty(), at).success())
        assertEquals(0, idle.calls)
        RandomTools.selectSeat(snapshot(), idle, SnapshotList.of(seatA, seatA), at).failure(FailureCode.INVALID_INPUT)
        RandomTools.selectSeat(snapshot(), idle, SnapshotList.of(EntityId("seat-z")), at).failure(FailureCode.INVALID_INPUT)
    }

    @Test fun opponentSelectionExcludesTheAskingSeat() {
        val picked = RandomTools.selectOpponent(snapshot(), ScriptedRandom(0), SnapshotList.of(seatA, seatB, seatC), seatA, at).success()
        assertEquals(SeatSelection.Selected(seatB, SnapshotList.of(seatB, seatC), at), picked)
        assertEquals(
            SeatSelection.NoEligibleSeat(SnapshotList.empty(), at),
            RandomTools.selectOpponent(snapshot(), BoundRandom { 0 }, SnapshotList.of(seatA), seatA, at).success(),
        )
        RandomTools.selectOpponent(snapshot(), BoundRandom { 0 }, SnapshotList.of(seatB), EntityId("seat-z"), at).failure(FailureCode.INVALID_INPUT)
    }

    @Test fun seededTenThousandDiceStayInRangeAndCoverEveryFace() {
        val roll = RandomTools.rollDice(SeededRandom(20_261_012), 10_000, 6).success()
        assertTrue(roll.values.all { it in 1..6 })
        assertEquals(roll.values.sumOf { it.toLong() }, roll.sum)
        val counts = roll.values.groupingBy { it }.eachCount()
        assertEquals((1..6).toSet(), counts.keys)
        // Expected 1666.7 per face, standard deviation about 37; this fixed seed is deterministic, the bound is a sanity check.
        assertTrue(counts.toString(), counts.values.all { it in 1_400..1_950 })
    }
}
