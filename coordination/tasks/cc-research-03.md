# cc-research-03: R-03 Comprehensive Rules

Path: `coordination/tasks/cc-research-03.md`. Written by the orchestrator. This card plus the files under Brief are everything the agent receives (GAUNTLET.md 5.7).

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
Where do the official Magic Comprehensive Rules come from, in what formats, how often do they change, may an app store and display them, and how should they be parsed into searchable, cross-linked entries?

## Feeds
GAUNTLET.md 2.A (rules text, glossary, cross-links, keyword links, rulings attached to cards).

## Scope (must cover)
- Official source page and file URLs for every format offered (TXT, PDF, DOCX, others). Current version, effective date, and file sizes measured via `curl -sI`.
- Encoding and line-ending facts of the TXT file as served today (BOM, charset, CRLF), measured.
- Update cadence over the last two years (list versions and dates with sources), and how a client detects a new version (URL pattern, page structure, any feed).
- Terms: may an app bundle, store, and display the full CR text? Quote the Wizards of the Coast Fan Content Policy and any notice on the rules page. Note what applies to personal use versus public release.
- Structure: sections, rule numbering grammar (for example 100., 100.1, 100.1a), examples, glossary format, credits. Count rules and glossary entries in the current TXT by piping it through grep, and show the commands.
- Cross-reference patterns inside rule text (for example "see rule 702.19") with a parsing approach that turns them into links.
- Keyword linking: how keyword abilities (702.x) and keyword actions (701.x) map to Scryfall `keywords` values and the Scryfall catalogs (`/catalog/keyword-abilities`, `/catalog/keyword-actions`, `/catalog/ability-words`). Report mismatches.
- Card rulings: the Scryfall rulings source and fields, and how they attach to cards (oracle_id).
- Related official documents and whether the app should hold them: Magic Tournament Rules, Commander format rules page. Sources and formats.
- Secondary hyperlinked CR sites, marked as secondary.

## Brief (files to read, nothing else)
- `coordination/agents/researcher.md` (role brief, source of truth)
- this card
- `coordination/templates/RESEARCH.md`
- `docs/gauntlet/GAUNTLET.md`: sections 2, 3, 10 and Appendix A, for context only

## Working rules
- Write only `docs/gauntlet/research/R-03-comprehensive-rules.md`. Create it early and extend it section by section, so partial work survives an interruption. Do not commit; the orchestrator commits it to `main`.
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
- `docs/gauntlet/research/R-03-comprehensive-rules.md`
- Handoff: none. A researcher writes only its research file (role brief). The orchestrator tracks status in this card and commits the file. A crash before commit loses only this card's run, which restarts from scratch.
