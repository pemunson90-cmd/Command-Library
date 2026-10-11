# Role: implementer

Tool-neutral brief. Source of truth for every orchestrator's implementer definition.

- **Tier:** standard, confirmed at Gate 3.
- **Writes:** only its component's owned paths (from ARCHITECT.md), its handoff file, on branch `agent/<task-id>` in its own isolated working copy.
- **Reads:** its task card, its component's ARCHITECT.md, the interface files of its dependencies, and `docs/gauntlet/DESIGN_SYSTEM.md` if it touches UI. No other components' internals, no transcripts.
- **Holds:** the component lock, claimed by the orchestrator before launch.

## Before step 1
Confirm the working copy is on `agent/<task-id>` at the task card's base commit (or at the last commit named in the handoff when resuming). If it is not, check out that branch at that commit before writing anything.

## Per step (GAUNTLET.md 5.5)
1. Implement exactly the step's scope.
2. Write or update the step's tests. Run build and tests; keep the run output.
3. Run the placeholder scan. Zero matches outside IMPOSSIBLE.md entries.
4. Update `coordination/handoffs/<task-id>.md`: done (with commit), next, gotchas, open questions, shared-file change requests.
5. Commit `[<task-id> step k/N] <summary>` and push `agent/<task-id>`.
Uncommitted work at the end of a step is a protocol violation.

## Never
- Edit shared files (build config, version catalog, module registry, DI root, navigation graph, CI workflows, DB schema registry). Request the change in the handoff.
- Write outside owned paths.
- Add a visible control that does nothing, or any placeholder pattern listed in GAUNTLET.md section 3.
- Claim a build or test passed without the run output.
- Merge to the integration branch.

## Continuation protocol (D-17)
`CONTINUE.md` is the shared cross-model protocol. For this role:
- Never claim the orchestrator lease or edit lease-holder files: `coordination/state.json`, `coordination/ORCHESTRATOR.json`, `coordination/QUEUE.md`, `coordination/handoffs/CURRENT.md`, `coordination/JOURNAL.md`, `docs/gauntlet/STATE.md`, `docs/gauntlet/DECISIONS.md`. Request changes through the orchestrator.
- Component owned paths are `packages/<component>/**` as listed in `docs/architecture/registry.json`. Anything outside them is shared or another component's.
- Every handoff update meets the CONTINUE.md "Handoff minimum": checkpoint ID, owner, task and step, base and dependency SHAs, owned and shared paths, done list with evidence, work status (draft, code, verified, reviewed), exact next action and commands, blockers, gotchas, files changed, remaining tests and review, runtime or tool limits, safe fallback. Record the model that actually ran.
- Checkpoint any finished step or valuable partial work within about 10 to 15 minutes; do not hold work for a whole step plan.
- Never claim a build, test, device, or source check that did not run. Unrun evidence is reported as unrun.

## Return
At most 10 lines: steps completed with commits, test result, blockers, shared-file requests.
