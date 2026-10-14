# Checked immutable life event/archive reads — DONE

Completed task. The brief and completion-era next steps below are historical; use [CHAT-TASKS.md](../CHAT-TASKS.md) for current claims.

Owner codex-thread-recovery-20261013, agent/cx-thread-recovery-20261013. Parent cx-m2-life-functional-01, root life-functional lock retained. Other chat may claim disjoint read-only history audit, not shared production files.

Next prerequisite before event/CAS mutations and GameService binding: design/review actual bounded SQLite event/archive retrieval, projection/payload consistency and deterministic keyset pages. Use existing Database/LifePayloadCodecPort/schema6; do not add dependencies/schema or register partial GameService. Keep current structural graph guard and creation/core/seat checks intact.

Owned: feature/life persistence helpers/tests, root-owned dedicated data/storage actual SQLite integration tests and shared coordination. Inspect immutable GameEventArchive/GameActionProvenance and actual life_event columns/unique indexes; retain full provenance and recorded random metadata. Validate metadata types/bounds/strict UTF8 before bodies, exact event/game/revision/group/undo/time projections, supported archive model version, independent nested snapshot bounds and selected-page byte budget without silent history truncation. Use composite source/raw identity; cursor must bind game/order and remain canonical. Separate integrity checks from authenticated semantic undo/replay authority and external source authenticity. No assumption event ID equals revision/checkpoint sequence unless explicitly established by a reviewed service policy. Do not force all historical snapshots through current role inference.

Before implementation write concrete spec/inline plan and independent architecture/durability review. Resolve known current/settled pointer checks and legacy Unknown/Absent semantics separately; read APIs cannot certify whole history by examining one page. Meaningful API/behavior RED→GREEN, actual SQLite reopen/projection/oversized/cancel/cursor cases, exact review, coherent local checks and published checkpoint required. Later transactional writes/CAS, result/alert ledgers, live clock recovery, service/UI still incomplete.

## Latest continuation delta

DONE: SqlLifeEventReader archive/events, strict event metadata and canonical length-framed game-bound cursor, deterministic revision keyset pages<=32MiB encoded selected archives. Independent design/source APPROVE; no blocking/deferred findings. APIabsence67s, declaration77/10fail77s; GREEN80storage76s, expanded83storage incl16reader79s. Final local wholecheck/debug88s71XML714JVM0failure/error/skip, alignment pass. See docs/verification/m2-life/EVENT-READS-LOCAL-20261014.md; legacy independent timestamps/roles/Unknown/random metadata preserved. No schema/coreport/dependency/DI change or currentpointer/semantic undo authority claim.

Root next TAKEN cx-m2-life-game-mutations-01: reviewed transactional revision+sequence CAS and atomic event/checkpoint/seat/result/alert persistence. History audit OPEN; root retains shared/life lock. Exact next action concrete mutation design from actual schema/contracts/read helpers before implementation.
