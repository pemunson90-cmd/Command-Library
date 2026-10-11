# K1 Kotlin readiness

Status: K1 readiness accepted by [independent Astra-high final review](K1-FINAL-REVIEW.md). The K2 card is accepted for a bounded compatibility attempt and functional foundation build. No Android application or Kotlin compatibility result is claimed.

## Selected candidate and sources

Official source observations dated 2026-10-11 UTC are captured in [candidate lock](../../../config/kotlin-android-candidate-lock.json), [Android source notes](K1-ANDROID-SOURCES.md), and [FTS5 source notes](K1-FTS5-SOURCES.md). These distinguish documentation, artifact inspection and required runtime evidence.

| Item | Selected candidate | Boundary |
|---|---|---|
| AGP / Gradle / JDK | 9.4.1 / 9.6.0 / 17 | Official AGP requirements; Gradle distribution SHA-256 pinned |
| Application Kotlin / Compose compiler | 2.2.10 / 2.2.10 | AGP 9.4.1 published POM built-in Kotlin default; no separate Android Kotlin plugin; K2 must inspect actual resolution |
| Compose BOM | 2026.09.00 | Source-backed, not compiled |
| Room / compiler / KSP | androidx.room3 3.0.3 / 3.0.3 / 2.3.12 | Room3 namespace and FTS5 API; actual KSP generation untested |
| SQLite bundled | 2.7.1 | FTS5 driver candidate; native AAR static 16-KiB alignment observation is not final APK/runtime proof |
| DataStore | 1.2.1 | Source-backed, not compiled |
| Android | min 23; compile/target 36; build-tools 36.0.0 | Candidate; K2 must inspect merged manifest/native packaging and minimum-API execution |

Latest discovered versions were not automatically selected: Gradle 9.8.1, Kotlin 2.4.21 and Room2 2.8.5 observations remain historical discovery. Room3 is a distinct namespace. Gradle's embedded Kotlin 2.3.21 printed by --version is its build-script runtime, not the application's selected Kotlin compiler.

## Observed hosted capability

[Capability run 38177202140](https://github.com/pemunson90-cmd/Command-Library/actions/runs/38177202140) succeeded at tested commit `210d256a058c46fc620dff9ae951b919695937aa`, job `114581983584`. Decoded job logs were read on 2026-10-11 UTC. Runner: Ubuntu 24.04.5, image `20261004.327.1`.

Actual output reports Temurin Java/javac 17.0.20.1, checksum-verified Gradle 9.6.0, sdkmanager 12.0, aapt2 2.20-13193326 and adb 37.0.1-15733141. The script checked Android platform36, build-tools36.0.0 and apksigner executable availability. It ran no Kotlin project, Compose/Room/KSP compilation, FTS5 runtime, signing or app/device tests. [Probe design](K1-PROBE-DESIGN.md) records the independent narrow review and permissions.

[Continuity run 38177202244](https://github.com/pemunson90-cmd/Command-Library/actions/runs/38177202244) also succeeded on that commit, including twelve checkpoint/continuity regression tests. Later changes require their own continuity result; this run is not evidence for later commits.

[Continuity run 38178006460](https://github.com/pemunson90-cmd/Command-Library/actions/runs/38178006460) succeeded on evidence checkpoint commit `5f3b752f491ea80c7cf922a4260fffe8cc4298b9`, including all23 checkpoint/architecture regression tests. The final closure checkpoint is verified separately; no continuity run is application evidence.

Closure validation found the lease timestamp check was active-only; releasing the lease exposed that gap. The check now validates active and released UTC timestamps, with an independently Luna-reviewed released-expiry regression. All24 local script tests pass. This narrow continuity fix changes no application or architecture behavior.

Local authoritative checkout is now /workspace/Command-Library-kotlin; the earlier /workspace/Command-Library text snapshot is preserved. Local Java21 runtime has no compiler/Android SDK/Gradle; no local app build was attempted. Hosted tooling supplies the next stage's build transport.

## Architecture and next section

[Kotlin ownership map](../../architecture/KOTLIN_OWNERSHIP_MAP.md), [machine registry](../../architecture/kotlin-registry.json), and [contract translation](../../architecture/KOTLIN_CONTRACTS.md) preserve twenty components, sixty-four requirements and the complete acceptance inventory. Twenty-two eventual modules include two core modules and a separate orchestrator-owned bootstrap; component dependencies retain the accepted DAG. K2 creates only its functional subset, with complete public contracts compiled before consumers and no future adapter stubs.

[K2 task card](../../../coordination/tasks/orch-kotlin-m0-01.md) defines a working local catalog/import/settings/status UI, genuine fixture provenance, hosted compile, debug APK, emulator launch and meaningful FTS5/cancellation/persistence checks. Debug foundation evidence is an internal milestone; complete-app acceptance and permanent-key signing remain later work. Physical Fold7/TalkBack and performance evidence must be observed, never inferred from CI.

## Remaining proof

K2 must prove the selected tuple through actual resolution/compile, generated Room3 code, packaged bound FTS5 MATCH behavior, cancellation/transaction durability, persistence, minimum API and final native alignment. Candidate acceptance permits a bounded compatibility attempt, not a validated production binding. If it fails, record the actual diagnostic, correct only the affected candidate with source evidence and independent review, and rerun the affected check.

Standalone future API/terms/source-format research is deferred to [regular-chat packets](../../../coordination/chat-handoffs/README.md). It is required before affected consumers ship, not before unrelated foundation work. Windows and sync remain deferred under D-19/D-20.

