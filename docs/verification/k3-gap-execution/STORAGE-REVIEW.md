# Independent additive rules storage review

Root reviewed worker candidate a866fd9 against accepted schema1 export, StorageDatabase transaction/open path, entity declarations, explicit Migration1To2 and migration test. Source inspection only; hosted KSP/native checks still required.

Verdict: source acceptable as a candidate, not durability acceptance. No catalog1 entity/column is altered or deleted; ten migration statements add rules tables/indices/FTS. Room owns transaction and schema identity validation; explicit migration registered; no destructive fallback. Newer-schema admission remains closed. Failed import attempts remain independent of deleted staging through attempt_payload. Full typed payloads preserve unknown metadata; nullable projections do not fabricate versions/dates.

Entry generation cascade and unique corpus ordinal are enforced. Reference source FK cascades, while resolved targets retain unknown/external evidence and are validated by adapter codec. Active pointer FK protects deletion; singleton id1 and FTS cleanup are repository invariants requiring adapter review. No automatic FTS trigger exists, so each staged entry and FTS row must share one transaction and cleanup must target only uncommitted generations.

The native migration test creates real exported schema1/Room identity, inserts sentinel values across every legacy table/column, compares all values after migration and reopen, and exercises rules FTS/constraints/activation rollback/cascade/audit. SQLite smoke is reported by worker; root did not rerun that smoke. Actual bundled SQLite/Room migration schema validation and native execution remain pending.

Required before integration acceptance: hosted KSP generated schema2 artifact with authentic identity; compare exported schema/migration DDL; native migration/reopen/FTS test passes in required Android matrix. No genuine corpus or final K3 claim.
