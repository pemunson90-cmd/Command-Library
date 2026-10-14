# Transactional life mutation implementation plan

> Execute inline with superpowers:executing-plans; existing independent reviewer owns design/source review, root remains sole shared/life writer.

Goal: atomic checked action/checkpoint/operational ledger persistence before real service binding.
Spec: ../specs/2026-10-14-life-game-mutations-design.md. Existing Kotlin/Database/codec/schema6; no dependencies/schema/core port/DI/UI change. Local-first isolated recovery branch/root lock. Read current task delta before execution; pending clock-policy prerequisite is completed at3631654.

## Task1: Transaction-local readers and operational projection guards

Modify SqlLifeGameStore/SqlLifeEventReader to expose internal readWithin(DbTransaction,...) facades preserving public behavior and no nested transactions. Create LifeOperationalRows.kt for strict bounded result/alert projections; LifeMutationGuards.kt for current spine/packet checks; tests dedicated storage/life/SqlLifeGameMutatorTest.kt.

- [x] Independent written design review, resolve concrete findings before product code.
- [ ] Write APIabsence and behavioral RED actualSQLite cases for current spine/expected revision+sequence and corrupt result/alert metadata. Define LifeActionWrite and SqlLifeGameMutator signatures exactly as spec.
- [ ] Implement reusable internal readers, checked current spine and operation metadata in bounded chunks<=100. Preserve strict UTF8/null/type distinction and original creation.

## Task2: Atomic actions, checkpoints and result/alert bookkeeping

Create SqlLifeGameMutator.kt, LifeOperationalWrites.kt; same actualSQLite test file (split fixtures/test classes if necessary, not a production test seam).

- [ ] RED action/systemcheckpoint CAS success/stale/overflow/callback failure, before/after/lineage mismatch, undo target duplicate/unknown, size guards beforewrites.
- [ ] Implement same transaction action(IDmax+1) and checkpoint(noevent/samerev/newseq), all caller packet checks and preencoding, exact1CAS then event/currentseats/result/alerts updates; returned success only after commit.
- [ ] RED finish/retract/refinish stable result identity, ack/firedID carry-forward, deliverymask OR idempotence and invalid channels/targets; implement guarded projection writes and markDelivered without revision/sequence mutation.
- [ ] RED actual trigger failures/cancellation at each write stage; assert reopen all tables match prior state and archive original immutable. Cover original/currentsettings distinction, nonmonotonic eventIDs, legacy unsupported lineage without codec rewrite.
- [ ] Focused actualSQLite GREEN and independent exactsource review; fix blockers with regression tests, no unverified semantic-engine claim.
- [ ] Coherent local check :app:assembleDebug/XML/alignment, continuity/Python, evidence/taskdelta/checkpoint and publish. Continue next real engine/service prerequisite; no partial GameService registration.

Review focus: timestamp equality is NEW writer policy, never legacy read rejection; known undo reference is not latest-action/semantic authority; acknowledgment/delivery is operational and never undone; system sequence differs from action revision and immutable event archive; callbacks can rerun after rollback and cannot perform external effects. Each pinned by Task2 tests. Design review may narrow/split boundaries only with an explicit task/spec ruling and honest unregistered scope.

Design review amendments incorporated: one narrow settlement guard reused for last-after/current/before/system; gameElapsed never decreases through any action; first result nonretracted; stable seat/commander semantic IDs and retained definition roles. Add regression counterexamples in Task2.
