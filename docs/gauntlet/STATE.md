# STATE

Resume pointer. Updated after every merge and every gate. Files beat conversation memory.

| Field | Value |
|---|---|
| Gate | 0 (Bootstrap): report filed; D-09 ruled; D-01, D-02, D-03, D-05, D-11 awaiting Pat |
| Milestone | none. No app code before Gate 4 |
| Integration branch | `main` (D-09) |
| Last good commit | 284069d (Gate 0 report). Later commits: last line of `coordination/JOURNAL.md` |
| Active tasks | none |
| Locks held | none |
| Merged ARCHITECT.md files | none |
| Max concurrent implementers | not set (D-08, Gate 3) |

## Next action

Pat starts a new session with only Command-Library attached and posts the remaining Gate 0 rulings plus push permission for `main` and `agent/*`. Then:
1. Record the rulings verbatim in DECISIONS.md.
2. Start Gate 1: write task cards `cc-research-01` to `cc-research-17`, launch researcher subagents in parallel batches (R-01 on the strongest model, the rest on Sonnet per D-10), then write RESEARCH_SUMMARY.md and `gates/GATE-1.md`.

## Orchestrators

| Orchestrator | Entry file | Task prefix | Status |
|---|---|---|---|
| Claude Code | `CLAUDE.md` | `cc-` | active |
| Codex | `AGENTS.md` | `cx-` | not present in repo (D-05 open) |
