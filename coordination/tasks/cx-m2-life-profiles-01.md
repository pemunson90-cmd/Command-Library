# Life profiles — TAKEN

Owner: codex-thread-recovery-20261013 (this chat).
Branch: agent/cx-thread-recovery-20261013. Parent: cx-m2-life-functional-01.

Implement core/model LifePayloadCodecPort, concrete storage codec conformance and feature/life SqlPlayerProfileRepository using Database/Clock/IdSource. Preserve module production DAG; storage-to-life dependency is tests only. Use strict checked projections, revision CAS, deterministic bounded metadata-first keyset pages, typed failures/cancellation and profile deletion that preserves frozen game history. No GameService/DI/UI registration in this unit.

Owned: android-app/feature/life production/tests; core/model codec port; data/storage life codec and dedicated profile integration tests; orchestrator-owned build configuration needed for these tests. Existing root life-functional lock remains active. Other workers must not edit these paths or shared configuration.

Acceptance: meaningful RED→GREEN; real local bundledSQLite and codec CRUD/reopen/CAS/corruption/pagination/cancellation/history-preservation tests; independent required contract/durability review; appropriate local whole-app checks. No hosted emulator per feature.

## Latest continuation delta

Task explicitly taken on2026-10-14. Design reviewed in current chat; no implementation edits yet. Next: persist concrete spec/plan, write failing actual integration tests, implement and verify locally. See coordination/CHAT-TASKS.md for other disjoint assignments.
