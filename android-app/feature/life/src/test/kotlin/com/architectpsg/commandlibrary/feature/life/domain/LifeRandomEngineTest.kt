package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class LifeRandomEngineTest {
    private val requestId = EntityId("random-request")
    private val at = UtcTimestamp(42)
    private class Draw(private val answer: (Int) -> Int = { 0 }) : RandomSource {
        var calls = 0
        override fun nextInt(upperBound: Int): Int { calls++; return answer(upperBound) }
    }
    private fun game(): GameSnapshot {
        val defs = SnapshotList.copyOf(LifeFixtures.definitions().map { it.copy(role = when(it.id) {
            LifeFixtures.life -> CounterRole.LIFE
            LifeFixtures.commanderDamage -> CounterRole.COMMANDER_DAMAGE
            LifeFixtures.commanderTax -> CounterRole.COMMANDER_TAX
            else -> CounterRole.OTHER
        }) })
        return LifeFixtures.snapshot(definitions = defs)
    }
    private fun <T> ok(outcome: Outcome<T>): T { assertTrue("Expected success, got $outcome", outcome is Outcome.Success); return (outcome as Outcome.Success).value }
    private fun action(request: RandomRequest) = GameAction.RequestRandom(request)
    private fun diceRequest(count: Long = 10_001, sides: Int = 6) = RandomRequest.Dice(requestId, count, sides)
    private fun invalid(outcome: Outcome<*>) { assertTrue("Expected invalid input, got $outcome", outcome is Outcome.Failure); assertEquals(FailureCode.INVALID_INPUT, (outcome as Outcome.Failure).error.code) }
    @Test fun cappedDiceContinuationRetainsEveryFaceAndExactTailWithoutReplayDraws() {
        val input = game(); val request = diceRequest(); val source = Draw { it - 1 }
        val first = ok(LifeRandomEngine.resolve(input, action(request), at, source))
        val batch = first.recorded.value as RecordedRandomValue.Dice
        assertEquals(10_000, batch.faces.size); assertEquals(60_000L, batch.sum); assertEquals(1L, batch.remaining); assertEquals(0L, batch.batchIndex)
        assertEquals(10_000, source.calls); assertEquals(input, first.snapshot); assertEquals(action(request), first.action)
        val context = RandomRequestContext(request, first.recorded)
        val next = action(RandomRequest.ContinueDice(requestId, 1))
        val last = ok(LifeRandomEngine.resolve(input, next, UtcTimestamp(43), source, context))
        val tail = last.recorded.value as RecordedRandomValue.Dice
        assertEquals(1, tail.faces.size); assertEquals(0L, tail.remaining); assertEquals(10_001L, tail.requested); assertEquals(10_001, source.calls)
        assertEquals(first, ok(LifeRandomEngine.replay(input, action(request), first.recorded)))
        assertEquals(last, ok(LifeRandomEngine.replay(input, next, last.recorded, context)))
        assertEquals(10_001, source.calls)
    }
    @Test fun hugeRequestAndFinalTailUseCheckedArithmetic() {
        val input = game(); val request = diceRequest(Long.MAX_VALUE, Int.MAX_VALUE); val source = Draw { it - 1 }
        val initial = ok(LifeRandomEngine.resolve(input, action(request), at, source)).recorded.value as RecordedRandomValue.Dice
        assertEquals(Long.MAX_VALUE - 10_000, initial.remaining); assertEquals(10_000L * Int.MAX_VALUE, initial.sum)
        val finalIndex = (Long.MAX_VALUE - 1) / 10_000
        val previousIndex = finalIndex - 1
        val previous = RecordedRandomValue.Dice(Int.MAX_VALUE, SnapshotList.copyOf(List(10_000) { 1 }), 10_000, Long.MAX_VALUE, previousIndex,
            Long.MAX_VALUE - previousIndex * 10_000 - 10_000)
        val context = RandomRequestContext(request, RecordedRandomResult(requestId, at, previous))
        val tail = ok(LifeRandomEngine.resolve(input, action(RandomRequest.ContinueDice(requestId, finalIndex)), at, Draw(), context)).recorded.value as RecordedRandomValue.Dice
        assertEquals(5807, tail.faces.size); assertEquals(0L, tail.remaining)
    }
    @Test fun duplicateSkippedWrongKindAndCompletedRequestsConsumeNoDraws() {
        val input = game(); val request = diceRequest(); val first = ok(LifeRandomEngine.resolve(input, action(request), at, Draw()))
        val context = RandomRequestContext(request, first.recorded)
        val source = Draw()
        for((requestAction, supplied) in listOf(action(request) to context,
            action(RandomRequest.ContinueDice(requestId, 2)) to context,
            action(RandomRequest.ContinueDice(EntityId("other"), 1)) to context,
            action(RandomRequest.ContinueDice(requestId, 1)) to null,
            action(RandomRequest.ContinueDice(requestId, 1)) to context.copy(last = first.recorded.copy(value = RecordedRandomValue.Coin(true))),
            action(RandomRequest.ContinueDice(requestId, 1)) to context.copy(last = first.recorded.copy(requestId = EntityId("other"))))) {
            invalid(LifeRandomEngine.resolve(input, requestAction, at, source, supplied))
        }
        invalid(LifeRandomEngine.resolve(input.copy(status = GameStatus.COMPLETED), action(request), at, source))
        invalid(LifeRandomEngine.resolve(input, GameAction.Group(SnapshotList.of(action(request)), EntityId("mixed")), at, source))
        assertEquals(0, source.calls)
    }
    @Test fun coinRecordsActualDrawAndOutOfBoundsSourceReturnsTypedFailure() {
        val input = game(); val request = action(RandomRequest.Coin(requestId))
        assertEquals(RecordedRandomValue.Coin(true), ok(LifeRandomEngine.resolve(input, request, at, Draw())).recorded.value)
        assertEquals(RecordedRandomValue.Coin(false), ok(LifeRandomEngine.resolve(input, request, at, Draw { 1 })).recorded.value)
        val failed = LifeRandomEngine.resolve(input, action(diceRequest()), at, Draw { it })
        assertTrue(failed is Outcome.Failure); assertEquals(FailureCode.INTERNAL, (failed as Outcome.Failure).error.code)
    }
    @Test fun seatPickUsesOnlyActiveOrderedSeatsWithEmptyAndSingletonNoDraws() {
        val input = game()
        val eliminated = SeatParticipation.Eliminated(SeatExitKind.MANUAL, "Explicit", null, at)
        val one = input.copy(seats = SnapshotList.copyOf(input.seats.map { if(it.id != LifeFixtures.seatB) it.copy(participation = eliminated) else it }))
        val source = Draw()
        val request = action(RandomRequest.SeatPick(requestId, null, false))
        val picked = ok(LifeRandomEngine.resolve(one, request, at, source)).recorded.value as RecordedRandomValue.SeatPick
        assertEquals(SnapshotList.of(LifeFixtures.seatB), picked.eligible); assertEquals(LifeFixtures.seatB, picked.selected)
        val none = ok(LifeRandomEngine.resolve(one, action(RandomRequest.SeatPick(requestId, LifeFixtures.seatB, true)), at, source)).recorded.value as RecordedRandomValue.SeatPick
        assertNull(none.selected); assertTrue(none.eligible.isEmpty()); assertEquals(0, source.calls)
        invalid(LifeRandomEngine.resolve(one, action(RandomRequest.SeatPick(requestId, EntityId("foreign"), false)), at, source))
    }
    @Test fun allRosterSeatOrderReplaysExactPermutationWithoutChangingOtherData() {
        val original = game()
        val input = original.copy(seats = SnapshotList.copyOf(original.seats.map { if(it.id == LifeFixtures.seatB) it.copy(participation = SeatParticipation.Eliminated(SeatExitKind.MANUAL, "Explicit", null, at)) else it }))
        val request = action(RandomRequest.SeatOrder(requestId)); val source = Draw()
        val resolved = ok(LifeRandomEngine.resolve(input, request, at, source))
        val order = (resolved.recorded.value as RecordedRandomValue.SeatOrder).seatIds
        assertEquals(input.seats.map { it.id }.toSet(), order.toSet()); assertEquals(2, source.calls)
        assertEquals(order, resolved.snapshot.seats.sortedBy { it.order }.map { it.id })
        assertEquals(input.counters, resolved.snapshot.counters); assertEquals(input.revision, resolved.snapshot.revision)
        assertEquals(input.seats.associate { it.id to it.copy(order = 0) }, resolved.snapshot.seats.associate { it.id to it.copy(order = 0) })
        assertEquals(resolved, ok(LifeRandomEngine.replay(input, request, resolved.recorded)))
        assertEquals(request, resolved.action)
    }
    @Test fun replayRejectsWrongKindsParametersEligibilityAndOrder() {
        val input = game()
        invalid(LifeRandomEngine.replay(input, action(RandomRequest.Coin(requestId)), RecordedRandomResult(requestId, at, RecordedRandomValue.SeatOrder(SnapshotList.empty()))))
        invalid(LifeRandomEngine.replay(input, action(RandomRequest.SeatOrder(requestId)), RecordedRandomResult(requestId, at, RecordedRandomValue.SeatOrder(SnapshotList.of(LifeFixtures.seatA)))))
        invalid(LifeRandomEngine.replay(input, action(RandomRequest.SeatPick(requestId, null, false)), RecordedRandomResult(requestId, at, RecordedRandomValue.SeatPick(LifeFixtures.seatA, SnapshotList.of(LifeFixtures.seatA)))))
        val dice = RecordedRandomValue.Dice(6, SnapshotList.of(1), 1, 1, 0, 0)
        invalid(LifeRandomEngine.replay(input, action(diceRequest(1, 8)), RecordedRandomResult(requestId, at, dice)))
        invalid(LifeRandomEngine.replay(input, action(RandomRequest.Coin(requestId)), RecordedRandomResult(EntityId("foreign"), at, RecordedRandomValue.Coin(true))))
    }
    @Test fun continuationRejectsWrongOriginalParametersCompletedTailAndLatestInsteadOfHistoricalContext() {
        val input = game(); val request = diceRequest(); val first = ok(LifeRandomEngine.resolve(input, action(request), at, Draw()))
        val context = RandomRequestContext(request, first.recorded)
        val continuation = action(RandomRequest.ContinueDice(requestId, 1)); val source = Draw()
        for(bad in listOf(context.copy(original = request.copy(sides = 8)), context.copy(original = request.copy(requested = 20_001)),
            context.copy(original = RandomRequest.Coin(requestId)))) invalid(LifeRandomEngine.resolve(input, continuation, at, source, bad))
        val tail = ok(LifeRandomEngine.resolve(input, continuation, at, Draw(), context))
        val latest = RandomRequestContext(request, tail.recorded)
        invalid(LifeRandomEngine.resolve(input, action(RandomRequest.ContinueDice(requestId, 2)), at, source, latest))
        invalid(LifeRandomEngine.replay(input, continuation, tail.recorded, latest))
        assertEquals(tail, ok(LifeRandomEngine.replay(input, continuation, tail.recorded, context)))
        assertEquals(0, source.calls)
    }
    @Test fun eligibleOrderAndPartialSourceFailureCannotReturnInventedSuccess() {
        val input = game(); val request = action(RandomRequest.SeatPick(requestId, null, false))
        val reversed = SnapshotList.copyOf(input.seats.sortedByDescending { it.order }.map { it.id })
        invalid(LifeRandomEngine.replay(input, request, RecordedRandomResult(requestId, at, RecordedRandomValue.SeatPick(reversed.first(), reversed))))
        var draws = 0
        val source = Draw { bound -> draws++; if(draws == 5) bound else 0 }
        val outcome = LifeRandomEngine.resolve(input, action(diceRequest()), at, source)
        assertTrue(outcome is Outcome.Failure); assertEquals(FailureCode.INTERNAL, (outcome as Outcome.Failure).error.code)
        assertEquals(5, draws); assertEquals(game(), input)
        val wall = UtcTimestamp(-100)
        val recorded = RecordedRandomResult(requestId, wall, RecordedRandomValue.Coin(true))
        assertSame(recorded, ok(LifeRandomEngine.replay(input, action(RandomRequest.Coin(requestId)), recorded)).recorded)
    }
}
