package com.architectpsg.commandlibrary.core.model

import kotlin.time.Duration

/** Explicit metadata; complete game graphs are validated at creation/import/resume. */
public enum class CounterRole { LIFE, COMMANDER_DAMAGE, COMMANDER_TAX, OTHER }
public enum class CounterResetPolicy { MANUAL, ON_TURN_START }
public enum class CounterPreset { POISON, ENERGY, EXPERIENCE, RAD, STORM, MANA, TREASURE, CHARGE, BLOOD, FOOD, CLUE, RING, PLANESWALKER, SAGA, SIEGE }
public data class DisplayColor(public val argb: Long) {
    init { require(argb in 0L..0xffffffffL) { "ARGB must be an unsigned 32-bit value" } }
}
public enum class SeatOrientation { UPRIGHT, CLOCKWISE, INVERTED, COUNTERCLOCKWISE }
public enum class GameLayoutMode { TABLETOP, OPERATOR }
public enum class ArtFit { CONTAIN, COVER }
public data class ArtCrop(public val left: Double, public val top: Double, public val right: Double, public val bottom: Double) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0.0..1.0 }) {
            "Crop coordinates must be finite and normalized"
        }
        require(left < right && top < bottom) { "Crop must have positive width and height" }
    }
}
/** Already local art references never authorize a download. */
public sealed interface GameArtReference {
    public data class Local(public val art: LocalArtReference) : GameArtReference
    public data class CachedCard(public val identity: CardIdentity, public val assetId: EntityId) : GameArtReference
}
public data class SeatAppearance(
    public val color: DisplayColor?, public val art: GameArtReference?,
    public val fit: ArtFit = ArtFit.COVER, public val crop: ArtCrop? = null,
    public val orientation: SeatOrientation = SeatOrientation.UPRIGHT,
)
public data class GamePresentation(
    public val layout: GameLayoutMode = GameLayoutMode.TABLETOP,
    public val arrangementId: String? = null, public val keepAwake: Boolean = false,
)
public enum class SeatExitKind { CONFIRMED_LOSS, CONCEDED, DISCONNECTED, MANUAL }
public data class LossEvidence(
    public val definitionId: EntityId?, public val commanderSlotId: EntityId?,
    public val observedValue: Int?, public val policy: CounterLossPolicy?,
    public val ruleVersion: Knowledge<String>, public val rationale: String,
)
public sealed interface SeatParticipation {
    public data object Active : SeatParticipation
    public data class Eliminated(
        public val kind: SeatExitKind, public val reason: String,
        public val evidence: LossEvidence?, public val at: UtcTimestamp,
    ) : SeatParticipation
}
public enum class DayNightState { UNSET, DAY, NIGHT }
public data class GameDesignations(
    public val monarch: EntityId? = null, public val initiative: EntityId? = null,
    public val initiativeNote: String = "", public val dayNight: DayNightState = DayNightState.UNSET,
    public val blessedSeats: SnapshotList<EntityId> = SnapshotList.empty(),
)
public enum class SeatResultKind { WIN, LOSS, DRAW }
public data class SeatResult(public val seatId: EntityId, public val result: SeatResultKind, public val exit: SeatExitKind?)
/** Finite overtime is representable; the service applies the selected zero policy. */
public data class SharedCountdown(public val initial: Duration, public val remaining: Duration) {
    init {
        require(initial.isFinite() && initial >= Duration.ZERO) { "Initial countdown must be finite and nonnegative" }
        require(remaining.isFinite()) { "Remaining countdown must be finite" }
    }
}
public data class ElapsedTimerState(
    public val gameElapsed: Duration, public val turnElapsed: Duration,
    public val paused: Boolean, public val anchor: ClockAnchor?,
    public val turnLineageId: Knowledge<EntityId> = Knowledge.Unknown("Legacy turn lineage not supplied", SnapshotList.empty()),
) {
    init {
        require(gameElapsed.isFinite() && gameElapsed >= Duration.ZERO) { "Game elapsed must be finite and nonnegative" }
        require(turnElapsed.isFinite() && turnElapsed >= Duration.ZERO) { "Turn elapsed must be finite and nonnegative" }
    }
}
public sealed interface GameAlertTarget {
    public data class SeatClock(public val seatId: EntityId) : GameAlertTarget
    public data object SharedCountdown : GameAlertTarget
    public data object TurnTimer : GameAlertTarget
    public data object GameTimer : GameAlertTarget
}
public data class GameAlert(
    public val id: EntityId, public val gameId: GameId, public val target: GameAlertTarget,
    public val firedAt: UtcTimestamp, public val acknowledgedAt: UtcTimestamp?,
)
public data class GameAlertPreferences(public val visual: Boolean = true, public val haptic: Boolean = false, public val audio: Boolean = false)
public data class ElapsedTimerSettings(
    public val enabled: Boolean = true, public val turnWarning: Duration? = null, public val gameWarning: Duration? = null,
) {
    init {
        require(turnWarning == null || (turnWarning.isFinite() && turnWarning > Duration.ZERO)) { "Turn warning must be positive and finite" }
        require(gameWarning == null || (gameWarning.isFinite() && gameWarning > Duration.ZERO)) { "Game warning must be positive and finite" }
    }
}
