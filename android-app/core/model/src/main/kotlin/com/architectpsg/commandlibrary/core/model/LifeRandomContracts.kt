package com.architectpsg.commandlibrary.core.model

/** Requests are resolved and recorded by the transactional service, never by replay. */
public sealed interface RandomRequest {
    public data class Dice(public val requestId: EntityId, public val requested: Long, public val sides: Int) : RandomRequest {
        init { require(requested > 0 && sides > 0) { "Dice count and sides must be positive" } }
    }
    public data class ContinueDice(public val requestId: EntityId, public val batchIndex: Long) : RandomRequest {
        init { require(batchIndex > 0) { "Continuation starts after the initial batch" } }
    }
    public data class Coin(public val requestId: EntityId) : RandomRequest
    public data class SeatPick(public val requestId: EntityId, public val asker: EntityId?, public val opponentsOnly: Boolean) : RandomRequest {
        init { require(!opponentsOnly || asker != null) { "Opponent selection needs an asking seat" } }
    }
    public data class SeatOrder(public val requestId: EntityId) : RandomRequest
}

/** Recorded data contains the actual result; replay consumes no random source. */
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
            require(sides > 0 && requested > 0) { "Dice count and sides must be positive" }
            require(faces.size in 1..10_000) { "A committed batch contains 1..10000 faces" }
            require(faces.all { it in 1..sides }) { "Recorded face outside die range" }
            require(sum == faces.fold(0L) { total, face -> total + face }) { "Recorded sum must match faces" }
            // Guard before multiplying, including Long.MAX_VALUE-sized requests.
            require(batchIndex >= 0 && batchIndex <= (requested - 1) / 10_000) { "Batch index outside request" }
            val owed = requested - batchIndex * 10_000
            require(faces.size.toLong() == minOf(owed, 10_000)) { "Each batch must consume the capped remainder" }
            require(remaining == owed - faces.size) { "Recorded remainder must retain all unrolled dice" }
        }
    }
    public data class Coin(public val heads: Boolean) : RecordedRandomValue
    public data class SeatPick(public val selected: EntityId?, public val eligible: SnapshotList<EntityId>) : RecordedRandomValue {
        init {
            require(eligible.toSet().size == eligible.size) { "Eligible seats must be distinct" }
            require(if (eligible.isEmpty()) selected == null else selected != null && selected in eligible) {
                "Selection must be an eligible seat, or null for an empty eligible set"
            }
        }
    }
    public data class SeatOrder(public val seatIds: SnapshotList<EntityId>) : RecordedRandomValue {
        init { require(seatIds.toSet().size == seatIds.size) { "Seat order must not repeat a seat" } }
    }
}
public data class RecordedRandomResult(public val requestId: EntityId, public val at: UtcTimestamp, public val value: RecordedRandomValue)
