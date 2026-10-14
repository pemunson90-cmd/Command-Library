# Life history, undo, result, alert and clock-recovery audit

Task: `coordination/tasks/cx-m2-life-history-audit-01.md`. Branch `component/life-history-audit-20261014`, base `e489af7` of `agent/cx-thread-recovery-20261013`. Documentation only. This audit reads source and test text; it ran no build, test or emulator. It does not approve the eventual service.

## Evidence levels

| Code | Meaning |
|---|---|
| C | Public contract declaration (`core/model`) |
| S | Schema 6 / codec structure |
| D | Pure JVM domain or reducer test (`feature/life/src/test`), no database |
| L | Local host SQLite test (`data/storage/src/test/.../life`, `HostLifeDatabase`). Not Room on Android |
| O | Operational: real `GameService` binding, DI, process lifecycle, UI |
| P | Physical device or emulator acceptance |

No row below reaches O or P. `PersistedGameService` is declared (`core/model/.../LifePersistenceContracts.kt:63`) with no implementation or DI binding: nothing under `app/` references `GameService`, `PersistedGameService` or `SqlLifeGameSessions`, and the only non-main use of `SqlLifeGameSessions` is `SqlLifeGameSessionsTest`.

## Sources

- Retained requirements: `docs/gauntlet/SPEC.md` G07 (line 57), G09 (59), G10 (60), G11 (61), G13 (95). `docs/gauntlet/PARITY_ACCEPTANCE.md` PX07-035 to PX07-038 (lines 174 to 177) and PX07-067 (206). PX07 has no history-filter row; G11 alone carries it.
- Accepted decisions: `docs/verification/m2-life/CONTRACT-DISPOSITIONS.md` requests 9, 10, 13, 14 (D-25 in `docs/gauntlet/DECISIONS.md`).
- Already covered, not repeated: `HISTORY-QUERY-LOCAL-20261014.md`, `SCHEMA6-LOCAL-20261013.md`, `docs/superpowers/specs/2026-10-14-life-history-query-design.md`.

Abbreviations: `LS` = `android-app/feature/life/src/main/kotlin/com/architectpsg/commandlibrary/feature/life`, `LT` = `android-app/feature/life/src/test/kotlin/com/architectpsg/commandlibrary/feature/life`, `ST` = `android-app/data/storage/src/test/kotlin/com/architectpsg/commandlibrary/data/storage/life`.

## Matrix

### 1. Action-time undo provenance (G07, G10, PX07-035, PX07-038; disposition 10)

| Item | Finding |
|---|---|
| Requirement | Undo uses immutable before/after-action snapshots plus the action's clock reading, so it stays correct after later checkpoints and restart. G07: "Undo distinguishes elapsed time from reversible turn assignment." |
| Existing | C: `GameEventArchive.provenance` with `before`, `afterAction`, `reading`. D: four-argument `SeatClockReducer.undo(before, afterAction, current, reading)` (`LS/domain/SeatClockReducer.kt:175`), `LifeTimingUndo.undo` (`LS/domain/LifeTimingUndo.kt:30`), `LifeSemanticUndo.undo` (`LS/domain/LifeSemanticUndo.kt:10`); tests `SeatClockReducerTest.kt:60-72` (undo against a later settle checkpoint), `:386`, `:399`, `:517` (undo after restart), `LifeSemanticUndoTest.kt:62,73,85,101,143`. L: `SqlLifeGameCommands.undo` (`LS/persistence/SqlLifeGameCommands.kt:38`) selects the latest un-undone original via `latestUndoTarget` (`LS/persistence/LifeActionContext.kt:24`); `SqlLifeGameCommandsTest.kt:26` (group undo, file reopen, undo event group and `undoOfEventId`), `SqlLifeGameMutatorTest.kt:85` (no double undo), `:193` (legacy unknown target unsupported), `:255` (undo of undo rejected), `:124` (action cannot refund time after an intervening checkpoint). |
| Missing operational behavior | `GameService.undo(id, cancel)` and `PersistedGameService.undo(id, expected, cancel)` (`GameContracts.kt:231`, `LifePersistenceContracts.kt:66`) take no clock reading. The reading must come from the service-owned clock and process session (`SqlLifeGameSessions.observation()`, line 118). That wiring exists only as an unbound class. |
| Gap A1 | No L test undoes an original action after a settle checkpoint written between the action and the undo. `SqlLifeGameCommandsTest` calls `life.resume` only at lines 117, 120 and 167; none is followed by an undo of an action taken before that checkpoint (167 undoes actions taken after it). The checkpoint-after-target case is D-only. |
| Gap A2 | No L test undoes an original action in a new process session after `recoverAfterRestart`. `SqlLifeGameCommandsTest.kt:113` restarts, then dispatches, never undoes the pre-restart action. |
| Gap A3 | I found no L test that drives PER_TURN refill, SHARED or elapsed undo (restore versus keep elapsed) through stored provenance. Those policies are covered at D level only. |
| Future tests | A1 and A2 as real-file SQLite reopen tests: start clock, dispatch SwitchTurn, `resume` (checkpoint), optionally cross to a new process id and recover, then `undo`; assert balances, turn target, elapsed, alerts and the stored `undoOfEventId`. Repeat for PER_TURN, SHARED, CHESS, and restore versus keep elapsed. |
| Dependencies | Service clock/process-session provider (`Clock`, `processSessionId` in `SqlLifeGameSessions`); DI owner is root. |

### 2. Result retraction, finish and game reopening (G09, PX07-038; dispositions 3, 13)

| Item | Finding |
|---|---|
| Requirement | "End confirmation commits result once. Undo/reopen removes or updates previous result rather than double counting." Finish settles and pauses the clock in the same transaction; a completed snapshot cannot hold a running clock. |
| Existing | C: `GameAction.Finish`, `FinishWithOutcomes`, `Reopen` (`GameContracts.kt:172`), `GameResult.retracted`. D: `LifeResultEngineTest.kt:62,80,98,101,111,113` (reopen retracts exact facts, refinish reuses identity, invalid reopen cases), `LifeSemanticUndoTest.kt:62,73`. L: `SqlLifeGameCommandsTest.kt:71` (finish, undo, refinish: one result id, exact event revision, `count(*) FROM life_result = 1`), `SqlLifeGameMutatorTest.kt:63`, `:152` (initial result cannot arrive retracted), `SqlLifeGameLifecycleTest.kt:176` (completed result survives recovery across a huge gap), `SqlLifeGameHistoryTest.kt:114` (retracted draw and unknown deck knowledge in history). |
| Gap B1 | `GameAction.Reopen` appears only in D tests (`LifeSemanticUndoTest`, `LifeResultEngineTest`, `LifeActionEventEngineTest.kt:69`). No L test dispatches `Reopen` through `SqlLifeGameCommands` or undoes a `Reopen` against stored rows. Reopen as a user action is therefore not shown to persist, retract the row and refinish correctly. |
| Gap B2 | The cited L tests do not assert the clock is paused and the elapsed timer stopped in the stored completed snapshot (disposition 13). Only the D reducers and recovery test above touch that. |
| Gap B3 | History entries expose `result` with `retracted`, but this audit found no consumer that excludes retracted results from win/loss or linked-deck stats (G09). Stats code was not examined and may not exist yet; it is a consumer dependency, not shown here. |
| Future tests | L: finish, `Reopen`, edit counters, refinish with different outcomes; assert one `life_result` row, stable id, retracted flag between steps, completed snapshot clock paused. L: undo `Reopen`, then repeat undo. Stats test belongs to whichever task builds stats. |
| Dependencies | Gap B3 depends on a stats consumer not in this slice. |

### 3. Alert acknowledgement and delivery (G13, PX07-067; dispositions 9, 14)

| Item | Finding |
|---|---|
| Requirement | Durable acknowledgement retained through history and undo; fired occurrence ids and delivery state atomic and idempotent; zero fires once per transition. |
| Existing | C: `GameAction.AcknowledgeGameAlert` (`GameContracts.kt:161`). S: `life_alert` with `index_life_alert_acknowledgement` (`data/storage/.../schema/Migration5To6.kt:40-46`), `LifeMigrationTest.kt:82,84`. D: `LifeTableEngineTest.kt:93`, `LifeSemanticUndoTest.kt:85`. L: `SqlLifeGameCommandsTest.kt:161` (ack and delivery bits survive finish undo, result revision unchanged), `SqlLifeGameMutatorTest.kt:73,276,286` (delivery survives undo and checkpoint, historical occurrence cannot be recycled, acknowledged cannot be unacknowledged), `SqlLifeGameLifecycleTest.kt:63,127,176`. |
| Missing operational behavior | `SqlLifeGameMutator.markDelivered` (`LS/persistence/SqlLifeGameMutator.kt:90`) has no caller outside tests. Nothing triggers delivery, owns retries, or presents the foreground visual, haptic or audio alert G13 requires. |
| Gap C1 | No L test for a duplicate `AcknowledgeGameAlert` dispatch through `SqlLifeGameCommands` (second ack on an already acknowledged alert, unknown alert id, ack on a completed game). D `LifeTableEngineTest.kt:93` covers wall-clock reversal idempotence only at engine level. |
| Gap C2 | No test that a delivery retry racing a concurrent command preserves both (mask bits and revision). `SqlLifeGameSessionsTest` serializes commands but does not cover `markDelivered`. |
| Future tests | L duplicate and unknown-id ack; L `markDelivered` interleaved with `dispatch` and `undo` in one `SqlLifeGameSessions` queue. Foreground alert presentation and preference handling: UI and P tests only. |
| Dependencies | Alert dispatcher and presentation are unowned and belong with the service/UI task. |

### 4. History filters and pagination (G11)

| Item | Finding |
|---|---|
| Requirement | Filter by player, linked deck and date range, alone or AND-combined; active filters and result count visible; date bounds include both end days; deleted deck's games findable by stored name; genuine empty state; works offline, by keyboard, by screen reader. |
| Existing | C: `GameHistoryFilter(playerName, deckId, deckName, fromInclusive, untilExclusive)`, `GameHistoryPage.totalMatches` (`LifePersistenceContracts.kt:27,50`), `PersistedGameService.history` (line 68). L: `SqlLifeGameHistory` and 10 tests in `ST/SqlLifeGameHistoryTest.kt` (:29 combined filters and tombstoned names, :43 Unicode and half-open subsecond bounds, :54 stable cursor and full count, :65, :78, :90 byte budget, :103, :114, :138, :149). Details in `HISTORY-QUERY-LOCAL-20261014.md`, not repeated. |
| Missing operational behavior | `history` is not reachable through any `GameService`. No history UI exists. |
| Gap D1 | The contract takes UTC instants. G11 says "date bounds include both end days". Nothing defines who converts a user-chosen calendar day, in which time zone, to `fromInclusive` and `untilExclusive`. This is a missing rule, not a stored-data gap. |
| Gap D2 | No test changes a game's result, status or revision between two page fetches (finish, reopen or undo mid-pagination). The tests cover edited and deleted anchors only. |
| Gap D3 | Host bundled SQLite is not Room on a device, so query plan, collation and `BINARY` ordering behavior on Android SQLite are unproven. |
| Future tests | Pure-function test for calendar-day to UTC bounds with DST and time zone cases once D1 is ruled. L: undo or reopen a listed game between page 1 and page 2. P: keyboard and TalkBack filter flow and date picker on the device matrix. |
| Dependencies | D1 needs a root ruling on time zone ownership. UI depends on the service binding. |

### 5. Checkpoint and restart clock recovery (G07, G10, G13; dispositions 8, 10)

| Item | Finding |
|---|---|
| Requirement | Settle checkpoints persisted; a running clock paused exactly once per new process session; unknown wall-time gaps never charged; exact state and log return after force stop; uncommitted groups roll back. |
| Existing | D: `SeatClockReducer.settle` (:97), `recoverAfterRestart` (:141); `LifeTimingEngineTest.kt:153`. L: `SqlLifeGameLifecycle.resume` and `checkpoint` (`LS/persistence/SqlLifeGameLifecycle.kt:28,31`); `SqlLifeGameLifecycleTest.kt:63` (same-process settlement persists occurrence once, no revision change), `:76` (new process pauses both ledgers at durable balances, repeat recovery is a no-op), `:127` and `:207` (alert write failure and cancellation roll back checkpoint and settlement), `:176` (huge gap), `:194` (unsafe lineage pauses for repair); `SqlLifeGameCommandsTest.kt:113` (foreign running process needs explicit recovery); `SqlLifeGameMutatorTest.kt:53,112,124,132,143,206`. |
| Missing operational behavior | Nothing calls `resume` on cold start or process change in a real app. `SqlLifeGameSessions` takes a `Clock` and `processSessionId` (constructor) but no Android provider for a monotonic clock or a per-process id exists in `app/`. |
| Gap E1 | Crash and force-stop is tested only as host-side cancellation and injected SQL failure (rollback). No test kills a writing process and reopens the file, as G10 states ("Force stop after committed event"). |
| Gap E2 | Backgrounding versus process death versus device reboot (monotonic clock resets) are not distinguished by any test; `reading(5000, 1, newProcess)` style tests use synthetic monotonic values. |
| Gap E3 | One test fixture (`HostLifeDatabase`) per case; no test with two `SqlLifeGameSessions` instances over one file, which is what a second process would be. |
| Future tests | P: force-stop during a running clock on the target device, relaunch, assert paused balances equal last durable settle, no gap charge. L: two sessions instances over one file with distinct process ids. L: reboot case where the new monotonic reading is below the stored anchor. |
| Dependencies | Android clock and process-session provider, app-start recovery call, DI: root-owned. |

## Cross-cutting blockers for the eventual service

1. `PersistedGameService` has no implementation, and `GameService.undo`/`dispatch` omit the clock reading the SQL layer needs, so the service must own the clock source (`SqlLifeGameSessions` is the intended pattern, unbound).
2. No `watch`/`observe` evidence beyond `SqlLifeGameSessionsTest` local ordering; cross-process watch is explicitly out of that class's scope.
3. Root owns DI, navigation and the Android clock provider; none exists.
4. Everything above is D or L. Room-on-device, UI, accessibility and Fold7 evidence remain separate and unstarted.

## Verification of this report

Paths and requirement ids above were checked against the checkout at the base commit by reading the cited lines. Test names are taken from test source; the tests were not run here. `git diff --check` is recorded in the task card delta.
