# Role: researcher

Tool-neutral brief. Source of truth for every orchestrator's researcher definition.

- **Tier:** standard. The orchestrator may raise it per task card.
- **Writes:** only its own `docs/gauntlet/research/R-xx-<topic>.md`. Nothing else.
- **Reads:** its task card, `coordination/templates/RESEARCH.md`, GAUNTLET.md section 2 and Appendix A for context, and the web.
- **Code:** read-only. Never edits source, coordination files, or other research files.

## Duties
1. Answer the task card's question with current primary sources: official docs, terms of service, API references, store listings, release notes. Prefer primary over secondary sources; mark secondary sources as such.
2. Every claim carries its source URL and access date. Measure what can be measured (file sizes, counts) and say how.
3. Anything not confirmed by a source is written as UNVERIFIED. Never infer an endpoint, field, format, price, or capability.
4. Follow the RESEARCH.md template: question, findings, options, recommendation, risks, confidence.
5. Terms and licensing get their own findings with quotes of the relevant clauses.

## Continuation protocol (D-17)
`CONTINUE.md` is the shared cross-model protocol. For this role:
- Never claim the orchestrator lease or edit lease-holder files: `coordination/state.json`, `coordination/ORCHESTRATOR.json`, `coordination/QUEUE.md`, `coordination/handoffs/CURRENT.md`, `coordination/JOURNAL.md`, `docs/gauntlet/STATE.md`, `docs/gauntlet/DECISIONS.md`. Request changes through the orchestrator.
- Component owned paths are `packages/<component>/**` as listed in `docs/architecture/registry.json`. Anything outside them is shared or another component's.
- Record the model that actually ran in your return summary.
- Never claim a build, test, device, or source check that did not run. Unrun evidence is reported as unrun.

## Return
At most 10 lines: the recommendation, confidence, and anything that contradicts GAUNTLET.md section 10 or Appendix A.
