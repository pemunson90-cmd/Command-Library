# Shared continuation protocol

Applies to Codex, other GPT models, Claude and Claude Code. Latest D-17 user mandate controls earlier tool-specific/orchestrator/gate-stop text. The supplied original GAUNTLET documents are historical baseline requirements, not rewritten.

## What "continue" authorizes

Resume the exact committed queue pointer, review plans, carry out reversible planned work within the current reviewed product scope, and publish bounded checkpoints. Any model family may replace a stopped prior orchestrator. One orchestrator at a time; component workers may run only under exclusive locks and merged dependency interfaces. Do not impersonate another author, infer missing approvals, report planned tests as run, or drop requirements to simplify handoff.

D15/D16 were previously proposed, not explicitly approved; D17 now authorizes using the independently reviewed specification and proposed targets as the working technical baseline while orchestrating subsequent stages. Keep that distinction in decisions and results. Routine design/dependency/CI choices are delegated; changing product scope/English consent/security, relaxing a failed budget, spending money, deleting user data or public release is not. Write a concrete BLOCKER before asking only for actually needed information.

## Resume algorithm

1. Fetch README, CONTINUE, STATE, coordination/state.json and CURRENT from current main. Pin all reads to one verified head. Read latest applicable DECISIONS/current queue task; inspect locks/lease and final20 JOURNAL lines. Tool entry files must not introduce a different protocol.
2. Compare checkpoint_id across machine state and CURRENT. Verify checkpoint commit by locating that ID in GitHub commit message; current main may be a descendant. Validate the task's base/dependency commits. A checkpoint file cannot embed its own commit SHA; verified_base_head records its parent, while checkpoint_id locates the containing immutable commit.
3. Inspect local git status/branch/remotes or explicitly record snapshot mode. Never reset/clean/rebase/force-push someone else's work. Recover unpublished local changes to a bounded recovery branch/checkpoint before replacing files. A file snapshot without .git is not a checkout or worktree.
4. Claim coordination/ORCHESTRATOR.json in an expected-head atomic commit: session ID, model family, task, heartbeat UTC, expiry UTC, checkpoint ID and no secret. Existing fresh active lease means inspect/review only, never shared-file writes. Release when stopping. Expired lease is suspect, not automatic evidence of death: inspect named branch/task/handoff/head, preserve work, then record takeover reason atomically. Never steal a fresh lease.
5. Pick state.next_task, not an old "Next" section in a historical handoff. Confirm dependencies accepted, architecture merged before code, owned paths disjoint and tool capability adequate. If blocked, update blocker record, choose an independent eligible task from QUEUE, and record why; never repeat a known failing probe without new evidence.
6. Execute one numbered step. Save useful drafts early. Checkpoint after any finished step or valuable partial work, ideally within10-15minutes; do not wait for an entire gate or predicted usage exhaustion. Task state distinguishes draft/code/verified/reviewed/merged/blocked, with exact evidence and uncommitted losses.
7. Validate task-specific tests/fixtures/reviewer, path ownership and secret-safe content. Do not merge unchecked component code. Independent review must come from a different agent/session than its author; lack of agents means queue review for another model, never self-approve.
8. Publish code/docs, task state, CURRENT, both STATE files, queue and append-only journal together. Use ordinary fast-forward Git, or Git-data tree+commit+expected-head ref update. On rejection re-read main and reconcile; do not blindly retry stale state. Preserve unrelated concurrent ledger changes.
9. Re-fetch published head and exact key files; record whether checks/push/read-back succeeded. Print commit/checkpoint and CURRENT link. Release lease. Leave next eligible task/step/first action, prerequisites and remaining evidence.

## Bounded session contract

Default: one to three small eligible steps, around30-60minutes or earlier when context/tools/usage appear constrained. Stop on a clean checkpoint and do not require Pat to say which model takes over. Prefer reserving the final portion for publication/verification; actual usage limits are not always visible. If interrupted abruptly, committed drafts survive; no promise can preserve an unsaved edit after a hard kill. Reduce that risk through early publication.

A technical stage is ready only after acceptance evidence/review. Under D17 publish its gate report and advance automatically when all prerequisites pass, except where a current decision or CURRENT explicitly requires user execution release. D-20 requires that release after K0; no audit may auto-start K1. Record why advance is authorized. If actual product ruling/manual evidence/tool permission is needed, STATE includes exact blocker and safe next alternative, not a generic "ask user to continue." "Continue" is not proof a device test occurred.

## Handoff minimum

Every task handoff: checkpoint_id, owner, task/step, base and dependency SHAs, owned/shared paths, done list with evidence, work status, exact next action/commands, blockers, gotchas, files changed, remaining tests/review, runtime/tool limitations, safe fallback. CURRENT has one live next pointer; old handoffs are historical. QUEUE and machine state agree. Journals append; decisions quote user words and separate their implementation interpretation.

No essential instruction belongs only in a private agent memory, lost chat, tool session or ephemeral local branch.

## Models and unavailable tools

Use actual installed capabilities, not a provider name in old role definitions. Strongest available architect/reviewer, lower-cost implementer/research/mechanical helpers when suitable; record actual model or unknown. Never spawn recursive untracked workers. Agent definitions are adapters to tool-neutral role briefs. Read-only models can review/write downloadable patches; publisher-capable successor applies/commits with evidence. If GitHub write denied, preserve local recovery bundle and mark unpublished; do not claim durable GitHub completion.

## Serialization and partial recovery

Main is integration; agent/<task-id> branches isolate implementers. Shared configuration, schema/DI/navigation/CI registries and coordination files belong to the lease-holder. Component locks contain branch/base/step; claim/release through main commits. Draft architecture/research only writes the output named by task card.

For a stale lock inspect last branch commit/CI/handoff. Preserve orphan commit to named recovery ref if necessary before takeover. If takeover overlaps an alive writer, stop mutation and resolve ownership; don't delete another writer's commit or lock. Session-continuity tooling never contains secrets.

## Canonical state schema

coordination/state.json version1 requires project/repository/integration_branch/checkpoint_id/verified_base_head/stage/status/next_task/next_step/first_action/active_tasks/blockers/worktree_mode/review_status/verification/last_completed/updated_utc. Unknown values null with explanation; no made-up pass. ORCHESTRATOR schema1 status released/active, session/model/task/heartbeat/expires/checkpoint_id. Scripts/check_continuity.py validates pointers/docs/task completeness, not live lease ownership or app performance. Capture remote identity at resume, not from stale downloaded bundle.


## D-20 execution section policy (2026-10-11)

Pat released K1 after requesting Astra verification and a complete scalable Kotlin Android application. For this project, each section must have a defined functional finish line, independent review where architecture/security/durability requires it, a published checkpoint and exact next action. A usage-limit checkpoint can preserve incomplete work but must say so. Do not advance to K2 merely because K1 has a candidate version list; K1 requires reviewed evidence and an explicit Kotlin ownership/replacement map.
