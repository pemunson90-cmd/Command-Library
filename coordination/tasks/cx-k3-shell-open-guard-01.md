# Task cx-k3-shell-open-guard-01

Owner delegated gpt-6-sol medium. Isolated worktree /workspace/Command-Library-k3-shell, branch agent/k3-shell-open-guard-01. Exclusive android-app/feature/shell/** and coordination/handoffs/cx-k3-shell-open-guard-01.md. No Claude rules, catalog, core, app, shared config/coordination writes.

Fix ImportController.open resource leak when documents.open returns a stream after cancellation and withContext prompt cancellation discards that return. Establish ownership before crossing dispatcher return; close exactly once on cancelled return without swallowing cancellation. Normal importer/failure closure behavior preserved. Add deterministic meaningful regression exercising cancellation during actual dispatcher hop/open and verifying close and no importer invocation. No timing sleeps or broad UI refactor. Audit existing tests for compatibility. Root supplies hosted verification and integration; no claimed local tests without tools.

Update owned handoff with replaceable Latest continuation delta, exact commit/files/evidence/next action. Commit/push coherent candidate. No subagents.
