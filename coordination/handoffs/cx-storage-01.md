# Handoff: cx-storage-01

Checkpoint: cx-storage-01 step 2/4 draft, incomplete; root assigns global checkpoint.
Owner: Codex implementer, Sol tier. Task: coordination/tasks/cx-storage-01.md.
Branch: agent/cx-storage-01. Base: preserved schema 09ea4ccf9e09d8532c2b236828036aa0d71e2376, ordinary main merges through 3d80fb7. Accepted core contracts unchanged.
Owned paths: android-app/data/storage/** and this handoff. Shared paths changed: none.

## Saved implementation

Real StorageDatabase over released Room3 useReaderConnection/useWriterConnection and IMMEDIATE transactions. Core RollbackOnlyLatch prevents ignored failures and caught validation abort from committing. Returned callback non-success propagates first status. Structured cancellation is checked before commit and rethrown; Room completes commit/rollback in NonCancellable. A cancellation after durable commit cannot undo committed state. No future provider stubs are registered.

Factory is public suspend StorageDatabase.open(context, fileName): Outcome<StorageDatabase>; returned class implements Database and Closeable. Public methods are query, transaction, capabilities and close. SQLite statements stay within usePrepared; transaction clears its connection when callback finishes and checks its owner coroutine on query/execute. Close is idempotent; callers finish database operations before close.

Final named-parameter rule: :identifier only, ASCII letters/underscore first then ASCII letters/digits/underscore. Distinct repeated names share the SQLite index in first-appearance order. Supplied order does not matter. Literals, quoted identifiers and comments do not contribute parameters. Missing, unused, duplicate supplied parameters, positional/other sigils, multiple statements and unterminated SQL tokens return INVALID_INPUT. Transaction/connection-control SQL is adapter-owned and rejected. Row reads step at most request.maxRows, up to101.

Capabilities probe the real bundled engine/schema/foreign_keys, read MAX_VARIABLE_NUMBER and verify that parameter index is accepted, and perform bound FTS5 MATCH in an IMMEDIATE transaction rolled back with the result. The read-only schema gate now rejects unreadable files before writable Room open, preserving their bytes.

## Evidence

- git diff --check: passed on draft before checkpoint.
- Local Gradle/JVM/Android checks: not_run; selected executor only has Java21 runtime, no JDK17/compiler or Android SDK. Prior schema checks remain the original handoff's evidence, not this draft's tests.
- Pure parser/row-bound/latch tests added, not_run.

## Next

Add Android instrumentation for FTS punctuation and injection, typed/ignored/abort/cancellation rollback, row bound, committed reopen, newer/corrupt schema preservation and capabilities. Push owned branch to trigger hosted android-k2 build and API23/API36/16KiB instrumentation, then address actual diagnostics. Root performs independent durability review before merge.

## Limitations and fallback

Incomplete and unverified adapter checkpoint. No build/runtime acceptance claim. Preserve this branch and original agent/cc-storage-01; never reset/rebase or delete saved schema. Resume from pushed branch with ordinary fetch/checkout when no local edits would be lost. No shared changes requested.
