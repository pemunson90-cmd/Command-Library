# CI and packaging plan

Kotlin Android v1 CI plan under D-20/D-21. Existing continuity and Kotlin capability workflows are real; application workflows are not yet implemented. orch-kotlin-toolchain-01 supplies source-backed candidates and observed hosted capability; orch-kotlin-m0-01 proves actual resolution/compile/runtime behavior. No remembered pin or tool-version check proves application compatibility.

D-19/D-20: version1 CI is Android plus pure Kotlin/JVM domain jobs on hosted runners. Windows Flutter jobs/artifacts are separate version2 work after complete Android M9 acceptance. K2 uses debug APK compile/install/launch/instrumentation; full permanent-key release signing is later acceptance.

## Pipeline order

1. Candidate compatibility: resolve the Kotlin/AGP/Gradle/JDK/Compose/KSP/Room3/bundled SQLite candidate through actual hosted compile and packaged FTS5 tests. Record resolution/manifests/native packaging and sources. Native inference/Keystore evidence belongs to its later consumed stage; no unused adapter stub. Missing tools are exact blockers, not fake passes.
2. Pure Kotlin/JVM domain job: format/static checks/test/contract/schema/ownership/continuity, exact locked dependency restore. Compile every public port before consumers. Commit small authentic attributed fixtures and separately labeled synthetic edges, not bulk corpus. Run meaningful migration/query/math/reducer/parser checks for changed behavior.
3. Android hosted build: K2 debug APK and hosted emulator install/launch/instrumentation, checksum and commit provenance; no signing-secret access. Complete-app M9 release uses existing single ANDROID_SIGNING only on trusted repository pushes/workflow input. Decode compact JSON/base64 to runner temp, validate four fields and mask sensitive strings before further commands, use no shell trace, compare certificate SHA256 from D03, clean always. Fork/untrusted PR never receives signing material; debug/test job doesn't claim permanent-key release signing.
4. Windows hosted build (v2, D-19): x64 Flutter release, include all required DLL/data/native libraries, per-user unsigned Inno Setup EXE and portable ZIP; no unsignedMSIX install claim. Native vault/FTS/inference launch smoke. Real install evidence from Windows run or stated pending physical check.
5. Android emulator UI smoke/goldens (Windows UI smoke/goldens: v2, D-19): five widths + compactheight/book/tabletop safe-region fixtures,200% text, truthful routes. Exact native/physical reader/sensors separately tested, never passed by synthetic posture. Android update-install test: install the previous signed APK with `adb install`, then the new build with `adb install -r`, and confirm the app keeps its data and launches (IMPOSSIBLE.md RJ-05).
6. Artifact/report job: APK checksum/certificate/app version/build SHA (EXE/ZIP checksum: v2, D-19), platform/runtime/license notices, CI/test/golden links, install notes and pending physical evidence in milestone report. Do not publish a public release without recorded user scope/terms permission.

## Controls

Least permissions contents:read for normal jobs; write only explicit artifact/release purpose if approved. Action refs pinned to reviewed immutable commits. Dependency updates separate, audited PR/commit. Runs grouped by branch, duplicate work prevented, maximum2 build jobs, bounded runner timeouts and artifact retention14days unless changed for concrete need. No credentials/log/prompt source raw dump; no untrusted pull-request code in secret-bearing context.

No user workstation is a build requirement. A publisher capable of committing push-triggered workflows can start hosted checks even when workflow dispatch tool is unavailable; record actual run URL/result, don't claim a file triggers a successful run. Build failures are next task input with exact step/log link (redacted), not reasons to skip platforms.

## Initial M0 acceptance

Actual locked SDK, core/public interface smoke, SQLiteFTS5 tiny fixture, native vault smoke, navigation only into working tiny-import/search/status route, existing-key signedAPK from hosted CI (EXE/ZIP: v2, D-19). Runtime interfaces for future features are legitimate abstract contracts, but no stub feature UI or fabricated source/LLM response. Components only imported when dependency interfaces/builds reviewed.

Performance targets retain PERFORMANCE semantics. Current full source/physical/ref-hardware checks are E01-E13 and must migrate into component task test evidence, not disappear.
