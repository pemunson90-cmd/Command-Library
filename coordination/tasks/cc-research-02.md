# cc-research-02: R-02 Card data sources

Path: `coordination/tasks/cc-research-02.md`. Written by the orchestrator. This card plus the files under Brief are everything the agent receives (GAUNTLET.md 5.7).

| Field | Value |
|---|---|
| Role | researcher |
| Component | research (Gate 1) |
| Orchestrator | claude-code |
| Model tier | standard (Sonnet, D-10) |
| Branch | none. Researchers write one file in the main checkout; the orchestrator commits it to `main` |
| Base commit | b1c922eaa96c25209691253c9849066b89a71846 |
| Steps | 1/1: write the research file |
| Status | active |

## Question
Which card data sources should Command Library use for Full and Lean storage modes, what do they contain, how big are they today, how often do they change, and what do their terms allow?

## Feeds
GAUNTLET.md 2.A (database, Full and Lean modes, image packs, refresh). R-14 scale figures. R-08 and R-09 field availability.

## Scope (must cover)
- Scryfall bulk data: list every type from `https://api.scryfall.com/bulk-data`. For each, record `size`, `compressed_size`, `updated_at`, and the download URIs as reported today. Measure card counts for `oracle_cards` and `default_cards` by streaming the JSONL download through `gzip -dc | wc -l`. Do not download `all_cards`; report its metadata only.
- Scryfall update cadence for bulk files, and how a client detects a new version.
- Field inventory against GAUNTLET.md 2.A: oracle_id, name, mana_cost, cmc, colors, color_identity, type_line, oracle_text, power, toughness, loyalty, defense, keywords, card_faces, layout (list every layout value), legalities (list every format key), reserved, game changer flag (confirm the exact field name and that it is present in bulk files), rulings (separate bulk file and its fields), printings (set, collector_number, rarity, finishes, prices, image_uris, multiverse_ids, tcgplayer_id, cardmarket_id), edhrec_rank, penny_rank, produced_mana, all_parts (tokens and meld parts), related_uris, purchase_uris. Mark any field absent from bulk files.
- How multi-face layouts are represented (MDFC, transform, split, adventure, flip, meld, battle, others) and which fields sit on the face versus the card.
- Filtering: tokens, art series, digital-only, funny/acorn, oversized, memorabilia, non-English. What a "playable card" filter needs.
- Images: image_uris sizes (small, normal, large, png, art_crop, border_crop). Measure byte size of a sample of at least 20 cards per size via `curl -sI`. Estimate pack sizes for "deck cards" (100), "collection" (1,000 and 5,000), and "all cards" (one printing per oracle card, and every printing) per size. State the image host's rate-limit and caching guidance.
- Lean mode on-demand fetch: `/cards/collection` (identifier types, max per request), `/cards/named`, `/cards/:id`, rate limits, required headers. Re-verify GAUNTLET.md section 10 Scryfall facts against current docs and quote them.
- Scryfall terms: quote clauses on data use, attribution, paywalling, image use, caching, and bulk redistribution inside an app.
- MTGJSON: list relevant files (AllPrintings, AtomicCards, AllIdentifiers, Keywords, CardTypes, AllPrices), formats (JSON, SQLite, CSV, others), measured sizes via `curl -sI`, build cadence, fields Scryfall lacks or holds differently, identifier cross-mapping to Scryfall IDs, license text.
- Recommend primary and secondary sources for Full mode and for Lean mode, and how an atomic refresh can detect and apply changes (metadata check, download, apply).

## Brief (files to read, nothing else)
- `coordination/agents/researcher.md` (role brief, source of truth)
- this card
- `coordination/templates/RESEARCH.md`
- `docs/gauntlet/GAUNTLET.md`: sections 2, 3, 10 and Appendix A, for context only

## Working rules
- Write only `docs/gauntlet/research/R-02-card-data.md`. Create it early and extend it section by section, so partial work survives an interruption. Do not commit; the orchestrator commits it to `main`.
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
- `docs/gauntlet/research/R-02-card-data.md`
- Handoff: none. A researcher writes only its research file (role brief). The orchestrator tracks status in this card and commits the file. A crash before commit loses only this card's run, which restarts from scratch.
