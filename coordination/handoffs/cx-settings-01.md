# cx-settings-01 handoff

Checkpoint-ID: cp-20261012-cx-settings-bounds-01
Status: READY-FOR-REVIEW source; hosted verification pending, not scoped acceptance.
Owner: delegated Codex settings worker (parent requested Sol medium; effective backend not independently introspectable).
Task: cx-settings-01, bounded corrections to S1/S2/S3 and API23 clock exposure.
Branch: agent/cx-settings-01. Worktree: /workspace/Command-Library-cx-settings.
Original source: 714cd7e92ba3cf6a4abf2dd55802d2230e09e28b preserved.
Worktree starting head: fe5930a (root main merged into assigned branch).
Independent review: docs/gauntlet/gates/K2-SETTINGS-REVIEW.md.
Owned paths: android-app/feature/settings/** and this handoff only. No core, shared Gradle, CI, task/state/lock or original Claude branch edits.

Done:
- Complete serialization has the same 16 MiB bound on reads/writes, including CRC. BoundedOutputStream checks before forwarding every write. Candidate validation serializes into a bounded discard sink inside the transaction before DataStore receives the prospective state, protecting cumulative consent growth as well as individual fields. UTF8 encoding uses fixed8192-byte chunks, rather than allocating a string-sized encoded array.
- Strict CharsetEncoder/Decoder with CodingErrorAction.REPORT prevents replacement of malformed strings. Invalid prospective strings/sizes map to INVALID_INPUT; persisted malformed UTF8 remains CORRUPT_DATA. No corruption replacement handler or defaults reset added.
- API23 system-clock implementation uses integer quotient/remainder with explicit negative normalization; no Math floor APIs, java.time, minimum-SDK change or NewApi suppression. Module NewApi lint severity is fatal.
- Eight new regression tests exercise exact complete-store size including trailer, one-byte overflow, oversized Unknown reason, cumulative approvals, bounded-output refusal before forwarding, patch/approval surrogates, malformed/truncated UTF8 with valid CRC and unchanged bytes across repository restarts, supplementary Unicode intent equality after restart, and epoch/pre-epoch boundaries.
- Installation-ID/consent restore binding, revision rules and cancellation semantics unchanged.

Temporary K2 operational state, not final product defaults:
Cache0 / retainRulings=false exists only while acquisition/cache/rulings services are absent. SPEC A03/A05/K01 still require retained fetched rulings and default online-on-view art with bounded caching. Before those consumers ship, define a versioned/default-state transition preserving existing explicit choices and fetched rulings; persisted K2 zero must not be mistaken for the user's opt-out. Do not wire Lean/cache/delete-download success controls while affected behavior is absent. No image/network placeholder is implemented or requested. Full/English remains unchanged.

Actual checks:
- `git diff --check`: pass.
- Inspected `/usr/lib/jvm` and `/opt`: only JDK21, no Android SDK or JDK17. Gradle regression/lint/assemble commands: not_run locally; no repeated unsupported build probe.
- Original Claude35 JVM tests are preserved evidence for714cd7e, not a pass for these corrections.

Parent hosted commands, from android-app on the corrected branch:
`bash gradlew --no-daemon --console=plain :feature:settings:testDebugUnitTest :feature:settings:lintDebug :feature:settings:assembleDebug`
Existing android-k2 push workflow also runs aggregate check/assembly. Parent must observe final SHA/run/logs and independently recheck actual fixes before merge. No local/JVM test proves Android runtime or backup behavior.

Remaining acceptance: Android Context factory; API23 read/update/approve/reopen with the production system clock; Android cancellation/concurrency; noBackup installation-ID loss with retained preferences; backup/transfer exclusion; atomic replacement/process-death behavior; singleton composition lifetime; future same-install restore invalidation policy. These remain pending, never passing evidence.

Next action: root observes hosted regression, NewApi-fatal lint and assemble results for published branch; independently review the corrections and integration notes, then merge only if accepted. Safe fallback: preserve this scoped branch/source and leave merge pending if hosted checks cannot run. No user decision is needed for this authorized narrow repair.
