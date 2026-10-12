# Handoff cc-settings-01

READY-FOR-REVIEW (independent reviewer required: the author is the Claude Code Sonnet tier).

- Checkpoint ID: cc-settings-01-cp3
- Owner: Claude Code implementer, worker tier Sonnet (D-10). Lease holder and integrator: the orchestrator.
- Task and step: cc-settings-01, step 3 of 3 (all steps done).
- Branch: `agent/cc-settings-01`. Head: the tip of that branch (a file cannot embed its own SHA; step commits are listed below). Base: orchestrator prep commit b39a3b1 on 64f828a. Dependency: accepted core contracts at 575159b, read only, unchanged.
- Owned paths: `android-app/feature/settings/**`, this handoff. Shared paths touched: none.
- Work status: verified locally on the JVM (see "Not run"). Not reviewed.
- Model that ran: Sonnet tier (Claude Sonnet 5.5).

## Commits

| Step | Commit | Content |
|---|---|---|
| 1/3 | 25da64e | Module build file, `DataStoreSettingsRepository`, codec, serializer, installation-ID store |
| 2/3 | db2e31f | 35 JVM tests, cause-chain failure classification, in-transaction cancellation check |
| 3/3 | the tip of the branch | This handoff |

## Evidence (commands actually run, JDK 17, from `android-app/`, `bash gradlew --no-daemon --max-workers=2`)

- `:feature:settings:assembleDebug`: BUILD SUCCESSFUL, no Kotlin warnings (step 1; the first attempt hit Maven Central HTTP 429, the retry passed).
- `:feature:settings:cleanTestDebugUnitTest :feature:settings:testDebugUnitTest :feature:settings:assembleDebug`: BUILD SUCCESSFUL. Counts from `build/test-results/testDebugUnitTest/*.xml`: `DataStoreSettingsRepositoryTest` 27 tests, `SettingsStoreCodecTest` 8 tests, 35 total, 0 failures, 0 errors, 0 skipped. An earlier run of the same suite had 2 failures (DataStore wraps the serializer's exception on the second access); fixed by classifying through the cause chain, then 35 of 35 passed.
- Placeholder scan (`TODO|FIXME|XXX|NotImplementedError|not implemented|TODO()`) over `android-app/feature/settings`: no matches. Em dash scan: no matches.
- Local mutation probes (not committed, file restored afterwards): removing the installation-ID filter failed the two restore tests and the no-op test (that run also removed the explicit stale check); removing every in-transaction cancellation check failed the cancellation-checkpoint test. The caller-cancelled-mid-transaction test still passed in that run because DataStore's `withContext(callerContext)` also refuses to commit for a cancelled caller.

## Stored format and version

One DataStore file, typed `DataStore<StoredSettings>` with a custom `Serializer`, no `ReplaceFileCorruptionHandler`, so DataStore never substitutes defaults for a file it cannot read. Binary, big-endian:

```
"CLSS"          4 bytes magic
schema version  int32, currently 1
body            revision, updatedAt, dataLanguage, theme, storageMode, cache, budget,
                providerProfileIds, consents (each: installationId + full ConsentRecord)
CRC-32          int32 over every preceding byte
```

- Enums are stored by name, strings as int32 length plus UTF-8, `Knowledge` as a tag byte, `DecimalAmount` as unscaled integer text plus scale plus unit tag (scale is preserved exactly).
- Reading is bounds checked: a damaged length or count cannot allocate or read past the payload; a value that a domain constructor rejects is reported as corruption.
- Version above 1: reported before the checksum is looked at, as `Outcome.Failure(CORRUPT_DATA)` with message "written by a newer version of the app ... it was not modified" and evidence `stored-version=N supported-version=1`. Version 0, negative, bad magic, bad checksum, truncation, empty file: `Outcome.Failure(CORRUPT_DATA)` "damaged and was not modified". In both cases `read`, `update` and `approve` all fail and the file bytes are untouched (tests assert bytes equal before and after, also across a restart). K2 has no reset action; a UI that hits this must show an unavailable state. Offering a reset would delete user data and is a product decision.
- `SettingsSnapshot.sourceNotices` is always empty in K2 (no `SettingChange` writes notices, and the notice data belongs to the catalog side), so notices are not stored. A future version adds them under a new schema version.

## No-op patch rule

`update` always checks `expected` against the stored revision first, inside `updateData`; stale means `Failure(STALE_REVISION)` and nothing is written, even for an empty patch. Changes then apply in order to a working copy. If the resulting stored state equals the current stored state (empty patch, values already in effect, a change followed by its reversal, or revoking an already revoked consent), nothing is committed: the revision and `updatedAt` stay as they are and `Success(current snapshot)` is returned. Otherwise the revision increments exactly once for the whole patch and `updatedAt` is the real clock reading. `approve` follows the same rule: if an effective, unrevoked consent already permits the identical intent, that record is returned and nothing is committed; otherwise one new record is added and the revision increments once. Validation that needs no state (negative known budget, duplicate provider profile ids) returns `INVALID_INPUT` before the store is touched; revoking an id with no effective record returns `INVALID_INPUT` and aborts the whole patch.

## Behavior notes for the integrator and Pat

- Defaults (orchestrator choice, flagged to Pat as having no documented product source): English data language, theme SYSTEM, storage FULL, cache `imageBytes = 0, retainRulings = false, wifiOnlyDownloads = true`, budget `Knowledge.Unknown("No budget has been set")`, empty provider profiles, notices and consents. Revision 0 with `updatedAt = UtcTimestamp(0)` means "never written": a first `read` writes no settings file and invents no timestamp. UI must not display `updatedAt` while the revision is 0.
- Restore never grants acquisition: each stored consent holds the installation ID that approved it. The ID is a random UUID in `Context.noBackupFilesDir/command_library_installation_id`, created on first use (temp file, fsync, rename). `read` and `update` see only consents of the current ID; foreign consents stay inert in the file, are never reported as granted, never get a fabricated `revokedAt`, and become effective again only if the original ID returns (tested). Approving the same intent under the new ID creates a new record. An ID file whose content is not a UUID is replaced with a new ID (fail closed: existing consents become inert, nothing is granted). An unreadable ID file is `Failure(PERSISTENCE)`.
- `approve` records the intent and returns the `ConsentRecord`; it starts no job and makes no network call (a test asserts the directory holds only the store and ID files afterward).
- `RevokeConsent(id, removeDownloadedData)`: sets `revokedAt` to the real clock reading on that record only and returns the updated snapshot. `removeDownloadedData` is not actionable in K2 because no downloaded data exists; the flag is accepted, nothing is deleted, and no result claims a deletion. Revoking an already revoked record keeps its original `revokedAt`.
- Cancellation: the token is checked before the work, at the start of the transaction, immediately before DataStore writes, and after. A `CancellationException` is rethrown, never turned into an `Outcome`. Cancelled before the last in-transaction check means nothing is written; cancelled after the commit still throws, and the commit stands (matches the core `cancellableOutcome` rule). The one conversion: if the repository itself was closed and the caller is not cancelled, the call returns `Failure(PERSISTENCE, "Settings repository is closed")` instead of cancelling an innocent caller.
- Concurrency: one winner per expected revision (5 rounds of 16 concurrent updates in a test); concurrent approvals all commit, each incrementing once (12 in a test).
- Wiring: create exactly one instance per process with `DataStoreSettingsRepository.create(applicationContext)` (store at `filesDir/datastore/command_library_settings.bin`, ID at `noBackupFilesDir/command_library_installation_id`; both names are public constants on the companion). A second live instance on the same file is an `INTERNAL` failure (DataStore rule, tested). JVM tests use `create(storeFile, installationIdFile, clock, idSource)`. `suspend close()` cancels the instance's scope and waits until DataStore releases the file.

## Requested shared-file changes (orchestrator)

1. `android-app/app/**` (when the app module exists): depend on `project(":feature:settings")` and register one `DataStoreSettingsRepository.create(applicationContext)` in the composition root. No catalog, settings.gradle or CI change is needed: `:feature:settings` is already registered and `datastore-core`, `coroutines-core`, `junit`, `coroutines-test` are already in the catalog. Hosted CI should run `:feature:settings:testDebugUnitTest` (JVM only, no emulator).
2. Manifest backup and data-extraction rules (`android-app/app/src/main/**`), your decision: the installation ID file sits in `noBackupFilesDir`, which the platform documents as excluded from backup and transfer, so a restored settings file can never carry effective consent. Excluding the settings file is therefore not needed for safety. If the product wants no preference restore, exclude `datastore/command_library_settings.bin` (domain `file`) from both `fullBackupContent` and `dataExtractionRules` (cloud-backup and device-transfer sections). The `noBackupFilesDir` exclusion was not verified on a device or emulator here.

## Not run

- No Android instrumentation, emulator, device, or hosted CI run. Nothing exercises the `Context` factory (`create(context)`) at run time; it only compiles. The JVM tests run on JDK 17 against the Android variant of the library with no framework calls.
- Not tested: process death in the middle of a write (the atomic replace is DataStore's own mechanism, relied on, not re-proven), multi-process access (unsupported by design), Android lint, the backup/transfer behavior above, API 23 runtime behavior of `File.renameTo` and `FileDescriptor.sync`.
- Independent review has not happened.

## Gotchas

- DataStore wraps what a serializer throws on later accesses, so failure classification walks the cause chain; do not classify by exception type at the top level only.
- Tests use `kotlin.time`/JUnit4 only (no `kotlin-test` in the catalog). The `blocking` helper adds a 60 s timeout so a hung store fails instead of stalling CI.
- `explicitApi()` applies to main only; the test sources are not public API.

## Files changed

`android-app/feature/settings/build.gradle.kts`; main: `DataStoreSettingsRepository.kt`, `SettingsStoreFormat.kt`, `SettingsStoreSerializer.kt`, `InstallationIdStore.kt`, `SystemSources.kt`; test: `DataStoreSettingsRepositoryTest.kt`, `SettingsStoreCodecTest.kt`, `SettingsTestSupport.kt`; `coordination/handoffs/cc-settings-01.md`. All under `android-app/feature/settings/src/{main,test}/kotlin/com/architectpsg/commandlibrary/feature/settings/`.

## Next and fallback

Next: orchestrator assigns an independent reviewer (not the Sonnet tier of Claude Code), then integrates the branch and wires the composition root. First command for the reviewer: `cd android-app && bash gradlew --no-daemon --max-workers=2 :feature:settings:testDebugUnitTest`. Safe fallback: the branch touches no shared file, so dropping it loses nothing outside `android-app/feature/settings/**` and this handoff.
