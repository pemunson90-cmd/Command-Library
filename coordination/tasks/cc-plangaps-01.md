# cc-plangaps-01: planning gap fills on a side branch

| Field | Value |
|---|---|
| Role | orchestrator-directed side branch (Claude Code), not the D-17 orchestrator lease |
| Orchestrator | claude-code |
| Branch | `agent/cc-plangaps-01` |
| Base commit | 3799edb60bf2ab0b2cbb3e1ffc26613adcc0dd28 |
| Authority | Pat, 2026-10-11, verbatim: "Approve all" (plan items 1 to 6 and questions 2 to 4 of the Claude gap plan) |
| Status | active |

## Scope (planning documents only, no app code)
1. Gap report against base: `docs/gauntlet/gates/GATE-3-CC-GAPS.md`
2. RISKS.md entries (G-11, G-17)
3. IMPOSSIBLE.md screening (G-10)
4. POST_RELEASE.md deferred parity rows (G-12)
5. Claude agent definitions and neutral briefs aligned with D-17, keeping D-10 tiers (G-21)
6. SPEC acceptance text for life counter elimination, seat reorder, history filter (G-13); Surprise mode angles (G-18); English UI with externalized strings (G-15); COVERAGE E13 register row (G-16)
7. DECISIONS entry for Pat's G-15 ruling
8. Independent review, then merge to `main` under a claimed D-17 lease

## Constraints
- `python3 scripts/check_continuity.py` and `python3 scripts/test_continuity.py` must pass at every commit: SPEC keeps exactly 64 requirement rows, PARITY_ACCEPTANCE exactly 213 rows.
- Do not edit coordination/state.json, ORCHESTRATOR.json, QUEUE.md, handoffs/CURRENT.md, or any ARCHITECT/registry/coverage.json file.
- Merge only after a review approves and a D-17 lease is claimed.
