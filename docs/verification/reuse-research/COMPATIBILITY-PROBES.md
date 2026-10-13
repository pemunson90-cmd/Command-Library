# Image/CSV compatibility probes — 2026-10-13

Recommend **CSV 1.10.0 and Coil 3.3.0 as test-only candidates**. Production acceptance requires independent review and hosted Android verification. Exact artifact/POM/module/source hashes, dependencies, licenses, tag SHAs, rejection evidence and integration requests live in [PROBE-DECISIONS.json](PROBE-DECISIONS.json).

Actual published code was compiled using Kotlin K2 **2.2.10**, stdlib **2.2.10**, JVM target17, JUnit4.13.2, coroutines1.10.2 and okio3.18.1. Compilation referenced the real `android-all:15-robolectric-12650502` jar and Robolectric4.17 APIs. No local SDK or fabricated Android classes were used. This is source/API evidence, **not an AGP or Android runtime result**.

## Actual results

- CSV: **8/8 actual JVM tests pass**, including quoted commas, quoted newlines, escaped quotes, UTF-8 BOM, strict truncated-quote failure, strict mismatched-field failure, complete valid rows, lazy early stop, caller cancellation and stream closure.
- Finite test-only input/field/row guard tests pass. Raw String is capped before parsing; field and row checks reject rather than truncate. CSV itself provides no such cap. Production streaming byte bounds, schema mapping, preview and import/export loss accounting remain application work.
- CSV1.10 initially returned `MalformedCSVException` rather than the expected newer `CsvParseFormatException`: 6/8 passed. Correcting the specific published exception expectation gave8/8; no library behavior was altered. Removing the test-only guards in a scratch mutation caused two expected failures, preserved in JSON.
- Coil: full authored probe source **compiles**, including `ImageLoader`, `ImageRequest`, `Interceptor`, `DiskCache`, and Compose `AsyncImagePainter.State.Success` API reference. **1/1 actual JVM cache test passes**: eviction removes the older cache entry, retains the newer entry within64 bytes, and clearing the cache preserves a separate pack file byte-for-byte.
- Four Android runtime tests are authored and compiled but **not_run**: local1x1 PNG success; malformed bytes error; HTTPS request has no fetcher; cancellation while a real Coil interceptor is suspended before local decode. The cancellation test does not claim to interrupt a native decoder. The fixture PNG has valid chunk CRCs.
- Coil network artifacts are absent and every probe loader calls `serviceLoaderEnabled(false)`. This prevents ambient network service fetchers; it does not substitute for the app's consent policy. Retained packs never live under Coil's cache directory.

## Candidate decisions

`com.jsoizo:kotlin-csv-jvm:1.10.0` retains the legacy `com.github.doyaaaaaken.kotlincsv` API package. Its published stdlib1.7.21 and coroutines1.5.2 dependencies can be upgraded by the application's existing pins. Its synchronous parser requires explicit caller cancellation checks between rows. `open(InputStream)` closes the stream and performs buffered read-ahead: early stopping parsing does not prove zero read-ahead.

CSV2.0.0 moved both Maven coordinates and API package to `com.jsoizo`. Its eight comparison probes passed with pinned2.2.10 runtime, and its declared2.3.21 stdlib also compiled with K2. However, its published graph requests stdlib2.3.21 and kotlinx-io0.9.0. It provides no required behavior missing from1.10.0 in this bounded packet, so it is deferred rather than overriding its runtime requirement.

Coil3.6.3 requests stdlib2.4.10 and Compose foundation1.12.0. Compiling the published stdlib graph with K2 exits1 with `metadata is 2.4.0, expected version is 2.2.0`. Merely importing its types while substituting the old stdlib compiled, which was insufficient evidence for acceptance. Coil3.5.0 and3.4.0 were metadata-screened only; their declared runtime graphs also raise the current Kotlin pin. Coil3.3.0 requests stdlib2.2.0 and works with the pinned compiler/runtime in these probes.

Selected POMs do not declare Room, SQLite or DataStore. Their coexistence with Room3.0.3, SQLite2.7.1 and DataStore1.2.1 is still an unrun full Gradle graph/build check. Selected JAR/AAR archives contain no `.so` entries; this does not establish native-library absence or16KiB compliance for the entire resolved application graph.

Both candidates are Apache-2.0. Source LICENSE bytes and artifact/source checksums are recorded separately; Git tags are source provenance, not an attestation that published binaries were rebuilt from those tags. Preserve relevant LICENSE/NOTICE obligations at integration. No reference source was copied and artwork licenses are separate.

## Reproduce and integrate

Root owns the test-only configuration commit `2235e5e30c27e3a2320801117024759874180ad0` (cherry-picked here as990975f). Aliases are `libs.kotlin.csv.compatibility`, `libs.coil.core.compatibility`, `libs.coil.compose.core.compatibility`. No production dependency or DI change is included.

Local commands actually run:

```sh
python3 /workspace/scratch/reuse-compatibility-probe/run-csv.py
python3 /workspace/scratch/reuse-compatibility-probe/run-coil.py
```

These private scratch runners invoke `org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath "$CP" -d "$CLASSES" "$OWNED_TEST_SOURCE"`, then `org.junit.runner.JUnitCore` for the CSV class or the separate `CoilDiskCacheCompatibilityProbeTest` class. Compiler jars are in `/workspace/scratch/kotlin-probe/lib`; Coil/CSV/Robolectric published artifacts in `/workspace/scratch/reuse-compatibility-probe/accepted`; Android-all in `/workspace/scratch/settings-outbox-probe/lib`. No compiler metadata-skip flag was used. These scratch runners are replaceable evidence, not committed build infrastructure.

Required hosted commands, from `android-app`:

```sh
./gradlew :core:common:test --tests '*CsvCompatibilityProbeTest'
./gradlew :feature:shell:testDebugUnitTest --tests '*Coil*CompatibilityProbeTest'
./gradlew :feature:shell:dependencies --configuration debugUnitTestRuntimeClasspath
```

Inspect resolved variants and Kotlin/Compose/lifecycle/coroutines/okio pins before production acceptance. Hosted AGP9.4.1 compilation, Robolectric decode and cancellation, Compose rendering, instrumentation, device/fold behavior and the full resolved native graph are **not_run locally**. Existing Android CI is the runtime gate. This packet performs no engine/model/native-inference trials and no application cache/pack integration.
