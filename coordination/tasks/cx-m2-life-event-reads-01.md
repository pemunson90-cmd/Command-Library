# Checked immutable life event/archive reads — TAKEN

Owner codex-thread-recovery-20261013, agent/cx-thread-recovery-20261013. Parent cx-m2-life-functional-01, root life-functional lock retained. Other chat may claim disjoint read-only history audit, not shared production files.

Next prerequisite before event/CAS mutations and GameService binding: design/review actual bounded SQLite event/archive retrieval, projection/payload consistency and deterministic keyset pages. Use existing Database/LifePayloadCodecPort/schema6; do not add dependencies/schema or register partial GameService. Keep current structural graph guard and creation/core/seat checks intact.

Owned: feature/life persistence helpers/tests, root-owned dedicated data/storage actual SQLite integration tests and shared coordination. Inspect immutable GameEventArchive/GameActionProvenance and actual life_event columns/unique indexes; retain full provenance and recorded random metadata. Validate metadata types/bounds/strict UTF8 before bodies, exact event/game/revision/group/undo/time projections, supported archive model version, independent nested snapshot bounds and selected-page byte budget without silent history truncation. Use composite source/raw identity; cursor must bind game/order and remain canonical. Separate integrity checks from authenticated semantic undo/replay authority and external source authenticity. No assumption event ID equals revision/checkpoint sequence unless explicitly established by a reviewed service policy. Do not force all historical snapshots through current role inference.

Before implementation write concrete spec/inline plan and independent architecture/durability review. Resolve known current/settled pointer checks and legacy Unknown/Absent semantics separately; read APIs cannot certify whole history by examining one page. Meaningful API/behavior RED→GREEN, actual SQLite reopen/projection/oversized/cancel/cursor cases, exact review, coherent local checks and published checkpoint required. Later transactional writes/CAS, result/alert ledgers, live clock recovery, service/UI still incomplete.

## Latest continuation delta

TAKEN after structural current graph guard design/source/integration approved and local wholecheck/debug698JVM/70XML0failure/error/skip80s,67storage including18Store cases; APK alignment pass. New event-read design/code/tests not_run. Exact next action: inspect schema/contract/history requirements, specify bounded read/page API and integrity/legacy boundaries, obtain review before tests/code. Root remains sole life/shared orchestrator.
