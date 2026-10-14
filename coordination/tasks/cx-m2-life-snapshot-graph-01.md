# Life snapshot structural graph validation — TAKEN

Owner codex-thread-recovery-20261013, agent/cx-thread-recovery-20261013. Parent cx-m2-life-functional-01. Root life-functional lock continues. No other component writer may edit feature/life/shared model or tests under this claim.

Next prerequisite before event/checkpoint mutations and service binding: review retained G01–G13/dispositions and existing CounterBoard/SeatClock validation, design and implement reusable pure-JVM structural validation for decoded current snapshots. Check explicit role bindings, seat/commander/counter references and duplicate keys, clock modes/banks/anchors, elapsed/turn state, designation/alert targets, result identities/outcomes and completed-state timing. Do not infer roles from labels, invent legacy metadata, refund unknown elapsed time or mistake structural validity for external source/undo/event authority.

Before code, distinguish safe structural reading from requirements for live timing/recovery and authenticated service lineage. Preserve meaningful legacy Knowledge.Unknown/Absent without certifying incomplete state. Derive exact invariants from current contracts/domain/retained requirements; do not invent blanket UTC ordering across wall-clock changes or require retained historical evidence to reference a current mutable definition. Reuse accepted domain validation where correct, avoiding duplicate contradictory policies. Contract changes require independent root-owned review.

Owned: feature/life domain/persistence pure helpers and tests; root owns necessary changes to checked store integration and dedicated data/storage integration tests. No schema/dependency/DI/UI change assumed. Other chat may claim the read-only history audit; its output file is separate and must not be overwritten here.

Acceptance: meaningful RED→GREEN pure-JVM positive/adversarial graph tests, independent architecture/durability review, actual existing SQLite regression when integrating guards, appropriate local coherent checks. No false full-service/source/Room/concurrency/physical claim. Subsequent event/CAS/alert/result adapters and operational service/UI still required.

## Latest continuation delta

Design and inline plan drafted in docs/superpowers/specs/2026-10-14-life-snapshot-graph-design.md and docs/superpowers/plans/2026-10-14-life-snapshot-graph.md. Existing read-only life_contract_review requested to review structural vs operational boundaries before code. Product code/tests unchanged; new tests not_run. Exact next action: resolve independent design findings, then API/behavior RED and pure guard implementation. Other chat's disjoint audit remains OPEN; root life lock unchanged.
