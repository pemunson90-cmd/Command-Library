# Modern life timing engine — local evidence, 2026-10-14

Owner codex-thread-recovery-20261013, branch agent/cx-thread-recovery-20261013. Task cx-m2-life-timing-engine-01. Concrete design/inline plan under docs/superpowers. Independent architecture review approved design and lineage, bounded identity, explicit refill-target and typed undo-patch refinements; narrow identity/settlement/control source reviews approved; undo source review found no blocking algorithm defect and requested concrete regressions, all now passed. Pure helper acceptance only.

Implemented internal LifeTimingEngine with same-observation settlement, independent pause controls, SHARED single-bank zero policies, elapsed game/turn warnings, SwitchTurn/fresh lineage, PassClock without turn/refill/reset, joint recovery, immutable action-time undo and typed composition. Countdown seat math reuses accepted reducer; its existing three/four-argument undo callers retain behavior. Explicit modern refill target correctly handles flat single-switch groups followed by priority passing. Typed TimingPatch cannot become a TimingReduction until composed with a semantic base and validated graph; undoFinish requires semantic status/result restoration first. Settled current is returned separately for immutable undo-event audit. Game elapsed is never refunded; restored turn elapsed belongs to original lineage at original action time.

Ordinary legacy zero IDs remain exact. Oversized IDs use length-framed domain-separated SHA256 in a separate namespace, with UTF8 65536-byte limit checked including occurrence suffix. Exact canonical occurrence matching fixes delimiter-prefix ambiguity; set membership avoids repeated linear fired-list lookup. Creation persistence now accepts Known opening lineage or genuine historical Absent, still rejects Unknown. Actual initializer must allocate fresh Known; accepting its storage shape alone does not initialize a playable game. Caller owns globally fresh lineage allocation and must pass original archive.event.action with its provenance.

## Actual local commands/results

JAVA_HOME=/workspace/.command-library-env/jdk-17.0.16+8; ANDROID_HOME=/workspace/.command-library-env/android-sdk; GRADLE_USER_HOME=/workspace/.command-library-env/gradle. Accepted dependency/schema tuple unchanged.

- First boundary RED: feature/life98 tests,2 expected failures (oversized zero ID, ambiguous undo-prefix suppression),193s. Storage did not run after failing life task.
- Mixed --continue run:99 life GREEN;111 storage with1 expected Known creation-lineage failure,238s. Long UTF8 seat checkpoint/refill-undo covered.
- Creation fix plus declared settlement API RED:111 storage GREEN;103 life4 expected failures,150s.
- Implemented settlement:103 life GREEN,110s. Declared controls plus three settlement edge cases:110 life4 expected controls failures,130s; implemented controls110 GREEN,75s.
- Declared undo:115 life5 expected failures,105s. Implemented undo115 GREEN,85s.
- Expanded final focused life/storage:122 life +111 storage,0failures/errors/skips,131s. Includes group SwitchB/PassC and same-seat SwitchA/PassC charge accounting, undoFinish semantic composition, paused Absent restoration, SHARED RESTORE zero under all policies, acknowledged zero replay suppression, exact UTF8 suffix boundary, huge warning thresholds, arithmetic overflow and second-ledger failure without partial returned state.
- From android-app: `bash gradlew --no-daemon --console=plain check :app:assembleDebug`: BUILD SUCCESSFUL104s,336tasks9executed. Actual whole XML readback73 reports /770 JVM tests /0failures /0errors /0skips; includes122 life,111 storage (19Store,27mutator,16reader,13profile among storage suites). No new schema or platform dependency.
- Android build-tools36.0.0 `zipalign -c -P 16 4` debug APK: exit0. SHA2569b668e3fb4ba720276e3a3e4cfb7cbf53cebbb7ca4a1ac9cfa2a044ddd702d4c; unchanged unbound app artifact.
- Continuity checks PASS; Python coordination31tests OK. Missing synthetic visual captures logged by expected negative fixtures, not a new device capture run.

## Limits and next work

No GameService/DI/UI binding, actual creation initializer, semantic action selection or complete current-spine authority supplied by these pure helpers. Persisted service must use full checked transactional current state, explicit archive action/provenance, authoritative unconditional undo, CAS recovery marker/sequence, after-commit per-game ordered publication, result/retraction and operational delivery rules. Flat groups permit at most one SwitchTurn; historical ambiguous nested/multiple-turn groups return typed unsupported rather than invented accounting. Missing lineage never authorizes live elapsed charge/resume. Original provenance remains immutable.

No new native/emulator/hosted run, foreground cadence/force-stop loss measurement, OS notification delivery, Room concurrency, physical Fold7/TalkBack/performance or finished-v1/canonical signed-package claim. Existing schema6 native evidence remains historical and unchanged. Next bounded root task: modern non-timing action and initializer design before persisted service/UI.
