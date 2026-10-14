# Checked game creation/read persistence — TAKEN

Owner: codex-thread-recovery-20261013; branch agent/cx-thread-recovery-20261013. Parent cx-m2-life-functional-01. Root life-functional lock continues; no component writer owns these files.

Next bounded unit: design/review and implement internal SqlLifeGameStore creation/read foundation over Database and LifePayloadCodecPort. Store exact original creation input/version/hash for operation retry identity and authoritative GameCheckpoint, project current game/seat metadata, and validate stored payload/projection agreement on read. Use original-version bytes or decoded GameCreation equality across codec versions; header changes cannot invalidate a legitimate retry. No reflection, history truncation, fake GameService or DI/UI registration.

Before code, settle explicit initial-snapshot graph invariants and schema6 creation/checkpoint bounds, ID/timestamp/projection checks, idempotent same-operation retry versus conflicting input, and initial revision/sequence behavior in a concrete spec/plan with independent architecture/durability review. Preserve codec string/byte limits and actual source-qualified identities. Use bounded metadata-first reads and strict UTF8 bytes learned from profile tests. Transactions must return typed failures, exact row counts and cancellation rollback; no overwrites on duplicate operation or ID.

Owned: feature/life persistence/domain helpers and tests, dedicated actual SQLite/codec integration tests under data/storage/life, root-owned shared contracts only if reviewed necessity established. No schema/config/DI change assumed. Root handles any needed shared changes explicitly.

Acceptance: meaningful RED→GREEN with actual SQLite reopen, duplicate operation/input conflict, corruption/projection disagreement, cancellation rollback and unchanged existing games on failure; local module/whole checks as justified, independent risk review. This foundation is not full service dispatch/undo/history/watch or clock recovery/UI acceptance.

## Latest continuation delta

TAKEN. Profiles and Scryfall publishedb8b103e; clean repository verified after environment restart. Lazy initializer rationale independently reviewed: lookup before initialization, semantic retry equality, current checkpoint returned without comparing current settings to original input; callback can repeat only after uncommitted failures. Concrete spec/plan written2026-10-14-life-game-store; exact written spec/plan independently approved; absence/complete variant invariants clarified. Public concrete visibility enables actual storage integration tests, no new core port/DI. Implementation/tests not_run. Next: actual SQLite RED tests and code; keep result/alert/event/full graph/service obligations explicit.
