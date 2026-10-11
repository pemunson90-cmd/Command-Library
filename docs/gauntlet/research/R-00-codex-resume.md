# R-00 Codex resume tooling addendum

## Question

Which Codex mechanisms are actually available when resuming Gate 1 on 2026-10-11? The original Claude R-00 remains intact.

## Findings

- Local `codex --version` reports `codex-cli 0.159.2`; `codex --help` succeeds with a read-only PATH-alias warning. Help exposes `exec`, `resume`, `agents`, `--worktree`, `--sandbox` and approval options. This is command evidence from this session, not proof a later invocation will inherit connector authentication.
- Native `collaboration.spawn_agent`, `followup_task`, `send_message`, `list_agents` and `wait_agent` are callable. Six research agents received self-contained task messages, wrote their assigned files and returned topic-specific findings. Role text is supplied by the tool-neutral brief plus the task card. Only the orchestrator delegates. Official mechanism documentation: https://developers.openai.com/api/docs/guides/responses-multi-agent (accessed 2026-10-11). Source corroboration: https://github.com/openai/codex/blob/main/codex-rs/prompts/src/model_messages/multi_agent.rs (accessed 2026-10-11).
- The session allows seven total concurrent agents including root. Research has six at a time, one owned topic file each. Sonnet/Haiku/Opus are not exposed; agents inherit the available OpenAI model. Historical D-10 names are not pretended to be callable. Implementer concurrency remains D-08 at Gate 3.
- Supplied sandbox is workspace-write, writable `/workspace` and `/tmp`, network through the configured proxy. Cloud environment status reports unrestricted HTTP policy enforced and no configured secret bindings or outbound identities. The supported policy snapshot reports no VPN. Credential values were not inspected.
- Shell `git clone` and bounded `git ls-remote` both fail immediately with inability to connect to proxy:8080. The local directory is a connector-recovered file snapshot, not a Git checkout. No shell push, branch checkout, worktree or build success is claimed. Git 2.52.0 is installed. CLI exposes managed worktrees but actual repository worktree behavior remains UNVERIFIED until normal transport is restored.
- GitHub connector authenticates to `pemunson90-cmd/Command-Library`, confirms public visibility/main/admin+push permission, reads the recursive tree/files and publishes fast-forward Git-data commits with an expected head lease. Resume checkpoint `8e5ea03023fec4068e3a8191cbe808db01cc24c5` and topic checkpoints establish actual write access. Source: https://github.com/pemunson90-cmd/Command-Library/commit/8e5ea03023fec4068e3a8191cbe808db01cc24c5 (accessed 2026-10-11). This substitutes remote Git operations during documentation research only; Gate 3 must resolve real checkout/worktree isolation before implementers run.
- Both cited Codex issues remain open in current GitHub API reads: https://github.com/openai/codex/issues/37870 updated 2026-08-18, and https://github.com/openai/codex/issues/35932 updated 2026-08-03 (accessed 2026-10-11). Neither issue proves this newer CLI reproduces the bug. Reuse the six existing researchers through follow-up tasks, use native OpenAI agents and checkpoint topic completion. Custom-provider payload loss is not tested. Restart at gate boundaries per supplied Codex gauntlet mitigation.
- AGENTS.md was absent at resume; CLAUDE.md served as historical entry. STATE, open decisions, all cards and journal were read; tree contained only keep-files in locks/handoffs. Main head was ab33a6c, the historical WIP checkpoint. Latest user D-05 overrides the historical Claude-only choice. Supplied Codex protocol is retained separately at `docs/gauntlet/GAUNTLET_CODEX.md`; GAUNTLET.md and CLAUDE.md remain intact.

## Options

1. Use verified native research agents and connector-backed commits now.
2. Restore normal shell transport before any implementation requiring Git worktrees.

## Recommendation

Use option 1 for Gate 1, with sequential main updates and published topic checkpoints. Resolve option 2 before Gate 3 execution plans promise worktree isolation. Do not regenerate the already-delivered signing key or place its uploaded sheets in any tracked file.

## Risks

Connector snapshots lack local Git history; current source measurements requiring shell HTTP are unavailable. Every affected report marks them UNVERIFIED. Remote refs can change concurrently, so expected-head failures require re-read and reconciliation, never force updates.

## Confidence

High for observed native-agent and connector operations. Low for untested local checkout/worktree behavior and reproduction of historical CLI issues.
