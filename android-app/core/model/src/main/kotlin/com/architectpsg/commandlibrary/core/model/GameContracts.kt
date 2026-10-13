package com.architectpsg.commandlibrary.core.model

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

public enum class GameStatus { ACTIVE, PAUSED, COMPLETED }
public data class DeckReference(public val id: DeckId, public val revision: Revision, public val name: String, public val tombstoned: Boolean)
public data class CommanderSlot(public val id: EntityId, public val identity: CardIdentity, public val label: String)
public data class Seat(
    public val id: EntityId,
    public val playerName: String,
    public val profileId: EntityId?,
    public val deck: Knowledge<DeckReference>,
    public val order: Int,
    public val commanders: SnapshotList<CommanderSlot>,
    public val appearance: SeatAppearance = SeatAppearance(null, null),
    public val participation: SeatParticipation = SeatParticipation.Active,
) { init { require(order >= 0) } }
public sealed interface CounterLossPolicy {
    public data object None : CounterLossPolicy
    public data class AtOrAbove(public val threshold: Int) : CounterLossPolicy
    public data class AtOrBelow(public val threshold: Int) : CounterLossPolicy
}
public data class CounterDefinition(
    public val id: EntityId,
    public val label: String,
    public val minimum: Int?,
    public val maximum: Int?,
    public val initial: Int,
    public val lossPolicy: CounterLossPolicy,
    public val custom: Boolean,
    public val role: CounterRole = CounterRole.OTHER,
    public val color: DisplayColor? = null,
    public val preset: CounterPreset? = null,
    public val resetPolicy: CounterResetPolicy = CounterResetPolicy.MANUAL,
) { init { require(minimum == null || maximum == null || minimum <= maximum); require(minimum == null || initial >= minimum); require(maximum == null || initial <= maximum) } }
public data class CounterState(
    public val definitionId: EntityId,
    public val seatId: EntityId,
    public val commanderSlotId: EntityId?,
    public val value: Int,
    public val hidden: Boolean,
)
public enum class SeatClockMode { NONE, CHESS, PER_TURN, SHARED }
public enum class ClockZeroPolicy { ALERT, PAUSE, ELIMINATE }
public enum class ClockUndoPolicy { RESTORE_TIME, KEEP_ELAPSED }
public data class SeatTime(public val seatId: EntityId, public val initial: Duration, public val remaining: Duration) { init { require(initial >= Duration.ZERO) } }
public data class ClockAnchor(public val wallTime: UtcTimestamp, public val monotonicElapsed: Duration, public val processSessionId: EntityId)
public data class SeatClockState(
    public val mode: SeatClockMode,
    public val seats: SnapshotList<SeatTime>,
    public val activeSeat: EntityId?,
    public val anchor: ClockAnchor?,
    public val paused: Boolean,
    public val zeroPolicy: ClockZeroPolicy,
    public val undoPolicy: ClockUndoPolicy,
    public val firedAlertIds: SnapshotList<EntityId>,
    public val shared: SharedCountdown? = null,
)
public data class ClockAlert(
    public val id: EntityId,
    public val gameId: GameId,
    public val seatId: EntityId,
    public val firedAt: UtcTimestamp,
    public val acknowledgedAt: UtcTimestamp?,
)
public enum class VariantNamespace { PLANECHASE, ARCHENEMY, BOUNTY, CUSTOM }
public data class VariantActionDefinition(
    public val id: String,
    public val label: String,
    public val instructions: String,
    public val fromZone: String?,
    public val toZone: String?,
    public val maximumPerPhase: Int?,
) { init { require(maximumPerPhase == null || maximumPerPhase > 0) } }
public data class VariantZoneDefinition(public val id: String, public val label: String, public val maximumCards: Int?, public val ordered: Boolean) { init { require(maximumCards == null || maximumCards >= 0) } }
public data class VariantDefinition(
    public val id: ProviderIdentity,
    public val namespace: VariantNamespace,
    public val version: String,
    public val instructions: String,
    public val allowedActions: SnapshotList<VariantActionDefinition>,
    public val zones: SnapshotList<VariantZoneDefinition>,
    public val phases: SnapshotList<String>,
    public val cards: SnapshotList<VariantCard>,
    public val provenance: Provenance,
    public val notice: SourceNotice,
)
public data class VariantCard(
    public val id: ProviderIdentity,
    public val name: String,
    public val text: String,
    public val image: Knowledge<ResourceUri>,
    public val actionIds: SnapshotList<String>,
    public val provenance: Provenance,
)
public data class VariantZone(public val zoneId: String, public val cardIds: SnapshotList<ProviderIdentity>)
public sealed interface VariantValue {
    public data class Text(public val value: String) : VariantValue
    public data class Integer(public val value: Long) : VariantValue
    public data class Flag(public val value: Boolean) : VariantValue
    public data class Card(public val value: ProviderIdentity) : VariantValue
}
public data class VariantProperty(public val key: String, public val value: VariantValue)
public data class VariantState(
    public val definitionId: ProviderIdentity,
    public val version: String,
    public val zones: SnapshotList<VariantZone>,
    public val drawn: SnapshotList<ProviderIdentity>,
    public val discarded: SnapshotList<ProviderIdentity>,
    public val phase: String,
    public val properties: SnapshotList<VariantProperty>,
    public val seed: Long,
    public val actionCounts: SnapshotList<VariantActionCount>,
)
public data class VariantActionCount(public val actionId: String, public val count: Int) { init { require(count >= 0) } }
public data class GameSettings(
    public val format: Knowledge<FormatId>,
    public val initialLife: Int,
    public val counterDefinitions: SnapshotList<CounterDefinition>,
    public val variantDefinitions: SnapshotList<VariantDefinition>,
    public val clockMode: SeatClockMode,
    public val randomSeed: Long,
    public val presentation: GamePresentation = GamePresentation(),
    public val adjustmentStep: Int = 1,
    public val deductLifeForCommanderDamage: Boolean = false,
    public val elapsedTimers: ElapsedTimerSettings = ElapsedTimerSettings(),
    public val alertPreferences: GameAlertPreferences = GameAlertPreferences(),
) { init { require(adjustmentStep > 0) { "Adjustment step must be positive" } } }
public sealed interface GameAction {
    public data class RequestRandom(public val request: RandomRequest) : GameAction
    public data class AdjustCounter(public val seatId: EntityId, public val definitionId: EntityId, public val commanderSlotId: EntityId?, public val delta: Int) : GameAction
    public data class SetCounterHidden(public val seatId: EntityId, public val definitionId: EntityId, public val hidden: Boolean) : GameAction
    public data class RenameSeat(public val seatId: EntityId, public val name: String) : GameAction
    public data class ReorderSeats(public val seatIds: SnapshotList<EntityId>) : GameAction
    public data class SwitchTurn(public val seatId: EntityId) : GameAction
    public data class ClockPause(public val paused: Boolean) : GameAction
    public data class Variant(public val definitionId: ProviderIdentity, public val actionId: String, public val selectedCards: SnapshotList<ProviderIdentity>) : GameAction
    public data class Finish(public val winnerSeatIds: SnapshotList<EntityId>, public val reason: String) : GameAction
    public data class Reopen(public val reason: String) : GameAction
    public data class Group(public val actions: SnapshotList<GameAction>, public val groupId: EntityId) : GameAction
}
public data class GameEvent(
    public val id: Long,
    public val gameId: GameId,
    public val groupId: EntityId,
    public val action: GameAction,
    public val at: UtcTimestamp,
    public val undoOfEventId: Knowledge<Long>,
    public val revision: Revision,
    public val randomResults: SnapshotList<RecordedRandomResult> = SnapshotList.empty(),
) { init { require(id >= 0) } }
public data class GameResult(
    public val id: EntityId,
    public val gameId: GameId,
    public val winners: SnapshotList<EntityId>,
    public val finishedAt: UtcTimestamp,
    public val reason: String,
    public val revision: Revision,
    public val retracted: Boolean,
)
public data class GameSnapshot(
    public val id: GameId,
    public val version: Int,
    public val revision: Revision,
    public val settings: GameSettings,
    public val status: GameStatus,
    public val startedAt: UtcTimestamp,
    public val endedAt: Knowledge<UtcTimestamp>,
    public val seats: SnapshotList<Seat>,
    public val counters: SnapshotList<CounterState>,
    public val activeSeatId: EntityId?,
    public val turnNumber: Long,
    public val clock: SeatClockState,
    public val variants: SnapshotList<VariantState>,
    public val lastEventId: Knowledge<Long>,
    public val result: Knowledge<GameResult>,
    public val designations: GameDesignations = GameDesignations(),
    public val elapsed: ElapsedTimerState = ElapsedTimerState(Duration.ZERO, Duration.ZERO, true, null),
    public val alerts: SnapshotList<GameAlert> = SnapshotList.empty(),
) { init { require(version > 0 && turnNumber >= 0) } }
public interface GameService {
    public suspend fun resume(id: GameId, cancel: CancelToken): Outcome<GameSnapshot>
    public suspend fun dispatch(id: GameId, action: GameAction, expected: Revision, cancel: CancelToken): Outcome<GameSnapshot>
    public suspend fun undo(id: GameId, cancel: CancelToken): Outcome<GameSnapshot>
    public fun watch(id: GameId): Flow<GameSnapshot>
}
