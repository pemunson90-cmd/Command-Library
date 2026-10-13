# Independent focused re-review

Reviewed exact candidate: `ce97e47e0d072f83d1668802543cca004db23d12` on `origin/agent/cx-life-domain-fix-01`, clean worktree `/workspace/scratch/Command-Library-life-fix`.
Code commit: `dc8c7bd7024b0c2ff8d8bbf411353a0a0bbc7088`; candidate adds only the handoff after that code commit.
Baseline: `c4a27190d343d3d01ece4d0c70c26c0f87d45736`. Changed production code is confined to SeatClockReducer; tests add 12 methods, and the only other new file is the fix handoff. No worktree edits were made.

## Disposition

Approve this exact candidate for the assigned private life-domain slice. Both P2 findings in `/workspace/scratch/m2-life-domain-review.md` are resolved. No new scoped blocker was found in the changed logic. Approval does not claim hosted Gradle or full M2/life/GameService acceptance.

The refill helper now calculates the pre-refill balance by settling the original before snapshot at the original action anchor when outgoing and incoming seats match. This retains elapsed charges on both sides of a same-seat turn. For later checkpoints/pause/restart, the four-argument undo overload receives the immutable immediate after-action snapshot and uses its original anchor rather than the newer current anchor. The overload checks same game, turn, clock mode, active seat and seat-set; running same-seat refill additionally requires the action bank to equal its initial refill and a comparable original process session. These are structural/provenance preconditions, not an independently reconstructed event lineage.

The reconstructed bank now follows zero policy: PAUSE/ELIMINATE clamp at zero and pause, ALERT preserves negative overtime, and newly reconstructed crossings emit one alert and, under ELIMINATE, one candidate. Already reported crossings are suppressed without eliminating seats, and redo picks the next unused occurrence. Existing immutable failure behavior, revision/event-pointer handling, same-session monotonic time, recovery pause and no wall-clock charging remain intact. Added deterministic tests cover both same-seat table sizes, all zero policies, pause, checkpoints, restart, outgoing-zero replay, incoming-zero replay/redo and mismatched supplied provenance.

## Verification actually run independently

Ran `/workspace/scratch/m2-life-rereview-probe/run.py` against exact candidate production/test sources and actual cached jars from `/workspace/scratch/kotlin-probe/lib`; no model/domain substitutes. Kotlin 2.2.10, separate model/domain compilation with `-Xexplicit-api=strict`, JVM17 target, friend-path test compilation, OpenJDK21 execution:

- model compilation exit 0, no diagnostics.
- domain compilation exit 0, no diagnostics.
- test compilation exit 0, no diagnostics.
- repository JUnit exit 0: `OK (97 tests)` = 16 model + 25 counter + 44 clock + 12 random.
- the unchanged original independent scratch regression tests all pass, exit 0: `OK (3 tests)`; they previously failed on original exact sources (expected 2s vs 7s, clamped 0s vs -5s, expected timeout candidate vs empty).
- `git diff --check` and clean-worktree check completed without findings.

Commands, logs and original regression copy are in `/workspace/scratch/m2-life-rereview-probe`. The parent-produced evidence in `/workspace/scratch/m2-life-fix-probe` and its red runs were inspected as context; approval's runtime assertions use the separate independent reproduction above.

`not_run` by this reviewer: exact Gradle task, hosted CI, device/UI/OS delivery, persistence/force-stop, GameService transactions, performance, complete G01-G13 acceptance. Those limitations are unchanged from the original review.

## Material integration conditions

Future GameService must persist/retain the immutable before and immediately after-action states (or a reviewed equivalent event-time provenance representation) and pass the four-argument overload when same-seat PER_TURN undo runs after checkpoints, pause/resume or recovery. The three-argument convenience overload is valid only for the documented immediate after-action state in that case; its guard rejects detectable missing provenance, but a structurally similar later state is not a cryptographic/event-lineage proof. Caller transaction/undo selection must enforce the precondition rather than relying on bank equality to establish history.

ELIMINATE outputs remain candidates requiring confirmation and override under Pat's reported ruling. SHARED remains truthfully unsupported in this slice pending the new shared-countdown contract/reducer task. Durable role bindings, random-result logs, elapsed timers, priority passing, loss-source versions, confirmed elimination/restore, alert persistence, service atomicity and all UI/variant requirements remain as documented in the original request assessment. No unrelated product requirement was narrowed by the correction.

Next: parent records exact-head hosted build/test evidence and this independent approval, integrates per repository protocol, and assigns the real service/contract/UI work separately.
