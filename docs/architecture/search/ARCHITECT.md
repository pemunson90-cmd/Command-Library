# ARCHITECT: search

Gate3 contract draft under D17. Required before component code; exact toolchain/dependency pin tuple still requires orch-toolchain-01. This is not a built implementation.

## Purpose

Typed local query AST, held-field filters, Oracle/rules FTS and explicit online execution.

## COVERAGE IDs served

A07, A08. Primary and cross-feature obligations in SPEC/COVERAGE/PARITY_ACCEPTANCE. Shell displays every component's required screen; service ownership never silently cuts UI. Registry maps every64 requirement and213 parity row to these contracts.

## Public interface

Pure Dart contract sketch; export packages/search/lib/search.dart. Shared DTOs/outcome/identity/cancel/clock live in core. Signatures finalized/compiled together during core/interface task; no client imports src internals.

```dart
abstract interface class CardSearch {
 Outcome<QueryAst> parse(String input);
 Future<Outcome<Page<CardRecord>>> local(QueryAst query, SearchContext context, PageRequest page, CancelToken cancel);
 Future<Outcome<List<Suggestion>>> suggest(String input, SearchContext context, CancelToken cancel);
 Future<Outcome<Page<CardRecord>>> online(String input, PageRequest page, CancelToken cancel);
}
```

Return typed failure/unknown, not bogus success. Queries return bounded Page; mutation expected revision rejects stale state. Long operations accept cancellation/progress and preserve committed state. Storage/network/native implementations behind ports; no UI objects in domain signatures.

## Data model

QueryAst/TypedFilter/QueryDiagnostic/SearchResult with corpus revision and offline/online provenance; null stats never0.

See DATA_MODEL for exact logical fields/identity and generation/revision/transaction semantics. Physical schema registry is shared/orchestrator-owned; feature repositories may define parameterized queries, never own global migration ordering. User and provider data never share destructive refresh lifecycles.

## Allowed dependencies

core, catalog, rules, storage. Only declared public exports/core contracts. No reverse dependency or runtime dynamic import defeating DAG. Pure domain logic remains platform-independent; platform adapters are tested separately.

## Owned paths

- packages/search/**

Includes this package's lib/test/fixtures and its native-plugin files where applicable. Does not own root/apps/database registry/CI/config/coordination. Architecture document is edited only by assigned architect/reviewer instruction, not free permission for implementation writer. No other component may own this prefix.

## Shared-file change requests

Orchestrator adds package workspace/dependency pins, DI registration, routes/screens and database/registry migrations/index metadata as required by this component. Root/apps native launcher configuration and Actions workflows are shared. Worker requests exact file/change/reason in handoff, never edits shared paths. Interface changes affecting other packages require compatibility review before integration.

## Error and offline behavior

All local known data/state remains usable offline; online-only acquisition/provider operations expose typed unavailable and retain input/previous active state. Unknown quote/ID/rule/intent is not zero/legal/complete. Persist committed state atomically, surface failed save. Cancel doesn't undo already committed user changes; download/restore/model work stops safely. Preserve foreign raw identity without automatic language transfer. No fabricated fixture/source measurements.

## Performance budget

Apply PERFORMANCE P01-P12 to this component's involved workflows: DBp95<=150ms, UI-visible<=250ms, base RSS<=300MiB/import peak<=450MiB excluding loadedLLM only, artdecoded64/96MiB, import<=10min, lifecommit<=100ms, tools8/50rows/5s, cancel<=1s; narrower relevant target selected in task card. No irrelevant promise of network/model latency. Profile real corpus/device after implementation; budget relaxation needs recorded ruling.

## Tests and acceptance criteria

50 pinned queries/Unicode/face semantics/aliases/quotes/grouping/negation/unknown numeric and prices, exact expected IDs, stale result cancel, P02-P04.

Acceptance cases AC-A07, AC-A08 plus mapped parity PX obligations. Authentic fixtures sanitized/attributed/source-versioned; synthetic labeled. Every relevant migration/failure/cancel case required. Reviewer separate from author; UIphysical tests aren't replaced by goldens. No skipped checks claimed passing.

## Step plan

1. Implement tokenizer/AST/error positions and typed operator semantics with golden domain queries. Write only packages/search/lib/** and matching packages/search/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.
2. Implement indexed SQL/FTS translation and context filters/pagination. Write only packages/search/lib/** and matching packages/search/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.
3. Implement autocomplete/stale cancellation and separately labeled online path. Write only packages/search/lib/** and matching packages/search/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.
4. Profile actual full corpus; independent query correctness review. Write only packages/search/lib/** and matching packages/search/test/**/fixtures as applicable; run named AC cases in hosted Actions or appropriate pure-Dart/native harness; update task/handoff and publish bounded checkpoint. Split this step into <=20minute substeps before starting if larger than one run.

## Implementation prerequisite

Architecture independent audit accepted, public interfaces of dependencies actually imported by this scoped step merged (declare the exact subset in its task card; unused future services must not be imported or fabricated), verified toolchain/pins, named concrete task/lock/base and available test transport. Until then task is planned, not eligible. Never use a stub implementation to pretend a not-yet-built service exists. Stage report must distinguish contract review from runtime evidence.

Search.online delegates to CatalogRepository.remoteSearch through the declared catalog dependency; caller supplies an approved selection/consent context at construction. Search never imports network or constructs an HTTP client. No consent means typed consent-required, never an implicit optional-language download.
