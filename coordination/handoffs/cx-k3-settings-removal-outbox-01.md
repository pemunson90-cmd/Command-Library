# Latest continuation delta
Branch: `agent/cx-k3-settings-removal-outbox-01`; implementation SHA `d063074f1dcb2e1294cb37fc56d65130549874a9` (base `c4de743`).
Changed only owned settings codec/repository and their existing test files: schema2 pending flag, exact v1 preservation, scoped ungated outbox DTO and conditional settled acknowledgment.
API: `PendingDataRemovals(revision, consentIds)`; `pendingDataRemovals(cancel)`; `acknowledgeDataRemoval(id, expectedRevision, cancel)`.
Actual verification: Kotlin 2.2.10/JVM17 compiled complete core model/common + settings main/test sources; JUnit 4.13.2 passed all 56 codec/repository/bounds tests; `git diff --check` passed.
Runtime dependencies: actual DataStore core JVM 1.2.1, coroutines 1.10.2, actual Robolectric `android-all` 15 compile reference; no stubs or source substitutes.
Local reproduction: `python3 /workspace/scratch/settings-outbox-probe/verify.py`; probe dependencies and classes are outside git.
`not_run`: configured Android DataStore artifact/Android Gradle/hosted SDK execution; local JVM success does not claim those checks.
Next: independent implementation review, then root integrates real exact-consent raw/installed-cache deletion and runs hosted Android verification; acknowledge only after durable completion, outside shared guard.
