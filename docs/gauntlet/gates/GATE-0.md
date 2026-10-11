# GATE 0: Bootstrap

- Date: 2026-10-11
- Orchestrator: Claude Code
- Branch: `ccr-b0894b79-qzhxhw` (provisional integration branch, see D-09)
- Status: awaiting Pat's rulings

## Done

- Repo access and push confirmed: commit f4fa915 pushed.
- Section 4 layout created:
  - `CLAUDE.md` (entry file, under 100 lines)
  - `docs/gauntlet/`: GAUNTLET.md (verbatim, byte-identical to the uploaded file), STATE.md, DECISIONS.md (seeded D-01 to D-08, plus D-09 to D-11), RESEARCH_SUMMARY.md, SPEC.md, DESIGN_SYSTEM.md, COVERAGE.md, MILESTONES.md, IMPOSSIBLE.md, RISKS.md, POST_RELEASE.md, `research/R-00-tooling.md`, `gates/GATE-0.md`
  - `docs/architecture/` (empty until Gate 3)
  - `coordination/agents/` six tool-neutral role briefs; `tasks/`, `locks/`, `handoffs/` (empty); `JOURNAL.md`
  - `.claude/agents/` six subagent definitions generated from the briefs; `claude plugin validate .claude/agents` passed
- No app code.

## Additions beyond the section 4 layout

- `coordination/templates/`: formats for lock files, task cards, handoffs, ARCHITECT.md, and research files.
- `.gitignore`: `.claude/worktrees/`, `.claude/settings.local.json`.

## Tooling findings (detail in R-00)

1. **Worktree isolation works here, but worktrees start from `main`**, not from the session branch (probe: HEAD 512cdcc while the session branch was at f4fa915). Every worktree agent now checks out `agent/<task-id>` at its base commit before doing anything. With integration on `main` (D-09), the default base is also correct.
2. **Subagents cannot spawn subagents here:** the environment sets `CLAUDE_CODE_MAX_SUBAGENT_SPAWN_DEPTH=1`. Current docs say nesting is on by default elsewhere, so every definition also sets `disallowedTools: Agent`.
3. **Hooks do not load in this session** because two repos are attached (Command-Library and Pocket-Steward). Hooks are deferred to Gate 3; the CI placeholder scan is the authoritative check.
4. **The GitHub proxy rejects branch deletions** from cloud sessions. Merged `agent/*` branches will accumulate until deleted from the GitHub UI.
5. **Agent definitions load at the next session start.** `.claude/agents/` did not exist when this session began, so hot reload does not pick it up. For the rest of this session, delegations use general-purpose subagents with the model set per D-10 and the role brief path in the prompt.
6. **Background subagents verified:** both Gate 0 agents (Sonnet docs research, Haiku worktree probe) ran in the background and reported by notification.

## Reference read (read-only)

Pocket-Steward, as Pat's known working setup: CI on `ubuntu-latest`, Temurin JDK 21, `gradle/actions/setup-gradle@v4`, runs unit tests, lint, and `assembleDebug`. The APK is debug-signed. No release-signing workflow exists to reuse (feeds D-03, R-01, R-15).

## Rulings needed

Detail and tradeoffs in DECISIONS.md.

1. **D-09 Integration branch.** Recommend: authorize pushes to `main` and `agent/*`, and merge this branch into `main` first.
2. **D-01 Package ID.** Recommend `com.architectpsg.commandlibrary`.
3. **D-03 Signing key.** Does your permanent keystore exist, and where? Recommend reusing it via Actions secrets.
4. **D-05 Codex concurrency.** Recommend Claude Code alone through Gate 3, both from M0.
5. **D-02 Visibility.** Repo is public. Recommend staying public (free standard runners).
6. **D-11 Session setup.** Recommend future sessions attach only Command-Library.

Deferred: D-04 after R-08, D-06 after R-15, D-07 after R-01, D-08 at Gate 3.

## Next on approval: Gate 1

17 research task cards (`cc-research-01` to `cc-research-17`). Researchers run on Sonnet, launched in parallel batches of about 6. R-01 (stack selection) runs on Opus because every later gate rests on it; the reason goes in its task card per D-10. Then RESEARCH_SUMMARY.md with the three parity matrices, then `gates/GATE-1.md`.
