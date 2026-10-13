# Independent life-domain review

Reviewed HEAD: `c4a27190d343d3d01ece4d0c70c26c0f87d45736`, detached worktree `/workspace/scratch/Command-Library-life-review`.
Code-final commit: `08d5de0aad0f9da7aee5fa045042f84c2499dcca`; no feature/life differences between that commit and HEAD.
Comparison base: `fef937aa726681044ae8b9b70cb0e9e3787e60f5`.
Scope: nine changed files, all within the task's owned domain main/test paths and its handoff. Worktree remained clean; review/probes live outside it. No additional agents were used.

## Conclusion

Request changes before accepting this exact code: two independently reproduced P2 defects in PER_TURN / KEEP_ELAPSED undo remain despite the passing repository suite. They require technical corrections and regression coverage, not new product rulings. No independent blocker was found in the counter or random-tool implementations within the assigned private-domain scope. This is a component review, not M2/K3/full-app or all G01-G13 acceptance.

### P2: undo refunds pre-refill elapsed time when the same seat advances its turn

Location: `android-app/feature/life/src/main/kotlin/com/architectpsg/commandlibrary/feature/life/domain/SeatClockReducer.kt:217-225`, called by the KEEP_ELAPSED branch at 186-196.

Reproduction: one-seat practice game, PER_TURN, initial 30s, current balance 10s, running anchor 100s. SwitchTurn to the same seat at 105s charges 5s then refills to 30s. Undo at 108s under KEEP_ELAPSED must retain both the outgoing 5s charge and the incoming 3s charge: balance 2s. Actual success returns 7s, refunding the outgoing 5s. `revertTurnRefill` reconstructs from `before.remaining` minus only the post-refill charge, forgetting that this seat was also outgoing. This can also affect same-seat advancement in a larger table because `switchTurn` permits it.

Evidence: scratch `soloUndoMustKeepTimeChargedBeforeAndAfterRefill` fails with expected 2s / actual 7s. Existing `perTurnKeepElapsedUndoRevertsTheRefill` advances at precisely the anchor, so outgoing charge is zero and the defect is hidden.

Recommendation: reconstruct the refill amount from the actual balance immediately before the refill, accounting for the outgoing settlement when incoming and outgoing are identical, while respecting restart/session and zero-policy rules. Do not charge elapsed time twice. Add a nonzero pre-switch interval regression.

### P2: undoing a refill bypasses PAUSE/ELIMINATE zero handling

Location: the same `SeatClockReducer.kt:217-225` helper and caller at 186-196.

Reproduction: two-seat PER_TURN game, outgoing A active, incoming B old balance 10s and initial 30s. Switch to B at anchor 100s; undo at 115s under KEEP_ELAPSED. Current B has 15s, so settling the refilled timeline does not cross zero. Reversing its refill computes B = 10s - 15s = -5s. Actual successful undo leaves B at -5s under both PAUSE and ELIMINATE and emits no alert/timeout. The helper checks finiteness but never applies zero policy to the reconstructed balance. B is now inactive; a subsequent settle charges only A and cannot repair B. When B later becomes active, PER_TURN refills it, so the unreported crossing can be permanently lost.

Evidence: scratch `pauseUndoMustClampRestoredBalanceAtZero` fails expected 0s / actual -5s; `eliminateUndoMustReportRestoredBankCrossing` fails expected [B] / actual []. This is one defect with two policy manifestations.

Recommendation: apply configured zero policy and transition reporting to the restored bank, preserving idempotence and explicit undo semantics; keep PAUSE/ELIMINATE banks at zero and report an ELIMINATE candidate for confirmation rather than deleting a seat. Add both zero-policy regressions and a replay/idempotence case.

## Evidence actually run

Exact checked-out core/model production/test sources and life production/test sources were compiled with the repository-pinned Kotlin compiler 2.2.10 and cached real dependencies in `/workspace/scratch/kotlin-probe/lib`. Production model and domain compiled separately with `-Xexplicit-api=strict`, `-jvm-target 17`, correct actual-model classpath; tests compiled without explicit-api strictness with friend paths for internal declarations. No substituted GameContracts, Values, stubs, or domain sources were used. Execution Java was OpenJDK 21.0.12.1; target bytecode was JVM17.

- model compilation: exit 0, no diagnostics.
- domain compilation: exit 0, no diagnostics.
- test compilation: exit 0, no diagnostics.
- JUnit repository baseline: exit 0, `OK (85 tests)` (16 model + 25 counter + 32 clock + 12 random).
- scratch regression compilation: exit 0, no diagnostics.
- scratch JUnit regressions: exit 1, 3 tests / 3 assertion failures reproducing the two defects above.
- git ownership comparison and clean-worktree check: completed; nine owned changed files, no review edits.

Commands and logs: `/workspace/scratch/m2-life-review-probe/run.py`, `model-command.txt`, `domain-command.txt`, `test-compile-command.txt`, `JUnit-baseline-command.txt`, their `.log` files, `regression-compile-command.txt`, `JUnit-regressions-command.txt`, `JUnit-regressions.log`, and `ReviewRegressionTest.kt`.

Harness correction: an initial combined production/test invocation incorrectly applied explicit-api strictness to tests and failed on implicit test visibility; this was a harness setup error. The corrected separate-production/friend-test compilation above passed. A first regression runner invocation raced creation of the scratch test file and reported class-not-found; the separately compiled final regression run above produced real assertion failures. Neither setup failure is attributed to product code.

`not_run` by this reviewer: exact Gradle `cd android-app && bash gradlew --no-daemon --console=plain :core:model:test :feature:life:test` and hosted CI; parent orchestrator is obtaining that evidence. No Android SDK is needed for the life JVM module itself, though whole-build configuration/dependency resolution is separate from this pure compiler/JUnit proof. No UI/device, OS background notifications, persistence/force-stop, source-license/content authenticity, variant-module runtime, GameService transaction, performance, or full acceptance runs were performed. Author's reported 69+16 tests and earlier eight-agent review were treated as claims, not independent proof; the reproduction above is this review's evidence.

## Source and requirement assessment

Read the assigned task/handoff, AGENTS/CONTINUE, life ARCHITECT, active Kotlin contracts/ownership, actual core/model GameContracts and Values, SPEC G01-G13 (G12/G13 are the later explicit rows), DATA_MODEL timing/identity/durability rows, relevant PARITY_ACCEPTANCE PX07 rows and R-07 counter-clock behavior matrix. Historical Dart paths do not supersede current Kotlin declarations. Only the four production domain files and their four test/fixture files changed.

- Immutable/atomic reductions: `SnapshotList` defensively copies; counter working state is privately owned; rejected groups discard it. Delta uses Long before narrowing, bounds reject rather than clamp, negative life remains supported. Nested groups are rejected before recursion, including the 200000-deep test. Flat group length has no explicit cap; this task defines no numerical flat-action limit, so this is not a blocker, but a service must keep user hold batches responsive.
- Counter identity: explicit role IDs select behavior; duplicate/missing seats, definitions and commander slots are validated. Damage is keyed by victim seat and source slot; partner values/loss thresholds remain separate. Tax requires the seat's own slot. Damage-plus-life returns one validated Group and invalid second steps do not modify input. Hiding preserves values and handles recorded own-source damage and partner tax. Absent values use definition.initial, including loss assessment. Results keep revision/event pointers for the committing service.
- Loss handling: policies are definition-driven and rad is not implicitly poison. Reports are candidates, not persisted elimination. Product acceptance still needs per-format legitimate policies, rule version/evidence, manual override/confirmation, logged elimination/restore, and unique transactional result/stat updates. The core contract currently does not express the loss-policy rule-source version; source attribution must be designed before claiming G03/G09 complete.
- Clock accounting: readings and balances/anchors are finite, running session mismatch and monotonic regression reject, wall time never measures elapsed duration. Restart pauses/reanchors with durable balances preserved; live nonpersisted elapsed time is deliberately not reconstructed from wall time. Active-seat-only charging, pause/switch, long turn overflow, zero alert occurrence IDs, redo, restore-time timeline and restart behavior have meaningful deterministic coverage. Remaining defects are the two refill-undo cases above. `ClockView` is a live unsettled view; accessible foreground delivery and background platform guarantees require later integration.
- Random tools: positive sides (including Int.MAX_VALUE), 1..10000 count, Long sum, capped batch with remaining count, injected RNG, Fisher-Yates and complete seat permutations are sound. Invalid source outputs produce INTERNAL with no partial result, although consumed RNG draws cannot be rolled back. Completed/corrupt random reorder rejects before drawing. Seat/opponent selection is from the supplied eligible set, canonicalized to table order, with explicit no-eligible outcome and timestamp. Elimination filtering is necessarily caller-owned until a real elimination contract exists. Selection/result logging is unimplemented because there is no action variant for it.
- G01-G04/G06/G07/G09/G13 and relevant PX07-001/002/004-019/024-031/034-038/045/047/057/058/067 are partially supported by this private-domain slice. G05 designations, G08 art/profile UI, G10 transaction/event-service durability, G11 full parity/history/UI, G12 variants, and G13 alert delivery are not implemented or accepted here. Generic counters do not independently prove storm/mana reset policies or object-specific presets. Absence of these later features is allowed by the assigned scoped task and remains an integration obligation.

## Handoff requests 1-14: technical recommendations

These are recommendations for orchestration/contract review, not edits made by this reviewer. Parent supplied updated Pat rulings during review; that information is recorded as parent-reported authorization, not independently committed repository evidence.

| # | Review disposition |
|---|---|
| 1 | Accept need for durable role identity. Add CounterDefinition.role or persisted GameSettings role bindings with migration and distinct-binding validation. Persist roles before real resume/service integration. Avoid display-name inference. |
| 2 | Keep current strict equality invariant until contract cleanup; document initialLife equals bound life initial and validate creation/import/migration. Removing duplicate state is optional future contract refactoring. |
| 3 | Parent reports Pat ruled to ASK before elimination because cards/effects can prevent losing. Candidate reporting is appropriate; add durable confirmed elimination state/action, reason, restore/log/undo with explicit override. ELIMINATE clock policy must produce a prompt/candidate, not automatically persist defeat. |
| 4 | No new contract needed for required per-seat/per-counter hiding. Slot-specific hiding is optional future scope and cannot be inferred from current action. |
| 5 | Parent reports Pat delegated technical judgment; orchestrator chooses SHARED as one global game countdown, distinct from per-seat clocks/elapsed timers. Keep current Unsupported truthful and unbound; implement a defined single pool and tests in a new contract/reducer task before exposing it. |
| 6 | Keep and document clockMode equality; enforce it at construction/import/migration. Future removal of duplication is a compatibility change, not needed to make this slice reviewable. |
| 7 | Accept persistent elapsed-game timer requirement gap (G07/PX07-031). Store elapsed progress/paused policy with restart anchors; a lone duration field is insufficient without clear settling/recovery semantics. Distinguish game elapsed, per-turn elapsed, per-seat banks and shared countdown. |
| 8 | Accept need to represent priority/clock handoff separately from turn advancement (PX07-047). Define PassClock semantics across CHESS/PER_TURN, ensuring no turn increment or accidental per-turn refill on priority pass. |
| 9 | Add durable acknowledgement action only when the chosen foreground/notification UX requires it. Existing acknowledgedAt cannot currently be mutated; do not claim acknowledgement persistence. Notification integration must respect preferences. |
| 10 | Accept settle checkpoints + one recoverAfterRestart before other running calculations per process session. Checkpoint cadence/lifecycle and save failures are service obligations. Never claim zero loss of unpersisted elapsed time. |
| 11 | Accept missing random-event contract. Record dice faces/sides/sum, coin, seat ID or no eligible outcome, eligible set and actual event time in immutable typed data; use GameEvent.at or explicit timestamp consistently. Persist requested/batch/remaining relationships for larger dice histories and replay recorded results without redrawing. |
| 12 | Parent reports Pat approved official CR903.10a including a player's own commander when stolen. Existing ANY_COMMANDER implementation is technically consistent; retain per-slot identity and record the ruling/source version in integration docs. No code narrowing recommended. |
| 13 | Accept Finish must settle and pause in the same event/snapshot/result transaction. Preserve completed/paused clock invariants and reopen/undo policy; pure reducer does not prove transaction execution. |
| 14 | Current occurrence IDs are suitable within retained firedAlertIds and isolate seat/turn. Persist fired state + alert + game event atomically; make delivery retries idempotent. Do not prune/reset fired IDs without defined occurrence lineage; persistence keys should include game identity. |

## Latest continuation delta

Independent exact-head review completed. Baseline 85 tests reproduced, two P2 undo defects confirmed through three scratch assertion failures, no repository edits. Next: parent fixes these cases on its integration branch with meaningful regressions, obtains exact hosted Gradle evidence, records the reported Pat rulings and contract decisions, then sends the changed undo logic for focused independent re-review. This report does not approve merging the faulty exact HEAD or claim full life acceptance.
