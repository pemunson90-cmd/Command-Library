# Durable catalog search preparation implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline. Root is the sole writer; independent read-only durability review is required. D-17 delegates routine design; D-28 batches related checks. No routine permission pause.

**Goal:** Materialize complete version1 search facts from retained immutable English catalog generations in bounded, resumable transactions.

**Architecture:** Catalog owns extraction and progress in the existing schema9 database. Keep the first implementation internal until the entire preparation capability works; no CardSearch stub or DI binding. Existing literal readers continue independently.

**Tech stack:** Existing Kotlin, Database/DbTransaction, bundled SQLite, schema9 and CatalogSearchProjector. No new dependency or schema change.

**Spec:** `2026-10-15-indexed-query-projection-design.md`, current K3 task and KOTLIN_CONTRACTS.

## Constraints and review focus

- Source rows remain authoritative and unchanged; requested generation never silently changes.
- A post-commit acknowledgement failure must not replay a committed batch. A pre-commit failure must not advance its cursor.
- Payloads whose IDs differ from their stored source identity fail before any derived write.
- Every text fact has exactly one matching FTS mirror, including known empty text; stale/duplicate mirrors prevent readiness.
- A single valid large record must fit a fresh batch; batch exhaustion commits only a complete prefix and resumes at the next source row.
- Retired snapshots remain eligible, STAGING/FAILED originals do not; preparation never activates or deletes source data.

## Internal capability and state machine

`CatalogSearchPreparationStore(database: Database, clock: Clock)` exposes internal `status(generation: GenerationId, cancel: CancelToken): Outcome<SearchPreparationCheckpoint?>` and `advance(generation: GenerationId, cancel: CancelToken): Outcome<SearchPreparationCheckpoint>`. Explicit generation is mandatory. NULL status means an installed generation has no build, never an empty ready index. Checkpoint contains generation, implicit projection version1, PREPARING/READY/FAILED, source/verification phase, actual committed counts and bounded diagnostic. Missing source returns NOT_INSTALLED; invalid/removed snapshot is never rebound. FAILED is terminal for that projection version; no automatic destructive reset. The public capability remains a separately reviewed next packet.

Use `withCatalogImportOwner` across an advance, including acknowledgement; do not call an importer initialization while holding the lock. Source metadata must pass SQL byte guards, source provider=scryfall, format/payload version supported, language=en and status ACTIVE/RETIRED. Reject malformed counters/partial cursors. A phase starts with all cursor fields NULL; CARD cursor is provider/oracle/language; PRINTING is provider/printing with language NULL; initial VERIFY handoff all NULL; verification subphases use the distinct grammar below. No empty-string sentinel.

Build creation and each phase transition are bounded transactions. Each data transaction processes <=16 source rows and <=16MiB raw UTF8, <=4096 emitted rows/16MiB derived UTF8 and <=4096 write statements including progress overhead. Per-record projector limits remain1024rows/8MiB strings/2048 conservative statements. Fetch one original payload at a time using CASE byte guards plus explicit oversize flags. Check cancellation within projector loops and between SQL operations. Soft250ms applies between records, not as a fabricated parser deadline. If a projected record would exceed the remaining batch budget, leave that record unconsumed and commit the earlier prefix. First-record oversize is an explicit failure, never a zero-progress success loop.

CARD emits only card/face/text/FTS rows; PRINTING emits only printing/legality rows. Bind projection generation/provider/Oracle/printing identity to the actual stored row before insertion. CARD validates original canonical digest and actual face identities/count against decoded card; PRINTING validates direct relation, language, set and retained printing properties. Do not infer a whole Oracle for reversible printing-only records. Do not claim per-record origin cryptographic proof: generation SHA is the original stream hash.

All row inserts and build cursor/count increments commit together. Fixed table/column names come only from the projector enum, never source text. INSERT rather than REPLACE catches unexpected duplicate facts. Each advancement compares the complete prior build row (including nullable cursor and counters) before updating; a mismatching checkpoint aborts. READY performs source identity coverage, original card/face/printing count checks, derived counters, no extra facts and exact bidirectional text/FTS identity+value correspondence with duplicate detection. This may require bounded verification subphases rather than one unbounded scan; do not announce READY until every check has completed. Query-plan/runtime budgets require measured follow-up; no physical-performance claim.

Before the transaction returns, retain its exact intended checkpoint. On an ambiguous outer result/cancellation after that point, perform <=500ms NonCancellable exact checkpoint readback. Exact intended checkpoint proves success; exact prior proves no advancement and preserves the original error/cancellation. Any other result is Unknown, never a replay. No cancellation callback/check after acknowledged commit. Fresh invocation reads the actual durable checkpoint. Database's rollback latch remains authoritative; never swallow a failed SQL operation.

Deterministic malformed/unsupported/budget failures roll back the data transaction and can mark FAILED in a separately fenced transaction with a fixed bounded diagnostic. Persistence/transient failures leave the prior PREPARING checkpoint resumable. Failure-mark acknowledgement has the same reconciliation policy. No raw source text in diagnostics.

Existing generation cleanup must explicitly reclaim `catalog_search_text_fts` under its current source-generation eligibility and bounded page limit before original deletion; ordinary facts cascade. No retained/active generation is newly eligible for cleanup. New-import staging integration is a subsequent packet, not claimed by this legacy-preparation implementation.

## Task 1: Actual schema9 durable batch store

Files: create `feature/catalog/.../CatalogSearchPreparationStore.kt`; test `.../CatalogSearchPreparationStoreTest.kt` and `.../SearchProjectionSqlDatabase.kt`. Paths are under `android-app`, standard Kotlin package directories. The test database loads actual KSP9 JSON, enables FKs, implements Database rollback/cancellation semantics and supports fault injection before writes/after commit/readback plus file reopen. Do not relabel the older schema3-based BulkSqlTestDatabase.

- [x] Implement internal checkpoint model, guarded source/build reads, complete-checkpoint fenced update and atomic insertion of already source-bound per-record projections. First deliverable is a complete internal transaction primitive, not a public preparer placeholder.
- [x] Actual SQLite tests prove ordinary+FTS atomicity, duplicate rejection, source identity binding, stale checkpoint rejection, no source mutation, rollback, exact acknowledgement reconciliation and reopen.
- [x] Run focused new tests plus affected catalog compilation; obtain exact-source durability review and publish a coherent checkpoint.

## Task 2: Bounded preparation coordinator and full readiness

Files: extend `CatalogSearchPreparationStore.kt` with complete internal status/advance and verification; create `SearchVerificationPosition.kt`, `SearchVerificationPositionTest.kt`, `CatalogSearchReadinessTest.kt`; modify `CatalogGenerationCleanup.kt` for explicit FTS cleanup. Tiny-import staging integration/cleanup is deferred: this packet only creates ACTIVE/RETIRED derived facts, and a later staging writer must establish bounded reclamation before producing staging FTS.

- [x] Implement status/advance, source keysets/budgets/phase transitions and complete original/derived/mirror verification; retain internal visibility until contract review.
- [x] Tests pin cancellation before/after commit, process reopen, batch limits, malformed/oversize optional fields, retired snapshot, source disappearance, corrupt progress, unknown fields, exact complete coverage, duplicate/stale FTS and cleanup exclusion for active/retired originals.
- [x] Run full catalog tests and affected native compilation at this persistence boundary, review exact source/durability, checkpoint actual evidence.

## Task 3: Real capability binding and importer integration

Public port/consumer and staging integration get separate exact contracts/design review once the complete internal capability is proven. No stub executor, automatic startup backfill or extra network work. Typed compiler/face-printing/context semantics remain a separate reviewed packet. This plan does not claim K3/full-v1 acceptance.

## Design review disposition

Independent reviewer ACKs Task1 with store-owned source reads/projection in the transaction, exact affected-row checks and shared ownership through reconciliation. Creation uses absent prior. Terminal VERIFY is a read-only no-op with no candidate acknowledgement.

FTS addressing is now fixed for Task1: ordinary text insertion and its FTS mirror share the ordinary text table rowid explicitly. Obtain the inserted rowid on the same DbTransaction connection and bind it to the immediately following mirror insert. This permits indexed rowid joins in both directions, exact identity/value checks and duplicate detection without per-fact FTS metadata scans. Normal-text rowids are globally unique across generation/version. Future staging writers must use the same rule. Existing tables are empty before this writer, so no historical rowid backfill is implied.

Task2 HOLD remains until durable bounded verification state is designed: schema9's phase/cursor fields may encode explicit verification subphases and a checked rowid cursor, or a separately reviewed additive schema may be justified. Do not implement READY using unbounded scans or reuse completed source counters as verification cursors. Task1 only reaches VERIFY/PREPARING and cannot claim ready.

## Task2 bounded verification design (latest)

No new schema is needed. Extend version1 phase grammar to VERIFY_CARD, VERIFY_FACE, VERIFY_PRINTING, VERIFY_TEXT, VERIFY_LEGALITY and VERIFY_FTS (<=16 ASCII bytes). Existing VERIFY is the Task1 handoff. At each verification subphase, cursor_provider stores a canonical five-count vector `card,face,printing,text,legality` (five nonnegative Long values, <=99 ASCII bytes). cursor_id stores the canonical signed Long last GLOBAL rowid, or NULL before the first page; cursor_language stays NULL. Zero vector+NULL is the initial state. This phase-specific grammar is distinct from original provider/ID cursors and public search cursors. Reject signs/leading zeros in count values, overflow, leading zeros/plus/negative-zero in rowid; initial NULL watermark requires zero counts.

The global rowid is a resume watermark, not an assertion the row still exists. Cleanup can legitimately delete an unrelated STAGING/FAILED row between calls. Validate encoding and exact checkpoint fencing; never fail the retained target merely because that unrelated watermark row disappeared. SQLite rowid reuse for other generations is irrelevant: the target's originals/facts are immutable while verifying. First-page SQL omits the rowid predicate entirely; later pages use `rowid>:watermark`. This includes negative/zero rows. A target row with nonpositive rowid fails visibly; an unrelated such row may advance a signed watermark without becoming target authority.

All source/fact/FTS walks use GLOBAL rowid keysets without generation filters, avoiding unindexed generation+rowid scans or full sorts on schema9. Inspect <=64 global rows/call and decode <=16 target source records/16MiB fetched raw UTF8, with the same per-record/batch derived/4096SQL/time limits as materialization. CASE only returns guarded target payload/scalars; unrelated row values are not materialized. A payload that cannot fit the remaining allowance stays unconsumed. Only completed rows advance watermark/counts. A final soft250ms boundary cannot certify unexamined rows.

CARD and PRINTING rederive exact expected facts from guarded source raw records using the reviewed mapper and original binding checks. Compare complete expected typed scalars by indexed source/derived PK predicates. CARD accumulates expected card/face/text totals; PRINTING accumulates printing/legality totals. At phase exhaustion require these independently rederived totals equal build counters and corresponding original metadata. No existing completed counters are repurposed as verification progress. This excludes trusting corrupt progress to prove readiness.

TEXT walks actual ordinary text rows and joins FTS by explicit shared rowid, requiring exact identity/scope/field/value. Its actual count must equal the independently source-verified text counter. LEGALITY validates actual parent/status/count against independently source-verified legality total. Expected-row comparison from the source phases proves no expected field is missing or changed; actual derived counts and FK/PK identity coverage exclude extra facts/scopes/fields. FACE coverage similarly checks actual originals/facts against source-rederived face total and metadata. Every source row remains unchanged.

FTS walks GLOBAL rowid pages with no generation filter (identity columns are UNINDEXED). For target generation/version, require ordinary-text counterpart at identical indexed rowid and exact identity/value; count must equal verified text count. Do not return unrelated values. This is linear in retained rows, not repeated whole scans per text fact. No measured physical performance acceptance is implied.

Each verification cursor/vector/subphase transition is atomically fenced/committed and exact-acknowledged under the existing owner. Final READY is a distinct status-bearing checkpoint after every subphase passes. A deterministic validation mismatch can fence-mark FAILED with fixed bounded diagnostic; transient persistence failures preserve resumability. Commit reconciliation compares full checkpoint/status, not counts alone. No callback or cancellation check after acknowledged READY. Source-generation FTS cleanup remains under its existing eligibility and bounded page limit; no new retained-generation deletion/reset policy.

Task1 final exact-source durability ACK; fullcatalog25XML251tests0failure/error/skip and native Kotlin test compile SUCCESS30s. Report CATALOG-SEARCH-PREPARATION-STORE-LOCAL-20261015.md. Task2 design and exact-source durability reviewed; fullcatalog27XML269tests0failure/error/skip, lint/native/appcompile SUCCESS1m14s. Root regression evidence is recorded separately in the readiness report.
