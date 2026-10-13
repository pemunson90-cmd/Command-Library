# Command Library Claude entrypoint

Command Library is a Magic: The Gathering companion app. Android version 1 is Kotlin for phones, foldables and tablets. Windows is a separate Flutter version 2 after Android v1 is accepted. Sync is later.

Do not maintain a separate Claude execution protocol here. The active cross-model protocol is `CONTINUE.md`, and documentation authority is defined by `docs/gauntlet/DOCUMENTATION_POLICY.md`.

## Start here

For a clean resume:

1. Verify current `main` and the continuation branch named by `state.working_branch`; use that branch's live pointers and preserve dirty/unpublished work.
2. Read `coordination/state.json`.
3. Read `coordination/ORCHESTRATOR.json`.
4. Read the task card named by `state.next_task` and only the documents it links.
5. Follow `CONTINUE.md` for lease, ownership, checkpoint and recovery rules.

`coordination/handoffs/CURRENT.md`, `docs/gauntlet/STATE.md`, journal/events and historical GAUNTLET editions are recovery/audit material, not mandatory clean-resume reading.

## Hard invariants

- Pat's recorded rulings in `docs/gauntlet/DECISIONS.md` control conflicts.
- One orchestrator lease-holder at a time. Parallel Claude/other workers require explicit disjoint ownership and locks.
- No production placeholders, fake-success adapters or fabricated build/test/source claims.
- No secrets, API keys or signing material in git or logs.
- Public contract, schema, durability, security and source-license changes receive the review required by the active task.
- Kotlin Android v1 uses the current Kotlin ownership/contracts/registry documents. Old Dart/Flutter v1 instructions are historical.
- Shared coordination/configuration files belong to the orchestrator.
- Preserve other writers' branches and commits. No blind reset, clean, rebase or force-push.

Claude subagent definitions may exist in `.claude/agents/`, but only the orchestrator delegates and every worker remains bounded by the current task/lock. `CLAUDE-PARALLEL.md` is valid only when it explicitly says an assignment is active and that assignment agrees with the live queue/locks.

Use the weakest capable model for routine work and stronger review only where risk/complexity warrants it. No essential handoff information may exist only in chat memory.
