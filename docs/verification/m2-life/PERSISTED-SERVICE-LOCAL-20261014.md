# Persisted service facade: local verification, 2026-10-14

SqlPersistedGameService now implements every PersistedGameService/GameService method using one real SqlLifeGameSessions and checked SQL event/history readers over the same database. Public ordinary and CUSTOM factories cannot combine mismatched database/session readers. Serialized read samples no clock and performs no recovery/write; conditional undo checks the expected revision inside the actual authoritative transaction. Recovery may commit before a stale action is rejected.

Typed observation preserves exact Failure/Unavailable/Unknown and structured cancellation. Legacy watch reports terminal typed status through LifeGameObservationException; coroutine cancellation closes an idle watch. No placeholder snapshots, fake totals, background cadence, DI binding or named-source installation were added. Public architecture, exact production source, expanded tests and native probe received independent APPROVE from life_contract_review. The reviewer ran no tests; execution evidence below is root-local.

## Executed local checks

API-absence RED: 103s. Initial five host tests GREEN:171s; expanded seven GREEN:162s. Final check :app:assembleDebug :data:storage:assembleDebugAndroidTest passed in608s,372 tasks (21 executed,351 up-to-date). Actual final XML:950 JVM tests in95 files,0 failures/errors/skips; model73,life212,storage198. Both APKs independently passed zipalign -c -P16 (actual tool option -P 16).

Seven public-port tests use real host SQLite for create/read/dispatch/stale and unconditional undo, event pages/history, file reopen, clock-free read versus resume, all non-success identities, cancellation, conflated observation and idle-watch cleanup, serialized concurrent conditional undo, and actual CUSTOM shuffle/undo/configuration/reopen behavior. Host SQLite is not Room/device/process-kill proof.

App APK:19706004 bytes, SHA256 abb96caa1c90966e25e7e36aa1ab38014b89ab20bd351a68af9885b54eda6ff0. Storage test APK:10273143 bytes, SHA256 2a940792c5431f2b0d6e65f86c51718b3c2bfb0c558c171d9cb939cd658b2b8d. Actual test DEX contains SqlPersistedGameService and PersistedLifeServiceIntegrationTest. The app remains unbound to life, hence unchanged app hash; this is internal verification, not APK delivery.

## Native boundary and remaining obligations

PersistedLifeServiceIntegrationTest.actualRoomCheckpointRecoveryAndConditionalUndoSurviveFileReopen compiles against real Room StorageDatabase and the public facade: start/switch/checkpoint/file close/reopen/new explicit process ID/backward wall clock/recovery/conditional undo/event/history/observe. It is authored and compiled, NOT RUN at this checkpoint. It simulates process identity and file reopen, not an OS process kill. CalendarHistoryCompatibilityTest is likewise compiled; API23 runtime is NOT RUN. One focused API36 run is justified to exercise real Room and packaged date desugaring; record exact published ref/head/run and actual method XML before claiming runtime success.

No full operational-service/UI/M2 acceptance: Android elapsed-realtime/process providers, DI, cadence, source-backed named variants, manifest persistence/import, presentation/delivery concurrency, statistics, physical accessibility/performance and process-kill acceptance remain incomplete. Design: docs/superpowers/specs/2026-10-14-life-persisted-service-facade-design.md. Frozen integration5a88846 excludes this implementation.

Native verification attempt: pushed source1dc43908b81a3869fc064ea29ad3f5375f69640a to agent/cx-native-life-service-source-20261014 (Android tree identical to checkpointd3beaae). Only continuity38571765761 observed success; no focused Android run observed. Earlier checkpoint-only ref agent/cx-native-life-service-20261014 likewise only continuity38571695707. Manual gh workflow run failed at GET workflow endpoint with Forbidden; no dispatch/queued/runtime claim. Existing GitHub App GET remains usable. No CI edits, duplicate emulator runs or product changes. Next diagnose workflow availability/trigger using read-only Actions API, then execute exactly one focused run when callable; native and API23 remain not_run.
