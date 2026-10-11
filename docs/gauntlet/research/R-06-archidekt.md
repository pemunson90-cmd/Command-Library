# R-06 Archidekt feature inventory and import

Research date: 2026-10-11. Status: complete with explicit verification limits. All linked sources below were accessed 2026-10-11; the date applies to each finding and parity row. Unconfirmed capabilities are UNVERIFIED, not asserted absent. Historical announcements establish shipped features at their publication, not an exhaustive test of today's UI.

## Question

Which Archidekt features should Command Library build, adapt or skip, and can it import decks by URL with established permission?

## Findings

### Features and evidence quality

The parity matrix below covers every requested deckbuilding item. Recommendations are Command Library design judgments based on the brief, not copied implementation or permission to reuse Archidekt assets. Primary help pages and team announcements support most editor/analysis features. Account-only export controls were not exercised; community requests are not treated as proof of a shipped feature.

- Official Deck Stats announcement documents color production/cost, configurable land-only production, full and grouped curves, probability controls and quantity breakdowns. It also links stats selections back to editable cards. Source: https://archidekt.com/news/9287631 (accessed 2026-10-11).
- Versions and comparisons were actually released, not merely promised: named snapshots and comparison by card differences. Restore was subsequently shipped. Sources: https://archidekt.com/news/3047466 and https://archidekt.com/news/3189294 (accessed 2026-10-11).
- Multiple card categories and primary/secondary categories are documented separately from deck-level tags, which initially used a curated vocabulary. Sources: https://archidekt.com/news/660198 and https://archidekt.com/news/3189294 and https://archidekt.com/news/4256905 (accessed 2026-10-11).
- The latest interaction simulator announcement describes configurable hypothetical attacks/removal/counterspells after a turn, with the player resolving outcomes manually. It is not an automated full-rules opponent. Source: https://archidekt.com/news/26104256 (accessed 2026-10-11).
- Paid feature confirmed: FAQ says Patreon at $2 or higher can remove advertisements. UNVERIFIED: current complete paid-benefit/tier list; no inference that core tools require payment. Source: https://archidekt.com/faq (accessed 2026-10-11).

### Terms, API permission and robots

The terms page states an effective date of September 7, 2018. Relevant exact clauses:

> "use software or automated agents or scripts to produce multiple accounts on the Site, or to generate automated searches, requests, or queries to the Site."

This appears under actions users agree not to perform. The access license is:

> "solely for your own personal, noncommercial use."

The restriction additionally states:

> "you shall not access the Site in order to build a similar or competitive website"

The terms also restrict copying/reproduction/download unless indicated otherwise and prohibit collecting other users' information without consent. These restrict assumptions about scraping and importing public user descriptions. Source: https://archidekt.com/terms (accessed 2026-10-11).

There is material counterevidence, not merely silence about permission. Michael's older developer response says:

> "our API is open and public (as far as reading is concerned)."

It asks for link-back when data is posted publicly, credits for brewers and restraint to avoid excessive load, and explicitly disclaims maintenance of the linked third-party npm wrapper. Source: https://archidekt.com/forum/thread/40557 (accessed 2026-10-11; page labels it seven years old).

A later developer reply says:

> "You're more than welcome to use our API for whatever you want (just know that you might hit our rate limiter if you hit it too hard)."

The same reply explains the absence of maintained API documentation and version guarantees. Source: https://archidekt.com/forum/thread/2832338 (accessed 2026-10-11; page labels it four years old).

Finding: a public read API was explicitly welcomed historically, but there is no verified maintained public API specification or current scoped exception reconciling this with the automated-request terms clause. Do not call it definitively forbidden or definitively terms-compliant. UNVERIFIED: current supported endpoint/schema, auth policy, numerical rate limit, third-party-client contract and permission for this particular integration. Searches used Archidekt FAQ/news and the API discussions above; no endpoint was guessed or called. Recommended ruling: guided export now, optional public URL import only after current clarification resolves this contradiction. Source URLs and access date: the three terms/API pages above, 2026-10-11.

UNVERIFIED: robots directives. Opening https://archidekt.com/robots.txt returned Internal Error in the web fetcher (accessed 2026-10-11). Robots instructions, if retrieved later, would not alone grant reuse/API rights. No robots text is invented.

### Export-then-paste and file evidence

Verified human-operated fallback:

1. Open the intended deck in Archidekt. Find Export among deck controls/shortcuts. The historical toolbar locations changed, so instructions should name the control rather than promise one fixed screen position. Sources: https://archidekt.com/news/2939828 and https://archidekt.com/news/7305181 (accessed 2026-10-11).
2. Choose Text or CSV and inspect whether the selected boards/categories and printing fields match the intended import. Text and configurable CSV are evidenced by official release notes, but the complete current selector labels are UNVERIFIED. Sources: https://archidekt.com/news/885575 and https://archidekt.com/news/2890329 and https://archidekt.com/news/2939828 (accessed 2026-10-11).
3. Download the result, then open/share the file into Command Library or paste its text. On desktop Archidekt also documents Ctrl+C for a simple deck list and Ctrl+Shift+C for its full syntax. Source: https://archidekt.com/news/3189294 (accessed 2026-10-11). File/paste destination is the proposed Command Library flow, not an existing Archidekt integration.
4. Command Library should preview card resolution, quantities, boards, printings and finish before committing; retain unknown lines as reported errors. This is a proposed import acceptance rule. The reason is Archidekt's documented richer category/printing syntax, not a verified universal serialization contract. Sources: https://archidekt.com/news/3189294 and https://archidekt.com/news/2890329 (accessed 2026-10-11).

Exact documented text input example copied from Archidekt's own release note:

```text
1x Forest (m13) 247
```

This is a documented text-editor/import example, not a captured current export. It proves quantity, card name, set and collector-number syntax; it does not prove categories/foil serialization. Source: https://archidekt.com/news/11650 (accessed 2026-10-11).

A user report quotes this export format selector example:

```text
1x Card Name (code) *F* [Category] ^Label,#000000^
```

Source: https://archidekt.com/forum/thread/6024990/1 (accessed 2026-10-11). Evidence class: secondary user testimony hosted on Archidekt. This is template text, not a real card row or confirmed current normative grammar. Do not invent delimiter escaping, multiple-category separators or finish semantics from it.

CSV: official announcements confirm optional collector number and foiling for deck export, salt as another deck field, and prices/date-added for collection export. Sources: https://archidekt.com/news/2890329 and https://archidekt.com/news/2939828 and https://archidekt.com/news/3678345 (accessed 2026-10-11). UNVERIFIED: exact current CSV headers, order, delimiter/escaping, category encoding, finish values and an authentic exported CSV sample. No actual CSV bytes were available from the checked public docs, and no private export endpoint was invoked. Therefore there is intentionally no fabricated CSV sample. R-16 must obtain a human-exported fixture before promising lossless CSV compatibility.

Import formats: Archidekt FAQ tells users to consult Formatting Examples in Import Cards, paste text and Save Changes. Arena import and the collector-number example are documented in Changelog 0.7.0; full syntax/detailed add is documented later. UNVERIFIED: complete current import/export menu formats, including any MTGO-specific mode or current Arena export details. Sources: https://archidekt.com/faq and https://archidekt.com/news/11650 and https://archidekt.com/news/3189294 (accessed 2026-10-11).

## Parity matrix

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

## Options

1. Build the local deck tools and support user-exported text/CSV. Recommended now. Evidence: the feature/export sources above (accessed 2026-10-11).
2. Add public URL import after current API permission and a stable-enough contract are established, with conservative request limits and provenance. Historical developer permission makes this plausible, but does not erase the contradictory terms. Sources: https://archidekt.com/forum/thread/40557 and https://archidekt.com/forum/thread/2832338 and https://archidekt.com/terms (accessed 2026-10-11).
3. Depend on undocumented authenticated APIs or scrape public decks wholesale. Skip: unnecessary for user-directed import, absent verified current permission, and fragile. Same sources, accessed 2026-10-11.

## Recommendation

Build the required native deckbuilder, adapting Archidekt's categories, analysis, snapshots/diffs and manual playtesting to offline Android/Windows. Include all brief-required rows even where reference support is UNVERIFIED. Prefer guided text/file import at release; leave public URL import conditional on resolving the documented permission conflict. No website UI/assets are copied. Sources: parity matrix and terms/API findings, accessed 2026-10-11.

## Risks

- Exact CSV bytes and the current complete export selector remain UNVERIFIED. An authentic human export is required before R-16 can claim lossless support; do not treat the copied input example as an exported fixture. Sources: export findings above, accessed 2026-10-11.
- Developer invitations and general terms conflict. No numerical API rate or current special exception was confirmed. Sources: https://archidekt.com/forum/thread/2832338 and https://archidekt.com/terms (accessed 2026-10-11).
- Reference legality, probability implementation, paid-tier boundaries and current bracket algorithm were not exhaustively tested. Documented historical feature support is distinguished from exact present behavior throughout the matrix. Sources: corresponding matrix links, accessed 2026-10-11.

## Confidence

High for shipped categories, analysis, version comparison and playtester features from primary announcements. Medium for current menu details and exhaustive validation. Low for exact current CSV serialization and for claiming current URL-import permission. All scope items have a finding or explicit UNVERIFIED entry. No conflict with the brief's mandated export-then-paste fallback; historical API permission is an option for later clarification, not a blocker to native development.
