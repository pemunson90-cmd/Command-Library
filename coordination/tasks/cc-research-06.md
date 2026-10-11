# cc-research-06: R-06 Archidekt feature inventory and import

Path: `coordination/tasks/cc-research-06.md`. Written by the orchestrator. This card plus the files under Brief are everything the agent receives (GAUNTLET.md 5.7).

| Field | Value |
|---|---|
| Role | researcher |
| Component | research (Gate 1) |
| Orchestrator | claude-code |
| Model tier | standard (Sonnet, D-10) |
| Branch | none. Researchers write one file in the main checkout; the orchestrator commits it to `main` |
| Base commit | b1c922eaa96c25209691253c9849066b89a71846 |
| Steps | 1/1: write the research file |
| Status | queued |

## Question
What does Archidekt offer a deck builder today, which features should Command Library build, adapt, or skip, and is there a terms-compliant way to import an Archidekt deck by URL?

## Feeds
GAUNTLET.md 2.B (deck builder parity matrix), 2.B import and export.

## Scope (must cover)
- Feature inventory from Archidekt's own pages, help docs, changelog, and announcements. Cover at least every GAUNTLET.md 2.B item: commander(s) including partners, backgrounds, companions; mainboard, sideboard, maybeboard; custom categories and tags; drag-and-drop; per-card printing and finish; quantities; per-card notes; description or primer in Markdown; mana curve; color pips versus land sources; type breakdown; average mana value; land count guidance; hypergeometric calculator; opening hand simulator and playtester with mulligan rules; token list; per-format legality and validation; deck versions and diff; duplicate deck; folders; tags; search and sort across decks; import formats; export formats.
- Add every other Archidekt feature found (category system details, stacks and view modes, collection, price sources, bracket display, sandbox or playtest tools, social features, paid tier features). Mark paid features.
- Import feasibility: is there a public, documented API? Quote the Terms of Service and any robots or API policy clauses on automated access, scraping, and third-party clients. If URL import is not terms-compliant, document the export-then-paste flow step by step from Archidekt's UI, with the exact export formats offered.
- Exact Archidekt export text and CSV formats, with a sample of each copied from documentation or a public deck export (cross-check with R-16).

## Parity matrix (required section, after Findings)
A table with one row per feature: feature, how Archidekt does it (with source), recommendation (build, adapt, or skip), and reason. Every GAUNTLET.md item named in Scope gets a row even if the reference app lacks it. The orchestrator copies this table into RESEARCH_SUMMARY.md for Pat's ruling.

## Brief (files to read, nothing else)
- `coordination/agents/researcher.md` (role brief, source of truth)
- this card
- `coordination/templates/RESEARCH.md`
- `docs/gauntlet/GAUNTLET.md`: sections 2, 3, 10 and Appendix A, for context only

## Working rules
- Write only `docs/gauntlet/research/R-06-archidekt.md`. Create it early and extend it section by section, so partial work survives an interruption. Do not commit; the orchestrator commits it to `main`.
- Do not change branches, stage, commit, or run any git command that changes state.
- Bash is for measurement only (`curl -sI` for sizes, `curl ... | gzip -dc | wc -l` for counts, `jq` on small JSON). Stream through pipes. Never save bulk files or any other file to disk.
- Network etiquette: send a descriptive `User-Agent` (for example `CommandLibraryGauntlet/0.1 (research)`) and `Accept` header to Scryfall, stay well under its rate limit, and download bulk data only from the bulk file hosts.
- Every claim carries its source URL and access date (today, from `date -u +%F`). Anything not confirmed by a source is written UNVERIFIED with what was tried. Never infer an endpoint, field, format, price, or capability.
- Terms and licensing get their own findings, quoting the relevant clauses.
- No em dashes anywhere in the file or the return message.

## Acceptance
- RESEARCH.md template sections present: question, findings, options, recommendation, risks, confidence, plus the parity matrix.
- Every Scope item is answered or marked UNVERIFIED with what was tried.
- Every finding has a source URL and access date.
- Zero em dashes.

## Output
- `docs/gauntlet/research/R-06-archidekt.md`
- Handoff: none. A researcher writes only its research file (role brief). The orchestrator tracks status in this card and commits the file. A crash before commit loses only this card's run, which restarts from scratch.
