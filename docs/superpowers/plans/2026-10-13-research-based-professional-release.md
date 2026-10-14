# Research-Based Professional Android Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Preserve Pat's established routing: smallest capable workers, bounded independent review, maximum three component writers. This is a master release plan with a detailed first execution batch; later subsystems receive separate bounded plans before implementation.

**Approval:** Pat approved execution on2026-10-13, with D26 moving on-device AI engines and dependent model management to v1.1; they cannot block functional v1 release.

**Goal:** Complete every retained Android-v1 feature as a cohesive, professional application on Pat's Fold7, reusing suitable open-source components and delivering only after canonical signing and actual device verification.

**Architecture:** Keep the accepted Kotlin/Compose/core-port/Room3 architecture and reviewed counter, clock, acquisition and bounded-import logic. Adopt compatible libraries for commodity capabilities, adapt licensed presentation patterns into one app-owned design system, and finish complete persisted user journeys. External applications are references rather than replacement architectures.

**Tech Stack:** Existing Gradle9.6.0, AGP9.4.1, Kotlin2.2.10, KSP2.3.12, Room3 3.0.3, SQLite2.7.1, DataStore1.2.1, Compose BOM2026.09.00, coroutines1.10.2, OkHttp5.5.0, JVM17. Coil/CSV/native-inference versions are candidates until compatibility trials select exact stable pins.

**Spec:** `docs/gauntlet/SPEC.md`, `PARITY_ACCEPTANCE.md`, `SCREENS.md`, `DESIGN_SYSTEM.md`, `DATA_MODEL.md`, `DECISIONS.md`, `PERFORMANCE.md`, `MILESTONES.md`, `V1_KOTLIN_EXECUTION_SPEC.md`; current Kotlin contracts and ownership map. Research evidence: `docs/verification/reuse-research/RECOMMENDATIONS.json`. Life decisions: `docs/verification/m2-life/CONTRACT-DISPOSITIONS.md`.

## Global Constraints

- Preserve the64-requirement/194-retained-parity/25-screen inventory and19 previously approved skips as traceability. D26 additionally assigns H03/S25 and on-device-only clauses to v1.1; they are approved deferrals, not implemented v1 features. G14 profile/game transport remains required.
- Android v1 first; Windows v2 and later sync remain deferred. Fold7 is primary; older-device performance is secondary under Pat's ruling. Do not lower existing minSdk23 or silently cut Android input/adaptive requirements.
- No production placeholders, fake-success adapters, inert release controls or invented source/test/device evidence. Synthetic fixtures belong to tests/previews and cannot appear as real user data.
- Every required catalog/rules/rulings/art/variant/bracket/enrichment/language resource has an operational in-app download/update path at its consumer's acceptance. Optional means user-enabled, not omitted.
- English default and explicit language/resource consent remain binding. Scryfall Default Cards includes original-language fallbacks; no English-only approval is inferred for that mixed file.
- Retain license/copyright/NOTICE obligations for reused code. Artwork, trademarks and model-weight rights are separate. No GPL/MPL code copy without a concrete license-fit decision.
- Coil loads local/managed artifacts or a reviewed guarded fetch path. Installing a default network loader must not bypass source/consent policy.
- Root owns core contracts, database/schema/migrations, DI/navigation, shared build/CI and coordination. Workers get disjoint locked files/worktrees and exact accepted dependency interfaces.
- UI tokens remain the normative DESIGN_SYSTEM defaults: compact gutter16dp, minimum Android target48dp, large text200% reflow, hinge8dp safety area, four/eight spacing scale; dark canvas#101718/accent#70D9BC, light canvas#F6F5F1/accent#166B5C. Do not change mandatory tokens without reconciling the design authority.
- Canonical certificate SHA256: E2:D8:F5:0D:A6:01:48:CE:E8:55:BF:C6:F3:01:D4:FE:38:E9:E3:DE:28:10:C6:05:83:30:16:7F:B4:F5:77:E7. Existing debug artifacts do not satisfy it.

## Review Focus

1. Fold/unfold, rotation, backgrounding and process death preserve selected cards, drafts, game state and committed history: Tasks2/3/4/6.
2. Network loss, consent revocation, disk exhaustion and interrupted import keep old installed data readable and never activate partial content: Tasks3/4/5.
3. Offline packs survive ordinary cache eviction; explicit removal affects only the exact consent's downloaded resources and preserves user identities: Tasks1/4/5.
4. Clock checkpoints/undo/redo, prevented losses and finish/reopen never refund elapsed time or duplicate results/alerts: Task6, using D25 and action-time provenance.
5. Imported/provider/model data is malformed, ambiguous, too large, stale or unsupported: Task1 and the owning later transfer/search/AI plans must test bounded failure and actionable recovery, rather than infer valid/zero/success.

## Research decisions

| Source | Intended use | Decision boundary |
|---|---|---|
| Coil3, Apache-2.0 | Image decoding/display and bounded ordinary cache | Trial exact compatible stable version; durable packs remain separately indexed and retained. |
| kotlin-csv, Apache-2.0 | Streaming CSV syntax | Trial bounded parsing; app-owned vendor mappings/unknown-row resolution/loss previews remain required. |
| TvManiac, NIA, Jetcaster/Jetsnack, Apache-2.0 | Visual hierarchy, components, state handling and adaptive panes | Adapt selected code/patterns with notices; retain current architecture/toolchain. Mock domain behavior and third-party media are excluded. |
| OneTwo/SpellCounter, MIT | Seat/timer/lifecycle/profile behavior comparisons | Close requirement gaps; preserve reviewed counter/clock/RNG logic and add durable event service. |
| llama.cpp, MIT | v1.1 Kotlin/JNI GGUF runtime candidate | Deferred by D26; no v1 build, dependency trial or performance gate. |
| LiteRT-LM, Apache-2.0 | v1.1 alternate supported model/runtime path | Deferred by D26; no v1 format/runtime selection gate. |
| OpenAI-Kotlin, MIT | Provider streaming/API reference or compatible client | Verify required dialect/API coverage, transport/consent and cancellation before adding a second HTTP stack. |
| ManaVault/MTG Desktop Companion/Forge/Cockatrice | Deck/import/collection/format behavior research | Use researched formats/ideas; copying covered source requires its actual license obligations. |
| Fetch/Ketch/OkDownload | Transfer implementation comparison | No engine replacement is scheduled: current consent/provenance/recovery semantics must not be lost. |

Alternative whole-app forks introduce platform/toolchain/license changes and still lack the full spec. Continuing custom implementation for commodity image/CSV/native inference capabilities creates avoidable work. Targeted reuse is the chosen approach, subject to trial results.

## Execution routing and efficiency

- Root: preserve lease/continuation, accept shared interfaces and schema, choose source dispositions, integrate reviewed work, maintain feature acceptance and final release evidence.
- Luna medium: narrow source/license/format research and mechanical comparisons. Reuse the research results; refresh only changed or missing facts.
- Sol medium: isolated visual components, straightforward format adapters and ordinary UI flow work against accepted ports.
- Sol high: transactional persistence, recovery, complex source/format/runtime adapters and game service work.
- Independent Sol high review for bounded implementation; Astra high for cross-component architecture, schema/durability/security/destructive-data boundaries and final acceptance when required. Escalate from concrete difficulty, not habit.
- Maximum3 component writers, preferably data/platform, game service and UI. Start parallel work only after the exact shared interfaces compile and receive required review; otherwise use the waiting slot for independent tests/research.
- Use failing regression tests for behavioral changes, focused source/module checks during work, then a single coherent hosted Android matrix at meaningful integration boundaries. Proposed root CI adjustment: lightweight/focused worker checks, full matrix for integration/release; preserve explicitly required exact-head gates. Review that CI change before applying it.
- Give each worker only its task, contracts, owned files and relevant evidence; no full-history fork or extra review swarm. Preserve useful evidence when its actual source tree is unchanged.
- Status after each checkpoint: completed user journeys, current task, next task, actual blocker. No invented percentages/delivery dates. Track feature acceptance, not number of commits/tests.

## First execution batch

Tasks1/2 prepare reuse and visual identity. Task3 and Task6 are independent after their own shared-contract reviews. UI bindings follow their real services. No partial APK is offered as the completed release.

### Task1: Select compatible reusable components

**Files:** modify candidate-worktree `android-app/gradle/libs.versions.toml` and only the module build files needed for each isolated trial; create `docs/verification/reuse-research/PROBE-DECISIONS.json`. Trial tests: `android-app/feature/shell/src/androidTest/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/ImageLoadingCompatibilityTest.kt`, `android-app/feature/transfer/src/test/kotlin/com/architectpsg/commandlibrary/feature/transfer/CsvReaderCompatibilityTest.kt`; root first registers the real transfer module if needed. These paths use existing `com/architectpsg/commandlibrary/feature/<component>` package prefixes.

**Interfaces:** consumes current toolchain pins and researched license/source references; produces a decision entry per candidate with exact artifact/version/sourceSHA/license, actual commands/results, native libraries, fit/rejection reason and intended module. These are compatibility trials, not new app service contracts.

- [ ] Check stable release metadata/licenses/dependency requirements; record precise candidate pins and required notices. Never use a moving branch as a production dependency.
- [ ] Test image decode success/failure/cancel using a controlled test image, ordinary cache eviction and a separate protected-pack file. Test CSV quoted comma/newline, UTF8, truncated quote, oversized field/row and cancellation with expected retained row identity. On-device/native-model trials are excluded from this v1 batch by D26.
- [ ] Run candidate focused checks and actual Android resolution/assembly; if native code is present verify ABIs/16KiB packaging. Document unrun physical checks separately.
- [ ] Accept only candidates compatible with approved architecture/pins and full requirements. Reject or select an older compatible stable release when evidence requires it; do not downgrade the app's core stack to fit a sample.
- [ ] Commit only accepted dependency changes, tests and decision evidence; remove throwaway trials from the integration diff and retain reproducible failure evidence.

### Task2: Establish the professional visual system

**Files:** modify shell `ui/ShellTheme.kt`, `ui/Components.kt`, `ui/CardsScreen.kt`, `ui/CardDetail.kt`; create focused card/action/progress/empty-state components under the same UI package. Preview future live-table composition in test/preview-only sources until Task6 supplies real game ports. Tests: `android-app/feature/shell/src/androidTest/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/VisualSystemAcceptanceTest.kt`. Root owns `ShellRoot.kt` and route registration.

**Interfaces:** consumes existing `ShellPorts`, card records/controllers, DESIGN_SYSTEM and S02/S03/S21 screen states; produces one consistent presentation system and representative compact/unfolded renders, not mock service registrations.

- [ ] Map reference patterns to card search, card detail and live table: imagery, readable type hierarchy, intentional spacing/actions and native accessible behavior. Keep full-card scans legible and attribution visible; do not copy TV/movie artwork or obscure card copyright with scrims.
- [ ] Test compact/unfolded sizes, selected-card continuity, dark/light themes, 200% text, 48dp targets, hinge8dp safe area, TalkBack labels and offline/empty/loading/failure states. Use labeled fixtures only in tests/previews.
- [ ] Implement/adapt components within the existing tokens. Foldable panes follow actual window/posture, preserving actions and state; professional styling extends through dialogs/settings/downloads.
- [ ] Render actual screenshots for the three representative screens at compact and expanded widths. Present the visual checkpoint for Pat's assessment before applying it throughout all25 screens; do not infer aesthetic approval from test success.
- [ ] Commit visual components/tests/notices and evidence. Production routes continue to use only real functional ports.

### Task3: Bind Android transfer execution to the reviewed engine

**Files:** existing catalog `LocalAcquisition.kt`, `AndroidArtifactFiles.kt`, `DurableArtifactFiles.kt`; root-created `AndroidCatalogServices.kt`; app `di/AppGraph.kt`, `AndroidManifest.xml`; root-created Android job/platform adapters. Existing network `OkHttpNetworkClient.kt`, `WireDispatchGuard.kt`. Tests: catalog `AndroidAcquisitionBindingTest.kt`, app `DownloadLifecycleTest.kt`.

**Interfaces:** consumes `NetworkClient`, `SettingsRepository`, shared `ConsentCoordinator`, `Database`, `Clock`, `IdSource`; produces actual `DownloadService`/`DownloadRunner` ports. Root reviews the public factory and private artifact-provider/lifetime design before construction; internal acquisition types are not exposed as guessed core contracts. Every physical enqueue goes through `LocalAcquisition.authorizeWireDispatch(RequestSpec, ResourceUri, CancelToken, () -> Unit)`.

- [ ] Write binding/lifetime design and independent review covering exact request identity, redirect/retry gates, provider visibility and artifact pins. Settle factory interface before worker assignment.
- [ ] Test real manifest permissions/network capabilities, caller-owned cancellation, waiting for WiFi/network/consent, late redirect revocation, process recreation and artifact descriptor/URI identity. A selected local file never authorizes network work.
- [ ] Implement actual ConnectivityManager/space checks, guarded transport/factory and qualifying UIDT execution on supported Android; reviewed fallback uses appropriate WorkManager execution. Jobs are DB-recoverable and visible; force-stop behavior is described honestly.
- [ ] Run focused native lifecycle/cancellation tests and genuine source transfer verification; retain actual dates/hash/counts. Never assume Scryfall's mixed Default Cards means strictly English content.
- [ ] Review/integrate factory, native configuration and exact evidence. Do not bind an ungated transport or fabricated content URI.

### Task4: Activate complete data and drain explicit removal requests

**Files:** catalog `LocalBulkCatalog.kt`, `LocalCatalog.kt`, `LocalCatalogIdentities.kt`, `CatalogGenerationCleanup.kt`; rules `LocalRules.kt`; root-created `DownloadedDataRemovalCoordinator.kt`, trusted verified-input bindings, shared generation/status provenance. Root owns any schema or public capability changes.

**Interfaces:** consumes Task3's `DownloadService`/`DownloadRunner`, actual pinned artifact input, `CatalogBulkImport`, `CatalogPrintingLookup`, `CatalogOracleLookup`, rules import/lookup, settings `pendingDataRemovals(CancelToken)` and `acknowledgeDataRemoval(EntityId, Revision, CancelToken)`. Produces installed data and durable scoped removals while keeping user entities intact.

- [ ] Review the verified-artifact/private-provider pin design and any missing immutable installed-resource consent provenance before implementation.
- [ ] Test tampered/caller-forged input, crash before/after activation, stale pointer, canceled ownership wait, bounded abandoned-generation cleanup, selected-import exclusion, new-consent exclusion and removal interrupted across restart.
- [ ] Bind full importer and shared-owner bounded cleanup to the existing tiny importer. Preserve small-file limits and old active reads. Fence old-consent writers/readers, remove only explicitly requested downloaded data, then revision-conditionally acknowledge only after durable completion.
- [ ] Run genuine full-card/rules/rulings installation and offline lookup/reopen proofs; distinguish original traversal/bulk consistency and earlier evidence from this exact final binding run.
- [ ] Independently review durability/deletion scope, run coherent hosted Android gate, then integrate. Source-only accepted engines do not close this task.

### Task5: Finish the first complete downloadable/offline workflow

**Files:** shell `ShellPorts.kt`, `presentation/SearchController.kt`, `presentation/RulesController.kt`, root-created `presentation/DownloadsController.kt`, `ui/DownloadsScreen.kt`, `ui/OnboardingScreen.kt`; existing card/rules/status/settings screens. Core `CatalogContracts.kt` if a reviewed gap requires change. Search/rulings adapters stay in their owned catalog/rules/search components.

**Interfaces:** consumes Tasks2–4 real ports plus existing `CatalogRepository`, `RulesRepository`, `CardSearch` contracts as each complete implementation becomes available; produces real S01–S04/S18/S19 flows. Root extends ShellPorts only after exact required adapters are reviewed; partial aggregate services are not registered.

- [ ] Split concrete source/rulings/query gaps into bounded cards with exact accepted signatures before parallel implementation. Preserve typed syntax/autocomplete/error positions and later owned/in-deck/price integration obligations.
- [ ] Test download/update/pause/resume/cancel/retry, failed activation keeping old content, real metadata/progress, rule/glossary references, dated card rulings, face/printing identity and offline query results.
- [ ] Implement Full/Lean/source/consent choices, operational resource controls and complete card/rules/search browsing in the visual system. Resource size/scope unknowns remain explicit.
- [ ] Complete the user journey: start download, interrupt/restart, finish installation, enable flight mode, search/browse full retained data, update safely and explicitly remove requested downloaded content.
- [ ] Review/run named data/download/rules acceptance rows and native UI evidence. Owned/in-deck/price query integrations remain explicitly OPEN until Tasks7/8 supply real consumers; syntax and typed unavailable behavior must already work. Advance dependent modules from the accepted data foundation, and close full K3/query acceptance only after those integrations pass. No zero-result substitute or final release with open rows.

### Task6: Complete life service, persistence and required UI

**Files:** task/lock `cx-m2-life-functional-01`/`life-functional.lock`; core `GameContracts.kt`; root-owned storage entities/migrations; feature/life production/tests; root-owned app registration and life UI module configuration. Implement service, codecs and repositories in focused files rather than mixing SQL with UI.

**Interfaces:** consumes accepted counter/clock/RNG domain, `GameService.resume/dispatch/undo/watch`, `Database`, `Clock`, `IdSource`, rules sources and required transfer capability. First root contract step adds reviewed missing creation/history/role/elimination/timer/priority/random/alert/provenance surfaces from all14 dispositions. No worker invents shared signatures.

- [ ] Implement/review missing persistent contracts and schema before service/UI bindings; map every G01–G14 and retained PX07 row to a bounded action-family task.
- [ ] Test grouped actions/undo, partners, own stolen commander combat damage, confirmation/override for prevented loss, clock zero policies/SHARED/priority pass, immutable action-time undo provenance, process recovery, unique alerts/results, finish/reopen and profile/game round trips.
- [ ] Implement real create/resume/dispatch/undo/watch/history and transaction-safe checkpoints/results. Implement designations, required counters/customization/random tools and authentic versioned Planechase/Archenemy/Bounty/custom module flows; no sample-only variant substitutions.
- [ ] Connect professional S20–S23 UI and G14/S17 exchange to actual services. Run create→play→background/restart→restore→undo→finish→reopen fully offline; assess 1–6 seat layouts on folded/unfolded/tabletop states.
- [ ] Independently review event/result/durability and source/variant correctness, run focused and hosted/native gates, then mark M2 acceptance only after the full retained feature flow is working.

## Remaining release roadmap: separate bounded plans

Each row requires an exact owned task/lock, accepted consumes/produces signatures from the listed core contract, meaningful tests, required independent review, coherent integration, actual UI journey and updated acceptance rows before the next dependent row. Setup/scaffolding stays inside its feature task. The master plan does not authorize workers to guess future shared interfaces.

| Order | Existing milestone and interfaces | Required functioning outcome / reused material |
|---|---|---|
| 7 | M3: `DeckRepository`, `DeckAnalysis`, `TransferService`; DeckCollectionContracts/TransferContracts | Create/edit boards, versions/diffs, packages, multi-edit, authentic vendor/native import/export, unknown resolution and loss preview. Use tested CSV syntax; study ManaVault/Desktop Companion formats without inheriting their architecture. S05–S11/S17. |
| 8 | M4: `CollectionRepository`, `PricingService` | Exact owned lots/printing/finish/location, allocations, real unknown-aware price sources/native units/policy, printing optimizer and undo/restore. S12/S14 plus deck integration. |
| 9 | M5: `DeckAnalysis`, `DeckValidator`, `BracketService`, link contracts | Complete probability/opening-hand/manual goldfish, deterministic all-format validation, versioned brackets/Game Changers/combos, interactions and real approved links with evidence/intent/unknown distinctions. All required datasets download/update in-app. S09/S10/S15 and consumer surfaces. |
| 10 | M6: `RecommendationEngine` | Offline roles/overrides/budget/legality-aware explanations and versioned patch previews; apply/undo through real deck transactions. S13. |
| 11 | M7 v1: `DeckLab`, `SecretVault`, `TransferService` | All specified prompt/cloud/custom/LAN modes/dialects, prompt export/pasteback, real streaming/tool limits/cancel/history, credential vault and backup restore. S16/S24/S17 operate without `LocalInference`. H03/S25/native runtime and dependent model management are v1.1 under D26, not v1 blockers. |
| 12 | M8: image/pack capabilities and all screen contracts | Managed optional image packs separate from Coil cache, on-view art and bounded quotas; consistent visual system across all25 screens, adaptive inputs/TalkBack/large text, measured responsiveness/full-corpus performance. Accessibility and visual quality begin with each feature, not here for the first time. |
| 13 | M9: all retained contracts/acceptance rows | Whole-app regression, destructive-data/restore/security/privacy/license audit, personal canonical release package and install/update proof on Fold7. Read signing certificate from actual APK; no secret logs or debug-package substitution. |

## Acceptance, reporting and handoff

- A user journey is complete only when real UI → real operation → persisted outcome → restart/undo/recovery all work where required. A test-only primitive or available port declaration remains partial.
- Maintain a current acceptance delta from the historical full audit: completed/partial/absent with exact implementation and verification evidence. Preserve the historical baseline; never overwrite it with optimistic estimates.
- Release only after every retained v1 requirement/parity/screen row is satisfied (D26 engine/S25 clauses explicitly tracked for v1.1), required download/update paths work, Pat's actual Fold7 flows pass, and the actual APK matches the canonical certificate. Visual refinement feedback is assessed from rendered screens; it does not waive functionality.
- Preserve existing source reviews and hosted evidence; fresh compatibility/native/device work in this plan is not_run until performed. This plan makes no model-speed, device-performance or delivery-date promise.

## Latest continuation delta

Pat approved execution with D26: on-device AI engines and dependent model management belong to v1.1 and do not block v1. Four Luna medium research agents plus root checked candidates and pinned examples/licenses outside the app; structured findings are archived under verification/reuse-research. No dependency/product code has been changed for reuse. Plan self-review covers all retained milestone groups, all14 life dispositions, downloads/consent, professional UI, license boundaries and canonical/device acceptance. Next execution: Task1 exact compatibility decisions and Task2 visual foundation, then Tasks3/4/5 and independent Task6 according to accepted interfaces/locks. Existing worker-routing preference is preserved; implementation resumes with the compatibility/visual batch and no local-engine task.
