# cc-research-17: R-17 Card link URL patterns

Path: `coordination/tasks/cc-research-17.md`. Written by the orchestrator. This card plus the files under Brief are everything the agent receives (GAUNTLET.md 5.7).

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
What are the verified URL patterns for each card link target, and which tokens must a user URL template support?

## Feeds
GAUNTLET.md 2.F (per-card links, user URL templates with tokens).

## Scope (must cover)
- Targets: Scryfall, Gatherer, EDHREC, Commander Spellbook, TCGplayer, Cardmarket, Card Kingdom. Add any other widely used target and say why.
- For each target: the card page URL pattern and a search URL fallback; which identifier each needs (set and collector number, Scryfall ID, oracle ID, multiverse ID, tcgplayer_id, cardmarket_id, a name slug); and the slug rules where a slug is used (case, punctuation, apostrophes, commas, double-faced and split names). Verify each pattern by requesting at least three real cards, including one double-faced card and one split card, and record the HTTP status and final URL after redirects (`curl -sIL -o /dev/null -w '%{http_code} %{url_effective}'`). Respect each site's robots and rate limits; a handful of requests only.
- Scryfall card objects' `related_uris` and `purchase_uris`: which links they already provide offline, and any affiliate parameters in them. Quote Scryfall's terms on using these links.
- Each target's terms on deep linking, if stated.
- The token list for user templates (for example `{name}`, `{name_slug}`, `{set}`, `{collector_number}`, `{scryfall_id}`, `{oracle_id}`, `{multiverse_id}`, `{tcgplayer_id}`, `{cardmarket_id}`, `{front_face_name}`, `{lang}`) with the encoding rule for each (URL-encode, slugify, raw).

## Brief (files to read, nothing else)
- `coordination/agents/researcher.md` (role brief, source of truth)
- this card
- `coordination/templates/RESEARCH.md`
- `docs/gauntlet/GAUNTLET.md`: sections 2, 3, 10 and Appendix A, for context only

## Working rules
- Write only `docs/gauntlet/research/R-17-card-links.md`. Create it early and extend it section by section, so partial work survives an interruption. Do not commit; the orchestrator commits it to `main`.
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
- `docs/gauntlet/research/R-17-card-links.md`
- Handoff: none. A researcher writes only its research file (role brief). The orchestrator tracks status in this card and commits the file. A crash before commit loses only this card's run, which restarts from scratch.
