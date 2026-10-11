# Shared interface contract inventory

Public exports in registry.json are the only cross-package import boundary. No application code exists yet. Type names below are architectural contracts to define/compile in core before a dependent step; not a claim they are implemented.

Core exports Outcome<T> (success/typed error/unknown), Page<T>/PageRequest, typed source-qualified IDs (Card/Oracle/Rule/Deck/Game/Format), Revision, CancelToken, Clock, RandomSource, DecimalAmount/unit/precision, immutable entity snapshots. DATA_MODEL is field authority.

Required contract groups:
- Network: EndpointProfile, RequestSpec, ByteResponse/ByteChunk, Request/Http metadata and safeRetry/charged classification.
- Storage: RowData, SqlRequest (bound parameters), DbTransaction, DbCapabilities, migration/generation metadata.
- Vault/settings: SecretScope/SecretRef, SettingsSnapshot/Patch, DownloadIntent/ConsentRecord.
- Catalog/rules/query: CardRecord/PrintingRecord, CatalogSelection/StorageMode/JobProgress, RulesSource/RuleEntry/RulesQuery, QueryAst/SearchContext/Suggestion/CatalogSnapshot.
- Deck/collection: DeckSnapshot/Patch/Version, PackageAction/Preview, AnalysisPolicy/Result, CollectionQuery/Patch/Preview, OwnedLot/InventorySnapshot.
- Pricing/validation/brackets: QuoteSnapshot/Request, PricePolicy/AcquisitionPlan, OptimizationPolicy/Plan, FormatDefinition/ValidationReport, BracketData/Assessment/Target/IntentAnswers/ChangeConstraints.
- Recommendation/transfer: RecommendationRequest/Page/Context, CardChange/UpgradePlan, TransferInput/ImportProfile/Row/Preview, ExportRequest/ByteArtifact/RestorePreview and versioned interchange records.
- Game/inference: GameSnapshot/Action, VariantDefinition/Card/State, ModelSelection/Session, RuntimeProfile, GenerationRequest/TokenChunk.
- AI/UI: DeckContext/LabMode/Request/Event, PromptTemplate/Artifact, ProviderProfile/ModelPage/ValidatedReply, ScreenIntent/WindowGeometry/ViewState<T>/UiIntent.

Cross-package implementations consume these typed snapshots/ports; no arbitrary DB connection/raw provider response escapes into presentation. Core stores DTOs/ports, not all business reducers, source parsing or widgets. Providers/repositories may add internal types, but exporting a new shared type requires orchestrator registry and dependent contract review.

Core API smoke must compile each public signature against real type declarations before dependent code tasks become eligible. Contract tests verify revision/unknown/decimal/cancel and serialized schema. Public signature changes require additive versioning or reviewed migration/update of all consumers; no hidden dynamic dependency. Root composition registers real implementations only and never a service returning invented dummy success for an unfinished feature.
