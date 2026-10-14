# Shared root ruling access implementation plan

> Execute inline under executing-plans. D-17 delegates routine technical design; D-28 batches verification. Root remains sole implementation writer. No additional human approval is required for this already-authorized K3 wiring.

Goal: expose the real ruling acquisition/import and independent offline reader through the application graph while retaining one lazy process acquisition owner and recovery boundary.

Spec/design: reviewed source boundary in `2026-10-14-rulings-source-admission.md`, public CatalogRulings/CatalogRulingsImport contracts and the active K3 task. No product/security/consent policy changes. This unit binds capabilities; the actual ruling UI follows separately.

## Design decision

Reuse AppEnglishCatalogAccess as the existing process owner, adding RulingsCatalogAccess with distinct `openRulings` to avoid return-type-only overloads. Both methods enter one mutex and initialize the same private complete Ready session (services, English facade, ruling facade), recovering once before publishing either capability. Independent Rulings owners would recover each other's active workers; adding ruling operations to an English-named public facade would mix contracts. Keep the existing English API unchanged.

RulingsCatalogDownloads exposes the real shared DownloadService/DownloadRunner, fixed discovery request, metadata-job-ID-only file request and verified job-ID-only import. It does not accept caller URLs, bytes, receipts or candidates and never grants consent or runs a request merely by opening/planning.

Expose a pure `AndroidCatalogServices.localRulings(database): CatalogRulings` constructor. AppGraph binds it directly, so offline reads do not initialize acquisition, scan/recover its jobs or create transport. ShellPorts receives optional complete `rulings` and `rulingsDownloads` capabilities. AppGraph creates one AppEnglishCatalogAccess and supplies that SAME object to both download interfaces. Absence remains absence for unbound/test graphs; no fake success.

## Files and execution

- [ ] Add `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/RulingsCatalogAccess.kt`: `RulingsCatalogAccess.openRulings(CancelToken): Outcome<RulingsCatalogDownloads>`, complete session methods `discoveryRequest(String,Boolean)`, `fileRequest(EntityId,String,Boolean,CancelToken)`, `install(EntityId,(JobProgress)->Unit,CancelToken)`.
- [ ] Extend the existing catalog factory with the pure offline reader constructor. Extend ShellPorts with typed optional complete capabilities; no schema or source-policy changes.
- [ ] Refactor AppEnglishCatalogAccess cached value to private Ready. One initialization, recovery, cancellation/failed-init disposal and final close path serves both methods. Build facades only over the SAME factory. Preserve existing English verified-input/import semantics.
- [ ] Bind the single process owner and offline reader in AppGraph.
- [ ] Expand existing shared host/device EnglishCatalogAccessCases: concurrent opens across both families share exact services; opening/reading/planning grants no consent or wire; opening the other family preserves a live/orphan state; newly created owner recovers once; failed/cancelled initialization and closed access apply to both. Assert actual graph offline ports and both lazy capabilities exist.
- [ ] Run one focused app/root gate with native counterpart compile after the batch. Required independent architecture/ownership review at this root-wiring boundary. Reuse prior source-security evidence; no repeated full suite absent a new concern.
- [ ] Update the task delta/checkpoint with exact evidence and next UI action. Continue into actual download/ruling presentation rather than ending at the checkpoint.

## Review focus

Concurrent mixed-family opens, cancellation after factory creation/recovery, failed creation retried through another family, re-opening the second family while a worker exists, and closing one owner must preserve exactly one shared recovery/transport lifetime. Existing host directory bridges remain test-only; production UID/mode guards stay intact. UI, device runtime, publisher TLS/full-feed, physical Fold7 and full-v1 delivery are not inferred from capability registration.
