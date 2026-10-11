# Handoff <task-id>

Path: `coordination/handoffs/<task-id>.md`. Updated at the end of every step, before the step's commit. A fresh agent must be able to continue from this file alone.

## Status
active, blocked (with reason), review, or merged

## Done
- step k/N: <summary> (commit <sha>)

## Next
The exact next step, its first action, and any command to run.

## Gotchas

## Open questions

## Shared-file change requests
File, exact change, reason. The orchestrator applies these; the agent never edits shared files.

## Review notes
Reviewer appends here: verdict (approve or changes requested), findings with file:line.

## Audit notes
UI auditor appends here: screen, size class, finding, DESIGN_SYSTEM.md section violated.

## Recovery notes
Orchestrator only, for stale locks (GAUNTLET.md 5.8).
