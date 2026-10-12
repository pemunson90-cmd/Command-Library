# Task cc-settings-01

Status: original implementation READY-FOR-REVIEW, preserved. Independent review requested bounded fixes; cx-settings-01 now holds the settings lock. Do not write this component while that lock is active. Parent: orch-kotlin-m0-01 step2.

## Ownership

- Branch `agent/cc-settings-01`, based on the orchestrator prep commit on that branch (shared Gradle files already include `:feature:settings` and the catalog entries). Do not edit shared files: `android-app/settings.gradle.kts`, `android-app/build.gradle.kts`, `android-app/gradle/**`, `android-app/app/**` (including the manifest and backup rules), `.github/workflows/**`, `android-app/database/registry/**`, `config/**`, `coordination/**` except this task's handoff.
- Writable: `android-app/feature/settings/**` and `coordination/handoffs/cc-settings-01.md`. Lock: `coordination/locks/settings.lock`.
- Core contracts are accepted and frozen: read, never edit, `android-app/core/**`.

## Read first

1. `docs/architecture/K2-FOUNDATION-DESIGN.md` section "Settings and UI".
2. `InfrastructureContracts.kt` lines 160 to 212: `DownloadKind`, `DownloadIntent`, `ConsentRecord`, `CachePolicy`, `SettingsSnapshot`, `SettingChange`, `SettingsPatch`, `SettingsRepository` (`read`, `update(patch, expected: Revision, cancel)`, `approve(intent, cancel)`), plus `Revision`, `LanguageTag`, `Outcome`, `CancelToken` in `Values.kt` and `ContractSemantics.kt`.
3. `docs/architecture/settings/ARCHITECT.md` (product behavior; its Dart signatures are historical) and `docs/architecture/DATA_MODEL.md` settings/consent sections.
4. `docs/gauntlet/gates/K2-TUPLE-PROBE.md` (proven build setup; DataStore 1.2.1 `preferencesDataStore` compiled there).

## Design decisions fixed by the orchestrator

- **One store, atomic.** Settings and consent records persist in one DataStore file so a revision bump and a consent change commit together. Use typed DataStore (`DataStoreFactory.create` with a `Serializer`) or Preferences DataStore with a versioned encoded payload; either way the stored format carries an explicit schema version, and an unknown newer version is a typed failure, never a silent reset.
- **Revision check inside the transaction.** `update` compares `expected` with the stored revision inside `updateData`; a mismatch commits nothing and returns a typed stale-revision failure. Each committed `update` or `approve` increments the revision exactly once. A patch whose changes are all no-ops still follows one documented rule (state it in the handoff).
- **Restore never grants acquisition.** Each stored consent carries the installation ID that approved it. The installation ID is a random UUID kept in a file under `Context.noBackupFilesDir` (never backed up or transferred), created on first use. `read` returns only consents approved under the current installation ID; consents from a restored or transferred store are not effective and are not reported as granted. Do not fabricate `revokedAt` for them. The orchestrator will also exclude the settings file from device-to-device transfer if needed; you only request that in the handoff.
- **`approve` starts nothing.** It records the intent and returns the `ConsentRecord`; no job, no network.
- **Defaults** (no product default is documented for these; orchestrator choice, flagged to Pat): `dataLanguage = LanguageTag.ENGLISH`, `theme = SYSTEM`, `storageMode = FULL`, `cache = CachePolicy(imageBytes = 0, retainRulings = false, wifiOnlyDownloads = true)` (K2 makes no image or rules requests), `budget = Knowledge` unknown variant, empty provider profiles, notices and consents. If `Knowledge` or `Revision` has no suitable constructor for these, stop and record it; do not invent core changes.
- `RevokeConsent(id, removeDownloadedData)`: marks that record revoked with the real current UTC time; `removeDownloadedData` is recorded in the handoff as not actionable in K2 (no downloaded data exists) and must not report a deletion that did not happen. Read the core types to choose the truthful Outcome.

## Steps (N = 3)

1. **Module and repository.** `android-app/feature/settings/build.gradle.kts`: `com.android.library` (AGP built-in Kotlin, no `kotlin-android`, no kapt), `compileSdk 37`, `minSdk 23`, Java 17, `kotlin { jvmToolchain(17); explicitApi() }`, dependencies via catalog aliases (`project(":core:model")`, `project(":core:common")`, datastore). A public `DataStoreSettingsRepository : SettingsRepository` plus a public factory taking `Context` (or the store `File` and the installation-ID `File`, so JVM tests can run without Android). Cancellation is checked and rethrown, never converted to an Outcome. Check: `./gradlew --no-daemon :feature:settings:assembleDebug`.
2. **JVM tests** (`testDebugUnitTest`, temporary directories, no Android framework): defaults on first read; update with matching revision commits and increments once; stale revision rejected with no change; approve persists and survives a new repository instance on the same files; consents from a different installation ID are not effective; revoke records real time; unknown newer stored version is a typed failure; concurrent updates with the same expected revision: exactly one wins; cancellation propagates. Check: `./gradlew --no-daemon :feature:settings:testDebugUnitTest`.
3. **Handoff.** `coordination/handoffs/cc-settings-01.md` per the CONTINUE.md minimum: READY-FOR-REVIEW, branch head, commands and results, the stored format and version, the no-op patch rule, requested shared-file changes (for example backup and data-extraction rules in the app manifest), anything not run.

## Local environment (this container)

`export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/opt/android-sdk PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH`; run Gradle from `android-app/` with `bash gradlew --no-daemon --max-workers=2`. Maven Central may return HTTP 429 through the proxy: wait and retry. `android-app/local.properties` (`sdk.dir=/opt/android-sdk`) only if needed; gitignored, never committed.

## Rules

Zero placeholders (CLAUDE.md). Every step: code and tests, placeholder scan (`grep -rnE "TODO|FIXME|XXX|NotImplementedError|not implemented|TODO\(\)" android-app/feature/settings`), handoff update, commit `[cc-settings-01 step k/3] <summary>`, `git push -u origin agent/cc-settings-01`. Record your model tier, not a model ID. No em dashes. Never claim a check that did not run.
