# Current Claude Code parallel assignment

Status: parser candidate209a938/code796c665 reviewed; one private cancellation fix requested before merge. Your rules paths remain exclusively reserved.

## Immediate continuation

Read docs/verification/k3-rules-parser/REVIEW.md. Fix F1 only on agent/cc-k3-rules-parser-01: token cancellation in an onEntry callback during a single final chunk must throw CancellationException, never emit further entries or return Ok. Check both token and coroutine cancellation before/after callbacks and before successful completion (including finish/end-of-pass paths). Add one-final-chunk synthetic regression. Rerun existing rules tests, update your owned Latest continuation delta and push exact candidate. Root handles shared Gradle inclusion and hosted CI after reviewed merge. Do not touch shell/catalog/app/core/shared paths; those have active separate owners. No genuine corpus/completeness claim or new download.

## Original scope

K2 assignments are finished; do not reopen them.

> Work on https://github.com/pemunson90-cmd/Command-Library on branch agent/cc-k3-rules-parser-01 from verified current main. Read only CONTINUE.md, the Latest continuation delta and ownership section in coordination/tasks/orch-kotlin-k3-offline-data-01.md, coordination/tasks/cc-k3-rules-parser-01.md and coordination/locks/rules.lock, plus their relevant contracts. You exclusively own android-app/feature/rules/** and coordination/handoffs/cc-k3-rules-parser-01.md. Codex reserves these until reviewed merge and handles catalog scaling, storage/core/contracts/shared config and later UI integration. Implement a private pure streaming strict-UTF8 rules TXT parser and meaningful explicitly synthetic JVM tests against the accepted RuleEntry DTOs; no database/repository fake adapter or application wiring. Preserve original text/examples/notices and unresolved references; use supplied truthful provenance, never guessed official versions/dates. Genuine fixture/source binding remains a later review gate, so do not download/bundle product rules or claim official completeness. Use the weakest capable model available. Commit useful actions with a single replaceable Latest continuation delta in your handoff, push the branch, then report exact commit/shared requests/test evidence or not_run as READY-FOR-REVIEW. Do not claim the orchestrator lease, touch other/shared paths, push main, reset/clean/rebase/force-push or merge your work. No recursive workers.

Verify the live lock/task before beginning. Request root Gradle inclusion/dependencies in your handoff; do not edit them yourself. No application feature is accepted until actual tests and required review pass.
