# Handoff cc-settings-01

- Checkpoint ID: cc-settings-01-cp1
- Owner: Claude Code implementer, worker tier Sonnet (D-10). Lease holder and integrator: the orchestrator.
- Task and step: cc-settings-01, step 1 of 3 done (module and repository).
- Branch: agent/cc-settings-01. Base: orchestrator prep commit b39a3b1 (on 64f828a). Dependency: accepted core contracts at 575159b, read only.
- Owned paths: `android-app/feature/settings/**`, this handoff. Shared paths touched: none.
- Work status: code (compiled). Tests are step 2 and have not run.

## Done

- Step 1: `android-app/feature/settings/build.gradle.kts` (AGP built-in Kotlin, `com.android.library`, compileSdk 37, minSdk 23, Java 17, `explicitApi()`), public `DataStoreSettingsRepository : SettingsRepository` with two `create` factories (Context, and store File plus installation-ID File), internal codec, serializer, installation-ID store, system clock and id source.
- Evidence: `./gradlew --no-daemon --max-workers=2 :feature:settings:assembleDebug` BUILD SUCCESSFUL, no Kotlin warnings (first attempt hit Maven Central HTTP 429, retried). Placeholder scan: no matches.

## Next

Step 2: JVM tests in `android-app/feature/settings/src/test/kotlin/...`, run `:feature:settings:testDebugUnitTest`. Step 3: finalize this handoff (stored format, no-op rule, shared-file requests, results).

## Design as built (details finalized in step 3)

- One DataStore file (`DataStoreFactory.create` with a custom `Serializer`), no corruption handler, so a corrupt or newer store is never replaced.
- Stored format: magic `CLSS`, int32 schema version 1, body, CRC-32. Version above 1 is checked before the checksum and reported as a typed failure.
- Revision check and bump happen inside `updateData`; consents carry the installation ID that approved them; `read` returns only consents of the current installation ID.

## Gotchas

- Only one live repository per store file per process (DataStore rule). `close()` cancels the scope and waits so a new instance can open the same file.
- Model tier recorded: Sonnet (not a model ID).
