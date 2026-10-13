# Image/CSV compatibility probes — 2026-10-13

Recommend **CSV 1.10.0 and Coil 3.3.0 as test-only candidates**. Production acceptance requires independent review and hosted Android verification. Original hosted Coil runtime probes failed2/4; the native graphics rerun passed3/4 and exposed Robolectric4.17 SDK35 FileSource support limits. Actual Android instrumentation remains required. Exact artifact/POM/module/source hashes, dependencies, licenses, tag SHAs, rejection evidence and integration requests live in [PROBE-DECISIONS.json](PROBE-DECISIONS.json).

Actual published code was compiled using Kotlin K2 **2.2.10**, stdlib **2.2.10**, JVM target17, JUnit4.13.2, coroutines1.10.2 and okio3.18.1. Compilation referenced the real `android-all:15-robolectric-12650502` jar and Robolectric4.17 APIs. No local SDK or fabricated Android classes were used. This is source/API evidence, **not an AGP or Android runtime result**.

## Actual results

- CSV: **8/8 actual JVM tests pass**, including quoted commas, quoted newlines, escaped quotes, UTF-8 BOM, strict truncated-quote failure, strict mismatched-field failure, complete valid rows, lazy early stop, caller cancellation and stream closure.
- Finite test-only input/field/row guard tests pass. Raw String is capped before parsing; field and row checks reject rather than truncate. CSV itself provides no such cap. Production streaming byte bounds, schema mapping, preview and import/export loss accounting remain application work.
- CSV1.10 initially returned `MalformedCSVException` rather than the expected newer `CsvParseFormatException`: 6/8 passed. Correcting the specific published exception expectation gave8/8; no library behavior was altered. Removing the test-only guards in a scratch mutation caused two expected failures, preserved in JSON.
- Coil: full authored probe source **compiles**, including `ImageLoader`, `ImageRequest`, `Interceptor`, `DiskCache`, and Compose `AsyncImagePainter.State.Success` API reference. **1/1 actual JVM cache test passes**: eviction removes the older cache entry, retains the newer entry within64 bytes, and clearing the cache preserves a separate pack file byte-for-byte.
- Four original Android runtime tests were executed by hosted run38341796969: cancellation and no-HTTPS passed, valid PNG success and malformed-input error assertions failed. The revised native graphics runtime rerun38343151548 executed all four cases: malformed bytes, missing HTTPS fetcher and cancellation passed; valid local PNG failed in ImageDecoder FileSource nativeBinding. The cases cover: local1x1 PNG success; malformed bytes error; HTTPS request has no fetcher; cancellation while a real Coil interceptor is suspended before local decode. The cancellation test does not claim to interrupt a native decoder. The fixture PNG has valid chunk CRCs.
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

Inspect resolved variants and Kotlin/Compose/lifecycle/coroutines/okio pins before production acceptance. Hosted AGP9.4.1 compilation, Robolectric decode and cancellation, Compose rendering, instrumentation, device/fold behavior and the full resolved native graph are **not_run locally**. Existing Android CI is the runtime gate; its original Coil result was2/4 rather than acceptance. This packet performs no engine/model/native-inference trials and no application cache/pack integration.

## Hosted failure investigation and native graphics candidate

Exact original source `c540a3a18e76e9930711313d55a8c14e6492b183` ran in [hosted run38341796969](https://github.com/pemunson90-cmd/Command-Library/actions/runs/38341796969), JVM job115114220595. XML reports four cases, two failures and no skips. PNG expected-success failed at line78; malformed expected-error failed at line94. Cancellation and missing-HTTPS-fetcher passed. The original assertions omitted result type and cause, so that XML cannot identify the actual PNG error. This is a failed candidate runtime gate, not a production library rejection.

Investigation used the actual published Coil3.3.0 and Robolectric4.17 source jars. Coil adds FileMapper and bundled Android decoders independently of service discovery; SDK>=29 normally selects StaticImageDecoder. Robolectric's `GraphicsModeConfigurer` defaults to LEGACY, and the original test had no graphics annotation. Legacy `ShadowImageDecoder.ImgStream` manufactures10x10/image-unknown metadata for bytes it cannot recognize; legacy BitmapFactory also defaults `allowInvalidImageData=true`. Its file descriptor implementation throws UnsupportedOperationException, and the annotated file descriptor overloads stop atSDK30. These emulator behaviors can explain the opposite outcomes and differ from native Android codecs. The original PNG cause remains unknown, so this is a source-supported harness hypothesis awaiting the rerun, not a claim that the runtime issue is fixed.

The candidate changes only this test class to `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, which selects Robolectric's native-backed ImageDecoder implementation. Every success/error/dimension, no-fetcher and cancellation assertion remains. Assertion messages and stdout now show actual ImageResult type, source/dimensions for success or the first16 error stacktrace lines, plus requested graphics mode and actual runtime SDK. No decoder is mocked, no case is skipped and no malformed-image success is accepted. Shared configuration and all candidate pins remain unchanged.

Revised source compilation and the real cache test pass locally; the revised four Android runtime cases are **not_run locally**. Root must independently review and run the exact candidate in existing CI, then inspect all four outcomes and diagnostics. If a native decode case fails, its actual cause becomes the next investigation input. Details and published source checksums are in JSON.

## Confirmed SDK35 FileSource limitation and acceptance route

[Hosted native run38343151548](https://github.com/pemunson90-cmd/Command-Library/actions/runs/38343151548) executed four cases with one failure and no skips. Actual SDK35/NATIVE local-file PNG returned `ImageDecoder.DecodeException: Only supported on AndroidException in input: null`; its stack enters `ImageDecoder.nativeBinding`, `FileSource.createImageDecoder`, then Coil's `StaticImageDecoder.decode`. The malformed case passed through `BitmapFactoryDecoder`, so its result does not establish the valid PNG decoder path works.

This is confirmed by [Robolectric4.17's own native ImageDecoder test](https://github.com/robolectric/robolectric/blob/robolectric-4.17/integration_tests/nativegraphics/src/test/java/org/robolectric/shadows/ShadowNativeImageDecoderTest.java): `getSourceCreators` says “TODO(hoisie): Support file sources in Android V+.” and includes file sources only below SDK35. Published Coil3.3.0 `toImageDecoderSourceOrNull` deliberately selects `ImageDecoder.createSource(file.toFile())` for a SYSTEM local file. Source and hosted failure checksums are preserved in JSON.

Implemented minimal acceptance route: retain the separate JVM cache test, move all four runtime cases to `feature/shell/src/androidTest/kotlin/com/architectpsg/commandlibrary/feature/shell/compatibility/CoilCompatibilityProbeTest.kt` with `AndroidJUnit4` and `ApplicationProvider`. Keep actual local-file input, success/error/dimension/no-fetcher/cancellation assertions and disabled service discovery. Root approved the exact source path and added Coil core/Compose-core test dependencies to `androidTestImplementation` in294e8b3 (worker cherry-pick0a65c97); coroutine-test and AndroidX runner/ext-junit already exist. Run the filtered `:feature:shell:connectedDebugAndroidTest` on an API35 Android emulator/device. Instrumentation source compilation with actual published AndroidX core1.7.0/ext-junit1.3.0 artifacts and K2/std2.2.10 exits0; API23-safe file creation and Android Base64 are used. Fresh CSV8/8 and JVM cache1/1 pass. Device execution is **not_run**; no production acceptance is inferred. Changing SDK, input type, or decoder to evade the unsupported path would not validate this acceptance route.

Root's next filtered API35 emulator/device command:

```sh
./gradlew :feature:shell:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.architectpsg.commandlibrary.feature.shell.compatibility.CoilCompatibilityProbeTest
```

The source check references actual `android-all:15-robolectric-12650502` and published AndroidX classes; it does not prove AGP instrumentation packaging or device codec behavior. The four cases remain a pending acceptance gate.

## Pending actual Compose smoke fixture

Root reports all four Android codec/network/cancellation cases passed exactd9 hosted38345526072 on API23/API36/API36-16KiB. That gate does not establish Coil3.3 `AsyncImage` rendering with the current Compose BOM1.12.1. One focused `CoilComposeCompatibilityProbeTest` now mounts the published `AsyncImage` API through the existing Compose content rule: real local-file PNG/default codec, service discovery disabled, actual successful callback with1x1 image dimensions, accessible image displayed with nonempty bounds. It removes the painter before shutting down the loader and deleting its source file. No main-dispatcher replacement or screenshot capture is involved. Published API source URL/entry/hash are in JSON.

Actual Compose compiler/AGP build and device execution are **not_run locally**; importing the composable without its compiler plugin would not prove its ABI. Root must independently review and run this single class with the provider/UI hosted batch before production Coil binding.
