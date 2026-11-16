## Owner directive — rapid prototyping, definition of done, and testing priority (2026-11-16)

**This directive applies to every agent, subagent, orchestrator, and reviewer. It takes precedence over older repository instructions requiring test-first development, routine exhaustive verification, perpetual testing, or test-count-based completion. Preserve existing capabilities and do not bypass concrete release/security requirements, but do not invent testing gates.**

**Rapid prototyping is Pat's preferred working method.** Prioritize building and completing a tangible, useful, owner-evaluable product over process, scaffolding, speculative infrastructure, paperwork, or validation for its own sake. A working prototype that delivers the requested experience is more valuable than a thoroughly tested fragment of an unfinished product.

**Before doing any task work, ask Pat what counts as finished for that task.** Establish the actual product-level acceptance target in Pat's terms before implementation, review campaigns, or planning machinery. Do not substitute an agent's assumptions, a test suite, a PR, or a milestone for Pat's definition of done. Once answered, carry out the agreed work without repeatedly reopening the same question; ask again when a genuinely new task lacks a definition or scope materially changes.

**Excessive testing is considered a failure, not a safety goal.** Product completion is more important than testing. Testing comes after there is a product worth testing; it is not an obligation to prove that every action an agent takes is functional. Do not default to test-driven development, per-edit tests, repetitive regressions, broad simulation campaigns, extra reviewers, or CI loops. Make the product first, then run the smallest proportionate checks necessary to resolve concrete risks and enable delivery. Stop checking when those questions are answered, and return to product work. Required high-consequence checks at a real release or integration boundary remain applicable, but must be tightly scoped and must never become an excuse to substitute verification activity for product progress.

**Report completion in product terms:** what is actually usable, what remains unfinished against Pat's definition, and only the verification that materially supports that assessment. A green test suite alone never means the product is finished.

# Command Library agent entrypoint

## Non-negotiable feature continuity (owner directive)

**If Pat did not explicitly authorize removing a working capability, it stays.** Existing working features, gameplay rules, card effects, interfaces, workflows, integrations, content, data, settings, accessibility, and tools survive fixes, refactors, rewrites, ports, engine replacements and architecture changes. Do not silently delete, stub, disable, simplify, postpone or leave them behind because a new implementation is cleaner or the task named only one feature. Lack of an explicit preservation list is **not** permission to drop anything.

**Before coding**, identify the actual most recent owner-accepted/installed usable baseline and the current authoritative development/integration branch; verify commit ancestry and inspect existing successful implementations, including playable prototypes, simulators and branches that the new work replaces. Neither `main` nor the newest-looking version number alone establishes the full feature set. For replacements or substantial refactors, account for each relevant existing capability and preserve behavior or explicitly approved improvements; check meaningful parity/regressions against the old working path. Recheck on the final integration head. If preservation and the requested change conflict, retain the old working functionality and seek Pat's explicit ruling on that **specific** loss; continue independent non-destructive work. Do not substitute extra paperwork or indiscriminate test campaigns for actual continuity checks. Apply updated guidance at the next safe work boundary without disrupting active writers.


## Main integration routing

This main checkout contains the accepted frozen foundation. Active development, state, lease and component assignments live on `agent/cx-thread-recovery-20261013`. Fetch that branch and read its entrypoint and live coordination before any write or assignment. This main routing snapshot and historical locks are not a takeover invitation; root retains exclusive active development and final merge authority. Newer recovery product work is not yet integrated, and Android v1 is incomplete.

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
