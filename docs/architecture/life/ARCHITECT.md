# ARCHITECT: life

> Historical Dart-path/signature notice (D-20): This component contract is retained as product behavior and acceptance authority only. Its Dart paths/signatures are historical and ineligible for Android v1; use the active Kotlin registry, KOTLIN_CONTRACTS and KOTLIN_OWNERSHIP_MAP.

Gate3 contract draft under D17. Historical toolchain instructions are superseded by orch-kotlin-toolchain-01; K2 requires actual compile/runtime proof. This is not a built implementation.

## Current Kotlin custom configuration boundary

The reviewed `SqlLifeGameSessions.withCustomVariants` factory composes immutable user-authored CUSTOM definitions, exact initial placement and bounded typed transitions with the real initializer, action engine, recorded shuffle authority and checked SQLite lifecycle. It computes a pinned canonical SHA256 execution-policy marker; full definition equality and retained policy equality prevent changed configuration from reinterpreting saved games. Configuration validation samples no clock/ID/random source and touches no database. Callers retain/reprovide exact manifests; import/parser/repository/UI remain required later. This adds no GameService/DI binding or official named source acceptance. Current API/framing: KOTLIN_CONTRACTS and docs/superpowers/specs/2026-10-14-life-custom-manifest-factory-design.md. Runtime evidence is in docs/verification/m2-life/CUSTOM-FACTORY-LOCAL-20261014.md.

## Purpose

Fully offline1-6 seat event-sourced life/counters/designations/commander/timing/variant/results state.

## COVERAGE IDs served

G01, G02, G03, G04, G05, G06, G07, G08, G09, G10, G11, G12, G13. Primary and cross-feature obligations in SPEC/COVERAGE/PARITY_ACCEPTANCE. Shell displays every component's required screen; service ownership never silently cuts UI. Registry maps every64 requirement and213 parity row to these contracts.

## Public interface

Pure Dart contract sketch; export packages/life/lib/life.dart. Shared DTOs/outcome/identity/cancel/clock live in core. Signatures finalized/compiled together during core/interface task; no client imports src internals.

```dart
abstract interface class GameService {
 Future<Outcome<GameSnapshot>> resume(GameId id, CancelToken cancel);
 Future<Outcome<GameSnapshot>> dispatch(GameId id, GameAction action, Revision expected, CancelToken cancel);
 Future<Outcome<GameSnapshot>> undo(GameId id, CancelToken cancel);
 Stream<GameSnapshot> watch(GameId id);
}
```

Return typed failure/unknown, not bogus success. Queries return bounded Page; mutation expected revision rejects stale state. Long operations accept cancellation/progress and preserve committed state. Storage/network/native implementations behind ports; no UI objects in domain signatures.

## Data model

Game/Seat/CommanderSlot/Counter/Event/Snapshot/Result, clock and variant definitions/cards/state; source rule versions, unique result and undo lineage.

See DATA_MODEL for exact logical fields/identity and generation/revision/transaction semantics. Physical schema registry is shared/orchestrator-owned; feature repositories may define parameterized queries, never own global migration ordering. User and provider data never share destructive refresh lifecycles.

## Allowed dependencies

core, rules, storage. Only declared public exports/core contracts. No reverse dependency or runtime dynamic import defeating DAG. Pure domain logic remains platform-independent; platform adapters are tested separately.

## Owned paths

- packages/life/**

Includes this package's lib/test/fixtures and its native-plugin files where applicable. Does not own root/apps/database registry/CI/config/coordination. Architecture document is edited only by assigned architect/reviewer instruction, not free permission for implementation writer. No other component may own this prefix.

## Shared-file change requests

Orchestrator adds package workspace/dependency pins, DI registration, routes/screens and database/registry migrations/index metadata as required by this component. Root/apps native launcher configuration and Actions workflows are shared. Worker requests exact file/change/reason in handoff, never edits shared paths. Interface changes affecting other packages require compatibility review before integration.

## Error and offline behavior

All local known data/state remains usable offline; online-only acquisition/provider operations expose typed unavailable and retain input/previous active state. Unknown quote/ID/rule/intent is not zero/legal/complete. Persist committed state atomically, surface failed save. Cancel doesn't undo already committed user changes; download/restore/model work stops safely. Preserve foreign raw identity without automatic language transfer. No fabricated fixture/source measurements.

## Performance budget

Apply PERFORMANCE P01-P12 to this component's involved workflows: DBp95<=150ms, UI-visible<=250ms, base RSS<=300MiB/import peak<=450MiB excluding loadedLLM only, artdecoded64/96MiB, import<=10min, lifecommit<=100ms, tools8/50rows/5s, cancel<=1s; narrower relevant target selected in task card. No irrelevant promise of network/model latency. Profile real corpus/device after implementation; budget relaxation needs recorded ruling.

## Tests and acceptance criteria

Every baseline counter/designation and separate partners, grouped hold/damage-life/undo,10k dice batch/seat RNG, clock/turn restart, loss rationale/override and unique results; each named variant authentic offline setup/action/undo/resume; profile/game snapshot ports.

Acceptance cases AC-G01, AC-G02, AC-G03, AC-G04, AC-G05, AC-G06, AC-G07, AC-G08, AC-G09, AC-G10, AC-G11, AC-G12, AC-G13 plus mapped parity PX obligations. Authentic fixtures sanitized/attributed/source-versioned; synthetic labeled. Every relevant migration/failure/cancel case required. Reviewer separate from author; UIphysical tests aren't replaced by goldens. No skipped checks claimed passing.

## Step plan

1. Implement pure reducer/actions/seat identities and property/state fixtures. Write only packages/life/lib/** and matching packages/life/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.
2. Implement transactional event/snapshot persistence/autosave/restart/undo. Write only packages/life/lib/** and matching packages/life/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.
3. Implement counters/damage/designations/random/timers/results as separately committed tested action families. Write only packages/life/lib/** and matching packages/life/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.
4. Implement versioned variant managers/countdown/chess/alerts and artwork/deck references; profile offline durability and review. Write only packages/life/lib/** and matching packages/life/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.

## Implementation prerequisite

Architecture independent audit accepted, public interfaces of dependencies actually imported by this scoped step merged (declare the exact subset in its task card; unused future services must not be imported or fabricated), verified toolchain/pins, named concrete task/lock/base and available test transport. Until then task is planned, not eligible. Never use a stub implementation to pretend a not-yet-built service exists. Stage report must distinguish contract review from runtime evidence.
