package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*

/** Bounded predecessor facts, authenticated by service; historical predecessor for replay. */
internal data class RandomRequestContext(val original: RandomRequest, val last: RecordedRandomResult)
internal data class RandomReduction(val snapshot: GameSnapshot, val action: GameAction.RequestRandom, val recorded: RecordedRandomResult)

/** Proposed recorded values only. Service owns request uniqueness, durable retries and predecessor lookup. */
internal object LifeRandomEngine {
    fun resolve(snapshot: GameSnapshot, action: GameAction, at: UtcTimestamp, random: RandomSource,
        context: RandomRequestContext? = null, variants: LifeVariantShuffleAuthority? = null): Outcome<RandomReduction> {
        val plan = prepare(snapshot, action, context).valueOr { return it }
        val value: RecordedRandomValue = when (val request = plan.action.request) {
            is RandomRequest.Dice, is RandomRequest.ContinueDice -> {
                val original = plan.dice!!
                val batch = RandomTools.rollBatch(random, plan.owed, original.sides).valueOr { return it }
                RecordedRandomValue.Dice(original.sides, batch.roll.values, batch.roll.sum, original.requested, plan.batchIndex, batch.remaining)
            }
            is RandomRequest.Coin -> RecordedRandomValue.Coin(RandomTools.flipCoin(random).valueOr { return it } == CoinFace.HEADS)
            is RandomRequest.SeatPick -> {
                val selected = RandomTools.selectSeat(snapshot, random, plan.eligible, at).valueOr { return it }
                RecordedRandomValue.SeatPick((selected as? SeatSelection.Selected)?.seatId, plan.eligible)
            }
            is RandomRequest.SeatOrder -> {
                val ordered = RandomTools.randomizeSeats(snapshot, random).valueOr { return it }
                RecordedRandomValue.SeatOrder(ordered.action.seatIds)
            }
            is RandomRequest.VariantZoneShuffle -> {
                val authority = variants ?: return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Variant shuffle requires installed authored manager")
                authority.authorizeShuffle(snapshot, request).valueOr { return it }
                val facts = LifeVariantShuffle.prepare(snapshot, request).valueOr { return it }
                val order = RandomTools.shuffle(random, facts.zone.cardIds).valueOr { return it }
                RecordedRandomValue.VariantZoneShuffle(request.definitionId, facts.definition.version, request.actionId, request.zoneId,
                    request.policyKey, request.policyValue, facts.zone.cardIds, order)
            }
        }
        // Apply the same checked recorded representation used by replay; no second random draw.
        return replay(snapshot, action, RecordedRandomResult(plan.action.request.id(), at, value), context)
    }

    /** Context is the historical predecessor, not the ledger's latest record at replay time. */
    fun replay(snapshot: GameSnapshot, action: GameAction, recorded: RecordedRandomResult,
        context: RandomRequestContext? = null): Outcome<RandomReduction> {
        val plan = prepare(snapshot, action, context).valueOr { return it }
        if (recorded.requestId != plan.action.request.id()) return invalidInput("Recorded request identity mismatch")
        var next = snapshot
        when (plan.action.request) {
            is RandomRequest.Dice, is RandomRequest.ContinueDice -> {
                val value = recorded.value as? RecordedRandomValue.Dice ?: return invalidInput("Recorded result is not dice")
                val original = plan.dice!!
                if (value.sides != original.sides || value.requested != original.requested || value.batchIndex != plan.batchIndex ||
                    value.faces.size.toLong() != minOf(plan.owed, MAX_DICE_PER_ROLL.toLong()) || value.remaining != plan.owed - value.faces.size) return invalidInput("Recorded dice parameters/batch/remainder mismatch")
            }
            is RandomRequest.Coin -> if (recorded.value !is RecordedRandomValue.Coin) return invalidInput("Recorded result is not a coin")
            is RandomRequest.SeatPick -> {
                val value = recorded.value as? RecordedRandomValue.SeatPick ?: return invalidInput("Recorded result is not a seat pick")
                if (value.eligible != plan.eligible) return invalidInput("Recorded eligible seats differ from current active order")
            }
            is RandomRequest.SeatOrder -> {
                val value = recorded.value as? RecordedRandomValue.SeatOrder ?: return invalidInput("Recorded result is not a seat order")
                next = RandomTools.reorderSeats(snapshot, GameAction.ReorderSeats(value.seatIds)).valueOr { return it }.snapshot
            }
            is RandomRequest.VariantZoneShuffle -> next = LifeVariantShuffle.replay(snapshot, plan.action.request as RandomRequest.VariantZoneShuffle, recorded.value).valueOr { return it }
        }
        return Outcome.Success(RandomReduction(next, plan.action, recorded))
    }

    private data class Plan(val action: GameAction.RequestRandom, val dice: RandomRequest.Dice? = null,
        val batchIndex: Long = 0, val owed: Long = 0, val eligible: SnapshotList<EntityId> = SnapshotList.empty())

    private fun prepare(snapshot: GameSnapshot, action: GameAction, context: RandomRequestContext?): Outcome<Plan> {
        if (snapshot.status == GameStatus.COMPLETED) return invalidInput("Reopen completed game before requesting randomness")
        val command = action as? GameAction.RequestRandom ?: return invalidInput("Random resolution requires a single request action")
        SnapshotGraphValidator.validate(snapshot).valueOr { return it }
        val request = command.request
        if (request is RandomRequest.ContinueDice) {
            val original = context?.original as? RandomRequest.Dice ?: return invalidInput("Continuation requires original dice request and committed predecessor")
            val previous = context.last.value as? RecordedRandomValue.Dice ?: return invalidInput("Predecessor is not a dice batch")
            if (request.requestId != original.requestId || context.last.requestId != original.requestId || previous.sides != original.sides ||
                previous.requested != original.requested || previous.remaining <= 0 || previous.batchIndex == Long.MAX_VALUE ||
                request.batchIndex != previous.batchIndex + 1) return invalidInput("Continuation identity/parameters/index/remainder mismatch")
            return Outcome.Success(Plan(command, original, request.batchIndex, previous.remaining))
        }
        if (context != null) return invalidInput("Initial random request identity is already committed")
        return when (request) {
            is RandomRequest.Dice -> Outcome.Success(Plan(command, request, owed = request.requested))
            is RandomRequest.SeatPick -> {
                if (request.asker != null && snapshot.seats.none { it.id == request.asker }) return invalidInput("Asking seat is not in this game")
                val eligible = SnapshotList.copyOf(snapshot.seats.sortedBy { it.order }.filter {
                    it.participation == SeatParticipation.Active && (!request.opponentsOnly || it.id != request.asker)
                }.map { it.id })
                Outcome.Success(Plan(command, eligible = eligible))
            }
            is RandomRequest.Coin, is RandomRequest.SeatOrder, is RandomRequest.VariantZoneShuffle -> Outcome.Success(Plan(command))
            is RandomRequest.ContinueDice -> return invalidInput("Missing continuation predecessor")
        }
    }

    private fun RandomRequest.id(): EntityId = when (this) {
        is RandomRequest.Dice -> requestId
        is RandomRequest.ContinueDice -> requestId
        is RandomRequest.Coin -> requestId
        is RandomRequest.SeatPick -> requestId
        is RandomRequest.SeatOrder -> requestId
        is RandomRequest.VariantZoneShuffle -> requestId
    }
}
