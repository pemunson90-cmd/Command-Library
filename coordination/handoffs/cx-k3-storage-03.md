# cx-k3-storage-03 storage handoff

## Latest continuation delta

Branch `agent/cx-k3-storage-03`, preserved worktree `/workspace/scratch/Command-Library-storage-03`, base `049dec7eacd21f2426a590f1d65cdbeafe979967`. This handoff accompanies the implementation commit; its exact head is available from branch history. Owned changes only: `AcquisitionTables.kt`, `Migration2To3.kt`, three storage `src/androidTest` files, this handoff. All 13 existing entities, schema exports and root registry/configuration files remain untouched. Jobs-only infrastructure; no K3/full-app acceptance claim.

Implemented the independently approved three-table physical schema using explicit Room3 `ColumnInfo` names, the existing suspend migration API, five named indexes, per-job seen-ID primary key, unique private partial/artifact keys, and acquisition-only child cascades. There are no extra defaults or constraints absent from the Room entity shape. Existing migrations and entities are preserved.

Preserved and completed `AcquisitionStorageMigrationTest` with synthetic sentinel rows seeded from committed actual KSP `2.json` DDL/indexes/setup queries and identity. Corrected missing `oracle_provider` / `oracle_language` sentinel mappings so the legacy printing composite foreign key resolves. Native cases authored:

- `actualV2MigrationPreservesLegacyDataAndDurableAcquisitionRows`: actual `StorageDatabase.open` upgrade, every legacy column/DDL unchanged, UTF-8 attempt payload bytes including embedded NUL, active pointers/FKs/FTS preserved; >32-bit paused offsets/counters and completed artifact metadata survive reopen; seen-ID and private-key constraints; acquisition-only cascade deletion; integrity/foreign-key checks.
- `staleOwnerCasExplicitAbortRollsBackPrecedingSeenInsertEvenWhenCaught`: successful zero-row SQL leaves the transaction active until caller aborts; both propagated and caught abort latch cases roll back preceding seen-ID insert and retain owner/checkpoint across reopen.
- `interruptedMigrationRollsBackPartialDdlAndCanRetry`: injects cancellation after four migration DDL statements in an explicit transaction; rollback preserves v2 version/identity and removes all new DDL; actual Room open then retries 2->3 and preserves legacy data. This is a transaction interruption test, not a simulated device power loss.
- `recoveryPrimitivesFenceInterruptedOwnerAndRetainJobWideBudget`: storage SQL recovery preserves paused/cancelled/completed/queued rows and retained artifact, fences old owner, atomically resets attempt/IDs/checkpoints while retaining conservative transferred-byte accounting. This tests storage primitives, not production runner/filesystem/consent coordination.

Updated existing `RulesStorageMigrationTest` to exercise 1->2->3 and verify all acquisition tables are created; existing `StorageDatabaseTest` now expects version3 and uses version4 for newer-schema refusal. Old v1/v2 fixtures/DDL and exported identities were not modified.

Verification actually run:

- `git diff --check`: pass.
- Private Python `sqlite3` checks: pass for all 13 real-v2 synthetic fixtures/FKs, every legacy row/table/index/FTS DDL preservation, interrupted additive DDL rollback, explicit entity column/affinity/nullability matching, reviewed proposal table/FK/index shape matching, uniqueness/FK rejection, isolated cascades, explicit zero-row CAS rollback, and 64-bit interruption/reset budget retention. These are non-Android checks, not Room acceptance evidence.
- `python3 /tmp/k3-storage-compile/compile.py` from this worktree: direct Kotlin 2.2.10 `K2JVMCompiler`, JVM target17, all 27 core/model + core/common + storage main + storage androidTest sources compile with exit0 and no diagnostics. Uses real downloaded Room3 runtime/common3.0.3, SQLite/bundled2.7.1, Android test junit1.3.0/monitor1.8.0, Android API Maven4.1.1.4, org.json and existing coroutine/JUnit artifacts. Checks real signatures/syntax; no stubs or generated Room implementation. Private artifacts/log/script are under `/tmp/k3-storage-compile`; they are not repository exports or durable acceptance evidence.
- `not_run`: Android Gradle compilation, KSP schema3 export, connected Android instrumentation and physical/device power-loss recovery. Local Android SDK is absent. All authored native test cases above remain unrun.

Exact root-owned integration requests / next action:

1. Add `AcquisitionJobEntity::class`, `AcquisitionArtifactEntity::class`, `AcquisitionSeenIdEntity::class` to `StorageSchemaDatabase.kt`; set `StorageSchema.VERSION=3`.
2. Import `Migration2To3` in `StorageDatabase.kt` and register `.addMigrations(Migration1To2, Migration2To3)` without destructive fallback.
3. Run actual hosted KSP compilation and commit its real `schemas/...StorageSchemaDatabase/3.json`; verify all 13 legacy exported entities are unchanged. Never fabricate schema3 identity/export.
4. Run the storage Android tests (including both migrations through actual open) on the hosted API23/API36/API36-16KiB lanes and review migration/durability evidence before acceptance. Keep real runner file/consent exclusion tests separate from these SQL primitive checks.
