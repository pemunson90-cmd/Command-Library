# Life history and recovery requirement audit — DONE

Parent: cx-m2-life-functional-01. Candidate branch component/life-history-audit-20261014 from latest recovery. Claim through coordination/CHAT-TASKS.md before writes; lock life-history-audit.lock.

Read the parent task's architecture/contracts/disposition links and retained SPEC G/PX07 history, undo, finish/reopen, results and alert requirements. Compare current core/model and feature/life contracts/reducers, schema6 and codec evidence. Produce one concise implementation/test matrix: exact requirement/source location, current code/evidence, missing service invariant and meaningful future verification. Cover action-time provenance across intervening checkpoints, result retraction/reopen, acknowledged alerts through undo, history name/deck/date filtering and pagination, checkpoint/restart clock recovery. Distinguish declarations/codec/schema tests from operational service/UI evidence. Do not invent completed behavior or silently drop retained rows.

Owned: docs/verification/m2-life/HISTORY-SERVICE-AUDIT-20261014.md (new), this task, your dedicated lock/board row. Read-only access elsewhere. No production/test/schema/build/global coordination edits, no new source research or implementation. This is useful preparatory review and does not approve the eventual service.

Acceptance: every cited path/requirement exists; gaps are concrete and nonduplicative against CONTRACT-DISPOSITIONS.md; git diff --check. No build/emulator needed for documentation-only audit. Publish the matrix and completed task/lock release; root consumes it before service work.

## Latest continuation delta

DONE by claude-code-life-history-audit-20261014 on component/life-history-audit-20261014 (base e489af7). Report: docs/verification/m2-life/HISTORY-SERVICE-AUDIT-20261014.md. Documentation only; nothing built or run. Verified by reading cited paths/lines; `git diff --check` clean.

Material gaps for root: (A1/A2) no SQLite test undoes an action across a later checkpoint or a process restart; (B1) `GameAction.Reopen` has only JVM domain tests, none through SqlLifeGameCommands; (C1/C2) no duplicate/unknown ack or markDelivered-vs-command race test, markDelivered has no caller; (D1) no rule for calendar day to UTC bounds; (E1) force-stop is only host rollback, no process-kill test. PersistedGameService is unimplemented and GameService.undo/dispatch omit the clock reading.

Next: root consumes the report and reconciles the board; no service implementation is assigned by this task.
