# R-05 Moxfield feature inventory and import

Path: `docs/gauntlet/research/R-05-moxfield.md`. Status: complete with explicit verification gaps. Research/access date: 2026-10-11. Recommendations are design proposals, not assertions about Moxfield.

## Question

What does Moxfield offer a deck builder, which features should Command Library build/adapt/skip, and is there an authorized URL import route?

## Findings

### F1. Primary sources and evidence age

- **W**: [Official feature wiki](https://github.com/moxfield/moxfield-public/wiki/Features), accessed 2026-10-11, edited 2020-10-14. This is historical primary evidence, not a current hands-on test. Individual historical features appear in the matrix below; current unchanged behavior is UNVERIFIED.
- **A**: [Main app](https://moxfield.com/) and [help](https://moxfield.com/help), accessed 2026-10-11. Both returned only a JavaScript loading shell. UNVERIFIED means a current browser inspection or current official help source is still required; no lack of a feature is inferred from an inaccessible UI.
- **T**: [Moxfield LLC terms served by its Corefield property](https://sorcery.moxfield.com/help/terms), accessed 2026-10-11, effective date 2023-03-02. The text explicitly applies to contents within moxfield.com. The [main-site terms](https://moxfield.com/help/terms), accessed 2026-10-11, returned a JavaScript shell. Exact identity with the main app's currently displayed version is UNVERIFIED.
- **P**: [Moxfield's Patreon about page](https://www.patreon.com/moxfield/about?l=en-GB), accessed 2026-10-11, confirms over 30 formats, views, tags, collection tracking and dark mode.
- **M**: [Membership page](https://www.patreon.com/moxfield/membership), accessed 2026-10-11. Paid monthly tiers shown in USD: Status Quo $1, Improvements $5, Innovations $10, Beyond Imaginable $20, Bragging Rights $50. Specific paid feature entitlements are UNVERIFIED because the retrieved tier listing lacks benefit details; do not mark ordinary builder features paywalled without evidence.
- **E**: [Export feedback with reply by Kerakis // Rob](https://moxfield.nolt.io/2064), accessed 2026-10-11. This source confirms Moxfield, MTGO and Arena exports, and tag copying through Bulk Edit. Treat the named maintainer reply separately from the user's proposed new format, which is not a shipped sample.
- **R**: [Release announcement by u/moxfieldmtg](https://www.reddit.com/r/Moxfield/comments/ubz6t1/new_release_april_25_2022/), accessed 2026-10-11, dated 2022-04-25. Confirms packages, account preferences, set browsing and a site search popup; current exact controls are UNVERIFIED.
- **B**: [Commander Brackets thread and maintainer replies](https://www.reddit.com/r/Moxfield/comments/1inq8bn/megathread_commander_brackets_feature_feedback/), accessed 2026-10-11. Historical primary statements confirm auto-estimate, manual bracket setting subject to minimum criteria, and deck bracket sorting. The 2025 rules referenced there must not be imported as current 2026 bracket rules.
- **C**: [Binder-column export feedback](https://moxfield.nolt.io/1782), accessed 2026-10-11. User requests and merged feedback establish investigation leads, not official proof of shipped CSV columns.

Source codes in every matrix cell resolve to these explicit URLs/access dates. W entries say what the dated official wiki documented; A entries mark a failed current verification, not presumed features. Paid status for each builder feature is UNVERIFIED unless M provides an entitlement, which it presently does not.

### F2. Feature coverage and limits

W documents boards, tags, visual dragging, printing choice, analysis, history, primers, exports, validation and a sandbox. The matrix itemizes them with deliberately concise historical descriptions. P corroborates current collection tracking, views and custom tags. Exact quantities UI, partner/background/companion handling, notes, named-version diffs, folders, land advice and hypergeometric tools were not established by primary evidence. Sources: W, P, A (URLs above, accessed 2026-10-11).

Searches targeted Moxfield's feature/help pages, public repository, changelog, Patreon and owner announcements. A changelog attempt at https://moxfield.com/help/changelog was inaccessible on 2026-10-11; no URL route or undocumented API was guessed to recover it. For each UNVERIFIED matrix item, the required probe is a human browser test of that specific feature on the live app or retrieval of a current owner-authored help/release statement. A creator beta/editor teaser seen in search is not treated as shipped parity.

### F3. Automated access, API, and terms

T's Security and Restrictions section prohibits automated site access. Short exact excerpt: "Use any robot, spider or other automatic device, process or means to access the Site for any purpose". Its other clauses restrict unauthorized manual copying, overburdening and unauthorized access, and reserve reproduction rights and written-consent exceptions. Source: T (accessed 2026-10-11). This establishes a restrictive primary policy, not an affirmative third-party API license.

UNVERIFIED: a public officially documented Moxfield API, third-party-client authorization process, exception for this application, or scope/rate limits of such an exception. Search surfaced reverse-engineered third-party docs, which cannot supply permission and were not used to infer endpoints. Required probe: locate an owner-published API policy or obtain written authorization specifying Command Library's access and redistribution scope. No contact was sent. Sources attempted: https://moxfield.com/help and https://moxfield.com/help/terms (accessed 2026-10-11, JavaScript shell).

UNVERIFIED: robots rules. https://moxfield.com/robots.txt was inaccessible through the research browser on 2026-10-11. Required probe: retrieve the canonical robots file without bypassing access controls, record user-agent rules and date. Even a permissive robots directive would not establish a terms exception. Recommendation: skip URL data retrieval unless written or published authorization is established, and offer user-initiated export/paste instead. Source policy basis: T (accessed 2026-10-11).

### F4. Export-then-paste fallback

Historical supported export destinations: Arena and MTGO (W); Moxfield format and Bulk Edit tag copying are confirmed by the maintainer reply (E). The full current menu, exact labels, text grammar, board markers and finish encoding remain UNVERIFIED from primary UI evidence. Collection CSV is distinct from deck export; no primary proof establishes that the deck Export menu currently offers CSV. Sources: W, E, C (accessed 2026-10-11).

Proposed guided flow, to be UI-tested before publishing exact screenshots/labels:

1. The user opens their deck in Moxfield in a normal browser. Command Library can display the supplied URL as a link without fetching its data.
2. Open the deck actions/export control. The likely route **More > Export** is UNVERIFIED by primary evidence in this session, so guidance must permit finding the visible Export action instead of asserting its current placement.
3. Choose an available Moxfield, MTGO or Arena text export and use its copy/download control. Exact current labels and which preserve commanders/companions/considering remain UNVERIFIED. If tags matter, use Bulk Edit copying as E describes; check the resulting text rather than assuming a standard export carries tags.
4. Paste text or select the downloaded file in Command Library. Parse locally, show board/quantity/printing/finish resolution, and ask the user to resolve unmatched or ambiguous rows before saving.
5. Review commanders, companion, sideboard and maybe entries, then save the imported deck. Descriptions, primers, history and collection ownership must never be silently presumed transferred by a simple list export.

Steps 1 and 4 to 5 are our proposed flow, not claims about Moxfield's exact UI. Steps 2 to 3 need live testing; their known format availability derives from W and E (accessed 2026-10-11). Prefer migration of the user's own lists; this does not grant wholesale copying of others' primers or private data.

### F5. Exact text/CSV examples and R-16 boundary

UNVERIFIED: an authentic current Moxfield text export sample and an authentic CSV header/sample. No public deck export was fetched because automated access authorization was not established. No invented example is labelled Moxfield output. Source attempts: A, W, E, C (accessed 2026-10-11).

Required exact probe: in the normal UI, create/use an owned representative deck with a two-commander configuration, companion where legal, multiple quantities, split/DFC names, specific printing/finish, sideboard, maybe entries and tags. Export every offered text format; retain its exact labels and resulting bytes, checking separators, board markers, set/collector/finish encodings and line endings. Separately export an owned collection/binder CSV, retaining exact header, quoting and one representative row with commas/quotes in notes. If a deck CSV export exists, capture it separately. Compare a reimport against the original to identify lost fields. Sources requiring browser confirmation: https://moxfield.com/ and https://moxfield.nolt.io/2064 (accessed 2026-10-11).

Cross-check with R-16 is a required orchestrator follow-up; the researcher role's supplied brief does not authorize reading another researcher's file here. Recommendation: R-16 must use these authentic user-exported fixtures to verify syntax, rather than deriving Moxfield's format from third-party parsers. This is a dependency recommendation, not an assertion that R-16 has passed.

## Parity matrix

Every GAUNTLET 2.B item has a row. **Build** means native feature; **adapt** means implement a local version with stated differences; **skip** means omit the named reference behavior. All rulings are recommendations for Pat. Source codes resolve to F1, all accessed 2026-10-11. Historical W descriptions do not establish present UI parity.

| Feature | Moxfield evidence | Ruling | Reason for Command Library |
|---|---|---|---|
| Commander slot | Commander format (W); exact slot UNVERIFIED (A) | build | Store explicit commander board and validate eligibility. |
| Partners | UNVERIFIED (A) | build | Validate both cards and combined color identity. |
| Backgrounds | UNVERIFIED (A) | build | Support eligible commander/background pair rules. |
| Companion | UNVERIFIED (A) | build | Keep separate role and determinable companion checks. |
| Mainboard | Supported (W) | build | Core local deck contents. |
| Sideboard | Supported (W) | build | Apply per-format size and legality rules. |
| Maybeboard/considering | Maybeboard (W) | build | Preserve ideas outside active legality and cost totals. |
| Custom categories | Categories (W) | build | User-defined grouping with ordered display. |
| Card tags | Global/deck tags (W) | adapt | Separate global role defaults from per-deck overrides. |
| Drag-and-drop | Visual tag movement (W) | adapt | Touch, keyboard and menu move alternatives. |
| Per-card printing | Printing switch (W) | build | Keep printing identity distinct from Oracle identity. |
| Per-card finish | Foil prices (W); selector UNVERIFIED (A) | build | Track finish for ownership and cost calculation. |
| Quantities | UNVERIFIED exact controls (A) | build | Support positive counts and format copy limits. |
| Per-card notes | UNVERIFIED (A) | build | Local context and upgrade rationale. |
| Markdown description | UNVERIFIED separate field (A) | build | Short deck summary with safe Markdown rendering. |
| Markdown primer | Editor/preview/contents (W) | adapt | Offline editor and navigation, sanitized rendering. |
| Mana curve | Spell/permanent/color curves (W) | adapt | Expose relevant distributions and face assumptions. |
| Color pips versus sources | Color-production ratio (W) | adapt | Separate demand from usable sources and conditional production. |
| Type breakdown | Type counts (W) | build | Derived local stats. |
| Average mana value | With/without lands (W) | build | Show denominator and MDFC treatment. |
| Land count guidance | Opening-land/curve statistics (W); advice UNVERIFIED (A) | adapt | Explain heuristics and avoid universal land targets. |
| Hypergeometric calculator | UNVERIFIED (A) | build | Exact without-replacement probability with visible inputs. |
| Opening-hand simulator | Seven-card samples (W) | build | Seeded/repeatable draw results from actual deck. |
| Goldfish playtester | Sandbox (W) | adapt | Local manual play with clear non-rules-engine scope. |
| Mulligan rules | UNVERIFIED (A) | build | Format-aware London flow and Commander free first mulligan. |
| Token list | Required tokens (W) | adapt | Derive related objects and preserve user additions. |
| Per-format legality | Format enforcement (W) | build | Use versioned legality snapshots and offline warnings. |
| Deck size validation | General validation (W); check details UNVERIFIED (A) | build | Explicit minimum/exact size and sideboard constraints. |
| Singleton validation | UNVERIFIED exact exceptions (A) | build | Honor basic lands and text-granted exceptions. |
| Color identity validation | Commander filtering (W) | build | Validate whole active deck, including paired leaders. |
| Banned/restricted validation | Banned enforcement (W); restricted details UNVERIFIED (A) | build | Separate unavailable, banned and restricted states. |
| Companion conditions | UNVERIFIED (A) | adapt | Report checked, failed or unknown rather than overclaim completeness. |
| Live warnings | Failure reasons (W); live timing UNVERIFIED (A) | build | Recompute incrementally after edit/import. |
| Deck versions | Card-change history (W); named snapshots UNVERIFIED (A) | adapt | Named immutable snapshots with restore. |
| Version diff | UNVERIFIED (A) | build | Added/removed quantity, board, printing, finish and note changes. |
| Duplicate deck | Copy decks (W) | build | Clone local content with independent identity. |
| Folders | UNVERIFIED (A) | build | Simple local organization without server dependence. |
| Deck tags | UNVERIFIED distinct deck metadata tags (A) | build | Searchable organization separate from card tags. |
| Search across decks | Site search (R); own-deck scope UNVERIFIED (A) | build | Offline name, format, commander and tag matching. |
| Sort across decks | Bracket sorting (B); full options UNVERIFIED (A) | build | Stable name, updated time, format, cost and bracket sorting. |
| Pasted-text import | Supported (W) | build | Preview and resolve all commonly supported grammars from R-16. |
| File import | Supported (W) | build | Local file picker and parse report. |
| CSV deck import | UNVERIFIED (A) | build | Explicit column mapping, proper CSV quoting, preserve unresolved rows. |
| Moxfield URL import | Permission absent; restrictive policy (T) | skip | Enable guided export until a provider authorization exists. |
| Import from other providers | URL imports historically (W) | adapt | Provider-specific permitted paths or user export fallback. |
| Common plain-text export | Arena/MTGO (W); Moxfield (E) | build | Round-trip where supported; label information loss. |
| CSV export | Collection reports (C); exact deck CSV UNVERIFIED (A) | build | Document our own stable schema including role and identity. |
| Bulk Edit tag copying | Maintainer-described (E) | adapt | Offer editable text plus structured backup for richer fields. |
| Bracket display | Auto/manual estimate (B) | adapt | Current official rules, explain evidence and uncertainty. |
| Packages | Package navigation (R) | adapt | Reusable local card groups with explicit copy/update semantics. |
| Collection tracking | Confirmed (P) | build | Owned quantities by printing and finish; effective acquisition cost. |
| Binders | Feedback context (C); exact current behavior UNVERIFIED (A) | adapt | Physical location labels and CSV preservation. |
| Price providers/currency | TCGPlayer/CardMarket/CardHoarder (W) | adapt | Only legally available provider fields and current currencies. |
| View modes | Text/images (W); multiple views (P) | adapt | Responsive list/grid and accessible text view. |
| Private/public/unlisted visibility | Visibility choices (W) | skip | Local decks by default; export sharing needs no hosted visibility system. |
| Social likes/comments/follows | Social feed (W) | skip | Outside native personal/offline library scope. |
| Public share links | Sharing (W) | adapt | OS share sheet and user-chosen exported artifact. |
| Social notifications/view counts | Documented (W) | skip | Avoid building a social backend for local use. |
| EDHREC integration | Recommendations (W) | adapt | Local recommendations now; external enrichment only with authorization. |
| Sandbox counters/dice/copies | Sandbox tools (W) | adapt | Manual local controls, including life/energy/poison and tokens. |
| Set/card browsing | Set page (R) | build | Offline catalog navigation and printing detail. |
| Scryfall-style search | Search syntax (W) | adapt | Implement locally supported fields and clearly label online fallback. |
| Card rulings display | Legality/rulings (W) | build | Offline source-dated rulings and rules links. |
| Dark mode/preferences | Confirmed (P, R) | build | Native theme, accessible contrast and stored preferences. |
| Patreon tiers | Paid tiers (M) | skip | Do not reproduce fundraising tiers; no verified feature gate. |
| Paid exclusive features | UNVERIFIED entitlements (M, A) | skip | No justified parity requirement until benefits are established. |
| In-app effective cost (n) | UNVERIFIED reference parity (A) | build | Required product feature; owned copies free, missing copies priced. |


## Options

1. Native offline builder with guided Moxfield export import. Recommended: fulfills the brief without assuming provider permissions. Policy basis: T; export evidence: W/E (accessed 2026-10-11).
2. Add URL import later under documented provider authorization. Tradeoff: smoother migration, but requires confirmed access scope, endpoint contract and revocation behavior. No such authorization is established. Sources attempted: A/T (accessed 2026-10-11).
3. Hosted social parity. Not recommended for the current personal library; it introduces accounts, moderation, synchronization and availability requirements unrelated to the brief. Reference features: W (accessed 2026-10-11).

## Recommendation

Build the core boards, editing, validation, organization and local analysis natively. Adapt views, playtesting, packages and bracket analysis for offline use. Skip social infrastructure, paid-tier imitation and unauthorized URL retrieval. Source basis: matrix references W/P/R/B/M/T, accessed 2026-10-11. Treat all matrix build/adapt behavior as Command Library proposals requiring spec acceptance, independent of whether Moxfield currently implements them.

Provide guided export/paste first. Before claiming import fidelity, obtain real user-initiated export fixtures for R-16 and confirm the live export controls and menu labels. Preserve every unresolved row, board and quantity in the preview; warn when a format omits notes, tags, primer, finish or printing. Source evidence: W/E/C, accessed 2026-10-11; unsupported fixture details are explicitly UNVERIFIED in F5.

## Risks

- Historical wiki facts cannot prove current UI behavior. The live app/help rendering prevented hands-on verification. Sources: W/A (accessed 2026-10-11).
- Terms on the Moxfield-owned subdomain explicitly cover moxfield.com, but the current main-site text could not be compared. Existing evidence supplies no automated import permission. Sources: T/A (accessed 2026-10-11).
- Feedback requests must not be confused with implemented features or authentic export samples. Source: C/E (accessed 2026-10-11).
- Exact CSV/text fixtures, paid entitlements and several advanced features are UNVERIFIED; probes are listed rather than fabricated. Sources attempted: A/M/E (accessed 2026-10-11).
- Historical bracket implementation used historical rules; current thresholds belong to the independent official-rules research. Source: B (accessed 2026-10-11).

## Confidence

Medium for historical feature inventory and proposed parity priorities; high that no authorized URL-import route has been established in this research; low for exact current export schemas and UI-only details. All scope items have matrix rows or explicit verification gaps. No endpoints were inferred, no automated deck extraction was performed, and no secrets were accessed.
