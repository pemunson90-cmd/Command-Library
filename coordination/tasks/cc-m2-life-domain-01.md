# Claude: isolated life domain implementation

Task: cc-m2-life-domain-01. Parent: orch-kotlin-k3-offline-data-01. Status: assigned by Codex at Pat's explicit request. This prepares M2; it does not close M2, K3 or full-app acceptance.

Use branch `agent/cc-m2-life-domain-01` in a separate worktree. Its prepared commit is `9ce1a488902d18b1a670ecef08c024466c841249`; the life JVM module and settings inclusion already exist there. Merge this documentation assignment branch into your worktree before coding. Do not take over the orchestrator lease or resume the completed rules-parser assignment.

Read AGENTS.md, CONTINUE.md, this task, docs/architecture/life/ARCHITECT.md, docs/architecture/KOTLIN_CONTRACTS.md, docs/architecture/KOTLIN_OWNERSHIP_MAP.md, the life requirements in docs/gauntlet/SPEC.md and DATA_MODEL.md, and actual core/model GameContracts.kt and Values.kt. Follow current Kotlin contracts over historical Dart examples.

## Exclusive ownership

- android-app/feature/life/src/main/kotlin/com/architectpsg/commandlibrary/feature/life/domain/**
- android-app/feature/life/src/test/kotlin/com/architectpsg/commandlibrary/feature/life/domain/**
- coordination/handoffs/cc-m2-life-domain-01.md

All core contracts, Gradle/configuration, app, UI/shell, network, catalog, rules, storage/schema, CI and shared coordination files belong to Codex. Request any necessary changes in your own handoff; do not edit those files. Preserve other branches and commits. No main merges, force pushes or additional agents. Network and storage workers are active in separate ownership areas.

## Concrete work

Implement immutable, pure life domain logic using existing core types. Do not register a partial GameService or add production placeholders.

1. Counter reductions: adjustments, bounds and overflow validation, hiding without losing values, and distinct commander slots for partner commanders and their victims. Use explicit counter-role IDs, not display names. Optional commander damage/life deduction must produce one atomic validated result. Invalid operations leave the original snapshot intact. Report loss candidates without pretending elimination has been persisted.
2. Seat clock calculations: explicit injected time, active-seat-only charging, pause/switch, zero transitions and alerts without duplicate firing, and explicit undo policy. Use monotonic time within a session. On restart explicitly pause/re-anchor while preserving durable balances; never reuse another process's monotonic origin. Wall-clock changes must not corrupt elapsed time.
3. Random tools: injected RandomSource, validated dice count 1..10000 and sides, Long sum, coin, shuffle and seat selection from explicitly supplied eligible IDs. No fixed production RNG and no invented eliminated-seat field.

If a public type is missing, document the exact proposed contract addition and finish independent supported work. Private helper types are allowed within owned paths. No persistence, UI, acquisition, source fetching or synthetic success adapters.

## Verification and return

Add meaningful deterministic tests for atomic invalid operations, bounds/overflow, hidden values, partner identity, clock pause/switch/zero/restart/undo and wall-clock changes, plus random bounds/count/sums/eligible IDs. Fixtures must be synthetic.

Run `cd android-app && bash gradlew --no-daemon --console=plain :core:model:test :feature:life:test`. If environment bootstrap prevents execution, report the precise limitation and request hosted verification; never report an unrun check as passed. Commit and push coherent code/test steps on your branch. Maintain your owned handoff with latest commit, actual checks, missing contract requests and exact next action. Return at most ten lines with branch, commit and evidence. Codex handles independent review, integration and complete-app acceptance.
