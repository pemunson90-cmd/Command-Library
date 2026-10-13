# cx-k3-settings-removal-outbox-01 — durable explicit consent removal request

Owner: bounded Sol medium worker. Base c4de743, isolated branch/worktree. Own only exact settings source/test paths and handoff in settings-removal-outbox.lock; no shared coordination/config/core/DI changes, no extra agents.

Read CONTINUE.md and docs/verification/k3-downloads/REMOVAL-OUTBOX-DESIGN.md plus approved review. Implement that exact schema2/pendingDataRemoval design, legacy schema1 preservation and conditional-revision acknowledgement. Do not change cache defaults/languages/consent semantics. Retain the existing settled DataStore mutation/cancellation acknowledgment and shared consent guard semantics. Foreign installations remain inert. Root implements real removal drainer/installed-cache scope; this worker must not pretend that a pending request completes deletion.

Run meaningful actual codec and DataStore tests listed in the design. Existing Robolectric dependencies may require hosted Android environment; report not_run rather than SDK stubs or fabricated passes. Actual K2 compiler/JUnit jars are /workspace/scratch/kotlin-probe/lib for feasible pure-source tests. Request root changes outside ownership. Commit/push complete bounded implementation plus replaceable <=10line handoff listing exact SHA, actual tests, limitations and next independent review. No remote app publication.
