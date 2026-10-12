# Handoff: cx-storage-01

Checkpoint: cx-storage-01 steps 2/3 code, incomplete acceptance; root assigns global checkpoint.
Owner: Codex implementer, Sol high tier. Task: coordination/tasks/cx-storage-01.md.
Branch: agent/cx-storage-01. Base: preserved schema 09ea4ccf9e09d8532c2b236828036aa0d71e2376, ordinary main merges through 3d80fb7. Accepted core contracts unchanged.
Owned paths: android-app/data/storage/** and this handoff. Shared paths changed: none.

## Saved implementation

Real StorageDatabase over released Room3 useReaderConnection/useWriterConnection and IMMEDIATE transactions. Core RollbackOnlyLatch prevents ignored failures and caught validation abort from committing. Returned callback non-success propagates first status. Structured cancellation is checked before commit and rethrown; Room completes commit/rollback in NonCancellable. A cancellation after durable commit cannot undo committed state. No future provider stubs are registered.

Factory is public suspend StorageDatabase.open(context, fileName): Outcome<StorageDatabase>; returned class implements Database and Closeable. Public methods are query, transaction, capabilities and close. SQLite statements stay within usePrepared; transaction clears its connection when callback finishes and checks its owner coroutine on query/execute. Close is idempotent; callers finish database operations before close.

Final named-parameter rule: :identifier only, ASCII letters/underscore first then ASCII letters/digits/underscore. Distinct repeated names share the SQLite index in first-appearance order. Supplied order does not matter. Literals, quoted identifiers and comments do not contribute parameters. Missing, unused, duplicate supplied parameters, positional/other sigils, multiple statements and unterminated SQL tokens return INVALID_INPUT. Transaction/connection-control SQL is adapter-owned and rejected. Row reads step at most request.maxRows, up to101.

Capabilities probe the real bundled engine/schema/foreign_keys, read MAX_VARIABLE_NUMBER and verify that parameter index is accepted, and perform bound FTS5 MATCH in an IMMEDIATE transaction rolled back with the result. The read-only schema gate now rejects unreadable files before writable Room open, preserving their bytes.

## Evidence

- Adapter draft published at f291896; instrumentation at b556c28 and actual repeated-name SQLite binding test at f96f3332bdcd3f19eef7a9006c3b5c53ac05f186. Remote branch read-back matched f96f333.
- git diff --check: passed on draft and instrumentation before checkpoint.
- Placeholder scan: no matches in storage module.
- Local Gradle/JVM/Android checks: not_run; selected executor only has Java21 runtime, no JDK17/compiler or Android SDK. Prior schema checks remain the original handoff's evidence, not this draft's tests.
- Pure parser/row-bound/latch tests added, not_run.
- Android StorageDatabaseTest now has15 tests covering actual bound FTS punctuation/quotes/keywords, injection as data, repeated-name actual SQLite binding order, ignored query failure, missing parameter/manual COMMIT rejection, abort/caught abort, returned three typed statuses/first-status precedence, thrown exception/FK enforcement, caller-token and structured cancellation,101 row bound and copied values, committed reopen/late token, escaped scope/closed guard, newer schema and corrupt bytes, repeated real capabilities. All not_run locally.
- Root observed hosted run38198689827 for b556c28 running at handoff time. Result unknown; f96f333 latest source acceptance not_run. Root owns collecting hosted diagnostics and final independent review.

## Next

Run hosted android-k2 build and API23/API36/16KiB instrumentation on this branch, then address actual diagnostics. Root performs independent durability review before merge. Gradle commands: bash gradlew --no-daemon --max-workers=2 :data:storage:testDebugUnitTest :data:storage:assembleDebug :data:storage:assembleDebugAndroidTest, then :data:storage:connectedDebugAndroidTest on the workflow emulator matrix. Check tested SHA and actual XML counts before acceptance.

## Targeted review corrections S1 through S4

Root's independent K2-STORAGE-REVIEW required four corrections. Implemented one bounded batch; corrected-source acceptance remains not_run.

- S1: reject every non-ASCII character immediately continuing an ASCII parameter name, including emoji, combining mark and Unicode space. Actual bundled regression first proves native SQLite distinguishes both names, then checks the adapter rejects rather than silently returning NULL. Released SQLiteStatement2.7.1 sources inspected for the precise requested APIs: it exposes neither native parameter count/name/index nor statement readonly. No unsupported API was invented.
- S2: after the first owner-operation non-success, all later owner query/execute returns the same first status without preparing or stepping SQL. Preserve that status if an engine-aborted transaction makes Room's final rollback fail. Packaged regression performs write, INSERT OR ROLLBACK uniqueness failure, ignored failure and later write/query, then reopen and assert zero rows.
- S3: before prepare, queries enforce SELECT or WITH whose main statement is SELECT. The CTE prefix parser handles optional RECURSIVE, column lists, multiple CTEs, and AS [NOT] MATERIALIZED parenthesized bodies. SQLite itself validates the read body grammar. INSERT/UPDATE/DELETE RETURNING and WITH INSERT RETURNING regressions reject and roll back. This deliberately scoped supported-query policy does not expose PRAGMA, EXPLAIN or VALUES as query roots.
- S4: callback ThreadContextElement records the executing Job for synchronous abort and suspend operations. All foreign Job/thread and post-callback query/execute is rejected without latching; abort throws IllegalStateException before latching. Owner observation, abort, lifetime revoke and final commit-permission decision share one monitor; revocation/decision is atomic. Callbacks must use their own Job for operations. Cross-coroutine/thread abort/query/execute and post-callback abort regressions prove the owner remains Active and commits only its write.

Corrected suite:13 JVM methods (original schema5 plus parser/row6 plus latch2),19 instrumentation methods. All corrected checks are not_run locally. Root reported original f96f333 JVM/build/lint passed at38198744105; all original emulator jobs failed before ADB with Unknown AVD[k2], so no instrumentation executed. Root owns the AVD workflow correction and exact corrected-source hosted acceptance plus narrow independent re-review.

## Limitations and fallback

Implemented adapter/instrumentation source, incomplete and unverified acceptance checkpoint. No build/runtime acceptance claim. Preserve this branch and original agent/cc-storage-01; never reset/rebase or delete saved schema. Resume from pushed branch with ordinary fetch/checkout when no local edits would be lost. No shared changes requested.
