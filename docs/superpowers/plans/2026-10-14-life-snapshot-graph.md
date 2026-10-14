# Structural snapshot graph implementation plan

> Execute inline with superpowers:executing-plans; root retains shared ownership and existing independent reviewer performs design/source review.

Goal: reject contradictory current snapshot graphs before persistence/service use without inventing legacy history or timing authority.

Spec: ../specs/2026-10-14-life-snapshot-graph-design.md. Kotlin/JVM, existing model types; no new dependency/schema/DI/UI. Work on isolated recovery branch. Local checks first.

## Task 1: Pure graph validator

Files: feature/life domain/SnapshotGraphValidator.kt, SnapshotVariantValidator.kt; modify CounterReducer.kt with internal validateState(snapshot, roles): Outcome<Unit>; tests domain/SnapshotGraphValidatorTest.kt and SnapshotVariantValidatorTest.kt.

- [x] Independent spec review, incorporate concrete findings before implementation.
- [ ] Add positive/adversarial tests calling SnapshotGraphValidator.validate(GameSnapshot): Outcome<CounterRoles>. Observe missing API RED via :feature:life:test. Add declaration returning success to observe behavior RED before implementation.
- [ ] Implement role extraction and CounterBoard reuse, seat references, structural clock/elapsed checks, designation/alert/result checks. Add separate internal validateSnapshotVariants(GameSnapshot): Outcome<Unit> for exact variant graph references/keys; no source authority or automation claim.
- [ ] Cover legacy unknown lineage/result, eliminated retained counters, own-slot commander damage, independent owners, finite overtime and backwards UTC; mode/bank/anchor errors; duplicate and dangling identities; result revision/retraction and exact variant coverage/reference/capacity failures. Test future model UNSUPPORTED without recoding it as corruption.
- [ ] Run :feature:life:test, inspect XML zero failures/errors/skips. Independent exact source review; fix concrete findings with regression tests.

## Task 2: Checked store integration

Files: persistence/SqlLifeGameStore.kt and LifeGameInitialState.kt; data/storage test/.../life/SqlLifeGameStoreTest.kt.

- [ ] Add real SQLite adversarial checkpoint tests (invalid role, dangling counter, malformed clock/result/variant) with actual encoded payloads; prove caller-invalid initialization leaves no rows and current corrupt graph blocks read/idempotent retry.
- [ ] Observe behavioral RED in :data:storage:testDebugUnitTest. Invoke validator on new initialized snapshot and decoded current checkpoint. Preserve UNSUPPORTED for reads, map rejected new initializer to INVALID_INPUT. Remove duplicated version guard only if equivalent. Preserve all existing metadata limits/lazy retry/transaction behavior.
- [ ] GREEN focused life/storage regression with existing actual bundled SQLite; independent integration review.
- [ ] Run local check :app:assembleDebug, count actual XML, APK ZIP alignment. Record evidence and task delta, checkpoint/continuity/Python checks and publish. Then claim next prerequisite, do not stop at component completion.

## Review focus

Legacy OTHER-only metadata remains codec-readable but cannot be silently rebound; missing roles must fail guard. Paused retained anchors are valid; unknown lineage does not authorize charging. Negative ALERT overtime remains finite and representable. Current alerts are not entire historical delivery ledger. Mutable definition and wall-clock changes do not rewrite historical loss evidence or impose UTC chronology. Each is covered in Task1 positive/adversarial cases, not deferred to physical testing.
