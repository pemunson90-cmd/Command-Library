# DECISIONS

Ledger of every decision. Pat's rulings are quoted verbatim. Status values: Open, Ruled, Superseded.
Intake rulings (the twelve answers) are in GAUNTLET.md Appendix A, verbatim, and outrank this ledger's recommendations.
Conflicts between orchestrators, or between GAUNTLET.md and Appendix A, are logged here for Pat.

## Index

| ID | Decision | Status |
|---|---|---|
| D-01 | Package / application ID | Open: Pat rules |
| D-02 | Repo name and visibility | Open |
| D-03 | Release signing key | Open |
| D-04 | Default currency and price source | Open (after R-08) |
| D-05 | Claude Code and Codex concurrency | Open |
| D-06 | Windows installer format and code signing | Open (after R-15) |
| D-07 | Stack | Open (after R-01) |
| D-08 | Max concurrent implementer agents | Open (Gate 3) |
| D-09 | Integration branch and push permissions | Open: blocks Gate 1 workflow |
| D-10 | Subagent model tiering | Ruled 2026-10-11 |

## Open

### D-01 Package / application ID
- Proposed: `com.architectpsg.commandlibrary`.
- Recommendation: accept.
- Tradeoff: permanent once installed. Changing it later makes a separate app and needs a manual data move.

### D-02 Repo name and visibility
- Current: `pemunson90-cmd/Command-Library`, public, default branch `main`.
- Recommendation: keep the name, keep it public for now.
- Tradeoff: public repos run standard GitHub-hosted runners free (source: https://docs.github.com/en/billing/concepts/product-billing/github-actions, accessed 2026-10-11). Private repos draw from a plan minute quota, and Windows minutes cost more than Linux minutes. Cost of public: the spec and code are visible to anyone. Secrets never enter the repo either way. R-15 verifies current billing figures.

### D-03 Release signing key
- Finding: Pocket-Steward CI builds debug-signed APKs only (`.github/workflows/main.yml`, `BUILD_ENVIRONMENT.md` line 157). No release-key workflow exists there to copy.
- Options: (a) Pat's existing permanent key, added to this repo as GitHub Actions secrets; (b) a new key for this app.
- Recommendation: (a) if the keystore file exists and Pat can reach it. One key to back up.
- Tradeoff: (a) a leak of one key affects every app signed with it; (b) isolates the blast radius but adds a second keystore Pat must back up forever. Losing the key means users cannot update in place.
- Needed from Pat: does the keystore exist, and where.

### D-04 Default currency and price source
- Ruled after R-08.

### D-05 Claude Code and Codex concurrency
- Recommendation: Claude Code alone through Gate 3; both orchestrators concurrently from M0 onward under the lock protocol.
- Tradeoff: Codex sits idle early, versus two orchestrators producing duplicate research and conflicting edits to SPEC, DECISIONS, and STATE, which are orchestrator-owned shared files with no lock.

### D-06 Windows installer format and code signing
- Ruled after R-15.
- Preliminary recommendation: unsigned installer for personal use. SmartScreen shows a warning that Pat clicks through once per install. Revisit for public release (POST_RELEASE.md).

### D-07 Stack
- Ruled after R-01. Fallback: Kotlin, Jetpack Compose, Room, Navigation Compose, DataStore, Gradle, GitHub Actions; Windows via Kotlin Multiplatform with Compose Multiplatform desktop.

### D-08 Max concurrent implementer agents
- Set at Gate 3. Default 3.

### D-09 Integration branch and push permissions
- Conflict: GAUNTLET.md 5.2 and 5.4 require the orchestrator to claim locks and merge on `main`, and implementers to push `agent/<task-id>` branches. This cloud session is instructed to push only to `ccr-b0894b79-qzhxhw` unless Pat gives explicit permission. Every new cloud session gets a new `ccr-*` branch name.
- Finding (R-00): subagent worktrees are created from the repo default branch (`main`, commit 512cdcc), not from the session branch. With work held on a `ccr-*` branch, every implementer starts on stale code unless it re-checks out the base by hand.
- Options:
  - (a) Pat authorizes Claude Code to push to `main` and to `agent/*` branches in this repo. Protocol runs as written; Codex sees locks on `main`.
  - (b) Work stays on the session branch; Pat merges a PR to `main` at every gate. Locks and merges stay invisible to Codex and to fresh worktrees until Pat merges.
- Recommendation: (a), starting by merging this Gate 0 branch into `main`.
- Status: Open. Until ruled, the provisional integration branch is `ccr-b0894b79-qzhxhw` and no lock is claimed.

## Ruled

### D-10 Subagent model tiering
- Pat, 2026-10-11, verbatim: "Delegate subagents wisely. If you can use sonnet or haiku subagents, do so. Not every task is worthy of Opus"
- Applied: architect and reviewer use the strongest model (GAUNTLET.md 11). Researcher, test-writer, ui-auditor: Sonnet. Implementer: Sonnet, confirmed at Gate 3 once step plans exist. Mechanical probes and scans: Haiku. The orchestrator may raise a single invocation's tier when the task card justifies it (for example R-01 stack selection) and records why in the task card.
