package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.DomainFailure
import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.GameAction
import com.architectpsg.commandlibrary.core.model.GameSnapshot
import com.architectpsg.commandlibrary.core.model.GameStatus
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.RandomSource
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.core.model.UtcTimestamp

/** SPEC G06 responsiveness cap for one roll; larger requests are split by [RandomTools.rollBatch]. */
internal const val MAX_DICE_PER_ROLL: Int = 10_000

internal data class DiceRoll(val sides: Int, val values: SnapshotList<Int>, val sum: Long)

/** One capped batch of a larger request. [remaining] rolls are still owed to the caller, never dropped. */
internal data class DiceBatch(val roll: DiceRoll, val remaining: Long)

internal enum class CoinFace { HEADS, TAILS }

/** Selection result with the exact eligible set it drew from, in table order, and the caller's time for the log. */
internal sealed interface SeatSelection {
    val eligible: SnapshotList<EntityId>
    val at: UtcTimestamp

    data class Selected(val seatId: EntityId, override val eligible: SnapshotList<EntityId>, override val at: UtcTimestamp) : SeatSelection
    data class NoEligibleSeat(override val eligible: SnapshotList<EntityId>, override val at: UtcTimestamp) : SeatSelection
}

/** Seat order change ready for the committing transaction; revision and event pointers are untouched. */
internal data class SeatOrderReduction(val snapshot: GameSnapshot, val action: GameAction.ReorderSeats)

/**
 * Random tools over an injected [RandomSource]. There is deliberately no default or seeded production source.
 * A source returning a value outside `0 until bound` yields an [FailureCode.INTERNAL] failure and no partial result.
 */
internal object RandomTools {
    fun rollDice(random: RandomSource, count: Int, sides: Int): Outcome<DiceRoll> {
        if (count !in 1..MAX_DICE_PER_ROLL) return invalidInput("Dice count must be 1..$MAX_DICE_PER_ROLL, was $count")
        if (sides < 1) return invalidInput("Die sides must be positive, was $sides")
        val values = ArrayList<Int>(count)
        var sum = 0L
        repeat(count) {
            val face = draw(random, sides).valueOr { return it } + 1
            values += face
            sum += face
        }
        return Outcome.Success(DiceRoll(sides, SnapshotList.copyOf(values), sum))
    }

    /** Rolls up to [MAX_DICE_PER_ROLL] of [requested] dice and reports how many are still owed. */
    fun rollBatch(random: RandomSource, requested: Long, sides: Int): Outcome<DiceBatch> {
        if (requested < 1) return invalidInput("Requested dice must be positive, was $requested")
        val count = minOf(requested, MAX_DICE_PER_ROLL.toLong()).toInt()
        val roll = rollDice(random, count, sides).valueOr { return it }
        return Outcome.Success(DiceBatch(roll, requested - count))
    }

    fun flipCoin(random: RandomSource): Outcome<CoinFace> =
        Outcome.Success(if (draw(random, 2).valueOr { return it } == 0) CoinFace.HEADS else CoinFace.TAILS)

    /** Uniform Fisher-Yates permutation; [items] is not modified. */
    fun <T> shuffle(random: RandomSource, items: List<T>): Outcome<SnapshotList<T>> {
        val shuffled = items.toMutableList()
        for (i in shuffled.lastIndex downTo 1) {
            val j = draw(random, i + 1).valueOr { return it }
            val held = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = held
        }
        return Outcome.Success(SnapshotList.copyOf(shuffled))
    }

    /** Permutes the table once. Seat IDs keep their players, decks and commanders; counters stay keyed by seat ID. */
    fun randomizeSeats(snapshot: GameSnapshot, random: RandomSource): Outcome<SeatOrderReduction> {
        val seats = SeatIndex.of(snapshot).valueOr { return it }
        val order = shuffle(random, seats.seats.values.sortedBy { it.order }.map { it.id }).valueOr { return it }
        return reorderSeats(snapshot, GameAction.ReorderSeats(order))
    }

    /** Applies an explicit permutation: position in [GameAction.ReorderSeats.seatIds] becomes each seat's order. */
    fun reorderSeats(snapshot: GameSnapshot, action: GameAction.ReorderSeats): Outcome<SeatOrderReduction> {
        if (snapshot.status == GameStatus.COMPLETED) return invalidInput("Game is completed; reopen it before reordering seats")
        val seats = SeatIndex.of(snapshot).valueOr { return it }
        val ids = action.seatIds
        if (ids.toSet().size != ids.size) return invalidInput("Seat order repeats a seat")
        if (ids.size != seats.seats.size || ids.any { !seats.contains(it) }) return invalidInput("Seat order must list every seat of this game exactly once")
        val reordered = ids.mapIndexed { index, id -> seats.seats.getValue(id).copy(order = index) }
        return Outcome.Success(SeatOrderReduction(snapshot.copy(seats = SnapshotList.copyOf(reordered)), action))
    }

    /**
     * Picks one seat from [eligible], which the caller derives (for example, excluding eliminated seats; the core
     * snapshot has no eliminated-seat field). The draw uses table order, so the caller's list order cannot bias a seeded run.
     */
    fun selectSeat(snapshot: GameSnapshot, random: RandomSource, eligible: SnapshotList<EntityId>, at: UtcTimestamp): Outcome<SeatSelection> {
        val seats = SeatIndex.of(snapshot).valueOr { return it }
        if (eligible.toSet().size != eligible.size) return invalidInput("Eligible seats repeat a seat")
        eligible.firstOrNull { !seats.contains(it) }?.let { return invalidInput("Eligible seat ${it.value} is not in this game") }
        val ordered = SnapshotList.copyOf(eligible.sortedBy { seats.seats.getValue(it).order })
        return Outcome.Success(
            when (ordered.size) {
                0 -> SeatSelection.NoEligibleSeat(ordered, at)
                1 -> SeatSelection.Selected(ordered.single(), ordered, at)
                else -> SeatSelection.Selected(ordered[draw(random, ordered.size).valueOr { return it }], ordered, at)
            },
        )
    }

    /** Opponent pick: [eligible] minus [askingSeat]. */
    fun selectOpponent(snapshot: GameSnapshot, random: RandomSource, eligible: SnapshotList<EntityId>, askingSeat: EntityId, at: UtcTimestamp): Outcome<SeatSelection> {
        if (snapshot.seats.none { it.id == askingSeat }) return invalidInput("Asking seat ${askingSeat.value} is not in this game")
        return selectSeat(snapshot, random, SnapshotList.copyOf(eligible.filter { it != askingSeat }), at)
    }

    private fun draw(random: RandomSource, bound: Int): Outcome<Int> {
        val value = random.nextInt(bound)
        return if (value in 0 until bound) Outcome.Success(value) else Outcome.Failure(
            DomainFailure(FailureCode.INTERNAL, "RandomSource returned $value outside 0 until $bound", SnapshotList.empty()),
        )
    }
}
