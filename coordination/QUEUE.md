# Execution queue

Checkpoint-ID: cp-20261011-handoff-03. Next task: orch-toolchain-01 step1; previous lease released. Use machine state and CURRENT, not stale historical pointers.

| Task | Stage | Status | Prerequisites | Next |
|---|---|---|---|---|
| orch-architecture-plan-01 | G3-A contract planning | accepted independent audit | reviewed Gate2/D17 | resolve independent findings |
| orch-architecture-review-01 | G3-A independent audit | completed, accepted | corrections rechecked | report saved |
| orch-toolchain-01 | G3-B readiness | ready to claim | accepted contract plan | verify current pins/checkout/hosted probe |
| orch-source-fixtures-01 | source evidence | eligible fallback | source gap registry | one unanswered official-source probe |
| orch-m0-01 | signed skeleton | planned, not eligible yet | accepted architecture/toolchain | orchestrate concrete foundation workers |
| worker-core-m0-01 | M0 core | planned | pinned workspace +accepted architecture | value/public DTO contracts +unitfixtures |
| worker-storage-m0-01 | M0 SQLite | planned | accepted core/registry/native tuple | FTS5/transaction tests |
| worker-catalog-m0-01 | M0 tiny import | planned | accepted network/storage/authentic fixture | atomic real staging |
| worker-shell-m0-01 | M0 genuine UI | planned | real catalog/settings/query path | working status/search-only UI |
| orch-m1-01 | M1 offline data | planned | accepted M0/source proofs | bounded real English/rules |
| worker-rules-m1-01 | M1 rules | planned | actualofficialsource/acceptedports | corpus parser/refglossary |
| worker-search-m1-01 | M1 localquery | planned | acceptedcatalog/rules/storage | AST/semanticfixtures |

Higher later M2-M9 scopes in MILESTONES. Worker tasks need claimed locks and actual merged interfaces, not merely this row. If write/toolchain access unavailable, preserve checkpoint and source-audit fallback; no claimedbuild or silently skipped requiredtest. At most3 component workers only when disjoint safe worktrees available; single orchestrator.
