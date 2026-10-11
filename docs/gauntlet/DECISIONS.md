# DECISIONS

Ledger of every decision. Pat's rulings are quoted verbatim. Status values: Open, Ruled, Superseded.
Intake rulings (the twelve answers) are in GAUNTLET.md Appendix A, verbatim, and outrank this ledger's recommendations.
Conflicts between orchestrators, or between GAUNTLET.md and Appendix A, are logged here for Pat.

## Index

| ID | Decision | Status |
|---|---|---|
| D-01 | Package / application ID | Ruled 2026-10-11 |
| D-02 | Repo name and visibility | Ruled 2026-10-11 |
| D-03 | Release signing key | Ruled 2026-10-11 |
| D-04 | Default currency and price source | Open (after R-08) |
| D-05 | Claude Code and Codex concurrency | Ruled 2026-10-11 (through Gate 3) |
| D-06 | Windows installer format and code signing | Open (after R-15) |
| D-07 | Stack | Open (after R-01) |
| D-08 | Max concurrent implementer agents | Open (Gate 3) |
| D-09 | Integration branch and push permissions | Ruled 2026-10-11 |
| D-10 | Subagent model tiering | Ruled 2026-10-11 |
| D-11 | Cloud session setup: repos attached per session | Ruled 2026-10-11 |

## Open

### D-04 Default currency and price source
- Ruled after R-08.

### D-06 Windows installer format and code signing
- Ruled after R-15.
- Preliminary recommendation: unsigned installer for personal use. SmartScreen shows a warning that Pat clicks through once per install. Revisit for public release (POST_RELEASE.md).

### D-07 Stack
- Ruled after R-01. Fallback: Kotlin, Jetpack Compose, Room, Navigation Compose, DataStore, Gradle, GitHub Actions; Windows via Kotlin Multiplatform with Compose Multiplatform desktop.

### D-08 Max concurrent implementer agents
- Set at Gate 3. Default 3.

## Ruled

### D-01 Package / application ID
- Proposed: `com.architectpsg.commandlibrary`.
- Recommendation: accept.
- Tradeoff: permanent once installed. Changing it later makes a separate app and needs a manual data move.
- Pat, 2026-10-11, verbatim: "com.architectpsg.commandlibrary"
- Applied: application ID `com.architectpsg.commandlibrary` on Android; the same string is the Windows package identifier where the packaging format takes one (R-15).

### D-02 Repo name and visibility
- Current: `pemunson90-cmd/Command-Library`, public, default branch `main`.
- Recommendation: keep the name, keep it public for now.
- Tradeoff: public repos run standard GitHub-hosted runners free (source: https://docs.github.com/en/billing/concepts/product-billing/github-actions, accessed 2026-10-11). Private repos draw from a plan minute quota, and Windows minutes cost more than Linux minutes. Cost of public: the spec and code are visible to anyone. Secrets never enter the repo either way. R-15 verifies current billing figures.
- Pat, 2026-10-11, verbatim: "Stay public."
- Applied: repo stays `pemunson90-cmd/Command-Library`, public.

### D-03 Release signing key
- Finding: Pocket-Steward CI builds debug-signed APKs only (`.github/workflows/main.yml`, `BUILD_ENVIRONMENT.md` line 157). No release-key workflow exists there to copy.
- Options: (a) Pat's existing permanent key, added to this repo as GitHub Actions secrets; (b) a new key for this app.
- Recommendation: (a) if the keystore file exists and Pat can reach it. One key to back up.
- Tradeoff: (a) a leak of one key affects every app signed with it; (b) isolates the blast radius but adds a second keystore Pat must back up forever. Losing the key means users cannot update in place.
- Needed from Pat: does the keystore exist, and where.
- Pat, 2026-10-11, verbatim: "[where your keystore is, or "make a new key"]"
  - Context: this is the fill-in text from the ruling template, sent unfilled. No option was chosen, so D-03 stayed open until the follow-up below.
- Pat, 2026-10-11, verbatim: "Make a new key"
- Applied: option (b). Command Library gets its own release keystore, separate from Pat's existing key. The key is not generated in a cloud session: the private key would sit in an ephemeral VM and have to travel to Pat through chat or a file. The creation method (keytool on Pat's PC, or a workflow that writes directly to GitHub Actions secrets), the secret names, and Pat's backup steps are set from R-15 before M0. Key material and passwords never enter the repo, logs, or chat. Losing the keystore means installed builds cannot be updated in place, so Pat keeps two backups.

### D-05 Claude Code and Codex concurrency
- Recommendation: Claude Code alone through Gate 3; both orchestrators concurrently from M0 onward under the lock protocol.
- Tradeoff: Codex sits idle early, versus two orchestrators producing duplicate research and conflicting edits to SPEC, DECISIONS, and STATE, which are orchestrator-owned shared files with no lock.
- Pat, 2026-10-11, verbatim: "Claude Code alone through Gate 3."
- Applied: Claude Code is the only orchestrator through Gate 3. Codex concurrency from M0 onward is not yet ruled; it returns at Gate 3 together with D-08.

### D-09 Integration branch and push permissions
- Conflict: GAUNTLET.md 5.2 and 5.4 require the orchestrator to claim locks and merge on `main`, and implementers to push `agent/<task-id>` branches. This cloud session is instructed to push only to `ccr-b0894b79-qzhxhw` unless Pat gives explicit permission. Every new cloud session gets a new `ccr-*` branch name.
- Finding (R-00): subagent worktrees are created from the repo default branch (`main`, commit 512cdcc), not from the session branch. With work held on a `ccr-*` branch, every implementer starts on stale code unless it re-checks out the base by hand.
- Options:
  - (a) Pat authorizes Claude Code to push to `main` and to `agent/*` branches in this repo. Protocol runs as written; Codex sees locks on `main`.
  - (b) Work stays on the session branch; Pat merges a PR to `main` at every gate. Locks and merges stay invisible to Codex and to fresh worktrees until Pat merges.
- Recommendation: (a), starting by merging this Gate 0 branch into `main`.
- Pat, 2026-10-11, verbatim: "A"
  - Context: answering the offer: Option A, tell me "merge to main" and I'll push it now. That also rules D-09.
- Applied: option (a). Integration branch is `main`. The first push to `main` was blocked by the session's auto-mode permission check. Pat then chose, verbatim, "2." (switch out of auto mode and approve the push), and `main` was fast-forwarded from 512cdcc to 08cd06a. Each new cloud session still needs Pat to grant push to `main` and `agent/*` in that session, because the session's own instructions limit pushes to its branch.
- Pat, 2026-10-11, verbatim (Gate 1 session, branch `ccr-d1e9ac59-b90cv2`): "Approved. You may push to main and to agent/* branches in this repo."
  - Applied: commits land on the session branch and are pushed to both it and `main`, so the two stay identical and `main` remains the integration branch.

### D-10 Subagent model tiering
- Pat, 2026-10-11, verbatim: "Delegate subagents wisely. If you can use sonnet or haiku subagents, do so. Not every task is worthy of Opus"
- Applied: architect and reviewer use the strongest model (GAUNTLET.md 11). Researcher, test-writer, ui-auditor: Sonnet. Implementer: Sonnet, confirmed at Gate 3 once step plans exist. Mechanical probes and scans: Haiku. The orchestrator may raise a single invocation's tier when the task card justifies it (for example R-01 stack selection) and records why in the task card.

### D-11 Cloud session setup: repos attached per session
- Finding (R-00): this session has Command-Library and Pocket-Steward attached. In an Anthropic-hosted session with several repositories, no repository's `.claude/settings.json` hooks, permission rules, or `env` load (https://code.claude.com/docs/en/cloud-environments, accessed 2026-10-11).
- Recommendation: start future gauntlet sessions with only Command-Library attached.
- Tradeoff: repo hooks and permission rules load (fewer prompts, enforcement hooks possible at Gate 3), versus losing direct read access to Pocket-Steward as a reference. What R-01 and R-15 need from it is already recorded in `gates/GATE-0.md`, and a research agent can read it from GitHub when it is attached.
- Pat, 2026-10-11, verbatim: "Approved."
- Applied: the Gate 1 session (session branch `ccr-d1e9ac59-b90cv2`) has only Command-Library attached. Verified at session start: the six `.claude/agents/` definitions loaded as subagent types without hot reload, and `CLAUDE_CODE_MAX_SUBAGENT_SPAWN_DEPTH=1` is still set by the environment. No `.claude/settings.json` exists yet, so no repo hooks or permission rules are in play.
