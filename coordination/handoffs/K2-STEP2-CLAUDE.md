# K2 step 2: Claude Code work notes (read this first, Codex)

Single file to resume from. It is updated at every Claude Code checkpoint; the last section always says exactly what to do next.

## Why Claude Code is here

Codex ran out of usage with its lease expired (2026-10-11T23:47:41Z). Pat authorized Claude Code to take over K2 step 2 and leave these notes (DECISIONS.md, "Lease takeover, 2026-10-12"). Claude Code follows CONTINUE.md and the K2 card `coordination/tasks/orch-kotlin-m0-01.md`.

## State at takeover (verified, not assumed)

- `main` = `575159ba13be87def464fa419d1bbcb22a48211d`: core contracts integrated, independently accepted by Astra, hosted compile success (run 38182564573, 25 JVM tests per Codex's state notes). Core lock released.
- Nothing of K2 step 2 existed: no Android module, no Room3/FTS5, no importer, no DataStore, no UI, no APK.
- Claude QA branch `agent/cc-k2-qa-01` (836e6df) is READY-FOR-REVIEW: test matrix, synthetic vectors, contract review. It is not merged. Its author is Claude Code, so an independent reviewer (Codex or Astra) should review it, not Claude Code.

## Environment facts for whoever continues

- This Claude Code container has JDK 21 with `javac` and Gradle; no Android SDK preinstalled; no `/dev/kvm`, so no local emulator. Instrumentation and emulator checks must run on hosted GitHub Actions.
- `scripts/checkpoint.py` hard-codes `codex` in the JOURNAL line and does not change lease `session_id`/`model_family`; Claude Code corrected both by hand after running it.
- Claude Code installed locally (environment only, never committed): OpenJDK 17 via apt, Android cmdline-tools 23.0 at `/opt/android-sdk`, `platforms;android-36`, `platforms;android-37.0`, `build-tools;36.0.0`, `platform-tools`. Build with `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/opt/android-sdk`.
- Maven Central sometimes returns HTTP 429 through the proxy. Retry; use `--max-workers=2`. It is not a build defect.
- Existing core checks pass locally on JDK 17: `cd android-app && ./gradlew --no-daemon :core:model:check :core:common:check` gave 13 + 12 = 25 JVM tests, 0 failures (matches hosted run).

## Progress log

| Checkpoint | Commit | What |
|---|---|---|
| cp-20261012-claude-k2-14 | e9afe60 | Lease claimed; this file created. |
| cp-20261012-claude-k2-15 | (this commit) | K1 tuple resolved and compiled in a scratch probe. Evidence: `docs/gauntlet/gates/K2-TUPLE-PROBE.md`. One binding changed: compileSdk 36 to 37 (Compose BOM 2026.09.00 AARs require it); target 36, min 23 and all library versions unchanged. Independent review requested: `docs/gauntlet/gates/K2-TUPLE-REVIEW.md` (written by a separate reviewer; if absent, the review did not finish). |

## Next action

1. Read `docs/gauntlet/gates/K2-TUPLE-REVIEW.md`. If its verdict is ACCEPT (or ACCEPT-WITH-CHANGES with the changes made), record compileSdk 37 in `config/kotlin-android-candidate-lock.json` (android_sdk.compile, with the probe and review as evidence). If REJECT, follow its alternative and re-probe before any Android module lands.
2. Then start card step 2 in `android-app/` (orchestrator-owned shared files: `settings.gradle.kts`, root `build.gradle.kts`, `gradle/libs.versions.toml`, `app/**`):
   - Add catalog entries and plugins exactly as in the probe section of K2-TUPLE-PROBE.md (AGP 9.4.1 `com.android.application`/`com.android.library`, compose plugin 2.2.10, KSP 2.3.12, `androidx.room3` 3.0.3; libraries room3-runtime/compiler 3.0.3, sqlite-bundled 2.7.1, datastore-preferences 1.2.1, compose BOM 2026.09.00, activity-compose 1.13.0).
   - Create `:data:storage` (Android library, Room3 + BundledSQLiteDriver) implementing the schema in `docs/architecture/K2-FOUNDATION-DESIGN.md` section "Storage schema version 1" against the core contracts (`Database`, `DbTransaction`, `RollbackOnlyLatch`, `CatalogLookup`, `CatalogStatus`, `CatalogImport`). Use `withWriteTransaction` and `TransactionScope.rollback(...)` (verified in Room3 3.0.3 sources); no Room2 `withTransaction`.
   - Claim the `storage` component lock before writing `android-app/data/storage/**`.
3. Instrumentation needs hosted CI (no KVM here). The workflow must `sdkmanager --install "platforms;android-37.0"`.
