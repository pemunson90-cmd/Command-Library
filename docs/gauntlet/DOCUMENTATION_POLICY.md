# Documentation policy

Status: active. This file defines how current project documentation stays small, current and resumable across GPT, Codex, Claude and Claude Code.

## Canonical live state

Normal resume should begin from only these live sources:

1. `coordination/state.json` - authoritative current checkpoint, stage, next task, next step, blockers and verification summary.
2. `coordination/ORCHESTRATOR.json` - authoritative lease/owner status.
3. `coordination/tasks/<state.next_task>.md` - authoritative bounded task scope, dependencies, execution steps and acceptance evidence.
4. `coordination/QUEUE.md` - runnable tasks and blocked alternatives only.

Read task-linked architecture, decisions, contracts, gate reports or research only when the current task names them or the work touches that area. Do not reread the whole project corpus on every resume.

## Mirrors and audit history

`coordination/handoffs/CURRENT.md` and `docs/gauntlet/STATE.md` are human-readable mirrors generated at checkpoint time. They are useful for quick inspection and recovery, but they do not outrank `coordination/state.json`.

`coordination/JOURNAL.md` and `coordination/events/*.jsonl` are append-only audit history. Read their recent entries only for takeover, mismatch investigation, recovery or provenance questions. They are not part of the normal resume path.

Accepted gate reports preserve detailed evidence. Live state should summarize and link to that evidence rather than copying full test narratives forward forever.

Historical GAUNTLET editions, superseded Flutter/Dart task cards and completed worker handoffs remain evidence and history. They are not active execution instructions unless the current task explicitly references them.

## Keep one fact in one authoritative place

Avoid repeating mutable facts across multiple active documents. Prefer links over copied paragraphs.

- Product direction and durable user rulings: `docs/gauntlet/DECISIONS.md`.
- Version-1 stage plan: `docs/gauntlet/V1_KOTLIN_EXECUTION_SPEC.md` and `docs/gauntlet/MILESTONES.md`.
- Current mutable execution state: `coordination/state.json`.
- Current ownership/lease: `coordination/ORCHESTRATOR.json` and active component locks.
- Detailed acceptance evidence: stage/gate report.
- Current bounded work: one task card.
- Public project overview: `README.md`, intentionally high level and not a live checkpoint ledger.

If a current fact changes, update its authoritative source and let the checkpoint helper refresh generated mirrors. Do not manually maintain several prose copies of the same state unless the helper requires it.

## Fast checkpoint policy

Checkpoint a coherent, commit-worthy work unit rather than every tiny edit. Publish earlier when ownership changes, shared schema/contracts change, a risky migration is about to begin, a meaningful partial result would otherwise be lost, or tool/context/usage limits make interruption likely.

A checkpoint must still be enough for another model to resume without chat history. The minimum useful handoff is:

- checkpoint ID and published head/base;
- current task and exact next action;
- files/components changed or owned;
- verification actually run and its result;
- blockers or unresolved review items, if any.

Omit empty boilerplate. Do not repeat architecture background that already exists in the task card or linked docs.

Use `scripts/checkpoint.py` after updating the task card and any real verification fields. It synchronizes machine state, lease status, CURRENT, STATE, queue header, journal and event log. Then run `scripts/check_continuity.py` and the script unit tests before publishing a checkpoint that changes coordination state.

## Review policy

Independent review is required where the active task or decisions require it, especially for architecture/public contract changes, source/license conclusions, database durability or migration semantics, security/credential handling, destructive behavior and final stage acceptance.

Routine isolated implementation does not need a separate prose review ceremony for every micro-step. It still needs tests and truthful evidence. Consolidate review at meaningful boundaries instead of generating review paperwork that does not reduce risk.

## Entry files

`README.md`, `AGENTS.md` and `CLAUDE.md` are entry points, not parallel protocols. They should point to `CONTINUE.md` and this policy rather than restating competing rules.

`CLAUDE-PARALLEL.md` describes only a currently active explicit parallel assignment. If none exists, it must say so and direct the model to the live queue/locks instead of preserving an obsolete assignment as active text.
