# ARCHITECT: storage

> Historical Dart-path/signature notice (D-20): This component contract is retained as product behavior and acceptance authority only. Its Dart paths/signatures are historical and ineligible for Android v1; use the active Kotlin registry, KOTLIN_CONTRACTS and KOTLIN_OWNERSHIP_MAP.

Gate3 contract draft under D17. Historical toolchain instructions are superseded by orch-kotlin-toolchain-01; K2 requires actual compile/runtime proof. This is not a built implementation.

## Purpose

Cancelable bounded SQLite executor/transaction/migration infrastructure and FTS5 capability probes.

## COVERAGE IDs served

N01, A06, A08, I02. Primary and cross-feature obligations in SPEC/COVERAGE/PARITY_ACCEPTANCE. Shell displays every component's required screen; service ownership never silently cuts UI. Registry maps every64 requirement and213 parity row to these contracts.

## Public interface

Pure Dart contract sketch; export packages/storage/lib/storage.dart. Shared DTOs/outcome/identity/cancel/clock live in core. Signatures finalized/compiled together during core/interface task; no client imports src internals.

```dart
abstract interface class Database {
 Future<Outcome<List<RowData>>> query(SqlRequest request, CancelToken cancel);
 Future<Outcome<T>> transaction<T>(Future<T> Function(DbTransaction tx) action, CancelToken cancel);
 Future<Outcome<DbCapabilities>> capabilities(CancelToken cancel);
}
```

Return typed failure/unknown, not bogus success. Queries return bounded Page; mutation expected revision rejects stale state. Long operations accept cancellation/progress and preserve committed state. Storage/network/native implementations behind ports; no UI objects in domain signatures.

## Data model

DbExecutor, transaction boundary, schema generation/version and migration evidence. Orchestrator owns central database/registry; feature packages own repositories and queries.

See DATA_MODEL for exact logical fields/identity and generation/revision/transaction semantics. Physical schema registry is shared/orchestrator-owned; feature repositories may define parameterized queries, never own global migration ordering. User and provider data never share destructive refresh lifecycles.

## Allowed dependencies

core. Only declared public exports/core contracts. No reverse dependency or runtime dynamic import defeating DAG. Pure domain logic remains platform-independent; platform adapters are tested separately.

## Owned paths

- packages/storage/**

Includes this package's lib/test/fixtures and its native-plugin files where applicable. Does not own root/apps/database registry/CI/config/coordination. Architecture document is edited only by assigned architect/reviewer instruction, not free permission for implementation writer. No other component may own this prefix.

## Shared-file change requests

Orchestrator adds package workspace/dependency pins, DI registration, routes/screens and database/registry migrations/index metadata as required by this component. Root/apps native launcher configuration and Actions workflows are shared. Worker requests exact file/change/reason in handoff, never edits shared paths. Interface changes affecting other packages require compatibility review before integration.

## Error and offline behavior

All local known data/state remains usable offline; online-only acquisition/provider operations expose typed unavailable and retain input/previous active state. Unknown quote/ID/rule/intent is not zero/legal/complete. Persist committed state atomically, surface failed save. Cancel doesn't undo already committed user changes; download/restore/model work stops safely. Preserve foreign raw identity without automatic language transfer. No fabricated fixture/source measurements.

## Performance budget

Apply PERFORMANCE P01-P12 to this component's involved workflows: DBp95<=150ms, UI-visible<=250ms, base RSS<=300MiB/import peak<=450MiB excluding loadedLLM only, artdecoded64/96MiB, import<=10min, lifecommit<=100ms, tools8/50rows/5s, cancel<=1s; narrower relevant target selected in task card. No irrelevant promise of network/model latency. Profile real corpus/device after implementation; budget relaxation needs recorded ruling.

## Tests and acceptance criteria

Real bundled SQLite FTS5 insert/MATCH; parameter safety, rollback/cancel/process failure, every migration up/down where supported, indexes/query plans/full corpus later.

Acceptance cases AC-N01, AC-A06, AC-A08, AC-I02 plus mapped parity PX obligations. Authentic fixtures sanitized/attributed/source-versioned; synthetic labeled. Every relevant migration/failure/cancel case required. Reviewer separate from author; UIphysical tests aren't replaced by goldens. No skipped checks claimed passing.

## Step plan

1. Implement executor and transaction lifecycle with real SQLite integration fixtures. Write only packages/storage/lib/** and matching packages/storage/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.
2. Wire reviewed registry migration/version contract and FTS5 probe. Write only packages/storage/lib/** and matching packages/storage/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.
3. Implement bounded transaction/cancel and generation-pointer activation primitives. Write only packages/storage/lib/** and matching packages/storage/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.
4. Run integrity/migration smoke on Android/Windows (Windows v2, D-19); independent review. Write only packages/storage/lib/** and matching packages/storage/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.

## Implementation prerequisite

Architecture independent audit accepted, public interfaces of dependencies actually imported by this scoped step merged (declare the exact subset in its task card; unused future services must not be imported or fabricated), verified toolchain/pins, named concrete task/lock/base and available test transport. Until then task is planned, not eligible. Never use a stub implementation to pretend a not-yet-built service exists. Stage report must distinguish contract review from runtime evidence.
