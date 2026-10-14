# Life Profiles Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline; independent durability review before acceptance.

**Goal:** Real checked SQLite persistence for reusable life profiles.
**Architecture:** Model codec port, pure-JVM life repository, actual storage codec/SQLite host integration tests. No production reverse dependency.
**Tech Stack:** Existing Kotlin/JVM, core Database, bundledSQLite2.7.1, JUnit4.
**Spec:** ../specs/2026-10-14-life-profiles-design.md

## Global constraints

No schema/version/production dependency change. Codec limits4MiB, page8MiB, IDs256UTF8bytes. No fake service registration; preserve game history. Local-first checks.

## Review focus

Invalid generator IDs never truncate; stale revision never overwrites; corrupt projection never returns success; budget continuation uses last returned ID; cancellation before commit leaves original bytes.

### Task1: Codec port and repository integration

Files: core/model/LifePayloadCodecPort.kt; data/storage life codec; feature/life/persistence/SqlPlayerProfileRepository.kt; data/storage/src/test life/SqlPlayerProfileRepositoryTest.kt and HostLifeDatabase.kt; storage build.gradle.kts test-only dependency.
Interfaces: existing PlayerProfileRepository; codec port has the twelve existing public encode/decode signatures from LifePayloadCodec.

- [x] Write actual SQLite/codec CRUD, reopen and CAS tests; observe missing API RED, then declaration-only behavior RED.
- [x] Implement codec port/conformance and checked repository per spec.
- [x] Test malformed IDs/cursors, max revision, corrupt blobs/namefold/nanos/length, paging budget and continuation, collisions, cancellation and history preservation.
- [x] Run :data:storage:testDebugUnitTest, then whole check/debug build after focused green; inspect XML totals and alignment.
- [x] Obtain exact source/durability review; resolve concrete findings with meaningful RED→GREEN tests.
- [x] Record actual evidence, update task/board/checkpoint and publish recovery branch. Continue to next service unit.

## Execution ledger

Task1 start: base07fdbd0; design reviewed. Implementation not_run. Existing recovery branch is isolated from main and other component paths.

API-absence RED:95s missing repository (also corrected test-only inline-class lateinit declaration). Declaration-only behavior RED:8/8 fail with intentional RED exception,85s. Host harness/spec independent review approved; first implementation test run pending.

First actual SQLite GREEN8/8,65s. Expanded12-test run reproduced oversized metadata fetching1blob instead of0 (11pass/1fail),81s. Root cause: name/fold strings selected unbounded and checked against payload only after fetch. Fix bounds variable columns in SQL CASE/byte-length before driver retrieval, typed integer projection and qualified binary ID ordering. Independent review found API26 java.util.Base64 linkage; javap confirmed references. Replaced with Kotlin portable URLsafe encoder, preserving literal cursor golden. Guard ID UTF16 length before UTF8 encoder allocation. No API23 execution claim.

Whole check/debug GREEN666/67XML,453s, alignment pass. Late13th profile test separately RED64s: SQL CAST(XFF AS TEXT) name/fold projected through driver replacement to UFFFD, matching genuine UFFFD payload, returned Success instead of CORRUPT_DATA. Root cause confirmed in actual SQLite. Metadata now projects bounded UTF8BLOBs and strictly decodes before any payload fetch; full verification rerun pending.

Task1 complete: final wholecheck/debug667cases/67XML/0fail-error-skip,113s; actual49storage including13profile cases. UTF8 corruption RED→GREEN; portable cursor golden retained, bytecode linkage absent; alignment pass. Independent source findings resolved in one fix pass; no deferred finding. Evidence PROFILES-LOCAL-20261014.md.
