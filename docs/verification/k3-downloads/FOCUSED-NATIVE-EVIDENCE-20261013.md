# Focused native evidence — 2026-10-13

Run `38463900556`, exact source `db15592ac9c57fa97755d6c471dac283de11d9ce`, failed in `changed-data-flows` after instrumentation APK compilation, emulator boot and connected execution. The artifact `android-native-focused-db15592ac9c57fa97755d6c471dac283de11d9ce` (ID `11847503230`, ZIP digest `sha256:85e2776ead495c3cdd69eb21b5426b455b4d761e3dbf8b2f9443d3ba9cea5f2f`) contains actual connected XML for network and storage. This is focused API36 evidence, not a full matrix or a passing candidate.

| Module | XML tests | Failures | Errors | Skipped | Required cases |
| --- | ---: | ---: | ---: | ---: | --- |
| `data/network` | 3 | 0 | 0 | 0 | All three `AcquisitionWireIntegrationTest` cases executed and passed. |
| `data/storage` | 32 | 4 | 0 | 0 | Both `OriginCooldownMigrationTest` cases executed and passed. |

The four failures are old storage assertions:

- `FullCatalogMigrationTest.actualV3PreservesEveryLegacyColumnAndBackfillsOnlyWholeRelations`, `interruptedColumnAddsRollBackAndActualOpenRetries`, and `danglingCompleteLegacyRelationAbortsBeforeForeignKeysAreEnabled` fail in `assertLegacyPreserved` at line 298. The historical DDL comparison includes the new schema-5 `acquisition_origin_cooldown` table and its autoindex in the actual side. Existing schema-3 DDL otherwise remains compared. Minimal test fix: omit only rows whose `tbl_name = 'acquisition_origin_cooldown'` from that historical equality check, while retaining the dedicated schema-5 table/row assertions in `OriginCooldownMigrationTest`.
- `StorageDatabaseTest.newerSchemaIsRejectedAndCommittedRowsRemain` writes `PRAGMA user_version = 5`, then casts the reopen result to `Outcome.Unavailable` at line 348. Version 5 is now current, so `StorageDatabase.open` succeeds and the cast fails. Minimal test fix: use synthetic future version 6 for the PRAGMA and matching expected version, preserving the unchanged-byte and committed-row assertions.

The workflow's `Verify executed suites` step also failed because it requires zero failures for all storage XML cases, even though the two newly required cooldown cases were present. Do not report the overall focused run as passed. No code fix or rerun was made in this diagnosis. After the two fixture updates, compile locally and rerun the affected native gate; the new cooldown behavior already has two passing API36 native cases in this failed run.

## Fixture correction delta

After this readback, the two test-only corrections were applied locally. `FullCatalogMigrationTest` now excludes only rows whose third `SELECT type,name,tbl_name,sql` column names `acquisition_origin_cooldown` from its historical DDL equality check; all prior table/index DDL and separate schema-5 assertions remain checked. `StorageDatabaseTest` now sets and verifies synthetic future `user_version = 6` while retaining the byte-preservation and committed-row assertions. No build or native rerun was performed for this delta; the failed run above remains failed evidence.
