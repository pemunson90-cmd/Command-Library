# Life profiles — DONE

Owner: codex-thread-recovery-20261013 (this chat).
Branch: agent/cx-thread-recovery-20261013. Parent: cx-m2-life-functional-01.

Implement core/model LifePayloadCodecPort, concrete storage codec conformance and feature/life SqlPlayerProfileRepository using Database/Clock/IdSource. Preserve module production DAG; storage-to-life dependency is tests only. Use strict checked projections, revision CAS, deterministic bounded metadata-first keyset pages, typed failures/cancellation and profile deletion that preserves frozen game history. No GameService/DI/UI registration in this unit.

Owned: android-app/feature/life production/tests; core/model codec port; data/storage life codec and dedicated profile integration tests; orchestrator-owned build configuration needed for these tests. Existing root life-functional lock remains active. Other workers must not edit these paths or shared configuration.

Acceptance: meaningful RED→GREEN; real local bundledSQLite and codec CRUD/reopen/CAS/corruption/pagination/cancellation/history-preservation tests; independent required contract/durability review; appropriate local whole-app checks. No hosted emulator per feature.

## Latest continuation delta

DONE: actual twelve-method codec port and SQLite-backed repository, strict bounded UTF8 projection/payload checks, CAS, canonical keyset cursor and bytebudget pages. Independent design/source review findings fixed; meaningful malformed metadata/UTF8 RED→GREEN. Final local wholecheck/debug667JVM/67XML and storage49cases including13profile tests,0failures/errors/skips; alignment pass. Details docs/verification/m2-life/PROFILES-LOCAL-20261014.md. No operational service/DI/UI binding.

Next root unit TAKEN: cx-m2-life-game-store-01 (checked creation/read foundation). This profile assignment is complete; broad parent life-functional lock remains with root for subsequent life work. Scryfall integration ad3f9aa source/test hashes verified and integrated with the coherent local check.
