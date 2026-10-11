# R-17: Card link URL patterns

## Question

Which card link targets and user template tokens can Command Library support without inventing vendor identifiers or fragile slugs?

## Findings

Research date: **2026-10-11 UTC**. Every source and probe below was accessed on that date. This is desktop browser/search-connector evidence, not a curl transport audit. The shell proxy is unavailable. **Every HTTP status and curl final URL is UNVERIFIED**; connector errors containing a status are not independently measured origin responses. Successful browser rendering, search indexing, and HTTP redirect measurement are separate evidence classes. No bulk download or automated crawling was performed.

### Primary identifier and link evidence

Source **S1**: https://github.com/scryfall/api-types/blob/main/src/objects/Card/CardFields.ts (2026-10-11). Official Scryfall types identify `id` as the Scryfall UUID, `oracle_id` as the cross-print card identity, `scryfall_uri` as the website permapage, and `uri` as the API object URL. `multiverse_ids` is an optional integer array; many promos and tokens have none. `tcgplayer_id` is the optional productId, `tcgplayer_etched_id` can identify a separate etched product, and `cardmarket_id` is optional idProduct. Collector numbers must remain strings. Root `name` includes both face names separated by ` // `; card faces retain their individual names. Reversible layouts can put oracle IDs on faces rather than the root. Do not substitute one vendor's identifier for another.

Source **S2**: https://github.com/scryfall/api-types/blob/main/src/objects/Card/values/RelatedUris.ts (2026-10-11). Confirmed related links: optional `gatherer`, `edhrec`, `tcgplayer_infinite_articles`, `tcgplayer_infinite_decks`. The Infinite links are articles/decks, not the purchase product. No Card Kingdom or Commander Spellbook field is listed in this type.

Source **S3**: https://github.com/scryfall/api-types/blob/main/src/objects/Card/values/PurchaseUris.ts (2026-10-11). Confirmed purchase keys: `tcgplayer`, `cardmarket`, `cardhoarder`. It describes Cardmarket links as **“Often inexact due to how Cardmarket links work.”** Individual availability must still be checked; the containing purchase object is optional in S1. Neither the type nor a field name proves inventory, currency, current price, finish selection, or permanent validity.

Offline implication, a proposed design based on S1-S3: persist supplied complete URLs with downloaded card records. Link construction then needs no live lookup; visiting the destination still needs network access. Verify that the chosen bulk dataset actually contains these fields before claiming offline availability in the app. Dataset-specific coverage is UNVERIFIED; no bulk payload was fetched here.

### Target patterns and fallbacks

The patterns describe observed examples, not guaranteed universal routing contracts. `ENC(x)` below means UTF-8 percent encoding of one query value or path segment. Complete supplied URLs are opaque links, not templates to rebuild. Dates for all rows: 2026-10-11; supporting card probes follow.

| Target | Preferred card link and identifiers | Observed pattern and naming | Search fallback and verification limit |
|---|---|---|---|
| Scryfall | Supplied `scryfall_uri` (S1). This preserves exact printing/language. | Candidate `/card/{set}/{collector_number}/{slug}` exists in inspected external examples; all three browser requests below failed. Universal slug rules and slug omission are UNVERIFIED. UUID is an API identity, not a demonstrated browser path. | Candidate `https://scryfall.com/search?q=ENC(name)` is UNVERIFIED here: official https://scryfall.com/docs/api/cards/named and https://scryfall.com/docs/api/cards/collector were inaccessible. API named/collector endpoints are not substitutes for a browser card page. |
| Gatherer | Supplied `related_uris.gatherer` (S2); otherwise an actual optional multiverse ID, never Scryfall UUID. | Historical `/Pages/Card/Details.aspx?multiverseid=N` appears in a secondary Scryfall response example; current direct-ID behavior is UNVERIFIED. | Card Kingdom's primary outbound links use `https://gatherer.wizards.com/Pages/Search/Default.aspx?name=ENC(name)`. Three requests failed in connector. Treat this as observed referral, not verified current Gatherer routing. |
| EDHREC | Supplied `related_uris.edhrec` (S2), avoiding hand-built slug. | Verified `/cards/lightning-bolt`, `/cards/bala-ged-recovery`, `/cards/fire-ice`. Lowercase/hyphens; DFC front only, split both names. Apostrophe removal observed at https://edhrec.com/cards/chandras-spitfire; comma removal at https://edhrec.com/cards/thalia-guardian-of-thraben. These examples do not establish all Unicode/punctuation behavior. | Secondary supplied example uses `/route/?cc=ENC(name)`. Browser resolution of this fallback is UNVERIFIED. No independently confirmed universal search route. |
| Commander Spellbook | Card-combo search, not a fabricated per-card page. Full name or oracle ID in documented query syntax. | `https://commanderspellbook.com/search/?q=ENC(card="NAME")`. Primary syntax https://commanderspellbook.com/syntax-guide/ documents `card=` exact versus `card:` substring, quotes, case-insensitive searches, and backslash escaping embedded double quotes. Search-expression escaping must precede URL encoding. Combo URLs identify combinations, not individual cards. | Same search endpoint is the intended target. Lightning Bolt and DFC exact queries rendered; split query failed. Oracle-ID grammar is present in syntax guide, but exact parameter spelling and a live three-card ID probe remain UNVERIFIED here; use tested name grammar initially. |
| TCGplayer | Supplied `purchase_uris.tcgplayer` (S3); actual tcgplayer_id is productId (S1). | Indexed DFC product `/product/221901/magic-zendikar-rising-bala-ged-recovery`. Product slug uses front name in this example. Whether `/product/{id}` alone is sufficient is UNVERIFIED. No general slug guarantee. | `https://www.tcgplayer.com/search/magic/product?q=ENC(name)` rendered a JavaScript shell for all three, not card results. Search correctness is UNVERIFIED. |
| Cardmarket | Supplied `purchase_uris.cardmarket` (S3); actual cardmarket_id is idProduct (S1). | Indexed `/en/Magic/Products/Singles/{set-slug}/{card-slug}` examples below use title case. DFC includes both names; split is `Fire-Ice`. Site set names are not Scryfall set codes. Browser access failed. | Secondary supplied example uses `https://www.cardmarket.com/en/Magic/Products/Search?searchString=ENC(name)` plus attribution parameters. Current fallback and any `idProduct` browser shortcut are UNVERIFIED. Preserve the supplied URI. |
| Card Kingdom | Verified vendor page URL; no CK identifier supplied by Scryfall S1-S3. | `/mtg/{vendor-set-slug}/{card-slug}`: lowercase, `2011-core-set`, `zendikar-rising`, `apocalypse`. DFC front name; split `fire-ice`. Foils and special printings can have different paths; do not infer them from finish/set code. | Indexed `https://www.cardkingdom.com/catalog/view?filter%5Bname%5D=ENC(name)&filter%5Bsearch%5D=mtg_advanced`, including Bolt and Fire // Ice. DFC indexed header search uses `/catalog/search?ac=1&filter%5Bname%5D=Bala+Ged+Recovery&search=header`. Three live search-result probes remain UNVERIFIED. |
| Cardhoarder (optional addition) | Supplied `purchase_uris.cardhoarder` (S3). Add for the MTGO purchasing use case, not as a default paper seller. | Secondary example uses `/cards?data%5Bsearch%5D=ENC(name)` with affiliate attribution. Actual MTGO IDs differ from Scryfall UUIDs (S1). | Three-card probes, direct-ID browser pattern, and fallback behavior are UNVERIFIED. The source type confirms a purchase link field, not market popularity or universal availability. |

Secondary example source, explicitly not current official policy: https://github.com/rubenrangel/scryfall-types (2026-10-11), Abu Ja'far card object. It corroborates historical Gatherer direct-ID and EDHREC route links and purchase/search attribution shapes. No production endpoint should be inferred solely from it.

### Three-card probe ledger

Cards: Lightning Bolt (normal); Bala Ged Recovery // Bala Ged Sanctuary (modal DFC); Fire // Ice (split). Primary Card Kingdom pages identify M11 collector **149**, ZNR **180**, APC **128**. An earlier failed candidate `/m11/146/lightning-bolt` used the wrong collector number and is excluded as evidence. Source pages are the Card Kingdom rows below (2026-10-11).

**All rows: measured HTTP status = UNVERIFIED; curl effective URL = UNVERIFIED.** Browser effective address equals the listed input when the connector reported no different address; this does not prove absence of redirects. `B` = browser rendered matching card/search content; `J` = JavaScript shell only; `E` = connector fetch error; `I` = primary search index match. An I/E result does not verify a working card page.

| Target/card | Exact requested URL | Browser evidence / effective address |
|---|---|---|
| Scryfall / Bolt | https://scryfall.com/card/m11/149/lightning-bolt | E; no independently observed final URL |
| Scryfall / DFC | https://scryfall.com/card/znr/180/bala-ged-recovery-bala-ged-sanctuary | E; candidate slug UNVERIFIED |
| Scryfall / split | https://scryfall.com/card/mh2/290/fire-ice | E; candidate printing/path UNVERIFIED |
| Gatherer / Bolt | https://gatherer.wizards.com/Pages/Search/Default.aspx?name=Lightning+Bolt | E; actual CK outbound URL |
| Gatherer / DFC | https://gatherer.wizards.com/Pages/Search/Default.aspx?name=Bala+Ged+Recovery | E; CK outbound uses front name |
| Gatherer / split | https://gatherer.wizards.com/Pages/Search/Default.aspx?name=Fire+%2F%2F+Ice | E; actual CK outbound URL |
| EDHREC / Bolt | https://edhrec.com/cards/lightning-bolt | B; matching title |
| EDHREC / DFC | https://edhrec.com/cards/bala-ged-recovery | B; matching front-name page |
| EDHREC / split | https://edhrec.com/cards/fire-ice | B; matching split-name page |
| Spellbook / Bolt | https://commanderspellbook.com/search/?q=card%3D%22Lightning%20Bolt%22 | B; address https://commanderspellbook.com/search/?q=card%3D%22Lightning+Bolt%22; exact-name results |
| Spellbook / DFC | https://commanderspellbook.com/search/?q=card%3D%22Bala+Ged+Recovery+%2F%2F+Bala+Ged+Sanctuary%22 | B; address https://commanderspellbook.com/search/?q=card%3D%22Bala+Ged+Recovery+//+Bala+Ged+Sanctuary%22; exact-name results |
| Spellbook / split | https://commanderspellbook.com/search/?q=card%3D%22Fire%20%2F%2F%20Ice%22 | E; final URL/result correctness UNVERIFIED |
| TCGplayer / Bolt | https://www.tcgplayer.com/search/magic/product?q=Lightning%20Bolt | J; address https://www.tcgplayer.com/search/magic/product?q=Lightning+Bolt |
| TCGplayer / DFC | https://www.tcgplayer.com/search/magic/product?q=Bala%20Ged%20Recovery%20%2F%2F%20Bala%20Ged%20Sanctuary | J; address https://www.tcgplayer.com/search/magic/product?q=Bala+Ged+Recovery+//+Bala+Ged+Sanctuary |
| TCGplayer / split | https://www.tcgplayer.com/search/magic/product?q=Fire%20%2F%2F%20Ice | J; address https://www.tcgplayer.com/search/magic/product?q=Fire+//+Ice |
| TCGplayer / DFC product (extra) | https://www.tcgplayer.com/product/221901/magic-zendikar-rising-bala-ged-recovery | I/J; no rendered product verification; other two product-ID probes UNVERIFIED |
| Cardmarket / Bolt | https://www.cardmarket.com/en/Magic/Products/Singles/Magic-2011/Lightning-Bolt | I/E; no independently observed final URL |
| Cardmarket / DFC | https://www.cardmarket.com/en/Magic/Products/Singles/Zendikar-Rising/Bala-Ged-Recovery-Bala-Ged-Sanctuary | I/E; no independently observed final URL |
| Cardmarket / split | https://www.cardmarket.com/en/Magic/Products/Singles/Apocalypse/Fire-Ice | I/E; no independently observed final URL |
| Card Kingdom / Bolt | https://www.cardkingdom.com/mtg/2011-core-set/lightning-bolt | B; M11 collector149 |
| Card Kingdom / DFC | https://www.cardkingdom.com/mtg/zendikar-rising/bala-ged-recovery | B; ZNR180, back name Sanctuary |
| Card Kingdom / split | https://www.cardkingdom.com/mtg/apocalypse/fire-ice | B; APC128 |

Completion boundary: requests exist for all seven targets and three card types, but transport verification is incomplete. Scryfall guessed candidates, Gatherer access, seller JavaScript, and index-only Cardmarket evidence must not be promoted to verified endpoint contracts. When shell network returns, repeat a handful of supplied official URLs with descriptive User-Agent, appropriate Accept header and site rate limits; record measured status and final URL separately. Do not attempt to bypass blocks.

### Affiliate parameters and terms

Sources S2-S3 define keys but do not specify affiliate policy. The dated secondary Abu Ja'far example above carries TCGplayer `utm_campaign=affiliate`, `utm_medium=api`, `utm_source=scryfall`; Cardmarket `referrer=scryfall`, `utm_campaign=card_prices`, `utm_medium=text`, `utm_source=scryfall`; Cardhoarder `affiliate_id=scryfall`, `ref=card-profile`, `utm_campaign=affiliate`, `utm_medium=card`, `utm_source=scryfall`. Those are historical observed parameters, not an exhaustive/current policy. Proposed default: preserve the entire supplied link, including attribution; do not rewrite or add an application affiliate ID. Exact current rules are UNVERIFIED.

| Target | Primary terms evidence and bounded conclusion (2026-10-11) |
|---|---|
| Scryfall | https://scryfall.com/docs/terms could not be retrieved; searches did not produce a primary clause on using purchase/related links. Requested exact quotation is **UNVERIFIED**. No quotation or permission invented. https://scryfall.com/docs/api/cards also inaccessible. Recheck terms before asserting a required affiliate-preservation rule. |
| Gatherer | https://company.wizards.com/en/legal/terms search returned Wizards General Terms. No Gatherer-specific inbound/deep-link clause verified; **UNVERIFIED**. User-content/link language in general terms is not permission for this app. |
| EDHREC | https://edhrec.com/terms rendered; searching returned no literal `link` occurrence. No explicit inbound deep-link clause confirmed in the retrieved page; **UNVERIFIED**, not unrestricted permission. |
| Commander Spellbook | https://commanderspellbook.com/privacy-policy/ and the search-page footer were retrieved/indexed; no deep-link terms clause located in site-specific search. **UNVERIFIED**. Privacy/Fan Content notices are not inbound-link licenses. |
| TCGplayer | https://help.tcgplayer.com/hc/en-us/articles/360061115874-TCGplayer-API-Terms-Conditions was indexed. It concerns API/content use; no inbound website deep-link permission verified. **UNVERIFIED**. Opening an external URL does not establish API access rights. |
| Cardmarket | https://www.cardmarket.com/en/Policies/GeneralTermsAndConditions/IT indexed the clause **“you are prohibited from using hyperlinks in your offers.”** This concerns marketplace offers, not external inbound card links. Full browser retrieval failed; inbound deep-link terms remain **UNVERIFIED**. |
| Card Kingdom | Site-specific search and product-page inspection did not locate a primary deep-link clause. Product sources in ledger; policy wording **UNVERIFIED**. Unrelated kingdom.com search result excluded. |
| Cardhoarder | No policy retrieval/three-card probes performed; **UNVERIFIED**. Optional link only, based on S3. |

No site-specific legal permission follows from lack of a located prohibition. This research does not authorize scraping, embedded pages, cached vendor content, or price harvesting.

### User template tokens and encoding

The following is a **proposed application contract**, not a statement that every target accepts every token. Data semantics come from S1-S3; escaping reference https://www.rfc-editor.org/rfc/rfc3986 (2026-10-11), sections2.1-2.3. Encode substituted components once; never encode the whole assembled URL. UTF-8 bytes for reserved/non-ASCII characters become percent sequences. Spaces use `%20` by default; `+` is only a form-query convention. The browser connector's normalized `//` query output is not a reason to leave slash-containing path tokens raw.

| Token | Proposed value | Encoding rule / missing value |
|---|---|---|
| `{name}` | Full canonical English name, including ` // ` | Percent encode as one component; retain punctuation and Unicode before encoding |
| `{front_face_name}` | First actual face name, else root name | Percent encode; do not blindly truncate unrelated strings |
| `{back_face_name}` | Second actual face name | Percent encode; missing for single-faced cards |
| `{printed_name}` | Localized printed name where supplied | Percent encode; explicit fallback to canonical name must be visible in preview |
| `{name_slug}` | Explicit app-defined slug, not universal vendor slug | Proposed lowercase ASCII, remove apostrophe/comma, replace runs of separators with `-`, trim; non-ASCII policy must be selected and documented before shipping. UNVERIFIED for vendor routing; warn rather than silently claim compatibility |
| `{set}` | Exact Scryfall set code | Validate then percent encode; does not equal retailer set slug |
| `{collector_number}` | Exact collector string, retaining letters/punctuation/leading zeros | Percent encode; never cast to integer |
| `{scryfall_id}` | Printing UUID | Validate canonical UUID; raw UUID characters are unreserved |
| `{oracle_id}` | Card identity UUID, with explicit face selection for reversible layouts | Validate UUID; missing disables template rather than guessing |
| `{multiverse_id}` | Explicit selected member of multiverse_ids | Validated decimal integer, raw; choose face/index deliberately, absent disables direct-ID link |
| `{tcgplayer_id}` | Actual optional productId | Validated decimal integer, raw |
| `{tcgplayer_etched_id}` | Actual optional etched productId | Validated decimal integer, raw; never replace normal id without finish intent |
| `{cardmarket_id}` | Actual optional idProduct | Validated decimal integer, raw |
| `{mtgo_id}` / `{mtgo_foil_id}` | Actual optional MTGO catalog ID | Validated decimal integer, raw; no assumed Cardhoarder path |
| `{lang}` | Printing language code | Validate source value then percent encode |
| `{scryfall_uri}` | Complete supplied website URL | Whole-link template only; preserve exactly, validate scheme |
| `{gatherer_uri}` / `{edhrec_uri}` | Complete corresponding related URI | Whole-link only; missing disables link |
| `{tcgplayer_uri}` / `{cardmarket_uri}` / `{cardhoarder_uri}` | Complete corresponding purchase URI | Whole-link only; preserve attribution, missing disables link |

Proposed validation: restrict launch schemes to HTTP/HTTPS, show expanded URL before saving, reject unknown tokens and malformed URLs, distinguish missing data from empty string. Never interpolate `null`, `0`, or a guessed vendor ID. Allow static constants and documented component tokens; do not silently allow raw arbitrary name substitution. If future raw tokens are added, label their injection risk explicitly.

Proposed examples: `https://www.tcgplayer.com/search/magic/product?q={name}`; `https://www.cardkingdom.com/catalog/view?filter%5Bname%5D={name}&filter%5Bsearch%5D=mtg_advanced`; whole-link `{scryfall_uri}`. Their route evidence and limits are in the target table. Spellbook requires escaping embedded backslashes/double quotes in its query grammar before component encoding, so prefer a built-in adapter over pretending `{name}` alone solves expression escaping. Grammar source: https://commanderspellbook.com/syntax-guide/ (2026-10-11).

Proposed acceptance fixtures: the ledger's three cards plus Chandra's Spitfire, Thalia, Guardian of Thraben, Unicode accents, `Henzie "Toolbox" Torre`, collector suffixes, missing IDs, DFC face choices, and reversible oracle IDs. Verify encoded apostrophe `%27`, comma `%2C`, slash `%2F`, ampersand `%26`, percent `%25`, and no double encoding; these are design tests to implement, not tests run in this research. EDHREC punctuation examples and Spellbook syntax above provide dated primary evidence; generic slug compatibility remains UNVERIFIED.

## Options

1. Build every URL from a generic slug. Small schema, but contradicted by the observed DFC differences between EDHREC/Card Kingdom and Cardmarket, plus differing retailer set names. Not recommended (probe ledger, 2026-10-11).
2. Prefer supplied Scryfall links, add isolated site adapters and encoded search fallbacks, retain configurable templates. Recommended; S1-S3 confirm stable data fields, while the ledger bounds actual site evidence.
3. Use search pages only. Useful when identifiers are missing, but exact printing/finish is lost and JavaScript/result correctness remains unverified for some sellers (ledger).

## Recommendation

Adopt option2. Store `scryfall_uri`, complete related/purchase URIs and optional typed IDs during card ingestion. Use supplied links first, checked adapter second, user-visible search fallback third. Provide all seven named destinations as configurable entries, and optional Cardhoarder for MTGO. Do not ship guessed exact-printing seller slugs as verified links. Source basis: S1-S3 and dated primary ledger.

Keep terms and curl verification as explicit follow-up work. A field can be supported while its current destination is unverified. Preserve complete supplied attribution by default pending official policy retrieval. Mark missing-data targets unavailable with a specific reason. Browser-proven EDHREC/Card Kingdom examples and Spellbook grammar justify adapters; they do not prove exhaustive slug or transport behavior.

## Risks

- Vendor routing, attribution and terms can change; source objects may contain stale links. S3 expressly warns Cardmarket links can be inexact.
- Name-only search can choose the wrong printing, language or finish. S1 optional IDs and reversible-face identity require deliberate selection.
- Browser index/cache and JS shells conceal live transport and results; all measured HTTP/final redirects remain UNVERIFIED.
- Generic slugification fails across DFC/split targets and non-ASCII names. Restrict claims to observed ledger examples.
- Official Scryfall terms could not be quoted; deep-link policies remain unverified for all targets. Policy silence must not be described as permission.

## Confidence

**High:** official Scryfall field/key semantics (S1-S3), three EDHREC and three Card Kingdom browser card pages, Spellbook name grammar. **Medium:** search-indexed seller path shapes and historical attribution example. **Low/UNVERIFIED:** live HTTP statuses/redirect chains, all-cards slug rules, seller JS search results, current Scryfall terms quotation, inbound deep-link policies, missing three-card direct-ID and fallback probes. Access date for all evidence: 2026-10-11. No transport tests or app tests claimed.
