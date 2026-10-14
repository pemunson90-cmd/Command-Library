# Shared continuation protocol

## Main integration routing

This main checkout contains the accepted frozen foundation. Active development, state, lease and component assignments live on `agent/cx-thread-recovery-20261013`. Fetch that branch and read its entrypoint and live coordination before any write or assignment. This main routing snapshot and historical locks are not a takeover invitation; root retains exclusive active development and final merge authority. Newer recovery product work is not yet integrated, and Android v1 is incomplete.

Applies to Codex, GPT, Claude and Claude Code. The repository must remain resumable without chat history, but normal resume should be fast and should not require rereading the whole project.

See `docs/gauntlet/DOCUMENTATION_POLICY.md` for documentation authority and duplication rules.

## What "continue" authorizes

Resume the exact committed queue pointer, perform reversible work inside the current reviewed product scope, verify it honestly and publish bounded progress. Any model family may replace a stopped prior orchestrator. One orchestrator lease-holder at a time. Parallel component writers require explicit disjoint ownership/locks.

Pat's recorded rulings in `docs/gauntlet/DECISIONS.md` override project documents. Routine technical implementation is delegated. Product scope changes, security/language-consent changes, destructive user-data behavior, new spending, public release and unresolved permission barriers still require an explicit ruling.

Never impersonate another author, invent approvals, report planned tests as run, silently drop requirements, or treat a stale file as stronger evidence than the current repository state.

## Fast resume path

For a normal clean resume:

1. Verify current `main` head.
2. Read `coordination/state.json`.
3. Read `coordination/ORCHESTRATOR.json`.
4. Read only `coordination/tasks/<state.next_task>.md` and its `Latest continuation delta` when present.
5. Read only the specific files, contracts, decisions or evidence named by that task step/delta.
6. If the lease is released, claim it before shared-file writes. If a fresh active lease exists, inspect/review only unless the owner has clearly stopped and the takeover procedure applies.
7. Execute the next coherent action, verify it as appropriate, update the smallest local continuation surface, and commit the action when it mutates the repository.

`coordination/QUEUE.md` is needed when choosing an alternate task, resolving a blocker or checking runnable work. `coordination/handoffs/CURRENT.md` and `docs/gauntlet/STATE.md` are generated human-readable mirrors. The journal/events are audit history. Do not read all of them on every clean resume.

## Per-action handoff rule

Every completed model, agent or subagent action must leave a minimal continuation delta so another actor can continue from that exact point without reconstructing the whole repository history.

For an orchestrator action, update the current task card. For a delegated worker, update that worker's task handoff or task card. The delta contains only what changed/was learned, affected branch/commit/files when applicable, verification actually run or `not_run`, and the exact next action.

Use a single replaceable `Latest continuation delta` section. Do not append a growing mini-journal to the task card. Git history provides provenance for previous deltas.

A local action delta does not require synchronizing every global coordination file. Global state is updated only when the actual global pointer, ownership, blocker, task/step, accepted evidence or other coordination state changes.

## Recovery or takeover path

Use the heavier recovery path only when state is inconsistent, work may be orphaned, a lease is stale, a branch is unclear, or a prior model stopped mid-step:

1. Compare checkpoint IDs and branch heads across `coordination/state.json`, `coordination/ORCHESTRATOR.json` and `coordination/handoffs/CURRENT.md`.
2. Inspect the named task branch, task card, active locks and the recent journal/events needed to explain the mismatch.
3. Preserve unpublished useful work on a recovery branch before replacing anything. Never reset, clean, rebase or force-push someone else's work.
4. An expired lease is not automatic proof that the writer is dead. Inspect its branch/head/handoff before takeover and record the reason.
5. Reconcile to one truthful next pointer, then resume the normal path.

## Ownership and execution

Main is the integration branch. Agent branches isolate work. Shared configuration, schema/DI/navigation/CI registries and coordination files belong to the orchestrator lease-holder. Component workers may modify only their locked/owned paths.

No production placeholder or fake-success adapter may satisfy a task. Unbuilt product features should remain absent rather than pretending to work. Existing public contracts and requirement tracking remain binding unless a recorded decision changes them.

Windows/Flutter is version 2 after Android v1 acceptance. Sync begins only after the Windows app runs and receives explicit authorization. Old Flutter/Dart version-1 instructions are historical unless a current task explicitly references them as evidence.

## Checkpoint policy

Do not confuse an action delta with a global checkpoint. Checkpoint when a coherent unit is complete or global coordination state changes: ownership, task/step, blockers, shared/public contracts, risky migration boundaries, accepted verification/review, or a useful partial result that could otherwise be lost.

A resumable checkpoint must contain enough information for another model to continue without chat history:

- checkpoint ID and published head/base;
- current task and exact next action;
- files/components changed or owned;
- verification actually run and its result;
- blockers or unresolved review items, if any.

Omit empty boilerplate and do not restate architecture background already linked by the task card.

After updating the task card and real verification fields, use `scripts/checkpoint.py` to synchronize machine state, lease status, CURRENT, STATE, queue header, journal and event log. Then run `scripts/check_continuity.py` and `python3 -m unittest discover -s scripts -p 'test_*.py'` before publishing a coordination-changing checkpoint.

## Review policy

Independent review is required when the current task/decision requires it, especially for architecture or public-contract changes, source/license conclusions, database durability or migration semantics, credentials/security, destructive behavior and final section acceptance.

Routine isolated implementation still needs tests and truthful evidence, but it does not require a separate review document for every micro-step. Consolidate review at meaningful risk boundaries and stage acceptance.

## Models and tools

Use actual installed capabilities, not stale provider role text. Use the weakest capable model for routine work and stronger review only where complexity/risk justifies it. Standalone source/API/terms/design research may use regular chat packets in `coordination/chat-handoffs/`; repository integration, builds/tests and byte-level verification stay with tooling that can actually perform them.

No essential instruction may live only in private memory, lost chat, an ephemeral tool session or an unpublished branch.
