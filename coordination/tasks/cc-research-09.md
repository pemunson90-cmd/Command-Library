# cc-research-09: R-09 Recommendation data

Path: `coordination/tasks/cc-research-09.md`. Written by the orchestrator. This card plus the files under Brief are everything the agent receives (GAUNTLET.md 5.7).

| Field | Value |
|---|---|
| Role | researcher |
| Component | research (Gate 1) |
| Orchestrator | claude-code |
| Model tier | standard (Sonnet, D-10) |
| Branch | none. Researchers write one file in the main checkout; the orchestrator commits it to `main` |
| Base commit | b1c922eaa96c25209691253c9849066b89a71846 |
| Steps | 1/1: write the research file |
| Status | filed |

## Question
What data supports offline, budget-aware deck recommendations and automatic role tagging, and which online enrichers can the app use under their terms?

## Feeds
GAUNTLET.md 2.D (budget-aware recommendations, role tagging), 2.H (AI grounding).

## Scope (must cover)
- Offline signals available in local card data (cross-check R-02): Oracle text, type line, keywords, color identity, mana value, produced_mana, edhrec_rank, penny_rank, MTGJSON fields such as EDHREC saltiness or leadership skills. Confirm each field exists and where.
- Role tagging from Oracle text (ramp, card draw, removal, board wipes, protection, lands, win conditions, tutors, recursion, others worth having): existing rule sets or open datasets (Scryfall oracle tags via `otag:` search, open-source classifiers), their license, and whether any is available as bulk data or only through online search. Show three sample regex or rule approaches from sources, marked as examples.
- Synergy heuristics used by existing tools, from their own documentation.
- Online enrichers: EDHREC (any public API or data endpoint, terms), Commander Spellbook (combos; cross-reference R-04), Scryfall search API (tag queries, rate limits), others found. For each: endpoints, data, terms quoted, whether caching for offline use is allowed.
- Recommend which signals form the offline engine and which online enrichers are allowed, each with the terms clause that permits it.

## Brief (files to read, nothing else)
- `coordination/agents/researcher.md` (role brief, source of truth)
- this card
- `coordination/templates/RESEARCH.md`
- `docs/gauntlet/GAUNTLET.md`: sections 2, 3, 10 and Appendix A, for context only

## Working rules
- Write only `docs/gauntlet/research/R-09-recommendation-data.md`. Create it early and extend it section by section, so partial work survives an interruption. Do not commit; the orchestrator commits it to `main`.
- Do not change branches, stage, commit, or run any git command that changes state.
- Bash is for measurement only (`curl -sI` for sizes, `curl ... | gzip -dc | wc -l` for counts, `jq` on small JSON). Stream through pipes. Never save bulk files or any other file to disk.
- Network etiquette: send a descriptive `User-Agent` (for example `CommandLibraryGauntlet/0.1 (research)`) and `Accept` header to Scryfall, stay well under its rate limit, and download bulk data only from the bulk file hosts.
- Every claim carries its source URL and access date (today, from `date -u +%F`). Anything not confirmed by a source is written UNVERIFIED with what was tried. Never infer an endpoint, field, format, price, or capability.
- Terms and licensing get their own findings, quoting the relevant clauses.
- No em dashes anywhere in the file or the return message.

## Acceptance
- RESEARCH.md template sections present: question, findings, options, recommendation, risks, confidence.
- Every Scope item is answered or marked UNVERIFIED with what was tried.
- Every finding has a source URL and access date.
- Zero em dashes.

## Output
- `docs/gauntlet/research/R-09-recommendation-data.md`
- Handoff: none. A researcher writes only its research file (role brief). The orchestrator tracks status in this card and commits the file. A crash before commit loses only this card's run, which restarts from scratch.

Codex cx-research-09 filed 2026-10-11. Scored/source evidence verified, runtime/permission unknowns explicit.
