# K1 Android FTS5 source check

Checked 2026-10-11. This is an official-source and published-artifact review for K1. No application or probe code was added, and this does not claim a Kotlin build or device test.

## Finding

The supported Kotlin Android candidate is Room 3 with AndroidX SQLite's bundled driver. Room 3 adds FTS5 entities through `@Fts5`, and its database builder requires an explicit `SQLiteDriver`. AndroidX recommends `BundledSQLiteDriver` for a consistent, source-built SQLite across supported platforms. This makes FTS5 independent of the Android framework SQLite build, whose FTS5 support is not established here.

Room 2 annotations must not be treated as FTS5 support. Room 3 is a distinct package and Maven group (`androidx.room3`, package `androidx.room3`); it requires Kotlin code generation/KSP and uses the SQLiteDriver APIs. The stable Room 3 release page documents `@Fts5` as a Room 3 feature and publishes 3.0.3 as stable (September 9, 2026). Google Maven metadata independently lists Room runtime and compiler 3.0.3. Do not mix Room 2 `androidx.room` dependencies or SupportSQLite factories into this path.

## Candidate artifacts and platform floor

| Role | Stable candidate | Source-backed detail |
|---|---|---|
| Room runtime | `androidx.room3:room3-runtime:3.0.3` | Official Room 3 dependency coordinates and stable version |
| Room compiler | `androidx.room3:room3-compiler:3.0.3` via KSP | Official Room 3 dependency coordinates; KSP required |
| SQLite interfaces and bundled implementation | `androidx.sqlite:sqlite-bundled:2.7.1` | Official SQLite guide recommends this artifact and `BundledSQLiteDriver`; 2.7.1 is stable |

Use one Room3 version and one SQLite version consistently. The `sqlite-bundled` Android artifact transitively supplies `sqlite-bundled-android`; Google Maven's 2.7.1 POM identifies the implementation as the bundled SQLite library. The AndroidX SQLite 2.6.0 release notes state the Android minSDK was raised to API 23. Therefore set the app's Android minimum to at least API 23 for this candidate unless later official artifact metadata demonstrates a higher requirement. Confirm the resolved dependency graph at K2.

The Room 3 builder requires a driver; configure it with `BundledSQLiteDriver()` for this path. The framework-backed `AndroidSQLiteDriver` uses platform SQLite and does not meet this FTS5 requirement by evidence gathered here.

## FTS5 and native packaging evidence

- Room 3 stable notes: https://developer.android.com/jetpack/androidx/releases/room3 — list `@Fts5` support; document `androidx.room3:room3-runtime` and `androidx.room3:room3-compiler` at 3.0.3; state Room 3 is SQLiteDriver-backed and requires a driver. Accessed 2026-10-11.
- Room3 Google Maven metadata: https://dl.google.com/dl/android/maven2/androidx/room3/room3-runtime/maven-metadata.xml and https://dl.google.com/dl/android/maven2/androidx/room3/room3-compiler/maven-metadata.xml — both include stable 3.0.3 (metadata last updated 2026-09-09). Accessed 2026-10-11.
- SQLite guide: https://developer.android.com/kotlin/multiplatform/sqlite — identifies `androidx.sqlite:sqlite-bundled`, the `BundledSQLiteDriver` class, its Android support, and recommends it for the most up-to-date consistent SQLite implementation. The guide's version catalog currently gives 2.7.1. Accessed 2026-10-11.
- SQLite release notes: https://developer.android.com/jetpack/androidx/releases/sqlite — stable 2.7.1 and the API 23 Android minSDK change in 2.6.0. Accessed 2026-10-11.
- Google Maven metadata: https://dl.google.com/dl/android/maven2/androidx/sqlite/sqlite-bundled/maven-metadata.xml — contains 2.7.1 and newer alpha 2.8.0-alpha01; choose stable 2.7.1 for K1. The published POM is https://dl.google.com/dl/android/maven2/androidx/sqlite/sqlite-bundled/2.7.1/sqlite-bundled-2.7.1.pom and shows the Android bundled implementation dependency. Accessed 2026-10-11.
- Room 3.0.3 runtime POM: https://dl.google.com/dl/android/maven2/androidx/room3/room3-runtime/3.0.3/room3-runtime-3.0.3.pom — published by Google Maven and declares Apache 2.0. Accessed 2026-10-11.
- AndroidX SQLite bundled 2.7.1 POM: https://dl.google.com/dl/android/maven2/androidx/sqlite/sqlite-bundled/2.7.1/sqlite-bundled-2.7.1.pom — declares Apache 2.0 and the Android implementation dependency. Accessed 2026-10-11.
- AndroidX source build flags: https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/sqlite/sqlite-bundled/build.gradle — the bundled SQLite build defines `SQLITE_ENABLE_FTS5`. This is the moving main branch, not a 2.7.1 source tag, so it corroborates project configuration but is not version-specific proof of the released binary. Accessed 2026-10-11.

The published 2.7.1 Android AAR was read directly from Google Maven (https://dl.google.com/dl/android/maven2/androidx/sqlite/sqlite-bundled-android/2.7.1/sqlite-bundled-android-2.7.1.aar). Its JNI entries are `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`; this confirms those four ABI payloads are present in the AAR. A read-only ELF program-header inspection of each contained `.so` showed all LOAD segments use `p_align=0x4000` (16 KB). This is artifact inspection, not a vendor claim, and it does not verify final APK/AAB ZIP alignment. Android's 16 KB guidance (https://developer.android.com/guide/practices/page-sizes) requires checking native-library ELF alignment and package alignment. K2 must build the actual hosted APK/AAB and run the Android 16 KB alignment checks on the packaged output; keep the 16 KB emulator/device install smoke distinct from this static AAR observation. Accessed 2026-10-11.

## License

The published Room 3.0.3 runtime and AndroidX SQLite bundled 2.7.1 POMs declare Apache License 2.0. Preserve the AndroidX license and required notices in distributed third-party notices. SQLite itself states that its code and documentation are dedicated to the public domain (https://www.sqlite.org/copyright.html; accessed 2026-10-11). This does not establish the license status of all transitive dependencies; generate and review the resolved artifact license inventory during K2/release preparation.

## Required K2 instrumentation probe

The documentation and package inspection establish a viable candidate, not that our resolved Android app works. A small Android instrumentation probe should compile a Room 3 `@Fts5` entity, build the database with `BundledSQLiteDriver()`, create/open the database, insert known text, and execute a `MATCH` query using a bound query parameter. Include punctuation/quote-like input to prove the query is bound data rather than concatenated SQL. Then verify transaction behavior: commit one change, cancel a coroutine during a second transaction after a write, reopen the database, and assert the committed write persists while the cancelled transaction's write is absent. Reopen after close/process recreation to prove persistence. Run this on the hosted Android target and record the resolved dependency versions, ABI packaged, 16 KB alignment report, and instrumentation result. No K1 source review can substitute for this probe.

## Source links

- Room 3 release/API/dependency documentation: https://developer.android.com/jetpack/androidx/releases/room3
- AndroidX SQLite release history: https://developer.android.com/jetpack/androidx/releases/sqlite
- AndroidX SQLite driver guide: https://developer.android.com/kotlin/multiplatform/sqlite
- Google Maven published metadata and POMs linked above.
- Android 16 KB native page-size packaging guide: https://developer.android.com/guide/practices/page-sizes
- SQLite copyright/license statement: https://www.sqlite.org/copyright.html
