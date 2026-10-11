# ARCHITECT: core

Gate3 contract draft under D17. Required before component code; exact toolchain/dependency pin tuple still requires orch-toolchain-01. This is not a built implementation.

## Purpose

Pure Dart identity/value types, outcomes, cancellation/clock abstractions and immutable shared entity snapshots.

## COVERAGE IDs served

N01, N03. Primary and cross-feature obligations in SPEC/COVERAGE/PARITY_ACCEPTANCE. Shell displays every component's required screen; service ownership never silently cuts UI. Registry maps every64 requirement and213 parity row to these contracts.

## Public interface

Pure Dart contract sketch; export packages/core/lib/core.dart. Shared DTOs/outcome/identity/cancel/clock live in core. Signatures finalized/compiled together during core/interface task; no client imports src internals.

```dart
abstract interface class Clock { DateTime utcNow(); Duration monotonicNow(); }
abstract interface class CancelToken { bool get isCancelled; void throwIfCancelled(); }
abstract interface class IdSource { String nextId(); }
abstract interface class RandomSource { int nextInt(int upperBound); }
abstract interface class DecimalPolicy { DecimalAmount multiply(DecimalAmount amount, int quantity); }
// Outcome<T>, Page<T> and core entity snapshots are immutable exported value types.
```

Return typed failure/unknown, not bogus success. Queries return bounded Page; mutation expected revision rejects stale state. Long operations accept cancellation/progress and preserve committed state. Storage/network/native implementations behind ports; no UI objects in domain signatures.

## Data model

Source-qualified IDs, exact DecimalAmount/native unit, unknown versus absent, deck/game/catalog revisions, Clock/RandomSource and immutable record DTOs. Central contracts named in INTERFACES; no Flutter, SQLite or HTTP types.

See DATA_MODEL for exact logical fields/identity and generation/revision/transaction semantics. Physical schema registry is shared/orchestrator-owned; feature repositories may define parameterized queries, never own global migration ordering. User and provider data never share destructive refresh lifecycles.

## Allowed dependencies

none. Only declared public exports/core contracts. No reverse dependency or runtime dynamic import defeating DAG. Pure domain logic remains platform-independent; platform adapters are tested separately.

## Owned paths

- packages/core/**

Includes this package's lib/test/fixtures and its native-plugin files where applicable. Does not own root/apps/database registry/CI/config/coordination. Architecture document is edited only by assigned architect/reviewer instruction, not free permission for implementation writer. No other component may own this prefix.

## Shared-file change requests

Orchestrator adds package workspace/dependency pins, DI registration, routes/screens and database/registry migrations/index metadata as required by this component. Root/apps native launcher configuration and Actions workflows are shared. Worker requests exact file/change/reason in handoff, never edits shared paths. Interface changes affecting other packages require compatibility review before integration.

## Error and offline behavior

All local known data/state remains usable offline; online-only acquisition/provider operations expose typed unavailable and retain input/previous active state. Unknown quote/ID/rule/intent is not zero/legal/complete. Persist committed state atomically, surface failed save. Cancel doesn't undo already committed user changes; download/restore/model work stops safely. Preserve foreign raw identity without automatic language transfer. No fabricated fixture/source measurements.

## Performance budget

Apply PERFORMANCE P01-P12 to this component's involved workflows: DBp95<=150ms, UI-visible<=250ms, base RSS<=300MiB/import peak<=450MiB excluding loadedLLM only, artdecoded64/96MiB, import<=10min, lifecommit<=100ms, tools8/50rows/5s, cancel<=1s; narrower relevant target selected in task card. No irrelevant promise of network/model latency. Profile real corpus/device after implementation; budget relaxation needs recorded ruling.

## Tests and acceptance criteria

Immutable IDs/revisions/null semantics; exact decimal quantity math and TIX scale; cancellation and injected clock/RNG behavior. Pure-Dart dependency/import check.

Acceptance cases AC-N01, AC-N03 plus mapped parity PX obligations. Authentic fixtures sanitized/attributed/source-versioned; synthetic labeled. Every relevant migration/failure/cancel case required. Reviewer separate from author; UIphysical tests aren't replaced by goldens. No skipped checks claimed passing.

## Step plan

1. Create package/export/value and clock/cancel contracts with unit fixtures. Write only packages/core/lib/** and matching packages/core/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.
2. Define minimal catalog/deck/inventory/game DTOs and revision/provenance fields; test round trips. Write only packages/core/lib/** and matching packages/core/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.
3. Expand AI/transfer/variant DTOs only as downstream contract tasks need them; keep additive compatibility. Write only packages/core/lib/** and matching packages/core/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.
4. Independent contract review and public API smoke; publish dependency interface checkpoint. Write only packages/core/lib/** and matching packages/core/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.

## Implementation prerequisite

Architecture independent audit accepted, all dependency public interfaces merged, verified toolchain/pins, named concrete task/lock/base and available test transport. Until then task is planned, not eligible. Never use a stub implementation to pretend a not-yet-built service exists. Stage report must distinguish contract review from runtime evidence.
