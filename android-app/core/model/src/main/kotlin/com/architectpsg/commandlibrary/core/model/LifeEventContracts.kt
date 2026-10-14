package com.architectpsg.commandlibrary.core.model

import kotlin.time.Duration

public data class GameTimeObservation(public val monotonic: Duration, public val wall: UtcTimestamp, public val processSessionId: EntityId) {
    init { require(monotonic.isFinite() && monotonic >= Duration.ZERO) { "Monotonic observation must be finite and nonnegative" } }
}

/** Original immutable action-time states; later checkpoints do not replace this undo input. */
public data class GameActionProvenance(
    public val eventId: Long,
    public val before: GameSnapshot,
    public val afterAction: GameSnapshot,
    public val reading: GameTimeObservation,
) {
    init {
        require(eventId >= 0) { "Event identity must be nonnegative" }
        require(before.id == afterAction.id) { "Action snapshots must describe the same game" }
        require(before.revision.value < Long.MAX_VALUE && afterAction.revision.value == before.revision.value + 1) {
            "Action snapshots must have consecutive revisions"
        }
        require(afterAction.lastEventId == Knowledge.Known(eventId)) { "After-action event pointer must match provenance" }
    }
}
public data class GameCheckpoint(
    public val snapshot: GameSnapshot,
    public val lastSettledEventId: Knowledge<Long>,
    public val recoveredProcessSessionId: EntityId?,
) {
    init {
        require(lastSettledEventId !is Knowledge.Known || lastSettledEventId.value >= 0) { "Settled event identity must be nonnegative" }
    }
}
/** Structural validation cannot certify authenticated service event/undo lineage. */
public data class GameEventArchive(public val version: Int, public val event: GameEvent, public val provenance: GameActionProvenance) {
    init {
        require(version > 0) { "Archive version must be positive" }
        require(event.id == provenance.eventId && event.gameId == provenance.afterAction.id && event.revision == provenance.afterAction.revision) {
            "Archived event must match action provenance"
        }
    }
}
