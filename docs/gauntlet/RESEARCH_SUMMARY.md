# RESEARCH SUMMARY

Gate 1 research filed 2026-10-11; awaiting Pat ruling. All 17 topic reports are saved. No application code, builds, device tests or credentialed API probes were run. Source URLs, dates and explicit UNVERIFIED scope facts are in each report. Recommendations below are proposals, not Pat rulings.

## Recommended rulings

| Topic | File | Recommendation | Confidence | Decision ID |
|---|---|---|---|---|
| R-01 | [R-01-stack.md](research/R-01-stack.md) | Flutter S2: 88.6/100 versus Kotlin S1 84.0; documented Narrator path motivates departure. Keep Kotlin fallback and sensitivity results. Runtime accessibility unproven. | Medium | D-07 |
| R-02 | [R-02-card-data.md](research/R-02-card-data.md) | Scryfall primary, MTGJSON secondary, conditional on current contracts/terms. Full retains all Oracle identities and printings including languages; Lean caches fetched gameplay/rulings. Inherited metadata is not remeasurement. | Medium/low on current probes | D-14 |
| R-03 | [R-03-comprehensive-rules.md](research/R-03-comprehensive-rules.md) | Full versioned searchable offline official rules, acquired personally from official source or user import; separate public corpus redistribution permission. Links alone do not satisfy the brief. | Medium | D-13 |
| R-04 | [R-04-brackets-combos.md](research/R-04-brackets-combos.md) | Refreshable reviewed Wizards criteria and reconciled Game Changers; attributed Spellbook bulk-local combos. No hardcoded count or official-rank claim. | Medium | D-12 |
| R-05 | [R-05-moxfield.md](research/R-05-moxfield.md) | Adopt complete native build/adapt matrix; guided export then paste/file import. Automated URL retrieval conditional on current permission. | Medium | D-12 |
| R-06 | [R-06-archidekt.md](research/R-06-archidekt.md) | Adopt complete native build/adapt matrix; guided import pending current API/terms reconciliation and authentic producer fixtures. | Medium | D-12 |
| R-07 | [R-07-life-counters.md](research/R-07-life-counters.md) | Every mandatory 1-6 player baseline preserved. Lotus/Mox are established-app sample choices, not exhaustive global top-two ranking. Adopt matrix with disclosed optional extras. | Medium | D-12 |
| R-08 | [R-08-price-data.md](research/R-08-price-data.md) | USD/Scryfall default conditional on source/terms checks; offer native EUR and MTGO TIX. Owned-as-free default; missing prices unknown; source/finish/unit provenance. | Medium | D-04 |
| R-09 | [R-09-recommendation-data.md](research/R-09-recommendation-data.md) | Explainable offline roles/synergies plus permitted Spellbook/MTGJSON enrichment; no automated EDHREC under current terms. Keep external links. | Medium | D-12 |
| R-10 | [R-10-llm-providers.md](research/R-10-llm-providers.md) | Four API dialects with provider overrides and Gemini subcodecs; keep eight cloud and six local candidates. Terms, exact schemas and units explicitly unresolved where unverified. | Medium/low on unprobed contracts | D-12 |
| R-11 | [R-11-on-device-llm.md](research/R-11-on-device-llm.md) | llama.cpp/GGUF baseline Android and Windows; optional LiteRT-LM. Runtime-neutral streaming/cancel and resumable model downloads; license each model separately. | Medium | D-12 |
| R-12 | [R-12-key-storage.md](research/R-12-key-storage.md) | Android Keystore AES-GCM; Windows current-user DPAPI or Credential Manager. Verify stable Flutter bridge; no plaintext fallback, logs or credential exports. | High on native APIs | D-12 |
| R-13 | [R-13-adaptive-layouts.md](research/R-13-adaptive-layouts.md) | Five width classes plus height overrides, real hinge/posture signals, keyboard/mouse and state retention. Require actual TalkBack/Narrator checks on packaged implementation. | Medium | D-07 |
| R-14 | [R-14-database-fts.md](research/R-14-database-fts.md) | SQLite/FTS5, canonical Oracle plus printing/ruling entities, typed query AST, bounded gzip JSONL framing where documented and atomic catalog generations. Flutter drift if selected; Room3 for Kotlin fallback. | Medium | D-07 |
| R-15 | [R-15-ci-cd.md](research/R-15-ci-cd.md) | Existing-key signed Android APK via single ANDROID_SIGNING secret; if Flutter, unsigned per-user Inno Setup EXE plus portable ZIP. Five width classes and posture screenshots/native smoke planned. | Medium | D-06 |
| R-16 | [R-16-deck-formats.md](research/R-16-deck-formats.md) | Lossless board/printing/finish/language intermediate import, robust UTF-8 quoted/multiline CSV and unresolved-row preview. Native full exports; vendor adapters require authentic fixtures. | Medium | D-12 |
| R-17 | [R-17-card-links.md](research/R-17-card-links.md) | Stored provider URI first, verified adapter second, visible search fallback third. All seven named configurable destinations; no guessed printing-specific seller slugs. | Medium | D-12 |

## Gate choices and evidence limits

- D-04: USD/Scryfall reference prices by default, conditional on current source terms; EUR/Cardmarket and MTGO TIX remain selectable. MTGJSON/Cardmarket documented alternatives remain available. Do not require closed new API enrollment.
- D-06: if Flutter, unsigned per-user Inno Setup EXE plus portable ZIP; signing deferred for personal use. Unsigned delivery may face Windows policy/SmartScreen restrictions. No install result is claimed. If Kotlin is selected, reconsider its jpackage MSI/ZIP option.
- D-07: Flutter wins the baseline scored matrix by 4.6 points because official Windows accessibility evidence addresses Narrator more directly. It still needs pinned engine/config and real reader validation. Kotlin wins under stronger reuse weighting or successful Narrator proof; the departure is evidence-sensitive.
- D-12: adopt the full matrices and topic recommendations, retaining every required baseline. Reference support marked U remains unknown, not a reason to cut a feature. Approve Lotus/Mox as the disclosed established-app comparison cohort, not a global rating claim. Automated Moxfield/Archidekt/EDHREC enrichment is not assumed permitted; guided imports/external links preserve required workflows. Keep all provider candidates with terms/contract checks before verified support claims.
- D-13: preserve full offline searchable Comprehensive Rules through personal official-source acquisition/cache where applicable terms allow, with user-initiated local import fallback. Public verbatim bundling permission is unresolved and separate; link-only behavior is insufficient. No new permission to republish is inferred.
- D-14: Full means every Oracle card and every printing/language. Do not treat default_cards as that entire scope; resolve current all_cards/other-feed coverage and measure transfer/storage before specifying budgets. Lean remains fetched-data offline. Foreign inventory identities are preserved in both modes.

Important verification gaps: current Scryfall terms/bytes/card/image counts, exact live Game Changer set reconciliation, current vendor export fixtures and URL HTTP results, cloud provider terms/schema details, target model/device performance, stable plugin tuple and real accessibility remain unverified where stated. The next spec must track these as acceptance work, never as completed measurements.

## Parity matrices

The following are the complete R-05/R-06/R-07 matrices, reproduced without removing required or optional rows. Source codes inherit each report's dated register. Build/adapt/skip choices concern Command Library, not claims of tested competitor behavior.

### Moxfield (R-05)

See [source report](research/R-05-moxfield.md) for source register and evidence age.

- **W**: [Official feature wiki](https://github.com/moxfield/moxfield-public/wiki/Features), accessed 2026-10-11, edited 2020-10-14. This is historical primary evidence, not a current hands-on test. Individual historical features appear in the matrix below; current unchanged behavior is UNVERIFIED.
- **A**: [Main app](https://moxfield.com/) and [help](https://moxfield.com/help), accessed 2026-10-11. Both returned only a JavaScript loading shell. UNVERIFIED means a current browser inspection or current official help source is still required; no lack of a feature is inferred from an inaccessible UI.
- **T**: [Moxfield LLC terms served by its Corefield property](https://sorcery.moxfield.com/help/terms), accessed 2026-10-11, effective date 2023-03-02. The text explicitly applies to contents within moxfield.com. The [main-site terms](https://moxfield.com/help/terms), accessed 2026-10-11, returned a JavaScript shell. Exact identity with the main app's currently displayed version is UNVERIFIED.
- **P**: [Moxfield's Patreon about page](https://www.patreon.com/moxfield/about?l=en-GB), accessed 2026-10-11, confirms over 30 formats, views, tags, collection tracking and dark mode.
- **M**: [Membership page](https://www.patreon.com/moxfield/membership), accessed 2026-10-11. Paid monthly tiers shown in USD: Status Quo $1, Improvements $5, Innovations $10, Beyond Imaginable $20, Bragging Rights $50. Specific paid feature entitlements are UNVERIFIED because the retrieved tier listing lacks benefit details; do not mark ordinary builder features paywalled without evidence.
- **E**: [Export feedback with reply by Kerakis // Rob](https://moxfield.nolt.io/2064), accessed 2026-10-11. This source confirms Moxfield, MTGO and Arena exports, and tag copying through Bulk Edit. Treat the named maintainer reply separately from the user's proposed new format, which is not a shipped sample.
- **R**: [Release announcement by u/moxfieldmtg](https://www.reddit.com/r/Moxfield/comments/ubz6t1/new_release_april_25_2022/), accessed 2026-10-11, dated 2022-04-25. Confirms packages, account preferences, set browsing and a site search popup; current exact controls are UNVERIFIED.
- **B**: [Commander Brackets thread and maintainer replies](https://www.reddit.com/r/Moxfield/comments/1inq8bn/megathread_commander_brackets_feature_feedback/), accessed 2026-10-11. Historical primary statements confirm auto-estimate, manual bracket setting subject to minimum criteria, and deck bracket sorting. The 2025 rules referenced there must not be imported as current 2026 bracket rules.
- **C**: [Binder-column export feedback](https://moxfield.nolt.io/1782), accessed 2026-10-11. User requests and merged feedback establish investigation leads, not official proof of shipped CSV columns.

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

### Archidekt (R-06)

See [source report](research/R-06-archidekt.md) for source register and evidence age.

All source links in this table were accessed 2026-10-11. Build/adapt/skip are proposed product rulings, independent of whether reference-app support is confirmed. UNVERIFIED rows name the pages/searches checked.

| Feature | Archidekt evidence | Ruling | Reason for Command Library |
|---|---|---|---|
| Commander | Commander category. [Card menus](https://archidekt.com/news/2703551) | Build | Required command-zone model. |
| Partners | Developer describes multiple commander slots. [Background reply](https://archidekt.com/forum/thread/2932579/1) | Build | Validate eligible pairs. |
| Backgrounds | Developer says put both cards in commander slot; no conformance test. [Reply](https://archidekt.com/forum/thread/2932579/1) | Build | Enforce pairing/color rules locally. |
| Companions | Companion designation and deckbuilding-condition checks shipped. [Announcement](https://archidekt.com/news/2377839) | Build | Separate zone and explain unmet restrictions. |
| Mainboard | In-deck cards/categories. [Menus](https://archidekt.com/news/2703551) | Build | Core deck content. |
| Sideboard | Explicit card-category option. [Menus](https://archidekt.com/news/2703551) | Build | Format-aware sideboard counts. |
| Maybeboard | Brewer view gives maybeboard prominence. [Views](https://archidekt.com/news/3189294) | Build | Preserve excluded ideas. |
| Custom categories | User-created categories. [FAQ](https://archidekt.com/faq) | Adapt | Many roles without duplicating card quantities. |
| Card tags/labels | Color tags and primary/secondary categories. [Views](https://archidekt.com/news/3189294) | Adapt | Explicit card tags independent of zones. |
| Drag and drop | Category reordering shipped. [Draggable categories](https://archidekt.com/news/7305181) | Adapt | Touch and mouse, with accessible move commands. |
| Per-card printing | Card-menu printing selector. [Search overhaul](https://archidekt.com/news/2703551) | Build | Preserve chosen physical printing. |
| Finish | Treatment and foiling controls. [Search overhaul](https://archidekt.com/news/2703551) | Build | Printing/finish drive collection and cost. |
| Quantities | Quantity menu. [Search overhaul](https://archidekt.com/news/2703551) | Build | Avoid duplicate rows for count edits. |
| Per-card notes | UNVERIFIED shipped support; request/developer response indicates future work. [Feature thread](https://archidekt.com/forum/thread/9252375?orderBy=-points&page=1) | Build | Brief requires deck-specific annotations. |
| Description/primer | Rich-text descriptions; primer search exists. [Potpourri](https://archidekt.com/news/885575), [Primer page](https://archidekt.com/primers) | Build | Store user-owned description. |
| Markdown | UNVERIFIED current Markdown editor; search found request. [Discussion](https://archidekt.com/forum/thread/623675) | Build | Brief explicitly requires Markdown. |
| Mana curve | Full and grouped curves. [Stats](https://archidekt.com/news/9287631) | Adapt | Interactive breakdown without overwhelming mobile UI. |
| Pips versus land sources | Color cost/production, optional lands-only. [Stats](https://archidekt.com/news/9287631) | Adapt | Distinguish conditional production from reliable sources. |
| Type breakdown | Quantity by type/subtype. [Stats](https://archidekt.com/news/9287631) | Build | Explain composition. |
| Average mana value | Existing average plus new total. [Odds and Ends](https://archidekt.com/news/19248512) | Build | Explicit land/MDFC handling. |
| Land count guidance | UNVERIFIED automation; stats/news search found counts, not a recommendation model. [Stats](https://archidekt.com/news/9287631) | Build | Explain heuristic and uncertainty. |
| Hypergeometric calculator | Configurable probability table; exact algorithm UNVERIFIED. [Stats](https://archidekt.com/news/9287631) | Build | Tested draw probabilities, explicit inputs. |
| Opening hands | Initial hand/mulligan overlay. [Playtester 2.0](https://archidekt.com/news/3417345) | Build | Offline random opening-hand simulation. |
| Goldfish playtester | Draw/search/shuffle/move actions. [Playtester 2.0](https://archidekt.com/news/3417345) | Adapt | User-operated tabletop, bounded state history. |
| Mulligan rules | London and optional free mulligans. [Playtester 2.0](https://archidekt.com/news/3417345) | Build | Format-aware free-mulligan behavior. |
| Token list | Derived extras with originating-card links. [Tokens and Extras](https://archidekt.com/news/3834269?page=1) | Build | Derive from data and preserve printing preferences. |
| Per-format legality | Companion checks plus chosen format. [Companion](https://archidekt.com/news/2377839), [Search overhaul](https://archidekt.com/news/2703551) | Build | Local, dated legality data. |
| Deck-size checks | UNVERIFIED exact coverage from checked format/companion docs. [Companion](https://archidekt.com/news/2377839) | Build | Counts by included zone and format. |
| Singleton checks | UNVERIFIED exceptions coverage from checked docs. [Companion](https://archidekt.com/news/2377839) | Build | Card-specific quantity exceptions. |
| Color identity checks | UNVERIFIED complete rule coverage; background slot advice only. [Background](https://archidekt.com/forum/thread/2932579/1) | Build | Pair-aware identity validation. |
| Banned/restricted checks | UNVERIFIED current validation coverage. [Companion](https://archidekt.com/news/2377839) | Build | Data-driven banned/restricted quantities. |
| Live warnings | UNVERIFIED current presentation beyond legality/companion announcements. [Companion](https://archidekt.com/news/2377839) | Build | Recalculate actionable warnings after edits. |
| Deck versions | Named snapshots. [Versions](https://archidekt.com/news/3047466) | Build | Preserve user checkpoints. |
| Diff | Compare membership and attributes. [Versions](https://archidekt.com/news/3047466) | Adapt | Include quantity/printing/finish/zone changes. |
| Restore | Restore saved versions shipped. [Views](https://archidekt.com/news/3189294) | Build | Recover prior state safely. |
| Duplicate deck | Versions can be copied into a new deck. [Versions](https://archidekt.com/news/3047466) | Build | Independent local copy. |
| Folders | Folder organization. [FAQ](https://archidekt.com/faq) | Build | Local organization. |
| Deck tags | Curated themes searchable on decks. [Deck Tags](https://archidekt.com/news/4256905) | Adapt | Permit user-defined local tags. |
| Search decks | Filtered deck discovery. [FAQ](https://archidekt.com/faq) | Build | Search user's local decks. |
| Sort decks | Sortable deck overview. [FAQ](https://archidekt.com/faq) | Build | Predictable sort controls. |
| Text import | Paste into Import Cards. [FAQ](https://archidekt.com/faq) | Build | Preview and report unresolved lines. |
| Arena text import | Documented format support. [0.7.0](https://archidekt.com/news/11650) | Build | Common-format compatibility with fixtures. |
| CSV import | Collection CSV workflow evidenced; exact deck CSV import UNVERIFIED. [Collection](https://archidekt.com/news/3678345), [User report](https://archidekt.com/forum/thread/17168277) | Build | Flexible column mapping plus authentic fixtures. |
| URL import | Historical read permission conflicts with current general terms. [API](https://archidekt.com/forum/thread/2832338), [Terms](https://archidekt.com/terms) | Adapt | Ship guided export until scoped permission settled. |
| Plain text export | Text with categories shipped. [Potpourri](https://archidekt.com/news/885575) | Build | Choose boards and target syntax. |
| CSV export | Configurable printing/foil fields. [Autosave](https://archidekt.com/news/2890329) | Build | Document a stable application-owned schema. |
| Stacks/Grid/Text/Table/Brewer views | Multiple visual/editing layouts. [Views](https://archidekt.com/news/3189294), [Draggable categories](https://archidekt.com/news/7305181) | Adapt | Responsive modes with shared state. |
| Category templates | Presets. [Multi-select/templates](https://archidekt.com/news/2981306?page=1) | Adapt | Reusable user role categories. |
| Multi-edit | Bulk card editing. [Multi-select/templates](https://archidekt.com/news/2981306?page=1) | Build | Efficient collection and deck changes. |
| Collection | Tags/search/public subsets and deck membership. [Collection](https://archidekt.com/news/3678345) | Adapt | Local ownership drives the (n) rule. |
| Price sources | TCGplayer, Card Kingdom, Cardmarket, Cardhoarder attribution. [Static view page footer](https://archidekt.com/news/20605672) | Adapt | Use only independently verified permitted providers. |
| Printing optimization | Bulk edition optimization. [FAQ](https://archidekt.com/faq) | Adapt | Respect owned copies, finishes and budget. |
| Bracket display | Estimated or user-set bracket; original estimator used Game Changers. [Brackets](https://archidekt.com/news/11356226) | Adapt | Current R-04 rules with evidence and uncertainty. |
| Sandbox | Unsaved editor mode. [FAQ](https://archidekt.com/faq) | Adapt | Local draft with clear save/discard. |
| Interaction simulator | Hypothetical disruption prompts, manually resolved. [Simulator](https://archidekt.com/news/26104256) | Adapt | Optional advanced playtest tool after core goldfish. |
| Playtest log | Turn-organized move logging and tracker widget. [Logs](https://archidekt.com/news/13186079) | Adapt | Useful replay/debugging with offline persistence. |
| Autosave/undo | Default autosave, recent-change undo. [Views](https://archidekt.com/news/3189294) | Build | Durable local edits and reversible actions. |
| Collaboration | Live multi-user deck editing. [Collaborative](https://archidekt.com/news/3577935) | Skip | Cloud collaboration outside personal-first brief. |
| Comments/follows/voting | Community features. [FAQ](https://archidekt.com/faq) | Skip | No social backend required. |
| Image/QR sharing | Deck image and QR tools. [History](https://archidekt.com/news/2939828), [Deck Tags](https://archidekt.com/news/4256905) | Adapt | Optional user-controlled sharing. |
| Shopping carts | Vendor cart export. [FAQ](https://archidekt.com/faq) | Adapt | Existing external links and missing-copy export. |
| EDHREC recommendations/cuts | Search recommendations and cuts dropdown. [Static card view](https://archidekt.com/news/20605672) | Adapt | Offline recommendations; online only with R-09 permission. |
| Custom/pinned cards | Custom-card creation and pinning shipped. [Custom Cards](https://archidekt.com/news/17621845) | Skip | Outside baseline official-card catalog; possible backlog. |
| Paid ad removal | Patreon benefit. [FAQ](https://archidekt.com/faq) | Skip | No reason to add advertising infrastructure. |

### Life counters (R-07)

See [source report](research/R-07-life-counters.md) for source register and evidence age.

Evidence register and interpretation

All links in this register were accessed 2026-10-11. Matrix citations inherit that explicit date. `U` means UNVERIFIED: the linked store/developer descriptions were checked but do not establish that precise capability. It never means the app lacks the feature. `iOS` marks evidence confined to Apple releases; Android parity is not assumed. Recommendations and reasons are proposed product decisions against local GAUNTLET.md 2.G/Appendix A, read 2026-10-11, not competitor claims.

- [E] LifeElk Google Play: https://play.google.com/store/apps/details?hl=en&id=com.bartollesina.lifetap (accessed 2026-10-11).
- [EW] LifeElk developer site: https://lifeelk.com/ (accessed 2026-10-11).
- [EA] LifeElk US Apple listing/release history: https://apps.apple.com/us/app/mtg-life-counter-lifeelk/id6443751449 (accessed 2026-10-11).
- [T] Lifetap Google Play: https://play.google.com/store/apps/details?hl=en&id=com.lifetap (accessed 2026-10-11).
- [TW] Lifetap developer site: https://getlifetap.com/ (accessed 2026-10-11).
- [TA] Lifetap US Apple release history: https://apps.apple.com/us/app/lifetap-life-counter-for-mtg/id1508241754 (accessed 2026-10-11).
- [L] Lotus Google Play: https://play.google.com/store/apps/details?hl=en&id=com.vanilla.mtgcounter (accessed 2026-10-11).
- [LW] Lotus developer feature/pricing page: https://lifecounter.app/pricing/ (accessed 2026-10-11).
- [M] Mox Google Play: https://play.google.com/store/apps/details?hl=en&id=com.quince.magiccounterapp (accessed 2026-10-11).
- [MW] BabyDragon developer homepage: https://babydragondevelopment.com/ (accessed 2026-10-11). Its linked Mox detail page timed out, then returned 404: https://babydragondevelopment.com/our-subjects/magic-the-life-counter/ . No extra capability inferred.


Each cell's source is dated 2026-10-11 in the register. U means precise behavior unverified after reading that app's sources. `Build` preserves the mandated baseline; `Adapt` means independently build with the stated behavior refinement; `Skip` means recommend omitting the optional extra from initial scope, subject to Pat's gate ruling.

| Feature | LifeElk | Lifetap | Lotus | Mox | Recommendation | Reason |
|---|---|---|---|---|---|---|
| 1 player | iOS yes [EA] | Yes [T] | U [L] | Solo EDH [M] | Build | Practice mode required |
| 2, 3, 4, 5, 6 players | Up to 6 [E] | 2-6 [T] | 2-10 [L] | 2-6 [M] | Build | Every required table size |
| Seat-oriented panels | Rotated [EW] | Layouts/camera iOS [TA] | Rotated [L] | Layouts [M]; rotation U | Adapt | Seat mode plus operator-facing desktop mode |
| Tap +1/-1 | One [EW] | Tap [TW]; unit U | Buttons [LW]; unit U | Tap [M]; unit U | Build | Predictable fast adjustment |
| Hold auto-repeat | Fast counting [EW] | Accelerates [TW] | Long tap +10/-10 [LW] | Fast adjustment [M] | Adapt | Repeat with visible delta, cancel on release |
| Configurable step | U [E] | U [T] | Fixed hold-10 [LW]; configuration U | U [M] | Build | Explicit baseline |
| Starting life 20/25/30/40 | All described [EW] | All [T] | Custom [L]; presets U | Custom [M]; presets U | Build | Format setup |
| Custom starting life | Yes [E] | Yes [TW] | Yes [L] | Yes [M] | Build | House rules |
| Damage per opposing commander | Per opponent [EW] | Per source [TW] | Multiple sources [LW] | Opponents [M] | Build | Correct loss attribution |
| Partners tracked separately | U [E] | Partners [T] | Partners [L] | Partners [M] | Build | Separate commander identity |
| Optional auto life deduction | iOS setting [EA] | User review [T]; option U | U [L] | U [M] | Adapt | Toggle prevents accidental double subtraction |
| Poison | Infect [E] | Infect [T] | Yes [L] | Yes [M] | Build | Loss condition |
| Energy | Yes [E] | Yes [T] | Yes [L] | Yes [M] | Build | Baseline counter |
| Experience | U [E] | Yes [T] | Yes [L] | Yes [M] | Build | Baseline counter |
| Rad | U [E] | Yes [TW] | U [L] | U [M] | Build | Baseline counter |
| Commander tax | U [E] | Yes [T] | Yes [L] | Yes [M] | Build | Per commander casts and tax |
| Storm | U [E] | Yes [T] | Yes [L] | U [M] | Build | Turn scoped |
| User-defined counters | U [E] | U [T] | Custom [LW] | U [M] | Build | Deck-specific needs |
| Hide each counter | Extras hidden [E] | U [T] | Toggle counters [LW] | U [M] | Adapt | Visibility per seat and counter |
| Monarch | U [E] | Yes [T] | Yes [L] | Yes [M] | Build | Single shared holder |
| Initiative | U [E] | Yes [T] | Yes [L] | Yes [M] | Build | Holder plus editable state |
| Day/Night | U [E] | Yes [T] | Yes [L] | Yes [M] | Build | Shared game state |
| City's Blessing | U [E] | Yes [T] | U [L] | Yes [M] | Build | Per player state |
| Dice any sides | Standard [EW]; custom U | Custom sides [TW] | Custom [L] | Dice [M]; custom U | Build | Validated arbitrary sides |
| Dice any count | U [E] | U [TW] | Multiple [LW]; limit U | U [M] | Adapt | Choose count with practical display limits |
| Coin flip | Yes [E] | Yes [T] | Yes [L] | Yes [M] | Build | Baseline randomizer |
| Random first player | Yes [EW] | Player picker [T] | High roll [LW] | U [M] | Adapt | Fair selection plus optional high roll |
| Seat randomizer | U [E] | Yes [T] | U [L] | U [M] | Build | Baseline setup tool |
| Turn counter | U [E] | iOS count-only [TA] | Turn tracker [LW] | U [M] | Build | Separate turns from timing |
| Turn timer | U [E] | Multiple modes [T] | Per player timing [LW] | U [M] | Adapt | Pause/resume, warnings, undo advancement |
| Game timer | U [E] | U [T] | Pausable stopwatch [LW] | U [M] | Build | Persistent elapsed game time |
| Per-player backgrounds | Colors [EW] | Art/colors [T] | Images/colors [L] | Colors/landscapes [M] | Adapt | Readable overlay and low-motion fallback |
| Card-art background | U [E] | Scryfall [TW] | Custom image [LW] | Avatars/landscapes [M]; arbitrary art U | Adapt | Reuse own DB/cache, preserve attribution |
| Game-over detection | U [E] | U [T] | Kill/unkill [LW]; automatic U | U [M] | Adapt | Explain threshold, allow overrides |
| Undo life/counter changes | U [E] | U [T]; timer undo iOS [TA] | U [L] | U [M] | Build | Reversible events prevent miscounts |
| Autosave | Yes [E] | Crash-safe [T] | Progress saved [LW] | Profiles [M]; game state U | Build | Atomic local event/state writes |
| Resume after restart | Yes [EW] | Yes [T] | Progress saved [LW] | U [M] | Build | No lost match |
| Game log | U [E] | Turn history [TA] | Every life change [L] | U [M] | Adapt | Append-only events, undo recorded explicitly |
| Keep screen awake | U [E] | U [T] | U [L] | U [M] | Build | Opt-in during active match |
| Deck linked to seat, W/L | U [E] | Deck stats [TW]; binding U | Winner/history [L]; deck binding U | Profile [M]; W/L U | Build | Own deck IDs and match results |
| Foldable tabletop posture | U [E] | U [T] | U [L] | U [M] | Build | Hinge-aware required layout |
| Offline life-counter operation | Local storage; full offline U [E] | Local policy; full offline U [T] | Full offline U [L] | Full offline U [M] | Build | No reference proves entire baseline offline |
| Tablet sizing | Phones/tablets [E] | Tablets [T] | U [L] | Mobile layout [M]; tablet U | Build | Adaptive sizes already required |
| Format/team presets | All formats [E] | Three-Headed Giant [T] | Format history [L] | EDH [M] | Adapt | Local presets; explicit team rules if accepted |
| Random player/opponent | First player [EW]; opponent U | Both [T] | High roll [LW]; opponent U | U [M] | Adapt | Exclude eliminated seats and record selection |
| 3/5-player layout picker | U [E] | iOS picker [TA] | Layout choice [L] | Layouts [M]; picker U | Adapt | Seat arrangement should fit actual table |
| Timer undo | U [E] | iOS long press [TA] | U [L] | U [M] | Adapt | Correct turn and priority advances |
| Shared profile transport | U [E] | U [T] | Share profiles [L] | U [M] | Adapt | Local export/import, cloud deferred |
| Over 6 players | Ceiling 6 [E] | Ceiling 6 [T] | Up to 10 [L] | Ceiling 6 [M] | Skip | Beyond approved 1-6 baseline |
| Saved player profiles | U [E] | Names/art/colors [T] | Names/art/colors [L] | Avatars/colors/commander [M] | Adapt | Reusable setup stored locally |
| Two commander artworks | U [E] | Partners/backgrounds [T] | Partners [L]; dual art U | Partners [M]; dual art U | Adapt | Clear commander source selection |
| Mana pool | U [E] | Yes [T] | Yes [L] | U [M] | Adapt | Typed custom preset, reset policy explicit |
| Treasure | U [E] | Yes [T] | U [L] | Yes [M] | Adapt | Optional counter preset |
| Charge | U [E] | U [T] | Yes [L] | U [M] | Adapt | Optional counter preset |
| Blood/Food/Clue/Ring | U [E] | U [T] | Custom [LW]; named U | Named [M] | Adapt | Optional presets without UI clutter |
| Planeswalker/Saga/Siege | U [E] | U [T] | Custom [LW]; named U | Named [M] | Adapt | Object-specific counters, avoid false player semantics |
| Direct numeric life input | U [E] | U [T] | Keypad [LW] | U [M] | Adapt | Confirm exact total and record delta |
| Eliminate/restore player | U [E] | U [T] | Kill/unkill [LW] | U [M] | Adapt | Recover mistakes without resetting table |
| Defeat/victory messages | U [E] | U [T] | Custom [LW] | U [M] | Skip | Optional cosmetic scope |
| Planechase | U [E] | Yes [T] | Yes [L] | U [M] | Adapt | Optional data-driven game module, Pat ruling |
| Archenemy | U [E] | Yes [T] | Yes [L] | U [M] | Adapt | Optional module, no competitor asset copying |
| Bounty | U [E] | OTJ [T] | Yes [LW] | U [M] | Adapt | Optional versioned rule/cards module |
| Custom variant decks/presets | U [E] | iOS five presets [TA] | U [L] | U [M] | Adapt | Local selection if variant module accepted |
| CSV/JSON game export | U [E] | iOS export [TA] | Full backup [L]; format U | U [M] | Adapt | Own stable event schema, exclude secrets |
| Game history search | U [E] | Yes [TA] | History [L]; search U | U [M] | Adapt | Filter by player/deck/date |
| Draw results | U [E] | iOS W/D/L [TA] | U [L] | U [M] | Adapt | Do not force every game to have winner |
| Countdown/chess clock/alerts | U [E] | Yes [T] | Timer [LW]; modes U | U [M] | Adapt | Optional modes reuse baseline timer model |
| Camera/operator orientation | U [E] | iOS camera [TA] | Spectator upright [LW] | U [M] | Adapt | All upright on desktop/operator mode |
| Drag reorder seats | U [E] | Yes [TW] | Spectator ordering [LW] | U [M] | Adapt | Preserve identities, commander relations |
| Art crop/contain/cover | U [E] | iOS controls [TA] | Custom [LW]; crop U | U [M] | Adapt | Readable responsive art |
| Languages | iOS 10 [EA] | 12 [T] | U [L] | U [M] | Adapt | Localization-ready architecture; languages Pat rules |
| OLED/dark appearance | U [E] | iOS option [TA] | Dark [L] | U [M] | Adapt | Use app-wide light/dark tokens |
| Disable dice animation | U [E] | iOS setting [TA] | U [L] | U [M] | Adapt | Respect reduced-motion preference |
| Background music | U [E] | U [T] | U [L] | Themed [M] | Skip | Licensing/audio scope with low utility |
| Flavor text in menu | U [E] | U [T] | U [L] | Random [M] | Skip | Not core life-counter function |
| Companion/shop promotion | Token shop iOS [EA] | U [T] | Related sites [LW] | Separate brewer [MW] | Skip | Avoid unrelated promotion |
| Card search/prices/legalities | U [E] | iOS Scryfall query [TA] | Yes [L] | U [M] | Adapt | Reuse Command Library database/search |
| Live TV/spectator sharing | U [E] | U [T] | Code/QR [LW] | U [M] | Skip | Network shared state deferred |
| Shared recap links/voting/chat | U [E] | U [T] | Yes [LW] | U [M] | Skip | Online scope deferred; local history retained |
| Account/cloud sync | U [E] | Policy mentions optional; shipped U | Account sync [L] | U [M] | Skip | Sync post-release per brief |
| Full backup/import | U [E] | CSV/JSON iOS [TA]; full restore U | New backup [L] | U [M] | Build | Integrate required app backup, not account dependency |
| Ads/paid ad removal | IAP [E]/[EA] | No ads [TW] | No ads [L] | Premium ads [M] | Skip | No ad system needed for personal app |
