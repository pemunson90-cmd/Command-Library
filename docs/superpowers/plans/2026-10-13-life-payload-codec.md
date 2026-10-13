# Life payload codec implementation plan

> **For agentic workers:** Use superpowers:executing-plans for inline implementation and independent exact-source review under the active task.

**Goal:** Complete typed, bounded snapshot/profile/event/archive/checkpoint serialization for later transactional storage.

**Architecture:** Dependency-free binary framing primitives, explicit model codecs and typed Outcome facade in data:storage. Reconstruct DTOs through their constructors; independently frame archive snapshots.

**Tech Stack:** Kotlin, java.io and java.nio charset codecs, existing JUnit4/Gradle.

**Spec:** ../specs/2026-10-13-life-payload-codec-design.md

## Global constraints

- Snapshot/profile4MiB, event/archive16MiB, checkpoint4MiB+64KiB; stringUTF8<=65536, definitions<=128, flat group<=1024.
- Stable tags, strict EOF/UTF8, finite lossless seconds/nanoseconds durations.
- No schema/service/DI/whole-history transfer acceptance.

## Review focus

- Duration near representation boundaries must not round or saturate.
- Raw foreign card metadata and unknown evidence must survive.
- Corrupt lengths/tags must fail before allocation.
- Archive before/after counters/definitions get separate budgets.
- Every existing action/variant/alert/Knowledge branch must have a codec.

### Task 1: Framing and complete snapshot/profile roots

Files: create life/codec/LifeBinary.kt, LifeStateWires.kt, LifePayloadCodec.kt; tests LifePayloadCodecTest.kt.

- [ ] Write API-absence/behavior tests for duration extremes, metadata-rich roundtrip, strict UTF8/root/version/EOF and string/definition budgets.
- [ ] Run tests RED; implement bounded framing and explicit snapshot/profile codecs.
- [ ] Run storage/model/life JVM checks GREEN.

### Task 2: Event/archive/checkpoint roots

Files: create life/codec/LifeEventWires.kt; extend facade and tests.

- [ ] Test all action/random/provenance branches, flat groups and independent archived snapshot budgets RED.
- [ ] Implement explicit event/request/result codecs; frame before/after snapshots independently; preserve reading/checkpoint lineage.
- [ ] Run affected whole-app checks; request independent source review, resolve findings, publish evidence and truthful continuation checkpoint.
