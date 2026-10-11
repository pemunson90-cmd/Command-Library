# cc-research-16: R-16 Deck and collection text and CSV formats

Path: `coordination/tasks/cc-research-16.md`. Written by the orchestrator. This card plus the files under Brief are everything the agent receives (GAUNTLET.md 5.7).

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
Which deck and collection text and CSV formats are in common use, and what is the exact grammar of each?

## Feeds
GAUNTLET.md 2.B (import from pasted text in every common format, CSV; export), 2.C (bulk CSV collection import), 2.I.

## Scope (must cover)
- Deck text formats: MTG Arena export and import, MTGO .txt and .dek, Moxfield text export variants, Archidekt text export, MTGGoldfish, TappedOut, Deckstats, Cockatrice .cod, Forge .dck, XMage .dck, plain "N Card Name". Add any other format with real use and say why.
- For each: section headers (Commander, Companion, Deck, Sideboard, Maybeboard), quantity syntax, set code and collector number syntax, foil and etched markers, category or tag syntax, comments, blank-line rules, how multi-face names are written (front face only, "A // B"), encoding. Give one sample per format copied from an official doc or a real export, with its source.
- Collection CSV formats: Moxfield, Archidekt, Deckbox, ManaBox, Dragon Shield, Delver Lens, TCGplayer, Helvault, Echo MTG, others found. For each: header row exactly, column meanings, condition and language codes, foil encoding, how set and printing are identified (set code, collector number, Scryfall ID).
- Name matching hazards: split and adventure names, Alchemy "A-" prefixes, accented names, Universes Beyond and Secret Lair name variants, set code differences between Arena and Scryfall. Give sourced examples.
- Recommend which formats the app imports and exports, and the detection rules for auto-detecting a pasted format.

## Brief (files to read, nothing else)
- `coordination/agents/researcher.md` (role brief, source of truth)
- this card
- `coordination/templates/RESEARCH.md`
- `docs/gauntlet/GAUNTLET.md`: sections 2, 3, 10 and Appendix A, for context only

## Working rules
- Write only `docs/gauntlet/research/R-16-deck-formats.md`. Create it early and extend it section by section, so partial work survives an interruption. Do not commit; the orchestrator commits it to `main`.
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
- `docs/gauntlet/research/R-16-deck-formats.md`
- Handoff: none. A researcher writes only its research file (role brief). The orchestrator tracks status in this card and commits the file. A crash before commit loses only this card's run, which restarts from scratch.
