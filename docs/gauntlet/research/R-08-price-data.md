# R-08 Price data

Path: `docs/gauntlet/research/R-08-price-data.md`. Task: `cc-research-08`. Author: Codex researcher. Completed 2026-10-11. All source access/attempt dates are 2026-10-11. Primary documentation, upstream code and file-server listings are evidence; no vendor account was created, authenticated request made, or secret accessed. Proposed application policies are labeled recommendations.

## Question

Which card price sources and currencies can Command Library use, how fresh are they, and what do their terms allow? This informs D-04, per-printing/finish collection valuation, effective acquisition cost `(n)` and budget-aware recommendations.

## Findings

### F1. Scryfall price schema and native units

The official `ScryfallPrices` type contains nullable strings for six fields:

| Field | Native unit and treatment | Provenance status |
|---|---|---|
| `usd` | USD, normal | Common attribution to TCGplayer requires current Scryfall pricing FAQ confirmation; exact market statistic UNVERIFIED. |
| `usd_foil` | USD, foil | Same provenance verification outstanding. |
| `usd_etched` | USD, etched | Same provenance verification outstanding. |
| `eur` | EUR, normal | Cardmarket confirms its Scryfall partnership and downstream price availability; exact trend/average algorithm UNVERIFIED. |
| `eur_foil` | EUR, foil | Same Cardmarket source relationship; statistic UNVERIFIED. |
| `tix` | MTGO Event Tickets | Cardhoarder attribution and exact feed statistic UNVERIFIED from current first-party Scryfall docs. Tix is not fiat USD. |

Schema source: https://github.com/scryfall/api-types/blob/main/src/objects/Card/values/Prices.ts (accessed 2026-10-11). Cardmarket provenance: https://insight.cardmarket.com/en/Articles/scryfall-x-cardmarket (accessed 2026-10-11). MTGO ticket definition: https://help.cardhoarder.com/en/articles/8674937-economy-cashing-out (accessed 2026-10-11).

Important schema discrepancy: the official CardFields comment lists `eur_etched`, but the actual Prices.ts type omits it. Do not advertise native EUR etched prices until inspecting current API/bulk responses and documentation. Support unknown added keys in ingestion without manufacturing missing values. Sources: https://github.com/scryfall/api-types/blob/main/src/objects/Card/CardFields.ts and Prices.ts above (accessed 2026-10-11).

CardFields describes price data as daily. Exact update hour, once-per-day guarantee, bulk staleness threshold and current caching/usage clauses are UNVERIFIED: https://scryfall.com/docs/api, https://scryfall.com/docs/api/cards, https://scryfall.com/docs/api/bulk-data and https://scryfall.com/docs/terms returned forbidden/error through the web tool; primary-domain searches did not recover current clauses (attempted 2026-10-11). A daily type comment is not proof of current data freshness or terms. No fabricated quote is supplied. Recommendation pending confirmation: refresh daily, show snapshot age, preserve old data offline and flag stale prices.

TCGplayer distinguishes Market Price based on recent completed sales from other price points. This defines its statistic, but does not independently prove which one Scryfall chooses. Source: https://help.tcgplayer.com/hc/en-us/articles/222376867-What-do-the-different-price-points-on-TCGplayer-com-mean (accessed 2026-10-11).

### F2. MTGJSON price files, data shape and providers

`AllPrices` holds 90 days; `AllPricesToday` holds the current day's snapshot. Both key records by MTGJSON printing `uuid`. Source: https://mtgjson.com/downloads/all-files/ and https://mtgjson.com/data-models/price/ (accessed 2026-10-11). Daily builds are documented: https://mtgjson.com/ (accessed 2026-10-11). Build date is distinct from each provider observation date; missing days/providers must remain absent.

Nested data uses paper/mtgo, provider, currency, optional retail/buylist, finish and date. `retail` means buying from provider; `buylist` means selling to provider. Normal, foil and etched are optional finish maps with ISO dates. Sources: https://mtgjson.com/data-models/price/price-formats/, https://mtgjson.com/data-models/price/price-list/ and https://mtgjson.com/data-models/price/price-points/ (accessed 2026-10-11). Never use buylist as acquisition cost.

Current upstream price-engine documentation identifies five providers:

| Provider | Medium | Native currency in MTGJSON | Documented quote modes |
|---|---|---|---|
| TCGplayer | Paper | USD | Retail; buylist API deprecated |
| Card Kingdom | Paper | USD | Retail and buylist |
| Cardmarket | Paper | EUR | Retail and buylist |
| Mana Pool | Paper | USD | Retail, cents converted to dollars |
| Cardhoarder | MTGO | USD | Retail normal and foil |

Source for table: https://github.com/mtgjson/mtgjson/blob/e730ae727a8c610c1660a69067f326e22241623e/docs/prices.md (accessed 2026-10-11). This is upstream implementation documentation, not a live-file coverage measurement. Cardhoarder MTGJSON USD must not be treated as Scryfall TIX. Public Price Formats docs still list Cardsphere and omit Mana Pool while Price List lists Mana Pool; actual current file coverage must decide the settings choices. Do not promise Cardsphere availability based on stale schema documentation. Sources: Price Formats/Price List above (accessed 2026-10-11).

Mana Pool upstream provider maps `scryfall_id` to UUID and consumes `price_cents`, `price_cents_foil`, `price_cents_etched`; it requests the bulk price endpoint without authentication in that code. Source: https://github.com/mtgjson/mtgjson/blob/e730ae727a8c610c1660a69067f326e22241623e/mtgjson5/providers/manapool/provider.py (accessed 2026-10-11). This establishes code behavior, not permission for Command Library to redistribute Mana Pool's feed directly.

### F3. File sizes and measurement limitation

A bounded `curl -sSI --connect-timeout 8 --max-time 12 https://mtgjson.com/api/v5/AllPricesToday.json.gz` failed immediately because the configured proxy on port 8080 was unreachable. Consequently exact Content-Length HEAD measurements are UNVERIFIED. No bulk files were downloaded or saved. The primary server index supplies these rounded sizes, with October 10, 2026 timestamps:

| File | Server-listed size |
|---|---:|
| AllPrices.json | 1.2 GiB |
| AllPrices.json.gz | 141.6 MiB |
| AllPrices.json.bz2 | 95.3 MiB |
| AllPrices.json.xz | 45.7 MiB |
| AllPricesToday.json | 51.1 MiB |
| AllPricesToday.json.gz | 5.3 MiB |
| AllPricesToday.json.bz2 | 3.3 MiB |
| AllPricesToday.json.xz | 4.1 MiB |

Source: https://mtgjson.com/api/v5/ (accessed 2026-10-11). These are directory-listing observations, not exact byte measurements or guaranteed next-build sizes. Recommend current-only download by default; optional history must show download estimate. JSON import should stream rather than materialize the history file.

### F4. Direct vendor access and terms

| Vendor | Personal app access/auth today | Terms and implementation conclusion |
|---|---|---|
| TCGplayer | New API access is closed. Existing approved clients use client credentials to obtain bearer tokens. Source: https://docs.tcgplayer.com/docs/getting-started (accessed 2026-10-11). | Clause: "We are no longer granting new API access at this time." Existing users remain subject to restrictions/attributions; full API agreement UNVERIFIED. No new-account dependency. Same source, accessed 2026-10-11. |
| Cardmarket | New API applications are closed. Legacy API documentation uses OAuth; current help overrides its historical signup instructions. Sources: https://help.cardmarket.com/en/cardmarket-api and https://apiv2.cardmarket.com/ws/documentation/API_2.0:Main_Page (accessed 2026-10-11). | Clause: "Currently, we are not accepting applications for access to the Cardmarket API." Do not offer shared/embedded developer credentials. However public daily catalog/price downloads are expressly available without extra permission; see F5. |
| Card Kingdom | A personal developer-access program/auth contract is UNVERIFIED. Official homepage exposed insufficient text and primary searches found no current API agreement. Source attempted: https://www.cardkingdom.com/ (2026-10-11). MTGJSON uses Card Kingdom as a provider, which is not proof of an open developer contract. | Direct endpoint stability, auth, currencies, redistribution and cadence are UNVERIFIED. Prefer optional MTGJSON retail/buylist data with its notices, not an undocumented vendor endpoint. No clause could be verified. |
| Mana Pool | Official API docs exist but returned only shell/navigation content. Vendor-owned examples authenticate buyer operations with `X-ManaPool-Email` and `X-ManaPool-Access-Token`. Sources: https://manapool.com/api/docs/v1 and https://github.com/manapoolinc/manapool-api-examples/blob/main/examples/Mana%20Pool%20Flexible%20Purchasing%20Script/purchase.py (accessed 2026-10-11). | Site terms require consent for automated content reproduction. Clause fragment: "without the express written consent of Mana Pool". Whether public API price distribution supplies that consent is UNVERIFIED; no direct catalog scraping/redistribution dependency. Source: https://manapool.com/terms (accessed 2026-10-11). |
| CardTrader | Official reference documents bearer auth, marketplace products, blueprint IDs, per-product properties and money with cents/currency. USD/EUR examples confirmed; complete supported-currency list and token issuance eligibility are UNVERIFIED. Source: https://www.cardtrader.com/docs/api/full/reference (accessed 2026-10-11). | API rate clause: "200 requests per 10 seconds." API exists, but full price redistribution/history/caching terms were not established. Treat as optional account integration after agreement verification, not default. Same source, accessed 2026-10-11. |
| Cardhoarder | Affiliate program explicitly offers sale-price data and requires contacting vendor to join. No automatic personal API entitlement, auth schema or direct bulk redistribution rights verified. Source: https://help.cardhoarder.com/en/articles/8684184-affiliate-program (accessed 2026-10-11). | Clause: "Sale price data for MTGO Cards". Site prices update at a regular interval without a stated exact duration: https://help.cardhoarder.com/en/articles/9897537-card-price-updates (accessed 2026-10-11). Prefer existing aggregated data or approved partnership. |
| Cardsphere/other vendors | Cardsphere exists in MTGJSON's public model, but current price-engine documentation names five other providers. Open direct API/auth/redistribution/currency/cadence are UNVERIFIED in this audit. Sources: https://mtgjson.com/data-models/price/price-formats/ and upstream price-engine source in F2 (accessed 2026-10-11). | Do not invent a selectable live price source based on an identifier or legacy schema. Additional providers require primary API/terms evidence. |

### F5. Licensing and allowed public data paths

MTGJSON publishes its website/content under MIT. Required clause: "The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software." Preserve the notice/disclaimer in distributed data credits. Source: https://mtgjson.com/license/ (accessed 2026-10-11). Its project license is not an independent license to scrape or redistribute vendor websites through undocumented APIs.

Cardmarket expressly describes public daily catalog and price files. Clause: "Anyone can import and incorporate the data into their own applications, no extra permission or access point necessary." This provides a direct public-download alternative despite API closure; retain provenance and do not imply live inventory. Source: https://insight.cardmarket.com/en/Articles/scryfall-x-cardmarket (accessed 2026-10-11). Product catalog/download schema and exact file sizes are UNVERIFIED here; article confirms permission and daily cadence, not complete implementation details.

Scryfall current API Additional Terms, price disclaimers, required attribution and bulk caching clauses remain UNVERIFIED because official pages failed retrieval (F1). Its api-types repository proves schema, not data-use licensing. Gate 1 should preserve this legal uncertainty instead of substituting a third-party transcription. CardTrader full license/terms and Card Kingdom direct terms also remain UNVERIFIED. Sources attempted: https://scryfall.com/docs/api, https://scryfall.com/docs/terms, https://www.cardtrader.com/docs/api and https://www.cardkingdom.com/ (2026-10-11).

Mana Pool's prohibition includes automated cataloging/downloading/storage/distribution of site/app content without consent; do not interpret its API examples as unrestricted rights to republish all marketplace prices. Source: https://manapool.com/terms (accessed 2026-10-11). This is an integration-specific unknown, not a reason to halt unrelated research or implementation design.

### F6. Printing, finish and identifier joins

Scryfall `id` is a printing record and `oracle_id` is the card's reprint-shared identity; prices are treatment-specific fields within a record. CardFields supplies optional vendor IDs and finish metadata. Source: https://github.com/scryfall/api-types/blob/main/src/objects/Card/CardFields.ts and https://github.com/scryfall/api-types/blob/main/src/objects/Card/values/Prices.ts (accessed 2026-10-11). Keep `(scryfall_id, finish)` as the local acquisition/valuation quote key. An Oracle ID is not a unique price key.

MTGJSON prices join via printing UUID to `identifiers.scryfallId`. Available mapping identifiers include `scryfallOracleId`, `tcgplayerProductId`, `tcgplayerEtchedProductId`, `tcgplayerAlternativeFoilProductId`, `mcmId`, `cardKingdomId`, `cardKingdomFoilId`, `cardKingdomEtchedId`, `cardtraderId`, `mtgoId` and `mtgoFoilId`. All optional fields can be absent. Source: https://mtgjson.com/data-models/identifiers/ (accessed 2026-10-11).

Upstream price docs use distinct TCGplayer etched and MTGO mappings; Mana Pool uses Scryfall IDs; Cardmarket has finish-specific mapping with omission of ambiguous values. Recommendation: preserve upstream finish mapping, detect one-to-many joins and reject ambiguous price propagation. Do not apply one product's normal quote to another etched/foil variant by name. Source: https://github.com/mtgjson/mtgjson/blob/e730ae727a8c610c1660a69067f326e22241623e/docs/prices.md (accessed 2026-10-11).

CardTrader marketplace quotes have blueprint/product IDs, condition/language/foil properties and explicit currency; MTGJSON `cardtraderId` can assist joins but reference examples show `scryfall_id` may be null. Complete deterministic mapping and etched distinctions are UNVERIFIED. Source: https://www.cardtrader.com/docs/api/full/reference and MTGJSON identifiers source above (accessed 2026-10-11). Card Kingdom and Cardhoarder direct identifiers/auth contracts are UNVERIFIED; their MTGJSON crosswalks are the confirmed integration route.

### F7. FX for additional display currencies

ECB reference rates use EUR as base, generally update around 16:00 CET on working days except TARGET closing days, and offer XML/CSV. The observed page was dated October 9, 2026, appropriate for the Sunday research date. Supported examples include USD, GBP, CAD, AUD, JPY and CHF; EUR/RUB publication is suspended. Clause: "Using the rates for transaction purposes is strongly discouraged." Source: https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html (accessed 2026-10-11).

Reproduction must be accurate and credit ECB. Clause: "the ECB must be cited as the source". Source: https://www.ecb.europa.eu/services/using-our-site/disclaimer/html/index.en.html (accessed 2026-10-11). Recommendation: offer only published fiat currency codes, label converted estimates and persist original amount/currency, FX date and price date. Do not silently substitute FX conversion for native local-market prices.

For EUR-base rates `r_X` and `r_Y`, proposed conversion is `amount_Y = amount_X * r_Y / r_X`, with `r_EUR = 1`; decimal arithmetic and currency-appropriate rounding apply after aggregation. Tickets need a separate sourced cash exchange mechanism; never apply ECB rates or a fabricated 1 TIX = 1 USD conversion. Basis: ECB EUR-base definition and Cardhoarder ticket economy source in F1 (accessed 2026-10-11).

### F8. Proposed `(n)` and missing-price policy

These are product recommendations derived from GAUNTLET 2.C and source identity/nullable schemas in F1/F6, not source promises:

- Store collection ownership by exact printing and finish. Default exact-printing mode matches only the requested finish/printing. Offer an explicit "any printing of this card" acquisition policy that uses Oracle identity across interchangeable eligible printings; it changes completion cost, never the recorded collection or selected-printing market value.
- For required quantity `q`, allocate compatible owned copies once within the evaluated deck: `missing = max(q - allocated_owned, 0)`. With owned-as-free enabled and a valid unit quote `p`, `(n) = missing * p`. Fully covered demand is `(0.00)` even if its market quote is unknown; zero is justified by no acquisition need, not absence of pricing.
- Without owned-as-free, cost uses the selected applicable market quote for all required copies. Keep separate deck market value, owned value and completion estimate. A single physical copy cannot satisfy two rows within one deck; alternative decks can be evaluated separately without claiming simultaneous ownership allocation.
- Missing/null/unmapped finish or currency quotes yield unknown acquisition cost when copies are needed, never `(0.00)`. Budget checks return "unknown", not "within budget". Deck total shows known subtotal plus count of unpriced required copies; no false complete total.
- Stale known prices retain amount, source and dates with stale status; offline usage must not relabel them fresh. Do not silently mix vendors/currencies/statistics or substitute foil for normal. Explicit user-selected fallback marks each quote's real provenance.
- Cheapest-printing recommendations compare only valid, priced, eligible printings/finishes under selected market/policy; unknown-price printings cannot win as free. Market estimates exclude shipping, tax, stock and condition adjustments unless a verified quote explicitly includes them.

Acceptance examples: needs four, owns two compatible, quote USD 3 => `(6.00)`; needs one, owns none, quote null => unknown; needs one, owns one compatible, quote null and owned-as-free => `(0.00)` acquisition with unknown market value. Under any-printing policy, an owned normal reprint may cover demand only if the user has allowed that substitution; an explicitly requested etched collectible is not automatically satisfied by it.

## Options

| Option | Why offer it | Limit |
|---|---|---|
| Scryfall USD reference | Simple default alongside primary card data; nullable per-finish schema | Current source statistic and terms still need primary confirmation. |
| Scryfall EUR/Cardmarket reference | Native European market prices, not converted US quotes | Exact statistic and EUR etched support unresolved. |
| Scryfall TIX | Native MTGO price unit | Distinct digital inventory; never mix with paper or MTGJSON USD. |
| MTGJSON retail providers | Current daily provider/finish quotes; optional buylist view | Crosswalk/coverage required; use actual available provider/currency metadata. |
| Cardmarket public daily downloads | Expressly permitted direct public source | Separate mapping/schema import, not live API access. |
| ECB conversion | More display fiat currencies | Estimate with separate FX date; not local-market or transaction quote. |
| Direct approved accounts | Potential live listings/condition/shipping | Not required for baseline; eligibility and redistribution rights must be established per vendor. |

Source bases: F1-F7, accessed 2026-10-11. Options are proposed settings, not a promise every source has a price for every card.

## Recommendation

D-04 proposal: default **USD with Scryfall reference pricing**, subject to recovering and confirming current Scryfall price-source/usage documentation before integration. Offer native EUR/Cardmarket and native MTGO TIX; optional MTGJSON retail provider selection after successful mapping and actual coverage validation. If Scryfall terms cannot be confirmed, MTGJSON current-only pricing plus its MIT notice and Cardmarket's explicitly permitted public-download option are the evidenced alternatives. Do not depend on acquiring new TCGplayer or Cardmarket API keys. Basis: F1/F2/F4/F5.

One-line tradeoff for Pat: "USD gives a simple US-market estimate; EUR/Cardmarket better reflects European buying, while converted currencies change display units rather than the market."

This is a recommendation, not a ruling on Pat's locale. Default the owned-as-free toggle on and keep an explicit exact-printing versus any-printing policy; the latter should be decided in the spec. Use current-only daily refresh by default and optional 90-day history download. Keep missing prices unknown, and preserve source/price/FX dates in budget calculations and exports.

## Risks

- Primary Scryfall price-source, staleness and Additional Terms clauses could not be verified. Do not call data-use approval complete based on a repository schema.
- Official sources disagree: Scryfall comment versus six-key type; MTGJSON website model versus current five-provider engine. Inspect real snapshots before finalizing settings or parsing assumptions.
- MTGJSON Cardhoarder prices are USD in current upstream documentation, while Scryfall exposes TIX. Medium and unit belong in the quote key.
- API availability is distinct from redistribution permission. Cardmarket public downloads are permitted while new API access is closed; Mana Pool site consent restrictions need API-specific interpretation.
- Daily market estimates can diverge from in-stock purchasable listings, shipping and tax. Budgets must disclose unknown/stale data and never interpret missing prices as free.
- Exact HEAD byte sizes/live provider coverage and full direct-vendor contracts remain UNVERIFIED; rounded directory listings and upstream code are stated as such.

## Confidence

Medium. High on official schema nullability, MTGJSON structure/90-day history, current upstream provider currencies, closed TCGplayer/Cardmarket API enrollment, Cardmarket public-download permission and ECB cadence/reuse conditions. Low on inaccessible Scryfall terms/statistics and vendor access not granted/tested. Every required scope item is answered or explicitly UNVERIFIED with attempted evidence. No contradiction with GAUNTLET section 10 or Appendix A; `(n)` policy needs explicit ownership-substitution and unknown-price acceptance criteria.

RESEARCH COMPLETE: R-08, 2026-10-11. Terms and runtime/data-snapshot unknowns remain explicit Gate 1 risks.
