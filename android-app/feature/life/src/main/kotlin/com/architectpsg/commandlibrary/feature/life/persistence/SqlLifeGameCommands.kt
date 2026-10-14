package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.life.domain.*
import kotlin.coroutines.cancellation.CancellationException

/** Durable semantic commands only. Lifecycle recovery and ordered publication remain separate. */
public class SqlLifeGameCommands internal constructor(db: Database, codec: LifePayloadCodecPort,
    private val ids: IdSource, private val random: RandomSource, private val variants: LifeVariantActionBackend?) {
    public constructor(db: Database, codec: LifePayloadCodecPort, ids: IdSource, random: RandomSource) : this(db, codec, ids, random, null)
    private val mutations = SqlLifeGameMutator(db, codec)

    public suspend fun dispatch(id: GameId, action: GameAction, expected: Revision, reading: GameTimeObservation,
        cancel: CancelToken): Outcome<StoredLifeGame> = mutations.actionWithContext(id, expected, { context ->
        gameIdentity(reading.processSessionId.value, FailureCode.INVALID_INPUT)
        val snapshot = context.current.checkpoint.snapshot
        val actions = (action as? GameAction.Group)?.actions?.toList() ?: listOf(action)
        // Historical authentication precedes source calls; original requests stay reserved after undo.
        val execution = if (action is GameAction.RequestRandom) {
            val requestId = when (val request = action.request) {
                is RandomRequest.Dice -> request.requestId
                is RandomRequest.ContinueDice -> request.requestId
                is RandomRequest.Coin -> request.requestId
                is RandomRequest.SeatPick -> request.requestId
                is RandomRequest.SeatOrder -> request.requestId
                is RandomRequest.VariantZoneShuffle -> request.requestId
            }
            val history = gameValue(context.randomPredecessor(requestId))
            RandomExecution.Resolve(random, history?.let { RandomRequestContext(it.original, it.last) })
        } else null
        val allocation = ActionEventAllocation(context.eventId, Revision(snapshot.revision.value + 1),
            (action as? GameAction.Group)?.groupId ?: nextIdentity(),
            if (snapshot.result is Knowledge.Absent && actions.any { it is GameAction.Finish || it is GameAction.FinishWithOutcomes }) nextIdentity() else null,
            if (actions.any { it is GameAction.SwitchTurn }) nextIdentity() else null)
        val reduced = gameValue(LifeActionEventEngine.dispatch(context.current.checkpoint, action, reading, allocation, execution, variants))
        Outcome.Success(LifeActionWrite(reduced.archive, reduced.checkpoint))
    }, cancel)

    public suspend fun undo(id: GameId, expected: Revision?, reading: GameTimeObservation,
        cancel: CancelToken): Outcome<StoredLifeGame> = mutations.actionWithContext(id, expected, { context ->
        gameIdentity(reading.processSessionId.value, FailureCode.INVALID_INPUT)
        val target = gameValue(context.latestUndoTarget())
        val allocation = ActionEventAllocation(context.eventId, Revision(context.current.checkpoint.snapshot.revision.value + 1),
            (target.event.action as? GameAction.Group)?.groupId ?: nextIdentity())
        val reduced = gameValue(LifeActionEventEngine.undo(context.current.checkpoint, target, reading, allocation))
        Outcome.Success(LifeActionWrite(reduced.archive, reduced.checkpoint))
    }, cancel)

    private fun nextIdentity(): EntityId {
        val value = try { ids.nextId() } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { rejectGame(FailureCode.INTERNAL, "Game identity source failed") }
        gameIdentity(value, FailureCode.INTERNAL)
        return EntityId(value)
    }
}
