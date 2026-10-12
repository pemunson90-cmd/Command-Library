# Handoff: cx-storage-01

Checkpoint: cx-storage-01 steps 2/3 code, incomplete acceptance; root assigns global checkpoint.
Owner: Codex implementer, Sol tier. Task: coordination/tasks/cx-storage-01.md.
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

## Limitations and fallback

Implemented adapter/instrumentation source, incomplete and unverified acceptance checkpoint. No build/runtime acceptance claim. Preserve this branch and original agent/cc-storage-01; never reset/rebase or delete saved schema. Resume from pushed branch with ordinary fetch/checkout when no local edits would be lost. No shared changes requested.
