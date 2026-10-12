# Command Library

Personal-first, offline-first Magic: The Gathering companion for Android phones, foldables and tablets. Android version 1 is Kotlin. Windows is a separate Flutter version 2 after Android v1 is accepted, and Android/Windows sync is deferred until both offline apps run. English is the default dataset; non-English downloads require explicit user approval.

## Current status

K2, the Kotlin Android foundation, is accepted. The repository now contains a real Android `:app` with a Compose shell, persistent Room/SQLite storage, DataStore settings, user-selected local catalog import, local status/search behavior and a hosted debug APK that passed the accepted K2 Android matrix. This is a runnable foundation, not the finished Commander Library product.

A tested functional Android build now also provides local English rules TXT import, persistent offline rules search and reference navigation. K3 full-corpus acceptance remains open; Pat prioritizes getting this build working on his Fold7 before broader features and polish. It adds the full offline English catalog/rules acquisition or import path, durable consent/source state, atomic corpus replacement and bounded local search. Later Android stages add life tracking, decks/import, collection/pricing, analysis/validation, recommendations, AI, images/polish/performance and final hardening/signing. Windows remains version 2 and sync remains later.

For exact live status, evidence and the next action, use [`coordination/state.json`](coordination/state.json). K2 acceptance evidence is in [`docs/gauntlet/gates/K2-FINAL-ACCEPTANCE.md`](docs/gauntlet/gates/K2-FINAL-ACCEPTANCE.md). The Android stage plan is in [`docs/gauntlet/MILESTONES.md`](docs/gauntlet/MILESTONES.md).

## Install on Fold7

Download the [APK bundle](https://github.com/pemunson90-cmd/Command-Library/actions/runs/38309643911/artifacts/11774097672), extract `app-debug.apk`, and open it on the phone. Use Catalog to import an English card JSON array and Rules to import an English rules TXT. Each local file is limited to8MiB; the catalog also caps10000printing records. Search uses the installed data offline.

[Build evidence and physical-device next step](docs/verification/k3-gap-execution/FUNCTIONAL-BUILD.md).

## Continue from any model

Codex, GPT, Claude and Claude Code all use the same continuation path. Chat history is optional.

Normal resume is intentionally small:

1. Verify current `main`.
2. Read [`CONTINUE.md`](CONTINUE.md) if unfamiliar with the protocol.
3. Read [`coordination/state.json`](coordination/state.json), [`coordination/ORCHESTRATOR.json`](coordination/ORCHESTRATOR.json), and the task card named by `state.next_task`.
4. Read only the decisions, contracts, architecture or gate evidence linked by that task.
5. Claim the lease before shared writes, complete a coherent bounded work unit, run the required verification, then publish a resumable checkpoint.

[`coordination/handoffs/CURRENT.md`](coordination/handoffs/CURRENT.md), [`docs/gauntlet/STATE.md`](docs/gauntlet/STATE.md), the journal and event logs are mirrors/audit history. They are primarily for human inspection, mismatch recovery and takeover investigation, not mandatory rereads every session.

The documentation authority and efficiency rules are in [`docs/gauntlet/DOCUMENTATION_POLICY.md`](docs/gauntlet/DOCUMENTATION_POLICY.md). The runnable queue is [`coordination/QUEUE.md`](coordination/QUEUE.md). Durable product rulings are [`docs/gauntlet/DECISIONS.md`](docs/gauntlet/DECISIONS.md).

## Execution rules that matter

Pat's recorded rulings control conflicts. Never claim an unrun build, test, device check or source result. Keep secrets and signing material out of git and logs. Do not introduce fake production adapters or placeholder success paths. Shared state has one orchestrator lease-holder at a time, with component locks only when parallel writers are actually active.

Independent review is used where risk justifies it: architecture/public contracts, source-license conclusions, database durability or migration semantics, credentials/security, destructive behavior and final section acceptance. Routine isolated work still needs tests, but not duplicate review paperwork for every small edit.

GitHub is the source of record. Build/test/package through GitHub Actions where required; Pat does not need a development machine. Unknown evidence stays unknown rather than becoming a pass.

Tool-specific entry files are [`AGENTS.md`](AGENTS.md) and [`CLAUDE.md`](CLAUDE.md). They point back to the same protocol instead of maintaining competing copies.
