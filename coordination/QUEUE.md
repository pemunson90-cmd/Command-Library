# Execution queue

Checkpoint-ID: cp-20261012-k2-accepted-27. Next: orch-kotlin-k3-offline-data-01 step1; ready.

| Task | Stage | Status | Prerequisites | Next |
|---|---|---|---|---|
| orch-architecture-plan-01 | G3-A contract planning | accepted independent audit | reviewed Gate2/D17 | historical accepted planning |
| orch-architecture-review-01 | G3-A independent audit | completed, accepted | corrections rechecked | report saved |
| orch-kotlin-plan-audit-01 | K0 plan consistency | completed, approved | D-20 recorded | report K0-KOTLIN-PLAN-AUDIT.md |
| orch-kotlin-toolchain-01 | K1 Kotlin readiness | completed; independently accepted | Astra-high final review + observed hosted capability | report K1-KOTLIN-TOOLCHAIN and K1-FINAL-REVIEW |
| orch-kotlin-m0-01 | K2 functional Kotlin foundation | completed; independently accepted | real core/storage/catalog/settings/shell/app + hosted matrix | K2-FINAL-ACCEPTANCE.md; hand off K3 |
| k2-core-contracts | K2 pure contracts | completed; compiled and independently accepted | corrected actual declarations25 JVM tests/Astra review | integrated unchanged; core lock released |
| cc-k2-qa-01 | K2 parallel Claude Code QA | completed; independently reviewed/integrated | user D-23; docs/vectors only | lock released; retained historical QA |
| cx-storage-01 | K2 Room3 storage | completed; independently reviewed/integrated | accepted core/tuple/design | hosted storage matrix accepted; lock released |
| cc-settings-01 | K2 persisted settings | completed; source corrected/integrated | independent scoped review + hosted evidence | accepted in K2 app graph |
| cx-settings-01 | K2 settings remediation | completed; integrated and lock released | 68 JVM tests, fatal API lint and library assembly passed at4f20764 | accepted in K2 app graph |
| cx-catalog-k2-01 | K2 local catalog import/status/lookup | completed; reviewed/integrated | core/storage accepted | genuine fixture/parser/import/search evidence retained; lock released |
| cc-k2-shell-01 | K2 Compose foundation UI | completed; reviewed/integrated | core ports accepted | real shell/app wiring accepted; lock released |
| orch-kotlin-k3-offline-data-01 | K3 offline catalog/rules/search | ready; not claimed | K2 accepted at cp-20261012-k2-accepted-27 | claim lease; requirement/ownership delta first |
| orch-toolchain-01 | historical Flutter readiness | superseded for v1 by D-20 | retained evidence only | no action |
| orch-source-fixtures-01 | source evidence | conditional fallback only | future accepted task identifies unanswered evidence need | one approved read-only official-source probe |
| orch-m0-01 | historical Flutter M0 | superseded/ineligible | D-20 | no action |
| worker-core-m0-01 | historical Dart M0 core | superseded/ineligible | D-20 | replaced by accepted K2 |
| worker-storage-m0-01 | historical Dart M0 SQLite | superseded/ineligible | D-20 | replaced by accepted K2 |
| worker-catalog-m0-01 | historical Dart M0 import | superseded/ineligible | D-20 | replaced by accepted K2 |
| worker-shell-m0-01 | historical Dart M0 UI | superseded/ineligible | D-20 | replaced by accepted K2 |
| orch-m1-01 | historical Dart M1 data | superseded/ineligible | D-20 | replaced by orch-kotlin-k3-offline-data-01 |
| worker-rules-m1-01 | historical Dart M1 rules | superseded/ineligible | D-20 | K3 task owns current Android section planning |
| worker-search-m1-01 | historical Dart M1 query | superseded/ineligible | D-20 | K3 task owns current Android section planning |

Higher later K4-K5/M2-M9 scopes remain in V1_KOTLIN_EXECUTION_SPEC.md and MILESTONES. Worker tasks require claimed locks and actual merged interfaces, not merely a queue row. At most 3 component workers only when disjoint safe worktrees are available (D-08 ruled 3; at most 4 subagents of any role at once, 2 after a usage-limit stop); single orchestrator. No K3 worker is active at this checkpoint.
