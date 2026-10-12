# K2 tuple probe: actual resolution and compile of the K1 candidate tuple

Status: executed locally by Claude Code on 2026-10-12 (UTC) in a throwaway probe project outside the repo. One binding changed (compileSdk 36 to 37). Independently reviewed: ACCEPT-WITH-CHANGES in [K2-TUPLE-REVIEW.md](K2-TUPLE-REVIEW.md); its required changes are applied (lock `android_sdk.compile` 37 with the pinned platform package; hosted workflow `android-k2.yml` installs `platforms;android-37.0`). No emulator or device run happened here; this container has no `/dev/kvm`.

## Environment (observed)

- OpenJDK 17.0.20.1 (`/usr/lib/jvm/java-17-openjdk-amd64`), installed for this probe; Gradle via the repo wrapper (`android-app/gradle/wrapper`, Gradle 9.6.0, distribution SHA-256 `bbaeb2fe...a01` verified by the wrapper).
- Android command-line tools 23.0 (`commandlinetools-linux-16111833_latest.zip`, SHA-1 `e025545c62a8e64c7559119566a569fb1dec5f60` matched Google's `repository2-3.xml`).
- SDK packages installed: `platforms;android-36` (2.0.0), `platforms;android-37.0` (2.0.0), `build-tools;36.0.0`, `platform-tools`.
- Repositories: Google Maven, Maven Central, Gradle Plugin Portal. Maven Central returned HTTP 429 through the session proxy on first attempts; retries succeeded. Not a tuple defect.

## Probe project

Settings use `FAIL_ON_PROJECT_REPOS` with `google()` and `mavenCentral()`. Root plugins (all `apply false`):

```kotlin
id("com.android.application") version "9.4.1"
id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
id("org.jetbrains.kotlin.jvm") version "2.2.10"
id("com.google.devtools.ksp") version "2.3.12"
id("androidx.room3") version "3.0.3"
```

`:probe` is an Android application using AGP built-in Kotlin (no `org.jetbrains.kotlin.android`, no kapt): namespace and applicationId `com.architectpsg.commandlibrary.probe`, `compileSdk 37` (see failure below), `targetSdk 36`, `minSdk 23`, `buildToolsVersion 36.0.0`, Java 17 source/target, `kotlin { jvmToolchain(17) }`, `buildFeatures { compose = true }`, `room3 { schemaDirectory("$projectDir/schemas") }`. Dependencies:

```kotlin
implementation("androidx.room3:room3-runtime:3.0.3")
ksp("androidx.room3:room3-compiler:3.0.3")
implementation("androidx.sqlite:sqlite-bundled:2.7.1")
implementation("androidx.datastore:datastore-preferences:1.2.1")
implementation(platform("androidx.compose:compose-bom:2026.09.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.activity:activity-compose:1.13.0")
androidTestImplementation("androidx.test.ext:junit:1.3.0")
androidTestImplementation("androidx.test:runner:1.7.0")
androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
```

Source exercised: an `@Fts5 @Entity(tableName = "probe_name_fts") data class ProbeNameFts(val name: String)`, a `@Dao` with `suspend` `@Insert` and a bound `MATCH :query ... LIMIT :limit` `@Query`, a `@Database(version = 1, exportSchema = true)` built with `Room.inMemoryDatabaseBuilder(context, ProbeDatabase::class.java).setDriver(BundledSQLiteDriver()).build()`, a `preferencesDataStore` read/edit, a `ComponentActivity` with `setContent { MaterialTheme { Text(...) } }`, and an `androidTest` that inserts three names and asserts bounded MATCH results. Room3 API names were read from the 3.0.3 source jars (`Fts5.kt`, `Room.android.kt`, `RoomDatabase.kt`, `Transactor.kt`), not assumed: `withWriteTransaction`, `TransactionScope.rollback(result)`, `setDriver`, and the Gradle extension `room3 { schemaDirectory(...) }` exist as used.

A second pure-JVM module `:fts-jvm` tests `androidx.sqlite:sqlite-bundled-jvm:2.7.1` directly.

## Results

### First complete run: compileSdk 36 (as locked) fails

An earlier invocation stopped during configuration on Maven Central HTTP 429 (see Environment); it is not counted.

`./gradlew :probe:assembleDebug` failed in `:probe:checkDebugAarMetadata` with 9 issues, all the same cause. First issue verbatim:

```
1.  Dependency 'androidx.compose.material:material-ripple-android:1.12.1' requires libraries and applications that
    depend on it to compile against version 37 or later of the
    Android APIs.

    :probe is currently compiled against android-36.

    Recommended action: Update this project to use a newer compileSdk
    of at least 37, for example 37.2.
```

The other eight: `foundation-android`, `animation-android`, `foundation-layout-android`, `animation-core-android`, `ui-graphics-android`, `ui-text-android`, `ui-android`, `runtime-saveable-android`, all 1.12.1 from Compose BOM 2026.09.00.

### Second complete run: compileSdk 37, targetSdk 36, minSdk 23 succeeds

`./gradlew --no-daemon --max-workers=2 :probe:assembleDebug :probe:assembleDebugAndroidTest`: BUILD SUCCESSFUL, zero Kotlin `w:` warnings.

| Check | Observed |
|---|---|
| Kotlin Gradle plugin on build classpath | `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10` |
| Kotlin compiler resolved | only `kotlin-compiler-embeddable` 2.2.10 in the Gradle cache |
| Compose compiler plugin | `compose-compiler-gradle-plugin:2.2.10` |
| KSP | `symbol-processing-gradle-plugin:2.3.12` (AGP pulled `2.2.10-2.0.2`, upgraded by the constraint to 2.3.12) |
| Room3 plugin | `room3-gradle-plugin:3.0.3` |
| Build-classpath stdlib | `kotlin-stdlib 2.3.21` under a `strictly 2.3.21` constraint from the Gradle 9.6 runtime. This is the Gradle daemon's classpath, not the app compiler or app runtime. |
| App runtime (debugRuntimeClasspath) | room3-runtime 3.0.3, sqlite-bundled 2.7.1, sqlite 2.7.1, datastore-preferences 1.2.1, compose ui 1.12.1, material3 1.4.0, activity-compose 1.13.0, kotlin-stdlib 2.2.10, kotlinx-coroutines-core 1.9.0 (core modules pin 1.10.2 and will raise it once wired) |
| Merged manifest | `minSdkVersion="23"`, `targetSdkVersion="36"` |
| Room3 exported schema | `CREATE VIRTUAL TABLE IF NOT EXISTS ${TABLE_NAME} USING FTS5(name, tokenize=unicode61)` |
| Debug APK | 17,619,112 bytes, SHA-256 `d6a605b4e3a3fbde503f082fff5b2d54288bd71baed6f2cb1121c6b05aabe94a` (probe only, not a product artifact, not retained) |
| Native libs in APK | `libsqliteJni.so`, `libdatastore_shared_counter.so`, `libandroidx.graphics.path.so` for arm64-v8a, armeabi-v7a, x86, x86_64 |
| ZIP alignment | `build-tools/36.0.0/zipalign -c -P 16 -v 4 <apk>`: every `.so` (OK), "Verification successful" |
| ELF alignment | `readelf -lW`: every LOAD segment of all 12 `.so` files aligned `0x4000` (16 KiB) |

### JVM FTS5 check (supplementary, not the Android proof)

`:fts-jvm:test` 1 test, 0 failures. `BundledSQLiteDriver().open(":memory:")`, `CREATE VIRTUAL TABLE n USING fts5(name, tokenize = 'unicode61')`, four inserts, bound `MATCH ? ... LIMIT ?` returned exactly `[Lightning Bolt, Lightning Helix]`. `sqlite_version()` = `3.50.1`. This proves the bundled SQLite build has FTS5 compiled in on the JVM target. The Android runtime MATCH proof remains the instrumentation test, which did not run here.

## Changed binding (needs independent review)

| Field | Lock | Proposed | Reason |
|---|---|---|---|
| `android_sdk.compile` | 36 | 37 (`platforms;android-37.0`) | Nine Compose 1.12.1 artifacts from BOM 2026.09.00 declare minCompileSdk 37 in AAR metadata; build fails at 36. Other BOM artifacts declare lower values. |

Unchanged: targetSdk 36, minSdk 23, build-tools 36.0.0, every library and plugin version. compileSdk only selects the API surface compiled against; it does not opt the app into new runtime behavior, which targetSdk (still 36) does. Device OS version and library code also affect runtime behavior; neither changes with this binding. Because compileSdk 37 exposes API 37 symbols to code that must run on API 23, lint `NewApi` stays fatal. AGP 9.4 documents API 37 as its maximum, so 37 is inside AGP's supported range.

Alternative not probed: keep compileSdk 36 and select an older Compose BOM whose artifacts accept 36. That would change a library binding instead of an SDK level and would need its own probe.

Consequence for hosted CI: the workflow must install `platforms;android-37.0` with `sdkmanager` rather than assume the runner image has it.

## Not run here

- `connectedDebugAndroidTest` (no KVM). The probe's androidTest APK assembled; it was not executed.
- API 23 and API 36 emulator runs, install/launch, Compose launch checks.
- Transitive license inventory.

## Retained logs

Sanitized copies (the environment's `JAVA_TOOL_OPTIONS` proxy line removed, scratchpad path replaced by `<scratchpad>`) in [k2-tuple-probe/](k2-tuple-probe/):

| File | SHA-256 |
|---|---|
| run-compilesdk36-fail.txt | `691c55dd1d63777da306037e092c05edf690b0fafd3957712d03b0faf8a7c64c` |
| run-compilesdk37-success.txt | `86d5171d34e16893570a1c0debfd040589884f0091057eec54bfabae0f01231e` |
| build-classpath.txt | `7b8da52545a01c2337b80fca6dd1515d66668d0136e714756584396407c048c1` |
| debug-runtime-classpath.txt | `5f3a9d635cd5fe27284d5a8cb1f18f49d7305be16cd85fb65df5e920a38e9f47` |
