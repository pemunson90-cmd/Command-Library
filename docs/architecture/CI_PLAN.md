# CI and packaging plan

Gate3 plan, not existing build workflow. orch-toolchain-01 verifies official current Flutter/Dart/package/native tuple and immutable action/runtime SHAs before workflows/apps code. Do not paste remembered versions or assume old Kotlin workflow exists.

D-19: version 1 CI is Android plus platform-neutral pure-Dart jobs, which may run on Linux runners. Every Windows runner job and Windows artifact below is marked "(v2, D-19)" and is built only after M9.

## Pipeline order

1. Dependency/toolchain probe: resolve exact stable Flutter SDK commit/archive checksum and generated Dart version; compatible Android Java/Gradle/SDK/NDK, bundled SQLite, native llama.cpp/compiler and chosen Windows installer (v2, D-19). Verify source licenses and stable vault bridge. Record resolved tuple/evidence; commit lock manifest. Missing platform tool/permission is a named blocker with safe alternative, not fake resolution.
2. Pure domain PR job (platform-neutral, Linux runner allowed): format/analyze/test/contract/schema/ownership/continuity checks, exact locked dependency restore. Small authentic sanitized fixtures committed, bulk corpus not committed. Migration/query/math/reducer/parser tests relevant to changes.
3. Android hosted build: signed release APK using existing single ANDROID_SIGNING only on trusted repository pushes/workflow input. Decode compact JSON/base64 to runner temp, validate four fields and mask sensitive strings before further commands, use no shell trace, compare certificate SHA256 from D03, clean always. Fork/untrusted PR never receives signing material; unsigned/test job doesn't claim release-signed success.
4. Windows hosted build (v2, D-19): x64 Flutter release, include all required DLL/data/native libraries, per-user unsigned Inno Setup EXE and portable ZIP; no unsignedMSIX install claim. Native vault/FTS/inference launch smoke. Real install evidence from Windows run or stated pending physical check.
5. Android emulator UI smoke/goldens (Windows UI smoke/goldens: v2, D-19): five widths + compactheight/book/tabletop safe-region fixtures,200% text, truthful routes. Exact native/physical reader/sensors separately tested, never passed by synthetic posture.
6. Artifact/report job: APK checksum/certificate/app version/build SHA (EXE/ZIP checksum: v2, D-19), platform/runtime/license notices, CI/test/golden links, install notes and pending physical evidence in milestone report. Do not publish a public release without recorded user scope/terms permission.

## Controls

Least permissions contents:read for normal jobs; write only explicit artifact/release purpose if approved. Action refs pinned to reviewed immutable commits. Dependency updates separate, audited PR/commit. Runs grouped by branch, duplicate work prevented, maximum2 build jobs, bounded runner timeouts and artifact retention14days unless changed for concrete need. No credentials/log/prompt source raw dump; no untrusted pull-request code in secret-bearing context.

No user workstation is a build requirement. A publisher capable of committing push-triggered workflows can start hosted checks even when workflow dispatch tool is unavailable; record actual run URL/result, don't claim a file triggers a successful run. Build failures are next task input with exact step/log link (redacted), not reasons to skip platforms.

## Initial M0 acceptance

Actual locked SDK, core/public interface smoke, SQLiteFTS5 tiny fixture, native vault smoke, navigation only into working tiny-import/search/status route, existing-key signedAPK from hosted CI (EXE/ZIP: v2, D-19). Runtime interfaces for future features are legitimate abstract contracts, but no stub feature UI or fabricated source/LLM response. Components only imported when dependency interfaces/builds reviewed.

Performance targets retain PERFORMANCE semantics. Current full source/physical/ref-hardware checks are E01-E13 and must migrate into component task test evidence, not disappear.
