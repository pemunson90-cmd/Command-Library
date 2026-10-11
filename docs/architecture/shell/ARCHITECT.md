# ARCHITECT: shell

Gate3 contract draft under D17. Required before component code; exact toolchain/dependency pin tuple still requires orch-toolchain-01. This is not a built implementation.

## Purpose

Flutter presentation/navigation/composition adapters;25 screens,5 widths/postures, reader/task UI and onlybuilt destinations.

## COVERAGE IDs served

J01, J02, J03, J04, N01, N03. Primary and cross-feature obligations in SPEC/COVERAGE/PARITY_ACCEPTANCE. Shell displays every component's required screen; service ownership never silently cuts UI. Registry maps every64 requirement and213 parity row to these contracts.

## Public interface

Pure Dart contract sketch; export packages/shell/lib/shell.dart. Shared DTOs/outcome/identity/cancel/clock live in core. Signatures finalized/compiled together during core/interface task; no client imports src internals.

```dart
abstract interface class NavigationHost {
 Future<void> open(ScreenIntent intent);
 void applyWindow(WindowGeometry geometry);
}
abstract interface class FeaturePresenter<T> { Stream<ViewState<T>> watch(); Future<void> dispatch(UiIntent action, CancelToken cancel); }
```

Return typed failure/unknown, not bogus success. Queries return bounded Page; mutation expected revision rejects stale state. Long operations accept cancellation/progress and preserve committed state. Storage/network/native implementations behind ports; no UI objects in domain signatures.

## Data model

UI state keyed by domain IDs/revisions, draft recovery, safe region/pane state and no service logic in widgets.

See DATA_MODEL for exact logical fields/identity and generation/revision/transaction semantics. Physical schema registry is shared/orchestrator-owned; feature repositories may define parameterized queries, never own global migration ordering. User and provider data never share destructive refresh lifecycles.

## Allowed dependencies

core, catalog, rules, search, decks, collection, pricing, validation, brackets, recommendations, transfer, life, ai, inference, settings, links. Only declared public exports/core contracts. No reverse dependency or runtime dynamic import defeating DAG. Pure domain logic remains platform-independent; platform adapters are tested separately.

## Owned paths

- packages/shell/**

Includes this package's lib/test/fixtures and its native-plugin files where applicable. Does not own root/apps/database registry/CI/config/coordination. Architecture document is edited only by assigned architect/reviewer instruction, not free permission for implementation writer. No other component may own this prefix.

## Shared-file change requests

Orchestrator adds package workspace/dependency pins, DI registration, routes/screens and database/registry migrations/index metadata as required by this component. Root/apps native launcher configuration and Actions workflows are shared. Worker requests exact file/change/reason in handoff, never edits shared paths. Interface changes affecting other packages require compatibility review before integration.

## Error and offline behavior

All local known data/state remains usable offline; online-only acquisition/provider operations expose typed unavailable and retain input/previous active state. Unknown quote/ID/rule/intent is not zero/legal/complete. Persist committed state atomically, surface failed save. Cancel doesn't undo already committed user changes; download/restore/model work stops safely. Preserve foreign raw identity without automatic language transfer. No fabricated fixture/source measurements.

## Performance budget

Apply PERFORMANCE P01-P12 to this component's involved workflows: DBp95<=150ms, UI-visible<=250ms, base RSS<=300MiB/import peak<=450MiB excluding loadedLLM only, artdecoded64/96MiB, import<=10min, lifecommit<=100ms, tools8/50rows/5s, cancel<=1s; narrower relevant target selected in task card. No irrelevant promise of network/model latency. Profile real corpus/device after implementation; budget relaxation needs recorded ruling.

## Tests and acceptance criteria

S01-S25 real task smoke/goldens allstates/width boundaries/compactheight/book/tabletop, keyboard/drag equivalents,200% text, actualTalkBack (Narrator v2, D-19), no loss across resize/stream/live game, staticcontrast+artRAM/fullperf.

Acceptance cases AC-J01, AC-J02, AC-J03, AC-J04, AC-N01, AC-N03 plus mapped parity PX obligations. Authentic fixtures sanitized/attributed/source-versioned; synthetic labeled. Every relevant migration/failure/cancel case required. Reviewer separate from author; UIphysical tests aren't replaced by goldens. No skipped checks claimed passing.

## Step plan

1. Build adaptive tokens/navigation/safe-region shell with only actual M0 route and truthful errorstate. Write only packages/shell/lib/** and matching packages/shell/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.
2. Implement catalog/rules/settings screens after accepted repositories, including keyboard/reader tests. Write only packages/shell/lib/** and matching packages/shell/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.
3. Implement life/deck/collection/analysis/recommendation/AI/transfer UI per milestone in one-screen or one-flow bounded tasks. Write only packages/shell/lib/** and matching packages/shell/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.
4. Audit all screens/P01-P12/physicalreaders/fold evidence; no fake screenshots or empty destinations. Write only packages/shell/lib/** and matching packages/shell/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.

## Implementation prerequisite

Architecture independent audit accepted, public interfaces of dependencies actually imported by this scoped step merged (declare the exact subset in its task card; unused future services must not be imported or fabricated), verified toolchain/pins, named concrete task/lock/base and available test transport. Until then task is planned, not eligible. Never use a stub implementation to pretend a not-yet-built service exists. Stage report must distinguish contract review from runtime evidence.

M0 consumes only core/catalog/settings and the real CatalogRepository.lookupName local query. M1 adds search/rules after acceptance. Later feature interfaces in the dependency envelope are prerequisites only when their stage imports them; no AI/inference/other future implementation is constructed in M0. Root composition follows this staged subset.
