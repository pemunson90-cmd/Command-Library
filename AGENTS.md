# Command Library agent entrypoint

> **Historical continuation branch — do not resume its task directions.** Active unfinished work moved to [`agent/cx-native-20261013`](https://github.com/pemunson90-cmd/Command-Library/tree/agent/cx-native-20261013), published at `341b7194df68e285cf4bef159da4d4e66c4e34a5` when this redirect was written. Fetch/read that branch's latest `coordination/state.json`, lease and task before continuing; preserve dirty or unpublished work. This branch's code, machine pointers and old lease are retained historical evidence. See [HANDOFF-REDIRECT.md](HANDOFF-REDIRECT.md).

Android version 1 is Kotlin. Windows is a separate Flutter version 2 after Android v1 is accepted. Sync is later, after both offline apps run and explicit authorization is recorded.

Do not maintain a separate Codex protocol here. The active cross-model protocol is `CONTINUE.md`, and documentation authority is defined by `docs/gauntlet/DOCUMENTATION_POLICY.md`.

## Start here

For a clean resume:

1. Verify current `main`.
2. Read `coordination/state.json`.
3. Read `coordination/ORCHESTRATOR.json`.
4. Read the task card named by `state.next_task` and only the documents it links.
5. Follow `CONTINUE.md` for lease, ownership, checkpoint and recovery rules.

Use `coordination/handoffs/CURRENT.md`, `docs/gauntlet/STATE.md`, journal/events and historical GAUNTLET editions only when recovery, mismatch investigation or provenance requires them.

## Hard invariants

- Pat's recorded rulings in `docs/gauntlet/DECISIONS.md` control conflicts.
- One orchestrator lease-holder at a time; component writers require explicit disjoint locks/ownership.
- No production placeholders, fake-success adapters or fabricated build/test/source claims.
- No secrets, API keys or signing material in git or logs.
- Public contract, schema, durability, security and source-license changes receive the review required by the active task.
- Kotlin Android v1 uses the current Kotlin ownership/contracts/registry documents. Old Dart/Flutter v1 instructions are historical.
- Shared coordination/configuration files belong to the orchestrator.
- Preserve other writers' branches and commits. No blind reset, clean, rebase or force-push.

Use the weakest capable model for routine work and stronger review only where risk/complexity warrants it. No essential handoff information may exist only in chat memory.
