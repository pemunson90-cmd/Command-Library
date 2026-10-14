# Independent ruling storage and local reads

Status: independent durability design ACK; implementation in progress under D-17/D-28. Parent orch-kotlin-k3-offline-data-01. Builds on reviewed fe48e56 contracts. No importer, acquisition authority, DI, UI or network action in this unit.

Design reviewer: rulings_contract_review (Astra high), read-only, no blockers. Actual Database.transaction uses IMMEDIATE writer connection; bounded snapshot reads briefly serialize writers. Catch stored-data validation inside its callback as CORRUPT_DATA and flatten typed nested outcomes; do not let storage misclassify them as user input errors.

## Physical data

Add schema7 with three tables only; preserve every schema6 entity and opaque life/archive payload. Register Migration6To7 in the actual Room builder and commit its real KSP export. No destructive fallback or old-table rewrite.

- rulings_generation: generation_id TEXT primary key; provider, status, language_scope, provenance_payload TEXT; record_count INTEGER; payload_schema_version INTEGER. No card-language assertion. Full Provenance preserves known/absent/unknown metadata, evidence, timestamps, notice/license and nullable checksum/source without fabrication. Add a new private acquisition-codec envelope type for Provenance, reusing its strict/bounded/Unicode/number helpers without changing existing formats. Reader metadata bound128KiB. Generation identity bound512UTF8bytes permits a complete bounded cursor; importer must use the same storage bound later.
- ruling_record: primary key(generation_id,ordinal); oracle_provider, oracle_id, publisher, published_day, record_payload TEXT; ordinal INTEGER. FK generation CASCADE. No FK to cards/printings: unmatched Oracle IDs survive independent card replacement. Index(generation_id ASC,oracle_provider ASC,oracle_id ASC,published_day DESC,ordinal ASC), using actual Room Index.Order support. Raw source record remains bounded128KiB and redecoded with the actual ScryfallRulingRecordCodec; projection values must agree. Identical content at distinct ordinals remains distinct.
- rulings_active: singleton_id INTEGER primary key (repository uses1), generation_id TEXT FK generation NO_ACTION and indexed. Protect active parent deletion. Future importer must stage/verify/finalize record_count then atomically switch status/pointer; it is not implemented or claimed here. No premature attempt/job owner fields or fake staged-success adapter.

Rulings never enter catalog_active/LocalCatalogStatus.activeGenerations: a ruling-only corpus must not make English cards appear installed. No schema-level fabricated nonempty corpus assumption; count0 is valid installed absence of rows.

## Local reader

Internal LocalCatalogRulings(Database) implements CatalogRulings. No automatic initialization, network or writes. Run each status/page read in one Database.transaction for a consistent active-pointer/metadata/row snapshot. Query pointer separately; missing generation, bad singleton, nonACTIVE status, wrong provider/scope/schema, inconsistent provenance/count or malformed payload are CORRUPT_DATA, not empty success. Distinguish actual absence(status Success(null), rulings NOT_INSTALLED) from installed/no matching Oracle(empty real page). Propagate typed database outcomes and cancellation unchanged.

Validate requested Oracle canonical Scryfall UUID before reading. Cursor is versioned bounded strict UTF8 hex of generation plus qualified Oracle and full(day,ordinal) order key. Reject malformed/wrongOracle INVALID_INPUT; valid cursor for another active generation STALE_GENERATION. Stable order publication day DESC, ordinal ASC. Snapshot source=scryfall, active generation, revision=null, language=null.

Read at most limit+1 small identity rows, then one payload at a time, with SQL byte-length/CASE guards before transferring oversized text. At most2MiB retained raw-record bytes per page (existing BoundedRecordBatches bound), next cursor from the last returned row, never skip a row at byte cut. Validate raw record and every row projection, positive ordinal, bounded generation, valid date. No full corpus COUNT scan per page: record_count is finalized by future atomic importer. Full-corpus integrity is not claimed by a page reader.

## Checks at this persistence boundary

Batch schema, real local reader, codec and meaningful regression tests. Use actual bundled SQLite for migration/payload preservation/rollback/reopen/FKs/query-plan and keyset paging; no fake SQL evaluation. Migration tests start with actual exported6schema and retained life/acquisition payloads. Add an actual Room6-to7 open/reopen instrumentation probe, compile it locally; device execution remains not_run until a later justified integration run. Existing historical5-to6 tests remain unchanged.

One broader local check/lint/all-test-APK/debug-APK boundary after implementation and independent exact-source durability review. Compare actual KSP6/7: all24old entities byte-for-byte equal, exactly3new entities. Keep native frozen12f8980/run38623690607 separate; that earlier source cannot prove this schema. Do not retrigger it. No finished APK/full-v1 acceptance claim.
