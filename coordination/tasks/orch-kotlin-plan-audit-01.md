# Task orch-kotlin-plan-audit-01

Status: queued planning audit. No implementation authority.

## Purpose

Independently audit `docs/gauntlet/V1_KOTLIN_EXECUTION_SPEC.md` against the current requirements and continuation files after the Kotlin version-1 / Flutter Windows version-2 / post-Windows sync ruling.

## Scope

Read only: SPEC, COVERAGE, DECISIONS, MILESTONES, STATE, CURRENT, QUEUE, existing architecture contracts and task cards. Write only `docs/gauntlet/gates/K0-KOTLIN-PLAN-AUDIT.md` and this task's handoff if needed. Do not modify product code, build configuration, locks, CI, source fixtures, credentials, signing files, or version pins.

## Required checks

1. Confirm D-07 is superseded for Android version 1 and that no next action remains blocked on rescore.
2. Locate each stale Flutter/Dart or Windows version-1 command, dependency, prerequisite or acceptance claim. Report exact file and replacement needed; do not silently fix it.
3. Confirm Kotlin Android technology names in the execution specification are proposals awaiting K1 evidence, never pins or completed checks.
4. Confirm Flutter is isolated to version 2, with a separate source tree and no shared-code assumption.
5. Confirm sync has no implementation task and needs a future explicit authorization after Windows runs.
6. Confirm source-fixture wording cannot be read as authorization to fetch data.
7. Confirm the model allocation requests Astra high for architecture/security/cross-cutting work, Sol high for implementation, and permits lower-cost mechanical work only under accepted cards.

## Completion rule

Verdict is approve, approve with named corrections, or block. Report evidence and every correction by file. Stop when the report is published; do not begin K1, modify the plan, or claim an application check.

## Recommended reviewer

GPT-6 Astra at low reasoning is sufficient for this consistency audit. Escalate to Astra high only if requirement interpretation, data durability, identity, security, or multi-platform boundary semantics are ambiguous.

