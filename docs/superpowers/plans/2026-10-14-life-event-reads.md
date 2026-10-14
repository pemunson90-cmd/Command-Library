# Checked event/archive reader implementation plan

> Execute inline with superpowers:executing-plans; independent root-owned design/source review by existing life reviewer.

Goal: bounded actual SQLite retrieval of immutable archives/events with strict projection consistency and canonical game-bound keyset paging.
Spec: ../specs/2026-10-14-life-event-reads-design.md. Existing Kotlin/model/Database/codec/schema6, no dependency/schema/core port/DI/UI changes; local-first recovery branch/root lock.

## Task1: Reader and checked metadata

Files create feature/life/persistence/SqlLifeEventReader.kt, LifeEventRows.kt, LifeEventCursor.kt; data/storage test/life/SqlLifeEventReaderTest.kt. Test-only HostLifeDatabase counts archive_payload retrieval as blobQueries.

- [ ] Independent design review; incorporate concrete findings before code.
- [ ] Write actual SQLite APIabsence tests for archive/events methods, observe missingAPI RED. Add declaration-only methods observe expected behavioral RED.
- [ ] Implement archive(id,eventId,cancel) and events(id,page,cancel) signatures per spec with gameTransaction. Metadata CASE/types/budgets first; exact projection/decode/knownUndo target checks; page prefix<=32MiB and cursorlastreturned, no role guessing on historical snapshots.
- [ ] Tests pin reopen/metadata/string/type/projection/provenance/group/undo/version/errors and pages/byteprefix/canonical binding/SQL composite IDs; preserve all random/provenance metadata. Budget fixture must exceed32MiB total while every archive<=16MiB, and not rely on artificial production limit injection.
- [ ] Local :data:storage:testDebugUnitTest GREEN actualXML; independent exactsource review/fix concrete blockers with regressions.
- [ ] Local check :app:assembleDebug, actual XML count and APK alignment, continuity/Python checks; record truthful evidence/taskdelta/checkpoint and publish. Claim next prerequisite; no finished-app or service claim.

Review focus: nonmonotonic event IDs sort by revision; source/raw concatenation collision rejected by length-framed digest; unknown undo metadata preserved but not operationally certified; phase/settings changes do not rewrite historical snapshots; byte-limited pages resume last returned tuple instead of skipping candidates. Each pinned in actualSQLite cases above. No current/settledpointer or authenticated undo policy introduced in this unit.
