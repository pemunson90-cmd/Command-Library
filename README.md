# Command Library

Personal-first, offline-first Magic: The Gathering companion for Android phones, foldables and tablets. Android version 1 is Kotlin. Windows is a separate Flutter version 2 after Android v1 is accepted, and Android/Windows sync is deferred until both offline apps run. English is the default dataset; non-English downloads require explicit user approval.

## Current status

K2, the Kotlin Android foundation, is accepted. The repository now contains a real Android `:app` with a Compose shell, persistent Room/SQLite storage, DataStore settings, user-selected local catalog import, local status/search behavior and a hosted debug APK that passed the accepted K2 Android matrix. This is a runnable foundation, not the finished Command Library product.

The foundation also provides local English rules TXT import, persistent rules search and reference navigation. The complete Android app remains unfinished. D-24 requires completing all retained functionality, operational in-app downloads, professional design and canonical signing before finished-app delivery. K3 full data/search/downloads is active, followed by the existing M2–M9 plan.

The [full feature audit](docs/verification/full-v1-audit/FEATURE-AUDIT.md) maps all 64 requirements, 213 parity rows and 25 screens. Existing debug artifacts are internal verification evidence, not the finished app. For exact live status and next action, use [coordination/state.json](coordination/state.json); the stage plan is [MILESTONES](docs/gauntlet/MILESTONES.md).

## Continue from any model

Codex, GPT, Claude and Claude Code all use the same continuation path. Chat history is optional.

Normal resume is intentionally small:

1. Verify current `main` and the published continuation branch named by `state.working_branch`. Current unfinished work is on `agent/cx-native-20261013`; use its live pointers, preserving any dirty checkout or unpublished commits.
2. Read [`CONTINUE.md`](CONTINUE.md) if unfamiliar with the protocol.
3. Read [`coordination/state.json`](coordination/state.json), [`coordination/ORCHESTRATOR.json`](coordination/ORCHESTRATOR.json), and the task card named by `state.next_task`.
4. Read only the decisions, contracts, architecture or gate evidence linked by that task.
5. Claim the lease before shared writes, complete coherent bounded work units, run minimum sufficient verification, and publish resumable checkpoints while continuing the goal. Stop only for missing research or a material blocking question.

[`coordination/handoffs/CURRENT.md`](coordination/handoffs/CURRENT.md), [`docs/gauntlet/STATE.md`](docs/gauntlet/STATE.md), the journal and event logs are mirrors/audit history. They are primarily for human inspection, mismatch recovery and takeover investigation, not mandatory rereads every session.

The documentation authority and efficiency rules are in [`docs/gauntlet/DOCUMENTATION_POLICY.md`](docs/gauntlet/DOCUMENTATION_POLICY.md). The runnable queue is [`coordination/QUEUE.md`](coordination/QUEUE.md). Durable product rulings are [`docs/gauntlet/DECISIONS.md`](docs/gauntlet/DECISIONS.md).

## Execution rules that matter

Pat's recorded rulings control conflicts. Never claim an unrun build, test, device check or source result. Keep secrets and signing material out of git and logs. Do not introduce fake production adapters or placeholder success paths. Shared state has one orchestrator lease-holder at a time, with component locks only when parallel writers are actually active.

Independent review is used where risk justifies it: architecture/public contracts, source-license conclusions, database durability or migration semantics, credentials/security, destructive behavior and final section acceptance. Routine isolated work still needs tests, but not duplicate review paperwork for every small edit.

GitHub is the versioning and handoff source of record. Run compilation/JVM/real SQLite checks locally when available; use hosted execution only for device checks this workspace cannot run. Routine verification targets modern Android for Galaxy Z Fold7, with historical compatibility lanes optional under D-27; actual Fold7 acceptance remains required. Pat does not need a development machine. Unknown evidence stays unknown rather than becoming a pass.

Tool-specific entry files are [`AGENTS.md`](AGENTS.md) and [`CLAUDE.md`](CLAUDE.md). They point back to the same protocol instead of maintaining competing copies.
