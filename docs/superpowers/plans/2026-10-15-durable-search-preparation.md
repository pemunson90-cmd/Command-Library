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

`LocalCatalogSearchPreparation(database: Database, clock: Clock)` exposes `status(generation: GenerationId, cancel: CancelToken): Outcome<CatalogSearchPreparationState>` and `advance(generation: GenerationId, cancel: CancelToken): Outcome<CatalogSearchPreparationState>`. Explicit generation is mandatory. State contains generation, version1, status ABSENT/PREPARING/READY/FAILED, phase CARD/PRINTING/VERIFY and committed card/face/printing/text/legality counts. Missing source returns NOT_INSTALLED; invalid/removed snapshot is never rebound. ABSENT is not an empty ready index. FAILED is terminal for that projection version; no automatic destructive reset.

Use `withCatalogImportOwner` across an advance, including acknowledgement; do not call an importer initialization while holding the lock. Source metadata must pass SQL byte guards, source provider=scryfall, format/payload version supported, language=en and status ACTIVE/RETIRED. Reject malformed counters/partial cursors. A phase starts with all cursor fields NULL; CARD cursor is provider/oracle/language; PRINTING is provider/printing with language NULL; VERIFY all NULL. No empty-string sentinel.

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

Files: create `LocalCatalogSearchPreparation.kt`, extend store only where needed, add `LocalCatalogSearchPreparationTest.kt`; modify `CatalogGenerationCleanup.kt` and tiny-import cleanup for explicit FTS cleanup.

- [ ] Implement status/advance, source keysets/budgets/phase transitions and complete original/derived/mirror verification; retain internal visibility until contract review.
- [ ] Tests pin cancellation before/after commit, process reopen, batch limits, malformed/oversize optional fields, retired snapshot, source disappearance, corrupt progress, unknown fields, exact complete coverage, duplicate/stale FTS and cleanup exclusion for active/retired originals.
- [ ] Run full catalog tests and affected native compilation at this persistence boundary, review exact source/durability, checkpoint actual evidence.

## Task 3: Real capability binding and importer integration

Public port/consumer and staging integration get separate exact contracts/design review once the complete internal capability is proven. No stub executor, automatic startup backfill or extra network work. Typed compiler/face-printing/context semantics remain a separate reviewed packet. This plan does not claim K3/full-v1 acceptance.

## Design review disposition

Independent reviewer ACKs Task1 with store-owned source reads/projection in the transaction, exact affected-row checks and shared ownership through reconciliation. Creation uses absent prior. Terminal VERIFY is a read-only no-op with no candidate acknowledgement.

FTS addressing is now fixed for Task1: ordinary text insertion and its FTS mirror share the ordinary text table rowid explicitly. Obtain the inserted rowid on the same DbTransaction connection and bind it to the immediately following mirror insert. This permits indexed rowid joins in both directions, exact identity/value checks and duplicate detection without per-fact FTS metadata scans. Normal-text rowids are globally unique across generation/version. Future staging writers must use the same rule. Existing tables are empty before this writer, so no historical rowid backfill is implied.

Task2 HOLD remains until durable bounded verification state is designed: schema9's phase/cursor fields may encode explicit verification subphases and a checked rowid cursor, or a separately reviewed additive schema may be justified. Do not implement READY using unbounded scans or reuse completed source counters as verification cursors. Task1 only reaches VERIFY/PREPARING and cannot claim ready.

## Proposed Task2 verification encoding (review pending)

Avoid a schema10 solely for verification progress. Extend the version1 phase grammar to VERIFY_CARD, VERIFY_FACE, VERIFY_PRINTING, VERIFY_TEXT, VERIFY_LEGALITY and VERIFY_FTS (all <=16 ASCII bytes). In verification phases only, cursor_id is the canonical nonnegative decimal last rowid and cursor_provider is the canonical nonnegative decimal verified-target-row count; cursor_language is NULL. Begin each subphase with both strings "0". Source-phase cursors keep their existing actual provider/ID meanings. No completed source counters are reused/modified as verification counters. This encoding is internal version1 progress, not a source identity or public cursor. Strict decoding rejects signs, leading zeros except0, overflow, partial tuples and count/rowid inconsistencies. VERIFY remains the Task1 handoff state; Task2 atomically enters VERIFY_CARD.

Each verification call examines <=64 rows selected by rowid keyset with no sentinel omission (SQLite generated positive rowids; reject nonpositive source/fact rowids during writing/verification). It commits its subphase cursor/count or next subphase in the same fenced checkpoint transaction; acknowledged READY is only the final such commit. Validate the current cursor points to a real previously checked row before continuing. Source rows and facts are immutable under the shared owner between calls; another prepared generation can add unrelated text/FTS rows, never mutate this generation's facts.

CARD/FACE/PRINTING walk actual original rows and join their matching fact PKs using generation/version/source identity; require one exact fact per original and validate actual field/projection knownness. End counts must equal original metadata AND the stored materialization counters. Derived FK+PK constraints exclude extra card/face/printing identities when every original has exactly one matching fact. TEXT walks ordinary text facts by rowid and joins FTS by the shared rowid, comparing every identity/scope/field/value with NULL-safe equality; validate scope against actual parent and source face/keyword namespace. LEGALITY walks derived legality rows, validates parent/status and count; malformed statuses remain explicit UNKNOWN. Counters cannot alone establish source dictionary completeness; retain writer's exact extraction proof and rederive bounded source records if runtime corruption checking is required rather than claiming count equivalence proves arbitrary raw content.

FTS needs a different scan because its generation metadata is UNINDEXED. Walk the GLOBAL FTS rowid range <=64 rows per call with no generation predicate, inspect bounded metadata, and for this generation/version check the indexed ordinary-text rowid counterpart and exact identity/value. This is linear in total retained FTS rows, not quadratic or an unbounded filtered scan. Do not load unrelated row values; use CASE on generation/version and SQL byte guards. Persist the last global rowid separately from the count of matching target rows. At exhaustion target count must equal text count. Other generations do not become source authority or undergo changes. No runtime/performance acceptance is claimed without measurement.

Readiness reconciliation binds the entire final checkpoint/status, not just counts. A missing/malformed cursor/row, FK contradiction, mismatch or duplicate is CORRUPT_DATA and may mark FAILED using fenced prior state. SQL/transport adapter failure remains resumable, not FAILED. No automatic reset/rebuild/delete. Independently review this encoding and exact coverage proof before implementation.

Verification proposal refinement before review: source/fact rowid walks are GLOBAL keysets too, not generation-filtered rowid ORDER BY that could scan or sort a whole generation each call. Bound global rows inspected64 and source records decoded16/16MiB, plus the same derived/statement/time budgets. Read only bounded current-target values with CASE; unrelated rows advance the global cursor without payload materialization.

Source CARD and PRINTING verification rederive their expected facts from guarded original raw payloads and compare each expected row's complete typed scalar set by indexed PK/exact SQL predicates. They accumulate expected output counts durably, rather than trusting writer counters. Generalize verification cursor_provider to a canonical comma-delimited nonnegative count vector (card,face,printing,text,legality), each <=Long.MAX_VALUE, <=99 ASCII bytes total; cursor_id remains last global rowid. Reset the vector at each subphase and compare only that phase's actual categories against build counters and source metadata at exhaustion. CARD proves expected card/face/text totals, PRINTING proves printing/legality totals. TEXT/LEGALITY/FTS count actual rows and compare against those independently source-validated counters; this excludes extra derived identities/scopes/fields, while exact expected-row comparisons exclude missing/changed rows. Rows other than the target generation/version contribute zero. Canonical "0,0,0,0,0"/"0" starts avoid absent-first-row ambiguity. This replaces the earlier single verified-row count encoding, which was insufficient to independently prove derived fanout completeness.

Task1 final exact-source durability ACK after fixes; fullcatalog25XML251tests0failure/error/skip and native Kotlin test compile SUCCESS30s. Report CATALOG-SEARCH-PREPARATION-STORE-LOCAL-20261015.md. Task2 is design-only until its separate bounded verification review is resolved.
