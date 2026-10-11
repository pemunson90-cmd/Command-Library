# cc-research-04: R-04 Commander Brackets, Game Changers, combo data

Path: `coordination/tasks/cc-research-04.md`. Written by the orchestrator. This card plus the files under Brief are everything the agent receives (GAUNTLET.md 5.7).

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
What are the current official Commander Brackets rules, where does the Game Changers list come from in machine-readable form, and which combo database can the app cache offline under its terms?

## Feeds
GAUNTLET.md 2.E (bracket identifier), 2.D (bracket impact on recommendations).

## Scope (must cover)
- Fetch the current official Commander Brackets rules from Wizards of the Coast primary sources. Record every bracket (1 to 5) and every criterion verbatim, with the publication date. List every revision since launch with dates and what changed, including the Oct 21, 2025 revision and the Feb 9, 2026 Game Changers update named in GAUNTLET.md section 10. Confirm or correct those section 10 statements.
- For each criterion: automatable from card data, partly automatable, or intent-based (cannot be automated). Say which data each automatable criterion needs.
- Game Changers: the official list source, the current count, and machine-readable sources (Scryfall `is:gamechanger` search, the card-object flag, MTGJSON field). Verify they agree today and show how.
- Data sources for the other criteria: mass land denial, extra turns, early two-card combos, tutors (status after the 2025 revision). Is any official or community list maintained? Scryfall oracle tags or searches that approximate them, and their terms.
- Combo database: Commander Spellbook (API base URL, endpoints, bulk or export option, data size, fields, update cadence, license and terms, any bracket estimate endpoint and its method). Compare with any other combo source found, with terms.
- Existing bracket estimators (Commander Spellbook, Moxfield, Archidekt, EDHREC, others): method and output, for reference only.
- Recommend how rules, the Game Changers list, and combo data become refreshable, versioned data with an "as of" date.

## Brief (files to read, nothing else)
- `coordination/agents/researcher.md` (role brief, source of truth)
- this card
- `coordination/templates/RESEARCH.md`
- `docs/gauntlet/GAUNTLET.md`: sections 2, 3, 10 and Appendix A, for context only

## Working rules
- Write only `docs/gauntlet/research/R-04-brackets-combos.md`. Create it early and extend it section by section, so partial work survives an interruption. Do not commit; the orchestrator commits it to `main`.
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
- `docs/gauntlet/research/R-04-brackets-combos.md`
- Handoff: none. A researcher writes only its research file (role brief). The orchestrator tracks status in this card and commits the file. A crash before commit loses only this card's run, which restarts from scratch.

Codex cx-research-04 filed 2026-10-11; documented primary evidence and explicit unresolved measurements/terms.
