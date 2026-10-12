# Task orch-kotlin-k3-offline-data-01

Status: in progress, step1 requirement/ownership reconciliation. K2 foundation remains accepted. No K3 application implementation or product-data acquisition has started.

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

## Latest continuation delta

Verified main6b68906 is a descendant of accepted K2 base21bf53f; continuity38281170275 succeeded. Catalog638caba and Claude shell/app are already integrated and K2 accepted; obsolete component workers/locks are not resumed. Corrected the DATA_MODEL precondition path. Read only this task-linked scope under updated documentation policy. Next build/review the K3 requirement and exclusive-path delta before application/source writes; no new component lock yet. Verification: accepted evidence reused, no duplicate Android matrix or application changes.
