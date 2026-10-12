# Execution queue

Checkpoint-ID: cp-20261012-k2-storage-verified-22. Next: orch-kotlin-m0-01 step2; in_progress.

| Task | Stage | Status | Prerequisites | Next |
|---|---|---|---|---|
| orch-architecture-plan-01 | G3-A contract planning | accepted independent audit | reviewed Gate2/D17 | resolve independent findings |
| orch-architecture-review-01 | G3-A independent audit | completed, accepted | corrections rechecked | report saved |
| orch-kotlin-plan-audit-01 | K0 plan consistency | completed, approved | D-20 recorded | report K0-KOTLIN-PLAN-AUDIT.md |
| orch-kotlin-toolchain-01 | K1 Kotlin readiness | completed; independently accepted | Astra-high final review + observed hosted capability | report K1-KOTLIN-TOOLCHAIN and K1-FINAL-REVIEW |
| orch-kotlin-m0-01 | K2 functional Kotlin foundation | active step2; incomplete | accepted actual core/contracts/tuple/design | reviewed storage/settings, then local importer/UI/emulator acceptance |
| k2-core-contracts | K2 pure contracts | completed; compiled and independently accepted | corrected actual declarations25 JVM tests/Astra review | integrated unchanged; core lock released |
| cc-k2-qa-01 | K2 parallel Claude Code QA | completed; independently reviewed/integrated | user D-23; docs/vectors only | lock released; assertions remain not_run |
| cx-storage-01 | K2 Room3 storage | delegated; preserved schema step1 | accepted core/tuple/design + current storage lock | step2 real Database adapter, then instrumentation/review |
| cc-settings-01 | K2 persisted settings | completed; original source preserved and corrected implementation integrated | independent scoped review + hosted evidence | app/runtime integration remains in root K2 card |
| cx-settings-01 | K2 settings remediation | completed; integrated and lock released | 68 JVM tests, fatal API lint and library assembly passed at4f20764 | no further component writer assigned |
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
