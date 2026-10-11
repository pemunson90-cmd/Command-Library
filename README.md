# Command Library

Personal-first, offline-first Magic companion for Android phones, foldables and tablets; Windows is version 2 (D-19). Flutter; Full English by default; extra-language downloads require user approval. Repository: https://github.com/pemunson90-cmd/Command-Library, integration branch main.

## Continue from any model

If Pat says **continue**, start here. Codex, GPT, Claude and Claude Code use the same files and take sequential turns. No chat history is required.

1. Read [CONTINUE.md](CONTINUE.md), [current state](docs/gauntlet/STATE.md), [machine state](coordination/state.json) and [current handoff](coordination/handoffs/CURRENT.md).
2. Read only the next task card named there, its component architecture, relevant decisions and dependency contracts.
3. Verify current GitHub main and local checkout/snapshot; claim the single orchestrator lease before changing shared files.
4. Complete the next eligible bounded step, verify it, update task/handoff/state/journal, commit and push. Print the checkpoint link.
5. Leave the next exact action and stop at a clean checkpoint after a reasonable work chunk. The next model repeats these steps.

[Execution queue](coordination/QUEUE.md) · [Authoritative decisions](docs/gauntlet/DECISIONS.md) · [Stage plan](docs/gauntlet/MILESTONES.md) · [Spec](docs/gauntlet/SPEC.md) · [Coverage](docs/gauntlet/COVERAGE.md)

## Current facts

Gate 2 specification independently reviewed; source/runtime/device checks remain unperformed. Current continuation work is architecture, CI design and recovery infrastructure; see state for the live pointer, never this summary alone. No application build or usable binary is claimed.

User's latest continuation mandate supersedes old Codex-only and mandatory-chat-at-every-technical-gate instructions. Routine reviewed stages may advance under [D-17](docs/gauntlet/DECISIONS.md); material product changes, destructive actions, new costs and unresolved actual permission barriers still need a recorded decision. Previously proposed performance numbers remain targets, not measured results.

## Tool entry points

[AGENTS.md](AGENTS.md) for Codex/agents, [CLAUDE.md](CLAUDE.md) for Claude Code, this README for general GPT/Claude. All point to the same continuation policy. Historical GAUNTLET editions and prior reports remain intact; latest user decisions control conflicts.

GitHub is the durable source. A local snapshot or downloadable handoff is convenience, not a separate branch of truth. A read-only model can audit and prepare a patch, but must label unpublished work and cannot claim a push it did not perform. Repository access is needed to update shared checkpoints.

## Security and deliveries

Never commit/log signing sheets, keys or credentials. Existing ANDROID_SIGNING secret and permanent certificate are described without values in DECISIONS. Build/test/package through GitHub Actions; Pat need not own a development machine. Verified install/device evidence must be recorded honestly; unknown is never a pass.
