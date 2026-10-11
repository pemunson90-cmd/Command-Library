# Task orch-kotlin-toolchain-01

Status: completed, independently accepted. K0 accepted and execution released under D-21. Official candidate sources, reviewed capability workflow, observed hosted success and final Astra-high map/contracts/K2-card review are recorded. Application compatibility/compile/runtime/signing remain unrun; K1 permits the bounded K2 attempt.

## Purpose

Produce a reviewed Kotlin/Android readiness proposal for version 1. This task does not create the Android application.

## Outputs

`config/kotlin-android-candidate-lock.json`, `docs/gauntlet/gates/K1-KOTLIN-TOOLCHAIN.md`, a Kotlin ownership/replacement map for the historical `packages/<component>` and pubspec contracts, a task handoff and, if needed, a documentation-only capability probe. The candidate lock records exact sources, access dates, version constraints, licenses and observed probe results. It contains no signing material.

## Work sequence

1. Re-fetch `main`, claim a lease, confirm K0 acceptance and verify the recorded explicit user execution release. Verify actual checkout, GitHub Actions and network capabilities.
2. From official sources, gather compatible Kotlin, Android Gradle Plugin/Gradle/JDK, Compose, Room/SQLite, possible FTS5 integration, DataStore and Android Keystore requirements. Record candidate values as unverified until targeted compatibility evidence observes them. A tool-version probe cannot prove Compose, Room or FTS5 compatibility.
3. Design a bounded hosted probe that initializes no product code, reads no secret, and reports tool versions. Run it only after an independent reviewer accepts the proposed probe.
4. Completed: Kotlin ownership/registry/contracts and concrete K2 card published and independently accepted. Hosted capability run38177202140 passed at210d256a; evidence checkpoint5f3b752 passed hosted continuity run38178006460 with23 tests. Final Astra review resolved catalog capability segregation, preserving the full aggregate contract without a partial implementation. No application exists yet. Successful K2 compile/runtime proof is still required before any binding is treated as validated.

## Stop rules

Do not create Gradle modules, Kotlin source, database schema, fixture, signing flow, application build, release artifact or Windows/Flutter source. Escalate to GPT-6 Astra high when compatibility, FTS5 packaging, encryption or source-license conclusions require judgment. Stop after the reviewed readiness report.

