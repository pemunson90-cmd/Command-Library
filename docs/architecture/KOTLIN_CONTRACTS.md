# Kotlin contract translation

This guide translates the existing shared interface inventory and twenty component architecture contracts to Kotlin. `docs/gauntlet/DATA_MODEL.md` remains authoritative for fields, identity, provenance, and transaction rules. This document defines names and translation rules; it is not implementation or compile evidence.

## Shared Kotlin rules

- Preserve every component, port, and exported DTO name listed below. Use immutable `data class` value snapshots, sealed typed outcomes, `suspend` for one-shot asynchronous calls, and `Flow` for streams. Pure calculations remain ordinary functions.
- `Outcome<T>` distinguishes `Success<T>`, typed `Unavailable`, typed `Failure`, and `Unknown` with a reason/evidence where applicable. Unknown, unavailable, or failed data must never be encoded as success, empty-as-zero, legal, or complete. Keep absent distinct from unknown when the domain requires it.
- A `Page<T>` is bounded and carries items, an optional opaque continuation cursor, and the source/revision snapshot that produced it. `PageRequest` validates a documented maximum page size and carries an optional cursor; no unbounded list query is public.
- Long-running work runs in caller-owned structured coroutines and accepts cancellation through the coroutine context. Preserve the `CancelToken` public name for boundary/interoperability contracts; adapt it to coroutine cancellation at the boundary. Catch `CancellationException` only to clean up, then immediately rethrow it. Never turn cancellation into `Unavailable`, `Failure`, or success.
- Snapshots and emitted values are immutable. Mutations carrying `Revision expected` reject stale writes; each committed user edit advances its revision once. Provider/source generation, source date, fetched time, effective time, and provenance remain distinct.
- IDs remain source-qualified (`CardId`, `OracleId`, `RuleId`, `DeckId`, `GameId`, `FormatId`); preserve the producer's raw foreign identity and unresolved state. Never infer identity from localized name or silently transfer a foreign ID to another language.
- `DecimalAmount` carries exact value, native `DecimalUnit`, and precision/scale. Use integer minor units with explicit scale or exact decimal text; no binary floating point for money, budgets, or TIX. Unknown price is not zero.
- Core/domain modules contain no Android, Compose, SQLite, HTTP, or other platform types. Put those types and conversions in adapters; ports expose only core values.
- Settings default to English. Non-English data acquisition requires explicit, scope-specific `ConsentRecord`; revocation blocks future matching transfers. Restoring settings never implies download consent.

## Minimum complete core contract for K2

Implement and compile the complete shared core surface before any feature consumer is imported. At minimum it includes typed source-qualified IDs; a platform-independent UTC timestamp value plus monotonic `Duration` via `Clock`; `Revision`; `CancelToken`; `IdSource`; `RandomSource`; `DecimalAmount`/unit/precision and `DecimalPolicy`; immutable DTO snapshots; `Outcome<T>`; `Page<T>`; and `PageRequest`.

Required core value shapes (illustrative Kotlin syntax only):

```kotlin
data class Revision(val value: Long) // nonnegative; increment only on committed mutation
data class UtcTimestamp(val epochSeconds: Long, val nanoAdjustment: Int)
data class PageRequest(val limit: Int, val cursor: String? = null) // limit is bounded
data class Page<T>(val items: List<T>, val nextCursor: String?, val revision: Revision?)
sealed interface Outcome<out T> // Success, Unavailable, Failure, Unknown; no implicit fallback
```

`CardRecord` must be a useful immutable canonical OracleCard snapshot, not an ID/name placeholder. Preserve source-qualified `OracleId`, producer/source and catalog generation/revision provenance, original name and text, mana cost, nullable mana value, colors and color identity, types, nullable stats (including defense), keywords, layout, reserved status, normalized search text, and explicit multi-face data/identity. Preserve source-provided null/unknown distinctly from numeric zero and empty text. `PrintingRecord` separately preserves source-qualified printing identity, oracle identity, set, collector number as text, language, rarity, available finishes, image URI, and supplied links/vendor IDs. Exact logical semantics follow DATA_MODEL.

Core must also compile the shared contract groups in `INTERFACES.md`: `EndpointProfile`, `RequestSpec`, `ByteResponse`, `ByteChunk`, HTTP metadata/retry classification; `RowData`, `SqlRequest`, `DbTransaction`, `DbCapabilities`; `SecretScope`, `SecretRef`, `SettingsSnapshot`, `SettingsPatch`, `DownloadIntent`, `ConsentRecord`; and the catalog/rules/query, deck/collection, pricing/validation/bracket, recommendation/transfer, game/inference, and AI/UI snapshots and policy/request/result types. The component table preserves each named port and its public signature types. Do not create generic `Any`, platform-handle, or raw-map substitutes for missing domain types.

## Component exports and staged scope

Names are retained from each `ARCHITECT.md` public sketch plus the shared type inventory. This is a contract map, not authorization to implement future component behavior.

| Component | Public port(s) | Public DTO/type names to preserve |
|---|---|---|
| core | `Clock`, `CancelToken`, `IdSource`, `RandomSource`, `DecimalPolicy` | `Outcome`, `Page`, `PageRequest`, `Revision`, source-qualified IDs, `DecimalAmount`, `DecimalUnit`, immutable entity snapshots |
| network | `NetworkClient` | `EndpointProfile`, `RequestSpec`, `ByteResponse`, `ByteChunk`, `HttpMetadata`, `Request`, safe-retry/charged classification |
| storage | `Database` | `RowData`, `SqlRequest`, `DbTransaction`, `DbCapabilities`, migration/generation metadata |
| vault | `SecretVault` | `SecretScope`, `SecretRef` |
| settings | `SettingsRepository` | `SettingsSnapshot`, `SettingsPatch`, `DownloadIntent`, `ConsentRecord` |
| catalog | `CatalogRepository` | `CardRecord`, `PrintingRecord`, `CatalogSelection`, `StorageMode`, `JobProgress`, `CardId`, `OracleId` |
| rules | `RulesRepository` | `RulesSource`, `RuleEntry`, `RulesQuery`, `RuleId`, `Page`, `PageRequest` |
| search | `CardSearch` | `QueryAst`, `SearchContext`, `Suggestion`, `CatalogSnapshot`, `CardRecord`, `Page`, `PageRequest` |
| decks | `DeckRepository`, `DeckAnalysis` | `DeckSnapshot`, `DeckPatch`, `DeckVersion`, `PackageAction`, `PackagePreview`, `AnalysisPolicy`, `AnalysisResult`, `DeckId`, `Revision` |
| collection | `CollectionRepository` | `CollectionQuery`, `CollectionPatch`, `CollectionPreview`, `OwnedLot`, `InventorySnapshot`, `Revision` |
| pricing | `PricingService` | `QuoteSnapshot`, `QuoteRequest`, `PricePolicy`, `AcquisitionPlan`, `OptimizationPolicy`, `OptimizationPlan`, `DecimalAmount` |
| validation | `DeckValidator` | `FormatId`, `FormatDefinition`, `CatalogSnapshot`, `ValidationReport`, `Legality` |
| brackets | `BracketService` | `BracketData`, `BracketAssessment`, `BracketTarget`, `IntentAnswers`, `ChangeConstraints`, `DeckSnapshot` |
| recommendations | `RecommendationEngine` | `RecommendationRequest`, `RecommendationPage`, `RecommendationContext`, `CardChange`, `UpgradePlan` |
| transfer | `TransferService` | `TransferInput`, `ImportProfile`, `ImportRow`, `ImportPreview`, `ExportRequest`, `ByteArtifact`, `RestorePreview`, versioned interchange records |
| life | `GameService` | `GameId`, `GameSnapshot`, `GameAction`, `VariantDefinition`, `VariantCard`, `VariantState`, `Revision` |
| inference | `LocalInference` | `ModelSelection`, `ModelSession`, `RuntimeProfile`, `GenerationRequest`, `TokenChunk` |
| ai | `DeckLab` | `DeckContext`, `LabMode`, `LabRequest`, `LabEvent`, `PromptTemplate`, `PromptArtifact`, `ProviderProfile`, `ModelPage`, `ValidatedReply` |
| links | `CardLinks` | `ResolvedLink`, `LinkPreferences`, `LinkTokenValues`, `CardRecord`, `PrintingRecord` |
| shell | `NavigationHost`, `FeaturePresenter<T>` | `ScreenIntent`, `WindowGeometry`, `ViewState<T>`, `UiIntent` |

K2 starts with real local lookup/import/status capability ports and a real settings path, plus composition of those accepted implementations. K2 step1 must independently review and name the segregated local ports and concrete signatures. Retain the full aggregate `CatalogRepository` declaration and compile it against real DTOs, but do not register or instantiate a partial aggregate until all its required methods are implemented. The local adapter implements only its actual capability interfaces; consumers must not receive an aggregate filled with unsupported/fake-success methods. This segregation preserves every future aggregate method and its acceptance obligation, not a scope cut.

Local capabilities return truthful typed empty/unavailable/unknown results when local data is absent; never invent a card, remote lookup, consent, source status, or initialized catalog. Remote search/refresh, online transport, future services and their stubs remain unimplemented in K2. English-default status and actual catalog generation/source/language state come from persisted state.

## Consumer API conversion gate

Before any consumer imports core, translate the Dart-shaped APIs exactly once in the Kotlin core contract and compile the actual declarations plus every public port signature. Convert `Future<Outcome<T>>` to `suspend fun …: Outcome<T>`; `Stream<T>` to `Flow<T>`; `Stream<Outcome<T>>` to `Flow<Outcome<T>>`; synchronous pure `Outcome<T>` stays synchronous. Preserve nullability and typed status/revision/provenance. Replace `Future<void>` with `suspend fun …` returning `Unit`; retain a typed outcome wherever the source contract reports operation failure. Convert `CancelToken` boundaries to structured cancellation and prove cancellation propagates. Do not let consumers import Dart signatures, internal implementation packages, Android types, or an incomplete subset hidden behind stubs.

K2 acceptance requires hosted Kotlin compile/API smoke evidence for all twenty public contracts against real DTO declarations, plus meaningful checks for typed unknown/unavailable/error, cancellation rethrow, page bounds, revision staleness, provenance, foreign identity, and exact decimal handling. This guide provides no such test or compile evidence; until those checks pass and are reviewed, no consumer contract is accepted.

## Reviewed K3 additive acquisition surface

`AcquisitionContracts.kt` declares the separately reviewed `DownloadService` and caller-owned `DownloadRunner`, `DownloadPlan`, finite acquisition limits/states and `VerifiedArtifact` provenance. Acquisition completion is not installed-catalog success. These capabilities preserve the future aggregate ports; no partially implemented CatalogRepository is registered. Implementation and binding require the acquisition reviews, durable consent/job/file protocols and actual acceptance evidence tracked by the K3 task.

`core:common` supplies the platform-free `ConsentCoordinator` utility. Every settings consent writer and acquisition dispatch/commit path must share the application's instance. Read remains callable inside the guard; writers hold it until DataStore acknowledgment settles, including cancellation during an irreversible write. Hold only bounded checks/dispatch/commit handoffs, never transfers or callbacks to another consent-writing method. This utility changes no source permission or user-consent requirement.

## Life persistence serialization boundary

`core:model` declares `LifePayloadCodecPort` with twelve typed encode/decode operations for creation, snapshot, profile draft, event, archive and checkpoint. `data:storage` implements the bounded versioned codec; pure-JVM `feature:life` consumes the model port and `Database` for checked `PlayerProfileRepository` persistence. No platform types cross the port and decoding alone does not establish service graph or source authority. Storage tests may depend on feature/life to exercise the actual repository/codec/SQLite combination; this is a test-only reverse edge and does not alter the production registry DAG. No operational GameService/DI/UI registration follows from this boundary.

`RandomRequest.VariantZoneShuffle` and `RecordedRandomValue.VariantZoneShuffle` preserve exact versioned definition/action/zone/policy and before/after card-order multisets (<=10000, duplicates retained). New writes require installed internal authored shuffle authority before draws; historical replay verifies explicit recorded mathematical state/count consistency without asserting old source authenticity. CLIF3 adds version-gated tags and reads actual format1/2 fixtures; SQLschema6 unchanged. `feature:life.persistence` additionally exposes immutable `CustomVariantManifest`, `CustomVariantTransition`, `CustomVariantSelection` and `CustomVariantInsertion`, accepted by non-suspend `SqlLifeGameSessions.withCustomVariants(...):Outcome<SqlLifeGameSessions>`. The factory accepts CUSTOM only, validates explicit bounded execution configuration without calling supplied sources/database, and computes the pinned `command-library.custom-policy-v1` marker from strict canonical policy bytes (4MiB per manifest, maximum128 manifests). The unchanged plain constructor remains usable for games without installed custom execution. Exact full definition plus retained computed policy must match for new variant writes; callers must retain/reprovide the exact configuration after reopen. This is user-authored configuration, with no manifest parser/persistence/import/export/UI, named-source authenticity, public backend callbacks or GameService/DI binding. Full source display/provenance text and complete creation payload bounds remain actual persistence checks. Design/framing authority: `docs/superpowers/specs/2026-10-14-life-custom-manifest-factory-design.md`.


`feature:life.persistence.CalendarGameHistoryFilter.create(base,fromDay,throughDay,zone)` returns a retained explicit calendar-date/zone selection plus `GameHistoryFilter` query. Inclusive local dates become UTC `[from.atStartOfDay(zone),through.plusDays(1).atStartOfDay(zone))`, including DST/skipped-date semantics; null dates clear old bounds and non-date filters persist. No default-zone/source/clock sampling. Private constructor prevents inconsistent endpoints/query copies; invalid/inverted/unrepresentable dates return typed INVALID_INPUT. This public JavaTime API requires core-library desugaring in Androidmin23 consumers. Current app/storage packaging configure pinned desugar_jdk_libs2.1.5; future Android consumers must configure their actual test/lint/runtime paths. Design/evidence: docs/superpowers/specs/2026-10-14-life-calendar-history-adapter-design.md and docs/verification/m2-life/CALENDAR-HISTORY-LOCAL-20261014.md. UI binding and actual API23 runtime acceptance are separate.

`feature:life.persistence.SqlPersistedGameService` implements the complete existing persisted/legacy service ports through same-database checked sessions/readers. Public constructor and validated CUSTOM companion factory remain unbound to app DI. Serialized read performs no clock sampling/recovery/write; conditional undo checks expected revision inside the authoritative transaction. Typed observe preserves every non-success; legacy watch throws public LifeGameObservationException with the original typed status and propagates cancellation. No core signature, schema or codec change. Actual local950JVM evidence and remaining source/platform/UI obligations: docs/verification/m2-life/PERSISTED-SERVICE-LOCAL-20261014.md.

`feature:shell.ShellPorts` accepts the complete optional trailing `playerProfiles:PlayerProfileRepository?=null` capability. Its absence hides the Profiles destination. The scoped profile controller preserves loaded revisions and advanced fields, requires explicit stale reload/delete confirmation, uses actual paging cursors, and awaits repository success before closing edits. An unchanged blank legacy name remains valid on numeric-only edit. Global navigation leaves a pending scoped write running; closing a busy delete dialog dismisses only its modal and never promises rollback. This independent profile capability does not register `GameService` or relax named-variant/source readiness. Profile-library design/evidence: `docs/superpowers/specs/2026-10-14-life-profile-library-design.md` and `docs/verification/m2-life/PROFILE-LIBRARY-LOCAL-20261014.md`. The actual AppGraph now assembles SqlPlayerProfileRepository over its shared successful StorageDatabase open with LifePayloadCodec/SystemUtcClock/RandomUuidIdSource and caches the complete ShellPorts under the existing mutex. Independent exact-source review approves this binding; the actual app graph/Room CRUD host probe passes. Whole-project/APK/native/physical evidence is reported separately in the linked verification record.
