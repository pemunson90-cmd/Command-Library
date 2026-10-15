# Catalog search preparation transaction store — local2026-10-15

Status: internal transaction primitive implemented and independently reviewed; no public preparation capability or READY/query claim. Source parentfd2ccb7 on agent/cx-thread-recovery-20261013. Root sole writer/final merge authority.

## Implemented boundary

Internal CatalogSearchPreparationStore reads guarded source/progress inside one actual Database transaction and projects at most16 original records per call. It validates installed English Scryfall proof, explicit ACTIVE/RETIRED generation, payload/parser version, original IDs, canonical digest, actual face count/index/provider ID/name/raw payload and retained printing scalar/direct Oracle relation. Original payloads, source provenance and active pointers remain unchanged.

Committed facts/text/FTS and keyset cursor/counters advance together. Initial source scan has no empty-string sentinel. Data phases CARD→PRINTING→VERIFY remain PREPARING; terminal VERIFY does no derived writes. There is no complete preparer status API, readiness certification, automatic backfill, new-import integration, compiler/DI/UI or finished APK.

Ceilings: source record8MiB, batch16records/16MiB fetched raw UTF8; projector1024rows/8MiB derived strings; batch4096derivedrows/16MiB derived strings and conservative4096SQL operations including reads/creation/checkpoint overhead. SQL CASE caps the next raw payload to the remaining allowance, with separate absolute oversize and batch-exhaustion flags. A valid next record that does not fit remains unconsumed. Soft250ms checked between complete records, cancellation inside fanout/between SQL operations. Existing bounded parsers remain noncancellable; additional bounded decoding for retained-field validation is not performance acceptance.

Every insert/checkpoint CAS requires one affected row. Unexpected duplicate facts fail rather than replace. Normal text rowid is explicitly reused for its FTS mirror on the same transaction connection, enabling indexed exact bidirectional verification later. Neither source ID nor source text becomes a SQL identifier.

Shared catalog owner remains held through exact <=500ms NonCancellable readback after ambiguous commit. Candidate is captured only after final CAS and active rollback latch. Intended checkpoint proves commit; exact prior preserves failure/cancellation; anything else is Unknown with no replay. Callback returns typed Outcome rather than leaking catalog-specific exceptions into production storage classification.

## Review and verification

Independent design ACK for bounded store-owned source reads and transaction primitive. Review caught/fixed typed-outcome fixture discrepancy, omitted creation overhead, incomplete face/printing binding and new scalar byte limits. Retained scalar validation uses SQLite NULL-safe equality bits without duplicate materialization or narrower field admission. Display metadata guard covers the installed-reader512-character model; current import adapters independently cap512UTF8bytes. Unicode test is explicitly a retained legacy-row compatibility probe, not a changed importer admission claim.

Actual-export9 fixture loads real35-entity KSP export, FKs/FTS and file reopen; does not reuse/relabel historical schema3-based BulkSqlTestDatabase. Fault injection distinguishes precommit rollback, failed SQL/rollback latch, postcommit lost acknowledgement and unavailable readback.

Final local boundary SUCCESS30s:25XML251tests0failure/error/skip (236prior+15new), full catalog unit tests and native Kotlin test compilation;42tasks3executed39up-to-date. XML retained /tmp/catalog-search-preparation-r2-xml, log /tmp/catalog-search-preparation-boundary-r2.log. Independent exact-source durability ACK after fixes; no remaining Task1 blocker. Earlier14focused tests passed; initial15th regression incorrectly attempted600UTF8byte display through current512byte importer and was corrected to a legacy-row fixture. No production admission change was made for that test.

## Remaining

Task2 bounded verification subphase/cursor encoding and exact FTS/original coverage need independent design review before READY implementation. Proposed encoding/linear global FTS rowid walk is in2026-10-15-durable-search-preparation.md. Failure-state lifecycle, cleanup integration, complete capability/root consumer and new-import staging integration follow; none are implied by this primitive.

Frozen native9c1573b/run38657175303 failed8legacy test-snapshot assertions and remains preserved; no repeat scheduled. No device/live-corpus/performance/physical Fold7/TalkBack/release evidence from this unit. Fullv1 remains incomplete.
