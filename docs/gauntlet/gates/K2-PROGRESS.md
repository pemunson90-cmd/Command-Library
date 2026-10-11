# K2 foundation progress

Status: in progress, incomplete. No app or complete section release exists. Resume from CURRENT/state, not this report's historical next sentence.

## Contract and design work

Core draft is exclusively owned on agent/k2-core-contracts; no consumer imports it until hosted compilation and independent actual-contract review pass. Root draft [foundation design](../../architecture/K2-FOUNDATION-DESIGN.md) defines local capability segregation, bounded import, canonical/printing/face relationships, FTS binding, generation activation, cancellation and persisted settings. Review pending. Source/API/terms research remains with Pat in ordinary chat; unrelated foundation work is eligible.

## Build transport preparation

Local Java21 runtime cannot compile Java/Kotlin or build Android. It executed only Gradle's built-in wrapper generator from an official Gradle9.6.0 distribution downloaded through the inherited HTTPS proxy and verified against pinned SHA-256 `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01`. The initial JVM URL HEAD validation failed because that client did not use the curl proxy transport; successful verified HTTPS byte retrieval was already observed. Wrapper generation reran with URL validation disabled only for that generation task; committed wrapper properties restore validation and retain the distribution hash. No TLS verification was disabled. Generated wrapper JAR SHA-256: `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`.

Root created only the consumed pure core/model/common Gradle modules. Kotlin JVM candidate2.2.10 follows K1. Coroutines1.10.2 is deliberately compatible older stable rather than latest1.11.0; official published POM declares Kotlin stdlib2.1.0. JUnit4.13.2 POM/metadata observed. Sources dated2026-10-11: https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.10.2/kotlinx-coroutines-core-jvm-1.10.2.pom and https://repo.maven.apache.org/maven2/junit/junit/4.13.2/junit-4.13.2.pom. These are candidate/source observations, not a compile result or complete transitive license inventory.

The hosted contract workflow is scoped to agent/k2-contract-compile or explicit trusted manual main execution, with contents-read permission, fifteen-minute timeout, K1-observed JDK17, no signing secrets and no automatic product acquisition. Checkout v7.0.1 SHA is retained; upload-artifact v7.0.2 release and immutable commit `cf430e030ddbb5b0abf93d22962f4752f3646cd9` were verified using its official GitHub release/tag API. Source-presence/public-port guard, actual core check tasks and nonzero successful JVM reports for each core module prevent an empty module from being reported as contract evidence. Independent Luna-medium narrow workflow review found no blockers; final properties restore validation and report checks were strengthened to both modules. No run yet.

## Parallel Claude Code

Pat explicitly requested parallel work. [CLAUDE-PARALLEL.md](../../../CLAUDE-PARALLEL.md) and task cc-k2-qa-01 assign an isolated branch and exclusive docs/verification/k2-claude/** folder, with synthetic vectors clearly labeled. Codex retains orchestration/shared config; Claude does not edit main or core. Root checks the worker branch and integrates only reviewed changes. No worker result is assumed until actually pushed/read.

## Still required

Actual all-port compile, meaningful JVM results and independent design/contract review; real Room3/storage/import/settings/Compose implementation; authentic fixture provenance; packaged FTS5/transaction/persistence tests; emulator install/launch/adaptive checks; debug artifact/checksum/native alignment; final independent implementation review. All app, Room/FTS runtime, device and signing evidence remain unrun. Record exact diagnostics and correct affected candidates when observations require it; no guessed success or silently skipped checks.
