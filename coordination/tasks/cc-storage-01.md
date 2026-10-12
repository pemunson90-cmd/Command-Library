# Task cc-storage-01

Status: preserved step1/4; resumed by cx-storage-01 after expired Claude orchestration lease. Original branch is retained, not overwritten. Do not write this component while the current storage lock names cx-storage-01. Parent: orch-kotlin-m0-01 step2.

## Ownership

- Branch `agent/cc-storage-01`, based on the orchestrator prep commit on that branch (shared Gradle files already include `:data:storage` and the catalog entries below). Do not edit shared files: `android-app/settings.gradle.kts`, `android-app/build.gradle.kts`, `android-app/gradle/**`, `android-app/app/**`, `.github/workflows/**`, `android-app/database/registry/**`, `config/**`, `coordination/**` except this task's handoff.
- Writable: `android-app/data/storage/**` and `coordination/handoffs/cc-storage-01.md`. Lock: `coordination/locks/storage.lock`.
- Core contracts are accepted and frozen: read, never edit, `android-app/core/**`. If a contract blocks you, stop and record it in the handoff.

## Read first

1. `docs/architecture/K2-FOUNDATION-DESIGN.md` sections "Storage schema version 1" and "Bound local lookup" (table list, integrity rules, transaction semantics).
2. `android-app/core/model/src/main/kotlin/com/architectpsg/commandlibrary/core/model/InfrastructureContracts.kt` lines 64 to 150 (`SqlValue`, `SqlRequest`, `RowData`, `DbCapabilities`, `DbTransactionState`, `TransactionAbort`, `DbTransaction`, `Database`) and `CatalogContracts.kt` / `Values.kt` for field types the tables persist.
3. `docs/gauntlet/gates/K2-TUPLE-PROBE.md` (proven build setup).
4. `docs/architecture/DATA_MODEL.md` for record fields, null/unknown and identity rules.

## Verified Room3 3.0.3 facts (from the published source jars; do not assume Room 2 APIs)

- Package `androidx.room3`: `@Entity`, `@Fts5(tokenizer = TOKENIZER_UNICODE61, ...)`, `@Database(entities, version, exportSchema)`, `@Dao`, `@Query`, `@Insert`, `Room.databaseBuilder(context, Klass::class.java, name)`, `RoomDatabase.Builder.setDriver(BundledSQLiteDriver())`, `setQueryCoroutineContext`.
- Transactions: `RoomDatabase.useWriterConnection { transactor -> ... }`, `Transactor.withTransaction(SQLiteTransactionType.IMMEDIATE) { ... }`, shorthand `RoomDatabase.withWriteTransaction { }` / `withReadTransaction { }`. Inside, `TransactionScope<T>.rollback(result: T): Nothing` and `PooledConnection.usePrepared(sql) { stmt -> ... }`. No Room 2 `withTransaction`.
- Gradle: plugin `androidx.room3`, extension `room3 { schemaDirectory("$projectDir/schemas") }`. KSP processor `androidx.room3:room3-compiler`.
- Driver: `androidx.sqlite.driver.bundled.BundledSQLiteDriver` (bundled SQLite 3.50.1 observed, FTS5 present). `SQLiteStatement` binds by 1-based index.

## Parameter convention (cross-module; catalog will rely on it)

Statements use SQLite named parameters `:name` only. Each `SqlParameter.name` is the identifier without the colon. SQLite numbers distinct named parameters by first appearance; the adapter must map names to indexes the same way, ignoring `:` inside string literals and comments. Missing, unused, duplicate or positional (`?`, `?NNN`) parameters are a typed non-success Outcome (latched inside a transaction), never a crash. `maxRows` is enforced by stepping at most `maxRows` rows; a statement that would return more returns exactly `maxRows` (the caller fetches `limit + 1` to detect a next page). Record the final rule in the handoff for the catalog worker.

## Steps (N = 4)

1. **Module and schema v1.** `android-app/data/storage/build.gradle.kts`: `com.android.library` (AGP built-in Kotlin, no `kotlin-android`, no kapt), KSP, `androidx.room3`, `compileSdk 37`, `minSdk 23`, Java 17, `kotlin { jvmToolchain(17); explicitApi() }`, `room3 { schemaDirectory("$projectDir/schemas") }`, dependencies via the version catalog aliases (room3-runtime, ksp room3-compiler, sqlite-bundled, `project(":core:model")`, `project(":core:common")`). Room entities for the seven tables (`catalog_generation`, `catalog_active`, `oracle_card`, `card_face`, `printing`, `printing_face_asset`, `catalog_name_fts` as `@Fts5` with unicode61) with the identities, foreign keys and indexes the design requires. Collector number is TEXT. No destructive fallback. Opening a file whose schema version is newer than the code's fails with a typed, visible, recoverable Outcome and never clears data (verify what Room3 actually throws for a downgrade and map it). Commit the exported `schemas/**/1.json`. Check: `./gradlew --no-daemon :data:storage:assembleDebug` passes.
2. **Database port adapter.** A public adapter implementing core `Database` over Room3 (public surface limited to an `open(context, fileName)` factory returning `Outcome`, the `Database` implementation, and `close()`). Honor every KDoc rule on `DbTransaction` and `Database.transaction`: irreversible rollback-only latch, `abort` latches then throws `TransactionAbort`, a returned typed non-success rolls back and propagates the first latched status, other exceptions roll back and are classified, cancellation always rolls back and rethrows unchanged and wins over typed statuses, `CancelToken` checked before commit. `capabilities()` reports the real `sqlite_version()`, schema version, `fts5 = true` only after an actual FTS5 probe, foreign keys state, and the real bound-parameter limit. JVM unit tests (`testDebugUnitTest`) for every pure piece: parameter parser/binder, latch state machine, row bound. Check: `./gradlew --no-daemon :data:storage:testDebugUnitTest :data:storage:assembleDebug`.
3. **Instrumentation tests** in `src/androidTest` (AndroidJUnit4, coroutines-test): bound FTS5 MATCH with a literal built from punctuation, quotes, apostrophes and FTS keywords (bound, never interpolated); write then query failure rolls back; write then `abort` rolls back; caught `TransactionAbort` still rolls back; returned `Outcome.Failure` rolls back with that status; cancellation after writes rolls back and rethrows; `maxRows` 101 returns 101 and 102 is rejected by `SqlRequest`; close and reopen keeps committed rows; newer on-disk schema version is rejected without data loss; `capabilities().fts5` is true. Check locally: `./gradlew --no-daemon :data:storage:assembleDebugAndroidTest` compiles. They cannot run in this container (no `/dev/kvm`); the orchestrator runs them on the hosted emulator workflow. Do not claim they passed.
4. **Handoff.** `coordination/handoffs/cc-storage-01.md` per the CONTINUE.md minimum: status READY-FOR-REVIEW, branch head, exact commands run with results, the final parameter rule, the public API surface, anything not run, and requested shared-file changes.

## Local environment (this container)

`export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/opt/android-sdk PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH`; run Gradle from `android-app/` with `bash gradlew --no-daemon --max-workers=2`. Maven Central may return HTTP 429 through the proxy: wait and retry, it is not a build error. Add `android-app/local.properties` with `sdk.dir=/opt/android-sdk` only if Gradle cannot find the SDK; it is gitignored and must never be committed.

## Rules

Zero placeholders (CLAUDE.md). Every step: code and tests, placeholder scan (`grep -rnE "TODO|FIXME|XXX|NotImplementedError|not implemented|TODO\(\)" android-app/data/storage`), handoff update, commit `[cc-storage-01 step k/4] <summary>`, `git push -u origin agent/cc-storage-01`. Record your model tier (for example "Sonnet tier"), not a model ID. No em dashes. Never claim a check that did not run.
