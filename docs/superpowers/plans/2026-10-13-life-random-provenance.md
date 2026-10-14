# Life random/provenance recovery implementation plan

> **For agentic workers:** Use superpowers:executing-plans for inline execution; independent review is required by the active life task.

**Goal:** Recover the next unpublished model unit: replayable random results and immutable action-time undo provenance.

**Architecture:** Add model DTOs and compatible default fields only. Existing reducers remain unchanged; service-level lineage, persistence and random draws remain separate work.

**Tech Stack:** Kotlin/JVM, immutable SnapshotList, JUnit4, existing Gradle wrapper.

**Spec:** ../specs/2026-10-13-life-persistence-contract-design.md, Random requests and Persistent event envelope sections. Independent review approved this bounded subset on 2026-10-13.

## Global constraints

- Batch indices are Long; each committed batch consumes min(remaining, 10000).
- Recorded payload validation rejects malformed values without truncation.
- No codec, schema6, service implementation, DI or operational UI acceptance.
- Original thread branch and unavailable unpublished files remain preserved.

## Review focus

- Long.MAX_VALUE dice requests retain exact final remainder without overflow.
- Revision MAX-1 to MAX is valid; MAX cannot increment.
- Different game/event identities or after-action event pointers cannot be archived together.
- Recorded selections are empty exactly when no eligible seat exists; duplicates fail.
- Nonfinite or negative monotonic readings fail; unrelated clock anchors remain legal.

### Task 1: Replayable random contracts

Files: create LifeRandomContracts.kt and LifeRandomContractsTest.kt under core/model; append RequestRandom and GameEvent.randomResults in GameContracts.kt.

- [x] Write tests for positive input, capped batches, exact sums/remainders, large Long batch indices, and malformed selections.
- [x] Run :core:model:test and confirm missing API failure, then declaration-only behavioral RED.
- [x] Implement RandomRequest, RecordedRandomValue and RecordedRandomResult; validate exact capped batching with division guard before index multiplication.
- [x] Run :core:model:test :feature:life:test; verify XML failures/errors/skips.

### Task 2: Immutable action provenance

Files: create LifeEventContracts.kt and LifeEventContractsTest.kt under core/model; append GameExchange.actionArchives in TransferContracts.kt.

- [x] Write tests for valid revision boundary, mismatched game/event/pointers, finite readings and checkpoint IDs.
- [x] Confirm missing API failure and declaration-only behavioral RED.
- [x] Implement GameTimeObservation, GameActionProvenance, GameCheckpoint and GameEventArchive with local consistency checks.
- [x] Run focused tests and affected whole-app check; request exact-source independent review and resolve findings.
- [x] Publish truthful verification and continuation checkpoint with exact next codec design action.

## Execution ledger

- Recovery baseline:54 model+81 life JVM tests, zero failures/errors/skips. Published recovery8112922; original remote464d875 unchanged.
- Independent reviewer approved bounded random/provenance subset with Long indices and full capped batches. Remaining action families deliberately stay separate.
- API-absence RED: compileTestKotlin failed on absent new types/fields,50s. Declaration-only behavioral RED in progress.
- Ruling: continue existing approved execution inline; recovery is not a new product-design authorization. Architecture and exact-source reviews remain required.

- Behavioral RED:66 model tests,11 expected failures. GREEN:66model+81life,0failure/error/skip,72s. Reviewer exact-source APPROVE; no service/codec acceptance. Whole-app check will run with the codec unit.
