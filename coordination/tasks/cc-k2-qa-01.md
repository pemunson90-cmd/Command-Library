# Task cc-k2-qa-01: parallel Claude Code QA

Status: delegated and eligible for independent documentation/test-vector work. Codex remains the sole integration orchestrator for this parallel turn; Claude Code must not claim its lease. This task is authorized by Pat's parallel-work request, not a competing orchestrator takeover.

Branch: agent/cc-k2-qa-01. Exclusive outputs: docs/verification/k2-claude/** only. Base is the verified main commit containing this card/lock; record full base SHA in the worker handoff. Core implementation belongs to agent/k2-core-contracts. No production code or shared-file changes are authorized here.

## Purpose

Build a concrete K2 QA matrix and useful explicitly synthetic test vectors, then independently inspect actual published core declarations when available. This is repository-bound verification design, not standalone provider/API/terms research (Pat is handling that in ordinary chat). Do not repeat K1 source/version discovery.

## Sequence

1. Read README, CONTINUE, current STATE/CURRENT, this card, K2 foundation card, KOTLIN_CONTRACTS, DATA_MODEL, accepted K1 final review and relevant SPEC/COVERAGE. Verify the core lock and main head. Create an isolated branch/worktree from that head; record it. An active Codex lease is expected; only the delegated exclusive folder may be written.
2. Write TEST-MATRIX.md mapping K2's actual finish line to exact observable assertions: source-qualified identity; original Unicode vs normalized lookup; multi-face records and collector numbers as text; known/absent/unknown and exact decimal units; bounded pages; stale revision; cancellation rethrow and rollback after writes; malformed/oversized input; atomic old-generation preservation; close/reopen/process recreation; persisted English settings and restore-without-consent; bound FTS token/punctuation behavior; usable compact/medium/expanded UI; artifact/commit/hash/install provenance. Distinguish JVM, Android instrumentation, emulator and later physical checks. Full-app obligations remain partial, not waived.
3. Add small synthetic-vectors.json for selected pure-domain/error cases with descriptions, inputs and expected semantic outcomes. Label every vector synthetic; never fabricate a supposedly authentic Scryfall fixture/source date. These are test inputs, not fake application data or implementations. Avoid copying giant requirements tables. No external API calls, signing files, paid services or product downloads.
4. When real core source is published on agent/k2-core-contracts, inspect a pinned commit read-only in a separate worktree or git show. Do not read an actively edited file as accepted evidence. Record reviewed SHA, concrete findings by file/line, omitted public methods/types, weakened null/provenance/cancel semantics and test gaps in CONTRACT-REVIEW.md. If that branch is not yet published, mark this step pending and finish the independent matrix/vectors; do not invent a review or wait indefinitely. Astra's mandatory architecture/durability review is separate.
5. Write HANDOFF.md with task/branch/full base SHA, files, actual checks/results (not planned tests), reviewed commit if any, findings and exact next action. Mark READY-FOR-REVIEW in that handoff. Run JSON parsing and git diff --check. Commit only your exclusive folder and push only agent/cc-k2-qa-01. Do not merge, edit main, release the root lease/lock, touch CURRENT/STATE/QUEUE/JOURNAL, modify Gradle/CI or amend another worker's branch. Root reviews and integrates sequentially.

## Stop and conflicts

Stop after this bounded deliverable. If an owned path already has another active writer or the task/lock changed, report in your own handoff and stop mutation. Never reset/clean/rebase/force-push other's work. Suggestions for shared files belong in findings; do not apply them. No recursive agents without root delegation. A successor resumes this task from its own committed HANDOFF, not chat memory.
