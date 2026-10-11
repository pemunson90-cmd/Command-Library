# STATE

Resume pointer. Updated after every merge and every gate. Files beat conversation memory.

| Field | Value |
|---|---|
| Gate | 0 (Bootstrap): report filed, awaiting Pat's rulings |
| Milestone | none. No app code before Gate 4 |
| Integration branch | `ccr-b0894b79-qzhxhw` (provisional until D-09; target is `main`) |
| Last good commit | see last line of `coordination/JOURNAL.md` |
| Active tasks | none |
| Locks held | none |
| Merged ARCHITECT.md files | none |
| Max concurrent implementers | not set (D-08, Gate 3) |

## Next action

Wait for Pat's rulings on `docs/gauntlet/gates/GATE-0.md`. Then:
1. Apply D-09 (move integration to `main` if approved).
2. Record rulings verbatim in DECISIONS.md.
3. Start Gate 1: write task cards `cc-research-01` to `cc-research-17`, launch researcher subagents in parallel batches, then write RESEARCH_SUMMARY.md and `gates/GATE-1.md`.

## Orchestrators

| Orchestrator | Entry file | Task prefix | Status |
|---|---|---|---|
| Claude Code | `CLAUDE.md` | `cc-` | active |
| Codex | `AGENTS.md` | `cx-` | not present in repo (D-05 open) |
