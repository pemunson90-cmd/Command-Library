# Task cx-storage-01: resume saved storage adapter

Status: delegated; resume step2 of4 from Claude's committed schema at09ea4ccf9e09d8532c2b236828036aa0d71e2376. Do not repeat schema design/version discovery or discard prior work. Original branch agent/cc-storage-01 is preserved.

Branch: agent/cx-storage-01; isolated worktree /workspace/Command-Library-cx-storage. Source base: original schema commit plus ordinary merge of current main. Exclusive writes: android-app/data/storage/** and coordination/handoffs/cx-storage-01.md. Root owns all shared Gradle/CI/registry/state files. Core source remains unchanged and accepted.

Read original cc-storage-01 task/handoff, accepted K2-FOUNDATION-DESIGN and actual core Database/DbTransaction/SqlValue types. The original safe-fallback checkout -B command is unsafe for uncommitted work; never use it. Preserve branch/worktree state and commit recoverable drafts.

Implement a real Room3/bundled SQLite Database adapter and its current named-parameter contract, bounded101-row query, irreversible rollback-only latch, validation abort, callback non-success rollback, caller cancellation and actual capabilities. Initial schema exists; no placeholder/no-op Database or DAO. Keep post-durable-commit truth explicit. Opening newer/corrupt schema must preserve bytes/data. SQLite statement, pooled connection and transaction scopes cannot escape their lifetime.

Reuse original steps2/3/4: meaningful pure parser/latch/row-bound tests plus Android instrumentation for bound FTS5 punctuation/quotes, write-then-fail/abort/caught abort/returned failure/cancellation rollback, committed close/reopen, newer schema and real capabilities. No test may report planned execution as passed. Root runs hosted android-k2 workflow when source exists; local executor currently lacks Android tools. Compile and emulator acceptance are required before merging, not before drafting real adapter code.

Save early; commit only exclusive paths and push agent/cx-storage-01. Write handoff with actual tested SHA/commands/results or not_run, final named-parameter rule, API surface and exact next action. Root independently reviews before integration. No source/API terms research or recursive agents. Use weakest capable standard implementation model; request a scoped stronger review if unexpected durability/API semantics arise.
