# K1 Android toolchain source findings

Research snapshot: 2026-10-11 UTC. This note records official documentation and release metadata; it does not validate a build or pin a compatible dependency tuple. No application code was changed.

## Kotlin, AGP, and Compose

AGP 9.0+ enables built-in Kotlin and replaces the `org.jetbrains.kotlin.android` plugin for Android modules. The same Android guidance says `org.jetbrains.kotlin.kapt` is incompatible with built-in Kotlin and recommends moving processors to KSP. AGP 9.4’s official release notes set Gradle 9.6.0 and JDK 17 as both minimum and default, and state a maximum supported API level of 37. These are direct requirements for the AGP candidate, not evidence that all project plugins work with it.

The Compose compiler is configured through `org.jetbrains.kotlin.plugin.compose`, available with Kotlin 2.0+. Android’s setup guide says its version matches the Kotlin version and demonstrates both plugin-marker and classpath coordinates. Therefore, for a standalone Kotlin Gradle Plugin (KGP) route, use the Compose compiler plugin at the exact KGP version; if the candidate remains KGP 2.4.21, the matching compiler plugin candidate is 2.4.21. This is a versioning rule, not proof that this combination works with AGP 9.4.

The available official docs do not establish a complete, supported pairing of AGP 9.4 built-in Kotlin with a separately versioned Compose compiler plugin, nor do they say that adding standalone KGP 2.4.21 is valid while built-in Kotlin is enabled. AGP documents the temporary escape hatch `android.builtInKotlin=false` for projects not yet migrated, with a warning that this cannot be disabled in AGP 10.0. Keep both integration routes unselected until a minimal hosted Android compile resolves Compose, KSP, and the chosen Room compiler together. KSP’s official releases show 2.3.12 (2026-09-09); its 2.3.10 notes include a fix for Kotlin 2.4.0 module names and AGP 9 built-in Kotlin R-class resolution. This makes KSP 2.3.12 a reasonable stable probe candidate, not a compatibility guarantee for Kotlin 2.4.21 or every processor.

The AGP 9.4 source-backed Gradle/JDK candidate is 9.6.0/JDK 17. Kotlin 2.4.21 is the candidate previously extracted from Kotlin’s official release metadata; its Compose plugin version follows the matching-version rule above. None of these facts proves the whole tuple.

## Room 3, Room 2, and FTS5

Room 3 is a separate stable line, not merely a future or alpha artifact: Android’s Room 3 release page lists stable 3.0.3 on 2026-09-09, and Google Maven metadata for `androidx.room3:room3-runtime` also reports 3.0.3 as release/latest. Room 3 provides `androidx.room3:room3-runtime` and `androidx.room3:room3-compiler`; the official setup example uses KSP. Room 3 requires KSP and coroutine-based DAO APIs, generates Kotlin only, and moves to new packages/artifacts. It also explicitly adds FTS5 through `@Fts5`, including tokenizer constants and the detail option. That is direct evidence that raw SQLite is not needed solely to obtain an FTS5 declaration, though app-specific query behavior, tokenization, ranking, and migration still need a real test.

Room 2.8.5 remains a separate stable candidate (`androidx.room:room-runtime`, Google Maven metadata), and its standard compiler route is `ksp("androidx.room:room-compiler:<same-version>")`. Room 2.8.0 release notes raised Room’s Android min SDK to API 23; this supports minSdk 23 if selecting Room 2.8.x, subject to the rest of the app. The Room 3 release page inspected here did not state an Android min SDK in its visible release/setup guidance. Do not transfer Room 2’s API-23 fact to Room 3 without checking Room 3’s published Android variant metadata and build behavior. Room 3 should now be compared against Room 2 on the project’s FTS, migration, coroutine, and dependency requirements before selecting a line.

## DataStore and Android API levels

The current stable DataStore examples on Android’s release page use 1.2.1 for Preferences and typed DataStore. The page separately lists 1.3.0-alpha11, so 1.2.1 is the stable candidate as of this snapshot. This is suitable for preference-like settings or small typed settings; it is not a replacement for the relational card/library database.

Android 16 is API 36, while AGP 9.4 officially supports through API 37. A conservative current recommendation is `compileSdk = 36` and `targetSdk = 36`, then re-evaluate target policy when release timing is known; AGP’s maximum is a build-tool ceiling, not a recommendation to target preview/future API 37. Use `minSdk = 23` only if the selected Android dependencies (including Room) confirm it and product/device reach accepts that floor. If Room 3 is chosen, first inspect its published Android AAR/Gradle variant metadata and run the minimal compile at the proposed floor; its release guide alone did not settle that value.

## Evidence and provenance

Official sources retrieved 2026-10-11 UTC. Android developer documentation pages state the Android Docs content license in their footer (CC BY 4.0; code samples are Apache 2.0). The Room 3.0.3 Android AAR POM identifies Apache License 2.0. Release metadata is evidence of publication/status only; it is not a compatibility matrix.

| Finding | Official source |
|---|---|
| Built-in Kotlin behavior, KAPT limitation, migration and AGP 10 note | [Migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin) |
| Compose compiler plugin availability, artifact coordinates, and matching Kotlin version | [Set up the Compose Compiler Gradle plugin](https://developer.android.com/develop/ui/compose/compiler) (redirects to current setup page) |
| Kotlin release information for candidate 2.4.21 | [Kotlin releases](https://kotlinlang.org/docs/releases.html) |
| AGP 9.4 Gradle/JDK/API compatibility | [AGP 9.4 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes) |
| KSP current release and 2.3.10 compatibility fixes | [KSP releases](https://github.com/google/ksp/releases) |
| Stable Room 2 and 2.8 min SDK/compiler guidance | [Room release notes](https://developer.android.com/jetpack/androidx/releases/room) |
| Stable Room 3.0.3, KSP requirement, changed API surface, FTS5 | [Room 3 release notes](https://developer.android.com/jetpack/androidx/releases/room3) |
| Room 3.0.3 published runtime version | [Google Maven metadata: room3-runtime](https://dl.google.com/dl/android/maven2/androidx/room3/room3-runtime/maven-metadata.xml) |
| Room 2.8.5 published runtime version | [Google Maven metadata: room-runtime](https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/maven-metadata.xml) |
| Stable DataStore 1.2.1 and alpha line | [DataStore release notes](https://developer.android.com/jetpack/androidx/releases/datastore) |
| Android 16 API level | [Android 16](https://developer.android.com/about/versions/16) |
| Room 3.0.3 artifact license | [room3-runtime 3.0.3 POM](https://dl.google.com/dl/android/maven2/androidx/room3/room3-runtime/3.0.3/room3-runtime-3.0.3.pom) |

## Still unverified

- A minimal hosted build with the actual AGP/Kotlin/Compose/KSP/Room selections, including whether to use built-in Kotlin or standalone KGP.
- Room 3’s effective Android min SDK from its published variant metadata and the app’s desired device reach.
- FTS5 runtime behavior on the selected Android SQLite driver/runtime, including `MATCH`, tokenizer, ranking, migrations, and packaged-device execution.
- Google Play target API deadline applicable at the eventual release date.
- Whether the proposed candidate BOM and all Compose artifacts resolve and compile against the selected Kotlin route.
