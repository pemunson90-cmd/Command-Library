# COMMAND LIBRARY: BUILD GAUNTLET
## Claude Code edition

You are Claude Code, running as orchestrator for this project. Read this entire document before taking any action.

- If `/docs/gauntlet/STATE.md` exists in the repo, run the resume protocol (5.6) and continue from where it points.
- Otherwise start at Gate 0.
- Section 11 holds the Claude Code specifics. Everything else is shared with the Codex edition, word for word, so both orchestrators run one protocol.

---

## 0. MISSION

You are the lead architect and orchestrator for **Command Library**, a Magic: The Gathering companion app for Android (standard phones, foldables, tablets) and Windows desktop.

You run this project in gated passes: research, spec, architecture, then milestone-by-milestone build. You STOP at every gate and wait for Pat's ruling. Pat is the product owner. His rulings override this document and are recorded verbatim in `DECISIONS.md`.

Definition of finished: fully functional, zero placeholders, efficient and scalable code, pristine UI on every supported screen class. The only permitted gaps are things that are impossible to complete from a phone plus GitHub workflow. Each one gets an `IMPOSSIBLE.md` entry stating the reason and the exact manual step Pat takes to close it.

Appendix A holds Pat's original request and rulings verbatim. When this document and Appendix A disagree, Appendix A wins and you log the conflict in `DECISIONS.md`.

---

## 1. OPERATING ENVIRONMENT

- Pat directs from an Android phone. All builds run on GitHub Actions. Pat sideloads Android builds and runs Windows builds on his PC. Nothing may require a local dev machine to build, test, or package.
- The GitHub repo is the source of record. Every artifact this gauntlet produces lives in the repo, committed and pushed.
- Two orchestrators exist: Claude Code and OpenAI Codex, each running its own edition of this gauntlet. They may work this repo at different times or concurrently. The coordination protocol in section 5 is shared and tool-neutral, and both obey it. Never rewrite, rebase, force-push, or delete another orchestrator's commits, branches, or locks.
- Sessions will die: context rot, crashes, usage limits, compaction. The protocol assumes this. Any fresh session must be able to resume from the files alone with zero loss beyond the current step.

---

## 2. PRODUCT BRIEF

Personal use first. Public release is a post-release, low-priority item (section 8). It is never a blocker and never a reason to cut or delay a feature.

### A. Card database and Oracle (offline-first)

- Full local database of every card: Oracle name, mana cost, mana value, colors, color identity, type line, Oracle text, power/toughness/loyalty/defense, keywords, all multi-face layouts (MDFC, transform, split, adventure, flip, meld, etc.), legalities for every format, reserved list flag, Game Changer flag, rulings, and printings (set, collector number, rarity, finishes, prices, image URIs).
- Full Comprehensive Rules text stored locally, searchable, with rule-number cross-links and the glossary. Card rulings attach to cards. Keywords in Oracle text link to their rules entries.
- Storage modes (user setting, switchable without data loss, resumable):
  - **Full** (default): entire card database + rules + rulings.
  - **Lean**: Oracle data only for cards in the user's decks and collection, plus rules. Cards added later are fetched and stored on demand.
- Card images: online-only by default, cached on view with a size-capped cache. Opt-in offline image packs: deck cards, collection cards, all cards. Show the download size estimate before any pack download. Downloads are resumable with a Wi-Fi-only toggle.
- Data refresh: check source metadata, download, then apply atomically. The DB is never left half-updated. Manual "update now". "Data as of <date>" visible in settings.
- Search: offline query engine implementing Scryfall-style syntax for every field held locally (name, Oracle text, type, color, identity, mana value, power, toughness, format legality, rarity, set, `is:gamechanger`, owned, in-deck, price ranges, plus anything else cheap to support). Full-text search over Oracle text and rules. Autocomplete. Syntax the offline engine cannot answer passes through to the online Scryfall search, clearly labeled as online.
- Data source attribution shown in About.

### B. Deck builder (Moxfield / Archidekt parity, built natively)

- Gate 1 produces a feature parity matrix for Moxfield and Archidekt. Every row gets a recommended ruling: build, adapt, or skip with reason. Pat rules at the gate. Build natively as far as possible unless research finds a smarter solution, which you present as an option.
- The matrix must at least cover:
  - Boards: commander(s) including partners, backgrounds, companions; mainboard; sideboard; maybeboard/considering. Custom categories and tags with drag-and-drop. Per-card printing and finish selection. Quantities. Per-card notes. Deck description/primer in Markdown.
  - Analysis: mana curve, color pips vs land sources, type breakdown, average mana value, land count guidance, draw probability (hypergeometric calculator), opening hand simulator and goldfish playtester with mulligan rules, token list derived from card data.
  - Validation: per-format legality, deck size, singleton, color identity, banned/restricted, companion conditions where determinable. Live warnings.
  - Organization: deck versions with diff between versions, duplicate deck, folders, tags, search and sort across decks.
  - Import: pasted text in every common format (research R-16), CSV, and URL import from Moxfield/Archidekt only if Gate 1 confirms a terms-compliant way. If not, a guided export-then-paste flow.
  - Export: plain text in each common format, CSV.
- Every card row shows its (n) value (module C).

### C. Collection and the (n) rule

- Owned quantity per printing and finish. Quick add from search, from a deck ("mark all owned"), and bulk CSV import.
- **(n) rule:** everywhere a card appears (deck lists, recommendations, search results, AI suggestions), its price shows inline as `(n)`, where n is the effective cost to acquire it in that context. Owned copies show `(0.00)`. Partially owned (deck needs 4, user owns 2) shows the cost of the missing copies only. Market price is one tap or hover away. Deck header shows total value, owned value, and cost to complete.
- Price source and currency are selectable from whatever the data sources actually expose (verify at Gate 1).

### D. Budget-aware recommendations

- Budget settings: per-card max, per-upgrade-pass total, deck target total, "owned cards count as free" toggle, preferred price source.
- Recommendation engine works offline from local data: synergy heuristics from Oracle text, types, keywords, color identity, curve gaps, and role counts (ramp, draw, removal, sweepers, protection, lands, win conditions). Online enrichers are used only where Gate 1 confirms the terms allow it.
- Automatic role tagging from Oracle text, with user override.
- Every recommendation shows why, its (n), its legality, and its bracket impact.

### E. Bracket identifier (Commander first-class, all formats supported)

- Implements the current official Commander Brackets rules as fetched at Gate 1. Rules and the Game Changers list are data, never hardcoded: loaded from refreshable sources, versioned, with "rules as of <date>" shown.
- Output: estimated bracket; evidence per criterion (which cards, which combos); confidence; which criteria are intent-based and cannot be fully automated (the UI says so plainly); and "to reach bracket X, change these cards", respecting budget and owned cards.
- Combo detection from a combo database verified at Gate 1, cached for offline use.
- Non-Commander formats: full legality and format-specific validation in place of brackets.

### F. Card links

- Per card, a configurable list of external links: Scryfall, Gatherer, EDHREC, Commander Spellbook, TCGplayer, Cardmarket, Card Kingdom, plus user-defined URL templates with tokens (`{name}`, `{set}`, `{collector_number}`, `{scryfall_id}`, etc.). Gate 1 verifies every URL pattern.

### G. Life counter (LifeElk and Lifetap parity, plus)

- Gate 1 inventories LifeElk (CardElk d.o.o.), Lifetap, and the top two other rated life counters into a parity matrix.
- Baseline: 1 to 6 players; seat-oriented table layouts; tap for ±1, hold to auto-repeat, configurable step; starting life presets 20/25/30/40/custom; commander damage per opposing commander with partners tracked separately and optional auto life deduction; counters for poison, energy, experience, rad, commander tax, storm, and user-defined counters, each hideable; Monarch, Initiative, Day/Night, City's Blessing; dice of any size and count; coin flip; random first player and seat randomizer; turn counter, turn timer, game timer; per-player backgrounds including card art from the database; game-over detection; undo; autosave and resume; game log; keep-screen-awake; link a deck to a seat for win/loss stats.
- Foldables: tabletop posture layout. Fully functional offline at all times.
- Shared multi-device game state: post-release backlog.

### H. AI deck lab

- Modes: **Roast my deck** (intensity selector), **Chat about my deck**, **Upgrade my deck** (respects budget, bracket target, owned cards), **Surprise me** (an unexpected angle: hidden synergies, alternate win conditions, theme reads, spicy tech).
- Providers, as many as possible:
  - Direct APIs. Verify the current list at Gate 1; candidates include Anthropic, OpenAI, Google Gemini, Mistral, xAI, DeepSeek, Groq, OpenRouter.
  - Any OpenAI-compatible endpoint, local or LAN: Ollama, LM Studio, llama.cpp server, vLLM, KoboldCpp, LocalAI.
  - On-device models on Android and Windows. Gate 1 picks the runtime(s); candidates include llama.cpp, Google AI Edge / LiteRT-LM, ONNX Runtime GenAI. Model download manager with size warnings and resumable downloads.
  - **Prompt export**: no provider at all. Builds the full prompt with deck context and copies/shares it to any AI app. A paste-back field imports the reply so the app can validate it.
- Model lists are fetched from providers that support listing. A model ID is never only hardcoded; the user can always type one.
- Grounding: the app sends deck data with Oracle text from the local DB. Card names in replies are parsed and validated against the DB (exists, legal in format, within color identity, within budget, owned status) and rendered as tappable cards with (n). Invalid suggestions are visibly flagged.
- Optional tool calling where the provider supports it: the model may query the local card DB (search, get card), bounded by a call limit.
- API keys encrypted at rest with the platform keystore (verify per platform at Gate 1), never logged, never exported in plain text.
- Streaming, cancel, retry, per-deck conversation history, token/cost estimate where the provider exposes pricing (hidden otherwise).
- System prompts are versioned files in the repo and editable in an advanced settings screen.

### I. Import, export, data safety

- Plain text and CSV for decks and collection. Full app backup/restore as a single file (decks, collection, settings; keys excluded by default).
- Sync: post-release backlog.

### J. UI/UX

- `DESIGN_SYSTEM.md` is written at Gate 2, before any UI code: tokens (color, type, spacing, elevation, motion), components, light/dark themes, color-identity accents, card rendering rules, iconography.
- Adaptive by window size class, never by device type: compact (phone), medium (small tablet, folded inner screen, narrow desktop window), expanded (unfolded foldable, tablet, desktop). List-detail and supporting-pane layouts on wide screens. Foldable postures (tabletop, book) with hinge-aware layout: nothing important under the fold. Fold/unfold and window resize lose no state.
- Desktop: keyboard shortcuts, hover states, right-click context menus, drag-and-drop, resizable windows.
- Accessibility: scalable text, screen reader labels (TalkBack, Narrator), contrast, touch target sizes.
- Performance budgets with concrete numbers are proposed at Gate 2: cold start, search latency over the full DB, smooth scrolling over 30,000+ cards, full data import time, memory ceiling.
- "Pristine" is enforced: a UI auditor agent checks every screen against `DESIGN_SYSTEM.md`, and CI produces screenshots per size class that are attached to milestone reports.

### K. Settings, onboarding, about

- First-run flow: storage mode choice, download size estimate, data download with progress and resume.
- Settings for every module above. About screen with data attribution and data dates.

---

## 3. ENGINEERING STANDARDS

- **Stack.** Gate 1 researches and justifies the stack with a scored matrix: one codebase for Android and Windows; foldable and adaptive support; Windows packaging from GitHub Actions; SQLite and full-text search performance; on-device LLM interop; build reliability; ecosystem maturity. **Fallback** is Pat's known working setup: Kotlin, Jetpack Compose, Room, Navigation Compose, DataStore, Gradle, GitHub Actions builds, sideloaded APKs signed with Pat's permanent release key. The natural Windows extension of that fallback is Kotlin Multiplatform with Compose Multiplatform desktop. Departing from the fallback requires a stated reason that wins on the matrix.
- **Architecture.** Modular by component. One-directional dependency DAG, no cycles. Domain logic free of UI and platform code. Platform specifics behind interfaces. Data access through repositories. All background work cancelable.
- **Data.** Local DB with indexes and full-text search designed for the full card set. Bulk imports stream JSON and never load a whole bulk file into memory. Batched transactions. Every schema migration has a test.
- **Network.** One shared HTTP client. Polite to Scryfall: required headers, rate limiting, bulk data preferred. Retries with backoff. Every screen defines its offline behavior in its ARCHITECT.md.
- **Zero placeholders.** CI fails on `TODO`, `FIXME`, `XXX`, `placeholder`, `not implemented`, `NotImplementedError`, Kotlin `TODO()`, empty click handlers, lorem ipsum, and stub returns, unless the exact file and line is listed in `IMPOSSIBLE.md`. Every visible control does something real. Unbuilt features are absent from the UI, never stubbed.
- **Tests.** Unit tests for all domain logic: text and CSV parsers, search syntax, legality, bracket engine, (n) pricing, budget filters, life counter state, AI reply validation. DB tests. UI smoke tests where the stack supports them. Small committed fixtures cut from real data.
- **Security.** Keys encrypted. No secrets in the repo. Signing keys live only in GitHub Actions secrets. Dependencies pinned. Real, exploitable issues are flagged once in `RISKS.md` with specifics.
- **Efficiency.** Performance budgets are acceptance criteria. DB import and search are profiled against a mid-range Android device spec stated at Gate 2.

---

## 4. REPOSITORY LAYOUT (coordination files)

```
/<ENTRY FILE>                      edition-specific (see section 11); short; points to STATE.md
/docs/gauntlet/
  GAUNTLET.md                      this document, copied verbatim at Gate 0
  STATE.md                         resume pointer: gate, milestone, active tasks, last good commit, next action
  DECISIONS.md                     ledger: D-xx open/ruled; Pat's rulings quoted verbatim
  research/R-xx-<topic>.md         one file per topic; every claim has URL + access date
  RESEARCH_SUMMARY.md
  SPEC.md
  DESIGN_SYSTEM.md
  COVERAGE.md                      brief item -> spec section -> component -> milestone -> test
  MILESTONES.md
  IMPOSSIBLE.md
  RISKS.md
  POST_RELEASE.md
  gates/GATE-<n>.md                gate reports
/docs/architecture/<component>/ARCHITECT.md
/coordination/
  agents/<role>.md                 tool-neutral role briefs
  tasks/<task-id>.md               task cards
  locks/<component>.lock           one file per lock (avoids merge conflicts)
  handoffs/<task-id>.md            per-task progress and next step
  JOURNAL.md                       append-only, one line per checkpoint/merge/gate
```

Task IDs: `<orchestrator>-<component>-<nn>`, e.g. `cc-carddb-03` (Claude Code) or `cx-lifecounter-01` (Codex).

---

## 5. COORDINATION PROTOCOL

**5.1 ARCHITECT.md before code.** No code is written in a component until its `ARCHITECT.md` exists, is merged to main, and is listed in `STATE.md`. Required sections: purpose; COVERAGE IDs served; public interface (signatures); data model; allowed dependencies; owned paths (globs, exclusive); shared-file change requests; error and offline behavior; performance budget; tests and acceptance criteria; numbered step plan, each step small enough for one subagent to finish, test, and commit in one run.

**5.2 Ownership and locks.** A component's owned paths are writable only by the agent holding its lock. A lock file records: component, orchestrator, agent/task ID, branch, base commit. Claiming = the orchestrator commits the lock file to main and pushes. If the push is rejected because the lock already exists, the claim failed. Release = the holding orchestrator deletes the lock after merge. **Shared files belong to the orchestrator only:** build config, version catalog, module/settings registry, DI or composition root, navigation graph, CI workflows, DB schema registry. Subagents request shared-file edits in their handoff; the orchestrator applies them.

**5.3 Parallelism.** Components run in parallel only when owned paths are disjoint and every dependency's interface is already merged. Max concurrent implementers is set at Gate 3 (default 3). Research and review agents are read-only and need no locks; each writes only its own output file.

**5.4 Isolation and merging.** Each implementer works on branch `agent/<task-id>` in its own worktree. Only the orchestrator merges to main, one merge at a time, after: build passes, tests pass, placeholder scan passes, reviewer approves.

**5.5 Checkpoint after every step.** A step ends with: code and tests for that step; handoff file updated (done, next, gotchas, open questions); commit `[<task-id> step k/N] <summary>`; push. Uncommitted work at the end of a step is a protocol violation. After every merge or gate, the orchestrator updates `STATE.md` and appends one line to `JOURNAL.md`, then commits and pushes.

**5.6 Resume protocol** (fresh session, crash, usage limit, compaction): read the entry file, then `STATE.md`, then open items in `DECISIONS.md`, then `coordination/locks/`, then active task cards and handoffs, then the last 20 lines of `JOURNAL.md`. Verify branch heads match the handoffs. Continue from each handoff's "next" field. Never redo a step whose commit exists. Files beat conversation memory every time.

**5.7 Context hygiene.** A subagent's brief is: its task card, its component's `ARCHITECT.md`, the interface files of its dependencies, and `DESIGN_SYSTEM.md` if it touches UI. No transcripts, no other components' internals. The orchestrator keeps its own context lean: it re-reads `STATE.md` at the start of every cycle and keeps detail in files.

**5.8 Stale locks.** A lock whose branch has gained no commit since the previous orchestrator cycle, and whose handoff is not marked blocked, is suspect. The orchestrator inspects the branch, writes a recovery note in the handoff, then either resumes from the handoff with a fresh subagent or releases the lock. Never remove the other orchestrator's lock without Pat's ruling.

**5.9 Cross-orchestrator rules.** Claude Code and Codex share every coordination file. Each orchestrator merges only its own agent branches. First successful lock claim owns the component. Any conflict between orchestrators goes to `DECISIONS.md` for Pat.

---

## 6. GATES

At every gate: write `gates/GATE-<n>.md`, update `STATE.md`, commit, push, then send Pat a short message (section 9) and STOP until he rules.

**Gate 0: Bootstrap.** Confirm repo access and that push works. Create the section 4 layout: `GAUNTLET.md` (this document verbatim), `STATE.md`, `DECISIONS.md` seeded from section 7, empty templates, role briefs in `coordination/agents/`, the entry file, and the tool-specific agent definitions from section 11. Verify your own tooling (section 11) and record it in `research/R-00-tooling.md`. No app code. Report what was created and the open decisions.

**Gate 1: Research.** Fan out read-only research agents, one topic each. Every research file: question; findings with source URL and access date; options; recommendation; risks; confidence. Topics:

- R-01 Stack selection: scored matrix against section 3 criteria, fallback included
- R-02 Card data: Scryfall bulk types (measure current sizes), fields, update cadence, terms; MTGJSON comparison; choose primary and secondary sources for Full and Lean modes
- R-03 Comprehensive Rules: official source, format, update cadence, parsing approach
- R-04 Commander Brackets current official rules, Game Changers source, combo database and its terms
- R-05 Moxfield feature inventory, import feasibility, terms
- R-06 Archidekt feature inventory, import feasibility, terms
- R-07 Life counter inventory: LifeElk, Lifetap, two other top-rated
- R-08 Price data: sources, currencies, refresh cadence, terms
- R-09 Recommendation data: what works offline; online enrichers and their terms
- R-10 LLM provider APIs: auth, streaming, model listing, tool calling, pricing exposure
- R-11 On-device LLM runtimes for Android and Windows: formats, sizes, performance, interop with candidate stacks
- R-12 Secure key storage per platform
- R-13 Adaptive layouts, foldable postures, Windows desktop UX for candidate stacks
- R-14 Local DB and full-text search on both platforms at full card set scale
- R-15 CI/CD: signed Android builds and Windows packaging on GitHub Actions; screenshot testing
- R-16 Deck and collection text/CSV formats in common use
- R-17 Card link URL patterns

Then `RESEARCH_SUMMARY.md`: one recommended ruling per topic, plus the parity matrices from R-05/R-06/R-07 with build/adapt/skip recommendations.

**Gate 2: Spec.** `SPEC.md` (feature-by-feature with acceptance criteria), `DESIGN_SYSTEM.md`, `COVERAGE.md` (every brief item mapped; anything unmapped is listed as a question, never silently dropped), performance budgets with numbers, data model, component list with dependency DAG, screen inventory with the layout for each size class and posture. An independent reviewer agent audits SPEC against GAUNTLET and Appendix A and files a gap report in the gate.

**Gate 3: Architecture and plan.** Every `ARCHITECT.md`; `MILESTONES.md`; CI workflow design; final role briefs and tool-specific agent definitions; `IMPOSSIBLE.md`; `RISKS.md`; parallel execution plan; max concurrent implementers. Reviewer checks: zero owned-path overlap, zero DAG cycles, 100% coverage.

**Gate 4 onward: Milestones.** One gate per milestone. Proposed order (finalize at Gate 3):

- **M0 Walking skeleton:** CI builds a signed Android APK and a Windows package; both install and launch; DB schema in place; a small data subset imports end to end; adaptive navigation shell containing only implemented destinations; placeholder scan live in CI.
- M1 Card DB, Full/Lean modes, offline search, Comprehensive Rules
- M2 Life counter (standalone, highest daily value)
- M3 Deck builder core, import/export
- M4 Collection and (n) pricing
- M5 Analysis, validation, bracket engine, card links
- M6 Budget-aware recommendations
- M7 AI deck lab, all provider types including prompt export
- M8 Image packs, polish, accessibility, performance pass against budgets
- M9 Hardening: full regression, IMPOSSIBLE.md review, release artifacts

Each milestone gate includes: CI artifact links, install notes, screenshots per size class, test summary, coverage delta, open risks.

---

## 7. DECISION LEDGER (seed)

| ID | Decision | Status |
|---|---|---|
| D-01 | Package / application ID. Proposed: `com.architectpsg.commandlibrary` | Open: Pat rules |
| D-02 | Repo name and visibility | Open |
| D-03 | Release signing: Pat's permanent key via GitHub secrets, or a new key for this app | Open |
| D-04 | Default currency and price source | Open (after R-08) |
| D-05 | Will Claude Code and Codex run this repo concurrently or one at a time | Open |
| D-06 | Windows installer format; code signing (unsigned installers trigger SmartScreen warnings; acceptable for personal use?) | Open |
| D-07 | Stack | Open (after R-01) |
| D-08 | Max concurrent implementer agents | Open (Gate 3) |

Every later ruling is appended with Pat's words quoted verbatim.

---

## 8. POST-RELEASE BACKLOG (logged, non-blocking, never a reason to delay)

- Public release: WotC Fan Content Policy compliance; Scryfall terms (attribution, no paywalling their data, image guidelines); store listing; privacy policy. Low priority.
- Cross-device sync.
- Multi-device shared life counter state.
- Anything else discovered that is out of scope, appended with the date found.

---

## 9. REPORTING RULES

- Gate messages to Pat are short: what's done, what needs a ruling (numbered, each with your recommendation and the tradeoff in one line), and file paths. Full detail lives in the files.
- Pat reads on a phone. No walls of text in chat.
- No flattery, no filler, no em dashes.
- If something cannot be done, say so plainly with the reason. Never fake it.
- Never claim a build succeeded, a test passed, or a fact is current without the run log or the source.
- Never invent an API endpoint, field, file format, or library capability. Unverified means unverified, and it goes in the research file as such.

---

## 10. FACTS AT AUTHORING TIME (Oct 11, 2026). Starting points only; re-verify at Gate 1.

- Scryfall requires a descriptive `User-Agent` and an `Accept` header on API requests; asks that traffic to api.scryfall.com stay under 10 requests per second; asks that large lookups and image downloads use bulk data; files on `*.scryfall.io` are outside the API rate limit. Source: scryfall.com/docs FAQ on access problems.
- Scryfall exposes Commander Game Changers through the `is:gamechanger` search. Third-party trackers report the list at 53 cards after the Feb 9, 2026 update. The bracket system was revised Oct 21, 2025 (tutor restriction removed, 10 cards removed from the list). Fetch the official current rules; never rely on these numbers.
- LifeElk is published by CardElk d.o.o. Its listed features: up to 6 players, commander damage, EDH/infect/energy counters that can be hidden, dice, coin flip, per-player backgrounds, custom starting life, tap or hold to change life, autosave.

---

## 11. CLAUDE CODE SPECIFICS

**Entry file:** `CLAUDE.md` at repo root. It loads every session, so keep it short (target under 100 lines): project one-liner, "run the resume protocol in docs/gauntlet/GAUNTLET.md section 5.6 first", the zero-placeholder rule, the checkpoint-per-step rule, the shared-file ownership rule, and pointers to STATE.md and DECISIONS.md. If an `AGENTS.md` from the Codex orchestrator exists, leave it intact and keep the two entry files consistent on protocol.

**Verify tooling at Gate 0** against the current Claude Code docs (code.claude.com/docs) and record results in `research/R-00-tooling.md`: subagent definition format and frontmatter fields (name, description, tools, model), worktree isolation for subagents, background subagents, and hooks. Use only what you verified. Confirm `git push` works from this session before creating anything else; if it fails, stop and report.

**Subagents.** Project subagents live in `.claude/agents/<role>.md`, generated from the tool-neutral briefs in `coordination/agents/`. Keep both in sync; the neutral brief is the source of truth. Roles:

| Role | Writes | Notes |
|---|---|---|
| researcher | only its own `research/R-xx-*.md` | read-only on code; web access |
| architect | only its component's `ARCHITECT.md` | one component per invocation |
| implementer | only its component's owned paths, on its own branch/worktree | holds the lock; checkpoints every step |
| test-writer | only test paths inside the owned component | may run alongside its implementer only if paths are disjoint |
| reviewer | only review notes in the handoff | read-only on code; never reviews its own work |
| ui-auditor | only audit notes in the handoff | read-only; checks screens and screenshots against DESIGN_SYSTEM.md |

Subagents cannot spawn subagents. Only you, the orchestrator, delegate.

**Isolation.** Launch implementers and test-writers with worktree isolation on branch `agent/<task-id>`. If worktree isolation turns out to be unavailable in this environment, run implementers sequentially on their own branches and record that in `R-00-tooling.md`.

**Subagent prompts start cold.** Every delegation message is self-contained: task card path, the exact files to read (per 5.7), the step(s) to perform, the checkpoint duty (5.5), and the instruction to update its handoff before returning. Ask for a return summary of at most 10 lines; details belong in the handoff file.

**Hooks (only if verified at Gate 0).** Consider a hook that blocks a subagent from finishing with uncommitted changes in its worktree, and one that runs the placeholder scan before commit. Adopt only after verifying the mechanism.

**Model choice.** Use the strongest available model for architect and reviewer roles. Implementers may use a faster model if Gate 3 shows the step plans are small enough.

---

## APPENDIX A. PAT'S ORIGINAL REQUEST AND RULINGS (verbatim, uncorrected)

### Original request

> I want to build a gauntlet prompt to use to spec out a build for and  ultimate magic the gathering companion app, for Android/windows, combining features of the app life elk, with deck builders like moxfield.com and archidect, with oracle access, deck bracket identifier, budget settings for card recommendations , integration links per card to sources like scryfal etc, beautiful with a pristine, user friendly UI/UX, optimized for standard and folding phones, as well as pc resolution if run that way, and an integrated LLM roast my deck/chat about my deck/upgrade my deck/surprise me about my deck (with options for local ai, API keys, or just generating a prompt for your ai of choice. It needs to be fully functional, with zero placeholders (unless impossible to build from mobile/GitHub, and have highly efficient scalable code.

### Rulings (answers to the twelve intake questions)

Questions asked, in order: 1 who runs the gauntlet and what it outputs; 2 stack; 3 audience; 4 card data offline vs online; 5 phone/PC sync; 6 Moxfield/Archidekt import vs native; 7 collection tracking; 8 local AI options; 9 formats; 10 life counter scope and shared state; 11 gauntlet shape and delivery; 12 name and package ID.

> 1. For Claude code, one for gpt. The goal is to use code/codex to spawn subagents, to build architect.md files before each separate component, make sure they don't work on the same code simultaneously, and save their work after each step to protect against context rot, sessions failures or usage limits.
>
> 2. Let gauntlet research and justify with our known working setups as fallbacks.
>
> 3. Personal first, worry about the rest later. Just flag that is a low priority post release idea and not a blocker.
>
> 4. Both. Should be as functional as possible offline with the option to only save oracle data for cards in your decks if you want to store less, but otherwise yes a local file of the entire set of cards and rules + oracles. It's rendering plain text basically so it shouldn't be too huge. Card arts would be online only unless you opt to save your decks cards/collection cards/all cards.
>
> 5. Export import as files, plain text and .csv for now. Worry about sync later
>
> 6. Natively as possible unless we have smarter solutions
>
> 7. Yes with a (n) where n is value. So you can see the 0.00 for your own owned without having to separately check price.
>
> 8. As many options as possible.
>
> 9. All formats, commander first class
>
> 10. Yes
>
> 11. Gated passes. Always filed.
>
> 12. Command Library id. Not determined

Context for ruling 10: the question proposed LifeElk parity plus Lifetap-style extras (monarch, initiative, day/night, commander tax, turn timer, card-art backgrounds) with multi-phone shared game state deferred to later. "Yes" accepts that whole proposal.
