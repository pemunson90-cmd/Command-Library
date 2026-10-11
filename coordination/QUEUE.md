# Execution queue

Checkpoint-ID: cp-20261011-k1-start-03. Active task: orch-kotlin-toolchain-01 step1, K1 readiness and architecture translation.

| Task | Stage | Status | Prerequisites | Next |
|---|---|---|---|---|
| orch-architecture-plan-01 | G3-A contract planning | accepted independent audit | reviewed Gate2/D17 | resolve independent findings |
| orch-architecture-review-01 | G3-A independent audit | completed, accepted | corrections rechecked | report saved |
| orch-kotlin-plan-audit-01 | K0 plan consistency | completed, approved | D-20 recorded | report K0-KOTLIN-PLAN-AUDIT.md |
| orch-kotlin-toolchain-01 | K1 Kotlin readiness | active | Astra-high readiness approval + user K1 release | source-backed candidate, Kotlin ownership map and probe plan |
| orch-kotlin-toolchain-01 | K1 Kotlin readiness | active | Astra-high readiness approval + user K1 release | source-backed candidate, Kotlin ownership map and probe plan |
| orch-toolchain-01 | historical Flutter readiness | superseded for v1 by D-20 | retained evidence only | no action |
| orch-source-fixtures-01 | source evidence | conditional fallback only | K1 identifies an unanswered evidence need | one approved read-only official-source probe |
| orch-m0-01 | historical Flutter M0 | superseded/ineligible | D-20 | no action |
| worker-core-m0-01 | M0 core | planned | pinned workspace +accepted architecture | value/public DTO contracts +unitfixtures |
| worker-storage-m0-01 | M0 SQLite | planned | accepted core/registry/native tuple | FTS5/transaction tests |
| worker-catalog-m0-01 | M0 tiny import | planned | accepted network/storage/authentic fixture | atomic real staging |
| worker-shell-m0-01 | M0 genuine UI | planned | real catalog/settings/query path | working status/search-only UI |
| orch-m1-01 | M1 offline data | planned | accepted M0/source proofs | bounded real English/rules |
| worker-rules-m1-01 | M1 rules | planned | actualofficialsource/acceptedports | corpus parser/refglossary |
| worker-search-m1-01 | M1 localquery | planned | acceptedcatalog/rules/storage | AST/semanticfixtures |

Higher later M2-M9 scopes in MILESTONES. Worker tasks need claimed locks and actual merged interfaces, not merely this row. If write/toolchain access unavailable, preserve checkpoint and source-audit fallback; no claimedbuild or silently skipped requiredtest. At most3 component workers only when disjoint safe worktrees available (D-08 ruled 3; at most 4 subagents of any role at once, 2 after a usage-limit stop); single orchestrator.
