# Handoff: cc-m2-life-domain-01 (Claude, life domain)

Worker handoff for `coordination/tasks/cc-m2-life-domain-01.md`. Branch `agent/cc-m2-life-domain-01`, base `9ce1a48` plus Codex merge `fef937a` (already contains `origin/agent/cx-claude-life-handoff-01` at `d5c5f31`; the requested merge was a verified no-op). Codex holds the orchestrator lease; this worker writes only its three locked paths.

## Plan and section status

| # | Section | Status |
|---|---|---|
| 0 | Design, plan, baseline check | done (this commit) |
| 1 | Counter reductions + tests | done: `LifeDomainSupport.kt`, `CounterReducer.kt`, `CounterReducerTest.kt` (20 tests) |
| 2 | Seat clock calculations + tests | pending |
| 3 | Random tools + tests | pending |
| 4 | Independent adversarial review of 1-3, fixes, final checks | pending |

Each section is committed and pushed with this file updated before the next starts.

## Design (binding for sections 1-3)

All domain declarations are `internal` to `:feature:life`. A future `GameService` implementation in the same module consumes them; no new public API surface is added outside core contracts. Inputs are immutable core snapshots; every operation returns `Outcome`. A rejected operation returns `Outcome.Failure` and leaves the input untouched (immutability, no partial result). `INVALID_INPUT` marks a bad request; `CORRUPT_DATA` marks an inconsistent snapshot. Revision, event ID and persistence are not advanced here; the committing transaction owns them.

Counters: definitions are identified by explicit role bindings (`life`, optional `commanderDamage`, optional `commanderTax` definition IDs), never by label. Scope: life and other counters are seat-scoped (no slot); commander damage requires an opposing seat's `CommanderSlot` ID (victim is `seatId`); commander tax requires the seat's own slot. Partners are distinct slots, so subtotals never merge. An absent `CounterState` means `definition.initial`. Adjustments compute in `Long`, reject zero delta, Int overflow and min/max violations (no clamping). Hide/show touches every state of that seat and definition, never values; no-op toggles are rejected. Commander damage with optional life deduction is one `GameAction.Group`, validated all-or-nothing. Loss candidates come from each state's `CounterLossPolicy`, per commander slot, with a `newlyReached` flag; nothing is marked eliminated.

Clocks: time enters only as an explicit reading (monotonic `Duration`, wall `UtcTimestamp`, process session `EntityId`). Only the active seat is charged, using monotonic deltas within one session; wall time is recorded, never used to compute elapsed. A running anchor from another session is rejected until an explicit restart recovery pauses and re-anchors with durable balances unchanged. Zero transition fires one `ClockAlert` per transition with a deterministic ID; `firedAlertIds` blocks refiring. Undo takes the pre-action clock state: `RESTORE_TIME` restores those balances, `KEEP_ELAPSED` keeps settled balances and reverts only active seat, pause flag and turn.

Random: `RandomSource` is injected; results outside `0 until bound` are a typed failure. Dice count `1..10000`, sides `>= 1`, `Long` sum; larger requests are split into batches without dropping the remainder. Coin, Fisher-Yates shuffle, seat randomizer producing `ReorderSeats` with seat data attached, and seat selection from explicitly supplied eligible IDs (none eligible is a typed result). No fixed production RNG.

## Verification

All runs are local JVM runs in this Claude Code cloud container (OpenJDK 17 toolchain, cached Gradle 9.6.0). No hosted CI run has been requested or claimed.

- Baseline before code: `cd android-app && bash gradlew --no-daemon --console=plain :core:model:test :feature:life:test` BUILD SUCCESSFUL, `:core:model` 16 tests, 0 failures/errors/skips; `:feature:life:test` NO-SOURCE.
- Section 1: same command BUILD SUCCESSFUL, 0 compiler warnings; `:core:model` 16/0/0/0; `:feature:life` `CounterReducerTest` 20 tests, 0 failures/errors/skips.

## Contract requests for Codex

1. Counter roles are not persisted. `GameSettings` and `CounterDefinition` carry no role, so the domain takes an internal `CounterRoles(life, commanderDamage?, commanderTax?)` from its caller. Proposed core addition: `public enum class CounterRole { LIFE, COMMANDER_DAMAGE, COMMANDER_TAX, OTHER }` and `CounterDefinition.role: CounterRole` (or `GameSettings.counterRoles` holding the three IDs). Without it a resumed game cannot recover which definition is life or commander damage.
2. Starting life has two sources: `GameSettings.initialLife` and the life `CounterDefinition.initial`. The domain rejects disagreement as `CORRUPT_DATA`. Proposed: document that the life definition's `initial` must equal `initialLife`, or drop one field.
3. Elimination has no contract surface. SPEC G01/G09 require logged, undoable eliminate/restore, but `Seat`, `GameSnapshot` and `GameAction` have no eliminated state or action. The domain reports `LossCandidate`s only. Proposed: `GameAction.SetSeatEliminated(seatId: EntityId, eliminated: Boolean, reason: String)` plus `GameSnapshot.eliminatedSeatIds: SnapshotList<EntityId>` (or `Seat.eliminated`), so eligible-seat selection can be derived instead of caller-supplied.
4. `GameAction.SetCounterHidden` has no commander slot, so hiding applies to every slot of that seat's counter (commander damage covers all opposing slots plus any recorded slot). Per-slot hiding would need `commanderSlotId: EntityId?` on the action. No change requested unless per-slot hiding is wanted.

## Latest continuation delta

Section 1 complete at the commit that carries this text: counter reductions with tests, local check green as recorded above. Next action: implement section 2 (seat clock calculations) in `feature/life/.../domain/` with tests, run the task's Gradle command, commit, push, update this file.
