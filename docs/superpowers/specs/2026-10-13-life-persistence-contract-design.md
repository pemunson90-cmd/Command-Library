# Life persistence contract design

2026-10-13. **Bounded model units architecture/source-approved; service/schema/codec implementation remains review-gated.** No product implementation, migration, build or runtime verification is represented by this document. Root owns public contracts, schema, configuration and DI. The user approved the overall execution plan; this draft prepares the next technical review, not another product-permission request.

## Intent and authority

Make the accepted private life reducers usable by a real offline, transactional service: creation, resume, grouped actions, undo, timers, random histories and results must survive restart. Preserve all G01–G13/PX07 requirements, stable seat/commander/deck identities and D-25 confirmation before elimination. LifeElk screenshots are references for interaction/visual work, not permission to copy assets or replace scope.

Authority: [life task](../../../coordination/tasks/cx-m2-life-functional-01.md), [D-25](../../gauntlet/DECISIONS.md#d-25-life-contract-rulings-and-delegated-shared-clock-semantics), [dispositions 1–14](../../verification/m2-life/CONTRACT-DISPOSITIONS.md), [SPEC G01–G13](../../gauntlet/SPEC.md), [PX07](../../gauntlet/PARITY_ACCEPTANCE.md), [logical model](../../gauntlet/DATA_MODEL.md), [life architecture](../../architecture/life/ARCHITECT.md), [Kotlin contracts](../../architecture/KOTLIN_CONTRACTS.md), [ownership](../../architecture/KOTLIN_OWNERSHIP_MAP.md), [accepted undo integration conditions](../../verification/m2-life/DOMAIN-REREVIEW.md), [reference brief](../../../coordination/chat-handoffs/lifeelk-reference-brief-20261013.md).

Current source: `android-app/core/model/.../GameContracts.kt`; `TransferContracts.kt` ProfileExchange/GameExchange; `feature/life/.../domain/{CounterReducer,SeatClockReducer,RandomTools}.kt`. StorageSchemaDatabase is now version5 (durable origin cooldown) with no life tables. Existing domain acceptance is not persisted-service acceptance.

## Approach and compatibility

Recommend typed additive DTOs/actions plus a new `PersistedGameService : GameService` port. Keep the existing GameService signatures and append constructor fields with explicit defaults; new UI depends on the stronger port. This avoids replacing existing IDs, rewriting accepted reducers or putting untyped maps into game state. Alternatives: replacing every DTO at once creates unnecessary fixture/consumer churn; a generic JSON property bag hides invariants and replay failures.

Defaults below describe real absence or legacy state. They never certify an incomplete imported game. New creation/import/resume validates role bindings, timing equality, identities and codec version. Unrecoverable missing required data yields CORRUPT_DATA or an explicit import repair preview, not inferred labels or fake defaults. Source compatibility is practical, not a promised binary ABI. New sealed action cases require updating exhaustive consumers; model compilation verifies those changes.

Kotlin snippets specify named shapes and parameter types; use existing `GameId`, `EntityId`, `ProviderIdentity`, `Revision`, `Knowledge`, `SnapshotList`, `ClockAnchor`, `Duration`, `Outcome`, `PageRequest` and `Page`. These are declarations to review, not compiled source.

## Counter and setup shapes

```kotlin
enum class CounterRole { LIFE, COMMANDER_DAMAGE, COMMANDER_TAX, OTHER }
enum class CounterResetPolicy { MANUAL, ON_TURN_START }
data class DisplayColor(val argb: Long) // 0..0xffffffff, independent of card Color
enum class CounterPreset { POISON, ENERGY, EXPERIENCE, RAD, STORM, MANA,
    TREASURE, CHARGE, BLOOD, FOOD, CLUE, RING, PLANESWALKER, SAGA, SIEGE }
enum class SeatOrientation { UPRIGHT, CLOCKWISE, INVERTED, COUNTERCLOCKWISE }
enum class GameLayoutMode { TABLETOP, OPERATOR }
enum class ArtFit { CONTAIN, COVER }
data class ArtCrop(val left: Double, val top: Double, val right: Double, val bottom: Double)
sealed interface GameArtReference {
    data class Local(val art: LocalArtReference) : GameArtReference
    data class CachedCard(val identity: CardIdentity, val assetId: EntityId) : GameArtReference
}
data class SeatAppearance(val color: DisplayColor?, val art: GameArtReference?,
    val fit: ArtFit = ArtFit.COVER, val crop: ArtCrop? = null,
    val orientation: SeatOrientation = SeatOrientation.UPRIGHT)
data class GamePresentation(val layout: GameLayoutMode = GameLayoutMode.TABLETOP,
    val arrangementId: String? = null, val keepAwake: Boolean = false)
```

Append to CounterDefinition: `role: CounterRole = OTHER`, `color: DisplayColor? = null`, `preset: CounterPreset? = null`, `resetPolicy: CounterResetPolicy = MANUAL`. OTHER is not a life binding. Exactly one LIFE and at most one of each commander role; all role IDs refer to frozen game definitions. LIFE.initial equals settings.initialLife. Slot scope remains seat/any-commander/own-commander according to role, including stolen own commander damage. Custom definitions default to no loss policy; explicit custom thresholds remain possible. Preset identity does not silently import poison loss semantics into rad or object counters. Turn reset is explicit and included in the SwitchTurn transaction/group undo; mana reset does not pretend to automate every game phase.

Append Seat `appearance: SeatAppearance = SeatAppearance(null, null)` and GameSettings `presentation: GamePresentation = GamePresentation()`, `adjustmentStep: Int = 1`, `deductLifeForCommanderDamage: Boolean = false`. Step is positive. Add `SetCounter(seatId: EntityId, definitionId: EntityId, commanderSlotId: EntityId?, value: Int)` for exact numeric input; service converts it into a checked logged delta independent of step, rejecting an unrepresentable delta. Add actions `SetAdjustmentStep(step)` and `SetCommanderLifeDeduction(enabled)`; actual damage/life remains one atomic flat group. Crop coordinates are finite normalized0..1 with left<right/top<bottom; local art stays private and checksum-validated. Cached references never cause a download; unavailable art falls back to color/text. Arrangement IDs are versioned local layout choices, not physical coordinates or a hinge guarantee. Saved orientation/order follows seat identity. Existing commander slots retain separate CardIdentity/art lookup; partners do not share one slot.

Applying a profile allocates fresh per-game seat/commander-slot IDs while preserving commander CardIdentity; selecting one profile twice cannot alias counters. Creation copies definitions/profile preferences into immutable game settings; later profile edits do not mutate a running game's history. Definitions/ranges/preset colors may be changed through `GameAction.UpdateCounterDefinition(definition)` only with the same ID/role and validation of all existing values; `AddCounterDefinition(definition)` allows OTHER/custom additions. Definitions with recorded states/history are hidden rather than deleted. This supplies durable custom labels/colors without corrupting earlier events. Object presets use distinct stable OTHER definition IDs per configured object, with explicit labels and no player-loss policy; a Saga counter is not represented as poison or shared with another object's subtotal.

## Participation, designations and results

```kotlin
enum class SeatExitKind { CONFIRMED_LOSS, CONCEDED, DISCONNECTED, MANUAL }
data class LossEvidence(val definitionId: EntityId?, val commanderSlotId: EntityId?,
    val observedValue: Int?, val policy: CounterLossPolicy?,
    val ruleVersion: Knowledge<String>, val rationale: String)
sealed interface SeatParticipation {
    data object Active : SeatParticipation
    data class Eliminated(val kind: SeatExitKind, val reason: String,
        val evidence: LossEvidence?, val at: UtcTimestamp) : SeatParticipation
}
enum class DayNightState { UNSET, DAY, NIGHT }
data class GameDesignations(val monarch: EntityId? = null, val initiative: EntityId? = null,
    val initiativeNote: String = "", val dayNight: DayNightState = DayNightState.UNSET,
    val blessedSeats: SnapshotList<EntityId> = SnapshotList.empty())
enum class SeatResultKind { WIN, LOSS, DRAW }
data class SeatResult(val seatId: EntityId, val result: SeatResultKind, val exit: SeatExitKind?)
```

Append Seat `participation: SeatParticipation = Active`; GameSnapshot `designations: GameDesignations = GameDesignations()`; GameResult `seatResults: SnapshotList<SeatResult> = empty`. New results contain exactly one outcome for every seat; winners agrees with WIN entries, and a draw has no winners. Legacy winner-only results retain their known winners without fabricating historical concede/disconnect reasons; an explicit legacy conversion policy must precede import.

New GameAction cases: `EliminateSeat(seatId, kind, reason, evidence?)`, `RestoreSeat(seatId, reason)`, `SetMonarch(seatId?)`, `SetInitiative(seatId?, note)`, `SetDayNight(state)`, `SetBlessing(seatId, blessed)`, `SetSeatAppearance(seatId, appearance)`, `SetGamePresentation(presentation)`, `FinishWithOutcomes(seatResults, reason)`. These are explicit user decisions; a reducer loss/timeout report does not dispatch EliminateSeat or Finish. Override is recorded as the absence/rejection of a candidate plus reason through `DismissLossCandidate(candidate: LossEvidence, seatId, reason)`; dismissal applies to that observation, not all future threshold crossings. A restored seat keeps its exact counters, commander damage and identity. Active-only random eligibility excludes eliminated seats; opponent pick also excludes its asker.

Legacy Finish remains usable: empty winners means draw; nonempty valid winners means those seats win and others lose. New UI uses explicit outcomes and records exit reasons. Finish settles and pauses all timing in the same transaction as event/snapshot/unique result. Reopen and undo update/retract that result identity instead of inserting another result. Reopening requires explicit timing resume; it never charges the unknown completed interval.

## Timing and alerts

```kotlin
data class SharedCountdown(val initial: Duration, val remaining: Duration)
data class ElapsedTimerState(val gameElapsed: Duration, val turnElapsed: Duration,
    val paused: Boolean, val anchor: ClockAnchor?,
    val turnLineageId: Knowledge<EntityId> = Knowledge.Unknown("Legacy turn lineage not supplied", SnapshotList.empty()))
sealed interface GameAlertTarget {
    data class SeatClock(val seatId: EntityId) : GameAlertTarget
    data object SharedCountdown : GameAlertTarget
    data object TurnTimer : GameAlertTarget
    data object GameTimer : GameAlertTarget
}
data class GameAlert(val id: EntityId, val gameId: GameId, val target: GameAlertTarget,
    val firedAt: UtcTimestamp, val acknowledgedAt: UtcTimestamp?)
data class GameAlertPreferences(val visual: Boolean = true, val haptic: Boolean = false,
    val audio: Boolean = false)
data class ElapsedTimerSettings(val enabled: Boolean = true,
    val turnWarning: Duration? = null, val gameWarning: Duration? = null)
```

Append SeatClockState `shared: SharedCountdown? = null`; GameSnapshot `elapsed: ElapsedTimerState = ElapsedTimerState(ZERO, ZERO, true, null)` and `alerts: SnapshotList<GameAlert> = empty`; GameSettings `elapsedTimers: ElapsedTimerSettings = ElapsedTimerSettings()`, `alertPreferences: GameAlertPreferences = GameAlertPreferences()`. Existing ClockAlert remains source-compatible and maps to GameAlertTarget.SeatClock; SHARED alerts use a genuine game target, never a fabricated seat ID.

Settings.clockMode equals clock.mode. SHARED requires shared!=null, no per-seat banks or running clock seat; other modes require shared==null. SHARED charges its single bank once while running. NONE has no countdown bank. All durations/anchors are finite; initial/elapsed and warning durations nonnegative (warnings positive when set). Remaining permits negative overtime only under ALERT; PAUSE/ELIMINATE clamp and pause. A shared zero under ELIMINATE is a game-wide adjudication candidate, never automatic elimination of every seat. At zero it pauses the shared countdown and emits one durable game-wide confirmation candidate; acknowledgement/override never eliminates seats or finalizes a result.

GameSnapshot.activeSeatId remains **turn owner**. SeatClockState.activeSeat becomes **clock owner** and can differ after new `PassClock(seatId)`; turn count/turn elapsed/PER_TURN refill are unchanged by priority pass. SwitchTurn settles, changes turn owner, advances turnNumber and resets turnElapsed once; PER_TURN refills only on SwitchTurn. `SetElapsedPaused(paused)` controls elapsed timers independently of ClockPause. New games begin paused until explicit start/resume actions; no silently running setup clock.

Add actions `AcknowledgeGameAlert(alertId)` and `SetAlertPreferences(preferences)`. Acknowledgement is idempotent and operational: undoing a game action does not unacknowledge already delivered alerts. Fired IDs and acknowledgement survive undo; a genuine recrossing after redo gets a new occurrence ID. Turn warnings key occurrence by turn; game warning by explicitly rearmed timer occurrence. Foreground delivery obeys preferences; background OS delivery remains a separate acceptance gate.

Settle uses same-session monotonic deltas for both elapsed and countdown state. On a new process session, one transactional recovery pauses/reanchors running timing at durable balances; no wall-time gap is charged. Checkpointing bounds potential unpersisted time loss, but cadence must be measured and documented. Completed snapshots have all timers/countdowns paused. Keep current ClockUndoPolicy meaning for countdown banks. Elapsed game time is never refunded; undo turn assignment restores prior turn identity while retaining elapsed time charged to the undone turn in an audit record, rather than presenting refunded elapsed duration. Persisted timing checkpoints are not independently user-undoable.

## Random requests and recorded replay

```kotlin
sealed interface RandomRequest {
    data class Dice(val requestId: EntityId, val requested: Long, val sides: Int) : RandomRequest
    data class ContinueDice(val requestId: EntityId, val batchIndex: Long) : RandomRequest
    data class Coin(val requestId: EntityId) : RandomRequest
    data class SeatPick(val requestId: EntityId, val asker: EntityId?, val opponentsOnly: Boolean) : RandomRequest
    data class SeatOrder(val requestId: EntityId) : RandomRequest
}
sealed interface RecordedRandomValue {
    data class Dice(val sides: Int, val faces: SnapshotList<Int>, val sum: Long,
        val requested: Long, val batchIndex: Long, val remaining: Long) : RecordedRandomValue
    data class Coin(val heads: Boolean) : RecordedRandomValue
    data class SeatPick(val selected: EntityId?, val eligible: SnapshotList<EntityId>) : RecordedRandomValue
    data class SeatOrder(val seatIds: SnapshotList<EntityId>) : RecordedRandomValue
}
data class RecordedRandomResult(val requestId: EntityId, val at: UtcTimestamp,
    val value: RecordedRandomValue)
```

Add GameAction `RequestRandom(request)`; append GameEvent `randomResults: SnapshotList<RecordedRandomResult> = empty`. These are resolved by the service, not caller-provided results. Replay consumes recorded values only. One transaction draws/persists at most10000 dice, faces1..sides, checked sum, positive sides/requested, remaining=requested minus all committed batch faces. ContinueDice resolves the same request, next expected batch only; no omitted remainder or redrawn committed batch. Request/batch identity is unique per game. Seat selection null means no eligible seat, not failure or invented seat. Seat order must be an exact permutation of existing seat IDs and log the chosen order. Mixed random/hold groups are rejected until a reviewed atomicity rule exists; a random reorder event is itself undoable by immutable before/after state.

## Creation, profiles and queries

```kotlin
data class GameCreation(val operationId: EntityId, val settings: GameSettings,
    val seats: SnapshotList<Seat>, val clock: SeatClockState)
data class GameHistoryFilter(val playerName: String? = null, val deckId: DeckId? = null,
    val deckName: String? = null, val fromInclusive: UtcTimestamp? = null,
    val untilExclusive: UtcTimestamp? = null)
data class GameHistoryEntry(val gameId: GameId, val revision: Revision,
    val startedAt: UtcTimestamp, val endedAt: Knowledge<UtcTimestamp>,
    val seats: SnapshotList<Seat>, val result: Knowledge<GameResult>)
data class GameHistoryPage(val games: Page<GameHistoryEntry>, val totalMatches: Long)
data class PlayerProfileDraft(val name: String, val preferredLife: Int,
    val counterDefinitions: SnapshotList<CounterDefinition>, val appearance: SeatAppearance,
    val commanders: SnapshotList<CommanderSlot>, val adjustmentStep: Int = 1)
data class PlayerProfileSnapshot(val id: EntityId, val revision: Revision,
    val value: PlayerProfileDraft)
interface PlayerProfileRepository {
    suspend fun create(value: PlayerProfileDraft, cancel: CancelToken): Outcome<PlayerProfileSnapshot>
    suspend fun read(id: EntityId, cancel: CancelToken): Outcome<PlayerProfileSnapshot>
    suspend fun update(id: EntityId, value: PlayerProfileDraft, expected: Revision, cancel: CancelToken): Outcome<PlayerProfileSnapshot>
    suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<PlayerProfileSnapshot>>
    suspend fun delete(id: EntityId, expected: Revision, cancel: CancelToken): Outcome<Unit>
}
interface PersistedGameService : GameService {
    suspend fun create(input: GameCreation, cancel: CancelToken): Outcome<GameSnapshot>
    suspend fun read(id: GameId, cancel: CancelToken): Outcome<GameSnapshot>
    suspend fun undo(id: GameId, expected: Revision, cancel: CancelToken): Outcome<GameSnapshot>
    suspend fun events(id: GameId, page: PageRequest, cancel: CancelToken): Outcome<Page<GameEvent>>
    suspend fun history(filter: GameHistoryFilter, page: PageRequest, cancel: CancelToken): Outcome<GameHistoryPage>
    fun observe(id: GameId, cancel: CancelToken): Flow<Outcome<GameSnapshot>>
}
```

Creation operationId provides idempotence: same ID/same canonical input returns its committed game; different input is rejected. Service supplies GameId/event IDs/timestamps/revision, initial counters and empty designation/result state. Validate1..6 unique seat IDs, complete unique order0..n-1, globally unique commander-slot IDs, valid definitions/role bindings, matching paused initial clock banks, no pre-eliminated seats. Keep foreign deck/CardIdentity references and unresolved Knowledge values; creation needs no provider/art/network.

History filter fields are AND-combined; player/deck names use documented case-insensitive text matching; deck ID is exact stable identity. Deleted decks remain searchable by recorded deck name. Date selection includes both displayed days by converting their start/end in the selected display zone to [fromInclusive, untilExclusive); document that zone in UI. Reject inverted bounds. Page cursor binds canonical filter and deterministic startedAt/gameId ordering; totalMatches and page use one database read snapshot. No unbounded event/history list. Empty results are successful empty pages.

Profile deletion removes reusable setup only, never game history/art still referenced by games. ProfileExchange app RestorableSettings remain separate from player setup; append `lifeSetup: PlayerProfileDraft? = null` for explicit versioned exchange, and validate duplicated name/preferredLife/counter fields agree when present. Old exchanges remain legacy import previews, not complete live profiles.

Keep legacy undo signature for compatibility: implementation performs one serialized transaction against its authoritative loaded revision; new callers always use revision-checked overload. No unconditional stale overwrite. Legacy watch cannot carry typed outcomes: it must surface a terminal read exception rather than fake a snapshot; new UI uses observe. These compatibility methods have real implementation obligations, not default Unsupported/fake-success bodies.

## Persistent event envelope and transaction rules

```kotlin
data class GameTimeObservation(val monotonic: Duration, val wall: UtcTimestamp, val processSessionId: EntityId)
data class GameActionProvenance(val eventId: Long, val before: GameSnapshot,
    val afterAction: GameSnapshot, val reading: GameTimeObservation)
data class GameCheckpoint(val snapshot: GameSnapshot, val lastSettledEventId: Knowledge<Long>,
    val recoveredProcessSessionId: EntityId?)
data class GameEventArchive(val version: Int, val event: GameEvent,
    val provenance: GameActionProvenance)
```

These are storage/exchange envelopes, not extra fields duplicating live GameSnapshot at every UI observation. Append GameExchange `actionArchives: SnapshotList<GameEventArchive> = empty`; archive export is bounded by existing transfer streaming limits. Provenance uses snapshots without nested archives. For new format every undoable event has exactly one matching archive, before.revision+1==after.revision==event.revision, same game/event identity, and immutable original action-time clock reading. The service verifies actual event/undo lineage; structural clock similarity is not proof. It passes the accepted four-argument overload when necessary.

Dispatch transaction checks expected revision, settles timing, reduces validated action/group, appends event plus immutable provenance/random data, updates snapshot, alert occurrences and result, increments revision once, then publishes after commit. A flat hold/damage-life group is one event/revision/undo unit; nested groups rejected. Cancellation never rolls back a committed transaction. Undo appends an explicit undo event with undoOfEventId, restores relevant reversible state and reconciles timing/result/acknowledgement under these policies. Repeated undo selection cannot undo the same event twice. Recovery/checkpoints are persisted system records with their own lineage; they cannot masquerade as another user action.

## Minimal contract patch versus following work

**First bounded root unit:** state/presentation declarations, compatible defaults and local invariants in GameContracts/LifeStateContracts, with focused core tests. Separate following model units add ports/profile/query/provenance DTOs, exchange extensions and actions with their necessary exhaustive-consumer changes. Retain existing slot/hiding/group semantics and private reducers. Do not register a service, add platform classes or claim SHARED works merely because its DTO exists. Required architecture review covers the root-selected choices below before implementation.

**Following schema packet:** additive5→6 GameTables (schema5 reserved for durable origin cooldown) plus registry/migration/actual KSP6 export; game checkpoint, ordered events/archives, seat search projections, unique per-game result, game+occurrence alerts/acknowledgement, profiles. Root also registers Migration5To6 in StorageDatabase. Prior catalog/rules/acquisition tables unchanged. Typed versioned payload codec and indexed projections must agree; malformed/version-newer data fails visibly, not destructive reset. Real SQLite migration/reopen/rollback/crash evidence precedes binding.

**Following service/domain packet:** extend reducers for new actions/SHARED/elapsed/priority/designations/participation; implement real transactions, random request resolution, profile/history reads, checkpoint/recovery and alert delivery idempotence. Storage failures do not advance observable state. Persisted service review uses actual SQLite CAS/group/finish/reopen/undo and process-session tests. Only then bind production UI, setup/history/profile/custom/art/variant controls. Named variant rules/cards remain authentic versioned corpus obligations; generic variant DTOs are not operational completeness.

## Root-selected engineering decisions for architecture review

1. Elapsed undo: settle at undo time; gameElapsed stays the current settled value. Undoing SwitchTurn restores the immutable pre-action turn lineage/owner and its settled turnElapsed at that action; time accrued in the undone turn stays in its event audit, rather than being reassigned to another player. Undo of a non-turn action preserves the currently settled turnElapsed. Creation and each SwitchTurn persist a fresh turnLineageId; immutable provenance preserves the restored ID. Subsequent time accrues to the restored lineage from the new anchor; no wall-time gap or game elapsed is refunded. Group validation permits at most one SwitchTurn, with an explicit flat atomic action lineage.
2. SHARED zero ELIMINATE pauses that countdown and produces one game-wide adjudication candidate/alert occurrence, not automatic defeat or result. Candidate resolution is manual: acknowledge/override or explicitly finish with selected result; counters/seat participation remain untouched without confirmed actions. A new positive-to-zero crossing needs a fresh occurrence, so retained acknowledgement is never erased by undo.
3. Legacy exchange: preview/repair requires explicit role selection and missing-provenance warning; do not infer LIFE from display labels. New profile duplicate fields must agree exactly. Existing old fixtures stay source-compatible with absent/default metadata, but are not silently certified as complete resumable imported games. Runtime/store has no legacy persisted game tables to destructively reinterpret.
4. First contract patch does not introduce a codec or truncate history. Following codec/service packet uses explicit engineering limits (initial policy: snapshot4MiB, individual event/archive16MiB, string64KiB, definitions128, flat group1024, query page1–100) with validation before commit and a visible failure, never silent truncation. These are application bounds, not measured capacity guarantees. Store retains all history/provenance until explicit user deletion; bounded event/archive pages and streaming export avoid whole-history loading. A 1second foreground timing checkpoint target is an engineering cadence; actual force-stop loss and device cost must be measured before timing acceptance. No model declaration claims that the cadence or full exchange implementation exists.

No additional product decision is inferred from the reference screenshots. These are concrete engineering review choices; none authorizes silently dropping a retained requirement.

Current implementation pointer: `coordination/tasks/cx-m2-life-state-contracts-01.md`. Independent Astra approved the first state-only model design after the four root technical choices. Exact-source review is separate; no service, codec, schema6, exchange or UI acceptance follows from declarations. Later packets retain all obligations above.

Numeric refinement2026-10-13: random continuation/record batch indices use Long, matching the accepted Long request count. An Int index would silently prevent sufficiently large requests from completing. Independent Astra approved overflow-safe local batch plausibility validation; actual next-index CAS/draw replay and practical runtime capacity remain service gates.
