# Command Library

Magic: The Gathering companion app for Android (phones, foldables, tablets); Windows desktop is version 2 (D-19). Built through the gated protocol in `docs/gauntlet/GAUNTLET.md`. Pat is the product owner. His rulings in `docs/gauntlet/DECISIONS.md` override every other document.

## Every session starts here

Run the resume protocol (GAUNTLET.md section 5.6) before anything else:

1. This file.
2. `docs/gauntlet/STATE.md`: current gate, milestone, integration branch, next action.
3. Open items in `docs/gauntlet/DECISIONS.md`.
4. `coordination/locks/`.
5. Active task cards in `coordination/tasks/` and their handoffs in `coordination/handoffs/`.
6. Last 20 lines of `coordination/JOURNAL.md`.

Verify branch heads match the handoffs. Continue from each handoff's "Next" field. Never redo a step whose commit exists. Files beat conversation memory every time.

## Hard rules

- **Gates.** At every gate: write `docs/gauntlet/gates/GATE-<n>.md`, update STATE.md, commit, push, send Pat a short message, then STOP until he rules.
- **ARCHITECT.md before code** (5.1). No code in a component until `docs/architecture/<component>/ARCHITECT.md` is merged and listed in STATE.md.
- **Zero placeholders.** No `TODO`, `FIXME`, `XXX`, placeholder text, `not implemented`, `NotImplementedError`, Kotlin `TODO()`, empty click handlers, lorem ipsum, or stub returns. Unbuilt features are absent from the UI, never stubbed. The only exceptions are exact file and line entries in `docs/gauntlet/IMPOSSIBLE.md`.
- **Checkpoint every step** (5.5): code and tests for the step, handoff updated, commit `[<task-id> step k/N] <summary>`, push. Uncommitted work at the end of a step is a protocol violation.
- **Shared files belong to the orchestrator only:** build config, version catalog, module/settings registry, DI or composition root, navigation graph, CI workflows, DB schema registry. Subagents request changes in their handoff.
- **Locks.** One file per component in `coordination/locks/`. Owned paths are writable only by the lock holder. Never rewrite, rebase, force-push, or delete another orchestrator's commits, branches, or locks.
- **Task IDs.** `cc-<component>-<nn>` for Claude Code, `cx-<component>-<nn>` for Codex.
- **Secrets.** None in the repo. API keys and signing material never get logged or committed.
- **Truth.** Never claim a build or test passed without the run log. Never invent an API, field, file format, or library capability.
- **Style** for every file and message: no em dashes, no filler, no flattery.

## Subagents

Definitions live in `.claude/agents/`, generated from the tool-neutral briefs in `coordination/agents/`. The neutral briefs are the source of truth; keep both in sync. Only the orchestrator delegates. Verified tooling facts and the worktree procedure: `docs/gauntlet/research/R-00-tooling.md`.

Model tiering (Pat, D-10): strongest model for architect and reviewer; Sonnet or Haiku for everything else unless a task card says otherwise.

## Codex

If `AGENTS.md` exists, it belongs to the Codex orchestrator. Leave it intact and keep protocol statements in both entry files consistent.

## Current cross-model continuation override (D-17)

Read README.md and CONTINUE.md first, then STATE.md, coordination/state.json and coordination/handoffs/CURRENT.md. The latest user mandate supersedes earlier Codex-only and mandatory-chat-stop technical gates. Any model family may take the next sequential turn, holding the single orchestrator lease. Continue only the current queued step, checkpoint early, publish/verify notes and leave an exact next pointer. Preserve original gauntlet/product/security requirements; never pretend a source/device/build test ran. No extra conversation context is required when repository access is available.
