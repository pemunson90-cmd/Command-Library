# Execution queue

Checkpoint-ID: cp-20261011-k1-evidence-08. Next: orch-kotlin-toolchain-01 step4; in_progress.

| Task | Stage | Status | Prerequisites | Next |
|---|---|---|---|---|
| orch-architecture-plan-01 | G3-A contract planning | accepted independent audit | reviewed Gate2/D17 | resolve independent findings |
| orch-architecture-review-01 | G3-A independent audit | completed, accepted | corrections rechecked | report saved |
| orch-kotlin-plan-audit-01 | K0 plan consistency | completed, approved | D-20 recorded | report K0-KOTLIN-PLAN-AUDIT.md |
| orch-kotlin-toolchain-01 | K1 Kotlin readiness | active | Astra-high readiness approval + user K1 release | source-backed candidate, Kotlin ownership map and probe plan |
| orch-kotlin-m0-01 | K2 functional Kotlin foundation | planned; awaits K1 review | accepted K1 candidate/map + hosted capability | real contracts/local import/settings/search, hosted debug APK/tests |
| orch-toolchain-01 | historical Flutter readiness | superseded for v1 by D-20 | retained evidence only | no action |
| orch-source-fixtures-01 | source evidence | conditional fallback only | K1 identifies an unanswered evidence need | one approved read-only official-source probe |
| orch-m0-01 | historical Flutter M0 | superseded/ineligible | D-20 | no action |
| worker-core-m0-01 | historical Dart M0 core | superseded/ineligible | D-20 | replaced by reviewed K2 card |
| worker-storage-m0-01 | historical Dart M0 SQLite | superseded/ineligible | D-20 | replaced by reviewed K2 card |
| worker-catalog-m0-01 | historical Dart M0 import | superseded/ineligible | D-20 | replaced by reviewed K2 card |
| worker-shell-m0-01 | historical Dart M0 UI | superseded/ineligible | D-20 | replaced by reviewed K2 card |
| orch-m1-01 | historical Dart M1 data | superseded/ineligible | D-20 | Kotlin K3 card required after K2 |
| worker-rules-m1-01 | historical Dart M1 rules | superseded/ineligible | D-20 | Kotlin K3 card required after K2 |
| worker-search-m1-01 | historical Dart M1 query | superseded/ineligible | D-20 | Kotlin K3 card required after K2 |

Higher later M2-M9 scopes in MILESTONES. Worker tasks need claimed locks and actual merged interfaces, not merely this row. If write/toolchain access unavailable, preserve checkpoint and source-audit fallback; no claimedbuild or silently skipped requiredtest. At most3 component workers only when disjoint safe worktrees available (D-08 ruled 3; at most 4 subagents of any role at once, 2 after a usage-limit stop); single orchestrator.
