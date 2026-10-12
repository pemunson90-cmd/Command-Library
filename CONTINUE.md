# Shared continuation protocol

Applies to Codex, GPT, Claude and Claude Code. The repository must remain resumable without chat history, but normal resume should be fast and should not require rereading the whole project.

See `docs/gauntlet/DOCUMENTATION_POLICY.md` for documentation authority and duplication rules.

## What "continue" authorizes

Resume the exact committed queue pointer, perform reversible work inside the current reviewed product scope, verify it honestly and publish a bounded checkpoint. Any model family may replace a stopped prior orchestrator. One orchestrator lease-holder at a time. Parallel component writers require explicit disjoint ownership/locks.

Pat's recorded rulings in `docs/gauntlet/DECISIONS.md` override project documents. Routine technical implementation is delegated. Product scope changes, security/language-consent changes, destructive user-data behavior, new spending, public release and unresolved permission barriers still require an explicit ruling.

Never impersonate another author, invent approvals, report planned tests as run, silently drop requirements, or treat a stale file as stronger evidence than the current repository state.

## Fast resume path

For a normal clean resume:

1. Verify current `main` head.
2. Read `coordination/state.json`.
3. Read `coordination/ORCHESTRATOR.json`.
4. Read only `coordination/tasks/<state.next_task>.md` and the specific documents it names.
5. If the lease is released, claim it before shared-file writes. If a fresh active lease exists, inspect/review only unless the owner has clearly stopped and the takeover procedure applies.
6. Execute a coherent commit-worthy work unit, run the task-required checks, update the task card, then checkpoint and publish.

`coordination/QUEUE.md` is needed when choosing an alternate task, resolving a blocker or checking runnable work. `coordination/handoffs/CURRENT.md` and `docs/gauntlet/STATE.md` are generated human-readable mirrors. The journal/events are audit history. Do not read all of them on every clean resume.

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

Checkpoint meaningful work, not every tiny edit. A checkpoint is warranted when a coherent unit is complete, ownership changes, a shared/public contract changes, a risky migration is about to begin, a useful partial result would otherwise be lost, or tool/context/usage limits make interruption likely.

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
