# cc-research-08: R-08 Price data

Path: `coordination/tasks/cc-research-08.md`. Written by the orchestrator. This card plus the files under Brief are everything the agent receives (GAUNTLET.md 5.7).

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
Which card price sources and currencies can Command Library use, how fresh are they, and what do their terms allow?

## Feeds
D-04 (default currency and price source). GAUNTLET.md 2.C (the (n) rule), 2.D (budget).

## Scope (must cover)
- Scryfall prices: every price field (for example usd, usd_foil, usd_etched, eur, eur_foil, tix), what vendor or index each comes from, refresh cadence, and any staleness or usage guidance in Scryfall's docs or terms. Quote the clauses.
- MTGJSON price files (AllPrices, AllPricesToday): providers, retail versus buylist, finishes, currencies, history window, measured file sizes via `curl -sI`, build cadence, license.
- Direct vendor APIs: TCGplayer, Cardmarket, Card Kingdom, Mana Pool, CardTrader, Cardhoarder, others found. For each: is access open to a personal app today, auth model, terms, and the source for that status.
- Currencies available natively per source. For any other currency: FX sources (for example the ECB reference rates) with terms and update cadence.
- How each source identifies a printing and finish, and how it maps to Scryfall IDs (feeds per-printing, per-finish (n) pricing).
- Recommendation for D-04: default currency and default price source, with the alternatives the settings screen should offer, and the one-line tradeoff Pat rules on.

## Brief (files to read, nothing else)
- `coordination/agents/researcher.md` (role brief, source of truth)
- this card
- `coordination/templates/RESEARCH.md`
- `docs/gauntlet/GAUNTLET.md`: sections 2, 3, 10 and Appendix A, for context only

## Working rules
- Write only `docs/gauntlet/research/R-08-price-data.md`. Create it early and extend it section by section, so partial work survives an interruption. Do not commit; the orchestrator commits it to `main`.
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
- `docs/gauntlet/research/R-08-price-data.md`
- Handoff: none. A researcher writes only its research file (role brief). The orchestrator tracks status in this card and commits the file. A crash before commit loses only this card's run, which restarts from scratch.

Codex cx-research-08 filed 2026-10-11. Current evidence and bounded verification gaps documented.
