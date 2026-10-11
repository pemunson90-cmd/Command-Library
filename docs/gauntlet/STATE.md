# STATE

Resume pointer. Updated after every merge and every gate. Files beat conversation memory.

| Field | Value |
|---|---|
| Gate | 1 (Research): in progress |
| Milestone | none. No app code before Gate 4 |
| Integration branch | `main` (D-09). This session also pushes the same commits to `ccr-d1e9ac59-b90cv2` |
| Last good commit | b1c922e (Gate 0 rulings). Later commits: last line of `coordination/JOURNAL.md` |
| Active tasks | `cc-research-01` to `cc-research-17`; per-card status in `coordination/tasks/` (queued, active, filed) |
| Locks held | none (research needs no locks, GAUNTLET.md 5.3) |
| Merged ARCHITECT.md files | none |
| Max concurrent implementers | not set (D-08, Gate 3) |

## Gate 1 run plan

- Researchers run on Sonnet in a rolling window of 6 (D-10). R-01 runs on Opus, reason in its card.
- Order: R-02 and R-11 to R-15 first. R-01 launches once R-11 to R-15 are all filed, because it scores the stacks with their evidence. The rest fill free slots in number order.
- A researcher writes only its own research file in the main checkout. The orchestrator checks it (template sections, sources, no em dashes), commits it as `[cc-research-xx step 1/1] ...`, pushes to `main` and the session branch, sets the card to `filed`, and appends a JOURNAL line.

## Next action

Resume: a card marked `active` is not finished, even if its research file exists. Partial files are committed as `[gate-1] WIP checkpoint` whenever the orchestrator ends a turn, so a fresh session finds them on `main`. Relaunch the researcher with the partial file in place and tell it to complete it against the card's Acceptance list. Launch queued cards per the run plan. When all 17 are filed: write RESEARCH_SUMMARY.md (one ruling per topic, parity matrices from R-05, R-06, R-07), update DECISIONS.md (D-04, D-06, D-07 recommendations and any new decisions), write `gates/GATE-1.md`, update this file, commit, push, message Pat, stop.

D-03 ruled: new key for this app. Creation steps come from R-15, before M0.

## Orchestrators

| Orchestrator | Entry file | Task prefix | Status |
|---|---|---|---|
| Claude Code | `CLAUDE.md` | `cc-` | active, sole orchestrator through Gate 3 (D-05) |
| Codex | `AGENTS.md` | `cx-` | not present in repo; idle through Gate 3 (D-05) |
