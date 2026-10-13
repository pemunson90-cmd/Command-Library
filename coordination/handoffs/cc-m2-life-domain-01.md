# Handoff: cc-m2-life-domain-01 (Claude, life domain)

Worker handoff for `coordination/tasks/cc-m2-life-domain-01.md`. Branch `agent/cc-m2-life-domain-01` on base `9ce1a48` plus Codex merge `fef937a` of `origin/main` `d5c5f31`, which already contains `origin/agent/cx-claude-life-handoff-01` at `354c1a0`. The requested merge of that branch was a verified no-op. Codex holds the orchestrator lease; this worker wrote only its three locked paths and did not take over orchestration. This is an internal M2 component, not a claim that M2, K3 or the app is complete.

## Status

Complete and ready for Codex review. Code and tests are final at `08d5de0`; this handoff commit follows it.

| # | Section | Commit |
|---|---|---|
| 0 | Design, plan, baseline | `4d65b2c` |
| 1 | Counter reductions + tests | `09a098d` |
| 2 | Seat clock calculations + tests | `d5b7b7a` |
| 3 | Random tools + tests | `9ffc840` |
| 4a | Independent adversarial review recorded (36 findings, 28 confirmed, 8 refuted) | `7a37750` |
| 4b | Counter and random review fixes + tests | `f16e742` |
| 4c | Clock review fixes + tests | `08d5de0` |
| 4d | Handoff corrected to match the code, final evidence | this commit |

Files, all under the owned paths `android-app/feature/life/src/{main,test}/kotlin/com/architectpsg/commandlibrary/feature/life/domain/`:

- main: `LifeDomainSupport.kt` (typed failures, `valueOr`, seat/slot index, 1..6 seats), `CounterReducer.kt`, `SeatClockReducer.kt`, `RandomTools.kt`
- test: `LifeFixtures.kt` (synthetic fixtures, `success()`/`failure()` helpers), `CounterReducerTest.kt` (25), `SeatClockReducerTest.kt` (32), `RandomToolsTest.kt` (12)

## Design as implemented

General: every declaration is `internal` (or file-private) to `:feature:life`; no public API is added outside core contracts. Only `:core:model` types are used. Every operation takes immutable snapshots and returns `Outcome`; it never throws for bad input or a bad snapshot. A rejected operation returns `Outcome.Failure` and the caller's snapshot is untouched. `INVALID_INPUT` is a bad request, `CORRUPT_DATA` an inconsistent snapshot, `INTERNAL` a `RandomSource` breaking its contract, `Unavailable(UNSUPPORTED)` the undefined SHARED clock mode. Results keep the input revision and event pointers; the committing transaction advances them. No `GameService`, persistence, UI, acquisition or placeholder adapter exists.

Counters (`CounterReducer`): behavior is selected by explicit role bindings `CounterRoles(life, commanderDamage?, commanderTax?)` by definition ID, never by label. Seat counters take no slot. Commander damage takes any commander slot in the game, keyed (victim seat, source slot), so partners never share a subtotal; the victim's own slot is accepted because damage belongs to the commander, not its controller (CR 903.10a, stolen commander). Commander tax takes the seat's own slot. An absent state means `definition.initial`. Adjustments compute in `Long` and reject zero delta, Int overflow and min/max violations without clamping. A flat `GameAction.Group` applies in order, all or nothing; a group nested in a group is rejected (one event carries one group ID). Hide/show changes flags only: a seat counter, every own slot for tax, every opposing slot plus any recorded slot for commander damage; later-created slot states inherit hidden only when every sibling is hidden; no-op toggles are rejected. `commanderDamage(..., deductLife, groupId)` returns one validated group of damage then life. Loss candidates use each definition's `CounterLossPolicy`, per slot (21 from one commander, not from partners combined), including implicit states at their initial value, with `newlyReached` for crossings caused by the action. Nothing is marked eliminated. `GameStatus.COMPLETED` rejects changes.

Clocks (`SeatClockReducer`): time enters only as an explicit `ClockReading(monotonic, wall, processSessionId)`, built from the injected core `Clock` port; non-finite readings are rejected at every entry point. CHESS keeps per-seat banks; PER_TURN refills the incoming seat on every `SwitchTurn`; NONE tracks turns only; SHARED is unsupported. Only the active seat is charged, by monotonic delta within one process session; wall time is recorded but never measures elapsed time. A running anchor from another session is rejected until `recoverAfterRestart`, which pauses and re-anchors with durable balances unchanged. `SwitchTurn` always advances `turnNumber` and keeps a paused clock paused. A zero transition (balance above zero to at or below zero) fires one `ClockAlert` with ID `clock-zero/<seat>/turn-<n>/<occurrence>`, the first occurrence not yet in `firedAlertIds`, so every real firing is unique and a redone turn alerts again. Under PAUSE and ELIMINATE the seat is held at exactly zero and the clock pauses whenever it would run below zero, including a seat resumed at zero; ELIMINATE reports the seat in `timedOutSeats` on every real crossing, without eliminating it. Undo takes the snapshot before the undone action: turn assignment, including a PER_TURN refill, returns to it. `RESTORE_TIME` continues that pre-action timeline to the undo reading as if the action never happened (its active seat is charged from its anchor, so pre-action balances come back exactly only when that clock was paused); across a restart it keeps the lower of the two durable balances for that seat and comes back paused. `KEEP_ELAPSED` keeps time charged where the clock charged it, reverts active seat and pause flag, and keeps a pause the current snapshot received in another session. A zero transition already fired in the current snapshot is not fired or reported again by an undo replay. A completed game with a running clock and a non-finite anchor are `CORRUPT_DATA`. `observe` is read-only and agrees with `settle`.

Random (`RandomTools`): `RandomSource` is always injected; there is no default or seeded production source. A value outside `0 until bound` is an `INTERNAL` failure with no partial result. Dice: count 1..10000, sides at least 1, faces 1..sides, `Long` sum; `rollBatch` rolls at most 10000 of a larger request and returns the remaining count, never dropping rolls. Coin: two outcomes. Shuffle: Fisher-Yates, input untouched. `randomizeSeats` checks status before drawing, permutes the table once and returns `ReorderSeats`; seat data stays attached and counters stay keyed by seat ID. `reorderSeats` requires a complete permutation. `selectSeat` draws only from caller-supplied eligible IDs, canonicalized to table order, and returns the eligible set and caller time; none eligible is a typed `NoEligibleSeat`; `selectOpponent` removes the asking seat.

## Independent review (section 4)

An 8-agent review ran on `9ffc840`: three read-only reviewers (counters, clocks, random plus compliance), one mutation tester in an isolated worktree (28 mutants, 7 survived), and one verifier per reviewer that confirmed or refuted each finding with probe tests in its own worktree. Result: 36 findings, 28 confirmed, 8 refuted, 0 uncertain. All confirmed findings are fixed in `f16e742` and `08d5de0`, each with a regression test. The main defects were: under PAUSE/ELIMINATE a seat already at zero could be charged below zero; RESTORE_TIME undo failed after a restart and KEEP_ELAPSED undo resumed a clock that recovery had paused; reused alert IDs silenced a real timeout after undo and redo; an infinite reading could wedge the clock; deeply nested groups overflowed the stack; loss assessment ignored absent states; `randomizeSeats` consumed draws before rejecting; the PER_TURN refill survived KEEP_ELAPSED undo. Three handoff statements were also wrong and are corrected here. Refuted, no change: role-binding error codes, failure precedence on doubly bad inputs, groups with hide steps, output ordering, active-seat field cross-check, SHARED blocking turn counting, opponent duplicate handling, batch total overflow. The review's scratch worktrees and branches were removed afterwards; nothing from them was committed.

## Verification

Local JVM runs in this Claude Code cloud container (OpenJDK 17 toolchain, cached Gradle 9.6.0). No hosted CI run was requested or is claimed; Codex should obtain hosted evidence during integration.

Command each time: `cd android-app && bash gradlew --no-daemon --console=plain :core:model:test :feature:life:test`.

- Baseline before code: BUILD SUCCESSFUL; `:core:model` 16 tests 0 failures/errors/skips; `:feature:life:test` NO-SOURCE.
- Sections 1, 2, 3: BUILD SUCCESSFUL, 0 compiler warnings; `:feature:life` 20, 42, 54 tests respectively, 0 failures/errors/skips.
- Section 4b: 59 tests, 0 failures. Reverting each 4b fix one at a time (nested-group rejection, implicit loss states, randomize status first, hide of a recorded own slot) fails the suite every time; sources restored from git after each.
- Section 4c: 69 tests, 0 failures. Reverting each of 14 clock fixes or guards one at a time (clamp at zero, observe clamp, restart-safe RESTORE_TIME, alert occurrence, reading check, recovery pause, refill revert, completed-running, anchor finiteness, observe finiteness, replay alert filter, replay timeout filter, seat-change guard, exact-zero re-crossing) fails the suite every time; sources restored from git after each.
- Final, on `08d5de0` with a clean tree and `--rerun-tasks`: BUILD SUCCESSFUL in 37 s, 0 compiler warnings; `:core:model` 16 tests (LocalRulesContractsTest 3, PrintingFaceAssetTest 2, ValuesTest 11) and `:feature:life` 69 tests (CounterReducerTest 25, SeatClockReducerTest 32, RandomToolsTest 12), 0 failures, 0 errors, 0 skipped.
- Ownership check: `git diff --stat fef937a..HEAD` touches only the two owned domain directories and this file.

## Contract requests and integration notes for Codex

1. Counter roles are not persisted. `GameSettings` and `CounterDefinition` carry no role, so the domain takes `CounterRoles` from its caller. Proposed: `public enum class CounterRole { LIFE, COMMANDER_DAMAGE, COMMANDER_TAX, OTHER }` and `CounterDefinition.role: CounterRole` (or `GameSettings.counterRoles` with the three IDs). Without it a resumed game cannot recover which definition is life or commander damage.
2. Starting life has two sources: `GameSettings.initialLife` and the life definition's `initial`. Disagreement is `CORRUPT_DATA`. Proposed: document that they must match, or drop one.
3. Elimination has no contract surface. SPEC G01/G09 require logged, undoable eliminate/restore, but `Seat`, `GameSnapshot` and `GameAction` have no eliminated state or action. Proposed: `GameAction.SetSeatEliminated(seatId: EntityId, eliminated: Boolean, reason: String)` plus `GameSnapshot.eliminatedSeatIds: SnapshotList<EntityId>` (or `Seat.eliminated`), so eligible seats can be derived instead of caller-supplied.
4. `GameAction.SetCounterHidden` has no slot, so hiding covers every slot of that seat's counter. Per-slot hiding would need `commanderSlotId: EntityId?`. No change requested unless per-slot hiding is wanted.
5. `SeatClockMode.SHARED` has no defined semantics in the contract, SPEC or DATA_MODEL; the domain returns `Unavailable(UNSUPPORTED)`. Proposed: define it (for example one game countdown charged whenever the clock runs, stored as a single pool) or remove it.
6. Clock mode has two sources: `GameSettings.clockMode` and `SeatClockState.mode`. Disagreement is `CORRUPT_DATA`. Proposed: document that they must match, or drop one.
7. The G07/PX07-031 persistent game timer has no field. Proposed: `SeatClockState.gameElapsed: Duration`, settled at each anchor.
8. `SwitchTurn` always advances `turnNumber`. A chess-clock pass on priority without a new turn (PX07-047) needs a separate action, for example `GameAction.PassClock(seatId: EntityId)`.
9. `ClockAlert.acknowledgedAt` has no action that sets it. Proposed: `GameAction.AcknowledgeClockAlert(alertId: EntityId)` if acknowledgement must be durable.
10. Integration note: running time after the last persisted anchor is not charged after a process death, because wall time is never trusted. The service should persist `SeatClockReducer.settle` checkpoints periodically while a clock runs and call `recoverAfterRestart` once per new process session on the live snapshot before any other clock calculation. `undo` accepts a historical `before` snapshot anchored by an earlier process and handles it as described above.
11. Random results cannot be logged. SPEC G06 requires result history and G01 requires the random pick to log seat ID, eligible set and time, but `GameAction` has no random-result variant (seat shuffles use `ReorderSeats`). Proposed: `GameAction.RecordRandom(result: RandomResult)` with `sealed interface RandomResult { Dice(sides: Int, values: SnapshotList<Int>, sum: Long); Coin(heads: Boolean); SeatPick(seatId: EntityId?, eligible: SnapshotList<EntityId>) }`. The domain already returns these values.
12. Commander damage scope is wider than the SPEC G03 wording "per opposing commander": the domain also accepts the victim's own commander slot, because CR 903.10a counts damage by commander regardless of controller (a stolen commander can damage its owner). Please confirm, or rule that only opposing slots are valid and the domain will be narrowed.
13. Integration note: `Finish` must settle and pause the clock in the same transaction; a completed game with a running clock is rejected as `CORRUPT_DATA` by every clock calculation.
14. Integration note: alert IDs are `clock-zero/<seat>/turn-<n>/<occurrence>` and stay unique within `firedAlertIds`; a persisted alert table can key on them.

## Latest continuation delta

Task complete. Code and tests final at `08d5de0` on `origin/agent/cc-m2-life-domain-01`; this handoff commit (parent `08d5de0`) records the final evidence. Local check green as recorded above; hosted CI not run. Next action, owned by Codex: independent review of `fef937a..HEAD`, a hosted JVM run of the task command, decisions on contract requests 1-14, then integration of the domain into a real `GameService`. This worker holds no further work and does not hold the orchestrator lease.
