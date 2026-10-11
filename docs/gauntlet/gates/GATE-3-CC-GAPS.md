# Gate 3 planning gap check (Claude Code)

- Date: 2026-10-11
- Branch: `agent/cc-plangaps-01`, base `main` 3799edb (after Codex released the lease at the end of G3-A)
- Scope: planning documents only. No app code exists. No build, test, device, or source-currentness claim is made.
- Method: a read-only check (Haiku) audited `main` at c1a2733 and reported 22 gaps, G-01 to G-22. Claude Code spot-checked seven of its claims, then rechecked every gap against 3799edb, which added the 20 ARCHITECT files, `registry.json`, `coverage.json`, `GATE-3.md`, `GATE-3-REVIEW.md`, and `scripts/check_continuity.py`.
- Authority for the fills on this branch: Pat, 2026-10-11, verbatim: "Approve all".

## Status at base 3799edb

| ID | Gap (short) | Status | Evidence or action |
|---|---|---|---|
| G-01 | search, catalog, inference lack network/consent edges | Resolved by design | `search` ARCHITECT: online search delegates to `CatalogRepository.remoteSearch`, caller supplies consent context; GATE-3-REVIEW marks the online-search transport finding resolved (paraphrase) |
| G-02 | consumers of deck contents lack a `decks` edge | Resolved by design | Data crosses components as immutable core snapshots composed in `shell`, for example `pricing.calculate(DeckSnapshot, InventorySnapshot, QuoteSnapshot, PricePolicy)` |
| G-03 | `transfer` cannot reach life/settings/ai data | Resolved by design | DATA_MODEL.md "Backup and retention": the archive holds user tables, game state, conversations and settings, which `transfer` reaches through its `storage` dependency; Profile and GameExchange types cover game exchange |
| G-04 | Mermaid projection omits `settings`, `links` and some edges | Open, cosmetic | `COMPONENTS.md` names the table authoritative and the diagram a projection; `check_continuity.py` validates the registry, which agrees. Recommend regenerating the diagram from `registry.json` at the next COMPONENTS edit |
| G-05 | linked-deck stats (G09) at M2 before decks at M3; (n) before pricing | Acknowledged, not resolved in COVERAGE | COVERAGE still maps G08 (art/deck profiles) and G09 (stats) to M2. GATE-3-REVIEW: "Required deck references/art and future analysis remain obligations ... Concrete stage cards must retain this foundation distinction." Recommend the M2 life stage cards split the deck-linked parts of AC-G08 and AC-G09 into M3 follow-ups |
| G-06 | MILESTONES one line per milestone | Resolved | MILESTONES rewritten in f58f3d4 |
| G-07 | no ARCHITECT files | Resolved | 20 files under `docs/architecture/` |
| G-08 | no CI workflow design | Partly resolved | GATE-3-REVIEW covers the CI plan; `.github/workflows/continuity.yml` exists for documents. App CI is queued as `orch-toolchain-01` |
| G-09 | D-08 max concurrent implementers unset; no parallel execution plan | Ruled; plan open | D-08 ruled 3 (Pat, 2026-10-11) with operating rules in DECISIONS. The per-milestone parallel execution plan is still unwritten; derive it from `registry.json` dependencies and QUEUE prerequisites at `orch-m0-01` |
| G-10 | IMPOSSIBLE.md empty | This branch, step 3 | Screened against CI capability; rejected candidates listed |
| G-11 | RISKS.md empty | This branch, step 2 | Rows sourced from DECISIONS, reviews, research, DESIGN_SYSTEM |
| G-12 | 3 deferred parity rows missing from POST_RELEASE | This branch, step 4 | PX06-063, PX07-078, PX07-079 |
| G-13 | life counter elimination, seat reorder, history filter have no SPEC acceptance | This branch, step 6 | Acceptance text extended in existing rows; row counts unchanged (64 and 213) |
| G-14 | 26 build/adapt parity rows reference only the B07 or G11 family | Residual, non-blocking | GATE-3-REVIEW confirms every parity row resolves through family joins to contracts. Feature-specific acceptance for these rows belongs in each worker task card |
| G-15 | interface language undecided | Ruled; this branch, steps 6 and 7 | D-18 records Pat's ruling; SPEC and PX07-071 updated |
| G-16 | evidence register table stops at E12 | This branch, step 6 | E13 row added to the COVERAGE register |
| G-17 | mana-symbol licensing has no risk row | This branch, step 2 | RISKS.md row |
| G-18 | Surprise mode content undefined | This branch, step 6 | H01 acceptance names the four GAUNTLET 2.H angles |
| G-19 | no fixture policy for committed real-data fixtures | Queued | `orch-source-fixtures-01` in QUEUE.md; ARCHITECT test sections require authentic fixtures |
| G-20 | ledger structure: "Open" section lists D-04, D-06, D-07 that the index marks ruled; a blank line splits the index table before D-17 | Partly, this branch, step 7 | Index table rejoined (needed for the D-18 row). Recommend the next orchestrator move the three stale "Open" entries under "Ruled" |
| G-21 | Claude agent definitions predate D-17 | This branch, step 5 | Definitions and neutral briefs now carry a D-17 section that points to CONTINUE.md; D-10 model tiers kept (Pat's ruling outranks the CONTINUE.md note on provider names) |
| G-22 | `scripts/check_continuity.py` missing | Resolved | Built by Codex in f58f3d4; PASS, and its 5 tests pass |

## Correction to the first check

The Haiku check read `COMPONENTS.md` line "Deck/collection identities are core snapshots" as covering identities only, and reported G-01 to G-03 as missing edges. At 3799edb the ARCHITECT interfaces show the snapshot pattern carries full contents (for example `DeckSnapshot`, `InventorySnapshot`, `QuoteSnapshot`), and `shell`, the composition root, depends directly or transitively on every component. No dependency edge is missing for those flows.

## Still open

- The per-milestone parallel execution plan (GAUNTLET section 6, Gate 3), now bounded by D-08.

## Plan approved by Pat (verbatim as sent, 2026-10-11)

Pat's reply, verbatim: "Approve all". It approved the following plan and recommendations, which Claude Code sent as written below.

| # | Gap | Work | Who |
|---|---|---|---|
| 1 | All 22 | Gap report: `docs/gauntlet/gates/GATE-2-CC-GAPS.md` | me |
| 2 | G-11, G-17 | Fill RISKS.md (keystore loss, unsigned Windows installer, Narrator, scanner blind spots, corpus bundling, mana-symbol licensing) | Sonnet |
| 3 | G-10 | Screen IMPOSSIBLE.md: only what phone plus GitHub truly can't do, each entry with your manual step | Sonnet |
| 4 | G-12 | Add 3 deferred parity rows to POST_RELEASE.md | me |
| 5 | G-22 | Write `scripts/check_continuity.py`, which CONTINUE.md names but nobody wrote | Sonnet |
| 6 | G-21 | Finalize Claude agent definitions for Codex's new D-17 lease protocol | me |
| | | One review of the whole branch before merge | Opus (D-10) |

Needs your call:
1. Approve items 1 to 6? I recommend yes.
2. Tell Codex about G-01 to G-05 now? Once item 1 exists, you'd send Codex one line: "Before writing ARCHITECT files, read `docs/gauntlet/gates/GATE-2-CC-GAPS.md` on branch `agent/cc-plangaps-01` and fix G-01 to G-05." I recommend yes, because merging later is too late for this one.
3. SPEC additions (G-13, G-18): life counter seat elimination and restore, seat reordering, history filters, and what "Surprise me" actually does. I recommend doing these after Codex finishes, since Codex may edit SPEC.md in its corrections step.
4. App language (G-15): nobody has ruled on it. I recommend an English-only interface, built so translations can be added later, with no translations now.

Changes after approval, recorded here: Codex finished G3-A before the work began, so the report was renamed `GATE-3-CC-GAPS.md`; item 5 was dropped because Codex built the script (G-22); question 2 was dropped because G-01 to G-05 were resolved or acknowledged at 3799edb; question 3 work ran after Codex released the lease.
