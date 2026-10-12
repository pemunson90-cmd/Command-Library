# K2 step 2: Claude Code work notes (read this first, Codex)

Single file to resume from. It is updated at every Claude Code checkpoint; the last section always says exactly what to do next.

## Why Claude Code is here

Codex ran out of usage with its lease expired (2026-10-11T23:47:41Z). Pat authorized Claude Code to take over K2 step 2 and leave these notes (DECISIONS.md, "Lease takeover, 2026-10-12"). Claude Code follows CONTINUE.md and the K2 card `coordination/tasks/orch-kotlin-m0-01.md`.

## State at takeover (verified, not assumed)

- `main` = `575159ba13be87def464fa419d1bbcb22a48211d`: core contracts integrated, independently accepted by Astra, hosted compile success (run 38182564573, 25 JVM tests per Codex's state notes). Core lock released.
- Nothing of K2 step 2 existed: no Android module, no Room3/FTS5, no importer, no DataStore, no UI, no APK.
- Claude QA branch `agent/cc-k2-qa-01` (836e6df) is READY-FOR-REVIEW: test matrix, synthetic vectors, contract review. It is not merged. Its author is Claude Code, so an independent reviewer (Codex or Astra) should review it, not Claude Code.

## Environment facts for whoever continues

- This Claude Code container has JDK 21 with `javac` and Gradle; no Android SDK preinstalled; no `/dev/kvm`, so no local emulator. Instrumentation and emulator checks must run on hosted GitHub Actions.
- `scripts/checkpoint.py` hard-codes `codex` in the JOURNAL line and does not change lease `session_id`/`model_family`; Claude Code corrected both by hand after running it.
- Claude Code installed locally (environment only, never committed): OpenJDK 17 via apt, Android cmdline-tools 23.0 at `/opt/android-sdk`, `platforms;android-36`, `platforms;android-37.0`, `build-tools;36.0.0`, `platform-tools`. Build with `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/opt/android-sdk`.
- Maven Central sometimes returns HTTP 429 through the proxy. Retry; use `--max-workers=2`. It is not a build defect.
- Existing core checks pass locally on JDK 17: `cd android-app && ./gradlew --no-daemon :core:model:check :core:common:check` gave 13 + 12 = 25 JVM tests, 0 failures (matches hosted run).

## Progress log

| Checkpoint | Commit | What |
|---|---|---|
| cp-20261012-claude-k2-14 | e9afe60 | Lease claimed; this file created. |
| cp-20261012-claude-k2-15 | 5a8efcb | K1 tuple resolved and compiled in a scratch probe. Evidence: `docs/gauntlet/gates/K2-TUPLE-PROBE.md`. One binding changed: compileSdk 36 to 37 (Compose BOM 2026.09.00 AARs require it); target 36, min 23 and all library versions unchanged. Independent review requested: `docs/gauntlet/gates/K2-TUPLE-REVIEW.md` (written by a separate reviewer; if absent, the review did not finish). |
| (no checkpoint id) | 64f828a | Cards `coordination/tasks/cc-storage-01.md`, `cc-settings-01.md`; locks `storage.lock`, `settings.lock` claimed by claude-code; version catalog and root plugins gain the probe-proven tuple (no module included on main yet). |
| (no checkpoint id) | 01cc040 | Workflow `.github/workflows/android-k2.yml` (JVM check + APK zipalign job; emulator matrix API 23, API 36, API 36 16 KiB page size; no third-party actions). First run on main: 38188001791. |

| cp-20261012-claude-k2-16 | (this commit) | Tuple review ACCEPT-WITH-CHANGES applied: lock compileSdk 37, K1 supersession note, probe doc fixes, retained logs. |
## Delegated work in flight (Claude Code Sonnet-tier implementers)

| Task | Branch | Base | Writes | Status |
|---|---|---|---|---|
| cc-storage-01 | `agent/cc-storage-01` | c3abc98 (prep: includes `:data:storage`) | `android-app/data/storage/**` | running; handoff `coordination/handoffs/cc-storage-01.md` on the branch |
| cc-settings-01 | `agent/cc-settings-01` | b39a3b1 (prep: includes `:feature:settings`) | `android-app/feature/settings/**` | running; handoff `coordination/handoffs/cc-settings-01.md` on the branch |

If Claude Code stops before these finish, read each branch's handoff; continue from its Next field. Do not redo a step whose `[cc-<c>-01 step k/N]` commit exists. Neither branch contains the new workflow yet: before CI can run on them, merge `main` into the branch (merge commit, no rebase) after the worker's last push.

## Next action

1. Done (cp-20261012-claude-k2-16): tuple review ACCEPT-WITH-CHANGES (`docs/gauntlet/gates/K2-TUPLE-REVIEW.md`). Lock now has compileSdk 37 with the pinned `platforms;android-37.0` r2 package; K1 line marked superseded; probe doc corrected; sanitized probe logs retained in `docs/gauntlet/gates/k2-tuple-probe/`.
2. Check run 38188001791 (workflow `android-k2.yml` on main). Expected: JVM job passes core `check`; emulator jobs print "No androidTest sources" and skip. Fix the workflow if not.
3. When each worker reports READY-FOR-REVIEW: merge `main` into its branch (merge commit), push, confirm `android-k2.yml` runs green including the emulator matrix for storage; then an independent reviewer (not the author) reviews the branch against its card and K2-FOUNDATION-DESIGN.md; fix blockers; merge to main; release its lock.
4. After storage merges: write `cc-catalog-01` (`:feature:catalog`: bounded importer, staging/activation, `CatalogLookup` with escaped FTS literal and cursor, `CatalogStatus`, genuine Scryfall fixture with provenance). It issues SQL through the storage `Database` port using the parameter rule in the storage handoff.
5. Then `:feature:shell` and `:app` (card steps 3 and 4).

Open for Pat (not blocking): settings defaults chosen by the orchestrator because none are documented: image cache 0 bytes, retain rulings off, Wi-Fi-only downloads on, budget unknown (see `coordination/tasks/cc-settings-01.md`).
