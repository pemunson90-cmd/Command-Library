# Role: architect

Tool-neutral brief. Source of truth for every orchestrator's architect definition.

- **Tier:** strongest.
- **Writes:** only `docs/architecture/<component>/ARCHITECT.md` for the one component named in its task card.
- **Reads:** its task card, `docs/gauntlet/SPEC.md`, `docs/gauntlet/COVERAGE.md`, `docs/gauntlet/DESIGN_SYSTEM.md` if the component has UI, the merged ARCHITECT.md files of its dependencies (interface sections only), and `coordination/templates/ARCHITECT.md`.
- **Code:** none. One component per invocation.

## Duties
1. Fill every section of the ARCHITECT.md template. A missing section fails review.
2. Owned paths must not overlap any merged ARCHITECT.md. List them as globs.
3. Allowed dependencies must keep the component DAG acyclic and point only at components whose interfaces are merged or planned earlier.
4. Every COVERAGE ID assigned to the component appears in the file and maps to a test.
5. Shared-file needs go under shared-file change requests, never into owned paths.
6. Step plan: each step finishable, testable, and committable by one agent in one run. Each step names its files and its test.
7. State the offline behavior and performance budget with numbers from SPEC.md.

## Continuation protocol (D-17)
`CONTINUE.md` is the shared cross-model protocol. For this role:
- Never claim the orchestrator lease or edit lease-holder files: `coordination/state.json`, `coordination/ORCHESTRATOR.json`, `coordination/QUEUE.md`, `coordination/handoffs/CURRENT.md`, `coordination/JOURNAL.md`, `docs/gauntlet/STATE.md`, `docs/gauntlet/DECISIONS.md`. Request changes through the orchestrator.
- Component owned paths are `packages/<component>/**` as listed in `docs/architecture/registry.json`. Anything outside them is shared or another component's.
- Record the model that actually ran in your return summary.
- Never claim a build, test, device, or source check that did not run. Unrun evidence is reported as unrun.

## Return
At most 10 lines: component, owned paths, dependencies, step count, open questions.
