# Execution queue

Checkpoint-ID: cp-20261012-fold7-functional-40. Next: orch-fold7-functional-01 step1; Functional Fold7 build awaiting Android verification.

This file lists runnable or conditionally runnable work. Completed and superseded task history remains in task cards, gate reports, journal/events and git history rather than occupying the live queue.

| Task | Stage | Status | Prerequisites | Next |
|---|---|---|---|---|
| orch-fold7-functional-01 | Functional Fold7 build | active root verification | D-22/D-23; reviewed real candidates preserved | green exact Android matrix, package APK, physical Fold7 install |
| orch-kotlin-k3-offline-data-01 | K3 full offline corpus | retained; paused behind working build | D-23 prioritizes functional APK | resume full-feed/rulings/coverage work later; no acceptance claim |
| orch-source-fixtures-01 | Source evidence fallback | policy gap closed; source-specific cases retained | FIXTURE_POLICY and genuine input evidence | exact rights/coverage review when a later acquisition/distribution path needs it |

## Accepted history summary

G3-A architecture planning/review, K0 Kotlin plan audit, K1 Kotlin/toolchain readiness and K2 Kotlin Android foundation are accepted. K2 core, storage, settings, catalog, shell and QA worker tasks are complete, integrated and unlocked. Detailed evidence is retained in their task cards and gate reports.

Historical Flutter/Dart version-1 tasks are superseded by the Kotlin Android direction and are not runnable. Do not resurrect them from old handoffs or GAUNTLET text.

## Later roadmap

K4/K5 and M2-M9 remain defined in `docs/gauntlet/V1_KOTLIN_EXECUTION_SPEC.md` and `docs/gauntlet/MILESTONES.md`. Add a concrete task row when a later section becomes the accepted next bounded task instead of pre-populating the live queue with speculative worker rows.

At most three component workers may run only when the orchestrator has explicit disjoint locks/worktrees and merged dependency interfaces. Active assignments are recorded in component locks and task cards; inspect their branch/handoff before reassigning.
