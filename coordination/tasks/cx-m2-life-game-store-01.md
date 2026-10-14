# Checked game creation/read persistence — DONE

Owner: codex-thread-recovery-20261013; branch agent/cx-thread-recovery-20261013. Parent cx-m2-life-functional-01. Root life-functional lock continues; no component writer owns these files.

Next bounded unit: design/review and implement internal SqlLifeGameStore creation/read foundation over Database and LifePayloadCodecPort. Store exact original creation input/version/hash for operation retry identity and authoritative GameCheckpoint, project current game/seat metadata, and validate stored payload/projection agreement on read. Use original-version bytes or decoded GameCreation equality across codec versions; header changes cannot invalidate a legitimate retry. No reflection, history truncation, fake GameService or DI/UI registration.

Before code, settle explicit initial-snapshot graph invariants and schema6 creation/checkpoint bounds, ID/timestamp/projection checks, idempotent same-operation retry versus conflicting input, and initial revision/sequence behavior in a concrete spec/plan with independent architecture/durability review. Preserve codec string/byte limits and actual source-qualified identities. Use bounded metadata-first reads and strict UTF8 bytes learned from profile tests. Transactions must return typed failures, exact row counts and cancellation rollback; no overwrites on duplicate operation or ID.

Owned: feature/life persistence/domain helpers and tests, dedicated actual SQLite/codec integration tests under data/storage/life, root-owned shared contracts only if reviewed necessity established. No schema/config/DI change assumed. Root handles any needed shared changes explicitly.

Acceptance: meaningful RED→GREEN with actual SQLite reopen, duplicate operation/input conflict, corruption/projection disagreement, cancellation rollback and unchanged existing games on failure; local module/whole checks as justified, independent risk review. This foundation is not full service dispatch/undo/history/watch or clock recovery/UI acceptance.

## Latest continuation delta

DONE bounded foundation: SqlLifeGameStore/StoredLifeGame and focused SQL/row/initial-state helpers implement actual lazy idempotent create/read, exact original input/hash storage, current core/seat projection checks, bounded strict UTF8/nullable metadata and atomic rollback. Exact written design/source independently APPROVED. APIabsence52s/behavior9fail55s then9GREEN73s; expanded64storage incl15Store cases125s; final whole68XML682JVM/check/debug0failure/error/skip76s and16KiB alignment passed. Evidence docs/verification/m2-life/GAME-STORE-LOCAL-20261014.md.

No event/result/alert/full domain/undo/source authority or operational service/UI acceptance. Next root TAKEN cx-m2-life-snapshot-graph-01 before event/checkpoint mutation adapters. History audit remains OPEN for other chat. Existing root life-functional lock continues; this bounded unit is complete and no separate writer remains.
