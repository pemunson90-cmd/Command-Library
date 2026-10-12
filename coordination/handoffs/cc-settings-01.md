# Handoff cc-settings-01

- Checkpoint ID: cc-settings-01-cp2
- Owner: Claude Code implementer, worker tier Sonnet (D-10). Lease holder and integrator: the orchestrator.
- Task and step: cc-settings-01, steps 1 and 2 of 3 done (module and repository; JVM tests).
- Branch: agent/cc-settings-01. Base: orchestrator prep commit b39a3b1 (on 64f828a). Dependency: accepted core contracts at 575159b, read only.
- Owned paths: `android-app/feature/settings/**`, this handoff. Shared paths touched: none.
- Work status: verified locally (JVM tests pass). Not reviewed.

## Done

- Step 1: `android-app/feature/settings/build.gradle.kts` (AGP built-in Kotlin, `com.android.library`, compileSdk 37, minSdk 23, Java 17, `explicitApi()`), public `DataStoreSettingsRepository : SettingsRepository` with two `create` factories (Context, and store File plus installation-ID File), internal codec, serializer, installation-ID store, system clock and id source.
- Evidence: `./gradlew --no-daemon --max-workers=2 :feature:settings:assembleDebug` BUILD SUCCESSFUL, no Kotlin warnings (first attempt hit Maven Central HTTP 429, retried). Placeholder scan: no matches.

- Step 2: 35 JVM tests, 0 failures: `DataStoreSettingsRepositoryTest` (27) and `SettingsStoreCodecTest` (8), `src/test/kotlin/...`. Run: `./gradlew --no-daemon --max-workers=2 :feature:settings:cleanTestDebugUnitTest :feature:settings:testDebugUnitTest :feature:settings:assembleDebug` BUILD SUCCESSFUL (counts read from `build/test-results/testDebugUnitTest/*.xml`). Placeholder scan and em dash scan: no matches.
- Local mutation probes (not committed, files restored): dropping the installation-ID filter failed 2 consent tests plus the no-op test (the same run also removed the explicit stale check); dropping every in-transaction cancellation check failed the cancellation-checkpoint test.

## Next

Step 3: finalize this handoff (stored format, no-op rule, shared-file requests, results) and push.

## Design as built (details finalized in step 3)

- One DataStore file (`DataStoreFactory.create` with a custom `Serializer`), no corruption handler, so a corrupt or newer store is never replaced.
- Stored format: magic `CLSS`, int32 schema version 1, body, CRC-32. Version above 1 is checked before the checksum and reported as a typed failure.
- Revision check and bump happen inside `updateData`; consents carry the installation ID that approved them; `read` returns only consents of the current installation ID.

## Gotchas

- Only one live repository per store file per process (DataStore rule). `close()` cancels the scope and waits so a new instance can open the same file.
- Model tier recorded: Sonnet (not a model ID).
