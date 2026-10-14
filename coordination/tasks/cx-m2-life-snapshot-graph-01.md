# Life snapshot structural graph validation — DONE

Completed task. The brief and completion-era next steps below are historical; use [CHAT-TASKS.md](../CHAT-TASKS.md) for current claims.

Owner codex-thread-recovery-20261013, agent/cx-thread-recovery-20261013. Parent cx-m2-life-functional-01. Root life-functional lock continues. No other component writer may edit feature/life/shared model or tests under this claim.

Next prerequisite before event/checkpoint mutations and service binding: review retained G01–G13/dispositions and existing CounterBoard/SeatClock validation, design and implement reusable pure-JVM structural validation for decoded current snapshots. Check explicit role bindings, seat/commander/counter references and duplicate keys, clock modes/banks/anchors, elapsed/turn state, designation/alert targets, result identities/outcomes and completed-state timing. Do not infer roles from labels, invent legacy metadata, refund unknown elapsed time or mistake structural validity for external source/undo/event authority.

Before code, distinguish safe structural reading from requirements for live timing/recovery and authenticated service lineage. Preserve meaningful legacy Knowledge.Unknown/Absent without certifying incomplete state. Derive exact invariants from current contracts/domain/retained requirements; do not invent blanket UTC ordering across wall-clock changes or require retained historical evidence to reference a current mutable definition. Reuse accepted domain validation where correct, avoiding duplicate contradictory policies. Contract changes require independent root-owned review.

Owned: feature/life domain/persistence pure helpers and tests; root owns necessary changes to checked store integration and dedicated data/storage integration tests. No schema/dependency/DI/UI change assumed. Other chat may claim the read-only history audit; its output file is separate and must not be overwritten here.

Acceptance: meaningful RED→GREEN pure-JVM positive/adversarial graph tests, independent architecture/durability review, actual existing SQLite regression when integrating guards, appropriate local coherent checks. No false full-service/source/Room/concurrency/physical claim. Subsequent event/CAS/alert/result adapters and operational service/UI still required.

## Latest continuation delta

DONE: internal pure graph/variant guards and CounterBoard reuse integrated into checked store new/current read. Design/pure source/integration reviews APPROVE; no remaining blockers. APIabsence RED73s, declaration behavior94/11fail66s, actual SQLite67/3fail68s, then94life/67storage GREEN71s. Final local wholecheck/debug80s70XML698JVM0failure/error/skip, alignment pass. See docs/verification/m2-life/SNAPSHOT-GRAPH-LOCAL-20261014.md and spec/plan; pure source b8bbe7d. No schema/publicport/dependency/DI/UI change or operational authority claim.

Next TAKEN cx-m2-life-event-reads-01: checked immutable archive/event projection reads and bounded pages before mutation/CAS. Full result/alert/event/undo lineage and live timing/service/UI remain pending; legacy metadata is preserved, never guessed. History audit OPEN, root life lock retained.
