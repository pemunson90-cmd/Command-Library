# Focused native evidence — 2026-10-13

Corrected run `38465889103`, exact source `341b7194df68e285cf4bef159da4d4e66c4e34a5`, completed **success** in `changed-data-flows` on one API36 emulator. The retained artifact `android-native-focused-341b7194df68e285cf4bef159da4d4e66c4e34a5` is ID `11847843523`; its downloaded ZIP SHA-256 is `769f91e57a5a4c3e1d290946437af0e6dd5c805926bb001b24b391d05c46f148`, matching GitHub artifact metadata. The actual XML was parsed from that ZIP, including testcase children as well as suite attributes.

| Module | Executed | Failures | Errors | Skipped | Exact required cases |
| --- | ---: | ---: | ---: | ---: | --- |
| `data/network` | 3 | 0 | 0 | 0 | All three distinct `AcquisitionWireIntegrationTest` methods executed and passed. |
| `data/storage` | 32 | 0 | 0 | 0 | Both distinct `OriginCooldownMigrationTest` methods executed and passed. |

The four previously failing storage methods also executed and passed: `FullCatalogMigrationTest.actualV3PreservesEveryLegacyColumnAndBackfillsOnlyWholeRelations`, `interruptedColumnAddsRollBackAndActualOpenRetries`, `danglingCompleteLegacyRelationAbortsBeforeForeignKeysAreEnabled`, and `StorageDatabaseTest.newerSchemaIsRejectedAndCommittedRowsRemain`. The fixture corrections exclude only schema-5 cooldown DDL from the historical schema-3 equality assertion and use synthetic future version 6; legacy data/DDL, rollback and committed-byte assertions remain in those methods.

Prior run `38463900556` at `db15592ac9c57fa97755d6c471dac283de11d9ce` was **failed**, not accepted: network 3/3 passed, storage 32 executed with four fixture failures and zero errors/skips; its artifact ID was `11847503230`. The corrected run above closes those four observed fixture failures. It does not establish a full API23/API36/16-KiB matrix, production publisher TLS, source terms, full app acceptance or physical-device behavior. The wire fixture uses a real local socket and synthetic URL rewriting, as stated in `AcquisitionWireIntegrationTest`.
