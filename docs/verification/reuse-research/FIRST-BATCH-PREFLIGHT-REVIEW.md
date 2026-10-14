Independent focused source preflight: APPROVE
Exact candidate: 6adf97b5d47fe2531afe829291b2ea9c140e2ea2
Scope: root294e8b3 test dependencies/lock, Coil migrationaf6e5c9 (worker9e170fc), CI6adf97b; prior visual corrective sourcec377542 approval remains. Read-only repository review; actual hosted acceptance pending.

Findings
- Four actual Coil runtime cases move intact from unsupported Robolectric SDK35 native FileSource to AndroidJUnit4. Normalized source comparison confirms identical assertions, file decode input, bundled default codec, dimensions, Compose result API, malformed error, absent HTTPS fetcher and suspended-interceptor cancellation checks. Only context, API23-safe File/Base64 and diagnostic substitutions affect bodies. JVM cache preservation/eviction case remains.
- Test loaders all retain serviceLoaderEnabled(false), disabled memory/disk caches and shutdown/resetMain cleanup; local-file test deletes its file. No production dependency/service/network route or success substitute was introduced. API23 tests use java.io.File and android.util.Base64; compiled instrumentation classes contain no java.nio.file.Files or java.util.Base64 references. Setup/cleanup discipline is preserved from the prior tests.
- Actual failed XML has4 tests,1 failure,0 errors/skips; hash matches PROBE-DECISIONS.json. Its stack is FileSource -> ImageDecoder.nativeBinding. Independently fetched exact Robolectric4.17 upstream test source matches recorded hash and excludes FileSource at API35+, with explicit TODO. Moving the real file route to actual Android preserves the required test rather than substituting decoder/input.
- Root294e8b3 adds only Coil androidTest dependencies, the exact probe lock path and task delta. Runtime pins remain unchanged. Worker compile log compiler_exit0 hash matches JSON; root test trees match worker9e170fc exactly. This is existing worker source-compile evidence, not reviewer native execution.
- CI6adf97b adds assembleDebugAndroidTest before check and emulator needs:jvm-and-assemble. It removes only the projects listing. All JVM/lint/APK/native execution, matrix, evidence retention and visual collector requirements remain. The dependency prevents emulator jobs after failed prerequisites. Successful execution serializes provisioning behind preflight as documented.

Checks actually run
- Exact candidate SHA, git diff --check for294e8b3/af6e5c9/6adf97b and c377542^..HEAD: PASS.
- Normalized old/new Coil test-body comparison; exact worker/root probe tree match; actual XML parsing/hash; upstream source fetch/hash; existing compiler-log hash; all9 compiled class files inspected for API26-only references: PASS.
- YAML parse; compile-before-check ordering; needs dependency; exact unchanged emulator body and all3 lanes: PASS.
- Reviewer native compilation, instrumentation and actual36-PNG collection/render inspection: NOT RUN.

Next action
One coherent hosted push of exact6adf97b. Require successful compilation/JVM/build and all4 actual Coil runtime cases, all existing native gates, and36 executed visual cases with36 complete retained PNGs/no skips. Inspect actual captures before visual acceptance. No source blocker remains in the bounded changes; no complete-app or production library acceptance is inferred.

Final copy addendum: APPROVE exact0446f142e87a16e709ea28eee04566534b211cc0. Focused3-file/8-line change replaces technical commander IDs in visible/spoken labels with source-owner-relative1-based commander numbers, retaining distinct same-name partners and own-commander identity. Missing-source copy uses unknown commander. Exact GameAction callbacks and slot-ID assertions are unchanged. Actual diff --check, XML uniqueness/formatter argument counts and callback/assertion source comparison PASS. Native compilation/rendering remains pending at this final SHA; all preceding acceptance conditions still apply.
