# Task orch-kotlin-k3-offline-data-01

Status: in progress, local-first input paths selected; step3 private parser preparation. K2 remains accepted. Production full-corpus contracts/migration/activation are not implemented.

## Purpose

Extend the accepted Kotlin Android foundation from a tiny user-selected local catalog into the K3 offline-data section: English catalog/rules acquisition or import through explicitly permitted user action, durable consent/source state, full local rules availability for acquired/imported records, and bounded offline search over the installed corpus.

K3 is an internal section, not a public finished-app release. It advances the retained Android v1 requirement inventory without starting Windows v2 or sync.

## Preconditions

1. Read `docs/gauntlet/gates/K2-FINAL-ACCEPTANCE.md`, `docs/gauntlet/V1_KOTLIN_EXECUTION_SPEC.md`, `docs/architecture/KOTLIN_CONTRACTS.md`, `docs/architecture/KOTLIN_OWNERSHIP_MAP.md`, `docs/gauntlet/DATA_MODEL.md`, and `docs/architecture/kotlin-registry.json`.
2. Verify `main` contains K2 accepted base `21bf53f30ca0ddb9c482d5f605a35f82dff7fcc4` or a descendant with continuity green.
3. Claim the orchestrator lease before writes. Claim component locks only for actually delegated owned paths. Do not resurrect K2 catalog/shell locks.
4. Before adding any network acquisition path, resolve the exact source/license/terms question for that source from existing evidence or a narrowly scoped source review. A user-selected local file path remains permitted independently of network acquisition.

## Scope and finish line

K3 finishes when the Android app can maintain an offline English card/rules corpus with truthful provenance and consent state, import or acquire that corpus through a permitted explicit user action, recover atomically from interruption or malformed data, and search the installed data locally without network dependence.

Required behavior:

- English remains the default dataset/language.
- Non-English data is never downloaded merely because a foreign printing or identity is observed; explicit approval remains required before any non-English download.
- Acquisition/import source, observed/as-of information, generation/revision, language, checksum where applicable, and failure state remain distinguishable from unknown values.
- Full rules text needed by the installed corpus is usable offline after the permitted acquisition/import completes.
- Existing active data remains usable while a replacement corpus is staged. Activation is atomic; cancellation or malformed input cannot replace the previous good generation.
- Search remains local, bounded, deterministic and snapshot-aware. Cursor/version behavior may not mix generations.
- Consent is scoped and durable but never inferred from restored preferences, previous file selection, or possession of a foreign card identity.
- Empty, unavailable, stale, importing, cancelled and failed states are truthful UI states, not simulated success.

## Out of scope

- Windows/Flutter implementation.
- Android/Windows synchronization, accounts, cloud identity or conflict resolution.
- Pricing, collection, deck construction, life tracking, recommendation, AI, bracket or validation product modules except interfaces already required by accepted contracts.
- Permanent-key signed public release, Play deployment, Fold7 manual hinge acceptance, final TalkBack/manual performance acceptance. Those remain later stages.
- Broad background download or unbounded refresh behavior.

## Execution steps

1. Reconcile K3-owned requirements and accepted public contracts against the K2 implementation. Name the exact components and owned paths before delegation. Preserve the complete twenty-component public-port inventory and all 64 requirement / 213 parity tracking obligations.
2. Decide the permitted English corpus input path(s) for this section. For any network source, record current official source URL/terms evidence and explicit consent semantics before implementation. Keep local-file import available as a first-class offline path.
3. Implement streaming/bounded parsing, staging and atomic generation activation for the K3 corpus. Preserve structured cancellation, size/record/nesting limits, UTF-8 validation and provenance. Add schema/migration work only through the database owner and obtain durability review for migration/activation semantics.
4. Extend local rules and FTS/search storage so acquired/imported records and rules are queryable offline. Test MATCH token/punctuation behavior, Unicode/case behavior, pagination, stale cursors, close/reopen persistence and generation replacement.
5. Wire truthful consent/source/status/search/rules presentation into the existing shell. Expose only working actions. Externalize English strings and retain adaptive/accessibility behavior established in K2.
6. Run focused JVM tests and packaged Android instrumentation, then the hosted Android matrix on API 23, API 36 and API 36 16 KiB. Retain artifacts/logs needed to prove corpus/import/cancel/search behavior. Treat infrastructure failures separately from code failures and rerun only with evidence.
7. Obtain independent review for source/license conclusions, database durability/migration changes, cross-module contract changes, and final K3 acceptance. Fix blockers and rerun affected checks.
8. Publish the K3 section report and synchronized queue/state/CURRENT/journal/events, release locks/lease, and hand off the next accepted section. Do not silently roll K4 product-module work into K3.

## Acceptance evidence

K3 acceptance requires actual code and retained evidence for: permitted explicit acquisition/import; durable consent/source state; genuine English corpus/rules fixture or source lineage; atomic activation and rollback/cancel behavior; offline rules retrieval; bounded local search and snapshot behavior; process close/reopen persistence; hosted API 23/API 36/API 36-16KiB instrumentation; and an independent final review.

Requirement claims remain scoped. K3 may close foundation/data portions of requirements but must not mark later product or final-release acceptance complete without their own evidence.

## First action after claim

Verify current `main`, claim the lease, then build a K3 requirement/ownership delta from the accepted K2 tree and existing architecture documents before modifying application code or contacting a product-data source.

## K3 requirement and ownership delta (inventory; implementation bindings still require review)

Baseline: `orch-kotlin-k3-offline-data-01.md` and the accepted K2 Kotlin tree. K2 supplied a tiny user-selected English catalog import, name lookup, status, Room schema v1 and shell. K3 is the offline data section; preserve all 20 public component contracts, 64 requirement IDs and 213 parity rows without calling later product or release gates complete.

| Requirement | K3 portion | Retained later/full obligation |
| --- | --- | --- |
| A01 | Complete selected English corpus/printing coverage, gameplay/face/stat fields, supplied legalities/image links and dated rulings; prove source completeness and truthful unknowns. | Game Changer source flags and final complete-product claims remain separately evidenced; no image acquisition or bracket-service scope is implied. |
| A02 | English default; explicit, scoped, durable approval before any additional-language download; foreign identity alone starts none. | Full language-pack preview, pause/resume and foreign inventory flows remain. |
| A03 | Preserve active offline corpus and truthful Full/partial state. | Full/Lean switching, user-data preservation and demand resolution remain. |
| A04 | Import permitted full CR offline with numbered rules, glossary, examples, notices, cross-references, unresolved targets and search; include card rulings in installed corpus. | Authentic source/version/rights and keyword links need evidence; parser-only work cannot close A04. |
| A05 | Text fallback while offline. | Art cache/packs, estimates, eviction, resume and Wi-Fi control remain. |
| A06 | Stage replacement, validate counts/checksum/schema, atomically move active pointer; cancellation/failure retains old corpus; persist source/failure dates separately. | Manual metadata check and update-now/resume when a permitted network source exists. |
| A07 | Bounded local queries needed for installed corpus; report unsupported syntax honestly. | Full typed Scryfall-style syntax, all feature-specific filters (owned, in-deck, price etc.), AST/autocomplete and online action remain. |
| A08 | Offline Oracle/rules text, deterministic pagination, Unicode/punctuation and stale-cursor behavior tied to a snapshot. | Full search/filter behavior and performance P02/P03/P04 remain separately evidenced. |
| K01/K02 | Explicit source/import/consent controls; accurate empty/importing/failed/stale status, dates, notices and offline attribution. | Full onboarding, all module settings, diagnostics and final interface acceptance remain. |
| N01/N02 | Maintain accepted ownership/CI gates and scoped contracts. No network behavior until source review. | Full N01 release gates and N02 shared-client/rate-limit/retry/resume proof when network acquisition is introduced. |

### Accepted contract and implementation gap

- `LocalCatalogContracts.kt`: K2 `CatalogImport` takes `Flow<ByteChunk>` and `CatalogImportLimits` hard-caps input at 8 MiB, printings at 10,000, each record at 128 KiB, batch 128, nesting 32. These caps and the JSON root-array framer cannot process a full bulk feed. `LocalCatalog.kt` holds a `HashMap<OracleId, CardRecord>` and printing-ID set for the whole import; its canonical state scales with corpus size. The tiny K2 array is not full-feed proof. R02 evidence describes a gzipped JSONL bulk contract; verify actual chosen source/format before adapting.
- `CatalogContracts.kt` declares full `CatalogRepository`, `RulesRepository`, `CardSearch`, `RuleEntry`/`RulesQuery`/cross-reference DTOs; K2 implements only separate `CatalogImport`, `CatalogLookup`, `CatalogStatus`. Keep unconsumed aggregate ports as declarations; no partial adapter advertised as their implementation.
- Card-ruling DTO `Ruling` exists, but no actual local ruling import/read capability is registered. Bind truthful local capabilities/storage before claiming A01/A04 rulings.
- `RulesSource.version: String` is required. An unversioned local rules file cannot be assigned a fabricated official version. Define a reviewed truthful import policy/contract change before binding such input. `RulesRepository.entry(RuleId)` lacks a corpus argument; resolve its active-corpus/snapshot binding with reviewers, including replacement between search page and entry lookup. Cursor must reject a generation change rather than mix corpora.
- Room `StorageSchemaDatabase.kt` v1 has catalog generation, active pointer, cards/faces/printings and catalog-name FTS only. No rules corpus/entries/cross-reference tables, rules FTS, or rules implementation/module exists. Durable scoped consent already exists in DataStore settings and should be reused; new corpus/import-job source state needs a reviewed persistence design. Migration, active-pointer and close/reopen durability belong to the storage owner with independent review.
- K2 catalog generation stores separate source date, observed time, checksum and notice; unknown source version remains unknown. K3 must persist explicit import/acquisition intent, scoped consent, status/failure and snapshot lineage without inferring source date or rights from local file metadata.

### Exclusive paths for next scoped work

- External Claude: **only** `android-app/feature/rules/**` and `coordination/handoffs/cc-k3-rules-parser-01.md`, after a rules-component lock. First task: internal pure streaming TXT parser and focused JVM tests mapping accepted `RuleEntry` DTOs; synthetic cases clearly labeled. Enforce byte/line/entry/reference limits, UTF-8 errors, cancellation and retain examples/notices/unresolved cross-references. No database, schema, core DTO, app/UI, network, source download, production wiring, or lease edits. Genuine rules fixture and reviewed source/contract binding gate activation.
- Codex catalog owner: `android-app/feature/catalog/**` for full-feed streaming/identity work. Root orchestrator controls shared `android-app/data/storage/**` migration assignment, `android-app/core/**` public contract decisions, `android-app/app/**`, Gradle/config/registry/CI, final UI integration and task coordination. Registry maps rules to `:feature:rules`, catalog to `:feature:catalog`, search to `:feature:search`, settings to `:feature:settings`, shell to `:feature:shell`, network to `:data:network`; a declared module is not permission to create or wire an unused module.
- Search/settings/shell work is **not delegated now**. Their paths are respectively `android-app/feature/search/**`, `android-app/feature/settings/**`, `android-app/feature/shell/**`; owners receive bounded tasks only after corpus/storage contracts are reviewed. No obsolete K2 lock reuse.

Assumptions/open decisions: user-selected local files are an allowed first input; R02 current catalog terms/format/completeness and R03 CR use/version/actual bytes are unresolved. This inventory makes no legal conclusion and authorizes no product-data download, source contact or network path. Decide genuine feed/CR fixture lineage, unversioned-rules policy, rules entry snapshot semantics and migration ownership before production activation.

Exact next action: Claude may begin the bound internal parser/tests task; Codex selects local-first corpus input formats and the necessary truthful source/version/snapshot contracts before proposing catalog scaling or storage migration. No new public contract/schema binding is accepted by this inventory alone.

## Local-first input decision

K3 starts with explicitly user-selected local cards, separate dated card-rulings input, and rules TXT; file selection permits that import only and never grants remote consent. Keep network acquisition and bundled datasets absent until source-specific rights/contract review. Reuse existing DataStore consent.

Exact metadata probe is retained in docs/verification/k3-input-probe: one public metadata GET,3472 bytes, SHA256f454b3f9df03ae0d4b5c7912ae6748d9d9791b509030a36d70a4e3f79950557b. It advertises7 JSONL/gzip-suffixed feeds but does not prove actual compressed/framing bytes or complete English printing coverage. default_cards explicitly includes a printed-language fallback; all_cards claims every card object/every language. No product download occurred. Do not promote metadata updated_at, file name or fetch time into an unverified dataset as-of date.

Preserve K2 small-file behavior. Implement independent private JSONL framing with finite byte/record/nesting bounds first; production full-feed limits must be based on verified source size/disk/memory and reviewed together with on-disk duplicate/canonical checks. The corpus-sized HashMap/ID set cannot become the full-feed engine merely by increasing8MiB. Handle legal valid source shapes lacking top-level Oracle IDs before claiming all layouts; obtain genuine evidence rather than inventing identity joins.

Next meaningful design boundary: additive bulk budget/coverage semantics; bounded disk-backed staging; rules/rulings/source tables and Room1 migration; truthful unknown source/version capability; snapshot-bound entry/ruling retrieval. Those public-contract/durability changes require independent review before application binding. Parser drafts have no repository/network/UI activation.

## Latest continuation delta

Clean stop requested by Pat at50% usage; orchestrator lease released, no new work started. Main contains shell stream cancellation guard merged9d7e0fd without source changes from tested3ffbb1f: hosted38292178104 passed68 tests/14 ImportController,0 failures/errors/skips; continuity38292178108 passed. Shell lock released.

Catalog branch agent/k3-catalog-framer-01 HEAD01e0c404b71eb9f88add561c391bd18daf31c010 preserved/unmerged with lock. JSONL code86dc00d focused38291004261 passed. Cleanup codeed26b326 independent source review accepted (docs/verification/k3-catalog-cleanup/REVIEW.md); matrix38292134079 still in_progress at stop on JVM + API23/API36/API36-16KiB. First resume action: inspect completed matrix, diagnose any failure or merge exact reviewed/tested source if green, then compare merged source to testedSHA. Do not claim runtime acceptance from source review.

Claude branch agent/cc-k3-rules-parser-01 HEAD209a938/code796c665 remains exclusively reserved/unmerged. Private review returned token-cancellation success bug: CLAUDE-PARALLEL.md and docs/verification/k3-rules-parser/REVIEW.md give exact correction/test. Claude owns repair and handoff; root re-reviews correctedSHA, then handles shared Gradle inclusion/hosted evidence at integration. Do not touch rules paths before reviewed merge.

Confirmed later UI issue: MainActivity default enableEdgeToEdge follows device mode, ShellTheme can select opposite; status/navigation icon appearance needs resolved shell-theme handling + API23/API36 evidence. No implementation assigned. Full-feed budgets/on-disk staging/snapshot/rules-ruling schema/genuine bytes/source rights remain incomplete; private parsers are not full K3 acceptance. All useful code/notes published on named branches; start no new task without resuming this pointer.
