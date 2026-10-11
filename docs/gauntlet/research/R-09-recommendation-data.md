# R-09 Recommendation data

Path: `docs/gauntlet/research/R-09-recommendation-data.md`. Access date for all findings: 2026-10-11. Publication dates are stated separately when retrieved. Research uses primary web documentation; no live API or bulk measurement was possible through the unavailable shell proxy. Unconfirmed claims are marked UNVERIFIED.

## Question

What data supports explainable offline budget-aware recommendations and role tagging, and which online enrichers permit the required use?

## Findings

### F1. Available offline card signals

| Signal | Confirmed producer field | Recommendation use and boundary |
|---|---|---|
| Current rules text | MTGJSON `text`; Scryfall `oracle_text` candidate is UNVERIFIED from primary docs here | Effects, conditions, costs and payoff/enabler evidence. Printed text is not a substitute for current rules. |
| Types/subtypes | MTGJSON `type`, `types`, `subtypes`, `supertypes`; Scryfall `type_line` candidate UNVERIFIED here | Typal/type links and land classification; use token boundaries and face semantics. |
| Keywords | MTGJSON optional `keywords`; Scryfall `keywords` candidate UNVERIFIED here | Ability hints, not complete card-role labels. |
| Color identity | MTGJSON `colorIdentity`; Scryfall `color_identity` candidate UNVERIFIED here | Commander eligibility, distinct from castable colors. |
| Mana value/cost | MTGJSON `manaValue`, optional `manaCost`, `faceManaValue`; Scryfall `cmc`/`mana_cost` candidate UNVERIFIED here | Curve/pip gaps and efficiency; alternatives and activated costs need separate reads. |
| Mana production | Scryfall `produced_mana` candidate UNVERIFIED from primary docs; no equivalent confirmed in MTGJSON Atomic/Set schema | Useful hint, cannot alone classify fetch lands or context-restricted mana. |
| Popularity | MTGJSON optional `edhrecRank`; Scryfall `edhrec_rank` candidate UNVERIFIED here | Weak prior/tie breaker, not commander-specific synergy. |
| Penny rank | Scryfall `penny_rank` existence/semantics UNVERIFIED here | Exclude from Commander synergy until verified; format-specific rank is not budget price. |
| Saltiness | MTGJSON optional `edhrecSaltiness` | Preference warning, not official bracket or legality. |
| Commander eligibility | MTGJSON `leadershipSkills` with `commander`, `brawl`, `oathbreaker` Booleans | Eligibility helper; does not prove partner/background pairing legality. |
| Legality/Game Changers | MTGJSON `legalities`, optional `isGameChanger` | Hard legality filter, reviewed bracket evidence as R-04 describes. |

Primary schema sources for MTGJSON fields: https://mtgjson.com/data-models/card/card-atomic/, https://mtgjson.com/data-models/card/card-set/ and https://mtgjson.com/data-models/leadership-skills/ (accessed 2026-10-11). Scryfall primary schema https://scryfall.com/docs/api/cards returned inaccessible/403 on 2026-10-11; third-party SDK/code references were deliberately not elevated into primary-source confirmation. R-02 must reconcile these exact Scryfall fields and producer semantics before schema pinning. Every optional signal requires missing-value handling rather than invented zero/rank.

### F2. Existing tagging rules and datasets

`dan-blanchard/mtg-skills` is a concrete 0BSD code reference. Its current role layer uses structural signal/IR reads and fallbacks for uncovered cards, rather than only simple regex. Roles include lands, ramp, draw, interaction and board wipe, with protection advisory. Source reviewed through GitHub connector: https://github.com/dan-blanchard/mtg-skills/blob/main/mtg-utils/src/mtg_utils/_analysis/roles.py (accessed 2026-10-11). This is publicly licensed source logic, not a downloaded, independently validated labeled card corpus; its auxiliary model/data licenses and accuracy remain UNVERIFIED.

Three source-derived rule approaches, examples rather than production-ready classifier:

1. Land rule: read layout and face-specific type tokens; transforming/flip cards use front-face type, modal cards retain choices. Avoid matching substrings inside unrelated types. Source: https://github.com/dan-blanchard/mtg-skills/blob/main/mtg-utils/src/mtg_utils/card_classify.py (accessed 2026-10-11).
2. Ramp fallback: nonland card with a controller mana-producing effect, extra land play or own-library land search reaching battlefield. Land to hand alone is not acceleration; opponent compensation/token reminder text must not become your ramp. Source: same `card_classify.py` (accessed 2026-10-11).
3. Removal fallback: effect verbs such as destroy/exile paired with board target scope; include damage, toughness reduction, edicts and bounce where appropriate; exclude graveyard-card targets and player-only damage from permanent removal. Source: same `card_classify.py` (accessed 2026-10-11).

These approaches summarize source behavior without copying a full implementation. New-set coverage, negative clauses, reminder text and sentence/face boundaries still need fixture tests. Source license permits adaptation: "Permission to use, copy, modify, and/or distribute this software". Source: https://raw.githubusercontent.com/dan-blanchard/mtg-skills/main/LICENSE (accessed 2026-10-11; 0BSD, copyright 2026 Dan Blanchard).

Tagit is another current public-source role classifier: its own README distinguishes actual draw effects from draw payoffs, supports multi-role tags including ramp, recursion, wipes, protection, tutor, tokens and sacrifice outlets, and preserves user tags. No LICENSE was visible in the inspected repository root; license/redistribution permission is UNVERIFIED, so do not copy its code or package it as an open dataset. Source: https://github.com/TrumanAmsler/Tagit (accessed 2026-10-11). Its Moxfield endpoint fallback behavior is a description, not authorization for this app to reuse Moxfield data.

Scryfall Oracle Tagger and `otag:` queries are relevant candidates. Contrary to an old assumption that tags are online-only, Scrython's current maintainer docs/code explicitly support `oracle_tags` and `art_tags` bulk types, including nested taggings keyed by Oracle or illustration identity. This is a credible lead, not verified Scryfall documentation: live `/bulk-data`, Tagger and official bulk docs were inaccessible. Bulk availability/schema/license/rate policy and exact tags remain UNVERIFIED until primary verification; do not infer named tag strings or a download URL. Sources attempted: https://api.scryfall.com/bulk-data, https://scryfall.com/docs/api/bulk-data and https://tagger.scryfall.com/ (2026-10-11). Lead source: https://github.com/NandaScott/Scrython and https://raw.githubusercontent.com/NandaScott/Scrython/main/scrython/bulk_data/bulk_data.py (accessed 2026-10-11, primary for wrapper behavior only).

No other fully verified open labeled corpus covering all required roles was established. Proposed app taxonomy: lands, sustained ramp, burst mana, draw, selection, targeted removal, counterspell, sweepers, protection, tutors, recursion, graveyard hate, token maker/payoff, sacrifice outlet/payoff and win condition. Preserve overlapping tags; explicit win-text detection is narrower than practical win conditions and requires combo/context evidence. This taxonomy is a design judgment informed by the role references above, not an externally certified classifier.

### F3. Existing synergy methods from publisher documentation

EDHREC now documents lift rather than treating its historical synergy metric as current everywhere. Official December 15, 2025 article defines lift as co-occurrence probability divided by independent occurrence probabilities, with eligible color-identity pools and release-date windows. It identifies correlation, not proven mechanical interaction, and warns that user decks can be suboptimal. Source: https://edhrec.com/articles/from-synergy-to-lift-the-math-behind-edhrecs-new-era (published 2025-12-15, accessed 2026-10-11).

October 1, 2026 changelog says the lift rollout now covers commander, tag and combo pages as well as card pages; ratios are centered on 1, unlike percentage synergy centered on zero. Commander-page numerator is contextual inclusion rate and denominator eligible-dataset inclusion. Therefore importing an old `synergy` field and calling it current lift would be wrong. Source: https://edhrec.com/articles/changelog-replacing-synergy-with-lift-on-edhrecs-card-pages (published 2026-10-01, accessed 2026-10-11).

Spellbook documents a different, mechanical method: editors describe card/template prerequisites and produced features; its graph expands concrete variants. That provides explainable known interactions/combo completion, not a statistical deck-quality score. Source: https://spacecowmedia.github.io/commander-spellbook-backend/domain-model.html (accessed 2026-10-11).

The 0BSD mtg-skills README describes local card filters, role/mana analysis, commander trigger/coherence checks, combo discovery and ownership/budget handling. These establish concrete reference mechanisms; its inclusion of an EDHREC JSON client does not establish permission to use that provider. Source: https://github.com/dan-blanchard/mtg-skills (accessed 2026-10-11). No claim of benchmarked superiority or universal role quotas is made.

### F4. Online enrichers and permission boundaries

| Provider | Endpoint/data | Caching/use ruling from retrieved primary terms |
|---|---|---|
| Commander Spellbook | API base `https://backend.commanderspellbook.com`; `/variants/`, `/find-my-combos`, `/estimate-bracket`; bulk `https://json.commanderspellbook.com/variants.json.gz` | Allowed attributed scheduled local bulk consumption; sparse API reads, identified User-Agent, 429 backoff. See permission below and R-04. |
| MTGJSON | Public AtomicCards/Set datasets and schema, optional rank/saltiness/eligibility | Published MIT website/content license supports copying with notices. Dataset metadata and exact current download URLs/size not measured here. No direct EDHREC scraping implied. |
| Scryfall | Candidate `/cards/search?q=...`, card/bulk metadata and tag queries | Current primary API/data/tag permission clauses, required headers and numeric rate limits UNVERIFIED due 403. Defer to independently verified R-02, avoid calling it forbidden or online-only. |
| EDHREC | Public browser pages and internal JSON endpoints mentioned in third-party tools | No developer API or permission grant confirmed. Current terms restrict automated requests/copying. Do not ship scraper/internal JSON client or offline recommendation table without explicit provider permission. Browser links remain the appropriate reference path. |
| User-owned deck/collection | Local authored/imported deck and quantity records | Local heuristic inputs do not need an external enrichment API. Public sharing/co-occurrence corpus acquisition would require independent licensing/consent, not presumed from URL visibility. |

Spellbook docs say "Fetch it on a schedule of your own and read it locally." They prefer bulk over full API pagination; advise roughly 80 requests/minute as safe, credit/backlinks and service identification. `timestamp` and `version` support snapshot freshness, but exact cadence/SLA and current bulk bytes are UNVERIFIED. Source: https://spacecowmedia.github.io/commander-spellbook-backend/api.html (accessed 2026-10-11).

MTGJSON permission includes rights "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies" with retained license/copyright notices. This does not transfer underlying Wizards artwork/mark ownership; retain attribution and producer provenance. Source: https://mtgjson.com/license/ (accessed 2026-10-11). Rank/saltiness exported by MTGJSON is a limited supplied field, not a grant to copy EDHREC's complete recommendation database.

EDHREC terms effective 2024-08-06 grant access "solely for your own personal, noncommercial use" and prohibit "automated searches, requests, or queries to the Site". Their restrictions also disallow copying/downloading/republication except stated permissions and competitive/derivative use. These are current retrieved terms, not an inference from robots.txt. No clause granting this app automated enrichment/offline copying was found. Source: https://edhrec.com/terms (accessed 2026-10-11). Visibility of internal JSON or third-party open-source scrapers is not permission; exact internal endpoints were not accessed or asserted as public API contracts.

Scryfall primary terms/API pages were attempted at https://scryfall.com/docs/api, https://scryfall.com/docs/api/cards and https://scryfall.com/docs/api/bulk-data (2026-10-11). Numeric 10-requests/second folklore or historical SDK docs were not accepted as verified current policy. The product brief's descriptive User-Agent/Accept and polite batching are requirements regardless; current provider rules still need R-02 verification.

### F5. Proposed offline ranking and explainability

The following is an implementation proposal, not a claim that an external dataset includes all these scores. It uses verified MTGJSON schema, licensed role-code approaches and permitted Spellbook bulk evidence from F1/F2/F4 (accessed 2026-10-11).

1. Reject illegal/color-ineligible candidates and target-bracket conflicts using local identity/legality and versioned R-04 rules. Keep uncertainty separate from a hard rejection; intent-based bracket constraints need user input.
2. Produce versioned role facts per face and ability: role, enabler/payoff status, required conditions, recipient, confidence, rule ID, evidence span and optional user override. Unknown text remains unknown, not no-role. Display actual evidence, such as creating controller tokens versus rewarding tokens another card creates.
3. Rank by deck-specific gap filling and enabler/payoff compatibility: role counts, mana curve, colored-pip demand/usable sources, types, trigger events, token/counter/sacrifice/graveyard patterns and verified combo completion. Attach reasons to candidate cards and identify enabling cards. Keyword overlap alone is weak evidence.
4. Popularity/rank can break ties, never override budget/legal restrictions or masquerade as commander-specific synergy. Saltiness is an optional preference signal. Do not train a model on scraped EDHREC recommendations; mechanical logic is sufficient for an initial offline engine.
5. Validate LLM recommendations through the same candidate constraints and cost computation; keep suggestions whose evidence is uncertain visibly qualified. Model prose does not become a card rule or legal verdict.

Role counts are quantity-weighted and may overlap; one card filling draw and removal contributes to both, so role totals are not necessarily deck size. Distinguish recurring draw from cantrip/loot, permanent ramp from ritual, and flexible modal roles from simultaneously usable roles. Target counts are configurable by archetype/format and user; no source established a universal 10/10/10 formula as law. Sources informing these distinctions: https://github.com/dan-blanchard/mtg-skills/blob/main/mtg-utils/src/mtg_utils/_analysis/roles.py and https://github.com/TrumanAmsler/Tagit (accessed 2026-10-11).

### F6. Budget, ownership and `(n)` proposal

Product-derived arithmetic, not provider behavior: compute unmet quantity after assigning compatible owned copies by printing/finish and selected ownership policy. With owned-as-free enabled, `n = max(required - availableOwned, 0) * selectedUnitPrice`; fully owned is `(0.00)`. Adding a card already used elsewhere must follow an explicit copy-sharing/allocation setting; never silently spend one owned copy twice in a simultaneous allocation. Re-evaluate aggregate upgrade-pass cost, per-card acquisition cap and deck target after each proposed swap; removed cards are not assumed resale income.

For required quantity four, compatible owned quantity two, and selected unit price 3.00, display `(6.00)`, with market price and source/date separately accessible. Turning owned-as-free off changes the budgeting basis but does not change stored ownership. Missing/stale price remains unknown and fails strict price-confirmed budgets; it must never become zero. Do not combine USD and EUR without a verified conversion source/policy; group by currency or flag mismatch. Provider field/finish/price-source support is a R-02 dependency and UNVERIFIED from Scryfall primary documentation in this pass. Source attempted: https://scryfall.com/docs/api/cards (2026-10-11). MTGJSON Set documents finishes/identifiers but does not by itself establish a market-price feed: https://mtgjson.com/data-models/card/card-set/ (accessed 2026-10-11).

### F7. Multilingual and incomplete-data boundaries

MTGJSON Set schema includes `language`, `foreignData`, `originalText`, `originalType`, `text`, and face links. Canonical rules identity must be separate from localized display name/printed text. Recommend tagging the canonical normalized rules representation and translating role labels/UI, not applying English regex to translated or obsolete printed text. Store per-face evidence and avoid concatenating an incomplete sentence on one face into the next. Sources: https://mtgjson.com/data-models/card/card-set/ and https://github.com/dan-blanchard/mtg-skills/blob/main/mtg-utils/src/mtg_utils/card_classify.py (accessed 2026-10-11).

Coverage/accuracy for every language, new keyword, novel release and missing Oracle identity is UNVERIFIED: no multilingual classifier evaluation or labeled bulk dataset was measured. The proposed engine returns unknown on absent/unrecognized evidence, offers user override and records rule/data versions so tags can be refreshed without erasing overrides. Lean mode must label a recommendation universe limited to locally stored cards until an allowed online search fills it; absence from Lean storage is not proof no better card exists. These are product/engineering recommendations, not dataset guarantees; schema/coverage basis is F1/F2 sources (accessed 2026-10-11).

## Options

1. Recommended: deterministic local role/synergy engine from verified card data and app-owned rules, with licensed code inspiration and attributed Spellbook bulk combinations. Fully usable offline; explainable gaps and configurable budgets.
2. Optional Scryfall tags/rank enrichment after primary schema/terms verification. Tag bulk capability is a credible current lead, so do not design an unnecessary online-only assumption.
3. EDHREC browser links for reference. Automated enrichment or cached tables require explicit grant not found in current terms.

Judgments follow F1 through F7 sources (accessed 2026-10-11).

## Recommendation

Build the offline engine around effects, role gaps, enabler/payoff links, verified combos and exact marginal ownership-aware cost. Permit Spellbook's documented bulk-local use and MTGJSON's published license with notices; adapt verified 0BSD rule logic if useful after dependency audit. Leave automated EDHREC out under current terms. Enable Scryfall/tag enrichment only after R-02 resolves primary-source permissions and current metadata/schema. Every suggestion should show its role/synergy evidence, `(n)`, legality, bracket impact and data date. Sources and applicable permission clauses: F2/F4 (accessed 2026-10-11).

## Risks

- Publicly accessible source/data is not necessarily licensed; Tagit lacks a verified license and EDHREC explicitly restricts automation/copying. Sources: F2/F4 (accessed 2026-10-11).
- Current Scryfall primary schema/terms/tags could not be retrieved; wrapper tag-bulk support is evidence to investigate, not official confirmation. Sources: F1/F2/F4 (accessed/attempted 2026-10-11).
- Rules regex can confuse costs, opponents, payoffs, modes, transformed faces and reminders. Keep uncertainty/user overrides and fixture coverage. Source: licensed classifier in F2 (accessed 2026-10-11).
- Popularity/lift is correlation, not mechanical correctness; current October 2026 lift rollout supersedes old terminology. Sources: F3 (accessed 2026-10-11).
- Missing currency/finish/price/ownership allocation can produce false budget compliance. Validate with product arithmetic in F6; provider capabilities remain R-02 verification items.

## Confidence

High for retrieved EDHREC restrictions/lift change, MTGJSON fields/license, 0BSD code permission and Spellbook local-bulk instruction. Medium for proposed heuristic engine; no recommendation-quality or runtime benchmark performed. Low for unavailable primary Scryfall schema/tag policy, exact current host metadata and universal/multilingual classifier coverage. No finding contradicts the product's offline recommendation requirement; it establishes why proprietary online recommendation tables cannot be presumed available.
