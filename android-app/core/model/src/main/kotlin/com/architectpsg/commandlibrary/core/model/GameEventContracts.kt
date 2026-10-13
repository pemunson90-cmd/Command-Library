package com.architectpsg.commandlibrary.core.model

import kotlin.time.Duration

/** Requests contain intent, never caller-supplied random results. */
public sealed interface RandomRequest {
    public val requestId: EntityId
    public data class Dice(public override val requestId: EntityId, public val requested: Long, public val sides: Int) : RandomRequest {
        init { require(requested > 0 && sides > 0) { "Dice count and sides must be positive" } }
    }
    public data class ContinueDice(public override val requestId: EntityId, public val batchIndex: Long) : RandomRequest {
        init { require(batchIndex >= 0) { "Batch index must be nonnegative" } }
    }
    public data class Coin(public override val requestId: EntityId) : RandomRequest
    public data class SeatPick(public override val requestId: EntityId, public val asker: EntityId?, public val opponentsOnly: Boolean) : RandomRequest {
        init { require(!opponentsOnly || asker != null) { "Opponent selection requires an asker" } }
    }
    public data class SeatOrder(public override val requestId: EntityId) : RandomRequest
}
/** Local metadata coherence does not prove actual draw history or a valid game permutation. */
public sealed interface RecordedRandomValue {
    public data class Dice(
        public val sides: Int,
        public val faces: SnapshotList<Int>,
        public val sum: Long,
        public val requested: Long,
        public val batchIndex: Long,
        public val remaining: Long,
    ) : RecordedRandomValue {
        init {
            require(sides > 0 && faces.size in 1..MAX_FACES) { "Recorded dice require positive sides and a bounded nonempty batch" }
            require(faces.all { it in 1..sides }) { "Recorded faces must lie within the die range" }
            // MAX_FACES positive Int values sum exactly within Long, without overflow.
            require(sum == faces.fold(0L) { total, face -> total + face.toLong() }) { "Recorded dice sum must match every face" }
            require(requested > 0 && batchIndex >= 0 && remaining >= 0) { "Invalid request, batch index or remainder" }
            require(remaining <= requested - faces.size.toLong()) { "Recorded batch and remainder exceed the request" }
            val consumedBefore = requested - faces.size.toLong() - remaining
            val minimumPriorBatches = consumedBefore / MAX_FACES + if (consumedBefore % MAX_FACES == 0L) 0L else 1L
            require(batchIndex in minimumPriorBatches..consumedBefore) { "Prior batch count cannot explain the recorded consumed count" }
        }
        public companion object { public const val MAX_FACES: Int = 10_000 }
    }
    public data class Coin(public val heads: Boolean) : RecordedRandomValue
    public data class SeatPick(public val selected: EntityId?, public val eligible: SnapshotList<EntityId>) : RecordedRandomValue {
        init {
            require(eligible.toSet().size == eligible.size) { "Eligible seat identities must be unique" }
            require((selected == null) == eligible.isEmpty() && (selected == null || selected in eligible)) { "Selected seat must agree with the eligible set" }
        }
    }
    public data class SeatOrder(public val seatIds: SnapshotList<EntityId>) : RecordedRandomValue {
        init { require(seatIds.toSet().size == seatIds.size) { "Recorded seat order must not repeat identities" } }
    }
}
public data class RecordedRandomResult(public val requestId: EntityId, public val at: UtcTimestamp, public val value: RecordedRandomValue)
public data class GameTimeObservation(public val monotonic: Duration, public val wall: UtcTimestamp, public val processSessionId: EntityId) {
    init { require(monotonic.isFinite()) { "Time observation must be finite" } }
}
/** Immutable original action boundary; the service separately proves actual event/undo lineage. */
public data class GameActionProvenance(
    public val eventId: Long,
    public val before: GameSnapshot,
    public val afterAction: GameSnapshot,
    public val reading: GameTimeObservation,
) {
    init {
        require(eventId >= 0) { "Event identity must be nonnegative" }
        require(before.id == afterAction.id) { "Action provenance must retain game identity" }
        require(before.revision.value < Long.MAX_VALUE && afterAction.revision.value == before.revision.value + 1) {
            "Action provenance must advance exactly one representable revision"
        }
    }
}
public data class GameCheckpoint(
    public val snapshot: GameSnapshot,
    public val lastSettledEventId: Knowledge<Long>,
    public val recoveredProcessSessionId: EntityId?,
)
public data class GameEventArchive(public val version: Int, public val event: GameEvent, public val provenance: GameActionProvenance) {
    init {
        require(version > 0) { "Archive version must be positive" }
        require(event.id == provenance.eventId && event.gameId == provenance.before.id && event.revision == provenance.afterAction.revision) {
            "Archive event must agree with its action provenance identity and revision"
        }
    }
}
