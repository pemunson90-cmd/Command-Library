# cc-research-07: R-07 Life counter inventory

Path: `coordination/tasks/cc-research-07.md`. Written by the orchestrator. This card plus the files under Brief are everything the agent receives (GAUNTLET.md 5.7).

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
What do LifeElk, Lifetap, and the two other top-rated Magic life counters offer today, and which features should Command Library build, adapt, or skip?

## Feeds
GAUNTLET.md 2.G (life counter parity matrix).

## Scope (must cover)
- LifeElk (CardElk d.o.o.) and Lifetap: store listings (Google Play, and Microsoft Store or other Windows sources if any), version, last update, rating, rating count, installs, developer site, and the full feature list. Confirm or correct the GAUNTLET.md section 10 LifeElk feature list.
- Identify the two other top-rated Magic life counters with evidence: Play Store rating, rating count, install band, recency. Show the candidates considered and why the two chosen win.
- Inventory each app against the GAUNTLET.md 2.G baseline: 1 to 6 players; seat-oriented layouts; tap plus or minus 1; hold to auto-repeat; configurable step; starting life presets and custom; commander damage per opposing commander with partners separate; optional auto life deduction; poison, energy, experience, rad, commander tax, storm, user-defined counters, each hideable; Monarch, Initiative, Day/Night, City's Blessing; dice of any size and count; coin flip; random first player and seat randomizer; turn counter, turn timer, game timer; per-player backgrounds including card art; game-over detection; undo; autosave and resume; game log; keep screen awake; deck linked to a seat for win and loss stats; foldable tabletop posture; offline operation.
- Add every other feature found in these four apps. Mark paid features.
- Where the store listing and the developer site disagree, record both with sources.

## Parity matrix (required section, after Findings)
A table with one row per feature: feature, how each app does it (with source), recommendation (build, adapt, or skip), and reason. Every GAUNTLET.md item named in Scope gets a row even if the reference app lacks it. The orchestrator copies this table into RESEARCH_SUMMARY.md for Pat's ruling.

## Brief (files to read, nothing else)
- `coordination/agents/researcher.md` (role brief, source of truth)
- this card
- `coordination/templates/RESEARCH.md`
- `docs/gauntlet/GAUNTLET.md`: sections 2, 3, 10 and Appendix A, for context only

## Working rules
- Write only `docs/gauntlet/research/R-07-life-counters.md`. Create it early and extend it section by section, so partial work survives an interruption. Do not commit; the orchestrator commits it to `main`.
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
- `docs/gauntlet/research/R-07-life-counters.md`
- Handoff: none. A researcher writes only its research file (role brief). The orchestrator tracks status in this card and commits the file. A crash before commit loses only this card's run, which restarts from scratch.

Codex cx-research-07 filed 2026-10-11. Current evidence and bounded verification gaps documented.
