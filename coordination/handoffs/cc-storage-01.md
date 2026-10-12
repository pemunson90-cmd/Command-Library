# Handoff: cc-storage-01

Checkpoint: cc-storage-01 step 1/4 (the lease holder assigns the global cp-* id)
Owner: Claude Code implementer, Sonnet tier (D-10)
Task: `coordination/tasks/cc-storage-01.md`, parent orch-kotlin-m0-01 step 2
Branch: `agent/cc-storage-01`; base commit `c3abc98` (orchestrator prep, registers `:data:storage`); core contracts as on that base (main `575159ba`, accepted)
Owned paths: `android-app/data/storage/**`, this handoff. Shared files edited: none.
Work status: code (step 1 of 4 committed; build and JVM unit tests run, see Evidence)

## Done

### Step 1/4: module and schema v1
- `android-app/data/storage/build.gradle.kts`: `com.android.library` with AGP built-in Kotlin (no kotlin-android, no kapt), KSP, `androidx.room3`, compileSdk 37, minSdk 23, build-tools 36.0.0, Java 17, `kotlin { jvmToolchain(17); explicitApi() }`, `room3 { schemaDirectory("$projectDir/schemas") }`. Dependencies by catalog alias only.
- `schema/CatalogTables.kt`: seven Room3 entities (internal): `catalog_generation`, `catalog_active`, `oracle_card`, `card_face`, `printing`, `printing_face_asset`, and `catalog_name_fts` (`@Fts5`, unicode61 with `remove_diacritics 2`, four UNINDEXED identity columns). Composite primary keys, foreign keys and indexes per the design; collector number is TEXT. No DAOs.
- `schema/StorageSchemaDatabase.kt`: `@Database(version = 1, exportSchema = true)`; destructive fallbacks are not configured anywhere.
- `SchemaVersionGate.kt`: newer-on-disk-schema protection. Verified Room3 3.0.3 behavior (see Evidence): for a file whose `user_version` is newer than the code, Room throws `IllegalStateException("A migration from N to M was required but not found ...")` from the first connection and leaves file and rows untouched. The gate reads `PRAGMA user_version` read-only first and returns `Outcome.Unavailable(UnavailableReason.UNSUPPORTED, detail)` ("update the app, no data was changed") before Room touches the file.
- `schemas/com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase/1.json` committed (identity hash `3c9df8f3d6ad3fda813b926ddfb7227f`).
- `SchemaVersionGateTest` (5 JVM tests).

## Evidence (commands actually run)

| Command | Result |
|---|---|
| `bash gradlew --no-daemon --max-workers=2 :data:storage:assembleDebug` (from `android-app/`, JDK 17, SDK 37.0) | BUILD SUCCESSFUL; no `w:` or `e:` lines; KSP generated `StorageSchemaDatabase_Impl` |
| `... :data:storage:testDebugUnitTest :data:storage:assembleDebug` | BUILD SUCCESSFUL; `SchemaVersionGateTest` tests=5 failures=0 errors=0 |
| Scratch JVM Room3 probe (outside the repo, `jvm` runtime of the same Room3 3.0.3 sources, srcDir pointing at this module's main sources; NOT the Android runtime) | opened the real schema on real bundled SQLite 3.50.1: `foreign_keys=1` on writer and reader connections (Room's generated `onOpen` runs `PRAGMA foreign_keys = ON`), `user_version=1`, WAL; FTS5 table created with `tokenize=\`unicode61 remove_diacritics 2\``; bound MATCH of a folded phrase hit the row; FK violation raised `SQLiteException: Error code: 787, message: FOREIGN KEY constraint failed`; downgrade open threw the `IllegalStateException` above, `user_version` stayed 2 and the row count stayed 1; the gate classified a live WAL file, a closed file, a version-7 file as `Versioned` and a garbage file as `Unreadable (Error code: 26, file is not a database)` |

## Next (step 2/4)
Database port adapter: parameter parser and binder, row bound, latch-driven transaction runner over Room3 `useWriterConnection` plus `withTransaction(IMMEDIATE)` and `TransactionScope.rollback`, `capabilities()`, public `StorageDatabase.open(context, fileName)` and `close()`. Command: `./gradlew --no-daemon :data:storage:testDebugUnitTest :data:storage:assembleDebug`.

## Gotchas
- Room3 here does not enable `foreign_keys` itself; the generated `onOpen` does (verified).
- Room3 `RoomDatabase.close()` after a failed first open does not throw (verified on JVM runtime).
- Sandbox note for whoever reruns: Maven Central can return HTTP 429; wait and retry.

## Blockers
None.

## Shared-file change requests
None so far.

## Not run
- Instrumentation tests (none exist yet; they cannot run here, no `/dev/kvm`).
- Anything on the Android runtime of Room3 (only the JVM runtime was exercised by the scratch probe).

## Safe fallback
Delete nothing. `git checkout -B agent/cc-storage-01 origin/agent/cc-storage-01` resumes from the last pushed step.
