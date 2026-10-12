# Execution queue

Checkpoint-ID: cp-20261012-k3-gap-execution-39. Next: orch-kotlin-k3-offline-data-01 step3; K3 active implementation and verification.

This file lists runnable or conditionally runnable work. Completed and superseded task history remains in task cards, gate reports, journal/events and git history rather than occupying the live queue.

| Task | Stage | Status | Prerequisites | Next |
|---|---|---|---|---|
| orch-kotlin-k3-offline-data-01 | K3 offline catalog/rules/search | active; recovery orchestrator | K2 accepted; existing K3 candidates preserved | repair diagnostic compile failure; review contracts and source evidence; implement production bindings |
| cc-k3-rules-parser-01 | K3 internal rules parser | complete; merged and hosted verified | genuine source and production binding remain gated | no parser reassignment |
| cx-k3-catalog-framer-01 | K3 local bulk framing | preserved candidate; integration gated | combined matrix failed to compile shell diagnostics | repair shell diagnostic; obtain runtime evidence |
| orch-source-fixtures-01 | source evidence fallback | conditional only | current accepted task identifies an unanswered evidence need | one approved narrow read-only source probe |

## Accepted history summary

G3-A architecture planning/review, K0 Kotlin plan audit, K1 Kotlin/toolchain readiness and K2 Kotlin Android foundation are accepted. K2 core, storage, settings, catalog, shell and QA worker tasks are complete, integrated and unlocked. Detailed evidence is retained in their task cards and gate reports.

Historical Flutter/Dart version-1 tasks are superseded by the Kotlin Android direction and are not runnable. Do not resurrect them from old handoffs or GAUNTLET text.

## Later roadmap

K4/K5 and M2-M9 remain defined in `docs/gauntlet/V1_KOTLIN_EXECUTION_SPEC.md` and `docs/gauntlet/MILESTONES.md`. Add a concrete task row when a later section becomes the accepted next bounded task instead of pre-populating the live queue with speculative worker rows.

At most three component workers may run only when the orchestrator has explicit disjoint locks/worktrees and merged dependency interfaces. Active assignments are recorded in component locks and task cards; inspect their branch/handoff before reassigning.
