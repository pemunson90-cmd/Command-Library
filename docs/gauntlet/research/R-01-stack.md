# R-01 Stack selection

## Question

Which shared Android/Windows stack satisfies Command Library, including foldables, offline FTS, on-device AI and the explicitly named TalkBack/Windows Narrator requirement, with S1 Kotlin/Compose as the fallback?

Research date: 2026-10-11. Scores and weights are engineering judgments from documented capabilities, not app benchmark results. Sources below were accessed on that date. Read-only inputs: R-02 and finalized R-11 through R-15, GAUNTLET sections 2/3/10 and Appendix A, and GATE-0. Historical model names are not claims about the model used for this report.

## Findings

### F1. Evidence reconciliation

| Input and finding | What affects this selection | Verification boundary |
|---|---|---|
| [R-11 F5/F6](R-11-on-device-llm.md) | S1 and S2 both score 4/5 for LLM integration; S1 needs its desktop JNI/JNA adapter, S2 has llamadart. | Runtime/model byte counts and app performance are unverified; no native app was built. |
| [R-12 candidate table and recommendation](R-12-key-storage.md) | S1 native Keystore plus JNA DPAPI is viable; S2 has a maintained two-target store. | Stable plugin source parity, isolation and packaged-runtime tests remain required. |
| [R-13 F2/F3/F4/F7](R-13-adaptive-layouts.md) | S1 has strong Android fold tooling; S2 exposes display features. S1 Windows docs name Java Access Bridge with JAWS/NVDA; S2 has a Windows UIA path. | S1 Narrator is UNVERIFIED. Neither toolkit's documentation proves the app's reader correctness or physical hinge behavior. |
| [R-14 F2/F3/F4/F5](R-14-database-fts.md) | Room 3 now supports FTS5; drift/sqlite3 also provides bundled FTS5. No database-only reason to abandon Kotlin. | Library capability is documented, final native packages/performance not measured. |
| [R-15 C/E/G](R-15-ci-cd.md) | Both leaders support native Windows builds, Android signing, CI UI smoke and screenshots. | App installer, signing and complete screenshot workflows have not run. |
| [R-02 F1/F2/F3](R-02-card-data.md) | Preserved same-day bulk observation reports gzipped JSONL with `jsonl_download_uri`, not a root JSON array. | Fresh access, complete counts, terms and live row validation remain open. R-14's array-parser discussion must not dictate the current importer. |

Primary URLs supporting this synthesis: https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android , https://pub.dev/packages/llamadart , https://developer.android.com/jetpack/androidx/releases/room3 , https://drift.simonbinder.eu/platforms/vm/ , https://kotlinlang.org/docs/multiplatform/compose-desktop-accessibility.html , https://github.com/flutter/flutter/blob/master/docs/platforms/desktop/windows/Accessibility-on-Windows.md and https://api.scryfall.com/bulk-data (accessed 2026-10-11; bulk snapshot inherited from R-02 rather than newly fetched).

Known working setup is specifically Android CI: ubuntu-latest, Temurin JDK 21, setup-gradle v4, unit tests, lint and assembleDebug. It proves familiarity with an Android Gradle pipeline, not working desktop packaging, release signing or Kotlin Multiplatform dependencies. Source: [GATE-0 reference read](../gates/GATE-0.md), read 2026-10-11.

### F2. Current releases and desktop maturity

| Stack | Current release evidence | Date/status qualification |
|---|---|---|
| S1 | Compose Multiplatform 1.12.1 | September 22, 2026; stable latest release. Its Material3 component is 1.12.0-alpha03 and adaptive 1.3.0-rc01, despite stable UI/runtime. https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.1 |
| S2 | Flutter stable family 3.47; 3.47.0 confirmed stable release | Announcement August 12, 2026. Exact newest patch and its release date are UNVERIFIED: dynamically loaded archive rows were absent and release JSON was inaccessible. https://docs.flutter.dev/release/release-notes and https://flutter.dev/blog/whats-new-in-flutter-3-47 |
| S3 | MAUI 10.0.110 | September 22, 2026, latest stable redirect. https://github.com/dotnet/maui/releases/tag/10.0.110 |
| S4 | Avalonia 12.1.4 | October 9, 2026, latest stable redirect. https://github.com/AvaloniaUI/Avalonia/releases/tag/12.1.4 |
| S5 | RN 0.87.1; RN Windows 0.84.0 | RN August 26; RNW June 30, 2026 as shown by current release pages. These are different release trains, not a compatible pair established by this report. https://github.com/react/react-native/releases/tag/v0.87.1 and https://github.com/microsoft/react-native-windows/releases/tag/react-native-windows_v0.84.0 |
| S6 | Tauri CLI 2.12.2 | October 10, 2026; core/plugin versions are separate and UNVERIFIED, not automatically 2.12.2. https://github.com/tauri-apps/tauri/releases/tag/tauri-cli-v2.12.2 |

All table URLs accessed 2026-10-11. No latest prerelease is substituted for stable. Exact Kotlin/KSP/AGP/Gradle tuple is UNVERIFIED until dependency resolution; a stable Compose plugin does not make every component stable.

Compose desktop is a released Windows target, not an experimental Windows extension: official 1.12.1 support lists Windows 10 x64/ARM64, JDK 11+ for desktop and JDK 17+ for native distribution packaging. JDK 21 therefore fits documented minimums. Sources: https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html and https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html (accessed 2026-10-11; packaging detail also R-15 C1). This establishes toolkit maturity, not Narrator support or native ARM64 availability of every dependency.

Windows ARM: S1 toolkit and S2 Flutter both list ARM64; Avalonia lists Windows 11 ARM64 Tier 1. Sources: https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html , https://docs.flutter.dev/reference/supported-platforms and https://docs.avaloniaui.net/docs/supported-platforms (accessed 2026-10-11). MAUI/RNW/Tauri exact pinned end-to-end ARM64 app support remains UNVERIFIED after platform/deployment searches. All six require native SQLite/LLM/key-storage assets to be checked independently. R-14 F1's old Windows-x64-only artifact observation is an unverified inherited measurement, not proof of current S1 ARM64 impossibility. Start release feasibility with Windows x64; do not claim universal ARM support.

Bundled Windows runtime/package size, cold start and steady/peak memory are UNVERIFIED for all six. R-11 F4 has a named Android inference benchmark, not a UI-stack startup comparison. Official size/performance searches returned no comparable published Windows workload. Flutter documents `flutter build windows --analyze-size`, which is a method, not this app's measurement. Source: https://docs.flutter.dev/perf/app-size (accessed 2026-10-11). Measure installer download, installed bytes, runtime-only baseline, SQLite increment, native inference increment and downloaded weights separately. Record cold start to interactive UI and memory on the actual Windows package; no guessed JVM/.NET/WebView cost enters the matrix.

### F3. Added criteria and HTTP evidence

Secure storage is required because users bring paid-provider keys. SSE is required because provider replies stream and must cancel. Accessibility belongs because TalkBack and Narrator are explicitly named, not interchangeable reader brands. CI testability belongs because adaptive breakpoints/hinges and domain rules need reproducible regression coverage. Agent correctness belongs because Codex implements the app, but no controlled evidence ranks these six stacks for this agent/model/task.

S1 Ktor has a client SSE plugin: https://ktor.io/docs/client-server-sent-events.html . S2 `http` supports persistent clients, streamed responses and abortable requests: https://pub.dev/packages/http . S3/S4 HttpClient supports ResponseHeadersRead: https://learn.microsoft.com/en-us/dotnet/api/system.net.http.httpcompletionoption . RN/Tauri selected native response-streaming adapters were not confirmed, so they receive a lower confidence score instead of browser EventSource being assumed sufficient. Sources accessed 2026-10-11.

S2 proposed SSE layer: one shared HTTP service, POST/auth headers as provider requires, incremental UTF-8 decoding and an event parser handling blank-line dispatch, multiline data, comments, event/id/retry and cancellation. Browser EventSource's interface is not a general arbitrary-header POST client. Provider replay/retry must be deliberate to avoid duplicated paid generations. Source: https://html.spec.whatwg.org/multipage/server-sent-events.html (accessed 2026-10-11; parser is proposed application work).

Flutter publishes AI development guidance, but it does not prove better code correctness than Kotlin/.NET/Rust: https://docs.flutter.dev/ai/get-started (accessed 2026-10-11). Agent scores below are explicitly judgment. S1's +1 reflects reuse of Pat's known setup, not an empirical model benchmark. Remove this row and S2 still leads the baseline.

### F4. Weighted matrix

Scale: 0 no verified route; 1 major unresolved requirement/integration; 2 substantial custom work; 3 plausible documented route with significant gaps; 4 strong documented route plus target validation; 5 especially complete/reusable documented route. Scores represent feasibility/integration risk, not measured speed or app test passes. Weight sum 100; total = sum(weight * score) / 5, out of 100. No separate hidden fallback bonus.

| Criterion | Weight | Rationale/evidence | S1 | S2 | S3 | S4 | S5 | S6 |
|---|---:|---|---:|---:|---:|---:|---:|---:|
| Shared Android/Windows codebase | 9 | Mandatory core architecture; platform adapters allowed, R-11 F5/R-13 F5. | 5 | 5 | 5 | 5 | 4 | 5 |
| Adaptive/foldable UI | 12 | Central phone/foldable/desktop scope, R-13 F2-F5. | 5 | 4 | 3 | 2 | 2 | 2 |
| Windows Actions packaging | 8 | Downloadable PC deliverable, R-15 C. | 4 | 4 | 5 | 4 | 3 | 5 |
| SQLite/FTS path | 14 | Offline card corpus and queries, R-14 F2-F5. | 5 | 5 | 5 | 5 | 2 | 5 |
| On-device LLM interop | 10 | Required option on both targets, R-11 F5. | 4 | 4 | 4 | 3 | 2 | 3 |
| Build reliability | 10 | Pinning and native integration burden, R-15 G/F2 release compatibility. | 4 | 4 | 3 | 3 | 2 | 3 |
| Ecosystem maturity | 6 | Stable targets and maintained integration paths, F2/R-13/R-15. | 5 | 5 | 4 | 4 | 4 | 3 |
| Secure key storage | 5 | R-12 comparison, at-rest requirement. | 4 | 5 | 3 | 3 | 1 | 3 |
| Streaming HTTP/SSE | 5 | F3 evidence and remaining custom adapters. | 5 | 5 | 4 | 4 | 3 | 5 |
| TalkBack and Narrator | 11 | Named acceptance requirement, R-13 F7; S1 Narrator unresolved. | 1 | 4 | 4 | 4 | 3 | 2 |
| CI unit/UI/goldens | 7 | Repeatable window/posture regression, R-15 E. | 5 | 5 | 3 | 4 | 3 | 3 |
| Agent implementation reliability | 3 | Judgment only; modest weight avoids invented benchmark authority. | 4 | 3 | 3 | 3 | 3 | 3 |
| **Weighted total /100** | **100** | **Calculated from these raw scores** | **84.0** | **88.6** | **78.8** | **74.2** | **51.8** | **69.8** |

All cited research inputs read 2026-10-11; underlying URLs/access dates are in F1-F3 and those named findings. Database scores rate access/FTS integration, not an established performance ranking. RNW's lower scores reflect multiple current Windows wrapper/architecture gaps, not a claim that native code cannot solve them.

Narrator is a feasibility gate separate from averaging: S1 has not established it. NVDA/JAWS success cannot close a Narrator acceptance check. S2's documentation supplies a credible route, but its app still must pass reader tasks. A high overall score never waives a missing requirement.

Sensitivity uses the same scores and weight ordering as the matrix:

| Scenario | Weight vector | S1 | S2 | S3 | S4 | S5 | S6 | Winner |
|---|---|---:|---:|---:|---:|---:|---:|---|
| Baseline | 9,12,8,14,10,10,6,5,5,11,7,3 | 84.0 | 88.6 | 78.8 | 74.2 | 51.8 | 69.8 | S2 |
| More accessibility | 9,10,8,13,9,9,6,5,5,17,6,3 | 79.6 | 88.2 | 79.4 | 75.2 | 52.8 | 68.6 | S2 |
| More reuse/adaptive/build emphasis | 9,16,8,14,10,14,6,5,5,3,7,3 | 89.6 | 88.6 | 77.2 | 71.8 | 50.2 | 70.6 | S1 numerically |
| More local inference | 9,10,8,12,18,10,6,5,5,7,7,3 | 85.6 | 88.2 | 78.8 | 73.0 | 51.0 | 70.2 | S2 |

Winner is not weight-invariant. Reducing accessibility's weight cannot resolve S1 Narrator feasibility. Score uncertainty matters too: raising S1 accessibility from 1 to 4 following actual proof changes its baseline to 90.6, above S2. Consequently a successful Narrator validation could restore the fallback as winner. These are calculated scenarios, not runtime findings.

### F5. Concrete S2 dependency baseline

These are individually verified published stable package versions, not a validated combined lockfile. Use exact direct constraints plus committed pubspec.lock and an immutable SDK revision. Flutter 3.47.0 is the confirmed stable baseline; newest 3.47 patch/date remains UNVERIFIED and must be resolved before bootstrap.

| Concern | Proposed package/version | Source, accessed 2026-10-11 |
|---|---|---|
| UI | Flutter 3.47.0 plus Material UI 1.6.0; choose the documented decoupled package deliberately. | https://docs.flutter.dev/release/release-notes ; https://pub.dev/packages/material_ui |
| Navigation | go_router 18.0.2 | https://pub.dev/packages/go_router |
| Adaptive panes/hinges | SDK LayoutBuilder/MediaQuery/displayFeatures, own stable-state pane composition; avoid discontinued adaptive_scaffold. | https://docs.flutter.dev/ui/adaptive-responsive/general ; R-13 F4 |
| DB/FTS | drift 2.35.2, sqlite3 3.7.0; generator drift_dev 2.35.1 and build_runner 2.16.2 | https://pub.dev/packages/drift ; https://pub.dev/packages/sqlite3 ; https://pub.dev/packages/drift_dev ; https://pub.dev/packages/build_runner |
| DI and state | flutter_riverpod 3.4.3; hand-declared providers avoid extra codegen dependency. | https://pub.dev/packages/flutter_riverpod |
| Shared HTTP/SSE | http 1.6.0 plus owned SSE framing layer, tested against fragmented input. | https://pub.dev/packages/http ; F3 |
| JSON streaming | Bundled Dart dart:io/dart:convert: gzip stream, UTF-8 decoder, bounded line framing and per-line jsonDecode for R-02 JSONL. | https://api.dart.dev/dart-io/GZipCodec-class.html ; https://api.dart.dev/dart-convert/LineSplitter-class.html ; R-02 F1 |
| Optional root-array adapter | json_events 1.2.2 if a verified alternate provider requires arrays; not needed for current JSONL path. | https://pub.dev/packages/json_events ; R-14 F5 |
| Settings | shared_preferences 2.5.6 for replaceable nonsecret UI preferences; authoritative exportable settings in drift. | https://pub.dev/packages/shared_preferences |
| Images/cache | cached_network_image 4.0.4 plus flutter_cache_manager 3.4.5; shared cache policy must honor R-02 terms once verified. | https://pub.dev/packages/cached_network_image ; https://pub.dev/packages/flutter_cache_manager |
| Secrets | flutter_secure_storage 11.2.0 with actual Android filename exclusions and isolated Windows credential target. | https://pub.dev/packages/flutter_secure_storage ; R-12 |
| Local LLM | llamadart 0.11.1, pin its native artifacts/hash; CPU first and runtime-neutral domain interface. | https://pub.dev/packages/llamadart ; R-11 F5 |
| Unit tests | test 1.33.0 for pure Dart; SDK flutter_test for widgets. | https://pub.dev/packages/test ; https://docs.flutter.dev/testing/overview |
| UI smoke | SDK integration_test on Android and Windows. | https://docs.flutter.dev/testing/integration-tests |
| Screenshots | SDK matchesGoldenFile, fixed fonts/viewports and injected display features; platform baselines separately. | https://api.flutter.dev/flutter/flutter_test/matchesGoldenFile.html ; R-15 E |

Published generator/runtime versions differ; do not force equal numbers or assume compatibility. Minimum Dart/Flutter constraints, Material UI migration compatibility, native sqlite hooks and plugin assets must resolve during Gate 3 bootstrap. No package-resolution command or build has run. A record/line length cap prevents one malicious JSONL record defeating bounded memory; an isolate alone is not a memory bound. Keep application HTTP traffic under one shared service; cache plugins' internally owned transport needs an explicit ownership audit rather than silently claiming it uses that same client.

Fallback libraries: Room KMP and Room 3 with bundled SQLite carry over to desktop JVM (R-14 F2). Navigation Compose requires JetBrains multiplatform coordinates, not simply copying Android-only dependency declarations; 1.12.1 currently lists Navigation 2.10.0-beta01 and stable Navigation3 1.1.2. DataStore Preferences supports KMP and a documented JVM desktop path; platform file factories remain necessary. Sources: https://kotlinlang.org/docs/multiplatform/compose-navigation-routing.html , https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.1 and https://developer.android.com/kotlin/multiplatform/datastore (accessed 2026-10-11). In S2, replace Room with drift, Navigation with go_router and DataStore with authoritative drift settings plus disposable preferences. Kotlin/Gradle Android host code still exists for signing/native adapters; its existence does not make shared Dart code a Kotlin reuse path.

### F6. Terms and licenses

Flutter is BSD-3-Clause; a short redistribution clause is "Redistributions in binary form must reproduce the above copyright notice". Preserve notices/license/disclaimer and avoid implying endorsement. Source: https://raw.githubusercontent.com/flutter/flutter/master/LICENSE (accessed 2026-10-11). Drift/sqlite3 and Riverpod registry entries identify MIT, whose inclusion requirement is detailed in R-14 F8. Secure-storage BSD requirements are detailed in R-12 licensing. SQLite public-domain status does not license card/rules/art data. Ship notices for the resolved full dependency tree; complete transitive license inventory, model licenses and data redistribution permission remain UNVERIFIED until the lockfile/source scope is chosen. Do not select commercial Conveyor/Parcel merely because this repository is public; R-15 C identifies separate license conditions and free packaging alternatives.

## Options

S1 retains the most familiar Android path and excellent foldable/DB support. It remains the fallback and would win the baseline if the named Narrator route is proved at a comparable score. S2 is the strongest documented complete-target alternative and currently wins by 4.6 points. S3/S4 are credible but bring more mobile adaptive/native integration uncertainty; S5/S6 require more simultaneous bridges or mobile testing work. No seventh candidate has evidence warranting expansion of the matrix.

## Recommendation

For D-07, recommend **S2 Flutter**, with the platform checks below recorded as Gate 3 acceptance requirements. It clears the stated fallback departure bar under the full brief's baseline matrix because it pairs a documented Windows Narrator/UIA route with comparable DB/LLM/test coverage and turnkey secrets; this is a concrete reason, not a preference for Dart. The result is sensitive and not a claim of universal superiority.

One-line tradeoff for Pat's ruling: **Choose Flutter to preserve a credible Narrator path on Windows, accepting replacement of the known Kotlin application layer and custom adaptive/SSE composition.**

Gate 2 should specify these Gate 3 implementation acceptance checks: resolve the dependency set; build Android and Windows x64 release packages; verify FTS5 insert/MATCH, JSONL bounded import, vault round trip and corruption handling, SSE POST/auth/cancel, one native inference token stream/cancel, compact/medium/expanded and synthetic tabletop/book screenshots, and actual TalkBack/Narrator keyboard tasks on search/list/detail/key-entry controls. Profile baseline bytes/startup/memory and record real results. Documentation alone does not pass any of these checks. Gate 1 remains research only; no early app spike or code is proposed.

If Pat prefers retaining S1, record actual packaged-runtime Narrator validation as an unresolved Gate 3 requirement. Do not replace that check with NVDA. Successful Narrator proof justifies rescoring S1 and likely retaining the fallback; failure requires a real accessibility implementation or the S2 departure, not silently lowering the requirement. D-07 is a proposed ruling, not already approved by this research file.

## Risks

S2 newest SDK patch and combined dependency compatibility remain unresolved. Material decoupling, plugins' native ABIs, Windows ARM assets, off-main-thread DB/import/inference execution and cache transport ownership need concrete validation. All timing/size/memory figures remain unmeasured. Scryfall JSONL contract/terms and full CR reuse rights are cross-cutting data questions, not fixed by a stack. Weighted scores do not authorize dropping required readers, fake controls or performance budgets.

## Confidence

Medium for S2 over S1 under the unchanged named-reader requirement; high that both have documented DB, native interop and CI routes. Low for actual application behavior, package budgets and model usability because no app build/device test occurred. Matrix calculations were checked locally; required sections and zero em dashes checked. No conflict with GAUNTLET section 10 or Appendix A. Material outcome: do not equate Java Access Bridge's NVDA/JAWS examples with Windows Narrator, and do not reuse a root-array importer against R-02's current JSONL finding.
