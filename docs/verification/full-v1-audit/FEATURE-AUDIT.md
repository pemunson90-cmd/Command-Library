# Command Library full feature audit

Audited 2026-10-12 against main `f4760ba2451ee369519ee6e0220ff6352fc60fc5`. The delivered APK was built from `d334d9e3a60ac739c038edcc52f18fb72414a068`; its entire Android source tree matches main. This is a source/evidence audit, not new device testing. No production code or repository documentation was changed during this audit.

## Verdict

The delivered package is an incomplete foundation. It does not satisfy complete Android-v1 acceptance. Interfaces, registered components, requirement mappings and passing tests for the foundation are not implemented product features. Omitting unbuilt navigation prevents placeholder controls but does not fulfill the omitted requirements.

The previous interpretation of “polish, feature specifications, updates etc can be handled later” incorrectly narrowed delivery to four destinations. Full existing functionality remains required. The added “D-23 Immediate functional Fold7 build” records that mistaken interpretation and also duplicates the existing D-23 parallel-work identifier. Completion claims and continuation state must be corrected before further implementation. Windows, later sync/public-release work remain genuinely deferred by earlier explicit rulings; Android feature omissions do not.

## Signing

**The delivered APK is debug-signed, not canonically signed.** `.github/workflows/android-k2.yml` invokes `:app:assembleDebug` and publishes `outputs/apk/debug/*.apk`. `android-app/app/build.gradle.kts` has no canonical release signing configuration. The workflow does not consume `ANDROID_SIGNING` or verify the approved signing certificate. No private key or secret value was accessed for this audit.

D-03 specifies canonical certificate SHA-256:
`E2:D8:F5:0D:A6:01:48:CE:E8:55:BF:C6:F3:01:D4:FE:38:E9:E3:DE:28:10:C6:05:83:30:16:7F:B4:F5:77:E7`.

Delivered APK file SHA-256 (a file checksum, not a signing certificate):
`cdc696e77ba78023dcd2b20e4b02016f68264e4890c62510d6f43a09f5d7c3b6`.

Canonical release signing, public-certificate verification, and signed-package installation/update testing remain required. A debug installation cannot be assumed to update in place to a differently signed package.

## In-app downloads

**There are no in-app dataset download buttons or implemented network downloader.** The manifest has no INTERNET permission. Catalog and Rules invoke Android OpenDocument file pickers. Imported data is real and persisted, but users must acquire files elsewhere. “Reading the file” is not a network download.

| Required downloadable content | Current state | Completion requirement |
|---|---|---|
| Full English Oracle text and every English printing | Absent download; JSON-array import limited to 8 MiB/10,000 printings | In-app source selection, size/disk preview, Download, real progress/resume/pause/cancel/retry, validated atomic activation; real full coverage |
| Comprehensive Rules and glossary | Local TXT import/search/reference browsing exists; download absent | Official-source Download/update button, version/date/provenance and safe activation; preserve import option |
| Dated card rulings and keyword links | Representative source evidence only; no working product download/integration | Download/update corpus and show linked offline rulings/keyword-rule links |
| Art/cache and deck/collection/all-card image packs | Absent | On-view art, bounded cache, explicit optional pack Download, size/storage/Wi-Fi/resume controls |
| Bracket criteria/Game Changers and permitted combo/enrichment catalogs | Absent consumers/downloads | Reviewed versioned sources, in-app downloads/updates, provenance and deterministic consumers |
| Variant rules/card manifests | Absent | In-app supported manifest downloads/import and operational Planechase/Archenemy/Bounty/custom modules |
| On-device models | Absent | In-app model Download with license/hash/size/RAM/storage preview and actual inference runtime |
| Optional non-English card packs | Absent | Explicit language approval before Download; retain English default |

Downloads must be actual operations rather than external links or inert controls. Optional means user-enabled, not omitted. Normal user-created decks, inventory and histories need functional import/export/backup controls rather than being treated as remote downloadable catalogs.

## Verification limits

The existing hosted run 38309643911 reports 256 JVM tests and 57 native tests in each of three emulator lanes, with no reported failures/errors. These validate implemented foundation paths only. Genuine official rules parsing was exercised; catalog evidence includes one genuine card and representative rulings, not the complete English corpus. No physical Fold7 install, full-feature task execution, TalkBack/fold proof, complete screen audit or full-corpus performance pass exists.

## Evidence obligations (COVERAGE E01–E13)

| ID | Audit status |
|---|---|
| E01 | Partial representative Scryfall evidence; full English printing/ruling coverage, real whole-feed bytes/counts/art and price-source acceptance missing |
| E02 | Genuine official TXT and parser integrity evidence exists; complete linked card-rulings/keyword path and app download acceptance missing |
| E03 | Current bracket criteria/Game Changer reconciliation and permitted combo ingestion/consumers unverified |
| E04 | Authentic vendor/deck/collection round-trip acceptance absent |
| E05 | Operational vendor card-link routing/source behavior acceptance absent |
| E06 | Provider auth/stream/tool/error/pricing/eligibility acceptance absent |
| E07 | Local/LAN server integration acceptance absent |
| E08 | Model/runtime download/load/cancel/license and Fold7 performance acceptance absent |
| E09 | Android key vault/security integration acceptance absent |
| E10 | Partial emulator Compose evidence; actual Fold7 sensors/TalkBack and complete task accessibility unrun; Windows v2 deferred |
| E11 | Foundation SQLite/FTS/migration tests exist; full-corpus/task/performance/restore acceptance missing |
| E12 | Debug hosted build/native proof exists; canonical signing and physical signed install/update unverified; Windows v2 deferred |
| E13 | Variant source/module, QR and seat-clock acceptance absent |

## Completion order derived from gaps

1. Correct scope/continuation records and duplicate decision identifier; track every requirement and retained parity row to actual acceptance evidence.
2. Implement full source acquisition/download/update engine, full English catalog/rulings/rules coverage, images and complete local search.
3. Implement deck management/import/export, collection/pricing, all-format validation/analysis, brackets/recommendations and every retained deck parity extra.
4. Implement all offline life/game state, profiles/history, timers/counters/variants and retained life parity extras.
5. Implement all specified AI/provider/local/model/vault/prompt paths and safe backup/restore/transport.
6. Complete all screens, custom identity, adaptive/input/accessibility states alongside the features; visual refinement may follow testing but required usability cannot be skipped.
7. Verify complete acceptance on the Fold7, sign with the canonical key and verify its certificate, then deliver the completed app. Emulator/API compatibility work is secondary to the user's Fold7.

The detailed matrices below classify production behavior against every requirement, parity row and screen. “Partial” never means accepted.

Audit totals: **64 requirements: 14 partial, 49 absent, one approved scope-boundary ruling (N03); none fully accepted. 194 retained parity rows: 188 absent, four partial, two narrow behaviors implemented; 19 approved skips. All 25 screen contracts audited: five partial, 20 absent; none complete.**

# Complete SPEC implementation audit

**Baseline:** immutable `f4760ba` (main handoff merge), audited 2026-10-12. All 64 IDs in `docs/gauntlet/SPEC.md` included. Repository was read-only; production files extracted with `git archive` to an external scratch directory because live HEAD had moved.

This evaluates actual shipped Kotlin implementation, not interfaces, tests, architecture plans, schema declarations or CI success. Partial means real production code implements a subset or foundation; it does not mean acceptance passed. Absent means no executing production behavior for the ID. Implemented-unverified would require all specified behavior to exist, but none qualifies. Deferred-approved is reserved for the expressly approved scope boundary, not missing Android features.

**Scope:** full personal Android v1 remains required. D-23 “Immediate functional Fold7 build” is treated as mistaken narrowing, not user approval to omit any feature. Windows is approved v2; sync/public release/shared multi-phone state is later. Partial Windows clauses do not defer their Android parent requirement. “Optional” variant/language/image/model functionality means user-enabled, not omitted.

**Production inventory:** AppGraph creates StorageDatabase, LocalCatalog, LocalRules and DataStoreSettingsRepository. ShellRoot exposes only Cards, Catalog, Rules and Settings. The `src/main` Kotlin tree contains no executable deck, collection, pricing, validation, recommendation, bracket, life/game, AI/provider, inference, vault, transfer, external-link service or network adapter. Contracts under `core/model/*Contracts.kt` and domain data classes are excluded as implementation evidence. The LIMIT citation below is used only to identify the real enforced importer configuration, not to credit contracts.

## Source citations

- **GRAPH**: `android-app/app/src/main/kotlin/com/architectpsg/commandlibrary/di/AppGraph.kt`
- **NAV**: `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/ShellRoot.kt`
- **CAT**: `android-app/feature/catalog/src/main/kotlin/com/architectpsg/commandlibrary/feature/catalog/LocalCatalog.kt`
- **CODEC**: `android-app/feature/catalog/src/main/kotlin/com/architectpsg/commandlibrary/feature/catalog/ScryfallCodec.kt`
- **LIMIT**: `android-app/core/model/src/main/kotlin/com/architectpsg/commandlibrary/core/model/LocalCatalogContracts.kt`
- **RULES**: `android-app/feature/rules/src/main/kotlin/com/architectpsg/commandlibrary/feature/rules/LocalRules.kt`
- **PARSER**: `android-app/feature/rules/src/main/kotlin/com/architectpsg/commandlibrary/feature/rules/parser/RulesTextParser.kt`
- **RULEUI**: `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/RulesController.kt`
- **SEARCH**: `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/SearchController.kt`
- **DETAIL**: `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/CardDetail.kt`
- **SET**: `android-app/feature/settings/src/main/kotlin/com/architectpsg/commandlibrary/feature/settings/DataStoreSettingsRepository.kt`
- **DEFAULT**: `android-app/feature/settings/src/main/kotlin/com/architectpsg/commandlibrary/feature/settings/SettingsStoreFormat.kt`
- **SETUI**: `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/SettingsScreen.kt`
- **LAYOUT**: `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/Components.kt`
- **THEME**: `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/ShellTheme.kt`
- **CARDUI**: `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/CardsScreen.kt`
- **DB**: `android-app/data/storage/src/main/kotlin/com/architectpsg/commandlibrary/data/storage/StorageDatabase.kt`

## All requirement IDs

| ID | Capability | Status | Production evidence and missing acceptance |
|---|---|---|---|
| A01 | Full English catalog | partial | **CAT, CODEC, DETAIL, LIMIT**. English local-file import stores Oracle/printing/face/stat/image metadata and displays text/faces. Missing full-feed acquisition and coverage: enforced 8 MiB/10,000-printing cap; no dated card rulings, legality/Game Changer runtime projection, or all-printings detail workflow. |
| A02 | Language consent | partial | **SET, DEFAULT, CAT**. English default and durable installation-bound explicit consent exist; importer rejects non-English. No language/size approval UI, optional language download/resume, or foreign owned-identity workflow; absence of network does not satisfy the foreign inventory acceptance. |
| A03 | Storage modes | partial | **SET, DEFAULT, CAT**. Storage preference can be persisted, but catalog status always reports FULL. No Lean retention/eviction/on-demand resolution or mode switch UI; deck/collection preservation acceptance cannot run. |
| A04 | Offline rules | partial | **RULES, PARSER, RULEUI**. Real two-pass local TXT import, versioned atomic corpus, glossary/numbered entries, text search and cross-reference navigation exist. Missing card-ruling ingestion/linking and card keyword-to-rule links; genuine full corpus/Android and physical offline acceptance is not established by this audit. |
| A05 | Image packs/cache | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No image fetch/cache, pinning/pack manager, estimates/disk check, Wi-Fi control or resumable verified packs; storing image URIs and cache preferences is not image behavior. |
| A06 | Atomic updates | partial | **CAT, RULES**. Local imports stage, validate/count/checksum and transactionally activate; failed staging is cleaned and prior active corpus retained. Missing upstream metadata-check/update-now network acquisition, resumable download/index lifecycle and whole-product crash-boundary/user-edit acceptance. |
| A07 | Local query syntax | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No typed local query AST/parser/evaluator/autocomplete, field/comparison/group/negation semantics, positioned unsupported syntax or labeled online fallback. lookupName is a literal name-prefix query only. |
| A08 | Text search | partial | **CAT, RULES, SEARCH**. Name-prefix FTS and rules-text FTS have snapshot-bound stable pagination; original names retained. Catalog Oracle-text search and prefix suggestions UI absent; typing alone does not supersede submitted card search. Full catalog and P02/P03/P04 evidence missing; rules UI caps displayed results at 200. |
| B01 | Deck boards/roles | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No deck repository/editor or persisted boards, quantities, roles, printing/finish/language selections, category allocation or undo. |
| B02 | Editing and organization | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No deck categories/tags/reorder/notes/Markdown primer/duplicate/folders/search/sort, sanitized renderer or autosaved deck drafts. |
| B03 | Deck analysis | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No mana curve/pips/source/type/average-mana analysis, land guidance or derived token list engine/UI. |
| B04 | Draw/probability | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No hypergeometric calculator, opening hands/mulligans, random goldfish simulator or conserved card zones/undo. |
| B05 | Versions/diff | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No immutable named deck snapshots/diff/previewed restore or deck edit undo. |
| B06 | All-format validation | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No executing all-format registry, Commander/partner/companion constraints, banned/restricted checks or legal/illegal/unknown verdict UI. |
| B07 | Native parity | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No executable approved deck parity features/packages/views/analysis. Matrix/planning records cannot demonstrate build/adapt rows. |
| B08 | Deck imports/exports | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No guided vendor deck paste/file flow, native deck text/CSV/destination exporters, loss preview or unresolved-card resolution. |
| C01 | Inventory | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No owned-lot inventory, condition/location/finish/language quantities, mark-owned preview/transaction/undo or CSV/foreign identity storage. |
| C02 | Acquisition n | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No acquisition-cost math/quote consumer or contextual inline n; missing/owned-zero/market-value acceptance absent. |
| C03 | Price source/currency | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No actual price adapters/quotes/source/native unit selector/FX conversion or stale statistic/date display; decimal types alone are declarations. |
| C04 | Printing allocation/totals | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No compatible owned printing allocation, physical-copy conservation, pinning policy or known-plus-missing deck totals. |
| D01 | Budgets | partial | **SET**. Real settings persistence rejects negative budgets and stores one budget amount. No per-card/upgrade/deck caps UI, owned-free/source policy or suggestion consumer enforces affordability; unknown-cost/boundary acceptance absent. |
| D02 | Offline recommendations | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No offline effect/role/enabler/payoff/curve/identity/combination recommendation engine, explanations, role overrides or local cost/legality/bracket evidence. |
| E01 | Bracket rules data | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No official versioned bracket criteria/Game Changer activation/reconciliation or attributed permitted combo cache. |
| E02 | Bracket estimates/targets | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No evidence-backed bracket estimates/confidence/intent questions or constrained revalidated target-change patch. |
| F01 | External links | partial | **CODEC**. Importer preserves supplied printing links/vendor IDs with URI validation. No external-link resolver, configured templates/adapters/search fallback or clickable seller/card links; selected-printing behavior and percent encoding acceptance absent. |
| G01 | Players/layout | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No game setup/1–6 seat layout, orientation/hinge-safe tabletop, persisted seat identity/reorder/elimination/restore or eligible-player random pick. |
| G02 | Life adjustments | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No life counter tap/hold/numeric input/presets, release/cancel/focus handling or reversible hold group. |
| G03 | Commander damage | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No separate opposing commander/partner damage, simultaneous life change, tax or threshold/override behavior. |
| G04 | Counters | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No hideable poison/energy/experience/rad/tax/storm/custom/mana/object counter state or bounds/loss semantics. |
| G05 | Game designations | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No Monarch/Initiative/Day-Night/Blessing holder state or transfer/log/undo. |
| G06 | Random tools | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No dice/coin/first-player/seat randomizer, validation/batching/full history/sum. |
| G07 | Timers | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No turn/game timers, monotonic persisted anchors, background policy or timing undo. |
| G08 | Art/deck profiles | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No linked player profiles/decks, cached art backgrounds or contrast-safe overlays/deleted-deck handling. |
| G09 | Loss/end/stats | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No format-aware loss detection/rationale/override, once-only confirmed results or linked-deck win/loss/draw statistics/reopen correction. |
| G10 | Durability/undo/log | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No game event log/autosave/resume/grouped undo/keep-awake lifecycle; generic database transactions do not implement game durability. |
| G11 | Life parity extras | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No life parity modules/profiles/numeric entry/crop/reduced-motion or game history AND filters/count/clear. Multi-phone alone is approved deferred. |
| H01 | Modes/context | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No grounded deck prompt modes/constraints/truncation/Surprise angle or validated local-cost card suggestions. |
| H02 | Cloud/custom providers | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No direct-key cloud/custom LAN providers, native codecs, model discovery/manual real request, SSE or cancellation. Local file import is not provider networking. |
| H03 | On-device | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No Android llama.cpp/GGUF runtime, model selection/storage/license/RAM warnings or verified resumable model file lifecycle. Windows runtime portion approved v2. |
| H04 | Prompt export/pasteback | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No account-free full deck prompt copy/share/export or grounded paste-back. |
| H05 | Grounding/reply validation | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No reply parser/name resolution/local existence-format-identity-budget-owned validation, tappable suggestions or atomic upgrade preview/application. |
| H06 | Bounded tool calling | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No bounded read-only local tool dispatcher, caps/timeout/partial JSON validation/provider cancellation. |
| H07 | Secrets | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No Android Keystore AES-GCM vault, credential reentry/corruption handling, redaction or origin-safe authenticated redirects. Windows vault approved v2. |
| H08 | Streaming/history/cost | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No streaming generation/cancel/retry/partial attempt state, per-deck conversation persistence/delete or truthful token/cost reporting. |
| H09 | Prompts | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No shipped versioned system prompt loader/editable override/reset/template preview/provenance export. |
| I01 | Lossless transfer | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No user deck/collection exchange, one-file full backup/export, histories/game/settings backup manifest or loss disclosure. Catalog/rules imports are not user-data transfer. |
| I02 | Restore safety | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No validated archive staging/atomic restore/pre-restore recovery or whole-user-data crash-boundary acceptance. Installation-bound consent only contributes a safe settings foundation. |
| J01 | Adaptive state | partial | **NAV, LAYOUT, SEARCH, RULEUI**. Available-width layout and ViewModel controllers retain active shell state across recreation; compact navigation bar versus rail and card split panes exist. Only THREE width classes (600/840 dp), no five classes/height overrides/hinge sensors/manual posture. Deck/game/stream resize acceptance impossible with absent features. |
| J02 | Desktop input | partial | **CARDUI, NAV**. Compose controls, scrollable content and search IME action are real. No app shortcut map, right-click contexts/focus restoration, file drag/drop or accessible reorder commands. Complete primary-task keyboard acceptance absent; Windows chrome/drop/min-size is approved v2 only. |
| J03 | Accessibility/design | partial | **THEME, LAYOUT, SETUI, DETAIL**. Real light/dark token themes, scalable sp text, 48 dp theme controls, heading/merged-label/polite live-region semantics and resource-backed UI strings exist. Full app TalkBack/200% acceptance absent; no implemented reduced-motion/high-contrast policy, identity accents or throttled announcements shown. New features must also externalize strings; backend/domain text needs display-localization review. |
| J04 | Performance/visual audit | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No P01–P12 instrumentation suite or complete screen/posture screenshot audit; physical Fold7/TalkBack proof absent. Builds/emulator smoke do not satisfy these budgets. |
| K01 | Onboarding/settings | partial | **DEFAULT, SETUI, NAV**. Full/English settings defaults, persisted theme and local catalog/rules picker/import progress/error paths exist. No first-run download/size/source choice, consent manager/module settings, resumable Wi-Fi downloads or USD active source. Default cache is zero bytes/rulings retention false, explicitly temporary, not required online-on-view art behavior. |
| K02 | Attribution/diagnostics | partial | **SETUI, CAT, RULES, DEFAULT**. Imported provenance/status and supplied catalog notice are shown; unknown source dates remain unknown rather than fetch dates. No complete About license/date inventory or redacted diagnostic export; settings sourceNotices is empty and rules notice/required-source support issues need full acceptance. |
| N01 | Architecture/CI gates | partial | **GRAPH, DB, SET**. Kotlin app composition, real cancellable repositories, Room/SQLite durability and migration foundation exist. Remaining component implementations absent. Hosted debug checks are evidence outside this source-only audit; signed existing-key release/certificate/install/launch, final gates and full architecture acceptance remain unverified. |
| N02 | Network etiquette | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No shared HTTP client/rate limiter/identity headers/Retry-After/safe retry or ETag download resume. No network calls is not implementation of network etiquette. |
| N03 | Scope boundaries | deferred-approved | **GRAPH, NAV** establish a personal offline Android subset; Windows/sync/public release/shared-phone deferral is explicitly authorized by D-19/D-20 and AGENTS.md. This row credits only scope boundaries, not completeness of the subset. No absent Android behavior above is excused. D-23 narrowing must be corrected; demonstrated blockers/manual steps remain required. |
| B09 | Reusable packages | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No card package create/edit/apply/copy/link/update/remove provenance transactions or backup integration. |
| B10 | Multi-edit | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No deck/OwnedLot multi-selection, mixed-value validation preview, transactional multi-edit or grouped undo. |
| B11 | Printing optimizer | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No pricing/ownership optimizer, deterministic eligible plan, unknown-cheapest handling or versioned substitution patch. |
| B12 | Interaction playtest | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No manual interaction scenarios, zone/event selection, conserved moves, replay/reset/undo. |
| B13 | Local image/QR sharing | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No deck-image renderer/export, local versioned QR payload/frame/hash encode/decode/camera import or privacy/overflow preview. |
| G12 | Variant modules | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No Planechase/Archenemy/Bounty/custom variant manifest/rules/zones/action manager, personal corpus import or persisted undo. |
| G13 | Seat clocks/alerts | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No countdown/chess seat clocks, switching/pause/restart balances or configurable accessible once-per-transition alerts. |
| G14 | Profile/game transport | absent | **GRAPH, NAV** (complete registered adapter/destination inventory); no production adapter for this ID. No local profile exchange collision preview or versioned game CSV/JSON import/export/privacy and statistic consistency. |

## Counts and implications

- 64 requirements: **14 partial**, **49 absent**, **0 implemented-unverified**, **1 deferred-approved** (N03 scope boundary only).
- **0 complete requirement IDs are established by production inspection.** Real catalog/rules/settings/storage/shell foundations are useful; the small functional APK is not the full specified product.
- Immediate catalog blockers: enforced 8 MiB/10,000-printing limit, name-only catalog search, no card rulings, no source refresh/download or optional pack lifecycle. Three width classes do not meet five-class/height/hinge requirements.
- Next completion work must implement every absent Android domain and finish every partial acceptance; authentic corpus/provider/export/format fixtures and physical fold/accessibility/performance/release evidence must accompany delivery.

**Verification actually performed:** source inventory, immutable git archive, composition-root/navigation inspection and targeted production source reads. No compilation, unit/instrumentation run, physical device run, genuine full-catalog activation or release-signing verification performed in this audit. Existing test reports cannot fill missing functionality. No repository files changed.


# Production parity and design audit

**Audited revision:** `f4760ba` (`origin/main` per task); production UI tree checked against the shared checkout at `707a271`, whose `android-app/feature/shell/.../ui` tree matches the target. **Matrix:** 213 rows: 112 build, 82 adapt, 19 approved skips; 194 retained obligations and 19 approved skips.

The status below is based on production UI wiring and user-visible behavior. Contract/data declarations, tests, and feature names do not count as shipped behavior. Production navigation registers only Cards, Catalog, Rules (when its controller exists), and Settings. No deck, collection, pricing, recommendation, life/game, AI, export/backup, or model screen is registered. The catalog and rules screens import user-selected local files; neither provides in-app dataset downloads.

Status key: **Absent** means no user-visible production behavior for the row; **Partial** means only the stated narrow adjacent behavior exists and the approved behavior is not met; **Implemented** means the named user-visible behavior is present (full final acceptance evidence can still be outstanding).

## Parity rows

| ID | Feature | Disposition | Production status |
|---|---|---|---|
| PX05-001 | Commander slot | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-002 | Partners | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-003 | Backgrounds | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-004 | Companion | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-005 | Mainboard | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-006 | Sideboard | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-007 | Maybeboard/considering | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-008 | Custom categories | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-009 | Card tags | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-010 | Drag-and-drop | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-011 | Per-card printing | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-012 | Per-card finish | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-013 | Quantities | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-014 | Per-card notes | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-015 | Markdown description | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-016 | Markdown primer | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-017 | Mana curve | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-018 | Color pips versus sources | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-019 | Type breakdown | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-020 | Average mana value | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-021 | Land count guidance | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-022 | Hypergeometric calculator | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-023 | Opening-hand simulator | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-024 | Goldfish playtester | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-025 | Mulligan rules | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-026 | Token list | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-027 | Per-format legality | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-028 | Deck size validation | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-029 | Singleton validation | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-030 | Color identity validation | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-031 | Banned/restricted validation | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-032 | Companion conditions | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-033 | Live warnings | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-034 | Deck versions | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-035 | Version diff | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-036 | Duplicate deck | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-037 | Folders | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-038 | Deck tags | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-039 | Search across decks | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-040 | Sort across decks | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-041 | Pasted-text import | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-042 | File import | build | Absent — local catalog-file import exists, but there is no deck-file parser/preview. |
| PX05-043 | CSV deck import | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-044 | Moxfield URL import | skip | Approved skip — Enable guided export until a provider authorization exists. |
| PX05-045 | Import from other providers | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-046 | Common plain-text export | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-047 | CSV export | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-048 | Bulk Edit tag copying | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-049 | Bracket display | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-050 | Packages | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-051 | Collection tracking | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-052 | Binders | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-053 | Price providers/currency | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-054 | View modes | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-055 | Private/public/unlisted visibility | skip | Approved skip — Local decks by default; export sharing needs no hosted visibility system. |
| PX05-056 | Social likes/comments/follows | skip | Approved skip — Outside native personal/offline library scope. |
| PX05-057 | Public share links | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-058 | Social notifications/view counts | skip | Approved skip — Avoid building a social backend for local use. |
| PX05-059 | EDHREC integration | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-060 | Sandbox counters/dice/copies | adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX05-061 | Set/card browsing | build | Partial — local card search/detail and imported catalog exist; set navigation/printing browser is absent. |
| PX05-062 | Scryfall-style search | adapt | Partial — local text query exists; Scryfall field syntax and explicitly labeled online fallback are absent. |
| PX05-063 | Card rulings display | build | Absent — rules corpus import/search exists, but this is not card-specific rulings display. |
| PX05-064 | Dark mode/preferences | build | Implemented — system/light/dark selection is persisted and applied; required contrast/reflow audit evidence is absent. |
| PX05-065 | Patreon tiers | skip | Approved skip — Do not reproduce fundraising tiers; no verified feature gate. |
| PX05-066 | Paid exclusive features | skip | Approved skip — No justified parity requirement until benefits are established. |
| PX05-067 | In-app effective cost (n) | build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-001 | Commander | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-002 | Partners | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-003 | Backgrounds | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-004 | Companions | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-005 | Mainboard | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-006 | Sideboard | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-007 | Maybeboard | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-008 | Custom categories | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-009 | Card tags/labels | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-010 | Drag and drop | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-011 | Per-card printing | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-012 | Finish | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-013 | Quantities | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-014 | Per-card notes | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-015 | Description/primer | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-016 | Markdown | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-017 | Mana curve | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-018 | Pips versus land sources | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-019 | Type breakdown | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-020 | Average mana value | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-021 | Land count guidance | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-022 | Hypergeometric calculator | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-023 | Opening hands | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-024 | Goldfish playtester | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-025 | Mulligan rules | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-026 | Token list | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-027 | Per-format legality | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-028 | Deck-size checks | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-029 | Singleton checks | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-030 | Color identity checks | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-031 | Banned/restricted checks | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-032 | Live warnings | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-033 | Deck versions | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-034 | Diff | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-035 | Restore | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-036 | Duplicate deck | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-037 | Folders | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-038 | Deck tags | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-039 | Search decks | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-040 | Sort decks | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-041 | Text import | Build | Absent — no deck text import or preview. |
| PX06-042 | Arena text import | Build | Absent — no Arena deck import or preview. |
| PX06-043 | CSV import | Build | Absent — no CSV deck import/mapping. |
| PX06-044 | URL import | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-045 | Plain text export | Build | Absent — no deck text export. |
| PX06-046 | CSV export | Build | Absent — no deck CSV export. |
| PX06-047 | Stacks/Grid/Text/Table/Brewer views | Adapt | Absent — no deck view modes or shared deck state surface. |
| PX06-048 | Category templates | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-049 | Multi-edit | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-050 | Collection | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-051 | Price sources | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-052 | Printing optimization | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-053 | Bracket display | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-054 | Sandbox | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-055 | Interaction simulator | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-056 | Playtest log | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-057 | Autosave/undo | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-058 | Collaboration | Skip | Approved skip — Cloud collaboration outside personal-first brief. |
| PX06-059 | Comments/follows/voting | Skip | Approved skip — No social backend required. |
| PX06-060 | Image/QR sharing | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-061 | Shopping carts | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-062 | EDHREC recommendations/cuts | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX06-063 | Custom/pinned cards | Skip | Approved skip — Outside baseline official-card catalog; possible backlog. |
| PX06-064 | Paid ad removal | Skip | Approved skip — No reason to add advertising infrastructure. |
| PX07-001 | 1 player | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-002 | 2, 3, 4, 5, 6 players | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-003 | Seat-oriented panels | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-004 | Tap +1/-1 | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-005 | Hold auto-repeat | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-006 | Configurable step | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-007 | Starting life 20/25/30/40 | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-008 | Custom starting life | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-009 | Damage per opposing commander | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-010 | Partners tracked separately | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-011 | Optional auto life deduction | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-012 | Poison | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-013 | Energy | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-014 | Experience | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-015 | Rad | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-016 | Commander tax | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-017 | Storm | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-018 | User-defined counters | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-019 | Hide each counter | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-020 | Monarch | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-021 | Initiative | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-022 | Day/Night | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-023 | City's Blessing | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-024 | Dice any sides | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-025 | Dice any count | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-026 | Coin flip | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-027 | Random first player | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-028 | Seat randomizer | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-029 | Turn counter | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-030 | Turn timer | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-031 | Game timer | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-032 | Per-player backgrounds | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-033 | Card-art background | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-034 | Game-over detection | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-035 | Undo life/counter changes | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-036 | Autosave | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-037 | Resume after restart | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-038 | Game log | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-039 | Keep screen awake | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-040 | Deck linked to seat, W/L | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-041 | Foldable tabletop posture | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-042 | Offline life-counter operation | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-043 | Tablet sizing | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-044 | Format/team presets | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-045 | Random player/opponent | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-046 | 3/5-player layout picker | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-047 | Timer undo | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-048 | Shared profile transport | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-049 | Over 6 players | Skip | Approved skip — Beyond approved 1-6 baseline |
| PX07-050 | Saved player profiles | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-051 | Two commander artworks | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-052 | Mana pool | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-053 | Treasure | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-054 | Charge | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-055 | Blood/Food/Clue/Ring | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-056 | Planeswalker/Saga/Siege | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-057 | Direct numeric life input | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-058 | Eliminate/restore player | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-059 | Defeat/victory messages | Skip | Approved skip — Optional cosmetic scope |
| PX07-060 | Planechase | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-061 | Archenemy | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-062 | Bounty | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-063 | Custom variant decks/presets | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-064 | CSV/JSON game export | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-065 | Game history search | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-066 | Draw results | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-067 | Countdown/chess clock/alerts | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-068 | Camera/operator orientation | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-069 | Drag reorder seats | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-070 | Art crop/contain/cover | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-071 | Languages | Adapt | Implemented per recorded English-only ruling — English UI strings are externalized; translations are out of current ruling. |
| PX07-072 | OLED/dark appearance | Adapt | Partial — app-wide light/dark tokens and theme preference exist; OLED-specific appearance evidence is absent. |
| PX07-073 | Disable dice animation | Adapt | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-074 | Background music | Skip | Approved skip — Licensing/audio scope with low utility |
| PX07-075 | Flavor text in menu | Skip | Approved skip — Not core life-counter function |
| PX07-076 | Companion/shop promotion | Skip | Approved skip — Avoid unrelated promotion |
| PX07-077 | Card search/prices/legalities | Adapt | Partial — local card search/detail exists; price/legalities surfaces are absent. |
| PX07-078 | Live TV/spectator sharing | Skip | Approved skip — Network shared state deferred |
| PX07-079 | Shared recap links/voting/chat | Skip | Approved skip — Online scope deferred; local history retained |
| PX07-080 | Account/cloud sync | Skip | Approved skip — Sync post-release per brief |
| PX07-081 | Full backup/import | Build | Absent — no corresponding production screen/flow or implemented user-visible behavior. |
| PX07-082 | Ads/paid ad removal | Skip | Approved skip — No ad system needed for personal app |

## Approved skips (separate disposition list)

19 approved source rows are scope dispositions, not implementation failures. These are the only skipped rows; every build/adapt row remains required by the matrix.

| ID | Feature | Approved reason |
|---|---|---|
| PX05-044 | Moxfield URL import | Enable guided export until a provider authorization exists. |
| PX05-055 | Private/public/unlisted visibility | Local decks by default; export sharing needs no hosted visibility system. |
| PX05-056 | Social likes/comments/follows | Outside native personal/offline library scope. |
| PX05-058 | Social notifications/view counts | Avoid building a social backend for local use. |
| PX05-065 | Patreon tiers | Do not reproduce fundraising tiers; no verified feature gate. |
| PX05-066 | Paid exclusive features | No justified parity requirement until benefits are established. |
| PX06-058 | Collaboration | Cloud collaboration outside personal-first brief. |
| PX06-059 | Comments/follows/voting | No social backend required. |
| PX06-063 | Custom/pinned cards | Outside baseline official-card catalog; possible backlog. |
| PX06-064 | Paid ad removal | No reason to add advertising infrastructure. |
| PX07-049 | Over 6 players | Beyond approved 1-6 baseline |
| PX07-059 | Defeat/victory messages | Optional cosmetic scope |
| PX07-074 | Background music | Licensing/audio scope with low utility |
| PX07-075 | Flavor text in menu | Not core life-counter function |
| PX07-076 | Companion/shop promotion | Avoid unrelated promotion |
| PX07-078 | Live TV/spectator sharing | Network shared state deferred |
| PX07-079 | Shared recap links/voting/chat | Online scope deferred; local history retained |
| PX07-080 | Account/cloud sync | Sync post-release per brief |
| PX07-082 | Ads/paid ad removal | No ad system needed for personal app |

## Screen inventory S01–S25

No complete screen is present against its SCREENS contract. Status records any partial adjacent surface so that it is not mistaken for parity.

| Screen | Status against specified screen |
|---|---|
| S01 Onboarding | Absent — no onboarding, language choice, consent/estimate, or first-run download/resume flow. |
| S02 Card search | Partial — local query/results and catalog-missing action; no autocomplete/filter set, quick collection add, or explicit online fallback. |
| S03 Card detail | Partial — Oracle fields, multi-face text, and provenance render; no art/printing/finish selection, rulings, ownership, n, or market information. |
| S04 Rules search/reader | Partial — user-imported rules corpus can be searched and selected references followed; no complete keyword-to-rule/card-ruling integration or dedicated glossary/index experience; absent corpus directs to manual import. A genuine full rules TXT can be imported, and bundling is not required by the spec. |
| S05 Deck library | Absent — no decks, folders, tags, create/duplicate, or overview. |
| S06 Deck editor | Absent — no deck boards/editor/autosave/undo/analysis. |
| S07 Entry/category editor | Absent. |
| S08 Import preview | Absent for deck import — catalog JSON import does show progress/result, but it is a replace/import operation without deck-row resolution preview. |
| S09 Deck analysis | Absent. |
| S10 Goldfish/opening hand | Absent. |
| S11 Versions/diff | Absent. |
| S12 Collection | Absent. |
| S13 Recommendations | Absent. |
| S14 Budget/price policy | Absent. |
| S15 Bracket assessment | Absent. |
| S16 AI lab | Absent. |
| S17 Export/backup/restore | Absent. |
| S18 Data/language/image downloads | Absent — catalog/rules import only launches Android document picker for user-provided local files; no in-app download catalog/jobs. |
| S19 About/help/diagnostics | Partial — Settings shows basic About copy and source attribution/license notices; help index and diagnostics/report are absent. |
| S20 Game setup/profiles | Absent. |
| S21 Live table | Absent. |
| S22 Game tools/counter detail | Absent. |
| S23 Results/game/deck stats | Absent. |
| S24 Settings/providers/prompts | Partial — only persisted theme preference, basic About, and source notices; accessibility/modules/providers/prompts absent. |
| S25 Models/runtime | Absent. |

## Design and visual identity gaps

The app does have a custom visual foundation: `ShellTheme.kt` defines the specified light/dark color tokens and typography sizes, and Settings exposes persisted System/Light/Dark choice. Standard Material 3 Compose controls, English externalized strings, and some 48dp minimum action sizing are present. This does not amount to completion of the design system or visual acceptance. No user-visible in-app download progress for datasets exists; catalog and rules imports are local document-picker workflows.

- Navigation has four destinations (Cards, Catalog, Rules, Settings), not the specified Cards/Decks/Collection/Rules/Life/AI + Settings hierarchy; currently implemented destinations are appropriately the only ones shown, but most product surface is absent.
- Width handling exposes compact, medium, expanded classes only; implementation uses bottom bar for compact, rail otherwise, and results/detail split only at expanded. Required large/XL classes, supporting panes, compact-height override, posture/tabletop/book layouts, hinge exclusion and manual posture override are not implemented.
- Typography and palette tokens align with custom identity, but specialized mana/color identity accents, card art and grid rendering, quote/card-row contract, focus-ring interaction, desktop shortcuts/context menus/drag alternatives, high-contrast/reduced-motion behavior, 200% text acceptance and per-state component inventory are not established by visible UI.
- The basic card detail is text-first and provenance-aware, but the app has no art rendering, printing picker, collection/price display, detailed rules/rulings or associated visual language. Game/life tiles, charts, conversations, progress/download jobs, budget quotes and other feature-specific components are absent.
- Existing UI supplies some loading/empty/error/stale/import states and retries. No comprehensive screen-by-screen loading/partial/offline/error behavior, screen-reader acceptance, contrast measurements, responsive golden/auditor outcomes or TalkBack workflow proof was found in production UI.
- No approved complete visual audit is evident. The design system explicitly says its targets are requirements, not evidence of rendered UI.

## Evidence basis

Read-only inspection of `android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/ShellRoot.kt`, `CardsScreen.kt`, `CardDetail.kt`, `CatalogScreen.kt`, `RulesScreen.kt`, `SettingsScreen.kt`, `ShellTheme.kt`, and `Components.kt`, plus production destination wiring and root application graph. Target commit parity matrix was read with `git show f4760ba:docs/gauntlet/PARITY_ACCEPTANCE.md`; it contains exactly 213 source rows. Status claims intentionally do not infer production behavior from contracts or tests.
