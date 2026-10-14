# Game Store Foundation Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline; independent architecture/durability review before implementation/acceptance.

**Goal:** Transactional idempotent game creation and checked core/seat read persistence.
**Architecture:** Concrete feature/life store over Database+codec port with lazy engine initializer; no GameService binding. Storage integration tests use the existing test-only life dependency and actual host SQLite/codec.
**Tech Stack:** Existing Kotlin/JVM/JUnit4/SQLite2.7.1 and SHA256; no dependency/schema change.
**Spec:** ../specs/2026-10-14-life-game-store-design.md

## Global constraints

Strict UTF8/bounded metadata before blobs; creation4MiB/checkpoint4MiB+65536; strings65536/folds262144bytes. No placeholders, source authority or full service claim. Local-first.

## Review focus

Retry after current settings changed must return current game without initializer; operation conflict never replaces data; invalid SQL TEXT must not masquerade as a valid Unicode projection; last seat failure must roll back parent; unsupported authentic variant setup must not be dropped.

### Task1: Actual checked creation/read store

Files: feature/life/persistence/SqlLifeGameStore.kt; focused store row/invariant helpers if needed; storage/src/test life/SqlLifeGameStoreTest.kt using existing HostLifeDatabase; this plan/spec and task evidence.
Interfaces: public StoredLifeGame(creation,checkpoint,sequence); SqlLifeGameStore(Database,LifePayloadCodecPort).create(input,initialize,cancel) and read(id,cancel), exact signatures in spec. No core port modification.

- [x] Written spec review: settle initializer and read-validation boundaries; preserve full-service follow-up obligations.
- [x] Write real SQLite creation/reopen/idempotency/conflict/collision tests; API-absence then declaration-only behavioral RED.
- [x] Implement atomic create/read and bounded strict core/seat projection checks. Initial scope/variant checks derive only from explicit creation inputs.
- [x] Add corruption, changed-current-state retry, callback failure/UNSUPPORTED and cancellation/partial-seat rollback tests; observe meaningful RED→GREEN.
- [x] Run appropriate storage/model/life tests, independent exact-source durability review and coherent local whole check/debug as warranted; inspect actual results and alignment.
- [x] Publish truthful evidence/task/board/checkpoint, then next event/checkpoint/service unit. No component-end stop or emulator per feature.

## Execution ledger

Task1 start b8b103e; repository clean after restart. Design rationale review recommends lazy initializer; exact written spec/plan reviewed APPROVE; genuine absent initial lineage and complete unique variant zones clarified. Implementation/tests not_run.

API-absence RED52s: missing SqlLifeGameStore and its dependent inferred result members. Declaration-only behavior run in progress; no implementation accepted. Initial9 actual integration tests on disk; HostLifeDatabase payload instrumentation now counts actual payload Blob columns instead of SQL string prefixes.

Declaration-only RED9/9 failures55s; actual first GREEN9/9 in73s. Exact bounded-source durability review APPROVE; no blocker. Expanded15-case storage suite in progress, including a measured inner snapshot fixture intended >4MiB but below outer budget. First snapshot fixture was clarified to supply genuine Absent turn lineage (model legacy default is Unknown and is intentionally rejected for new creation). No new core/DI/schema changes.

Task1 complete: expanded64storage tests/8XML including15Store cases,0failure/error/skip125s; final coherent68XML682JVM/check/debug0failure/error/skip76s; alignment pass. Exact source review APPROVE, no blocking/deferred finding. Next structural graph guard task remains separate and must precede operational binding.
