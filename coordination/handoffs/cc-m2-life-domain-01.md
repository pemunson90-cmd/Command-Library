# Handoff: cc-m2-life-domain-01 (Claude, life domain)

Worker handoff for `coordination/tasks/cc-m2-life-domain-01.md`. Branch `agent/cc-m2-life-domain-01`, base `9ce1a48` plus Codex merge `fef937a` (already contains `origin/agent/cx-claude-life-handoff-01` at `d5c5f31`; the requested merge was a verified no-op). Codex holds the orchestrator lease; this worker writes only its three locked paths.

## Plan and section status

| # | Section | Status |
|---|---|---|
| 0 | Design, plan, baseline check | done (this commit) |
| 1 | Counter reductions + tests | done: `LifeDomainSupport.kt`, `CounterReducer.kt`, `CounterReducerTest.kt` (20 tests) |
| 2 | Seat clock calculations + tests | done: `SeatClockReducer.kt`, `SeatClockReducerTest.kt` (22 tests) |
| 3 | Random tools + tests | done: `RandomTools.kt`, `RandomToolsTest.kt` (12 tests) |
| 4a | Independent adversarial review of 1-3 | done: 8-agent review (3 readers, 1 mutation tester, 4 probe-test verifiers); 36 findings, 28 confirmed, 8 refuted |
| 4b | Fix confirmed counter and random findings + tests | pending |
| 4c | Fix confirmed clock findings + tests | pending |
| 4d | Correct handoff statements, final checks | pending |

Each section is committed and pushed with this file updated before the next starts.

## Design (binding for sections 1-3)

All domain declarations are `internal` to `:feature:life`. A future `GameService` implementation in the same module consumes them; no new public API surface is added outside core contracts. Inputs are immutable core snapshots; every operation returns `Outcome`. A rejected operation returns `Outcome.Failure` and leaves the input untouched (immutability, no partial result). `INVALID_INPUT` marks a bad request; `CORRUPT_DATA` marks an inconsistent snapshot. Revision, event ID and persistence are not advanced here; the committing transaction owns them.

Counters: definitions are identified by explicit role bindings (`life`, optional `commanderDamage`, optional `commanderTax` definition IDs), never by label. Scope: life and other counters are seat-scoped (no slot); commander damage requires an opposing seat's `CommanderSlot` ID (victim is `seatId`); commander tax requires the seat's own slot. Partners are distinct slots, so subtotals never merge. An absent `CounterState` means `definition.initial`. Adjustments compute in `Long`, reject zero delta, Int overflow and min/max violations (no clamping). Hide/show touches every state of that seat and definition, never values; no-op toggles are rejected. Commander damage with optional life deduction is one `GameAction.Group`, validated all-or-nothing. Loss candidates come from each state's `CounterLossPolicy`, per commander slot, with a `newlyReached` flag; nothing is marked eliminated.

Clocks: time enters only as an explicit reading (monotonic `Duration`, wall `UtcTimestamp`, process session `EntityId`). Only the active seat is charged, using monotonic deltas within one session; wall time is recorded, never used to compute elapsed. A running anchor from another session is rejected until an explicit restart recovery pauses and re-anchors with durable balances unchanged. Zero transition fires one `ClockAlert` per transition with a deterministic ID; `firedAlertIds` blocks refiring. Undo takes the pre-action clock state: `RESTORE_TIME` restores those balances, `KEEP_ELAPSED` keeps settled balances and reverts only active seat, pause flag and turn.

Random: `RandomSource` is injected; results outside `0 until bound` are a typed failure. Dice count `1..10000`, sides `>= 1`, `Long` sum; larger requests are split into batches without dropping the remainder. Coin, Fisher-Yates shuffle, seat randomizer producing `ReorderSeats` with seat data attached, and seat selection from explicitly supplied eligible IDs (none eligible is a typed result). No fixed production RNG.

## Section 4 review: confirmed findings and planned fixes

Verifiers confirmed each item with a probe test on `9ffc840` unless noted. IDs are the review's own.

Counters (4b): C1 a deeply nested `Group` recurses until `StackOverflowError`; reject nested groups. C3 loss assessment ignores absent states whose `definition.initial` already meets the loss policy, so hiding changes the assessment; evaluate implicit states too. T1/M23 no test for a fresh (absent) counter becoming newly lethal. T2/M24 no test hiding commander damage that already has an own-slot state. T3 no test for partner-slot tax hiding, slot-scoped no-op hide, or mixed-sibling inheritance.

Random (4b): R1 `randomizeSeats` consumes draws before rejecting a completed game; check status first.

Clocks (4c): C1 under PAUSE/ELIMINATE a seat already at zero is charged into negative time after resume or switch-back; clamp whenever the policy clamps, not only on the crossing. C2 (also compliance C1) RESTORE_TIME undo fails after a restart because `before` carries the old session anchor; reconstruct from durable balances, never refunding charged time, and come back paused. C3 alert ID `(seat, turn)` is reused by an undone-then-redone turn, so a real new timeout is silenced and ELIMINATE reports nothing; give each firing a unique occurrence ID and report timeouts on every real crossing, keeping undo-replay dedupe. C4 infinite monotonic readings are stored by resume/undo/switch, wedging the clock; validate the reading at every entry point. C5 KEEP_ELAPSED undo after a restart restarts a clock recovery paused; keep a pause made in another session. C6 PER_TURN KEEP_ELAPSED undo keeps the turn refill; revert it. C7 a completed game with a running clock keeps charging; report it as corrupt. C9 non-finite anchor is reported as bad input; report it as corrupt. C10 `observe` returns `-Infinity` where `settle` rejects; reject in both. C12/M10/M25/M26/M27/M28 untested: re-crossing from exactly zero, undo replay alert and timeout dedupe, `zeroPending` after settled overtime, undo across a seat change, re-anchor after KEEP_ELAPSED undo, zero crossing inside `switchTurn`.

Handoff (4d): commander damage text must say any slot in the game (code and CR 903.10a), RESTORE_TIME text must say it continues the pre-action timeline, provenance hash for the assignment branch is `354c1a0`, `LifeFixtures.kt` belongs in the section 1 file list, deltas must name commits.

Refuted (no change): role-binding error codes, failure precedence on doubly bad inputs, groups with hide steps, output ordering, active-seat field cross-check, SHARED blocking turn counting, opponent duplicate handling, batch total overflow.

## Verification

All runs are local JVM runs in this Claude Code cloud container (OpenJDK 17 toolchain, cached Gradle 9.6.0). No hosted CI run has been requested or claimed.

- Baseline before code: `cd android-app && bash gradlew --no-daemon --console=plain :core:model:test :feature:life:test` BUILD SUCCESSFUL, `:core:model` 16 tests, 0 failures/errors/skips; `:feature:life:test` NO-SOURCE.
- Section 1: same command BUILD SUCCESSFUL, 0 compiler warnings; `:core:model` 16/0/0/0; `:feature:life` `CounterReducerTest` 20 tests, 0 failures/errors/skips.
- Section 2: same command BUILD SUCCESSFUL, 0 compiler warnings; `:core:model` 16/0/0/0; `:feature:life` 42 tests (`CounterReducerTest` 20, `SeatClockReducerTest` 22), 0 failures/errors/skips.
- Section 3: same command BUILD SUCCESSFUL, 0 compiler warnings; `:core:model` 16/0/0/0; `:feature:life` 54 tests (`CounterReducerTest` 20, `SeatClockReducerTest` 22, `RandomToolsTest` 12), 0 failures/errors/skips.

## Contract requests for Codex

1. Counter roles are not persisted. `GameSettings` and `CounterDefinition` carry no role, so the domain takes an internal `CounterRoles(life, commanderDamage?, commanderTax?)` from its caller. Proposed core addition: `public enum class CounterRole { LIFE, COMMANDER_DAMAGE, COMMANDER_TAX, OTHER }` and `CounterDefinition.role: CounterRole` (or `GameSettings.counterRoles` holding the three IDs). Without it a resumed game cannot recover which definition is life or commander damage.
2. Starting life has two sources: `GameSettings.initialLife` and the life `CounterDefinition.initial`. The domain rejects disagreement as `CORRUPT_DATA`. Proposed: document that the life definition's `initial` must equal `initialLife`, or drop one field.
3. Elimination has no contract surface. SPEC G01/G09 require logged, undoable eliminate/restore, but `Seat`, `GameSnapshot` and `GameAction` have no eliminated state or action. The domain reports `LossCandidate`s only. Proposed: `GameAction.SetSeatEliminated(seatId: EntityId, eliminated: Boolean, reason: String)` plus `GameSnapshot.eliminatedSeatIds: SnapshotList<EntityId>` (or `Seat.eliminated`), so eligible-seat selection can be derived instead of caller-supplied.
4. `GameAction.SetCounterHidden` has no commander slot, so hiding applies to every slot of that seat's counter (commander damage covers all opposing slots plus any recorded slot). Per-slot hiding would need `commanderSlotId: EntityId?` on the action. No change requested unless per-slot hiding is wanted.
5. `SeatClockMode.SHARED` has no defined semantics in the contract, SPEC or DATA_MODEL. The domain returns `Outcome.Unavailable(UNSUPPORTED)` instead of guessing. Proposed: define SHARED (for example, one game countdown charged whenever the clock runs, stored as a single pool rather than per seat) or remove it.
6. Clock mode has two sources: `GameSettings.clockMode` and `SeatClockState.mode`. Disagreement is rejected as `CORRUPT_DATA`. Proposed: document that they must match, or drop one.
7. G07/PX07-031 persistent game timer has no field. `SeatClockState` stores only per-seat countdown balances. Proposed: `SeatClockState.gameElapsed: Duration` (settled at each anchor, charged whenever the clock runs).
8. `GameAction.SwitchTurn` always advances `turnNumber`. A chess clock pass on priority without a new turn (PX07-047 "turn and priority advances") needs a separate action, for example `GameAction.PassClock(seatId: EntityId)`.
9. `ClockAlert.acknowledgedAt` has no action that sets it. Proposed: `GameAction.AcknowledgeClockAlert(alertId: EntityId)` if acknowledgement must be durable.
10. Integration note, no contract change: running time after the last persisted anchor is not charged after a process death, because wall time is never trusted. The future service should persist `SeatClockReducer.settle` checkpoints periodically while a clock runs, and must call `recoverAfterRestart` once per new process session before any other clock calculation.
11. Random results cannot be logged. SPEC G06 requires result history and G01 requires the random pick to log seat ID, eligible set and time, but `GameAction` has no random-result variant (seat shuffles can use the existing `ReorderSeats`). Proposed: `GameAction.RecordRandom(result: RandomResult)` with `sealed interface RandomResult { Dice(sides: Int, values: SnapshotList<Int>, sum: Long); Coin(heads: Boolean); SeatPick(seatId: EntityId?, eligible: SnapshotList<EntityId>) }`. The domain already returns these values (`DiceRoll`, `CoinFace`, `SeatSelection` with eligible set and time).

## Latest continuation delta

Section 4a complete on `origin/agent/cc-m2-life-domain-01` (parent of this commit: `9ffc840`): review results recorded above, no code changed. Next action: 4b, fix counter C1/C3 and random R1 with the listed tests, run the task's Gradle command, commit, push, update this file; then 4c clock fixes; then 4d handoff corrections.
