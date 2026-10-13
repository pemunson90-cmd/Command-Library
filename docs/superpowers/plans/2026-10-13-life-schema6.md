# Life schema6 implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline; the life task requires independent schema/durability review.

**Goal:** Add durable life tables without altering any existing storage data.

**Architecture:** Six Room entities plus explicit additive SQL migration, with indexed projections beside typed authoritative payloads. Registration/export are root-owned; real service follows later.

**Tech Stack:** Room3/KSP, Kotlin, existing bundled SQLite driver/JUnit4/coroutines.

**Spec:** ../specs/2026-10-13-life-schema6-design.md

## Global constraints

- Preserve all eighteen schema5 entities/indexes exactly.
- Composite game identities; revision+checkpoint sequence CAS; unique non-null undo target.
- Profiles independent from game deletion; explicit game deletion only cascades life children.
- No service/OS delivery/physical acceptance inferred from tables.

## Review focus

- Migration rollback must preserve schema5 state.
- Same-revision stale sequence updates must not overwrite newer timing.
- Two undo records cannot target one game event.
- Reopening retains opaque payload bytes and old acquisition/catalog/rules data.
- Profile deletion/history retention and game-child FK ownership stay distinct.

### Task 1: Schema and actual SQLite migration

Files: create schema/LifeTables.kt, Migration5To6.kt; modify StorageSchemaDatabase.kt and StorageDatabase.kt; create LifeMigrationTest.kt and exported schema5 fixture resource; retain generated6.json.

- [ ] Generate schema5 SQL fixture from committed5.json and verify provenance; write migration/constraint/CAS/reopen/rollback tests before implementation.
- [ ] Run APIabsence RED; implement exact six-table migration/entities/registration.
- [ ] Run local realSQLite/storage JVM tests and actualKSP6 compile. Compare old18 exports and physical schema/index/FK to new export.
- [ ] Independent source/durability review; fix and rerun affected checks.
- [ ] Publish truthful checkpoint; next is checked storage adapters and service action/timing contracts.
