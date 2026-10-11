# R-14 Local database and full-text search

Path: `docs/gauntlet/research/R-14-database-fts.md`. Every claim carries its source URL and access date. Anything not confirmed by a source is marked UNVERIFIED.

Task card: `coordination/tasks/cc-research-14.md`. Date of research: 2026-10-11 (all "accessed" dates below are 2026-10-11 unless stated).

STATUS: work in progress. Sections are filled one at a time; a section marked PENDING has not been researched yet.

## Question

Which local database and full-text search setup holds the full card set and rules on Android and Windows within the performance needed, and how does each candidate stack (S1 to S6) reach it?

## Findings

### F1. SQLite on Android (framework version, FTS modules, bundled options)

**F1.1 Framework SQLite version per API level (official table).**
- Official table, as listed: API 37 = 3.50; API 36.1 = 3.50; API 35 = 3.44; API 34 = 3.42 and 3.39 (two rows); API 33 = 3.32; API 32 = 3.32; API 31 = 3.32; API 30 = 3.28; API 28 = 3.22; API 27 = 3.19; API 26 = 3.18; API 24 = 3.9; API 21 = 3.8; API 11 = 3.7; API 8 = 3.6; API 3 = 3.5; API 1 = 3.4. Source: https://developer.android.com/reference/android/database/sqlite/package-summary (accessed 2026-10-11)
- Rules stated on the same page: "SQLite upgrades have been delivered as part of a new API level, but more recently SQLite may be upgraded within an API level (as with API 34)"; "Note that patch-level upgrades (e.g. from 3.44.0 to 3.44.3) are not reported here. If an Android release is not in the table, it uses the version of SQLite from the next-lower Android release that is listed"; "Some device manufacturers include different versions of SQLite on their devices." Source: same URL (accessed 2026-10-11)
- Consequence: androidx.sqlite 2.6.0+, Room 2.8 and Room 3 all require minSdk 23 (F1.3, F2), and API 23 maps to SQLite 3.8 by the "next-lower listed" rule (inference from the table, not a separate source). The framework version only matters if the app uses the framework driver; the bundled drivers in F1.3 remove the dependency on it.

**F1.2 Which FTS modules the framework build enables.**
- The AOSP build file for the platform SQLite (`external/sqlite/dist/Android.bp`) lists these cflags: `-DSQLITE_ENABLE_FTS3`, `-DSQLITE_ENABLE_FTS3_BACKWARDS`, `-DSQLITE_ENABLE_FTS4`, `-DSQLITE_OMIT_LOAD_EXTENSION`, `-DSQLITE_OMIT_COMPILEOPTION_DIAGS`, `-DSQLITE_ENABLE_DBSTAT_VTAB`, `-DSQLITE_SECURE_DELETE`. The string "fts5" does not occur anywhere in the file (grep count 0 on the `main` branch). Source: https://android.googlesource.com/platform/external/sqlite/+/refs/heads/main/dist/Android.bp (accessed 2026-10-11)
- The `android16-release`, `android15-release`, and `android14-release` branches of the same file show the same FTS3 / FTS3_BACKWARDS / FTS4 / OMIT_LOAD_EXTENSION flags and no FTS5 flag. Sources: https://android.googlesource.com/platform/external/sqlite/+/refs/heads/android16-release/dist/Android.bp (and the android15-release, android14-release equivalents) (accessed 2026-10-11). The ref `android-latest-release` returned nothing for the same grep (UNVERIFIED for that ref).
- The ICU tokenizer is compiled in for some targets (`-DSQLITE_ENABLE_ICU`). Source: same Android.bp files (accessed 2026-10-11)
- SQLite documents `SQLITE_OMIT_LOAD_EXTENSION` as omitting "the entire extension loading mechanism", so FTS5 cannot be added as a loadable extension on the framework build. `SQLITE_OMIT_COMPILEOPTION_DIAGS` removes `sqlite_compileoption_used()` and the compile_options pragma, so an app cannot probe the framework build for FTS5 that way. Source: https://www.sqlite.org/compile.html (accessed 2026-10-11)
- Conclusion: FTS5 is NOT enabled in the AOSP framework SQLite on any branch checked (Android 14, 15, 16, main). FTS3 and FTS4 are. OEM builds may differ ("Some device manufacturers include different versions of SQLite"); FTS5 on a specific OEM device is UNVERIFIED. Not relying on the framework for FTS5 is the only safe design.
- Secondary corroboration: CommonsWare's Room book says Room supports FTS3 and FTS4 but not FTS5. Source (secondary): https://commonsware.com/Room/pages/chap-roomfts-001 (accessed 2026-10-11, search-result snippet only)

**F1.3 Bundled SQLite options for Android.**

| Option | FTS5 | SQLite version | Size cost | License | Notes |
|---|---|---|---|---|---|
| `androidx.sqlite:sqlite-bundled` 2.7.1 (`BundledSQLiteDriver`) | Yes | 3.50.1 in the released 2.7.1 binaries (measured); androidx-main source already targets 3.53.4 | AAR 2,551,019 bytes (measured, 4 ABIs); per-ABI .so 1.14 to 1.28 MB uncompressed, 0.63 to 0.65 MB compressed (measured) | Apache-2.0 | Android, JVM desktop, iOS, Linux, macOS. minSdk 23 |
| `com.github.requery:sqlite-android` 3.50.4 | Yes | 3.50.4 | README: about 1.2 to 1.8 MB per ABI | Apache-2.0 | Android only. JitPack, not Maven Central. Last commit 2025-10-14 |
| SQLCipher for Android `net.zetetic:sqlcipher-android` 4.19.1 | UNVERIFIED | UNVERIFIED | UNVERIFIED | BSD-style (Zetetic) | Encryption-focused. API 23+. Not needed for public card data |
| Framework SQLite (`AndroidSQLiteDriver`) | No | per F1.1 | 0 | Public domain | FTS3 and FTS4 only |

- Bundled driver FTS5 status (source): the build script defines `-DSQLITE_ENABLE_FTS3`, `-DSQLITE_ENABLE_FTS3_PARENTHESIS`, `-DSQLITE_ENABLE_FTS4`, `-DSQLITE_ENABLE_FTS5`, `-DSQLITE_ENABLE_JSON1`, `-DSQLITE_ENABLE_MATH_FUNCTIONS`, `-DSQLITE_ENABLE_NORMALIZE`, `-DSQLITE_ENABLE_RTREE`, `-DSQLITE_ENABLE_STAT4`, `-DSQLITE_THREADSAFE=2`, `-DSQLITE_TEMP_STORE=3`, `-DSQLITE_SECURE_DELETE`, `-DSQLITE_OMIT_SHARED_CACHE`. Source: https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/sqlite/sqlite-bundled/build.gradle (accessed 2026-10-11)
- Bundled driver FTS5 status (measured on the released binary): the compile-option string `ENABLE_FTS5` is present in `jni/arm64-v8a/libsqliteJni.so` (AAR) and `natives/windows_x64/sqliteJni.dll` (JVM jar); the embedded version string is `3.50.1` with a source id dated 2025-06-06. Method: streamed each artifact from Google Maven into an in-memory zip and searched the binary; nothing written to disk. Sources: https://dl.google.com/dl/android/maven2/androidx/sqlite/sqlite-bundled-android/2.7.1/sqlite-bundled-android-2.7.1.aar and https://dl.google.com/dl/android/maven2/androidx/sqlite/sqlite-bundled-jvm/2.7.1/sqlite-bundled-jvm-2.7.1.jar (accessed 2026-10-11)
- The JVM jar (3,781,360 bytes, measured via HEAD) carries native libraries for `linux_arm64`, `linux_x64`, `osx_arm64`, and `windows_x64` (`sqliteJni.dll`, 1,893,888 bytes uncompressed, 964,315 compressed). There is no `osx_x64` entry. Windows x64 is covered. Windows on ARM: no native entry (UNVERIFIED whether an x64 DLL loads in an x64 JVM under emulation; not tested). Source: the 2.7.1 jar URL above (accessed 2026-10-11)
- Android ABIs in the AAR: arm64-v8a, armeabi-v7a, x86, x86_64 (listed from the AAR contents). Source: 2.7.1 AAR URL above (accessed 2026-10-11)
- Docs: "The recommended implementation to use is `BundledSQLiteDriver` ... It includes the SQLite library compiled from source, offering the most up-to-date version and consistency across all the supported KMP platforms." The driver table lists `BundledSQLiteDriver` for "Android, iOS, Mac, Linux and JVM (Desktop)" and `AndroidSQLiteDriver` for Android only. Source: https://developer.android.com/kotlin/multiplatform/sqlite (accessed 2026-10-11)
- Release history: 2.5.0 (2025-04-09) first stable KMP drivers; 2.6.0 (2025-09-10) added `addExtension` and raised minSdk 21 to 23; 2.7.0 (2026-07-01); 2.7.1 (2026-09-09) latest stable; 2.8.0-alpha01 (2026-09-09). Source: https://developer.android.com/jetpack/androidx/releases/sqlite (accessed 2026-10-11)
- Thread-safety is `SQLITE_THREADSAFE=2` (multi-thread: a connection must not be shared across threads). Room's connection pool handles this; direct use needs care. Source: build.gradle above (accessed 2026-10-11)
- requery sqlite-android: README lists "Full-Text Search 5" among usable features and gives the dependency `com.github.requery:sqlite-android:3.50.4` via JitPack. `Android.mk` defines `-DSQLITE_ENABLE_FTS3`, `-DSQLITE_ENABLE_FTS4`, `-DSQLITE_ENABLE_FTS5`, `-DSQLITE_ENABLE_JSON1`, `-DSQLITE_ENABLE_RTREE=1`. It implements the SupportSQLite interfaces, so it plugs into Room 2.x through `RequerySQLiteOpenHelperFactory`. Sources: https://raw.githubusercontent.com/requery/sqlite-android/master/README.md , https://raw.githubusercontent.com/requery/sqlite-android/master/sqlite-android/src/main/jni/sqlite/Android.mk (accessed 2026-10-11). Last commit "Update SQLite to v3.50.4 (#208)", dated 2025-10-14, per https://github.com/requery/sqlite-android/commits/master (accessed 2026-10-11).
- requery cannot be used with Room 3 directly: Room 3 "is fully backed by the SQLiteDriver APIs and no longer references `SupportSQLite` types" (SupportSQLite survives only as a wrapper artifact). Source: https://developer.android.com/jetpack/androidx/releases/room3 (accessed 2026-10-11). Whether a requery-backed `SQLiteDriver` exists is UNVERIFIED (searched only the requery README).
- SQLCipher for Android: README states "supports Android API 23 and up on `armeabi-v7a`, `x86`, `x86_64`, and `arm64-v8a`", artifact `net.zetetic:sqlcipher-android:4.19.1@aar`, Room 2 integration via `SupportOpenHelperFactory`. FTS5: not mentioned in the README (UNVERIFIED). Source: https://raw.githubusercontent.com/sqlcipher/sqlcipher-android/master/README.md (accessed 2026-10-11)

**F1.4 Why this matters.** The brief needs "Full-text search over Oracle text and rules" plus Scryfall-style syntax (GAUNTLET.md 2.A). FTS5 (bm25 ranking, prefix indexes, trigram tokenizer, external-content tables) is a better tool than FTS4. Every stack must ship its own SQLite on Android to be sure FTS5 exists; none can rely on the framework.

### F2. S1: Room KMP and SQLDelight
PENDING

### F3. S2 to S6: SQLite packages per stack and FTS5 status
PENDING

### F4. Scale: card counts and published benchmarks
PENDING

### F5. Streaming JSON import per stack
PENDING

### F6. Scryfall-syntax parsers and the AST to SQL approach
PENDING

### F7. Atomic data refresh patterns
PENDING

### F8. Licensing findings
PENDING

## Options

PENDING

## Recommendation

PENDING

## Risks

PENDING

## Confidence

PENDING
