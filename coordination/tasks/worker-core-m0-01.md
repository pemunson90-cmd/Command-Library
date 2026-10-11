# Task worker-core-m0-01

Status: planned, not eligible until listed prerequisites accepted. Role: implementer, component core. One assigned lock-holder; branch agent/worker-core-m0-01; base SHA verified and recorded at claim. Architect: docs/architecture/core/ARCHITECT.md.

## Exact next step

1/3: implement source IDs/revision/Outcome/unknown/decimal/cancel/clock immutable contracts. First read architecture/interface exports and confirmed source/lock evidence, then write packages/core/lib/core.dart, packages/core/lib/src/value_types.dart, packages/core/test/value_types_test.dart. If setup/type contracts absent, block and return exact shared-file request; do not invent compiled dependencies.

## Dependencies

accepted architecture audit; accepted orch-toolchain-01; root workspace/dependency lock merged

## Ownership

Only packages/core/** plus assigned task handoff coordination/handoffs/worker-core-m0-01.md. Root orchestrator updates state/CI/sharedregistry. No other component or app/CI file edits. Use isolated worktree/branch; documentation snapshot alone is not safe implementation checkout.

## Acceptance and test

q-o allocation arithmetic respects exact decimal/native unit; unknown notzero; cancellation/UTC-vsmonotonic/revision fixtures pass. No Flutter/storage/network imports.
Planned command/harness: cd packages/core then dart test; hosted API smoke. Actual invocation/result/runner URL and revisions go into handoff; a missing harness is not pass. Reviewer independent of author; path/placeholder/secret checks before merge.

## Checkpoint and next

Split into10-20minute substep; saveearly then checkpoint task/handoff after each real improvement. Record exact nextfile/test/command, partial/unrun evidence, base/depsSHA. Request shared edits rather than modifying app/CI/schema registry. Parent stage never skips prerequisites to makeprogress.
