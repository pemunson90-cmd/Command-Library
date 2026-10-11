# <task-id>

Path: `coordination/tasks/<task-id>.md`. Written by the orchestrator. The task card plus the files listed under Brief are everything the agent receives (GAUNTLET.md 5.7).

| Field | Value |
|---|---|
| Role | researcher, architect, implementer, test-writer, reviewer, or ui-auditor |
| Component | |
| Orchestrator | claude-code or codex |
| Model tier | strongest, standard, or fast (with reason if raised above the role default) |
| Branch | `agent/<task-id>` |
| Base commit | |
| Steps | ARCHITECT.md step plan items k to N |
| Status | queued, active, blocked, review, merged |

## Brief (files to read, nothing else)
- `docs/architecture/<component>/ARCHITECT.md`
- Interface files of dependencies:
- `docs/gauntlet/DESIGN_SYSTEM.md` (UI tasks only)

## Owned paths (exclusive, copied from ARCHITECT.md)

## Acceptance
- Build passes
- Tests pass
- Placeholder scan passes
- Reviewer approves (and ui-auditor for UI tasks)

## Output
- Handoff: `coordination/handoffs/<task-id>.md`
