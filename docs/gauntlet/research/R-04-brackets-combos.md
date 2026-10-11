# R-04 Commander Brackets, Game Changers and combo data

Path: `docs/gauntlet/research/R-04-brackets-combos.md`. Research access date: 2026-10-11. Publication dates below are source dates, not dates inferred from search freshness. Status: complete evidence inventory with explicit live-data verification gaps. Shell network proxy was unavailable; web connector retrieved primary documentation, while Scryfall returned 403 and the live Spellbook schema was inaccessible. No bulk dataset or current API response was measured.

## Question

What official Commander Brackets rules and machine-readable Game Changers sources should Command Library use, and which combo data can be cached offline under documented terms?

## Findings

### F1. Official sources and revisions

The live Wizards Commander format page still identifies five brackets in beta. It makes intent primary and confirms Game Changer limits of zero in 1/2, three in 3, unlimited in 4/5. No newer official structural revision was found in domain-restricted searches through access date; absence of a search result does not prove a complete revision history. Source: https://magic.wizards.com/en/formats/commander (accessed 2026-10-11, live page has no displayed publication date).

| Published | Confirmed change | Primary source, accessed 2026-10-11 |
|---|---|---|
| 2025-02-11 | Beta launch: five named brackets, Game Changers, two-card infinite combos, extra turns, mass land denial and tutor guidance. Article says initial list has 40 cards; that is a historical author assertion, not a current measurement. | https://magic.wizards.com/en/news/announcements/introducing-commander-brackets-beta |
| 2025-04-22 | No bracket structural changes. Removed Trouble in Pairs and Trinisphere; added 18 named cards and five unbanned cards: Braids, Cabal Minion; Coalition Victory; Gifts Ungiven; Panoptic Mirror; Sway of the Stars. Emphasized intent and ability to bracket upward. | https://magic.wizards.com/en/news/announcements/commander-brackets-beta-update-april-22-2025 |
| 2025-10-21 | Added expected-turn guidance; Core separated from precon benchmark; removed tutor restrictions. Removed ten Game Changers: Expropriate, Jin-Gitaxias/Core Augur, Sway of the Stars, Vorinclex/Voice of Hunger, Kinnan, Urza, Winota, Yuriko, Deflecting Swat, Food Chain. Clarified Exhibition theme and Rule Zero flexibility. | https://magic.wizards.com/en/news/announcements/commander-brackets-beta-update-october-21-2025 |
| 2026-02-09 | No bracket-level changes. Farewell AND Biorhythm added; Lutri explicitly not added. Hybrid color-identity proposal shelved. | https://magic.wizards.com/en/news/announcements/commander-brackets-beta-update-february-9-2026 |

The June/July 2025 and May/June 2026 check-ins were announced as plans in official articles; a plan is not proof of a published revision. Full intervening/later revision history is UNVERIFIED after searching Wizards for those dates and checking the live format page. December 11, 2025 panel rotation is not established as a bracket rule revision. Sources: the April/February articles above and https://magic.wizards.com/en/news/announcements/commander-format-panel-update-december-11-2025 (accessed 2026-10-11).

GAUNTLET section 10: October tutor/removal statements are confirmed. Its third-party 53-card number is not confirmed and must not be a constant. The February official update includes Biorhythm as well as Farewell, which a Farewell-only update misses. Current exact count is UNVERIFIED, including agreement between Wizards, Scryfall and MTGJSON. Source: February 9 article above (published 2026-02-09, accessed 2026-10-11).

### F2. Criteria and automation

The tables summarize criteria with source links; they do not reproduce entire copyrighted articles. Full verbatim transcription requested by the task card is not included. The brief official turn wording is "at least six turns before you win or lose" for Upgraded; Wizards explains this means being satisfied with an ending on someone's seventh turn. Source: October 21 article above (published 2025-10-21, accessed 2026-10-11). Verbatim full criteria remain a source-link reference item, rather than a fabricated quotation.

| Bracket | Current intent/expectation summary | Automation classification |
|---|---|---|
| 1 Exhibition | Theme first, unusual deckbuilding and thematic/weak wins; pod-agreed legality flexibility; showcase deck, nine turns before ending. | Intent-based; user declares theme and Rule Zero exceptions. |
| 2 Core | Straightforward/unoptimized; visible, incremental, interruptible wins; creative/social, considerate proactive play; eight turns before ending. | Intent-based; no card-only proof of style or pace. |
| 3 Upgraded | Synergy/high card quality, disruption, value-engine/finisher Game Changers, accrued resources enabling a large turn; six turns before ending. | Partly automatable evidence; speed/intent remains user judgment. |
| 4 Optimized | Fast, consistent, efficient lethal play, strong mana/engines/disruption/tutors, explosive threats; not cEDH metagame; four turns before ending. | Partly automatable indicators; cannot classify intent or consistency conclusively. |
| 5 cEDH | Tournament-metagame design, efficient consistent wins, advanced play and victory priority; could end on any turn. | Intent-based; user identifies competitive context. |

Source for expectation rows: https://magic.wizards.com/en/news/announcements/commander-brackets-beta-update-october-21-2025 (published 2025-10-21, accessed 2026-10-11). These turn anchors are expectations, not guaranteed simulations or permission to hold a routinely early combo until a later turn.

| Barometer | Applicable guidance | Classification and needed evidence |
|---|---|---|
| Game Changers | 1/2 exclude; 3 maximum three; 4/5 unlimited, subject to normal legality and pod exceptions | Automatable count: Oracle identity, quantities and versioned official membership, including commander. |
| Mass land denial | Excluded in 1 through 3; allowed in 4/5 | Partly automatable: curated effects/card identities and known combinations; strategy/context requires judgment. |
| Two-card infinite combos | No intentional combos in 1/2; no intentionally early combos in 3; unrestricted 4/5 | Partly automatable: variants, card quantities, commander, prerequisites, zones, outcomes and mana; intent/frequency/assembly turn uncertain. |
| Extra turns | 1 excludes; 2/3 low quantities without sequential chains/loops; unrestricted 4/5 | Partly automatable: card-effect tags plus loop variants. Low quantities is not a newly verified official numeric threshold. |
| Tutors | October revision removed general tutor restrictions; efficient tutors may still count as Game Changers | No tutor-count gate. Tagging useful for consistency evidence only. |
| Commander legality | Standard banned list/color identity/deck rules still apply unless table agrees a Rule Zero exception | Automatable standard checks; pod exception needs user declaration. |

Sources: https://magic.wizards.com/en/news/announcements/introducing-commander-brackets-beta (published 2025-02-11, accessed 2026-10-11) for original barometers; https://magic.wizards.com/en/formats/commander (accessed 2026-10-11) for current Game Changer caps; October article above for tutor removal. Source interpretation: original barometers retained except announced changes; precise current dynamically-rendered full barometer text is UNVERIFIED because live format text extraction exposes only overview and Game Changer caps.

### F3. Machine-readable Game Changers and agreement test

- Official authority is Wizards' live Game Changers section and dated update articles. The format page list entries are dynamically rendered and were not present in extracted text. Current official set/count is UNVERIFIED; the dated October card-name list and February additions can seed a reviewed reconstruction, but that is not proof of the live complete set. Sources: https://magic.wizards.com/en/formats/commander and the October/February articles in F1 (accessed 2026-10-11).
- Scryfall `is:gamechanger` search and card Boolean `game_changer` are expected integration candidates, but primary documentation/live responses are UNVERIFIED in this pass: https://scryfall.com/docs/api/cards, https://scryfall.com/docs/syntax and https://scryfall.com/search?q=is%3Agamechanger were inaccessible/403 on 2026-10-11. Third-party SDK representations support the lead, but do not satisfy the primary-source acceptance test.
- MTGJSON explicitly documents optional Boolean `isGameChanger` on Card Atomic, introduced v5.2.2; Set/Deck schemas also expose it. Omitted optional fields must be handled according to verified producer semantics, not blindly asserted true or false. Source: https://mtgjson.com/data-models/card/card-atomic/ and https://mtgjson.com/changelogs/mtgjson-v5/ (accessed 2026-10-11).
- Required agreement check, proposed procedure not a completed result: resolve official names to Oracle identities; fetch Scryfall search with `unique=cards`, paginate all results; compare against true flags in a same-date Oracle bulk snapshot; collect MTGJSON true flags and map identifiers to Oracle identities; deduplicate alternate faces/printings; report cardinalities, hash of sorted sets and symmetric differences. Record official effective date, each upstream generated date and access timestamp independently. Until performed, no agreement claim or current count is accepted. Schema sources: Scryfall/MTGJSON URLs above (accessed/attempted 2026-10-11).

### F4. Other card-effect data

Wizards defines mass land denial by repeated destruction/exile/bounce, tap restrictions or changed mana affecting several lands, with examples Armageddon, Ruination, Sunder, Winter Orb and Blood Moon. Card text matching alone misses persistent tap/mana replacement and multi-card denial strategies. No complete maintained official machine-readable denial/extra-turn/early-combo list was found. Source: https://magic.wizards.com/en/news/announcements/introducing-commander-brackets-beta (published 2025-02-11, accessed 2026-10-11).

Moxfield's own announcement says it maintains reference pages for Game Changers, mass land denial, tutors and extra turns. They are community lists, not new official rules; live list URLs, membership exports and caching terms are UNVERIFIED after inspecting the announcement/searching primary help. Source: https://bsky.app/profile/moxfield.com/post/3lhyxswvxcs2h (accessed 2026-10-11, launch-era post; exact publication date not retrieved).

Spellbook cards/variants expose classifications and outcomes useful for denial, extra turns and combo detection. Scryfall Oracle Tagger and Oracle-text searches are approximation candidates; actual tag identifiers, complete maintained tag membership and bulk tag redistribution terms remain UNVERIFIED because primary Scryfall documentation was blocked. Do not invent `otag` values or an endpoint. Extra-turn text searches can be tested locally against Oracle text, but remain app heuristics rather than official lists. Sources: https://spacecowmedia.github.io/commander-spellbook-backend/domain-model.html and https://raw.githubusercontent.com/SpaceCowMedia/commander-spellbook-backend/master/backend/spellbook/views/estimate_bracket.py (accessed 2026-10-11); attempted https://scryfall.com/docs/syntax (2026-10-11).

### F5. Commander Spellbook API and offline bulk data

Official About links backend REST API at `https://backend.commanderspellbook.com`. Published routes include `GET /variants/`, `/cards/`, `/features/`, `/templates/`, `/variant-aliases/`; GET/POST `/find-my-combos` and `/estimate-bracket`. Live schema/response status is UNVERIFIED after connector schema retrieval failed, although routes are corroborated by upstream code. Sources: https://commanderspellbook.com/about/ and https://raw.githubusercontent.com/SpaceCowMedia/commander-spellbook-backend/master/backend/spellbook/urls.py (accessed 2026-10-11).

Bulk files are officially documented at https://json.commanderspellbook.com/variants.json.gz (preferred) and https://json.commanderspellbook.com/variants.json. Document includes `timestamp`, `version`, `variants`, `aliases`; periodically refreshed. Official consumer instructions favor local bulk reads over exporting all API pages, require identifiable User-Agent, credit/link and handling 429. They say 80 requests/minute should be safe. Exact refresh schedule/SLA, compressed/uncompressed size, count, checksum and Range support are UNVERIFIED: no shell network measurement possible; periodic is not daily. Source: https://spacecowmedia.github.io/commander-spellbook-backend/api.html (accessed 2026-10-11).

Variants describe concrete recipes: `uses`, `requires`, `produces`, `of`, `includes`; card/template ingredient quantities, start zones/states and commander requirements; identity, mana needed, easy/notable prerequisites, description, notes, popularity, salt, `bracketTag`, legalities and prices. HTTP middleware changes serializer snake_case to camelCase. Example variants can omit execution/prerequisite values; importer must accept documented nullable fields. Sources: https://raw.githubusercontent.com/SpaceCowMedia/commander-spellbook-backend/master/backend/spellbook/serializers/variant_serializer.py and https://spacecowmedia.github.io/commander-spellbook-backend/domain-model.html (accessed 2026-10-11). Documentation/code are current upstream snapshots, not a production schema pin.

### F6. Bracket estimator methods and other sources

Spellbook `/estimate-bracket` returns `bracketTag`, classified cards and classified combos, including two-card certainty and speed/denial/turn/lock flags. It checks actual included variants with commander-aware counting. Speed uses execution-mana thresholds and uncertainty adjustment; it is not a simulation of turn-to-win. Code maps Ruthless to 4; Spicy/Powerful to 3; Oddball/Core to 2; Exhibition to 1; banned separately, without a cEDH 5 distinction. Four Game Changers, two extra-turn cards, turn loops or land denial can trigger Ruthless. The numeric two-turn-card threshold is Spellbook's heuristic, not an official numeric definition. Sources: https://raw.githubusercontent.com/SpaceCowMedia/commander-spellbook-backend/master/backend/spellbook/views/estimate_bracket.py and https://raw.githubusercontent.com/SpaceCowMedia/commander-spellbook-backend/master/backend/spellbook/models/variant.py (accessed 2026-10-11).

| Estimator/source | Verified reference behavior and limit | Source, accessed 2026-10-11 |
|---|---|---|
| Archidekt | Official development announcement adds estimated bracket on Commander deck page and manual identification/display support. Exact current algorithm, combo-source integration, revision handling and permission to reuse internals UNVERIFIED. | https://archidekt.com/forum/thread/11356226?page=1 |
| Moxfield | Own announcement exposes classification-list reference pages. Wizards acknowledges its overall estimates; exact current estimator algorithm/API/terms UNVERIFIED. | https://bsky.app/profile/moxfield.com/post/3lhyxswvxcs2h and April 2025 Wizards article in F1 |
| EDHREC combos | Spellbook About identifies EDHREC combo feature as powered by Spellbook, so it is not an independent database for coverage/license comparison. EDHREC standalone bracket algorithm and offline export grant UNVERIFIED after combos page inspection. | https://commanderspellbook.com/about/ and https://edhrec.com/combos |
| TableCommander | Own article describes card categories plus combos and competitive intent, calls output guidance, and distinguishes B4/B5 by field/intent. Its implementation is a reference, not an official rules source or licensed dataset. Exact export/license UNVERIFIED. | https://tablecommander.com/blog/bracket-estimates-rebuilt |

No alternative with a verified, independent, comprehensive combo bulk dataset and explicit offline-use instructions was established. Historical Google Sheets wrappers are not accepted as a current API contract. Sources compared: Spellbook About/API docs and EDHREC combos above (accessed 2026-10-11).

### F7. Terms, permissions and attribution

- Spellbook source code is MIT. License covers software and associated documentation, with preservation of copyright/permission notice. Quote: "The above copyright notice and this permission notice shall be included". Do not infer that this source-code license independently licenses all Wizards card material or community database prose. Source: https://raw.githubusercontent.com/SpaceCowMedia/commander-spellbook-backend/master/LICENSE (accessed 2026-10-11).
- Offline data use has separate direct documentation: "Fetch it on a schedule of your own and read it locally." This is explicit bulk-local-consumption instruction; use that as the basis for personal-use offline cache, with required credit/backlinks. Separate sublicense/commercial redistribution grant for dataset prose is UNVERIFIED after About/API/license review. Source: https://spacecowmedia.github.io/commander-spellbook-backend/api.html (accessed 2026-10-11).
- Spellbook labels itself unofficial Fan Content and acknowledges Wizards-owned material. Source: https://commanderspellbook.com/about/ (accessed 2026-10-11). Retain attribution and per-combo canonical source link; do not claim official endorsement.
- MTGJSON license page applies its MIT terms to website/content and requires notices: "The above copyright notice and this permission notice shall be included". It does not grant ownership of underlying Wizards art or marks. Source: https://mtgjson.com/license/ (accessed 2026-10-11).
- Scryfall data/tag terms are UNVERIFIED here: primary API/syntax pages returned 403. Third-party copied no-paywall statements were not treated as a verified current permission. Follow R-02's primary-source terms findings if independently established. Sources attempted: https://scryfall.com/docs/api and https://scryfall.com/docs/syntax (2026-10-11).

### F8. Refreshable versioned data proposal

Implementation recommendation based on F1/F3/F5 sources (accessed 2026-10-11), not a claim of an existing endpoint: separate rule bundle, official Game Changer identity set, community effect lists and combo snapshot. Each records schema version, source URL, publication/effective date, upstream generated timestamp, retrieval timestamp and SHA-256. Rules require reviewed updates from official publications; APIs update card flags/combo data. Validate identities and differences before atomically switching snapshots; retain last good data and migration history. Display separate rules-as-of and combo-data-as-of dates, because they change independently. Test disagreement without overwriting reviewed official membership. Index card-to-variant relations for offline detection; do not load a whole bulk JSON document into memory. Store per-criterion evidence and confidence, with user intent overrides; preserve normal legality checks and explain Rule Zero exceptions separately.

## Options

1. Recommended: reviewed official rule/membership bundles, local Spellbook bulk combo cache and explainable app-owned estimate. Documented offline bulk consumption supports this; user confirms intent.
2. Online Spellbook estimator: useful cross-check and sparse enrichment, but offline requirement still needs a local engine and its output is a heuristic without bracket 5.
3. Provider website-only links: terms-light fallback for enrichment, but insufficient for the requested offline combo detection.

These judgments follow F1 through F7 sources (accessed 2026-10-11).

## Recommendation

Use official Wizards rules as reviewed refreshable data, Game Changer set reconciliation before activation and Commander Spellbook's documented S3 bulk feed for attributed offline caching. Build an estimate with visible evidence and uncertainty; never present it as an official rank or infer cEDH from card counts. Remove old tutor-count penalties. Correct the February update narrative to include Biorhythm. Do not promise a current 53/54 count until all source sets are actually compared. Sources: F1, F3, F5 to F7 (accessed 2026-10-11).

## Risks

- Full dynamically rendered current criteria/membership, exact count and three-source agreement remain UNVERIFIED; source publication and retrieval dates must remain distinct. Sources: F1/F3 (accessed 2026-10-11).
- Combo databases are incomplete and recipe prerequisites matter; presence of pieces does not establish intentional early wins. Sources: F2/F5/F6 (accessed 2026-10-11).
- Bracket estimator heuristics differ from official philosophy; especially extra-turn counts and cEDH intent. Source: Spellbook model code in F6 (accessed 2026-10-11).
- Bulk cache is documented; broad commercial redistribution terms, Scryfall tags/terms and current production API schema still need verification before those particular uses. Sources: F5/F7 (accessed 2026-10-11).

## Confidence

High for October tutor removal, February Farewell/Biorhythm additions, and Spellbook's explicit bulk-local instructions. Medium for engineering approach and published source-code estimator behavior. Low for current exact list counts, full post-launch revision completeness, dynamically rendered rule text, measured bulk bytes/cadence and current live API shape. All remaining scope gaps include attempted sources/methods above.
