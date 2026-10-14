# Modern timing implementation plan

Owner codex-thread-recovery-20261013; inline execution under continuing user authorization. Task cx-m2-life-timing-engine-01 TAKEN; central coordination remains authoritative.

1. Review concrete timing design independently before code; resolve actual contract gaps.
2. RED/GREEN bounded legacy zero IDs and exact replay matcher; preserve existing normal IDs and clock regression. Accept Known creation lineage with actual storage regression.
3. RED/GREEN modern settlement facade: graph/eligibility guards, same-observation elapsed and SHARED settlement, durable warning occurrence generation.
4. RED/GREEN pause, turn switch/fresh lineage and independent pass operations. No new user rearm action.
5. RED/GREEN joint recovery and immutable four-argument undo composition; carry operational history, gameElapsed never refunded.
6. Independent source review, resolve findings with tests; coherent local check/debug/alignment, checkpoint and task/status synchronization. Keep later service obligations explicit.

## Execution record

Architecture review approved design with clarifications recorded there. Initial boundary RED: 98 feature/life tests, two expected failures (bounded alert ID and canonical replay matching), 193s; storage task not executed after life failed. Root then implemented bounded formatter/exact matcher; mixed --continue run checks existing seat-clock regression alongside the still-unfixed creation Known lineage case. Full modern timing engine is not implemented by this boundary patch.

All six bounded steps completed locally; source reviews and requested regressions resolved/passed. Final770JVM/73XML0failure/error/skip, check/debug104s, alignmentpass. Evidence docs/verification/m2-life/TIMING-ENGINE-LOCAL-20261014.md. No service/device acceptance. Central CHAT-TASKS controls the current next assignment.
